package com.gorgiibot.gui;

import com.gorgiibot.GiveawayState;
import com.gorgiibot.GorgiiBotClient;
import com.gorgiibot.Participant;
import com.gorgiibot.audio.GorgiiSounds;
import com.gorgiibot.twitch.TwitchIrcClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Haupt-Screen als <b>hochauflösendes Fenster</b>: Sidebar-Navigation mit
 * Icons (Rad, Roulette, Teilnehmer, Gesperrte, Einstellungen), Status-Pills
 * und Karten mit Schatten – groß und scharf statt kleiner Minecraft-Menü-Optik.
 * Für die hohe Auflösung die GUI-Skala in den Minecraft-Optionen auf
 * „Automatisch“ oder 3–4 stellen (siehe README).
 *
 * <p>Die Teilnehmer- und Banlisten sind paginiert (Suche + Sortierung),
 * Aktionen (Entfernen / Bannen / Entbannen) laufen über Icon-Buttons.
 */
public class GiveawayScreen extends Screen {
    public enum View {
        WHEEL, ROULETTE, PARTICIPANTS, BANNED, SETTINGS
    }

    private static View lastView = View.WHEEL;

    private static final long WHEEL_MILLIS = 7000;
    /** Idle-Drehung des Rads (rad/s) – identisch zur Web-App. */
    private static final float IDLE_SPEED = 0.22f;
    private static final double TWO_PI = Math.PI * 2.0;
    private static final int ROW_H = 30;
    private static final int ROW_GAP = 2;

    private final GiveawayState state = GorgiiBotClient.state;
    private View view;
    private boolean autoStart;
    private boolean nextIsReroll;

    private final WheelRenderer wheel = new WheelRenderer();
    private final RouletteView roulette = new RouletteView();
    private final Random random = new Random();

    // Rad-Spin (eingefrorener Pool, damit nichts wackelt)
    private boolean wheelSpinning = false;
    private long wheelStart = 0;
    private float wheelFrom = 0;
    private float wheelTo = 0;
    private float wheelRotation = 0;
    private List<Participant> wheelPool = new ArrayList<>();
    private int wheelFrozenVersion = Integer.MIN_VALUE;
    private Participant wheelWinner;
    private int wheelLastSegment = -1;

    private long lastFrameAt = 0;
    private int lastMouseX = 0;
    private int lastMouseY = 0;

    // Listen-Zustand (bleibt über Rebuilds erhalten)
    private String searchText = "";
    private GiveawayState.SortMode sortMode = GiveawayState.SortMode.NEWEST;
    private int page = 0;
    private String bannedSearchText = "";
    private GiveawayState.SortMode bannedSortMode = GiveawayState.SortMode.AZ;
    private int bannedPage = 0;
    private String addText = "";

    // Layout (wird in init/render berechnet)
    private int winX, winY, winW, winH;
    private int sideX, sideW, contentX, contentW, contentY, contentH;
    private int rows = 8;

    private ButtonWidget[] navButtons = new ButtonWidget[5];
    private ButtonWidget closeButton;
    private ButtonWidget connectButton;
    private ButtonWidget startButton;
    private ButtonWidget resetButton;
    private ButtonWidget gotoParticipantsButton;
    private TextFieldWidget searchField;
    private TextFieldWidget bannedSearchField;
    private TextFieldWidget addField;
    private TextFieldWidget channelField;
    private TextFieldWidget keywordField;
    private ButtonWidget sortButton;
    private ButtonWidget prevButton;
    private ButtonWidget nextButton;
    private ButtonWidget addButton;
    private ButtonWidget testButton;
    private ButtonWidget clearButton;
    private ButtonWidget bannedSortButton;
    private ButtonWidget bannedPrevButton;
    private ButtonWidget bannedNextButton;
    private ButtonWidget[] rowRemoveButtons = new ButtonWidget[0];
    private ButtonWidget[] rowBanButtons = new ButtonWidget[0];
    private ButtonWidget[] rowUnbanButtons = new ButtonWidget[0];
    private ButtonWidget subOnlyButton;
    private ButtonWidget subLuckButton;
    private ButtonWidget claimButton;
    private ButtonWidget avatarsButton;
    private ButtonWidget removeConfirmedButton;
    private ButtonWidget removeTimeoutButton;
    private ButtonWidget soundButton;
    private ButtonWidget confettiButton;
    private ButtonWidget doneButton;

    public GiveawayScreen() {
        this(false, false);
    }

    /** @param autoStart startet sofort eine Ziehung (für „Neu ziehen“). */
    public GiveawayScreen(boolean autoStart) {
        this(autoStart, false);
    }

    /**
     * @param autoStart startet sofort eine Ziehung (für „Neu ziehen“ / Auto-Reroll)
     * @param isReroll  markiert die Ziehung als Reroll
     */
    public GiveawayScreen(boolean autoStart, boolean isReroll) {
        super(Text.literal("GorgiiBot Giveaway"));
        this.autoStart = autoStart;
        this.nextIsReroll = isReroll;
        if (autoStart) {
            this.view = GorgiiBotClient.state.uiTab == 1 ? View.ROULETTE : View.WHEEL;
        } else {
            this.view = lastView;
        }
    }

    /** Öffnet direkt eine bestimmte Ansicht (z. B. /giveaway settings). */
    public GiveawayScreen(View view) {
        super(Text.literal("GorgiiBot Giveaway"));
        this.view = view == null ? View.WHEEL : view;
        lastView = this.view;
    }

    private void computeLayout() {
        winX = 10;
        winY = 10;
        winW = Math.max(320, this.width - 20);
        winH = Math.max(240, this.height - 20);
        sideW = this.width < 600 ? 124 : 168;
        sideX = winX;
        contentX = winX + sideW + 10;
        contentW = Math.max(200, winX + winW - contentX - 10);
        contentY = winY + 38;
        contentH = Math.max(160, winY + winH - contentY - 10);
        int listTop = contentY + 82;
        int listBottom = contentY + contentH - 34;
        rows = Math.max(3, Math.min(14, (listBottom - listTop) / (ROW_H + ROW_GAP)));
    }

    @Override
    protected void init() {
        computeLayout();
        this.clearChildren();
        navButtons = new ButtonWidget[5];
        rowRemoveButtons = new ButtonWidget[0];
        rowBanButtons = new ButtonWidget[0];
        rowUnbanButtons = new ButtonWidget[0];
        searchField = null;
        bannedSearchField = null;
        addField = null;
        channelField = null;
        keywordField = null;
        connectButton = null;
        startButton = null;
        resetButton = null;
        gotoParticipantsButton = null;
        sortButton = null;
        prevButton = null;
        nextButton = null;
        addButton = null;
        testButton = null;
        clearButton = null;
        bannedSortButton = null;
        bannedPrevButton = null;
        bannedNextButton = null;
        subOnlyButton = null;
        subLuckButton = null;
        claimButton = null;
        avatarsButton = null;
        removeConfirmedButton = null;
        removeTimeoutButton = null;
        soundButton = null;
        confettiButton = null;
        doneButton = null;

        // Schließen (oben rechts, normaler Text-Button)
        closeButton = ButtonWidget.builder(Text.literal("Schließen"), b -> {
            if (client != null) {
                client.setScreen(null);
            }
        }).dimensions(winX + winW - 84, winY + 6, 74, 20).build();
        this.addDrawableChild(closeButton);

        buildNav();
        switch (view) {
            case WHEEL, ROULETTE -> buildStageControls();
            case PARTICIPANTS -> buildParticipants();
            case BANNED -> buildBanned();
            case SETTINGS -> buildSettings();
        }
        refreshButtons();
    }

    private void buildNav() {
        View[] views = View.values();
        int ny = winY + 44;
        for (int i = 0; i < views.length; i++) {
            View target = views[i];
            navButtons[i] = ButtonWidget.builder(Text.literal(""), b -> switchView(target))
                    .dimensions(sideX + 8, ny, sideW - 16, 26).build();
            this.addDrawableChild(navButtons[i]);
            ny += 30;
        }
    }

    private void buildStageControls() {
        int bw = Math.min(170, (contentW - 24) / 3);
        int totalW = bw * 3 + 16;
        int bx = contentX + (contentW - totalW) / 2;
        int by = contentY + contentH - 30;
        connectButton = ButtonWidget.builder(Text.literal("Verbinden"), b -> toggleConnect())
                .dimensions(bx, by, bw, 22).build();
        startButton = ButtonWidget.builder(Text.literal("Start"), b -> startSpin())
                .dimensions(bx + bw + 8, by, bw, 22).build();
        resetButton = ButtonWidget.builder(Text.literal("Zurücksetzen"), b -> reset())
                .dimensions(bx + (bw + 8) * 2, by, bw, 22).build();
        this.addDrawableChild(connectButton);
        this.addDrawableChild(startButton);
        this.addDrawableChild(resetButton);
        // Schnellzugriff auf die Teilnehmerliste
        gotoParticipantsButton = ButtonWidget.builder(Text.literal("Teilnehmer verwalten"),
                b -> switchView(View.PARTICIPANTS))
                .dimensions(contentX + contentW - 150, contentY + 8, 150, 20).build();
        this.addDrawableChild(gotoParticipantsButton);
    }

    private void buildParticipants() {
        int ty = contentY + 8;
        searchField = new TextFieldWidget(this.textRenderer, contentX, ty, Math.min(240, contentW - 160), 20,
                Text.literal("Suche"));
        searchField.setMaxLength(32);
        searchField.setText(searchText);
        searchField.setPlaceholder(Text.literal("Name suchen …"));
        searchField.setChangedListener(text -> {
            searchText = text;
            page = 0;
        });
        this.addDrawableChild(searchField);

        sortButton = ButtonWidget.builder(Text.literal(""), b -> {
            sortMode = nextSort(sortMode);
            page = 0;
            refreshButtons();
        }).dimensions(contentX + Math.min(240, contentW - 160) + 8, ty, 150, 20).build();
        this.addDrawableChild(sortButton);

        // Manuell hinzufügen + Test + Leeren
        int ay = contentY + contentH - 30;
        int addFieldW = Math.max(60, Math.min(200, contentW - 284));
        addField = new TextFieldWidget(this.textRenderer, contentX, ay, addFieldW, 20,
                Text.literal("Hinzufügen"));
        addField.setMaxLength(32);
        addField.setText(addText);
        addField.setPlaceholder(Text.literal("Name hinzufügen …"));
        addField.setChangedListener(text -> addText = text);
        this.addDrawableChild(addField);
        int ax = addField.getX() + addField.getWidth() + 6;
        addButton = ButtonWidget.builder(Text.literal("Hinzufügen"), b -> {
            if (state.addParticipant(addText)) {
                addText = "";
                if (addField != null) {
                    addField.setText("");
                }
            }
            refreshButtons();
        }).dimensions(ax, ay, 100, 20).build();
        this.addDrawableChild(addButton);
        ax += 104;
        testButton = ButtonWidget.builder(Text.literal("12 Test-User"), b -> {
            state.addTestParticipants();
            refreshButtons();
        }).dimensions(ax, ay, 100, 20).build();
        this.addDrawableChild(testButton);
        ax += 104;
        clearButton = ButtonWidget.builder(Text.literal("Leeren"), b -> {
            state.clear();
            page = 0;
            refreshButtons();
        }).dimensions(ax, ay, 70, 20).build();
        this.addDrawableChild(clearButton);

        // Paginierung (oben rechts)
        prevButton = ButtonWidget.builder(Text.literal(""), b -> {
            if (page > 0) {
                page--;
            }
            refreshButtons();
        }).dimensions(contentX + contentW - 62, ty, 28, 20).build();
        this.addDrawableChild(prevButton);
        nextButton = ButtonWidget.builder(Text.literal(""), b -> {
            page++;
            refreshButtons();
        }).dimensions(contentX + contentW - 30, ty, 28, 20).build();
        this.addDrawableChild(nextButton);

        // Zeilen-Buttons (Icon-Buttons, Icons werden in render() darüber gezeichnet)
        rowRemoveButtons = new ButtonWidget[rows];
        rowBanButtons = new ButtonWidget[rows];
        int listTop = contentY + 82;
        for (int i = 0; i < rows; i++) {
            int ry = listTop + i * (ROW_H + ROW_GAP);
            int slot = i;
            rowRemoveButtons[i] = ButtonWidget.builder(Text.literal(""), b -> {
                Participant p = participantAtSlot(slot);
                if (p != null) {
                    state.remove(p.lower);
                    refreshButtons();
                }
            }).dimensions(contentX + contentW - 62, ry + 3, 24, 24).build();
            this.addDrawableChild(rowRemoveButtons[i]);
            rowBanButtons[i] = ButtonWidget.builder(Text.literal(""), b -> {
                Participant p = participantAtSlot(slot);
                if (p != null) {
                    state.ban(p.lower, p.name);
                    refreshButtons();
                }
            }).dimensions(contentX + contentW - 34, ry + 3, 24, 24).build();
            this.addDrawableChild(rowBanButtons[i]);
        }
    }

    private void buildBanned() {
        int ty = contentY + 8;
        bannedSearchField = new TextFieldWidget(this.textRenderer, contentX, ty,
                Math.min(240, contentW - 160), 20, Text.literal("Suche"));
        bannedSearchField.setMaxLength(32);
        bannedSearchField.setText(bannedSearchText);
        bannedSearchField.setPlaceholder(Text.literal("Name suchen …"));
        bannedSearchField.setChangedListener(text -> {
            bannedSearchText = text;
            bannedPage = 0;
        });
        this.addDrawableChild(bannedSearchField);

        bannedSortButton = ButtonWidget.builder(Text.literal(""), b -> {
            bannedSortMode = nextSort(bannedSortMode);
            bannedPage = 0;
            refreshButtons();
        }).dimensions(contentX + Math.min(240, contentW - 160) + 8, ty, 150, 20).build();
        this.addDrawableChild(bannedSortButton);

        bannedPrevButton = ButtonWidget.builder(Text.literal(""), b -> {
            if (bannedPage > 0) {
                bannedPage--;
            }
            refreshButtons();
        }).dimensions(contentX + contentW - 62, ty, 28, 20).build();
        this.addDrawableChild(bannedPrevButton);
        bannedNextButton = ButtonWidget.builder(Text.literal(""), b -> {
            bannedPage++;
            refreshButtons();
        }).dimensions(contentX + contentW - 30, ty, 28, 20).build();
        this.addDrawableChild(bannedNextButton);

        rowUnbanButtons = new ButtonWidget[rows];
        int listTop = contentY + 82;
        for (int i = 0; i < rows; i++) {
            int ry = listTop + i * (ROW_H + ROW_GAP);
            int slot = i;
            rowUnbanButtons[i] = ButtonWidget.builder(Text.literal(""), b -> {
                GiveawayState.BannedEntry e = bannedAtSlot(slot);
                if (e != null) {
                    state.unban(e.lower());
                    refreshButtons();
                }
            }).dimensions(contentX + contentW - 34, ry + 3, 24, 24).build();
            this.addDrawableChild(rowUnbanButtons[i]);
        }
    }

    private void buildSettings() {
        int fieldW = Math.min(260, contentW - 20);
        int fx = contentX + 10;
        int fy = contentY + 30;
        channelField = new TextFieldWidget(this.textRenderer, fx, fy, fieldW, 20, Text.literal("Twitch-Kanal"));
        channelField.setMaxLength(32);
        channelField.setText(state.config.channel);
        channelField.setPlaceholder(Text.literal("kanalname"));
        channelField.setChangedListener(text -> state.config.channel = text);
        this.addDrawableChild(channelField);
        fy += 32;
        keywordField = new TextFieldWidget(this.textRenderer, fx, fy, fieldW, 20, Text.literal("Schlüsselwort"));
        keywordField.setMaxLength(32);
        keywordField.setText(state.config.keyword);
        keywordField.setPlaceholder(Text.literal("!teilnahme"));
        keywordField.setChangedListener(text -> state.config.keyword = text.isEmpty() ? "!teilnahme" : text);
        this.addDrawableChild(keywordField);

        int colW = Math.min(210, (contentW - 30) / 2);
        int colB = fx + colW + 10;
        int ry = fy + 34;
        subOnlyButton = toggleButton(fx, ry, colW, () -> state.config.requireSubscriber,
                v -> state.config.requireSubscriber = v);
        ry += 24;
        subLuckButton = ButtonWidget.builder(Text.literal(""), b -> {
            state.config.subLuck = state.config.subLuck % 5 + 1;
            refreshButtons();
        }).dimensions(fx, ry, colW, 20).build();
        this.addDrawableChild(subLuckButton);
        ry += 24;
        claimButton = ButtonWidget.builder(Text.literal(""), b -> {
            state.config.claimSeconds = nextClaimStep(state.config.claimSeconds);
            refreshButtons();
        }).dimensions(fx, ry, colW, 20).build();
        this.addDrawableChild(claimButton);
        ry += 24;
        avatarsButton = toggleButton(fx, ry, colW, () -> state.config.avatars,
                v -> state.config.avatars = v);
        ry += 24;

        int ryB = fy + 34;
        removeConfirmedButton = toggleButton(colB, ryB, colW, () -> state.config.autoRemoveConfirmed,
                v -> state.config.autoRemoveConfirmed = v);
        ryB += 24;
        removeTimeoutButton = toggleButton(colB, ryB, colW, () -> state.config.autoRemoveTimeout,
                v -> state.config.autoRemoveTimeout = v);
        ryB += 24;
        soundButton = toggleButton(colB, ryB, colW, () -> state.config.sound,
                v -> state.config.sound = v);
        ryB += 24;
        confettiButton = toggleButton(colB, ryB, colW, () -> state.config.confetti,
                v -> state.config.confetti = v);
        ryB += 24;

        int bottom = Math.max(ry, ryB) + 10;
        doneButton = ButtonWidget.builder(Text.literal("Fertig"), b -> {
            saveSettings();
            switchView(View.WHEEL);
        }).dimensions(fx, bottom, fieldW, 22).build();
        this.addDrawableChild(doneButton);
    }

    private ButtonWidget toggleButton(int x, int y, int w, java.util.function.BooleanSupplier get,
                                      java.util.function.Consumer<Boolean> set) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(""), btn -> {
            set.accept(!get.getAsBoolean());
            refreshButtons();
        }).dimensions(x, y, w, 20).build();
        this.addDrawableChild(b);
        return b;
    }

    private static int nextClaimStep(int current) {
        int[] steps = {30, 45, 60, 90, 120, 180};
        for (int step : steps) {
            if (step > current) {
                return step;
            }
        }
        return steps[0];
    }

    private static GiveawayState.SortMode nextSort(GiveawayState.SortMode current) {
        return switch (current) {
            case NEWEST -> GiveawayState.SortMode.OLDEST;
            case OLDEST -> GiveawayState.SortMode.AZ;
            case AZ -> GiveawayState.SortMode.ZA;
            case ZA -> GiveawayState.SortMode.NEWEST;
        };
    }

    private static String sortLabel(GiveawayState.SortMode mode) {
        return switch (mode) {
            case NEWEST -> "Neueste zuerst";
            case OLDEST -> "Älteste zuerst";
            case AZ -> "A–Z";
            case ZA -> "Z–A";
        };
    }

    private void switchView(View next) {
        if (view == View.SETTINGS && next != View.SETTINGS) {
            saveSettings();
        }
        view = next;
        lastView = next;
        if (next == View.WHEEL || next == View.ROULETTE) {
            state.uiTab = next == View.ROULETTE ? 1 : 0;
        }
        this.init();
    }

    /** Speichert Einstellungen + verbindet bei Kanalwechsel neu (wie früher). */
    private void saveSettings() {
        state.config.save();
        TwitchIrcClient.Status status = state.twitch.status();
        if (!state.config.channel.equals(state.twitch.channel())
                && (status == TwitchIrcClient.Status.CONNECTED
                || status == TwitchIrcClient.Status.CONNECTING)) {
            state.twitch.disconnect();
            state.twitch.connect(state.config.channel);
        }
    }

    private void toggleConnect() {
        TwitchIrcClient twitch = state.twitch;
        if (twitch.status() == TwitchIrcClient.Status.CONNECTED
                || twitch.status() == TwitchIrcClient.Status.CONNECTING) {
            twitch.disconnect();
        } else {
            twitch.connect(state.config.channel);
        }
        refreshButtons();
    }

    private void startSpin() {
        startSpin(false);
    }

    private void startSpin(boolean reroll) {
        if (wheelSpinning || roulette.isSpinning() || state.isClaimPending()) {
            return;
        }
        List<Participant> pool = state.snapshot();
        if (pool.isEmpty()) {
            return;
        }
        // Alle Avatare des Pools proaktiv laden (Fix: nicht nur sichtbare).
        state.avatars.prefetchParticipants(pool);
        Participant winner = state.pickWinner(pool);
        if (winner == null) {
            return;
        }
        // Alte Bestätigungsphase beenden (z. B. nach manuellem „Neu ziehen“)
        state.endClaim();
        nextIsReroll = reroll;
        if (view == View.WHEEL) {
            wheelPool = pool;
            wheelWinner = winner;
            wheelFrozenVersion = state.getVersion();
            int index = pool.indexOf(winner);
            wheelFrom = wheelRotation;
            wheelTo = WheelRenderer.rotationForSegment(wheelFrom, index, pool.size(), 5,
                    0.2 + random.nextDouble() * 0.6);
            wheelStart = System.currentTimeMillis();
            wheelSpinning = true;
            wheelLastSegment = -1;
        } else {
            roulette.start(pool, winner, this::showWinner);
        }
        refreshButtons();
    }

    /** Gewinner steht fest: Bestätigungsphase starten und Gewinner-Screen zeigen. */
    private void showWinner(Participant winner) {
        state.startClaim(winner, nextIsReroll);
        nextIsReroll = false;
        if (client != null) {
            client.setScreen(new WinnerScreen(winner));
        }
    }

    /** Auto-Reroll: Zeit abgelaufen (wie in der Web-App) – neu auslosen. */
    private void rerollNow() {
        state.endClaim();
        nextIsReroll = true;
        startSpin(true);
    }

    private void reset() {
        if (wheelSpinning || roulette.isSpinning()) {
            return;
        }
        state.clear();
        roulette.backToIdle();
        refreshButtons();
    }

    private List<Participant> filteredParticipants() {
        return state.snapshotFiltered(searchText, sortMode);
    }

    private List<GiveawayState.BannedEntry> filteredBanned() {
        return state.bannedFiltered(bannedSearchText, bannedSortMode);
    }

    private Participant participantAtSlot(int slot) {
        List<Participant> list = filteredParticipants();
        int idx = page * rows + slot;
        return idx >= 0 && idx < list.size() ? list.get(idx) : null;
    }

    private GiveawayState.BannedEntry bannedAtSlot(int slot) {
        List<GiveawayState.BannedEntry> list = filteredBanned();
        int idx = bannedPage * rows + slot;
        return idx >= 0 && idx < list.size() ? list.get(idx) : null;
    }

    private void refreshButtons() {
        boolean busy = wheelSpinning || roulette.isSpinning();
        boolean pending = state.isClaimPending();
        // Navigation während eines Laufs sperren (Spin läuft im Hintergrund weiter).
        for (ButtonWidget b : navButtons) {
            if (b != null) {
                b.active = !busy;
            }
        }
        if (connectButton != null) {
            boolean connected = state.twitch.status() == TwitchIrcClient.Status.CONNECTED
                    || state.twitch.status() == TwitchIrcClient.Status.CONNECTING;
            connectButton.setMessage(Text.literal(connected ? "Trennen" : "Verbinden"));
        }
        if (startButton != null) {
            startButton.setMessage(Text.literal(busy ? "Läuft …" : pending ? "Warte …" : "Start"));
            startButton.active = !busy && !pending && state.count() > 0;
        }
        if (resetButton != null) {
            resetButton.active = !busy;
        }
        if (gotoParticipantsButton != null) {
            gotoParticipantsButton.active = !busy;
        }
        if (sortButton != null) {
            sortButton.setMessage(Text.literal("Sortierung: " + sortLabel(sortMode)));
        }
        if (bannedSortButton != null) {
            bannedSortButton.setMessage(Text.literal("Sortierung: " + sortLabel(bannedSortMode)));
        }
        // Paginierung
        int total = filteredParticipants().size();
        int maxPage = Math.max(0, (total - 1) / Math.max(1, rows));
        if (page > maxPage) {
            page = maxPage;
        }
        if (prevButton != null) {
            prevButton.active = page > 0;
        }
        if (nextButton != null) {
            nextButton.active = page < maxPage;
        }
        int bannedTotal = filteredBanned().size();
        int bannedMax = Math.max(0, (bannedTotal - 1) / Math.max(1, rows));
        if (bannedPage > bannedMax) {
            bannedPage = bannedMax;
        }
        if (bannedPrevButton != null) {
            bannedPrevButton.active = bannedPage > 0;
        }
        if (bannedNextButton != null) {
            bannedNextButton.active = bannedPage < bannedMax;
        }
        // Zeilen-Buttons ein-/ausblenden
        for (int i = 0; i < rowRemoveButtons.length; i++) {
            boolean has = participantAtSlot(i) != null;
            rowRemoveButtons[i].visible = has;
            rowRemoveButtons[i].active = has && !busy;
            rowBanButtons[i].visible = has;
            rowBanButtons[i].active = has && !busy;
        }
        for (int i = 0; i < rowUnbanButtons.length; i++) {
            boolean has = bannedAtSlot(i) != null;
            rowUnbanButtons[i].visible = has;
            rowUnbanButtons[i].active = has && !busy;
        }
        // Einstellungen
        if (subOnlyButton != null) {
            subOnlyButton.setMessage(Text.literal("Nur Subs: " + onOff(state.config.requireSubscriber)));
            subLuckButton.setMessage(Text.literal("Sub-Glück: x" + state.config.subLuck));
            claimButton.setMessage(Text.literal("Bestätigung: " + state.config.claimSeconds + " s"));
            avatarsButton.setMessage(Text.literal("Avatare: " + onOff(state.config.avatars)));
            removeConfirmedButton.setMessage(Text.literal("Nach Claim: " + removeLabel(state.config.autoRemoveConfirmed)));
            removeTimeoutButton.setMessage(Text.literal("Nach Timeout: " + removeLabel(state.config.autoRemoveTimeout)));
            soundButton.setMessage(Text.literal("Sound: " + onOff(state.config.sound)));
            confettiButton.setMessage(Text.literal("Konfetti: " + onOff(state.config.confetti)));
        }
    }

    private static String onOff(boolean value) {
        return value ? "AN" : "AUS";
    }

    private static String removeLabel(boolean value) {
        return value ? "ENTF." : "BEHALTEN";
    }

    /** Relative Zeitangabe für die Teilnehmerliste („vor 5 Min“). */
    private static String timeAgo(long time) {
        long diff = System.currentTimeMillis() - time;
        if (diff < 60_000L) {
            return "gerade eben";
        }
        if (diff < 3_600_000L) {
            return "vor " + (diff / 60_000L) + " Min";
        }
        if (diff < 86_400_000L) {
            return "vor " + (diff / 3_600_000L) + " Std";
        }
        return "vor " + (diff / 86_400_000L) + " Tagen";
    }

    private void updateWheelSpin() {
        if (!wheelSpinning) {
            return;
        }
        float t = (System.currentTimeMillis() - wheelStart) / (float) WHEEL_MILLIS;
        if (t >= 1f) {
            wheelSpinning = false;
            wheelRotation = wheelTo;
            showWinner(wheelWinner);
            return;
        }
        wheelRotation = wheelFrom + (wheelTo - wheelFrom) * easeOutQuint(t);
        int seg = WheelRenderer.segmentUnderPointer(wheelRotation, wheelPool.size());
        if (seg != wheelLastSegment) {
            wheelLastSegment = seg;
            GorgiiSounds.tick();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (client == null) {
            return;
        }
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        long now = System.currentTimeMillis();
        float dt = lastFrameAt == 0 ? 0.016f : Math.min(0.1f, (now - lastFrameAt) / 1000f);
        lastFrameAt = now;

        if (autoStart) {
            autoStart = false;
            startSpin(nextIsReroll);
        }

        // Teilnehmer + Idle-Animationen
        List<Participant> pool = wheelSpinning ? wheelPool : state.snapshot();
        if (!wheelSpinning && pool.size() > 1) {
            wheelRotation += IDLE_SPEED * dt;
            if (wheelRotation > TWO_PI) {
                wheelRotation -= (float) TWO_PI;
            }
        }
        int stageW = contentW;
        roulette.tick(pool, state.getVersion(), stageW, dt);
        updateWheelSpin();

        // Automatischer Reroll, wenn die Bestätigungszeit abgelaufen ist
        if (!wheelSpinning && !roulette.isSpinning() && state.isRerollDue()) {
            rerollNow();
            if (wheelSpinning || roulette.isSpinning()) {
                pool = wheelSpinning ? wheelPool : state.snapshot();
            }
        }

        if (client.currentScreen != this) {
            return;
        }
        // Alle Avatare proaktiv laden (Fix) – Dedup macht das billig.
        state.avatars.prefetchParticipants(pool);
        if (view == View.BANNED) {
            java.util.ArrayList<String> lowers = new java.util.ArrayList<>();
            for (GiveawayState.BannedEntry e : filteredBanned()) {
                lowers.add(e.lower());
            }
            state.avatars.prefetch(lowers);
        }
        refreshButtons();

        drawWindow(context);
        drawSidebar(context);
        drawHeader(context);
        switch (view) {
            case WHEEL -> drawWheelView(context, pool);
            case ROULETTE -> drawRouletteView(context, pool);
            case PARTICIPANTS -> drawParticipantsView(context);
            case BANNED -> drawBannedView(context);
            case SETTINGS -> drawSettingsView(context);
        }
        if (client.currentScreen != this) {
            return;
        }
        super.render(context, mouseX, mouseY, delta);
        drawIconOverlays(context);
    }

    // ---------------- Fenster / Sidebar / Header ----------------

    private void drawWindow(DrawContext context) {
        // Hintergrund-Verlauf + hochauflösendes Panel (keine Fenster-Optik)
        GuiUtil.gradientV(context, 0, 0, this.width, this.height, 0xFF0D1117, 0xFF131720);
        GuiUtil.shadow(context, winX, winY, winX + winW, winY + winH, 12);
        GuiUtil.roundedRect(context, winX, winY, winX + winW, winY + winH, 12, GuiUtil.PANEL);
        GuiUtil.roundedBorder(context, winX, winY, winX + winW, winY + winH, 12, GuiUtil.BORDER);
        TextRenderer tr = this.textRenderer;
        context.drawText(tr, "GorgiiBot Giveaway", winX + 14, winY + 8, GuiUtil.TEXT, false);
        context.drawText(tr, "Twitch · Glücksrad & Roulette", winX + 14, winY + 17, GuiUtil.TEXT_FAINT, false);
    }

    private void drawSidebar(DrawContext context) {
        TextRenderer tr = this.textRenderer;
        int sx2 = sideX + sideW;
        GuiUtil.roundedRect(context, sideX + 4, winY + 36, sx2 - 4, winY + winH - 8, 8, GuiUtil.PANEL_DARK);
        View[] views = View.values();
        String[] labels = {"Glücksrad", "Roulette", "Teilnehmer", "Gesperrte", "Einstellungen"};
        int ny = winY + 44;
        for (int i = 0; i < views.length; i++) {
            boolean active = views[i] == view;
            if (active) {
                GuiUtil.roundedRect(context, sideX + 8, ny, sx2 - 8, ny + 26, 6, 0xFF21262D);
                context.fill(sideX + 8, ny + 5, sideX + 11, ny + 21, GuiUtil.ACCENT);
            }
            int iconColor = active ? GuiUtil.ACCENT : GuiUtil.TEXT_DIM;
            drawNavIcon(context, views[i], sideX + 16, ny + 5, iconColor);
            context.drawText(tr, labels[i], sideX + 38, ny + 9, active ? GuiUtil.TEXT : GuiUtil.TEXT_DIM, false);
            // Zähler-Badges
            if (views[i] == View.PARTICIPANTS) {
                drawBadge(context, tr, sx2 - 30, ny + 6, String.valueOf(state.count()), GuiUtil.ACCENT_BLUE);
            } else if (views[i] == View.BANNED) {
                drawBadge(context, tr, sx2 - 30, ny + 6, String.valueOf(state.bannedCount()), GuiUtil.RED);
            }
            ny += 30;
        }
        // Sidebar-Fuß: Tastenhilfe
        int footY = winY + winH - 34;
        context.drawText(tr, "G: Menü öffnen", sideX + 14, footY, GuiUtil.TEXT_FAINT, false);
        context.drawText(tr, "/giveaway im Chat", sideX + 14, footY + 11, GuiUtil.TEXT_FAINT, false);
    }

    private void drawNavIcon(DrawContext context, View v, int x, int y, int color) {
        switch (v) {
            case WHEEL -> GuiIcons.wheel(context, x, y, color);
            case ROULETTE -> GuiIcons.roulette(context, x, y, color);
            case PARTICIPANTS -> GuiIcons.users(context, x, y, color);
            case BANNED -> GuiIcons.banned(context, x, y, color);
            case SETTINGS -> GuiIcons.settings(context, x, y, color);
        }
    }

    private void drawBadge(DrawContext context, TextRenderer tr, int x, int y, String text, int color) {
        int w = Math.max(16, tr.getWidth(text) + 8);
        GuiUtil.roundedRect(context, x, y, x + w, y + 14, 7, GuiUtil.darken(color, 0.35f));
        GuiUtil.drawCenteredText(context, tr, text, x + w / 2, y + 3, color, false);
    }

    private void drawHeader(DrawContext context) {
        TextRenderer tr = this.textRenderer;
        String title = switch (view) {
            case WHEEL -> "Glücksrad";
            case ROULETTE -> "Roulette";
            case PARTICIPANTS -> "Teilnehmer verwalten";
            case BANNED -> "Gesperrte User";
            case SETTINGS -> "Einstellungen";
        };
        context.drawText(tr, title, contentX, contentY - 14, GuiUtil.TEXT, false);
        // Status-Pills rechts
        TwitchIrcClient twitch = state.twitch;
        TwitchIrcClient.Status status = twitch.status();
        int dot = status == TwitchIrcClient.Status.CONNECTED ? GuiUtil.GREEN
                : status == TwitchIrcClient.Status.CONNECTING ? GuiUtil.YELLOW
                : status == TwitchIrcClient.Status.ERROR ? GuiUtil.RED : GuiUtil.TEXT_FAINT;
        String label = status == TwitchIrcClient.Status.CONNECTED ? "Verbunden"
                : status == TwitchIrcClient.Status.CONNECTING ? "Verbinde …"
                : status == TwitchIrcClient.Status.ERROR ? "Fehler" : "Getrennt";
        String detail = twitch.statusDetail();
        if (detail.isEmpty() && status == TwitchIrcClient.Status.DISCONNECTED) {
            detail = state.config.channel.isEmpty() ? "Kein Kanal gesetzt" : "#" + state.config.channel;
        }
        String pill = label + " · " + detail;
        int pillW = Math.min(contentW, tr.getWidth(pill) + 26);
        int pillX = contentX + contentW - pillW;
        GuiUtil.roundedRect(context, pillX, contentY - 18, pillX + pillW, contentY - 2, 8, GuiUtil.PANEL_DARK);
        GuiUtil.fillCircle(context, pillX + 10, contentY - 10, 3, dot);
        context.drawText(tr, GuiUtil.trimToWidth(tr, pill, pillW - 20), pillX + 16, contentY - 13,
                GuiUtil.TEXT_DIM, false);
        // Claim-Banner (nur auf den Bühnen-Ansichten)
        if ((view == View.WHEEL || view == View.ROULETTE) && state.isClaimActive()) {
            drawClaimBanner(context, contentX, contentY, contentW);
        }
    }

    /** Bestätigungs-Phase: Countdown, Gewinner und Status als Banner. */
    private void drawClaimBanner(DrawContext context, int x, int y, int w) {
        TextRenderer tr = this.textRenderer;
        Participant winner = state.getClaimWinner();
        if (winner == null) {
            return;
        }
        int bh = 30;
        GuiUtil.roundedRect(context, x, y, x + w, y + bh, 8, GuiUtil.PANEL_DARK);
        GuiUtil.roundedBorder(context, x, y, x + w, y + bh, 8, GuiUtil.BORDER);
        String statusText;
        int statusColor;
        if (state.isClaimed()) {
            statusText = "Bestätigt";
            statusColor = GuiUtil.GREEN;
        } else if (state.isClaimExpired()) {
            statusText = "Zeit abgelaufen – Reroll …";
            statusColor = GuiUtil.RED;
        } else {
            statusText = "Warte auf Bestätigung";
            statusColor = GuiUtil.YELLOW;
        }
        String left = (state.isRerollClaim() ? "Reroll: " : "") + statusText + " · @" + winner.name;
        context.drawText(tr, GuiUtil.trimToWidth(tr, left, w - 120), x + 10, y + 6, statusColor, false);
        long remain = state.claimRemainMs();
        long total = state.claimTotalMs();
        String right = state.isClaimPending() ? "noch " + ((remain + 999) / 1000) + " s"
                : state.isClaimed() ? "✓ bestätigt" : "0 s";
        context.drawText(tr, right, x + w - 10 - tr.getWidth(right), y + 6, GuiUtil.TEXT_DIM, false);
        // Fortschrittsbalken
        int barX = x + 10;
        int barW = w - 20;
        int barY = y + bh - 8;
        GuiUtil.roundedRect(context, barX, barY, barX + barW, barY + 4, 2, 0xFF23262E);
        int filled = total <= 0 ? 0 : (int) (barW * Math.max(0, Math.min(1, remain / (double) total)));
        int barColor = state.isClaimed() ? GuiUtil.GREEN : remain < 10_000 ? GuiUtil.RED : GuiUtil.YELLOW;
        if (filled > 2) {
            GuiUtil.roundedRect(context, barX, barY, barX + filled, barY + 4, 2, barColor);
        }
    }

    // ---------------- Bühnen-Ansichten ----------------

    private int stageTop() {
        return contentY + (state.isClaimActive() && (view == View.WHEEL || view == View.ROULETTE) ? 36 : 4);
    }

    private void drawWheelView(DrawContext context, List<Participant> pool) {
        TextRenderer tr = this.textRenderer;
        int top = stageTop();
        int bottom = contentY + contentH - 38;
        int stageH = Math.max(120, bottom - top);
        GuiUtil.roundedRect(context, contentX, top, contentX + contentW, top + stageH, 10, GuiUtil.PANEL_DARK);
        GuiUtil.roundedBorder(context, contentX, top, contentX + contentW, top + stageH, 10, GuiUtil.BORDER);
        int version = wheelSpinning ? wheelFrozenVersion : state.getVersion();
        wheel.ensure(pool, version);
        int size = Math.max(80, Math.min(contentW, stageH) - 24);
        int cx = contentX + contentW / 2;
        int cy = top + stageH / 2;
        wheel.draw(context, tr, pool, cx, cy, size, wheelRotation);
        // Zeiger oben (nach unten zeigendes Dreieck aus Balken)
        int py = cy - size / 2 - 2;
        for (int k = 0; k < 6; k++) {
            int barW = 20 - k * 3;
            context.fill(cx - barW / 2, py + k * 3, cx + (barW + 1) / 2, py + 3 + k * 3, GuiUtil.ACCENT);
        }
        if (pool.isEmpty()) {
            GuiUtil.drawCenteredText(context, tr, "Keine Teilnehmer", cx, cy - 4, GuiUtil.TEXT_DIM, false);
            GuiUtil.drawCenteredText(context, tr, "Chat: " + state.config.keyword, cx, cy + 10,
                    GuiUtil.TEXT_FAINT, false);
        }
        drawStageFooter(context, pool);
    }

    private void drawRouletteView(DrawContext context, List<Participant> pool) {
        TextRenderer tr = this.textRenderer;
        int top = stageTop();
        int bottom = contentY + contentH - 38;
        int stageH = Math.max(120, bottom - top);
        GuiUtil.roundedRect(context, contentX, top, contentX + contentW, top + stageH, 10, GuiUtil.PANEL_DARK);
        GuiUtil.roundedBorder(context, contentX, top, contentX + contentW, top + stageH, 10, GuiUtil.BORDER);
        int stripH = RouletteView.CARD_HEIGHT + 28;
        int stripY = top + (stageH - stripH) / 2;
        roulette.draw(context, tr, state.avatars, contentX + 8, stripY, contentW - 16, stripH);
        if (pool.isEmpty()) {
            GuiUtil.drawCenteredText(context, tr, "Keine Teilnehmer – Chat: " + state.config.keyword,
                    contentX + contentW / 2, stripY + stripH + 8, GuiUtil.TEXT_FAINT, false);
        }
        drawStageFooter(context, pool);
    }

    private void drawStageFooter(DrawContext context, List<Participant> pool) {
        TextRenderer tr = this.textRenderer;
        String info = "Teilnehmer: " + state.count() + " · Wort: " + state.config.keyword;
        Participant last = state.getLastWinner();
        if (last != null) {
            info += " · Letzter Gewinner: " + last.name;
        }
        GuiUtil.drawCenteredText(context, tr, GuiUtil.trimToWidth(tr, info, contentW - 20),
                contentX + contentW / 2, contentY + contentH - 52, GuiUtil.TEXT_FAINT, false);
    }

    // ---------------- Teilnehmerliste ----------------

    private void drawParticipantsView(DrawContext context) {
        TextRenderer tr = this.textRenderer;
        List<Participant> list = filteredParticipants();
        int total = list.size();
        int maxPage = Math.max(0, (total - 1) / Math.max(1, rows));
        if (page > maxPage) {
            page = maxPage;
        }
        // Paginierungs-Label (links neben den Pfeil-Buttons)
        String pageLabel = total == 0 ? "0" : (page + 1) + " / " + (maxPage + 1);
        GuiUtil.drawCenteredText(context, tr, pageLabel, contentX + contentW - 82, contentY + 13,
                GuiUtil.TEXT_DIM, false);
        // Listen-Karte
        int listTop = contentY + 82;
        int listBottom = contentY + contentH - 38;
        GuiUtil.roundedRect(context, contentX, listTop - 6, contentX + contentW, listBottom + 4, 8,
                GuiUtil.PANEL_DARK);
        GuiUtil.roundedBorder(context, contentX, listTop - 6, contentX + contentW, listBottom + 4, 8,
                GuiUtil.BORDER);
        if (total == 0) {
            String msg = searchText.isEmpty() ? "Noch keine Teilnehmer – Chat: " + state.config.keyword
                    : "Keine Treffer für „" + searchText + "“";
            GuiUtil.drawCenteredText(context, tr, msg, contentX + contentW / 2, listTop + 10,
                    GuiUtil.TEXT_FAINT, false);
        }
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= total) {
                break;
            }
            Participant p = list.get(idx);
            int ry = listTop + i * (ROW_H + ROW_GAP);
            boolean hover = GuiUtil.inRect(mouseX(), mouseY(), contentX, ry, contentX + contentW, ry + ROW_H);
            GuiUtil.roundedRect(context, contentX + 4, ry, contentX + contentW - 4, ry + ROW_H, 6,
                    hover ? 0xFF21262D : 0xFF161B22);
            // Avatar (24×24) + Name + Badges
            int ax = contentX + 10;
            int ay = ry + 3;
            drawAvatar(context, tr, p.lower, p.name, p.color, ax, ay, 24);
            String name = GuiUtil.trimToWidth(tr, p.name, contentW - 220);
            context.drawText(tr, name, ax + 30, ry + 6, p.color, false);
            int badgeX = ax + 30 + tr.getWidth(name) + 6;
            if (p.boosted) {
                context.drawText(tr, "★ SUB", badgeX, ry + 6, 0xFFFFD166, false);
                badgeX += tr.getWidth("★ SUB") + 8;
            }
            String joined = "· " + timeAgo(p.joinedAt);
            context.drawText(tr, GuiUtil.trimToWidth(tr, joined, contentX + contentW - 70 - badgeX),
                    badgeX, ry + 6, GuiUtil.TEXT_FAINT, false);
            context.drawText(tr, GuiUtil.trimToWidth(tr, "@" + p.lower, contentW - 220),
                    ax + 30, ry + 17, GuiUtil.TEXT_FAINT, false);
        }
        // Fußzeile: Zähler + Legende
        String foot = total + " Teilnehmer" + (searchText.isEmpty() ? "" : " (gefiltert)");
        context.drawText(tr, foot, contentX + 4, contentY + contentH - 14, GuiUtil.TEXT_FAINT, false);
        if (contentW > 520) {
            String legend = "Papierkorb = entfernen · Verbot = bannen";
            context.drawText(tr, legend, contentX + contentW - 4 - tr.getWidth(legend),
                    contentY + contentH - 14, GuiUtil.TEXT_FAINT, false);
        }
    }

    // ---------------- Banliste ----------------

    private void drawBannedView(DrawContext context) {
        TextRenderer tr = this.textRenderer;
        List<GiveawayState.BannedEntry> list = filteredBanned();
        int total = list.size();
        int maxPage = Math.max(0, (total - 1) / Math.max(1, rows));
        if (bannedPage > maxPage) {
            bannedPage = maxPage;
        }
        String pageLabel = total == 0 ? "0" : (bannedPage + 1) + " / " + (maxPage + 1);
        GuiUtil.drawCenteredText(context, tr, pageLabel, contentX + contentW - 82, contentY + 13,
                GuiUtil.TEXT_DIM, false);
        int listTop = contentY + 82;
        int listBottom = contentY + contentH - 38;
        GuiUtil.roundedRect(context, contentX, listTop - 6, contentX + contentW, listBottom + 4, 8,
                GuiUtil.PANEL_DARK);
        GuiUtil.roundedBorder(context, contentX, listTop - 6, contentX + contentW, listBottom + 4, 8,
                GuiUtil.BORDER);
        if (total == 0) {
            String msg = bannedSearchText.isEmpty() ? "Keine gesperrten User – alles sauber!"
                    : "Keine Treffer für „" + bannedSearchText + "“";
            GuiUtil.drawCenteredText(context, tr, msg, contentX + contentW / 2, listTop + 10,
                    GuiUtil.TEXT_FAINT, false);
        }
        for (int i = 0; i < rows; i++) {
            int idx = bannedPage * rows + i;
            if (idx >= total) {
                break;
            }
            GiveawayState.BannedEntry e = list.get(idx);
            int ry = listTop + i * (ROW_H + ROW_GAP);
            boolean hover = GuiUtil.inRect(mouseX(), mouseY(), contentX, ry, contentX + contentW, ry + ROW_H);
            GuiUtil.roundedRect(context, contentX + 4, ry, contentX + contentW - 4, ry + ROW_H, 6,
                    hover ? 0xFF21262D : 0xFF161B22);
            int ax = contentX + 10;
            int ay = ry + 3;
            drawAvatar(context, tr, e.lower(), e.display(), GiveawayState.fallbackColor(e.lower()), ax, ay, 24);
            String name = GuiUtil.trimToWidth(tr, e.display(), contentW - 220);
            context.drawText(tr, name, ax + 30, ry + 6, GuiUtil.RED, false);
            context.drawText(tr, GuiUtil.trimToWidth(tr, "@" + e.lower(), contentW - 220),
                    ax + 30, ry + 17, GuiUtil.TEXT_FAINT, false);
            context.drawText(tr, "GESPERT", contentX + contentW - 70, ry + 6, GuiUtil.RED, false);
        }
        String foot = total + " gesperrte User";
        context.drawText(tr, foot, contentX + 4, contentY + contentH - 14, GuiUtil.TEXT_FAINT, false);
        if (contentW > 520) {
            String legend = "Haken = entbannen (wieder teilnahmeberechtigt)";
            context.drawText(tr, legend, contentX + contentW - 4 - tr.getWidth(legend),
                    contentY + contentH - 14, GuiUtil.TEXT_FAINT, false);
        }
    }

    private void drawAvatar(DrawContext context, TextRenderer tr, String lower, String name, int color,
                            int x, int y, int size) {
        com.gorgiibot.avatar.AvatarManager.AvatarImage avatar = state.avatars.getAvatar(lower);
        if (avatar != null) {
            GuiUtil.drawTexture(context, avatar.id(), x, y, size, size, avatar.size(), avatar.size());
            GuiUtil.roundedBorder(context, x, y, x + size, y + size, 4, 0x55343945);
        } else {
            GuiUtil.roundedRect(context, x, y, x + size, y + size, 4, color);
            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            GuiUtil.drawCenteredText(context, tr, initial, x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF, true);
        }
    }

    // ---------------- Einstellungen ----------------

    private void drawSettingsView(DrawContext context) {
        TextRenderer tr = this.textRenderer;
        if (channelField != null) {
            context.drawText(tr, "Twitch-Kanal (ohne #):", channelField.getX(), channelField.getY() - 11,
                    GuiUtil.TEXT_DIM, false);
        }
        if (keywordField != null) {
            context.drawText(tr, "Schlüsselwort für Teilnahme:", keywordField.getX(),
                    keywordField.getY() - 11, GuiUtil.TEXT_DIM, false);
        }
        int infoY = contentY + contentH - 52;
        GuiUtil.roundedRect(context, contentX, infoY, contentX + contentW, infoY + 44, 8, GuiUtil.PANEL_DARK);
        GuiUtil.roundedBorder(context, contentX, infoY, contentX + contentW, infoY + 44, 8, GuiUtil.BORDER);
        context.drawText(tr, "Tipp für die hohe Auflösung: GUI-Skala in den Minecraft-Optionen auf", contentX + 10,
                infoY + 6, GuiUtil.TEXT_DIM, false);
        context.drawText(tr, "„Automatisch“ oder 3–4 stellen – das Fenster wirkt dann wie ein echtes Programm.",
                contentX + 10, infoY + 17, GuiUtil.TEXT_DIM, false);
        context.drawText(tr, "Banliste & Einstellungen bleiben in config/gorgiibot.json erhalten.",
                contentX + 10, infoY + 28, GuiUtil.TEXT_FAINT, false);
    }

    // ---------------- Icon-Overlays (nach super.render) ----------------

    private void drawIconOverlays(DrawContext context) {
        // Paginierung
        if (prevButton != null) {
            GuiIcons.arrowLeft(context, prevButton.getX() + 6, prevButton.getY() + 2, GuiUtil.TEXT);
        }
        if (nextButton != null) {
            GuiIcons.arrowRight(context, nextButton.getX() + 6, nextButton.getY() + 2, GuiUtil.TEXT);
        }
        if (bannedPrevButton != null) {
            GuiIcons.arrowLeft(context, bannedPrevButton.getX() + 6, bannedPrevButton.getY() + 2, GuiUtil.TEXT);
        }
        if (bannedNextButton != null) {
            GuiIcons.arrowRight(context, bannedNextButton.getX() + 6, bannedNextButton.getY() + 2, GuiUtil.TEXT);
        }
        // Zeilen-Aktionen
        for (int i = 0; i < rowRemoveButtons.length; i++) {
            if (rowRemoveButtons[i].visible) {
                GuiIcons.remove(context, rowRemoveButtons[i].getX() + 4, rowRemoveButtons[i].getY() + 4,
                        GuiUtil.TEXT);
            }
            if (rowBanButtons[i].visible) {
                GuiIcons.ban(context, rowBanButtons[i].getX() + 4, rowBanButtons[i].getY() + 4, GuiUtil.RED);
            }
        }
        for (int i = 0; i < rowUnbanButtons.length; i++) {
            if (rowUnbanButtons[i].visible) {
                GuiIcons.unban(context, rowUnbanButtons[i].getX() + 4, rowUnbanButtons[i].getY() + 4,
                        GuiUtil.GREEN);
            }
        }
    }

    private int mouseX() {
        return lastMouseX;
    }

    private int mouseY() {
        return lastMouseY;
    }

    private static float easeOutQuint(float t) {
        float u = 1f - t;
        return 1f - u * u * u * u * u;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        wheel.close();
    }
}

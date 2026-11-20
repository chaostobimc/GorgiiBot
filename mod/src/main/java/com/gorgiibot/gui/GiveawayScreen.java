package com.gorgiibot.gui;

import com.gorgiibot.GiveawayState;
import com.gorgiibot.GorgiiBotClient;
import com.gorgiibot.Participant;
import com.gorgiibot.audio.GorgiiSounds;
import com.gorgiibot.avatar.AvatarManager;
import com.gorgiibot.twitch.TwitchIrcClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Haupt-Screen im <b>Vanilla-Minecraft-Stil</b>: oben eine Reihe normaler
 * Minecraft-Buttons als Navigation, darunter die jeweilige Ansicht. Keine
 * eigene „Programm-Optik“ – es sieht aus wie ein ganz normales
 * Minecraft-Menü (Standard-Schrift, Standard-Buttons, flache Listen).
 *
 * <p>Ansichten: Glücksrad, Roulette, Teilnehmer, Gesperrte, Einstellungen.
 * Teilnehmer- und Banliste sind paginiert und haben Suche + Sortierung.
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
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 2;
    private static final int ROW_PITCH = ROW_H + ROW_GAP;

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

    // Layout
    private int contentX, contentY, contentW, contentH;
    private int panelX, panelW, stageW, stageH;
    private int listTop, listBottom, rows;

    private ButtonWidget[] navButtons = new ButtonWidget[0];
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
        this.view = autoStart ? (GorgiiBotClient.state.uiTab == 1 ? View.ROULETTE : View.WHEEL) : lastView;
    }

    /** Öffnet direkt eine bestimmte Ansicht (z. B. {@code /giveaway teilnehmer}). */
    public GiveawayScreen(View view) {
        super(Text.literal("GorgiiBot Giveaway"));
        this.view = view == null ? View.WHEEL : view;
        lastView = this.view;
    }

    // ---------------- Layout ----------------

    private void computeLayout() {
        int margin = 6;
        contentX = margin;
        contentW = this.width - margin * 2;
        contentY = 42;
        contentH = Math.max(120, this.height - contentY - margin);
        panelW = Math.max(120, Math.min(180, this.width / 5));
        stageW = Math.max(120, contentW - panelW - 6);
        panelX = contentX + stageW + 6;
        stageH = contentH;
        listTop = contentY + 24;
        listBottom = contentY + contentH - 24;
        rows = Math.max(3, Math.min(16, Math.max(1, (listBottom - listTop) / ROW_PITCH)));
    }

    @Override
    protected void init() {
        computeLayout();
        this.clearChildren();

        navButtons = new ButtonWidget[0];
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

        // „Fertig“ oben rechts – wie in Vanilla-Menüs üblich
        closeButton = ButtonWidget.builder(Text.literal("Fertig"), b -> closeScreen())
                .dimensions(this.width - 6 - 74, 4, 74, 20).build();
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

    /** Navigations-Reihe (normale Minecraft-Buttons, aktive Ansicht gesperrt). */
    private void buildNav() {
        View[] views = View.values();
        navButtons = new ButtonWidget[views.length];
        int gap = 4;
        int available = this.width - 12 - 78; // rechts Platz für „Fertig“
        int bw = Math.max(56, Math.min(150, (available - gap * (views.length - 1)) / views.length));
        int total = bw * views.length + gap * (views.length - 1);
        int x = 6 + Math.max(0, (available - total) / 2);
        for (int i = 0; i < views.length; i++) {
            View target = views[i];
            navButtons[i] = ButtonWidget.builder(Text.literal(""), b -> switchView(target))
                    .dimensions(x, 20, bw, 20).build();
            this.addDrawableChild(navButtons[i]);
            x += bw + gap;
        }
    }

    private void buildStageControls() {
        int bx = panelX + 4;
        int bw = panelW - 8;
        int by = contentY + contentH - 24;
        resetButton = ButtonWidget.builder(Text.literal("Zurücksetzen"), b -> reset())
                .dimensions(bx, by, bw, 20).build();
        this.addDrawableChild(resetButton);
        by -= 24;
        startButton = ButtonWidget.builder(Text.literal("Start"), b -> startSpin())
                .dimensions(bx, by, bw, 20).build();
        this.addDrawableChild(startButton);
        by -= 24;
        connectButton = ButtonWidget.builder(Text.literal("Verbinden"), b -> toggleConnect())
                .dimensions(bx, by, bw, 20).build();
        this.addDrawableChild(connectButton);
        by -= 24;
        gotoParticipantsButton = ButtonWidget.builder(Text.literal("Teilnehmer"), b -> switchView(View.PARTICIPANTS))
                .dimensions(bx, by, bw, 20).build();
        this.addDrawableChild(gotoParticipantsButton);
    }

    private void buildParticipants() {
        int ty = contentY + 2;
        int searchW = Math.max(80, Math.min(180, contentW - 300));
        searchField = new TextFieldWidget(this.textRenderer, contentX, ty, searchW, 20,
                Text.literal("Suche"));
        searchField.setMaxLength(32);
        searchField.setText(searchText);
        searchField.setPlaceholder(Text.literal("Suchen …"));
        searchField.setChangedListener(text -> {
            searchText = text;
            page = 0;
        });
        this.addDrawableChild(searchField);

        sortButton = ButtonWidget.builder(Text.literal(""), b -> {
            sortMode = nextSort(sortMode);
            page = 0;
            refreshButtons();
        }).dimensions(contentX + searchW + 4, ty, 132, 20).build();
        this.addDrawableChild(sortButton);

        // Paginierung rechts
        prevButton = ButtonWidget.builder(Text.literal("<"), b -> {
            if (page > 0) {
                page--;
            }
            refreshButtons();
        }).dimensions(contentX + contentW - 44, ty, 20, 20).build();
        this.addDrawableChild(prevButton);
        nextButton = ButtonWidget.builder(Text.literal(">"), b -> {
            page++;
            refreshButtons();
        }).dimensions(contentX + contentW - 20, ty, 20, 20).build();
        this.addDrawableChild(nextButton);

        // Fußzeile: manuell hinzufügen + Test-User + Leeren
        int by = contentY + contentH - 20;
        int clearW = 62;
        int testW = 76;
        int addW = 84;
        int clearX = contentX + contentW - clearW;
        int testX = clearX - 4 - testW;
        int addX = testX - 4 - addW;
        int fieldW = Math.max(50, addX - contentX - 6);
        addField = new TextFieldWidget(this.textRenderer, contentX, by, fieldW, 20,
                Text.literal("Hinzufügen"));
        addField.setMaxLength(32);
        addField.setText(addText);
        addField.setPlaceholder(Text.literal("Name …"));
        addField.setChangedListener(text -> addText = text);
        this.addDrawableChild(addField);

        addButton = ButtonWidget.builder(Text.literal("Hinzufügen"), b -> {
            if (state.addParticipant(addText)) {
                addText = "";
                if (addField != null) {
                    addField.setText("");
                }
            }
            refreshButtons();
        }).dimensions(addX, by, addW, 20).build();
        this.addDrawableChild(addButton);
        testButton = ButtonWidget.builder(Text.literal("Test-User"), b -> {
            state.addTestParticipants();
            refreshButtons();
        }).dimensions(testX, by, testW, 20).build();
        this.addDrawableChild(testButton);
        clearButton = ButtonWidget.builder(Text.literal("Leeren"), b -> {
            state.clear();
            page = 0;
            refreshButtons();
        }).dimensions(clearX, by, clearW, 20).build();
        this.addDrawableChild(clearButton);

        // Zeilen-Aktionen
        rowRemoveButtons = new ButtonWidget[rows];
        rowBanButtons = new ButtonWidget[rows];
        int banW = 40;
        int banX = contentX + contentW - 2 - banW;
        int removeX = banX - 4 - 18;
        for (int i = 0; i < rows; i++) {
            int ry = listTop + i * ROW_PITCH;
            int slot = i;
            rowRemoveButtons[i] = ButtonWidget.builder(
                            Text.literal("X").formatted(Formatting.RED), b -> {
                                Participant p = participantAtSlot(slot);
                                if (p != null) {
                                    state.remove(p.lower);
                                    refreshButtons();
                                }
                            }).dimensions(removeX, ry + 2, 18, 18).build();
            this.addDrawableChild(rowRemoveButtons[i]);
            rowBanButtons[i] = ButtonWidget.builder(
                            Text.literal("Bann").formatted(Formatting.RED), b -> {
                                Participant p = participantAtSlot(slot);
                                if (p != null) {
                                    state.ban(p.lower, p.name);
                                    refreshButtons();
                                }
                            }).dimensions(banX, ry + 2, banW, 18).build();
            this.addDrawableChild(rowBanButtons[i]);
        }
    }

    private void buildBanned() {
        int ty = contentY + 2;
        int searchW = Math.max(80, Math.min(180, contentW - 300));
        bannedSearchField = new TextFieldWidget(this.textRenderer, contentX, ty, searchW, 20,
                Text.literal("Suche"));
        bannedSearchField.setMaxLength(32);
        bannedSearchField.setText(bannedSearchText);
        bannedSearchField.setPlaceholder(Text.literal("Suchen …"));
        bannedSearchField.setChangedListener(text -> {
            bannedSearchText = text;
            bannedPage = 0;
        });
        this.addDrawableChild(bannedSearchField);

        bannedSortButton = ButtonWidget.builder(Text.literal(""), b -> {
            bannedSortMode = nextSort(bannedSortMode);
            bannedPage = 0;
            refreshButtons();
        }).dimensions(contentX + searchW + 4, ty, 132, 20).build();
        this.addDrawableChild(bannedSortButton);

        bannedPrevButton = ButtonWidget.builder(Text.literal("<"), b -> {
            if (bannedPage > 0) {
                bannedPage--;
            }
            refreshButtons();
        }).dimensions(contentX + contentW - 44, ty, 20, 20).build();
        this.addDrawableChild(bannedPrevButton);
        bannedNextButton = ButtonWidget.builder(Text.literal(">"), b -> {
            bannedPage++;
            refreshButtons();
        }).dimensions(contentX + contentW - 20, ty, 20, 20).build();
        this.addDrawableChild(bannedNextButton);

        rowUnbanButtons = new ButtonWidget[rows];
        int unbanW = 42;
        int unbanX = contentX + contentW - 2 - unbanW;
        for (int i = 0; i < rows; i++) {
            int ry = listTop + i * ROW_PITCH;
            int slot = i;
            rowUnbanButtons[i] = ButtonWidget.builder(
                            Text.literal("Frei").formatted(Formatting.GREEN), b -> {
                                GiveawayState.BannedEntry e = bannedAtSlot(slot);
                                if (e != null) {
                                    state.unban(e.lower());
                                    refreshButtons();
                                }
                            }).dimensions(unbanX, ry + 2, unbanW, 18).build();
            this.addDrawableChild(rowUnbanButtons[i]);
        }
    }

    private void buildSettings() {
        int fieldW = Math.max(80, Math.min(200, contentW / 3));
        int fx = contentX + 4;
        int fy = contentY + 14;
        channelField = new TextFieldWidget(this.textRenderer, fx, fy, fieldW, 20,
                Text.literal("Twitch-Kanal"));
        channelField.setMaxLength(32);
        channelField.setText(state.config.channel);
        channelField.setPlaceholder(Text.literal("kanalname"));
        channelField.setChangedListener(text -> state.config.channel = text);
        this.addDrawableChild(channelField);

        fy += 34;
        keywordField = new TextFieldWidget(this.textRenderer, fx, fy, fieldW, 20,
                Text.literal("Schlüsselwort"));
        keywordField.setMaxLength(32);
        keywordField.setText(state.config.keyword);
        keywordField.setPlaceholder(Text.literal("!teilnahme"));
        keywordField.setChangedListener(text -> state.config.keyword = text.isEmpty() ? "!teilnahme" : text);
        this.addDrawableChild(keywordField);

        int colW = Math.max(110, Math.min(180, (contentW - 24) / 2));
        int leftX = fx;
        int rightX = fx + colW + 10;
        int step = 22;
        int ry = fy + 32;
        subOnlyButton = toggleButton(leftX, ry, colW, () -> state.config.requireSubscriber,
                v -> state.config.requireSubscriber = v);
        ry += step;
        subLuckButton = ButtonWidget.builder(Text.literal(""), b -> {
            state.config.subLuck = state.config.subLuck % 5 + 1;
            refreshButtons();
        }).dimensions(leftX, ry, colW, 20).build();
        this.addDrawableChild(subLuckButton);
        ry += step;
        claimButton = ButtonWidget.builder(Text.literal(""), b -> {
            state.config.claimSeconds = nextClaimStep(state.config.claimSeconds);
            refreshButtons();
        }).dimensions(leftX, ry, colW, 20).build();
        this.addDrawableChild(claimButton);
        ry += step;
        avatarsButton = toggleButton(leftX, ry, colW, () -> state.config.avatars,
                v -> state.config.avatars = v);

        int ryB = fy + 32;
        removeConfirmedButton = toggleButton(rightX, ryB, colW, () -> state.config.autoRemoveConfirmed,
                v -> state.config.autoRemoveConfirmed = v);
        ryB += step;
        removeTimeoutButton = toggleButton(rightX, ryB, colW, () -> state.config.autoRemoveTimeout,
                v -> state.config.autoRemoveTimeout = v);
        ryB += step;
        soundButton = toggleButton(rightX, ryB, colW, () -> state.config.sound,
                v -> state.config.sound = v);
        ryB += step;
        confettiButton = toggleButton(rightX, ryB, colW, () -> state.config.confetti,
                v -> state.config.confetti = v);

        int bottom = Math.max(ry, ryB) + step;
        doneButton = ButtonWidget.builder(Text.literal("Speichern"), b -> {
            saveSettings();
            refreshButtons();
        }).dimensions(leftX, bottom, fieldW, 20).build();
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

    // ---------------- Aktionen ----------------

    private void closeScreen() {
        saveSettings();
        if (client != null) {
            client.setScreen(null);
        }
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
        // Alle Avatare des Pools proaktiv laden (nicht nur sichtbare).
        state.avatars.prefetchParticipants(pool);
        Participant winner = state.pickWinner(pool);
        if (winner == null) {
            return;
        }
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

    private void showWinner(Participant winner) {
        state.startClaim(winner, nextIsReroll);
        nextIsReroll = false;
        if (client != null) {
            client.setScreen(new WinnerScreen(winner));
        }
    }

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

        View[] views = View.values();
        for (int i = 0; i < navButtons.length && i < views.length; i++) {
            ButtonWidget b = navButtons[i];
            if (b == null) {
                continue;
            }
            boolean active = views[i] == view;
            b.setMessage(Text.literal(navLabel(views[i])));
            b.active = !busy && !active;
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
            sortButton.setMessage(Text.literal(sortLabel(sortMode)));
        }
        if (bannedSortButton != null) {
            bannedSortButton.setMessage(Text.literal(sortLabel(bannedSortMode)));
        }

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

    private String navLabel(View v) {
        return switch (v) {
            case WHEEL -> "Glücksrad";
            case ROULETTE -> "Roulette";
            case PARTICIPANTS -> "Teilnehmer " + state.count();
            case BANNED -> "Gesperrt " + state.bannedCount();
            case SETTINGS -> "Einstellungen";
        };
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
            case NEWEST -> "Neueste";
            case OLDEST -> "Älteste";
            case AZ -> "A-Z";
            case ZA -> "Z-A";
        };
    }

    private static String onOff(boolean value) {
        return value ? "AN" : "AUS";
    }

    private static String removeLabel(boolean value) {
        return value ? "entfernen" : "behalten";
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

    // ---------------- Rendering ----------------

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

        List<Participant> pool = wheelSpinning ? wheelPool : state.snapshot();
        if (!wheelSpinning && pool.size() > 1) {
            wheelRotation += IDLE_SPEED * dt;
            if (wheelRotation > TWO_PI) {
                wheelRotation -= (float) TWO_PI;
            }
        }
        roulette.tick(pool, state.getVersion(), stageW, dt);
        updateWheelSpin();

        if (!wheelSpinning && !roulette.isSpinning() && state.isRerollDue()) {
            rerollNow();
            if (wheelSpinning || roulette.isSpinning()) {
                pool = wheelSpinning ? wheelPool : state.snapshot();
            }
        }

        if (client.currentScreen != this) {
            return;
        }
        state.avatars.prefetchParticipants(pool);
        if (view == View.BANNED) {
            List<String> lowers = new ArrayList<>();
            for (GiveawayState.BannedEntry e : filteredBanned()) {
                lowers.add(e.lower());
            }
            state.avatars.prefetch(lowers);
        }
        refreshButtons();

        // Kein renderBackground(): Blur ist nur 1× pro Frame erlaubt.
        // Stattdessen der typische Vanilla-Verlauf von dunkel nach dunkler.
        context.fillGradient(0, 0, this.width, this.height, GuiUtil.BACKDROP, 0xF0101010);
        GuiUtil.drawCenteredText(context, this.textRenderer, "GorgiiBot Giveaway",
                this.width / 2, 6, GuiUtil.TEXT, true);

        switch (view) {
            case WHEEL -> drawStage(context, pool, true);
            case ROULETTE -> drawStage(context, pool, false);
            case PARTICIPANTS -> drawParticipants(context);
            case BANNED -> drawBanned(context);
            case SETTINGS -> drawSettings(context);
        }

        if (client.currentScreen != this) {
            return;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    // ---- Bühne (Rad / Roulette) ----

    private void drawStage(DrawContext context, List<Participant> pool, boolean isWheel) {
        TextRenderer tr = this.textRenderer;
        int top = contentY;
        int height = stageH;
        if (state.isClaimActive()) {
            int bannerH = 26;
            drawClaimBanner(context, contentX, top, stageW, bannerH);
            top += bannerH + 4;
            height -= bannerH + 4;
        }
        GuiUtil.panel(context, contentX, top, contentX + stageW, top + height,
                GuiUtil.PANEL, GuiUtil.BORDER);

        if (isWheel) {
            int version = wheelSpinning ? wheelFrozenVersion : state.getVersion();
            wheel.ensure(pool, version);
            int size = Math.max(60, Math.min(stageW, height) - 28);
            int cx = contentX + stageW / 2;
            int cy = top + height / 2;
            wheel.draw(context, tr, pool, cx, cy, size, wheelRotation);
            // Zeiger oben
            int py = cy - size / 2 - 2;
            for (int k = 0; k < 6; k++) {
                int barW = 18 - k * 3;
                context.fill(cx - barW / 2, py + k * 3, cx + (barW + 1) / 2, py + 3 + k * 3, GuiUtil.GOLD);
            }
            if (pool.isEmpty()) {
                GuiUtil.drawCenteredText(context, tr, "Keine Teilnehmer", cx, cy - 4, GuiUtil.TEXT_DIM, true);
                GuiUtil.drawCenteredText(context, tr, "Chat: " + state.config.keyword, cx, cy + 8,
                        GuiUtil.TEXT_FAINT, true);
            }
        } else {
            int stripH = RouletteView.CARD_HEIGHT + 24;
            int stripY = top + (height - stripH) / 2;
            roulette.draw(context, tr, state.avatars, contentX + 2, stripY, stageW - 4, stripH);
            if (pool.isEmpty()) {
                GuiUtil.drawCenteredText(context, tr, "Keine Teilnehmer - Chat: " + state.config.keyword,
                        contentX + stageW / 2, stripY + stripH - 8, GuiUtil.TEXT_FAINT, true);
            }
        }
        drawStagePanel(context, tr);
    }

    /** Rechtes Infofeld: Verbindung, Teilnehmer, Gewinner, Wort. */
    private void drawStagePanel(DrawContext context, TextRenderer tr) {
        GuiUtil.panel(context, panelX, contentY, panelX + panelW, contentY + stageH,
                GuiUtil.PANEL, GuiUtil.BORDER);
        int tx = panelX + 6;
        int maxW = panelW - 12;
        int ty = contentY + 6;

        TwitchIrcClient twitch = state.twitch;
        TwitchIrcClient.Status status = twitch.status();
        int color = status == TwitchIrcClient.Status.CONNECTED ? GuiUtil.GREEN
                : status == TwitchIrcClient.Status.CONNECTING ? GuiUtil.YELLOW
                : status == TwitchIrcClient.Status.ERROR ? GuiUtil.RED : GuiUtil.TEXT_FAINT;
        String label = status == TwitchIrcClient.Status.CONNECTED ? "Verbunden"
                : status == TwitchIrcClient.Status.CONNECTING ? "Verbinde ..."
                : status == TwitchIrcClient.Status.ERROR ? "Fehler" : "Getrennt";
        context.drawText(tr, "Twitch", tx, ty, GuiUtil.TEXT_DIM, true);
        ty += 11;
        context.drawText(tr, label, tx, ty, color, true);
        ty += 11;
        String detail = twitch.statusDetail();
        if (detail.isEmpty() && status == TwitchIrcClient.Status.DISCONNECTED) {
            detail = state.config.channel.isEmpty() ? "kein Kanal" : "#" + state.config.channel;
        }
        context.drawText(tr, GuiUtil.trimToWidth(tr, detail, maxW), tx, ty, GuiUtil.TEXT_FAINT, true);
        ty += 14;

        context.drawText(tr, "Teilnehmer", tx, ty, GuiUtil.TEXT_DIM, true);
        ty += 11;
        context.drawText(tr, String.valueOf(state.count()), tx, ty, GuiUtil.TEXT, true);
        ty += 14;

        context.drawText(tr, "Wort", tx, ty, GuiUtil.TEXT_DIM, true);
        ty += 11;
        context.drawText(tr, GuiUtil.trimToWidth(tr, state.config.keyword, maxW), tx, ty,
                GuiUtil.TEXT, true);
        ty += 14;

        Participant last = state.getLastWinner();
        if (last != null && ty < contentY + stageH - 104) {
            context.drawText(tr, "Letzter Gewinner", tx, ty, GuiUtil.TEXT_DIM, true);
            ty += 11;
            context.drawText(tr, GuiUtil.trimToWidth(tr, last.name, maxW), tx, ty, GuiUtil.GOLD, true);
        }
    }

    /** Bestätigungs-Banner: Status links, Restzeit rechts, Balken unten. */
    private void drawClaimBanner(DrawContext context, int x, int y, int w, int h) {
        TextRenderer tr = this.textRenderer;
        Participant winner = state.getClaimWinner();
        if (winner == null) {
            return;
        }
        GuiUtil.panel(context, x, y, x + w, y + h, GuiUtil.PANEL_DARK, GuiUtil.BORDER);
        String statusText;
        int statusColor;
        if (state.isClaimed()) {
            statusText = "Bestätigt";
            statusColor = GuiUtil.GREEN;
        } else if (state.isClaimExpired()) {
            statusText = "Zeit abgelaufen - Reroll ...";
            statusColor = GuiUtil.RED;
        } else {
            statusText = "Warte auf Bestätigung";
            statusColor = GuiUtil.YELLOW;
        }
        String left = (state.isRerollClaim() ? "Reroll: " : "") + statusText + " - @" + winner.name;
        context.drawText(tr, GuiUtil.trimToWidth(tr, left, w - 70), x + 6, y + 4, statusColor, true);
        long remain = state.claimRemainMs();
        long total = state.claimTotalMs();
        String right = state.isClaimPending() ? ((remain + 999) / 1000) + " s"
                : state.isClaimed() ? "OK" : "0 s";
        GuiUtil.drawRightText(context, tr, right, x + w - 6, y + 4, GuiUtil.TEXT_DIM, true);

        int barW = w - 12;
        int filled = total <= 0 ? 0 : (int) (barW * Math.max(0, Math.min(1, remain / (double) total)));
        int barColor = state.isClaimed() ? GuiUtil.GREEN : remain < 10_000 ? GuiUtil.RED : GuiUtil.YELLOW;
        context.fill(x + 6, y + h - 8, x + 6 + barW, y + h - 4, 0xFF202020);
        if (filled > 1) {
            context.fill(x + 6, y + h - 8, x + 6 + filled, y + h - 4, barColor);
        }
    }

    // ---- Teilnehmerliste ----

    private void drawParticipants(DrawContext context) {
        TextRenderer tr = this.textRenderer;
        List<Participant> list = filteredParticipants();
        int total = list.size();
        int maxPage = Math.max(0, (total - 1) / Math.max(1, rows));
        if (page > maxPage) {
            page = maxPage;
        }
        String pageLabel = total == 0 ? "0 / 0" : (page + 1) + " / " + (maxPage + 1);
        GuiUtil.drawRightText(context, tr, pageLabel, contentX + contentW - 48, contentY + 7,
                GuiUtil.TEXT_DIM, true);

        int bottom = contentY + contentH - 24;
        GuiUtil.panel(context, contentX, listTop - 2, contentX + contentW, bottom + 2,
                GuiUtil.PANEL, GuiUtil.BORDER);

        if (total == 0) {
            String msg = searchText.isEmpty() ? "Noch keine Teilnehmer - Chat: " + state.config.keyword
                    : "Keine Treffer";
            GuiUtil.drawCenteredText(context, tr, msg, contentX + contentW / 2, listTop + 8,
                    GuiUtil.TEXT_FAINT, true);
        }

        int banW = 40;
        int banX = contentX + contentW - 2 - banW;
        int removeX = banX - 4 - 18;
        int textW = removeX - (contentX + 22) - 6;

        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= total) {
                break;
            }
            Participant p = list.get(idx);
            int ry = listTop + i * ROW_PITCH;
            boolean hover = GuiUtil.inRect(mouseX(), mouseY(), contentX, ry,
                    contentX + contentW, ry + ROW_H);
            if (hover) {
                context.fill(contentX + 1, ry, contentX + contentW - 1, ry + ROW_H, GuiUtil.HOVER);
            }
            context.fill(contentX + 1, ry + ROW_H, contentX + contentW - 1, ry + ROW_H + 1,
                    GuiUtil.SEPARATOR);

            drawAvatar(context, tr, p.lower, p.name, p.color, contentX + 3, ry + 3, 16);
            String badge = p.boosted ? " [SUB]" : "";
            String name = p.name + badge;
            context.drawText(tr, GuiUtil.trimToWidth(tr, name, textW), contentX + 22, ry + 3,
                    p.color, true);
            String sub = "@" + p.lower + " - " + timeAgo(p.joinedAt);
            context.drawText(tr, GuiUtil.trimToWidth(tr, sub, textW), contentX + 22, ry + 12,
                    GuiUtil.TEXT_FAINT, true);
        }

        String foot = total + " Teilnehmer" + (searchText.isEmpty() ? "" : " (gefiltert)");
        context.drawText(tr, foot, contentX, contentY + contentH - 14, GuiUtil.TEXT_FAINT, true);
    }

    // ---- Banliste ----

    private void drawBanned(DrawContext context) {
        TextRenderer tr = this.textRenderer;
        List<GiveawayState.BannedEntry> list = filteredBanned();
        int total = list.size();
        int maxPage = Math.max(0, (total - 1) / Math.max(1, rows));
        if (bannedPage > maxPage) {
            bannedPage = maxPage;
        }
        String pageLabel = total == 0 ? "0 / 0" : (bannedPage + 1) + " / " + (maxPage + 1);
        GuiUtil.drawRightText(context, tr, pageLabel, contentX + contentW - 48, contentY + 7,
                GuiUtil.TEXT_DIM, true);

        int bottom = contentY + contentH - 24;
        GuiUtil.panel(context, contentX, listTop - 2, contentX + contentW, bottom + 2,
                GuiUtil.PANEL, GuiUtil.BORDER);

        if (total == 0) {
            String msg = bannedSearchText.isEmpty() ? "Keine gesperrten User" : "Keine Treffer";
            GuiUtil.drawCenteredText(context, tr, msg, contentX + contentW / 2, listTop + 8,
                    GuiUtil.TEXT_FAINT, true);
        }

        int unbanW = 42;
        int unbanX = contentX + contentW - 2 - unbanW;
        int textW = unbanX - (contentX + 22) - 6;

        for (int i = 0; i < rows; i++) {
            int idx = bannedPage * rows + i;
            if (idx >= total) {
                break;
            }
            GiveawayState.BannedEntry e = list.get(idx);
            int ry = listTop + i * ROW_PITCH;
            boolean hover = GuiUtil.inRect(mouseX(), mouseY(), contentX, ry,
                    contentX + contentW, ry + ROW_H);
            if (hover) {
                context.fill(contentX + 1, ry, contentX + contentW - 1, ry + ROW_H, GuiUtil.HOVER);
            }
            context.fill(contentX + 1, ry + ROW_H, contentX + contentW - 1, ry + ROW_H + 1,
                    GuiUtil.SEPARATOR);

            int color = GiveawayState.fallbackColor(e.lower());
            drawAvatar(context, tr, e.lower(), e.display(), color, contentX + 3, ry + 3, 16);
            context.drawText(tr, GuiUtil.trimToWidth(tr, e.display(), textW), contentX + 22, ry + 3,
                    GuiUtil.RED, true);
            context.drawText(tr, GuiUtil.trimToWidth(tr, "@" + e.lower(), textW), contentX + 22,
                    ry + 12, GuiUtil.TEXT_FAINT, true);
        }

        String foot = total + " gesperrt";
        context.drawText(tr, foot, contentX, contentY + contentH - 14, GuiUtil.TEXT_FAINT, true);
    }

    // ---- Einstellungen ----

    private void drawSettings(DrawContext context) {
        TextRenderer tr = this.textRenderer;
        if (channelField != null) {
            context.drawText(tr, "Twitch-Kanal (ohne #):", channelField.getX(),
                    channelField.getY() - 10, GuiUtil.TEXT_DIM, true);
        }
        if (keywordField != null) {
            context.drawText(tr, "Schlüsselwort für Teilnahme:", keywordField.getX(),
                    keywordField.getY() - 10, GuiUtil.TEXT_DIM, true);
        }
        int infoY = contentY + contentH - 12;
        String info = "Gespeichert in config/gorgiibot.json - Gesperrte können erst nach dem Entbannen wieder teilnehmen.";
        context.drawText(tr, GuiUtil.trimToWidth(tr, info, contentW - 8), contentX + 4, infoY,
                GuiUtil.TEXT_FAINT, true);
    }

    private void drawAvatar(DrawContext context, TextRenderer tr, String lower, String name, int color,
                            int x, int y, int size) {
        AvatarManager.AvatarImage avatar = state.avatars.getAvatar(lower);
        if (avatar != null) {
            GuiUtil.drawTexture(context, avatar.id(), x, y, size, size, avatar.size(), avatar.size());
        } else {
            context.fill(x, y, x + size, y + size, color);
            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            GuiUtil.drawCenteredText(context, tr, initial, x + size / 2, y + (size - 8) / 2,
                    0xFFFFFFFF, true);
        }
        GuiUtil.fillBorder(context, x, y, x + size, y + size, 0xFF202020);
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

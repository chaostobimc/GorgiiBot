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
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Haupt-Screen: Tabs für Glücksrad und Roulette plus Seitenpanel mit
 * Twitch-Status, Teilnehmerzahl, Bestätigungs-Countdown und Aktionen.
 */
public class GiveawayScreen extends Screen {
    private enum Tab {
        WHEEL, ROULETTE
    }

    private static final long WHEEL_MILLIS = 7000;
    private static final int PANEL_WIDTH = 190;
    /** Idle-Drehung des Rads (rad/s) – identisch zur Web-App. */
    private static final float IDLE_SPEED = 0.22f;
    private static final double TWO_PI = Math.PI * 2.0;

    private final GiveawayState state = GorgiiBotClient.state;
    private Tab tab;
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

    private ButtonWidget tabWheelButton;
    private ButtonWidget tabRouletteButton;
    private ButtonWidget connectButton;
    private ButtonWidget startButton;
    private ButtonWidget resetButton;
    private ButtonWidget settingsButton;

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
        this.tab = GorgiiBotClient.state.uiTab == 1 ? Tab.ROULETTE : Tab.WHEEL;
    }

    @Override
    protected void init() {
        int panelX = this.width - PANEL_WIDTH - 12;
        tabWheelButton = ButtonWidget.builder(Text.literal("Glücksrad"), b -> switchTab(Tab.WHEEL))
                .dimensions(12, 10, 110, 20).build();
        tabRouletteButton = ButtonWidget.builder(Text.literal("Roulette"), b -> switchTab(Tab.ROULETTE))
                .dimensions(126, 10, 110, 20).build();
        this.addDrawableChild(tabWheelButton);
        this.addDrawableChild(tabRouletteButton);

        int bx = panelX + 12;
        int bw = PANEL_WIDTH - 24;
        int by = 118;
        connectButton = ButtonWidget.builder(Text.literal("Verbinden"), b -> toggleConnect())
                .dimensions(bx, by, bw, 20).build();
        by += 24;
        startButton = ButtonWidget.builder(Text.literal("Start"), b -> startSpin())
                .dimensions(bx, by, bw, 20).build();
        by += 24;
        resetButton = ButtonWidget.builder(Text.literal("Zurücksetzen"), b -> reset())
                .dimensions(bx, by, bw, 20).build();
        by += 24;
        settingsButton = ButtonWidget.builder(Text.literal("Einstellungen"), b -> openSettings())
                .dimensions(bx, by, bw, 20).build();
        this.addDrawableChild(connectButton);
        this.addDrawableChild(startButton);
        this.addDrawableChild(resetButton);
        this.addDrawableChild(settingsButton);
        refreshButtons();
    }

    private void switchTab(Tab next) {
        tab = next;
        state.uiTab = (next == Tab.ROULETTE) ? 1 : 0;
        refreshButtons();
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
        Participant winner = state.pickWinner(pool);
        if (winner == null) {
            return;
        }
        // Alte Bestätigungsphase beenden (z. B. nach manuellem „Neu ziehen“)
        state.endClaim();
        nextIsReroll = reroll;
        if (tab == Tab.WHEEL) {
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

    private void openSettings() {
        if (client != null) {
            client.setScreen(new SettingsScreen());
        }
    }

    private void refreshButtons() {
        if (startButton == null) {
            return;
        }
        boolean busy = wheelSpinning || roulette.isSpinning();
        boolean pending = state.isClaimPending();
        boolean connected = state.twitch.status() == TwitchIrcClient.Status.CONNECTED
                || state.twitch.status() == TwitchIrcClient.Status.CONNECTING;
        connectButton.setMessage(Text.literal(connected ? "Trennen" : "Verbinden"));
        startButton.setMessage(Text.literal(busy ? "Läuft …" : pending ? "Warte …" : "Start"));
        startButton.active = !busy && !pending && state.count() > 0;
        resetButton.active = !busy;
        tabWheelButton.active = !busy;
        tabRouletteButton.active = !busy;
        tabWheelButton.setMessage(Text.literal((tab == Tab.WHEEL ? "> " : "") + "Glücksrad"));
        tabRouletteButton.setMessage(Text.literal((tab == Tab.ROULETTE ? "> " : "") + "Roulette"));
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
        long now = System.currentTimeMillis();
        float dt = lastFrameAt == 0 ? 0.016f : Math.min(0.1f, (now - lastFrameAt) / 1000f);
        lastFrameAt = now;

        int panelX = this.width - PANEL_WIDTH - 12;
        int mainW = panelX - 24;

        if (autoStart) {
            autoStart = false;
            startSpin(nextIsReroll);
        }

        // Teilnehmer + Idle-Animationen
        List<Participant> pool = wheelSpinning ? wheelPool : state.snapshot();
        if (!wheelSpinning && pool.size() > 1) {
            // AFK-Animation: langsame Endlos-Drehung wie auf der Website
            wheelRotation += IDLE_SPEED * dt;
            if (wheelRotation > TWO_PI) {
                wheelRotation -= (float) TWO_PI;
            }
        }
        roulette.tick(pool, state.getVersion(), mainW, dt);
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
        refreshButtons();
        // Hinweis: kein renderBackground() — das wendet Blur an, der nur einmal
        // pro Frame erlaubt ist (Vanilla/andere Mods nutzen ihn bereits).
        context.fill(0, 0, this.width, this.height, 0xB0101015);

        TextRenderer tr = this.textRenderer;

        context.fill(panelX, 36, panelX + PANEL_WIDTH, this.height - 12, 0xDD14161B);
        GuiUtil.fillBorder(context, panelX, 36, panelX + PANEL_WIDTH, this.height - 12, 0xFF2A2E36);
        drawSidePanel(context, tr, panelX);

        if (tab == Tab.WHEEL) {
            drawWheelTab(context, tr, pool, 12, 36, mainW, this.height - 48);
        } else {
            drawRouletteTab(context, tr, pool, 12, 36, mainW, this.height - 48);
        }
        if (client.currentScreen != this) {
            return;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawSidePanel(DrawContext context, TextRenderer tr, int panelX) {
        int tx = panelX + 12;
        int ty = 44;
        GuiUtil.drawCenteredText(context, tr, "GorgiiBot", panelX + PANEL_WIDTH / 2, ty, 0xFFFFFFFF,
                false);
        ty += 13;
        TwitchIrcClient twitch = state.twitch;
        TwitchIrcClient.Status status = twitch.status();
        int dot = status == TwitchIrcClient.Status.CONNECTED ? 0xFF3FB950
                : status == TwitchIrcClient.Status.CONNECTING ? 0xFFFFC94D
                : status == TwitchIrcClient.Status.ERROR ? 0xFFF85149 : 0xFF5B6470;
        context.fill(tx, ty, tx + 6, ty + 6, dot);
        String label = status == TwitchIrcClient.Status.CONNECTED ? "Verbunden"
                : status == TwitchIrcClient.Status.CONNECTING ? "Verbinde …"
                : status == TwitchIrcClient.Status.ERROR ? "Fehler" : "Getrennt";
        context.drawText(tr, label, tx + 10, ty - 1, 0xFFE6E6E6, false);
        ty += 12;
        String detail = twitch.statusDetail();
        if (detail.isEmpty() && status == TwitchIrcClient.Status.DISCONNECTED) {
            detail = state.config.channel.isEmpty() ? "Kein Kanal gesetzt" : "#" + state.config.channel;
        }
        if (!detail.isEmpty()) {
            context.drawText(tr, GuiUtil.trimToWidth(tr, detail, PANEL_WIDTH - 24), tx, ty, 0xFF8B949E,
                    false);
            ty += 12;
        }
        ty += 4;
        context.drawText(tr, "Teilnehmer: " + state.count(), tx, ty, 0xFFE6E6E6, false);
        ty += 12;
        context.drawText(tr, GuiUtil.trimToWidth(tr, "Wort: " + state.config.keyword, PANEL_WIDTH - 24),
                tx, ty, 0xFF8B949E, false);
        ty += 16;

        drawClaimPanel(context, tr, tx, ty, PANEL_WIDTH - 24);

        int hintY = this.height - 36;
        context.drawText(tr, "G: Menü öffnen", tx, hintY, 0xFF5B6470, false);
        context.drawText(tr, "/giveaway im Chat", tx, hintY + 11, 0xFF5B6470, false);
    }

    /** Bestätigungs-Phase: Countdown, Gewinner und Status im Seitenpanel. */
    private void drawClaimPanel(DrawContext context, TextRenderer tr, int tx, int ty, int width) {
        if (!state.isClaimActive()) {
            return;
        }
        Participant winner = state.getClaimWinner();
        if (winner == null) {
            return;
        }
        int barW = width;
        context.fill(tx, ty, tx + barW, ty + 1, 0xFF2A2E36);
        ty += 6;
        int statusColor;
        String statusText;
        if (state.isClaimed()) {
            statusColor = 0xFF3FB950;
            statusText = "Bestätigt";
        } else if (state.isClaimExpired()) {
            statusColor = 0xFFF85149;
            statusText = "Zeit abgelaufen – Reroll …";
        } else {
            statusColor = 0xFFFFC94D;
            statusText = "Warte auf Bestätigung";
        }
        context.drawText(tr, GuiUtil.trimToWidth(tr, (state.isRerollClaim() ? "Reroll: " : "") + statusText,
                width), tx, ty, statusColor, false);
        ty += 12;
        context.drawText(tr, GuiUtil.trimToWidth(tr, "@" + winner.name, width), tx, ty, 0xFFE6E6E6,
                false);
        ty += 12;

        long remain = state.claimRemainMs();
        long total = state.claimTotalMs();
        if (state.isClaimPending()) {
            context.drawText(tr, "noch " + ((remain + 999) / 1000) + " s", tx, ty, 0xFF8B949E, false);
        } else if (state.isClaimed()) {
            String msg = state.getClaimMessage();
            context.drawText(tr, GuiUtil.trimToWidth(tr, msg == null ? "im Chat" : msg, width), tx, ty,
                    0xFF8B949E, false);
        } else {
            context.drawText(tr, "0 s", tx, ty, 0xFF8B949E, false);
        }
        ty += 11;
        // Fortschrittsbalken
        int filled = total <= 0 ? 0 : (int) (barW * Math.max(0, Math.min(1, remain / (double) total)));
        context.fill(tx, ty, tx + barW, ty + 4, 0xFF23262E);
        int barColor = state.isClaimed() ? 0xFF3FB950
                : remain < 10_000 ? 0xFFF85149 : 0xFFFFC94D;
        if (filled > 0) {
            context.fill(tx, ty, tx + filled, ty + 4, barColor);
        }
    }

    private void drawWheelTab(DrawContext context, TextRenderer tr, List<Participant> pool,
                              int x, int y, int w, int h) {
        int version = wheelSpinning ? wheelFrozenVersion : state.getVersion();
        // Wichtig: immer absichern – auch während des Spinnens. Sonst zeigt das
        // Rad nach „Neu ziehen“ eine fehlende (lila/schwarze) Textur.
        wheel.ensure(pool, version);
        int size = Math.max(80, Math.min(w, h) - 16);
        int cx = x + w / 2;
        int cy = y + h / 2;
        wheel.draw(context, tr, pool, cx, cy, size, wheelRotation);
        // Zeiger oben (nach unten zeigendes Dreieck aus Balken)
        int py = cy - size / 2 - 2;
        for (int k = 0; k < 6; k++) {
            int barW = 20 - k * 3;
            context.fill(cx - barW / 2, py + k * 3, cx + (barW + 1) / 2, py + 3 + k * 3, 0xFFFFC94D);
        }
        if (pool.isEmpty()) {
            GuiUtil.drawCenteredText(context, tr, "Keine Teilnehmer", cx, cy - 4, 0xFF8B949E, false);
            GuiUtil.drawCenteredText(context, tr, "Chat: " + state.config.keyword, cx, cy + 10, 0xFF5B6470,
                    false);
        }
    }

    private void drawRouletteTab(DrawContext context, TextRenderer tr, List<Participant> pool,
                                 int x, int y, int w, int h) {
        int stripH = RouletteView.CARD_HEIGHT + 28;
        int stripY = y + (h - stripH) / 2;
        roulette.draw(context, tr, state.avatars, x, stripY, w, stripH);
        if (pool.isEmpty()) {
            GuiUtil.drawCenteredText(context, tr, "Keine Teilnehmer – Chat: " + state.config.keyword,
                    x + w / 2, stripY + stripH + 8, 0xFF5B6470, false);
            return;
        }
        Participant last = state.getLastWinner();
        if (last != null) {
            GuiUtil.drawCenteredText(context, tr, "Letzter Gewinner: " + last.name, x + w / 2,
                    stripY + stripH + 8, 0xFFFFD166, false);
        }
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

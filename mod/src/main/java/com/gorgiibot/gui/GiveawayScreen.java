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
 * Twitch-Status, Teilnehmerzahl und Aktionen.
 */
public class GiveawayScreen extends Screen {
    private enum Tab {
        WHEEL, ROULETTE
    }

    private static final long WHEEL_MILLIS = 7000;
    private static final int PANEL_WIDTH = 190;

    private final GiveawayState state = GorgiiBotClient.state;
    private Tab tab;
    private boolean autoStart;

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
    private Participant wheelWinner;
    private int wheelLastSegment = -1;

    private ButtonWidget tabWheelButton;
    private ButtonWidget tabRouletteButton;
    private ButtonWidget connectButton;
    private ButtonWidget startButton;
    private ButtonWidget resetButton;
    private ButtonWidget settingsButton;

    public GiveawayScreen() {
        this(false);
    }

    /** @param autoStart startet sofort eine Ziehung (für „Neu ziehen“). */
    public GiveawayScreen(boolean autoStart) {
        super(Text.literal("GorgiiBot Giveaway"));
        this.autoStart = autoStart;
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
        if (wheelSpinning || roulette.isSpinning()) {
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
        if (tab == Tab.WHEEL) {
            wheelPool = pool;
            wheelWinner = winner;
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
        if (client != null) {
            client.setScreen(new WinnerScreen(winner));
        }
    }

    private void reset() {
        if (wheelSpinning || roulette.isSpinning()) {
            return;
        }
        state.clear();
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
        boolean connected = state.twitch.status() == TwitchIrcClient.Status.CONNECTED
                || state.twitch.status() == TwitchIrcClient.Status.CONNECTING;
        connectButton.setMessage(Text.literal(connected ? "Trennen" : "Verbinden"));
        startButton.setMessage(Text.literal(busy ? "Läuft …" : "Start"));
        startButton.active = !busy && state.count() > 0;
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
        if (autoStart) {
            autoStart = false;
            startSpin();
        }
        updateWheelSpin();
        if (client == null || client.currentScreen != this) {
            return;
        }
        refreshButtons();
        this.renderBackground(context, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;
        int panelX = this.width - PANEL_WIDTH - 12;
        int mainW = panelX - 24;

        context.fill(panelX, 36, panelX + PANEL_WIDTH, this.height - 12, 0xDD14161B);
        GuiUtil.fillBorder(context, panelX, 36, panelX + PANEL_WIDTH, this.height - 12, 0xFF2A2E36);
        drawSidePanel(context, tr, panelX);

        if (tab == Tab.WHEEL) {
            drawWheelTab(context, tr, 12, 36, mainW, this.height - 48);
        } else {
            drawRouletteTab(context, tr, 12, 36, mainW, this.height - 48);
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

        int hintY = this.height - 36;
        context.drawText(tr, "G: Menü öffnen", tx, hintY, 0xFF5B6470, false);
        context.drawText(tr, "/giveaway im Chat", tx, hintY + 11, 0xFF5B6470, false);
    }

    private void drawWheelTab(DrawContext context, TextRenderer tr, int x, int y, int w, int h) {
        List<Participant> pool;
        if (wheelSpinning) {
            pool = wheelPool;
        } else {
            pool = state.snapshot();
            wheel.ensure(pool, state.getVersion());
        }
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

    private void drawRouletteTab(DrawContext context, TextRenderer tr, int x, int y, int w, int h) {
        int stripH = RouletteView.CARD_HEIGHT + 28;
        int stripY = y + (h - stripH) / 2;
        roulette.draw(context, tr, state.avatars, x, stripY, w, stripH);
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

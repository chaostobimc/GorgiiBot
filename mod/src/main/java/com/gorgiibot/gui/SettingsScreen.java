package com.gorgiibot.gui;

import com.gorgiibot.GiveawayState;
import com.gorgiibot.GorgiiBotClient;
import com.gorgiibot.config.GorgiiConfig;
import com.gorgiibot.twitch.TwitchIrcClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Einstellungen: Kanal, Schlüsselwort, Teilnahme-Regeln, Claim-Zeit, Sound. */
public class SettingsScreen extends Screen {
    /** Auswahlstufen für die Bestätigungszeit (Sekunden). */
    private static final int[] CLAIM_STEPS = {30, 45, 60, 90, 120, 180};

    private final GiveawayState state = GorgiiBotClient.state;

    private TextFieldWidget channelField;
    private TextFieldWidget keywordField;
    private ButtonWidget subOnlyButton;
    private ButtonWidget subLuckButton;
    private ButtonWidget claimButton;
    private ButtonWidget avatarsButton;
    private ButtonWidget removeConfirmedButton;
    private ButtonWidget removeTimeoutButton;
    private ButtonWidget soundButton;
    private ButtonWidget confettiButton;

    public SettingsScreen() {
        super(Text.literal("GorgiiBot Einstellungen"));
    }

    @Override
    protected void init() {
        TextRenderer tr = this.textRenderer;
        GorgiiConfig config = state.config;

        int totalW = Math.min(330, this.width - 20);
        int colW = (totalW - 12) / 2;
        int left = (this.width - totalW) / 2;
        int colB = left + colW + 12;
        int fieldW = Math.min(220, totalW);
        int fieldX = (this.width - fieldW) / 2;

        int y = Math.max(28, Math.min(64, (this.height - 230) / 2));

        channelField = new TextFieldWidget(tr, fieldX, y, fieldW, 20, Text.literal("Twitch-Kanal"));
        channelField.setMaxLength(32);
        channelField.setText(config.channel);
        channelField.setPlaceholder(Text.literal("kanalname"));
        channelField.setChangedListener(text -> config.channel = text);
        this.addDrawableChild(channelField);
        y += 34;

        keywordField = new TextFieldWidget(tr, fieldX, y, fieldW, 20, Text.literal("Schlüsselwort"));
        keywordField.setMaxLength(32);
        keywordField.setText(config.keyword);
        keywordField.setPlaceholder(Text.literal("!teilnahme"));
        keywordField.setChangedListener(text -> config.keyword = text.isEmpty() ? "!teilnahme" : text);
        this.addDrawableChild(keywordField);
        y += 30;

        int rowA = y;
        int rowB = y;

        subOnlyButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.requireSubscriber = !config.requireSubscriber;
            refreshButtons();
        }).dimensions(left, rowA, colW, 20).build();
        this.addDrawableChild(subOnlyButton);
        rowA += 22;

        subLuckButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.subLuck = config.subLuck % 5 + 1;
            refreshButtons();
        }).dimensions(left, rowA, colW, 20).build();
        this.addDrawableChild(subLuckButton);
        rowA += 22;

        claimButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.claimSeconds = nextClaimStep(config.claimSeconds);
            refreshButtons();
        }).dimensions(left, rowA, colW, 20).build();
        this.addDrawableChild(claimButton);
        rowA += 22;

        avatarsButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.avatars = !config.avatars;
            refreshButtons();
        }).dimensions(left, rowA, colW, 20).build();
        this.addDrawableChild(avatarsButton);
        rowA += 22;

        removeConfirmedButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.autoRemoveConfirmed = !config.autoRemoveConfirmed;
            refreshButtons();
        }).dimensions(colB, rowB, colW, 20).build();
        this.addDrawableChild(removeConfirmedButton);
        rowB += 22;

        removeTimeoutButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.autoRemoveTimeout = !config.autoRemoveTimeout;
            refreshButtons();
        }).dimensions(colB, rowB, colW, 20).build();
        this.addDrawableChild(removeTimeoutButton);
        rowB += 22;

        soundButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.sound = !config.sound;
            refreshButtons();
        }).dimensions(colB, rowB, colW, 20).build();
        this.addDrawableChild(soundButton);
        rowB += 22;

        confettiButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.confetti = !config.confetti;
            refreshButtons();
        }).dimensions(colB, rowB, colW, 20).build();
        this.addDrawableChild(confettiButton);
        rowB += 22;

        int bottom = Math.max(rowA, rowB) + 6;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("12 Test-Teilnehmer"),
                b -> state.addTestParticipants()).dimensions(fieldX, bottom, fieldW, 20).build());
        bottom += 22;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Fertig"), b -> saveAndBack())
                .dimensions(fieldX, bottom, fieldW, 20).build());
        refreshButtons();
    }

    private static int nextClaimStep(int current) {
        for (int step : CLAIM_STEPS) {
            if (step > current) {
                return step;
            }
        }
        return CLAIM_STEPS[0];
    }

    private void refreshButtons() {
        if (subOnlyButton == null) {
            return;
        }
        GorgiiConfig config = state.config;
        subOnlyButton.setMessage(Text.literal("Nur Subs: " + onOff(config.requireSubscriber)));
        subLuckButton.setMessage(Text.literal("Sub-Glück: x" + config.subLuck));
        claimButton.setMessage(Text.literal("Bestätigung: " + config.claimSeconds + " s"));
        avatarsButton.setMessage(Text.literal("Avatare: " + onOff(config.avatars)));
        removeConfirmedButton.setMessage(Text.literal("Nach Claim: " + removeLabel(config.autoRemoveConfirmed)));
        removeTimeoutButton.setMessage(Text.literal("Nach Timeout: " + removeLabel(config.autoRemoveTimeout)));
        soundButton.setMessage(Text.literal("Sound: " + onOff(config.sound)));
        confettiButton.setMessage(Text.literal("Konfetti: " + onOff(config.confetti)));
    }

    private static String onOff(boolean value) {
        return value ? "AN" : "AUS";
    }

    private static String removeLabel(boolean value) {
        return value ? "ENTF." : "BEHALTEN";
    }

    /** Speichern und zurück zum Giveaway-Screen. */
    public void saveAndBack() {
        saveAndGo(true);
    }

    /** Speichern und ganz schließen (für die G-Taste). */
    public void saveAndClose() {
        saveAndGo(false);
    }

    private void saveAndGo(boolean backToGiveaway) {
        state.config.save();
        // Falls der Kanal geändert wurde und eine Verbindung läuft: neu verbinden.
        TwitchIrcClient.Status status = state.twitch.status();
        if (!state.config.channel.equals(state.twitch.channel())
                && (status == TwitchIrcClient.Status.CONNECTED
                || status == TwitchIrcClient.Status.CONNECTING)) {
            state.twitch.disconnect();
            state.twitch.connect(state.config.channel);
        }
        if (client != null) {
            client.setScreen(backToGiveaway ? new GiveawayScreen() : null);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Kein renderBackground(): Blur ist nur einmal pro Frame erlaubt.
        context.fill(0, 0, this.width, this.height, 0xB0101015);
        TextRenderer tr = this.textRenderer;
        GuiUtil.drawCenteredText(context, tr, "GorgiiBot Einstellungen", this.width / 2, 14, 0xFFFFFFFF,
                false);
        if (channelField != null) {
            context.drawText(tr, "Twitch-Kanal (ohne #):", channelField.getX(), channelField.getY() - 11,
                    0xFF8B949E, false);
        }
        if (keywordField != null) {
            context.drawText(tr, "Schlüsselwort für Teilnahme:", keywordField.getX(),
                    keywordField.getY() - 11, 0xFF8B949E, false);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

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

/** Einstellungen: Kanal, Schlüsselwort, Teilnahme-Regeln, Sound, Konfetti. */
public class SettingsScreen extends Screen {
    private final GiveawayState state = GorgiiBotClient.state;

    private TextFieldWidget channelField;
    private TextFieldWidget keywordField;
    private ButtonWidget subOnlyButton;
    private ButtonWidget subLuckButton;
    private ButtonWidget soundButton;
    private ButtonWidget confettiButton;

    public SettingsScreen() {
        super(Text.literal("GorgiiBot Einstellungen"));
    }

    @Override
    protected void init() {
        TextRenderer tr = this.textRenderer;
        GorgiiConfig config = state.config;
        int w = 220;
        int x = (this.width - w) / 2;
        int y = 60;

        channelField = new TextFieldWidget(tr, x, y, w, 20, Text.literal("Twitch-Kanal"));
        channelField.setMaxLength(32);
        channelField.setText(config.channel);
        channelField.setPlaceholder(Text.literal("kanalname"));
        channelField.setChangedListener(text -> config.channel = text);
        this.addDrawableChild(channelField);
        y += 34;

        keywordField = new TextFieldWidget(tr, x, y, w, 20, Text.literal("Schlüsselwort"));
        keywordField.setMaxLength(32);
        keywordField.setText(config.keyword);
        keywordField.setPlaceholder(Text.literal("!teilnahme"));
        keywordField.setChangedListener(text -> config.keyword = text.isEmpty() ? "!teilnahme" : text);
        this.addDrawableChild(keywordField);
        y += 34;

        subOnlyButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.requireSubscriber = !config.requireSubscriber;
            refreshButtons();
        }).dimensions(x, y, w, 20).build();
        this.addDrawableChild(subOnlyButton);
        y += 24;

        subLuckButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.subLuck = config.subLuck % 5 + 1;
            refreshButtons();
        }).dimensions(x, y, w, 20).build();
        this.addDrawableChild(subLuckButton);
        y += 24;

        soundButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.sound = !config.sound;
            refreshButtons();
        }).dimensions(x, y, w, 20).build();
        this.addDrawableChild(soundButton);
        y += 24;

        confettiButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.confetti = !config.confetti;
            refreshButtons();
        }).dimensions(x, y, w, 20).build();
        this.addDrawableChild(confettiButton);
        y += 28;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("12 Test-Teilnehmer"),
                b -> state.addTestParticipants()).dimensions(x, y, w, 20).build());
        y += 24;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Fertig"), b -> saveAndBack())
                .dimensions(x, y, w, 20).build());
        refreshButtons();
    }

    private void refreshButtons() {
        if (subOnlyButton == null) {
            return;
        }
        GorgiiConfig config = state.config;
        subOnlyButton.setMessage(Text.literal("Nur Subs/VIPs/Mods: " + (config.requireSubscriber ? "AN" : "AUS")));
        subLuckButton.setMessage(Text.literal("Sub-Glück: x" + config.subLuck));
        soundButton.setMessage(Text.literal("Sound: " + (config.sound ? "AN" : "AUS")));
        confettiButton.setMessage(Text.literal("Konfetti: " + (config.confetti ? "AN" : "AUS")));
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
        int w = 220;
        int x = (this.width - w) / 2;
        GuiUtil.drawCenteredText(context, tr, "GorgiiBot Einstellungen", this.width / 2, 20, 0xFFFFFFFF,
                false);
        context.drawText(tr, "Twitch-Kanal (ohne #):", x, 48, 0xFF8B949E, false);
        context.drawText(tr, "Schlüsselwort für Teilnahme:", x, 82, 0xFF8B949E, false);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

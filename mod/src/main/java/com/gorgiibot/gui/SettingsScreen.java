package com.gorgiibot.gui;

import com.gorgiibot.GiveawayState;
import com.gorgiibot.GorgiiBotClient;
import com.gorgiibot.twitch.TwitchIrcClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * @deprecated Die Einstellungen leben jetzt als Ansicht im Hauptfenster
 * ({@link GiveawayScreen} → „Einstellungen“). Diese Klasse bleibt nur aus
 * Kompatibilität erhalten und leitet sofort weiter.
 */
@Deprecated
public class SettingsScreen extends Screen {
    private final GiveawayState state = GorgiiBotClient.state;

    public SettingsScreen() {
        super(Text.literal("GorgiiBot Einstellungen"));
    }

    @Override
    protected void init() {
        if (client != null) {
            client.setScreen(new GiveawayScreen(GiveawayScreen.View.SETTINGS));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xB0101015);
        super.render(context, mouseX, mouseY, delta);
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
    public boolean shouldPause() {
        return false;
    }
}

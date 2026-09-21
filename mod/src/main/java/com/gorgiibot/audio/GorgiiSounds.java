package com.gorgiibot.audio;

import com.gorgiibot.GorgiiBotClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Vanilla-Sounds für Ticks und Fanfare (keine eigenen Assets nötig). */
public final class GorgiiSounds {
    private GorgiiSounds() {
    }

    private static final SoundEvent TICK = SoundEvent.of(Identifier.ofVanilla("block.note_block.hat"));
    private static final SoundEvent PLING = SoundEvent.of(Identifier.ofVanilla("block.note_block.pling"));
    private static final SoundEvent LEVELUP = SoundEvent.of(Identifier.ofVanilla("entity.player.levelup"));

    /** Tonhöhen für die Gewinner-Fanfare (aufsteigend). */
    public static final float[] FANFARE = {0.5f, 0.63f, 0.75f, 1.0f, 1.26f, 1.5f};

    public static void play(SoundEvent event, float pitch) {
        if (!GorgiiBotClient.state.config.sound) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.getSoundManager().play(PositionedSoundInstance.ui(event, pitch));
    }

    /** Kurzer Tick beim Passieren eines Segments / einer Karte. */
    public static void tick() {
        play(TICK, 1.0f);
    }

    /** Fanfaren-Ton in der gegebenen Tonhöhe. */
    public static void pling(float pitch) {
        play(PLING, pitch);
    }

    /** Level-up-Jingle für den Gewinner-Moment. */
    public static void levelup() {
        play(LEVELUP, 1.0f);
    }
}

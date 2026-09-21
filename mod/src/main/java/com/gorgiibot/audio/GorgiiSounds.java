package com.gorgiibot.audio;

import com.gorgiibot.GorgiiBotClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Vanilla-Sounds für Ticks, Countdown und Fanfare (keine eigenen Assets nötig). */
public final class GorgiiSounds {
    private GorgiiSounds() {
    }

    private static final SoundEvent TICK = SoundEvent.of(Identifier.ofVanilla("block.note_block.hat"));
    private static final SoundEvent PLING = SoundEvent.of(Identifier.ofVanilla("block.note_block.pling"));
    private static final SoundEvent LEVELUP = SoundEvent.of(Identifier.ofVanilla("entity.player.levelup"));
    private static final SoundEvent BELL = SoundEvent.of(Identifier.ofVanilla("block.note_block.bell"));
    private static final SoundEvent DRUM = SoundEvent.of(Identifier.ofVanilla("block.note_block.basedrum"));

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

    /** Countdown-Pieps in den letzten 5 Sekunden der Bestätigungszeit. */
    public static void countdown(boolean urgent) {
        play(PLING, urgent ? 0.5f : 0.75f);
    }

    /** Der Gewinner hat sich im Chat gemeldet. */
    public static void confirm() {
        play(BELL, 1.0f);
    }

    /** Zeit abgelaufen – gleich folgt der automatische Reroll. */
    public static void timeout() {
        play(DRUM, 0.7f);
    }
}

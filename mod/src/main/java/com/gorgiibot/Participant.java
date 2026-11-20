package com.gorgiibot;

import java.util.Locale;

/** Ein Giveaway-Teilnehmer aus dem Twitch-Chat. */
public class Participant {
    /** Anzeigename (Twitch display-name). */
    public final String name;
    /** Kleingeschriebener Login als eindeutiger Schlüssel (Twitch-Nick, nicht Display-Name). */
    public final String lower;
    /** Namensfarbe als ARGB (aus Twitch oder Ersatzpalette). */
    public int color;
    /** true für Subs/VIPs/Mods (bekommen ggf. Sub-Glück-Bonus). */
    public boolean boosted;
    /** Beitrittszeitpunkt (Millis). */
    public final long joinedAt;

    public Participant(String lower, String displayName, int color, boolean boosted) {
        this.lower = lower.toLowerCase(Locale.ROOT);
        this.name = displayName == null || displayName.isEmpty() ? this.lower : displayName;
        this.color = color;
        this.boosted = boosted;
        this.joinedAt = System.currentTimeMillis();
    }

    /** @deprecated Nur für Tests; bevorzugt die Konstruktor-Variante mit explizitem Login. */
    @Deprecated
    public Participant(String displayName, int color, boolean boosted) {
        this(displayName == null ? "" : displayName.toLowerCase(Locale.ROOT), displayName, color, boosted);
    }
}

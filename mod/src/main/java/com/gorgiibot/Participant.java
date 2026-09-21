package com.gorgiibot;

import java.util.Locale;

/** Ein Giveaway-Teilnehmer aus dem Twitch-Chat. */
public class Participant {
    /** Anzeigename (Twitch display-name). */
    public final String name;
    /** Kleingeschriebener Name als eindeutiger Schlüssel. */
    public final String lower;
    /** Namensfarbe als ARGB (aus Twitch oder Ersatzpalette). */
    public int color;
    /** true für Subs/VIPs/Mods (bekommen ggf. Sub-Glück-Bonus). */
    public boolean boosted;
    /** Beitrittszeitpunkt (Millis). */
    public final long joinedAt;

    public Participant(String displayName, int color, boolean boosted) {
        this.name = displayName;
        this.lower = displayName.toLowerCase(Locale.ROOT);
        this.color = color;
        this.boosted = boosted;
        this.joinedAt = System.currentTimeMillis();
    }
}

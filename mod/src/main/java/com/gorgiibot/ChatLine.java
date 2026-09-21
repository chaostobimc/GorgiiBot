package com.gorgiibot;

/** Eine einzelne Twitch-Chatnachricht (für Verlauf & Teilnahme). */
public record ChatLine(String lower, String displayName, int color, String text, long time,
        boolean boosted) {
}

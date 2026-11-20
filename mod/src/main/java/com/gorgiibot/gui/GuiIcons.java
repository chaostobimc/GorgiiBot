package com.gorgiibot.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Vektor-Icons (16×16) für die Desktop-UI: Entfernen, Bannen, Entbannen,
 * Suche, Navigation, Status. Rein aus Füll-Rechtecken gezeichnet – keine
 * Texturen, keine Emoji-Font nötig, in jedem GUI-Maßstab scharf.
 */
public final class GuiIcons {
    private GuiIcons() {
    }

    public static final int SIZE = 16;

    /** Entfernen: Papierkorb (Deckel + Korpus + Griff). */
    public static void remove(DrawContext ctx, int x, int y, int color) {
        // Deckel
        ctx.fill(x + 3, y + 3, x + 13, y + 5, color);
        // Griff
        ctx.fill(x + 6, y + 1, x + 10, y + 3, color);
        // Korpus (leicht trapezförmig)
        ctx.fill(x + 4, y + 5, x + 12, y + 14, color);
        ctx.fill(x + 3, y + 13, x + 13, y + 15, color);
        // Schlitze (Panel-Farbe als Aussparung)
        int slot = GuiUtil.PANEL_LIGHT;
        ctx.fill(x + 6, y + 7, x + 7, y + 12, slot);
        ctx.fill(x + 9, y + 7, x + 10, y + 12, slot);
    }

    /** Bannen: Verbotsschild (Kreis + Schrägstrich). */
    public static void ban(DrawContext ctx, int x, int y, int color) {
        GuiUtil.fillCircle(ctx, x + 8, y + 8, 7, color);
        // Innenkreis ausschneiden (Panel-Farbe)
        GuiUtil.fillCircle(ctx, x + 8, y + 8, 4, GuiUtil.PANEL_LIGHT);
        // Schrägstrich
        ctx.fill(x + 4, y + 4, x + 6, y + 12, color);
        ctx.fill(x + 5, y + 3, x + 7, y + 5, color);
        ctx.fill(x + 9, y + 11, x + 11, y + 13, color);
    }

    /** Entbannen: Haken im Kreis. */
    public static void unban(DrawContext ctx, int x, int y, int color) {
        GuiUtil.fillCircle(ctx, x + 8, y + 8, 7, color);
        GuiUtil.fillCircle(ctx, x + 8, y + 8, 4, GuiUtil.PANEL_LIGHT);
        // Haken
        ctx.fill(x + 5, y + 8, x + 7, y + 10, color);
        ctx.fill(x + 6, y + 9, x + 8, y + 11, color);
        ctx.fill(x + 7, y + 8, x + 11, y + 5, color);
        ctx.fill(x + 8, y + 7, x + 12, y + 4, color);
    }

    /** Haken (ohne Kreis). */
    public static void check(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x + 2, y + 8, x + 6, y + 10, color);
        ctx.fill(x + 5, y + 9, x + 7, y + 11, color);
        ctx.fill(x + 6, y + 8, x + 13, y + 3, color);
        ctx.fill(x + 7, y + 7, x + 14, y + 2, color);
    }

    /** Kreuz (Schließen / Abbrechen). */
    public static void close(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x + 3, y + 3, x + 5, y + 13, color);
        ctx.fill(x + 11, y + 3, x + 13, y + 13, color);
        ctx.fill(x + 3, y + 3, x + 13, y + 5, color);
        ctx.fill(x + 3, y + 11, x + 13, y + 13, color);
        // Mitte dünner
        ctx.fill(x + 6, y + 6, x + 10, y + 10, color);
    }

    /** Suche: Lupe. */
    public static void search(DrawContext ctx, int x, int y, int color) {
        GuiUtil.fillCircle(ctx, x + 6, y + 6, 5, color);
        GuiUtil.fillCircle(ctx, x + 6, y + 6, 3, GuiUtil.PANEL);
        // Griff
        ctx.fill(x + 9, y + 9, x + 11, y + 11, color);
        ctx.fill(x + 10, y + 10, x + 14, y + 14, color);
    }

    /** Glücksrad: Kreis mit Speichen. */
    public static void wheel(DrawContext ctx, int x, int y, int color) {
        GuiUtil.fillCircle(ctx, x + 8, y + 8, 7, color);
        GuiUtil.fillCircle(ctx, x + 8, y + 8, 5, GuiUtil.PANEL);
        ctx.fill(x + 7, y + 1, x + 9, y + 15, color);
        ctx.fill(x + 1, y + 7, x + 15, y + 9, color);
        ctx.fill(x + 3, y + 3, x + 5, y + 13, color);
        ctx.fill(x + 11, y + 3, x + 13, y + 13, color);
        GuiUtil.fillCircle(ctx, x + 8, y + 8, 2, color);
    }

    /** Roulette: drei Karten / Pfeile. */
    public static void roulette(DrawContext ctx, int x, int y, int color) {
        // Linke + rechte Karte
        ctx.fill(x + 1, y + 4, x + 5, y + 12, color);
        ctx.fill(x + 11, y + 4, x + 15, y + 12, color);
        // Mittlere Karte (höher)
        ctx.fill(x + 5, y + 2, x + 11, y + 14, color);
        // Stern/Punkt in der Mitte
        ctx.fill(x + 7, y + 6, x + 9, y + 10, GuiUtil.PANEL);
    }

    /** Teilnehmer: zwei Köpfe. */
    public static void users(DrawContext ctx, int x, int y, int color) {
        // Kopf links
        GuiUtil.fillCircle(ctx, x + 5, y + 5, 3, color);
        // Körper links
        ctx.fill(x + 2, y + 9, x + 8, y + 14, color);
        // Kopf rechts (kleiner, dahinter)
        GuiUtil.fillCircle(ctx, x + 11, y + 6, 2, color);
        ctx.fill(x + 9, y + 10, x + 14, y + 14, color);
    }

    /** Gesperrte: Schild mit Ausrufezeichen. */
    public static void banned(DrawContext ctx, int x, int y, int color) {
        // Schild
        ctx.fill(x + 3, y + 2, x + 13, y + 14, color);
        ctx.fill(x + 5, y + 14, x + 11, y + 15, color);
        // Ausrufezeichen (Panel-Farbe)
        ctx.fill(x + 7, y + 4, x + 9, y + 10, GuiUtil.PANEL);
        ctx.fill(x + 7, y + 11, x + 9, y + 13, GuiUtil.PANEL);
    }

    /** Einstellungen: Zahnrad (Näherung). */
    public static void settings(DrawContext ctx, int x, int y, int color) {
        GuiUtil.fillCircle(ctx, x + 8, y + 8, 5, color);
        GuiUtil.fillCircle(ctx, x + 8, y + 8, 2, GuiUtil.PANEL);
        // Zähne (8 Richtungen)
        ctx.fill(x + 7, y + 0, x + 9, y + 3, color);
        ctx.fill(x + 7, y + 13, x + 9, y + 16, color);
        ctx.fill(x + 0, y + 7, x + 3, y + 9, color);
        ctx.fill(x + 13, y + 7, x + 16, y + 9, color);
        ctx.fill(x + 2, y + 2, x + 4, y + 4, color);
        ctx.fill(x + 12, y + 2, x + 14, y + 4, color);
        ctx.fill(x + 2, y + 12, x + 4, y + 14, color);
        ctx.fill(x + 12, y + 12, x + 14, y + 14, color);
    }

    /** Plus (Hinzufügen). */
    public static void plus(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x + 7, y + 2, x + 9, y + 14, color);
        ctx.fill(x + 2, y + 7, x + 14, y + 9, color);
    }

    /** Zurück / Vor (Pfeile). */
    public static void arrowLeft(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x + 2, y + 7, x + 12, y + 9, color);
        ctx.fill(x + 2, y + 4, x + 5, y + 12, color);
        ctx.fill(x + 5, y + 2, x + 8, y + 5, color);
        ctx.fill(x + 5, y + 11, x + 8, y + 14, color);
    }

    public static void arrowRight(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x + 4, y + 7, x + 14, y + 9, color);
        ctx.fill(x + 11, y + 4, x + 14, y + 12, color);
        ctx.fill(x + 8, y + 2, x + 11, y + 5, color);
        ctx.fill(x + 8, y + 11, x + 11, y + 14, color);
    }

    /** Sortieren: Pfeil nach unten. */
    public static void sort(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x + 3, y + 3, x + 13, y + 5, color);
        ctx.fill(x + 5, y + 5, x + 11, y + 7, color);
        ctx.fill(x + 7, y + 7, x + 9, y + 11, color);
        ctx.fill(x + 5, y + 11, x + 11, y + 13, color);
    }

    /** Twitch-Statuspunkt (wird als Kreis gezeichnet). */
    public static void dot(DrawContext ctx, int x, int y, int color) {
        GuiUtil.fillCircle(ctx, x + 4, y + 4, 3, color);
    }
}

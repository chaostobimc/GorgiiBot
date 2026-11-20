package com.gorgiibot.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Zeichenhelfer im <b>Vanilla-Minecraft-Stil</b>: flache Flächen, 1-px-Rahmen,
 * Standard-Textfarben – keine abgerundeten Ecken, Schatten oder Verläufe.
 */
public final class GuiUtil {
    private GuiUtil() {
    }

    // ---- Textfarben wie im Vanilla-UI ----
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFA0A0A0;
    public static final int TEXT_FAINT = 0xFF808080;
    public static final int GOLD = 0xFFFFAA00;
    public static final int YELLOW = 0xFFFFFF55;
    public static final int GREEN = 0xFF55FF55;
    public static final int RED = 0xFFFF5555;
    public static final int AQUA = 0xFF55FFFF;

    // ---- Flächen ----
    /** Hintergrund einer Liste (wie im Vanilla-UI halbtransparent schwarz). */
    public static final int PANEL = 0x90000000;
    /** Etwas dunklere Fläche für Kopf-/Fußzeilen. */
    public static final int PANEL_DARK = 0xB0000000;
    public static final int BORDER = 0xFF555555;
    public static final int BORDER_DARK = 0xFF000000;
    /** Trennlinie zwischen Listenzeilen. */
    public static final int SEPARATOR = 0x30FFFFFF;
    /** Hover-Highlight einer Zeile. */
    public static final int HOVER = 0x33FFFFFF;
    /** Bildschirm abdunkeln (statt Blur – der ist nur 1× pro Frame erlaubt). */
    public static final int BACKDROP = 0xC0101010;

    /** Kürzt Text mit „…“ auf die maximale Pixelbreite. */
    public static String trimToWidth(TextRenderer textRenderer, String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        return textRenderer.trimToWidth(text, Math.max(0, maxWidth - 6)) + "…";
    }

    /** Zeichnet einen 1-px-Rahmen. */
    public static void fillBorder(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1, x1 + 1, y2, color);
        context.fill(x2 - 1, y1, x2, y2, color);
    }

    /** Zeichnet zentrierten Text. */
    public static void drawCenteredText(DrawContext context, TextRenderer textRenderer, String text,
                                       int centerX, int y, int color, boolean shadow) {
        context.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, color, shadow);
    }

    /** Text rechtsbündig an {@code rightX} ausrichten. */
    public static void drawRightText(DrawContext context, TextRenderer textRenderer, String text,
                                     int rightX, int y, int color, boolean shadow) {
        context.drawText(textRenderer, text, rightX - textRenderer.getWidth(text), y, color, shadow);
    }

    /** Fläche mit 1-px-Rahmen (Vanilla-Kasten). */
    public static void panel(DrawContext context, int x1, int y1, int x2, int y2, int fill, int border) {
        context.fill(x1, y1, x2, y2, fill);
        fillBorder(context, x1, y1, x2, y2, border);
    }

    /**
     * Zeichnet eine Textur <b>vollständig</b> in das Zielrechteck.
     *
     * <p>Wichtig: Die kurze {@code drawTexture}-Variante benutzt Breite/Höhe
     * gleichzeitig als Größe des Quellausschnitts <i>und</i> des Zielrechtecks
     * („The width and height of the region are the same as the dimensions of the
     * rectangle“). Ist die Textur größer als das Ziel, sähe man dadurch nur
     * einen Ausschnitt (beim Rad exakt ein Viertel, bei Avataren ein
     * verschobener Bildausschnitt). Diese Variante übergibt Ziel- und
     * Quellgröße getrennt, deshalb landet die Grafik immer komplett,
     * skaliert und zentriert im Zielrechteck.
     */
    public static void drawTexture(DrawContext context, Identifier texture, int x, int y,
                                   int width, int height, int textureWidth, int textureHeight) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    /** Dunkelt eine ARGB-Farbe um den Faktor ab (Alpha bleibt). */
    public static int darken(int argb, float factor) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (int) (((argb >> 16) & 0xFF) * factor);
        int green = (int) (((argb >> 8) & 0xFF) * factor);
        int blue = (int) ((argb & 0xFF) * factor);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** Prüft, ob ein Punkt in einem Rechteck liegt (für Hover-Tests). */
    public static boolean inRect(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }
}

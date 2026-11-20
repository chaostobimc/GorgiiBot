package com.gorgiibot.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Zeichenhelfer für die Giveaway-Screens: Theme (abgerundete Ecken,
 * Schatten, Verläufe) plus die ursprünglichen Textur-Helfer.
 */
public final class GuiUtil {
    private GuiUtil() {
    }

    // ---- Theme (GitHub-Dark) ----
    public static final int BG = 0xFF0D1117;
    public static final int PANEL = 0xFF161B22;
    public static final int PANEL_LIGHT = 0xFF1C2128;
    public static final int PANEL_DARK = 0xFF0D1117;
    public static final int BORDER = 0xFF30363D;
    public static final int BORDER_SOFT = 0xFF21262D;
    public static final int TEXT = 0xFFE6EDF3;
    public static final int TEXT_DIM = 0xFF8B949E;
    public static final int TEXT_FAINT = 0xFF6E7681;
    public static final int ACCENT = 0xFFFFC94D;
    public static final int ACCENT_BLUE = 0xFF58A6FF;
    public static final int GREEN = 0xFF3FB950;
    public static final int RED = 0xFFF85149;
    public static final int YELLOW = 0xFFFFC94D;

    /** Kürzt Text mit „…“ auf die maximale Pixelbreite. */
    public static String trimToWidth(TextRenderer textRenderer, String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int ellipsisWidth = textRenderer.getWidth(ellipsis);
        return textRenderer.trimToWidth(text, Math.max(0, maxWidth - ellipsisWidth)) + ellipsis;
    }

    /** Zeichnet einen 1px-Rahmen. */
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

    /** Hellt eine ARGB-Farbe um den Faktor auf (Alpha bleibt). */
    public static int lighten(int argb, float factor) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = Math.min(255, (int) (((argb >> 16) & 0xFF) * factor));
        int green = Math.min(255, (int) (((argb >> 8) & 0xFF) * factor));
        int blue = Math.min(255, (int) ((argb & 0xFF) * factor));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** Mischt zwei ARGB-Farben (t = 0 -> a, t = 1 -> b). */
    public static int lerpColor(int a, int b, float t) {
        int alpha = lerpChannel((a >>> 24) & 0xFF, (b >>> 24) & 0xFF, t);
        int red = lerpChannel((a >> 16) & 0xFF, (b >> 16) & 0xFF, t);
        int green = lerpChannel((a >> 8) & 0xFF, (b >> 8) & 0xFF, t);
        int blue = lerpChannel(a & 0xFF, b & 0xFF, t);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int lerpChannel(int a, int b, float t) {
        return Math.max(0, Math.min(255, Math.round(a + (b - a) * t)));
    }

    /**
     * Füllt ein abgerundetes Rechteck (Eckenradius {@code r}). Näherung aus
     * Rechteck + Ecken-Pixeln – ohne Shader, dafür in jedem GUI-Maßstab scharf.
     */
    public static void roundedRect(DrawContext context, int x1, int y1, int x2, int y2, int r, int color) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        int rad = Math.max(0, Math.min(r, Math.min(x2 - x1, y2 - y1) / 2));
        // Hauptfläche ohne Ecken
        context.fill(x1 + rad, y1, x2 - rad, y2, color);
        context.fill(x1, y1 + rad, x2, y2 - rad, color);
        // Ecken: je 2 Zeilen einrücken (weiche Rundung)
        if (rad >= 2) {
            context.fill(x1 + 1, y1 + 1, x1 + rad, y1 + 2, color);
            context.fill(x2 - rad, y1 + 1, x2 - 1, y1 + 2, color);
            context.fill(x1 + 1, y2 - 2, x1 + rad, y2 - 1, color);
            context.fill(x2 - rad, y2 - 2, x2 - 1, y2 - 1, color);
        }
        if (rad >= 4) {
            context.fill(x1, y1 + 2, x1 + 1, y1 + rad, color);
            context.fill(x2 - 1, y1 + 2, x2, y1 + rad, color);
            context.fill(x1, y2 - rad, x1 + 1, y2 - 2, color);
            context.fill(x2 - 1, y2 - rad, x2, y2 - 2, color);
        }
    }

    /** Abgerundeter Rahmen (1 px). */
    public static void roundedBorder(DrawContext context, int x1, int y1, int x2, int y2, int r, int color) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        int rad = Math.max(1, Math.min(r, Math.min(x2 - x1, y2 - y1) / 2));
        context.fill(x1 + rad, y1, x2 - rad, y1 + 1, color);
        context.fill(x1 + rad, y2 - 1, x2 - rad, y2, color);
        context.fill(x1, y1 + rad, x1 + 1, y2 - rad, color);
        context.fill(x2 - 1, y1 + rad, x2, y2 - rad, color);
        // Eckverbindungen
        context.fill(x1 + 1, y1 + 1, x1 + rad, y1 + 2, color);
        context.fill(x2 - rad, y1 + 1, x2 - 1, y1 + 2, color);
        context.fill(x1 + 1, y2 - 2, x1 + rad, y2 - 1, color);
        context.fill(x2 - rad, y2 - 2, x2 - 1, y2 - 1, color);
        context.fill(x1, y1 + 2, x1 + 1, y1 + rad, color);
        context.fill(x2 - 1, y1 + 2, x2, y1 + rad, color);
        context.fill(x1, y2 - rad, x1 + 1, y2 - 2, color);
        context.fill(x2 - 1, y2 - rad, x2, y2 - 2, color);
    }

    /** Weicher Schatten unter einem Fenster (mehrere Alpha-Schichten). */
    public static void shadow(DrawContext context, int x1, int y1, int x2, int y2, int r) {
        // Äußere, weiche Schicht
        roundedRect(context, x1 - 6, y1 - 4, x2 + 6, y2 + 10, r + 4, 0x33000000);
        roundedRect(context, x1 - 3, y1 - 2, x2 + 3, y2 + 6, r + 2, 0x55000000);
        roundedRect(context, x1 - 1, y1, x2 + 1, y2 + 3, r + 1, 0x66000000);
    }

    /** Vertikaler Farbverlauf (oben -> unten), in 2-px-Schritten. */
    public static void gradientV(DrawContext context, int x1, int y1, int x2, int y2, int top, int bottom) {
        int h = Math.max(1, y2 - y1);
        for (int y = y1; y < y2; y += 2) {
            float t = (y - y1) / (float) h;
            int c = lerpColor(top, bottom, t);
            context.fill(x1, y, x2, Math.min(y2, y + 2), c);
        }
    }

    /** Füllt einen Kreis (Näherung aus Zeilen, für Status-Punkte). */
    public static void fillCircle(DrawContext context, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            double half = Math.sqrt(Math.max(0, radius * radius - dy * dy));
            int w = (int) Math.round(half);
            context.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    /** Prüft, ob ein Punkt in einem Rechteck liegt (für Hover-Tests). */
    public static boolean inRect(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }
}

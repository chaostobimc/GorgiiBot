package com.gorgiibot.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Kleine Zeichenhelfer für die Giveaway-Screens. */
public final class GuiUtil {
    private GuiUtil() {
    }

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

    /** Dunkelt eine ARGB-Farbe um den Faktor ab (Alpha bleibt). */
    public static int darken(int argb, float factor) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (int) (((argb >> 16) & 0xFF) * factor);
        int green = (int) (((argb >> 8) & 0xFF) * factor);
        int blue = (int) ((argb & 0xFF) * factor);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}

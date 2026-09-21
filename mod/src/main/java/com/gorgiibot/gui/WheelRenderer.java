package com.gorgiibot.gui;

import com.gorgiibot.GorgiiBotClient;
import com.gorgiibot.Participant;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * Glücksrad: Die Segmente werden einmalig in eine Textur gerastert (nur neu,
 * wenn sich die Teilnehmer ändern), pro Frame rotiert gezeichnet, die Namen
 * laufen radial mit.
 */
public class WheelRenderer {
    private static final int TEX_SIZE = 512;
    /** Zeiger-Winkel (oben) im Textur-Koordinatensystem. */
    private static final double POINTER_ANGLE = -Math.PI / 2.0;
    /** Ab dieser Teilnehmerzahl werden keine Namen mehr gezeichnet (Performance). */
    private static final int MAX_NAMES = 200;

    /** Segment-Farbpalette (ARGB, kräftig wie in der Web-App). */
    private static final int[] PALETTE = {
            0xFFE74C3C, 0xFF3498DB, 0xFFF1C40F, 0xFF2ECC71, 0xFF9B59B6,
            0xFFE67E22, 0xFF1ABC9C, 0xFFF368E0, 0xFF48C9B0, 0xFFF1948A,
            0xFF85C1E9, 0xFFF7DC6F
    };

    private final Identifier textureId = Identifier.of(GorgiiBotClient.MOD_ID, "wheel");
    private NativeImageBackedTexture texture;
    private NativeImage image;
    private int builtVersion = -1;
    private int builtCount = 0;

    /** Stellt sicher, dass die Rad-Textur zum Teilnehmerstand passt. */
    public void ensure(List<Participant> parts, int version) {
        if (texture != null && version == builtVersion && parts.size() == builtCount) {
            return;
        }
        if (image == null) {
            image = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
            texture = new NativeImageBackedTexture(() -> "gorgiibot-wheel", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
        }
        rasterize(parts);
        texture.upload();
        builtVersion = version;
        builtCount = parts.size();
    }

    private void rasterize(List<Participant> parts) {
        int center = TEX_SIZE / 2;
        double radius = center - 4;
        int count = Math.max(1, parts.size());
        double seg = Math.PI * 2.0 / count;
        boolean empty = parts.isEmpty();
        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                double dx = x + 0.5 - center;
                double dy = y + 0.5 - center;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist > radius) {
                    image.setColorArgb(x, y, 0x00000000);
                    continue;
                }
                double angle = Math.atan2(dy, dx);
                if (angle < 0) {
                    angle += Math.PI * 2.0;
                }
                int index = (int) (angle / seg);
                if (index >= count) {
                    index = count - 1;
                }
                int color = empty ? 0xFF3A3F4A : PALETTE[index % PALETTE.length];
                // Segment-Trennlinie
                double inSeg = angle - index * seg;
                if (!empty && count > 1 && inSeg < 0.012) {
                    color = GuiUtil.darken(color, 0.45f);
                }
                // Außenring
                if (dist > radius - 10) {
                    color = GuiUtil.darken(color, 0.55f);
                } else if (dist > radius - 14) {
                    color = 0xFFF5F5F5;
                }
                // Nabe (Mitte)
                if (dist < 34) {
                    color = dist < 30 ? 0xFF23262E : 0xFFF5F5F5;
                }
                image.setColorArgb(x, y, color);
            }
        }
    }

    /**
     * Berechnet aus der Rotation den Index des Segments unterm Zeiger.
     * Muss exakt zur Draw-Matrix passen (Rotation um die Radmitte).
     */
    public static int segmentUnderPointer(float rotation, int count) {
        if (count <= 0) {
            return -1;
        }
        double seg = Math.PI * 2.0 / count;
        double local = (POINTER_ANGLE - rotation) % (Math.PI * 2.0);
        if (local < 0) {
            local += Math.PI * 2.0;
        }
        return Math.min((int) (local / seg), count - 1);
    }

    /**
     * Zielrotation, damit der Zeiger auf Segment {@code index} landet.
     *
     * @param fraction Position innerhalb des Segments (0..1, z. B. 0.2–0.8 für Streuung)
     */
    public static float rotationForSegment(float fromRotation, int index, int count, int fullTurns,
                                          double fraction) {
        double seg = Math.PI * 2.0 / count;
        double clamped = Math.min(0.9, Math.max(0.1, fraction));
        double targetLocal = index * seg + seg * clamped;
        double twoPi = Math.PI * 2.0;
        double base = POINTER_ANGLE - targetLocal;
        double delta = (base - fromRotation) % twoPi;
        if (delta < 0) {
            delta += twoPi;
        }
        return (float) (fromRotation + delta + fullTurns * twoPi);
    }

    /** Zeichnet das Rad rotiert plus radial mitlaufende Namen. */
    public void draw(DrawContext context, TextRenderer textRenderer, List<Participant> parts,
                     int centerX, int centerY, int size, float rotation) {
        int half = size / 2;
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        matrices.rotate(rotation);
        matrices.translate(-half, -half);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId, 0, 0, 0f, 0f, size, size,
                TEX_SIZE, TEX_SIZE);
        matrices.popMatrix();

        int count = parts.size();
        if (count == 0 || count > MAX_NAMES) {
            return;
        }
        double seg = Math.PI * 2.0 / count;
        double textRadius = size * 0.5 * 0.68;
        int maxTextWidth = (int) (size * 0.5 * 0.52);
        for (int i = 0; i < count; i++) {
            double mid = rotation + i * seg + seg / 2.0;
            int px = centerX + (int) (Math.cos(mid) * textRadius);
            int py = centerY + (int) (Math.sin(mid) * textRadius);
            String name = GuiUtil.trimToWidth(textRenderer, parts.get(i).name, maxTextWidth);
            int nameWidth = textRenderer.getWidth(name);
            matrices.pushMatrix();
            matrices.translate(px, py);
            matrices.rotate((float) mid);
            context.drawText(textRenderer, name, -nameWidth / 2, -4, 0xFFFFFFFF, true);
            matrices.popMatrix();
        }
    }

    /** Gibt die GPU-Textur frei (beim Schließen des Screens). */
    public void close() {
        try {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
        } catch (Exception ignored) {
        }
        texture = null;
        image = null;
        builtVersion = -1;
    }
}

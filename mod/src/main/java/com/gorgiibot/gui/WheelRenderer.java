package com.gorgiibot.gui;

import com.gorgiibot.GorgiiBotClient;
import com.gorgiibot.Participant;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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
 *
 * <p><b>Warum zwei Texturen?</b> Ring und Nabe stecken in einer zweiten,
 * <i>nicht</i> rotierenden Textur. Vorher drehte sich das komplette Rad
 * inklusive Außenkante mit: die Kante wurde beim Verkleinern der 1024er-Textur
 * angetastet und flackerte dadurch im Kreis – das sah aus, als würde das Rad
 * „eiern“. Jetzt liegt der Rahmen still über den Segmenten und deckt deren
 * Kante ab; die Rotation läuft zusätzlich in Gleitkomma um den exakten
 * Mittelpunkt (vorher wurde halbe Pixel abgeschnitten).
 */
public class WheelRenderer {
    /** Hochauflösend (1024×1024), damit das Rad auch groß scharf bleibt. */
    private static final int TEX_SIZE = 1024;
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

    /** Radius der Segmente relativ zum Radradius (Rahmen deckt den Rand ab). */
    private static final double SEGMENT_RADIUS = 0.955;
    /** Außenring (dunkel) und weißer Ring, jeweils relativ zum Radradius. */
    private static final double RING_OUTER = 1.0;
    private static final double RING_INNER = 0.90;
    /** Nabe in der Mitte. */
    private static final double HUB_OUTER = 0.17;
    private static final double HUB_INNER = 0.145;

    private final Identifier segmentId = Identifier.of(GorgiiBotClient.MOD_ID, "wheel");
    private final Identifier frameId = Identifier.of(GorgiiBotClient.MOD_ID, "wheel_frame");
    private NativeImageBackedTexture segmentTexture;
    private NativeImage segmentImage;
    private NativeImageBackedTexture frameTexture;
    private NativeImage frameImage;
    private int builtVersion = Integer.MIN_VALUE;
    private int builtCount = -1;

    /** Stellt sicher, dass die Rad-Textur zum Teilnehmerstand passt. */
    public void ensure(List<Participant> parts, int version) {
        if (segmentTexture == null) {
            segmentImage = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
            segmentTexture = new NativeImageBackedTexture(() -> "gorgiibot-wheel", segmentImage);
            MinecraftClient.getInstance().getTextureManager().registerTexture(segmentId, segmentTexture);
        }
        if (frameTexture == null) {
            frameImage = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
            frameTexture = new NativeImageBackedTexture(() -> "gorgiibot-wheel-frame", frameImage);
            MinecraftClient.getInstance().getTextureManager().registerTexture(frameId, frameTexture);
            rasterizeFrame();
            frameTexture.upload();
        }
        if (version == builtVersion && parts.size() == builtCount && builtVersion != Integer.MIN_VALUE) {
            return;
        }
        rasterizeSegments(parts);
        segmentTexture.upload();
        builtVersion = version;
        builtCount = parts.size();
    }

    /** Nur die farbigen Segmente – der Rahmen liegt als zweite Textur darüber. */
    private void rasterizeSegments(List<Participant> parts) {
        int center = TEX_SIZE / 2;
        double radius = (center - 2) * SEGMENT_RADIUS;
        int count = Math.max(1, parts.size());
        double seg = Math.PI * 2.0 / count;
        boolean empty = parts.isEmpty();
        for (int y = 0; y < TEX_SIZE; y++) {
            double dy = y + 0.5 - center;
            for (int x = 0; x < TEX_SIZE; x++) {
                double dx = x + 0.5 - center;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist > radius) {
                    segmentImage.setColorArgb(x, y, 0x00000000);
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
                // Trennlinien zwischen den Segmenten
                double inSeg = angle - index * seg;
                double lineWidth = Math.max(0.004, 1.2 / radius);
                if (!empty && count > 1 && inSeg < lineWidth) {
                    color = GuiUtil.darken(color, 0.7f);
                }
                segmentImage.setColorArgb(x, y, color);
            }
        }
    }

    /** Rahmen + Nabe: statisch, wird nie mitgedreht. */
    private void rasterizeFrame() {
        int center = TEX_SIZE / 2;
        double base = center - 2;
        double ringOuter = base * RING_OUTER;
        double ringInner = base * RING_INNER;
        double hubOuter = base * HUB_OUTER;
        double hubInner = base * HUB_INNER;
        for (int y = 0; y < TEX_SIZE; y++) {
            double dy = y + 0.5 - center;
            for (int x = 0; x < TEX_SIZE; x++) {
                double dx = x + 0.5 - center;
                double dist = Math.sqrt(dx * dx + dy * dy);
                int color = 0x00000000;
                if (dist >= ringInner && dist <= ringOuter) {
                    // Außenring dunkel, innen ein heller Abschluss
                    color = dist > ringInner + (ringOuter - ringInner) * 0.45 ? 0xFF2A2A2A : 0xFFE8E8E8;
                } else if (dist <= hubOuter) {
                    color = dist >= hubInner ? 0xFFE8E8E8 : 0xFF2A2A2A;
                }
                frameImage.setColorArgb(x, y, color);
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
        if (segmentTexture == null || frameTexture == null) {
            return;
        }
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        // Gleitkomma-Translate: exakter Mittelpunkt, kein halbes Pixel Versatz.
        matrices.translate(centerX, centerY);
        matrices.rotate(rotation);
        matrices.translate(-size / 2f, -size / 2f);
        GuiUtil.drawTexture(context, segmentId, 0, 0, size, size, TEX_SIZE, TEX_SIZE);
        matrices.popMatrix();
        // Rahmen + Nabe stehen still (kein Flackern der Außenkante).
        GuiUtil.drawTexture(context, frameId, centerX - size / 2, centerY - size / 2, size, size,
                TEX_SIZE, TEX_SIZE);

        int count = parts.size();
        if (count == 0 || count > MAX_NAMES) {
            return;
        }
        double seg = Math.PI * 2.0 / count;
        double textRadius = size * 0.5 * 0.68;
        int maxTextWidth = (int) (size * 0.5 * 0.52);
        for (int i = 0; i < count; i++) {
            double mid = rotation + i * seg + seg / 2.0;
            // Gleitkomma-Position: Namen springen nicht von Pixel zu Pixel.
            float px = (float) (centerX + Math.cos(mid) * textRadius);
            float py = (float) (centerY + Math.sin(mid) * textRadius);
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
            MinecraftClient.getInstance().getTextureManager().destroyTexture(segmentId);
        } catch (Exception ignored) {
        }
        try {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(frameId);
        } catch (Exception ignored) {
        }
        segmentTexture = null;
        segmentImage = null;
        frameTexture = null;
        frameImage = null;
        builtVersion = Integer.MIN_VALUE;
        builtCount = -1;
    }
}

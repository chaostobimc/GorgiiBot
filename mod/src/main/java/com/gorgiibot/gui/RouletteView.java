package com.gorgiibot.gui;

import com.gorgiibot.Participant;
import com.gorgiibot.audio.GorgiiSounds;
import com.gorgiibot.avatar.AvatarManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Roulette im Stil der Web-App: horizontal scrollende Karten (nur Avatar +
 * Name, keine Tickets), Marker in der Mitte, Ease-Out-Landung auf dem vorher
 * per Zufall gezogenen Gewinner.
 */
public class RouletteView {
    public static final int CARD_WIDTH = 76;
    public static final int CARD_HEIGHT = 96;
    private static final int CARD_GAP = 8;
    private static final int PITCH = CARD_WIDTH + CARD_GAP;
    private static final int AVATAR = 52;
    private static final int STRIP_CARDS = 60;
    private static final int WINNER_INDEX = 47;
    private static final long SPIN_MILLIS = 6500;
    private static final int ACCENT = 0xFFFFC94D;

    public interface WinCallback {
        void onWin(Participant winner);
    }

    private final Random random = new Random();
    private List<Participant> strip = new ArrayList<>();
    private boolean spinning = false;
    private long spinStart = 0;
    private float scroll = 0;
    private float targetScroll = 0;
    private int lastCenterIndex = -1;
    private Participant winner;
    private WinCallback callback;

    public boolean isSpinning() {
        return spinning;
    }

    /** Startet einen Lauf: Gewinner steht fest, der Strip wird darum gebaut. */
    public void start(List<Participant> pool, Participant winner, WinCallback callback) {
        this.winner = winner;
        this.callback = callback;
        strip = new ArrayList<>(STRIP_CARDS);
        for (int i = 0; i < STRIP_CARDS; i++) {
            if (i == WINNER_INDEX) {
                strip.add(winner);
            } else {
                strip.add(pool.get(random.nextInt(pool.size())));
            }
        }
        targetScroll = WINNER_INDEX * PITCH + PITCH / 2f;
        scroll = 0;
        lastCenterIndex = -1;
        spinning = true;
        spinStart = System.currentTimeMillis();
    }

    /** Zeichnet Strip + Marker; ruft bei Landung einmal den Callback auf. */
    public void draw(DrawContext context, TextRenderer textRenderer, AvatarManager avatars,
                     int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, 0xFF101216);
        context.fill(x, y, x + width, y + 1, 0xFF2A2E36);
        context.fill(x, y + height - 1, x + width, y + height, 0xFF2A2E36);

        if (strip.isEmpty()) {
            GuiUtil.drawCenteredText(context, textRenderer, "Drücke „Start“, um zu ziehen",
                    x + width / 2, y + (height - 8) / 2, 0xFF8B949E, false);
            return;
        }
        if (spinning) {
            float t = (System.currentTimeMillis() - spinStart) / (float) SPIN_MILLIS;
            if (t >= 1f) {
                spinning = false;
                scroll = targetScroll;
                WinCallback cb = callback;
                callback = null;
                if (cb != null) {
                    cb.onWin(winner);
                }
            } else {
                scroll = targetScroll * easeOutQuint(t);
            }
            int centerIndex = (int) (scroll / PITCH);
            if (centerIndex != lastCenterIndex) {
                lastCenterIndex = centerIndex;
                GorgiiSounds.tick();
            }
        }
        int centerX = x + width / 2;
        int cardTop = y + (height - CARD_HEIGHT) / 2;
        context.enableScissor(x, y, x + width, y + height);
        int first = Math.max(0, (int) ((scroll - width / 2f) / PITCH) - 1);
        int last = Math.min(strip.size() - 1, (int) ((scroll + width / 2f) / PITCH) + 1);
        for (int i = first; i <= last; i++) {
            float cardCenter = centerX + (i * PITCH + PITCH / 2f - scroll);
            drawCard(context, textRenderer, avatars, strip.get(i),
                    (int) (cardCenter - CARD_WIDTH / 2f), cardTop, i == WINNER_INDEX && !spinning);
        }
        context.disableScissor();
        // Marker: Mittellinie + Pfeile oben/unten
        context.fill(centerX - 1, y + 4, centerX + 1, y + height - 4, ACCENT);
        for (int k = 0; k < 5; k++) {
            int w = 14 - k * 3;
            context.fill(centerX - w / 2, y + 4 + k * 3, centerX + (w + 1) / 2, y + 7 + k * 3, ACCENT);
            context.fill(centerX - w / 2, y + height - 7 - k * 3, centerX + (w + 1) / 2,
                    y + height - 4 - k * 3, ACCENT);
        }
    }

    private void drawCard(DrawContext context, TextRenderer textRenderer, AvatarManager avatars,
                          Participant p, int cardX, int cardTop, boolean highlighted) {
        int x2 = cardX + CARD_WIDTH;
        int y2 = cardTop + CARD_HEIGHT;
        context.fill(cardX, cardTop, x2, y2, 0xFF1B1E24);
        int border = highlighted ? ACCENT : 0xFF343945;
        GuiUtil.fillBorder(context, cardX, cardTop, x2, y2, border);
        if (highlighted) {
            context.fill(cardX + 1, cardTop + 1, x2 - 1, cardTop + 2, border);
            context.fill(cardX + 1, y2 - 2, x2 - 1, y2 - 1, border);
        }
        int ax = cardX + (CARD_WIDTH - AVATAR) / 2;
        int ay = cardTop + 8;
        AvatarManager.AvatarImage avatar = avatars.getAvatar(p.lower);
        if (avatar != null) {
            int s = avatar.size();
            context.drawTexture(RenderPipelines.GUI_TEXTURED, avatar.id(), ax, ay, 0f, 0f,
                    AVATAR, AVATAR, s, s);
        } else {
            // Platzhalter: Twitch-Farbe + Anfangsbuchstabe
            context.fill(ax, ay, ax + AVATAR, ay + AVATAR, p.color);
            context.fill(ax, ay, ax + AVATAR, ay + 1, 0x55FFFFFF);
            String initial = p.name.isEmpty() ? "?" : p.name.substring(0, 1).toUpperCase();
            int initialWidth = textRenderer.getWidth(initial);
            context.drawText(textRenderer, initial, ax + (AVATAR - initialWidth) / 2,
                    ay + (AVATAR - 8) / 2, 0xFFFFFFFF, true);
        }
        String name = GuiUtil.trimToWidth(textRenderer, p.name, CARD_WIDTH - 8);
        int nameColor = p.boosted ? 0xFFFFD166 : 0xFFE6E6E6;
        context.drawText(textRenderer, name, cardX + (CARD_WIDTH - textRenderer.getWidth(name)) / 2,
                cardTop + CARD_HEIGHT - 16, nameColor, false);
    }

    private static float easeOutQuint(float t) {
        float u = 1f - t;
        return 1f - u * u * u * u * u;
    }
}

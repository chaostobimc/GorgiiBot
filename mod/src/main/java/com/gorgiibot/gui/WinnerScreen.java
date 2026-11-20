package com.gorgiibot.gui;

import com.gorgiibot.ChatLine;
import com.gorgiibot.GiveawayState;
import com.gorgiibot.GorgiiBotClient;
import com.gorgiibot.Participant;
import com.gorgiibot.audio.GorgiiSounds;
import com.gorgiibot.avatar.AvatarManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gewinner-Screen im Vanilla-Stil: Avatar, Name, <b>live</b>
 * mitlaufender Chat-Verlauf, Bestätigungs-Countdown (der Gewinner muss sich
 * im Chat melden), Konfetti und Fanfare. Meldet sich niemand, folgt nach
 * Ablauf der Zeit automatisch ein Reroll – genau wie in der Web-App.
 */
public class WinnerScreen extends Screen {
    private static final int[] CONFETTI_COLORS = {
            0xFFFF5555, 0xFFFFAA00, 0xFF55FF55, 0xFF55FFFF, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF
    };

    private static class Confetto {
        float x, y, vx, vy;
        int color;
        int size;
    }

    private final GiveawayState state = GorgiiBotClient.state;
    private final Participant winner;
    private List<ChatLine> history;
    private int lastHistoryVersion = Integer.MIN_VALUE;

    private final Random random = new Random();
    private final List<Confetto> confetti = new ArrayList<>();
    private long lastFrameAt = 0;
    private long openedAt = 0;
    private int fanfareStep = 0;
    private long nextFanfareAt = 0;

    private boolean claimHandled = false;
    private boolean rerollStarted = false;

    public WinnerScreen(Participant winner) {
        super(Text.literal("Gewinner"));
        this.winner = winner;
        this.history = state.historyFor(winner.lower, 6);
        this.lastHistoryVersion = state.getHistoryVersion();
    }

    @Override
    protected void init() {
        if (openedAt == 0) {
            openedAt = System.currentTimeMillis();
            nextFanfareAt = openedAt + 200;
            fanfareStep = 0;
            if (state.config.confetti) {
                spawnConfetti();
            }
        }
        int pw = Math.min(480, this.width - 40);
        int px = (this.width - pw) / 2;
        int ph = 300;
        int by = this.height / 2 + ph / 2 - 32;
        int bw = (pw - 40) / 4;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Neu ziehen"), b -> drawAgain())
                .dimensions(px + 12, by, bw, 22).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Entfernen"), b -> removeAndBack())
                .dimensions(px + 16 + bw, by, bw, 22).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Bannen"), b -> banAndBack())
                .dimensions(px + 20 + bw * 2, by, bw, 22).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"), b -> back())
                .dimensions(px + 24 + bw * 3, by, bw, 22).build());
    }

    private void drawAgain() {
        if (client != null) {
            client.setScreen(new GiveawayScreen(true, false));
        }
    }

    private void removeAndBack() {
        state.remove(winner.lower);
        back();
    }

    private void banAndBack() {
        state.ban(winner.lower, winner.name);
        back();
    }

    private void back() {
        if (client != null) {
            client.setScreen(new GiveawayScreen());
        }
    }

    /** Der Gewinner hat geantwortet: Konfetti + ggf. aus dem Pool entfernen. */
    private void onClaimed() {
        if (state.config.confetti) {
            spawnConfetti();
        }
        if (state.config.autoRemoveConfirmed) {
            state.remove(winner.lower);
        }
    }

    /** Zeit abgelaufen: automatisch neu auslosen (wie in der Web-App). */
    private void autoReroll() {
        if (rerollStarted) {
            return;
        }
        rerollStarted = true;
        state.endClaim();
        if (client != null) {
            client.setScreen(new GiveawayScreen(true, true));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (client == null) {
            return;
        }
        // Live-Chat: neue Nachrichten des Gewinners sofort übernehmen
        int historyVersion = state.getHistoryVersion();
        if (historyVersion != lastHistoryVersion) {
            lastHistoryVersion = historyVersion;
            history = state.historyFor(winner.lower, 6);
        }
        if (!claimHandled && state.isClaimed()) {
            claimHandled = true;
            onClaimed();
        }

        // Kein renderBackground(): Blur ist nur einmal pro Frame erlaubt.
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        TextRenderer tr = this.textRenderer;
        int pw = Math.min(480, this.width - 40);
        int ph = 300;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        GuiUtil.panel(context, px, py, px + pw, py + ph, GuiUtil.PANEL, GuiUtil.BORDER);

        String title = state.isRerollClaim() ? "GEWINNER (REROLL)" : "GEWINNER";
        GuiUtil.drawCenteredText(context, tr, title, px + pw / 2, py + 8, GuiUtil.GOLD, true);

        int avatarSize = 64;
        int ax = px + pw / 2 - avatarSize / 2;
        int ay = py + 24;
        AvatarManager.AvatarImage avatar = state.avatars.getAvatar(winner.lower);
        if (avatar != null) {
            GuiUtil.drawTexture(context, avatar.id(), ax, ay, avatarSize, avatarSize, avatar.size(),
                    avatar.size());
        } else {
            context.fill(ax, ay, ax + avatarSize, ay + avatarSize, winner.color);
            String initial = winner.name.isEmpty() ? "?" : winner.name.substring(0, 1).toUpperCase();
            GuiUtil.drawCenteredText(context, tr, initial, px + pw / 2, ay + (avatarSize - 8) / 2,
                    0xFFFFFFFF, true);
        }
        GuiUtil.fillBorder(context, ax, ay, ax + avatarSize, ay + avatarSize, 0xFF202020);

        GuiUtil.drawCenteredText(context, tr, GuiUtil.trimToWidth(tr, winner.name, pw - 40),
                px + pw / 2, ay + avatarSize + 6, GuiUtil.TEXT, true);

        // ---- Bestätigungs-Status ----
        boolean pending = state.isClaimPending();
        boolean claimed = state.isClaimed();
        boolean expired = state.isClaimExpired();
        String status;
        int statusColor;
        if (claimed) {
            status = "Bestätigt - Glückwunsch!";
            statusColor = GuiUtil.GREEN;
        } else if (expired) {
            status = "Zeit abgelaufen - Reroll startet ...";
            statusColor = GuiUtil.RED;
        } else if (pending) {
            status = "Warte auf Bestätigung im Chat ...";
            statusColor = GuiUtil.YELLOW;
        } else {
            status = "Bestätigung beendet";
            statusColor = GuiUtil.TEXT_DIM;
        }
        GuiUtil.drawCenteredText(context, tr, GuiUtil.trimToWidth(tr, status, pw - 24),
                px + pw / 2, py + 122, statusColor, true);

        long remain = state.claimRemainMs();
        long total = state.claimTotalMs();
        int barX = px + 20;
        int barW = pw - 40;
        int barY = py + 136;
        context.fill(barX, barY, barX + barW, barY + 4, 0xFF202020);
        int filled = total <= 0 ? 0 : (int) (barW * Math.max(0, Math.min(1, remain / (double) total)));
        int barColor = claimed ? GuiUtil.GREEN : remain < 10_000 ? GuiUtil.RED : GuiUtil.YELLOW;
        if (filled > 1) {
            context.fill(barX, barY, barX + filled, barY + 4, barColor);
        }
        String timerText = claimed ? "Bestätigt"
                : pending ? "noch " + ((remain + 999) / 1000) + " s" : "0 s";
        GuiUtil.drawCenteredText(context, tr, timerText, px + pw / 2, barY + 7, GuiUtil.TEXT_DIM, true);

        // ---- Chat-Verlauf (live) ----
        int hy = barY + 22;
        context.drawText(tr, "Chat-Verlauf:", px + 20, hy, GuiUtil.TEXT_DIM, true);
        hy += 11;
        if (history.isEmpty()) {
            context.drawText(tr, "Noch keine Nachrichten", px + 20, hy, GuiUtil.TEXT_FAINT, true);
        } else {
            int shown = Math.min(4, history.size());
            int start = history.size() - shown;
            for (int i = start; i < history.size(); i++) {
                ChatLine line = history.get(i);
                String row = line.displayName() + ": " + line.text();
                context.drawText(tr, GuiUtil.trimToWidth(tr, row, pw - 40), px + 20, hy,
                        i == history.size() - 1 ? GuiUtil.TEXT : GuiUtil.TEXT_DIM, true);
                hy += 11;
            }
        }

        super.render(context, mouseX, mouseY, delta);
        updateFanfare();
        updateConfetti(context);

        // Auto-Reroll erst am Ende, damit der Frame noch vollständig gezeichnet wird
        if (client.currentScreen == this && !rerollStarted && state.isRerollDue()) {
            autoReroll();
        }
    }

    private void updateFanfare() {
        if (fanfareStep < 0) {
            return;
        }
        if (fanfareStep >= GorgiiSounds.FANFARE.length) {
            GorgiiSounds.levelup();
            fanfareStep = -1;
            return;
        }
        if (System.currentTimeMillis() >= nextFanfareAt) {
            GorgiiSounds.pling(GorgiiSounds.FANFARE[fanfareStep]);
            fanfareStep++;
            nextFanfareAt = System.currentTimeMillis() + 140;
        }
    }

    private void spawnConfetti() {
        confetti.clear();
        for (int i = 0; i < 150; i++) {
            Confetto c = new Confetto();
            c.x = random.nextFloat() * this.width;
            c.y = -10 - random.nextFloat() * this.height * 0.5f;
            c.vx = (random.nextFloat() - 0.5f) * 60;
            c.vy = 120 + random.nextFloat() * 160;
            c.color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.length)];
            c.size = 3 + random.nextInt(4);
            confetti.add(c);
        }
        lastFrameAt = System.currentTimeMillis();
    }

    private void updateConfetti(DrawContext context) {
        if (confetti.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        float dt = lastFrameAt == 0 ? 0.016f : Math.min(0.05f, (now - lastFrameAt) / 1000f);
        lastFrameAt = now;
        for (Confetto c : confetti) {
            c.vy += 260 * dt;
            if (c.vy > 420) {
                c.vy = 420;
            }
            c.x += c.vx * dt + (float) Math.sin(now / 240.0 + c.y * 0.05) * 30 * dt;
            c.y += c.vy * dt;
            if (c.y > this.height + 12) {
                c.y = -12;
                c.x = random.nextFloat() * this.width;
                c.vy = 120 + random.nextFloat() * 120;
            }
            int s = c.size;
            context.fill((int) c.x, (int) c.y, (int) c.x + s, (int) c.y + (s * 2 / 3 + 1), c.color);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

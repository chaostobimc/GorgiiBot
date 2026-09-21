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
 * Gewinner-Screen: großer Avatar, Name, <b>live</b> mitlaufender Chat-Verlauf,
 * Bestätigungs-Countdown (der Gewinner muss sich im Chat melden), Konfetti und
 * Fanfare. Meldet sich niemand, folgt nach Ablauf der Zeit automatisch ein
 * Reroll – genau wie in der Web-App.
 */
public class WinnerScreen extends Screen {
    private static final int[] CONFETTI_COLORS = {
            0xFFF85149, 0xFFFFC94D, 0xFF3FB950, 0xFF58A6FF, 0xFFBC8CFF, 0xFFFF7B72, 0xFF79C0FF
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
        int pw = Math.min(420, this.width - 40);
        int px = (this.width - pw) / 2;
        int ph = 270;
        int by = this.height / 2 + ph / 2 - 30;
        int bw = (pw - 32) / 3;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Neu ziehen"), b -> drawAgain())
                .dimensions(px + 12, by, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Entfernen"), b -> removeAndBack())
                .dimensions(px + 16 + bw, by, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"), b -> back())
                .dimensions(px + 20 + bw * 2, by, bw, 20).build());
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
        int pw = Math.min(420, this.width - 40);
        int ph = 270;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        context.fill(px, py, px + pw, py + ph, 0xFF14161B);
        GuiUtil.fillBorder(context, px, py, px + pw, py + ph, 0xFFFFC94D);

        String title = state.isRerollClaim() ? "GEWINNER (REROLL)" : "GEWINNER";
        GuiUtil.drawCenteredText(context, tr, title, px + pw / 2, py + 14, 0xFFFFC94D, false);

        int avatarSize = 64;
        int ax = px + pw / 2 - avatarSize / 2;
        int ay = py + 32;
        AvatarManager.AvatarImage avatar = state.avatars.getAvatar(winner.lower);
        if (avatar != null) {
            GuiUtil.drawTexture(context, avatar.id(), ax, ay, avatarSize, avatarSize, avatar.size(),
                    avatar.size());
            GuiUtil.fillBorder(context, ax, ay, ax + avatarSize, ay + avatarSize, 0x55343945);
        } else {
            context.fill(ax, ay, ax + avatarSize, ay + avatarSize, winner.color);
            String initial = winner.name.isEmpty() ? "?" : winner.name.substring(0, 1).toUpperCase();
            GuiUtil.drawCenteredText(context, tr, initial, px + pw / 2, ay + (avatarSize - 8) / 2,
                    0xFFFFFFFF, true);
        }

        GuiUtil.drawCenteredText(context, tr, GuiUtil.trimToWidth(tr, winner.name, pw - 40),
                px + pw / 2, ay + avatarSize + 8, 0xFFFFFFFF, false);

        // ---- Bestätigungs-Status ----
        boolean pending = state.isClaimPending();
        boolean claimed = state.isClaimed();
        boolean expired = state.isClaimExpired();
        String status;
        int statusColor;
        if (claimed) {
            status = "Bestätigt – Glückwunsch!";
            statusColor = 0xFF3FB950;
        } else if (expired) {
            status = "Zeit abgelaufen – Reroll startet …";
            statusColor = 0xFFF85149;
        } else if (pending) {
            status = "Warte auf Bestätigung im Chat …";
            statusColor = 0xFFFFC94D;
        } else {
            status = "Bestätigung beendet";
            statusColor = 0xFF8B949E;
        }
        GuiUtil.drawCenteredText(context, tr, GuiUtil.trimToWidth(tr, status, pw - 24),
                px + pw / 2, py + 118, statusColor, false);

        long remain = state.claimRemainMs();
        long total = state.claimTotalMs();
        int barX = px + 20;
        int barW = pw - 40;
        int barY = py + 132;
        context.fill(barX, barY, barX + barW, barY + 5, 0xFF23262E);
        int filled = total <= 0 ? 0 : (int) (barW * Math.max(0, Math.min(1, remain / (double) total)));
        int barColor = claimed ? 0xFF3FB950 : remain < 10_000 ? 0xFFF85149 : 0xFFFFC94D;
        if (filled > 0) {
            context.fill(barX, barY, barX + filled, barY + 5, barColor);
        }
        String timerText = claimed ? "Bestätigt"
                : pending ? "noch " + ((remain + 999) / 1000) + " s" : "0 s";
        GuiUtil.drawCenteredText(context, tr, timerText, px + pw / 2, barY + 8, 0xFF8B949E, false);

        // ---- Chat-Verlauf (live) ----
        int hy = barY + 24;
        context.drawText(tr, "Chat-Verlauf:", px + 20, hy, 0xFF5B6470, false);
        hy += 12;
        if (history.isEmpty()) {
            GuiUtil.drawCenteredText(context, tr, "Noch keine Nachrichten – warte auf den Chat",
                    px + pw / 2, hy, 0xFF5B6470, false);
        } else {
            int shown = Math.min(6, history.size());
            int start = history.size() - shown;
            for (int i = start; i < history.size(); i++) {
                ChatLine line = history.get(i);
                String row = line.displayName() + ": " + line.text();
                context.drawText(tr, GuiUtil.trimToWidth(tr, row, pw - 40), px + 20, hy,
                        i == history.size() - 1 ? 0xFFE6E6E6 : 0xFF8B949E, false);
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

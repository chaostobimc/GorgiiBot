package com.gorgiibot.gui;

import com.gorgiibot.ChatLine;
import com.gorgiibot.GiveawayState;
import com.gorgiibot.GorgiiBotClient;
import com.gorgiibot.Participant;
import com.gorgiibot.audio.GorgiiSounds;
import com.gorgiibot.avatar.AvatarManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gewinner-Screen: großer Avatar, Name, letzte Chatnachrichten, Konfetti und
 * Fanfare. Aktionen: Neu ziehen, Gewinner entfernen, Zurück.
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
    private final List<ChatLine> history;

    private final Random random = new Random();
    private final List<Confetto> confetti = new ArrayList<>();
    private long lastFrameAt = 0;
    private long openedAt = 0;
    private int fanfareStep = 0;
    private long nextFanfareAt = 0;

    public WinnerScreen(Participant winner) {
        super(Text.literal("Gewinner"));
        this.winner = winner;
        this.history = state.historyFor(winner.lower, 6);
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
        int by = this.height / 2 + 96;
        int bw = (pw - 32) / 3;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Neu ziehen"), b -> drawAgain())
                .dimensions(px + 12, by, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Gewinner entfernen"), b -> removeAndBack())
                .dimensions(px + 16 + bw, by, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"), b -> back())
                .dimensions(px + 20 + bw * 2, by, bw, 20).build());
    }

    private void drawAgain() {
        if (client != null) {
            client.setScreen(new GiveawayScreen(true));
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        TextRenderer tr = this.textRenderer;
        int pw = Math.min(420, this.width - 40);
        int ph = 250;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        context.fill(px, py, px + pw, py + ph, 0xFF14161B);
        GuiUtil.fillBorder(context, px, py, px + pw, py + ph, 0xFFFFC94D);

        GuiUtil.drawCenteredText(context, tr, "GEWINNER", px + pw / 2, py + 14, 0xFFFFC94D, false);

        int avatarSize = 64;
        int ax = px + pw / 2 - avatarSize / 2;
        int ay = py + 32;
        AvatarManager.AvatarImage avatar = state.avatars.getAvatar(winner.lower);
        if (avatar != null) {
            int s = avatar.size();
            context.drawTexture(RenderPipelines.GUI_TEXTURED, avatar.id(), ax, ay, 0f, 0f,
                    avatarSize, avatarSize, s, s);
        } else {
            context.fill(ax, ay, ax + avatarSize, ay + avatarSize, winner.color);
            String initial = winner.name.isEmpty() ? "?" : winner.name.substring(0, 1).toUpperCase();
            GuiUtil.drawCenteredText(context, tr, initial, px + pw / 2, ay + (avatarSize - 8) / 2,
                    0xFFFFFFFF, true);
        }
        GuiUtil.fillBorder(context, ax, ay, ax + avatarSize, ay + avatarSize, 0xFF343945);

        GuiUtil.drawCenteredText(context, tr, GuiUtil.trimToWidth(tr, winner.name, pw - 40),
                px + pw / 2, ay + avatarSize + 8, 0xFFFFFFFF, false);

        int hy = ay + avatarSize + 24;
        if (history.isEmpty()) {
            GuiUtil.drawCenteredText(context, tr, "Keine Chatnachrichten", px + pw / 2, hy, 0xFF5B6470,
                    false);
        } else {
            for (ChatLine line : history) {
                String row = line.displayName() + ": " + line.text();
                context.drawText(tr, GuiUtil.trimToWidth(tr, row, pw - 40), px + 20, hy, 0xFF8B949E,
                        false);
                hy += 11;
            }
        }

        super.render(context, mouseX, mouseY, delta);
        updateFanfare();
        updateConfetti(context);
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

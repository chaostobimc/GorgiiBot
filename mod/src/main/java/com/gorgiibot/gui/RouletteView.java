package com.gorgiibot.gui;

import com.gorgiibot.Participant;
import com.gorgiibot.audio.GorgiiSounds;
import com.gorgiibot.avatar.AvatarManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Roulette im Stil der Web-App: horizontal scrollende Karten (nur Avatar +
 * Name, keine Tickets), Marker in der Mitte, Ease-Out-Landung auf dem vorher
 * per Zufall gezogenen Gewinner.
 *
 * <p><b>Fix „Rahmen vor der Entscheidung“:</b> Früher bekam die
 * Gewinner-Karte schon <i>während</i> des Spins einen goldenen Rahmen – wer
 * genau hinsah, wusste vor der Landung, wer gewinnt. Jetzt gibt es
 * <b>während des Spins keinerlei Markierung</b>; der Rahmen erscheint erst
 * nach der Landung (und bleibt ~0,9 s sichtbar, bevor der Gewinner-Screen
 * öffnet).
 *
 * <p><b>Fix „nicht alle Profilbilder“:</b> Beim Zeichnen werden jetzt die
 * Avatare <i>aller</i> Karten der aktuellen Leiste (nicht nur der sichtbaren)
 * in die Lade-Warteschlange gelegt – zusammen mit dem proaktiven Prefetch im
 * Haupt-Screen laden dadurch alle Bilder, auch die, die gerade off-screen
 * vorbeiscrollen.
 *
 * <p>Wie in der Web-App sind die Teilnehmer <b>immer</b> sichtbar: Im
 * Ruhezustand (Idle) läuft eine langsame Endlos-Scrollanimation (45 px/s)
 * über die Teilnehmerliste. Erst beim Drehen wird eine eigene Spin-Leiste mit
 * dem Gewinner an fester Position aufgebaut.
 */
public class RouletteView {
    public static final int CARD_WIDTH = 96;
    public static final int CARD_HEIGHT = 124;
    private static final int CARD_GAP = 8;
    private static final int PITCH = CARD_WIDTH + CARD_GAP;
    private static final int AVATAR = 64;
    private static final int SPIN_CARDS = 60;
    private static final int WINNER_INDEX = 47;
    private static final long SPIN_MILLIS = 6500;
    /** Pause auf der gelandeten Karte (Rahmen sichtbar), bevor der Gewinner-Screen öffnet. */
    private static final long LANDED_PAUSE_MS = 900;
    /** Idle-Geschwindigkeit der Web-App (px/s). */
    private static final float IDLE_SPEED = 45f;
    private static final int MAX_IDLE_CARDS = 400;
    private static final int MARKER = 0xFFFFFFFF;

    private enum Mode {
        IDLE, SPINNING, LANDED
    }

    public interface WinCallback {
        void onWin(Participant winner);
    }

    private final Random random = new Random();

    // ---- Idle-Leiste (Teilnehmer, Endlos-Loop) ----
    private final List<Participant> idleCards = new ArrayList<>();
    private int idleSetWidth = 0;
    private float idleScroll = 0;
    private int idleBuiltVersion = Integer.MIN_VALUE;
    private int idleBuiltWidth = -1;

    // ---- Spin-Leiste ----
    private List<Participant> spinCards = new ArrayList<>();
    private float spinFrom = 0;
    private float spinTo = 0;
    private long spinStart = 0;
    private long landedAt = 0;
    private int lastCard = Integer.MIN_VALUE;

    private Mode mode = Mode.IDLE;
    private Participant winner;
    private WinCallback callback;
    private String hint = "Keine Teilnehmer";

    public boolean isSpinning() {
        return mode == Mode.SPINNING;
    }

    /**
     * Hält die Idle-Leiste aktuell und bewegt sie langsam weiter.
     *
     * @param pool      aktuelle Teilnehmer
     * @param version   Teilnehmer-Version (aus {@code GiveawayState})
     * @param viewWidth Breite des sichtbaren Bereichs
     * @param dt        vergangene Sekunden seit dem letzten Frame
     */
    public void tick(List<Participant> pool, int version, int viewWidth, float dt) {
        if (mode != Mode.IDLE) {
            return;
        }
        if (version != idleBuiltVersion || viewWidth != idleBuiltWidth) {
            buildIdle(pool, viewWidth);
            idleBuiltVersion = version;
            idleBuiltWidth = viewWidth;
        }
        if (idleSetWidth > 0 && pool.size() > 1) {
            idleScroll += IDLE_SPEED * dt;
            while (idleScroll >= idleSetWidth) {
                idleScroll -= idleSetWidth;
            }
        }
    }

    /** Baut die Idle-Leiste: Set so oft wiederholen, bis es breit genug ist, dann 2×. */
    private void buildIdle(List<Participant> pool, int viewWidth) {
        idleCards.clear();
        idleSetWidth = 0;
        if (pool.isEmpty()) {
            hint = "Keine Teilnehmer";
            return;
        }
        if (pool.size() == 1) {
            // Einzelner Teilnehmer: statisch mittig zeigen (kein Loop nötig).
            idleCards.add(pool.get(0));
            idleSetWidth = PITCH;
            hint = "";
            return;
        }
        hint = "";
        int target = Math.max(viewWidth, 240) * 3 / 2;
        List<Participant> set = new ArrayList<>();
        while (set.size() * PITCH < target && set.size() < MAX_IDLE_CARDS) {
            set.addAll(pool);
        }
        if (set.isEmpty()) {
            set.addAll(pool);
        }
        idleSetWidth = set.size() * PITCH;
        idleCards.addAll(set);
        idleCards.addAll(set); // zweiter Durchlauf für den nahtlosen Wrap
    }

    /** Startet einen Lauf: Gewinner steht fest, der Strip wird darum gebaut. */
    public void start(List<Participant> pool, Participant winner, WinCallback callback) {
        this.winner = winner;
        this.callback = callback;
        spinCards = new ArrayList<>(SPIN_CARDS);
        for (int i = 0; i < SPIN_CARDS; i++) {
            if (i == WINNER_INDEX) {
                spinCards.add(winner);
                continue;
            }
            Participant pick = pool.get(random.nextInt(pool.size()));
            int guard = 0;
            while (pick.lower.equals(winner.lower) && pool.size() > 1 && guard++ < 5) {
                pick = pool.get(random.nextInt(pool.size()));
            }
            spinCards.add(pick);
        }
        double jitter = (random.nextDouble() * 2 - 1) * (CARD_WIDTH / 2f - 14);
        spinTo = (float) (WINNER_INDEX * PITCH + PITCH / 2f + jitter);
        // Nahtlos aus der Idle-Bewegung heraus starten (niemals rückwärts scrollen).
        float from = idleScroll;
        if (from > spinTo - PITCH * 2) {
            from = from % PITCH;
        }
        spinFrom = from;
        lastCard = Integer.MIN_VALUE;
        landedAt = 0;
        mode = Mode.SPINNING;
        spinStart = System.currentTimeMillis();
    }

    /** Zurück in den Ruhezustand (Idle-Leiste wird neu aufgebaut). */
    public void backToIdle() {
        if (mode == Mode.SPINNING) {
            return;
        }
        mode = Mode.IDLE;
        idleBuiltVersion = Integer.MIN_VALUE;
    }

    /** Zeichnet Strip + Marker; ruft nach der Landepause einmal den Callback auf. */
    public void draw(DrawContext context, TextRenderer textRenderer, AvatarManager avatars,
                     int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, GuiUtil.PANEL);

        // Alle Avatare der aktuellen Leiste einreihen (auch off-screen) – Fix
        // „nicht alle Profilbilder laden“. Dedup macht das billig.
        if (avatars != null) {
            List<Participant> all = mode == Mode.IDLE ? idleCards : spinCards;
            if (!all.isEmpty()) {
                java.util.ArrayList<String> lowers = new java.util.ArrayList<>(all.size());
                for (Participant p : all) {
                    lowers.add(p.lower);
                }
                avatars.prefetch(lowers);
            }
        }

        List<Participant> cards;
        float scroll;
        // WICHTIG (Fix): Nur nach der Landung gibt es einen Rahmen. Während
        // des Spins ist die Gewinner-Karte bewusst nicht markiert.
        int highlight = -1;

        if (mode == Mode.IDLE) {
            if (idleCards.isEmpty()) {
                GuiUtil.drawCenteredText(context, textRenderer, hint, x + width / 2,
                        y + (height - 8) / 2, 0xFFA0A0A0, false);
                drawMarker(context, x, y, width, height);
                return;
            }
            if (idleCards.size() == 1) {
                int cardX = x + (width - CARD_WIDTH) / 2;
                int cardTop = y + (height - CARD_HEIGHT) / 2;
                drawCard(context, textRenderer, avatars, idleCards.get(0), cardX, cardTop, false);
                drawMarker(context, x, y, width, height);
                return;
            }
            cards = idleCards;
            scroll = idleScroll;
        } else {
            if (mode == Mode.SPINNING) {
                float t = (System.currentTimeMillis() - spinStart) / (float) SPIN_MILLIS;
                scroll = t >= 1f ? spinTo : spinFrom + (spinTo - spinFrom) * easeOutQuint(t);
                int cardIndex = (int) (scroll / PITCH);
                if (cardIndex != lastCard) {
                    lastCard = cardIndex;
                    GorgiiSounds.tick();
                }
                if (t >= 1f) {
                    // Gelangt: Rahmen zeigen, kurz innehalten, erst dann Callback.
                    mode = Mode.LANDED;
                    landedAt = System.currentTimeMillis();
                }
            } else {
                scroll = spinTo;
                highlight = WINNER_INDEX; // erst jetzt sichtbar
                if (System.currentTimeMillis() - landedAt >= LANDED_PAUSE_MS) {
                    WinCallback cb = callback;
                    callback = null;
                    if (cb != null) {
                        cb.onWin(winner);
                    }
                }
            }
            cards = spinCards;
        }

        int centerX = x + width / 2;
        int cardTop = y + (height - CARD_HEIGHT) / 2;
        int count = cards.size();
        context.enableScissor(x + 2, y + 2, x + width - 2, y + height - 2);
        int first = (int) Math.floor((scroll - width / 2f) / PITCH) - 1;
        int last = (int) Math.ceil((scroll + width / 2f) / PITCH) + 1;
        for (int i = first; i <= last; i++) {
            int index = i;
            if (mode == Mode.IDLE) {
                index = ((i % count) + count) % count; // Endlos-Wrap
            } else if (index < 0 || index >= count) {
                continue;
            }
            float cardCenter = centerX + (i * PITCH + PITCH / 2f - scroll);
            drawCard(context, textRenderer, avatars, cards.get(index),
                    (int) (cardCenter - CARD_WIDTH / 2f), cardTop, i == highlight);
        }
        context.disableScissor();
        drawMarker(context, x, y, width, height);
    }

    /** Marker: Mittellinie + Pfeile oben/unten. */
    private void drawMarker(DrawContext context, int x, int y, int width, int height) {
        int centerX = x + width / 2;
        context.fill(centerX - 1, y + 6, centerX + 1, y + height - 6, MARKER);
        for (int k = 0; k < 5; k++) {
            int w = 14 - k * 3;
            context.fill(centerX - w / 2, y + 6 + k * 3, centerX + (w + 1) / 2, y + 9 + k * 3, MARKER);
            context.fill(centerX - w / 2, y + height - 9 - k * 3, centerX + (w + 1) / 2,
                    y + height - 6 - k * 3, MARKER);
        }
    }

    private void drawCard(DrawContext context, TextRenderer textRenderer, AvatarManager avatars,
                          Participant p, int cardX, int cardTop, boolean highlighted) {
        int x2 = cardX + CARD_WIDTH;
        int y2 = cardTop + CARD_HEIGHT;
        // Flache Karte im Minecraft-Stil
        GuiUtil.panel(context, cardX, cardTop, x2, y2, 0xC0101010,
                highlighted ? MARKER : 0xFF404040);
        if (highlighted) {
            GuiUtil.fillBorder(context, cardX + 1, cardTop + 1, x2 - 1, y2 - 1, MARKER);
        }
        int ax = cardX + (CARD_WIDTH - AVATAR) / 2;
        int ay = cardTop + 10;
        AvatarManager.AvatarImage avatar = avatars.getAvatar(p.lower);
        if (avatar != null) {
            GuiUtil.drawTexture(context, avatar.id(), ax, ay, AVATAR, AVATAR, avatar.size(),
                    avatar.size());
            GuiUtil.fillBorder(context, ax, ay, ax + AVATAR, ay + AVATAR, 0xFF202020);
        } else {
            // Platzhalter: Twitch-Farbe + Anfangsbuchstabe
            context.fill(ax, ay, ax + AVATAR, ay + AVATAR, p.color);
            String initial = p.name.isEmpty() ? "?" : p.name.substring(0, 1).toUpperCase();
            GuiUtil.drawCenteredText(context, textRenderer, initial, ax + AVATAR / 2,
                    ay + (AVATAR - 8) / 2, 0xFFFFFFFF, true);
        }
        String name = GuiUtil.trimToWidth(textRenderer, p.name, CARD_WIDTH - 10);
        int nameColor = p.boosted ? 0xFFFFAA00 : 0xFFFFFFFF;
        GuiUtil.drawCenteredText(context, textRenderer, name, cardX + CARD_WIDTH / 2,
                cardTop + CARD_HEIGHT - 20, nameColor, false);
        if (p.boosted) {
            GuiUtil.drawCenteredText(context, textRenderer, "[SUB]", cardX + CARD_WIDTH / 2,
                    cardTop + CARD_HEIGHT - 11, 0xFFFFAA00, true);
        }
    }

    private static float easeOutQuint(float t) {
        float u = 1f - t;
        return 1f - u * u * u * u * u;
    }
}

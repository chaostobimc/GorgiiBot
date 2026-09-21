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
 * <p>Wie in der Web-App sind die Teilnehmer <b>immer</b> sichtbar: Im Ruhezust
 * (Idle) läuft eine langsame Endlos-Scrollanimation (45 px/s) über die
 * Teilnehmerliste. Erst beim Drehen wird eine eigene Spin-Leiste mit dem
 * Gewinner an fester Position aufgebaut.
 */
public class RouletteView {
    public static final int CARD_WIDTH = 76;
    public static final int CARD_HEIGHT = 96;
    private static final int CARD_GAP = 8;
    private static final int PITCH = CARD_WIDTH + CARD_GAP;
    private static final int AVATAR = 52;
    private static final int SPIN_CARDS = 60;
    private static final int WINNER_INDEX = 47;
    private static final long SPIN_MILLIS = 6500;
    /** Idle-Geschwindigkeit der Web-App (px/s). */
    private static final float IDLE_SPEED = 45f;
    private static final int MAX_IDLE_CARDS = 400;
    private static final int ACCENT = 0xFFFFC94D;

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
    private int lastCard = Integer.MIN_VALUE;

    private Mode mode = Mode.IDLE;
    private Participant winner;
    private WinCallback callback;
    private String hint = "Noch keine Teilnehmer – ab in den Chat!";

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
            hint = "Noch keine Teilnehmer – ab in den Chat!";
            return;
        }
        if (pool.size() < 2) {
            // Wie in der Web-App: erst ab 2 Teilnehmern lohnt die Endlos-Leiste.
            hint = "1 Teilnehmer – ab 2 läuft die Animation";
            return;
        }
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

    /** Zeichnet Strip + Marker; ruft bei Landung einmal den Callback auf. */
    public void draw(DrawContext context, TextRenderer textRenderer, AvatarManager avatars,
                     int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, 0xFF101216);
        context.fill(x, y, x + width, y + 1, 0xFF2A2E36);
        context.fill(x, y + height - 1, x + width, y + height, 0xFF2A2E36);

        List<Participant> cards;
        float scroll;
        int highlight = -1;

        if (mode == Mode.IDLE) {
            if (idleCards.isEmpty()) {
                GuiUtil.drawCenteredText(context, textRenderer, hint, x + width / 2,
                        y + (height - 8) / 2, 0xFF8B949E, false);
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
                    mode = Mode.LANDED;
                    WinCallback cb = callback;
                    callback = null;
                    if (cb != null) {
                        cb.onWin(winner);
                    }
                }
            } else {
                scroll = spinTo;
            }
            cards = spinCards;
            highlight = WINNER_INDEX;
        }

        int centerX = x + width / 2;
        int cardTop = y + (height - CARD_HEIGHT) / 2;
        int count = cards.size();
        context.enableScissor(x, y, x + width, y + height);
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
            // Komplette Textur (Quellgröße s×s) auf AVATAR×AVATAR skalieren,
            // damit das Bild zentriert und vollständig in der Box sitzt.
            GuiUtil.drawTexture(context, avatar.id(), ax, ay, AVATAR, AVATAR, avatar.size(),
                    avatar.size());
            GuiUtil.fillBorder(context, ax, ay, ax + AVATAR, ay + AVATAR, 0x55343945);
        } else {
            // Platzhalter: Twitch-Farbe + Anfangsbuchstabe
            context.fill(ax, ay, ax + AVATAR, ay + AVATAR, p.color);
            context.fill(ax, ay, ax + AVATAR, ay + 1, 0x55FFFFFF);
            String initial = p.name.isEmpty() ? "?" : p.name.substring(0, 1).toUpperCase();
            GuiUtil.drawCenteredText(context, textRenderer, initial, ax + AVATAR / 2,
                    ay + (AVATAR - 8) / 2, 0xFFFFFFFF, true);
        }
        String name = GuiUtil.trimToWidth(textRenderer, p.name, CARD_WIDTH - 8);
        int nameColor = p.boosted ? 0xFFFFD166 : 0xFFE6E6E6;
        GuiUtil.drawCenteredText(context, textRenderer, name, cardX + CARD_WIDTH / 2,
                cardTop + CARD_HEIGHT - 16, nameColor, false);
    }

    private static float easeOutQuint(float t) {
        float u = 1f - t;
        return 1f - u * u * u * u * u;
    }
}

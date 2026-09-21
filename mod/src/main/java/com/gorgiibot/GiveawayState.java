package com.gorgiibot;

import com.gorgiibot.audio.GorgiiSounds;
import com.gorgiibot.avatar.AvatarManager;
import com.gorgiibot.config.GorgiiConfig;
import com.gorgiibot.twitch.TwitchIrcClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Zentraler Giveaway-Zustand: Teilnehmer, Chat-Verlauf, Gewinner-Bestätigung
 * und Dienste.
 * Thread-sicher: Der IRC-Thread schreibt, der Render-Thread liest Momentaufnahmen.
 */
public class GiveawayState {
    private static final int MAX_HISTORY_PER_USER = 100;
    private static final int MAX_HISTORY_USERS = 3000;
    /** Pause zwischen „Zeit abgelaufen“ und dem automatischen Reroll (wie in der Web-App). */
    public static final long REROLL_DELAY_MS = 2200;

    /** Twitch-Standardfarben als Ersatz für Nutzer ohne eigene Farbe. */
    private static final int[] FALLBACK_COLORS = {
            0xFFFF0000, 0xFF0000FF, 0xFF00FF00, 0xFFB22222, 0xFFFF7F50,
            0xFF9ACD32, 0xFFFF4500, 0xFF2E8B57, 0xFFDAA520, 0xFFD2691E,
            0xFF5F9EA0, 0xFF1E90FF, 0xFFFF69B4, 0xFF8A2BE2, 0xFF00FF7F
    };

    public final GorgiiConfig config;
    public final TwitchIrcClient twitch;
    public final AvatarManager avatars;

    /** Aktiver Tab der UI (0 = Rad, 1 = Roulette), bleibt über Screens erhalten. */
    public int uiTab = 0;

    private final Map<String, Participant> participants = new LinkedHashMap<>();
    private final Map<String, Deque<ChatLine>> chatHistory = new LinkedHashMap<>();
    private final Random random = new Random();

    /** Wird bei jeder Teilnehmeränderung erhöht (Renderer-Cache). */
    private int version = 0;
    /** Wird bei jeder neuen Chatnachricht erhöht (live Verlaufsanzeige). */
    private int historyVersion = 0;
    private Participant lastWinner;

    // ---- Gewinner-Bestätigung (Claim) ----
    private Participant claimWinner;
    private boolean claimed;
    private boolean claimExpired;
    private boolean claimReroll;
    private long claimEndsAt;
    private long claimExpiredAt;
    private String claimMessage;
    private int claimBeep = -1;
    private boolean confirmSoundPending;

    public GiveawayState() {
        this.config = GorgiiConfig.load();
        this.twitch = new TwitchIrcClient(this);
        this.avatars = new AvatarManager();
    }

    /** Neue Chatnachricht: Verlauf speichern, ggf. als Teilnehmer aufnehmen. */
    public synchronized void onChat(ChatLine line) {
        recordHistory(line);
        // 1) Meldet sich der Gewinner? -> Bestätigung (wie in der Web-App)
        if (claimWinner != null && !claimed && !claimExpired
                && line.lower().equals(claimWinner.lower)) {
            claimed = true;
            claimMessage = line.text();
            confirmSoundPending = true; // Sound im Client-Thread abspielen
        }
        Participant existing = participants.get(line.lower());
        if (existing != null) {
            existing.color = line.color();
            if (line.boosted()) {
                existing.boosted = true;
            }
            return;
        }
        String keyword = config.keyword.trim().toLowerCase(Locale.ROOT);
        if (keyword.isEmpty()) {
            return;
        }
        if (!line.text().trim().toLowerCase(Locale.ROOT).startsWith(keyword)) {
            return;
        }
        if (config.requireSubscriber && !line.boosted()) {
            return;
        }
        participants.put(line.lower(), new Participant(line.displayName(), line.color(), line.boosted()));
        version++;
    }

    private void recordHistory(ChatLine line) {
        Deque<ChatLine> deque = chatHistory.computeIfAbsent(line.lower(), key -> new ArrayDeque<>());
        deque.addLast(line);
        historyVersion++;
        while (deque.size() > MAX_HISTORY_PER_USER) {
            deque.removeFirst();
        }
        while (chatHistory.size() > MAX_HISTORY_USERS) {
            String oldest = chatHistory.keySet().iterator().next();
            chatHistory.remove(oldest);
        }
    }

    /** Momentaufnahme der Teilnehmer (sicher für den Render-Thread). */
    public synchronized List<Participant> snapshot() {
        return new ArrayList<>(participants.values());
    }

    public synchronized int count() {
        return participants.size();
    }

    public synchronized int getVersion() {
        return version;
    }

    public synchronized int getHistoryVersion() {
        return historyVersion;
    }

    /** Die letzten Nachrichten eines Nutzers (für den Gewinner-Screen). */
    public synchronized List<ChatLine> historyFor(String lower, int max) {
        Deque<ChatLine> deque = chatHistory.get(lower);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        List<ChatLine> all = new ArrayList<>(deque);
        return all.subList(Math.max(0, all.size() - max), all.size());
    }

    /** Gewichtete Zufallsziehung (Subs/VIPs/Mods zählen subLuck-fach). */
    public synchronized Participant pickWinner() {
        return pickWinner(new ArrayList<>(participants.values()));
    }

    /** Gewichtete Zufallsziehung aus der übergebenen Liste. */
    public synchronized Participant pickWinner(List<Participant> pool) {
        if (pool.isEmpty()) {
            return null;
        }
        int total = 0;
        for (Participant p : pool) {
            total += weight(p);
        }
        int roll = random.nextInt(total);
        for (Participant p : pool) {
            roll -= weight(p);
            if (roll < 0) {
                lastWinner = p;
                return p;
            }
        }
        lastWinner = pool.get(0);
        return lastWinner;
    }

    private int weight(Participant p) {
        return (p.boosted && config.subLuck > 1) ? config.subLuck : 1;
    }

    public synchronized Participant getLastWinner() {
        return lastWinner;
    }

    // ---------------- Gewinner-Bestätigung ----------------

    /** Startet die Bestätigungsphase (Countdown läuft, bis der Gewinner schreibt). */
    public synchronized void startClaim(Participant winner, boolean reroll) {
        claimWinner = winner;
        claimReroll = reroll;
        claimed = false;
        claimExpired = false;
        claimMessage = null;
        claimBeep = -1;
        claimEndsAt = System.currentTimeMillis() + claimTotalMs();
        claimExpiredAt = 0;
    }

    /** Beendet die Bestätigungsphase (z. B. vor einer neuen Ziehung). */
    public synchronized void endClaim() {
        claimWinner = null;
        claimed = false;
        claimExpired = false;
        claimReroll = false;
        claimMessage = null;
        claimEndsAt = 0;
        claimExpiredAt = 0;
        claimBeep = -1;
    }

    /** Läuft eine Bestätigung und wartet noch auf den Gewinner? */
    public synchronized boolean isClaimPending() {
        return claimWinner != null && !claimed && !claimExpired;
    }

    /** Gibt es überhaupt eine aktive Gewinner-Phase (auch bestätigt/abgelaufen)? */
    public synchronized boolean isClaimActive() {
        return claimWinner != null;
    }

    public synchronized boolean isClaimed() {
        return claimWinner != null && claimed;
    }

    /** Countdown abgelaufen, der Gewinner hat sich nicht gemeldet. */
    public synchronized boolean isClaimExpired() {
        return claimWinner != null && claimExpired;
    }

    public synchronized boolean isRerollClaim() {
        return claimReroll;
    }

    /** Verbleibende Millisekunden (0 wenn bestätigt/abgelaufen). */
    public synchronized long claimRemainMs() {
        if (claimWinner == null || claimed || claimExpired) {
            return 0;
        }
        return Math.max(0, claimEndsAt - System.currentTimeMillis());
    }

    public synchronized long claimTotalMs() {
        return Math.max(5_000L, Math.min(300_000L, config.claimSeconds * 1000L));
    }

    public synchronized Participant getClaimWinner() {
        return claimWinner;
    }

    /** Die Nachricht, mit der der Gewinner bestätigt hat (oder null). */
    public synchronized String getClaimMessage() {
        return claimMessage;
    }

    /** Millisekunden seit dem Ablauf (für die Reroll-Pause). */
    public synchronized long claimExpiredForMs() {
        if (claimExpiredAt == 0) {
            return 0;
        }
        return System.currentTimeMillis() - claimExpiredAt;
    }

    /** Ist die Reroll-Pause nach dem Ablauf vorbei? */
    public synchronized boolean isRerollDue() {
        return isClaimExpired() && claimExpiredForMs() >= REROLL_DELAY_MS;
    }

    public synchronized boolean remove(String lower) {
        if (participants.remove(lower) != null) {
            version++;
            // Läuft noch eine Bestätigung für genau diesen Teilnehmer, ist sie
            // damit hinfällig. Bei bereits abgelaufener Zeit bleibt der Zustand
            // erhalten, damit der automatische Reroll noch greift.
            if (claimWinner != null && claimWinner.lower.equals(lower) && !claimed && !claimExpired) {
                endClaim();
            }
            return true;
        }
        return false;
    }

    /** Setzt Teilnehmer, Verlauf und Gewinner zurück. */
    public synchronized void clear() {
        participants.clear();
        chatHistory.clear();
        lastWinner = null;
        endClaim();
        version++;
    }

    /** Fügt Test-Teilnehmer ein (zum Ausprobieren ohne Chat). */
    public synchronized void addTestParticipants() {
        String[] names = {
                "AuroraFan88", "PixelGorg", "TwitchTom", "LunaPlays", "CraftyChris", "NovaStar",
                "ZockerAxel", "MiaMadness", "TurboTobi", "KeksKoenig", "StreamSusi", "LootLarry"
        };
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (participants.containsKey(lower)) {
                continue;
            }
            boolean boosted = random.nextBoolean();
            int color = fallbackColor(lower);
            participants.put(lower, new Participant(name, color, boosted));
            Deque<ChatLine> deque = new ArrayDeque<>();
            deque.add(new ChatLine(lower, name, color, config.keyword + " ich will gewinnen!",
                    System.currentTimeMillis() - 60000, boosted));
            chatHistory.put(lower, deque);
            historyVersion++;
        }
        version++;
    }

    /** Stabile Ersatzfarbe pro Name (Twitch-Palette). */
    public static int fallbackColor(String lower) {
        int hash = lower.hashCode() & 0x7fffffff;
        return FALLBACK_COLORS[hash % FALLBACK_COLORS.length];
    }

    /** Wird jeden Client-Tick aufgerufen (Reconnect-Watchdog + Claim-Countdown). */
    public void tick() {
        twitch.tick();
        tickClaim();
    }

    private void tickClaim() {
        boolean soundConfirm;
        boolean soundTimeout = false;
        synchronized (this) {
            soundConfirm = confirmSoundPending;
            confirmSoundPending = false;
            if (claimWinner != null && !claimed && !claimExpired) {
                long remain = claimEndsAt - System.currentTimeMillis();
                int secs = (int) Math.ceil(Math.max(0, remain) / 1000.0);
                if (secs <= 5 && secs >= 1 && secs != claimBeep) {
                    claimBeep = secs;
                    GorgiiSounds.countdown(secs <= 2);
                }
                if (remain <= 0) {
                    claimExpired = true;
                    claimExpiredAt = System.currentTimeMillis();
                    soundTimeout = true;
                    if (config.autoRemoveTimeout) {
                        remove(claimWinner.lower);
                    }
                }
            }
        }
        if (soundConfirm) {
            GorgiiSounds.confirm();
        }
        if (soundTimeout) {
            GorgiiSounds.timeout();
        }
    }
}

package com.gorgiibot.avatar;

import com.gorgiibot.GorgiiBotClient;
import com.gorgiibot.GiveawayState;
import com.gorgiibot.Participant;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Lädt Twitch-Profilbilder (Avatar-URL via decapi.me, genau wie die Web-App)
 * und hält sie als GPU-Texturen vor.
 *
 * <p><b>Fix „nicht alle Profilbilder laden“:</b> Die alte Version lud nur die
 * Avatare, die gerade sichtbar waren, und verwarf neue Anfragen, sobald 6
 * Downloads parallel liefen. Bei vielen Teilnehmern (und im Roulette, wo
 * während des Spins ständig neue Karten vorbeiscrollen) kamen dadurch viele
 * Bilder nie an. Außerdem hämmerte sie decapi.me mit 3 parallelen Requests,
 * woraufhin der Dienst drosselte und fast alles fehlschlug (60 s Sperre).
 *
 * <p>Jetzt gilt: <b>Eine Warteschlange, ein Worker, gedrosselt wie die
 * Web-App</b> (1 Request, ~300 ms Pause). {@link #prefetch} lädt proaktiv
 * <i>alle</i> Teilnehmer (nicht nur sichtbare), jeder Name steht höchstens
 * einmal in der Schlange, Fehlschläge werden nach kurzer Zeit erneut
 * versucht. Netzwerk + Dekodieren laufen im Hintergrund, nur die
 * Textur-Registrierung passiert im Render-Thread.
 */
public class AvatarManager {
    /** Geladener Avatar inkl. Texturgröße (für UVs). */
    public record AvatarImage(Identifier id, int size) {
    }

    /** Alle Avatare werden auf diese Kantenlänge normiert (Power-of-Two, GPU-freundlich). */
    private static final int TEX_SIZE = 128;
    private static final int MAX_CACHED = 500;
    /** Pause zwischen zwei Downloads (decapi.me-Schutz, wie die Web-App mit 400 ms). */
    private static final long REQUEST_DELAY_MS = 300L;
    /** Nach einem Fehlversuch so lange warten, bevor erneut versucht wird. */
    private static final long FAIL_COOLDOWN_MS = 15_000L;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /** Warteschlange aller noch zu ladenden Logins (Reihenfolge = Priorität). */
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    /** Logins, die bereits in der Schlange oder im Download sind (Dedup). */
    private final Set<String> queued = new HashSet<>();
    /** Zwischengespeicherte decapi-URLs (spart einen Request bei Wiederholungen). */
    private final Map<String, String> urlCache = new HashMap<>();

    /** LRU-Cache (access-order): Name -> Avatar. */
    private final Map<String, AvatarImage> cache = new LinkedHashMap<>(64, 0.75f, true);
    private final Map<String, Long> failedAt = new HashMap<>();

    public AvatarManager() {
        Thread worker = new Thread(this::workerLoop, "GorgiiBot-Avatar");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Gibt den Avatar zurück oder null, falls er noch lädt / fehlschlägt.
     * Beim ersten Aufruf pro Name wird der Download in die Warteschlange
     * gelegt (nie verworfen, auch bei vielen Teilnehmern).
     */
    public synchronized AvatarImage getAvatar(String lower) {
        if (!avatarsEnabled()) {
            return null;
        }
        AvatarImage cached = cache.get(lower);
        if (cached != null) {
            return cached;
        }
        enqueueLocked(lower);
        return null;
    }

    /**
     * Lädt proaktiv alle Avatare einer Liste (z. B. alle Teilnehmer oder alle
     * Karten der aktuellen Roulette-Leiste). Bereits geladene, wartende oder
     * kürzlich fehlgeschlagene Namen werden übersprungen.
     */
    public void prefetch(Collection<String> lowers) {
        if (lowers == null || lowers.isEmpty() || !avatarsEnabled()) {
            return;
        }
        synchronized (this) {
            for (String lower : lowers) {
                if (lower != null && !lower.isEmpty()) {
                    enqueueLocked(lower);
                }
            }
        }
    }

    /** Lädt proaktiv die Avatare aller Teilnehmer einer Liste. */
    public void prefetchParticipants(List<Participant> participants) {
        if (participants == null || participants.isEmpty()) {
            return;
        }
        // Sichtbare zuerst? Die Liste ist bereits in Anzeige-Reihenfolge –
        // einfach in dieser Reihenfolge einreihen.
        java.util.ArrayList<String> lowers = new java.util.ArrayList<>(participants.size());
        for (Participant p : participants) {
            if (p != null) {
                lowers.add(p.lower);
            }
        }
        prefetch(lowers);
    }

    private void enqueueLocked(String lower) {
        if (cache.containsKey(lower) || queued.contains(lower)) {
            return;
        }
        Long failed = failedAt.get(lower);
        if (failed != null && System.currentTimeMillis() - failed < FAIL_COOLDOWN_MS) {
            return;
        }
        queued.add(lower);
        queue.offer(lower);
    }

    private static boolean avatarsEnabled() {
        GiveawayState state = GorgiiBotClient.state;
        return state != null && state.config.avatars;
    }

    private void workerLoop() {
        while (true) {
            String lower;
            try {
                lower = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            try {
                if (avatarsEnabled()) {
                    download(lower);
                }
            } catch (Exception ignored) {
                fail(lower);
            } finally {
                synchronized (this) {
                    queued.remove(lower);
                }
                // decapi.me schonen: kurze Pause zwischen den Requests.
                try {
                    Thread.sleep(REQUEST_DELAY_MS);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private void download(String lower) {
        try {
            String url;
            synchronized (this) {
                url = urlCache.get(lower);
            }
            if (url == null) {
                url = getString("https://decapi.me/twitch/avatar/" + lower).trim();
                if (!url.startsWith("http") || url.length() > 500) {
                    fail(lower);
                    return;
                }
                synchronized (this) {
                    urlCache.put(lower, url);
                }
            }
            byte[] data = getBytes(url);
            if (data.length == 0 || data.length > 4_000_000) {
                fail(lower);
                return;
            }
            NativeImage raw = NativeImage.read(data);
            NativeImage square;
            try {
                square = toSquare(raw, TEX_SIZE);
            } finally {
                raw.close();
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                square.close();
                fail(lower);
                return;
            }
            client.execute(() -> register(lower, square));
        } catch (Exception e) {
            fail(lower);
        }
    }

    private void register(String lower, NativeImage image) {
        try {
            Identifier id = Identifier.of(GorgiiBotClient.MOD_ID, "avatar/" + lower);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "gorgiibot-avatar", image);
            MinecraftClient client = MinecraftClient.getInstance();
            client.getTextureManager().registerTexture(id, texture);
            texture.upload();
            synchronized (this) {
                cache.put(lower, new AvatarImage(id, image.getWidth()));
                failedAt.remove(lower);
                evictLocked(client);
            }
        } catch (Exception e) {
            image.close();
            fail(lower);
        }
    }

    private synchronized void fail(String lower) {
        failedAt.put(lower, System.currentTimeMillis());
    }

    private void evictLocked(MinecraftClient client) {
        while (cache.size() > MAX_CACHED) {
            String oldest = cache.keySet().iterator().next();
            AvatarImage removed = cache.remove(oldest);
            try {
                client.getTextureManager().destroyTexture(removed.id());
            } catch (Exception ignored) {
            }
        }
    }

    /** Schneidet mittig-quadratisch zu und skaliert auf die Zielgröße (ARGB). */
    private static NativeImage toSquare(NativeImage raw, int out) {
        int width = raw.getWidth();
        int height = raw.getHeight();
        int side = Math.min(width, height);
        int ox = (width - side) / 2;
        int oy = (height - side) / 2;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, out, out, false);
        for (int y = 0; y < out; y++) {
            int sy = oy + (y * side) / out;
            for (int x = 0; x < out; x++) {
                int sx = ox + (x * side) / out;
                image.setColorArgb(x, y, raw.getColorArgb(sx, sy));
            }
        }
        return image;
    }

    private String getString(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "GorgiiBot/1.1 (Minecraft mod)")
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String getBytes(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "GorgiiBot/1.1 (Minecraft mod)")
                .GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }
}

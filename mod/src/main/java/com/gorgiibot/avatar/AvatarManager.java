package com.gorgiibot.avatar;

import com.gorgiibot.GorgiiBotClient;
import com.gorgiibot.GiveawayState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Lädt Twitch-Profilbilder (Avatar-URL via decapi.me, genau wie die Web-App)
 * und hält sie als GPU-Texturen vor. Netzwerk + Dekodieren laufen im
 * Hintergrund, nur die Textur-Registrierung passiert im Render-Thread.
 *
 * <p>Wichtig für die Performance: Jeder Name wird höchstens gleichzeitig
 * einmal geladen, und nach einem Fehlschlag gibt es eine Sperrzeit. Sonst
 * würde jeder Frame einen neuen Download anstoßen (decapi.me drosselt dann
 * und es lädt gar nichts mehr).
 */
public class AvatarManager {
    /** Geladener Avatar inkl. Texturgröße (für UVs). */
    public record AvatarImage(Identifier id, int size) {
    }

    /** Alle Avatare werden auf diese Kantenlänge normiert (Power-of-Two, GPU-freundlich). */
    private static final int TEX_SIZE = 64;
    private static final int MAX_CACHED = 200;
    private static final int MAX_IN_FLIGHT = 6;
    /** Nach einem Fehlversuch so lange nicht erneut probieren. */
    private static final long FAIL_COOLDOWN_MS = 60_000L;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private final ExecutorService pool;

    /** LRU-Cache (access-order): Name -> Avatar. */
    private final Map<String, AvatarImage> cache = new LinkedHashMap<>(64, 0.75f, true);
    private final Set<String> inFlight = new HashSet<>();
    private final Map<String, Long> failedAt = new HashMap<>();

    public AvatarManager() {
        ThreadFactory factory = r -> {
            Thread thread = new Thread(r, "GorgiiBot-Avatar");
            thread.setDaemon(true);
            return thread;
        };
        this.pool = Executors.newFixedThreadPool(3, factory);
    }

    /**
     * Gibt den Avatar zurück oder null, falls er noch lädt / fehlschlägt.
     * Beim ersten Aufruf pro Name wird der Download im Hintergrund gestartet.
     */
    public synchronized AvatarImage getAvatar(String lower) {
        if (!avatarsEnabled()) {
            return null;
        }
        AvatarImage cached = cache.get(lower);
        if (cached != null) {
            return cached;
        }
        if (inFlight.contains(lower) || inFlight.size() >= MAX_IN_FLIGHT) {
            return null;
        }
        Long failed = failedAt.get(lower);
        if (failed != null && System.currentTimeMillis() - failed < FAIL_COOLDOWN_MS) {
            return null;
        }
        inFlight.add(lower);
        pool.submit(() -> download(lower));
        return null;
    }

    private static boolean avatarsEnabled() {
        GiveawayState state = GorgiiBotClient.state;
        return state != null && state.config.avatars;
    }

    private void download(String lower) {
        try {
            String url = getString("https://decapi.me/twitch/avatar/" + lower).trim();
            if (!url.startsWith("http")) {
                fail(lower);
                return;
            }
            byte[] data = getBytes(url);
            if (data.length == 0 || data.length > 4_000_000) {
                fail(lower);
                return;
            }
            NativeImage square = toSquare(NativeImage.read(data), TEX_SIZE);
            MinecraftClient.getInstance().execute(() -> register(lower, square));
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
                inFlight.remove(lower);
                failedAt.remove(lower);
                evictLocked(client);
            }
        } catch (Exception e) {
            fail(lower);
        }
    }

    private synchronized void fail(String lower) {
        inFlight.remove(lower);
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
                .header("User-Agent", "GorgiiBot/1.0 (Minecraft mod)")
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private byte[] getBytes(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "GorgiiBot/1.0 (Minecraft mod)")
                .GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }
}

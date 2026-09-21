package com.gorgiibot.avatar;

import com.gorgiibot.GorgiiBotClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Lädt Twitch-Profilbilder (Avatar-URL via decapi.me) und hält sie als
 * GPU-Texturen vor. Netzwerk + Dekodieren laufen im Hintergrund, nur die
 * Textur-Registrierung passiert im Render-Thread.
 */
public class AvatarManager {
    /** Geladener Avatar inkl. Texturgröße (für UVs). */
    public record AvatarImage(Identifier id, int size) {
    }

    /** Max. Avatar-Seite in px (wird runterskaliert, spart GPU-RAM). */
    private static final int MAX_SIZE = 96;
    private static final int MAX_CACHED = 150;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /** LRU-Cache (access-order): Name -> Avatar. */
    private final Map<String, AvatarImage> cache = new LinkedHashMap<>(64, 0.75f, true);
    private final Set<String> inFlight = new HashSet<>();

    /**
     * Gibt den Avatar zurück oder null, falls er noch lädt / fehlschlägt.
     * Beim ersten Aufruf pro Name wird der Download im Hintergrund gestartet.
     */
    public synchronized AvatarImage getAvatar(String lower) {
        AvatarImage cached = cache.get(lower);
        if (cached != null) {
            return cached;
        }
        if (!inFlight.add(lower)) {
            return null;
        }
        CompletableFuture.runAsync(() -> download(lower));
        return null;
    }

    private void download(String lower) {
        try {
            String url = getString("https://decapi.me/twitch/avatar/" + lower).trim();
            if (!url.startsWith("http")) {
                return;
            }
            byte[] data = getBytes(url);
            if (data.length == 0 || data.length > 4_000_000) {
                return;
            }
            NativeImage square = toSquare96(NativeImage.read(data));
            MinecraftClient.getInstance().execute(() -> register(lower, square));
        } catch (Exception e) {
            synchronized (this) {
                inFlight.remove(lower);
            }
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
                evictLocked(client);
            }
        } catch (Exception e) {
            synchronized (this) {
                inFlight.remove(lower);
            }
        }
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

    /** Schneidet mittig-quadratisch zu und skaliert auf max. 96 px (ARGB). */
    private static NativeImage toSquare96(NativeImage raw) {
        int width = raw.getWidth();
        int height = raw.getHeight();
        int side = Math.min(width, height);
        int ox = (width - side) / 2;
        int oy = (height - side) / 2;
        int out = Math.max(1, Math.min(MAX_SIZE, side));
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
                .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private byte[] getBytes(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }
}

package com.gorgiibot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** JSON-Konfiguration (config/gorgiibot.json). Wird live angewendet. */
public class GorgiiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Twitch-Kanalname (klein, ohne #). */
    public String channel = "";
    /** Chat-Schlüsselwort für die Teilnahme. */
    public String keyword = "!teilnahme";
    /** Nur Subs/VIPs/Mods dürfen teilnehmen. */
    public boolean requireSubscriber = false;
    /** Los-Gewicht für Subs/VIPs/Mods (1 = kein Bonus, max. 5). */
    public int subLuck = 1;
    /** Sounds an/aus. */
    public boolean sound = true;
    /** Konfetti an/aus. */
    public boolean confetti = true;
    /** Twitch-Profilbilder laden (wie die Web-App via decapi.me). */
    public boolean avatars = true;
    /** Sekunden, die der Gewinner Zeit hat, sich im Chat zu melden. */
    public int claimSeconds = 60;
    /** Bestätigten Gewinner automatisch aus dem Pool entfernen. */
    public boolean autoRemoveConfirmed = true;
    /** Gewinner ohne Reaktion nach Ablauf automatisch entfernen. */
    public boolean autoRemoveTimeout = true;
    /**
     * Banliste: Login (klein) -> Anzeigename. Gesperrte User können nicht
     * teilnehmen und werden sofort aus dem Pool entfernt. Bleibt über
     * Neustarts erhalten (in derselben JSON-Datei).
     */
    public Map<String, String> banned = new LinkedHashMap<>();

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("gorgiibot.json");
    }

    public static GorgiiConfig load() {
        GorgiiConfig config = new GorgiiConfig();
        try {
            Path path = file();
            if (Files.exists(path)) {
                GorgiiConfig loaded = GSON.fromJson(Files.readString(path), GorgiiConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } else {
                config.save();
            }
        } catch (Exception e) {
            config = new GorgiiConfig();
        }
        config.validate();
        return config;
    }

    public void validate() {
        if (channel == null) {
            channel = "";
        }
        channel = channel.trim().toLowerCase(Locale.ROOT);
        if (channel.startsWith("#")) {
            channel = channel.substring(1);
        }
        if (keyword == null || keyword.isBlank()) {
            keyword = "!teilnahme";
        }
        keyword = keyword.trim();
        if (subLuck < 1) {
            subLuck = 1;
        }
        if (subLuck > 5) {
            subLuck = 5;
        }
        if (claimSeconds < 10) {
            claimSeconds = 10;
        }
        if (claimSeconds > 300) {
            claimSeconds = 300;
        }
        if (banned == null) {
            banned = new LinkedHashMap<>();
        }
        // Keys normalisieren (klein), leere Einträge verwerfen.
        Map<String, String> clean = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : banned.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String key = e.getKey().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            String display = e.getValue() == null || e.getValue().isBlank() ? key : e.getValue().trim();
            clean.put(key, display);
        }
        banned = clean;
    }

    public void save() {
        validate();
        try {
            Files.writeString(file(), GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }
}

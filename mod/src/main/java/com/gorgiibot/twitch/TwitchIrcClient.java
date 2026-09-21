package com.gorgiibot.twitch;

import com.gorgiibot.ChatLine;
import com.gorgiibot.GiveawayState;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Anonymer Twitch-IRC-Client über WebSocket (nur Lesen, kein Token nötig).
 * Wie die Web-App: justinfan-Login, Tags-Capability, Auto-Reconnect mit Backoff.
 */
public class TwitchIrcClient {
    public enum Status {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    private static final URI TWITCH_WS = URI.create("wss://irc-ws.chat.twitch.tv:443");

    private final GiveawayState state;
    private final ExecutorService exec;

    private volatile Status status = Status.DISCONNECTED;
    private volatile String statusDetail = "";
    private volatile boolean wantConnection = false;
    private volatile String channel = "";
    private volatile WebSocket socket;
    private volatile int failures = 0;
    private volatile long nextRetryAt = 0;

    public TwitchIrcClient(GiveawayState state) {
        this.state = state;
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "GorgiiBot-Twitch");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Status status() {
        return status;
    }

    public String statusDetail() {
        return statusDetail;
    }

    public String channel() {
        return channel;
    }

    /** Verbinden (nicht-blockierend, eigentlicher Aufbau im Tick). */
    public void connect(String newChannel) {
        String clean = newChannel == null ? "" : newChannel.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        if (clean.isEmpty()) {
            status = Status.ERROR;
            statusDetail = "Kein Kanal gesetzt (Einstellungen).";
            return;
        }
        channel = clean;
        wantConnection = true;
        failures = 0;
        status = Status.DISCONNECTED;
        statusDetail = "";
        nextRetryAt = 0;
    }

    public void disconnect() {
        wantConnection = false;
        status = Status.DISCONNECTED;
        statusDetail = "";
        WebSocket ws = socket;
        socket = null;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").exceptionally(t -> null);
            } catch (Exception ignored) {
            }
        }
    }

    /** Muss regelmäßig vom Client-Thread aufgerufen werden. */
    public void tick() {
        if (!wantConnection) {
            return;
        }
        if (status == Status.CONNECTED || status == Status.CONNECTING) {
            return;
        }
        if (System.currentTimeMillis() < nextRetryAt) {
            return;
        }
        status = Status.CONNECTING;
        statusDetail = "Verbinde mit #" + channel + " …";
        final String target = channel;
        exec.submit(() -> doConnect(target));
    }

    private void doConnect(String target) {
        try {
            HttpClient http = HttpClient.newHttpClient();
            IrcListener listener = new IrcListener(target);
            socket = http.newWebSocketBuilder().buildAsync(TWITCH_WS, listener).join();
        } catch (Exception e) {
            onConnectionLost(trimmedMessage(e));
        }
    }

    private void onConnectionLost(String detail) {
        socket = null;
        if (!wantConnection) {
            status = Status.DISCONNECTED;
            statusDetail = "";
            return;
        }
        failures++;
        status = Status.ERROR;
        statusDetail = detail;
        long backoff = Math.min(30_000L, 1000L * (1L << Math.min(failures, 10)));
        nextRetryAt = System.currentTimeMillis() + backoff;
    }

    private static String trimmedMessage(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.isEmpty()) {
            message = t.getClass().getSimpleName();
        }
        return message.length() > 90 ? message.substring(0, 90) : message;
    }

    private static int parseColor(String raw, String nick) {
        if (raw != null && raw.startsWith("#") && raw.length() == 7) {
            try {
                return 0xFF000000 | Integer.parseInt(raw.substring(1), 16);
            } catch (NumberFormatException ignored) {
            }
        }
        return GiveawayState.fallbackColor(nick);
    }

    private static boolean hasBadge(String badges, String want) {
        if (badges == null || badges.isEmpty()) {
            return false;
        }
        for (String badge : badges.split(",")) {
            if (badge.startsWith(want + "/")) {
                return true;
            }
        }
        return false;
    }

    private class IrcListener implements WebSocket.Listener {
        private final String target;
        private final StringBuilder buffer = new StringBuilder();
        private volatile WebSocket ws;

        IrcListener(String target) {
            this.target = target;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            this.ws = webSocket;
            webSocket.request(Long.MAX_VALUE);
            String nick = "justinfan" + (1000 + ThreadLocalRandom.current().nextInt(9000));
            send("CAP REQ :twitch.tv/tags twitch.tv/commands");
            send("PASS SCHMOOPIIE");
            send("NICK " + nick);
            send("JOIN #" + target);
        }

        private void send(String line) {
            WebSocket w = ws;
            if (w == null) {
                return;
            }
            try {
                w.sendText(line, true).exceptionally(t -> null);
            } catch (Exception ignored) {
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            int idx;
            while ((idx = buffer.indexOf("\n")) >= 0) {
                String line = buffer.substring(0, idx).trim();
                buffer.delete(0, idx + 1);
                if (!line.isEmpty()) {
                    handleLine(line);
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (wantConnection) {
                onConnectionLost("Getrennt (" + statusCode + "). Neuer Versuch …");
            } else {
                status = Status.DISCONNECTED;
                statusDetail = "";
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (wantConnection) {
                onConnectionLost(trimmedMessage(error));
            }
        }

        private void handleLine(String line) {
            if (line.startsWith("PING")) {
                send("PONG" + line.substring(4));
                return;
            }
            if (line.contains(" RECONNECT")) {
                try {
                    if (ws != null) {
                        ws.abort();
                    }
                } catch (Exception ignored) {
                }
                onConnectionLost("Serverwechsel (RECONNECT) …");
                return;
            }
            Map<String, String> tags = new HashMap<>();
            String rest = line;
            if (rest.startsWith("@")) {
                int space = rest.indexOf(' ');
                if (space < 0) {
                    return;
                }
                for (String pair : rest.substring(1, space).split(";")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        tags.put(pair.substring(0, eq), pair.substring(eq + 1));
                    }
                }
                rest = rest.substring(space + 1);
            }
            String[] parts = rest.split(" ", 4);
            if (parts.length < 2) {
                return;
            }
            String command = parts[1];
            if (command.equals("JOIN") || command.equals("366")) {
                status = Status.CONNECTED;
                statusDetail = "Verbunden mit #" + channel;
                failures = 0;
                return;
            }
            if (!command.equals("PRIVMSG")) {
                return;
            }
            // :nick!user@host PRIVMSG #kanal :text
            String prefix = parts[0];
            int bang = prefix.indexOf('!');
            if (bang < 0 || parts.length < 4) {
                return;
            }
            String nick = prefix.substring(1, bang).toLowerCase(Locale.ROOT);
            String text = parts[3];
            if (text.startsWith(":")) {
                text = text.substring(1);
            }
            if (text.isEmpty()) {
                return;
            }
            String display = tags.getOrDefault("display-name", "");
            if (display.isEmpty()) {
                display = nick;
            }
            int color = parseColor(tags.get("color"), nick);
            boolean boosted = "1".equals(tags.get("subscriber"))
                    || "1".equals(tags.get("mod"))
                    || "1".equals(tags.get("vip"))
                    || hasBadge(tags.get("badges"), "broadcaster")
                    || hasBadge(tags.get("badges"), "founder");
            status = Status.CONNECTED;
            statusDetail = "Verbunden mit #" + channel;
            failures = 0;
            state.onChat(new ChatLine(nick, display, color, text, System.currentTimeMillis(), boosted));
        }
    }
}

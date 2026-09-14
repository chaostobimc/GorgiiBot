/* ============================================================================
 * GorgiiBot – twitch.js
 * Anonymer Twitch-Chat-Client über das offizielle IRC-WebSocket-Gateway
 * (wss://irc-ws.chat.twitch.tv). Kein Token / kein Backend nötig – reines
 * Mitlesen des Chats. Sendet keine Nachrichten.
 *
 * Events: 'status'  -> {status, message}  (connecting|open|closed|error)
 *         'message' -> {login, display, color, text, ts, mod, sub}
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});
  const URL = 'wss://irc-ws.chat.twitch.tv:443';

  let ws = null;
  let channel = '';
  let wantedChannel = '';   // Ziel-Kanal (für Auto-Reconnect)
  let userClosed = false;
  let reconnectAttempt = 0;
  let reconnectTimer = 0;
  let joinConfirmed = false;
  const listeners = { status: [], message: [] };

  function on(evt, cb) {
    if (listeners[evt]) listeners[evt].push(cb);
  }

  function emit(evt, data) {
    (listeners[evt] || []).forEach(function (cb) {
      try { cb(data); } catch (e) { /* Listener-Fehler isolieren */ }
    });
  }

  function emitStatus(status, message) {
    emit('status', { status: status, message: message || '', channel: channel });
  }

  /** Kanalnamen normalisieren: nur a-z0-9_ , ohne # */
  function cleanChannel(input) {
    return String(input || '')
      .trim()
      .toLowerCase()
      .replace(/^#/, '')
      .replace(/^(https?:\/\/)?(www\.)?twitch\.tv\//, '')
      .replace(/[^a-z0-9_]/g, '')
      .slice(0, 25);
  }

  /** IRC-Tags parsen: "@a=1;b=2 :..." -> {a:"1", b:"2"} */
  function parseTags(tagStr) {
    const tags = {};
    if (!tagStr) return tags;
    tagStr.split(';').forEach(function (kv) {
      const i = kv.indexOf('=');
      if (i > 0) tags[kv.slice(0, i)] = kv.slice(i + 1);
    });
    return tags;
  }

  /** Eine rohe IRC-Zeile auswerten */
  function handleLine(line) {
    if (!line) return;

    // PING -> PONG (Verbindung halten)
    if (line.indexOf('PING') === 0) {
      try { ws.send('PONG :tmi.twitch.tv'); } catch (e) {}
      return;
    }

    // Tags abtrennen
    let tags = {};
    let rest = line;
    if (rest.charAt(0) === '@') {
      const sp = rest.indexOf(' ');
      tags = parseTags(rest.slice(1, sp));
      rest = rest.slice(sp + 1);
    }

    // PRIVMSG: ":user!user@user.tmi.twitch.tv PRIVMSG #kanal :text"
    const privIdx = rest.indexOf(' PRIVMSG ');
    if (privIdx > 0) {
      const prefixEnd = rest.indexOf('!');
      const login = rest.slice(1, prefixEnd > 0 ? prefixEnd : privIdx).toLowerCase();
      const msgIdx = rest.indexOf(' :', privIdx);
      const text = msgIdx >= 0 ? rest.slice(msgIdx + 2) : '';
      const display = tags['display-name'] || login;
      emit('message', {
        login: login,
        display: display,
        color: tags.color || '',
        text: text,
        ts: Date.now(),
        mod: tags.mod === '1',
        sub: tags.subscriber === '1' || (tags.badges && tags.badges.indexOf('subscriber') === 0)
      });
      return;
    }

    // Erfolgreicher Join: "JOIN #kanal"
    if (rest.indexOf(' JOIN #') > 0) {
      if (!joinConfirmed) {
        joinConfirmed = true;
        reconnectAttempt = 0;
        emitStatus('open', 'Verbunden mit #' + channel);
      }
      return;
    }

    // ROOMSTATE bestätigt ebenfalls den Kanal
    if (rest.indexOf(' ROOMSTATE #') > 0) {
      if (!joinConfirmed) {
        joinConfirmed = true;
        reconnectAttempt = 0;
        emitStatus('open', 'Verbunden mit #' + channel);
      }
      return;
    }

    // NOTICE mit Fehlern (z. B. falscher Kanal)
    if (rest.indexOf(' NOTICE ') > 0) {
      const msgIdx = rest.indexOf(' :');
      const text = msgIdx >= 0 ? rest.slice(msgIdx + 2) : rest;
      // Nur echte Fehler melden, keine "login unused"-Hinweise
      if (/error|failed|invalid|bad/i.test(text)) {
        emitStatus('error', text);
      }
    }
  }

  function connect(rawChannel) {
    const clean = cleanChannel(rawChannel);
    if (!clean) {
      emitStatus('error', 'Bitte einen gültigen Kanalnamen eingeben.');
      return false;
    }
    disconnect(true); // evtl. alte Verbindung schließen
    userClosed = false;
    wantedChannel = clean;
    channel = clean;
    joinConfirmed = false;
    reconnectAttempt = 0;

    emitStatus('connecting', 'Verbinde mit #' + clean + ' …');

    try {
      ws = new WebSocket(URL);
    } catch (e) {
      emitStatus('error', 'WebSocket konnte nicht geöffnet werden.');
      scheduleReconnect();
      return false;
    }

    ws.onopen = function () {
      try {
        // Anonyme Anmeldung (nur Lesen)
        const nick = 'justinfan' + (10000 + Math.floor(Math.random() * 89999));
        ws.send('CAP REQ :twitch.tv/tags twitch.tv/commands');
        ws.send('PASS SCHMOOPIIE');
        ws.send('NICK ' + nick);
        ws.send('JOIN #' + clean);
      } catch (e) {}
      // Fallback: Falls kein JOIN bestätigt wird, trotzdem als offen werten
      window.setTimeout(function () {
        if (!joinConfirmed && ws && ws.readyState === 1) {
          joinConfirmed = true;
          emitStatus('open', 'Verbunden mit #' + channel);
        }
      }, 4000);
    };

    ws.onmessage = function (ev) {
      String(ev.data || '')
        .split('\r\n')
        .forEach(handleLine);
    };

    ws.onerror = function () {
      emitStatus('error', 'Verbindungsfehler – versuche erneut …');
    };

    ws.onclose = function () {
      ws = null;
      if (!userClosed) {
        emitStatus('connecting', 'Verbindung verloren – Reconnect …');
        scheduleReconnect();
      }
    };

    return true;
  }

  /** Exponentieller Backoff für Reconnects (max. 30s) */
  function scheduleReconnect() {
    if (userClosed || !wantedChannel) return;
    window.clearTimeout(reconnectTimer);
    reconnectAttempt++;
    const delay = Math.min(30000, 1000 * Math.pow(2, Math.min(5, reconnectAttempt - 1)));
    reconnectTimer = window.setTimeout(function () {
      if (!userClosed && wantedChannel) connect(wantedChannel);
    }, delay);
  }

  /** silent=true: kein Status-Event (für interne Neuverbindung) */
  function disconnect(silent) {
    userClosed = true;
    wantedChannel = silent ? wantedChannel : '';
    window.clearTimeout(reconnectTimer);
    reconnectAttempt = 0;
    joinConfirmed = false;
    if (ws) {
      try { ws.onclose = null; ws.onerror = null; ws.close(); } catch (e) {}
      ws = null;
    }
    if (!silent) {
      channel = '';
      emitStatus('closed', 'Getrennt.');
    }
  }

  function isConnected() {
    return !!(ws && ws.readyState === 1 && joinConfirmed);
  }

  function getChannel() { return channel; }

  GB.twitch = {
    on: on,
    connect: connect,
    disconnect: disconnect,
    isConnected: isConnected,
    getChannel: getChannel,
    cleanChannel: cleanChannel
  };
})();

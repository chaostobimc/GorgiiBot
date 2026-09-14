/* ============================================================================
 * GorgiiBot – overlay.js
 * Transparente Overlay-Seite für OBS-Browserquellen: zeigt NUR die Bühne
 * (Glücksrad oder Case Opening) plus kompaktes Gewinner-Banner.
 * - Konfiguration komplett per URL-Parameter (siehe OBS-Dialog der Hauptapp).
 * - Baut seinen Pool selbst aus demselben Twitch-Chat (kein geteilter
 *   Speicher nötig) – daher das Overlay vor Giveaway-Start öffnen.
 * - Gedreht wird im Overlay (OBS: Rechtsklick -> Interagieren, oder
 *   Leertaste im geöffneten Overlay-Tab).
 * Nutzt dieselben Module wie die Hauptapp (twitch/pool/wheel/case/winner).
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});
  const U = GB.util;

  let booted = false;
  let spinning = false;
  let cfg = null;

  function intParam(q, name, def, min, max) {
    const v = parseInt(q.get(name) || '', 10);
    if (isNaN(v)) return def;
    return Math.min(max, Math.max(min, v));
  }

  /** Konfiguration aus URL lesen (Fallback: zuletzt gespeicherte) */
  function readConfig() {
    const q = new URLSearchParams(location.search);
    let c = null;
    if (location.search && location.search.length > 1) {
      const keywords = String(q.get('keywords') || '!giveaway')
        .split(',')
        .map(function (x) { return x.trim(); })
        .filter(Boolean);
      c = {
        channel: GB.twitch.cleanChannel(q.get('channel') || ''),
        view: q.get('view') === 'case' ? 'case' : 'wheel',
        mode: q.get('mode') === 'all' ? 'all' : 'keywords',
        keywords: keywords.length ? keywords : ['!giveaway'],
        spinSeconds: intParam(q, 'spin', 8, 3, 20),
        claimSeconds: intParam(q, 'claim', 60, 10, 300),
        prize: q.get('prize') || '',
        ignoreCommands: q.get('ignore') !== '0',
        defaultBots: q.get('bots') !== '0',
        banlist: q.get('ban') || '',
        sound: q.get('sound') === '1',
        theme: q.get('theme') === 'light' ? 'light' : 'dark'
      };
      try { localStorage.setItem('gorgiibot.overlay', JSON.stringify(c)); } catch (e) {}
    } else {
      try { c = JSON.parse(localStorage.getItem('gorgiibot.overlay') || 'null'); } catch (e) { c = null; }
      if (!c || typeof c !== 'object') {
        c = {
          channel: '', view: 'wheel', mode: 'keywords', keywords: ['!giveaway'],
          spinSeconds: 8, claimSeconds: 60, prize: '', ignoreCommands: true,
          defaultBots: true, banlist: '', sound: false, theme: 'dark'
        };
      }
    }
    return c;
  }

  function init() {
    if (booted) return;
    booted = true;
    cfg = readConfig();

    // Store laden und mit Overlay-Konfiguration überschreiben
    GB.store.load();
    const st = GB.store.state;
    st.theme = cfg.theme;
    st.view = cfg.view;
    st.collectionOpen = true;
    const s = st.settings;
    s.mode = cfg.mode;
    s.keywords = cfg.keywords;
    s.spinSeconds = cfg.spinSeconds;
    s.claimSeconds = cfg.claimSeconds;
    s.prize = cfg.prize;
    s.ignoreCommands = cfg.ignoreCommands;
    s.defaultBots = cfg.defaultBots;
    s.banlist = cfg.banlist;
    s.sound = cfg.sound;
    // Hinweis: Pool wird NICHT wiederhergestellt – Overlay startet immer frisch.

    GB.ui.applyTheme(st.theme);
    GB.audio.setEnabled(cfg.sound);
    GB.audio.setVolume(0.6);

    // Pool ohne sichtbare Liste (entkoppeltes Dummy-Element),
    // Bühnen-Aktualisierung über minimale GB.app-Schnittstelle
    GB.pool.init(document.createElement('div'));
    GB.app = { refreshStages: refreshStages };
    GB.wheel.init('wheelCanvas');
    GB.caseOp.init('caseViewport', 'caseTrack');
    applyView(cfg.view);

    GB.twitch.on('status', onStatus);
    GB.twitch.on('message', onMessage);
    GB.winner.on('onTick', onTick);
    GB.winner.on('onClaimed', onClaimed);
    GB.winner.on('onTimeout', onTimeout);

    U.$('#ovSpin').addEventListener('click', function () {
      GB.audio.ensure();
      doSpin(false);
    });
    document.addEventListener('keydown', function (ev) {
      if (ev.code === 'Space') {
        ev.preventDefault();
        GB.audio.ensure();
        doSpin(false);
      }
    });

    if (cfg.channel) {
      setStatus('Verbinde mit #' + cfg.channel + ' …');
      GB.twitch.connect(cfg.channel);
    } else {
      setStatus('Kein Kanal – ?channel=NAME an die URL hängen.');
    }
    refreshStages();
  }

  function applyView(view) {
    const isWheel = view !== 'case';
    U.$('#wheelWrap').classList.toggle('hidden', !isWheel);
    U.$('#caseWrap').classList.toggle('hidden', isWheel);
    const label = U.$('#ovSpin span');
    if (label) label.textContent = isWheel ? 'Rad drehen' : 'Case öffnen';
    window.requestAnimationFrame(function () {
      GB.wheel.resize();
      if (!isWheel) GB.caseOp.backToIdle();
    });
  }

  /** Bühnen mit aktuellen Teilnehmern versorgen (wird vom Pool aufgerufen) */
  function refreshStages() {
    const list = GB.pool.ordered();
    GB.wheel.setData(list);
    GB.caseOp.setData(list);
    const has = list.length >= 2;
    U.$('#wheelEmpty').classList.toggle('hidden', has);
    U.$('#caseEmpty').classList.toggle('hidden', has);
    U.$('#ovCount').textContent = String(list.length);
  }

  function setStatus(t) {
    const el = U.$('#ovStatus');
    if (el) el.textContent = t;
  }

  function onStatus(ev) {
    if (ev.status === 'open') setStatus('Live mit #' + ev.channel);
    else if (ev.status === 'connecting') setStatus('Verbinde …');
    else if (ev.status === 'closed') setStatus('Getrennt.');
    else if (ev.status === 'error') setStatus('Fehler: ' + (ev.message || 'Verbindung'));
  }

  function onMessage(m) {
    GB.store.state.stats.messages++;
    // Gewinner-Claim hat Vorrang
    if (GB.winner.isPending()) {
      const w = GB.winner.current();
      if (w && m.login === w.login) {
        GB.winner.claim('chat');
        return;
      }
    }
    GB.pool.tryAdd(m);
  }

  function doSpin(isReroll) {
    if (spinning) return;
    if (GB.winner.isPending()) return;
    const list = GB.pool.ordered();
    if (list.length < 2) {
      setStatus('Noch zu wenige Teilnehmer (' + list.length + '/2).');
      return;
    }
    GB.winner.reset();
    hideWinner();
    spinning = true;
    U.$('#ovSpin').disabled = true;
    setStatus(isReroll ? 'Reroll läuft …' : 'Auslosung läuft …');

    const idx = U.randInt(0, list.length);
    const winner = list[idx];
    const engine = cfg.view === 'case' ? GB.caseOp : GB.wheel;
    engine.spinTo(idx, cfg.spinSeconds * 1000).then(function () {
      spinning = false;
      GB.audio.win();
      showWinner(winner, isReroll);
      GB.winner.start(winner, { isReroll: isReroll, claimSeconds: cfg.claimSeconds });
      // Spin-Button bleibt bis Claim/Timeout gesperrt
    });
  }

  function showWinner(p, isReroll) {
    U.$('#ovWinner').classList.remove('hidden');
    U.$('#ovAvatar').innerHTML = GB.pool.avatarHTML(p, 40);
    U.$('#ovName').textContent = p.display || p.login;
    U.$('#ovLogin').textContent = '@' + p.login;
    const prize = (cfg.prize || '').trim();
    U.$('#ovPrize').textContent = prize ? prize : '';
    setWinStatus(
      isReroll
        ? 'Reroll – warte auf Bestätigung im Chat …'
        : 'Warte auf Bestätigung im Chat …',
      'pending'
    );
  }

  function hideWinner() {
    U.$('#ovWinner').classList.add('hidden');
  }

  function setWinStatus(t, kind) {
    const el = U.$('#ovWinStatus');
    if (el) {
      el.textContent = t;
      el.setAttribute('data-kind', kind || 'pending');
    }
  }

  function onTick(remainMs, totalMs) {
    const bar = U.$('#ovBar');
    const secs = U.$('#ovSecs');
    const pct = totalMs > 0 ? (remainMs / totalMs) * 100 : 0;
    if (bar) {
      bar.style.width = pct.toFixed(1) + '%';
      bar.classList.toggle('danger', remainMs < 10000 && GB.winner.isPending());
    }
    if (secs) {
      secs.textContent = GB.winner.isPending()
        ? Math.max(0, Math.ceil(remainMs / 1000)) + ' s'
        : '';
    }
  }

  function onClaimed(p) {
    setWinStatus('Bestätigt! ' + p.display + ' hat sich gemeldet.', 'ok');
    setStatus('Gewinner bestätigt: ' + p.display);
    GB.pool.remove(p.login);
    U.$('#ovSpin').disabled = false;
    if (cfg.view === 'case') GB.caseOp.backToIdle();
    window.setTimeout(function () {
      if (!GB.winner.isPending()) hideWinner();
    }, 8000);
  }

  function onTimeout(p) {
    if (!p) return;
    setWinStatus('Zeit abgelaufen – Reroll …', 'timeout');
    setStatus('Zeit abgelaufen – Reroll …');
    GB.pool.remove(p.login);
    GB.winner.reset();
    window.setTimeout(function () {
      if (GB.pool.count() < 2) {
        setStatus('Zu wenige Teilnehmer für Reroll.');
        U.$('#ovSpin').disabled = false;
        hideWinner();
        return;
      }
      doSpin(true);
    }, 900);
  }

  GB.overlay = {
    init: init,
    doSpin: doSpin,
    config: function () { return cfg; }
  };

  document.addEventListener('DOMContentLoaded', init);
})();

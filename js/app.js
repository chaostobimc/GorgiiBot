/* ============================================================================
 * GorgiiBot – app.js
 * Einstiegspunkt: verdrahtet alle Module (Twitch-Chat -> Pool -> Bühne ->
 * Gewinner), bindet alle Bedienelemente und stellt Demo-Daten bereit.
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});
  const U = GB.util;

  let spinning = false;

  // Demo-Namen für Tests ohne Twitch-Verbindung
  const DEMO_NAMES = [
    'Gorgii', 'PixelPanda', 'LunaPlays', 'NoScopeNina', 'Kaffeekrieger',
    'LootLama', 'SilentStorm', 'TurboTobi', 'MondMaus', 'ChatChamp',
    'FrischFritz', 'NebulaNerd', 'PixelPirat', 'QuantumQuinn', 'RetroRalle',
    'StreamSusi', 'TaktikTom', 'WolkenWilli', 'ZockerZoe', 'BitBastler',
    'DrLoot', 'EmoteEmma', 'FragFelix', 'GamerGreta'
  ];

  // ---------------- Boot ----------------

  function init() {
    GB.store.load();
    const st = GB.store.state;

    // Theme + Sound initial anwenden
    GB.ui.applyTheme(st.theme);
    GB.audio.setEnabled(st.settings.sound);
    GB.audio.setVolume(st.settings.volume);
    GB.ui.renderSoundBtn();

    // Module initialisieren
    GB.pool.init(U.$('#roster'));
    GB.pool.restore(st.participants);
    GB.wheel.init('wheelCanvas');
    GB.roulette.init('caseViewport', 'caseTrack');
    // Migration: alter View-Wert 'case' heißt jetzt 'roulette'
    setView((st.view === 'roulette' || st.view === 'case') ? 'roulette' : 'wheel', true);

    // UI mit gespeicherten Werten füllen
    bindControls();
    applySettingsToUI();

    // Twitch-Events
    GB.twitch.on('status', onConnStatus);
    GB.twitch.on('message', onChatMessage);

    // Gewinner-Events
    GB.winner.on('onTick', onClaimTick);
    GB.winner.on('onClaimed', onClaimed);
    GB.winner.on('onTimeout', onClaimTimeout);
    GB.winner.on('onClose', onWinnerClose);

    // Tastaturkürzel
    document.addEventListener('keydown', onKey);

    GB.ui.updateStats();
    GB.ui.lockSpin(false, GB.ui.spinLabel());
    GB.ui.log('Bereit. Verbinde einen Twitch-Kanal oder nutze Demo-Daten.', 'ok');
    if (location.protocol === 'file:') {
      GB.ui.log('Hinweis: Datei-Modus – alles läuft lokal, Avatare brauchen Internet.', 'muted');
    }
  }

  // ---------------- Ansicht (Rad / Roulette) ----------------

  function setView(view, silent) {
    GB.store.state.view = view;
    if (!silent) GB.store.save();
    const isWheel = view === 'wheel';
    U.$('#tabWheel').classList.toggle('active', isWheel);
    U.$('#tabRoulette').classList.toggle('active', !isWheel);
    U.$('#tabWheel').setAttribute('aria-selected', isWheel ? 'true' : 'false');
    U.$('#tabRoulette').setAttribute('aria-selected', !isWheel ? 'true' : 'false');
    U.$('#wheelWrap').classList.toggle('hidden', !isWheel);
    U.$('#rlWrap').classList.toggle('hidden', isWheel);
    const emptyW = U.$('#wheelEmpty');
    const emptyC = U.$('#rlEmpty');
    const has = GB.pool.count() >= 2;
    if (emptyW) emptyW.classList.toggle('hidden', has);
    if (emptyC) emptyC.classList.toggle('hidden', has);
    // Größen nach Sichtbarkeitswechsel korrigieren
    window.requestAnimationFrame(function () {
      GB.wheel.resize();
      if (!isWheel) GB.roulette.backToIdle();
    });
    GB.ui.lockSpin(spinning || GB.winner.isPending(), GB.ui.spinLabel());
  }

  /** Bühnen mit aktuellen Teilnehmern versorgen (nach Pool-Änderung) */
  function refreshStages() {
    const list = GB.pool.ordered();
    GB.wheel.setData(list);
    GB.roulette.setData(list);
    const has = list.length >= 2;
    const emptyW = U.$('#wheelEmpty');
    const emptyC = U.$('#rlEmpty');
    if (emptyW) emptyW.classList.toggle('hidden', has);
    if (emptyC) emptyC.classList.toggle('hidden', has);
  }

  // ---------------- Twitch ----------------

  function onConnStatus(ev) {
    GB.ui.setConn(ev.status, ev.message, ev.channel);
    if (ev.status === 'open') {
      GB.ui.log('Verbunden mit #' + ev.channel + ' – Chat wird gelesen.', 'ok');
      GB.ui.stageStatus('Live mit #' + ev.channel + ' – warte auf Teilnehmer …', '');
    } else if (ev.status === 'closed') {
      GB.ui.log('Verbindung getrennt.', 'muted');
      GB.ui.stageStatus('Getrennt. Verbinde einen Kanal oder nutze Demo-Daten.', '');
    } else if (ev.status === 'error') {
      GB.ui.toast(ev.message || 'Verbindungsfehler', 'error');
      GB.ui.log('Fehler: ' + (ev.message || 'Verbindung fehlgeschlagen'), 'bad');
    }
  }

  function toggleConnect() {
    const st = GB.store.state;
    if (GB.twitch.isConnected() || U.$('#connectBtn').textContent === 'Trennen') {
      GB.twitch.disconnect();
      GB.ui.setConn('closed', 'Getrennt.');
      return;
    }
    const input = U.$('#channelInput');
    const channel = GB.twitch.cleanChannel(input.value);
    if (!channel) {
      GB.ui.toast('Bitte zuerst einen Kanalnamen eingeben.', 'error');
      input.focus();
      return;
    }
    input.value = channel;
    st.channel = channel;
    GB.store.save();
    GB.twitch.connect(channel);
  }

  /** Eingehende Chat-Nachricht verarbeiten */
  function onChatMessage(m) {
    const st = GB.store.state;
    st.stats.messages++;
    GB.store.save();

    // 1) Gewinner-Claim prüfen (hat Vorrang vor allem)
    if (GB.winner.isPending()) {
      const w = GB.winner.current();
      if (w && m.login === w.login) {
        GB.ui.chatMessage(m, 'claim');
        GB.winner.claim('chat');
        GB.ui.updateStats();
        return;
      }
    }

    // 2) Teilnahme versuchen
    const res = GB.pool.tryAdd(m);
    if (res.added) {
      GB.ui.chatMessage(m, 'joined');
      GB.ui.updateStats();
    } else if (res.reason === 'duplicate') {
      GB.ui.chatMessage(m, 'duplicate');
    } else if (res.reason === 'banned') {
      GB.ui.chatMessage(m, 'banned');
    } else if (res.reason === 'no-keyword' || res.reason === 'command' || res.reason === 'closed') {
      GB.ui.chatMessage(m, null);
    } else {
      GB.ui.updateStats();
    }
    // Zähler auch bei reinen Chat-Nachrichten aktualisieren (gedrosselt)
    bumpMsgCounter();
  }

  const bumpMsgCounter = U.debounce(function () {
    GB.ui.updateStats();
  }, 800);

  // ---------------- Spin-Ablauf ----------------

  function doSpin(isReroll) {
    if (spinning) return;
    if (GB.winner.isPending()) {
      GB.ui.toast('Bitte zuerst den laufenden Claim-Timer abwarten (oder Reroll).', 'error');
      return;
    }
    const list = GB.pool.ordered();
    if (list.length < 2) {
      GB.ui.toast('Mindestens 2 Teilnehmer nötig (aktuell: ' + list.length + ').', 'error');
      return;
    }
    GB.audio.ensure();
    GB.winner.reset();
    GB.ui.claimBanner(false);
    spinning = true;
    GB.ui.lockSpin(true, 'Läuft …');
    GB.ui.stageStatus(isReroll ? 'Reroll läuft – viel Glück!' : 'Auslosung läuft – viel Glück!', 'spin');

    const st = GB.store.state;
    st.stats.spins++;
    GB.store.save();
    GB.ui.updateStats();

    const winnerIdx = U.randInt(0, list.length);
    const winner = list[winnerIdx];
    const durMs = U.clamp(st.settings.spinSeconds, 3, 20) * 1000;

    GB.ui.log((isReroll ? 'Reroll' : 'Spin') + ' gestartet (' + list.length + ' Teilnehmer).', '');

    const engine = st.view === 'roulette' ? GB.roulette : GB.wheel;
    engine.spinTo(winnerIdx, durMs).then(function () {
      spinning = false;
      GB.audio.win();
      GB.ui.stageStatus('Gewinner: ' + winner.display + ' – warte auf Bestätigung …', 'win');
      GB.ui.log('Gewinner gezogen: ' + winner.display + ' (@' + winner.login + ').', 'ok');
      GB.winner.start(winner, { isReroll: isReroll, claimSeconds: st.settings.claimSeconds });
      GB.ui.lockSpin(true, GB.ui.spinLabel()); // gesperrt bis Claim/Timeout
    });
  }

  // ---------------- Claim-Events ----------------

  function onClaimTick(remainMs, totalMs, p) {
    if (!p) return;
    const secs = Math.max(0, Math.ceil(remainMs / 1000));
    if (GB.winner.isPending()) {
      GB.ui.claimBanner(true,
        'Warte auf @' + p.login + ' im Chat … noch ' + secs + ' s',
        totalMs > 0 ? (remainMs / totalMs) * 100 : 0);
    }
  }

  function onClaimed(p) {
    const st = GB.store.state;
    GB.ui.claimBanner(true, 'Bestätigt! @' + p.login + ' hat gewonnen.', 100);
    GB.ui.stageStatus('Gewinner bestätigt: ' + p.display, 'win');
    GB.ui.lastWinner(p.display + ' (@' + p.login + ') – bestätigt ' + U.timeHM(Date.now()));
    GB.ui.log(p.display + ' hat den Gewinn bestätigt.', 'ok');
    GB.ui.toast(p.display + ' hat bestätigt!', 'ok');
    if (st.settings.autoRemoveConfirmed) {
      GB.pool.remove(p.login);
      GB.ui.log(p.display + ' wurde aus dem Pool entfernt.', 'muted');
    }
    window.setTimeout(function () { GB.ui.claimBanner(false); }, 6000);
    GB.ui.lockSpin(false, GB.ui.spinLabel());
    if (st.view === 'roulette') GB.roulette.backToIdle();
  }

  function onClaimTimeout(p, manual) {
    const st = GB.store.state;
    if (!p) return;
    GB.ui.stageStatus('Zeit abgelaufen – ' + (manual ? 'manueller' : 'automatischer') + ' Reroll …', 'timeout');
    GB.ui.log('Keine Antwort von ' + p.display + ' – ' + (manual ? 'manueller' : 'automatischer') + ' Reroll.', 'bad');
    if (st.settings.autoRemoveTimeout) {
      GB.pool.remove(p.login);
      GB.ui.log(p.display + ' wurde aus dem Pool entfernt.', 'muted');
    }
    GB.winner.reset();
    GB.ui.claimBanner(false);
    // Kurz warten, dann automatisch neu drehen
    window.setTimeout(function () {
      if (GB.pool.count() < 2) {
        GB.ui.toast('Nicht genug Teilnehmer für einen Reroll.', 'error');
        GB.ui.stageStatus('Zu wenige Teilnehmer für einen Reroll.', '');
        GB.ui.lockSpin(false, GB.ui.spinLabel());
        return;
      }
      doSpin(true);
    }, manual ? 300 : 800);
  }

  function onWinnerClose() {
    // Modal geschlossen – Banner/Timer laufen ggf. weiter; "Anzeigen"-Pfad:
    if (GB.winner.isPending()) {
      GB.ui.toast('Timer läuft weiter – Klick aufs Banner zeigt den Gewinner.', '');
    }
  }

  // ---------------- Demo-Daten ----------------

  function addDemo(n) {
    const candidates = U.shuffle(DEMO_NAMES);
    let added = 0;
    for (let i = 0; i < candidates.length && added < n; i++) {
      const name = candidates[i];
      if (GB.pool.has(name.toLowerCase())) continue;
      const p = GB.pool.add({
        login: name.toLowerCase().replace(/[^a-z0-9_]/g, ''),
        display: name,
        color: ''
      });
      if (p) added++;
    }
    if (added > 0) {
      GB.ui.toast(added + ' Demo-Teilnehmer hinzugefügt.', 'ok');
      GB.ui.log(added + ' Demo-Teilnehmer hinzugefügt.', 'muted');
    } else {
      GB.ui.toast('Keine neuen Demo-Namen verfügbar (alle schon dabei).', 'error');
    }
  }

  // ---------------- Bedienelemente ----------------

  function bindControls() {
    const st = GB.store.state;
    const s = st.settings;

    // Kopfzeile
    U.$('#channelInput').value = st.channel || '';
    U.$('#connectBtn').addEventListener('click', toggleConnect);
    U.$('#channelInput').addEventListener('keydown', function (ev) {
      if (ev.key === 'Enter') toggleConnect();
    });
    U.$('#themeBtn').addEventListener('click', GB.ui.toggleTheme);
    U.$('#soundBtn').addEventListener('click', toggleSound);

    // Tabs
    U.$('#tabWheel').addEventListener('click', function () { setView('wheel'); });
    U.$('#tabRoulette').addEventListener('click', function () { setView('roulette'); });

    // Spin
    U.$('#spinBtn').addEventListener('click', function () { doSpin(false); });
    U.$('#rerollBtn').addEventListener('click', function () {
      if (GB.winner.isActive()) GB.winner.rerollNow();
      else doSpin(true);
    });

    // Claim-Banner -> Modal erneut öffnen
    U.$('#claimBanner').addEventListener('click', function () { GB.winner.reopen(); });

    // Teilnahme
    U.$('#collectionToggle').addEventListener('change', function (ev) {
      st.collectionOpen = ev.target.checked;
      GB.store.save();
      GB.ui.log(st.collectionOpen ? 'Teilnahme geöffnet.' : 'Teilnahme geschlossen.', 'muted');
    });
    Array.prototype.forEach.call(document.querySelectorAll('input[name="mode"]'), function (r) {
      r.addEventListener('change', function () {
        s.mode = r.value;
        GB.store.save();
        GB.ui.updateStats();
        U.$('#keywordsWrap').classList.toggle('hidden', s.mode !== 'keywords');
        U.$('#ignoreCmdWrap').classList.toggle('hidden', s.mode !== 'all');
      });
    });
    U.$('#keywordsInput').addEventListener('change', function (ev) {
      s.keywords = String(ev.target.value).split(/[\n,;]+/)
        .map(function (x) { return x.trim(); }).filter(Boolean);
      if (!s.keywords.length) s.keywords = ['!giveaway'];
      ev.target.value = s.keywords.join(', ');
      GB.store.save();
      GB.ui.updateStats();
    });
    U.$('#ignoreCommands').addEventListener('change', function (ev) {
      s.ignoreCommands = ev.target.checked;
      GB.store.save();
    });
    U.$('#prizeInput').addEventListener('change', function (ev) {
      s.prize = ev.target.value;
      GB.store.save();
    });
    U.$('#demoBtn').addEventListener('click', function () { addDemo(10); });
    U.$('#manualAddBtn').addEventListener('click', manualAdd);
    U.$('#manualInput').addEventListener('keydown', function (ev) {
      if (ev.key === 'Enter') manualAdd();
    });

    // Einstellungen
    U.$('#spinRange').addEventListener('input', function (ev) {
      s.spinSeconds = Number(ev.target.value);
      U.$('#spinVal').textContent = s.spinSeconds + ' s';
      GB.store.save();
    });
    U.$('#claimRange').addEventListener('input', function (ev) {
      s.claimSeconds = Number(ev.target.value);
      U.$('#claimVal').textContent = s.claimSeconds + ' s';
      GB.store.save();
    });
    U.$('#soundToggle').addEventListener('change', function (ev) {
      s.sound = ev.target.checked;
      GB.audio.setEnabled(s.sound);
      GB.ui.renderSoundBtn();
      GB.store.save();
    });
    U.$('#volumeRange').addEventListener('input', function (ev) {
      s.volume = Number(ev.target.value);
      U.$('#volumeVal').textContent = Math.round(s.volume * 100) + ' %';
      GB.audio.setVolume(s.volume);
      GB.store.save();
    });
    U.$('#soundTestBtn').addEventListener('click', function () { GB.audio.test(); });
    U.$('#banlistInput').addEventListener('change', function (ev) {
      s.banlist = ev.target.value;
      GB.store.save();
      GB.ui.toast('Banliste gespeichert.', 'ok');
    });
    U.$('#defaultBots').addEventListener('change', function (ev) {
      s.defaultBots = ev.target.checked;
      GB.store.save();
    });
    U.$('#autoRemoveConfirmed').addEventListener('change', function (ev) {
      s.autoRemoveConfirmed = ev.target.checked;
      GB.store.save();
    });
    U.$('#autoRemoveTimeout').addEventListener('change', function (ev) {
      s.autoRemoveTimeout = ev.target.checked;
      GB.store.save();
    });
    U.$('#avatarsToggle').addEventListener('change', function (ev) {
      s.avatars = ev.target.checked;
      GB.store.save();
      GB.pool.render();
    });

    // Daten
    U.$('#exportCsvBtn').addEventListener('click', function () { GB.pool.exportCSV(); });
    U.$('#exportJsonBtn').addEventListener('click', function () { GB.pool.exportJSON(); });
    U.$('#importFile').addEventListener('change', function (ev) {
      const f = ev.target.files && ev.target.files[0];
      if (!f) return;
      U.readFile(f).then(function (txt) {
        const res = GB.pool.importJSON(txt);
        if (res.ok) {
          GB.ui.toast(res.added + ' Teilnehmer importiert.', 'ok');
          GB.ui.log(res.added + ' Teilnehmer importiert.', 'muted');
        } else {
          GB.ui.toast(res.error, 'error');
        }
      });
      ev.target.value = '';
    });
    U.$('#resetBtn').addEventListener('click', function () {
      if (!window.confirm('Wirklich alle Teilnehmer und Zähler zurücksetzen?')) return;
      GB.winner.reset();
      GB.pool.clear();
      GB.store.resetData();
      GB.ui.lastWinner('');
      GB.ui.claimBanner(false);
      GB.ui.updateStats();
      GB.ui.lockSpin(false, GB.ui.spinLabel());
      GB.ui.stageStatus('Zurückgesetzt. Bereit für das nächste Giveaway.', '');
      GB.ui.toast('Zurückgesetzt.', 'ok');
    });

    // Teilnehmer-Suche & Sortierung
    U.$('#rosterSearch').addEventListener('input', U.debounce(function (ev) {
      GB.pool.setSearch(ev.target.value);
    }, 120));
    U.$('#rosterSort').addEventListener('change', function (ev) {
      GB.pool.setSort(ev.target.value);
    });

    // Feed-Tabs
    U.$('#feedTabChat').addEventListener('click', function () { setFeedTab('chat'); });
    U.$('#feedTabLog').addEventListener('click', function () { setFeedTab('log'); });

    // Gewinner-Modal
    U.$('#btnCloseWinner').addEventListener('click', function () { GB.winner.close(); });
    U.$('#modalClose').addEventListener('click', function () { GB.winner.close(); });
    U.$('#btnRerollNow').addEventListener('click', function () { GB.winner.rerollNow(); });
    U.$('#btnSimClaim').addEventListener('click', function () { GB.winner.claim('test'); });
    U.$('#winnerModal').addEventListener('click', function (ev) {
      if (ev.target.id === 'winnerModal') GB.winner.close();
    });
  }

  function applySettingsToUI() {
    const st = GB.store.state;
    const s = st.settings;
    U.$('#collectionToggle').checked = !!st.collectionOpen;
    Array.prototype.forEach.call(document.querySelectorAll('input[name="mode"]'), function (r) {
      r.checked = r.value === s.mode;
    });
    U.$('#keywordsWrap').classList.toggle('hidden', s.mode !== 'keywords');
    U.$('#ignoreCmdWrap').classList.toggle('hidden', s.mode !== 'all');
    U.$('#keywordsInput').value = (s.keywords || []).join(', ');
    U.$('#ignoreCommands').checked = !!s.ignoreCommands;
    U.$('#prizeInput').value = s.prize || '';
    U.$('#spinRange').value = s.spinSeconds;
    U.$('#spinVal').textContent = s.spinSeconds + ' s';
    U.$('#claimRange').value = s.claimSeconds;
    U.$('#claimVal').textContent = s.claimSeconds + ' s';
    U.$('#soundToggle').checked = !!s.sound;
    U.$('#volumeRange').value = s.volume;
    U.$('#volumeVal').textContent = Math.round(s.volume * 100) + ' %';
    U.$('#banlistInput').value = s.banlist || '';
    U.$('#defaultBots').checked = !!s.defaultBots;
    U.$('#autoRemoveConfirmed').checked = !!s.autoRemoveConfirmed;
    U.$('#autoRemoveTimeout').checked = !!s.autoRemoveTimeout;
    U.$('#avatarsToggle').checked = !!s.avatars;
  }

  function manualAdd() {
    const input = U.$('#manualInput');
    const raw = String(input.value || '').trim().replace(/^@/, '');
    const login = raw.toLowerCase().replace(/[^a-z0-9_]/g, '');
    if (!login) {
      GB.ui.toast('Bitte einen Twitch-Namen eingeben.', 'error');
      return;
    }
    if (GB.store.bannedSet()[login]) {
      GB.ui.toast(login + ' steht auf der Banliste.', 'error');
      return;
    }
    const p = GB.pool.add({ login: login, display: raw });
    if (p) {
      GB.ui.toast(p.display + ' hinzugefügt.', 'ok');
      GB.ui.log('Manuell hinzugefügt: ' + p.display, 'muted');
      input.value = '';
    } else {
      GB.ui.toast(login + ' ist bereits dabei.', 'error');
    }
  }

  function toggleSound() {
    const s = GB.store.state.settings;
    s.sound = !s.sound;
    GB.audio.setEnabled(s.sound);
    if (s.sound) GB.audio.ensure();
    U.$('#soundToggle').checked = s.sound;
    GB.ui.renderSoundBtn();
    GB.store.save();
  }

  function setFeedTab(which) {
    const isChat = which === 'chat';
    U.$('#feedTabChat').classList.toggle('active', isChat);
    U.$('#feedTabLog').classList.toggle('active', !isChat);
    U.$('#chatFeed').classList.toggle('hidden', !isChat);
    U.$('#chatEmpty').classList.toggle('hidden', !isChat || U.$('#chatFeed').children.length > 0);
    U.$('#activityLog').classList.toggle('hidden', isChat);
  }

  function onKey(ev) {
    // Nicht in Eingabefeldern auslösen
    const tag = (ev.target && ev.target.tagName) || '';
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || ev.target.isContentEditable) {
      if (ev.key === 'Escape') ev.target.blur();
      return;
    }
    if (ev.code === 'Space') {
      ev.preventDefault();
      GB.audio.ensure();
      doSpin(false);
    } else if (ev.key === 'm' || ev.key === 'M') {
      toggleSound();
    } else if (ev.key === 't' || ev.key === 'T') {
      GB.ui.toggleTheme();
    } else if (ev.key === 'Escape') {
      if (GB.winner.isActive()) GB.winner.close();
    }
  }

  GB.app = {
    init: init,
    setView: setView,
    refreshStages: refreshStages,
    doSpin: doSpin
  };

  document.addEventListener('DOMContentLoaded', init);
})();

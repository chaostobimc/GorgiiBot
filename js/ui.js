/* ============================================================================
 * GorgiiBot – ui.js
 * Theme, Toasts, Aktivitäts-Log, Chat-Feed, Statistiken, Claim-Banner,
 * Einstellungs-Bindings und kleine UI-Helfer.
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});
  const U = GB.util;

  // ---- Theme ----

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    const btn = U.$('#themeBtn');
    if (btn) {
      btn.setAttribute('title', theme === 'dark' ? 'Zum hellen Modus' : 'Zum dunklen Modus');
      const sun = btn.querySelector('.icon-sun');
      const moon = btn.querySelector('.icon-moon');
      if (sun) sun.classList.toggle('hidden', theme !== 'dark');
      if (moon) moon.classList.toggle('hidden', theme !== 'light');
    }
    try {
      const meta = document.querySelector('meta[name="theme-color"]');
      if (meta) meta.setAttribute('content', theme === 'dark' ? '#0d1117' : '#ffffff');
    } catch (e) {}
    if (GB.wheel && GB.wheel.refreshTheme) GB.wheel.refreshTheme();
  }

  function toggleTheme() {
    const st = GB.store.state;
    st.theme = st.theme === 'dark' ? 'light' : 'dark';
    GB.store.saveNow();
    applyTheme(st.theme);
  }

  /** Akzentfarbe setzen (data-accent + Swatch-Markierung + Rad-Farben) */
  function applyAccent(accent) {
    const valid = ['green', 'blue', 'purple', 'orange'];
    const a = valid.indexOf(accent) >= 0 ? accent : 'green';
    document.documentElement.setAttribute('data-accent', a);
    U.$$('.swatch').forEach(function (b) {
      b.classList.toggle('active', b.getAttribute('data-accent') === a);
    });
    if (GB.wheel && GB.wheel.refreshTheme) GB.wheel.refreshTheme();
  }

  // ---- Toasts ----

  function toast(msg, kind) {
    const wrap = U.$('#toasts');
    if (!wrap) return;
    const el = document.createElement('div');
    el.className = 'toast' + (kind ? ' toast-' + kind : '');
    el.textContent = msg;
    wrap.appendChild(el);
    // Limit: max. 5 gleichzeitig
    while (wrap.children.length > 5) wrap.removeChild(wrap.firstChild);
    window.setTimeout(function () { el.classList.add('show'); }, 10);
    window.setTimeout(function () {
      el.classList.remove('show');
      window.setTimeout(function () { el.remove(); }, 300);
    }, 3500);
  }

  // ---- Aktivitäts-Log ----

  function log(msg, kind) {
    const list = U.$('#activityLog');
    if (!list) return;
    const el = document.createElement('div');
    el.className = 'log-item' + (kind ? ' log-' + kind : '');
    el.innerHTML = '<span class="log-time">' + U.esc(U.timeHM(Date.now())) + '</span>' +
      '<span>' + U.esc(msg) + '</span>';
    list.prepend(el);
    while (list.children.length > 120) list.removeChild(list.lastChild);
  }

  // ---- Chat-Feed ----

  function chatMessage(m, flag) {
    const feed = U.$('#chatFeed');
    if (!feed) return;
    const empty = U.$('#chatEmpty');
    if (empty) empty.classList.add('hidden');
    const el = document.createElement('div');
    el.className = 'chat-item' + (flag === 'joined' ? ' chat-joined' : '') +
      (flag === 'claim' ? ' chat-claim' : '');
    const color = m.color && /^#[0-9a-f]{6}$/i.test(m.color)
      ? m.color : U.colorFor(m.login);
    let badge = '';
    if (flag === 'joined') badge = '<span class="pill pill-ok">dabei</span>';
    if (flag === 'claim') badge = '<span class="pill pill-ok">Claim!</span>';
    if (flag === 'duplicate') badge = '<span class="pill">bereits dabei</span>';
    if (flag === 'banned') badge = '<span class="pill pill-bad">blockiert</span>';
    el.innerHTML =
      '<span class="chat-time">' + U.esc(U.timeHM(m.ts)) + '</span>' +
      '<span class="chat-user" style="color:' + U.esc(color) + '">' +
      U.esc(m.display || m.login) + '</span>' +
      '<span class="chat-text">' + U.esc(m.text) + '</span>' + badge;
    feed.prepend(el);
    while (feed.children.length > 60) feed.removeChild(feed.lastChild);
  }

  // ---- Statistiken ----

  function updateStats() {
    const st = GB.store.state;
    if (!st) return;
    const set = function (id, txt) {
      const el = document.getElementById(id);
      if (el) el.textContent = txt;
    };
    set('statParticipants', String(GB.pool ? GB.pool.count() : 0));
    set('statMessages', String(st.stats.messages || 0));
    set('statSpins', String(st.stats.spins || 0));
    set('statMode', st.settings.mode === 'all'
      ? 'Alle Nachrichten'
      : 'Keywords: ' + ((st.settings.keywords || []).join(', ') || '–'));
  }

  // ---- Verbindungsstatus ----

  function setConn(status, message, channel) {
    const dot = U.$('#statusDot');
    const label = U.$('#connLabel');
    const btn = U.$('#connectBtn');
    if (dot) dot.setAttribute('data-state', status);
    if (label) {
      label.textContent =
        status === 'open' ? '#' + (channel || '') :
        status === 'connecting' ? 'Verbinde …' :
        status === 'error' ? 'Fehler' : 'Getrennt';
      label.title = message || '';
    }
    if (btn) {
      const connected = status === 'open' || status === 'connecting';
      btn.textContent = connected ? 'Trennen' : 'Verbinden';
      btn.classList.toggle('btn-danger', connected && status === 'open');
      btn.classList.toggle('btn-primary', !connected);
    }
    const statStatus = U.$('#statStatus');
    if (statStatus) {
      statStatus.textContent =
        status === 'open' ? 'Live' :
        status === 'connecting' ? 'Verbinde …' :
        status === 'error' ? 'Fehler' : 'Offline';
      statStatus.setAttribute('data-state', status);
    }
  }

  // ---- Bühnen-Statuszeile ----

  function stageStatus(text, kind) {
    const el = U.$('#stageStatus');
    if (!el) return;
    el.textContent = text;
    el.setAttribute('data-kind', kind || '');
  }

  // ---- Claim-Banner (unter der Bühne, läuft parallel zum Modal) ----

  function claimBanner(show, text, pct) {
    const b = U.$('#claimBanner');
    if (!b) return;
    b.classList.toggle('hidden', !show);
    if (!show) return;
    const t = U.$('#claimText');
    const bar = U.$('#claimBar');
    const secs = U.$('#claimSecs');
    if (t && typeof text === 'string') t.textContent = text;
    if (bar && typeof pct === 'number') {
      bar.style.width = Math.max(0, Math.min(100, pct)).toFixed(1) + '%';
      bar.classList.toggle('danger', pct < 18);
    }
    if (secs && typeof text === 'string') {
      const m = text.match(/(\d+)\s?s/);
      secs.textContent = m ? m[1] + 's' : '';
    }
  }

  // ---- Letzter Gewinner ----

  function lastWinner(text) {
    const el = U.$('#lastWinnerText');
    if (el) el.textContent = text;
    const row = U.$('#lastWinner');
    if (row) row.classList.toggle('hidden', !text);
  }

  // ---- Spin-Buttons sperren/entsperren ----

  function lockSpin(locked, label) {
    const spin = U.$('#spinBtn');
    const rr = U.$('#rerollBtn');
    if (spin) {
      spin.disabled = !!locked;
      if (label) {
        const span = spin.querySelector('span');
        if (span) span.textContent = label;
      }
    }
    if (rr) rr.disabled = !GB.winner || !GB.winner.isActive();
  }

  function spinLabel() {
    const view = GB.store.state.view;
    return view === 'roulette' ? 'Roulette starten' : 'Rad drehen';
  }

  // ---- Sound-Button ----

  function renderSoundBtn() {
    const btn = U.$('#soundBtn');
    if (!btn) return;
    const on = GB.store.state.settings.sound;
    btn.setAttribute('title', on ? 'Sound ausschalten (M)' : 'Sound einschalten (M)');
    const sOn = btn.querySelector('.icon-sound-on');
    const sOff = btn.querySelector('.icon-sound-off');
    if (sOn) sOn.classList.toggle('hidden', !on);
    if (sOff) sOff.classList.toggle('hidden', on);
  }

  GB.ui = {
    applyTheme: applyTheme,
    toggleTheme: toggleTheme,
    applyAccent: applyAccent,
    toast: toast,
    log: log,
    chatMessage: chatMessage,
    updateStats: updateStats,
    setConn: setConn,
    stageStatus: stageStatus,
    claimBanner: claimBanner,
    lastWinner: lastWinner,
    lockSpin: lockSpin,
    spinLabel: spinLabel,
    renderSoundBtn: renderSoundBtn
  };
})();

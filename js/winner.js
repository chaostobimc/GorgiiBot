/* ============================================================================
 * GorgiiBot – winner.js
 * Gewinner-Dialog + Claim-Timer: Nach dem Spin läuft ein konfigurierbarer
 * Countdown. Schreibt der Gewinner im Chat (oder per Test-Simulation),
 * wird der Gewinn bestätigt. Sonst: Timeout -> automatischer Reroll.
 * Die App registriert Callbacks (onClaimed / onTimeout / onTick / onClose).
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});

  const S = {
    active: false,      // Gewinner-Phase läuft (pending oder claimed)
    pending: false,     // wartet auf Claim
    claimed: false,
    participant: null,
    isReroll: false,
    totalMs: 60000,
    endsAt: 0,
    timerId: 0,
    lastBeep: -1,
    rerollTimer: 0,
    cbs: { onClaimed: null, onTimeout: null, onTick: null, onClose: null }
  };

  function onEvent(name, cb) { S.cbs[name] = cb; }

  function $(id) { return document.getElementById(id); }

  function fmtSecs(ms) {
    return Math.max(0, Math.ceil(ms / 1000));
  }

  /** Gewinner-Phase starten (Modal öffnen + Timer laufen lassen) */
  function start(participant, opts) {
    opts = opts || {};
    stopTimer();
    window.clearTimeout(S.rerollTimer);
    S.active = true;
    S.pending = true;
    S.claimed = false;
    S.participant = participant;
    S.isReroll = !!opts.isReroll;
    S.totalMs = Math.max(5000, (opts.claimSeconds || 60) * 1000);
    S.endsAt = performance.now() + S.totalMs;
    S.lastBeep = -1;
    renderModal();
    openModal();
    S.timerId = window.setInterval(tick, 100);
    tick();
  }

  function tick() {
    if (!S.pending) return;
    const remain = S.endsAt - performance.now();
    const remainMs = Math.max(0, remain);
    const secs = fmtSecs(remainMs);

    // Countdown-Pieps in den letzten 5 Sekunden
    if (secs <= 5 && secs >= 1 && secs !== S.lastBeep) {
      S.lastBeep = secs;
      if (GB.audio) GB.audio.beep(secs <= 2);
    }

    updateTimerUI(remainMs);
    if (S.cbs.onTick) {
      try { S.cbs.onTick(remainMs, S.totalMs, S.participant); } catch (e) {}
    }

    if (remain <= 0) {
      onTimeout();
    }
  }

  function stopTimer() {
    window.clearInterval(S.timerId);
    S.timerId = 0;
  }

  /** Gewinner hat sich gemeldet (Chat oder Test) */
  function claim(source) {
    if (!S.active || !S.pending) return false;
    S.pending = false;
    S.claimed = true;
    stopTimer();
    if (GB.audio) GB.audio.confirm();
    updateStatusUI(
      'Bestätigt! ' + (S.participant ? S.participant.display : 'Gewinner') +
      ' hat sich im Chat gemeldet.',
      'ok'
    );
    updateTimerUI(0);
    if (S.cbs.onTick) {
      try { S.cbs.onTick(0, S.totalMs, S.participant); } catch (e) {}
    }
    if (S.cbs.onClaimed) {
      try { S.cbs.onClaimed(S.participant, source || 'chat'); } catch (e) {}
    }
    return true;
  }

  /** Zeit abgelaufen -> Status zeigen, dann Auto-Reroll einleiten */
  function onTimeout() {
    if (!S.active || !S.pending) return;
    S.pending = false;
    stopTimer();
    if (GB.audio) GB.audio.timeout();
    updateStatusUI('Zeit abgelaufen! ' + (S.participant ? S.participant.display : '') +
      ' hat sich nicht gemeldet – Reroll startet …', 'timeout');
    updateTimerUI(0);
    if (S.cbs.onTimeout) {
      // Kurze Lesepause, damit der Status sichtbar bleibt
      S.rerollTimer = window.setTimeout(function () {
        try { S.cbs.onTimeout(S.participant); } catch (e) {}
      }, 2200);
    }
  }

  /** Manueller Reroll aus dem Dialog */
  function rerollNow() {
    if (!S.active) return;
    window.clearTimeout(S.rerollTimer);
    S.pending = false;
    stopTimer();
    const p = S.participant;
    if (S.cbs.onTimeout) {
      try { S.cbs.onTimeout(p, true); } catch (e) {}
    }
  }

  /** Dialog schließen (Timer läuft im Hintergrund weiter, Banner bleibt) */
  function close() {
    closeModal();
    if (S.cbs.onClose) {
      try { S.cbs.onClose(S.participant, S.claimed); } catch (e) {}
    }
    // Wenn bereits geclaimt/abgelaufen: Phase beenden
    if (!S.pending) {
      S.active = false;
      S.participant = null;
    }
  }

  /** Phase komplett beenden (z. B. vor neuem Spin) */
  function reset() {
    window.clearTimeout(S.rerollTimer);
    stopTimer();
    S.active = false;
    S.pending = false;
    S.claimed = false;
    S.participant = null;
    closeModal();
  }

  function isActive() { return S.active; }
  function isPending() { return S.pending; }
  function current() { return S.participant; }

  // ---- Modal-Rendering ----

  function renderModal() {
    const U = GB.util;
    const p = S.participant;
    if (!p) return;
    const avWrap = $('wAvatar');
    if (avWrap) avWrap.innerHTML = GB.pool.avatarHTML(p, 64);
    const set = function (id, txt) { const el = $(id); if (el) el.textContent = txt; };
    set('wName', p.display || p.login);
    set('wLogin', '@' + p.login);
    const prize = (GB.store.state.settings.prize || '').trim();
    const prizeRow = $('wPrizeRow');
    if (prizeRow) {
      prizeRow.classList.toggle('hidden', !prize);
      set('wPrize', prize);
    }
    const tag = $('wRerollTag');
    if (tag) tag.classList.toggle('hidden', !S.isReroll);
    updateStatusUI(
      S.isReroll
        ? 'Reroll-Gewinner – warte auf Bestätigung im Chat …'
        : 'Warte auf Bestätigung im Chat …',
      'pending'
    );
    updateTimerUI(S.totalMs);
    const sim = $('btnSimClaim');
    if (sim) sim.classList.toggle('hidden', false);
    renderHistory();
  }

  /** Chat-Verlauf des Gewinners rechts neben dem Dialog rendern */
  function renderHistory() {
    const U = GB.util;
    const p = S.participant;
    const nameEl = $('wHistoryName');
    if (nameEl) nameEl.textContent = p ? '@' + p.login : '–';
    const list = $('wHistory');
    if (!list) return;
    const items = (p && GB.app && GB.app.userHistory) ? GB.app.userHistory(p.login) : [];
    if (!items.length) {
      list.innerHTML = '<div class="history-empty">Noch keine Nachrichten erfasst.<br>' +
        'Meldet sich der Gewinner im Chat, erscheinen sie hier.</div>';
      return;
    }
    list.innerHTML = items.map(function (m) {
      return '<div class="history-item">' +
        '<span class="history-time">' + U.esc(U.timeHM(m.ts)) + '</span>' +
        '<span class="history-text">' + U.esc(m.text) + '</span></div>';
    }).join('');
    list.scrollTop = list.scrollHeight;
  }

  /** Neue Nachricht des Gewinners live ans Verlaufs-Panel anhängen */
  function appendHistory(m) {
    if (!S.active || !S.participant || !m || m.login !== S.participant.login) return;
    const U = GB.util;
    const list = $('wHistory');
    if (!list) return;
    const empty = list.querySelector('.history-empty');
    if (empty) empty.remove();
    const div = document.createElement('div');
    div.className = 'history-item history-new';
    const time = document.createElement('span');
    time.className = 'history-time';
    time.textContent = U.timeHM(m.ts);
    const text = document.createElement('span');
    text.className = 'history-text';
    text.textContent = m.text;
    div.appendChild(time);
    div.appendChild(text);
    list.appendChild(div);
    list.scrollTop = list.scrollHeight;
  }

  function updateTimerUI(remainMs) {
    const bar = $('wTimerBar');
    const txt = $('wTimerText');
    const pct = S.totalMs > 0 ? (remainMs / S.totalMs) * 100 : 0;
    if (bar) {
      bar.style.width = pct.toFixed(1) + '%';
      bar.classList.toggle('danger', remainMs < 10000 && S.pending);
    }
    if (txt) {
      txt.textContent = S.claimed
        ? 'Bestätigt'
        : (S.pending ? fmtSecs(remainMs) + ' s' : '0 s');
    }
  }

  function updateStatusUI(text, kind) {
    const el = $('wStatus');
    if (!el) return;
    el.textContent = text;
    el.setAttribute('data-kind', kind || 'pending');
    const sim = $('btnSimClaim');
    if (sim) sim.classList.toggle('hidden', kind !== 'pending');
    const rr = $('btnRerollNow');
    if (rr) rr.disabled = kind !== 'pending' && kind !== 'timeout';
  }

  function openModal() {
    const m = $('winnerModal');
    if (m) {
      m.classList.remove('hidden');
      // Fokus für Tastaturbedienung
      const btn = $('btnCloseWinner');
      if (btn) window.setTimeout(function () { btn.focus(); }, 50);
    }
  }

  function closeModal() {
    const m = $('winnerModal');
    if (m) m.classList.add('hidden');
  }

  /** Dialog erneut öffnen (wenn Timer im Hintergrund weiterläuft) */
  function reopen() {
    if (S.active && S.participant) {
      renderModal();
      if (!S.pending && S.claimed) {
        updateStatusUI('Bestätigt! ' + S.participant.display +
          ' hat sich im Chat gemeldet.', 'ok');
      }
      openModal();
    }
  }

  GB.winner = {
    on: onEvent,
    start: start,
    claim: claim,
    rerollNow: rerollNow,
    close: close,
    reset: reset,
    reopen: reopen,
    appendHistory: appendHistory,
    isActive: isActive,
    isPending: isPending,
    current: current
  };
})();

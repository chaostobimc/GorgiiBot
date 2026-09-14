/* ============================================================================
 * GorgiiBot – roulette.js
 * Roulette: horizontale Kärtchen-Leiste mit Mittelmarker.
 * Idle: langsamer Endlos-Loop. Spin: Ease-Out-Animation auf eine
 * vorbestimmte Gewinner-Position, Tick-Sound pro Kärtchen.
 * Kärtchen zeigen nur Avatar + Name.
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});
  const REDUCED = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const S = {
    viewport: null,
    track: null,
    data: [],          // Teilnehmer (stabile Reihenfolge)
    mode: 'idle',      // 'idle' | 'spinning'
    offset: 0,         // aktuelle Scroll-Position (px)
    setW: 0,           // Breite eines Idle-Sets (px) für den Wrap
    cardW: 140,
    gap: 10,
    idleSpeed: 45,     // px/s
    raf: 0,
    lastT: 0,
    anim: null,        // {from,to,dur,t0,lastCard,resolve}
    winnerPos: -1,
    idleOn: true
  };

  function easeOutQuint(t) {
    return 1 - Math.pow(1 - t, 5);
  }

  function step() { return S.cardW + S.gap; }

  function init(viewportId, trackId) {
    S.viewport = document.getElementById(viewportId);
    S.track = document.getElementById(trackId);
    if (!S.viewport || !S.track) return;
    if (REDUCED) S.idleSpeed = 0;
    S.lastT = performance.now();
    const loop = function (t) {
      frame(t);
      S.raf = requestAnimationFrame(loop);
    };
    S.raf = requestAnimationFrame(loop);
    window.addEventListener('resize', function () {
      if (S.mode === 'idle') buildIdle();
    });
  }

  function setData(participants) {
    S.data = (participants || []).slice();
    if (S.mode === 'idle') buildIdle();
  }

  /** Kärtchen-HTML für einen Teilnehmer (nur Avatar + Name) */
  function cardHTML(p, extraClass) {
    const U = GB.util;
    const bg = p.color && /^#[0-9a-f]{6}$/i.test(p.color) ? p.color : U.colorFor(p.login);
    const initial = U.esc(U.initials(p.display || p.login));
    let av;
    if (p.avatar) {
      av = '<img src="' + U.esc(p.avatar) + '" alt="" loading="lazy" ' +
        'onerror="this.outerHTML=\'<span>' + initial + '</span>\'">';
    } else {
      av = '<span>' + initial + '</span>';
    }
    return '<div class="rl-card ' + (extraClass || '') + '">' +
      '<span class="rl-avatar" style="background:' + U.esc(bg) + '">' + av + '</span>' +
      '<span class="rl-name">' + U.esc(p.display || p.login) + '</span>' +
      '</div>';
  }

  /** Idle-Leiste: Set 2× rendern für nahtlosen Wrap */
  function buildIdle() {
    if (!S.track || S.mode !== 'idle') return;
    const U = GB.util;
    const data = S.data;
    if (data.length < 2) {
      S.track.innerHTML = '';
      S.track.style.transform = 'translateX(0px)';
      return;
    }
    // Set so oft wiederholen, bis es breiter als der Viewport ist (min. 2×)
    const vw = S.viewport.clientWidth || 600;
    const setCards = [];
    while (setCards.length * step() < vw * 1.5) {
      data.forEach(function (p) { setCards.push(p); });
      if (setCards.length > 400) break;
    }
    let html = '';
    for (let r = 0; r < 2; r++) {
      setCards.forEach(function (p) { html += cardHTML(p, ''); });
    }
    S.track.innerHTML = html;
    S.setW = setCards.length * step();
    S.offset = S.offset % (S.setW || 1);
    applyTransform();
  }

  function applyTransform() {
    if (S.track) S.track.style.transform = 'translate3d(' + (-S.offset) + 'px,0,0)';
  }

  function frame(t) {
    const dt = Math.min(0.1, (t - S.lastT) / 1000 || 0);
    S.lastT = t;

    if (S.mode === 'spinning' && S.anim) {
      const a = S.anim;
      const p = Math.min(1, (t - a.t0) / a.dur);
      S.offset = a.from + (a.to - a.from) * easeOutQuint(p);
      const cardIdx = Math.floor(S.offset / step());
      if (cardIdx !== a.lastCard) {
        a.lastCard = cardIdx;
        if (p < 1 && GB.audio) GB.audio.tick();
      }
      applyTransform();
      if (p >= 1) {
        S.mode = 'idle';
        S.anim = null;
        highlightWinner();
        if (a.resolve) a.resolve(a.winner);
      }
    } else if (S.idleOn && S.mode === 'idle' && S.setW > 0) {
      S.offset += S.idleSpeed * dt;
      if (S.offset >= S.setW) S.offset -= S.setW;
      applyTransform();
    }
  }

  /**
   * Spin: Leiste mit N Kärtchen aufbauen, Gewinner an fester Position.
   * Löst mit dem Gewinner-Index (in S.data) auf.
   */
  function spinTo(winnerIndex, durationMs) {
    return new Promise(function (resolve) {
      const data = S.data;
      const U = GB.util;
      if (data.length < 2) { resolve(-1); return; }
      const winner = data[Math.max(0, Math.min(data.length - 1, winnerIndex))];

      S.mode = 'spinning';
      S.track.innerHTML = '';
      S.track.style.transform = 'translateX(0px)';

      // Spin-Leiste: 90 Kärtchen, Gewinner bei Position ~76
      const N = 90;
      const winnerPos = N - 14;
      S.winnerPos = winnerPos;
      const items = [];
      for (let i = 0; i < N; i++) {
        if (i === winnerPos) {
          items.push(winner);
        } else {
          // Zufällige Füllung (Gewinner nur an seiner Position)
          let cand = data[U.randInt(0, data.length)];
          let guard = 0;
          while (cand.login === winner.login && data.length > 1 && guard++ < 5) {
            cand = data[U.randInt(0, data.length)];
          }
          items.push(cand);
        }
      }
      let html = '';
      // Links etwas Vorlauf einplanen, damit der Start natürlich wirkt
      items.forEach(function (p, i) {
        html += cardHTML(p, i === winnerPos ? 'is-winner' : '');
      });
      S.track.innerHTML = html;

      // Ziel: Gewinner-Kärtchen mittig unter dem Marker (+/- leichte Streuung)
      const vw = S.viewport.clientWidth || 600;
      const jitter = (Math.random() * 2 - 1) * (S.cardW / 2 - 14);
      const target = winnerPos * step() + S.cardW / 2 - vw / 2 + jitter;

      S.offset = 0;
      S.anim = {
        from: 0,
        to: Math.max(0, target),
        dur: Math.max(1500, durationMs),
        t0: performance.now(),
        lastCard: 0,
        winner: data.indexOf(winner),
        resolve: resolve
      };
    });
  }

  function highlightWinner() {
    if (!S.track) return;
    const cards = S.track.children;
    const el = cards[S.winnerPos];
    if (el) {
      el.classList.add('landed');
      // Nach kurzer Zeit Idle-Leiste wieder aufbauen (Highlight bleibt sichtbar,
      // bis der nächste Spin startet oder sich die Daten ändern)
    }
  }

  /** Idle-Loop neu aufbauen (z. B. nach Gewinner-Dialog) */
  function backToIdle() {
    if (S.mode === 'spinning') return;
    S.winnerPos = -1;
    buildIdle();
  }

  function isSpinning() { return S.mode === 'spinning'; }
  function setIdle(on) { S.idleOn = !!on; }

  GB.roulette = {
    init: init,
    setData: setData,
    spinTo: spinTo,
    backToIdle: backToIdle,
    isSpinning: isSpinning,
    setIdle: setIdle
  };
})();

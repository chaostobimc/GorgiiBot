/* ============================================================================
 * GorgiiBot – wheel.js
 * Glücksrad auf Canvas-Basis: ein Segment pro Teilnehmer, dezente
 * GitHub-Farbgebung, Idle-Rotation im Hintergrund und physikalisch
 * wirkendes Abbremsen (Ease-Out-Quint) beim Spin. Der Gewinner-Sektor
 * landet exakt unter dem Zeiger (oben).
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});
  const TAU = Math.PI * 2;
  const POINTER = -Math.PI / 2; // Zeiger zeigt nach oben (12 Uhr)
  const REDUCED = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const S = {
    canvas: null,
    ctx: null,
    size: 0,
    names: [],        // [{login, display}]
    rot: 0,           // absolute Rotation (wächst monoton)
    state: 'idle',    // 'idle' | 'spinning'
    idleOn: true,
    idleSpeed: 0.22,  // rad/s im Idle
    highlight: -1,    // Gewinner-Index nach dem Spin
    spin: null,       // {from, to, dur, t0, lastIdx, resolve}
    raf: 0,
    lastT: 0
  };

  function isLight() {
    return document.documentElement.getAttribute('data-theme') === 'light';
  }

  function easeOutQuint(t) {
    return 1 - Math.pow(1 - t, 5);
  }

  function norm(a) {
    a = a % TAU;
    return a < 0 ? a + TAU : a;
  }

  /** Welches Segment liegt gerade unter dem Zeiger? */
  function segmentAtPointer() {
    const n = S.names.length;
    if (!n) return -1;
    const seg = TAU / n;
    const rel = norm(POINTER - S.rot);
    return Math.min(n - 1, Math.floor(rel / seg));
  }

  function init(canvasId) {
    S.canvas = document.getElementById(canvasId);
    if (!S.canvas) return;
    S.ctx = S.canvas.getContext('2d');
    resize();
    if (window.ResizeObserver && S.canvas.parentElement) {
      const ro = new ResizeObserver(function () { resize(); });
      ro.observe(S.canvas.parentElement);
    } else {
      window.addEventListener('resize', resize);
    }
    if (REDUCED) S.idleSpeed = 0;
    S.lastT = performance.now();
    const loop = function (t) {
      step(t);
      S.raf = requestAnimationFrame(loop);
    };
    S.raf = requestAnimationFrame(loop);
  }

  /** Canvas an Containerbreite anpassen (HiDPI-scharf) */
  function resize() {
    if (!S.canvas) return;
    const parent = S.canvas.parentElement;
    const w = Math.min(parent ? parent.clientWidth : 480, 560);
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    S.size = Math.max(240, Math.floor(w));
    S.canvas.width = Math.floor(S.size * dpr);
    S.canvas.height = Math.floor(S.size * dpr);
    S.canvas.style.width = S.size + 'px';
    S.canvas.style.height = S.size + 'px';
    S.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  function setData(participants) {
    S.names = (participants || []).map(function (p) {
      return { login: p.login, display: p.display || p.login };
    });
    S.highlight = -1;
  }

  function step(t) {
    const dt = Math.min(0.1, (t - S.lastT) / 1000 || 0);
    S.lastT = t;

    if (S.state === 'spinning' && S.spin) {
      const sp = S.spin;
      const p = Math.min(1, (t - sp.t0) / sp.dur);
      S.rot = sp.from + (sp.to - sp.from) * easeOutQuint(p);
      // Tick bei jedem Sektorwechsel unter dem Zeiger
      const idx = segmentAtPointer();
      if (idx !== sp.lastIdx) {
        sp.lastIdx = idx;
        if (p < 1 && GB.audio) GB.audio.tick();
      }
      if (p >= 1) {
        S.state = 'idle';
        S.spin = null;
        S.highlight = sp.winner;
        if (sp.resolve) sp.resolve(sp.winner);
      }
    } else if (S.idleOn && S.names.length > 1) {
      S.rot += S.idleSpeed * dt;
    }

    draw();
  }

  function draw() {
    const ctx = S.ctx;
    if (!ctx) return;
    const size = S.size;
    const c = size / 2;
    const R = c - 8;
    const n = S.names.length;
    const light = isLight();

    ctx.clearRect(0, 0, size, size);

    // Außenring
    ctx.beginPath();
    ctx.arc(c, c, R + 4, 0, TAU);
    ctx.fillStyle = light ? '#d0d7de' : '#30363d';
    ctx.fill();

    if (!n) {
      // Leerzustand
      ctx.beginPath();
      ctx.arc(c, c, R, 0, TAU);
      ctx.fillStyle = light ? '#f6f8fa' : '#161b22';
      ctx.fill();
      ctx.fillStyle = light ? '#59636e' : '#7d8590';
      ctx.font = '13px -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('Noch keine Teilnehmer', c, c - 8);
      ctx.font = '12px -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif';
      ctx.fillText('Chat-Keyword oder Demo-Daten nutzen', c, c + 12);
      drawHub(ctx, c, light);
      return;
    }

    const seg = TAU / n;
    const showText = n <= 120;
    const fontSize = n > 60 ? 10 : n > 24 ? 11 : 12;

    for (let i = 0; i < n; i++) {
      const a0 = S.rot + i * seg;
      const a1 = a0 + seg;

      // Sektorfüllung: dezent alternierend, Gewinner grün hinterlegt
      ctx.beginPath();
      ctx.moveTo(c, c);
      ctx.arc(c, c, R, a0, a1);
      ctx.closePath();
      if (i === S.highlight) {
        ctx.fillStyle = light ? '#dafbe1' : '#12261a';
      } else if (i % 2 === 0) {
        ctx.fillStyle = light ? '#ffffff' : '#1c2128';
      } else {
        ctx.fillStyle = light ? '#eef1f4' : '#22272f';
      }
      ctx.fill();
      ctx.strokeStyle = light ? '#d0d7de' : '#30363d';
      ctx.lineWidth = 1;
      ctx.stroke();

      // Name entlang des Radius
      if (showText) {
        ctx.save();
        ctx.translate(c, c);
        ctx.rotate(a0 + seg / 2);
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        const isWin = i === S.highlight;
        ctx.font = (isWin ? '600 ' : '400 ') + fontSize +
          'px -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif';
        ctx.fillStyle = isWin
          ? (light ? '#1a7f37' : '#3fb950')
          : (light ? '#1f2328' : '#e6edf3');
        let label = S.names[i].display;
        const maxChars = R > 200 ? 16 : 12;
        if (label.length > maxChars) label = label.slice(0, maxChars - 1) + '…';
        ctx.fillText(label, R - 10, 0, R - 40);
        ctx.restore();
      }
    }

    drawHub(ctx, c, light);
  }

  function drawHub(ctx, c, light) {
    // Nabe in der Mitte
    ctx.beginPath();
    ctx.arc(c, c, 30, 0, TAU);
    ctx.fillStyle = light ? '#ffffff' : '#0d1117';
    ctx.fill();
    ctx.strokeStyle = light ? '#d0d7de' : '#30363d';
    ctx.lineWidth = 2;
    ctx.stroke();
    // Grüner Punkt als Markenzeichen
    ctx.beginPath();
    ctx.arc(c, c, 9, 0, TAU);
    ctx.fillStyle = light ? '#1f883d' : '#238636';
    ctx.fill();
  }

  /**
   * Dreht das Rad so, dass winnerIndex exakt unter dem Zeiger landet.
   * Gibt ein Promise zurück, das bei Stillstand auflöst.
   */
  function spinTo(winnerIndex, durationMs) {
    return new Promise(function (resolve) {
      const n = S.names.length;
      if (!n) { resolve(-1); return; }
      const winner = Math.max(0, Math.min(n - 1, winnerIndex));
      const seg = TAU / n;

      // Aktuelle Mitte des Gewinner-Sektors
      const center = S.rot + winner * seg + seg / 2;
      // Fehlende Drehung bis zur Zeigerposition (immer vorwärts)
      const delta = norm(POINTER - center);
      // Volle Umdrehungen je nach Dauer (mehr Dauer = mehr Runden)
      const turns = 4 + Math.round(durationMs / 1000);
      const from = S.rot;
      const to = from + turns * TAU + delta;

      S.highlight = -1;
      S.state = 'spinning';
      S.spin = {
        from: from,
        to: to,
        dur: Math.max(1200, durationMs),
        t0: performance.now(),
        winner: winner,
        lastIdx: segmentAtPointer(),
        resolve: resolve
      };
    });
  }

  function isSpinning() { return S.state === 'spinning'; }
  function setIdle(on) { S.idleOn = !!on; }
  function clearHighlight() { S.highlight = -1; }

  GB.wheel = {
    init: init,
    resize: resize,
    setData: setData,
    spinTo: spinTo,
    isSpinning: isSpinning,
    setIdle: setIdle,
    clearHighlight: clearHighlight
  };
})();

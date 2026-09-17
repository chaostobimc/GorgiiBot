/* ============================================================================
 * GorgiiBot – confetti.js
 * Leichtes Canvas-Konfetti ohne Abhängigkeiten: Regnet bei der
 * Gewinnverkündung ca. 3,5 Sekunden über den Bildschirm und blendet sich
 * danach selbst aus. Respektiert "Reduzierte Bewegung" (dann kein Konfetti).
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});
  const REDUCED = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const COLORS = ['#2ea043', '#388bfd', '#a371f7', '#d29922', '#f778ba', '#f85149', '#e6edf3'];

  let canvas = null;
  let ctx = null;
  let parts = [];
  let raf = 0;
  let endAt = 0;
  let running = false;

  function ensure() {
    if (canvas) return;
    canvas = document.createElement('canvas');
    canvas.id = 'confetti';
    const st = canvas.style;
    st.position = 'fixed';
    st.inset = '0';
    st.width = '100%';
    st.height = '100%';
    st.pointerEvents = 'none';
    st.zIndex = '300';
    document.body.appendChild(canvas);
    ctx = canvas.getContext('2d');
    resize();
    window.addEventListener('resize', resize);
  }

  function resize() {
    if (!canvas) return;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    canvas.width = Math.floor((window.innerWidth || 800) * dpr);
    canvas.height = Math.floor((window.innerHeight || 600) * dpr);
    if (ctx) ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  function rand(a, b) { return a + Math.random() * (b - a); }

  function burst(n) {
    const W = window.innerWidth || 800;
    const H = window.innerHeight || 600;
    for (let i = 0; i < n; i++) {
      const fromCenter = i % 3 === 0;
      parts.push({
        x: fromCenter ? W / 2 + rand(-40, 40) : rand(0, W),
        y: fromCenter ? H * 0.35 : rand(-40, -5),
        vx: rand(-1.6, 1.6) + (fromCenter ? rand(-3, 3) : 0),
        vy: fromCenter ? rand(-7, -3) : rand(1, 3),
        w: rand(5, 9),
        h: rand(8, 14),
        rot: rand(0, Math.PI * 2),
        vr: rand(-0.2, 0.2),
        color: COLORS[(Math.random() * COLORS.length) | 0],
        sway: rand(0, Math.PI * 2)
      });
    }
  }

  function frame(t) {
    if (!running) return;
    const W = window.innerWidth || 800;
    const H = window.innerHeight || 600;
    ctx.clearRect(0, 0, W, H);
    parts = parts.filter(function (p) { return p.y < H + 30; });
    parts.forEach(function (p) {
      p.vy = Math.min(p.vy + 0.05, 4.5);
      p.sway += 0.05;
      p.x += p.vx + Math.sin(p.sway) * 0.6;
      p.y += p.vy;
      p.rot += p.vr;
      ctx.save();
      ctx.translate(p.x, p.y);
      ctx.rotate(p.rot);
      ctx.fillStyle = p.color;
      ctx.globalAlpha = 0.92;
      // Flattern: Höhe schwingt mit der Rotation
      ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h * (0.4 + 0.6 * Math.abs(Math.cos(p.rot))));
      ctx.restore();
    });
    ctx.globalAlpha = 1;
    if (t < endAt || parts.length) {
      raf = requestAnimationFrame(frame);
    } else {
      stop();
    }
  }

  function celebrate() {
    if (REDUCED) return;
    ensure();
    resize();
    burst(150);
    endAt = performance.now() + 3500;
    if (!running) {
      running = true;
      raf = requestAnimationFrame(frame);
    }
  }

  function stop() {
    running = false;
    if (raf) {
      if (window.cancelAnimationFrame) window.cancelAnimationFrame(raf);
      raf = 0;
    }
    parts = [];
    if (ctx && canvas) ctx.clearRect(0, 0, canvas.width, canvas.height);
  }

  GB.confetti = { celebrate: celebrate, stop: stop };
})();

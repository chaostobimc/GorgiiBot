/* ============================================================================
 * GorgiiBot – audio.js
 * Sound-Engine auf Basis der Web Audio API. Alle Sounds werden synthetisiert,
 * es sind keine externen Audio-Dateien nötig.
 * Sounds: tick (Rad/Kärtchen), win (Gewinner), confirm (Claim), timeout,
 * beep (Countdown). Master-Lautstärke + Mute, faul initialisiert (Autoplay-
 * Policies verlangen eine Nutzer-Geste).
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});
  const clamp = function (v, a, b) { return Math.min(b, Math.max(a, v)); };

  let ctx = null;        // AudioContext (lazy)
  let master = null;     // Master-Gain-Node
  let enabled = true;
  let volume = 0.6;
  let lastTick = 0;
  let tickAlt = false;

  /** AudioContext erzeugen/fortsetzen (nur nach Nutzer-Geste aufrufen) */
  function ensure() {
    try {
      if (!ctx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return false;
        ctx = new AC();
        master = ctx.createGain();
        master.gain.value = enabled ? volume : 0;
        master.connect(ctx.destination);
      }
      if (ctx.state === 'suspended') ctx.resume();
      return true;
    } catch (e) {
      return false;
    }
  }

  function applyGain() {
    if (master) master.gain.value = enabled ? volume : 0;
  }

  function setEnabled(on) {
    enabled = !!on;
    applyGain();
  }

  function setVolume(v) {
    volume = clamp(Number(v) || 0, 0, 1);
    applyGain();
  }

  function isEnabled() { return enabled; }

  /** Kurzer Oszillator-Blip */
  function blip(freq, dur, type, gainVal, when, slideTo) {
    if (!enabled) return;
    if (!ensure()) return;
    try {
      const t0 = ctx.currentTime + (when || 0);
      const osc = ctx.createOscillator();
      const g = ctx.createGain();
      osc.type = type || 'sine';
      osc.frequency.setValueAtTime(freq, t0);
      if (slideTo) osc.frequency.exponentialRampToValueAtTime(slideTo, t0 + dur);
      g.gain.setValueAtTime(0.0001, t0);
      g.gain.exponentialRampToValueAtTime(gainVal || 0.5, t0 + 0.008);
      g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
      osc.connect(g);
      g.connect(master);
      osc.start(t0);
      osc.stop(t0 + dur + 0.05);
    } catch (e) { /* ignorieren */ }
  }

  /** Tick-Sound (leicht alternierend, gedrosselt) */
  function tick() {
    const now = performance.now();
    if (now - lastTick < 35) return; // Drosselung bei hohem Tempo
    lastTick = now;
    tickAlt = !tickAlt;
    // Kurzer "Klick": Dreieck-Oszillator, fallende Tonhöhe
    blip(tickAlt ? 1250 : 1050, 0.045, 'triangle', 0.35, 0, 700);
  }

  /** Gewinn-Fanfare: kleine aufsteigende Arpeggio */
  function win() {
    const notes = [523.25, 659.25, 783.99, 1046.5]; // C5 E5 G5 C6
    notes.forEach(function (f, i) {
      blip(f, 0.28, 'sine', 0.5, i * 0.11);
      blip(f / 2, 0.3, 'triangle', 0.18, i * 0.11);
    });
  }

  /** Bestätigung (Claim): freundlicher Zwei-Klang */
  function confirm() {
    blip(880, 0.16, 'sine', 0.45, 0);
    blip(1318.5, 0.24, 'sine', 0.45, 0.12);
  }

  /** Timeout: tiefer, kurzer Buzz */
  function timeout() {
    blip(220, 0.3, 'sawtooth', 0.22, 0, 110);
    blip(110, 0.35, 'triangle', 0.3, 0.05, 90);
  }

  /** Countdown-Beep */
  function beep(high) {
    blip(high ? 1320 : 990, 0.09, 'square', 0.16, 0);
  }

  /** Test-Sound für die Einstellungen */
  function test() {
    ensure();
    tick();
    window.setTimeout(function () { blip(880, 0.14, 'sine', 0.4, 0); }, 120);
    window.setTimeout(function () { blip(1174.7, 0.2, 'sine', 0.4, 0); }, 260);
  }

  GB.audio = {
    ensure: ensure,
    setEnabled: setEnabled,
    setVolume: setVolume,
    isEnabled: isEnabled,
    tick: tick,
    win: win,
    confirm: confirm,
    timeout: timeout,
    beep: beep,
    test: test
  };
})();

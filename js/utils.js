/* ============================================================================
 * GorgiiBot – utils.js
 * Kleine, abhängigkeitsfreie Helfer (DOM, Zufall, Formatierung, Download).
 * Lädt als klassisches Script (kein ES-Modul), damit die App auch per
 * Doppelklick auf index.html (file://) funktioniert.
 * ========================================================================== */
(function () {
  'use strict';

  // Globaler Namespace für alle Module
  window.GB = window.GB || {};
  const GB = window.GB;

  /** Kurzer querySelector */
  function $(sel, root) {
    return (root || document).querySelector(sel);
  }

  /** querySelectorAll als echtes Array */
  function $$(sel, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(sel));
  }

  /** HTML escapen (gegen Chat-Injection ins DOM) */
  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function clamp(v, min, max) {
    return Math.min(max, Math.max(min, v));
  }

  /** Kryptografisch sicherer Zufalls-Integer in [min, max) */
  function randInt(min, max) {
    min = Math.ceil(min);
    max = Math.floor(max);
    if (max <= min) return min;
    const range = max - min;
    // Rejection sampling für gleichmäßige Verteilung
    const maxUint = 4294967296;
    const limit = maxUint - (maxUint % range);
    const arr = new Uint32Array(1);
    let x;
    do {
      window.crypto.getRandomValues(arr);
      x = arr[0];
    } while (x >= limit);
    return min + (x % range);
  }

  function pick(arr) {
    if (!arr || !arr.length) return undefined;
    return arr[randInt(0, arr.length)];
  }

  /** Fisher–Yates Shuffle (gibt neues Array zurück) */
  function shuffle(arr) {
    const a = arr.slice();
    for (let i = a.length - 1; i > 0; i--) {
      const j = randInt(0, i + 1);
      const t = a[i];
      a[i] = a[j];
      a[j] = t;
    }
    return a;
  }

  /** Einfacher String-Hash (djb2) für Farben u.ä. */
  function hashStr(s) {
    s = String(s || '');
    let h = 5381;
    for (let i = 0; i < s.length; i++) {
      h = ((h << 5) + h + s.charCodeAt(i)) | 0;
    }
    return Math.abs(h);
  }

  // Gedeckte Avatar-Farben (GitHub-inspiriert, dezent)
  const AVATAR_COLORS = [
    '#1f6feb', '#8957e5', '#1f883d', '#9a6700',
    '#a40e26', '#6639ba', '#0969da', '#6e40c9',
    '#2da44e', '#cf222e', '#8250df', '#0550ae'
  ];

  function colorFor(name) {
    return AVATAR_COLORS[hashStr(name) % AVATAR_COLORS.length];
  }

  function initials(name) {
    const s = String(name || '?').replace(/^_+/, '');
    return (s.charAt(0) || '?').toUpperCase();
  }

  /** HH:MM:SS */
  function timeHM(ts) {
    const d = new Date(ts);
    const p = (n) => String(n).padStart(2, '0');
    return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
  }

  /** "vor X Min." */
  function timeAgo(ts) {
    const s = Math.max(0, Math.floor((Date.now() - ts) / 1000));
    if (s < 10) return 'jetzt';
    if (s < 60) return 'vor ' + s + ' Sek.';
    const m = Math.floor(s / 60);
    if (m < 60) return 'vor ' + m + ' Min.';
    const h = Math.floor(m / 60);
    return 'vor ' + h + ' Std.';
  }

  function debounce(fn, ms) {
    let t = 0;
    return function () {
      const args = arguments;
      const self = this;
      window.clearTimeout(t);
      t = window.setTimeout(function () { fn.apply(self, args); }, ms);
    };
  }

  /** Datei-Download im Browser erzeugen */
  function download(filename, content, mime) {
    const blob = new Blob([content], { type: mime || 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    window.setTimeout(function () {
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    }, 100);
  }

  /** Datei einlesen (für JSON-Import) */
  function readFile(file) {
    return new Promise(function (resolve, reject) {
      const r = new FileReader();
      r.onload = function () { resolve(String(r.result || '')); };
      r.onerror = function () { reject(r.error); };
      r.readAsText(file);
    });
  }

  GB.util = {
    $, $$, esc, clamp, randInt, pick, shuffle,
    hashStr, colorFor, initials, timeHM, timeAgo,
    debounce, download, readFile
  };
})();

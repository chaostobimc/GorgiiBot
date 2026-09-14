/* ============================================================================
 * GorgiiBot – obs.js
 * Der OBS-Knopf: öffnet einen Dialog mit der Overlay-URL (overlay.html),
 * die Glücksrad oder Case Opening als transparente OBS-Browserquelle zeigt.
 * Die URL enthält Kanal, Ansicht, Modus, Keywords und Zeiten als Parameter.
 * Ändert nichts am Verhalten der Hauptapp – reine Ergänzung.
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});
  const U = GB.util;

  /** Overlay-URL aus dem aktuellen Stand bauen */
  function buildUrl() {
    const st = GB.store.state;
    const s = st.settings;
    const base = new URL('overlay.html', location.href);
    const input = U.$('#channelInput');
    const channel = GB.twitch.cleanChannel(
      (input && input.value) || st.channel || GB.twitch.getChannel() || ''
    );
    base.searchParams.set('channel', channel);
    const checked = document.querySelector('input[name="obsView"]:checked');
    base.searchParams.set('view', checked ? checked.value : (st.view === 'case' ? 'case' : 'wheel'));
    base.searchParams.set('mode', s.mode);
    base.searchParams.set('keywords', (s.keywords || []).join(','));
    base.searchParams.set('spin', String(s.spinSeconds));
    base.searchParams.set('claim', String(s.claimSeconds));
    if ((s.prize || '').trim()) base.searchParams.set('prize', s.prize.trim());
    base.searchParams.set('ignore', s.ignoreCommands ? '1' : '0');
    base.searchParams.set('bots', s.defaultBots ? '1' : '0');
    if ((s.banlist || '').trim()) base.searchParams.set('ban', s.banlist.trim());
    base.searchParams.set('theme', st.theme);
    return base.toString();
  }

  function refresh() {
    const el = U.$('#obsUrl');
    if (el) el.value = buildUrl();
  }

  function open() {
    // Aktuelle Ansicht vorauswählen
    const v = GB.store.state.view === 'case' ? 'case' : 'wheel';
    const r = document.querySelector('input[name="obsView"][value="' + v + '"]');
    if (r) r.checked = true;
    refresh();
    U.$('#obsModal').classList.remove('hidden');
    const url = U.$('#obsUrl');
    if (url) {
      url.focus();
      url.select();
    }
  }

  function close() {
    U.$('#obsModal').classList.add('hidden');
  }

  function copy() {
    const url = U.$('#obsUrl');
    if (!url) return;
    const done = function () { GB.ui.toast('Overlay-URL kopiert.', 'ok'); };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(url.value).then(done, function () {
        url.select();
        document.execCommand('copy');
        done();
      });
    } else {
      url.select();
      try { document.execCommand('copy'); } catch (e) {}
      done();
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    U.$('#obsBtn').addEventListener('click', open);
    U.$('#obsClose').addEventListener('click', close);
    U.$('#obsModal').addEventListener('click', function (ev) {
      if (ev.target.id === 'obsModal') close();
    });
    U.$('#obsCopy').addEventListener('click', copy);
    Array.prototype.forEach.call(
      document.querySelectorAll('input[name="obsView"]'),
      function (r) { r.addEventListener('change', refresh); }
    );
    document.addEventListener('keydown', function (ev) {
      if (ev.key === 'Escape') close();
    });
  });
})();

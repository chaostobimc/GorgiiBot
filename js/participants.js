/* ============================================================================
 * GorgiiBot – participants.js
 * Teilnehmer-Pool: Hinzufügen (mit Duplikat-/Ban-Filter), Entfernen,
 * Suche, Sortierung, Rendering der Liste, Avatar-Cache (decapi.me mit
 * Initialen-Fallback), CSV/JSON-Export & JSON-Import.
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});
  const U = GB.util;

  // Map: login -> Teilnehmer {login, display, color, avatar, joinedAt}
  const byLogin = {};
  let order = [];          // Einfügereihenfolge (Logins)
  let searchTerm = '';
  let sortMode = 'newest'; // 'newest' | 'oldest' | 'az'
  let rosterEl = null;

  // ---- Avatar-Queue (schont decapi.me, max. 1 Request / 400ms) ----
  const avatarQueue = [];
  let avatarBusy = false;

  function processAvatarQueue() {
    if (avatarBusy) return;
    const login = avatarQueue.shift();
    if (!login) return;
    const p = byLogin[login];
    if (!p || p.avatar || !GB.store.state.settings.avatars) {
      window.setTimeout(processAvatarQueue, 50);
      return;
    }
    avatarBusy = true;
    fetch('https://decapi.me/twitch/avatar/' + encodeURIComponent(login))
      .then(function (res) { return res.text(); })
      .then(function (txt) {
        const url = String(txt || '').trim();
        // decapi antwortet mit der Bild-URL als Text; Fehlerseiten filtern
        if (p && /^https?:\/\/.+/i.test(url) && url.length < 500) {
          p.avatar = url;
          updateAvatarInDOM(p);
          GB.store.save();
        }
      })
      .catch(function () { /* stiller Fallback auf Initialen */ })
      .finally(function () {
        avatarBusy = false;
        window.setTimeout(processAvatarQueue, 400);
      });
  }

  function queueAvatar(login) {
    if (avatarQueue.indexOf(login) === -1 && avatarQueue.length < 500) {
      avatarQueue.push(login);
      processAvatarQueue();
    }
  }

  function updateAvatarInDOM(p) {
    if (!rosterEl) return;
    const slots = rosterEl.querySelectorAll('[data-avatar-for="' + p.login + '"]');
    Array.prototype.forEach.call(slots, function (slot) {
      slot.innerHTML = avatarInner(p, 32);
    });
  }

  /** Avatar-HTML: Bild (falls geladen) oder Initialen-Platzhalter */
  function avatarInner(p, size) {
    size = size || 32;
    if (p.avatar) {
      return '<img src="' + U.esc(p.avatar) + '" alt="" width="' + size +
        '" height="' + size + '" loading="lazy" onerror="this.parentNode.innerHTML=' +
        "'" + '<span>' + U.esc(U.initials(p.display || p.login)) + '</span>' + "'" + '">';
    }
    return '<span>' + U.esc(U.initials(p.display || p.login)) + '</span>';
  }

  function avatarHTML(p, size) {
    size = size || 32;
    const bg = p.color && /^#[0-9a-f]{6}$/i.test(p.color) ? p.color : U.colorFor(p.login);
    return '<span class="avatar" data-avatar-for="' + U.esc(p.login) + '"' +
      ' style="width:' + size + 'px;height:' + size + 'px;background:' + U.esc(bg) +
      ';font-size:' + Math.round(size * 0.42) + 'px">' + avatarInner(p, size) + '</span>';
  }

  // ---- Pool-Logik ----

  function count() { return order.length; }

  function has(login) {
    return !!byLogin[String(login || '').toLowerCase()];
  }

  function get(login) {
    return byLogin[String(login || '').toLowerCase()] || null;
  }

  /** Stabile Reihenfolge für Rad & Roulette (Einfügereihenfolge) */
  function ordered() {
    return order.map(function (l) { return byLogin[l]; }).filter(Boolean);
  }

  function add(info) {
    const login = String(info.login || '').trim().toLowerCase();
    if (!login || has(login)) return null;
    const p = {
      login: login,
      display: String(info.display || login),
      color: String(info.color || ''),
      avatar: String(info.avatar || ''),
      joinedAt: info.joinedAt || Date.now()
    };
    byLogin[login] = p;
    order.push(login);
    if (!p.avatar && GB.store.state.settings.avatars) queueAvatar(login);
    persist();
    render();
    return p;
  }

  /**
   * Chat-Nachricht gegen Modus-/Ban-/Duplikat-Regeln prüfen und ggf. aufnehmen.
   * Rückgabe: {added:boolean, reason:string}
   */
  function tryAdd(msg) {
    const st = GB.store.state;
    if (!st.collectionOpen) return { added: false, reason: 'closed' };
    const login = msg.login;
    if (has(login)) return { added: false, reason: 'duplicate' };
    const banned = GB.store.bannedSet();
    if (banned[login]) return { added: false, reason: 'banned' };

    const s = st.settings;
    const text = String(msg.text || '').trim();
    if (!text) return { added: false, reason: 'empty' };

    if (s.mode === 'all') {
      if (s.ignoreCommands && text.charAt(0) === '!') {
        return { added: false, reason: 'command' };
      }
    } else {
      // Keyword-Modus: Nachricht muss mit einem Keyword beginnen (oder gleich sein)
      const lower = text.toLowerCase();
      const hit = (s.keywords || []).some(function (kw) {
        kw = String(kw || '').trim().toLowerCase();
        if (!kw) return false;
        return lower === kw || lower.indexOf(kw + ' ') === 0;
      });
      if (!hit) return { added: false, reason: 'no-keyword' };
    }

    const p = add({ login: login, display: msg.display, color: msg.color });
    return { added: !!p, reason: p ? 'joined' : 'duplicate', participant: p };
  }

  function remove(login) {
    login = String(login || '').toLowerCase();
    if (!byLogin[login]) return false;
    delete byLogin[login];
    order = order.filter(function (l) { return l !== login; });
    persist();
    render();
    return true;
  }

  function clear() {
    Object.keys(byLogin).forEach(function (k) { delete byLogin[k]; });
    order = [];
    avatarQueue.length = 0;
    persist();
    render();
  }

  /** Aus Store wiederherstellen (beim Start) */
  function restore(list) {
    Object.keys(byLogin).forEach(function (k) { delete byLogin[k]; });
    order = [];
    (list || []).forEach(function (p) {
      if (p && p.login && !byLogin[p.login]) {
        byLogin[p.login] = {
          login: p.login,
          display: p.display || p.login,
          color: p.color || '',
          avatar: p.avatar || '',
          joinedAt: p.joinedAt || Date.now()
        };
        order.push(p.login);
      }
    });
    render();
  }

  function persist() {
    GB.store.state.participants = ordered();
    GB.store.save();
  }

  // ---- Export / Import ----

  function exportCSV() {
    const rows = ['login,display_name,joined_at'];
    ordered().forEach(function (p) {
      const q = function (s) { return '"' + String(s || '').replace(/"/g, '""') + '"'; };
      rows.push([q(p.login), q(p.display), q(new Date(p.joinedAt).toISOString())].join(','));
    });
    U.download('gorgiibot-teilnehmer.csv', rows.join('\n'), 'text/csv;charset=utf-8');
  }

  function exportJSON() {
    U.download(
      'gorgiibot-teilnehmer.json',
      JSON.stringify(ordered(), null, 2),
      'application/json;charset=utf-8'
    );
  }

  function importJSON(text) {
    let arr;
    try {
      arr = JSON.parse(text);
    } catch (e) {
      return { ok: false, error: 'Keine gültige JSON-Datei.' };
    }
    if (!Array.isArray(arr)) return { ok: false, error: 'JSON muss eine Liste sein.' };
    let added = 0;
    arr.forEach(function (p) {
      if (p && (p.login || p.name)) {
        const r = add({
          login: String(p.login || p.name || '').toLowerCase(),
          display: p.display || p.display_name || p.name || p.login,
          color: p.color || '',
          avatar: p.avatar || '',
          joinedAt: p.joinedAt || Date.now()
        });
        if (r) added++;
      }
    });
    return { ok: true, added: added };
  }

  // ---- Rendering ----

  function filtered() {
    let list = ordered();
    if (searchTerm) {
      list = list.filter(function (p) {
        return p.login.indexOf(searchTerm) !== -1 ||
          p.display.toLowerCase().indexOf(searchTerm) !== -1;
      });
    }
    if (sortMode === 'az') {
      list = list.slice().sort(function (a, b) {
        return a.display.toLowerCase() < b.display.toLowerCase() ? -1 : 1;
      });
    } else if (sortMode === 'oldest') {
      list = list.slice().sort(function (a, b) { return a.joinedAt - b.joinedAt; });
    } else {
      list = list.slice().sort(function (a, b) { return b.joinedAt - a.joinedAt; });
    }
    return list;
  }

  function render() {
    if (!rosterEl) return;
    const list = filtered();

    const countEl = U.$('#rosterCount');
    if (countEl) {
      countEl.textContent = count() + (count() === 1 ? ' Teilnehmer' : ' Teilnehmer');
    }
    const emptyEl = U.$('#rosterEmpty');
    if (emptyEl) {
      emptyEl.classList.toggle('hidden', list.length > 0);
      emptyEl.innerHTML = count() > 0
        ? 'Keine Treffer für diese Suche.'
        : 'Leer – Teilnehmer kommen über den Chat<br>oder den Demo-Button herein.';
    }

    // Effizientes Rendering via innerHTML-String (auch bei 1000+ okay)
    const html = list.slice(0, 1000).map(function (p) {
      return '<div class="roster-item" data-login="' + U.esc(p.login) + '">' +
        avatarHTML(p, 32) +
        '<div class="roster-meta">' +
        '<span class="roster-name">' + U.esc(p.display) + '</span>' +
        '<span class="roster-sub">' + U.esc(p.login) + ' · ' + U.esc(U.timeHM(p.joinedAt)) + '</span>' +
        '</div>' +
        '<button class="icon-btn roster-remove" data-remove="' + U.esc(p.login) + '"' +
        ' title="Entfernen" aria-label="' + U.esc(p.display) + ' entfernen">×</button>' +
        '</div>';
    }).join('');
    rosterEl.innerHTML = html;

    // Zähler in der Kopfzeile aktualisieren
    if (GB.ui && GB.ui.updateStats) GB.ui.updateStats();
    // Bühnen (Rad/Roulette) über neue Daten informieren
    if (GB.app && GB.app.refreshStages) GB.app.refreshStages();
  }

  function setSearch(term) {
    searchTerm = String(term || '').trim().toLowerCase();
    render();
  }

  function setSort(mode) {
    sortMode = mode;
    render();
  }

  function init(rosterElement) {
    rosterEl = rosterElement;
    // Event-Delegation für Remove-Buttons
    rosterEl.addEventListener('click', function (ev) {
      const btn = ev.target.closest('[data-remove]');
      if (!btn) return;
      const login = btn.getAttribute('data-remove');
      const p = get(login);
      remove(login);
      if (p && GB.ui) GB.ui.log('Teilnehmer entfernt: ' + p.display, 'muted');
    });
  }

  GB.pool = {
    init: init,
    render: render,
    add: add,
    tryAdd: tryAdd,
    remove: remove,
    clear: clear,
    restore: restore,
    has: has,
    get: get,
    count: count,
    ordered: ordered,
    setSearch: setSearch,
    setSort: setSort,
    avatarHTML: avatarHTML,
    exportCSV: exportCSV,
    exportJSON: exportJSON,
    importJSON: importJSON
  };
})();

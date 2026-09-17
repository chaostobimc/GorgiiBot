/* ============================================================================
 * GorgiiBot – store.js
 * Zentraler State + Persistenz in localStorage (Theme, Einstellungen,
 * Kanal, Teilnehmer). Alles bleibt lokal im Browser.
 * ========================================================================== */
(function () {
  'use strict';

  const GB = (window.GB = window.GB || {});
  const KEY = 'gorgiibot.v1';

  const DEFAULTS = {
    theme: 'dark',          // 'dark' | 'light'
    channel: '',
    view: 'wheel',          // 'wheel' | 'roulette'
    collectionOpen: true,
    settings: {
      mode: 'keywords',     // 'keywords' | 'all'
      keywords: ['!giveaway'],
      ignoreCommands: true, // in "Alle Nachrichten": Nachrichten mit ! ignorieren
      prize: '',
      spinSeconds: 8,       // 3–20
      claimSeconds: 60,     // 10–300
      sound: true,
      volume: 0.6,
      accent: 'green',      // 'green' | 'blue' | 'purple' | 'orange'
      banlist: '',
      defaultBots: true,
      autoRemoveConfirmed: true,
      autoRemoveTimeout: true,
      avatars: true         // Avatare via decapi.me laden (Fallback: Initialen)
    },
    participants: [],       // [{login, display, color, avatar, joinedAt}]
    stats: { messages: 0, spins: 0 }
  };

  // Bekannte Chat-Bots, die nie teilnehmen sollen
  const DEFAULT_BOTS = [
    'nightbot', 'streamelements', 'streamlabs', 'moobot', 'fossabot',
    'commanderroot', 'wizebot', 'pretzelrocks', 'soundalerts', 'blerp',
    'streamholic', 'kofistreams', 'sery_bot', 'supibot', 'tmi', 'twitchnotify'
  ];

  let saveTimer = 0;

  function deepMerge(base, over) {
    const out = Array.isArray(base) ? base.slice() : Object.assign({}, base);
    if (!over || typeof over !== 'object') return out;
    Object.keys(over).forEach(function (k) {
      if (
        over[k] && typeof over[k] === 'object' && !Array.isArray(over[k]) &&
        base && typeof base[k] === 'object' && !Array.isArray(base[k])
      ) {
        out[k] = deepMerge(base[k], over[k]);
      } else {
        out[k] = over[k];
      }
    });
    return out;
  }

  function load() {
    let data = null;
    try {
      const raw = window.localStorage.getItem(KEY);
      if (raw) data = JSON.parse(raw);
    } catch (e) {
      data = null;
    }
    GB.store.state = deepMerge(DEFAULTS, data || {});
    // Hygiene: ungültige Werte abfangen
    const st = GB.store.state;
    if (!Array.isArray(st.settings.keywords)) st.settings.keywords = ['!giveaway'];
    if (['green', 'blue', 'purple', 'orange'].indexOf(st.settings.accent) === -1) st.settings.accent = 'green';
    if (!Array.isArray(st.participants)) st.participants = [];
    // Nur plausible Teilnehmer übernehmen (max. 5000)
    st.participants = st.participants
      .filter(function (p) { return p && typeof p.login === 'string' && p.login; })
      .slice(0, 5000);
    return st;
  }

  function saveNow() {
    try {
      window.localStorage.setItem(KEY, JSON.stringify(GB.store.state));
    } catch (e) { /* Speicher voll o.ä. – ignorieren */ }
  }

  /** Entprelltes Speichern (z. B. bei jedem Chat-Join) */
  function save() {
    window.clearTimeout(saveTimer);
    saveTimer = window.setTimeout(saveNow, 400);
  }

  function resetData() {
    GB.store.state.participants = [];
    GB.store.state.stats = { messages: 0, spins: 0 };
    saveNow();
  }

  /** Banliste als Set (kleingeschrieben), inkl. Standard-Bots wenn aktiv */
  function bannedSet() {
    const s = GB.store.state.settings;
    const set = {};
    if (s.defaultBots) {
      DEFAULT_BOTS.forEach(function (b) { set[b] = true; });
    }
    String(s.banlist || '')
      .split(/[\n,;]+/)
      .map(function (x) { return x.trim().toLowerCase().replace(/^@/, ''); })
      .filter(Boolean)
      .forEach(function (x) { set[x] = true; });
    return set;
  }

  GB.store = {
    DEFAULTS: DEFAULTS,
    DEFAULT_BOTS: DEFAULT_BOTS,
    state: null,
    load: load,
    save: save,
    saveNow: saveNow,
    resetData: resetData,
    bannedSet: bannedSet
  };
})();

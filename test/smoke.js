// Smoke-Test: lädt alle GorgiiBot-Module in Node mit DOM-Stubs und
// prüft Kern-Logik (Utils, Store, Pool-Regeln, Gewinner-Flow, Akzent).
// Start: npm test (oder: node test/smoke.js)
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const crypto = require('crypto');

const JS_DIR = path.join(__dirname, '..', 'js');

function makeEl() {
  return {
    value: '', checked: false, textContent: '', innerHTML: '', disabled: false,
    title: '', style: {}, dataset: {}, _attrs: {},
    classList: { toggle() {}, add() {}, remove() {}, contains() { return false; } },
    setAttribute(k, v) { this._attrs[k] = v; },
    getAttribute(k) { return this._attrs[k] || null; },
    addEventListener() {}, removeEventListener() {},
    querySelector() { return makeEl(); }, querySelectorAll() { return []; },
    appendChild() {}, removeChild() {}, prepend() {}, remove() {},
    click() {}, focus() {}, blur() {},
    children: [], firstChild: null, files: []
  };
}

const domReady = [];
const memStore = {};
global.window = {
  crypto: crypto.webcrypto,
  localStorage: {
    getItem: (k) => (k in memStore ? memStore[k] : null),
    setItem: (k, v) => { memStore[k] = String(v); },
    removeItem: (k) => { delete memStore[k]; }
  },
  setTimeout, clearTimeout, setInterval, clearInterval,
  requestAnimationFrame: () => 0,
  matchMedia: undefined, devicePixelRatio: 1,
  confirm: () => false, fetch: undefined
};
global.document = {
  querySelector: () => makeEl(),
  querySelectorAll: () => [],
  getElementById: () => null,
  createElement: () => makeEl(),
  addEventListener: (t, cb) => { if (t === 'DOMContentLoaded') domReady.push(cb); },
  documentElement: makeEl(), body: makeEl()
};
global.location = { protocol: 'http:' };

const files = ['utils', 'audio', 'store', 'twitch', 'participants', 'wheel',
  'roulette', 'winner', 'ui', 'confetti', 'app'];
for (const f of files) {
  const code = fs.readFileSync(path.join(JS_DIR, f + '.js'), 'utf8');
  vm.runInThisContext(code, { filename: f + '.js' });
}
console.log('LOAD ok');

let pass = 0, fail = 0;
function assert(name, cond) {
  if (cond) { pass++; console.log('  PASS', name); }
  else { fail++; console.log('  FAIL', name); }
}

const GB = global.window.GB;

// --- Utils ---
assert('esc', GB.util.esc('<b>"&"</b>') === '&lt;b&gt;&quot;&amp;&quot;&lt;/b&gt;');
const r = GB.util.randInt(0, 10);
assert('randInt range', r >= 0 && r < 10);
const sh = GB.util.shuffle([1, 2, 3, 4, 5]);
assert('shuffle keeps elements', sh.slice().sort().join() === '1,2,3,4,5');
assert('colorFor deterministic', GB.util.colorFor('alice') === GB.util.colorFor('alice'));
assert('timeHM format', /^\d\d:\d\d:\d\d$/.test(GB.util.timeHM(Date.now())));

// --- App boot (lädt u.a. den Store) ---
domReady.forEach((cb) => cb());
console.log('BOOT ok');

// --- Store ---
assert('store loaded', !!GB.store.state && GB.store.state.theme === 'dark');
assert('bannedSet has nightbot', !!GB.store.bannedSet()['nightbot']);
assert('accent default green', GB.store.state.settings.accent === 'green');
GB.store.state.settings.avatars = false; // keine Netz-Requests im Test

// --- Akzent ---
GB.ui.applyAccent('blue');
assert('accent gesetzt', global.document.documentElement.getAttribute('data-accent') === 'blue');
GB.ui.applyAccent('pink');
assert('accent fallback green', global.document.documentElement.getAttribute('data-accent') === 'green');
GB.ui.applyAccent('green');

// --- Verlauf / Konfetti / Collapse (API + Guards) ---
assert('confetti API', !!GB.confetti && typeof GB.confetti.celebrate === 'function');
GB.confetti.stop();
assert('confetti stop ok', true);
assert('appendHistory API', typeof GB.winner.appendHistory === 'function');
GB.winner.appendHistory({ login: 'x', text: 'y', ts: Date.now() });
assert('appendHistory inaktiv ok', true);
assert('userHistory leer', GB.app.userHistory('alice').length === 0);
GB.ui.initCollapse();
assert('initCollapse ok', typeof GB.store.state.collapsedCards === 'object');

// --- Pool-Regeln ---
GB.pool.clear();
let res = GB.pool.tryAdd({ login: 'alice', display: 'Alice', color: '#ff0000', text: '!giveaway', ts: Date.now() });
assert('keyword join', res.added === true && GB.pool.count() === 1);
res = GB.pool.tryAdd({ login: 'alice', display: 'Alice', color: '', text: '!giveaway bitte', ts: Date.now() });
assert('duplicate blocked', res.added === false && res.reason === 'duplicate');
res = GB.pool.tryAdd({ login: 'bob', display: 'Bob', color: '', text: 'nur chat', ts: Date.now() });
assert('no-keyword ignored', res.added === false && res.reason === 'no-keyword');
res = GB.pool.tryAdd({ login: 'nightbot', display: 'Nightbot', color: '', text: '!giveaway', ts: Date.now() });
assert('bot banned', res.added === false && res.reason === 'banned');
GB.store.state.settings.mode = 'all';
res = GB.pool.tryAdd({ login: 'carol', display: 'Carol', color: '', text: '!irgendeinbefehl', ts: Date.now() });
assert('command ignored in all-mode', res.added === false && res.reason === 'command');
res = GB.pool.tryAdd({ login: 'carol', display: 'Carol', color: '', text: 'hallo chat!', ts: Date.now() });
assert('all-mode join', res.added === true && GB.pool.count() === 2);
GB.pool.tryAdd({ login: 'dave', display: 'Dave', color: '', text: 'hi', ts: Date.now() });
assert('ordered stable', GB.pool.ordered().map((p) => p.login).join() === 'alice,carol,dave');
assert('remove', GB.pool.remove('dave') === true && GB.pool.count() === 2);
assert('remove missing', GB.pool.remove('nobody') === false);

// --- Twitch-Kanal-Normalisierung ---
assert('cleanChannel url', GB.twitch.cleanChannel('https://twitch.tv/Gorgii_123') === 'gorgii_123');
assert('cleanChannel hash', GB.twitch.cleanChannel('#Foo-Bar!') === 'foobar');

// --- Gewinner-Flow (Claim) ---
let claimedEvt = null, timeoutEvt = null;
GB.winner.on('onClaimed', (p) => { claimedEvt = p.login; });
GB.winner.on('onTimeout', (p) => { timeoutEvt = p.login; });
const alice = GB.pool.get('alice');
GB.winner.start(alice, { claimSeconds: 60, isReroll: false });
assert('winner active+pending', GB.winner.isActive() && GB.winner.isPending());
assert('claim works', GB.winner.claim('test') === true && claimedEvt === 'alice');
assert('claim idempotent', GB.winner.claim('test') === false);

// --- Gewinner-Flow (Timeout -> Reroll-Callback) ---
GB.winner.start(alice, { claimSeconds: 1, isReroll: false });
setTimeout(() => {
  assert('timeout fired', timeoutEvt === 'alice');
  GB.winner.reset();
  assert('reset clears', !GB.winner.isActive());
  console.log(`\nERGEBNIS SMOKE: ${pass} bestanden, ${fail} fehlgeschlagen`);
  process.exit(fail ? 1 : 0);
}, 9000);

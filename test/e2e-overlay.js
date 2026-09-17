// Overlay-E2E: overlay.html mit URL-Parametern laden, Pool per API füllen,
// Spin + Claim prüfen – je einmal für Rad und Roulette.
// Start: npm test (oder: node test/e2e-overlay.js) – braucht: npm install (jsdom)
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const DIR = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(DIR, 'overlay.html'), 'utf8');
const errors = [];

function makeCtx(search) {
  const dom = new JSDOM(html, {
    url: 'http://localhost:8080/overlay.html' + search,
    pretendToBeVisual: true,
    runScripts: 'outside-only'
  });
  const { window } = dom;
  window.addEventListener('error', (ev) => {
    errors.push('window.onerror: ' + (ev.error && ev.error.stack || ev.message));
  });
  const ctxStub = new Proxy({}, {
    get(t, prop) {
      if (prop === 'measureText') return () => ({ width: 10 });
      if (typeof prop === 'string') return (...a) => undefined;
      return undefined;
    },
    set() { return true; }
  });
  window.HTMLCanvasElement.prototype.getContext = function () { return ctxStub; };
  window.requestAnimationFrame = (cb) => setTimeout(() => {
    try { cb(window.performance.now()); } catch (e) { errors.push('rAF: ' + e.stack); }
  }, 16);
  window.cancelAnimationFrame = (id) => clearTimeout(id);
  window.matchMedia = window.matchMedia || (() => ({ matches: false }));
  window.fetch = () => Promise.resolve({ text: () => Promise.resolve('not-a-url') });
  const files = ['utils', 'audio', 'store', 'twitch', 'participants', 'wheel',
    'roulette', 'winner', 'ui', 'overlay'];
  for (const f of files) {
    window.eval(fs.readFileSync(path.join(DIR, 'js', f + '.js'), 'utf8') + `\n//# sourceURL=${f}.js`);
  }
  return window;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let pass = 0, fail = 0;
function assert(name, cond, extra) {
  if (cond) { pass++; console.log('  PASS', name); }
  else { fail++; console.log('  FAIL', name, extra || ''); }
}

async function testView(view) {
  console.log(`--- Overlay: ${view} ---`);
  const search = `?view=${view}&channel=demokanal&mode=keywords&keywords=!win,!dabei&spin=3&claim=60&prize=Testpreis&bots=1&theme=dark&accent=blue`;
  const window = makeCtx(search);
  await new Promise((resolve) => {
    const rs = window.document.readyState;
    if (rs === 'interactive' || rs === 'complete') resolve();
    else window.document.addEventListener('DOMContentLoaded', () => resolve(), { once: true });
  });
  await sleep(300);
  const GB = window.GB;
  const doc = window.document;
  const $ = (s) => doc.querySelector(s);

  const cfg = GB.overlay.config();
  assert('Konfig: View', cfg.view === view, cfg.view);
  assert('Konfig: Kanal', cfg.channel === 'demokanal', cfg.channel);
  assert('Konfig: Keywords', cfg.keywords.join(',') === '!win,!dabei', cfg.keywords.join(','));
  assert('Konfig: Spin/Claim', cfg.spinSeconds === 3 && cfg.claimSeconds === 60);
  assert('Konfig: Akzent', cfg.accent === 'blue' &&
    doc.documentElement.getAttribute('data-accent') === 'blue');
  assert('Richtige Ansicht sichtbar',
    view === 'roulette' ? !$('#rlWrap').classList.contains('hidden') : !$('#wheelWrap').classList.contains('hidden'));
  assert('Andere Ansicht versteckt',
    view === 'roulette' ? $('#wheelWrap').classList.contains('hidden') : $('#rlWrap').classList.contains('hidden'));

  for (let i = 1; i <= 10; i++) {
    GB.pool.add({ login: 'ovuser' + i, display: 'OvUser' + i });
  }
  await sleep(200);
  assert('Zähler zeigt 10', $('#ovCount').textContent === '10', $('#ovCount').textContent);
  if (view === 'roulette') {
    assert('Roulette-Kärtchen da', doc.querySelectorAll('.rl-card').length > 5);
    assert('Keine Seltenheit', doc.querySelectorAll('.rl-tier,.rl-rar,.case-card').length === 0);
  } else {
    assert('Canvas bemasst', $('#wheelCanvas').width > 0);
  }

  $('#ovSpin').click();
  await sleep(400);
  const spinning = view === 'roulette' ? GB.roulette.isSpinning() : GB.wheel.isSpinning();
  assert('Spin läuft', spinning);
  await sleep(3200);
  assert('Gewinner-Banner sichtbar', !$('#ovWinner').classList.contains('hidden'));
  assert('Gewinner-Name gesetzt', $('#ovName').textContent.length > 1, $('#ovName').textContent);
  assert('Preis angezeigt', $('#ovPrize').textContent === 'Testpreis', $('#ovPrize').textContent);
  assert('Claim-Timer läuft', GB.winner.isPending());
  GB.winner.claim('test');
  assert('Claim bestätigt', $('#ovWinStatus').textContent.includes('Bestätigt'));
  assert('Spin-Button wieder frei', $('#ovSpin').disabled === false);
}

(async () => {
  await testView('wheel');
  await testView('roulette');
  console.log(`\nFEHLER GESAMMELT: ${errors.length}`);
  errors.slice(0, 10).forEach((e) => console.log('---', String(e).split('\n').slice(0, 4).join('\n')));
  console.log(`ERGEBNIS OVERLAY: ${pass} bestanden, ${fail} fehlgeschlagen`);
  process.exit(fail || errors.length ? 1 : 0);
})().catch((e) => { console.log('HARNESS-FEHLER:', e.stack); process.exit(1); });

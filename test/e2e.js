// E2E-Laufzeit-Test Hauptapp: echte index.html + echte JS-Module in jsdom,
// Canvas-2D als No-op-Stub, rAF via Timer. Fängt jeden Runtime-Fehler.
// Start: npm test (oder: node test/e2e.js) – braucht: npm install (jsdom)
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const DIR = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(DIR, 'index.html'), 'utf8');

const errors = [];
process.on('unhandledRejection', (e) => errors.push('unhandledRejection: ' + (e && e.stack || e)));

const dom = new JSDOM(html, {
  url: 'http://localhost:8080/',
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
    if (prop === 'getImageData') return () => ({ data: [] });
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
  'roulette', 'winner', 'ui', 'app', 'obs'];
for (const f of files) {
  const code = fs.readFileSync(path.join(DIR, 'js', f + '.js'), 'utf8');
  try {
    window.eval(code + `\n//# sourceURL=${f}.js`);
  } catch (e) {
    console.log(`LOAD-FEHLER in ${f}.js:`, e.stack);
    process.exit(1);
  }
}
console.log('LOAD ok');

// (kein manuelles DOMContentLoaded: jsdom feuert ihn selbst, genau einmal)
if (process.env.PRESET_VIEW) {
  window.localStorage.setItem('gorgiibot.v1', JSON.stringify({ view: process.env.PRESET_VIEW }));
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let pass = 0, fail = 0;
function assert(name, cond, extra) {
  if (cond) { pass++; console.log('  PASS', name); }
  else { fail++; console.log('  FAIL', name, extra || ''); }
}

(async () => {
  await new Promise((resolve) => {
    const rs = window.document.readyState;
    if (rs === 'interactive' || rs === 'complete') resolve();
    else window.document.addEventListener('DOMContentLoaded', () => resolve(), { once: true });
  });
  await sleep(300); // init + erste Frames abwarten
  const GB = window.GB;
  const doc = window.document;
  const $ = (s) => doc.querySelector(s);

  assert('GB Namespace', !!GB && !!GB.app);
  assert('Teilnehmer initial 0', GB.pool.count() === 0);

  // Demo-Teilnehmer per Klick hinzufügen
  $('#demoBtn').click();
  await sleep(200);
  assert('Demo-Button fügt 10 hinzu', GB.pool.count() === 10, 'count=' + GB.pool.count());
  assert('Roster rendert 10 Einträge', doc.querySelectorAll('.roster-item').length === 10);

  // Rad: Canvas hat Größe & rAF-Loop läuft
  const canvas = $('#wheelCanvas');
  assert('Canvas existiert', !!canvas);
  assert('Canvas hat Pixelgröße', canvas.width > 0 && canvas.height > 0, canvas.width + 'x' + canvas.height);
  assert('wheelWrap sichtbar', !$('#wheelWrap').classList.contains('hidden'));

  // Auf Roulette wechseln (echter Klick)
  $('#tabRoulette').click();
  await sleep(200);
  assert('Roulette-Tab aktiv', $('#tabRoulette').classList.contains('active'));
  assert('rlWrap sichtbar', !$('#rlWrap').classList.contains('hidden'));
  assert('wheelWrap versteckt', $('#wheelWrap').classList.contains('hidden'));
  assert('Roulette-Kärtchen gerendert', doc.querySelectorAll('.rl-card').length > 5,
    'cards=' + doc.querySelectorAll('.rl-card').length);
  assert('Keine Seltenheits-Labels', doc.querySelectorAll('.rl-tier,.rl-rar,.case-card').length === 0);
  assert('Kärtchen nur Avatar+Name',
    doc.querySelectorAll('.rl-card .rl-avatar').length > 5 &&
    doc.querySelectorAll('.rl-card .rl-name').length > 5);

  // Zurück zum Rad
  $('#tabWheel').click();
  await sleep(200);
  assert('Rad-Tab wieder aktiv', $('#tabWheel').classList.contains('active'));
  assert('wheelWrap wieder sichtbar', !$('#wheelWrap').classList.contains('hidden'));

  // Spin im Rad-Modus (Dauer kurz stellen)
  GB.store.state.settings.spinSeconds = 3;
  GB.store.state.settings.claimSeconds = 60;
  $('#spinBtn').click();
  await sleep(500);
  assert('Spin läuft', window.GB.wheel.isSpinning());
  await sleep(3200); // Spin beenden lassen
  assert('Gewinner-Modal offen', !$('#winnerModal').classList.contains('hidden'));
  assert('Gewinner gesetzt', !!GB.winner.current(), '');
  assert('Claim-Timer läuft', GB.winner.isPending());
  GB.winner.claim('test');
  assert('Claim bestätigt', !GB.winner.isPending());

  // Roulette-Spin: wechseln + drehen
  $('#tabRoulette').click();
  await sleep(100);
  GB.winner.reset();
  $('#spinBtn').click();
  await sleep(400);
  assert('Roulette-Spin läuft', GB.roulette.isSpinning());
  await sleep(3200);
  assert('Roulette-Gewinner gezogen', !!GB.winner.current());

  // Timeout -> Auto-Reroll (Timer kurz)
  GB.winner.reset();
  GB.winner.start(GB.pool.ordered()[0], { claimSeconds: 10, isReroll: false });
  assert('Claim-Neustart ok', GB.winner.isPending());
  GB.winner.reset();

  // OBS-Dialog
  $('#obsBtn').click();
  await sleep(100);
  const obsUrl = $('#obsUrl').value;
  assert('OBS-Dialog offen', !$('#obsModal').classList.contains('hidden'));
  assert('OBS-URL enthält Overlay + Ansicht', obsUrl.includes('overlay.html') && obsUrl.includes('view='), obsUrl);
  assert('OBS-URL enthält Spin/Claim', obsUrl.includes('spin=') && obsUrl.includes('claim='), obsUrl);
  assert('OBS-URL enthält Akzent', obsUrl.includes('accent='), obsUrl);
  $('#obsClose').click();
  await sleep(50);
  assert('OBS-Dialog schließbar', $('#obsModal').classList.contains('hidden'));

  // Akzentfarbe
  const swBlue = doc.querySelector('.swatch[data-accent="blue"]');
  swBlue.click();
  await sleep(50);
  assert('Akzent gesetzt', doc.documentElement.getAttribute('data-accent') === 'blue');
  assert('Swatch markiert', swBlue.classList.contains('active'));
  assert('Akzent gespeichert', GB.store.state.settings.accent === 'blue');
  const swGreen = doc.querySelector('.swatch[data-accent="green"]');
  swGreen.click();
  await sleep(50);
  assert('Akzent zurückgesetzt', doc.documentElement.getAttribute('data-accent') === 'green');

  console.log(`\nFEHLER GESAMMELT: ${errors.length}`);
  errors.slice(0, 10).forEach((e) => console.log('---', e.split('\n').slice(0, 4).join('\n')));
  console.log(`ERGEBNIS E2E: ${pass} bestanden, ${fail} fehlgeschlagen`);
  process.exit(fail || errors.length ? 1 : 0);
})().catch((e) => { console.log('HARNESS-FEHLER:', e.stack); process.exit(1); });

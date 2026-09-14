# GorgiiBot · Twitch Giveaway System

Interaktives Giveaway-System für Twitch – **Glücksrad** & **CS2 Case-Opening** – als
dezente, GitHub-inspirierte Web-App. Läuft **komplett clientseitig**, ohne Backend,
ohne Build-Schritt, ohne Account.

## Starten (eine Option reicht)

**Option A – Doppelklick:** `index.html` im Browser öffnen. Fertig.

**Option B – lokaler Server** (empfohlen für OBS-Browser-Source):

```bash
cd GorgiiBot
python3 -m http.server 8080
# danach http://localhost:8080 öffnen
```

## Benutzung

1. **Twitch-Kanal verbinden:** Kanalnamen oben eingeben → *Verbinden*.
   Der Chat wird anonym per offiziellem Twitch-WebSocket mitgelesen (nur Lesen,
   der Bot schreibt nichts).
2. **Teilnahme-Modus wählen:**
   - *Per Keyword* (Standard `!giveaway`, mehrere kommagetrennt möglich), oder
   - *Alle Nachrichten* (jede Nachricht = 1 Los, jeder User max. 1×).
3. **Drehen:** *Rad drehen* / *Case öffnen* oder `Leertaste`.
4. **Claim-Timer:** Der Gewinner muss sich innerhalb der eingestellten Zeit
   (Standard 60 s) im Chat melden. Sonst startet automatisch ein **Reroll**
   aus den verbleibenden Teilnehmern.

## Funktionen

- 🎡 **Glücksrad** (Canvas, Ease-Out-Physik, Idle-Rotation, Tick-Sounds)
- 📦 **Case Opening** (Roulette-Leiste mit Marker, Seltenheits-Kärtchen, Endlos-Loop im Idle)
- ⏱️ **Claim-Timer** mit Chat-Erkennung, Bestätigungs-Status & Auto-Reroll
- 👥 **Teilnehmer-Pool:** Duplikat-Filter, Suche, Sortierung, manueller Remove,
  manuelles Hinzufügen, CSV/JSON-Export, JSON-Import
- 🚫 **Banliste** + automatischer Ausschluss bekannter Chat-Bots
- 🔊 **Sound-Engine** (Web Audio, keine Dateien): Tick, Win, Claim, Timeout, Countdown
- 🌓 **Dark/Light Mode** mit Speicherung, alles in `localStorage`
- ⌨️ Kürzel: `Leertaste` drehen · `M` Sound · `T` Theme · `Esc` Dialog schließen
- 🧪 **Demo-Teilnehmer** & Claim-Simulation zum Testen ohne Twitch

## Projektstruktur

```
index.html          – Layout (Header, Steuerung, Bühne, Teilnehmer, Modal)
css/styles.css      – GitHub-Theme via CSS-Variablen (Dark & Light)
js/utils.js         – Helfer (DOM, Zufall, Format, Download)
js/audio.js         – Web-Audio-Sounds (Tick, Win, Claim, Timeout, Beep)
js/store.js         – State + localStorage-Persistenz
js/twitch.js        – Anonymer IRC-WebSocket-Client (nur Lesen)
js/participants.js  – Pool, Avatare, Suche/Sortierung, Ex-/Import
js/wheel.js         – Glücksrad-Engine (Canvas + Idle + Ease-Out-Spin)
js/case.js          – Case-Opening-Engine (Roulette + Idle-Loop)
js/winner.js        – Gewinner-Modal + Claim-Timer + Auto-Reroll
js/ui.js            – Theme, Toasts, Chat-Feed, Log, Statistiken
js/app.js           – Orchestrierung & Event-Verdrahtung
```

## Datenschutz

- Chat wird nur gelesen, nie gesendet. Kein Token, kein Login nötig.
- Einzige Netzverbindungen: Twitch-Chat (`irc-ws.chat.twitch.tv`) und optional
  Profilbilder (`decapi.me`, abschaltbar – Fallback sind Initialen-Avatare).
- Einstellungen & Teilnehmerliste bleiben in deinem Browser (`localStorage`).

## Lizenz

MIT – frei verwenden und anpassen.

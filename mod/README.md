# GorgiiBot Giveaway (Fabric-Mod für 1.21.11)

Twitch-Giveaway-System **direkt in Minecraft**: **Glücksrad** & **Roulette** mit
Twitch-Chat-Anbindung und **Twitch-Profilbildern** im Spiel. Alles läuft lokal
im Client – keine Website, kein Server-Mod, keine Zusatzdienste nötig.

> **Version:** Minecraft **1.21.11** (Fabric Loader ≥ 0.19.5, Yarn
> 1.21.11+build.6, Fabric API 0.141.6+1.21.11). 1.21.11 ist die letzte
> obfuskierte Minecraft-Version – Yarn wird danach nicht mehr aktualisiert,
> die Mod bleibt daher bewusst auf 1.21.11 gepinnt.

## Voraussetzungen

- Minecraft **1.21.11** mit **Fabric Loader ≥ 0.19.5**
- **Fabric API** für 1.21.11 (im `mods`-Ordner)
- **Java 21** zum Bauen (das Spiel bringt sein eigenes Java mit)

## Bauen

```bash
cd mod
./gradlew build        # Linux/macOS
gradlew.bat build      # Windows
```

Die fertige Mod-Datei liegt danach in `mod/build/libs/gorgiibot-1.1.0.jar`.
Beim ersten Bau lädt Gradle automatisch Minecraft, Mappings und Fabric API
herunter (Internet nötig, dauert ein paar Minuten).

Zum Testen direkt aus dem Quellcode: `./gradlew runClient`.

## Installieren

1. Fabric Loader für 1.21.11 installieren (https://fabricmc.net/use/).
2. Fabric API für 1.21.11 in den `mods`-Ordner legen.
3. `gorgiibot-1.1.0.jar` in den `mods`-Ordner legen.
4. Spiel starten – fertig.

## Benutzung

- **G-Taste** oder Befehl **`/giveaway`** öffnet das Giveaway-Fenster
  (funktioniert im Singleplayer und auf Servern, pausiert das Spiel nicht).
  Unterbefehlele: `/giveaway rad`, `/giveaway roulette`,
  `/giveaway teilnehmer`, `/giveaway gesperrt`, `/giveaway einstellungen`.
- Das Fenster ist ein **Desktop-Programm-Fenster** mit Titelleiste,
  Sidebar-Navigation (Icons) und Karten – kein Minecraft-Menü mehr.
  **Tipp für den hochauflösenden Look:** In den Minecraft-Optionen die
  **GUI-Skala auf „Automatisch“ oder 3–4** stellen. Dann wirkt das Fenster
  wie ein echtes Programm (groß, scharf, mit Schatten und abgerundeten Ecken).
- In den **Einstellungen**: Twitch-Kanal und Schlüsselwort (Standard:
  `!teilnahme`) eintragen.
- Auf **Verbinden** klicken – der Mod hört ab sofort anonym den Twitch-Chat
  mit (kein Token/Login nötig).
- Zuschauer nehmen per Schlüsselwort im Chat teil. Bei **Start** wird fair
  per Zufall gezogen (Subs/VIPs/Mods können mit „Sub-Glück“ mehr Lose
  bekommen).
- **Bestätigung wie auf der Website:** Nach der Ziehung läuft ein Countdown
  (Standard **60 s**). Schreibt der Gewinner in dieser Zeit **irgendetwas** im
  Twitch-Chat, gilt der Gewinn als bestätigt (grün + Konfetti). Meldet er sich
  nicht, erfolgt nach kurzer Pause automatisch ein **Reroll**. Der Countdown
  läuft auch weiter, wenn man den Gewinner-Dialog schließt.
- **AFK-Animation:** Glücksrad und Roulette bewegen sich im Ruhezustand ganz
  langsam weiter (0,22 rad/s bzw. 45 px/s) – genau wie in der Web-App. In der
  Roulette sind die Teilnehmer damit **dauerhaft sichtbar**, nicht erst beim
  Drehen.
- **Roulette ohne Vorab-Verrat:** Während des Spins ist die Gewinner-Karte
  **nicht markiert** – der goldene Rahmen erscheint erst nach der Landung
  (und bleibt kurz sichtbar, bevor der Gewinner-Screen öffnet).
- **Alle Profilbilder:** Avatare werden proaktiv für **alle** Teilnehmer
  geladen (Warteschlange, gedrosselt wie die Web-App) – nicht nur für die
  gerade sichtbaren Karten. Bei vielen Teilnehmern dauert es ein paar
  Sekunden, bis alle Bilder da sind; Platzhalter zeigen bis dahin den
  Anfangsbuchstaben.
- **Teilnehmer verwalten** (Sidebar): Liste aller Teilnehmer mit Suche,
  Sortierung (Neueste/Älteste/A–Z/Z–A) und Paginierung. Pro Zeile:
  **Papierkorb = entfernen**, **Verbotsschild = bannen**. Manuell
  hinzufügen, 12 Test-User oder alle leeren ist ebenfalls möglich.
- **Gesperrte User** (Sidebar): Liste aller Banns mit Suche und Sortierung.
  **Haken = entbannen** – der User kann danach wieder per Keyword
  teilnehmen. Gesperrte werden sofort aus dem Pool entfernt und können
  nicht mehr teilnehmen. Die Banliste bleibt über Neustarts erhalten.
- Neu in den Einstellungen: **Bestätigung** (30/45/60/90/120/180 s),
  **Avatare** an/aus, Gewinner **nach Claim** bzw. **nach Timeout**
  automatisch entfernen.
- Zum Ausprobieren ohne Chat gibt es **12 Test-User**.

Die Konfiguration (inkl. Banliste) liegt in `config/gorgiibot.json` und kann
auch per Texteditor geändert werden.

## Hinweise

- Der Mod ist **rein clientseitig** (`environment: client`) und läuft daher
  überall, auch auf Vanilla-Servern.
- Profilbilder werden über den freien Dienst **decapi.me** aufgelöst und
  zwischengespeichert. Ohne Internet im Spiel: farbiger Platzhalter mit
  Anfangsbuchstabe.
- Die Ziehung ist fair: Der Gewinner wird zuerst per Zufall bestimmt, die
  Animation (Rad/Roulette) landet danach exakt auf ihm.
- Sounds sind Vanilla-Sounds (Notenblöcke, Level-up), es werden keine
  eigenen Assets benötigt.

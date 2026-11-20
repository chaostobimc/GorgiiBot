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
- Das Menü sieht aus **wie ein normales Minecraft-Menü**: oben eine Reihe
  Vanilla-Buttons zum Umschalten (Glücksrad, Roulette, Teilnehmer,
  Gesperrt, Einstellungen), Standard-Schrift, flache Listen mit
  Hover-Highlight und Trennlinien – keine eigene „Programm-Optik“.
  **Tipp:** In den Minecraft-Optionen die **GUI-Skala** passend einstellen
  (z. B. 3–4) – dann ist alles angenehm groß, aber weiter komplett im
  Minecraft-Stil.
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
- **Teilnehmer** (Button oben): Liste aller Teilnehmer mit Avatar, Name,
  „[SUB]“-Markierung, Login und Beitrittszeit, dazu Suche, Sortierung
  (Neueste/Älteste/A–Z/Z–A) und Blättern (`<` / `>`). Pro Zeile:
  **X = entfernen**, **Bann = bannen**. Unten: manuell hinzufügen,
  Test-User anlegen, alle leeren.
- **Gesperrt** (Button oben): Liste aller Banns mit Suche, Sortierung und
  Blättern. **Frei = entbannen** – der User kann danach wieder per Keyword
  teilnehmen. Gesperrte fliegen sofort aus dem Pool, können nicht mehr
  teilnehmen und bleiben über Neustarts erhalten.
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

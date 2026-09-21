# GorgiiBot Giveaway (Fabric-Mod für 1.21.11)

Twitch-Giveaway-System **direkt in Minecraft**: **Glücksrad** & **Roulette** mit
Twitch-Chat-Anbindung und **Twitch-Profilbildern** im Spiel. Alles läuft lokal
im Client – keine Website, kein Server-Mod, keine Zusatzdienste nötig.

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

Die fertige Mod-Datei liegt danach in `mod/build/libs/gorgiibot-1.0.0.jar`.
Beim ersten Bau lädt Gradle automatisch Minecraft, Mappings und Fabric API
herunter (Internet nötig, dauert ein paar Minuten).

Zum Testen direkt aus dem Quellcode: `./gradlew runClient`.

## Installieren

1. Fabric Loader für 1.21.11 installieren (https://fabricmc.net/use/).
2. Fabric API für 1.21.11 in den `mods`-Ordner legen.
3. `gorgiibot-1.0.0.jar` in den `mods`-Ordner legen.
4. Spiel starten – fertig.

## Benutzung

- **G-Taste** oder Befehl **`/giveaway`** öffnet das Giveaway-Menü
  (funktioniert im Singleplayer und auf Servern, pausiert das Spiel nicht).
- In den **Einstellungen**: Twitch-Kanal und Schlüsselwort (Standard:
  `!teilnahme`) eintragen.
- Auf **Verbinden** klicken – der Mod hört ab sofort anonym den Twitch-Chat
  mit (kein Token/Login nötig).
- Zuschauer nehmen per Schlüsselwort im Chat teil. Bei **Start** wird fair
  per Zufall gezogen (Subs/VIPs/Mods können mit „Sub-Glück“ mehr Lose
  bekommen).
- Zum Ausprobieren ohne Chat gibt es in den Einstellungen
  **Test-Teilnehmer**.

Die Konfiguration liegt in `config/gorgiibot.json` und kann auch per
Texteditor geändert werden.

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

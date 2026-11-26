# Mytic Friends-Server

Freundesliste für den Mytic Client: Anfragen, online/zuletzt online, Server und Skin-Kopf.
Derselbe Code läuft unter Node (eigener Server) und als Cloudflare Worker.

## Anmeldung

Mojang sperrt Anfragen von Cloudflare-Servern. Deshalb fragt der Server Mojang nie selbst:
Der Launcher holt sein Spieler-Zertifikat und sein signiertes Profil bei Mojang und beweist mit dem
privaten Schlüssel des Zertifikats, dass ihm das Konto gehört (`src/mojang.js`). Der Server prüft
das mit Mojangs öffentlichen Schlüsseln (`src/mojang-keys.js`, wird beim Veröffentlichen aktualisiert).
Das Minecraft-Token verlässt den PC nur in Richtung Mojang.

## Cloudflare

    CLOUDFLARE_API_TOKEN=… CLOUDFLARE_ACCOUNT_ID=… node deploy.mjs

Benötigt ein Token mit „Workers Scripts: Bearbeiten“. Die Daten liegen in einem Durable Object mit
SQLite. Das Token nie ins Repository schreiben.

## Node (z. B. für die Entwicklerversion mit Offline-Konten)

    node src/node-server.js --port 8766 --db friends.db --offline

## Tests

    npm test

## Anfragen und Kapazität (Cloudflare Free: 100 000 Anfragen/Tag)

Jede Anfrage zählt, deshalb bündelt die Mod alles Regelmäßige in **ein** Lebenszeichen (`POST /api/presence`):
Spielzeit (`play`), offene Einladungen zu Partien (`games.invites`), eigene Freischaltungen (`owned`) und den
versteckten Status (`hidden`). Server mit diesen Feldern melden `caps: ["presence2"]` in jeder Freundesliste; ältere
Mods ignorieren das, neuere Mods fragen bei älteren Servern wie früher einzeln. **Reihenfolge:** erst den Server
veröffentlichen, dann Mods/Launcher.

| je spielendem Nutzer und Stunde | vorher | jetzt |
|---|---|---|
| Lebenszeichen der Mod (60 s, bei Statuswechsel sofort; 30 s, solange ein Freund im Spiel ist und zu einer Partie einladen kann) | 60 | 60 (–120) |
| Launcher: Server für Discord (jetzt lokale Statusdatei der Mod; nur alte Mods: alle 2 min) | 120 | 0 |
| Launcher: Freundesliste im Hintergrund (jetzt nur bei offener Freunde-Seite) | 60 | 0 |
| Partien ohne laufende Partie (Einladungen kommen jetzt mit dem Lebenszeichen) | 720 | 0 |
| Spielzeit (`api/stats/play`) | 60 | 0 (im Lebenszeichen) |
| Freischaltungen (`api/cosmetics/owned`) | 20 | 0 (im Lebenszeichen) |
| Cosmetics anderer Spieler (gesammelt; mit `cos1` alle 10 bzw. 15 min + sofort bei gemeldeter Änderung, sonst 3 bzw. 6 min) | ~30 | ~6 (ältere Server: ~10) |
| **Summe** | **≈ 1 070** (1.8.9: ≈ 290) | **≈ 70** (mit Freund im Spiel ≈ 130) |

Dazu kommen Anfragen nur bei Bedarf (laufende Partie ~40/min, Fenster öffnen, Aktionen) und 60/h für einen offenen
Launcher ohne Spiel („im Launcher“). Online gilt man bis 150 s nach dem letzten Lebenszeichen (`ONLINE_MS`), also auch
bei einem verpassten Lebenszeichen.

**Kapazität:** 100 000 / ≈ 70 ≈ **1 400 Spielstunden pro Tag**, z. B. rund 700 Nutzer mit je 2 Stunden
(vorher ≈ 90 Spielstunden, also ≈ 45 Nutzer).

## Cosmetics anderer Spieler (`cos1`)

Damit andere eine neue Auswahl (z. B. die Weihnachtsmütze) nach höchstens einem Lebenszeichen (≤ 60 s) sehen statt
nach 3–6 Minuten, ohne öfter abzufragen:

- `POST /api/cosmetics` und `POST /api/admin/grants` setzen `users.cos_at` (nur bei echter Änderung; die Mod schickt
  ihre Auswahl bei jedem Spielstart).
- Das Lebenszeichen der Mod schickt `cosSince` (Stand aus dem letzten Lebenszeichen, anfangs 0) und bekommt
  `cos: { since, changed: [uuid, …] }` (höchstens 200, Stand älter als 1 h = Neustart ohne Liste); `caps` enthält `"cos1"`.
  Die Mod fragt dann nur geänderte Spieler in ihrer Nähe neu ab (`api/cosmetics/lookup`) und alle anderen nur noch
  alle 10 (mit Cosmetics) bzw. 15 Minuten (ohne).
- `POST /api/cosmetics/lookup` nimmt zusätzlich `names: { uuid: Name }` für Spieler mit Offline-UUID (Version 3,
  Server im Offline-Modus): Passt die UUID zum Namen, gilt das Microsoft-Konto mit diesem Namen; die Antwort steht
  unter der angefragten UUID, `aliases: { angefragt: Konto }` ordnet Änderungsmeldungen zu.

## Einstellungs-Sync (`GET`/`PUT /api/sync`)

Der Launcher synchronisiert die Mytic-Einstellungen (Mods, HUD, Profile, Fadenkreuz, Chat, Cosmetics-Auswahl) je Konto:

- `GET /api/sync?have=<hash>` → `{ version, hash, updatedAt, device, data }`; ist `have` schon der aktuelle Stand, kommt
  `{ …, unchanged: true }` ohne Daten.
- `PUT /api/sync` mit `{ data: Base64(gzip(JSON)), hash: SHA-256 des JSON, ifVersion?, force?, device? }` → `{ version, hash, updatedAt }`.
  Höchstens 256 KB gzip. Gleicher Hash schreibt nichts (`unchanged`). Mit `ifVersion` und inzwischen neuerem Stand kommt
  `{ conflict: true, …, data }` (Status 200) zurück – der Launcher sichert den Stand des Servers lokal und lädt mit `force: true` hoch.
- Das Lebenszeichen (`POST /api/presence`) bringt `sync: { version, hash, updatedAt }` mit, `caps` enthält `"sync1"`.
  Ältere Clients ignorieren beides; `/api/settings` (alte Cloud-Sync der Mod) bleibt unverändert.

Anfragen: höchstens 1 `GET` je Spielstart (0, wenn der Stand aus einem Lebenszeichen schon bekannt ist), 1 `PUT` je
geänderter Einstellung (frühestens 30 s nach der letzten Änderung, gesammelt) und 1 beim Beenden, falls sich etwas geändert
hat – typisch 1–3 Anfragen je Spielsitzung, nichts im Takt.

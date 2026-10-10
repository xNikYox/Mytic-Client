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

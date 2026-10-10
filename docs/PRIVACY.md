# Datenschutzerklärung – Mytic Client

*Stand: 8. Oktober 2026*

Der Mytic Client ist ein kostenloser Launcher für Minecraft: Java Edition. Er läuft vollständig auf deinem Computer. Es gibt keine eigenen Server des Mytic Client, und es werden keine Daten an den Entwickler gesendet.

## Welche Daten verarbeitet werden

| Daten | Wofür | Wo gespeichert |
|---|---|---|
| Microsoft-Anmeldung (Zugriffs- und Aktualisierungstoken) | Anmeldung bei Xbox Live und Minecraft | nur lokal auf deinem PC, verschlüsselt mit der Betriebssystem-Verschlüsselung (Windows DPAPI bzw. Schlüsselbund) |
| Minecraft-Name und UUID | Anzeige im Launcher, Spielstart | nur lokal (`%APPDATA%\.myticclient\accounts.json`) |
| Einstellungen (RAM, Mods, HUD-Positionen) | deine Einstellungen merken | nur lokal |
| Profile anderer Launcher (Modrinth App, NoRiskClient, Lunar Client) | nur wenn du „Importieren“ öffnest: Version und Mods werden gelesen und in ein neues Mytic-Profil kopiert | nur lokal; die Originale bleiben unverändert |

## Mit welchen Diensten der Launcher spricht

- **Microsoft / Xbox Live / Mojang** (`login.microsoftonline.com`, `*.xboxlive.com`, `api.minecraftservices.com`): Anmeldung und Prüfung, ob du Minecraft besitzt. Es gelten die Datenschutzbestimmungen von Microsoft.
- **Mojang** (`piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`, `launchermeta.mojang.com`): Download von Minecraft, Bibliotheken, Spieldateien und Java.
- **Fabric** (`meta.fabricmc.net`, `maven.fabricmc.net`): Download des Fabric Loaders.
- **Modrinth** (`api.modrinth.com`, `cdn.modrinth.com`): Download der Performance-Mods und der Mods aus dem Mod-Browser. Beim Import werden die SHA-1-Prüfsummen der Mod-Dateien an Modrinth geschickt, um die Mods wiederzuerkennen (keine Dateien, keine persönlichen Daten).
- **Fabric**, **Forge** (`maven.minecraftforge.net`): Loader für die gewählte Version.
- **GitHub** (`api.github.com`, `github.com`): Prüfung auf neue Versionen des Launchers und der Mytic-Mods sowie deren Download. Dabei werden keine persönlichen Daten übertragen.
- **mc-heads.net**: Anzeige deines Spieler-Kopfes im Launcher. Dabei wird nur deine öffentliche Minecraft-UUID übertragen.

Deine Anmeldedaten werden nur an Microsoft und Mojang gesendet und an niemanden sonst. Es gibt keine Werbung, kein Tracking und keine Analyse-Tools.

## Daten löschen

Über den Launcher (Konto → Entfernen) entfernst du ein Konto. Alle Daten verschwinden, wenn du den Ordner `%APPDATA%\.myticclient` löschst. Den Zugriff der App auf dein Microsoft-Konto kannst du jederzeit unter <https://account.live.com/consent/Manage> widerrufen.

## Kontakt

Fragen kannst du über die Issues im GitHub-Repository stellen: <https://github.com/xNikYox/Mytic-Client/issues>

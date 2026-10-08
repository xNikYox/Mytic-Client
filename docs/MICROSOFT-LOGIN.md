# Microsoft-Login für den Mytic Client einrichten

Damit sich Spieler mit ihrem echten Minecraft-Konto anmelden können, braucht der Launcher eine eigene **Client-ID** von Microsoft. Die Einrichtung kostet nichts, ein Azure-Abo ist nicht nötig.

## 1. App bei Microsoft registrieren (ca. 5 Minuten)

1. Öffne <https://entra.microsoft.com> (oder <https://portal.azure.com>) und melde dich mit einem Microsoft-Konto an. Das kann dein normales Konto sein.
   - Microsoft verlangt dabei manchmal eine Telefonnummer oder Kreditkarte zur Identitätsprüfung. Für die App-Registrierung wird nichts abgebucht.
2. Gehe zu **Identität → Anwendungen → App-Registrierungen → Neue Registrierung**. Im Azure-Portal heißt der Weg **Microsoft Entra ID → App-Registrierungen**.
3. Fülle das Formular so aus:
   - **Name:** `Mytic Client`
   - **Unterstützte Kontotypen:** *Nur persönliche Microsoft-Konten*
   - **Umleitungs-URI:** Plattform *Öffentlicher Client/nativ (mobil und Desktop)*, Adresse `https://login.microsoftonline.com/common/oauth2/nativeclient`
4. Klicke auf **Registrieren**.
5. Kopiere auf der Übersichtsseite die **Anwendungs-ID (Client)**. Das ist deine Client-ID, sie sieht so aus: `1a2b3c4d-…`.
6. Öffne **Authentifizierung**, setze *Öffentliche Clientflows zulassen* auf **Ja** und klicke auf **Speichern**.

Weitere Berechtigungen musst du nicht eintragen. Der Launcher fragt `XboxLive.signin` beim Anmelden selbst an.

## 2. Freischaltung bei Mojang beantragen

Mojang blockiert neue Client-IDs für den Minecraft-Login, bis sie geprüft wurden. Solange die Prüfung läuft, zeigt der Launcher die Meldung *„Minecraft hat die Anmeldung abgelehnt“*.

1. Öffne das Antragsformular von Mojang/Microsoft für neue App-IDs: <https://aka.ms/mce-reviewappid>.
   - Falls der Link nicht mehr funktioniert, suche nach „Minecraft AppID review“.
2. Trage dort ein:
   - die Client-ID,
   - den Namen *Mytic Client*,
   - eine kurze Beschreibung, z. B. *„Launcher für Minecraft: Java Edition mit Fabric und Komfort-Mods (FPS, Keystrokes, Zoom) für den Server MyticLegacy“*,
   - deine Kontakt-E-Mail.
3. Warte auf die Freigabe. Das kann einige Tage bis wenige Wochen dauern.

## 3. Client-ID im Launcher eintragen

Dafür gibt es zwei Wege:

- **Für dich allein:** Öffne den Launcher, gehe zu **Einstellungen → Microsoft Client-ID**, füge die ID ein und klicke dann unten links auf das Konto → **Mit Microsoft anmelden**.
- **Für alle Spieler:** Trage die ID vor dem Bauen in `launcher/resources/config.json` ein:

  ```json
  { "microsoftClientId": "deine-client-id" }
  ```

  Danach baust du den Installer neu (`npm run dist:win`). Jeder Spieler hat die ID dann automatisch.

Bis die Freigabe da ist, kannst du mit einem **Offline-Konto** testen. Das funktioniert nur auf Offline- und eigenen Testservern.

## Häufige Fehler

| Meldung | Lösung |
|---|---|
| Minecraft hat die Anmeldung abgelehnt | Die Client-ID ist noch nicht von Mojang freigeschaltet (Schritt 2). |
| Dieses Konto besitzt Minecraft: Java Edition nicht | Mit dem Konto anmelden, das Minecraft gekauft hat. |
| Microsoft-Konto hat noch kein Xbox-Profil | Einmal auf xbox.com anmelden und ein Profil anlegen. |
| Kinderkonto | Ein Erwachsener muss das Konto zu einer Microsoft-Familie hinzufügen. |
| `AADSTS700016` / Anwendung nicht gefunden | Client-ID falsch kopiert oder Kontotyp nicht „persönliche Microsoft-Konten“. |
| `AADSTS7000218` | *Öffentliche Clientflows zulassen* ist nicht auf **Ja** gesetzt (Schritt 1.6). |

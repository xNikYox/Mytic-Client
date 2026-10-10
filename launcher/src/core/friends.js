// Verbindung zum Friends-Server (siehe friends-server/). Anmeldung ohne das Minecraft-Token weiterzugeben: Der Launcher
// holt bei Mojang sein Spieler-Zertifikat und sein signiertes Profil und beweist mit dem privaten Schlüssel des
// Zertifikats, dass ihm das Konto gehört. Der Server prüft das mit Mojangs öffentlichen Schlüsseln.
// Offline-Konten (Entwicklerversion) melden sich nur mit dem Namen an.
const crypto = require("node:crypto");

const CERTIFICATES_URL = "https://api.minecraftservices.com/player/certificates";
const PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

/** PEM von Mojang ("RSA PRIVATE/PUBLIC KEY", tatsächlich PKCS#8/X.509) → DER. */
function pemToDer(pem) {
  return Buffer.from(String(pem).replace(/-----[^-]+-----/g, "").replace(/\s+/g, ""), "base64");
}

class FriendsError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

class FriendsClient {
  /** base: Adresse des Friends-Servers (leer = nicht eingerichtet). */
  constructor({ base, fetchImpl = (...args) => fetch(...args), log = () => {} }) {
    this.base = base ? (base.endsWith("/") ? base : `${base}/`) : "";
    this.fetch = fetchImpl;
    this.log = log;
    this.session = null; // { uuid, token }
  }

  get configured() {
    return Boolean(this.base);
  }

  async request(method, path, data, token) {
    if (!this.base) throw new FriendsError(0, "Freunde sind noch nicht eingerichtet");
    let response;
    try {
      response = await this.fetch(new URL(path, this.base).toString(), {
        method,
        headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
        body: data ? JSON.stringify(data) : undefined,
        signal: AbortSignal.timeout(10000),
      });
    } catch {
      throw new FriendsError(0, "Friends-Server nicht erreichbar");
    }
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new FriendsError(response.status, body.error || `Fehler ${response.status}`);
    return body;
  }

  /** Meldet ein Konto beim Friends-Server an (gibt das Token zurück, zwischengespeichert pro Konto). */
  async login(account) {
    if (this.session && this.session.uuid === account.uuid) return this.session.token;
    let result;
    if (account.type === "offline") {
      result = await this.request("POST", "api/auth/offline", { name: account.name });
    } else {
      const { serverId } = await this.request("POST", "api/auth/challenge");
      result = await this.request("POST", "api/auth/login", await this.proof(account, serverId));
    }
    this.session = { uuid: account.uuid, token: result.token };
    return result.token;
  }

  async mojang(url, options) {
    let response;
    try {
      response = await this.fetch(url, { ...options, signal: AbortSignal.timeout(10000) });
    } catch {
      throw new FriendsError(0, "Mojang nicht erreichbar");
    }
    if (!response.ok) throw new FriendsError(response.status === 401 ? 401 : 0, `Mojang-Anmeldung fehlgeschlagen (${response.status})`);
    return response.json();
  }

  /** Nachweis für den Friends-Server: Zertifikat, Signatur der Aufgabe, signiertes Profil. */
  async proof(account, serverId) {
    const uuid = account.uuid.replace(/-/g, "").toLowerCase();
    if (!this.certificate || this.certificate.uuid !== uuid || Date.parse(this.certificate.data.refreshedAfter) < Date.now()) {
      const data = await this.mojang(CERTIFICATES_URL, { method: "POST", headers: { authorization: `Bearer ${account.accessToken}` } });
      this.certificate = { uuid, data };
    }
    const cert = this.certificate.data;
    const privateKey = crypto.createPrivateKey({ key: pemToDer(cert.keyPair.privateKey), format: "der", type: "pkcs8" });
    const signature = crypto.sign("sha256", Buffer.from(`mytic-friends:${serverId}:${uuid}`), privateKey);
    const profile = await this.mojang(`${PROFILE_URL}${uuid}?unsigned=false`, {});
    const textures = (profile.properties || []).find((p) => p.name === "textures");
    if (!textures || !textures.signature) throw new FriendsError(0, "Mojang-Profil ohne Signatur");
    return {
      serverId,
      uuid,
      publicKey: pemToDer(cert.keyPair.publicKey).toString("base64"),
      keySignature: cert.publicKeySignatureV2,
      expiresAt: cert.expiresAt,
      signature: signature.toString("base64"),
      textures: { value: textures.value, signature: textures.signature },
    };
  }

  /** Aufruf mit Anmeldung; bei abgelaufener Sitzung einmal neu anmelden. */
  async call(account, method, path, data) {
    const token = await this.login(account);
    try {
      return await this.request(method, path, data, token);
    } catch (error) {
      if (error.status !== 401) throw error;
      this.session = null;
      return this.request(method, path, data, await this.login(account));
    }
  }

  logout() {
    this.session = null;
  }
}

module.exports = { FriendsClient, FriendsError };

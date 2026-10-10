// Anmeldung ohne Anfrage bei Mojang (Mojang sperrt Cloudflare-Server aus): Der Launcher schickt
//  1. sein Spieler-Zertifikat (api.minecraftservices.com/player/certificates) – von Mojang signiert, gehört zur UUID,
//  2. eine Signatur der Anmelde-Aufgabe mit dem privaten Schlüssel dieses Zertifikats – beweist den Besitz,
//  3. sein signiertes Profil (sessionserver …/profile/<uuid>?unsigned=false) – Mojang bestätigt Name und Skin.
// Geprüft wird alles mit Mojangs öffentlichen Schlüsseln (mojang-keys.js, wird von deploy.mjs aktualisiert).

function base64ToBytes(text) {
  const clean = String(text).replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const binary = atob(clean);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function verifyRsa(publicKeyDer, hash, signature, data) {
  try {
    const key = await crypto.subtle.importKey("spki", publicKeyDer, { name: "RSASSA-PKCS1-v1_5", hash }, false, ["verify"]);
    return await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, signature, data);
  } catch {
    return false;
  }
}

async function verifyAny(keys, hash, signature, data) {
  for (const key of keys) {
    if (await verifyRsa(base64ToBytes(key), hash, signature, data)) return true;
  }
  return false;
}

/** Zeitpunkt wie Javas Instant.toEpochMilli() (Nachkommastellen nach 3 Stellen abgeschnitten). */
export function epochMillis(iso) {
  const m = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?Z$/.exec(String(iso));
  if (!m) return NaN;
  return Date.parse(`${m[1]}Z`) + Number(((m[2] || "") + "000").slice(0, 3));
}

/** Von Mojang signierte Nutzdaten des Zertifikats: UUID (16 Byte), Ablauf (8 Byte), öffentlicher Schlüssel (X.509). */
export function certificatePayload(uuid, expiresAtMillis, publicKeyDer) {
  const out = new Uint8Array(24 + publicKeyDer.length);
  const view = new DataView(out.buffer);
  const hex = uuid.replace(/-/g, "");
  view.setBigUint64(0, BigInt(`0x${hex.slice(0, 16)}`));
  view.setBigUint64(8, BigInt(`0x${hex.slice(16)}`));
  view.setBigInt64(16, BigInt(expiresAtMillis));
  out.set(publicKeyDer, 24);
  return out;
}

export function challengeText(serverId, uuid) {
  return new TextEncoder().encode(`mytic-friends:${serverId}:${uuid}`);
}

/**
 * Prüft eine Anmeldung. Gibt { uuid, name, skinUrl } zurück oder wirft einen Fehlertext.
 * proof: { uuid, publicKey, keySignature, expiresAt, signature, textures: { value, signature } }
 */
export async function verifyLogin(proof, serverId, keys, now) {
  const uuid = String(proof.uuid || "").replace(/-/g, "").toLowerCase();
  if (!/^[0-9a-f]{32}$/.test(uuid)) throw "Ungültige UUID";
  const expires = epochMillis(proof.expiresAt);
  if (!(expires > now)) throw "Zertifikat abgelaufen";
  const publicKey = base64ToBytes(proof.publicKey || "");
  if (!(await verifyAny(keys.playerCertificateKeys, "SHA-1", base64ToBytes(proof.keySignature || ""), certificatePayload(uuid, expires, publicKey)))) {
    throw "Zertifikat nicht von Mojang";
  }
  if (!(await verifyRsa(publicKey, "SHA-256", base64ToBytes(proof.signature || ""), challengeText(serverId, uuid)))) throw "Signatur ungültig";

  const textures = proof.textures || {};
  if (!(await verifyAny(keys.profilePropertyKeys, "SHA-1", base64ToBytes(textures.signature || ""), new TextEncoder().encode(String(textures.value || ""))))) {
    throw "Profil nicht von Mojang bestätigt";
  }
  const profile = JSON.parse(new TextDecoder().decode(base64ToBytes(textures.value)));
  if (String(profile.profileId).toLowerCase() !== uuid) throw "Profil gehört zu einem anderen Konto";
  if (!(profile.timestamp > now - 6 * 60 * 60 * 1000)) throw "Profil zu alt";
  const skin = profile.textures && profile.textures.SKIN && profile.textures.SKIN.url;
  return { uuid, name: String(profile.profileName), skinUrl: skin || null };
}

// „Freunden beitreten“: Ist die Server-Adresse eines Freundes für andere erreichbar?
// Nur öffentliche Adressen (Domain oder öffentliche IP) – Einzelspieler, LAN, Realms und private Netze nicht.
// Gleiche Regeln wie in der Mod (friends/JoinTarget.java) und im Launcher.

/** Platzhalter, die die Mod statt einer Adresse schickt */
export const NOT_ADDRESSES = new Set(["einzelspieler", "lan-welt", "realms", "privater server"]);

/** IPv4 als Zahlenfeld oder null. */
function ipv4(host) {
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(host);
  if (!m) return null;
  const parts = m.slice(1).map(Number);
  return parts.every((p) => p <= 255) ? parts : null;
}

/** Private, lokale oder sonst nicht öffentliche IPv4-Adresse? */
function privateV4([a, b]) {
  return a === 0 || a === 10 || a === 127 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31)
    || (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127) || a >= 224;
}

/**
 * Host und Port aus "host", "host:port" oder "[v6]:port" (null = keine gültige Adresse).
 * Gibt { host (klein), port } zurück.
 */
export function parseAddress(server) {
  if (typeof server !== "string") return null;
  const text = server.trim().toLowerCase();
  if (!text || text.length > 64 || /\s|\/|@/.test(text)) return null;
  let host = text, port = 25565;
  const v6 = /^\[([0-9a-f:.]+)\](?::(\d{1,5}))?$/.exec(text);
  if (v6) {
    host = v6[1];
    if (v6[2]) port = Number(v6[2]);
  } else if ((text.match(/:/g) || []).length === 1) {
    const [h, p] = text.split(":");
    if (!/^\d{1,5}$/.test(p)) return null;
    host = h;
    port = Number(p);
  } else if (text.includes(":")) {
    host = text; // IPv6 ohne Klammern
  }
  if (!host || port < 1 || port > 65535) return null;
  return { host, port };
}

/** Darf ein Freund dieser Adresse beitreten (öffentlich erreichbar)? */
export function joinable(server) {
  if (typeof server !== "string" || NOT_ADDRESSES.has(server.trim().toLowerCase())) return false;
  const addr = parseAddress(server);
  if (!addr) return false;
  const { host } = addr;
  if (host.includes(":")) {
    // IPv6: Loopback, link-lokal (fe80::/10), eindeutig lokal (fc00::/7), unspezifiziert
    return !(host === "::1" || host === "::" || /^fe[89ab]/.test(host) || /^f[cd]/.test(host));
  }
  const v4 = ipv4(host);
  if (v4) return !privateV4(v4);
  if (!/^[a-z0-9.-]+$/.test(host) || !host.includes(".") || host.startsWith(".") || host.endsWith(".") || host.includes("..")) return false;
  // Namen nur im eigenen Netz
  return !/(^|\.)(localhost|local|lan|home|internal|intranet|corp|private|home\.arpa)$/.test(host);
}

/**
 * Was Freunde als Server sehen: öffentliche Adressen bleiben, private Adressen werden zu „Privater Server“
 * (ältere Mods schicken sie noch mit). Platzhalter wie „Einzelspieler“ bleiben unverändert.
 */
export function shownServer(server) {
  if (typeof server !== "string" || !server.trim()) return null;
  if (NOT_ADDRESSES.has(server.trim().toLowerCase())) return server.trim();
  if (joinable(server)) return server.trim();
  return parseAddress(server) ? "Privater Server" : server.trim();
}

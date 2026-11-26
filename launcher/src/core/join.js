// „Freunden beitreten“ im Launcher: Darf mit der Server-Adresse eines Freundes gestartet werden? Gleiche Regeln wie im
// Friends-Server (friends-server/src/join.js) und in der Mod (friends/JoinTarget.java): nur öffentliche Adressen.

const NOT_ADDRESSES = new Set(["einzelspieler", "lan-welt", "realms", "privater server"]);

function parseAddress(server) {
  if (typeof server !== "string") return null;
  const text = server.trim().toLowerCase();
  if (!text || text.length > 64 || /\s|\/|@/.test(text)) return null;
  let host = text;
  let port = 25565;
  const v6 = /^\[([0-9a-f:.]+)\](?::(\d{1,5}))?$/.exec(text);
  if (v6) {
    host = v6[1];
    if (v6[2]) port = Number(v6[2]);
  } else if ((text.match(/:/g) || []).length === 1) {
    const [h, p] = text.split(":");
    if (!/^\d{1,5}$/.test(p)) return null;
    host = h;
    port = Number(p);
  }
  if (!host || port < 1 || port > 65535) return null;
  return { host, port };
}

function privateV4(host) {
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(host);
  if (!m) return null;
  const [a, b, c, d] = m.slice(1).map(Number);
  if ([a, b, c, d].some((n) => n > 255)) return true; // ungültig = nicht beitreten
  return a === 0 || a === 10 || a === 127 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31)
    || (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127) || a >= 224;
}

/** Öffentlich erreichbare Server-Adresse? (Einzelspieler, LAN, Realms und private Netze nicht) */
function joinable(server) {
  if (typeof server !== "string" || NOT_ADDRESSES.has(server.trim().toLowerCase())) return false;
  const addr = parseAddress(server);
  if (!addr) return false;
  const { host } = addr;
  if (host.includes(":")) return !(host === "::1" || host === "::" || /^fe[89ab]/.test(host) || /^f[cd]/.test(host));
  const v4 = privateV4(host);
  if (v4 !== null) return !v4;
  if (!/^[a-z0-9.-]+$/.test(host) || !host.includes(".") || host.startsWith(".") || host.endsWith(".") || host.includes("..")) return false;
  return !/(^|\.)(localhost|local|lan|home|internal|intranet|corp|private|home\.arpa)$/.test(host);
}

module.exports = { joinable, parseAddress };

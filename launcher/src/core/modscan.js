// Mod-Malware-Scanner: prüft Mods (auch verschachtelte Jars) auf bekannte Schadsoftware und typische Merkmale von
// Datendieben (Discord-/Minecraft-/Browser-Logins abgreifen und verschicken, Code nachladen, Autostart).
// Liest nur. Ergebnis pro Datei: ok | hinweis | verdaechtig | malware, mit Begründung.
const AdmZip = require("adm-zip");
const crypto = require("node:crypto");
const fsp = require("node:fs/promises");
const path = require("node:path");

const MAX_CLASS_BYTES = 96 * 1024 * 1024; // insgesamt pro Mod
const MAX_DEPTH = 2; // Jar in Jar in Jar

/**
 * Regeln: kind = known (bekannte Malware), exfil (Daten verschicken), steal (Logins/Tokens abgreifen),
 * exec (Programme starten), persist (Autostart), load (Code aus dem Internet nachladen), session (Minecraft-Login lesen).
 * test bekommt den Text einer Klasse (Konstanten als Latin-1).
 */
const RULES = [
  // ---- bekannte Malware
  { id: "fractureiser", kind: "known", label: "Fractureiser (bekannte Minecraft-Malware, 2023)", test: (t) => /dev\/neko\/neko(client|injector)|85\.217\.144\.130|files-8ie\.pages\.dev/.test(t) },
  { id: "skyrage", kind: "known", label: "Skyrage (bekannte Minecraft-Malware)", test: (t) => /skyrage\.de/i.test(t) },
  // ---- Daten verschicken
  { id: "webhook", kind: "exfil", label: "Discord-Webhook (verschickt Daten an einen Discord-Kanal)", test: (t) => /(discord|discordapp|canary\.discord|ptb\.discord)\.com\/api\/webhooks\//i.test(t) },
  { id: "telegram", kind: "exfil", label: "Telegram-Bot (verschickt Daten an Telegram)", test: (t) => /api\.telegram\.org\/bot/i.test(t) },
  // ---- Logins und Tokens abgreifen
  { id: "discord-token", kind: "steal", label: "Liest Discord-Logins (Token-Dieb)", test: (t) => /dQw4w9WgXcQ|discord(canary|ptb)?\\\\?Local Storage|Local Storage\\\\?leveldb|\[\\w-\]\{24\}\\\.\[\\w-\]\{6\}|mfa\\\.\[\\w-\]\{84\}/i.test(t) },
  { id: "browser", kind: "steal", label: "Liest gespeicherte Browser-Passwörter oder Cookies", test: (t) => /CryptUnprotectData/.test(t) || (/Login Data|Cookies/.test(t) && /User Data/.test(t)) || (/Local State/.test(t) && /encrypted_key/.test(t)) },
  { id: "launcher-accounts", kind: "steal", label: "Liest Konten anderer Launcher (Minecraft-Logins)", test: (t) => /launcher_accounts(_microsoft_store)?\.json|microsoft_accounts\.json|\.lunarclient[\\/]settings[\\/]game[\\/]accounts\.json|\.feather[\\/]accounts\.json|tlauncher_profiles\.json/i.test(t) },
  { id: "wallet", kind: "steal", label: "Sucht nach Krypto-Wallets", test: (t) => /Exodus[\\/]exodus\.wallet|Electrum[\\/]wallets|wallet\.dat|MetaMask|nkbihfbeogaeaoehlefnkodbefgpgknn/i.test(t) },
  // ---- Minecraft-Login der laufenden Sitzung
  { id: "session", kind: "session", label: "Liest den Login-Token der laufenden Minecraft-Sitzung", test: (t) => /getAccessToken|func_148254_d|method_1674|getSessionToken/.test(t) && /net\/minecraft\/(client\/User|class_320|util\/Session)/.test(t) },
  // ---- Programme starten, Autostart, nachladen
  { id: "exec", kind: "exec", label: "Startet Windows-Befehle oder Skripte", test: (t) => /java\/lang\/(ProcessBuilder|Runtime)/.test(t) && /powershell|cmd\.exe|cmd \/c|wscript|cscript|mshta|rundll32|regsvr32|bitsadmin|certutil/i.test(t) },
  { id: "persist", kind: "persist", label: "Trägt sich in den Autostart ein", test: (t) => /Start Menu\\\\?Programs\\\\?Startup|CurrentVersion\\\\?Run|schtasks/i.test(t) },
  { id: "load", kind: "load", label: "Lädt Programmcode aus dem Internet nach", test: (t) => /java\/net\/URLClassLoader|defineClass/.test(t) && /https?:\/\/(?!(www\.)?(github\.com|raw\.githubusercontent\.com|maven\.|repo\.|cdn\.modrinth\.com|api\.modrinth\.com|mojang\.com|minecraft\.net))/i.test(t) },
];

const KIND_LABEL = { known: "Bekannte Malware", exfil: "Daten verschicken", steal: "Logins abgreifen", session: "Minecraft-Login", exec: "Befehle ausführen", persist: "Autostart", load: "Code nachladen" };

/** Bewertung aus den Treffern (Kombinationen zählen: abgreifen UND verschicken = Datendieb) */
function verdict(hits) {
  const kinds = new Set(hits.map((h) => h.kind));
  const has = (k) => kinds.has(k);
  if (has("known")) return "malware";
  if (has("exfil") && (has("steal") || has("session"))) return "malware";
  if (has("steal") && (has("exec") || has("persist") || has("load"))) return "malware";
  if (has("steal") || has("exfil") || has("persist") || (has("exec") && has("load"))) return "verdaechtig";
  if (has("exec") || has("load") || has("session")) return "hinweis";
  return "ok";
}

/** Klassen einer Jar (und verschachtelter Jars) prüfen → Treffer [{ id, kind, label, where }] */
function scanZip(buffer, name, depth = 0, budget = { bytes: 0 }) {
  const hits = [];
  let zip;
  try {
    zip = new AdmZip(buffer);
  } catch {
    return hits;
  }
  for (const entry of zip.getEntries()) {
    if (entry.isDirectory) continue;
    const n = entry.entryName;
    if (n.endsWith(".jar") && depth < MAX_DEPTH) {
      try {
        hits.push(...scanZip(entry.getData(), `${name} › ${path.basename(n)}`, depth + 1, budget));
      } catch {
        // kaputte innere Jar
      }
      continue;
    }
    if (!n.endsWith(".class")) continue;
    if (budget.bytes > MAX_CLASS_BYTES) break;
    let data;
    try {
      data = entry.getData();
    } catch {
      continue;
    }
    budget.bytes += data.length;
    const text = data.toString("latin1");
    for (const rule of RULES) {
      if (rule.test(text) && !hits.some((h) => h.id === rule.id)) hits.push({ id: rule.id, kind: rule.kind, label: rule.label, where: `${name}: ${n.replace(/\.class$/, "").replace(/\//g, ".")}` });
    }
  }
  return hits;
}

/** Eine Mod-Datei prüfen */
async function scanFile(file) {
  const buffer = await fsp.readFile(file);
  const sha1 = crypto.createHash("sha1").update(buffer).digest("hex");
  const hits = scanZip(buffer, path.basename(file));
  return { file, name: path.basename(file), sha1, verdict: verdict(hits), hits };
}

/**
 * Alle Mods eines Ordners prüfen (Jar und .jar.disabled). skip(name) überspringt z. B. vom Launcher selbst geladene,
 * geprüfte Mods; cache (sha1 → Ergebnis) vermeidet erneutes Prüfen unveränderter Dateien.
 */
async function scanFolder(dir, { skip = () => false, cache = {} } = {}) {
  let names = [];
  try {
    names = (await fsp.readdir(dir)).filter((n) => /\.jar(\.disabled)?$/i.test(n));
  } catch {
    return { results: [], cache };
  }
  const results = [];
  for (const name of names) {
    if (skip(name)) continue;
    const file = path.join(dir, name);
    try {
      const buffer = await fsp.readFile(file);
      const sha1 = crypto.createHash("sha1").update(buffer).digest("hex");
      let r = cache[sha1];
      if (!r) {
        const hits = scanZip(buffer, name);
        r = { verdict: verdict(hits), hits };
        cache[sha1] = r;
      }
      results.push({ file, name, sha1, disabled: name.endsWith(".disabled"), ...r });
    } catch {
      // Datei gerade nicht lesbar
    }
  }
  const order = { malware: 0, verdaechtig: 1, hinweis: 2, ok: 3 };
  results.sort((a, b) => order[a.verdict] - order[b.verdict] || a.name.localeCompare(b.name));
  return { results, cache };
}

module.exports = { scanZip, scanFile, scanFolder, verdict, RULES, KIND_LABEL };

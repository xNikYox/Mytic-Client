// Friends-Server als Cloudflare Worker veröffentlichen – direkt über die Cloudflare-API (ohne wrangler).
// Aufruf: CLOUDFLARE_API_TOKEN=… CLOUDFLARE_ACCOUNT_ID=… node deploy.mjs
// Lädt den Worker samt Durable Object (SQLite) hoch und schaltet die workers.dev-Adresse frei.
// Benötigte Token-Rechte: Workers Scripts (Bearbeiten). Das Token wird nirgends gespeichert.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const TOKEN = process.env.CLOUDFLARE_API_TOKEN;
const ACCOUNT = process.env.CLOUDFLARE_ACCOUNT_ID;
const NAME = process.env.WORKER_NAME || "mytic-friends";
if (!TOKEN || !ACCOUNT) throw new Error("CLOUDFLARE_API_TOKEN und CLOUDFLARE_ACCOUNT_ID setzen");

const api = async (method, url, body, headers = {}) => {
  const response = await fetch(`https://api.cloudflare.com/client/v4/accounts/${ACCOUNT}${url}`, {
    method,
    headers: { authorization: `Bearer ${TOKEN}`, ...(body && !(body instanceof FormData) ? { "content-type": "application/json" } : {}), ...headers },
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  const data = await response.json().catch(() => ({}));
  if (!data.success) throw new Error(`${method} ${url}: ${JSON.stringify(data.errors || data)}`);
  return data.result;
};

// 0. Mojangs öffentliche Schlüssel aktualisieren (Cloudflare selbst darf Mojang nicht abfragen)
const keys = await (await fetch("https://api.minecraftservices.com/publickeys")).json();
const pick = (list) => (list || []).map((k) => k.publicKey);
if (!pick(keys.playerCertificateKeys).length || !pick(keys.profilePropertyKeys).length) throw new Error("Mojang-Schlüssel nicht geladen");
fs.writeFileSync(path.join(here, "src", "mojang-keys.js"), `// Öffentliche Schlüssel von Mojang (api.minecraftservices.com/publickeys) – wird von deploy.mjs aktualisiert.\nexport const MOJANG_KEYS = ${JSON.stringify({ playerCertificateKeys: pick(keys.playerCertificateKeys), profilePropertyKeys: pick(keys.profilePropertyKeys) }, null, 2)};\n`);
console.log("Mojang-Schlüssel aktualisiert");

// 1. Gibt es den Worker schon? (Die Durable-Object-Migration darf nur beim ersten Mal mitgeschickt werden.)
const exists = (await api("GET", "/workers/scripts")).some((script) => script.id === NAME);

// 1b. workers.dev-Adresse des Kontos (muss vor dem ersten Hochladen existieren)
let subdomain;
try {
  subdomain = (await api("GET", "/workers/subdomain")).subdomain;
} catch {
  subdomain = null;
}
if (!subdomain) {
  subdomain = (await api("PUT", "/workers/subdomain", { subdomain: process.env.WORKERS_SUBDOMAIN || `mytic-${ACCOUNT.slice(0, 6)}` })).subdomain;
}
// 2. Worker hochladen (ES-Module, ohne Bündeln)
const form = new FormData();
form.append("metadata", new Blob([JSON.stringify({
  main_module: "worker.js",
  compatibility_date: "2026-09-01",
  compatibility_flags: ["nodejs_compat"],
  bindings: [{ type: "durable_object_namespace", name: "FRIENDS", class_name: "FriendsStore" }],
  ...(exists ? {} : { migrations: { new_tag: "v1", new_sqlite_classes: ["FriendsStore"] } }),
})], { type: "application/json" }));
for (const file of ["worker.js", "api.js", "skin.js", "schema.js", "mojang.js", "mojang-keys.js"]) {
  form.append(file, new Blob([fs.readFileSync(path.join(here, "src", file))], { type: "application/javascript+module" }), file);
}
await api("PUT", `/workers/scripts/${NAME}`, form);
console.log(`Worker ${NAME} hochgeladen`);

// 3. Worker unter workers.dev erreichbar machen
await api("POST", `/workers/scripts/${NAME}/subdomain`, { enabled: true, previews_enabled: false });
console.log(`URL: https://${NAME}.${subdomain}.workers.dev/`);

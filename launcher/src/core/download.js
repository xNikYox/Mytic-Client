// Downloads mit Prüfsumme, Wiederholungen und paralleler Abarbeitung.
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");

const USER_AGENT = "MyticClient/1.0 (+https://myticlegacy.de)";

async function fetchRetry(url, options = {}, tries = 4) {
  let lastError;
  for (let attempt = 1; attempt <= tries; attempt++) {
    try {
      const response = await fetch(url, { ...options, headers: { "User-Agent": USER_AGENT, ...(options.headers || {}) } });
      if (response.status >= 500 || response.status === 429) throw new Error(`HTTP ${response.status} bei ${url}`);
      return response;
    } catch (error) {
      lastError = error;
      if (attempt < tries) await new Promise((r) => setTimeout(r, 400 * attempt * attempt));
    }
  }
  throw lastError;
}

async function getJson(url, options) {
  const response = await fetchRetry(url, options);
  if (!response.ok) throw new Error(`HTTP ${response.status} bei ${url}`);
  return response.json();
}

async function sha1File(file) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash("sha1");
    fs.createReadStream(file).on("data", (d) => hash.update(d)).on("error", reject).on("end", () => resolve(hash.digest("hex")));
  });
}

/** Ist die Datei schon vorhanden und korrekt? Ohne sha1 genügt die Größe (bzw. die bloße Existenz). */
async function isValid(file, { sha1, size, checkHash = true } = {}) {
  let stat;
  try {
    stat = await fsp.stat(file);
  } catch {
    return false;
  }
  if (size != null && stat.size !== size) return false;
  if (sha1 && checkHash) return (await sha1File(file)) === sha1;
  return true;
}

/** Lädt eine Datei herunter (über eine .part-Datei) und prüft sha1, falls angegeben. */
async function downloadFile(url, file, { sha1, size } = {}) {
  await fsp.mkdir(path.dirname(file), { recursive: true });
  for (let attempt = 1; attempt <= 3; attempt++) {
    const response = await fetchRetry(url);
    if (!response.ok) throw new Error(`HTTP ${response.status} bei ${url}`);
    const data = Buffer.from(await response.arrayBuffer());
    if (sha1 && crypto.createHash("sha1").update(data).digest("hex") !== sha1) {
      if (attempt === 3) throw new Error(`Prüfsumme falsch: ${url}`);
      continue;
    }
    if (size != null && data.length !== size && !sha1) throw new Error(`Größe falsch: ${url}`);
    const part = `${file}.part`;
    await fsp.writeFile(part, data);
    await fsp.rename(part, file);
    return data.length;
  }
}

/**
 * Arbeitet eine Liste von Downloads parallel ab.
 * items: [{ url, file, sha1?, size?, checkHash? }]; onProgress(done, total, bytes)
 */
async function downloadAll(items, { concurrency = 16, onProgress } = {}) {
  let done = 0;
  let bytes = 0;
  let index = 0;
  const total = items.length;
  const worker = async () => {
    while (index < items.length) {
      const item = items[index++];
      if (!(await isValid(item.file, item))) bytes += await downloadFile(item.url, item.file, item);
      done++;
      if (onProgress) onProgress(done, total, bytes);
    }
  };
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, worker));
  return { total, bytes };
}

module.exports = { fetchRetry, getJson, downloadFile, downloadAll, isValid, sha1File, USER_AGENT };

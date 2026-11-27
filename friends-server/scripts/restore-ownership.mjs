#!/usr/bin/env node
// Stellt den Besitz (Freischaltungen und Cosmetics-Auswahl) aus der Sicherung wieder her, die „Besitz zurücksetzen“
// auf der Admin-Seite angelegt hat (Tabellen grants_backup und cos_backup). Gekauftes bleibt erhalten, Coins werden nicht
// verändert; ausgewählte Cosmetics werden nur dort zurückgeholt, wo gerade nichts gewählt ist.
//
//   Node-Server (eigene Datenbank-Datei):
//     node scripts/restore-ownership.mjs --db friends.db            → zeigt nur, was passieren würde
//     node scripts/restore-ownership.mjs --db friends.db --yes      → stellt wieder her
//     (--batch reset-… wählt eine bestimmte Sicherung, sonst die des Zurücksetzens)
//
//   Cloudflare (Durable Object, kein direkter Datenbankzugriff) – über die Admin-API in zwei Schritten:
//     MYTIC_ADMIN_KEY=… node scripts/restore-ownership.mjs --url https://<worker>/ [--yes]
//   Dasselbe geht auf der Admin-Seite (/admin → „Besitz wiederherstellen“).
import { fileURLToPath } from "node:url";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";
import { SCHEMA, MIGRATIONS } from "../src/schema.js";
import { runBatch } from "../src/node-server.js";
import { RESET_ID, RESTORE_WORD, restoreStatements } from "../src/coins.js";

/** Sicherung in einer lokalen Datenbank prüfen (apply = false) bzw. einspielen. */
export function restoreLocal(file, { batch = null, apply = false, now = Date.now() } = {}) {
  const sqlite = new DatabaseSync(file);
  try {
    sqlite.exec(SCHEMA);
    for (const sql of MIGRATIONS) {
      try {
        sqlite.exec(sql);
      } catch {
        // schon vorhanden
      }
    }
    const reset = sqlite.prepare("SELECT * FROM migrations WHERE id = ?").get(RESET_ID);
    const id = batch || (reset && reset.batch);
    if (!id) throw new Error("Keine Sicherung gefunden (Besitz wurde nicht zurückgesetzt)");
    const grants = Number(sqlite.prepare("SELECT COUNT(*) AS n FROM grants_backup WHERE batch = ?").get(id).n);
    const missing = Number(sqlite.prepare("SELECT COUNT(*) AS n FROM grants_backup b WHERE b.batch = ? AND NOT EXISTS (SELECT 1 FROM grants g WHERE g.uuid = b.uuid AND g.item = b.item)").get(id).n);
    const users = Number(sqlite.prepare("SELECT COUNT(DISTINCT uuid) AS n FROM grants_backup WHERE batch = ?").get(id).n);
    if (!grants) throw new Error(`Sicherung ${id} ist leer oder unbekannt`);
    const summary = { batch: id, grants, missing, users, applied: false };
    if (apply) {
      runBatch(sqlite, restoreStatements(id, now));
      summary.applied = true;
    }
    return summary;
  } finally {
    sqlite.close();
  }
}

async function restoreRemote(base, key, apply) {
  const call = async (data) => {
    const res = await fetch(new URL("api/admin/restore-ownership", base.endsWith("/") ? base : `${base}/`), {
      method: "POST", headers: { "content-type": "application/json", "x-admin-key": key }, body: JSON.stringify(data),
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(body.error || `HTTP ${res.status}`);
    return body;
  };
  const step = await call({});
  console.log(`Sicherung ${step.summary.batch}: ${step.summary.items} Freischaltungen${step.summary.restored ? " (schon einmal wiederhergestellt)" : ""}`);
  if (!apply) return console.log("Nur angezeigt – mit --yes wiederherstellen.");
  const done = await call({ token: step.token, confirm: RESTORE_WORD });
  console.log(done.restored ? "Wiederhergestellt." : JSON.stringify(done));
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  const arg = (name) => {
    const i = process.argv.indexOf(name);
    return i > 0 ? process.argv[i + 1] : null;
  };
  const apply = process.argv.includes("--yes");
  try {
    if (arg("--db")) {
      const s = restoreLocal(arg("--db"), { batch: arg("--batch"), apply });
      console.log(`Sicherung ${s.batch}: ${s.grants} Freischaltungen von ${s.users} Spielern, davon ${s.missing} derzeit fehlend.`);
      console.log(s.applied ? "Wiederhergestellt." : "Nur angezeigt – mit --yes wiederherstellen.");
    } else if (arg("--url")) {
      const key = process.env.MYTIC_ADMIN_KEY || "";
      if (!key) throw new Error("MYTIC_ADMIN_KEY setzen (Admin-Passwort, wird nicht ausgegeben)");
      await restoreRemote(arg("--url"), key, apply);
    } else {
      console.log("Aufruf: --db <datei> [--batch <id>] [--yes]  oder  --url <server> [--yes] (mit MYTIC_ADMIN_KEY)");
      process.exitCode = 2;
    }
  } catch (error) {
    console.error(`Fehler: ${error.message}`);
    process.exitCode = 1;
  }
}

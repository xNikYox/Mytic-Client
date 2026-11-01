// Spielstatistik: Hilfen für den Start-Eintrag beim Friends-Server und die JVM-Argumente der Mod.

const UMLAUTS = { ä: "ae", ö: "oe", ü: "ue", Ä: "Ae", Ö: "Oe", Ü: "Ue", ß: "ss" };

/** Profilname für -Dmytic.profile und api/stats/launch: nur [\w .-], 1–40 Zeichen (Umlaute umgeschrieben). */
function statsProfileName(name) {
  const clean = String(name || "")
    .replace(/[äöüÄÖÜß]/g, (c) => UMLAUTS[c])
    .normalize("NFKD")
    .replace(/[^\w .-]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 40)
    .trim();
  return clean || "Standard";
}

/** Zeitzone als Minuten-Abstand zu UTC (positiv = östlich), wie vom Friends-Server erwartet. */
function tzOffset(date = new Date()) {
  return -date.getTimezoneOffset();
}

module.exports = { statsProfileName, tzOffset };

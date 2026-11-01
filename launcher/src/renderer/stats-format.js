// Formatierung für die Statistik-Seite (auch in den Tests unter Node nutzbar).
(function (root) {
  /** Sekunden → "12 Std 5 Min" / "45 Min" / "3 Std" ("0 Min" ohne Spielzeit). */
  function formatDuration(seconds) {
    const total = Math.round(Math.max(0, Number(seconds) || 0) / 60);
    const hours = Math.floor(total / 60);
    const minutes = total % 60;
    if (!hours) return `${minutes} Min`;
    return minutes ? `${hours} Std ${minutes} Min` : `${hours} Std`;
  }

  /** Zeitpunkt vom Server (Millisekunden, ältere Angaben ggf. in Sekunden) → Millisekunden oder null. */
  function toMillis(time) {
    const n = Number(time);
    if (!n || !Number.isFinite(n)) return null;
    return n < 1e12 ? n * 1000 : n;
  }

  /** "heute, 14:32" / "gestern, 09:10" / "12.10.2026" */
  function formatLastPlayed(time, now = Date.now()) {
    const ms = toMillis(time);
    if (!ms) return "noch nie";
    const date = new Date(ms);
    const today = new Date(now);
    today.setHours(0, 0, 0, 0);
    const clock = date.toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" });
    if (ms >= today.getTime()) return `heute, ${clock}`;
    if (ms >= today.getTime() - 86400000) return `gestern, ${clock}`;
    return date.toLocaleDateString("de-DE", { day: "2-digit", month: "2-digit", year: "numeric" });
  }

  /** "2026-10-31" → "Sa., 31.10." */
  function formatDay(day) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(day || ""));
    if (!m) return String(day || "");
    return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])).toLocaleDateString("de-DE", { weekday: "short", day: "2-digit", month: "2-digit" });
  }

  /** Heutiger Tag als "YYYY-MM-DD" in lokaler Zeit. */
  function localDay(now = Date.now()) {
    const d = new Date(now);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  const api = { formatDuration, formatLastPlayed, formatDay, localDay, toMillis };
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.statsFormat = api;
})(typeof window === "object" ? window : globalThis);

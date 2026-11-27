// Admin-Seite des Friends-Servers (/admin): Cosmetics freischalten, Coins geben/nehmen, Preise, Besitz zurücksetzen.
// Anmeldung mit dem Admin-Passwort (ADMIN_KEY); es bleibt nur im Browser-Tab (sessionStorage).
export const ADMIN_PAGE = `<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex">
<title>Mytic Admin</title>
<style>
:root { color-scheme: dark; --bg:#07050f; --panel:#120c24; --line:#2a2048; --text:#efeaf9; --muted:#a39bbd; --cyan:#22e5ff; --violet:#9b5cff; --pink:#ff2bd6; --ok:#3ddc84; --bad:#ff5a6e; }
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font: 15px/1.5 "Segoe UI", system-ui, sans-serif; padding: 24px 16px 60px; }
main { max-width: 1040px; margin: 0 auto; display: grid; gap: 18px; }
h1 { margin: 0; font-size: 26px; letter-spacing: .06em; }
h1 span { background: linear-gradient(100deg, var(--cyan), var(--violet), var(--pink)); -webkit-background-clip: text; background-clip: text; color: transparent; }
.card { background: var(--panel); border: 1px solid var(--line); border-radius: 14px; padding: 18px; display: grid; gap: 12px; }
.row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
input[type=text], input[type=password] { flex: 1 1 220px; min-width: 0; font: inherit; color: var(--text); background: #0b0818; border: 1px solid var(--line); border-radius: 9px; padding: 10px 12px; }
input:focus { outline: none; border-color: var(--violet); }
button { font: 600 14px inherit; font-family: inherit; color: #0a0614; background: linear-gradient(100deg, var(--cyan), var(--violet) 55%, var(--pink)); border: 0; border-radius: 9px; padding: 10px 16px; cursor: pointer; }
button.ghost { background: transparent; color: var(--text); box-shadow: inset 0 0 0 1px var(--line); }
button:disabled { opacity: .5; cursor: default; }
.muted { color: var(--muted); font-size: 13px; }
.msg { font-size: 14px; } .msg.ok { color: var(--ok); } .msg.bad { color: var(--bad); }
.cat { border-top: 1px solid var(--line); padding-top: 12px; display: grid; gap: 8px; }
.cat h3 { margin: 0; font-size: 15px; display: flex; gap: 12px; align-items: center; }
.items { display: flex; flex-wrap: wrap; gap: 6px; }
label.chip { display: inline-flex; gap: 6px; align-items: center; padding: 5px 10px; border-radius: 999px; border: 1px solid var(--line); background: #0b0818; cursor: pointer; font-size: 13px; user-select: none; }
label.chip:has(input:checked) { border-color: var(--violet); background: rgba(155, 92, 255, .18); }
label.chip input { accent-color: var(--violet); margin: 0; }
label.chip.all { border-style: dashed; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
td, th { text-align: left; padding: 8px 6px; border-bottom: 1px solid var(--line); vertical-align: top; }
th { color: var(--muted); font-weight: 500; }
td.items-col { color: var(--muted); font-size: 12px; }
a.pick { color: var(--cyan); cursor: pointer; text-decoration: underline; }
input[type=number] { width: 110px; font: inherit; color: var(--text); background: #0b0818; border: 1px solid var(--line); border-radius: 9px; padding: 8px 10px; }
input.price { width: 90px; padding: 5px 8px; }
input.price.over { border-color: var(--violet); }
.coins { font-size: 22px; font-weight: 700; } .coins small { font-size: 13px; color: var(--muted); font-weight: 400; }
.pos { color: var(--ok); } .neg { color: var(--bad); }
button.small { padding: 4px 10px; font-size: 12px; }
button.danger { background: var(--bad); color: #fff; }
.scroll { max-height: 420px; overflow: auto; }
[hidden] { display: none !important; }
</style>
</head>
<body>
<main>
  <h1><span>MYTIC</span> Admin · Cosmetics &amp; Coins</h1>

  <section class="card" id="login">
    <strong>Anmelden</strong>
    <div class="row"><input type="password" id="key" placeholder="Admin-Passwort" autocomplete="current-password"><button id="loginBtn">Anmelden</button></div>
    <div class="msg" id="loginMsg"></div>
  </section>

  <section class="card" id="app" hidden>
    <strong>Spieler</strong>
    <div class="row"><input type="text" id="name" placeholder="Minecraft-Name oder UUID" spellcheck="false"><button id="loadBtn">Laden</button><button class="ghost" id="logoutBtn">Abmelden</button></div>
    <div class="msg" id="msg"></div>
    <div id="editor" hidden>
      <div class="row"><strong id="who"></strong><span class="muted" id="uuid"></span></div>
      <div class="cat"><h3>Allgemein</h3><div class="items" id="general"></div></div>
      <div class="cat" id="setsBox" hidden><h3>Sets <span class="muted">(markiert alle Teile eines Sets – danach Speichern)</span></h3><div class="items" id="sets"></div></div>
      <div id="cats"></div>
      <div class="row" style="margin-top:6px"><button id="saveBtn">Speichern</button><button class="ghost" id="noneBtn">Alles entfernen</button></div>
      <div class="cat"><h3>Coins</h3>
        <div class="row"><span class="coins" id="balance">–</span><span class="muted" id="coinInfo"></span></div>
        <div class="row"><input type="number" id="coinAmount" min="1" step="1" placeholder="Menge"><input type="text" id="coinReason" placeholder="Grund (sieht der Spieler)" maxlength="120"><button id="coinGive">+ Geben</button><button class="ghost" id="coinTake">− Nehmen</button></div>
        <div class="msg" id="coinMsg"></div>
        <div class="scroll"><table><thead><tr><th>Zeit</th><th>Art</th><th>Coins</th><th>Stand</th><th>Details</th><th></th></tr></thead><tbody id="coinTx"></tbody></table></div>
      </div>
    </div>
  </section>

  <section class="card" id="priceCard" hidden>
    <div class="row"><strong>Preise &amp; Sets</strong><span class="muted">Leeres Feld = Standardpreis. Event-Sets sind immer kaufbar.</span></div>
    <div class="row"><label>Set-Rabatt <input type="number" id="discount" min="0" max="90" step="1"> %</label><span class="muted" id="discountInfo"></span><button id="priceSave">Preise speichern</button></div>
    <div class="msg" id="priceMsg"></div>
    <div id="setList" class="muted"></div>
    <div class="scroll"><table><thead><tr><th>Teil</th><th>Art</th><th>Standard</th><th>Preis</th></tr></thead><tbody id="priceList"></tbody></table></div>
  </section>

  <section class="card" id="resetCard" hidden>
    <strong>Besitz zurücksetzen</strong>
    <div class="muted">Einmalig: sichert zuerst ALLE Freischaltungen und Auswahlen (Tabellen grants_backup/cos_backup) und nimmt dann allen Spielern ohne Admin-Recht ihre Freischaltungen – alle müssen neu kaufen. Coins bleiben. Wiederherstellen geht hier oder mit <code>scripts/restore-ownership.mjs</code>.</div>
    <div class="row"><span id="resetState" class="muted"></span></div>
    <div class="row"><button class="danger" id="resetBtn">Besitz zurücksetzen …</button><button class="ghost" id="restoreBtn" hidden>Besitz wiederherstellen …</button></div>
    <div class="msg" id="resetMsg"></div>
  </section>

  <section class="card" id="listCard" hidden>
    <div class="row"><strong>Spieler mit Freischaltungen</strong><button class="ghost" id="reloadBtn">Aktualisieren</button></div>
    <table><thead><tr><th>Spieler</th><th>Freigeschaltet</th></tr></thead><tbody id="list"></tbody></table>
  </section>
</main>
<script>
(function () {
  var NAMES = { cape: "Umhänge", wings: "Flügel", shield: "Schilde", aura: "Auren", acc: "Kopf & Hals", nametag: "Nametag-Vorlagen", emote: "Emotes" };
  var KIND_NAMES = { cape: "Umhang", wings: "Flügel", shield: "Schild", aura: "Aura", acc: "Kopf & Hals", minime: "MiniMe", nametag: "Nametag" };
  var TX_NAMES = { grant: "Gutschrift", take: "Abzug", purchase: "Kauf", daily: "Tagesbonus", gift_in: "Geschenk erhalten", gift_out: "Geschenk gesendet", refund: "Erstattung" };
  var GENERAL = [["*", "Alles"], ["admin", "Admin"], ["minime", "MiniMe"], ["nametag", "Nametag (freier Text + Vorlagen)"]];
  var key = "", catalog = null, current = null;
  try { key = sessionStorage.getItem("mytic-admin-key") || ""; } catch (e) {}
  var $ = function (id) { return document.getElementById(id); };

  function call(method, path, data) {
    return fetch("api/" + path, { method: method, headers: { "content-type": "application/json", "x-admin-key": key }, body: data ? JSON.stringify(data) : undefined })
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (j) { if (!r.ok) throw new Error(j.error || ("Fehler " + r.status)); return j; }); });
  }
  function say(el, text, ok) { el.textContent = text || ""; el.className = "msg " + (ok ? "ok" : "bad"); }

  function login() {
    call("GET", "admin/catalog").then(function (j) {
      catalog = j.catalog;
      try { sessionStorage.setItem("mytic-admin-key", key); } catch (e) {}
      $("login").hidden = true; $("app").hidden = false; $("listCard").hidden = false; $("priceCard").hidden = false; $("resetCard").hidden = false;
      loadList(); loadPrices();
    }).catch(function (e) { say($("loginMsg"), e.message === "Keine Admin-Rechte" ? "Falsches Passwort" : e.message); });
  }
  // beim Kopieren mitgekommene Zeichen (Leerzeichen, Anführungszeichen, Backticks, Sternchen) entfernen
  $("loginBtn").onclick = function () { key = $("key").value.replace(/[\\s"'\u0060*]/g, ""); login(); };
  $("key").onkeydown = function (e) { if (e.key === "Enter") $("loginBtn").click(); };
  $("logoutBtn").onclick = function () { key = ""; try { sessionStorage.removeItem("mytic-admin-key"); } catch (e) {} location.reload(); };

  function chip(value, label, checked, cls) {
    var l = document.createElement("label"); l.className = "chip" + (cls ? " " + cls : "");
    var i = document.createElement("input"); i.type = "checkbox"; i.value = value; i.checked = checked;
    l.appendChild(i); l.appendChild(document.createTextNode(label)); return l;
  }
  function render(user) {
    current = user;
    var has = new Set(user.items);
    $("editor").hidden = false;
    $("who").textContent = user.name || "(unbekannt)";
    $("uuid").textContent = user.uuid;
    var g = $("general"); g.innerHTML = "";
    GENERAL.forEach(function (x) { g.appendChild(chip(x[0], x[1], has.has(x[0]), x[0] === "*" ? "all" : "")); });
    var sets = (catalog && catalog.sets) || [], sb = $("sets"); sb.innerHTML = "";
    $("setsBox").hidden = !sets.length;
    sets.forEach(function (set) {
      var b = document.createElement("button"); b.type = "button"; b.className = "ghost";
      b.textContent = set.name + " (" + set.items.length + ")";
      b.onclick = function () {
        var want = new Set(set.items);
        document.querySelectorAll("#editor input[type=checkbox]").forEach(function (i) { if (want.has(i.value)) i.checked = true; });
      };
      sb.appendChild(b);
    });
    var c = $("cats"); c.innerHTML = "";
    Object.keys(NAMES).forEach(function (kind) {
      if (!catalog[kind]) return; // älterer Katalog ohne diese Art
      var box = document.createElement("div"); box.className = "cat";
      var h = document.createElement("h3"); h.textContent = NAMES[kind] + " (" + catalog[kind].length + ")";
      if (kind !== "nametag") h.appendChild(chip(kind + ":*", "alle " + NAMES[kind], has.has(kind + ":*"), "all"));
      var items = document.createElement("div"); items.className = "items";
      catalog[kind].forEach(function (it) { items.appendChild(chip(kind + ":" + it[0], it[1], has.has(kind + ":" + it[0]))); });
      box.appendChild(h); box.appendChild(items); c.appendChild(box);
    });
  }
  function load(name) {
    say($("msg"), "");
    call("POST", "admin/user", { name: name }).then(function (u) { render(u); loadCoins(u.uuid); }).catch(function (e) { say($("msg"), e.message); });
  }

  // ---------------------------------------------------------------- Coins
  function fmt(t) { var d = new Date(t); return d.toLocaleDateString("de-DE") + " " + d.toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" }); }
  function renderCoins(v) {
    $("balance").innerHTML = ""; $("balance").appendChild(document.createTextNode(v.balance + " ")); var sm = document.createElement("small"); sm.textContent = "Coins"; $("balance").appendChild(sm);
    $("coinInfo").textContent = "Serie: " + (v.streak || 0) + " Tag(e)" + (v.dailyDay ? " · zuletzt Bonus am " + v.dailyDay : "") + " · heute verschenkt: " + v.giftedToday;
    var tb = $("coinTx"); tb.innerHTML = "";
    if (!v.tx.length) { tb.innerHTML = '<tr><td colspan="6" class="muted">Noch keine Buchungen.</td></tr>'; return; }
    v.tx.forEach(function (t) {
      var tr = document.createElement("tr");
      var details = [t.reason, t.other, t.ref, t.admin ? "von " + t.admin : null].filter(Boolean).join(" · ");
      [fmt(t.at), TX_NAMES[t.kind] || t.kind, (t.delta > 0 ? "+" : "") + t.delta, String(t.balance), details].forEach(function (text, i) {
        var td = document.createElement("td"); td.textContent = text; if (i === 2) td.className = t.delta >= 0 ? "pos" : "neg"; tr.appendChild(td);
      });
      var td = document.createElement("td");
      if (t.kind === "purchase") {
        var b = document.createElement("button"); b.className = "ghost small"; b.textContent = "Erstatten";
        b.onclick = function () {
          if (!confirm("Kauf erstatten? " + (-t.delta) + " Coins zurück, die gekauften Teile werden wieder entfernt.")) return;
          call("POST", "admin/refund", { tx: t.id }).then(function (nv) { renderCoins(nv); say($("coinMsg"), "Erstattet", true); load(current.uuid); }).catch(function (e) { say($("coinMsg"), e.message); });
        };
        td.appendChild(b);
      }
      tr.appendChild(td); tb.appendChild(tr);
    });
  }
  function loadCoins(uuid) { call("GET", "admin/coins/" + uuid).then(renderCoins).catch(function (e) { say($("coinMsg"), e.message); }); }
  function book(sign) {
    if (!current) return;
    var n = parseInt($("coinAmount").value, 10);
    if (!(n > 0)) { say($("coinMsg"), "Menge eingeben"); return; }
    call("POST", "admin/coins", { player: current.uuid, delta: sign * n, reason: $("coinReason").value.trim() || null }).then(function (v) {
      renderCoins(v); say($("coinMsg"), (v.changed > 0 ? "+" : "") + v.changed + " Coins gebucht", true); $("coinAmount").value = "";
    }).catch(function (e) { say($("coinMsg"), e.message); });
  }
  $("coinGive").onclick = function () { book(1); };
  $("coinTake").onclick = function () { book(-1); };

  // ---------------------------------------------------------------- Preise
  var prices = null;
  function loadPrices() { call("GET", "admin/prices").then(renderPrices).catch(function (e) { say($("priceMsg"), e.message); }); }
  function renderPrices(p) {
    prices = p;
    $("discount").value = p.discount;
    $("discountInfo").textContent = "Standard: " + p.defaultDiscount + " %";
    $("setList").textContent = p.sets.map(function (s) { return s.name + ": " + s.items.length + " Teile, einzeln " + s.sum + " → Set " + s.price + " Coins"; }).join(" · ");
    var tb = $("priceList"); tb.innerHTML = "";
    p.items.forEach(function (it) {
      var tr = document.createElement("tr");
      var td1 = document.createElement("td"); td1.textContent = it.name + (it.event ? " (" + it.event + ")" : ""); tr.appendChild(td1);
      var td2 = document.createElement("td"); td2.className = "muted"; td2.textContent = KIND_NAMES[it.kind] || it.kind; tr.appendChild(td2);
      var td3 = document.createElement("td"); td3.className = "muted"; td3.textContent = it.base; tr.appendChild(td3);
      var td4 = document.createElement("td"); var i = document.createElement("input"); i.type = "number"; i.min = "0"; i.step = "10"; i.className = "price" + (it.override ? " over" : "");
      i.value = it.override ? it.price : ""; i.placeholder = String(it.base); i.dataset.item = it.item; i.dataset.was = i.value; td4.appendChild(i); tr.appendChild(td4);
      tb.appendChild(tr);
    });
    renderReset(p.reset);
  }
  $("priceSave").onclick = function () {
    if (!prices) return;
    var change = {};
    document.querySelectorAll("#priceList input.price").forEach(function (i) {
      if (i.value === i.dataset.was) return;
      change[i.dataset.item] = i.value === "" ? null : parseInt(i.value, 10);
    });
    var data = { prices: change };
    var d = $("discount").value === "" ? null : parseInt($("discount").value, 10);
    if (d !== prices.discount) data.discount = d;
    call("POST", "admin/prices", data).then(function (p) { renderPrices(p); say($("priceMsg"), "Gespeichert (" + Object.keys(change).length + " Preise)", true); }).catch(function (e) { say($("priceMsg"), e.message); });
  };

  // ---------------------------------------------------------------- Besitz zurücksetzen / wiederherstellen
  function renderReset(r) {
    $("resetBtn").hidden = Boolean(r);
    $("restoreBtn").hidden = !r;
    $("resetState").textContent = r ? "Zurückgesetzt am " + fmt(r.at) + " (Sicherung " + r.batch + ": " + r.items + " Freischaltungen von " + r.users + " Spielern)" + (r.restored ? " · wiederhergestellt am " + fmt(r.restored) : "")
      : "Noch nicht zurückgesetzt.";
  }
  function twoStep(path, label) {
    call("POST", path, {}).then(function (j) {
      if (j.done) { say($("resetMsg"), "Wurde schon durchgeführt (" + j.batch + ")", true); loadPrices(); return; }
      var s = j.summary, text = path === "admin/reset-ownership"
        ? "Besitz zurücksetzen?\\n\\n" + s.items + " Freischaltungen von " + s.users + " Spielern werden gesichert und entfernt, " + s.equipped + " Spieler legen ihre Cosmetics ab.\\n" + s.admins + " Admin(s) behalten alles."
        : "Sicherung " + s.batch + " wieder einspielen?\\n\\n" + s.items + " Freischaltungen kommen zurück (Gekauftes bleibt, Coins unverändert).";
      if (!confirm(text)) return;
      var word = prompt("Zur zweiten Bestätigung bitte „" + j.word + "“ eingeben:");
      if (word === null) return;
      return call("POST", path, { token: j.token, confirm: word.trim() }).then(function (r) {
        say($("resetMsg"), label + (r.batch ? " (Sicherung " + r.batch + ")" : ""), true); loadPrices(); loadList();
      });
    }).catch(function (e) { say($("resetMsg"), e.message); });
  }
  $("resetBtn").onclick = function () { twoStep("admin/reset-ownership", "Besitz zurückgesetzt"); };
  $("restoreBtn").onclick = function () { twoStep("admin/restore-ownership", "Besitz wiederhergestellt"); };
  $("loadBtn").onclick = function () { var n = $("name").value.trim(); if (n) load(n); };
  $("name").onkeydown = function (e) { if (e.key === "Enter") $("loadBtn").click(); };
  $("noneBtn").onclick = function () { document.querySelectorAll("#editor input[type=checkbox]").forEach(function (i) { i.checked = false; }); };
  $("saveBtn").onclick = function () {
    if (!current) return;
    var items = [].map.call(document.querySelectorAll("#editor input:checked"), function (i) { return i.value; });
    $("saveBtn").disabled = true;
    call("POST", "admin/grants", { uuid: current.uuid, items: items }).then(function (u) {
      render(u); say($("msg"), "Gespeichert: " + (u.items.length ? u.items.length + " Freischaltungen" : "nichts freigeschaltet"), true); loadList();
    }).catch(function (e) { say($("msg"), e.message); }).then(function () { $("saveBtn").disabled = false; });
  };

  function loadList() {
    call("GET", "admin/users").then(function (j) {
      var tb = $("list"); tb.innerHTML = "";
      if (!j.users.length) { tb.innerHTML = '<tr><td colspan="2" class="muted">Noch niemand freigeschaltet.</td></tr>'; return; }
      j.users.forEach(function (u) {
        var tr = document.createElement("tr");
        var a = document.createElement("a"); a.className = "pick"; a.textContent = u.name || u.uuid;
        a.onclick = function () { $("name").value = u.name || u.uuid; load(u.uuid); window.scrollTo(0, 0); };
        var td1 = document.createElement("td"); td1.appendChild(a);
        var td2 = document.createElement("td"); td2.className = "items-col"; td2.textContent = u.items.join(", ");
        tr.appendChild(td1); tr.appendChild(td2); tb.appendChild(tr);
      });
    }).catch(function () {});
  }
  $("reloadBtn").onclick = loadList;
  if (key) login();
})();
</script>
</body>
</html>`;

// Admin-Seite des Friends-Servers (/admin): Cosmetics für einzelne Spieler freischalten.
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
[hidden] { display: none !important; }
</style>
</head>
<body>
<main>
  <h1><span>MYTIC</span> Admin · Cosmetics</h1>

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
      <div id="cats"></div>
      <div class="row" style="margin-top:6px"><button id="saveBtn">Speichern</button><button class="ghost" id="noneBtn">Alles entfernen</button></div>
    </div>
  </section>

  <section class="card" id="listCard" hidden>
    <div class="row"><strong>Spieler mit Freischaltungen</strong><button class="ghost" id="reloadBtn">Aktualisieren</button></div>
    <table><thead><tr><th>Spieler</th><th>Freigeschaltet</th></tr></thead><tbody id="list"></tbody></table>
  </section>
</main>
<script>
(function () {
  var NAMES = { cape: "Umhänge", wings: "Flügel", shield: "Schilde", aura: "Auren" };
  var GENERAL = [["*", "Alles"], ["admin", "Admin"], ["minime", "MiniMe"], ["nametag", "Nametag"]];
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
      $("login").hidden = true; $("app").hidden = false; $("listCard").hidden = false;
      loadList();
    }).catch(function (e) { say($("loginMsg"), e.message === "Keine Admin-Rechte" ? "Falsches Passwort" : e.message); });
  }
  $("loginBtn").onclick = function () { key = $("key").value.trim(); login(); };
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
    var c = $("cats"); c.innerHTML = "";
    Object.keys(NAMES).forEach(function (kind) {
      var box = document.createElement("div"); box.className = "cat";
      var h = document.createElement("h3"); h.textContent = NAMES[kind] + " (" + catalog[kind].length + ")";
      h.appendChild(chip(kind + ":*", "alle " + NAMES[kind], has.has(kind + ":*"), "all"));
      var items = document.createElement("div"); items.className = "items";
      catalog[kind].forEach(function (it) { items.appendChild(chip(kind + ":" + it[0], it[1], has.has(kind + ":" + it[0]))); });
      box.appendChild(h); box.appendChild(items); c.appendChild(box);
    });
  }
  function load(name) {
    say($("msg"), "");
    call("POST", "admin/user", { name: name }).then(render).catch(function (e) { say($("msg"), e.message); });
  }
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

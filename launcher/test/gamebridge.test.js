// Kontowechsel-Verbindung: nur mit Schlüssel, nur ohne Origin, Server schließt ohne Spiele.
const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const { createBridge } = require("../src/core/gamebridge");

function request(url, { method = "GET", key, body, headers = {} } = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request(url, { method, headers: { ...(key ? { Authorization: `Bearer ${key}` } : {}), ...headers } }, (res) => {
      let data = "";
      res.on("data", (c) => (data += c));
      res.on("end", () => resolve({ status: res.statusCode, json: data ? JSON.parse(data) : null }));
    });
    req.on("error", reject);
    if (body) req.write(body);
    req.end();
  });
}

const ACCOUNTS = [{ uuid: "u1", name: "Alpha", type: "microsoft" }, { uuid: "u2", name: "Beta", type: "microsoft" }];

test("Konten nur mit gültigem Schlüssel, ohne Token in der Liste", async () => {
  const switched = [];
  const bridge = createBridge({ listAccounts: () => ACCOUNTS, switchAccount: async (uuid, key) => (switched.push([uuid, key]), { name: "Beta", uuid, accessToken: "tok" }) });
  const key = bridge.issueKey();
  const base = await bridge.start();
  assert.match(base, /^http:\/\/127\.0\.0\.1:\d+\/$/);
  assert.equal((await request(`${base}accounts`)).status, 401);
  assert.equal((await request(`${base}accounts`, { key: "x".repeat(43) })).status, 401);
  assert.equal((await request(`${base}accounts`, { key, headers: { Origin: "https://evil.example" } })).status, 403);
  const list = await request(`${base}accounts`, { key });
  assert.equal(list.status, 200);
  assert.deepEqual(list.json.accounts, [{ uuid: "u1", name: "Alpha" }, { uuid: "u2", name: "Beta" }]);
  assert.equal((await request(`${base}switch`, { method: "POST", key, body: JSON.stringify({ uuid: "nope" }) })).status, 404);
  const sw = await request(`${base}switch`, { method: "POST", key, body: JSON.stringify({ uuid: "u2" }) });
  assert.equal(sw.status, 200);
  assert.equal(sw.json.accessToken, "tok");
  assert.deepEqual(switched, [["u2", key]]);
  bridge.revoke(key);
  await assert.rejects(request(`${base}accounts`, { key }));
});

test("Schlüssel einer beendeten Instanz gilt nicht mehr, andere schon", async () => {
  const bridge = createBridge({ listAccounts: () => ACCOUNTS, switchAccount: async () => ({}) });
  const a = bridge.issueKey(), b = bridge.issueKey();
  const base = await bridge.start();
  bridge.revoke(a);
  assert.equal((await request(`${base}accounts`, { key: a })).status, 401);
  assert.equal((await request(`${base}accounts`, { key: b })).status, 200);
  bridge.revoke(b);
});

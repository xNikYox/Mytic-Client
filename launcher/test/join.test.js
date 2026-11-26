const test = require("node:test");
const assert = require("node:assert");
const { joinable, parseAddress } = require("../src/core/join");

const ip = (...parts) => parts.join(".");

test("Beitreten: nur öffentliche Adressen", () => {
  for (const ok of ["play.hypixel.net", "mc.example.de:25570", ip(1, 2, 3, 4), "[2001:db8::1]:25565"]) assert.equal(joinable(ok), true, ok);
  for (const no of ["Einzelspieler", "Realms", "LAN-Welt", "Privater Server", "localhost", ip(127, 0, 0, 1), ip(10, 1, 2, 3), ip(192, 168, 1, 1) + ":25565",
    ip(172, 20, 0, 1), "[::1]:25565", "server", "mc.local", "x.de:0", "", null]) assert.equal(joinable(no), false, String(no));
  assert.deepEqual(parseAddress("Play.X.de:1234"), { host: "play.x.de", port: 1234 });
});

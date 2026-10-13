// Discord Rich Presence ("Spielt Mytic Client") über die lokale Discord-Schnittstelle (IPC), ohne Zusatzbibliothek.
// Discord am PC öffnet eine Pipe (Windows) bzw. einen Socket (Linux/macOS) namens discord-ipc-0 … 9. Nachrichten sind
// Rahmen aus Opcode (int32 LE), Länge (int32 LE) und JSON. Läuft Discord nicht, versucht es der Launcher später erneut.
const net = require("node:net");
const path = require("node:path");
const crypto = require("node:crypto");

const OP_HANDSHAKE = 0;
const OP_FRAME = 1;
const OP_CLOSE = 2;
const RETRY_MS = 20000;

function socketPath(i) {
  if (process.platform === "win32") return `\\\\?\\pipe\\discord-ipc-${i}`;
  const base = process.env.XDG_RUNTIME_DIR || process.env.TMPDIR || process.env.TMP || process.env.TEMP || "/tmp";
  return path.join(base.replace(/\/$/, ""), `discord-ipc-${i}`);
}

function encode(op, data) {
  const json = Buffer.from(JSON.stringify(data), "utf8");
  const header = Buffer.alloc(8);
  header.writeInt32LE(op, 0);
  header.writeInt32LE(json.length, 4);
  return Buffer.concat([header, json]);
}

class DiscordPresence {
  constructor({ clientId, log = () => {} }) {
    this.clientId = clientId;
    this.log = log;
    this.socket = null;
    this.ready = false;
    this.enabled = false;
    this.activity = null;
    this.buffer = Buffer.alloc(0);
    this.timer = null;
  }

  /** Ein- oder ausschalten (Einstellung im Launcher). */
  setEnabled(on) {
    if (on === this.enabled) return;
    this.enabled = on;
    if (on) this.connect();
    else this.close();
  }

  /** Aktivität setzen (null = nichts anzeigen); wird nach einem Neuverbinden erneut gesendet. */
  set(activity) {
    this.activity = activity;
    this.send();
  }

  send() {
    if (!this.ready || !this.socket) return;
    this.socket.write(encode(OP_FRAME, { cmd: "SET_ACTIVITY", args: { pid: process.pid, activity: this.activity || undefined }, nonce: crypto.randomUUID() }));
  }

  connect(index = 0) {
    if (!this.enabled || this.socket) return;
    if (index > 9) {
      this.retry();
      return;
    }
    const socket = net.createConnection(socketPath(index));
    let opened = false;
    socket.once("connect", () => {
      opened = true;
      this.socket = socket;
      this.buffer = Buffer.alloc(0);
      socket.write(encode(OP_HANDSHAKE, { v: 1, client_id: this.clientId }));
    });
    socket.on("data", (chunk) => this.receive(chunk));
    socket.on("error", () => {
      if (!opened) this.connect(index + 1);
    });
    socket.on("close", () => {
      if (!opened) return;
      this.socket = null;
      this.ready = false;
      this.retry();
    });
  }

  receive(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (this.buffer.length >= 8) {
      const op = this.buffer.readInt32LE(0);
      const length = this.buffer.readInt32LE(4);
      if (this.buffer.length < 8 + length) return;
      let data = {};
      try {
        data = JSON.parse(this.buffer.subarray(8, 8 + length).toString("utf8"));
      } catch {
        // kaputter Rahmen – überspringen
      }
      this.buffer = this.buffer.subarray(8 + length);
      if (op === OP_FRAME && data.evt === "READY") {
        this.ready = true;
        this.log("[Discord] verbunden");
        this.send();
      } else if (op === OP_FRAME && data.evt === "ERROR") {
        this.log(`[Discord] ${data.data && data.data.message}`);
      } else if (op === OP_CLOSE) {
        this.log(`[Discord] getrennt: ${data.message || ""}`);
        if (this.socket) this.socket.destroy();
      }
    }
  }

  retry() {
    clearTimeout(this.timer);
    if (this.enabled) this.timer = setTimeout(() => this.connect(), RETRY_MS);
  }

  close() {
    clearTimeout(this.timer);
    this.ready = false;
    if (this.socket) {
      try {
        this.socket.write(encode(OP_FRAME, { cmd: "SET_ACTIVITY", args: { pid: process.pid }, nonce: crypto.randomUUID() }));
      } catch {
        // egal
      }
      this.socket.destroy();
      this.socket = null;
    }
  }
}

module.exports = { DiscordPresence, encode, socketPath };

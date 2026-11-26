// Kleiner WebSocket-Server (RFC 6455) für den Node-Betrieb des Friends-Servers – nur das, was GET /api/live braucht:
// Text-Nachrichten bis 4 KB, Ping/Pong, Schließen. Auf Cloudflare übernimmt das die Plattform (worker.js).
import crypto from "node:crypto";
import { EmoteHub, MemorySockets } from "./live.js";

const GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
const MAX_FRAME = 4096;

/** Eine Server-Verbindung: send(text), close(code, reason), onmessage/onclose. */
class NodeSocket {
  constructor(socket) {
    this.socket = socket;
    this.closed = false;
    this.buffer = Buffer.alloc(0);
    this.parts = null;
    this.onmessage = () => {};
    this.onclose = () => {};
    socket.on("data", (chunk) => this.data(chunk));
    socket.on("close", () => this.finish());
    socket.on("error", () => this.finish());
  }

  frame(opcode, payload) {
    const len = payload.length;
    const head = len < 126 ? Buffer.from([0x80 | opcode, len]) : Buffer.from([0x80 | opcode, 126, len >> 8, len & 255]);
    return Buffer.concat([head, payload]);
  }

  send(text) {
    if (this.closed) throw new Error("geschlossen");
    const payload = Buffer.from(String(text), "utf8");
    if (payload.length > 65535) throw new Error("zu groß");
    this.socket.write(this.frame(0x1, payload));
  }

  close(code = 1000, reason = "") {
    if (this.closed) return;
    const why = Buffer.from(String(reason).slice(0, 100), "utf8");
    const payload = Buffer.concat([Buffer.from([code >> 8, code & 255]), why]);
    try {
      this.socket.end(this.frame(0x8, payload));
    } catch {
      // egal
    }
    this.finish();
  }

  finish() {
    if (this.closed) return;
    this.closed = true;
    try {
      this.socket.destroy();
    } catch {
      // egal
    }
    this.onclose();
  }

  data(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (!this.closed) {
      const b = this.buffer;
      if (b.length < 2) return;
      const fin = (b[0] & 0x80) !== 0, opcode = b[0] & 0x0f, masked = (b[1] & 0x80) !== 0;
      let len = b[1] & 0x7f, pos = 2;
      if (len === 126) {
        if (b.length < 4) return;
        len = b.readUInt16BE(2);
        pos = 4;
      } else if (len === 127) {
        return this.close(1009, "Zu groß");
      }
      if (!masked) return this.close(1002, "Maske fehlt");
      if (len > MAX_FRAME) return this.close(1009, "Zu groß");
      if (b.length < pos + 4 + len) return;
      const mask = b.subarray(pos, pos + 4);
      const payload = Buffer.alloc(len);
      for (let i = 0; i < len; i++) payload[i] = b[pos + 4 + i] ^ mask[i & 3];
      this.buffer = b.subarray(pos + 4 + len);
      if (opcode === 0x8) return this.close(1000, "");
      if (opcode === 0x9) {
        this.socket.write(this.frame(0xA, payload));
        continue;
      }
      if (opcode === 0xA) continue;
      if (opcode === 0x1 || opcode === 0x2) this.parts = { text: opcode === 0x1, data: [payload] };
      else if (opcode === 0x0 && this.parts) this.parts.data.push(payload);
      else return this.close(1002, "Unerwartet");
      const size = this.parts.data.reduce((n, p) => n + p.length, 0);
      if (size > MAX_FRAME) return this.close(1009, "Zu groß");
      if (!fin) continue;
      const { text, data } = this.parts;
      this.parts = null;
      this.onmessage(text ? Buffer.concat(data).toString("utf8") : null);
    }
  }
}

/**
 * GET /api/live an einem http.Server anbieten. api = createApi(...) (für Anmeldung und Freischaltungen),
 * ipOf(req) = Adresse des Aufrufers. Gibt den EmoteHub zurück (für Tests).
 */
export function attachLive(server, api, ipOf = (req) => String(req.socket.remoteAddress || "")) {
  const sockets = new MemorySockets();
  const hub = new EmoteHub({ sockets, now: api.now, ownsEmote: api.ownsEmote });
  server.on("upgrade", async (req, socket) => {
    const reject = (status, text) => {
      socket.end(`HTTP/1.1 ${status} ${text}\r\nconnection: close\r\ncontent-length: 0\r\n\r\n`);
    };
    try {
      if (!/\/api\/live\/?(\?|$)/.test(req.url || "") || String(req.headers.upgrade || "").toLowerCase() !== "websocket") return reject(404, "Not Found");
      const key = String(req.headers["sec-websocket-key"] || "");
      if (!/^[A-Za-z0-9+/]{22}==$/.test(key)) return reject(400, "Bad Request");
      const request = new Request(`http://localhost${req.url}`, { method: "GET", headers: { authorization: String(req.headers.authorization || "") } });
      const user = await api.liveAuth(request, { ip: ipOf(req) });
      if (user.response) return reject(user.response.status, user.response.status === 429 ? "Too Many Requests" : "Unauthorized");
      const accept = crypto.createHash("sha1").update(key + GUID).digest("base64");
      socket.write(`HTTP/1.1 101 Switching Protocols\r\nupgrade: websocket\r\nconnection: Upgrade\r\nsec-websocket-accept: ${accept}\r\n\r\n`);
      socket.setNoDelay(true);
      const ws = new NodeSocket(socket);
      ws.onclose = () => sockets.remove(ws);
      let queue = Promise.resolve();
      ws.onmessage = (text) => {
        queue = queue.then(() => hub.message(ws, text)).catch(() => {});
      };
      queue = queue.then(() => hub.opened(ws, user)).catch(() => ws.close(1011, "Fehler"));
    } catch {
      reject(500, "Internal Server Error");
    }
  });
  return hub;
}

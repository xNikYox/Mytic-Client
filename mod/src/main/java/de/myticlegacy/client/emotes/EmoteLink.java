package de.myticlegacy.client.emotes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.myticlegacy.client.friends.Friends;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Echtzeit-Verbindung zum Friends-Server für Emotes (GET /api/live, WebSocket). Kostet auf dem Cloudflare-Free-Plan
 * fast nichts: Aufbau = 1 Anfrage, eigene Nachrichten zählen zu 1/20, empfangene gar nicht. Deshalb nur verbunden,
 * solange man auf einem Multiplayer-Server ist (und Emotes an sind); nach IDLE_MS im Menü wird getrennt.
 * Ein kleiner eigener WebSocket-Client (Java 8, auch für 1.8.9), alles in Hintergrund-Threads.
 */
public final class EmoteLink {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final long PING_MS = 60_000;
    private static final long IDLE_MS = 120_000;
    private static final long[] BACKOFF_MS = {5_000, 15_000, 60_000, 300_000};
    private static final int MAX_FRAME = 16 * 1024;

    private static ScheduledExecutorService exec;
    /** gewünschter Server (Hash) – null = kein Multiplayer-Server */
    private static volatile String wanted;
    private static volatile long wantedNullSince;
    private static volatile boolean allowed;
    private static Socket socket;
    private static OutputStream out;
    private static String sentServer;
    private static volatile boolean open;
    private static long lastPing;
    private static long retryAt;
    private static int failures;
    private static int generation;
    /** Abstand lokale Uhr − Serveruhr (aus "hi") */
    private static volatile long offset;
    private static volatile Set<String> usable;

    private EmoteLink() {
    }

    /** Aus dem Spiel-Thread, jeden Tick: Server-Hash (null = kein Multiplayer) und ob überhaupt verbunden werden soll. */
    public static synchronized void want(String serverHash, boolean enabled) {
        if (serverHash == null && wanted != null) wantedNullSince = System.currentTimeMillis();
        wanted = serverHash;
        allowed = enabled;
        if (exec == null && enabled && serverHash != null) {
            exec = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Mytic Emotes");
                t.setDaemon(true);
                return t;
            });
            exec.scheduleWithFixedDelay(EmoteLink::step, 0, 1, TimeUnit.SECONDS);
        }
    }

    public static boolean connected() {
        return open;
    }

    /** Serverzeit (ms) → lokale Zeit */
    public static long toLocal(long serverTime) {
        return serverTime + offset;
    }

    /** Darf dieses Emote genutzt werden? (vor der ersten Verbindung: alle freien) */
    public static boolean usable(EmoteCatalog.Emote e) {
        Set<String> u = usable;
        return u == null ? e.free : u.contains(e.id);
    }

    public static void sendEmote(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "emote");
        o.addProperty("e", id);
        sendLater(o.toString());
    }

    public static void sendStop() {
        JsonObject o = new JsonObject();
        o.addProperty("t", "stop");
        sendLater(o.toString());
    }

    private static void sendLater(final String text) {
        ScheduledExecutorService e = exec;
        if (e == null || !open) return;
        e.execute(() -> {
            if (open) write(0x1, text.getBytes(StandardCharsets.UTF_8));
        });
    }

    // ------------------------------------------------------------------------------------------ Hintergrund

    private static void step() {
        try {
            long now = System.currentTimeMillis();
            String target = wanted;
            boolean should = allowed && Friends.available() && Friends.hasCap("emote1");
            if (open) {
                if (!should || (target == null && now - wantedNullSince > IDLE_MS)) {
                    close();
                    return;
                }
                if (target != null && !target.equals(sentServer)) {
                    JsonObject o = new JsonObject();
                    o.addProperty("t", "srv");
                    o.addProperty("s", target);
                    if (write(0x1, o.toString().getBytes(StandardCharsets.UTF_8))) sentServer = target;
                }
                if (now - lastPing >= PING_MS && write(0x1, "ping".getBytes(StandardCharsets.UTF_8))) lastPing = now;
            } else if (should && target != null && now >= retryAt) {
                connect();
            }
        } catch (Throwable ignored) {
            // nächster Takt
        }
    }

    private static void connect() {
        try {
            URI uri = new URI(Friends.liveUrl());
            boolean tls = "https".equalsIgnoreCase(uri.getScheme());
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : tls ? 443 : 80;
            Socket s;
            if (tls) {
                SSLSocket ssl = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
                SSLParameters params = ssl.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS"); // Zertifikat muss zum Namen passen
                ssl.setSSLParameters(params);
                ssl.connect(new InetSocketAddress(host, port), 10_000);
                s = ssl;
            } else {
                s = new Socket();
                s.connect(new InetSocketAddress(host, port), 10_000);
            }
            s.setTcpNoDelay(true);
            s.setSoTimeout(15_000);
            if (s instanceof SSLSocket) {
                // SNI für den Namen setzen (createSocket() ohne Host kennt ihn sonst nicht)
                SSLSocket ssl = (SSLSocket) s;
                SSLParameters params = ssl.getSSLParameters();
                params.setServerNames(java.util.Collections.<javax.net.ssl.SNIServerName>singletonList(new javax.net.ssl.SNIHostName(host)));
                ssl.setSSLParameters(params);
                ssl.startHandshake();
            }
            byte[] nonce = new byte[16];
            new SecureRandom().nextBytes(nonce);
            String key = Base64.getEncoder().encodeToString(nonce);
            String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath());
            String request = "GET " + path + " HTTP/1.1\r\nHost: " + host + (uri.getPort() > 0 ? ":" + port : "") + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\nAuthorization: Bearer " + Friends.authToken() + "\r\nUser-Agent: MyticClient\r\n\r\n";
            OutputStream o = s.getOutputStream();
            o.write(request.getBytes(StandardCharsets.UTF_8));
            o.flush();
            InputStream in = s.getInputStream();
            String status = line(in);
            String accept = null;
            for (String h = line(in); !h.isEmpty(); h = line(in)) {
                int i = h.indexOf(':');
                if (i > 0 && h.substring(0, i).trim().equalsIgnoreCase("sec-websocket-accept")) accept = h.substring(i + 1).trim();
            }
            String expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
            if (!status.startsWith("HTTP/1.1 101") || !expected.equals(accept)) throw new IOException("Kein WebSocket: " + status);
            s.setSoTimeout(0);
            synchronized (EmoteLink.class) {
                socket = s;
                out = o;
                sentServer = null;
                open = true;
                lastPing = System.currentTimeMillis();
                failures = 0;
            }
            final int gen = ++generation;
            Thread reader = new Thread(() -> read(in, gen), "Mytic Emotes lesen");
            reader.setDaemon(true);
            reader.start();
        } catch (Exception e) {
            retryAt = System.currentTimeMillis() + BACKOFF_MS[Math.min(failures++, BACKOFF_MS.length - 1)];
        }
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0 && c != '\n') {
            if (c != '\r') b.write(c);
            if (b.size() > 8192) throw new IOException("Kopfzeile zu lang");
        }
        if (c < 0 && b.size() == 0) throw new IOException("Verbindung zu");
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void read(InputStream in, int gen) {
        try {
            ByteArrayOutputStream message = null;
            while (true) {
                int b0 = in.read(), b1 = in.read();
                if (b0 < 0 || b1 < 0) break;
                int opcode = b0 & 0x0f;
                boolean fin = (b0 & 0x80) != 0;
                long len = b1 & 0x7f;
                if (len == 126) len = (in.read() << 8) | in.read();
                else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
                }
                if (len < 0 || len > MAX_FRAME) break;
                byte[] payload = new byte[(int) len];
                int got = 0;
                while (got < len) {
                    int n = in.read(payload, got, (int) len - got);
                    if (n < 0) throw new IOException("Verbindung zu");
                    got += n;
                }
                if (opcode == 0x8) break;
                if (opcode == 0x9) {
                    write(0xA, payload);
                    continue;
                }
                if (opcode == 0xA) continue;
                if (opcode == 0x1 || opcode == 0x2) message = new ByteArrayOutputStream();
                if (message == null) continue;
                message.write(payload);
                if (message.size() > MAX_FRAME) break;
                if (!fin) continue;
                String text = new String(message.toByteArray(), StandardCharsets.UTF_8);
                message = null;
                handle(text);
            }
        } catch (Exception ignored) {
            // getrennt
        }
        synchronized (EmoteLink.class) {
            if (gen == generation && open) {
                closeQuietly();
                retryAt = System.currentTimeMillis() + BACKOFF_MS[Math.min(failures++, BACKOFF_MS.length - 1)];
            }
        }
    }

    private static void handle(String text) {
        if ("pong".equals(text)) return;
        JsonObject o;
        try {
            o = new JsonParser().parse(text).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        String t = str(o, "t");
        if ("hi".equals(t)) {
            if (o.has("time")) offset = System.currentTimeMillis() - o.get("time").getAsLong();
            if (o.has("emotes") && o.get("emotes").isJsonArray()) {
                Set<String> set = new HashSet<String>();
                JsonArray a = o.getAsJsonArray("emotes");
                for (JsonElement e : a) set.add(e.getAsString());
                usable = set;
            }
        } else if ("emote".equals(t)) {
            String u = str(o, "u"), n = str(o, "n"), e = str(o, "e");
            long at = o.has("at") ? o.get("at").getAsLong() : 0;
            if (u != null && e != null) Emotes.received(u, n, e, at == 0 ? System.currentTimeMillis() : toLocal(at));
        } else if ("stop".equals(t)) {
            String u = str(o, "u");
            if (u != null) Emotes.receivedStop(u);
        } else if ("wait".equals(t)) {
            Emotes.serverWait(o.has("ms") ? o.get("ms").getAsLong() : 1000);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    /** Rahmen vom Client an den Server (immer maskiert). */
    private static synchronized boolean write(int opcode, byte[] payload) {
        if (!open || out == null) return false;
        try {
            ByteArrayOutputStream f = new ByteArrayOutputStream(payload.length + 14);
            f.write(0x80 | opcode);
            if (payload.length < 126) f.write(0x80 | payload.length);
            else {
                f.write(0x80 | 126);
                f.write(payload.length >> 8);
                f.write(payload.length & 255);
            }
            byte[] mask = new byte[4];
            new SecureRandom().nextBytes(mask);
            f.write(mask);
            for (int i = 0; i < payload.length; i++) f.write(payload[i] ^ mask[i & 3]);
            out.write(f.toByteArray());
            out.flush();
            return true;
        } catch (IOException e) {
            closeQuietly();
            retryAt = System.currentTimeMillis() + BACKOFF_MS[Math.min(failures++, BACKOFF_MS.length - 1)];
            return false;
        }
    }

    private static synchronized void close() {
        if (open) write(0x8, new byte[]{0x03, (byte) 0xE8});
        closeQuietly();
    }

    private static synchronized void closeQuietly() {
        open = false;
        generation++;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
            // schon zu
        }
        socket = null;
        out = null;
        sentServer = null;
    }
}

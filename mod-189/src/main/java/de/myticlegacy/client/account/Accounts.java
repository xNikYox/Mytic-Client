package de.myticlegacy.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import de.myticlegacy.client.friends.Friends;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Kontowechsel im Hauptmenü (1.8.9): Konten kommen aus dem Mytic Launcher (lokale Verbindung, Adresse und Schlüssel
 * als Umgebungsvariablen MYTIC_LAUNCHER_URL / MYTIC_LAUNCHER_KEY). Beim Wechsel wird die Sitzung (Session) von
 * Minecraft ersetzt – Server-Beitritte nutzen danach das neue Konto –, dazu Freundesliste und Cosmetics.
 */
public final class Accounts {
    /** Konto aus dem Launcher; type „microsoft“ oder „offline“, expiresAt = Ablauf der Minecraft-Anmeldung (ms) */
    public static final class Account {
        private final String uuid, name, type;
        private final long expiresAt;
        private final boolean needsLogin;

        Account(String uuid, String name, String type, long expiresAt, boolean needsLogin) {
            this.uuid = uuid;
            this.name = name;
            this.type = type;
            this.expiresAt = expiresAt;
            this.needsLogin = needsLogin;
        }

        public String uuid() {
            return uuid;
        }

        public String name() {
            return name;
        }

        public String type() {
            return type;
        }

        public long expiresAt() {
            return expiresAt;
        }

        public boolean needsLogin() {
            return needsLogin;
        }

        public boolean offline() {
            return "offline".equals(type);
        }
    }

    private static final String URL = launcherUrl();
    private static final String KEY = System.getenv("MYTIC_LAUNCHER_KEY") == null ? "" : System.getenv("MYTIC_LAUNCHER_KEY").trim();
    private static volatile boolean busy;
    private static volatile Set<String> features = Collections.emptySet();

    private interface Call {
        JsonObject run() throws Exception;
    }

    private Accounts() {
    }

    public static boolean available() {
        return !URL.isEmpty() && !KEY.isEmpty();
    }

    public static boolean busy() {
        return busy;
    }

    public static boolean supports(String feature) {
        return features.contains(feature);
    }

    private static String launcherUrl() {
        String url = System.getenv("MYTIC_LAUNCHER_URL");
        if (url == null) return "";
        url = url.trim();
        return url.matches("http://127\\.0\\.0\\.1:\\d{1,5}/") ? url : "";
    }

    private static void onGame(Runnable r) {
        Minecraft.getMinecraft().addScheduledTask(r);
    }

    public static void list(final Consumer<List<Account>> done, final Consumer<String> error) {
        if (!available()) {
            error.accept("Nur verfügbar, wenn das Spiel über den Mytic Launcher gestartet wurde");
            return;
        }
        thread("list", () -> {
            try {
                JsonObject res = request("GET", "accounts", null, 30000);
                final List<Account> list = new ArrayList<Account>();
                for (JsonElement e : res.getAsJsonArray("accounts")) {
                    JsonObject o = e.getAsJsonObject();
                    list.add(new Account(o.get("uuid").getAsString(), o.get("name").getAsString(), str(o, "type").isEmpty() ? "microsoft" : str(o, "type"),
                            o.has("expiresAt") ? o.get("expiresAt").getAsLong() : 0, o.has("needsLogin") && o.get("needsLogin").getAsBoolean()));
                }
                Set<String> f = new HashSet<String>();
                if (res.has("features")) for (JsonElement e : res.getAsJsonArray("features")) f.add(e.getAsString());
                features = f;
                onGame(() -> done.accept(list));
            } catch (final Exception e) {
                onGame(() -> error.accept(message(e)));
            }
        });
    }

    public static void switchTo(Account account, final Runnable done, Consumer<String> error) {
        session(account, "switch", r -> done.run(), error);
    }

    /** Neu einloggen: Launcher erneuert die Anmeldung auf jeden Fall, das Spiel übernimmt die neue Sitzung */
    public static void refresh(Account account, final Consumer<Boolean> done, Consumer<String> error) {
        if (account.offline()) {
            error.accept("Offline-Konten haben keine Anmeldung zum Erneuern");
            return;
        }
        session(account, supports("refresh") ? "refresh" : "switch", r -> done.accept(r.has("relogin") && r.get("relogin").getAsBoolean()), error);
    }

    private static void session(Account account, final String path, final Consumer<JsonObject> done, final Consumer<String> error) {
        if (busy) return;
        if (Minecraft.getMinecraft().theWorld != null) {
            error.accept("Erst die Welt bzw. den Server verlassen");
            return;
        }
        busy = true;
        final JsonObject body = new JsonObject();
        body.addProperty("uuid", account.uuid());
        thread(path, () -> {
            try {
                final JsonObject r = request("POST", path, body, path.equals("refresh") ? 300000 : 30000);
                onGame(() -> {
                    try {
                        apply(r);
                        done.accept(r);
                    } catch (Exception e) {
                        error.accept("Übernehmen fehlgeschlagen: " + message(e));
                    } finally {
                        busy = false;
                    }
                });
            } catch (final Exception e) {
                busy = false;
                onGame(() -> error.accept(message(e)));
            }
        });
    }

    public static void add(final Consumer<String> done, Consumer<String> error) {
        if (busy) return;
        busy = true;
        background("add", () -> request("POST", "add", new JsonObject(), 300000), r -> done.accept(str(r, "name")), error);
    }

    public static void remove(Account account, final Runnable done, Consumer<String> error) {
        if (busy) return;
        busy = true;
        final JsonObject body = new JsonObject();
        body.addProperty("uuid", account.uuid());
        background("remove", () -> request("POST", "remove", body, 30000), r -> done.run(), error);
    }

    private static void background(String name, final Call call, final Consumer<JsonObject> done, final Consumer<String> error) {
        thread(name, () -> {
            try {
                final JsonObject r = call.run();
                onGame(() -> {
                    busy = false;
                    done.accept(r);
                });
            } catch (final Exception e) {
                onGame(() -> {
                    busy = false;
                    error.accept(message(e));
                });
            }
        });
    }

    private static void thread(String name, Runnable r) {
        Thread t = new Thread(r, "Mytic Account " + name);
        t.setDaemon(true);
        t.start();
    }

    private static String message(Exception e) {
        if (e instanceof java.net.ConnectException) return "Launcher nicht erreichbar – ist er noch geöffnet?";
        if (e instanceof java.net.SocketTimeoutException) return "Zeit abgelaufen – keine Antwort vom Launcher";
        if ("not found".equals(e.getMessage())) return "Dafür bitte den Mytic Launcher aktualisieren";
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static JsonObject request(String method, String path, JsonObject body, int readTimeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URI(URL + path).toURL().openConnection(java.net.Proxy.NO_PROXY);
        c.setRequestMethod(method);
        c.setConnectTimeout(3000);
        c.setReadTimeout(readTimeout);
        c.setRequestProperty("Authorization", "Bearer " + KEY);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            OutputStream out = c.getOutputStream();
            try {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
        }
        int status = c.getResponseCode();
        InputStream in = status < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        if (in != null) {
            try {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0 && buf.size() < 1 << 20) buf.write(b, 0, n);
            } finally {
                in.close();
            }
        }
        JsonObject json = buf.size() == 0 ? new JsonObject() : new JsonParser().parse(new String(buf.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
        if (status >= 400) throw new Exception(json.has("error") ? json.get("error").getAsString() : "Launcher antwortet mit " + status);
        return json;
    }

    /** Sitzung von Minecraft ersetzen (Feld über den Typ, unabhängig von den Namen), dann Freunde und Cosmetics */
    private static void apply(JsonObject r) throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        String name = r.get("name").getAsString();
        String uuid = r.get("uuid").getAsString().replace("-", "");
        String token = r.get("accessToken").getAsString();
        Session session = new Session(name, uuid, token, "msa".equals(str(r, "type")) ? "mojang" : "legacy");
        boolean set = false;
        for (Field f : Minecraft.class.getDeclaredFields()) {
            if (f.getType() == Session.class && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true); // auch finale Felder (Java 8)
                f.set(mc, session);
                set = true;
            }
        }
        if (!set) throw new IllegalStateException("Sitzung nicht gefunden");
        JsonElement ft = r.get("friendsToken");
        Friends.switchSession(ft == null || ft.isJsonNull() ? null : ft.getAsString());
        CosmeticsCore.refreshOwned();
    }
}

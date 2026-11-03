package de.myticlegacy.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.UserApiService;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.games.Games;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Kontowechsel im Hauptmenü: Die Konten kommen aus dem Mytic Launcher (lokale Verbindung, Adresse und Schlüssel dieser
 * Instanz als Umgebungsvariablen MYTIC_LAUNCHER_URL / MYTIC_LAUNCHER_KEY). Beim Wechsel werden Konto, Dienste für Chat-
 * Signatur und Profil ersetzt, danach Freundesliste, Partien und Cosmetics auf das neue Konto umgestellt.
 * <p>
 * Die Felder in {@link Minecraft} werden über ihren Typ gefunden (nicht über den Namen) – so passt derselbe Code zu allen
 * Minecraft-Versionen von 1.21 bis 26.x.
 */
public final class Accounts {
    public record Account(String uuid, String name) {
    }

    private static final String URL = launcherUrl();
    private static final String KEY = System.getenv("MYTIC_LAUNCHER_KEY") == null ? "" : System.getenv("MYTIC_LAUNCHER_KEY").trim();
    private static volatile boolean busy;

    private Accounts() {
    }

    /** Nur über den Mytic Launcher gestartet (und nur zu 127.0.0.1) */
    public static boolean available() {
        return !URL.isEmpty() && !KEY.isEmpty();
    }

    public static boolean busy() {
        return busy;
    }

    private static String launcherUrl() {
        String url = System.getenv("MYTIC_LAUNCHER_URL");
        if (url == null) return "";
        url = url.trim();
        return url.matches("http://127\\.0\\.0\\.1:\\d{1,5}/") ? url : "";
    }

    /** Konten aus dem Launcher holen (im Hintergrund); Ergebnis bzw. Fehlertext auf dem Spiel-Thread */
    public static void list(Consumer<List<Account>> done, Consumer<String> error) {
        if (!available()) {
            error.accept("Nur verfügbar, wenn das Spiel über den Mytic Launcher gestartet wurde");
            return;
        }
        Thread t = new Thread(() -> {
            try {
                JsonArray arr = request("GET", "accounts", null).getAsJsonArray("accounts");
                List<Account> list = new ArrayList<>();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    list.add(new Account(o.get("uuid").getAsString(), o.get("name").getAsString()));
                }
                Minecraft.getInstance().execute(() -> done.accept(list));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> error.accept(message(e)));
            }
        }, "Mytic Accounts");
        t.setDaemon(true);
        t.start();
    }

    /** Zu einem Konto wechseln: Token vom Launcher holen, dann auf dem Spiel-Thread umstellen */
    public static void switchTo(Account account, Runnable done, Consumer<String> error) {
        if (busy) return;
        if (Minecraft.getInstance().level != null) {
            error.accept("Erst die Welt bzw. den Server verlassen");
            return;
        }
        busy = true;
        Thread t = new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("uuid", account.uuid());
                JsonObject r = request("POST", "switch", body);
                String token = r.get("accessToken").getAsString();
                // Dienste mit dem neuen Token schon hier anlegen (Netzwerk), nicht auf dem Spiel-Thread
                UserApiService api = createApi(token);
                Minecraft.getInstance().execute(() -> {
                    try {
                        apply(r, api);
                        done.run();
                    } catch (Exception e) {
                        error.accept("Wechsel fehlgeschlagen: " + message(e));
                    } finally {
                        busy = false;
                    }
                });
            } catch (Exception e) {
                busy = false;
                Minecraft.getInstance().execute(() -> error.accept(message(e)));
            }
        }, "Mytic Account Switch");
        t.setDaemon(true);
        t.start();
    }

    private static String message(Exception e) {
        if (e instanceof java.net.ConnectException) return "Launcher nicht erreichbar – ist er noch geöffnet?";
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static JsonObject request(String method, String path, JsonObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URI(URL + path).toURL().openConnection(java.net.Proxy.NO_PROXY);
        c.setRequestMethod(method);
        c.setConnectTimeout(3000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Authorization", "Bearer " + KEY);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = c.getResponseCode();
        InputStream in = status < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        if (in != null) {
            try (in) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0 && buf.size() < 1 << 20) buf.write(b, 0, n);
            }
        }
        JsonObject json = buf.size() == 0 ? new JsonObject() : JsonParser.parseString(buf.toString(StandardCharsets.UTF_8)).getAsJsonObject();
        if (status >= 400) throw new Exception(json.has("error") ? json.get("error").getAsString() : "Launcher antwortet mit " + status);
        return json;
    }

    // ------------------------------------------------------------------------------------------ Umstellen

    /** UserApiService (Chat-Schlüssel, Blockliste, Chat-Rechte) für den neuen Token – authlib je nach Version */
    private static UserApiService createApi(String token) {
        if (token.isEmpty() || token.equals("0")) return UserApiService.OFFLINE;
        try {
            // ab 26.x: Dienste über die Service-Erkennung
            Class<?> discovery = Class.forName("com.mojang.authlib.services.MinecraftServicesDiscoveryService");
            Object service = discovery.getMethod("create", java.net.Proxy.class).invoke(null, java.net.Proxy.NO_PROXY);
            return (UserApiService) discovery.getMethod("createUserApiService", String.class).invoke(service, token);
        } catch (ClassNotFoundException e) {
            // bis 1.21.x: Yggdrasil
        } catch (Exception e) {
            return UserApiService.OFFLINE;
        }
        try {
            Class<?> ygg = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService");
            Object service = ygg.getConstructor(java.net.Proxy.class).newInstance(java.net.Proxy.NO_PROXY);
            return (UserApiService) ygg.getMethod("createUserApiService", String.class).invoke(service, token);
        } catch (Exception e) {
            return UserApiService.OFFLINE;
        }
    }

    private static void apply(JsonObject r, UserApiService api) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        String name = r.get("name").getAsString();
        UUID uuid = uuid(r.get("uuid").getAsString());
        String token = r.get("accessToken").getAsString();
        String xuid = str(r, "xuid"), clientId = str(r, "clientId");
        User user = newUser(mc.getUser(), name, uuid, token, xuid, clientId);

        set(mc, User.class, user);
        set(mc, UserApiService.class, api);
        set(mc, ProfileKeyPairManager.class, ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath()));
        setFuture(mc, "UserProperties", CompletableFuture.supplyAsync(() -> {
            try {
                return api.fetchProperties();
            } catch (Exception e) {
                return UserApiService.OFFLINE_PROPERTIES;
            }
        }));
        // eigenes Profil (Skin im Einzelspieler usw.): Minecrafts eigene Ladefunktion liest das neue Konto
        Method profile = profileLoader();
        if (profile != null) {
            setFuture(mc, "ProfileResult", CompletableFuture.supplyAsync(() -> {
                try {
                    return profile.invoke(mc);
                } catch (Exception e) {
                    return null;
                }
            }));
        }

        // Mytic: Freunde, Partien und Cosmetics gehören jetzt zum neuen Konto
        JsonElement ft = r.get("friendsToken");
        Friends.switchSession(ft == null || ft.isJsonNull() ? null : ft.getAsString());
        Games.reset();
        CosmeticsCore.refreshOwned();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static UUID uuid(String raw) {
        String s = raw.replace("-", "");
        if (s.length() != 32) return UUID.fromString(raw);
        return new UUID(Long.parseUnsignedLong(s.substring(0, 16), 16), Long.parseUnsignedLong(s.substring(16), 16));
    }

    /** User(name, uuid, token, xuid, clientId[, Type]) – bis 1.21.8 mit Typ, der vom bisherigen Konto übernommen wird */
    private static User newUser(User old, String name, UUID uuid, String token, String xuid, String clientId) throws Exception {
        Optional<String> x = xuid.isEmpty() ? Optional.empty() : Optional.of(xuid);
        Optional<String> c = clientId.isEmpty() ? Optional.empty() : Optional.of(clientId);
        for (Constructor<?> ctor : User.class.getConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length == 5 && p[0] == String.class && p[1] == UUID.class) return (User) ctor.newInstance(name, uuid, token, x, c);
            if (p.length == 6 && p[0] == String.class && p[1] == UUID.class && p[5].isEnum()) {
                Object type = null;
                for (Field f : User.class.getDeclaredFields()) {
                    if (f.getType() == p[5] && !Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        type = f.get(old);
                    }
                }
                return (User) ctor.newInstance(name, uuid, token, x, c, type);
            }
        }
        throw new IllegalStateException("Kontoklasse unbekannt");
    }

    /** Feld der Klasse Minecraft mit genau diesem Typ ersetzen (auch final) */
    private static void set(Minecraft mc, Class<?> type, Object value) throws Exception {
        for (Field f : Minecraft.class.getDeclaredFields()) {
            if (f.getType() == type && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                f.set(mc, value);
                return;
            }
        }
        throw new IllegalStateException("Feld " + type.getSimpleName() + " nicht gefunden");
    }

    /** CompletableFuture-Feld anhand des Inhaltstyps (z. B. „ProfileResult“) ersetzen; fehlt es, wird nichts geändert */
    private static void setFuture(Minecraft mc, String inner, CompletableFuture<?> value) throws Exception {
        for (Field f : Minecraft.class.getDeclaredFields()) {
            if (f.getType() == CompletableFuture.class && !Modifier.isStatic(f.getModifiers()) && f.getGenericType().getTypeName().contains(inner)) {
                f.setAccessible(true);
                f.set(mc, value);
                return;
            }
        }
    }

    /** Minecrafts Funktion, die das eigene Profil (mit Skin) für das aktuelle Konto lädt */
    private static Method profileLoader() {
        for (Method m : Minecraft.class.getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers()) && !Modifier.isPublic(m.getModifiers())
                    && m.getReturnType().getName().endsWith(".ProfileResult")) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}

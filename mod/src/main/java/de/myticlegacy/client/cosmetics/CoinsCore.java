package de.myticlegacy.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.gui.ui.Toasts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coins und Shop: Kontostand (kommt mit dem Lebenszeichen, ohne eigene Abfrage), Preise und Sets (kommen mit
 * api/cosmetics/owned beim Öffnen des Cosmetics-Menüs, nur bei neuem Preisstand), Kaufen, Verschenken an Freunde,
 * Tagesbonus-Hinweise und Admin-Buchungen. Netz nur im Hintergrund-Thread der Freundesliste; das Menü liest nur den
 * aktuellen Stand. Echtgeld gibt es hier nicht. (Gleicher Code für alle Versionen, daher Java 8 und altes Gson.)
 */
public final class CoinsCore {
    /** Set aus dem Shop: Teile ("cape:x" …), Summe einzeln und Set-Preis (mit Rabatt, für alle Teile). */
    public static final class SetInfo {
        public final String id, name;
        public final List<String> items;
        public final int sum, price;

        SetInfo(String id, String name, List<String> items, int sum, int price) {
            this.id = id;
            this.name = name;
            this.items = items;
            this.sum = sum;
            this.price = price;
        }
    }

    /** Buchung im Verlauf */
    public static final class Tx {
        public final String kind, reason, other, ref, admin;
        public final int delta, balance;
        public final long at;

        Tx(JsonObject o) {
            kind = str(o, "kind");
            reason = str(o, "reason");
            other = str(o, "other");
            ref = str(o, "ref");
            admin = str(o, "admin");
            delta = o.has("delta") ? o.get("delta").getAsInt() : 0;
            balance = o.has("balance") ? o.get("balance").getAsInt() : 0;
            at = o.has("at") ? o.get("at").getAsLong() : 0;
        }
    }

    public interface Result {
        /** ok, Meldung (Fehler oder Bestätigung) – läuft im Hintergrund-Thread */
        void done(boolean ok, String message);
    }

    public static final int GIFT_MIN = 10;
    private static volatile int balance = -1;
    private static volatile String pv;
    private static volatile int lastTx = -1;
    private static volatile Map<String, Integer> prices = Collections.emptyMap();
    private static volatile List<SetInfo> sets = Collections.emptyList();
    private static volatile int discount = 20;
    private static volatile String shopV;
    private static volatile boolean busy;

    // Verlauf, Tagesbonus und Geschenk-Limit (GET api/coins, beim Öffnen des Reiters „Coins“)
    public static volatile List<Tx> history = Collections.emptyList();
    public static volatile boolean infoLoaded, infoLoading;
    public static volatile boolean dailyClaimed;
    public static volatile int dailyStreak, dailyAmount = 10, dailyMax = 40, giftSent, giftLimit = 500;

    // Admin: Coins des im Reiter „Admin“ geladenen Spielers
    public static volatile String adminUuid;
    public static volatile int adminBalance = -1;
    public static volatile List<Tx> adminTx = Collections.emptyList();
    public static volatile String adminMessage;
    public static volatile boolean adminError;

    // Vorschau eines noch nicht gekauften Teils an der eigenen Figur (solange der Kauf-Dialog offen ist)
    private static volatile String previewKind, previewId;
    private static volatile long previewAt;

    private CoinsCore() {
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    // ------------------------------------------------------------------------------------------ Stand

    /** Kann der Friends-Server Coins (neuere Server melden "coins1")? */
    public static boolean supported() {
        return Friends.available() && (Friends.hasCap("coins1") || balance >= 0);
    }

    /** Kontostand oder -1 (noch unbekannt) */
    public static int balance() {
        return balance;
    }

    /** Preis eines Teils ("cape:galaxie", "minime", "nametag", "nametag:<vorlage>") oder null (unbekannt) */
    public static Integer price(String item) {
        return prices.get(item);
    }

    public static boolean shopLoaded() {
        return !prices.isEmpty();
    }

    /** Preisstand der geladenen Preise (für api/cosmetics/owned: Preise nur, wenn sich etwas geändert hat) */
    public static String shopVersion() {
        String v = shopV;
        return v != null && v.equals(pv == null ? v : pv) ? v : "";
    }

    public static List<SetInfo> sets() {
        return sets;
    }

    public static int discount() {
        return discount;
    }

    public static boolean busy() {
        return busy;
    }

    /** Letzte bekannte Buchung (für das Lebenszeichen: Hinweise nur für Neues) oder -1 */
    public static int lastTx() {
        return lastTx;
    }

    /** Zeitzone für den Tagesbonus: Minuten östlich von UTC */
    public static int tz() {
        return java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000;
    }

    /** Set-Preis für die noch fehlenden Teile (wie der Server: Summe minus Rabatt, gerundet); 0 = alles schon da */
    public static int bundlePrice(SetInfo set) {
        int sum = 0;
        for (String item : set.items) if (!ownsItem(item)) sum += prices.containsKey(item) ? prices.get(item) : 0;
        return Math.round(sum * (100 - discount) / 100f);
    }

    public static int missing(SetInfo set) {
        int n = 0;
        for (String item : set.items) if (!ownsItem(item)) n++;
        return n;
    }

    /** Besitzt man ein kaufbares Teil schon? */
    public static boolean ownsItem(String item) {
        int i = item.indexOf(':');
        return i < 0 ? CosmeticsCore.owns(item, null) : CosmeticsCore.owns(item.substring(0, i), item.substring(i + 1));
    }

    /** 1.234 statt 1234 */
    public static String fmt(int n) {
        String s = Integer.toString(Math.abs(n));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) out.append('.');
            out.append(s.charAt(i));
        }
        return (n < 0 ? "−" : "") + out;
    }

    // ------------------------------------------------------------------------------------------ Vorschau

    /** Kauf-Dialog: dieses Teil an der eigenen Figur zeigen (jedes Bild neu setzen, verfällt sonst nach 0,5 s). */
    public static void preview(String kind, String id) {
        previewKind = kind;
        previewId = id;
        previewAt = System.currentTimeMillis();
    }

    /** Vorschau für diese Art (cape, wings, shield, aura, acc, nametag) oder null */
    public static String previewOf(String kind) {
        String k = previewKind;
        if (k == null || !k.equals(kind)) return null;
        if (System.currentTimeMillis() - previewAt > 500) {
            previewKind = null;
            return null;
        }
        return previewId;
    }

    // ------------------------------------------------------------------------------------------ Vom Server

    /** coins aus dem Lebenszeichen: { balance, pv, tx, daily?, events? } */
    public static void fromBeat(JsonObject c) {
        try {
            if (c.has("balance")) balance = c.get("balance").getAsInt();
            if (c.has("pv")) pv = c.get("pv").getAsString();
            boolean first = lastTx < 0;
            if (c.has("daily") && c.get("daily").isJsonObject()) {
                JsonObject d = c.getAsJsonObject("daily");
                int amount = d.get("amount").getAsInt(), streak = d.get("streak").getAsInt();
                dailyClaimed = true;
                dailyStreak = streak;
                dailyAmount = amount;
                String text = "+" + amount + " Coins (Tag " + streak + ")";
                Friends.notice("§6⛃ " + text + " §7– Tagesbonus" + (streak >= 7 ? ", Höchststand" : "") + " §8(Kontostand: " + fmt(balance) + ")");
                Toasts.show(text, "coin", Toasts.OK);
            }
            if (!first && c.has("events") && c.get("events").isJsonArray()) {
                for (JsonElement e : c.getAsJsonArray("events")) {
                    JsonObject o = e.getAsJsonObject();
                    String kind = str(o, "kind"), reason = str(o, "reason");
                    int delta = o.get("delta").getAsInt();
                    String why = reason == null ? "" : " §8(" + reason + ")";
                    if ("gift_in".equals(kind)) {
                        String from = str(o, "from");
                        Friends.notice("§d" + (from == null ? "Ein Freund" : from) + " §7hat dir §6" + fmt(delta) + " Coins §7geschenkt");
                        Toasts.show((from == null ? "Geschenk" : from) + ": +" + fmt(delta) + " Coins", "gift", Toasts.OK);
                    } else if ("grant".equals(kind)) {
                        Friends.notice("§6+" + fmt(delta) + " Coins §7von einem Admin" + why);
                        Toasts.show("+" + fmt(delta) + " Coins", "coin", Toasts.OK);
                    } else if ("take".equals(kind)) {
                        Friends.notice("§c" + fmt(delta) + " Coins §7(von einem Admin abgezogen)" + why);
                    } else if ("refund".equals(kind)) {
                        Friends.notice("§6+" + fmt(delta) + " Coins §7erstattet" + why);
                    }
                }
            }
            if (c.has("tx")) lastTx = c.get("tx").getAsInt();
            if (infoLoaded && c.has("daily")) infoLoaded = false; // Verlauf beim nächsten Öffnen neu
        } catch (RuntimeException ignored) {
            // unlesbar – alter Stand bleibt
        }
    }

    /** Antwort von api/cosmetics/owned: { coins, pv, shop? } */
    public static void fromOwned(JsonObject a) {
        try {
            if (a.has("coins")) balance = a.get("coins").getAsInt();
            if (a.has("pv")) pv = a.get("pv").getAsString();
            if (a.has("shop") && a.get("shop").isJsonObject()) parseShop(a.getAsJsonObject("shop"));
        } catch (RuntimeException ignored) {
            // unlesbar – alter Stand bleibt
        }
    }

    private static void parseShop(JsonObject s) {
        Map<String, Integer> p = new HashMap<String, Integer>();
        for (Map.Entry<String, JsonElement> e : s.getAsJsonObject("items").entrySet()) p.put(e.getKey(), e.getValue().getAsInt());
        List<SetInfo> list = new ArrayList<SetInfo>();
        if (s.has("sets")) {
            for (JsonElement e : s.getAsJsonArray("sets")) {
                JsonObject o = e.getAsJsonObject();
                List<String> items = new ArrayList<String>();
                for (JsonElement i : o.getAsJsonArray("items")) items.add(i.getAsString());
                list.add(new SetInfo(str(o, "id"), str(o, "name"), Collections.unmodifiableList(items), o.get("sum").getAsInt(), o.get("price").getAsInt()));
            }
        }
        if (s.has("discount")) discount = s.get("discount").getAsInt();
        prices = Collections.unmodifiableMap(p);
        sets = Collections.unmodifiableList(list);
        shopV = str(s, "v");
    }

    // ------------------------------------------------------------------------------------------ Aktionen

    private static String newKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Anfrage mit einem zweiten Versuch (gleicher Schlüssel), falls der Server nicht erreichbar war */
    private static JsonObject postTwice(String path, JsonObject body) throws Exception {
        try {
            return Friends.post(path, body);
        } catch (Exception e) {
            if (e.getMessage() == null || !e.getMessage().contains("nicht erreichbar")) throw e;
            Thread.sleep(1500);
            return Friends.post(path, body);
        }
    }

    /** Teil (item) oder Set (setId) kaufen; price = angezeigter Preis (weicht er ab, lehnt der Server ab). */
    public static void buy(final String item, final String setId, final int price, final Result result) {
        if (busy || !Friends.available()) return;
        busy = true;
        final JsonObject body = new JsonObject();
        if (setId != null) body.addProperty("set", setId);
        else body.addProperty("item", item);
        body.addProperty("idemKey", newKey());
        body.addProperty("price", price);
        Friends.background(new Runnable() {
            public void run() {
                try {
                    JsonObject answer = postTwice("api/shop/buy", body);
                    if (answer.has("balance")) balance = answer.get("balance").getAsInt();
                    if (answer.has("owned")) {
                        JsonObject owned = new JsonObject();
                        owned.add("items", answer.getAsJsonArray("owned"));
                        owned.addProperty("admin", CosmeticsCore.isAdmin());
                        CosmeticsCore.applyOwnedAnswer(owned);
                    } else {
                        CosmeticsCore.refreshOwned();
                    }
                    infoLoaded = false;
                    result.done(true, (answer.has("name") ? str(answer, "name") : "Gekauft") + " gekauft – " + fmt(price) + " Coins");
                } catch (Exception e) {
                    // Preis geändert: neue Preise holen
                    if (e.getMessage() != null && e.getMessage().contains("Preis")) {
                        shopV = null;
                        CosmeticsCore.refreshOwned();
                    }
                    result.done(false, e.getMessage());
                } finally {
                    busy = false;
                }
            }
        });
    }

    /** Coins an einen Freund verschenken */
    public static void gift(final String to, final int amount, final Result result) {
        if (busy || !Friends.available()) return;
        busy = true;
        final JsonObject body = new JsonObject();
        body.addProperty("to", to);
        body.addProperty("amount", amount);
        body.addProperty("idemKey", newKey());
        Friends.background(new Runnable() {
            public void run() {
                try {
                    JsonObject answer = postTwice("api/coins/gift", body);
                    if (answer.has("balance")) balance = answer.get("balance").getAsInt();
                    if (answer.has("sentToday")) giftSent = answer.get("sentToday").getAsInt();
                    infoLoaded = false;
                    result.done(true, fmt(amount) + " Coins an " + to + " verschenkt");
                } catch (Exception e) {
                    result.done(false, e.getMessage());
                } finally {
                    busy = false;
                }
            }
        });
    }

    /** Verlauf, Tagesbonus und Geschenk-Limit laden (beim Öffnen des Reiters „Coins“, nicht im Takt) */
    public static void loadInfo() {
        if (infoLoading || !Friends.available()) return;
        infoLoading = true;
        Friends.background(new Runnable() {
            public void run() {
                try {
                    JsonObject a = Friends.get("api/coins?tz=" + tz());
                    if (a.has("balance")) balance = a.get("balance").getAsInt();
                    if (a.has("daily")) {
                        JsonObject d = a.getAsJsonObject("daily");
                        dailyClaimed = d.get("claimed").getAsBoolean();
                        dailyStreak = d.get("streak").getAsInt();
                        dailyAmount = d.get("amount").getAsInt();
                        dailyMax = d.has("max") ? d.get("max").getAsInt() : 40;
                    }
                    if (a.has("gift")) {
                        JsonObject g = a.getAsJsonObject("gift");
                        giftSent = g.get("sent").getAsInt();
                        giftLimit = g.get("limit").getAsInt();
                    }
                    history = txList(a.getAsJsonArray("tx"));
                    infoLoaded = true;
                } catch (Exception ignored) {
                    // alter Stand bleibt
                } finally {
                    infoLoading = false;
                }
            }
        });
    }

    private static List<Tx> txList(JsonArray array) {
        List<Tx> out = new ArrayList<Tx>();
        if (array != null) for (JsonElement e : array) out.add(new Tx(e.getAsJsonObject()));
        return Collections.unmodifiableList(out);
    }

    // ------------------------------------------------------------------------------------------ Admin

    /** Coins eines Spielers laden (Name oder UUID) */
    public static void adminLoad(final String player) {
        if (!Friends.available() || player == null || player.trim().isEmpty()) return;
        Friends.background(new Runnable() {
            public void run() {
                try {
                    adminApply(Friends.get("api/admin/coins/" + java.net.URLEncoder.encode(player.trim(), "UTF-8")));
                } catch (Exception e) {
                    adminMessage = e.getMessage();
                    adminError = true;
                }
            }
        });
    }

    private static void adminApply(JsonObject v) {
        adminUuid = str(v, "uuid");
        adminBalance = v.get("balance").getAsInt();
        adminTx = txList(v.getAsJsonArray("tx"));
    }

    /** Coins geben (delta > 0) oder nehmen (delta < 0) mit Grund (sieht der Spieler) */
    public static void adminBook(final String player, final int delta, final String reason, final Result result) {
        if (busy || !Friends.available()) return;
        busy = true;
        final JsonObject body = new JsonObject();
        body.addProperty("player", player);
        body.addProperty("delta", delta);
        if (reason != null && !reason.trim().isEmpty()) body.addProperty("reason", reason.trim());
        Friends.background(new Runnable() {
            public void run() {
                try {
                    JsonObject v = Friends.post("api/admin/coins", body);
                    adminApply(v);
                    int changed = v.has("changed") ? v.get("changed").getAsInt() : delta;
                    String name = str(v, "name");
                    adminMessage = (changed > 0 ? "+" : "") + fmt(changed) + " Coins für " + (name == null ? player : name) + " – Stand " + fmt(adminBalance);
                    adminError = false;
                    Friends.Friend me = Friends.me();
                    if (me != null && me.uuid.equals(adminUuid)) balance = adminBalance;
                    result.done(true, adminMessage);
                } catch (Exception e) {
                    adminMessage = e.getMessage();
                    adminError = true;
                    result.done(false, e.getMessage());
                } finally {
                    busy = false;
                }
            }
        });
    }

    /** Text für eine Buchung im Verlauf */
    public static String describe(Tx t) {
        String k = t.kind == null ? "" : t.kind;
        if (k.equals("daily")) return "Tagesbonus" + (t.reason != null ? " · " + t.reason : "");
        if (k.equals("purchase")) return "Kauf" + (t.ref != null ? " · " + itemName(t.ref) : "");
        if (k.equals("gift_in")) return "Geschenk von " + (t.other != null ? t.other : "?");
        if (k.equals("gift_out")) return "Geschenk an " + (t.other != null ? t.other : "?");
        if (k.equals("grant")) return "Gutschrift" + (t.reason != null ? " · " + t.reason : "") + (t.admin != null ? " · " + t.admin : "");
        if (k.equals("take")) return "Abzug" + (t.reason != null ? " · " + t.reason : "") + (t.admin != null ? " · " + t.admin : "");
        if (k.equals("refund")) return "Erstattung" + (t.reason != null ? " · " + t.reason : "");
        return k;
    }

    /** Anzeigename eines Teils oder Sets ("cape:galaxie" → „Galaxie“) */
    public static String itemName(String ref) {
        if (ref == null) return "";
        if (ref.startsWith("set:")) {
            for (SetInfo s : sets) if (ref.equals("set:" + s.id)) return s.name;
            return ref.substring(4);
        }
        if (ref.equals("minime")) return "MiniMe";
        if (ref.equals("nametag")) return "Nametag";
        int i = ref.indexOf(':');
        if (i < 0) return ref;
        String kind = ref.substring(0, i), id = ref.substring(i + 1);
        if (kind.equals("cape")) {
            CosmeticsCore.Cape c = CosmeticsCore.byId(id);
            if (c != null) return c.name;
        } else if (kind.equals("aura")) {
            CosmeticsCore.Aura a = CosmeticsCore.aura(id);
            if (a != null) return a.name;
        } else if (kind.equals("acc")) {
            CosmeticsCore.Acc a = CosmeticsCore.acc(id);
            if (a != null) return a.name;
        } else if (kind.equals("wings")) {
            for (String[] w : CosmeticsCore.wings()) if (w[0].equals(id)) return w[1];
        } else if (kind.equals("shield")) {
            for (String[] s : CosmeticsCore.shields()) if (s[0].equals(id)) return s[1];
        } else if (kind.equals("nametag")) {
            for (String[] p : CosmeticsCore.nametagPresets()) if (p[0].equals(id)) return "Vorlage „" + p[1] + "“";
        }
        return id;
    }
}

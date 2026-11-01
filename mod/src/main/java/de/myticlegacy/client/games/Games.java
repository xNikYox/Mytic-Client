package de.myticlegacy.client.games;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.module.Msg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Mytic Games (zuerst Schach): Partien liegen auf dem Friends-Server (api/games). Der Client fragt regelmäßig ab –
 * alle 1,5 s während einer Partie, sonst alle 5 s – und meldet Einladungen, Züge und Ergebnisse im Chat.
 * Alle Anfragen laufen im Hintergrund-Thread der Freundesliste; Ergebnisse gehen über mc.execute auf den Spiel-Thread.
 */
public final class Games {
    private Games() {
    }

    /** Eine Partie wie vom Server (Zeitangaben in ms; Uhren gelten zum Zeitpunkt fetchedAt) */
    public static final class Game {
        public final String id, status, color, fen, turn, drawOffer, result, reason;
        public final String inviterUuid, inviterName, inviteeUuid, inviteeName, whiteUuid, whiteName, blackUuid, blackName;
        public final int time;
        public final List<String> moves;
        public final long clockWhite, clockBlack, fetchedAt;

        Game(JsonObject o) {
            id = str(o, "id");
            status = str(o, "status");
            color = str(o, "color");
            fen = str(o, "fen");
            turn = str(o, "turn");
            drawOffer = str(o, "drawOffer");
            result = str(o, "result");
            reason = str(o, "reason");
            JsonObject inviter = obj(o, "inviter"), invitee = obj(o, "invitee"), white = obj(o, "white"), black = obj(o, "black");
            inviterUuid = str(inviter, "uuid");
            inviterName = str(inviter, "name");
            inviteeUuid = str(invitee, "uuid");
            inviteeName = str(invitee, "name");
            whiteUuid = str(white, "uuid");
            whiteName = str(white, "name");
            blackUuid = str(black, "uuid");
            blackName = str(black, "name");
            time = o.has("time") ? o.get("time").getAsInt() : 300;
            List<String> list = new ArrayList<>();
            if (o.has("moves") && o.get("moves").isJsonArray()) for (JsonElement e : o.getAsJsonArray("moves")) list.add(e.getAsString());
            moves = Collections.unmodifiableList(list);
            JsonObject clock = obj(o, "clock");
            clockWhite = clock != null && clock.has("white") ? clock.get("white").getAsLong() : time * 1000L;
            clockBlack = clock != null && clock.has("black") ? clock.get("black").getAsLong() : time * 1000L;
            fetchedAt = System.currentTimeMillis();
        }

        public boolean active() {
            return "active".equals(status);
        }

        public boolean invited() {
            return "invited".equals(status);
        }

        public boolean over() {
            return "over".equals(status);
        }

        /** Spiele ich Weiß? (vor dem Annehmen: was ich bekäme, falls festgelegt) */
        public boolean meWhite() {
            String me = myUuid();
            return me != null && me.equals(whiteUuid);
        }

        public boolean myTurn() {
            return active() && ("w".equals(turn) == meWhite());
        }

        public String opponentName() {
            String me = myUuid();
            return me != null && me.equals(inviterUuid) ? inviteeName : inviterName;
        }

        public boolean incoming() {
            String me = myUuid();
            return invited() && me != null && me.equals(inviteeUuid);
        }

        /** Restzeit jetzt (die Uhr der Seite am Zug läuft seit fetchedAt weiter) */
        public long clock(boolean whiteSide) {
            long base = whiteSide ? clockWhite : clockBlack;
            if (active() && "w".equals(turn) == whiteSide) base -= System.currentTimeMillis() - fetchedAt;
            return Math.max(0, base);
        }

        public Chess position() {
            try {
                return fen == null ? Chess.start() : Chess.fromFen(fen);
            } catch (Exception e) {
                return Chess.start();
            }
        }
    }

    private static volatile List<Game> games = Collections.emptyList();
    private static volatile boolean polling;
    private static long lastPoll;
    private static volatile boolean pollSoon;
    /** schon gemeldet: Einladungen, Zugzahl je Partie, Endstand je Partie */
    private static final Set<String> announcedInvites = new HashSet<>();
    private static final Map<String, Integer> seenMoves = new HashMap<>();
    private static final Map<String, String> seenStatus = new HashMap<>();
    private static boolean firstPoll = true;
    /** Bildschirm, der nach dem Schließen des Chats geöffnet wird (Befehle laufen aus dem Chat heraus) */
    private static Screen openNext;
    private static int openDelay;
    /** War der Spieler in einer Welt? (Verlassen der Welt/des Servers = Partie aufgeben) */
    private static boolean inWorld;
    private static boolean hookAdded;

    public static List<Game> all() {
        return games;
    }

    public static Game get(String id) {
        for (Game g : games) if (g.id.equals(id)) return g;
        return null;
    }

    /** Laufende Partie (die zuletzt aktualisierte) oder null */
    public static Game active() {
        for (Game g : games) if (g.active()) return g;
        return null;
    }

    /** Neueste Einladung an mich oder null */
    public static Game incomingInvite() {
        for (Game g : games) if (g.incoming()) return g;
        return null;
    }

    public static String myUuid() {
        Friends.Friend me = Friends.me();
        return me == null ? null : me.uuid;
    }

    public static boolean available() {
        return Friends.available();
    }

    /** Bildschirm im nächsten Tick öffnen (nach dem Chat) */
    public static void openLater(Screen screen) {
        openNext = screen;
        openDelay = 2;
    }

    // ------------------------------------------------------------------------------------------ Abfragen

    public static void tick(Minecraft mc) {
        if (!hookAdded && available()) {
            hookAdded = true;
            Runtime.getRuntime().addShutdownHook(new Thread(Games::leaveAllNow, "Mytic Games verlassen"));
        }
        boolean nowInWorld = mc.level != null;
        if (inWorld && !nowInWorld) leaveAll();
        inWorld = nowInWorld;
        if (openNext != null && --openDelay <= 0) {
            Screen s = openNext;
            openNext = null;
            Compat.setScreen(s);
        }
        if (!available() || polling) return;
        boolean busy = active() != null || Compat.screen(mc) instanceof ChessScreen || Compat.screen(mc) instanceof ChallengeScreen || hasOutgoing();
        long interval = busy ? 1500 : 5000;
        long now = System.currentTimeMillis();
        if (!pollSoon && now - lastPoll < interval) return;
        pollSoon = false;
        lastPoll = now;
        polling = true;
        Friends.background(() -> {
            try {
                JsonObject r = Friends.get("api/games");
                List<Game> list = new ArrayList<>();
                JsonArray arr = r.has("games") ? r.getAsJsonArray("games") : new JsonArray();
                for (JsonElement e : arr) list.add(new Game(e.getAsJsonObject()));
                mc.execute(() -> update(mc, list));
            } catch (Exception ignored) {
                // nächster Versuch beim nächsten Abfragen
            } finally {
                polling = false;
            }
        });
    }

    private static boolean hasOutgoing() {
        String me = myUuid();
        for (Game g : games) if (g.invited() && me != null && me.equals(g.inviterUuid)) return true;
        return false;
    }

    /** Neue Liste übernehmen und Änderungen melden (Spiel-Thread) */
    private static void update(Minecraft mc, List<Game> list) {
        games = Collections.unmodifiableList(list);
        boolean quiet = firstPoll; // beim ersten Abruf nur merken, nicht alles noch einmal melden
        firstPoll = false;
        String me = myUuid();
        Screen screen = Compat.screen(mc);
        for (Game g : list) {
            String before = seenStatus.put(g.id, g.status);
            Integer movesBefore = seenMoves.put(g.id, g.moves.size());
            if (quiet) {
                if (g.incoming()) announcedInvites.add(g.id);
                continue;
            }
            boolean viewing = screen instanceof ChessScreen cs && cs.gameId().equals(g.id);
            if (g.incoming() && announcedInvites.add(g.id)) {
                chat(mc, "§d" + g.inviterName + "§7 fordert dich zu §fSchach§7 heraus §8(" + g.time / 60 + " Min, "
                        + colorText(g.color, true) + ")§7 – §f/myticgame annehmen§7 oder §f/myticgame ablehnen");
                Msg.actionBar("Schach-Herausforderung von " + g.inviterName);
            }
            if (g.active() && "invited".equals(before)) {
                // Partie beginnt: Herausforderer bekommt das Brett, wenn gerade nichts anderes offen ist
                if (screen == null && me != null && me.equals(g.inviterUuid)) Compat.setScreen(new ChessScreen(g.id));
                else if (!viewing) chat(mc, "§7Schach gegen §d" + g.opponentName() + "§7 beginnt – §f/myticgame§7 öffnet das Brett");
            }
            if (g.active() && movesBefore != null && g.moves.size() > movesBefore && g.myTurn() && !viewing) {
                Msg.actionBar(g.opponentName() + " hat gezogen – du bist dran (/myticgame)");
            }
            if (g.over() && before != null && !"over".equals(before) && !viewing) chat(mc, resultText(g));
            if ("declined".equals(g.status) && "invited".equals(before) && me != null && me.equals(g.inviterUuid))
                chat(mc, "§d" + g.inviteeName + "§7 hat die Schach-Herausforderung abgelehnt.");
            if ("expired".equals(g.status) && "invited".equals(before)) chat(mc, "§7Schach-Herausforderung abgelaufen.");
        }
    }

    public static String colorText(String color, boolean forInvitee) {
        if (color == null || color.equals("random")) return "Farbe: Zufall";
        boolean inviterWhite = color.equals("white");
        return (forInvitee ? (inviterWhite ? "du spielst Schwarz" : "du spielst Weiß") : (inviterWhite ? "du spielst Weiß" : "du spielst Schwarz"));
    }

    public static String resultText(Game g) {
        boolean meWhite = g.meWhite();
        String why = reasonText(g.reason);
        if ("1/2-1/2".equals(g.result)) return "§7Schach gegen §d" + g.opponentName() + "§7: §fRemis§7 (" + why + ")";
        boolean won = ("1-0".equals(g.result)) == meWhite;
        return "§7Schach gegen §d" + g.opponentName() + "§7: " + (won ? "§aGewonnen" : "§cVerloren") + "§7 (" + why + ")";
    }

    public static String reasonText(String reason) {
        if (reason == null) return "";
        return switch (reason) {
            case "checkmate" -> "Schachmatt";
            case "stalemate" -> "Patt";
            case "resign" -> "Aufgegeben";
            case "timeout" -> "Zeit abgelaufen";
            case "agreement" -> "Remis vereinbart";
            case "left" -> "Spiel verlassen";
            case "abandoned" -> "Verbindung verloren";
            default -> reason;
        };
    }

    /** Meldung im Chat (Mytic-Präfix) */
    public static void notice(String text) {
        chat(Minecraft.getInstance(), text);
    }

    private static void chat(Minecraft mc, String text) {
        if (mc.player != null) Compat.chat(mc, "§8[§dMytic§8] " + text);
    }

    // ------------------------------------------------------------------------------------------ Aktionen

    /** Herausfordern: time in Sekunden (180/300/600), color "random", "white" oder "black" */
    public static void invite(String name, int time, String color, Consumer<Game> ok, Consumer<String> error) {
        JsonObject body = new JsonObject();
        body.addProperty("to", name);
        body.addProperty("time", time);
        body.addProperty("color", color);
        send("api/games", body, ok, error);
    }

    public static void accept(String id, Consumer<Game> ok, Consumer<String> error) {
        send("api/games/" + id + "/accept", new JsonObject(), ok, error);
    }

    public static void decline(String id) {
        send("api/games/" + id + "/decline", new JsonObject(), null, null);
    }

    public static void cancel(String id) {
        send("api/games/" + id + "/cancel", new JsonObject(), null, null);
    }

    public static void move(String id, String uci, int ply, Consumer<String> error) {
        JsonObject body = new JsonObject();
        body.addProperty("move", uci);
        body.addProperty("ply", ply);
        send("api/games/" + id + "/move", body, null, error);
    }

    /** Aufgeben; left = Brett geschlossen, Welt/Server verlassen oder Minecraft beendet */
    public static void resign(String id, boolean left) {
        JsonObject body = new JsonObject();
        if (left) body.addProperty("reason", "left");
        send("api/games/" + id + "/resign", body, null, null);
    }

    /** Alle laufenden Partien verlassen (Welt/Server verlassen) */
    private static void leaveAll() {
        for (Game g : games) if (g.active()) resign(g.id, true);
    }

    /** Beim Beenden von Minecraft: laufende Partien sofort verlassen (blockierend, kurz) – sonst merkt der Server es nach 30 s */
    private static void leaveAllNow() {
        for (Game g : games) {
            if (!g.active()) continue;
            try {
                JsonObject body = new JsonObject();
                body.addProperty("reason", "left");
                Friends.post("api/games/" + g.id + "/resign", body);
            } catch (Exception ignored) {
                // dann eben über das fehlende Lebenszeichen
            }
        }
    }

    public static void draw(String id, String action) {
        JsonObject body = new JsonObject();
        body.addProperty("action", action);
        send("api/games/" + id + "/draw", body, null, null);
    }

    /** Anfrage senden; die Antwort (eine Partie) sofort in die Liste übernehmen, dann ok bzw. error (Spiel-Thread) */
    private static void send(String path, JsonObject body, Consumer<Game> ok, Consumer<String> error) {
        Minecraft mc = Minecraft.getInstance();
        Friends.background(() -> {
            try {
                JsonObject r = Friends.post(path, body);
                if (r.has("game") && r.get("game").isJsonObject()) {
                    Game g = new Game(r.getAsJsonObject("game"));
                    mc.execute(() -> {
                        replace(g);
                        if (ok != null) ok.accept(g);
                    });
                }
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "Fehler" : e.getMessage();
                mc.execute(() -> {
                    if (error != null) error.accept(msg);
                    else Msg.actionBar("Schach: " + msg);
                });
            }
            pollSoon = true;
        });
    }

    private static void replace(Game g) {
        List<Game> list = new ArrayList<>(games);
        list.removeIf(x -> x.id.equals(g.id));
        list.add(0, g);
        games = Collections.unmodifiableList(list);
        seenStatus.put(g.id, g.status);
        seenMoves.put(g.id, g.moves.size());
    }

    // ------------------------------------------------------------------------------------------ JSON

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }
}

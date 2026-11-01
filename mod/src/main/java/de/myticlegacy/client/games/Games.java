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
        /** "chess" oder "xo" (X und O); bei X und O sind white/black = X/O */
        public final String kind;
        public final int rounds, moveTime;
        /** X und O: Brett (9 Zeichen „.XO“), Runde, wer dran ist ("x"/"o"), Punkte, Restzeit des Zugs (ms, -1 = ohne Limit) */
        public final String xoBoard, xoTurn;
        public final int xoRound, xoScoreX, xoScoreO;
        public final long xoMoveLeft;
        public final List<XoResult> xoResults;

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
            kind = o.has("kind") && !o.get("kind").isJsonNull() ? o.get("kind").getAsString() : "chess";
            rounds = o.has("rounds") && o.get("rounds").isJsonPrimitive() ? o.get("rounds").getAsInt() : 1;
            moveTime = o.has("moveTime") && o.get("moveTime").isJsonPrimitive() ? o.get("moveTime").getAsInt() : 0;
            JsonObject xo = obj(o, "xo");
            // Server schreibt „x“/„o“ klein, die Anzeige arbeitet mit „X“/„O“
            xoBoard = xo != null && str(xo, "board") != null ? str(xo, "board").toUpperCase(java.util.Locale.ROOT) : ".........";
            xoTurn = xo != null && str(xo, "turn") != null ? str(xo, "turn") : "x";
            xoRound = xo != null && xo.has("round") ? xo.get("round").getAsInt() : 1;
            JsonObject score = obj(xo, "score");
            xoScoreX = score != null && score.has("x") ? score.get("x").getAsInt() : 0;
            xoScoreO = score != null && score.has("o") ? score.get("o").getAsInt() : 0;
            xoMoveLeft = xo != null && xo.has("moveLeft") && !xo.get("moveLeft").isJsonNull() ? xo.get("moveLeft").getAsLong() : -1;
            List<XoResult> res = new ArrayList<>();
            if (xo != null && xo.has("results") && xo.get("results").isJsonArray()) {
                for (JsonElement e : xo.getAsJsonArray("results")) {
                    JsonObject r = e.getAsJsonObject();
                    int[] line = new int[0];
                    if (r.has("line") && r.get("line").isJsonArray()) {
                        JsonArray a = r.getAsJsonArray("line");
                        line = new int[a.size()];
                        for (int i = 0; i < line.length; i++) line[i] = a.get(i).getAsInt();
                    }
                    String endBoard = str(r, "board");
                    res.add(new XoResult(str(r, "winner"), str(r, "reason"), line, endBoard == null ? null : endBoard.toUpperCase(java.util.Locale.ROOT)));
                }
            }
            xoResults = Collections.unmodifiableList(res);
        }

        public boolean xo() {
            return "xo".equals(kind);
        }

        /** Mein Symbol bei X und O ("x" = white) */
        public String mySymbol() {
            return meWhite() ? "x" : "o";
        }

        /** Restzeit des aktuellen Zugs jetzt (ms) oder -1 ohne Limit */
        public long moveLeft() {
            if (xoMoveLeft < 0 || !active()) return -1;
            return Math.max(0, xoMoveLeft - (System.currentTimeMillis() - fetchedAt));
        }

        /** Spielname für Meldungen */
        public String gameName() {
            return xo() ? "X und O" : "Schach";
        }

        /** Einstellungen kurz, z. B. „5 Min“ oder „Best of 3, 10 s/Zug“ */
        public String settings() {
            if (!xo()) return time / 60 + " Min";
            return (rounds == 1 ? "1 Runde" : "Best of " + rounds) + ", " + (moveTime > 0 ? moveTime + " s/Zug" : "ohne Zeitlimit");
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
            if (xo()) return active() && xoTurn.equals(mySymbol());
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

    /** Ergebnis einer X-und-O-Runde: Gewinner ("x"/"o"/null), Grund, Gewinnreihe, Endbrett */
    public record XoResult(String winner, String reason, int[] line, String board) {
    }

    /** Fenster für eine Partie (Schach oder X und O) */
    public static Screen screenFor(Game g) {
        return g.xo() ? new XoScreen(g.id) : new ChessScreen(g.id);
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
        Screen open = Compat.screen(mc);
        boolean busy = active() != null || open instanceof ChessScreen || open instanceof XoScreen || open instanceof ChallengeScreen || hasOutgoing();
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
            boolean viewing = (screen instanceof ChessScreen cs && cs.gameId().equals(g.id)) || (screen instanceof XoScreen xs && xs.gameId().equals(g.id));
            if (g.incoming() && announcedInvites.add(g.id)) {
                chat(mc, "§d" + g.inviterName + "§7 fordert dich zu §f" + g.gameName() + "§7 heraus §8(" + g.settings() + ", "
                        + colorText(g, true) + ")§7 – §f/myticgame annehmen§7 oder §f/myticgame ablehnen §8(1 Minute Zeit)");
                Msg.actionBar(g.gameName() + "-Herausforderung von " + g.inviterName);
            }
            if (g.active() && "invited".equals(before)) {
                // Partie beginnt: Herausforderer bekommt das Brett, wenn gerade nichts anderes offen ist
                if (screen == null && me != null && me.equals(g.inviterUuid)) Compat.setScreen(screenFor(g));
                else if (!viewing) chat(mc, "§7" + g.gameName() + " gegen §d" + g.opponentName() + "§7 beginnt – §f/myticgame§7 öffnet das Spiel");
            }
            if (g.active() && movesBefore != null && g.moves.size() > movesBefore && g.myTurn() && !viewing) {
                Msg.actionBar(g.opponentName() + " hat gezogen – du bist dran (/myticgame)");
            }
            if (g.over() && before != null && !"over".equals(before) && !viewing) chat(mc, resultText(g));
            if ("declined".equals(g.status) && "invited".equals(before) && me != null && me.equals(g.inviterUuid))
                chat(mc, "§d" + g.inviteeName + "§7 hat die " + g.gameName() + "-Herausforderung abgelehnt.");
            if ("expired".equals(g.status) && "invited".equals(before))
                chat(mc, me != null && me.equals(g.inviterUuid) ? "§d" + g.inviteeName + "§7 hat nicht innerhalb einer Minute angenommen – Herausforderung abgelehnt."
                        : "§7Herausforderung von §d" + g.inviterName + "§7 ist abgelaufen.");
        }
    }

    public static String colorText(Game g, boolean forInvitee) {
        String color = g.color;
        if (color == null || color.equals("random")) return g.xo() ? "Symbol: Zufall" : "Farbe: Zufall";
        boolean inviterWhite = color.equals("white");
        boolean meWhite = forInvitee != inviterWhite;
        return g.xo() ? (meWhite ? "du bist X" : "du bist O") : (meWhite ? "du spielst Weiß" : "du spielst Schwarz");
    }

    public static String resultText(Game g) {
        boolean meWhite = g.meWhite();
        String why = reasonText(g.reason);
        String score = g.xo() ? " " + (meWhite ? g.xoScoreX + ":" + g.xoScoreO : g.xoScoreO + ":" + g.xoScoreX) : "";
        if ("1/2-1/2".equals(g.result)) return "§7" + g.gameName() + " gegen §d" + g.opponentName() + "§7: §fRemis§7 (" + why + ")";
        boolean won = ("1-0".equals(g.result)) == meWhite;
        return "§7" + g.gameName() + " gegen §d" + g.opponentName() + "§7: " + (won ? "§aGewonnen" : "§cVerloren") + score + "§7 (" + why + ")";
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
            case "rounds" -> "Runden";
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

    /** X und O herausfordern: rounds 1/3/5, moveTime Sekunden pro Zug (0 = ohne), color "random"/"white"(X)/"black"(O) */
    public static void inviteXo(String name, int rounds, int moveTime, String color, Consumer<Game> ok, Consumer<String> error) {
        JsonObject body = new JsonObject();
        body.addProperty("to", name);
        body.addProperty("game", "xo");
        body.addProperty("rounds", rounds);
        body.addProperty("moveTime", moveTime);
        body.addProperty("color", color);
        send("api/games", body, ok, error);
    }

    /** X und O: Feld 0–8 setzen; ply = Zeichen auf dem Brett in dieser Runde */
    public static void place(String id, int cell, int ply, Consumer<String> error) {
        JsonObject body = new JsonObject();
        body.addProperty("move", cell);
        body.addProperty("ply", ply);
        send("api/games/" + id + "/move", body, null, error);
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

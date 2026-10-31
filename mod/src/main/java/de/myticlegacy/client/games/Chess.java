package de.myticlegacy.client.games;

import java.util.ArrayList;
import java.util.List;

/**
 * Schachregeln (wie friends-server/src/chess.js): Brett als 64 Felder, Index 0 = a8, 63 = h1. Figuren als Zeichen,
 * Weiß groß (KQRBNP), Schwarz klein, leer = 0. Rochade, en passant, Umwandlung, Schach, Matt und Patt.
 * Unveränderlich: jeder Zug liefert eine neue Stellung. Der Server prüft dieselben Regeln noch einmal.
 */
public final class Chess {
    private static final int[][] KNIGHT = {{1, 2}, {2, 1}, {-1, 2}, {-2, 1}, {1, -2}, {2, -1}, {-1, -2}, {-2, -1}};
    private static final int[][] KING = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] ROOK = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public final char[] board = new char[64];
    public boolean whiteToMove = true;
    /** Rochaderechte: weiß kurz/lang, schwarz kurz/lang */
    public boolean wK, wQ, bK, bQ;
    /** en-passant-Zielfeld oder -1 */
    public int ep = -1;

    /** Ein Zug: von, nach, Umwandlung ('q', 'r', 'b', 'n' oder 0) */
    public record Move(int from, int to, char promo) {
        public String uci() {
            return square(from) + square(to) + (promo == 0 ? "" : String.valueOf(promo));
        }
    }

    public static Chess start() {
        return fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    /** Stellung aus FEN (nur Brett, Zugrecht, Rochade, en passant) */
    public static Chess fromFen(String fen) {
        Chess c = new Chess();
        String[] parts = fen.trim().split("\\s+");
        int i = 0;
        for (char ch : parts[0].toCharArray()) {
            if (ch == '/') continue;
            if (Character.isDigit(ch)) i += ch - '0';
            else if (i < 64) c.board[i++] = ch;
        }
        c.whiteToMove = parts.length < 2 || parts[1].equals("w");
        String castle = parts.length > 2 ? parts[2] : "-";
        c.wK = castle.contains("K");
        c.wQ = castle.contains("Q");
        c.bK = castle.contains("k");
        c.bQ = castle.contains("q");
        c.ep = parts.length > 3 && !parts[3].equals("-") ? index(parts[3]) : -1;
        return c;
    }

    public static String square(int i) {
        return "" + (char) ('a' + (i & 7)) + (char) ('8' - (i >> 3));
    }

    public static int index(String sq) {
        if (sq == null || sq.length() < 2) return -1;
        int f = sq.charAt(0) - 'a', r = '8' - sq.charAt(1);
        return f < 0 || f > 7 || r < 0 || r > 7 ? -1 : r * 8 + f;
    }

    public static boolean white(char p) {
        return Character.isUpperCase(p);
    }

    private static int at(int r, int f) {
        return r < 0 || r > 7 || f < 0 || f > 7 ? -1 : r * 8 + f;
    }

    private Chess copy() {
        Chess c = new Chess();
        System.arraycopy(board, 0, c.board, 0, 64);
        c.whiteToMove = whiteToMove;
        c.wK = wK;
        c.wQ = wQ;
        c.bK = bK;
        c.bQ = bQ;
        c.ep = ep;
        return c;
    }

    /** Wird Feld sq von der Seite byWhite angegriffen? */
    public boolean attacked(int sq, boolean byWhite) {
        int r = sq >> 3, f = sq & 7, dir = byWhite ? 1 : -1;
        char pawn = byWhite ? 'P' : 'p', knight = byWhite ? 'N' : 'n', king = byWhite ? 'K' : 'k';
        char queen = byWhite ? 'Q' : 'q', bishop = byWhite ? 'B' : 'b', rook = byWhite ? 'R' : 'r';
        for (int df : new int[]{-1, 1}) {
            int s = at(r + dir, f + df);
            if (s >= 0 && board[s] == pawn) return true;
        }
        for (int[] d : KNIGHT) {
            int s = at(r + d[0], f + d[1]);
            if (s >= 0 && board[s] == knight) return true;
        }
        for (int[] d : KING) {
            boolean diagonal = d[0] != 0 && d[1] != 0;
            for (int k = 1; k < 8; k++) {
                int s = at(r + d[0] * k, f + d[1] * k);
                if (s < 0) break;
                char q = board[s];
                if (q == 0) continue;
                if (q == queen || (q == king && k == 1) || (diagonal ? q == bishop : q == rook)) return true;
                break;
            }
        }
        return false;
    }

    public int king(boolean whiteKing) {
        char k = whiteKing ? 'K' : 'k';
        for (int i = 0; i < 64; i++) if (board[i] == k) return i;
        return -1;
    }

    public boolean inCheck() {
        int k = king(whiteToMove);
        return k >= 0 && attacked(k, !whiteToMove);
    }

    private List<Move> pseudo() {
        List<Move> out = new ArrayList<>();
        boolean w = whiteToMove;
        for (int i = 0; i < 64; i++) {
            char p = board[i];
            if (p == 0 || white(p) != w) continue;
            int r = i >> 3, f = i & 7;
            char t = Character.toUpperCase(p);
            if (t == 'P') {
                int d = w ? -1 : 1, s1 = at(r + d, f);
                if (s1 >= 0 && board[s1] == 0) {
                    pawnMove(out, i, s1);
                    int s2 = at(r + 2 * d, f);
                    if ((w ? r == 6 : r == 1) && board[s2] == 0) out.add(new Move(i, s2, (char) 0));
                }
                for (int df : new int[]{-1, 1}) {
                    int s = at(r + d, f + df);
                    if (s >= 0 && ((board[s] != 0 && white(board[s]) != w) || s == ep)) pawnMove(out, i, s);
                }
            } else if (t == 'N' || t == 'K') {
                for (int[] d : t == 'N' ? KNIGHT : KING) {
                    int s = at(r + d[0], f + d[1]);
                    if (s >= 0 && (board[s] == 0 || white(board[s]) != w)) out.add(new Move(i, s, (char) 0));
                }
                if (t == 'K' && i == (w ? 60 : 4) && !attacked(i, !w)) {
                    if ((w ? wK : bK) && board[i + 1] == 0 && board[i + 2] == 0 && !attacked(i + 1, !w)) out.add(new Move(i, i + 2, (char) 0));
                    if ((w ? wQ : bQ) && board[i - 1] == 0 && board[i - 2] == 0 && board[i - 3] == 0 && !attacked(i - 1, !w))
                        out.add(new Move(i, i - 2, (char) 0));
                }
            } else {
                int[][] dirs = t == 'B' ? BISHOP : t == 'R' ? ROOK : KING;
                for (int[] d : dirs) {
                    for (int k = 1; k < 8; k++) {
                        int s = at(r + d[0] * k, f + d[1] * k);
                        if (s < 0) break;
                        if (board[s] != 0) {
                            if (white(board[s]) != w) out.add(new Move(i, s, (char) 0));
                            break;
                        }
                        out.add(new Move(i, s, (char) 0));
                    }
                }
            }
        }
        return out;
    }

    private static void pawnMove(List<Move> out, int from, int to) {
        if (to < 8 || to > 55) for (char p : new char[]{'q', 'r', 'b', 'n'}) out.add(new Move(from, to, p));
        else out.add(new Move(from, to, (char) 0));
    }

    /** Zug ausführen ohne Prüfung (neue Stellung) */
    public Chess apply(Move m) {
        Chess n = copy();
        char p = n.board[m.from];
        boolean w = white(p);
        char t = Character.toUpperCase(p);
        if (t == 'P' && m.to == ep && n.board[m.to] == 0) n.board[m.to + (w ? 8 : -8)] = 0;
        n.board[m.to] = p;
        n.board[m.from] = 0;
        if (t == 'K' && Math.abs(m.to - m.from) == 2) {
            int rook = m.to > m.from ? m.from + 3 : m.from - 4, dst = m.to > m.from ? m.to - 1 : m.to + 1;
            n.board[dst] = n.board[rook];
            n.board[rook] = 0;
        }
        if (t == 'P' && (m.to < 8 || m.to > 55)) {
            char promo = m.promo == 0 ? 'q' : m.promo;
            n.board[m.to] = w ? Character.toUpperCase(promo) : promo;
        }
        if (t == 'K') {
            if (w) n.wK = n.wQ = false;
            else n.bK = n.bQ = false;
        }
        if (m.from == 63 || m.to == 63) n.wK = false;
        if (m.from == 56 || m.to == 56) n.wQ = false;
        if (m.from == 7 || m.to == 7) n.bK = false;
        if (m.from == 0 || m.to == 0) n.bQ = false;
        n.ep = t == 'P' && Math.abs(m.to - m.from) == 16 ? (m.from + m.to) / 2 : -1;
        n.whiteToMove = !whiteToMove;
        return n;
    }

    /** Alle erlaubten Züge der Seite am Zug */
    public List<Move> legal() {
        List<Move> out = new ArrayList<>();
        for (Move m : pseudo()) {
            Chess n = apply(m);
            int k = n.king(whiteToMove);
            if (k >= 0 && !n.attacked(k, !whiteToMove)) out.add(m);
        }
        return out;
    }

    /** Erlaubter Zug von → nach (Umwandlung in Dame), sonst null */
    public Move find(int from, int to) {
        for (Move m : legal()) if (m.from == from && m.to == to && (m.promo == 0 || m.promo == 'q')) return m;
        return null;
    }

    public Move parse(String uci) {
        if (uci == null || uci.length() < 4) return null;
        int from = index(uci.substring(0, 2)), to = index(uci.substring(2, 4));
        char promo = uci.length() > 4 ? uci.charAt(4) : 0;
        for (Move m : legal()) if (m.from == from && m.to == to && (m.promo == promo || (promo == 0 && m.promo == 'q'))) return m;
        return null;
    }

    /** Kurzschreibweise auf Deutsch (S, L, T, D, K), z. B. „Sf3“, „exd5“, „O-O“, „e8=D+“ */
    public String san(Move m) {
        char p = board[m.from];
        char t = Character.toUpperCase(p);
        boolean capture = board[m.to] != 0 || (t == 'P' && m.to == ep);
        String s;
        if (t == 'K' && Math.abs(m.to - m.from) == 2) s = m.to > m.from ? "O-O" : "O-O-O";
        else {
            String letter = switch (t) {
                case 'K' -> "K";
                case 'Q' -> "D";
                case 'R' -> "T";
                case 'B' -> "L";
                case 'N' -> "S";
                default -> capture ? String.valueOf((char) ('a' + (m.from & 7))) : "";
            };
            s = letter + (capture ? "x" : "") + square(m.to);
            if (t == 'P' && (m.to < 8 || m.to > 55)) s += "=" + switch (m.promo == 0 ? 'q' : m.promo) {
                case 'r' -> "T";
                case 'b' -> "L";
                case 'n' -> "S";
                default -> "D";
            };
        }
        Chess after = apply(m);
        if (after.inCheck()) s += after.legal().isEmpty() ? "#" : "+";
        return s;
    }
}

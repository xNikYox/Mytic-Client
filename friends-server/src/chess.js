// Schachregeln für die Partien zwischen Freunden (reine Funktionen, ohne Abhängigkeiten).
// Brett als Feld mit 64 Einträgen: Index 0 = a8, 7 = h8, 56 = a1, 63 = h1. Figur: { c: "w"|"b", t: "K|Q|R|B|N|P" } oder null.
// Zustand: { board, turn, castling: { K, Q, k, q }, ep (Index des En-passant-Felds oder -1), half, full }.

const FILES = "abcdefgh";
const KNIGHT = [[-2, -1], [-2, 1], [-1, -2], [-1, 2], [1, -2], [1, 2], [2, -1], [2, 1]];
const KING = [[-1, -1], [-1, 0], [-1, 1], [0, -1], [0, 1], [1, -1], [1, 0], [1, 1]];
const ROOK = [[-1, 0], [1, 0], [0, -1], [0, 1]];
const BISHOP = [[-1, -1], [-1, 1], [1, -1], [1, 1]];
const PROMOTIONS = ["Q", "R", "B", "N"];

export const START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

/** Feldname ("e4") → Index, ungültig → -1. */
export function squareIndex(name) {
  if (typeof name !== "string" || !/^[a-h][1-8]$/.test(name)) return -1;
  return (8 - Number(name[1])) * 8 + FILES.indexOf(name[0]);
}

/** Index → Feldname ("e4"). */
export function squareName(index) {
  return FILES[index % 8] + (8 - (index >> 3));
}

/** Zustand aus FEN; ungültig → null. */
export function parseFen(fen) {
  if (typeof fen !== "string") return null;
  const parts = fen.trim().split(/\s+/);
  if (parts.length < 4) return null;
  const rows = parts[0].split("/");
  if (rows.length !== 8) return null;
  const board = [];
  for (const row of rows) {
    let n = 0;
    for (const ch of row) {
      if (/[1-8]/.test(ch)) {
        for (let i = 0; i < Number(ch); i++) board.push(null);
        n += Number(ch);
      } else if (/[KQRBNPkqrbnp]/.test(ch)) {
        board.push({ c: ch === ch.toUpperCase() ? "w" : "b", t: ch.toUpperCase() });
        n++;
      } else {
        return null;
      }
    }
    if (n !== 8) return null;
  }
  if (parts[1] !== "w" && parts[1] !== "b") return null;
  if (!/^(-|K?Q?k?q?)$/.test(parts[2]) || parts[2] === "") return null;
  const castling = { K: parts[2].includes("K"), Q: parts[2].includes("Q"), k: parts[2].includes("k"), q: parts[2].includes("q") };
  const ep = parts[3] === "-" ? -1 : squareIndex(parts[3]);
  if (parts[3] !== "-" && ep < 0) return null;
  const half = parts.length > 4 ? Number(parts[4]) : 0;
  const full = parts.length > 5 ? Number(parts[5]) : 1;
  if (!Number.isInteger(half) || half < 0 || !Number.isInteger(full) || full < 1) return null;
  return { board, turn: parts[1], castling, ep, half, full };
}

/** Zustand → FEN. */
export function toFen(state) {
  const rows = [];
  for (let r = 0; r < 8; r++) {
    let row = "";
    let empty = 0;
    for (let f = 0; f < 8; f++) {
      const p = state.board[r * 8 + f];
      if (!p) {
        empty++;
        continue;
      }
      if (empty) row += empty;
      empty = 0;
      row += p.c === "w" ? p.t : p.t.toLowerCase();
    }
    if (empty) row += empty;
    rows.push(row);
  }
  const c = state.castling;
  const castling = (c.K ? "K" : "") + (c.Q ? "Q" : "") + (c.k ? "k" : "") + (c.q ? "q" : "") || "-";
  return `${rows.join("/")} ${state.turn} ${castling} ${state.ep >= 0 ? squareName(state.ep) : "-"} ${state.half} ${state.full}`;
}

/** Ausgangsstellung. */
export function initialState() {
  return parseFen(START_FEN);
}

/** Wird Feld sq von Farbe by angegriffen? */
export function isAttacked(board, sq, by) {
  const r = sq >> 3;
  const f = sq % 8;
  // Bauern: weiße schlagen nach oben (kleinere Reihe), stehen also eine Reihe darunter
  const pr = by === "w" ? r + 1 : r - 1;
  if (pr >= 0 && pr < 8) {
    for (const df of [-1, 1]) {
      const pf = f + df;
      if (pf < 0 || pf > 7) continue;
      const p = board[pr * 8 + pf];
      if (p && p.c === by && p.t === "P") return true;
    }
  }
  for (const [dr, df] of KNIGHT) {
    const nr = r + dr;
    const nf = f + df;
    if (nr < 0 || nr > 7 || nf < 0 || nf > 7) continue;
    const p = board[nr * 8 + nf];
    if (p && p.c === by && p.t === "N") return true;
  }
  for (const [dr, df] of KING) {
    const nr = r + dr;
    const nf = f + df;
    if (nr < 0 || nr > 7 || nf < 0 || nf > 7) continue;
    const p = board[nr * 8 + nf];
    if (p && p.c === by && p.t === "K") return true;
  }
  for (const [dirs, kinds] of [[ROOK, "RQ"], [BISHOP, "BQ"]]) {
    for (const [dr, df] of dirs) {
      let nr = r + dr;
      let nf = f + df;
      while (nr >= 0 && nr < 8 && nf >= 0 && nf < 8) {
        const p = board[nr * 8 + nf];
        if (p) {
          if (p.c === by && kinds.includes(p.t)) return true;
          break;
        }
        nr += dr;
        nf += df;
      }
    }
  }
  return false;
}

function kingSquare(board, color) {
  for (let i = 0; i < 64; i++) {
    const p = board[i];
    if (p && p.c === color && p.t === "K") return i;
  }
  return -1;
}

/** Steht die Seite am Zug im Schach? */
export function inCheck(state) {
  const k = kingSquare(state.board, state.turn);
  return k >= 0 && isAttacked(state.board, k, state.turn === "w" ? "b" : "w");
}

/** Mögliche Züge ohne Prüfung, ob der eigene König danach im Schach steht. Zug: { from, to, promo }. */
function pseudoMoves(state) {
  const { board, turn } = state;
  const enemy = turn === "w" ? "b" : "w";
  const moves = [];
  const add = (from, to) => {
    const toRow = to >> 3;
    if (board[from].t === "P" && (toRow === 0 || toRow === 7)) {
      for (const promo of PROMOTIONS) moves.push({ from, to, promo });
    } else {
      moves.push({ from, to, promo: null });
    }
  };
  for (let sq = 0; sq < 64; sq++) {
    const p = board[sq];
    if (!p || p.c !== turn) continue;
    const r = sq >> 3;
    const f = sq % 8;
    if (p.t === "P") {
      const dir = turn === "w" ? -1 : 1;
      const startRow = turn === "w" ? 6 : 1;
      const one = sq + dir * 8;
      if (r + dir >= 0 && r + dir < 8 && !board[one]) {
        add(sq, one);
        const two = sq + dir * 16;
        if (r === startRow && !board[two]) add(sq, two);
      }
      for (const df of [-1, 1]) {
        const nf = f + df;
        const nr = r + dir;
        if (nf < 0 || nf > 7 || nr < 0 || nr > 7) continue;
        const to = nr * 8 + nf;
        const target = board[to];
        if ((target && target.c === enemy) || to === state.ep) add(sq, to);
      }
    } else if (p.t === "N" || p.t === "K") {
      for (const [dr, df] of p.t === "N" ? KNIGHT : KING) {
        const nr = r + dr;
        const nf = f + df;
        if (nr < 0 || nr > 7 || nf < 0 || nf > 7) continue;
        const target = board[nr * 8 + nf];
        if (!target || target.c === enemy) add(sq, nr * 8 + nf);
      }
    } else {
      const dirs = p.t === "R" ? ROOK : p.t === "B" ? BISHOP : [...ROOK, ...BISHOP];
      for (const [dr, df] of dirs) {
        let nr = r + dr;
        let nf = f + df;
        while (nr >= 0 && nr < 8 && nf >= 0 && nf < 8) {
          const target = board[nr * 8 + nf];
          if (target && target.c === turn) break;
          add(sq, nr * 8 + nf);
          if (target) break;
          nr += dr;
          nf += df;
        }
      }
    }
  }
  // Rochade: König und Turm auf Ausgangsfeldern, Felder dazwischen frei, König steht/zieht nicht durch Schach
  const home = turn === "w" ? 60 : 4;
  const king = board[home];
  if (king && king.c === turn && king.t === "K") {
    const rights = turn === "w" ? { short: state.castling.K, long: state.castling.Q } : { short: state.castling.k, long: state.castling.q };
    const rook = (i) => board[i] && board[i].c === turn && board[i].t === "R";
    if (rights.short && rook(home + 3) && !board[home + 1] && !board[home + 2]
      && !isAttacked(board, home, enemy) && !isAttacked(board, home + 1, enemy) && !isAttacked(board, home + 2, enemy)) {
      moves.push({ from: home, to: home + 2, promo: null });
    }
    if (rights.long && rook(home - 4) && !board[home - 1] && !board[home - 2] && !board[home - 3]
      && !isAttacked(board, home, enemy) && !isAttacked(board, home - 1, enemy) && !isAttacked(board, home - 2, enemy)) {
      moves.push({ from: home, to: home - 2, promo: null });
    }
  }
  return moves;
}

/** Zug ausführen (ohne Prüfung) → neuer Zustand. */
function makeMove(state, move) {
  const board = state.board.slice();
  const piece = board[move.from];
  const captured = board[move.to];
  board[move.to] = move.promo ? { c: piece.c, t: move.promo } : piece;
  board[move.from] = null;
  let ep = -1;
  if (piece.t === "P") {
    // en passant: geschlagener Bauer steht neben dem Ausgangsfeld
    if (move.to === state.ep && !captured) board[(move.from >> 3) * 8 + (move.to % 8)] = null;
    if (Math.abs(move.to - move.from) === 16) ep = (move.from + move.to) / 2;
  }
  if (piece.t === "K" && Math.abs(move.to - move.from) === 2) {
    // Rochade: Turm mitziehen
    const short = move.to > move.from;
    const rookFrom = short ? move.from + 3 : move.from - 4;
    const rookTo = short ? move.from + 1 : move.from - 1;
    board[rookTo] = board[rookFrom];
    board[rookFrom] = null;
  }
  const castling = { ...state.castling };
  for (const sq of [move.from, move.to]) {
    if (sq === 60) castling.K = castling.Q = false;
    if (sq === 4) castling.k = castling.q = false;
    if (sq === 63) castling.K = false;
    if (sq === 56) castling.Q = false;
    if (sq === 7) castling.k = false;
    if (sq === 0) castling.q = false;
  }
  return {
    board,
    turn: state.turn === "w" ? "b" : "w",
    castling,
    ep,
    half: piece.t === "P" || captured ? 0 : state.half + 1,
    full: state.turn === "b" ? state.full + 1 : state.full,
  };
}

/** Erlaubte Züge als Objekte { from, to, promo, next } (next = Zustand danach). */
function legal(state) {
  const result = [];
  for (const move of pseudoMoves(state)) {
    const next = makeMove(state, move);
    const k = kingSquare(next.board, state.turn);
    if (k >= 0 && isAttacked(next.board, k, next.turn)) continue;
    result.push({ ...move, next });
  }
  return result;
}

function uci(move) {
  return squareName(move.from) + squareName(move.to) + (move.promo ? move.promo.toLowerCase() : "");
}

/** Erlaubte Züge in UCI-Schreibweise ("e2e4", "e7e8q"). */
export function legalMoves(state) {
  return legal(state).map(uci);
}

/** Zug in UCI-Schreibweise ausführen → neuer Zustand, unerlaubt → null. Umwandlung ohne Angabe: Dame. */
export function applyUci(state, text) {
  const m = typeof text === "string" && text.toLowerCase().match(/^([a-h][1-8])([a-h][1-8])([qrbn]?)$/);
  if (!m) return null;
  const from = squareIndex(m[1]);
  const to = squareIndex(m[2]);
  const candidates = legal(state).filter((move) => move.from === from && move.to === to);
  if (!candidates.length) return null;
  if (!candidates[0].promo) return m[3] ? null : candidates[0].next;
  const promo = (m[3] || "q").toUpperCase();
  const move = candidates.find((c) => c.promo === promo);
  return move ? move.next : null;
}

/** "ok", "checkmate" (Seite am Zug ist matt) oder "stalemate" (patt). */
export function status(state) {
  if (legal(state).length) return "ok";
  return inCheck(state) ? "checkmate" : "stalemate";
}

/** Anzahl der Stellungen nach depth Halbzügen (zum Prüfen der Zuggenerierung). */
export function perft(state, depth) {
  if (depth === 0) return 1;
  const moves = legal(state);
  if (depth === 1) return moves.length;
  let n = 0;
  for (const move of moves) n += perft(move.next, depth - 1);
  return n;
}

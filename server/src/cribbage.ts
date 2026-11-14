// Two-player six-card cribbage to 121: a line-for-line port of the app's rules engine
// (shared/src/commonMain/kotlin/com/geoguy89/cribbage/game/Rules.kt and Scoring.kt). The JSON shape of GameState is the
// app's own, so the app can read a game straight from the server. Cards are numbers: suit * 13 + rank - 1.

export type Kind =
  | "FIFTEEN" | "PAIR" | "RUN" | "FLUSH" | "NOBS" | "HEELS" | "GO" | "LAST_CARD" | "THIRTY_ONE" | "SAYS_GO";

export interface ScoreItem { kind: Kind; points: number; cards: number[] }
export interface HandScore { items: ScoreItem[] }
export interface Played { player: number; card: number }
export interface LogEntry { seq: number; hand: number; player: number; item: ScoreItem; count: number }
export interface ShowCount { player: number; crib: boolean; cards: number[]; score: HandScore }
export interface Show { hand: number; dealer: number; starter: number; startScores: number[]; counts: ShowCount[] }
export type Phase = "DISCARD" | "PEG" | "OVER";

export interface GameState {
  scores: number[];
  prevScores: number[];
  dealer: number;
  hand: number;
  phase: Phase;
  hands: number[][];
  kept: number[][];
  crib: number[];
  starter: number | null;
  deck: number[];
  turn: number;
  count: number;
  pile: Played[];
  played: Played[];
  lastPlayer: number;
  go: boolean[];
  log: LogEntry[];
  seq: number;
  lastShow: Show | null;
  winner: number | null;
  cutForDeal: number[];
}

export const GAME_POINTS = 121;
export const SKUNK_LINE = 91;
export const DOUBLE_SKUNK_LINE = 61;

export const rank = (c: number) => (c % 13) + 1;
export const suit = (c: number) => Math.floor(c / 13);
export const value = (c: number) => Math.min(10, rank(c));
const byCard = (a: number, b: number) => rank(a) - rank(b) || suit(a) - suit(b);
const other = (p: number) => 1 - p;

export type Rng = () => number; // [0, 1)

export function shuffled(rng: Rng): number[] {
  const d = Array.from({ length: 52 }, (_, i) => i);
  for (let i = d.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [d[i], d[j]] = [d[j], d[i]];
  }
  return d;
}

/** A cryptographically random [0, 1) for real games. */
export const cryptoRng: Rng = () => crypto.getRandomValues(new Uint32Array(1))[0] / 0x1_0000_0000;

// ---- scoring --------------------------------------------------------------------------------------------------------

const ids = (cards: number[]) => cards.join(",");

export function fifteens(cards: number[]): number[][] {
  const out: number[][] = [];
  const n = cards.length;
  for (let mask = 1; mask < 1 << n; mask++) {
    let sum = 0;
    for (let i = 0; i < n; i++) if (mask & (1 << i)) sum += value(cards[i]);
    if (sum === 15) out.push(cards.filter((_, i) => mask & (1 << i)));
  }
  return out.sort((a, b) => a.length - b.length || (ids(a) < ids(b) ? -1 : ids(a) > ids(b) ? 1 : 0));
}

export function pairs(cards: number[]): number[][] {
  const groups = new Map<number, number[]>();
  for (const c of cards) groups.set(rank(c), [...(groups.get(rank(c)) ?? []), c]);
  return [...groups.values()].filter((g) => g.length >= 2).sort((a, b) => rank(a[0]) - rank(b[0]));
}

export const pairPoints = (n: number) => n * (n - 1);

export function isRun(cards: number[]): boolean {
  if (cards.length < 3) return false;
  const r = cards.map(rank).sort((a, b) => a - b);
  for (let i = 1; i < r.length; i++) if (r[i] !== r[i - 1] + 1) return false;
  return true;
}

const bits = (v: number) => { let c = 0; while (v) { v &= v - 1; c++; } return c; };

export function runs(cards: number[]): number[][] {
  const n = cards.length;
  for (let size = n; size >= 3; size--) {
    const found: number[][] = [];
    for (let mask = 0; mask < 1 << n; mask++) {
      if (bits(mask) !== size) continue;
      const pick = cards.filter((_, i) => mask & (1 << i)).sort((a, b) => rank(a) - rank(b));
      if (isRun(pick)) found.push(pick);
    }
    if (found.length) return found.sort((a, b) => (ids(a) < ids(b) ? -1 : ids(a) > ids(b) ? 1 : 0));
  }
  return [];
}

export function flush(hand: number[], starter: number | null, crib: boolean): number[] | null {
  if (hand.length < 4) return null;
  const s = suit(hand[0]);
  if (hand.some((c) => suit(c) !== s)) return null;
  if (starter !== null && suit(starter) === s) return [...hand, starter];
  return crib ? null : hand;
}

export function nobs(hand: number[], starter: number | null): number | null {
  if (starter === null) return null;
  return hand.find((c) => rank(c) === 11 && suit(c) === suit(starter)) ?? null;
}

export function score(hand: number[], starter: number | null, crib = false): HandScore {
  const all = starter === null ? hand : [...hand, starter];
  const items: ScoreItem[] = [];
  for (const f of fifteens(all)) items.push({ kind: "FIFTEEN", points: 2, cards: f });
  for (const p of pairs(all)) items.push({ kind: "PAIR", points: pairPoints(p.length), cards: p });
  for (const r of runs(all)) items.push({ kind: "RUN", points: r.length, cards: r });
  const fl = flush(hand, starter, crib);
  if (fl) items.push({ kind: "FLUSH", points: fl.length, cards: fl });
  const nb = nobs(hand, starter);
  if (nb !== null) items.push({ kind: "NOBS", points: 1, cards: [nb] });
  return { items };
}

export const total = (s: HandScore) => s.items.reduce((a, i) => a + i.points, 0);

export function pegging(pile: number[], count: number): ScoreItem[] {
  const items: ScoreItem[] = [];
  if (!pile.length) return items;
  if (count === 15) items.push({ kind: "FIFTEEN", points: 2, cards: pile });
  if (count === 31) items.push({ kind: "THIRTY_ONE", points: 2, cards: pile });
  const last = pile[pile.length - 1];
  let same = 1;
  for (let i = pile.length - 2; i >= 0; i--) if (rank(pile[i]) === rank(last)) same++; else break;
  if (same >= 2) items.push({ kind: "PAIR", points: pairPoints(same), cards: pile.slice(-same) });
  for (let len = pile.length; len >= 3; len--) {
    const tail = pile.slice(-len);
    if (new Set(tail.map(rank)).size === len && isRun(tail)) {
      items.push({ kind: "RUN", points: len, cards: tail });
      break;
    }
  }
  return items;
}

// ---- the game -------------------------------------------------------------------------------------------------------

const replace = <T>(list: T[], i: number, v: T): T[] => list.map((x, j) => (j === i ? v : x));
export const canPlay = (s: GameState, p: number) => s.hands[p].some((c) => value(c) + s.count <= 31);

export function newGame(rng: Rng): GameState {
  let cut: number[];
  do {
    const d = shuffled(rng);
    cut = [d[0], d[1 + Math.floor(rng() * 50)]];
  } while (rank(cut[0]) === rank(cut[1]));
  const dealer = rank(cut[0]) < rank(cut[1]) ? 0 : 1;
  const blank: GameState = {
    scores: [0, 0], prevScores: [-1, -1], dealer, hand: 1, phase: "DISCARD", hands: [[], []], kept: [[], []], crib: [],
    starter: null, deck: [], turn: other(dealer), count: 0, pile: [], played: [], lastPlayer: -1, go: [false, false],
    log: [], seq: 0, lastShow: null, winner: null, cutForDeal: cut,
  };
  return deal(blank, rng);
}

export function deal(s: GameState, rng: Rng): GameState {
  const d = shuffled(rng);
  const pone = other(s.dealer);
  const hands: number[][] = [[], []];
  for (let i = 0; i < 12; i++) hands[i % 2 === 0 ? pone : s.dealer].push(d[i]);
  return {
    ...s, phase: "DISCARD", hands: hands.map((h) => h.sort(byCard)), kept: [[], []], crib: [], starter: null,
    deck: d.slice(12), turn: pone, count: 0, pile: [], played: [], lastPlayer: -1, go: [false, false],
  };
}

export function discard(s: GameState, player: number, cards: number[]): GameState {
  if (s.phase !== "DISCARD") throw new RuleError("Not the time to discard.");
  if (cards.length !== 2 || cards[0] === cards[1]) throw new RuleError("Discard two cards.");
  if (s.hands[player].length !== 6) throw new RuleError("You've already discarded.");
  if (!cards.every((c) => s.hands[player].includes(c))) throw new RuleError("Those aren't your cards.");
  const hand = s.hands[player].filter((c) => !cards.includes(c));
  let n: GameState = { ...s, hands: replace(s.hands, player, hand), kept: replace(s.kept, player, hand), crib: [...s.crib, ...cards] };
  if (n.hands.every((h) => h.length === 4)) n = cut(n);
  return n;
}

function cut(s: GameState): GameState {
  const starter = s.deck[0];
  let n: GameState = { ...s, starter, deck: s.deck.slice(1), phase: "PEG", turn: other(s.dealer) };
  if (rank(starter) === 11) n = award(n, n.dealer, { kind: "HEELS", points: 2, cards: [starter] });
  return n;
}

export function play(s: GameState, player: number, card: number, rng: Rng): GameState {
  if (s.phase !== "PEG") throw new RuleError("Not the time to play a card.");
  if (s.turn !== player) throw new RuleError("It's not your turn.");
  if (!s.hands[player].includes(card)) throw new RuleError("That isn't your card.");
  if (value(card) + s.count > 31) throw new RuleError("That would take the count past 31.");
  const count = s.count + value(card);
  const laid = { player, card };
  const pile = [...s.pile, laid];
  let n: GameState = {
    ...s, hands: replace(s.hands, player, s.hands[player].filter((c) => c !== card)), count, pile,
    played: [...s.played, laid], lastPlayer: player, turn: other(player),
  };
  for (const item of pegging(pile.map((p) => p.card), count)) {
    n = award(n, player, item, count);
    if (n.phase === "OVER") return n;
  }
  return settle(n, rng);
}

function settle(start: GameState, rng: Rng): GameState {
  let s = start;
  for (;;) {
    if (s.phase === "OVER") return s;
    if (s.hands.every((h) => h.length === 0)) {
      if (s.count >= 1 && s.count <= 30) s = award(s, s.lastPlayer, { kind: "LAST_CARD", points: 1, cards: [] }, s.count);
      return s.phase === "OVER" ? s : finishHand(s, rng);
    }
    if (s.count === 31) {
      s = { ...s, count: 0, pile: [], go: [false, false], turn: other(s.lastPlayer) };
      continue;
    }
    const t = s.turn;
    if (canPlay(s, t)) return s;
    if (s.hands[t].length && !s.go[t]) {
      s = { ...log(s, t, { kind: "SAYS_GO", points: 0, cards: [] }, s.count), go: replace(s.go, t, true) };
    }
    const o = other(t);
    if (canPlay(s, o)) return { ...s, turn: o };
    s = award(s, s.lastPlayer, { kind: "GO", points: 1, cards: [] }, s.count);
    if (s.phase === "OVER") return s;
    s = { ...s, count: 0, pile: [], go: [false, false], turn: other(s.lastPlayer) };
  }
}

export function finishHand(s: GameState, rng: Rng): GameState {
  const starter = s.starter!;
  const pone = other(s.dealer);
  const counts: ShowCount[] = [
    { player: pone, crib: false, cards: s.kept[pone], score: score(s.kept[pone], starter) },
    { player: s.dealer, crib: false, cards: s.kept[s.dealer], score: score(s.kept[s.dealer], starter) },
    { player: s.dealer, crib: true, cards: s.crib, score: score(s.crib, starter, true) },
  ];
  const startScores = s.scores;
  let n = s;
  const counted: ShowCount[] = [];
  for (const c of counts) {
    counted.push(c);
    for (const item of c.score.items) n = award(n, c.player, item);
    if (n.phase === "OVER") break;
  }
  n = { ...n, lastShow: { hand: s.hand, dealer: s.dealer, starter, startScores, counts: counted } };
  if (n.phase === "OVER") return n;
  return deal({ ...n, dealer: pone, hand: s.hand + 1 }, rng);
}

export function award(s: GameState, player: number, item: ScoreItem, count = 0): GameState {
  if (item.points <= 0) return log(s, player, item, count);
  if (s.phase === "OVER") return s;
  const old = s.scores[player];
  const nu = Math.min(GAME_POINTS, old + item.points);
  let n: GameState = { ...log(s, player, item, count), scores: replace(s.scores, player, nu), prevScores: replace(s.prevScores, player, old) };
  if (nu >= GAME_POINTS) n = { ...n, phase: "OVER", winner: player };
  return n;
}

function log(s: GameState, player: number, item: ScoreItem, count: number): GameState {
  const entry: LogEntry = { seq: s.seq + 1, hand: s.hand, player, item, count };
  return { ...s, log: [...s.log, entry].slice(-80), seq: s.seq + 1 };
}

export function resign(s: GameState, player: number): GameState {
  return s.phase === "OVER" ? s : { ...s, phase: "OVER", winner: other(player) };
}

/** 0 none, 1 skunk, 2 double skunk. */
export function skunk(s: GameState): number {
  if (s.winner === null) return 0;
  const loser = s.scores[other(s.winner)];
  return loser < DOUBLE_SKUNK_LINE ? 2 : loser < SKUNK_LINE ? 1 : 0;
}

/** What [player] may see: the opponent's cards, the crib and the deck are hidden (their counts are given instead). */
export function viewFor(s: GameState, player: number) {
  const opp = other(player);
  return {
    state: { ...s, hands: replace(s.hands, opp, [] as number[]), kept: replace(s.kept, opp, [] as number[]), crib: [] as number[], deck: [] as number[] },
    handCounts: s.hands.map((h) => h.length),
    cribCount: s.crib.length,
  };
}

/** Whether [player] has something to do: discard, or lay a card. */
export function needsMove(s: GameState, player: number): boolean {
  if (s.phase === "DISCARD") return s.hands[player].length === 6;
  if (s.phase === "PEG") return s.turn === player;
  return false;
}

export class RuleError extends Error {}

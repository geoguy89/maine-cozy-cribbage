// Maine Cozy Cribbage game server, built the same way as Refiner's Fire's: one Hub (accounts and friend codes, friends,
// records, invites and the list of games) and one Game Durable Object per game, which deals the cards and is the only
// one that knows both hands. Games are played turn by turn: live over a WebSocket when both players are in, or
// whenever each gets to it.

import { DurableObject } from "cloudflare:workers";
import * as C from "./cribbage";

export interface Env {
  HUB: DurableObjectNamespace<Hub>;
  GAME: DurableObjectNamespace<Game>;
}

const NAME_MAX = 18;
const INVITE_TTL_MS = 7 * 24 * 3600_000;
const REQUEST_TTL_MS = 30 * 24 * 3600_000;
const GAME_KEEP_MS = 30 * 24 * 3600_000;
const MAX_ACTIVE_GAMES = 12;
const REGISTRATIONS_PER_IP_PER_DAY = 30;
const ONLINE_MS = 45_000;
const CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // No 0/O, 1/I.
/** Table talk: fixed phrases only, so nothing needs moderating. Indexes match the app's list. */
const EMOTES = 12;

// ---- helpers --------------------------------------------------------------------------------------------------------

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
const fail = (status: number, error: string) => json({ error }, status);

function randomHex(bytes: number): string {
  return [...crypto.getRandomValues(new Uint8Array(bytes))].map((x) => x.toString(16).padStart(2, "0")).join("");
}

function randomCode(): string {
  const chars = [...crypto.getRandomValues(new Uint8Array(12))].map((x) => CODE_ALPHABET[x % 32]).join("");
  return `${chars.slice(0, 4)}-${chars.slice(4, 8)}-${chars.slice(8, 12)}`;
}

/** Friend codes are typed by hand: accept any case, spaces or missing dashes. */
function normalizeCode(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const c = raw.toUpperCase().replace(/[^0-9A-Z]/g, "");
  if (c.length !== 12) return null;
  return `${c.slice(0, 4)}-${c.slice(4, 8)}-${c.slice(8, 12)}`;
}

async function sha256(text: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(d)].map((x) => x.toString(16).padStart(2, "0")).join("");
}

function cleanName(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const n = raw.replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, NAME_MAX);
  return n.length ? n : null;
}

// ---- entry point ----------------------------------------------------------------------------------------------------

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    if (url.pathname === "/health") return new Response("ok");
    const hub = env.HUB.get(env.HUB.idFromName("hub"));
    // Game routes: the Hub checks the player belongs to the game, then the game's own object takes over.
    const g = url.pathname.match(/^\/v1\/game\/([0-9a-f]{32})(\/action|\/ws)?$/);
    if (g) {
      const auth = await hub.fetch("https://hub/internal/game-auth", {
        method: "POST",
        headers: { authorization: req.headers.get("authorization") ?? "" },
        body: JSON.stringify({ gameId: g[1] }),
      });
      if (!auth.ok) return auth;
      if (g[2] === "/ws" && req.headers.get("Upgrade")?.toLowerCase() !== "websocket") return fail(426, "websocket required");
      const headers = new Headers(req.headers);
      headers.set("x-game-auth", await auth.text());
      return env.GAME.get(env.GAME.idFromName(g[1])).fetch(new Request(req, { headers }));
    }
    if (url.pathname.startsWith("/v1/")) {
      const headers = new Headers(req.headers);
      headers.set("x-client-ip", req.headers.get("cf-connecting-ip") ?? "local");
      return hub.fetch(new Request(req, { headers }));
    }
    return fail(404, "not found");
  },
};

// ---- Hub ------------------------------------------------------------------------------------------------------------

interface Player { id: string; code: string; name: string; last_seen: number; wins: number; losses: number }

interface GameRow {
  id: string; a: string; b: string; status: string; move_a: number; move_b: number; score_a: number; score_b: number;
  winner: string | null; skunk: number; hand: number; created: number; updated: number;
}

/** What the Game object tells the Hub after every move. */
export interface GameSummary {
  scores: number[]; needsMove: boolean[]; over: boolean; winnerSeat: number | null; skunk: number; hand: number;
  /** The seat that just moved, so the other player can be told. */
  movedBy: number | null;
}

export class Hub extends DurableObject<Env> {
  sql: SqlStorage;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS players (id TEXT PRIMARY KEY, secret_hash TEXT NOT NULL, code TEXT NOT NULL UNIQUE,
        name TEXT NOT NULL, created INTEGER NOT NULL, last_seen INTEGER NOT NULL,
        wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0);
      CREATE TABLE IF NOT EXISTS friends (a TEXT NOT NULL, b TEXT NOT NULL, since INTEGER NOT NULL,
        wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0, skunks INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (a, b));
      CREATE TABLE IF NOT EXISTS requests (id TEXT PRIMARY KEY, from_id TEXT NOT NULL, to_id TEXT NOT NULL, created INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS invites (id TEXT PRIMARY KEY, from_id TEXT NOT NULL, to_id TEXT NOT NULL,
        created INTEGER NOT NULL, status TEXT NOT NULL, game_id TEXT);
      CREATE TABLE IF NOT EXISTS games (id TEXT PRIMARY KEY, a TEXT NOT NULL, b TEXT NOT NULL, status TEXT NOT NULL,
        move_a INTEGER NOT NULL DEFAULT 0, move_b INTEGER NOT NULL DEFAULT 0, score_a INTEGER NOT NULL DEFAULT 0,
        score_b INTEGER NOT NULL DEFAULT 0, winner TEXT, skunk INTEGER NOT NULL DEFAULT 0, hand INTEGER NOT NULL DEFAULT 1,
        created INTEGER NOT NULL, updated INTEGER NOT NULL);
      CREATE INDEX IF NOT EXISTS games_a ON games(a);
      CREATE INDEX IF NOT EXISTS games_b ON games(b);
      CREATE TABLE IF NOT EXISTS reg_limit (ip TEXT NOT NULL, day INTEGER NOT NULL, count INTEGER NOT NULL, PRIMARY KEY (ip, day));
    `);
  }

  private one<T>(query: string, ...args: unknown[]): T | undefined {
    return this.sql.exec(query, ...args).toArray()[0] as T | undefined;
  }

  private all<T>(query: string, ...args: unknown[]): T[] {
    return this.sql.exec(query, ...args).toArray() as T[];
  }

  private async auth(req: Request): Promise<Player | null> {
    const m = (req.headers.get("authorization") ?? "").match(/^Bearer ([0-9a-f]{32})\.([A-Za-z0-9_-]{20,100})$/);
    if (!m) return null;
    const row = this.one<Player & { secret_hash: string }>("SELECT * FROM players WHERE id = ?", m[1]);
    if (!row || row.secret_hash !== (await sha256(m[2]))) return null;
    this.sql.exec("UPDATE players SET last_seen = ? WHERE id = ?", Date.now(), row.id);
    return row;
  }

  private areFriends(a: string, b: string): boolean {
    return !!this.one("SELECT 1 FROM friends WHERE a = ? AND b = ?", a, b);
  }

  private befriend(a: string, b: string) {
    const now = Date.now();
    this.sql.exec("INSERT OR IGNORE INTO friends (a, b, since) VALUES (?, ?, ?)", a, b, now);
    this.sql.exec("INSERT OR IGNORE INTO friends (a, b, since) VALUES (?, ?, ?)", b, a, now);
    this.sql.exec("DELETE FROM requests WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)", a, b, b, a);
  }

  private nameOf(id: string): string {
    return this.one<{ name: string }>("SELECT name FROM players WHERE id = ?", id)?.name ?? "Mainer";
  }

  async fetch(req: Request): Promise<Response> {
    const url = new URL(req.url);
    const path = url.pathname;
    let body: any = {};
    if (req.method === "POST") {
      try { body = await req.json(); } catch { return fail(400, "bad json"); }
    }
    if (path === "/v1/register" && req.method === "POST") return this.register(req, body);

    const me = await this.auth(req);
    if (!me) return fail(401, "unauthorized");
    switch (`${req.method} ${path}`) {
      case "GET /v1/sync": return this.syncFor(me);
      case "POST /v1/profile": return this.profile(me, body);
      case "POST /v1/friends/request": return this.friendRequest(me, body);
      case "POST /v1/friends/respond": return this.friendRespond(me, body);
      case "POST /v1/friends/remove": return this.friendRemove(me, body);
      case "POST /v1/invite": return this.invite(me, body);
      case "POST /v1/invite/respond": return this.inviteRespond(me, body);
      case "POST /v1/invite/cancel": return this.inviteCancel(me, body);
      case "POST /v1/game/hide": return this.hideGame(me, body);
      case "POST /internal/game-auth": return this.gameAuth(me, body);
    }
    return fail(404, "not found");
  }

  private async register(req: Request, body: any): Promise<Response> {
    const name = cleanName(body.name) ?? "Mainer";
    const ip = req.headers.get("x-client-ip") ?? "unknown";
    const day = Math.floor(Date.now() / 86_400_000);
    const used = this.one<{ count: number }>("SELECT count FROM reg_limit WHERE ip = ? AND day = ?", ip, day)?.count ?? 0;
    if (used >= REGISTRATIONS_PER_IP_PER_DAY) return fail(429, "too many registrations");
    this.sql.exec("INSERT INTO reg_limit (ip, day, count) VALUES (?, ?, 1) ON CONFLICT(ip, day) DO UPDATE SET count = count + 1", ip, day);
    this.sql.exec("DELETE FROM reg_limit WHERE day < ?", day - 1);
    const id = randomHex(16);
    const secret = [...crypto.getRandomValues(new Uint8Array(32))].map((x) => CODE_ALPHABET[x % 32]).join("");
    const hash = await sha256(secret);
    // The UNIQUE constraint guarantees no two installs share a code; retry on the (astronomically rare) clash.
    for (let attempt = 0; attempt < 8; attempt++) {
      const code = randomCode();
      if (this.one("SELECT 1 FROM players WHERE code = ?", code)) continue;
      const now = Date.now();
      this.sql.exec("INSERT INTO players (id, secret_hash, code, name, created, last_seen) VALUES (?, ?, ?, ?, ?, ?)", id, hash, code, name, now, now);
      return json({ playerId: id, secret, friendCode: code, name });
    }
    return fail(500, "could not allocate a friend code");
  }

  private profile(me: Player, body: any): Response {
    const name = cleanName(body.name);
    if (name) this.sql.exec("UPDATE players SET name = ? WHERE id = ?", name, me.id);
    return json({ ok: true });
  }

  private syncFor(me: Player): Response {
    const now = Date.now();
    this.sql.exec("DELETE FROM requests WHERE created < ?", now - REQUEST_TTL_MS);
    this.sql.exec("UPDATE invites SET status = 'expired' WHERE status = 'pending' AND created < ?", now - INVITE_TTL_MS);
    this.sql.exec("DELETE FROM invites WHERE status != 'pending' AND created < ?", now - INVITE_TTL_MS);
    this.sql.exec("DELETE FROM games WHERE status IN ('over', 'hidden') AND updated < ?", now - GAME_KEEP_MS);

    const friends = this.all<{ id: string; name: string; last_seen: number; wins: number; losses: number; skunks: number }>(
      `SELECT p.id, p.name, p.last_seen, f.wins, f.losses, f.skunks FROM friends f JOIN players p ON p.id = f.b
       WHERE f.a = ? ORDER BY p.name COLLATE NOCASE`, me.id,
    ).map((f) => ({ playerId: f.id, name: f.name, online: now - f.last_seen < ONLINE_MS, lastSeenAgoMs: now - f.last_seen, wins: f.wins, losses: f.losses, skunks: f.skunks }));
    const incoming = this.all<{ id: string; from_id: string; name: string }>(
      "SELECT r.id, r.from_id, p.name FROM requests r JOIN players p ON p.id = r.from_id WHERE r.to_id = ?", me.id,
    ).map((r) => ({ id: r.id, playerId: r.from_id, name: r.name }));
    const outgoing = this.all<{ id: string; to_id: string; name: string }>(
      "SELECT r.id, r.to_id, p.name FROM requests r JOIN players p ON p.id = r.to_id WHERE r.from_id = ?", me.id,
    ).map((r) => ({ id: r.id, playerId: r.to_id, name: r.name }));
    const invites = this.all<{ id: string; from_id: string; to_id: string; created: number; status: string; game_id: string | null }>(
      "SELECT * FROM invites WHERE (from_id = ? OR to_id = ?) AND status = 'pending' ORDER BY created", me.id, me.id,
    ).map((i) => {
      const incomingInvite = i.to_id === me.id;
      const other = incomingInvite ? i.from_id : i.to_id;
      return { id: i.id, incoming: incomingInvite, playerId: other, name: this.nameOf(other), ageMs: now - i.created };
    });
    const games = this.all<GameRow>(
      "SELECT * FROM games WHERE (a = ? OR b = ?) AND status != 'hidden' ORDER BY updated DESC LIMIT 30", me.id, me.id,
    ).map((g) => {
      const seat = g.a === me.id ? 0 : 1;
      const other = seat === 0 ? g.b : g.a;
      return {
        id: g.id, playerId: other, name: this.nameOf(other), seat, over: g.status === "over",
        myMove: (seat === 0 ? g.move_a : g.move_b) === 1, theirMove: (seat === 0 ? g.move_b : g.move_a) === 1,
        myScore: seat === 0 ? g.score_a : g.score_b, theirScore: seat === 0 ? g.score_b : g.score_a,
        won: g.winner === null ? null : g.winner === me.id, skunk: g.skunk, hand: g.hand, updatedAgoMs: now - g.updated,
      };
    });
    const fresh = this.one<Player>("SELECT * FROM players WHERE id = ?", me.id)!;
    return json({
      me: { playerId: me.id, friendCode: me.code, name: fresh.name, wins: fresh.wins, losses: fresh.losses },
      friends, incoming, outgoing, invites, games,
    });
  }

  private friendRequest(me: Player, body: any): Response {
    let target: Player | undefined;
    if (body.code !== undefined) {
      const code = normalizeCode(body.code);
      if (!code) return fail(400, "That doesn't look like a friend code.");
      target = this.one<Player>("SELECT * FROM players WHERE code = ?", code);
    } else if (typeof body.playerId === "string") {
      target = this.one<Player>("SELECT * FROM players WHERE id = ?", body.playerId);
    }
    if (!target) return fail(404, "No player has that friend code.");
    if (target.id === me.id) return fail(400, "That's your own code.");
    if (this.areFriends(me.id, target.id)) return json({ status: "friends", name: target.name });
    // They already asked us: that's a yes from both sides.
    if (this.one("SELECT 1 FROM requests WHERE from_id = ? AND to_id = ?", target.id, me.id)) {
      this.befriend(me.id, target.id);
      return json({ status: "friends", name: target.name });
    }
    const pending = this.one<{ n: number }>("SELECT COUNT(*) AS n FROM requests WHERE from_id = ?", me.id)?.n ?? 0;
    if (pending >= 50) return fail(429, "Too many pending requests.");
    if (!this.one("SELECT 1 FROM requests WHERE from_id = ? AND to_id = ?", me.id, target.id)) {
      this.sql.exec("INSERT INTO requests (id, from_id, to_id, created) VALUES (?, ?, ?, ?)", randomHex(16), me.id, target.id, Date.now());
    }
    return json({ status: "pending", name: target.name });
  }

  private friendRespond(me: Player, body: any): Response {
    const r = this.one<{ id: string; from_id: string }>("SELECT * FROM requests WHERE id = ? AND to_id = ?", String(body.id), me.id);
    if (!r) return fail(404, "request not found");
    if (body.accept === true) this.befriend(me.id, r.from_id);
    else this.sql.exec("DELETE FROM requests WHERE id = ?", r.id);
    return json({ ok: true });
  }

  private friendRemove(me: Player, body: any): Response {
    const other = String(body.playerId);
    this.sql.exec("DELETE FROM friends WHERE (a = ? AND b = ?) OR (a = ? AND b = ?)", me.id, other, other, me.id);
    this.sql.exec("DELETE FROM requests WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)", me.id, other, other, me.id);
    this.sql.exec("UPDATE invites SET status = 'cancelled' WHERE status = 'pending' AND ((from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?))", me.id, other, other, me.id);
    return json({ ok: true });
  }

  private activeGames(id: string): number {
    return this.one<{ n: number }>("SELECT COUNT(*) AS n FROM games WHERE (a = ? OR b = ?) AND status = 'active'", id, id)?.n ?? 0;
  }

  /** Invite a friend to a game. They can accept whenever they like (within a week). */
  private async invite(me: Player, body: any): Promise<Response> {
    const other = String(body.playerId);
    if (other === me.id || !this.areFriends(me.id, other)) return fail(403, "You can only invite friends.");
    if (this.activeGames(me.id) >= MAX_ACTIVE_GAMES) return fail(429, `You have ${MAX_ACTIVE_GAMES} games going already. Finish one first.`);
    const existing = this.one<{ id: string }>("SELECT id FROM invites WHERE from_id = ? AND to_id = ? AND status = 'pending'", me.id, other);
    if (existing) return json({ id: existing.id });
    // They already invited us: accept theirs instead of crossing invites.
    const theirs = this.one<{ id: string }>("SELECT id FROM invites WHERE from_id = ? AND to_id = ? AND status = 'pending'", other, me.id);
    if (theirs) return this.inviteRespond(me, { id: theirs.id, accept: true });
    const id = randomHex(16);
    this.sql.exec("INSERT INTO invites (id, from_id, to_id, created, status) VALUES (?, ?, ?, ?, 'pending')", id, me.id, other, Date.now());
    return json({ id });
  }

  private async inviteRespond(me: Player, body: any): Promise<Response> {
    const i = this.one<{ id: string; from_id: string }>("SELECT * FROM invites WHERE id = ? AND to_id = ? AND status = 'pending'", String(body.id), me.id);
    if (!i) return fail(404, "That invitation has expired.");
    if (body.accept !== true) {
      this.sql.exec("UPDATE invites SET status = 'declined' WHERE id = ?", i.id);
      return json({ ok: true });
    }
    if (this.activeGames(me.id) >= MAX_ACTIVE_GAMES) return fail(429, `You have ${MAX_ACTIVE_GAMES} games going already. Finish one first.`);
    const gameId = randomHex(16);
    const now = Date.now();
    // The inviter takes seat 0.
    this.sql.exec("INSERT INTO games (id, a, b, status, created, updated) VALUES (?, ?, ?, 'active', ?, ?)", gameId, i.from_id, me.id, now, now);
    this.sql.exec("UPDATE invites SET status = 'accepted', game_id = ? WHERE id = ?", gameId, i.id);
    const players = [{ playerId: i.from_id, name: this.nameOf(i.from_id) }, { playerId: me.id, name: me.name }];
    const summary = await this.env.GAME.get(this.env.GAME.idFromName(gameId)).init(gameId, players);
    this.gameUpdated(gameId, summary);
    return json({ ok: true, gameId });
  }

  private inviteCancel(me: Player, body: any): Response {
    this.sql.exec("UPDATE invites SET status = 'cancelled' WHERE id = ? AND from_id = ? AND status = 'pending'", String(body.id), me.id);
    return json({ ok: true });
  }

  /** Clear a finished game off your list. */
  private hideGame(me: Player, body: any): Response {
    this.sql.exec("UPDATE games SET status = 'hidden' WHERE id = ? AND (a = ? OR b = ?) AND status = 'over'", String(body.id), me.id, me.id);
    return json({ ok: true });
  }

  private gameAuth(me: Player, body: any): Response {
    const g = this.one<GameRow>("SELECT * FROM games WHERE id = ?", String(body.gameId));
    if (!g || (g.a !== me.id && g.b !== me.id)) return fail(403, "That isn't one of your games.");
    return json({ playerId: me.id, seat: g.a === me.id ? 0 : 1 });
  }

  /** Called over RPC by a Game after every move: keeps the games list current and records the result once. */
  gameUpdated(gameId: string, s: GameSummary) {
    const g = this.one<GameRow>("SELECT * FROM games WHERE id = ?", gameId);
    if (!g) return;
    const wasOver = g.status !== "active";
    const winner = s.winnerSeat === null ? null : s.winnerSeat === 0 ? g.a : g.b;
    this.sql.exec(
      `UPDATE games SET status = CASE WHEN status = 'active' AND ? THEN 'over' ELSE status END, move_a = ?, move_b = ?,
       score_a = ?, score_b = ?, winner = COALESCE(winner, ?), skunk = ?, hand = ?, updated = ? WHERE id = ?`,
      s.over ? 1 : 0, s.needsMove[0] ? 1 : 0, s.needsMove[1] ? 1 : 0, s.scores[0], s.scores[1], winner, s.skunk, s.hand, Date.now(), gameId);
    if (s.over && !wasOver && winner) {
      const loser = winner === g.a ? g.b : g.a;
      this.sql.exec("UPDATE friends SET wins = wins + 1, skunks = skunks + ? WHERE a = ? AND b = ?", s.skunk > 0 ? 1 : 0, winner, loser);
      this.sql.exec("UPDATE friends SET losses = losses + 1 WHERE a = ? AND b = ?", loser, winner);
      this.sql.exec("UPDATE players SET wins = wins + 1 WHERE id = ?", winner);
      this.sql.exec("UPDATE players SET losses = losses + 1 WHERE id = ?", loser);
    }
  }
}

// ---- Game -----------------------------------------------------------------------------------------------------------

interface GameData {
  id: string;
  players: { playerId: string; name: string }[];
  state: C.GameState;
  /** Bumped on every change, so the app can tell a fresh view from a stale one. */
  version: number;
}

export class Game extends DurableObject<Env> {
  private async load(): Promise<GameData | undefined> {
    return this.ctx.storage.get<GameData>("g");
  }

  /** Called by the Hub when an invite is accepted: cut for deal and deal the first hand. */
  async init(id: string, players: { playerId: string; name: string }[]): Promise<GameSummary> {
    let g = await this.load();
    if (!g) {
      g = { id, players, state: C.newGame(C.cryptoRng), version: 1 };
      await this.ctx.storage.put("g", g);
    }
    return this.summary(g, null);
  }

  private summary(g: GameData, movedBy: number | null): GameSummary {
    const s = g.state;
    return {
      scores: s.scores, needsMove: [C.needsMove(s, 0), C.needsMove(s, 1)], over: s.phase === "OVER",
      winnerSeat: s.winner, skunk: C.skunk(s), hand: s.hand, movedBy,
    };
  }

  private view(g: GameData, seat: number) {
    const others = this.ctx.getWebSockets().map((ws) => this.ctx.getTags(ws)[0]);
    return {
      gameId: g.id, version: g.version, seat, players: g.players.map((p) => ({ ...p, here: others.includes(p.playerId) })),
      ...C.viewFor(g.state, seat),
    };
  }

  private broadcast(g: GameData, extra?: unknown) {
    for (const ws of this.ctx.getWebSockets()) {
      const playerId = this.ctx.getTags(ws)[0];
      const seat = g.players.findIndex((p) => p.playerId === playerId);
      if (seat < 0) continue;
      try { ws.send(JSON.stringify(extra ?? { t: "view", view: this.view(g, seat) })); } catch { /* closing */ }
    }
  }

  async fetch(req: Request): Promise<Response> {
    const auth = JSON.parse(req.headers.get("x-game-auth") ?? "{}") as { playerId: string; seat: number };
    const g = await this.load();
    if (!g) return fail(404, "That game doesn't exist.");
    const seat = g.players.findIndex((p) => p.playerId === auth.playerId);
    if (seat < 0) return fail(403, "That isn't one of your games.");
    const path = new URL(req.url).pathname;

    if (path.endsWith("/ws")) {
      for (const old of this.ctx.getWebSockets(auth.playerId)) { try { old.close(4000, "replaced"); } catch { /* closed */ } }
      const pair = new WebSocketPair();
      this.ctx.acceptWebSocket(pair[1], [auth.playerId]);
      // Everyone gets a fresh view, so both see who's at the table.
      this.broadcast(g);
      return new Response(null, { status: 101, webSocket: pair[0] });
    }
    if (req.method === "GET") return json(this.view(g, seat));
    if (req.method === "POST" && path.endsWith("/action")) {
      let body: any;
      try { body = await req.json(); } catch { return fail(400, "bad json"); }
      return this.act(g, seat, body);
    }
    return fail(404, "not found");
  }

  private async act(g: GameData, seat: number, body: any): Promise<Response> {
    if (body.type === "emote") {
      const id = Number(body.id);
      if (!Number.isInteger(id) || id < 0 || id >= EMOTES) return fail(400, "bad emote");
      this.broadcast(g, { t: "emote", seat, id });
      return json({ ok: true });
    }
    // Moves are checked against the rules (not the app's view version): both players discard at once, and in pegging
    // only the player on turn can change anything. An illegal move comes back with the latest view.
    try {
      switch (body.type) {
        case "discard":
          if (!Array.isArray(body.cards)) return fail(400, "cards required");
          g.state = C.discard(g.state, seat, body.cards.map(Number));
          break;
        case "play":
          g.state = C.play(g.state, seat, Number(body.card), C.cryptoRng);
          break;
        case "resign":
          g.state = C.resign(g.state, seat);
          break;
        default:
          return fail(400, "unknown action");
      }
    } catch (e) {
      if (e instanceof C.RuleError) return json({ error: e.message, view: this.view(g, seat) }, 409);
      throw e;
    }
    g.version++;
    await this.ctx.storage.put("g", g);
    this.broadcast(g);
    await this.env.HUB.get(this.env.HUB.idFromName("hub")).gameUpdated(g.id, this.summary(g, seat));
    return json(this.view(g, seat));
  }

  async webSocketMessage(ws: WebSocket, raw: string | ArrayBuffer) {
    let msg: any;
    try { msg = JSON.parse(typeof raw === "string" ? raw : new TextDecoder().decode(raw)); } catch { return; }
    if (msg.t === "ping") { try { ws.send(JSON.stringify({ t: "pong" })); } catch { /* closing */ } }
  }

  async webSocketClose(ws: WebSocket) {
    const g = await this.load();
    if (!g) return;
    // Let the other player know who's still at the table.
    const leaving = this.ctx.getTags(ws)[0];
    for (const other of this.ctx.getWebSockets()) {
      if (other === ws) continue;
      const pid = this.ctx.getTags(other)[0];
      const seat = g.players.findIndex((p) => p.playerId === pid);
      if (seat < 0) continue;
      const v = this.view(g, seat);
      v.players = v.players.map((p) => (p.playerId === leaving ? { ...p, here: false } : p));
      try { other.send(JSON.stringify({ t: "view", view: v })); } catch { /* closing */ }
    }
  }

  async webSocketError(ws: WebSocket) {
    await this.webSocketClose(ws);
  }
}

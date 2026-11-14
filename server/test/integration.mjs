// End to end against a running server (npm run dev): two players make friends, play a whole game by REST while one
// also watches over a WebSocket, and the result lands in their records.
const BASE = process.env.BASE_URL ?? "http://127.0.0.1:8787";
let failures = 0;
const check = (ok, msg) => { if (!ok) { failures++; console.error("FAIL", msg); } };

async function call(method, path, body, acc) {
  const r = await fetch(BASE + path, {
    method,
    headers: { "content-type": "application/json", ...(acc ? { authorization: `Bearer ${acc.playerId}.${acc.secret}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  return { status: r.status, body: text ? JSON.parse(text) : null };
}

const a = (await call("POST", "/v1/register", { name: "Ayuh Alice" })).body;
const b = (await call("POST", "/v1/register", { name: "Bob from Bangor" })).body;
check(/^[2-9A-Z]{4}-[2-9A-Z]{4}-[2-9A-Z]{4}$/.test(a.friendCode), "friend code format");
check((await call("GET", "/v1/sync", null, { playerId: a.playerId, secret: "x".repeat(32) })).status === 401, "bad secret refused");

// Friends: a request, then accepted.
const req = await call("POST", "/v1/friends/request", { code: b.friendCode.toLowerCase().replace(/-/g, " ") }, a);
check(req.body.status === "pending", "request pending");
const bSync = (await call("GET", "/v1/sync", null, b)).body;
check(bSync.incoming.length === 1, "b sees the request");
await call("POST", "/v1/friends/respond", { id: bSync.incoming[0].id, accept: true }, b);
check((await call("GET", "/v1/sync", null, a)).body.friends[0]?.name === "Bob from Bangor", "now friends");
check((await call("POST", "/v1/invite", { playerId: "0".repeat(32) }, a)).status === 403, "can't invite a stranger");

// Invite, accept.
await call("POST", "/v1/invite", { playerId: b.playerId }, a);
const inv = (await call("GET", "/v1/sync", null, b)).body.invites.find((i) => i.incoming);
check(!!inv, "b sees the invite");
const acc = await call("POST", "/v1/invite/respond", { id: inv.id, accept: true }, b);
const gameId = acc.body.gameId;
check(/^[0-9a-f]{32}$/.test(gameId), "game created");
check((await call("GET", `/v1/game/${gameId}`, null, { playerId: "f".repeat(32), secret: "y".repeat(32) })).status === 401, "outsider refused");

// b watches live.
const wsUrl = BASE.replace("http", "ws") + `/v1/game/${gameId}/ws`;
const ws = new WebSocket(wsUrl, { headers: { authorization: `Bearer ${b.playerId}.${b.secret}` } });
let liveViews = 0;
ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.t === "view") liveViews++; });
await new Promise((res, rej) => { ws.addEventListener("open", res); ws.addEventListener("error", rej); setTimeout(rej, 5000); });

const accounts = [a, b];
let view = (await call("GET", `/v1/game/${gameId}`, null, a)).body;
check(view.seat === 0 && view.state.hands[0].length === 6 && view.state.hands[1].length === 0 && view.handCounts[1] === 6, "a's view hides b's hand");
const value = (c) => Math.min(10, (c % 13) + 1);
let moves = 0;
while (view.state.phase !== "OVER" && moves < 2000) {
  const views = await Promise.all(accounts.map((acc) => call("GET", `/v1/game/${gameId}`, null, acc).then((r) => r.body)));
  let moved = false;
  for (let seat = 0; seat < 2; seat++) {
    const v = views[seat];
    const s = v.state;
    if (s.phase === "DISCARD" && s.hands[seat].length === 6) {
      const r = await call("POST", `/v1/game/${gameId}/action`, { type: "discard", cards: s.hands[seat].slice(0, 2), version: v.version }, accounts[seat]);
      check(r.status === 200, `discard ok ${JSON.stringify(r.body).slice(0, 200)}`);
      view = r.body; moved = true; moves++;
    } else if (s.phase === "PEG" && s.turn === seat) {
      const card = s.hands[seat].find((c) => value(c) + s.count <= 31);
      const r = await call("POST", `/v1/game/${gameId}/action`, { type: "play", card, version: v.version }, accounts[seat]);
      check(r.status === 200, `play ok ${JSON.stringify(r.body).slice(0, 200)}`);
      view = r.body; moved = true; moves++;
      break; // the other view is now stale
    }
  }
  if (!moved) { check(false, "nobody could move"); break; }
}
check(view.state.phase === "OVER", `game finished after ${moves} moves`);
// A move after the game is over is refused, with the latest view.
const late = await call("POST", `/v1/game/${gameId}/action`, { type: "play", card: 0 }, a);
check(late.status === 409 && late.body.view?.state.phase === "OVER", "late move refused");

await new Promise((r) => setTimeout(r, 300));
check(liveViews > moves / 2, `live updates over the socket (${liveViews})`);
ws.close();

const after = (await call("GET", "/v1/sync", null, a)).body;
const f = after.friends[0];
check(f.wins + f.losses === 1, "result recorded between friends");
check(after.games[0]?.over === true, "game listed as over");
const winnerSeat = view.state.winner;
check(after.me.wins === (winnerSeat === 0 ? 1 : 0), "a's overall record");

// Resigning a fresh game hands it to the other player.
await call("POST", "/v1/invite", { playerId: a.playerId }, b);
const inv2 = (await call("GET", "/v1/sync", null, a)).body.invites.find((i) => i.incoming);
const g2 = (await call("POST", "/v1/invite/respond", { id: inv2.id, accept: true }, a)).body.gameId;
const resigned = (await call("POST", `/v1/game/${g2}/action`, { type: "resign" }, a)).body;
check(resigned.state.phase === "OVER" && resigned.state.winner === 0, "resign gives the game to the inviter (seat 0)");

if (failures) { console.error(`${failures} failures`); process.exit(1); }
console.log(`integration ok: ${moves} moves, ${liveViews} live views`);

// The server's rules against the app's: the shared scoring examples, plus whole random games checked for sanity.
import { readFileSync } from "node:fs";
import * as C from "../src/cribbage.ts";

const v = JSON.parse(readFileSync(new URL("../../spec/scoring-vectors.json", import.meta.url), "utf8"));
let failures = 0;
const check = (ok, msg) => { if (!ok) { failures++; if (failures < 20) console.error("FAIL", msg); } };

for (const h of v.hands) check(C.total(C.score(h.hand, h.starter, h.crib)) === h.total, `hand ${JSON.stringify(h)}`);
for (const p of v.pegging) {
  const count = p.pile.reduce((a, c) => a + C.value(c), 0);
  check(C.pegging(p.pile, count).reduce((a, i) => a + i.points, 0) === p.points, `pile ${JSON.stringify(p)}`);
}

// A seeded generator so failures can be replayed.
function mulberry(seed) {
  return () => { seed |= 0; seed = (seed + 0x6d2b79f5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
for (let g = 0; g < 300; g++) {
  const rng = mulberry(g + 1);
  let s = C.newGame(rng);
  let steps = 0;
  while (s.phase !== "OVER") {
    if (++steps > 5000) { check(false, `game ${g} stuck`); break; }
    if (s.phase === "DISCARD") {
      const p = s.hands[0].length === 6 ? 0 : 1;
      s = C.discard(s, p, s.hands[p].slice(0, 2));
    } else {
      const total = s.hands[0].length + s.hands[1].length + s.crib.length + s.deck.length + 1 + s.played.length;
      check(total === 52, `game ${g} cards conserved (${total})`);
      check(C.canPlay(s, s.turn), `game ${g}: player on turn can play`);
      const options = s.hands[s.turn].filter((c) => C.value(c) + s.count <= 31);
      s = C.play(s, s.turn, options[Math.floor(rng() * options.length)], rng);
    }
  }
  check(s.scores[s.winner] === 121 && s.scores[1 - s.winner] < 121, `game ${g} ends at 121`);
}
// A view hides the opponent's cards.
const s = C.newGame(mulberry(9));
const view = C.viewFor(s, 0);
check(view.state.hands[1].length === 0 && view.handCounts[1] === 6 && view.state.deck.length === 0 && view.state.hands[0].length === 6, "view hides the other hand");

if (failures) { console.error(`${failures} failures`); process.exit(1); }
console.log(`rules ok: ${v.hands.length} hands, ${v.pegging.length} piles, 300 games`);

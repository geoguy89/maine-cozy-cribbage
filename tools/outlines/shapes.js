const fs = require("fs"), path = require("path");
const dir = process.argv[2];
const files = { MOOSEHEAD: "Moosehead_Lake", SEBEC: "Sebec_Lake", SCHOODIC: "Schoodic_Lake", RANGELEY: "Rangeley_Lake", SEBAGO: "Sebago_Lake", MOUNT_DESERT: "Mount_Desert_Island", MOOSE_ISLAND: "Moose_Island_Eastport" };
function dp(pts, eps) {
  if (pts.length < 3) return pts;
  let max = 0, idx = 0; const [a, b] = [pts[0], pts[pts.length - 1]];
  for (let i = 1; i < pts.length - 1; i++) {
    const p = pts[i]; const dx = b[0] - a[0], dy = b[1] - a[1];
    const len = Math.hypot(dx, dy) || 1e-12;
    const d = Math.abs(dy * p[0] - dx * p[1] + b[0] * a[1] - b[1] * a[0]) / len;
    if (d > max) { max = d; idx = i; }
  }
  if (max > eps) return [...dp(pts.slice(0, idx + 1), eps).slice(0, -1), ...dp(pts.slice(idx), eps)];
  return [a, b];
}
function dpRing(r, eps) {
  const ring = r.slice(0, -1); let far = 0, fd = -1;
  for (let i = 0; i < ring.length; i++) { const d = Math.hypot(ring[i][0] - ring[0][0], ring[i][1] - ring[0][1]); if (d > fd) { fd = d; far = i; } }
  const a = dp(ring.slice(0, far + 1), eps), b = dp([...ring.slice(far), ring[0]], eps);
  return [...a.slice(0, -1), ...b];
}
const area = (r) => { let s = 0; for (let i = 0; i < r.length; i++) { const [x1, y1] = r[i], [x2, y2] = r[(i + 1) % r.length]; s += x1 * y2 - x2 * y1; } return Math.abs(s / 2); };
let out = "";
for (const [key, f] of Object.entries(files)) {
  const j = JSON.parse(fs.readFileSync(path.join(dir, f + ".json"), "utf8"))[0];
  const rings = j.geojson.coordinates;
  const lat0 = Number(j.lat) * Math.PI / 180, k = Math.cos(lat0);
  const proj = rings.map((r) => r.map(([lon, lat]) => [lon * k, -lat]));
  const outer = proj[0];
  const xs = outer.map((p) => p[0]), ys = outer.map((p) => p[1]);
  const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
  const w = maxX - minX, h = maxY - minY, s = Math.max(w, h);
  const norm = (r) => r.map(([x, y]) => [(x - minX) / s, (y - minY) / s]);
  const target = 220;
  let eps = 0.0005, simp;
  for (;;) { simp = dpRing(norm(outer), eps); if (simp.length <= target) break; eps *= 1.25; }
  const holes = proj.slice(1).map(norm).filter((r) => area(r) > 0.0004).map((r) => dpRing(r, eps * 1.5)).filter((r) => r.length >= 4);
  const fmt = (r) => "floatArrayOf(" + r.slice(0, -1).map(([x, y]) => `${x.toFixed(4)}f, ${y.toFixed(4)}f`).join(", ") + ")";
  out += `    val ${key} = Outline(
        projection = Projection(${minX.toFixed(6)}f, ${minY.toFixed(6)}f, ${s.toFixed(6)}f, ${k.toFixed(6)}f),\n        width = ${(w / s).toFixed(4)}f, height = ${(h / s).toFixed(4)}f,\n        outer = ${fmt(simp)},\n        islands = listOf(${holes.map(fmt).join(", ")}),\n    )\n`;
  console.error(key, "outer", simp.length, "islands", holes.length, "w/h", (w / h).toFixed(2));
}
fs.writeFileSync(process.argv[3], out);

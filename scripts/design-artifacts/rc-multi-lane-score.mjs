#!/usr/bin/env node
/** Pairwise pixel comparison for manifest-driven Remote Compose player lanes. */
import fs from "node:fs";
import path from "node:path";
import { PNG } from "pngjs";
import pixelmatch from "pixelmatch";

function usage() {
  console.error(
    "usage: node rc-multi-lane-score.mjs <lanes-dir> <lane> <lane>... [--json <out>]",
  );
  process.exit(2);
}

const args = process.argv.slice(2);
const jsonAt = args.indexOf("--json");
const jsonOut = jsonAt >= 0 ? args[jsonAt + 1] : null;
if (jsonAt >= 0) args.splice(jsonAt, 2);
const [lanesDir, ...lanes] = args;
if (!lanesDir || lanes.length < 2 || (jsonAt >= 0 && !jsonOut)) usage();
if (new Set(lanes).size !== lanes.length) {
  console.error("error: lane names must be unique");
  process.exit(1);
}

const ids = [
  ...new Set(
    lanes.flatMap((lane) => {
      const dir = path.join(lanesDir, lane);
      if (!fs.existsSync(dir)) return [];
      return fs
        .readdirSync(dir)
        .filter((name) => name.endsWith(".png") || name.endsWith(".error"))
        .map((name) => name.replace(/\.(png|error)$/, ""));
    }),
  ),
].sort();

function result(lane, id) {
  const dir = path.join(lanesDir, lane);
  const errorPath = path.join(dir, `${id}.error`);
  const unsupportedPath = path.join(dir, `${id}.unsupported`);
  const primary = fs.existsSync(path.join(dir, `${id}.png`))
    ? "rendered"
    : fs.existsSync(errorPath)
      ? "error"
      : "missing";
  return {
    primary,
    error: primary === "error" ? fs.readFileSync(errorPath, "utf8") : null,
    unsupported: fs.existsSync(unsupportedPath)
      ? fs.readFileSync(unsupportedPath, "utf8").split("\n").filter(Boolean)
      : [],
  };
}

function compare(left, right) {
  const rows = [];
  const unavailable = [];
  let exact = 0;
  for (const id of ids) {
    const leftResult = result(left, id);
    const rightResult = result(right, id);
    if (leftResult.primary !== "rendered" || rightResult.primary !== "rendered") {
      unavailable.push({ id, left: leftResult.primary, right: rightResult.primary });
      continue;
    }
    const a = PNG.sync.read(fs.readFileSync(path.join(lanesDir, left, `${id}.png`)));
    const b = PNG.sync.read(fs.readFileSync(path.join(lanesDir, right, `${id}.png`)));
    if (a.width !== b.width || a.height !== b.height) {
      rows.push({ id, fraction: 1, differing: null, total: null, note: "size mismatch" });
      continue;
    }
    const total = a.width * a.height;
    const differing = pixelmatch(a.data, b.data, null, a.width, a.height, { threshold: 0.1 });
    if (differing === 0) exact += 1;
    else rows.push({ id, fraction: differing / total, differing, total });
  }
  rows.sort((a, b) => b.fraction - a.fraction);
  return { left, right, documents: ids.length, exact, unavailable, rows };
}

const comparisons = [];
for (let left = 0; left < lanes.length; left += 1) {
  for (let right = left + 1; right < lanes.length; right += 1) {
    comparisons.push(compare(lanes[left], lanes[right]));
  }
}

for (const result of comparisons) {
  const compared = result.documents - result.unavailable.length;
  console.log(`${result.left} <> ${result.right}`);
  console.log(
    `    ${compared}/${result.documents} compared, ${result.exact} exact, ` +
      `${result.rows.length} differing, ${result.unavailable.length} unavailable`,
  );
  for (const row of result.rows.slice(0, 5)) {
    console.log(`    ${(row.fraction * 100).toFixed(2).padStart(6)}%  ${row.id}`);
  }
}

const results = Object.fromEntries(
  lanes.map((lane) => [lane, Object.fromEntries(ids.map((id) => [id, result(lane, id)]))]),
);
const summary = { lanes, documents: ids.length, results, comparisons };
if (jsonOut) fs.writeFileSync(jsonOut, `${JSON.stringify(summary, null, 2)}\n`);

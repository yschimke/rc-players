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

const pngCache = new Map();
function readPng(file) {
  if (!pngCache.has(file)) pngCache.set(file, PNG.sync.read(fs.readFileSync(file)));
  return pngCache.get(file);
}

/**
 * Pixels a lane actually painted, against the canvas it was given.
 *
 * The difference percentage is the wrong instrument for a document that draws nothing: a blank
 * that occupies little of its canvas scores under 1%, so a sheet of blanks reads as near-misses.
 * Coverage is what makes them findable, and a lane whose coverage is far below the reference's is
 * drawing less than it should even when the difference number is small.
 */
function coverage(png) {
  let opaque = 0;
  for (let i = 3; i < png.data.length; i += 4) if (png.data[i] > 0) opaque += 1;
  const total = png.width * png.height;
  return { opaque, total, coverage: total > 0 ? opaque / total : 0 };
}

function result(lane, id) {
  const dir = path.join(lanesDir, lane);
  const errorPath = path.join(dir, `${id}.error`);
  const unsupportedPath = path.join(dir, `${id}.unsupported`);
  const pngPath = path.join(dir, `${id}.png`);
  const primary = fs.existsSync(pngPath)
    ? "rendered"
    : fs.existsSync(errorPath)
      ? "error"
      : "missing";
  const painted = primary === "rendered" ? coverage(readPng(pngPath)) : null;
  return {
    primary,
    error: primary === "error" ? fs.readFileSync(errorPath, "utf8") : null,
    unsupported: fs.existsSync(unsupportedPath)
      ? fs.readFileSync(unsupportedPath, "utf8").split("\n").filter(Boolean)
      : [],
    ...(painted ?? {}),
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
    const a = readPng(path.join(lanesDir, left, `${id}.png`));
    const b = readPng(path.join(lanesDir, right, `${id}.png`));
    // Direction-agnostic: 1 when both lanes painted the same amount, 0 when either painted
    // nothing. Which lane is short travels with the row so the console can name it.
    const minOpaque = Math.min(leftResult.opaque, rightResult.opaque);
    const maxOpaque = Math.max(leftResult.opaque, rightResult.opaque);
    const inkRatio = maxOpaque > 0 ? minOpaque / maxOpaque : 1;
    const underCovered =
      leftResult.opaque < rightResult.opaque
        ? left
        : rightResult.opaque < leftResult.opaque
          ? right
          : null;
    if (a.width !== b.width || a.height !== b.height) {
      rows.push({
        id,
        fraction: 1,
        differing: null,
        total: null,
        note: "size mismatch",
        coverage: inkRatio,
        underCovered,
      });
      continue;
    }
    const total = a.width * a.height;
    const differing = pixelmatch(a.data, b.data, null, a.width, a.height, { threshold: 0.1 });
    if (differing === 0) exact += 1;
    else
      rows.push({
        id,
        fraction: differing / total,
        differing,
        total,
        coverage: inkRatio,
        underCovered,
      });
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
  // Under-coverage: a lane that painted far less than the other is a blank or a partial draw. The
  // difference percentage hides those when the document occupies little of its canvas — a
  // completely blank sticker can score under 1%.
  for (const row of result.rows
    .filter((entry) => entry.coverage !== null && entry.coverage < 0.5)
    .slice(0, 5)) {
    console.log(
      `    coverage ${row.coverage.toFixed(2).padStart(5)}  ${row.id} ` +
        `(${row.underCovered ?? "both"} painted less)`,
    );
  }
}

const results = Object.fromEntries(
  lanes.map((lane) => [lane, Object.fromEntries(ids.map((id) => [id, result(lane, id)]))]),
);
const summary = { lanes, documents: ids.length, results, comparisons };
if (jsonOut) fs.writeFileSync(jsonOut, `${JSON.stringify(summary, null, 2)}\n`);

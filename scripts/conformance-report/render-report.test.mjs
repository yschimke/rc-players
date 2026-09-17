import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, test } from "node:test";
import { fileURLToPath } from "node:url";

const script = fileURLToPath(new URL("./render-report.mjs", import.meta.url));
const roots = [];

afterEach(() => {
  for (const root of roots.splice(0)) fs.rmSync(root, { recursive: true, force: true });
});

function workspace() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "rc-conformance-report-"));
  roots.push(root);
  return root;
}

/** One lane's results file, in the shape `:rc-conformance` writes. */
function writeResults(root, name, results, summary = {}) {
  const file = path.join(root, `${name}.json`);
  fs.writeFileSync(
    file,
    JSON.stringify({
      player: { name, version: "test" },
      corpus: { path: "/corpus", gold_count: results.length },
      summary: { passed: 0, failed: 0, errored: 0, skipped: 0, suspicious: 0, ...summary },
      results,
    }),
  );
  return `${name}=${file}`;
}

function gold(name, overrides = {}) {
  return {
    name,
    category: "layout",
    profile: "core",
    status: "PASS",
    suspicious: false,
    checks_total: 1,
    checks_failed: 0,
    diffs: [],
    observed: { initial: { tree: "a large blob that must not be archived" } },
    ...overrides,
  };
}

function render(root, out, lanes, extra = []) {
  const result = spawnSync(
    process.execPath,
    [script, "--out", out, "--run-id", "2026-01-01", "--commit", "abc1234", ...extra, ...lanes],
    { encoding: "utf8" },
  );
  assert.equal(result.status, 0, result.stderr);
  return result;
}

test("separates the frames only the subject lane fails from the ones every lane fails", () => {
  const root = workspace();
  const out = path.join(root, "report");

  // `shared` disagrees on both lanes; `mine` only on the subject. That distinction is the whole
  // reason a reference lane exists, so it is the thing worth asserting.
  const subject = writeResults(root, "cmp", [
    gold("shared", { status: "FAIL", diffs: [{ probe: "raster", at: "initial" }] }),
    gold("mine", { status: "FAIL", diffs: [{ probe: "raster", at: "initial" }] }),
    gold("clean"),
  ]);
  const reference = writeResults(root, "androidx-jvm", [
    gold("shared", { status: "FAIL", diffs: [{ probe: "raster", at: "initial" }] }),
    gold("mine"),
    gold("clean"),
  ]);

  render(root, out, [subject, reference]);
  const run = fs.readFileSync(path.join(out, "runs", "2026-01-01", "README.md"), "utf8");

  assert.match(run, /\| `androidx-jvm` \| 1 \| 1 \|/);
  // Named, not just counted: a count of one is only actionable if the reader can see which one.
  assert.match(run, /- `mine` at `initial`/);
  assert.doesNotMatch(run, /- `shared` at `initial`/);
});

test("renders a lane whose job produced nothing as not measured rather than as a zero", () => {
  const root = workspace();
  const out = path.join(root, "report");
  const subject = writeResults(root, "cmp", [gold("only")]);

  render(root, out, [subject, `native-appkit=${path.join(root, "absent.json")}`]);
  const run = fs.readFileSync(path.join(out, "runs", "2026-01-01", "README.md"), "utf8");

  assert.match(run, /\| `native-appkit` \| — \| — \| — \| — \| \*not measured\* \|/);
  assert.equal(fs.existsSync(path.join(out, "runs", "2026-01-01", "native-appkit.summary.json")), false);
});

test("archives per-gold outcomes without the observation blobs", () => {
  const root = workspace();
  const out = path.join(root, "report");
  const subject = writeResults(root, "cmp", [
    gold("one", { status: "FAIL", diffs: [{ probe: "raster", at: "resize_1" }, { probe: "tree", at: "initial" }] }),
  ]);

  render(root, out, [subject]);
  const archived = JSON.parse(
    fs.readFileSync(path.join(out, "runs", "2026-01-01", "cmp.summary.json"), "utf8"),
  );

  assert.deepEqual(archived.results[0].diffs_by_probe, { raster: 1, tree: 1 });
  assert.deepEqual(archived.results[0].raster_failures, ["resize_1"]);
  // The blob is what makes a full results file 3 MB a lane; keeping it would put gigabytes on the
  // report branch inside a year.
  assert.equal("observed" in archived.results[0], false);
});

test("keeps one history row per lane per run, and replaces a re-run rather than duplicating it", () => {
  const root = workspace();
  const out = path.join(root, "report");
  const lanes = [writeResults(root, "cmp", [gold("a"), gold("b", { status: "FAIL" })])];

  render(root, out, lanes);
  render(root, out, lanes);
  const history = fs.readFileSync(path.join(out, "history.csv"), "utf8").trim().split("\n");

  assert.equal(history.length, 2, history.join("\n"));
  assert.match(history[1], /^2026-01-01,cmp,2,1,0,0,abc1234$/);
});

test("excludes suspicious golds from the rate instead of counting them as failures", () => {
  const root = workspace();
  const out = path.join(root, "report");
  const subject = writeResults(root, "cmp", [
    gold("good"),
    gold("disputed", { status: "SUSPICIOUS", suspicious: true }),
  ]);

  render(root, out, [subject]);
  const run = fs.readFileSync(path.join(out, "runs", "2026-01-01", "README.md"), "utf8");

  assert.match(run, /\| `cmp` \| 1 \/ 1 \| 100\.0% \|/);
  assert.match(run, /- `disputed`/);
});

test("a lane excluded from --reference cannot mask the subject's unique failures", () => {
  const root = workspace();

  // `mine` is a frame only the subject fails: the credible reference passes it. A lane that fails
  // everything would mark it shared, which is the whole reason the reference set is narrowable.
  const subject = writeResults(root, "cmp", [
    gold("mine", { status: "FAIL", diffs: [{ probe: "raster", at: "initial" }] }),
  ]);
  const reference = writeResults(root, "androidx-jvm", [gold("mine")]);
  const weak = writeResults(root, "native-appkit", [
    gold("mine", { status: "FAIL", diffs: [{ probe: "raster", at: "initial" }] }),
  ]);

  const diluted = path.join(root, "diluted");
  render(root, diluted, [subject, reference, weak]);
  assert.match(
    fs.readFileSync(path.join(diluted, "README.md"), "utf8"),
    /not shared with a reference lane \| \*\*0\*\*/,
  );

  const narrowed = path.join(root, "narrowed");
  render(root, narrowed, [subject, reference, weak], ["--reference", "androidx-jvm"]);
  const index = fs.readFileSync(path.join(narrowed, "README.md"), "utf8");
  const run = fs.readFileSync(path.join(narrowed, "runs", "2026-01-01", "README.md"), "utf8");

  // The landing page and the run it links to must agree about the number both lead with.
  assert.match(index, /not shared with a reference lane \| \*\*1\*\*/);
  assert.match(run, /- `mine` at `initial`/);
  assert.match(run, /Scored above but not compared here: `native-appkit`/);
});

test("names the golds the reference reproduces and the subject does not", () => {
  const root = workspace();
  const out = path.join(root, "report");

  // The one row that is a work list. `ours` fails here and passes on the reference, so the
  // expectation cannot be blamed -- that is the finding. `disputed` is suspicious on both sides and
  // must not appear in any column.
  const subject = writeResults(root, "cmp", [
    gold("settled"),
    gold("ours", { status: "FAIL", diffs: [{ probe: "tree", at: "initial" }, { probe: "tree", at: "resize_0" }] }),
    gold("ahead"),
    gold("neither", { status: "FAIL", diffs: [{ probe: "raster", at: "initial" }] }),
    gold("disputed", { status: "SUSPICIOUS", suspicious: true }),
  ]);
  const reference = writeResults(root, "androidx-jvm", [
    gold("settled"),
    gold("ours"),
    gold("ahead", { status: "FAIL", diffs: [{ probe: "raster", at: "initial" }] }),
    gold("neither", { status: "FAIL", diffs: [{ probe: "raster", at: "initial" }] }),
    gold("disputed", { status: "SUSPICIOUS", suspicious: true }),
  ]);

  render(root, out, [subject, reference]);
  const run = fs.readFileSync(path.join(out, "runs", "2026-01-01", "README.md"), "utf8");

  assert.match(run, /\| both pass \| 1 \|/);
  assert.match(run, /\| only `androidx-jvm` passes \| \*\*1\*\* \|/);
  assert.match(run, /\| only `cmp` passes \| 1 \|/);
  assert.match(run, /\| both fail \| 1 \|/);
  // Named with its probes: "which gold" without "which probe" is not yet a bug report.
  assert.match(run, /- `ours` — tree ×2/);
  assert.doesNotMatch(run, /`disputed` — /);
});

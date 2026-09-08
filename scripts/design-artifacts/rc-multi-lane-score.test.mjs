import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, test } from "node:test";
import { fileURLToPath } from "node:url";
import { PNG } from "pngjs";

const script = fileURLToPath(new URL("./rc-multi-lane-score.mjs", import.meta.url));
const roots = [];

afterEach(() => {
  for (const root of roots.splice(0)) fs.rmSync(root, { recursive: true, force: true });
});

function writePng(file, rgba) {
  const png = new PNG({ width: 1, height: 1 });
  png.data.set(rgba);
  fs.writeFileSync(file, PNG.sync.write(png));
}

test("scores every lane pair and retains errors and unsupported reports", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "rc-multi-lane-score-"));
  roots.push(root);
  for (const lane of ["view", "upstream", "vendored"]) {
    fs.mkdirSync(path.join(root, lane));
  }
  writePng(path.join(root, "view", "same.png"), [0, 0, 0, 255]);
  writePng(path.join(root, "upstream", "same.png"), [0, 0, 0, 255]);
  writePng(path.join(root, "vendored", "same.png"), [255, 255, 255, 255]);
  fs.writeFileSync(path.join(root, "view", "failure.error"), "view failed");
  writePng(path.join(root, "upstream", "failure.png"), [0, 0, 0, 0]);
  writePng(path.join(root, "vendored", "failure.png"), [0, 0, 0, 0]);
  fs.writeFileSync(
    path.join(root, "vendored", "same.unsupported"),
    "DATA_SHADER[2]: unavailable",
  );

  const json = path.join(root, "comparison.json");
  const result = spawnSync(
    process.execPath,
    [script, root, "view", "upstream", "vendored", "--json", json],
    { encoding: "utf8" },
  );
  assert.equal(result.status, 0, result.stderr);
  const summary = JSON.parse(fs.readFileSync(json, "utf8"));
  assert.equal(summary.comparisons.length, 3);
  assert.equal(summary.results.view.failure.primary, "error");
  assert.equal(summary.results.view.failure.error, "view failed");
  assert.deepEqual(summary.results.vendored.same.unsupported, ["DATA_SHADER[2]: unavailable"]);
  const viewVsUpstream = summary.comparisons.find(
    (comparison) => comparison.left === "view" && comparison.right === "upstream",
  );
  assert.equal(viewVsUpstream.exact, 1);
  assert.deepEqual(viewVsUpstream.unavailable, [
    { id: "failure", left: "error", right: "rendered" },
  ]);
});

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, test } from "node:test";
import { fileURLToPath } from "node:url";

const script = fileURLToPath(new URL("./validate-results.mjs", import.meta.url));
const roots = [];

afterEach(() => {
  for (const root of roots.splice(0)) fs.rmSync(root, { recursive: true, force: true });
});

function fixture(ids = ["one", "two"]) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "rc-operation-conformance-"));
  roots.push(root);
  const input = path.join(root, "input");
  const lanes = path.join(root, "lanes");
  fs.mkdirSync(input);
  for (const lane of ["view", "upstream-release", "vendored-android", "cmp-jvm"]) {
    fs.mkdirSync(path.join(lanes, lane), { recursive: true });
  }
  fs.writeFileSync(
    path.join(input, "manifest.json"),
    JSON.stringify(ids.map((id) => ({ id, width: 1, height: 1 }))),
  );
  for (const id of ids) fs.writeFileSync(path.join(input, `${id}.rc`), "rc");
  return { input, lanes, ids };
}

function run(input, lanes, laneNames) {
  return spawnSync(
    process.execPath,
    [script, "results", input, lanes, ...laneNames],
    { encoding: "utf8" },
  );
}

test("accepts independently named lanes with one primary result per document", () => {
  const { input, lanes, ids } = fixture();
  const laneNames = ["view", "upstream-release", "vendored-android", "cmp-jvm"];
  for (const lane of laneNames) {
    for (const id of ids) fs.writeFileSync(path.join(lanes, lane, `${id}.png`), "png");
  }
  assert.equal(run(input, lanes, laneNames).status, 0);
});

test("keeps errors as lane results instead of appointing View as an oracle", () => {
  const { input, lanes, ids } = fixture();
  const laneNames = ["view", "upstream-release"];
  fs.writeFileSync(path.join(lanes, "view", "one.error"), "boom");
  fs.writeFileSync(path.join(lanes, "view", "two.png"), "png");
  for (const id of ids) {
    fs.writeFileSync(path.join(lanes, "upstream-release", `${id}.png`), "png");
  }
  assert.equal(run(input, lanes, laneNames).status, 0);
});

test("allows an unsupported report only alongside a rendered result", () => {
  const { input, lanes, ids } = fixture();
  const laneNames = ["cmp-jvm"];
  for (const id of ids) fs.writeFileSync(path.join(lanes, "cmp-jvm", `${id}.png`), "png");
  fs.writeFileSync(path.join(lanes, "cmp-jvm", "one.unsupported"), "missing operation");
  assert.equal(run(input, lanes, laneNames).status, 0);

  fs.rmSync(path.join(lanes, "cmp-jvm", "one.png"));
  fs.writeFileSync(path.join(lanes, "cmp-jvm", "one.error"), "boom");
  const invalid = run(input, lanes, laneNames);
  assert.equal(invalid.status, 1);
  assert.match(invalid.stderr, /unsupported without a rendered PNG/);
});

test("rejects duplicate primary results and duplicate lane names", () => {
  const { input, lanes, ids } = fixture();
  for (const id of ids) fs.writeFileSync(path.join(lanes, "view", `${id}.png`), "png");
  fs.writeFileSync(path.join(lanes, "view", "one.error"), "boom");
  const duplicateResult = run(input, lanes, ["view"]);
  assert.equal(duplicateResult.status, 1);
  assert.match(duplicateResult.stderr, /exactly one primary result/);

  fs.rmSync(path.join(lanes, "view", "one.error"));
  const duplicateLane = run(input, lanes, ["view", "view"]);
  assert.equal(duplicateLane.status, 1);
  assert.match(duplicateLane.stderr, /lane names must be unique/);
});

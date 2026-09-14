import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, test } from "node:test";
import { fileURLToPath } from "node:url";

const sourceScript = fileURLToPath(new URL("./render-ab.sh", import.meta.url));
const roots = [];

afterEach(() => {
  for (const root of roots.splice(0))
    fs.rmSync(root, { recursive: true, force: true });
});

function fixture({ withFontCache }) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "rc-lane-ab-render-"));
  roots.push(root);

  const input = path.join(root, "input");
  const lanes = path.join(root, "lanes");
  const cacheRoot = path.join(root, "cache");
  const argsFile = path.join(root, "gradle-args.txt");
  const scripts = path.join(root, "scripts", "rc-lane-ab");
  const designArtifacts = path.join(root, "scripts", "design-artifacts");
  const fakeBin = path.join(root, "bin");
  fs.mkdirSync(input);
  fs.mkdirSync(scripts, { recursive: true });
  fs.mkdirSync(path.join(designArtifacts, "node_modules", "pixelmatch"), {
    recursive: true,
  });
  fs.mkdirSync(fakeBin);
  if (withFontCache)
    fs.mkdirSync(path.join(cacheRoot, "composeai", "fonts"), { recursive: true });

  fs.writeFileSync(path.join(input, "manifest.json"), "[]\n");
  fs.copyFileSync(sourceScript, path.join(scripts, "render-ab.sh"));
  fs.writeFileSync(path.join(scripts, "validate-stage.mjs"), "");
  fs.writeFileSync(path.join(designArtifacts, "rc-lane-ab-score.mjs"), "");
  fs.writeFileSync(path.join(scripts, "compose_ab.py"), "");
  fs.writeFileSync(
    path.join(root, "gradlew"),
    '#!/usr/bin/env bash\nprintf "%s\\n" "$@" > "$RC_LANE_TEST_ARGS"\n',
  );
  fs.chmodSync(path.join(root, "gradlew"), 0o755);
  fs.writeFileSync(path.join(fakeBin, "python3"), "#!/usr/bin/env bash\nexit 0\n");
  fs.chmodSync(path.join(fakeBin, "python3"), 0o755);

  const result = spawnSync(
    "bash",
    [path.join(scripts, "render-ab.sh"), input, lanes],
    {
      encoding: "utf8",
      env: {
        ...process.env,
        PATH: `${fakeBin}${path.delimiter}${process.env.PATH}`,
        RC_LANE_TEST_ARGS: argsFile,
        XDG_CACHE_HOME: cacheRoot,
      },
    },
  );
  return { argsFile, cacheRoot, result };
}

test("fails before rendering when the shared font cache is absent", () => {
  const { cacheRoot, result } = fixture({ withFontCache: false });
  assert.equal(result.status, 1);
  assert.match(
    result.stderr,
    new RegExp(`no Google Fonts cache directory at ${cacheRoot}/composeai/fonts`),
  );
});

test("passes the shared font cache to both Android render harnesses", () => {
  const { argsFile, cacheRoot, result } = fixture({ withFontCache: true });
  assert.equal(result.status, 0, result.stderr);
  const args = fs.readFileSync(argsFile, "utf8").split("\n");
  assert.ok(args.includes("--tests"));
  assert.ok(args.includes("*RcViewPlayerRenderHarness*"));
  assert.ok(args.includes("*RcEmbeddedRenderHarness*"));
  assert.ok(
    args.includes(`-Pcomposeai.fonts.cacheDir=${cacheRoot}/composeai/fonts`),
  );
});

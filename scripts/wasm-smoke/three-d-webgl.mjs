#!/usr/bin/env node
/**
 * The 3D extension's WebGL2 depth renderer, tested in a real browser.
 *
 * `:rc-player-3d:wasmWebGlTestDist` stages the production-compiled `wasmJs` test executable of
 * `:rc-player-3d` next to Skiko's runtime and a page (`src/wasmJsTest/page/index.html`) that runs
 * every test in it and publishes the results on `window`. This serves that directory, opens it in
 * Chromium, and fails if any test failed or none ran.
 *
 * The test that matters here is `Rc3DWebGlDepthTest`: it draws the scenes the Android depth
 * device test draws through both the WebGL2 renderer and the software rasterizer and holds them to
 * the same thresholds (coverage within 4%, a mean colour difference of at most 6 levels). Its
 * `Rc3DProbe` lines, printed here, are the per-frame timings of both paths.
 *
 * A GPU-less runner has WebGL2 only through Chrome's software GL (SwiftShader), which Chrome no
 * longer falls back to by itself: `--enable-unsafe-swiftshader` asks for it. It is safe here, the
 * page being our own bundle. Set `RC_3D_REQUIRE_WEBGL2=0` to let a browser without WebGL2 pass
 * (the test then skips its comparisons); by default that is a failure, so a lost flag cannot turn
 * the lane into a no-op.
 *
 * Set `RC_3D_OUT` to a directory to write each scene's render there, software on the left and
 * WebGL2 on the right (`renders/rc-3d-extension/webgl2-*.png` are these).
 *
 * Usage: `node three-d-webgl.mjs [dist-directory]`, default
 * `rc-player/three-d/build/wasmWebGlTestDist`. Set `CHROMIUM_EXECUTABLE` to launch a browser
 * Playwright did not install itself.
 */

import { createServer } from 'node:http';
import { createReadStream } from 'node:fs';
import { mkdir, stat, writeFile } from 'node:fs/promises';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const REPO_ROOT = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const DIST = resolve(
  process.argv[2] ?? join(REPO_ROOT, 'rc-player', 'three-d', 'build', 'wasmWebGlTestDist'),
);
const TIMEOUT_MS = 600_000;
const REQUIRE_WEBGL2 = process.env.RC_3D_REQUIRE_WEBGL2 !== '0';

const MIME_TYPES = new Map(
  Object.entries({
    '.html': 'text/html; charset=utf-8',
    '.mjs': 'text/javascript; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.wasm': 'application/wasm',
  }),
);

function startServer() {
  const server = createServer(async (request, response) => {
    const requestedPath = new URL(request.url, 'http://127.0.0.1').pathname;
    const relative = normalize(decodeURIComponent(requestedPath)).replace(/^([/\\])+/, '');
    const file = join(DIST, relative === '' ? 'index.html' : relative);
    if (file !== DIST && !file.startsWith(DIST + sep)) {
      response.writeHead(403).end('forbidden');
      return;
    }
    try {
      const info = await stat(file);
      if (!info.isFile()) throw new Error('not a file');
      response.writeHead(200, {
        'content-type': MIME_TYPES.get(extname(file)) ?? 'application/octet-stream',
        'content-length': info.size,
        'cache-control': 'no-store',
      });
      createReadStream(file).pipe(response);
    } catch {
      response.writeHead(404).end('not found');
    }
  });
  return new Promise((resolveServer) => {
    server.listen(0, '127.0.0.1', () => resolveServer(server));
  });
}

const server = await startServer();
const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM_EXECUTABLE || undefined,
  args: ['--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
});
let failed = false;
try {
  const page = await browser.newPage();
  const probes = [];
  page.on('console', (message) => {
    const text = message.text();
    if (text.startsWith('Rc3D')) probes.push(text);
  });
  page.on('pageerror', (error) => {
    console.error(`page error: ${error.stack || error}`);
    failed = true;
  });
  const { port } = server.address();
  console.log(`rc-player-3d WebGL2 tests: ${DIST}`);
  await page.goto(`http://127.0.0.1:${port}/index.html`);
  await page.waitForFunction(() => window.rcTestsDone === true, null, { timeout: TIMEOUT_MS });
  const renderer = await page.evaluate(() => {
    const gl = new OffscreenCanvas(1, 1).getContext('webgl2');
    if (!gl) return null;
    const info = gl.getExtension('WEBGL_debug_renderer_info');
    return info ? gl.getParameter(info.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER);
  });
  const results = await page.evaluate(() => window.rcTestResults);
  if (process.env.RC_3D_OUT) {
    const renders = await page.evaluate(() => globalThis.rcRenders ?? {});
    await mkdir(process.env.RC_3D_OUT, { recursive: true });
    for (const [name, base64] of Object.entries(renders)) {
      await writeFile(join(process.env.RC_3D_OUT, `webgl2-${name}.png`), Buffer.from(base64, 'base64'));
    }
  }
  console.log(`WebGL2 renderer: ${renderer ?? 'none'}`);
  for (const line of probes) console.log(`  ${line}`);
  for (const result of results) {
    console.log(`${result.ok ? (result.skipped ? 'SKIP' : 'PASS') : 'FAIL'} ${result.name}`);
    if (!result.ok) {
      console.log(result.error);
      failed = true;
    }
  }
  if (!results.some((result) => result.name.includes('Rc3DWebGlDepthTest'))) {
    console.error('Rc3DWebGlDepthTest did not run');
    failed = true;
  }
  if (REQUIRE_WEBGL2 && (renderer === null || probes.some((line) => line.includes('no WebGL2')))) {
    console.error('no WebGL2 context: the depth renderer went untested');
    failed = true;
  }
} finally {
  await browser.close();
  server.close();
}
process.exit(failed ? 1 : 0);

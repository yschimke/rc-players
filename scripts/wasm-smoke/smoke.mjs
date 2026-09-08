#!/usr/bin/env node
/**
 * The headless-browser smoke run over the shipped Wasm player bundle.
 *
 * `:rc-player-wasm:wasmPlayerTestDist` stages the optimized distribution next to four `.rc`
 * documents written by the *authoritative AndroidX writer* (`:rc-player-compat-tests`' fixture
 * generators) plus one committed image fixture — it was built that way so a browser smoke test
 * could run against real writer output, and then nothing opened it (#77). This is the thing that
 * opens it.
 *
 * `wasmJsBrowserTest` is not the same lane and does not replace this one: it runs the Kotlin
 * `commonTest` sources against a Karma-hosted harness, so it exercises the player's *code* but
 * never the artifact — not `rcPlayer.mjs` and `rcPlayer.wasm` as the production compiler emitted
 * and `wasmPlayerDist` laid them out, not `index.html`, not the `js-joda` import map, not the
 * bundled font manifest, and not the embed contract in `docs/design/RC_PLAYER_EMBED.md` that every
 * host codes against. Each of those can break with the Kotlin tests still green.
 *
 * What a case asserts:
 *
 *   * the page reaches `data-rc-player-state="ready"` (or `"error"`, on the one case where that
 *     is the point) and publishes the embed contract's version,
 *   * no exception escaped to the page, and nothing reached `console.error` that the case did not
 *     ask for,
 *   * the canvas is not blank — see `assertNotBlank`, which is what makes this a render test
 *     rather than a "the module instantiated" test.
 *
 * Usage: `node smoke.mjs [dist-directory]`, default `rc-player/wasm/build/wasmTestDist`.
 * Set `CHROMIUM_EXECUTABLE` to launch a browser Playwright did not install itself.
 */

import { createServer } from 'node:http';
import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { PNG } from 'pngjs';

const REPO_ROOT = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const DIST = resolve(
  process.argv[2] ?? join(REPO_ROOT, 'rc-player', 'wasm', 'build', 'wasmTestDist'),
);

// A cold Wasm start on a shared CI runner is slow, and the player's own handoff tail is disabled
// below, so this bounds instantiation plus first draw rather than any deliberate wait. A bundle
// that is broken rather than slow does not spend it: `waitForSettled` gives up as soon as the page
// reports an error.
const READY_TIMEOUT_MS = 90_000;

const VIEWPORT = { width: 800, height: 600 };

/**
 * `handoffDelayMs=0` because this driver composites the result itself: Playwright's screenshot
 * drives its own compositor frame and `assertNotBlank` then checks the pixels that came back, so a
 * surface that was not presented yet cannot slip past. That is exactly the exemption
 * `docs/design/RC_PLAYER_EMBED.md` names for the parity driver; a host that reveals an iframe on
 * `ready` without checking must keep the default 1.5 s.
 */
const COMMON_QUERY = 'handoffDelayMs=0';

const CASES = [
  {
    name: 'androidx-baseline',
    description: 'the AndroidX writer\'s baseline document',
    query: `src=androidx-baseline.rc&${COMMON_QUERY}`,
  },
  {
    name: 'androidx-layout',
    description: 'the AndroidX writer\'s layout document',
    query: `src=androidx-layout.rc&${COMMON_QUERY}`,
  },
  {
    name: 'androidx-scroll',
    description: 'the AndroidX writer\'s scrolling document',
    query: `src=androidx-scroll.rc&${COMMON_QUERY}`,
  },
  {
    name: 'androidx-component-value',
    description: 'the AndroidX writer\'s ComponentValue document',
    query: `src=androidx-component-value.rc&${COMMON_QUERY}`,
  },
  {
    name: 'image-background-button',
    description: 'the committed image-background button fixture',
    query: `src=ImageBackgroundRemoteButton-454x200.rc&${COMMON_QUERY}`,
  },
  {
    // The negative case is load-bearing, not decoration. Without it a bundle that reported `ready`
    // unconditionally — or one whose failure path stopped setting the marker — would pass every
    // case above, and hosts wait on exactly these two markers.
    name: 'missing-document',
    description: 'a `?src=` that 404s reports an error rather than a render',
    query: `src=does-not-exist.rc&${COMMON_QUERY}`,
    expect: 'error',
  },
  {
    // `window.rcPlayerLoad` is the warm path every embedding host uses after the first document
    // (docs/design/RC_PLAYER_EMBED.md); it lives entirely in the shipped bundle's `js(...)` blocks,
    // so no Kotlin test can reach it.
    name: 'document-swap',
    description: '`window.rcPlayerLoad` swaps a second document into the running player',
    query: `src=androidx-baseline.rc&${COMMON_QUERY}`,
    swapTo: 'androidx-layout.rc',
  },
];

const MIME_TYPES = new Map(
  Object.entries({
    '.html': 'text/html; charset=utf-8',
    '.mjs': 'text/javascript; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.wasm': 'application/wasm',
    '.ttf': 'font/ttf',
    '.txt': 'text/plain; charset=utf-8',
    // Not a registered type. The player reads `?src=` with `fetch` and `arrayBuffer()`, which does
    // not care, but serving it as anything text-shaped would invite a transcoding proxy to touch
    // the bytes.
    '.rc': 'application/octet-stream',
  }),
);

/**
 * A static file server over the staged distribution.
 *
 * Same-origin throughout, because the bundle is not merely fetched: `rcPlayer.mjs` is loaded as a
 * module, resolves `@js-joda/core` through the import map in `index.html`, instantiates
 * `rcPlayer.wasm`, and posts to `window.parent` with `window.location.origin` as the target. A
 * `file://` page satisfies none of that, which is why this is a server and not a path.
 */
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
        // Every case gets a fresh browser context, but the bundle is re-fetched several times in
        // one run and a cached 404 for the negative case would be indistinguishable from a live
        // one.
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

/**
 * The floor for "something was drawn", in pixels that differ from the page's dominant colour.
 *
 * Deliberately absolute rather than a share of the viewport: these fixtures are documents of their
 * own declared size laid out in an 800x600 page, and the smallest of them (the AndroidX writer's
 * ComponentValue document) inks about 1,200 pixels — 0.25% of the viewport, and a perfectly good
 * render. A percentage threshold tuned to the *largest* fixture would fail it; this one clears the
 * smallest by roughly 5x while a surface that drew nothing scores exactly 0.
 */
const MINIMUM_INK_PIXELS = 250;

/**
 * Fail a render that produced nothing to look at.
 *
 * A Wasm regression that survives compilation typically still reaches `ready` — the document
 * decodes, composition runs, and the draw pass produces an empty surface. Counting the pixels that
 * are *not* the background is the cheapest oracle that separates that from a real render without
 * pinning a baseline image. It is deliberately not a pixel comparison: parity against AndroidX is
 * measured by the conformance lanes, and a tolerance-bearing baseline here would only make the
 * smoke run fragile without catching anything the conformance lanes miss.
 */
function assertNotBlank(screenshot, prefix = '') {
  const png = PNG.sync.read(screenshot);
  const histogram = new Map();
  for (let index = 0; index < png.data.length; index += 4) {
    const key = png.data.readUInt32BE(index);
    histogram.set(key, (histogram.get(key) ?? 0) + 1);
  }
  const pixels = png.width * png.height;
  const dominant = Math.max(...histogram.values());
  const ink = pixels - dominant;
  if (histogram.size < 2 || ink < MINIMUM_INK_PIXELS) {
    throw new Error(
      `${prefix}the canvas is blank — ${histogram.size} distinct colour(s) and ${ink} ` +
        `pixel(s) of ${pixels} differ from the background; at least ${MINIMUM_INK_PIXELS} ` +
        'are expected',
    );
  }
  return { colours: histogram.size, ink };
}

/**
 * Wait for the player to reach `ready` or `error`, or for the page to fail underneath it.
 *
 * Polled rather than `waitForFunction`, so `watched()` can end the wait: a bundle that does not
 * instantiate at all — a truncated `rcPlayer.wasm`, a missing `skiko.mjs`, an import map that
 * stopped resolving `@js-joda/core` — never sets either marker, and waiting `READY_TIMEOUT_MS` for
 * each of the cases below would turn the most basic breakage this lane exists to catch into the
 * slowest possible way to report it. The state is read before the faults are, so the expected
 * failure case still returns its `error` marker rather than tripping over the `console.error` the
 * player writes alongside it.
 */
async function waitForSettled(page, watched) {
  const deadline = Date.now() + READY_TIMEOUT_MS;
  for (;;) {
    const settled = await page.evaluate(() => ({
      state: document.documentElement.dataset.rcPlayerState ?? null,
      error: document.documentElement.dataset.rcPlayerError ?? null,
      contract: document.documentElement.dataset.rcPlayerContract ?? null,
    }));
    if (settled.state === 'ready' || settled.state === 'error') return settled;
    const faults = watched();
    if (faults.length > 0) {
      throw new Error(`the page failed before it settled:\n  ${faults.join('\n  ')}`);
    }
    if (Date.now() > deadline) {
      throw new Error(
        `no readiness marker after ${READY_TIMEOUT_MS / 1000}s ` +
          `(last state: ${settled.state ?? 'unset'})`,
      );
    }
    await page.waitForTimeout(250);
  }
}

async function runCase(browser, origin, testCase) {
  const context = await browser.newContext({ viewport: VIEWPORT });
  const page = await context.newPage();
  // Two lists, because they are not equally conclusive. An exception that escapes to the browser is
  // a failure on any page. A `console.error` usually is too — but Chromium writes one for every
  // failed network request, so the case that deliberately asks for a document that 404s produces
  // one before the player has set its marker. Treating that as a fault made this run flaky (it
  // fails on roughly half of local runs) and, worse, would have failed the case for doing exactly
  // what it is there to prove.
  const pageErrors = [];
  const consoleErrors = [];
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  page.on('pageerror', (error) => pageErrors.push(String(error)));
  const expectsError = testCase.expect === 'error';
  // What ends the wait early. An uncaught exception always does; a console error does unless this
  // case expects the load to fail. Read through a callback rather than handed over as an array,
  // because the listeners above push into these lists *while* the wait is running.
  const watched = expectsError ? () => pageErrors : () => [...pageErrors, ...consoleErrors];

  try {
    await page.goto(`${origin}/index.html?${testCase.query}`, { waitUntil: 'domcontentloaded' });
    let settled = await waitForSettled(page, watched);

    // The contract version is published before anything is loaded, so it is readable even on the
    // failure case. A host that resolved this bundle from npm reads it to decide what it is talking
    // to; a bundle that stopped publishing it would break that silently.
    if (settled.contract === null) {
      throw new Error('the bundle published no data-rc-player-contract');
    }

    if (expectsError) {
      if (settled.state !== 'error') {
        throw new Error(
          `expected the error marker, got "${settled.state}" — a host waiting on \`ready\` ` +
            'would screenshot nothing and call it a render',
        );
      }
      if (!settled.error) {
        throw new Error('the error marker carried no message');
      }
      // `reportFailure` writes the message to `console.error` on purpose, and Chromium logs the
      // 404 itself, so this is the one case where console errors are the assertion rather than a
      // finding. An uncaught exception still is not: `watched` above keeps failing on those.
      if (pageErrors.length > 0) {
        throw new Error(`the page threw while reporting the error:\n  ${pageErrors.join('\n  ')}`);
      }
      return { note: `reported "${settled.error}"` };
    }

    if (settled.state !== 'ready') {
      throw new Error(settled.error ?? 'the player reported an error');
    }
    let pixels = assertNotBlank(await page.screenshot());

    if (testCase.swapTo) {
      await page.evaluate((source) => window.rcPlayerLoad(source), testCase.swapTo);
      // The marker goes back to `loading` synchronously inside `rcPlayerLoad`, so this cannot
      // observe the outgoing render's `ready` and screenshot the document it just replaced.
      settled = await waitForSettled(page, watched);
      if (settled.state !== 'ready') {
        throw new Error(
          `the swap to ${testCase.swapTo} failed: ${settled.error ?? 'unknown error'}`,
        );
      }
      pixels = assertNotBlank(await page.screenshot(), 'after the swap, ');
    }

    const faults = [...pageErrors, ...consoleErrors];
    if (faults.length > 0) {
      throw new Error(`the page reported errors:\n  ${faults.join('\n  ')}`);
    }
    return { note: `${pixels.ink} px drawn in ${pixels.colours} distinct colours` };
  } finally {
    await context.close();
  }
}

async function main() {
  try {
    const info = await stat(join(DIST, 'index.html'));
    if (!info.isFile()) throw new Error('not a file');
  } catch {
    console.error(
      `No Wasm distribution at ${DIST}.\n` +
        'Build it first: ./gradlew :rc-player-wasm:wasmPlayerTestDist',
    );
    process.exitCode = 1;
    return;
  }

  const server = await startServer();
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({
    executablePath: process.env.CHROMIUM_EXECUTABLE || undefined,
  });
  console.log(`rc-player wasm smoke: ${DIST}`);

  const failures = [];
  try {
    for (const testCase of CASES) {
      const started = Date.now();
      try {
        const { note } = await runCase(browser, origin, testCase);
        const seconds = ((Date.now() - started) / 1000).toFixed(1);
        console.log(`  ok    ${testCase.name} — ${testCase.description} (${seconds}s; ${note})`);
      } catch (error) {
        console.log(`  FAIL  ${testCase.name} — ${testCase.description}`);
        console.log(`        ${error.message.split('\n').join('\n        ')}`);
        failures.push(testCase.name);
      }
    }
  } finally {
    await browser.close();
    await new Promise((done) => server.close(done));
  }

  if (failures.length > 0) {
    console.error(`\n${failures.length} of ${CASES.length} wasm smoke cases failed: ` +
      failures.join(', '));
    process.exitCode = 1;
    return;
  }
  console.log(`\nAll ${CASES.length} wasm smoke cases passed.`);
}

await main();

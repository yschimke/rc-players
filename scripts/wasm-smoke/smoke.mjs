#!/usr/bin/env node
/**
 * The headless-browser smoke run over the shipped Wasm player bundle.
 *
 * `:rc-player-wasm:wasmPlayerTestDist` stages the optimized distribution next to four `.rc`
 * documents written by the *authoritative AndroidX writer* (`:rc-player-compat-tests`' fixture
 * generators) plus committed fixtures: an image button, a 3D scene, and the Swift core's circular
 * progress document for the `namedValues` cases — it was built that way so a browser smoke test
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
 * Set `RC_SMOKE_ONLY` to a comma-separated list of case names to run only those.
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

/**
 * The embed contract version the bundle must publish, on both of the exports a host can read.
 *
 * Pinned rather than merely required to be present: a host that resolved this bundle from npm
 * branches on this number, and the npm package's *major* tracks it
 * (docs/design/RC_PLAYER_EMBED.md). A bump is a deliberate act — a query parameter's meaning, a
 * `data-rc-player-state` value, a `postMessage` payload or `window.rcPlayerLoad`'s behaviour
 * changed — so it should be a deliberate edit here too, in the same commit, rather than something
 * a presence check waves through.
 */
const EXPECTED_CONTRACT_VERSION = 2;

/**
 * The document, colour variable and override the `namedColour` and `namedExpectations` cases share:
 * the Swift core's circular progress document, whose `WearM3.primary` the override turns orange.
 */
const NAMED_COLOUR_FIXTURE = Object.freeze({
  src: 'CircularProgressRemote-384x384.rc',
  name: 'WearM3.primary',
  colour: '#FFFF8800',
  // The RGB the override draws: `colour` without its `#` and alpha.
  get expectRgb() {
    return Number.parseInt(this.colour.slice(3), 16);
  },
  // A second override the `namedValues` cases set against the first.
  blueColour: '#FF0000FF',
});

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
    // The draft 3D operations behind `?extensions=3d` (docs/design/RC_3D_EXTENSION.md): a torus
    // and a cube that need the depth buffer, a label over them and a sphere over the label. Under
    // the default renderer the depth-needing meshes go through the WebGL2 depth renderer, which
    // `webgl2Contexts` proves ran; the render is then held to the software rasterizer's at the
    // thresholds the Android device tests use.
    name: 'three-d-depth',
    description: 'a 3D document under `?extensions=3d`, WebGL2 depth against software',
    query: `src=rc3d-depth-scene.rc&extensions=3d&${COMMON_QUERY}`,
    expectWebGl2DepthRenderer: true,
    compareTo: `src=rc3d-depth-scene.rc&extensions=3d&rc3dRenderer=software&${COMMON_QUERY}`,
  },
  {
    // The extension is opt-in: without the parameter the same document fails to decode, as it does
    // on AndroidX's players, so the parity lanes keep comparing like with like.
    name: 'three-d-not-enabled',
    description: 'the 3D document without `?extensions=3d` is refused',
    query: `src=rc3d-depth-scene.rc&${COMMON_QUERY}`,
    expect: 'error',
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
  {
    // `window.rcPlayerLoadBytes` is the same swap for a host that already holds the bytes — a
    // dropped file, say — and is just as unreachable from Kotlin. The page fetches the document
    // itself and hands over a view into the *middle* of a larger buffer, so a player that read the
    // whole backing `ArrayBuffer` instead of the view's window would decode garbage and fail.
    name: 'document-swap-bytes',
    description: '`window.rcPlayerLoadBytes` swaps a document handed over as bytes',
    query: `src=androidx-baseline.rc&${COMMON_QUERY}`,
    swapTo: 'androidx-layout.rc',
    swapAsBytes: true,
  },
  {
    // `?namedValues=` colours, end to end through the shipped bundle's `js(...)` flattening, which no
    // Kotlin test reaches. Each spelling is compared with the others, never with a stored image, so
    // a Skia or font change cannot fail it; the override must also change the render, or a bundle
    // that dropped every value would pass.
    name: 'named-colour-spellings',
    description: '`?namedValues=` colour as #AARRGGBB, #RRGGBB and an ARGB number draws alike',
    namedColour: {
      src: NAMED_COLOUR_FIXTURE.src,
      name: NAMED_COLOUR_FIXTURE.name,
      reference: NAMED_COLOUR_FIXTURE.colour,
      expectRgb: NAMED_COLOUR_FIXTURE.expectRgb,
      // The last three only agree with `parseColor` in the shipped bundle if the Kotlin/Wasm number
      // and trim paths match JavaScript: numbers that wrap past 32 bits, and JS-only whitespace.
      sameAs: ['#FF8800', 'FFFF8800', 0xffff8800, -30720, 8589903872, 4294936576.5, '\uFEFF#FF8800\u3000'],
    },
  },
  {
    // `?namedValues=` element by element, through the shipped bundle's `js(...)` flattening
    // (docs/design/RC_PLAYER_EMBED.md, "namedValues in detail"). Each entry is a whole array (or a
    // raw query) and the render it must draw exactly: `good` (orange), `blue` or `plain` (the
    // document's own colour). A bad element is dropped on its own, so the good colour beside it
    // still draws; a bundle that dropped every override draws `plain` and fails.
    name: 'named-values-elements',
    description: 'a bad `?namedValues=` element is dropped alone: every shape, unicode, %, long values',
    namedExpectations: {
      src: NAMED_COLOUR_FIXTURE.src,
      good: { kind: 'color', name: NAMED_COLOUR_FIXTURE.name, value: NAMED_COLOUR_FIXTURE.colour },
      blue: { kind: 'color', name: NAMED_COLOUR_FIXTURE.name, value: NAMED_COLOUR_FIXTURE.blueColour },
      expectRgb: NAMED_COLOUR_FIXTURE.expectRgb,
      cases: (good, blue) => ({
        // Each bad element before and after the good one. The bundle's earlier one-`try`
        // flattening threw on `null.kind` and on `encodeURIComponent` of a lone surrogate, losing
        // every override, and let the unescaped `kind` that spells another row forge a blue
        // `color` row. That last one is also what catches the JavaScript no longer encoding `kind`
        // (the JVM tests build their rows themselves). The control characters that spell no row
        // were dropped harmlessly even then; they pin that an escaped kind stays one row.
        ...beforeAndAfter(good, {
          'a null element': null,
          'a lone surrogate in a name': { kind: 'string', name: '\uD800', value: 'x' },
          'a lone surrogate in a value': { kind: 'string', name: 'label', value: 'a\uDC00' },
          'control characters in a kind': { ...blue, kind: 'color\u0000\u0001' },
          // The bundle's row format, which NamedValueRowsTest pins.
          'a kind that spells another row': { ...blue, kind: 'x\u0000a\u0000b\u0000s\u0001color' },
        }),
        // Every JSON value type as an element.
        'a number element': { namedValues: [5, good], expect: 'good' },
        'a string element': { namedValues: ['color', good], expect: 'good' },
        'an array element': {
          namedValues: [good, ['color', blue.name, blue.value]], expect: 'good',
        },
        'a boolean element': { namedValues: [true, good, false], expect: 'good' },
        'a nested-object value': {
          namedValues: [good, { ...blue, value: { argb: blue.value } }], expect: 'good',
        },
        'a boolean colour value': { namedValues: [good, { ...blue, value: true }], expect: 'good' },
        // Stringified as documented ("`value` is stringified"), as HostApi.parseColor's String(v).
        'a one-element array value': {
          namedValues: [good, { ...blue, value: [blue.value] }], expect: 'blue',
        },
        // The doc drops a `kind` that is "unrecognized"; an array is not a kind string.
        'an array kind': { namedValues: [good, { ...blue, kind: ['color'] }], expect: 'good' },
        'an object kind': { namedValues: [good, { ...blue, kind: {} }], expect: 'good' },
        'a kind whose toString throws': {
          namedValues: [{ ...blue, kind: { toString: 1 } }, good], expect: 'good',
        },
        // Missing and extra fields.
        'no kind': { namedValues: [good, { name: blue.name, value: blue.value }], expect: 'good' },
        'no name': { namedValues: [good, { kind: 'color', value: blue.value }], expect: 'good' },
        'a null name': { namedValues: [good, { ...blue, name: null }], expect: 'good' },
        'no value': { namedValues: [good, { kind: 'color', name: blue.name }], expect: 'good' },
        'a null value': { namedValues: [good, { ...blue, value: null }], expect: 'good' },
        'extra fields': {
          namedValues: [good, { ...blue, extra: 1, Kind: 'nope', __proto__: null }], expect: 'blue',
        },
        'an empty object': { namedValues: [{}, good, {}], expect: 'good' },
        // Unicode in names and values.
        'emoji, combining and RTL names of unknown variables': {
          namedValues: [
            { kind: 'string', name: '\u{1F600}ש́‮', value: 'x' },
            good,
            { kind: 'color', name: 'WearM3.primarý', value: blue.value },
            { kind: 'color', name: '‮WearM3.primary', value: blue.value },
            { kind: 'color', name: 'WearM3.١primary', value: blue.value },
          ],
          expect: 'good',
        },
        'emoji, combining and RTL values': {
          namedValues: [
            good,
            { kind: 'string', name: 'label', value: 'é \u{1F600}\u{1F3FD} של‮' },
            { ...blue, value: `${blue.value}\u200F` },
            { ...blue, value: '\u{1F600}' },
          ],
          expect: 'good',
        },
        'a JS-trimmed line separator around a colour': {
          namedValues: [good, { ...blue, value: ` ${blue.value} ` }], expect: 'blue',
        },
        // Very long values.
        'a 200k-character value of an unknown variable': {
          namedValues: [good, { kind: 'string', name: 'n'.repeat(5000), value: '\u{1F600}'.repeat(100000) }],
          expect: 'good',
        },
        'a colour behind 100k spaces': {
          namedValues: [good, { ...blue, value: ' '.repeat(100000) + blue.value }], expect: 'blue',
        },
        // Duplicate names with a bad one in between: the last *good* one wins.
        'blue, a bad duplicate, then orange': {
          namedValues: [blue, { ...blue, value: '#nope' }, good], expect: 'good',
        },
        'blue, null, a lone surrogate, then orange': {
          namedValues: [blue, null, { ...blue, value: '\uD800' }, good], expect: 'good',
        },
        'orange then a bad duplicate': {
          namedValues: [good, { ...blue, value: 'FF0000F' }], expect: 'good',
        },
        'orange then blue': { namedValues: [good, blue], expect: 'blue' },
        // A `kind`, name or value that is valid only after one more percent-decoding.
        'a %-escaped kind': { namedValues: [good, { ...blue, kind: '%63olor' }], expect: 'good' },
        'a %-escaped name': {
          namedValues: [good, { ...blue, name: 'WearM3%2Eprimary' }], expect: 'good',
        },
        'a %-escaped value': {
          namedValues: [good, { ...blue, value: `%23${blue.value.slice(1)}` }], expect: 'good',
        },
        'malformed % sequences': {
          namedValues: [
            good,
            { ...blue, value: '%' },
            { ...blue, value: '%E0%A4%A' },
            { kind: 'string', name: '%uD800%', value: '%%%' },
          ],
          expect: 'good',
        },
        // Raw (not URL-encoded) input, as a host that forgot `encodeURIComponent` sends it: the
        // query layer percent-decodes once, so `%63olor` is `color` there (URL semantics, not the
        // player's), and an unencoded `#` starts the fragment and truncates the JSON.
        'raw query: a %-escaped kind': {
          rawNamedValues:
            `[{"kind":"%63olor","name":"${blue.name}","value":"%23${blue.value.slice(1)}"}]`,
          expect: 'blue',
        },
        'raw query: an unencoded #': {
          rawNamedValues: `[{"kind":"color","name":"${blue.name}","value":"${blue.value}"}]`,
          expect: 'plain',
        },
        // A JSON number String() writes with an exponent: HostApi.parseColor's `1e21 | 0` is
        // 0xDEA00000, so the Kotlin/Wasm number parser must read `1e+21` as JavaScript does.
        'a colour number written with an exponent': {
          namedValues: [good, { ...blue, value: 1e21 }],
          expect: [good, { ...blue, value: 0xdea00000 }],
        },
        // Nothing valid at all: the document's own colour.
        'only bad elements': {
          namedValues: [null, 5, { ...blue, kind: 'nope' }, { ...blue, value: '\uDC00' }],
          expect: 'plain',
        },
      }),
    },
  },
  {
    // `rc-cmp-player.js`, the library a page includes instead of driving the iframe by hand. It
    // ships in the distribution beside `index.html`, so it is tested against the same bundle: a
    // host page creates a player, hands it bytes, then swaps to a URL, awaiting each promise.
    name: 'embed-library',
    description: '`rc-cmp-player.js` loads bytes, then a URL, from a host page',
    embed: { first: 'androidx-baseline.rc', then: 'androidx-layout.rc' },
  },
  {
    // The same, with the host page on another origin from the library and the player — the shape
    // of a site including them from a CDN. `localhost` and `127.0.0.1` on one port are different
    // origins, so the one static server stands in for both. Nothing but `postMessage` crosses.
    name: 'embed-library-cross-origin',
    description: '`rc-cmp-player.js` drives a player on another origin',
    embed: { first: 'androidx-baseline.rc', then: 'androidx-layout.rc', crossOrigin: true },
  },
];

/**
 * The host page for the `embed` case: what a site including the library writes. Served by the
 * smoke server itself rather than staged, because it is the test's page, not the product's.
 */
const EMBED_HOST_PATH = '/__embed-host.html';
/** The host page; `lib` is the URL of `rc-cmp-player.js`, which may be on another origin. */
function embedHostPage(lib) {
  return `<!doctype html>
<html><head><meta charset="utf-8"><link rel="icon" href="data:,"><style>html,body{margin:0;background:#fff}</style></head>
<body><div id="host"></div><script src="${lib}"></script>
<script>
  window.player = RcCmp.createPlayer(document.getElementById('host'), {
    width: ${VIEWPORT.width}, height: ${VIEWPORT.height}, handoffDelayMs: 0,
    onError: (error) => (window.embedErrors = window.embedErrors || []).push(error.message),
  });
</script></body></html>`;
}

/**
 * The one console error the embed case expects: the library opens the player page with no
 * `?src=` and waits for it to settle on exactly this, as RC_PLAYER_EMBED.md prescribes.
 */
const EXPECTED_EMBED_STARTUP_ERROR = 'Missing ?src';

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
  // The `namedExpectations` case sends request lines of over a megabyte (100,000 emoji, 12
  // characters each once percent-encoded); Node's default 16 KiB header limit resets them.
  const server = createServer({ maxHeaderSize: 4_000_000 }, async (request, response) => {
    const requestedPath = new URL(request.url, 'http://127.0.0.1').pathname;
    if (requestedPath === EMBED_HOST_PATH) {
      const lib = new URL(request.url, 'http://127.0.0.1').searchParams.get('lib') ?? '';
      if (!/^(https?:\/\/[^"<>\s]+|[\w./-]+)$/.test(lib)) {
        response.writeHead(400).end('bad lib');
        return;
      }
      response
        .writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' })
        .end(embedHostPage(lib));
      return;
    }
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
  return { png, colours: histogram.size, ink };
}

/**
 * The share of pixels a swap has to repaint before it counts as having loaded another document.
 *
 * `rcPlayerLoad` cycles the readiness marker synchronously, from JavaScript, before any of the
 * Kotlin side runs — so a player that dropped the new source on the floor and left the outgoing
 * document on screen would still reach `ready` again with a canvas that is not blank, and the
 * swap case would pass having proved nothing. The two documents it uses differ over roughly half
 * the viewport; 1% is far below that and far above the antialiasing noise two renders of the
 * *same* document would produce.
 */
const MINIMUM_SWAP_CHANGE = 0.01;

/** Fail a swap that repainted (almost) nothing, which is how "the source was ignored" looks. */
function assertRenderChanged(before, after) {
  if (before.width !== after.width || before.height !== after.height) {
    // The viewport is fixed for the whole case, so this cannot happen by design — and if it ever
    // does, silently skipping the comparison is the wrong answer.
    throw new Error(
      `the swap changed the surface size, ${before.width}x${before.height} -> ` +
        `${after.width}x${after.height}`,
    );
  }
  let changed = 0;
  for (let index = 0; index < before.data.length; index += 4) {
    if (before.data.readUInt32BE(index) !== after.data.readUInt32BE(index)) changed += 1;
  }
  const pixels = before.width * before.height;
  if (changed / pixels < MINIMUM_SWAP_CHANGE) {
    throw new Error(
      `the swap repainted ${changed} of ${pixels} pixels ` +
        `(${((changed / pixels) * 100).toFixed(3)}%) — the player kept showing the outgoing ` +
        'document rather than loading the new source',
    );
  }
  return changed / pixels;
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
      // Both halves of the published version, so they can be checked against each other as well as
      // against the expected number.
      contractAttribute: document.documentElement.dataset.rcPlayerContract ?? null,
      contractGlobal: window.rcPlayerContractVersion ?? null,
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

/**
 * How far a 3D render may stray from the software rasterizer's: the Android device tests' own
 * thresholds. Coverage is the pixels that are not the page background; colour is the mean
 * per-channel difference over the pixels both renders cover.
 */
const MAX_3D_COVERAGE_DIFFERENCE = 0.04;
const MAX_3D_MEAN_COLOUR_DIFFERENCE = 6;

/** Count WebGL2 contexts made on an `OffscreenCanvas`: the 3D depth renderer's, not Skia's. */
function countOffscreenWebGl2() {
  window.rcWebGl2Offscreen = 0;
  if (typeof OffscreenCanvas === 'undefined') return;
  const getContext = OffscreenCanvas.prototype.getContext;
  OffscreenCanvas.prototype.getContext = function (type, ...rest) {
    const context = getContext.call(this, type, ...rest);
    if (type === 'webgl2' && context) window.rcWebGl2Offscreen += 1;
    return context;
  };
}

/**
 * Hold the version [settled] read off the page to [EXPECTED_CONTRACT_VERSION].
 *
 * The contract version is published before anything is loaded, so it is readable even on the
 * failure case. Hosts read `window.rcPlayerContractVersion`; a page driving an iframe reads the
 * `data-rc-player-contract` attribute. Both must exist, agree, and carry the version this revision
 * of the repository documents — a bundle that published a different one, or only one of the two,
 * would break a host silently.
 */
function assertContractVersion(settled) {
  if (settled.contractGlobal !== EXPECTED_CONTRACT_VERSION) {
    throw new Error(
      `window.rcPlayerContractVersion is ${JSON.stringify(settled.contractGlobal)}, expected ` +
        `${EXPECTED_CONTRACT_VERSION} (bump EXPECTED_CONTRACT_VERSION here in the same commit ` +
        'that bumps the contract)',
    );
  }
  if (settled.contractAttribute !== String(EXPECTED_CONTRACT_VERSION)) {
    throw new Error(
      `data-rc-player-contract is ${JSON.stringify(settled.contractAttribute)}, expected ` +
        `"${EXPECTED_CONTRACT_VERSION}"`,
    );
  }
}

/**
 * Render [query] in a fresh page and return its screenshot, for [compareRenders] and the
 * `namedColour` and `namedExpectations` cases. It is held to what the header promises of every
 * case: the contract version, no uncaught exception, and nothing written to `console.error`.
 */
async function renderOnce(browser, origin, query) {
  const context = await browser.newContext({ viewport: VIEWPORT });
  try {
    const page = await context.newPage();
    const faults = [];
    page.on('console', (message) => {
      if (message.type() === 'error') faults.push(message.text());
    });
    page.on('pageerror', (error) => faults.push(String(error)));
    await page.goto(`${origin}/index.html?${query}`, { waitUntil: 'domcontentloaded' });
    const settled = await waitForSettled(page, () => faults);
    assertContractVersion(settled);
    if (settled.state !== 'ready') {
      throw new Error(`the render of ?${query} failed: ${settled.error ?? 'unknown error'}`);
    }
    const screenshot = PNG.sync.read(await page.screenshot());
    // One more frame, so an error logged just after the screenshot is still caught.
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => resolve())));
    if (faults.length > 0) {
      throw new Error(`the render of ?${query} reported errors:\n  ${faults.join('\n  ')}`);
    }
    return screenshot;
  } finally {
    await context.close();
  }
}

/** Hold [a] to [b]: the same pixels covered, in nearly the same colours. */
function compareRenders(a, b) {
  const background = (png) => {
    const histogram = new Map();
    for (let index = 0; index < png.data.length; index += 4) {
      const key = png.data.readUInt32BE(index);
      histogram.set(key, (histogram.get(key) ?? 0) + 1);
    }
    return [...histogram.entries()].reduce((x, y) => (y[1] > x[1] ? y : x))[0];
  };
  const backgroundA = background(a);
  const backgroundB = background(b);
  let coveredB = 0;
  let differ = 0;
  let total = 0;
  let both = 0;
  for (let index = 0; index < a.data.length; index += 4) {
    const inA = a.data.readUInt32BE(index) !== backgroundA;
    const inB = b.data.readUInt32BE(index) !== backgroundB;
    if (inB) coveredB += 1;
    if (inA !== inB) differ += 1;
    if (inA && inB) {
      for (let channel = 0; channel < 3; channel += 1) {
        total += Math.abs(a.data[index + channel] - b.data[index + channel]);
      }
      both += 1;
    }
  }
  const mean = total / Math.max(both * 3, 1);
  if (coveredB === 0) throw new Error('the software render drew nothing');
  if (differ > coveredB * MAX_3D_COVERAGE_DIFFERENCE) {
    throw new Error(`coverage differs on ${differ} of ${coveredB} pixels`);
  }
  if (mean > MAX_3D_MEAN_COLOUR_DIFFERENCE) {
    throw new Error(`mean colour difference ${mean.toFixed(2)} levels`);
  }
  return `coverage differs on ${differ} of ${coveredB} px, mean colour difference ${mean.toFixed(2)}`;
}

async function runCase(browser, origin, testCase) {
  if (testCase.namedColour) return runNamedColourCase(browser, origin, testCase.namedColour);
  if (testCase.namedExpectations) {
    return runNamedExpectationsCase(browser, origin, testCase.namedExpectations);
  }
  const context = await browser.newContext({ viewport: VIEWPORT });
  const page = await context.newPage();
  if (testCase.expectWebGl2DepthRenderer) await page.addInitScript(countOffscreenWebGl2);
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

  if (testCase.embed) {
    try {
      return await runEmbedCase(page, origin, testCase.embed, pageErrors, consoleErrors);
    } finally {
      await context.close();
    }
  }

  try {
    await page.goto(`${origin}/index.html?${testCase.query}`, { waitUntil: 'domcontentloaded' });
    let settled = await waitForSettled(page, watched);

    assertContractVersion(settled);

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
    let swapNote = '';

    if (testCase.swapTo) {
      const before = pixels.png;
      if (testCase.swapAsBytes) {
        await page.evaluate(async (source) => {
          const bytes = new Uint8Array(await (await fetch(source)).arrayBuffer());
          const padded = new Uint8Array(bytes.length + 32).fill(0xff);
          padded.set(bytes, 16);
          window.rcPlayerLoadBytes(padded.subarray(16, 16 + bytes.length));
          // Copied during the call: scribbling over the buffer afterwards must not reach the
          // document the player decodes.
          padded.fill(0);
        }, testCase.swapTo);
      } else {
        await page.evaluate((source) => window.rcPlayerLoad(source), testCase.swapTo);
      }
      // The marker goes back to `loading` synchronously inside both swap functions, so this cannot
      // observe the outgoing render's `ready` and screenshot the document it just replaced.
      settled = await waitForSettled(page, watched);
      if (settled.state !== 'ready') {
        throw new Error(
          `the swap to ${testCase.swapTo} failed: ${settled.error ?? 'unknown error'}`,
        );
      }
      pixels = assertNotBlank(await page.screenshot(), 'after the swap, ');
      const changed = assertRenderChanged(before, pixels.png);
      swapNote = `, ${(changed * 100).toFixed(0)}% of the viewport repainted by the swap`;
    }

    let compareNote = '';
    if (testCase.expectWebGl2DepthRenderer) {
      const contexts = await page.evaluate(() => window.rcWebGl2Offscreen);
      if (!(contexts > 0)) {
        throw new Error(
          'the 3D depth renderer made no WebGL2 context: its draws went to the software ' +
            'rasterizer',
        );
      }
    }
    if (testCase.compareTo) {
      const reference = await renderOnce(browser, origin, testCase.compareTo);
      compareNote = `; against software: ${compareRenders(pixels.png, reference)}`;
    }

    const faults = [...pageErrors, ...consoleErrors];
    if (faults.length > 0) {
      throw new Error(`the page reported errors:\n  ${faults.join('\n  ')}`);
    }
    return {
      note: `${pixels.ink} px drawn in ${pixels.colours} distinct colours${swapNote}${compareNote}`,
    };
  } finally {
    await context.close();
  }
}

/** The query that shows [spec]'s document with its colour variable overridden by [value]. */
function namedColourQuery(spec, value) {
  return namedValuesQuery(spec.src, [{ kind: 'color', name: spec.name, value }]);
}

/** The query that shows [src] with the `?namedValues=` array [namedValues]. */
function namedValuesQuery(src, namedValues) {
  return (
    `src=${src}&namedValues=${encodeURIComponent(JSON.stringify(namedValues))}&` + COMMON_QUERY
  );
}

/** How many pixels of [a] and [b] differ; both come from the same fixed viewport. */
function differingPixels(a, b) {
  if (a.width !== b.width || a.height !== b.height) {
    throw new Error(`render sizes differ, ${a.width}x${a.height} and ${b.width}x${b.height}`);
  }
  let differ = 0;
  for (let index = 0; index < a.data.length; index += 4) {
    if (a.data.readUInt32BE(index) !== b.data.readUInt32BE(index)) differ += 1;
  }
  return differ;
}

/** How many pixels of [png] are within a few levels of the opaque colour `#RRGGBB` [rgb]. */
function pixelsNear(png, rgb, tolerance = 8) {
  const target = [(rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff];
  let near = 0;
  for (let index = 0; index < png.data.length; index += 4) {
    if (target.every((value, channel) => Math.abs(png.data[index + channel] - value) <= tolerance)) {
      near += 1;
    }
  }
  return near;
}

/**
 * The `namedColour` case. The same document under the same renderer draws the same pixels, so
 * every spelling of one colour must match the reference render exactly; and the reference must
 * differ from the document's own colour by at least `MINIMUM_INK_PIXELS`, which is what proves the
 * override was applied at all.
 */
async function runNamedColourCase(browser, origin, spec) {
  const plain = await renderOnce(browser, origin, `src=${spec.src}&${COMMON_QUERY}`);
  const reference = await renderOnce(browser, origin, namedColourQuery(spec, spec.reference));
  const changed = differingPixels(plain, reference);
  if (changed < MINIMUM_INK_PIXELS) {
    throw new Error(
      `overriding ${spec.name} with ${spec.reference} changed only ${changed} pixels: the ` +
        'override was not applied',
    );
  }
  // Changed is not enough: a reference drawn with a zero alpha also differs from the plain render.
  // The override has to draw its own colour.
  const drawn = pixelsNear(reference, spec.expectRgb);
  if (drawn < MINIMUM_INK_PIXELS) {
    throw new Error(
      `the ${spec.reference} render has only ${drawn} pixels near ` +
        `#${spec.expectRgb.toString(16).padStart(6, '0')}: the override did not draw its colour`,
    );
  }
  for (const value of spec.sameAs) {
    const render = await renderOnce(browser, origin, namedColourQuery(spec, value));
    const differ = differingPixels(reference, render);
    if (differ !== 0) {
      throw new Error(
        `${JSON.stringify(value)} drew ${differ} pixels differently from ` +
          `${JSON.stringify(spec.reference)}`,
      );
    }
  }
  return {
    note: `${drawn} px drawn in the override's colour; ${spec.sameAs.length} other spellings identical`,
  };
}

/** Each of [bad] as an entry of its own, before and after [good]: it must draw the good render. */
function beforeAndAfter(good, bad) {
  return Object.fromEntries(
    Object.entries(bad).flatMap(([label, element]) => [
      [`${label}, before the good one`, { namedValues: [element, good], expect: 'good' }],
      [`${label}, after the good one`, { namedValues: [good, element], expect: 'good' }],
    ]),
  );
}

/**
 * The `namedExpectations` case: each entry must draw exactly the `good`, `blue` or `plain`
 * reference. Also records what a host could see of the dropped elements, which the contract keeps
 * silent: every console
 * message at any level and every `data-rc-player-*` attribute, against the plain render's.
 */
async function runNamedExpectationsCase(browser, origin, spec) {
  const plainQuery = `src=${spec.src}&${COMMON_QUERY}`;
  const references = {
    plain: await renderOnce(browser, origin, plainQuery),
    good: await renderOnce(browser, origin, namedValuesQuery(spec.src, [spec.good])),
    blue: await renderOnce(browser, origin, namedValuesQuery(spec.src, [spec.blue])),
  };
  if (differingPixels(references.plain, references.good) < MINIMUM_INK_PIXELS ||
    differingPixels(references.good, references.blue) < MINIMUM_INK_PIXELS ||
    pixelsNear(references.good, spec.expectRgb) < MINIMUM_INK_PIXELS) {
    throw new Error('the good and blue overrides do not draw distinguishable renders');
  }
  const failures = [];
  const entries = Object.entries(spec.cases(spec.good, spec.blue));
  for (const [label, entry] of entries) {
    const query = entry.rawNamedValues !== undefined
      ? `src=${spec.src}&namedValues=${entry.rawNamedValues}&${COMMON_QUERY}`
      : namedValuesQuery(spec.src, entry.namedValues);
    let render;
    try {
      render = await renderOnce(browser, origin, query);
    } catch (error) {
      failures.push(`${label}: ${error.message.split('\n')[0].slice(0, 200)}`);
      continue;
    }
    if (Array.isArray(entry.expect)) {
      // Another array that must draw the same: HostApi.parseColor parity for spellings.
      const same = await renderOnce(browser, origin, namedValuesQuery(spec.src, entry.expect));
      const differ = differingPixels(same, render);
      const applied = differingPixels(references.good, same) >= MINIMUM_INK_PIXELS;
      if (differ !== 0 || !applied) {
        failures.push(`${label}: ${differ} pixels differ from its equivalent (applied: ${applied})`);
      }
      continue;
    }
    const matches = Object.keys(references).filter(
      (name) => differingPixels(references[name], render) === 0,
    );
    if (!matches.includes(entry.expect)) {
      failures.push(
        `${label}: expected the ${entry.expect} render, drew ` +
          (matches.length ? `the ${matches.join('/')} render` : 'none of the references'),
      );
    }
  }
  const visible = await hostVisibleSignals(browser, origin, plainQuery);
  const dropped = await hostVisibleSignals(
    browser,
    origin,
    namedValuesQuery(spec.src, [null, { ...spec.blue, kind: 'nope' }, { ...spec.blue, value: 'x' }]),
  );
  const extra = {
    console: dropped.console.filter((line) => !visible.console.includes(line)),
    dataset: Object.entries(dropped.dataset)
      .filter(([key, value]) => visible.dataset[key] !== value)
      .map(([key, value]) => `${key}=${value}`),
    messages: dropped.messages.filter((m) => !visible.messages.includes(m)),
  };
  const note =
    `${entries.length} arrays; dropped elements visible to a host: ` +
    `console ${JSON.stringify(extra.console)}, dataset ${JSON.stringify(extra.dataset)}, ` +
    `postMessage ${JSON.stringify(extra.messages)}`;
  if (failures.length > 0) throw new Error([...failures, `(${note})`].join('\n'));
  return { note };
}

/** Every console line, `data-*` attribute on the root and parent `postMessage` of one load. */
async function hostVisibleSignals(browser, origin, query) {
  const context = await browser.newContext({ viewport: VIEWPORT });
  try {
    const page = await context.newPage();
    const lines = [];
    page.on('console', (message) => lines.push(`${message.type()}: ${message.text()}`));
    await page.addInitScript(() => {
      window.__seenMessages = [];
      window.addEventListener('message', (event) => {
        window.__seenMessages.push(JSON.stringify(event.data));
      });
    });
    await page.goto(`${origin}/index.html?${query}`, { waitUntil: 'domcontentloaded' });
    const settled = await waitForSettled(page, () => []);
    if (settled.state !== 'ready') throw new Error(`?${query} did not load: ${settled.error}`);
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => resolve())));
    const { dataset, messages } = await page.evaluate(() => ({
      dataset: { ...document.documentElement.dataset },
      messages: window.__seenMessages,
    }));
    return { console: lines, dataset, messages };
  } finally {
    await context.close();
  }
}

/** The `embed` case: drive the library from a host page, as a site including it would. */
async function runEmbedCase(page, origin, embed, pageErrors, consoleErrors) {
  // Cross-origin: the host page on `localhost`, the library and player on `127.0.0.1`.
  const hostOrigin = embed.crossOrigin ? origin.replace('127.0.0.1', 'localhost') : origin;
  const lib = embed.crossOrigin ? `${origin}/rc-cmp-player.js` : 'rc-cmp-player.js';
  await page.goto(`${hostOrigin}${EMBED_HOST_PATH}?lib=${encodeURIComponent(lib)}`, {
    waitUntil: 'domcontentloaded',
  });
  const unexpected = () => [
    ...pageErrors,
    ...consoleErrors.filter((text) => !text.includes(EXPECTED_EMBED_STARTUP_ERROR)),
  ];
  const settle = async (label, action) => {
    const outcome = await page.evaluate(action, embed).catch((error) => ({ error: error.message }));
    if (outcome && outcome.error) throw new Error(`${label}: ${outcome.error}`);
    const faults = unexpected();
    if (faults.length > 0) throw new Error(`${label}: the page reported errors:\n  ${faults.join('\n  ')}`);
  };

  await settle('loading bytes', async ({ first }) => {
    const bytes = await (await fetch(first)).arrayBuffer();
    try {
      await window.player.loadFromArrayBuffer(bytes);
      return null;
    } catch (error) {
      return { error: String(error.message || error) };
    }
  });
  const first = assertNotBlank(await page.screenshot(), 'after loading bytes, ');

  await settle('loading a URL', async ({ then }) => {
    try {
      await window.player.loadFromUrl(then);
      return null;
    } catch (error) {
      return { error: String(error.message || error) };
    }
  });
  const second = assertNotBlank(await page.screenshot(), 'after loading a URL, ');
  const changed = assertRenderChanged(first.png, second.png);
  let directNote = 'a host-realm ArrayBuffer accepted';

  if (embed.crossOrigin) {
    // Proves the case is what it claims: the host cannot reach into the player at all, so the
    // renders above can only have come over postMessage.
    const reachable = await page.evaluate(() => {
      try {
        return window.player.iframe.contentDocument !== null;
      } catch (error) {
        return false;
      }
    });
    if (reachable) throw new Error('the player iframe is reachable, so this is not cross-origin');
    directNote = 'the player unreachable except by postMessage';
  } else {
    // A host that calls the contract itself, without the library: `frame.contentWindow
    // .rcPlayerLoadBytes(buffer)` hands over an ArrayBuffer from the *host's* realm, which fails the
    // player realm's `instanceof ArrayBuffer`. The library's own calls pass a Uint8Array, which
    // `ArrayBuffer.isView` accepts across realms, so only a direct call shows it.
    await settle('a direct cross-realm rcPlayerLoadBytes call', async ({ first }) => {
      const buffer = await (await fetch(first)).arrayBuffer();
      const frame = window.player.iframe;
      try {
        frame.contentWindow.rcPlayerLoadBytes(buffer);
      } catch (error) {
        return { error: String(error.message || error) };
      }
      const root = frame.contentDocument.documentElement;
      while (root.dataset.rcPlayerState === 'loading') {
        await new Promise((resolve) => setTimeout(resolve, 50));
      }
      return root.dataset.rcPlayerState === 'ready'
        ? null
        : { error: root.dataset.rcPlayerError || 'the player reported an error' };
    });
    const third = assertNotBlank(await page.screenshot(), 'after a direct cross-realm call, ');
    assertRenderChanged(second.png, third.png);
  }

  // Failures must reject the load *and* reach `onError` — the `<rc-cmp-player>` element's
  // `rc-error` event hangs off that callback. Two kinds: bytes the *player* refuses, which come
  // back as an `rc-player-state` error, and a URL the *host* cannot fetch. Last, because the 404
  // and the player's own report are console errors this case otherwise counts as faults.
  const reported = await page.evaluate(async () => {
    const rejected = [];
    for (const attempt of [
      () => window.player.loadFromArrayBuffer(new Uint8Array([1, 2, 3])),
      () => window.player.loadFromUrl('does-not-exist.rc'),
    ]) {
      try {
        await attempt();
        rejected.push(null);
      } catch (error) {
        rejected.push(String(error.message || error));
      }
    }
    return { rejected, onError: window.embedErrors || [] };
  });
  if (reported.rejected.includes(null)) {
    throw new Error(`a failed load resolved instead of rejecting: ${JSON.stringify(reported)}`);
  }
  if (reported.onError.length !== 2) {
    throw new Error(
      `two failed loads reached onError ${reported.onError.length} time(s), expected twice ` +
        `(rejected with ${JSON.stringify(reported.rejected)})`,
    );
  }
  if (pageErrors.length > 0) {
    throw new Error(`the page threw while reporting the error:\n  ${pageErrors.join('\n  ')}`);
  }
  return {
    note:
      `${second.ink} px drawn in ${second.colours} distinct colours, ` +
      `${(changed * 100).toFixed(0)}% of the viewport repainted by the second load, ` +
      `${directNote}, and failed loads reported to onError`,
  };
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

  // `RC_SMOKE_ONLY=name,name` runs just those cases, for iterating on one; CI runs them all.
  const only = process.env.RC_SMOKE_ONLY?.split(',');
  const cases = only ? CASES.filter((c) => only.includes(c.name)) : CASES;
  if (only && cases.length !== only.length) {
    const known = new Set(CASES.map((c) => c.name));
    console.error(`RC_SMOKE_ONLY names no case: ${only.filter((n) => !known.has(n)).join(', ')}`);
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
    for (const testCase of cases) {
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
    console.error(`\n${failures.length} of ${cases.length} wasm smoke cases failed: ` +
      failures.join(', '));
    process.exitCode = 1;
    return;
  }
  console.log(`\nAll ${cases.length} ${only ? 'selected ' : ''}wasm smoke cases passed.`);
}

await main();

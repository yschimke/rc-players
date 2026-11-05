# Vendored: `remote-compose-player` (TypeScript Remote Compose player)

A pure-TypeScript renderer for the Remote Compose binary format — parses an RC
document and paints it to a Canvas2D (with a WebGL path for shader ops). Runs in
the browser, Node, and VS Code webviews.

We vendor it so `compose-preview` can offer an **in-browser Remote Compose render
lane** — the client-side counterpart of the CMP Kotlin/Wasm tier — so a viewer can
render a catalog's captured `.rc` document (the bytes packed at `ir/<id>.rc`)
without a server-side Robolectric daemon.

## Upstream

- Repository: <https://github.com/yschimke/remotecompose-experiments> (fork of
  `camaelon/remotecompose-experiments`, which is where changes are filed)
- Path: `players/typescript/`
- Commit: `f760e94cf07ab84818f5cdc67bee01e203743457` ("json2rc: canvas commands draw
  resources.bitmaps by name", camaelon `main`, 2026-11-03)
- License: Apache-2.0 (see `LICENSE`)

Previously vendored at `c2b2860b4ba43a754e5cafeb803a0e45cbbb3c12` ("json2rc: decode custom layout
component"), and before that at `53e19e93b3fa6dd4ec31dbdb4e32717ce8c9735d`.

The refresh to `f760e94c` was a three-way merge (base `c2b2860b`, ours, upstream `f760e94c`, each
restricted to the vendored file set), not a re-copy. It incorporates the ~40 upstream commits under
`players/typescript/` since `c2b2860b`:
- Conformance with the Java reference across layout and text: `BaseModernMeasurePolicy`-style
  `widthIn`/`heightIn` tightening and `DimensionConstraints` REQUIRED clamps, row/column weight fill,
  ellipsis re-layout with fewer lines (`CoreText`), START/MIDDLE ellipsis, `updatePadding`, size
  modifiers re-resolved (and EXACT scaled under `DENSITY_BEHAVIOR_DP`) on every measure, component
  values resolved on the first layout, `StateLayout`, templates, collapsible layouts that stop
  repainting every frame, TS number formatting matching Java, layout animation fixes.
- Compressed documents: `Header.COMPRESS = DEFLATE` (`src/core/Compression.ts`, its own synchronous
  inflater).
- `createHostApi`, a document's host contract by name (`src/web/HostApi.ts`).
- Sound: `SoundExpression` resolving variables, one voice per sound, synth and sequence types,
  `playSound` on a tap.
- `FloatFunctionDefine` / `FloatFunctionCall` implemented (`src/core/operations/FloatFunction.ts`),
  replacing their stubs; a pinnable clock (`src/core/ClockPin.ts`).
- 3D: optional supersampling for the software rasterizer, surfaces, font-box text heights; path
  expression ops and path trims (`decodePath`); text on path, crossover animations, video cues,
  image zoom, fullscreen; touch-up after drag and touches inside scrolled/padded containers; the
  layer alpha of `saveLayer` fades; `ValueIntegerExpressionChangeAction` with long-encoded ids.

Not vendored, as before: `src/json2rc/` and the new `src/json2rc-web.ts` (authoring tooling),
`packaging/`, `vscode-extension/`, `build-json2rc/`, `live/`, `doc/`, `web-player/`, `testdata/`,
upstream's `tests/` (they import a `build-node/` or `build-json2rc/` output this copy does not
build; `tests/compression_test.mjs` was run once against the merged source and passes), the
top-level `*.mjs`/`*.py` scripts, `DEBUGGING.md` and `GAPS.md`.

Upstream still ships stubs for `TextLookupInt` (153), `ImageAttribute`, `FontData` and
`CanvasOperations` (173), so our real implementations are kept. `DataDynamicListFloat` and
`UpdateDynamicFloatList` are upstream's own real implementations (they were already at `c2b2860b`).

### Local deltas retired by this refresh

Upstream now implements the same fix, so its version was taken and ours dropped:
- **`readNanIdBits` on `WireBuffer` / `LoomWireBuffer`, and `TextLayout` reading `fontSize` /
  `fontWeight` through it.** Upstream added the same bits-domain reader (remapped through
  `RemapContext.resolveNanIdBits` under pattern expansion) and reads both `TextLayout` fields with it.
- **`LayoutComponentContent` reporting its parent's size.** Upstream parents the content wrapper and
  resolves a `ComponentValue` naming it to the enclosing component's box (`ComponentValue.apply`).
- **`refreshPadding()`** (re-summing padding before measure): upstream's `updatePadding()` does the
  same at the start of `LayoutManager.measure`.
- **`ValueIntegerExpressionChangeAction` / `evaluateIntegerExpression`**: upstream's
  `CoreDocument.evaluateIntExpression` with long-encoded id handling and per-expansion re-indexing.
- **`<rc-player>` disconnect calls `destroy()`**, not `stop()`: upstream made the same change.

And one local delta retired because it turned out to double-apply a fix upstream already had:
- **dp scaling of `widthIn` / `heightIn` bounds in `LayoutManager`, `FitBoxLayout` and
  `FlowLayout`.** `WidthInModifier` / `HeightInModifier.updateVariables` already apply the density
  (as the reference's `DimensionInModifierOperation` does; the data pass runs it before layout), so
  multiplying by the dp scale again drew every `heightIn(52.dp)` Wear button, card and toggle row at
  twice its height at density 2 (208 px instead of 104). The refreshed player draws them at the
  CMP player's geometry (`renders/native-swift-dimension-in-bounds/button-filled-cmp.png`).
  EXACT_DP sizes are still scaled in `LayoutManager.measure` (see the next section).

Partially overlapping: upstream now clamps negative rounded-clip radii with `max(0, r)`; our
sanitiser (below) also squares non-finite radii, so ours is kept.

## Local modifications

Vendored from the upstream path above (`src/`, `package.json`,
`package-lock.json`, `tsconfig.json`, `README.md`, `BUILDING.md`). Upstream's own
`packaging/`, `vscode-extension/`, and standalone-site tooling are intentionally
not vendored; only the library source needed to build the browser bundle.

Local deltas over that snapshot (filed upstream unless noted):

- **Document-fetch HTTP errors** (`src/web/main.ts`). Reject failed HTTP responses before reading
  their bodies or replacing the current document. The error reports the HTTP status without
  copying potentially sensitive request URLs. Covered by `src/load-url.test.ts`. This local fix
  has not yet been filed upstream.

- **Delayed repaint cancellation** (`src/web/main.ts`). Track the repaint timeout as well as the
  animation frame, so stopping, pausing, destroying or replacing a frame cancels both. A pending
  delay also yields to an immediate interaction repaint without leaving a second loop behind.
  Covered by `src/lifecycle.test.ts`. This local lifecycle fix has not yet been filed upstream.

- **`CanvasOperations` (opcode 173).** Replaced the parse-only stub with a real
  implementation (`src/core/operations/layout/CanvasOperations.ts`, registered in
  `src/core/Operations.ts`), mirroring `CanvasOperations` in remote-core: a
  `Container` whose child draw ops (between it and its `ContainerEnd`) are grouped
  via `getList()` and replayed in `apply()` — so a component's `drawWithContent`
  decoration (Material3 button/card fill + label) paints instead of being dropped.
- **`LayoutComponent` draw-content guard** (`operations/layout/LayoutComponent.ts`).
  The draw-content path only replaces normal painting when the block actually
  holds a drawing op (a `PaintOperation`); a `DrawContentModifier` trailed solely
  by non-visual ops (e.g. `AccessibilitySemantics`) no longer blanks the component's real
  content. Known remaining gap: a fill whose path geometry comes from
  layout-bound `FloatExpression`s still resolves empty, so the fill *shape*
  (not its colour or the label) is missing — tracked separately.

- **`TextLookupInt` (opcode 153) and integer-expression ids** (`src/core/operations/TextLookupInt.ts`,
  `src/core/operations/IntegerExpression.ts`, registered in `src/core/Operations.ts`). The
  integer-indexed sibling of `TextLookup` (151) had no implementation, so a document containing one
  hit `Unknown operation opcode: 153` and abandoned the rest of the buffer — losing far more than the
  looked-up string. Added in [#3427](https://github.com/yschimke/compose-ai-tools/pull/3427); it was
  not written down here at the time.

- **An explicit size wins over a weight fill** (`src/core/operations/layout/managers/LayoutManager.ts`,
  new with the `f760e94c` refresh). Upstream now mirrors the Java core's
  `RowLayout.isInHorizontalFill()` / `ColumnLayout.isInVerticalFill()`: a row holding a weighted
  child measures at the full incoming width *whatever its own size modifier says*. Over an explicit
  `width(180.dp)` that is the AOSP view player's behaviour, not Compose's — the CMP player and the
  AndroidX embedded player apply the exact size as the constraint first, and the weights share it.
  Taken as-is it laid every Wear split toggle row and button group out full-bleed
  (`renders/rc-cmp-lane-ab/README.md`, "The AOSP view player lays the split row out full-bleed").
  The weight fill is therefore suppressed on an axis whose size modifier is EXACT or EXACT_DP; a
  wrapping or unsized row still fills, which matches Compose.

- **EXACT_DP sizes are scaled once, by the layout pass**
  (`src/core/operations/layout/modifiers/ModifierOperations.ts`, `.../managers/LayoutManager.ts`).
  `LayoutManager.measure` multiplies an EXACT_DP width/height by the document's dp scale, for a
  literal and a variable alike. Upstream now also scales EXACT_DP inside
  `WidthModifier`/`HeightModifier.updateVariables`; taking both would draw it at density², so the
  modifiers keep upstream's re-resolution and its EXACT-under-`DENSITY_BEHAVIOR_DP` scaling but leave
  EXACT_DP raw. Covered by `src/density.test.ts`.

- **`IntegerExpression` tells operators from ids by the declared value**
  (`src/core/operations/IntegerExpression.ts`). A set mask bit means "not a literal", which is an id
  when the written value is below `OFFSET` and an operator otherwise; the operator test reads
  `mValues`, not the resolved array, so a variable whose value falls in the operator range is not
  executed as an opcode. Upstream still reads the mask alone.

- **Path operands may be variables** (`src/web/CanvasPaintContext.ts`, `pathCoord`). A path
  coordinate that is a NaN-boxed id is dereferenced to its current value instead of being read as a
  literal NaN; now applied inside upstream's new `decodePath`, so trimmed paths get it too.

- **Text layout** (`src/web/CanvasPaintContext.ts` `layoutComplexText`, `CoreText`). Ours is kept
  over upstream's parallel rewrite: one-line clip/visible text is not wrapped, ellipsis is
  grapheme-safe, `height` is clamped to `maxHeight` with the natural height reported separately
  (`naturalHeight`), and autosized clip/visible text is laid out uncapped. Upstream's
  ellipsis-with-fewer-lines loop in `CoreText` is taken and reads `naturalHeight`, and its
  `visibleLines` count is added to our layout so that loop has what it needs.

- **A weighted child honours the size its parent distributed to it**
  (`src/core/operations/layout/managers/LayoutManager.ts`). Upstream's `WEIGHT` branch takes the
  child's modifier-defined size (`mPadBeforeWidth`, normally 0) rather than the full `maxWidth`,
  which is right for the case it was written for — a weight on the cross axis, or in a parent that
  wraps and so has no slack to distribute. But `RowLayout`/`ColumnLayout` communicate the share they
  decided on *as the incoming constraint*, re-measuring each weighted child with
  `minWidth == maxWidth == childWidth`; taking the modifier-defined size unconditionally discards
  that and re-measures the child at ~0. The branch now clamps the modifier-defined size into the
  incoming `[min, max]`, so a tight constraint wins and a loose one leaves upstream's behaviour
  intact. Filed upstream.

  Worth stating how this failed, because it is the reason the round-clip fixture grew a second
  assertion: the child is *laid out and painted* at that zero width, so its text re-wraps one word
  per line and each line is then centred about a zero-width box — landing at a negative x, outside
  the component, clipped away. Both card titles vanished from the watch-face fixture while the
  document parsed cleanly, warned about nothing, and still scored 78.8% canvas coverage, because two
  titles are ~0.001% of a 454x454 canvas. `rc-round-clip.test.mjs` now asserts on the text draws
  themselves (one `fillText` per string, no negative offsets), which is the signal coverage cannot
  carry.

- **Size-relative corner radii on `MODIFIER_ROUNDED_CLIP_RECT` (opcode 54)**
  (`src/core/operations/layout/modifiers/ModifierOperations.ts`, with a guard in
  `src/web/CanvasPaintContext.ts`). Each corner arrives as raw float32 bits that may be a
  NaN-encoded variable reference: a *fixed* shape (`RemoteRoundedCornerShape(4.dp)`) writes a dp
  literal, but a *size-relative* one — `RemoteCircleShape`, a 50% corner — writes an expression id
  computed from the component's measured width and height. `RoundedClipRectModifier.read` used
  `readFloat()`, which collapses that payload to a plain `NaN`; `ctx.roundRect` ignores a radius
  list containing a non-finite value entirely, so the following `clip()` got an **empty path** —
  and an empty clip hides everything drawn inside the component, not just its corners. A round
  watch face therefore replayed as a completely blank canvas while parsing cleanly and emitting no
  warning ([#2930](https://github.com/yschimke/compose-ai-tools/issues/2930)). The modifier now
  keeps the raw bits and implements `VariableSupport` the way `PaddingModifier` does, so a
  NaN-encoded corner is resolved against the context. The dp→px density scale is applied to
  *literal* corners only: a variable corner is derived from the component's measured size, which
  the engine already carries in generation pixels, so scaling it would double-apply the density.
  `apply()` re-resolves before clipping, because a corner expressed over the component's own size
  only has a value once that component has been measured. Independently, `roundedClipRect` now
  sanitises the two radii Canvas cannot take — non-finite (ignored, hence the empty path) and
  negative (throws, aborting the paint) both become a square corner — so a future unresolved corner
  degrades to a cosmetic difference rather than a blank or missing component. Over-large radii are
  passed through untouched: Canvas scales an *overlapping* set down proportionally by itself, and a
  single large corner on an oblong rect is valid geometry that the embedded player also draws
  unmodified. Guarded by `scripts/design-artifacts/rc-round-clip.test.mjs` against the committed
  round-clip fixture.

- **Concrete font stacks for the generic families** (`src/web/CanvasPaintContext.ts`). The typeface
  id → CSS family mapping named only the generics (`sans-serif` / `serif` / `monospace`, with
  `DEFAULT` and every unrecognised id collapsing to `sans-serif`). Android resolves those families
  to specific faces — `DEFAULT` and `sans-serif` are **Roboto**, not whatever the host calls
  `sans-serif` — so every string the player drew used a different typeface from the snapshot
  renderer, which reads as a permanent few-percent parity residual that no layout fix can close.
  `cssFontStackFor` now names the concrete face first and keeps the generic as the fallback, so a
  page that registers the faces matches the baked raster and one that does not renders exactly as
  before. Registering them is the harness's job (`scripts/design-artifacts/rc-fonts.mjs`), which
  also owns the weight ranges: declared at discrete weights, a request for an in-between weight
  (Wear M3 asks for 450) resolves upward to Medium and renders visibly too heavy.

- **Named font families, served from Google Fonts** (`src/web/WebFonts.ts`, wired into
  `src/web/CanvasPaintContext.ts` and `src/web/main.ts`). A document can name a family rather than
  pick one of the four generic typefaces (`RemoteFontFamily.Named(...)`), but that name never reached
  the paint layer: `CoreText.updateVariables` ends `else this.mType = this.mFontFamilyId`, so a named
  family arrives at `PaintBundle.TYPEFACE` as the document's **text id**, which `cssFontStackFor`
  didn't recognise and mapped to its `default` — every branded string silently rendered in Roboto.
  The operand is now disambiguated by magnitude (ids are handed out from `RemoteComposeState
  .START_ID` = 42 upward, so no text id can collide with the generics 0..3), resolved back through
  the text table, and the face registered on demand.

  Which families to fetch is stated by the document rather than guessed: a family namespaced
  `google:Orbitron` is fetched from the Google Fonts CSS API, an unprefixed one is only *named* and
  left to the host. Treating any unrecognised family as a Google Font would turn a typo — or a name
  that only means something on the host, like `SF Pro` — into a network request, with no way to say
  "this one is local".

  Three things this has to get right, all covered by
  `scripts/design-artifacts/rc-webfonts.test.mjs`:

  - **The request form must enumerate weights, not range them.** `wght@100..900` is rejected with
    HTTP 400 by any family that isn't variable (`Lobster`, `Pacifico`), and at the `<link>` a 400 is
    indistinguishable from a network failure — so a static family would be reported unavailable and
    fall back. The enumerated `ital,wght@0,100;…;1,900` is accepted for variable and static families
    alike, and the API returns only the faces the family actually ships, so over-asking is free.
  - **`@font-face` is lazy and canvas does not drive it.** `ctx.font` neither triggers a load nor
    waits for one, so the face is loaded explicitly. Note `document.fonts.check()` is useless as an
    assertion here — it answers *true* for a family that was never declared at all, so it cannot tell
    "registered" from "fell back"; the tests measure text width instead. The load is asked for *by
    font shorthand*, for the (weight, style) the paint op actually carries, so the browser's own CSS
    matching fetches just that face: the stylesheet declares every weight, but declaring is free and
    fetching is not — pulling all six of Orbitron's to draw one regular label would also hold
    `fontsReady()` open on faces nothing paints.
  - **Callbacks are per waiter and fire once.** Resolution runs per *paint*, so a settled variant
    must never re-announce — that would schedule a repaint from inside painting, forever — while a
    caller that arrives mid-fetch must still be recorded: with two players on a page, dropping the
    second leaves that canvas in the fallback permanently, since a static document has no later
    frame to recover on.
  - **Resolution is synchronous but fetching is not.** Resolution happens mid-paint, so the stack
    names the family immediately and the face lands later: interactive players repaint via
    `onFontLoaded`, and single-shot renderers await `player.fontsReady()` *after* the first paint,
    which is what discovers the families in the first place.

  `configureWebFonts({enabled, baseUrl})` is the embedder's switch — off for a webview whose CSP
  forbids the font origins or a hermetic CI lane, redirected for a mirror. A family that cannot be
  served never throws: it degrades to the fallback generic, because a document naming a font we
  can't fetch is an authoring fact, not a player fault.

  **Known asymmetry.** The snapshot renderer does *not* yet honour a named family in an `.rc`
  document — Robolectric resolves it through `DefaultTypefaceResolver`, which looks the name up as a
  file under `/system/fonts/` and finds nothing, so the baked raster stays Roboto while the browser
  lane now draws the real face. The hook to close this exists (`RemoteDocumentPlayer` takes a
  `TypefaceResolver`, and `renderers/android` already downloads Google Fonts for Compose's own font
  resolver via `GoogleFontCache`); what it needs is that downloader lifted out of `renderers/android`
  into a module the Remote Compose connector can also see, rather than a second copy of it. Until
  then the `Text/Branded` sticker is expected to read high on the PNG↔Remote-Compose parity page —
  that row is measuring the renderer gap, not a player regression.

  That gap also shows up as *clipping*, which is worth knowing before it is mistaken for a text-layout
  bug: an `.rc` carries geometry the authoring renderer measured, so while that renderer resolves the
  family to Roboto the player is drawing a wider face into a box measured for a narrower one. Closing
  the renderer side fixes the metrics and the typeface together.

## Building the browser bundle

```sh
npm ci
# esbuild IIFE, global `RC`. The browser build never touches the Node-only
# `canvas` dependency, so mark it external:
npx esbuild src/web/main.ts --bundle --outfile=web-player/bundle.js \
  --format=iife --target=es2020 --global-name=RC --external:canvas
```

The bundle exposes `RC.RcdPlayer`, `RC.createPlayer`, the `<rc-player>` custom
element, and `RC.base64ToArrayBuffer`. Render a document:

```js
const player = new RC.RcdPlayer(canvas);
await player.loadFromArrayBuffer(rcBytes);
// Named-value overrides (match a preview's declared knob names):
player.getRemoteContext().setNamedFloatOverride('progress', 0.15);
player.repaint();
```
- **Measure pass: a component rendered at a size it was never asked for**
  (`operations/layout/LayoutComponent.ts`, `operations/layout/managers/LayoutManager.ts`,
  `.../RowLayout.ts`, `.../ColumnLayout.ts`). Three related divergences from Compose's measure
  semantics, all silent — the document parses, no opcode is unknown, nothing warns:

  - `LayoutComponent.preferExactSize` kept the **last** fixed size modifier when a component
    carried two. The chain is emitted outermost-first and a widget appends its own default behind
    the caller's (`RemoteIcon(modifier = size(48.dp))` writes 48 then the built-in 24), so every
    icon rendered at 24 dp regardless of the request. Compose resolves `size(48).size(24)` to 48 —
    the outer call fixes the constraints and the inner is coerced into them — so the **first** fixed
    modifier now wins. Fixed-over-`FILL`/`WRAP` precedence is unchanged.
  - `LayoutManager.measure` forwarded the container's own `minWidth`/`minHeight` to its children.
    A min constraint bounds *that* component — a weighted row cell is handed min == max so it fills
    its slot — so passing it down made every child at least as large as the parent: a `size(20)`
    icon and a wrapped label both came out full-cell and overlapped their neighbours. It also kept
    `ColumnLayout.computeSize` from shrinking, since that loop reduces the child *max* per child:
    with a fixed min the second child was measured min > max (143.8 > 0). Children now get a zero
    minimum, which is what the scroll path in the same function already passed.
  - `RowLayout`/`ColumnLayout.internalLayoutMeasure` re-measured a weighted child with its **cross**
    axis pinned to the size from an earlier pass (`cm.getH(), cm.getH()`), taken while the available
    space was still being decremented per child — so every cell after the first carried an
    under-measured height and starved its own children to fit. Only the main axis is decided by the
    weight; the cross axis is now free, matching the weighted branch of `computeWrapSize` in the
    same files.

  Measured over the captured `remote-m3` documents (`scripts/design-artifacts/rc-compare.mjs`, JS
  player lane): mean mismatch against the AndroidX bake **1.32% → 0.96%** across 27 documents with
  no document regressing, `IconRemote` 5.83% → 0.00%, `ButtonGroupRemote` 2.84% → 0.14%. Guarded by
  `scripts/design-artifacts/rc-size-modifier.test.mjs` against the committed bundle.

## Validation

Built as above and rendered our actual captured `remote-m3` documents in headless
Chromium: `CircularProgressRemote.rc` paints correctly (the determinate arc at
its 0.66 default). The render is captured at
`docs/design/evidence/rc-ts-player/circularprogress-clientside.png`.

Known gaps to resolve when wiring the live viewer lane (tracked as follow-ups, not
blockers for vendoring):

- **Shader stickers** (e.g. `ShaderGradientSticker`) use the player's WebGL path,
  which needs a real WebGL context — headless Chromium must be launched with
  software GL (`--enable-unsafe-swiftshader`), as the repo's `serve-lanes` harness
  already does.
- **By-name overrides** (`setNamed*Override`) did not take effect in a first pass;
  the name lookup likely needs the `USER:`-domain-qualified name our connector
  binds. Until resolved, the lane renders the baked document but not live knob edits.

## Follow-up

Upstream is not published to npm. The plan is to publish this library (upstream or
ourselves) and switch this vendored copy for a normal npm dependency bundled by our
own esbuild, the way `three` is consumed by the VS Code webview.

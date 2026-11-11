# Vendored: `rc-embedded-player` (AndroidX experimental Compose embedded Remote Compose player)

`RcPlayer` is a **pure-Compose interpreter** for a Remote Compose `CoreDocument`. It walks the
document's operation tree and emits Compose layout and draw nodes directly, rather than painting to
a framework `Canvas` inside an Android `View`.

That contrast is why this repo vendors it. `remote-player-view`'s `RemoteComposePlayer` is an
Android `View` bridged into Compose. The embedded player is what a host embedding Remote Compose
content *inside* a Compose tree gets: different layout, text and draw code, so different pixels.
Having both lets the render lanes diff them against the same document.

**This module is a direct copy of upstream plus a short list of marked patches.** It is Android
only. The desktop-JVM cut that used to compile a platform-neutral subset of these sources was
removed on 2026-09-25; the CMP player (`rc-player/compose`) is this repository's JVM player.

## Upstream

- Repository: <https://github.com/androidx/androidx>
- Paths:
  - `compose/remote/remote-player-compose/src/main/java/androidx/compose/remote/player/compose/embedded`
  - `compose/remote/remote-player-compose/src/main/java/androidx/compose/remote/player/compose/utils`
- Commit: `21a1a95e41de8a72a30e70b471122530089a5c58` (`androidx-main`, 2026-11-10)
- License: Apache-2.0

## How the copy is made

1. Copy both upstream directories into `src/main/kotlin/ee/schimke/composeai/rcembedded/player/`
   (`utils/` under `player/utils/`), keeping upstream's subpackages (`components/`, `doc/`, `draw/`,
   `inspectable/`, `layout/`, `modifier/`, `resources/`, `state/`, from `82ec7b31a8c`) as they are.
   Leave out upstream's `README.md`.
2. Rewrite the packages: `androidx.compose.remote.player.compose.embedded` (and every subpackage
   under it) → `ee.schimke.composeai.rcembedded.player`, and
   `androidx.compose.remote.player.compose.utils` → `ee.schimke.composeai.rcembedded.player.utils`.
   Upstream publishes its own embedded player in the original package, so this copy cannot share
   it.
3. Run `./gradlew :third-party-rc-embedded-player:ktfmtFormat`. This module is formatted in ktfmt's
   kotlinlang style, which is what AndroidX uses (`build.gradle.kts`). The formatter changes import
   order in an upstream file, since the renamed imports sort differently. Where AndroidX's ktfmt
   version differs from this one it also adds a trailing comma to an argument list that is already
   broken over lines, and rewraps the odd line: `RcPlayerState.kt`, `components/RcPlayerTouch.kt`
   and `modifier/ClickModifier.kt` at this pin.
4. Re-apply the patches and version-skew shims below. Every patched line sits under a
   `// LOCAL PATCH (rc-players)` or `// VERSION SKEW (rc-players)` comment, and the helpers they need
   live in files of their own.

`scripts/diff-upstream.sh <androidx checkout>` diffs this copy against a checkout with the package
rewrite and import order normalised. Its output should be the patches and shims listed here and
nothing more than that formatting.

## Version skew

Upstream builds this player against the in-tree `remote-core` / `remote-player-core` /
`remote-creation-compose`. This module builds it against the published alphas the version catalog
pins (`compose-remote = 1.0.0-alpha23`). The player reaches `@RestrictTo(LIBRARY_GROUP)` members of
the core, so it is sensitive to the gap between `androidx-main` and the pinned alpha: pair a refresh
with a render of the embedded lane (`scripts/rc-lane-ab/render-ab.sh`), not just a compile. There is
no reflection left in the copied sources.

**Which commit, and why.** alpha23 was cut from `androidx-main` at `a0e84553095` (2026-10-29): its
`remote-core`, `remote-player-compose` and `remote-creation-compose` `-sources.jar`s match that
tree file for file (302/302, 103/103 and 213/214, the odd one out being the generated `R.java`).
The pin is `androidx-main` HEAD rather than that commit because HEAD compiles against alpha23 with
two one-line shims, and the player work after the cut (TextAttribute/TextMeasure ink bounds,
`697c967d0d9`; O(1) core-paint seeding, `e293d337e0f`; float variables resolved across the player,
`300a543b216`; the layer compositing strategy, `28fb380046b`) is what a comparison lane should
measure. Nothing the player gained after the cut reaches a `remote-core` member alpha23 lacks: the
graphics layer's `COMPOSITING_STRATEGY` attribute and the raw padding and dimension accessors the
float-variable sweep reads are all in the release. Move the pin back to the newest commit that
compiles if a later refresh needs more shimming.

The shims, both in `CreationVersionSkew.kt` and both to be removed once the pinned release catches
up. Each stands in for a name that moved after the cut; the file that uses it drops that one import
line and resolves the alias from its own package instead.

- `RemoteCapturedDocument` is a typealias of alpha23's `CapturedDocument`. `remote-creation-compose`
  renamed the class in `7177ff18cdd`, and `RcPlayer.kt` / `RcPlayerState.kt` import the new name.
- `PendingIntentActionHandler` is a typealias of alpha23's class of that name in
  `androidx.compose.remote.creation.compose.action`. `3be30799c59` moved it, unchanged but for its
  KDoc links, to `remote-player-compose`'s `androidx.compose.remote.player.compose.action`, where
  `RcPlayer.kt` now imports it from.

The previous pin's two shims retired with alpha23, which ships the core changes they stood in for:
`doc/RcPlayerPreprocess.kt`'s `floatExpressions` registration (`461d551382f`) and the
`ImageAttribute.imageId` reflection in `doc/` and `draw/CoreVersionSkew.kt` (`d18fe8f34fb`).

`SoundFeature` plays document sounds through the View player's `SoundSupport`, so this module now
depends on `remote-player-view`, as upstream's `remote-player-compose` does.

## Local patches

Each patch that fixes an upstream gap has an issue on this repository. That's where it gets reported
upstream and where it is retired from. The list is meant to shrink.

| Where | Patch | Issue |
| --- | --- | --- |
| `RcPlayer.kt` | Drops the `RemoteComposePlayerFlags.isEmbeddedPlayerEnabled` check. That flag belongs to AndroidX's own `remote-player-compose` and also routes its `RemoteDocumentPlayer` onto AndroidX's embedded player, so enabling it for this copy would change the View-player control lane too. Depending on this copy is the opt-in. | — (local by design) |
| `resources/RcPlayerFontResolution.kt` (`resolveFontFamily`) | A `google:` family is resolved from the shared machine-local Google Fonts cache first, at the document's variation axes, which the downloadable-font factory cannot apply. There is no cache on a device, so this only affects test renders; upstream's path runs everywhere else. | — (render-side; see [#501](https://github.com/yschimke/rc-players/pull/501)) |
| `components/RcPlayerTextLayout.kt` (`RcPlayerText`, the `CoreText` overload) | The text node is as wide as its lines, `ceil(max lineRight) - floor(min lineLeft)`, which is what `CoreText.computeWrapSize` takes from `AndroidPaintContext.getTightBoundingBox`. Upstream keeps `BasicText`'s own width, `ceil(maxIntrinsicWidth)`. With letter spacing that runs up to a pixel past the line's end, so a wrap-width button holding a 198.43 px label measured 320 rather than 319 and sat one pixel left of the core's layout and of the CMP player. A truncated or overflowing run keeps its full width. `RcCoreTextTightWidthTest` covers it. | [#572](https://github.com/yschimke/rc-players/issues/572) |

Files that exist only here:

- `RcPlayerLocalPatches.kt`: helpers for the patches above.
- `CreationVersionSkew.kt`: the version-skew shim described above.
- `../GoogleFontFamilies.kt`: the Google Fonts cache resolver behind the `google:` patch. It is null
  unless `composeai.fonts.cacheDir` is set, which is a render-side property no app sets.

### The 2026-11-11 refresh

Upstream from `a328222bef` to `21a1a95e41` retires no local patch and adds one shim, the
`PendingIntentActionHandler` move above. In the copied sources it changes:

- Float variables resolved across the player (`300a543b216`). A row's, column's or collapsible
  layout's child weight is read reactively (the new `layout/RcPlayerWeight.kt`) and a child whose
  weight is not a positive number is left unweighted, where a variable weight reached
  `Modifier.weight` as NaN and threw. `CoreText` letter spacing, line height and autosize bounds
  are resolved, and a value that is not a number is read as unset. A text's font weight is clamped
  to 1..1000. The content size a component publishes subtracts padding as `Modifier.padding`
  applies it, and is republished when a variable padding changes.
- `DrawTextOnCircle` reads its centre, radius, start angle and warp offset through the draw's read
  context (`6de498aec4f`), and draws nothing on an empty circle, as the View player does.
- The graphics layer's `COMPOSITING_STRATEGY` maps to Compose's `CompositingStrategy`
  (`28fb380046b`). The View player still ignores the attribute.
- The `RestrictedApiAndroidX` lint suppressions are gone from every file (`960c9542e6f`).

The other `remote-player-compose` commits in the range change upstream's own tests and API files,
neither of which is copied. `renders/embedded-refresh-21a1a95e41/` has the embedded lane over the
catalog corpus before and after.

### The 2026-11-05 refresh

Upstream from `5dab33683c` to `a328222bef` retires no local patch; it adds:

- Optional features (`8664ae1895b`): `RcPlayerFeature` / `RcPlayerFeatureSet`, and an `RcPlayer`
  overload taking an explicit set so R8 can drop the rest. The default overloads include every
  feature: 3D matrices (`ee9b168f6b7`), 2D meshes (`c8b6e53ecaa`), document sounds
  (`91cde6cbac9`), device sensors (`f5e45dbdba8`) and particle systems (`4029f120cd1`).
- An unknown-operation policy (`cbdf432ce5d`): a paint operation nothing executes logs once per
  class by default.
- Content layers allocated only when drawn (`969ad9e28e0`), positions tracked only for `CUSTOM`
  visibility animations (`896938df3dd`), and O(1) core-paint seeding (`e293d337e0f`).
- `TextAttribute` / `TextMeasure` bounds (`697c967d0d9`): measured from a paint spec captured from
  the preceding `PaintData`, with `Paint.getTextBounds` ink bounds as the View player measures them.
- The `RemoteCapturedDocument` rename (`7177ff18cdd`), shimmed above.

`RcAndroidxEmbeddedRenderHarness` follows AndroidX's published embedded player, whose
`RemoteImageSupport` moved to `embedded.resources` in alpha23.

### Retired by the 2026-10-27 refresh

Upstream from `11ece46a49` to `5dab33683c` took over five of the patches above, and this copy now
runs upstream's version. The regression tests written for them still pass against it.

- A component's canvas stream during setup (#54): upstream registers only the stream's static
  resources (bitmap metadata, text, fonts, sounds) and no longer evaluates it before layout
  (`55c1057c63d`), which is what the local walk did. `DynamicDimensionConstraintRenderTest` and
  `RcCanvasStreamBitmapRenderTest` pass on upstream's.
- Failed bitmap decodes are cached (#509): `7677a999c19`, the same `FailedBitmapDecode` marker.
- `maxLines` only truncates where the View player's does (#511): `resolveEffectiveMaxLines`
  (`0b4724b092f`).
- A font size deferred to the host resolves by id (#558): `resolveTextFontSizePx` reads the raw
  size through the reactive float graph (`9c7bf332b3c`). `RcHostDensityTextRenderTest` passes on it.
- Spring-animated floats (#551): upstream's `hasAnimation` counts a spring, and
  `rememberAnimatedRemoteFloat` decodes it as a spring and starts settled at its target.

This repository's tests followed the copy: they import from the new subpackages, build a
`GraphContext` over a `GraphTimeState`, run under Robolectric where the player now calls
`androidx.tracing` (`android.os.Trace` is not in the plain unit-test `android.jar`), and read the
core's own `raw*` accessors where they used to call this copy's reflective helpers.
`AndroidColorTableDriftTest` now compares the CMP table with AndroidX's shipped
`AndroidSystemColorMap`.

### Retired by the 2026-10-02 refresh

Upstream from `a12036836c` to `11ece46a49` took over three of the patches above, and this copy now
runs upstream's version:

- Value-producing ops in a draw stream (#510, #54): upstream evaluates `ColorExpression`,
  `ColorAttribute`, `ImageAttribute` and `TextMeasure` itself (`2149117c4`). Its `TextMeasure`
  measures with Compose's `TextMeasurer` rather than the framework `Paint` ink rectangle the local
  patch read, so `TextMeasureBehaviorTest`, which pinned the patch's selectors, is gone with it.
- A component's canvas-stream `BitmapData` (#54): upstream registers its metadata during setup (`3876c8b86`), so the local walk is gone; what is left of #54 is the narrower patch in the table above.
- `CoreText` line height from the drawn size (#511): upstream's `resolveCoreTextLineHeight`
  (`530b47a2e`). The only difference from the patch is autosize text with a non-zero `lineHeightAdd`,
  which upstream sizes in pixels and the patch scaled in `em`.

Also picked up: per-frame op-count resets (`b0376ab06`), `DENSITY_BEHAVIOR_DP` EXACT width/height
(`65a3bf206`), font weight/style preserved under font axes (`530b47a2e`), standalone ripple press
interactions (`d3a20802e`), disabled `AnimationSpec`s in `StateLayout`/`FitBox` (`cffb71595`) and
time variables read as integers (`c73c596e9`).

### Retired by the 2026-09-25 refresh

The previous copy was pinned at `c36509dbc4a` and had drifted into a fork: it split files for the
JVM cut, and carried its own versions of several fixes. Those have since landed upstream, or upstream
fixed them differently, and this refresh takes upstream's version:

- The inspection seam. Upstream's `RcPlayerInspector` replaces this repo's `LocalRcPlayerInspector`.
  It works through inspectable modifier elements gated on `isDebugInspectorInfoEnabled`. A host
  still gets actions through `onAction` / `onNamedAction`.
- Dynamic text colour, read by id when the op's own `isDynamicColorEnabled` says so. This replaces
  the local alpha heuristic.
- A rounded clip's radius. Upstream no longer reads (0, 1] as a fraction of the size.
- The host passes captured lambdas and `PendingIntent`s to `RcPlayer(state, lambdas = …)`. This
  replaces this copy's `RcPlayerState.lambdas`.
- Nested relative-URI bitmaps are skipped during setup (`applyDataOperationsWithoutBitmaps`).
- The transform origin reads `remote-core`'s declared default, 0.
- `Rc.AndroidColors`'s index table (#3), through `AndroidSystemColorMap`.
- The default paint colour, `updateColor` write-through, the computed-op index following a canvas
  stream, cold-start `ColorTheme` resolution with `Theme.SYSTEM`, the idle-reaching frame loop, and
  the removal of `autoUpdate`.
- Op-counter resets, dynamic path building (`PathCreate` / `PathCombine` / …) and the other upstream
  work since the old pin.
- `google:` families on a device. These now need the host to pass a `GmsFontTypefaceResolver` with
  its certificate array, as upstream intends. This copy no longer bakes in GMS certificates (#5).

The history of the old fork is in `git log -- third_party/rc-embedded-player`.

## Copyright

Every source file in this module carries the AOSP Apache-2.0 header. Upstream files keep theirs
verbatim; the files written here carry the same header, because they exist only to build and
exercise AOSP-derived code.

```sh
for f in $(find src -type f \( -name '*.kt' -o -name '*.xml' \)) build.gradle.kts; do
  head -6 "$f" | grep -q 'The Android Open Source Project' || echo "MISSING: $f"
done
```

## Tests

`src/test` is this repository's own suite: the render harnesses (`RcEmbeddedRenderHarness`,
`RcViewPlayerRenderHarness`) and regression tests for the patches. Upstream's tests depend on
AndroidX-internal test rules (`remote-testing`), so they are not copied.

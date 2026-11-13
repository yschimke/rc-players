# Forked: Compose Multiplatform Remote Compose creation

Covers the four creation modules whose sources are in this directory and beside it:

| Gradle project and artifact | Directory | AndroidX module it mirrors | Package |
| --- | --- | --- | --- |
| `rc-creation-writer` | `../remote-creation-writer` | none: stands where `remote-core`, `remote-creation-core` and `remote-creation` (Java) are | `ee.schimke.composeai.rccreation.common` |
| `rc-creation-expression` | `expression/` | `remote-creation-expression` | `ee.schimke.composeai.rccreation.compose` |
| `rc-creation-compose` | `compose/` | `remote-creation-compose` | `ee.schimke.composeai.rccreation.compose` |
| `rc-creation-foundation` | `foundation/` | `foundation/foundation` | `ee.schimke.composeai.rccreation.foundation` |

They depend on each other as upstream's do: `foundation` → `compose` → `expression` → the writer.
Until the relocation below they were one module and the writer, published as
`third-party-remote-creation-compose` and `third-party-remote-creation-writer`; those coordinates
are no longer published.

This is a fork of AndroidX's Remote Compose **creation** stack, ported to Compose Multiplatform, so
`@RemoteComposable` content can be authored and captured on every rc-players target.

The fork is AndroidX's source in **rc-players' own package**: `ee.schimke.composeai.rccreation`
where AndroidX has `androidx.compose.remote.creation` (and `androidx.compose.remote.foundation`).
Apart from that rename it changes as little as it can, and every other difference is a marked
hunk. It declares none of AndroidX's classes or Gradle capabilities, so it can be in one app with
AndroidX's own artifacts; `:rc-creation-coexistence-check` verifies that. Why it left AndroidX's
package, and what that costs, is in
[`docs/design/RC_CREATION_BRIDGE.md`](../../docs/design/RC_CREATION_BRIDGE.md).

Anything rc-players adds to creation lives outside this fork, in
`ee.schimke.composeai.rccreation.bridge` (`:rc-creation-bridge`). The fork carries only the seams
that layer cannot reach from outside, and each one is a marked local patch.

The direction is convergence: AndroidX's creation is now multiplatform for the JVM and Android, and
the fork is being brought back into line with it step by step. What still has to differ and why,
the plan, and the feedback for AndroidX are in
[`docs/design/RC_CREATION_UPSTREAM.md`](../../docs/design/RC_CREATION_UPSTREAM.md).

## Upstream

- Repository: <https://github.com/androidx/androidx>
- AndroidX change [4307936](https://android-review.googlesource.com/c/platform/frameworks/support/+/4307936)
  patch set 17, commit `01398020c7ec20f51e51bfc2b65b9b425e338be9`, refreshed to androidx.dev
  snapshot `16547072` (androidx-main `40ff48144`), then to androidx-main `51b1c49d7`
  (2026-10-15, a superset of snapshot `16617687` = `522d8695c`), then to androidx-main
  `5dab33683` (2026-10-27), then to androidx-main `a0e84553095` (2026-10-29), the commit
  `remote-creation-compose:1.0.0-alpha23` was cut from, then to androidx-main `1448d6497`
  (2026-11-08), then to androidx-main `21a1a95e4` (2026-11-10).
- License: Apache-2.0
- Paths:

| This fork | AndroidX source (under `compose/remote/`) |
| --- | --- |
| `expression/upstream/remote-creation-expression/src/main/java` | `remote-creation-expression/src/main/java` |
| `expression/upstream/remote-creation-expression/src/test/java` | `remote-creation-expression/src/test/java` |
| `compose/upstream/remote-creation-compose/src/jvmAndAndroidMain/kotlin` | `remote-creation-compose/src/jvmAndAndroidMain/kotlin` |
| `compose/upstream/remote-creation-compose/src/androidMain/kotlin` | `remote-creation-compose/src/androidMain/kotlin` |
| `foundation/upstream/foundation/foundation/src/main/java` | `foundation/foundation/src/main/java` |
| `expression/src/`, `compose/src/` | nothing: files only the fork has |
| `../remote-creation-writer` | write-side sources from `remote-core`, `remote-creation-core` and `remote-creation`, re-expressed as a common `RemoteWriter` (which the carried Kotlin names `RemoteComposeWriter`, a typealias) |

Below a source root, a file is at upstream's path with the fork's package directories:
`ee/schimke/composeai/rccreation/compose/…` for `androidx/compose/remote/creation/compose/…`, and
`ee/schimke/composeai/rccreation/foundation/…` for `androidx/compose/remote/foundation/…`.

## Layout, and measuring the difference

A file with an AndroidX counterpart sits in the module that mirrors its upstream module, under
`upstream/`, at the counterpart's path: the upstream module, its source set, then the same file
name under the package path, with the fork's package in place of AndroidX's. So each module's
`upstream/` mirrors a module of `compose/remote/` in an androidx checkout, and the map between the
two is the package map and nothing else. An `upstream/` root is a source directory of its module's
`commonMain` (each module's `build.gradle.kts`); the directory says where a file comes from, not
which targets it compiles for. Upstream's `androidMain` files are among them, under their
`*.android.kt` names: what the fork carries from there needs only Compose UI, which Compose
Multiplatform has on every target.

`compose/src/commonMain`, `compose/src/androidMain` and `compose/src/skikoMain` hold what has no
upstream file: the local additions, the stand-in for a Java class (`RemotePath`), the layout nodes'
`WriterOp`, and the platform `actual`s. `expression/src/commonMain` holds the three fork-only files
the expression module's own sources need (`RemoteDecimalFormat`, `MeshExpressionLowering`,
`RemoteTextUnitDp.kt`), and `expression/src/jvmTest` what AndroidX's own tests, carried under
`expression/upstream/remote-creation-expression/src/test`, need in place of the Java writer.

`scripts/diff-upstream.sh <androidx checkout>` compares the two trees. It sorts imports, drops a
comma that ends a line (this repository's ktfmt adds one after a lone argument it breaks onto its
own line, AndroidX's does not), rewrites upstream's names for its Java classes to the fork's
(`scripts/upstream-fqn.tsv`) and upstream's packages to the fork's
(`scripts/upstream-packages.tsv`, for a file's text and for its path), and reports the upstream files not carried, the fork-only files, and
the hunks of every carried file that differs; `--summary` prints the counts.
`scripts/upstream-renames.tsv` lists carried files that are not at their upstream path, and is
empty. At androidx-main `21a1a95e4`:

| | Upstream files | Identical | Differing | Not carried | Under another name, split or merged | Fork-only | Differing hunks | Marked | Import-only | Unmarked |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Before the layout change | 210 | 58 | 115 | 37 | 23 | 19 | 590 | 15 | | 575 |
| After it (step 1) | 210 | 60 | 114 | 36 | 0 | 19 | 562 | 14 | | 548 |
| The same tree, as the script counts now | 210 | 66 | 108 | 36 | 0 | 19 | 520 | 14 | 74 | 432 |
| After step 2 | 210 | 95 | 79 | 36 | 0 | 17 | 226 | 120 | 54 | 52 |
| After step 4 | 214 | 97 | 86 | 31 | 0 | 17 | 215 | 147 | 58 | 10 |
| After the canvas port fixes | 214 | 97 | 86 | 31 | 0 | 17 | 208 | 141 | 57 | 10 |
| As that change merged (`6368c6a1`) | 214 | 97 | 86 | 31 | 0 | 17 | 214 | 147 | 57 | 10 |
| After the upstream bug patches | 214 | 96 | 87 | 31 | 0 | 17 | 232 | 163 | 59 | 10 |
| After the relocation | 214 | 96 | 87 | 31 | 0 | 17 | 232 | 163 | 59 | 10 |
| After the module split (step 3) | 214 | 96 | 87 | 31 | 0 | 18 | 233 | 166 | 57 | 10 |

The third row is step 1's tree measured with the two things the script learned in step 2 (the
trailing comma, and import-only hunks counted apart), so that the last two rows compare like with
like. By source root, identical / differing after step 2: `remote-creation-expression` 21 / 10
(6 / 25 before), `jvmAndAndroidMain` 64 / 61 (50 / 75), `androidMain` 6 / 8 (6 / 8),
`foundation` 4 / 0 (4 / 0). Step 4 adds a fifth root, the four test files of
`remote-creation-expression` (`src/test/java`), all carried with marked hunks; without it the last
row reads 210 upstream files, 97 identical, 82 differing, 31 not carried.

A hunk that has to stay different carries one of two markers, with its reason, in a comment:

- `// LOCAL PATCH (rc-players)`: something the fork adds, or does differently on purpose (the
  table under "Local patches").
- `// KMP (rc-players)`: a substitution forced by the non-JVM targets or by the Kotlin writer: a
  JVM-only construct replaced, a `RemoteModifierOperation` or `RemoteActionData` built where
  upstream builds `remote-creation-core`'s Java object, a layout recorded as a `WriterOp` where
  upstream writes to `document`, an Android-only function not carried.

The upstream bug patches add 16 marked hunks and 2 of imports, in five files, one of which
(`layout/RemoteTextMeasure.kt`) was identical to upstream's before.

A hunk that changes only `import` lines follows from its file's body and has no place for a
comment; the script counts it apart. The 10 unmarked hunks left are none of them
text the fork could take as it is, and the script still only counts them:

- 2 in `shapes/RemoteCornerBasedShape.kt`: upstream's single shared radius for a shape whose four
  corners are equal is not carried. Taking it changes the documents written (one expression and a
  `drawRoundRect` where the port writes four and a path), so it waits for a refresh.
- 8 that are this repository's ktfmt against AndroidX's, not text: a lambda or a parameter list
  one of the two breaks and the other does not (`state/RemoteFloatOperations.kt`,
  `shaders/RemoteShader.kt`, `vector/RemoteVectorPainter.kt` twice,
  `state/RemoteTextUnitCompose.android.kt`, and since step 4 `layout/RemoteCanvas.kt`,
  `vector/RemoteAnimatedVector.kt` and `vector/RemoteAnimatedVectorPainter.android.kt`).

`capture/RemoteCreationDisplayInfo.android.kt` is partial: it holds the `widthDp`/`heightDp`
extensions but not `rememberRemoteCreationDisplayInfo`, which reads Android resources.

## The CMP port it starts from

From here to "Relocation to `ee.schimke.composeai.rccreation`" this file is a log. Its sections
name modules, packages and paths as they were when each was written: one module,
`:third-party-remote-creation-compose`, in `androidx.compose.remote.creation.compose`, with
`upstream/` and `src/` directly under this directory.

The CMP port of those sources was made in yschimke/remote-m3-catalog `vendor/` and published there
as `ee.schimke.remotecompose:*:4307936-ps17-cmp09`. Its `vendor/README.md` lists what the port took
from upstream and what it left out. This fork is that port's **commonMain**, copied from
remote-m3-catalog `9cb05a6`:

- **remote-creation-compose and remote-foundation:** `src/commonMain` is copied into `src/commonMain`.
- **remote-write-core:** `src/commonMain` and `src/commonTest` are copied into `../remote-creation-writer`.
- **Actuals:**
  - `src/skikoMain` (JVM, Wasm, iOS, macOS) holds the port's three `wasmJsMain` actuals. They have no
    platform dependencies.
  - `src/androidMain` holds the port's Android actuals.

These are deliberately **not** carried:

- The port's legacy JVM writer path (`jvmMain`, `jvmAndAndroidMain`, and `vendor/remote-core`'s
  roughly 340 Java files).
- Its AndroidX `remote-creation` Android integration.

Every target, Android included, captures through the common `RemoteWriter`.

Formatting: `./gradlew :rc-creation-expression:ktfmtFormat :rc-creation-compose:ktfmtFormat
:rc-creation-foundation:ktfmtFormat` uses ktfmt's kotlinlang
style, which AndroidX uses. It re-wraps lines in about 80 files where the port had drifted.
`../remote-creation-writer` is the port's own code and keeps Google style. Both are `explicitApi()`:
AndroidX's sources already were, and the writer's implicit visibilities and return types (in
`PaintBundleData` and `RemoteDocumentWriter`) were made explicit when it was first published.

## Refresh 16547072 → androidx-main 51b1c49d7

Base and target were pinned by hashing every source in the snapshots' `-sources.jar`s against git:
`16547072` matches `40ff48144` (no compose/remote change until 2026-10-08 09:03 UTC) and `16617687`
matches `522d8695c`. The target is the later `51b1c49d7`, to include `7ce0408b0`. No `remote-core`
wire format changed in the range.

Taken:

- `915075137`: `animateEnterExit` defaults `animationId` to -1 (0 disabled the animation) and takes
  an `animationId`; `sharedElement`/`sharedBounds`/`animateEnterExit` reject 0.
- `04130d542`: `RemoteTextStyle.fromTextStyle` picks the variation settings of the font matching
  the style's weight and style (CSS matching), through the port's `Font.variationSettings` compat.
- `d3e5a08a0`, `9cda2fbc7` (common part): `HostAction` always writes a named action and loses its
  `id` field; new `HostIdAction`, restricted `hostAction(id, metadata)` and
  `hostAction(name, RemoteBoolean)`. These write through the existing `RemoteActionData.Host` /
  `HostMetadata` / `HostNamed`.
- `83b3b6737`, `7b5e7c3cc`: `RemoteDensity` and `RemoteDp` decoupled from Compose UI, with the Dp
  and TextUnit API in `RemoteDensityCompose.kt` / `RemoteDpCompose.kt`. `RemoteDp` is now a
  `BaseRemoteState<Float>`. (Its `cacheKey` stayed `internal` until realignment step 2.)
- `d5c248a6d`: `RemoteTimeVariables` and `RemoteConfiguration` are public.
- `ce05fb8a3`: `RemoteBoolean.select(RemoteBoolean, RemoteBoolean)` peepholes.
- `09ca86e4d`: `GraphicsLayerModifier` takes a `RemoteShape` and writes a rounded corner's radius
  (dynamic too) instead of a fixed 40. The `Shape` overloads and `GraphicsLayerScope` stay in
  `commonMain`, because CMP has the types. The unsupported-shape `Log.w` is dropped.
- `bb80833b5`, `3da7b37d6`: `RemoteCustomPropertiesScope.property(id, RemoteImageBitmap)`, and
  `RemoteCustomComponent(content)`, whose children are recorded into the custom component's child
  span.
- `f5b27d93b`: `drawScaledBitmap` without a description writes id 0, not an empty `TextData`. The
  writer's `contentDescription` is nullable.

Already in the port: `7ce0408b0` (`RemoteDocumentWriter.addPathData` already dedupes path data);
`a4e385e70` (the Android `Font.variationSettings` compat reads `ResourceFont` reflectively);
`7f692f209`; `6586caebb` and `792724d34` (`PaintTracker` is already common).

Not applicable:

- Android-only files the fork does not carry: `PendingIntentAction*`, `RCWidget`,
  `RemoteTextMeasure` (`109bc2783`), `LocalPlatform` (`ad23d88e4`), `69b8baeda`.
- Upstream's capture loop, which the port replaces: `7d994aebb`, `ae4347854`, `6c83948ca`,
  `188c0b4a8`, `b80603c16`, `861cb428e`.
- `97010f58b`: the `RcFloat` DSL is not carried. The port's caches already use `NanIdEquality`.
- Player, test and JSON-only changes: `ee7f84157`, `1cfe2843c`, `6c49ce3e2`, `0e6b76d61`,
  `adb54cfeb`.

Deliberately skipped:

- Android/common file splits that only serve upstream's module layout, which the port already
  splits its own way: `a12e3c520`, `7554d3b46`, `59e506463`, `8a77cf6b3`.
- The `RecordingCanvas` → `RemoteCanvas` inlining step `33de12812` (`skew`, `saveCount`), as before.
- `a07eab443`: it exposes cache keys to the library group for `remote-creation-expression`, which
  the fork did not carry then. Taken in realignment step 2.
- `c93e26675` (`currentProfileSupports*`): the port has no `Profile`. Its `supportedOperations`
  lists only opted-in operations, so the helpers would report false by default.
- `4e646d52e`: KDoc only, and it names upstream's `RemotePath`.

## Refresh 51b1c49d7 → androidx-main 5dab33683

The range is mostly Compose UI decoupling: upstream adds `Remote*` value types and moves the Compose
UI overloads into `*Android.kt` files. CMP has those types in common, so the split files that need
only Compose UI are carried in `commonMain` under upstream's names, which keeps the mapping 1:1. Its
`DrawHelpersAndroid.kt` conversions replace the port's `layout/DrawHelpers.kt`. The `remote-core`
constants the new types read are added to `../remote-creation-writer` (`CoreText`,
`PaintBundle.STYLE_*`).

Taken:

- Decoupling: `RemoteBlendMode` (`14f2588ba`, `8cb61b2dd`, `aeda599f7`), `RemoteTileMode`
  (`e06f18f2c`, `d4b9ca4bc`), `RemoteContentScale` (`bbeaf2e64`, `077424ee0`), `RemoteFontWeight`
  and `RemoteFontStyle` (`c37b396ba`, `9649a609f`), `RemoteTextAlign`/`RemoteTextOverflow`
  (`82dda3a2a`), `RemoteTextDecoration`/`RemoteLineBreak`/`RemoteHyphens` (`35e0a1097`),
  `RemoteLayoutDirection`, `LocalRemoteLayoutDirection` and `RemoteStateScope.remoteLayoutDirection`
  (`dec095528`, `d94310113`, `f44a94348`, `81608b392`), `RemoteRole` (`ae4402ae9`; written as the
  `AccessibleComponent.Role` ordinal), `RemoteStrokeCap`/`Join` (`09d495416`, `16c82eeba`),
  `RemotePaintingStyle` (`80aa0bed6`), `RemotePathFillType` (`a18703165`), `RemoteFilterQuality`
  (`923c036ab`), `RemoteCompositingStrategy` (`690d61b02`), `RemoteColorFilter.applyToPaintBundle`
  (`0fb296d3f`), and the bridge splits (`69b196837`, `b420d4759`, `db7c0e82e`, `26d1d2931`,
  `0bbb15f98`, `25b11cab9`, `0f63d733f`, `cb6e68901`, `f0c1b60e2`). The capture provides
  `LocalRemoteLayoutDirection` computed from `LocalLayoutDirection`, as upstream's Android capture does.
- Removals: `RemoteModifier.animationSpec` (`5ef70121c`), `painterRemoteColor(Color)`
  (`673052411`), `ComposeRemoteColorFilter` (`c30e9d159`, so the port's `setComposeColorFilter`
  expect/actuals go), `RemoteCustomPropertiesScope.property(id, Color)` (`c4af53541`), unused
  `ClipOp` parameters (`30ad6b28f`), `RemoteBrush.fromComposeUi` (`97e519eba`), the Compose
  `Offset`/`Size` conversions (`2deadc573`, `bec5e5508`), `RemoteTypeface`'s Android conversions
  (`183d083fb`), `RemoteGroupComponent` tint tracking (`037843b55`), `PathEffect` (`f05536a5b`),
  `ready`/`time` (`c7cdd34fc`), `AnimatedRemoteFloat`'s unused fields (`7915a136e`).
- `RemoteCanvas` (from the `RecordingCanvas` moves `e39ea21a5`, `bc2d34478`, `391c03af9`,
  `c5161ae3d`): public `flush()`, `scale`/`rotate` about (`px`, `py`), `drawText`/`drawTextRun`/
  `drawTextOnPath` with plain text, `drawBitmap` into a rectangle, `loop(Int, RemoteInt)`, and
  `drawPath(RemotePathFillType)`.
- `1d18c52eb`: a component writes one `DrawContent` marker however many `drawWithContent`s it
  chains. The port already nested chained modifiers (its local patch); that patch now resolves
  padding as upstream does, through `resolveLeft`/`Top`/`Right`/`Bottom` (layout direction, and dp
  under `RemoteDensityBehavior.Dp`).
- `8002ce4e0`: `RemoteDrawScope.clipOutline`, in `shapes/RemoteOutlineDraw.kt`.
- `fda5f7c27`: `RemoteBrush` fills and strokes in `RemoteImageVector`.
- `fb80250be`: `RemoteComponent.x`/`y`/`rootX`/`rootY`, through new `RemoteWriter.addComponent*Value`
  (`ComponentValue` types 2-5), keyed on the last component like width and height.
- `a97f4da26`: `RemoteImageBitmap.IntrinsicSize` and the `size` overloads.
- `e91e0524a`: the constructor-wrapping `remember*` factories are deprecated, and
  `MutableRemoteBoolean(Boolean)` / `RemoteScrollState(Float, Int)` are public, as upstream.
- Val-ification (`aff8be7df`, `9c24f3ac4`, `88da8dbad`, `8f2515dea`, `78a8cd4df`, `db6f41f07`,
  `efa25d41a`, `3deb92820`), `ListUtils` (`1f72da5c5`, `6bc85dcbc`), `368f0e3a7`, and the KDoc
  passes on carried files (`bd8c4fe69`, `71f64acb1`, `42f5cfd67`).

Already in the port: `61b971e7a` (the port's modifier folding already uses `mutableSetOf`); the
nesting half of `1d18c52eb` (see above).

Not applicable:

- Upstream's capture loop and headless package, which the port replaces with
  `RemoteDocumentSession`: `143ef54f9`, `99db52477`, `304dcdff4`, `e1253ffde`, `73133f2dd`,
  `142e7e07c`, `54d22ac14`, `18620d630`, `20643465d`, `da54ebbec`, `efe35bdd7`, `45d191140`,
  `12d05312a`, `f7b819280`, `c20632eae`, `d5ec98641`, `23bce7a38`. The session already renders in a
  mutable snapshot and tracks what a render reads; it still applies a render's own writes, as
  upstream's single-shot capture does without enforced clean recomposition.
- `RcPlatformServices` logging (`9bade0df2`, `9d116b015`, `c0d32f135`), `PendingIntentAction`
  (`c622a0ab5`), `Profile`/`RcPlatformProfiles` (`164305fee`), `remote-core` (`b7649f8f8`,
  `e37d48c74`: the writer validates no operation list), `RemoteCanvasAndroid`'s `android.graphics`
  overloads (`d65779c59`, `c16ab834e`, `6802c2286`, `f2f548a81`, `084fd1f2c`),
  `RemoteAnimatedVectorPainter` (`def66c97b`), and test-only migrations (`e52229d46`, `087694c20`,
  `c7346cf07`, `99095c2c2`, `930e88d43`, `1788a8ba6`).

Deliberately skipped:

- `RemoteCanvas` Loom patterns and `forEach` (`dafb6ee95`, `efcc8513d`) and
  `drawBitmapFontTextRun*` (`f9b2770ce`): `RemoteWriter` has no pattern-define or bitmap-font-run
  operations. `skew`, `saveCount`/`restoreToCount` (`33de12812`), `custom` and
  `startStateLayout`/`endStateLayout` on `RemoteCanvas` stay out, as before.
- `currentProfileSupportsPathClip` (`8002ce4e0`): as with `c93e26675`, the port has no `Profile`.
- `RemoteComposeCreationState` keeps `layoutDirection` as its stored property and derives
  `remoteLayoutDirection`; upstream stores the remote one. Same values, no constructor change.

## Refresh 5dab33683 → androidx-main a0e84553095 (1.0.0-alpha23)

The target is pinned by its sources: `remote-creation-compose:1.0.0-alpha23`'s `-sources.jar`
matches the `a0e84553095` tree file for file. Fourteen commits touch the carried sources; none
touches `remote-core`, `remote-creation-core` or `remote-creation`, so `../remote-creation-writer`
is unchanged. The fork's public API now matches alpha23's, which is what lets shared code compile
against this fork off Android and against AndroidX alpha23 on it.

Taken:

- `c75f1f4e0`: `Action` is renamed `RemoteAction`, and the restricted abstract `RemoteAction` class
  is folded into the one public interface, as upstream. The interface's restricted member is the
  fork's `RemoteStateScope.toRemoteActionData()`, writing the common `RemoteActionData`, in place of
  upstream's `toRemoteAction(): CreationAction`. Every action implements the interface.
- `23a939aff`: `RemoteColor` is backed by a packed ARGB `Int` (`BaseRemoteState<Int>`), with
  upstream's packing, `compositeOver` and `RemoteColor(red, green, blue, alpha)` factories; `Int.rc`
  replaces `Color.rc` in `RemoteColor.kt`. The Compose `Color` API moves to `RemoteColorCompose.kt`
  (`RemoteColor(Color)`, `Color.rc`, `asRemoteColor`, `asConstantColor(OrNull)`,
  `createNamedRemoteColor(…, Color)`, both `rememberNamedRemoteColor`s). A constant color is keyed
  by the new `RemoteColorConstantCacheKey`, which `transform` resolves.
- `c5c5b03fb`: `RemoteFontWeight` is a class backed by a `RemoteFloat`.
- `b657e6e62`, and the `*ModifierAndroid` half of `fa48ff16b`: `RemoteIntrinsicSize` and
  `width`/`height(RemoteIntrinsicSize)` replace the overloads taking Compose's `IntrinsicSize`,
  which the port carried in `commonMain`. Its value is the port's `RemoteDimensionType` ordinal,
  which is the wire's `DimensionModifierOperation.Type` ordinal.
- `312828a8d`: `RemoteLayoutScopeMarker` replaces foundation's `LayoutScopeMarker` on the layout
  scopes. `8b20d38fe`: `layoutDirection` moves from `RemoteComposeNode` to the new
  `RemoteLayoutNode`, which the layouts extend; the port's local patches on `RemoteComposeNode` stay
  where they were.
- Parameter order and names: `c2bdb7e96` (`RemoteFloat`'s operators take `other`, in the port's
  `state/FloatState.kt`; the `RemoteCreationDisplayInfo` ranges, in `capture/DisplayInfo.kt`;
  `RemoteCanvas(onDraw)`, `RemoteImage(bitmap)`, `remoteSpring(dampingRatio, stiffness, …)`,
  `offset` defaults, `@IntRange`/`@FloatRange` annotations), `fe9e0bfce` (`RemoteBasicImage`,
  `RemoteBasicText`) and `cdab571bf` (`RemoteShape.createOutline(size, layoutDirection, density)`).
- Visibility: `StandardRemotePaint` (`51e5e5379`) and the remaining modifier classes (`c83d297b1`:
  `ClipModifier`, `CollapsiblePriorityModifier`, `GraphicsLayerModifier`, `MarqueeModifier`,
  `RippleModifier`) are internal.
- `fa48ff16b` (rest): `RoundedPolygon` drawing goes (`drawRoundedPolygon`/`drawRoundedPolygonMorph`
  on `RemoteCanvas`, `RecordingCanvas` and `RemoteDrawScope`, with `RemoteDrawScopeAndroid.kt` and
  `MorphTweenUtility.kt`), and so does the `androidx.graphics:graphics-shapes` dependency.

Not applicable:

- Android-only files the fork does not carry: `AndroidRemoteColor` (so `createThemedRemoteColor`,
  including its Compose `Color` overload in `RemoteColorCompose.kt`, and `RemoteStateThemeColorKey`),
  `CompatAndroidRemotePaint`, `RemoteTextMeasure`, `CaptureRemoteDocumentAndroid`,
  `RemoteCreationDisplayInfoAndroid`, `LambdaAction`, `PendingIntentAction` and
  `RemoteCanvasAndroid`.

Deliberately skipped:

- The `RecordingCanvas` deletion in `fa48ff16b`: the port's `RecordingCanvas` is the seam its
  `CommonRecordingCanvas` implements and `RemoteCanvas` wraps, as with the inlining step `33de12812`
  before.
- `81fbc523b`, `4a99fb010` (internal one-handed gesture modifier and configuration): internal, with
  no public entry point to mirror, and built on JVM-only `WeakHashMap`/`synchronized`.
- Visibility widened for `remote-creation-expression`: upstream makes `RemoteColor`'s cache-key
  constructors, `RemoteStateInstanceKey` and `RemoteColorConstantCacheKey` public for the library
  group. Here they stayed internal, as the port's cache keys were (`a07eab443`), until
  realignment step 2 took them.

## Refresh a0e84553095 → androidx-main 1448d6497

The motivating fix is `ab1b76a2f` ("Scope RemoteComponent to container and state scope", with
`d7a5c31b5` and `f1f51073e`): a size a modifier or drawing reads refers to the component it belongs
to, through a `RemoteComponentToken`, not to `lastComponentId` (the component written before it,
for a box's own modifiers its preceding sibling or its parent's content). It retires the fork's
"sizes refer to the last component recorded" behaviour, which `ComponentSizeScopeTest` pinned; the
test now asserts each 40dp box's circle clip reads its own size.

Upstream moved its sources in this range: `src/main/java` became `src/jvmAndAndroidMain/kotlin`
and `src/androidMain/kotlin` (`1be9a9fed`), and the state and AST code moved to a new
`remote-creation-expression` module (`91c532558`, `0919df98a`), in the same packages. The fork maps
them by package-relative path. Seventeen of the range's commits reach `androidx-main` through
merges that `git log`'s default history simplification hides (`--full-history` lists them); they
are attributed below with the rest. Files upstream renamed in `jvmAndAndroidMain` take upstream's name
(`action/ValueChangeAction.kt`, `modifier/RemoteBlurEffect.kt`, `modifier/RemoteRenderEffect.kt`);
those moved to `androidMain` (`*Android.kt` → `*.android.kt`) keep the fork's `*Android.kt` /
`*Compose.kt` names in `commonMain`, as before. No `remote-core` wire format changed in the range.

Taken:

- Component scoping (`ab1b76a2f`, `d7a5c31b5`, `f1f51073e`): `RemoteComponentToken` (restricted)
  on every node, created with the node's content and provided as `LocalRemoteComponentToken`;
  public `RemoteComponent` (`width`, `height`, `centerX`, `centerY`; restricted `x`, `y`, `rootX`,
  `rootY`, constructors from a scope or a token; `equals`/`hashCode`, `@Stable`); `@Composable
  remoteComponent()` and `RemoteStateScope.remoteComponent()` in `layout/RemoteComponentCompose.kt`;
  `RemoteFloatContext` internal; `RemoteComponentCacheKey` keyed by token; and `Border`, `Clip`
  and `GraphicsLayer` modifiers read sizes through `remoteComponent()`. A modifier's scope resolves
  its node's token; a draw scope or canvas resolves the node currently rendering
  (`currentRenderingNode`), as upstream. The port writes `ComponentValue`s as declarations with
  the component's explicit id, so where upstream emits them inside the owning component's body, the
  port records the token's component id when the node is recorded (`beginComponentContent`, before
  its modifiers convert) and writes values for that id; a value requested before its component is
  recorded reserves a variable that is bound once the id is known, through the writer's new
  `addComponentValue(type, componentId, valueId)` (`RemoteComposeBuffer.addComponentValue`). The
  token state is per document, cleared with the caches and by `resetComponentIds()`. Without any
  token the context falls back to the last component recorded, as upstream falls back to
  `lastComponentId`.
- `f1f51073e`: the `RemoteFloatContext` lambda APIs go: `createNamedRemoteFloatExpression` becomes
  `createNamedRemoteFloat(name, defaultValue: RemoteFloat, domain)`, `MutableRemoteFloat` takes a
  `RemoteFloat` (and `MutableRemoteFloat(RemoteFloat)` companion `invoke`), `RemoteFloatExpression`
  takes a scope lambda (the document's `RemoteStateScope`, where upstream's is
  `RemoteDocumentScope`), and `RemoteFloatExpression.fromFloatArray` goes.
- `1b1ba0521`: the `rememberNamedRemote*` composables move to `state/RemoteStateComposables.kt`
  (`@JvmName("RemoteStateKt")`), `rememberNamedRemoteFloat` takes a `RemoteFloat`, the deprecated
  `rememberMutableRemote*`, `rememberRemoteFloatArray` and `rememberRemoteFloatExpression` go, and
  `RemoteEnum.enumEntries` is public. `toRemoteString()` uses upstream's default format (40 integer
  digits, 3 or 0 fraction digits, grouped); the port's common `DecimalFormat` overload stays, without
  a default, where upstream's Java/ICU overloads moved to `RemoteFormatting.android.kt`.
- Compose UI decoupling and promotions (with the `Remote` type prefixes of `7177ff18c`):
  `RemoteCreationDisplayInfo` holds `width`, `height`,
  `density`, `fontScale` (`1d600a819`; `RemoteDensity.from` moves beside it); `RemoteTextUnit` is
  backed by `RemoteTextUnitType` with public constructors, its Compose API in
  `state/RemoteTextUnitCompose.kt` (`d63c53ec4`); `RemoteImageBitmap` is an open
  `BaseRemoteState<Any>` with a public URL constructor, its `ImageBitmap` API in
  `state/RemoteImageBitmapCompose.kt` (`a1edaa2a0`);
  `RemoteFontVariation` (`7056ec5ed`, `9a252fb8a`; `text/RemoteFontVariation.kt`, Compose
  conversion in `text/RemoteFontVariationAndroid.kt`) on `RemoteText`, `RemoteTextStyle`,
  `RemotePaint` and `PaintTracker`, whose axis values are now `RemoteFloat`s; `RemoteTextStyle`'s
  Compose API (`fromTextStyle`, the `FontVariation.Settings` factory) in
  `text/RemoteTextStyleAndroid.kt` (`99f14adf8`), reading font variations through the port's
  `Font.variationSettings` compat; `RemoteFontFamily.fromComposeFontFamily` becomes the internal
  `FontFamily.toRemoteFontFamily()` (`cec30cfa8`); `RemoteRole` with `ValuePicker` and `Carousel` mapped (`a38a0f359`)
  and `RemoteSemantics*` types, `clickable(onClick, enabled, role: RemoteRole? = null)` (no role by
  default) and the Compose `Role` overload removed (`af61a3e9c`); `RemoteStrokeCap`/`Join`,
  `RemotePaintingStyle` and `RemoteBlendMode` on `RemotePaint` (`d3cd5dc90`, `db6a60d2e`,
  `d98caffa0`, after `7ef214a7c`, `88669848f`); `RemoteBlendMode` and ARGB colours in
  `RemoteImageVector` (`5898066b4`, `4484850ee`); `RemoteBitmapFont` (`1ec61e7e1`); `RemoteTileMode` and the `RemoteColor` linear
  gradients (`67250b113`, so `shaders/RemoteLinearGradientAndroid.kt` goes); `RemoteRenderEffect`
  and `RemoteBlurEffect`, `RemoteGraphicsLayerScope` (`379e83181` and the renames);
  `RemoteStateScope.layoutDirection` and `RemoteShape.createOutline` take `RemoteLayoutDirection`
  and `remoteLayoutDirection` goes (`a8c279329`; the fork's `RemoteComposeCreationState` and
  `RemoteComposeCreationContext` with them, while `captureCommonRemoteDocument` and
  `RemoteDocumentSession` still take Compose's `LayoutDirection` for `LocalLayoutDirection`).
- API clean-ups: `RemoteAction.None` (`c62eca783`); `valueChange` → `valueChangeAction`,
  `RemoteColor` `tween` → `lerp`, `RemoteEasing.cubic`/`spline` (`9e7f13cac`); `RemoteDp(RemoteFloat)`
  / `RemoteDp(Float)` constructors and `RemoteInt`'s `Int` operators public with `other`
  parameters (`91c532558`); `@RemoteComposable` slots and `@Immutable`/`@Stable` (`6503344d9`); the
  no-op `@file:RestrictTo`s removed (`6b3a74cbd`); the primitive
  `weight(Float)` and `RemoteOffset` overloads removed (`e6f8df93a`), the
  `allowSendingEmptyFontAxis` flag removed (`afff0e6c2`; with no `Profile`, the port sends the
  settings as upstream does for a `CORE_TEXT` profile), `RemoteBrush.intrinsicSize` removed
  (`e0463c044`), helpers made internal (`9b16ab7fa`: `RemoteCanvas.remote`,
  `RemoteVectorPainter.draw`, `RemoteImageVector.hasClipPath`, `AnimationType`, `isTimeVar`,
  `RemoteFloatReference`), `remotePath` restricted, alpha/rotate/scale on `RemoteRectangleShape`
  (`07a74b336`), `round` rounding half up (`floor(a + 0.5f)`, `0919df98a`), null-propagating
  `computeRequiredCodePointSet` for string arrays and enums, the `RemoteStateLayout` content
  lambdas `@RemoteComposable`, value classes' `value` internal (`d2196a0b5`), the foundation API
  review (`ddb240a6f`, `ce56a93b7`, `28e5acdba`, `effec30dd`, `3a9e0563a`), and the KDoc passes
  (`bef581aa9`, `55635633d`, `b91a6ef8c`, `978bc747f`, `960c9542e`).
- `6c2271e14`: `RemoteCanvas.clipPath` is recorded as a clip (`DocumentOp.Clip`), not a draw.
- `remoteComponent()`-reading callers outside the fork move with it: `:rc-widget-creation`'s size
  breakpoints read the component inside the box they gate (an `expect`/`actual` keeps the
  `RemoteFloatContext` form for AndroidX alpha23 on Android), and `:rc-player-edit`'s
  `toRemoteComposeKotlin` writes `RemoteRole`, `RemoteTextDecoration`, `RemoteFontVariation` and
  `weight(RemoteFloat)`.

Already in the port: `37cc3c75b` (paths are snapshotted at record time, and the writer dedupes path
data, `7ce0408b0`); `6f22a9fd0` (the fork's Android-named files already carry `@JvmName`s); the
`withCanvasOperationsScope` cache frames of `09c269541` (unused while the writer hoisted every
expression into the document's declarations, so that none was paint-only; the nodes enter them
since "Declarations in place, and a capture profile", below).

Not applicable:

- Upstream's capture loop, headless package and their Android helpers, which the port replaces with
  `RemoteDocumentSession`: `dc209d471` (the `RemoteComposeCaptureState` split), `3b34e6bfc`,
  `8525a7cbd`, `bf5a39a44` and `b75d2cb6a` (`CaptureTracer`), `6140a625e`, `f1f954105`, `b51def2d3`.
- Files the fork does not carry: `RemoteTextMeasure`/`RemoteTextLayoutResult` (`6ee4d9bed`,
  `981d83c75`, `d6687b729`), `PendingIntentActionHandler` (`3be30799c`), the one-handed gesture
  classes (`8e6915cc9`, `d1143f5ea`; skipped before), `Hoist`, test resources (`aa07fa7d5`), the
  remote-testing players (`99f2345f4`) and tests.
- `remote-core` player changes (`412c1b7bb`, the 3D and embedded-document operation constants of
  `3cc6af924`) and
  `RemoteComposeWriter.addPathData(…, winding)`, which no longer dedupes because upstream's
  `CanvasOperationBuffer` now does; the port's writer still dedupes.
- `67860cefe`/`78428d941` and `9ec73dc4f`/`ae6ff2faf`: each reverted in the range.

Deliberately skipped:

- The file reorganisation that serves upstream's module split (`91c532558`, `0919df98a`,
  `1b1ba0521`'s `Runtime*` and formatting extractions, `FastListUtils`): the fork keeps one module
  and its file names. Their API changes are taken above.
- Visibility widened only for `remote-creation-expression` (restricted-public `arrayProvider`,
  `arrayForCreationState`, `createForId`/`createMutableForId`, state constructors taking cache
  keys, `combineToLongArray`, `RemoteEasing`'s constructor, `RemoteMatrix3x3.isIdentity`,
  `nanIdContentEquals`, `RemoteState<*>.cacheKey`): they stayed internal, as the port's cache
  keys were (`a07eab443`). Taken in realignment step 2.
- `RemoteDocumentScope` and the `RemoteStateScope.resolveComponentToken()` member (`f1f51073e`
  made the resolution a public member so the expression module can reach it): the fork resolved
  tokens with `ab1b76a2f`'s internal walk of the scope chain. Taken in realignment step 2.
- The requested-property mask on `RemoteComponentToken` and the eager `ComponentValue` writes at
  the start of a component: they exist to place values inside the component body, which the port's
  explicit ids make unnecessary.
- `toRecordingModifierElement()`/`RecordingModifier` and `document.*` writes: a mechanism change;
  the port keeps `toRemoteModifierOperation()` and the common `RemoteWriter`.

## Refresh 1448d6497 → androidx-main 21a1a95e4

The range finishes the Compose UI decoupling: upstream deletes the public `androidMain` wrappers
that took Compose UI types (`ContentScale`, `FontWeight`, `TextOverflow`, `TileMode`, `Shape`,
`CompositingStrategy`, `Brush`, `Color`, `Dp`, `IntSize`) and makes the `Remote*` overloads beside
them public, with the defaults the wrappers had. The fork carried those wrappers in `commonMain`
(`*Android.kt`), and they go with upstream's, as the Compose `Role` and `IntrinsicSize` overloads
did before: the same source compiles against either library only if neither has them. What stays in
the fork's `*Android.kt` / `*Compose.kt` files is what upstream's `*.android.kt` files still hold.
Seven commits touch the carried sources; none changes a wire format, and
`../remote-creation-writer` is unchanged.

Taken:

- `af1bac731`: `RemoteImage`, `RemoteText`, `graphicsLayer`, `RemoteBrush.image`,
  `RemoteBrush.radialGradient` and `RemoteImageVector.Builder.path` take `RemoteContentScale`,
  `RemoteFontStyle`/`RemoteFontWeight`/`RemoteTextAlign`/`RemoteTextOverflow`/
  `RemoteFontVariation.Settings`, `RemoteShape`/`RemoteCompositingStrategy`, `RemoteTileMode` and
  `RemoteBrush`/`RemoteStrokeCap`/`RemoteStrokeJoin`/`RemotePathFillType` only, unrestricted and
  with defaults (`Fit`, `Clip`, `RemoteRectangleShape`, `Clamp`, `None`, `DefaultFillType`);
  `RemoteGraphicsLayerScope.shape` and `compositingStrategy` are `RemoteShape` and
  `RemoteCompositingStrategy`, and the scope overload delegates to the argument one. Removed with
  their files (`layout/RemoteImageAndroid.kt`, `layout/RemoteTextAndroid.kt`,
  `layout/RemoteContentScaleAndroid.kt`, `shaders/RemoteImageBrushAndroid.kt`,
  `shaders/RemoteRadialGradientAndroid.kt`, `vector/RemoteVectorPainterAndroid.kt`,
  `capture/RemoteDensityCompose.kt`): the Compose-typed overloads of the above,
  `painterRemoteVector(ImageVector)` (convert with `ImageVector.toRemoteImageVector()`), the
  `Dp`/`TextUnit` `toPx` extensions on and for `RemoteDensity`, and the internal
  `toRemoteContentScale`/`toRemoteTileMode`/`toRemoteTextOverflow`/`toRemotePaintingStyle`
  conversions. Named state defaults are `Remote*` values:
  `RemoteColor.createNamedRemoteColor(name, defaultValue: RemoteColor, domain)` is new (a constant
  default is a named colour; any other is named on an id-to-id colour expression of it, written
  through `RemoteWriter.addColorExpression` and `setNamedVariable` where upstream calls
  `setColorName`), `rememberNamedRemoteColor` and `rememberNamedRemoteDp` take a `RemoteColor` and a
  `RemoteDp` and move to `state/RemoteStateComposables.kt` with `rememberRemoteImageBitmap(url)` and
  `rememberNamedRemoteImageBitmap(name, url, domain)`, and the `Color` and `Dp`
  `createNamedRemoteColor`/`createNamedRemoteDp` overloads, `rememberNamedRemoteColor(name, Int)`,
  `rememberNamedRemoteDp(name, domain, value)` and the top-level
  `createNamedRemoteImageBitmap(name, url, domain)` go.
- `ccade014a`: `RemoteContentScale`, `RemoteTextOverflow`, `RemoteCompositingStrategy` and
  `RemotePathFillType` are unrestricted.
- `00600f9da` (carried part): the restricted wrappers go: `path` with a `RemoteBrush` and Compose
  stroke types, `RemoteBlendModeColorFilter(color, BlendMode)` (`state/RemoteColorFilterAndroid.kt`),
  the `RemoteTextStyle` factory taking `FontVariation.Settings`, and the `IntSize` API of
  `a97f4da26` (`RemoteImageBitmap.IntrinsicSize`, `createNamedRemoteImageBitmap(…, size, …)`,
  `rememberNamedRemoteImageBitmap(…, size, …)`; the `width`/`height` companion overload stays).
- `06067dbfa`: `RemoteBasicImage` takes a `RemoteContentScale` and `RemoteBasicText` the
  `Remote*` text types; `RemoteBasicIcon`'s default tint is written as ARGB.
- `6979e8551`: `RemoteFontVariation.weight`/`width`/`slant`, used by `PaintTracker`.
- `02bc68077`: `RemoteComponent.positionInParent` and `positionInRoot` (`RemoteOffset`s) are public
  and replace the restricted `x`, `y`, `rootX` and `rootY`.
- `de4a9909e`: `RemoteState.Domain(namespace)` is a public constructor that rejects an empty
  namespace or one containing `:`, `Domain.None` and `prefixed` are public, and `Domain` is
  `@Stable`.
- Callers outside the fork move with it: `:rc-creation-bridge`, `:rc-player-embedded` and
  `:rc-widget-creation` name colours from ARGB (`createNamedRemoteColor(name, Int)`, which AndroidX
  alpha23 has too), and `:rc-player-edit`'s `toRemoteComposeKotlin` writes
  `rememberNamedRemoteColor(name, RemoteColor(Color(…)))`.
  `CreationRefresh21a1a95e4Test` (`:rc-creation-bridge`) covers the new API.

Already in the port: the `RemoteOffset` move into `remote-creation-expression` (`02bc68077`), as
the fork keeps one module; `Domain.equals`/`hashCode` by namespace.

Not applicable:

- Files the fork does not carry: `RemoteAnimatedVector` (moved to `jvmAndAndroidMain` by
  `00600f9da`; its painter and parser are Android-only), the `RemoteComposeCaptureState`
  factories of upstream's capture loop (`00600f9da`), `createThemedRemoteColor`.
- The `RcFloat`/`RcScope` DSL in `remote-creation-core` (`a4c2a8762`, `61d9715bd`), which is not
  carried (`97010f58b`, before).
- `remote-core`: profile operation tables (`48e08566f`, `353f021ee`; the writer validates no
  operation list) and comment-only changes (`e3fb5ee40`).
- Players, tests, build files and documentation: `28fb38004`, `300a543b2` (the embedded player,
  `third_party/rc-embedded-player`'s concern), `e95d0e5ef`, `7f6caf201`, `0b2376ee0`, `d3d73ec7e`,
  and three unrelated commits that `--full-history` lists for `compose/remote/Documentation`
  (`1b5737e50`, `b706516a0`, `fa6cb5492`).

Deliberately skipped:

- Nothing new. Visibility widened only for `remote-creation-expression` stayed internal, as
  before, until realignment step 2.

## alpha22 write side: component offscreen buffers and visibility functions

Not a refresh: `b34eed8a88b` ("Add custom visibility animation support", in the alpha22 cut
`0b7d5401bee`) authors these only below `remote-creation-compose`, in `remote-core`'s
`RemoteComposeBuffer` and `remote-creation-core`'s `RemoteComposeWriter` and `RcScope` DSL. At
`5dab33683`, `remote-creation-compose` has no API to define either, only the library-group
`RemoteEnterTransition`/`RemoteExitTransition.Custom(functionId)` and `animationSpec` function ids
already carried.

Taken into `../remote-creation-writer`, under `RemoteComposeWriter`'s names:

- `RemoteWriter.createOffscreenBitmap(componentId = 0)`: `BitmapData` `TYPE_RAW8888`, 1x1,
  `ENCODING_COMPONENT_OFFSCREEN_BUFFER`, with the component id as a 4-byte big-endian payload when
  non-zero (`RemoteComposeBuffer.createOffscreenBitmap`).
- `drawComponentToBitmap(componentId, bitmapId)` and `drawComponentToBitmap(bitmapId)`: default
  methods composing `drawOnBitmap` (`MODE_COMPONENT_ID` for the first), `drawComponentContent` and
  `drawOnBitmap(0, 0, 0)`, as upstream composes them.
- `createFloatFunction(args)`, `endFloatFunction()` and `callFloatFunction(id, args)`
  (`FunctionDefine` 168, `FunctionCall` 166), allocating the function id then one id per
  parameter, as upstream.

The fork's Compose layer takes nothing, as AndroidX has nothing there to mirror.
`RcScope.defineVisibilityAnimation`'s equivalent, and helpers naming a function as a transition,
are rc-players' own, in `:rc-creation-bridge` (`RcVisibilityAnimations.kt`).

A function is written where it is defined, as upstream's (the writer hoisted declarations ahead of
the layout until "Declarations in place, and a capture profile", below, and a function with them):
its body, declarations included, stays inline and in order, because a player evaluates it only when
it runs the function, and the dedup caches forget what the body declared once it ends.
`defineVisibilityAnimation` asks for a global section, so its functions are still ahead of the root. The first paint inside a function, and the first after it, are written
in full. Byte equality with alpha22's `RemoteComposeWriter` and DSL, and alpha22's `CoreDocument`
reading the result, are tested in `:rc-creation-bridge`'s jvmTest.

## Realignment step 1: upstream's file names and layout

Not a refresh: no upstream change is taken and no behaviour changes. Twenty documents captured
before and after are byte-identical. It undoes the "file splits that only serve upstream's module
layout" skips recorded in the refreshes above, which predate upstream becoming multiplatform.

- Every carried file moves to its upstream path under `upstream/` (above). Renamed on the way:
  `state/FloatState.kt` → `RemoteFloat.kt`, `state/IntState.kt` → `RemoteInt.kt`,
  `capture/CommonCreationState.kt` → `RemoteComposeCreationState.kt`, `capture/DisplayInfo.kt` →
  `RemoteCreationDisplayInfo.kt`, `capture/CommonRemoteImageVector.kt` → `RemoteImageVector.kt`,
  `capture/CaptureRemoteDocument.common.kt` → `CaptureRemoteDocument.kt`,
  `capture/RemoteDocumentProgram.kt` → `CanvasOperationBuffer.kt`, `layout/DrawHelpers.kt` →
  `DrawHelpers.android.kt`, and the `*Android.kt` / `*Compose.kt` / `widgets/*.kt` bridge files →
  upstream's `*.android.kt` names (`RemoteImageVector`, `RemoteLayoutDirection`,
  `RemoteColorCompose`, `RemoteDpCompose`, `RemoteImageBitmapCompose`, `RemoteTextUnitCompose`,
  `RemoteFontVariation`, `RemoteTextStyle`, `RemoteVector`, `WidgetInformation`,
  `WidgetLambdaAction`, `WidgetsModifiers`).
- Internal classes take upstream's names in `capture/CanvasOperationBuffer.kt`:
  `RemoteDocumentProgram` → `CanvasOperationBuffer`, `DocumentOp` → `CanvasOp`, `DocumentTransform`
  → `PendingOp`.
- Declarations move to the file upstream has them in: `shapes/RemoteOutlineDraw.kt` is merged into
  `shapes/RemoteOutline.kt`; `USER_DOMAIN`, `SYSTEM_DOMAIN` (now `internal`, as upstream) and
  `RemoteState<*>.cacheKey` go from `state/RemoteState.kt` to `state/RemoteStateCacheKey.kt`;
  `NoRemoteCompose` and `LocalRemoteComposeCreationState` go to the new
  `capture/RemoteComposeCaptureState.kt`; `widthDp`/`heightDp` go to the new
  `capture/RemoteCreationDisplayInfo.android.kt`; and the port's own `DecimalFormat` helpers
  (`TextFromFloatOptions`, `toJavaTextFromFloatOptions`, `formatRemoteFloatJvm`,
  `formatRemoteIntJvm`) leave `RemoteFloat.kt` and `RemoteInt.kt` for the fork-only
  `state/RemoteDecimalFormat.kt`.
- `state/FastListUtils.kt` is carried, verbatim: `remote-creation-expression`'s own copy of four of
  `util/ListUtils.kt`'s functions, in the `state` package. The expression files use it, as
  upstream's do, and drop their imports from `util`.
- `@file:JvmName` matches upstream's wherever upstream has one, and is dropped where the file name
  now gives the same class: added on `capture/CaptureRemoteDocument.kt`,
  `modifier/GraphicsLayerModifier.kt`, `HeightModifier.kt`, `WidthModifier.kt`,
  `state/RemoteFloatOperations.kt`, `RemoteImageBitmapCompose.android.kt` and
  `RemoteTextUnitCompose.android.kt`; removed from `RemoteComposeCreationState.kt`,
  `RemoteFloat.kt`, `RemoteInt.kt` and `RemoteState.kt`, and `RemoteStateComposables.kt` is no
  longer a multifile class part, as `RemoteState.kt` has no top-level declaration left.

The JVM and Android ABI changes in five facade class names, each to the name AndroidX's artifact
has: `CaptureRemoteDocument_commonKt` → `CaptureRemoteDocumentKt`, `RemoteOutlineDrawKt` →
`RemoteOutlineKt`, `RemoteFloatOperationsKt` → `RemoteFloatOperations`, `WidgetsModifiersKt` →
`WidgetsModifiers_androidKt`, and `getLocalRemoteComposeCreationState()` moves from
`RemoteComposeCreationStateKt` to `RemoteComposeCaptureStateKt`. Kotlin source is unaffected. The
klib dump changes only in the Compose compiler's synthetic `$stableprop` entries for the three
renamed internal classes.

## Realignment step 2: upstream's visibility, scope types and writer names

Not a refresh: no upstream change is taken and no document changes. 112 distinct documents (every
one the JVM tests of `:rc-creation-bridge`, `:rc-player-embedded`, `:rc-player-edit` and
`:third-party-remote-creation-writer` encode, with the round-trip samples and two documents
exercising clips, borders, `drawWithContent`, a canvas, text, actions, named values, gradients,
scrolling, a state layout and number formatting captured under both density behaviours) are
byte-identical before and after. It undoes the "stays internal" and `RemoteDocumentScope` skips
recorded in the refreshes above.

- **The expression files are upstream's text.** Each of the 31 files of
  `remote-creation-expression` was re-taken from `21a1a95e4` and the differences re-applied as
  marked hunks: 21 are identical and the other 10 differ only in marked hunks, their imports, and
  one formatter hunk. That takes upstream's visibility with it: the restricted-public cache keys
  and their constructors, `RemoteStateInstanceKey`, `RemoteColorConstantCacheKey`, `arrayProvider`,
  `arrayForCreationState`, `hasBeenWrittenToDoc`, `createForId`/`createMutableForId`,
  `combineToLongArray`, `RemoteEasing`'s constructor, `RemoteMatrix3x3.isIdentity`,
  `nanIdContentEquals`, `RemoteState<*>.cacheKey`, `toRemoteStringWithPadding`/`Options`, the
  `@JvmStatic`/`@JvmOverloads` annotations, `@RememberInComposition` on the `Mutable*` `invoke`s,
  `RemoteStateCacheKey.accept` as an extension, and `RemoteFloatReference` as a value class.
- **Scope types.** `RemoteDocumentScope` (`document`, `resolveComponentToken()`) and
  `RemoteComponentTokenProvider` are carried; `RemoteStateScope` extends `RemoteDocumentScope` and
  resolves tokens through the member; `RemoteComposeCreationState` is upstream's base class
  (caches, token state, the canvas-scope frames, which nothing here enters) and
  `RemoteComposeCaptureState` is split from it in `remote-creation-compose` (display info, layout
  direction, density behaviour, `time`), with the fork's `supportedOperations` and image encoder
  where upstream has a `Profile`. `LocalRemoteComposeCreationState` holds a
  `RemoteComposeCaptureState`. The port's `RemoteComposeCreationContext` interface is gone, and
  with it `enqueueGlobalDeclaration`, `enterComponentScope` and `platformState`, which nothing
  read. A layout node's scope is built over the canvas (`overriddenScope(remoteCanvas)`), as
  upstream's, so a spaced arrangement resolves its spacing with the node's own density like every
  other modifier value: the one thing here that could change a document, and only for content
  under a `LocalRemoteDensity` other than the capture's.
- **The mesh lowering** (local patch) was a `RemoteComposeCreationContext` delegate. It is now
  `MeshExpressionLowering`, set on the creation state while a channel is lowered.
- **Writer names** (`../remote-creation-writer`). `RemoteWriter`'s methods take
  `RemoteComposeWriter`'s or `RemoteComposeBuffer`'s names where they are the same operation:
  `startText`/`endText` → `startTextComponent`/`endTextComponent`, `startConditional`/
  `endConditional` → `conditionalOperations`/`endConditionalOperations`, `addIdList` → `addList`,
  `writeIdLookup` → `idLookup`, `colorAttribute` → `getColorAttribute`, `consumePaintReset` →
  `checkAndClearForceSendingNewPaint`, `clipPath` → `addClipPath`, `startPatternDefinition`/
  `endPatternDefinition`/`startPatternInflation`/`startPatternForEach` → `definePattern`/
  `endPatternDefine`/`inflatePattern`/`addPatternForEach`, `touchExpression` →
  `addTouchExpression`; and `setColorName`/`setStringName`/`setFloatName`, `addStringList` and
  `textCreateId` are added over `setNamedVariable`, `addList` and `addText`, as they are upstream.
  `RemoteComposeWriter` is a typealias of `RemoteWriter`, so the carried text names the type as
  upstream does. More stand-ins take AndroidX's class names (and are in `upstream-fqn.tsv`):
  `PaintBundleData` → `PaintBundle` (its constants in the companion; `setFontAxes` →
  `setTextAxis`), `RemoteCustomPropertyData` → `Custom.CustomProperty`, `NamedVariableType` →
  `NamedVariable` (`*_TYPE`), `RemoteDimensionType` → `DimensionModifierOperation.Type`,
  `RemoteAnimation`/`RemoteAnimationSequence` → `AnimationSpec.ANIMATION`/`SEQUENCE`, the port's
  `packAnimation` → `RemoteComposeBuffer.packAnimation` and `FloatAnimation.packToFloatArray`,
  and new `ComponentValue`, `HostNamedActionOperation`, `AccessibleComponent.Role`,
  `MultiClickModifier` and `CollapsiblePriority`. No package changes, `RemoteWriter` is still an
  interface, and what it writes is unchanged.
- **Signatures that differ in more than the name**, left as they are: `startBox`/`startRow`/… and
  `startCanvas`/`startStateLayout`/`startCustom` take a `RemoteModifierData` (upstream a
  `RecordingModifier`); `startTextComponent(RemoteTextData)` and `image(RemoteImageData)` take one
  value (upstream a parameter list); `addComponent*Value(componentId)` and
  `addComponentValue(type, componentId, valueId)` name the component (upstream's bind to the
  buffer's last one); `applyPaint(PaintBundle)` is `buffer.addPaint` upstream;
  `conditionalOperations` takes an `Int` type (upstream a `Byte`); `addBitmapFont` takes a list of
  `BitmapFontGlyph` (upstream an array of `BitmapFontData.Glyph`); `drawMesh2D(meshId, imageId,
  blend)` has upstream's last two the other way round; `addThemedColor`, `drawBitmap` and the
  defaulted `addMesh2D*` differ in return or optional parameters; `writeModifier`, `addShader`
  and `componentIdForCache` have no upstream counterpart.
- **Other drift taken**: `RemoteTimeDefaults` reads `LocalIs24HourFormat`, which the fork's two
  captures provide from the platform (`text/PlatformTimeFormat.kt`); `@JvmField` on
  `isEnforceCleanRecompositionEnabled`; KDoc and declaration order in `RemoteImageVector.kt`,
  `RemoteStateLayout.kt`, `RemoteCustomComponent.kt`, `DrawHelpers.android.kt` and others.

The ABI: the dumps of both modules change (`api/`). Kotlin source that used the names above has
to follow; everything else compiles as before.

What step 3 still has to solve, found here: `layout/RemoteFloatContext.kt` reaches the creation
state through `RemoteStateScope` (a `remote-creation-compose` type) for a size bound to no
component token, and `state/RemoteStateVisitor.kt` rebuilds an image constant from a Compose
`ImageBitmap`. Both are marked.

## Realignment step 4: what is no longer Android-only upstream

Not a refresh: no upstream change to a carried file is taken, and no document that could be
captured before changes. Every document the JVM tests of `:rc-creation-bridge`,
`:rc-player-embedded`, `:rc-player-edit` and `:third-party-remote-creation-writer` encoded before
the step (107 distinct ones, with 18 captured for the purpose: the six round-trip samples and three
documents of canvas shapes, paths, text, transforms, clips, conditional blocks, loops, offscreen
bitmaps, `usePaint`, clips, borders, gradients, `drawWithContent` after padding, scrolling, icons,
a state layout and a custom component, each under both density behaviours) is encoded to the same
bytes after it.

- **`RecordingCanvas` is folded into `RemoteCanvas`**, as upstream did in `fa48ff16b`: the
  `RecordingCanvas` interface and the port's `CommonRecordingCanvas` are gone, a canvas is built
  from the capture state (`RemoteCanvas(creationState, enableOptimizations = false)`), and
  `layout/RemoteCanvas.kt` and `capture/CanvasOperationBuffer.kt` are upstream's text with marked
  hunks. That undoes the `RecordingCanvas` skips recorded in the refreshes above. The drawing
  operations are recorded as upstream records them (closures over `document`, `CanvasOp.Transform`
  and `CanvasOp.Clip`, a `SaveRestore` node for `save`/`restore`, a path written once per span);
  `WriterOp` (fork-only) keeps only the layout nodes' operations, which are still recorded into the
  canvas's buffer and written when it flushes (`RemoteCanvas.recordRenderingOp(WriterOp)`, marked).
  The port's `CanvasOp.Loop`, `CanvasOp.DrawToBitmap`, `CanvasOp.Declaration` and the declaration
  span, which nothing used, are gone.
- **The `RemoteCanvas` API the fork lacked**, all upstream's text: `skew`, `saveCount`,
  `save(): Int`, `restoreToCount`, `custom`, `drawBitmapFontTextRun`,
  `drawBitmapFontTextRunOnPath`, `startStateLayout`/`endStateLayout`, the Loom patterns
  (`definePattern` with none to six parameters, `RemotePattern0`…`RemotePattern6`) and `forEach`
  over a list, a `RemoteFloatArray`, `RemoteStringArray` or `RemoteIntArray`, and two to six
  lists. Nothing was left out as unsupported.
- **Carried from `jvmAndAndroidMain`**: `layout/RemoteTextMeasure.kt` (`RemoteTextMeasurer`,
  `RemoteTextLayoutResult`, `rememberRemoteTextMeasurer`; identical), `vector/RemoteAnimatedVector.kt`
  (identical but for a formatter hunk), `capture/RemoteProfileCapabilities.kt`
  (`currentProfileSupports*`), `capture/CaptureTracer.kt` (identical; nothing uses it before step
  5), and `Hoist` in `state/RemoteStateComposables.kt`. From `androidMain`:
  `vector/RemoteAnimatedVectorPainter.android.kt` (`painterRemoteAnimatedVector`,
  `rememberRemoteAnimatedVectorPainter`, `defaultProgress`), which needs only Compose UI's
  `PathParser` and the vector model above.
- **`currentProfileSupports*` without a `Profile`.** The fork has none. What stands in for a
  profile's operation table is the set a host gives the capture (`supportedOperations` of
  `captureCommonRemoteDocument`, `captureCommonRemoteDocumentInto` and `RemoteDocumentSession`): the
  operations it says the document's reader takes. It is empty unless the host names some, so each
  function answers `false` by default and a component takes its fallback, as under upstream's most
  restricted profile, and as `RemoteVectorPainter`'s path clip already did. The answer is about the
  reader: the fork's writer can write every operation these functions ask about.
- **`Hoist`** records its states and writes them when the canvas flushes, at the container
  `Hoist` is called in, as upstream's (until the writer wrote declarations in place they ended
  ahead of the layout instead).
- **`RemotePath(pathData: String)`**, `arcTo` and `createFloatArray()` on the fork's `RemotePath`
  (fork-only), ported from `remote-core`'s `RemotePathBase` as AndroidX's JVM artifact parses a
  path string (`JvmRcPlatformServices.parsePath`): `M L H C S Q Z` and the arcs `A a`, anything
  else an `IllegalArgumentException`. `RemoteAnimatedVector.draw` reaches it through
  `RemoteComposeWriter.addPathString`, an extension in `vector/RemoteWriterPathString.kt`
  (fork-only), where AndroidX's writer asks its `RcPlatformServices`.
- **Writer** (`../remote-creation-writer`), each named as `RemoteComposeWriter` names it:
  `drawBitmapFontTextRun` (`DRAW_BITMAP_FONT_TEXT_RUN`, 48), `drawBitmapFontTextRunOnPath` (49),
  `textAttribute` (`ATTRIBUTE_TEXT`, 170), `nextId`, `rcPaint` (an `RcPaint` with the setters the
  carried Kotlin calls, and `commit`), `loop(…) { }` and `conditionalOperations(…) { }` around a
  body, `scale(sx, sy)` and `rotate(angle)` about the origin, and `buffer`, which is the writer
  itself: upstream reaches the pattern operations, `idLookup` and the paint through
  `writer.buffer`, and that text can now stand. New stand-ins for constants (in
  `upstream-fqn.tsv`): `Operations`, `ConditionalOperations`, `PaintContext`, `TextAttribute`.
  Inside `definePattern`…`endPatternDefine` and `addPatternForEach`…`endPatternForEach` the writer
  writes a declaration that reads one of the body's own ids (a parameter, the item, or something
  already declared from one) where it is made instead of ahead of the layout, and forgets it at
  the end of the body: the body is a template the player expands with its parameters replaced, so
  what it computes from them has to be in it. Every other declaration made in a body still goes
  ahead of the layout, so a value the body is the first to read is the same id for the code after
  it (as first written, the step put every declaration of a body in the body, and a named float
  read first in a pattern was undeclared after it). That holds for every kind of declaration:
  the writer has one way to write one (`declaration`, which is told what it reads; the plain
  `operation` refuses a declaration's opcode), so a runtime shader with a uniform of the parameter,
  a path, a text merged or formatted from it, a colour expression, a bitmap font of its bitmaps are
  all the body's. A body also owns what is defined in it by an operation written in place (a text
  attribute, a mesh, a themed colour, a touch expression, a float function) and any id reserved
  while it is open, so what reads those is the body's too. When a body ends the writer retires its
  ids (`RemoteWriter.isRetired`), and the creation state's caches, which are not scoped to a body,
  no longer answer one: an expression first asked for in a nested body is declared again in the
  body around it, however it is reached (a paint's states included, which the canvas's common
  sub-expression pass never sees). One case is refused rather than written wrong: an id taken
  for an expression of a parameter while the body is recorded, not when it is written
  (`(parameter * 2f).getFloatIdForCreationState(…)` for a path's coordinate, say) is declared
  before the writer knows the body exists and cannot be moved into it, so opening the body throws
  and says so. (`endPatternDefine` also measured the body in whichever
  buffer was written last; it measures the one the definition began in.) Nothing wrote a pattern
  before this step.
- **AndroidX's four tests of `remote-creation-expression`** are carried at their upstream path,
  `upstream/remote-creation-expression/src/test/java/…/state/` (`RemoteExpressionJvmTest`,
  `RemoteFloatOperationTest`, `RemoteStateCacheKeyTest`, `RemoteStateDomainTest`; 136 cases), and
  run as the fork's `jvmTest` with JUnit 4, Truth and TestParameterInjector as upstream. They are
  JVM tests because they read and evaluate what they wrote with AndroidX's reader (`remote-core`
  1.0.0-alpha23). Marked hunks: the fork's `RemoteDocumentWriter` for
  `RemoteComposeWriter(RcPlatformServices.None)`, the bytes it wrote handed to AndroidX's reader
  where upstream rewinds the writer's `RemoteComposeBuffer`, and the fork's enum hash
  (`::class.simpleName`) where two tests expect `declaringJavaClass.name`.

Skips recorded in the refreshes above that this step retires: the `RecordingCanvas` →
`RemoteCanvas` inlining (`33de12812`: `skew`, `saveCount`) and the `RecordingCanvas` deletion
(`fa48ff16b`); `RemoteTextMeasure`/`RemoteTextLayoutResult` (`109bc2783`, `6ee4d9bed`,
`981d83c75`, `d6687b729`), listed as Android-only before upstream moved them to
`jvmAndAndroidMain`; `currentProfileSupports*` (`c93e26675`); `Hoist`; and `CaptureTracer`
(`b75d2cb6a`). The path data upstream's `CanvasOperationBuffer` dedupes per span is carried with
it; the port's writer still dedupes for the whole document, so the bytes are the same.

Where the port's canvas wrote something other than upstream's text writes, the port's bytes were
kept under a marked hunk in this step: `usePaint`, the paint after a conditional block or an
offscreen bitmap, a scale or rotation about a pivot, `drawText`/`drawTextRun`/`drawTextOnPath`
given a `String`, the loop index, and `drawBitmap`'s content description. They are settled under
"Canvas port fixes" below.

Still not carried (31 files), and why:

| Files | Why |
| --- | --- |
| `capture/headless/` (10 files), `capture/CaptureUpdateThrottle.kt` | Step 5: the capture loop. `CaptureUpdateThrottle` is a parameter of that loop's capture functions and is built on `headless/UpdateRateLimiter`. |
| `capture/RemoteLog.kt` | Internal, logs through `RcPlatformServices` on the capture state's `Profile`, which the fork does not have, and nothing in upstream's main sources calls it. Goes with the `Profile` seam (step 6). |
| `capture/CustomComponentFactory.kt`, `util/DocumentStats.kt` | Both are over `remote-core`'s Java classes for a document that has been read (`operations.layout.Component`, `CoreDocument`). |
| `action/PendingIntentAction.android.kt`, `action/LambdaAction.android.kt`, `capture/RemoteWriterEvents.android.kt`, `capture/RemoteCapturedDocument.android.kt` | `android.app.PendingIntent`; `LambdaAction` stores its lambda in a `RemoteWriterEvents`, which holds them. |
| `capture/CaptureRemoteDocument.android.kt`, `capture/RemoteComposeCapture.android.kt`, `capture/Profile.android.kt`, `capture/headless/HeadlessLifecycleOwner.android.kt` | `android.content.Context`, `Configuration`, `Parcelable`, `androidx.lifecycle`, `androidx.savedstate`, and `remote-creation-core`'s `Profile` and Android writer. |
| `state/CompatAndroidRemotePaint.android.kt`, `state/RemoteColor.android.kt`, `state/RemoteFormatting.android.kt` | `android.graphics.Paint` and `Typeface`, `android.R` colour resources, ICU's `DecimalFormat`. |
| `vector/RemoteAnimatedVectorPainterAndroid.android.kt`, `vector/RemoteAnimatedVectorParser.android.kt` | `Context`, `Resources`, `XmlPullParser`: reading an animated vector drawable resource. |
| `widgets/AbstractRCWidget.android.kt`, `ProceduralRCWidget.android.kt`, `RCWidget.android.kt`, `RemoteComposeWidget.android.kt` | `android.appwidget`, `RemoteViews`. |

The ABI: the dumps of both modules change (`api/`). Removed: `RecordingCanvas`,
`RemoteCanvas(internalCanvas)` and `RemoteCanvas.internalCanvas` (now
`RemoteCanvas(creationState, enableOptimizations)`), and `RemoteCanvas.transform(Matrix)`, which
only ever applied the matrix's translation and had no caller. Changed: `RemoteCanvas.save()`
returns the save count. `RemoteWriter` gains five members an implementation has to provide
(`nextId`, `rcPaint`, `drawBitmapFontTextRun`, `drawBitmapFontTextRunOnPath`, `textAttribute`);
the rest of what is new there has a default.

## Canvas port fixes

The one change in the series that is meant to change what is written. Step 4 kept the port's bytes
wherever its canvas wrote something other than upstream's text writes; each of those was then
decided by reading both writers and both readers (AndroidX's `remote-core`, and this repository's
CMP player), fixed, and proved in a player: captured with the fork, played by `RcComposePlayer`
(`:rc-player-embedded`, the `RcCanvas*PlaybackTest`s), and read or painted by AndroidX's own
`CoreDocument` (`:rc-creation-bridge`, the `Canvas*ReadBackTest`s; `AndroidxHeadlessPaint.kt`
paints a frame into a context that records the calls AndroidX makes, with the values it resolved).

- **`usePaint` writes its paint.** Upstream's line. It recorded an empty operation, so
  `RemotePainter.draw(blendMode, alpha)` applied neither, a colour filter set through it tinted
  nothing, and a draw with no paint of its own used the one before. `renders/creation-use-paint/`.
- **The whole paint in and after a block that may not run.** Upstream's line: `forceSendingPaint`
  is read. After a `drawConditionally` block that was skipped, a draw whose paint equalled the last
  one written (in the block) sent nothing and was drawn with the paint before the block, in both
  players. Around `drawToOffscreenBitmap` the reason is AndroidX's player, which sets the paint's
  alpha when it switches to a bitmap (`AndroidPaintContext.drawToBitmap`); the CMP player keeps one
  paint there and drew it right before. `renders/creation-paint-after-block/`.
- **A pivot is in its operation.** Upstream's lines: `scale(sx, sy, px, py)` and
  `rotate(degrees, px, py)` write one `MatrixScale` or `MatrixRotate`, not translate, operation,
  translate. The same picture: a test compares the two forms' renders pixel for pixel.
- **A `String` goes to the writer with its length.** Upstream's lines for `drawText(String, …)`,
  `drawTextRun(CharSequence, …)` and `drawTextOnPath(String, …)`; `RemoteWriter` gains
  `drawTextRun(String, …)` with a default. The run's ends are 0 and the length where they were -1,
  which both readers draw alike.
- **A loop computes in its body what it computes from its index.** `RemoteCanvas.loop` is
  upstream's text (the index is a variable reserved when the loop is written; the integer overload
  writes its own loop). That was not the bug. The bug was the writer's: the expressions of the index
  were declared ahead of the layout with every other declaration, and AndroidX runs an expression
  there once a frame (`LoopOperation.paint` applies only the loop's own operations for each value
  of the index), so every iteration of a loop drew with one value. The CMP player re-evaluated
  header float expressions that read a loop index, which hid it there for floats; an integer index
  was wrong in both. The writer now writes a declaration that reads the index, directly or through
  another, in the loop, and forgets it at the loop's end, as it does for a pattern's parameters;
  everything else a loop body declares still goes ahead of the layout.
- **`drawBitmap(bitmap, left, top)` draws a rectangle of the bitmap's size.** Not upstream's line,
  which asks the platform for the size of an id and gets 0: the fork writes left + width and
  top + height, where it wrote the width and height themselves, right only at the origin. Marked.
- **A canvas bitmap has no content description: decided, not fixed.** The port's id 0 is what the
  format has for none; upstream's `""` declares an empty text and names it. Kept, marked, and
  written up for AndroidX.

Found on the way and fixed with them: `save()` returned a count `restoreToCount` did nothing with;
a pattern's body was written against the paint before it; a value a pattern's body was the first to
read was declared in the template and undeclared after it (all three are upstream's behaviour too:
see the local patches below and RC_CREATION_UPSTREAM.md); and in the CMP player, a pattern
definition written with its body's length did not link, and an image's width and height declared
ahead of the layout were never evaluated, so a canvas drew a bitmap at its own size as nothing.

What changed in the documents written: of the 129 distinct documents the JVM tests of
`:rc-creation-bridge`, `:rc-player-embedded`, `:rc-player-edit` and
`:third-party-remote-creation-writer` encoded before (18 of them the documents captured for step
4), 121 are encoded to the same bytes after. The 8 that changed are the four pattern and for-each
documents of `LoomPatternsTest`, the canvas document of step 4's harness under both density
behaviours (it calls every function above), and its decoration document under both (two
`RemoteBasicIcon`s, each now writing its paint reset). The six round-trip samples of
`toRemoteComposeKotlin` are unchanged, so nothing generated was regenerated.

## Upstream bug patches

Four bugs that are in upstream's own text, recorded until now as feedback for AndroidX only
(RC_CREATION_UPSTREAM.md, "Bugs and inconsistencies found"), are patched in the fork. Each is a
marked local patch with a test that failed before it, and each is still upstream's to fix.

- **An animated vector's animator repeats.** `painterRemoteAnimatedVector` held an animator with a
  non-zero `repeatCount` at its end value from the end of its first iteration. It now restarts or
  runs back, for floats and colours. Of several animators on one property the painter now
  chooses as `RemoteAnimatedVector.evaluate` does: the next takes over when its start delay is
  reached, where upstream's painter switched at the end of the earlier one's first iteration.
- **An animated vector's interpolators, the same in both evaluations.**
  `RemoteAnimatedVector.draw(writer, progress)` ignored the animator's own interpolator, and the
  painter ignored every keyframe's (and an `Accelerate` or `Decelerate` factor). Both now take the
  iteration and its direction, then the animator's interpolator, then the segment's end
  keyframe's. `AnimatedVectorEvaluationTest` (`:rc-creation-bridge`) draws the same animators both
  ways, has AndroidX's `CoreDocument` paint both documents, and compares what it draws. Left as it
  was: the painter's path morph, which knows neither repeats nor the animator's interpolator.
- **A text measurement does not change the paint a draw uses.** `RemoteTextMeasurer` writes its
  measuring paint where the measurement is first read, which on a canvas is between a draw's paint
  and the draw. The canvas now sends its paint again, whole, after a measurement written in one of
  its draws, and the next paint whole in any case. A paint one of whose own properties is a
  measurement is worked out again and sent whole, rather than sent again from inside its own
  resolution (which measured again without end, the measurement not being cached until its
  provider returns). `renders/creation-text-measure-paint/`.
- **A loop's body, and what follows a loop, draw with their own paints.** Suspected from reading,
  then shown: a body's first draw with the paint that preceded the loop was drawn with the body's
  last paint from the second pass on, and a draw after a loop that did not run with the paint
  before it. The writer forces the whole paint at `startLoop` and `endLoop`, as it does around a
  pattern. `renders/creation-loop-paint/`.

What changed in the documents written, measured as for the canvas port fixes (every document
`RemoteDocumentWriter` encoded while the JVM tests of `:rc-creation-bridge`, `:rc-player-embedded`,
`:rc-player-edit` and `:third-party-remote-creation-writer` ran, on `6368c6a1` and on the result):
of the 180 distinct documents before, 179 are encoded to the same bytes after. The one that
changed is `CarriedCreationApiTest.textMeasurer`'s, which reads five measurements in two draws and
now carries the draw's paint again after each: 365 bytes, five whole paints. No loop document
changed: none of the earlier tests draws before a loop with the paint its body starts with, or
after one. The animated vector patches change no document of an animator that does not repeat and
whose keyframes are linear, which is every one there was. The 183 documents that are new are the
new tests'.

## Relocation to `ee.schimke.composeai.rccreation`, and the module split (step 3)

Two changes made together, so that consumers move once. Neither changes a document: the JVM tests
of the writer, the three modules, `:rc-creation-bridge`, `:rc-player-embedded` and
`:rc-player-edit` encode the same bytes before and after
([RC_CREATION_UPSTREAM.md](../../docs/design/RC_CREATION_UPSTREAM.md), "How this was checked").

**The relocation.** Every package the fork declares moved, and nothing else:

| Was | Is |
| --- | --- |
| `androidx.compose.remote.creation.compose` and its subpackages | `ee.schimke.composeai.rccreation.compose` |
| `androidx.compose.remote.foundation` and its subpackages | `ee.schimke.composeai.rccreation.foundation` |
| `androidx.compose.remote.creation.common` (the writer; never an AndroidX package) | `ee.schimke.composeai.rccreation.common` |

What was done, and what a refresh repeats for each file it takes whole (this rewrite is all of
it; the writer's package is not AndroidX's, so an upstream file never names it):

```sh
perl -pi -e '
  s/\bandroidx\.compose\.remote\.creation\.compose\b/ee.schimke.composeai.rccreation.compose/g;
  s/\bandroidx\.compose\.remote\.foundation\b/ee.schimke.composeai.rccreation.foundation/g;
' <files>
```

- `git mv` each package directory, so history follows the files.
- Rewrite the three prefixes in every file: package statements, imports, qualified names, KDoc
  links and `@sample` references alike. References to packages the fork does not declare are left
  alone: `androidx.compose.remote.core.*` and the rest of `androidx.compose.remote.creation.*`
  (AndroidX's Java modules, which the JVM tests read through the released artifacts),
  `androidx.compose.runtime`, `androidx.compose.ui`, `androidx.annotation`, `androidx.collection`.
- `scripts/upstream-packages.tsv` records the map and `diff-upstream.sh` applies it to upstream's
  side, text and paths, before comparing. It also sorts imports where their AndroidX names sort, so
  the import block keeps upstream's order. The counts did not move: 232 hunks (163 marked, 59
  import-only, 10 unmarked) on both sides of the change.
- The AndroidX Gradle capabilities the Android variants declared
  (`androidx.compose.remote:remote-creation-compose`, `…:remote-creation-expression`,
  `androidx.compose.remote.foundation:foundation`) are gone, with the `capabilitiesResolution`
  rules in the widget modules that chose AndroidX over the fork. `verifyPublishedCapabilities`
  stays, in `published-capabilities.gradle.kts`: every published variant offers its own module's
  capability.
- One formatting difference fell out: `capture/RemoteComposeCaptureState.kt`'s KDoc line naming
  `RemoteComposeWriter` is one character shorter and fits on one line again, as upstream's does.

**The split.** One module became three, with the directories above. What had to change in the
sources, all of it in files that already carried marked hunks or in fork-only files:

| Where | What | Why |
| --- | --- | --- |
| `capture/RemoteComposeCreationState.kt` (`RemoteDocumentScope.lastComponentId`, `KMP`), `layout/RemoteFloatContext.kt`, `state/RemoteStateScope.kt`, `capture/RemoteComposeCaptureState.kt` | `RemoteFloatContext` asks the scope it is given for the last component recorded. The creation state answers with the id it tracks, `RemoteStateScope` with its parent's | Upstream reads `document.buffer.lastComponentId`, through the scope's `document`. The Kotlin writer has no such field while a render is recorded, so the fork read the creation state, and found it by casting the scope to `RemoteStateScope`, a type of the compose module. Asking the scope keeps upstream's shape (the scope is the way to the answer) and needs no type from above. |
| `state/RemoteStateCacheKey.kt` (`RemoteConstantCacheKey`, `KMP`), `state/RemoteStateVisitor.kt` | A constant key keeps the state of a value `resolveConstantState` cannot rebuild, which in practice is an image's; for a number, a boolean, a string or null it keeps none, as before. `resolveConstantState` is upstream's text again apart from `.javaClass` | Upstream's key holds a `WeakReference` to its state, so the visitor is handed an image constant's state and never rebuilds one. Without weak references the fork kept no state and rebuilt every constant, the image from its Compose `ImageBitmap`, which only the compose module can turn into a `RemoteImageBitmap`. Keeping the state for exactly the values that cannot be rebuilt changes nothing for the others (their keys still answer no state, so `isCanvasScopedExpressionKey` and the visitors see what they saw) and adds no lifetime: the state already holds its key and the bitmap. |
| `state/RemoteTextUnitDp.kt` (new, `expression/src`), `layout/RemoteText.kt` (`dpDocumentFontSizeDp`) | `RemoteTextUnit.toDp(fontScale)`, restricted | The DP font size patch in `RemoteText` (compose) used `RemoteFontScaleConverter`, internal to the expression module upstream. The conversion is offered from a fork-only file there, so upstream's declaration keeps its visibility. |
| `state/MeshExpressionLowering.kt` (`expression/src`) | Restricted public, where it was `@PublishedApi internal` | The creation state that consults it is the expression module's, and `Mesh2DWriterOp`, which sets it, is the compose module's. |

`RemoteDecimalFormat.kt` and `MeshExpressionLowering.kt` moved from the one module's `src/` to
`expression/src/`, because the expression sources use them. The modules are still built with the
Compose compiler, the expression module included (upstream's needs only `runtime-annotation`), so
their classes keep the stability metadata they were published with. The union of the three ABI
dumps is the one module's dump plus what the table adds.

The script's counts after the split: 233 hunks, 166 marked (three more: the member on
`RemoteDocumentScope` and its two overrides), 57 import-only (two fewer: `RemoteFloatContext` no
longer imports the creation state, `RemoteStateVisitor` no longer imports `ImageBitmap`), 10
unmarked, and 18 fork-only files (`RemoteTextUnitDp.kt`).

## Declarations in place, and a capture profile

Two consumers moved real content from AndroidX's `remote-creation-compose` to this fork and found
the documents did not play the same in AndroidX's own players (the View player, and Android 16's
platform player): a slider snapped back on release, a launcher's canvas expressions were no longer
in their canvas, an animated float of a measured size inside a state layout's branch vanished. The
CMP player played all of it. Two differences from AndroidX were behind it, and both are gone.

### Where an operation is written

The port wrote every declaration ahead of the layout, and its layout nodes recorded their writer
calls (`WriterOp`) to be written when the canvas flushed, once, at the end. AndroidX writes
everything where it is made, and its players read meaning from the place:

- `LayoutComponent.inflate` gives a `TouchExpression` to the component whose list it is in, and the
  expression moves only for touches on it;
- it gives a `ComponentValue` to the component it finds it in, whatever id the value names;
- an expression in a canvas's operations is evaluated when the canvas paints, after the touch
  expression before it; one ahead of the root is evaluated with the document, once, before any
  canvas is measured.

Now:

- **`RemoteDocumentWriter` writes a declaration where it is asked for.** `declaration(opcode, id,
  readsBody)` is still the one door. `beginGlobal()`/`endGlobal()`, as `RemoteComposeWriter`'s, move
  a section ahead of the root (they nest, where upstream refuses a second begin).
- **The writer shares by value only what a player loads with the document**: text, bitmaps, paths,
  text from a float, as `RemoteComposeWriter` does. It no longer shares float and integer
  expressions, integers, float arrays, id lists, text lengths or text lookups: an expression
  declared in one canvas is evaluated with that canvas, and upstream's creation state, with its
  `withCanvasOperationsScope` frames, decides what to reuse.
- **Layout nodes write as they render** (`RemoteCanvas.writeLayout`, which flushes what the canvas
  has recorded first), a canvas flushes before it ends, and both run in a
  `withCanvasOperationsScope`, as upstream's text has it. `WriterOp` remains as the value a node
  hands the Kotlin writer.
- **A component's values are written in the component.** Upstream's token emission is restored
  (`beginComponentContent`, `disableDirectComponentEmission`, `endComponentContent`, the emitted
  mask), with the modifier data's `afterOperations` standing where upstream's
  `RecordingModifier.then { }` element does. The Kotlin writer still names the component's own id
  in each value, where upstream's names the last component started.
- **A host action's constant name and a string change's constant value** are declared inside the
  click or touch modifier (`RemoteActionData.HostNamedText`, `StringChangeText`), as
  `remote-creation-core`'s `HostAction` and `ValueStringChange` do.

What is still moved, each marked `LOCAL PATCH (rc-players)` where it is done:

| What | Where it goes | Why |
| --- | --- | --- |
| A declaration made in a pattern or for-each body that reads nothing of a body's | Just ahead of the template | Upstream leaves it in the template and answers its id afterwards; see "What a pattern's body declares first…" in RC_CREATION_UPSTREAM.md. |
| A declaration made among a layout's operations of a kind AndroidX's `LayoutComponent.inflate` drops there (anything but `TextData`, `BitmapData` and the `ComponentData` operations: a named variable, an integer or long constant, path data, a float list, a time, image or text attribute, a text transform, a bitmap font, …), unless it reads an id a component's content declared in place | Ahead of the root | A named state first read by a component's modifier is otherwise one its host cannot set (`AndroidxPlayerParityTest.aNamedStateFirstReadInALayoutCanBeSetByTheHost` plays both writers' documents). In a canvas, whose drawing a player keeps whole, it stays in place; so does one that reads an expression written among the components (a `TextSubtext` of a text made from a touch value), which ahead of the root would name ids not declared yet (`DeclarationPlacementTest.whatReadsAComponentsValueIsNotMovedAheadOfIt`). |
| A named value (`addNamedInt`/`Float`/`String`/`Long`, a named colour) | Ahead of the root, once per name and type | A document has one variable for a name. Upstream's canvas scopes (`withCanvasOperationsScope`) forget a named state at each canvas's end and declare it again in the next, two ids for one name: a host that sets the name reaches the last, and AndroidX's alpha23 writer, with no such scopes, declared one (`AndroidxPlayerParityTest.aNamedValueReadByTwoCanvasesIsOneVariable`). |
| A `RemoteTextMeasurer` measurement first read outside a canvas, with its paint | Ahead of the root (`beginGlobal`) | `TextAttribute` is one of those kinds, and its paint has to precede it. |
| A component's value asked for after the component has been written (a later sibling sized from it) | After the root | Nothing more can be written in the component, and in another a player gives it to that one. Outside every component a player binds it by the id it names, if the component precedes it (`CoreDocument.registerVariables`): after the root, not ahead of it. Upstream reserves a variable no `ComponentValue` binds. |
| What a render requests of a component token | Forgotten when the render ends | A retained composition's second render is then its first; upstream's tokens keep the request and write those values earlier next time. |

### `Profile`

`:rc-creation-writer` declares AndroidX's capture profile under AndroidX's names, in
`ee.schimke.composeai.rccreation.common`: `Profile`, `RemoteComposeWriterFactory`,
`CreationDisplayInfo`, `HTag` (`remote-creation-core`), `RcProfiles`, `Header`,
`Operations.getOperations` (`remote-core`) and `RcPlatformProfiles` (`remote-creation`'s
`androidMain`). `scripts/upstream-fqn.tsv` maps them. Not carried: `RcPlatformServices`, which
upstream's profile also holds (the fork's capture takes an image encoder, and its path is pure
Kotlin), and `WidgetsProfileWriterV6`'s checks of a level 6 operation's fields.

`RemoteDocumentWriter` honours a profile as `RemoteComposeWriter` and `RemoteComposeBuffer` do:
`WireBuffer.start`'s refusal and message, `addTextComponentStart`'s `TextLayout` backstop,
`startCanvas`'s `CanvasContent` and `addModifierBorder`'s legacy form up to API level 7,
`filterAndValidateTags`, and `Header.apply`'s two headers. A writer made from a size alone
(`RemoteDocumentWriter(width, height, …)`) is as it was: it writes anything, and raises the profile
it declares to what its operations need.

In the Compose module, `RemoteComposeCaptureState` has upstream's `profile` and constructors again
(each with the image encoder the fork adds), `capture/RemoteProfileCapabilities.kt`,
`capture/PaintTracker.kt` and `vector/RemoteVector.kt` read it as upstream's text does, and
`capture/Profile.kt` is upstream's `Profile.android.kt` in common code, with a `headerTags`
parameter for what a consumer of AndroidX's API passes its own writer factory. The capture entry
points and `RemoteDocumentSession` take `profile` (default `RcPlatformProfiles.ANDROIDX`); the
`supportedOperations` forms of 3.0.0 are deprecated and behave as they did.

The operation tables were `:rc-player-edit`'s; they are in `:rc-player-protocol` now
(`RcProfileOperations`), and the writer depends on that module, so what a capture for a player may
write is what `validateFor` accepts for it.

### How it was checked

`:rc-creation-coexistence-check`'s `androidHostTest` (Robolectric) captures one content with this
fork and with AndroidX 1.0.0-alpha23, compares the documents field for field (but for component
numbering), and plays both in AndroidX's `RemoteComposePlayer`:

- `AndroidxPlayerParityTest`: a slider (a touch expression, text made from its value, a release
  that sends the text) sends 60 after a 60 px drag from both; before the change the fork's sent the
  resting value. A named value read by two canvases is one variable. An inline bitmap draws. A canvas's expression of its own measured width, and the same inside a state
  layout's branch, are in the canvas and draw the same bar. A click.
- `AndroidxProfileParityTest`: under `RcPlatformProfiles.ANDROIDX`, and under a launcher's profile
  as `rc-widget-launcher` builds it (API level 7, the widget and experimental operations less
  `CoreText`, `TextStyle` and the flow layout, `FEATURE_PAINT_MEASURE = 0`), the header is the same
  bytes and the operations the same, text a `TextLayout` and the canvas's drawing in a
  `CanvasContent`; the named profiles hold AndroidX's levels, bits and operation sets; a flow row
  under AndroidX's profile is refused with AndroidX's message.

Two differences remain between the fork (androidx-main) and alpha23 in those comparisons, both
upstream's own changes since: a text that names no alignment (the fork's `Start`, a local patch
over main's `Unspecified`, against alpha23's `Left`), and a paint's `wght` axis under a profile
without `CoreText`. alpha23's `clickable` also writes an empty `AccessibilitySemantics` that main's
writes only for a role.

## Known port issues

- **Scroll orientation** was reversed (`verticalScroll` wrote 1, the wire's horizontal). Fixed on
  its own, with `ScrollDirectionTest`.
- **Unbalanced `save`/`restore`.** As upstream since step 4, a `restore` without a `save` throws,
  a child block (`drawConditionally`, `loop`, `drawToOffscreenBitmap`, a pattern) has to restore
  what it saves, and a `save` with nothing drawn inside a block does not keep the block alive. The
  port wrote whatever sequence it was given. This is the one place step 4 is stricter than before.
- **`RemoteCornerBasedShape`'s shared radius** is still not taken (two unmarked hunks): it changes
  the documents of every shape whose four corners are equal and was left out of this change.

## Local patches

Every patched line sits under a `// LOCAL PATCH (rc-players)` comment. Each patch is generic enough
to offer upstream, and the list is meant to shrink. Paths are package-relative (below
`ee/schimke/composeai/rccreation/compose/`); a file marked (new) is under a module's
`src/commonMain`, the others under its `upstream/`.

| Where | What | Why |
| --- | --- | --- |
| `capture/CaptureRemoteDocument.kt` | `captureCommonRemoteDocumentInto(writer, …)`; the byte path calls it | Capture into any `RemoteWriter` |
| `capture/CanvasOperationBuffer.kt` (`save`) | `save()` returns the save count before it, not after | **Upstream bug.** `RemoteCanvas.save` documents its result as what `restoreToCount` takes to balance it, as `android.graphics.Canvas.save` returns; upstream returns the incremented count, for which `restoreToCount` restores nothing, so the save stays open (and a child block throws for the unbalanced save). `RcSaveRestoreToCountPlaybackTest` and `CarriedCreationApiTest` cover it. |
| `layout/RemoteCanvas.kt` (`drawBitmap(bitmap, left, top)`) | The rectangle is (left, top, left + width, top + height) | **Upstream bug.** Upstream's `document.drawBitmap(id, left, top, "")` asks the platform services for the size of an id, gets 0, and passes it as right and bottom. `RcCanvasBitmapSizePlaybackTest` and `CanvasBitmapReadBackTest` cover it. |
| `layout/RemoteCanvas.kt` (both `drawBitmap`s) | No content description is id 0 | Upstream passes `""`, which declares an empty text and names it as the description; 0 is what the format and `RemoteComposeWriter.drawBitmap(…, null)` have for none. |
| `../remote-creation-writer/.../RemoteDocumentWriter.kt` (`bodyScopes`, `declaration`, `startLoop`/`endLoop`, `definePattern`/`endPatternDefine`, `addPatternForEach`/`endPatternForEach`) | A declaration that reads an id a loop, pattern or for-each body owns (its index, parameters or item, or anything declared from them) is the body's, and is forgotten at its end; what a pattern or for-each body declares without reading one goes just ahead of the template | A player expands a template with its own ids replaced, and runs a loop's body once per value of its index; upstream leaves everything a template declares in it and answers the ids afterwards. `CanvasLoopReadBackTest`, `RcCanvasLoopPlaybackTest`, `LoomPatternsTest` and `RcPatternCapturedStatePlaybackTest` cover it. |
| `capture/RemoteDocumentSession.kt` (new) | A composition kept alive under a host `CompositionContext`, re-rendered into a fresh writer on demand; reports when a state its last render read changes (`onRenderInputsChanged`) | The live, in-process path. It mirrors upstream's Android `captureRemoteDocument` loop, including its `RenderInvalidationTracker`. |
| `capture/RemoteComposeCreationState.kt` (`getOrPutVariableId`, `getOrPutFloatArray`, `getOrPutLongArray`, `getOrPutFloatExpressionId`, `getOrPutIntExpressionId`, `readsRetiredId`), `../remote-creation-writer/.../RemoteWriter.kt` (`isRetired`) | A cached id, or a cached lowered expression that reads one, is not answered once the writer has retired it: it is asked for again | The caches are per document (upstream scopes some to a canvas), and what a pattern, for-each or loop body declares from its own ids exists only in that body. Without the check an expression first used in a nested body was answered with the nested body's id in the body around it. `BodyOwnedDeclarationsTest` covers the writer's side. |
| `capture/RemoteComposeCreationState.kt` | `resetComponentIds()`, which also forgets the component tokens' ids (the session sets upstream's own `document` for each render) | A session renders each frame into a fresh writer. Without the reset, every render renumbers every component (upstream has the same drift). |
| `layout/RemoteComposeApplier.kt` | `onChangesApplied` | Tells a session when to re-render |
| `modifier/ComponentIdModifier.kt` (new), `layout/RemoteComposeNode.kt`, `capture/RemoteComposeCreationState.kt`, `modifier/RemoteModifier.kt` | `RemoteModifier.componentId(id)`: a node takes a requested component id (at most `MaxExplicitComponentId`, -2,000,000, below both ranges a document allocates) instead of a positional one, once per render | Lets a host name components: an editor maps the player's geometry back to its own nodes (`RcComponentIds` in `:rc-creation-bridge`) |
| `capture/RemoteCreationDisplayInfo.kt` | `RemoteDensityBehavior.wireValue`; `RemoteDensity.from(creationDisplayInfo)` is public | Lets a writer outside the module describe the same display, and a host that makes its own session name the density a display captures at |
| `capture/Profile.kt` (`headerTags`), `capture/RemoteComposeCaptureState.kt` (`legacyProfile`, the deprecated `supportedOperations`), `capture/CaptureRemoteDocument.kt` and `capture/RemoteDocumentSession.kt` (the deprecated `supportedOperations` forms) | Header tags on the common `Profile(…)`; 3.0.0's capture forms kept | A consumer of AndroidX's API writes feature tags through a writer factory of its own; 3.0.0 callers keep compiling and capturing what they did |
| `capture/RemoteComposeCreationState.kt` (`getComponentValue`, `isInCanvasOperationsScope`), `layout/RemoteComponentToken.kt` (`saveRequested`, `restoreRequested`), `layout/RemoteComposeNode.kt` (`RemoteRootNode.render`), `layout/RemoteTextMeasure.kt` | See "Declarations in place, and a capture profile": a value asked for after its component, a render's token requests, a measurement outside a canvas | AndroidX's player drops or misassigns them where upstream writes them |
| `../remote-creation-writer/.../RemoteDocumentWriter.kt` (`addBitmapPng`) | An encoded image carried in the document is `TYPE_PNG_8888` (0), not `TYPE_PNG` (1) | **Port bug.** `RemoteComposeWriter.storeBitmap` writes the plain width and height for an inline image; `TYPE_PNG` is the type of a URL's or a file's, and AndroidX's `RemoteBitmapDecoder` refuses it inline ("TYPE_PNG is not allowed for ENCODING_INLINE"), so the image did not draw in AndroidX's players. rc-players' player reads both. `AndroidxPlayerParityTest.anInlineBitmapDrawsInAndroidxsPlayer`. |
| `../remote-creation-writer/.../RemoteDocumentWriter.kt` | `startCustom` content section takes a fresh component id | **Port bug.** Upstream's `addContentStart` allocates one; the port reused the layout's own id, and rc-players' player rejects the duplicate. The same fix is in yschimke/remote-m3-catalog's vendor copy (yschimke/remote-m3-catalog#133); this patch retires when that lands and the fork is re-taken from it. |
| `../remote-creation-writer/.../RemoteDocumentWriter.kt` (`definePattern`, `endPatternDefine`, `endPatternInflation`, `addPatternForEach`, `endPatternForEach`) | The first paint in a pattern or for-each body is written in full, and so is the first after a definition, a call and a for-each | **Upstream bug.** The body is a template the player draws where the pattern is called, with the paint it has there, but upstream's canvas records it through its one `PaintTracker`: a body whose paint equals the one before it has no paint operation, and a draw after a call sends none when its paint equals the one before the call. `RcPatternPaintPlaybackTest` and `LoomPatternsTest` cover it. |
| `../remote-creation-writer/.../RemoteDocumentWriter.kt` (`startLoop`, `endLoop`) | The first paint in a loop's body is written in full, and so is the first after the loop | **Upstream bug.** The body is drawn once per value of the index with the paint the player has then (the body's last from the second pass on) and not at all for an empty range, but upstream's `RemoteCanvas.loop` records it through the canvas's one `PaintTracker` with no forced paint: a body's first draw with the paint before the loop has no paint operation, and neither has a draw after the loop with the body's last. `RcCanvasLoopPaintPlaybackTest` and `CanvasPaintReadBackTest` cover it. |
| `layout/RemoteTextMeasure.kt` (`measureAttribute`), `layout/RemoteCanvas.kt` (`flush`, `paintWrittenOutsideTracker`, `usePaintInternal`, `recordRenderingOp`), `capture/RemoteComposeCaptureState.kt` (`onPaintWrittenOutsideCanvas`) | The canvas being written is told when a measurement has written its paint. Its next paint is whole in any case. If one of its draws is being written it sends the paint it last applied again, in full, there; if the measurement is a property of the paint being worked out (`RemotePaint { textSize = measured.size.width }`), that paint is worked out again and sent whole, once; if it is outside a draw (a transform, an expression shared by two draws, a layout modifier), the paint is sent again before the next draw that inherits it in the same component | **Upstream bug.** A measurement writes `rcPaint…commit()` and a `TextAttribute` where its value is first read, behind the canvas's `PaintTracker`. In `drawText(text, measured.size.width, y, paint)` that is after the draw's paint and before the draw, so text with a 12 px paint was drawn at the 20 px it was measured at, and later draws whose paint the tracker took to be unchanged inherited it. `RcTextMeasurePaintPlaybackTest` and `CanvasTextMeasureReadBackTest` cover it, the second case by case: a draw's argument, a paint's text size, stroke width and colour, `usePaint`, a transform, a shared expression, a measurement of a measurement, a conditional block, a loop's body, a pattern's body, a layout modifier. |
| `vector/RemoteAnimatedVectorPainter.android.kt` (`computeAnimatorLocalProgress`, `evaluateDynamicFloat`, `evaluateDynamicColor`) | An animator with a non-zero `repeatCount` restarts, or runs back under `REPEAT_MODE_REVERSE`, after each iteration, and ends after its last; an animator that does not repeat writes the expression it wrote before. Of several animators on one property, the next by start delay takes over when its start delay is reached, as `RemoteAnimatedVector.evaluate` chooses; two that are end to end write the expression they wrote before | **Upstream bug.** Upstream clamps the animator's progress to its first iteration, so it holds its end value for the rest of a timeline that `totalDuration` made long enough for all of them (100 ms, 0 to 1, repeated once in reverse: 1 at three quarters, not 0.5). And it switches from one animator to the next at the end of the first one's first iteration, so the painter and `evaluate` differ wherever two animators overlap or leave a gap. `AnimatedVectorEvaluationTest` covers it. |
| `vector/RemoteAnimatedVector.kt` (`RemotePropertyAnimator.evaluateAt`; `RemoteInterpolator.Accelerate.factor`, `Decelerate.factor`), `vector/RemoteAnimatedVectorPainter.android.kt` (`transformInterpolator`, `evaluateAnimatorFloat`, `evaluateAnimatorColor`) | One definition of an animator's value in both evaluations: the iteration and its direction, then the animator's interpolator, then the segment's end keyframe's. `evaluateAt` applies the animator's; the painter applies the keyframes', and an `Accelerate` or `Decelerate` with its factor (which becomes `internal` to be read) | **Upstream bug.** `evaluateAt` applied only the keyframe's interpolator (0 to 1 with `Accelerate` on the animator was 0.5 half way, not 0.25), and the painter only the animator's, as the factor-1 curve whatever the factor. `AnimatedVectorEvaluationTest` draws the same animators both ways and compares what AndroidX draws. |
| `../remote-creation-writer/.../RemoteDocumentWriter.kt` (`encodeToByteArray`) | A document that draws to a bitmap declares `PROFILE_ANDROIDX` unless it declares the AndroidX or widgets profile already | remote-core reads `DrawToBitmap` under no other profile and stops at it ("Unknown operation encountered 190"), so a profile-0 document with an offscreen buffer (`drawToOffscreenBitmap` included) did not load in AndroidX. Only for a writer made from a size alone (`RemoteDocumentWriter(width, height, …)`); one made for a `Profile` refuses the operation, as upstream's does. |
| `modifier/DrawWithContentModifier.kt`, `layout/RemoteDrawScope.kt`, `layout/RemoteContentDrawScope.kt` | A `drawWithContent` after padding draws inside it: translated past the padding before it, its scope's `width`/`height` reduced by it, its content drawn back at the component's origin | **Port bug.** AndroidX writes it this way, and the player does not inset a draw modifier itself, so the fork drew a padded component's decoration over its padding |
| `shaders/RemoteRuntimeShader.kt` (new), `../remote-creation-writer/.../RemoteWriter.kt` + `RemoteDocumentWriter.kt` (`addShader`) | `RemoteBrush.runtimeShader(source, floatUniforms, intUniforms, sizeUniform)`: AGSL written as `TextData` + `ShaderData` (`DATA_SHADER`) and set as the paint's shader; float uniforms are any `RemoteFloat` (resolved by the player every frame), and the painted size is passed as a `float2`. A document with a shader declares `PROFILE_ANDROIDX`, as one that draws to a bitmap does | The core format and both players run runtime shaders; the port had no way to write one. remote-core reads `ShaderData` only under the AndroidX profile. `ShaderDataWriteTest`, `RuntimeShaderBrushTest` (with AndroidX read-back) and `RcRuntimeShaderPlaybackTest` cover it. |
| `layout/RemoteMesh2D.kt` (new), `capture/Mesh2DWriterOp.kt` (new), `state/MeshExpressionLowering.kt` (new), `state/RemoteFloat.kt` (`combineToFloatArray`), `capture/RemoteComposeCreationState.kt` (`meshExpressionLowering`, and `getOrPutFloatArray` bypassing the cache while it is set), `../remote-creation-writer/.../RemoteWriter.kt` + `RemoteDocumentWriter.kt` (`addMesh2D`, `addMesh2DValues`, `drawMesh2D`, `matrixFromMesh2D`) + `ProtocolPrimitives.kt` (`Mesh2D`) | `RemoteCanvas.drawMesh2D` / `RemoteDrawScope.drawMesh2D(uCount, vCount, layout, image, paint, antialiasWidth) { ... }`: a parametric 2D mesh written as `AddMesh2D` (`TYPE_EXPRESSION`) plus `DrawMesh2D`, each channel (`x`, `y`, `texU`, `texV`, `alpha`, `red`, `green`, `blue`) the raw RPN of a `RemoteFloat` over `u`/`v` (`VAR1`/`VAR2`). Channels are lowered while `state/MeshExpressionLowering.kt`'s lowering is set on the creation state, which `combineToFloatArray` (`state/RemoteFloat.kt`) defers to. Every operand that reads `u`/`v` stays inline. Every other operand becomes a reference to an ordinary float. The channels are not registered as roots either. So a `u`/`v` expression is never hoisted into a document float, which the player would evaluate without `u`/`v`. A document with a mesh declares `PROFILE_EXPERIMENTAL`, plus `PROFILE_ANDROIDX` unless it names AndroidX or widgets | The core format (alpha23, experimental) and rc-players' players draw meshes; the port had no way to write one. remote-core reads the mesh operations only in its experimental V7 maps. `Mesh2DWriteTest`, `Mesh2DAuthoringTest` (with AndroidX read-back) and `RcMesh2DPlaybackTest` cover it. |
| `../remote-creation-writer/.../RemoteWriter.kt` + `RemoteDocumentWriter.kt` (`addTouchExpression`, `textMerge(outputId, …)`) + `ProtocolConstants.kt` (`TouchStop`) | A free-standing `TouchExpression` (opcode 157) into a reserved float, and a `TextMerge` into a reserved text id, as AndroidX's `RemoteComposeBuffer.addTouchExpression` / `textMerge(id, left, right)` write them; `Scroll`'s own touch expression is written through it, byte for byte as before | The fork wrote a touch expression only inside `Scroll`, so a drag control (a slider following `Touch.POSITION_X`, its value as a label) could be authored only against AndroidX's writer. `TouchExpressionWriteTest` (with AndroidX read-back) covers it. |
| `layout/RemoteText.kt` (`textAlign`) | An alignment neither the argument nor the style specifies is written as `Start` | **Upstream bug.** Since `99f14adf8` upstream passes `RemoteTextAlign.Unspecified` (`Int.MIN_VALUE`) through to `startTextComponent`: AndroidX's `CoreText` reads its low bits as start and its high bits as flags, and rc-players' player rejects it. |
| `layout/RemoteText.kt` (`dpDocumentFontSizeDp`) | In a `RemoteDensityBehavior.Dp` document a constant font size is written in dp, not the capture's pixels | **Upstream bug.** remote-core alpha22's `CoreText`/`TextLayout` multiply a literal font size by the host density in a DP document, as they do padding, so the pixels upstream writes drew text at density squared in the View player and the CMP player alike. A non-constant size stays the pixel expression, which the core reads unscaled. `DpDocumentFontSizeTest` covers it. |
| `../remote-creation-writer/.../RemoteDocumentWriter.kt` (`startCanvas`, `endCanvas`, `valueComponentId`, `componentValue`, `addComponentValue`) | A `ComponentValue` written directly in a canvas's `CanvasContent` for the canvas names the content, as AndroidX's writer does (`lastComponentId`). Only where the writer writes a `CanvasContent`: documents up to API level 7, which is every profile for Android's platform players (`WIDGETS_V6`, `WIDGETS_V7`, `RcCaptureProfiles.launcherWidgets`) and `ANDROIDX7`; level 8 and above name the canvas, as before | **Port bug.** The port's canvas node passed its own id. Android 16's platform player draws a canvas that reads its measured size as nothing when the value names the canvas (Cadence's notification progress bar: 0 pixels) and draws it when it names the content; Android 17's, AndroidX's and rc-players' players read either. `PlatformPlayerComponentValueTest` (`:rc-widget-launcher`, on API 36 and 37) and `AndroidxPlayerParityTest.aCanvasSizeInALevel7DocumentNamesTheCanvasContent` cover it. |

## Upstream follow-ups

Problems found here that belong in AndroidX, not in a local patch. The fork keeps upstream's
behaviour until a fix lands there and a refresh takes it. They are written up for AndroidX, with
the structural feedback, in
[`docs/design/RC_CREATION_UPSTREAM.md`](../../docs/design/RC_CREATION_UPSTREAM.md).

- **Dp header dimensions.** With `RemoteDensityBehavior.Dp`, `RemoteCreationDisplayInfo.size`
  (pixels) is written unscaled into a header the player reads as dp, so a 200 px display at
  density 2 becomes a 200 dp (400 px) document, and `RemoteConfiguration.windowWidth` reports
  200 dp instead of 100. Upstream: `RemoteCreationDisplayInfo.toCreationDisplayInfo()` in
  `compose/remote/remote-creation-compose/.../capture/RemoteCreationDisplayInfo.kt`. Here:
  `captureCommonRemoteDocument` and `RcEmbeddedPlayer.writerFor`, both marked `TODO(upstream)`.
  Raised in review of yschimke/rc-players#672.
- **Dp font sizes.** `RemoteTextNode.render` writes `fontSize.toPx(remoteDensity)`, a pixel
  literal when the density is constant, while `CoreText.updateVariables` scales a literal font size
  by the host density in a DP document. The fork writes dp there (local patch above); upstream
  should do the same, or the core should stop scaling. Upstream: `layout/RemoteText.kt`.

- **A named state is declared once per canvas.** `withCanvasOperationsScope` restores the
  variable cache at a canvas's end, named states included, so a name read in two canvases is two
  variables with one name, and `setUserLocal*` reaches one. The fork's writer keeps one id per name
  and type. Upstream: `capture/RemoteComposeCreationState.kt`.
- **What a layout's content declares, AndroidX's player drops**, and **a component's size asked
  for after the component is never bound**: both in RC_CREATION_UPSTREAM.md.

- **Unspecified text alignment.** `RemoteText` writes `RemoteTextAlign.Unspecified`
  (`Int.MIN_VALUE`) when neither its argument nor its style sets an alignment (`99f14adf8`), where
  it wrote `Start` before. The fork writes `Start` (local patch above). Upstream:
  `layout/RemoteText.kt`.

## Refreshing

Apply the upstream delta to the files this fork carries, as the port's earlier refreshes did, rather
than re-copying. For a file taken whole (one upstream adds, or one that was identical): copy it to
the mapped path, rewrite its packages with the command under "Relocation" above, and re-apply the
patches listed here. A file upstream adds, renames or moves between modules goes to the same path
under the `upstream/` of the module that mirrors its upstream module, in the fork's package
directories. `:rc-player-embedded:jvmTest` is the check that the seams still hold,
`:rc-creation-expression:jvmTest` runs AndroidX's own expression tests against the result,
`:rc-creation-coexistence-check:check` that nothing of AndroidX's package came in with the copy, and
`scripts/diff-upstream.sh --summary <androidx checkout>` should show no file under another name
and no more unmarked hunks than before; update the table above with its numbers. The script
applies the package map to upstream's side, so a file that was copied without the rewrite, or to
the unmapped path, shows up at once: as "not carried", or as a file that differs on every line that
names a package. A line that has to differ from upstream's gets a `KMP (rc-players)` or
`LOCAL PATCH (rc-players)` comment saying why.

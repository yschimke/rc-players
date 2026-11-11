# Forked: Compose Multiplatform Remote Compose creation

Covers `:third-party-remote-creation-compose` (this directory) and
`:third-party-remote-creation-writer` (`../remote-creation-writer`), published as
`third-party-remote-creation-compose` and `third-party-remote-creation-writer`.

This is a fork of AndroidX's Remote Compose **creation** stack, ported to Compose Multiplatform, so
`@RemoteComposable` content can be authored and captured on every rc-players target.

The fork stays in **AndroidX's own package** (`androidx.compose.remote.creation.*`) and changes as
little as it can. Component libraries such as Wear `remote-material3`, catalog code, and code a UI
builder exports are all written against that API. They then compile against this fork in a CMP host
and against real AndroidX on an Android device, from the same source.

Anything rc-players adds to creation lives outside this fork, in its own package
(`ee.schimke.composeai.rccreation`, `:rc-creation-bridge`). The fork carries only the seams that
layer cannot reach from outside, and each one is a marked local patch.

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
| `upstream/remote-creation-expression/src/main/java` | `remote-creation-expression/src/main/java` |
| `upstream/remote-creation-compose/src/jvmAndAndroidMain/kotlin` | `remote-creation-compose/src/jvmAndAndroidMain/kotlin` |
| `upstream/remote-creation-compose/src/androidMain/kotlin` | `remote-creation-compose/src/androidMain/kotlin` |
| `upstream/foundation/foundation/src/main/java` | `foundation/foundation/src/main/java` |
| `src/` | nothing: files only the fork has |
| `../remote-creation-writer` | write-side sources from `remote-core`, `remote-creation-core` and `remote-creation`, re-expressed as a common `RemoteWriter` |

## Layout, and measuring the difference

A file with an AndroidX counterpart sits under `upstream/` at the counterpart's path: the upstream
module, its source set, then the same package path and file name. So `upstream/` mirrors
`compose/remote/` of an androidx checkout, and the map between the two is the identity. All four
roots are source directories of the fork's one `commonMain` (`build.gradle.kts`); the directory
says where a file comes from, not which targets it compiles for. Upstream's `androidMain` files are
among them, under their `*.android.kt` names: what the fork carries from there needs only Compose
UI, which Compose Multiplatform has on every target.

`src/commonMain`, `src/androidMain` and `src/skikoMain` hold what has no upstream file: the local
additions, the stand-ins for Java classes (`RemotePath`, `RemoteDimensionType`,
`AnimationEncoding`, `RemoteDecimalFormat`), the port's own `RemoteComposeCreationContext`,
`RecordingCanvas` and `WriterOp`, and the platform `actual`s.

`scripts/diff-upstream.sh <androidx checkout>` compares the two trees. It sorts imports, rewrites
upstream's names for its Java classes to the fork's (`scripts/upstream-fqn.tsv`), and reports the
upstream files not carried, the fork-only files, and the hunks of every carried file that differs;
`--summary` prints the counts. `scripts/upstream-renames.tsv` lists carried files that are not at
their upstream path, and is empty. At androidx-main `21a1a95e4`:

| | Upstream files | Identical | Differing | Not carried | Under another name, split or merged | Fork-only | Differing hunks (unmarked) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Before the layout change | 210 | 58 | 115 | 37 | 23 | 19 | 590 (575) |
| After | 210 | 60 | 114 | 36 | 0 | 19 | 562 (548) |

A hunk is marked when it contains `LOCAL PATCH (rc-players)` or `KMP (rc-players)`. Most hunks are
not yet, and the script only counts them: they are the fork's older, unmarked differences (the
writer's shape, visibility, the creation-state types), which the plan removes. Once they are gone
the script should fail on an unmarked hunk.

Two carried files are partial, and say so at their top: `capture/RemoteComposeCaptureState.kt`
holds `NoRemoteCompose` and `LocalRemoteComposeCreationState` but not the class, which the fork has
not split out of `RemoteComposeCreationState` yet; and
`capture/RemoteCreationDisplayInfo.android.kt` holds the `widthDp`/`heightDp` extensions but not
`rememberRemoteCreationDisplayInfo`, which reads Android resources.

## The CMP port it starts from

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

Formatting: `./gradlew :third-party-remote-creation-compose:ktfmtFormat` uses ktfmt's kotlinlang
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
  `BaseRemoteState<Float>`. Its `cacheKey` stays `internal`, as `a07eab443` is not taken.
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
  the fork does not carry.
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
  group. Here they stay internal, as the port's cache keys are (`a07eab443`, skipped before).

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
`withCanvasOperationsScope` cache frames of `09c269541`, whose bug cannot occur here: the writer
hoists every expression into the document's declarations, so none is paint-only.

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
  `nanIdContentEquals`, `RemoteState<*>.cacheKey`): they stay internal, as the port's cache keys are (`a07eab443`, skipped
  before).
- `RemoteDocumentScope` and the `RemoteStateScope.resolveComponentToken()` member (`f1f51073e`
  made the resolution a public member so the expression module can reach it): the fork resolves
  tokens with `ab1b76a2f`'s internal walk of the scope chain.
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

- Nothing new. Visibility widened only for `remote-creation-expression` stays internal, as before.

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

The writer hoists declarations ahead of the layout, which upstream's does not, so a function is
written whole into the declarations: its body, declarations included, stays inline and in order,
because a player evaluates it only when it runs the function, and the dedup caches forget what the
body declared once it ends. The first paint inside a function, and the first after it, are written
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

## Local patches

Every patched line sits under a `// LOCAL PATCH (rc-players)` comment. Each patch is generic enough
to offer upstream, and the list is meant to shrink. Paths are package-relative; a file marked
(new) is under `src/commonMain`, the others under `upstream/`.

| Where | What | Why |
| --- | --- | --- |
| `capture/CaptureRemoteDocument.kt` | `captureCommonRemoteDocumentInto(writer, …)`; the byte path calls it | Capture into any `RemoteWriter` |
| `capture/RemoteDocumentSession.kt` (new) | A composition kept alive under a host `CompositionContext`, re-rendered into a fresh writer on demand; reports when a state its last render read changes (`onRenderInputsChanged`) | The live, in-process path. It mirrors upstream's Android `captureRemoteDocument` loop, including its `RenderInvalidationTracker`. |
| `capture/RemoteComposeCreationState.kt` | `writer` settable (internal); `resetComponentIds()`, which also forgets the component tokens' ids | A session renders each frame into a fresh writer. Without the reset, every render renumbers every component (upstream has the same drift). |
| `layout/RemoteComposeApplier.kt` | `onChangesApplied` | Tells a session when to re-render |
| `modifier/ComponentIdModifier.kt` (new), `layout/RemoteComposeNode.kt`, `capture/RemoteComposeCreationState.kt`, `modifier/RemoteModifier.kt` | `RemoteModifier.componentId(id)`: a node takes a requested component id (at most `MaxExplicitComponentId`, -2,000,000, below both ranges a document allocates) instead of a positional one, once per render | Lets a host name components: an editor maps the player's geometry back to its own nodes (`RcComponentIds` in `:rc-creation-bridge`) |
| `capture/RemoteCreationDisplayInfo.kt` | `RemoteDensityBehavior.wireValue` | Lets a writer outside the module describe the same display |
| `../remote-creation-writer/.../RemoteDocumentWriter.kt` | `startCustom` content section takes a fresh component id | **Port bug.** Upstream's `addContentStart` allocates one; the port reused the layout's own id, and rc-players' player rejects the duplicate. The same fix is in yschimke/remote-m3-catalog's vendor copy (yschimke/remote-m3-catalog#133); this patch retires when that lands and the fork is re-taken from it. |
| `../remote-creation-writer/.../RemoteDocumentWriter.kt` (`encodeToByteArray`) | A document that draws to a bitmap declares `PROFILE_ANDROIDX` unless it declares the AndroidX or widgets profile already | remote-core reads `DrawToBitmap` under no other profile and stops at it ("Unknown operation encountered 190"), so the fork's default profile-0 documents with an offscreen buffer (`drawToOffscreenBitmap` included) did not load in AndroidX. Upstream's writer refuses the operation instead; the fork names the profile, as it does the experimental one for custom components. |
| `modifier/DrawWithContentModifier.kt`, `layout/RemoteDrawScope.kt`, `layout/RemoteContentDrawScope.kt` | A `drawWithContent` after padding draws inside it: translated past the padding before it, its scope's `width`/`height` reduced by it, its content drawn back at the component's origin | **Port bug.** AndroidX writes it this way, and the player does not inset a draw modifier itself, so the fork drew a padded component's decoration over its padding |
| `shaders/RemoteRuntimeShader.kt` (new), `../remote-creation-writer/.../RemoteWriter.kt` + `RemoteDocumentWriter.kt` (`addShader`) | `RemoteBrush.runtimeShader(source, floatUniforms, intUniforms, sizeUniform)`: AGSL written as `TextData` + `ShaderData` (`DATA_SHADER`) and set as the paint's shader; float uniforms are any `RemoteFloat` (resolved by the player every frame), and the painted size is passed as a `float2`. A document with a shader declares `PROFILE_ANDROIDX`, as one that draws to a bitmap does | The core format and both players run runtime shaders; the port had no way to write one. remote-core reads `ShaderData` only under the AndroidX profile. `ShaderDataWriteTest`, `RuntimeShaderBrushTest` (with AndroidX read-back) and `RcRuntimeShaderPlaybackTest` cover it. |
| `layout/RemoteMesh2D.kt` (new), `capture/Mesh2DWriterOp.kt` (new), `state/MeshExpressionLowering.kt` (new), `state/RemoteFloat.kt` (`combineToFloatArray`), `../remote-creation-writer/.../RemoteWriter.kt` + `RemoteDocumentWriter.kt` (`addMesh2D`, `addMesh2DValues`, `drawMesh2D`, `matrixFromMesh2D`) + `ProtocolPrimitives.kt` (`Mesh2D`) | `RemoteCanvas.drawMesh2D` / `RemoteDrawScope.drawMesh2D(uCount, vCount, layout, image, paint, antialiasWidth) { ... }`: a parametric 2D mesh written as `AddMesh2D` (`TYPE_EXPRESSION`) plus `DrawMesh2D`, each channel (`x`, `y`, `texU`, `texV`, `alpha`, `red`, `green`, `blue`) the raw RPN of a `RemoteFloat` over `u`/`v` (`VAR1`/`VAR2`). Channels are lowered under `state/MeshExpressionLowering.kt`'s context, which `combineToFloatArray` (`state/RemoteFloat.kt`) defers to. Every operand that reads `u`/`v` stays inline. Every other operand becomes a reference to an ordinary float. The channels are not registered as roots either. So a `u`/`v` expression is never hoisted into a document float, which the player would evaluate without `u`/`v`. A document with a mesh declares `PROFILE_EXPERIMENTAL`, plus `PROFILE_ANDROIDX` unless it names AndroidX or widgets | The core format (alpha23, experimental) and rc-players' players draw meshes; the port had no way to write one. remote-core reads the mesh operations only in its experimental V7 maps. `Mesh2DWriteTest`, `Mesh2DAuthoringTest` (with AndroidX read-back) and `RcMesh2DPlaybackTest` cover it. |
| `../remote-creation-writer/.../RemoteWriter.kt` + `RemoteDocumentWriter.kt` (`touchExpression`, `textMerge(outputId, …)`) + `ProtocolConstants.kt` (`TouchStop`) | A free-standing `TouchExpression` (opcode 157) into a reserved float, and a `TextMerge` into a reserved text id, as AndroidX's `RemoteComposeBuffer.addTouchExpression` / `textMerge(id, left, right)` write them; `Scroll`'s own touch expression is written through it, byte for byte as before | The fork wrote a touch expression only inside `Scroll`, so a drag control (a slider following `Touch.POSITION_X`, its value as a label) could be authored only against AndroidX's writer. `TouchExpressionWriteTest` (with AndroidX read-back) covers it. |
| `layout/RemoteText.kt` (`textAlign`) | An alignment neither the argument nor the style specifies is written as `Start` | **Upstream bug.** Since `99f14adf8` upstream passes `RemoteTextAlign.Unspecified` (`Int.MIN_VALUE`) through to `startTextComponent`: AndroidX's `CoreText` reads its low bits as start and its high bits as flags, and rc-players' player rejects it. |
| `layout/RemoteText.kt` (`dpDocumentFontSizeDp`) | In a `RemoteDensityBehavior.Dp` document a constant font size is written in dp, not the capture's pixels | **Upstream bug.** remote-core alpha22's `CoreText`/`TextLayout` multiply a literal font size by the host density in a DP document, as they do padding, so the pixels upstream writes drew text at density squared in the View player and the CMP player alike. A non-constant size stays the pixel expression, which the core reads unscaled. `DpDocumentFontSizeTest` covers it. |

## Upstream follow-ups

Problems found here that belong in AndroidX, not in a local patch. The fork keeps upstream's
behaviour until a fix lands there and a refresh takes it.

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

- **Unspecified text alignment.** `RemoteText` writes `RemoteTextAlign.Unspecified`
  (`Int.MIN_VALUE`) when neither its argument nor its style sets an alignment (`99f14adf8`), where
  it wrote `Start` before. The fork writes `Start` (local patch above). Upstream:
  `layout/RemoteText.kt`.

## Refreshing

Apply the upstream delta to the files this fork carries, as the port's earlier refreshes did, rather
than re-copying. Then re-apply the patches above. A file upstream adds, renames or moves between
modules goes to the same path under `upstream/`. `:rc-player-embedded:jvmTest` is the check that the
seams still hold, and `scripts/diff-upstream.sh --summary <androidx checkout>` should show no file
under another name and no more differing hunks than before; update the table above with its
numbers.

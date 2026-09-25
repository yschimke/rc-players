# Vendored: `rc-embedded-player` (AndroidX experimental Compose embedded Remote Compose player)

`RcPlayer` — a **pure-Compose interpreter** for a Remote Compose `CoreDocument`. It walks the
document's operation tree and emits Compose layout and draw nodes directly, rather than painting to
a framework `Canvas` inside an Android `View`.

That contrast is the reason we vendor it. The player compose-ai-tools uses today
(`RemoteComposeIrReplay` → `androidx.compose.remote.player.compose.RemoteDocumentPlayer`) is backed
by `remote-player-view`'s `RemoteComposePlayer`, an Android `View` bridged into Compose via
`AndroidView`. The embedded player is what a host embedding Remote Compose content *inside* a Compose
tree actually gets — different layout, text, and draw code, and therefore different pixels. Having
both lets `rc-compare` diff them against the same baked PNG.

## Upstream

- Repository: <https://github.com/androidx/androidx>
- Path: `compose/remote/remote-player-compose/src/main/java/androidx/compose/remote/player/compose/embedded`
- Commit: `c36509dbc4a16ac3db9b968fb68929ab9b5c7f44` (`androidx-main`, 2026-09-23)
- License: Apache-2.0

The original 2026-07-29 snapshot came from the integration-test application. AndroidX moved the
implementation into `androidx.compose.remote:remote-player-compose` on 2026-08-07; the pin above is
the current library source after importing the subsequent correctness, transition, frame-limiting,
and preprocessing changes. The vendored copy remains useful as a pinned, locally patched baseline
that can be loaded alongside the published implementation.

### 2026-09-23 refresh

The source baseline now follows `androidx-main` at `c36509dbc4a`. This imports AndroidX's
`RcPlayerState` state-holder API: reactive, named float/int/boolean/string/colour/bitmap and float
array overrides, reset-to-authored-default support, and the state-based `RcPlayer` entry point.
The bootstrap is adapted to the fork's snapshot-backed store and lazy bitmap path; the pre-existing
document and captured-document entry points remain source compatible.

### 2026-09-08 refresh

This refresh imports the player changes through the pin above, adapted across the Android/JVM seam:

- DP-aware offsets, reactive custom integer/color properties, draw-stream `loadFloat`, dynamic
  circle/oval/gradient values, and indirect `DrawPath`/`ClipPath` ids;
- StateLayout and FitBox animated/shared-element transitions, including synchronized fades,
  unclipped size transforms, nested-spec duration selection, and intrinsic FitBox selection;
- the core `Limiter` frame throttle on Android; and
- AndroidX's single-pass document preprocessing, shared with the JVM renderer and extended here to
  retain this fork's lazy bitmap inventory.

### 2026-09-14 refresh

This refresh imports AndroidX's bounded FitBox candidate measurement, animation-time system-variable
support, and the upstream resolution of #98: raw dynamic sources for dimension constraints, offsets,
padding, and graphics-layer floats; density-aware constraints and padding; required constraint
semantics; fill-parent modes; and explicit fill fractions. The local graphics-layer adapter then
adds the still-missing transform-origin mapping, which #98 continues to track upstream.

### 2026-09-18 refresh

Eight upstream commits ported as fix ports (the pin above is unchanged; the deferred `RcPlayerState`
rework stays deferred — see the 2026-09-17 note):

- `deeace26cf4` — GraphContext's per-operation `derivedStateOf` wrappers replaced by per-evaluation-pass
  memoization (`EvalPassState`), so reconvergent expression DAGs evaluate in Θ(V+E) instead of
  O(2^D).
- `730322a70ba` — wall-clock and calendar time variables answered through a new `GraphTimeState`
  (vendored verbatim and added to the JVM shared list): `ID_TIME_IN_SEC/MIN/HR` and `ID_EPOCH_SECOND`
  are wall-clock readings quantized per second, `ID_ANIMATION_TIME`/`ID_CONTINUOUS_SEC` are the only
  continuous ids and the continuous second is wall clock too. The frame loop distinguishes
  continuous documents (per-frame loop), discrete-time documents (sleep to the next second) and
  static ones (settle immediately), and preserves a document's custom `RemoteClock` instead of
  substituting `RemoteClock.SYSTEM`. The fork's `epochBaseMillis`/`loadInteger(EPOCH_SECOND)` epoch
  path and its `LocalEpochBaseMillis`/`LocalCurrentTimeMillis` resolver special-case are removed
  with it. `GraphContextTimeTest` rewritten against the new model with a deterministic `FakeClock`.
- `866099217` — `MultiClickModifier` support (all gesture types coalesced into one
  `combinedClickable`) and literal integer ids below `RemoteComposeState.START_ID` answering their
  constant value through `SnapshotRemoteComposeState`.
- `2cebbfbd6` — a host-supplied `TypefaceResolver` delegates in `resolveFontFamily`. **Adapted:** the
  fork's `GraphContext` cannot name `remote-player-core` types (the JVM half shares that file and
  the artifact carries `android.graphics`), so the resolver seam is `LocalTypefaceResolver` declared
  in the Android-only `RcPlayerTextLayout.kt` and provided by `RcPlayer` from the context's
  resolver; the guard excludes this fork's `EmbeddedPlayerTypefaceResolver` (no
  `GmsFontTypefaceResolver` here). Upstream's `RcPlayerStateImpl`-keyed propagation is likewise not
  applicable.
- `da8cde4e8` — root-level draw operations execute via `Canvas` ahead of the children, and
  `filterQuality` is tracked from the paint bundle (ANTialias consumed; FILTER_BITMAP and
  IMAGE_FILTER_QUALITY map onto Compose's `FilterQuality` at the rect-overload `drawImage` sites —
  the `topLeft` overload has no such parameter).
- `e84a025c9` — root pointer events (press/move/release forwarded to the document) and the
  `SnapshotRemoteComposeState` integer literal resolution above.
- `c8456354e` — FitBox probe constraints bound per candidate type (collapsibles get their collapse
  axis), FitBox and StateLayout write GONE/VISIBLE child visibility for tree inspection, no-fit
  content measures unclamped, StateLayout adopts its active child's size (`wrapContentSize`), and
  `RcPlayerComponent`'s visibility gate defers to those two containers. The
  `LocalRcPlayerInspector` hooks in this commit belong to the harness CL and are imported with it.
- `ad58914fb` — CollapsibleLayout rewritten (vendored verbatim): weight-carrying children measured
  once weights are known, explicit GONE skipped, spacing accounted in the collapse walk, Compose
  `Arrangement` drives placement, container/child visibility written back, collapsed children record
  the layout cursor. FlowRow's `verticalArrangement` honours the layout's positioning.

Upstream's new test files are **not** vendored: they need upstream's `RcPlayerTestRule` /
`EnableEmbeddedPlayerRule` and Google Truth, none of which this module carries. `GraphContextTimeTest`
was rewritten (not ported) for the same reason. The JVM cut's shared source list (that module was
removed on 2026-09-25) gained `GraphTimeState.kt`.

### 2026-09-17 refresh

Two player-side fixes, imported file by file rather than by moving the pin: the commits below also
carry a new host-facing `RcPlayerState` and a creation-side theme-colour move that this fork has no
counterpart for yet, so the pin above still names the snapshot the rest of these sources track.

- **`modifier/GraphicsLayerModifier.kt`** — androidx-main `4969cdd96c6`. Upstream resolved the
  transform-origin mapping the 2026-09-14 entry above recorded as still missing (#98), and added the
  implicit value-change animation `GraphicsLayerModifierOperation`'s `AnimatableValue` wrappers
  imply: a 300ms `CUBIC_STANDARD` tween on discrete variable changes, skipped for the first value
  and for sources that are continuous, component-driven, or already animated. Taken verbatim modulo
  the package rewrite, plus one adaptation — `expressionDependsOnTime` drops upstream's
  `expr.mSrcValue ?: return false`, because the `remote-core` this module compiles against declares
  the field non-null. `state/RcPlayerState.kt`'s `expressionDependsOnAnimation` becomes `internal`
  to match upstream, which is what the modifier imports.

  Upstream's code reads `TRANSFORM_ORIGIN_X/Y` straight from the attribute's source, so an
  **absent** origin resolves to `remote-core`'s declared `0f` and pivots at the top-left. That is
  taken as is: the writer since `4969cdd96c6` writes a centre origin explicitly and omits only `0f`.
  `rc-player-compose` and the native Swift core read an absent origin the same way.
- **`RcPlayer.kt` theme initialisation** — androidx-main `6e43f08a938`. Resolved `ColorTheme`
  operations are now applied into the context during setup rather than only by the effect that runs
  after the first frame, so the opening frame is themed instead of showing authored defaults; the
  effect is additionally keyed on the `Context` and re-resolves through it, so a host moving the
  player to a differently themed context follows. Upstream's half of that commit also moves the
  `android.R.color` table into `remote-creation-compose` as `AndroidSystemColorMap`; this fork keeps
  its local `resolveAndroidThemeColors` table, so only the player hunk is imported.

Not imported, and deliberately: `0e26b42f7d0` and `550ad7d3801` (`RcPlayerState`,
`SnapshotRemoteComposeState`, `floatArrayState`) rework `RcPlayer`'s parameters — `550ad7d3801`
removes `namedColorOverrides` from `ExperimentalRemoteDocumentPlayer` — and land on top of local
deltas in the same functions. That is a pin move with caller changes, not a fix port, and it is
tracked separately.

### That premise has expired — so the vendored player left upstream's package

`androidx.compose.remote:remote-player-compose:1.0.0-SNAPSHOT` — which this module takes as an
`implementation` dependency, and which every Android consumer of it resolves since the catalog moved
to the androidx.dev snapshot line — **now ships the embedded player itself**: 138 class files under
`androidx/compose/remote/player/compose/embedded/`, the package these sources used to be vendored
into. **45 of the 56 files here collided by fully-qualified name** — the whole player, not a fringe:
`RcPlayer`, every layout and modifier file, `RcPlayerDrawing`, `RcPlayerPaint`,
`ExperimentalRemoteDocumentPlayer`, and `AndroidColorThemeResolver`, which carries one of the local
modifications below.

Which bytes ran was therefore decided by classpath ordering rather than by this directory. That is
worse than it sounds for a module whose whole job is to be a *comparison* lane: if the upstream class
won, the local delta silently was not there, the pixels changed with nothing in any log to say why,
and no `rc-compare` or CMP/Wasm parity number could be attributed to a known player. On 22 Aug 2026
it stopped being silent — upstream reshaped the entry point (`theme` moved to the end, a
`customPlugins: CustomPluginRegistry?` parameter added), so with upstream's copy first every
`remote-m3` render on preview.coo.ee died with

```
render failed: NoSuchMethodError: 'void androidx.compose.remote.player.compose.embedded
  .ExperimentalRemoteDocumentPlayerKt.ExperimentalRemoteDocumentPlayer(RemoteDocument, Modifier,
  int, ObjectIntMap, RcImageLoader, Function1, Function2, Function3, Composer, int, int)'
```

and `serve` — correctly — treats a `NoSuchMethodError` as non-recoverable and disabled the catalog's
whole live render lane ([#4464](https://github.com/yschimke/compose-ai-tools/pull/4464)).

**The fix was to stop squatting.** These sources now live in
`ee.schimke.composeai.rcembedded.player` (`.layout`, `.modifier`, `.state`, `.utils`), a package
nobody else publishes into, so both copies can sit on one classpath and neither can shadow the
other. Two things follow, and both are improvements rather than costs:

- a `rc-compare` or parity number is now attributable — the embedded column is *this* code, by
  construction rather than by luck;
- the availability gate in the connector
  ([`RemoteComposeIrReplay.kt`](../../data/remotecompose/connector/src/main/kotlin/ee/schimke/composeai/daemon/RemoteComposeIrReplay.kt))
  goes back to meaning "did the consumer ship the player", which is the question it always looked
  like it was asking. It still resolves the entry-point *method* rather than the class, as a
  seatbelt against a future re-vendor whose signature drifts.

The refresh workflow pays for this: a `diff -r` against an androidx checkout now shows a package line
and import block differing in every file. Handle it by rewriting upstream's package on the way in
rather than by hand — the transform is exactly

```
androidx.compose.remote.player.compose.embedded[.x] -> ee.schimke.composeai.rcembedded.player[.x]
androidx.compose.remote.player.compose.utils        -> ee.schimke.composeai.rcembedded.player.utils
```

(and the same with `/` for paths), after which the diff is as verbatim as it ever was.

Reproduce the overlap that motivated this:

```bash
./gradlew :third-party-rc-embedded-player:assembleDebug
jar=$(find ~/.gradle/caches -path '*remote-player-compose-1.0.0-SNAPSHOT*' -name classes.jar | head -1)
unzip -l "$jar" | grep -c 'compose/embedded'
```

**What relocation does *not* decide:** whether to keep vendoring at all. Upstream publishing the
player is still the condition under which vendoring stops being justified, and the two remaining
options — retire this module for the published artifact (re-baselining `rc-compare`, and noting that
upstream defaults `theme` to `Theme.SYSTEM` where this copy uses `Theme.UNSPECIFIED`), or keep it —
are now a considered choice rather than something forced by a build accident. Relocation is correct
under either, which is why it went first. Tracked as a follow-up to
[#4184](https://github.com/yschimke/compose-ai-tools/pull/4184).

### The path seam, and why it exists

*Historical since 2026-09-25, when the JVM cut was removed; the Android half described here stays.*

`RcPlayerDrawing.kt` was compiled into both this module and the jvm sibling, and calls
`RemoteComposeState.getPath` / `getTweenPath`. The two targets need different implementations:
upstream's Android version reaches `(path as AndroidPath).internalPath.conicTo(...)` behind an
SDK-34 gate, which a `kotlin("jvm")` module cannot call, so the jvm side vendors an adapted copy
routing CONIC through skiko (`utils/FloatsToPath.kt` there).

That split used to be arranged by the same squatting trick one layer down — the jvm copy declared
itself in `androidx.compose.remote.player.compose.utils`, so a single import string resolved to
upstream's AAR on Android and to the vendored copy on jvm. It never collided in practice (a jvm
classpath never sees the Android artifact), but it made which-code-runs a property of the classpath
again. Both targets now import `ee.schimke.composeai.rcembedded.player.utils.getPath`, and each
module supplies it: the jvm module from its adapted copy, this one by forwarding to upstream in
[`utils/PathUtilsAndroid.kt`](src/main/kotlin/ee/schimke/composeai/rcembedded/player/utils/PathUtilsAndroid.kt).
The Android rendering path is unchanged — it is the same upstream function it always called.

## What is vendored

The player proper: the package root plus `layout/`, `modifier/`, and `state/`, with local file splits
for the Android/JVM platform seams and the shared preprocessing pass. Upstream's
`demos/`, `integration/previews/`, and the `androidx.wear.compose.remote.material3.previews` sample
previews that live in the same source set are **not** vendored — they are demo/test scaffolding for
the integration-test app, and they drag in Wear Material3 and `remote-creation-compose` capture.

Package names are **not** verbatim: upstream's
`androidx.compose.remote.player.compose.embedded[.x]` is rewritten to
`ee.schimke.composeai.rcembedded.player[.x]` on the way in, because upstream now publishes an
embedded player into `androidx.compose.remote.player.compose.embedded` itself (see "That premise has
expired" above). Upstream's own tree still uses the original package — the rewrite is this
repository's destination, not a change to the source it is copied from. Apply it when refreshing and
a `diff -r` against an androidx checkout is verbatim again everywhere else.

## Copyright

**Every source file in this module carries the AOSP Apache-2.0 header, without exception.** That
holds for the three kinds of file here:

- the vendored player sources, which keep upstream's header verbatim — a refresh must not strip it,
  and the local-delta comments below sit *inside* those files rather than replacing their headers;
- `GmsFontProviderCertificates.kt`, whose certificate strings are copied out of an androidx resource
  file (Apache-2.0, same as everything else here) and which carries the header for that reason;
- the files written here (`build.gradle.kts`, `src/test/.../RcEmbeddedRenderHarness.kt`), which carry
  the same header because they exist only to build and exercise AOSP-derived code in an AOSP package.

Check before committing a refresh:

```sh
for f in $(find src -type f \( -name '*.kt' -o -name '*.xml' \)) build.gradle.kts; do
  head -6 "$f" | grep -q 'The Android Open Source Project' || echo "MISSING: $f"
done
```

## Version skew

Upstream builds this player against the **in-tree** `remote-core` / `remote-player-core`. We build
it against the published alphas the version catalog pins (`compose-remote = 1.0.0-alpha19`). The
player reaches a number of `@RestrictTo(LIBRARY_GROUP)` members, and `CoreDataAccessors.kt` reaches
private `CoreDocument` state **reflectively** (upstream guards those names with its own
`CoreReflectionGuardTest`). Both are sensitive to the gap between `androidx-main` and the pinned
alpha, so a snapshot refresh should be paired with a render of the `rc-compare` lane, not just a
compile.

## Local modifications

See the `rc-embedded` column of the catalogs' `rc-compare.html` for the current visual delta against
the baked PNG. Local deltas over the upstream snapshot are listed here as they are made, each with
the upstream tracking issue it was reported under.

**Each delta that is a genuine upstream fix now has an issue on this repository**, so the patch has a
home and a place to be retired from. They are the ones to work off — the rest of what `diff -r`
shows against an androidx checkout is this repo's own restructuring (the platform seams that made
the since-removed JVM cut possible, and a few file splits), not something upstream owes anyone:

| Issue | Delta |
| --- | --- |
| [#3](https://github.com/yschimke/rc-players/issues/3) | `Rc.AndroidColors` is wrong for 21 of 196 indices — upstream's data, not its rendering |
| [#5](https://github.com/yschimke/rc-players/issues/5) | Published `ui-text-google-fonts` AAR ships no GMS font-provider certificates |
| [#54](https://github.com/yschimke/rc-players/issues/54) | `findBitmaps` never walks a component's canvas stream, so a `BitmapData` declared there is unregistered — costing both the texture and the `ImageAttribute` dimensions; `ImageAttribute` also needs an explicit draw case, being neither `VariableSupport` nor `VariableProvider` |
| [#98](https://github.com/yschimke/rc-players/issues/98) | The embedded graphics-layer adapter still ignores authored transform origins and bypasses core's implicit value-change animation |

The action-dispatch pair was restored verbatim when alpha17 published `LambdaAction`,
`PendingIntentAction.Companion.parseId` and `CapturedDocument.lambdas` / `.pendingIntents`. The
2026-09-08 refresh also retires issues #1, #2, #4, #6, #7, #47, #50, and #52 from the delta table:
AndroidX now carries their behavior. That is the pattern working — the list is meant to shrink.

- **Default Compose paint colour aligned with the framework player** (`RcPlayerPaint.kt`).
  `ComposeLocalPaint.color` initialized to transparent ARGB `0`, while `android.graphics.Paint`
  initializes to opaque black. A Remote Compose icon paint bundle sets a `SRC_IN` colour filter but
  no base `COLOR`; the Compose renderer therefore tinted a transparent source and drew no icon,
  while the View renderer tinted its default-black source successfully. The default is now
  `Color.Black`, while `isColorSet` remains false so an implicit default is still distinguishable
  from an explicit `COLOR` operation. `RcPlayerPaintTest` pins the player default and
  `ComposePathColorFilterRobolectricReproTest` independently demonstrates the SrcIn behaviour using
  only standard Compose drawing.

- **Graphics layers apply authored transform origins** (`GraphicsLayerModifier.kt`). AndroidX now
  preserves raw variable sources for the float attributes it consumes, but it still omits
  `TRANSFORM_ORIGIN_X` / `TRANSFORM_ORIGIN_Y` when constructing Compose's graphics layer. This copy
  resolves both reactively and assigns `TransformOrigin`; [#98](https://github.com/yschimke/rc-players/issues/98)
  also tracks the remaining implicit 300 ms `AnimatableValue` parity decision.

- **A colour the document derives now reaches the text that names it**
  (`SnapshotRemoteComposeState.kt`, `RcPlayerTextLayout.kt`, `CoreDataAccessors.kt`,
  `CoreDataModel.kt`). Two halves of one bug. `SnapshotRemoteComposeState` mirrors the base store
  into `SnapshotStateMap`s, and overrode every write except `updateColor` — which is exactly where
  `RemoteContext.loadColor` lands, so a computed colour was written to the base store while the
  mirror went on serving what it had cached on the first read. And `RcPlayerText` took its colour
  from the op's reflected `mColorValue`, the value resolved the last time `updateVariables` ran: for
  a `ColorExpression` over `ColorAttribute` channels that happens at document load, when the
  channels are still 0, so the op carried `rgb(alpha, 0, 0, 0)` for life. The store now writes
  through, and the text reads by id when the op's own `mIsDynamicColorEnabled` says the colour is
  computed — gated on that flag rather than on `mColorId`, which is set for literals too and whose
  store lookup returns 0, blanking every specimen document's text. `RcDerivedColorRenderTest` pins
  both on pixels against the published `remote-m3` documents; over the whole 475-document catalog
  the change leaves 437 untouched and moves 24 closer to the View player.

- **The computed-op index follows a component's canvas stream too**
  (`RcPlayerCompositionLocals.kt`). `buildComputedOpIndex` recursed through `Container.getList()`
  only, and a component's draw-content operations are not children — they hang off it as a field,
  reachable through `LayoutComponent.getCanvasOperations()`. That would not matter if their values
  stayed inside the draw pass, but `remote-m3` builds a disabled label's colour in the *layout* tree
  from `ColorAttribute`s declared in the *canvas* stream, so the expression resolved its channels
  against a store nothing had written and published `rgb(alpha, 0, 0, 0)`. The labels were drawn the
  whole time, fully transparent: a disabled `RemoteButton` peaked at the container's own alpha 31
  where the View player draws 116, and a disabled `RemoteCheckboxButton` drew its container and its
  box and no text. The walk now follows both edges, which is what `RcPlayer.kt` already does in two
  other places. It is not a creation bug — the View, JS and CMP players all draw these documents
  correctly from the same bytes.

- **Indexed Android `ColorTheme` values resolved at cold start**
  (`AndroidColorThemeResolver.kt`, `RcPlayer.kt`). Upstream's embedded player applies each
  `ColorTheme` operation but never performs the View player's preceding `ThemeSupport.mapColors`
  pass, so both light and dark branches retain their authored fallbacks. The embedded player now
  maps the index to a framework `android.R.color` resource before its first operation replay,
  retains the fallback for unknown groups or unavailable resources, and exposes an explicit `theme`
  parameter that also reapplies colors when it changes. The paired Robolectric conformance test
  authors one document through the public writer API and checks the dark path against the AndroidX
  View player; light is deliberately excluded because alpha17's View player has a separate
  cold-start light-theme bug.

  Two follow-ups, both from not taking the AndroidX pieces on trust:

  - **The index table is transcribed, not derived** (`ColorThemeResolution.kt`). Deriving it by
    reflecting over `Rc.AndroidColors` and lowercasing the field names — which is how this started
    — is wrong for **21 of the 196 indices** at alpha17. `SYSTEM_ACCENT2_200` is `30`, colliding
    with `SYSTEM_ACCENT2_1000`, so index `31` has no constant and index `30` resolves to whichever
    field reflection yields: a real resource, and the wrong colour. Twenty more are misspelled
    against the resource they select (`SYSTEM_ERROR_620` at index `62`, where the resource is
    `system_error_10`; the whole `system_neutral1_*` run at 78–90 spelled `SYSTEM_NEUTRAL78_0`,
    `SYSTEM_NEUTRAL79_790`, …), so they match nothing and the colour keeps its fallback with no
    diagnostic. The table now comes from `ThemeSupport.AndroidColors` in `remote-player-view`,
    which is what actually resolves indices on a device. `AndroidColorTableDriftTest` keeps it
    equal to the CMP player's copy in `:rc-player-protocol`.

  - **`SYSTEM` / `UNSPECIFIED` are resolved to a real mode** (`resolveThemeMode`, used by
    `RcPlayer` and the jvm renderer). `theme` used to default to `Theme.UNSPECIFIED` and be
    assigned straight to `paintTheme`, and `ColorTheme.apply` selects light only for `Theme.LIGHT`
    — so the default rendered every themed document *dark*, having just resolved the light palette
    correctly. Measured on a themed document, the embedded lane drew `#292A2D`
    (`system_surface_container_high_dark`) where the View lane drew `#E9E7EC`
    (`…_high_light`); both now draw `#E9E7EC`. The default is `Theme.SYSTEM`, answered from
    `isSystemInDarkTheme()` rather than from a `Configuration` read, so every player resolves it
    the same way — the View player's own rule (an SDK-33-guarded `Configuration.isNightModeActive`,
    with `UNSPECIFIED` falling through to dark) is where the divergence was found, not the
    specification it was fixed against.

### Resolved: the two action-dispatch deltas (restored at alpha17)

The `LocalRemoteNamedActionHandler` block and the `CapturedDocument` overload's `lambdas` /
`pendingIntents` forwarding (both `RcPlayer.kt`) were **build-against-published-alpha** gaps rather
than rendering fixes. `compose-remote` 1.0.0-alpha17 publishes all three symbols, so both blocks are
**restored to upstream verbatim** and this module carries no action-dispatch delta.

Recorded because the shape recurs: when this module cannot reach a symbol upstream compiles against
in-tree, drop the call and note it here — the note is what makes the delta findable once a later
alpha closes the gap.

- **GMS font-provider certificates inlined as source** (`GmsFontProviderCertificates.kt`; the
  `GoogleFontR` import is gone from `EmbeddedPlayerTypefaceResolver.kt` and `RcPlayerTextLayout.kt`).
  Upstream reads `com_google_android_gms_fonts_certs` off the google-fonts library's `R`, but the
  **published** `androidx.compose.ui:ui-text-google-fonts` AAR ships an empty `<resources/>` and a
  zero-byte `R.txt` — that array lives only in the library's `src/androidTest/res`, so it never
  reaches a consumer.

  Both consumers take the certificates directly as `List<List<ByteArray>>` — `GoogleFont.Provider`
  and `FontRequest` each carry that constructor beside the resource-id one — so no resource table is
  needed to supply them. The base64 strings are the verbatim contents of the `_dev` / `_prod` arrays
  from `compose/ui/ui-text-google-fonts/src/androidTest/res/values/font_certs.xml` at the pinned
  commit (same Apache-2.0 source), with the XML's line-wrapping whitespace removed; they are
  generated from that file rather than transcribed. Behaviour is unchanged: same certificates, same
  provider, same bytes.

  This replaces an earlier delta that vendored `font_certs.xml` into this module. That worked, but
  it obliged the module to carry a resource table for two constants — which meant
  `androidResources = true`, which in turn made the KMP restructure depend on whether the KMP-Android
  library plugin supports resource processing. Inlining removes the question rather than answering
  it.

  Worth reporting upstream on its own — any out-of-tree consumer following the documented
  downloadable-fonts pattern against the published artifact hits this, not just this player.

- **`GraphContext` reparented off `AndroidRemoteContext`** (`GraphContext.kt`, plus the new
  `StoreBackedRemoteContext.kt`). Upstream's `GraphContext` extends `AndroidRemoteContext`, which
  pinned it — and through `LocalGraphContext`, the whole state/expression path — to Android for
  behaviour it never used: it overrides every platform-bound member away (`loadBitmap`, `loadShader`
  are empty bodies) and shares the store explicitly.

  `StoreBackedRemoteContext` is a platform-neutral `RemoteContext` **ported from
  `AndroidRemoteContext` at the pinned commit**, method for method, minus the one method that cannot
  come along. That framing is deliberate: `GraphContext`'s leaf reads call `super.getFloat`/
  `getText`/…, so a reimplementation that diverged would change every computed value — which is every
  pixel. The bodies are upstream's, not ours. (`getText` reads via `getFromId`, not a typed getter —
  exactly the sort of thing a from-signatures guess gets wrong.)

  This is only viable because `AndroidRemoteContext` is barely an Android class: of its 63 methods,
  **five** touch the platform, and four of those (`setAndroidContext`, `setBitmapLoader`,
  `setTypefaceResolver`, `useCanvas`) are its own API rather than the `RemoteContext` contract. The
  sole contract method that does is `loadBitmap` — which `GraphContext` already stubbed. Worth
  reporting upstream alongside their issue #12: the split they describe is close to mechanical.

- **Canvas text gathered behind a platform seam** (`RcPlayerTextPlatform.kt`, plus `toTextStyle` in
  `RcPlayerPaint.kt`). Three of the four canvas text ops reached for `android.graphics` inline:
  `DrawTextAnchored` built an `android.graphics.Paint`, measured with `getTextBounds`, and drew via
  `nativeCanvas.drawText`; `DrawTextOnPath` and `DrawTextOnCircle` drew via
  `nativeCanvas.drawTextOnPath`, the latter also measuring with `Paint.measureText`.

  Those framework calls now live in four functions in one file — `measureTextInkBounds`,
  `measureTextWidth`, `drawTextAtOriginPlatform`, `drawTextOnPathPlatform` — with the
  `android.graphics.Paint` builder (`toNativeTextPaint`) private alongside them. **This is a move,
  not a port: the bodies are the same framework calls, so Android's text output is unchanged.** What
  moves out of the ops is only the geometry that was never platform-specific — the anchoring
  arithmetic, and the arc construction, which switches from `android.graphics.Path` to Compose's
  `Path.addArc` (the same framework call underneath on Android).

  The seam is deliberately *below* Compose's text APIs rather than through them. An earlier revision
  of this delta drew anchored text with `drawText(TextLayoutResult)`; that is a rendering change on
  Android, and it also splits measurement from drawing — `toNativeTextPaint` resolves named and
  downloadable families that the `TextStyle` path maps to `FontFamily.Default`, so a document naming
  a font would be measured with one face and drawn with another. Keeping both sides on the framework
  `Paint` makes that class of drift impossible by construction.

  The four take a **`TextPaintSpec`** (`RcPlayerTextPaintSpec.kt`) rather than `ComposeLocalPaint`.
  That projection is what makes the seam implementable off Android at all: the paint state carries
  brushes, colour filters and a framework `Shader`, and stays Android-coupled until the AGSL path
  (issue #2954) is seamed, while the text ops need six fields out of it — size, family id, weight,
  slant, whether a typeface was set, and the alpha-folded ARGB. `ComposeLocalPaint.toTextPaintSpec()`
  is a pure projection with no mapping or defaulting; `TextInkBounds` moved into the same file, since
  both halves return it. A side benefit at the two call sites that measure *and* draw
  (`DrawTextAnchored`, `DrawTextOnCircle`): they now build one spec and hand it to both, so the
  "measured with one face, drawn with another" failure is impossible per call rather than merely
  unlikely.

  `toTextStyle` is a pure extraction of `DrawText`'s own inline `TextStyle` construction, its only
  caller, generics-only family mapping and upstream `aosp/4187117` TODO included. Unifying it with
  the native ops means teaching it the richer resolution, not pointing the native ops at it; that is
  a separate change with its own render verification.

  Net: `android.graphics.Paint` is gone from `RcPlayerPaint.kt`, and `RcPlayerDrawing.kt` no longer
  names `android.graphics` or the native canvas at all — it is down to `Bitmap`/`BitmapDrawable`,
  i.e. the image-decode seam. Behaviour-preserving on Android, and unlike a rendering change it does
  not need the rc-compare lane to say so.

- **`PaintBundle.TEXTURE` uses multiplatform `ImageShader` instead of `BitmapShader`**
  (`RcPlayerPaint.kt`). The texture path built a framework `BitmapShader` and needed a parallel
  `nativeTileMode` table to feed it — a second tile-mode mapping alongside the Compose `mapTileMode`
  the same file already used everywhere else. `ImageShader` is the multiplatform equivalent and takes
  the Compose `TileMode` directly, so the duplicate table is gone and the bitmap goes through
  `asImageBitmap()`.

  Behaviour-preserving: same tile modes, same image, still wrapped as a `ShaderBrush`. Note the
  *other* `BitmapShader` — the one binding a bitmap uniform inside `buildRuntimeShader` — belongs to
  the AGSL path, which now lives in its own seam file (`RcPlayerShaders.kt`, next entry), not in
  `RcPlayerPaint.kt`.

- **AGSL runtime shaders isolated behind a platform seam** (`RcPlayerShaders.kt`, split out of
  `RcPlayerPaint.kt`; issue #2954). The paint decoder's `SHADER` and `SHADER_MATRIX` paths were the
  one part of it that cannot become a single multiplatform implementation: `RuntimeShader` (AGSL),
  the bitmap-uniform `BitmapShader`, `android.graphics.Matrix` for the local matrix, and the API-33
  `Build` guard have no portable Compose equivalent — desktop Compose exposes SkSL `RuntimeEffect`
  through skiko, a different shading language *and* uniform-binding API.

  So the two functions that touch them, `buildRuntimeShader(shaderId, remoteContext): Shader?` and
  `applyShaderMatrix(paintState, matrixWord, read)`, move verbatim into `RcPlayerShaders.kt` and the
  rest of `RcPlayerPaint.kt` — brushes, tile modes, blend modes, colour filters, images — is left
  expressed in multiplatform Compose graphics. The seam's signatures are the multiplatform
  `androidx.compose.ui.graphics.Shader` (a typealias for `android.graphics.Shader` here), so a
  jvm/desktop file supplying the same two functions over skiko is a drop-in with no change to the
  shared decoder. `RcPlayerPaint.kt` no longer imports `RuntimeShader`, `BitmapShader`, `Matrix` or
  `os.Build` — and with framework `Paint` already gone (the text port, above) it now imports no
  `android.*` at all. It is *import-clean* but not yet movable: the TEXTURE path still calls the
  in-package `resolveBitmap`, which stays in androidMain, so it graduates when the bitmap seam does
  (the `GraphContext`-style "import-clean ≠ movable" distinction the sequencing draws).

  Behaviour-preserving on Android: the bodies are the same code with the same call sites, verified
  by compile. The Android AGSL implementation is all this file carries; the desktop counterpart is
  **still deferred**, because the embedded player's shader output already diverges from the View
  player *on Android* (~89% on `ShaderGradientSticker` in the rc-compare lane), and that wants
  understanding before the path is used as a desktop baseline (sequencing step 5).

- **Bitmap decode/blit gathered behind a platform seam** (`RcPlayerImagePlatform.kt`, plus the
  `resolveImage`/`resolveCanvasImage`/`prepareOffscreenTarget` calls in `RcPlayerDrawing.kt` and the
  `TEXTURE` path of `RcPlayerPaint.kt`). The draw path reached `android.graphics.Bitmap` inline in
  three shapes: `resolveBitmap`/`resolveCanvasBitmap` returned a framework `Bitmap` that the blit ops
  (`DrawBitmap`/`DrawBitmapScaled`/`DrawBitmapInt`, the three bitmap-font ops) converted with
  `asImageBitmap()`; the `DrawToBitmap` offscreen target did the mutable-copy + `eraseColor` +
  `Canvas(target.asImageBitmap())` dance; and `RcPlayerPaint`'s `TEXTURE` path wrapped the decode in
  an `ImageShader`.

  Those framework touches now live in one written-here file — `resolveImage`, `resolveCanvasImage`,
  `prepareOffscreenTarget`, with the framework `resolveBitmap` kept `internal` alongside them. **This
  is a move, not a port: the bodies are the same decode/lookup/copy, so Android's pixels are
  unchanged.** The seam hands back Compose's multiplatform `ImageBitmap` (the type every draw-path
  caller already converted to), so `RcPlayerDrawing.kt` and `RcPlayerPaint.kt` no longer name
  `android.graphics` at all — with framework `Paint` already gone via the text seam, the draw path's
  two shared files are now import-clean. A jvm sibling of *this file alone* (over skiko
  `org.jetbrains.skia.Image`, plus a jvm draw context whose `loadBitmap` decodes) is what the image
  half of the draw path needs off Android.

  `resolveBitmap` stays framework-typed and `internal` because the surfaces that still genuinely need
  a `Bitmap` — the AGSL `BitmapShader` (`RcPlayerShaders.kt`), the reactive
  `rememberRemoteBitmapAsState` (`state/RcPlayerBitmapState.kt`), and the host `RcImageLoader` — are
  all Android-only anyway (the `Drawable`-typed loader and the AGSL seam are separately deferred). The
  seam deliberately does **not** try to make those portable; it removes `android.graphics` from the
  two files that are otherwise ready to compile off Android.

- **`RcImageSource` extracted, and `mapEasing` split out of `RcPlayer.kt`** (`RcImageSource.kt`,
  `RcPlayerEasing.kt`). Two small deltas with the same shape: a neutral declaration was living inside
  an Android-coupled one, so everything that touched it inherited coupling it never used.

  `RcImageSource` is an **empty** supertype of `RcImageLoader`. `GraphContext` only ever *carried* a
  loader — `RcPlayer` sets one, the canvas draw path reads it back — so it now carries the neutral
  type, and the single site that actually loads casts back to `RcImageLoader`. Narrowing rather than
  generalising is the point: the interface has no members, so nothing pretends image decode is
  platform-neutral. `mapEasing` is a six-line `when` from a core easing constant to a Compose one,
  whose only caller is the expression evaluator; leaving it in `RcPlayer.kt` pinned that whole
  evaluator to `SuppressLint`/`PendingIntent`.

  Together these are what let `GraphContext`, `RcPlayerState.kt` and `RcPlayerExpression.kt` compile
  for the jvm target (the JVM cut, removed on 2026-09-25 — see "Removed: the desktop-JVM cut" below).

- **`rememberRemoteBitmapAsState` moved to its own file** (`state/RcPlayerBitmapState.kt`, out of
  `state/RcPlayerState.kt`). Not a behaviour change and not an upstream gap — a refactor in service
  of the CMP split, recorded here because it is the one place the snapshot is no longer file-for-file
  with upstream, so a refresh `diff -r` will flag both files. The function body is verbatim; re-apply
  the move after a refresh rather than treating the diff as a conflict. Rationale under
  "Done: `state/` decoupled" below.

### Behaviour delta: the frame loop, and the dead `autoUpdate` knob

Unlike everything above, this one *is* a fix rather than a build-gap workaround, so it is worth
reporting upstream on its own.

- **The time loop requests frames through `withInfiniteAnimationFrameMillis`, not `withFrameMillis`**
  (`RcPlayer.kt`). `RcPlayer` drives document time from a `LaunchedEffect` whose `while (true)` loop
  only breaks when the document has no animations, no time dependency, no particles and no `wakeIn` —
  i.e. for any animated or time-driven document it never returns. Requested through `withFrameMillis`
  that is indistinguishable from ordinary pending recomposition work, so **the composition never
  reaches idle**: `ComposeTestRule.waitForIdle()` blocks forever, and with it every wait-for-idle
  capture API. That is exactly what Compose's `InfiniteAnimationPolicy` exists to make visible, and
  `withInfiniteAnimationFrameMillis` is how you opt into it. Outside a test no policy is installed
  and the call degrades to `withFrameMillis`, so production timing is unchanged — confirmed by
  re-rendering the 24-document `remote-m3` lane and comparing by md5: **24 identical, 0 differing**.
  `RcIdleProbeTest` pins the property by composing a real document and asserting `waitForIdle()`
  returns at all.

- **`autoUpdate` removed** from both `RcPlayer` overloads and from
  `ExperimentalRemoteDocumentPlayer`. Upstream declares the parameter, defaults it to `true`, and
  forwards it down the wrapper chain — but **no body ever reads it**. It is dead, and worse than
  dead: it reads exactly like the knob that stops the frame loop, so a host trying to render a still
  frame passes `autoUpdate = false`, gets no error, and still hangs. The render harnesses here were
  written around that misunderstanding. With the loop fixed the knob has nothing left to mean, so it
  goes rather than being wired up.

### Not a source delta, but worth knowing about the build

The module carries **no resource table** and leaves `androidResources` at AGP 9's default (`false`
for libraries). It briefly needed it enabled, when the font certificates lived in a vendored
`font_certs.xml`; inlining them as source removed the only resource this module ever had. Do not
re-enable it without a reason — an empty resource table is one of the two things that made the KMP
restructure uncertain.

`testOptions { unitTests { isIncludeAndroidResources = true } }` stays, and is unrelated: that puts
the *dependencies'* merged resources (Compose's own themes) on the unit-test classpath, which the
Robolectric render harness needs to inflate real Compose content.

## Planned: CMP android/jvm (superseded)

**Superseded on 2026-09-25.** The desktop-JVM cut this plan produced was removed; the CMP player
(`:rc-player-compose`) is the JVM player, and this module is to be re-vendored as a direct copy of
upstream AndroidX with patches. What follows is kept as the record of the decoupling that was done.

Goal: a `jvm` target that renders through Compose Desktop's Skia backend, so the `rc-compare` lane
rasterizes `.rc` documents headlessly **without Robolectric** — and, as a side effect, without the
software-canvas ambiguity that made the shader finding hard to attribute (see the tracking issue).

### Measured surface

**32 of the 42 vendored files reference nothing platform-specific** — no `android.*`, no
`androidx.core.*`, no `player.core.platform.*`, no `ui.text.googlefonts`. The remaining 10 (plus the
two written-here Android-only splits, `state/RcPlayerBitmapState.kt` and `RcPlayerShaders.kt`), with
what actually couples them:

| file | coupling |
| --- | --- |
| `RcPlayerPaint.kt` | none via import — runtime shaders moved to `RcPlayerShaders.kt`, framework `Paint` gone (text ported), and the `TEXTURE` bitmap now comes back from the image seam as an `ImageBitmap`; it stays in androidMain only through the in-package `resolveImage` it calls (an Android-only seam file today) |
| `RcPlayerShaders.kt` | `RuntimeShader`, `BitmapShader`, `Matrix`, `Build` (the AGSL seam, split out of `RcPlayerPaint.kt`) |
| `RcPlayerDrawing.kt` | none via import — `Bitmap`/`drawable`/the native canvas are gone (text + image seams); it stays in androidMain only through the in-package `resolveImage`/`resolveCanvasImage`/`prepareOffscreenTarget` it calls |
| `RcPlayer.kt` | `SuppressLint`, `PendingIntent`, `AndroidRemoteContext` |
| `EmbeddedPlayerTypefaceResolver.kt` | `Typeface`, `Handler`, `Looper`, `Log`, `FontRequest`, `FontsContractCompat`, `AndroidRemoteContext`, `TypefaceResolver`, `FontInstance` |
| `RcImageLoader.kt` | `Bitmap`, `drawable`, `content.res` |
| `state/RcPlayerBitmapState.kt` | `Bitmap` (split out of `RcPlayerState.kt` — see below) |
| `GraphContext.kt` | extends `AndroidRemoteContext` |
| `RcPlayerParticles.kt` | `AndroidPaintContext` |
| `RcPlayerTextLayout.kt` | `googlefonts.Font`, `googlefonts.GoogleFont` |
| `DrawablePainter.kt` | `drawable.Drawable` — Android-only by definition, no jvm counterpart needed |

The parenthesised "was also" entries are coupling this branch has already moved out, not coupling
removed: it now lives in `RcPlayerTextPlatform.kt`, which is written here rather than vendored and so
is not one of the 42 — alongside its neutral vocabulary in `RcPlayerTextPaintSpec.kt`. Concentrating
it there is the point: it is the one file a jvm sibling had to replace (the JVM cut carried one until
its removal), and the two vendored files above never needed one.

#### 32/10 is a coupling *surface*, not a partition

That table counts files by what they **import**. It does not describe a source-set split, and reading
it as one is the mistake to avoid: a source set can only hold a file whose *callees* are also
visible to it, and the 32 call into the 10 constantly. Following the references transitively — mark
the 10 as `androidMain`, then repeatedly pull in anything referencing a declaration that lives
there — the partition collapses to roughly **five** files in `jvmCommonMain`. The chains that do it,
each verifiable by grep:

| declaration | lives in (Android-coupled) | pulls in |
| --- | --- | --- |
| `rememberRemote*AsState` (14 helpers) | `state/RcPlayerState.kt` | 19 files outside `state/` |
| `RcPlayerChildren` | `RcPlayer.kt` | 5 of the 8 `layout/` files |
| `RcPlayerComponent` | `RcPlayer.kt` | `layout/RcPlayerStateLayout.kt` |
| `executeOperations` | `RcPlayerDrawing.kt` | `RcPlayerCanvas.kt`, `RcPlayerModifiers.kt` |
| ~~`GraphContext` (extends `AndroidRemoteContext`)~~ — **resolved**, see below | `GraphContext.kt` | the state + expression path |
| `RcImageLoader` (`Drawable`-typed) | `RcImageLoader.kt` | `layout/RcPlayerImageLayout.kt`, `RcPlayerCustom.kt`, **`GraphContext.kt`** |

So the unit of work is a **declaration**, not a file. Some of those splits are nearly free — the
`rememberRemote*AsState` row was the largest single blocker and its whole Android coupling was *one*
function (see the note below). Others are the `expect`/`actual` seams the sequencing already
names — `GraphContext`/`RemoteContext`, image decode, the `Drawable`-typed loader — which means
the original "step 1 needs no `expect`/`actual`" is only true for a `jvmCommonMain` of about five
files. Anything larger pulls step 2 forward.

#### A single android target cannot enforce the split

Worth knowing before treating step 1 as done: with only the android target configured,
`jvmCommonMain` is compiled *as part of the android compilation* and has the Android SDK on its
classpath. Nothing rejects an `android.*` import that lands in "common" code — the separation is
convention, not a constraint, until a second target exists to contradict it. The same is already
true *today*, before any restructure: this is a plain android library, so a file decoupled by hand
can be re-coupled by the next edit with nothing to notice.

`RcSemanticsExtractionTest`'s neighbour `PlatformNeutralSourcesTest` is that missing constraint. It
keeps two lists, because moving a file needs two things and they are worth not conflating:

- **`IMPORT_CLEAN`** — the file imports no Android platform API. This is what a declaration split
  buys, and the list exists so a later edit can't quietly undo one.
- **`READY_FOR_JVM_COMMON`** — additionally, the file imports nothing that stays in `androidMain`,
  which `jvmCommonMain` would not be able to see. Only these can actually move.

Import-freedom alone does **not** make a file movable, and `state/RcPlayerState.kt` is the case in
point: it is import-clean after the split below, but its helpers read `LocalGraphContext`, whose type
extends `AndroidRemoteContext`. It graduates when the `GraphContext` chain in the table above is
split — which is the same thing that unblocks the rest of the state path.

The second check works on imports, so it catches *cross-package* references only: the player splits
into `embedded`, `embedded.layout`, `embedded.modifier` and `embedded.state`, so a file in a
sub-package must import what it uses from the root package. References within the root package need
no import and this test cannot see them — the chain table above is the record for those. The whole
test retires once a `jvm` target enforces both halves by compiling.

#### Done: `state/` decoupled

`rememberRemoteBitmapAsState` (`State<Bitmap?>`) coupled the whole of `state/RcPlayerState.kt`, and
through it 19 dependent files, to `android.graphics.Bitmap`. It now lives in
`state/RcPlayerBitmapState.kt`; the body is verbatim and had no call sites in the vendored subset, so
this is a file move. `PlatformNeutralSourcesTest` now holds `RcPlayerState.kt` import-clean and pins
`CoreDataAccessors.kt` / `CoreDataModel.kt` / `SnapshotRemoteComposeState.kt` as genuinely movable.

This clears the *import* half of the largest chain, not the whole chain: the fourteen helpers still
read `LocalGraphContext`, so `state/` moves when `GraphContext` is split. Verified by compile, not by
a render — see the sequencing note on the staged catalog.

#### Removed: the desktop-JVM cut

Five subsections used to follow here, recording how a separate `kotlin("jvm")` module compiled the
neutral subset of this module's sources against Compose Desktop: the skiko half of the canvas text
seam, a desktop draw context that decoded bitmaps, the whole draw path rendering a document to PNG,
and the figma-svg export running over it. That JVM cut was **removed on 2026-09-25**. The CMP player
(`rc-player/compose`, `:rc-player-compose`) is this repository's JVM player, and this module will be
re-vendored as a direct copy of upstream AndroidX plus patches, so a JVM-specific split of it no
longer has a purpose. The removed text is in git history before that date.

### The source-set shape is `jvmCommon`, not `common`

`remote-core` — the document and operation model the player reads throughout — is a plain
`java-library` upstream, **not** a multiplatform artifact. So the shared code is not
platform-agnostic; it is *JVM*-common. The layout has to be an intermediate source set both targets
depend on:

```
commonMain        (empty, or Compose-only helpers)
└── jvmCommonMain  ← the 32 clean files + the `remote-core` dependency
    ├── androidMain ← the 10 above, as today
    └── jvmMain     ← their jvm actuals
```

Putting `remote-core` in `commonMain` would not resolve. This is the single most important structural
constraint and the easiest one to get wrong.

### What a `JvmRemoteContext` actually costs

Less than the line counts suggest. `RemoteContext` (remote-core, JVM) declares **42 abstract
members**, and `AndroidRemoteContext` implements them in 669 lines — but the contract is
overwhelmingly a *variable/state store*, which is platform-neutral: `loadFloat`/`getFloat`,
`loadColor`/`getColor`, `loadText`/`getText`, `loadInteger`, `setNamed*Override` /
`clearNamed*Override`, `addCollection`, `putDataMap`/`getDataMap`, `putObject`/`getObject`,
`listensTo`, `updateOps`, `loadAnimatedFloat`, `loadPathData`/`getPathData` (plain float arrays).

Genuinely platform-bound, and short: `loadBitmap` (decode), `hapticEffect` (no-op on jvm),
`runAction`/`runNamedAction`/`addClickArea` (host callbacks), `loadShader`/`getShader` (storage of a
core `ShaderData`).

`AndroidPaintContext` is 1510 lines but is reached **only** by `RcPlayerParticles.kt` — the embedded
player draws through Compose's `DrawScope`, not the core's paint pipeline. Particles can stay
Android-only in a first cut rather than forcing a Skia `PaintContext` port.

### Known parity limits before starting

- **Text.** The canvas text ops measure and draw through a framework `android.graphics.Paint`. That
  is behind one seam (`RcPlayerTextPlatform.kt`, four functions) and the skiko half is **written**
  (in the JVM cut — see "Removed: the desktop-JVM cut"), so what is left
  here is not a port but the parity limit itself: metrics will not be bit-identical across targets,
  because Skia's shaping is reachable from both but Android's font stack is not. The seam makes that a
  *measurable* difference — both sides answer the same four questions about the same
  `TextPaintSpec` — rather than a diffuse one.
- **Shaders.** AGSL has no JVM equivalent; desktop Compose exposes SkSL `RuntimeEffect`, which is
  close but not the same language or the same uniform plumbing. Shader parity across targets will not
  be exact — and note the embedded player's shader path already diverges from the View player on
  *Android* (89% on `ShaderGradientSticker`), so that wants fixing before it is used as a jvm
  baseline.
- **Downloadable fonts — closed.** `google:`-prefixed fonts go through
  `FontRequest`/`FontsContractCompat`, which is Android-only, so the jvm side used to substitute a
  local face and render such a document in the wrong face. There is no font *provider* off Android,
  but there is a *downloader*: `GoogleFontTypefaceResolver` (in the since-removed JVM cut) resolves a `google:`
  family through `:data-fonts-google` — the same `(family, weight, italic) -> File` machine-local
  cache the Robolectric downloadable-font shadow and the figma-svg embed path use — and hands the
  file to both jvm text seams (a Compose `FontFamily` for the layout ops, a skiko `Typeface` for the
  canvas ops). So the branded face this lane draws is the same file every other lane draws. The
  fallback chain is unchanged for everything else: no cache directory configured (`-Dcomposeai.fonts
  .cacheDir`, which `serve`'s cmp-jvm subprocess sets), an offline miss, a failed fetch, or a
  `device:` family still substitutes a local face rather than failing.
- **Shaping on a path.** Three of the four seam functions shape through HarfBuzz with fallback; the
  text-on-path one resolves glyphs per character instead, so cross-character kerning and joining are
  not applied along a path. Blocked on a skiko crash rather than on design — details were in the
  removed seam section (git history before 2026-09-25).

### Sequencing

Revised after the measurement above: the original step 1 ("move the 32/10 split into
`jvmCommonMain`/`androidMain`, no `expect`/`actual` needed") is not a pure source-set move, and as a
single-target milestone it cannot be verified. It splits into 1a/1b.

1. **1a — declaration splits, still a plain android library.** Peel the platform-neutral
   declarations out of the coupled files so a `jvmCommonMain` worth having exists *before* the build
   is restructured. Each split is behaviour-preserving and verified by a compile, so they land
   independently and bisect cleanly. Progress so far, in the order the table above implies:

   - `state/`'s Android *import* — **done**, by splitting out `rememberRemoteBitmapAsState`.
   - `GraphContext`'s `AndroidRemoteContext` base — **done**, via `StoreBackedRemoteContext`. This
     was step 2's `RemoteContext` seam, pulled forward because it gated most of 1a; it turned out to
     need no `expect`/`actual` at all, since `RemoteContext` is itself platform-neutral (42 abstract
     members, not one naming an Android type — even `loadBitmap` takes a `byte[]`).
   - `RcImageLoader` pinning `GraphContext` — **done**, via the empty `RcImageSource` supertype.
     `GraphContext` only ever *carried* a loader, so it now carries the neutral type and the one
     site that loads casts back. Narrowing beat generalising here: the interface has no members, so
     nothing pretends image decode is platform-neutral.
   - `mapEasing` out of `RcPlayer.kt` — **done** (`RcPlayerEasing.kt`), which is what let
     `RcPlayerExpression.kt` and `RcPlayerState.kt` compile for the jvm target.
   - The canvas text ops' framework `Paint` — **done** (`RcPlayerTextPlatform.kt`), which is what
     removed `Paint` from `RcPlayerPaint.kt` and the native canvas from `RcPlayerDrawing.kt`. A
     seam, not a port: Android's text output is unchanged. **Both halves now exist** — the skiko
     sibling is written and tested against a real raster, so step 4 below is spent early and text is
     no longer on the critical path.
   - The draw path's framework `Bitmap` — **done** (`RcPlayerImagePlatform.kt`), which is what
     removed `android.graphics` from `RcPlayerDrawing.kt` and `RcPlayerPaint.kt`. A seam, not a port:
     Android's pixels are unchanged. The *Android* half is written; the skiko sibling waits on a jvm
     draw context (whose `loadBitmap` decodes via `org.jetbrains.skia.Image`), which is step 3's
     `JvmRemoteContext` — so unlike the text seam, the image seam's jvm half is paced by the context,
     not ready ahead of it.
   - The draw dispatcher out of `RcPlayer.kt` — **done** (`RcPlayerDispatch.kt`). The four
     component-tree composables (`RcPlayerRawDocument`, `RcPlayerRootLayoutComponent`,
     `RcPlayerComponent`, `RcPlayerChildren`) touch only neutral types — the composition locals, the
     seamed `executeOperations`, and the per-layout composables — so moving them out keeps
     `RcPlayer.kt`'s `SuppressLint`/`PendingIntent`/`AndroidRemoteContext` coupling (document setup +
     interactive dispatch, neither on the pixel path) off the draw/layout path. A move, not a change:
     the bodies are verbatim. `RcPlayerDispatch.kt` is import-clean but not yet movable — its `when`
     still reaches `RcPlayerText` (googlefonts) and `RcPlayerImageLayout` (the `Drawable` loader).
   - **Next: add the import-clean set to the jvm source list.** `RcPlayerDrawing.kt` /
     `RcPlayerPaint.kt` / `RcPlayerDispatch.kt` / `RcPlayerCanvas.kt` / `RcPlayerModifiers.kt` /
     `RcPlayerDensity.kt` and the neutral `layout/**` + `modifier/**`, once their remaining
     androidMain callees (the `Drawable` image loader, the googlefonts text layout, and a jvm draw
     `RemoteContext` whose `loadBitmap` decodes) are split or seamed. This is where the deferrals
     below start to matter.

   **Import-clean and movable are different things**, and 1a keeps tripping over the difference —
   `GraphContext` imports nothing Android yet still cannot move, because of an ordinary in-package
   reference. `PlatformNeutralSourcesTest` tracks the two as separate lists for exactly this reason.

   **Deferred deliberately** (niche surfaces the jvm target does not need in a first cut, each
   isolated enough to postpone): **particles** — `RcPlayerParticles.kt` is the only
   `AndroidPaintContext` user, so deferring it avoids a Skia `PaintContext` port entirely, at the
   cost of a small seam where `RcPlayerPaint.kt`/`RcPlayerDrawing.kt` dispatch into it; **AGSL
   shaders** (now behind their own seam file `RcPlayerShaders.kt` — see the shader-seam delta under
   "Local modifications" — so the shared paint decoder no longer holds any runtime-shader coupling;
   the desktop `actual` is what stays deferred, issue #2954); and **downloadable fonts**
   (`RcPlayerTextLayout.kt`). What is *not* deferrable is framework `Paint` and bitmaps — those are
   the draw path itself; `Paint` is now seamed, bitmaps are not.
2. **1b — restructure to KMP, android target only,** moving the (now much larger) clean set into
   `jvmCommonMain`. Ship it with the forbidden-import guard from above, since the target itself
   enforces nothing. One build-level unknown left: whether `com.android.kotlin.multiplatform.library`
   under AGP 9 supports Robolectric unit tests with the dependencies' merged resources, which
   `RcEmbeddedRenderHarness` needs to inflate Compose content. The other unknown — resource
   *processing* for the module's own table — is **gone**: the font certificates are source constants
   now, the module owns no resources, and `androidResources` is back at its default.
3. Add the `jvm` target and the `expect`/`actual` seams, starting with `RemoteContext`
   (`GraphContext`'s `AndroidRemoteContext` base) and image decode. This is where the remaining
   chains in the table are actually paid for, not step 1. **Partly done:** the draw `RemoteContext`
   and its bitmap decode are in place (in the JVM cut — see "Removed: the desktop-JVM cut"). What remains here is the jvm `resolveImage`/`resolveCanvasImage` seam siblings that read
   that decode back, and the `Drawable`-typed host loader.
4. ~~Port text off framework `Paint`.~~ — **done ahead of order**, both halves. Pulled forward
   because it was the step with an actual unknown in it (does Skia answer the same four questions?),
   and the answer turned out to be yes; leaving it last would have deferred the only risk in the plan
   to the end. Its callers still wait on bitmaps.
5. Shaders last, after the Android-side shader divergence is understood. The **seam** is already in
   place — `RcPlayerShaders.kt` isolates the two AGSL functions behind a portable
   `androidx.compose.ui.graphics.Shader` signature (issue #2954) — so what remains at this step is
   the desktop skiko `RuntimeEffect` *body*, not the extraction.

**On the success criterion.** "The existing render output does not change" is the right test for
every step here, but it needs a staged catalog: `RcEmbeddedRenderHarness` skips unless
`rc-compare.mjs --stage-embedded` has written `<id>.rc` + `manifest.json`, and no `.rc` fixtures are
committed. So a compile-only check is what a working tree gives you; the render comparison is a CI /
full-capture step, and a step-1a split should not be called verified on a compile alone.

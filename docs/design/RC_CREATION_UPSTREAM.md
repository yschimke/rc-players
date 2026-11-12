# The creation fork and AndroidX: divergence, realignment, and feedback for upstream

**Status: steps 0 to 4 of the realignment are done, the canvas differences step 4 kept are settled, the fork is in rc-players' own package, and the fork writes where and for whom AndroidX's writer does (declarations in place, and a capture `Profile`: part of steps 5 and 6); the rest of steps 5 and 6 is planned.** This note records
three things about `third_party/remote-creation-compose` and `third_party/remote-creation-writer`,
the Compose Multiplatform fork of AndroidX Remote Compose creation:

1. what in the fork has to differ from AndroidX today, and exactly what forces it;
2. the plan for removing everything else that differs, and where that plan stands;
3. what we would ask AndroidX to change, written so that it can be read without knowing this
   repository.

Everything here is measured against androidx-main `21a1a95e4` (2026-11-10;
`REMOTECOMPOSE = 1.0.0-alpha24`, `COMPOSE_REMOTE_FOUNDATION = 1.0.0-alpha07`). Upstream paths are
relative to `compose/remote/` in the androidx repository. Three prefixes are abbreviated:

| Short | Path |
| --- | --- |
| `expression/` | `remote-creation-expression/src/main/java/androidx/compose/remote/creation/compose/` |
| `common/` | `remote-creation-compose/src/jvmAndAndroidMain/kotlin/androidx/compose/remote/creation/compose/` |
| `android/` | `remote-creation-compose/src/androidMain/kotlin/androidx/compose/remote/creation/compose/` |

The fork's history, refresh by refresh, is in
[PROVENANCE.md](../../third_party/remote-creation-compose/PROVENANCE.md). Why the fork exists and
why it is in rc-players' package and not AndroidX's is in
[RC_CREATION_BRIDGE.md](RC_CREATION_BRIDGE.md).

The fork declares AndroidX's Kotlin in its own packages, and this note names a file by AndroidX's
path when it is about upstream and by the fork's when it is about the fork:

| AndroidX | The fork | Published as |
| --- | --- | --- |
| `androidx.compose.remote.creation.compose` in `remote-creation-expression` | `ee.schimke.composeai.rccreation.compose` | `rc-creation-expression` |
| `androidx.compose.remote.creation.compose` in `remote-creation-compose` | `ee.schimke.composeai.rccreation.compose` | `rc-creation-compose` |
| `androidx.compose.remote.foundation` | `ee.schimke.composeai.rccreation.foundation` | `rc-creation-foundation` |
| `remote-core`, `remote-creation-core`, `remote-creation` (Java, not carried) | `ee.schimke.composeai.rccreation.common`, the Kotlin writer | `rc-creation-writer` |

## Why there is a fork

rc-players plays Remote Compose documents on Android, the JVM, iOS, macOS and in the browser
(Wasm). An editor, a preview pane or an app that renders its own remote UI has to *create* documents
on those targets too, from the same `@RemoteComposable` source that runs against AndroidX on a
device. AndroidX creation cannot be compiled for iOS, macOS or Wasm, for the reasons in the next
section, so the fork carries AndroidX's Kotlin and replaces what that Kotlin stands on.

The fork's aim is to be AndroidX's source with a short, marked list of differences, and to shrink
that list until the fork is unnecessary.

## Where AndroidX is today

| Module | Targets | Main sources |
| --- | --- | --- |
| `remote-core` | JVM | 302 Java files |
| `remote-creation-core` | JVM | 63 Java files, 17 Kotlin files that call them |
| `remote-creation` | Android, JVM | `commonMain`: one annotation file. `jvmAndAndroidMain`: `RemotePath`, `RemotePathBase` |
| `remote-creation-expression` | JVM (`java-library` + `kotlin.jvm`) | 31 Kotlin files, 11,774 lines |
| `remote-creation-compose` | Android, JVM | `jvmAndAndroidMain`: 143 Kotlin files, 24,455 lines. `androidMain`: 32 files, 5,542 lines. No `commonMain` |
| `foundation/foundation` | Android (`com.android.library`) | 4 Kotlin files, 319 lines |

`remote-creation-compose` became multiplatform in `1be9a9fed`, and the state types moved to
`remote-creation-expression` in `91c532558`. "Multiplatform" here means two targets, Android and
the JVM, sharing `jvmAndAndroidMain`. Nothing has an iOS, macOS, JS or Wasm target, and nothing
below `remote-creation-compose` has common code.

Much of the Kotlin is already platform-neutral. Counting a file as blocked if it imports a Java
module (`androidx.compose.remote.core.*`, or `androidx.compose.remote.creation.*` outside
`.compose`), imports `java.*` or `android.*`, or uses a JVM-only construct:

| Source set | Files | No blocker | Blocked only by imports of the Java modules | Has a JVM or Android construct of its own |
| --- | --- | --- | --- | --- |
| `remote-creation-expression` | 31 | 9 (1,499 lines) | 19 (8,114 lines) | 3 |
| `remote-creation-compose` `jvmAndAndroidMain` | 143 | 56 (7,776 lines) | 76 (11,094 lines) | 11 |
| `foundation/foundation` | 4 | 4 (319 lines) | 0 | 0 |

No file in `jvmAndAndroidMain` imports `androidx.compose.ui.*`: the Compose UI decoupling is
complete there.

## Divergence that is necessary today

Each row is something the fork cannot take from AndroidX as it is, with the upstream fact that
forces it.

| The fork has | Forced by |
| --- | --- |
| A Kotlin write side, `third_party/remote-creation-writer` (11 files, 3,938 lines): the `RemoteWriter` interface, which the carried Kotlin names `RemoteComposeWriter` through a typealias, and `RemoteDocumentWriter`, which encodes the wire format | The only writer is `remote-creation-core`'s `RemoteComposeWriter.java` on top of `remote-core`'s `RemoteComposeBuffer.java`, and the Kotlin is typed against the class: `RemoteDocumentScope.document: RemoteComposeWriter` (`expression/capture/RemoteComposeCreationState.kt:44`). Java does not compile for Wasm or native. |
| Constants and pure helpers re-declared in Kotlin under AndroidX's class names (`Utils`, `RemoteContext`, `AnimatedFloatExpression`, `TextFromFloat`, `CoreText`, `StringUtils`, `CubicEasing`, `MonotonicSpline`, `PaintBundle`, `NamedVariable`, `ComponentValue`, `DimensionModifierOperation.Type`, `AnimationSpec.ANIMATION`, `Custom.CustomProperty`, …) | They are static members of `remote-core`'s Java player classes. 21 of the 31 expression files and 53 of the 143 `jvmAndAndroidMain` files import `remote-core`, most of them for constants. |
| A few lines changed in carried files, each under a `KMP (rc-players)` comment: no `WeakReference` in `RemoteConstantCacheKey`, a stored hash in place of `System.identityHashCode`, `value::class.simpleName` in place of `Enum.declaringJavaClass.name`, `copyOf`/`copyInto` in place of `clone()`/`System.arraycopy`, a surrogate-pair loop in place of `String.codePoints()`, `kotlin.concurrent.atomics` in place of `java.util.concurrent.atomic` | JVM-only constructs in the Kotlin itself; the full list is under items 3, 5 and 6 of the feedback below. |
| Modifiers, actions and layouts handed to the writer as Kotlin values (`RemoteModifierOperation`, `RemoteActionData`, `WriterOp`), and a `ComponentValue` that names its component's own id (written in the component, as upstream's) | The Kotlin builds `remote-creation-core`'s Java objects (`RecordingModifier.Element` in 26 files, `actions.Action` in 5) and writes each component's values inside its body through `document.buffer`; items 8 and 11 below. |
| `Profile`, `RcPlatformProfiles`, `RcProfiles`, `Header`, `HTag`, `CreationDisplayInfo` and `RemoteComposeWriterFactory` declared in Kotlin in the writer module, without `RcPlatformServices`; `RemoteComposeCaptureState`'s constructors each take an image encoder too; `Profile.android.kt` carried as common `capture/Profile.kt` | Upstream's are Java in `remote-core`, `remote-creation-core` and `remote-creation`'s `androidMain` (`profile/Profile.java`, `RcPlatformProfiles.java`), the profile carries `RcPlatformServices`, and `Profile.android.kt` builds one around `AndroidxRcPlatformServices` and `RemoteComposeWriterAndroid`. |
| Its own capture entry point, `captureCommonRemoteDocument`, that takes no `android.content.Context` | Upstream's non-Android capture functions are `internal` (`common/capture/CaptureRemoteDocument.kt:63,84,126,151`); the public ones are in `android/capture/CaptureRemoteDocument.android.kt` and take a `Context`. |
| The Compose UI conversions (`RemoteColor(Color)`, `Dp.asRdp()`, `RemoteTextStyle.fromTextStyle`, `ImageVector.toRemoteImageVector()`, …) compiled for every target | Upstream has them only in `androidMain`, so they exist only in the Android artifact. |
| `Font.variationSettings` through an `expect`/`actual` with reflection on Android | `android/text/RemoteTextStyle.android.kt:103-104` reads `AndroidFont.variationSettings` and `ResourceFont.variationSettings`; neither type is in Compose Multiplatform's common `ui-text`. |
| A pure-Kotlin `RemotePath` | `remote-creation`'s `RemotePath` is an `expect class` whose API names `expect class Path`, `RectF` and `MatrixTransform`, with actuals only for `android.graphics` and `java.awt.geom`, and `RemotePathBase` wraps `remote-core`'s Java class. |
| 17 `androidMain` files not carried: `PendingIntentAction`, `LambdaAction`, `RemoteColor.android.kt`, `CompatAndroidRemotePaint`, `RemoteFormatting.android.kt`, the animated-vector resource parser and its `Context` painter, `Profile.android.kt`, the capture and widget classes | They use `android.*`, `androidx.lifecycle`, `androidx.savedstate`, ICU or `XmlPullParser`. |
| `util/DocumentStats.kt` and `capture/CustomComponentFactory.kt` not carried | They are over `remote-core`'s Java classes for a document that has been read (`CoreDocument`, `operations.layout.Component`). |
| `capture/RemoteLog.kt` not carried | It logs through the capture state's `Profile`'s `RcPlatformServices`. |
| `RemotePath(pathData: String)` ported from `remote-core`'s `RemotePathBase`, and `addPathString` as an extension over it | `RemoteComposeWriter.addPathString` asks `RcPlatformServices.parsePath` (`remote-creation-core/…/RemoteComposeWriter.java:2253`), and `vector/RemoteAnimatedVector.kt` draws with it. |
| Its own package, `ee.schimke.composeai.rccreation`, and its own coordinates | AndroidX's artifacts are multiplatform for the JVM and Android in `androidx.compose.remote.creation.compose`. A fork that kept that package could not be on a classpath with them, on the two targets where it is used most; see [RC_CREATION_BRIDGE.md](RC_CREATION_BRIDGE.md). This is the one difference every carried file has, and `diff-upstream.sh` removes it before it compares. |

## Divergence that is not necessary

The rest of what differs is history: the fork began as a hand-made port with its own file names
(gone since step 1), its own visibility and creation-state types (gone since step 2), one module
where upstream now has three (gone since step 3), its own canvas recording (gone since step 4), and
its own capture loop. None of that is forced by anything upstream, and
all of it makes every refresh from androidx-main harder to do and to review. The plan below removes
it in an order that keeps each step small and keeps the bytes the fork writes unchanged until the
last step.

## Realignment plan

Divergence is measured by
[`third_party/remote-creation-compose/scripts/diff-upstream.sh`](../../third_party/remote-creation-compose/scripts/diff-upstream.sh),
which compares the fork with an androidx checkout file by file after normalising import order,
the names in `scripts/upstream-fqn.tsv` (upstream's Java classes, which the fork's writer declares
in another package) and the packages in `scripts/upstream-packages.tsv`:

| Upstream's package | The fork's |
| --- | --- |
| `androidx.compose.remote.creation.compose` | `ee.schimke.composeai.rccreation.compose` |
| `androidx.compose.remote.foundation` | `ee.schimke.composeai.rccreation.foundation` |

The script applies that map to upstream's side, to a file's text (package statements, imports,
qualified names, KDoc links, `@sample` references) and to its path, so a carried file is compared
with upstream's text as it reads in the fork's package, and the relocation itself adds nothing to
the counts below. Imports are sorted where their AndroidX names sort, so the block keeps upstream's
order on both sides.

| | Upstream files | Identical | Differing | Not carried | Carried under another name, split or merged | Fork-only files | Differing hunks | Marked | Import-only | Unmarked |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Before step 1 | 210 | 58 | 115 | 37 | 23 | 19 | 590 | 15 | | 575 |
| After step 1 | 210 | 60 | 114 | 36 | 0 | 19 | 562 | 14 | | 548 |
| After step 1, as the script counts now | 210 | 66 | 108 | 36 | 0 | 19 | 520 | 14 | 74 | 432 |
| After step 2 | 210 | 95 | 79 | 36 | 0 | 17 | 226 | 120 | 54 | 52 |
| After step 4 | 214 | 97 | 86 | 31 | 0 | 17 | 215 | 147 | 58 | 10 |
| After the canvas port fixes | 214 | 97 | 86 | 31 | 0 | 17 | 208 | 141 | 57 | 10 |
| After the upstream bug patches | 214 | 96 | 87 | 31 | 0 | 17 | 232 | 163 | 59 | 10 |
| After the relocation to `ee.schimke.composeai.rccreation` | 214 | 96 | 87 | 31 | 0 | 17 | 232 | 163 | 59 | 10 |
| After step 3 | 214 | 96 | 87 | 31 | 0 | 18 | 233 | 166 | 57 | 10 |

A marked hunk carries `LOCAL PATCH (rc-players)` (a fork addition or a deliberate difference) or
`KMP (rc-players)` (a substitution forced by the non-JVM targets or by the Kotlin writer), with its
reason. An import-only hunk follows from its file's body. In step 2 the script also began to drop a
comma that ends a line, because this repository's ktfmt adds one where AndroidX's does not; the
third row is step 1's tree counted that way. Step 4 added the four test files of
`remote-creation-expression` to what the script compares (214 files; without them the last row is
210 files, 97 identical, 82 differing). The canvas port fixes that followed it are the one change
meant to alter what is written: the hunks step 4 kept so that no document changed (`usePaint`, the
paint around a block that may not run, pivots, `String` text, the loop index) became upstream's
lines, and what replaced the rest is marked with its reason (PROVENANCE.md, "Canvas port fixes").
The relocation changes no count: it is a rename the script undoes. Step 3 adds three marked hunks
(`lastComponentId` on `RemoteDocumentScope`, and its two overrides in `remote-creation-compose`),
removes two import-only ones (`RemoteFloatContext` no longer names the creation state, and
`RemoteStateVisitor` no longer imports `ImageBitmap`), and adds one fork-only file
(`state/RemoteTextUnitDp.kt`).
Of the 10 unmarked hunks left, 2 are an upstream change to
`RemoteCornerBasedShape` that alters the documents written and waits for a refresh, and 8 are the
two formatters disagreeing about where to break a line. The 31 files not carried are the capture
loop (`capture/headless/` and `CaptureUpdateThrottle`, step 5), `RemoteLog`, the two files over the
Java reader, and 17 Android-only files.

| Step | What | State |
| --- | --- | --- |
| 0 | Make divergence measurable (`diff-upstream.sh` and its data files). Declare the `androidx.compose.remote:remote-creation-expression` capability, so the fork and that AndroidX artifact cannot both be resolved on Android (withdrawn in step 3a, when the fork left AndroidX's package). | Done |
| 1 | Renames and moves only. Every carried file has upstream's name and sits at upstream's path under `third_party/remote-creation-compose/upstream/<upstream module>/src/<source set>/…` (since step 3, `third_party/remote-creation-compose/<expression, compose or foundation>/upstream/…`, with the fork's package directories); declarations that upstream keeps in another file moved there; `@file:JvmName`s match upstream's. No behaviour change: the documents written are byte-identical. | Done |
| 2 | Take upstream's visibility and scope types: the restricted-public cache keys and helpers, `RemoteDocumentScope` with `document` and `resolveComponentToken()`, the `RemoteComposeCreationState` / `RemoteComposeCaptureState` split, and `RemoteComposeWriter`'s method names on the fork's writer interface. The 31 files of `remote-creation-expression` are upstream's text again: 21 identical, 10 with marked hunks only (and one formatter hunk). Every other hunk that has to stay is marked with its reason. No document changes. | Done |
| 3 | Split the one Gradle module into three that mirror upstream, each published under its own coordinates: `rc-creation-expression`, `rc-creation-compose`, `rc-creation-foundation`, over `rc-creation-writer`, with upstream's dependencies between them (`foundation` → `compose` → `expression` → the writer). The two marked hunks in the expression files that reached into the compose side went first. `RemoteFloatContext` asks its scope for the last component (`RemoteDocumentScope.lastComponentId`, a marked member beside `document`, where upstream reads `document.buffer.lastComponentId`); the creation state answers and `RemoteStateScope` passes the question to its parent. `RemoteStateVisitor.resolveConstantState` is upstream's text again: a constant key keeps the state of a value that cannot be rebuilt from the value alone (an image), where upstream keeps a weak reference to every state, so the expression module no longer needs Compose UI's `ImageBitmap`. Two fork-only pieces cross the new boundary as restricted API: `MeshExpressionLowering`, and `RemoteTextUnit.toDp`, which `RemoteText`'s DP font size patch calls where it used the expression module's internal `RemoteFontScaleConverter`. No document changes. | Done |
| 3a | Relocate the fork out of AndroidX's packages, to `ee.schimke.composeai.rccreation`, and stop declaring AndroidX's Gradle capabilities. Not a step towards upstream's text but the condition for being used beside it; `diff-upstream.sh` learned the package map, so every count is what it was. `:rc-creation-coexistence-check` compiles one source set against the fork and AndroidX's `remote-creation-compose` on Android and on the JVM and checks that the two share no class. No document changes. | Done |
| 4 | Carry what is no longer Android-only upstream: `RemoteTextMeasure.kt`, `RemoteProfileCapabilities.kt`, `Hoist`, `RemoteAnimatedVector.kt` and its painter, `CaptureTracer.kt`, the `RemoteCanvas` API the fork lacked (`skew`, `saveCount`/`restoreToCount`, `custom`, the bitmap-font text runs, `startStateLayout`, the Loom patterns and `forEach`), with the writer operations under them, and the four JVM tests of `remote-creation-expression`. The fork's `RecordingCanvas` is folded into `RemoteCanvas`, as upstream did, so `RemoteCanvas.kt` and `CanvasOperationBuffer.kt` are upstream's text with marked hunks. No document that could be captured before changes; where the port's canvas wrote something other than upstream's text does, the port's bytes are kept under a marked hunk and listed in PROVENANCE.md ("Known port issues"). `CaptureUpdateThrottle.kt` moved to step 5 (it is built on `headless/UpdateRateLimiter`), and `RemoteLog.kt` to step 6 (it logs through the `Profile`). | Done |
| 5a | Write as upstream writes: layout nodes write as they render, a canvas flushes before it ends inside a `withCanvasOperationsScope`, a component's values are written in the component (upstream's token emission), and the writer writes every declaration where it is made (`beginGlobal`/`endGlobal` for what has to be ahead of the root). This is the one step since the canvas port fixes that changes the documents written, and it is what makes them play in AndroidX's players as AndroidX's own do. `WriterOp` remains, as the value a node hands the Kotlin writer. PROVENANCE.md, "Declarations in place, and a capture profile". | Done |
| 5 | Carry upstream's capture loop, `capture/headless/`, `capture/CaptureUpdateThrottle.kt` and `capture/CaptureRemoteDocument.kt`, with the JVM-only constructs replaced, in place of the fork's own. | Planned |
| 6a | The capture profile under upstream's names: `Profile`, `RcPlatformProfiles`, `RcProfiles`, `Header`, `HTag` in the writer module, honoured by `RemoteDocumentWriter` as `RemoteComposeWriter` honours it (refusal, the `TextLayout` backstop, the API level's forms, the header), `profile` on the capture state and entry points, and `currentProfileSupports*`, `PaintTracker` and `RemoteVector` reading it as upstream's text does. | Done |
| 6 | The rest of the writer under upstream's names, so that the modifier, action and canvas files become upstream's text. Its design depends on AndroidX's answer to items 1, 2 and 8 below. | Planned, not designed |

After step 6 the only differences left should be the replacements for the JVM-only constructs
listed in items 3, 5 and 6, and the additions listed under "Candidate contributions".

### A refresh, with the fork in its own package

A refresh from androidx-main is what it was, with one mechanical step added:

1. Copy upstream's changed file to its place: the module that mirrors upstream's (`expression/`,
   `compose/` or `foundation/` under `third_party/remote-creation-compose/`), `upstream/`, upstream's
   source root, then the package path with `ee/schimke/composeai/rccreation/…` in place of
   `androidx/compose/remote/creation/…` (`…/rccreation/foundation/…` for `…/remote/foundation/…`).
2. Rewrite its packages, as `scripts/upstream-packages.tsv` lists them:
   `androidx.compose.remote.creation.compose` to `ee.schimke.composeai.rccreation.compose` and
   `androidx.compose.remote.foundation` to `ee.schimke.composeai.rccreation.foundation`, everywhere
   in the file. Names of upstream's Java classes (`androidx.compose.remote.core.…`,
   `androidx.compose.remote.creation.RemoteComposeWriter`) become the writer's, as
   `scripts/upstream-fqn.tsv` lists them.
3. Re-apply the marked hunks (`LOCAL PATCH (rc-players)`, `KMP (rc-players)`), and format.

`diff-upstream.sh --summary <androidx checkout>` verifies the result: it applies steps 1 and 2 to
upstream's side itself, so a file that was copied and rewritten correctly is identical, or differs
only in its marked hunks. A file upstream moves between modules moves between the fork's modules
with it.

## Feedback for AndroidX

In priority order: the earlier an item, the more of the fork it would remove. Each was checked by
reading the upstream sources at `21a1a95e4`; the line numbers are from that commit.

### 1. Put an interface in front of the Java writer

**What.** The Kotlin creation code is bound to one concrete Java class.

**Where.** `expression/capture/RemoteComposeCreationState.kt:43-44`:
`public interface RemoteDocumentScope { val document: RemoteComposeWriter }`.
`RemoteComposeWriter` is `remote-creation-core/src/main/java/androidx/compose/remote/creation/RemoteComposeWriter.java`,
5,707 lines with 347 public methods.

**Why it matters.** However portable the Kotlin becomes, every file that writes can only target the
JVM while `document` is that class. The Kotlin uses a fraction of it: about 40 distinct methods from
`remote-creation-expression` and about 85 from `remote-creation-compose`.

**Suggested.** A Kotlin interface in `remote-creation`'s `commonMain` (which today holds one
31-line file) declaring the methods the Kotlin calls; `RemoteComposeWriter` implements it;
`RemoteDocumentScope.document` is typed as the interface. The fork's
`third_party/remote-creation-writer/src/commonMain/kotlin/ee/schimke/composeai/rccreation/common/RemoteWriter.kt`
(128 functions) is a working version of this surface, with a second implementation behind it, and
can be offered as a starting point.

A naming note: `remote-creation-core/…/RemoteComposeWriterInterface.java` is not this. It is a
one-method callback (`void run()`) used as the `content` parameter of `root`, `box`, `row` and the
other layout methods (16 uses in `RemoteComposeWriter.java`, from line 3576). Its name reads as the
interface asked for here and would collide with it.

### 2. Move wire constants and pure helpers out of the Java player classes

**What.** Opcodes, variable ids, flag values and a few pure functions live as static members of
`remote-core` classes that are otherwise player implementation.

**Where.** Imported by 21 of 31 expression files and 53 of 143 `jvmAndAndroidMain` files, among
them `core.operations.Utils` (`asNan`, `idFromNan`),
`core.operations.utilities.AnimatedFloatExpression` and `IntegerExpressionEvaluator` (operator
codes), `core.RemoteContext` (`FLOAT_*` and `ID_*`), `core.operations.TextFromFloat` (padding and
grouping flags), `TimeAttribute`, `ColorAttribute`, `ImageAttribute`, `ComponentValue`,
`core.Operations`, `layout.managers.CoreText` and `TextLayout`,
`layout.modifiers.DimensionModifierOperation.Type`, `paint.PaintBundle`,
`layout.modifiers.GraphicsLayerModifierOperation` (28 attribute ids in
`common/modifier/GraphicsLayerModifier.kt`), `layout.MultiClickModifier`,
`layout.managers.CollapsiblePriority`, `semantics.AccessibleComponent`. Some constants are one
module up, on `remote-creation-core`'s builder classes: `modifiers.ScrollModifier.VERTICAL` and
`HORIZONTAL` (`common/modifier/ScrollModifier.kt:129,146`). Four uses are code, not
constants: `StringUtils.floatToString` (`expression/state/RemoteFloat.kt:942`), `CubicEasing()` and
`MonotonicSpline(…)` used for constant folding (`expression/state/RemoteFloatOperations.kt:506,548`),
`FloatAnimation.packToFloatArray` (`expression/state/RemoteAnimation.kt:136`) and
`RemoteComposeBuffer.packAnimation` (`expression/state/RemoteFloatOperations.kt:653`,
`common/layout/RemoteAccess.kt:59`).

**Why it matters.** 19 expression files (8,114 lines) and 76 `jvmAndAndroidMain` files
(11,094 lines) have no portability blocker other than imports from the Java modules, and for most
of them those imports are constants.

**Suggested.** A small common-Kotlin constants artifact that both the Java player and the Kotlin
creation side read. `remote-generator` (added as a skeleton in `0b2376ee0`, described in its build
file as a generator of multi-language operation bindings) looks like the natural producer; Kotlin
Multiplatform common code would be a valuable output, constants first. The per-profile operation
tables that `48e08566f` turned into plain `int[]`s (`remote-core/…/core/Operations.java:459-552`)
belong in the same place.

### 3. Make `remote-creation-expression` multiplatform

**What.** The module is 31 Kotlin files and no Java, built as `java-library` +
`org.jetbrains.kotlin.jvm` with its sources in `src/main/java`.

**Where.** `remote-creation-expression/build.gradle`.

**Why it matters.** It is the only Kotlin dependency of `remote-creation-compose`, so while it is
JVM-only, `jvmAndAndroidMain` there can never become `commonMain`. Its own JVM-only constructs are
nine lines in three files:

| Construct | Where |
| --- | --- |
| `java.lang.ref.WeakReference` | `expression/state/RemoteStateCacheKey.kt:27,228,233` |
| `System.identityHashCode` | `expression/state/RemoteStateCacheKey.kt:202,218` |
| `Enum.declaringJavaClass` | `expression/state/RemoteStateCacheKey.kt:257,506` |
| `System.arraycopy` (`copyInto` does the same) | `expression/state/RemoteInt.kt:766` |
| `.javaClass`, in an error message | `expression/state/RemoteStateVisitor.kt:147` |

**Suggested.** `androidXMultiplatform { androidLibrary; jvm() }` as `remote-creation-compose` has.
Nine files (1,499 lines) can be `commonMain` as they are; the rest follow items 1 and 2.

One of these needs a decision, not just an `expect`/`actual`: cache-key equality decides whether two
expressions share an id in the document. `RemoteStateInstanceKey` relies on identity hashing, and
the enum keys use `declaringJavaClass.name.hashCode()` with a comment that an enum's own
`hashCode()` is not stable across processes. The fork uses a per-instance stored hash and
`value::class.simpleName`; the second is weaker (two enums with one simple name in different
packages collide), so a specified replacement would be better than ours.

### 4. A public capture entry point without `android.content.Context`

**What.** The JVM artifact of `remote-creation-compose` can declare remote UI but has no public way
to turn it into a document.

**Where.** `common/capture/CaptureRemoteDocument.kt:63,84` (`captureSingleRemoteDocument`) and
`:126,151` (`captureRemoteDocument`) are `internal`. The public functions of the same names are in
`android/capture/CaptureRemoteDocument.android.kt:98,229` and take a `Context`. The public
`Profile(…)` factory is in `android/capture/Profile.android.kt:49`; `remote-creation`'s `jvmMain`
has `JvmRcPlatformServices`, but nothing public builds a `Profile` from it.

**Why it matters.** A desktop host has to use restricted API to capture. This is also why the fork
has its own entry point.

**Suggested.** Make the `jvmAndAndroidMain` overloads public (display info, profile, content;
optional clock and coroutine context) and add a `jvmMain` `Profile(…)`.

### 5. Give `remote-creation-compose` a `commonMain`

**What.** There is no `commonMain` source set; everything shared is in `jvmAndAndroidMain`.

**Where.** `remote-creation-compose/build.gradle` declares `androidLibrary` and `jvm()` only.

**Why it matters.** 56 of the 143 files (7,776 lines) import nothing but the Compose runtime,
`androidx.annotation`, `androidx.collection`, coroutines and each other, and could be common today.
Outside `capture/headless/`, the JVM-only constructs in the source set are four:

| Construct | Where |
| --- | --- |
| `java.util.concurrent.atomic.AtomicLong` | `common/layout/RemoteComposeApplier.kt:20` |
| `java.util.concurrent.atomic.AtomicInteger` | `common/capture/CaptureRemoteDocument.kt:39` |
| `.javaClass`, in an error message | `common/layout/RemoteCanvas.kt:966` |
| `Math.toRadians`, `Math.cos`, `Math.sin` (`kotlin.math` has all three) | `common/capture/CanvasOperationBuffer.kt:1082-1084` |

**Suggested.** Add the source set and move files into it as their dependencies clear, the way the
Compose UI decoupling was done one type at a time.

### 6. Extract `capture/headless/` as the multiplatform library its README describes

**What.** The capture loop is written to be independent of Remote Compose, and is JVM-bound.

**Where.** `common/capture/headless/` (10 Kotlin files, 2,146 lines). Its `README.md` says the
package "must not import `androidx.compose.remote.*`, so that it can be lifted into a library
shared with the other" headless hosts, and that "`ThreadLocal` and `java.util.concurrent` usages
would need `expect`/`actual` counterparts". What binds it to the JVM, in full:

| Construct | Where |
| --- | --- |
| `synchronized(…)`, 23 uses | `CaptureWriteTracker.kt` (8), `HeadlessSession.kt` (6), `RenderInvalidationTracker.kt` (5), `SnapshotWriteMonitor.kt` (4) |
| `java.util.concurrent.atomic.*` | `HeadlessFrameClock.kt:21-22`, `HeadlessSession.kt:24`, `RecomposerContext.kt:19`, `SnapshotWriteMonitor.kt:22` |
| `ThreadLocal`, `IdentityHashMap`, `Collections` | `CaptureWriteTracker.kt:22-23,59` |
| `LockSupport.parkNanos`, `Thread.yield()` | `RecomposerQuiescence.kt:22,443,445` |

**Why it matters.** This is the loop every host without a UI needs: frame budget, quiescence, a
streaming flow, tracking what a render read. The fork wrote a smaller one of its own because this
one could not be compiled off the JVM, and would rather carry upstream's.

**Suggested.** `kotlin.concurrent.atomics` and a lock type that has a no-op single-threaded
implementation. Please also say what `SnapshotWriteMonitor` and `awaitRecomposerQuiescence` should
do on a single-threaded target (JS, Wasm): both are written for a second thread delivering apply
notifications.

A small inconsistency while there: `HeadlessRenderFlow.kt:19-20` and `HeadlessSession.kt:19-20`
import `androidx.compose.remote.creation.compose.capture.CaptureTracer` and `NoOpCaptureTracer`,
which the README's rule forbids.

### 7. Move the Compose UI conversions out of `androidMain`

**What.** The functions that convert Compose UI types to `Remote*` types exist only for Android.

**Where.** Ten `androidMain` files (1,512 lines) import nothing but Compose UI, the Compose runtime
and the module itself: `android/layout/RemoteLayoutDirection.android.kt`,
`android/state/RemoteDpCompose.android.kt`, `RemoteImageBitmapCompose.android.kt`,
`RemoteTextUnitCompose.android.kt`, `android/text/RemoteFontVariation.android.kt`,
`RemoteTextStyle.android.kt`, `android/vector/RemoteAnimatedVectorPainter.android.kt`,
`RemoteVector.android.kt`, `android/widgets/WidgetInformation.android.kt` and
`WidgetsModifiers.android.kt`. Two more are the same apart from one resource-id overload each:
`android/capture/RemoteImageVector.android.kt` (`vectorResource(@DrawableRes id)`) and
`android/state/RemoteColorCompose.android.kt` (`@ColorRes` themed colours).

**Why it matters.** `RemoteColor(Color)`, `Dp.asRdp()`, `RemoteTextStyle.fromTextStyle` and
`ImageVector.toRemoteImageVector()` are what an app author types first. On the JVM target they do
not exist, so source that uses them does not compile for desktop. The fork compiles these files for
JVM, Wasm, iOS and macOS with Compose Multiplatform, unchanged apart from the one obstacle below.

**Suggested.** A source set shared by every target that has Compose UI. The obstacle is
`android/text/RemoteTextStyle.android.kt:103-104`, which reads `variationSettings` from
`AndroidFont` and `ResourceFont`; a common accessor for a `Font`'s variation settings in `ui-text`
would remove it.

### 8. Do not reach through the writer into `RemoteComposeBuffer` from Kotlin

**What.** Some Kotlin calls bypass `RemoteComposeWriter` and use the buffer under it, so an
interface on the writer (item 1) would not cover them.

**Where.** In `jvmAndAndroidMain`, 74 uses of `….buffer.` in four files:
`common/layout/RemoteCanvas.kt` (67: `idLookup` 20, `addPatternForEach` and `endPatternForEach` 9
each, `definePattern`, `inflatePattern`, `endPatternInflation` and `endPatternDefine` 7 each,
`addPaint` 1), `common/action/HostAction.kt:153,168,170` (the static
`HostNamedActionOperation.apply(writer.buffer.buffer, …)` and its two siblings, which go one level
further down to the `WireBuffer`), `common/capture/CaptureRemoteDocument.kt:231,273,275`
(`document.buffer.buffer.size()`) and `common/layout/RemoteCanvasComposable.kt:32`
(`lastComponentId`). In `remote-creation-expression`, nine: `document.buffer.addComponentValue`
(`expression/capture/RemoteComposeCreationState.kt:144`) and eight reads of
`doc.buffer.lastComponentId` (`expression/layout/RemoteFloatContext.kt`).

**Suggested.** Forwarding methods on the writer for these, and `packAnimation` (item 2) as a pure
function beside the constants.

### 9. `foundation/foundation` is Android-only for no reason visible in its sources

**What.** The module is a `com.android.library`.

**Where.** `foundation/foundation/build.gradle`. Its four main files
(`icon/RemoteBasicIcon.kt`, `image/RemoteBasicImage.kt`, `layout/RemoteSpacer.kt`,
`text/RemoteBasicText.kt`) import only `androidx.compose.remote.creation.compose.*` and
`androidx.compose.runtime.*`.

**Why it matters.** `RemoteBasicText` and `RemoteBasicImage` are the leaves other components are
built from; the JVM target of `remote-creation-compose` has neither.

**Suggested.** The same multiplatform setup as `remote-creation-compose`. Nothing in the main
sources uses the module's `api("androidx.compose.ui:ui")` or `api("…foundation-layout")`
dependencies; whether its samples or tests need them was not checked.

### 10. `RemotePath` should not put platform geometry types in an `expect` API

**What.** The common declaration of `RemotePath` names platform types.

**Where.** `remote-creation/src/jvmAndAndroidMain/kotlin/androidx/compose/remote/creation/RemotePath.kt:21-28`
declares `public expect class RectF`, `Path` and `MatrixTransform`, each with
`@Suppress("KmpVisibilityMismatch") // actuals are internal`; the actuals are typealiases to
`android.graphics.*` (`androidMain/…/RemotePath.android.kt:27-31`) and to `java.awt.geom.*`
(`jvmMain/…/RemotePath.jvm.kt:26-30`). `RemotePath` itself exposes `val path: Path`,
`transform(matrix: MatrixTransform)` and `addArc`/`arcTo(oval: RectF, …)`.
`RemotePathBase.kt` wraps `androidx.compose.remote.core.RemotePathBase`, a Java class.

**Why it matters.** A new target has to invent a path, a rectangle and a matrix type to provide the
actuals, and the visibility mismatch is suppressed, not resolved. Creation only needs the encoded
float array and the builder verbs (`moveTo`, `lineTo`, `cubicTo`, …).

**Suggested.** Make the pure-Kotlin builder the common class, and make `path`, `transform(Matrix)`
and the `RectF` overloads platform extension functions. The fork's
`third_party/remote-creation-compose/compose/src/commonMain/kotlin/ee/schimke/composeai/rccreation/compose/path/RemotePath.kt`
(150 lines) is such a builder.

### 11. Modifiers and actions are built as `remote-creation-core` Java objects

**What.** Each modifier and action element of the Compose layer turns itself into a Java builder
object, which then writes itself.

**Where.** `common/modifier/RemoteModifier.kt:40,117`:
`fun RemoteStateScope.toRecordingModifier(): RecordingModifier` and
`fun RemoteStateScope.toRecordingModifierElement(): RecordingModifier.Element`, implemented in 26
files of `common/modifier/` against 23 classes of `androidx.compose.remote.creation.modifiers`
(`WidthModifier`, `PaddingModifier`, `ClickActionModifier`, `GraphicsLayerModifier`, …).
`common/action/RemoteAction.kt:27`: `fun RemoteStateScope.toRemoteAction(): CreationAction`
(`androidx.compose.remote.creation.actions.Action`), implemented in 5 files. The layout nodes pass
the result to `document.startBox(recordingModifier, …)` and its siblings.

**Why it matters.** An interface in front of the writer (item 1) does not free these files: their
return types are Java classes of `remote-creation-core`, so every modifier and action file stays
JVM-only until those types are Kotlin or are behind an interface too. In the fork they are the
largest group of marked hunks: one per element, where it builds a Kotlin value
(`RemoteModifierOperation`, `RemoteActionData`) in place of the Java object.

**Suggested.** Decide this together with item 1. Either the writer interface takes plain values
(an operation per modifier, as `ScrollModifierOperation.apply(buffer, …)` already is one level
down), or `RecordingModifier.Element` and `Action` become Kotlin interfaces in `remote-creation`'s
common source set with the element classes beside them. The fork's
`third_party/remote-creation-writer/…/RemoteModifierData.kt` (160 lines) is the first form.

`RemoteComposeCaptureState` has the same shape of problem in one place: all three constructors
(`common/capture/RemoteComposeCaptureState.kt:60,75,88`) take a `Profile`, a Java class of
`remote-creation-core` that carries `RcPlatformServices`, so the capture state cannot be built
without both.

## Candidate contributions

Additions the fork carries as marked local patches because something outside it needed them. Each
is small and not specific to rc-players. File paths are under
`third_party/remote-creation-compose/` unless they start with `../`.

| Addition | What it is for | In the fork | Upstream today |
| --- | --- | --- | --- |
| Capture into a caller-supplied writer | A host that wants the operations, not the bytes | `captureCommonRemoteDocumentInto(writer, …)` in `compose/upstream/remote-creation-compose/src/jvmAndAndroidMain/…/capture/CaptureRemoteDocument.kt` | `RemoteComposeCaptureState` has a constructor taking a `RemoteComposeWriter` and `document` is a `var`, but no public capture uses them |
| A composition under a host's `CompositionContext`, re-rendered on demand, reporting when a state its last render read has changed | Live preview and editors: the remote content recomposes with the host's | `compose/src/commonMain/…/capture/RemoteDocumentSession.kt`; `onChangesApplied` on `RemoteComposeApplier` | Free-standing recomposers only (`headlessRenderFlow`, internal); `rememberRemoteDocument` in `android/capture/RemoteComposeCapture.android.kt` is the nearest |
| Component ids that restart with each render of a retained tree | Anything keyed on a component id (editor selection, player state) survives a re-render | `resetComponentIds()` in `expression/upstream/remote-creation-expression/…/capture/RemoteComposeCreationState.kt` | Not present |
| `RemoteModifier.componentId(id)` | A host names a component and maps player geometry back to its own nodes | `compose/src/commonMain/…/modifier/ComponentIdModifier.kt` | `RecordingModifier.componentId(id)` exists in Java (`remote-creation-core/…/modifiers/RecordingModifier.java:146`); the Compose layer calls it only to number a custom component (`common/layout/RemoteCustomComponent.kt:42`), and no `RemoteModifier` exposes it |
| `RemoteBrush.runtimeShader(source, uniforms…)` | AGSL shaders with `RemoteFloat` uniforms | `compose/src/commonMain/…/shaders/RemoteRuntimeShader.kt` | The core reads `ShaderData`; nothing in `remote-creation-compose` writes it |
| `drawMesh2D` on `RemoteCanvas` and `RemoteDrawScope`, channels as `RemoteFloat` expressions over `u`/`v` | Parametric 2D meshes | `compose/src/commonMain/…/layout/RemoteMesh2D.kt`, `expression/src/commonMain/…/state/MeshExpressionLowering.kt` | The core has the experimental operations; `remote-creation-core`'s `dsl/RcMesh.kt` writes them, nothing in `remote-creation-compose` does |
| A free-standing touch expression (`addTouchExpression`), and `textMerge` into a reserved id | Drag controls: a slider following the touch position, its value as a label | `../remote-creation-writer/…/RemoteWriter.kt` | In `RemoteComposeBuffer`; not reachable from `remote-creation-compose` |
| Component offscreen buffers and float functions | Custom visibility animations | `../remote-creation-writer/…/RemoteWriter.kt` (`createOffscreenBitmap`, `createFloatFunction`, …) | In `RemoteComposeWriter` and the `RcScope` DSL; not in `remote-creation-compose` |
| `toRemoteString(DecimalFormat)` over a plain Kotlin `DecimalFormat` | Number formatting off Android | `expression/src/commonMain/…/state/RemoteDecimalFormat.kt` | The overloads take `java.text.DecimalFormat` or ICU's, in `android/state/RemoteFormatting.android.kt` |
| PNG encoding supplied by the host (`encodePng: (ImageBitmap) -> ByteArray?`) | Inline bitmaps without a platform service | `captureCommonRemoteDocument` | `RcPlatformServices`, a Java interface, per platform |

Three of the bug fixes under "Bugs and inconsistencies found" are patches to upstream's own text
and could be offered as they are:

| Fix | In the fork | Applies upstream |
| --- | --- | --- |
| An animated vector's repeats, in the painter | `computeAnimatorLocalProgress`, `evaluateDynamicFloat`, `evaluateDynamicColor` in `compose/upstream/remote-creation-compose/src/androidMain/…/vector/RemoteAnimatedVectorPainter.android.kt` | As it is |
| An animated vector's interpolators, the same in both evaluations | `RemotePropertyAnimator.evaluateAt` and `RemoteInterpolator.Accelerate`/`Decelerate` in `compose/upstream/remote-creation-compose/src/jvmAndAndroidMain/…/vector/RemoteAnimatedVector.kt`; `transformInterpolator`, `evaluateAnimatorFloat`, `evaluateAnimatorColor` in the painter | As it is |
| A text measurement does not change the paint a draw uses | `measureAttribute` in `…/layout/RemoteTextMeasure.kt`, `flush`, `usePaintInternal`, `recordRenderingOp` and `paintWrittenOutsideTracker` in `…/layout/RemoteCanvas.kt`, one field in `…/capture/RemoteComposeCaptureState.kt` | As it is: none of it touches what the fork replaces |

The loop fix is not on that list: the fork makes it in its own writer (`startLoop`, `endLoop`),
where upstream's place for it is `RemoteCanvas.loop`, two lines in each overload.

## Bugs and inconsistencies found

Each says how it was verified. None depends on the structural items above.

### `RemoteText` writes `RemoteTextAlign.Unspecified` to the wire

`common/layout/RemoteText.kt:117` falls back to `style.textAlign`, whose default is
`RemoteTextAlign.Unspecified` (`common/text/RemoteTextStyle.kt:58`), which is `Int.MIN_VALUE`
(`common/text/RemoteTextAlign.kt:42`). The `when` at `RemoteText.kt:245-259` maps `Start` and `End`
for layout direction and passes anything else through (`else -> textAlign`), and line 285 writes
`resolvedTextAlign.value`. So a `RemoteText` with no alignment set writes `0x80000000` as its text
alignment, where it wrote `Start` before `99f14adf8`.

AndroidX's own reader hides this: `CoreText` masks the value to its low 16 bits
(`remote-core/…/layout/managers/CoreText.java:386`), gets 0, and its `switch` treats that as start
(`:535-546`). The high bits are read as flags (`getFlagsFromTextAlign`, `:258-260`), so the
document also carries flag bit 15 set. A reader that validates the alignment against the six
defined values rejects the document; rc-players' does. Suggested fix: resolve `Unspecified` to
`Start` before writing, which is what the fork does.

*Verified by reading both sides. No test here isolates it.*

### A `RemoteDensityBehavior.Dp` document's header carries pixels

`RemoteCreationDisplayInfo.toCreationDisplayInfo()`
(`common/capture/RemoteCreationDisplayInfo.kt:105-111`) passes `width` and `height`, which are
pixels, into the header. For a `DENSITY_BEHAVIOR_DP` document the player multiplies the header's
width and height by the display density (`remote-core/…/core/RemoteContext.java:635-639`), that is,
it reads them as dp. A 200-pixel-wide capture at density 2 becomes a 200 dp, 400-pixel document,
and `RemoteConfiguration.windowWidth` reports 200 dp where the display is 100 dp wide.

*Verified by reading both sides. The fork writes what upstream writes here, so nothing in this
repository tests a fix.*

### A constant font size is scaled twice in a `Dp` document

`common/layout/RemoteText.kt:239` writes `fontSize.toPx(remoteDensity)`: when the density is a
constant, a literal number of pixels. `CoreText.updateVariables` multiplies a literal font size by
the display density when the document's density behaviour is `Dp`
(`remote-core/…/layout/managers/CoreText.java:273-276`), so the text is drawn at the size times the
density squared. A font size that is an expression is not rescaled, so only constants are affected.
The fork writes the constant in dp in a `Dp` document.

*Verified by reading both sides, and by a test here: `DpDocumentFontSizeTest` in
`:rc-creation-bridge` asserts the value the fork writes for each density behaviour.*

### `ValueChangeAction` throws for a string that is not a constant

`common/action/ValueChangeAction.kt:62` builds
`ValueStringChange(actualMutable.id, actualValue.constantValue)`, and `constantValue` is
`checkNotNull(constantValueOrNull)` (`expression/state/RemoteState.kt:51-52`). Setting a mutable
remote string to a computed string therefore fails at capture with "No constant value for this
state". `ValueStringChange` already has an `(int id, int stringId)` constructor
(`remote-creation-core/…/actions/ValueStringChange.java:36`), so `actualValue.id` would serve both
cases. Two lines further on, the final `else` is
`TODO("println unsupported type in ValueChange $actualMutable")`, which throws
`NotImplementedError` at capture for any other state type.

*Verified by reading. No test here.*

### What a layout's content declares, the player drops unless it is `ComponentData`

`LayoutComponent.inflate` (`remote-core/…/operations/layout/LayoutComponent.java`) rebuilds a
layout component from its modifiers, its child components and, of everything else its content
holds, `Component.getData`'s selection: `TextData`, `BitmapData` and the operations that implement
`ComponentData`. The rest is not kept, and is never applied. `NamedVariable`, `IntegerConstant`,
`LongConstant`, `PathData`, `DataListFloat`, `TimeAttribute`, `ImageAttribute`, `TextAttribute`,
`TextSubtext`, `TextTransform`, `IdLookup`, `BitmapFontData`, `FloatFunctionDefine` and
`MatrixExpression` are none of the three (alpha23 and `21a1a95e4` alike).

The creation library writes a declaration where its value is first resolved, and a component's
modifiers are resolved just before the component starts, which is inside its parent's content. So
a named state first read by a modifier, or as a state layout's index, is declared in a box's
content: `RemoteStateLayout(RemoteInt.createNamedRemoteInt("mode", 0), 0, 1) { … }` inside a
`RemoteBox` writes `NamedVariable` and `IntegerConstant` after the box's `LayoutContent`. The
player then has no name `USER:mode`, and `RemoteComposePlayer.setUserLocalInt("mode", 1)` does
nothing. A path first used by a clip or border modifier, a float list and a time attribute go the
same way. (Android 16's platform player, whose `ComponentData` set is smaller, also loses
`TextFromFloat`; `rc-widget-launcher`'s `platformWidgetDocument` lifts those for it.)

Suggested fix: either keep every data operation `inflate` finds in a content (all of them are
harmless to apply), or have the writer write what is not `ComponentData` ahead of the root. The
fork's writer does the second, for a declaration made among a layout's operations and not in a
canvas, whose drawing the player keeps whole. It cannot do it for one that reads a value declared
in place among the components (a `TextSubtext` of a text made from a touch expression's value):
that one stays, and AndroidX's player drops it.

*Verified by playing: `AndroidxPlayerParityTest.aNamedStateFirstReadInALayoutCanBeSetByTheHost`
(`:rc-creation-coexistence-check`, Robolectric) captures that state layout with AndroidX
1.0.0-alpha23's own `captureSingleRemoteDocument` and plays it in `RemoteComposePlayer`: the branch
does not change when the host sets the name, and does for the fork's document. The `ComponentData`
sets were read from alpha23's classes and from `21a1a95e4`.*

### A component's size asked for after the component is never bound

`RemoteComposeCreationState.getComponentValue` writes a `ComponentValue` directly while the writer
is in the component, and otherwise reserves a variable that `beginComponentContent` or
`endComponentContent` binds (`expression/capture/RemoteComposeCreationState.kt:193-218`). A request
that arrives after the component's `endComponentContent` (a later sibling sized from an earlier
one's `remoteComponent()`, resolved for the first time there) reserves a variable that nothing
binds in that capture: the token remembers the request (`markRequested`), so a second capture of
the same nodes would write it, but a capture is one pass. The reader of that variable reads
nothing. The fork writes such a value after the root, naming the component's id:
`CoreDocument.registerVariables` gives a value outside every component to the component it names,
provided the component comes before it in the document (ahead of the root it prints "Component not
found for id" and binds nothing, which is why no measured size of a 3.0.0 document, whose values
were all ahead of the root, reached AndroidX's player).

*Read in upstream, not run against AndroidX's own writer. The fork's branch is tested in
`:rc-creation-bridge`: `DeclarationPlacementTest.aSizeAskedForAfterItsComponentIsWrittenAfterTheRootForIt`
has AndroidX's `CoreDocument` (alpha23) resolve the value to the earlier box's width.*

### A named state read in two canvases is two variables

`withCanvasOperationsScope` (`expression/capture/RemoteComposeCreationState.kt`, `21a1a95e4`)
saves the variable cache when a canvas starts and restores it when it ends, so that an expression
declared in one canvas is declared again in the next. A named state is in that cache too:
`createNamedRemoteFloat("level", 0.5f)` first read in one canvas's draw and read again in another's
writes two `NamedVariable`s for `USER:level`, with two ids. A player keeps one id for a name, the
last, so `setUserLocalFloat("level", …)` moves the second canvas and not the first. alpha23, which
has no such scopes, wrote one. Suggested fix: keep named states (and anything else a player loads
with the document) out of the saved frame. The fork's writer answers one id for a name and type
and declares it ahead of the root.

*Verified here: `AndroidxPlayerParityTest.aNamedValueReadByTwoCanvasesIsOneVariable`
(`:rc-creation-coexistence-check`) compares the fork's document with alpha23's. The two variables
were seen in the fork's own documents before the writer kept one, in Home Assistant's
`CardDesignParityTest`; androidx-main's writer was read, not run.*

### An operation outside the declared profile fails without saying which profile is missing

At document API level 7, `DRAW_TO_BITMAP` (190) is in the AndroidX and widgets operation tables
only, `DATA_SHADER` (45) in the AndroidX table only, and the 2D mesh operations in the two
experimental tables only (`remote-core/…/core/Operations.java:459-552`). Writing one under another profile fails with
"Operation 190 is not supported for this version" (`remote-core/…/core/WireBuffer.java:130`), and
reading one fails with "Unknown operation encountered 190"
(`remote-core/…/core/RemoteComposeBuffer.java:1082`). Neither message names a profile, so the
author of a baseline document that draws to an offscreen bitmap is not told that the fix is to
declare `PROFILE_ANDROIDX`. The fork's writer gives the same message under a `Profile`, to be
AndroidX's; a writer made without one raises the declared profile itself when a document uses one
of these operations.

*The tables and both messages verified by reading. That a document declaring the profile loads in
AndroidX's `CoreDocument` is tested here by the read-back tests in `:rc-creation-bridge`
(`AndroidxReadsVisibilityAnimationTest`, `RuntimeShaderBrushTest`, `Mesh2DAuthoringTest`), which
run against the AndroidX release this repository depends on, not against `21a1a95e4`.*

### `RemoteCanvas.drawBitmap(bitmap, left, top)` asks the platform for the size of an id

`common/layout/RemoteCanvas.kt:684` calls `document.drawBitmap(bitmap.id, left, top, "")`, the
overload at `remote-creation-core/…/RemoteComposeWriter.java:1425-1430`, which reads
`mPlatform.getImageWidth(imageId)` and `getImageHeight(imageId)`. `RcPlatformServices.getImageWidth`
takes the platform image object (`remote-core/…/RcPlatformServices.java:41`); given a boxed `int`,
the JVM implementation returns 0 (`remote-creation/src/jvmMain/…/JvmRcPlatformServices.jvm.kt:41-46`,
`if (image is BufferedImage)`), `AndroidRcPlatformServices` does the same with `instanceof Bitmap`
(`remote-player-view/…/platform/AndroidRcPlatformServices.java:54-59`), and
`RcPlatformServices.None` throws. So the bitmap is drawn into the rectangle (left, top, 0, 0).
Separately, the width and height are passed as the rectangle's right and bottom, which is the
bitmap's size only when `left` and `top` are 0. The fork writes `left + bitmap.width` and
`top + bitmap.height`, from the bitmap's own size expressions.

*Upstream's side verified by reading: the fork's writer has no platform services. The fork's
rectangle is tested here: `RcCanvasBitmapSizePlaybackTest` in `:rc-player-embedded` plays a bitmap
drawn at an offset, and `CanvasBitmapReadBackTest` in `:rc-creation-bridge` reads the rectangle
with AndroidX's reader.*

### A bitmap drawn on a canvas is given an empty content description

Both `RemoteCanvas.drawBitmap` overloads (`common/layout/RemoteCanvas.kt:684,715`) pass `""` as the
content description. `RemoteComposeWriter.drawBitmap` adds a text for any description that is not
null (`remote-creation-core/…/RemoteComposeWriter.java:1405-1416`), so every document that draws a
bitmap on a canvas declares an empty text and names it in `DrawBitmap`, where id 0 is what the
operation has for no description (the same method writes 0 for `null`). Nothing in `remote-core`
reads the id when it paints, so nothing is drawn differently; a reader that exposes descriptions
(this repository's player builds semantics from them) reports an image with an empty one instead of
an image with none. Suggested fix: pass `null`. The fork writes 0.

*Verified by reading both sides; `CanvasBitmapReadBackTest` asserts what the fork writes.*

### A path string means different things on Android and on the JVM

`RemoteAnimatedVector` (`common/vector/RemoteAnimatedVector.kt:392,411`) draws through
`writer.addPathString`, which is `addPathData(mPlatform.parsePath(path))`
(`RemoteComposeWriter.java:2253`). On the JVM that is `RemotePath(pathData)`
(`JvmRcPlatformServices.jvm.kt:71-73`), whose parser
(`remote-core/…/RemotePathBase.java:564-646`) takes only the absolute commands `M L H C S Q Z` and
the arcs `A a`, and throws `IllegalArgumentException("Unsupported command: …")` for every relative
command and for `V` and `T`, and `M` takes one pair. An animated vector drawable's
path data is usually relative, so the same `RemoteAnimatedVector` that draws on Android throws on
the JVM. `android/vector/RemoteAnimatedVectorPainter.android.kt:188,451` already parses with Compose
UI's `PathParser`, which is common code; the fork carries that file for every target and ported
the JVM parser as it is for `addPathString`.

*Verified by reading; the fork's port of the parser is tested (`CarriedCreationApiTest` in
`:rc-creation-bridge`), AndroidX's is not run here.*

### What a pattern's body declares first is not declared for the code after it

The creation state caches the id it gave a state (`remoteVariableToId` and the lowered-expression
caches, `expression/capture/RemoteComposeCreationState.kt`), and the writer writes a declaration
where it is first asked for. A value from outside a pattern that its body is the first to read (a
named float, say) is therefore declared inside `MACRO_DEFINE`, in the template. AndroidX gives
what a template declares a new id when it expands it, so the original id is declared nowhere in
the expanded document, and a later draw that reads the same state, for which the cache still
answers the original id, reads a variable that does not exist. `withCanvasOperationsScope` scopes
those caches to a canvas, not to a pattern, for-each or loop body, and the canvas's common
sub-expression pass hoists only an operation used twice, not a named value. Suggested fix: declare
in the body only what is computed from the pattern's parameters (or the for-each's item), and
everything else before the definition; or scope the caches to the body. The fork's writer does the
first: it tracks the ids a body owns, writes a declaration in the body only when it reads one, and
writes what a template's body declares otherwise just ahead of the template.

Two more forms of the same exposure, read in upstream and not run against it. A body nested in a
body: an expression of the outer pattern's parameter first used inside a `forEach` in it is
declared in the for-each's template, and the same caches answer that id for the outer body's later
use, unless the canvas's common sub-expression pass saw both uses (it sees what a draw registers
with `addRoots`; a paint's states are not registered). And an id taken while the body is being
recorded: `x.floatId` on an expression of the parameter, for a `RemotePath` coordinate, writes the
expression at that moment, before the pattern definition is written when the canvas flushes, so
it is outside the template. `vector/RemoteVector.kt`'s `toRemotePath(clipPath, remoteCanvas)` takes
ids that way. The fork checks its caches against the ids of bodies that have ended, and refuses
the second form when the body is opened.

*Verified with a document laid out as upstream's writer lays it out (the fork's, before the fix,
wrote every declaration of a pattern body in the body): `LoomPatternsTest` in `:rc-creation-bridge`
has AndroidX's reader (`remote-core` 1.0.0-alpha23) expand "a pattern that draws a circle of a named
float's radius, called, then a circle of the same radius", and the last circle's radius was an id
no operation declared; `RcPatternCapturedStatePlaybackTest` in `:rc-player-embedded` plays it, and
before the fix neither circle was drawn. Not run against AndroidX's own writer.*

### `RemoteCanvas.save()` returns a count `restoreToCount` does nothing with

`common/layout/RemoteCanvas.kt:193-197` documents `save()` as returning "the value to pass to
`restoreToCount` to balance this `save`", as `android.graphics.Canvas.save` does. It returns
`buffer.save()`, and `CanvasOperationBuffer.save` (`common/capture/CanvasOperationBuffer.kt:846-857`)
increments `globalSaveCounter` and returns it: the count after the save. `restoreToCount`
(`:877-881`) restores `while (globalSaveCounter > saveCount)`, so
`val count = canvas.save(); …; canvas.restoreToCount(count)` restores nothing. The save stays open,
its transform and clip apply to everything drawn afterwards, and inside a child block
(`drawConditionally`, `loop`, a pattern) the block ends with "Unbalanced save/restore in child
span". Nothing upstream calls `restoreToCount`, and `RemoteCanvasTest` only reads `saveCount`.
Suggested fix: return the count before the increment. The fork does.

*Verified by reading, and by tests here: `RcSaveRestoreToCountPlaybackTest` in `:rc-player-embedded`
captures the documented sequence, alone, nested and inside a conditional block, and plays it;
before the fix what followed `restoreToCount` was drawn translated, and the block threw.*

### `RemoteTextMeasurer` changes the paint behind the canvas's back

Each measurement writes a paint directly (`common/layout/RemoteTextMeasure.kt:219-230`:
`doc.rcPaint.setTextSize(…).setTypeface(…).commit()`) and then the `TextAttribute`. The canvas's
`PaintTracker` (`common/layout/RemoteCanvas.kt:142-156`) sends only what differs from the paint it
last sent, and nothing tells it that the text size and typeface have changed: the writer's
force-new-paint flag is set only at a component's content start
(`RemoteComposeWriter.java:3698-3701`). A draw after a measurement, with a paint whose text size
equals the one the tracker last sent, therefore draws with the measurement's size and weight.

The draw that reads the measurement is hit first. A measurement is written where its value is first
asked for, and a canvas draw asks for its arguments after it has applied its paint
(`recordRenderingOp(paint) { usePaintInternal(…); action() }`, `:162-171`), so
`drawText(text, measured.size.width, y, paint)` writes the draw's paint, then the measurement's,
then the draw: text with a 12 px paint, placed by a 20 px measurement, is 20 px text.

The fork patches it locally. The canvas is told when a measurement has been written
(`RemoteComposeCaptureState.onPaintWrittenOutsideCanvas`, set while `RemoteCanvas.flush` writes and
called at the end of `measureAttribute`'s provider). In every case the next paint it sends is
whole. Beyond that it depends on where the measurement was written:

- as a draw's argument: the paint last applied is sent again, in full, there, between the
  measurement and the draw;
- as a property of the paint being worked out (`RemotePaint { textSize = measured.size.width }`,
  a stroke width, a colour): nothing is sent from the callback, because the measurement is not
  cached until its provider returns and applying a paint there would measure again without end;
  the paint is worked out a second time once the first pass is over, and sent whole;
- outside a draw (a transform's argument, an expression two draws share, which is written ahead
  of both, a layout modifier): nothing is sent there, and the paint last applied is sent again
  before the next draw that inherits its paint, if no component's content has begun since.

A measurement of a measurement, and one in a conditional block, a loop's body or a pattern's body,
are these cases again. A document that measures on a canvas carries one more paint operation per
measurement first read in a draw; no other document changes. What it does not cover: a draw with
no paint at all before it in its component, which draws with the measurement's size on the
player's default paint, since there is no paint of the author's to send again. Suggested fix
upstream: the same, or resolve a draw's arguments before its paint.

*Verified by reading both sides, and by tests here, each failing before the patch.
`RcTextMeasurePaintPlaybackTest` in `:rc-player-embedded` plays it in the CMP player and measures
the text drawn (`renders/creation-text-measure-paint/`): 41×14 px before for "HHH" with a 12 px
paint, 25×9 after, which is what 12 px text is. `CanvasTextMeasureReadBackTest` in
`:rc-creation-bridge` has AndroidX's `CoreDocument` (`remote-core` 1.0.0-alpha23) paint the
document into a context that records the text size at each `getTextBounds` and `drawTextRun`: 20
at the measurement and 12 at each draw, where it was 20 at the draws. It has a test for each case
above, asserting the order of paints, measurements and texts written and the sizes AndroidX draws
at. One thing seen on the way and not changed: this repository's CMP player throws from Skia's
`TextStyle.setFontSize` on the first frame of a document whose paint's text size is an
*expression* of a measurement (`measured.size.width * 0.4f`), presumably evaluated before the
measurement has a value (not looked into); a text size that is the measurement itself plays.*

### A loop's body is written against the paint before it

`RemoteCanvas.loop` (`common/layout/RemoteCanvas.kt:909-950`, both overloads) records its body
through the canvas's one `PaintTracker` and writes it with `childSpan.record` inside
`writer.loop { }`, with no forced paint before or after: `forceSendingPaint` is set around
`drawConditionally` and `drawToOffscreenBitmap` only (`:850-852`, `:870-872`, `:896-898`), and the
writer's flag only at a component's content start. A loop's body is written once and drawn once
per value of the index, so the paint the player has at the top of the body is the body's last one
on every pass after the first. `drawRect(red)`, then a loop whose body draws a red rectangle and a
blue one: the body's first rectangle is sent no paint, since red is what the tracker last sent, and
from the second pass on it is blue. The other direction is the conditional block's: when the range
is empty (its end is a value the player holds), the player still has the paint from before the
loop, the tracker has the body's last, and a draw after the loop whose paint equals the tracker's
sends nothing. Suggested fix: `forceSendingPaint = true` before and after `childSpan.record` in
both overloads, as in `drawConditionally`.

The fork patches it locally, in its writer rather than in the canvas: `startLoop` and `endLoop`
set the writer's force flag, as its pattern and for-each operations already do, so the first paint
in a loop's body and the first after the loop are whole.

*Verified by reading, and by tests here, each failing before the patch.
`RcCanvasLoopPaintPlaybackTest` in `:rc-player-embedded` plays the sequence above with three passes
and with none (`renders/creation-loop-paint/`). `CanvasPaintReadBackTest` in `:rc-creation-bridge`
has AndroidX's `CoreDocument` (`remote-core` 1.0.0-alpha23) paint it: red, then red and blue three
times, then blue and red, where the second and third passes drew blue twice. Not run against
AndroidX's own writer. A for-each over a list and `drawToOffscreenBitmap` were already covered
(`RcPatternPaintPlaybackTest`, `RcCanvasPaintAfterBlockPlaybackTest`) and are not affected.*

### A pattern's body is written against the paint before it

`RemoteCanvas` has one `PaintTracker` (`common/layout/RemoteCanvas.kt:68`), and a paint is written
as its difference from the last one the tracker saw (`usePaintInternal`, `:149`). A pattern's body
is recorded through the same tracker (`patternBodySpan.record`, `:1015` and the six parameterized
overloads; the `forEach` bodies from `:1390`), but it is a template: the player draws it where the
pattern is called, with whatever paint it has there. So `drawCircle(red)`, then a pattern that
draws a red circle, leaves the template with no paint operation at all; call it, draw something
blue, call it again, and the second call draws blue. The other direction fails too: after a call
the player's paint is the template's last, the tracker's is the one before the call, and a draw
whose paint equals the tracker's sends nothing. The writer's force flag does not cover it:
`mHasForceSendingNewPaint` is set only at a component's content start
(`remote-creation-core/…/RemoteComposeWriter.java:3700`). Suggested fix: force a full paint for
the first draw in a pattern or for-each body, and for the first after a definition, a call and a
for-each, as `drawConditionally` and `drawToOffscreenBitmap` already do with `forceSendingPaint`.
The fork's writer sets its force flag at those five points.

*Verified by reading, and by tests here: `RcPatternPaintPlaybackTest` in `:rc-player-embedded`
plays the sequence above (the second call was blue before the fix), and `LoomPatternsTest` in
`:rc-creation-bridge` has AndroidX's reader (`remote-core` 1.0.0-alpha23) expand it: a paint now
precedes every circle.*

### An animated vector's two evaluations disagree on repeats and on the animator's interpolator

`vector/RemoteAnimatedVector.kt` evaluates a `RemotePropertyAnimator` twice over, and the two do
not agree. Both are upstream's text. The fork patches both locally (below).

- `RemotePropertyAnimator.evaluateAt` (the path `RemoteAnimatedVector.draw(writer, progress)`
  takes) works out the iteration and, under `REPEAT_MODE_REVERSE`, reverses odd ones, then
  interpolates between the adjacent keyframes with the *destination keyframe's* interpolator. It
  never applies the animator's own `interpolator`: a 0-to-1 animator with
  `RemoteInterpolator.Accelerate()` and default keyframes returns 0.5 halfway through, not 0.25.
- `computeAnimatorLocalProgress` in `vector/RemoteAnimatedVectorPainter.android.kt` (the path
  `painterRemoteAnimatedVector` takes, where progress is a `RemoteFloat`) does the opposite: it
  applies the animator's interpolator, but maps progress over `startDelay … startDelay + duration`
  and clamps, so an animator with a non-zero `repeatCount` holds its end value after the first
  iteration instead of restarting or reversing. `totalDuration` counts the repeats
  (`startDelay + duration * (repeatCount + 1)`), so the vector's timeline is the right length and
  the animator simply stops moving part of the way along it.

Two more differences come to light once those two agree, and belong to the same fix. The
painter's float and colour animators never apply a *keyframe's* interpolator (`evaluateAnimatorFloat`
and `evaluateAnimatorColor` interpolate each segment linearly), where `evaluateAt` applies the
segment's end keyframe's. And `transformInterpolator` writes `Accelerate` and `Decelerate` as the
squares they are for a factor of 1, whatever the factor (`RemoteInterpolator.Accelerate.factor` is
private, so it could not read it).

Suggested: one definition of an animator's value at a time, applied in both — iteration and
reverse first, then the animator's interpolator, then the keyframe segment's.

The fork patches it locally, to that definition:

- `computeAnimatorLocalProgress` counts iterations: for an animator with a non-zero `repeatCount`
  the fraction is the remainder of the elapsed iterations, run back on odd iterations under
  `REPEAT_MODE_REVERSE`, and the end value after the last. An animator that does not repeat
  writes the expression it wrote before.
- Where several animators drive one property, the painter (`evaluateDynamicFloat`,
  `evaluateDynamicColor`) chooses as `RemoteAnimatedVector.evaluate` does: the next animator, by
  start delay, takes over when its start delay is reached. Upstream's painter switches at the end
  of the earlier animator's first iteration instead, which is the same moment only when the two
  are end to end and the first does not repeat: with a gap it showed the second's first value
  where `evaluate` holds the first's last, and with an overlap it let the first run on.
- `evaluateAt` applies the animator's interpolator to the iteration's fraction before it looks for
  the keyframe segment.
- The painter applies the end keyframe's interpolator to each segment's fraction, and writes
  `Accelerate` and `Decelerate` with their factor.

`evaluate`'s rule, which both now follow, has one odd corner that is recorded here and not
changed: of the animators whose start delay has been reached it takes the one with the latest,
and of two with the same the later in the list; before any has been reached it takes the one with
the earliest, and of two with the same the *earlier* in the list. So two animators with the same
start delay show the first one's first keyframe until they start and the second one's values
after, and the first never runs. The order of the list matters nowhere else. An animator is cut
short by the next one's start whatever it is doing, including one that repeats without end.

What is left: a path morph (`pathData`) in the painter is laid out as segments of the whole
timeline (`extractPathSegments`), with neither repeats nor the animator's interpolator, so the two
ways still differ there for an animator that repeats or has an interpolator of its own; two
keyframes that are not at fractions 0 and 1 are read as if they were by the painter; and an
interpolator that is not one of the library's own (any other `RemoteInterpolator { }`) is linear in
the painter, which cannot write an arbitrary function as an expression.

*Read at `21a1a95e4`; reported by review on the PR that carried these files. Tested here:
`AnimatedVectorEvaluationTest` in `:rc-creation-bridge` draws the same vectors both ways and has
AndroidX's `CoreDocument` (`remote-core` 1.0.0-alpha23) paint each document into a context that
records the rotation and the fill colour it draws with, so the painter's expressions are evaluated
by AndroidX. Seven sets of several animators on one property (the second starting during the
first's repeats, while it runs, and after it has ended; none started yet; three listed out of
order; two with the same start delay; one repeating without end and then another) agree at every
start delay, a millisecond either side and between. Nine sets of animators (repeats in both modes, an interpolator on the animator, on a
keyframe, on both and with a factor, three keyframes with a start delay, one animator after a
repeated one) at ten times each agree, and the two examples above have their values. All five
tests failed before the patches.*

### `RemoteLog.kt` has no caller

`common/capture/RemoteLog.kt` declares `RemoteLog` and `LogTodo`, both `internal`. Nothing in
`remote-creation-compose`'s `jvmAndAndroidMain` or `androidMain` calls either.

*Verified with `grep -rn "RemoteLog(\|LogTodo("` over `remote-creation-compose/src` at `21a1a95e4`.*

## How this was checked

- Step 5a and the profile were checked against AndroidX itself, where the earlier steps were
  checked against the fork's own previous bytes (these two change them). `:rc-creation-coexistence-check`'s
  Robolectric tests capture one content with the fork and with AndroidX 1.0.0-alpha23's
  `captureSingleRemoteDocument`, compare the two documents field for field (component numbering
  aside) and play both in AndroidX's `RemoteComposePlayer`, with real `MotionEvent`s for the slider:
  `AndroidxPlayerParityTest` for where things are written, `AndroidxProfileParityTest` for the
  header, the operations, the fallbacks and the refusal under AndroidX's profile and under a
  launcher's. `rc-widget-launcher`'s `RcLauncherWidgetTest` holds `rc-widget-creation`'s
  `RcCaptureProfiles` to its own AndroidX profiles. Not checked: Android 16's platform player (no
  such player runs here; `validateFor(RcTargetProfiles.WIDGETS_V6)` is the nearest thing), and the
  iOS, macOS and browser targets, which this change compiles for (Kotlin metadata and klibs) but
  does not run on a Linux host.
- The relocation and step 3 were checked the way step 2 was. `RemoteDocumentWriter` dumped every
  document it encoded while the JVM tests of the writer, the creation modules, `:rc-creation-bridge`,
  `:rc-player-embedded` and `:rc-player-edit` ran, on a worktree of the commit before and on the
  result: 830 encodes in 49 test classes, 535 distinct documents, the same bytes in the same order in every class (one class, `RcEmbeddedPlayerTest`, re-renders a live session a number of times that varies from run to run of the same commit; its documents are the same set). `diff-upstream.sh` counted the same hunks before and after the relocation, and
  the five that step 3 changed are listed under the plan's table. That the two libraries can share
  a classpath is checked on every build by `:rc-creation-coexistence-check`; against the fork as it
  was before the relocation, the same comparison finds 626 classes in both.
- File and line counts, import counts and the construct lists were recomputed from the upstream
  checkout at `21a1a95e4` with `find`, `grep` and `wc`, not carried over from earlier notes.
- "Could be common code" is established by a build only for what the fork carries: those files
  compile for JVM, Android and Wasm (compiled for this change) and for iOS and macOS (the fork's
  other targets, built on macOS hosts). For upstream files the fork does not carry
  (`capture/headless/` among them) it is a reading of their imports and constructs, not a build.
- The canvas port fixes were each checked in two readers. Captured with the fork and played by
  this repository's CMP player on the JVM, with pixels asserted (`:rc-player-embedded`:
  `RcCanvasUsePaintPlaybackTest`, `RcCanvasPaintAfterBlockPlaybackTest`, `RcCanvasPivotPlaybackTest`,
  `RcCanvasLoopPlaybackTest`, `RcCanvasBitmapSizePlaybackTest`, `RcPatternPaintPlaybackTest`,
  `RcPatternCapturedStatePlaybackTest`, `RcSaveRestoreToCountPlaybackTest`). And read by AndroidX's
  own reader, `remote-core` 1.0.0-alpha23, field by field, or painted by its `CoreDocument` into a
  context that records the calls it makes with the values it resolved, which is how "AndroidX
  draws every iteration of that loop at one place" and "AndroidX draws the shape after a skipped
  block in the wrong colour" were established rather than read (`:rc-creation-bridge`:
  `CanvasPaintReadBackTest`, `CanvasLoopReadBackTest`, `CanvasPivotReadBackTest`,
  `CanvasTextReadBackTest`, `CanvasBitmapReadBackTest`, `LoomPatternsTest`). Each test failed
  before its fix. The recording context draws nothing: what AndroidX's Android paint context does
  with a call (`drawToBitmap` setting the paint's alpha, for one) is read, not run. The documents
  were compared as in step 4: 129 distinct ones before, 121 of them byte for byte the same after,
  and the 8 that changed all use what was fixed.
- The four patches to upstream's own bugs (the animated vector's repeats and interpolators, the
  text measurement's paint, the loop's paint) were each checked by a test that failed before the
  patch, in the same two readers: the CMP player with pixels asserted (`:rc-player-embedded`:
  `RcTextMeasurePaintPlaybackTest`, `RcCanvasLoopPaintPlaybackTest`), and AndroidX's `CoreDocument`
  painting into the recording context, which now also records the text size a call is made with
  (`:rc-creation-bridge`: `CanvasTextMeasureReadBackTest`, `CanvasPaintReadBackTest`,
  `AnimatedVectorEvaluationTest`). The documents were compared as before: 180 distinct ones on the
  commit before, 179 of them byte for byte the same after, and the one that changed measures text
  in a draw.
- Step 4 was checked the way step 2 was (next item), on a worktree of the commit before it and on
  the result: the 107 distinct documents the four modules' JVM tests encoded before are all
  encoded to the same bytes after, and 18 captured for the purpose (the six round-trip samples and
  three documents of canvas drawing, decoration and structure, under both density behaviours)
  match byte for byte. Each writer operation the step added is read back by AndroidX's own reader
  (`remote-core` 1.0.0-alpha23) in `NewWriterOperationsReadBackTest`, `CarriedCreationApiTest` and
  `LoomPatternsTest` of `:rc-creation-bridge`; AndroidX's four expression tests run against the
  fork in `:rc-creation-expression:jvmTest` (`:third-party-remote-creation-compose:jvmTest` then).
- Step 2 was checked by having `RemoteDocumentWriter` dump every document it encoded while the
  JVM tests of `:rc-creation-bridge`, `:rc-player-embedded`, `:rc-player-edit` and
  `:third-party-remote-creation-writer` (now `:rc-creation-writer`) ran, with 22 more captured for the purpose (the round-trip
  samples and two documents of clips, borders, `drawWithContent`, a canvas, text, actions, named
  values, gradients, scrolling and a state layout, under both density behaviours), before and
  after: the same 112 distinct documents, and the 22 byte for byte.
- Step 1 was checked by capturing 20 documents (the round-trip samples of `:rc-creation-bridge`
  and one exercising clips, borders, `drawWithContent`, a canvas and number formatting, under both
  density behaviours) before and after the moves and comparing the bytes: all identical.

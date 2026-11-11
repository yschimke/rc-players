# The creation fork and AndroidX: divergence, realignment, and feedback for upstream

**Status: steps 0 and 1 of the realignment are done; steps 2 to 6 are planned.** This note records
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
why it stays in AndroidX's package is in [RC_CREATION_BRIDGE.md](RC_CREATION_BRIDGE.md).

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
| A Kotlin write side, `third_party/remote-creation-writer` (11 files, 3,760 lines): the `RemoteWriter` interface and `RemoteDocumentWriter`, which encodes the wire format | The only writer is `remote-creation-core`'s `RemoteComposeWriter.java` on top of `remote-core`'s `RemoteComposeBuffer.java`, and the Kotlin is typed against the class: `RemoteDocumentScope.document: RemoteComposeWriter` (`expression/capture/RemoteComposeCreationState.kt:44`). Java does not compile for Wasm or native. |
| Constants and pure helpers re-declared in Kotlin under AndroidX's class names (`Utils`, `RemoteContext`, `AnimatedFloatExpression`, `TextFromFloat`, `CoreText`, `StringUtils`, `CubicEasing`, `MonotonicSpline`, …) | They are static members of `remote-core`'s Java player classes. 21 of the 31 expression files and 53 of the 143 `jvmAndAndroidMain` files import `remote-core`, most of them for constants. |
| A few lines changed in carried files: no `WeakReference` in `RemoteConstantCacheKey`, a stored hash in place of `System.identityHashCode`, `value::class.simpleName` in place of `Enum.declaringJavaClass.name`, `kotlin.concurrent.atomics` in place of `java.util.concurrent.atomic` | JVM-only constructs in the Kotlin itself; the full list is under items 3, 5 and 6 of the feedback below. |
| Its own capture entry point, `captureCommonRemoteDocument`, that takes no `android.content.Context` | Upstream's non-Android capture functions are `internal` (`common/capture/CaptureRemoteDocument.kt:63,84,126,151`); the public ones are in `android/capture/CaptureRemoteDocument.android.kt` and take a `Context`. |
| The Compose UI conversions (`RemoteColor(Color)`, `Dp.asRdp()`, `RemoteTextStyle.fromTextStyle`, `ImageVector.toRemoteImageVector()`, …) compiled for every target | Upstream has them only in `androidMain`, so they exist only in the Android artifact. |
| `Font.variationSettings` through an `expect`/`actual` with reflection on Android | `android/text/RemoteTextStyle.android.kt:103-104` reads `AndroidFont.variationSettings` and `ResourceFont.variationSettings`; neither type is in Compose Multiplatform's common `ui-text`. |
| A pure-Kotlin `RemotePath` | `remote-creation`'s `RemotePath` is an `expect class` whose API names `expect class Path`, `RectF` and `MatrixTransform`, with actuals only for `android.graphics` and `java.awt.geom`, and `RemotePathBase` wraps `remote-core`'s Java class. |
| 18 `androidMain` files not carried: `PendingIntentAction`, `LambdaAction`, `RemoteColor.android.kt`, `CompatAndroidRemotePaint`, `RemoteFormatting.android.kt`, the animated-vector painter and parser, `Profile.android.kt`, the capture and widget classes | They use `android.*`, `androidx.lifecycle`, `androidx.savedstate`, ICU or `XmlPullParser`. |
| `util/DocumentStats.kt` not carried | It walks a `CoreDocument`, the Java reader. |
| A Gradle capability shared with AndroidX on Android | The fork keeps AndroidX's package and class names, so the two cannot be on one classpath. |

## Divergence that is not necessary

The rest of what differs is history: the fork began as a hand-made port with its own file names,
one module where upstream now has three, its own shapes for the creation state, modifiers, actions
and canvas recording, and its own capture loop. None of that is forced by anything upstream, and
all of it makes every refresh from androidx-main harder to do and to review. The plan below removes
it in an order that keeps each step small and keeps the bytes the fork writes unchanged until the
last step.

## Realignment plan

Divergence is measured by
[`third_party/remote-creation-compose/scripts/diff-upstream.sh`](../../third_party/remote-creation-compose/scripts/diff-upstream.sh),
which compares the fork with an androidx checkout file by file after normalising import order and
the names in `scripts/upstream-fqn.tsv` (upstream's Java classes, which the fork's writer declares
in another package).

| | Upstream files | Identical | Differing | Not carried | Carried under another name, split or merged | Fork-only files | Differing hunks |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Before step 1 | 210 | 58 | 115 | 37 | 23 | 19 | 590 |
| After step 1 | 210 | 60 | 114 | 36 | 0 | 19 | 562 |

| Step | What | State |
| --- | --- | --- |
| 0 | Make divergence measurable (`diff-upstream.sh` and its data files). Declare the `androidx.compose.remote:remote-creation-expression` capability, so the fork and that AndroidX artifact cannot both be resolved on Android. | Done |
| 1 | Renames and moves only. Every carried file has upstream's name and sits at upstream's path under `third_party/remote-creation-compose/upstream/<upstream module>/src/<source set>/…`; declarations that upstream keeps in another file moved there; `@file:JvmName`s match upstream's. No behaviour change: the documents written are byte-identical. | Done |
| 2 | Take upstream's visibility and scope types: the restricted-public cache keys and helpers, `RemoteDocumentScope` with `document` and `resolveComponentToken()`, the `RemoteComposeCreationState` / `RemoteComposeCaptureState` split, and `RemoteComposeWriter`'s method names on the fork's writer interface. | Planned |
| 3 | Split the one Gradle module into three that mirror upstream: expression, compose, foundation. | Planned |
| 4 | Carry what is no longer Android-only upstream: `RemoteTextMeasure.kt`, `RemoteProfileCapabilities.kt`, `Hoist`, `RemoteAnimatedVector.kt`, `CaptureUpdateThrottle.kt`, `RemoteLog.kt`, the `RemoteCanvas` API the fork still lacks, and the four JVM tests of `remote-creation-expression`. Fold the fork's `RecordingCanvas` into `RemoteCanvas`, as upstream did. | Planned |
| 5 | Carry upstream's capture loop, `capture/headless/` and `capture/CaptureRemoteDocument.kt`, with the JVM-only constructs replaced, in place of the fork's own. | Planned |
| 6 | The writer under upstream's names, so that the modifier, action and canvas files become upstream's text. Its design depends on AndroidX's answer to items 1, 2 and 8 below. | Planned, not designed |

After step 6 the only differences left should be the replacements for the JVM-only constructs listed in items 3, 5 and 6, and the
additions listed under "Candidate contributions".

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
`third_party/remote-creation-writer/src/commonMain/kotlin/androidx/compose/remote/creation/common/RemoteWriter.kt`
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
`layout.modifiers.DimensionModifierOperation.Type`, `paint.PaintBundle`. Four uses are code, not
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
`third_party/remote-creation-compose/src/commonMain/kotlin/androidx/compose/remote/creation/compose/path/RemotePath.kt`
(150 lines) is such a builder.

## Candidate contributions

Additions the fork carries as marked local patches because something outside it needed them. Each
is small and not specific to rc-players. File paths are under
`third_party/remote-creation-compose/` unless they start with `../`.

| Addition | What it is for | In the fork | Upstream today |
| --- | --- | --- | --- |
| Capture into a caller-supplied writer | A host that wants the operations, not the bytes | `captureCommonRemoteDocumentInto(writer, …)` in `upstream/remote-creation-compose/src/jvmAndAndroidMain/…/capture/CaptureRemoteDocument.kt` | `RemoteComposeCaptureState` has a constructor taking a `RemoteComposeWriter` and `document` is a `var`, but no public capture uses them |
| A composition under a host's `CompositionContext`, re-rendered on demand, reporting when a state its last render read has changed | Live preview and editors: the remote content recomposes with the host's | `src/commonMain/…/capture/RemoteDocumentSession.kt`; `onChangesApplied` on `RemoteComposeApplier` | Free-standing recomposers only (`headlessRenderFlow`, internal); `rememberRemoteDocument` in `android/capture/RemoteComposeCapture.android.kt` is the nearest |
| Component ids that restart with each render of a retained tree | Anything keyed on a component id (editor selection, player state) survives a re-render | `resetComponentIds()` in `upstream/remote-creation-expression/…/capture/RemoteComposeCreationState.kt` | Not present |
| `RemoteModifier.componentId(id)` | A host names a component and maps player geometry back to its own nodes | `src/commonMain/…/modifier/ComponentIdModifier.kt` | `RecordingModifier.componentId(id)` exists in Java (`remote-creation-core/…/modifiers/RecordingModifier.java:146`); the Compose layer calls it only to number a custom component (`common/layout/RemoteCustomComponent.kt:42`), and no `RemoteModifier` exposes it |
| `RemoteBrush.runtimeShader(source, uniforms…)` | AGSL shaders with `RemoteFloat` uniforms | `src/commonMain/…/shaders/RemoteRuntimeShader.kt` | The core reads `ShaderData`; nothing in `remote-creation-compose` writes it |
| `drawMesh2D` on `RemoteCanvas` and `RemoteDrawScope`, channels as `RemoteFloat` expressions over `u`/`v` | Parametric 2D meshes | `src/commonMain/…/layout/RemoteMesh2D.kt`, `state/MeshExpressionLowering.kt` | The core has the experimental operations; `remote-creation-core`'s `dsl/RcMesh.kt` writes them, nothing in `remote-creation-compose` does |
| A free-standing `touchExpression`, and `textMerge` into a reserved id | Drag controls: a slider following the touch position, its value as a label | `../remote-creation-writer/…/RemoteWriter.kt` | In `RemoteComposeBuffer`; not reachable from `remote-creation-compose` |
| Component offscreen buffers and float functions | Custom visibility animations | `../remote-creation-writer/…/RemoteWriter.kt` (`createOffscreenBitmap`, `createFloatFunction`, …) | In `RemoteComposeWriter` and the `RcScope` DSL; not in `remote-creation-compose` |
| `toRemoteString(DecimalFormat)` over a plain Kotlin `DecimalFormat` | Number formatting off Android | `src/commonMain/…/state/RemoteDecimalFormat.kt` | The overloads take `java.text.DecimalFormat` or ICU's, in `android/state/RemoteFormatting.android.kt` |
| PNG encoding supplied by the host (`encodePng: (ImageBitmap) -> ByteArray?`) | Inline bitmaps without a platform service | `captureCommonRemoteDocument` | `RcPlatformServices`, a Java interface, per platform |

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

### An operation outside the declared profile fails without saying which profile is missing

At document API level 7, `DRAW_TO_BITMAP` (190) is in the AndroidX and widgets operation tables
only, `DATA_SHADER` (45) in the AndroidX table only, and the 2D mesh operations in the two
experimental tables only (`remote-core/…/core/Operations.java:459-552`). Writing one under another profile fails with
"Operation 190 is not supported for this version" (`remote-core/…/core/WireBuffer.java:130`), and
reading one fails with "Unknown operation encountered 190"
(`remote-core/…/core/RemoteComposeBuffer.java:1082`). Neither message names a profile, so the
author of a baseline document that draws to an offscreen bitmap is not told that the fix is to
declare `PROFILE_ANDROIDX`. The fork's writer raises the declared profile itself when a document
uses one of these operations.

*The tables and both messages verified by reading. That a document declaring the profile loads in
AndroidX's `CoreDocument` is tested here by the read-back tests in `:rc-creation-bridge`
(`AndroidxReadsVisibilityAnimationTest`, `RuntimeShaderBrushTest`, `Mesh2DAuthoringTest`), which
run against the AndroidX release this repository depends on, not against `21a1a95e4`.*

## How this was checked

- File and line counts, import counts and the construct lists were recomputed from the upstream
  checkout at `21a1a95e4` with `find`, `grep` and `wc`, not carried over from earlier notes.
- "Could be common code" is established by a build only for what the fork carries: those files
  compile for JVM, Android and Wasm (compiled for this change) and for iOS and macOS (the fork's
  other targets, built on macOS hosts). For upstream files the fork does not carry
  (`capture/headless/` among them) it is a reading of their imports and constructs, not a build.
- Step 1 was checked by capturing 20 documents (the round-trip samples of `:rc-creation-bridge`
  and one exercising clips, borders, `drawWithContent`, a canvas and number formatting, under both
  density behaviours) before and after the moves and comparing the bytes: all identical.

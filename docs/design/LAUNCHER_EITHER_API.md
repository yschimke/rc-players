# Launcher widgets from either creation API

`rc-widget-launcher` captures a Remote Compose home-screen widget on the device and publishes it as
`RemoteViews.DrawInstructions`. It used to take AndroidX's creation types only, so an app whose
authoring had moved to rc-players' Compose Multiplatform creation API (`ee.schimke.composeai.rccreation`)
could not capture its launcher widgets through it. Cadence kept a second copy of its swim drawing,
rewritten to AndroidX's package at build time (`generateAndroidxBinding`), for that reason alone.
This page records how the module is split so a launcher widget can be authored through either API,
and why.

## What is API-neutral

Everything after the capture works on the document's bytes, an `RcDocument`, or an `RcTargetProfile`
(`rc-player-edit`), and none of it names a creation API:

| Part | Works on | Where it lives now |
| --- | --- | --- |
| `platformWidgetDocument` (Android 16 pass: resources lifted, component values placed, wire minor version) | bytes, `androidx.compose.remote.core` operations (the wire format, not creation) | `rc-widget-launcher-core` |
| bake, image embedding, action collection after a capture | `RcDocument`, `RcWidgetActionIds` | core: `finishLauncherCapture` |
| `RcLauncherWidgets` minus `profileFor`: `remoteViews`, `forDisplay` (recovery for the platform player), `forNotification`, `violations`, `actionBroadcast`, `actionActivity`, `tapOf`, `targetFor` | bytes, `RcTargetProfile` | core: `RcPlatformWidgets` |
| `RcWidgetSizing`, `RcLauncherGridBounds`, `RcWidgetImages` | `AppWidgetManager`, bitmaps | core |
| `RcLauncherWidgetPreview`, `RcLauncherGrid`, `RcAdaptiveRemoteDocumentPlayer` | `RemoteViews`, AndroidX player view | core |
| The Robolectric / API 36 and 37 platform-player lane | `RemoteViews` | test setup shared by the three modules (`gradle/robolectric-platforms.gradle.kts`) |

What is tied to a creation API is two things only: the **capture entry point** (compose the content
under that API's `Provide…` locals, call its capture function with a `Profile`) and the **`Profile`
values** (`widgetsProfile`, `systemThemedWidgetsProfile`, and `RcLauncherWidgets.profileFor`, which
return AndroidX's `Profile`).

One type crossed the line without belonging to either side: `RcWidgetCaptureSize`, the slot a capture
is for. `RcWidgetSizing.slotOf` returns it and both entry points take it. It is now one class in
`rc-widget-core` (`ee.schimke.composeai.rcwidget.RcWidgetCaptureSize`); each creation binding names it
with a typealias in its own package, so `…rcwidget.creation.RcWidgetCaptureSize` and
`…rcwidget.creation.androidx.RcWidgetCaptureSize` both still compile.

## The split

```
before:  rc-widget-core ─▶ rc-widget-creation-androidx ─▶ rc-widget-launcher

after:   rc-player-edit ─▶ rc-widget-core ─▶ rc-widget-launcher-core            (no creation API)
                                               ▲                  ▲
         rc-widget-creation-androidx ─▶ rc-widget-launcher        rc-widget-launcher-cmp ◀─ rc-widget-creation
         (AndroidX creation)                                                                (rc-players creation)
```

| Module | Artifact | Holds | Creation API on its classpath |
| --- | --- | --- | --- |
| `rc-widget/launcher-core` (new) | `rc-widget-launcher-core` | everything in the table above | none |
| `rc-widget/launcher` | `rc-widget-launcher` (unchanged) | `captureLauncherWidget` (AndroidX), `widgetsProfile`, `systemThemedWidgetsProfile`, `RcLauncherWidgets` | AndroidX |
| `rc-widget/launcher-cmp` (new) | `rc-widget-launcher-cmp` | `captureLauncherWidget` (rc-players), `launcherCaptureProfile` | rc-players |

- **`rc-widget-launcher` keeps its artifact, package and every name an app calls today.** It
  `api`s the core, so one dependency still gives an existing app everything. `RcLauncherWidgets` is
  kept whole, as a delegating façade over core's `RcPlatformWidgets` plus the AndroidX `profileFor`;
  `platformWidgetDocument`, `RcWidgetSizing`, `RcLauncherWidgetCapture`, the preview and the player
  stay in `ee.schimke.composeai.rcwidget.launcher`, now from the core jar.
- **`rc-widget-launcher-cmp.captureLauncherWidget`** is the same function in package
  `ee.schimke.composeai.rcwidget.launcher.cmp`. It captures through
  `captureCommonRemoteDocument(profile = RcCaptureProfiles.…)` and needs no `Context` (an overload
  with one reads like the AndroidX call). It composes the content under the same locals
  (`ProvideRcWidgetCaptureSize`, `ProvideRcWidgetActions`, `ProvideRcExpressionNames`) and hands the
  bytes to the same `finishLauncherCapture`.
- **Same document, whichever API.** Both entry points end in `finishLauncherCapture`, and everything
  after it is one code path. `EitherApiParityTest` holds the two to it (below).

### Why the neutral object has a new name

`RcLauncherWidgets` cannot move to the core under its own name: its `profileFor` returns an AndroidX
`Profile`, and an `object` cannot be split across artifacts (an extension would need an import at
every call site, which Cadence's `RcLauncherWidgets.profileFor()` does not have). So the core's
object is `RcPlatformWidgets`, and `RcLauncherWidgets` stays in the façade as a thin delegate.
An app on the CMP entry point uses `RcPlatformWidgets` (same member names) and
`launcherCaptureProfile()`.

### Alternatives rejected

- **One module with both creation stacks.** An AndroidX consumer would resolve rc-players' creation
  libraries and a CMP consumer AndroidX's `remote-creation*`, the one thing the split is for. The two
  stacks are built to coexist (`:rc-creation-coexistence-check`), so this would work, but nothing
  would stop an app that chose one from shipping both.
- **`compileOnly` for both, the app picks one.** Neither API would be on the compile classpath of
  the module's own tests or of a consumer that forgot one; a missing class is a runtime crash on a
  device, found by the launcher. The public API would still name both stacks' types.
- **`expect`/`actual`.** The two creation APIs are different packages of the same shape, not two
  platforms of one API; an `expect` would have to be written in terms of types neither has.
- **A CMP module that depends on `rc-widget-launcher`.** The AndroidX creation libraries would be on
  its runtime classpath through the façade's `api`, and `profileFor` would be an AndroidX type in its
  public API.
- **The CMP entry point inside the core.** The core would then carry `rc-widget-creation`, and the
  façade, which `api`s the core, would hand every AndroidX app the CMP creation libraries.
- **A deprecated `RcLauncherWidgets`.** Not done; see "Decisions".

## Equivalence of the profiles

`theCmpCaptureProfilesAreTheAndroidxOnes` (in `RcLauncherWidgetTest`) stays where it is: it needs
both stacks, so it lives in the façade's tests, which hold `rc-widget-creation` as a test
dependency. It compares `widgetsProfile` to `RcCaptureProfiles.launcherWidgets`, and
`systemThemedWidgetsProfile` to `systemThemedLauncherWidgets`: API level, operation-profile bits and
the operation set. `EitherApiParityTest.theCaptureProfilesAreForTheSameLauncher` adds the one the
entry points use, `launcherCaptureProfile(sdk)` against `RcLauncherWidgets.profileFor(sdk)`, for 36
and 37.

## What the tests prove

| Test | Module | Proves |
| --- | --- | --- |
| `RcLauncherWidgetTest`, `PlatformPlayerComponentValueTest`, `PlatformPlayerAdaptedDocumentTest` | `rc-widget-launcher` | unchanged: the AndroidX path, the `ComponentValue`-for-canvas fix (rc-players#875) and `adaptTo` parity (#876) on API 36 and 37 |
| `EitherApiParityTest` | `rc-widget-launcher` | one widget (canvas that reads its width, border, text, paint, size breakpoint, numeric tap) written against each API, captured through each entry point, goes through the core: the same layouts, modifiers and draws in the same order, the same texts and tap ids, no violations for the launcher, and **the same pixels** on the platform widget player of API 36 and 37 |
| `RcLauncherCmpCaptureTest` | `rc-widget-launcher-cmp` | the CMP capture needs no `Context`, collects taps, picks its layout for the slot, and its profile leaves out what each launcher cannot read |
| `RcPlatformWidgetsTest` | `rc-widget-launcher-core` | the core stands alone: targets, taps, sizing |
| `verifyCreationApiIsolation` | all three | the dependency isolation below |

What the parity test deliberately does not compare: the two writers differ in bytes that are not
drawn. AndroidX's `clickable` adds accessibility semantics, rc-players' border writes the box's
`ComponentValue`s, text data is declared in a different order, and AndroidX declares the font axis
name `wght` as string data. The structure is compared with those left out, and the pixels in full.

## Dependency isolation

`./gradlew :<module>:verifyCreationApiIsolation` (part of `check`, `gradle/creation-isolation.gradle.kts`)
resolves the Android compile and runtime classpaths a consumer gets and fails on the other API's
libraries (`androidx.compose.remote:remote-creation*`, `androidx.compose.remote.foundation`,
`rc-widget-creation-androidx` for the CMP side; `:rc-creation-*`, `rc-widget-creation` for the
AndroidX side). It logs what it found:

```
:rc-widget-launcher-core creation libraries on its classpath: none
:rc-widget-launcher-cmp  creation libraries on its classpath: cmp: :rc-creation-compose, :rc-creation-expression,
                                                              :rc-creation-writer, :rc-widget-creation
:rc-widget-launcher      creation libraries on its classpath: androidx: :rc-widget-creation-androidx,
                                                              remote-creation, remote-creation-android, remote-creation-compose,
                                                              remote-creation-core, foundation
```

`androidx.compose.remote:remote-core` and the player libraries are on all three: they are the wire
format and the platform's player, not a creation API. The test classpath of `rc-widget-launcher` has
both (the parity test needs both) and is not checked.

## Public surface, before and after

| Before | After |
| --- | --- |
| `rc-widget-launcher`: `captureLauncherWidget`, `RcLauncherWidgetCapture`, `widgetsProfile`, `systemThemedWidgetsProfile`, `platformWidgetDocument`, `RcLauncherWidgets`, `RcWidgetSizing`, `RcLauncherGridBounds`, `RcWidgetImages`, `RcLauncherWidgetPreview`, `RcLauncherGrid`, `inflateWidgetPreview`, `RcAdaptiveRemoteDocumentPlayer` | all of them, same artifact, same package and names. The profiles, `captureLauncherWidget` and `RcLauncherWidgets` are in the artifact itself; the rest arrive from `rc-widget-launcher-core` through `api` |
| | new: `rc-widget-launcher-core`: `RcPlatformWidgets`, `finishLauncherCapture`, `RcWidgetSizing.dpToPx` |
| | new: `rc-widget-launcher-cmp`: `captureLauncherWidget` (×2), `launcherCaptureProfile` |
| `…rcwidget.creation.androidx.RcWidgetCaptureSize`, `…rcwidget.creation.RcWidgetCaptureSize`: classes | typealiases of `…rcwidget.RcWidgetCaptureSize` (`rc-widget-core`) |

**Source compatible, not binary compatible.** The classes moved jars and `RcWidgetCaptureSize` moved
packages behind an alias, so an app recompiles against the new artifacts; mixed versions of the
launcher modules on one classpath are not supported (the BOM pins them together). The ABI dumps in
each touched module record the change.

## Consumers checked

Read, not built (the repositories are not modified): every call below compiles against the façade
because the name, package and parameter list are unchanged.

- **Cadence** (`shared`, `composeApp`): `captureLauncherWidget(context, slot) { … }`,
  `captureLauncherWidget(context, RcWidgetCaptureSize(w, h)) { … }`, `RcLauncherWidgetCapture`,
  `RcWidgetSizing.slotOf`, `RcLauncherWidgets.{MIN_HOST_API, remoteViews, actionActivity,
  actionBroadcast, ACTION_WIDGET_ACTION, EXTRA_ACTION_PAYLOAD, notificationRemoteViews, targetFor,
  profileFor}`, `platformWidgetDocument(document, RcLauncherWidgets.targetFor())`,
  `RcAdaptiveRemoteDocumentPlayer`. It imports `RcWidgetCaptureSize` from `…creation.androidx`,
  which is now an alias.
- **Livery**: `livery-launcher-widgets` (`RcWidgetSizing.slotOf`, `RcLauncherWidgets.{remoteViews,
  actionBroadcast, tapOf, ACTION_WIDGET_ACTION, MIN_HOST_API, isSupported}`), `livery-notifications`
  (`notificationRemoteViews`, `actionBroadcast`, `tapOf`), `livery-verify`
  (`RcLauncherWidgets.remoteViews`, `targetFor`, `platformWidgetDocument(document, target)`),
  `livery-capture` (`widgetsProfile`). `livery-capture-cmp` takes only `rc-widget-creation`'s profiles.
  Livery has both creation stacks in one app, so `rc-widget-launcher-cmp` and `rc-widget-launcher`
  coexisting is the case that matters: the two `captureLauncherWidget`s are in different packages.
- **Home Assistant** (`homeassistant-remotecompose`): does not depend on `rc-widget-launcher`. It
  has its own `widgetsProfile`, `platformWidgetDocument` and `captureLauncherWidgetDocument` in
  `rc-converter` (`ee.schimke.ha.rc`). Unaffected; it could later take the core for
  `platformWidgetDocument`, which has no creation API in it.

## Cadence: dropping `generateAndroidxBinding`

For the launcher surfaces, three edits, none to the drawing:

```kotlin
// libs.versions.toml
rc-widget-launcher     = { module = "ee.schimke.composeai:rc-widget-launcher" }      // before
rc-widget-launcher-cmp = { module = "ee.schimke.composeai:rc-widget-launcher-cmp" }  // after (the BOM pins it)

// shared/…/PodcastSyncWidget.kt, CloudSyncWidget.kt, *HomeCard.android.kt
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget            // before
import ee.schimke.composeai.rcwidget.launcher.cmp.captureLauncherWidget        // after
import ee.schimke.composeai.rcwidget.creation.androidx.rcWidgetHostAction      // before
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction               // after
import ee.schimke.cadence.swim.androidx.SwimmingPool                           // before (PodcastSyncWidgetContent.kt)
import ee.schimke.cadence.swim.SwimmingPool                                    // after
// RcLauncherWidgets.remoteViews / actionActivity / actionBroadcast / tapOf: unchanged.
```

and the widget contents (`PodcastSyncWidgetContent`, `CloudSyncWidgetContent`) take their
`RemoteComposable` building blocks from `ee.schimke.composeai.rccreation.compose.*` like the rest of
the CMP authoring. `RcLauncherWidgets.profileFor()` in the previews becomes
`launcherCaptureProfile()` where the preview is a CMP one.

`generateAndroidxBinding` stays while anything else needs AndroidX types. In Cadence that is the
**Wear widgets** (`wearApp`, Glance Wear takes AndroidX `RemoteComposable`), which import
`ee.schimke.cadence.swim.androidx.*`, and the AndroidX tooling previews (`RemoteContentPreview`). So
the launcher change removes the copy from the phone, and the task goes when `rc-widget-wear` has a CMP
entry point too (open, below).

## Decisions

1. **Names of the two new artifacts** (`rc-widget-launcher-core`, `rc-widget-launcher-cmp`) and of
   the neutral object (`RcPlatformWidgets`). Easy to change before the first release; both are in
   `PublishedArtifactIds`, `root-tasks.gradle.kts` and the BOM through that table.
2. **Whether to deprecate the AndroidX-flavoured `RcLauncherWidgets` and `captureLauncherWidget`.**
   Not deprecated: the AndroidX path is a supported way to author, and Wear and Glance still need it.
3. **A CMP entry point for `rc-widget-wear`**, which would let a Cadence-like app drop the AndroidX
   copy altogether. Not part of this change.

# Migrating to `rc-creation-*` and `ee.schimke.composeai.rccreation`

rc-players' Compose Multiplatform creation API moved out of AndroidX's packages and into four
modules under new coordinates. This is a breaking change for anything that compiled against
`third-party-remote-creation-compose`, `third-party-remote-creation-writer`, or the widget creation
helpers. It is made once: the packages and coordinates below are the ones that stay.

Why: [`design/RC_CREATION_BRIDGE.md`](design/RC_CREATION_BRIDGE.md), "Why the fork is in
rc-players' package". In short, AndroidX's `androidx.compose.remote.creation.compose` is on
Android today and, on androidx-main, builds for the JVM too (its published alphas are still
Android-only), and a library that declares the same classes cannot be on a classpath with it.

Two different things are called "the creation API", and which one a codebase is on decides how much
of this page applies.

- **From rc-players' own fork, 2.30.0 to 2.33.0** (`third-party-remote-creation-compose`): the move
  to 3.0.0 is a rename. Every class, function and parameter is where it was, under another package
  prefix, and documents written before and after it are the same bytes. Sections 1 to 6.
- **From AndroidX `remote-creation-compose` 1.0.0-alpha23, or from the fork at 2.29.0 or earlier**
  (whose API was alpha23's): the fork follows androidx-main, which has moved on from alpha23. The
  rename is the smaller part; section 7 lists the API changes.

Releases after 3.0.0 changed what a capture writes (where declarations go, and the capture
profile): section 8.

## 1. Coordinates

The group is still `ee.schimke.composeai`, and `rc-players-bom` pins all of these.

| Before | After |
| --- | --- |
| `third-party-remote-creation-writer` | `rc-creation-writer` |
| `third-party-remote-creation-compose` | `rc-creation-compose` (brings `rc-creation-expression` and `rc-creation-writer`) |
| `third-party-remote-creation-compose`, for `RemoteBasicText`, `RemoteBasicImage`, `RemoteBasicIcon` or `RemoteSpacer` | add `rc-creation-foundation` |
| `rc-creation-bridge`, `rc-player-embedded` | unchanged; they depend on `rc-creation-compose` |

```kotlin
dependencies {
  implementation(platform("ee.schimke.composeai:rc-players-bom:<version>"))
  implementation("ee.schimke.composeai:rc-creation-compose")
  implementation("ee.schimke.composeai:rc-creation-foundation") // if you use RemoteBasicText & co.
}
```

`rc-creation-expression` holds the remote state types (`RemoteFloat`, `RemoteColor`,
`RemoteState`, …), as AndroidX's `remote-creation-expression` does. `rc-creation-compose` exposes
it as an `api` dependency, so name it yourself only in a module that needs the state types and
nothing else.

The old coordinates are not published from this version on. There is no relocation POM and no stub
artifact: a build that still names `third-party-remote-creation-compose` keeps resolving the release
it names, in AndroidX's package, until its dependency is changed. `rc-players-bom` 3.0.0 and later
do not list `third-party-remote-creation-compose` or `third-party-remote-creation-writer` either, so
a build that takes the players from the BOM and stays on the old creation coordinates has to give
those two a version itself (2.33.0 is the last; 2.29.0 the last with alpha23's API). `rc-creation-bridge` no longer
brings the foundation components with it; add `rc-creation-foundation` if you used them through it.

## 2. Packages

| Before | After |
| --- | --- |
| `androidx.compose.remote.creation.compose` and every subpackage | `ee.schimke.composeai.rccreation.compose` |
| `androidx.compose.remote.foundation` and every subpackage | `ee.schimke.composeai.rccreation.foundation` |
| `androidx.compose.remote.creation.common` (the writer) | `ee.schimke.composeai.rccreation.common` |
| `ee.schimke.composeai.rccreation.bridge` | unchanged |

Run this over the sources that used rc-players' creation API (Kotlin sources, and any consumer
ProGuard or R8 rules that name these packages):

```sh
git ls-files -z '*.kt' '*.kts' '*.pro' | xargs -0 perl -pi -e '
  s/\bandroidx\.compose\.remote\.creation\.compose\b/ee.schimke.composeai.rccreation.compose/g;
  s/\bandroidx\.compose\.remote\.creation\.common\b/ee.schimke.composeai.rccreation.common/g;
  s/\bandroidx\.compose\.remote\.foundation(?=\.)/ee.schimke.composeai.rccreation.foundation/g;
  s/\bandroidx\.compose\.remote\.creation\.RemotePath\b/ee.schimke.composeai.rccreation.compose.path.RemotePath/g;
'
```

The last rule is for code that came from AndroidX's own artifacts, where `RemotePath` is
`androidx.compose.remote.creation.RemotePath` (the `remote-creation` module); the fork's was always
in `…creation.compose.path`, which the first rule covers.

Three cautions.

- **Run it only where the code is meant for rc-players' API.** The same package names are AndroidX's
  own. A module that really does use AndroidX's `androidx.compose.remote:remote-creation-compose`
  (a launcher widget, Glance Wear, Wear `remote-material3`) must keep its imports; limit the
  `git ls-files` patterns to the directories that move.
- **The third rule only rewrites the package where a dot follows it**
  (`androidx.compose.remote.foundation.text.RemoteBasicText`), so that it leaves the Maven
  coordinate `androidx.compose.remote.foundation:foundation` in a build file alone.
- **Nothing else under `androidx.compose.remote` moves.** `androidx.compose.remote.core.*`,
  `androidx.compose.remote.creation.profile.*` and the rest of `androidx.compose.remote.creation.*`
  are AndroidX's Java modules; rc-players never declared them.

Afterwards, re-sort imports with your formatter (`ee.` sorts after `androidx.`).

## 3. Using both libraries in one app

This is what the move is for. `rc-creation-compose` and AndroidX's `remote-creation-compose` can
now be dependencies of the same module, on Android and on the JVM. Remove any workaround that kept
them apart:

- `capabilitiesResolution` rules that selected AndroidX's `remote-creation-compose`,
  `remote-creation-expression` or `foundation` over rc-players' fork;
- `exclude` rules or `compileOnly` tricks that kept the fork off an Android classpath.

The two are different classes in different packages (and, against a released AndroidX alpha, not
quite the same API; section 7), so their types do not mix: an AndroidX `RemoteModifier` is not an
rc-players `RemoteModifier`, and `@RemoteComposable` content written for
one is not content for the other. Import with an alias where a file needs both:

```kotlin
import androidx.compose.remote.creation.compose.state.RemoteFloat as AndroidxRemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
```

A component library compiled against AndroidX's API (Wear `remote-material3`) works with AndroidX's
creation only. To use such components on the CMP API, build the library's source against
`rc-creation-compose` with the package rewrite above.

## 4. Widgets: two bindings

`rc-widget-creation` used to compile one source against AndroidX on Android and against the fork
everywhere else. It is now two artifacts.

| Artifact | Targets | Creation API | Package |
| --- | --- | --- | --- |
| `rc-widget-creation` | JVM, Android, iOS, macOS, wasmJs | `rc-creation-compose` | `ee.schimke.composeai.rcwidget.creation` |
| `rc-widget-creation-androidx` | Android | AndroidX `remote-creation-compose` | `ee.schimke.composeai.rcwidget.creation.androidx` |

The helpers are the same in both (`ProvideRcExpressionNames`, `RcSizeBreakpoint`,
`rcWidgetHostAction`, the Wear containers, …); the second is generated from the first.

- **On Android, `rc-widget-creation` changed what it is.** It was AndroidX-typed there and is
  `rc-creation-compose`-typed now. Android code that passes its helpers AndroidX content, or uses
  them inside a launcher or Glance Wear capture, switches to `rc-widget-creation-androidx` and
  changes its imports:

  ```sh
  perl -pi -e 's/\bee\.schimke\.composeai\.rcwidget\.creation\.(?!androidx\b)/ee.schimke.composeai.rcwidget.creation.androidx./g' <files>
  ```

- **`rc-widget-launcher` and `rc-widget-wear`** depend on `rc-widget-creation-androidx` and take
  AndroidX `@RemoteComposable` content, as before. Types of theirs that came from the creation
  helpers (`RcWidgetCaptureSize`, `RcWearBackground`, `RcWearLevel`, …) are now the
  `…creation.androidx` ones.
- **The AndroidX capture profiles** `androidXExperimental` and `androidXExperimentalWrap` are in
  `rc-widget-creation-androidx` (`ee.schimke.composeai.rcwidget.creation.androidx`). Since the
  release after 3.0.0, `rc-widget-creation` has the same two and the launcher and Wear ones for
  rc-players' API, as `RcCaptureProfiles` (section 8).
- **Off Android** (a design server on the JVM, a browser preview) nothing changes but the creation
  package under the helpers: keep `rc-widget-creation`.

An app can hold both bindings: CMP-typed cards played in-app by `rc-player-embedded`, and
AndroidX-typed launcher or Wear widgets.

## 5. Generated Kotlin: `toRemoteComposeKotlin`

`rc-player-edit`'s decompiler writes rc-players' imports by default now. Ask for AndroidX's when
the generated file is for a module on AndroidX's creation API:

```kotlin
document.toRemoteComposeKotlin(
  RcKotlinOptions(functionName = "Card", creationApi = RcKotlinCreationApi.AndroidX)
)
```

`RcKotlinCreationApi.RcCreation` (the default) imports `ee.schimke.composeai.rccreation.compose.…`;
`RcKotlinCreationApi.AndroidX` imports `androidx.compose.remote.creation.compose.…`. The file is
otherwise identical. From the desktop viewer: `remote-compose-player --to-kotlin --androidx in.rc`.

The generator writes the API as androidx-main has it. A released AndroidX artifact can be behind
that in places, and `RemoteModifier.componentId`, which the source uses for a component the
document gives an explicit id, exists only in `rc-creation-compose`.

Source generated before this change imports `androidx.compose.remote.creation.compose`; it
compiles against AndroidX as it is, and against `rc-creation-compose` after the rewrite in
section 2.

## 6. Checklist (from the fork, 2.30.0 or later)

1. Replace the two old coordinates (section 1); add `rc-creation-foundation` if needed.
2. Rewrite packages in the sources that use rc-players' creation API (section 2).
3. Remove capability and exclusion workarounds (section 3).
4. Android widget code: move to `rc-widget-creation-androidx` and its package (section 4).
5. Pass `creationApi` where generated source targets AndroidX (section 5).
6. Build. A leftover shows as an unresolved `androidx.compose.remote.creation.compose` import in a
   module without AndroidX's artifact, or as a type mismatch between the two `Remote*` types of the
   same simple name.

## 7. Coming from AndroidX 1.0.0-alpha23, or from the fork at 2.29.0 or earlier

`rc-creation-compose` carries androidx-main's sources (`21a1a95e4`, which AndroidX will release as
alpha24), not alpha23's. After the package rewrite of section 2, this is what does not compile, and
what to write instead. Each row was checked against alpha23's published classes and the fork's
sources.

| alpha23 | `rc-creation-compose` |
| --- | --- |
| `RemoteText(fontWeight: FontWeight?, fontStyle: FontStyle?, textAlign: TextAlign?, overflow: TextOverflow, …)` taking Compose UI's text types (alongside overloads taking `Remote*`) | Only the `Remote*` overloads: `RemoteFontWeight`, `RemoteFontStyle`, `RemoteTextAlign`, `RemoteTextOverflow`, `RemoteTextDecoration`, `RemoteFontVariation`. The same in `RemoteTextStyle`. |
| `RemoteImage(contentScale: ContentScale)` | `RemoteContentScale` |
| `RemotePaint.style: PaintingStyle`, `strokeCap: StrokeCap`, and a brush's `tileMode: TileMode` | `RemotePaintingStyle`, `RemoteStrokeCap`, `RemoteTileMode` |
| `clickable(…, role: Role?)`, `semantics { role = Role.Button }` | `RemoteRole` |
| `RemoteOffset(Float, Float)`, `RemoteOffset(Float, RemoteFloat)`, `RemoteOffset(RemoteFloat, Float)` | `RemoteOffset(RemoteFloat, RemoteFloat)` only: `RemoteOffset(4f.rf, 8f.rf)` |
| `RemoteModifier.weight(Float)` in a row or column | `weight(RemoteFloat)`: `weight(1f.rf)` |
| `valueChange(state, value)` | `valueChangeAction(state, value)` |
| `tween(from, to, fraction)` of two colours | `lerp(from, to, fraction)` |
| `rememberMutableRemoteFloat(…)`, `rememberMutableRemoteInt(…)`, `rememberMutableRemoteString(…)` and the other `rememberMutableRemote*` | Removed, but for `rememberMutableRemoteImageBitmap`. A state an action changes is a named one (`rememberNamedRemoteFloat`, `rememberNamedRemoteInt`, …, or `RemoteFloat.createNamedRemoteFloat`); the `MutableRemote*` constructors are restricted to the library group. |
| `RemoteFloat.createNamedRemoteFloatExpression(name, domain) { componentWidth() }` | `RemoteFloat.createNamedRemoteFloat(name, remoteComponent().width, domain)`: the size is read from a component (`remoteComponent()`), not from a `RemoteFloatContext` lambda. |
| `import androidx.compose.remote.creation.RemotePath` (the `remote-creation` module) | `ee.schimke.composeai.rccreation.compose.path.RemotePath`; the rewrite of section 2 moves the import. It is a pure-Kotlin path: no `android.graphics.Path` or `java.awt.geom` type in its API. |
| `RemoteFloat.toRemoteString(java.text.DecimalFormat)` and `toRemoteString(android.icu.text.DecimalFormat)` | `toRemoteString(format: ee.schimke.composeai.rccreation.compose.state.DecimalFormat)`, the fork's own platform-neutral class of that name (separators, grouping, digits, `RoundingMode`). The Java and ICU ones are Android and JVM types and are not in common code. |
| `clickable(action)` writes an `AccessibilitySemantics` after the click, whether a role is given or not | Only for a `role`, as androidx-main. |
| A text that names no alignment is written `Left` | `Start` |
| `captureSingleRemoteDocument(context, creationDisplayInfo, profile)` | `captureCommonRemoteDocument(creationDisplayInfo, profile = …)`, which takes no `Context` and returns the bytes. Section 8 has the profile. |

What is not there at all: `PendingIntentAction` and `LambdaAction` (Android types; use `hostAction`
and run it in the host), `CompatAndroidRemotePaint`, the animated-vector resource parser, and Wear
`remote-material3`, which is compiled against AndroidX's API (section 3).

A hint for a large port: the compile errors are almost all of the first six rows, and are mechanical.
What needs thought is the last row and section 8.

## 8. Captures after 3.0.0: where declarations go, and profiles

3.0.0's writer wrote every declaration (a float expression, text made from a float, a `TextMerge`,
a component's measured size) ahead of the layout, wherever it was made, and held a layout's own
operations back until the end of the capture. AndroidX's players read meaning from position: a
touch expression moves only for touches on the component it is written in, text made from its value
follows it only when written after it there, a measured size belongs to the component its
`ComponentValue` is written in, and an expression of that size is evaluated with the canvas that
holds it. A document the CMP player played could behave differently in AndroidX's (a slider that
snapped back on release).

From the release after 3.0.0 the writer writes as AndroidX's does, and the capture takes a profile.
The documents change; the API changes as follows.

### Declarations are written where they are made

Nothing to change in `@RemoteComposable` content. Code that calls the writer itself should know:

- **`RemoteWriter` writes an operation where it is called**, including from a draw lambda
  (`remoteComposeCreationState.document.addTouchExpression(…)` lands in the canvas being drawn).
- **`RemoteWriter.beginGlobal()` / `endGlobal()`** (AndroidX's names) move a section ahead of the
  root. `defineVisibilityAnimation` uses it.
- **The writer no longer shares expressions by value.** `floatExpression`, `integerExpression`,
  `addInteger`, `addFloatArray`, `addList`, `textLength` and `textLookup` declare each time they are
  called, as AndroidX's do; text, bitmaps and paths are still shared. The remote state types above
  the writer do their own sharing, scoped to a canvas, so this matters only to direct callers.
- **One thing is still moved for you.** AndroidX's player drops some kinds of declaration when it
  finds them among a layout's operations (a named variable, an integer or long constant, path data,
  a float list and a few more: anything but text, bitmaps and its `ComponentData` operations).
  Written in a layout, those go ahead of the root, unless they read a value a component declared
  in place; in a canvas they stay where they are.
- **A named value is one variable.** `addNamedInt`, `addNamedFloat`, `addNamedString`,
  `addNamedLong` and a named colour answer the id the name already has, and declare it ahead of the
  root the first time, wherever they are called.
- **An image carried in the document is `TYPE_PNG_8888`** (0), as AndroidX's writer stores one.
  3.0.0 wrote `TYPE_PNG` (1), which AndroidX's players refuse for inline data, so the image was
  missing there. A reader that told an inline image by its type should tell it by its encoding.

### `Profile`

A capture is for a player. `Profile` says which: the document API level, the `RcProfiles` bits, the
operations the player reads, and the header. It is AndroidX's class of that name
(`androidx.compose.remote.creation.profile.Profile`) in
`ee.schimke.composeai.rccreation.common`, with `RcProfiles`, `RcPlatformProfiles`, `Header` and
`HTag` beside it.

```kotlin
import ee.schimke.composeai.rccreation.common.HTag
import ee.schimke.composeai.rccreation.common.Header
import ee.schimke.composeai.rccreation.common.RcPlatformProfiles
import ee.schimke.composeai.rccreation.common.RcProfiles
import ee.schimke.composeai.rccreation.compose.capture.Profile

// AndroidX's player. The default.
captureCommonRemoteDocument(display, profile = RcPlatformProfiles.ANDROIDX) { Card() }

// With AndroidX's experimental operations (a flow row, a pattern, a 2D mesh, a custom component),
// and a header that turns paint measuring off.
val app =
  Profile(
    profileFlags = RcProfiles.PROFILE_ANDROIDX or RcProfiles.PROFILE_EXPERIMENTAL,
    headerTags = listOf(HTag(Header.FEATURE_PAINT_MEASURE, 0)),
  )
```

Under a profile the writer does what AndroidX's does:

- it **refuses** an operation the profile does not support (`RuntimeException: Operation 240 is not
  supported for this version`);
- it **falls back** where AndroidX's has a fallback: text is a `TextLayout` when the profile has no
  `CORE_TEXT`; up to API level 7 a canvas's drawing is in a `CanvasContent` and a border is the
  legacy one;
- it **writes the header** the profile asks for: `DOC_PROFILES`, the legacy header at API level 6,
  and any `headerTags` (`FEATURE_PAINT_MEASURE`, `FEATURE_DISALLOW_INTERCEPT_TOUCH`, …);
- `currentProfileSupports…()` answers from the same operations.

What changes for a caller of 3.0.0:

| 3.0.0 | Now |
| --- | --- |
| `captureCommonRemoteDocument(display) { … }` wrote any operation, declared no profile (but what its operations needed), and answered every `currentProfileSupports…()` with `false` | The default profile is `RcPlatformProfiles.ANDROIDX`: the header declares it, themed colours, touch modifiers and path clips are written, not their fallbacks, and an experimental operation is refused. |
| A flow row, `definePattern`/`forEach`, `drawMesh2D` or a `RemoteInput` in a default capture | Pass a profile with `PROFILE_EXPERIMENTAL`. `RcEmbeddedPlayer` captures under one (`RcEmbeddedPlayerProfile`). |
| `supportedOperations = setOf(…)` on `captureCommonRemoteDocument`, `captureCommonRemoteDocumentInto` and `RemoteDocumentSession` | Deprecated, and it behaves as it did. Replace with `profile = Profile(supportedOperations = intSetOf(…))`, or a named profile. |
| `LocalRemoteComposeCreationState.current.supportedOperations` | `.profile.supportedOperations` |
| `RemoteDocumentWriter(width, height, densityBehavior, profiles)` | Unchanged: a writer for no profile, which writes anything. `RemoteDocumentWriter(creationDisplayInfo, null, profile)` and `RemoteDocumentWriter(profile, vararg tags)` are AndroidX's constructors, and `profile.create(…)` calls the profile's factory. |

For widgets, `rc-widget-creation` has the profiles `rc-widget-launcher` and
`rc-widget-creation-androidx` have for AndroidX's API, on rc-players':

| `RcCaptureProfiles` | The AndroidX-API profile it is |
| --- | --- |
| `androidXExperimental`, `androidXExperimentalWrap` | `rc-widget-creation-androidx`'s of the same names |
| `launcherWidgets` | `rc-widget-launcher`'s `widgetsProfile` (Android 16 and later) |
| `systemThemedLauncherWidgets` | `systemThemedWidgetsProfile` (API 37 and later) |
| `wearWidgets` | Glance Wear's allow-list (`RcTargetProfiles.WEAR_WIDGETS`) |
| `forTarget(RcWidgetTarget)`, `RcTargetProfile.toCaptureProfile()` | Any player `rc-player-edit` describes |

A capture under `launcherWidgets` holds what a launcher reads, text as `TextLayout` included, so it
needs no `adaptTo(…)` to rewrite it and no header fixed up afterwards. Android 16 still wants
`rc-widget-launcher`'s `platformWidgetDocument` over the bytes (its header version, and where it
keeps resources and measured sizes), and `validateFor` remains the check that a document plays.

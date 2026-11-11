# Migrating to `rc-creation-*` and `ee.schimke.composeai.rccreation`

rc-players' Compose Multiplatform creation API moved out of AndroidX's packages and into four
modules under new coordinates. This is a breaking change for anything that compiled against
`third-party-remote-creation-compose`, `third-party-remote-creation-writer`, or the widget creation
helpers. It is made once: the packages and coordinates below are the ones that stay.

Why: [`design/RC_CREATION_BRIDGE.md`](design/RC_CREATION_BRIDGE.md), "Why the fork is in
rc-players' package". In short, AndroidX's `androidx.compose.remote.creation.compose` is on
Android today and, on androidx-main, builds for the JVM too (its published alphas are still
Android-only), and a library that declares the same classes cannot be on a classpath with it.

The API did not change: every class, function and parameter is where it was, under another package
prefix. Documents written before and after are the same bytes.

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
artifact: a build that still names `third-party-remote-creation-compose` keeps resolving its last
release, in AndroidX's package, until its dependency is changed. `rc-creation-bridge` no longer
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
'
```

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

The two are the same API in different packages, so their types do not mix: an AndroidX
`RemoteModifier` is not an rc-players `RemoteModifier`, and `@RemoteComposable` content written for
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
  `rc-widget-creation-androidx` only (`ee.schimke.composeai.rcwidget.creation.androidx`).
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

## 6. Checklist

1. Replace the two old coordinates (section 1); add `rc-creation-foundation` if needed.
2. Rewrite packages in the sources that use rc-players' creation API (section 2).
3. Remove capability and exclusion workarounds (section 3).
4. Android widget code: move to `rc-widget-creation-androidx` and its package (section 4).
5. Pass `creationApi` where generated source targets AndroidX (section 5).
6. Build. A leftover shows as an unresolved `androidx.compose.remote.creation.compose` import in a
   module without AndroidX's artifact, or as a type mismatch between the two `Remote*` types of the
   same simple name.

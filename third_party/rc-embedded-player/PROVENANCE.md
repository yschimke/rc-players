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
- Commit: `a12036836c464b39bde66b7e2a7c4238eef3b884` (`androidx-main`, 2026-09-24)
- License: Apache-2.0

## How the copy is made

1. Copy both upstream directories into `src/main/kotlin/ee/schimke/composeai/rcembedded/player/`
   (`utils/` under `player/utils/`). Leave out upstream's `README.md`.
2. Rewrite the packages: `androidx.compose.remote.player.compose.embedded` →
   `ee.schimke.composeai.rcembedded.player`, and `androidx.compose.remote.player.compose.utils` →
   `ee.schimke.composeai.rcembedded.player.utils`. Upstream publishes its own embedded player in the
   original package, so this copy cannot share it.
3. Run `./gradlew :third-party-rc-embedded-player:ktfmtFormat`. This module is formatted in ktfmt's
   kotlinlang style, which is what AndroidX uses (`build.gradle.kts`). The formatter changes import
   order in an upstream file, since the renamed imports sort differently. It also rewraps the odd
   line where AndroidX's ktfmt version differs from this one: `RcPlayerState.kt` at this pin.
4. Re-apply the patches below. Every patched line sits under a `// LOCAL PATCH (rc-players)`
   comment, and the helpers they need live in files of their own.

`scripts/diff-upstream.sh <androidx checkout>` diffs this copy against a checkout with the package
rewrite and import order normalised. Its output should be the patches listed here and nothing more
than that rewrapping.

## Version skew

Upstream builds this player against the in-tree `remote-core` / `remote-player-core`. This module
builds it against the published alphas the version catalog pins (`compose-remote = 1.0.0-alpha19`).
The player reaches `@RestrictTo(LIBRARY_GROUP)` members, and `CoreDataAccessors.kt` reaches private
`CoreDocument` state reflectively. Both are sensitive to the gap between `androidx-main` and the
pinned alpha, so pair a refresh with a render of the embedded lane (`scripts/rc-lane-ab/render-ab.sh`),
not just a compile.

At this pin the only source the alphas lack is `remote-creation-compose`'s `AndroidSystemColorMap`,
which `AndroidColorThemeResolver.kt` imports. `AndroidSystemColorMap.kt` is a copy of it, made
`internal`. Delete it, and restore the upstream import, once the pinned release ships it.

## Local patches

Each patch that fixes an upstream gap has an issue on this repository. That's where it gets reported
upstream and where it is retired from. The list is meant to shrink.

| Where | Patch | Issue |
| --- | --- | --- |
| `RcPlayer.kt` | Drops the `RemoteComposePlayerFlags.isEmbeddedPlayerEnabled` check. That flag belongs to AndroidX's own `remote-player-compose` and also routes its `RemoteDocumentPlayer` onto AndroidX's embedded player, so enabling it for this copy would change the View-player control lane too. Depending on this copy is the opt-in. | — (local by design) |
| `RcPlayerDrawing.kt` (`resolveBitmap`) | An image that fails to decode (for example a relative URI) leaves its slot empty, and the failure is cached so it is not retried every frame. Upstream throws out of the draw. | [#509](https://github.com/yschimke/rc-players/issues/509) |
| `RcPlayerDrawing.kt` (`executeOperations`) | Runs the value-producing ops a draw stream can declare: `ColorExpression`, `ColorAttribute`, `ImageAttribute` and `TextMeasure`. Upstream's loop skips them, so their ids resolve against a store nothing wrote. | [#510](https://github.com/yschimke/rc-players/issues/510), [#54](https://github.com/yschimke/rc-players/issues/54) |
| `CoreDataAccessors.kt` (`applyOperationsWithoutBitmaps`) | Registers a `BitmapData` declared in a component's canvas stream. The setup walk follows `Container` children only, and a canvas stream hangs off its component as a field. | [#54](https://github.com/yschimke/rc-players/issues/54) |
| `RcPlayerTextLayout.kt` (`resolveFontFamily`) | A `google:` family is resolved from the shared machine-local Google Fonts cache first, at the document's variation axes, which the downloadable-font factory cannot apply. There is no cache on a device, so this only affects test renders; upstream's path runs everywhere else. | — (render-side; see [#501](https://github.com/yschimke/rc-players/pull/501)) |
| `RcPlayerTextLayout.kt` (`RcPlayerText`) | Line height follows the size the text is drawn at (a paint override, or autosize), and `maxLines` truncates only where the View player's does. | [#511](https://github.com/yschimke/rc-players/issues/511) |

Files that exist only here:

- `RcPlayerLocalPatches.kt`: helpers for the patches above.
- `AndroidSystemColorMap.kt`: the version-skew copy described above.
- `../GoogleFontFamilies.kt`: the Google Fonts cache resolver behind the `google:` patch. It is null
  unless `composeai.fonts.cacheDir` is set, which is a render-side property no app sets.

### Retired by this refresh

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

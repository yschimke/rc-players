# Adapting a level 8 document down to the platform player

`rc-player-edit`'s `adaptTo` rewrites a document written for AndroidX's player (level 8, `CoreText`,
a canvas's `ComponentValue` naming its content) for the platform widget player's profile (level 7 or
6). Android 16's platform player (`RemoteViews.DrawInstructions`) draws a canvas that reads its own
measured width as nothing unless the `ComponentValue` names the `CanvasContent`; before the fix,
`adaptTo` left the level 8 form in place, so the bar vanished on API 36 and 37. With the fix the
adapted document draws what the creation writer's direct level 7 document draws.

## Images (300 x 60 px, shown at 3x)

- API 36 (Android 16): [adapt-level8-down-api36.png](https://github.com/yschimke/rc-players/blob/5226590ecee9164a0be40bddba57ac77136c9ebe/renders/edit-version-parity/adapt-level8-down-api36.png)
- API 37 (Android 17): [adapt-level8-down-api37.png](https://github.com/yschimke/rc-players/blob/5226590ecee9164a0be40bddba57ac77136c9ebe/renders/edit-version-parity/adapt-level8-down-api37.png)

Each image has three panels, top to bottom:

1. **Direct**: the content captured with the launcher's level 7 profile (`RcCaptureProfiles.forTarget`).
   The reference.
2. **Before**: the same content captured at level 8 (`RcCaptureProfiles.androidXExperimental`) and
   adapted with `adaptTo` as it was on `agent/android16-component-value-placement`.
3. **After**: the same, adapted with `adaptTo` at this commit.

Each panel is labelled with its count of the bar's red pixels: 3322 (direct), 406 (before), 3322 (after),
on both API levels. The after panels are byte-identical to the direct ones.

## What to look for

- **Before**: the red bar across the top half of the width is missing. The 406 red pixels left are the
  circle in the bottom row. On API 36 the top strip is blank; on API 37 only a faint 1 px sliver at the
  left edge is drawn.
- **After**: bar, blue border, "Hi", the circle and "Ab" all match the direct panel.

## Cases covered

The document (`PlatformPlayerAdaptedDocumentTest`) holds each version-dependent write in the creation
writer: a canvas that reads its measured width (inside a state layout, a named flag baked to true), a
border, text (`CoreText` or `TextLayout`), and a canvas's paint, with text drawn in it.

## How they were produced

```sh
./gradlew :rc-widget-launcher:testAndroidHostTest --tests '*PlatformPlayerAdaptedDocumentTest*' -Prc.render.out=<dir>
```

The test writes `direct-sdk<N>.png` and `adapted-sdk<N>.png`. "After" is the run at HEAD. "Before" is the
same run with `rc-player/edit/src` checked out from `origin/agent/android16-component-value-placement`
(the test compiles unchanged against it; its assertions fail, as they should, after writing the
images), then restored. The three panels were composed by a throwaway script that scales each render 3x
with nearest-neighbour and counts pixels equal to the bar's red (0xE53935). The test's Robolectric run
needed a temp dir outside `/`, set by an uncommitted local Gradle init script.

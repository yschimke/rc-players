# Text in a `RemoteDensity.Host` capture

Evidence for [#558](https://github.com/yschimke/rc-players/issues/558). A document captured
against `RemoteDensity.Host` writes no pixel sizes: each text's font size is an expression over the
player's `DENSITY` and `FONT_SIZE` variables. Neither Android-side player drew that text correctly.

`title-card-host-probes.png` shows three of wear-m3-catalog's `TitleCardHostProbe*` previews,
captured with `composePreview.rcDensity=host` at 227x200dp and dpi 320 (454x400px, density 2).
They are rendered as four columns:

| column | what it is |
| --- | --- |
| 1 | `RcEmbeddedRenderHarness` on `main` |
| 2 | the same harness with this change |
| 3 | `RcCmpRenderHarness` (`scene.render(0)`) on `main` |
| 4 | the same harness with this change |

The rows, top to bottom, are `TitleCardHostProbeBareText` (two bare `RemoteText`s, drawn over each
other), `TitleCardHostProbeCard` (a `RemoteTitleCard` with title and time) and
`TitleCardHostProbeWithContent` (title, time and a content body).

## What was wrong

- **Embedded player: no text.** `RcPlayerText` read `mFontSizeValue` once, at composition. The
  core's constructor seeds that field with the raw word, which in a `host` capture is a NaN-boxed id,
  and only resolves it in `updateVariables`. The text was laid out at `NaN.sp`.
- **CMP player: text at `1 / density`.** The host density reached `RcPlayerState` from a
  `SideEffect`. By then the constructor's `beginFrame()` had loaded `DENSITY` and `FONT_SIZE` at
  the 1.0 placeholder, and the first frame laid out against them.

After the change, both players draw all three probes alike, and they match the `fixed` capture of
the same previews: four title lines, the content body and the `XXm` time badge.

## Reproducing

Stage the `.rc` files with a `manifest.json` of `{id, width: 454, height: 400, density: 2.0}` and
run both harnesses (absolute paths):

```sh
./gradlew :third-party-rc-embedded-player:testDebugUnitTest --tests '*RcEmbeddedRenderHarness*' \
  --rerun -Prc.embedded.input=<stage> -Prc.embedded.output=<out>/embedded
./gradlew :rc-player-compose:jvmTest --tests '*RcCmpRenderHarness*' \
  --rerun -Prc.cmp.input=<stage> -Prc.cmp.output=<out>/cmp
```

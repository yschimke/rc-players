# External image declared inside a macro

`RcImageLoaderTest.loadsAndDrawsAnExternalImageDeclaredInsideACalledMacro` plays an
8×8 document whose called macro declares a URL bitmap and draws it across the canvas.
The host loader returns a red image, so no network access is needed.

- `before.png`: the unmodified player on `0ebda18` never calls the loader and draws blank.
  The regression fails with `expected:<1> but was:<0>` for its request count.
- `after.png`: the fixed player calls the loader once using the linked bitmap ID and
  draws red. The same test verifies the source URL and center pixel.

Reproduce with `scripts/agent-gradle.sh :rc-player-compose:jvmTest --tests
'*RcImageLoaderTest.loadsAndDrawsAnExternalImageDeclaredInsideACalledMacro'
-Prc.imageLoader.out=/tmp/rc-macro-image-loader`.

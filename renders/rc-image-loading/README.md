# External image loading

`RcImageLoaderTest.completionRepaintsACanvasAndTheHostReceivesTheWireRequest` renders an 80×80
canvas referencing an external bitmap. `before.png` captures the empty canvas while the request
is pending (also the previous player's output for this external bitmap). `after.png` captures
the same canvas once the loader supplies the red bitmap. The test checks the rendered pixel and
the complete wire request, without depending on an external server.

Regenerate:

```sh
scripts/agent-gradle.sh :rc-player-compose:jvmTest --rerun \
  --tests '*RcImageLoaderTest*' \
  -Prc.imageLoader.out="$PWD/renders/rc-image-loading"
```

The suite separately verifies Coil's default file decoding, composition-local overrides,
per-player overrides and cancellation on document/loader replacement.

# `rc-swift-render`: the native Swift player as a render tool

The native Swift player is the only `.rc` player here whose pixels come from Apple's own stack:
CoreText shapes the text, CoreGraphics antialiases it, AppKit lays out the views. Those are exactly
the places the other players disagree with it. `rc-swift-render` puts those pixels anywhere that
already renders `.rc` documents to PNG.

## What it is

An `executableTarget` in `Package.swift` (`Sources/RcSwiftRender`) over
`NativeAppKitWindowController.renderPNG(data:pixelWidth:pixelHeight:density:…)`. It has two modes,
and both match compose-ai-tools' `rc-render-jvm`, so one client drives either renderer:

```
rc-swift-render --input doc.rc --output out.png --width 640 --height 480 \
  [--density 2] [--fontScale 1] [--theme light|dark] [--seeds file] [--time seconds]

rc-swift-render --worker   # rc-render-jvm's binary frame protocol, version 2, over stdin/stdout
```

- **Sizing.** The document lays out in `width / density` by `height / density` points, because
  AppKit points are this player's dp. It then draws into a bitmap of exactly `width` × `height`
  pixels. The bitmap's scale is stated rather than taken from a screen, so a headless CI runner and
  a Retina laptop produce the same image.
- **Seeds.** These use the `rc-render-jvm` seed lines. A name the document does not declare is
  ignored.
- **SVG.** A request for SVG fails that one request: AppKit has no vector export.

## Where it comes from

`scripts/package-swift-render.sh` builds it with SwiftPM in native-only mode, so the Kotlin binary
target is not involved. Before packaging, `scripts/check-swift-render.sh` renders a fixture one shot
and through the worker protocol.

Every release attaches two assets:

```
rc-swift-render-macos-arm64.zip          rc-swift-render-macos-arm64/bin/rc-swift-render
rc-swift-render-macos-arm64.zip.sha256
```

Both are attested with the other Apple artifacts. The executable is Apple-silicon only and ad-hoc
signed, like the macOS player app.

## Who uses it

compose-ai-tools' `render-host` adds a `swift-appkit` Remote Compose backend (`RcSwiftServerRenderer`).
That backend uses the binary that `-Dcomposeai.rcswift.binary` or `COMPOSEAI_RC_SWIFT_RENDER`
names, or `rc-swift-render` on `PATH`. Failing those, it downloads this asset for the `rcplayers`
version it was built against into `~/.cache/composeai/rc-swift-render/<version>/`. It runs the
binary under the same worker pool as `cmp-jvm`, and the preview server offers the result as the
**Swift AppKit** player.

The asset layout above is that downloader's contract. Change it in both repositories in the same
release.

## What it cannot do

- **No Linux.** The document core builds and is tested on Linux, but the renderer is AppKit,
  CoreGraphics and CoreText. Porting the drawing to Cairo or Skia would produce a fourth player's
  pixels, not Apple's. On a Linux server the lane is simply not offered.
- **AppKit is not UIKit.** This is the macOS renderer. iOS pixels still come from the simulator lane
  (`scripts/render-native-uikit-lane.sh`).
- **No custom visibility animations or component buffers.** The AppKit renderer lays a component
  out at the visibility the core reports but does not run remote-core alpha22's `CUSTOM` enter and
  exit functions, and drops component offscreen buffers; the UIKit player draws both.
- **Platform fonts only.** `google:` font families are not fetched, so text falls back to the
  platform faces.

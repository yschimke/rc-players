# Native Swift image button evidence

The same `ImageBackgroundRemoteButton-454x200.rc` bytes are rendered at the document's declared
454×200 size. `cmp-jvm.png` is the supported CMP oracle used by the comparison harness;
`native-uikit.png` is the packaged pure-Swift UIKit product; and `native-appkit.png` is the macOS
player's pure-Swift AppKit renderer.

The automated comparison reports 6.45% differing pixels between CMP JVM and UIKit. The native
renderers intentionally retain approximate text metrics and gradient handling at this stage, while
the embedded bitmap texture, rounded button geometry, label, and click target are recognizable.

Regenerate the UIKit comparison with:

```sh
scripts/check-native-uikit-comparison.sh build/native-swift-image-button-comparison
```

Regenerate the AppKit image after `scripts/build-macos-player.sh` with:

```sh
build/macos-player/Remote\ Compose\ Player.app/Contents/MacOS/RemoteComposePlayer \
  --render-native-png \
  third_party/rc-embedded-player/src/test/resources/rc-fixtures/ImageBackgroundRemoteButton-454x200.rc \
  /tmp/native-image-button-appkit.png
```

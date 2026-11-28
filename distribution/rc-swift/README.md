# rc-swift

Pure-Swift distribution of the Remote Compose native Apple player and Swift Creation APIs.

This repository is an automated Swift Package Manager mirror published from
[yschimke/rc-players](https://github.com/yschimke/rc-players) on every release. Issues and pull
requests belong in `yschimke/rc-players`.

Unlike `yschimke/rc-players`'s root package, `rc-swift` declares no Kotlin/Native XCFramework
binary target:

- Resolves directly in Xcode and `Package.swift` without downloading `RcComposePlayer.xcframework`.
- Compiles the pure-Swift document core (`RcNativePlayerCore`) and authoring libraries
  (`RcCreation`, `RcCreationFoundation`, `RcCreationAppleUI`, `RcCreationHost`) on Linux as well as
  Apple platforms.

## Installation

Add `rc-swift` in Xcode (**File -> Add Package Dependencies...**) or in `Package.swift`:

```swift
dependencies: [
  .package(url: "https://github.com/yschimke/rc-swift.git", from: "4.1.0"),
]
```

## Products

| Product | Platforms | Purpose |
| --- | --- | --- |
| `RcNativePlayerUIKit` | iOS 13+, macOS 12+, visionOS 1+ | Native UIKit / AppKit / SwiftUI `.rc` player (`RemoteComposeNativePlayerView`, `RemoteComposeNativePlayerRepresentable`) |
| `RcNativePlayerCore` | Apple, Linux | Pure-Swift `.rc` document decoder, runtime state and `NativeSwiftDocumentSession` |
| `RcPlayerAppleFonts` | Apple | Bounded Google Fonts resolver for `RcNativePlayerUIKit` |
| `RcCreation` | Apple, Linux | SwiftUI-style authoring API (`RemoteView`, `RemoteDocument`, `RemoteVariable`) that encodes `.rc` documents |
| `RcCreationFoundation` | Apple, Linux | System colours, symbols, icons and image helpers for `RcCreation` |
| `RcCreationAppleUI` | Apple, Linux | Apple HIG controls and widget components on `RcCreation` |
| `RcCreationHost` | Apple, Linux | Typed host variable writes (`set(variable, to: value)`) for hosts that author with `RcCreation` |

## Playing a document in SwiftUI

```swift
import RcNativePlayerUIKit
import SwiftUI

struct CardView: View {
  let documentData: Data

  var body: some View {
    RemoteComposeNativePlayerRepresentable(
      data: documentData,
      background: .transparent,
      compatibilityPolicy: .compatible,
      onEvent: { event in
        print(event)
      }
    )
  }
}
```

## Authoring a document in Swift

```swift
import RcCreation

let title = RemoteVariable("title", default: "Hello")

let document = RemoteDocument(width: 320, height: 120, displayScale: 2) {
  RemoteVStack(alignment: .leading, spacing: 8) {
    RemoteText("\(title)")
      .font(.headline)
  }
  .padding(16)
}

let bytes: Data = try document.encode()
```

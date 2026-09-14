# RcNativePlayerUIKit release package

This archive is the self-contained, experimental UIKit Remote Compose player for iOS. It includes
the Swift sources and the exact `RcComposePlayer.xcframework` used by their temporary Kotlin decode
bridge.

Add the extracted directory as a local Swift package and select the `RcNativePlayerUIKit` product.
The package supports iOS 13 or newer on arm64 devices and Apple-silicon simulators. It is a static,
time-zero proof of concept and does not yet provide the full CMP player's operation, state, action,
or animation compatibility.

For normal versioned SwiftPM consumption, use the repository's bare semantic-version tag and select
the same product. This archive is intended for pinned evaluation, CI fixtures, and consumers that
want one downloadable bundle with no binary fetch during package resolution.

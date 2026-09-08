Remote Compose Player — arm64 iOS Simulator build

Requirements:
- An Apple-silicon Mac
- Xcode 26 with an iPad Simulator runtime

Open Simulator and boot an iPad, then double-click install-and-run.sh or run it
from Terminal. The app includes sample Remote Compose documents; use Open or
drag a .rc file into the app to test your own document.

This is a development test build for Simulator, not a signed App Store or
notarized macOS application. Its framework is built from the same release source
and uploaded as the checksummed Swift package binary for downstream apps.

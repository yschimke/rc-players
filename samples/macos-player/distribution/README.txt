Remote Compose Player for macOS (Apple silicon)
================================================

1. Unzip the archive.
2. Move "Remote Compose Player.app" to Applications if desired.
3. Open the app, choose CMP or Native AppKit POC, then open a local .rc file.

The Native AppKit POC also has Compatible and Strict policies. Compatible renders the supported
subset and reports known differences; Strict refuses partial frames. Both enforce bounded document,
frame, geometry, text, path, and resource work before AppKit installs a view hierarchy.

You can also double-click an .rc file after selecting this app with Open With, drag an .rc file
onto the control window, or pass a file path to the executable from Terminal.

This release artifact is ad-hoc signed, not notarized. macOS may require Control-click > Open on
first launch. It requires an Apple-silicon Mac running macOS 12 or newer.

# Native Swift progress compatibility

Before this slice, the pure-Swift decoder rejected the Material progress fixtures and the native
surface remained blank, as shown by `progress-before.png`. The UIKit player now decodes and renders
the real circular and arc fixtures through its UIView/Core Graphics hierarchy:

- `circular-after.png` — `CircularProgressRemote-384x384.rc`
- `arc-after.png` — `ArcProgressRemote-454x400.rc`

The equivalent AppKit offscreen captures are also exercised during validation. Exact antialiasing
and frame-time phase can differ from CMP, but geometry, two-tone color treatment, stroke caps, and
overall identity are preserved.

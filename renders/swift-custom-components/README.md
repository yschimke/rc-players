# Native Swift custom-component demos

These screenshots come from the iPad simulator running the sample app's **Native POC** renderer.
The `.rc` documents supply the values and return channels; the custom component registry supplies
the native SwiftUI controls, `PhaseAnimator`, and Swift Charts content.

- `swift-controls.png` — two-way `TextField` and `Slider` bindings.
- `swift-pulse.png` — document-configured native SwiftUI animation.
- `swift-chart.png` — document data rendered by Swift Charts with a selection return channel.
- `before-dynamic-float-support.png` — the same controls document against the previous native core,
  which rejected its document-bound float property before constructing the custom component.

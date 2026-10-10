# AndroidX embedded-player adaptation, October 2026

The reference snapshot is `11ece46a49d485c7644e53cb0684a611d7a0ec10` (October 2).
This review examines subsequent merged AndroidX embedded-player changes, including commits
authored by `yschimke@google.com`, through the AndroidX GitHub mirror. The nine Gerrit patch files supplied by the user were reviewed for intent; their
status was not inferred from the patch artifacts. The vendored AndroidX implementation is unchanged; CMP implementations use this
repository's protocol model, runtime and Compose renderer.

| Upstream change | CMP disposition |
| --- | --- |
| [Custom remote children](https://github.com/androidx/androidx/commit/3da7b37d6cf88926eafdbc41865f06b656c58f66) | Preserve custom layout children in wire order; expose `childCount`, `Child`, `Children` and `customChild`. Traverse them for shared elements. |
| [Animated reactive evaluation](https://github.com/androidx/androidx/commit/44f67f0db13db7071302d8104d97e3dc4ffd5437) | Existing runtime resolves computed expressions. Interrupted float tweens now retarget from their current displayed value. |
| [Direct variable preprocessing](https://github.com/androidx/androidx/commit/b417129579088ca6a9d0c8b07ba9019bc1c0df49) | CMP already decodes variable references directly and does not allocate AndroidX RemoteContext/GraphContext objects. |
| [Root touch position and bounds](https://github.com/androidx/androidx/commit/819218298807c2d1cc98f6e92d95c79c509a28df), [outer coordinates](https://github.com/androidx/androidx/commit/1330e3f0180b714a73f614f0ffd69a6b34476067), [root/component dispatch](https://github.com/androidx/androidx/commit/bd364abbe7e42e4ec75fe11bf984d5ebb6a8822e) | Bind TouchExpression operations to their component or document root. Evaluate with current coordinates, apply component handlers before padding, respect referenced drag axes and release on cancellation/disposal. Scroll expressions keep their existing handler. |
| [Direct root dispatch](https://github.com/androidx/androidx/commit/1c441d413c807fcec9e70c2de6583ec33115f47b), [typed constants](https://github.com/androidx/androidx/commit/25e59f0e7d1d15f9a77c788686da18ecb3aeb5c7) | CMP uses typed protocol operations and its own state; it does not depend on reflective AndroidX internals or class names. |
| [Root size after layout](https://github.com/androidx/androidx/commit/a5ef344b37b642e269b850b9eb92c028f817e1a6) | CMP already publishes measured dimensions through its layout callbacks. |
| [Scroll edge ownership](https://github.com/androidx/androidx/commit/3ff4cd04c0a3cff93dfc9ff58d59e7799dcf4733) | Consume same-axis residual drag and fling when remote content scrolls, retaining cross-axis handoff and allowing fitting content to hand off. |
| [Paired touch actions](https://github.com/androidx/androidx/commit/853f3350f100b34a72fa13fdb3be5f7ac551f644) | Track one pointer and pair DOWN with exactly one UP or CANCEL, including stolen gestures, modifier replacement and disposal. Existing click timing stays in CMP's recognizer. |
| [Lazy hierarchy inspection](https://github.com/androidx/androidx/commit/3ba8469555b809fc695921c742edffa2e9cf00aa) | CMP already exposes its own semantic tree and inspector rather than traversing AndroidX components. |

Other reviewed author changes include font-feature/variation separation, multiline text alignment,
expression-id cache fixes, time/configuration slots, Wear color/switch/bookend demos, and capture
quiescence/throttling. CMP already separates font features from variation axes, maintains its own
expression caches and system slots, and renders host controls via its custom registry. AndroidX
Wear/demo and capture-service changes do not map to the published CMP player.

## Image loading

`RcImageLoader` is the host entry point for URL/file bitmap operations. The default adapter uses
Coil's singleton, original image size and platform bitmap conversion. Ktor networking and platform
engines are included. Per-player `imageLoader` and `LocalRcImageLoader` override it; `Empty` disables
external loading. Loading completion invalidates the player while offscreen render-target writes
remain ordinary internal map updates. Inline decoding is unchanged. Fetch failures produce blank
images through Coil's error result; coroutine cancellation remains cancellation.

The new input adapter uses the existing CMP TouchExpression evaluator. Inertial/eased release
for general root/component expressions remains a limitation of that evaluator; notch/end targets
are applied on release. This change does not claim full AndroidX physics parity.

## October Gerrit patch review

The supplied patches describe independent embedded-player capabilities. CMP already executes
matrix expressions, vector projection, and path-derived transforms through its own runtime and
renderer, including a 4×4 evaluator. Its existing `RcSoundHost`, `LocalRcSoundHost` and state
sound dispatcher load and play document sounds on a host-provided engine. CMP also re-evaluates
text/id/data-map lookups and dynamic float-list writes during draw, dispatches metadata host
actions, and invokes the host's haptic feedback from the document stream. These paths remain in
the normal player; they do not need the AndroidX feature-set abstraction.

The native Swift core likewise evaluates 4×4 matrix expressions, perspective vector math and
matrix-from-path transforms. It resolves derived text/data values when snapshots are refreshed.
Native Swift still consumes sound data and play operations without playback, and experimental 2D
meshes have no portable protocol/renderer model in this repository. Sensor subscriptions are not
hosted by either player. The uploaded mesh patch delegates drawing to an Android-only paint
context and reads a texture field reflectively, so it cannot be ported directly to Apple or KMP.
Those capabilities need separate rendering/audio/sensor contracts before claiming support. The
shared upstream test-document patch contains test helpers, not a player behavior change.

| Gerrit change | Player disposition |
| --- | --- |
| [3D matrix operations](https://android-review.googlesource.com/c/4350689) | CMP and native Swift already evaluate expressions, project vectors and follow paths. |
| [Sound playback](https://android-review.googlesource.com/c/4350692) | CMP exposes a sound host and dispatches sound operations. Native Swift has no audio host yet. |
| [2D vertex meshes](https://android-review.googlesource.com/c/4350691) | Experimental mesh opcodes need new portable protocol and rendering support. |
| [Optional features](https://android-review.googlesource.com/c/4354387) | Keep opt-in host interfaces in CMP; do not recreate AndroidX's class/graph bridge. |
| [Device sensors](https://android-review.googlesource.com/c/4350694) | Requires an opt-in lifecycle-bound sensor source on each platform. |
| [Document haptics](https://android-review.googlesource.com/c/4350693) | CMP already forwards document haptics to `LocalHapticFeedback`. Native Swift has no haptic host yet. |
| [Metadata host actions](https://android-review.googlesource.com/c/4354328) | CMP already emits `HostActionMetadata`; native Swift explicitly reports unsupported click actions. |
| [Reactive lookups and stream data](https://android-review.googlesource.com/c/4354389) | CMP already resolves these in draw order; Swift refreshes its snapshots. |
| [Shared test documents](https://android-review.googlesource.com/c/4354488) | Test scaffolding only; existing player tests use local wire builders and captures. |

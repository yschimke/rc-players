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

## Embedded player, 2026-10-08 → 10-27 (androidx-main 5dab33683)

A second pass over the embedded `RcPlayer` changes merged after the table above. Most of them make
the embedded player evaluate a draw stream the way the View player paints it; the CMP player
already executes its draw stream in order through its own runtime, with a flat operation model that
registers resources wherever they sit, so many are already its behaviour. Four were gaps and are
ported. CoreDocument accessors, reflection, `GraphContext` plumbing and tracing do not map.

| Upstream change | CMP disposition |
| --- | --- |
| [GraphTimeState injection](https://github.com/androidx/androidx/commit/3c090de3af36f1528d52894a668a39bb16b5107b) | Not applicable: `GraphContext` plumbing. |
| [Named variables in float functions](https://github.com/androidx/androidx/commit/faefc1f8cad4e959ce5ed87edcc95488cc8ef821) | Already: `RcPlayerState` registers every `NamedVariable` in the flat operation list, function bodies included. |
| [Draw-stream float writes in order](https://github.com/androidx/androidx/commit/b618c2fa5d642040b4c416e44368c49d2dbbf9a7) | Already: the canvas renderer runs the stream imperatively and reads the last value written. |
| [No-argument float function calls](https://github.com/androidx/androidx/commit/1e6cd572958ecc69e2c4401bafee85046c33c3bb) | Already: a call with no arguments runs its body. |
| [View-player particle seeding](https://github.com/androidx/androidx/commit/a6fa4b7c9229c800353aeff7486d954daa37743c) | Ported: a `ParticlesCreate` the draw stream reaches re-seeds its system (`RcPlayerState.seedParticles`); one it never reaches is still seeded once. |
| [NaN floats through MaybeVariableFloat](https://github.com/androidx/androidx/commit/d8c1348fc3815e042e71733a3b6aecdf80fc4751), [system-variable fields](https://github.com/androidx/androidx/commit/0ac5b763015a65a5bd3d2037f4e02772594b0236) | Already: `RcFloatWord.referencedId` treats every NaN-encoded id as a reference, system ids 10–40 included. |
| [Loop body resources, countdown loops](https://github.com/androidx/androidx/commit/8feed82bd79024db2998dc8387db55618c98de4d) | Resources already: setup reads the flat operation list. Countdowns deliberately not ported: core `LoopOperation.paint` still runs `i < until`, which `forEachLoopValue` matches. |
| [Font cache across animated axes](https://github.com/androidx/androidx/commit/2e258c601f1889777535db54698e33c90bbfdafd) | Host faces are cached per axis set and features apply only for whole-number values. Axes on inline `FontData` fonts remain a follow-up. |
| [Root component values, no 2px deadzone](https://github.com/androidx/androidx/commit/0846e92a7af65285035219d5a87a8f1591d4d47b) | Already: the root publishes through `trackComponentGeometry`, and `publishComponentValues` compares exactly. |
| [State layout child enter/exit](https://github.com/androidx/androidx/commit/cb044ebca4edccb4f7a0bf52ae7d803a6b25fe41) | Already: a branch child's own slide or rotate runs through `animateEnterExit`. Sizing the switch to the slowest child is not ported. |
| [Autosize line height](https://github.com/androidx/androidx/commit/e2c79744fe7be6a83fe0dbff31678caaa838a779) | Already: autosized `CoreText` writes its line height in em, so it scales with the chosen size. |
| [Impulse windows, full-paint bitmap fonts](https://github.com/androidx/androidx/commit/4dda3cdf556d5f4e2d1b6e9f417c80848ddc9fac) | Impulses already follow `RcImpulseTimeline`, with sounds in the stream. Ported: bitmap-font glyphs draw with the colour filter and filter quality. |
| [Ancestor takes the gesture](https://github.com/androidx/androidx/commit/27e471ec437790cab0777235a32486fb8124f722) | Already: #711. |
| [Idle clock](https://github.com/androidx/androidx/commit/ed9c2129dca77ff519890f4c26c8c968bd90e498), [direct clock reads](https://github.com/androidx/androidx/commit/73dad9479fe779f49fe0b8a1dc928f62bd0657fc) | Already: #716 schedules frames from what the player reads. |
| [Trace sections](https://github.com/androidx/androidx/commit/68e39e581c272f199efd775ef4a97faaa4b98a82) | Not applicable: `rc-player-trace` has its own sections. |
| [Shader matrices](https://github.com/androidx/androidx/commit/b5e82a7b952215e58a49577fbe2f150f876a0a62) | Already: `SHADER_MATRIX` transforms gradients and image shaders; Decal maps. |
| [Paint colour alpha](https://github.com/androidx/androidx/commit/5df87aacc084f8da35697ad6d31073db8ce4cb83) | Ported: brush, gradient, bitmap and glyph draws use the colour's alpha times `ALPHA`. |
| [Bounded typeface caches, tag classification](https://github.com/androidx/androidx/commit/20c49a1cb1bd77f39fb9a442c55a50a963f4da71) | Classification already. Ported: malformed `CoreText` axis tags are dropped, as the paint path drops them. |
| [Setup skips component draw streams](https://github.com/androidx/androidx/commit/55c1057c63dfa8477e5eeff75404c9c72105b920) | Already: setup only loads constants and data. |
| [BitmapData dimensions](https://github.com/androidx/androidx/commit/31ac8e6ab3268cf7a77cad9abc65bbf65b04e082) | Ported: `ImageAttribute` reports a loaded URL/file bitmap's own size (`setLoadedImageSize`). |
| [FloatExpression workaround](https://github.com/androidx/androidx/commit/8aac4fef43c94006307d5fe4d60c1232754b7ba5), [canvas expressions in CoreDocument](https://github.com/androidx/androidx/commit/461d551382fae67b691f308eae1276a67b0c919d), [animated draw expressions](https://github.com/androidx/androidx/commit/6236a29100344236abc5fa79a3bc66fd8df84d46) | Already: every `FloatExpression` animates wherever it is defined. |
| [Preprocess scoping](https://github.com/androidx/androidx/commit/a4d56c3e5ebb6c20d1dcdd273614d4c088aca5aa) | Already: no preprocess pass; component-scoped names register. |
| [Suspending typeface resolver](https://github.com/androidx/androidx/commit/dcd6619bffa1ad193a7a5d32073ceb6245f9bb6e), [FontInstance variations](https://github.com/androidx/androidx/commit/60c5ff5fce2b151b62cd8175c345ddea34e40784) | Already: `RcTypefaceLoader` hosts resolve families per axis set for layout and canvas text. |
| [Custom visibility animations](https://github.com/androidx/androidx/commit/92da47b3ac6370bdd690b1deca5ce74ef764f3f5), [built-in visibility animations](https://github.com/androidx/androidx/commit/2ed28fbdae425b4234c5f5f2735e4b6c6acc48c3) | Built-ins already (`RcVisibilityTransition`). Follow-up: custom function animations and enter/exit sequencing need new `AnimationSpec` fields in the protocol model. |
| [Nested offscreen bitmaps, DrawContent layers](https://github.com/androidx/androidx/commit/41ae24116a42a808499b82cd8aa50bdceb1c0fbc) | Already: `RcDrawTargetState` follows core's `DrawToBitmap` redirect model and function calls draw. |
| [Single paint context](https://github.com/androidx/androidx/commit/998fa9dd2d3070609c6295af868e9869943636ca), [failed decode cache](https://github.com/androidx/androidx/commit/7677a999c1901f35e20a959108b2aa7281f9103e) | Not applicable: paint resolves through `RcPlayerState`; inline bitmaps decode once per document. |
| [Click areas](https://github.com/androidx/androidx/commit/9e030adfe3dc9b7fe14edb9c1bb2568a47db514e) | Already: `applyAndroidXClickAreas`. |
| [Embedded document fonts](https://github.com/androidx/androidx/commit/f94b740d87c32fec53b8651d4ecaa5342188eec1) | `CoreText` already loads `FontData`. Follow-up: canvas text naming a `FontData` and the `trustedContent` gate. |
| [maxLines](https://github.com/androidx/androidx/commit/0b4724b092fe553fefd9ffd9d24fbd8f9b2f8ead) | Already: `androidXMaxLines`. |
| [Dynamic font size, DP density](https://github.com/androidx/androidx/commit/9c7bf332b3c02e3a5b09d5019061eb057689865d) | Dynamic size already. DP scaling is in alpha22's `CoreText` and `TextLayout` and is now ported: a literal size scales by the host density in a DP document (`rcTextFontSizePixels`). The creation fork writes DP-mode sizes in dp to match; see its PROVENANCE. |
| [Collections and loop bounds](https://github.com/androidx/androidx/commit/d1431a7e3c5f40f63de234db3faa72376eb20737) | Already: flat collection registration; loops write their index before the body. |
| [RootContentBehavior scaling](https://github.com/androidx/androidx/commit/ce15fd60fad51be06e4a52d35721a9eb2620018c) | Already: raw documents scale and align by `RcRootContentBehavior`. |
| [TextSubtext/TextTransform](https://github.com/androidx/androidx/commit/c1c31ea5a6621fd080547ebe750760c3a87fa2d3), [text and time attributes](https://github.com/androidx/androidx/commit/03ba86c21377908e0ddf8b78240e3ba965f38501) | Already: evaluated in draw order, including `BitmapTextMeasure`. |
| [Subpackages](https://github.com/androidx/androidx/commit/82ec7b31a8cc3356824be0cdf69f3dc7dc977de2), [CoreDataModel removal](https://github.com/androidx/androidx/commit/d3d6aa83f1758e4c350ba6d71d306ba9b099aacd), [accessor renames](https://github.com/androidx/androidx/commit/157d42d43cf466e1e6106c07744bc4efcf53ccbc), [core accessors](https://github.com/androidx/androidx/commit/1cfe2843cafce5e11194619691e9e11fccc357b7) | Not applicable: refactors. |

Follow-ups: apply variation axes to inline `FontData` fonts, keyed per font and axis set as
`RcHostFonts` keys host faces; let canvas text name a `FontData` font; model the custom
visibility-animation and sequencing fields of `AnimationSpec` before rendering them; and give
the native Swift player the same DP font-size scaling.

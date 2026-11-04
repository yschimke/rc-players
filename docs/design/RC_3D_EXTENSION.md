# Draft 3D operations as an opt-in player extension

**Status: experimental.** `:rc-player-3d` plays the ten draft 3D operations from AndroidX
[ag/4108133](https://android-review.googlesource.com/c/platform/frameworks/support/+/4108133)
(`DEFINE_MESH_3D` … `MESH_PRIMITIVE_3D`, opcodes 110–118 and 120) in `RcComposePlayer`. The engine
is ported from the TypeScript and C++ players in
[camaelon/remotecompose-experiments](https://github.com/camaelon/remotecompose-experiments)
(`players/3D_PLAN.md`), which are in turn ports of the reference `JavaPaint3DContext`.

Nothing changes for a host that does not opt in: a document that uses these opcodes still fails to
decode with `Unsupported operation`, as it does on AndroidX's players.

## Using it

```kotlin
CompositionLocalProvider(
  LocalRcPlayerExtensions provides RcPlayerExtensions(Rc3DExtension(Rc3DOptions()))
) {
  RcComposePlayer(bytes)
}
```

A host that decodes the document itself passes the same extensions to the codec:
`RcDocumentCodec.decode(bytes, extensions.wire)`. `Rc3DDocumentBuilder` writes documents that use
the operations; there is no creation-API support for them, since AndroidX's writer has none yet.

## The extension mechanism

Two layers, both opt-in behind `@ExperimentalRcWireExtension`:

| Layer | Type | What it does |
| --- | --- | --- |
| Protocol | `RcWireExtension`, `RcWireExtensions`, `RcExtensionOperation` | Codecs for opcodes the built-in table leaves free. Registering a built-in or duplicate opcode is an error, so a document never decodes differently depending on which extensions a host installed. |
| Compose | `RcPlayerExtension`, `RcExtensionSession`, `LocalRcPlayerExtensions` | One session per played document that uses the extension. The draw loop hands each extension operation to it in document order, with the `DrawScope`, the player state and the current paint. |

Values an extension reads through `RcPlayerState.resolve` are tracked like the player's own reads,
so a camera that follows the clock or a named value redraws with it, and one that does not is drawn
once. An extension writes variables with `RcPlayerState.setFloat` (`VECTOR_EXPRESSION` does), and
later operations in the same pass read them.

Limit: extensions apply to the top-level operation stream. Bodies the runtime decodes later (macro
and referenced-operation blobs) see only the built-in table.

## Rendering, and where the GPU comes in

Every operation runs through one engine (`RcSoftware3D`): camera, model matrix, lights, material
and the mesh cache. A `DRAW_MESH_3D` is then drawn by one of two rasterizers, chosen per draw by
`Rc3DOptions.renderer`:

| Renderer | How | Depth |
| --- | --- | --- |
| Software | The reference's 28.4 fixed-point rasterizer into an ARGB buffer, composited after each mesh so 3D interleaves with 2D in document order | Per pixel |
| GPU | Transform, lighting and culling on the CPU (the same code), then a back-to-front triangle list through the platform's `drawVertices`: `android.graphics.Canvas` on Android, Skia on desktop, iOS, macOS and Wasm | Painter's sort per triangle |

`Auto` follows the document: software modes and wireframe (which needs the depth buffer) go
through software, every accelerated mode through the GPU. The two agree pixel for pixel on a convex
shape except along its antialiased silhouette (`Rc3DRenderTest`).

What keeps a steady scene cheap: a parametric primitive or expression surface is rebuilt only when
the values it was built from change; a texture is read back once per bitmap; the software frame
bitmap and the GPU vertex buffers are reused across frames.

![Software and GPU cubes, hidden-line wireframe](../../renders/rc-3d-extension/cube-software.png)
![](../../renders/rc-3d-extension/cube-gpu.png)
![](../../renders/rc-3d-extension/cube-wireframe.png)

### Next GPU steps, by target

| Target | Next step | Why |
| --- | --- | --- |
| Android (API 34+) | `android.graphics.Mesh` with a `MeshSpecification`: upload each mesh once, the MVP matrix as a uniform, lighting in the fragment shader | Moves the per-vertex transform off the CPU; geometry uploads only when a primitive rebuilds |
| Android (any) | GLES into a `HardwareBuffer`, drawn with `Bitmap.wrapHardwareBuffer` | A real depth buffer with no copy, still in document order |
| Desktop, iOS, macOS | Skia's mesh API, if Skiko exposes it; otherwise render on Skia's own `DirectContext` | Same as Android `Mesh`; the open question is sharing a texture with Skiko without a copy |
| Web (Wasm) | WebGL2 in Skiko's context, or WebGPU | Skiko already draws through WebGL2, so zero-copy needs the same context |

## XR

This is built to fit [compose-ai-tools' Remote Compose in XR](https://github.com/yschimke/compose-ai-tools/blob/main/docs/design/REMOTE_COMPOSE_XR.md)
work, which played a path-drawn cube and a wall card as panel textures. On Android XR there are
four ways to show a 3D document. Each falls back to the one before it, and a host can choose per
mesh draw, not per document.

| Level | What the user sees | How | Per-frame cost | Here |
| --- | --- | --- | --- | --- |
| 1. Flat panel | The document as a card, its 3D drawn flat | `RcComposePlayer` in a `SpatialPanel`, or a hardware canvas on a SceneCore `SurfaceEntity` | One render for both eyes, only when something changed | Works now |
| 2. Stereo window | Depth inside the panel's rectangle | Two renders, one camera per eye, into a side-by-side stereo `SurfaceEntity`; eye cameras from the head pose relative to the panel, through `Rc3DHostCamera` | Two renders, following the head | Host camera is here; the stereo surface host is not |
| 3. Hand-off | Meshes as objects in the room: stereo, occlusion against the room, system lighting | Each mesh exported once as glTF (`Rc3DGltf`) and loaded as a SceneCore glTF model; poses updated from `Rc3DPanelPlacement` each frame; the 2D document stays a panel, with the entities as its children | Pose updates only | Listener, export and placement are here; the SceneCore host is not |
| 4. Own renderer | Anything | An OpenXR app rendering each eye itself, through Filament or GLES | All of it | Out of scope |

**Level 3 is the target for rigid meshes, and level 1 the fallback for everything else.** As far as
this design knows, SceneCore loads glTF models but has no public API for geometry that changes, so:

- A primitive with constant parameters, moved by `MATRIX_3D_OP` or the clock, is handed off. Its
  `Rc3DMeshDraw.meshVersion` stays put and only `model` changes.
- A deforming mesh (an animated `MESH_EXPRESSION_3D`, a primitive whose radius is an expression) is
  rebuilt every frame and its `meshVersion` rises each time. Re-exporting glTF per frame is too
  heavy, so those draws stay with the player (level 1), or a stereo surface (level 2) when the
  document is 3D-only.

### The listener contract a hand-off host relies on

`Rc3DOptions(drawInPlayer = false, sceneListener = ...)` keeps the player evaluating the document
(variables, camera, primitive rebuilds, touch) while the host owns the 3D drawing:

- `onDrawMesh` reports each draw as it happens; `onFrame` reports the whole of one component's
  pass once it is drawn. The draws in a frame are exactly what that component shows now: the host
  keeps those and drops any others it held for that component. A static document is drawn once, so
  it costs nothing afterwards.
- `Rc3DMeshDraw.key` (component and position in its pass) is stable from frame to frame while the
  document's structure is: one entity per key.
- `meshVersion` changes exactly when the geometry does: re-export on change, and treat a version
  that rises every frame as deforming.
- `texture` is the bitmap in effect. The host encodes it as PNG for `Rc3DGltf.toGlb`; encoding is
  platform code the player does not carry.

### Mapping the document's camera into the room

In XR the eyes are the camera, so the document's camera places the scene instead.
`Rc3DPanelPlacement.panelTransform` treats the panel as a window: the camera's focus plane lies on
the panel, and eye-space units are scaled so the view's height there is the panel's height in
metres. Head-on, the room version lines up with the flat render; moving around reveals the depth.
`Rc3DXrHelpersTest` holds a point on the focus plane to exactly where the flat render draws it.

The panel's physical size is the host's choice. The XR doc's proposed header hints (a preferred
physical size and depth extent) are where a document would state it.

### Input

2D taps keep working through the panel. A tap on a handed-off entity arrives as a SceneCore
interaction hit; the host projects the hit point through the draw's view and projection into the
viewport and feeds it to the document as a touch, so touch expressions (drag to turn, tap to step)
work unchanged.

### What is here, and what is not

- Here: transparent output (`Rc3DRenderTest` asserts the corners stay at alpha 0, as
  `SpatialRemoteComposeTest` does for the path-drawn cube); the host camera; the listener contract
  above; glTF export; panel placement. [`renders/rc-3d-extension/scene.json`](../../renders/rc-3d-extension/scene.json)
  is a SpatialScene with two renders as panel textures, the shape `xr-composite` reads, for
  level 1 previews.
- Not here: a SceneCore host. It needs the Jetpack XR dependencies and belongs in an Android XR
  sample app; SceneCore's testing fakes can cover entity creation and pose updates without a
  headset. Level 2's stereo surface host, and level 3 previews in `xr-composite`, which would need
  glTF model support there.

## Divergences from the reference

- `VECTOR_EXPRESSION` resolves only variables before evaluating. The reference's `updateVariables`
  also replaces the vector opcodes with variable reads, so every vector program evaluates to zeros
  (finding 3 in `3D_PLAN.md`). This follows the experiments repo's players.
- An unset variable reads as 0, as `RemoteContext.getFloat` returns.
- Transcendentals use `kotlin.math`, which may differ from glibc in the last bit, so output is not
  promised bit-identical to the C++ oracle on every target. Parity against that oracle's 46 scenes is
  not wired up yet.

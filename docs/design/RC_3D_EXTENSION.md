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
once. `RcExtensionDrawScope.size` is the area the drawing covers in the document's own units, so a
document with a scaling `ROOT_CONTENT_BEHAVIOR` scales its 3D with its 2D. An extension writes variables with `RcPlayerState.setFloat` (`VECTOR_EXPRESSION` does), and
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
| GPU, shader mesh (Android 14+) | A convex mesh is uploaded once per geometry version as an `android.graphics.Mesh`; a vertex shader projects, culls and lights it each frame from uniforms | None needed: a convex mesh's visible faces never overlap |

`Auto` uses the GPU wherever it gives the same picture. Every accelerated mode goes through the
GPU, and so does a software mode for a convex mesh that is alone in its depth pass: back faces are
culled, so a convex mesh's visible faces never overlap and there is nothing for depth to resolve.
Wireframe, a non-convex mesh (a torus) and a pass with several meshes keep the software
rasterizer's depth buffer. The two agree pixel for pixel on a convex shape except along its
antialiased silhouette (`Rc3DRenderTest`). `Software` and `Gpu` force one or the other.

What keeps a steady scene cheap: a parametric primitive or expression surface is rebuilt only when
the values it was built from change; a texture is read back once per bitmap; the software frame
bitmap, the GPU path's paint, texture shader and vertex arrays are reused across frames. The
software rasterizer tracks the rectangle each mesh touched, and only that rectangle is cleared,
converted, uploaded and composited, so a small mesh in a large viewport costs its own area.

The software buffer is the canvas's own pixels (times `pixelScale`), not the document's units, so
a document scaled up by its root content behavior is rasterized sharp rather than stretched.

On the desktop test harness (CPU Skia, whose own floor is about 12 ms per render at 1024 px), these
took a software-mode cube from 23.3 to 13.5 ms per render, a small cube forced through software
from 24.3 to 12.2 ms, and a torus forced through software from 24.2 to 16.8 ms.

![Software and GPU cubes, hidden-line wireframe](../../renders/rc-3d-extension/cube-software.png)
![](../../renders/rc-3d-extension/cube-gpu.png)
![](../../renders/rc-3d-extension/cube-wireframe.png)

### The Android shader mesh

On a hardware canvas on Android 14 and later, the GPU path draws a convex mesh through
`android.graphics.Mesh` (`Rc3DShaderMesh.android.kt`). The geometry is laid out once per mesh
version, three vertices per triangle so each carries its face's edges and centroid
(`Rc3DShaderMeshData`). Each frame then sets only uniforms: the model-view and
model-view-projection matrices, up to eight eye-space lights, and the material. The vertex shader
repeats the engine's `projectTriangle` and `litColor`: smooth shading lights each vertex,
flat shading lights the face at its centroid, Lambert is two-sided, and a back face collapses to a
point.

What stays on the CPU path: non-convex meshes, which need the painter's sort; meshes not wholly in
front of the near plane, which the engine clips per triangle; scenes with more than eight lights;
software canvases; Android 13 and earlier; and every Skia target.

Robolectric's host graphics have no meshes, so `Rc3DAndroidMeshDeviceTest` checks it on an API 34
emulator (`.github/workflows/three-d-device-tests.yml`). It draws each scene through both paths on a
hardware canvas and requires the same coverage (within 4%) and colours (a mean difference of at
most 4 levels).

### The Android GLES depth renderer

`Auto` sends a draw that needs depth to the GPU too, on Android 10 and later
(`Rc3DDepthRenderer.android.kt`). That is any software-mode draw the depthless paths above cannot
take: non-convex meshes, several meshes in one depth pass, textures, translucency.

- **Rendering.** One EGL context per session renders each mesh into an offscreen framebuffer
  whose 24-bit depth buffer lasts until the document's `CLEAR_DEPTH`, as the software z-buffer
  does. Each mesh's colour is then copied to an `ImageReader` surface and drawn into the canvas
  as a hardware bitmap, so it lands between the 2D drawn before and after it.
- **Shading.** Projection and lighting follow the engine: the vertex shader repeats `litColor`
  from the shader mesh's per-face layout. GL culls on the projected winding, and texture
  coordinates are perspective-correct.
- **Routing.** The first depth-needing draw in a pass picks the renderer for the whole pass, so a
  pass never mixes two depth buffers.
- **What stays on the software rasterizer:** wireframe, whose hidden-line pass is its own; a pass
  that started there; more than eight lights; software canvases; Android 9 and earlier; every
  Skia target; and `Rc3DRenderer.Software`.
- **Testing.** `Rc3DAndroidDepthDeviceTest` compares it with the software rasterizer on the
  emulator, for a torus, intersecting cubes, a glossy cube and a textured sphere.

### Next GPU steps, by target

| Target | Next step | Why |
| --- | --- | --- |
| Android (API 34+) | Done for convex meshes: `RcShaderMeshDrawer` | The per-vertex transform and lighting leave the CPU; geometry uploads only when a primitive rebuilds |
| Android (API 29+) | Done: `RcGlesDepth`, GLES into an `ImageReader` surface, drawn with `Bitmap.wrapHardwareBuffer` | A real depth buffer with no CPU copy, still in document order |
| Desktop, iOS, macOS | Blocked on Skiko: `SkMesh` bindings upstream (see below) | Same as Android `Mesh`; nothing in Skiko 0.150 can stand in for it |
| Web (Wasm) | WebGL2 in Skiko's context, or WebGPU | Skiko already draws through WebGL2, so zero-copy needs the same context |

### The Skia targets: what Skiko allows today

Checked against Skiko 0.150.2, the version Compose Multiplatform 1.12.2 pulls (issue #791). The
Skiko actuals of `RcShaderMeshDrawer` and `RcDepthRenderer` still decline every draw, and none of
the three routes can land in this repository yet:

1. **`SkMesh` bindings in Skiko: the route to take, but it has to start upstream.** Skia has
   `SkMeshSpecification`, `SkMesh` and `SkCanvas::drawMesh`, which is what `android.graphics.Mesh`
   wraps. Skiko binds none of them: `org.jetbrains.skia` has no `Mesh` or `MeshSpecification`
   class, `Canvas` has no `drawMesh`, and the JNI/native exports are generated per binding, so the
   player cannot reach Skia's mesh from Kotlin either. `android.graphics.Mesh` is a thin wrapper
   over `SkMesh`, and the Android shaders already use `SkMesh`'s `Varyings main(const Attributes)`
   and `float2 main(const Varyings, out float4)` entry points. So once Skiko ships the bindings,
   the Skiko actual of `RcShaderMeshDrawer` can reuse the shaders, attribute layout
   (`Rc3DShaderMeshData`) and uniforms unchanged; only the upload and draw calls are new.
   The proposal to file is in [`SKIKO_MESH_PROPOSAL.md`](SKIKO_MESH_PROPOSAL.md).
2. **`RuntimeEffect`: ruled out.** Skiko has only `RuntimeEffect.makeForShader`,
   `makeForColorFilter` and `makeForBlender`, all fragment stages. A draw's geometry can only come
   from `drawVertices`, whose positions are 2D device points, so projection, culling and the
   painter's sort stay on the CPU, which is the vertex path that exists now. Its two
   interpolated per-vertex channels (a colour and one `float2` of local coordinates) cannot carry
   a normal and a position for per-pixel lighting, and moving lighting alone off the CPU saves
   little, since it costs the same order as the transform. A fragment-only renderer that skips
   `drawVertices` would have to find each pixel's face itself, for example by intersecting a view
   ray with every face plane of a convex mesh, which costs pixels × faces per frame against the
   vertex path's vertices + pixels. At 512 × 512 and 1000 triangles that is about 2.6 × 10⁸ face
   tests a frame, so it loses to the existing path on anything but a handful of faces.
3. **A GPU context of our own: not now.** Compose desktop exposes no public way to reach the
   `DirectContext` it draws with, and the backend differs by OS (Metal on macOS, Direct3D on
   Windows, OpenGL on Linux; Metal on iOS). Skiko's `DirectContext.makeGL/makeMetal/makeDirect3D`
   can make a second context, but its output cannot be shared with Compose's without per-backend
   interop the public API lacks, so every frame is a GPU → CPU → GPU copy. Against the convex vertex
   path (CPU work proportional to vertices, GPU rasterization) that copy only adds cost. The only
   draws it could win are the ones that need depth, which go to `RcSoftware3D` today, and it needs
   a GPU to measure on: the hosted Linux runners and this repository's sandboxes render on CPU
   Skia, where it would only add a readback to software rendering. Revisit it as an
   `RcDepthRenderer` once it can be measured on a real desktop GPU, and only if it beats the
   software rasterizer at document sizes there.

The Wasm target is in the same `skiko` source set and has the same stubs. Its route is WebGL2 in
Skiko's own context (the table above), which is a separate problem from these bindings.

## XR

This is built to fit [compose-ai-tools' Remote Compose in XR](https://github.com/yschimke/compose-ai-tools/blob/main/docs/design/REMOTE_COMPOSE_XR.md)
work, which played a path-drawn cube and a wall card as panel textures. On Android XR there are
four ways to show a 3D document. Each falls back to the one before it, and a host can choose per
mesh draw, not per document.

| Level | What the user sees | How | Per-frame cost | Here |
| --- | --- | --- | --- | --- |
| 1. Flat panel | The document as a card, its 3D drawn flat | `RcComposePlayer` in a `SpatialPanel`, or a hardware canvas on a SceneCore `SurfaceEntity` | One render for both eyes, only when something changed | Works now |
| 2. Stereo window | Depth inside the panel's rectangle | Two renders, one camera per eye, into a side-by-side stereo `SurfaceEntity` over the player's viewport; each eye's camera an off-axis frustum through the viewport from that eye's pose relative to the panel | Two renders, following the head | Here, for deforming meshes, in [`rc-player/xr-sample`](../../rc-player/xr-sample) |
| 3. Hand-off | Meshes as objects in the room: stereo, occlusion against the room, system lighting | Each mesh built once as a SceneCore `CustomMesh` in a `MeshEntity`, a child of the panel; poses updated from `Rc3DPanelPlacement` each frame. (`Rc3DGltf` writes the same geometry as `.glb` for runtimes that load glTF.) | Pose updates only | Here, in [`rc-player/xr-sample`](../../rc-player/xr-sample) |
| 4. Own renderer | Anything | An OpenXR app rendering each eye itself, through Filament or GLES | All of it | Out of scope |

**Level 3 is the target for rigid meshes, and level 1 the fallback for everything else, per draw.**
SceneCore 1.0 builds a mesh from vertex and index buffers (`CustomMesh`, `MeshEntity`), but the
geometry of a built mesh is fixed and an app cannot free an entity (`Entity.dispose` is
restricted to the library). So:

- A primitive with constant parameters, moved by `MATRIX_3D_OP` or the clock, is handed off. Its
  `Rc3DMeshDraw.meshVersion` stays put and only `model` changes: one entity, moved each frame.
- A mesh rebuilt once (a host value changed its radius, say) gets a new entity; the old one is
  disabled and detached, and goes with the session.
- A deforming mesh (an animated `MESH_EXPRESSION_3D`, a primitive whose radius follows the clock)
  is rebuilt every frame. A new entity per frame would leak one per frame, so once its version has
  changed twice within a few frames (a window, since the player can repaint at the same animation
  time between clock ticks), the host returns true from `Rc3DSceneListener.keepInPlayer` and
  it goes to the stereo surface (level 2) while the eyes are tracked, and otherwise the host
  returns true from `keepInPlayer` and the player draws it on the panel (level 1).

### The stereo surface (level 2)

`RcSceneCoreHost` keeps one side-by-side stereo `SurfaceEntity`, a child of the panel, covering
the player's viewport a millimetre in front of it. Each frame it renders the deforming draws twice
into the surface's halves with GLES, one camera per eye:

- **Eye cameras.** The eyes' poses come from ARCore for Jetpack XR (`RenderViewpoint.left` and
  `right`, with device tracking on, which needs the `HEAD_TRACKING` permission), moved from
  perception space into the panel's. The surface is a window onto the scene, so each eye's camera
  is an off-axis perspective whose frustum passes through the viewport's corners, aligned with the
  panel's axes ("generalised perspective projection"). Only the eye's position matters. A point on
  the panel plane lands at the same place for both eyes, the flat render's place; what lies behind
  parts uncrossed and what lies in front crossed.
- **Placement.** Each mesh is placed in panel space by `Rc3DPanelPlacement`, as the room entities
  are, so a deforming mesh and a rigid one share one frame.
- **Routing.** A rigid draw is an entity; a deforming one goes stereo when the session allows 3D
  content, both eyes are tracked and the renderer is working, and back to the panel when any of
  these is missing. `RcDrawRouting` decides, per draw and per frame.
- **Limits.** The surface is flat, so its meshes have depth for the eyes but do not sort against
  the room entities or the room, and anything outside the viewport's rectangle is clipped. A draw
  carries the document's paint but not its lights, so the surface lights with one fixed light, like
  the entities' rough PBR material. SceneCore 1.0's public `SurfaceEntity.create` blends the
  surface's alpha, which lets the panel show around the meshes; the overload that takes a blending
  mode is restricted. The surface is re-rendered when the document redraws, which a deforming mesh
  does every frame.

`Rc3DHostCamera` is not needed for this: the draws already carry the document's camera, which
places the scene, and the eye cameras are applied after placement.

### The listener contract a hand-off host relies on

`Rc3DOptions(drawInPlayer = false, sceneListener = ...)` keeps the player evaluating the document
(variables, camera, primitive rebuilds, touch) while the host owns the 3D drawing:

- `onDrawMesh` reports each draw as it happens; `onFrame` reports the whole of one component's
  pass once it is drawn. The draws in a frame are exactly what that component shows now: the host
  keeps those and drops any others it held for that component. A static document is drawn once, so
  it costs nothing afterwards.
- `Rc3DMeshDraw.key` (component and position in its pass) is stable from frame to frame while the
  document's structure is: one entity per key.
- `meshVersion` changes exactly when the geometry does: rebuild on change, and treat a version
  that rises every frame as deforming.
- `keepInPlayer(draw)` hands a draw back: the player draws it on the 2D surface even with
  `drawInPlayer = false`. It is how a host declines geometry it cannot take.
- `texture` is the bitmap in effect. A host that needs a file encodes it (as PNG for
  `Rc3DGltf.toGlb`, or for SceneCore's `Texture`); encoding is platform code the player does not
  carry.

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
- The sample app, [`rc-player/xr-sample`](../../rc-player/xr-sample): it plays a document with a
  2D card, a rigid cube spun by the clock and a torus whose tube breathes with it. On a headset in
  Full Space the cube becomes a `MeshEntity` in the room and the torus, deforming, goes to the
  stereo surface while the eyes are tracked, or stays on the panel; anywhere else the player draws
  all of it on the panel. `RcSceneCoreHost` is the level-2 and level-3 host; its mesh packing,
  deformation rule, eye cameras and routing are unit-tested on the JVM. It has not been run on a
  headset or emulator from this repository's CI.
- Not here: textures on handed-off meshes (SceneCore's `Texture` loads from a file, so the host
  would encode the bitmap first) or on the stereo surface; input on handed-off entities or the
  stereo surface;
  level 3 previews in `xr-composite`, which would need mesh or glTF support there.

## Divergences from the reference

- `VECTOR_EXPRESSION` resolves only variables before evaluating. The reference's `updateVariables`
  also replaces the vector opcodes with variable reads, so every vector program evaluates to zeros
  (finding 3 in `3D_PLAN.md`). This follows the experiments repo's players.
- An unset variable reads as 0, as `RemoteContext.getFloat` returns.
- Smooth normals go through the model-view's normal matrix (its inverse transpose), so they stay
  perpendicular to the surface under a non-uniform `M3_SCALE`. The reference transforms them with
  the model-view itself, which bends them and lights a stretched mesh wrongly. Every renderer here
  (software, vertex, `Mesh`, GLES) uses the normal matrix.
- A vector in `VECTOR_EXPRESSION` keeps its lanes past its dimension at zero through every
  operation. The reference applies arithmetic to that padding too, so `length(vec3(1, 2, 3) + 1)`
  is sqrt(30) there and sqrt(29) here; dot, length and normalize all read the padding. A scalar
  still broadcasts across all four lanes.
- Transcendentals use `kotlin.math`, which may differ from glibc in the last bit, so output is not
  promised bit-identical to the C++ oracle on every target. Parity against that oracle's 46 scenes is
  not wired up yet.

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
work, which played a path-drawn cube and a wall card as panel textures:

- **Transparent output.** `PAINT_3D_STATE` clear-depth clears colour to transparent and the player
  paints no background, so a 3D document composites over a room. `Rc3DRenderTest` asserts the
  corners stay at alpha 0, as `SpatialRemoteComposeTest` does for the path-drawn cube.
- **A host-driven camera** (tier 1 of that doc's "per-eye view" ask). Every camera parameter may be a
  variable. `Rc3DDocumentBuilder.hostCamera` names them (`xr.camera.eye.x` …), and an XR host puts
  the document's camera at an eye's pose with `Rc3DHostCamera(...).namedValues()`, without
  re-sending the document.
- **Hand-off to the XR runtime** (tier 3). `Rc3DOptions.sceneListener` receives every mesh draw with
  its model-space geometry, model, view and projection matrices and colour. With
  `drawInPlayer = false` the player draws only the 2D content, and the host turns each mesh into a
  SceneCore entity once and updates its pose each frame.
- **Previewing it in a room.** [`renders/rc-3d-extension/scene.json`](../../renders/rc-3d-extension/scene.json)
  is a SpatialScene with two 3D renders as panel textures, in the shape `xr-composite` reads.

## Divergences from the reference

- `VECTOR_EXPRESSION` resolves only variables before evaluating. The reference's `updateVariables`
  also replaces the vector opcodes with variable reads, so every vector program evaluates to zeros
  (finding 3 in `3D_PLAN.md`). This follows the experiments repo's players.
- An unset variable reads as 0, as `RemoteContext.getFloat` returns.
- Transcendentals use `kotlin.math`, which may differ from glibc in the last bit, so output is not
  promised bit-identical to the C++ oracle on every target. Parity against that oracle's 46 scenes is
  not wired up yet.

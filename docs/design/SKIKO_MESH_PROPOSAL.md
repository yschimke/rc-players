# Proposal for Skiko: bind `SkMesh`

A draft issue for [JetBrains/skiko](https://github.com/JetBrains/skiko/issues), from
`rc-players` issue #791. It has not been filed yet. When it is, link it here and from
[`RC_3D_EXTENSION.md`](RC_3D_EXTENSION.md#the-skia-targets-what-skiko-allows-today).

---

**Title:** Expose `SkMesh` / `SkMeshSpecification` / `SkCanvas::drawMesh`

**Motivation.** Skia's custom mesh API lets an app upload a vertex buffer once and run an SkSL
vertex shader every frame from uniforms. Android exposes it as `android.graphics.Mesh` (API 34+),
and Compose Multiplatform code that uses it there has nothing to call on desktop, iOS, macOS or
Wasm: today the only geometry entry point is `Canvas.drawVertices`, whose 2D positions mean the CPU
must transform every vertex every frame. Our use is a 3D renderer for Remote Compose documents. Its
Android path projects, culls and lights convex meshes in an SkSL vertex shader, and on Skiko it
falls back to CPU transforms plus `drawVertices`.

**Proposed surface** (mirroring `android.graphics.Mesh`, which is a thin wrapper over the same
Skia types, so existing SkSL works unchanged):

```kotlin
class MeshSpecification private constructor(ptr: NativePointer) : Managed {
  companion object {
    /** Throws with Skia's error text if the shaders do not compile. */
    fun make(
      attributes: Array<Attribute>,
      vertexStride: Int,
      varyings: Array<Varying>,
      vertexShader: String,
      fragmentShader: String,
      colorSpace: ColorSpace? = null,
      alphaType: ColorAlphaType = ColorAlphaType.PREMUL,
    ): MeshSpecification
  }
  class Attribute(val type: AttributeType, val offset: Int, val name: String)
  class Varying(val type: VaryingType, val name: String)
  enum class AttributeType { FLOAT, FLOAT2, FLOAT3, FLOAT4, UBYTE4_UNORM }
  enum class VaryingType { FLOAT, FLOAT2, FLOAT3, FLOAT4, HALF, HALF2, HALF3, HALF4 }
}

class Mesh private constructor(ptr: NativePointer) : Managed {
  companion object {
    fun make(
      spec: MeshSpecification,
      mode: MeshMode, // TRIANGLES, TRIANGLE_STRIP
      vertices: ByteArray, // or a VertexBuffer made once, see below
      vertexCount: Int,
      bounds: Rect,
      uniforms: Data? = null,
    ): Mesh
    fun makeIndexed(/* … plus an index buffer */): Mesh
  }
  fun setFloatUniform(name: String, vararg values: Float)
  fun setIntUniform(name: String, vararg values: Int)
}

fun Canvas.drawMesh(mesh: Mesh, blender: Blender? /* or BlendMode */, paint: Paint): Canvas
```

`SkMesh::MakeVertexBuffer(GrDirectContext*, …)` (or Graphite's equivalent) lets the buffer live on
the GPU; a version that takes a `DirectContext?` and falls back to a CPU buffer when it is null
keeps raster canvases working, as Skia does.

**Notes.**

- Raster canvases draw meshes too (Skia's CPU backend runs the SkSL), which keeps the API testable
  on headless CI.
- Uniform setters by name match Android's and avoid exposing `SkMeshSpecification::uniforms()`
  offsets; a `uniforms(): List<Uniform>` accessor could come later.
- Child effects (`SkMesh::ChildPtr`, for sampling images in the fragment stage) can be a follow-up;
  `drawMesh`'s paint shader blended with the mesh colour already covers texturing.

**Reference.** Android's binding is in
`frameworks/base/graphics/java/android/graphics/Mesh.java` and
`frameworks/base/libs/hwui/jni/Mesh.cpp`; the Skia API is
[`SkMeshSpecification`](https://api.skia.org/classSkMeshSpecification.html) in
`include/core/SkMesh.h`.

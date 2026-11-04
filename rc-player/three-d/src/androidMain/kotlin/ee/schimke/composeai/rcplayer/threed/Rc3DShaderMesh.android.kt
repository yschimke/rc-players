package ee.schimke.composeai.rcplayer.threed

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.ColorSpace
import android.graphics.Mesh
import android.graphics.MeshSpecification
import android.graphics.MeshSpecification.Attribute
import android.graphics.MeshSpecification.Varying
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.FLOATS_PER_VERTEX
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.MAX_LIGHTS
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.MAX_VERTICES
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal actual class RcShaderMeshDrawer actual constructor() {
  internal val api: RcMeshApi34? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) RcMeshApi34() else null

  // Only on a GPU canvas: that is where it pays, and the path the device tests cover.
  actual fun canDraw(scope: DrawScope): Boolean =
    api?.available == true && scope.drawContext.canvas.nativeCanvas.isHardwareAccelerated

  actual fun draw(
    scope: DrawScope,
    width: Float,
    height: Float,
    mesh: Rc3DMeshData,
    engine: RcSoftware3D,
    smooth: Boolean,
    baseColor: Int,
    texture: ImageBitmap?,
    slot: Int,
  ): Boolean {
    val api = api ?: return false
    if (!canDraw(scope)) return false
    return api.draw(scope, width, height, mesh, engine, smooth, baseColor, texture, slot)
  }

  actual fun dispose() {
    api?.dispose()
  }
}

/**
 * The `android.graphics.Mesh` renderer. Geometry is uploaded once per mesh version (laid out by
 * [Rc3DShaderMeshData]); each draw then sets only uniforms: the matrices, the lights and the
 * material. The vertex shader projects, culls and lights each vertex the way the engine's
 * `projectTriangle` and `litColor` do, so its output matches the CPU vertex path.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class RcMeshApi34 {
  private var spec: MeshSpecification? = null
  private var disabled = false

  /** False once the specification failed to build: every later draw would be declined. */
  val available: Boolean
    get() = !disabled

  /** Uploaded geometry, by mesh: a deforming mesh brings a new one, so only a few are kept. */
  private val uploads = LinkedHashMap<Rc3DMeshData, Upload>()

  /** One `Mesh` per draw in a frame: two draws of one mesh in a frame need separate uniforms. */
  private val slots = mutableListOf<Pair<Upload, Mesh>?>()
  private val paint = Paint()
  private var shaderBitmap: Bitmap? = null
  private var shader: BitmapShader? = null
  private val light = FloatArray(7)
  private val lightPos = FloatArray(MAX_LIGHTS * 4)
  private val lightColor = FloatArray(MAX_LIGHTS * 4)

  private class Upload(val data: Rc3DShaderMeshData, val buffer: ByteBuffer)

  /** Device-test switches: draw back faces too, or every face flat red, to isolate a stage. */
  var debugNoCull = false
  var debugFlatColor = false

  /** The vertex count of the last upload, for the device tests. */
  var lastVertexCount = -1
    private set

  fun draw(
    scope: DrawScope,
    width: Float,
    height: Float,
    mesh: Rc3DMeshData,
    engine: RcSoftware3D,
    smooth: Boolean,
    baseColor: Int,
    texture: ImageBitmap?,
    slot: Int,
  ): Boolean {
    if (disabled) return false
    val spec = spec ?: makeSpec() ?: return false
    engine.prepareShading()
    if (engine.eyeLightCount > MAX_LIGHTS) return false
    val upload = upload(mesh) ?: return false
    lastVertexCount = upload.data.vertexCount
    if (upload.data.vertexCount == 0) return true
    if (!upload.data.inFrontOfNearPlane(engine.modelViewProjection)) return false
    val target =
      runCatching { meshFor(slot, upload, spec) }
        .getOrElse {
          Log.w(TAG, "android.graphics.Mesh could not be created; 3D draws use the vertex path", it)
          disabled = true
          return false
        }
    target.setFloatUniform("mvp", engine.modelViewProjection)
    target.setFloatUniform("mv", engine.modelView)
    target.setFloatUniform("viewport", width, height)
    target.setFloatUniform("debug", if (debugNoCull) 1f else 0f, if (debugFlatColor) 1f else 0f)
    target.setFloatUniform(
      "base",
      ((baseColor shr 16) and 0xFF) / 255f,
      ((baseColor shr 8) and 0xFF) / 255f,
      (baseColor and 0xFF) / 255f,
      ((baseColor ushr 24) and 0xFF) / 255f,
    )
    target.setFloatUniform(
      "shading",
      if (smooth && upload.data.hasNormals) 1f else 0f,
      engine.specularStrength,
      engine.specularShininess,
      if (engine.isPerspective) 1f else 0f,
    )
    val lights = engine.eyeLightCount
    lightPos.fill(0f)
    lightColor.fill(0f)
    for (i in 0 until lights) {
      engine.eyeLight(i, light)
      lightPos[i * 4] = light[1]
      lightPos[i * 4 + 1] = light[2]
      lightPos[i * 4 + 2] = light[3]
      lightPos[i * 4 + 3] = if (light[0].toInt() == Rc3D.LIGHT_POINT) 1f else 0f
      lightColor[i * 4] = light[4]
      lightColor[i * 4 + 1] = light[5]
      lightColor[i * 4 + 2] = light[6]
    }
    target.setFloatUniform("lightCount", lights.toFloat())
    target.setFloatUniform("lightPos", lightPos)
    target.setFloatUniform("lightColor", lightColor)

    val blend: BlendMode
    if (texture != null && upload.data.hasUv) {
      val bitmap = texture.asAndroidBitmap()
      if (bitmap !== shaderBitmap) {
        shaderBitmap = bitmap
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      }
      paint.shader = shader
      target.setFloatUniform("texSize", bitmap.width.toFloat(), bitmap.height.toFloat())
      // The lit colour modulates the texel, as the engine and the vertex path do.
      blend = BlendMode.MODULATE
    } else {
      paint.shader = null
      target.setFloatUniform("texSize", 0f, 0f)
      blend = BlendMode.DST
    }
    scope.drawContext.canvas.nativeCanvas.drawMesh(target, blend, paint)
    return true
  }

  internal fun makeSpec(): MeshSpecification? =
    runCatching {
        MeshSpecification.make(
          arrayOf(
            Attribute(MeshSpecification.TYPE_FLOAT3, 0, "position"),
            Attribute(MeshSpecification.TYPE_FLOAT3, 12, "normal"),
            Attribute(MeshSpecification.TYPE_FLOAT3, 24, "edge1"),
            Attribute(MeshSpecification.TYPE_FLOAT3, 36, "edge2"),
            // Not "centroid": that is a reserved word in GLSL ES 3.0, and SkSL is built from it.
            Attribute(MeshSpecification.TYPE_FLOAT3, 48, "faceCentre"),
            Attribute(MeshSpecification.TYPE_FLOAT2, 60, "uv"),
          ),
          FLOATS_PER_VERTEX * 4,
          arrayOf(
            Varying(MeshSpecification.TYPE_FLOAT4, "shade"),
            Varying(MeshSpecification.TYPE_FLOAT3, "uvw"),
          ),
          VERTEX_SHADER,
          FRAGMENT_SHADER,
          // The vertex shader writes premultiplied sRGB, as the engine's colours are.
          ColorSpace.get(ColorSpace.Named.SRGB),
          MeshSpecification.ALPHA_TYPE_PREMULTIPLIED,
        )
      }
      .onFailure {
        // Once, with the reason: the shaders failed to compile on this device, and every later
        // draw takes the vertex path.
        Log.w(TAG, "android.graphics.Mesh is unavailable; 3D draws use the vertex path", it)
        disabled = true
      }
      .getOrNull()
      .also { spec = it }

  private fun upload(mesh: Rc3DMeshData): Upload? {
    uploads[mesh]?.let {
      return it
    }
    if (mesh.indices.size > MAX_VERTICES) return null
    val data = Rc3DShaderMeshData(mesh)
    val buffer =
      ByteBuffer.allocateDirect(data.vertices.size * 4).order(ByteOrder.nativeOrder()).apply {
        asFloatBuffer().put(data.vertices)
      }
    val upload = Upload(data, buffer)
    uploads[mesh] = upload
    // A deforming mesh brings new geometry every frame; keep only the recent ones.
    while (uploads.size > MAX_UPLOADS) uploads.remove(uploads.keys.first())
    return upload
  }

  private fun meshFor(slot: Int, upload: Upload, spec: MeshSpecification): Mesh {
    while (slots.size <= slot) slots += null
    slots[slot]?.let { (held, mesh) -> if (held === upload) return mesh }
    upload.buffer.rewind()
    val mesh =
      Mesh(
        spec,
        Mesh.TRIANGLES,
        upload.buffer,
        upload.data.vertexCount,
        // Positions follow the camera from frame to frame, so the bounds cannot be known when
        // the mesh is made: wide enough never to reject it.
        RectF(-BOUNDS, -BOUNDS, BOUNDS, BOUNDS),
      )
    if (slot < MAX_SLOTS) slots[slot] = upload to mesh
    return mesh
  }

  fun dispose() {
    uploads.clear()
    slots.clear()
    shaderBitmap = null
    shader = null
  }

  internal companion object {
    const val TAG = "Rc3D"
    const val MAX_UPLOADS = 16
    const val MAX_SLOTS = 64
    const val BOUNDS = 1e6f

    /**
     * Projection, culling and lighting per vertex, following `RcSoftware3D.projectTriangle` and
     * `litColor` / `litColorSpecular`: smooth shading lights each vertex at its own normal, flat
     * shading lights the face normal at its centroid (or at each vertex when glossy), Lambert is
     * two-sided, specular orients the normal to the viewer. A face the engine would cull, by its
     * projected winding, collapses to a point.
     */
    val VERTEX_SHADER =
      """
      uniform float4x4 mvp;
      uniform float4x4 mv;
      uniform float2 viewport;
      uniform float2 debug;
      uniform float4 base;
      uniform float4 shading;
      uniform float lightCount;
      uniform float4 lightPos[$MAX_LIGHTS];
      uniform float4 lightColor[$MAX_LIGHTS];

      // Takes the light, not its index: SkSL only indexes a uniform array with a constant or a
      // loop index, and a function parameter is neither.
      float3 toLight(float4 light, float3 p) {
        if (light.w > 0.5) {
          float3 d = light.xyz - p;
          float l = length(d);
          return l > 0.0 ? d / l : d;
        }
        return light.xyz;
      }

      float2 screen(float4 clip) {
        float2 ndc = clip.xy / clip.w;
        return float2((ndc.x * 0.5 + 0.5) * viewport.x, (0.5 - ndc.y * 0.5) * viewport.y);
      }

      Varyings main(const Attributes a) {
        Varyings v;
        float3 e1 = (mv * float4(a.edge1, 0.0)).xyz;
        float3 e2 = (mv * float4(a.edge2, 0.0)).xyz;
        float3 face = cross(e1, e2);
        float3 faceCentre = (mv * float4(a.faceCentre, 1.0)).xyz;
        // Cull as the engine does, on the projected triangle's winding in window space, so a
        // projection that mirrors (an orthographic camera with bottom above top, say) culls the
        // same faces. The triangle's corners come back from its centroid and edges.
        float3 c0 = a.faceCentre - (a.edge1 + a.edge2) / 3.0;
        float2 s0 = screen(mvp * float4(c0, 1.0));
        float2 s1 = screen(mvp * float4(c0 + a.edge1, 1.0));
        float2 s2 = screen(mvp * float4(c0 + a.edge2, 1.0));
        float area = (s1.x - s0.x) * (s2.y - s0.y) - (s2.x - s0.x) * (s1.y - s0.y);
        if (area > 0.0 && debug.x < 0.5) {
          v.position = float2(0.0, 0.0);
          v.shade = float4(0.0);
          v.uvw = float3(0.0, 0.0, 1.0);
          return v;
        }
        float4 clip = mvp * float4(a.position, 1.0);
        float invW = 1.0 / clip.w;
        float2 ndc = clip.xy * invW;
        v.position = float2((ndc.x * 0.5 + 0.5) * viewport.x, (0.5 - ndc.y * 0.5) * viewport.y);
        v.uvw = float3(a.uv.x * invW, (1.0 - a.uv.y) * invW, invW);

        float3 eye = (mv * float4(a.position, 1.0)).xyz;
        float3 n;
        float3 p;
        if (shading.x > 0.5) {
          n = (mv * float4(a.normal, 0.0)).xyz;
          p = eye;
        } else {
          n = face;
          p = shading.y > 0.0 ? eye : faceCentre;
        }
        float nl = length(n);
        n = nl > 0.0 ? n / nl : float3(0.0);
        float3 diffuse = float3(${Rc3DShaderMeshData.AMBIENT});
        float3 spec = float3(0.0);
        if (shading.y > 0.0) {
          float pl = length(p);
          float3 view = pl > 0.0 ? -p / pl : float3(0.0, 0.0, 1.0);
          if (dot(n, view) < 0.0) n = -n;
          for (int i = 0; i < $MAX_LIGHTS; i++) {
            if (float(i) >= lightCount) break;
            float3 l = toLight(lightPos[i], p);
            float ndl = dot(n, l);
            if (ndl <= 0.0) continue;
            diffuse += lightColor[i].rgb * ndl;
            float3 h = l + view;
            float hl = length(h);
            if (hl <= 0.0) continue;
            float ndh = dot(n, h) / hl;
            if (ndh <= 0.0) continue;
            spec += lightColor[i].rgb * (pow(ndh, shading.z) * shading.y);
          }
        } else {
          for (int i = 0; i < $MAX_LIGHTS; i++) {
            if (float(i) >= lightCount) break;
            diffuse += lightColor[i].rgb * abs(dot(n, toLight(lightPos[i], p)));
          }
        }
        float3 rgb = min(base.rgb * diffuse + spec, float3(1.0));
        v.shade = debug.y > 0.5 ? float4(1.0, 0.0, 0.0, 1.0) : float4(rgb * base.a, base.a);
        return v;
      }
      """
        .trimIndent()

    /** The lit colour, and the texture coordinates in texels, divided back out of w. */
    val FRAGMENT_SHADER =
      """
      uniform float2 texSize;

      float2 main(const Varyings v, out float4 color) {
        color = v.shade;
        return (v.uvw.xy / v.uvw.z) * texSize;
      }
      """
        .trimIndent()
  }
}

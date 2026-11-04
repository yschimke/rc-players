package ee.schimke.composeai.rcplayer.threed

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Mesh
import android.graphics.MeshSpecification
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The platform behaviour the shader-mesh renderer relies on, checked on its own so a failure names
 * the assumption rather than a whole scene: how `Mesh` takes a `float4x4` uniform, which colour
 * `drawMesh` keeps under `BlendMode.DST`, and that a mesh draws at all on a hardware canvas.
 */
@RunWith(AndroidJUnit4::class)
class Rc3DAndroidMeshPlatformTest {
  @Before
  fun needsMeshes() {
    assumeTrue("Mesh needs API 34", Build.VERSION.SDK_INT >= 34)
  }

  private val spec by lazy {
    MeshSpecification.make(
      arrayOf(MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT2, 0, "position")),
      8,
      arrayOf(MeshSpecification.Varying(MeshSpecification.TYPE_FLOAT4, "shade")),
      """
      uniform float4x4 m;
      Varyings main(const Attributes a) {
        Varyings v;
        float4 p = m * float4(a.position, 0.0, 1.0);
        v.position = p.xy / p.w;
        v.shade = float4(1.0, 0.0, 0.0, 1.0);
        return v;
      }
      """
        .trimIndent(),
      """
      float2 main(const Varyings v, out float4 color) {
        color = v.shade;
        return v.position;
      }
      """
        .trimIndent(),
      ColorSpace.get(ColorSpace.Named.SRGB),
      MeshSpecification.ALPHA_TYPE_PREMULTIPLIED,
    )
  }

  private fun triangle(matrix: FloatArray): Mesh {
    // A triangle from (10, 10) to (50, 50), well inside a 128 px canvas before any transform.
    val verts =
      ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).apply {
        asFloatBuffer().put(floatArrayOf(10f, 10f, 50f, 10f, 10f, 50f))
      }
    return Mesh(spec, Mesh.TRIANGLES, verts, 3, RectF(-1e6f, -1e6f, 1e6f, 1e6f)).apply {
      setFloatUniform("m", matrix)
    }
  }

  @Test
  fun aMeshDrawsOnAHardwareCanvas() {
    val pixels = render { it.drawMesh(triangle(IDENTITY), BlendMode.DST, Paint()) }
    val (covered, bounds) = stats(pixels)
    Log.i(TAG, "identity: covered=$covered bounds=$bounds")
    assertTrue("nothing drawn", covered > 0)
  }

  @Test
  fun aFloat4x4UniformIsColumnMajor() {
    // Translation by (60, 0) in column-major order lives in elements 12 and 13.
    val translate = IDENTITY.copyOf().apply { this[12] = 60f }
    val pixels = render { it.drawMesh(triangle(translate), BlendMode.DST, Paint()) }
    val (covered, bounds) = stats(pixels)
    Log.i(TAG, "translate x+60: covered=$covered bounds=$bounds")
    assertTrue("nothing drawn", covered > 0)
    assertTrue("not moved right by 60: $bounds", bounds[0] >= 65)
  }

  @Test
  fun blendModeDstKeepsTheMeshColour() {
    val paint = Paint().apply { color = Color.GREEN }
    val pixels = render { it.drawMesh(triangle(IDENTITY), BlendMode.DST, paint) }
    val centre = pixels[20 * SIZE + 20]
    Log.i(TAG, "DST over green paint: centre=${Integer.toHexString(centre)}")
    assertEquals("the mesh's red", 0xFFFF0000.toInt(), centre)
  }

  private fun stats(pixels: IntArray): Pair<Int, List<Int>> {
    var l = SIZE
    var t = SIZE
    var r = -1
    var b = -1
    var covered = 0
    for (i in pixels.indices) {
      if (pixels[i] ushr 24 == 0) continue
      covered++
      val x = i % SIZE
      val y = i / SIZE
      l = minOf(l, x)
      t = minOf(t, y)
      r = maxOf(r, x)
      b = maxOf(b, y)
    }
    return covered to listOf(l, t, r, b)
  }

  private fun render(draw: (android.graphics.Canvas) -> Unit): IntArray {
    val reader =
      ImageReader.newInstance(
        SIZE,
        SIZE,
        PixelFormat.RGBA_8888,
        1,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
      )
    val node = RenderNode("probe").apply { setPosition(0, 0, SIZE, SIZE) }
    draw(node.beginRecording())
    node.endRecording()
    val renderer = HardwareRenderer()
    try {
      renderer.isOpaque = false
      renderer.setSurface(reader.surface)
      renderer.setContentRoot(node)
      renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
      val image = reader.acquireNextImage()
      val buffer = image.hardwareBuffer!!
      val bitmap =
        Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))!!
          .copy(Bitmap.Config.ARGB_8888, false)
      buffer.close()
      image.close()
      return IntArray(SIZE * SIZE).also { bitmap.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
    } finally {
      renderer.destroy()
      reader.close()
    }
  }

  private companion object {
    const val TAG = "Rc3DProbe"
    const val SIZE = 128
    val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
  }
}

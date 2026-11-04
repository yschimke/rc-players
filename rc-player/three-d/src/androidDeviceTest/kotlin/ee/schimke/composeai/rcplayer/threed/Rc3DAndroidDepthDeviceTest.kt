package ee.schimke.composeai.rcplayer.threed

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3D
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The GLES depth renderer against the software rasterizer it stands in for, on a real hardware
 * canvas: scenes that need depth (a torus, intersecting meshes) must cover the same pixels in the
 * same colours. Robolectric's host graphics have no GL, so this runs on an emulator (API 29+).
 */
@RunWith(AndroidJUnit4::class)
class Rc3DAndroidDepthDeviceTest {
  @Before
  fun needsGles3() {
    assumeTrue("the depth renderer needs API 29", Build.VERSION.SDK_INT >= 29)
  }

  @Test
  fun torus() = compare("torus") { listOf(torus(it)) }

  @Test
  fun intersectingCubes() =
    compare("intersecting-cubes") {
      listOf(cube(it, 0.6f, -0.3f, 0xFFEE8844.toInt()), cube(it, 0.2f, 0.3f, 0xFF4488EE.toInt()))
    }

  @Test
  fun glossyCube() =
    compare("glossy-cube") {
      it.setMaterial(0.6f, 24f)
      listOf(cube(it, 0.6f, 0f, 0xFFEE8844.toInt()))
    }

  @Test
  fun texturedSphere() = compare("textured-sphere", checker()) { listOf(sphere(it)) }

  /** A scene: per draw, a setup that leaves the engine ready to draw mesh 1. */
  private fun compare(
    name: String,
    texture: ImageBitmap? = null,
    scene: (RcSoftware3D) -> List<(RcSoftware3D) -> Unit>,
  ) {
    val software = renderSoftware(texture, scene)
    val renderer = RcDepthRenderer()
    var drew = 0
    val gpu =
      renderOnGpu { scope ->
        val engine = engine(texture)
        renderer.beginFrame()
        renderer.clearDepth()
        for (setup in scene(engine)) {
          setup(engine)
          val ok =
            renderer.draw(
              scope,
              Size(SIZE.toFloat(), SIZE.toFloat()),
              SIZE,
              SIZE,
              engine.mesh(1)!!,
              engine,
              true,
              texture,
            )
          if (ok) drew++
        }
      }
    renderer.dispose()
    val a = IntArray(SIZE * SIZE).also { gpu.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
    val coveredA = a.count { (it ushr 24) > 127 }
    val coveredB = software.count { (it ushr 24) > 127 }
    val differ = a.indices.count { ((a[it] ushr 24) > 127) != ((software[it] ushr 24) > 127) }
    var total = 0L
    var both = 0
    for (i in a.indices) {
      if ((a[i] ushr 24) < 255 || (software[i] ushr 24) < 255) continue
      for (shift in intArrayOf(0, 8, 16)) {
        total += abs(((a[i] shr shift) and 0xFF) - ((software[i] shr shift) and 0xFF))
      }
      both++
    }
    val mean = total.toDouble() / (both * 3).coerceAtLeast(1)
    Log.i(
      "Rc3DProbe",
      "depth $name: drew=$drew gpu covered=$coveredA software covered=$coveredB differ=$differ mean=$mean",
    )
    assertTrue("$name: the depth renderer declined", drew > 0)
    assertTrue("$name: drew nothing", coveredA > 0)
    assertTrue("$name: coverage differs on $differ of $coveredB", differ <= coveredB / 25)
    assertTrue("$name: mean colour difference $mean", mean <= 6.0)
  }

  private fun engine(texture: ImageBitmap?): RcSoftware3D =
    RcSoftware3D().apply {
      setSize(SIZE, SIZE)
      setCamera(
        Rc3D.PROJECTION_PERSPECTIVE,
        floatArrayOf(0.9f, 1f, 0.1f, 100f),
        floatArrayOf(0f, 0.4f, 4f, 0f, 0f, 0f, 0f, 1f, 0f),
      )
      setLights(listOf(Rc3D.LIGHT_DIRECTIONAL), listOf(-1), floatArrayOf(-0.3f, -0.5f, -0.8f, 1f))
      if (texture != null) {
        val pixels = IntArray(texture.width * texture.height).also { texture.readPixels(it) }
        setTexture(pixels, texture.width, texture.height)
      }
    }

  private fun renderSoftware(
    texture: ImageBitmap?,
    scene: (RcSoftware3D) -> List<(RcSoftware3D) -> Unit>,
  ): IntArray {
    val engine = engine(texture)
    engine.clearDepth()
    for (setup in scene(engine)) {
      setup(engine)
      engine.drawMesh(1, Rc3D.MODE_SOFTWARE_SMOOTH)
    }
    // The rasterizer writes unpremultiplied ARGB; read it as a bitmap would show it.
    val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(engine.colorBuffer, 0, SIZE, 0, 0, SIZE, SIZE)
    return IntArray(SIZE * SIZE).also { bitmap.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
  }

  private fun cube(engine: RcSoftware3D, angle: Float, x: Float, color: Int): (RcSoftware3D) -> Unit = {
    it.baseColorArgb = color
    it.matrixOp(Rc3D.M3_IDENTITY, FloatArray(0))
    it.matrixOp(Rc3D.M3_TRANSLATE, floatArrayOf(x, 0f, 0f))
    it.matrixOp(Rc3D.M3_ROTATE_AXIS, floatArrayOf(angle, 0f, 1f, 0f))
    it.matrixOp(Rc3D.M3_ROTATE_AXIS, floatArrayOf(0.35f, 1f, 0f, 0f))
    it.defineMesh(
      1,
      RcPrimitive3D.build(RcPrimitive3D.CUBE, 0f, 0, listOf(floatArrayOf(1.2f, 1.2f, 1.2f, 0f, 0f, 0f))),
    )
  }

  private fun torus(engine: RcSoftware3D): (RcSoftware3D) -> Unit = {
    it.baseColorArgb = 0xFF4488EE.toInt()
    it.matrixOp(Rc3D.M3_IDENTITY, FloatArray(0))
    it.matrixOp(Rc3D.M3_ROTATE_AXIS, floatArrayOf(1.1f, 1f, 0f, 0f))
    it.defineMesh(
      1,
      RcPrimitive3D.build(RcPrimitive3D.TORUS, 32f, 0, listOf(floatArrayOf(1.0f, 0.35f, 0f, 0f, 0f))),
    )
  }

  private fun sphere(engine: RcSoftware3D): (RcSoftware3D) -> Unit = {
    it.baseColorArgb = -1
    it.matrixOp(Rc3D.M3_IDENTITY, FloatArray(0))
    it.matrixOp(Rc3D.M3_ROTATE_AXIS, floatArrayOf(0.8f, 0f, 1f, 0f))
    it.defineMesh(
      1,
      RcPrimitive3D.build(RcPrimitive3D.SPHERE, 24f, 16, listOf(floatArrayOf(1.2f, 0f, 0f, 0f))),
    )
  }

  private fun checker(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    for (y in 0 until 64) for (x in 0 until 64) {
      bitmap.setPixel(x, y, if ((x / 8 + y / 8) % 2 == 0) -1 else 0xFF3355AA.toInt())
    }
    return bitmap.asImageBitmap()
  }

  private fun renderOnGpu(draw: (DrawScope) -> Unit): Bitmap {
    val reader =
      ImageReader.newInstance(
        SIZE,
        SIZE,
        PixelFormat.RGBA_8888,
        1,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
      )
    val node = RenderNode("rc3d-depth").apply { setPosition(0, 0, SIZE, SIZE) }
    val recording = node.beginRecording()
    CanvasDrawScope()
      .draw(Density(1f), LayoutDirection.Ltr, Canvas(recording), Size(SIZE.toFloat(), SIZE.toFloat())) {
        draw(this)
      }
    node.endRecording()
    val renderer = HardwareRenderer()
    try {
      renderer.isOpaque = false
      renderer.setSurface(reader.surface)
      renderer.setContentRoot(node)
      renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
      val image = reader.acquireNextImage()
      val buffer = image.hardwareBuffer!!
      val copy =
        Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))!!
          .copy(Bitmap.Config.ARGB_8888, false)
      buffer.close()
      image.close()
      return copy
    } finally {
      renderer.destroy()
      reader.close()
    }
  }

  private companion object {
    const val SIZE = 256
  }
}

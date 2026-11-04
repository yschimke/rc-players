package ee.schimke.composeai.rcplayer.threed

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3D
import ee.schimke.composeai.rcplayer.threed.engine.RcScreenMesh
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `android.graphics.Mesh` renderer against the CPU-transformed vertex path, on a real hardware
 * canvas: the same scene drawn both ways must cover the same pixels in the same colours. Robolectric's
 * host graphics have no mesh support, so this runs on an emulator (API 34+).
 *
 * The renders are written under the app's external files directory, `rc3d-mesh/`, for the PR.
 */
@RunWith(AndroidJUnit4::class)
class Rc3DAndroidMeshDeviceTest {
  @Before
  fun needsMeshes() {
    assumeTrue("Mesh needs API 34", Build.VERSION.SDK_INT >= 34)
  }

  @Test fun smoothCube() = compare("smooth-cube") { cube(it, smooth = true) }

  @Test fun flatSphereTwoLights() = compare("flat-sphere") { sphere(it, smooth = false, lights = 2) }

  @Test
  fun glossyCube() =
    compare("glossy-cube") {
      it.setMaterial(0.6f, 24f)
      cube(it, smooth = false)
    }

  @Test fun orthographicCube() = compare("ortho-cube") { cube(it, smooth = true, ortho = true) }

  @Test
  fun mirroredOrthographicCube() = compare("mirrored-ortho-cube") { cube(it, smooth = true, ortho = true, mirrored = true) }

  @Test
  fun texturedSphere() = compare("textured-sphere", checker()) { sphere(it, smooth = true, uv = true) }

  /** Sets up [engine] for a scene and returns the mesh id to draw and whether it is smooth. */
  private fun compare(
    name: String,
    texture: ImageBitmap? = null,
    scene: (RcSoftware3D) -> Pair<Int, Boolean>,
  ) {
    val viaMesh = renderScene(texture, scene, mesh = true)
    val viaVertices = renderScene(texture, scene, mesh = false)
    save("$name-mesh", viaMesh)
    save("$name-vertices", viaVertices)
    val a = IntArray(SIZE * SIZE).also { viaMesh.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
    val b = IntArray(SIZE * SIZE).also { viaVertices.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
    val coveredA = a.count { (it ushr 24) > 127 }
    val coveredB = b.count { (it ushr 24) > 127 }
    android.util.Log.i(
      "Rc3DProbe",
      "$name: mesh covered=$coveredA vertices covered=$coveredB " +
        "mesh centre=${Integer.toHexString(a[SIZE / 2 * SIZE + SIZE / 2])} " +
        "vertices centre=${Integer.toHexString(b[SIZE / 2 * SIZE + SIZE / 2])}",
    )
    assertTrue("$name: the mesh path drew nothing", coveredA > 0)
    val coverageDiffers = a.indices.count { ((a[it] ushr 24) > 127) != ((b[it] ushr 24) > 127) }
    assertTrue(
      "$name: coverage differs on $coverageDiffers of $coveredB pixels",
      coverageDiffers <= coveredB / 25,
    )
    var total = 0L
    var both = 0
    for (i in a.indices) {
      if ((a[i] ushr 24) < 255 || (b[i] ushr 24) < 255) continue
      for (shift in intArrayOf(0, 8, 16)) {
        total += abs(((a[i] shr shift) and 0xFF) - ((b[i] shr shift) and 0xFF))
      }
      both++
    }
    val mean = total.toDouble() / (both * 3).coerceAtLeast(1)
    assertTrue("$name: mean colour difference $mean", mean <= 4.0)
  }

  private fun renderScene(
    texture: ImageBitmap?,
    scene: (RcSoftware3D) -> Pair<Int, Boolean>,
    mesh: Boolean,
  ): Bitmap {
    val engine = RcSoftware3D().apply { setSize(SIZE, SIZE) }
    engine.baseColorArgb = 0xFFEE8844.toInt()
    val (meshId, smooth) = scene(engine)
    return renderOnGpu { scope ->
      if (mesh) {
        val drew =
          RcShaderMeshDrawer()
            .draw(
              scope,
              SIZE.toFloat(),
              SIZE.toFloat(),
              engine.mesh(meshId)!!,
              engine,
              smooth,
              engine.baseColorArgb,
              texture,
              0,
            )
        assertTrue("the mesh path declined the draw", drew)
      } else {
        val screen = RcScreenMesh()
        val count = engine.buildScreenVertices(meshId, screen, smooth)
        RcTriangleDrawer()
          .draw(
            scope,
            screen.positions,
            screen.colors,
            if (texture != null && screen.hasUv) screen.uvs else null,
            count,
            texture,
          )
      }
    }
  }

  private fun camera(engine: RcSoftware3D, ortho: Boolean, mirrored: Boolean = false) {
    val view = floatArrayOf(0f, 0.4f, 4f, 0f, 0f, 0f, 0f, 1f, 0f)
    if (ortho) {
      // Mirrored: bottom above top, a y-down projection that flips every triangle's winding.
      val (bottom, top) = if (mirrored) 1.5f to -1.5f else -1.5f to 1.5f
      engine.setCamera(Rc3D.PROJECTION_ORTHO, floatArrayOf(-1.5f, 1.5f, bottom, top, 0.1f, 100f), view)
    } else {
      engine.setCamera(Rc3D.PROJECTION_PERSPECTIVE, floatArrayOf(0.9f, 1f, 0.1f, 100f), view)
    }
  }

  private fun cube(
    engine: RcSoftware3D,
    smooth: Boolean,
    ortho: Boolean = false,
    mirrored: Boolean = false,
  ): Pair<Int, Boolean> {
    camera(engine, ortho, mirrored)
    engine.setLights(listOf(Rc3D.LIGHT_DIRECTIONAL), listOf(-1), floatArrayOf(-0.3f, -0.5f, -0.8f, 1f))
    engine.matrixOp(Rc3D.M3_ROTATE_AXIS, floatArrayOf(0.6f, 0f, 1f, 0f))
    engine.matrixOp(Rc3D.M3_ROTATE_AXIS, floatArrayOf(0.35f, 1f, 0f, 0f))
    val mesh = RcPrimitive3D.build(RcPrimitive3D.CUBE, 0f, 0, listOf(floatArrayOf(1.4f, 1.4f, 1.4f, 0f, 0f, 0f)))
    engine.defineMesh(1, mesh)
    return 1 to smooth
  }

  private fun sphere(
    engine: RcSoftware3D,
    smooth: Boolean,
    lights: Int = 1,
    uv: Boolean = false,
  ): Pair<Int, Boolean> {
    camera(engine, ortho = false)
    if (lights == 2) {
      engine.setLights(
        listOf(Rc3D.LIGHT_DIRECTIONAL, Rc3D.LIGHT_POINT),
        listOf(0xFFFFFFFF.toInt(), 0xFF88AAFF.toInt()),
        floatArrayOf(-0.3f, -0.5f, -0.8f, 0.8f, 2f, 1f, 3f, 0.6f),
      )
    } else {
      engine.setLights(listOf(Rc3D.LIGHT_DIRECTIONAL), listOf(-1), floatArrayOf(-0.3f, -0.5f, -0.8f, 1f))
    }
    val mesh =
      RcPrimitive3D.build(RcPrimitive3D.SPHERE, 24f, if (uv) 16 else 0, listOf(floatArrayOf(1.2f, 0f, 0f, 0f)))
    engine.defineMesh(2, mesh)
    return 2 to smooth
  }

  private fun checker(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    for (y in 0 until 64) for (x in 0 until 64) {
      bitmap.setPixel(x, y, if ((x / 8 + y / 8) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF3355AA.toInt())
    }
    return bitmap.asImageBitmap()
  }

  /** Records [draw] into a render node and renders it with HWUI into a readable buffer. */
  private fun renderOnGpu(draw: (DrawScope) -> Unit): Bitmap {
    val reader =
      ImageReader.newInstance(
        SIZE,
        SIZE,
        PixelFormat.RGBA_8888,
        1,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
      )
    val node = RenderNode("rc3d").apply { setPosition(0, 0, SIZE, SIZE) }
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
      val hardware = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))!!
      val copy = hardware.copy(Bitmap.Config.ARGB_8888, false)
      buffer.close()
      image.close()
      return copy
    } finally {
      renderer.destroy()
      reader.close()
    }
  }

  private fun save(name: String, bitmap: Bitmap) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val dir = File(context.getExternalFilesDir(null), "rc3d-mesh").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private companion object {
    const val SIZE = 256
  }
}

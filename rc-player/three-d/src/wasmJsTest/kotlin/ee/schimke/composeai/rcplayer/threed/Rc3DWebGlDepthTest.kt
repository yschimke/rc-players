package ee.schimke.composeai.rcplayer.threed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3D
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * The WebGL2 depth renderer against the software rasterizer it stands in for, in a real browser:
 * the scenes `Rc3DAndroidDepthDeviceTest` draws on Android (a torus, intersecting meshes, a glossy
 * cube, a textured sphere) must cover the same pixels (within 4%) in the same colours (a mean
 * difference of at most 6 levels).
 *
 * Each case also times both paths over [FRAMES] frames, composite included, and prints them with
 * the `Rc3DProbe` prefix: the measurements behind the design doc's Wasm row. Without a GPU (a
 * headless runner) WebGL2 is SwiftShader, itself a CPU rasterizer, so the times there say what the
 * copy costs rather than what a GPU gains.
 */
class Rc3DWebGlDepthTest {
  @Test fun torus() = compare("torus") { listOf(torusMesh()) }

  @Test
  fun intersectingCubes() =
    compare("intersecting-cubes") {
      listOf(cube(0.6f, -0.3f, 0xFFEE8844.toInt()), cube(0.2f, 0.3f, 0xFF4488EE.toInt()))
    }

  @Test
  fun glossyCube() =
    compare("glossy-cube") {
      it.setMaterial(0.6f, 24f)
      listOf(cube(0.6f, 0f, 0xFFEE8844.toInt()))
    }

  @Test fun texturedSphere() = compare("textured-sphere", checker()) { listOf(sphereMesh()) }

  /** Depth lasts the pass: a second frame with no clear in between hides the mesh behind itself. */
  @Test
  fun depthPersistsUntilCleared() {
    val renderer = RcDepthRenderer()
    if (!renderer.webgl.available) return skip(renderer)
    val engine = engine(null)
    val setup = cube(0.6f, 0f, 0xFFEE8844.toInt())
    fun frame(clear: Boolean): Int {
      val pixels =
        drawInto(SIZE) { scope ->
          renderer.beginFrame()
          if (clear) renderer.clearDepth()
          setup(engine)
          renderer.draw(
            scope,
            Size(SIZE.toFloat(), SIZE.toFloat()),
            SIZE,
            SIZE,
            engine.mesh(1)!!,
            engine,
            true,
            null,
          )
        }
      return pixels.count { (it ushr 24) > 127 }
    }
    val first = frame(clear = true)
    // The same mesh at the same depth fails GL_LESS everywhere it drew before.
    val again = frame(clear = false)
    val cleared = frame(clear = true)
    renderer.dispose()
    assertTrue(first > 0, "drew nothing")
    assertTrue(again == 0, "depth was not kept: $again pixels drew again")
    assertTrue(cleared == first, "a cleared pass drew $cleared of $first")
  }

  private fun compare(
    name: String,
    texture: ImageBitmap? = null,
    scene: (RcSoftware3D) -> List<(RcSoftware3D) -> Unit>,
  ) {
    val renderer = RcDepthRenderer()
    if (!renderer.webgl.available) return skip(renderer)
    val software = renderSoftware(texture, scene)
    var drew = 0
    val gpu =
      drawInto(SIZE) { scope ->
        val engine = engine(texture)
        renderer.beginFrame()
        renderer.clearDepth()
        for (setup in scene(engine)) {
          setup(engine)
          val size = Size(SIZE.toFloat(), SIZE.toFloat())
          if (renderer.draw(scope, size, SIZE, SIZE, engine.mesh(1)!!, engine, true, texture))
            drew++
        }
      }
    val available = renderer.webgl.available
    val coveredA = gpu.count { (it ushr 24) > 127 }
    val coveredB = software.count { (it ushr 24) > 127 }
    val differ = gpu.indices.count { ((gpu[it] ushr 24) > 127) != ((software[it] ushr 24) > 127) }
    var total = 0L
    var both = 0
    for (i in gpu.indices) {
      if ((gpu[i] ushr 24) < 255 || (software[i] ushr 24) < 255) continue
      for (shift in intArrayOf(0, 8, 16)) {
        total += abs(((gpu[i] shr shift) and 0xFF) - ((software[i] shr shift) and 0xFF))
      }
      both++
    }
    val mean = total.toDouble() / (both * 3).coerceAtLeast(1)
    println(
      "Rc3DProbe depth $name: drew=$drew gpu covered=$coveredA software covered=$coveredB " +
        "differ=$differ mean=$mean"
    )
    for (size in intArrayOf(SIZE, BENCH_SIZE)) benchmark(name, size, texture, scene, renderer)
    renderer.dispose()
    assertTrue(available, "$name: the renderer disabled itself")
    assertTrue(drew > 0, "$name: the depth renderer declined")
    assertTrue(coveredA > 0, "$name: drew nothing")
    assertTrue(differ <= coveredB / 25, "$name: coverage differs on $differ of $coveredB")
    assertTrue(mean <= 6.0, "$name: mean colour difference $mean")
  }

  /**
   * Per-frame time of each path at [size] pixels, as the session runs them: the software path
   * rasterizes, converts the touched rectangle and composites it; the WebGL2 path draws, reads back
   * and composites the frame.
   */
  private fun benchmark(
    name: String,
    size: Int,
    texture: ImageBitmap?,
    scene: (RcSoftware3D) -> List<(RcSoftware3D) -> Unit>,
    renderer: RcDepthRenderer,
  ) {
    val target =
      Bitmap().apply { allocPixels(ImageInfo.makeN32(size, size, ColorAlphaType.PREMUL)) }
    val canvas = Canvas(target)
    val drawScope = CanvasDrawScope()
    val extent = Size(size.toFloat(), size.toFloat())
    val software = engine(texture, size)
    val softwareSetups = scene(software)
    val frame = RcArgbFrame(size, size)
    val gpu = engine(texture, size)
    val gpuSetups = scene(gpu)
    fun time(block: (DrawScope) -> Unit): Double {
      // One frame to warm up: programs, buffers and textures are made once.
      repeat(FRAMES + 1) {
        if (it == 1) start = TimeSource.Monotonic.markNow()
        drawScope.draw(Density(1f), LayoutDirection.Ltr, canvas.asComposeCanvas(), extent) {
          block(this)
        }
      }
      return start.elapsedNow().inWholeMicroseconds / 1000.0 / FRAMES
    }
    val softwareMs = time { scope ->
      software.clearDepth()
      for (setup in softwareSetups) {
        setup(software)
        software.drawMesh(1, Rc3D.MODE_SOFTWARE_SMOOTH)
        val l = software.dirtyLeft
        val t = software.dirtyTop
        val r = software.dirtyRight
        val b = software.dirtyBottom
        if (l >= r || t >= b) continue
        frame.update(software.colorBuffer, l, t, r, b)
        software.clearColor()
        scope.scale(1f, 1f, Offset.Zero) {
          drawImage(
            frame.image,
            srcOffset = IntOffset(l, t),
            srcSize = IntSize(r - l, b - t),
            dstOffset = IntOffset(l, t),
            dstSize = IntSize(r - l, b - t),
          )
        }
      }
    }
    val gpuMs = time { scope ->
      renderer.beginFrame()
      renderer.clearDepth()
      for (setup in gpuSetups) {
        setup(gpu)
        renderer.draw(scope, extent, size, size, gpu.mesh(1)!!, gpu, true, texture)
      }
    }
    println(
      "Rc3DProbe bench $name ${size}px: software=${round(softwareMs)} ms " +
        "webgl2=${round(gpuMs)} ms per frame"
    )
  }

  private var start = TimeSource.Monotonic.markNow()

  private fun round(ms: Double): String = ((ms * 100).toInt() / 100.0).toString()

  private fun skip(renderer: RcDepthRenderer) {
    // A browser without WebGL2 is not what this tests; the software rasterizer covers it.
    println("Rc3DProbe: no WebGL2 here, skipped")
    renderer.dispose()
  }

  private fun engine(texture: ImageBitmap?, size: Int = SIZE): RcSoftware3D =
    RcSoftware3D().apply {
      setSize(size, size)
      setCamera(
        Rc3D.PROJECTION_PERSPECTIVE,
        floatArrayOf(0.9f, 1f, 0.1f, 100f),
        floatArrayOf(0f, 0.4f, 4f, 0f, 0f, 0f, 0f, 1f, 0f),
      )
      setLights(listOf(Rc3D.LIGHT_DIRECTIONAL), listOf(-1), floatArrayOf(-0.3f, -0.5f, -0.8f, 1f))
      if (texture != null) setTexture(argb(texture), texture.width, texture.height)
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
    // The rasterizer writes unpremultiplied ARGB, which is what [drawInto] reads back too.
    return engine.colorBuffer.copyOf()
  }

  // Each mesh is built once per scene, as a session keeps a primitive until its inputs change, so
  // the timings are of drawing rather than of rebuilding and re-uploading geometry.
  private fun cube(angle: Float, x: Float, color: Int): (RcSoftware3D) -> Unit {
    val mesh =
      RcPrimitive3D.build(
        RcPrimitive3D.CUBE,
        0f,
        0,
        listOf(floatArrayOf(1.2f, 1.2f, 1.2f, 0f, 0f, 0f)),
      )
    return {
      it.baseColorArgb = color
      it.matrixOp(Rc3D.M3_IDENTITY, FloatArray(0))
      it.matrixOp(Rc3D.M3_TRANSLATE, floatArrayOf(x, 0f, 0f))
      it.matrixOp(Rc3D.M3_ROTATE_AXIS, floatArrayOf(angle, 0f, 1f, 0f))
      it.matrixOp(Rc3D.M3_ROTATE_AXIS, floatArrayOf(0.35f, 1f, 0f, 0f))
      it.defineMesh(1, mesh)
    }
  }

  private fun torusMesh(): (RcSoftware3D) -> Unit {
    val mesh =
      RcPrimitive3D.build(
        RcPrimitive3D.TORUS,
        32f,
        0,
        listOf(floatArrayOf(1.0f, 0.35f, 0f, 0f, 0f)),
      )
    return {
      it.baseColorArgb = 0xFF4488EE.toInt()
      it.matrixOp(Rc3D.M3_IDENTITY, FloatArray(0))
      it.matrixOp(Rc3D.M3_ROTATE_AXIS, floatArrayOf(1.1f, 1f, 0f, 0f))
      it.defineMesh(1, mesh)
    }
  }

  private fun sphereMesh(): (RcSoftware3D) -> Unit {
    val mesh =
      RcPrimitive3D.build(RcPrimitive3D.SPHERE, 24f, 16, listOf(floatArrayOf(1.2f, 0f, 0f, 0f)))
    return {
      it.baseColorArgb = -1
      it.matrixOp(Rc3D.M3_IDENTITY, FloatArray(0))
      it.matrixOp(Rc3D.M3_ROTATE_AXIS, floatArrayOf(0.8f, 0f, 1f, 0f))
      it.defineMesh(1, mesh)
    }
  }

  private fun checker(): ImageBitmap {
    val pixels = IntArray(64 * 64)
    for (y in 0 until 64) for (x in 0 until 64) {
      pixels[y * 64 + x] = if ((x / 8 + y / 8) % 2 == 0) -1 else 0xFF3355AA.toInt()
    }
    val frame = RcArgbFrame(64, 64)
    frame.update(pixels, 0, 0, 64, 64)
    return frame.image
  }

  /** [texture]'s pixels as unpremultiplied ARGB, as the software rasterizer takes them. */
  private fun argb(texture: ImageBitmap): IntArray = readArgb(texture.asSkiaBitmap())

  /** Draw into a fresh [size]-square raster canvas and read it back as unpremultiplied ARGB. */
  private fun drawInto(size: Int, draw: (DrawScope) -> Unit): IntArray {
    val target =
      Bitmap().apply { allocPixels(ImageInfo.makeN32(size, size, ColorAlphaType.PREMUL)) }
    val canvas = Canvas(target)
    CanvasDrawScope().draw(
      Density(1f),
      LayoutDirection.Ltr,
      canvas.asComposeCanvas(),
      Size(size.toFloat(), size.toFloat()),
    ) {
      draw(this)
    }
    canvas.close()
    return readArgb(target)
  }

  private fun readArgb(bitmap: Bitmap): IntArray {
    val info = ImageInfo(bitmap.width, bitmap.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    val bytes = bitmap.readPixels(info, bitmap.width * 4, 0, 0)!!
    return IntArray(bitmap.width * bitmap.height) {
      val o = it * 4
      (bytes[o].toInt() and 0xFF) or
        ((bytes[o + 1].toInt() and 0xFF) shl 8) or
        ((bytes[o + 2].toInt() and 0xFF) shl 16) or
        ((bytes[o + 3].toInt() and 0xFF) shl 24)
    }
  }

  private companion object {
    const val SIZE = 256
    const val BENCH_SIZE = 768
    const val FRAMES = 20
  }
}

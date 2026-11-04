package ee.schimke.composeai.rcplayer.threed

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.compose.LocalRcPlayerExtensions
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcPlayerExtensions
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcWireException
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * End to end: documents written with the draft 3D operations, encoded, decoded with the extension
 * and played by `RcComposePlayer` on desktop Skia, through both the software rasterizer and the GPU
 * (`drawVertices`) path.
 *
 * The XR checks mirror compose-ai-tools' `SpatialRemoteComposeTest` for the path-drawn cube: the
 * document paints nothing but the shape, so a host surface keeps alpha and composites it over a
 * room; and a host camera moves the view without re-sending the document.
 */
class Rc3DRenderTest {
  @Test
  fun withoutTheExtensionADocumentFailsToDecodeAsOnAndroidX() {
    val bytes = Rc3DDocuments.litCube(Rc3D.MODE_SOFTWARE_SMOOTH).encode()
    assertFailsWith<RcWireException> { RcDocumentCodec.decode(bytes) }
  }

  @Test
  fun everyOperationRoundTripsThroughTheWire() {
    val document = Rc3DDocuments.everyOperation().build()
    val extensions = Rc3DExtension.wireExtensions
    val bytes = RcDocumentCodec.encode(document, extensions)
    val decoded = RcDocumentCodec.decode(bytes, extensions)
    assertEquals(document.operations, decoded.operations)
    assertEquals(
      Rc3DOpcodes.ALL,
      decoded.operations.filterIsInstance<Rc3DOperation>().map { it.opcode }.toSet(),
    )
    assertTrue(bytes.contentEquals(RcDocumentCodec.encode(decoded, extensions)))
  }

  @Test
  fun softwareCubePaintsOnlyTheCube() {
    val pixels = render(Rc3DDocuments.litCube(Rc3D.MODE_SOFTWARE_SMOOTH).encode())
    assertTransparentCornersAndOpaqueCentre(pixels)
  }

  @Test
  fun gpuCubeCoversTheSamePixelsAsTheSoftwareCube() {
    val software = render(Rc3DDocuments.litCube(Rc3D.MODE_SOFTWARE_SMOOTH).encode())
    val gpu = render(Rc3DDocuments.litCube(Rc3D.MODE_CANVAS_SMOOTH).encode())
    assertTransparentCornersAndOpaqueCentre(gpu)
    // A convex shape culled to its front faces has no depth conflicts, so the two rasterizers
    // differ only along the silhouette's antialiased edge.
    val covered = { p: IntArray -> p.count { (it ushr 24) > 127 } }
    val differ =
      software.indices.count { ((software[it] ushr 24) > 127) != ((gpu[it] ushr 24) > 127) }
    assertTrue(
      differ < covered(software) / 20,
      "coverage differs on $differ of ${covered(software)}",
    )
  }

  @Test
  fun forcingSoftwareOverridesAnAcceleratedMode() {
    val doc = Rc3DDocuments.litCube(Rc3D.MODE_CANVAS_SMOOTH).encode()
    val forced = render(doc, Rc3DOptions(renderer = Rc3DRenderer.Software))
    val software = render(Rc3DDocuments.litCube(Rc3D.MODE_SOFTWARE_SMOOTH).encode())
    assertTrue(forced.contentEquals(software))
  }

  @Test
  fun aHostCameraMovesTheViewWithoutReSendingTheDocument() {
    val bytes = Rc3DDocuments.hostCameraCube().encode()
    val front = Rc3DHostCamera(eye = floatArrayOf(0f, 0f, 4f))
    val side = Rc3DHostCamera(eye = floatArrayOf(2.5f, 1.5f, 2.5f))
    val fromDefault = render(bytes)
    val fromFront = render(bytes, namedValues = front.namedValues())
    val fromSide = render(bytes, namedValues = side.namedValues())
    assertTrue(fromDefault.contentEquals(fromFront), "the default pose is the front pose")
    assertNotEquals(fromFront.toList(), fromSide.toList())
    assertTransparentCornersAndOpaqueCentre(fromSide)
  }

  @Test
  fun aSceneListenerCanTakeOverDrawing() {
    val draws = mutableListOf<Rc3DMeshDraw>()
    val pixels =
      render(
        Rc3DDocuments.litCube(Rc3D.MODE_SOFTWARE_SMOOTH).encode(),
        Rc3DOptions(drawInPlayer = false, sceneListener = { draws += it }),
      )
    assertTrue(pixels.all { it ushr 24 == 0 }, "nothing drawn by the player")
    val draw = draws.first()
    assertEquals(36, draw.mesh.indices.size, "a cube is 12 triangles")
    assertEquals(SIZE.toFloat(), draw.viewportWidth)
    // The model rotation reached the listener: not the identity.
    assertNotEquals(1f, draw.model[0])
  }

  @Test
  fun aSceneListenerGetsWholeFramesWithStableKeysAndMeshVersions() {
    val frames = mutableListOf<Rc3DFrame>()
    val listener =
      object : Rc3DSceneListener {
        override fun onDrawMesh(draw: Rc3DMeshDraw) = Unit

        override fun onFrame(frame: Rc3DFrame) {
          frames += frame
        }
      }
    val times = listOf(0L, 250_000_000L, 500_000_000L)
    val options = Rc3DOptions(drawInPlayer = false, sceneListener = listener)

    // A rigid cube spun by the clock: a frame per redraw, the same key and the same mesh version
    // every time, and only the model matrix moving. An XR host updates one entity's pose.
    renderFrames(Rc3DDocuments.spinningCube().encode(), times, options)
    assertTrue(frames.size >= times.size, "a frame per redraw: ${frames.size}")
    assertTrue(
      frames.all { it.componentId == -1 && it.draws.map(Rc3DMeshDraw::key) == listOf("-1/0") }
    )
    assertEquals(1, frames.map { it.draws.single().meshVersion }.distinct().size)
    assertTrue(frames.map { it.draws.single().model.toList() }.distinct().size > 1)

    // A torus whose tube radius follows the clock is rebuilt each frame: its version rises, the
    // signal to leave a deforming mesh to the player.
    frames.clear()
    renderFrames(Rc3DDocuments.breathingTorus().encode(), times, options)
    val versions = frames.map { it.draws.single().meshVersion }
    assertTrue(versions.zipWithNext().all { (a, b) -> b > a }, "versions $versions")
  }

  @Test
  fun parametricAndExpressionMeshesRender() {
    for (document in listOf(Rc3DDocuments.torus(), Rc3DDocuments.heightField())) {
      val pixels = render(document.encode())
      assertTrue(pixels.count { (it ushr 24) == 255 } > SIZE * SIZE / 10)
      assertEquals(0, pixels[0] ushr 24)
    }
  }

  @Test
  fun aClockDrivenRotationRedrawsEveryFrame() {
    val bytes = Rc3DDocuments.spinningCube().encode()
    val frames = renderFrames(bytes, listOf(0L, 250_000_000L, 500_000_000L))
    assertNotEquals(frames[0].toList(), frames[1].toList())
    assertNotEquals(frames[1].toList(), frames[2].toList())
  }

  @Test
  fun aLaterSoftwareMeshDoesNotRepaintAnEarlierOneOver2D() {
    // Mesh A at the centre, an opaque 2D square over it, then mesh B off to one side. B's
    // composite must not carry A's pixels back over the square.
    val pixels = render(Rc3DDocuments.meshLabelMesh().encode())
    assertEquals(Rc3DDocuments.LABEL, pixels[SIZE / 2 * SIZE + SIZE / 2], "the label stays on top")
    assertTrue(pixels[SIZE / 2 * SIZE + SIZE - 12] ushr 24 == 255, "mesh B was drawn")
  }

  @Test
  fun aSurfaceReadsTheDocumentsArraysAndRebuildsWhenOneChanges() {
    // The height field's height is `heights[0]`: a plane that moves with the host's array.
    val bytes = Rc3DDocuments.arrayHeightField().encode()
    fun heights(h: Float) = mapOf("USER:heights" to RcNamedValue.FloatArrayValue(listOf(h)))
    val low = render(bytes, namedValues = heights(-0.6f))
    val high = render(bytes, namedValues = heights(0.6f))
    assertTrue(high.any { it ushr 24 != 0 }, "the surface was drawn")
    assertNotEquals(low.toList(), high.toList())
  }

  @Test
  fun aVectorExpressionDrivesTheLight() {
    // The light direction is normalize(vec3(namedX, -1, -1)): moving the named value moves the
    // shading, which only happens if the vector program kept its opcodes and wrote its outputs.
    val bytes = Rc3DDocuments.vectorLitCube().encode()
    val left = render(bytes, namedValues = mapOf("USER:light.x" to RcNamedValue.FloatValue(-1f)))
    val right = render(bytes, namedValues = mapOf("USER:light.x" to RcNamedValue.FloatValue(1f)))
    assertNotEquals(left.toList(), right.toList())
  }

  private fun assertTransparentCornersAndOpaqueCentre(pixels: IntArray) {
    for (index in listOf(0, SIZE - 1, SIZE * (SIZE - 1), SIZE * SIZE - 1)) {
      assertEquals(0, pixels[index] ushr 24, "corner $index is transparent")
    }
    assertEquals(255, pixels[SIZE / 2 * SIZE + SIZE / 2] ushr 24, "the centre is opaque")
  }

  internal companion object {
    const val SIZE = 128

    fun render(
      bytes: ByteArray,
      options: Rc3DOptions = Rc3DOptions(),
      namedValues: Map<String, RcNamedValue> = emptyMap(),
      size: Int = SIZE,
    ): IntArray = renderFrames(bytes, listOf(0L), options, namedValues, size).single()

    @OptIn(ExperimentalComposeUiApi::class)
    fun renderFrames(
      bytes: ByteArray,
      nanos: List<Long>,
      options: Rc3DOptions = Rc3DOptions(),
      namedValues: Map<String, RcNamedValue> = emptyMap(),
      size: Int = SIZE,
    ): List<IntArray> {
      val extensions = RcPlayerExtensions(Rc3DExtension(options))
      val values = mutableStateMapOf<String, RcNamedValue>().apply { putAll(namedValues) }
      val scene =
        ImageComposeScene(width = size, height = size, density = Density(1f)) {
          CompositionLocalProvider(LocalRcPlayerExtensions provides extensions) {
            RcComposePlayer(bytes, namedValues = values)
          }
        }
      try {
        return nanos.map { time ->
          // Prime with the frame before, as the player's frame loop re-arms after each frame.
          if (time > 0) scene.render(time - 16_000_000L)
          val bitmap = Bitmap().apply { allocN32Pixels(size, size) }
          check(scene.render(time).readPixels(bitmap))
          IntArray(size * size) { bitmap.getColor(it % size, it / size) }
        }
      } finally {
        scene.close()
      }
    }
  }
}

/** The documents the render tests and the evidence generator play. */
internal object Rc3DDocuments {
  private const val SIZE = Rc3DRenderTest.SIZE

  private fun Rc3DDocumentBuilder.standardCamera(): Rc3DDocumentBuilder =
    perspectiveCamera(
      float(0.9f),
      float(1f),
      float(0.1f),
      float(100f),
      listOf(float(0f), float(0f), float(4f)),
      listOf(float(0f), float(0f), float(0f)),
    )

  private fun Rc3DDocumentBuilder.cubeRotation(): Rc3DDocumentBuilder =
    rotate(float(0.6f), 0f, 1f, 0f).rotate(float(0.35f), 1f, 0f, 0f)

  private fun Rc3DDocumentBuilder.cube(): Int =
    primitive(RcPrimitive3D.CUBE, List(6) { float(if (it < 3) 1f else 0f) })

  /** The reference parity scene, `cube.txt`: one lit cube rotated so three faces show. */
  fun litCube(mode: Int, size: Int = SIZE): Rc3DDocumentBuilder =
    Rc3DDocumentBuilder(size, size).apply {
      clearDepth()
      standardCamera()
      lights(Rc3DLight.directional(-0.3f, -0.5f, -0.8f))
      color(0xFFEE8844.toInt())
      identity()
      cubeRotation()
      draw(cube(), mode)
    }

  const val LABEL: Int = 0xFFFF0000.toInt()

  fun meshLabelMesh(): Rc3DDocumentBuilder =
    Rc3DDocumentBuilder(SIZE, SIZE).apply {
      clearDepth()
      standardCamera()
      lights(Rc3DLight.directional(-0.3f, -0.5f, -0.8f))
      color(0xFF4488EE.toInt())
      identity()
      cubeRotation()
      draw(cube())
      color(LABEL)
      add(
        RcDraw4(
          RcOpcodes.DRAW_RECT,
          float(SIZE / 2f - 8f),
          float(SIZE / 2f - 8f),
          float(SIZE / 2f + 8f),
          float(SIZE / 2f + 8f),
        )
      )
      color(0xFF44CC88.toInt())
      identity()
      translate(float(1.45f), float(0f), float(0f))
      val ball = primitive(RcPrimitive3D.SPHERE, listOf(0.3f, 0f, 0f, 0f).map(::float))
      draw(ball)
    }

  fun arrayHeightField(): Rc3DDocumentBuilder =
    Rc3DDocumentBuilder(SIZE, SIZE).apply {
      val arrayId = 0x200000 or newId()
      add(RcFloatList(arrayId, listOf(float(0f))))
      add(RcNamedVariable(arrayId, RcNamedVariable.FLOAT_ARRAY_TYPE, "USER:heights"))
      val arrayWord = RcFloatWord(0xff800000.toInt() or arrayId)
      clearDepth()
      perspectiveCamera(
        float(0.9f),
        float(1f),
        float(0.1f),
        float(100f),
        listOf(float(0f), float(2.5f), float(3f)),
        listOf(float(0f), float(0f), float(0f)),
      )
      lights(Rc3DLight.directional(-0.3f, -0.8f, -0.4f))
      color(0xFF66AADD.toInt())
      identity()
      val id = newId()
      add(
        RcMeshExpression3D(
          id,
          Rc3D.SURFACE_HEIGHT_FIELD,
          0,
          listOf(-1f, 1f, 8f, -1f, 1f, 8f, -1f, 1f).map(::float),
          listOf(listOf(arrayWord, float(0f), Rc3DDocumentBuilder.operator(32))),
          emptyList(),
          emptyList(),
        )
      )
      draw(id)
    }

  fun breathingTorus(): Rc3DDocumentBuilder =
    Rc3DDocumentBuilder(SIZE, SIZE).apply {
      val radius =
        expression(
          listOf(
            continuousSeconds(),
            Rc3DDocumentBuilder.operator(Rc3DDocumentBuilder.SIN),
            float(0.1f),
            Rc3DDocumentBuilder.operator(Rc3DDocumentBuilder.MUL),
            float(0.35f),
            Rc3DDocumentBuilder.operator(Rc3DDocumentBuilder.ADD),
          )
        )
      clearDepth()
      standardCamera()
      lights(Rc3DLight.directional(-0.3f, -0.5f, -0.8f))
      color(0xFFCC4466.toInt())
      identity()
      val torus =
        primitive(
          RcPrimitive3D.TORUS,
          listOf(float(1.1f), radius, float(0f), float(0f), float(0f)),
          float(16f),
        )
      draw(torus)
    }

  fun hostCameraCube(): Rc3DDocumentBuilder =
    Rc3DDocumentBuilder(SIZE, SIZE).apply {
      clearDepth()
      hostCamera(default = Rc3DHostCamera(eye = floatArrayOf(0f, 0f, 4f)))
      lights(Rc3DLight.directional(-0.3f, -0.5f, -0.8f))
      color(0xFF4488EE.toInt())
      identity()
      draw(cube())
    }

  fun spinningCube(): Rc3DDocumentBuilder =
    Rc3DDocumentBuilder(SIZE, SIZE).apply {
      val angle = times(continuousSeconds(), float(2f))
      clearDepth()
      standardCamera()
      lights(Rc3DLight.directional(-0.3f, -0.5f, -0.8f))
      color(0xFF44CC88.toInt())
      identity()
      rotate(angle, 0f, 1f, 0f)
      rotate(float(0.35f), 1f, 0f, 0f)
      draw(cube())
    }

  fun torus(): Rc3DDocumentBuilder =
    Rc3DDocumentBuilder(SIZE, SIZE).apply {
      clearDepth()
      standardCamera()
      lights(Rc3DLight.directional(-0.3f, -0.5f, -0.8f))
      material(0.6f, 24f)
      color(0xFFCC4466.toInt())
      identity()
      rotate(float(0.9f), 1f, 0f, 0f)
      val torus =
        primitive(RcPrimitive3D.TORUS, listOf(1.1f, 0.4f, 0f, 0f, 0f).map(::float), float(32f))
      draw(torus)
    }

  /** A height field `y = 0.3 * sin(3x) * cos(3z)` over [-1.5, 1.5]². */
  fun heightField(): Rc3DDocumentBuilder =
    Rc3DDocumentBuilder(SIZE, SIZE).apply {
      val op = Rc3DDocumentBuilder::operator
      val var1 = RcFloatWord(0xff800000.toInt() or (0x310000 + 70))
      val var2 = RcFloatWord(0xff800000.toInt() or (0x310000 + 71))
      val height =
        listOf(
          var1,
          float(3f),
          op(Rc3DDocumentBuilder.MUL),
          op(Rc3DDocumentBuilder.SIN),
          var2,
          float(3f),
          op(Rc3DDocumentBuilder.MUL),
          op(Rc3DDocumentBuilder.COS),
          op(Rc3DDocumentBuilder.MUL),
          float(0.3f),
          op(Rc3DDocumentBuilder.MUL),
        )
      clearDepth()
      perspectiveCamera(
        float(0.9f),
        float(1f),
        float(0.1f),
        float(100f),
        listOf(float(0f), float(2.5f), float(3f)),
        listOf(float(0f), float(0f), float(0f)),
      )
      lights(Rc3DLight.directional(-0.3f, -0.8f, -0.4f))
      color(0xFF66AADD.toInt())
      identity()
      val id = newId()
      add(
        RcMeshExpression3D(
          id,
          Rc3D.SURFACE_HEIGHT_FIELD,
          0,
          listOf(-1.5f, 1.5f, 40f, -1.5f, 1.5f, 40f, -1f, 1f).map(::float),
          listOf(height),
          emptyList(),
          emptyList(),
        )
      )
      draw(id)
    }

  fun vectorLitCube(): Rc3DDocumentBuilder =
    Rc3DDocumentBuilder(SIZE, SIZE).apply {
      val x = namedFloat("light.x", -1f)
      val direction = newId()
      newId()
      newId()
      add(
        RcVectorExpression(
          direction,
          3,
          0,
          listOf(
            x,
            float(-1f),
            float(-1f),
            vectorOp(engineOp("VBUILD3")),
            vectorOp(engineOp("VNORM")),
          ),
        )
      )
      fun ref(id: Int) = RcFloatWord(0x7fc00000 or id)
      clearDepth()
      standardCamera()
      lights(
        Rc3DLight(
          Rc3D.LIGHT_DIRECTIONAL,
          -1,
          ref(direction),
          ref(direction + 1),
          ref(direction + 2),
          float(1f),
        )
      )
      color(0xFFDDDDDD.toInt())
      identity()
      cubeRotation()
      draw(cube(), Rc3D.MODE_SOFTWARE_FLAT)
    }

  /** One of each operation, for the wire round trip. */
  fun everyOperation(): Rc3DDocumentBuilder =
    litCube(Rc3D.MODE_SOFTWARE_SMOOTH).apply {
      mesh(
        intArrayOf(0, 1, 2),
        floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
      )
      texture(0)
      add(
        RcVectorExpression(
          newId(),
          2,
          0,
          listOf(float(1f), float(2f), vectorOp(engineOp("VBUILD2"))),
        )
      )
      add(
        RcMeshExpression3D(
          newId(),
          Rc3D.SURFACE_SPHERE,
          0,
          listOf(1f, 8f, 8f).map(::float),
          listOf(listOf(float(0f))),
          emptyList(),
          emptyList(),
        )
      )
      material(0.5f, 16f)
    }

  private fun engineOp(name: String): Int =
    when (name) {
      "VBUILD2" -> 0x310000 + 100
      "VBUILD3" -> 0x310000 + 101
      "VNORM" -> 0x310000 + 107
      else -> error(name)
    }

  private fun vectorOp(id: Int): RcFloatWord = RcFloatWord(0xff800000.toInt() or id)
}

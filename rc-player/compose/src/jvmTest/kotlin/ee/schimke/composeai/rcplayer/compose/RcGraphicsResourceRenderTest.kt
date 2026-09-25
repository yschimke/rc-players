package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmap
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcMatrixConstant
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcShaderData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

class RcGraphicsResourceRenderTest {
  @Test
  fun repeatedFramesReuseTheSameOffscreenAllocation() {
    val images = mutableMapOf(20 to ImageBitmap(4, 4))
    val targets = RcOffscreenTargetPool()

    val first = targets.canvasFor(20, requireNotNull(images[20]), images)
    val second = targets.canvasFor(20, requireNotNull(images[20]), images)

    assertSame(first, second)
    assertEquals(1, targets.allocationCount)
    targets.dispose()
    assertEquals(0, targets.allocationCount)
  }

  @Test
  fun aggregateOffscreenPixelBudgetRejectsAdditionalTargets() {
    val images =
      mutableMapOf(
        20 to ImageBitmap(4, 4),
        21 to ImageBitmap(1, 1),
      )
    val targets = RcOffscreenTargetPool(RcOffscreenTargetLimits(maxTargets = 2, maxPixels = 16))

    targets.canvasFor(20, requireNotNull(images[20]), images)

    assertFailsWith<IllegalArgumentException> {
      targets.canvasFor(21, requireNotNull(images[21]), images)
    }
    assertEquals(1, targets.allocationCount)
  }

  @Test
  fun offscreenTargetCountIsBounded() {
    val images = mutableMapOf(20 to ImageBitmap(1, 1), 21 to ImageBitmap(1, 1))
    val targets = RcOffscreenTargetPool(RcOffscreenTargetLimits(maxTargets = 1, maxPixels = 2))

    targets.canvasFor(20, requireNotNull(images[20]), images)

    assertFailsWith<IllegalArgumentException> {
      targets.canvasFor(21, requireNotNull(images[21]), images)
    }
  }

  @Test
  fun runtimeShaderUsesFloatUniforms() {
    val document =
      document(
        RcTextData(
          10,
          "uniform float4 tint; half4 main(float2 position) { return half4(tint); }",
        ),
        RcShaderData(
          11,
          10,
          floatUniforms =
            linkedMapOf(
              "tint" to
                listOf(
                  RcFloatWord.literal(0f),
                  RcFloatWord.literal(1f),
                  RcFloatWord.literal(0f),
                  RcFloatWord.literal(1f),
                )
            ),
        ),
        RcPaintData(listOf(9, 11)),
        rect(0f, 0f, 8f, 8f),
      )

    val bitmap = render(document, 8, 8)

    assertEquals(0xff00ff00.toInt(), bitmap.getColor(4, 4))
  }

  /**
   * A shader matrix moves a runtime shader too, as it does a gradient or an image shader. Runtime
   * shaders used to draw through their own paint, which on Skia kept the untransformed shader and
   * dropped the matrix: the red half stayed at x < 4 instead of moving 4 px right.
   */
  @Test
  fun aShaderMatrixMovesARuntimeShader() {
    val document =
      document(
        RcTextData(
          10,
          "half4 main(float2 p) { return p.x < 4.0 ? half4(1, 0, 0, 1) : half4(0, 0, 1, 1); }",
        ),
        RcShaderData(11, 10),
        RcMatrixConstant(
          30,
          0,
          listOf(1f, 0f, 4f, 0f, 1f, 0f, 0f, 0f, 1f).map(RcFloatWord::literal),
        ),
        RcPaintData(listOf(9, 11, 22, 0x7fc00000 or 30)),
        rect(0f, 0f, 8f, 8f),
      )

    val bitmap = render(document, 8, 8)

    // Unshifted, x = 6 is in the blue half. Shifted 4 px right, the red half reaches it.
    assertEquals(RED, bitmap.getColor(6, 4), "the shader matrix moved the red half right")
  }

  @Test
  fun redirectedDrawingMutatesBitmapAndIdZeroRestoresMainCanvas() {
    val transparent = ByteArray(4 * 4 * 4)
    val document =
      document(
        RcBitmapData(
          20,
          4,
          4,
          RcBitmapData.TYPE_RAW8888,
          RcBitmapData.ENCODING_INLINE,
          transparent,
        ),
        RcDrawToBitmap(20, 0, BLUE),
        RcPaintData(listOf(4, RED)),
        rect(1f, 1f, 3f, 3f),
        RcDrawToBitmap(0, 0, 0),
        RcDrawBitmap(
          20,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(0f),
          RcFloatWord.literal(4f),
          RcFloatWord.literal(4f),
          0,
        ),
      )

    val bitmap = render(document, 4, 4)

    assertEquals(BLUE, bitmap.getColor(0, 0))
    assertEquals(RED, bitmap.getColor(2, 2))
  }

  @Test
  fun unsupportedShaderDialectFailsExplicitly() {
    val document =
      document(
        RcTextData(10, "this is not AGSL or SkSL"),
        RcShaderData(11, 10),
        RcPaintData(listOf(9, 11)),
        rect(0f, 0f, 8f, 8f),
      )
    val scene =
      ImageComposeScene(width = 8, height = 8, density = Density(1f)) { RcComposePlayer(document) }

    try {
      val failure = runCatching { scene.render(0L) }.exceptionOrNull()
      assertTrue(failure?.message.orEmpty().contains("unsupported by the platform runtime"))
    } finally {
      scene.close()
    }
  }

  @Test
  fun filterBitmapOffSamplesScaledBitmapsWithoutBlending() {
    // Black beside white, stretched to 8 px. Sampled with filtering, the pixel just left of the
    // middle is a blend; with FILTER_BITMAP off it is the black source pixel itself.
    fun drawn(vararg paint: Int): Bitmap =
      render(
        document(
          RcBitmapData(
            20,
            2,
            1,
            RcBitmapData.TYPE_RAW8888,
            RcBitmapData.ENCODING_INLINE,
            byteArrayOf(0, 0, 0, -1, -1, -1, -1, -1),
          ),
          RcPaintData(paint.toList()),
          RcDrawBitmap(
            20,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(8f),
            RcFloatWord.literal(8f),
            0,
          ),
        ),
        8,
        8,
      )

    val black = 0xff000000.toInt()
    assertEquals(black, drawn(FILTER_BITMAP).getColor(3, 4))
    assertTrue(drawn(FILTER_BITMAP or (1 shl 16)).getColor(3, 4) != black)
  }

  private fun document(vararg operations: ee.schimke.composeai.rcplayer.protocol.RcOperation) =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 8, legacyHeight = 8, modern = false),
      operations.toList(),
    )

  private fun rect(left: Float, top: Float, right: Float, bottom: Float) =
    RcDraw4(
      RcOpcodes.DRAW_RECT,
      RcFloatWord.literal(left),
      RcFloatWord.literal(top),
      RcFloatWord.literal(right),
      RcFloatWord.literal(bottom),
    )

  private fun render(document: RcDocument, width: Int, height: Int): Bitmap {
    val scene =
      ImageComposeScene(width = width, height = height, density = Density(1f)) {
        RcComposePlayer(document)
      }
    return try {
      Bitmap().apply {
        allocN32Pixels(width, height)
        assertTrue(scene.render(0L).readPixels(this))
      }
    } finally {
      scene.close()
    }
  }

  private companion object {
    const val RED = 0xffff0000.toInt()
    const val BLUE = 0xff0000ff.toInt()
    /** `PaintBundle.FILTER_BITMAP`; its on/off value rides in the high 16 bits. */
    const val FILTER_BITMAP = 17
  }
}

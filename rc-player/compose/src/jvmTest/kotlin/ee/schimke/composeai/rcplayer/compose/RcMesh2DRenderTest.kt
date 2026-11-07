package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcAddMesh2D
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawMesh2D
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcMatrixFromMesh2D
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

/**
 * The CMP player's mesh operations on a layout canvas: a literal mesh with per-vertex colours, a
 * parametric one in the paint's colour, and `MatrixFromMesh2D` placing a rect on a mesh's surface.
 */
class RcMesh2DRenderTest {
  @Test
  fun aLiteralMeshDrawsItsVertexColours() {
    val pixels =
      render(
        RcAddMesh2D(
          meshId = 70,
          type = RcAddMesh2D.TYPE_VALUES,
          layout = RcAddMesh2D.LAYOUT_GRID,
          uCount = 2,
          vCount = 2,
          flags = 0,
          aux = 0,
          indices = listOf(0, 2, 1, 1, 2, 3),
          vertices = listOf(0f, 0f, 40f, 0f, 0f, 40f, 40f, 40f).map(RcFloatWord::literal),
          // A flat green quad: every vertex the same, so every pixel inside is exactly it.
          colors = List(4) { GREEN },
        ),
        RcDrawMesh2D(70, RcDrawMesh2D.BLEND_COLORS_ONLY, RcDrawMesh2D.NO_IMAGE),
      )
    assertEquals(GREEN, pixels(20, 20))
    assertEquals(0, pixels(50, 20), "nothing outside the mesh")
  }

  @Test
  fun aMeshWithoutColoursTakesThePaintColour() {
    val pixels =
      render(
        RcPaintData(listOf(4, RED)),
        parametricSquare(),
        RcDrawMesh2D(71, RcDrawMesh2D.BLEND_MODULATE, RcDrawMesh2D.NO_IMAGE),
      )
    assertEquals(RED, pixels(10, 10))
    assertEquals(RED, pixels(50, 50))
  }

  @Test
  fun matrixFromMesh2DPlacesDrawingOnTheSurface() {
    val pixels =
      render(
        parametricSquare(),
        RcPaintData(listOf(4, RED)),
        RcNoArg(RcOpcodes.MATRIX_SAVE),
        // The surface's origin at (0.5, 0.5) is (30, 30); the rect is drawn from there.
        RcMatrixFromMesh2D(
          71,
          RcFloatWord.literal(0.5f),
          RcFloatWord.literal(0.5f),
          RcMatrixFromMesh2D.FLAG_ORIGIN,
        ),
        RcDraw4(
          RcOpcodes.DRAW_RECT,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(0f),
          RcFloatWord.literal(10f),
          RcFloatWord.literal(10f),
        ),
        RcNoArg(RcOpcodes.MATRIX_RESTORE),
      )
    assertEquals(RED, pixels(35, 35))
    assertEquals(0, pixels(5, 5))
  }

  @Test
  fun aTexturedMeshSamplesItsImageUnlessColoursOnly() {
    val blue =
      RcBitmapData(
        imageId = 80,
        width = 1,
        height = 1,
        type = RcBitmapData.TYPE_RAW8888,
        encoding = RcBitmapData.ENCODING_INLINE,
        data = byteArrayOf(0, 0, 0xff.toByte(), 0xff.toByte()),
      )
    val textured =
      render(
        RcPaintData(listOf(4, RED)),
        parametricSquare(),
        RcDrawMesh2D(71, RcDrawMesh2D.BLEND_MODULATE, 80),
        prefix = listOf(blue),
      )
    assertEquals(BLUE, textured(30, 30))
    // As `ComposePaintContext.drawMesh`: colours only ignores the image, leaving the paint's
    // colour.
    val coloursOnly =
      render(
        RcPaintData(listOf(4, RED)),
        parametricSquare(),
        RcDrawMesh2D(71, RcDrawMesh2D.BLEND_COLORS_ONLY, 80),
        prefix = listOf(blue),
      )
    assertEquals(RED, coloursOnly(30, 30))
  }

  /** A 3x3 parametric grid spanning (0, 0) to (60, 60): x = u * 60, y = v * 60. */
  private fun parametricSquare(): RcAddMesh2D =
    RcAddMesh2D(
      meshId = 71,
      type = RcAddMesh2D.TYPE_EXPRESSION,
      layout = RcAddMesh2D.LAYOUT_GRID,
      uCount = 3,
      vCount = 3,
      flags = 0,
      aux = 0,
      expressions =
        List(RcAddMesh2D.EXPRESSION_GROUPS) {
          when (it) {
            RcAddMesh2D.EXP_X -> listOf(nan(VAR1), RcFloatWord.literal(60f), nan(MUL))
            RcAddMesh2D.EXP_Y -> listOf(nan(VAR1 + 1), RcFloatWord.literal(60f), nan(MUL))
            else -> emptyList()
          }
        },
    )

  @OptIn(ExperimentalComposeUiApi::class)
  private fun render(
    vararg canvas: RcOperation,
    prefix: List<RcOperation> = emptyList(),
  ): (Int, Int) -> Int {
    val scene =
      ImageComposeScene(width = SIZE, height = SIZE, density = Density(1f)) {
        RcComposePlayer(document(prefix, canvas.toList()))
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(SIZE, SIZE) }
      check(scene.render(0L).readPixels(bitmap))
      return { x, y -> bitmap.getColor(x, y) }
    } finally {
      scene.close()
    }
  }

  private fun document(prefix: List<RcOperation>, canvas: List<RcOperation>): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = SIZE, legacyHeight = SIZE, modern = false),
      prefix +
        listOf(
          RcRootLayout(1),
          RcLayoutContent(2),
          RcCanvasLayout(5, 50),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE.toFloat())),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE.toFloat())),
          RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
        ) +
        canvas +
        listOf(end, end, end, end),
    )
  }

  private fun nan(id: Int) = RcFloatWord(0xff800000.toInt() or id)

  private companion object {
    const val SIZE = 80
    const val RED = 0xffff0000.toInt()
    const val GREEN = 0xff00ff00.toInt()
    const val BLUE = 0xff0000ff.toInt()
    const val VAR1 = 0x310000 + 70
    const val MUL = 0x310000 + 3
  }
}

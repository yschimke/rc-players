package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcIdOperation
import ee.schimke.composeai.rcplayer.protocol.RcMatrixExpression
import ee.schimke.composeai.rcplayer.protocol.RcMatrixVectorMath
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcTheme
import ee.schimke.composeai.rcplayer.protocol.RcTransform2
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RcLowerMatricesTest {
  private fun literal(value: Float) = RcFloatWord.literal(value)

  private fun reference(id: Int) = RcFloatWord(NAN or id)

  private fun matrixOperator(code: Int) = RcFloatWord(NAN or (0x320000 + code))

  private fun pathCommand(command: Int) = RcFloatWord(NAN or command)

  /** A texture paint positioned by matrix [MATRIX], filling. */
  private val texturePaint =
    RcPaintData(listOf(PAINT_STYLE, PAINT_TEXTURE, 54, 0, 0, PAINT_SHADER_MATRIX, NAN or MATRIX))

  /** The next paint, clearing the shader matrix — where the matrix stops positioning anything. */
  private val clearMatrix = RcPaintData(listOf(PAINT_SHADER_MATRIX, 0))

  /** A rectangle from (10, 20) to (30, 40). */
  private val path =
    RcPathData(
      PATH,
      listOf(
        pathCommand(RcPathCommands.MOVE),
        literal(10f),
        literal(20f),
        pathCommand(RcPathCommands.LINE),
        literal(10f),
        literal(20f),
        literal(30f),
        literal(40f),
        pathCommand(RcPathCommands.CLOSE),
      ),
    )

  private fun imageBrush(
    expression: List<RcFloatWord>,
    before: List<RcOperation> = emptyList(),
    between: List<RcOperation> = emptyList(),
    region: List<RcOperation> = emptyList(),
    paint: RcPaintData = texturePaint,
  ): RcDocument =
    document(
      *before.toTypedArray(),
      RcMatrixExpression(MATRIX, 0, expression),
      *between.toTypedArray(),
      paint,
      path,
      *region.toTypedArray(),
      RcIdOperation(RcOpcodes.DRAW_PATH, PATH),
      clearMatrix,
    )

  /** `RemoteImageBrush`'s matrix: translate, then scale x and y each in a pushed level. */
  private fun remoteImageBrushMatrix(
    translateX: RcFloatWord,
    translateY: RcFloatWord,
    scale: RcFloatWord,
  ) =
    listOf(
      translateX,
      translateY,
      matrixOperator(8),
      matrixOperator(1),
      scale,
      matrixOperator(10),
      matrixOperator(15),
      matrixOperator(1),
      scale,
      matrixOperator(11),
      matrixOperator(15),
    )

  @Test
  fun aLiteralTranslateAndScaleBecomesCanvasTransformsAroundTheInversePath() {
    val source = imageBrush(remoteImageBrushMatrix(literal(4f), literal(6f), literal(2f)))

    val result = assertRoundTrips(source.lowerMatrixExpressions())

    val inverse = result.operations.filterIsInstance<RcPathData>().single { it.id != PATH }
    assertEquals(
      listOf(
        RcPaintData(listOf(PAINT_STYLE, PAINT_TEXTURE, 54, 0, 0)),
        path,
        // (x - 4) / 2 and (y - 6) / 2: the inverse of the shader matrix.
        RcPathData(
          inverse.id,
          listOf(
            pathCommand(RcPathCommands.MOVE),
            literal(3f),
            literal(7f),
            pathCommand(RcPathCommands.LINE),
            literal(3f),
            literal(7f),
            literal(13f),
            literal(17f),
            pathCommand(RcPathCommands.CLOSE),
          ),
        ),
        RcNoArg(RcOpcodes.MATRIX_SAVE),
        RcTransform2(RcOpcodes.MATRIX_TRANSLATE, literal(4f), literal(6f)),
        RcDraw4(RcOpcodes.MATRIX_SCALE, literal(2f), literal(2f), literal(0f), literal(0f)),
        RcIdOperation(RcOpcodes.DRAW_PATH, inverse.id),
        RcNoArg(RcOpcodes.MATRIX_RESTORE),
        clearMatrix,
      ),
      result.operations,
    )
    assertTrue(result.validateFor(RcTargetProfiles.WIDGETS_V6).none { it.opcode != null })
  }

  @Test
  fun variableOperandsAreReadWhereTheMatrixReadThem() {
    val source =
      imageBrush(
        remoteImageBrushMatrix(reference(TRANSLATE_X), literal(6f), reference(SCALE)),
        before =
          listOf(RcFloatConstant(TRANSLATE_X, literal(4f)), RcFloatConstant(SCALE, literal(2f))),
      )

    val result = assertRoundTrips(source.lowerMatrixExpressions())

    assertTrue(result.operations.none { it is RcMatrixExpression })
    val state = RcPlayerState(result)
    state.applyLayoutContentStateOperations(RcDocumentLinker.link(result).operations)
    val translate = result.operations.filterIsInstance<RcTransform2>().single()
    val scale = result.operations.filterIsInstance<RcDraw4>().single()
    assertEquals(listOf(4f, 6f), listOf(translate.first, translate.second).map(state::resolve))
    assertEquals(listOf(2f, 2f), listOf(scale.first, scale.second).map(state::resolve))
    val inverse = result.operations.filterIsInstance<RcPathData>().single { it.id != PATH }
    assertEquals(
      listOf(3f, 7f, 3f, 7f, 13f, 17f),
      inverse.words.filterNot { it.referencedId in PATH_COMMANDS }.map(state::resolve),
    )
  }

  @Test
  fun aMatrixThatDoesMoreThanTranslateAndScaleStays() {
    // Rotate about z.
    val source = imageBrush(listOf(literal(30f), matrixOperator(4)))
    assertSame(source, source.lowerMatrixExpressions())
  }

  @Test
  fun aMatrixWithAnyOtherUseStays() {
    val declines: List<RcDocument> =
      listOf(
        // Read by vector math as well as by the paint.
        imageBrush(
          listOf(literal(4f), literal(6f), matrixOperator(8)),
          between = listOf(RcMatrixVectorMath(0, listOf(70), MATRIX, listOf(literal(1f)))),
        ),
        // A stroke scales with the canvas.
        imageBrush(
          listOf(literal(4f), literal(6f), matrixOperator(8)),
          paint =
            RcPaintData(listOf(PAINT_STYLE or (1 shl 16), PAINT_SHADER_MATRIX, NAN or MATRIX)),
        ),
        // Something other than a path drawn under the matrix.
        imageBrush(
          listOf(literal(4f), literal(6f), matrixOperator(8)),
          region =
            listOf(
              RcDraw4(RcOpcodes.DRAW_RECT, literal(0f), literal(0f), literal(1f), literal(1f))
            ),
        ),
        // An operand redefined before the draw would read it.
        imageBrush(
          listOf(reference(TRANSLATE_X), literal(6f), matrixOperator(8)),
          before = listOf(RcFloatConstant(TRANSLATE_X, literal(4f))),
          between = listOf(RcFloatConstant(TRANSLATE_X, literal(5f))),
        ),
        // A theme marker between the matrix and its draw.
        imageBrush(
          listOf(literal(4f), literal(6f), matrixOperator(8)),
          between = listOf(RcTheme(RcTheme.DARK)),
        ),
        // A path effect anywhere, which a canvas scale would scale.
        imageBrush(
          listOf(literal(4f), literal(6f), matrixOperator(8)),
          before = listOf(RcPaintData(listOf(PAINT_PATH_EFFECT or (1 shl 16), 0))),
        ),
      )
    declines.forEachIndexed { index, source ->
      assertSame(source, source.lowerMatrixExpressions(), "case $index")
    }
  }

  @Test
  fun adaptingToLevelSixLowersTheMatrix() {
    val source = imageBrush(remoteImageBrushMatrix(literal(4f), literal(6f), literal(2f)))
    assertTrue(
      source.validateFor(RcTargetProfiles.WIDGETS_V6).any {
        it.opcode == RcOpcodes.MATRIX_EXPRESSION
      }
    )

    val adapted = source.adaptTo(RcTargetProfiles.WIDGETS_V6)

    assertTrue(adapted.violations.none { it.opcode == RcOpcodes.MATRIX_EXPRESSION })
    // A target with matrices keeps them.
    assertSame(source.operations, source.adaptTo(RcTargetProfiles.ANDROIDX).document.operations)
  }

  private companion object {
    const val NAN = 0xff800000.toInt()
    const val MATRIX = 60
    const val PATH = 61
    const val TRANSLATE_X = 70
    const val SCALE = 71
    val PATH_COMMANDS = setOf(RcPathCommands.MOVE, RcPathCommands.LINE, RcPathCommands.CLOSE)
  }
}

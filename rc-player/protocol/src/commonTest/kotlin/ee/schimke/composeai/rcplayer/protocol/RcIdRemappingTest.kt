package ee.schimke.composeai.rcplayer.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The ids AndroidX's `LoomWireBuffer` remaps by hand — packed into paint words, colour-expression
 * modes and integer-expression masks — move with a remapping reader, and nothing else does.
 */
class RcIdRemappingTest {
  private val mapping = mapOf(50 to 150, 51 to 151, 52 to 152, 60 to 60)

  private fun remap(operation: RcOperation): RcOperation {
    val output = RcWireWriter()
    RcDocumentCodec.encodeOperation(output, operation)
    return RcDocumentCodec.decodeOperations(
        output.toByteArray(),
        idRemapper = RcIdRemapper.expanding(mapping, mapping.values.toSet()),
      )
      .single()
  }

  private fun nan(id: Int) = 0x7fc00000 or id

  @Test
  fun paintIdsAndFloatIdsMoveButLiteralsStay() {
    val words =
      listOf(
        19, // COLOR_ID
        50,
        5, // STROKE_WIDTH
        nan(51),
        4, // COLOR
        50,
        0x10000 or 23, // FONT_AXIS x1
        52,
        nan(50),
        0x10000 or 11, // GRADIENT, radial
        (0b10 shl 16) or 2, // two colours, the second a register
        50,
        51,
        1, // one stop
        nan(52),
        nan(50),
        0,
        nan(51), // radius
        0, // tile mode
      )
    val expected =
      listOf(19, 150, 5, nan(151), 4, 50, 0x10000 or 23, 152, nan(150)) +
        listOf(0x10000 or 11, (0b10 shl 16) or 2, 50, 151, 1, nan(152), nan(150), 0, nan(151), 0)
    assertEquals(RcPaintData(expected), remap(RcPaintData(words)))
  }

  @Test
  fun aShortPaintBundleKeepsWhatItHas() {
    assertEquals(RcPaintData(listOf(19)), remap(RcPaintData(listOf(19))))
  }

  @Test
  fun colorExpressionsMoveTheIdsTheirModeNames() {
    // ID_COLOR_INTERPOLATE: the first colour is an id, the second a literal ARGB.
    assertEquals(
      RcColorExpression(60, 1, 150, 51, nan(152)),
      remap(RcColorExpression(60, 1, 50, 51, nan(52))),
    )
    // IDARGB: the alpha id lives in the mode word's high half.
    assertEquals(
      RcColorExpression(60, (151 shl 16) or 6, nan(150), 0, nan(152)),
      remap(RcColorExpression(60, (51 shl 16) or 6, nan(50), 0, nan(52))),
    )
  }

  @Test
  fun integerExpressionsMoveMaskedIdsButNotOperators() {
    val add = 0x10001
    assertEquals(
      RcIntegerExpression(60, 0b101, listOf(150, 51, add)),
      remap(RcIntegerExpression(60, 0b101, listOf(50, 51, add))),
    )
  }

  @Test
  fun graphicsLayerShadowColorsAreIds() {
    val layer = { spot: Int, alpha: Int ->
      RcGraphicsLayerModifier(
        listOf(
          RcGraphicsLayerAttribute.IntValue(RcGraphicsLayerModifier.SPOT_SHADOW_COLOR, spot),
          RcGraphicsLayerAttribute.IntValue(RcGraphicsLayerModifier.COMPOSITING_STRATEGY, alpha),
        )
      )
    }
    assertEquals(layer(150, 50), remap(layer(50, 50)))
  }
}

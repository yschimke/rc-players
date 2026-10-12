package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.edit.RcProfileViolation.Kind
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcFloatExpressionEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcHardcodeDensityTest {
  private fun ref(id: Int) = RcFloatWord(0x7fc00000 or id)

  /** The legacy level 6 header every launcher catalog widget carries. */
  private fun widget(vararg operations: RcOperation) =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 200, legacyHeight = 100, legacyCapabilities = 0),
      operations.toList(),
    )

  private val multiply =
    RcFloatExpressionEvaluator.operatorWord(RcFloatExpressionEvaluator.OFFSET + 3)

  /** A `RemoteDensity.Host` widget's 12dp padding, as the launcher catalog writes it. */
  private val dpPadding =
    widget(
      RcFloatExpression(
        42,
        listOf(ref(RcSystemVariables.DENSITY), RcFloatWord.literal(12f), multiply),
        null,
      ),
      RcPaddingModifier(ref(42), ref(42), ref(42), ref(42)),
    )

  @Test
  fun aDensityExpressionDoesNotPlayOnBaklavaUntilTheDensityIsBaked() {
    val kinds = dpPadding.adaptTo(RcTargetProfiles.WIDGETS_V6).violations.map { it.kind }
    assertEquals(List(4) { Kind.DYNAMIC_VALUE }, kinds)

    val baked = dpPadding.hardcodeDensity(2.75f)
    assertEquals(
      RcFloatExpression(
        42,
        listOf(RcFloatWord.literal(2.75f), RcFloatWord.literal(12f), multiply),
        null,
      ),
      baked.operations[0],
    )
    val adaptation = baked.adaptTo(RcTargetProfiles.WIDGETS_V6)
    assertTrue(adaptation.isPlayable, adaptation.violations.toString())
    val literal = RcFloatWord.literal(33f)
    assertEquals(
      RcPaddingModifier(literal, literal, literal, literal),
      adaptation.document.operations[1],
    )
  }

  @Test
  fun theHostTextSizeFollowsTheFontScale() {
    val text =
      document(RcFloatExpression(43, listOf(ref(RcSystemVariables.FONT_SIZE)), null))
        .hardcodeDensity(2f, fontScale = 1.5f)
    assertEquals(
      listOf(RcFloatWord.literal(42f)),
      (text.operations[0] as RcFloatExpression).expression,
    )
  }
}

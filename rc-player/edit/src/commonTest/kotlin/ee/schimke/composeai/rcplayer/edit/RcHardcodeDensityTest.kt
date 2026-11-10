package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.edit.RcProfileViolation.Kind
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcFloatExpressionEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    val kinds = dpPadding.validateFor(RcTargetProfiles.WIDGETS_V6).map { it.kind }
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
  fun adaptingForTheClientsDisplayBakesTheDensityOnlyWhereTheTargetNeedsIt() {
    val display = RcDisplay(density = 2.75f)
    val served = dpPadding.adaptTo(RcTargetProfiles.WIDGETS_V6, emptySet(), display)
    assertTrue(served.isPlayable, served.violations.toString())

    assertTrue(dpPadding.needsDisplay(RcTargetProfiles.WIDGETS_V6))
    assertFalse(dpPadding.needsDisplay(RcTargetProfiles.WIDGETS_V7))

    // A level 7 player reads the padding as a variable: nothing needs baking.
    val modern = dpPadding.adaptTo(RcTargetProfiles.WIDGETS_V7, emptySet(), display).document
    assertEquals(dpPadding.operations, modern.operations)
  }

  @Test
  fun aModernPlayerWithoutVariableFloatFieldsGetsTheDensityBaked() {
    val target = RcTargetProfiles.ANDROIDX.without(RcPlayerFeature.VARIABLE_FLOAT_FIELDS)
    assertTrue(dpPadding.needsDisplay(target))
    assertFalse(dpPadding.needsDisplay(RcTargetProfiles.ANDROIDX))

    val served = dpPadding.adaptTo(target, emptySet(), RcDisplay(density = 2.75f))
    assertTrue(served.isPlayable, served.violations.toString())
    val literal = RcFloatWord.literal(33f)
    assertEquals(
      RcPaddingModifier(literal, literal, literal, literal),
      served.document.operations.filterIsInstance<RcPaddingModifier>().single(),
    )
  }

  /** A `RemoteDensity.Host` column's `Arrangement.spacedBy(10.dp)`, at document level 7. */
  private val dpSpacing =
    RcDocument(
      RcHeader(RcVersion(1, 1, 0), legacyWidth = 200, legacyHeight = 100, legacyCapabilities = 0),
      listOf(
        RcFloatExpression(
          42,
          listOf(ref(RcSystemVariables.DENSITY), RcFloatWord.literal(10f), multiply),
          null,
        ),
        RcColumnLayout(-3, -1, 1, 4, spacedBy = ref(42)),
        RcNoArg(RcOpcodes.CONTAINER_END),
      ),
    )

  @Test
  fun aLayoutsSpacingIsALiteralOnTheLauncherAtEveryLevel() {
    // The platform widget player draws a document blank when a spacing is a variable, at level 7
    // as at level 6; the AndroidX player reads it.
    for (target in listOf(RcTargetProfiles.WIDGETS_V6, RcTargetProfiles.WIDGETS_V7)) {
      val violations = dpSpacing.validateFor(target).filter { it.kind == Kind.DYNAMIC_VALUE }
      assertEquals(listOf(1), violations.map { it.operationIndex }, target.name)
      assertTrue(dpSpacing.needsDisplay(target), target.name)
    }
    assertFalse(dpSpacing.needsDisplay(RcTargetProfiles.ANDROIDX))

    val served = dpSpacing.adaptTo(RcTargetProfiles.WIDGETS_V7, emptySet(), RcDisplay(2.75f))
    assertTrue(
      served.violations.none { it.kind == Kind.DYNAMIC_VALUE },
      served.violations.toString(),
    )
    assertEquals(
      RcColumnLayout(-3, -1, 1, 4, spacedBy = RcFloatWord.literal(27.5f)),
      served.document.operations.filterIsInstance<RcColumnLayout>().single(),
    )
    // Only the spacing: a level 7 player reads the document's other density expressions itself.
    assertEquals(dpSpacing.operations[0], served.document.operations[0])

    val untouched = dpSpacing.adaptTo(RcTargetProfiles.ANDROIDX, emptySet(), RcDisplay(2.75f))
    assertEquals(dpSpacing.operations, untouched.document.operations)
  }

  @Test
  fun aSpacingNoDisplayFixesStaysAVariableAndIsReported() {
    // A spacing that follows a named host value: no density makes it a number.
    val hostSpacing =
      dpSpacing.copy(
        operations =
          listOf(
            RcFloatExpression(42, listOf(ref(99), RcFloatWord.literal(10f), multiply), null),
            RcColumnLayout(-3, -1, 1, 4, spacedBy = ref(42)),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )
    assertFalse(hostSpacing.needsDisplay(RcTargetProfiles.WIDGETS_V7))
    val served = hostSpacing.adaptTo(RcTargetProfiles.WIDGETS_V7, emptySet(), RcDisplay(2f))
    assertEquals(hostSpacing.operations[1], served.document.operations[1])
    assertTrue(served.violations.any { it.kind == Kind.DYNAMIC_VALUE })
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

package ee.schimke.composeai.rcplayer.edit

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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RcAnimationTimeAsWallClockTest {
  private fun ref(id: Int) = RcFloatWord(0x7fc00000 or id)

  private fun widget(vararg operations: RcOperation) =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 200, legacyHeight = 100, legacyCapabilities = 0),
      operations.toList(),
    )

  private val divide =
    RcFloatExpressionEvaluator.operatorWord(RcFloatExpressionEvaluator.OFFSET + 4)

  /** A wave's phase, `ANIMATION_TIME / 2.4`, as `RemoteTimeVariables.animationTime` writes it. */
  private val wave =
    widget(
      RcFloatExpression(
        42,
        listOf(ref(RcSystemVariables.ANIMATION_TIME), RcFloatWord.literal(2.4f), divide),
        null,
      ),
      RcPaddingModifier(ref(42), ref(42), ref(42), ref(42)),
    )

  @Test
  fun theAnimationClockBecomesTheWallClock() {
    assertTrue(wave.readsAnimationTime())
    val converted = wave.animationTimeAsWallClock()
    assertFalse(converted.readsAnimationTime())
    assertEquals(
      RcFloatExpression(
        42,
        listOf(ref(RcSystemVariables.CONTINUOUS_SEC), RcFloatWord.literal(2.4f), divide),
        null,
      ),
      converted.operations[0],
    )
    assertEquals(wave.operations.drop(1), converted.operations.drop(1))
  }

  @Test
  fun withALoadTimeTheClockCountsSecondsSinceThenAcrossTheHour() {
    val converted = wave.animationTimeAsWallClock(loadedAt = 3590f)
    assertEquals(wave.operations.size + 1, converted.operations.size)
    val clock = converted.operations[0] as RcFloatExpression
    assertTrue(clock.id !in setOf(42, RcSystemVariables.CONTINUOUS_SEC))
    assertEquals(
      ref(clock.id),
      (converted.operations[1] as RcFloatExpression).expression.first(),
      "the wave reads the new clock",
    )
    fun elapsedAt(wallClock: Float) =
      RcFloatExpressionEvaluator().evaluate(clock.expression) { word ->
        if (word.referencedId == RcSystemVariables.CONTINUOUS_SEC) wallClock else word.value
      }
    assertEquals(0f, elapsedAt(3590f))
    assertEquals(5f, elapsedAt(3595f))
    assertEquals(15f, elapsedAt(5f), "ten seconds past the hour")
  }

  @Test
  fun aDocumentWithoutTheAnimationClockIsUnchanged() {
    val still = widget(RcFloatExpression(42, listOf(ref(RcSystemVariables.CONTINUOUS_SEC)), null))
    assertFalse(still.readsAnimationTime())
    assertSame(still, still.animationTimeAsWallClock(loadedAt = 10f))
  }

  @Test
  fun theLoadTimeIsASecondOfTheHour() {
    assertFailsWith<IllegalArgumentException> { wave.animationTimeAsWallClock(loadedAt = 3600f) }
    assertFailsWith<IllegalArgumentException> { wave.animationTimeAsWallClock(loadedAt = -1f) }
  }

  @Test
  fun adaptingToALauncherConvertsOnlyWhenAsked() {
    assertTrue(wave.adaptTo(RcTargetProfiles.WIDGETS_V7).document.readsAnimationTime())
    val asked = setOf(RcApproximation.WALL_CLOCK_ANIMATION)
    assertFalse(wave.adaptTo(RcTargetProfiles.WIDGETS_V6, asked).document.readsAnimationTime())
    assertFalse(wave.adaptTo(RcTargetProfiles.WIDGETS_V7, asked).document.readsAnimationTime())
    assertTrue(
      wave.adaptTo(RcTargetProfiles.ANDROIDX, asked).document.readsAnimationTime(),
      "the AndroidX player redraws for the animation clock",
    )
  }
}

package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RcAnimatableFloatTest {
  @Test
  fun theOpeningPoseLandsWithoutAnimating() {
    val value = RcAnimatableFloat()

    assertEquals(1f, value.evaluate(target = 1f, animatable = true, nowSeconds = 0f))
    assertFalse(value.isAnimating, "a document's opening pose is not a change to ease into")

    // Re-evaluating an unchanged target is every frame of a still document, and starts nothing.
    assertEquals(1f, value.evaluate(target = 1f, animatable = true, nowSeconds = 10f))
    assertFalse(value.isAnimating)
  }

  @Test
  fun aDiscreteChangeEasesOverTheAndroidXDuration() {
    val value = RcAnimatableFloat()
    value.evaluate(target = 1f, animatable = true, nowSeconds = 0f)

    // The clock a host write is noticed on is whatever the player last rendered, which for an idle
    // document is arbitrarily stale — so the retarget holds the old value and stakes nothing.
    val noticed = value.evaluate(target = 2f, animatable = true, nowSeconds = 1f)
    assertEquals(1f, noticed, "the tween starts from the value it held")
    assertTrue(value.isAnimating, "and it is running, which is what asks for the frame below")

    // The first frame with a clock that has moved is the tween's zero. Same value, real start.
    assertEquals(1f, value.evaluate(target = 2f, animatable = true, nowSeconds = 1.05f))

    val midway = value.evaluate(target = 2f, animatable = true, nowSeconds = 1.2f)
    assertTrue(midway > 1f && midway < 2f, "expected an eased value, got $midway")
    assertTrue(value.isAnimating)

    assertEquals(2f, value.evaluate(target = 2f, animatable = true, nowSeconds = 1.35f))
    assertFalse(value.isAnimating, "a finished tween must stop asking for frames")
  }

  @Test
  fun aContinuouslyDrivenSourceIsFollowedRatherThanChased() {
    val value = RcAnimatableFloat()
    value.evaluate(target = 0f, animatable = false, nowSeconds = 0f)

    // What a clock-driven layer looks like: a new target every frame, resolved straight through
    // because the caller classified the source rather than waiting to infer it from cadence.
    var last = 0f
    var now = 1f
    for (step in 1..10) {
      now += 1f / 60f
      last = value.evaluate(target = step.toFloat(), animatable = false, nowSeconds = now)
    }

    assertEquals(10f, last, "a clock-driven layer must track its source, not lag a tween behind it")
    assertFalse(value.isAnimating)
  }

  @Test
  fun aClockDrivenLayerIsNotEasedAtAll() {
    // `[51] = CONTINUOUS_SEC` — an expression over a moving system variable, the shape
    // `remote-m3`'s
    // progress indicators build their sweep from.
    val movingSecond = RcFloatWord(0x7fc00000 or RcSystemVariables.CONTINUOUS_SEC)
    val state =
      RcPlayerState(
        RcDocument(
          RcHeader(RcVersion(1, 0, 0)),
          listOf(RcFloatExpression(51, listOf(movingSecond), null)),
        )
      )

    assertTrue(state.isContinuouslyDriven(51), "an expression over the clock moves every frame")
    assertFalse(state.isContinuouslyDriven(99), "a variable nothing drives is a discrete source")
  }

  @Test
  fun aLiteralAttributeNeverAnimates() {
    val value = RcAnimatableFloat()

    assertEquals(1f, value.evaluate(target = 1f, animatable = false, nowSeconds = 0f))
    assertEquals(9f, value.evaluate(target = 9f, animatable = false, nowSeconds = 5f))
    assertFalse(value.isAnimating)
  }

  @Test
  fun absentAttributesTakeTheAndroidXDefaultsAndPivotAtTheTopLeft() {
    val state = RcPlayerState(RcDocument(RcHeader(RcVersion(1, 0, 0)), emptyList()))
    val values = RcGraphicsLayerAnimator().evaluate(RcGraphicsLayerModifier(emptyList()), state)

    assertEquals(RcGraphicsLayerValues(), values)
    // Spelled out because this used to be the centre: the declared `remote-core` default is 0f,
    // and AndroidX's embedded player reads it. See `RcGraphicsLayerValues`.
    assertEquals(0f, values.transformOriginX)
    assertEquals(0f, values.transformOriginY)
    assertFalse(values.isAnimating)
  }

  @Test
  fun anAuthoredCentrePivotIsHonoured() {
    val state = RcPlayerState(RcDocument(RcHeader(RcVersion(1, 0, 0)), emptyList()))
    val modifier =
      RcGraphicsLayerModifier(
        listOf(
          RcGraphicsLayerAttribute.FloatValue(
            RcGraphicsLayerModifier.TRANSFORM_ORIGIN_X,
            RcFloatWord.literal(0.5f),
          )
        )
      )

    val values = RcGraphicsLayerAnimator().evaluate(modifier, state)

    assertEquals(0.5f, values.transformOriginX)
    assertEquals(0f, values.transformOriginY)
  }

  @Test
  fun aVariableBackedLayerEasesAndReportsThatItOwesAFrame() {
    val reference = RcFloatWord(0x7fc00000 or 51)
    val state =
      RcPlayerState(
        RcDocument(
          RcHeader(RcVersion(1, 0, 0)),
          listOf(RcFloatConstant(51, RcFloatWord.literal(0f))),
        )
      )
    val modifier =
      RcGraphicsLayerModifier(
        listOf(RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.ALPHA, reference))
      )
    val animator = RcGraphicsLayerAnimator()

    assertEquals(0f, animator.evaluate(modifier, state, nowSeconds = 0f).alpha)

    state.setFloat(51, 1f)
    val noticed = animator.evaluate(modifier, state, nowSeconds = 1f)
    assertEquals(0f, noticed.alpha, "the tween starts from the value the layer held")
    assertTrue(noticed.isAnimating, "a running tween is what asks the player for the next frame")

    // The frame that answers that request anchors the tween; the ones after it ease.
    animator.evaluate(modifier, state, nowSeconds = 1.05f)
    val midway = animator.evaluate(modifier, state, nowSeconds = 1.2f)
    assertTrue(
      midway.alpha > 0f && midway.alpha < 1f,
      "expected an eased alpha, got ${midway.alpha}",
    )

    val settled = animator.evaluate(modifier, state, nowSeconds = 1.35f)
    assertEquals(1f, settled.alpha)
    assertFalse(settled.isAnimating)
  }
}

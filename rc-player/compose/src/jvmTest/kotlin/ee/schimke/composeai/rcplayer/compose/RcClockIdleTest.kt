package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcVisibilityModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A card asks for frames only while something it shows reads the clock: a "live" badge that pulses
 * over `ANIMATION_TIME` animates while it is shown, and the card goes idle once it is gone.
 */
class RcClockIdleTest {

  @Test
  fun aPulseThatIsNotShownAsksForNoFrames() {
    val frames = framesThatInvalidate(live = 0, then = null)
    assertEquals(0, frames.first, "a hidden pulse kept the card drawing")
  }

  @Test
  fun aPulseThatEndsStopsAskingForFrames() {
    val (whileLive, afterwards) = framesThatInvalidate(live = 1, then = 0)
    assertEquals(FRAMES, whileLive, "a shown pulse did not animate on every frame")
    assertTrue(afterwards < FRAMES / 2, "the card kept drawing after its pulse ended: $afterwards")
  }

  @Test
  fun aPulseThatStartsAgainAnimatesAgain() {
    val (_, afterwards) = framesThatInvalidate(live = 0, then = 1)
    assertEquals(FRAMES, afterwards, "a pulse shown again did not animate")
  }

  /**
   * Frames, of [FRAMES] each, that left an invalidation: with `USER:live` = [live], then [then].
   */
  private fun framesThatInvalidate(live: Int, then: Int?): Pair<Int, Int> {
    val values = mutableStateMapOf<String, RcNamedValue>("USER:live" to RcNamedValue.Integer(live))
    val scene =
      ImageComposeScene(width = 40, height = 40, density = Density(1f)) {
        RcComposePlayer(RcPreparedDocument(document), Modifier.size(40.dp), namedValues = values)
      }
    try {
      var time = 0L
      fun run(): Int {
        var invalidated = 0
        repeat(FRAMES) {
          time += FRAME_NANOS
          scene.render(time).close()
          if (scene.hasInvalidations()) invalidated++
        }
        return invalidated
      }
      val first = run()
      if (then == null) return first to 0
      values["USER:live"] = RcNamedValue.Integer(then)
      Snapshot.sendApplyNotifications()
      return first to run()
    } finally {
      scene.close()
    }
  }

  private val document =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 40, modern = false),
      listOf(
        RcIntegerConstant(LIVE, 0),
        RcNamedVariable(LIVE, RcNamedVariable.INT_TYPE, "USER:live"),
        // The badge's alpha: a pulse over the animation clock.
        RcFloatExpression(
          PULSE,
          listOf(RcFloatWord(0x7fc00000 or RcSystemVariables.ANIMATION_TIME)),
          animation = null,
        ),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcColumnLayout(3, -1, 1, 1, RcFloatWord.literal(0f)),
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcLayoutContent(4),
        RcBoxLayout(5, -1, 1, 1),
        RcVisibilityModifier(LIVE),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        RcGraphicsLayerModifier(
          listOf(
            RcGraphicsLayerAttribute.FloatValue(
              RcGraphicsLayerModifier.ALPHA,
              RcFloatWord(0x7fc00000 or PULSE),
            ),
          ),
        ),
        RcBackgroundModifier(
          flags = 0,
          colorId = 0,
          reserved1 = 0,
          reserved2 = 0,
          red = RcFloatWord.literal(0f),
          green = RcFloatWord.literal(1f),
          blue = RcFloatWord.literal(0f),
          alpha = RcFloatWord.literal(1f),
          shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
        ),
        RcLayoutContent(6),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
      ),
    )

  private companion object {
    const val LIVE = 42
    const val PULSE = 43
    const val FRAMES = 60
    const val FRAME_NANOS = 16_000_000L
  }
}

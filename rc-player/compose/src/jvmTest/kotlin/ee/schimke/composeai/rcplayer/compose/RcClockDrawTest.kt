package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
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
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.trace.RcTrace
import ee.schimke.composeai.rcplayer.trace.RcTraceRecorder
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * A flattened card draws again with the clock only while what it draws reads the clock: a card
 * whose "live" badge pulses on a layer of its own keeps the rest of the card still, and a
 * background whose colour follows the clock still animates.
 */
@OptIn(ExperimentalRcPlayerApi::class)
class RcClockDrawTest {

  @Test
  fun aPulseOnItsOwnLayerLeavesTheRestOfTheCardStill() {
    val (draws, _) = run(pulseOnLayer = true)
    assertTrue(draws <= 3, "the flattened card was drawn $draws times over $FRAMES frames")
  }

  @Test
  fun aBackgroundThatFollowsTheClockStillAnimates() {
    val (draws, frames) = run(pulseOnLayer = false)
    assertTrue(draws >= FRAMES - 2, "a clock-driven background drew only $draws times")
    assertTrue(frames.toSet().size > 1, "a clock-driven background did not change")
  }

  /** Draws of the flattened container over [FRAMES] frames, and a pixel of the plain box. */
  private fun run(pulseOnLayer: Boolean): Pair<Int, List<Int>> {
    val recorder = RcTraceRecorder()
    val scene =
      ImageComposeScene(width = 40, height = 60, density = Density(1f)) {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides true,
        ) {
          RcComposePlayer(RcPreparedDocument(document(pulseOnLayer)), Modifier.size(40.dp, 60.dp))
        }
      }
    try {
      var time = 0L
      repeat(5) { scene.render(time).close().also { time += FRAME_NANOS } }
      val pixels = mutableListOf<Int>()
      RcTrace.recorder = recorder
      repeat(FRAMES) {
        time += FRAME_NANOS
        val image = scene.render(time)
        val bitmap = Bitmap().apply { allocN32Pixels(40, 60) }
        image.readPixels(bitmap)
        pixels += bitmap.getColor(10, 30)
        image.close()
      }
      RcTrace.recorder = null
      val draws = recorder.summary().singleOrNull { it.name == "rc:drawFlat" }?.count ?: 0
      return draws to pixels
    } finally {
      RcTrace.recorder = null
      scene.close()
    }
  }

  /**
   * A column: a 20 x 20 badge, composed (it has a graphics layer), then a 20 x 20 plain box. The
   * pulse over the animation clock is the badge's alpha, or else the plain box's red.
   */
  private fun document(pulseOnLayer: Boolean): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val pulse = RcFloatWord(0x7fc00000 or PULSE)
    fun background(red: RcFloatWord) =
      RcBackgroundModifier(
        flags = 0,
        colorId = 0,
        reserved1 = 0,
        reserved2 = 0,
        red = red,
        green = RcFloatWord.literal(0f),
        blue = RcFloatWord.literal(1f),
        alpha = RcFloatWord.literal(1f),
        shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
      )
    fun square(id: Int, extra: List<RcOperation>) =
      listOf(
        RcBoxLayout(id, -1, 1, 1),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
      ) + extra + listOf(RcLayoutContent(id + 1), end, end)
    val badge =
      square(
        10,
        listOf(
          RcGraphicsLayerModifier(
            listOf(
              RcGraphicsLayerAttribute.FloatValue(
                RcGraphicsLayerModifier.ALPHA,
                if (pulseOnLayer) pulse else RcFloatWord.literal(1f),
              ),
            ),
          ),
          background(RcFloatWord.literal(0f)),
        ),
      )
    val plain = square(20, listOf(background(if (pulseOnLayer) RcFloatWord.literal(1f) else pulse)))
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 60, modern = false),
      listOf(
        // The animation clock, in seconds: under 1 over the frames drawn, and moving every one.
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
      ) + badge + plain + listOf(end, end, end, end),
    )
  }

  private companion object {
    const val PULSE = 43
    const val FRAMES = 30
    const val FRAME_NANOS = 16_000_000L
  }
}

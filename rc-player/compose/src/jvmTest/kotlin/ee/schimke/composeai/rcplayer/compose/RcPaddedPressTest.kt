package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcMultiClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcMultiClickType
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRippleModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * A gesture is tested against the component's whole box, and reaches each handler in coordinates
 * that leave out only the padding before that handler.
 *
 * That is `remote-core`'s `ComponentModifiers` dispatch; androidx-main `412c1b7bb` finished it by
 * making long press, double click and touch cancel subtract the preceding padding as click and
 * touch down, up and drag already did. This player appended its click and touch handlers after
 * every padding of the component, where a Compose pointer node sees only what lands inside that
 * padding. So a press in the padding did nothing, and a press on the content reached the ripple in
 * the padded content's space: with `ripple, click, padding(40)` the wave started 40px up and left
 * of the finger.
 */
@OptIn(ExperimentalTestApi::class)
class RcPaddedPressTest {

  @Test
  fun aPressInThePaddingReachesTheClickHandlers() {
    for (multiClick in listOf(false, true)) for (paddingFirst in listOf(false, true)) {
      runSkikoComposeUiTest(size = Size(SIZE, SIZE), density = Density(1f)) {
        val events = mutableListOf<RcPlayerEvent>()
        setContent {
          RcComposePlayer(document(multiClick, paddingFirst, ripple = false), onEvent = events::add)
        }
        waitForIdle()
        onRoot().performTouchInput {
          down(Offset(10f, 10f))
          up()
        }
        mainClock.advanceTimeBy(1_500)
        assertEquals(
          listOf<RcPlayerEvent>(RcPlayerEvent.HostAction(77)),
          events,
          "multiClick=$multiClick paddingFirst=$paddingFirst",
        )
      }
    }
  }

  @Test
  fun aLongPressInThePaddingReachesTheLongClickHandler() =
    runSkikoComposeUiTest(size = Size(SIZE, SIZE), density = Density(1f)) {
      val events = mutableListOf<RcPlayerEvent>()
      setContent {
        RcComposePlayer(
          document(multiClick = true, paddingFirst = false, ripple = false),
          onEvent = events::add,
        )
      }
      waitForIdle()
      onRoot().performTouchInput { down(Offset(10f, 10f)) }
      mainClock.advanceTimeBy(1_500)
      onRoot().performTouchInput { up() }
      mainClock.advanceTimeBy(1_500)
      assertEquals(listOf<RcPlayerEvent>(RcPlayerEvent.HostAction(79)), events)
    }

  @Test
  fun theRippleStartsUnderTheFingerWhereverThePaddingIsWritten() {
    for (multiClick in listOf(false, true)) for (paddingFirst in listOf(false, true)) {
      runSkikoComposeUiTest(size = Size(SIZE, SIZE), density = Density(1f)) {
        setContent { RcComposePlayer(document(multiClick, paddingFirst, ripple = true)) }
        waitForIdle()
        mainClock.autoAdvance = false
        onRoot().performTouchInput { down(Offset(PRESS, PRESS)) }
        mainClock.advanceTimeBy(48)

        // The wave has barely left its origin: the centre of what it has lit is where it started.
        val pixels = onRoot().captureToImage().toPixelMap()
        var x = 0.0
        var y = 0.0
        var lit = 0.0
        for (py in 0 until pixels.height) for (px in 0 until pixels.width) {
          val light = pixels[px, py].red.toDouble()
          x += px * light
          y += py * light
          lit += light
        }
        val where = "multiClick=$multiClick paddingFirst=$paddingFirst"
        assertTrue(lit > 0.0, "no ripple drawn ($where)")
        assertTrue(
          abs(x / lit - PRESS) < 2.0 && abs(y / lit - PRESS) < 2.0,
          "the ripple started at (${x / lit}, ${y / lit}), not under the press at $PRESS ($where)",
        )
        onRoot().performTouchInput { up() }
      }
    }
  }

  /**
   * Evidence for `renders/cmp-padded-press/`, written only when `RC_LAYOUT_EVIDENCE_DIR` is set:
   * the box 120ms into a press at (70, 70), with the padding written after the ripple and click and
   * then before them, at 3x. The green ticks surround the press; the grey disc is the ripple's
   * wave.
   */
  @Test
  fun writePaddedPressEvidence() {
    val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    val side = SIZE.toInt() * EVIDENCE_SCALE
    val surface = Surface.makeRasterN32Premul(side * 2 + EVIDENCE_GAP, side)
    surface.canvas.clear(0xffffffff.toInt())
    listOf(false, true).forEachIndexed { cell, paddingFirst ->
      runSkikoComposeUiTest(size = Size(SIZE, SIZE), density = Density(1f)) {
        setContent { RcComposePlayer(document(multiClick = false, paddingFirst, ripple = true)) }
        waitForIdle()
        mainClock.autoAdvance = false
        onRoot().performTouchInput { down(Offset(PRESS, PRESS)) }
        mainClock.advanceTimeBy(EVIDENCE_MILLIS)
        val pixels = onRoot().captureToImage().toPixelMap()
        val left = cell * (side + EVIDENCE_GAP)
        val paint = Paint()
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
          // The wave is a few percent of white over black; stretched so it reads at a glance.
          val light = (pixels[x, y].red * 255 * EVIDENCE_GAIN).toInt().coerceIn(0, 255)
          paint.color = (0xff shl 24) or (light shl 16) or (light shl 8) or light
          surface.canvas.drawRect(
            Rect.makeXYWH(
              (left + x * EVIDENCE_SCALE).toFloat(),
              (y * EVIDENCE_SCALE).toFloat(),
              EVIDENCE_SCALE.toFloat(),
              EVIDENCE_SCALE.toFloat(),
            ),
            paint,
          )
        }
        paint.color = 0xff00c853.toInt()
        val centre = PRESS * EVIDENCE_SCALE
        // Four ticks around the press, open in the middle so the wave's own origin stays visible.
        for (reach in listOf(-EVIDENCE_TICK_END, EVIDENCE_TICK_START)) {
          val length = EVIDENCE_TICK_END - EVIDENCE_TICK_START
          surface.canvas.drawRect(
            Rect.makeXYWH(left + centre + reach, centre - 1f, length, 2f),
            paint,
          )
          surface.canvas.drawRect(
            Rect.makeXYWH(left + centre - 1f, centre + reach, 2f, length),
            paint,
          )
        }
        onRoot().performTouchInput { up() }
      }
    }
    directory
      .resolve("ripple-padding-after-then-before.png")
      .writeBytes(surface.makeImageSnapshot().encodeToData()!!.bytes)
  }

  private companion object {
    const val EVIDENCE_SCALE = 3
    const val EVIDENCE_GAP = 12
    const val EVIDENCE_GAIN = 6f
    const val EVIDENCE_MILLIS = 120L
    const val EVIDENCE_TICK_START = 24f
    const val EVIDENCE_TICK_END = 42f
    const val SIZE = 120f
    const val PADDING = 40f

    /** Inside the padded content, off its centre so a mirrored or centred origin cannot pass. */
    const val PRESS = 70f
    val END = RcNoArg(RcOpcodes.CONTAINER_END)

    /**
     * A 120 x 120 opaque black box with 40px of padding all round, written before or after its
     * ripple and click handlers. A click sends host action 77; a multi-click's long press, 79.
     */
    fun document(multiClick: Boolean, paddingFirst: Boolean, ripple: Boolean): RcDocument {
      val inset = RcFloatWord.literal(PADDING)
      val padding = RcPaddingModifier(inset, inset, inset, inset)
      return RcDocument(
        RcHeader(
          RcVersion(1, 0, 0),
          legacyWidth = SIZE.toInt(),
          legacyHeight = SIZE.toInt(),
          modern = false,
        ),
        buildList {
          add(RcRootLayout(1))
          add(RcBoxLayout(2, 20, 1, 4))
          add(RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE)))
          add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE)))
          add(
            RcBackgroundModifier(
              flags = 0,
              colorId = 0,
              reserved1 = 0,
              reserved2 = 0,
              red = RcFloatWord.literal(0f),
              green = RcFloatWord.literal(0f),
              blue = RcFloatWord.literal(0f),
              alpha = RcFloatWord.literal(1f),
              shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
            ),
          )
          if (paddingFirst) add(padding)
          if (ripple) add(RcRippleModifier)
          add(if (multiClick) RcMultiClickModifier(RcMultiClickType.SINGLE) else RcClickModifier)
          add(RcHostAction(77))
          add(END)
          if (multiClick) {
            add(RcMultiClickModifier(RcMultiClickType.LONG))
            add(RcHostAction(79))
            add(END)
          }
          if (!paddingFirst) add(padding)
          add(END)
          add(END)
        },
      )
    }
  }
}

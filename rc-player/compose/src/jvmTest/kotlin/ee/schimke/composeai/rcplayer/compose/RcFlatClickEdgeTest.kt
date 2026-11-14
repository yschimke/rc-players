package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.protocol.RcZIndexModifier
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cases where a plain clickable cannot be a flat tap target, and stays a component instead: one
 * holding a composed child, whose texts its own clickable merges, and one a rounded clip cuts, its
 * own before its padding or a clipped group's around it, which a layer clip clips taps for.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcFlatClickEdgeTest {
  @Test
  fun aClickableHoldingAComposedTextIsReadWithItsText() {
    val prepared = RcPreparedDocument(clickableHoldingComposedText())
    assertTrue(CLICKABLE in prepared.plainComponents, "the clickable group is not plain")
    val components = labels(prepared, flatten = false)
    assertTrue(
      components.any { "Live" in it },
      "the fixture's clickable is not labelled: $components",
    )
    assertEquals(components, labels(prepared, flatten = true))
  }

  @Test
  fun aClickableWithItsOwnRoundedClipTakesNoTapInItsCorners() =
    assertTapsAsComponents(clippedClickable(ownClip = true))

  @Test
  fun aClickableInsideAClippedGroupTakesNoTapInItsCorners() =
    assertTapsAsComponents(clippedClickable(ownClip = false))

  private fun assertTapsAsComponents(document: RcDocument) {
    val prepared = RcPreparedDocument(document)
    assertTrue(CLICKABLE in prepared.plainComponents, "the clickable is not plain")
    // A corner the clip cuts away, and the middle.
    val points = listOf(Offset(3f, 3f), Offset(50f, 50f))
    val components = taps(prepared, flatten = false, points)
    assertEquals(listOf(false, true), components.map { it.isNotEmpty() }, "fixture: $components")
    assertEquals(components, taps(prepared, flatten = true, points))
  }

  /** The texts of each clickable node, top to bottom. */
  private fun labels(prepared: RcPreparedDocument, flatten: Boolean): List<List<String>> {
    var result = emptyList<List<String>>()
    play(prepared, flatten) {
      result =
        onAllNodes(hasClickAction())
          .fetchSemanticsNodes()
          .sortedBy { it.boundsInRoot.top }
          .map {
            it.config.getOrNull(SemanticsProperties.Text)?.map { text -> text.text }.orEmpty()
          }
    }
    return result
  }

  /** The events a tap at each of [points] sends. */
  private fun taps(
    prepared: RcPreparedDocument,
    flatten: Boolean,
    points: List<Offset>,
  ): List<List<RcPlayerEvent>> {
    val result = mutableListOf<List<RcPlayerEvent>>()
    play(prepared, flatten) { events ->
      for (point in points) {
        events.clear()
        onRoot().performTouchInput { click(point) }
        waitForIdle()
        result += events.toList()
      }
    }
    return result
  }

  private fun play(
    prepared: RcPreparedDocument,
    flatten: Boolean,
    block: androidx.compose.ui.test.ComposeUiTest.(MutableList<RcPlayerEvent>) -> Unit,
  ) {
    runSkikoComposeUiTest(size = Size(WIDTH.toFloat(), HEIGHT.toFloat()), density = Density(1f)) {
      val events = mutableListOf<RcPlayerEvent>()
      setContent {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides flatten,
        ) {
          Box {
            RcComposePlayer(
              prepared,
              Modifier.width(WIDTH.dp),
              onEvent = { events += it },
              systemColors = { null },
            )
          }
        }
      }
      waitForIdle()
      block(events)
    }
  }

  /** A column holding a clickable box whose text sits in a z-indexed box, which stays composed. */
  private fun clickableHoldingComposedText(): RcDocument =
    document(
      listOf(RcBoxLayout(CLICKABLE, -1, 1, 1)) +
        click(1) +
        listOf(
          RcLayoutContent(CLICKABLE + 1),
          RcBoxLayout(CLICKABLE + 2, -1, 1, 1),
          RcZIndexModifier(RcFloatWord.literal(0f)),
          RcLayoutContent(CLICKABLE + 3),
        ) +
        text(CLICKABLE + 4) +
        listOf(END, END, END, END),
    )

  /**
   * A 100 px clickable box, rounded to a circle: by its own clip, before any padding, or by a plain
   * group around it.
   */
  private fun clippedClickable(ownClip: Boolean): RcDocument {
    val circle = RcFloatWord.literal(50f)
    val clip = RcRoundedClipRectModifier(circle, circle, circle, circle)
    val size =
      listOf(
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
      )
    val clickable =
      listOf<RcOperation>(RcBoxLayout(CLICKABLE, -1, 1, 1)) +
        (if (ownClip) listOf(clip) else emptyList()) +
        size +
        click(1) +
        listOf(RcLayoutContent(CLICKABLE + 1)) +
        text(CLICKABLE + 4) +
        listOf(END, END)
    return document(
      if (ownClip) clickable
      else
        listOf(RcBoxLayout(GROUP, -1, 1, 1), clip) +
          size +
          listOf(RcLayoutContent(GROUP + 1)) +
          clickable +
          listOf(END, END),
    )
  }

  private fun document(content: List<RcOperation>) =
    RcDocument(
      RcHeader(RcVersion(1, 1, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      listOf(
        RcTextData(LABEL, "Live"),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcColumnLayout(COLUMN, -1, 1, 1, RcFloatWord.literal(0f)),
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcLayoutContent(COLUMN + 1),
      ) + content + listOf(END, END, END, END),
    )

  private fun click(action: Int) = listOf(RcClickModifier, RcHostAction(action), END)

  private fun text(id: Int) =
    listOf(
      RcCoreText(
        textId = LABEL,
        properties =
          listOf(
            RcTextStyleProperty.IntValue(1, id),
            RcTextStyleProperty.IntValue(2, id + 1),
            RcTextStyleProperty.IntValue(3, 0xff000000.toInt()),
            RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(14f)),
          ),
      ),
      END,
    )

  private companion object {
    val END = RcNoArg(RcOpcodes.CONTAINER_END)
    const val WIDTH = 200
    const val HEIGHT = 120
    const val COLUMN = 3
    const val GROUP = 20
    const val CLICKABLE = 40
    const val LABEL = 100
  }
}

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
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
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcVisibilityModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.protocol.RcZIndexModifier
import ee.schimke.composeai.rcplayer.runtime.RcLayoutNode
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import ee.schimke.composeai.rcplayer.trace.RcTrace
import ee.schimke.composeai.rcplayer.trace.RcTraceRecorder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A plain clickable the flat layout draws ([LocalRcFlattenLayout]) is still tapped, and still read,
 * as its own composable is: the same clickable nodes, with the same texts and bounds, and a click
 * on each sends its host the same events, both ways. Checked on a session card (a clickable card
 * holding clickable speaker names and a clickable bookmark), and on every catalog document with a
 * plain clickable.
 */
@OptIn(ExperimentalRcPlayerApi::class, ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcFlatClickTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }

  @Test
  fun aSessionCardsClickablesAreTappedAndReadAsTheirComponentsAre() {
    // The second speaker shown, invisible (alpha 0, still there to tap) and gone.
    for (second in listOf(1, 2, 0)) {
      val prepared = RcPreparedDocument(sessionCard(second))
      for (id in listOf(CARD, FIRST, SECOND, BOOKMARK)) {
        assertTrue(id in prepared.plainComponents, "component $id is not plain")
      }
      val components = clicks(prepared, flatten = false)
      assertTrue(components.size >= 3, "too few clickables: $components")
      val recorder = RcTraceRecorder()
      RcTrace.recorder = recorder
      val flat =
        try {
          clicks(prepared, flatten = true)
        } finally {
          RcTrace.recorder = null
        }
      assertTrue(recorder.summary().any { it.name == "rc:drawFlat" }, "the card was not flattened")
      assertEquals(components, flat, "second speaker $second")
    }
  }

  @Test
  fun aFlattenedClickableIsTappedAndReadAsItsComponentIs() {
    var compared = 0
    val changed = mutableListOf<String>()
    for (file in corpus) {
      val prepared =
        runCatching { RcPreparedDocument.decode(file.readBytes()) }.getOrNull() ?: continue
      if (prepared.changesLayoutOnItsOwn || !prepared.hasPlainClickable()) continue
      val header = prepared.document.header
      if (header.width !in 1..1024 || header.height !in 1..1024) continue
      val components = runCatching { clicks(prepared, flatten = false) }.getOrNull() ?: continue
      compared++
      val flat = clicks(prepared, flatten = true)
      if (flat != components) changed += "${file.name}:\n  components $components\n  flat $flat"
    }
    // The catalog's clickables write the document's own state or ripple, so none is plain yet:
    // this guards the ones that are, as the catalog grows.
    assertTrue(
      changed.isEmpty(),
      "${changed.size} of $compared differ:\n${changed.joinToString("\n")}",
    )
  }

  /** Each clickable node: its texts, its bounds, and the events a click on it sends. */
  private fun clicks(prepared: RcPreparedDocument, flatten: Boolean): List<String> {
    val width = prepared.document.header.width.toFloat()
    val height = prepared.document.header.height.toFloat()
    val result = mutableListOf<String>()
    runSkikoComposeUiTest(size = Size(width, height), density = Density(1f)) {
      val events = mutableListOf<RcPlayerEvent>()
      setContent {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides flatten,
        ) {
          Box {
            RcComposePlayer(
              prepared,
              Modifier.width(width.dp),
              onEvent = { events += it },
              systemColors = { null },
            )
          }
        }
      }
      waitForIdle()
      val nodes: SemanticsNodeInteractionCollection = onAllNodes(hasClickAction())
      val described =
        nodes.fetchSemanticsNodes().mapIndexed { index, node ->
          val texts = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
          Triple(index, texts, node.boundsInRoot)
        }
      for ((index, texts, bounds) in described.sortedBy { it.third.top * 100000 + it.third.left }) {
        events.clear()
        nodes[index].performClick()
        waitForIdle()
        result += "$texts at $bounds -> $events"
      }
    }
    return result
  }

  /**
   * A session card: a clickable card (a row with a rounded clip, a background and padding) holding
   * a title, two clickable speaker names with a separator, and a clickable bookmark with padding, a
   * background and a text of its own. The second speaker's visibility is [second].
   */
  private fun sessionCard(second: Int): RcDocument {
    fun click(action: Int) = listOf(RcClickModifier, RcHostAction(action), END)
    fun text(id: Int, textId: Int, extra: List<RcOperation> = emptyList()) =
      listOf<RcOperation>(
        RcCoreText(
          textId = textId,
          properties =
            listOf(
              RcTextStyleProperty.IntValue(1, id),
              RcTextStyleProperty.IntValue(2, id + 1),
              RcTextStyleProperty.IntValue(3, 0xff000000.toInt()),
              RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(14f)),
            ),
        ),
      ) + extra + listOf(END)
    fun background(red: Float) =
      RcBackgroundModifier(
        flags = 0,
        colorId = 0,
        reserved1 = 0,
        reserved2 = 0,
        red = RcFloatWord.literal(red),
        green = RcFloatWord.literal(0.8f),
        blue = RcFloatWord.literal(0.8f),
        alpha = RcFloatWord.literal(1f),
        shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
      )
    fun padding(value: Float) =
      RcPaddingModifier(
        RcFloatWord.literal(value),
        RcFloatWord.literal(value),
        RcFloatWord.literal(value),
        RcFloatWord.literal(value),
      )
    val corner = RcFloatWord.literal(12f)
    return RcDocument(
      RcHeader(RcVersion(1, 1, 0), legacyWidth = 300, legacyHeight = 120, modern = false),
      listOf(
        RcTextData(TITLE_TEXT, "Flattening a feed"),
        RcTextData(TIME_TEXT, "10:00"),
        RcTextData(BADGE_TEXT, "Live"),
        RcTextData(FIRST_TEXT, "Ada"),
        RcTextData(SEPARATOR_TEXT, ", "),
        RcTextData(SECOND_TEXT, "Grace"),
        RcTextData(BOOKMARK_TEXT, "Save"),
        RcIntegerConstant(SECOND_VISIBLE, second),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcRowLayout(CARD, -1, 1, 1, RcFloatWord.literal(8f)),
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcRoundedClipRectModifier(corner, corner, corner, corner),
        background(0.9f),
        padding(8f),
      ) +
        click(1) +
        listOf(
          RcLayoutContent(CARD + 1),
          RcColumnLayout(10, -1, 1, 1, RcFloatWord.literal(4f)),
          RcWidthModifier(RcDimensionType.WEIGHT, RcFloatWord.literal(1f)),
          RcLayoutContent(11),
        ) +
        text(15, TIME_TEXT) +
        // A badge the flat layout leaves composed (it has a z-index), between two plain texts: the
        // card reads its texts in their order, the badge's in its place.
        listOf(
          RcBoxLayout(BADGE, -1, 1, 1),
          RcZIndexModifier(RcFloatWord.literal(0f)),
          RcLayoutContent(BADGE + 1),
        ) +
        text(BADGE + 10, BADGE_TEXT) +
        listOf(END, END) +
        text(20, TITLE_TEXT) +
        listOf(RcRowLayout(30, -1, 1, 1, RcFloatWord.literal(0f)), RcLayoutContent(31)) +
        text(FIRST, FIRST_TEXT, click(2)) +
        text(50, SEPARATOR_TEXT) +
        text(SECOND, SECOND_TEXT, listOf(RcVisibilityModifier(SECOND_VISIBLE)) + click(3)) +
        listOf(END, END, END, END) +
        listOf(
          RcBoxLayout(BOOKMARK, -1, 2, 2),
          padding(4f),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(48f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(32f)),
          background(0.5f),
        ) +
        click(4) +
        listOf(RcLayoutContent(BOOKMARK + 1)) +
        text(80, BOOKMARK_TEXT) +
        listOf(END, END) +
        listOf(END, END, END, END),
    )
  }

  private fun RcPreparedDocument.hasPlainClickable(): Boolean {
    fun visit(node: RcLayoutNode): Boolean =
      (node.componentId in plainComponents && node.modifiers.clicks.isNotEmpty()) ||
        node.layoutChildren().any(::visit)
    return layout?.let(::visit) == true
  }

  private companion object {
    val END = RcNoArg(RcOpcodes.CONTAINER_END)
    const val CARD = 3
    const val FIRST = 40
    const val SECOND = 60
    const val BOOKMARK = 70
    const val TITLE_TEXT = 101
    const val FIRST_TEXT = 102
    const val SEPARATOR_TEXT = 103
    const val SECOND_TEXT = 104
    const val BOOKMARK_TEXT = 105
    const val SECOND_VISIBLE = 106
    const val TIME_TEXT = 107
    const val BADGE_TEXT = 108
    const val BADGE = 90
  }
}

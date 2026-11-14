package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcAlignByModifier
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
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
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * Flattened layout ([LocalRcFlattenLayout]) draws what the per-component path draws: every catalog
 * document it applies to, rendered both ways, is the same to the pixel; and the texts it draws
 * itself are still in the semantics tree.
 */
@OptIn(ExperimentalRcPlayerApi::class)
class RcFlatLayoutTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }

  @Test
  fun aFlattenedDocumentDrawsTheSameAsItsComponents() {
    var compared = 0
    val changed = mutableListOf<String>()
    for (file in corpus) {
      val prepared =
        runCatching { RcPreparedDocument.decode(file.readBytes()) }.getOrNull() ?: continue
      // Only a document that defers bounds animations is flattened.
      if (prepared.plainComponents.isEmpty() || prepared.changesLayoutOnItsOwn) continue
      val header = prepared.document.header
      if (header.width !in 1..1024 || header.height !in 1..1024) continue
      val components = render(prepared, flatten = false) ?: continue
      if (!components.contentEquals(render(prepared, flatten = false))) continue
      compared++
      if (!components.contentEquals(render(prepared, flatten = true))) changed += file.name
    }
    assertTrue(compared > 100, "only $compared corpus documents were compared")
    assertTrue(changed.isEmpty(), "${changed.size} of $compared drew differently: $changed")
  }

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun aTextTheContainerDrawsIsStillInTheSemanticsTree() =
    runSkikoComposeUiTest(size = Size(454f, 200f), density = Density(DENSITY)) {
      val prepared = RcPreparedDocument.decode(corpus.single { it.name == SUBHEADER }.readBytes())
      assertTrue(prepared.plainComponents.isNotEmpty())
      setContent {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides true,
        ) {
          RcComposePlayer(prepared, systemColors = { null })
        }
      }
      waitForIdle()
      onNodeWithText("Subtitle").assertExists()
    }

  @Test
  fun aRowLeavesRoomForItsGapsWhenItMeasures() {
    // 60 + 10 + 60 in 100: the second box gets the 30 left after the gap, as a Compose row gives
    // it.
    assertSameBothWays(linear(row = true, weighted = false), width = 100, height = 40, room = 40)
  }

  @Test
  fun aRowRunsFromTheEndRightToLeft() {
    assertSameBothWays(linear(row = true, weighted = false), width = 200, height = 40, rtl = true)
  }

  /**
   * Compose gives a weighted child no space when the main axis is unbounded; the flat layout
   * measures it the same way rather than dropping it (which drew the same nothing, but left a
   * composed child unmeasured).
   */
  @Test
  fun aWeightedChildOfAnUnboundedColumnIsStillMeasured() {
    assertSameBothWays(
      linear(row = false, weighted = true),
      width = 100,
      height = 120,
      unboundedHeight = true,
    )
  }

  @Test
  fun aWeightedColumnStillLeavesItsGaps() {
    assertSameBothWays(
      linear(row = false, weighted = true),
      width = 100,
      height = 120,
      roomBelow = 40,
    )
  }

  @Test
  fun aCircularBorderStaysCircular() {
    val border =
      RcBorderModifier(
        flags = 0,
        colorId = 0,
        wireVersion = 1,
        reserved = 0,
        borderWidth = RcFloatWord.literal(4f),
        roundedCorner = RcFloatWord.literal(0f),
        red = RcFloatWord.literal(0f),
        green = RcFloatWord.literal(0f),
        blue = RcFloatWord.literal(1f),
        alpha = RcFloatWord.literal(1f),
        shapeType = RcBackgroundModifier.SHAPE_CIRCLE,
      )
    assertSameBothWays(
      document(RcBoxLayout(3, -1, 2, 2), listOf(box(10, 1f, 60f, 60f, listOf(border)))),
      width = 100,
      height = 100,
    )
  }

  @Test
  fun aRowAlignedByAnAnchorKeepsItsAlignment() {
    val anchored = box(10, 1f, 30f, 20f, listOf(RcAlignByModifier(RcFloatWord.literal(15f), 0)))
    assertSameBothWays(
      document(
        RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
        listOf(anchored, box(20, 0f, 30f, 40f)),
      ),
      width = 100,
      height = 80,
    )
  }

  @Test
  fun aPlainBoxDrawnAfterAComposedOneStaysOnTop() {
    // The first child is composed (it has a z-index); the second, plain one
    // overlaps it and has to draw over it.
    val composed = box(10, 1f, 80f, 80f, composedMarker)
    assertSameBothWays(
      document(
        RcBoxLayout(3, -1, 1, 4),
        listOf(composed, box(20, 0f, 40f, 40f)),
        listOf(RcIntegerConstant(VISIBLE, 1)),
      ),
      width = 100,
      height = 100,
    )
  }

  @Test
  fun halfPixelPaddingRoundsAsOneInset() {
    // 31.5 + 31.5 px of padding is 63 px, not 64: the next box starts where it does on a component.
    val padded = box(10, 1f, 20f, 20f, leading = listOf(padding(31.5f, 31.5f)))
    assertSameBothWays(
      document(
        RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
        listOf(padded, box(20, 0f, 20f, 20f)),
      ),
      width = 140,
      height = 30,
    )
  }

  @Test
  fun weightedSharesAddUpToTheRow() {
    // Two equal weights in 101 px: 51 and 50, not 51 and 51.
    assertSameBothWays(
      document(
        RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
        listOf(
          box(10, 1f, 1f, 20f, widthType = RcDimensionType.WEIGHT),
          box(20, 0f, 1f, 20f, widthType = RcDimensionType.WEIGHT),
        ),
      ),
      width = 101,
      height = 30,
      room = 20,
    )
  }

  @Test
  fun startPaddingIsOnTheRightInRightToLeft() {
    val padded = box(10, 1f, 20f, 20f, leading = listOf(padding(20f, 0f)))
    assertSameBothWays(
      document(
        RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
        listOf(padded, box(20, 0f, 20f, 20f)),
      ),
      width = 120,
      height = 30,
      rtl = true,
    )
  }

  private fun padding(start: Float, end: Float) =
    RcPaddingModifier(
      RcFloatWord.literal(start),
      RcFloatWord.literal(0f),
      RcFloatWord.literal(end),
      RcFloatWord.literal(0f),
    )

  @Test
  fun aRoundedClipStillClipsAComposedChild() {
    // A plain box clipped to round corners holds a composed child that fills it: the corners stay
    // clipped (the box stays a component, whose clip is a layer clip).
    val corner = RcFloatWord.literal(20f)
    val composed = box(20, 0f, 60f, 60f, composedMarker)
    val clipped =
      listOf<RcOperation>(
        RcBoxLayout(10, -1, 1, 1),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
        RcRoundedClipRectModifier(corner, corner, corner, corner),
        RcLayoutContent(11),
      ) + composed + listOf(end, end)
    assertSameBothWays(
      document(
        RcColumnLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
        listOf(clipped, box(30, 1f, 60f, 10f)),
        listOf(RcIntegerConstant(VISIBLE, 1)),
      ),
      width = 80,
      height = 80,
    )
  }

  @Test
  fun aPlainBoxAfterAClippedGroupWithAComposedChildStaysOnTop() {
    // The clipped group stays a component, so the box holding it and a later, overlapping plain
    // box does too: the plain box draws over the group, as on the per-component path.
    val corner = RcFloatWord.literal(10f)
    val clipped =
      listOf<RcOperation>(
        RcBoxLayout(10, -1, 1, 1),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
        RcRoundedClipRectModifier(corner, corner, corner, corner),
        RcBackgroundModifier(
          flags = 0,
          colorId = 0,
          reserved1 = 0,
          reserved2 = 0,
          red = RcFloatWord.literal(0f),
          green = RcFloatWord.literal(0f),
          blue = RcFloatWord.literal(1f),
          alpha = RcFloatWord.literal(1f),
          shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
        ),
        RcLayoutContent(11),
      ) + box(20, 0f, 20f, 20f, composedMarker) + listOf(end, end)
    assertSameBothWays(
      document(
        RcBoxLayout(3, -1, 1, 4),
        listOf(clipped, box(30, 1f, 40f, 40f)),
        listOf(RcIntegerConstant(VISIBLE, 1)),
      ),
      width = 80,
      height = 80,
    )
  }

  @Test
  fun aGoneWeightedChildTakesNoShare() {
    // Two equal weights, one of them gone: the other has the whole row, as a Compose row gives it.
    val gone = box(20, 0f, 1f, 20f, listOf(RcVisibilityModifier(VISIBLE)), RcDimensionType.WEIGHT)
    assertSameBothWays(
      document(
        RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
        listOf(box(10, 1f, 1f, 20f, widthType = RcDimensionType.WEIGHT), gone),
        listOf(RcIntegerConstant(VISIBLE, 0)),
      ),
      width = 100,
      height = 40,
    )
  }

  @Test
  fun aGoneChildIsFlattenedAndLeavesNoGap() {
    // Three boxes 10 apart, the middle one gone: the third sits 10 after the first, as on the
    // per-component path, and the gone box is one the flat layout handles.
    val document =
      document(
        RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(10f)),
        listOf(
          box(10, 1f, 30f, 20f),
          box(20, 0.5f, 30f, 20f, listOf(RcVisibilityModifier(VISIBLE))),
          box(30, 0f, 30f, 20f),
        ),
        listOf(RcIntegerConstant(VISIBLE, 0)),
      )
    assertTrue(20 in RcPreparedDocument(document).plainComponents, "the gone box is not plain")
    assertSameBothWays(document, width = 120, height = 40)
  }

  @Test
  fun anInvisibleChildKeepsItsSpaceAndDrawsNothing() {
    val document =
      document(
        RcColumnLayout(3, -1, 1, 4, RcFloatWord.literal(5f)),
        listOf(
          box(10, 1f, 30f, 20f),
          box(20, 0.5f, 30f, 20f, listOf(RcVisibilityModifier(VISIBLE))),
          box(30, 0f, 30f, 20f),
        ),
        listOf(RcIntegerConstant(VISIBLE, 2)),
      )
    assertSameBothWays(document, width = 60, height = 80)
  }

  @Test
  fun aHiddenGroupHidesWhatItHolds() {
    // A group (and the plain box inside it) gone or invisible, with a background of its own.
    for (visibility in listOf(0, 2)) {
      val group =
        listOf<RcOperation>(
          RcRowLayout(10, -1, 1, 1, RcFloatWord.literal(0f)),
          RcVisibilityModifier(VISIBLE),
          RcPaddingModifier(
            RcFloatWord.literal(4f),
            RcFloatWord.literal(4f),
            RcFloatWord.literal(4f),
            RcFloatWord.literal(4f),
          ),
          RcLayoutContent(11),
        ) + box(20, 0.5f, 30f, 20f) + listOf(end, end)
      assertSameBothWays(
        document(
          RcColumnLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
          listOf(box(30, 1f, 40f, 10f), group, box(40, 0f, 40f, 10f)),
          listOf(RcIntegerConstant(VISIBLE, visibility)),
        ),
        width = 60,
        height = 80,
      )
    }
  }

  @Test
  fun aHiddenGroupWithAComposedChildStaysAComponent() {
    // The flat layout hides only what it draws, so a group holding a composed child keeps its
    // visibility as a component: the composed child is hidden with it.
    for (visibility in listOf(0, 2)) {
      val group =
        listOf<RcOperation>(
          RcBoxLayout(10, -1, 1, 1),
          RcVisibilityModifier(VISIBLE),
          RcLayoutContent(11),
        ) + box(20, 0.5f, 30f, 20f, composedMarker) + listOf(end, end)
      assertSameBothWays(
        document(
          RcColumnLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
          listOf(box(30, 1f, 40f, 10f), group, box(40, 0f, 40f, 10f)),
          listOf(RcIntegerConstant(VISIBLE, visibility)),
        ),
        width = 60,
        height = 80,
      )
    }
  }

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun aGoneTextIsNotInTheSemanticsTree() =
    runSkikoComposeUiTest(size = Size(200f, 100f), density = Density(1f)) {
      val document =
        document(
          RcColumnLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
          listOf(
            text(50, TEXT, listOf()),
            text(60, TEXT + 1, listOf(RcVisibilityModifier(VISIBLE))),
          ),
          listOf(
            RcTextData(TEXT, "Shown"),
            RcTextData(TEXT + 1, "Hidden"),
            RcIntegerConstant(VISIBLE, 0),
          ),
        )
      val prepared = RcPreparedDocument(document)
      assertTrue(prepared.plainComponents.isNotEmpty())
      setContent {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides true,
        ) {
          RcComposePlayer(prepared, Modifier.width(200.dp))
        }
      }
      waitForIdle()
      onNodeWithText("Shown").assertExists()
      onNodeWithText("Hidden").assertDoesNotExist()
    }

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun anAutoSizedTextInAnInvisibleGroupKeepsItsSemantics() =
    runSkikoComposeUiTest(size = Size(200f, 100f), density = Density(1f)) {
      // Auto-sized text is composed rather than drawn by the flat layout, so an invisible group
      // holding it stays a component too, and the text keeps its semantics as at alpha 0.
      val group =
        listOf<RcOperation>(
          RcRowLayout(10, -1, 1, 1, RcFloatWord.literal(0f)),
          RcVisibilityModifier(VISIBLE),
          RcLayoutContent(11),
        ) + text(50, TEXT, listOf(), autoSize = true) + listOf(end, end)
      val prepared =
        RcPreparedDocument(
          document(
            RcColumnLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
            listOf(box(30, 1f, 40f, 10f), group),
            listOf(RcTextData(TEXT, "Invisible"), RcIntegerConstant(VISIBLE, 2)),
          ),
        )
      assertTrue(prepared.plainComponents.isNotEmpty())
      setContent {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides true,
        ) {
          RcComposePlayer(prepared, Modifier.width(200.dp))
        }
      }
      waitForIdle()
      onNodeWithText("Invisible").assertIsDisplayed()
    }

  @Test
  fun aVisibilityTheHostChangesStillChanges() {
    // A flattened card whose host then shows a gone box: the card switches to components (and its
    // bounds animation) and ends up drawing what the per-component path draws.
    val document =
      document(
        RcColumnLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
        listOf(
          box(10, 1f, 30f, 20f),
          box(20, 0.5f, 30f, 20f, listOf(RcVisibilityModifier(VISIBLE))),
          box(30, 0f, 30f, 20f),
        ),
        listOf(
          RcIntegerConstant(VISIBLE, 0),
          RcNamedVariable(VISIBLE, RcNamedVariable.INT_TYPE, "USER:shown"),
        ),
      )
    val prepared = RcPreparedDocument(document)
    assertTrue(20 in prepared.plainComponents)
    fun settled(flatten: Boolean): IntArray {
      val values = mutableStateMapOf<String, RcNamedValue>("USER:shown" to RcNamedValue.Integer(0))
      val scene =
        ImageComposeScene(width = 60, height = 80, density = Density(1f)) {
          CompositionLocalProvider(
            LocalRcDeferLayoutAnimations provides true,
            LocalRcFlattenLayout provides flatten,
          ) {
            RcComposePlayer(prepared, Modifier.width(60.dp), namedValues = values)
          }
        }
      try {
        var time = 0L
        repeat(3) { scene.render(time).close().also { time += FRAME_NANOS } }
        values["USER:shown"] = RcNamedValue.Integer(1)
        Snapshot.sendApplyNotifications()
        repeat(120) { scene.render(time).close().also { time += FRAME_NANOS } }
        val bitmap = Bitmap().apply { allocN32Pixels(60, 80) }
        check(scene.render(time).readPixels(bitmap))
        return IntArray(60 * 80) { bitmap.getColor(it % 60, it / 60) }
      } finally {
        scene.close()
      }
    }
    val components = settled(flatten = false)
    // The box was shown: the third box moved down to make room for it.
    assertTrue(components[60 * 50 + 5] != 0, "the shown box did not push the last one down")
    assertContentEquals(components, settled(flatten = true))
  }

  @Test fun aCardChangedByItsHostFlattensAgainOnceTheChangeSettles() = flattensAgainOnceSettled(1f)

  @Test
  fun aSlowedAnimationKeepsThePassUntilItEnds() =
    // The platform's animation duration scale (Android's developer setting) stretches the change's
    // 300 ms animations to 1200 ms: the card stays composed as components until they have run.
    flattensAgainOnceSettled(4f)

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  private fun flattensAgainOnceSettled(durationScale: Float) =
    runSkikoComposeUiTest(
      size = Size(200f, 200f),
      density = Density(1f),
      effectContext =
        object : androidx.compose.ui.MotionDurationScale {
          override val scaleFactor: Float = durationScale
        },
    ) {
      // Two texts and a box the host shows: flattened, both texts are one node's; while the change
      // animates, under the lookahead pass, each text is a component of its own; and once it has
      // settled the card is flattened again.
      val document =
        document(
          RcColumnLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
          listOf(
            text(50, TEXT, listOf()),
            box(20, 0.5f, 30f, 20f, listOf(RcVisibilityModifier(VISIBLE))),
            text(60, TEXT + 1, listOf()),
          ),
          listOf(
            RcTextData(TEXT, "First"),
            RcTextData(TEXT + 1, "Second"),
            RcIntegerConstant(VISIBLE, 0),
            RcNamedVariable(VISIBLE, RcNamedVariable.INT_TYPE, "USER:shown"),
          ),
        )
      val prepared = RcPreparedDocument(document)
      val values = mutableStateMapOf<String, RcNamedValue>("USER:shown" to RcNamedValue.Integer(0))
      mainClock.autoAdvance = false
      setContent {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides true,
        ) {
          RcComposePlayer(prepared, Modifier.width(200.dp), namedValues = values)
        }
      }
      fun textsOfFirst() =
        onNodeWithText("First", substring = true)
          .fetchSemanticsNode()
          .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
          .size
      mainClock.advanceTimeBy(100)
      assertEquals(2, textsOfFirst(), "the card was not flattened")
      values["USER:shown"] = RcNamedValue.Integer(1)
      mainClock.advanceTimeBy(100)
      assertEquals(1, textsOfFirst(), "the change did not switch the lookahead pass on")
      // Most of the way through the (scaled) animations, which a card switched back early snaps.
      mainClock.advanceTimeBy((225 * durationScale).toLong())
      assertEquals(1, textsOfFirst(), "the pass was dropped while the change still animated")
      mainClock.advanceTimeBy((1500 * durationScale).toLong())
      assertEquals(2, textsOfFirst(), "the card did not flatten again once the change settled")
    }

  /** A plain core text of [textId], filling the width, with [extra] modifiers. */
  private fun text(
    id: Int,
    textId: Int,
    extra: List<RcOperation>,
    autoSize: Boolean = false,
  ): List<RcOperation> =
    listOf<RcOperation>(
      RcCoreText(
        textId = textId,
        properties =
          listOf(
            RcTextStyleProperty.IntValue(1, id),
            RcTextStyleProperty.IntValue(2, id + 1),
            RcTextStyleProperty.IntValue(3, 0xff000000.toInt()),
            RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(16f)),
          ) + listOfNotNull(RcTextStyleProperty.BooleanValue(22, true).takeIf { autoSize }),
      ),
      RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
    ) + extra + listOf(end)

  /** A modifier that keeps a component composed rather than flattened, with no visible effect. */
  private val composedMarker = listOf<RcOperation>(RcZIndexModifier(RcFloatWord.literal(0f)))

  @Test
  fun aTextClippedByItsHeightStaysInsideIt() {
    // Wrapped text in a 20 px tall component, over a green box: what overflows is clipped.
    val text =
      listOf(
        RcCoreText(
          textId = TEXT,
          properties =
            listOf(
              RcTextStyleProperty.IntValue(1, 30),
              RcTextStyleProperty.IntValue(2, 31),
              RcTextStyleProperty.IntValue(3, 0xff000000.toInt()),
              RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(16f)),
            ),
        ),
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        end,
      )
    assertSameBothWays(
      document(
        RcColumnLayout(3, -1, 1, 4, RcFloatWord.literal(0f)),
        listOf(text, box(20, 0f, 100f, 60f)),
        listOf(RcTextData(TEXT, "WWWW WWWW WWWW WWWW WWWW WWWW")),
      ),
      width = 100,
      height = 100,
    )
  }

  /**
   * A box of [width] x [height] in a red-to-green [red], with [extra] modifiers after its
   * background.
   */
  private fun box(
    id: Int,
    red: Float,
    width: Float,
    height: Float,
    extra: List<RcOperation> = emptyList(),
    widthType: Int = RcDimensionType.EXACT,
    /** Modifiers before its size: padding here is outside the box. */
    leading: List<RcOperation> = emptyList(),
  ): List<RcOperation> = buildList {
    add(RcBoxLayout(id, -1, 1, 1))
    addAll(leading)
    add(RcWidthModifier(widthType, RcFloatWord.literal(width)))
    add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(height)))
    add(
      RcBackgroundModifier(
        flags = 0,
        colorId = 0,
        reserved1 = 0,
        reserved2 = 0,
        red = RcFloatWord.literal(red),
        green = RcFloatWord.literal(1f - red),
        blue = RcFloatWord.literal(0f),
        alpha = RcFloatWord.literal(1f),
        shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
      ),
    )
    addAll(extra)
    add(RcLayoutContent(id + 1))
    add(end)
    add(end)
  }

  /** [container] filling the width, holding [children]. */
  private fun document(
    container: RcOperation,
    children: List<List<RcOperation>>,
    constants: List<RcOperation> = emptyList(),
  ): RcDocument =
    RcDocument(
      // 1.1: a border's width is taken as written (see `borderWidthPixels`).
      RcHeader(RcVersion(1, 1, 0), legacyWidth = 200, legacyHeight = 120, modern = false),
      constants +
        listOf(
          RcRootLayout(1),
          RcLayoutContent(2),
          container,
          RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
          RcLayoutContent(4),
        ) +
        children.flatten() +
        listOf(end, end, end, end),
    )

  private val end = RcNoArg(RcOpcodes.CONTAINER_END)

  private fun assertSameBothWays(
    document: RcDocument,
    width: Int,
    height: Int,
    rtl: Boolean = false,
    unboundedHeight: Boolean = false,
    /** Space beside the player, so a child that overflows it shows. */
    room: Int = 0,
    /** Space below the player, so a child that overflows it downwards shows. */
    roomBelow: Int = 0,
  ) {
    val prepared = RcPreparedDocument(document)
    assertTrue(prepared.plainComponents.isNotEmpty(), "nothing to flatten")
    fun draw(flatten: Boolean): IntArray {
      val scene =
        ImageComposeScene(
          width = width + room,
          height = height + roomBelow,
          density = Density(1f),
        ) {
          CompositionLocalProvider(
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalRcDeferLayoutAnimations provides true,
            LocalRcFlattenLayout provides flatten,
          ) {
            val host =
              if (unboundedHeight) Modifier.wrapContentHeight(unbounded = true) else Modifier
            val size =
              if (roomBelow > 0) Modifier.width(width.dp).height(height.dp)
              else Modifier.width(width.dp)
            Box(host) { RcComposePlayer(prepared, size) }
          }
        }
      try {
        val sceneWidth = width + room
        val sceneHeight = height + roomBelow
        val bitmap = Bitmap().apply { allocN32Pixels(sceneWidth, sceneHeight) }
        check(scene.render(0L).readPixels(bitmap))
        return IntArray(sceneWidth * sceneHeight) {
          bitmap.getColor(it % sceneWidth, it / sceneWidth)
        }
      } finally {
        scene.close()
      }
    }
    val components = draw(flatten = false)
    assertTrue(components.any { it != 0 }, "nothing was drawn")
    assertContentEquals(components, draw(flatten = true))
  }

  /** A row or column filling the width: two coloured boxes 10 apart, the second weighted or 60. */
  private fun linear(row: Boolean, weighted: Boolean): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    fun box(id: Int, red: Float, weightedBox: Boolean) =
      listOf(
        RcBoxLayout(id, -1, 1, 1),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
        if (weightedBox) RcHeightModifier(RcDimensionType.WEIGHT, RcFloatWord.literal(1f))
        else RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        RcBackgroundModifier(
          flags = 0,
          colorId = 0,
          reserved1 = 0,
          reserved2 = 0,
          red = RcFloatWord.literal(red),
          green = RcFloatWord.literal(1f - red),
          blue = RcFloatWord.literal(0f),
          alpha = RcFloatWord.literal(1f),
          shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
        ),
        RcLayoutContent(id + 1),
        end,
        end,
      )
    val container =
      if (row) RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(10f))
      else RcColumnLayout(3, -1, 1, 4, RcFloatWord.literal(10f))
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 200, legacyHeight = 120, modern = false),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        container,
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcLayoutContent(4),
      ) +
        box(10, 1f, weightedBox = false) +
        box(20, 0f, weightedBox = weighted) +
        listOf(end, end, end, end),
    )
  }

  private fun render(prepared: RcPreparedDocument, flatten: Boolean): IntArray? {
    val width = prepared.document.header.width
    val height = prepared.document.header.height
    val scene =
      ImageComposeScene(width = width, height = height, density = Density(DENSITY)) {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides flatten,
        ) {
          RcComposePlayer(prepared, systemColors = { null })
        }
      }
    return try {
      val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
      if (!scene.render(0L).readPixels(bitmap)) return null
      IntArray(width * height) { bitmap.getColor(it % width, it / width) }
    } catch (_: Exception) {
      null
    } finally {
      scene.close()
    }
  }

  private companion object {
    const val DENSITY = 2f
    const val SUBHEADER = "listsubheader__ideal__centred__compact.rc"
    const val VISIBLE = 42
    const val TEXT = 43
    const val FRAME_NANOS = 16_000_000L
  }
}

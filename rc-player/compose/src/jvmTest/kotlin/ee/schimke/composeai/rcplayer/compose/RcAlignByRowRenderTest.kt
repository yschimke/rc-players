package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcAlignByModifier
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

/**
 * Pins how a Row whose children carry `alignBy` lays out now that it is a Compose `Row` with
 * `Modifier.alignBy { }` rather than a hand-rolled layout: AndroidX's RowLayout semantics (every
 * child aligned to the largest anchor, unanchored children at 0, the aligned group offset by the
 * row's vertical positioning) plus the row's arrangement and spacing.
 */
class RcAlignByRowRenderTest {
  @Test
  fun literalAnchorsLineUpUnanchoredChildrenAtTheLargestAnchor() {
    val pixels =
      render(
        rowWidth = 90f,
        rowHeight = 60f,
        verticalPositioning = TOP,
        children =
          box(5, RED, 20f, 20f, alignBy = RcFloatWord.literal(5f)) +
            box(6, GREEN, 20f, 20f, alignBy = RcFloatWord.literal(15f)) +
            box(7, BLUE, 20f, 20f, alignBy = null),
      )

    assertEquals(10, pixels.topOf(RED), "anchor 5 sits 10 px below the largest anchor, 15")
    assertEquals(0, pixels.topOf(GREEN), "the largest anchor is placed at the row's top")
    assertEquals(15, pixels.topOf(BLUE), "an unanchored child aligns its top edge to the anchor")
    assertEquals(0, pixels.leftOf(RED))
    assertEquals(20, pixels.leftOf(GREEN))
    assertEquals(40, pixels.leftOf(BLUE))
  }

  @Test
  fun centeredRowOffsetsTheAlignedGroupByItsVerticalPositioning() {
    // Neither box has text, so both baselines resolve to the top edge, and AndroidX centres the
    // aligned group in the row: (80 - 40) / 2 = 20.
    val pixels =
      render(
        rowWidth = 80f,
        rowHeight = 80f,
        verticalPositioning = CENTER,
        children =
          box(5, RED, 20f, 20f, alignBy = FIRST_BASELINE) +
            box(6, GREEN, 20f, 40f, alignBy = FIRST_BASELINE),
      )

    assertEquals(20, pixels.topOf(RED))
    assertEquals(20, pixels.topOf(GREEN))
  }

  @Test
  fun bottomRowPlacesTheAlignedGroupAtTheBottom() {
    val pixels =
      render(
        rowWidth = 80f,
        rowHeight = 80f,
        verticalPositioning = BOTTOM,
        children =
          box(5, RED, 20f, 20f, alignBy = LAST_BASELINE) +
            box(6, GREEN, 20f, 40f, alignBy = LAST_BASELINE),
      )

    assertEquals(40, pixels.topOf(RED))
    assertEquals(40, pixels.topOf(GREEN))
  }

  @Test
  fun alignedRowKeepsItsArrangementAndSpacing() {
    val pixels =
      render(
        rowWidth = 100f,
        rowHeight = 40f,
        horizontalPositioning = CENTER,
        verticalPositioning = TOP,
        spacing = 10f,
        children =
          box(5, RED, 20f, 20f, alignBy = RcFloatWord.literal(0f)) +
            box(6, GREEN, 20f, 20f, alignBy = RcFloatWord.literal(10f)),
      )

    // 20 + 10 + 20 = 50 px of content centred in 100 px.
    assertEquals(25, pixels.leftOf(RED))
    assertEquals(55, pixels.leftOf(GREEN))
    assertEquals(10, pixels.topOf(RED))
    assertEquals(0, pixels.topOf(GREEN))
  }

  @Test
  fun weightedChildInAnAlignedRowTakesTheRemainingWidth() {
    val pixels =
      render(
        rowWidth = 100f,
        rowHeight = 40f,
        verticalPositioning = TOP,
        children =
          box(5, RED, 20f, 20f, alignBy = RcFloatWord.literal(10f)) +
            box(
              6,
              GREEN,
              width = null,
              height = 20f,
              alignBy = RcFloatWord.literal(0f),
              weight = 1f,
            ),
      )

    assertEquals(20, pixels.leftOf(GREEN))
    assertEquals(99, pixels.rightOf(GREEN))
    assertEquals(0, pixels.topOf(RED))
    assertEquals(10, pixels.topOf(GREEN))
  }

  @Test
  fun centeredRowOffsetsDifferingAnchorsByTheTallestChild() {
    // AndroidX: base = (60 - 20) / 2 = 20, then each child sits at base + maxAnchor - anchor.
    val pixels =
      render(
        rowWidth = 60f,
        rowHeight = 60f,
        verticalPositioning = CENTER,
        children =
          box(5, RED, 20f, 20f, alignBy = RcFloatWord.literal(5f)) +
            box(6, GREEN, 20f, 20f, alignBy = RcFloatWord.literal(15f)),
      )

    assertEquals(30, pixels.topOf(RED))
    assertEquals(20, pixels.topOf(GREEN))
  }

  @Test
  fun bottomRowOffsetsDifferingAnchorsByTheTallestChild() {
    // AndroidX: base = 60 - 20 = 40; the lower child overflows the row's bottom edge.
    val pixels =
      render(
        rowWidth = 60f,
        rowHeight = 60f,
        verticalPositioning = BOTTOM,
        children =
          box(5, RED, 20f, 10f, alignBy = RcFloatWord.literal(5f)) +
            box(6, GREEN, 20f, 20f, alignBy = RcFloatWord.literal(15f)),
      )

    assertEquals(50, pixels.topOf(RED))
    assertEquals(40, pixels.topOf(GREEN))
  }

  @Test
  fun fractionalAnchorsRoundTheirSharedDeltaNotEachAnchor() {
    // AndroidX rounds `maxAnchor - anchor`: 0.6 - 0.4 = 0.2 rounds to 0, so both tops are 0.
    val pixels =
      render(
        rowWidth = 60f,
        rowHeight = 40f,
        verticalPositioning = TOP,
        children =
          box(5, RED, 20f, 20f, alignBy = RcFloatWord.literal(0.4f)) +
            box(6, GREEN, 20f, 20f, alignBy = RcFloatWord.literal(0.6f)),
      )

    assertEquals(0, pixels.topOf(RED))
    assertEquals(0, pixels.topOf(GREEN))
  }

  private class Pixels(val bitmap: Bitmap, val width: Int, val height: Int) {
    private fun points(color: Int) =
      (0 until height).flatMap { y ->
        (0 until width).filter { x -> bitmap.getColor(x, y) == color }.map { x -> x to y }
      }

    fun topOf(color: Int): Int = points(color).minOf { it.second }

    fun leftOf(color: Int): Int = points(color).minOf { it.first }

    fun rightOf(color: Int): Int = points(color).maxOf { it.first }
  }

  private fun render(
    rowWidth: Float,
    rowHeight: Float,
    horizontalPositioning: Int = START,
    verticalPositioning: Int,
    spacing: Float = 0f,
    children: List<RcOperation>,
  ): Pixels {
    val width = rowWidth.toInt()
    val height = rowHeight.toInt()
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = width, legacyHeight = height, modern = false),
        listOf(
          RcRootLayout(1),
          RcLayoutContent(2),
          RcRowLayout(
            3,
            30,
            horizontalPositioning,
            verticalPositioning,
            RcFloatWord.literal(spacing),
          ),
          exactWidth(rowWidth),
          exactHeight(rowHeight),
          RcLayoutContent(4),
        ) + children + List(4) { RcNoArg(RcOpcodes.CONTAINER_END) },
      )
    val scene =
      ImageComposeScene(width = width, height = height, density = Density(1f)) {
        RcComposePlayer(document)
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
      check(scene.render().readPixels(bitmap))
      return Pixels(bitmap, width, height)
    } finally {
      scene.close()
    }
  }

  private fun box(
    componentId: Int,
    color: Int,
    width: Float?,
    height: Float,
    alignBy: RcFloatWord?,
    weight: Float? = null,
  ): List<RcOperation> = buildList {
    add(RcBoxLayout(componentId, componentId * 10, 1, 4))
    if (weight != null) {
      add(RcWidthModifier(RcDimensionType.WEIGHT, RcFloatWord.literal(weight)))
    } else if (width != null) {
      add(exactWidth(width))
    }
    add(exactHeight(height))
    if (alignBy != null) add(RcAlignByModifier(alignBy, 0))
    add(background(color))
    add(RcLayoutContent(componentId * 10 + 1))
    add(RcNoArg(RcOpcodes.CONTAINER_END))
    add(RcNoArg(RcOpcodes.CONTAINER_END))
  }

  private fun background(color: Int) =
    RcBackgroundModifier(
      flags = 0,
      colorId = 0,
      reserved1 = 0,
      reserved2 = 0,
      red = RcFloatWord.literal(((color shr 16) and 0xff) / 255f),
      green = RcFloatWord.literal(((color shr 8) and 0xff) / 255f),
      blue = RcFloatWord.literal((color and 0xff) / 255f),
      alpha = RcFloatWord.literal(1f),
      shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
    )

  private fun exactWidth(value: Float) =
    RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(value))

  private fun exactHeight(value: Float) =
    RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(value))

  private companion object {
    const val START = 1
    const val CENTER = 2
    const val TOP = 4
    const val BOTTOM = 5
    const val RED = 0xffff0000.toInt()
    const val GREEN = 0xff00ff00.toInt()
    const val BLUE = 0xff0000ff.toInt()
    val FIRST_BASELINE = RcFloatWord(0x7fc00000 or RcAlignByModifier.FIRST_BASELINE_ID)
    val LAST_BASELINE = RcFloatWord(0x7fc00000 or RcAlignByModifier.LAST_BASELINE_ID)
  }
}

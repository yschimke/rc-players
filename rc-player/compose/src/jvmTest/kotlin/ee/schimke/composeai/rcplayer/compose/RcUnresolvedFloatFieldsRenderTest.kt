package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
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
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * A float field whose variable does not resolve to a number is read as unset, not handed on as NaN.
 *
 * Any float field of a document may be a NaN-encoded variable id, and a variable can have no number
 * behind it: an id nothing has written yet, or an expression that divides zero by zero. AndroidX's
 * embedded player swept its float fields for this in androidx-main `300a543b2`; the ones that took
 * this player down, or drew nothing, are pinned here:
 * * a row's, column's or collapsible layout's child **weight** reached `Modifier.weight` as NaN,
 *   which throws; the child is left unweighted and sized by its content;
 * * a `TextLayout`'s or `CoreText`'s **font weight** was rounded, and rounding NaN throws; it keeps
 *   the normal weight;
 * * a `CoreText`'s **letter spacing** and **line height** multiplier and addition went to text
 *   layout as NaN; they are read as none.
 */
class RcUnresolvedFloatFieldsRenderTest {

  @Test
  fun aRowChildWithAnUnresolvedWeightIsSizedByItsContent() {
    val bitmap = render(weightedDocument(Layout.ROW), 100, 20)
    assertEquals(RED, bitmap.getColor(5, 10), "the fixed child")
    assertEquals(GREEN, bitmap.getColor(35, 10), "the unresolved weight wraps its 10px content")
    assertEquals(BLUE, bitmap.getColor(45, 10), "the weighted child starts right after it")
    assertEquals(BLUE, bitmap.getColor(95, 10), "and takes the rest of the row")
  }

  @Test
  fun aColumnChildWithAnUnresolvedWeightIsSizedByItsContent() {
    val bitmap = render(weightedDocument(Layout.COLUMN), 20, 100)
    assertEquals(RED, bitmap.getColor(10, 5), "the fixed child")
    assertEquals(GREEN, bitmap.getColor(10, 35), "the unresolved weight wraps its 10px content")
    assertEquals(BLUE, bitmap.getColor(10, 45), "the weighted child starts right after it")
    assertEquals(BLUE, bitmap.getColor(10, 95), "and takes the rest of the column")
  }

  @Test
  fun aCollapsibleChildWithAnUnresolvedWeightIsSizedByItsContent() {
    val bitmap = render(weightedDocument(Layout.COLLAPSIBLE_ROW), 100, 20)
    assertEquals(RED, bitmap.getColor(5, 10), "the fixed child")
    assertEquals(GREEN, bitmap.getColor(35, 10), "the unresolved weight wraps its 10px content")
    assertEquals(BLUE, bitmap.getColor(45, 10), "the weighted child starts right after it")
    assertEquals(BLUE, bitmap.getColor(95, 10), "and takes the rest of the row")
  }

  @Test
  fun aTextLayoutWithAnUnresolvedFontWeightDrawsAtTheNormalWeight() {
    val unresolved = render(textLayoutDocument(UNRESOLVED), TEXT_WIDTH, TEXT_HEIGHT)
    val normal = render(textLayoutDocument(RcFloatWord.literal(400f)), TEXT_WIDTH, TEXT_HEIGHT)
    assertTrue(normal.ink() > 0, "the text drew nothing")
    assertContentEquals(normal.pixels(), unresolved.pixels())
  }

  @Test
  fun aCoreTextWithAnUnresolvedFontWeightDrawsAtTheNormalWeight() {
    val unresolved = render(coreTextDocument(FONT_WEIGHT to UNRESOLVED), TEXT_WIDTH, TEXT_HEIGHT)
    val normal =
      render(coreTextDocument(FONT_WEIGHT to RcFloatWord.literal(400f)), TEXT_WIDTH, TEXT_HEIGHT)
    assertTrue(normal.ink() > 0, "the text drew nothing")
    assertContentEquals(normal.pixels(), unresolved.pixels())
  }

  @Test
  fun aCoreTextWithUnresolvedSpacingAndLineHeightDrawsAsIfTheyWereUnset() {
    val unset = render(coreTextDocument(), TEXT_WIDTH, TEXT_HEIGHT)
    assertTrue(unset.ink() > 0, "the text drew nothing")
    for (property in listOf(LETTER_SPACING, LINE_HEIGHT_ADD, LINE_HEIGHT_MULTIPLIER)) {
      val unresolved = render(coreTextDocument(property to UNRESOLVED), TEXT_WIDTH, TEXT_HEIGHT)
      assertContentEquals(unset.pixels(), unresolved.pixels(), "text style property $property")
    }
  }

  private enum class Layout {
    ROW,
    COLUMN,
    COLLAPSIBLE_ROW,
  }

  /**
   * A 100px run of three children along the main axis: 30px of red, a green child whose weight is
   * [UNRESOLVED] around a 10px box, and a blue child of weight 1.
   */
  private fun weightedDocument(layout: Layout): RcDocument {
    val vertical = layout == Layout.COLUMN
    fun main(type: Int, value: RcFloatWord): RcOperation =
      if (vertical) RcHeightModifier(type, value) else RcWidthModifier(type, value)
    fun cross(value: Float): RcOperation =
      if (vertical) RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(value))
      else RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(value))
    fun child(id: Int, mainSize: RcOperation, color: Int, inner: Boolean): List<RcOperation> =
      buildList {
        add(RcBoxLayout(id, id * 10, 1, 1))
        add(mainSize)
        add(cross(20f))
        add(background(color))
        add(RcLayoutContent(id * 10 + 1))
        if (inner) {
          add(RcBoxLayout(id * 10 + 2, id * 10 + 3, 1, 1))
          add(main(RcDimensionType.EXACT, RcFloatWord.literal(10f)))
          add(cross(20f))
          add(END)
        }
        add(END)
        add(END)
      }
    val container: RcOperation =
      when (layout) {
        Layout.ROW -> RcRowLayout(3, 30, 1, 1, RcFloatWord.literal(0f))
        Layout.COLUMN -> RcColumnLayout(3, 30, 1, 1, RcFloatWord.literal(0f))
        Layout.COLLAPSIBLE_ROW -> RcCollapsibleRowLayout(3, 30, 1, 1, RcFloatWord.literal(0f))
      }
    val width = if (vertical) 20 else 100
    val height = if (vertical) 100 else 20
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = width, legacyHeight = height, modern = false),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        container,
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(width.toFloat())),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(height.toFloat())),
        RcLayoutContent(4),
      ) +
        child(5, main(RcDimensionType.EXACT, RcFloatWord.literal(30f)), RED, inner = false) +
        child(6, main(RcDimensionType.WEIGHT, UNRESOLVED), GREEN, inner = true) +
        child(7, main(RcDimensionType.WEIGHT, RcFloatWord.literal(1f)), BLUE, inner = false) +
        List(4) { END },
    )
  }

  private fun textLayoutDocument(fontWeight: RcFloatWord): RcDocument =
    textDocument(
      RcTextLayout(
        componentId = -6,
        animationId = -1,
        textId = 42,
        color = INK,
        fontSize = RcFloatWord.literal(FONT_SIZE),
        fontStyle = 0,
        fontWeight = fontWeight,
        fontFamilyId = -1,
        textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
        overflow = RcTextLayout.OVERFLOW_CLIP,
        maxLines = Int.MAX_VALUE,
      ),
      RcLayoutContent(-7),
      RcNoArg(RcOpcodes.CONTAINER_END),
      RcNoArg(RcOpcodes.CONTAINER_END),
    )

  private fun coreTextDocument(vararg floats: Pair<Int, RcFloatWord>): RcDocument =
    textDocument(
      RcCoreText(
        textId = 42,
        properties =
          listOf(
            RcTextStyleProperty.IntValue(3, INK),
            RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(FONT_SIZE)),
          ) + floats.map { (index, value) -> RcTextStyleProperty.FloatValue(index, value) },
      ),
      RcLayoutContent(-5),
      RcNoArg(RcOpcodes.CONTAINER_END),
      RcNoArg(RcOpcodes.CONTAINER_END),
    )

  private fun textDocument(vararg text: RcOperation): RcDocument =
    RcDocument(
      RcHeader(
        RcVersion(1, 0, 0),
        legacyWidth = TEXT_WIDTH,
        legacyHeight = TEXT_HEIGHT,
        modern = false,
      ),
      listOf(
        RcRootLayout(-2),
        RcBoxLayout(-3, -1, 1, 1),
        RcLayoutContent(-4),
        RcTextData(42, "Remote Compose\nplayers"),
      ) + text + List(3) { END },
    )

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

  private fun render(document: RcDocument, width: Int, height: Int): Bitmap {
    val scene =
      ImageComposeScene(width = width, height = height, density = Density(1f)) {
        RcComposePlayer(document)
      }
    try {
      return Bitmap().apply {
        allocN32Pixels(width, height)
        check(scene.render(0L).readPixels(this))
      }
    } finally {
      scene.close()
    }
  }

  private fun Bitmap.pixels(): IntArray =
    IntArray(width * height) { getColor(it % width, it / width) }

  private fun Bitmap.ink(): Int = pixels().count { it != 0 }

  private companion object {
    val END = RcNoArg(RcOpcodes.CONTAINER_END)

    /** A float variable id no operation of the document defines, NaN-encoded as the wire does. */
    val UNRESOLVED = RcFloatWord(0x7fc00000 or 900)

    const val RED = 0xffff0000.toInt()
    const val GREEN = 0xff00ff00.toInt()
    const val BLUE = 0xff0000ff.toInt()
    const val INK = 0xff101828.toInt()
    const val FONT_SIZE = 24f
    const val TEXT_WIDTH = 240
    const val TEXT_HEIGHT = 80

    /** `CoreText` style properties. */
    const val FONT_WEIGHT = 7
    const val LETTER_SPACING = 12
    const val LINE_HEIGHT_ADD = 13
    const val LINE_HEIGHT_MULTIPLIER = 14
  }
}

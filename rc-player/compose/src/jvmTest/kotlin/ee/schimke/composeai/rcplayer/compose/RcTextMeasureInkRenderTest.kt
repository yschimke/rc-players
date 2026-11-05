package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawText
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * `TextMeasure` reports a run's ink, as the View player's `Paint.getTextBounds` does and the
 * embedded player does since androidx `697c967d0d9`, unless `TEXT_MEASURE_FONT_HEIGHT` asks for the
 * font's ascent and descent. A box drawn from the measured bounds hugs the glyphs; before, the CMP
 * player reported each character's line box, so the box spanned the whole line.
 */
class RcTextMeasureInkRenderTest {

  @Test
  fun aBoxDrawnFromTextMeasureHugsTheInk() {
    val text = bounds(render(document(flags = 0, box = false)), ::isText)
    val box = bounds(render(document(flags = 0, box = true)), ::isBox)
    assertTrue(text != null && box != null, "nothing drawn: text $text, box $box")
    // The box's stroke straddles the measured edges, so allow its width plus a pixel of rounding.
    for ((name, measured, ink) in
      listOf(
        Triple("left", box.left, text.left),
        Triple("top", box.top, text.top),
        Triple("right", box.right, text.right),
        Triple("bottom", box.bottom, text.bottom),
      )) {
      assertTrue(abs(measured - ink) <= STROKE + 1, "$name: box $box, ink $text")
    }
  }

  @Test
  fun fontHeightSpansMoreThanTheInk() {
    val ink = bounds(render(document(flags = 0, box = true)), ::isBox)!!
    val font = bounds(render(document(flags = TEXT_MEASURE_FONT_HEIGHT, box = true)), ::isBox)!!
    assertTrue(font.top < ink.top - 5 && font.bottom > ink.bottom, "font $font, ink $ink")
  }

  /** Writes PR evidence when explicitly requested; ordinary test runs remain side-effect free. */
  @Test
  fun writeTextMeasureInkEvidence() {
    val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    for ((name, flags) in listOf("ink" to 0, "font-height" to TEXT_MEASURE_FONT_HEIGHT)) {
      val scene =
        ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
          RcComposePlayer(document(flags, box = true))
        }
      try {
        directory
          .resolve("text-measure-$name.png")
          .writeBytes(scene.render(0L).encodeToData()!!.bytes)
      } finally {
        scene.close()
      }
    }
  }

  private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

  private fun isText(color: Int) = color ushr 24 != 0

  private fun isBox(color: Int) =
    (color ushr 24) > 0x80 && (color shr 16 and 0xff) > 0xa0 && (color and 0xff) < 0x60

  private fun bounds(bitmap: Bitmap, match: (Int) -> Boolean): Box? {
    var left = WIDTH
    var top = HEIGHT
    var right = -1
    var bottom = -1
    for (y in 0 until HEIGHT) {
      for (x in 0 until WIDTH) {
        if (match(bitmap.getColor(x, y))) {
          left = minOf(left, x)
          right = maxOf(right, x)
          top = minOf(top, y)
          bottom = maxOf(bottom, y)
        }
      }
    }
    return if (right < 0) null else Box(left, top, right + 1, bottom + 1)
  }

  private fun render(document: RcDocument): Bitmap {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(document)
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      check(scene.render(0L).readPixels(bitmap))
      return bitmap
    } finally {
      scene.close()
    }
  }

  /**
   * `xg` at 80 px on a baseline at ([X], [Y]); with [box], `TextMeasure` writes the run's left,
   * top, right and bottom and a red stroke is drawn around those bounds, offset to the baseline
   * origin.
   */
  private fun document(flags: Int, box: Boolean): RcDocument {
    val measure: List<RcOperation> =
      if (!box) emptyList()
      else
        listOf(
          RcTextMeasure(LEFT, TEXT, (flags shl 8) or 2),
          RcTextMeasure(TOP, TEXT, (flags shl 8) or 4),
          RcTextMeasure(RIGHT, TEXT, (flags shl 8) or 3),
          RcTextMeasure(BOTTOM, TEXT, (flags shl 8) or 5),
          RcFloatExpression(BOX_LEFT, listOf(literal(X), ref(LEFT), ADD), null),
          RcFloatExpression(BOX_TOP, listOf(literal(Y), ref(TOP), ADD), null),
          RcFloatExpression(BOX_RIGHT, listOf(literal(X), ref(RIGHT), ADD), null),
          RcFloatExpression(BOX_BOTTOM, listOf(literal(Y), ref(BOTTOM), ADD), null),
          RcPaintData(
            listOf(
              PAINT_COLOR,
              0xffe53935.toInt(),
              PAINT_STYLE or (STYLE_STROKE shl 16),
              PAINT_STROKE_WIDTH,
              literal(STROKE.toFloat()).bits,
            )
          ),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            ref(BOX_LEFT),
            ref(BOX_TOP),
            ref(BOX_RIGHT),
            ref(BOX_BOTTOM),
          ),
        )
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      listOf(
        RcTextData(TEXT, "xg"),
        RcPaintData(
          listOf(
            PAINT_COLOR,
            0xff101828.toInt(),
            PAINT_STYLE or (STYLE_FILL shl 16),
            PAINT_TEXT_SIZE,
            literal(80f).bits,
          )
        ),
        RcDrawText(TEXT, 0, 2, 0, 2, literal(X), literal(Y), false),
      ) + measure,
    )
  }

  private fun literal(value: Float) = RcFloatWord.literal(value)

  private fun ref(id: Int) = RcFloatWord(0x7fc00000 or id)

  private companion object {
    const val WIDTH = 240
    const val HEIGHT = 200
    const val X = 60f
    const val Y = 120f
    const val STROKE = 2

    const val TEXT = 42
    const val LEFT = 50
    const val TOP = 51
    const val RIGHT = 52
    const val BOTTOM = 53
    const val BOX_LEFT = 54
    const val BOX_TOP = 55
    const val BOX_RIGHT = 56
    const val BOX_BOTTOM = 57

    // `PaintBundle` keys and values.
    const val PAINT_TEXT_SIZE = 1
    const val PAINT_COLOR = 4
    const val PAINT_STROKE_WIDTH = 5
    const val PAINT_STYLE = 8
    const val STYLE_FILL = 0
    const val STYLE_STROKE = 1

    val ADD = RcFloatWord(0xffb10001.toInt())
  }
}

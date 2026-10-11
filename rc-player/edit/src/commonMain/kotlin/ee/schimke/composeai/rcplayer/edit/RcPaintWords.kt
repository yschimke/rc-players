package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcPaintData

/**
 * One command of a `PaintData` bundle: its tag (`PaintBundle.COLOR`, `TEXTURE`, …), the value
 * packed in the command word's high half, and where its words sit in [RcPaintData.words] — the
 * command word at [start], its operands up to [end] exclusive.
 */
internal class RcPaintCommand(val tag: Int, val packed: Int, val start: Int, val end: Int)

/**
 * [RcPaintData.words] split into commands, the way AndroidX `PaintBundle` walks them, or null for a
 * bundle with a command this does not know the length of — a path effect among them — since a
 * walker that guesses a length misreads every command after it.
 */
internal fun RcPaintData.commands(): List<RcPaintCommand>? {
  val result = mutableListOf<RcPaintCommand>()
  var index = 0
  while (index < words.size) {
    val start = index
    val command = words[index++]
    val tag = command and 0xffff
    val packed = command ushr 16
    val operands =
      when (tag) {
        PAINT_TEXT_SIZE,
        PAINT_COLOR,
        PAINT_STROKE_WIDTH,
        PAINT_STROKE_MITER,
        PAINT_SHADER,
        PAINT_ALPHA,
        PAINT_COLOR_FILTER,
        PAINT_TYPEFACE,
        PAINT_COLOR_ID,
        PAINT_COLOR_FILTER_ID,
        PAINT_SHADER_MATRIX,
        PAINT_FALLBACK_TYPEFACE -> 1
        PAINT_STROKE_CAP,
        PAINT_STYLE,
        PAINT_IMAGE_FILTER_QUALITY,
        PAINT_ANTI_ALIAS,
        PAINT_STROKE_JOIN,
        PAINT_FILTER_BITMAP,
        PAINT_BLEND_MODE,
        PAINT_CLEAR_COLOR_FILTER -> 0
        PAINT_FONT_AXIS -> packed * 2
        PAINT_TEXTURE -> 3
        PAINT_GRADIENT -> gradientLength(index) ?: return null
        else -> return null
      }
    index += operands
    if (index > words.size) return null
    result += RcPaintCommand(tag, packed, start, index)
  }
  return result
}

/** `PaintBundle.callSetGradient`'s layout: colours, stops, then the type's own fields. */
private fun RcPaintData.gradientLength(start: Int): Int? {
  val gradientType = words[start - 1] ushr 16
  var index = start
  val colors = words.getOrNull(index++)?.and(0xff) ?: return null
  index += colors
  val stops = words.getOrNull(index++) ?: return null
  if (colors == 0) return index - start
  if (stops > 0) index += colors
  index +=
    when (gradientType) {
      GRADIENT_LINEAR -> 5
      GRADIENT_RADIAL -> 4
      GRADIENT_SWEEP -> 2
      else -> 0
    }
  return index - start
}

internal const val PAINT_TEXT_SIZE = 1
internal const val PAINT_COLOR = 4
internal const val PAINT_STROKE_WIDTH = 5
internal const val PAINT_STROKE_MITER = 6
internal const val PAINT_STROKE_CAP = 7
internal const val PAINT_STYLE = 8
internal const val PAINT_SHADER = 9
internal const val PAINT_IMAGE_FILTER_QUALITY = 10
internal const val PAINT_GRADIENT = 11
internal const val PAINT_ALPHA = 12
internal const val PAINT_COLOR_FILTER = 13
internal const val PAINT_ANTI_ALIAS = 14
internal const val PAINT_STROKE_JOIN = 15
internal const val PAINT_TYPEFACE = 16
internal const val PAINT_FILTER_BITMAP = 17
internal const val PAINT_BLEND_MODE = 18
internal const val PAINT_COLOR_ID = 19
internal const val PAINT_COLOR_FILTER_ID = 20
internal const val PAINT_CLEAR_COLOR_FILTER = 21
internal const val PAINT_SHADER_MATRIX = 22
internal const val PAINT_FONT_AXIS = 23
internal const val PAINT_TEXTURE = 24
internal const val PAINT_PATH_EFFECT = 25
internal const val PAINT_FALLBACK_TYPEFACE = 26

/** `PaintBundle.STYLE`'s packed value for a filled shape. */
internal const val PAINT_STYLE_FILL = 0

private const val GRADIENT_LINEAR = 0
private const val GRADIENT_RADIAL = 1
private const val GRADIENT_SWEEP = 2

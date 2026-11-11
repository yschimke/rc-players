package ee.schimke.composeai.rccreation.common

/**
 * `RcPaint`, the paint builder AndroidX's `RemoteComposeWriter.getRcPaint` returns, with the
 * setters the carried Kotlin calls. Each sets a value in a [PaintBundle]; [commit] writes the
 * bundle as one paint operation and empties it.
 */
public class RcPaint(private val writer: RemoteWriter) {
  private val paint = PaintBundle()

  /** Writes the values set since the last commit, as `RcPaint.commit`. */
  public fun commit() {
    writer.applyPaint(paint)
    paint.reset()
  }

  public fun setColor(color: Int): RcPaint = apply { paint.setColor(color) }

  /** [style] is one of [PaintBundle.STYLE_FILL], `STYLE_STROKE`, `STYLE_FILL_AND_STROKE`. */
  public fun setStyle(style: Int): RcPaint = apply { paint.setStyle(style) }

  public fun setStrokeWidth(width: Float): RcPaint = apply { paint.setStrokeWidth(width) }

  public fun setStrokeCap(cap: Int): RcPaint = apply { paint.setStrokeCap(cap) }

  public fun setStrokeJoin(join: Int): RcPaint = apply { paint.setStrokeJoin(join) }

  public fun setTextSize(size: Float): RcPaint = apply { paint.setTextSize(size) }

  /** The typeface [fontType] (a `PaintBundle.FONT_TYPE_*` or a text id) at [weight]. */
  public fun setTypeface(fontType: Int, weight: Int, italic: Boolean): RcPaint = apply {
    paint.setTextStyle(fontType, weight, italic)
  }
}

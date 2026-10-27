package ee.schimke.composeai.rcwidget.launcher

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.util.Xml
import ee.schimke.composeai.rcwidget.creation.RcWidgetCaptureSize
import kotlin.math.roundToInt
import org.xmlpull.v1.XmlPullParser

/** Launcher widget sizes: the slot a widget has, and the cell grid a provider resizes on. */
public object RcWidgetSizing {
  /** One launcher cell on a typical phone, in dp. A widget slot is a whole number of them. */
  public const val CELL_WIDTH_DP: Int = 72
  public const val CELL_HEIGHT_DP: Int = 84

  /**
   * The slot the user dragged widget [widgetId] out to, so a capture fills it rather than wrapping
   * its content: the portrait pair the launcher reports (`MIN_WIDTH × MAX_HEIGHT`). A launcher
   * reports landscape as `MAX_WIDTH × MIN_HEIGHT`; a width breakpoint still reflows against the live
   * canvas. Missing options read as one cell.
   */
  public fun slotOf(appWidgetManager: AppWidgetManager, widgetId: Int): RcWidgetCaptureSize =
    slotOf(appWidgetManager.getAppWidgetOptions(widgetId))

  /** [slotOf] from a widget's options bundle (`onAppWidgetOptionsChanged`'s `newOptions`). */
  public fun slotOf(options: Bundle): RcWidgetCaptureSize =
    RcWidgetCaptureSize(
      options.dp(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, CELL_WIDTH_DP),
      options.dp(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, CELL_HEIGHT_DP),
    )

  /** [cellsW] × [cellsH] cells, in dp. */
  public fun cells(cellsW: Int, cellsH: Int): RcWidgetCaptureSize =
    RcWidgetCaptureSize(cellsW * CELL_WIDTH_DP, cellsH * CELL_HEIGHT_DP)

  private fun Bundle.dp(key: String, fallback: Int): Int =
    getInt(key, fallback).takeIf { it > 0 } ?: fallback
}

/**
 * A provider's resize range on the launcher's cell grid, in whole cells: its minimum, its default
 * (`targetCell*`) and the largest a preview shows. Without an advertised maximum the launcher owns
 * the upper limit, and [maxCellsW] / [maxCellsH] are a representative 4 × 4.
 */
public data class RcLauncherGridBounds(
  val minCellsW: Int,
  val minCellsH: Int,
  val defaultCellsW: Int,
  val defaultCellsH: Int,
  val maxCellsW: Int,
  val maxCellsH: Int,
) {
  public companion object {
    /**
     * The `appwidget-provider` resize range in [xmlRes] (the provider's `android:resource`),
     * quantised onto the cell grid: `targetCell*` is already cells; `min/maxResize*` are dimensions,
     * rounded to the nearest whole cell (at least one). The default is clamped into the range.
     */
    public fun fromProviderXml(context: Context, xmlRes: Int): RcLauncherGridBounds {
      val parser = context.resources.getXml(xmlRes)
      var event = parser.eventType
      while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
        event = parser.next()
      }
      val attrs = Xml.asAttributeSet(parser)
      val density = context.resources.displayMetrics.density
      fun dimPx(attr: Int): Int {
        val a = context.obtainStyledAttributes(attrs, intArrayOf(attr))
        return try {
          a.getDimensionPixelSize(0, 0)
        } finally {
          a.recycle()
        }
      }
      fun intVal(attr: Int): Int {
        val a = context.obtainStyledAttributes(attrs, intArrayOf(attr))
        return try {
          a.getInt(0, 0)
        } finally {
          a.recycle()
        }
      }
      fun cellsW(px: Int) =
        ((px / density) / RcWidgetSizing.CELL_WIDTH_DP).roundToInt().coerceAtLeast(1)
      fun cellsH(px: Int) =
        ((px / density) / RcWidgetSizing.CELL_HEIGHT_DP).roundToInt().coerceAtLeast(1)
      // Every attribute is read while the parser is on the start tag; the set reads through it.
      val minW = cellsW(dimPx(android.R.attr.minResizeWidth))
      val minH = cellsH(dimPx(android.R.attr.minResizeHeight))
      val maxWidthPx = dimPx(android.R.attr.maxResizeWidth)
      val maxHeightPx = dimPx(android.R.attr.maxResizeHeight)
      val maxW = (if (maxWidthPx > 0) cellsW(maxWidthPx) else 4).coerceAtLeast(minW)
      val maxH = (if (maxHeightPx > 0) cellsH(maxHeightPx) else 4).coerceAtLeast(minH)
      val defW = intVal(android.R.attr.targetCellWidth).coerceIn(minW, maxW)
      val defH = intVal(android.R.attr.targetCellHeight).coerceIn(minH, maxH)
      parser.close()
      return RcLauncherGridBounds(minW, minH, defW, defH, maxW, maxH)
    }
  }
}

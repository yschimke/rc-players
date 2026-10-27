package ee.schimke.composeai.rcwidget.launcher

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ee.schimke.composeai.rcwidget.creation.RcWidgetCaptureSize
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * [views] inflated as the launcher would: `RemoteViews.apply` into a frame filling its parent. The
 * same native view a home screen shows, so an in-app preview is the widget, not an imitation.
 */
public fun inflateWidgetPreview(context: Context, views: RemoteViews): FrameLayout =
  FrameLayout(context).apply { addView(views.apply(context, this), FrameLayout.LayoutParams(-1, -1)) }

/**
 * The real launcher widget at [size]: [capture] makes its `RemoteViews` (off the main thread,
 * coalesced 100 ms after the last change), which are inflated and shown clipped to the launcher's
 * widget corner. The last good preview stays on screen while a new one is made, and through a
 * failed capture, which [onError] hears about. A widget's document is recaptured per size because
 * its breakpoints pick their layout at capture time, as the launcher does on every resize.
 */
@Composable
public fun RcLauncherWidgetPreview(
  size: RcWidgetCaptureSize,
  capture: suspend (Context, RcWidgetCaptureSize) -> RemoteViews?,
  modifier: Modifier = Modifier,
  /** What the widget shows (a design, its data): a change recaptures at the same size. */
  contentKey: Any? = null,
  contentDescription: String? = null,
  onError: (Throwable) -> Unit = {},
  placeholder: @Composable () -> Unit = {},
) {
  val context = LocalContext.current
  val cornerRadius =
    with(LocalDensity.current) {
      context.resources.getDimension(android.R.dimen.system_app_widget_background_radius).toDp()
    }
  var frame by remember { mutableStateOf<FrameLayout?>(null) }
  val latestCapture by rememberUpdatedState(capture)
  val latestOnError by rememberUpdatedState(onError)
  LaunchedEffect(size, contentKey) {
    delay(100)
    val views =
      try {
        withContext(Dispatchers.Default) { latestCapture(context, size) }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        latestOnError(failure)
        null
      } ?: return@LaunchedEffect
    // Inflation is UI work; hop back only when the capture resumed elsewhere.
    val ui =
      if (Looper.myLooper() == Looper.getMainLooper()) EmptyCoroutineContext else Dispatchers.Main
    withContext(ui) {
      try {
        frame = inflateWidgetPreview(context, views)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        latestOnError(failure)
      }
    }
  }
  Box(modifier.size(size.widthDp.dp, size.heightDp.dp), contentAlignment = Alignment.Center) {
    val current = frame
    if (current == null) placeholder()
    else
      Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(cornerRadius)).semantics {
          contentDescription?.let { this.contentDescription = it }
        }
      ) {
        AndroidView(
          factory = { ctx ->
            FrameLayout(ctx).apply {
              importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
          },
          update = { host ->
            if (host.getChildAt(0) !== current) {
              host.removeAllViews()
              host.addView(current, FrameLayout.LayoutParams(-1, -1))
            }
          },
          modifier = Modifier.fillMaxSize(),
        )
      }
  }
}

/**
 * The launcher's cell grid spanning [bounds]' largest slot, its smallest and largest sizes outlined,
 * and [content] at the current [cellsW] × [cellsH] slot with a drag handle at its bottom end corner
 * that resizes it a cell at a time ([onResize]). Accessibility actions make it wider, narrower,
 * taller and shorter. Put an [RcLauncherWidgetPreview] of [RcWidgetSizing.cells] inside to preview
 * a widget as it resizes on a home screen.
 */
@Composable
public fun RcLauncherGrid(
  bounds: RcLauncherGridBounds,
  cellsW: Int,
  cellsH: Int,
  onResize: (cellsW: Int, cellsH: Int) -> Unit,
  modifier: Modifier = Modifier,
  gridColor: Color = Color(0x33888888),
  smallestColor: Color = Color(0xFF7D5260),
  largestColor: Color = Color(0xFF625B71),
  currentColor: Color = Color(0xFF6750A4),
  handleSize: Dp = 24.dp,
  content: @Composable () -> Unit,
) {
  val density = LocalDensity.current
  val cellW = RcWidgetSizing.CELL_WIDTH_DP
  val cellH = RcWidgetSizing.CELL_HEIGHT_DP
  val cellWpx = with(density) { cellW.dp.toPx() }
  val cellHpx = with(density) { cellH.dp.toPx() }
  // Drag not yet spent on a cell step, in px; reset between gestures.
  var dragX by remember { mutableFloatStateOf(0f) }
  var dragY by remember { mutableFloatStateOf(0f) }
  val currentCells by rememberUpdatedState(cellsW to cellsH)
  val currentOnResize by rememberUpdatedState(onResize)
  val touch = handleSize * 2
  val currentWidth = (cellsW * cellW).dp
  val currentHeight = (cellsH * cellH).dp
  val scroll = rememberScrollState()
  LaunchedEffect(cellsW) {
    // A smaller size must not leave the widget off-screen after panning a wide grid.
    if (scroll.value > ((cellsW - 1) * cellWpx).toInt()) scroll.scrollTo(0)
  }
  Box(modifier.horizontalScroll(scroll)) {
    Box(
      Modifier.padding(end = touch / 2, bottom = touch / 2)
        .size((bounds.maxCellsW * cellW).dp, (bounds.maxCellsH * cellH).dp)
    ) {
      Canvas(Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        for (i in 0..bounds.maxCellsW) {
          val x = (i * cellWpx).coerceAtMost(size.width)
          drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        }
        for (j in 0..bounds.maxCellsH) {
          val y = (j * cellHpx).coerceAtMost(size.height)
          drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        drawRect(largestColor, style = Stroke(width = 2.dp.toPx(), pathEffect = dash))
        drawRect(
          smallestColor,
          size = Size(bounds.minCellsW * cellWpx, bounds.minCellsH * cellHpx),
          style = Stroke(width = 2.dp.toPx(), pathEffect = dash),
        )
      }
      Box(
        Modifier.align(Alignment.TopStart).size(currentWidth, currentHeight).border(2.dp, currentColor)
      ) {
        content()
      }
      Box(
        modifier =
          Modifier.align(Alignment.TopStart)
            .offset(x = currentWidth - touch / 2, y = currentHeight - touch / 2)
            .size(touch)
            .semantics {
              contentDescription = "Resize widget preview"
              stateDescription = "$cellsW by $cellsH cells"
              customActions = buildList {
                fun action(label: String, enabled: Boolean, w: Int, h: Int) {
                  if (enabled) add(CustomAccessibilityAction(label) { onResize(w, h).let { true } })
                }
                action("Make wider", cellsW < bounds.maxCellsW, cellsW + 1, cellsH)
                action("Make narrower", cellsW > bounds.minCellsW, cellsW - 1, cellsH)
                action("Make taller", cellsH < bounds.maxCellsH, cellsW, cellsH + 1)
                action("Make shorter", cellsH > bounds.minCellsH, cellsW, cellsH - 1)
              }
            }
            .pointerInput(bounds, cellWpx, cellHpx) {
              var gestureCells = currentCells
              fun reset() {
                dragX = 0f
                dragY = 0f
              }
              detectDragGestures(
                onDragStart = {
                  gestureCells = currentCells
                  reset()
                },
                onDragEnd = ::reset,
                onDragCancel = ::reset,
              ) { change, drag ->
                change.consume()
                dragX += drag.x
                dragY += drag.y
                var w = gestureCells.first
                var h = gestureCells.second
                while (dragX >= cellWpx && w < bounds.maxCellsW) {
                  w++
                  dragX -= cellWpx
                }
                while (dragX <= -cellWpx && w > bounds.minCellsW) {
                  w--
                  dragX += cellWpx
                }
                while (dragY >= cellHpx && h < bounds.maxCellsH) {
                  h++
                  dragY -= cellHpx
                }
                while (dragY <= -cellHpx && h > bounds.minCellsH) {
                  h--
                  dragY += cellHpx
                }
                // Don't bank drag past the clamp, or it must be unwound before the slot moves back.
                if (w == bounds.maxCellsW) dragX = dragX.coerceAtMost(0f)
                if (w == bounds.minCellsW) dragX = dragX.coerceAtLeast(0f)
                if (h == bounds.maxCellsH) dragY = dragY.coerceAtMost(0f)
                if (h == bounds.minCellsH) dragY = dragY.coerceAtLeast(0f)
                if (w != gestureCells.first || h != gestureCells.second) {
                  gestureCells = w to h
                  currentOnResize(w, h)
                }
              }
            },
        contentAlignment = Alignment.Center,
      ) {
        Box(Modifier.size(handleSize).clip(CircleShape).background(currentColor))
      }
    }
  }
}

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

/** Resource ceilings for document-scoped mutable bitmap render targets. */
internal data class RcOffscreenTargetLimits(
  val maxTargets: Int = 64,
  val maxPixels: Long = 16_777_216L,
) {
  init {
    require(maxTargets >= 0)
    require(maxPixels >= 0)
  }
}

/** Owns mutable offscreen images and canvases for one document's entire lifetime. */
internal class RcOffscreenTargetPool(
  private val limits: RcOffscreenTargetLimits = RcOffscreenTargetLimits()
) {
  private val targets = mutableMapOf<Int, Target>()
  private var allocatedPixels = 0L

  val allocationCount: Int
    get() = targets.size

  fun canvasFor(
    bitmapId: Int,
    source: ImageBitmap,
    images: MutableMap<Int, ImageBitmap>,
  ): Canvas {
    targets[bitmapId]?.let {
      return it.canvas
    }
    require(targets.size < limits.maxTargets) {
      "DrawToBitmap exceeds ${limits.maxTargets} mutable targets"
    }
    val pixels = source.width.toLong() * source.height.toLong()
    require(pixels <= limits.maxPixels - allocatedPixels) {
      "DrawToBitmap exceeds ${limits.maxPixels} aggregate mutable pixels"
    }
    val mutableImage = ImageBitmap(source.width, source.height)
    val canvas = GraphicsCanvas(mutableImage)
    canvas.drawImage(source, Offset.Zero, Paint())
    targets[bitmapId] = Target(mutableImage, canvas)
    allocatedPixels += pixels
    images[bitmapId] = mutableImage
    return canvas
  }

  /** Drops native canvas/image references when the owning document leaves composition. */
  fun dispose() {
    targets.clear()
    allocatedPixels = 0L
  }

  private data class Target(val image: ImageBitmap, val canvas: Canvas)
}

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData

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

/**
 * Owns mutable offscreen images and canvases for one document's entire lifetime.
 *
 * Two kinds of target live here. A declared bitmap (`BitmapData` with a fixed size) gets one
 * mutable copy on its first `DrawToBitmap`, reused for the rest of the document ([canvasFor]). A
 * component offscreen buffer (remote-core alpha22's `ENCODING_COMPONENT_OFFSCREEN_BUFFER`, named in
 * [componentBuffers]) has no size of its own: it is sized to a component every time it is drawn
 * into ([componentBuffer]), so its binding changes whenever that component's size does.
 *
 * Component buffers follow core's `BitmapData.OffscreenBitmapPool` scoping: a custom visibility
 * function runs inside a scope ([pushScope] / [popScope]), so a nested component animating with the
 * same function — and the same bitmap id — gets a buffer of its own while it runs, and the outer
 * binding comes back afterwards. A buffer a scope gave up is kept for reuse rather than freed.
 */
internal class RcOffscreenTargetPool(
  private val limits: RcOffscreenTargetLimits = RcOffscreenTargetLimits(),
  /** The document's component offscreen buffers, by bitmap id. */
  val componentBuffers: Map<Int, RcBitmapData> = emptyMap(),
) {
  private val targets = mutableMapOf<Int, Target>()
  private val bindings = mutableMapOf<Int, Binding>()
  private val spare = mutableListOf<Target>()
  private val scopes = ArrayList<Scope>()
  private var componentAllocations = 0
  private var allocatedPixels = 0L

  val allocationCount: Int
    get() = targets.size + componentAllocations

  fun canvasFor(
    bitmapId: Int,
    source: ImageBitmap,
    images: MutableMap<Int, ImageBitmap>,
  ): Canvas {
    targets[bitmapId]?.let {
      return it.canvas
    }
    reserve(source.width, source.height)
    val mutableImage = ImageBitmap(source.width, source.height)
    val canvas = GraphicsCanvas(mutableImage)
    canvas.drawImage(source, Offset.Zero, Paint())
    targets[bitmapId] = Target(mutableImage, canvas)
    allocatedPixels += source.width.toLong() * source.height.toLong()
    images[bitmapId] = mutableImage
    return canvas
  }

  /** The buffer component offscreen bitmap [bitmapId] is bound to now, if it has been sized. */
  fun boundComponentBuffer(bitmapId: Int): Target? = bindings[bitmapId]?.target

  /**
   * Binds component offscreen bitmap [bitmapId] to a [width] x [height] buffer and publishes it in
   * [images], as core's `BitmapData.ensureOffscreenBitmap` does each time the bitmap is drawn into.
   *
   * The current buffer is kept when it already has that size and belongs to the running scope.
   * Otherwise one of that size is taken from the spares or allocated; the one it replaces goes back
   * to the spares, or — when an outer scope bound it — is restored when this scope ends.
   */
  fun componentBuffer(
    bitmapId: Int,
    width: Int,
    height: Int,
    images: MutableMap<Int, ImageBitmap>,
  ): Target {
    val current = bindings[bitmapId]
    val scope = scopes.lastOrNull()
    val owned = current != null && current.scope === scope
    if (current != null && owned && current.target.matches(width, height)) {
      images[bitmapId] = current.target.image
      return current.target
    }
    if (current != null) {
      if (owned) {
        scope?.acquired?.remove(bitmapId to current.target)
        release(current.target)
      } else {
        requireNotNull(scope).saved += bitmapId to current
      }
    }
    val target = acquire(width, height)
    bindings[bitmapId] = Binding(target, scope)
    scope?.acquired?.add(bitmapId to target)
    images[bitmapId] = target.image
    return target
  }

  /** Opens a component-buffer scope: core's `BitmapData.pushOffscreenScope`. */
  fun pushScope() {
    scopes += Scope()
  }

  /**
   * Closes the innermost scope: its buffers go back to the spares, and the bindings it displaced
   * are restored — core's `BitmapData.popOffscreenScope`. A bitmap the scope bound and nothing
   * outside it did is unbound, so a later draw of it outside the scope draws nothing rather than a
   * buffer another scope may have reused.
   */
  fun popScope(images: MutableMap<Int, ImageBitmap>) {
    val scope = scopes.removeLastOrNull() ?: return
    for ((bitmapId, target) in scope.acquired) {
      if (bindings[bitmapId]?.target === target) {
        bindings.remove(bitmapId)
        if (images[bitmapId] === target.image) images.remove(bitmapId)
      }
      release(target)
    }
    for ((bitmapId, binding) in scope.saved.asReversed()) {
      bindings[bitmapId] = binding
      images[bitmapId] = binding.target.image
    }
  }

  /** Drops native canvas/image references when the owning document leaves composition. */
  fun dispose() {
    targets.clear()
    bindings.clear()
    spare.clear()
    scopes.clear()
    componentAllocations = 0
    allocatedPixels = 0L
  }

  private fun acquire(width: Int, height: Int): Target {
    val index = spare.indexOfFirst { it.matches(width, height) }
    if (index >= 0) return spare.removeAt(index)
    // Spares are only a cache: give them up before refusing a buffer the document needs now.
    while (spare.isNotEmpty() && !fits(width, height)) drop(spare.removeAt(0))
    reserve(width, height)
    val image = ImageBitmap(width, height)
    componentAllocations++
    allocatedPixels += width.toLong() * height.toLong()
    return Target(image, GraphicsCanvas(image))
  }

  private fun release(target: Target) {
    if (target in spare) return
    spare += target
    if (spare.size > MAX_SPARE_BUFFERS) drop(spare.removeAt(0))
  }

  private fun drop(target: Target) {
    componentAllocations--
    allocatedPixels -= target.image.width.toLong() * target.image.height.toLong()
  }

  private fun fits(width: Int, height: Int): Boolean =
    allocationCount < limits.maxTargets &&
      width.toLong() * height.toLong() <= limits.maxPixels - allocatedPixels

  private fun reserve(width: Int, height: Int) {
    require(allocationCount < limits.maxTargets) {
      "DrawToBitmap exceeds ${limits.maxTargets} mutable targets"
    }
    val pixels = width.toLong() * height.toLong()
    require(pixels <= limits.maxPixels - allocatedPixels) {
      "DrawToBitmap exceeds ${limits.maxPixels} aggregate mutable pixels"
    }
  }

  class Target(val image: ImageBitmap, val canvas: Canvas) {
    fun matches(width: Int, height: Int): Boolean = image.width == width && image.height == height
  }

  private class Binding(val target: Target, val scope: Scope?)

  private class Scope {
    val acquired = mutableListOf<Pair<Int, Target>>()
    val saved = mutableListOf<Pair<Int, Binding>>()
  }

  private companion object {
    /** Core's `Limits.MAX_BITMAP_POOL_SIZE`. */
    const val MAX_SPARE_BUFFERS = 8
  }
}

package ee.schimke.composeai.rcplayer.threed

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D

/**
 * No GPU depth renderer on desktop and Apple yet: the software rasterizer draws. See
 * docs/design/RC_3D_EXTENSION.md, "The Skia targets", for why. Wasm has its own, over WebGL2.
 */
internal actual class RcDepthRenderer actual constructor() {
  actual fun canDraw(scope: DrawScope): Boolean = false

  actual fun beginFrame() {}

  actual fun clearDepth() {}

  actual fun draw(
    scope: DrawScope,
    size: Size,
    width: Int,
    height: Int,
    mesh: Rc3DMeshData,
    engine: RcSoftware3D,
    smooth: Boolean,
    texture: ImageBitmap?,
  ): Boolean = false

  actual fun dispose() {}
}

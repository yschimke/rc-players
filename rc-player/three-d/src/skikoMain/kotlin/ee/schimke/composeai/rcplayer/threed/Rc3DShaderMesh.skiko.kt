package ee.schimke.composeai.rcplayer.threed

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D

/**
 * Skiko exposes no mesh API (no `SkMesh` bindings as of 0.150), so every draw takes the
 * CPU-transformed vertex path. See docs/design/RC_3D_EXTENSION.md, "The Skia targets".
 */
internal actual class RcShaderMeshDrawer actual constructor() {
  actual fun canDraw(scope: DrawScope): Boolean = false

  actual fun draw(
    scope: DrawScope,
    width: Float,
    height: Float,
    mesh: Rc3DMeshData,
    engine: RcSoftware3D,
    smooth: Boolean,
    baseColor: Int,
    texture: ImageBitmap?,
    slot: Int,
  ): Boolean = false

  actual fun dispose() {}
}

package ee.schimke.composeai.rcplayer.threed

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D

/** Skiko exposes no mesh API: every draw takes the CPU-transformed vertex path. */
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

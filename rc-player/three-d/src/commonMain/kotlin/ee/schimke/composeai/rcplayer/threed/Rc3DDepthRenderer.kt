package ee.schimke.composeai.rcplayer.threed

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D

/**
 * Rasterizes meshes on the GPU with a real depth buffer: OpenGL ES on Android 10 and later, WebGL2
 * in the browser. It does the software rasterizer's job (per-pixel depth that persists across the
 * meshes of a depth pass, each mesh composited where it sits in the document), so it takes the
 * draws that need depth and cannot go through the depthless vertex path. Where there is no such
 * renderer, [draw] returns false and the software rasterizer draws.
 */
internal expect class RcDepthRenderer() {
  /** Whether this platform and [scope]'s canvas can take a draw at all. */
  fun canDraw(scope: DrawScope): Boolean

  /** A new frame: composites from frames before the last are no longer on screen. */
  fun beginFrame()

  /** `CLEAR_DEPTH`: the next mesh starts a new depth pass. */
  fun clearDepth()

  /**
   * Rasterize [mesh] as [engine]'s camera, model matrix, lights and material set it, at [width] by
   * [height] pixels, depth-tested against the meshes drawn since the last [clearDepth], and
   * composite it into [scope] over [size] in the drawing's units.
   */
  fun draw(
    scope: DrawScope,
    size: Size,
    width: Int,
    height: Int,
    mesh: Rc3DMeshData,
    engine: RcSoftware3D,
    smooth: Boolean,
    texture: ImageBitmap?,
  ): Boolean

  fun dispose()
}

/** What the GPU depth renderer can take; [Rc3DSession] reads a document against these up front. */
internal object Rc3DDepthLimits {
  /** Composites one frame may hold. */
  const val MAX_DRAWS_PER_FRAME: Int = 16

  /** Lights its shader takes. */
  const val MAX_LIGHTS: Int = Rc3DShaderMeshData.MAX_LIGHTS
}

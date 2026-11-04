package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.threed.engine.RcMatrix4

/**
 * Places a document's 3D scene in the space of an XR panel, so a host can hand meshes to the XR
 * runtime as real objects in the room while the 2D document stays on the panel.
 *
 * In XR the user's eyes are the camera, so the document's camera cannot be used as one. It is used
 * to place the scene instead, with the panel as a window onto it: the camera's focus plane lies on
 * the panel, and eye-space units are scaled so the view's cross-section there matches the panel's
 * height. Seen head-on from the panel's normal, the scene then lines up with the flat render, and
 * moving around shows its depth.
 *
 * Panel space is the panel's local frame in metres: origin at its centre, +x right, +y up, +z
 * towards the viewer, the convention SceneCore uses for a panel entity's children.
 */
public object Rc3DPanelPlacement {
  /**
   * The column-major model matrix that puts [draw]'s mesh in panel space, for a panel
   * [panelHeightMeters] tall. [focusDistance] is how far in front of the camera the panel plane
   * sits, in the document's units; by default the distance to the world origin, which is where the
   * draft's look-at cameras usually aim.
   */
  public fun panelTransform(
    draw: Rc3DMeshDraw,
    panelHeightMeters: Float,
    focusDistance: Float = defaultFocusDistance(draw.view),
  ): FloatArray {
    require(panelHeightMeters > 0f) { "Panel height $panelHeightMeters" }
    val p = draw.projection
    val perspective = p[11] == -1f && p[15] == 0f
    // The view's height at the focus plane: 2 d tan(fov / 2) for a perspective camera, where
    // p[5] = 1 / tan(fov / 2); 2 / p[5] for an orthographic one.
    val viewHeight = if (perspective) 2f * focusDistance / p[5] else 2f / p[5]
    require(viewHeight.isFinite() && viewHeight > 0f) { "Camera has no usable vertical extent" }
    val s = panelHeightMeters / viewHeight

    // panel = S(s) * T(0, 0, d) * view * model: eye space shifted so the focus plane is z = 0.
    val out = FloatArray(16)
    RcMatrix4.identity(out)
    RcMatrix4.scale(out, s, s, s)
    RcMatrix4.translate(out, 0f, 0f, focusDistance)
    RcMatrix4.multiply(out, out, draw.view)
    RcMatrix4.multiply(out, out, draw.model)
    return out
  }

  /** Distance from the camera to the world origin along its view axis, or 1 if it is behind. */
  public fun defaultFocusDistance(view: FloatArray): Float {
    val d = -view[14]
    return if (d.isFinite() && d > 1e-4f) d else 1f
  }
}

package ee.schimke.composeai.rcplayer.xrsample

import ee.schimke.composeai.rcplayer.threed.Rc3DMeshDraw
import ee.schimke.composeai.rcplayer.threed.Rc3DPanelPlacement

/**
 * The stereo surface's rectangle in panel space (metres, +x right, +y up, +z towards the viewer):
 * centred on ([centerX], [centerY]) in the plane z = [z], [width] by [height].
 *
 * It covers the player's viewport, lifted [LIFT_METERS] off the panel so the compositor draws it
 * over the panel rather than fighting it for depth.
 */
internal data class RcStereoWindow(
  val centerX: Float,
  val centerY: Float,
  val z: Float,
  val width: Float,
  val height: Float,
) {
  val left: Float
    get() = centerX - width / 2f

  val right: Float
    get() = centerX + width / 2f

  val bottom: Float
    get() = centerY - height / 2f

  val top: Float
    get() = centerY + height / 2f

  companion object {
    /** A millimetre: enough to sort in front of the panel, too little to see as a gap. */
    const val LIFT_METERS: Float = 0.001f

    fun of(viewport: RcXrViewport): RcStereoWindow =
      RcStereoWindow(
        viewport.offsetXMeters,
        viewport.offsetYMeters,
        LIFT_METERS,
        viewport.widthMeters,
        viewport.heightMeters,
      )
  }
}

/** Both eyes' positions in panel space, in metres. */
internal data class RcEyePositions(
  val leftX: Float,
  val leftY: Float,
  val leftZ: Float,
  val rightX: Float,
  val rightY: Float,
  val rightZ: Float,
)

/** One eye's camera, column-major: panel space to eye space, and eye space to clip space. */
internal class RcEyeCamera(val view: FloatArray, val projection: FloatArray) {
  /** `projection * view * model`, for a [model] that maps a mesh into panel space. */
  fun modelViewProjection(model: FloatArray): FloatArray =
    RcStereoCameras.multiply(projection, RcStereoCameras.multiply(view, model))
}

/**
 * The per-eye cameras of the level-2 stereo surface (docs/design/RC_3D_EXTENSION.md, "XR").
 *
 * The surface is a window onto the scene: each eye sees through the same rectangle from its own
 * position, so each eye's camera is an off-axis perspective whose frustum passes through the
 * window's four corners (the "generalised perspective projection" of a CAVE wall or a head-tracked
 * display). Everything that lies on the window's plane then lands at the same place for both eyes,
 * which is the flat render's place; what lies behind it parts uncrossed and what lies in front
 * crossed, which is the depth.
 *
 * Only the eye's position matters, not where it looks: the window is fixed in panel space, and the
 * compositor shows each half of the surface to its eye wherever that eye points. The cameras are
 * aligned with the panel's axes. Plain Kotlin, so the maths is unit-tested without a headset.
 */
internal object RcStereoCameras {
  /** Near plane, metres in front of the eye: a mesh closer than this to an eye is clipped. */
  const val NEAR_METERS: Float = 0.01f

  /** Far plane, metres: far beyond any scene a panel places. */
  const val FAR_METERS: Float = 100f

  /**
   * The camera for an eye at ([x], [y], [z]) in panel space looking through [window], or null when
   * the eye is not in front of the window's plane (behind the panel, or on it): it can see nothing
   * through the window then.
   */
  fun eye(x: Float, y: Float, z: Float, window: RcStereoWindow): RcEyeCamera? {
    val distance = z - window.z
    if (!distance.isFinite() || distance <= NEAR_METERS) return null
    // The window's edges, relative to the eye, scaled from the window's plane to the near plane.
    val k = NEAR_METERS / distance
    val l = (window.left - x) * k
    val r = (window.right - x) * k
    val b = (window.bottom - y) * k
    val t = (window.top - y) * k
    val view =
      identity().also {
        it[12] = -x
        it[13] = -y
        it[14] = -z
      }
    return RcEyeCamera(view, frustum(l, r, b, t, NEAR_METERS, FAR_METERS))
  }

  /**
   * The column-major model matrix that puts [draw]'s mesh in panel space for [viewport]: the same
   * placement the level-3 entities take ([Rc3DPanelPlacement]), moved to the viewport's centre.
   */
  fun panelModel(draw: Rc3DMeshDraw, viewport: RcXrViewport): FloatArray {
    val placed = Rc3DPanelPlacement.panelTransform(draw, viewport.heightMeters)
    val shift =
      identity().also {
        it[12] = viewport.offsetXMeters
        it[13] = viewport.offsetYMeters
      }
    return multiply(shift, placed)
  }

  /** OpenGL's `glFrustum`, column-major. */
  fun frustum(l: Float, r: Float, b: Float, t: Float, n: Float, f: Float): FloatArray {
    val m = FloatArray(16)
    m[0] = 2f * n / (r - l)
    m[5] = 2f * n / (t - b)
    m[8] = (r + l) / (r - l)
    m[9] = (t + b) / (t - b)
    m[10] = -(f + n) / (f - n)
    m[11] = -1f
    m[14] = -2f * f * n / (f - n)
    return m
  }

  fun identity(): FloatArray =
    FloatArray(16).also {
      it[0] = 1f
      it[5] = 1f
      it[10] = 1f
      it[15] = 1f
    }

  /** `a * b`, column-major. */
  fun multiply(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(16)
    for (col in 0 until 4) {
      for (row in 0 until 4) {
        var sum = 0f
        for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
        out[col * 4 + row] = sum
      }
    }
    return out
  }

  /**
   * The inverse transpose of [m]'s upper 3x3, as a column-major 3x3: what turns a model-space
   * normal into panel space under a non-uniform scale. Identity when [m] is singular.
   */
  fun normalMatrix(m: FloatArray): FloatArray {
    val a = m[0]
    val b = m[4]
    val c = m[8]
    val d = m[1]
    val e = m[5]
    val f = m[9]
    val g = m[2]
    val h = m[6]
    val i = m[10]
    val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    if (det == 0f || !det.isFinite()) return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    val s = 1f / det
    // The cofactor matrix over the determinant is the inverse transpose; laid out column-major.
    return floatArrayOf(
      (e * i - f * h) * s,
      -(b * i - c * h) * s,
      (b * f - c * e) * s,
      -(d * i - f * g) * s,
      (a * i - c * g) * s,
      -(a * f - c * d) * s,
      (d * h - e * g) * s,
      -(a * h - b * g) * s,
      (a * e - b * d) * s,
    )
  }
}

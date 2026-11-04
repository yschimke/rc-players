package ee.schimke.composeai.rcplayer.xrsample

import ee.schimke.composeai.rcplayer.threed.Rc3DMeshDraw
import ee.schimke.composeai.rcplayer.threed.Rc3DPanelPlacement
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import kotlin.math.abs
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RcStereoCamerasTest {
  private fun near(expected: Float, actual: Float, tolerance: Float = 1e-4f) =
    assertTrue(abs(expected - actual) < tolerance, "expected $expected, got $actual")

  /** [p] in panel space through [camera] to normalized device coordinates. */
  private fun ndc(camera: RcEyeCamera, x: Float, y: Float, z: Float): FloatArray {
    val m = RcStereoCameras.multiply(camera.projection, camera.view)
    val out = FloatArray(4)
    for (row in 0 until 4) {
      out[row] = m[row] * x + m[4 + row] * y + m[8 + row] * z + m[12 + row]
    }
    return floatArrayOf(out[0] / out[3], out[1] / out[3], out[2] / out[3])
  }

  // A 0.8 m by 0.6 m viewport, its centre 0.1 m above the panel's.
  private val window = RcStereoWindow(0f, 0.1f, 0f, 0.8f, 0.6f)
  private val ipd = 0.064f

  @Test
  fun theWindowsCornersBoundEveryEyesView() {
    // Head-on, off to one side, and close up: wherever the eye is, the window fills its view.
    for ((x, y, z) in
      listOf(Triple(0f, 0.1f, 0.6f), Triple(0.5f, -0.2f, 0.9f), Triple(-0.1f, 0.3f, 0.2f))) {
      val eye = assertNotNull(RcStereoCameras.eye(x, y, z, window))
      val bottomLeft = ndc(eye, window.left, window.bottom, window.z)
      val topRight = ndc(eye, window.right, window.top, window.z)
      near(-1f, bottomLeft[0])
      near(-1f, bottomLeft[1])
      near(1f, topRight[0])
      near(1f, topRight[1])
    }
  }

  @Test
  fun aPointOnTheWindowHasNoDisparity() {
    val left = RcStereoCameras.eye(-ipd / 2, 0.1f, 0.6f, window)!!
    val right = RcStereoCameras.eye(ipd / 2, 0.1f, 0.6f, window)!!
    val l = ndc(left, 0.2f, 0.25f, window.z)
    val r = ndc(right, 0.2f, 0.25f, window.z)
    near(l[0], r[0])
    near(l[1], r[1])
  }

  @Test
  fun behindTheWindowPartsUncrossedAndInFrontCrossed() {
    val left = RcStereoCameras.eye(-ipd / 2, 0.1f, 0.6f, window)!!
    val right = RcStereoCameras.eye(ipd / 2, 0.1f, 0.6f, window)!!
    val behind = ndc(left, 0f, 0.1f, -0.3f)[0] - ndc(right, 0f, 0.1f, -0.3f)[0]
    val inFront = ndc(left, 0f, 0.1f, 0.2f)[0] - ndc(right, 0f, 0.1f, 0.2f)[0]
    // Uncrossed: the left eye sees a far point further left than the right eye does.
    assertTrue(behind < 0f, "behind $behind")
    assertTrue(inFront > 0f, "in front $inFront")
    // The same height for both eyes: they are level, so there is no vertical disparity.
    near(ndc(left, 0f, 0.3f, -0.3f)[1], ndc(right, 0f, 0.3f, -0.3f)[1])
  }

  @Test
  fun aNearerPointIsNearerInDepth() {
    val eye = RcStereoCameras.eye(0f, 0.1f, 0.6f, window)!!
    assertTrue(ndc(eye, 0f, 0.1f, 0.2f)[2] < ndc(eye, 0f, 0.1f, -0.3f)[2])
  }

  @Test
  fun anEyeBehindOrOnTheWindowSeesNothing() {
    assertNull(RcStereoCameras.eye(0f, 0f, 0f, window))
    assertNull(RcStereoCameras.eye(0f, 0f, -0.5f, window))
    assertNull(RcStereoCameras.eye(0f, 0f, Float.NaN, window))
  }

  @Test
  fun theWindowCoversTheViewportLiftedOffThePanel() {
    val viewport = RcXrViewport(0.6f, 0.05f, 0.2f, widthMeters = 0.9f)
    val w = RcStereoWindow.of(viewport)
    near(0.05f - 0.45f, w.left)
    near(0.05f + 0.45f, w.right)
    near(0.2f - 0.3f, w.bottom)
    near(0.2f + 0.3f, w.top)
    assertTrue(w.z > 0f && w.z < 0.01f)
  }

  @Test
  fun aHeadOnEyeSeesTheFocusPlaneWhereTheFlatRenderDrawsIt() {
    // A perspective camera 4 units back, 60 degrees tall, looking at the origin: the draft's usual
    // look-at camera. A point on its focus plane, a quarter of the way up the view, should land a
    // quarter of the way up the window for an eye straight in front of the viewport's centre.
    val fov = Math.toRadians(60.0).toFloat()
    val f = 1f / tan(fov / 2f)
    val projection =
      FloatArray(16).also {
        it[0] = f
        it[5] = f
        it[10] = -1.0002f
        it[11] = -1f
        it[14] = -0.2f
      }
    val view = RcStereoCameras.identity().also { it[14] = -4f }
    val halfHeight = 4f * tan(fov / 2f)
    val draw = draw(view = view, projection = projection)
    val viewport = RcXrViewport(0.6f, 0.1f, -0.05f, widthMeters = 0.6f)
    val model = RcStereoCameras.panelModel(draw, viewport)
    val eye = RcStereoCameras.eye(0.1f, -0.05f, 0.7f, RcStereoWindow.of(viewport))!!
    val mvp = eye.modelViewProjection(model)
    val y = halfHeight / 2f
    val clipY = mvp[5] * y + mvp[13]
    val clipW = mvp[7] * y + mvp[15]
    // The window is lifted a millimetre off the focus plane, hence the tolerance.
    near(0.5f, clipY / clipW, 2e-3f)
    // And the panel model is the room entities' placement, moved to the viewport's centre.
    val placed = Rc3DPanelPlacement.panelTransform(draw, viewport.heightMeters)
    near(placed[12] + 0.1f, model[12])
    near(placed[13] - 0.05f, model[13])
    near(placed[14], model[14])
  }

  @Test
  fun theNormalMatrixUndoesANonUniformScale() {
    val m =
      RcStereoCameras.identity().also {
        it[0] = 2f
        it[5] = 0.5f
        it[12] = 3f
      }
    val n = RcStereoCameras.normalMatrix(m)
    near(0.5f, n[0])
    near(2f, n[4])
    near(1f, n[8])
    near(0f, n[1])
  }

  private fun draw(view: FloatArray, projection: FloatArray) =
    Rc3DMeshDraw(
      meshId = 1,
      mesh =
        Rc3DMeshData(
          floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
          FloatArray(0),
          intArrayOf(0, 1, 2),
        ),
      model = RcStereoCameras.identity(),
      view = view,
      projection = projection,
      color = -1,
      textureBitmapId = 0,
      mode = 0,
      viewportWidth = 100f,
      viewportHeight = 100f,
    )
}

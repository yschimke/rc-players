package ee.schimke.composeai.rcplayer.xrsample

import ee.schimke.composeai.rcplayer.threed.Rc3DFrame
import ee.schimke.composeai.rcplayer.threed.Rc3DMeshDraw
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RcStereoRoutingTest {
  private val ready = RcStereoState(contentAllowed = true, eyesTracked = true, rendererReady = true)

  @Test
  fun rigidDrawsAreHandedOffWhateverTheStereoSurfaceCanDo() {
    for (stereo in listOf(ready, RcStereoState.OFF, ready.copy(eyesTracked = false))) {
      assertEquals(RcDrawRoute.Entity, RcDrawRouting.route(deforming = false, stereo))
    }
  }

  @Test
  fun deformingDrawsGoStereoWhenItIsReady() {
    assertEquals(RcDrawRoute.Stereo, RcDrawRouting.route(deforming = true, ready))
  }

  @Test
  fun deformingDrawsFallBackToThePanelWhenAnyPartIsMissing() {
    for (missing in
      listOf(
        ready.copy(contentAllowed = false),
        ready.copy(eyesTracked = false),
        ready.copy(rendererReady = false),
        RcStereoState.OFF,
      )) {
      assertFalse(missing.ready)
      assertEquals(RcDrawRoute.Panel, RcDrawRouting.route(deforming = true, missing), "$missing")
    }
  }

  @Test
  fun aDeformingMeshMovesFromEntityToStereoAndBackToThePanelWhenTrackingIsLost() {
    // The host's sequence: rigid until the tracker calls it deforming, then stereo while the eyes
    // are tracked, then the panel.
    val tracker = RcDeformationTracker()
    val routes = mutableListOf<RcDrawRoute>()
    for ((frame, version) in (1L..6L).withIndex()) {
      tracker.observe("-1/1", version)
      val stereo = if (frame < 4) ready else ready.copy(eyesTracked = false)
      routes += RcDrawRouting.route(tracker.isDeforming("-1/1"), stereo)
    }
    assertEquals(
      listOf(
        RcDrawRoute.Entity,
        RcDrawRoute.Entity,
        RcDrawRoute.Stereo,
        RcDrawRoute.Stereo,
        RcDrawRoute.Panel,
        RcDrawRoute.Panel,
      ),
      routes,
    )
  }

  @Test
  fun aComponentsFrameReplacesOnlyItsOwnStereoDraws() {
    val layer = RcStereoLayer()
    val root = listOf(draw(-1, 0), draw(-1, 1))
    val card = listOf(draw(10, 0))
    assertTrue(layer.update(Rc3DFrame(-1, root), root))
    assertTrue(layer.update(Rc3DFrame(10, card), card))
    assertEquals(listOf("-1/0", "-1/1", "10/0"), layer.current.map { it.key })

    // The root now draws only one deforming mesh: its other one leaves, the card's stays.
    val next = listOf(draw(-1, 1))
    assertTrue(layer.update(Rc3DFrame(-1, next), next))
    assertEquals(listOf("10/0", "-1/1"), layer.current.map { it.key })

    // "-1/" is not a prefix of "-10/": a component's id is matched whole.
    val other = listOf(draw(-10, 0))
    layer.update(Rc3DFrame(-10, other), other)
    layer.update(Rc3DFrame(-1, emptyList()), emptyList())
    assertEquals(listOf("10/0", "-10/0"), layer.current.map { it.key })
  }

  @Test
  fun aFrameWithNothingStereoForAComponentWithNothingThereChangesNothing() {
    val layer = RcStereoLayer()
    assertFalse(layer.update(Rc3DFrame(-1, listOf(draw(-1, 0))), emptyList()))
    assertTrue(layer.isEmpty)
  }

  private fun draw(component: Int, index: Int) =
    Rc3DMeshDraw(
      meshId = 1,
      mesh =
        Rc3DMeshData(
          floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
          FloatArray(0),
          intArrayOf(0, 1, 2),
        ),
      model = RcStereoCameras.identity(),
      view = RcStereoCameras.identity(),
      projection = RcStereoCameras.identity(),
      color = -1,
      textureBitmapId = 0,
      mode = 0,
      viewportWidth = 100f,
      viewportHeight = 100f,
      componentId = component,
      drawIndex = index,
    )
}

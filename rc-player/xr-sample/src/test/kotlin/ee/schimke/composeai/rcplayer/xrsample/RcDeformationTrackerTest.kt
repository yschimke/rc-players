package ee.schimke.composeai.rcplayer.xrsample

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RcDeformationTrackerTest {
  @Test
  fun aRigidMeshIsNeverDeforming() {
    val tracker = RcDeformationTracker()
    repeat(10) { assertFalse(tracker.observe("-1/0", 1L)) }
    assertFalse(tracker.isDeforming("-1/0"))
  }

  @Test
  fun aSingleRebuildIsNotDeforming() {
    // A primitive whose parameters change once (a host value set, say) is rebuilt and kept.
    val tracker = RcDeformationTracker()
    val reported = listOf(1L, 1L, 2L, 2L, 2L).map { tracker.observe("-1/0", it) }
    assertEquals(List(5) { false }, reported)
    assertFalse(tracker.isDeforming("-1/0"))
  }

  @Test
  fun twoChangesRunningMakeADeformingMeshAndItStaysOne() {
    val tracker = RcDeformationTracker()
    val reported = listOf(1L, 2L, 3L, 4L, 4L, 4L).map { tracker.observe("-1/1", it) }
    // Reported once, on the frame it becomes deforming; it then stays with the player.
    assertEquals(listOf(false, false, true, false, false, false), reported)
    assertTrue(tracker.isDeforming("-1/1"))
    assertFalse(tracker.isDeforming("-1/0"))
  }

  @Test
  fun everyLaterFrameOfADeformingMeshIsStillDeforming() {
    // The host consults isDeforming on every frame, not only observe's one-time report.
    val tracker = RcDeformationTracker()
    for (version in 1L..10L) {
      tracker.observe("-1/1", version)
      if (version >= 3) assertTrue(tracker.isDeforming("-1/1"), "frame $version")
    }
  }

  @Test
  fun repaintsAtTheSameAnimationTimeStillCountAsDeforming() {
    // Each version painted twice: the clock ticks every other repaint.
    val tracker = RcDeformationTracker()
    for (version in listOf(1L, 1L, 2L, 2L, 3L, 3L)) tracker.observe("-1/1", version)
    assertTrue(tracker.isDeforming("-1/1"))
  }

  @Test
  fun rebuildsFarApartAreNotDeforming() {
    val tracker = RcDeformationTracker()
    for (version in listOf(1L) + List(20) { 2L } + List(20) { 3L }) tracker.observe("-1/0", version)
    assertFalse(tracker.isDeforming("-1/0"))
  }
}

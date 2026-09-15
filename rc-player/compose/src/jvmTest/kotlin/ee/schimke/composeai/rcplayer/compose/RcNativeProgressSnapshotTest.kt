package ee.schimke.composeai.rcplayer.compose

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcNativeProgressSnapshotTest {
  @Test
  fun fillProgressPublishesRootGeometryBeforeResolvingArcBounds() {
    val arcs = snapshot("CircularProgressRemote-384x384.rc").arcs()

    assertEquals(2, arcs.size)
    assertTrue(arcs.all { it.third > it.first && it.fourth > it.second }, arcs.toString())
    assertTrue(arcs.all { it.third > 150f && it.fourth > 150f }, arcs.toString())
  }

  @Test
  fun progressColorsAreResolvedBeforeDrawingContent() {
    val arcs = snapshot("ArcProgressRemote-454x400.rc").arcs()

    assertEquals(2, arcs.size)
    assertTrue(arcs.all { it.color ushr 24 != 0 })
  }

  private fun snapshot(fixture: String): RcNativeDocumentSnapshot =
    RcNativeSnapshotBridge.decode(
      File("../../third_party/rc-embedded-player/src/test/resources/rc-fixtures/$fixture")
        .readBytes()
    )

  private fun RcNativeDocumentSnapshot.arcs(): List<RcNativeDrawCommand> =
    root.flatten().flatMap { it.commands }.filter { it.kind == RcNativeDrawCommand.ARC }

  private fun RcNativeNodeSnapshot.flatten(): List<RcNativeNodeSnapshot> =
    listOf(this) + children.flatMap { it.flatten() }
}

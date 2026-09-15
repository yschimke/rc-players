package ee.schimke.composeai.rcplayer.compose

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RcNativeAnimationSnapshotTest {
  @Test
  fun clockDrivenProgressProducesDifferentNativeFrames() {
    val bytes =
      File("src/jvmTest/resources/rc-fixtures/IndeterminateCircularProgress-400x400.rc").readBytes()
    val session = RcNativeSnapshotSession(bytes)

    val first = session.snapshot(0.25f)
    val second = session.snapshot(0.75f)
    val firstCommands = first.root.flatten().flatMap { it.commands }
    val secondCommands = second.root.flatten().flatMap { it.commands }
    val canvas = first.root.flatten().single { it.kind == RcNativeNodeSnapshot.CANVAS }
    assertEquals(72f, canvas.widthValue)
    assertEquals(72f, canvas.heightValue)
    assertTrue(canvas.commands.filter { it.kind == RcNativeDrawCommand.ARC }.all { it.third > 60f })
    assertTrue(first.needsContinuousFrames)
    assertTrue(
      firstCommands.isNotEmpty(),
      "native frame is empty; diagnostics=${first.diagnostics}",
    )
    assertTrue(
      firstCommands
        .flatMap { listOf(it.first, it.second, it.third, it.fourth, it.fifth, it.sixth) }
        .all(Float::isFinite),
      "component geometry must settle before animated draw commands are exported",
    )
    assertNotEquals(
      firstCommands.map { listOf(it.first, it.second, it.third, it.fourth, it.fifth, it.sixth) },
      secondCommands.map { listOf(it.first, it.second, it.third, it.fourth, it.fifth, it.sixth) },
      "clock-driven native frames must evolve",
    )
  }

  private fun RcNativeNodeSnapshot.flatten(): List<RcNativeNodeSnapshot> =
    listOf(this) + children.flatMap { it.flatten() }
}

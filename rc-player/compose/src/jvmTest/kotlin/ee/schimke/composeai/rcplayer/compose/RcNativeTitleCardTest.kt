package ee.schimke.composeai.rcplayer.compose

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcNativeTitleCardTest {
  @Test
  fun exportsTheMorningRunCardAsNativeLayoutAndText() {
    val document =
      File(
          "../../third_party/rc-embedded-player/src/test/resources/rc-fixtures/TitleCardRemote-640x480.rc"
        )
        .readBytes()
    val snapshot = RcNativeSnapshotBridge.decode(document)
    val nodes = snapshot.root.flatten()

    assertEquals(2f, snapshot.density)
    assertTrue(snapshot.diagnostics.isNotEmpty())
    assertTrue(snapshot.diagnostics.all { it.operationName.isNotBlank() && it.reason.isNotBlank() })

    val card = nodes.single { it.kind == RcNativeNodeSnapshot.COLUMN }
    assertEquals(12f, card.paddingLeft)
    assertEquals(12f, card.paddingTop)
    assertEquals(26f, card.cornerRadius)
    assertEquals(32f, card.minimumHeight)
    assertEquals(0xff332e3c.toInt(), card.backgroundColor)
    assertTrue(card.hasBackground)
    assertTrue(card.clickable)

    val labels =
      nodes
        .filter { it.kind == RcNativeNodeSnapshot.TEXT }
        .flatMap { it.commands }
        .filter { it.kind == RcNativeDrawCommand.TEXT }
    assertEquals(listOf("Morning run", "5.2 km · 28 min"), labels.map { it.text })
    assertEquals(listOf(16f, 15f), labels.map { it.textSize })
  }

  private fun RcNativeNodeSnapshot.flatten(): List<RcNativeNodeSnapshot> =
    listOf(this) + children.flatMap { it.flatten() }
}

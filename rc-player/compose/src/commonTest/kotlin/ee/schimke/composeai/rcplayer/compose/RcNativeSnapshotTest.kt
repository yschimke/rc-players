package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RcNativeSnapshotTest {
  @Test
  fun decodesStaticCanvasCommandsIntoAComponentTree() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0), legacyWidth = 320, legacyHeight = 180),
        listOf(
          RcRootLayout(7),
          RcAccessibilitySemantics(
            contentDescriptionId = -1,
            role = RcAccessibilitySemantics.ROLE_BUTTON,
            textId = -1,
            stateDescriptionId = -1,
            mode = RcAccessibilitySemantics.MODE_SET,
            enabled = true,
            clickable = true,
          ),
          RcPaintData(listOf(4, 0xff336699.toInt())),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(10f),
            RcFloatWord.literal(20f),
            RcFloatWord.literal(110f),
            RcFloatWord.literal(70f),
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))

    assertEquals(320, snapshot.width)
    assertEquals(180, snapshot.height)
    assertEquals(1, snapshot.root.children.size)
    assertEquals(RcNativeNodeSnapshot.ROOT, snapshot.root.children.single().kind)
    assertEquals(
      RcAccessibilitySemantics.ROLE_BUTTON,
      snapshot.root.children.single().semanticRole,
    )
    assertTrue(snapshot.root.children.single().clickable)
    val command = snapshot.root.children.single().commands.single()
    assertEquals(RcNativeDrawCommand.RECT, command.kind)
    assertEquals(10f, command.first)
    assertEquals(70f, command.fourth)
    assertEquals(0xff336699.toInt(), command.color)
    assertTrue(snapshot.unsupportedOpcodes.isEmpty())
  }

  @Test
  fun rejectsTruncatedPaintDataWithAnExportedExceptionType() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(1),
          RcPaintData(listOf(4)),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val error =
      assertFailsWith<IllegalArgumentException> {
        RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
      }

    assertEquals("Paint command 4 is truncated", error.message)
  }

  @Test
  fun reportsIgnoredBlendModesAsPartial() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(1),
          RcPaintData(listOf(18 or (3 shl 16))),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))

    assertEquals(listOf("Blend mode 3 is not represented by the native POC"), snapshot.notes)
  }
}

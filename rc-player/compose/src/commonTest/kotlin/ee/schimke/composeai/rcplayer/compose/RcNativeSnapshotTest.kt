package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightInModifier
import ee.schimke.composeai.rcplayer.protocol.RcIdOperation
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthInModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
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
    assertTrue(snapshot.unsupportedOpcodes.isEmpty(), snapshot.diagnostics.toString())
    assertTrue(snapshot.diagnostics.isEmpty())
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
    val diagnostic = snapshot.diagnostics.single()
    assertEquals(RcNativeDiagnostic.UNSUPPORTED, diagnostic.severity)
    assertEquals(RcOpcodes.PAINT_VALUES, diagnostic.opcode)
    assertEquals("PaintValues", diagnostic.operationName)
    assertEquals(1, diagnostic.componentId)
    assertEquals("Blend mode 3 is not represented by the native POC", diagnostic.reason)
  }

  @Test
  fun reportsUnsupportedOperationsWithTheirComponentAndInventoryName() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(42),
          RcIdOperation(RcOpcodes.DRAW_PATH, 7),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))

    assertEquals(listOf(RcOpcodes.DRAW_PATH), snapshot.unsupportedOpcodes)
    val diagnostic = snapshot.diagnostics.single()
    assertEquals(RcNativeDiagnostic.UNSUPPORTED, diagnostic.severity)
    assertEquals(RcOpcodes.DRAW_PATH, diagnostic.opcode)
    assertEquals("DrawPath", diagnostic.operationName)
    assertEquals(42, diagnostic.componentId)
    assertEquals("Operation is not represented by the native player", diagnostic.reason)
  }

  @Test
  fun reportsUnwiredClickModifiersAgainstTheirOwningComponent() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(9),
          RcClickModifier,
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))

    val diagnostic = snapshot.diagnostics.single()
    assertEquals(RcOpcodes.MODIFIER_CLICK, diagnostic.opcode)
    assertEquals("ModifierClick", diagnostic.operationName)
    assertEquals(9, diagnostic.componentId)
    assertEquals(
      "Click action dispatch is not implemented by the native player",
      diagnostic.reason,
    )
  }

  @Test
  fun reportsNonDefaultStrokeCapAndJoinAsPartial() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(1),
          RcPaintData(listOf(7 or (1 shl 16), 15 or (2 shl 16))),
          RcDraw4(
            RcOpcodes.DRAW_LINE,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(10f),
            RcFloatWord.literal(10f),
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))

    assertEquals(
      listOf(
        "Stroke cap 1 is not represented by the native POC",
        "Stroke join 2 is not represented by the native POC",
      ),
      snapshot.diagnostics.map { it.reason },
    )
    assertTrue(snapshot.diagnostics.all { it.severity == RcNativeDiagnostic.UNSUPPORTED })
  }

  @Test
  fun exportsStaticConstraintsAndSkipsUnreachableStateBranches() {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 50, modern = false),
        listOf(
          RcIntegerConstant(20, 0),
          RcRootLayout(1),
          RcLayoutContent(2),
          RcStateLayout(3, 0, horizontalPositioning = 1, verticalPositioning = 4, indexId = 20),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(80f)),
          RcWidthInModifier(RcFloatWord.literal(40f), RcFloatWord.literal(70f)),
          RcHeightInModifier(RcFloatWord.literal(10f), RcFloatWord.literal(30f)),
          RcLayoutContent(4),
          RcBoxLayout(5, 0, horizontalPositioning = 1, verticalPositioning = 4),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(10f),
            RcFloatWord.literal(10f),
          ),
          end,
          RcBoxLayout(6, 0, horizontalPositioning = 1, verticalPositioning = 4),
          RcIdOperation(RcOpcodes.DRAW_PATH, 99),
          end,
          end,
          end,
          end,
          end,
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    val state = snapshot.root.children.single().children.single().children.single()

    assertEquals(RcNativeNodeSnapshot.BOX, state.kind)
    assertEquals(40f, state.minimumWidth)
    assertEquals(70f, state.maximumWidth)
    assertEquals(10f, state.minimumHeight)
    assertEquals(30f, state.maximumHeight)
    assertEquals(listOf(5), state.children.map { it.componentId })
    assertTrue(snapshot.unsupportedOpcodes.isEmpty(), snapshot.diagnostics.toString())
  }
}

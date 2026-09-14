package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawTweenPath
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightInModifier
import ee.schimke.composeai.rcplayer.protocol.RcIdOperation
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathData
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
  fun reportsInvalidBlendModesAsPartial() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(1),
          RcPaintData(listOf(18 or (99 shl 16))),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))

    assertEquals(listOf("Blend mode 99 is invalid"), snapshot.notes)
    val diagnostic = snapshot.diagnostics.single()
    assertEquals(RcNativeDiagnostic.UNSUPPORTED, diagnostic.severity)
    assertEquals(RcOpcodes.PAINT_VALUES, diagnostic.opcode)
    assertEquals("PaintValues", diagnostic.operationName)
    assertEquals(1, diagnostic.componentId)
    assertEquals("Blend mode 99 is invalid", diagnostic.reason)
  }

  @Test
  fun reportsUnsupportedOperationsWithTheirComponentAndInventoryName() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(42),
          RcDrawTweenPath(
            path1Id = 7,
            path2Id = 8,
            tween = RcFloatWord.literal(0.5f),
            start = RcFloatWord.literal(0f),
            stop = RcFloatWord.literal(1f),
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))

    assertEquals(listOf(RcOpcodes.DRAW_TWEEN_PATH), snapshot.unsupportedOpcodes)
    val diagnostic = snapshot.diagnostics.single()
    assertEquals(RcNativeDiagnostic.UNSUPPORTED, diagnostic.severity)
    assertEquals(RcOpcodes.DRAW_TWEEN_PATH, diagnostic.opcode)
    assertEquals("DrawTweenPath", diagnostic.operationName)
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
  fun exportsNonDefaultStrokeCapJoinAndBlendMode() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(1),
          RcPaintData(listOf(7 or (1 shl 16), 15 or (2 shl 16), 18 or (14 shl 16))),
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

    val command = snapshot.root.children.single().commands.single()
    assertEquals(1, command.strokeCap)
    assertEquals(2, command.strokeJoin)
    assertEquals(14, command.blendMode)
    assertTrue(snapshot.diagnostics.isEmpty())
  }

  @Test
  fun diagnosesInvalidStrokeCapAndJoin() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(1),
          RcPaintData(listOf(7 or (9 shl 16), 15 or (8 shl 16))),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))

    assertEquals(
      listOf("Stroke cap 9 is invalid", "Stroke join 8 is invalid"),
      snapshot.diagnostics.map { it.reason },
    )
  }

  @Test
  fun exportsValidatedLineQuadraticAndCubicPathSegments() {
    fun marker(command: Int) = RcFloatWord(0x7fc00000 or command)
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcPathData(
            (1 shl 24) or 7,
            listOf(
              marker(RcPathCommands.MOVE),
              RcFloatWord.literal(1f),
              RcFloatWord.literal(2f),
              marker(RcPathCommands.LINE),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(3f),
              RcFloatWord.literal(4f),
              marker(RcPathCommands.QUADRATIC),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(5f),
              RcFloatWord.literal(6f),
              RcFloatWord.literal(7f),
              RcFloatWord.literal(8f),
              marker(RcPathCommands.CUBIC),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(9f),
              RcFloatWord.literal(10f),
              RcFloatWord.literal(11f),
              RcFloatWord.literal(12f),
              RcFloatWord.literal(13f),
              RcFloatWord.literal(14f),
              marker(RcPathCommands.CLOSE),
              marker(RcPathCommands.DONE),
            ),
          ),
          RcRootLayout(1),
          RcIdOperation(RcOpcodes.DRAW_PATH, 7),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    val command = snapshot.root.children.single().commands.single()

    assertEquals(RcNativeDrawCommand.PATH, command.kind)
    assertEquals(
      listOf(
        RcPathCommands.MOVE,
        RcPathCommands.LINE,
        RcPathCommands.QUADRATIC,
        RcPathCommands.CUBIC,
        RcPathCommands.CLOSE,
      ),
      command.path.map { it.kind },
    )
    assertEquals(14f, command.path[3].sixth)
    assertTrue(snapshot.diagnostics.isEmpty())
  }

  @Test
  fun exportsInlineGradientAndClipPathInCommandOrder() {
    fun marker(command: Int) = RcFloatWord(0x7fc00000 or command)
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcPathData(
            (1 shl 24) or 7,
            listOf(
              marker(RcPathCommands.MOVE),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(0f),
              marker(RcPathCommands.LINE),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(20f),
              RcFloatWord.literal(0f),
              marker(RcPathCommands.LINE),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(20f),
              RcFloatWord.literal(20f),
              marker(RcPathCommands.CLOSE),
              marker(RcPathCommands.DONE),
            ),
          ),
          RcRootLayout(1),
          RcNoArg(RcOpcodes.MATRIX_SAVE),
          RcIdOperation(RcOpcodes.CLIP_PATH, 7),
          RcPaintData(
            listOf(
              11,
              2,
              0xffff0000.toInt(),
              0xff0000ff.toInt(),
              2,
              RcFloatWord.literal(0f).bits,
              RcFloatWord.literal(1f).bits,
              RcFloatWord.literal(0f).bits,
              RcFloatWord.literal(0f).bits,
              RcFloatWord.literal(20f).bits,
              RcFloatWord.literal(0f).bits,
              0,
            )
          ),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(20f),
            RcFloatWord.literal(20f),
          ),
          RcNoArg(RcOpcodes.MATRIX_RESTORE),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    val commands = snapshot.root.children.single().commands

    assertEquals(
      listOf(
        RcNativeDrawCommand.SAVE,
        RcNativeDrawCommand.CLIP_PATH,
        RcNativeDrawCommand.RECT,
        RcNativeDrawCommand.RESTORE,
      ),
      commands.map { it.kind },
    )
    assertEquals(1, commands[1].pathWinding)
    val gradient = checkNotNull(commands[2].gradient)
    assertEquals(RcNativeGradient.LINEAR, gradient.kind)
    assertEquals(listOf(0xffff0000.toInt(), 0xff0000ff.toInt()), gradient.colors)
    assertEquals(listOf(0f, 1f), gradient.stops)
    assertEquals(20f, gradient.third)
    assertTrue(snapshot.diagnostics.isEmpty(), snapshot.diagnostics.toString())
  }

  @Test
  fun diagnosesUnsupportedGradientTilingWithoutDesynchronizingPaint() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(1),
          RcPaintData(
            listOf(
              11 or (1 shl 16),
              2,
              0xffff0000.toInt(),
              0xff0000ff.toInt(),
              0,
              RcFloatWord.literal(10f).bits,
              RcFloatWord.literal(10f).bits,
              RcFloatWord.literal(5f).bits,
              2,
              4,
              0xff112233.toInt(),
            )
          ),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(20f),
            RcFloatWord.literal(20f),
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    val command = snapshot.root.children.single().commands.single()

    assertEquals(0xff112233.toInt(), command.color)
    assertEquals(2, checkNotNull(command.gradient).tileMode)
    assertEquals(
      "Gradient tile mode 2 is approximated with clamp",
      snapshot.diagnostics.single().reason,
    )
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
          RcPaintData(listOf(4, 0xff123456.toInt())),
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
    assertEquals(0xff123456.toInt(), state.children.single().commands.single().color)
    assertTrue(snapshot.unsupportedOpcodes.isEmpty(), snapshot.diagnostics.toString())
  }
}

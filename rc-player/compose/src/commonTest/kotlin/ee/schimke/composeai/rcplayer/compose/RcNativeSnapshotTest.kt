package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmap
import ee.schimke.composeai.rcplayer.protocol.RcDrawText
import ee.schimke.composeai.rcplayer.protocol.RcDrawTweenPath
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcHapticFeedback
import ee.schimke.composeai.rcplayer.protocol.RcHapticType
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightInModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedAction
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedActionValue
import ee.schimke.composeai.rcplayer.protocol.RcIdOperation
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyle
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcVisibilityModifier
import ee.schimke.composeai.rcplayer.protocol.RcWakeIn
import ee.schimke.composeai.rcplayer.protocol.RcWidthInModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RcNativeSnapshotTest {
  @Test
  fun exportsOnlyRequiredNativeFrameScheduling() {
    fun snapshot(vararg operations: ee.schimke.composeai.rcplayer.protocol.RcOperation) =
      RcNativeSnapshotBridge.decode(
        RcDocumentCodec.encode(
          RcDocument(
            RcHeader(RcVersion(0, 1, 0)),
            operations.toList() + RcRootLayout(1) + RcNoArg(RcOpcodes.CONTAINER_END),
          )
        )
      )

    val static = snapshot(RcFloatConstant(100, RcFloatWord.literal(3f)))
    assertTrue(!static.needsContinuousFrames)
    assertTrue(!static.requestsNextFrame)
    assertEquals(-1f, static.wakeAfterSeconds)

    val animated =
      snapshot(
        RcFloatExpression(
          100,
          listOf(RcFloatWord(0x7fc00000 or RcSystemVariables.CONTINUOUS_SEC)),
          null,
        )
      )
    assertTrue(animated.needsContinuousFrames)
    assertTrue(!animated.requestsNextFrame)
    assertEquals(-1f, animated.wakeAfterSeconds)

    val delayed = snapshot(RcWakeIn(RcFloatWord.literal(0.25f)))
    assertTrue(!delayed.needsContinuousFrames)
    assertTrue(!delayed.requestsNextFrame)
    assertEquals(0.25f, delayed.wakeAfterSeconds)
  }

  @Test
  fun validatesAndDiagnosesAccessibilityModifierBoundaries() {
    val first =
      RcAccessibilitySemantics(
        contentDescriptionId = 0,
        role = RcAccessibilitySemantics.ROLE_BUTTON,
        textId = 0,
        stateDescriptionId = 0,
        mode = RcAccessibilitySemantics.MODE_SET,
        enabled = true,
        clickable = false,
      )
    val duplicate =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcTextData(0, "not semantic content"),
          RcRootLayout(1),
          first,
          first.copy(role = RcAccessibilitySemantics.ROLE_IMAGE),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(duplicate))

    assertEquals(RcAccessibilitySemantics.ROLE_IMAGE, snapshot.root.children.single().semanticRole)
    assertTrue(snapshot.root.children.single().hasSemantics)
    assertEquals(null, snapshot.root.children.single().semanticLabel)
    assertEquals(null, snapshot.root.children.single().semanticText)
    assertEquals(null, snapshot.root.children.single().semanticStateDescription)
    assertEquals(
      "Multiple accessibility modifiers collapse to the last modifier in the native player",
      snapshot.diagnostics.single().reason,
    )

    val invalid =
      duplicate.copy(
        operations =
          listOf(
            RcRootLayout(1),
            first.copy(role = 100),
            RcNoArg(RcOpcodes.CONTAINER_END),
          )
      )
    assertFailsWith<IllegalArgumentException> {
      RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(invalid))
    }

    val roleless =
      duplicate.copy(
        operations =
          listOf(
            RcRootLayout(1),
            first.copy(role = -1, mode = RcAccessibilitySemantics.MODE_CLEAR_AND_SET),
            RcNoArg(RcOpcodes.CONTAINER_END),
          )
      )
    assertTrue(
      RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(roleless))
        .root
        .children
        .single()
        .hasSemantics
    )
  }

  @Test
  fun retainedSessionAppliesNamedValuesAndDispatchesSingleClicksInOrder() {
    val width = RcFloatWord(0x7fc00000 or 20)
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcFloatConstant(20, RcFloatWord.literal(2f)),
          RcNamedVariable(20, RcNamedVariable.FLOAT_TYPE, "USER:width"),
          RcTextData(30, "open"),
          RcTextData(40, "before"),
          RcNamedVariable(40, RcNamedVariable.STRING_TYPE, "USER:title"),
          RcColorConstant(41, 0xff102030.toInt()),
          RcNamedVariable(41, RcNamedVariable.COLOR_TYPE, "theme:accent"),
          RcRootLayout(7),
          RcClickModifier,
          RcHostAction(77),
          RcHostNamedAction(30, RcHostNamedActionValue.FloatValue(20)),
          RcValueFloatChangeAction(20, RcFloatWord.literal(9f)),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            width,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(10f),
            RcFloatWord.literal(10f),
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )
    val session = RcNativeSnapshotSession(RcDocumentCodec.encode(document))

    assertFailsWith<IllegalArgumentException> {
      session.setFloat("width", 99f, timeSeconds = Float.NaN)
    }
    assertEquals(2f, session.snapshot().root.children.single().commands.single().first)
    assertFailsWith<IllegalArgumentException> { session.click(7, timeSeconds = -1f) }

    val named = session.setFloat("width", 5f)
    assertTrue(named.accepted)
    assertEquals(5f, named.snapshot.root.children.single().commands.single().first)
    assertTrue(session.setString("title", "after").accepted)
    assertTrue(session.setColor("theme:accent", 0xffaabbcc.toInt()).accepted)
    assertTrue(!session.setString("width", "wrong type").accepted)
    assertTrue(!session.setFloat("missing", 1f).accepted)

    val click = session.click(componentId = 7)

    assertTrue(click.accepted)
    assertEquals(
      listOf(RcNativeEvent.ACTION, RcNativeEvent.NAMED_FLOAT),
      click.events.map { it.kind },
    )
    assertEquals(77, click.events[0].actionId)
    assertEquals("open", click.events[1].name)
    assertEquals(5f, click.events[1].floatValue)
    assertEquals(9f, click.snapshot.root.children.single().commands.single().first)
    assertEquals(
      listOf(RcNativeNodeSnapshot.CLICK),
      click.snapshot.root.children.single().clickActionTypes,
    )
    assertTrue(click.snapshot.diagnostics.none { it.opcode == RcOpcodes.MODIFIER_CLICK })
  }

  @Test
  fun retainedSessionRollsBackNamedValueAfterInvalidSnapshot() {
    val fontSize = RcFloatWord(0x7fc00000 or 20)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 40, modern = false),
        listOf(
          RcFloatConstant(20, RcFloatWord.literal(12f)),
          RcNamedVariable(20, RcNamedVariable.FLOAT_TYPE, "USER:size"),
          RcTextData(30, "Title"),
          RcRootLayout(-2),
          RcTextLayout(
            -3,
            0,
            30,
            0xff000000.toInt(),
            fontSize,
            0,
            RcFloatWord.literal(400f),
            0,
            1,
            1,
            1,
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )
    val session = RcNativeSnapshotSession(RcDocumentCodec.encode(document))
    fun commands(node: RcNativeNodeSnapshot): List<RcNativeDrawCommand> =
      node.commands + node.children.flatMap(::commands)

    assertFailsWith<IllegalArgumentException> { session.setFloat("size", -1f) }
    assertEquals(12f, commands(session.snapshot().root).single().textSize)
  }

  @Test
  fun retainedSessionAdvancesFramesWithoutRedecoding() {
    val animationTime = RcFloatWord(0xff800000.toInt() or RcSystemVariables.ANIMATION_TIME)
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(1),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            animationTime,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(10f),
            RcFloatWord.literal(10f),
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )
    val session = RcNativeSnapshotSession(RcDocumentCodec.encode(document))

    val first = session.snapshot(timeSeconds = 1f)
    val second = session.snapshot(timeSeconds = 2f)

    assertEquals(1f, first.root.children.single().commands.single().first)
    assertEquals(2f, second.root.children.single().commands.single().first)
    assertFailsWith<IllegalArgumentException> { session.snapshot(timeSeconds = Float.NaN) }
  }

  @Test
  fun clickActionsObserveTheRequestedFrameTime() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcTextData(30, "clock"),
          RcRootLayout(7),
          RcClickModifier,
          RcHostNamedAction(
            30,
            RcHostNamedActionValue.FloatValue(RcSystemVariables.ANIMATION_TIME),
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )
    val session = RcNativeSnapshotSession(RcDocumentCodec.encode(document))

    val click = session.click(componentId = 7, timeSeconds = 2.5f)

    assertTrue(click.accepted)
    assertEquals(2.5f, click.events.single().floatValue)
  }

  @Test
  fun decodesStaticCanvasCommandsIntoAComponentTree() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0), legacyWidth = 320, legacyHeight = 180),
        listOf(
          RcTextData(10, "Submit"),
          RcTextData(11, "Send"),
          RcTextData(12, "Unavailable"),
          RcRootLayout(7),
          RcAccessibilitySemantics(
            contentDescriptionId = 10,
            role = RcAccessibilitySemantics.ROLE_BUTTON,
            textId = 11,
            stateDescriptionId = 12,
            mode = RcAccessibilitySemantics.MODE_CLEAR_AND_SET,
            enabled = false,
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
    assertTrue(!snapshot.root.children.single().enabled)
    assertEquals("Submit", snapshot.root.children.single().semanticLabel)
    assertEquals("Send", snapshot.root.children.single().semanticText)
    assertEquals("Unavailable", snapshot.root.children.single().semanticStateDescription)
    assertEquals(
      RcAccessibilitySemantics.MODE_CLEAR_AND_SET,
      snapshot.root.children.single().semanticMode,
    )
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
  fun exportsClickModifiersWithoutACompatibilityFailure() {
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

    val node = snapshot.root.children.single()
    assertEquals(listOf(RcNativeNodeSnapshot.CLICK), node.clickActionTypes)
    assertTrue(node.clickable)
    assertTrue(snapshot.diagnostics.isEmpty())
  }

  @Test
  fun diagnosesUnsupportedEffectsInsideSupportedClicks() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(9),
          RcClickModifier,
          RcHapticFeedback(RcHapticType.Confirm),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))

    assertTrue(
      snapshot.diagnostics.any {
        it.opcode == RcOpcodes.HAPTIC_FEEDBACK && it.severity == RcNativeDiagnostic.UNSUPPORTED
      }
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
  fun exportsResolvedCoreTextParagraphAndDecorationProperties() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0), legacyWidth = 200, legacyHeight = 100),
        listOf(
          RcTextData(40, "serif"),
          RcTextData(41, "مرحبا UIKit 👟"),
          RcTextStyle(
            listOf(
              RcTextStyleProperty.IntValue(1, 50),
              RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(24f)),
              RcTextStyleProperty.IntValue(6, 3),
              RcTextStyleProperty.FloatValue(7, RcFloatWord.literal(650f)),
              RcTextStyleProperty.IntValue(8, 40),
              RcTextStyleProperty.IntValue(9, RcTextLayout.ALIGN_END),
              RcTextStyleProperty.FloatValue(12, RcFloatWord.literal(0.05f)),
              RcTextStyleProperty.FloatValue(13, RcFloatWord.literal(2f)),
              RcTextStyleProperty.FloatValue(14, RcFloatWord.literal(1.2f)),
              RcTextStyleProperty.IntValue(16, 1),
              RcTextStyleProperty.IntValue(17, 1),
              RcTextStyleProperty.BooleanValue(18, true),
              RcTextStyleProperty.BooleanValue(19, true),
            )
          ),
          RcRootLayout(1),
          RcCoreText(
            textId = 41,
            properties =
              listOf(
                RcTextStyleProperty.IntValue(1, 2),
                RcTextStyleProperty.IntValue(24, 50),
                RcTextStyleProperty.IntValue(10, RcTextLayout.OVERFLOW_MIDDLE_ELLIPSIS),
                RcTextStyleProperty.IntValue(11, 2),
              ),
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    val command = snapshot.root.children.single().children.single().commands.single()
    val style = checkNotNull(command.textStyle)

    assertEquals("مرحبا UIKit 👟", command.text)
    assertEquals(24f, command.textSize)
    assertEquals(650f, command.textWeight)
    assertEquals(3, style.fontStyle)
    assertEquals("serif", style.fontFamilyName)
    assertEquals(RcTextLayout.ALIGN_END, style.alignment)
    assertEquals(RcTextLayout.OVERFLOW_MIDDLE_ELLIPSIS, style.overflow)
    assertEquals(2, style.maxLines)
    assertEquals(0.05f, style.letterSpacing)
    assertEquals(2f, style.lineHeightAdd)
    assertEquals(1.2f, style.lineHeightMultiplier)
    assertTrue(style.justified)
    assertTrue(style.underline)
    assertTrue(style.strikeThrough)
  }

  @Test
  fun exportsCanvasTextTypefaceAndBaselineAnchoring() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcTextData(40, "Canvas text"),
          RcRootLayout(1),
          RcPaintData(
            listOf(
              1,
              RcFloatWord.literal(18f).bits,
              16 or (((1 shl 10) or 650) shl 16),
              2,
            )
          ),
          RcDrawText(
            textId = 40,
            start = 0,
            end = 11,
            contextStart = 0,
            contextEnd = 11,
            x = RcFloatWord.literal(12f),
            y = RcFloatWord.literal(24f),
            rtl = false,
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val command =
      RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
        .root
        .children
        .single()
        .commands
        .single()
    val style = checkNotNull(command.textStyle)

    assertEquals("Canvas text", command.text)
    assertEquals(18f, command.textSize)
    assertEquals(650f, command.textWeight)
    assertEquals(2, style.fontStyle)
    assertEquals("serif", style.fontFamilyName)
    assertEquals(-1f, command.third)
    assertEquals(-1f, command.fourth)
  }

  @Test
  fun exportsImageAndFontResourcesWithOrderedBitmapGeometry() {
    val imageBytes = byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -1)
    val fontBytes = byteArrayOf(1, 2, 3, 4)
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0), legacyWidth = 100, legacyHeight = 50),
        listOf(
          RcBitmapData(
            imageId = 40,
            width = 2,
            height = 1,
            type = RcBitmapData.TYPE_RAW8888,
            encoding = RcBitmapData.ENCODING_INLINE,
            data = imageBytes,
          ),
          RcFontData(fontId = 50, type = 0, data = fontBytes),
          RcTextData(60, "two pixels"),
          RcRootLayout(1),
          RcDrawBitmap(
            imageId = 40,
            left = RcFloatWord.literal(10f),
            top = RcFloatWord.literal(12f),
            right = RcFloatWord.literal(30f),
            bottom = RcFloatWord.literal(22f),
            contentDescriptionId = 60,
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    assertContentEquals(imageBytes, snapshot.images.single().data)
    assertContentEquals(fontBytes, snapshot.fonts.single().data)
    val image = checkNotNull(snapshot.root.children.single().commands.single().image)
    assertEquals(40, image.imageId)
    assertEquals(0f, image.sourceLeft)
    assertEquals(2f, image.sourceRight)
    assertEquals(10f, image.destinationLeft)
    assertEquals(22f, image.destinationBottom)
    assertEquals("two pixels", image.contentDescription)
  }

  @Test
  fun zeroBitmapDescriptionIdDoesNotResolveUnrelatedText() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0), legacyWidth = 10, legacyHeight = 10),
        listOf(
          RcBitmapData(
            imageId = 40,
            width = 1,
            height = 1,
            type = RcBitmapData.TYPE_RAW8888,
            encoding = RcBitmapData.ENCODING_INLINE,
            data = byteArrayOf(-1, 0, 0, -1),
          ),
          RcTextData(0, "unrelated"),
          RcRootLayout(1),
          RcDrawBitmap(
            imageId = 40,
            left = RcFloatWord.literal(0f),
            top = RcFloatWord.literal(0f),
            right = RcFloatWord.literal(1f),
            bottom = RcFloatWord.literal(1f),
            contentDescriptionId = 0,
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val image =
      checkNotNull(
        RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
          .root
          .children
          .single()
          .commands
          .single()
          .image
      )
    assertEquals(null, image.contentDescription)
  }

  @Test
  fun paddedParentPublishesContentSizeToFillChild() {
    val reference: (Int) -> RcFloatWord = { RcFloatWord(0x7fc00000 or it) }
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 80, modern = false),
        listOf(
          RcRootLayout(-2),
          RcLayoutContent(-3),
          RcBoxLayout(-4, 0, horizontalPositioning = 1, verticalPositioning = 4),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(80f)),
          RcPaddingModifier(
            RcFloatWord.literal(10f),
            RcFloatWord.literal(15f),
            RcFloatWord.literal(30f),
            RcFloatWord.literal(25f),
          ),
          RcLayoutContent(-5),
          RcCanvasLayout(-6, 0),
          RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(Float.NaN)),
          RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(Float.NaN)),
          RcWidthInModifier(RcFloatWord.literal(-1f), RcFloatWord.literal(50f)),
          RcLayoutContent(-7),
          RcComponentValue(RcComponentValue.WIDTH, componentId = -6, valueId = 42),
          RcComponentValue(RcComponentValue.HEIGHT, componentId = -6, valueId = 43),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            reference(42),
            reference(43),
          ),
          end,
          end,
          end,
          end,
          end,
          end,
        ),
      )

    fun find(node: RcNativeNodeSnapshot, componentId: Int): RcNativeNodeSnapshot? {
      if (node.componentId == componentId) return node
      node.children.forEach { child ->
        find(child, componentId)?.let {
          return it
        }
      }
      return null
    }
    fun commands(node: RcNativeNodeSnapshot): List<RcNativeDrawCommand> =
      node.commands + node.children.flatMap(::commands)

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    val canvas = checkNotNull(find(snapshot.root, -6))
    val command = commands(canvas).single()

    assertEquals(50f, command.third)
    assertEquals(40f, command.fourth)
  }

  @Test
  fun fillFractionScalesAvailableGeometry() {
    val reference: (Int) -> RcFloatWord = { RcFloatWord(0x7fc00000 or it) }
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 80, modern = false),
        listOf(
          RcRootLayout(-2),
          RcLayoutContent(-3),
          RcBoxLayout(-4, 0, horizontalPositioning = 1, verticalPositioning = 4),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(80f)),
          RcLayoutContent(-5),
          RcCanvasLayout(-6, 0),
          RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(0.5f)),
          RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(0.25f)),
          RcLayoutContent(-7),
          RcComponentValue(RcComponentValue.WIDTH, componentId = -6, valueId = 42),
          RcComponentValue(RcComponentValue.HEIGHT, componentId = -6, valueId = 43),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            reference(42),
            reference(43),
          ),
          end,
          end,
          end,
          end,
          end,
          end,
        ),
      )

    fun commands(node: RcNativeNodeSnapshot): List<RcNativeDrawCommand> =
      node.commands + node.children.flatMap(::commands)

    val command =
      commands(RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document)).root).single()
    assertEquals(50f, command.third)
    assertEquals(20f, command.fourth)
  }

  @Test
  fun rowPublishesWeightedAllocationToEachChild() {
    val reference: (Int) -> RcFloatWord = { RcFloatWord(0x7fc00000 or it) }
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    fun weightedCanvas(componentId: Int, contentId: Int, valueId: Int) =
      listOf(
        RcCanvasLayout(componentId, 0),
        RcWidthModifier(RcDimensionType.WEIGHT, RcFloatWord.literal(1f)),
        RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(Float.NaN)),
        RcLayoutContent(contentId),
        RcComponentValue(RcComponentValue.WIDTH, componentId = componentId, valueId = valueId),
        RcDraw4(
          RcOpcodes.DRAW_RECT,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(0f),
          reference(valueId),
          RcFloatWord.literal(10f),
        ),
        end,
        end,
      )
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 40, modern = false),
        listOf(
          RcRootLayout(-2),
          RcLayoutContent(-3),
          RcRowLayout(
            -4,
            0,
            horizontalPositioning = 1,
            verticalPositioning = 4,
            spacedBy = RcFloatWord.literal(0f),
          ),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcLayoutContent(-5),
        ) + weightedCanvas(-6, -7, 42) + weightedCanvas(-8, -9, 43) + listOf(end, end, end, end),
      )
    fun find(node: RcNativeNodeSnapshot, componentId: Int): RcNativeNodeSnapshot? {
      if (node.componentId == componentId) return node
      node.children.forEach { child ->
        find(child, componentId)?.let {
          return it
        }
      }
      return null
    }
    fun commands(node: RcNativeNodeSnapshot): List<RcNativeDrawCommand> =
      node.commands + node.children.flatMap(::commands)

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    assertEquals(50f, commands(checkNotNull(find(snapshot.root, -6))).single().third)
    assertEquals(50f, commands(checkNotNull(find(snapshot.root, -8))).single().third)
  }

  @Test
  fun weightedAllocationSubtractsConstrainedFixedSibling() {
    val reference = RcFloatWord(0x7fc00000 or 42)
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 40, modern = false),
        listOf(
          RcRootLayout(-2),
          RcLayoutContent(-3),
          RcRowLayout(
            -4,
            0,
            horizontalPositioning = 1,
            verticalPositioning = 4,
            spacedBy = RcFloatWord.literal(0f),
          ),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcLayoutContent(-5),
          RcCanvasLayout(-6, 0),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(80f)),
          RcWidthInModifier(RcFloatWord.literal(-1f), RcFloatWord.literal(50f)),
          end,
          RcCanvasLayout(-7, 0),
          RcWidthModifier(RcDimensionType.WEIGHT, RcFloatWord.literal(1f)),
          RcLayoutContent(-8),
          RcComponentValue(RcComponentValue.WIDTH, componentId = -7, valueId = 42),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            reference,
            RcFloatWord.literal(10f),
          ),
          end,
          end,
          end,
          end,
          end,
          end,
        ),
      )

    fun commands(node: RcNativeNodeSnapshot): List<RcNativeDrawCommand> =
      node.commands + node.children.flatMap(::commands)

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    assertEquals(50f, commands(snapshot.root).single().third)
  }

  @Test
  fun weightedAllocationSubtractsFillSibling() {
    val reference = RcFloatWord(0x7fc00000 or 42)
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 40, modern = false),
        listOf(
          RcRootLayout(-2),
          RcLayoutContent(-3),
          RcRowLayout(
            -4,
            0,
            horizontalPositioning = 1,
            verticalPositioning = 4,
            spacedBy = RcFloatWord.literal(0f),
          ),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcLayoutContent(-5),
          RcCanvasLayout(-6, 0),
          RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(Float.NaN)),
          end,
          RcCanvasLayout(-7, 0),
          RcWidthModifier(RcDimensionType.WEIGHT, RcFloatWord.literal(1f)),
          RcLayoutContent(-8),
          RcComponentValue(RcComponentValue.WIDTH, componentId = -7, valueId = 42),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            reference,
            RcFloatWord.literal(10f),
          ),
          end,
          end,
          end,
          end,
          end,
          end,
        ),
      )

    fun commands(node: RcNativeNodeSnapshot): List<RcNativeDrawCommand> =
      node.commands + node.children.flatMap(::commands)

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    assertEquals(0f, commands(snapshot.root).single().third)
  }

  @Test
  fun settlesLaterSiblingGeometryBeforeResolvingEarlierCommands() {
    val reference = RcFloatWord(0x7fc00000 or 42)
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 40, modern = false),
        listOf(
          RcRootLayout(-2),
          RcLayoutContent(-3),
          RcRowLayout(
            -4,
            0,
            horizontalPositioning = 1,
            verticalPositioning = 4,
            spacedBy = RcFloatWord.literal(0f),
          ),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcLayoutContent(-5),
          RcCanvasLayout(-6, 0),
          RcLayoutContent(-7),
          RcComponentValue(RcComponentValue.WIDTH, componentId = -8, valueId = 42),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            reference,
            RcFloatWord.literal(10f),
          ),
          end,
          end,
          RcCanvasLayout(-8, 0),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(30f)),
          end,
          end,
          end,
          end,
          end,
        ),
      )

    fun commands(node: RcNativeNodeSnapshot): List<RcNativeDrawCommand> =
      node.commands + node.children.flatMap(::commands)

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    assertEquals(30f, commands(snapshot.root).single().third)
  }

  @Test
  fun publishesResolvedWidthWhenHeightRequiresIntrinsicMeasurement() {
    val reference = RcFloatWord(0x7fc00000 or 42)
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 40, modern = false),
        listOf(
          RcTextData(20, "label"),
          RcRootLayout(-2),
          RcLayoutContent(-3),
          RcTextLayout(
            componentId = -4,
            animationId = 0,
            textId = 20,
            color = 0xff000000.toInt(),
            fontSize = RcFloatWord.literal(12f),
            fontStyle = 0,
            fontWeight = RcFloatWord.literal(400f),
            fontFamilyId = 0,
            textAlignAndFlags = 1,
            overflow = 1,
            maxLines = 1,
          ),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(30f)),
          RcComponentValue(RcComponentValue.WIDTH, componentId = -4, valueId = 42),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            reference,
            RcFloatWord.literal(10f),
          ),
          end,
          end,
          end,
        ),
      )

    fun commands(node: RcNativeNodeSnapshot): List<RcNativeDrawCommand> =
      node.commands + node.children.flatMap(::commands)

    assertEquals(
      30f,
      commands(RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document)).root)
        .single { it.kind == RcNativeDrawCommand.RECT }
        .third,
    )
  }

  @Test
  fun settlesCurrentFrameSizeExpressionBeforePublishingSiblingGeometry() {
    val reference: (Int) -> RcFloatWord = { RcFloatWord(0x7fc00000 or it) }
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 40, modern = false),
        listOf(
          RcRootLayout(-2),
          RcLayoutContent(-3),
          RcRowLayout(-4, 0, 1, 4, RcFloatWord.literal(0f)),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcLayoutContent(-5),
          RcCanvasLayout(-6, 0),
          RcLayoutContent(-7),
          RcComponentValue(RcComponentValue.WIDTH, componentId = -8, valueId = 42),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            reference(42),
            RcFloatWord.literal(10f),
          ),
          end,
          end,
          RcCanvasLayout(-8, 0),
          RcFloatExpression(50, listOf(RcFloatWord.literal(30f)), null),
          RcWidthModifier(RcDimensionType.EXACT, reference(50)),
          end,
          end,
          end,
          end,
          end,
        ),
      )

    fun commands(node: RcNativeNodeSnapshot): List<RcNativeDrawCommand> =
      node.commands + node.children.flatMap(::commands)

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    assertEquals(30f, commands(snapshot.root).single().third)
  }

  @Test
  fun goneFixedSiblingDoesNotReduceWeightedAllocation() {
    val reference = RcFloatWord(0x7fc00000 or 42)
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 40, modern = false),
        listOf(
          RcIntegerConstant(20, 16),
          RcRootLayout(-2),
          RcLayoutContent(-3),
          RcRowLayout(-4, 0, 1, 4, RcFloatWord.literal(0f)),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcLayoutContent(-5),
          RcCanvasLayout(-6, 0),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(50f)),
          RcVisibilityModifier(20),
          end,
          RcCanvasLayout(-7, 0),
          RcWidthModifier(RcDimensionType.WEIGHT, RcFloatWord.literal(1f)),
          RcLayoutContent(-8),
          RcComponentValue(RcComponentValue.WIDTH, componentId = -7, valueId = 42),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            reference,
            RcFloatWord.literal(10f),
          ),
          end,
          end,
          end,
          end,
          end,
          end,
        ),
      )

    fun commands(node: RcNativeNodeSnapshot): List<RcNativeDrawCommand> =
      node.commands + node.children.flatMap(::commands)

    val snapshot = RcNativeSnapshotBridge.decode(RcDocumentCodec.encode(document))
    assertEquals(100f, commands(snapshot.root).single().third)
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

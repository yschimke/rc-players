package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcBitmapTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentStart
import ee.schimke.composeai.rcplayer.protocol.RcConditionalOperations
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRun
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRunOnPath
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapTextAnchored
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionDefine
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIdOperation
import ee.schimke.composeai.rcplayer.protocol.RcIncludeReferencedOperations
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcLoopOperation
import ee.schimke.composeai.rcplayer.protocol.RcMacroArgument
import ee.schimke.composeai.rcplayer.protocol.RcMacroBlock
import ee.schimke.composeai.rcplayer.protocol.RcMacroCall
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcMacroForEach
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcReferencedOperations
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRunAction
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTheme
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWireWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RcDocumentLinkerTest {
  private fun body(
    vararg operations: ee.schimke.composeai.rcplayer.protocol.RcOperation
  ): ByteArray =
    RcWireWriter()
      .apply {
        operations.forEach {
          ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec.encodeOperation(this, it)
        }
      }
      .toByteArray()

  @Test
  fun expandsMacroCallsWithIdRemappingAndOperationBlocks() {
    val idMacro = RcMacroDefine(9, listOf(100), body(RcIdOperation(RcOpcodes.DRAW_PATH, 100)))
    val blockMacro = RcMacroDefine(10, emptyList(), body(RcMacroArgument(0)))
    val operations =
      listOf(
        idMacro,
        blockMacro,
        RcMacroCall(9, listOf(200)),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcMacroCall(10, emptyList()),
        RcMacroBlock(0),
        RcTheme(RcTheme.DARK),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
      )

    val linked = RcDocumentLinker.link(RcDocument(header, operations)).operations

    assertEquals(
      200,
      assertIs<RcIdOperation>(assertIs<RcLinkedNode.Operation>(linked[0]).operation).id,
    )
    assertEquals(
      RcTheme.DARK,
      assertIs<RcTheme>(assertIs<RcLinkedNode.Operation>(linked[1]).operation).theme,
    )
  }

  @Test
  fun repeatedMacroCallsAllocateDistinctIdsForLocalDeclarations() {
    val definition = RcMacroDefine(9, emptyList(), body(RcTextData(100, "local")))
    val operations =
      listOf(
        definition,
        RcMacroCall(9, emptyList()),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcMacroCall(9, emptyList()),
        RcNoArg(RcOpcodes.CONTAINER_END),
      )

    val ids =
      RcDocumentLinker.link(RcDocument(header, operations)).operations.map {
        assertIs<RcTextData>(assertIs<RcLinkedNode.Operation>(it).operation).id
      }

    assertEquals(2, ids.distinct().size)
  }

  @Test
  fun remapsFlaggedBitmapFontIdsInsideMacroBodies() {
    val spacing = RcFloatWord.literal(1f)
    val definition =
      RcMacroDefine(
        9,
        listOf(100),
        body(
          RcDrawBitmapFontTextRun(
            100,
            40,
            0,
            -1,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            spacing,
          ),
          RcDrawBitmapFontTextRunOnPath(
            100,
            40,
            60,
            0,
            -1,
            RcFloatWord.literal(0f),
            spacing,
          ),
          RcBitmapTextMeasure(100, 41, 40, RcBitmapTextMeasure.MEASURE_WIDTH, spacing),
          RcDrawBitmapTextAnchored(
            100,
            40,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(-1f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            spacing,
          ),
        ),
      )
    val linked =
      RcDocumentLinker.link(
          RcDocument(
            header,
            listOf(
              definition,
              RcMacroCall(9, listOf(200)),
              RcNoArg(RcOpcodes.CONTAINER_END),
            ),
          )
        )
        .operations
        .map { assertIs<RcLinkedNode.Operation>(it).operation }

    assertEquals(200, assertIs<RcDrawBitmapFontTextRun>(linked[0]).textId)
    assertEquals(200, assertIs<RcDrawBitmapFontTextRunOnPath>(linked[1]).textId)
    assertEquals(200, assertIs<RcBitmapTextMeasure>(linked[2]).outId)
    assertEquals(200, assertIs<RcDrawBitmapTextAnchored>(linked[3]).textId)
  }

  @Test
  fun macroForEachExpandsAndRemapsEveryCollectionItem() {
    val operations =
      listOf(
        RcIdList(1, listOf(10, 11)),
        RcMacroForEach(1, 100),
        RcIdOperation(RcOpcodes.DRAW_PATH, 100),
        RcNoArg(RcOpcodes.CONTAINER_END),
      )

    val linked = RcDocumentLinker.link(RcDocument(header, operations)).operations

    assertEquals(
      listOf(10, 11),
      linked.drop(1).map {
        assertIs<RcIdOperation>(assertIs<RcLinkedNode.Operation>(it).operation).id
      },
    )
  }

  @Test
  fun rejectsRecursiveMacrosAndArgumentMismatches() {
    val recursiveBody = body(RcMacroCall(9, emptyList()), RcNoArg(RcOpcodes.CONTAINER_END))
    assertFailsWith<RcLinkException> {
      RcDocumentLinker.link(
        RcDocument(
          header,
          listOf(
            RcMacroDefine(9, emptyList(), recursiveBody),
            RcMacroCall(9, emptyList()),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
        )
      )
    }
    assertFailsWith<RcLinkException> {
      RcDocumentLinker.link(
        RcDocument(
          header,
          listOf(
            RcMacroDefine(9, listOf(1), body(RcTheme(RcTheme.LIGHT))),
            RcMacroCall(9, emptyList()),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
        )
      )
    }
  }

  @Test
  fun componentStartPreservesItsExecutableChildren() {
    val body = RcTheme(RcTheme.DARK)
    val component = RcComponentStart(2, 41, RcFloatWord.literal(100f), RcFloatWord.literal(50f))

    val linked =
      RcDocumentLinker.link(
        RcDocument(header, listOf(component, body, RcNoArg(RcOpcodes.CONTAINER_END)))
      )

    assertEquals(body, assertIs<RcLinkedNode.Operation>(linked.operations.single()).operation)
  }

  @Test
  fun expandsReferencedOperationsAndRemovesDefinitions() {
    val body = RcTheme(RcTheme.DARK)
    val document =
      RcDocument(
        header,
        listOf(
          RcReferencedOperations(7),
          body,
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcIncludeReferencedOperations(7),
        ),
      )

    val linked = RcDocumentLinker.link(document)

    assertEquals(body, assertIs<RcLinkedNode.Operation>(linked.operations.single()).operation)
  }

  @Test
  fun expandsNestedReferences() {
    val body = RcTheme(RcTheme.LIGHT)
    val operations =
      listOf(
        RcReferencedOperations(1),
        RcIncludeReferencedOperations(2),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcReferencedOperations(2),
        body,
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcIncludeReferencedOperations(1),
      )

    val linked = RcDocumentLinker.link(RcDocument(header, operations))

    assertEquals(body, assertIs<RcLinkedNode.Operation>(linked.operations.single()).operation)
  }

  @Test
  fun rejectsMissingCyclicAndOverlyDeepReferences() {
    assertFailsWith<RcLinkException> {
      RcDocumentLinker.link(RcDocument(header, listOf(RcIncludeReferencedOperations(1))))
    }
    val cycle =
      listOf(
        RcReferencedOperations(1),
        RcIncludeReferencedOperations(2),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcReferencedOperations(2),
        RcIncludeReferencedOperations(1),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcIncludeReferencedOperations(1),
      )
    assertFailsWith<RcLinkException> { RcDocumentLinker.link(RcDocument(header, cycle)) }

    val deep = buildList {
      repeat(66) { index ->
        add(RcReferencedOperations(index))
        add(RcIncludeReferencedOperations(index + 1))
        add(RcNoArg(RcOpcodes.CONTAINER_END))
      }
      add(RcReferencedOperations(66))
      add(RcTheme(RcTheme.LIGHT))
      add(RcNoArg(RcOpcodes.CONTAINER_END))
      add(RcIncludeReferencedOperations(0))
    }
    assertFailsWith<RcLinkException> { RcDocumentLinker.link(RcDocument(header, deep)) }
  }

  @Test
  fun capsExpandedReferenceNodeCount() {
    val operations = buildList {
      add(RcReferencedOperations(1))
      repeat(50_001) { add(RcTheme(RcTheme.LIGHT)) }
      add(RcNoArg(RcOpcodes.CONTAINER_END))
      add(RcIncludeReferencedOperations(1))
      add(RcIncludeReferencedOperations(1))
    }

    assertFailsWith<RcLinkException> { RcDocumentLinker.link(RcDocument(header, operations)) }
  }

  @Test
  fun linksNestedConditionalAndLoopBodiesImmutably() {
    val conditional =
      RcConditionalOperations(
        RcConditionalOperations.EQUAL,
        RcFloatWord.literal(1f),
        RcFloatWord.literal(1f),
      )
    val loop =
      RcLoopOperation(20, RcFloatWord.literal(0f), RcFloatWord.literal(1f), RcFloatWord.literal(3f))
    val body = RcFloatConstant(30, RcFloatWord.literal(7f))
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val document = RcDocument(header, listOf(conditional, loop, body, end, end))

    val outer =
      assertIs<RcLinkedNode.Container>(RcDocumentLinker.link(document).operations.single())
    val inner = assertIs<RcLinkedNode.Container>(outer.children.single())

    assertEquals(conditional, outer.operation)
    assertEquals(loop, inner.operation)
    assertEquals(body, assertIs<RcLinkedNode.Operation>(inner.children.single()).operation)
  }

  @Test
  fun linksRunActionAsAnImmutableActionContainer() {
    val action = RcHostAction(77)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(RcRunAction, action, RcNoArg(RcOpcodes.CONTAINER_END)),
      )

    val container =
      assertIs<RcLinkedNode.Container>(RcDocumentLinker.link(document).operations.single())

    assertEquals(RcRunAction, container.operation)
    assertEquals(action, assertIs<RcLinkedNode.Operation>(container.children.single()).operation)
  }

  @Test
  fun linksTheFoundationalLayoutTreeAsImmutableContainers() {
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(
          RcRootLayout(1),
          RcLayoutContent(2),
          RcCanvasLayout(3, 30),
          RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
          RcIdOperation(RcOpcodes.DRAW_PATH, 8),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )

    val root = assertIs<RcLinkedNode.Container>(RcDocumentLinker.link(document).operations.single())
    val content = assertIs<RcLinkedNode.Container>(root.children.single())
    val canvas = assertIs<RcLinkedNode.Container>(content.children.single())
    val operations = assertIs<RcLinkedNode.Container>(canvas.children.single())

    assertIs<RcRootLayout>(root.operation)
    assertIs<RcLayoutContent>(content.operation)
    assertIs<RcCanvasLayout>(canvas.operation)
    assertEquals(RcOpcodes.CANVAS_OPERATIONS, operations.operation.opcode)
  }

  @Test
  fun linksFloatFunctionBodiesWithoutExecutingThemAtTheRoot() {
    val definition = RcFloatFunctionDefine(40, listOf(7, 8))
    val body = RcFloatConstant(9, RcFloatWord.literal(3f))
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        listOf(definition, body, RcNoArg(RcOpcodes.CONTAINER_END)),
      )

    val container =
      assertIs<RcLinkedNode.Container>(RcDocumentLinker.link(document).operations.single())

    assertEquals(definition, container.operation)
    assertEquals(body, assertIs<RcLinkedNode.Operation>(container.children.single()).operation)
  }

  private val header = RcHeader(RcVersion(0, 1, 0), modern = false)

  @Test
  fun nestsCanvasOperationsWithoutChangingTheSourceDocument() {
    val operations =
      listOf(
        RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
        RcIdOperation(RcOpcodes.DRAW_PATH, 8),
        RcNoArg(RcOpcodes.CONTAINER_END),
      )
    val document = RcDocument(header, operations)

    val linked = RcDocumentLinker.link(document)
    val container = assertIs<RcLinkedNode.Container>(linked.operations.single())

    assertEquals(RcOpcodes.CANVAS_OPERATIONS, container.operation.opcode)
    assertEquals(
      RcOpcodes.DRAW_PATH,
      assertIs<RcLinkedNode.Operation>(container.children.single()).operation.opcode,
    )
    assertEquals(operations, document.operations)
  }

  @Test
  fun rejectsUnmatchedAndUnclosedContainers() {
    assertFailsWith<RcLinkException> {
      RcDocumentLinker.link(RcDocument(header, listOf(RcNoArg(RcOpcodes.CONTAINER_END))))
    }
    assertFailsWith<RcLinkException> {
      RcDocumentLinker.link(RcDocument(header, listOf(RcNoArg(RcOpcodes.CANVAS_OPERATIONS))))
    }
  }
}

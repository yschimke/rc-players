package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcConditionalOperations
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextSubtext
import ee.schimke.composeai.rcplayer.protocol.RcTextTransform
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLayoutNode
import ee.schimke.composeai.rcplayer.runtime.RcLayoutTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RcCollapseStateLayoutsTest {
  private fun stateDocument(
    vararg before: RcOperation,
    indexId: Int = INDEX,
    after: List<RcOperation> = emptyList(),
  ) =
    document(
      *before,
      *rootLayout(
          RcStateLayout(10, -1, 0, 0, indexId),
          RcLayoutContent(9),
          *box(20, RcTextData(100, "zero")).toTypedArray(),
          *box(30, RcTextData(101, "one")).toTypedArray(),
          *box(40, RcTextData(102, "two"), RcComponentValue(RcComponentValue.WIDTH, 40, 200))
            .toTypedArray(),
          END,
          END,
        )
        .toTypedArray(),
      *after.toTypedArray(),
    )

  @Test
  fun aConstantIndexKeepsOnlyTheSelectedBranch() {
    val source =
      stateDocument(
        RcIntegerConstant(INDEX, 1),
        after = listOf(RcComponentValue(RcComponentValue.HEIGHT, 20, 201)),
      )

    val result = assertRoundTrips(source.collapseConstantStateLayouts())

    assertEquals(
      listOf(
        RcIntegerConstant(INDEX, 1),
        *rootLayout(
            // Branch 0's definitions stay before the kept branch and branch 2's after it, so
            // whichever defines an id last still does. The width a removed branch published is now
            // the zero a hidden branch publishes.
            RcTextData(100, "zero"),
            RcStateLayout(10, -1, 0, 0, indexId = 0),
            RcLayoutContent(9),
            *box(30, RcTextData(101, "one")).toTypedArray(),
            END,
            END,
            RcTextData(102, "two"),
            RcFloatConstant(200, RcFloatWord.literal(0f)),
          )
          .toTypedArray(),
        RcFloatConstant(201, RcFloatWord.literal(0f)),
      ),
      result.operations,
    )

    // And the player still reads it: one state layout, one branch, the selected one.
    assertEquals(listOf(30), shownBranches(result))
  }

  @Test
  fun anIndexComputedFromConstantsFoldsAsThePlayerWouldComputeIt() {
    // 1 + 1, the id marked in slot 0 and the operator in slot 2.
    val expression =
      RcIntegerExpression(INDEX, mask = 0b101, values = listOf(SOURCE, 1, RcIntegerExpression.ADD))
    val result =
      stateDocument(RcIntegerConstant(SOURCE, 1), expression).collapseConstantStateLayouts()
    assertEquals(listOf(40), shownBranches(result))
  }

  @Test
  fun aHostBoundIndexStaysUntilItIsHardcoded() {
    val source =
      stateDocument(
        RcNamedVariable(INDEX, RcNamedVariable.INT_TYPE, "USER:tab"),
        RcIntegerConstant(INDEX, 0),
      )
    assertSame(source, source.collapseConstantStateLayouts())

    val result =
      source
        .hardcodeNamedValues(mapOf("tab" to RcHardcodedValue.Integer(2)))
        .collapseConstantStateLayouts()
    assertEquals(listOf(40), shownBranches(result))
  }

  @Test
  fun anIndexAnActionWritesIsNotConstant() {
    val source = stateDocument(RcIntegerConstant(INDEX, 1), RcValueIntegerChangeAction(INDEX, 2))
    assertSame(source, source.collapseConstantStateLayouts())
  }

  @Test
  fun anOutOfRangeIndexIsLeftForThePlayerToInterpret() {
    val source = stateDocument(RcIntegerConstant(INDEX, 5))
    assertSame(source, source.collapseConstantStateLayouts())
  }

  private fun branches(
    selected: Int,
    vararg branches: List<RcOperation>,
    after: List<RcOperation> = emptyList(),
  ) =
    document(
      RcIntegerConstant(INDEX, selected),
      *rootLayout(
          RcStateLayout(10, -1, 0, 0, INDEX),
          RcLayoutContent(9),
          *branches.toList().flatten().toTypedArray(),
          END,
          END,
        )
        .toTypedArray(),
      *after.toTypedArray(),
    )

  private val one = RcFloatWord.literal(1f)
  private val zero = RcFloatWord.literal(0f)

  @Test
  fun everyValueTheTextProducersDefineIsKept() {
    val producers =
      listOf(
        RcTextFromFloat(103, one, 1, 1, 0),
        RcTextSubtext(104, 100, zero, one),
        RcTextTransform(105, 100, zero, one, 0),
      )
    val result =
      branches(0, box(20), box(30, *producers.toTypedArray())).collapseConstantStateLayouts()
    assertEquals(producers, result.operations.filter { it in producers })
  }

  @Test
  fun aLaterBranchStillWinsAnIdItRedefines() {
    val result =
      branches(0, box(20, RcTextData(100, "a")), box(30, RcTextData(100, "b")))
        .collapseConstantStateLayouts()
    assertEquals("b", result.operations.filterIsInstance<RcTextData>().last { it.id == 100 }.text)
  }

  @Test
  fun aConditionalValueIsDroppedOnlyWhenNothingOutsideReadsIt() {
    val conditional =
      box(
        30,
        RcConditionalOperations(RcConditionalOperations.EQUAL, zero, zero),
        RcFloatExpression(300, listOf(one), animation = null),
        END,
      )
    val reader = RcFloatExpression(301, listOf(RcFloatWord(0x7fc00000 or 300)), animation = null)

    val read = branches(0, box(20), conditional, after = listOf(reader))
    assertSame(read, read.collapseConstantStateLayouts())

    val unread = branches(0, box(20), conditional).collapseConstantStateLayouts()
    assertTrue(unread.operations.filterIsInstance<RcStateLayout>().single().indexId == 0)
    assertTrue(unread.operations.none { it is RcFloatExpression })
  }

  /** The component ids of the branches the player's layout tree gives the one state layout. */
  private fun shownBranches(document: RcDocument): List<Int> {
    val root = RcLayoutTree.build(RcDocumentLinker.link(document))!!
    val content = root.children.single() as RcLayoutNode.Content
    val state = content.children.single() as RcLayoutNode.State
    return state.content.children.map { it.componentId }
  }

  private companion object {
    const val INDEX = 60
    const val SOURCE = 61
  }
}

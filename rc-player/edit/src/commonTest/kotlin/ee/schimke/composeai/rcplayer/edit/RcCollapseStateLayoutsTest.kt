package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontData
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcConditionalOperations
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcLoopOperation
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcPathCreate
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyle
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcTheme
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLayoutNode
import ee.schimke.composeai.rcplayer.runtime.RcLayoutTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RcCollapseStateLayoutsTest {
  private val zero = RcFloatWord.literal(0f)
  private val one = RcFloatWord.literal(1f)

  /** A state layout over three empty boxes, its index id [INDEX]. */
  private fun stateDocument(vararg before: RcOperation, after: List<RcOperation> = emptyList()) =
    branches(null, box(20), box(30), box(40), before = before.toList(), after = after)

  /** A state layout over [branches]; [selected] becomes the index constant when not null. */
  private fun branches(
    selected: Int?,
    vararg branches: List<RcOperation>,
    before: List<RcOperation> = emptyList(),
    after: List<RcOperation> = emptyList(),
  ) =
    document(
      *listOfNotNull(selected?.let { RcIntegerConstant(INDEX, it) }).toTypedArray(),
      *before.toTypedArray(),
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

  /** The component ids of the branches the player's layout tree gives the one state layout. */
  private fun shownBranches(document: RcDocument): List<Int> {
    val root = RcLayoutTree.build(RcDocumentLinker.link(document))!!
    val content = root.children.single() as RcLayoutNode.Content
    val state = content.children.single() as RcLayoutNode.State
    return state.content.children.map { it.componentId }
  }

  @Test
  fun aConstantIndexKeepsOnlyTheSelectedBranch() {
    val source =
      stateDocument(
        RcIntegerConstant(INDEX, 1),
        after =
          listOf(
            RcComponentValue(RcComponentValue.HEIGHT, 20, 201),
            RcComponentValue(RcComponentValue.WIDTH, 30, 202),
          ),
      )

    val result = assertRoundTrips(source.collapseConstantStateLayouts())

    assertEquals(
      listOf(
        RcIntegerConstant(INDEX, 1),
        *rootLayout(
            RcStateLayout(10, -1, 0, 0, indexId = 0),
            RcLayoutContent(9),
            *box(30).toTypedArray(),
            END,
            END,
          )
          .toTypedArray(),
        // A removed component publishes the zero a hidden branch does; the kept one still measures.
        RcFloatConstant(201, zero),
        RcComponentValue(RcComponentValue.WIDTH, 30, 202),
      ),
      result.operations,
    )
    assertEquals(listOf(30), shownBranches(result))
  }

  @Test
  fun aBranchOfLayoutModifiersDrawingAndHandlersIsRemoved() {
    val local =
      box(
        30,
        RcPaddingModifier(one, one, one, one),
        RcClickModifier,
        RcHostAction(5),
        RcValueIntegerChangeAction(70, 1),
        END,
        RcDraw4(RcOpcodes.DRAW_RECT, zero, zero, one, one),
      )
    val result = branches(0, box(20), local).collapseConstantStateLayouts()
    assertEquals(listOf(20), shownBranches(result))
  }

  @Test
  fun aBranchHoldingAnythingElseIsNotCollapsed() {
    val blockers: List<List<RcOperation>> =
      listOf(
        listOf(RcTextData(100, "hidden")),
        listOf(RcTextStyle(listOf(RcTextStyleProperty.IntValue(1, 5)))),
        listOf(RcBitmapFontData(300, emptyList())),
        listOf(RcFloatExpression(301, listOf(one), animation = null)),
        listOf(RcPathCreate(302, zero, zero)),
        listOf(RcTheme(RcTheme.DARK)),
        listOf(RcConditionalOperations(RcConditionalOperations.EQUAL, zero, zero), END),
        // An action outside a handler runs as the branch draws, not on touch.
        listOf(RcValueIntegerChangeAction(70, 1)),
      )
    blockers.forEach { content ->
      val source = branches(0, box(20), box(30, *content.toTypedArray()))
      assertSame(source, source.collapseConstantStateLayouts(), content.toString())
    }
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
  fun anIndexALoopWritesIsNotConstant() {
    val source =
      stateDocument(
        RcIntegerConstant(INDEX, 1),
        RcLoopOperation(INDEX, zero, one, RcFloatWord.literal(3f)),
        END,
      )
    assertSame(source, source.collapseConstantStateLayouts())
  }

  @Test
  fun anIndexWrittenOnlyUnderAConditionIsNotConstant() {
    val source =
      stateDocument(
        RcConditionalOperations(RcConditionalOperations.EQUAL, zero, one),
        RcIntegerConstant(INDEX, 1),
        END,
      )
    assertSame(source, source.collapseConstantStateLayouts())
  }

  @Test
  fun anIndexWrittenOnlyUnderOneThemeIsNotConstant() {
    val source =
      stateDocument(
        RcTheme(RcTheme.DARK),
        RcIntegerConstant(INDEX, 1),
        RcTheme(RcTheme.UNSPECIFIED),
      )
    assertSame(source, source.collapseConstantStateLayouts())
  }

  @Test
  fun aNestedThemeResetDoesNotUngateItsParentScope() {
    // DARK at the root; a nested content block resets to UNSPECIFIED, which stops at its end.
    val source =
      stateDocument(
        RcTheme(RcTheme.DARK),
        *box(80, RcTheme(RcTheme.UNSPECIFIED)).toTypedArray(),
        RcIntegerConstant(INDEX, 1),
      )
    assertSame(source, source.collapseConstantStateLayouts())
  }

  @Test
  fun anOutOfRangeIndexIsLeftForThePlayerToInterpret() {
    val source = stateDocument(RcIntegerConstant(INDEX, 5))
    assertSame(source, source.collapseConstantStateLayouts())
  }

  private companion object {
    const val INDEX = 60
    const val SOURCE = 61
  }
}

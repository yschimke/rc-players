package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcVisibilityModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthInModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RcHardcodeComponentSizesTest {
  private fun word(value: Float) = RcFloatWord.literal(value)

  private val fill =
    listOf(
      RcWidthModifier(RcDimensionType.FILL, word(1f)),
      RcHeightModifier(RcDimensionType.FILL, word(1f)),
    )

  private fun padding(all: Float) = RcPaddingModifier(word(all), word(all), word(all), word(all))

  /**
   * Box [id] with [modifiers], publishing its width and height into [id] + 1 and [id] + 2, around
   * [content].
   */
  private fun measured(
    id: Int,
    modifiers: List<RcOperation>,
    vararg content: RcOperation,
  ): List<RcOperation> =
    listOf(
      RcBoxLayout(id, -1, 0, 0),
      *modifiers.toTypedArray(),
      RcComponentValue(RcComponentValue.WIDTH, id, id + 1),
      RcComponentValue(RcComponentValue.HEIGHT, id, id + 2),
      RcLayoutContent(id - 1),
      *content,
      END,
      END,
    )

  private fun RcDocument.sizeOf(id: Int): Pair<Float?, Float?> {
    fun value(valueId: Int) =
      operations.filterIsInstance<RcFloatConstant>().firstOrNull { it.id == valueId }?.value?.value
    return value(id + 1) to value(id + 2)
  }

  @Test
  fun aFillChainIsTheRootLessEachPadding() {
    val source =
      document(
        *rootLayout(
            *measured(100, fill + padding(10f), *measured(200, fill).toTypedArray()).toTypedArray(),
          )
          .toTypedArray(),
      )
    val sized = source.hardcodeComponentSizes(400f, 200f)
    assertEquals(400f to 200f, sized.sizeOf(100))
    assertEquals(380f to 180f, sized.sizeOf(200), "inside the outer box's padding")
  }

  @Test
  fun aWrappingBoxTakesWhatItIsOfferedWhenItsChildrenFillIt() {
    val source =
      document(
        *rootLayout(*measured(100, emptyList(), *measured(200, fill).toTypedArray()).toTypedArray())
          .toTypedArray(),
      )
    assertEquals(400f to 200f, source.hardcodeComponentSizes(400f, 200f).sizeOf(100))
  }

  @Test
  fun whatTheLayoutLeavesInDoubtStaysMeasured() {
    // A wrapping box around nothing that fills it: its size is its content's.
    val wraps = document(*rootLayout(*measured(100, emptyList()).toTypedArray()).toTypedArray())
    assertSame(wraps, wraps.hardcodeComponentSizes(400f, 200f))
    // Padding before the size modifier is an outer margin: not modelled.
    val margin =
      document(
        *rootLayout(*measured(100, listOf(padding(10f)) + fill).toTypedArray()).toTypedArray(),
      )
    assertSame(margin, margin.hardcodeComponentSizes(400f, 200f))
    // A modifier that may change the size (here widthIn) leaves the whole component.
    val bounded =
      document(
        *rootLayout(*measured(100, fill + RcWidthInModifier(word(0f), word(100f))).toTypedArray())
          .toTypedArray(),
      )
    assertSame(bounded, bounded.hardcodeComponentSizes(400f, 200f))
  }

  @Test
  fun aBreakpointCollapsesOnceItsSizeIsKnown() {
    // index = width >= 300 ? 1 : 0, folded to a float, as a size breakpoint's comparison is.
    val index = 300
    val source =
      document(
        *rootLayout(
            *measured(
                100,
                fill,
                RcFloatConstant(index, word(1f)),
                RcStateLayout(10, -1, 0, 0, index),
                RcLayoutContent(9),
                *box(20).toTypedArray(),
                *box(30).toTypedArray(),
                END,
                END,
              )
              .toTypedArray(),
          )
          .toTypedArray(),
      )
    val collapsed = source.collapseConstantStateLayouts()
    assertEquals(
      listOf(10),
      collapsed.operations.filterIsInstance<RcStateLayout>().map { it.componentId },
    )
    assertEquals(
      listOf(0),
      collapsed.operations.filterIsInstance<RcStateLayout>().map { it.indexId },
      "the float-written index read as the integer 1",
    )
    assertEquals(
      listOf(100, 30),
      collapsed.operations.filterIsInstance<RcBoxLayout>().map { it.componentId },
    )
  }

  /**
   * State layout [id] with [modifiers], publishing its size into [id] + 1 and [id] + 2, over
   * [branches].
   */
  private fun measuredState(
    id: Int,
    modifiers: List<RcOperation>,
    vararg branches: List<RcOperation>,
  ): List<RcOperation> =
    listOf(
      RcIntegerConstant(id + 3, 0),
      RcStateLayout(id, -1, 0, 0, id + 3),
      *modifiers.toTypedArray(),
      RcComponentValue(RcComponentValue.WIDTH, id, id + 1),
      RcComponentValue(RcComponentValue.HEIGHT, id, id + 2),
      RcLayoutContent(id - 1),
      *branches.toList().flatten().toTypedArray(),
      END,
      END,
    )

  @Test
  fun aStateLayoutIsItsBranchesSizeWhateverItsOwnModifiersSay() {
    // Filling, around a branch that doesn't fill: the player sizes it to the branch, so it stays
    // measured.
    val around =
      document(*rootLayout(*measuredState(100, fill, box(20)).toTypedArray()).toTypedArray())
    assertSame(around, around.hardcodeComponentSizes(400f, 200f))
    // Its own padding takes nothing from its branches: a filling branch is the whole space offered.
    val padded =
      document(
        *rootLayout(*measuredState(100, fill + padding(10f), measured(200, fill)).toTypedArray())
          .toTypedArray(),
      )
    val sized = padded.hardcodeComponentSizes(400f, 200f)
    assertEquals(400f to 200f, sized.sizeOf(100))
    assertEquals(400f to 200f, sized.sizeOf(200))
  }

  @Test
  fun aStateLayoutsOtherConstraintsLeaveItMeasured() {
    // The player drops a state layout's size and padding modifiers but still applies a widthIn.
    val bounded =
      document(
        *rootLayout(
            *measuredState(100, fill + RcWidthInModifier(word(0f), word(100f)), measured(200, fill))
              .toTypedArray(),
          )
          .toTypedArray(),
      )
    assertSame(bounded, bounded.hardcodeComponentSizes(400f, 200f))
  }

  @Test
  fun aComponentThatMayBeGoneStaysMeasured() {
    // GONE measures 0 × 0; a visibility a host can change could be GONE.
    val bound =
      document(
        RcNamedVariable(50, RcNamedVariable.INT_TYPE, "USER:shown"),
        RcIntegerConstant(50, 1),
        *rootLayout(*measured(100, fill + RcVisibilityModifier(50)).toTypedArray()).toTypedArray(),
      )
    assertSame(bound, bound.hardcodeComponentSizes(400f, 200f))
    // Constantly visible keeps its size.
    val visible =
      document(
        RcIntegerConstant(50, 1),
        *rootLayout(*measured(100, fill + RcVisibilityModifier(50)).toTypedArray()).toTypedArray(),
      )
    assertEquals(400f to 200f, visible.hardcodeComponentSizes(400f, 200f).sizeOf(100))
  }

  @Test
  fun anExactSizeStaysMeasured() {
    // The player clamps it to its parent's constraints, which this doesn't model.
    val exact =
      document(
        *rootLayout(
            *measured(
                100,
                listOf(
                  RcWidthModifier(RcDimensionType.EXACT, word(500f)),
                  RcHeightModifier(RcDimensionType.EXACT, word(50f)),
                ),
              )
              .toTypedArray(),
          )
          .toTypedArray(),
      )
    assertSame(exact, exact.hardcodeComponentSizes(400f, 200f))
  }

  /** State layout 100 over [branches], its index [indexId] written by [index]. */
  private fun stateOver(
    index: List<RcOperation>,
    indexId: Int,
    vararg branches: List<RcOperation>,
  ) =
    document(
      *index.toTypedArray(),
      *rootLayout(
          RcStateLayout(100, -1, 0, 0, indexId),
          RcLayoutContent(99),
          *branches.toList().flatten().toTypedArray(),
          END,
          END,
        )
        .toTypedArray(),
    )

  @Test
  fun onlyTheSelectedBranchIsOfferedTheSpace() {
    // The other branches publish 0 × 0: a host-bound index leaves every branch measured.
    val bound =
      stateOver(
        listOf(RcNamedVariable(60, RcNamedVariable.INT_TYPE, "USER:tab"), RcIntegerConstant(60, 1)),
        60,
        measured(200, fill),
        measured(300, fill),
      )
    assertSame(bound, bound.hardcodeComponentSizes(400f, 200f))
    // A constant index resolves its branch alone.
    val fixed =
      stateOver(listOf(RcIntegerConstant(60, 1)), 60, measured(200, fill), measured(300, fill))
    val sized = fixed.hardcodeComponentSizes(400f, 200f)
    assertEquals(null to null, sized.sizeOf(200))
    assertEquals(400f to 200f, sized.sizeOf(300))
  }

  @Test
  fun aStateLayoutThatMayBeGoneDoesNotFillItsParent() {
    // A wrapping box around a GONE-able state layout whose branch fills: the box may measure 0.
    val source =
      document(
        RcNamedVariable(50, RcNamedVariable.INT_TYPE, "USER:shown"),
        RcIntegerConstant(50, 1),
        RcIntegerConstant(61, 0),
        *rootLayout(
            *measured(
                100,
                emptyList(),
                RcStateLayout(10, -1, 0, 0, 61),
                RcVisibilityModifier(50),
                RcLayoutContent(9),
                *box(20).toTypedArray(),
                END,
                END,
              )
              .toTypedArray(),
          )
          .toTypedArray(),
      )
    assertSame(source, source.hardcodeComponentSizes(400f, 200f))
  }
}

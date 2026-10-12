package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.runtime.RcFloatExpressionEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RcCombineStatesTest {
  private fun text(componentId: Int, textId: Int) =
    listOf(
      RcTextLayout(
        componentId,
        -1,
        textId,
        0xff000000.toInt(),
        RcFloatWord.literal(14f),
        0,
        RcFloatWord.literal(400f),
        -1,
        1,
        1,
        1,
      ),
      END,
    )

  private fun image(componentId: Int, bitmapId: Int) =
    listOf(RcImageLayout(componentId, -1, bitmapId, 1, RcFloatWord.literal(1f)), END)

  private fun bitmap(id: Int) = RcBitmapData(id, 1, 1, 0, 0, byteArrayOf(1, 2, 3, 4))

  private fun screen(vararg operations: RcOperation, content: List<RcOperation>): RcDocument =
    document(*operations, *rootLayout(*content.toTypedArray()).toTypedArray())

  private val home =
    screen(
      RcTextData(42, "Inbox"),
      RcTextData(43, "3 unread"),
      bitmap(44),
      content = text(-10, 42) + text(-11, 43) + image(-12, 44),
    )

  private val detail =
    screen(
      RcTextData(42, "Inbox"),
      RcTextData(43, "From Ada"),
      bitmap(45),
      content = text(-10, 42) + image(-12, 45) + text(-11, 43),
    )

  @Test
  fun bothScreensBecomeTheBranchesOfOneStateLayoutAHostSwitches() {
    val combined = home.combineAsStates(detail, stateName = "screen")
    val operations = assertRoundTrips(combined.document).operations

    val state = operations.filterIsInstance<RcStateLayout>().single()
    val name = operations.filterIsInstance<RcNamedVariable>().single()
    assertEquals(RcNamedVariable(state.indexId, RcNamedVariable.INT_TYPE, "USER:screen"), name)
    assertTrue(RcIntegerConstant(state.indexId, 0) in operations, "the first screen shows first")
  }

  @Test
  fun identicalDefinitionsAreCarriedOnceAndEverythingElseMovesClear() {
    val combined = home.combineAsStates(detail)
    val operations = combined.document.operations

    // "Inbox" and the 1x1 bitmap are the same on both screens; "From Ada" is not.
    assertEquals(2, combined.sharedDefinitions)
    assertEquals(1, operations.count { it == RcTextData(42, "Inbox") })
    assertEquals(1, operations.count { it is RcBitmapData })
    val fromAda = operations.filterIsInstance<RcTextData>().single { it.text == "From Ada" }
    assertTrue(fromAda.id !in listOf(42, 43, 44), "the second screen's own text moved clear")
    val ids =
      operations.filterNot { it is RcNamedVariable }.mapNotNull { it.definedIds()?.singleOrNull() }
    assertEquals(ids.size, ids.toSet().size, "no id is defined twice")
  }

  @Test
  fun theSameTextAndImageOnBothScreensMoveBetweenTheirPositions() {
    val combined = home.combineAsStates(detail)
    val operations = combined.document.operations

    // The title and the image appear once on each screen; "3 unread" and "From Ada" differ.
    assertEquals(2, combined.sharedElements.size)
    combined.sharedElements.forEach { element ->
      val animated = operations.filter { it.animationIdOf() == element.animationId }
      assertEquals(2, animated.size, "one component on each screen carries ${element.animationId}")
    }
    val title = operations.filterIsInstance<RcTextLayout>().filter { it.textId == 42 }
    assertEquals(1, title.map { it.animationId }.distinct().size)
  }

  @Test
  fun explicitPairsMatchComponentsWhoseContentDiffers() {
    val combined =
      home.combineAsStates(detail, matches = listOf(-11 to -11), matchByContent = false)
    val element = combined.sharedElements.single()
    val texts = combined.document.operations.filterIsInstance<RcTextLayout>()
    val first = texts.single { it.componentId == element.firstComponentId }
    val second = texts.single { it.componentId == element.secondComponentId }
    assertEquals(element.animationId, first.animationId)
    assertEquals(element.animationId, second.animationId)
    assertTrue(first.textId != second.textId, "the two texts differ, the pairing was asked for")
  }

  @Test
  fun aNamedValueBothScreensDeclareIsOneBinding() {
    val withProgress = { label: String ->
      screen(
        RcFloatConstant(50, RcFloatWord.literal(0f)),
        RcNamedVariable(50, RcNamedVariable.FLOAT_TYPE, "USER:progress"),
        RcTextData(51, label),
        content = text(-10, 51),
      )
    }
    val combined = withProgress("a").combineAsStates(withProgress("b"))
    val names = combined.document.operations.filterIsInstance<RcNamedVariable>()
    assertEquals(listOf("USER:progress", "USER:rcState"), names.map { it.name }.sorted())
  }

  @Test
  fun anExpressionOverSharedInputsIsSharedToo() {
    val multiply = RcFloatExpressionEvaluator.operatorWord(RcFloatExpressionEvaluator.OFFSET + 3)
    val scaled = { label: String ->
      screen(
        RcFloatConstant(60, RcFloatWord.literal(2f)),
        RcFloatExpression(
          61,
          listOf(
            RcFloatWord(0x7fc00000 or 60),
            RcFloatWord(0x7fc00000 or RcSystemVariables.DENSITY),
            multiply,
          ),
          null,
        ),
        RcTextData(62, label),
        content = text(-10, 62),
      )
    }
    val combined = scaled("a").combineAsStates(scaled("b"))
    assertEquals(2, combined.sharedDefinitions, "the constant, then the expression over it")
    assertEquals(1, combined.document.operations.count { it is RcFloatExpression })
  }

  @Test
  fun aDocumentWithoutOneRootLayoutIsRefused() {
    assertFailsWith<IllegalArgumentException> {
      home.combineAsStates(document(RcTextData(42, "x")))
    }
  }

  private fun RcOperation.animationIdOf(): Int? =
    when (this) {
      is RcTextLayout -> animationId
      is RcImageLayout -> animationId
      else -> null
    }
}

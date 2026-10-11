package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcConditionalOperations
import ee.schimke.composeai.rcplayer.protocol.RcDebugMessage
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcRemark
import ee.schimke.composeai.rcplayer.protocol.RcRootContentDescription
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatExpressionChangeAction
import ee.schimke.composeai.rcplayer.runtime.RcFloatExpressionEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RcOptimizeTest {
  private fun f(value: Float) = RcFloatWord.literal(value)

  private fun ref(id: Int) = RcFloatWord(0x7fc00000 or id)

  private fun op(operator: Int) =
    RcFloatExpressionEvaluator.operatorWord(RcFloatExpressionEvaluator.OFFSET + operator)

  @Test
  fun debugOperationsAreStripped() {
    val result =
      document(RcRemark("note"), RcDebugMessage(100, f(1f), 0), RcTextData(100, "a"))
        .stripDebugOperations()
    assertEquals(listOf(RcTextData(100, "a")), result.operations)
  }

  @Test
  fun constantIntegerAndFloatExpressionsFoldToTheirValues() {
    val result =
      document(
          RcIntegerConstant(50, 2),
          RcIntegerExpression(51, mask = 0b101, values = listOf(50, 3, RcIntegerExpression.MUL)),
          RcFloatConstant(60, f(2f)),
          RcFloatExpression(61, listOf(ref(60), f(3f), op(MUL)), animation = null),
        )
        .foldConstantExpressions()
    assertEquals(
      listOf(
        RcIntegerConstant(50, 2),
        RcIntegerConstant(51, 6),
        RcFloatConstant(60, f(2f)),
        RcFloatConstant(61, f(6f)),
      ),
      result.operations,
    )
  }

  @Test
  fun expressionsThatCanChangeDoNotFold() {
    val cases =
      listOf(
        // Reads a host-bound value.
        document(
          RcNamedVariable(60, RcNamedVariable.FLOAT_TYPE, "USER:x"),
          RcFloatConstant(60, f(2f)),
          RcFloatExpression(61, listOf(ref(60), f(3f), op(MUL)), animation = null),
        ),
        // Random.
        document(RcFloatExpression(61, listOf(op(RAND)), animation = null)),
        // Animated.
        document(RcFloatExpression(61, listOf(f(3f)), animation = listOf(f(1f)))),
        // An expression-change action evaluates it by id.
        document(
          RcFloatExpression(61, listOf(f(3f)), animation = null),
          RcValueFloatExpressionChangeAction(targetValueId = 62, expressionId = 61),
        ),
        // Written only under a condition.
        document(
          RcConditionalOperations(RcConditionalOperations.EQUAL, f(0f), f(1f)),
          RcFloatExpression(61, listOf(f(3f)), animation = null),
          END,
        ),
      )
    cases.forEach { source -> assertSame(source, source.foldConstantExpressions(), "$source") }
  }

  @Test
  fun identicalRepeatedDefinitionsCollapseButDifferingOnesStay() {
    assertEquals(
      listOf(RcTextData(100, "a")),
      document(RcTextData(100, "a"), RcTextData(100, "a")).removeDuplicateDefinitions().operations,
    )
    val differing = document(RcTextData(100, "a"), RcTextData(100, "b"), RcTextData(100, "a"))
    assertSame(differing, differing.removeDuplicateDefinitions())
  }

  @Test
  fun unreadDefinitionsGoIncludingWhatOnlyTheyRead() {
    val result =
      document(
          RcTextData(100, "unread"),
          RcTextData(101, "described"),
          RcRootContentDescription(101),
          // 60 is read only by 61, which nothing reads: both go.
          RcFloatConstant(60, f(2f)),
          RcFloatExpression(61, listOf(ref(60), f(3f), op(MUL)), animation = null),
          // A host can bind 70, so it stays.
          RcNamedVariable(70, RcNamedVariable.INT_TYPE, "USER:n"),
          RcIntegerConstant(70, 1),
        )
        .removeUnreadDefinitions()
    assertEquals(
      listOf(
        RcTextData(101, "described"),
        RcRootContentDescription(101),
        RcNamedVariable(70, RcNamedVariable.INT_TYPE, "USER:n"),
        RcIntegerConstant(70, 1),
      ),
      result.operations,
    )
  }

  @Test
  fun theSizeReportAddsUpToTheEncodedDocument() {
    val document = document(RcTextData(100, "hello"), RcRootContentDescription(100))
    val report = document.sizeReport()
    assertEquals(RcDocumentCodec.encode(document).size, report.totalBytes)
    assertEquals(setOf("HEADER", "DATA_TEXT", "ROOT_CONTENT_DESCRIPTION"), report.byOperation.keys)
  }

  private companion object {
    const val MUL = 3
    const val RAND = 39
  }
}

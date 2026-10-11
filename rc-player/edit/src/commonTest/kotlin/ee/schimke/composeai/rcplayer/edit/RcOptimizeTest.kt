package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcColorAttribute
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcConditionalOperations
import ee.schimke.composeai.rcplayer.protocol.RcDebugMessage
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRemark
import ee.schimke.composeai.rcplayer.protocol.RcRootContentDescription
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextLookupInt
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatExpressionChangeAction
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcFloatExpressionEvaluator
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
  fun constantColourAndTextProducersFoldToWhatThePlayerComputes() {
    val red = 0xFFFF0000.toInt()
    val blue = 0xFF0000FF.toInt()
    val result =
      document(
          RcColorConstant(50, red),
          RcColorConstant(51, blue),
          // Halfway from red to blue, by id; then red's channels.
          RcColorExpression(52, RcColorExpression.ID_ID_INTERPOLATE, 50, 51, 0.5f.toRawBits()),
          RcColorAttribute(53, 50, RcColorAttribute.COLOR_RED),
          RcTextData(60, "Steps: "),
          RcFloatConstant(61, f(1234f)),
          RcTextFromFloat(62, ref(61), digitsBefore = 5, digitsAfter = 0, flags = 0),
          RcTextMerge(63, 60, 62),
          RcTextData(64, "zero"),
          RcTextData(65, "one"),
          RcIdList(LIST, listOf(64, 65)),
          RcIntegerConstant(66, 1),
          RcTextLookupInt(67, LIST, 66),
        )
        .foldConstantExpressions()

    val folded = result.operations.associateBy { (it.definedIds() ?: emptyList()).firstOrNull() }
    val player =
      RcPlayerState(result).apply {
        applyContentStateOperations(RcDocumentLinker.link(result).operations)
      }
    val original =
      RcPlayerState(
          document(
            RcColorConstant(50, red),
            RcColorConstant(51, blue),
            RcColorExpression(52, RcColorExpression.ID_ID_INTERPOLATE, 50, 51, 0.5f.toRawBits()),
          )
        )
        .apply { applyContentStateOperations(RcDocumentLinker.link(document).operations) }
    assertEquals(RcColorConstant(52, original.color(52)), folded[52])
    assertEquals(RcFloatConstant(53, f(1f)), folded[53])
    assertEquals(RcTextData(63, "Steps: ${player.text(62)}"), folded[63])
    assertEquals(RcTextData(67, "one"), folded[67])
  }

  @Test
  fun aProducerReadingAHostValueFoldsOnceTheValueIsHardcoded() {
    val source =
      document(
        RcColorConstant(50, 0xFF000000.toInt()),
        RcNamedVariable(50, RcNamedVariable.COLOR_TYPE, "USER:accent"),
        RcColorAttribute(53, 50, RcColorAttribute.COLOR_ALPHA),
      )
    assertSame(source, source.foldConstantExpressions())

    val baked = source.hardcodeUnboundNamedValues().foldConstantExpressions()
    assertEquals(RcFloatConstant(53, f(1f)), baked.operations.last())
  }

  @Test
  fun aProducerInDrawnContentFolds() {
    val result =
      document(
          *rootLayout(
              RcColorConstant(50, 0xFF336699.toInt()),
              RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
              RcColorAttribute(53, 50, RcColorAttribute.COLOR_ALPHA),
              END,
            )
            .toTypedArray()
        )
        .foldConstantExpressions()
    assertTrue(RcFloatConstant(53, f(1f)) in result.operations)
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
    /** An id list's id, in the array region AndroidX's writer allocates them in. */
    const val LIST = 0x20002A
    const val RAND = 39
  }
}

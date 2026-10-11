package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcLoopOperation
import ee.schimke.composeai.rcplayer.protocol.RcMatrixVectorMath
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcTextAttribute
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatExpressionChangeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RcHardcodeNamedValuesTest {
  // The two orders AndroidX writers emit: a colour's name follows its constant, while floats,
  // integers and text declare the name first.
  private val source =
    document(
      RcNamedVariable(52, RcNamedVariable.FLOAT_TYPE, "USER:progress"),
      RcFloatConstant(52, RcFloatWord.literal(0.6f)),
      RcColorConstant(56, 0xff112233.toInt()),
      RcNamedVariable(56, RcNamedVariable.COLOR_TYPE, "USER:WearM3.surfaceContainer"),
      RcNamedVariable(51, RcNamedVariable.INT_TYPE, "USER:light.is_on"),
      RcIntegerConstant(51, 1),
      RcNamedVariable(65, RcNamedVariable.STRING_TYPE, "USER:label"),
      RcTextData(65, "On"),
      RcFloatExpression(70, listOf(RcFloatWord.literal(1f)), animation = null),
    )

  @Test
  fun hardcodedValuesReplaceTheDefinitionAndDropTheName() {
    val result =
      assertRoundTrips(
        source.hardcodeNamedValues(
          mapOf(
            "progress" to RcHardcodedValue.FloatValue(0.25f),
            "USER:WearM3.surfaceContainer" to RcHardcodedValue.Color(0xff445566.toInt()),
            "light.is_on" to RcHardcodedValue.BooleanValue(false),
          )
        )
      )

    assertEquals(
      listOf(
        RcFloatConstant(52, RcFloatWord.literal(0.25f)),
        RcColorConstant(56, 0xff445566.toInt()),
        RcIntegerConstant(51, 0),
        RcNamedVariable(65, RcNamedVariable.STRING_TYPE, "USER:label"),
        RcTextData(65, "On"),
        RcFloatExpression(70, listOf(RcFloatWord.literal(1f)), animation = null),
      ),
      result.operations,
    )
    assertEquals(listOf("USER:label"), result.namedVariables.map { it.name })
  }

  @Test
  fun aNameWithNoDefinitionGetsOneWhereItWasDeclared() {
    val result =
      document(RcNamedVariable(80, RcNamedVariable.LONG_TYPE, "USER:epoch"))
        .hardcodeNamedValues(mapOf("epoch" to RcHardcodedValue.LongValue(1234L)))
    assertEquals(listOf(RcLongConstant(80, 1234L)), result.operations)
  }

  @Test
  fun aValueOfTheWrongShapeIsRejected() {
    assertFailsWith<IllegalArgumentException> {
      source.hardcodeNamedValues(mapOf("progress" to RcHardcodedValue.Text("no")))
    }
  }

  @Test
  fun unknownNamesChangeNothing() {
    assertSame(source, source.hardcodeNamedValues(mapOf("missing" to RcHardcodedValue.Integer(1))))
  }

  @Test
  fun unboundNamesAreFrozenAtTheirDocumentDefaults() {
    val result = source.hardcodeUnboundNamedValues(bound = setOf("label"))
    assertEquals(listOf("USER:label"), result.namedVariables.map { it.name })
    assertEquals(
      source.operations.filterNot { it is RcNamedVariable },
      result.operations.filterNot { it is RcNamedVariable },
    )
  }

  @Test
  fun anUnqualifiedKeyNamesTheUserDeclarationWhenBothExist() {
    val both =
      document(
        RcNamedVariable(1, RcNamedVariable.INT_TYPE, "foo"),
        RcIntegerConstant(1, 0),
        RcNamedVariable(2, RcNamedVariable.INT_TYPE, "USER:foo"),
        RcIntegerConstant(2, 0),
      )

    val hardcoded = both.hardcodeNamedValues(mapOf("foo" to RcHardcodedValue.Integer(5)))
    assertEquals(listOf("foo"), hardcoded.namedVariables.map { it.name })
    assertEquals(
      listOf(RcIntegerConstant(1, 0), RcIntegerConstant(2, 5)),
      hardcoded.operations.filterIsInstance<RcIntegerConstant>(),
    )

    assertEquals(
      listOf("USER:foo"),
      both.hardcodeUnboundNamedValues(bound = setOf("foo")).namedVariables.map { it.name },
    )
  }

  @Test
  fun everyProducerOfAHardcodedIdIsReplaced() {
    val result =
      document(
          RcNamedVariable(65, RcNamedVariable.STRING_TYPE, "USER:label"),
          RcTextData(65, "On"),
          RcTextMerge(65, 1, 2),
        )
        .hardcodeNamedValues(mapOf("label" to RcHardcodedValue.Text("Off")))
    assertEquals(listOf(RcTextData(65, "Off"), RcTextData(65, "Off")), result.operations)
  }

  @Test
  fun anOperationWritingTheIdAmongOthersCannotBeHardcoded() {
    val document =
      document(
        RcNamedVariable(52, RcNamedVariable.FLOAT_TYPE, "USER:x"),
        RcFloatConstant(52, RcFloatWord.literal(0f)),
        RcMatrixVectorMath(0, listOf(52, 53), 70, listOf(RcFloatWord.literal(1f))),
      )
    assertFailsWith<IllegalArgumentException> {
      document.hardcodeNamedValues(mapOf("x" to RcHardcodedValue.FloatValue(1f)))
    }
  }

  @Test
  fun anExpressionAChangeActionEvaluatesCannotBeHardcoded() {
    val document =
      document(
        RcNamedVariable(52, RcNamedVariable.FLOAT_TYPE, "USER:x"),
        RcFloatExpression(52, listOf(RcFloatWord.literal(1f)), animation = null),
        RcValueFloatExpressionChangeAction(targetValueId = 53, expressionId = 52),
      )
    assertFailsWith<IllegalArgumentException> {
      document.hardcodeNamedValues(mapOf("x" to RcHardcodedValue.FloatValue(2f)))
    }
  }

  @Test
  fun aTextAttributeWritingTheIdIsReplacedToo() {
    val result =
      document(
          RcNamedVariable(52, RcNamedVariable.FLOAT_TYPE, "USER:width"),
          RcFloatConstant(52, RcFloatWord.literal(0f)),
          RcTextAttribute(52, 65, 0),
        )
        .hardcodeNamedValues(mapOf("width" to RcHardcodedValue.FloatValue(3f)))
    assertTrue(result.operations.none { it is RcTextAttribute })
  }

  @Test
  fun aLoopIndexCannotBeHardcoded() {
    val document =
      document(
        RcNamedVariable(52, RcNamedVariable.FLOAT_TYPE, "USER:i"),
        RcFloatConstant(52, RcFloatWord.literal(0f)),
        RcLoopOperation(
          52,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(1f),
          RcFloatWord.literal(3f),
        ),
        END,
      )
    assertFailsWith<IllegalArgumentException> {
      document.hardcodeNamedValues(mapOf("i" to RcHardcodedValue.FloatValue(2f)))
    }
  }
}

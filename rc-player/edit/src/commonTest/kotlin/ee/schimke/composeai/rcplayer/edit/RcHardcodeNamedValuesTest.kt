package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

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
}

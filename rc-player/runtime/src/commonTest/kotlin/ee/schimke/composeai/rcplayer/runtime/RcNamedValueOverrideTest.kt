package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDynamicFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The host overrides the embedded AndroidX player grew beyond the scalar ones: booleans (an integer
 * name holding 1 or 0), float arrays, and the `USER:` namespace an unqualified name resolves into.
 */
class RcNamedValueOverrideTest {

  private fun document() =
    RcDocument(
      RcHeader(RcVersion(0, 1, 0)),
      listOf(
        RcNamedVariable(50, RcNamedVariable.INT_TYPE, "USER:enabled"),
        RcFloatList(51, listOf(RcFloatWord.literal(1f), RcFloatWord.literal(2f))),
        RcNamedVariable(51, RcNamedVariable.FLOAT_ARRAY_TYPE, "USER:series"),
        RcFloatConstant(52, RcFloatWord.literal(3f)),
        RcNamedVariable(52, RcNamedVariable.FLOAT_TYPE, "plain"),
        RcFloatConstant(53, RcFloatWord.literal(4f)),
        RcNamedVariable(53, RcNamedVariable.FLOAT_TYPE, "OTHER:plain"),
      ),
    )

  @Test
  fun aBooleanOverrideWritesOneOrZeroToAnIntegerName() {
    val state = RcPlayerState(document())

    state.setNamedValue("USER:enabled", RcNamedValue.BooleanValue(true))
    assertEquals(1, state.integer(50))

    state.setNamedValue("USER:enabled", RcNamedValue.BooleanValue(false))
    assertEquals(0, state.integer(50))
  }

  @Test
  fun aFloatArrayOverrideReplacesTheListAndClearingRestoresTheAuthoredOne() {
    val state = RcPlayerState(document())
    assertEquals(RcNamedValue.FloatArrayValue(listOf(1f, 2f)), state.namedValue("USER:series"))

    state.setNamedValue("USER:series", RcNamedValue.FloatArrayValue(listOf(5f, 6f, 7f)))
    assertEquals(listOf(5f, 6f, 7f), state.floatValues(51)?.toList())

    state.clearNamedValue("USER:series")
    assertEquals(listOf(1f, 2f), state.floatValues(51)?.toList())
  }

  @Test
  fun anUnqualifiedNameResolvesIntoTheUserNamespaceFirst() {
    val state = RcPlayerState(document())

    state.setNamedValue("enabled", RcNamedValue.BooleanValue(true))
    state.setNamedValue("series", RcNamedValue.FloatArrayValue(listOf(9f)))

    assertEquals(1, state.integer(50))
    assertEquals(listOf(9f), state.floatValues(51)?.toList())
    assertEquals(RcNamedValue.Integer(1), state.namedValue("enabled"))
  }

  @Test
  fun anUndeclaredUserNameFallsBackToThePlainOneAndAColonIsUsedAsIs() {
    val state = RcPlayerState(document())

    state.setNamedValue("plain", RcNamedValue.FloatValue(8f))
    state.setNamedValue("OTHER:plain", RcNamedValue.FloatValue(9f))

    assertEquals(RcNamedValue.FloatValue(8f), state.namedValue("plain"))
    assertEquals(RcNamedValue.FloatValue(9f), state.namedValue("OTHER:plain"))
  }

  @Test
  fun anOverrideOfADynamicListSurvivesTheDocumentReplayingItsDeclaration() {
    // The document owns a 2-slot dynamic list; the host overrides it with three values. Replaying
    // the declaration each frame resizes the document's list, and must not resize the override.
    val declaration = RcDynamicFloatList(60, RcFloatWord.literal(2f))
    val state =
      RcPlayerState(
        RcDocument(
          RcHeader(RcVersion(0, 1, 0)),
          listOf(declaration, RcNamedVariable(60, RcNamedVariable.FLOAT_ARRAY_TYPE, "USER:dyn")),
        ),
        mapOf("USER:dyn" to RcNamedValue.FloatArrayValue(listOf(1f, 2f, 3f))),
      )

    state.applyDataOperation(declaration)
    assertEquals(listOf(1f, 2f, 3f), state.floatValues(60)?.toList())

    state.clearNamedValue("dyn")
    state.applyDataOperation(declaration)
    assertEquals(listOf(0f, 0f), state.floatValues(60)?.toList())
  }
}

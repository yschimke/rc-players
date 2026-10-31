package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RcDocumentUpdateTest {

  @Test
  fun ordinaryDocumentIsNotAnUpdateDocument() {
    assertEquals(false, document().header.isUpdateDocument)
  }

  @Test
  fun emitsOnlySupportedNamedDataOperationsInAnUpdateDocument() {
    val update =
      document()
        .snapshotUpdateDocument(
          mapOf(
            "label" to RcNamedValue.Text("Away"),
            "ratio" to RcNamedValue.FloatValue(0.75f),
            "enabled" to RcNamedValue.BooleanValue(true),
            "timestamp" to RcNamedValue.LongValue(42L),
            "series" to RcNamedValue.FloatArrayValue(listOf(3f, 4f)),
          )
        )

    requireNotNull(update)
    assertTrue(update.header.modern)
    assertTrue(update.header.isUpdateDocument)
    assertEquals(
      listOf(
        RcTextData(1, "Away"),
        RcFloatConstant(2, RcFloatWord.literal(0.75f)),
        RcIntegerConstant(3, 1),
        RcLongConstant(4, 42L),
        RcFloatList(5, listOf(RcFloatWord.literal(3f), RcFloatWord.literal(4f))),
      ),
      update.operations,
    )
  }

  @Test
  fun returnsNullForValuesAndroidxCannotApplyOrThatWouldBeIgnored() {
    assertNull(
      document().snapshotUpdateDocument(mapOf("accent" to RcNamedValue.Color(0xff000000.toInt())))
    )
    assertNull(document().snapshotUpdateDocument(mapOf("missing" to RcNamedValue.Text("value"))))
    assertNull(document().snapshotUpdateDocument(mapOf("label" to RcNamedValue.FloatValue(1f))))
  }

  @Test
  fun usesTheSameUserNamespaceResolutionAsNamedValueOverrides() {
    val update = document().snapshotUpdateDocument(mapOf("label" to RcNamedValue.Text("Away")))

    assertEquals(listOf(RcTextData(1, "Away")), requireNotNull(update).operations)
  }

  @Test
  fun updateHeaderAndDataRoundTripThroughTheWireCodec() {
    val update =
      requireNotNull(
        document().snapshotUpdateDocument(mapOf("ratio" to RcNamedValue.FloatValue(0.75f)))
      )

    assertEquals(update, RcDocumentCodec.decode(RcDocumentCodec.encode(update)))
  }

  private fun document(): RcDocument =
    RcDocument(
      RcHeader(RcVersion(0, 1, 0)),
      listOf(
        RcTextData(1, "Home"),
        RcNamedVariable(1, RcNamedVariable.STRING_TYPE, "USER:label"),
        RcFloatConstant(2, RcFloatWord.literal(0f)),
        RcNamedVariable(2, RcNamedVariable.FLOAT_TYPE, "USER:ratio"),
        RcIntegerConstant(3, 0),
        RcNamedVariable(3, RcNamedVariable.INT_TYPE, "USER:enabled"),
        RcLongConstant(4, 0L),
        RcNamedVariable(4, RcNamedVariable.LONG_TYPE, "USER:timestamp"),
        RcFloatList(5, listOf(RcFloatWord.literal(1f))),
        RcNamedVariable(5, RcNamedVariable.FLOAT_ARRAY_TYPE, "USER:series"),
        RcNamedVariable(6, RcNamedVariable.COLOR_TYPE, "USER:accent"),
      ),
    )
}

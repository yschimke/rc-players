package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcBooleanConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A state built from an [RcPlayerStateSeed] is the state the document constructor builds: the same
 * declarations in the same order, host values on top, and nothing shared between two states.
 */
class RcPlayerStateSeedTest {

  private val document =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 10, legacyHeight = 10, modern = false),
      listOf(
        RcFloatConstant(50, RcFloatWord.literal(2.5f)),
        // The same id declared again as an integer: the later declaration wins in both views.
        RcIntegerConstant(50, 7),
        RcColorConstant(51, 0xff112233.toInt()),
        RcTextData(52, "hello"),
        RcNamedVariable(52, RcNamedVariable.STRING_TYPE, "USER:title"),
        RcBooleanConstant(53, true),
        RcLongConstant(54, 1234L),
        RcIntegerConstant(55, 3),
        RcNamedVariable(55, RcNamedVariable.INT_TYPE, "USER:count"),
      ),
    )

  @Test
  fun aSeededStateReadsWhatAScannedStateReads() {
    val host = mapOf("USER:title" to RcNamedValue.Text("from the host"))
    val scanned = RcPlayerState(document, host)
    val seeded = RcPlayerState(RcPlayerStateSeed(document), host)
    for (id in 50..55) {
      assertEquals(scanned.integer(id), seeded.integer(id), "integer $id")
      assertEquals(scanned.color(id), seeded.color(id), "color $id")
      assertEquals(scanned.text(id), seeded.text(id), "text $id")
      assertEquals(scanned.boolean(id), seeded.boolean(id), "boolean $id")
      assertEquals(scanned.long(id), seeded.long(id), "long $id")
    }
    assertEquals(7, seeded.integer(50))
    assertEquals("from the host", seeded.text(52))
    assertEquals(scanned.namedValue("USER:count"), seeded.namedValue("USER:count"))
  }

  @Test
  fun statesFromOneSeedShareNothing() {
    val seed = RcPlayerStateSeed(document)
    val first = RcPlayerState(seed, mapOf("USER:count" to RcNamedValue.Integer(1)))
    val second = RcPlayerState(seed, mapOf("USER:count" to RcNamedValue.Integer(2)))
    first.setInteger(55, 10)
    assertEquals(10, first.integer(55))
    assertEquals(2, second.integer(55))
    assertEquals(3, RcPlayerState(seed).integer(55))
  }
}

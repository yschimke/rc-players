package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RcDocumentDataUpdateTest {

  @Test
  fun emitsACompleteCurrentDataSnapshotWhenOnlySupportedDataChanges() {
    val base = document(RcTextData(1, "Home"), RcFloatConstant(2, RcFloatWord.literal(0f)))
    val candidate = document(RcTextData(1, "Away"), RcFloatConstant(2, RcFloatWord.literal(0f)))

    val result = assertIs<RcDocumentDataUpdateResult.Update>(base.dataUpdateTo(candidate))

    assertEquals(true, result.document.header.isUpdateDocument)
    assertEquals(candidate.operations, result.document.operations)
  }

  @Test
  fun unchangedCandidateDoesNotPublishAnEmptyUpdate() {
    val base = document(RcTextData(1, "Home"))

    assertEquals(RcDocumentDataUpdateResult.Unchanged, base.dataUpdateTo(base))
  }

  @Test
  fun structuralAndUnsupportedDataChangesRequireAFullDocument() {
    val base = document(RcIntegerConstant(1, 1), RcLongConstant(2, 2L))

    assertEquals(
      RcDocumentDataUpdateResult.FullDocumentRequired,
      base.dataUpdateTo(document(RcIntegerConstant(1, 1), RcLongConstant(3, 2L))),
    )
  }

  @Test
  fun bitmapChangesProduceAnUpdateDocument() {
    val base = document(bitmap(byteArrayOf(1)))
    val candidate = document(bitmap(byteArrayOf(2)))

    assertIs<RcDocumentDataUpdateResult.Update>(base.dataUpdateTo(candidate))
  }

  private fun document(vararg operations: RcOperation): RcDocument =
    RcDocument(RcHeader(RcVersion(0, 1, 0)), operations.toList())

  private fun bitmap(bytes: ByteArray): RcBitmapData =
    RcBitmapData(
      imageId = 3,
      width = 1,
      height = 1,
      type = RcBitmapData.TYPE_PNG,
      encoding = RcBitmapData.ENCODING_INLINE,
      data = bytes,
    )
}

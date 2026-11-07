package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.edit.RcProfileViolation.Kind
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which measurements [Kind.MEASURED_IN_HIDDEN_BRANCH] reports: by the component measured. */
class RcMeasuredInHiddenBranchTest {
  /** A state layout over boxes 20 and 30, inside root box 5, then [after]. */
  private fun stateDocument(
    branch20: List<RcOperation> = emptyList(),
    after: List<RcOperation> = emptyList(),
  ): RcDocument =
    document(
      RcIntegerConstant(INDEX, 0),
      *rootLayout(
          *box(
              5,
              RcLayoutContent(4),
              RcStateLayout(10, -1, 0, 0, INDEX),
              RcLayoutContent(9),
              *box(20, *branch20.toTypedArray()).toTypedArray(),
              *box(30).toTypedArray(),
              END,
              END,
              END,
            )
            .toTypedArray(),
        )
        .toTypedArray(),
      *after.toTypedArray(),
    )

  /** An expression reading [id] over 24: an icon's scale. */
  private fun readerOf(id: Int) =
    RcFloatExpression(300, listOf(reference(id), RcFloatWord.literal(24f)), animation = null)

  private fun reference(id: Int) = RcFloatWord(0x7fc00000 or id)

  private fun RcDocument.reported() =
    validateFor(RcTargetProfiles.WIDGETS_V6).filter { it.kind == Kind.MEASURED_IN_HIDDEN_BRANCH }

  @Test
  fun measuringABranchComponentFromOutsideTheBranchIsReported() {
    val doc =
      stateDocument(
        after = listOf(RcComponentValue(RcComponentValue.WIDTH, 20, 201), readerOf(201)),
      )
    assertEquals(1, doc.reported().size)
  }

  @Test
  fun measuringAnOutsideComponentFromInsideABranchIsNotReported() {
    val doc =
      stateDocument(
        branch20 = listOf(RcComponentValue(RcComponentValue.WIDTH, 5, 201)),
        after = listOf(readerOf(201)),
      )
    assertEquals(emptyList(), doc.reported())
  }

  @Test
  fun measuringTheEnclosingComponentInsideABranchIsReported() {
    val doc =
      stateDocument(
        branch20 = listOf(RcComponentValue(RcComponentValue.WIDTH, -1, 201), readerOf(201)),
      )
    assertEquals(1, doc.reported().size)
  }

  @Test
  fun aMeasurementNothingReadsIsNotReported() {
    val doc = stateDocument(after = listOf(RcComponentValue(RcComponentValue.WIDTH, 20, 201)))
    assertEquals(emptyList(), doc.reported())
  }

  private companion object {
    const val INDEX = 60
  }
}

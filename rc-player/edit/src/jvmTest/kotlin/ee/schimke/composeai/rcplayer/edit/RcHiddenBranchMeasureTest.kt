package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.edit.RcProfileViolation.Kind
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A real widget template that failed on Android 16 (see `fixtures/README.md`): every branch of its
 * state layouts reads the measured size of a canvas, which the platform player evaluates while the
 * branch is hidden. The validator reports it, and the fixed template has none.
 */
class RcHiddenBranchMeasureTest {
  private fun fixture(name: String): RcDocument =
    RcDocumentCodec.decode(File("src/jvmTest/fixtures/$name").readBytes())

  private fun RcDocument.measured() =
    validateFor(RcTargetProfiles.WIDGETS_V6).filter { it.kind == Kind.MEASURED_IN_HIDDEN_BRANCH }

  @Test
  fun theShippedTemplateReadsMeasuredSizesInBranches() {
    val violations = fixture("cadence-headphone-0.1.19.rc").measured()
    assertTrue(violations.isNotEmpty())
    assertTrue(violations.all { it.message.contains("state layout branch") }, "$violations")
  }

  @Test
  fun theFixedTemplateReadsNone() {
    assertEquals(emptyList(), fixture("cadence-headphone-fixed.rc").measured())
  }
}

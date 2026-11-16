package ee.schimke.composeai.rcwidget.creation

import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.RemoteDensity
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWearHost
import ee.schimke.composeai.rcwidget.RcWearTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * A Wear widget template with size breakpoints, made for one watch's container: only the tier that
 * container's size picks is left, and the document is smaller for it.
 */
class RcWearTemplateSizeTest {
  private val template = runBlocking {
    RcDocumentCodec.decode(
      captureCommonRemoteDocument(
        creationDisplayInfo = RemoteCreationDisplayInfo(200, 112, 160),
        remoteDensity = RemoteDensity.Host,
        profile = RcCaptureProfiles.wearWidgets,
      ) {
        RcWearTemplateContainer {
          // Narrow below 150 dp wide; below 100 dp tall too.
          RcSizeBreakpoint(intArrayOf(150), RemoteModifier.fillMaxSize()) { wide ->
            RcSizeBreakpoint(
              intArrayOf(100),
              RemoteModifier.fillMaxSize(),
              RcBreakpointAxis.Height,
            ) { tall ->
              RemoteText(
                "tier-${if (wide == 1) "wide" else "narrow"}-${if (tall == 1) "tall" else "short"}"
                  .rs,
              )
            }
          }
        }
      },
      RcWireLimits(),
    )
  }

  private fun tiers(frame: ee.schimke.composeai.rcwidget.RcWearFrame): Pair<List<String>, Int> {
    val document = RcWearTemplates.instantiate(template, frame, RcDisplay(2f)).document
    val texts =
      document.operations
        .filterIsInstance<RcTextData>()
        .map { it.text }
        .filter { it.startsWith("tier-") }
    return texts to document.operations.count { it is RcStateLayout && it.indexId != 0 }
  }

  @Test
  fun eachContainerKeepsOnlyItsTier() {
    // Content is the frame less its padding: Pixel's Large is 184 × 92 dp, Galaxy's 90 × 104 dp.
    assertEquals(listOf("tier-wide-short") to 0, tiers(RcWearHost.Pixel.large))
    assertEquals(listOf("tier-narrow-tall") to 0, tiers(RcWearHost.Galaxy.large))
  }

  @Test
  fun theDocumentIsSmallerForIt() {
    val before = RcDocumentCodec.encode(template).size
    val after =
      RcDocumentCodec.encode(
          RcWearTemplates.instantiate(template, RcWearHost.Pixel.large, RcDisplay(2f)).document,
        )
        .size
    assertTrue(after < before * 3 / 4, "$before -> $after bytes")
  }
}

package ee.schimke.composeai.rcplayer.profile

import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLayoutTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RcEmbeddedFeedProfileTest {

  @Test
  fun embeddedFeedDocumentsRoundTripAndBuildLayout() {
    val bundle = RcEmbeddedFeedDocuments.build()
    listOf(
        "child" to bundle.childBytes,
        "flat" to bundle.flatCardBytes,
        "inline" to bundle.inlineCardBytes,
        "url" to bundle.urlCardBytes,
      )
      .forEach { (name, bytes) ->
        val decoded = RcDocumentCodec.decode(bytes)
        val linked = RcDocumentLinker.link(decoded)
        assertNotNull(RcLayoutTree.build(linked), "$name should build a layout tree")
      }
  }

  @Test
  fun embeddedFeedProfileRunsAllModes() {
    val profile =
      RcEmbeddedFeedProfile(visible = 2, arrivalsPerFrame = 1, frames = 4, warmupFrames = 1)
    val results = profile.run(rounds = 2)
    assertEquals(RcEmbeddedFeedMode.entries.size, results.size)
    results.forEach { result ->
      assertEquals(4, result.frameNanos.size, "mode ${result.mode}")
      assertTrue(result.frameNanos.all { it > 0L }, "mode ${result.mode}")
    }
    val report = RcEmbeddedFeedProfile.report(results, listOf("Env" to "test"))
    assertTrue(report.contains("FlatPrepared"))
    assertTrue(report.contains("embedded inline"))
    assertTrue(report.contains("embedded URL"))
  }
}

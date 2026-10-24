package ee.schimke.composeai.rcplayer.desktop

import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class RcViewerDocumentTest {
  private val fixtures = File(requireNotNull(System.getProperty("rc.fixtures")))

  @Test
  fun opensAPlayerFixture() {
    val load = loaded("IndeterminateCircularProgress-400x400.rc")
    assertEquals(400, load.widthPx)
    assertEquals(400, load.heightPx)
  }

  @Test
  fun refusesBytesThatAreNotADocument() {
    val load = loadRcViewerDocument("junk.rc", "not a document".encodeToByteArray(), false)
    assertIs<RcViewerLoad.Failed>(load)
    assertTrue(load.message.startsWith("Not a Remote Compose document"), load.message)
  }

  @Test
  fun reportsAMissingFileRatherThanThrowing() {
    val load = loadRcViewerDocument(File(fixtures, "does-not-exist.rc"), downloadFonts = false)
    assertIs<RcViewerLoad.Failed>(load)
    assertTrue(load.message.startsWith("Could not read"), load.message)
  }

  /** What `--render-png` and the release smoke test rely on: a frame with the document in it. */
  @Test
  fun rendersAFixtureHeadless() {
    val load = loaded("TextRemoteButton-454x200.rc")
    val png = renderRcPng(load.document, load.widthPx, load.heightPx, load.typefaces)
    val image = ImageIO.read(ByteArrayInputStream(png))
    assertEquals(load.widthPx, image.width)
    assertEquals(load.heightPx, image.height)
    val colours = buildSet {
      for (y in 0 until image.height step 4) for (x in 0 until image.width step 4) {
        add(image.getRGB(x, y))
      }
    }
    assertTrue(colours.size > 2, "the frame is not blank (${colours.size} distinct colours)")
  }

  @Test
  fun picksTheTrueTypeFaceFromAStylesheet() {
    val css =
      "@font-face { src: url(https://fonts.gstatic.com/a.woff2) format('woff2'); }\n" +
        "@font-face { src: url(https://fonts.gstatic.com/s/roboto/v1/b.ttf) format('truetype'); }"
    assertEquals(
      "https://fonts.gstatic.com/s/roboto/v1/b.ttf",
      RcGoogleFonts.fontUri(css).toString(),
    )
  }

  private fun loaded(name: String): RcViewerLoad.Loaded =
    // Offline: the suite must not depend on Google Fonts being reachable, and a `google:` family
    // that is not fetched still opens in the default face.
    when (val load = loadRcViewerDocument(File(fixtures, name), downloadFonts = false)) {
      is RcViewerLoad.Loaded -> load
      is RcViewerLoad.Failed -> fail("$name did not open: ${load.message}")
    }
}

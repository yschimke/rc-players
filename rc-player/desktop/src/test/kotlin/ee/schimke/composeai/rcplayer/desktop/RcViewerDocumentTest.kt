package ee.schimke.composeai.rcplayer.desktop

import com.sun.net.httpserver.HttpServer
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapInt
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
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

  /**
   * A URL bitmap is in the encoded frame, not still loading: `--render-png` closes the scene after
   * two frames, which would cancel an asynchronous load and leave the image blank. The server is
   * deliberately slow, as a real one is, so the image cannot arrive between the two frames.
   */
  @Test
  fun headlessRenderIncludesExternalImages() {
    val png =
      ByteArrayOutputStream()
        .also { out ->
          val red = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
          for (y in 0 until 8) for (x in 0 until 8) red.setRGB(x, y, 0xffff0000.toInt())
          ImageIO.write(red, "png", out)
        }
        .toByteArray()
    val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.createContext("/red.png") { exchange ->
      Thread.sleep(500)
      exchange.sendResponseHeaders(200, png.size.toLong())
      exchange.responseBody.use { it.write(png) }
    }
    server.start()
    try {
      val url = "http://127.0.0.1:${server.address.port}/red.png"
      val document =
        RcDocument(
          RcHeader(RcVersion(1, 0, 0), legacyWidth = 8, legacyHeight = 8, modern = false),
          listOf(
            RcBitmapData(
              42,
              8,
              8,
              RcBitmapData.TYPE_PNG,
              RcBitmapData.ENCODING_URL,
              url.encodeToByteArray(),
            ),
            RcDrawBitmapInt(42, 0, 0, 8, 8, 0, 0, 8, 8, 0),
          ),
        )
      val image = ImageIO.read(ByteArrayInputStream(renderRcPng(document, 8, 8)))
      assertEquals(0xffff0000.toInt(), image.getRGB(4, 4))
    } finally {
      server.stop(0)
    }
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

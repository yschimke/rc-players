package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextAnchored
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertTrue

class RcCanvasTypefaceTest {
  @Test
  fun canvasTextAsksTheHostForItsDefaultFace() {
    // `CoreText` resolves an unnamed family through the host's loader; canvas text went straight to
    // Compose's built-in face, so a host (or the conformance lane's Ahem) could restyle one kind of
    // text and not the other.
    val requested = mutableListOf<String>()
    val loader =
      object : RcTypefaceLoader {
        override val families: Set<String> = setOf("default")

        override fun typeface(family: String, variations: RcFontVariations?): FontFamily? {
          requested += family
          return null
        }
      }
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 20, modern = false),
        listOf(
          RcTextData(10, "Hi"),
          RcDrawTextAnchored(
            10,
            RcFloatWord.literal(20f),
            RcFloatWord.literal(10f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            0,
          ),
        ),
      )
    val scene =
      ImageComposeScene(width = 40, height = 20, density = Density(1f)) {
        RcComposePlayer(document, Modifier.fillMaxSize(), typefaces = loader)
      }
    try {
      scene.render()
    } finally {
      scene.close()
    }

    assertTrue("default" in requested, "the host was asked for $requested")
  }
}

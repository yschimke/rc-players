package ee.schimke.composeai.rcplayer.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcImageLoader
import ee.schimke.composeai.rcplayer.compose.RcPlayerTheme
import ee.schimke.composeai.rcplayer.compose.RcTypefaceLoader
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import org.jetbrains.skia.EncodedImageFormat

/**
 * Draw [document] once, off screen, and encode it as PNG.
 *
 * The same player the window shows, over Skiko's software raster, so no display is needed.
 * `fillMaxSize()` matters: with the default modifier the player's canvas measures 0×0 and text
 * sized from the draw scope lays out into nothing (see `RcProfileRunner`). [images] defaults to the
 * document's URL and file bitmaps fetched up front, so they are in the frame rather than still
 * loading when it is encoded.
 */
internal fun renderRcPng(
  document: RcDocument,
  width: Int,
  height: Int,
  typefaces: RcTypefaceLoader = RcTypefaceLoader.Default,
  theme: RcPlayerTheme = RcPlayerTheme.Light,
  images: RcImageLoader = RcPreloadedImages.of(document, network = true),
): ByteArray {
  val density = document.header.density.takeIf { it > 0f } ?: 1f
  val scene =
    ImageComposeScene(width = width, height = height, density = Density(density)) {
      RcComposePlayer(
        document,
        images,
        Modifier.fillMaxSize(),
        theme = theme,
        typefaces = typefaces,
      )
    }
  try {
    // Two frames: the first composes and lays out, the second draws what the first measured.
    scene.render(0L)
    val image = scene.render(16_666_667L)
    return requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed" }
      .bytes
  } finally {
    scene.close()
  }
}

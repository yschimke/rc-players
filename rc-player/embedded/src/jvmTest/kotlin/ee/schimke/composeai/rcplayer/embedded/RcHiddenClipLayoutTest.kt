package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteArrangement
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.background
import ee.schimke.composeai.rccreation.compose.modifier.clip
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.modifier.visibility
import ee.schimke.composeai.rccreation.compose.shapes.RemoteCircleShape
import ee.schimke.composeai.rccreation.compose.shapes.RemoteRoundedCornerShape
import ee.schimke.composeai.rccreation.compose.shapes.RemoteShape
import ee.schimke.composeai.rccreation.compose.state.RemoteInt
import ee.schimke.composeai.rccreation.compose.state.rc
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.ri
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * A box whose visibility is a named integer, in a column that spaces its children, played by the
 * CMP player: hidden it takes no room and no spacing, whether or not it is clipped; shown with a
 * rounded clip it keeps the clip's corners.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcHiddenClipLayoutTest {
  private val width = 120
  private val height = 100

  private fun author(clipped: Boolean, shown: Int): ByteArray = runBlocking {
    captureCommonRemoteDocument(
      RemoteCreationDisplayInfo(width, height, 160),
      profile = RcEmbeddedPlayerProfile,
    ) {
      RemoteColumn(
        modifier = RemoteModifier.fillMaxSize(),
        verticalArrangement = RemoteArrangement.spacedBy(10.rdp),
      ) {
        RemoteText("HEAD".rs)
        val state = RemoteInt.createNamedRemoteInt("poll_state", shown)
        var box =
          RemoteModifier.fillMaxWidth()
            .height(30.rdp)
            .visibility(state.isGreaterThan(3.ri).toRemoteInt())
        if (clipped) box = box.clip(RemoteRoundedCornerShape(12.rdp))
        RemoteColumn(box.background(0xFF3366FF.toInt().rc)) { RemoteText("MIDDLE".rs) }
        RemoteText("FOOT".rs)
      }
    }
  }

  private fun play(bytes: ByteArray): ImageBitmap {
    lateinit var image: ImageBitmap
    val document = RcDocumentCodec.decode(bytes)
    runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), Density(1f)) {
      setContent { RcComposePlayer(document, Modifier.fillMaxSize()) }
      waitForIdle()
      image = onRoot().captureToImage()
    }
    return image
  }

  private fun ImageBitmap.pixels(): List<Int> {
    val map = toPixelMap()
    return (0 until height).flatMap { y -> (0 until width).map { x -> map[x, y].hashCode() } }
  }

  @Test
  fun aHiddenClippedChildTakesNoRoomAndNoSpacing() {
    val plain = play(author(clipped = false, shown = 0))
    val clipped = play(author(clipped = true, shown = 0))
    assertTrue(plain.pixels().distinct().size > 1, "nothing is drawn")
    assertEquals(plain.pixels(), clipped.pixels(), "the clip moved the footer")
  }

  @Test
  fun aShownBoxKeepsItsRoundedClip() {
    val clipped = play(author(clipped = true, shown = 5)).toPixelMap()
    val square = play(author(clipped = false, shown = 5)).toPixelMap()
    // The box starts below the heading and its spacing: find its first blue row at the centre.
    val top = (0 until height).first { square[width / 2, it].blue > 0.9f }
    assertTrue(square[0, top].blue > 0.9f, "the unclipped box has a square corner")
    assertTrue(clipped[width / 2, top].blue > 0.9f, "the clipped box is drawn")
    assertTrue(clipped[0, top].alpha < 0.5f, "the clipped box's corner is cut")
  }

  /** A 40 px box clipped to a circle, with [visibility] on itself, on its parent, or nowhere. */
  private fun authorCircle(
    visibility: Where,
    shape: RemoteShape = RemoteCircleShape,
    constant: Boolean = false,
  ): ByteArray = runBlocking {
    captureCommonRemoteDocument(
      RemoteCreationDisplayInfo(width, height, 160),
      profile = RcEmbeddedPlayerProfile,
    ) {
      val shown =
        if (constant) 1.ri
        else RemoteInt.createNamedRemoteInt("poll_state", 5).isGreaterThan(3.ri).toRemoteInt()
      val parent =
        if (visibility == Where.Ancestor) RemoteModifier.visibility(shown) else RemoteModifier
      val own = if (visibility == Where.Itself) RemoteModifier.visibility(shown) else RemoteModifier
      RemoteBox(parent.fillMaxSize()) {
        RemoteBox(
          own.size(40.rdp).clip(shape).background(0xFF3366FF.toInt().rc),
        ) {}
      }
    }
  }

  private enum class Where {
    Nowhere,
    Itself,
    Ancestor,
  }

  @Test
  fun aCircleClipIsKeptWhateverWayTheBoxIsVisible() {
    for (where in Where.values()) for (constant in listOf(false, true)) for (shape in
      listOf<RemoteShape>(
        RemoteCircleShape,
        RemoteRoundedCornerShape(50),
        RemoteRoundedCornerShape(12.rdp),
      )) {
      val image = play(authorCircle(where, shape, constant)).toPixelMap()
      assertTrue(
        image[20, 20].blue > 0.9f && image[20, 20].alpha > 0.9f,
        "$where $constant $shape: box centre",
      )
      assertTrue(
        image[1, 1].alpha < 0.5f,
        "$where $constant $shape: the box's corner is cut by the circle",
      )
    }
  }
}

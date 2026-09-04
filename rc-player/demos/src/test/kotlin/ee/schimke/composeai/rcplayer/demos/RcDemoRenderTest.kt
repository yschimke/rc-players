package ee.schimke.composeai.rcplayer.demos

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcCustomComponentRegistry
import ee.schimke.composeai.rcplayer.compose.RcSpannableString
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Image

/** The demos are the regression tests for the two directions a custom component runs in. */
class RcDemoRenderTest {
  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun spannableStringCarriesItsTwoLinksAndTheDocumentColour() =
    runSkikoComposeUiTest(size = Size(360f, 96f), density = Density(1f)) {
      // Register a renderer that draws exactly what the player's would, and keeps the annotated
      // string it built — the spans are the interesting half and they are not readable from pixels.
      var annotated: androidx.compose.ui.text.AnnotatedString? = null
      val registry =
        RcCustomComponentRegistry(
          RcSpannableString.CONFIG to
            { component, modifier ->
              annotated = RcSpannableString.annotate(component)
              RcSpannableString.renderer(component, modifier)
            }
        )
      setContent { RcComposePlayer(RcDemoDocuments.spannableString(), customComponents = registry) }
      waitForIdle()

      val text = requireNotNull(annotated)
      val links = text.getLinkAnnotations(0, text.length)
      assertEquals(2, links.size, "both link spans survive")
      assertEquals(
        listOf("https://example.com/terms", "https://example.com/privacy"),
        links.map { (it.item as LinkAnnotation.Url).url },
      )
      assertEquals(
        listOf("terms", "privacy notice"),
        links.map { text.substring(it.start, it.end) },
      )

      // The colour arrived as a colour id; resolving it is what puts near-black text on screen.
      val pixels = onRoot().captureToImage().toPixelMap()
      var dark = 0
      for (y in 0 until 96) for (x in 0 until 360) if (pixels[x, y].red < 0.4f) dark++
      assertTrue(dark > 200, "the resolved document colour draws text, dark pixels = $dark")
    }

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun editingTheTextFieldWritesBackIntoTheDocument() =
    runSkikoComposeUiTest(size = Size(360f, 150f), density = Density(1f)) {
      setContent { RcEditableTextDemo() }
      waitForIdle()

      // Two nodes carry the document's text: the field itself, and the label under it — an ordinary
      // document text operation reading the same id. That the label is one of them is the point.
      assertEquals(
        2,
        onAllNodesWithText("Hello from the document").fetchSemanticsNodes().size,
        "the field and the document's own label both show the seeded value",
      )

      onNode(hasSetTextAction()).performTextClearance()
      onNode(hasSetTextAction()).performTextInput("Edited by the host")
      waitForIdle()

      assertEquals(
        2,
        onAllNodesWithText("Edited by the host").fetchSemanticsNodes().size,
        "the edit reached the document, so the label redrew with it",
      )
      assertEquals(
        0,
        onAllNodesWithText("Hello from the document").fetchSemanticsNodes().size,
        "and nothing is still showing the old value",
      )
    }

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun writeDemoRenders() {
    val target = System.getenv("RC_DEMO_RENDERS") ?: return
    File(target).mkdirs()
    capture("$target/spannable-string.png", height = 96f) { RcSpannableStringDemo() }
    capture("$target/editable-text.png", height = 150f) { RcEditableTextDemo() }
    capture("$target/editable-text-edited.png", height = 150f, type = "Edited by the host") {
      RcEditableTextDemo()
    }
  }

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  private fun capture(
    path: String,
    height: Float,
    type: String? = null,
    content: @androidx.compose.runtime.Composable () -> Unit,
  ) =
    // Density 1, deliberately: these documents are authored in device pixels (a legacy header), so
    // at any other density the host half of a custom component — which styles in `dp` and `sp`,
    // because that is what a host writes — would be sized against a box the document sized in
    // pixels. The mismatch is the document's to declare, not something to paper over here.
    runSkikoComposeUiTest(size = Size(360f, height), density = Density(1f)) {
      setContent { content() }
      waitForIdle()
      if (type != null) {
        onNode(hasSetTextAction()).performTextClearance()
        onNode(hasSetTextAction()).performTextInput(type)
        waitForIdle()
      }
      val bitmap = onRoot().captureToImage().asSkiaBitmap()
      File(path).writeBytes(Image.makeFromBitmap(bitmap).encodeToData()!!.bytes)
    }
}

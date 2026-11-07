package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.drawMesh2D
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.state.RemoteTimeVariables
import androidx.compose.remote.creation.compose.state.cos
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.sin
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * A mesh gradient authored with `drawMesh2D` plays in the CMP player through Skia's `drawVertices`:
 * red at the left edge, blue at the right, interpolated across the triangles in between — and a
 * channel reading `animationTime` brings green in as the clock runs.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcMesh2DPlaybackTest {
  private fun run(block: SkikoComposeUiTest.() -> Unit) =
    runSkikoComposeUiTest(Size(100f, 100f), Density(1f), block = block)

  private fun author(): ByteArray = runBlocking {
    captureCommonRemoteDocument(RemoteCreationDisplayInfo(100, 100, 160)) {
      RemoteCanvas(RemoteModifier.fillMaxSize()) {
        drawMesh2D(uCount = 2, vCount = 2) {
          xy(u * width, v * height)
          rgba(1f.rf - u, RemoteTimeVariables.animationTime.min(1f), u, 1f.rf)
        }
      }
    }
  }

  @Test
  fun vertexColoursInterpolateAndFollowTheClock() = run {
    val document = RcDocumentCodec.decode(author())
    mainClock.autoAdvance = false
    setContent { RcComposePlayer(document, Modifier.fillMaxSize()) }
    mainClock.advanceTimeByFrame()
    waitForIdle()
    val first = onRoot().captureToImage().toPixelMap()
    val left = first[2, 50]
    val middle = first[50, 50]
    val right = first[97, 50]
    assertTrue(left.red > 0.9f && left.blue < 0.1f, "left is red: $left")
    assertTrue(right.blue > 0.9f && right.red < 0.1f, "right is blue: $right")
    assertTrue(
      abs(middle.red - 0.5f) < 0.1f && abs(middle.blue - 0.5f) < 0.1f,
      "the middle is between: $middle",
    )
    // The same at the top and the bottom of the column: the gradient runs along u only.
    assertNear(first[50, 3], first[50, 96])
    assertTrue(middle.green < 0.2f, "green starts near zero: $middle")

    mainClock.advanceTimeBy(1_500)
    waitForIdle()
    val later = onRoot().captureToImage().toPixelMap()
    assertTrue(later[50, 50].green > 0.9f, "green has come in: ${later[50, 50]}")
    assertTrue(later[2, 50].red > 0.9f, "red is unchanged: ${later[2, 50]}")
  }

  /**
   * A `u`-reading sub-expression shared by two channels and the clock mixed in: the shape that once
   * hoisted `u` into a document float, which crashed the evaluator. It now plays, and the gradient
   * is smooth — no step between neighbouring pixels along a row.
   */
  @Test
  fun aSharedSubExpressionMeshPlaysSmoothly() = run {
    val bytes = runBlocking {
      captureCommonRemoteDocument(RemoteCreationDisplayInfo(100, 100, 160)) {
        RemoteCanvas(RemoteModifier.fillMaxSize()) {
          drawMesh2D(uCount = 6, vCount = 4) {
            val t = RemoteTimeVariables.animationTime * 0.6f
            xy(u * width, v * height)
            val a = sin(u * 3f + t) * 0.5f + 0.5f
            val b = cos(v * 2.5f - t * 0.7f) * 0.5f + 0.5f
            rgba(0.50f.rf + a * 0.40f, 0.32f.rf - a * 0.25f + b * 0.15f, 1f.rf - b * 0.6f, 1f.rf)
          }
        }
      }
    }
    val document = RcDocumentCodec.decode(bytes)
    mainClock.autoAdvance = false
    setContent { RcComposePlayer(document, Modifier.fillMaxSize()) }
    mainClock.advanceTimeByFrame()
    waitForIdle()
    val pixels = onRoot().captureToImage().toPixelMap()
    // At t = 0: a = sin(3u)/2 + 1/2, so red runs 0.70 at the left edge up to ~0.90 near u = 0.5.
    assertTrue(abs(pixels[1, 50].red - 0.70f) < 0.06f, "left ${pixels[1, 50]}")
    assertTrue(pixels[50, 50].red > 0.85f, "middle ${pixels[50, 50]}")
    for (x in 1 until 99) {
      val step = abs(pixels[x, 50].red - pixels[x + 1, 50].red)
      assertTrue(step < 0.03f, "a step of $step at x = $x")
    }
  }

  /**
   * Evidence for `renders/cmp-mesh2d/`, written only when `RC_LAYOUT_EVIDENCE_DIR` is set: a 6x4
   * mesh gradient — Kotlin purple through magenta to coral, drifting on the clock — at t = 0 and t
   * = 2 s, 720x400 at density 2.
   */
  @Test
  fun writeMeshGradientEvidence() {
    val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    val bytes = runBlocking {
      captureCommonRemoteDocument(RemoteCreationDisplayInfo(360, 200, 160)) {
        RemoteCanvas(RemoteModifier.fillMaxSize()) {
          drawMesh2D(uCount = 6, vCount = 4) {
            val t = RemoteTimeVariables.animationTime * 0.6f
            xy(u * width, v * height)
            val a = sin(u * 3f + t) * 0.5f + 0.5f
            val b = cos(v * 2.5f - t * 0.7f) * 0.5f + 0.5f
            rgba(
              0.50f.rf + a * 0.40f,
              0.32f.rf - a * 0.25f + b * 0.15f,
              1f.rf - b * 0.60f,
              1f.rf,
            )
          }
        }
      }
    }
    val document = RcDocumentCodec.decode(bytes)
    runSkikoComposeUiTest(Size(720f, 400f), Density(2f)) {
      mainClock.autoAdvance = false
      setContent { RcComposePlayer(document, Modifier.fillMaxSize()) }
      for ((name, advance) in listOf("t0" to 16L, "t2s" to 2_000L)) {
        mainClock.advanceTimeBy(advance)
        waitForIdle()
        ImageIO.write(
          onRoot().captureToImage().toAwtImage(),
          "png",
          directory.resolve("mesh-gradient-$name.png"),
        )
      }
    }
  }

  private fun assertNear(a: Color, b: Color) =
    assertTrue(
      abs(a.red - b.red) < 0.05f && abs(a.green - b.green) < 0.05f && abs(a.blue - b.blue) < 0.05f,
      "$a ~ $b",
    )
}

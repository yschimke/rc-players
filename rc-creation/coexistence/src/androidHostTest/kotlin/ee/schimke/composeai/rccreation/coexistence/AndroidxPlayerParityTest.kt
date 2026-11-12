@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rccreation.coexistence

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteContentScale
import ee.schimke.composeai.rccreation.compose.layout.RemoteImage
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.state.rb
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One content, written by rc-players' creation library and by AndroidX's, compared operation for
 * operation and played by AndroidX's View player (`RemoteComposePlayer`, the player a launcher
 * widget and a notification run as the platform's copy).
 *
 * Where an operation is written is part of what it means to that player: a touch expression moves
 * only for touches on the component it is written in, text made from its value follows it only
 * when written after it there, a measured size belongs to the component its `ComponentValue` is
 * written in, and an expression of that size is evaluated with the canvas that holds it. The
 * creation library used to write every declaration ahead of the layout; these fail there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w600dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidxPlayerParityTest {
  private val context = RuntimeEnvironment.getApplication()

  private fun androidx(content: @Composable () -> Unit): ByteArray =
    androidxDocument(context, RcPlatformProfiles.ANDROIDX, content)

  /** How many pixels of row 10 are drawn: the width of a bar drawn from the left edge. */
  private fun Played.bar(): Int {
    val image = frame()
    return (0 until Width).count { image.getPixel(it, 10) != 0 }
  }

  /** Frames over half a second of the player's clock and of the wall clock, which eases follow. */
  private fun Played.settle() {
    repeat(30) {
      SystemClock.sleep(16)
      Thread.sleep(16)
      frame()
    }
  }

  @Test
  fun aSliderIsWrittenAsAndroidxWritesIt() {
    val ours = rcDocument { RcContent.Slider() }
    assertEquals(
      wireText(androidx { AndroidxContent.Slider() }),
      wireText(ours),
    )
    // The touch expression, then the text made from its value, in the canvas.
    val operations = wireOperations(ours)
    val canvas = operations.indexOfFirst { it is RcCanvasLayout }
    val touch = operations.indexOfFirst { it is RcTouchExpression }
    val text = operations.indexOfFirst { it is RcTextFromFloat }
    val merge = operations.indexOfFirst { it is RcTextMerge }
    assertTrue(canvas < touch && touch < text && text < merge, "$canvas $touch $text $merge")
  }

  @Test
  fun aSliderSendsWhereItWasDraggedTo() {
    for ((writer, bytes) in
      listOf(
        "AndroidX" to androidx { AndroidxContent.Slider() },
        "rc-players" to rcDocument { RcContent.Slider() },
      )) {
      val played = Played(context, bytes)
      assertEquals(60, played.bar(), "$writer: the bar at rest")
      // 60 px at half a unit a pixel: 30 to 60.
      played.drag(100f, 160f, 20f)
      assertEquals("set= 60", played.sent.last(), "$writer: sent ${played.sent}")
    }
  }

  @Test
  fun anExpressionOfACanvasSizeIsInItsCanvas() {
    val ours = rcDocument { RcContent.MeasuredCanvas() }
    assertEquals(wireText(androidx { AndroidxContent.MeasuredCanvas() }), wireText(ours))
    val operations = wireOperations(ours)
    val canvas = operations.indexOfFirst { it is RcCanvasLayout }
    assertTrue(operations.indexOfFirst { it is RcComponentValue } > canvas)
    assertTrue(operations.indexOfFirst { it is RcFloatExpression } > canvas)

    val played = Played(context, ours)
    played.settle()
    assertEquals(Width / 2, played.bar())
  }

  @Test
  fun anAnimatedFloatOfAMeasuredSizeInAStateLayoutBranchIsInItsBranch() {
    val ours = rcDocument { RcContent.MeasuredInStateLayout() }
    val theirs = androidx { AndroidxContent.MeasuredInStateLayout() }
    // All but the named index, which this library writes ahead of the root; see the next test.
    assertEquals(
      wireText(theirs, without = setOf("RcNamedVariable", "RcIntegerConstant")),
      wireText(ours, without = setOf("RcNamedVariable", "RcIntegerConstant")),
    )
    val operations = wireOperations(ours)
    val state = operations.indexOfFirst { it is RcStateLayout }
    val animated = operations.filterIsInstance<RcFloatExpression>().single()
    assertTrue(animated.animation != null)
    assertTrue(operations.indexOf(animated) > state)
    assertTrue(operations.indexOfFirst { it is RcComponentValue } > state)

    for ((writer, bytes) in listOf("AndroidX" to theirs, "rc-players" to ours)) {
      val played = Played(context, bytes)
      played.settle()
      assertEquals(Width / 4, played.bar(), writer)
    }
  }

  /**
   * The click and its action's name, inside the click modifier. AndroidX 1.0.0-alpha23 follows a
   * click with an `AccessibilitySemantics` that says nothing (no role, not clickable); androidx-main
   * writes one only for a `role`, and so does this library.
   */
  @Test
  fun aClickableIsWrittenAsAndroidxWritesIt() {
    val theirs = androidx { AndroidxContent.Clickable() }
    val ours = rcDocument { RcContent.Clickable() }
    val semantics = setOf("RcAccessibilitySemantics")
    assertEquals(wireText(theirs, without = semantics), wireText(ours))
    val click = wireOperations(ours).indexOfFirst { it is RcClickModifier }
    assertEquals("tap", (wireOperations(ours)[click + 1] as RcTextData).text)
  }

  /**
   * A named value two canvases read is one variable, as AndroidX 1.0.0-alpha23 writes it, and both
   * bars follow it when the host sets it. This library declares it ahead of the root, alpha23 in
   * the first canvas. (androidx-main, whose creation state forgets what a canvas declared when the
   * canvas ends, declares it again in the second: two variables of one name.)
   */
  @Test
  fun aNamedValueReadByTwoCanvasesIsOneVariable() {
    val theirs = androidx { AndroidxContent.NamedInTwoCanvases() }
    val ours = rcDocument { RcContent.NamedInTwoCanvases() }
    val named = setOf("RcNamedVariable", "RcFloatConstant")
    assertEquals(wireText(theirs, without = named), wireText(ours, without = named))
    val operations = wireOperations(ours)
    assertEquals(1, operations.count { it is RcNamedVariable })
    assertTrue(
      operations.indexOfFirst { it is RcNamedVariable } <
        operations.indexOfFirst { it is RcRootLayout }
    )

    for ((writer, bytes) in listOf("AndroidX" to theirs, "rc-players" to ours)) {
      val played = Played(context, bytes)
      fun bars(): List<Int> {
        val image = played.frame()
        return listOf(10, 30).map { y -> (0 until Width).count { image.getPixel(it, y) != 0 } }
      }
      assertEquals(listOf(20, 30), bars(), writer)
      played.player.setUserLocalFloat("level", 40f)
      repeat(3) { played.frame() }
      assertEquals(listOf(80, 120), bars(), "$writer, after the host set the value")
    }
  }

  /**
   * A bitmap carried inline is `TYPE_PNG_8888`, as AndroidX's writer stores an encoded image, and
   * AndroidX's player draws it. (This library wrote `TYPE_PNG`, the type of a URL's or a file's
   * image, which AndroidX's decoder refuses inline.)
   */
  @Test
  fun anInlineBitmapDrawsInAndroidxsPlayer() {
    val colour = android.graphics.Color.rgb(183, 28, 28)
    val image =
      Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }.asImageBitmap()
    val bytes = runBlocking {
      captureCommonRemoteDocument(
        RemoteCreationDisplayInfo(Width, Height, 160),
        encodePng = { bitmap ->
          ByteArrayOutputStream()
            .also { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray()
        },
      ) {
        RemoteImage(
          remember { image.rb },
          contentDescription = null,
          modifier = RemoteModifier.fillMaxSize(),
          contentScale = RemoteContentScale.FillBounds,
        )
      }
    }
    val data = wireOperations(bytes).filterIsInstance<RcBitmapData>().single()
    assertEquals(RcBitmapData.TYPE_PNG_8888, data.type)
    assertEquals(colour, Played(context, bytes).frame().getPixel(Width / 2, Height / 2))
  }

  /**
   * A named state first read in a layout is one the host can set. AndroidX's player keeps only
   * text, bitmaps and `ComponentData` operations of what a layout component's content holds
   * (`LayoutComponent.inflate`), and a `NamedVariable` is none of them: where AndroidX's writer
   * writes it, in the box that holds the state layout, the player drops it and the name is unknown.
   * This library writes it ahead of the root.
   */
  @Test
  fun aNamedStateFirstReadInALayoutCanBeSetByTheHost() {
    val ours = rcDocument { RcContent.MeasuredInStateLayout() }
    val operations = wireOperations(ours)
    assertTrue(
      operations.indexOfFirst { it is RcNamedVariable } <
        operations.indexOfFirst { it is RcRootLayout }
    )
    val played = Played(context, ours)
    played.settle()
    assertEquals(Width / 4, played.bar())
    played.player.setUserLocalInt("mode", 1)
    repeat(3) { played.frame() }
    assertEquals(0, played.bar(), "the other branch draws no bar")

    // AndroidX's own document: the name is not found, and the branch does not change. If this
    // starts to fail, AndroidX keeps the declaration and `RemoteDocumentWriter.inLayoutContent`
    // has no more reason to move it.
    val theirs = Played(context, androidx { AndroidxContent.MeasuredInStateLayout() })
    theirs.settle()
    theirs.player.setUserLocalInt("mode", 1)
    repeat(3) { theirs.frame() }
    assertEquals(Width / 4, theirs.bar(), "AndroidX's document now follows the named state")
  }
}

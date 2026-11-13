@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.composeai.rcwidget.launcher

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.RemoteDensity
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.layout.RemoteStateLayout
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.state.RemoteBoolean
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWidgetDocuments
import ee.schimke.composeai.rcwidget.creation.RcCaptureProfiles
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A canvas that reads its own measured width, played on the platform's own widget player, which
 * Android 16 (API 36) and 17 (API 37) ship: what a launcher and SystemUI's notifications draw.
 *
 * Android 16's player draws such a canvas as nothing when the document names the canvas itself in
 * the `ComponentValue` it writes for the canvas's width, as rc-players' writer did. It draws it
 * when the `ComponentValue` names the canvas's `CanvasContent`, which AndroidX's writer does (it
 * writes the id of the last component started). Android 17's player reads either.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36, 37], qualifiers = "mdpi")
class PlatformPlayerComponentValueTest {
  private val bar = Color.rgb(0xE5, 0x39, 0x35)

  /** A half-width bar in a box, from the canvas's own width, in a state layout a named flag picks. */
  private fun capture(): ByteArray = runBlocking {
    captureCommonRemoteDocument(
      creationDisplayInfo = RemoteCreationDisplayInfo(WIDTH, HEIGHT, 160),
      remoteDensity = RemoteDensity.Host,
      profile = RcCaptureProfiles.launcherWidgets,
    ) {
      MeasuredWidthBar()
    }
  }

  @Composable
  private fun MeasuredWidthBar() {
    val show = RemoteBoolean.createNamedRemoteBoolean("show_bar", false)
    RemoteColumn(RemoteModifier.fillMaxSize()) {
      RemoteBox(RemoteModifier.fillMaxWidth().height(BAR_HEIGHT.rdp)) {
        RemoteStateLayout(show.toRemoteInt(), 0, 1, modifier = RemoteModifier.fillMaxSize()) { shown ->
          if (shown == 1) {
            RemoteCanvas(RemoteModifier.fillMaxSize()) {
              drawRect(
                RemotePaint { color = RemoteColor(androidx.compose.ui.graphics.Color(bar)) },
                RemoteOffset(0f.rf, 0f.rf),
                RemoteSize(width * 0.5f.rf, height),
              )
            }
          } else {
            RemoteBox(RemoteModifier.fillMaxSize())
          }
        }
      }
    }
  }

  /** The capture as a host that binds no values plays it: the flag written in as a constant. */
  private fun baked(): ByteArray {
    val template = RcDocumentCodec.decode(capture(), RcWireLimits())
    val document = RcWidgetDocuments.bake(template, mapOf("show_bar" to RcHardcodedValue.BooleanValue(true)))
    return RcDocumentCodec.encode(document)
  }

  @Test
  fun aCanvasValueNamesTheContentItIsWrittenIn() {
    val operations = RcDocumentCodec.decode(baked(), RcWireLimits()).operations
    val canvas = operations.indexOfFirst { it is RcCanvasLayout }
    val content = operations.drop(canvas).filterIsInstance<RcCanvasContent>().first()
    val values = operations.drop(canvas).filterIsInstance<RcComponentValue>()
    assertTrue(values.isNotEmpty(), "the canvas reads its width")
    assertEquals(setOf(content.componentId), values.map { it.componentId }.toSet())
  }

  @Test
  fun aCanvasThatReadsItsMeasuredWidthDrawsOnThePlatformPlayer() {
    val pixels = play(baked())
    val red = pixels.count { it == bar }
    // Half of the 300px width, 24px high.
    assertTrue(red > WIDTH / 2 * BAR_HEIGHT * 9 / 10, "the bar is drawn on API ${Build.VERSION.SDK_INT}: $red red pixels")
    assertTrue(red <= WIDTH / 2 * BAR_HEIGHT, "the bar is half the width: $red red pixels")
  }

  /** [document] as a launcher widget, drawn over white: its pixels. */
  private fun play(document: ByteArray): IntArray {
    val views: RemoteViews = RcLauncherWidgets.remoteViews(document)
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val activity = controller.get()
    val frame = FrameLayout(activity)
    frame.setBackgroundColor(Color.WHITE)
    frame.addView(views.apply(activity, frame))
    activity.setContentView(frame, FrameLayout.LayoutParams(WIDTH, HEIGHT))
    val looper = shadowOf(Looper.getMainLooper())
    // The tasks the player posted, to a limit: a document that animates posts the next frame itself.
    repeat(MAIN_THREAD_TASKS) { looper.runOneTask() }
    fun exactly(px: Int) = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)
    frame.measure(exactly(WIDTH), exactly(HEIGHT))
    frame.layout(0, 0, WIDTH, HEIGHT)
    repeat(MAIN_THREAD_TASKS) { looper.runOneTask() }
    val image = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    // The first frame lays out and paints; the second draws what the first asked to repaint.
    repeat(2) { frame.draw(Canvas(image)) }
    frame.removeAllViews()
    controller.pause().stop().destroy()
    System.getProperty("rc.render.out")?.let { directory ->
      val file = File(directory, "sdk${Build.VERSION.SDK_INT}.png")
      file.parentFile?.mkdirs()
      file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    return IntArray(WIDTH * HEIGHT).also { image.getPixels(it, 0, WIDTH, 0, 0, WIDTH, HEIGHT) }
  }

  private companion object {
    const val WIDTH = 300
    const val HEIGHT = 60
    const val BAR_HEIGHT = 24
    const val MAIN_THREAD_TASKS = 50
  }
}

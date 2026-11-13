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
import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.layout.RemoteRow
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.layout.RemoteStateLayout
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.border
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.state.RemoteBoolean
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rccreation.compose.state.rsp
import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcplayer.edit.adaptTo
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
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
 * A document written for a newer, freer player (AndroidX's, at the current API level, with
 * `CoreText`) and rewritten by `rc-player-edit`'s `adaptTo` for the platform's launcher player is
 * drawn as the document written directly for that launcher is, on the platform's own player (API 36
 * and 37). Each version-dependent write in the creation writer (`docs/design/VERSION_QUIRKS.md`) is
 * in the content: a canvas that reads its measured size, a border, text and a canvas's paint.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36, 37], qualifiers = "mdpi")
class PlatformPlayerAdaptedDocumentTest {
  private val red = Color.rgb(0xE5, 0x39, 0x35)
  private val blue = Color.rgb(0x1E, 0x58, 0xD8)

  @Composable
  private fun Content() {
    val show = RemoteBoolean.createNamedRemoteBoolean("show_bar", false)
    RemoteColumn(RemoteModifier.fillMaxSize()) {
      // A canvas that reads its measured width (CanvasContent and its ComponentValue, level 7).
      RemoteBox(RemoteModifier.fillMaxWidth().height(20.rdp)) {
        RemoteStateLayout(show.toRemoteInt(), 0, 1, modifier = RemoteModifier.fillMaxSize()) { shown ->
          if (shown == 1) {
            RemoteCanvas(RemoteModifier.fillMaxSize()) {
              drawRect(
                RemotePaint { color = RemoteColor(androidx.compose.ui.graphics.Color(red)) },
                RemoteOffset(0f.rf, 0f.rf),
                RemoteSize(width * 0.5f.rf, height),
              )
            }
          } else {
            RemoteBox(RemoteModifier.fillMaxSize())
          }
        }
      }
      RemoteRow(RemoteModifier.fillMaxWidth().height(40.rdp)) {
        // A border (the legacy form, level 7).
        RemoteBox(
          RemoteModifier.size(40.rdp)
            .border(3.rdp, RemoteColor(androidx.compose.ui.graphics.Color(blue))),
        )
        // Text: CoreText, or TextLayout where the profile has none.
        RemoteText("Hi".rs, fontSize = 20.rsp)
        // A canvas's paint, and text drawn in it (the paint's font axis where there is no CoreText).
        RemoteCanvas(RemoteModifier.size(80.rdp, 40.rdp)) {
          drawCircle(
            RemotePaint { color = RemoteColor(androidx.compose.ui.graphics.Color(red)) },
            12f.rf,
            RemoteOffset(20f.rf, 20f.rf),
          )
          drawText(
            "Ab".rs,
            40f.rf,
            28f.rf,
            RemotePaint {
              color = RemoteColor(androidx.compose.ui.graphics.Color(blue))
              textSize = 22f.rf
            },
          )
        }
      }
    }
  }

  private fun capture(profile: ee.schimke.composeai.rccreation.common.Profile): ByteArray = runBlocking {
    captureCommonRemoteDocument(
      creationDisplayInfo = RemoteCreationDisplayInfo(WIDTH, HEIGHT, 160),
      profile = profile,
    ) {
      Content()
    }
  }

  private fun baked(bytes: ByteArray) =
    RcWidgetDocuments.bake(
      RcDocumentCodec.decode(bytes, RcWireLimits()),
      mapOf("show_bar" to RcHardcodedValue.BooleanValue(true)),
    )

  /** The document written directly for the launcher this API level has. */
  private fun direct(): ByteArray {
    val target = RcLauncherWidgets.widgetTargetFor()
    return RcDocumentCodec.encode(baked(capture(RcCaptureProfiles.forTarget(target))))
  }

  /** The document written for AndroidX's player, rewritten for the launcher this API level has. */
  private fun adapted(): ByteArray {
    val target = RcLauncherWidgets.widgetTargetFor()
    val adaptation =
      baked(capture(RcCaptureProfiles.androidXExperimental))
        .adaptTo(target.profile, target.approximations, RcDisplay(1f))
    assertEquals(emptyList(), adaptation.violations)
    return RcDocumentCodec.encode(adaptation.document)
  }

  @Test
  fun aDocumentAdaptedForTheLauncherDrawsAsOneWrittenForIt() {
    val written = play(direct(), "direct")
    val rewritten = play(adapted(), "adapted")
    // The measured bar is drawn at all: half of the width, 20 high.
    assertTrue(written.count { it == red } > WIDTH / 2 * 20 * 9 / 10, "direct: ${written.count { it == red }} red")
    assertEquals(
      written.count { it == red },
      rewritten.count { it == red },
      "red pixels on API ${Build.VERSION.SDK_INT}",
    )
    assertEquals(written.count { it == blue }, rewritten.count { it == blue }, "blue pixels")
    assertTrue(written.contentEquals(rewritten), "the pixels differ on API ${Build.VERSION.SDK_INT}")
  }

  /**
   * A launcher's view is resized and played again: every launcher capture profile turns paint
   * measuring off (`RcCaptureProfiles`), and so does the adaptation, where the header can say it.
   */
  @Test
  @Config(sdk = [37])
  fun aDocumentAdaptedForTheLauncherTurnsPaintMeasuringOffAsTheCaptureProfileDoes() {
    fun paintMeasure(bytes: ByteArray) =
      RcDocumentCodec.decode(bytes, RcWireLimits())
        .header
        .properties
        .single { it.key == RcHeader.FEATURE_PAINT_MEASURE }
        .value
    assertEquals(RcHeaderValue.IntValue(0), paintMeasure(direct()))
    assertEquals(RcHeaderValue.IntValue(0), paintMeasure(adapted()))
  }

  private fun play(document: ByteArray, name: String): IntArray {
    val views = RcLauncherWidgets.remoteViews(document)
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val activity = controller.get()
    val frame = FrameLayout(activity)
    frame.setBackgroundColor(Color.WHITE)
    frame.addView(views.apply(activity, frame))
    activity.setContentView(frame, FrameLayout.LayoutParams(WIDTH, HEIGHT))
    val looper = shadowOf(Looper.getMainLooper())
    repeat(MAIN_THREAD_TASKS) { looper.runOneTask() }
    fun exactly(px: Int) = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)
    frame.measure(exactly(WIDTH), exactly(HEIGHT))
    frame.layout(0, 0, WIDTH, HEIGHT)
    repeat(MAIN_THREAD_TASKS) { looper.runOneTask() }
    val image = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    repeat(2) { frame.draw(Canvas(image)) }
    frame.removeAllViews()
    controller.pause().stop().destroy()
    System.getProperty("rc.render.out")?.let { directory ->
      val file = File(directory, "$name-sdk${Build.VERSION.SDK_INT}.png")
      file.parentFile?.mkdirs()
      file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    return IntArray(WIDTH * HEIGHT).also { image.getPixels(it, 0, WIDTH, 0, 0, WIDTH, HEIGHT) }
  }

  private companion object {
    const val WIDTH = 300
    const val HEIGHT = 60
    const val MAIN_THREAD_TASKS = 50
  }
}

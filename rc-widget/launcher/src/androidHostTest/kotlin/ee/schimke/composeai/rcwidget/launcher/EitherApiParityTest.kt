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
import androidx.compose.ui.graphics.asImageBitmap
import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.launcher.cmp.captureLauncherWidget as captureThroughCmp
import ee.schimke.composeai.rcwidget.launcher.cmp.launcherCaptureProfile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One widget, written twice — against rc-players' creation API (`rc-widget-launcher-cmp`) and
 * against AndroidX's (`rc-widget-launcher`) — and sent through the same `rc-widget-launcher-core`:
 * the documents are made of the same operations, hold the same texts and taps, and draw the same
 * pixels on the platform's own widget player (Android 16 and 17). That is the contract of
 * `docs/design/LAUNCHER_EITHER_API.md`: which API authored a widget is not visible to the launcher.
 *
 * The content has what each version-dependent write in the creation writer is about
 * (`docs/design/VERSION_QUIRKS.md`): a canvas that reads its measured width (the `ComponentValue`
 * fix, rc-players#875), a border, text, a canvas's paint, a size breakpoint and a numeric tap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36, 37], qualifiers = "mdpi")
class EitherApiParityTest {
  private val context = RuntimeEnvironment.getApplication()
  private val values = mapOf("show_bar" to RcHardcodedValue.BooleanValue(true))

  private fun cmp(widthDp: Int = WIDTH): RcLauncherWidgetCapture = runBlocking {
    // No `Context`: rc-players' capture needs none.
    captureThroughCmp(
      RcWidgetCaptureSize(widthDp, HEIGHT),
      densityDpi = 160,
      values = values,
    ) {
      CmpContent()
    }
  }

  private fun androidx(widthDp: Int = WIDTH): RcLauncherWidgetCapture = runBlocking {
    captureLauncherWidget(
      context,
      RcWidgetCaptureSize(widthDp, HEIGHT),
      densityDpi = 160,
      values = values,
    ) {
      AndroidxContent()
    }
  }

  private val image =
    Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
      .apply { eraseColor(Color.rgb(183, 28, 28)) }
      .asImageBitmap()

  private fun cmpImage() = runBlocking {
    captureThroughCmp(RcWidgetCaptureSize(WIDTH, HEIGHT), densityDpi = 160) { CmpImageContent(image) }
  }

  private fun androidxImage() = runBlocking {
    captureLauncherWidget(context, RcWidgetCaptureSize(WIDTH, HEIGHT), densityDpi = 160) {
      AndroidxImageContent(image)
    }
  }

  private fun operations(bytes: ByteArray) =
    RcDocumentCodec.decode(bytes, RcWireLimits()).operations

  @Test
  fun theCaptureProfilesAreForTheSameLauncher() {
    val sdk = Build.VERSION.SDK_INT
    val androidx = RcLauncherWidgets.profileFor(sdk)
    val cmp = launcherCaptureProfile(sdk)
    assertEquals(androidx.apiLevel, cmp.apiLevel)
    assertEquals(androidx.operationsProfiles, cmp.operationsProfiles)
    assertEquals(androidx.supportedOperations.toSet(), cmp.supportedOperations)
  }

  /**
   * What the two writers write differently without it being drawn differently, and so is left out
   * of the structure compared: AndroidX's `clickable` adds accessibility semantics, rc-players'
   * border writes the box's `ComponentValue`s, the order in which text data is declared differs,
   * and `RcNoArg` is every container end.
   */
  private val notStructure = setOf("RcAccessibilitySemantics", "RcComponentValue", "RcTextData", "RcNoArg")

  @Test
  fun bothDocumentsAreMadeOfTheSameLayoutsModifiersAndDrawsTextsAndTaps() {
    val fromCmp = cmp()
    val fromAndroidx = androidx()
    fun structure(bytes: ByteArray) =
      operations(bytes).map { it::class.simpleName }.filter { it !in notStructure }
    assertEquals(
      structure(fromAndroidx.bytes),
      structure(fromCmp.bytes),
      "the layouts, modifiers and draws on API ${Build.VERSION.SDK_INT}",
    )
    // AndroidX also declares the font-variation axis name ("wght") as string data; it is no text.
    fun texts(bytes: ByteArray) =
      operations(bytes).filterIsInstance<RcTextData>().map { it.text }.filter { it != "wght" }
    assertEquals(texts(fromAndroidx.bytes), texts(fromCmp.bytes))
    // A 300dp slot is past the 140dp breakpoint.
    assertTrue("Roomy" in texts(fromCmp.bytes) && "Compact" !in texts(fromCmp.bytes))
    assertEquals(fromAndroidx.actions, fromCmp.actions)
    assertEquals(listOf("{\"open\":\"kitchen\"}"), fromCmp.actions.values.toList())
    // And the slot picks the other layout alike.
    fun narrow(capture: RcLauncherWidgetCapture) = texts(capture.bytes)
    assertTrue("Compact" in narrow(cmp(100)) && "Compact" in narrow(androidx(100)))
  }

  /** An inline bitmap is carried in the document by either entry point, and drawn alike. */
  @Test
  fun anInlineBitmapIsEmbeddedByBothAndDrawnTheSame() {
    val red = Color.rgb(183, 28, 28)
    val pixels =
      listOf(cmpImage(), androidxImage()).map { capture ->
        val data = operations(capture.bytes).filterIsInstance<RcBitmapData>()
        assertTrue(data.isNotEmpty() && data.all { it.data.isNotEmpty() }, "bitmap data embedded")
        play(capture.bytes, "image").also { assertTrue(it.count { p -> p == red } > WIDTH * HEIGHT / 2) }
      }
    assertTrue(pixels[0].contentEquals(pixels[1]), "pixels on API ${Build.VERSION.SDK_INT}")
  }

  @Test
  fun bothPlayOnTheLauncherTheyWereCapturedFor() {
    val target = RcPlatformWidgets.targetFor()
    for ((name, capture) in listOf("cmp" to cmp(), "androidx" to androidx())) {
      assertEquals(emptyList(), RcPlatformWidgets.violations(capture.bytes, target), name)
      // The same core turns each into what the platform player is handed.
      assertTrue(platformWidgetDocument(capture.bytes, target).isNotEmpty(), name)
    }
  }

  @Test
  fun bothDrawTheSamePixelsOnThePlatformPlayer() {
    val fromCmp = play(cmp().bytes, "cmp")
    val fromAndroidx = play(androidx().bytes, "androidx")
    val red = Color.rgb(0xE5, 0x39, 0x35)
    // The measured bar is drawn at all (half of the width, 20 high), by either.
    assertTrue(
      fromCmp.count { it == red } > WIDTH / 2 * 20 * 9 / 10,
      "the bar is drawn on API ${Build.VERSION.SDK_INT}: ${fromCmp.count { it == red }} red pixels",
    )
    val different = fromCmp.indices.count { fromCmp[it] != fromAndroidx[it] }
    assertEquals(0, different, "pixels that differ between the two APIs on API ${Build.VERSION.SDK_INT}")
  }

  /** [document] as a launcher widget, drawn over white: its pixels. */
  private fun play(document: ByteArray, name: String): IntArray {
    val views = RcPlatformWidgets.remoteViews(document)
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
      val file = File(directory, "either-$name-sdk${Build.VERSION.SDK_INT}.png")
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

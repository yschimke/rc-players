@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.launcher.cmp

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteImage
import ee.schimke.composeai.rccreation.compose.state.rb
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.clickable
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.creation.RcCaptureProfiles
import ee.schimke.composeai.rcwidget.creation.RcSizeBreakpoint
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.RcPlatformWidgets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The CMP capture entry point, called with no Android `Context`: rc-players' capture asks the
 * platform only for static settings (the 24-hour clock), so a provider can capture from anywhere,
 * as a design server does. Robolectric stands in for the platform, not for a `Context`.
 * (`:rc-widget-launcher`'s `EitherApiParityTest` holds what it writes to what AndroidX's entry
 * point writes, and plays both.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RcLauncherCmpCaptureTest {
  private fun capture(widthDp: Int) = runBlocking {
    captureLauncherWidget(RcWidgetCaptureSize(widthDp, 84), densityDpi = 160) {
      val tap = rcWidgetHostAction("open", "{\"open\":\"kitchen\"}")
      RemoteBox(RemoteModifier.fillMaxSize().clickable(tap)) {
        RcSizeBreakpoint(intArrayOf(140)) { tier ->
          RemoteText(if (tier == 0) "Compact".rs else "Roomy".rs)
        }
      }
    }
  }

  private fun texts(bytes: ByteArray) =
    RcDocumentCodec.decode(bytes, RcWireLimits()).operations.filterIsInstance<RcTextData>().map {
      it.text
    }

  @Test
  fun aCaptureNeedsNoContextCollectsItsTapsAndPicksItsLayoutForTheSlot() {
    val small = capture(72)
    val wide = capture(220)
    assertEquals(listOf("{\"open\":\"kitchen\"}"), small.actions.values.toList())
    // The same payload has the same id, so a recapture keeps its PendingIntents.
    assertEquals(small.actions.keys, wide.actions.keys)
    assertTrue("Compact" in texts(small.bytes) && "Roomy" !in texts(small.bytes))
    assertTrue("Roomy" in texts(wide.bytes) && "Compact" !in texts(wide.bytes))
  }

  @Test
  fun aCaptureIsForTheLauncherItsProfileIs() {
    for ((sdk, target) in
      listOf(36 to RcTargetProfiles.WIDGETS_V6, 37 to RcTargetProfiles.WIDGETS_V7)) {
      assertEquals(target, RcPlatformWidgets.targetFor(sdk))
      val capture = runBlocking {
        captureLauncherWidget(
          RcWidgetCaptureSize(144, 84),
          profile = launcherCaptureProfile(sdk),
          densityDpi = 160,
        ) {
          RemoteText("Hi".rs)
        }
      }
      // What the player decodes, and nothing it doesn't: the profile left out what it can't read,
      // and `platformWidgetDocument` writes the header version the launcher reads.
      assertEquals(emptyList(), RcPlatformWidgets.violations(capture.bytes, target), "sdk $sdk")
    }
  }

  @Test
  fun theLauncherProfileIsTheCmpOneForEachLauncher() {
    assertSame(RcCaptureProfiles.launcherWidgets, launcherCaptureProfile(36))
    assertSame(RcCaptureProfiles.systemThemedLauncherWidgets, launcherCaptureProfile(37))
  }

  @Test
  fun anInlineBitmapIsEmbeddedByDefaultAndDroppedWithoutAnEncoder() {
    val image =
      android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
    fun bitmaps(encodePng: ((androidx.compose.ui.graphics.ImageBitmap) -> ByteArray?)?) =
      RcDocumentCodec.decode(
          runBlocking {
              captureLauncherWidget(RcWidgetCaptureSize(100, 100), densityDpi = 160, encodePng = encodePng) {
                RemoteImage(remember { image.rb }, contentDescription = null)
              }
            }
            .bytes,
          RcWireLimits(),
        )
        .operations
        .filterIsInstance<RcBitmapData>()
    assertTrue(bitmaps(AndroidPngEncoder).single().data.isNotEmpty())
    assertEquals(1, bitmaps(AndroidPngEncoder).size)
    assertTrue(bitmaps(null).isEmpty(), "no encoder, no bitmap")
  }
}

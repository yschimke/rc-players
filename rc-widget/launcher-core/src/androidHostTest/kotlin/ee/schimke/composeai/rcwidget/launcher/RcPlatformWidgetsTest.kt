package ee.schimke.composeai.rcwidget.launcher

import android.content.Intent
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcwidget.RcWidgetActionMetadata
import ee.schimke.composeai.rcwidget.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.RcWidgetTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `rc-widget-launcher-core` on its own: no creation API on its classpath, so everything here is
 * what either capture entry point gets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RcPlatformWidgetsTest {
  @Test
  fun eachLauncherHasItsPlayerAndTarget() {
    assertEquals(RcTargetProfiles.WIDGETS_V6, RcPlatformWidgets.targetFor(36))
    assertEquals(RcTargetProfiles.WIDGETS_V7, RcPlatformWidgets.targetFor(37))
    assertSame(RcWidgetTargets.LauncherAndroid16, RcPlatformWidgets.widgetTargetFor(36))
    assertSame(RcWidgetTargets.Launcher, RcPlatformWidgets.widgetTargetFor(37))
  }

  @Test
  fun aTapIntentCarriesItsWidgetActionAndMetadata() {
    val intent =
      Intent()
        .putExtra(RcPlatformWidgets.EXTRA_WIDGET_ID, 7)
        .putExtra(RcPlatformWidgets.EXTRA_ACTION_ID, 42)
        .putExtra(RcPlatformWidgets.EXTRA_ACTION_PAYLOAD, "{}")
        .putExtra(
          RcPlatformWidgets.EXTRA_REMOTE_COMPOSE_METADATA,
          RcWidgetActionMetadata.encode(RcWidgetActionMetadata("{}", "lock", "locked", "10:00:00")),
        )
    val tap = assertNotNull(RcPlatformWidgets.tapOf(intent))
    assertEquals(7, tap.widgetId)
    assertEquals(42, tap.actionId)
    assertEquals("locked", tap.metadata?.currentValue)
    assertNull(RcPlatformWidgets.tapOf(Intent()))
  }

  @Test
  fun aDocumentTheTargetCannotReadComesBackAsItWas() {
    val notADocument = byteArrayOf(1, 2, 3)
    assertSame(notADocument, RcPlatformWidgets.forDisplay(notADocument, density = 2f))
  }

  @Test
  fun slotsAndCells() {
    assertEquals(RcWidgetCaptureSize(144, 168), RcWidgetSizing.cells(2, 2))
    assertEquals(RcWidgetCaptureSize(72, 84), RcWidgetSizing.slotOf(android.os.Bundle()))
    assertEquals(450, RcWidgetSizing.dpToPx(300, 240))
  }
}

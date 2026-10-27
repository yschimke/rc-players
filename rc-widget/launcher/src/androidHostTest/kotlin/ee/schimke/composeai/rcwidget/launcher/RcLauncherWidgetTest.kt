@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.composeai.rcwidget.launcher

import android.content.Intent
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operations
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.state.rs
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWidgetActionMetadata
import ee.schimke.composeai.rcwidget.creation.RcSizeBreakpoint
import ee.schimke.composeai.rcwidget.creation.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RcLauncherWidgetTest {
  private val context = RuntimeEnvironment.getApplication()

  @Test
  fun widgetHeaderIsAcceptedByAndroid16WireVersion() {
    for (profile in listOf(widgetsProfile, systemThemedWidgetsProfile)) {
      val bytes = RemoteComposeWriter.obtain(100, 100, profile).encodeToByteArray()
      val document =
        CoreDocument().apply {
          initFromBuffer(
            RemoteComposeBuffer.fromInputStream(platformWidgetDocument(bytes).inputStream())
          )
        }
      assertTrue(document.canBeDisplayed(1, 0, 0L))
    }
  }

  @Test
  fun launcherProfilesLeaveOutWhatAndroid16CannotRead() {
    for (profile in listOf(widgetsProfile, systemThemedWidgetsProfile)) {
      assertFalse(Operations.LAYOUT_FLOW in profile.supportedOperations)
      assertFalse(Operations.CORE_TEXT in profile.supportedOperations)
    }
    assertFalse(Operations.COLOR_THEME in widgetsProfile.supportedOperations)
    assertTrue(Operations.COLOR_THEME in systemThemedWidgetsProfile.supportedOperations)
    assertEquals(widgetsProfile, RcLauncherWidgets.profileFor(36))
    assertEquals(systemThemedWidgetsProfile, RcLauncherWidgets.profileFor(37))
  }

  @Test
  fun aLocalCaptureCollectsItsTapsAndPicksItsLayoutForTheSlot() {
    fun capture(widthDp: Int) = runBlocking {
      captureLauncherWidget(context, RcWidgetCaptureSize(widthDp, 84), widgetsProfile) {
        val tap = rcWidgetHostAction("open", "{\"open\":\"kitchen\"}")
        RemoteBox(RemoteModifier.fillMaxSize().clickable(tap)) {
          RcSizeBreakpoint(intArrayOf(140)) { tier ->
            RemoteText(if (tier == 0) "Compact".rs else "Roomy".rs)
          }
        }
      }
    }
    val small = capture(72)
    val wide = capture(220)
    assertEquals(listOf("{\"open\":\"kitchen\"}"), small.actions.values.toList())
    // The same payload has the same id, so a recapture keeps its PendingIntents.
    assertEquals(small.actions.keys, wide.actions.keys)
    fun texts(bytes: ByteArray) =
      RcDocumentCodec.decode(bytes, RcWireLimits()).operations.filterIsInstance<RcTextData>().map {
        it.text
      }
    assertTrue("Compact" in texts(small.bytes) && "Roomy" !in texts(small.bytes))
    assertTrue("Roomy" in texts(wide.bytes) && "Compact" !in texts(wide.bytes))
    // What the launcher's player is handed still loads.
    val document =
      CoreDocument().apply {
        initFromBuffer(
          RemoteComposeBuffer.fromInputStream(platformWidgetDocument(small.bytes).inputStream())
        )
      }
    assertTrue(document.canBeDisplayed(1, 0, 0L))
  }

  @Test
  fun aTapIntentCarriesItsWidgetActionAndMetadata() {
    val intent =
      Intent()
        .putExtra(RcLauncherWidgets.EXTRA_WIDGET_ID, 7)
        .putExtra(RcLauncherWidgets.EXTRA_ACTION_ID, 42)
        .putExtra(RcLauncherWidgets.EXTRA_ACTION_PAYLOAD, "{}")
        .putExtra(
          RcLauncherWidgets.EXTRA_REMOTE_COMPOSE_METADATA,
          RcWidgetActionMetadata.encode(RcWidgetActionMetadata("{}", "lock", "locked", "10:00:00")),
        )
    val tap = assertNotNull(RcLauncherWidgets.tapOf(intent))
    assertEquals(7, tap.widgetId)
    assertEquals(42, tap.actionId)
    assertEquals("locked", tap.metadata?.currentValue)
  }

  @Test
  fun gridBoundsAndSlots() {
    assertEquals(RcWidgetCaptureSize(144, 168), RcWidgetSizing.cells(2, 2))
    assertEquals(RcWidgetCaptureSize(72, 84), RcWidgetSizing.slotOf(android.os.Bundle()))
  }
}

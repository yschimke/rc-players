@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.composeai.rcwidget.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.ui.graphics.Color
import androidx.glance.wear.WearWidgetBrush
import androidx.glance.wear.core.ContainerInfo
import androidx.glance.wear.core.WearWidgetParams
import androidx.glance.wear.core.WidgetInstanceId
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.edit.namedVariables
import ee.schimke.composeai.rcplayer.edit.validateFor
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWearContainer
import ee.schimke.composeai.rcwidget.RcWearContainerType
import ee.schimke.composeai.rcwidget.RcWearFrame
import ee.schimke.composeai.rcwidget.RcWearHost
import ee.schimke.composeai.rcwidget.creation.RcWearBackground
import ee.schimke.composeai.rcwidget.creation.RcWearEdge
import ee.schimke.composeai.rcwidget.creation.RcWearGlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RcWearWidgetTemplateTest {
  private val context = RuntimeEnvironment.getApplication()

  private fun params(frame: RcWearFrame): WearWidgetParams =
    WearWidgetParams(
      WidgetInstanceId("widgets", 1),
      frame.type.glanceType,
      frame.widthDp,
      frame.heightDp,
      frame.horizontalPaddingDp,
      frame.verticalPaddingDp,
      frame.cornerRadiusDp,
    )

  private fun decode(bytes: ByteArray) = RcDocumentCodec.decode(bytes, RcWireLimits())

  @Test
  fun containerTypesAreGlanceWears() {
    assertEquals(ContainerInfo.CONTAINER_TYPE_SMALL, RcWearContainerType.SMALL.glanceType)
    assertEquals(ContainerInfo.CONTAINER_TYPE_LARGE, RcWearContainerType.LARGE.glanceType)
    val frame = RcWearHost.Galaxy.large
    assertEquals(frame, params(frame).toRcWearFrame())
  }

  @Test
  fun oneLocalCaptureIsEveryHostsPlayableWidget() {
    runBlocking {
    // A custom container: a glow and a two-colour edge, drawn to the host's own corner radius.
    val background =
      RcWearBackground(
        base = Color(0xFF101820),
        layers = listOf(RcWearGlow(listOf(Color(0x664FC3F7), Color.Transparent), 0.5f, 0f, 0.8f)),
        edge = RcWearEdge(listOf(Color(0xFF4FC3F7), Color(0xFFFFB74D)), widthDp = 2f),
      )
    val template =
      RcWearWidgetTemplate.capture(context, background = background) { RemoteText("Kitchen".rs) }
    assertTrue(RcWearContainer.namesFrame(decode(template)))
    for (host in RcWearHost.All + RcWearHost.Default) {
      for (frame in listOf(host.small, host.large)) {
        val widget =
          assertNotNull(RcWearWidgetTemplate.instantiate(context, template, params(frame)), "$host")
        val document = decode(widget.rcDocument)
        assertEquals(emptyList(), document.validateFor(RcTargetProfiles.WEAR_WIDGETS), "$frame")
        // Every frame value is written in; none is left for a host that can't bind it.
        assertTrue(document.namedVariables.none { it.name.removePrefix("USER:") in RcWearContainer.FRAME_NAMES })
      }
    }
    }
  }

  @Test
  fun aHostFramedDocumentLeavesItsFrameToTheWatch() {
    runBlocking {
    val document =
      RcHostFramedWearWidgetDocument(WearWidgetBrush.Companion) { RemoteText("Hi".rs) }
        .captureRawContent(context, params(RcWearHost.Pixel.large), false)
    assertTrue(RcWearContainer.namesFrame(decode(document.rcDocument)))
    }
  }

  @Test
  fun tapsSendTheWidgetsPendingIntent() {
    runBlocking {
    val raw =
      RcHostFramedWearWidgetDocument(WearWidgetBrush.Companion) { RemoteText("Hi".rs) }
        .captureRawContent(context, params(RcWearHost.Pixel.large), false)
    val intent =
      PendingIntent.getActivity(context, 0, Intent("open"), PendingIntent.FLAG_IMMUTABLE)
    val sent = RcWearWidgetTaps.sending(raw, setOf("open"), intent)
    assertNotNull(sent.extras.getBundle(RcWearWidgetTaps.PENDING_INTENTS))
    }
  }

  @Test
  fun framesAreRememberedPerContainer() {
    val store = RcWearFrameStore(context)
    assertEquals(RcWearHost.Default.small, store.frameFor(RcWearContainerType.SMALL))
    store.remember(params(RcWearHost.Galaxy.large))
    assertEquals(RcWearHost.Galaxy.large, store.frameFor(RcWearContainerType.LARGE))
    assertEquals(RcWearHost.Default.small, store.frameFor(RcWearContainerType.SMALL))
  }

  @Test
  fun aConfigurationRequestNamesItsWidget() {
    val intent = Intent().putExtra(androidx.glance.wear.GlanceWearWidget.EXTRA_CONFIG_WIDGET_ID, 5)
    assertEquals(5, RcWearWidgetConfiguration.requestOf(intent)?.instanceId)
    assertEquals(null, RcWearWidgetConfiguration.requestOf(Intent()))
  }
}

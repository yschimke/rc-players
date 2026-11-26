@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.launcher

import android.content.Intent
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.utilities.AnimatedFloatExpression
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWidgetActionMetadata
import ee.schimke.composeai.rcwidget.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.RcWidgetTargets
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    assertSame(notADocument, prepareForViewPlayer(notADocument, density = 2f))
  }

  @Test
  fun prepareForViewPlayerBakesHostDensityLayoutSpacing() {
    val multiply = RcFloatWord(AnimatedFloatExpression.MUL.toRawBits())
    val hostDensityDoc =
      RcDocumentCodec.encode(
        RcDocument(
          RcHeader(
            RcVersion(1, 1, 0),
            legacyWidth = 200,
            legacyHeight = 100,
            legacyCapabilities = 0,
          ),
          listOf(
            RcFloatExpression(
              42,
              listOf(
                RcFloatWord(0x7fc00000 or RcSystemVariables.DENSITY),
                RcFloatWord.literal(4f),
                multiply,
              ),
              null,
            ),
            RcColumnLayout(-3, -1, 1, 4, spacedBy = RcFloatWord(0x7fc00000 or 42)),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
        ),
      )

    val prepared = prepareForViewPlayer(hostDensityDoc, density = 3f)
    val column =
      RcDocumentCodec.decode(prepared, RcWireLimits())
        .operations
        .filterIsInstance<RcColumnLayout>()
        .single()
    assertEquals(RcFloatWord.literal(12f), column.spacedBy)
    assertFalse(column.spacedBy.isNaNEncoded)

    // Already-literal documents are returned untouched without re-encoding.
    assertSame(prepared, prepareForViewPlayer(prepared, density = 3f))

    // AndroidX CoreDocument decodes the prepared document cleanly.
    val core =
      CoreDocument().apply {
        initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(prepared)))
      }
    assertNotNull(core)
  }

  @Test
  fun slotsAndCells() {
    assertEquals(RcWidgetCaptureSize(144, 168), RcWidgetSizing.cells(2, 2))
    assertEquals(RcWidgetCaptureSize(72, 84), RcWidgetSizing.slotOf(android.os.Bundle()))
    assertEquals(450, RcWidgetSizing.dpToPx(300, 240))
  }
}

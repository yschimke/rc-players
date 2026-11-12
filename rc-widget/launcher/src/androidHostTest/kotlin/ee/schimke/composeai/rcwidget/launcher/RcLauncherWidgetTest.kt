@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.composeai.rcwidget.launcher

import android.content.Intent
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operations
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.RemoteDensity
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.edit.validateFor
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWidgetActionMetadata
import ee.schimke.composeai.rcwidget.creation.RcCaptureProfiles
import ee.schimke.composeai.rcwidget.creation.androidx.RcSizeBreakpoint
import ee.schimke.composeai.rcwidget.creation.androidx.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.creation.androidx.rcWidgetHostAction
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

  /**
   * `rc-widget-creation`'s capture profiles for rc-players' own creation API are these AndroidX
   * ones: the same level, bits and operations, and so the same fallbacks.
   */
  @Test
  fun theCmpCaptureProfilesAreTheAndroidxOnes() {
    val pairs =
      listOf(
        widgetsProfile to RcCaptureProfiles.launcherWidgets,
        systemThemedWidgetsProfile to RcCaptureProfiles.systemThemedLauncherWidgets,
      )
    for ((androidx, cmp) in pairs) {
      assertEquals(androidx.apiLevel, cmp.apiLevel)
      assertEquals(androidx.operationsProfiles, cmp.operationsProfiles)
      assertEquals(androidx.supportedOperations.toSet(), cmp.supportedOperations)
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
    // Each profile only lets the writer emit what its launcher's own table decodes.
    for (sdk in listOf(36, 37)) {
      val unsupported =
        RcLauncherWidgets.profileFor(sdk).supportedOperations -
          RcLauncherWidgets.targetFor(sdk).supportedOpcodes
      assertEquals(emptySet(), unsupported, "sdk $sdk")
    }
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
  fun eachLauncherGetsTheHeaderVersionItReadsAndTheTargetItsProfileIsFor() {
    // The header's minor version: the int after the opcode and the major version.
    fun minor(bytes: ByteArray) = java.nio.ByteBuffer.wrap(bytes, 5, 4).int
    for ((sdk, wantMinor) in listOf(36 to 0, 37 to 1)) {
      val target = RcLauncherWidgets.targetFor(sdk)
      val bytes = RemoteComposeWriter.obtain(100, 100, RcLauncherWidgets.profileFor(sdk)).encodeToByteArray()
      // An API 37 launcher reads 1.0 as level 6, whose table has no ColorTheme (196): keep 1.1.
      assertEquals(wantMinor, minor(platformWidgetDocument(bytes, target)), "sdk $sdk")
    }
    assertEquals(RcTargetProfiles.WIDGETS_V6, RcLauncherWidgets.targetFor(36))
    assertEquals(RcTargetProfiles.WIDGETS_V7, RcLauncherWidgets.targetFor(37))
    assertEquals(0, minor(platformWidgetDocument(RemoteComposeWriter.obtain(100, 100, widgetsProfile).encodeToByteArray())))
  }

  @Test
  fun aCapturedWidgetPlaysOnTheLauncherItWasCapturedFor() {
    for (sdk in listOf(36, 37)) {
      val captured = runBlocking {
        captureLauncherWidget(context, RcWidgetCaptureSize(140, 84), RcLauncherWidgets.profileFor(sdk)) {
          RemoteBox(RemoteModifier.fillMaxSize()) { RemoteText("Kitchen".rs) }
        }
      }
      assertEquals(
        emptyList(),
        RcLauncherWidgets.violations(captured.bytes, RcLauncherWidgets.targetFor(sdk)),
        "sdk $sdk",
      )
    }
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

  /**
   * Android 17's `validateNestedViews` refuses a nested view whose own `mHasDrawInstructions` is
   * set; the notification wrapper's isn't, while the children it plays keep theirs.
   */
  @Test
  fun aNotificationViewNestsWithoutItsOwnDrawInstructionsFlag() {
    val bytes = RemoteComposeWriter.obtain(100, 100, widgetsProfile).encodeToByteArray()
    // `remoteViews` is the same view; built here because Robolectric's API 36 needs Java 21.
    val document =
      android.widget.RemoteViews(
        android.widget.RemoteViews.DrawInstructions.Builder(listOf(platformWidgetDocument(bytes)))
          .build()
      )
    val views = RcLauncherWidgets.forNotification(document)

    fun field(of: Any, name: String): Any? =
      android.widget.RemoteViews::class.java.getDeclaredField(name).apply { isAccessible = true }.get(of)

    assertEquals(false, field(views, "mHasDrawInstructions"), "the wrapper")
    for (child in listOf("mLandscape", "mPortrait")) {
      val nested = assertNotNull(field(views, child), child)
      assertEquals(true, field(nested, "mHasDrawInstructions"), child)
    }
  }

  /**
   * The platform's widget player draws a document blank when a layout's spacing is a variable,
   * which is what a host-density capture's `spacedBy(10.dp)` is. [RcLauncherWidgets.forDisplay],
   * which `remoteViews` applies, writes it as the number it is on the display.
   */
  @Test
  fun aHostDensitySpacingIsALiteralForTheLauncher() {
    val context = RuntimeEnvironment.getApplication()
    val captured = runBlocking {
      captureSingleRemoteDocument(
          context = context,
          creationDisplayInfo = RemoteCreationDisplayInfo(200, 100, 160),
          remoteDensity = RemoteDensity.Host,
          profile = widgetsProfile,
        ) {
          RemoteColumn(verticalArrangement = RemoteArrangement.spacedBy(10.rdp)) {
            RemoteText("one".rs)
            RemoteText("two".rs)
          }
        }
        .bytes
    }
    fun decode(bytes: ByteArray) = RcDocumentCodec.decode(bytes, RcWireLimits())
    fun spacing(bytes: ByteArray) =
      decode(bytes)
        .operations
        .filterIsInstance<RcColumnLayout>()
        .map { it.spacedBy }
    assertTrue(spacing(captured).any { it.isNaNEncoded }, "the capture's spacing is a variable")

    val modern = RcTargetProfiles.WIDGETS_V7
    val played = RcLauncherWidgets.forDisplay(captured, density = 2.75f, target = modern)
    assertEquals(listOf(RcFloatWord.literal(27.5f)), spacing(played).filter { it.bits != 0 })
    assertTrue(spacing(played).none { it.isNaNEncoded })
    // Nothing left to do: the same bytes come back.
    assertTrue(RcLauncherWidgets.forDisplay(played, density = 2.75f, target = modern) === played)

    // Android 16 reads more only as literals: the rest of the document is adapted for it too.
    val baklava = RcTargetProfiles.WIDGETS_V6
    assertTrue(decode(captured).validateFor(baklava).isNotEmpty(), "the capture needs adapting")
    val adapted = RcLauncherWidgets.forDisplay(captured, density = 2.75f, target = baklava)
    assertEquals(emptyList(), decode(adapted).validateFor(baklava))
  }

  @Test
  fun gridBoundsAndSlots() {
    assertEquals(RcWidgetCaptureSize(144, 168), RcWidgetSizing.cells(2, 2))
    assertEquals(RcWidgetCaptureSize(72, 84), RcWidgetSizing.slotOf(android.os.Bundle()))
  }
}

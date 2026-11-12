@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rccreation.coexistence

import androidx.collection.intSetOf
import androidx.compose.remote.core.Operations
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.RemoteComposeWriterAndroid
import androidx.compose.remote.creation.platform.AndroidxRcPlatformServices
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.common.HTag
import ee.schimke.composeai.rccreation.common.Header as RcHeader
import ee.schimke.composeai.rccreation.common.Operations as RcOperations
import ee.schimke.composeai.rccreation.common.RcPlatformProfiles as RcRcPlatformProfiles
import ee.schimke.composeai.rccreation.common.RcProfiles as RcRcProfiles
import ee.schimke.composeai.rccreation.compose.capture.Profile as RcProfile
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcHeader as RcProtocolHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * rc-players' capture `Profile` against AndroidX's: the same content captured under the same
 * profile by both libraries has the same header and the same operations, takes the same fallbacks,
 * is refused the same operations, and plays in AndroidX's View player.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w600dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidxProfileParityTest {
  private val context = RuntimeEnvironment.getApplication()

  /** How many pixels a frame draws. */
  private fun drawn(bytes: ByteArray): Int {
    val image = Played(context, bytes).frame()
    var count = 0
    for (y in 0 until Height) for (x in 0 until Width) if (image.getPixel(x, y) != 0) count++
    return count
  }

  /**
   * The header, byte for byte, and the operations, field for field, but for two things in which
   * this library follows androidx-main and the AndroidX release under test (1.0.0-alpha23) does
   * not yet:
   * - a text that names no alignment is `Start` (5), where alpha23 writes `Left` (1);
   * - a paint under a profile without `CoreText` names its weight as a `wght` axis
   *   (`PaintTracker`), which alpha23's does not.
   */
  private fun assertSameDocument(theirs: ByteArray, ours: ByteArray) {
    assertEquals(headerBytes(theirs), headerBytes(ours), "the header")
    fun text(bytes: ByteArray) =
      wireText(bytes, without = setOf("RcPaintData")) { it is RcTextData && it.text == "wght" }
        .replace(", IntValue(id=9, value=5)", "")
        .replace("textAlignAndFlags=5", "textAlignAndFlags=1")
    assertEquals(text(theirs), text(ours))
  }

  @Test
  fun theDefaultProfileIsAndroidx() {
    val theirs = androidxDocument(context, RcPlatformProfiles.ANDROIDX) { AndroidxContent.Labelled() }
    val ours = rcDocument { RcContent.Labelled() }
    assertSameDocument(theirs, ours)
    val header = RcDocumentCodec.decode(ours).header
    assertEquals(
      RcHeaderValue.IntValue(RcProfiles.PROFILE_ANDROIDX),
      header.properties.single { it.key == RcProtocolHeader.DOC_PROFILES }.value,
    )
    assertEquals(2, wireOperations(ours).count { it is RcCoreText })
    assertEquals(drawn(theirs), drawn(ours))
    assertTrue(drawn(ours) > Width * 20, "the texts and the canvas are drawn")
  }

  /**
   * A launcher's profile, as `rc-widget-launcher` builds it on AndroidX's API: API level 7, the
   * widgets and experimental operations less `CoreText`, its style and the flow layout, and a header
   * that turns paint measuring off. Text is a `TextLayout`, and a canvas's drawing is in a
   * `CanvasContent`, as at API level 7.
   */
  @Test
  fun aLauncherProfileTakesTheFallbacksAndWritesTheHeaderAndroidxDoes() {
    val flags = RcProfiles.PROFILE_WIDGETS or RcProfiles.PROFILE_EXPERIMENTAL
    val left = setOf(Operations.CORE_TEXT, Operations.TEXT_STYLE, Operations.LAYOUT_FLOW)
    val operations = requireNotNull(Operations.getOperations(7, flags)).keySet().toSet() - left
    val theirProfile =
      Profile(7, flags, AndroidxRcPlatformServices(), { operations }) { display, profile, _ ->
        RemoteComposeWriterAndroid(
          profile,
          RemoteComposeWriter.hTag(Header.DOC_WIDTH, display.width),
          RemoteComposeWriter.hTag(Header.DOC_HEIGHT, display.height),
          RemoteComposeWriter.hTag(Header.DOC_CONTENT_DESCRIPTION, ""),
          RemoteComposeWriter.hTag(Header.DOC_PROFILES, profile.operationsProfiles),
          RemoteComposeWriter.hTag(Header.FEATURE_PAINT_MEASURE, 0),
        )
      }
    // The same profile on rc-players' API, from its own tables.
    val ourOperations =
      requireNotNull(
        RcOperations.getOperations(
          7,
          RcRcProfiles.PROFILE_WIDGETS or RcRcProfiles.PROFILE_EXPERIMENTAL,
        )
      ) - setOf(RcOperations.CORE_TEXT, RcOperations.TEXT_STYLE, RcOperations.LAYOUT_FLOW)
    assertEquals(operations, ourOperations, "the operation tables")
    val ourProfile =
      RcProfile(
        docApiLevel = 7,
        profileFlags = RcRcProfiles.PROFILE_WIDGETS or RcRcProfiles.PROFILE_EXPERIMENTAL,
        supportedOperations = intSetOf(*ourOperations.toIntArray()),
        headerTags = listOf(HTag(RcHeader.FEATURE_PAINT_MEASURE, 0)),
      )

    val theirs = androidxDocument(context, theirProfile) { AndroidxContent.Labelled() }
    val ours = rcDocument(ourProfile) { RcContent.Labelled() }
    assertSameDocument(theirs, ours)

    val written = wireOperations(ours)
    assertEquals(2, written.count { it is RcTextLayout })
    assertEquals(0, written.count { it is RcCoreText })
    assertEquals(1, written.count { it is RcCanvasContent })
    val header = RcDocumentCodec.decode(ours).header
    assertEquals(
      RcHeaderValue.IntValue(0),
      header.properties.single { it.key == RcProtocolHeader.FEATURE_PAINT_MEASURE }.value,
    )
    assertEquals(
      RcHeaderValue.IntValue(flags),
      header.properties.single { it.key == RcProtocolHeader.DOC_PROFILES }.value,
    )
    assertEquals(drawn(theirs), drawn(ours))
    assertTrue(drawn(ours) > Width * 20, "the texts and the canvas are drawn")
  }

  /** Each named profile declares what AndroidX's of that name does. */
  @Test
  fun theNamedProfilesAreAndroidxs() {
    val pairs =
      listOf(
        RcPlatformProfiles.WIDGETS_V6 to RcRcPlatformProfiles.WIDGETS_V6,
        RcPlatformProfiles.WIDGETS_V7 to RcRcPlatformProfiles.WIDGETS_V7,
        RcPlatformProfiles.ANDROIDX to RcRcPlatformProfiles.ANDROIDX,
      )
    for ((theirs, ours) in pairs) {
      assertEquals(theirs.apiLevel, ours.apiLevel)
      assertEquals(theirs.operationsProfiles, ours.operationsProfiles)
      assertEquals(theirs.supportedOperations.toSet(), ours.supportedOperations, "$ours")
    }
  }

  /** An operation outside the profile is refused, with AndroidX's words. */
  @Test
  fun anOperationOutsideTheProfileIsRefusedAsAndroidxRefusesIt() {
    fun message(capture: () -> Unit): String? {
      var cause: Throwable? = assertFailsWith<Throwable> { capture() }
      while (cause?.cause != null && cause !is RuntimeException) cause = cause.cause
      return generateSequence(cause) { it.cause }.last().message
    }
    val theirs = message {
      androidxDocument(context, RcPlatformProfiles.ANDROIDX) { AndroidxContent.Flowing() }
    }
    val ours = message { rcDocument { RcContent.Flowing() } }
    assertEquals("Operation 240 is not supported for this version", ours)
    assertEquals(theirs, ours)
  }
}

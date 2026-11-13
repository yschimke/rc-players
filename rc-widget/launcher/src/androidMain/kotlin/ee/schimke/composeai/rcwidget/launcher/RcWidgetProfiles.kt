@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.launcher

import androidx.compose.remote.core.Operations
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.RemoteComposeWriterAndroid
import androidx.compose.remote.creation.platform.AndroidxRcPlatformServices
import androidx.compose.remote.creation.profile.Profile
import ee.schimke.composeai.rcplayer.edit.RcTargetProfile
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles

/**
 * Profile used when capturing the `.rc` document that ships to the launcher as a pinned widget (or
 * to the notification system as a `CustomBigContentView`). Both surfaces hand the bytes to
 * `RemoteViews.DrawInstructions`, which is interpreted by the platform's RemoteCompose runtime —
 * that runtime supports a stricter op set than the embedded AndroidX player, so capturing with the
 * widgets profile up-front keeps the bytes within the launcher's vocabulary.
 *
 * Shape mirrors `RcPlatformProfiles.WIDGETS_V7` (document API level 7,
 * `RcProfiles.PROFILE_WIDGETS`) but ORs in `PROFILE_EXPERIMENTAL` so experimental widget ops are
 * admitted at capture time.
 *
 * **`FEATURE_PAINT_MEASURE = 0` (no paint-measure for widgets).** The alpha default is `1`
 * ("measure during paint instead of wrap behaviour"), which makes
 * [androidx.compose.remote.player.view.platform.RemoteComposeView.onMeasure] skip the document's
 * measure pass and only re-measures content lazily during `dispatchDraw`. A widget's slot size is
 * *externally forced* by the launcher (the cell grid), and the host re-uses one player view across
 * resizes — the embedded long-press preview's `RcAdaptiveRemoteDocumentPlayer`, and the
 * launcher's own `RemoteViews.DrawInstructions` view. With paint-measure on, that re-used view
 * paints blank the moment it's re-measured at a new size (its layout was cached at the first draw
 * and never rebuilt), which is the "widget goes blank once resized" bug. With the feature off the
 * player runs a real measure pass against the (EXACTLY) slot on every size change, so `fillMaxSize`
 * re-fills and content reflows to the new cell — exactly what we want when the size is dictated
 * from outside.
 *
 * Baking the bit requires the writer's `HTag` varargs constructor (the flag lands in the same
 * single `HEADER` op that carries DOC_WIDTH / DOC_HEIGHT / DOC_PROFILES), which means dropping the
 * `writerCallback` — same tradeoff `androidXExperimentalWrap` already takes: it only tracks
 * PendingIntent-style side effects baked by AndroidX's `pendingIntentAction`. Launcher widget taps
 * instead use numeric host actions: the app collects their ids during capture, then attaches the
 * corresponding PendingIntents to `RemoteViews` after the bytes are encoded.
 */
private const val WIDGETS_API_LEVEL = 7
private const val WIDGETS_OPERATIONS = RcProfiles.PROFILE_WIDGETS or RcProfiles.PROFILE_EXPERIMENTAL

private val widgetOperations =
  requireNotNull(Operations.getOperations(WIDGETS_API_LEVEL, WIDGETS_OPERATIONS)).keySet().toSet() -
    setOf(Operations.CORE_TEXT, Operations.TEXT_STYLE, Operations.LAYOUT_FLOW)

// AndroidX alpha21 admits CoreText (239) as an experimental V7 widget operation, but the
// Android 16 platform decoder does not implement it. Excluding it makes the writer use
// its supported TextLayout fallback (208), retaining text/color bindings. Keep this
// compatibility path on newer hosts too; system color support is selected independently.
// FlowLayout (240) is also experimental in AndroidX but absent from Android 16's decoder.
// A capture checks `Operations.LAYOUT_FLOW in profile.supportedOperations` to use a row fallback.

/**
 * A capture profile for a launcher reading [target]: AndroidX's widget operations, less those above,
 * that [target]'s own table decodes. The writer then never emits an operation the launcher would
 * fail the document on — Android 16's level 6 table has no ColorTheme, and none of the level 7
 * additions — and takes its fallback instead.
 */
private fun createWidgetsProfile(target: RcTargetProfile): Profile {
  val operations = widgetOperations intersect target.supportedOpcodes
  return Profile(WIDGETS_API_LEVEL, WIDGETS_OPERATIONS, AndroidxRcPlatformServices(), { operations }) {
    creationDisplayInfo,
    profile,
    _ ->
    RemoteComposeWriterAndroid(
      profile,
      RemoteComposeWriter.hTag(Header.DOC_WIDTH, creationDisplayInfo.width),
      RemoteComposeWriter.hTag(Header.DOC_HEIGHT, creationDisplayInfo.height),
      RemoteComposeWriter.hTag(Header.DOC_CONTENT_DESCRIPTION, ""),
      RemoteComposeWriter.hTag(Header.DOC_PROFILES, profile.operationsProfiles),
      RemoteComposeWriter.hTag(Header.FEATURE_PAINT_MEASURE, 0),
    )
  }
}

/**
 * Android 16 widget host profile; excludes ColorTheme (wire op 196). Its documents ship through
 * [platformWidgetDocument] for [RcTargetProfiles.WIDGETS_V6].
 */
public val widgetsProfile: Profile = createWidgetsProfile(RcTargetProfiles.WIDGETS_V6)

/**
 * API 37+ widget host profile, including host-resolved ColorTheme values. Its documents ship through
 * [platformWidgetDocument] for [RcTargetProfiles.WIDGETS_V7], which keeps the level 7 header the
 * ColorTheme needs.
 */
public val systemThemedWidgetsProfile: Profile = createWidgetsProfile(RcTargetProfiles.WIDGETS_V7)

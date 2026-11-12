@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.creation

import androidx.collection.MutableIntSet
import ee.schimke.composeai.rccreation.common.HTag
import ee.schimke.composeai.rccreation.common.Header
import ee.schimke.composeai.rccreation.common.Operations
import ee.schimke.composeai.rccreation.common.Profile
import ee.schimke.composeai.rccreation.common.RcProfiles
import ee.schimke.composeai.rccreation.compose.capture.Profile
import ee.schimke.composeai.rcplayer.edit.RcTargetProfile
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcwidget.RcWidgetTarget
import ee.schimke.composeai.rcwidget.RcWidgetTargets

/**
 * The capture profile for a player `rc-player-edit` describes: a document captured under it holds
 * only what [this] decodes, so it needs no rewriting for that player afterwards (`adaptTo`), and
 * what the writer has a fallback for it takes while capturing (text as a `TextLayout` where the
 * player has no `CoreText`).
 *
 * @param docApiLevel the document API level to write; the player's own unless it reads a newer
 *   level's header (an Android 16 launcher is given level 7's, and `rc-widget-launcher`'s
 *   `platformWidgetDocument` turns it into the one it reads).
 * @param profileFlags the `RcProfiles` bits the header declares.
 * @param without operations the player's table lists that a capture must not use all the same.
 * @param headerTags header properties after the standard ones; see [Profile].
 */
public fun RcTargetProfile.toCaptureProfile(
  docApiLevel: Int = apiLevel,
  profileFlags: Int = profiles,
  without: Set<Int> = emptySet(),
  headerTags: List<HTag> = emptyList(),
): Profile {
  val operations = MutableIntSet()
  (supportedOpcodes - without).forEach { operations.add(it) }
  return Profile(docApiLevel, profileFlags, operations, headerTags)
}

/**
 * Capture profiles for the surfaces a Remote Compose widget is served to ([RcWidgetTargets]), on
 * rc-players' creation API. Each is what the matching AndroidX `Profile` is on AndroidX's:
 * `rc-widget-creation-androidx`'s `androidXExperimental` and `androidXExperimentalWrap`,
 * `rc-widget-launcher`'s `widgetsProfile` and `systemThemedWidgetsProfile`.
 */
public object RcCaptureProfiles {
  // Ahead of the profiles that read it: an object's properties are set in order.
  private val notOnALauncher =
    setOf(Operations.CORE_TEXT, Operations.TEXT_STYLE, Operations.LAYOUT_FLOW)

  /**
   * An app's own player (the CMP player, or AndroidX's embedded one): AndroidX's profile at the
   * current document API level with the experimental operations, among them the flow layout.
   */
  public val androidXExperimental: Profile =
    Profile(profileFlags = RcProfiles.PROFILE_ANDROIDX or RcProfiles.PROFILE_EXPERIMENTAL)

  /**
   * [androidXExperimental] with `Header.FEATURE_PAINT_MEASURE = 0`, so that a player sized by its
   * parent measures the document's content, not its declared size.
   */
  public val androidXExperimentalWrap: Profile =
    Profile(
      profileFlags = RcProfiles.PROFILE_ANDROIDX or RcProfiles.PROFILE_EXPERIMENTAL,
      headerTags = listOf(HTag(Header.FEATURE_PAINT_MEASURE, 0)),
    )

  /**
   * A launcher widget every launcher reads, Android 16's too: API level 7's widget and experimental
   * operations that Android 16's level 6 player also decodes, without `CoreText`, `TextStyle` and
   * the flow layout (AndroidX admits them to its experimental widget set; the platform's decoder
   * has none), so no `ColorTheme` either. Text is written as `TextLayout`. The header turns paint
   * measuring off, which a launcher's resized view depends on.
   */
  public val launcherWidgets: Profile = launcher(RcTargetProfiles.WIDGETS_V6)

  /**
   * A launcher widget for API 37 launchers only: [launcherWidgets] with `ColorTheme`, which keeps
   * the device's system colours.
   */
  public val systemThemedLauncherWidgets: Profile = launcher(RcTargetProfiles.WIDGETS_V7)

  /**
   * A Wear OS widget through Glance Wear: its allow-list at the current document API level, under
   * `RcProfiles.PROFILE_WEAR_WIDGETS`.
   */
  public val wearWidgets: Profile = RcTargetProfiles.WEAR_WIDGETS.toCaptureProfile()

  /** The capture profile for [target]. */
  public fun forTarget(target: RcWidgetTarget): Profile =
    when (target.id) {
      RcWidgetTargets.App.id -> androidXExperimental
      RcWidgetTargets.Launcher.id -> systemThemedLauncherWidgets
      RcWidgetTargets.LauncherAndroid16.id -> launcherWidgets
      RcWidgetTargets.WearWidget.id -> wearWidgets
      else -> target.profile.toCaptureProfile()
    }

  private const val WIDGETS_API_LEVEL = 7
  private const val WIDGETS_OPERATIONS =
    RcProfiles.PROFILE_WIDGETS or RcProfiles.PROFILE_EXPERIMENTAL

  /** AndroidX's widget operations, less those above, that [target]'s own table decodes. */
  private fun launcher(target: RcTargetProfile): Profile {
    val widgetOperations =
      checkNotNull(Operations.getOperations(WIDGETS_API_LEVEL, WIDGETS_OPERATIONS)) - notOnALauncher
    val operations = MutableIntSet()
    (widgetOperations intersect target.supportedOpcodes).forEach { operations.add(it) }
    return Profile(
      WIDGETS_API_LEVEL,
      WIDGETS_OPERATIONS,
      operations,
      listOf(HTag(Header.FEATURE_PAINT_MEASURE, 0)),
    )
  }
}

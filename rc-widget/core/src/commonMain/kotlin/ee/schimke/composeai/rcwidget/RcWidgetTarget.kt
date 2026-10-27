package ee.schimke.composeai.rcwidget

import ee.schimke.composeai.rcplayer.edit.RcApproximation
import ee.schimke.composeai.rcplayer.edit.RcProfiles
import ee.schimke.composeai.rcplayer.edit.RcTargetProfile
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles

/**
 * A surface a document is served to: the player that draws it ([profile]), what [RcWidgetDocuments]
 * may approximate to make a document play there ([approximations]), and whether the host can write
 * named values into the document at playback ([bindsValues]).
 *
 * A host that binds values (an app's own player) gets a template as it is and pushes its values
 * live. One that cannot (a launcher widget's `RemoteViews` player, a Wear widget) gets the template
 * baked: every value written in as a constant, and every image embedded.
 */
public class RcWidgetTarget(
  /** A short stable id, used in cache keys and in [RcPlayerCapabilities.headerValue]. */
  public val id: String,
  public val profile: RcTargetProfile,
  public val approximations: Set<RcApproximation> = emptySet(),
  public val bindsValues: Boolean = false,
) {
  override fun toString(): String = "RcWidgetTarget($id, ${profile.name})"
}

/** The surfaces a Remote Compose widget is commonly served to. */
public object RcWidgetTargets {
  /**
   * An app's own player — the AndroidX embedded player or the CMP player — which reads the
   * experimental operations and takes named values live.
   */
  public val App: RcWidgetTarget =
    RcWidgetTarget("app", RcTargetProfiles.ANDROIDX_EXPERIMENTAL, bindsValues = true)

  /**
   * A launcher widget on a host reading document API level 7 (Android 17+), which has `CoreText`
   * and `ColorTheme`: the document keeps the device's system colours.
   */
  public val Launcher: RcWidgetTarget =
    RcWidgetTarget("launcher", RcTargetProfiles.WIDGETS_V7, setOf(RcApproximation.TEXT))

  /**
   * A launcher widget on Android 16 (Baklava), the platform player's original level-6 set. Styled
   * text becomes `TextLayout`, and theme colours their recorded fallbacks where the theme sections
   * cannot carry them.
   */
  public val LauncherAndroid16: RcWidgetTarget =
    RcWidgetTarget(
      "launcher-v6",
      RcTargetProfiles.WIDGETS_V6,
      setOf(RcApproximation.TEXT, RcApproximation.THEME_COLORS),
    )

  /**
   * A Wear OS widget through Glance Wear: its fixed allow-list, no `CoreText` and no system
   * palette.
   */
  public val WearWidget: RcWidgetTarget =
    RcWidgetTarget(
      "wear-widget",
      RcTargetProfiles.WEAR_WIDGETS,
      setOf(RcApproximation.TEXT, RcApproximation.THEME_COLORS),
    )

  public val All: List<RcWidgetTarget> = listOf(App, Launcher, LauncherAndroid16, WearWidget)

  /** The target named [id], or null. */
  public fun byId(id: String): RcWidgetTarget? = All.firstOrNull { it.id == id }
}

/**
 * The player a client has, as it reports it: the highest document API level it reads and the
 * [RcProfiles] bits it plays. A server picks the document to send from this ([negotiate]) rather
 * than from the client's app version, so an old app on a new platform, and a new app on an old one,
 * each get what their player can draw.
 *
 * It travels as one header-style value ([headerValue], [parse]): `api=7; profiles=0x100;
 * density=2.625; font-scale=1.0`.
 */
public data class RcPlayerCapabilities(
  val apiLevel: Int,
  val profiles: Int,
  /** Device pixels per dp, when the document must be baked for one display. */
  val density: Float? = null,
  /** The user's text scale. */
  val fontScale: Float? = null,
) {
  /** Whether this player draws everything a document written for [target] may use. */
  public fun supports(target: RcTargetProfile): Boolean =
    apiLevel >= target.apiLevel && (profiles and target.profiles) == target.profiles

  /** The best of [candidates] this player draws: the highest API level, then the first listed. */
  public fun negotiate(candidates: List<RcWidgetTarget> = RcWidgetTargets.All): RcWidgetTarget? =
    candidates
      .filter { supports(it.profile) }
      .withIndex()
      .maxWithOrNull(compareBy({ it.value.profile.apiLevel }, { -it.index }))
      ?.value

  /** This as a header value, for an HTTP request header or a query parameter. */
  public fun headerValue(): String = buildString {
    append("api=").append(apiLevel)
    append("; profiles=0x").append(profiles.toString(16))
    density?.let { append("; density=").append(it) }
    fontScale?.let { append("; font-scale=").append(it) }
  }

  public companion object {
    /** The conventional request header that carries [headerValue]. */
    public const val HEADER: String = "RC-Player"

    /** What a player built for [target] reports. */
    public fun of(
      target: RcTargetProfile,
      density: Float? = null,
      fontScale: Float? = null,
    ): RcPlayerCapabilities =
      RcPlayerCapabilities(target.apiLevel, target.profiles, density, fontScale)

    /**
     * [value] read back from [headerValue], or null when it names no API level. Unknown fields are
     * ignored, so a newer client can add one; a missing `profiles` reads as the baseline.
     */
    public fun parse(value: String): RcPlayerCapabilities? {
      val fields =
        value
          .split(';')
          .mapNotNull { field ->
            val (name, raw) =
              field.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            name.trim().lowercase() to raw.trim()
          }
          .toMap()
      val api = fields["api"]?.toIntOrNull() ?: return null
      val profiles =
        fields["profiles"]?.let { raw ->
          if (raw.startsWith("0x", ignoreCase = true)) raw.drop(2).toIntOrNull(16)
          else raw.toIntOrNull()
        } ?: RcProfiles.BASELINE
      return RcPlayerCapabilities(
        api,
        profiles,
        fields["density"]?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f },
        fields["font-scale"]?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f },
      )
    }
  }
}

package ee.schimke.composeai.rcwidget.launcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.remote.creation.profile.Profile
import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.edit.RcProfileViolation
import ee.schimke.composeai.rcplayer.edit.RcTargetProfile
import ee.schimke.composeai.rcplayer.edit.adaptTo
import ee.schimke.composeai.rcplayer.edit.hardcodeLayoutSpacing
import ee.schimke.composeai.rcplayer.edit.validateFor
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWidgetTarget
import ee.schimke.composeai.rcwidget.RcWidgetTargets
import ee.schimke.composeai.rcwidget.RcWidgetActionMetadata

/**
 * Publishing a Remote Compose document as a launcher widget: `RemoteViews.DrawInstructions` around
 * the document, and one `PendingIntent` per numeric host action it taps.
 */
public object RcLauncherWidgets {
  /** The first API level whose launcher reads `RemoteViews.DrawInstructions` (Android 16). */
  public const val MIN_HOST_API: Int = 36

  /** The first API level whose launcher resolves `ColorTheme` system colours. */
  public const val SYSTEM_THEME_MIN_HOST_API: Int = 37

  /**
   * The fill-in extra the platform's Remote Compose player adds to a tap's `PendingIntent`: the
   * action's metadata, as the player resolved it when tapped ([RcWidgetActionMetadata.decode]).
   */
  public const val EXTRA_REMOTE_COMPOSE_METADATA: String = "remotecompose_metadata"

  /** Whether this device's launcher can show a Remote Compose widget. */
  public val isSupported: Boolean
    get() = Build.VERSION.SDK_INT >= MIN_HOST_API

  /** The capture profile for this device's launcher: system colours where it resolves them. */
  public fun profileFor(sdkInt: Int = Build.VERSION.SDK_INT): Profile =
    if (sdkInt >= SYSTEM_THEME_MIN_HOST_API) systemThemedWidgetsProfile else widgetsProfile

  /**
   * The player this device's launcher is, for a document captured with [profileFor] the same
   * [sdkInt]: `RcTargetProfiles.WIDGETS_V7` where it resolves system colours, else Android 16's
   * `RcTargetProfiles.WIDGETS_V6` — [widgetTargetFor]'s profile.
   */
  public fun targetFor(sdkInt: Int = Build.VERSION.SDK_INT): RcTargetProfile =
    widgetTargetFor(sdkInt).profile

  /**
   * The [RcWidgetTarget] serving this device's launcher, for `RcWidgetDocuments`:
   * [RcWidgetTargets.Launcher] where it resolves system colours, else
   * [RcWidgetTargets.LauncherAndroid16].
   */
  public fun widgetTargetFor(sdkInt: Int = Build.VERSION.SDK_INT): RcWidgetTarget =
    if (sdkInt >= SYSTEM_THEME_MIN_HOST_API) RcWidgetTargets.Launcher
    else RcWidgetTargets.LauncherAndroid16

  /**
   * [document] as a widget's `RemoteViews` on this device's launcher ([targetFor]), each of its
   * numeric host actions sending its entry in [actions].
   */
  @RequiresApi(MIN_HOST_API)
  public fun remoteViews(document: ByteArray, actions: Map<Int, PendingIntent> = emptyMap()): RemoteViews =
    remoteViews(document, actions, targetFor())

  /**
   * [document] as a widget's `RemoteViews` for a launcher reading [target] ([platformWidgetDocument]),
   * each of its numeric host actions sending its entry in [actions].
   */
  @RequiresApi(MIN_HOST_API)
  public fun remoteViews(
    document: ByteArray,
    actions: Map<Int, PendingIntent>,
    target: RcTargetProfile,
  ): RemoteViews {
    val resources = Resources.getSystem()
    val played =
      forDisplay(
        document,
        resources.displayMetrics.density,
        resources.configuration.fontScale,
        target,
      )
    val views =
      RemoteViews(
        RemoteViews.DrawInstructions.Builder(listOf(platformWidgetDocument(played, target)))
          .build()
      )
    for ((actionId, intent) in actions) views.setOnClickPendingIntent(actionId, intent)
    return views
  }

  /**
   * [document] recovered for the platform's widget player [target] on a display of [density] and
   * [fontScale]: what that player would draw as nothing, rewritten into what it draws. A document
   * written for a newer or freer player — a `RemoteDensity.Host` capture, most often — is the case.
   *
   * - Each layout's spacing is made a literal ([hardcodeLayoutSpacing]). The player, Android 16's
   *   and API 37's alike, lays a row or column whose `spacedBy` is a variable out as nothing, so a
   *   host-density capture — every `Arrangement.spacedBy(n.dp)` an expression over the density —
   *   draws blank on a launcher and in a notification, with no error.
   * - What [target] still can't read ([validateFor]) is then adapted for it ([adaptTo]): on Android
   *   16 the density baked in and the paddings, text sizes and colours folded to the literals its
   *   level 6 player needs. The adaptation is kept only where it leaves fewer violations.
   *
   * [remoteViews] and [notificationRemoteViews] apply this for the device's own display, so every
   * host that publishes through them is covered; call it directly only to hand the bytes to
   * `RemoteViews.DrawInstructions` yourself. A document [target] already plays, or that this can't
   * read, comes back as it was given.
   */
  public fun forDisplay(
    document: ByteArray,
    density: Float,
    fontScale: Float = 1f,
    target: RcTargetProfile = targetFor(),
  ): ByteArray {
    // Read as [target] reads it, so content a writer guarded for another player stays out.
    val decoded =
      runCatching { target.decode(document, RcWireLimits()) }.getOrNull() ?: return document
    val spaced = decoded.hardcodeLayoutSpacing(density, fontScale)
    val violations = spaced.validateFor(target)
    val recovered =
      if (violations.isEmpty()) spaced
      else {
        val widget =
          if (target.apiLevel <= 6) RcWidgetTargets.LauncherAndroid16 else RcWidgetTargets.Launcher
        val adapted = spaced.adaptTo(target, widget.approximations, RcDisplay(density, fontScale))
        if (adapted.violations.size < violations.size) adapted.document else spaced
      }
    return if (recovered === decoded) document else RcDocumentCodec.encode(recovered)
  }

  /**
   * [document] as a notification's custom view (`setCustomBigContentView`): [remoteViews] wrapped
   * by [forNotification], so Android 17 shows it.
   */
  @RequiresApi(MIN_HOST_API)
  public fun notificationRemoteViews(
    document: ByteArray,
    actions: Map<Int, PendingIntent> = emptyMap(),
    target: RcTargetProfile = targetFor(),
  ): RemoteViews = forNotification(remoteViews(document, actions, target))

  /**
   * [views], a `RemoteViews.DrawInstructions` document from [remoteViews], as a landscape/portrait
   * pair of itself, which SystemUI can nest in a notification's template.
   *
   * Since API 31 SystemUI puts every custom view of an app targeting it inside the system
   * template, whatever `Notification.Style` is set: `Notification.Builder`'s
   * `minimallyDecoratedExpandedContentView` → `buildCustomContentIntoTemplate` →
   * `RemoteViews.addView`. From Android 17 (API 37), `addView` runs `validateNestedViews`, which
   * refuses a nested view that has DrawInstructions:
   * ```
   * E/NotifContentInflater: couldn't inflate view for notification …
   * java.lang.IllegalArgumentException: Cannot add a nested view that has DrawInstructions.
   * ```
   * and SystemUI cancels the notification with `REASON_ERROR` within half a second. Bare
   * DrawInstructions views are fine as a launcher widget's root; only nesting is refused.
   *
   * The `RemoteViews(landscape, portrait)` wrapper doesn't take `mHasDrawInstructions` from its
   * children, so it passes that check, and applying it plays the child for the current
   * orientation, DrawInstructions and click intents included. On a Pixel 10a on Android 17 the
   * wrapped document survives, draws and its taps fire. This leans on the check reading only the
   * nested view's own flag: if a later build checks the wrapper's children too, the notification
   * drops again. A nested `RemoteViews` is applied without a size, so a sized
   * `RemoteViews(Map<SizeF, RemoteViews>)` wrapper would always play its smallest child: use this
   * one.
   */
  public fun forNotification(views: RemoteViews): RemoteViews = RemoteViews(views, views.clone())

  /**
   * What would keep [document] from playing on a launcher reading [target], as that launcher reads
   * it once [platformWidgetDocument] has prepared it: its header's level and profile bits select the
   * operation table, as the platform player's do. Empty means it plays. For tests and debug builds:
   * the check decodes the whole document.
   */
  public fun violations(
    document: ByteArray,
    target: RcTargetProfile = targetFor(),
  ): List<RcProfileViolation> =
    target.decode(platformWidgetDocument(document, target)).validateFor(target)

  /**
   * A broadcast `PendingIntent` for one tap of widget [widgetId]: [action] on [receiver], carrying
   * the widget, the action id and its static [payload] as extras ([EXTRA_WIDGET_ID],
   * [EXTRA_ACTION_ID], [EXTRA_ACTION_PAYLOAD]).
   *
   * Extras don't take part in a `PendingIntent`'s identity, so its data is a unique
   * `<scheme>://widget-action/<widget>/<action>` URI: one tap's payload never replaces a sibling's.
   * It is mutable because the player supplies the tap's metadata as a fill-in extra, which Android
   * drops for an immutable one; keep [receiver] explicit and unexported.
   */
  public fun actionBroadcast(
    context: Context,
    receiver: Class<*>,
    widgetId: Int,
    actionId: Int,
    payload: String,
    action: String = ACTION_WIDGET_ACTION,
    scheme: String = context.packageName,
  ): PendingIntent {
    val intent =
      Intent(context, receiver).apply {
        this.action = action
        data = Uri.parse("$scheme://widget-action/$widgetId/$actionId")
        putExtra(EXTRA_WIDGET_ID, widgetId)
        putExtra(EXTRA_ACTION_ID, actionId)
        putExtra(EXTRA_ACTION_PAYLOAD, payload)
      }
    return PendingIntent.getBroadcast(
      context,
      actionId,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
  }

  /**
   * An activity `PendingIntent` for a tap that needs an activity in front (open the app, a URL, a
   * confirmation): a broadcast receiver can't start one from the background. [intent]'s identity
   * is made unique per widget and action.
   */
  public fun actionActivity(
    context: Context,
    intent: Intent,
    widgetId: Int,
    actionId: Int,
    scheme: String = context.packageName,
  ): PendingIntent {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.identifier = "$scheme://widget-open/$widgetId/$actionId"
    return PendingIntent.getActivity(
      context,
      actionId,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  /** A tap delivered to a receiver of [actionBroadcast]. */
  public data class Tap(
    val widgetId: Int,
    val actionId: Int,
    /** The payload the capture registered, as it was then. */
    val staticPayload: String?,
    /** The metadata the player delivered, when this platform forwards it. */
    val metadata: RcWidgetActionMetadata?,
  )

  /** The tap [intent] carries, or null when it isn't one of [actionBroadcast]'s. */
  public fun tapOf(intent: Intent): Tap? {
    if (!intent.hasExtra(EXTRA_ACTION_ID)) return null
    return Tap(
      widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1),
      actionId = intent.getIntExtra(EXTRA_ACTION_ID, 0),
      staticPayload = intent.getStringExtra(EXTRA_ACTION_PAYLOAD),
      metadata = RcWidgetActionMetadata.decode(intent.getStringExtra(EXTRA_REMOTE_COMPOSE_METADATA)),
    )
  }

  public const val ACTION_WIDGET_ACTION: String = "ee.schimke.composeai.rcwidget.WIDGET_ACTION"
  public const val EXTRA_WIDGET_ID: String = "ee.schimke.composeai.rcwidget.WIDGET_ID"
  public const val EXTRA_ACTION_ID: String = "ee.schimke.composeai.rcwidget.ACTION_ID"
  public const val EXTRA_ACTION_PAYLOAD: String = "ee.schimke.composeai.rcwidget.ACTION_PAYLOAD"
}

package ee.schimke.composeai.rcwidget.launcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.remote.creation.profile.Profile
import ee.schimke.composeai.rcplayer.edit.RcProfileViolation
import ee.schimke.composeai.rcplayer.edit.RcTargetProfile
import ee.schimke.composeai.rcwidget.RcWidgetActionMetadata
import ee.schimke.composeai.rcwidget.RcWidgetTarget

/**
 * Publishing a Remote Compose document as a launcher widget: `RemoteViews.DrawInstructions` around
 * the document, and one `PendingIntent` per numeric host action it taps.
 *
 * This is [RcPlatformWidgets] (`rc-widget-launcher-core`, which has no creation API in it) with the
 * AndroidX capture [profileFor] added, under the name and package this artifact has always had.
 * Every other member delegates, so a document is published the same whichever API authored it. New
 * code that doesn't need an AndroidX [Profile] can use [RcPlatformWidgets] directly.
 */
public object RcLauncherWidgets {
  /** [RcPlatformWidgets.MIN_HOST_API]. */
  public const val MIN_HOST_API: Int = RcPlatformWidgets.MIN_HOST_API

  /** [RcPlatformWidgets.SYSTEM_THEME_MIN_HOST_API]. */
  public const val SYSTEM_THEME_MIN_HOST_API: Int = RcPlatformWidgets.SYSTEM_THEME_MIN_HOST_API

  /** [RcPlatformWidgets.EXTRA_REMOTE_COMPOSE_METADATA]. */
  public const val EXTRA_REMOTE_COMPOSE_METADATA: String =
    RcPlatformWidgets.EXTRA_REMOTE_COMPOSE_METADATA

  /** Whether this device's launcher can show a Remote Compose widget. */
  public val isSupported: Boolean
    get() = RcPlatformWidgets.isSupported

  /** The capture profile for this device's launcher: system colours where it resolves them. */
  public fun profileFor(sdkInt: Int = Build.VERSION.SDK_INT): Profile =
    if (sdkInt >= SYSTEM_THEME_MIN_HOST_API) systemThemedWidgetsProfile else widgetsProfile

  /** [RcPlatformWidgets.targetFor]: the player for a document captured with [profileFor]. */
  public fun targetFor(sdkInt: Int = Build.VERSION.SDK_INT): RcTargetProfile =
    RcPlatformWidgets.targetFor(sdkInt)

  /** [RcPlatformWidgets.widgetTargetFor]. */
  public fun widgetTargetFor(sdkInt: Int = Build.VERSION.SDK_INT): RcWidgetTarget =
    RcPlatformWidgets.widgetTargetFor(sdkInt)

  /** [RcPlatformWidgets.remoteViews]. */
  @RequiresApi(MIN_HOST_API)
  public fun remoteViews(document: ByteArray, actions: Map<Int, PendingIntent> = emptyMap()): RemoteViews =
    RcPlatformWidgets.remoteViews(document, actions)

  /** [RcPlatformWidgets.remoteViews]. */
  @RequiresApi(MIN_HOST_API)
  public fun remoteViews(
    document: ByteArray,
    actions: Map<Int, PendingIntent>,
    target: RcTargetProfile,
  ): RemoteViews = RcPlatformWidgets.remoteViews(document, actions, target)

  /** [RcPlatformWidgets.forDisplay]. */
  public fun forDisplay(
    document: ByteArray,
    density: Float,
    fontScale: Float = 1f,
    target: RcTargetProfile = targetFor(),
  ): ByteArray = RcPlatformWidgets.forDisplay(document, density, fontScale, target)

  /** [RcPlatformWidgets.notificationRemoteViews]. */
  @RequiresApi(MIN_HOST_API)
  public fun notificationRemoteViews(
    document: ByteArray,
    actions: Map<Int, PendingIntent> = emptyMap(),
    target: RcTargetProfile = targetFor(),
  ): RemoteViews = RcPlatformWidgets.notificationRemoteViews(document, actions, target)

  /** [RcPlatformWidgets.forNotification]. */
  public fun forNotification(views: RemoteViews): RemoteViews =
    RcPlatformWidgets.forNotification(views)

  /** [RcPlatformWidgets.violations]. */
  public fun violations(
    document: ByteArray,
    target: RcTargetProfile = targetFor(),
  ): List<RcProfileViolation> = RcPlatformWidgets.violations(document, target)

  /** [RcPlatformWidgets.actionBroadcast]. */
  public fun actionBroadcast(
    context: Context,
    receiver: Class<*>,
    widgetId: Int,
    actionId: Int,
    payload: String,
    action: String = ACTION_WIDGET_ACTION,
    scheme: String = context.packageName,
  ): PendingIntent =
    RcPlatformWidgets.actionBroadcast(context, receiver, widgetId, actionId, payload, action, scheme)

  /** [RcPlatformWidgets.actionActivity]. */
  public fun actionActivity(
    context: Context,
    intent: Intent,
    widgetId: Int,
    actionId: Int,
    scheme: String = context.packageName,
  ): PendingIntent = RcPlatformWidgets.actionActivity(context, intent, widgetId, actionId, scheme)

  /** A tap delivered to a receiver of [actionBroadcast]; [RcPlatformWidgets.Tap]. */
  public data class Tap(
    val widgetId: Int,
    val actionId: Int,
    /** The payload the capture registered, as it was then. */
    val staticPayload: String?,
    /** The metadata the player delivered, when this platform forwards it. */
    val metadata: RcWidgetActionMetadata?,
  )

  /** The tap [intent] carries, or null when it isn't one of [actionBroadcast]'s. */
  public fun tapOf(intent: Intent): Tap? =
    RcPlatformWidgets.tapOf(intent)?.let { Tap(it.widgetId, it.actionId, it.staticPayload, it.metadata) }

  public const val ACTION_WIDGET_ACTION: String = RcPlatformWidgets.ACTION_WIDGET_ACTION
  public const val EXTRA_WIDGET_ID: String = RcPlatformWidgets.EXTRA_WIDGET_ID
  public const val EXTRA_ACTION_ID: String = RcPlatformWidgets.EXTRA_ACTION_ID
  public const val EXTRA_ACTION_PAYLOAD: String = RcPlatformWidgets.EXTRA_ACTION_PAYLOAD
}

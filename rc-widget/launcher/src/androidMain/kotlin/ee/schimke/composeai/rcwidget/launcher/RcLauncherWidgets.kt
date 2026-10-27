package ee.schimke.composeai.rcwidget.launcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.remote.creation.profile.Profile
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
   * [document] as a widget's `RemoteViews`, made playable on Android 16 first
   * ([platformWidgetDocument]), each of its numeric host actions sending its entry in [actions].
   */
  @RequiresApi(MIN_HOST_API)
  public fun remoteViews(document: ByteArray, actions: Map<Int, PendingIntent> = emptyMap()): RemoteViews {
    val views =
      RemoteViews(
        RemoteViews.DrawInstructions.Builder(listOf(platformWidgetDocument(document))).build()
      )
    for ((actionId, intent) in actions) views.setOnClickPendingIntent(actionId, intent)
    return views
  }

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

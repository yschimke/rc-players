package ee.schimke.composeai.rcwidget.wear

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.wear.GlanceWearWidget
import androidx.glance.wear.GlanceWearWidgetManager

/**
 * Per-widget configuration on the watch, and the widgets placed there — for an app whose widgets
 * each show something chosen for them, generated on the watch.
 *
 * Glance Wear's provider-configuration contract: the host starts the provider's configuration
 * activity under [GlanceWearWidget.CATEGORY_PROVIDER_CONFIG] with
 * [GlanceWearWidget.EXTRA_CONFIG_WIDGET_ID] (the widget's instance id) and
 * [GlanceWearWidget.EXTRA_CONFIG_PROVIDER_COMPONENT] (its service). The activity sets
 * `RESULT_CANCELED` first, saves the choice and redraws the widget, then finishes `RESULT_OK`.
 * Declare it exported, with an intent filter on that category (and `DEFAULT`); how a host finds it
 * isn't documented by Glance Wear, so also open the same picker from the app.
 */
public object RcWearWidgetConfiguration {

  /** One configuration request: the widget being configured and its provider. */
  public data class Request(val instanceId: Int, val provider: ComponentName?)

  /** The request [intent] carries, or null when it names no widget. */
  @Suppress("DEPRECATION")
  public fun requestOf(intent: Intent?): Request? {
    intent ?: return null
    val id = intent.getIntExtra(GlanceWearWidget.EXTRA_CONFIG_WIDGET_ID, NO_ID)
    if (id == NO_ID) return null
    val provider =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(
          GlanceWearWidget.EXTRA_CONFIG_PROVIDER_COMPONENT,
          ComponentName::class.java,
        )
      } else {
        intent.getParcelableExtra(GlanceWearWidget.EXTRA_CONFIG_PROVIDER_COMPONENT)
      }
    return Request(id, provider)
  }

  /** A widget of this app placed on the watch. */
  public data class PlacedWidget(val instanceId: Int, val provider: ComponentName)

  /**
   * This app's widgets of [providers] placed on the watch, or null when the system can't say (an
   * older Wear OS, no widget host, a failed query). Unknown is not empty: never forget every
   * widget's choice on a failed query.
   */
  public suspend fun placedWidgets(
    context: Context,
    providers: Set<Class<*>>,
  ): List<PlacedWidget>? {
    val names = providers.mapTo(HashSet()) { it.name }
    val active =
      runCatching { GlanceWearWidgetManager(context.applicationContext).fetchActiveWidgets() }
        .getOrNull() ?: return null
    return active
      .filter { it.provider.packageName == context.packageName && it.provider.className in names }
      .map { PlacedWidget(it.instanceId.id, it.provider) }
      .distinctBy { it.instanceId }
      .sortedBy { it.instanceId }
  }

  private const val NO_ID = Int.MIN_VALUE
}

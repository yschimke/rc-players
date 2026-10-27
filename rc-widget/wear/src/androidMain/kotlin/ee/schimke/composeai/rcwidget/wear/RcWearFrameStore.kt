package ee.schimke.composeai.rcwidget.wear

import android.content.Context
import androidx.glance.wear.core.WearWidgetParams
import ee.schimke.composeai.rcwidget.RcWearContainerType
import ee.schimke.composeai.rcwidget.RcWearFrame
import ee.schimke.composeai.rcwidget.RcWearHost

/**
 * The widget container frames this watch's host has reported, so the app can draw a card as it
 * will look once added as a widget. [remember] keeps the last params the host handed a widget of
 * each container type; [frameFor] reads them back, or [default]'s until a widget has been placed.
 */
public class RcWearFrameStore(
  context: Context,
  private val default: RcWearHost = RcWearHost.Default,
  name: String = "rc_wear_widget_frames",
) {
  private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

  /** This watch's frame for [type]. */
  public fun frameFor(type: RcWearContainerType): RcWearFrame {
    val fallback = default.frame(type)
    val key = type.name
    if (!prefs.contains("$key.width")) return fallback
    return RcWearFrame(
      type,
      prefs.getFloat("$key.width", fallback.widthDp),
      prefs.getFloat("$key.height", fallback.heightDp),
      prefs.getFloat("$key.horizontal_padding", fallback.horizontalPaddingDp),
      prefs.getFloat("$key.vertical_padding", fallback.verticalPaddingDp),
      prefs.getFloat("$key.corner_radius", fallback.cornerRadiusDp),
    )
  }

  /** Keeps the frame the host gave [params]: call it from `provideWidgetData`. */
  public fun remember(params: WearWidgetParams) {
    val frame = params.toRcWearFrame()
    val key = frame.type.name
    prefs
      .edit()
      .putFloat("$key.width", frame.widthDp)
      .putFloat("$key.height", frame.heightDp)
      .putFloat("$key.horizontal_padding", frame.horizontalPaddingDp)
      .putFloat("$key.vertical_padding", frame.verticalPaddingDp)
      .putFloat("$key.corner_radius", frame.cornerRadiusDp)
      .apply()
  }
}

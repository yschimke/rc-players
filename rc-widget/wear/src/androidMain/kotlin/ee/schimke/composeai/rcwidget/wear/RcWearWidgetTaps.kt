@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.composeai.rcwidget.wear

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.glance.wear.WearWidgetData
import androidx.glance.wear.core.WearWidgetParams
import androidx.glance.wear.core.WearWidgetRawContent
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcHostActionRewrites

/**
 * Taps on a Wear widget. The host runs only `SendPendingIntent`, whose integer value indexes the
 * `PendingIntent`s in the widget's extras under [PENDING_INTENTS]: a template's own named taps,
 * which the app runs in its player, are rewritten on the widget's final document to send one
 * `PendingIntent` instead — typically the app, opened on what the widget shows.
 */
public object RcWearWidgetTaps {
  /** The index a widget's open-app `PendingIntent` takes when the content has none of its own. */
  public const val OPEN_APP_INDEX: Int = 0

  /** Glance Wear's extras key for a widget's `PendingIntent`s by index (internal to it). */
  public const val PENDING_INTENTS: String = "pending_intents"

  /**
   * [content] whose taps named in [actionNames] send [intent]. The intent takes the first index
   * the content's own `PendingIntent`s leave free ([OPEN_APP_INDEX] when it has none), so taps it
   * already had keep theirs; its extras keep everything else. Unchanged when no tap is named.
   */
  public fun sending(
    content: WearWidgetRawContent,
    actionNames: Set<String>,
    intent: PendingIntent,
  ): WearWidgetRawContent {
    val existing = content.extras.getBundle(PENDING_INTENTS)
    val index =
      existing?.keySet()?.mapNotNull { it.toIntOrNull() }?.maxOrNull()?.plus(1) ?: OPEN_APP_INDEX
    val decoded =
      runCatching { RcDocumentCodec.decode(content.rcDocument, RcWireLimits()) }.getOrNull()
        ?: return content
    val rewritten = RcHostActionRewrites.toPendingIntent(decoded, actionNames, index)
    if (rewritten === decoded) return content
    val document = runCatching { RcDocumentCodec.encode(rewritten) }.getOrNull() ?: return content
    val extras =
      Bundle(content.extras).apply {
        putBundle(
          PENDING_INTENTS,
          Bundle(existing ?: Bundle()).apply { putParcelable(index.toString(), intent) },
        )
      }
    return WearWidgetRawContent(document, extras, content.widgetTitle)
  }
}

/**
 * This widget data with its taps named in [actionNames] sending the `PendingIntent` [intent] makes
 * for each widget: see [RcWearWidgetTaps].
 */
public fun WearWidgetData.withTaps(
  actionNames: Set<String>,
  intent: (Context, WearWidgetParams) -> PendingIntent,
): WearWidgetData {
  val inner = this
  return object : WearWidgetData {
    override suspend fun captureRawContent(
      context: Context,
      params: WearWidgetParams,
      isPreview: Boolean,
    ): WearWidgetRawContent =
      RcWearWidgetTaps.sending(
        inner.captureRawContent(context, params, isPreview),
        actionNames,
        intent(context, params),
      )
  }
}

/**
 * A widget drawn from a template, falling back when it can't be: [template] gives this widget's
 * template (captured locally, read from a store, or fetched), [RcWearWidgetTemplate.instantiate]
 * makes it the widget's document with [values], and [fallback] (a Glance Wear capture, a
 * placeholder) draws it when there is no template or it can't play as a Wear widget.
 */
public class RcTemplatedWearWidgetData(
  private val template: suspend (Context, WearWidgetParams) -> ByteArray?,
  private val fallback: WearWidgetData,
  private val values: Map<String, ee.schimke.composeai.rcplayer.edit.RcHardcodedValue> = emptyMap(),
  private val containerArgb: Int? = null,
) : WearWidgetData {
  override suspend fun captureRawContent(
    context: Context,
    params: WearWidgetParams,
    isPreview: Boolean,
  ): WearWidgetRawContent {
    val bytes = runCatching { template(context, params) }.getOrNull()
    return bytes?.let {
      RcWearWidgetTemplate.instantiate(context, it, params, isPreview, containerArgb, values)
    } ?: fallback.captureRawContent(context, params, isPreview)
  }
}

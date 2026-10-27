package ee.schimke.composeai.rcwidget

import ee.schimke.composeai.rcplayer.edit.RcApproximation
import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcplayer.edit.RcProfileAdaptation
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.edit.adaptTo
import ee.schimke.composeai.rcplayer.edit.hardcodeDensity
import ee.schimke.composeai.rcplayer.edit.hardcodeNamedValues
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcHeader

/**
 * Wear widget templates: one document, captured once with its container frame left as named values
 * ([RcWearContainer]), made into the document for each widget on each watch.
 *
 * Capture a template with `rc-widget-wear`'s `RcWearWidgetTemplate` (or any capture that declares
 * the [RcWearContainer] names), or turn a phone card design into one with [fromCardDesign].
 */
public object RcWearTemplates {

  /**
   * [template] as the document for a widget in [frame] on a watch with [display]: the frame's
   * values and [values] written in, the watch's density and font scale folded in, and everything
   * the Wear widget player can't read rewritten into what it can ([adaptTo] with [approximations]).
   *
   * [header], when given, replaces the result's header: the one Glance Wear writes for this
   * widget's params (canvas, density, profile), which `rc-widget-wear` takes from Glance Wear's own
   * capture of an empty container. Check [RcProfileAdaptation.isPlayable]: a template that can't be
   * made playable falls back to a capture through Glance Wear.
   */
  public fun instantiate(
    template: RcDocument,
    frame: RcWearFrame,
    display: RcDisplay,
    containerArgb: Int? = null,
    values: Map<String, RcHardcodedValue> = emptyMap(),
    header: RcHeader? = null,
    approximations: Set<RcApproximation> = RcWidgetTargets.WearWidget.approximations,
  ): RcProfileAdaptation {
    val filled =
      template
        .hardcodeNamedValues(frame.namedValues(containerArgb) + values)
        .hardcodeDensity(display.density, display.fontScale)
    val adapted = filled.adaptTo(RcTargetProfiles.WEAR_WIDGETS, approximations, display)
    return if (header == null) adapted
    else adapted.copy(document = adapted.document.copy(header = header))
  }

  /**
   * [template] in [frame], for an app's own player: the container values [instantiate] writes for a
   * widget, without the widget header or the Wear widget profile's rewrites, so the app's card has
   * the widget's layout. Its other bindings stay, for the app to push live values.
   */
  public fun forApp(
    template: RcDocument,
    frame: RcWearFrame,
    containerArgb: Int? = null,
  ): RcDocument = template.hardcodeNamedValues(frame.namedValues(containerArgb))

  /**
   * A phone card design as a Wear widget template: its card box — the first rounded, filled
   * surface, when it holds the whole card — made into the widget's container, so a card with no
   * Wear design of its own still plays as one without being captured again. Null when the design
   * has no single card box.
   */
  public fun fromCardDesign(cardDesign: RcDocument): RcDocument? =
    CardDesignReframe.reframe(cardDesign)
}

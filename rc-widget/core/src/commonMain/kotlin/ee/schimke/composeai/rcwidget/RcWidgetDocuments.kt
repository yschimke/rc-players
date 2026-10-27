package ee.schimke.composeai.rcwidget

import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcplayer.edit.RcProfileAdaptation
import ee.schimke.composeai.rcplayer.edit.adaptTo
import ee.schimke.composeai.rcplayer.edit.bitmapUrls
import ee.schimke.composeai.rcplayer.edit.collapseConstantStateLayouts
import ee.schimke.composeai.rcplayer.edit.embedBitmapUrls
import ee.schimke.composeai.rcplayer.edit.hardcodeDensity
import ee.schimke.composeai.rcplayer.edit.hardcodeNamedValues
import ee.schimke.composeai.rcplayer.edit.optimize
import ee.schimke.composeai.rcplayer.protocol.RcDocument

/**
 * Named values a host writes into a template, by type: what a template's bindings resolve to for
 * one state of the world. [toHardcoded] gives the form `hardcodeNamedValues` takes.
 */
public data class RcNamedValues(
  val strings: Map<String, String> = emptyMap(),
  val booleans: Map<String, Boolean> = emptyMap(),
  val ints: Map<String, Int> = emptyMap(),
  val floats: Map<String, Float> = emptyMap(),
  val colors: Map<String, Int> = emptyMap(),
) {
  /** Every value, keyed by name. A name in two maps keeps the later type's value. */
  public fun toHardcoded(): Map<String, RcHardcodedValue> = buildMap {
    for ((name, v) in strings) put(name, RcHardcodedValue.Text(v))
    for ((name, v) in booleans) put(name, RcHardcodedValue.BooleanValue(v))
    for ((name, v) in ints) put(name, RcHardcodedValue.Integer(v))
    for ((name, v) in floats) put(name, RcHardcodedValue.FloatValue(v))
    for ((name, v) in colors) put(name, RcHardcodedValue.Color(v))
  }

  /** These values with [other]'s on top. */
  public operator fun plus(other: RcNamedValues): RcNamedValues =
    RcNamedValues(
      strings + other.strings,
      booleans + other.booleans,
      ints + other.ints,
      floats + other.floats,
      colors + other.colors,
    )

  public companion object {
    public val EMPTY: RcNamedValues = RcNamedValues()
  }
}

/**
 * The document pipeline between a captured template and the bytes a host plays.
 *
 * A template is captured once, values-free, and is the same for every widget showing that design.
 * What a host gets is made from it per serve: [bake] for a host that cannot bind values,
 * [forDisplay] for one that can, and [serve] to pick between them for an [RcWidgetTarget] and
 * rewrite the result into what that target's player reads. Every step is a pure rewrite of an
 * [RcDocument], so this runs as well on a server as on the device.
 */
public object RcWidgetDocuments {

  /**
   * [template] for a host that cannot bind values: [values] written in as constants, each image URL
   * the document loads fetched ([fetchImage]) and embedded, the state layouts that became constant
   * collapsed to their live branch, and the document optimised.
   *
   * Null when an image cannot be fetched: a host that cannot load a URL would draw nothing there,
   * so the caller falls back (a direct capture, a placeholder) rather than serve a hole.
   */
  public inline fun bake(
    template: RcDocument,
    values: Map<String, RcHardcodedValue>,
    fetchImage: (url: String) -> ByteArray?,
  ): RcDocument? {
    val urls = template.bitmapUrls
    val images = HashMap<String, ByteArray>(urls.size)
    for (url in urls) images[url] = fetchImage(url) ?: return null
    return bake(template, values, images)
  }

  /** [bake] with every image the template loads already fetched, keyed by URL. */
  public fun bake(
    template: RcDocument,
    values: Map<String, RcHardcodedValue>,
    images: Map<String, ByteArray> = emptyMap(),
  ): RcDocument {
    val embedded = if (images.isEmpty()) template else template.embedBitmapUrls(images)
    return embedded.hardcodeNamedValues(values).collapseConstantStateLayouts().optimize()
  }

  /**
   * [template], captured at host density, as the document for one [display]: its density and font
   * scale folded into constants and the document optimised. For a host that binds values, so none
   * are written in.
   */
  public fun forDisplay(template: RcDocument, display: RcDisplay): RcDocument =
    template.hardcodeDensity(display.density, display.fontScale).optimize()

  /**
   * [template] as [target] plays it.
   *
   * A target that binds values gets [forDisplay] (when [display] is known); one that cannot gets
   * [bake] with [values] and [images], then has [display] folded in. Either is then rewritten for
   * [target]'s player ([adaptTo], making only the target's own [RcWidgetTarget.approximations]).
   * Check [RcProfileAdaptation.isPlayable] before serving the result: anything left that the player
   * cannot read is in its violations.
   *
   * A result made with a [display] is right only for that display; cache it per display.
   */
  public fun serve(
    template: RcDocument,
    target: RcWidgetTarget,
    display: RcDisplay? = null,
    values: Map<String, RcHardcodedValue> = emptyMap(),
    images: Map<String, ByteArray> = emptyMap(),
  ): RcProfileAdaptation {
    val prepared = if (target.bindsValues) template else bake(template, values, images)
    val displayed = display?.let { prepared.hardcodeDensity(it.density, it.fontScale) } ?: prepared
    return displayed.adaptTo(
      target.profile,
      target.approximations,
      display ?: RcDisplay.DEFAULT,
    )
  }
}

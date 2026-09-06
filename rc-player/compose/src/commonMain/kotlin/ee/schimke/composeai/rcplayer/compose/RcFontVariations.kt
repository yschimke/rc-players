package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.text.font.FontVariation

/**
 * One font-variation axis: a four-character tag (`wght`, `wdth`, `opsz`, …) and the value to
 * instance it at.
 *
 * A plain tag/value pair rather than Compose's `FontVariation.Setting` because this type is part of
 * the player's *published* surface — see [RcFontVariations] for why that matters — and because a
 * pair is all a `.rc` document ever carries: properties 20 and 21 of a `CoreText` style are a list
 * of axis tags and a list of floats.
 */
public class RcFontAxis(public val tag: String, public val value: Float) {
  override fun equals(other: Any?): Boolean =
    this === other || (other is RcFontAxis && tag == other.tag && value == other.value)

  override fun hashCode(): Int = 31 * tag.hashCode() + value.hashCode()

  override fun toString(): String = "$tag=$value"
}

/**
 * The font-variation axes a document names, as the player's own type.
 *
 * **Why not `FontVariation.Settings` directly.** This type is the parameter of
 * [RcTypefaceLoader.typeface] and [RcFontFaces.family], both of which are public and therefore
 * exported into `RcComposePlayer.xcframework` for Swift. Kotlin/Native writes a nested Kotlin class
 * into the generated header as an Objective-C class carrying `swift_name("Outer.Inner")`, and for
 * `FontVariation.Settings` — nested inside an `object` belonging to a module the framework does not
 * export — the outer half never lands in the header. Swift then cannot complete the mapping and
 * warns, on every consumer build, that `imported declaration 'RCPUi_textFontVariationSettings'
 * could not be mapped to 'Ui_textFontVariation.Settings'`, closing with "please report this issue
 * to the owners of 'RcComposePlayer'". Keeping a Compose-internal nested type out of the exported
 * surface is what removes that, and it is the reason to own this type rather than re-export someone
 * else's.
 *
 * It is also the honest shape. The player only ever reads a tag and a float back out — the caching
 * key in [RcFontFaces] already flattened `Settings` to exactly that — and `FontVariation.Setting`
 * carries a density-dependent `toVariationValue` this code could never supply a density for.
 *
 * The conversion to Compose's own type happens once, at the point a `Font` is actually built.
 */
public class RcFontVariations(public val axes: List<RcFontAxis>) {

  /** True when there are no axes at all, in which case the faces are used as registered. */
  public val isEmpty: Boolean
    get() = axes.isEmpty()

  override fun equals(other: Any?): Boolean =
    this === other || (other is RcFontVariations && axes == other.axes)

  override fun hashCode(): Int = axes.hashCode()

  override fun toString(): String = axes.joinToString(separator = ",", prefix = "[", postfix = "]")

  public companion object {
    /** No axes — the faces as registered. */
    public val None: RcFontVariations = RcFontVariations(emptyList())
  }
}

/**
 * These axes as Compose's own type, for the one place that needs it: building a `Font`.
 *
 * Internal on purpose — it names `FontVariation.Settings`, and the whole point of
 * [RcFontVariations] is that the name does not reach the published API.
 */
internal fun RcFontVariations.toComposeSettings(): FontVariation.Settings =
  FontVariation.Settings(*axes.map { FontVariation.Setting(it.tag, it.value) }.toTypedArray())

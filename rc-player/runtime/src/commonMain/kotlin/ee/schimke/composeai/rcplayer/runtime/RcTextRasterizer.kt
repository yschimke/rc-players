package ee.schimke.composeai.rcplayer.runtime

/**
 * Draws one styled text to an image, for a player that cannot draw the style itself.
 *
 * `CoreText` carries typography the older `TextLayout` has no field for — letter spacing, line
 * height, font axes, decoration. `rc-player-edit` can keep that styling for such a player by
 * drawing the text here and embedding the image in its place, rather than dropping it. The contract
 * lives in this module so the editor that asks and the renderer that answers (`rc-player-compose`
 * implements it with the player's own text stack) need not depend on each other.
 *
 * Return null for a text this rasterizer cannot draw as the player would — a family it does not
 * have, say. The text is then left for the editor's other options rather than drawn in a fallback
 * face.
 */
public fun interface RcTextRasterizer {
  public fun rasterize(text: RcStyledText): RcRasterizedText?
}

/**
 * One `CoreText`, its `TextStyle` chain applied and every value resolved, in the units AndroidX's
 * `CoreText` reads them: sizes in pixels at [density], letter spacing in ems.
 */
public data class RcStyledText(
  val text: String,
  /** Device pixels per dp the image is drawn for; the editor shows it at its size in dp. */
  val density: Float,
  /** Font size in pixels. */
  val fontSize: Float,
  /** ARGB. */
  val color: Int,
  /** 1..1000; a bold style is already applied as 700. */
  val fontWeight: Int,
  val italic: Boolean,
  /** The family name the document records, or null for the default family. */
  val fontFamily: String?,
  /** The bytes of the document's own `FontData` for [fontFamily], when it embeds one. */
  val fontData: ByteArray?,
  /** Letter spacing in ems, as `Paint.setLetterSpacing` takes it. */
  val letterSpacing: Float = 0f,
  /** Pixels added to each line's height. */
  val lineHeightAdd: Float = 0f,
  /** Multiplier of the font size giving each line's height; 1 is the font's own. */
  val lineHeightMultiplier: Float = 1f,
  /** Font-variation axes and OpenType features, by tag (`wght`, `wdth`, `tnum`, …), in order. */
  val fontAxes: List<Pair<String, Float>> = emptyList(),
  /** AndroidX text alignment: 1 left, 2 right, 3 centre, 4 justify, 5 start, 6 end. */
  val textAlign: Int = 1,
  /** AndroidX `TextStyle` justification mode: 1 justifies. */
  val justification: Int = 0,
  /** AndroidX line-break strategy: 0 simple, 1 high quality, 2 balanced. */
  val lineBreak: Int = 0,
  /** AndroidX hyphenation frequency: above 0 hyphenates. */
  val hyphenation: Int = 0,
  val underline: Boolean = false,
  val strikethrough: Boolean = false,
  /** AndroidX text overflow: 1 clip, 2 visible, 3 ellipsis, 4 start and 5 middle ellipsis. */
  val overflow: Int = 1,
  val maxLines: Int = Int.MAX_VALUE,
  /** The width in pixels to wrap at, or null to break only at line breaks in [text]. */
  val maxWidth: Float? = null,
) {
  override fun equals(other: Any?): Boolean =
    other is RcStyledText &&
      text == other.text &&
      density == other.density &&
      fontSize == other.fontSize &&
      color == other.color &&
      fontWeight == other.fontWeight &&
      italic == other.italic &&
      fontFamily == other.fontFamily &&
      (fontData?.contentEquals(other.fontData) ?: (other.fontData == null)) &&
      letterSpacing == other.letterSpacing &&
      lineHeightAdd == other.lineHeightAdd &&
      lineHeightMultiplier == other.lineHeightMultiplier &&
      fontAxes == other.fontAxes &&
      textAlign == other.textAlign &&
      justification == other.justification &&
      lineBreak == other.lineBreak &&
      hyphenation == other.hyphenation &&
      underline == other.underline &&
      strikethrough == other.strikethrough &&
      overflow == other.overflow &&
      maxLines == other.maxLines &&
      maxWidth == other.maxWidth

  override fun hashCode(): Int = listOf(text, density, fontSize, color, fontAxes).hashCode()
}

/** A text drawn as a PNG [width] by [height] pixels: its laid-out box, not just its ink. */
public class RcRasterizedText(
  public val png: ByteArray,
  public val width: Int,
  public val height: Int,
)

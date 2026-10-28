package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.text.font.FontFamily
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFontData

/**
 * The fonts a document embeds (`FontData`), instanceable the way [RcFontFaces] instances a host's
 * faces: per font, weight, slant and axis set.
 *
 * **Why the bytes, not a built `FontFamily`.** This replaces a `Map<Int, FontFamily>` built once
 * per document, and that shape is why a `CoreText`'s `wght`/`wdth`/… never reached an embedded font
 * — Compose carries variations on a `Font`, and a built family's faces cannot be re-instanced (the
 * same reason [RcFontFace] keeps the bytes). Holding the payload lets [family] build the instance
 * the text actually asked for, on every target the player builds for: [rcFontFromBytes] is the same
 * call a host face goes through, with the same variation support and the same limits.
 *
 * **Why the weight and slant are part of the key.** The face is registered at the weight and slant
 * the text asked for, as AndroidX's embedded player builds its `Font.Builder` (`DocumentFontCache`,
 * keyed on weight, italic and axes). An embedded font is one file with no sibling faces to choose
 * between, so it *is* the face for whatever weight the text names, and its `wght` axis (which
 * [rcInstancedFontFamily] sets from that weight unless the document names one) moves a variable
 * file there. Where that is visible is synthesis, and it differs by platform: Compose on Android
 * fakes bold or italic only when the requested style is heavier or slanted relative to the
 * *registered* one, so registered at the text's own weight it draws the file as it is — what
 * AndroidX draws. Skia (JVM, Wasm, Apple) decides from the file's own `OS/2` weight, so a static
 * regular face asked for 700 is emboldened there either way; a variable one is not.
 *
 * **Bounded.** A weight or axis bound to a document float animates, asking for a new key on nearly
 * every frame; the cache is least-recently-used out first, as [RcFontFaces] is.
 *
 * Not thread-safe, like [RcFontFaces]: it is read from composition and draw.
 */
internal class RcDocumentFonts(fonts: Map<Int, ByteArray>) {

  private class Payload(val fontId: Int, val data: ByteArray) {
    /**
     * Compose's font cache keys on the identity and is process-wide, so the identity names the
     * *bytes*, not only the document's id for them: two documents (or two text images) that both
     * embed a font under id 42 must not be handed each other's typeface.
     */
    val identity: String by lazy { "remote-compose-$fontId-${data.size}-${fnv1a64Hex(data)}" }
  }

  private data class Key(
    val fontId: Int,
    val weight: Int,
    val italic: Boolean,
    val axes: List<RcFontAxis>,
  )

  private val payloads: Map<Int, Payload> = fonts.mapValues { (id, data) -> Payload(id, data) }

  /**
   * Insertion-ordered, re-inserted on each hit; a null value records a font that failed to build.
   */
  private val instances = LinkedHashMap<Key, FontFamily?>()

  operator fun contains(fontId: Int): Boolean = fontId in payloads

  /** Test-only: how many instances are held. */
  internal val cachedInstanceCount: Int
    get() = instances.size

  /**
   * The embedded font [fontId] at [weight], [italic] and [variations], or null when the document
   * embeds no font under that id or the platform cannot build one from its bytes.
   */
  fun family(
    fontId: Int,
    weight: Int = 400,
    italic: Boolean = false,
    variations: RcFontVariations? = null,
  ): FontFamily? {
    val payload = payloads[fontId] ?: return null
    val key = Key(fontId, weight.coerceIn(1, 1000), italic, variations?.axes.orEmpty())
    if (key in instances) {
      val cached = instances.remove(key)
      instances[key] = cached
      return cached
    }
    val built = runCatching {
      rcInstancedFontFamily(
        listOf(RcFontFace(payload.identity, payload.data, key.weight, key.italic)),
        key.axes,
      )
    }
      .getOrNull()
    if (instances.size >= MAX_INSTANCES) instances.remove(instances.keys.first())
    instances[key] = built
    return built
  }

  companion object {
    /** No embedded fonts. */
    val Empty: RcDocumentFonts = RcDocumentFonts(emptyMap())

    /**
     * Twice [RcFontFaces.MAX_INSTANCES]: weight joins the key here, and a document may embed
     * several fonts.
     */
    const val MAX_INSTANCES: Int = 64

    /** The fonts [document] embeds, by id; a later `FontData` for an id replaces an earlier one. */
    fun of(document: RcDocument): RcDocumentFonts =
      RcDocumentFonts(
        document.operations.filterIsInstance<RcFontData>().associate { it.fontId to it.data }
      )
  }
}

/**
 * 64-bit FNV-1a of [data], as hex. A content fingerprint for a cache identity, not a security
 * boundary — common code has no SHA, and the identity also carries the payload's size and id.
 */
private fun fnv1a64Hex(data: ByteArray): String {
  var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325, the FNV-1a 64-bit offset basis
  for (byte in data) {
    hash = hash xor (byte.toLong() and 0xff)
    hash *= 0x100000001b3L
  }
  return hash.toULong().toString(16)
}

/**
 * AndroidX `PaintBundle` reads a `TYPEFACE` font type above this as a text id naming the family; at
 * or below it, as a generic family (0–3) or a `FontData` id.
 */
internal const val PAINT_MAX_GENERIC_FONT_TYPE: Int = 10

/** The `TYPEFACE` style bit marking the font type as a `FontData` id rather than a family name. */
internal const val PAINT_TYPEFACE_FONT_DATA: Int = 1024

/** What a paint's `TYPEFACE` names: a family by name, or a document font by its `FontData` id. */
internal class RcPaintTypeface(val familyName: String?, val fontDataId: Int)

/**
 * The typeface a `PaintData` `TYPEFACE` command names — the canvas-text twin of `CoreText`'s
 * property 8, resolved by the same rules.
 *
 * * 0–3 are the generic families.
 * * With the [PAINT_TYPEFACE_FONT_DATA] bit — what `RcPaint.setTypeface(fontDataId)` writes — the
 *   font type is a `FontData` id.
 * * Without it, a font type above [PAINT_MAX_GENERIC_FONT_TYPE] is the text id of a family name
 *   (`RcPaint.setTypeface(String)`); a font type with no text under it that the document embeds a
 *   font for is that font, as AndroidX's embedded player resolves it (`name == null` falls through
 *   to `documentFontTypeface`).
 *
 * There is no trust gate: the player loads a document's `FontData` for `CoreText` unconditionally,
 * and canvas text follows the same rule (see `RC_CMP_UPSTREAM_REVIEW_2026_10.md`).
 *
 * Anything else is an error, as before: the support report refuses such a document first.
 */
internal fun rcPaintTypeface(
  fontType: Int,
  style: Int,
  text: (Int) -> String?,
  fonts: RcDocumentFonts,
): RcPaintTypeface =
  when {
    fontType == 0 -> RcPaintTypeface(RC_DEFAULT_FAMILY, -1)
    fontType == 1 -> RcPaintTypeface("sans-serif", -1)
    fontType == 2 -> RcPaintTypeface("serif", -1)
    fontType == 3 -> RcPaintTypeface("monospace", -1)
    style and PAINT_TYPEFACE_FONT_DATA != 0 ->
      if (fontType in fonts) RcPaintTypeface(null, fontType)
      else error("AndroidX FontData font $fontType is not in the document")
    fontType > PAINT_MAX_GENERIC_FONT_TYPE ->
      // A family-name text that shares a `FontData` id is that font, as for `CoreText`.
      text(fontType)?.let { RcPaintTypeface(it, if (fontType in fonts) fontType else -1) }
        ?: if (fontType in fonts) RcPaintTypeface(null, fontType)
        else error("AndroidX font family text $fontType is not in the document")
    fontType in fonts -> RcPaintTypeface(null, fontType)
    else -> error("AndroidX font id $fontType is not implemented by the CMP backend")
  }

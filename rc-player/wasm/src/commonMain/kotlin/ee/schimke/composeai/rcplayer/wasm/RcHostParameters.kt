package ee.schimke.composeai.rcplayer.wasm

import ee.schimke.composeai.rcplayer.runtime.RcHostValueType
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import kotlin.math.truncate

/**
 * Pure parsing for the browser host's page parameters, kept out of `Main.kt` so it runs under a JVM
 * test: the host itself reaches the browser only through `js(...)`, which nothing but a browser can
 * execute.
 */

/**
 * The accepted spelling of `?density=`: plain decimal, optionally with an exponent. Checked before
 * `toFloatOrNull`, whose accepted forms differ by platform — the JVM's takes `2.6f`, `0x1p1`, `NaN`
 * and `Infinity` — so the browser and the test agree on exactly what the page may send.
 */
private val DENSITY_PATTERN = Regex("""[0-9]*\.?[0-9]+(?:[eE][+-]?[0-9]+)?""")

/**
 * The largest `?density=` taken. Real screens run up to about 5 px per dp, and a host scaling a
 * snapshot for display multiplies that by its zoom; past this, a dp value times the density can
 * overflow layout arithmetic downstream (`RcComposePlayer` multiplies dp values by it again), so a
 * larger value is treated as malformed rather than clamped.
 */
internal const val MAX_DENSITY_PARAMETER = 64f

/**
 * `?density=`: the document's pixels per dp, measured in CSS pixels. A positive finite decimal up
 * to [MAX_DENSITY_PARAMETER], or null for anything else — absent, empty, zero, negative,
 * non-finite, too large or malformed — in which case the host keeps the browser's own density
 * (`devicePixelRatio`), exactly as before the parameter existed. A bad value is ignored rather than
 * reported: failing to *scale* a document is not failing to *load* one, which is how
 * `?namedValues=` treats its malformed input too.
 */
internal fun parseDensityParameter(raw: String?): Float? {
  val text = raw?.trim().orEmpty()
  if (!DENSITY_PATTERN.matches(text)) return null
  return text.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f && it <= MAX_DENSITY_PARAMETER }
}

/**
 * The Compose density the player runs at.
 *
 * [platformDensity] is the viewport's own `LocalDensity`, which Compose Multiplatform on the web
 * derives from `devicePixelRatio`: device pixels per CSS pixel. With no [requested] density it is
 * returned unchanged. With one, a dp becomes [requested] CSS pixels, i.e. `requested ×
 * platformDensity` device pixels, so the canvas keeps rendering at the screen's full resolution
 * while the document is laid out and drawn at the scale it was captured at. A product that is not a
 * usable density (a huge [requested] overflowing to infinity) falls back to [platformDensity], as
 * an unparseable one does.
 */
internal fun hostDensity(platformDensity: Float, requested: Float?): Float {
  if (requested == null) return platformDensity
  val scaled = platformDensity * requested
  return if (scaled.isFinite() && scaled > 0f) scaled else platformDensity
}

/**
 * The browser's preferred language tags, reduced to ones Compose can parse.
 *
 * Compose's web `Locale.current` builds an `Intl.Locale` from every entry of `navigator.languages`,
 * and `Intl.Locale` throws `RangeError: Incorrect locale information provided` on anything that is
 * not a BCP 47 tag. Browsers do report such values — a POSIX locale leaks through as `en-US@posix`
 * or `en_US.UTF-8` on some Linux setups — and a throw there takes down the composition, so the
 * whole document fails to render over a language preference it never reads.
 *
 * Each tag is tried as given, then with its POSIX decorations removed (`.codeset`, `@modifier`, `_`
 * as the separator), then as its primary language subtag; the first form [isValid] accepts is kept
 * and the rest of the entry is dropped. Order is preserved and duplicates removed. If nothing
 * survives, the list is [FALLBACK_LANGUAGE_TAG] alone, so it is never empty.
 */
internal fun usableLanguageTags(tags: List<String>, isValid: (String) -> Boolean): List<String> {
  val usable = LinkedHashSet<String>()
  for (tag in tags) {
    languageTagCandidates(tag).firstOrNull(isValid)?.let(usable::add)
  }
  return usable.toList().ifEmpty { listOf(FALLBACK_LANGUAGE_TAG) }
}

private fun languageTagCandidates(tag: String): List<String> {
  val trimmed = tag.trim()
  if (trimmed.isEmpty()) return emptyList()
  val posixStripped = trimmed.substringBefore('@').substringBefore('.').replace('_', '-')
  val primary = posixStripped.substringBefore('-')
  return listOf(trimmed, posixStripped, primary).filter { it.isNotEmpty() }.distinct()
}

internal const val FALLBACK_LANGUAGE_TAG: String = "en"

/** The player extensions `?extensions=` can turn on, by the name a page gives them. */
internal enum class RcHostExtension(val parameterName: String) {
  /** The draft 3D operations, `:rc-player-3d` (docs/design/RC_3D_EXTENSION.md). */
  ThreeD("3d"),
}

/**
 * `?extensions=`: a comma-separated list of [RcHostExtension] names, case-insensitive. Absent or
 * empty, none: a document using an extension's opcodes fails to decode, as it does on AndroidX's
 * players, so the parity lanes keep comparing like with like. A name this bundle does not know is
 * ignored rather than reported, so a page written for a newer bundle still loads on this one.
 */
internal fun parseExtensionsParameter(raw: String?): Set<RcHostExtension> {
  val names = raw.orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
  return RcHostExtension.entries.filterTo(mutableSetOf()) { it.parameterName in names }
}

/** Which 3D rasterizer `?rc3dRenderer=` asks for; see `Rc3DRenderer`. */
internal enum class Rc3DRendererParameter {
  Auto,
  Software,
  Gpu,
}

/**
 * `?rc3dRenderer=`: `software` or `gpu` forces one 3D rasterizer for every draw, which is what lets
 * a test compare the two in the shipped bundle. Anything else, or none, is `auto`, the player's own
 * choice per draw.
 */
internal fun parse3DRendererParameter(raw: String?): Rc3DRendererParameter =
  when (raw?.trim()?.lowercase()) {
    "software" -> Rc3DRendererParameter.Software
    "gpu" -> Rc3DRendererParameter.Gpu
    else -> Rc3DRendererParameter.Auto
  }

/**
 * The `kind` names the embed contract accepts (docs/design/RC_PLAYER_EMBED.md, the `namedValues`
 * row; a test keeps the two lists equal). Each resolves through [RcHostValueType], the one owner of
 * host type names; the contract accepts only these spellings.
 */
internal val EMBED_TYPE_NAMES: List<String> =
  listOf("string", "float", "dp", "int", "bool", "color", "long")

/**
 * The `?namedValues=` overrides carried by [rows], keyed `USER:<name>`: the internal shape
 * `Main.kt`'s `flattenNamedValuesFromLocation` hands across the JS/Wasm boundary, not the wire
 * format. Rows are separated by U+0001; each is four U+0000-separated fields — the `kind`, the
 * `name` and the `value` as `encodeURIComponent` wrote them, so none can contain a separator, then
 * `n` when `value` was a JSON number. [decodeComponent] undoes the encoding (`decodeURIComponent`
 * in the browser) and may throw on a field that is not percent-encoding.
 *
 * Each row stands alone: one that is malformed, names no variable or does not parse as its kind is
 * dropped and the rest are kept, as docs/design/RC_PLAYER_EMBED.md promises of the elements they
 * come from. When two rows name one variable, the later wins.
 */
internal fun namedValuesFromRows(
  rows: String,
  decodeComponent: (String) -> String,
): Map<String, RcNamedValue> {
  if (rows.isEmpty()) return emptyMap()
  return buildMap {
    rows.split('\u0001').forEach { row ->
      val fields = row.split('\u0000')
      if (fields.size != 4) return@forEach
      // A field [decodeComponent] rejects (a stray `%`) drops this row, not the whole array.
      val (kind, name, value) =
        runCatching { fields.take(3).map(decodeComponent) }.getOrNull() ?: return@forEach
      val namedValue = parseNamedValue(typeName = kind, value = value, isNumber = fields[3] == "n")
      if (name.isNotEmpty() && namedValue != null) put("USER:$name", namedValue)
    }
  }
}

/**
 * One `?namedValues=` element's value as the player takes it, or null when [typeName] is not one of
 * [EMBED_TYPE_NAMES] or [value] does not parse as it — the element is then dropped, as
 * docs/design/RC_PLAYER_EMBED.md describes.
 *
 * `bool` is a [RcNamedValue.BooleanValue], as the iOS controller and the embedded live bridge write
 * a boolean, mirroring AndroidX, where `setNamedBooleanOverride` is a separate call; the player
 * stores it as the integer 1 or 0. It does not validate: anything but `true` is false, as the embed
 * contract documents.
 *
 * [isNumber] says the host passed `value` as a JSON number rather than a string; only `color` reads
 * the two differently (see [argbFromNumber]).
 */
internal fun parseNamedValue(
  typeName: String,
  value: String,
  isNumber: Boolean = false,
): RcNamedValue? =
  when (if (typeName in EMBED_TYPE_NAMES) RcHostValueType.ofName(typeName) else null) {
    RcHostValueType.STRING -> RcNamedValue.Text(value)
    RcHostValueType.FLOAT -> value.toFloatOrNull()?.let(RcNamedValue::FloatValue)
    RcHostValueType.INT -> value.toIntOrNull()?.let(RcNamedValue::Integer)
    RcHostValueType.BOOLEAN -> RcNamedValue.BooleanValue(value == "true")
    RcHostValueType.COLOR ->
      (if (isNumber) argbFromNumber(value) else parseArgbColor(value))?.let(RcNamedValue::Color)
    RcHostValueType.LONG -> value.toLongOrNull()?.let(RcNamedValue::LongValue)
    RcHostValueType.IMAGE,
    RcHostValueType.FLOAT_ARRAY,
    null -> null
  }

/**
 * A `color` value as ARGB: `RRGGBB`, which is opaque, or `AARRGGBB`, each with or without a leading
 * `#` and surrounding whitespace as JavaScript's `trim` sees it — the forms
 * docs/design/RC_PLAYER_EMBED.md lists, read as the web player's `HostApi.parseColor` reads a
 * string. Null for anything else, so the element is dropped like any other value that does not
 * parse.
 */
internal fun parseArgbColor(value: String): Int? {
  val hex =
    ARGB_COLOR_PATTERN.matchEntire(value.trim(Char::isJsWhitespace))?.groupValues?.get(1)
      ?: return null
  val argb = hex.toLong(radix = 16)
  return (if (hex.length == 6) argb or 0xFF000000L else argb).toInt()
}

/**
 * A `color` passed as a JSON number, which is the ARGB itself (`0xFFFF8800`), as the web player's
 * `HostApi.parseColor` reads one with `v | 0`: JavaScript's ToInt32, which truncates a fraction and
 * wraps the integer modulo 2^32, so `0xFFFF8800` and its signed form `-30720` are the same colour
 * and `0x1FFFFFFFF` is `-1`. [value] is the number as JavaScript's `String` writes it. Null when it
 * is not finite (`parseColor` then tries it as hex, which never matches), so the element is
 * dropped.
 */
internal fun argbFromNumber(value: String): Int? {
  val number = value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
  // `%` on doubles is exact, and leaves an integer of magnitude below 2^32 whose low 32 bits are
  // ToInt32's result.
  return (truncate(number) % 4_294_967_296.0).toLong().toInt()
}

/**
 * Whether JavaScript's `String.prototype.trim` removes [this]: its WhiteSpace and LineTerminator
 * sets, which are tab, U+000B, U+000C, the byte order mark U+FEFF, the space separators (Zs), and
 * LF, CR, U+2028 and U+2029. Kotlin's `trim()` differs both ways: it keeps U+FEFF and removes the
 * information separators U+001C-U+001F.
 */
private fun Char.isJsWhitespace(): Boolean =
  when (this) {
    '\t',
    '\n',
    '\u000B',
    '\u000C',
    '\r',
    ' ',
    '\u00A0',
    '\u1680',
    '\u2028',
    '\u2029',
    '\u202F',
    '\u205F',
    '\u3000',
    '\uFEFF' -> true
    else -> this in '\u2000'..'\u200A'
  }

private val ARGB_COLOR_PATTERN = Regex("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")

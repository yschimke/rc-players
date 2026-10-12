package ee.schimke.composeai.rcplayer.wasm

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
 * `?density=`: the document's pixels per dp, measured in CSS pixels. A positive finite decimal, or
 * null for anything else — absent, empty, zero, negative, non-finite or malformed — in which case
 * the host keeps the browser's own density (`devicePixelRatio`), exactly as before the parameter
 * existed. A bad value is ignored rather than reported: failing to *scale* a document is not
 * failing to *load* one, which is how `?namedValues=` treats its malformed input too.
 */
internal fun parseDensityParameter(raw: String?): Float? {
  val text = raw?.trim().orEmpty()
  if (!DENSITY_PATTERN.matches(text)) return null
  return text.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
}

/**
 * The Compose density the player runs at.
 *
 * [platformDensity] is the viewport's own `LocalDensity`, which Compose Multiplatform on the web
 * derives from `devicePixelRatio`: device pixels per CSS pixel. With no [requested] density it is
 * returned unchanged. With one, a dp becomes [requested] CSS pixels, i.e. `requested ×
 * platformDensity` device pixels, so the canvas keeps rendering at the screen's full resolution
 * while the document is laid out and drawn at the scale it was captured at.
 */
internal fun hostDensity(platformDensity: Float, requested: Float?): Float =
  if (requested == null) platformDensity else platformDensity * requested

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

package ee.schimke.composeai.rcplayer.desktop

import androidx.compose.ui.text.font.FontFamily
import ee.schimke.composeai.rcplayer.compose.RcDownloadedFont
import ee.schimke.composeai.rcplayer.compose.RcFontVariations
import ee.schimke.composeai.rcplayer.compose.RcTypefaceLoader
import ee.schimke.composeai.rcplayer.compose.rcDownloadableFontFallback
import ee.schimke.composeai.rcplayer.compose.rcDownloadedTypefaceLoader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches the `google:` families a document names from Google Fonts, the JVM counterpart of
 * `Sources/RcPlayerAppleFonts/RemoteComposeDownloadableFonts.swift`.
 *
 * Only `fonts.googleapis.com` stylesheets and `fonts.gstatic.com` faces are accepted, both over
 * HTTPS and size-capped. A family that cannot be fetched — offline, unknown, or refused — falls
 * back to the player's default face rather than refusing the document, which is what the macOS app
 * does too: a missing font is a fidelity loss, not a reason not to look at the document.
 */
internal object RcGoogleFonts {
  private const val MAX_STYLESHEET_BYTES = 256 * 1024
  private const val MAX_FONT_BYTES = 16 * 1024 * 1024
  private val familyPattern = Regex("[\\p{L}\\p{N} .-]{1,100}")

  private val client: HttpClient by lazy {
    HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .followRedirects(HttpClient.Redirect.NEVER)
      .build()
  }

  private val cache = ConcurrentHashMap<String, RcDownloadedFont>()

  /**
   * A loader for [families]: downloaded faces where [download] allows and the fetch succeeds, the
   * player's default face for the rest.
   */
  fun typefaces(families: List<String>, download: Boolean): RcTypefaceLoader {
    if (families.isEmpty()) return RcTypefaceLoader.Default
    val downloaded = if (download) families.mapNotNull(::fetchOrNull) else emptyList()
    val fallback = rcDownloadableFontFallback(families)
    if (downloaded.isEmpty()) return fallback
    return RcFirstTypefaceLoader(rcDownloadedTypefaceLoader(downloaded), fallback)
  }

  private fun fetchOrNull(family: String): RcDownloadedFont? {
    val name = family.trim()
    if (!familyPattern.matches(name)) return null
    val key = name.lowercase()
    cache[key]?.let {
      return it
    }
    // The variable weight range first, so one face covers every weight a run asks for; a family
    // without a `wght` axis answers that with a 400, hence the plain query after it.
    val font =
      listOf("$name:wght@100..1000", name).firstNotNullOfOrNull { query ->
        runCatching { download(query, name, key) }.getOrNull()
      } ?: return null
    cache[key] = font
    return font
  }

  private fun download(query: String, family: String, key: String): RcDownloadedFont? {
    val stylesheetUri =
      URI("https://fonts.googleapis.com/css2?family=" + URLEncoder.encode(query, Charsets.UTF_8))
    val stylesheet =
      get(stylesheetUri, "fonts.googleapis.com", MAX_STYLESHEET_BYTES)?.decodeToString()
        ?: return null
    val faceUri = fontUri(stylesheet) ?: return null
    if (faceUri.scheme != "https" || !faceUri.host.equals("fonts.gstatic.com", true)) return null
    val data = get(faceUri, "fonts.gstatic.com", MAX_FONT_BYTES) ?: return null
    val digest =
      MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    return RcDownloadedFont(family, "google:$key#sha256:$digest", data)
  }

  private fun get(uri: URI, host: String, maximumBytes: Int): ByteArray? {
    if (uri.scheme != "https" || !uri.host.equals(host, ignoreCase = true)) return null
    val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
    response.body().use { body ->
      if (response.statusCode() != 200) return null
      val bytes = body.readNBytes(maximumBytes + 1)
      return bytes.takeIf { it.size <= maximumBytes }
    }
  }

  /**
   * The face to register. A client without a browser user agent is served TrueType, which Skia
   * loads everywhere; WOFF2 is accepted as a fallback, preferring the last block because Google
   * lists the Latin range last.
   */
  internal fun fontUri(stylesheet: String): URI? {
    val truetype =
      Regex(
        """url\(['"]?([^)'"]+)['"]?\)\s*format\(['"]?(?:truetype|opentype)['"]?\)""",
        RegexOption.IGNORE_CASE,
      )
    val woff2 = Regex("""url\(['"]?([^)'"]+\.woff2)['"]?\)""", RegexOption.IGNORE_CASE)
    val url =
      truetype.find(stylesheet)?.groupValues?.get(1)
        ?: woff2.findAll(stylesheet).lastOrNull()?.groupValues?.get(1)
        ?: return null
    return runCatching { URI(url.trim()) }.getOrNull()
  }
}

/** Asks [primary] first and [secondary] for whatever [primary] cannot resolve. */
private class RcFirstTypefaceLoader(
  private val primary: RcTypefaceLoader,
  private val secondary: RcTypefaceLoader,
) : RcTypefaceLoader {
  override val families: Set<String> = primary.families + secondary.families

  override fun typeface(family: String, variations: RcFontVariations?): FontFamily? =
    primary.typeface(family, variations) ?: secondary.typeface(family, variations)
}

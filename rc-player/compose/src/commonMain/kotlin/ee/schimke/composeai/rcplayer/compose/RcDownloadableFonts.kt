package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyle
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty

/** A `google:` family needed by a host player before its first render. */
public data class RcDownloadableFontRequest(public val family: String)

/** Font bytes downloaded by a host and ready to enter Compose's synchronous font loader. */
public class RcDownloadedFont(
  public val family: String,
  public val identity: String,
  public val data: ByteArray,
)

/**
 * Finds the Google Fonts explicitly referenced by layout-text operations in [bytes].
 *
 * Network work stays in the host. This bridge only uses the authoritative protocol decoder so an
 * adapter does not need a second partial wire parser and never fetches a string that merely happens
 * to look like a font name.
 */
public fun rcDownloadableFontRequests(bytes: ByteArray): List<RcDownloadableFontRequest> {
  val document = decodeCmpDocument(bytes)
  val texts = document.operations.filterIsInstance<RcTextData>().associate { it.id to it.text }
  val embedded =
    document.operations.filterIsInstance<RcFontData>().mapTo(mutableSetOf()) { it.fontId }
  val familyIds =
    document.operations.mapNotNullTo(mutableSetOf()) { operation ->
      when (operation) {
        is RcTextLayout -> operation.fontFamilyId
        is RcCoreText ->
          operation.properties
            .filterIsInstance<RcTextStyleProperty.IntValue>()
            .lastOrNull { it.id == 8 }
            ?.value
        is RcTextStyle ->
          operation.properties
            .filterIsInstance<RcTextStyleProperty.IntValue>()
            .lastOrNull { it.id == 8 }
            ?.value
        else -> null
      }
    }
  return familyIds
    .asSequence()
    .filterNot { it in embedded }
    .mapNotNull(texts::get)
    .map(String::trim)
    .filter { it.startsWith("google:", ignoreCase = true) }
    .map { it.substringAfter(':').trim() }
    .filter(String::isNotEmpty)
    .distinctBy(String::lowercase)
    .sortedBy(String::lowercase)
    .map(::RcDownloadableFontRequest)
    .toList()
}

/** Builds the synchronous Compose loader after a host has completed asynchronous downloads. */
public fun rcDownloadedTypefaceLoader(fonts: List<RcDownloadedFont>): RcTypefaceLoader =
  RcBundledTypefaceLoader(
    fonts.associate { font ->
      font.family.lowercase() to RcFontFaces(RcFontFace(identity = font.identity, data = font.data))
    }
  )

/** Allows explicit downloadable families to use Compose's default face in an offline host. */
public fun rcDownloadableFontFallback(families: List<String>): RcTypefaceLoader =
  object : RcTypefaceLoader {
    override val families: Set<String> = families.mapTo(mutableSetOf()) { it.lowercase() }

    override fun typeface(
      family: String,
      variations: RcFontVariations?,
    ): androidx.compose.ui.text.font.FontFamily? = null
  }

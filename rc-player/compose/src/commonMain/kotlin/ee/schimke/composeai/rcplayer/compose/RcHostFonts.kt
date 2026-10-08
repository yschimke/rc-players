package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * One host-supplied face: the font file's bytes plus the `(weight, italic)` it was registered for.
 *
 * The **bytes** are the point. A host could hand the player a ready-made [FontFamily], and that is
 * all a document naming a family needs — but a document may also name font-variation *axes*
 * (`wght`, `wdth`, `opsz`, …), and Compose carries those on a `Font`, not on a `TextStyle`. Once a
 * `FontFamily` exists its faces can no longer be re-instanced, so the axes could only be dropped.
 * Keeping the bytes is what lets [RcFontFaces] build the instance the document actually asked for.
 */
public class RcFontFace(
  public val identity: String,
  public val data: ByteArray,
  public val weight: Int = 400,
  public val italic: Boolean = false,
)

/**
 * The faces a host offers for one family, instanceable at a set of variation axes.
 *
 * [family] with no variations is the plain family — the faces as registered, Compose selecting
 * between them by weight/slant. With settings it is the same file(s) re-instanced at those axis
 * values, which for a variable font is a genuinely different shape (`wdth 25` is a narrower face,
 * not a scaled one) and for a static font is a no-op the font engine ignores.
 *
 * Instances are cached per axis set: a document draws the same family at the same axes on every
 * frame, and re-parsing the file per frame would be visible on a text-heavy watch face. The cache
 * is bounded, least recently used out first, because an *animated* axis — a `RemoteFloat` value —
 * asks for a new set on nearly every frame and would otherwise keep every instance it ever drew.
 */
public class RcFontFaces(private val faces: List<RcFontFace>) {

  public constructor(face: RcFontFace) : this(listOf(face))

  /**
   * The identities of the underlying faces, in registration order.
   *
   * Test-only. Compose's font cache keys on the identity, so "these two faces are distinct as far
   * as the cache is concerned" is a real property with no other observable: the resolved
   * [FontFamily] does not expose it, and two colliding identities differ only in which bytes get
   * drawn.
   */
  internal val identities: List<String>
    get() = faces.map { it.identity }

  private fun instanceSuffix(axes: List<Pair<String, Float>>): String =
    if (axes.isEmpty()) ""
    else axes.joinToString(separator = ",", prefix = "#") { (tag, value) -> "$tag=$value" }

  private fun faceSuffix(face: RcFontFace): String =
    "@${face.weight}" + if (face.italic) "i" else ""

  /** Insertion-ordered, and re-inserted on each hit, so the first entry is the least recent. */
  private val instances = LinkedHashMap<List<Pair<String, Float>>, FontFamily>()

  /** Test-only: how many axis-set instances are held. */
  internal val cachedInstanceCount: Int
    get() = instances.size

  /** The [FontFamily] for these faces at [variations], or null when there are no faces at all. */
  public fun family(variations: RcFontVariations? = null): FontFamily? {
    if (faces.isEmpty()) return null
    val key = variations?.axes.orEmpty().map { it.tag to it.value }
    instances.remove(key)?.let {
      instances[key] = it
      return it
    }
    val documentAxes = variations?.takeIf { !it.isEmpty }?.axes.orEmpty()
    val built =
      runCatching {
        FontFamily(
          faces.map { face ->
            val weight = FontWeight(face.weight)
            val style = if (face.italic) FontStyle.Italic else FontStyle.Normal
            // Every face carries its own `wght`/`ital` unless the document sets them, as Android's
            // file `Font` does by default. Without them a variable file listed at several weights
            // (one row per weight, one file) draws its default instance at all of them; a static
            // face ignores the settings.
            val settings = buildList {
              if (documentAxes.none { it.tag == "wght" }) add(FontVariation.weight(face.weight))
              if (documentAxes.none { it.tag == "ital" }) {
                add(FontVariation.italic(if (face.italic) 1f else 0f))
              }
              documentAxes.forEach { add(FontVariation.Setting(it.tag, it.value)) }
            }
            rcFontFromBytes(
              // The identity carries the face's weight and the axes, because Compose's font cache
              // keys on it: two instances of one file that share an identity are the *same* cached
              // typeface, so the first one drawn would silently be used for every later one (every
              // line of a `wght` ramp rendering at the first line's weight, or every row of a
              // variable family at its first row's).
              identity = face.identity + faceSuffix(face) + instanceSuffix(key),
              data = face.data,
              weight = weight,
              style = style,
              variationSettings = FontVariation.Settings(*settings.toTypedArray()),
            )
          }
        )
      }
        .getOrNull() ?: return null
    if (instances.size >= MAX_INSTANCES) instances.remove(instances.keys.first())
    instances[key] = built
    return built
  }

  internal companion object {
    /**
     * Enough for every static axis set a document draws plus a full sweep of an animated one at a
     * few distinct values per frame of a short transition; past it the oldest instance is rebuilt
     * if it is needed again.
     */
    const val MAX_INSTANCES: Int = 32
  }
}

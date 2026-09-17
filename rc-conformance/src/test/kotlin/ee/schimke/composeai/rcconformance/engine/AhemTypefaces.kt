package ee.schimke.composeai.rcconformance.engine

import androidx.compose.ui.text.font.FontFamily
import ee.schimke.composeai.rcplayer.compose.RcFontFace
import ee.schimke.composeai.rcplayer.compose.RcFontFaces
import ee.schimke.composeai.rcplayer.compose.RcFontVariations
import ee.schimke.composeai.rcplayer.compose.RcTypefaceLoader
import java.io.File

/**
 * Resolves **every** family a document names to the corpus's own Ahem face.
 *
 * This is required setup, not a convenience (`CONFORMANCE_FORMAT.md` §2.2). Ahem's glyphs are solid
 * 1em squares, so a text run's pixels depend only on where the run was placed — which is what the
 * corpus asserts. Glyph shape is not: two engines disagreeing about the outline of a lowercase `g`
 * is not a conformance failure, but in a pixel comparison it is indistinguishable from one. Before
 * the font was pinned, every text-bearing gold carried a permanent error floor from that mismatch
 * alone; on `text_on_path_glyph_placement` pinning it took the score from 9.44 to 0.06.
 *
 * Substituting the family wholesale is intended, which is why [typeface] answers for anything asked
 * of it and never returns null: `sans-serif`, `serif` and `monospace` all collapse to Ahem, because
 * the corpus asserts placement rather than font selection. Returning null for an unknown name would
 * hand that run to Compose's built-in face and silently reintroduce the error floor this class
 * exists to remove.
 *
 * The bytes go through the player's own [RcFontFaces], rather than a hand-rolled `FontFamily`, so
 * this lane loads a face by exactly the path a host does — including the caching, which matters
 * because [typeface] is called during composition and draw on every frame.
 */
public class AhemTypefaces(ahemTtf: File) : RcTypefaceLoader {
  init {
    require(ahemTtf.isFile) {
      "the corpus font is missing at ${ahemTtf.absolutePath} — every text-bearing gold would be " +
        "scored against the platform default face, which measures differently and makes the text " +
        "results meaningless"
    }
  }

  private val faces = RcFontFaces(RcFontFace(identity = "Ahem", data = ahemTtf.readBytes()))

  private val ahem: FontFamily =
    checkNotNull(faces.family()) { "Compose declined to instance the corpus's Ahem face" }

  /**
   * Named families this loader claims, lowercased as the interface requires.
   *
   * `composeSupportReport` checks a document's named families against this set before it draws, so
   * the generic names have to be present: without them a document naming `sans-serif` is reported
   * as an unsupported operation, when in this lane it resolves perfectly well.
   */
  override val families: Set<String> = setOf("ahem", "default", "sans-serif", "serif", "monospace")

  override fun typeface(family: String, variations: RcFontVariations?): FontFamily = ahem
}

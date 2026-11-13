package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.graphics.ImageBitmap
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLayoutNode
import ee.schimke.composeai.rcplayer.runtime.RcLayoutTree
import ee.schimke.composeai.rcplayer.runtime.RcLinkedDocument
import ee.schimke.composeai.rcplayer.runtime.RcPlayerStateSeed

/**
 * A decoded [document] with the work every player of it would otherwise repeat done once: linking,
 * the layout tree, the touch-expression index, and decoding the document's inline images and fonts.
 *
 * Every player that plays the same document can share one. A feed of cards drawn from one template
 * is the case it exists for: each card that scrolls on screen would otherwise decode, link and
 * build its layout tree again, although only its named values differ. Each [RcComposePlayer] still
 * builds its own player state from its own named values, so nothing one card does (a value, an
 * action, an animation, a touch) reaches another.
 *
 * Construction does all the work, so a host can build one off the main thread ahead of the first
 * frame that needs it. The derived structures are immutable, with two exceptions that stay per
 * player: a bitmap the document allocates to draw into (`ENCODING_EMPTY`) is created fresh for each
 * player, and the inline fonts' instance cache is shared on purpose, because Compose keys its text
 * layout cache on font identity. That cache is filled during composition, so share a prepared
 * document between players on one composition thread.
 */
public class RcPreparedDocument(public val document: RcDocument) {
  internal val linked: RcLinkedDocument = RcDocumentLinker.link(document)
  internal val layout: RcLayoutNode.Root? = RcLayoutTree.build(linked)
  internal val touchExpressions: Map<Int?, List<RcTouchExpression>> =
    rcTouchExpressions(linked.operations)
  internal val declaresFloatAnimation: Boolean =
    document.operations.filterIsInstance<RcFloatExpression>().any { it.animation != null }
  internal val fonts: RcDocumentFonts = decodeInlineFonts(document)

  /**
   * What each player's [RcPlayerState] starts from, scanned once: a card's state copies it rather
   * than walking the document's operations again (see [RcPlayerStateSeed]).
   */
  public val stateSeed: RcPlayerStateSeed = RcPlayerStateSeed(document)
  /** The URL and file images the document loads through the host, found once. */
  internal val externalBitmaps: List<RcBitmapData> = rcExternalBitmaps(document, linked)
  /** The document's component offscreen buffers by bitmap id, found once. */
  internal val componentOffscreenBuffers: Map<Int, RcBitmapData> =
    rcComponentOffscreenBuffers(document)
  /** Whether a player needs [RcComponentCaptures]: false for almost every document. */
  internal val capturesComponents: Boolean =
    componentOffscreenBuffers.isNotEmpty() ||
      document.operations.any {
        it is RcDrawToBitmap && it.mode and RcDrawToBitmap.MODE_COMPONENT_ID != 0
      }
  /**
   * Whether a player's first layout settles its `ComponentValue`s by composing throwaway passes
   * first ([RcSettledLayout]). False when the document reads none of its own geometry, or reads it
   * only at paint time (a rounded clip's corners), where a size published from the measure pass is
   * enough: see [rcGeometryReachesLayout].
   */
  internal val settlesFirstLayout: Boolean = rcGeometryReachesLayout(document.operations)

  /**
   * Whether the document can change its layout on its own, and so needs the lookahead pass behind
   * its bounds animations from its first frame; otherwise a player defers that pass until a host
   * value changes or someone touches the document. See [rcChangesLayoutOnItsOwn].
   */
  internal val changesLayoutOnItsOwn: Boolean =
    rcChangesLayoutOnItsOwn(document.operations) || touchExpressions.isNotEmpty()
  private val emptyBitmaps: List<RcBitmapData> =
    document.operations.filterIsInstance<RcBitmapData>().filter {
      it.encoding == RcBitmapData.ENCODING_EMPTY
    }
  // Only the decoded images are shared; a blank canvas is allocated per player, never here.
  private val decodedImages: Map<Int, ImageBitmap> =
    decodeInlineImages(
      if (emptyBitmaps.isEmpty()) document
      else RcDocument(document.header, document.operations.filter { it !in emptyBitmaps }),
    )

  /**
   * One player's inline images: the decoded ones shared, and a fresh blank bitmap for each one the
   * document draws into, so two players never draw into the same pixels.
   */
  /** The images every player shares, without the canvases each gets fresh. */
  internal val sharedImageCount: Int
    get() = decodedImages.size

  internal fun inlineImagesForPlayer(): MutableMap<Int, ImageBitmap> =
    decodedImages.toMutableMap().also { images ->
      emptyBitmaps.forEach { images[it.imageId] = ImageBitmap(it.width, it.height) }
    }

  public companion object {
    /**
     * Decodes [bytes] and prepares the result. A document that uses player extensions is decoded
     * with their wire half (`RcDocumentCodec.decode(bytes, extensions.wire)`) and passed to the
     * constructor instead.
     */
    public fun decode(bytes: ByteArray): RcPreparedDocument =
      RcPreparedDocument(decodeCmpDocument(bytes))
  }
}

package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument

/**
 * The distinct URLs this document's bitmaps load from, in document order.
 *
 * Only top-level operations: a bitmap declared inside a macro body is left alone by
 * [embedBitmapUrls] too, so listing it here would promise an embed that does not happen.
 */
public val RcDocument.bitmapUrls: List<String>
  get() =
    operations
      .filterIsInstance<RcBitmapData>()
      .filter { it.encoding == RcBitmapData.ENCODING_URL }
      .map { it.data.decodeToString() }
      .distinct()

/**
 * This document with each URL bitmap whose URL [fetch] returns bytes for replaced by those bytes,
 * embedded inline.
 *
 * `inline` so [fetch] may suspend when the caller can — fetching is the caller's business, with
 * whatever HTTP client and cache it already has. A URL [fetch] returns null for stays a URL. Each
 * URL is fetched once however many bitmaps share it.
 *
 * See the `Map` overload for what the embedded operation looks like.
 */
public inline fun RcDocument.embedBitmapUrls(fetch: (url: String) -> ByteArray?): RcDocument {
  val fetched = LinkedHashMap<String, ByteArray>()
  for (url in bitmapUrls) fetch(url)?.let { fetched[url] = it }
  return embedBitmapUrls(fetched)
}

/**
 * This document with each URL bitmap whose URL is a key of [bitmaps] embedded inline.
 *
 * The bytes are an encoded image — PNG, or anything else the target players' image decoders read —
 * and are embedded as [RcBitmapData.TYPE_PNG_8888], the type AndroidX's writer gives an encoded
 * bitmap and the one every player hands to its platform decoder. The bitmap keeps its id, so every
 * draw and image layout that names it now draws the embedded copy. The size comes from the PNG
 * header when there is one, since that is what will actually be decoded; otherwise the size the
 * document declared for the URL is kept.
 */
public fun RcDocument.embedBitmapUrls(bitmaps: Map<String, ByteArray>): RcDocument {
  if (bitmaps.isEmpty()) return this
  var changed = false
  val rewritten = operations.map { operation ->
    if (operation !is RcBitmapData || operation.encoding != RcBitmapData.ENCODING_URL) {
      return@map operation
    }
    val bytes = bitmaps[operation.data.decodeToString()] ?: return@map operation
    changed = true
    val (width, height) =
      pngSize(bytes)?.takeIf { (w, h) -> w in 1..0xffff && h in 1..0xffff }
        ?: (operation.width to operation.height)
    RcBitmapData(
      imageId = operation.imageId,
      width = width,
      height = height,
      type = RcBitmapData.TYPE_PNG_8888,
      encoding = RcBitmapData.ENCODING_INLINE,
      data = bytes,
    )
  }
  return if (changed) copy(operations = rewritten) else this
}

/** Width and height from a PNG's IHDR chunk, or null if [data] does not start with one. */
private fun pngSize(data: ByteArray): Pair<Int, Int>? {
  if (data.size < 24) return null
  if (PNG_SIGNATURE.indices.any { data[it] != PNG_SIGNATURE[it] }) return null
  fun int(offset: Int): Int =
    (0 until 4).fold(0) { value, i -> (value shl 8) or (data[offset + i].toInt() and 0xff) }
  return int(16) to int(20)
}

private val PNG_SIGNATURE: ByteArray =
  byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d) +
    "IHDR".encodeToByteArray()

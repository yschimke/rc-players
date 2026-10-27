package ee.schimke.composeai.rcwidget

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec

private const val FIRST_ID = 42
private const val ID_MASK = 0x003fffff

/**
 * [count] ids [document] doesn't use, for operations a rewrite adds. Every 4-byte window of its
 * encoding is read as one, which over-counts (any value, not only ids) but never misses one.
 */
internal fun freshIds(document: RcDocument, count: Int): List<Int> {
  val bytes = RcDocumentCodec.encode(document)
  val used = HashSet<Int>()
  for (i in 0..bytes.size - 4) {
    val word =
      (bytes[i].toInt() and 0xff shl 24) or
        (bytes[i + 1].toInt() and 0xff shl 16) or
        (bytes[i + 2].toInt() and 0xff shl 8) or
        (bytes[i + 3].toInt() and 0xff)
    used += word
    used += word and ID_MASK
  }
  return generateSequence(FIRST_ID) { it + 1 }.filter { it !in used }.take(count).toList()
}

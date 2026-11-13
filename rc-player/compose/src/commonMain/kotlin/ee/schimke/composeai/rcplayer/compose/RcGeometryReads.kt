package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcWireWriter

/**
 * Whether a document's first layout has to settle ([RcSettledLayout]): whether a size it reads of
 * its own components (`ComponentValue`) can reach anything composition or measurement uses.
 *
 * Settling composes and measures the whole tree up to [MAX_SETTLE_PASSES] extra times before the
 * kept tree, and disposes those compositions on the next frame. In a feed that is most of what a
 * card costs, so it is worth knowing when it buys nothing. The common case is the creation
 * library's own: every component with a circle or a rounded clip reads its width and height to size
 * the corners, and a clip, a background or a border reads its values at paint time, from the state,
 * after the component has been measured. For those a size published from the kept tree's measure
 * pass is already in place when the frame is drawn, so the first frame is the same.
 *
 * The answer is conservative. A geometry value is followed through the float expressions that read
 * it; every other operation that might name one of those ids, by [mentionedIds], counts as a layout
 * read unless it is one of the paint-time readers above. A wrong "settles" costs only the time it
 * always cost; a wrong "doesn't" would draw a first frame against zero sizes.
 */
internal fun rcGeometryReachesLayout(operations: List<RcOperation>): Boolean {
  val values = operations.filterIsInstance<RcComponentValue>()
  if (values.isEmpty()) return false
  val geometry = values.mapTo(mutableSetOf()) { it.valueId }
  val expressions = operations.filterIsInstance<RcFloatExpression>()
  // An expression over a geometry value is a geometry value too. Each pass adds at least one id to
  // a finite set, so this ends.
  var growing = true
  while (growing) {
    growing = false
    for (expression in expressions) {
      if (expression.id in geometry) continue
      if (expression.expression.any { word -> word.referencedId?.let { it in geometry } == true }) {
        geometry += expression.id
        growing = true
      }
    }
  }
  for (operation in operations) {
    when (operation) {
      is RcComponentValue -> continue
      is RcFloatExpression -> if (operation.id in geometry) continue
      // Read in the layer and draw phases (see `applyPaintDecorator`), after measure.
      is RcRoundedClipRectModifier,
      is RcBackgroundModifier,
      is RcBorderModifier -> continue
      else -> Unit
    }
    val mentioned = operation.mentionedIds() ?: return true
    if (mentioned.any { it in geometry }) return true
  }
  return false
}

/**
 * Every id this operation's encoding could name: the 2-, 3- and 4-byte big-endian value at every
 * offset, and the id every 4-byte NaN box carries. Wider than the ids it really reads, which only
 * makes [rcGeometryReachesLayout] more cautious; null when the operation does not encode. A text's
 * bytes name nothing, so a text reports only its own id.
 */
private fun RcOperation.mentionedIds(): Set<Int>? {
  if (this is RcTextData) return setOf(id)
  val bytes =
    runCatching { RcWireWriter().also { RcDocumentCodec.encodeOperation(it, this) }.toByteArray() }
      .getOrNull() ?: return null
  fun at(offset: Int, width: Int): Int {
    var bits = 0
    for (i in 0 until width) bits = (bits shl 8) or (bytes[offset + i].toInt() and 0xff)
    return bits
  }
  return buildSet {
    for (offset in bytes.indices) {
      if (offset + 2 <= bytes.size) add(at(offset, 2))
      if (offset + 3 <= bytes.size) add(at(offset, 3))
      if (offset + 4 <= bytes.size) {
        val word = at(offset, 4)
        add(word)
        RcFloatWord(word).referencedId?.let(::add)
      }
    }
  }
}

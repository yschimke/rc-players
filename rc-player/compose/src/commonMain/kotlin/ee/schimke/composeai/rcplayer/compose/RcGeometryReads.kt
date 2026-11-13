package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcImpulseStart
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLoopOperation
import ee.schimke.composeai.rcplayer.protocol.RcMarqueeModifier
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcParticleDefine
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcScrollModifier
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTimeAttribute
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatExpressionChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerExpressionChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueStringChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcWakeIn
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
      // An image or a font is its own encoded payload, which names no id; scanning it would only
      // allocate a window per byte of a possibly large asset.
      is RcBitmapData,
      is RcFontData -> continue
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

/**
 * Whether a document can change its layout without a host value changing: it animates, reads the
 * clock, scrolls, takes input, runs touch expressions, impulses, particles or a marquee, switches
 * state layouts, or has actions that write its own state. Such a document keeps the lookahead pass
 * its bounds animations need from its first frame. Any other document's layout changes only when
 * its host hands it a value or someone touches it, and a player defers that pass until then (see
 * `RcComposePlayerResolved`). Conservative: an operation it cannot account for counts as moving.
 */
internal fun rcChangesLayoutOnItsOwn(operations: List<RcOperation>): Boolean {
  for (operation in operations) {
    when (operation) {
      is RcImpulseStart,
      is RcParticleDefine,
      is RcTimeAttribute,
      is RcWakeIn,
      is RcScrollModifier,
      is RcMarqueeModifier,
      is RcStateLayout,
      is RcTouchExpression,
      // A host-rendered component (a text field, say) holds state of its own, which rebuilding the
      // tree when the pass switches on would lose: focus, selection, an IME composition.
      is RcCustomLayout,
      is RcLoopOperation,
      // A tap that writes the document's own state changes it there and then; one that only tells
      // the host (Livery's actions) changes it through a host value, which the player sees coming.
      is RcValueFloatChangeAction,
      is RcValueFloatExpressionChangeAction,
      is RcValueIntegerChangeAction,
      is RcValueIntegerExpressionChangeAction,
      is RcValueStringChangeAction -> return true
      is RcFloatExpression -> if (operation.animation != null) return true
      else -> Unit
    }
  }
  // A clock read that can reach layout. A moving value is followed through the float expressions
  // that read it; a reader that only paints (a graphics layer, a background, a border, a clip, all
  // applied after measure) moves pixels, never bounds, so it needs no lookahead pass. A pulse on a
  // "live" badge's layer is the common case. Anything else naming a moving id counts.
  val moving = RcSystemVariables.MOVING.toMutableSet()
  val expressions = operations.filterIsInstance<RcFloatExpression>()
  var growing = true
  while (growing) {
    growing = false
    for (expression in expressions) {
      if (expression.id in moving) continue
      if (expression.expression.any { word -> word.referencedId?.let { it in moving } == true }) {
        moving += expression.id
        growing = true
      }
    }
  }
  return operations.any { operation ->
    when (operation) {
      is RcTextData,
      is RcBitmapData,
      is RcFontData,
      is RcGraphicsLayerModifier,
      is RcRoundedClipRectModifier,
      is RcBackgroundModifier,
      is RcBorderModifier -> false
      is RcFloatExpression -> false // followed above; what reads it is checked in its place
      // An integer expression names ids as marked values, not NaN boxes: a clock (or an expression
      // over one) read there feeds whatever reads its result.
      is RcIntegerExpression ->
        operation.values.indices.any { index ->
          val value = operation.values[index]
          operation.isMarked(index) && value < RcIntegerExpression.OFFSET && value in moving
        }
      else -> operation.referencedFloatIds()?.any { it in moving } != false
    }
  }
}

/**
 * The ids the NaN-boxed float words of this operation's encoding name; null if it does not encode.
 */
private fun RcOperation.referencedFloatIds(): Set<Int>? {
  val bytes =
    runCatching { RcWireWriter().also { RcDocumentCodec.encodeOperation(it, this) }.toByteArray() }
      .getOrNull() ?: return null
  return buildSet {
    for (offset in 0..bytes.size - 4) {
      var bits = 0
      for (i in 0 until 4) bits = (bits shl 8) or (bytes[offset + i].toInt() and 0xff)
      RcFloatWord(bits).referencedId?.let(::add)
    }
  }
}

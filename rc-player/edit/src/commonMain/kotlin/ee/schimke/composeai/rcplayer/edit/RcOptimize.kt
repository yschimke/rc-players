package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontData
import ee.schimke.composeai.rcplayer.protocol.RcBooleanConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorAttribute
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcDataMapLookup
import ee.schimke.composeai.rcplayer.protocol.RcDebugMessage
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIdLookup
import ee.schimke.composeai.rcplayer.protocol.RcIdMap
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcMatrixConstant
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcRemark
import ee.schimke.composeai.rcplayer.protocol.RcShaderData
import ee.schimke.composeai.rcplayer.protocol.RcSoundData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextLength
import ee.schimke.composeai.rcplayer.protocol.RcTextLookup
import ee.schimke.composeai.rcplayer.protocol.RcTextLookupInt
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcTextStyle
import ee.schimke.composeai.rcplayer.protocol.RcTextSubtext
import ee.schimke.composeai.rcplayer.protocol.RcTextTransform
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatExpressionChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerExpressionChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcWireWriter

/**
 * This document made smaller without changing what it shows, by every pass below in order:
 * [stripDebugOperations], [foldConstantExpressions], [removeDuplicateDefinitions] and
 * [removeUnreadDefinitions]. Each is safe on its own and each leaves more for the next.
 *
 * Every pass is conservative in the same way: it changes an operation only when it can show nothing
 * else depends on what it changes, and treats anything it cannot account for as depended on.
 */
public fun RcDocument.optimize(): RcDocument =
  stripDebugOperations()
    .foldConstantExpressions()
    .removeDuplicateDefinitions()
    .removeUnreadDefinitions()

/** This document without remarks and debug messages, which draw nothing and set nothing. */
public fun RcDocument.stripDebugOperations(): RcDocument {
  val rewritten = operations.filterNot { it is RcRemark || it is RcDebugMessage }
  return if (rewritten.size == operations.size) this else copy(operations = rewritten)
}

/**
 * This document with every value producer whose value can never change replaced by a constant
 * holding that value, evaluated exactly as the player evaluates it: integer and float expressions,
 * colour expressions and the channels read from a colour, and merged, formatted and looked-up text.
 *
 * A producer folds when it always runs, writes an id nothing else writes, and reads only constants
 * — directly or through other foldable producers. Float expressions must also have no animation and
 * use only deterministic operators (no random numbers, registers, loop variables or arrays). An
 * expression an expression-change action evaluates stays an expression: the action looks it up by
 * id.
 *
 * This is what makes a widget whose host values were baked in with [hardcodeNamedValues] cheap to
 * play: the colours, labels and sizes derived from those values become constants the player loads
 * once rather than recomputes every frame.
 */
public fun RcDocument.foldConstantExpressions(): RcDocument {
  if (operations.none { it.isFoldableProducer() }) return this
  val unconditional = unconditionalOperations(operations) ?: return this
  val values = RcValueAnalysis(operations)
  val evaluatedByActions =
    operations.mapNotNullTo(mutableSetOf()) {
      when (it) {
        is RcValueFloatExpressionChangeAction -> it.expressionId
        is RcValueIntegerExpressionChangeAction -> it.expressionId.toInt()
        else -> null
      }
    }
  var changed = false
  val rewritten = operations.mapIndexed { index, operation ->
    if (!unconditional[index]) return@mapIndexed operation
    val folded =
      when (operation) {
        is RcIntegerExpression ->
          operation.outId
            .takeUnless { it in evaluatedByActions }
            ?.let { values.integer(it, at = index + 1) }
            ?.let { RcIntegerConstant(operation.outId, it) }
        is RcFloatExpression ->
          operation.id
            .takeUnless { it in evaluatedByActions || operation.animation != null }
            ?.let { values.float(it, at = index + 1) }
            ?.let { RcFloatConstant(operation.id, it) }
        is RcColorAttribute ->
          values.float(operation.outId, at = index + 1)?.let {
            RcFloatConstant(operation.outId, it)
          }
        is RcColorExpression ->
          values.color(operation.outId, at = index + 1)?.let {
            RcColorConstant(operation.outId, it)
          }
        is RcTextMerge ->
          values.text(operation.outId, at = index + 1)?.let { RcTextData(operation.outId, it) }
        is RcTextFromFloat ->
          values.text(operation.outId, at = index + 1)?.let { RcTextData(operation.outId, it) }
        is RcTextLookupInt ->
          values.text(operation.outId, at = index + 1)?.let { RcTextData(operation.outId, it) }
        else -> null
      }
    folded?.also { changed = true } ?: operation
  }
  return if (changed) copy(operations = rewritten) else this
}

/**
 * This document with repeated definitions of an id dropped where every definition of that id is the
 * same operation, so the first already gives the id its only value.
 *
 * Only for the values the player loads when the document opens, and only where every copy always
 * runs: an id defined two different ways keeps every definition, since which one wins depends on
 * where each sits.
 */
public fun RcDocument.removeDuplicateDefinitions(): RcDocument {
  val unconditional = unconditionalOperations(operations) ?: return this
  val writers = mutableMapOf<Int, MutableList<Int>>()
  operations.forEachIndexed { index, operation ->
    operation.definedIds()?.forEach { writers.getOrPut(it) { mutableListOf() } += index }
  }
  val drop = mutableSetOf<Int>()
  writers.values.forEach { indices ->
    if (indices.size < 2) return@forEach
    val first = operations[indices.first()]
    val sameEverywhere =
      first.isOpenTimeData() && indices.all { unconditional[it] && operations[it] == first }
    if (sameEverywhere) drop += indices.drop(1)
  }
  return if (drop.isEmpty()) this
  else copy(operations = operations.filterIndexed { index, _ -> index !in drop })
}

/**
 * This document without the definitions nothing reads: data, resources and expressions whose id no
 * other operation — nor the header — mentions. Repeated until nothing more goes, since removing a
 * reader can leave what it read unread.
 *
 * "Mentions" is found by scanning every operation's encoding, at every offset, for the id as a 2-,
 * 3- or 4-byte value or a NaN-boxed reference — wide enough to catch the ids the wire format packs
 * beside flags as well as whole-word references. It over-reports (a literal or flag that happens to
 * equal the id counts), which only ever keeps a definition. Named variables mention their id, so
 * anything a host can bind stays. Ids below 42, which the player reserves for its own variables,
 * are never removed.
 */
public fun RcDocument.removeUnreadDefinitions(): RcDocument {
  // An operation that cannot be encoded cannot be scanned, so nothing can be shown unread.
  val words = operations.map { it.mentionedValues() ?: return this }
  val headerWords = header.mentionedValues() ?: return this
  // How many operations, the header included, mention each word.
  val mentions = mutableMapOf<Int, Int>()
  fun count(set: Set<Int>, delta: Int) = set.forEach { mentions[it] = (mentions[it] ?: 0) + delta }
  count(headerWords, 1)
  words.forEach { count(it, 1) }

  val removed = BooleanArray(operations.size)
  var progress = true
  while (progress) {
    progress = false
    operations.forEachIndexed { index, operation ->
      if (removed[index] || !operation.isRemovableWhenUnread()) return@forEachIndexed
      val ids = operation.definedIds() ?: return@forEachIndexed
      val unread = ids.all { id ->
        id >= FIRST_DOCUMENT_ID && (mentions[id] ?: 0) - (if (id in words[index]) 1 else 0) == 0
      }
      if (unread) {
        removed[index] = true
        count(words[index], -1)
        progress = true
      }
    }
  }
  return if (removed.none { it }) this
  else copy(operations = operations.filterIndexed { index, _ -> !removed[index] })
}

/** The encoded size of this document and of each kind of operation in it, largest first. */
public fun RcDocument.sizeReport(): RcSizeReport {
  val byOperation = mutableMapOf<String, Int>()
  (listOf(header) + operations).forEach { operation ->
    val size = encodedSize(operation)
    val name = operationName(operation.opcode)
    byOperation[name] = (byOperation[name] ?: 0) + size
  }
  return RcSizeReport(
    totalBytes = byOperation.values.sum(),
    byOperation = byOperation.entries.sortedByDescending { it.value }.associate { it.toPair() },
  )
}

/** Bytes per document and per kind of operation, as [sizeReport] measures them. */
public data class RcSizeReport(
  val totalBytes: Int,
  /** Bytes per operation name (`DATA_BITMAP`, `DRAW_TEXT_RUN`, …), largest first. */
  val byOperation: Map<String, Int>,
)

private fun RcOperation.isFoldableProducer(): Boolean =
  this is RcIntegerExpression ||
    this is RcFloatExpression ||
    this is RcColorAttribute ||
    this is RcColorExpression ||
    this is RcTextMerge ||
    this is RcTextFromFloat ||
    this is RcTextLookupInt

/** The values the player loads when a document opens, wherever in it they sit. */
private fun RcOperation.isOpenTimeData(): Boolean =
  this is RcTextData ||
    this is RcFloatConstant ||
    this is RcColorConstant ||
    this is RcIntegerConstant ||
    this is RcBooleanConstant ||
    this is RcLongConstant ||
    this is RcPathData ||
    this is RcMatrixConstant ||
    this is RcIdList ||
    this is RcFloatList ||
    this is RcIdMap ||
    this is RcBitmapData

/**
 * Definitions whose only effect is the value they give their id: open-time data, resources, and the
 * expressions and text and colour producers the player evaluates in content state. Not touch
 * expressions, component values, draw-time producers or anything with an effect of its own.
 */
private fun RcOperation.isRemovableWhenUnread(): Boolean =
  isOpenTimeData() ||
    this is RcFontData ||
    this is RcBitmapFontData ||
    this is RcShaderData ||
    this is RcSoundData ||
    this is RcTextStyle ||
    this is RcFloatExpression ||
    this is RcIntegerExpression ||
    this is RcColorExpression ||
    this is RcColorTheme ||
    this is RcColorAttribute ||
    this is RcDataMapLookup ||
    this is RcIdLookup ||
    this is RcTextFromFloat ||
    this is RcTextLength ||
    this is RcTextLookup ||
    this is RcTextLookupInt ||
    this is RcTextMerge ||
    this is RcTextSubtext ||
    this is RcTextTransform

private fun encoded(operation: RcOperation): ByteArray? = runCatching {
  RcWireWriter().also { RcDocumentCodec.encodeOperation(it, operation) }.toByteArray()
}
  .getOrNull()

private fun encodedSize(operation: RcOperation): Int = encoded(operation)?.size ?: 0

/** The first id the player leaves to documents; everything below is its own variables. */
private const val FIRST_DOCUMENT_ID = 42

package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcColorAttribute
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextLookupInt
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWireWriter
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcFloatExpressionEvaluator
import ee.schimke.composeai.rcplayer.runtime.RcIntegerExpressionEvaluator
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState

/**
 * Which integer, float, colour and text ids hold a value that can never change, and what it is.
 *
 * Conservative by construction, in the same way as the state-layout collapse: rather than listing
 * every way the player can write an id — actions, scroll feedback, touch, loops, host bindings, the
 * integer and float views of one id being kept in step — an id is constant only when **every**
 * operation that mentions it is accounted for:
 * - each operation that defines it is a plain constant, or an expression or producer over
 *   constants, that always runs (see [unconditionalOperations]); and
 * - each other operation that mentions it is on [READ_ONLY_OPCODES], operations known to only read
 *   the ids they mention.
 *
 * Anything else that mentions the id — an operation this does not know, or knows can write — leaves
 * it variable. "Mentions" is the same over-reporting byte scan [mentionedValues] gives.
 *
 * A value is also only constant *at* a position: every write must come earlier in the document than
 * the operation reading it, as the player evaluates in wire order.
 *
 * Colour and text producers — `ColorExpression`, `ColorAttribute`, `TextMerge`, `TextFromFloat`,
 * `TextLookupInt` — are evaluated by the player itself: an [RcPlayerState] given just the producer
 * and its constant inputs, so a folded value is exactly the one the player would have computed.
 */
internal class RcValueAnalysis(private val operations: List<RcOperation>) {
  private val unconditional = unconditionalOperations(operations)
  private val mentions: Map<Int, List<Int>>? = buildMentions()
  private val integers = mutableMapOf<Int, Pair<Int, Int>?>()
  private val floats = mutableMapOf<Int, Pair<RcFloatWord, Int>?>()
  private val colors = mutableMapOf<Int, Pair<Int, Int>?>()
  private val texts = mutableMapOf<Int, Pair<String, Int>?>()
  private val resolving = mutableSetOf<Int>()

  /** The value integer [id] always holds by the time the operation at [at] reads it, or null. */
  fun integer(id: Int, at: Int): Int? =
    integerWithLastWrite(id)?.takeIf { (_, last) -> last < at }?.first

  /** The value float [id] always holds by the time the operation at [at] reads it, or null. */
  fun float(id: Int, at: Int): RcFloatWord? =
    floatWithLastWrite(id)?.takeIf { (_, last) -> last < at }?.first

  /** The colour [id] always holds by the time the operation at [at] reads it, or null. */
  fun color(id: Int, at: Int): Int? =
    colorWithLastWrite(id)?.takeIf { (_, last) -> last < at }?.first

  /** The text [id] always holds by the time the operation at [at] reads it, or null. */
  fun text(id: Int, at: Int): String? =
    textWithLastWrite(id)?.takeIf { (_, last) -> last < at }?.first

  private fun integerWithLastWrite(id: Int): Pair<Int, Int>? =
    memoized(integers, id) {
      val writers = pinnedWriters(id) { it is RcIntegerConstant || it is RcIntegerExpression }
      when {
        writers == null -> null
        writers.all { operations[it] is RcIntegerConstant } ->
          writers
            .map { (operations[it] as RcIntegerConstant).value }
            .distinct()
            .singleOrNull()
            ?.let { it to writers.max() }
        else -> {
          val at = writers.singleOrNull() ?: return@memoized null
          val expression = operations[at] as? RcIntegerExpression ?: return@memoized null
          if (RcIntegerExpressionEvaluator.validationError(expression) != null) return@memoized null
          evaluating {
            RcIntegerExpressionEvaluator.evaluate(expression) { input ->
              integer(input, at) ?: throw NotConstant
            }
          }
            ?.let { it to at }
        }
      }
    }

  private fun floatWithLastWrite(id: Int): Pair<RcFloatWord, Int>? =
    memoized(floats, id) {
      val writers =
        pinnedWriters(id) {
          it is RcFloatConstant || it is RcFloatExpression || it is RcColorAttribute
        }
      when {
        writers == null -> null
        writers.all { operations[it] is RcFloatConstant } ->
          writers
            .map { (operations[it] as RcFloatConstant).value.bits }
            .distinct()
            .singleOrNull()
            ?.let(::RcFloatWord)
            ?.takeUnless { it.isNaNEncoded }
            ?.let { it to writers.max() }
        else -> {
          val at = writers.singleOrNull() ?: return@memoized null
          when (val producer = operations[at]) {
            is RcFloatExpression -> {
              if (producer.animation != null || !producer.expression.all { it.isFoldable() }) {
                return@memoized null
              }
              evaluating {
                val value =
                  RcFloatExpressionEvaluator().evaluate(producer.expression) { word ->
                    word.referencedId?.let { float(it, at) }?.value ?: throw NotConstant
                  }
                RcFloatWord.literal(value).takeIf { value.isFinite() }
              }
            }
            is RcColorAttribute -> {
              val color = color(producer.colorId, at) ?: return@memoized null
              play(listOf(RcColorConstant(producer.colorId, color)), producer) { state ->
                RcFloatWord.literal(state.resolve(reference(id)))
              }
            }
            else -> null
          }?.let { it to at }
        }
      }
    }

  private fun colorWithLastWrite(id: Int): Pair<Int, Int>? =
    memoized(colors, id) {
      val writers = pinnedWriters(id) { it is RcColorConstant || it is RcColorExpression }
      when {
        writers == null -> null
        writers.all { operations[it] is RcColorConstant } ->
          writers
            .map { (operations[it] as RcColorConstant).argb }
            .distinct()
            .singleOrNull()
            ?.let { it to writers.max() }
        else -> {
          val at = writers.singleOrNull() ?: return@memoized null
          val expression = operations[at] as? RcColorExpression ?: return@memoized null
          val inputs = colorExpressionInputs(expression, at) ?: return@memoized null
          play(inputs, expression) { it.color(id) }?.let { it to at }
        }
      }
    }

  /** Constants standing for every id [expression] reads, or null when one is not constant. */
  private fun colorExpressionInputs(expression: RcColorExpression, at: Int): List<RcOperation>? {
    val inputs = mutableListOf<RcOperation>()
    fun colorInput(colorId: Int): Boolean =
      color(colorId, at)?.also { inputs += RcColorConstant(colorId, it) } != null
    fun floatInput(bits: Int): Boolean {
      val referenced = RcFloatWord(bits).referencedId ?: return true
      return float(referenced, at)?.also { inputs += RcFloatConstant(referenced, it) } != null
    }
    val mode = expression.mode
    val complete =
      when (mode) {
        in RcColorExpression.COLOR_COLOR_INTERPOLATE..RcColorExpression.ID_ID_INTERPOLATE ->
          (mode and 1 == 0 || colorInput(expression.first)) &&
            (mode and 2 == 0 || colorInput(expression.second)) &&
            floatInput(expression.third)
        RcColorExpression.HSV_MODE,
        RcColorExpression.ARGB_MODE ->
          floatInput(expression.first) &&
            floatInput(expression.second) &&
            floatInput(expression.third)
        RcColorExpression.IDARGB_MODE ->
          floatInput(reference(expression.modeAndAlpha ushr 16).bits) &&
            floatInput(expression.first) &&
            floatInput(expression.second) &&
            floatInput(expression.third)
        else -> false
      }
    return inputs.takeIf { complete }
  }

  private fun textWithLastWrite(id: Int): Pair<String, Int>? =
    memoized(texts, id) {
      val writers =
        pinnedWriters(id) {
          it is RcTextData || it is RcTextMerge || it is RcTextFromFloat || it is RcTextLookupInt
        }
      when {
        writers == null -> null
        writers.all { operations[it] is RcTextData } ->
          writers
            .map { (operations[it] as RcTextData).text }
            .distinct()
            .singleOrNull()
            ?.let { it to writers.max() }
        else -> {
          val at = writers.singleOrNull() ?: return@memoized null
          val producer = operations[at]
          val inputs: List<RcOperation> =
            when (producer) {
              is RcTextMerge ->
                listOf(producer.leftId, producer.rightId).map { input ->
                  RcTextData(input, text(input, at) ?: return@memoized null)
                }
              is RcTextFromFloat -> {
                val referenced = producer.value.referencedId
                if (referenced == null) emptyList()
                else
                  listOf(RcFloatConstant(referenced, float(referenced, at) ?: return@memoized null))
              }
              is RcTextLookupInt -> {
                val list = idList(producer.listId, at) ?: return@memoized null
                val index = integer(producer.indexId, at) ?: return@memoized null
                val selected = list.ids.getOrNull(index) ?: return@memoized null
                listOf(
                  list,
                  RcIntegerConstant(producer.indexId, index),
                  RcTextData(selected, text(selected, at) ?: return@memoized null),
                )
              }
              else -> return@memoized null
            }
          play(inputs, producer) { it.text(id) }?.let { it to at }
        }
      }
    }

  /** The id list [id] always is by [at]: one definition, and only ever read. */
  private fun idList(id: Int, at: Int): RcIdList? {
    val writers = pinnedWriters(id) { it is RcIdList } ?: return null
    val lists = writers.map { operations[it] as RcIdList }.distinct()
    return lists.singleOrNull()?.takeIf { writers.max() < at }
  }

  /**
   * [read] from a player that has run [producer] over [inputs] and nothing else, or null when it
   * cannot: the player's own evaluation, so a folded value is the value it would have computed.
   */
  private fun <T : Any> play(
    inputs: List<RcOperation>,
    producer: RcOperation,
    read: (RcPlayerState) -> T?,
  ): T? = evaluating {
    val document = RcDocument(RcHeader(RcVersion(1, 1, 0)), inputs + producer)
    val state = RcPlayerState(document)
    state.applyContentStateOperations(RcDocumentLinker.link(document).operations)
    read(state)
  }

  private fun reference(id: Int): RcFloatWord = RcFloatWord(0x7fc00000 or id)

  /**
   * The indices of [id]'s writers when every one is [allowed] and always runs and every other
   * operation mentioning [id] only reads it; null otherwise.
   */
  private fun pinnedWriters(id: Int, allowed: (RcOperation) -> Boolean): List<Int>? {
    if (unconditional == null || mentions == null) return null
    if (id in RcSystemVariables.ALL || id < FIRST_DOCUMENT_ID) return null
    val writers = mutableListOf<Int>()
    for (index in mentions[id].orEmpty()) {
      val operation = operations[index]
      when {
        operation.definedIds()?.contains(id) == true -> {
          if (!allowed(operation) || !unconditional[index]) return null
          writers += index
        }
        operation.opcode !in READ_ONLY_OPCODES -> return null
      }
    }
    return writers.takeIf { it.isNotEmpty() }
  }

  private fun buildMentions(): Map<Int, List<Int>>? {
    val result = mutableMapOf<Int, MutableList<Int>>()
    operations.forEachIndexed { index, operation ->
      val words = operation.mentionedValues() ?: return null
      words.forEach { result.getOrPut(it) { mutableListOf() } += index }
    }
    return result
  }

  private inline fun <T> memoized(cache: MutableMap<Int, T?>, id: Int, compute: () -> T?): T? {
    if (id in cache) return cache[id]
    // A cycle reads its own value before it exists: not a constant.
    if (!resolving.add(id)) return null
    val value = compute()
    resolving -= id
    cache[id] = value
    return value
  }

  private inline fun <T> evaluating(block: () -> T): T? =
    try {
      block()
    } catch (_: NotConstant) {
      null
    } catch (_: RuntimeException) {
      // A malformed expression (stack underflow) is the player's to report, not this analysis's.
      null
    }

  /**
   * Whether a word of a float expression evaluates the same every time: a literal, a variable
   * reference, or an operator that is neither random, nor stateful (registers), nor reads loop
   * variables or arrays.
   */
  private fun RcFloatWord.isFoldable(): Boolean {
    if (!isNaNEncoded) return true
    val id = bits and NAN_PAYLOAD_MASK
    if (id and ID_REGION_MASK == ID_REGION_ARRAY) return false
    if (id <= OPERATOR_OFFSET || id > OPERATOR_OFFSET + 79) return true
    return (id - OPERATOR_OFFSET) !in NON_DETERMINISTIC_OPERATORS
  }

  private object NotConstant : RuntimeException()

  private companion object {
    const val FIRST_DOCUMENT_ID = 42
    const val OPERATOR_OFFSET = 0x310_000
    const val NAN_PAYLOAD_MASK = 0x7fffff
    const val ID_REGION_MASK = 0x700000
    const val ID_REGION_ARRAY = 0x200000

    /** Random (39, 40, 42), register (56–63), command (64–69) and loop-variable (70–72). */
    val NON_DETERMINISTIC_OPERATORS: Set<Int> = setOf(39, 40, 42) + (56..72)
  }
}

/**
 * Every value this operation's encoding could be naming an id with, if it encodes: the 2-, 3- and
 * 4-byte big-endian value at every offset, and the id every 4-byte NaN box carries.
 *
 * Not just whole words, because not every reference is one: `ColorExpression` packs its alpha id
 * into the top half of its mode word, and other operations pack ids beside flags the same way. The
 * narrower windows over-report far more — any small number counts — which only ever makes an
 * analysis built on this more cautious.
 */
internal fun RcOperation.mentionedValues(): Set<Int>? {
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
 * Operations known to only read the ids they mention (beyond any they define themselves): layout,
 * modifiers other than scroll, drawing, and the definitions and producers whose operands they read.
 * Deliberately absent: every action, scroll, touch, loops, particles, impulses, functions, macros,
 * named variables — anything that can write an id it mentions or hand it to a host.
 */
internal val READ_ONLY_OPCODES: Set<Int> =
  setOf(
    RcOpcodes.HEADER,
    RcOpcodes.CONTAINER_END,
    RcOpcodes.COMPONENT_START,
    RcOpcodes.LAYOUT_ROOT,
    RcOpcodes.LAYOUT_BOX,
    RcOpcodes.LAYOUT_CANVAS,
    RcOpcodes.LAYOUT_CANVAS_CONTENT,
    RcOpcodes.LAYOUT_COLLAPSIBLE_COLUMN,
    RcOpcodes.LAYOUT_COLLAPSIBLE_ROW,
    RcOpcodes.LAYOUT_COLUMN,
    RcOpcodes.LAYOUT_CONTENT,
    RcOpcodes.LAYOUT_FIT_BOX,
    RcOpcodes.LAYOUT_FLOW,
    RcOpcodes.LAYOUT_IMAGE,
    RcOpcodes.LAYOUT_ROW,
    RcOpcodes.LAYOUT_STATE,
    RcOpcodes.LAYOUT_TEXT,
    RcOpcodes.CORE_TEXT,
    RcOpcodes.TEXT_STYLE,
    RcOpcodes.ANIMATION_SPEC,
    RcOpcodes.ACCESSIBILITY_SEMANTICS,
    RcOpcodes.ROOT_CONTENT_DESCRIPTION,
    RcOpcodes.MODIFIER_ALIGN_BY,
    RcOpcodes.MODIFIER_BACKGROUND,
    RcOpcodes.MODIFIER_BORDER,
    RcOpcodes.MODIFIER_CLIP_RECT,
    RcOpcodes.MODIFIER_COLLAPSIBLE_PRIORITY,
    RcOpcodes.MODIFIER_DIMENSION_CONSTRAINTS,
    RcOpcodes.MODIFIER_DRAW_CONTENT,
    RcOpcodes.MODIFIER_GRAPHICS_LAYER,
    RcOpcodes.MODIFIER_HEIGHT,
    RcOpcodes.MODIFIER_HEIGHT_IN,
    RcOpcodes.MODIFIER_MARQUEE,
    RcOpcodes.MODIFIER_OFFSET,
    RcOpcodes.MODIFIER_PADDING,
    RcOpcodes.MODIFIER_RIPPLE,
    RcOpcodes.MODIFIER_ROUNDED_CLIP_RECT,
    RcOpcodes.MODIFIER_VISIBILITY,
    RcOpcodes.MODIFIER_WIDTH,
    RcOpcodes.MODIFIER_WIDTH_IN,
    RcOpcodes.MODIFIER_ZINDEX,
    RcOpcodes.CANVAS_OPERATIONS,
    RcOpcodes.PAINT_VALUES,
    RcOpcodes.CLIP_PATH,
    RcOpcodes.CLIP_RECT,
    RcOpcodes.MATRIX_SAVE,
    RcOpcodes.MATRIX_RESTORE,
    RcOpcodes.MATRIX_ROTATE,
    RcOpcodes.MATRIX_SCALE,
    RcOpcodes.MATRIX_SKEW,
    RcOpcodes.MATRIX_TRANSLATE,
    RcOpcodes.DRAW_ARC,
    RcOpcodes.DRAW_BITMAP,
    RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN,
    RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH,
    RcOpcodes.DRAW_BITMAP_INT,
    RcOpcodes.DRAW_BITMAP_SCALED,
    RcOpcodes.DRAW_BITMAP_TEXT_ANCHORED,
    RcOpcodes.DRAW_CIRCLE,
    RcOpcodes.DRAW_CONTENT,
    RcOpcodes.DRAW_LINE,
    RcOpcodes.DRAW_OVAL,
    RcOpcodes.DRAW_PATH,
    RcOpcodes.DRAW_RECT,
    RcOpcodes.DRAW_ROUND_RECT,
    RcOpcodes.DRAW_SECTOR,
    RcOpcodes.DRAW_TEXT_ANCHOR,
    RcOpcodes.DRAW_TEXT_ON_CIRCLE,
    RcOpcodes.DRAW_TEXT_ON_PATH,
    RcOpcodes.DRAW_TEXT_RUN,
    RcOpcodes.DRAW_TWEEN_PATH,
    RcOpcodes.CONDITIONAL_OPERATIONS,
    // Definitions and producers: they read their operands and write only their own id, which the
    // analysis checks separately.
    RcOpcodes.DATA_TEXT,
    RcOpcodes.DATA_FLOAT,
    RcOpcodes.DATA_INT,
    RcOpcodes.DATA_BOOLEAN,
    RcOpcodes.DATA_LONG,
    RcOpcodes.DATA_PATH,
    RcOpcodes.DATA_BITMAP,
    RcOpcodes.DATA_FONT,
    RcOpcodes.DATA_BITMAP_FONT,
    RcOpcodes.COLOR_CONSTANT,
    RcOpcodes.MATRIX_CONSTANT,
    RcOpcodes.ID_LIST,
    RcOpcodes.FLOAT_LIST,
    RcOpcodes.ID_MAP,
    RcOpcodes.ANIMATED_FLOAT,
    RcOpcodes.INTEGER_EXPRESSION,
    RcOpcodes.COLOR_EXPRESSIONS,
    RcOpcodes.COLOR_THEME,
    RcOpcodes.ATTRIBUTE_COLOR,
    RcOpcodes.DATA_MAP_LOOKUP,
    RcOpcodes.ID_LOOKUP,
    RcOpcodes.TEXT_FROM_FLOAT,
    RcOpcodes.TEXT_LENGTH,
    RcOpcodes.TEXT_LOOKUP,
    RcOpcodes.TEXT_LOOKUP_INT,
    RcOpcodes.TEXT_MERGE,
    RcOpcodes.TEXT_SUBTEXT,
    RcOpcodes.TEXT_TRANSFORM,
    RcOpcodes.TEXT_MEASURE,
    RcOpcodes.COMPONENT_VALUE,
  )

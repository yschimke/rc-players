package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcOperation

/**
 * A float the player computes, as the postfix words of a `FloatExpression`: a single word (a
 * literal or a variable) or an expression over them. Arithmetic on literals folds, in the same
 * single-precision steps the player would take, and multiplying by one or adding zero is dropped.
 */
internal class RcSymbolic private constructor(val words: List<RcFloatWord>) {
  /** The value, when this is a literal. */
  val literal: Float?
    get() = words.singleOrNull()?.takeUnless { it.isNaNEncoded }?.value

  operator fun plus(other: RcSymbolic): RcSymbolic =
    when {
      literal == 0f -> other
      other.literal == 0f -> this
      else -> fold(other, OPERATOR_ADD) { a, b -> a + b }
    }

  operator fun minus(other: RcSymbolic): RcSymbolic =
    if (other.literal == 0f) this else fold(other, OPERATOR_SUB) { a, b -> a - b }

  operator fun times(other: RcSymbolic): RcSymbolic =
    when {
      literal == 1f -> other
      other.literal == 1f -> this
      else -> fold(other, OPERATOR_MUL) { a, b -> a * b }
    }

  operator fun div(other: RcSymbolic): RcSymbolic =
    if (other.literal == 1f) this else fold(other, OPERATOR_DIV) { a, b -> a / b }

  private inline fun fold(
    other: RcSymbolic,
    operator: Int,
    evaluate: (Float, Float) -> Float,
  ): RcSymbolic {
    val left = literal
    val right = other.literal
    return if (left != null && right != null) of(evaluate(left, right))
    else RcSymbolic(words + other.words + operatorWord(operator))
  }

  /**
   * This value as one word: itself when it is one already, otherwise a reference to a new
   * `FloatExpression` computing it, appended to [definitions].
   */
  fun asWord(ids: RcIdAllocator, definitions: MutableList<RcOperation>): RcFloatWord =
    words.singleOrNull()
      ?: ids.next().let { id ->
        definitions += RcFloatExpression(id, words, animation = null)
        reference(id)
      }

  companion object {
    fun of(word: RcFloatWord): RcSymbolic = RcSymbolic(listOf(word))

    fun of(value: Float): RcSymbolic = of(RcFloatWord.literal(value))

    fun reference(id: Int): RcFloatWord = RcFloatWord(NAN_PREFIX or id)

    private fun operatorWord(operator: Int): RcFloatWord =
      RcFloatWord(NAN_PREFIX or (FLOAT_OPERATOR_OFFSET + operator))

    private const val NAN_PREFIX = 0xff800000.toInt()
    private const val FLOAT_OPERATOR_OFFSET = 0x310000
    private const val OPERATOR_ADD = 1
    private const val OPERATOR_SUB = 2
    private const val OPERATOR_MUL = 3
    private const val OPERATOR_DIV = 4
  }
}

/**
 * A 2D translate-and-scale, `translate(translateX, translateY) · scale(scaleX, scaleY)`: the shape
 * AndroidX's matrix evaluator keeps a matrix in while an expression only translates and scales.
 */
internal class RcAffine(
  val translateX: RcSymbolic,
  val translateY: RcSymbolic,
  val scaleX: RcSymbolic,
  val scaleY: RcSymbolic,
) {
  /** This matrix followed by a translation, as `MatrixOperations` post-multiplies one. */
  private fun translated(x: RcSymbolic, y: RcSymbolic): RcAffine =
    RcAffine(translateX + scaleX * x, translateY + scaleY * y, scaleX, scaleY)

  /** `MatrixOperations`' scale: it multiplies the diagonal entries in place. */
  private fun scaled(x: RcSymbolic, y: RcSymbolic): RcAffine =
    RcAffine(translateX, translateY, scaleX * x, scaleY * y)

  /** This matrix multiplied by [child], as a pop multiplies a level into its parent. */
  private fun then(child: RcAffine): RcAffine =
    RcAffine(
      translateX + scaleX * child.translateX,
      translateY + scaleY * child.translateY,
      scaleX * child.scaleX,
      scaleY * child.scaleY,
    )

  /** This matrix with every composite value computed by a new expression in [definitions]. */
  fun placed(ids: RcIdAllocator, definitions: MutableList<RcOperation>): RcAffine {
    fun RcSymbolic.placed() = RcSymbolic.of(asWord(ids, definitions))
    return RcAffine(translateX.placed(), translateY.placed(), scaleX.placed(), scaleY.placed())
  }

  companion object {
    private val IDENTITY =
      RcAffine(RcSymbolic.of(0f), RcSymbolic.of(0f), RcSymbolic.of(1f), RcSymbolic.of(1f))

    /**
     * What a `MatrixExpression` computes, or null for one that does more than translate and scale
     * in 2D. Each operator reads the words just before it, as AndroidX `MatrixOperations` does;
     * translations and scales along z never reach the 3×3 matrix a shader is given, so they are
     * dropped.
     */
    fun of(expression: List<RcFloatWord>): RcAffine? {
      val stack = mutableListOf(IDENTITY)
      expression.forEachIndexed { index, word ->
        val code = matrixOperator(word) ?: return@forEachIndexed
        val arity = MATRIX_ARITY[code] ?: return null
        if (index < arity) return null
        val operands = expression.subList(index - arity, index)
        if (operands.any { matrixOperator(it) != null }) return null
        val values = operands.map(RcSymbolic::of)
        val top = stack.last()
        when (code) {
          MATRIX_PUSH -> {
            if (stack.size == MATRIX_STACK_DEPTH) return null
            stack += IDENTITY
          }
          MATRIX_POP -> {
            if (stack.size < 2) return null
            val child = stack.removeAt(stack.lastIndex)
            stack[stack.lastIndex] = stack.last().then(child)
          }
          MATRIX_TRANSLATE_X -> stack[stack.lastIndex] = top.translated(values[0], ZERO)
          MATRIX_TRANSLATE_Y -> stack[stack.lastIndex] = top.translated(ZERO, values[0])
          MATRIX_TRANSLATE_XY,
          MATRIX_TRANSLATE_XYZ -> stack[stack.lastIndex] = top.translated(values[0], values[1])
          MATRIX_SCALE_X -> stack[stack.lastIndex] = top.scaled(values[0], ONE)
          MATRIX_SCALE_Y -> stack[stack.lastIndex] = top.scaled(ONE, values[0])
          MATRIX_SCALE_XY,
          MATRIX_SCALE_XYZ -> stack[stack.lastIndex] = top.scaled(values[0], values[1])
          MATRIX_TRANSLATE_Z,
          MATRIX_SCALE_Z -> Unit
        }
      }
      // The evaluator answers with the bottom of its stack, whatever was left pushed above it.
      return stack.first()
    }

    private val ZERO = RcSymbolic.of(0f)
    private val ONE = RcSymbolic.of(1f)

    private fun matrixOperator(word: RcFloatWord): Int? {
      if (!word.isNaNEncoded) return null
      val code = (word.bits and 0x7fffff) - MATRIX_OPERATOR_OFFSET
      return code.takeIf { it in 1..MATRIX_LAST_OPERATOR }
    }

    private const val MATRIX_OPERATOR_OFFSET = 0x320000
    private const val MATRIX_LAST_OPERATOR = 54
    private const val MATRIX_STACK_DEPTH = 10
    private const val MATRIX_PUSH = 1
    private const val MATRIX_TRANSLATE_X = 5
    private const val MATRIX_TRANSLATE_Y = 6
    private const val MATRIX_TRANSLATE_Z = 7
    private const val MATRIX_TRANSLATE_XY = 8
    private const val MATRIX_TRANSLATE_XYZ = 9
    private const val MATRIX_SCALE_X = 10
    private const val MATRIX_SCALE_Y = 11
    private const val MATRIX_SCALE_Z = 12
    private const val MATRIX_SCALE_XY = 13
    private const val MATRIX_SCALE_XYZ = 14
    private const val MATRIX_POP = 15

    /** Operand counts of the operators [of] understands; any other operator declines. */
    private val MATRIX_ARITY: Map<Int, Int> =
      mapOf(
        MATRIX_PUSH to 0,
        MATRIX_TRANSLATE_X to 1,
        MATRIX_TRANSLATE_Y to 1,
        MATRIX_TRANSLATE_Z to 1,
        MATRIX_TRANSLATE_XY to 2,
        MATRIX_TRANSLATE_XYZ to 3,
        MATRIX_SCALE_X to 1,
        MATRIX_SCALE_Y to 1,
        MATRIX_SCALE_Z to 1,
        MATRIX_SCALE_XY to 2,
        MATRIX_SCALE_XYZ to 3,
        MATRIX_POP to 0,
      )
  }
}

/**
 * Hands out ids a document does not use: above every id it defines, and skipping any value any
 * operation mentions, so a new definition can shadow nothing.
 */
internal class RcIdAllocator(operations: List<RcOperation>) {
  private val mentioned = operations.flatMapTo(mutableSetOf()) { it.mentionedValues().orEmpty() }
  private var candidate =
    maxOf(
      FIRST_DOCUMENT_ID,
      operations
        .flatMap { it.definedIds().orEmpty() }
        .filter { it < ID_REGION }
        .maxOrNull()
        ?.plus(1) ?: 0,
    )

  fun next(): Int {
    while (candidate in mentioned) candidate++
    return candidate++
  }

  private companion object {
    /** Ids from here up carry AndroidX's region bits (arrays, lists) rather than a plain value. */
    const val ID_REGION = 0x100000
  }
}

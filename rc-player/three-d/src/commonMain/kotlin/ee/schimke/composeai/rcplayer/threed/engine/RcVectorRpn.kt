package ee.schimke.composeai.rcplayer.threed.engine

import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reverse-Polish evaluator for `VECTOR_EXPRESSION`, ported from the reference `VectorRpn.java`.
 *
 * Every stack slot holds four lanes. A scalar broadcasts across all four; a built vector zero-pads
 * its unused high lanes, which is what makes dot and length correct at any dimension.
 *
 * The program is taken as [RcFloatWord]s, not floats: an opcode is a NaN payload, and a NaN that
 * passes through a `Float` may be quieted on some targets (Kotlin/Wasm and JS among them), which
 * would turn the opcode into another one.
 */
internal class RcVectorRpn {
  /** Substitute a tiny denominator for a near-zero one; used for the corrective retry. */
  var softDomain: Boolean = false

  private val stack = FloatArray(MAX_STACK * DIM)
  private val dims = IntArray(MAX_STACK)

  /**
   * Evaluate [program], whose variable references the caller has already replaced with literals,
   * into [out] (four lanes). Returns the result's dimension.
   */
  fun apply(program: List<RcFloatWord>, out: FloatArray): Int {
    require(program.size <= MAX_PROGRAM) { "Vector program too long" }
    val s = stack
    var sp = -1
    for (word in program) {
      if (word.isNaNEncoded) {
        sp = opEval(sp, word.bits and OP_MASK)
      } else {
        sp++
        require(sp < MAX_STACK) { "Vector stack overflow" }
        val v = word.value
        val p = sp * DIM
        s[p] = v
        s[p + 1] = v
        s[p + 2] = v
        s[p + 3] = v
        dims[sp] = 1
      }
    }
    require(sp >= 0) { "Empty vector program" }
    val p = sp * DIM
    out[0] = s[p]
    out[1] = s[p + 1]
    out[2] = s[p + 2]
    out[3] = s[p + 3]
    return dims[sp]
  }

  private fun softDenom(denom: Float): Float =
    if (softDomain && denom < SOFT_DOMAIN_EPS && denom > -SOFT_DOMAIN_EPS) {
      if (denom < 0f) -SOFT_DOMAIN_EPS else SOFT_DOMAIN_EPS
    } else {
      denom
    }

  private fun build(sp: Int, n: Int): Int {
    val s = stack
    val base = sp - n + 1
    require(base >= 0) { "Vector stack underflow" }
    val c0 = s[base * DIM]
    val c1 = if (n > 1) s[(base + 1) * DIM] else 0f
    val c2 = if (n > 2) s[(base + 2) * DIM] else 0f
    val c3 = if (n > 3) s[(base + 3) * DIM] else 0f
    val p = base * DIM
    s[p] = c0
    s[p + 1] = c1
    s[p + 2] = c2
    s[p + 3] = c3
    dims[base] = n
    return base
  }

  private inline fun binary(sp: Int, op: (Float, Float) -> Float): Int {
    require(sp >= 1) { "Vector stack underflow" }
    val a = (sp - 1) * DIM
    val b = sp * DIM
    for (i in 0 until DIM) stack[a + i] = op(stack[a + i], stack[b + i])
    dims[sp - 1] = max(dims[sp - 1], dims[sp])
    return sp - 1
  }

  private inline fun unary(sp: Int, op: (Float) -> Float): Int {
    require(sp >= 0) { "Vector stack underflow" }
    val b = sp * DIM
    for (i in 0 until DIM) stack[b + i] = op(stack[b + i])
    return sp
  }

  private fun opEval(sp: Int, id: Int): Int {
    val s = stack
    return when (id) {
      OP_ADD -> binary(sp) { x, y -> x + y }
      OP_SUB -> binary(sp) { x, y -> x - y }
      OP_MUL -> binary(sp) { x, y -> x * y }
      OP_DIV -> binary(sp) { x, y -> x / softDenom(y) }
      OP_MOD -> binary(sp) { x, y -> x % softDenom(y) }
      OP_MIN -> binary(sp) { x, y -> min(x, y) }
      OP_MAX -> binary(sp) { x, y -> max(x, y) }
      OP_POW -> binary(sp) { x, y -> x.toDouble().pow(y.toDouble()).toFloat() }
      OP_SQRT -> unary(sp) { sqrt(it.toDouble()).toFloat() }
      OP_ABS -> unary(sp) { abs(it) }
      OP_SQUARE -> unary(sp) { it * it }
      OP_SIN -> unary(sp) { sin(it.toDouble()).toFloat() }
      OP_COS -> unary(sp) { cos(it.toDouble()).toFloat() }
      OP_FLOOR -> unary(sp) { floor(it.toDouble()).toFloat() }
      OP_CEIL -> unary(sp) { ceil(it.toDouble()).toFloat() }
      OP_ROUND -> unary(sp, ::javaRound)
      OP_CHANGE_SIGN -> unary(sp) { -it }
      OP_INV -> unary(sp) { 1f / softDenom(it) }
      OP_VBUILD2 -> build(sp, 2)
      OP_VBUILD3 -> build(sp, 3)
      OP_VBUILD4 -> build(sp, 4)
      OP_VDOT -> {
        require(sp >= 1) { "Vector stack underflow" }
        val a = (sp - 1) * DIM
        val b = sp * DIM
        // Zero-padded high lanes contribute 0, so summing all four is right at any dimension.
        val dot = s[a] * s[b] + s[a + 1] * s[b + 1] + s[a + 2] * s[b + 2] + s[a + 3] * s[b + 3]
        s.fill(dot, a, a + DIM)
        dims[sp - 1] = 1
        sp - 1
      }
      OP_VCROSS -> {
        require(sp >= 1) { "Vector stack underflow" }
        val a = (sp - 1) * DIM
        val b = sp * DIM
        val ax = s[a]
        val ay = s[a + 1]
        val az = s[a + 2]
        val bx = s[b]
        val by = s[b + 1]
        val bz = s[b + 2]
        s[a] = ay * bz - az * by
        s[a + 1] = az * bx - ax * bz
        s[a + 2] = ax * by - ay * bx
        s[a + 3] = 0f
        dims[sp - 1] = 3
        sp - 1
      }
      OP_VLEN,
      OP_VLENSQ -> {
        require(sp >= 0) { "Vector stack underflow" }
        val b = sp * DIM
        val sq = s[b] * s[b] + s[b + 1] * s[b + 1] + s[b + 2] * s[b + 2] + s[b + 3] * s[b + 3]
        val r = if (id == OP_VLEN) sqrt(sq.toDouble()).toFloat() else sq
        s.fill(r, b, b + DIM)
        dims[sp] = 1
        sp
      }
      OP_VNORM -> {
        require(sp >= 0) { "Vector stack underflow" }
        val b = sp * DIM
        val sq = s[b] * s[b] + s[b + 1] * s[b + 1] + s[b + 2] * s[b + 2] + s[b + 3] * s[b + 3]
        val inv = 1f / softDenom(sqrt(sq.toDouble()).toFloat())
        for (i in 0 until DIM) s[b + i] *= inv
        sp
      }
      OP_NOP -> sp
      else -> throw IllegalArgumentException("Vector operator ${id - OFFSET} is not implemented")
    }
  }

  internal companion object {
    const val OFFSET: Int = 0x310000
    const val DIM: Int = 4
    private const val OP_MASK = 0x7FFFFF
    private const val MAX_STACK = 64
    private const val MAX_PROGRAM = 512
    private const val SOFT_DOMAIN_EPS = 1e-6f

    const val OP_ADD: Int = OFFSET + 1
    const val OP_SUB: Int = OFFSET + 2
    const val OP_MUL: Int = OFFSET + 3
    const val OP_DIV: Int = OFFSET + 4
    const val OP_MOD: Int = OFFSET + 5
    const val OP_MIN: Int = OFFSET + 6
    const val OP_MAX: Int = OFFSET + 7
    const val OP_POW: Int = OFFSET + 8
    const val OP_SQRT: Int = OFFSET + 9
    const val OP_ABS: Int = OFFSET + 10
    const val OP_FLOOR: Int = OFFSET + 14
    const val OP_ROUND: Int = OFFSET + 17
    const val OP_SIN: Int = OFFSET + 18
    const val OP_COS: Int = OFFSET + 19
    const val OP_CEIL: Int = OFFSET + 31
    const val OP_SQUARE: Int = OFFSET + 45
    const val OP_INV: Int = OFFSET + 52
    const val OP_NOP: Int = OFFSET + 55
    const val OP_CHANGE_SIGN: Int = OFFSET + 73
    const val OP_VBUILD2: Int = OFFSET + 100
    const val OP_VBUILD3: Int = OFFSET + 101
    const val OP_VBUILD4: Int = OFFSET + 102
    const val OP_VDOT: Int = OFFSET + 103
    const val OP_VCROSS: Int = OFFSET + 104
    const val OP_VLEN: Int = OFFSET + 105
    const val OP_VLENSQ: Int = OFFSET + 106
    const val OP_VNORM: Int = OFFSET + 107

    /** Whether [word] is one of the vector-only opcodes, which must never be resolved as an id. */
    fun isVectorOp(word: RcFloatWord): Boolean {
      if (!word.isNaNEncoded) return false
      val id = word.bits and OP_MASK
      return id in OP_VBUILD2..OP_VNORM
    }

    /** Whether [word] is any operator, scalar or vector. */
    fun isOperator(word: RcFloatWord): Boolean {
      if (!word.isNaNEncoded) return false
      val id = word.bits and OP_MASK
      return id in (OFFSET + 1)..OP_VNORM
    }

    /** The NaN-boxed word for operator [id] (an `OP_*` constant). */
    fun word(id: Int): RcFloatWord = RcFloatWord(0xff800000.toInt() or id)

    /** Java's `Math.round(float)`: floor(x + 0.5) as an Int, saturating, NaN to 0. */
    private fun javaRound(x: Float): Float {
      if (x.isNaN()) return 0f
      val r = floor((x + 0.5f).toDouble())
      if (r >= 2147483647.0) return 2147483647f
      if (r <= -2147483648.0) return -2147483648f
      return r.toInt().toFloat()
    }
  }
}

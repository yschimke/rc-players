package ee.schimke.composeai.rcplayer.protocol

/**
 * AndroidX `AddMesh2D` (opcode 104): a 2D vertex mesh, defined once and drawn by [RcDrawMesh2D].
 *
 * One operation carries every way of supplying the vertices, discriminated by [type]:
 * - [TYPE_EXPRESSION]: one RPN expression group per channel ([expressions], [EXPRESSION_GROUPS] of
 *   them, each empty when absent), evaluated per vertex with the domain parameters `u` and `v`
 *   bound to `VAR1` / `VAR2`. Topology is implied by [layout], [uCount] and [vCount].
 * - [TYPE_VALUES] / [TYPE_F16_VALUES]: an explicit triangle list ([indices]) and per-vertex
 *   [vertices], [uv] and packed ARGB [colors]. The f16 form is only a wire encoding: the codec
 *   widens the halves to floats on read and narrows them back on write.
 * - [TYPE_PATH_SPLINE_STRIP] / [TYPE_SPLINE_ROUND_STRIP]: a ribbon along the path [aux] whose cross
 *   width is a spline through [widths], optionally placed at [widthPositions].
 *
 * With [FLAG_ANTIALIAS] in [flags] the payload ends in [antialiasWidth], the width of the skirt
 * that fades the mesh's edges; otherwise [antialiasWidth] is null.
 *
 * Experimental upstream: remote-core alpha23 reads it only under `PROFILE_EXPERIMENTAL` (with the
 * AndroidX or widgets profile), so a document carrying it must declare that profile.
 */
public data class RcAddMesh2D(
  val meshId: Int,
  val type: Int,
  val layout: Int,
  val uCount: Int,
  val vCount: Int,
  val flags: Int,
  val aux: Int,
  val expressions: List<List<RcFloatWord>> = emptyList(),
  val indices: List<Int> = emptyList(),
  val vertices: List<RcFloatWord> = emptyList(),
  val uv: List<Float> = emptyList(),
  val colors: List<Int> = emptyList(),
  val widths: List<RcFloatWord> = emptyList(),
  val widthPositions: List<RcFloatWord> = emptyList(),
  val antialiasWidth: RcFloatWord? = null,
) : RcOperation {
  override val opcode: Int = RcOpcodes.ADD_MESH_2D

  /** The expression group at [index] (one of the `EXP_*` constants), empty when absent. */
  public fun expression(index: Int): List<RcFloatWord> = expressions.getOrNull(index).orEmpty()

  public val isSplineStrip: Boolean
    get() = type == TYPE_PATH_SPLINE_STRIP || type == TYPE_SPLINE_ROUND_STRIP

  public companion object {
    /** RPN expressions over `(u, v)`; topology implicit, no index array. */
    public const val TYPE_EXPRESSION: Int = 0

    /** Explicit indices and vertices, 32-bit floats. */
    public const val TYPE_VALUES: Int = 1

    /** As [TYPE_VALUES], with positions and uv as IEEE half floats on the wire. */
    public const val TYPE_F16_VALUES: Int = 2

    /** A ribbon along a path whose cross width is a monotonic spline through control points. */
    public const val TYPE_PATH_SPLINE_STRIP: Int = 3

    /** As [TYPE_PATH_SPLINE_STRIP], ending in a semicircular cap at each end. */
    public const val TYPE_SPLINE_ROUND_STRIP: Int = 4

    /** `Mesh2DGenerator.LAYOUT_GRID`: u across, v down. */
    public const val LAYOUT_GRID: Int = 0

    /** `Mesh2DGenerator.LAYOUT_POLAR`: u is an angle over a full turn, v the radius 0..1. */
    public const val LAYOUT_POLAR: Int = 1

    /** `Mesh2DGenerator.LAYOUT_RING`: u is an angle, v crosses a band between two radii. */
    public const val LAYOUT_RING: Int = 2

    /** `Mesh2DGenerator.LAYOUT_STRIP`: u along, v across. */
    public const val LAYOUT_STRIP: Int = 3

    /** `Mesh2DGenerator.LAYOUT_FAN`: u is an angle about a shared centre vertex. */
    public const val LAYOUT_FAN: Int = 4

    /** `Mesh2DGenerator.LAYOUT_PATH_STRIP`: u is arclength along the path in `aux`, v across. */
    public const val LAYOUT_PATH_STRIP: Int = 5

    /** Expression group: vertex x. */
    public const val EXP_X: Int = 0

    /** Expression group: vertex y. */
    public const val EXP_Y: Int = 1

    /** Expression group: texture u. */
    public const val EXP_TEX_U: Int = 2

    /** Expression group: texture v. */
    public const val EXP_TEX_V: Int = 3

    /** Expression group: colour alpha, 0..1. */
    public const val EXP_COLOR_A: Int = 4

    /** Expression group: colour red, 0..1. */
    public const val EXP_COLOR_R: Int = 5

    /** Expression group: colour green, 0..1. */
    public const val EXP_COLOR_G: Int = 6

    /** Expression group: colour blue, 0..1. */
    public const val EXP_COLOR_B: Int = 7

    /** Expression group: the cross width of a path strip. */
    public const val EXP_WIDTH: Int = 8

    /** The number of expression groups [TYPE_EXPRESSION] carries. */
    public const val EXPRESSION_GROUPS: Int = 9

    /** Grow an alpha-fading skirt around the mesh's edges; the payload ends in its width. */
    public const val FLAG_ANTIALIAS: Int = 1 shl 8

    /** The bits of [flags] holding a [TYPE_SPLINE_ROUND_STRIP]'s cap columns. */
    public const val CAP_SEGMENTS_MASK: Int = 0xff

    /** `Limits.MAX_EXPRESSION_SIZE`: the longest one expression group may be. */
    public const val MAX_EXPRESSION_SIZE: Int = 32

    /** `Limits.MAX_MESH_2D_VERTICES`. */
    public const val MAX_VERTICES: Int = 16_384

    /** `Limits.MAX_MESH_2D_INDICES`. */
    public const val MAX_INDICES: Int = 49_152

    /** `Limits.MAX_MESH_2D_GRID`: the most `uCount * vCount` a parametric mesh may have. */
    public const val MAX_GRID: Int = 16_384

    /** `Limits.MAX_MESH_2D_WIDTH_SAMPLES`. */
    public const val MAX_WIDTH_SAMPLES: Int = 64
  }
}

/**
 * AndroidX `DrawMesh2D` (opcode 105): draws the mesh [meshId] with the current paint, sampling
 * [imageId] (or [NO_IMAGE]) through the mesh's texture coordinates.
 */
public data class RcDrawMesh2D(val meshId: Int, val blend: Int, val imageId: Int) : RcOperation {
  override val opcode: Int = RcOpcodes.DRAW_MESH_2D

  public companion object {
    /** `DrawMesh2D.BLEND_COLORS_ONLY`. */
    public const val BLEND_COLORS_ONLY: Int = 0

    /** `DrawMesh2D.BLEND_MODULATE`: texel times vertex colour. */
    public const val BLEND_MODULATE: Int = 1

    /** `DrawMesh2D.NO_IMAGE`: untextured. */
    public const val NO_IMAGE: Int = 0
  }
}

/**
 * AndroidX `MatrixFromMesh2D` (opcode 106): multiplies the local frame of mesh [meshId] at `(u, v)`
 * into the canvas matrix, keeping as much of it as [flags] asks for.
 */
public data class RcMatrixFromMesh2D(
  val meshId: Int,
  val u: RcFloatWord,
  val v: RcFloatWord,
  val flags: Int,
) : RcOperation {
  override val opcode: Int = RcOpcodes.MATRIX_FROM_MESH_2D

  public companion object {
    /** Translate to the surface point only. */
    public const val FLAG_ORIGIN: Int = 0

    /** Position and the surface's rotation. */
    public const val FLAG_ROTATION: Int = 1

    /** Position, rotation and scale. */
    public const val FLAG_SCALE: Int = 2

    /** The full 2x3 affine, skew included. */
    public const val FLAG_FULL: Int = 3
  }
}

/** Codecs for the experimental 2D mesh operations. */
internal val rcMesh2DOperationCodecs: List<RcOperationCodec<out RcOperation>> =
  listOf(AddMesh2DCodec, DrawMesh2DCodec, MatrixFromMesh2DCodec)

/**
 * Field order is AndroidX alpha23 `AddMesh2D.read`: a seven-int header, then a payload that depends
 * on the type, then — with `FLAG_ANTIALIAS` — the skirt width.
 */
private object AddMesh2DCodec : RcOperationCodec<RcAddMesh2D> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.ADD_MESH_2D, "AddMesh2D")

  override fun decode(input: RcWireReader): RcAddMesh2D {
    val meshId = input.readDeclaredId("meshId")
    val type = input.readInt("type")
    val layout = input.readInt("layout")
    val uCount = input.readInt("uCount")
    val vCount = input.readInt("vCount")
    val flags = input.readInt("flags")
    val aux = input.readInt("aux")
    var expressions: List<List<RcFloatWord>> = emptyList()
    var indices: List<Int> = emptyList()
    var vertices: List<RcFloatWord> = emptyList()
    var uv: List<Float> = emptyList()
    var colors: List<Int> = emptyList()
    var widths: List<RcFloatWord> = emptyList()
    var widthPositions: List<RcFloatWord> = emptyList()
    when (type) {
      RcAddMesh2D.TYPE_EXPRESSION,
      RcAddMesh2D.TYPE_PATH_SPLINE_STRIP,
      RcAddMesh2D.TYPE_SPLINE_ROUND_STRIP -> {
        if (uCount.toLong() * vCount.toLong() > RcAddMesh2D.MAX_GRID) {
          input.fail("uCount", "Mesh2D grid exceeds ${RcAddMesh2D.MAX_GRID}")
        }
        if (type == RcAddMesh2D.TYPE_EXPRESSION) {
          expressions =
            List(RcAddMesh2D.EXPRESSION_GROUPS) { group ->
              val length =
                input.readCount("expressions[$group].length", RcAddMesh2D.MAX_EXPRESSION_SIZE)
              List(length) { input.readFloatWord("expressions[$group][$it]") }
            }
        } else {
          val widthCount = input.readCount("widths.length", RcAddMesh2D.MAX_WIDTH_SAMPLES)
          widths = List(widthCount) { input.readFloatWord("widths[$it]") }
          val positionCount =
            input.readCount("widthPositions.length", RcAddMesh2D.MAX_WIDTH_SAMPLES)
          widthPositions = List(positionCount) { input.readFloatWord("widthPositions[$it]") }
        }
      }
      else -> {
        // Every other type is read as literal values, as AndroidX's `else` branch does.
        val indexCount = input.readCount("indices.length", RcAddMesh2D.MAX_INDICES)
        indices = List(indexCount) { input.readU16("indices[$it]") }
        val vertsLength = input.readCount("vertices.length", RcAddMesh2D.MAX_VERTICES * 2 + 1)
        val uvLength = input.readCount("uv.length", RcAddMesh2D.MAX_VERTICES * 2 + 1)
        val colorsLength = input.readCount("colors.length", RcAddMesh2D.MAX_VERTICES)
        if (type == RcAddMesh2D.TYPE_F16_VALUES) {
          vertices =
            List(vertsLength) { RcFloatWord.literal(halfToFloat(input.readU16("vertices[$it]"))) }
          uv = List(uvLength) { halfToFloat(input.readU16("uv[$it]")) }
        } else {
          vertices = List(vertsLength) { input.readFloatWord("vertices[$it]") }
          uv = List(uvLength) { Float.fromBits(input.readInt("uv[$it]")) }
        }
        colors = List(colorsLength) { input.readInt("colors[$it]") }
      }
    }
    val antialiasWidth =
      if (flags and RcAddMesh2D.FLAG_ANTIALIAS != 0) input.readFloatWord("antialiasWidth") else null
    return RcAddMesh2D(
      meshId = meshId,
      type = type,
      layout = layout,
      uCount = uCount,
      vCount = vCount,
      flags = flags,
      aux = aux,
      expressions = expressions,
      indices = indices,
      vertices = vertices,
      uv = uv,
      colors = colors,
      widths = widths,
      widthPositions = widthPositions,
      antialiasWidth = antialiasWidth,
    )
  }

  override fun encode(output: RcWireWriter, value: RcAddMesh2D) {
    output.writeInt(value.meshId)
    output.writeInt(value.type)
    output.writeInt(value.layout)
    output.writeInt(value.uCount)
    output.writeInt(value.vCount)
    output.writeInt(value.flags)
    output.writeInt(value.aux)
    when (value.type) {
      RcAddMesh2D.TYPE_EXPRESSION -> {
        require(value.expressions.size <= RcAddMesh2D.EXPRESSION_GROUPS) {
          "AddMesh2D carries at most ${RcAddMesh2D.EXPRESSION_GROUPS} expression groups"
        }
        repeat(RcAddMesh2D.EXPRESSION_GROUPS) { group ->
          val words = value.expression(group)
          require(words.size <= RcAddMesh2D.MAX_EXPRESSION_SIZE) {
            "AddMesh2D expression group $group exceeds AndroidX's 32-word limit"
          }
          output.writeInt(words.size)
          words.forEach(output::writeFloatWord)
        }
      }
      RcAddMesh2D.TYPE_PATH_SPLINE_STRIP,
      RcAddMesh2D.TYPE_SPLINE_ROUND_STRIP -> {
        output.writeInt(value.widths.size)
        value.widths.forEach(output::writeFloatWord)
        output.writeInt(value.widthPositions.size)
        value.widthPositions.forEach(output::writeFloatWord)
      }
      else -> {
        output.writeInt(value.indices.size)
        value.indices.forEach { output.writeU16(it and 0xffff) }
        output.writeInt(value.vertices.size)
        output.writeInt(value.uv.size)
        output.writeInt(value.colors.size)
        if (value.type == RcAddMesh2D.TYPE_F16_VALUES) {
          value.vertices.forEach { output.writeU16(floatToHalf(it.value)) }
          value.uv.forEach { output.writeU16(floatToHalf(it)) }
        } else {
          value.vertices.forEach(output::writeFloatWord)
          value.uv.forEach { output.writeInt(it.toRawBits()) }
        }
        value.colors.forEach(output::writeInt)
      }
    }
    if (value.flags and RcAddMesh2D.FLAG_ANTIALIAS != 0) {
      output.writeFloatWord(value.antialiasWidth ?: RcFloatWord.literal(1f))
    }
  }
}

private object DrawMesh2DCodec : RcOperationCodec<RcDrawMesh2D> {
  override val spec: RcOperationSpec = RcOperationSpec(RcOpcodes.DRAW_MESH_2D, "DrawMesh2D")

  override fun decode(input: RcWireReader): RcDrawMesh2D =
    RcDrawMesh2D(
      meshId = input.readId("meshId"),
      blend = input.readInt("blend"),
      imageId = input.readId("imageId"),
    )

  override fun encode(output: RcWireWriter, value: RcDrawMesh2D) {
    output.writeInt(value.meshId)
    output.writeInt(value.blend)
    output.writeInt(value.imageId)
  }
}

private object MatrixFromMesh2DCodec : RcOperationCodec<RcMatrixFromMesh2D> {
  override val spec: RcOperationSpec =
    RcOperationSpec(RcOpcodes.MATRIX_FROM_MESH_2D, "MatrixFromMesh2D")

  override fun decode(input: RcWireReader): RcMatrixFromMesh2D =
    RcMatrixFromMesh2D(
      meshId = input.readId("meshId"),
      u = input.readFloatWord("u"),
      v = input.readFloatWord("v"),
      flags = input.readInt("flags"),
    )

  override fun encode(output: RcWireWriter, value: RcMatrixFromMesh2D) {
    output.writeInt(value.meshId)
    output.writeFloatWord(value.u)
    output.writeFloatWord(value.v)
    output.writeInt(value.flags)
  }
}

/** `Mesh2DGenerator.halfToFloat`: an IEEE 754 half in the low 16 bits, widened. */
internal fun halfToFloat(half: Int): Float {
  val sign = (half ushr 15) and 0x1
  var exponent = (half ushr 10) and 0x1f
  var mantissa = half and 0x3ff
  val bits =
    when {
      exponent == 0 && mantissa == 0 -> sign shl 31
      exponent == 0 -> {
        exponent = 127 - 15 + 1
        while (mantissa and 0x400 == 0) {
          mantissa = mantissa shl 1
          exponent--
        }
        mantissa = mantissa and 0x3ff
        (sign shl 31) or (exponent shl 23) or (mantissa shl 13)
      }
      exponent == 0x1f -> (sign shl 31) or 0x7f800000 or (mantissa shl 13)
      else -> (sign shl 31) or ((exponent - 15 + 127) shl 23) or (mantissa shl 13)
    }
  return Float.fromBits(bits)
}

/** `Mesh2DGenerator.floatToHalf`: round to nearest, overflow to infinity. */
internal fun floatToHalf(value: Float): Int {
  val bits = value.toRawBits()
  val sign = (bits ushr 16) and 0x8000
  val exponent = (bits ushr 23) and 0xff
  var mantissa = bits and 0x7fffff
  if (exponent == 0xff) return sign or 0x7c00 or (if (mantissa != 0) 0x200 else 0)
  val unbiased = exponent - 127 + 15
  if (unbiased >= 0x1f) return sign or 0x7c00
  if (unbiased <= 0) {
    if (unbiased < -10) return sign
    mantissa = mantissa or 0x800000
    val shift = 14 - unbiased
    var half = mantissa ushr shift
    if ((mantissa ushr (shift - 1)) and 0x1 != 0) half++
    return sign or half
  }
  var half = (unbiased shl 10) or (mantissa ushr 13)
  if (mantissa and 0x1000 != 0) half++
  return sign or half
}

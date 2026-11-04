package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.protocol.RcExtensionOperation
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcOperationCodec
import ee.schimke.composeai.rcplayer.protocol.RcOperationSpec
import ee.schimke.composeai.rcplayer.protocol.RcWireReader
import ee.schimke.composeai.rcplayer.protocol.RcWireWriter

/**
 * Opcodes of the draft 3D operations, as AndroidX ag/4108133 registers them in its experimental
 * profiles. 119 is unassigned in the draft.
 *
 * These sit in AndroidX's core opcode range and are not reserved there, so a later AndroidX release
 * may give them other meanings. A document that uses them is only meaningful to a player that
 * installed this extension at a matching [Rc3DExtension.DRAFT] version.
 */
public object Rc3DOpcodes {
  public const val DEFINE_MESH_3D: Int = 110
  public const val SET_CAMERA_3D: Int = 111
  public const val MATRIX_3D_OP: Int = 112
  public const val DRAW_MESH_3D: Int = 113
  public const val PAINT_3D_STATE: Int = 114
  public const val SET_LIGHTS_3D: Int = 115
  public const val VECTOR_EXPRESSION: Int = 116
  public const val MESH_EXPRESSION_3D: Int = 117
  public const val SET_TEXTURE_3D: Int = 118
  public const val MESH_PRIMITIVE_3D: Int = 120

  public val ALL: Set<Int> =
    setOf(
      DEFINE_MESH_3D,
      SET_CAMERA_3D,
      MATRIX_3D_OP,
      DRAW_MESH_3D,
      PAINT_3D_STATE,
      SET_LIGHTS_3D,
      VECTOR_EXPRESSION,
      MESH_EXPRESSION_3D,
      SET_TEXTURE_3D,
      MESH_PRIMITIVE_3D,
    )
}

/** Constants the 3D operations' payloads use, named as `Paint3DContext` names them. */
public object Rc3D {
  public const val PROJECTION_PERSPECTIVE: Int = 0
  public const val PROJECTION_ORTHO: Int = 1

  public const val M3_IDENTITY: Int = 0
  public const val M3_TRANSLATE: Int = 1
  public const val M3_SCALE: Int = 2
  public const val M3_ROTATE_AXIS: Int = 3
  public const val M3_MULTIPLY: Int = 4

  public const val LIGHT_DIRECTIONAL: Int = 0
  public const val LIGHT_POINT: Int = 1

  public const val P3_CLEAR_DEPTH: Int = 0
  public const val P3_MATERIAL: Int = 1
  public const val P3_DEPTH_BIAS: Int = 2

  /** Render modes: `(backend << 1) | smoothBit`, plus the wireframe bits. */
  public const val MODE_SOFTWARE_FLAT: Int = 0
  public const val MODE_SOFTWARE_SMOOTH: Int = 1
  public const val MODE_CANVAS_FLAT: Int = 2
  public const val MODE_CANVAS_SMOOTH: Int = 3
  public const val MODE_DRAWMESH_FLAT: Int = 4
  public const val MODE_DRAWMESH_SMOOTH: Int = 5
  public const val MODE_GL_FLAT: Int = 6
  public const val MODE_GL_SMOOTH: Int = 7
  public const val MODE_DRAWMESH_ZBUF_FLAT: Int = 8
  public const val MODE_DRAWMESH_ZBUF_SMOOTH: Int = 9
  public const val MODE_CANVAS_ZBUF_FLAT: Int = 10
  public const val MODE_CANVAS_ZBUF_SMOOTH: Int = 11
  public const val MODE_SMOOTH_MASK: Int = 0x1
  public const val MODE_WIREFRAME: Int = 0x100
  public const val WIRE_EDGE0: Int = 0x200
  public const val WIRE_EDGE1: Int = 0x400
  public const val WIRE_EDGE2: Int = 0x800
  public const val WIRE_EDGE_MASK: Int = WIRE_EDGE0 or WIRE_EDGE1 or WIRE_EDGE2

  public const val MODE_BACKEND_SOFTWARE: Int = 0
  public const val MODE_BACKEND_CANVAS: Int = 1
  public const val MODE_BACKEND_DRAWMESH: Int = 2
  public const val MODE_BACKEND_GL: Int = 3
  public const val MODE_BACKEND_DRAWMESH_ZBUF: Int = 4
  public const val MODE_BACKEND_CANVAS_ZBUF: Int = 5

  public const val SURFACE_GENERAL: Int = 0
  public const val SURFACE_HEIGHT_FIELD: Int = 1
  public const val SURFACE_SPHERE: Int = 2
  public const val SURFACE_CYLINDER: Int = 3
  public const val SURFACE_FLAG_FLIP_WINDING: Int = 0x1
}

/** Common supertype of the ten draft operations. */
public sealed interface Rc3DOperation : RcExtensionOperation {
  override val extensionId: String
    get() = Rc3DExtension.ID
}

/** `DEFINE_MESH_3D`: upload, or replace, the mesh [id]. Literal geometry only. */
public class RcDefineMesh3D(
  public val id: Int,
  public val indices: IntArray,
  public val verts: FloatArray,
  public val normals: FloatArray,
  public val uv: FloatArray,
) : Rc3DOperation {
  override val opcode: Int
    get() = Rc3DOpcodes.DEFINE_MESH_3D

  override fun equals(other: Any?): Boolean =
    other is RcDefineMesh3D &&
      id == other.id &&
      indices.contentEquals(other.indices) &&
      verts.contentEquals(other.verts) &&
      normals.contentEquals(other.normals) &&
      uv.contentEquals(other.uv)

  override fun hashCode(): Int =
    (id * 31 + indices.contentHashCode()) * 31 + verts.contentHashCode()

  override fun toString(): String =
    "RcDefineMesh3D(id=$id, triangles=${indices.size / 3}, vertices=${verts.size / 3})"
}

/** `SET_CAMERA_3D`: projection and view in one call; every parameter may be a variable. */
public data class RcSetCamera3D(
  val projection: Int,
  val projParams: List<RcFloatWord>,
  val viewParams: List<RcFloatWord>,
) : Rc3DOperation {
  override val opcode: Int
    get() = Rc3DOpcodes.SET_CAMERA_3D
}

/** `MATRIX_3D_OP`: post-multiply the model matrix; every argument may be a variable. */
public data class RcMatrix3DOp(val sub: Int, val args: List<RcFloatWord>) : Rc3DOperation {
  override val opcode: Int
    get() = Rc3DOpcodes.MATRIX_3D_OP
}

/** `DRAW_MESH_3D`: draw [meshId] with the current matrices, lights and paint colour. */
public data class RcDrawMesh3D(val meshId: Int, val mode: Int) : Rc3DOperation {
  override val opcode: Int
    get() = Rc3DOpcodes.DRAW_MESH_3D
}

/** `PAINT_3D_STATE`: clear-depth, material or depth bias, by [sub]. */
public data class RcPaint3DState(val sub: Int, val params: List<RcFloatWord>) : Rc3DOperation {
  override val opcode: Int
    get() = Rc3DOpcodes.PAINT_3D_STATE
}

/**
 * `SET_LIGHTS_3D`: the whole light set. [params] holds four floats per light (direction or
 * position, then intensity), each of which may be a variable.
 */
public data class RcSetLights3D(
  val types: List<Int>,
  val colors: List<Int>,
  val params: List<RcFloatWord>,
) : Rc3DOperation {
  override val opcode: Int
    get() = Rc3DOpcodes.SET_LIGHTS_3D
}

/**
 * `VECTOR_EXPRESSION`: a vec2/3/4-valued RPN program whose components are written to the float ids
 * `id .. id + dimension - 1`.
 */
public data class RcVectorExpression(
  val id: Int,
  val dimension: Int,
  val flags: Int,
  val program: List<RcFloatWord>,
) : Rc3DOperation {
  override val opcode: Int
    get() = Rc3DOpcodes.VECTOR_EXPRESSION
}

/** `MESH_EXPRESSION_3D`: a surface generated from RPN expressions over a `(u,v)` grid. */
public data class RcMeshExpression3D(
  val id: Int,
  val type: Int,
  val flags: Int,
  val params: List<RcFloatWord>,
  val position: List<List<RcFloatWord>>,
  val normal: List<List<RcFloatWord>>,
  val uv: List<List<RcFloatWord>>,
) : Rc3DOperation {
  override val opcode: Int
    get() = Rc3DOpcodes.MESH_EXPRESSION_3D
}

/** `SET_TEXTURE_3D`: texture later meshes with bitmap [bitmapId]; 0 clears it. */
public data class RcSetTexture3D(val bitmapId: Int) : Rc3DOperation {
  override val opcode: Int
    get() = Rc3DOpcodes.SET_TEXTURE_3D
}

/**
 * `MESH_PRIMITIVE_3D`: mesh [id] generated from a parametric shape. `data[0]` is the scalar
 * parameters; later channels carry geometry streams for the multi-channel shapes.
 */
public data class RcMeshPrimitive3D(
  val id: Int,
  val type: Int,
  val segments: RcFloatWord,
  val flags: Int,
  val data: List<List<RcFloatWord>>,
) : Rc3DOperation {
  override val opcode: Int
    get() = Rc3DOpcodes.MESH_PRIMITIVE_3D
}

// Bounds from the reference readers: a malformed or hostile document must not allocate unbounded
// buffers.
private const val MAX_INDICES = 600_000
private const val MAX_VERT_FLOATS = 3 * 200_000
private const val MAX_LIGHTS = 32
private const val MAX_CHANNELS = 8
private const val MAX_EXPRESSION = 4096
private const val MAX_GROUP = 8
private const val MAX_VECTOR_PROGRAM = 512

private fun RcWireReader.readWords(field: String, maximum: Int): List<RcFloatWord> {
  val count = readCount(field, maximum)
  return List(count) { readFloatWord(field) }
}

private fun RcWireWriter.writeWords(words: List<RcFloatWord>) {
  writeInt(words.size)
  words.forEach(::writeFloatWord)
}

private fun RcWireReader.readLiteralFloats(field: String, count: Int): FloatArray =
  FloatArray(count) { Float.fromBits(readInt(field)) }

private fun RcWireWriter.writeLiteralFloats(values: FloatArray) {
  writeInt(values.size)
  values.forEach { writeInt(it.toRawBits()) }
}

internal object DefineMesh3DCodec : RcOperationCodec<RcDefineMesh3D> {
  override val spec = RcOperationSpec(Rc3DOpcodes.DEFINE_MESH_3D, "DefineMesh3D")

  override fun decode(input: RcWireReader): RcDefineMesh3D {
    val id = input.readDeclaredId("id")
    val indexCount = input.readCount("indices", MAX_INDICES)
    if (indexCount % 3 != 0) input.fail("indices", "Index count $indexCount is not a multiple of 3")
    val indices = IntArray(indexCount) { input.readInt("indices") }
    val vertCount = input.readCount("verts", MAX_VERT_FLOATS)
    if (vertCount % 3 != 0) input.fail("verts", "Vertex float count $vertCount is not xyz triples")
    val verts = input.readLiteralFloats("verts", vertCount)
    val normalCount = input.readCount("normals", MAX_VERT_FLOATS)
    if (normalCount != 0 && normalCount != vertCount) {
      input.fail("normals", "Normals ($normalCount floats) do not match vertices ($vertCount)")
    }
    val normals = input.readLiteralFloats("normals", normalCount)
    val uvCount = input.readCount("uv", MAX_VERT_FLOATS)
    if (uvCount != 0 && uvCount != vertCount / 3 * 2) {
      input.fail("uv", "UV ($uvCount floats) does not match ${vertCount / 3} vertices")
    }
    val uv = input.readLiteralFloats("uv", uvCount)
    for (index in indices) {
      if (index < 0 || index >= vertCount / 3) input.fail("indices", "Index $index out of range")
    }
    return RcDefineMesh3D(id, indices, verts, normals, uv)
  }

  override fun encode(output: RcWireWriter, value: RcDefineMesh3D) {
    output.writeInt(value.id)
    output.writeInt(value.indices.size)
    value.indices.forEach(output::writeInt)
    output.writeLiteralFloats(value.verts)
    output.writeLiteralFloats(value.normals)
    output.writeLiteralFloats(value.uv)
  }
}

internal object SetCamera3DCodec : RcOperationCodec<RcSetCamera3D> {
  override val spec = RcOperationSpec(Rc3DOpcodes.SET_CAMERA_3D, "SetCamera3D")

  override fun decode(input: RcWireReader): RcSetCamera3D =
    RcSetCamera3D(
      input.readInt("projection"),
      input.readWords("projParams", 16),
      input.readWords("viewParams", 16),
    )

  override fun encode(output: RcWireWriter, value: RcSetCamera3D) {
    output.writeInt(value.projection)
    output.writeWords(value.projParams)
    output.writeWords(value.viewParams)
  }
}

internal object Matrix3DOpCodec : RcOperationCodec<RcMatrix3DOp> {
  override val spec = RcOperationSpec(Rc3DOpcodes.MATRIX_3D_OP, "Matrix3DOp")

  override fun decode(input: RcWireReader): RcMatrix3DOp =
    RcMatrix3DOp(input.readInt("sub"), input.readWords("args", 16))

  override fun encode(output: RcWireWriter, value: RcMatrix3DOp) {
    output.writeInt(value.sub)
    output.writeWords(value.args)
  }
}

internal object DrawMesh3DCodec : RcOperationCodec<RcDrawMesh3D> {
  override val spec = RcOperationSpec(Rc3DOpcodes.DRAW_MESH_3D, "DrawMesh3D")

  override fun decode(input: RcWireReader): RcDrawMesh3D =
    RcDrawMesh3D(input.readId("meshId"), input.readInt("mode"))

  override fun encode(output: RcWireWriter, value: RcDrawMesh3D) {
    output.writeInt(value.meshId)
    output.writeInt(value.mode)
  }
}

internal object Paint3DStateCodec : RcOperationCodec<RcPaint3DState> {
  override val spec = RcOperationSpec(Rc3DOpcodes.PAINT_3D_STATE, "Paint3DState")

  override fun decode(input: RcWireReader): RcPaint3DState =
    RcPaint3DState(input.readInt("sub"), input.readWords("params", 8))

  override fun encode(output: RcWireWriter, value: RcPaint3DState) {
    output.writeInt(value.sub)
    output.writeWords(value.params)
  }
}

internal object SetLights3DCodec : RcOperationCodec<RcSetLights3D> {
  override val spec = RcOperationSpec(Rc3DOpcodes.SET_LIGHTS_3D, "SetLights3D")

  override fun decode(input: RcWireReader): RcSetLights3D {
    val count = input.readCount("lights", MAX_LIGHTS)
    val types = ArrayList<Int>(count)
    val colors = ArrayList<Int>(count)
    repeat(count) {
      types += input.readInt("type")
      colors += input.readInt("color")
    }
    return RcSetLights3D(types, colors, input.readWords("params", MAX_LIGHTS * 4))
  }

  override fun encode(output: RcWireWriter, value: RcSetLights3D) {
    output.writeInt(value.types.size)
    value.types.indices.forEach {
      output.writeInt(value.types[it])
      output.writeInt(value.colors[it])
    }
    output.writeWords(value.params)
  }
}

internal object VectorExpressionCodec : RcOperationCodec<RcVectorExpression> {
  override val spec = RcOperationSpec(Rc3DOpcodes.VECTOR_EXPRESSION, "VectorExpression")

  override fun decode(input: RcWireReader): RcVectorExpression {
    val id = input.readDeclaredId("id")
    val dimension = input.readU8("dimension")
    if (dimension !in 1..4) input.fail("dimension", "Vector dimension $dimension is not 1..4")
    val flags = input.readU8("flags")
    val length = input.readU16("length")
    if (length > MAX_VECTOR_PROGRAM) input.fail("length", "Vector program of $length words")
    return RcVectorExpression(id, dimension, flags, List(length) { input.readFloatWord("value") })
  }

  override fun encode(output: RcWireWriter, value: RcVectorExpression) {
    output.writeInt(value.id)
    output.writeU8(value.dimension)
    output.writeU8(value.flags)
    output.writeU16(value.program.size)
    value.program.forEach(output::writeFloatWord)
  }
}

internal object MeshExpression3DCodec : RcOperationCodec<RcMeshExpression3D> {
  override val spec = RcOperationSpec(Rc3DOpcodes.MESH_EXPRESSION_3D, "MeshExpression3D")

  private fun RcWireReader.readGroup(field: String): List<List<RcFloatWord>> =
    List(readCount(field, MAX_GROUP)) { readWords(field, MAX_EXPRESSION) }

  private fun RcWireWriter.writeGroup(group: List<List<RcFloatWord>>) {
    writeInt(group.size)
    group.forEach { writeWords(it) }
  }

  override fun decode(input: RcWireReader): RcMeshExpression3D {
    val id = input.readDeclaredId("id")
    val type = input.readInt("type")
    val flags = input.readInt("flags")
    val params = input.readWords("params", MAX_EXPRESSION)
    val position = input.readGroup("position")
    val normal = input.readGroup("normal")
    val uv = input.readGroup("uv")
    val needed =
      when (type) {
        Rc3D.SURFACE_HEIGHT_FIELD -> 8
        Rc3D.SURFACE_SPHERE -> 3
        Rc3D.SURFACE_CYLINDER -> 4
        else -> 6
      }
    if (params.size < needed) input.fail("params", "Surface type $type needs $needed params")
    val positions = if (type == Rc3D.SURFACE_GENERAL || type !in 1..3) 3 else 1
    if (position.size < positions) input.fail("position", "Surface type $type needs $positions")
    return RcMeshExpression3D(id, type, flags, params, position, normal, uv)
  }

  override fun encode(output: RcWireWriter, value: RcMeshExpression3D) {
    output.writeInt(value.id)
    output.writeInt(value.type)
    output.writeInt(value.flags)
    output.writeWords(value.params)
    output.writeGroup(value.position)
    output.writeGroup(value.normal)
    output.writeGroup(value.uv)
  }
}

internal object SetTexture3DCodec : RcOperationCodec<RcSetTexture3D> {
  override val spec = RcOperationSpec(Rc3DOpcodes.SET_TEXTURE_3D, "SetTexture3D")

  override fun decode(input: RcWireReader): RcSetTexture3D =
    RcSetTexture3D(input.readId("bitmapId"))

  override fun encode(output: RcWireWriter, value: RcSetTexture3D) {
    output.writeInt(value.bitmapId)
  }
}

internal object MeshPrimitive3DCodec : RcOperationCodec<RcMeshPrimitive3D> {
  override val spec = RcOperationSpec(Rc3DOpcodes.MESH_PRIMITIVE_3D, "MeshPrimitive3D")

  override fun decode(input: RcWireReader): RcMeshPrimitive3D {
    val id = input.readDeclaredId("id")
    val type = input.readInt("type")
    val segments = input.readFloatWord("segments")
    val flags = input.readInt("flags")
    val channels = input.readCount("channels", MAX_CHANNELS)
    return RcMeshPrimitive3D(
      id,
      type,
      segments,
      flags,
      List(channels) { input.readWords("channel", MAX_VERT_FLOATS) },
    )
  }

  override fun encode(output: RcWireWriter, value: RcMeshPrimitive3D) {
    output.writeInt(value.id)
    output.writeInt(value.type)
    output.writeFloatWord(value.segments)
    output.writeInt(value.flags)
    output.writeInt(value.data.size)
    value.data.forEach { output.writeWords(it) }
  }
}

internal val rc3DCodecs: List<RcOperationCodec<out Rc3DOperation>> =
  listOf(
    DefineMesh3DCodec,
    SetCamera3DCodec,
    Matrix3DOpCodec,
    DrawMesh3DCodec,
    Paint3DStateCodec,
    SetLights3DCodec,
    VectorExpressionCodec,
    MeshExpression3DCodec,
    SetTexture3DCodec,
    MeshPrimitive3DCodec,
  )

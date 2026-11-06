package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Writes one mesh as a binary glTF 2.0 (`.glb`), for a host that hands geometry to a runtime that
 * loads glTF: Android XR's SceneCore, a desktop viewer, a test fixture.
 *
 * The mesh is written in its own model space; the draw's matrices are the host's to apply as the
 * entity's pose (see [Rc3DPanelPlacement]). Positions, normals when present, texture coordinates
 * when present, and 32-bit indices. The material is unlit-looking PBR (metallic 0, rough 1) in the
 * draw's base colour, with the texture as base colour when the host supplies it encoded as PNG.
 */
public object Rc3DGltf {
  private const val GLB_MAGIC = 0x46546C67 // "glTF"
  private const val CHUNK_JSON = 0x4E4F534A // "JSON"
  private const val CHUNK_BIN = 0x004E4942 // "BIN\0"
  private const val ARRAY_BUFFER = 34962
  private const val ELEMENT_ARRAY_BUFFER = 34963
  private const val FLOAT = 5126
  private const val UNSIGNED_INT = 5125

  /**
   * [baseColorArgb] is the draw's unpremultiplied ARGB colour; [texturePng], when given, becomes
   * the base-colour texture, modulated by that colour as the player's lighting modulates texels.
   */
  public fun toGlb(
    mesh: Rc3DMeshData,
    baseColorArgb: Int = -1,
    texturePng: ByteArray? = null,
  ): ByteArray {
    val vertexCount = mesh.vertexCount
    require(vertexCount > 0 && mesh.indices.isNotEmpty()) { "glTF export: empty mesh" }
    val hasNormals = mesh.normals.size == mesh.verts.size
    val uv = mesh.uv?.takeIf { it.size == vertexCount * 2 }

    val bin = ByteBuilder()
    val views = StringBuilder()
    val accessors = StringBuilder()
    var viewCount = 0

    fun addView(bytes: Int, target: Int?): Int {
      val offset = bin.size
      if (views.isNotEmpty()) views.append(',')
      views.append("{\"buffer\":0,\"byteOffset\":$offset,\"byteLength\":$bytes")
      if (target != null) views.append(",\"target\":$target")
      views.append('}')
      return viewCount++
    }

    var accessorCount = 0
    fun addAccessor(
      view: Int,
      componentType: Int,
      count: Int,
      type: String,
      extra: String = "",
    ): Int {
      if (accessors.isNotEmpty()) accessors.append(',')
      accessors.append(
        "{\"bufferView\":$view,\"componentType\":$componentType,\"count\":$count,\"type\":\"$type\"$extra}",
      )
      return accessorCount++
    }

    // Positions, with the bounds glTF requires on POSITION.
    val lo = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    val hi = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    for (i in 0 until vertexCount) {
      for (k in 0 until 3) {
        val v = mesh.verts[i * 3 + k]
        lo[k] = min(lo[k], v)
        hi[k] = max(hi[k], v)
      }
    }
    val positionView = addView(vertexCount * 12, ARRAY_BUFFER)
    mesh.verts.forEach(bin::float)
    val position =
      addAccessor(
        positionView,
        FLOAT,
        vertexCount,
        "VEC3",
        ",\"min\":[${lo.joinToString(",") { num(it) }}],\"max\":[${hi.joinToString(",") { num(it) }}]",
      )

    var normal = -1
    if (hasNormals) {
      val view = addView(vertexCount * 12, ARRAY_BUFFER)
      mesh.normals.forEach(bin::float)
      normal = addAccessor(view, FLOAT, vertexCount, "VEC3")
    }

    var texcoord = -1
    if (uv != null) {
      val view = addView(vertexCount * 8, ARRAY_BUFFER)
      // The player's v runs bottom-up, as OpenGL's does; glTF's runs top-down.
      for (i in 0 until vertexCount) {
        bin.float(uv[i * 2])
        bin.float(1f - uv[i * 2 + 1])
      }
      texcoord = addAccessor(view, FLOAT, vertexCount, "VEC2")
    }

    val indexView = addView(mesh.indices.size * 4, ELEMENT_ARRAY_BUFFER)
    mesh.indices.forEach(bin::int)
    val indices = addAccessor(indexView, UNSIGNED_INT, mesh.indices.size, "SCALAR")

    var imageJson = ""
    var textureJson = ""
    var textureRef = ""
    if (texturePng != null && texcoord >= 0) {
      bin.align()
      val view = addView(texturePng.size, null)
      bin.bytes(texturePng)
      imageJson = ",\"images\":[{\"bufferView\":$view,\"mimeType\":\"image/png\"}]"
      textureJson = ",\"textures\":[{\"source\":0}]"
      textureRef = ",\"baseColorTexture\":{\"index\":0}"
    }
    bin.align()

    val attributes = buildList {
      add("\"POSITION\":$position")
      if (normal >= 0) add("\"NORMAL\":$normal")
      if (texcoord >= 0) add("\"TEXCOORD_0\":$texcoord")
    }
    val color = linearColor(baseColorArgb).joinToString(",") { num(it) }
    val json =
      "{\"asset\":{\"version\":\"2.0\",\"generator\":\"rc-player-3d\"}," +
        "\"scene\":0,\"scenes\":[{\"nodes\":[0]}],\"nodes\":[{\"mesh\":0}]," +
        "\"meshes\":[{\"primitives\":[{\"attributes\":{${attributes.joinToString(",")}}," +
        "\"indices\":$indices,\"material\":0,\"mode\":4}]}]," +
        "\"materials\":[{\"pbrMetallicRoughness\":{\"baseColorFactor\":[$color]," +
        "\"metallicFactor\":0,\"roughnessFactor\":1$textureRef}" +
        (if ((baseColorArgb ushr 24) < 255) ",\"alphaMode\":\"BLEND\"" else "") +
        "}]," +
        "\"buffers\":[{\"byteLength\":${bin.size}}]," +
        "\"bufferViews\":[$views],\"accessors\":[$accessors]$imageJson$textureJson}"

    val jsonBytes = json.encodeToByteArray()
    val jsonPadded = (jsonBytes.size + 3) / 4 * 4
    val total = 12 + 8 + jsonPadded + 8 + bin.size
    val out = ByteBuilder()
    out.int(GLB_MAGIC)
    out.int(2)
    out.int(total)
    out.int(jsonPadded)
    out.int(CHUNK_JSON)
    out.bytes(jsonBytes)
    repeat(jsonPadded - jsonBytes.size) { out.byte(0x20) } // JSON pads with spaces
    out.int(bin.size)
    out.int(CHUNK_BIN)
    out.bytes(bin.toByteArray())
    return out.toByteArray()
  }

  /** sRGB ARGB to glTF's linear RGBA base colour factor. */
  private fun linearColor(argb: Int): FloatArray {
    fun channel(c: Int): Float {
      val s = c / 255.0
      return (if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)).toFloat()
    }
    return floatArrayOf(
      channel((argb shr 16) and 0xFF),
      channel((argb shr 8) and 0xFF),
      channel(argb and 0xFF),
      ((argb ushr 24) and 0xFF) / 255f,
    )
  }

  /** A JSON number for [v]; JSON has no NaN or infinity. */
  private fun num(v: Float): String = if (v.isFinite()) v.toString() else "0"

  private class ByteBuilder {
    private var data = ByteArray(256)
    var size: Int = 0
      private set

    private fun ensure(extra: Int) {
      if (size + extra > data.size) data = data.copyOf(maxOf(data.size * 2, size + extra))
    }

    fun byte(b: Int) {
      ensure(1)
      data[size++] = b.toByte()
    }

    /** Little-endian, as glTF requires. */
    fun int(v: Int) {
      ensure(4)
      data[size++] = v.toByte()
      data[size++] = (v shr 8).toByte()
      data[size++] = (v shr 16).toByte()
      data[size++] = (v shr 24).toByte()
    }

    fun float(v: Float) = int(v.toRawBits())

    fun bytes(b: ByteArray) {
      ensure(b.size)
      b.copyInto(data, size)
      size += b.size
    }

    /** Pad to four bytes, which glTF requires between views and at the chunk's end. */
    fun align() {
      while (size % 4 != 0) byte(0)
    }

    fun toByteArray(): ByteArray = data.copyOf(size)
  }
}

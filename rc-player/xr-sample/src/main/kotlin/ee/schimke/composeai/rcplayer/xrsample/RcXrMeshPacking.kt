package ee.schimke.composeai.rcplayer.xrsample

import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import kotlin.math.sqrt

/**
 * A mesh laid out the way SceneCore's `CustomMesh` takes it: one interleaved vertex stream of
 * position and normal (and texture coordinate when the mesh has one), and 32-bit indices.
 *
 * Kept free of Android and SceneCore types so the layout is unit-tested on the JVM.
 */
internal class RcXrPackedMesh(
  /** `x, y, z, nx, ny, nz[, u, v]` per vertex. */
  val vertices: FloatArray,
  val indices: IntArray,
  val hasUv: Boolean,
  val boundsMin: FloatArray,
  val boundsMax: FloatArray,
) {
  /** Floats per vertex. */
  val stride: Int
    get() = if (hasUv) 8 else 6

  val vertexCount: Int
    get() = vertices.size / stride
}

internal object RcXrMeshPacking {
  fun pack(mesh: Rc3DMeshData): RcXrPackedMesh {
    val count = mesh.vertexCount
    require(count > 0 && mesh.indices.isNotEmpty()) { "Empty mesh" }
    val normals = if (mesh.normals.size == mesh.verts.size) mesh.normals else vertexNormals(mesh)
    val uv = mesh.uv?.takeIf { it.size == count * 2 }
    val stride = if (uv != null) 8 else 6
    val out = FloatArray(count * stride)
    val lo = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    val hi = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    for (i in 0 until count) {
      val o = i * stride
      for (k in 0 until 3) {
        val v = mesh.verts[i * 3 + k]
        out[o + k] = v
        out[o + 3 + k] = normals[i * 3 + k]
        if (v < lo[k]) lo[k] = v
        if (v > hi[k]) hi[k] = v
      }
      if (uv != null) {
        out[o + 6] = uv[i * 2]
        // The player's v runs bottom-up, as OpenGL's does; SceneCore's, like glTF's, top-down.
        out[o + 7] = 1f - uv[i * 2 + 1]
      }
    }
    return RcXrPackedMesh(out, mesh.indices.copyOf(), uv != null, lo, hi)
  }

  /**
   * Area-weighted vertex normals for a mesh that carries none. The player shades such a mesh flat
   * from each face's own normal; a lit runtime needs normals per vertex, and averaging the faces
   * that share a vertex is the closest smooth equivalent.
   */
  fun vertexNormals(mesh: Rc3DMeshData): FloatArray {
    val v = mesh.verts
    val n = FloatArray(v.size)
    val idx = mesh.indices
    for (t in 0 until idx.size / 3) {
      val a = idx[t * 3] * 3
      val b = idx[t * 3 + 1] * 3
      val c = idx[t * 3 + 2] * 3
      val ux = v[b] - v[a]
      val uy = v[b + 1] - v[a + 1]
      val uz = v[b + 2] - v[a + 2]
      val wx = v[c] - v[a]
      val wy = v[c + 1] - v[a + 1]
      val wz = v[c + 2] - v[a + 2]
      // Not normalised: the cross product's length is twice the face's area, which weights it.
      val fx = uy * wz - uz * wy
      val fy = uz * wx - ux * wz
      val fz = ux * wy - uy * wx
      for (corner in intArrayOf(a, b, c)) {
        n[corner] += fx
        n[corner + 1] += fy
        n[corner + 2] += fz
      }
    }
    for (i in 0 until n.size / 3) {
      val x = n[i * 3]
      val y = n[i * 3 + 1]
      val z = n[i * 3 + 2]
      val len = sqrt(x * x + y * y + z * z)
      if (len > 1e-12f) {
        n[i * 3] = x / len
        n[i * 3 + 1] = y / len
        n[i * 3 + 2] = z / len
      } else {
        n[i * 3 + 1] = 1f
      }
    }
    return n
  }

  /** The sRGB ARGB colour as linear RGBA, which a PBR base-colour factor expects. */
  fun linearRgba(argb: Int): FloatArray {
    fun channel(c: Int): Float {
      val s = c / 255.0
      return (if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)).toFloat()
    }
    return floatArrayOf(
      channel((argb shr 16) and 0xFF),
      channel((argb shr 8) and 0xFF),
      channel(argb and 0xFF),
      ((argb ushr 24) and 0xFF) / 255f,
    )
  }
}

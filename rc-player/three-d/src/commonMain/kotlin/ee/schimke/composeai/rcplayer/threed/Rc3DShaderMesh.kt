package ee.schimke.composeai.rcplayer.threed

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D

/**
 * Draws a mesh with the projection and lighting in a vertex shader, from geometry uploaded once:
 * `android.graphics.Mesh` on Android 14 and later. Where the platform has no such API, or this
 * canvas cannot take it, [draw] returns false and the caller uses the CPU-transformed vertex path.
 *
 * There is no depth buffer, so a caller hands it only meshes whose order cannot matter: convex
 * ones, whose visible faces never overlap. Back faces are culled in the shader.
 */
internal expect class RcShaderMeshDrawer() {
  /**
   * Whether this platform and [scope]'s canvas can take a draw at all: checked before the caller
   * spends anything, such as a convexity test, on a draw that would only be declined.
   */
  fun canDraw(scope: DrawScope): Boolean

  /**
   * Draw [mesh] as [engine]'s camera, model matrix, lights and material set it, onto a viewport of
   * [width] by [height] in [scope]'s units. [slot] tells apart the draws of one frame, which must
   * not share uniforms.
   */
  fun draw(
    scope: DrawScope,
    width: Float,
    height: Float,
    mesh: Rc3DMeshData,
    engine: RcSoftware3D,
    smooth: Boolean,
    baseColor: Int,
    texture: ImageBitmap?,
    slot: Int,
  ): Boolean

  fun dispose()
}

/**
 * A mesh laid out for the shader: three vertices per triangle, unshared, so each carries its face's
 * data and the shader can cull and flat-shade without seeing the other two.
 */
internal class Rc3DShaderMeshData(val mesh: Rc3DMeshData) {
  /** [FLOATS_PER_VERTEX] floats per vertex: see the offsets below. */
  val vertices: FloatArray
  val vertexCount: Int
  val hasUv: Boolean
  val hasNormals: Boolean
  private val boundsMin = FloatArray(3) { Float.POSITIVE_INFINITY }
  private val boundsMax = FloatArray(3) { Float.NEGATIVE_INFINITY }

  init {
    val v = mesh.verts
    val n = mesh.normals
    val uv = mesh.uv
    val idx = mesh.indices
    hasUv = uv != null && uv.isNotEmpty()
    hasNormals = n.isNotEmpty()
    val out = FloatArray((idx.size / 3) * 3 * FLOATS_PER_VERTEX)
    var count = 0
    for (t in 0 until idx.size - 2 step 3) {
      val i0 = idx[t]
      val i1 = idx[t + 1]
      val i2 = idx[t + 2]
      if (!valid(i0, v) || !valid(i1, v) || !valid(i2, v)) continue
      val e1x = v[i1 * 3] - v[i0 * 3]
      val e1y = v[i1 * 3 + 1] - v[i0 * 3 + 1]
      val e1z = v[i1 * 3 + 2] - v[i0 * 3 + 2]
      val e2x = v[i2 * 3] - v[i0 * 3]
      val e2y = v[i2 * 3 + 1] - v[i0 * 3 + 1]
      val e2z = v[i2 * 3 + 2] - v[i0 * 3 + 2]
      val cx = (v[i0 * 3] + v[i1 * 3] + v[i2 * 3]) / 3f
      val cy = (v[i0 * 3 + 1] + v[i1 * 3 + 1] + v[i2 * 3 + 1]) / 3f
      val cz = (v[i0 * 3 + 2] + v[i1 * 3 + 2] + v[i2 * 3 + 2]) / 3f
      for (i in intArrayOf(i0, i1, i2)) {
        val o = count * FLOATS_PER_VERTEX
        for (k in 0 until 3) {
          val p = v[i * 3 + k]
          out[o + POSITION + k] = p
          if (p < boundsMin[k]) boundsMin[k] = p
          if (p > boundsMax[k]) boundsMax[k] = p
          out[o + NORMAL + k] = if (i * 3 + k < n.size) n[i * 3 + k] else 0f
        }
        out[o + EDGE1] = e1x
        out[o + EDGE1 + 1] = e1y
        out[o + EDGE1 + 2] = e1z
        out[o + EDGE2] = e2x
        out[o + EDGE2 + 1] = e2y
        out[o + EDGE2 + 2] = e2z
        out[o + CENTROID] = cx
        out[o + CENTROID + 1] = cy
        out[o + CENTROID + 2] = cz
        if (hasUv && i * 2 + 1 < uv!!.size) {
          out[o + UV] = uv[i * 2]
          out[o + UV + 1] = uv[i * 2 + 1]
        }
        count++
      }
    }
    vertexCount = count
    vertices =
      if (count * FLOATS_PER_VERTEX == out.size) out else out.copyOf(count * FLOATS_PER_VERTEX)
  }

  private fun valid(i: Int, v: FloatArray) = i >= 0 && i * 3 + 2 < v.size

  /**
   * Whether every vertex is in front of the near plane under [mvp], read from the bounding box's
   * corners. The shader cannot clip a triangle that crosses it, and the engine drops any such
   * triangle; a mesh that is not wholly in front goes through the CPU path, which does the same.
   */
  fun inFrontOfNearPlane(mvp: FloatArray): Boolean {
    if (vertexCount == 0) return true
    for (corner in 0 until 8) {
      val x = if (corner and 1 == 0) boundsMin[0] else boundsMax[0]
      val y = if (corner and 2 == 0) boundsMin[1] else boundsMax[1]
      val z = if (corner and 4 == 0) boundsMin[2] else boundsMax[2]
      val w = mvp[3] * x + mvp[7] * y + mvp[11] * z + mvp[15]
      if (w < NEAR_W) return false
    }
    return true
  }

  companion object {
    const val POSITION = 0
    const val NORMAL = 3
    const val EDGE1 = 6
    const val EDGE2 = 9
    const val CENTROID = 12
    const val UV = 15
    const val FLOATS_PER_VERTEX = 17

    /** The engine's own near limit on w. */
    const val NEAR_W = 0.0001f

    /** Lights the shader takes; a scene with more goes through the CPU path. */
    const val MAX_LIGHTS = 8

    /** Vertices past which a mesh is left to the CPU path rather than uploaded. */
    const val MAX_VERTICES = 300_000

    /** Ambient light, as the engine adds it. */
    const val AMBIENT = RcSoftware3D.AMBIENT
  }
}

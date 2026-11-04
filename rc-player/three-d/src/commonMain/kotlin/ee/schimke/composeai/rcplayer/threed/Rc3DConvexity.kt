package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Whether a mesh is a convex shell: every vertex lies on one side of every face's plane, and on the
 * same side for every face, so the faces are consistently wound. With back faces culled, such a
 * mesh's visible faces never overlap on screen, so it draws correctly in any order and needs no
 * depth buffer.
 */
internal object Rc3DConvexity {
  /** Face-vertex tests past which a mesh is not checked and is taken as not convex. */
  const val MAX_WORK: Long = 4_000_000

  fun isConvex(mesh: Rc3DMeshData): Boolean {
    val v = mesh.verts
    val idx = mesh.indices
    val vertexCount = v.size / 3
    val faces = idx.size / 3
    if (vertexCount == 0 || faces == 0) return false
    if (faces.toLong() * vertexCount > MAX_WORK) return false
    // The mesh's own size, not its distance from the origin: tolerances scale with the geometry
    // and do not grow when the same shape is authored far away.
    val min = FloatArray(3) { Float.POSITIVE_INFINITY }
    val max = FloatArray(3) { Float.NEGATIVE_INFINITY }
    for (i in v.indices) {
      val x = v[i]
      if (!x.isFinite()) return false
      val axis = i % 3
      if (x < min[axis]) min[axis] = x
      if (x > max[axis]) max[axis] = x
    }
    var extent = 0f
    for (axis in 0 until 3) extent = max(extent, max[axis] - min[axis])
    val tolerance = 1e-4f * max(extent, 1e-6f)
    val minArea = 1e-6f * extent * extent
    // A face listed twice passes every plane test but overlaps itself on screen.
    val seen = HashSet<Triple<Int, Int, Int>>(faces * 2)
    for (f in 0 until faces) {
      val i0 = idx[f * 3]
      val i1 = idx[f * 3 + 1]
      val i2 = idx[f * 3 + 2]
      val lo = minOf(i0, i1, i2)
      val hi = maxOf(i0, i1, i2)
      if (!seen.add(Triple(lo, i0 + i1 + i2 - lo - hi, hi))) return false
    }
    var side = 0
    for (f in 0 until faces) {
      val a = idx[f * 3] * 3
      val b = idx[f * 3 + 1] * 3
      val c = idx[f * 3 + 2] * 3
      if (a < 0 || b < 0 || c < 0 || a + 2 >= v.size || b + 2 >= v.size || c + 2 >= v.size) {
        return false
      }
      val ux = v[b] - v[a]
      val uy = v[b + 1] - v[a + 1]
      val uz = v[b + 2] - v[a + 2]
      val wx = v[c] - v[a]
      val wy = v[c + 1] - v[a + 1]
      val wz = v[c + 2] - v[a + 2]
      var nx = uy * wz - uz * wy
      var ny = uz * wx - ux * wz
      var nz = ux * wy - uy * wx
      val length = sqrt(nx * nx + ny * ny + nz * nz)
      // Degenerate, or so thin (a sphere's pole slivers) that its plane is noise: no side to be on.
      if (length < minArea) continue
      nx /= length
      ny /= length
      nz /= length
      var above = false
      var below = false
      for (i in 0 until vertexCount) {
        val p = i * 3
        val d = nx * (v[p] - v[a]) + ny * (v[p + 1] - v[a + 1]) + nz * (v[p + 2] - v[a + 2])
        if (d > tolerance) above = true else if (d < -tolerance) below = true
        if (above && below) return false
      }
      // Every face must also be oriented the same way: the rest of the mesh behind each face (an
      // outward-wound shell) or in front of each (inward-wound). Two parallel triangles facing
      // the same way pass each face's own test but overlap on screen.
      val faceSide = if (above) 1 else if (below) -1 else 0
      if (faceSide != 0) {
        if (side == 0) side = faceSide else if (side != faceSide) return false
      }
    }
    return true
  }
}

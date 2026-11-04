package ee.schimke.composeai.rcplayer.threed.engine

/**
 * Indexed triangle geometry in model space, as `DEFINE_MESH_3D` carries it and as the parametric
 * generators produce it.
 *
 * [verts] and [normals] are `x,y,z` triples, [indices] a triangle list wound counter-clockwise from
 * outside, and [uv] `u,v` pairs with `(0,0)` at the top left of the texture. [normals] is empty
 * when the mesh has none (flat shading then derives a face normal) and [uv] is null when the mesh
 * is untextured.
 */
public class Rc3DMeshData(
  public val verts: FloatArray,
  public val normals: FloatArray,
  public val indices: IntArray,
  public val uv: FloatArray? = null,
) {
  public val vertexCount: Int
    get() = verts.size / 3

  public val triangleCount: Int
    get() = indices.size / 3
}

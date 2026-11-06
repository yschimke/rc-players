package ee.schimke.composeai.rcplayer.threed.engine

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RcPrimitive3DTest {
  private class Case(
    val name: String,
    val type: Int,
    val segments: Float,
    val flags: Int,
    val data: List<FloatArray>,
  )

  private fun f(vararg v: Float): FloatArray = v

  private val tubePath =
    f(
      -1.6f,
      -0.9f,
      0.4f,
      -0.7f,
      0.5f,
      -0.5f,
      0.1f,
      -0.6f,
      0.6f,
      0.9f,
      0.6f,
      -0.3f,
      1.6f,
      -0.2f,
      0.5f,
    )

  private val sweepXsec =
    f(0.3f, 0f, 0.15f, 0.26f, -0.15f, 0.26f, -0.3f, 0f, -0.15f, -0.26f, 0.15f, -0.26f)
  private val sweepPath =
    f(-1.4f, -0.7f, 0f, -0.7f, 0.1f, 0.4f, 0f, 0.6f, 0f, 0.7f, 0.1f, -0.4f, 1.4f, -0.7f, 0f)

  // Parameters taken from the 3d-oracle `p_*.txt` parity scenes (`mesh <id> prim <type> <segs>
  // <flags> <channels...>`), so every builder runs on data the reference renders.
  private val cases =
    listOf(
      Case("sphere", RcPrimitive3D.SPHERE, 16f, 0, listOf(f(1f, 0f, 0f, 0f))),
      Case("sphere_uv", RcPrimitive3D.SPHERE, 16f, 16, listOf(f(1f, 0f, 0f, 0f))),
      Case("cylinder", RcPrimitive3D.CYLINDER, 16f, 0, listOf(f(0.7f, 0f, -1f, 0f, 0f, 1f, 0f))),
      Case("cone", RcPrimitive3D.CONE, 16f, 0, listOf(f(0.9f, 1.6f, 0f, -0.8f, 0f))),
      Case("cube", RcPrimitive3D.CUBE, 0f, 0, listOf(f(1.4f, 1.4f, 1.4f, 0f, 0f, 0f))),
      Case("cube_uv", RcPrimitive3D.CUBE, 0f, 16, listOf(f(1.4f, 1.4f, 1.4f, 0f, 0f, 0f))),
      Case(
        "roundedcube",
        RcPrimitive3D.ROUNDED_CUBE,
        5f,
        0,
        listOf(f(1.6f, 1.6f, 1.6f, 0f, 0f, 0f, 0.35f)),
      ),
      Case("sector", RcPrimitive3D.SPHERICAL_SECTOR, 20f, 0, listOf(f(1.2f, 1.0f, 0f, 0f, 0f))),
      Case("dome", RcPrimitive3D.SPHERICAL_DOME, 20f, 0, listOf(f(1.2f, 1.5708f, 0f, -0.3f, 0f))),
      Case("tube", RcPrimitive3D.TUBE, 12f, 0, listOf(f(0.28f, *tubePath))),
      Case("tube_legacy", RcPrimitive3D.TUBE, 12f, 2, listOf(f(0.28f, *tubePath))),
      Case("tube_catmull", RcPrimitive3D.CAP_TUBE, 12f, 3, listOf(f(0.28f, *tubePath))),
      Case("tube_density", RcPrimitive3D.TUBE, 12f, 4, listOf(f(0.28f, 6f, *tubePath))),
      Case("captube", RcPrimitive3D.CAP_TUBE, 12f, 0, listOf(f(0.28f, *tubePath))),
      Case(
        "profiletube",
        RcPrimitive3D.PROFILE_TUBE,
        12f,
        8,
        listOf(
          f(
            5f,
            -1.5f,
            -0.8f,
            0f,
            -0.7f,
            0f,
            0.3f,
            0.1f,
            0.5f,
            -0.2f,
            0.9f,
            0.2f,
            0.4f,
            1.6f,
            -0.5f,
            0f,
            0.10f,
            0.42f,
            0.18f,
            0.50f,
            0.08f,
          ),
        ),
      ),
      Case("torus", RcPrimitive3D.TORUS, 20f, 0, listOf(f(1.0f, 0.35f, 0f, 0f, 0f))),
      Case("torus_uv", RcPrimitive3D.TORUS, 20f, 16, listOf(f(1.0f, 0.35f, 0f, 0f, 0f))),
      Case("plane", RcPrimitive3D.PLANE, 0f, 0, listOf(f(1.8f, 1.4f, 0f, 0f, 0f))),
      Case("plane_uv", RcPrimitive3D.PLANE, 0f, 16, listOf(f(1.8f, 1.4f, 0f, 0f, 0f))),
      Case("excircle", RcPrimitive3D.EXTRUDE_CIRCLE, 20f, 0, listOf(f(1.0f, 0.5f, 0f, 0f, 0f))),
      Case(
        "exsector",
        RcPrimitive3D.EXTRUDE_SECTOR,
        12f,
        0,
        listOf(f(1.2f, 0.3f, 2.2f, 0.5f, 0f, 0f, 0f)),
      ),
      Case(
        "exsegment",
        RcPrimitive3D.EXTRUDE_SEGMENT,
        12f,
        0,
        listOf(f(1.2f, 0.4f, 2.6f, 0.5f, 0f, 0f, 0f)),
      ),
      Case(
        "exarc",
        RcPrimitive3D.EXTRUDE_ARC,
        14f,
        0,
        listOf(f(0.6f, 1.2f, 0.2f, 3.0f, 0.5f, 0f, 0f, 0f)),
      ),
      Case(
        "exroundrect",
        RcPrimitive3D.EXTRUDE_ROUNDED_RECT,
        5f,
        0,
        listOf(f(1.8f, 1.2f, 0.35f, 0.5f, 0f, 0f, 0f)),
      ),
      Case(
        "exsquircle",
        RcPrimitive3D.EXTRUDE_SQUIRCLE,
        32f,
        0,
        listOf(f(1.1f, 4.0f, 0.5f, 0f, 0f, 0f)),
      ),
      Case(
        "expath_concave",
        RcPrimitive3D.EXTRUDE_PATH,
        0f,
        0,
        listOf(
          f(
            0.5f,
            0f,
            0f,
            0f,
            0f,
            1f,
            6f,
            -1.0f,
            -1.0f,
            0.4f,
            -1.0f,
            0.4f,
            0.0f,
            1.0f,
            0.0f,
            1.0f,
            1.0f,
            -1.0f,
            1.0f,
          ),
        ),
      ),
      Case(
        "expath_hole",
        RcPrimitive3D.EXTRUDE_PATH,
        0f,
        0,
        listOf(
          f(
            0.5f,
            0f,
            0f,
            0f,
            0f,
            2f,
            4f,
            -1.1f,
            -1.1f,
            1.1f,
            -1.1f,
            1.1f,
            1.1f,
            -1.1f,
            1.1f,
            4f,
            -0.45f,
            -0.45f,
            -0.45f,
            0.45f,
            0.45f,
            0.45f,
            0.45f,
            -0.45f,
          ),
        ),
      ),
      Case(
        "expath_bevel",
        RcPrimitive3D.EXTRUDE_PATH,
        0f,
        0,
        listOf(
          f(
            0.6f,
            0f,
            0f,
            0f,
            0.12f,
            2f,
            4f,
            -1.1f,
            -1.1f,
            1.1f,
            -1.1f,
            1.1f,
            1.1f,
            -1.1f,
            1.1f,
            4f,
            -0.45f,
            -0.45f,
            -0.45f,
            0.45f,
            0.45f,
            0.45f,
            0.45f,
            -0.45f,
          ),
        ),
      ),
      Case(
        "lathe",
        RcPrimitive3D.LATHE,
        20f,
        0,
        listOf(
          f(0f, 0f, 0f),
          f(
            0.0f,
            -1.2f,
            0.55f,
            -1.2f,
            0.62f,
            -0.8f,
            0.42f,
            -0.2f,
            0.38f,
            0.2f,
            0.55f,
            0.7f,
            0.75f,
            1.05f,
            0.72f,
            1.2f,
            0.0f,
            1.2f,
          ),
        ),
      ),
      Case(
        "sweep_ribbon",
        RcPrimitive3D.SWEEP,
        0f,
        0,
        listOf(f(0f, 0f, 0f, 0f, 0f, 0f), sweepXsec, sweepPath),
      ),
      Case(
        "sweep",
        RcPrimitive3D.SWEEP,
        0f,
        0,
        listOf(f(1f, 1f, 1f, 0f, 0f, 0f), sweepXsec, sweepPath),
      ),
      Case(
        "sweep_twist",
        RcPrimitive3D.SWEEP,
        0f,
        0,
        listOf(
          f(1f, 1f, 1f, 0f, 0f, 0f),
          sweepXsec,
          sweepPath,
          f(0.5f, 0.9f, 1.4f, 0.9f, 0.5f),
          f(0.0f, 0.5f, 1.0f, 1.5f, 2.0f),
        ),
      ),
      Case("helix", RcPrimitive3D.HELIX, 8f, 0, listOf(f(0.9f, 0.16f, 0.7f, 2.5f, 0f, 0f, 0f))),
      Case("icosphere", RcPrimitive3D.ICOSPHERE, 2f, 0, listOf(f(1.1f, 0f, 0f, 0f))),
    )

  @Test
  fun everyTypeIsCovered() {
    val covered = cases.map { it.type }.toSet()
    assertEquals((0..22).toSet(), covered)
  }

  @Test
  fun everyPrimitiveSatisfiesMeshInvariants() {
    for (c in cases) {
      val m = RcPrimitive3D.build(c.type, c.segments, c.flags, c.data)
      checkMesh(c.name, m)
      if ((c.flags and RcPrimitive3D.FLAG_UV_MASK) != 0) {
        assertNotNull(m.uv, "${c.name}: uv requested")
      } else {
        assertNull(m.uv, "${c.name}: uv not requested")
      }
    }
  }

  @Test
  fun defaultSegmentsAlsoBuild() {
    for (c in cases) {
      checkMesh("${c.name}@default", RcPrimitive3D.build(c.type, 0f, c.flags, c.data))
    }
  }

  private fun checkMesh(name: String, m: Rc3DMeshData) {
    assertTrue(m.verts.isNotEmpty(), "$name: no vertices")
    assertEquals(0, m.verts.size % 3, "$name: verts not xyz triples")
    assertTrue(m.indices.isNotEmpty(), "$name: no indices")
    assertEquals(0, m.indices.size % 3, "$name: indices not triangles")
    val vc = m.vertexCount
    for (i in m.indices) {
      if (i < 0 || i >= vc) fail("$name: index $i out of range 0 until $vc")
    }
    for (v in m.verts) assertTrue(!v.isNaN(), "$name: NaN vertex")
    // Every generator emits per-vertex normals.
    assertEquals(m.verts.size, m.normals.size, "$name: normals per vertex")
    var k = 0
    while (k < m.normals.size) {
      val nx = m.normals[k]
      val ny = m.normals[k + 1]
      val nz = m.normals[k + 2]
      assertTrue(!nx.isNaN() && !ny.isNaN() && !nz.isNaN(), "$name: NaN normal")
      val len = sqrt(nx * nx + ny * ny + nz * nz)
      assertTrue(abs(len - 1f) <= 1e-3f, "$name: normal ${k / 3} has length $len")
      k += 3
    }
    val uv = m.uv
    if (uv != null) {
      assertEquals(vc * 2, uv.size, "$name: uv size")
      for (t in uv) assertTrue(!t.isNaN(), "$name: NaN uv")
    }
  }

  @Test
  fun sphereVertexCountFollowsTheReferenceFormula() {
    // slices = segments, stacks = max(2, round(slices / 2)); (stacks+1)*(slices+1) vertices.
    val m = RcPrimitive3D.build(RcPrimitive3D.SPHERE, 16f, 0, listOf(f(1f, 0f, 0f, 0f)))
    assertEquals(9 * 17, m.vertexCount)
    assertEquals(8 * 16 * 2, m.triangleCount)
    val d = RcPrimitive3D.build(RcPrimitive3D.SPHERE, 0f, 0, listOf(f(1f, 0f, 0f, 0f)))
    assertEquals(13 * 25, d.vertexCount)
    // Odd slices round half away from zero: 7 * 0.5 = 3.5 -> 4 stacks.
    val o = RcPrimitive3D.build(RcPrimitive3D.SPHERE, 7f, 0, listOf(f(1f, 0f, 0f, 0f)))
    assertEquals(5 * 8, o.vertexCount)
    // The first quad keeps the reference order: meridian, parallel, diagonal last.
    val cols = 17
    assertEquals(listOf(1, cols + 1, cols, cols, 0, 1), m.indices.take(6))
    // North pole row sits at +radius.
    assertEquals(1f, m.verts[1])
  }

  @Test
  fun icosphereCountsMatchSubdivision() {
    val m = RcPrimitive3D.build(RcPrimitive3D.ICOSPHERE, 2f, 0, listOf(f(1f)))
    assertEquals(162, m.vertexCount)
    assertEquals(320, m.triangleCount)
  }

  @Test
  fun badInputThrowsIllegalArgument() {
    assertFailsWith<IllegalArgumentException> { RcPrimitive3D.build(99, 0f, 0, listOf(f(1f))) }
    assertFailsWith<IllegalArgumentException> {
      RcPrimitive3D.build(RcPrimitive3D.TUBE, 0f, 0, listOf(f(0.2f, 0f, 0f, 0f)))
    }
    assertFailsWith<IllegalArgumentException> {
      RcPrimitive3D.build(RcPrimitive3D.LATHE, 0f, 0, listOf(f(0f, 0f, 0f)))
    }
    assertFailsWith<IllegalArgumentException> {
      RcPrimitive3D.build(RcPrimitive3D.SWEEP, 0f, 0, listOf(f(), f(0f, 0f), f(0f, 0f, 0f)))
    }
    assertFailsWith<IllegalArgumentException> {
      RcPrimitive3D.build(RcPrimitive3D.CYLINDER, 0f, 0, listOf(f(1f)))
    }
  }

  @Test
  fun hugeSegmentCountsAreCappedOrRefusedBeforeAllocating() {
    // A sphere asking for 10,000 segments is built at the cap, not at ~2.4 GB.
    val sphere =
      RcPrimitive3D.build(RcPrimitive3D.SPHERE, 10_000f, 0, listOf(floatArrayOf(1f, 0f, 0f, 0f)))
    assertTrue(sphere.vertexCount <= 257 * 513)
    // A torus whose minor ring count scales past the cap is refused by the budget instead.
    assertFailsWith<IllegalArgumentException> {
      RcPrimitive3D.build(
        RcPrimitive3D.TORUS,
        512f,
        0,
        listOf(floatArrayOf(0.001f, 100f, 0f, 0f, 0f)),
      )
    }
  }

  @Test
  fun anExtrudePathContourLongerThanItsPayloadIsRefusedBeforeAllocating() {
    // Seven floats claiming a hundred-million-point contour.
    assertFailsWith<IllegalArgumentException> {
      RcPrimitive3D.build(
        RcPrimitive3D.EXTRUDE_PATH,
        0f,
        0,
        listOf(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 1e8f)),
      )
    }
  }

  @Test
  fun aProfileTubeWhosePointCountOverflowsIsRefused() {
    // 3 * 1e9 overflows Int; the count is checked in Long against the payload.
    assertFailsWith<IllegalArgumentException> {
      RcPrimitive3D.build(RcPrimitive3D.PROFILE_TUBE, 0f, 0, listOf(floatArrayOf(1e9f)))
    }
  }

  @Test
  fun aDenseTubePastTheBudgetIsRefusedBeforeSampling() {
    // 50,000 points at 256 rings per span: within the channel limit, far past the budget.
    val points = 50_000
    val params = FloatArray(2 + points * 3)
    params[0] = 0.1f
    params[1] = 256f
    for (i in 0 until points) params[2 + i * 3] = i.toFloat()
    assertFailsWith<IllegalArgumentException> {
      RcPrimitive3D.build(
        RcPrimitive3D.TUBE,
        0f,
        RcPrimitive3D.FLAG_TUBE_PATH_DENSITY,
        listOf(params),
      )
    }
  }

  @Test
  fun manyStaggeredExtrudePathContoursAreRefusedBeforeTheScanExplodes() {
    // 10,000 disjoint rectangles [3i, 3i+1] x [i, 10000+i]: every band crosses thousands of them.
    val n = 10_000
    val p = FloatArray(6 + n * 9)
    p[0] = 1f
    p[5] = n.toFloat()
    var k = 6
    for (i in 0 until n) {
      val x0 = 3f * i
      val x1 = x0 + 1f
      val y0 = i.toFloat()
      val y1 = 10_000f + i
      p[k++] = 4f
      for (v in floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)) p[k++] = v
    }
    assertFailsWith<IllegalArgumentException> {
      RcPrimitive3D.build(RcPrimitive3D.EXTRUDE_PATH, 0f, 0, listOf(p))
    }
  }

  @Test
  fun manyCrossingsInOneBandAreRefusedQuickly() {
    // 20,000 equal-height rectangles listed right to left: 40,000 crossings in a single band.
    val n = 20_000
    val p = FloatArray(6 + n * 9)
    p[0] = 1f
    p[5] = n.toFloat()
    var k = 6
    for (i in n - 1 downTo 0) {
      val x0 = 3f * i
      val x1 = x0 + 1f
      p[k++] = 4f
      for (v in floatArrayOf(x0, 0f, x1, 0f, x1, 1f, x0, 1f)) p[k++] = v
    }
    // Refused up front: 20,000 contours of 80,000 points is past the work budget for the hole
    // tests, and is refused before the scan's sort or the walls run at all.
    val start = kotlin.time.TimeSource.Monotonic.markNow()
    assertFailsWith<IllegalArgumentException> {
      RcPrimitive3D.build(RcPrimitive3D.EXTRUDE_PATH, 0f, 0, listOf(p))
    }
    assertTrue(start.elapsedNow().inWholeMilliseconds < 1_000, "refused before the quadratic work")
  }

  @Test
  fun aWideSingleBandWithinTheBudgetSortsQuickly() {
    // 2,000 rectangles right to left, 4,000 crossings in one band: within budget, and the
    // stable sort keeps it fast where an insertion sort would shift millions of times.
    val n = 2_000
    val p = FloatArray(6 + n * 9)
    p[0] = 1f
    p[5] = n.toFloat()
    var k = 6
    for (i in n - 1 downTo 0) {
      val x0 = 3f * i
      p[k++] = 4f
      for (v in floatArrayOf(x0, 0f, x0 + 1f, 0f, x0 + 1f, 1f, x0, 1f)) p[k++] = v
    }
    val mesh = RcPrimitive3D.build(RcPrimitive3D.EXTRUDE_PATH, 0f, 0, listOf(p))
    // Each rectangle: 2 cap quads (4 triangles) and 4 wall quads (8 triangles).
    assertEquals(n * 12, mesh.triangleCount)
  }
}

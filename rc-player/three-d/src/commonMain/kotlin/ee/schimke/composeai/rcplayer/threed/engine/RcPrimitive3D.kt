package ee.schimke.composeai.rcplayer.threed.engine

import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.lround
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3DMath.segCount
import kotlin.math.PI
import kotlin.math.abs

/**
 * Parametric mesh generators for `MESH_PRIMITIVE_3D`, a float-for-float port of the reference
 * `Primitive3D.java` (by way of the rccore C++ port).
 *
 * A document names a shape instead of shipping its vertices, so a sphere is ~40 bytes on the wire
 * instead of ~20 KB, and because the parameters may be expressions the shape can be driven and
 * rebuilt from them.
 *
 * Winding is CCW-from-outside throughout. The triangle *order* inside each quad is deliberate: each
 * triple lists its two grid edges first and closes on the diagonal, which is what makes the
 * wireframe edge-selection bits draw meridians, parallels or the full grid rather than an arbitrary
 * subset. Do not "tidy" it.
 */
public object RcPrimitive3D {
  public const val SPHERE: Int = 0
  public const val CYLINDER: Int = 1
  public const val CONE: Int = 2
  public const val CUBE: Int = 3
  public const val ROUNDED_CUBE: Int = 4
  public const val SPHERICAL_SECTOR: Int = 5
  public const val SPHERICAL_DOME: Int = 6
  public const val TUBE: Int = 7
  public const val CAP_TUBE: Int = 8
  public const val PROFILE_TUBE: Int = 9
  public const val TORUS: Int = 10
  public const val PLANE: Int = 11
  public const val EXTRUDE_CIRCLE: Int = 12
  public const val EXTRUDE_SECTOR: Int = 13
  public const val EXTRUDE_SEGMENT: Int = 14
  public const val EXTRUDE_ARC: Int = 15
  public const val EXTRUDE_ROUNDED_RECT: Int = 16
  public const val EXTRUDE_SQUIRCLE: Int = 17
  public const val EXTRUDE_PATH: Int = 18
  public const val LATHE: Int = 19
  public const val SWEEP: Int = 20
  public const val HELIX: Int = 21
  public const val ICOSPHERE: Int = 22

  /** Tubes: densify the polyline with Catmull-Rom (legacy path only). */
  public const val FLAG_SPLINE: Int = 0x1
  /** Tubes: the original polyline / Catmull-Rom path instead of the monotone spline. */
  public const val FLAG_TUBE_LEGACY: Int = 0x2
  /** Tubes: an explicit rings-per-span float precedes the points. */
  public const val FLAG_TUBE_PATH_DENSITY: Int = 0x4
  /** Profile tube: cap both ends. */
  public const val FLAG_TUBE_CAP: Int = 0x8
  public const val FLAG_UV_SHIFT: Int = 4
  /** UV mode bits; non-zero asks the sphere, torus, plane and cube for texture coordinates. */
  public const val FLAG_UV_MASK: Int = 0x3 shl 4

  private const val DEFAULT_RADIAL = 24
  private const val DEFAULT_TUBE_SIDES = 14
  private const val DEFAULT_ROUND_SEG = 4

  private fun uvModeOf(flags: Int): Int = (flags and FLAG_UV_MASK) shr FLAG_UV_SHIFT

  /**
   * Dispatch a (type, segments, flags, data) tuple to the matching builder. [segments] is the
   * primary division count; <= 0 means the type's default. `data[0]` is the scalar params; channels
   * 1+ carry geometry streams for the multi-channel types.
   *
   * @throws IllegalArgumentException for an unknown [type] or parameters the shape cannot use (too
   *   few points, missing scalars).
   */
  public fun build(type: Int, segments: Float, flags: Int, data: List<FloatArray>): Rc3DMeshData {
    require(data.isNotEmpty()) { "MeshPrimitive: no parameter channel" }
    return try {
      dispatch(type, segments, flags, data)
    } catch (e: IndexOutOfBoundsException) {
      // The reference reads past a short parameter list (UB in C++, a throw in Java).
      throw IllegalArgumentException("MeshPrimitive type $type: too few parameters", e)
    }
  }

  private fun dispatch(
    type: Int,
    segments: Float,
    flags: Int,
    data: List<FloatArray>,
  ): Rc3DMeshData {
    val p = data[0]
    val uvm = uvModeOf(flags)
    val empty = FloatArray(0)
    return when (type) {
      SPHERE -> {
        val slices = segCount(segments, DEFAULT_RADIAL, 3)
        val stacks = maxOf(2, lround(slices * 0.5f))
        RcPrimitive3DSolids.sphereBand(
          p[0],
          0f,
          PI.toFloat(),
          stacks,
          slices,
          p[1],
          p[2],
          p[3],
          uvm,
        )
      }
      CYLINDER ->
        RcPrimitive3DSolids.cylinder(
          p[0],
          p[1],
          p[2],
          p[3],
          p[4],
          p[5],
          p[6],
          segCount(segments, DEFAULT_RADIAL, 3),
        )
      CONE ->
        RcPrimitive3DSolids.cone(
          p[0],
          p[1],
          p[2],
          p[3],
          p[4],
          segCount(segments, DEFAULT_RADIAL, 3),
        )
      CUBE -> RcPrimitive3DSolids.cube(p[0], p[1], p[2], p[3], p[4], p[5], uvm)
      ROUNDED_CUBE ->
        RcPrimitive3DSolids.roundedCube(
          p[0],
          p[1],
          p[2],
          p[3],
          p[4],
          p[5],
          p[6],
          segCount(segments, DEFAULT_ROUND_SEG, 1),
        )
      SPHERICAL_SECTOR -> {
        val slices = segCount(segments, DEFAULT_RADIAL, 3)
        val stacks = maxOf(2, lround(slices / 3f))
        RcPrimitive3DSolids.sphericalSector(p[0], p[1], p[2], p[3], p[4], slices, stacks)
      }
      SPHERICAL_DOME -> {
        val slices = segCount(segments, DEFAULT_RADIAL, 3)
        val stacks = maxOf(2, lround(slices / 3f))
        RcPrimitive3DSolids.sphericalDome(p[0], p[1], p[2], p[3], p[4], slices, stacks)
      }
      TUBE -> RcPrimitive3DSweep.tube(p, false, segments, flags)
      CAP_TUBE -> RcPrimitive3DSweep.tube(p, true, segments, flags)
      PROFILE_TUBE ->
        RcPrimitive3DSweep.profileTube(p, segCount(segments, DEFAULT_TUBE_SIDES, 3), flags)
      TORUS -> {
        val majorR = p[0]
        val minorR = p[1]
        val majorSeg = segCount(segments, DEFAULT_RADIAL, 3)
        // Minor segments scale with the radius ratio so the quads stay roughly square.
        val ratio = if (abs(majorR) > 1e-6f) abs(minorR / majorR) else 0.5f
        val minorSeg = maxOf(3, lround(majorSeg * ratio))
        RcPrimitive3DSolids.torus(majorR, minorR, p[2], p[3], p[4], majorSeg, minorSeg, uvm)
      }
      PLANE -> RcPrimitive3DSolids.plane(p[0], p[1], p[2], p[3], p[4], uvm)
      EXTRUDE_CIRCLE ->
        RcPrimitive3DSolids.extrudeCircle(
          p[0],
          p[1],
          p[2],
          p[3],
          p[4],
          segCount(segments, DEFAULT_RADIAL, 3),
        )
      EXTRUDE_SECTOR ->
        RcPrimitive3DSolids.extrudeSector(
          p[0],
          p[1],
          p[2],
          p[3],
          p[4],
          p[5],
          p[6],
          segCount(segments, DEFAULT_RADIAL, 1),
        )
      EXTRUDE_SEGMENT ->
        RcPrimitive3DSolids.extrudeSegment(
          p[0],
          p[1],
          p[2],
          p[3],
          p[4],
          p[5],
          p[6],
          segCount(segments, DEFAULT_RADIAL, 1),
        )
      EXTRUDE_ARC ->
        RcPrimitive3DSolids.extrudeArc(
          p[0],
          p[1],
          p[2],
          p[3],
          p[4],
          p[5],
          p[6],
          p[7],
          segCount(segments, DEFAULT_RADIAL, 1),
        )
      EXTRUDE_ROUNDED_RECT ->
        RcPrimitive3DSolids.extrudeRoundedRect(
          p[0],
          p[1],
          p[2],
          p[3],
          p[4],
          p[5],
          p[6],
          segCount(segments, 6, 1),
        )
      EXTRUDE_SQUIRCLE ->
        RcPrimitive3DSolids.extrudeSquircle(
          p[0],
          p[1],
          p[2],
          p[3],
          p[4],
          p[5],
          segCount(segments, 48, 8),
        )
      EXTRUDE_PATH -> RcPrimitive3DSweep.extrudePath(p)
      LATHE ->
        RcPrimitive3DSolids.lathe(
          if (data.size > 1) data[1] else empty,
          if (p.size > 0) p[0] else 0f,
          if (p.size > 1) p[1] else 0f,
          if (p.size > 2) p[2] else 0f,
          segCount(segments, DEFAULT_RADIAL, 3),
        )
      SWEEP ->
        RcPrimitive3DSweep.sweep(
          p,
          if (data.size > 1) data[1] else empty,
          if (data.size > 2) data[2] else empty,
          if (data.size > 3) data[3] else null,
          if (data.size > 4) data[4] else null,
        )
      HELIX ->
        RcPrimitive3DSweep.helix(
          p[0],
          p[1],
          p[2],
          p[3],
          if (p.size > 4) p[4] else 0f,
          if (p.size > 5) p[5] else 0f,
          if (p.size > 6) p[6] else 0f,
          segCount(segments, 12, 3),
        )
      ICOSPHERE ->
        RcPrimitive3DSolids.icosphere(
          p[0],
          if (p.size > 1) p[1] else 0f,
          if (p.size > 2) p[2] else 0f,
          if (p.size > 3) p[3] else 0f,
          maxOf(0, minOf(4, if (segments > 0f) lround(segments) else 2)),
        )
      else -> throw IllegalArgumentException("MeshPrimitive: unknown type $type")
    }
  }
}

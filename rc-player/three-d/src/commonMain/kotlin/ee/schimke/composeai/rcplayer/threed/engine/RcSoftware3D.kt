package ee.schimke.composeai.rcplayer.threed.engine

import ee.schimke.composeai.rcplayer.threed.Rc3D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The 3D engine: camera, model matrix, lights, material, mesh cache and the software rasterizer.
 *
 * A port of the reference `JavaPaint3DContext` by way of the experiments repo's C++
 * `SoftwarePaint3DContext`. It owns an unpremultiplied ARGB colour buffer and a float depth buffer;
 * nothing here touches a canvas or a GPU. The host either reads [colorBuffer] and composites it
 * ([drawMesh]), or takes lit, culled, back-to-front screen-space triangles from
 * [buildScreenVertices] and rasterizes them itself, which is the GPU path.
 */
internal class RcSoftware3D {
  private class Light(
    val type: Int,
    val r: Float,
    val g: Float,
    val b: Float,
    val wx: Float,
    val wy: Float,
    val wz: Float,
    val intensity: Float,
  )

  private val proj = FloatArray(16).also(RcMatrix4::identity)
  private val view = FloatArray(16).also(RcMatrix4::identity)
  private val model = FloatArray(16).also(RcMatrix4::identity)
  private val pv = FloatArray(16)
  private val mvp = FloatArray(16)
  private val mv = FloatArray(16)
  private val meshes = mutableMapOf<Int, Rc3DMeshData>()

  var colorBuffer: IntArray = IntArray(0)
    private set

  private var zbuf = FloatArray(0)

  var width: Int = 0
    private set

  var height: Int = 0
    private set

  /** Set by every write to [colorBuffer]; the host clears it once it has composited. */
  var colorDirty: Boolean = false

  var baseColorArgb: Int = -1

  private val lights = mutableListOf<Light>()
  private var lightsTouched = false
  private var numEyeLights = 0
  private var eType = IntArray(0)
  private var elx = FloatArray(0)
  private var ely = FloatArray(0)
  private var elz = FloatArray(0)
  private var elr = FloatArray(0)
  private var elg = FloatArray(0)
  private var elb = FloatArray(0)

  private val scratch4 = FloatArray(4)
  private val lightScratch3 = FloatArray(3)
  private val normal3 = FloatArray(3)

  private var texPixels: IntArray? = null
  private var texW = 0
  private var texH = 0

  private var specStrength = 0f
  private var shininess = 32f
  private var depthBiasC = 0f
  private var depthBiasS = 0f

  private val triScreen = FloatArray(9)
  private val triColor = IntArray(3)
  private val triInvW = FloatArray(3)

  /** The current camera and model matrices, for a host that hands the scene to another renderer. */
  val projectionMatrix: FloatArray
    get() = proj.copyOf()

  val viewMatrix: FloatArray
    get() = view.copyOf()

  val modelMatrix: FloatArray
    get() = model.copyOf()

  fun mesh(id: Int): Rc3DMeshData? = meshes[id]

  /** (Re)allocate the buffers if the size changed; a resize starts transparent and far. */
  fun setSize(width: Int, height: Int) {
    if (width <= 0 || height <= 0) return
    if (colorBuffer.isEmpty() || this.width != width || this.height != height) {
      colorBuffer = IntArray(width * height)
      zbuf = FloatArray(width * height)
      this.width = width
      this.height = height
      RcRasterizer.clearDepth(zbuf)
    }
  }

  fun defineMesh(id: Int, mesh: Rc3DMeshData) {
    meshes[id] = mesh
  }

  fun setCamera(projection: Int, p: FloatArray, v: FloatArray) {
    if (projection == Rc3D.PROJECTION_PERSPECTIVE) {
      if (p.size != 4) return
      RcMatrix4.perspective(proj, p[0], p[1], p[2], p[3])
    } else {
      if (p.size != 6) return
      RcMatrix4.ortho(proj, p[0], p[1], p[2], p[3], p[4], p[5])
    }
    if (v.size != 9) return
    RcMatrix4.lookAt(view, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8])
    RcMatrix4.identity(model)
  }

  fun matrixOp(sub: Int, a: FloatArray) {
    when (sub) {
      Rc3D.M3_IDENTITY -> RcMatrix4.identity(model)
      Rc3D.M3_TRANSLATE -> if (a.size == 3) RcMatrix4.translate(model, a[0], a[1], a[2])
      Rc3D.M3_SCALE -> if (a.size == 3) RcMatrix4.scale(model, a[0], a[1], a[2])
      Rc3D.M3_ROTATE_AXIS -> if (a.size == 4) RcMatrix4.rotateAxis(model, a[0], a[1], a[2], a[3])
      Rc3D.M3_MULTIPLY -> if (a.size == 16) RcMatrix4.multiply(model, model, a)
    }
  }

  /**
   * Clear depth to far and colour to transparent, so what is behind shows where 3D draws nothing.
   */
  fun clearDepth() {
    if (zbuf.isEmpty()) return
    RcRasterizer.clearDepth(zbuf)
    colorBuffer.fill(0)
    colorDirty = false
  }

  /**
   * Clear colour to transparent and keep depth: after compositing a mesh, so the next composite
   * carries only the next mesh's pixels and never repaints an earlier mesh over 2D drawn since.
   */
  fun clearColor() {
    colorBuffer.fill(0)
    colorDirty = false
  }

  fun setLights(types: List<Int>, colors: List<Int>, params: FloatArray) {
    lights.clear()
    for (i in types.indices) {
      if (lights.size >= MAX_LIGHTS) break
      val p = i * 4
      if (p + 3 >= params.size) break
      val argb = colors[i]
      lights +=
        Light(
          types[i],
          ((argb shr 16) and 0xFF) * (1f / 255f),
          ((argb shr 8) and 0xFF) * (1f / 255f),
          (argb and 0xFF) * (1f / 255f),
          params[p],
          params[p + 1],
          params[p + 2],
          params[p + 3],
        )
    }
    lightsTouched = true
  }

  fun setMaterial(specStrength: Float, shininess: Float) {
    this.specStrength = specStrength
    this.shininess = if (shininess > 0f) shininess else 1f
  }

  fun setDepthBias(constant: Float, slope: Float) {
    depthBiasC = constant
    depthBiasS = slope
  }

  /** Texture pixels as unpremultiplied ARGB; null clears the texture. */
  fun setTexture(pixels: IntArray?, w: Int, h: Int) {
    texPixels = pixels?.takeIf { w > 0 && h > 0 && it.size >= w * h }
    texW = w
    texH = h
  }

  val hasTexture: Boolean
    get() = texPixels != null

  private fun ensureEyeLightCapacity(n: Int) {
    if (eType.size < n) {
      eType = IntArray(n)
      elx = FloatArray(n)
      ely = FloatArray(n)
      elz = FloatArray(n)
      elr = FloatArray(n)
      elg = FloatArray(n)
      elb = FloatArray(n)
    }
  }

  private fun prepareLightsEyeSpace() {
    if (!lightsTouched) {
      ensureEyeLightCapacity(1)
      eType[0] = Rc3D.LIGHT_DIRECTIONAL
      elx[0] = 0f
      ely[0] = 0f
      elz[0] = 1f
      elr[0] = 1f - AMBIENT
      elg[0] = 1f - AMBIENT
      elb[0] = 1f - AMBIENT
      numEyeLights = 1
      return
    }
    val n = lights.size
    ensureEyeLightCapacity(n)
    for (i in 0 until n) {
      val l = lights[i]
      eType[i] = l.type
      if (l.type == Rc3D.LIGHT_POINT) {
        RcMatrix4.transformPoint(view, l.wx, l.wy, l.wz, scratch4)
        elx[i] = scratch4[0]
        ely[i] = scratch4[1]
        elz[i] = scratch4[2]
      } else {
        // Directional: world travel direction to eye space, stored as the unit vector to the light.
        RcMatrix4.transformDirection(view, l.wx, l.wy, l.wz, lightScratch3)
        var x = -lightScratch3[0]
        var y = -lightScratch3[1]
        var z = -lightScratch3[2]
        val len = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        if (len > 0f) {
          x /= len
          y /= len
          z /= len
        }
        elx[i] = x
        ely[i] = y
        elz[i] = z
      }
      elr[i] = l.r * l.intensity
      elg[i] = l.g * l.intensity
      elb[i] = l.b * l.intensity
    }
    numEyeLights = n
  }

  private fun litColor(
    base: Int,
    nx: Float,
    ny: Float,
    nz: Float,
    px: Float,
    py: Float,
    pz: Float,
  ): Int {
    val nlen = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
    val inv = if (nlen == 0f) 0f else 1f / nlen
    if (specStrength > 0f) return litColorSpecular(base, nx * inv, ny * inv, nz * inv, px, py, pz)
    var lr = AMBIENT
    var lg = AMBIENT
    var lb = AMBIENT
    for (i in 0 until numEyeLights) {
      var lx: Float
      var ly: Float
      var lz: Float
      if (eType[i] == Rc3D.LIGHT_POINT) {
        lx = elx[i] - px
        ly = ely[i] - py
        lz = elz[i] - pz
        val ll = sqrt((lx * lx + ly * ly + lz * lz).toDouble()).toFloat()
        if (ll > 0f) {
          lx /= ll
          ly /= ll
          lz /= ll
        }
      } else {
        lx = elx[i]
        ly = ely[i]
        lz = elz[i]
      }
      var d = (nx * lx + ny * ly + nz * lz) * inv
      if (d < 0f) d = -d // two-sided at this profile
      lr += elr[i] * d
      lg += elg[i] * d
      lb += elb[i] * d
    }
    val a = (base shr 24) and 0xFF
    var r = (((base shr 16) and 0xFF) * lr).toInt()
    var g = (((base shr 8) and 0xFF) * lg).toInt()
    var b = ((base and 0xFF) * lb).toInt()
    if (r > 255) r = 255
    if (g > 255) g = 255
    if (b > 255) b = 255
    return (a shl 24) or (r shl 16) or (g shl 8) or b
  }

  private fun litColorSpecular(
    base: Int,
    nfxIn: Float,
    nfyIn: Float,
    nfzIn: Float,
    px: Float,
    py: Float,
    pz: Float,
  ): Int {
    val vlen = sqrt((px * px + py * py + pz * pz).toDouble()).toFloat()
    var vx = 0f
    var vy = 0f
    var vz = 1f
    if (vlen > 0f) {
      vx = -px / vlen
      vy = -py / vlen
      vz = -pz / vlen
    }
    var nfx = nfxIn
    var nfy = nfyIn
    var nfz = nfzIn
    // Orient the normal toward the viewer so glossy faces light correctly from either side.
    if (nfx * vx + nfy * vy + nfz * vz < 0f) {
      nfx = -nfx
      nfy = -nfy
      nfz = -nfz
    }
    var lr = AMBIENT
    var lg = AMBIENT
    var lb = AMBIENT
    var sr = 0f
    var sg = 0f
    var sb = 0f
    for (i in 0 until numEyeLights) {
      var lx: Float
      var ly: Float
      var lz: Float
      if (eType[i] == Rc3D.LIGHT_POINT) {
        lx = elx[i] - px
        ly = ely[i] - py
        lz = elz[i] - pz
        val ll = sqrt((lx * lx + ly * ly + lz * lz).toDouble()).toFloat()
        if (ll > 0f) {
          lx /= ll
          ly /= ll
          lz /= ll
        }
      } else {
        lx = elx[i]
        ly = ely[i]
        lz = elz[i]
      }
      val ndl = nfx * lx + nfy * ly + nfz * lz
      if (ndl <= 0f) continue // facing away: no diffuse, no highlight
      lr += elr[i] * ndl
      lg += elg[i] * ndl
      lb += elb[i] * ndl
      val hx = lx + vx
      val hy = ly + vy
      val hz = lz + vz
      val hlen = sqrt((hx * hx + hy * hy + hz * hz).toDouble()).toFloat()
      if (hlen <= 0f) continue
      val ndh = (nfx * hx + nfy * hy + nfz * hz) / hlen
      if (ndh <= 0f) continue
      val spec = ndh.toDouble().pow(shininess.toDouble()).toFloat() * specStrength
      sr += elr[i] * spec
      sg += elg[i] * spec
      sb += elb[i] * spec
    }
    val a = (base shr 24) and 0xFF
    var r = (((base shr 16) and 0xFF) * lr + sr * 255f).toInt()
    var g = (((base shr 8) and 0xFF) * lg + sg * 255f).toInt()
    var b = ((base and 0xFF) * lb + sb * 255f).toInt()
    if (r > 255) r = 255
    if (g > 255) g = 255
    if (b > 255) b = 255
    return (a shl 24) or (r shl 16) or (g shl 8) or b
  }

  /**
   * Transform, cull and light triangle [t] of [m] into [triScreen] / [triColor]; false when it is
   * degenerate, behind the eye or back-facing.
   */
  private fun projectTriangle(
    m: Rc3DMeshData,
    smooth: Boolean,
    computeInvW: Boolean,
    t: Int,
    baseColor: Int,
    w: Int,
    h: Int,
  ): Boolean {
    val idx = m.indices
    val verts = m.verts
    val i0 = idx[t] * 3
    val i1 = idx[t + 1] * 3
    val i2 = idx[t + 2] * 3
    val nv = verts.size
    if (i0 < 0 || i1 < 0 || i2 < 0 || i0 + 2 >= nv || i1 + 2 >= nv || i2 + 2 >= nv) return false

    RcMatrix4.transformPoint(mvp, verts[i0], verts[i0 + 1], verts[i0 + 2], scratch4)
    val cx0 = scratch4[0]
    val cy0 = scratch4[1]
    val cz0 = scratch4[2]
    val cw0 = scratch4[3]
    RcMatrix4.transformPoint(mvp, verts[i1], verts[i1 + 1], verts[i1 + 2], scratch4)
    val cx1 = scratch4[0]
    val cy1 = scratch4[1]
    val cz1 = scratch4[2]
    val cw1 = scratch4[3]
    RcMatrix4.transformPoint(mvp, verts[i2], verts[i2 + 1], verts[i2 + 2], scratch4)
    val cx2 = scratch4[0]
    val cy2 = scratch4[1]
    val cz2 = scratch4[2]
    val cw2 = scratch4[3]

    if (cw0 < NEAR_W_EPSILON || cw1 < NEAR_W_EPSILON || cw2 < NEAR_W_EPSILON) return false
    if (computeInvW) {
      triInvW[0] = 1f / cw0
      triInvW[1] = 1f / cw1
      triInvW[2] = 1f / cw2
    }

    val nx0 = cx0 / cw0
    val ny0 = cy0 / cw0
    val nz0 = cz0 / cw0
    val nx1 = cx1 / cw1
    val ny1 = cy1 / cw1
    val nz1 = cz1 / cw1
    val nx2 = cx2 / cw2
    val ny2 = cy2 / cw2
    val nz2 = cz2 / cw2

    val sx0 = (nx0 * 0.5f + 0.5f) * w
    val sy0 = (1f - (ny0 * 0.5f + 0.5f)) * h
    var sz0 = nz0 * 0.5f + 0.5f
    val sx1 = (nx1 * 0.5f + 0.5f) * w
    val sy1 = (1f - (ny1 * 0.5f + 0.5f)) * h
    var sz1 = nz1 * 0.5f + 0.5f
    val sx2 = (nx2 * 0.5f + 0.5f) * w
    val sy2 = (1f - (ny2 * 0.5f + 0.5f)) * h
    var sz2 = nz2 * 0.5f + 0.5f

    // Back-face cull in screen space (y down): CCW in NDC is CW after the flip, so a positive
    // signed area is back-facing.
    val signedArea = (sx1 - sx0) * (sy2 - sy0) - (sx2 - sx0) * (sy1 - sy0)
    if (signedArea > 0f) return false

    if ((depthBiasC != 0f || depthBiasS != 0f) && signedArea != 0f) {
      val invA = 1f / signedArea
      val dzdx = ((sz1 - sz0) * (sy2 - sy0) - (sz2 - sz0) * (sy1 - sy0)) * invA
      val dzdy = ((sz2 - sz0) * (sx1 - sx0) - (sz1 - sz0) * (sx2 - sx0)) * invA
      val slope = max(abs(dzdx), abs(dzdy))
      val bias = depthBiasC + depthBiasS * slope
      sz0 += bias
      sz1 += bias
      sz2 += bias
    }

    triScreen[0] = sx0
    triScreen[1] = sy0
    triScreen[2] = sz0
    triScreen[3] = sx1
    triScreen[4] = sy1
    triScreen[5] = sz1
    triScreen[6] = sx2
    triScreen[7] = sy2
    triScreen[8] = sz2

    // Eye-space positions: point-light directions and the flat face normal.
    RcMatrix4.transformPoint(mv, verts[i0], verts[i0 + 1], verts[i0 + 2], scratch4)
    val ev0x = scratch4[0]
    val ev0y = scratch4[1]
    val ev0z = scratch4[2]
    RcMatrix4.transformPoint(mv, verts[i1], verts[i1 + 1], verts[i1 + 2], scratch4)
    val ev1x = scratch4[0]
    val ev1y = scratch4[1]
    val ev1z = scratch4[2]
    RcMatrix4.transformPoint(mv, verts[i2], verts[i2 + 1], verts[i2 + 2], scratch4)
    val ev2x = scratch4[0]
    val ev2y = scratch4[1]
    val ev2z = scratch4[2]

    val normals = m.normals
    val nn = normals.size
    if (smooth && nn > 0 && i0 + 2 < nn && i1 + 2 < nn && i2 + 2 < nn) {
      RcMatrix4.transformDirection(mv, normals[i0], normals[i0 + 1], normals[i0 + 2], normal3)
      triColor[0] = litColor(baseColor, normal3[0], normal3[1], normal3[2], ev0x, ev0y, ev0z)
      RcMatrix4.transformDirection(mv, normals[i1], normals[i1 + 1], normals[i1 + 2], normal3)
      triColor[1] = litColor(baseColor, normal3[0], normal3[1], normal3[2], ev1x, ev1y, ev1z)
      RcMatrix4.transformDirection(mv, normals[i2], normals[i2 + 1], normals[i2 + 2], normal3)
      triColor[2] = litColor(baseColor, normal3[0], normal3[1], normal3[2], ev2x, ev2y, ev2z)
    } else {
      val ax = ev1x - ev0x
      val ay = ev1y - ev0y
      val az = ev1z - ev0z
      val bx = ev2x - ev0x
      val by = ev2y - ev0y
      val bz = ev2z - ev0z
      val nxn = ay * bz - az * by
      val nyn = az * bx - ax * bz
      val nzn = ax * by - ay * bx
      if (specStrength > 0f) {
        // Glossy: flat face normal with per-vertex eye positions, so the highlight gradates.
        triColor[0] = litColor(baseColor, nxn, nyn, nzn, ev0x, ev0y, ev0z)
        triColor[1] = litColor(baseColor, nxn, nyn, nzn, ev1x, ev1y, ev1z)
        triColor[2] = litColor(baseColor, nxn, nyn, nzn, ev2x, ev2y, ev2z)
      } else {
        val cx = (ev0x + ev1x + ev2x) * (1f / 3f)
        val cy = (ev0y + ev1y + ev2y) * (1f / 3f)
        val cz = (ev0z + ev1z + ev2z) * (1f / 3f)
        val shaded = litColor(baseColor, nxn, nyn, nzn, cx, cy, cz)
        triColor[0] = shaded
        triColor[1] = shaded
        triColor[2] = shaded
      }
    }
    return true
  }

  private fun prepareFrame() {
    RcMatrix4.multiply(pv, proj, view)
    RcMatrix4.multiply(mv, view, model)
    RcMatrix4.multiply(mvp, pv, model)
    prepareLightsEyeSpace()
  }

  private fun drawMeshWireframe(m: Rc3DMeshData, lineColor: Int, w: Int, h: Int, mode: Int) {
    var edges = mode and Rc3D.WIRE_EDGE_MASK
    if (edges == 0) edges = Rc3D.WIRE_EDGE_MASK // no selection: all three edges
    val e0 = (edges and Rc3D.WIRE_EDGE0) != 0
    val e1 = (edges and Rc3D.WIRE_EDGE1) != 0
    val e2 = (edges and Rc3D.WIRE_EDGE2) != 0
    val n = m.indices.size
    // Pass 1: depth prepass, front faces into the z-buffer with no colour.
    for (t in 0 until n step 3) {
      if (!projectTriangle(m, false, false, t, lineColor, w, h)) continue
      RcRasterizer.fillTriangleDepthOnly(
        zbuf,
        w,
        h,
        triScreen[6],
        triScreen[7],
        triScreen[8],
        triScreen[3],
        triScreen[4],
        triScreen[5],
        triScreen[0],
        triScreen[1],
        triScreen[2],
      )
    }
    // Pass 2: the selected edges, depth-tested against the prepass.
    for (t in 0 until n step 3) {
      if (!projectTriangle(m, false, false, t, lineColor, w, h)) continue
      val ax = triScreen[0]
      val ay = triScreen[1]
      val az = triScreen[2]
      val bx = triScreen[3]
      val by = triScreen[4]
      val bz = triScreen[5]
      val cx = triScreen[6]
      val cy = triScreen[7]
      val cz = triScreen[8]
      val img = colorBuffer
      if (e0) {
        RcRasterizer.drawLineDepthTested(
          zbuf,
          img,
          lineColor,
          w,
          h,
          ax,
          ay,
          az,
          bx,
          by,
          bz,
          WIRE_DEPTH_BIAS,
        )
      }
      if (e1) {
        RcRasterizer.drawLineDepthTested(
          zbuf,
          img,
          lineColor,
          w,
          h,
          bx,
          by,
          bz,
          cx,
          cy,
          cz,
          WIRE_DEPTH_BIAS,
        )
      }
      if (e2) {
        RcRasterizer.drawLineDepthTested(
          zbuf,
          img,
          lineColor,
          w,
          h,
          cx,
          cy,
          cz,
          ax,
          ay,
          az,
          WIRE_DEPTH_BIAS,
        )
      }
    }
  }

  /** Rasterize mesh [meshId] into [colorBuffer] with the depth test. */
  fun drawMesh(meshId: Int, mode: Int) {
    val m = meshes[meshId] ?: return
    if (colorBuffer.isEmpty()) return
    val w = width
    val h = height
    prepareFrame()
    colorDirty = true

    val baseColor = baseColorArgb
    if ((mode and Rc3D.MODE_WIREFRAME) != 0) {
      drawMeshWireframe(m, baseColor, w, h, mode)
      return
    }

    val smooth = (mode and Rc3D.MODE_SMOOTH_MASK) != 0 && m.normals.isNotEmpty()
    val tex = texPixels
    val uv = m.uv
    val textured = tex != null && uv != null && uv.isNotEmpty()
    // Glossy flat faces carry a per-vertex specular gradient, so they interpolate too.
    val interpolate = smooth || specStrength > 0f
    val img = colorBuffer
    val n = m.indices.size
    for (t in 0 until n step 3) {
      if (!projectTriangle(m, smooth, textured, t, baseColor, w, h)) continue
      if (textured) {
        val q0 = m.indices[t] * 2
        val q1 = m.indices[t + 1] * 2
        val q2 = m.indices[t + 2] * 2
        uv!!
        RcRasterizer.fillTriangleTextured(
          zbuf,
          img,
          tex!!,
          texW,
          texH,
          triColor[2],
          triColor[1],
          triColor[0],
          w,
          h,
          triScreen[6],
          triScreen[7],
          triScreen[8],
          uv[q2],
          uv[q2 + 1],
          triInvW[2],
          triScreen[3],
          triScreen[4],
          triScreen[5],
          uv[q1],
          uv[q1 + 1],
          triInvW[1],
          triScreen[0],
          triScreen[1],
          triScreen[2],
          uv[q0],
          uv[q0 + 1],
          triInvW[0],
        )
      } else if (interpolate) {
        RcRasterizer.fillTriangleGouraud(
          zbuf,
          img,
          triColor[2],
          triColor[1],
          triColor[0],
          w,
          h,
          triScreen[6],
          triScreen[7],
          triScreen[8],
          triScreen[3],
          triScreen[4],
          triScreen[5],
          triScreen[0],
          triScreen[1],
          triScreen[2],
        )
      } else {
        RcRasterizer.fillTriangle(
          zbuf,
          img,
          triColor[0],
          w,
          h,
          triScreen[6],
          triScreen[7],
          triScreen[8],
          triScreen[3],
          triScreen[4],
          triScreen[5],
          triScreen[0],
          triScreen[1],
          triScreen[2],
        )
      }
    }
  }

  /**
   * Project, light and cull mesh [meshId] into [out] as a flat, back-to-front triangle list in
   * window pixels, for a host that rasterizes on the GPU. Returns the vertex count.
   *
   * It runs the same [projectTriangle] the software rasterizer runs, so the two backends can only
   * disagree about rasterization: the GPU has no depth buffer here, and ordering comes from a
   * stable painter's sort on mean depth.
   */
  fun buildScreenVertices(meshId: Int, out: RcScreenMesh, smooth: Boolean): Int {
    out.vertexCount = 0
    val m = meshes[meshId] ?: return 0
    val w = width
    val h = height
    prepareFrame()
    val useSmooth = smooth && m.normals.isNotEmpty()
    val uv = m.uv
    out.hasUv = uv != null && uv.isNotEmpty()
    val n = m.indices.size
    val triCount = n / 3
    if (triCount <= 0) return 0
    out.ensureTriangles(triCount)

    var kept = 0
    for (t in 0 until n step 3) {
      if (!projectTriangle(m, useSmooth, false, t, baseColorArgb, w, h)) continue
      val p = kept * 6
      out.triXY[p] = triScreen[0]
      out.triXY[p + 1] = triScreen[1]
      out.triXY[p + 2] = triScreen[3]
      out.triXY[p + 3] = triScreen[4]
      out.triXY[p + 4] = triScreen[6]
      out.triXY[p + 5] = triScreen[7]
      val c = kept * 3
      out.triColor[c] = triColor[0]
      out.triColor[c + 1] = triColor[1]
      out.triColor[c + 2] = triColor[2]
      if (out.hasUv) {
        uv!!
        val u0 = m.indices[t] * 2
        val u1 = m.indices[t + 1] * 2
        val u2 = m.indices[t + 2] * 2
        out.triUv[p] = uv[u0]
        out.triUv[p + 1] = uv[u0 + 1]
        out.triUv[p + 2] = uv[u1]
        out.triUv[p + 3] = uv[u1 + 1]
        out.triUv[p + 4] = uv[u2]
        out.triUv[p + 5] = uv[u2 + 1]
      }
      // Painter's key: mean window depth, larger being farther.
      out.triDepth[kept] = (triScreen[2] + triScreen[5] + triScreen[8]) * (1f / 3f)
      kept++
    }

    // Stable, so coplanar triangles do not swap between frames and flicker.
    val order = (0 until kept).sortedByDescending { out.triDepth[it] }
    val verts = kept * 3
    out.ensureVertices(verts)
    order.forEachIndexed { k, tri ->
      val src = tri * 6
      val dst = k * 6
      out.triXY.copyInto(out.positions, dst, src, src + 6)
      if (out.hasUv) out.triUv.copyInto(out.uvs, dst, src, src + 6)
      out.triColor.copyInto(out.colors, k * 3, tri * 3, tri * 3 + 3)
    }
    out.vertexCount = verts
    return verts
  }

  private companion object {
    /** A vertex with clip w below this is behind or on the eye. */
    const val NEAR_W_EPSILON = 0.0001f

    /** Ambient floor, so triangles facing away from every light still show their colour. */
    const val AMBIENT = 0.2f

    const val MAX_LIGHTS = 32

    /** Window-z bias that lets a wireframe edge pass the depth its own face wrote. */
    const val WIRE_DEPTH_BIAS = 6e-4f
  }
}

/** Reusable output of [RcSoftware3D.buildScreenVertices]. */
internal class RcScreenMesh {
  /** `x,y` per vertex, window pixels. */
  var positions = FloatArray(0)
  /** Lit ARGB per vertex. */
  var colors = IntArray(0)
  /** `u,v` per vertex, valid when [hasUv]. */
  var uvs = FloatArray(0)
  var hasUv = false
  var vertexCount = 0

  internal var triXY = FloatArray(0)
  internal var triUv = FloatArray(0)
  internal var triColor = IntArray(0)
  internal var triDepth = FloatArray(0)

  internal fun ensureTriangles(count: Int) {
    if (triDepth.size < count) {
      triXY = FloatArray(count * 6)
      triUv = FloatArray(count * 6)
      triColor = IntArray(count * 3)
      triDepth = FloatArray(count)
    }
  }

  internal fun ensureVertices(count: Int) {
    if (colors.size < count) {
      positions = FloatArray(count * 2)
      uvs = FloatArray(count * 2)
      colors = IntArray(count)
    }
  }
}

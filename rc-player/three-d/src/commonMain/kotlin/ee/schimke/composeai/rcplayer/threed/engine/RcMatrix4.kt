package ee.schimke.composeai.rcplayer.threed.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 4x4 column-major float math for the 3D engine, ported from the reference `Matrix4.java` by way of
 * the experiments repo's C++ transcription. Indexing is `m[col * 4 + row]`, the OpenGL layout;
 * right-handed, +Y up, -Z forward.
 *
 * Kotlin `Float` is IEEE single precision on every target, so the reference's expressions are
 * transcribed verbatim, in the same order. Transcendentals go through `Double`, as Java's `Math`
 * does: a float argument widens, the result narrows.
 */
internal object RcMatrix4 {
  fun identity(out: FloatArray) {
    out.fill(0f)
    out[0] = 1f
    out[5] = 1f
    out[10] = 1f
    out[15] = 1f
  }

  /** `out = a x b`; [out] may alias [a] or [b]. */
  fun multiply(out: FloatArray, a: FloatArray, b: FloatArray) {
    val r = FloatArray(16)
    for (col in 0 until 4) {
      val cb = col * 4
      for (row in 0 until 4) {
        r[cb + row] =
          a[row] * b[cb] + a[4 + row] * b[cb + 1] + a[8 + row] * b[cb + 2] + a[12 + row] * b[cb + 3]
      }
    }
    r.copyInto(out)
  }

  /** Post-multiply by a translation: glTranslate. */
  fun translate(m: FloatArray, tx: Float, ty: Float, tz: Float) {
    m[12] += m[0] * tx + m[4] * ty + m[8] * tz
    m[13] += m[1] * tx + m[5] * ty + m[9] * tz
    m[14] += m[2] * tx + m[6] * ty + m[10] * tz
    m[15] += m[3] * tx + m[7] * ty + m[11] * tz
  }

  /** Post-multiply by a non-uniform scale: glScale. */
  fun scale(m: FloatArray, sx: Float, sy: Float, sz: Float) {
    for (i in 0 until 4) m[i] *= sx
    for (i in 4 until 8) m[i] *= sy
    for (i in 8 until 12) m[i] *= sz
  }

  /** Post-multiply by a rotation about a possibly unnormalised axis: glRotate, in radians. */
  fun rotateAxis(m: FloatArray, angleRadians: Float, x: Float, y: Float, z: Float) {
    val len = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
    if (len == 0f) return
    val ix = x / len
    val iy = y / len
    val iz = z / len
    val c = cos(angleRadians.toDouble()).toFloat()
    val s = sin(angleRadians.toDouble()).toFloat()
    val omc = 1f - c

    val r00 = ix * ix * omc + c
    val r01 = iy * ix * omc + iz * s
    val r02 = iz * ix * omc - iy * s
    val r10 = ix * iy * omc - iz * s
    val r11 = iy * iy * omc + c
    val r12 = iz * iy * omc + ix * s
    val r20 = ix * iz * omc + iy * s
    val r21 = iy * iz * omc - ix * s
    val r22 = iz * iz * omc + c

    val a00 = m[0]
    val a01 = m[1]
    val a02 = m[2]
    val a03 = m[3]
    val a10 = m[4]
    val a11 = m[5]
    val a12 = m[6]
    val a13 = m[7]
    val a20 = m[8]
    val a21 = m[9]
    val a22 = m[10]
    val a23 = m[11]

    m[0] = a00 * r00 + a10 * r01 + a20 * r02
    m[1] = a01 * r00 + a11 * r01 + a21 * r02
    m[2] = a02 * r00 + a12 * r01 + a22 * r02
    m[3] = a03 * r00 + a13 * r01 + a23 * r02
    m[4] = a00 * r10 + a10 * r11 + a20 * r12
    m[5] = a01 * r10 + a11 * r11 + a21 * r12
    m[6] = a02 * r10 + a12 * r11 + a22 * r12
    m[7] = a03 * r10 + a13 * r11 + a23 * r12
    m[8] = a00 * r20 + a10 * r21 + a20 * r22
    m[9] = a01 * r20 + a11 * r21 + a21 * r22
    m[10] = a02 * r20 + a12 * r21 + a22 * r22
    m[11] = a03 * r20 + a13 * r21 + a23 * r22
  }

  /** gluPerspective; [fovYRadians] is the full vertical field of view. */
  fun perspective(out: FloatArray, fovYRadians: Float, aspect: Float, near: Float, far: Float) {
    val f = (1.0 / tan(fovYRadians * 0.5)).toFloat()
    val nf = 1f / (near - far)
    out.fill(0f)
    out[0] = f / aspect
    out[5] = f
    out[10] = (far + near) * nf
    out[11] = -1f
    out[14] = 2f * far * near * nf
  }

  /** glOrtho. */
  fun ortho(
    out: FloatArray,
    left: Float,
    right: Float,
    bottom: Float,
    top: Float,
    near: Float,
    far: Float,
  ) {
    val lr = 1f / (left - right)
    val bt = 1f / (bottom - top)
    val nf = 1f / (near - far)
    out.fill(0f)
    out[0] = -2f * lr
    out[5] = -2f * bt
    out[10] = 2f * nf
    out[12] = (left + right) * lr
    out[13] = (top + bottom) * bt
    out[14] = (far + near) * nf
    out[15] = 1f
  }

  /** gluLookAt, right-handed. */
  fun lookAt(
    out: FloatArray,
    eyeX: Float,
    eyeY: Float,
    eyeZ: Float,
    centerX: Float,
    centerY: Float,
    centerZ: Float,
    upX: Float,
    upY: Float,
    upZ: Float,
  ) {
    var fx = centerX - eyeX
    var fy = centerY - eyeY
    var fz = centerZ - eyeZ
    val fl = sqrt((fx * fx + fy * fy + fz * fz).toDouble()).toFloat()
    if (fl == 0f) {
      identity(out)
      return
    }
    fx /= fl
    fy /= fl
    fz /= fl

    var sx = fy * upZ - fz * upY
    var sy = fz * upX - fx * upZ
    var sz = fx * upY - fy * upX
    val sl = sqrt((sx * sx + sy * sy + sz * sz).toDouble()).toFloat()
    if (sl == 0f) {
      identity(out)
      return
    }
    sx /= sl
    sy /= sl
    sz /= sl

    val ux = sy * fz - sz * fy
    val uy = sz * fx - sx * fz
    val uz = sx * fy - sy * fx

    out[0] = sx
    out[1] = ux
    out[2] = -fx
    out[3] = 0f
    out[4] = sy
    out[5] = uy
    out[6] = -fy
    out[7] = 0f
    out[8] = sz
    out[9] = uz
    out[10] = -fz
    out[11] = 0f
    out[12] = -(sx * eyeX + sy * eyeY + sz * eyeZ)
    out[13] = -(ux * eyeX + uy * eyeY + uz * eyeZ)
    out[14] = fx * eyeX + fy * eyeY + fz * eyeZ
    out[15] = 1f
  }

  /** Transform the point `(x, y, z, 1)` into [out4]. */
  fun transformPoint(m: FloatArray, x: Float, y: Float, z: Float, out4: FloatArray) {
    out4[0] = m[0] * x + m[4] * y + m[8] * z + m[12]
    out4[1] = m[1] * x + m[5] * y + m[9] * z + m[13]
    out4[2] = m[2] * x + m[6] * y + m[10] * z + m[14]
    out4[3] = m[3] * x + m[7] * y + m[11] * z + m[15]
  }

  /** Transform the direction `(x, y, z, 0)`, ignoring translation, into [out3]. */
  fun transformDirection(m: FloatArray, x: Float, y: Float, z: Float, out3: FloatArray) {
    out3[0] = m[0] * x + m[4] * y + m[8] * z
    out3[1] = m[1] * x + m[5] * y + m[9] * z
    out3[2] = m[2] * x + m[6] * y + m[10] * z
  }

  /**
   * The normal matrix of [m]: the inverse transpose of its upper-left 3x3, as a column-major 4x4
   * with no translation. Directions transformed by [m] itself stay perpendicular to a surface only
   * under rotation and uniform scale; this keeps them so under any scale. Its columns are the cross
   * products of [m]'s columns over the determinant, which keeps the sign under a mirroring matrix.
   * A singular [m] (a zero scale) has no inverse, and leaves [out] as [m]'s own linear part.
   */
  fun normalMatrix(out: FloatArray, m: FloatArray) {
    val ax = m[0]
    val ay = m[1]
    val az = m[2]
    val bx = m[4]
    val by = m[5]
    val bz = m[6]
    val cx = m[8]
    val cy = m[9]
    val cz = m[10]
    // b x c, c x a, a x b
    val n0x = by * cz - bz * cy
    val n0y = bz * cx - bx * cz
    val n0z = bx * cy - by * cx
    val n1x = cy * az - cz * ay
    val n1y = cz * ax - cx * az
    val n1z = cx * ay - cy * ax
    val n2x = ay * bz - az * by
    val n2y = az * bx - ax * bz
    val n2z = ax * by - ay * bx
    val det = ax * n0x + ay * n0y + az * n0z
    out.fill(0f)
    out[15] = 1f
    if (det == 0f || !det.isFinite()) {
      out[0] = ax
      out[1] = ay
      out[2] = az
      out[4] = bx
      out[5] = by
      out[6] = bz
      out[8] = cx
      out[9] = cy
      out[10] = cz
      return
    }
    val inv = 1f / det
    out[0] = n0x * inv
    out[1] = n0y * inv
    out[2] = n0z * inv
    out[4] = n1x * inv
    out[5] = n1y * inv
    out[6] = n1z * inv
    out[8] = n2x * inv
    out[9] = n2y * inv
    out[10] = n2z * inv
  }
}

package ee.schimke.composeai.rcconformance.runner

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** A decoded reference image, straight (non-premultiplied) RGBA, row-major. */
public data class DecodedImage(
  public val width: Int,
  public val height: Int,
  public val rgba: ByteArray,
) {
  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is DecodedImage &&
        width == other.width &&
        height == other.height &&
        rgba.contentEquals(other.rgba))

  override fun hashCode(): Int = (width * 31 + height) * 31 + rgba.contentHashCode()
}

/**
 * Decodes a `raster` check's expectation.
 *
 * The corpus stores reference images as `data:image/png;base64,…` inline in the gold, which is why
 * gold files carrying rasters are large. Returns null rather than throwing: a malformed or absent
 * reference is a corpus problem the runner reports as a failed check, not a reason to abandon the
 * remaining 251 golds.
 */
@OptIn(ExperimentalEncodingApi::class)
public fun decodeDataUriPng(dataUri: String?): DecodedImage? {
  val payload = dataUri?.substringAfter("base64,", missingDelimiterValue = "") ?: return null
  if (payload.isEmpty()) return null
  val bytes = runCatching { Base64.decode(payload) }.getOrNull() ?: return null
  val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
  return image.toRgba()
}

/** Straight RGBA, which is what [Pixelmatch] composites onto white. */
public fun BufferedImage.toRgba(): DecodedImage {
  val out = ByteArray(width * height * 4)
  var offset = 0
  for (y in 0 until height) {
    for (x in 0 until width) {
      val argb = getRGB(x, y)
      out[offset] = (argb ushr 16).toByte()
      out[offset + 1] = (argb ushr 8).toByte()
      out[offset + 2] = argb.toByte()
      out[offset + 3] = (argb ushr 24).toByte()
      offset += 4
    }
  }
  return DecodedImage(width, height, out)
}

/**
 * Re-encodes a frame as a `data:` URI, for the result file's `attachments.raster_actual`.
 *
 * The audit report renders the actual frame beside the reference, so a raster failure can be looked
 * at rather than only counted.
 */
@OptIn(ExperimentalEncodingApi::class)
public fun encodeRgbaAsDataUri(width: Int, height: Int, rgba: ByteArray): String {
  val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
  var offset = 0
  for (y in 0 until height) {
    for (x in 0 until width) {
      val argb =
        ((rgba[offset + 3].toInt() and 0xFF) shl 24) or
          ((rgba[offset].toInt() and 0xFF) shl 16) or
          ((rgba[offset + 1].toInt() and 0xFF) shl 8) or
          (rgba[offset + 2].toInt() and 0xFF)
      image.setRGB(x, y, argb)
      offset += 4
    }
  }
  val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
  return "data:image/png;base64,${Base64.encode(bytes)}"
}

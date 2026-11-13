package ee.schimke.composeai.rcwidget.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Images for hosts that can't fetch one: a launcher widget and a Wear widget embed them, and their
 * `RemoteViews` / data-layer payloads cross a Binder, so each must stay small.
 */
public object RcWidgetImages {
  /** The longest edge an image is decoded at: a card draws one at a fraction of the screen. */
  public const val MAX_DECODE_PX: Int = 1440

  /**
   * [bytes] decoded, downsampled by a power of two until its longest edge is at most [maxPx]: a
   * bounds-only pass reads the size, then the decode uses `inSampleSize`. Null when undecodable.
   */
  public fun decodeBounded(bytes: ByteArray, maxPx: Int = MAX_DECODE_PX): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
  }

  /**
   * [bitmap] as PNG scaled to fit [maxPx], halved again until it fits [maxBytes]; null when it can't
   * be made that small. PNG is what `embedBitmapUrls` embeds and every player decodes.
   */
  public fun png(bitmap: Bitmap, maxPx: Int, maxBytes: Int = Int.MAX_VALUE): ByteArray? {
    var limit = maxPx
    while (limit >= MIN_PX) {
      val png = scaled(bitmap, limit).toPng()
      if (png.size <= maxBytes) return png
      limit /= 2
    }
    return null
  }

  /** Encoded image [bytes] re-encoded by [png], for a fetched image of unknown size. */
  public fun png(bytes: ByteArray, maxPx: Int, maxBytes: Int = Int.MAX_VALUE): ByteArray? =
    decodeBounded(bytes, maxOf(maxPx, MIN_PX))?.let { png(it, maxPx, maxBytes) }

  private fun scaled(bitmap: Bitmap, maxPx: Int): Bitmap {
    val scale = minOf(1f, maxPx.toFloat() / maxOf(bitmap.width, bitmap.height))
    if (scale >= 1f) return bitmap
    return Bitmap.createScaledBitmap(
      bitmap,
      (bitmap.width * scale).toInt().coerceAtLeast(1),
      (bitmap.height * scale).toInt().coerceAtLeast(1),
      true,
    )
  }

  private fun Bitmap.toPng(): ByteArray =
    ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

  private const val MIN_PX = 32
}

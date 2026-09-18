package ee.schimke.composeai.rcconformance.runner

/**
 * The diff heatmap the audit report renders in its third image panel.
 *
 * `generate-audit-report.mjs` shows three frames per raster comparison — gold, the player's actual,
 * and a difference map. The first two this runner has always had; the third is what this file
 * encodes. Without it the panel reads *"Not computed by this player"* on every card, which is a
 * report claiming a gap it does not have.
 *
 * The pixels come from [PixelmatchDiff.magnitude] — the same walk that decided the verdict, so the
 * picture cannot disagree with the count beside it. Where the two frames agree — perceptually,
 * which is what the verdict measures — the map is fully transparent (the report panels sit on a
 * checkerboard, and painting matched pixels any colour at all would make every raster check *look*
 * like a disagreement). Where the verdict counted a pixel, it is coloured along a dark-blue →
 * yellow ramp by how much it differs, so a glance separates one-shade-off antialiasing noise from a
 * wrong colour drawn in the wrong place.
 *
 * The ramp's stops are fitted to the anchors of the heatmap the corpus's own TypeScript runner
 * publishes (`results/typescript/conformance-results.json`), so this lane's panels read the same
 * way as the reference lane's: small deltas dark blue, the largest bright yellow, the middle
 * passing through violet rather than green. The exact colours are presentation, not contract — the
 * metric in `Pixelmatch` is where comparability is protected.
 */
public object DiffHeatmap {
  /** (channel delta → RGB), interpolated piecewise-linearly between the stops. */
  private val STOPS =
    intArrayOf(
      1,
      50,
      100,
      120,
      150,
      180,
      200,
      230,
      255,
    )
  private val COLOURS =
    intArrayOf(
      0x000179, // (0, 1, 121) — a one-step rounding difference
      0x101fad, // (16, 31, 173)
      0x1f3fe2, // (31, 63, 226)
      0x264bf7, // (38, 75, 247) — near the verdict threshold the blue is at full strength
      0x4e4ad2, // (78, 74, 210)
      0x815a96, // (129, 90, 150)
      0xa2756e, // (162, 117, 110)
      0xd5b432, // (213, 180, 50)
      0xffff00, // (255, 255, 0) — the largest a channel can move
    )

  /**
   * Encodes the map as a `data:image/png;base64,…` URI, straight RGBA.
   *
   * [magnitude] is [PixelmatchDiff.magnitude]: one unsigned byte per pixel, row-major, `0` where
   * the frames are perceptually equal. Its non-zero entries are exactly the pixels the verdict
   * counted (`hardDiffs + aaExplained`), so the marked area is what the tolerance was applied to.
   */
  public fun encode(width: Int, height: Int, magnitude: ByteArray): String {
    require(magnitude.size == width * height) {
      "magnitude is ${magnitude.size} bytes, expected ${width * height} for ${width}x$height"
    }
    val rgba = ByteArray(width * height * 4)
    var offset = 0
    for (pixel in 0 until width * height) {
      val delta = magnitude[pixel].toInt() and 0xFF
      if (delta != 0) {
        val colour = ramp(delta)
        rgba[offset] = ((colour ushr 16) and 0xFF).toByte()
        rgba[offset + 1] = ((colour ushr 8) and 0xFF).toByte()
        rgba[offset + 2] = (colour and 0xFF).toByte()
        rgba[offset + 3] = 0xFF.toByte()
      }
      offset += 4
    }
    return encodeRgbaAsDataUri(width, height, rgba)
  }

  private fun ramp(delta: Int): Int {
    var upper = 1
    while (upper < STOPS.size - 1 && STOPS[upper] < delta) upper++
    val from = STOPS[upper - 1]
    val to = STOPS[upper]
    val t = (delta - from).toDouble() / (to - from)
    val a = COLOURS[upper - 1]
    val b = COLOURS[upper]
    val r = lerp((a ushr 16) and 0xFF, (b ushr 16) and 0xFF, t)
    val g = lerp((a ushr 8) and 0xFF, (b ushr 8) and 0xFF, t)
    val bl = lerp(a and 0xFF, b and 0xFF, t)
    return (r shl 16) or (g shl 8) or bl
  }

  /** A rounded channel interpolation, clamped the way a colour channel must be. */
  private fun lerp(from: Int, to: Int, t: Double): Int =
    (from + (to - from) * t).toInt().coerceIn(0, 255)
}

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Evidence generator for `FILTER_BITMAP`: a 4×4 checkerboard stretched to 192 px, drawn with
 * filtering off (left) and on (right). Skipped unless `-Prc.bitmapFilter.out=<abs dir>` is given,
 * so `check` neither writes files nor fails.
 *
 * ```
 * ./gradlew :rc-player-compose:jvmTest --rerun --tests '*RcBitmapFilterEvidenceTest*' \
 *   -Prc.bitmapFilter.out=<abs dir>
 * ```
 */
class RcBitmapFilterEvidenceTest {

  @Test
  fun renderFilterBitmapEvidence() {
    val out = System.getProperty("rc.bitmapFilter.out") ?: return
    val size = 192f
    val checker =
      ByteArray(4 * 4 * 4) { i ->
        val pixel = i / 4
        val dark = (pixel % 4 + pixel / 4) % 2 == 0
        when (i % 4) {
          3 -> -1
          2 -> if (dark) 0x80.toByte() else -1
          else -> if (dark) 0x10 else -1
        }
      }
    fun draw(left: Float, vararg paint: Int) =
      listOf(
        RcPaintData(paint.toList()),
        RcDrawBitmap(
          20,
          RcFloatWord.literal(left),
          RcFloatWord.literal(0f),
          RcFloatWord.literal(left + size),
          RcFloatWord.literal(size),
          0,
        ),
      )
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 400, legacyHeight = 192, modern = false),
        listOf(
          RcBitmapData(
            20,
            4,
            4,
            RcBitmapData.TYPE_RAW8888,
            RcBitmapData.ENCODING_INLINE,
            checker,
          )
        ) + draw(0f, FILTER_BITMAP) + draw(208f, FILTER_BITMAP or (1 shl 16)),
      )
    val scene =
      ImageComposeScene(width = 400, height = 192, density = Density(1f)) {
        RcComposePlayer(document)
      }
    try {
      val png = scene.render(0L).encodeToData(EncodedImageFormat.PNG)!!.bytes
      File(out).apply { mkdirs() }.resolve("filter-bitmap.png").writeBytes(png)
    } finally {
      scene.close()
    }
  }

  private companion object {
    /** `PaintBundle.FILTER_BITMAP`; its on/off value rides in the high 16 bits. */
    const val FILTER_BITMAP = 17
  }
}

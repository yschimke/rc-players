/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ee.schimke.composeai.rcembedded.jvm

import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * A graphics layer with no authored transform origin transforms about its centre (#153).
 *
 * `remote-core` declares the origin's default as 0, the corner, while `remote-creation-compose`
 * leaves the attribute out when it is the centre. So `graphicsLayer { scaleX = -1f }` arrives with
 * no origin, and a player that reads the table's default mirrors the content about its left edge,
 * out of its own bounds. The fixture is that mirror, reduced: a red bar at the left of the layer,
 * which the mirror should move to the right.
 */
class RcJvmGraphicsLayerOriginTest {

  @Before
  fun requireSkikoNatives() {
    Assume.assumeTrue("skiko natives unavailable: $skikoLoadFailure", SKIKO_LOADED)
  }

  @Test
  fun anUnauthoredOriginMirrorsAboutTheCentre() {
    val png = render(mirror(origin = null))
    assertEquals(
      "the mirrored bar lands at the right",
      BAR * HEIGHT,
      redPixels(png, WIDTH - BAR, WIDTH),
    )
    assertEquals("nothing is left at the left", 0, redPixels(png, 0, BAR))
  }

  /** An origin the document does write is still honoured, corner included. */
  @Test
  fun anAuthoredCornerOriginIsHonoured() {
    val png = render(mirror(origin = 0f))
    assertEquals(
      "mirrored about the left edge, the bar leaves the layer",
      0,
      redPixels(png, 0, WIDTH),
    )
  }

  private fun render(document: RcDocument): ByteArray =
    renderRemoteDocumentToPng(RcDocumentCodec.encode(document), WIDTH, HEIGHT, density = 1f)

  private fun mirror(origin: Float?, scale: Float = -1f): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val layer = buildList {
      add(
        RcGraphicsLayerAttribute.FloatValue(
          RcGraphicsLayerModifier.SCALE_X,
          RcFloatWord.literal(scale),
        )
      )
      if (origin != null) {
        add(
          RcGraphicsLayerAttribute.FloatValue(
            RcGraphicsLayerModifier.TRANSFORM_ORIGIN_X,
            RcFloatWord.literal(origin),
          )
        )
        add(
          RcGraphicsLayerAttribute.FloatValue(
            RcGraphicsLayerModifier.TRANSFORM_ORIGIN_Y,
            RcFloatWord.literal(origin),
          )
        )
      }
    }
    val operations =
      buildList<RcOperation> {
        add(RcRootLayout(1))
        add(RcBoxLayout(3, 0, 1, 4))
        add(RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(WIDTH.toFloat())))
        add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat())))
        add(RcLayoutContent(4))
        add(RcCanvasLayout(5, 50))
        add(RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(WIDTH.toFloat())))
        add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat())))
        add(RcGraphicsLayerModifier(layer))
        add(RcNoArg(RcOpcodes.CANVAS_OPERATIONS))
        add(RcPaintData(listOf(4, RED)))
        add(
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(BAR.toFloat()),
            RcFloatWord.literal(HEIGHT.toFloat()),
          )
        )
        repeat(5) { add(end) }
      }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      operations,
    )
  }

  /** Pure red pixels in the columns `[fromX, toX)`. */
  private fun redPixels(png: ByteArray, fromX: Int, toX: Int): Int {
    val image = Image.makeFromEncoded(png)
    val bitmap = Bitmap().apply { allocN32Pixels(image.width, image.height) }
    check(image.readPixels(bitmap))
    var count = 0
    for (y in 0 until bitmap.height) for (x in fromX until toX) if (bitmap.getColor(x, y) == RED)
      count++
    return count
  }

  private companion object {
    const val WIDTH = 60
    const val HEIGHT = 20
    const val BAR = 10
    const val RED = 0xffff0000.toInt()

    var skikoLoadFailure: String? = null

    /** Whether Skia is callable at all — decided once by touching a class that loads the native. */
    val SKIKO_LOADED: Boolean =
      try {
        org.jetbrains.skia.FontMgr.default.familiesCount
        true
      } catch (t: Throwable) {
        skikoLoadFailure = "${t::class.java.simpleName}: ${t.message}"
        false
      }
  }
}

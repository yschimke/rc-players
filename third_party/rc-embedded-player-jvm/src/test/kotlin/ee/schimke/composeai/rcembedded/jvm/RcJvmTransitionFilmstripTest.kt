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

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import java.io.File
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/** Regression coverage and opt-in visual evidence for the vendored StateLayout transition. */
class RcJvmTransitionFilmstripTest {
  @Before
  fun requireSkikoNatives() {
    Assume.assumeTrue(
      "skiko natives unavailable",
      runCatching { org.jetbrains.skia.FontMgr.default }.isSuccess,
    )
  }

  @Test
  fun sharedElementMovesThroughTheStateLayout() {
    val document = parseDocument(RcDocumentCodec.encode(document()))
    val scene =
      ImageComposeScene(width = SIZE, height = SIZE, density = Density(1f)) {
        RcPlayerJvm(document, Modifier)
      }
    try {
      val initial = scene.render(0)
      document.remoteComposeState.overrideInteger(INDEX_ID, 1)
      scene.render(1) // Start the transition before sampling it.
      val frames =
        listOf(initial) +
          listOf(75L, 150L, 225L, 300L).map { millis -> scene.render(millis * 1_000_000) }
      val settled = scene.render(900L * 1_000_000)

      System.getenv(OUTPUT_ENV)?.let { directory ->
        val output = File(directory).apply { mkdirs() }
        // Before this sync StateLayout selected its new child immediately. Repeating the settled
        // frame records that observable old behaviour at the same 75ms sampling points.
        writeFilmstrip(listOf(initial) + List(4) { settled }, File(output, "before.png"))
        writeFilmstrip(frames, File(output, "after.png"))
      }

      assertTrue("the shared square starts at top-left", hasBlue(frames.first(), 20, 20))
      assertTrue(
        "the shared square passes through the middle; ${frames.map(::blueBounds)}",
        frames.any { hasBlue(it, 60, 60) },
      )
      assertTrue("the shared square settles at bottom-right", hasBlue(settled, 100, 100))
    } finally {
      scene.close()
    }
  }

  private fun hasBlue(image: Image, x: Int, y: Int): Boolean {
    val bitmap = Bitmap().apply { allocN32Pixels(image.width, image.height) }
    check(image.readPixels(bitmap))
    val color = bitmap.getColor(x, y)
    return Color.getB(color) > 128 && Color.getR(color) < 64
  }

  private fun blueBounds(image: Image): String {
    val bitmap = Bitmap().apply { allocN32Pixels(image.width, image.height) }
    check(image.readPixels(bitmap))
    val points = buildList {
      for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
          val color = bitmap.getColor(x, y)
          if (Color.getB(color) > 128 && Color.getR(color) < 64) add(x to y)
        }
      }
    }
    return if (points.isEmpty()) "none"
    else
      "${points.minOf { it.first }},${points.minOf { it.second }}..${points.maxOf { it.first }},${points.maxOf { it.second }}"
  }

  private fun writeFilmstrip(frames: List<Image>, target: File) {
    val gap = 8
    val surface =
      Surface.makeRasterN32Premul(frames.size * SIZE + (frames.size + 1) * gap, SIZE + 2 * gap)
    val canvas = surface.canvas
    canvas.clear(Color.WHITE)
    val border =
      Paint().apply {
        color = Color.makeRGB(200, 200, 200)
        mode = PaintMode.STROKE
        strokeWidth = 1f
      }
    frames.forEachIndexed { index, frame ->
      val left = (gap + index * (SIZE + gap)).toFloat()
      canvas.drawImage(frame, left, gap.toFloat())
      canvas.drawRect(Rect.makeXYWH(left, gap.toFloat(), SIZE.toFloat(), SIZE.toFloat()), border)
    }
    target.writeBytes(surface.makeImageSnapshot().encodeToData()!!.bytes)
  }

  private fun document(): RcDocument {
    val operations =
      listOf<RcOperation>(
        RcIntegerConstant(INDEX_ID, 0),
        RcRootLayout(1),
        RcStateLayout(
          3,
          30,
          horizontalPositioning = 1,
          verticalPositioning = 4,
          indexId = INDEX_ID,
        ),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE.toFloat())),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE.toFloat())),
        animationSpec(30),
        RcLayoutContent(4),
      ) + branch(5, 1, 4) + branch(6, 3, 5) + List(4) { END }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = SIZE, legacyHeight = SIZE, modern = false),
      operations,
    )
  }

  private fun branch(componentId: Int, horizontal: Int, vertical: Int): List<RcOperation> =
    listOf(
      RcBoxLayout(componentId, 0, horizontal, vertical),
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE.toFloat())),
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE.toFloat())),
      RcLayoutContent(componentId * 100),
      RcCanvasLayout(componentId * 100 + 1, SHARED_ID),
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(SQUARE.toFloat())),
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(SQUARE.toFloat())),
      animationSpec(SHARED_ID),
      RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
      RcPaintData(listOf(4, BLUE)),
      RcDraw4(
        RcOpcodes.DRAW_RECT,
        RcFloatWord.literal(0f),
        RcFloatWord.literal(0f),
        RcFloatWord.literal(SQUARE.toFloat()),
        RcFloatWord.literal(SQUARE.toFloat()),
      ),
      END,
      END,
      END,
      END,
    )

  private fun animationSpec(animationId: Int) =
    RcAnimationSpec(
      animationId = animationId,
      motionDurationMillis = RcFloatWord.literal(300f),
      motionEasingType = 4,
      visibilityDurationMillis = RcFloatWord.literal(300f),
      visibilityEasingType = 4,
      enterAnimation = RcLayoutAnimation.FadeIn,
      exitAnimation = RcLayoutAnimation.FadeOut,
    )

  private companion object {
    const val OUTPUT_ENV = "RC_EMBEDDED_TRANSITION_FILMSTRIP"
    const val INDEX_ID = 20
    const val SHARED_ID = 500
    const val SIZE = 120
    const val SQUARE = 40
    const val BLUE = 0xff1a73e8.toInt()
    val END = RcNoArg(RcOpcodes.CONTAINER_END)
  }
}

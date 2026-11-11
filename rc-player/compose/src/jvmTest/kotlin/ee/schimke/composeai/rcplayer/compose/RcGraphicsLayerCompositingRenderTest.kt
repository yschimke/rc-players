package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * A graphics layer's `COMPOSITING_STRATEGY` decides how the layer is composited, as it does in
 * AndroidX's embedded player since androidx-main `28fb38004`.
 *
 * `remote-creation-compose` writes `RemoteCompositingStrategy` as an int attribute of the layer.
 * This player accepted and ignored it, so every layer composited as `Auto`. Two documents show the
 * two strategies that differ from it, the same two upstream's `RcPlayerGraphicsLayerTest` pins:
 * * **`ModulateAlpha`** applies the layer's alpha to each draw, where `Auto` draws the layer opaque
 *   and fades the result. Two overlapping opaque rectangles under a half alpha show the lower one
 *   through the upper one only when modulated.
 * * **`Offscreen`** isolates the layer at full alpha, where `Auto` draws straight into what is
 *   behind it. A `BlendMode.Clear` rectangle then cuts a hole in the layer alone, and the parent's
 *   background shows through it; under `Auto` it erases the background too.
 */
class RcGraphicsLayerCompositingRenderTest {

  @Test
  fun modulateAlphaFadesEachDrawRatherThanTheLayer() {
    val auto = render(overlapDocument(RcGraphicsLayerModifier.COMPOSITING_STRATEGY_AUTO))
    val absent = render(overlapDocument(strategy = null))
    val modulated =
      render(overlapDocument(RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA))

    // Where only one rectangle is, the two strategies agree: it is drawn at half alpha.
    assertEquals(auto.getColor(RED_ONLY_X, MID_Y), modulated.getColor(RED_ONLY_X, MID_Y))
    // Where they overlap, `Auto` composites blue over red first and fades the result, so no red
    // is left; an absent attribute is `Auto`.
    assertEquals(0, Color.getR(auto.getColor(OVERLAP_X, MID_Y)), "Auto leaves no red under blue")
    assertEquals(auto.getColor(OVERLAP_X, MID_Y), absent.getColor(OVERLAP_X, MID_Y))
    // `ModulateAlpha` draws each at half alpha, so half the red shows through the blue.
    val overlap = modulated.getColor(OVERLAP_X, MID_Y)
    assertTrue(
      Color.getR(overlap) > 32,
      "ModulateAlpha lets the red show through the blue: ${overlap.toUInt().toString(16)}",
    )
    assertTrue(
      Color.getA(overlap) > Color.getA(auto.getColor(OVERLAP_X, MID_Y)),
      "two half-alpha draws cover more than one",
    )
  }

  @Test
  fun offscreenKeepsAClearCutOutInsideTheLayer() {
    val auto = render(cutOutDocument(RcGraphicsLayerModifier.COMPOSITING_STRATEGY_AUTO))
    val offscreen = render(cutOutDocument(RcGraphicsLayerModifier.COMPOSITING_STRATEGY_OFFSCREEN))

    // Outside the hole both draw the layer's red over the parent's blue.
    assertEquals(RED, auto.getColor(RED_ONLY_X, MID_Y))
    assertEquals(RED, offscreen.getColor(RED_ONLY_X, MID_Y))
    // Inside it, `Auto` clears whatever is there, the parent's background included.
    assertEquals(0, auto.getColor(HOLE_X, MID_Y), "Auto clears through the parent's background")
    // `Offscreen` clears the layer's own red only, and the background shows through the hole.
    assertEquals(
      BLUE,
      offscreen.getColor(HOLE_X, MID_Y),
      "Offscreen stops the cut-out at the layer",
    )
  }

  /**
   * Evidence for `renders/cmp-compositing-strategy/`, written only when `RC_LAYOUT_EVIDENCE_DIR` is
   * set: `Auto`, `ModulateAlpha` (the overlap document) and `Auto`, `Offscreen` (the cut-out
   * document), left to right, at 6x on a checkerboard so transparency reads.
   */
  @Test
  fun writeCompositingStrategyEvidence() {
    val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    val cells =
      listOf(
        overlapDocument(RcGraphicsLayerModifier.COMPOSITING_STRATEGY_AUTO),
        overlapDocument(RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA),
        cutOutDocument(RcGraphicsLayerModifier.COMPOSITING_STRATEGY_AUTO),
        cutOutDocument(RcGraphicsLayerModifier.COMPOSITING_STRATEGY_OFFSCREEN),
      )
    val cellWidth = WIDTH * SCALE
    val cellHeight = HEIGHT * SCALE
    val surface = Surface.makeRasterN32Premul((cellWidth + GAP) * cells.size - GAP, cellHeight)
    val canvas = surface.canvas
    canvas.clear(Color.WHITE)
    val grey = org.jetbrains.skia.Paint().apply { color = 0xffd0d0d0.toInt() }
    cells.forEachIndexed { index, document ->
      val left = index * (cellWidth + GAP)
      for (y in 0 until cellHeight step CHECK) {
        for (x in 0 until cellWidth step CHECK) {
          if ((x / CHECK + y / CHECK) % 2 == 0) {
            canvas.drawRect(
              Rect.makeXYWH((left + x).toFloat(), y.toFloat(), CHECK.toFloat(), CHECK.toFloat()),
              grey,
            )
          }
        }
      }
      canvas.drawImageRect(
        renderImage(document),
        Rect.makeXYWH(left.toFloat(), 0f, cellWidth.toFloat(), cellHeight.toFloat()),
      )
    }
    directory
      .resolve("auto-modulate-alpha-auto-offscreen.png")
      .writeBytes(surface.makeImageSnapshot().encodeToData()!!.bytes)
  }

  private fun renderImage(document: RcDocument): Image {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(document)
      }
    try {
      return scene.render(0L)
    } finally {
      scene.close()
    }
  }

  private fun render(document: RcDocument): Bitmap =
    Bitmap().apply {
      allocN32Pixels(WIDTH, HEIGHT)
      check(renderImage(document).readPixels(this))
    }

  /** A half-alpha layer drawing an opaque red rectangle, then an opaque blue one over its end. */
  private fun overlapDocument(strategy: Int?): RcDocument =
    document(
      parentBackground = false,
      layer =
        listOfNotNull(
          RcGraphicsLayerAttribute.FloatValue(
            RcGraphicsLayerModifier.ALPHA,
            RcFloatWord.literal(0.5f),
          ),
          strategy?.let {
            RcGraphicsLayerAttribute.IntValue(RcGraphicsLayerModifier.COMPOSITING_STRATEGY, it)
          },
        ),
      draws =
        listOf(
          RcPaintData(listOf(PAINT_COLOR, RED)),
          rect(0f, 40f),
          RcPaintData(listOf(PAINT_COLOR, BLUE)),
          rect(20f, 60f),
        ),
    )

  /** A full-alpha layer over a blue parent: an opaque red rectangle with a cleared middle. */
  private fun cutOutDocument(strategy: Int): RcDocument =
    document(
      parentBackground = true,
      layer =
        listOf(
          RcGraphicsLayerAttribute.IntValue(RcGraphicsLayerModifier.COMPOSITING_STRATEGY, strategy),
        ),
      draws =
        listOf(
          RcPaintData(listOf(PAINT_COLOR, RED)),
          rect(0f, 60f),
          RcPaintData(listOf(PAINT_BLEND_MODE or (BLEND_MODE_CLEAR shl 16))),
          rect(20f, 40f),
        ),
    )

  private fun document(
    parentBackground: Boolean,
    layer: List<RcGraphicsLayerAttribute>,
    draws: List<RcOperation>,
  ): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val operations =
      buildList<RcOperation> {
        add(RcRootLayout(1))
        add(RcLayoutContent(2))
        add(RcBoxLayout(3, 30, 1, 1))
        add(RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(WIDTH.toFloat())))
        add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat())))
        if (parentBackground) {
          add(
            RcBackgroundModifier(
              flags = 0,
              colorId = 0,
              reserved1 = 0,
              reserved2 = 0,
              red = RcFloatWord.literal(0f),
              green = RcFloatWord.literal(0f),
              blue = RcFloatWord.literal(1f),
              alpha = RcFloatWord.literal(1f),
              shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
            ),
          )
        }
        add(RcLayoutContent(4))
        add(RcCanvasLayout(5, 50))
        add(RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(WIDTH.toFloat())))
        add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat())))
        add(RcGraphicsLayerModifier(layer))
        add(RcNoArg(RcOpcodes.CANVAS_OPERATIONS))
        addAll(draws)
        repeat(6) { add(end) }
      }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      operations,
    )
  }

  private fun rect(left: Float, right: Float) =
    RcDraw4(
      RcOpcodes.DRAW_RECT,
      RcFloatWord.literal(left),
      RcFloatWord.literal(0f),
      RcFloatWord.literal(right),
      RcFloatWord.literal(HEIGHT.toFloat()),
    )

  private companion object {
    const val WIDTH = 60
    const val HEIGHT = 20
    const val MID_Y = 10
    const val RED_ONLY_X = 10
    const val OVERLAP_X = 30
    const val HOLE_X = 30
    const val RED = 0xffff0000.toInt()
    const val BLUE = 0xff0000ff.toInt()

    /** `PaintBundle` tags: a colour word follows 4; 18 packs the blend mode in its high bits. */
    const val PAINT_COLOR = 4
    const val PAINT_BLEND_MODE = 18
    const val BLEND_MODE_CLEAR = 0

    const val SCALE = 6
    const val GAP = 12
    const val CHECK = 12
  }
}

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIdOperation
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

/**
 * Pins the component clip modifiers (Compose layer clips) and conic path segments (drawn by Skia
 * exactly on this target, not as quads) with pixel probes.
 */
class RcClipAndConicRenderTest {

  /** Each corner of a rounded clip takes its own radius, and "start" is the left edge. */
  @Test
  fun roundedClipGivesEachCornerItsOwnRadius() {
    assertCorners(render(clippedCanvas(ROUNDED)))
  }

  /** AndroidX maps the wire's start corners to the left whatever the layout direction. */
  @Test
  fun roundedClipCornersDoNotMirrorUnderRtl() {
    // The root places the canvas at its end edge under RTL, so the box starts at x = SIZE - BOX.
    assertCorners(render(clippedCanvas(ROUNDED), LayoutDirection.Rtl), left = SIZE - BOX)
  }

  /** A rect clip keeps an overdrawing canvas inside the component's bounds. */
  @Test
  fun rectClipKeepsOverdrawInsideTheComponent() {
    val pixels = render(clippedCanvas(RcClipRectModifier, overdraw = 10f))
    assertEquals(GREEN, pixels.getColor(0, 0))
    assertEquals(GREEN, pixels.getColor(BOX - 1, BOX - 1))
    assertEquals(0, pixels.getColor(BOX + 2, BOX + 2))
    assertEquals(0, pixels.getColor(BOX + 2, 5))
    assertEquals(0, pixels.getColor(5, BOX + 2))
  }

  /**
   * A weight-√½ conic from (R, 0) through (R, R) to (0, R) is an exact quarter circle about the
   * origin. The quad through the same points bulges to ~1.06 R on the diagonal, so a probe just
   * outside the circle tells the two apart.
   */
  @Test
  fun aConicSegmentIsAnExactCircularArc() {
    val pixels = render(conicCanvas(weight = sqrt(0.5f)))
    // Diagonal probes: (53, 53) is 75 px from the origin (inside R = 80), (60, 60) is 85 px.
    assertEquals(GREEN, pixels.getColor(53, 53))
    assertEquals(0, pixels.getColor(60, 60))
    assertEquals(GREEN, pixels.getColor(77, 5))
    assertEquals(GREEN, pixels.getColor(5, 77))
    assertEquals(0, pixels.getColor(78, 30))
  }

  /** Weight 1 degenerates to the quadratic, which does reach past the circle on the diagonal. */
  @Test
  fun aUnitWeightConicIsTheQuadratic() {
    val pixels = render(conicCanvas(weight = 1f))
    assertEquals(GREEN, pixels.getColor(58, 58))
    assertEquals(0, pixels.getColor(62, 62))
  }

  private fun assertCorners(bitmap: Bitmap, left: Int = 0) {
    val pixels = Probe(bitmap, left)
    // Top-left, 40 px radius: (6, 6) lies outside the arc; the top edge midpoint is inside.
    assertEquals(0, pixels.getColor(6, 6))
    assertEquals(GREEN, pixels.getColor(40, 1))
    // Top-right, square: the very corner pixel is painted.
    assertEquals(GREEN, pixels.getColor(BOX - 1, 0))
    // Bottom-left, 20 px radius: (3, BOX - 4) is outside, (8, BOX - 8) inside.
    assertEquals(0, pixels.getColor(3, BOX - 4))
    assertEquals(GREEN, pixels.getColor(8, BOX - 8))
    // Bottom-right, 4 px radius: only the extreme corner is cut.
    assertEquals(0, pixels.getColor(BOX - 1, BOX - 1))
    assertEquals(GREEN, pixels.getColor(BOX - 4, BOX - 4))
  }

  private fun render(
    document: RcDocument,
    direction: LayoutDirection = LayoutDirection.Ltr,
  ): Bitmap {
    val scene =
      ImageComposeScene(width = SIZE, height = SIZE, density = Density(1f)) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
          RcComposePlayer(document, Modifier.fillMaxSize())
        }
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(SIZE, SIZE) }
      check(scene.render().readPixels(bitmap))
      return bitmap
    } finally {
      scene.close()
    }
  }

  /** A BOX-square canvas at the origin, clipped by [clip], painting green [overdraw] px past it. */
  private fun clippedCanvas(clip: RcOperation, overdraw: Float = 0f): RcDocument =
    canvas(
      data = emptyList(),
      modifiers = listOf(clip),
      draw =
        listOf(
          RcPaintData(listOf(4, GREEN)),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            literal(-overdraw),
            literal(-overdraw),
            literal(BOX + overdraw),
            literal(BOX + overdraw),
          ),
        ),
    )

  /** A filled quarter disc: origin → (R, 0) → conic through (R, R) to (0, R) → close. */
  private fun conicCanvas(weight: Float): RcDocument {
    val command = { it: Int -> RcFloatWord(0x7fc00000 or it) }
    val pad = literal(0f)
    val path =
      RcPathData(
        PATH_ID,
        listOf(
          command(RcPathCommands.MOVE),
          literal(0f),
          literal(0f),
          command(RcPathCommands.LINE),
          pad,
          pad,
          literal(RADIUS),
          literal(0f),
          command(RcPathCommands.CONIC),
          pad,
          pad,
          literal(RADIUS),
          literal(RADIUS),
          literal(0f),
          literal(RADIUS),
          literal(weight),
          command(RcPathCommands.CLOSE),
          command(RcPathCommands.DONE),
        ),
      )
    return canvas(
      data = listOf(path),
      modifiers = emptyList(),
      draw = listOf(RcPaintData(listOf(4, GREEN)), RcIdOperation(RcOpcodes.DRAW_PATH, PATH_ID)),
    )
  }

  private fun canvas(
    data: List<RcOperation>,
    modifiers: List<RcOperation>,
    draw: List<RcOperation>,
  ): RcDocument =
    RcDocument(
      RcHeader(
        RcVersion(1, 0, 0),
        properties =
          listOf(
            RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(SIZE)),
            RcHeaderProperty(RcHeader.DOC_HEIGHT, RcHeaderValue.IntValue(SIZE)),
            RcHeaderProperty(
              RcHeader.DOC_DENSITY_BEHAVIOR,
              RcHeaderValue.IntValue(RcHeader.DENSITY_BEHAVIOR_DP),
            ),
          ),
      ),
      data +
        listOf(
          RcRootLayout(1),
          RcLayoutContent(2),
          RcCanvasLayout(3, 30),
          RcWidthModifier(RcDimensionType.EXACT, literal(BOX.toFloat())),
          RcHeightModifier(RcDimensionType.EXACT, literal(BOX.toFloat())),
        ) +
        modifiers +
        listOf(RcNoArg(RcOpcodes.CANVAS_OPERATIONS)) +
        draw +
        List(4) { RcNoArg(RcOpcodes.CONTAINER_END) },
    )

  /** Reads pixels relative to a box whose left edge is at [left]. */
  private class Probe(private val bitmap: Bitmap, private val left: Int) {
    fun getColor(x: Int, y: Int): Int = bitmap.getColor(left + x, y)
  }

  private companion object {
    const val SIZE = 100
    const val BOX = 80
    const val RADIUS = 80f
    const val PATH_ID = 20
    val GREEN = 0xff00ff00.toInt()
    val ROUNDED =
      RcRoundedClipRectModifier(
        topStart = literal(40f),
        topEnd = literal(0f),
        bottomStart = literal(20f),
        bottomEnd = literal(4f),
      )

    fun literal(value: Float) = RcFloatWord.literal(value)
  }
}

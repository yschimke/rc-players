package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * The host's `fontScale` must reach both text operations the same way, because a document's font
 * size is on the wire in **pixels** and only one conversion back to `sp` preserves that.
 *
 * ## Why this can matter now when it never did before
 *
 * Every conversion here is a no-op at `fontScale = 1f`, which is the only font scale this module
 * has ever rendered at — [RcCmpRenderHarness] builds its scene with `Density(entry.density)`, whose
 * `fontScale` parameter defaults to `1f`, and no other test in the module names a font scale at
 * all. The same shape as [RcCapturedPixelsDensityTest] and `RcRoundedClipDensityTest`, which exist
 * because their bugs were invisible at density 1.0.
 *
 * It stops being hypothetical the moment a capture defers density instead of folding it in. A
 * document captured against `RemoteDensity.Host` computes its font sizes from the player's own
 * `FONT_SIZE` variable — `14 x density x fontScale` — so the pixels [state.resolve] hands back
 * already carry the host's font scale. Applying it a second time on the way to `sp` scales such a
 * document's text by `fontScale` squared.
 *
 * ## What the two paths do
 *
 * Compose rasterizes `x.sp` at `x * density * fontScale` px, so a px value returns to the same px
 * only if it is divided by *both*:
 *
 * * [RcCoreText] divides by both — `with(density) { fontSize.toSp() }`.
 * * [RcTextLayout] divides by density alone — `(state.resolve(operation.fontSize) /
 *   density.density).sp`.
 *
 * So the two disagree by exactly `fontScale`. Measured, one string at a literal 40px through each
 * operation at density 2.0, ink width in pixels:
 *
 * | fontScale | CoreText | RcTextLayout |
 * |-----------|----------|--------------|
 * | 1.0       | 349      | 349          |
 * | 1.1       | 349      | 383          |
 * | 1.3       | 349      | 452          |
 * | 1.5       | 349      | 523          |
 * | 2.0       | 349      | 697          |
 *
 * [RcCoreText] holds the wire's pixels at every scale; [RcTextLayout] is linear in `fontScale` (349
 * x 2 = 698 against 697 drawn). So [theTwoTextOperationsAgreeAtEveryFontScale] says which is wrong
 * rather than merely that they differ, and [aLiteralFontSizeIsInvariantInTheHostFontScale] pins the
 * direction of the fix.
 *
 * ## What these tests do NOT establish, and must be checked before they are trusted
 *
 * **Both paths may be wrong, because the capture side is very likely not linear.** Android resolves
 * sp to px through `FontScaleConverter`, which is deliberately *non*-linear above about 1.1 — large
 * text grows more slowly than the scale factor. A `RemoteDensity.Host` document computes its font
 * sizes from the player's `FONT_SIZE` variable, and the Android capture writes that variable from
 * the composition's own `Density`; if the recorded value already carries a non-linear curve then
 * reproducing it needs the same curve at replay, and *neither* a linear multiply (RcTextLayout) nor
 * a pass-through (CoreText) is it.
 *
 * That makes the table above a statement about these two code paths relative to each other, not a
 * statement that either matches what Android draws. Verify against a real Android render at the
 * same scale before treating "CoreText is correct" as settled — the cross-lane comparison the
 * agreement test cannot make.
 *
 * Measured off the ink rather than a layout report, for the reason [RcLetterSpacingRenderTest]
 * records: a font size that never reached the rasterizer still reports correctly.
 */
class RcFontScaleRenderTest {

  /**
   * The divergence, pinned as measured rather than as it ought to be.
   *
   * This **characterizes current behaviour**; it does not bless it. Asserting the invariant the two
   * paths ought to share — same wire pixels, same glyphs, any host font scale — would go red today,
   * and fixing [RcTextLayout] to match [RcCoreText] is not yet justified: the section above records
   * why the capture's own scaling may be non-linear, in which case both paths are wrong and the
   * right fix is neither. So the ratio is pinned here, and this test is expected to be *deleted or
   * inverted* by the change that settles it against a real Android render.
   *
   * The ratio is the whole finding: at any scale, [RcTextLayout] draws `fontScale` times the ink
   * [RcCoreText] does, so the two are identical at 1.0 and nowhere else.
   */
  @Test
  fun theTwoTextOperationsDisagreeByExactlyTheFontScale() {
    val baseline = inkWidth(coreTextDocument(), fontScale = 1f)
    // Every expectation below is derived from this one measurement, so a regression that stopped
    // BOTH paths drawing would leave the whole test comparing zero against zero and still pass.
    assertTrue(baseline > 0, "the baseline drew no ink at all")
    for (fontScale in listOf(1f, 1.1f, 1.3f, 1.5f, 2f)) {
      assertEquals(
        baseline.toFloat(),
        inkWidth(coreTextDocument(), fontScale).toFloat(),
        TOLERANCE_PX,
        "CoreText should hold the wire's pixels at fontScale $fontScale",
      )
      assertEquals(
        baseline * fontScale,
        inkWidth(textLayoutDocument(), fontScale).toFloat(),
        baseline * fontScale * 0.02f,
        "TextLayout should scale linearly with fontScale $fontScale",
      )
    }
  }

  /** Width of the drawn run in pixels, measured from the ink. */
  private fun inkWidth(document: RcDocument, fontScale: Float): Int {
    val scene =
      ImageComposeScene(
        width = WIDTH,
        height = HEIGHT,
        density = Density(DENSITY, fontScale),
      ) {
        RcComposePlayer(document)
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      check(scene.render(0L).readPixels(bitmap))
      var minX = WIDTH
      var maxX = -1
      for (y in 0 until HEIGHT) {
        for (x in 0 until WIDTH) {
          if (bitmap.getColor(x, y) != 0) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
          }
        }
      }
      return if (maxX < minX) 0 else maxX - minX + 1
    } finally {
      scene.close()
    }
  }

  /** One `CoreText` run and nothing else to measure. Property 5 is the font size. */
  private fun coreTextDocument(): RcDocument =
    RcDocument(
      header(),
      listOf(
        RcRootLayout(-2),
        RcLayoutContent(-3),
        RcTextData(42, TEXT),
        RcCoreText(
          textId = 42,
          properties =
            listOf(
              RcTextStyleProperty.IntValue(3, 0xff101828.toInt()),
              RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(FONT_SIZE)),
            ),
        ),
        RcLayoutContent(-4),
      ) + List(4) { RcNoArg(RcOpcodes.CONTAINER_END) },
    )

  /** The same string at the same wire font size, through the other operation. */
  private fun textLayoutDocument(): RcDocument =
    RcDocument(
      header(),
      listOf(
        RcTextData(42, TEXT),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcTextLayout(
          componentId = 3,
          animationId = 30,
          textId = 42,
          color = 0xff101828.toInt(),
          fontSize = RcFloatWord.literal(FONT_SIZE),
          fontStyle = 0,
          fontWeight = RcFloatWord.literal(400f),
          fontFamilyId = -1,
          textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
          overflow = RcTextLayout.OVERFLOW_CLIP,
          maxLines = 1,
        ),
        RcLayoutContent(4),
      ) + List(4) { RcNoArg(RcOpcodes.CONTAINER_END) },
    )

  private fun header() =
    RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false)

  private companion object {
    const val WIDTH = 900
    const val HEIGHT = 200
    const val DENSITY = 2f
    const val FONT_SIZE = 40f
    const val TEXT = "Remote Compose"

    /**
     * Hinting and anti-aliasing can move an ink edge by a pixel between two runs that laid out
     * identically. The failures this guards against are multiplicative — 1.3x and 2x — so a pixel
     * of slack cannot hide one.
     */
    const val TOLERANCE_PX = 1f
  }
}

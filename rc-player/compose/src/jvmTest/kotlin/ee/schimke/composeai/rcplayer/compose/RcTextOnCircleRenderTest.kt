package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw3
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextOnCircle
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * `DrawTextOnCircle` (op 57) end to end: bytes in, ink on an arc out.
 *
 * The assertions are geometric rather than golden-image, because what can go wrong here is
 * geometry. Ink has to land *on the ring* the operation names and nowhere else — a sweep computed
 * from the wrong radius, an alignment applied with the wrong sign, or an arc built from the
 * unwarped radius all still draw glyphs, just in the wrong place, and a "did anything draw?" check
 * would pass for every one of them.
 */
class RcTextOnCircleRenderTest {

  @Test
  fun glyphsLandOnTheNamedRingAndNowhereElse() {
    val bitmap = render(document(alignment = RcDrawTextOnCircle.ALIGN_CENTER))

    assertTrue(inkOnRing(bitmap) > 0, "nothing drew on the ring the operation names")
    assertEquals(0, inkInDisc(bitmap, RADIUS - 24f), "ink landed well inside the ring")
    assertEquals(0, inkOutsideDisc(bitmap, RADIUS + 24f), "ink landed well outside the ring")
  }

  @Test
  fun alignmentMovesTheStringAroundTheRingWithoutLeavingIt() {
    // START pins the string's leading edge at the start angle and END its trailing edge, so the
    // two cannot put ink in the same places — that difference is the whole of the alignment
    // arithmetic. Both must still sit on the ring.
    val start = render(document(alignment = RcDrawTextOnCircle.ALIGN_START))
    val end = render(document(alignment = RcDrawTextOnCircle.ALIGN_END))

    assertTrue(inkOnRing(start) > 0 && inkOnRing(end) > 0)
    assertTrue(
      ringSignature(start) != ringSignature(end),
      "START and END put the string in the same place; the alignment offset is not applied",
    )
    assertEquals(0, inkInDisc(start, RADIUS - 24f))
    assertEquals(0, inkInDisc(end, RADIUS - 24f))
  }

  @Test
  fun insidePlacementRunsTheOtherWayRoundTheRing() {
    // Deliberately not ALIGN_CENTER: a centred string straddles the start angle whichever way it
    // travels, so it covers the same arc under both placements and this comparison would pass on a
    // renderer that ignored placement entirely. Pinned at its start, the two run opposite ways.
    val outside =
      render(
        document(
          alignment = RcDrawTextOnCircle.ALIGN_START,
          placement = RcDrawTextOnCircle.PLACEMENT_OUTSIDE,
        )
      )
    val inside =
      render(
        document(
          alignment = RcDrawTextOnCircle.ALIGN_START,
          placement = RcDrawTextOnCircle.PLACEMENT_INSIDE,
        )
      )

    assertTrue(inkOnRing(outside) > 0 && inkOnRing(inside) > 0)
    assertTrue(
      ringSignature(outside) != ringSignature(inside),
      "OUTSIDE and INSIDE drew identically; the sweep is not being negated",
    )
  }

  @Test
  fun aNonPositiveRadiusDrawsNothingRatherThanThrowing() {
    // The radius is a NaN-encoded word, so a live document can animate it through zero. Dividing
    // the text width by it would produce an infinite sweep; the renderer has to decline instead.
    val bitmap = render(document(radius = 0f, warpRadiusOffset = 0f))

    assertEquals(0, inkAnywhere(bitmap))
  }

  @Test
  fun writesTheBeforeAfterEvidenceStrip() {
    // Opt-in, on the `rc.embedded.input` pattern this repo already uses: skipped (as a pass) unless
    // an output path is named, so CI does not write files while `scripts/` can produce the PR's
    // evidence on demand. Run with:
    //   ./gradlew :rc-player-compose:jvmTest --rerun \
    //     --tests '*RcTextOnCircleRenderTest*' -Drc.textOnCircle.out=<abs dir>
    val out = System.getProperty("rc.textOnCircle.out") ?: return
    val directory = java.io.File(out).also { it.mkdirs() }

    // "Before" is the same dial with the operation deleted — which is exactly what the player drew
    // for this document until now, once you got a document at all. On `main` op 57 has no codec, so
    // `decode` throws before any of this: the honest before-picture is the rest of the dial.
    val before = render(dial(withCurvedText = false))
    val after = render(dial(withCurvedText = true))

    val strip = Bitmap().apply { allocN32Pixels(SIZE * 2 + GAP, SIZE) }
    org.jetbrains.skia.Canvas(strip).apply {
      clear(0xfff5f5f5.toInt())
      drawImage(org.jetbrains.skia.Image.makeFromBitmap(before), 0f, 0f)
      drawImage(org.jetbrains.skia.Image.makeFromBitmap(after), (SIZE + GAP).toFloat(), 0f)
    }
    val encoded =
      checkNotNull(org.jetbrains.skia.Image.makeFromBitmap(strip).encodeToData()) {
        "failed to encode the evidence strip"
      }
    java.io.File(directory, "text-on-circle-before-after.png").writeBytes(encoded.bytes)
  }

  /** A dial that draws with or without its curved label, so the pair is a real before/after. */
  private fun dial(withCurvedText: Boolean): RcDocument {
    val operations = mutableListOf<RcOperation>()
    operations += RcTextData(7, "REMOTE COMPOSE")
    operations += RcRootLayout(1)
    operations += RcLayoutContent(2)
    operations += RcCanvasLayout(3, 30)
    operations += RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE.toFloat()))
    operations += RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE.toFloat()))
    operations += RcNoArg(RcOpcodes.CANVAS_OPERATIONS)
    // The dial itself: a stroked circle, so the "before" panel is a picture rather than a blank.
    operations +=
      RcPaintData(listOf(8 or (1 shl 16), 5, RcFloatWord.literal(2f).bits, 4, 0xff9aa0a6.toInt()))
    operations +=
      RcDraw3(
        RcOpcodes.DRAW_CIRCLE,
        RcFloatWord.literal(CENTER),
        RcFloatWord.literal(CENTER),
        RcFloatWord.literal(RADIUS),
      )
    if (withCurvedText) {
      operations += RcPaintData(listOf(8, 1, RcFloatWord.literal(18f).bits, 4, 0xff1a73e8.toInt()))
      operations +=
        RcDrawTextOnCircle(
          textId = 7,
          centerX = RcFloatWord.literal(CENTER),
          centerY = RcFloatWord.literal(CENTER),
          radius = RcFloatWord.literal(RADIUS - WARP),
          startAngle = RcFloatWord.literal(-90f),
          warpRadiusOffset = RcFloatWord.literal(WARP),
          alignment = RcDrawTextOnCircle.ALIGN_CENTER,
          placement = RcDrawTextOnCircle.PLACEMENT_OUTSIDE,
        )
    }
    repeat(4) { operations += RcNoArg(RcOpcodes.CONTAINER_END) }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = SIZE, legacyHeight = SIZE, modern = false),
      operations,
    )
  }

  private fun document(
    alignment: Int = RcDrawTextOnCircle.ALIGN_CENTER,
    placement: Int = RcDrawTextOnCircle.PLACEMENT_OUTSIDE,
    radius: Float = RADIUS - WARP,
    warpRadiusOffset: Float = WARP,
  ): RcDocument {
    val operations = mutableListOf<RcOperation>()
    operations += RcTextData(7, "REMOTE COMPOSE")
    operations += RcRootLayout(1)
    operations += RcLayoutContent(2)
    operations += RcCanvasLayout(3, 30)
    operations += RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE.toFloat()))
    operations += RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(SIZE.toFloat()))
    operations += RcNoArg(RcOpcodes.CANVAS_OPERATIONS)
    // Command 1 is text size, command 4 is colour: opaque black on the default white ground, so
    // "is there ink here" is a plain darkness test.
    operations += RcPaintData(listOf(1, RcFloatWord.literal(18f).bits, 4, 0xff000000.toInt()))
    operations +=
      RcDrawTextOnCircle(
        textId = 7,
        centerX = RcFloatWord.literal(CENTER),
        centerY = RcFloatWord.literal(CENTER),
        radius = RcFloatWord.literal(radius),
        startAngle = RcFloatWord.literal(-90f),
        warpRadiusOffset = RcFloatWord.literal(warpRadiusOffset),
        alignment = alignment,
        placement = placement,
      )
    // Four opens above — root, content, the canvas layout and its operation block — so four ends.
    repeat(4) { operations += RcNoArg(RcOpcodes.CONTAINER_END) }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = SIZE, legacyHeight = SIZE, modern = false),
      operations,
    )
  }

  private fun render(document: RcDocument): Bitmap {
    // Through the codec, not straight from the model: op 57 decoding correctly is half of what
    // this test is for, and building the document in memory would skip it.
    val decoded = RcDocumentCodec.decode(RcDocumentCodec.encode(document))
    val support = decoded.composeSupportReport()
    assertTrue(support.fullyRenderable, "document is not renderable: ${support.issues}")
    val scene =
      ImageComposeScene(width = SIZE, height = SIZE, density = Density(1f)) {
        RcComposePlayer(decoded)
      }
    return try {
      Bitmap()
        .apply { allocN32Pixels(SIZE, SIZE) }
        .also { assertTrue(scene.render(0L).readPixels(it)) }
    } finally {
      scene.close()
    }
  }

  /** Dark pixels within a few points of the ring's own radius. */
  private fun inkOnRing(bitmap: Bitmap): Int =
    countInk(bitmap) { distance -> distance in (RADIUS - 16f)..(RADIUS + 16f) }

  private fun inkInDisc(bitmap: Bitmap, radius: Float): Int =
    countInk(bitmap) { distance -> distance < radius }

  private fun inkOutsideDisc(bitmap: Bitmap, radius: Float): Int =
    countInk(bitmap) { distance -> distance > radius }

  private fun inkAnywhere(bitmap: Bitmap): Int = countInk(bitmap) { true }

  private fun countInk(bitmap: Bitmap, accept: (Float) -> Boolean): Int {
    var count = 0
    for (y in 0 until SIZE) {
      for (x in 0 until SIZE) {
        if (!isInk(bitmap, x, y)) continue
        val dx = x - CENTER
        val dy = y - CENTER
        if (accept(kotlin.math.sqrt(dx * dx + dy * dy))) count++
      }
    }
    return count
  }

  private fun isInk(bitmap: Bitmap, x: Int, y: Int): Boolean {
    val colour = bitmap.getColor(x, y)
    if ((colour ushr 24) and 0xff < 0x40) return false
    val luminance =
      ((colour shr 16) and 0xff) * 0.299 +
        ((colour shr 8) and 0xff) * 0.587 +
        (colour and 0xff) * 0.114
    return luminance < 128
  }

  /**
   * Where the ink sits *around* the ring, as 36 ten-degree buckets. Two layouts of the same string
   * on the same ring differ here and nowhere a pixel count would notice.
   */
  private fun ringSignature(bitmap: Bitmap): List<Boolean> {
    val buckets = BooleanArray(36)
    for (y in 0 until SIZE) {
      for (x in 0 until SIZE) {
        if (!isInk(bitmap, x, y)) continue
        val angle = kotlin.math.atan2((y - CENTER).toDouble(), (x - CENTER).toDouble())
        val degrees = (angle * 180.0 / PI + 360.0) % 360.0
        buckets[(degrees / 10.0).toInt().coerceIn(0, 35)] = true
      }
    }
    return buckets.toList()
  }

  private companion object {
    const val SIZE = 240
    const val CENTER = 120f
    const val RADIUS = 90f
    /** Non-zero so a renderer that ignored the warp offset would draw on the wrong ring. */
    const val WARP = 10f
    const val GAP = 16
  }
}

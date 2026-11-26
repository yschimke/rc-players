package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import java.io.File
import kotlin.math.abs
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
   * A child moved with `offset` is still inside its parent's layer, so it is faded with the rest of
   * it whatever the strategy, as AndroidX's embedded player draws it.
   *
   * Compose's lambda `Modifier.offset` places its child with a layer of its own, and on Skiko a
   * `ModulateAlpha` layer fades only its own draw calls: a child layer is drawn into the raw canvas
   * at full alpha. So the offset child of a half-alpha `ModulateAlpha` layer used to be opaque.
   * `Auto` and `Offscreen` fade the layer as one image and were never affected; they are here as
   * parity guards, so that placing the child differently cannot break them.
   */
  @Test
  fun anOffsetChildIsFadedWithItsParentLayer() {
    for (strategy in
      listOf(
        RcGraphicsLayerModifier.COMPOSITING_STRATEGY_AUTO,
        RcGraphicsLayerModifier.COMPOSITING_STRATEGY_OFFSCREEN,
        RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA,
      )) {
      val image = render(offsetChildDocument(strategy))
      // The child without an offset, for comparison, then the offset one beside it.
      assertEquals(
        HALF_BLUE,
        image.getColor(STILL_CHILD_X, MID_Y),
        "strategy $strategy: still child",
      )
      assertEquals(
        HALF_RED,
        image.getColor(OFFSET_CHILD_X, MID_Y),
        "strategy $strategy: offset child",
      )
    }
  }

  /**
   * A blur on the `ModulateAlpha` layer makes Skiko composite the layer through a paint that
   * carries its alpha, on top of the alpha it already multiplies into the layer's own draws. A
   * child layer is faded once by that paint, so an offset keeps its layer there; placed without one
   * it would be faded twice, to a quarter. AndroidX fades the child to a half.
   *
   * The still child is the layer's own draw, so Skiko fades it twice whatever the offset does;
   * AndroidX draws it at a half. That is Compose Multiplatform's `SkiaGraphicsLayer`, not this
   * player, and it is pinned here so that a fix upstream shows up.
   */
  @Test
  fun anOffsetChildOfABlurredModulateAlphaLayerIsFadedOnce() {
    val image =
      render(
        offsetChildDocument(
          RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA,
          blur = RcFloatWord.literal(1f),
        ),
      )
    assertFaded(RED, HALF_ALPHA, image.getColor(OFFSET_CHILD_X, MID_Y), "offset child")
    assertFaded(
      BLUE,
      QUARTER_ALPHA,
      image.getColor(STILL_CHILD_X, MID_Y),
      "still child, Skiko's own quirk",
    )
  }

  /**
   * The blur and the alpha can move after the first frame, so whether the offset needs a layer is
   * decided each time it is placed. A blur a host turns on mid-way keeps the offset child at a half
   * in every frame of the tween, and the offset keeps fading once the blur is gone again.
   */
  @Test
  fun anOffsetChildStaysFadedOnceWhileTheBlurChanges() {
    val namedValues = mutableStateMapOf<String, RcNamedValue>()
    val document =
      offsetChildDocument(
        RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA,
        blur = variable(BLUR_ID),
      )
    scene(document, namedValues) {
      assertFaded(RED, HALF_ALPHA, pixel(0L, OFFSET_CHILD_X), "before the blur")
      var nanos = 0L
      for (blur in listOf(1f, 0f)) {
        namedValues["USER:blur"] = RcNamedValue.FloatValue(blur)
        Snapshot.sendApplyNotifications()
        repeat(STEPS) {
          nanos += FRAME
          assertFaded(
            RED,
            HALF_ALPHA,
            pixel(nanos, OFFSET_CHILD_X),
            "blur -> $blur, frame at $nanos",
          )
        }
      }
    }
  }

  /**
   * Outside a `ModulateAlpha` layer an offset keeps the layer Compose's lambda `Modifier.offset`
   * gives it, so an animated offset still moves without redrawing what is around it. Skiko's own
   * quirk is the probe: the player inside a plain Compose `ModulateAlpha` layer, with no layer in
   * the document. The still child is drawn into the probe and faded, which shows there is no layer
   * of the player's own in between; the offset child stays opaque only if the offset has a layer.
   */
  @Test
  fun anOffsetKeepsItsLayerOutsideAModulateAlphaLayer() {
    scene(offsetChildDocument(strategy = null, alpha = null), probe = true) {
      assertEquals(HALF_BLUE, pixel(0L, STILL_CHILD_X), "the probe reaches the player's draws")
      assertEquals(RED, pixel(0L, OFFSET_CHILD_X), "the offset is placed with a layer")
    }
  }

  /**
   * When padding comes before a later offset, the press handlers are wrapped in a pair of offsets
   * that move them with it and back (see `applyComponentModifiers`). That pair is inside the layer
   * too, so it is placed without a layer as the offset is, or the child would still be opaque.
   */
  @Test
  fun thePressHandlerOffsetsOfAModulateAlphaChildAreFaded() {
    val image =
      render(
        offsetChildDocument(
          RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA,
          pressHandlers = true,
        ),
      )
    assertEquals(HALF_BLUE, image.getColor(STILL_CHILD_X, MID_Y))
    assertEquals(HALF_RED, image.getColor(OFFSET_CHILD_X, MID_Y))
  }

  /** An offset a host moves after the first frame is placed afresh, and still faded. */
  @Test
  fun anOffsetThatMovesAfterTheFirstFrameIsStillFaded() {
    val namedValues = mutableStateMapOf<String, RcNamedValue>()
    val document =
      offsetChildDocument(
        RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA,
        offsetX = variable(OFFSET_ID),
      )
    scene(document, namedValues) {
      assertEquals(HALF_RED, pixel(0L, OFFSET_CHILD_X))
      namedValues["USER:offset"] = RcNamedValue.FloatValue(20f)
      Snapshot.sendApplyNotifications()
      val settled = STEPS * FRAME
      assertEquals(HALF_RED, pixel(settled, MOVED_CHILD_X), "moved to 20..40")
      assertEquals(0, pixel(settled, OFFSET_CHILD_X), "gone from 40..60")
    }
  }

  /**
   * A host's next document whose layer is equal to the last one's keeps the layer's holder in
   * `RenderLayoutNode` (`remember` compares with `==`), and its layer reads its alpha from the next
   * document's state: here a host value written with the swap, which that state starts from while
   * the last document's stays at 1. The offset follows the new alpha only if the next chain finds
   * the kept holder and binds the new reader to it.
   */
  @Test
  fun anOffsetChildFollowsTheNextDocumentsAlpha() {
    val strategy = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA
    val namedValues = mutableStateMapOf<String, RcNamedValue>()
    var document by mutableStateOf(offsetChildDocument(strategy, alpha = variable(ALPHA_ID)))
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(document, namedValues = namedValues)
      }
    try {
      assertEquals(RED, scene.pixel(0L, OFFSET_CHILD_X), "alpha 1")
      Snapshot.withMutableSnapshot {
        namedValues["USER:alpha"] = RcNamedValue.FloatValue(0.5f)
        document = offsetChildDocument(strategy, alpha = variable(ALPHA_ID), stillChildGreen = true)
      }
      Snapshot.sendApplyNotifications()
      assertEquals(HALF_GREEN, scene.pixel(FRAME, STILL_CHILD_X), "the next document at alpha 0.5")
      assertEquals(HALF_RED, scene.pixel(2 * FRAME, OFFSET_CHILD_X), "its offset child")
    } finally {
      scene.close()
    }
  }

  /**
   * A `ModulateAlpha` layer inside another: the inner layer is a child layer of the outer one, so
   * on Skiko it misses the outer alpha (a known limitation, see the README), and an offset in it is
   * faded by the inner layer alone, once. AndroidX fades it by both, to a quarter.
   */
  @Test
  fun anOffsetChildOfANestedModulateAlphaLayerIsFadedByTheInnerOne() {
    val image =
      render(
        offsetChildDocument(
          RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA,
          outerModulateAlpha = true,
        ),
      )
    assertEquals(HALF_BLUE, image.getColor(STILL_CHILD_X, MID_Y))
    assertEquals(HALF_RED, image.getColor(OFFSET_CHILD_X, MID_Y), "known limitation: not a quarter")
  }

  /**
   * **Known limitation, pinned.** Any other child that has a layer of its own still misses a
   * `ModulateAlpha` parent's alpha on Skiko. In this player that is a clipped child (`clip` is a
   * layer), a scroll container, a nested graphics layer, a component offscreen buffer, a visibility
   * transition, an image (drawn inside `clipToBounds`), a component with a `MEASURE` layout compute
   * (also `clipToBounds`), an embedded document (`clipToBounds` around its player) and a marquee
   * (`basicMarquee` places its content with a layer). Only offsets are placed without one. This
   * pins the clip case as it is drawn today, opaque, where AndroidX fades it; when it is fixed,
   * flip the expectation to [HALF_RED].
   */
  @Test
  fun aClippedChildOfAModulateAlphaLayerStillMissesItsAlpha() {
    val image =
      render(
        offsetChildDocument(
          RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA,
          clipOffsetChild = true,
        ),
      )
    assertEquals(HALF_BLUE, image.getColor(STILL_CHILD_X, MID_Y))
    assertEquals(RED, image.getColor(OFFSET_CHILD_X, MID_Y), "known limitation: still opaque")
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

  /**
   * A 60x20 box with a graphics layer of [strategy], [alpha] and [blur], and two 20x20 children: a
   * blue one where the box puts it, and a red one the box puts there too and `offset` moves
   * [offsetX] to the right, clipped to its bounds when [clipOffsetChild] is set. With
   * [pressHandlers] the red one has a padding before its offset and a click, so its press handlers
   * are wrapped in offsets of their own. The box has no layer when [strategy], [alpha] and [blur]
   * are all null. The blue child is green instead with [stillChildGreen], and the box sits in a
   * half-alpha `ModulateAlpha` box of the same size with [outerModulateAlpha]. `USER:offset`
   * ([OFFSET_ID], 40), `USER:blur` ([BLUR_ID], 0) and `USER:alpha` ([ALPHA_ID], 1) are declared for
   * [variable].
   */
  private fun offsetChildDocument(
    strategy: Int?,
    clipOffsetChild: Boolean = false,
    alpha: RcFloatWord? = RcFloatWord.literal(0.5f),
    blur: RcFloatWord? = null,
    offsetX: RcFloatWord = RcFloatWord.literal(OFFSET_CHILD_SHIFT),
    pressHandlers: Boolean = false,
    stillChildGreen: Boolean = false,
    outerModulateAlpha: Boolean = false,
  ): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    fun child(id: Int, red: Float, blue: Float, offset: Boolean, green: Float = 0f) = buildList {
      add(RcBoxLayout(id, 30 + id, 1, 1))
      if (offset) {
        if (pressHandlers) {
          val zero = RcFloatWord.literal(0f)
          add(RcPaddingModifier(zero, zero, zero, zero))
        }
        add(RcOffsetModifier(offsetX, RcFloatWord.literal(0f)))
        if (clipOffsetChild) add(RcClipRectModifier)
      }
      add(RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat())))
      add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat())))
      add(
        RcBackgroundModifier(
          flags = 0,
          colorId = 0,
          reserved1 = 0,
          reserved2 = 0,
          red = RcFloatWord.literal(red),
          green = RcFloatWord.literal(green),
          blue = RcFloatWord.literal(blue),
          alpha = RcFloatWord.literal(1f),
          shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
        ),
      )
      if (offset && pressHandlers) {
        add(RcClickModifier)
        add(RcHostAction(77))
        add(end)
      }
      add(RcLayoutContent(id + 1))
      add(end)
      add(end)
    }
    val layer = buildList {
      alpha?.let { add(RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.ALPHA, it)) }
      strategy?.let {
        add(RcGraphicsLayerAttribute.IntValue(RcGraphicsLayerModifier.COMPOSITING_STRATEGY, it))
      }
      blur?.let {
        add(RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.BLUR_RADIUS_X, it))
        add(RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.BLUR_RADIUS_Y, it))
      }
    }
    val operations =
      buildList<RcOperation> {
        add(RcFloatConstant(OFFSET_ID, RcFloatWord.literal(OFFSET_CHILD_SHIFT)))
        add(RcNamedVariable(OFFSET_ID, RcNamedVariable.FLOAT_TYPE, "USER:offset"))
        add(RcFloatConstant(BLUR_ID, RcFloatWord.literal(0f)))
        add(RcNamedVariable(BLUR_ID, RcNamedVariable.FLOAT_TYPE, "USER:blur"))
        add(RcFloatConstant(ALPHA_ID, RcFloatWord.literal(1f)))
        add(RcNamedVariable(ALPHA_ID, RcNamedVariable.FLOAT_TYPE, "USER:alpha"))
        add(RcRootLayout(1))
        add(RcLayoutContent(2))
        if (outerModulateAlpha) {
          add(RcBoxLayout(5, 35, 1, 1))
          add(
            RcGraphicsLayerModifier(
              listOf(
                RcGraphicsLayerAttribute.FloatValue(
                  RcGraphicsLayerModifier.ALPHA,
                  RcFloatWord.literal(0.5f),
                ),
                RcGraphicsLayerAttribute.IntValue(
                  RcGraphicsLayerModifier.COMPOSITING_STRATEGY,
                  RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA,
                ),
              ),
            ),
          )
          add(RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(WIDTH.toFloat())))
          add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat())))
          add(RcLayoutContent(6))
        }
        add(RcBoxLayout(3, 30, 1, 1))
        if (layer.isNotEmpty()) add(RcGraphicsLayerModifier(layer))
        add(RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(WIDTH.toFloat())))
        add(RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat())))
        add(RcLayoutContent(4))
        if (stillChildGreen) addAll(child(10, red = 0f, blue = 0f, offset = false, green = 1f))
        else addAll(child(10, red = 0f, blue = 1f, offset = false))
        addAll(child(20, red = 1f, blue = 0f, offset = true))
        repeat(4) { add(end) }
        if (outerModulateAlpha) repeat(2) { add(end) }
      }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      operations,
    )
  }

  /** A float the document reads from the variable [id] declares. */
  private fun variable(id: Int) = RcFloatWord(0x7fc00000 or id)

  /**
   * Runs [block] on a scene of the player drawing [document] with [namedValues], inside a plain
   * Compose half-alpha `ModulateAlpha` layer when [probe] is set.
   */
  private fun <T> scene(
    document: RcDocument,
    namedValues: SnapshotStateMap<String, RcNamedValue> = mutableStateMapOf(),
    probe: Boolean = false,
    block: ImageComposeScene.() -> T,
  ): T {
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        if (probe) {
          Box(
            Modifier.size(WIDTH.dp, HEIGHT.dp).graphicsLayer {
              alpha = 0.5f
              compositingStrategy = CompositingStrategy.ModulateAlpha
            },
          ) {
            RcComposePlayer(document, namedValues = namedValues)
          }
        } else {
          RcComposePlayer(document, namedValues = namedValues)
        }
      }
    try {
      return scene.block()
    } finally {
      scene.close()
    }
  }

  /** The colour at ([x], [MID_Y]) of the frame at [nanos]. */
  private fun ImageComposeScene.pixel(nanos: Long, x: Int): Int {
    val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
    check(render(nanos).readPixels(bitmap))
    return bitmap.getColor(x, MID_Y)
  }

  /**
   * [color] is the opaque [rgb] at [alpha], give or take one step of rounding in the alpha and two
   * in each colour channel (they are unpremultiplied from a fainter pixel).
   */
  private fun assertFaded(rgb: Int, alpha: Int, color: Int, message: String) {
    val channels =
      listOf(Color::getA, Color::getR, Color::getG, Color::getB).map { it(color) to it(rgb) }
    val (actualAlpha, _) = channels[0]
    val colourMatches = channels.drop(1).all { (actual, expected) -> abs(actual - expected) <= 2 }
    assertTrue(
      abs(actualAlpha - alpha) <= 1 && colourMatches,
      "$message: ${color.toUInt().toString(16)}, expected ${rgb.toUInt().toString(16)} at alpha " +
        alpha.toString(16),
    )
  }

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
    const val OFFSET_CHILD_SHIFT = 40f
    const val OFFSET_CHILD_X = 50
    const val STILL_CHILD_X = 10
    const val MOVED_CHILD_X = 30
    const val OFFSET_ID = 42
    const val BLUR_ID = 43
    const val ALPHA_ID = 61
    const val FRAME = 16_000_000L
    const val STEPS = 30
    const val HALF_ALPHA = 0x80
    const val QUARTER_ALPHA = 0x40
    const val RED = 0xffff0000.toInt()
    const val HALF_RED = 0x80ff0000.toInt()
    const val HALF_BLUE = 0x800000ff.toInt()
    const val HALF_GREEN = 0x8000ff00.toInt()
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

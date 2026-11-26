package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionDefine
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcScrollModifier
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcVisibilityModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image

/**
 * Other graphics layers the player inserts (`RcClipRectModifier`, `RcRoundedClipRectModifier`,
 * `RcComponentOffscreen` capture, and `RcVisibilityTransition` when its enter/exit transition uses
 * a layer) isolate their descendants from an enclosing `ModulateAlpha` layer on Skiko, just as an
 * embedded document's `clipToBounds` does. Offsets inside them keep their own layer, and swapping
 * the host layer's strategy, alpha or blur keeps every subtree intact.
 */
class RcPlayerLayersModulateAlphaTest {

  @Test
  fun clipRectChildStartsWithNoModulateAlphaLayerAndKeepsItsSubtree() {
    assertClipClearsLayerAndKeepsSubtree(rounded = false)
  }

  @Test
  fun roundedClipRectChildStartsWithNoModulateAlphaLayerAndKeepsItsSubtree() {
    assertClipClearsLayerAndKeepsSubtree(rounded = true)
  }

  @Test
  fun clipRectPixelsMatchBetweenStillAndOffsetChildren() {
    for (rounded in listOf(false, true)) {
      for ((strategy, blur, expectedAlpha) in
        listOf(
          Triple(null, 0f, OPAQUE),
          Triple(MODULATE_ALPHA, 0f, OPAQUE),
          Triple(MODULATE_ALPHA, 4f, HALF),
          Triple(AUTO, 0f, HALF),
          Triple(OFFSCREEN, 0f, HALF),
        )) {
        assertStillAndOffsetAlpha(
          clippedDocument(strategy, blur = blur, rounded = rounded),
          expectedAlpha,
          "rounded=$rounded strategy=$strategy blur=$blur",
        )
      }
    }
  }

  /**
   * An offset written *before* `RcClipRectModifier` on a box with a pre-clip background still draws
   * that background into the host `ModulateAlpha` layer (so the background is faded), whereas a
   * child inside the clip sees no `ModulateAlpha` layer.
   */
  @Test
  fun preClipBackgroundStillFadesWhileInsideClipSeesNoLayer() {
    val probes = Probes()
    val doc =
      RcDocument(
        header(),
        buildList<RcOperation> {
          add(RcTextData(50, INNER_PROBE))
          add(RcRootLayout(1))
          add(RcLayoutContent(2))
          add(RcBoxLayout(3, 30, 1, 1))
          add(layer(MODULATE_ALPHA))
          addAll(size(WIDTH, HEIGHT))
          add(RcLayoutContent(4))
          // Child box: offset before background, then clip, then inner probe.
          add(RcBoxLayout(10, 40, 1, 1))
          add(RcOffsetModifier(literal(20f), literal(0f)))
          addAll(size(20, HEIGHT))
          add(background(red = 1f, blue = 0f))
          add(RcClipRectModifier)
          add(RcLayoutContent(11))
          add(RcCustomLayout(12, 0, 50, emptyList()))
          addAll(size(20, HEIGHT))
          add(end)
          repeat(6) { add(end) }
        },
      )
    probes.scene({ doc }) {
      val bmp = bitmap(render(0L))
      val moved = bmp.getColor(MOVED_X, MID_Y)
      assertEquals(HALF, Color.getA(moved), "pre-clip background should be faded by host layer")
      assertNull(probes.inner.layer, "child inside clip starts with no layer")
      assertPresent(probes.inner, "inner")
    }
  }

  /**
   * A component recorded into a component offscreen buffer (`RcComponentCaptures`) is wrapped in
   * `graphicsLayer()`, so it and its descendants start with no enclosing `ModulateAlpha` layer.
   */
  @Test
  fun componentOffscreenCaptureStartsWithNoModulateAlphaLayer() {
    val probes = Probes()
    var document by mutableStateOf(capturedDocument(strategy = MODULATE_ALPHA))
    probes.scene({ document }) {
      var nanos = 0L
      render(nanos)
      assertNotNull(probes.host.layer, "control: host sibling sees host layer")
      assertNull(probes.inner.layer, "captured component sees no host layer")
      assertPresent(probes.inner, "first")
      for (strategy in listOf(AUTO, OFFSCREEN, null, MODULATE_ALPHA)) {
        swap { document = capturedDocument(strategy = strategy) }
        nanos += FRAME
        render(nanos)
        val step = "strategy $strategy"
        assertNull(probes.inner.layer, "$step: captured component sees no host layer")
        assertPresent(probes.inner, step)
      }
    }
    for ((strategy, blur, expectedAlpha) in
      listOf(
        Triple(null, 0f, OPAQUE),
        Triple(MODULATE_ALPHA, 0f, OPAQUE),
        Triple(MODULATE_ALPHA, 4f, HALF),
        Triple(AUTO, 0f, HALF),
      )) {
      assertStillAndOffsetAlpha(
        capturedDocument(strategy = strategy, blur = blur),
        expectedAlpha,
        "capture strategy=$strategy blur=$blur",
      )
    }
  }

  /**
   * `RcVisibilityTransition` wraps its content in Compose's `AnimatedVisibility`, which places its
   * child in a layer for every enter/exit spec (including slides and `None`). Its descendants start
   * with no enclosing `ModulateAlpha` layer and keep their subtrees across host layer swaps.
   */
  @Test
  fun visibilityTransitionStartsWithNoModulateAlphaLayer() {
    for ((label, spec, layoutAnimations) in
      listOf(
        Triple("fade", animSpec(RcLayoutAnimation.FadeIn, RcLayoutAnimation.FadeOut), true),
        Triple("rotate", animSpec(RcLayoutAnimation.Rotate, RcLayoutAnimation.SlideLeft), true),
        Triple("slide", animSpec(RcLayoutAnimation.SlideLeft, RcLayoutAnimation.SlideRight), true),
        Triple(
          "zero-duration",
          animSpec(RcLayoutAnimation.FadeIn, RcLayoutAnimation.FadeOut, durationMs = 0f),
          true,
        ),
        Triple(
          "animations-disabled",
          animSpec(RcLayoutAnimation.FadeIn, RcLayoutAnimation.FadeOut),
          false,
        ),
      )) {
      val probes = Probes()
      var document by mutableStateOf(visibilityDocument(strategy = MODULATE_ALPHA, spec = spec))
      probes.scene({ document }, layoutAnimations = layoutAnimations) {
        var nanos = 0L
        render(nanos)
        assertNotNull(probes.host.layer, "$label: control host sibling sees host layer")
        assertNull(probes.inner.layer, "$label: visibility transition starts with none")
        assertPresent(probes.inner, "$label: first")
        for (strategy in listOf(AUTO, null, MODULATE_ALPHA)) {
          swap { document = visibilityDocument(strategy = strategy, spec = spec) }
          nanos += FRAME
          render(nanos)
          assertNull(probes.inner.layer, "$label strategy $strategy: starts with none")
          assertPresent(probes.inner, "$label strategy $strategy")
        }
      }
      for ((strategy, blur, expectedAlpha) in
        listOf(
          Triple(null, 0f, OPAQUE),
          Triple(MODULATE_ALPHA, 0f, OPAQUE),
          Triple(MODULATE_ALPHA, 4f, HALF),
          Triple(AUTO, 0f, HALF),
        )) {
        assertStillAndOffsetAlpha(
          visibilityDocument(strategy = strategy, blur = blur, spec = spec),
          expectedAlpha,
          "$label strategy=$strategy blur=$blur",
          layoutAnimations = layoutAnimations,
        )
      }
    }
  }

  /**
   * A scroll container clips its content in a Compose layer (`verticalScroll`/`horizontalScroll`),
   * so its descendants start with no enclosing `ModulateAlpha` layer and keep their subtrees.
   */
  @Test
  fun scrollContainerStartsWithNoModulateAlphaLayer() {
    val probes = Probes()
    var document by mutableStateOf(scrollDocument(strategy = MODULATE_ALPHA))
    probes.scene({ document }) {
      var nanos = 0L
      render(nanos)
      assertNotNull(probes.host.layer, "control: host sibling sees host layer")
      assertNull(probes.inner.layer, "scroll child starts with none")
      assertPresent(probes.inner, "first")
      for (strategy in listOf(AUTO, null, MODULATE_ALPHA)) {
        swap { document = scrollDocument(strategy = strategy) }
        nanos += FRAME
        render(nanos)
        assertNull(probes.inner.layer, "strategy $strategy: scroll child starts with none")
        assertPresent(probes.inner, "strategy $strategy")
      }
    }
    for ((strategy, blur, expectedAlpha) in
      listOf(
        Triple(null, 0f, OPAQUE),
        Triple(MODULATE_ALPHA, 0f, OPAQUE),
        Triple(MODULATE_ALPHA, 4f, HALF),
        Triple(AUTO, 0f, HALF),
      )) {
      assertStillAndOffsetAlpha(
        scrollDocument(strategy = strategy, blur = blur),
        expectedAlpha,
        "scroll strategy=$strategy blur=$blur",
      )
    }
  }

  private fun assertClipClearsLayerAndKeepsSubtree(rounded: Boolean) {
    val probes = Probes()
    var document by mutableStateOf(clippedDocument(strategy = MODULATE_ALPHA, rounded = rounded))
    probes.scene({ document }) {
      var nanos = 0L
      render(nanos)
      assertTrue(assertNotNull(probes.host.layer).childLayersMissAlpha, "control: host layer")
      assertNull(probes.inner.layer, "clipped child starts with no layer")
      assertPresent(probes.inner, "first")
      for (strategy in listOf(AUTO, OFFSCREEN, null, MODULATE_ALPHA)) {
        swap { document = clippedDocument(strategy = strategy, rounded = rounded) }
        nanos += FRAME
        render(nanos)
        val step = "rounded=$rounded strategy=$strategy"
        assertNull(probes.inner.layer, "$step: clipped child starts with no layer")
        assertPresent(probes.inner, step)
      }
      // Toggling the clip off and back on also keeps the child subtree alive.
      swap {
        document = clippedDocument(strategy = MODULATE_ALPHA, rounded = rounded, clip = false)
      }
      nanos += FRAME
      render(nanos)
      assertNotNull(probes.inner.layer, "clip removed: child now sees host layer")
      assertPresent(probes.inner, "clip removed")
      swap { document = clippedDocument(strategy = MODULATE_ALPHA, rounded = rounded, clip = true) }
      nanos += FRAME
      render(nanos)
      assertNull(probes.inner.layer, "clip restored: child starts with no layer")
      assertPresent(probes.inner, "clip restored")
    }
  }

  private fun assertStillAndOffsetAlpha(
    document: RcDocument,
    expectedAlpha: Int,
    label: String,
    layoutAnimations: Boolean = true,
  ) {
    Probes().scene({ document }, layoutAnimations = layoutAnimations) {
      val bmp = bitmap(render(0L))
      val still = bmp.getColor(STILL_X, MID_Y)
      val moved = bmp.getColor(MOVED_X, MID_Y)
      val detail = "$label: still=${hex(still)}, offset=${hex(moved)}"
      assertTrue(Color.getB(still) > 200 && Color.getR(still) < 50, "$detail: still is blue")
      assertTrue(Color.getR(moved) > 200 && Color.getB(moved) < 50, "$detail: offset is red")
      assertEquals(expectedAlpha, Color.getA(still), "$detail: still alpha")
      assertEquals(expectedAlpha, Color.getA(moved), "$detail: offset alpha")
    }
  }

  private fun assertPresent(probe: Probe, step: String) {
    assertEquals(1, probe.remembers, "$step: created once")
    assertEquals(0, probe.forgets, "$step: never disposed")
  }

  private fun swap(block: () -> Unit) {
    Snapshot.withMutableSnapshot(block)
    Snapshot.sendApplyNotifications()
  }

  private fun bitmap(image: Image) =
    Bitmap().apply {
      allocN32Pixels(WIDTH, HEIGHT)
      check(image.readPixels(this))
    }

  private fun hex(color: Int) = color.toUInt().toString(16)

  private class Probe {
    var layer: RcModulateAlphaLayer? = null
    var remembers = 0
    var forgets = 0
  }

  private class Remembered(private val probe: Probe) : RememberObserver {
    override fun onRemembered() {
      probe.remembers++
    }

    override fun onForgotten() {
      probe.forgets++
    }

    override fun onAbandoned() = Unit
  }

  private class Probes {
    val host = Probe()
    val inner = Probe()

    private fun entry(
      name: String,
      probe: Probe,
    ): Pair<String, @Composable (RcCustomComponent, Modifier) -> Unit> =
      name to
        { _, modifier ->
          probe.layer = LocalRcModulateAlphaLayer.current
          remember { Remembered(probe) }
          Box(modifier)
        }

    val registry = RcCustomComponentRegistry(entry(HOST_PROBE, host), entry(INNER_PROBE, inner))

    fun scene(
      document: () -> RcDocument,
      layoutAnimations: Boolean = true,
      block: ImageComposeScene.() -> Unit,
    ) {
      val scene =
        ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
          CompositionLocalProvider(LocalRcLayoutAnimations provides layoutAnimations) {
            RcComposePlayer(document(), customComponents = registry)
          }
        }
      try {
        scene.block()
      } finally {
        scene.close()
      }
    }
  }

  private companion object {
    const val WIDTH = 60
    const val HEIGHT = 20
    const val MID_Y = 10
    const val STILL_X = 10
    const val MOVED_X = 30
    const val FRAME = 16_000_000L
    const val OPAQUE = 0xff
    const val HALF = 0x80
    const val MODULATE_ALPHA = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA
    const val AUTO = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_AUTO
    const val OFFSCREEN = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_OFFSCREEN
    const val HOST_PROBE = "hostProbe"
    const val INNER_PROBE = "innerProbe"

    val end = RcNoArg(RcOpcodes.CONTAINER_END)

    fun literal(value: Float) = RcFloatWord.literal(value)

    fun header() =
      RcHeader(RcVersion(1, 1, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false)

    fun size(width: Int, height: Int) =
      listOf(
        RcWidthModifier(RcDimensionType.EXACT, literal(width.toFloat())),
        RcHeightModifier(RcDimensionType.EXACT, literal(height.toFloat())),
      )

    fun background(red: Float, blue: Float) =
      RcBackgroundModifier(
        flags = 0,
        colorId = 0,
        reserved1 = 0,
        reserved2 = 0,
        red = literal(red),
        green = literal(0f),
        blue = literal(blue),
        alpha = literal(1f),
        shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
      )

    fun layer(strategy: Int, alpha: Float = 0.5f, blur: Float = 0f) =
      RcGraphicsLayerModifier(
        buildList {
          add(RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.ALPHA, literal(alpha)))
          add(
            RcGraphicsLayerAttribute.IntValue(
              RcGraphicsLayerModifier.COMPOSITING_STRATEGY,
              strategy,
            ),
          )
          if (blur > 0f) {
            add(
              RcGraphicsLayerAttribute.FloatValue(
                RcGraphicsLayerModifier.BLUR_RADIUS_X,
                literal(blur),
              ),
            )
            add(
              RcGraphicsLayerAttribute.FloatValue(
                RcGraphicsLayerModifier.BLUR_RADIUS_Y,
                literal(blur),
              ),
            )
          }
        },
      )

    fun animSpec(
      enter: RcLayoutAnimation,
      exit: RcLayoutAnimation,
      durationMs: Float = 300f,
    ) =
      RcAnimationSpec(
        animationId = 1,
        motionDurationMillis = literal(durationMs),
        motionEasingType = 1,
        visibilityDurationMillis = literal(durationMs),
        visibilityEasingType = 1,
        enterAnimation = enter,
        exitAnimation = exit,
      )

    fun MutableList<RcOperation>.stillAndOffsetChildren() {
      // Blue still child at 0..20.
      add(RcBoxLayout(21, 51, 1, 1))
      addAll(size(20, HEIGHT))
      add(background(red = 0f, blue = 1f))
      add(RcLayoutContent(22))
      add(end)
      add(end)
      // Red offset child at 20..40 holding INNER_PROBE.
      add(RcBoxLayout(23, 53, 1, 1))
      add(RcOffsetModifier(literal(20f), literal(0f)))
      addAll(size(20, HEIGHT))
      add(background(red = 1f, blue = 0f))
      add(RcLayoutContent(24))
      add(RcCustomLayout(25, 0, 51, emptyList()))
      addAll(size(20, HEIGHT))
      add(end)
      add(end)
      add(end)
    }

    fun hostWithMiddle(
      strategy: Int?,
      blur: Float = 0f,
      preamble: MutableList<RcOperation>.() -> Unit = {},
      middleModifiers: MutableList<RcOperation>.() -> Unit,
    ): RcDocument =
      RcDocument(
        header(),
        buildList {
          add(RcTextData(50, HOST_PROBE))
          add(RcTextData(51, INNER_PROBE))
          preamble()
          add(RcRootLayout(1))
          add(RcLayoutContent(2))
          add(RcBoxLayout(3, 30, 1, 1))
          if (strategy != null) add(layer(strategy, blur = blur))
          addAll(size(WIDTH, HEIGHT))
          add(RcLayoutContent(4))
          add(RcCustomLayout(10, 0, 50, emptyList()))
          addAll(size(20, HEIGHT))
          add(end)
          add(RcBoxLayout(20, 40, 1, 1))
          middleModifiers()
          addAll(size(40, HEIGHT))
          add(RcLayoutContent(29))
          stillAndOffsetChildren()
          repeat(6) { add(end) }
        },
      )

    fun clippedDocument(
      strategy: Int?,
      blur: Float = 0f,
      rounded: Boolean,
      clip: Boolean = true,
    ): RcDocument =
      hostWithMiddle(strategy, blur) {
        if (clip) {
          add(
            if (rounded) {
              RcRoundedClipRectModifier(literal(2f), literal(2f), literal(2f), literal(2f))
            } else {
              RcClipRectModifier
            },
          )
        }
      }

    fun capturedDocument(strategy: Int?, blur: Float = 0f): RcDocument =
      hostWithMiddle(
        strategy,
        blur,
        preamble = {
          add(
            RcBitmapData(
              imageId = 90,
              width = 40,
              height = HEIGHT,
              type = RcBitmapData.TYPE_PNG_8888,
              encoding = RcBitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER,
              data = ByteArray(0),
            ),
          )
          add(RcFloatFunctionDefine(91, emptyList()))
          add(RcDrawToBitmap(90, RcDrawToBitmap.MODE_COMPONENT_ID, 20))
          add(RcNoArg(RcOpcodes.DRAW_CONTENT))
          add(RcDrawToBitmap(0, 0, 0))
          add(end)
        },
      ) {}

    fun scrollDocument(strategy: Int?, blur: Float = 0f): RcDocument =
      hostWithMiddle(strategy, blur) {
        add(RcScrollModifier(RcScrollModifier.VERTICAL, literal(0f), literal(0f), literal(0f)))
        add(end)
      }

    fun visibilityDocument(
      strategy: Int?,
      blur: Float = 0f,
      spec: RcAnimationSpec,
    ): RcDocument =
      hostWithMiddle(
        strategy,
        blur,
        preamble = { add(RcIntegerConstant(60, 1)) },
      ) {
        add(RcVisibilityModifier(60))
        add(spec)
      }
  }
}

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
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
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
 * An embedded document is drawn inside `clipToBounds` around its player, a child layer of the
 * host's. On Skiko a child layer misses a `ModulateAlpha` parent's alpha unless that parent has a
 * blur (CMP-11189): without one the host's alpha never reaches the embedded document, with one
 * Skiko composites the host layer through a paint that carries the alpha, and the whole embedded
 * document is faded once. Either way no offset inside has to give up its own layer for the host's
 * alpha, so the embedded document starts with no enclosing `ModulateAlpha` layer.
 *
 * Pixels cannot tell an offset placed with a layer from one placed without in most of these cases,
 * so the layer tests pin the layer an offset is drawn into, as [RcModulateAlphaLayerTest] does: a
 * custom component inside an offset box records the [LocalRcModulateAlphaLayer] it is composed
 * with, which is the one that box's offset resolves (`drawnIntoOffset` of a component without a
 * layer of its own). Each probe also counts how often its remembered state is created and
 * forgotten, so a subtree that is disposed (and maybe never composed again) fails, not only one
 * that is created twice. A probe that skips recomposition keeps the layer it recorded; that is
 * still the current one, because a change of the static [LocalRcModulateAlphaLayer] recomposes
 * everything that reads it.
 */
class RcEmbeddedModulateAlphaLayerTest {

  @Test
  fun anEmbeddedDocumentStartsWithNoModulateAlphaLayerUnderAHostOne() {
    val probes = Probes()
    probes.scene({ host(strategy = MODULATE_ALPHA, url = URL_PLAIN) }) {
      render(0L)
      // The control: the host's own child is drawn into the host's ModulateAlpha layer, and that
      // layer makes an offset give up its layer (half alpha, no blur).
      val host = assertNotNull(probes.host.layer, "the host child sees the host's layer")
      assertTrue(host.childLayersMissAlpha, "the host layer is bound at half alpha")
      assertPresent(probes.embedded, "first composition")
      assertNull(probes.embedded.layer, "the embedded document starts with no layer")
    }
  }

  /**
   * The host layer's strategy goes through every value and back to `ModulateAlpha`, under an
   * embedded document with a half-alpha `ModulateAlpha` layer of its own. That layer never encloses
   * the host's, and both subtrees are kept: providing the embedded document's starting layer from
   * different branches would dispose its subtree and drop its remembered state.
   */
  @Test
  fun hostStrategySwapsKeepTheEmbeddedSubtreeAndItsOwnLayer() {
    val probes = Probes()
    var document by mutableStateOf(host(strategy = MODULATE_ALPHA, url = URL_OWN))
    probes.scene({ document }) {
      var nanos = 0L
      render(nanos)
      assertTrue(assertNotNull(probes.host.layer).childLayersMissAlpha, "first: host layer")
      assertOwnLayerOnly(probes.embeddedOwn, "first")
      for (strategy in listOf(AUTO, OFFSCREEN, null, MODULATE_ALPHA)) {
        swap { document = host(strategy = strategy, url = URL_OWN) }
        nanos += FRAME
        render(nanos)
        val step = "strategy $strategy"
        if (strategy == MODULATE_ALPHA) assertNotNull(probes.host.layer, "$step: host layer")
        else assertNull(probes.host.layer, "$step: no host layer")
        assertPresent(probes.host, step)
        assertOwnLayerOnly(probes.embeddedOwn, step)
      }
    }
  }

  /** The host's alpha flips the host's own answer; the embedded document starts with none. */
  @Test
  fun hostAlphaSwapsKeepTheEmbeddedSubtreeWithNoLayer() {
    val probes = Probes()
    var document by mutableStateOf(host(strategy = MODULATE_ALPHA, url = URL_PLAIN))
    probes.scene({ document }) {
      var nanos = 0L
      render(nanos)
      for (alpha in listOf(1f, 0.5f, 1f)) {
        swap { document = host(strategy = MODULATE_ALPHA, alpha = alpha, url = URL_PLAIN) }
        nanos += FRAME
        render(nanos)
        val step = "alpha $alpha"
        val host = assertNotNull(probes.host.layer, "$step: host layer")
        assertEquals(alpha < 1f, host.childLayersMissAlpha, "$step: host child layers miss it")
        assertNull(probes.embedded.layer, "$step: the embedded document starts with none")
        assertPresent(probes.embedded, step)
      }
    }
  }

  /**
   * A blur makes Skiko fade the host's child layers, so the host's answer flips; the embedded
   * document still starts with none, and its own layer encloses nothing.
   */
  @Test
  fun hostBlurSwapsKeepTheEmbeddedSubtreeAndItsOwnLayer() {
    val probes = Probes()
    var document by mutableStateOf(host(strategy = MODULATE_ALPHA, url = URL_OWN))
    probes.scene({ document }) {
      var nanos = 0L
      render(nanos)
      for (blur in listOf(4f, 0f, 4f)) {
        swap { document = host(strategy = MODULATE_ALPHA, blur = blur, url = URL_OWN) }
        nanos += FRAME
        render(nanos)
        val step = "blur $blur"
        val host = assertNotNull(probes.host.layer, "$step: host layer")
        assertEquals(blur == 0f, host.childLayersMissAlpha, "$step: host child layers miss it")
        assertOwnLayerOnly(probes.embeddedOwn, step)
      }
    }
  }

  /** An embedded document inside an embedded one: both levels start with none and are kept. */
  @Test
  fun nestedEmbeddedDocumentsStartWithNoLayerAndKeepTheirSubtrees() {
    val probes = Probes()
    var document by mutableStateOf(host(strategy = MODULATE_ALPHA, url = URL_NESTED))
    probes.scene({ document }) {
      var nanos = 0L
      render(nanos)
      assertNull(probes.embedded.layer, "first: the outer embedded document starts with none")
      assertPresent(probes.embedded, "first: outer")
      assertOwnLayerOnly(probes.embeddedOwn, "first: inner")
      for (strategy in listOf(AUTO, MODULATE_ALPHA, null, MODULATE_ALPHA)) {
        swap { document = host(strategy = strategy, url = URL_NESTED) }
        nanos += FRAME
        render(nanos)
        val step = "strategy $strategy"
        assertNull(probes.embedded.layer, "$step: the outer embedded document starts with none")
        assertPresent(probes.embedded, "$step: outer")
        assertOwnLayerOnly(probes.embeddedOwn, "$step: inner")
      }
    }
  }

  /** `maxDepth` 1 stops the inner embedded document; 2 lets it in, with no host layer. */
  @Test
  fun theDepthLimitStopsTheInnerEmbeddedDocumentAndTheOuterOneStartsWithNoLayer() {
    for (maxDepth in 1..2) {
      val probes = Probes()
      probes.scene({ host(strategy = MODULATE_ALPHA, url = URL_NESTED) }, maxDepth) {
        render(0L)
        assertPresent(probes.embedded, "maxDepth $maxDepth: outer")
        assertNull(probes.embedded.layer, "maxDepth $maxDepth: the outer one starts with none")
        if (maxDepth >= 2) assertOwnLayerOnly(probes.embeddedOwn, "maxDepth $maxDepth: inner")
        else assertEquals(0, probes.embeddedOwn.remembers, "maxDepth $maxDepth: no inner one")
      }
    }
  }

  /**
   * The host swaps its embedded document for another one of the same shape and back. Each is a new
   * document, so its subtree may be new; what must hold is that exactly one is present and its own
   * layer encloses nothing.
   */
  @Test
  fun aSwappedEmbeddedDocumentStartsWithNoLayer() {
    val probes = Probes()
    var document by mutableStateOf(host(strategy = MODULATE_ALPHA, url = URL_OWN))
    probes.scene({ document }) {
      var nanos = 0L
      render(nanos)
      for (url in listOf(URL_OWN_WIDER, URL_OWN)) {
        swap { document = host(strategy = MODULATE_ALPHA, url = url) }
        nanos += FRAME
        render(nanos)
        val own = assertNotNull(probes.embeddedOwn.layer, "$url: its own layer")
        assertNull(own.enclosing, "$url: its own layer encloses no host layer")
        assertTrue(own.childLayersMissAlpha, "$url: its own layer is bound at half alpha")
        assertEquals(1, probes.embeddedOwn.present, "$url: one embedded subtree is present")
      }
    }
  }

  /**
   * A regression guard, not a test of this change (it passes without it too): an embedded
   * document's own half-alpha `ModulateAlpha` layer fades its offset child as much as its still
   * child under every host layer. Without a blur on a host `ModulateAlpha` layer the host's alpha
   * misses the embedded document (half); with one, or with any other host strategy, it reaches it
   * once (a quarter).
   */
  @Test
  fun anEmbeddedDocumentsOwnModulateAlphaLayerFadesItsOffsetChildLikeItsStillOne() {
    for ((strategy, blur, alpha) in
      listOf(
        Triple(null, 0f, HALF),
        Triple(MODULATE_ALPHA, 0f, HALF),
        Triple(MODULATE_ALPHA, 4f, QUARTER),
        Triple(AUTO, 0f, QUARTER),
        Triple(AUTO, 4f, QUARTER),
        Triple(OFFSCREEN, 0f, QUARTER),
        Triple(OFFSCREEN, 4f, QUARTER),
      )) {
      assertStillAndOffsetChildAlpha(host(strategy, blur = blur, url = URL_OWN), alpha)
    }
  }

  /**
   * A regression guard, not a test of this change (it passes without it too): in an embedded
   * document without a layer of its own, the offset child is drawn like the still child under every
   * host layer: opaque under a host `ModulateAlpha` layer without a blur, faded once otherwise.
   */
  @Test
  fun anEmbeddedDocumentsOffsetChildIsDrawnLikeItsStillOneUnderAHostLayer() {
    for ((strategy, blur, alpha) in
      listOf(
        Triple(null, 0f, OPAQUE),
        Triple(MODULATE_ALPHA, 0f, OPAQUE),
        Triple(MODULATE_ALPHA, 4f, HALF),
        Triple(AUTO, 0f, HALF),
        Triple(OFFSCREEN, 0f, HALF),
      )) {
      assertStillAndOffsetChildAlpha(host(strategy, blur = blur, url = URL_PLAIN_WITH_STILL), alpha)
    }
  }

  private fun assertStillAndOffsetChildAlpha(document: RcDocument, alpha: Int) {
    Probes().scene({ document }) {
      val bitmap = bitmap(render(0L))
      val still = bitmap.getColor(STILL_X, MID_Y)
      val moved = bitmap.getColor(MOVED_X, MID_Y)
      val case = "still ${hex(still)}, offset ${hex(moved)}"
      assertTrue(Color.getB(still) > 200 && Color.getR(still) < 50, "$case: the still one is blue")
      assertTrue(Color.getR(moved) > 200 && Color.getB(moved) < 50, "$case: the offset one is red")
      assertEquals(alpha, Color.getA(still), "$case: still child alpha")
      assertEquals(alpha, Color.getA(moved), "$case: offset child alpha")
    }
  }

  /** [probe]'s own `ModulateAlpha` layer, half alpha, with no host layer above it; kept. */
  private fun assertOwnLayerOnly(probe: Probe, step: String) {
    val own = assertNotNull(probe.layer, "$step: the embedded document's own layer")
    assertNull(own.enclosing, "$step: its own layer encloses no host layer")
    assertTrue(own.childLayersMissAlpha, "$step: its own layer is bound at half alpha")
    assertPresent(probe, step)
  }

  /** [probe]'s subtree was created once and never forgotten. */
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

  /** What a custom component saw, and how often its remembered state was created and forgotten. */
  private class Probe {
    var layer: RcModulateAlphaLayer? = null
    var remembers = 0
    var forgets = 0
    val present
      get() = remembers - forgets
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
    /** The offset box of an embedded document with no layer of its own. */
    val embedded = Probe()
    /** The offset box of an embedded document with a `ModulateAlpha` layer of its own. */
    val embeddedOwn = Probe()

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

    val registry =
      RcCustomComponentRegistry(
        entry(HOST_PROBE, host),
        entry(EMBEDDED_PROBE, embedded),
        entry(OWN_PROBE, embeddedOwn),
      )

    fun scene(document: () -> RcDocument, maxDepth: Int = 3, block: ImageComposeScene.() -> Unit) {
      val embeddedDocuments = RcEmbeddedDocuments(maxDepth = maxDepth, resolver = { DOCUMENTS[it] })
      val scene =
        ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
          CompositionLocalProvider(LocalRcEmbeddedDocuments provides embeddedDocuments) {
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
    const val QUARTER = 0x40
    const val MODULATE_ALPHA = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA
    const val AUTO = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_AUTO
    const val OFFSCREEN = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_OFFSCREEN
    const val URL_PLAIN = "doc://plain"
    const val URL_PLAIN_WITH_STILL = "doc://plain-with-still"
    const val URL_OWN = "doc://own"
    const val URL_OWN_WIDER = "doc://own-wider"
    const val URL_NESTED = "doc://nested"
    const val HOST_PROBE = "hostProbe"
    const val EMBEDDED_PROBE = "embeddedProbe"
    const val OWN_PROBE = "ownProbe"

    val end = RcNoArg(RcOpcodes.CONTAINER_END)

    fun literal(value: Float) = RcFloatWord.literal(value)

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

    fun header(width: Int) =
      RcHeader(RcVersion(1, 1, 0), legacyWidth = width, legacyHeight = HEIGHT, modern = false)

    /**
     * A 60x20 box (with a graphics layer of [strategy] unless null) stacking a 20x20 custom
     * component ([HOST_PROBE]) and a 40x20 embedded document ([url]).
     */
    fun host(strategy: Int?, alpha: Float = 0.5f, blur: Float = 0f, url: String): RcDocument =
      RcDocument(
        header(WIDTH),
        buildList<RcOperation> {
          add(RcTextData(50, HOST_PROBE))
          add(RcTextData(51, url))
          add(RcRootLayout(1))
          add(RcLayoutContent(2))
          add(RcBoxLayout(3, 30, 1, 1))
          if (strategy != null) add(layer(strategy, alpha, blur))
          addAll(size(WIDTH, HEIGHT))
          add(RcLayoutContent(4))
          add(RcCustomLayout(10, 0, 50, emptyList()))
          addAll(size(20, HEIGHT))
          add(end)
          add(RcDocLayout(20, -1, 51))
          addAll(size(40, HEIGHT))
          add(end)
          repeat(4) { add(end) }
        },
      )

    /** A 20x20 box moved by [offsetX] unless null, with a background, holding [probeTextId]. */
    fun MutableList<RcOperation>.child(
      id: Int,
      offsetX: Float?,
      red: Float,
      blue: Float,
      probeTextId: Int?,
    ) {
      add(RcBoxLayout(id, 30 + id, 1, 1))
      if (offsetX != null) add(RcOffsetModifier(literal(offsetX), literal(0f)))
      addAll(size(20, HEIGHT))
      add(background(red, blue))
      add(RcLayoutContent(id + 1))
      if (probeTextId != null) {
        add(RcCustomLayout(id + 2, 0, probeTextId, emptyList()))
        addAll(size(20, HEIGHT))
        add(end)
      }
      add(end)
      add(end)
    }

    fun encode(width: Int, operations: List<RcOperation>) =
      RcDocumentCodec.encode(RcDocument(header(width), operations))

    /**
     * A 40x20 document with no layer: a blue still child at 0..20 when [withStill], and a red child
     * moved by `offset` to 20..40 holding [EMBEDDED_PROBE].
     */
    fun plainEmbedded(withStill: Boolean): ByteArray =
      encode(
        40,
        buildList {
          add(RcTextData(50, EMBEDDED_PROBE))
          add(RcRootLayout(1))
          add(RcLayoutContent(2))
          add(RcBoxLayout(3, 30, 1, 1))
          addAll(size(40, HEIGHT))
          add(RcLayoutContent(4))
          if (withStill) child(10, null, 0f, 1f, null)
          child(20, 20f, 1f, 0f, 50)
          repeat(4) { add(end) }
        },
      )

    /**
     * A [width]x20 document with a half-alpha `ModulateAlpha` layer: a blue still child at 0..20,
     * and a red child moved by `offset` to 20..40 holding [OWN_PROBE].
     */
    fun ownLayerEmbedded(width: Int): ByteArray =
      encode(
        width,
        buildList {
          add(RcTextData(50, OWN_PROBE))
          add(RcRootLayout(1))
          add(RcLayoutContent(2))
          add(RcBoxLayout(3, 30, 1, 1))
          add(layer(MODULATE_ALPHA))
          addAll(size(40, HEIGHT))
          add(RcLayoutContent(4))
          child(10, null, 0f, 1f, null)
          child(20, 20f, 1f, 0f, 50)
          repeat(4) { add(end) }
        },
      )

    /** A 40x20 document embedding [URL_OWN], and an offset child holding [EMBEDDED_PROBE]. */
    fun nestedEmbedded(): ByteArray =
      encode(
        40,
        buildList {
          add(RcTextData(50, EMBEDDED_PROBE))
          add(RcTextData(51, URL_OWN))
          add(RcRootLayout(1))
          add(RcLayoutContent(2))
          add(RcBoxLayout(3, 30, 1, 1))
          addAll(size(40, HEIGHT))
          add(RcLayoutContent(4))
          add(RcDocLayout(40, -1, 51))
          addAll(size(40, HEIGHT))
          add(end)
          child(20, 20f, 1f, 0f, 50)
          repeat(4) { add(end) }
        },
      )

    val DOCUMENTS: Map<String, ByteArray> =
      mapOf(
        URL_PLAIN to plainEmbedded(withStill = false),
        URL_PLAIN_WITH_STILL to plainEmbedded(withStill = true),
        URL_OWN to ownLayerEmbedded(40),
        URL_OWN_WIDER to ownLayerEmbedded(41),
        URL_NESTED to nestedEmbedded(),
      )
  }
}

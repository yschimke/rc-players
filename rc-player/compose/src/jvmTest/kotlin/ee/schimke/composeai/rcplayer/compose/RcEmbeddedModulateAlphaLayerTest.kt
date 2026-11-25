package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
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

/**
 * An embedded document is drawn inside `clipToBounds` around its player, a child layer of the
 * host's. On Skiko a child layer misses a `ModulateAlpha` parent's alpha, so nothing inside the
 * embedded document is faded by a host `ModulateAlpha` layer, and an offset in it gains nothing by
 * giving up its own layer there: it would only record the embedded document's layer again on each
 * move. So the embedded document starts with no enclosing `ModulateAlpha` layer.
 *
 * Pixels cannot tell an offset placed with a layer from one placed without inside the embedded
 * document (both miss the host alpha), so this pins the layer an offset there is drawn into, as
 * [RcModulateAlphaLayerTest] does: a custom component inside the embedded document's offset box
 * records the [LocalRcModulateAlphaLayer] it is composed with, which is the one that box's offset
 * resolves (`drawnIntoOffset` of a component without a layer of its own).
 */
class RcEmbeddedModulateAlphaLayerTest {

  @Test
  fun anOffsetInAnEmbeddedDocumentKeepsItsLayerUnderAHostModulateAlphaLayer() {
    val probes = Probes()
    probes.scene(hostDocument(RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA)) {
      render(0L)
      // The control: the host's own child is drawn into the host's ModulateAlpha layer, and that
      // layer now makes an offset give up its layer (half alpha, no blur).
      val host = assertNotNull(probes.host, "the host child sees the host's ModulateAlpha layer")
      assertTrue(host.childLayersMissAlpha, "the host layer is bound at half alpha")
      assertTrue(probes.embeddedComposed, "the embedded document is composed")
      assertNull(probes.embedded, "the embedded document starts with no ModulateAlpha layer")
    }
  }

  /**
   * The host layer's strategy flips `ModulateAlpha` -> `Auto` -> `ModulateAlpha` with each next
   * document. The embedded document's subtree has to survive both swaps: providing its starting
   * layer from different branches would dispose it and drop its remembered state.
   */
  @Test
  fun anEmbeddedDocumentKeepsItsStateWhenTheHostLayerStrategyChanges() {
    val probes = Probes()
    val modulate = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA
    val auto = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_AUTO
    var document by mutableStateOf(hostDocument(modulate))
    probes.scene({ document }) {
      var nanos = 0L
      render(nanos)
      assertNotNull(probes.host, "ModulateAlpha: the host child sees a layer")
      assertEquals(1, probes.embeddedRemembers, "first composition")
      for ((step, strategy) in listOf(auto, modulate).withIndex()) {
        Snapshot.withMutableSnapshot { document = hostDocument(strategy) }
        Snapshot.sendApplyNotifications()
        nanos += FRAME
        render(nanos)
        if (strategy == auto) assertNull(probes.host, "step $step, Auto: the host child sees none")
        else assertNotNull(probes.host, "step $step, ModulateAlpha: the host child sees a layer")
        assertNull(probes.embedded, "step $step: the embedded document starts with none")
        assertEquals(1, probes.embeddedRemembers, "step $step: the embedded subtree is kept")
      }
    }
  }

  /** What the custom components in the host and in the embedded document saw. */
  private class Probes {
    var host: RcModulateAlphaLayer? = null
    var embedded: RcModulateAlphaLayer? = null
    var embeddedComposed = false
    /** How many times the embedded component's remembered state has been created. */
    var embeddedRemembers = 0

    val registry =
      RcCustomComponentRegistry(
        HOST_PROBE to
          { _, modifier ->
            host = LocalRcModulateAlphaLayer.current
            Box(modifier)
          },
        EMBEDDED_PROBE to
          { _, modifier ->
            embedded = LocalRcModulateAlphaLayer.current
            embeddedComposed = true
            remember { embeddedRemembers++ }
            Box(modifier)
          },
      )

    fun scene(document: RcDocument, block: ImageComposeScene.() -> Unit) =
      scene({ document }, block)

    fun scene(document: () -> RcDocument, block: ImageComposeScene.() -> Unit) {
      val embeddedDocuments =
        RcEmbeddedDocuments(resolver = { url -> EMBEDDED.takeIf { url == URL } })
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
    const val FRAME = 16_000_000L
    const val URL = "doc://embedded"
    const val HOST_PROBE = "hostProbe"
    const val EMBEDDED_PROBE = "embeddedProbe"

    val end = RcNoArg(RcOpcodes.CONTAINER_END)

    fun literal(value: Float) = RcFloatWord.literal(value)

    fun size(width: Int, height: Int) =
      listOf(
        RcWidthModifier(RcDimensionType.EXACT, literal(width.toFloat())),
        RcHeightModifier(RcDimensionType.EXACT, literal(height.toFloat())),
      )

    /**
     * A 60x20 box with a half-alpha graphics layer of [strategy], holding a 20x20 custom component
     * ([HOST_PROBE]) and a 40x20 embedded document ([URL]).
     */
    fun hostDocument(strategy: Int): RcDocument =
      RcDocument(
        RcHeader(RcVersion(1, 1, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
        buildList<RcOperation> {
          add(RcTextData(50, HOST_PROBE))
          add(RcTextData(51, URL))
          add(RcRootLayout(1))
          add(RcLayoutContent(2))
          add(RcBoxLayout(3, 30, 1, 1))
          add(
            RcGraphicsLayerModifier(
              listOf(
                RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.ALPHA, literal(0.5f)),
                RcGraphicsLayerAttribute.IntValue(
                  RcGraphicsLayerModifier.COMPOSITING_STRATEGY,
                  strategy,
                ),
              ),
            ),
          )
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

    /** A 40x20 document: a box moved by `offset`, holding a custom component ([EMBEDDED_PROBE]). */
    val EMBEDDED: ByteArray =
      RcDocumentCodec.encode(
        RcDocument(
          RcHeader(RcVersion(1, 1, 0), legacyWidth = 40, legacyHeight = HEIGHT, modern = false),
          buildList<RcOperation> {
            add(RcTextData(50, EMBEDDED_PROBE))
            add(RcRootLayout(1))
            add(RcLayoutContent(2))
            add(RcBoxLayout(3, 30, 1, 1))
            add(RcOffsetModifier(literal(10f), literal(0f)))
            addAll(size(20, HEIGHT))
            add(RcLayoutContent(4))
            add(RcCustomLayout(10, 0, 50, emptyList()))
            addAll(size(20, HEIGHT))
            add(end)
            repeat(4) { add(end) }
          },
        ),
      )
  }
}

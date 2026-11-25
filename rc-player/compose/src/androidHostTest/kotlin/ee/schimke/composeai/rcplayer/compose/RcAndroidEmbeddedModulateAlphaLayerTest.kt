package ee.schimke.composeai.rcplayer.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Android's side of `RcEmbeddedModulateAlphaLayerTest`. AndroidX's child layers get a
 * `ModulateAlpha` parent's alpha, so the player never makes a `ModulateAlpha` layer holder here
 * (`rcChildLayersMissModulatedAlpha` is false) and every offset keeps its layer. This is a parity
 * guard, not a test of the embedded document's starting layer, which only matters on Skiko: it
 * passes with or without that. It pins that no holder ever appears, at any embedding level, and that
 * every subtree is kept through host strategy, alpha and blur changes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1000dp-h1000dp")
class RcAndroidEmbeddedModulateAlphaLayerTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun hostLayerChangesProvideNoLayerAndKeepEverySubtree() {
    val host = Probe()
    val embedded = Probe()
    val embeddedOwn = Probe()
    val registry =
      RcCustomComponentRegistry(
        entry(HOST_PROBE, host),
        entry(EMBEDDED_PROBE, embedded),
        entry(OWN_PROBE, embeddedOwn),
      )
    val documents = mapOf(URL_OWN to ownLayerEmbedded(), URL_NESTED to nestedEmbedded())
    val embeddedDocuments = RcEmbeddedDocuments(maxDepth = 3, resolver = { documents[it] })
    var document by mutableStateOf(host(strategy = MODULATE_ALPHA))
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      CompositionLocalProvider(
        LocalRcEmbeddedDocuments provides embeddedDocuments,
        LocalDensity provides Density(1f),
      ) {
        RcComposePlayer(document, customComponents = registry)
      }
    }
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.waitForIdle()
    val steps =
      listOf<Triple<Int?, Float, Float>>(
        Triple(MODULATE_ALPHA, 0.5f, 0f),
        Triple(AUTO, 0.5f, 0f),
        Triple(OFFSCREEN, 0.5f, 0f),
        Triple(null, 0.5f, 0f),
        Triple(MODULATE_ALPHA, 1f, 0f),
        Triple(MODULATE_ALPHA, 0.5f, 4f),
        Triple(MODULATE_ALPHA, 0.5f, 0f),
      )
    for ((index, step) in steps.withIndex()) {
      val (strategy, alpha, blur) = step
      if (index > 0) {
        composeRule.runOnIdle { document = host(strategy, alpha, blur) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
      }
      val name = "strategy $strategy, alpha $alpha, blur $blur"
      for ((probeName, probe) in
        listOf("host" to host, "embedded" to embedded, "embedded own" to embeddedOwn)) {
        assertNull("$name: $probeName sees no layer holder", probe.layer)
        assertEquals("$name: $probeName created once", 1, probe.remembers)
        assertEquals("$name: $probeName never disposed", 0, probe.forgets)
      }
    }
  }

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

  private companion object {
    const val WIDTH = 60
    const val HEIGHT = 20
    const val MODULATE_ALPHA = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA
    const val AUTO = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_AUTO
    const val OFFSCREEN = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_OFFSCREEN
    const val URL_OWN = "doc://own"
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

    fun layer(strategy: Int, alpha: Float = 0.5f, blur: Float = 0f) =
      RcGraphicsLayerModifier(
        buildList {
          add(RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.ALPHA, literal(alpha)))
          add(
            RcGraphicsLayerAttribute.IntValue(RcGraphicsLayerModifier.COMPOSITING_STRATEGY, strategy)
          )
          if (blur > 0f) {
            add(
              RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.BLUR_RADIUS_X, literal(blur))
            )
            add(
              RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.BLUR_RADIUS_Y, literal(blur))
            )
          }
        }
      )

    fun header(width: Int) =
      RcHeader(RcVersion(1, 1, 0), legacyWidth = width, legacyHeight = HEIGHT, modern = false)

    /**
     * A 60x20 box (with a graphics layer of [strategy] unless null) stacking a 20x20 custom
     * component ([HOST_PROBE]) and a 40x20 embedded document ([URL_NESTED]).
     */
    fun host(strategy: Int?, alpha: Float = 0.5f, blur: Float = 0f): RcDocument =
      RcDocument(
        header(WIDTH),
        buildList<RcOperation> {
          add(RcTextData(50, HOST_PROBE))
          add(RcTextData(51, URL_NESTED))
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

    /** A 20x20 box moved by `offset`, holding the custom component named by [probeTextId]. */
    fun MutableList<RcOperation>.offsetChild(id: Int, probeTextId: Int) {
      add(RcBoxLayout(id, 30 + id, 1, 1))
      add(RcOffsetModifier(literal(20f), literal(0f)))
      addAll(size(20, HEIGHT))
      add(RcLayoutContent(id + 1))
      add(RcCustomLayout(id + 2, 0, probeTextId, emptyList()))
      addAll(size(20, HEIGHT))
      add(end)
      add(end)
      add(end)
    }

    fun encode(operations: List<RcOperation>) =
      RcDocumentCodec.encode(RcDocument(header(40), operations))

    /** A 40x20 document with a half-alpha `ModulateAlpha` layer and an offset [OWN_PROBE]. */
    fun ownLayerEmbedded(): ByteArray =
      encode(
        buildList {
          add(RcTextData(50, OWN_PROBE))
          add(RcRootLayout(1))
          add(RcLayoutContent(2))
          add(RcBoxLayout(3, 30, 1, 1))
          add(layer(MODULATE_ALPHA))
          addAll(size(40, HEIGHT))
          add(RcLayoutContent(4))
          offsetChild(20, 50)
          repeat(4) { add(end) }
        }
      )

    /** A 40x20 document embedding [URL_OWN], and an offset [EMBEDDED_PROBE]. */
    fun nestedEmbedded(): ByteArray =
      encode(
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
          offsetChild(20, 50)
          repeat(4) { add(end) }
        }
      )
  }
}

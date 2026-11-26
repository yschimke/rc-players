package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

/**
 * The documents and probes the embedded `ModulateAlpha` layer tests share: the Skiko one
 * (`RcEmbeddedModulateAlphaLayerTest`, jvmTest) and its Android parity guard
 * (`RcAndroidEmbeddedModulateAlphaLayerTest`, androidHostTest).
 */
internal object RcEmbeddedLayerFixture {
  /** The host document: a [PROBE_WIDTH] custom component beside an [EMBEDDED_WIDTH] document. */
  const val WIDTH = 60
  const val HEIGHT = 20
  /** Each embedded document's width, and the slot the host gives it. */
  const val EMBEDDED_WIDTH = 40
  /** The host's custom component, and each child box an embedded document stacks. */
  const val PROBE_WIDTH = WIDTH - EMBEDDED_WIDTH
  const val MODULATE_ALPHA = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_MODULATE_ALPHA
  const val AUTO = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_AUTO
  const val OFFSCREEN = RcGraphicsLayerModifier.COMPOSITING_STRATEGY_OFFSCREEN
  const val URL_OWN = "doc://own"
  const val URL_NESTED = "doc://nested"
  const val HOST_PROBE = "hostProbe"
  const val EMBEDDED_PROBE = "embeddedProbe"
  const val OWN_PROBE = "ownProbe"
  /** The text holding a document's probe name, and the one holding the URL it embeds. */
  const val PROBE_TEXT_ID = 50
  const val URL_TEXT_ID = 51
  /** The alpha the documents' graphics layers default to. */
  const val HALF_LAYER_ALPHA = 0.5f

  /** Closes the innermost open container. */
  val containerEnd = RcNoArg(RcOpcodes.CONTAINER_END)

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

  fun layer(strategy: Int, alpha: Float = HALF_LAYER_ALPHA, blur: Float = 0f) =
    RcGraphicsLayerModifier(
      buildList {
        add(RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.ALPHA, literal(alpha)))
        add(
          RcGraphicsLayerAttribute.IntValue(RcGraphicsLayerModifier.COMPOSITING_STRATEGY, strategy),
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
   * The operations of a document whose root is one [width]x[HEIGHT] box (with [layer] unless null)
   * holding [content], with every container closed: the box's content and the box, then the root's
   * content and the root.
   */
  fun MutableList<RcOperation>.rootBox(
    width: Int,
    layer: RcGraphicsLayerModifier? = null,
    content: MutableList<RcOperation>.() -> Unit,
  ) {
    add(RcRootLayout(1))
    add(RcLayoutContent(2))
    add(RcBoxLayout(3, 30, 1, 1))
    if (layer != null) add(layer)
    addAll(size(width, HEIGHT))
    add(RcLayoutContent(4))
    content()
    repeat(4) { add(containerEnd) }
  }

  /**
   * A [WIDTH]x[HEIGHT] box (with a graphics layer of [strategy] unless null) stacking a
   * [PROBE_WIDTH] custom component ([HOST_PROBE]) and an [EMBEDDED_WIDTH] embedded document
   * ([url]).
   */
  fun host(
    strategy: Int?,
    alpha: Float = HALF_LAYER_ALPHA,
    blur: Float = 0f,
    url: String = URL_NESTED,
  ): RcDocument =
    RcDocument(
      header(WIDTH),
      buildList<RcOperation> {
        add(RcTextData(PROBE_TEXT_ID, HOST_PROBE))
        add(RcTextData(URL_TEXT_ID, url))
        rootBox(WIDTH, strategy?.let { layer(it, alpha, blur) }) {
          add(RcCustomLayout(10, 0, PROBE_TEXT_ID, emptyList()))
          addAll(size(PROBE_WIDTH, HEIGHT))
          add(containerEnd)
          add(RcDocLayout(20, -1, URL_TEXT_ID))
          addAll(size(EMBEDDED_WIDTH, HEIGHT))
          add(containerEnd)
        }
      },
    )

  /**
   * A [PROBE_WIDTH] box moved by [offsetX] unless null, with [background] unless null, holding the
   * custom component named by [probeTextId] unless null.
   */
  fun MutableList<RcOperation>.child(
    id: Int,
    offsetX: Float?,
    probeTextId: Int?,
    background: RcBackgroundModifier? = null,
  ) {
    add(RcBoxLayout(id, 30 + id, 1, 1))
    if (offsetX != null) add(RcOffsetModifier(literal(offsetX), literal(0f)))
    addAll(size(PROBE_WIDTH, HEIGHT))
    if (background != null) add(background)
    add(RcLayoutContent(id + 1))
    if (probeTextId != null) {
      add(RcCustomLayout(id + 2, 0, probeTextId, emptyList()))
      addAll(size(PROBE_WIDTH, HEIGHT))
      add(containerEnd)
    }
    add(containerEnd)
    add(containerEnd)
  }

  /**
   * The child an embedded document moves by `offset` from 0..[PROBE_WIDTH] to [PROBE_WIDTH]..
   * [EMBEDDED_WIDTH], holding the probe named by [PROBE_TEXT_ID]; red when [painted].
   */
  fun MutableList<RcOperation>.offsetChild(painted: Boolean) =
    child(
      id = 20,
      offsetX = PROBE_WIDTH.toFloat(),
      probeTextId = PROBE_TEXT_ID,
      background = if (painted) background(red = 1f, blue = 0f) else null,
    )

  /** The blue child an embedded document leaves at 0..[PROBE_WIDTH]. */
  fun MutableList<RcOperation>.stillChild() =
    child(id = 10, offsetX = null, probeTextId = null, background = background(red = 0f, blue = 1f))

  /**
   * A [width]x[HEIGHT] document with a half-alpha `ModulateAlpha` layer and an offset child holding
   * [OWN_PROBE]; when [painted], the offset child is red and a blue still child sits beside it.
   */
  fun ownLayerEmbedded(painted: Boolean, width: Int = EMBEDDED_WIDTH): ByteArray =
    encode(
      width,
      buildList {
        add(RcTextData(PROBE_TEXT_ID, OWN_PROBE))
        rootBox(EMBEDDED_WIDTH, layer(MODULATE_ALPHA)) {
          if (painted) stillChild()
          offsetChild(painted)
        }
      },
    )

  /**
   * An [EMBEDDED_WIDTH]x[HEIGHT] document embedding [URL_OWN], and an offset child holding
   * [EMBEDDED_PROBE], red when [painted].
   */
  fun nestedEmbedded(painted: Boolean): ByteArray =
    encode(
      EMBEDDED_WIDTH,
      buildList {
        add(RcTextData(PROBE_TEXT_ID, EMBEDDED_PROBE))
        add(RcTextData(URL_TEXT_ID, URL_OWN))
        rootBox(EMBEDDED_WIDTH) {
          add(RcDocLayout(40, -1, URL_TEXT_ID))
          addAll(size(EMBEDDED_WIDTH, HEIGHT))
          add(containerEnd)
          offsetChild(painted)
        }
      },
    )

  fun encode(width: Int, operations: List<RcOperation>) =
    RcDocumentCodec.encode(RcDocument(header(width), operations))

  /** What a custom component saw, and how often its remembered state was created and forgotten. */
  class Probe {
    var layer: RcModulateAlphaLayer? = null
    var remembers = 0
    var forgets = 0
    /** Remembered and not yet forgotten: the subtrees that hold this probe now. */
    val liveRemembers
      get() = remembers - forgets
  }

  private class RememberCounter(private val probe: Probe) : RememberObserver {
    override fun onRemembered() {
      probe.remembers++
    }

    override fun onForgotten() {
      probe.forgets++
    }

    override fun onAbandoned() = Unit
  }

  /** A custom component named [name] that records into [probe]. */
  fun probeEntry(
    name: String,
    probe: Probe,
  ): Pair<String, @Composable (RcCustomComponent, Modifier) -> Unit> =
    name to
      { _, modifier ->
        probe.layer = LocalRcModulateAlphaLayer.current
        remember { RememberCounter(probe) }
        Box(modifier)
      }
}

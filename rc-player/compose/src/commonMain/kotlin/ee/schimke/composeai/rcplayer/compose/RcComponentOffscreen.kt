package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionCall
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionDefine
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.runtime.RcLayoutNode
import ee.schimke.composeai.rcplayer.runtime.RcLinkedNode
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState

/*
 * remote-core alpha22's component offscreen buffers.
 *
 * `createOffscreenBitmap(imageId[, componentId])` declares a `BitmapData` with
 * `ENCODING_COMPONENT_OFFSCREEN_BUFFER`: no pixels and no size of its own, but a buffer sized to a
 * component — the one its payload names, or else the component being drawn into it, or else the
 * active component — each time it is drawn into. `drawComponentToBitmap` then redirects drawing
 * into it (`DrawToBitmap`, with `MODE_COMPONENT_ID` naming the component), draws the component
 * there (`DrawContent`), and returns (`DrawToBitmap(0)`); the bitmap can then be drawn like any
 * other — scaled, mirrored, through a shader.
 *
 * Core's "active component" is `RemoteContext.mLastComponent`: the component whose operations are
 * painting. Here that is [RcActiveComponent], handed to each draw stream by whoever runs it.
 */

/**
 * The component whose draw stream is running — remote-core's `RemoteContext.mLastComponent` — for a
 * component offscreen buffer named without a component id.
 *
 * [drawSelf] draws the component at the current origin, as core's `DrawContent` paints it into an
 * offscreen buffer; null where the stream has no content to draw (a canvas component's own drawing,
 * which is not separable from the stream running it).
 */
internal class RcActiveComponent(
  val componentId: Int,
  val size: Size,
  val drawSelf: (() -> Unit)?,
)

/**
 * What a component offscreen buffer can reach of components other than the active one: every
 * component's current size, for sizing a buffer by id, and — for the components a document draws by
 * id from a function, where core's `DrawContent` paints the named component — a recording of the
 * component to draw.
 *
 * Each recorded component draws through its own [GraphicsLayer] (see [rcComponentCapture]); drawing
 * that layer into a buffer is the CMP counterpart of core's `Component.paintingComponent`. A
 * recording is the component as it last drew, so a component that draws after the one capturing it
 * in a frame shows its previous frame; [recorded] lets the capturing component redraw once the
 * recording changes, so the buffer converges on the next frame.
 */
internal class RcComponentCaptures(
  private val componentIds: Set<Int>,
  /** The components to record, or null for every one: a reference that resolves only at runtime. */
  private val recordedIds: Set<Int>?,
) {
  private val sizes = mutableMapOf<Int, IntSize>()
  private val layers = mutableMapOf<Int, GraphicsLayer>()
  private val recorded = mutableIntStateOf(0)
  private var recordings = 0

  /** Whether the document has a component [id] — core's `CoreDocument.getComponent(id) != null`. */
  fun exists(id: Int): Boolean = id in componentIds

  fun records(id: Int): Boolean = exists(id) && (recordedIds == null || id in recordedIds)

  /** Component [id]'s size as last laid out, or null before it has been. */
  fun size(id: Int): IntSize? = sizes[id]

  internal fun setSize(id: Int, size: IntSize) {
    sizes[id] = size
  }

  internal fun onRecorded(id: Int, layer: GraphicsLayer) {
    layers[id] = layer
    // Written without being read, so the recording component never observes its own write.
    recorded.intValue = ++recordings
  }

  internal fun forget(id: Int, layer: GraphicsLayer) {
    if (layers[id] === layer) layers.remove(id)
  }

  /**
   * Draws component [id] at [scope]'s origin, as last recorded; false — drawing nothing — when it
   * is not recorded, or has not drawn yet.
   */
  fun draw(scope: DrawScope, id: Int): Boolean {
    recorded.intValue // Redraw the capturing component when a recording changes.
    val layer = layers[id] ?: return false
    scope.drawLayer(layer)
    return true
  }

  companion object {
    /**
     * The captures [document] needs, or null when it has no component offscreen buffer — so a
     * document without one pays nothing.
     */
    fun of(
      document: RcDocument,
      linked: List<RcLinkedNode>,
      layout: RcLayoutNode.Root,
    ): RcComponentCaptures? {
      val operations = document.operations
      val buffers = operations.any {
        it is RcBitmapData && it.encoding == RcBitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER
      }
      val byId = operations.any {
        it is RcDrawToBitmap && it.mode and RcDrawToBitmap.MODE_COMPONENT_ID != 0
      }
      if (!buffers && !byId) return null
      val componentIds = buildSet { layout.collectComponentIds(this) }
      val references = RcComponentReferences(operations, componentIds)
      // Core paints the named component only for a `DrawContent` no component owns — one in a
      // function body. Elsewhere `DrawContent` paints its own component, so nothing to record.
      val drawnById = mutableListOf<Int>()
      fun collect(nodes: List<RcLinkedNode>, inFunction: Boolean) {
        for (node in nodes) {
          when (node) {
            is RcLinkedNode.Container ->
              collect(node.children, inFunction || node.operation is RcFloatFunctionDefine)
            is RcLinkedNode.Operation -> {
              val operation = node.operation
              if (
                inFunction &&
                  operation is RcDrawToBitmap &&
                  operation.mode and RcDrawToBitmap.MODE_COMPONENT_ID != 0
              ) {
                drawnById += operation.color
              }
            }
          }
        }
      }
      collect(linked, inFunction = false)
      var recordedIds: MutableSet<Int>? = mutableSetOf()
      for (reference in drawnById) {
        val resolved = references.resolve(reference)
        if (resolved == null) {
          recordedIds = null
          break
        }
        recordedIds?.addAll(resolved)
      }
      return RcComponentCaptures(componentIds, recordedIds)
    }
  }
}

/**
 * Which components a component reference in a `DrawToBitmap` or a buffer's payload can name, read
 * the way core resolves it at runtime (`context.getInteger(id)`, then the id itself): a component
 * id; a constant holding one; or a function parameter, through the arguments its calls pass. A
 * custom visibility function's component parameter names the animating component, which is the
 * active one and needs nothing recorded. `null` when the reference is computed at runtime.
 */
private class RcComponentReferences(
  operations: List<ee.schimke.composeai.rcplayer.protocol.RcOperation>,
  private val componentIds: Set<Int>,
) {
  private val integers =
    operations.filterIsInstance<RcIntegerConstant>().associate { it.id to it.value }
  private val floats =
    operations
      .filterIsInstance<RcFloatConstant>()
      .filter { it.value.referencedId == null }
      .associate { it.id to it.value.value.toInt() }
  /** Parameter id to (function id, index). */
  private val parameters =
    operations
      .filterIsInstance<RcFloatFunctionDefine>()
      .flatMap { function ->
        function.parameterIds.mapIndexed { i, id -> id to (function.id to i) }
      }
      .toMap()
  private val calls = operations.filterIsInstance<RcFloatFunctionCall>().groupBy { it.functionId }
  private val visibilityFunctions =
    operations
      .filterIsInstance<RcAnimationSpec>()
      .flatMap {
        listOfNotNull(
          it.enterFunctionId.takeIf { _ ->
            it.enterAnimation.androidXValue == RcLayoutAnimation.Custom.wireValue
          },
          it.exitFunctionId.takeIf { _ ->
            it.exitAnimation.androidXValue == RcLayoutAnimation.Custom.wireValue
          },
        )
      }
      .toSet()

  fun resolve(reference: Int, depth: Int = 0): Set<Int>? {
    if (reference <= 0 || depth > MAX_DEPTH) return emptySet()
    if (reference in componentIds) return setOf(reference)
    integers[reference]?.let {
      return setOfNotNull(it.takeIf(componentIds::contains))
    }
    floats[reference]?.let {
      return setOfNotNull(it.takeIf(componentIds::contains))
    }
    val (functionId, index) = parameters[reference] ?: return null
    val names = mutableSetOf<Int>()
    if (functionId in visibilityFunctions && index != COMPONENT_PARAMETER) return null
    for (call in calls[functionId].orEmpty()) {
      val argument = call.arguments.getOrNull(index) ?: continue
      val referenced = argument.referencedId
      if (referenced == null) {
        argument.value.toInt().takeIf(componentIds::contains)?.let(names::add)
      } else {
        names += resolve(referenced, depth + 1) ?: return null
      }
    }
    return names
  }

  private companion object {
    /** The component id is the sixth value a custom visibility function is bound with. */
    const val COMPONENT_PARAMETER = 5
    const val MAX_DEPTH = 8
  }
}

private fun RcLayoutNode.collectComponentIds(into: MutableSet<Int>) {
  if (componentId > 0) into += componentId
  val children =
    when (this) {
      is RcLayoutNode.Root -> children
      is RcLayoutNode.Content -> children
      is RcLayoutNode.Custom -> children
      is RcLayoutNode.Canvas -> listOfNotNull(content)
      is RcLayoutNode.Box -> listOfNotNull(content)
      is RcLayoutNode.Row -> listOf(content)
      is RcLayoutNode.Column -> listOf(content)
      is RcLayoutNode.Flow -> listOf(content)
      is RcLayoutNode.State -> listOf(content)
      is RcLayoutNode.CollapsibleRow -> listOf(content)
      is RcLayoutNode.CollapsibleColumn -> listOf(content)
      is RcLayoutNode.FitBox -> listOf(content)
      is RcLayoutNode.CanvasContent,
      is RcLayoutNode.Image,
      is RcLayoutNode.Text,
      is RcLayoutNode.CoreText -> emptyList()
    }
  children.forEach { it.collectComponentIds(into) }
}

/** The document's component captures; null outside a layout document that has buffers. */
internal val LocalRcComponentCaptures = staticCompositionLocalOf<RcComponentCaptures?> { null }

/**
 * Tracks component [componentId]'s size for [captures], and records it into a [GraphicsLayer] when
 * a function draws it by id. The layer is the component as core's `paintingComponent` paints it:
 * its modifiers and content at its own origin, without its position or its visibility animation.
 *
 * The recording sits in a layer of its own, so a capturing ancestor redrawing does not re-record it
 * — which would invalidate the ancestor again, every frame.
 */
@Composable
internal fun Modifier.rcComponentCapture(
  componentId: Int,
  captures: RcComponentCaptures?,
): Modifier {
  if (captures == null || !captures.exists(componentId)) return this
  val sized = Modifier.onSizeChanged { captures.setSize(componentId, it) }
  if (!captures.records(componentId)) return then(sized)
  val layer = rememberGraphicsLayer()
  DisposableEffect(captures, componentId, layer) {
    onDispose { captures.forget(componentId, layer) }
  }
  return then(sized).graphicsLayer().drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
    captures.onRecorded(componentId, layer)
  }
}

/**
 * The component a `DrawToBitmap` colour word or a buffer's payload names, resolved as core does:
 * the integer the id holds when it is a variable that holds one, else the id itself.
 */
internal fun RcPlayerState.resolveComponentReference(reference: Int): Int {
  if (reference <= 0) return reference
  val value = integer(reference)
  return if (value != null && value != 0) value else reference
}

/**
 * The component a component offscreen buffer's payload names — 4 bytes, big-endian — or 0 for the
 * active component (core's `BitmapData.getComponentId`).
 */
internal val RcBitmapData.offscreenComponentId: Int
  get() =
    if (encoding != RcBitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER || data.size < 4) 0
    else
      ((data[0].toInt() and 0xff) shl 24) or
        ((data[1].toInt() and 0xff) shl 16) or
        ((data[2].toInt() and 0xff) shl 8) or
        (data[3].toInt() and 0xff)

/** [document]'s component offscreen buffers by bitmap id, for [RcOffscreenTargetPool]. */
internal fun rcComponentOffscreenBuffers(document: RcDocument): Map<Int, RcBitmapData> =
  document.operations
    .filterIsInstance<RcBitmapData>()
    .filter { it.encoding == RcBitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER }
    .associateBy { it.imageId }

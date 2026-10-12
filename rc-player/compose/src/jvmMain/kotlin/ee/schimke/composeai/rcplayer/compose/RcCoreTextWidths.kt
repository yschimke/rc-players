package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState

/**
 * The width in dp each `CoreText` lays its text out in when this player shows the document at
 * [width] by [height] pixels and [density] — its laid-out width less its own horizontal padding —
 * by component id.
 *
 * What `rc-player-edit`'s `coreTextAsImages` wraps a text at, so a paragraph drawn to an image
 * breaks its lines where the player broke them rather than at the screen's edge. One display's
 * answer: a text whose slot grows with the screen wraps there differently.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
public fun RcDocument.coreTextWidths(
  density: Float,
  typefaces: RcTypefaceLoader = RcTypefaceLoader.Default,
  width: Int = header.width,
  height: Int = header.height,
): Map<Int, Float> {
  val padding = coreTextPadding()
  if (padding.isEmpty()) return emptyMap()
  val scene =
    ImageComposeScene(width = width, height = height, density = Density(density)) {
      CompositionLocalProvider(LocalRcInspection provides true) {
        RcComposePlayer(this, Modifier.fillMaxSize(), typefaces = typefaces)
      }
    }
  return try {
    scene.render(0L)
    val root = scene.semanticsOwners.firstOrNull()?.unmergedRootSemanticsNode ?: return emptyMap()
    var state: RcPlayerState? = null
    val widths = mutableMapOf<Int, Int>()
    fun walk(node: SemanticsNode) {
      node.config.getOrElseNullable(RcDocumentStateKey) { null }?.let { state = it }
      val id = node.config.getOrElseNullable(RcComponentIdKey) { null }
      if (id != null && id in padding) widths[id] = node.size.width
      node.children.forEach(::walk)
    }
    walk(root)
    val resolved = state ?: return emptyMap()
    widths.mapValues { (id, pixels) ->
      val inset =
        padding.getValue(id).sumOf { modifier ->
          (pixels(resolved, modifier.left, density) + pixels(resolved, modifier.right, density))
            .toDouble()
        }
      ((pixels - inset) / density).toFloat().coerceAtLeast(0f)
    }
  } finally {
    scene.close()
  }
}

private fun RcDocument.pixels(
  state: RcPlayerState,
  word: RcFloatWord,
  density: Float,
): Float = rcDpTypedPixels(state.resolve(word), density, header.densityBehavior)

/** Each `CoreText`'s own padding modifiers, by component id. */
private fun RcDocument.coreTextPadding(): Map<Int, List<RcPaddingModifier>> {
  val padding = mutableMapOf<Int, List<RcPaddingModifier>>()
  operations.forEachIndexed { index, operation ->
    if (operation !is RcCoreText) return@forEachIndexed
    padding[operation.componentId] =
      operations
        .drop(index + 1)
        .takeWhile { it !is RcLayoutContent && it !is RcCoreText }
        .filterIsInstance<RcPaddingModifier>()
  }
  return padding
}

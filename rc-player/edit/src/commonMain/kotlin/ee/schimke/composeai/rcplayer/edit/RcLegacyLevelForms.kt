package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation

// The forms the creation writer writes for API levels up to 7 and not above, which a document
// written for a later level has to be given to play on a target that reads only those levels. See
// docs/design/VERSION_QUIRKS.md: each is the counterpart of one write in `RemoteDocumentWriter`.

/**
 * The drawing of each canvas in a `CanvasContent`, for a target up to API level 7, and each
 * `ComponentValue` of the canvas's size naming it.
 *
 * Up to level 7 AndroidX's writer (`RemoteComposeWriter.startCanvas`) writes a canvas's drawing in
 * a `CanvasContent` inside its `LayoutContent`; from level 8 it writes the drawing in the
 * `LayoutContent`. A level 7 player does not draw a canvas whose drawing is not in one: Android
 * 17's platform player draws it as nothing. A `ComponentValue` written in the content names the
 * content (the id of the last component started, as AndroidX writes it): Android 16's platform
 * player reads a value that names the canvas itself as unmeasured, and draws a canvas that reads it
 * as nothing.
 *
 * A canvas that already has its `CanvasContent` is left as it is. For a target at level 8 or later
 * nothing changes: its players read a `CanvasContent` all the same.
 */
internal fun RcDocument.canvasContentFor(target: RcTargetProfile): RcDocument {
  if (target.apiLevel > 7 || RcOpcodes.LAYOUT_CANVAS_CONTENT !in target.supportedOpcodes)
    return this
  val tree = operationTree(operations) ?: return this
  val ids =
    RcFreshIds(
      operations.flatMapTo(mutableSetOf()) { it.mentionedValues() ?: emptySet() },
      componentIds(operations),
    )
  // The index of a `LayoutContent` that gets a `CanvasContent`, and the content's id; the index of
  // the `ContainerEnd` that closes it; and each canvas value's index, with the id it names.
  val opened = mutableMapOf<Int, Int>()
  val closed = mutableSetOf<Int>()
  val renamed = mutableMapOf<Int, Int>()
  tree.forEach { root ->
    root.walk { node ->
      val canvas = node.operation as? RcCanvasLayout ?: return@walk
      val layout =
        node.children.firstOrNull { it.operation is RcLayoutContent && it.isContainer }
          ?: return@walk
      if (layout.children.firstOrNull()?.operation is RcCanvasContent) return@walk
      val content = ids.nextComponent()
      opened[layout.start] = content
      closed += layout.end
      layout.walk { inner ->
        val value = inner.operation as? RcComponentValue
        if (value != null && value.componentId == canvas.componentId) renamed[inner.start] = content
      }
    }
  }
  if (opened.isEmpty()) return this
  val end: RcOperation = RcNoArg(RcOpcodes.CONTAINER_END)
  return copy(
    operations =
      buildList {
        operations.forEachIndexed { index, operation ->
          if (index in closed) add(end)
          val content = renamed[index]
          add(
            if (content != null) (operation as RcComponentValue).copy(componentId = content)
            else operation,
          )
          opened[index]?.let { add(RcCanvasContent(it)) }
        }
      },
  )
}

/**
 * Each border in the legacy form (wire version 0: the stroke centred on the edge, half of it
 * clipped) for a target up to API level 7, as AndroidX's `RemoteComposeWriter.addModifierBorder`
 * writes it there. Wire version 1, the stroke inside the bounds, is written from level 8; no player
 * that reads only level 7 reads it.
 */
internal fun RcDocument.legacyBorders(target: RcTargetProfile): RcDocument {
  if (target.apiLevel > 7 || operations.none { it is RcBorderModifier && it.wireVersion != 0 }) {
    return this
  }
  return copy(
    operations =
      operations.map {
        if (it is RcBorderModifier && it.wireVersion != 0) it.copy(wireVersion = 0) else it
      },
  )
}

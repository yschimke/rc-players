package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcLoopOperation
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcTheme

/**
 * This document with every `ColorTheme` — a light/dark pair of system colours, each with a literal
 * fallback — replaced by its two fallbacks, for players that have no `ColorTheme`.
 *
 * This is an approximation: a player with the system palette draws the device's colour (Material
 * You's, say) where this draws the colour recorded when the document was written. A player without
 * the palette draws the fallbacks already, and draws exactly what it did.
 *
 * With [fixedTheme] null the colour still follows the theme, written the way AndroidX's level 6
 * widget writer writes a themed colour: a `Theme(DARK)` section and a `Theme(LIGHT)` section, each
 * setting the colour from a one-pass loop so it runs as the document draws. With [fixedTheme]
 * [RcTheme.DARK] or [RcTheme.LIGHT] the colour is that theme's fallback, for a player that has no
 * theme sections (Wear OS, which draws dark).
 *
 * AndroidX applies theme sections only to the document's top-level operations, so with [fixedTheme]
 * null each colour moves to just before the top-level operation holding it — usually the root
 * layout. Its value depends on nothing but the theme, so it is the same value earlier.
 *
 * A `ColorTheme` sitting where a theme marker is already in force, or whose id something else also
 * defines, is left alone, and so, for theme sections, is one inside a conditional or a loop, which
 * moving to the top level would make unconditional.
 */
public fun RcDocument.colorThemesAsFallbacks(fixedTheme: Int? = null): RcDocument {
  require(fixedTheme == null || fixedTheme == RcTheme.DARK || fixedTheme == RcTheme.LIGHT) {
    "fixedTheme must be RcTheme.DARK, RcTheme.LIGHT or null"
  }
  if (operations.none { it is RcColorTheme }) return this
  val tree = operationTree(operations) ?: return this
  val unthemed = unthemedOperations(operations) ?: return this
  val unconditional = unconditionalOperations(operations) ?: return this
  val replaced = mutableListOf<Int>()
  // Theme sections gate only at the top level, so a themed colour inside a layout moves to just
  // before the top-level operation that holds it; one at the top level stays where it is.
  val inserted = mutableMapOf<Int, MutableList<RcOperation>>()
  val removed = mutableSetOf<Int>()
  tree.forEach { top ->
    top.walk { node ->
      val theme = node.operation as? RcColorTheme ?: return@walk
      // Neither where it was nor, for sections, where they go may be under a theme marker. Sections
      // move to the top level, so only a colour that always ran may move: one under a condition or
      // in a loop could have been left unset, and stays.
      if (!unthemed[node.start]) return@walk
      if (fixedTheme == null && (!unthemed[top.start] || !unconditional[node.start])) return@walk
      val definitions = operations.count { it.definedIds()?.contains(theme.outId) == true }
      if (definitions != 1) return@walk
      replaced += theme.outId
      val fallback =
        when (fixedTheme) {
          null -> themedSections(theme)
          RcTheme.LIGHT -> listOf(RcColorConstant(theme.outId, theme.lightModeFallback))
          else -> listOf(RcColorConstant(theme.outId, theme.darkModeFallback))
        }
      inserted.getOrPut(if (fixedTheme == null) top.start else node.start) { mutableListOf() } +=
        fallback
      removed += node.start
    }
  }
  if (replaced.isEmpty()) return this
  val rewritten = operations.flatMapIndexed { index, operation ->
    inserted[index].orEmpty() + listOfNotNull(operation.takeUnless { index in removed })
  }
  return copy(operations = rewritten)
}

/**
 * `RemoteComposeWriter.addThemedColor(lightId, darkId)`'s shape, with the fallbacks inline: an
 * interpolation at 0 is exactly its first colour and at 1 exactly its second, on both players.
 */
private fun themedSections(theme: RcColorTheme): List<RcOperation> {
  fun section(marker: Int, tween: Float): List<RcOperation> =
    listOf(
      RcTheme(marker),
      RcLoopOperation(0, RcFloatWord.literal(0f), RcFloatWord.literal(1f), RcFloatWord.literal(1f)),
      RcColorExpression(
        outId = theme.outId,
        modeAndAlpha = RcColorExpression.COLOR_COLOR_INTERPOLATE,
        first = theme.darkModeFallback,
        second = theme.lightModeFallback,
        third = tween.toRawBits(),
      ),
      RcNoArg(RcOpcodes.CONTAINER_END),
    )
  return section(RcTheme.DARK, 0f) + section(RcTheme.LIGHT, 1f) + RcTheme(RcTheme.UNSPECIFIED)
}

/**
 * Which of [operations] no theme marker gates, read both ways the players read markers — lexically
 * scoped (CMP) and as one flat stream (AndroidX) — or null when the containers do not balance.
 */
private fun unthemedOperations(operations: List<RcOperation>): BooleanArray? {
  val tree = operationTree(operations) ?: return null
  val result = BooleanArray(operations.size)
  fun visit(nodes: List<RcOperationNode>, inherited: Int) {
    var theme = inherited
    nodes.forEach { node ->
      if (node.operation is RcTheme) theme = (node.operation as RcTheme).theme
      result[node.start] = theme == RcTheme.UNSPECIFIED
      visit(node.children, theme)
    }
  }
  visit(tree, RcTheme.UNSPECIFIED)
  var flat = RcTheme.UNSPECIFIED
  operations.forEachIndexed { index, operation ->
    if (operation is RcTheme) flat = operation.theme
    else if (flat != RcTheme.UNSPECIFIED) result[index] = false
  }
  return result
}

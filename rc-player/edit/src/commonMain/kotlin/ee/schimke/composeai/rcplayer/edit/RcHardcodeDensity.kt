package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables

/**
 * This document as it plays on one display: every float expression operand reading the player's
 * [RcSystemVariables.DENSITY] or [RcSystemVariables.FONT_SIZE] replaced by that display's value.
 *
 * A `RemoteDensity.Host` capture sizes its text and spacing as expressions over those two ids —
 * `[27] 12.0 *` for 12dp — so that one document fits every display. Android 16's launcher reads a
 * text size, padding or border only as a literal, so such a document does not play there at all.
 * Baked for the display it is served to, those expressions become constants [adaptTo] folds into
 * the literals that player needs.
 *
 * The result is right only at [density] and [fontScale]: serve it to that display, not cache it for
 * others. [fontScale] is the host's text scale, by which [RcSystemVariables.FONT_SIZE] — 14sp in
 * pixels — follows the user's text size setting.
 */
public fun RcDocument.hardcodeDensity(density: Float, fontScale: Float = 1f): RcDocument {
  require(density.isFinite() && density > 0f) { "density must be positive: $density" }
  require(fontScale.isFinite() && fontScale > 0f) { "fontScale must be positive: $fontScale" }
  val baked =
    mapOf(
      RcSystemVariables.DENSITY to RcFloatWord.literal(density),
      RcSystemVariables.FONT_SIZE to RcFloatWord.literal(DEFAULT_TEXT_SP * density * fontScale),
    )
  var changed = false
  val rewritten = operations.map { operation ->
    if (operation !is RcFloatExpression) return@map operation
    val expression =
      operation.expression.map { word ->
        word.referencedId?.let(baked::get)?.also { changed = true } ?: word
      }
    operation.copy(expression = expression)
  }
  return if (changed) copy(operations = rewritten) else this
}

/** The text size [RcSystemVariables.FONT_SIZE] holds at a font scale of 1. */
private const val DEFAULT_TEXT_SP = 14f

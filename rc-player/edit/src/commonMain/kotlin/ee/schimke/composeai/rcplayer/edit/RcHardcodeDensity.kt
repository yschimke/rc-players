package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFlowLayout
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
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

/**
 * This document with each row's, column's and flow layout's spacing a literal: the number it is on
 * a display of [density] and [fontScale]. The recovery a document needs before AndroidX's
 * `CoreDocument` layout managers — the AndroidX View player (`RemoteComposePlayer`), the Android 16
 * and 17 platform widget/notification players, and Wear widgets — are handed it.
 *
 * Those players read `mSpacedBy` only as a literal float: a `RemoteDensity.Host` capture writes
 * every `Arrangement.spacedBy(n.dp)` as an expression over the display's density, whose variable id
 * leaks as `NaN` into layout sizes (drawing blank on a launcher or failing downstream
 * `FloatExpression`s in the View player).
 *
 * Only the spacing changes: the document keeps its other density expressions, its named values and
 * its state layouts. A spacing no display fixes — one following a host value, or the time — stays a
 * variable, which [validateFor] reports for players missing
 * [RcPlayerFeature.VARIABLE_LAYOUT_SPACING]. [adaptTo] applies this for those targets.
 *
 * Like [hardcodeDensity], the result is right only on that display.
 */
public fun RcDocument.hardcodeLayoutSpacing(density: Float, fontScale: Float = 1f): RcDocument {
  if (operations.none { it.layoutSpacing?.isNaNEncoded == true }) return this
  // Baking only changes expression operands, so each operation keeps its index.
  val values = RcValueAnalysis(hardcodeDensity(density, fontScale).operations)
  var changed = false
  val rewritten = operations.mapIndexed { index, operation ->
    val id = operation.layoutSpacing?.takeIf { it.isNaNEncoded }?.referencedId
    val literal = id?.let { values.float(it, at = index) }?.takeUnless { it.isNaNEncoded }
    if (literal == null) operation else operation.withLayoutSpacing(literal).also { changed = true }
  }
  return if (changed) copy(operations = rewritten) else this
}

/**
 * The spacing a row, column or flow layout puts between its children, or null for anything else.
 */
internal val RcOperation.layoutSpacing: RcFloatWord?
  get() =
    when (this) {
      is RcRowLayout -> spacedBy
      is RcColumnLayout -> spacedBy
      is RcFlowLayout -> spacedBy
      is RcCollapsibleRowLayout -> spacedBy
      is RcCollapsibleColumnLayout -> spacedBy
      else -> null
    }

private fun RcOperation.withLayoutSpacing(spacing: RcFloatWord): RcOperation =
  when (this) {
    is RcRowLayout -> copy(spacedBy = spacing)
    is RcColumnLayout -> copy(spacedBy = spacing)
    is RcFlowLayout -> copy(spacedBy = spacing)
    is RcCollapsibleRowLayout -> copy(spacedBy = spacing)
    is RcCollapsibleColumnLayout -> copy(spacedBy = spacing)
    else -> this
  }

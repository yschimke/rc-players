package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcTextData

/** Result of checking whether one complete Remote Compose document can update another in place. */
public sealed interface RcDocumentDataUpdateResult {
  /** The candidate is byte-for-byte equivalent at the operation-model level. */
  public data object Unchanged : RcDocumentDataUpdateResult

  /** A data-only document AndroidX can apply to the existing player without replacing it. */
  public data class Update(public val document: RcDocument) : RcDocumentDataUpdateResult

  /** Header, layout, actions, expressions, or unsupported data changed; send a full document. */
  public data object FullDocumentRequired : RcDocumentDataUpdateResult
}

/**
 * Compares this loaded base document with a freshly encoded [candidate].
 *
 * The result is [RcDocumentDataUpdateResult.Update] only when the headers and operation sequence
 * are unchanged apart from AndroidX `CoreDocument.applyUpdate` data slots: text, bitmap, float,
 * integer, long and float list. Its document contains a complete snapshot of every supported data
 * slot in [candidate], rather than only the changed slots. That is required for
 * `AppWidgetManager.partiallyUpdateAppWidget`: the framework cache retains only the latest
 * `DrawInstructions` update action, so a launcher rebind must reconstruct current values from one
 * update document plus the full base document. Bitmap deltas work in AppWidgets, but their
 * allocations can remain cached until the next full update, so a host may schedule one for memory
 * hygiene.
 *
 * A successful result is not a semantic proof that two independently captured documents have the
 * same layout; it is deliberately stricter. Any reordered, added, removed, or unsupported changed
 * operation returns [RcDocumentDataUpdateResult.FullDocumentRequired].
 */
public fun RcDocument.dataUpdateTo(candidate: RcDocument): RcDocumentDataUpdateResult {
  if (header != candidate.header || header.isUpdateDocument || candidate.header.isUpdateDocument) {
    return RcDocumentDataUpdateResult.FullDocumentRequired
  }
  if (operations.size != candidate.operations.size) {
    return RcDocumentDataUpdateResult.FullDocumentRequired
  }

  var changed = false
  operations.zip(candidate.operations).forEach { (base, next) ->
    if (base == next) return@forEach
    if (!base.matchesUpdateSlot(next)) {
      return RcDocumentDataUpdateResult.FullDocumentRequired
    }
    changed = true
  }
  if (!changed) return RcDocumentDataUpdateResult.Unchanged

  return RcDocumentDataUpdateResult.Update(
    RcDocument(
      header =
        RcHeader(
          version = candidate.header.version,
          properties =
            listOf(RcHeaderProperty(RcHeader.DOC_DATA_UPDATE, RcHeaderValue.IntValue(1))),
          modern = true,
        ),
      operations = candidate.operations.filter(RcOperation::isUpdateData),
    )
  )
}

private fun RcOperation.matchesUpdateSlot(next: RcOperation): Boolean =
  when {
    this is RcTextData && next is RcTextData -> id == next.id
    this is RcFloatConstant && next is RcFloatConstant -> id == next.id
    this is RcIntegerConstant && next is RcIntegerConstant -> id == next.id
    this is RcLongConstant && next is RcLongConstant -> id == next.id
    this is RcFloatList && next is RcFloatList -> id == next.id
    this is RcBitmapData && next is RcBitmapData -> imageId == next.imageId
    else -> false
  }

private fun RcOperation.isUpdateData(): Boolean =
  this is RcTextData ||
    this is RcFloatConstant ||
    this is RcIntegerConstant ||
    this is RcLongConstant ||
    this is RcFloatList ||
    this is RcBitmapData

package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation

/** What [rewriteOperations] does with one operation. */
public sealed interface RcRewrite {
  /** The operation stays as it is; a container's body is rewritten in its turn. */
  public data object Keep : RcRewrite

  /** The operation goes; a container goes with its whole body, up to its `ContainerEnd`. */
  public data object Remove : RcRewrite

  /**
   * [operations] take the operation's place; for a container, the place of its whole body, up to
   * its `ContainerEnd`. A replacement that opens a container must close it.
   */
  public data class Replace(val operations: List<RcOperation>) : RcRewrite {
    public constructor(vararg operations: RcOperation) : this(operations.toList())
  }
}

/**
 * This document with each operation kept, removed or replaced as [rewrite] decides, outermost
 * first: a container [rewrite] keeps has its body offered next, one it removes or replaces takes
 * its body with it. `ContainerEnd`s are never offered; they follow their container.
 *
 * [rewrite] gets the operation's index in [RcDocument.operations] and the operation. A document
 * whose containers do not balance is returned unchanged, rather than cut at a guessed boundary.
 */
public fun RcDocument.rewriteOperations(
  rewrite: (index: Int, operation: RcOperation) -> RcRewrite
): RcDocument {
  val tree = operationTree(operations) ?: return this
  var changed = false
  val rewritten = buildList {
    fun emit(node: RcOperationNode) {
      when (val decision = rewrite(node.start, node.operation)) {
        RcRewrite.Keep -> {
          add(node.operation)
          if (node.isContainer) {
            node.children.forEach(::emit)
            add(operations[node.end])
          }
        }
        RcRewrite.Remove -> changed = true
        is RcRewrite.Replace -> {
          changed = true
          addAll(decision.operations)
        }
      }
    }
    tree.forEach(::emit)
  }
  return if (changed) copy(operations = rewritten) else this
}

/**
 * What [adaptTo] does with an operation [RcTargetProfile] still lacks once its own rewrites are
 * done. [keep] leaves it, to be reported among the violations; a policy can remove it or replace it
 * with something the target has.
 */
public fun interface RcUnsupportedOperations {
  /** The rewrite for [operation], which the target being adapted to does not decode. */
  public fun rewrite(operation: RcOperation): RcRewrite

  public companion object {
    /** Leaves every unsupported operation where it is, reported: [adaptTo]'s default. */
    public val keep: RcUnsupportedOperations = RcUnsupportedOperations { RcRewrite.Keep }

    /**
     * Removes the unsupported operations that never draw anything — sounds, haptic feedback and
     * debug messages — and keeps the rest, reported. A target without them plays the document
     * silently and otherwise as it looks.
     */
    public val dropSilent: RcUnsupportedOperations = RcUnsupportedOperations { operation ->
      if (operation.opcode in SILENT_OPCODES) RcRewrite.Remove else RcRewrite.Keep
    }

    /** Removes every unsupported operation: the document plays, without whatever they drew. */
    public val removeAll: RcUnsupportedOperations = RcUnsupportedOperations { RcRewrite.Remove }
  }
}

/** [RcUnsupportedOperations.rewrite]'s decision for each operation [target] lacks, others kept. */
internal fun RcDocument.rewriteUnsupported(
  target: RcTargetProfile,
  policy: RcUnsupportedOperations,
): RcDocument =
  if (policy === RcUnsupportedOperations.keep) this
  else
    rewriteOperations { _, operation ->
      if (operation.opcode in target.supportedOpcodes) RcRewrite.Keep else policy.rewrite(operation)
    }

/** Operations that draw nothing: removing them changes what a document sounds or feels like. */
private val SILENT_OPCODES: Set<Int> =
  setOf(
    RcOpcodes.PLAY_SOUND,
    RcOpcodes.DATA_SOUND,
    RcOpcodes.SOUND_EXPRESSION,
    RcOpcodes.HAPTIC_FEEDBACK,
    RcOpcodes.DEBUG_MESSAGE,
  )

package ee.schimke.composeai.rcwidget

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedAction
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedActionValue
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcTextData

/**
 * Rewrites of a document's named host actions, for a host that runs only some of them.
 *
 * A template's taps are named host actions its app understands (`ha`, `open`, …) with a text
 * payload the app decodes. A Wear widget host runs only one: `SendPendingIntent`, whose integer
 * value indexes the `PendingIntent`s in the widget's extras. [toPendingIntent] turns the app's own
 * taps into that one, so the template stays shared between the app (which keeps its taps) and the
 * widget (whose taps open the app).
 */
public object RcHostActionRewrites {

  /**
   * The name Remote Compose's `pendingIntentAction` gives the action a Wear widget host runs:
   * AndroidX's `PendingIntentAction.ACTION_NAME`.
   */
  public const val SEND_PENDING_INTENT: String = "SendPendingIntent"

  /**
   * [document] with each host action named in [actionNames] sending the widget's `PendingIntent`
   * [index] instead: it names [SEND_PENDING_INTENT] with the integer [index]. Both are new
   * definitions; the action's own name and payload are left as they were, since a writer that
   * interns equal strings may share them with text the document shows. Unchanged when it has no
   * such action.
   */
  public fun toPendingIntent(
    document: RcDocument,
    actionNames: Set<String>,
    index: Int = 0,
  ): RcDocument {
    val nameIds =
      document.operations
        .filterIsInstance<RcTextData>()
        .filter { it.text in actionNames }
        .mapTo(HashSet()) { it.id }
    val first =
      document.operations.indexOfFirst { it is RcHostNamedAction && it.nameTextId in nameIds }
    if (first < 0) return document
    val (nameId, indexId) = freshIds(document, count = 2)
    val operations = ArrayList<RcOperation>(document.operations.size + 2)
    document.operations.forEachIndexed { i, op ->
      // Declared before the first action that reads them.
      if (i == first) {
        operations += RcTextData(nameId, SEND_PENDING_INTENT)
        operations += RcIntegerConstant(indexId, index)
      }
      operations +=
        if (op is RcHostNamedAction && op.nameTextId in nameIds)
          RcHostNamedAction(nameId, RcHostNamedActionValue.IntegerValue(indexId))
        else op
    }
    return document.copy(operations = operations)
  }

  /** The names of every named host action [document] declares. */
  public fun actionNames(document: RcDocument): Set<String> {
    val texts = document.operations.filterIsInstance<RcTextData>().associateBy { it.id }
    return document.operations.filterIsInstance<RcHostNamedAction>().mapNotNullTo(LinkedHashSet()) {
      texts[it.nameTextId]?.text
    }
  }
}

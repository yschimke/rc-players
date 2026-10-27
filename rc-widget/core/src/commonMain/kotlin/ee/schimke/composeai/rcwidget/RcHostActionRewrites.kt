package ee.schimke.composeai.rcwidget

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedAction
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedActionValue
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
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
   * [index] instead. The ids stay; only what they hold changes: the name text becomes
   * [SEND_PENDING_INTENT] and each tap's text payload the integer [index]. Unchanged when it has no
   * such action.
   */
  public fun toPendingIntent(
    document: RcDocument,
    actionNames: Set<String>,
    index: Int = 0,
  ): RcDocument {
    val texts = document.operations.filterIsInstance<RcTextData>().associateBy { it.id }
    val nameIds = texts.values.filter { it.text in actionNames }.mapTo(HashSet()) { it.id }
    val taps =
      document.operations.filterIsInstance<RcHostNamedAction>().filter { it.nameTextId in nameIds }
    if (taps.isEmpty()) return document
    // A payload is rewritten only when nothing but these taps reads it: the host reads an integer.
    val payloads =
      taps
        .mapNotNull { (it.value as? RcHostNamedActionValue.TextValue)?.valueId }
        .filterTo(HashSet()) { it in texts }
    val operations =
      document.operations.map { op ->
        when {
          op is RcTextData && op.id in nameIds -> RcTextData(op.id, SEND_PENDING_INTENT)
          op is RcTextData && op.id in payloads -> RcIntegerConstant(op.id, index)
          op is RcHostNamedAction && op.nameTextId in nameIds -> {
            val payload = (op.value as? RcHostNamedActionValue.TextValue)?.valueId
            if (payload != null && payload in payloads)
              RcHostNamedAction(op.nameTextId, RcHostNamedActionValue.IntegerValue(payload))
            else op
          }
          else -> op
        }
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

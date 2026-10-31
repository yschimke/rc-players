package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcTextData

/**
 * Creates an AndroidX data-only update document for a complete host-owned [snapshot].
 *
 * AndroidX `CoreDocument.applyUpdate` only replaces matching text, float, integer, long and float
 * list data declarations. It cannot update colors, structure, actions, or expressions. This
 * function therefore returns `null` unless every value is declared by this document with a matching
 * supported type and has a matching data declaration to replace.
 *
 * AppWidget hosts must send the complete current snapshot on every partial update. `RemoteViews`
 * merges a new `DrawInstructions` action into its cached full update, retaining only the latest
 * update document; a changed-values-only document would lose earlier changes when the launcher
 * recreates the widget. A `null` result means the host must publish a new full document instead.
 */
public fun RcDocument.snapshotUpdateDocument(snapshot: Map<String, RcNamedValue>): RcDocument? {
  if (snapshot.isEmpty()) return null

  val variables = operations.filterIsInstance<RcNamedVariable>().associateBy { it.name }
  val data = operations.associateBy(RcOperation::updateDataId)
  val updates = mutableListOf<RcOperation>()
  val updatedNames = mutableSetOf<String>()

  snapshot.forEach { (hostName, value) ->
    val name = resolveNamedVariable(hostName, variables)
    if (!updatedNames.add(name)) return null
    val variable = variables[name] ?: return null
    updates += updateOperation(variable, value, data[variable.id]) ?: return null
  }

  return RcDocument(
    header =
      RcHeader(
        version = header.version,
        properties = listOf(RcHeaderProperty(RcHeader.DOC_DATA_UPDATE, RcHeaderValue.IntValue(1))),
        modern = true,
      ),
    operations = updates,
  )
}

private fun resolveNamedVariable(
  hostName: String,
  variables: Map<String, RcNamedVariable>,
): String =
  when {
    ':' in hostName -> hostName
    "USER:$hostName" in variables -> "USER:$hostName"
    else -> hostName
  }

private fun RcOperation.updateDataId(): Int? =
  when (this) {
    is RcTextData -> id
    is RcFloatConstant -> id
    is RcIntegerConstant -> id
    is RcLongConstant -> id
    is RcFloatList -> id
    else -> null
  }

private fun updateOperation(
  variable: RcNamedVariable,
  value: RcNamedValue,
  declaration: RcOperation?,
): RcOperation? =
  when {
    variable.type == RcNamedVariable.STRING_TYPE &&
      value is RcNamedValue.Text &&
      declaration is RcTextData -> RcTextData(variable.id, value.value)
    variable.type == RcNamedVariable.FLOAT_TYPE &&
      value is RcNamedValue.FloatValue &&
      declaration is RcFloatConstant ->
      RcFloatConstant(variable.id, RcFloatWord.literal(value.value))
    variable.type == RcNamedVariable.INT_TYPE &&
      value is RcNamedValue.Integer &&
      declaration is RcIntegerConstant -> RcIntegerConstant(variable.id, value.value)
    variable.type == RcNamedVariable.INT_TYPE &&
      value is RcNamedValue.BooleanValue &&
      declaration is RcIntegerConstant -> RcIntegerConstant(variable.id, if (value.value) 1 else 0)
    variable.type == RcNamedVariable.LONG_TYPE &&
      value is RcNamedValue.LongValue &&
      declaration is RcLongConstant -> RcLongConstant(variable.id, value.value)
    variable.type == RcNamedVariable.FLOAT_ARRAY_TYPE &&
      value is RcNamedValue.FloatArrayValue &&
      declaration is RcFloatList -> RcFloatList(variable.id, value.values.map(RcFloatWord::literal))
    else -> null
  }

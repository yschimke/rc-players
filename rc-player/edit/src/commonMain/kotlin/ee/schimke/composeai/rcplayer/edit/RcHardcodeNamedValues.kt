package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDynamicFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIdMap
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcMatrixConstant
import ee.schimke.composeai.rcplayer.protocol.RcMatrixExpression
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPathAppend
import ee.schimke.composeai.rcplayer.protocol.RcPathCombine
import ee.schimke.composeai.rcplayer.protocol.RcPathCreate
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcPathExpression
import ee.schimke.composeai.rcplayer.protocol.RcPathTween
import ee.schimke.composeai.rcplayer.protocol.RcSoundExpression
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcUpdateDynamicFloatList
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatExpressionChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerExpressionChangeAction

/**
 * A value to write into a document in place of host-bound state.
 *
 * The same shapes a host binds through the player's named values, minus the float array, which has
 * no single defining operation to rewrite.
 */
public sealed interface RcHardcodedValue {
  public data class Text(val value: String) : RcHardcodedValue

  public data class FloatValue(val value: Float) : RcHardcodedValue

  public data class Color(val argb: Int) : RcHardcodedValue

  public data class Integer(val value: Int) : RcHardcodedValue

  public data class LongValue(val value: Long) : RcHardcodedValue

  /** An integer-typed name holding 1 or 0, as AndroidX keeps a boolean. */
  public data class BooleanValue(val value: Boolean) : RcHardcodedValue
}

/** Every named variable this document declares — the state a host can bind. */
public val RcDocument.namedVariables: List<RcNamedVariable>
  get() = operations.filterIsInstance<RcNamedVariable>()

/**
 * This document with each named variable in [values] hardcoded: its defining operation now holds
 * the given value, and its name declaration is removed so no host can bind it any more.
 *
 * A key matches a variable by its full name (`USER:progress`) or, as the player resolves a host's
 * names, by the name without the `USER:` prefix (`progress`) — which names the `USER:` declaration
 * when a document declares both `progress` and `USER:progress`. Keys that match nothing are
 * ignored, as the player ignores them.
 *
 * The variable stays a variable inside the document: an action the document runs can still change
 * it. What goes is the host's handle on it. Pair with [collapseConstantStateLayouts] to then drop
 * the state-layout branches a hardcoded index can no longer select.
 *
 * @throws IllegalArgumentException when a value's shape does not fit the variable's declared type,
 *   or the variable is an image or float array, which have no single value to write, or its value
 *   comes from an operation that cannot be swapped for one constant — one writing several ids, or
 *   an expression an expression-change action evaluates.
 */
public fun RcDocument.hardcodeNamedValues(values: Map<String, RcHardcodedValue>): RcDocument {
  if (values.isEmpty()) return this
  val byName =
    values.entries.mapNotNull { (key, value) -> declaredName(key)?.let { it to value } }.toMap()
  val targets =
    namedVariables.filter { it.name in byName }.associateWith { byName.getValue(it.name) }
  if (targets.isEmpty()) return this
  val byId = targets.entries.associate { (variable, value) -> variable.id to (variable to value) }
  operations
    .firstOrNull { operation ->
      (operation.definedIds()?.size ?: 0) > 1 && operation.definedIds()!!.any { it in byId }
    }
    ?.let { operation ->
      throw IllegalArgumentException(
        "${operationName(operation.opcode)} writes a hardcoded named variable along with other " +
          "ids, so it cannot be replaced by a constant"
      )
    }
  // An expression-change action looks its expression up by id when it runs. Replacing that
  // expression with a constant would leave the action nothing to evaluate.
  operations
    .mapNotNull {
      when (it) {
        is RcValueFloatExpressionChangeAction -> it.expressionId
        is RcValueIntegerExpressionChangeAction -> it.expressionId.toInt()
        else -> null
      }
    }
    .firstOrNull { it in byId }
    ?.let { id ->
      throw IllegalArgumentException(
        "Named variable '${byId.getValue(id).first.name}' is also the expression an " +
          "expression-change action evaluates, so it cannot be replaced by a constant"
      )
    }
  val defined = mutableSetOf<Int>()
  val rewritten = buildList {
    operations.forEach { operation ->
      if (operation is RcNamedVariable && operation in targets) {
        // A variable with no defining operation of its own gets one where its name was declared.
        // Declarations come before or after the definition depending on the writer, so whether one
        // exists is only known once the whole list is seen — see the second pass below.
        add(operation)
        return@forEach
      }
      val target = operation.definedId()?.let(byId::get)
      if (target == null) {
        add(operation)
        return@forEach
      }
      val (variable, value) = target
      add(definition(variable, value))
      defined += variable.id
    }
  }
  return copy(
    operations =
      rewritten.mapNotNull { operation ->
        when {
          operation !is RcNamedVariable || operation !in targets -> operation
          operation.id in defined -> null
          else -> definition(operation, targets.getValue(operation))
        }
      }
  )
}

/**
 * This document with every named variable not in [bound] frozen at the value the document itself
 * gives it: the name declaration is removed, the defining operation is kept as it is.
 *
 * For a host that will only ever bind [bound]: everything else would have played at its document
 * default anyway, and now no longer advertises a binding it will not get. Names in [bound] match as
 * in [hardcodeNamedValues]. Hardcode explicit values first with [hardcodeNamedValues] to freeze an
 * unbound variable at something other than its default.
 */
public fun RcDocument.hardcodeUnboundNamedValues(bound: Set<String> = emptySet()): RcDocument {
  val keep = bound.mapNotNullTo(mutableSetOf()) { declaredName(it) }
  val rewritten = operations.filterNot { it is RcNamedVariable && it.name !in keep }
  return if (rewritten.size == operations.size) this else copy(operations = rewritten)
}

/**
 * The declared name a host's [key] binds, resolved as the player resolves it: a qualified key
 * (`USER:progress`) names itself, and an unqualified one names the `USER:` declaration when there
 * is one, else the bare declaration. One key never names two variables.
 */
private fun RcDocument.declaredName(key: String): String? {
  val names = namedVariables.mapTo(mutableSetOf()) { it.name }
  return when {
    ':' in key -> key.takeIf { it in names }
    "$USER_PREFIX:$key" in names -> "$USER_PREFIX:$key"
    else -> key.takeIf { it in names }
  }
}

/**
 * The id this operation gives a single scalar value — the kinds a host's named value can stand in
 * for — or null. An operation writing several ids at once cannot be swapped for one constant.
 */
private fun RcOperation.definedId(): Int? {
  if (this is RcNamedVariable || definedIds()?.size != 1) return null
  return when (this) {
    is RcBitmapData,
    is RcFontData,
    is RcIdList,
    is RcFloatList,
    is RcIdMap,
    is RcPathData,
    is RcPathExpression,
    is RcMatrixConstant,
    is RcMatrixExpression,
    is RcDynamicFloatList,
    is RcUpdateDynamicFloatList,
    is RcPathCreate,
    is RcPathAppend,
    is RcPathCombine,
    is RcPathTween,
    is RcSoundExpression -> null
    else -> definedIds()?.single()
  }
}

private fun definition(variable: RcNamedVariable, value: RcHardcodedValue): RcOperation =
  when {
    variable.type == RcNamedVariable.STRING_TYPE && value is RcHardcodedValue.Text ->
      RcTextData(variable.id, value.value)
    variable.type == RcNamedVariable.FLOAT_TYPE && value is RcHardcodedValue.FloatValue ->
      RcFloatConstant(variable.id, RcFloatWord.literal(value.value))
    variable.type == RcNamedVariable.COLOR_TYPE && value is RcHardcodedValue.Color ->
      RcColorConstant(variable.id, value.argb)
    variable.type == RcNamedVariable.INT_TYPE && value is RcHardcodedValue.Integer ->
      RcIntegerConstant(variable.id, value.value)
    variable.type == RcNamedVariable.INT_TYPE && value is RcHardcodedValue.BooleanValue ->
      RcIntegerConstant(variable.id, if (value.value) 1 else 0)
    variable.type == RcNamedVariable.LONG_TYPE && value is RcHardcodedValue.LongValue ->
      RcLongConstant(variable.id, value.value)
    else ->
      throw IllegalArgumentException(
        "Named variable '${variable.name}' has AndroidX type ${variable.type}, incompatible with " +
          "${value::class.simpleName}"
      )
  }

/** The namespace AndroidX gives names a document's author declared, as the player resolves it. */
private const val USER_PREFIX = "USER"

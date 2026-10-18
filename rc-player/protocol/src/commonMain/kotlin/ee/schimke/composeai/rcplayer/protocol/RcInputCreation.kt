package ee.schimke.composeai.rcplayer.protocol

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/** Versioned control identities; all values/events use the declared TEXT_RETURN string channel. */
public enum class RcInputKind(public val config: String) {
  TEXT(RcInput.TEXT),
  CHOICE(RcInput.CHOICE),
  TOGGLE(RcInput.TOGGLE),
  NUMBER(RcInput.NUMBER),
  RANGE(RcInput.RANGE),
}

/** Shared submission validation. Raw numeric edits may be incomplete; only valid values submit. */
public fun rcInputValueIsValid(
  config: String,
  value: String,
  options: List<String> = emptyList(),
  minimum: Double? = null,
  maximum: Double? = null,
  step: Double? = null,
): Boolean {
  if (config == RcInput.TEXT) return true
  if (config == RcInput.CHOICE) return value in options
  if (config == RcInput.TOGGLE) return value == "true" || value == "false"
  if (config != RcInput.NUMBER && config != RcInput.RANGE) return false
  if (
    listOfNotNull(minimum, maximum, step).any { !it.isFinite() } ||
      (minimum != null && maximum != null && minimum > maximum) ||
      (step != null && step <= 0)
  )
    return false
  if (
    config == RcInput.RANGE &&
      (minimum == null ||
        maximum == null ||
        step == null ||
        minimum >= maximum ||
        (maximum - minimum) / step > 10000)
  )
    return false
  if (config == RcInput.RANGE) {
    val count = (maximum!! - minimum!!) / step!!
    if (abs(count - round(count)) > 1e-7 * max(1.0, abs(count))) return false
  }
  if (!Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?").matches(value)) return false
  val number = value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return false
  if (minimum != null && number < minimum || maximum != null && number > maximum) return false
  if (step != null) {
    val units = (number - (minimum ?: 0.0)) / step
    if (!units.isFinite() || abs(units - round(units)) > 1e-7 * max(1.0, abs(units))) return false
  }
  return true
}

/** Validates authoring metadata and initial value, before emitting a custom component. */
public fun RcInputField.requireValid(): Unit {
  require(key.isNotBlank() && label.isNotBlank()) { "An input needs a stable key" }
  require(!multiline || kind == RcInputKind.TEXT) { "Only text can be multiline" }
  require(options.distinct().size == options.size && options.all { it.isNotEmpty() && '\n' !in it })
  require(if (kind == RcInputKind.CHOICE) options.isNotEmpty() else options.isEmpty())
  require(
    kind == RcInputKind.NUMBER ||
      kind == RcInputKind.RANGE ||
      listOf(minimum, maximum, step).all { it == null }
  )
  require(listOfNotNull(minimum, maximum, step).all { it.isFinite() })
  require(minimum == null || maximum == null || minimum <= maximum)
  require(step == null || step > 0)
  require(submit.isEmpty() || submitLabel.isNotBlank())
  require(
    (value.isEmpty() && kind in setOf(RcInputKind.NUMBER, RcInputKind.CHOICE)) ||
      rcInputValueIsValid(kind.config, value, options, minimum, maximum, step)
  ) {
    "Invalid initial input value: $key"
  }
  if (kind == RcInputKind.RANGE)
    require(rcInputValueIsValid(kind.config, maximum.toString(), options, minimum, maximum, step)) {
      "Range maximum must align to step"
    }
}

/**
 * A composable wire fragment: text resources followed by LAYOUT_CUSTOM. Append dimension/modifier
 * operations and CONTAINER_END yourself. Reserve 10 consecutive text ids starting at [textBaseId].
 * IDs belong to the containing document; callers must allocate them without collisions.
 */
public fun rcInputComponent(
  field: RcInputField,
  componentId: Int,
  textBaseId: Int,
): List<RcOperation> {
  field.requireValid()
  require(componentId >= 0 && textBaseId > 0 && textBaseId <= Int.MAX_VALUE - 9)
  val strings =
    listOf(
      field.kind.config,
      field.value,
      field.key,
      field.label,
      field.options.joinToString("\n"),
      field.submit,
      field.minimum?.toString().orEmpty(),
      field.maximum?.toString().orEmpty(),
      field.step?.toString().orEmpty(),
      field.submitLabel,
    )
  return strings.mapIndexed { i, text -> RcTextData(textBaseId + i, text) } +
    RcCustomLayout(
      componentId,
      0,
      textBaseId,
      listOf(
        RcCustomProperty.string(RcInput.VALUE, textBaseId + 1),
        RcCustomProperty.textReturn(RcInput.VALUE_RETURN, textBaseId + 1),
        RcCustomProperty.string(RcInput.KEY, textBaseId + 2),
        RcCustomProperty.string(RcInput.LABEL, textBaseId + 3),
        RcCustomProperty.int(RcInput.MULTILINE, if (field.multiline) 1 else 0),
        RcCustomProperty.string(RcInput.OPTIONS, textBaseId + 4),
        RcCustomProperty.string(RcInput.SUBMIT, textBaseId + 5),
        RcCustomProperty.string(RcInput.MIN, textBaseId + 6),
        RcCustomProperty.string(RcInput.MAX, textBaseId + 7),
        RcCustomProperty.string(RcInput.STEP, textBaseId + 8),
        RcCustomProperty.int(RcInput.ENABLED, if (field.enabled) 1 else 0),
        RcCustomProperty.string(RcInput.SUBMIT_LABEL, textBaseId + 9),
      ),
    )
}

/** Discoverable typed authoring API, usable without a UI toolkit or Home Assistant dependency. */
public object RcInputs {
  public fun text(
    key: String,
    label: String,
    value: String = "",
    multiline: Boolean = false,
    submit: String = "",
  ): RcInputField =
    RcInputField(key, label, value, multiline = multiline, submit = submit).also {
      it.requireValid()
    }

  public fun choice(
    key: String,
    label: String,
    options: List<String>,
    value: String = "",
    submit: String = "",
  ): RcInputField =
    RcInputField(key, label, value, options = options, submit = submit, kind = RcInputKind.CHOICE)
      .also { it.requireValid() }

  public fun toggle(
    key: String,
    label: String,
    value: Boolean = false,
    submit: String = "",
  ): RcInputField =
    RcInputField(key, label, value.toString(), submit = submit, kind = RcInputKind.TOGGLE).also {
      it.requireValid()
    }

  public fun number(
    key: String,
    label: String,
    value: Double = 0.0,
    minimum: Double? = null,
    maximum: Double? = null,
    step: Double? = null,
    submit: String = "",
  ): RcInputField =
    RcInputField(
        key,
        label,
        value.toString(),
        submit = submit,
        kind = RcInputKind.NUMBER,
        minimum = minimum,
        maximum = maximum,
        step = step,
      )
      .also { it.requireValid() }

  public fun range(
    key: String,
    label: String,
    value: Double,
    minimum: Double = 0.0,
    maximum: Double = 100.0,
    step: Double = 1.0,
    submit: String = "",
  ): RcInputField =
    RcInputField(
        key,
        label,
        value.toString(),
        submit = submit,
        kind = RcInputKind.RANGE,
        minimum = minimum,
        maximum = maximum,
        step = step,
      )
      .also { it.requireValid() }
}

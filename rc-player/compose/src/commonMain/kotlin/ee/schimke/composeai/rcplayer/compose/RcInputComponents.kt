package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcCustomProperty
import ee.schimke.composeai.rcplayer.protocol.RcInput
import ee.schimke.composeai.rcplayer.protocol.rcInputValueIsValid
import ee.schimke.composeai.rcplayer.runtime.RcInputDrafts
import kotlin.math.round

/**
 * Explicit opt-in controls shared by Android (including Wear app), desktop, Wasm and CMP Apple.
 * Widgets and read-only hosts use [editable] = false. Merely decoding opcode 93 is insufficient:
 * advertise [RcInput.configs] only when this registry is installed, with the host's write policy.
 *
 * Changes first traverse the declared TEXT_RETURN channel, then reach host state via [onChange].
 * [onSubmit] receives the opaque configured action and a draft snapshot. The authenticated host
 * validates/dispatches it; the renderer never calls services. Retain [drafts] per card.
 */
public fun rcInputComponents(
  drafts: RcInputDrafts,
  editable: Boolean = false,
  onChange: (key: String, value: String) -> Unit = { _, _ -> },
  onSubmit: (action: String, values: Map<String, String>) -> Unit = { _, _ -> },
): RcCustomComponentRegistry {
  val activeFields = mutableMapOf<Any, String>()
  val enabledFields = mutableMapOf<Any, Boolean>()
  val validity = mutableMapOf<Any, Boolean>()
  val renderer: RcCustomContent = { component, modifier ->
    val inputKey = component.text(RcInput.KEY)
    val lease = remember { Any() }
    DisposableEffect(activeFields, inputKey) {
      activeFields[lease] = inputKey
      onDispose {
        activeFields.remove(lease)
        validity.remove(lease)
        enabledFields.remove(lease)
      }
    }
    val label = component.text(RcInput.LABEL)
    val documentValue = component.text(RcInput.VALUE)
    val fieldEnabled = component.integer(RcInput.ENABLED, 1) == 1
    SideEffect { enabledFields[lease] = fieldEnabled }
    val writable =
      editable &&
        fieldEnabled &&
        inputKey.isNotBlank() &&
        component.property(RcInput.VALUE_RETURN)?.dataType == RcCustomProperty.TEXT_RETURN
    val currentValue = drafts.value(inputKey, documentValue)
    SideEffect {
      if (writable && currentValue != documentValue)
        component.returnText(RcInput.VALUE_RETURN, currentValue)
    }
    val minimum = component.text(RcInput.MIN).toDoubleOrNull()
    val maximum = component.text(RcInput.MAX).toDoubleOrNull()
    val step = component.text(RcInput.STEP).toDoubleOrNull()
    val options = component.text(RcInput.OPTIONS).split('\n').filter { it.isNotEmpty() }
    val metadataValid =
      listOf(RcInput.MIN, RcInput.MAX, RcInput.STEP).all {
        component.text(it).isEmpty() || component.text(it).toDoubleOrNull()?.isFinite() == true
      }
    val valid =
      metadataValid &&
        rcInputValueIsValid(component.config, currentValue, options, minimum, maximum, step)
    SideEffect { validity[lease] = valid }
    var controlValue by remember(inputKey) { mutableStateOf(currentValue) }
    SideEffect { if (controlValue != currentValue) controlValue = currentValue }
    val change: (String) -> Unit = { value ->
      if (component.returnText(RcInput.VALUE_RETURN, value)) {
        drafts.update(inputKey, value)
        controlValue = value
        onChange(inputKey, value)
      }
    }
    val focus = LocalFocusManager.current
    val action = component.text(RcInput.SUBMIT)
    val submit = {
      if (
        writable &&
          action.isNotBlank() &&
          validity.all { (id, valid) -> enabledFields[id] != true || valid }
      ) {
        onSubmit(
          action,
          (drafts.snapshot() + (inputKey to drafts.value(inputKey, documentValue))).filterKeys {
            activeFields.any { (id, key) -> key == it && enabledFields[id] == true }
          },
        )
        focus.clearFocus()
      }
    }
    val plain = component.integer(RcInput.CHROME) == 1
    val inset = if (plain) 0.dp else 8.dp
    Column(modifier.padding(inset)) {
      if (!plain) BasicText(label)
      if (!writable) {
        BasicText(currentValue)
        BasicText(if (!fieldEnabled) "Unavailable" else "Open in app to edit")
      } else if (component.config == RcInput.TOGGLE) {
        Box(
          Modifier.semantics { contentDescription = label }
            .toggleable(
              controlValue == "true",
              role = Role.Switch,
              onValueChange = { change(it.toString()) },
            )
            .padding(inset)
        ) {
          BasicText(if (controlValue == "true") "On" else "Off")
        }
      } else if (
        component.config == RcInput.RANGE &&
          minimum != null &&
          maximum != null &&
          step != null &&
          rcInputValueIsValid(
            RcInput.RANGE,
            minimum.toString(),
            minimum = minimum,
            maximum = maximum,
            step = step,
          )
      ) {
        RcRangeInput(
          label,
          controlValue.toDoubleOrNull() ?: minimum,
          minimum,
          maximum,
          step,
          change,
        )
      } else if (component.config == RcInput.CHOICE) {
        val options =
          component.text(RcInput.OPTIONS).split('\n').filter { it.isNotEmpty() }.distinct()
        var expanded by remember(inputKey) { mutableStateOf(false) }
        var selected by remember(inputKey) { mutableStateOf(drafts.value(inputKey, documentValue)) }
        SideEffect { if (selected != currentValue) selected = currentValue }
        BasicText(
          selected.ifEmpty { "Choose…" },
          Modifier.semantics { contentDescription = label }
            .clickable { expanded = !expanded }
            .padding(inset),
        )
        if (expanded) {
          androidx.compose.ui.window.Popup(
            onDismissRequest = { expanded = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = true),
          ) {
            Column(
              Modifier.background(androidx.compose.ui.graphics.Color.White)
                .heightIn(max = 240.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
              options.forEach { option ->
                BasicText(
                  option,
                  Modifier.clickable {
                      if (component.returnText(RcInput.VALUE_RETURN, option)) {
                        drafts.update(inputKey, option)
                        selected = option
                        onChange(inputKey, option)
                        expanded = false
                      }
                    }
                    .padding(12.dp),
                )
              }
            }
          }
        }
      } else {
        var editing by
          remember(inputKey) {
            mutableStateOf(TextFieldValue(drafts.value(inputKey, documentValue)))
          }
        SideEffect { if (editing.text != currentValue) editing = TextFieldValue(currentValue) }
        val multiline =
          component.config == RcInput.TEXT && component.integer(RcInput.MULTILINE) == 1
        BasicTextField(
          value = editing,
          onValueChange = { next ->
            if (component.returnText(RcInput.VALUE_RETURN, next.text)) {
              editing = next
              drafts.update(inputKey, next.text)
              onChange(inputKey, next.text)
            }
          },
          singleLine = !multiline,
          keyboardOptions =
            KeyboardOptions(
              keyboardType =
                if (component.config == RcInput.NUMBER) KeyboardType.Decimal else KeyboardType.Text,
              imeAction =
                if (multiline) ImeAction.Default
                else if (action.isNotBlank()) ImeAction.Send else ImeAction.Next,
            ),
          keyboardActions =
            KeyboardActions(
              onSend = { submit() },
              onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) },
            ),
          modifier =
            Modifier.fillMaxWidth()
              .semantics { contentDescription = label }
              .padding(vertical = inset),
        )
      }
      if (writable && !valid)
        BasicText("Enter a valid value", Modifier.semantics { error("Enter a valid value") })
      if (writable && action.isNotBlank())
        BasicText(
          component.text(RcInput.SUBMIT_LABEL, "Send").ifEmpty { "Send" },
          Modifier.clickable(onClick = submit).padding(inset),
        )
    }
  }
  return RcCustomComponentRegistry(RcInput.configs.associateWith { renderer })
}

/** Foundation-only slider: pointer, arrow keys and accessibility set-progress share one grid. */
@androidx.compose.runtime.Composable
private fun RcRangeInput(
  label: String,
  value: Double,
  minimum: Double,
  maximum: Double,
  step: Double,
  change: (String) -> Unit,
) {
  val currentChange by rememberUpdatedState(change)
  fun update(candidate: Double) {
    currentChange(
      (minimum + round((candidate.coerceIn(minimum, maximum) - minimum) / step) * step)
        .coerceIn(minimum, maximum)
        .toString()
    )
  }
  val fraction = ((value - minimum) / (maximum - minimum)).coerceIn(0.0, 1.0).toFloat()
  Column {
    BasicText(value.toString())
    Canvas(
      Modifier.fillMaxWidth()
        .height(36.dp)
        .semantics {
          contentDescription = label
          progressBarRangeInfo =
            ProgressBarRangeInfo(
              ((value - minimum) / step).toFloat(),
              0f..((maximum - minimum) / step).toFloat(),
              ((maximum - minimum) / step).toInt() - 1,
            )
          stateDescription = value.toString()
          setProgress {
            update(minimum + it.toDouble() * step)
            true
          }
        }
        .onKeyEvent { event ->
          if (event.type != KeyEventType.KeyDown) false
          else
            when (event.key) {
              Key.DirectionLeft,
              Key.DirectionDown -> {
                update(value - step)
                true
              }
              Key.DirectionRight,
              Key.DirectionUp -> {
                update(value + step)
                true
              }
              else -> false
            }
        }
        .focusable()
        .pointerInput(minimum, maximum, step) {
          detectTapGestures {
            update(minimum + (it.x / size.width).coerceIn(0f, 1f) * (maximum - minimum))
          }
        }
        .pointerInput(minimum, maximum, step) {
          detectDragGestures { pointer, _ ->
            pointer.consume()
            update(
              minimum + (pointer.position.x / size.width).coerceIn(0f, 1f) * (maximum - minimum)
            )
          }
        }
    ) {
      drawLine(
        Color.Gray,
        Offset(0f, size.height / 2),
        Offset(size.width, size.height / 2),
        4.dp.toPx(),
      )
      drawCircle(Color(0xff1565c0), 8.dp.toPx(), Offset(size.width * fraction, size.height / 2))
    }
  }
}

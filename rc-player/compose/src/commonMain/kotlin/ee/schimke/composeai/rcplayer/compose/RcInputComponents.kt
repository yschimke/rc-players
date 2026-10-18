package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcCustomProperty
import ee.schimke.composeai.rcplayer.protocol.RcInput
import ee.schimke.composeai.rcplayer.runtime.RcInputDrafts

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
  val renderer: RcCustomContent = { component, modifier ->
    val inputKey = component.text(RcInput.KEY)
    val label = component.text(RcInput.LABEL)
    val documentValue = component.text(RcInput.VALUE)
    val writable =
      editable &&
        inputKey.isNotBlank() &&
        component.property(RcInput.VALUE_RETURN)?.dataType == RcCustomProperty.TEXT_RETURN
    val currentValue = drafts.value(inputKey, documentValue)
    SideEffect {
      if (writable && currentValue != documentValue)
        component.returnText(RcInput.VALUE_RETURN, currentValue)
    }
    val focus = LocalFocusManager.current
    val action = component.text(RcInput.SUBMIT)
    val submit = {
      if (writable && action.isNotBlank()) {
        onSubmit(action, drafts.snapshot() + (inputKey to drafts.value(inputKey, documentValue)))
        focus.clearFocus()
      }
    }
    Column(modifier.padding(8.dp)) {
      BasicText(label)
      if (!writable) {
        BasicText(currentValue)
        BasicText("Open in app to edit")
      } else if (component.config == RcInput.CHOICE) {
        val options =
          component.text(RcInput.OPTIONS).split('\n').filter { it.isNotEmpty() }.distinct()
        var expanded by remember(inputKey) { mutableStateOf(false) }
        var selected by remember(inputKey) { mutableStateOf(drafts.value(inputKey, documentValue)) }
        SideEffect { if (selected != currentValue) selected = currentValue }
        BasicText(
          selected.ifEmpty { "Choose…" },
          Modifier.clickable { expanded = !expanded }.padding(8.dp),
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
        val multiline = component.integer(RcInput.MULTILINE) == 1
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
              imeAction =
                if (multiline) ImeAction.Default
                else if (action.isNotBlank()) ImeAction.Send else ImeAction.Next
            ),
          keyboardActions =
            KeyboardActions(
              onSend = { submit() },
              onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) },
            ),
          modifier =
            Modifier.fillMaxWidth()
              .semantics { contentDescription = label }
              .padding(vertical = 8.dp),
        )
      }
      if (writable && action.isNotBlank())
        BasicText("Send", Modifier.clickable(onClick = submit).padding(8.dp))
    }
  }
  return RcCustomComponentRegistry(RcInput.TEXT to renderer, RcInput.CHOICE to renderer)
}

@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.creation.compose.capture.LocalRemoteComposeCreationState
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteCustomComponent
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import ee.schimke.composeai.rcplayer.protocol.*

/**
 * Published creation-side input contract. [value] is both VALUE and TEXT_RETURN; callbacks and
 * service permissions belong to the player host, not the authoring composition. The creation
 * profile must support LAYOUT_CUSTOM. Widget authors supply an open-app alternative instead.
 * [field] declares metadata/initial defaults; [value] may be a live remote string, including an
 * incomplete numeric edit. Layout size is supplied by [modifier].
 */
@ExperimentalRcCreationApi
@Composable
@RemoteComposable
public fun RemoteInput(
  field: RcInputField,
  value: RemoteString,
  modifier: RemoteModifier = RemoteModifier,
  fallback: @Composable @RemoteComposable () -> Unit = {
    RemoteText("${field.label} · Open in app to edit".rs)
  },
): Unit {
  field.requireValid()
  if (!canCreateRcInputs()) {
    fallback()
    return
  }
  RemoteCustomComponent(
    name = field.kind.config,
    modifier = modifier,
    properties = {
      property(RcInput.VALUE, value)
      bindReturn(RcInput.VALUE_RETURN, value)
      property(RcInput.KEY, field.key)
      property(RcInput.LABEL, field.label)
      property(RcInput.MULTILINE, if (field.multiline) 1 else 0)
      property(RcInput.OPTIONS, field.options.joinToString("\n"))
      property(RcInput.SUBMIT, field.submit)
      property(RcInput.MIN, field.minimum?.toString().orEmpty())
      property(RcInput.MAX, field.maximum?.toString().orEmpty())
      property(RcInput.STEP, field.step?.toString().orEmpty())
      property(RcInput.ENABLED, if (field.enabled) 1 else 0)
      property(RcInput.SUBMIT_LABEL, field.submitLabel)
    },
  )
}

/**
 * Writer operation support only; a player must separately advertise the config and write policy.
 */
@ExperimentalRcCreationApi
@Composable
public fun canCreateRcInputs(): Boolean {
  val operations = LocalRemoteComposeCreationState.current.supportedOperations
  return operations.isEmpty() || RcOpcodes.LAYOUT_CUSTOM in operations
}

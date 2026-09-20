package ee.schimke.composeai.remote.apple

import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.remote.creation.compose.action.valueChange
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteColumnScope
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.alpha
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.border
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.enabled
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.offset
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.role
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.stateDescription
import androidx.compose.remote.creation.compose.shapes.RemoteCircleShape
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.MutableRemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role

public enum class RemoteAppleButtonStyle {
  Prominent,
  Bordered,
  Borderless,
  Destructive,
}

@Composable
public fun RemoteAppleButton(
  action: Action,
  title: RemoteString,
  modifier: RemoteModifier = RemoteModifier,
  style: RemoteAppleButtonStyle = RemoteAppleButtonStyle.Bordered,
  enabled: Boolean = true,
) {
  val colors = RemoteAppleTheme.colors
  val typography = RemoteAppleTheme.typography
  val prominent = style == RemoteAppleButtonStyle.Prominent
  val foreground =
    when (style) {
      RemoteAppleButtonStyle.Prominent -> colors.secondaryBackground
      RemoteAppleButtonStyle.Destructive -> colors.destructive
      else -> colors.accent
    }
  val shape = RemoteRoundedCornerShape(10.rdp)
  var buttonModifier =
    modifier
      .height(44.rdp)
      .clip(shape)
      .alpha(if (enabled) 1f.rf else 0.4f.rf)
      .semantics {
        contentDescription = title
        role = Role.Button
        this.enabled = enabled
      }
      .clickable(action, enabled, Role.Button)

  buttonModifier =
    when {
      prominent -> buttonModifier.background(colors.accent).padding(16.rdp, 10.rdp)
      style == RemoteAppleButtonStyle.Bordered || style == RemoteAppleButtonStyle.Destructive ->
        buttonModifier.border(1.rdp, foreground, shape).padding(16.rdp, 10.rdp)
      else -> buttonModifier.padding(8.rdp, 10.rdp)
    }

  RemoteBox(modifier = buttonModifier, contentAlignment = RemoteAlignment.Center) {
    RemoteText(
      text = title,
      color = foreground,
      style = typography.labelLarge,
    )
  }
}

@Composable
public fun RemoteAppleToggle(
  title: RemoteString,
  isOn: MutableRemoteBoolean,
  modifier: RemoteModifier = RemoteModifier,
  enabled: Boolean = true,
) {
  val colors = RemoteAppleTheme.colors
  val typography = RemoteAppleTheme.typography
  val action = valueChange(isOn, isOn.not())
  RemoteRow(
    modifier =
      modifier
        .fillMaxWidth()
        .height(52.rdp)
        .alpha(if (enabled) 1f.rf else 0.4f.rf)
        .clickable(action, enabled, Role.Switch)
        .semantics {
          contentDescription = title
          role = Role.Switch
          stateDescription = isOn.select("On".rs, "Off".rs)
          this.enabled = enabled
        },
    verticalAlignment = RemoteAlignment.CenterVertically,
  ) {
    RemoteText(
      text = title,
      modifier = RemoteModifier.weight(1f),
      color = colors.label,
      style = typography.bodyLarge,
    )
    RemoteBox(
      modifier =
        RemoteModifier.size(51.rdp, 31.rdp)
          .clip(RemoteRoundedCornerShape(16.rdp))
          .background(isOn.select(colors.toggleOn, colors.toggleOff)),
      contentAlignment = RemoteAlignment.CenterStart,
    ) {
      RemoteBox(
        modifier =
          RemoteModifier.size(27.rdp)
            .offset(isOn.select(22.rdp, 2.rdp), 0.rdp)
            .clip(RemoteCircleShape)
            .background(colors.secondaryBackground)
      )
    }
  }
}

@Composable
public fun RemoteAppleProgressView(
  progress: RemoteFloat,
  modifier: RemoteModifier = RemoteModifier,
  label: RemoteString? = null,
) {
  val colors = RemoteAppleTheme.colors
  val typography = RemoteAppleTheme.typography
  RemoteColumn(modifier = modifier, verticalArrangement = RemoteArrangement.spacedBy(8.rdp)) {
    if (label != null) {
      RemoteText(
        text = label,
        color = colors.secondaryLabel,
        style = typography.bodySmall,
      )
    }
    RemoteBox(
      modifier =
        RemoteModifier.fillMaxWidth()
          .height(4.rdp)
          .clip(RemoteRoundedCornerShape(2.rdp))
          .background(colors.toggleOff),
      contentAlignment = RemoteAlignment.CenterStart,
    ) {
      RemoteBox(
        modifier = RemoteModifier.fillMaxWidth(progress).height(4.rdp).background(colors.accent)
      )
    }
  }
}

@Composable
public fun RemoteAppleLabel(
  title: RemoteString,
  modifier: RemoteModifier = RemoteModifier,
  detail: RemoteString? = null,
) {
  val colors = RemoteAppleTheme.colors
  val typography = RemoteAppleTheme.typography
  RemoteRow(
    modifier = modifier.fillMaxWidth().height(44.rdp),
    verticalAlignment = RemoteAlignment.CenterVertically,
  ) {
    RemoteText(
      text = title,
      modifier = RemoteModifier.weight(1f),
      color = colors.label,
      style = typography.bodyLarge,
    )
    if (detail != null) {
      RemoteText(
        text = detail,
        color = colors.secondaryLabel,
        style = typography.bodyLarge,
      )
    }
  }
}

@Composable
public fun RemoteAppleSection(
  title: RemoteString,
  modifier: RemoteModifier = RemoteModifier,
  content: @Composable RemoteColumnScope.() -> Unit,
) {
  val colors = RemoteAppleTheme.colors
  val typography = RemoteAppleTheme.typography
  RemoteColumn(modifier = modifier, verticalArrangement = RemoteArrangement.spacedBy(7.rdp)) {
    RemoteText(
      text = title,
      modifier = RemoteModifier.padding(16.rdp, 0.rdp),
      color = colors.secondaryLabel,
      style = typography.bodySmall,
    )
    RemoteColumn(
      modifier =
        RemoteModifier.fillMaxWidth()
          .clip(RemoteRoundedCornerShape(10.rdp))
          .background(colors.secondaryBackground)
          .padding(16.rdp, 6.rdp),
      content = content,
    )
  }
}

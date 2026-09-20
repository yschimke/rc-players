package ee.schimke.composeai.remote.apple

import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.remote.creation.compose.action.valueChange
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.role
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.stateDescription
import androidx.compose.remote.creation.compose.shapes.RemoteCircleShape
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.MutableRemoteInt
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.clamp
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.ri
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role

@Composable
public fun RemoteAppleSegmentedPicker(
  options: List<RemoteString>,
  selection: MutableRemoteInt,
  modifier: RemoteModifier = RemoteModifier,
) {
  require(options.isNotEmpty()) { "RemoteAppleSegmentedPicker requires at least one option" }
  val colors = RemoteAppleTheme.colors
  val typography = RemoteAppleTheme.typography
  RemoteRow(
    modifier =
      modifier
        .fillMaxWidth()
        .height(36.rdp)
        .clip(RemoteRoundedCornerShape(9.rdp))
        .background(colors.toggleOff)
        .padding(2.rdp),
    verticalAlignment = RemoteAlignment.CenterVertically,
  ) {
    options.forEachIndexed { index, label ->
      val selected = selection.isEqualTo(index.ri)
      RemoteBox(
        modifier =
          RemoteModifier.weight(1f)
            .height(32.rdp)
            .clip(RemoteRoundedCornerShape(7.rdp))
            .background(selected.select(colors.secondaryBackground, colors.toggleOff))
            .clickable(valueChange(selection, index.ri), true, Role.Tab)
            .semantics {
              contentDescription = label
              role = Role.Tab
              stateDescription = selected.select("Selected".rs, "Not selected".rs)
            },
        contentAlignment = RemoteAlignment.Center,
      ) {
        RemoteText(
          text = label,
          color = selected.select(colors.label, colors.secondaryLabel),
          style = typography.labelMedium,
        )
      }
    }
  }
}

@Composable
public fun RemoteAppleStepper(
  title: RemoteString,
  value: MutableRemoteInt,
  range: IntRange,
  modifier: RemoteModifier = RemoteModifier,
) {
  require(!range.isEmpty()) { "RemoteAppleStepper range must not be empty" }
  val colors = RemoteAppleTheme.colors
  val typography = RemoteAppleTheme.typography
  val canDecrement = value.isGreaterThan(range.first.ri)
  val canIncrement = value.isLessThan(range.last.ri)
  RemoteRow(
    modifier =
      modifier.fillMaxWidth().height(52.rdp).semantics {
        contentDescription = title
        stateDescription = value.toRemoteString()
      },
    verticalAlignment = RemoteAlignment.CenterVertically,
  ) {
    RemoteText(
      text = title,
      modifier = RemoteModifier.weight(1f),
      color = colors.label,
      style = typography.bodyLarge,
    )
    RemoteText(
      text = value.toRemoteString(),
      modifier = RemoteModifier.padding(8.rdp, 0.rdp),
      color = colors.secondaryLabel,
      style = typography.bodyLarge,
    )
    StepperButton(
      symbol = "−".rs,
      action = valueChange(value, clamp(value - 1, range.first.ri, range.last.ri)),
      enabled = canDecrement,
    )
    StepperButton(
      symbol = "+".rs,
      action = valueChange(value, clamp(value + 1, range.first.ri, range.last.ri)),
      enabled = canIncrement,
    )
  }
}

@Composable
private fun StepperButton(symbol: RemoteString, action: Action, enabled: RemoteBoolean) {
  val colors = RemoteAppleTheme.colors
  val typography = RemoteAppleTheme.typography
  RemoteBox(
    modifier =
      RemoteModifier.size(32.rdp)
        .clip(RemoteCircleShape)
        .background(colors.toggleOff)
        .clickable(action, true, Role.Button)
        .semantics {
          contentDescription = symbol
          role = Role.Button
          stateDescription = enabled.select("Enabled".rs, "Disabled".rs)
        },
    contentAlignment = RemoteAlignment.Center,
  ) {
    RemoteText(
      text = symbol,
      color = enabled.select(colors.accent, colors.secondaryLabel),
      style = typography.titleLarge,
    )
  }
}

@Composable
public fun RemoteAppleBadge(
  text: RemoteString,
  modifier: RemoteModifier = RemoteModifier,
) {
  val colors = RemoteAppleTheme.colors
  val typography = RemoteAppleTheme.typography
  RemoteBox(
    modifier =
      modifier
        .height(24.rdp)
        .clip(RemoteRoundedCornerShape(12.rdp))
        .background(colors.accent)
        .padding(9.rdp, 2.rdp),
    contentAlignment = RemoteAlignment.Center,
  ) {
    RemoteText(
      text = text,
      color = colors.secondaryBackground,
      style = typography.labelSmall,
    )
  }
}

@Composable
public fun RemoteAppleBadgeRow(
  title: RemoteString,
  badge: RemoteString,
  modifier: RemoteModifier = RemoteModifier,
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
    RemoteAppleBadge(badge)
  }
}

@Composable
public fun RemoteAppleDisclosureRow(
  title: RemoteString,
  action: Action,
  modifier: RemoteModifier = RemoteModifier,
  detail: RemoteString? = null,
) {
  val colors = RemoteAppleTheme.colors
  val typography = RemoteAppleTheme.typography
  RemoteRow(
    modifier =
      modifier.fillMaxWidth().height(48.rdp).clickable(action, true, Role.Button).semantics {
        contentDescription = title
        role = Role.Button
      },
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
        modifier = RemoteModifier.padding(8.rdp, 0.rdp),
        color = colors.secondaryLabel,
        style = typography.bodyMedium,
      )
    }
    RemoteText(
      text = "›".rs,
      color = colors.secondaryLabel,
      style = typography.headlineMedium,
    )
  }
}

@Composable
public fun RemoteAppleStatusRow(
  title: RemoteString,
  status: RemoteString,
  active: RemoteBoolean,
  modifier: RemoteModifier = RemoteModifier,
) {
  val colors = RemoteAppleTheme.colors
  val typography = RemoteAppleTheme.typography
  RemoteRow(
    modifier =
      modifier.fillMaxWidth().height(44.rdp).semantics {
        contentDescription = title
        stateDescription = status
      },
    verticalAlignment = RemoteAlignment.CenterVertically,
  ) {
    RemoteBox(
      modifier =
        RemoteModifier.size(10.rdp)
          .clip(RemoteCircleShape)
          .background(active.select(colors.toggleOn, colors.secondaryLabel))
    )
    RemoteText(
      text = title,
      modifier = RemoteModifier.weight(1f).padding(10.rdp, 0.rdp),
      color = colors.label,
      style = typography.bodyLarge,
    )
    RemoteText(
      text = status,
      color = colors.secondaryLabel,
      style = typography.bodyMedium,
    )
  }
}

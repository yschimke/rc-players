package ee.schimke.composeai.rcplayer.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ee.schimke.composeai.rcplayer.compose.RcCustomContent

/**
 * An editable text custom component: the other direction a `Custom` component runs in.
 *
 * [RcSpannableString][ee.schimke.composeai.rcplayer.compose.RcSpannableString] reads document state
 * and draws it. This one also writes: every keystroke goes back through the component's
 * `TEXT_RETURN` channel into the document's text table, so anything else in the document reading
 * that id — the label under the field in [RcDemoDocuments.editableText] — redraws with it. That
 * round trip is the whole demo; a host that only mirrors the value locally would look identical
 * until something else in the document depended on it.
 *
 * Unlike the spannable string, this is not an upstream contract and does not ship in the player:
 * the config name and property ids are this demo's own. It is what a consumer writes.
 */
public object RcEditableText {
  /** The config name [RcDemoDocuments.editableText] authors. */
  public const val CONFIG: String = "demo:EditableText"

  /** The current value (`STRING_PROP`). */
  public const val PROP_TEXT: Int = 1

  /** Where edits are written back to (`TEXT_RETURN`). */
  public const val PROP_TEXT_RETURN: Int = 2

  /** Text colour — literal or a colour id, as in the spannable string. */
  public const val PROP_TEXT_COLOR: Int = 3

  /** The host content to register under [CONFIG]. */
  public val renderer: RcCustomContent = { component, modifier ->
    val documentText = component.text(PROP_TEXT)
    // The field's own state is seeded from the document and re-seeded whenever the document's value
    // changes underneath it (an action, a host override, another player). Between those it is the
    // field that leads: `returnText` writes each edit back, and reading the document value straight
    // back into the field would fight the caret on every keystroke.
    var editing by remember(documentText) { mutableStateOf(documentText) }
    val color = component.color(PROP_TEXT_COLOR, default = 0xff202124.toInt())
    BasicTextField(
      value = editing,
      onValueChange = {
        editing = it
        component.returnText(PROP_TEXT_RETURN, it)
      },
      textStyle = TextStyle(color = Color(color), fontSize = 15.sp),
      cursorBrush = SolidColor(Color(color)),
      modifier = modifier.background(Color.White).padding(horizontal = 8.dp, vertical = 10.dp),
    )
  }
}

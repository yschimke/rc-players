package ee.schimke.composeai.rcplayer.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcCustomComponentRegistry
import ee.schimke.composeai.rcplayer.compose.RcSpannableString
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The registry a host wanting both demo components would install. */
public val RcDemoComponents: RcCustomComponentRegistry =
  RcCustomComponentRegistry(
    RcSpannableString.CONFIG to RcSpannableString.renderer,
    RcEditableText.CONFIG to RcEditableText.renderer,
  )

/** A document paragraph with two live links, drawn by the player's `SupportSpannableString`. */
@Composable
public fun RcSpannableStringDemo(modifier: Modifier = Modifier) {
  RcComposePlayer(
    RcDemoDocuments.spannableString(),
    modifier = modifier,
    customComponents = RcDemoComponents,
  )
}

/** A document text field whose edits are written back into the document it came from. */
@Composable
public fun RcEditableTextDemo(modifier: Modifier = Modifier) {
  RcComposePlayer(
    RcDemoDocuments.editableText(),
    modifier = modifier,
    customComponents = RcDemoComponents,
  )
}

@Preview
@Composable
public fun RcSpannableStringDemoPreview() {
  RcSpannableStringDemo()
}

@Preview
@Composable
public fun RcEditableTextDemoPreview() {
  RcEditableTextDemo()
}

/** Both demos stacked, which is what `:rc-player-demos:run` and the desktop window show. */
@Preview
@Composable
public fun RcCustomComponentDemosPreview() {
  Column(
    modifier = Modifier.padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    BasicText("SupportSpannableString — document state, drawn by the host")
    RcSpannableStringDemo()
    BasicText("Editable text — host edits, written back to the document")
    RcEditableTextDemo()
  }
}

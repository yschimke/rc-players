package ee.schimke.composeai.remote.apple

import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.text.RemoteFontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
public data class RemoteAppleColors(
  val accent: RemoteColor,
  val destructive: RemoteColor,
  val label: RemoteColor,
  val secondaryLabel: RemoteColor,
  val background: RemoteColor,
  val secondaryBackground: RemoteColor,
  val separator: RemoteColor,
  val toggleOn: RemoteColor,
  val toggleOff: RemoteColor,
) {
  public companion object {
    public val Light: RemoteAppleColors =
      RemoteAppleColors(
        accent = Color(0xff007aff).rc,
        destructive = Color(0xffff3b30).rc,
        label = Color(0xff000000).rc,
        secondaryLabel = Color(0xff6c6c70).rc,
        background = Color(0xfff2f2f7).rc,
        secondaryBackground = Color(0xffffffff).rc,
        separator = Color(0xffc6c6c8).rc,
        toggleOn = Color(0xff34c759).rc,
        toggleOff = Color(0xffe9e9ea).rc,
      )
  }
}

private val LocalRemoteAppleColors = staticCompositionLocalOf { RemoteAppleColors.Light }

public object RemoteAppleTheme {
  public val colors: RemoteAppleColors
    @Composable get() = LocalRemoteAppleColors.current

  /** The Apple system face. `apple:` is resolved only by the iOS and macOS player hosts. */
  public val fontFamily: RemoteFontFamily = RemoteFontFamily.Named("apple:system")
}

@Composable
public fun RemoteAppleTheme(
  colors: RemoteAppleColors = RemoteAppleColors.Light,
  content: @Composable () -> Unit,
) {
  androidx.compose.runtime.CompositionLocalProvider(LocalRemoteAppleColors provides colors, content)
}

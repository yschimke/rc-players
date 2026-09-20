package ee.schimke.composeai.remote.apple

import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.text.RemoteFontFamily
import androidx.compose.remote.creation.compose.text.RemoteTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

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

@Immutable
public data class RemoteAppleTypography(
  public val bodyLarge: RemoteTextStyle,
  public val bodyMedium: RemoteTextStyle,
  public val bodySmall: RemoteTextStyle,
  public val labelLarge: RemoteTextStyle,
  public val labelMedium: RemoteTextStyle,
  public val labelSmall: RemoteTextStyle,
  public val titleLarge: RemoteTextStyle,
  public val headlineMedium: RemoteTextStyle,
)

/** Creates the Apple text scale with one family shared by every style. */
public fun RemoteAppleTypography(
  fontFamily: RemoteFontFamily = RemoteFontFamily.Named("apple:system")
): RemoteAppleTypography {
  val base = RemoteTextStyle(fontFamily = fontFamily)
  return RemoteAppleTypography(
    bodyLarge = base.copy(fontSize = 17.rsp),
    bodyMedium = base.copy(fontSize = 15.rsp),
    bodySmall = base.copy(fontSize = 13.rsp),
    labelLarge =
      base.copy(
        fontSize = 17.rsp,
        fontWeight = FontWeight.SemiBold,
      ),
    labelMedium =
      base.copy(
        fontSize = 13.rsp,
        fontWeight = FontWeight.Medium,
      ),
    labelSmall =
      base.copy(
        fontSize = 13.rsp,
        fontWeight = FontWeight.SemiBold,
      ),
    titleLarge =
      base.copy(
        fontSize = 20.rsp,
        fontWeight = FontWeight.Medium,
      ),
    headlineMedium = base.copy(fontSize = 24.rsp),
  )
}

private val LocalRemoteAppleTypography = staticCompositionLocalOf { RemoteAppleTypography() }

public object RemoteAppleTheme {
  public val colors: RemoteAppleColors
    @Composable get() = LocalRemoteAppleColors.current

  public val typography: RemoteAppleTypography
    @Composable get() = LocalRemoteAppleTypography.current
}

@Composable
public fun RemoteAppleTheme(
  colors: RemoteAppleColors = RemoteAppleColors.Light,
  typography: RemoteAppleTypography = RemoteAppleTypography(),
  content: @Composable () -> Unit,
) {
  androidx.compose.runtime.CompositionLocalProvider(
    LocalRemoteAppleColors provides colors,
    LocalRemoteAppleTypography provides typography,
    content = content,
  )
}

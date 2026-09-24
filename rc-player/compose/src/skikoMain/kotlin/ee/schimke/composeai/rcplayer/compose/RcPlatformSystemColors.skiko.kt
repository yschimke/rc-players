package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NoSystemColors: (name: String) -> Color? = { null }

/** No `android.R.color` palette off Android: every themed colour keeps its fallback. */
@Composable
internal actual fun rememberRcPlatformSystemColors(): (name: String) -> Color? = NoSystemColors

package ee.schimke.composeai.rcplayer.compose

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap

/**
 * A `systemColors` lookup that reads the device's `android.R.color` resources — what the embedded
 * player's `AndroidRemoteContext` does for the same `ColorTheme` indices.
 *
 * Names are the ones [ee.schimke.composeai.rcplayer.protocol.RcAndroidSystemColors] maps indices
 * to. A name the platform does not define — the `system_*` palette below API 31 — resolves to
 * `null`, so the document's recorded fallback stands. It is `RcComposePlayer`'s default on Android;
 * call it to combine the platform palette with overrides of your own.
 *
 * Keyed on the configuration as well as the context, so a theme or wallpaper-palette change that
 * arrives as a configuration change is picked up.
 */
@Composable
public fun rememberRcAndroidSystemColors(): (name: String) -> Color? {
  val context = LocalContext.current
  val configuration = LocalConfiguration.current
  return remember(context, configuration) { rcAndroidSystemColors(context) }
}

@Composable
internal actual fun rememberRcPlatformSystemColors(): (name: String) -> Color? =
  rememberRcAndroidSystemColors()

/** Absent from the platform; cached so a name is looked up once, not per frame. */
private val MISSING = Color.Unspecified

internal fun rcAndroidSystemColors(context: Context): (name: String) -> Color? {
  val resources = context.resources
  val theme = context.theme
  val cache = ConcurrentHashMap<String, Color>()
  return { name ->
    cache
      .getOrPut(name) {
        // By name, because the names are the wire contract and the ids are not API: the `system_*`
        // ids only exist on the SDKs that define them, and a document is read on every SDK.
        @SuppressLint("DiscouragedApi")
        val id = resources.getIdentifier(name, "color", "android")
        if (id == 0) MISSING else Color(resources.getColor(id, theme))
      }
      .takeIf { it != MISSING }
  }
}

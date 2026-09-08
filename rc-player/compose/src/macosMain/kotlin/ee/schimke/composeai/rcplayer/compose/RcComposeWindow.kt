package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcOperationProfiles
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent

/**
 * Open an AppKit window containing the Remote Compose player.
 *
 * Swift calls this through `RcComposeWindowKt.RcComposeWindow(...)`. All arguments are required at
 * that boundary because Objective-C does not export Kotlin default arguments.
 */
public fun RcComposeWindow(
  bytes: ByteArray,
  title: String = "Remote Compose",
  width: Float = 800f,
  height: Float = 600f,
  theme: RcPlayerTheme = RcPlayerTheme.System,
  onEvent: (RcPlayerEvent) -> Unit = {},
  typefaces: RcTypefaceLoader = RcTypefaceLoader.Default,
  onError: (String) -> Unit = {},
  lenient: Boolean = false,
): Unit {
  val document = runCatching {
    RcDocumentCodec.decode(bytes).also {
      it
        .composeSupportReport(
          RcOperationProfiles.CMP_MACOS_ALPHA16,
          availableFontFamilies = typefaces.families,
        )
        .requireRenderable(lenient)
    }
  }
    .getOrElse {
      onError(it.message ?: "Remote Compose document failed to load")
      return
    }

  Window(title = title, size = DpSize(width.dp, height.dp)) {
    RcComposePlayer(
      document,
      Modifier.fillMaxSize(),
      theme,
      onEvent = onEvent,
      typefaces = typefaces,
    )
  }
}

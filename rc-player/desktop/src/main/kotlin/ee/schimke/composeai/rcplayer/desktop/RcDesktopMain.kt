package ee.schimke.composeai.rcplayer.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.system.exitProcess

private const val USAGE =
  """usage: remote-compose-player [--offline] [file.rc]
       remote-compose-player --render-png [--offline] <in.rc> <out.png> [width height]
       remote-compose-player --version"""

/**
 * The Linux desktop viewer: the CMP player in a window, opening local `.rc` files.
 *
 * `--render-png` draws one frame headless and exits, which is how the release job smoke-tests the
 * packaged app on a runner without a display.
 */
public fun main(args: Array<String>) {
  // `--offline` keeps the viewer off the network: `google:` families draw in the default face.
  val offline = "--offline" in args
  val rest = args.filter { it != "--offline" }
  when (rest.firstOrNull()) {
    "--help",
    "-h" -> println(USAGE)
    "--version" -> println(RcDesktopVersion.value)
    "--render-png" -> exitProcess(renderPngCommand(rest.drop(1), downloadFonts = !offline))
    else -> {
      if (rest.size > 1) {
        System.err.println(USAGE)
        exitProcess(2)
      }
      openWindow(rest.firstOrNull()?.let(::File), downloadFonts = !offline)
    }
  }
}

private fun renderPngCommand(args: List<String>, downloadFonts: Boolean): Int {
  if (args.size != 2 && args.size != 4) {
    System.err.println(USAGE)
    return 2
  }
  val load = loadRcViewerDocument(File(args[0]), downloadFonts)
  if (load !is RcViewerLoad.Loaded) {
    System.err.println((load as RcViewerLoad.Failed).message)
    return 1
  }
  val width = args.getOrNull(2)?.toIntOrNull() ?: load.widthPx
  val height = args.getOrNull(3)?.toIntOrNull() ?: load.heightPx
  val png = renderRcPng(load.document, width, height, load.typefaces)
  File(args[1]).apply {
    parentFile?.mkdirs()
    writeBytes(png)
  }
  println("${args[1]}: ${width}x${height}")
  return 0
}

private fun openWindow(initial: File?, downloadFonts: Boolean) {
  val state =
    RcViewerState(initial, initial?.let { loadRcViewerDocument(it, downloadFonts) }).also {
      it.downloadFonts = downloadFonts
    }
  val initialSize =
    (state.load as? RcViewerLoad.Loaded)?.let { load ->
      val density = load.document.header.density.takeIf { it > 0f } ?: 1f
      DpSize(
        (load.widthPx / density).coerceIn(640f, 1600f).dp,
        // The toolbar and status bar sit above and below the document.
        (load.heightPx / density + 72f).coerceIn(480f, 1200f).dp,
      )
    } ?: DpSize(800.dp, 600.dp)

  application {
    val windowState = rememberWindowState(size = initialSize)
    Window(
      onCloseRequest = ::exitApplication,
      state = windowState,
      title = state.file?.let { "${it.name} — Remote Compose Player" } ?: "Remote Compose Player",
    ) {
      val openDialog = {
        chooseRcFile(window, state.file?.parentFile)?.let(state::open)
        Unit
      }
      MenuBar {
        Menu("File", mnemonic = 'F') {
          Item("Open…", shortcut = KeyShortcut(Key.O, ctrl = true), onClick = openDialog)
          Item(
            "Reload",
            enabled = state.file != null,
            shortcut = KeyShortcut(Key.R, ctrl = true),
            onClick = state::reload,
          )
          Separator()
          Item("Quit", shortcut = KeyShortcut(Key.Q, ctrl = true), onClick = ::exitApplication)
        }
        Menu("View", mnemonic = 'V') {
          Item("Theme: ${state.theme.name}", onClick = state::cycleTheme)
          Item(state.sizing.label, onClick = state::cycleSizing)
          CheckboxItem(
            "Download Google Fonts",
            checked = state.downloadFonts,
            onCheckedChange = { state.toggleDownloadFonts() },
          )
        }
      }
      RcViewer(state, onOpenRequest = openDialog)
    }
  }
}

private fun chooseRcFile(owner: Frame, directory: File?): File? {
  val dialog = FileDialog(owner, "Open a Remote Compose document", FileDialog.LOAD)
  directory?.let { dialog.directory = it.path }
  dialog.setFilenameFilter { _, name -> name.endsWith(".rc", ignoreCase = true) }
  dialog.isVisible = true
  val name = dialog.file ?: return null
  return File(dialog.directory, name)
}

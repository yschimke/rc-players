package ee.schimke.composeai.rcplayer.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcPlayerTheme
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the document is laid out in the window. */
internal enum class RcViewerSizing(val label: String) {
  /** The player fills the window, so resizing the window resizes the document. */
  Fill("Fill window"),

  /** The player gets the size the document's header declares, centred. */
  Document("Document size"),
}

/**
 * Everything the viewer window shows; owned by `main` so the menu bar can drive it too.
 *
 * [initialLoad] is read before the window opens, so the window can be sized to the document. Every
 * later (re)load runs off the UI thread in [RcViewer], because fetching a document's Google Fonts
 * goes to the network.
 */
internal class RcViewerState(initialFile: File? = null, initialLoad: RcViewerLoad? = null) {
  var file: File? by mutableStateOf(initialFile)
    private set

  var load: RcViewerLoad? by mutableStateOf(initialLoad)
    internal set

  /** True while a requested (re)load is still reading the file or fetching fonts. */
  var loading: Boolean by mutableStateOf(false)
    internal set

  /** Bumped on every (re)load request; also restarts the player's clock and named values. */
  var generation: Int by mutableStateOf(0)
    private set

  var theme: RcPlayerTheme by mutableStateOf(RcPlayerTheme.System)
  var sizing: RcViewerSizing by mutableStateOf(RcViewerSizing.Fill)

  /** Fetch the `google:` families a document names; off, they draw in the default face. */
  var downloadFonts: Boolean by mutableStateOf(true)

  fun open(file: File) {
    this.file = file
    reload()
  }

  fun reload() {
    if (file == null) return
    generation++
  }

  fun toggleDownloadFonts() {
    downloadFonts = !downloadFonts
    reload()
  }

  fun cycleTheme() {
    theme = RcPlayerTheme.entries[(theme.ordinal + 1) % RcPlayerTheme.entries.size]
  }

  fun cycleSizing() {
    sizing = RcViewerSizing.entries[(sizing.ordinal + 1) % RcViewerSizing.entries.size]
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun RcViewer(state: RcViewerState, onOpenRequest: () -> Unit) {
  var dragging by remember { mutableStateOf(false) }
  val dropTarget = remember {
    object : DragAndDropTarget {
      override fun onEntered(event: DragAndDropEvent) {
        dragging = true
      }

      override fun onExited(event: DragAndDropEvent) {
        dragging = false
      }

      override fun onEnded(event: DragAndDropEvent) {
        dragging = false
      }

      override fun onDrop(event: DragAndDropEvent): Boolean {
        dragging = false
        val file = event.droppedFiles().firstOrNull() ?: return false
        state.open(file)
        return true
      }
    }
  }

  LaunchedEffect(state.generation) {
    if (state.generation == 0) return@LaunchedEffect
    val file = state.file ?: return@LaunchedEffect
    val downloadFonts = state.downloadFonts
    state.loading = true
    state.load = withContext(Dispatchers.IO) { loadRcViewerDocument(file, downloadFonts) }
    state.loading = false
  }

  Column(
    Modifier.fillMaxSize()
      .background(ViewerColors.window)
      .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget)
  ) {
    Toolbar(state, onOpenRequest)
    Box(
      Modifier.fillMaxWidth()
        .weight(1f)
        .then(if (dragging) Modifier.border(2.dp, ViewerColors.accent) else Modifier),
      contentAlignment = Alignment.Center,
    ) {
      when (val load = state.load) {
        null ->
          Message("Open a .rc file (Ctrl+O), drop one here, or pass a path on the command line.")
        is RcViewerLoad.Failed -> Message(load.message, error = true)
        is RcViewerLoad.Loaded -> Document(load, state)
      }
    }
    StatusBar(state)
  }
}

@Composable
private fun Document(load: RcViewerLoad.Loaded, state: RcViewerState) {
  val sizeModifier =
    when (state.sizing) {
      RcViewerSizing.Fill -> Modifier.fillMaxSize()
      RcViewerSizing.Document -> {
        val density = load.document.header.density.takeIf { it > 0f } ?: 1f
        Modifier.size((load.widthPx / density).dp, (load.heightPx / density).dp)
          .border(1.dp, ViewerColors.divider)
      }
    }
  // Keyed on the generation so a reload is a fresh player: its clock, named values and animation
  // state restart rather than carrying over from the previous bytes.
  key(state.generation, load) {
    RcComposePlayer(
      load.document,
      sizeModifier,
      theme = state.theme,
      typefaces = load.typefaces,
    )
  }
}

@Composable
private fun Toolbar(state: RcViewerState, onOpenRequest: () -> Unit) {
  Row(
    Modifier.fillMaxWidth()
      .background(ViewerColors.bar)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ToolbarButton("Open…", onClick = onOpenRequest)
    ToolbarButton("Reload", enabled = state.file != null) { state.reload() }
    ToolbarButton("Theme: ${state.theme.name}") { state.cycleTheme() }
    ToolbarButton(state.sizing.label) { state.cycleSizing() }
  }
}

@Composable
private fun StatusBar(state: RcViewerState) {
  val load = state.load
  val text =
    when {
      state.loading -> "Loading ${state.file?.name}…"
      load is RcViewerLoad.Failed -> "${load.name} — failed to open"
      load !is RcViewerLoad.Loaded -> "No document"
      else ->
        buildString {
          append(load.name)
          append(" — ${load.widthPx}×${load.heightPx}px")
          append(", ${load.document.operations.size} operations")
          append(", ${load.bytes.size} bytes")
          if (load.skipped.isNotEmpty()) {
            append(" — ${load.skipped.size} skipped: ")
            append(load.skipped.map { it.operation }.distinct().joinToString())
          }
        }
    }
  BasicText(
    text,
    Modifier.fillMaxWidth()
      .background(ViewerColors.bar)
      .padding(horizontal = 8.dp, vertical = 4.dp),
    style = TextStyle(color = ViewerColors.secondaryText, fontSize = 12.sp),
    maxLines = 1,
  )
}

@Composable
private fun ToolbarButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
  BasicText(
    label,
    Modifier.background(ViewerColors.button, RoundedCornerShape(4.dp))
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 4.dp),
    style =
      TextStyle(
        color = if (enabled) ViewerColors.text else ViewerColors.secondaryText,
        fontSize = 13.sp,
      ),
    softWrap = false,
    maxLines = 1,
  )
}

@Composable
private fun Message(text: String, error: Boolean = false) {
  BasicText(
    text,
    Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
    style =
      TextStyle(
        color = if (error) ViewerColors.error else ViewerColors.secondaryText,
        fontSize = 14.sp,
      ),
  )
}

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.droppedFiles(): List<File> {
  val transferable = awtTransferable
  if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
  return (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
    .orEmpty()
    .filterIsInstance<File>()
}

/** Neutral chrome, so the colours on screen that are not grey are the document's. */
private object ViewerColors {
  val window = Color(0xFFE8E8E8)
  val bar = Color(0xFFF6F6F6)
  val button = Color(0xFFE0E0E0)
  val divider = Color(0xFFB0B0B0)
  val text = Color(0xFF1F1F1F)
  val secondaryText = Color(0xFF5F5F5F)
  val accent = Color(0xFF3B6FD8)
  val error = Color(0xFFB3261E)
}

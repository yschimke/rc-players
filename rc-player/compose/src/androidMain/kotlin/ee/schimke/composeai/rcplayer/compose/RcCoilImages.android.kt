package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Image
import coil3.toBitmap

internal actual fun Image.toRcImageBitmap(): ImageBitmap = toBitmap().asImageBitmap()

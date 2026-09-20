package ee.schimke.composeai.remote.apple

import android.content.Context
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedAction
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerExpressionChangeAction
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteAppleComponentGalleryTest {
  @Test
  fun galleryCapturesAsPortableRemoteComposeDocument() = runBlocking {
    val context: Context = RuntimeEnvironment.getApplication()
    val bytes =
      captureSingleRemoteDocument(context = context) { RemoteAppleComponentGallery() }.bytes
    System.getProperty("remote.apple.fixtureDir")?.let { fixtureDir ->
      File(fixtureDir).mkdirs()
      File(fixtureDir, "gallery.rc").writeBytes(bytes)
      File(fixtureDir, "raw-primitives.rc")
        .writeBytes(captureSingleRemoteDocument(context = context) { RawPrimitiveGallery() }.bytes)
    }
    val document = RcDocumentCodec.decode(bytes)
    val strings = document.operations.filterIsInstance<RcTextData>().map { it.text }.toSet()

    assertTrue(bytes.isNotEmpty())
    assertTrue("Wi-Fi" in strings)
    assertTrue("apple:system" in strings)
    assertTrue("Play" in strings)
    assertTrue("Remove Download" in strings)
    assertTrue(document.operations.any { it is RcValueIntegerExpressionChangeAction })
    assertTrue(document.operations.any { it is RcHostNamedAction })
  }

  @Test
  fun controlsGalleryCapturesInteractiveComponents() = runBlocking {
    val context: Context = RuntimeEnvironment.getApplication()
    val bytes =
      captureSingleRemoteDocument(context = context) { RemoteAppleControlsGallery() }.bytes
    System.getProperty("remote.apple.fixtureDir")?.let { fixtureDir ->
      File(fixtureDir).mkdirs()
      File(fixtureDir, "controls-gallery.rc").writeBytes(bytes)
    }
    val document = RcDocumentCodec.decode(bytes)
    val strings = document.operations.filterIsInstance<RcTextData>().map { it.text }.toSet()

    assertTrue("Daily" in strings)
    assertTrue("apple:system" in strings)
    assertTrue("Reminders" in strings)
    assertTrue("Connected" in strings)
    assertTrue("Privacy" in strings)
    assertTrue(document.operations.any { it is RcValueIntegerExpressionChangeAction })
    assertTrue(document.operations.any { it is RcHostNamedAction })
  }
}

@Composable
private fun RawPrimitiveGallery() {
  RemoteColumn(
    modifier = RemoteModifier.fillMaxSize().background(Color(0xfff2f2f7).rc).padding(20.rf),
    verticalArrangement = RemoteArrangement.spacedBy(18.rf),
  ) {
    RemoteText(text = "Connectivity".rs, fontSize = 13.rsp)
    RemoteText(text = "Wi-Fi: on".rs, fontSize = 17.rsp)
    RemoteText(text = "Network: Studio".rs, fontSize = 17.rsp)
    RemoteText(text = "Playback".rs, fontSize = 13.rsp)
    RemoteText(text = "Downloading: 64%".rs, fontSize = 17.rsp)
    RemoteText(text = "Play".rs, fontSize = 17.rsp)
    RemoteText(text = "Remove Download".rs, fontSize = 17.rsp)
  }
}

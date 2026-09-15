package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyle
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals

class RcDownloadableFontsTest {
  @Test
  fun findsReferencedGoogleFamiliesWithoutTreatingContentAsARequest() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(
          RcTextData(42, "google:Orbitron"),
          RcTextData(43, "Not a font: google:Unused"),
          RcTextStyle(
            listOf(RcTextStyleProperty.IntValue(1, 100), RcTextStyleProperty.IntValue(8, 42))
          ),
        ),
      )

    assertEquals(
      listOf("Orbitron"),
      rcDownloadableFontRequests(RcDocumentCodec.encode(document)).map { it.family },
    )
  }

  @Test
  fun buildsACaseInsensitiveByteBackedLoader() {
    val loader =
      rcDownloadedTypefaceLoader(
        listOf(RcDownloadedFont("Orbitron", "test-orbitron", byteArrayOf(1, 2, 3)))
      )

    assertEquals(setOf("orbitron"), loader.families)
  }

  @Test
  fun offlineLoaderAdvertisesFallbackFamiliesWithoutInventingATypeface() {
    val loader = rcDownloadableFontFallback(listOf("Orbitron"))

    assertEquals(setOf("orbitron"), loader.families)
    assertEquals(null, loader.typeface("orbitron"))
  }
}

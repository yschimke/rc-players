package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

/** Density-behavior coverage for padding and arrangement spacing. */
class RcCapturedPixelsDensityTest {

  /**
   * Historical alpha18 documents encoded DP-behavior padding as already-scaled pixels. Alpha19
   * corrected that writer behavior without a wire-version discriminator, so retain this fixture as
   * evidence of the old bytes rather than as the current playback contract.
   */
  @OptIn(ExperimentalEncodingApi::class)
  @Test
  fun alpha18FixtureEdgesCarryTheGenerationDensity() {
    val bytes =
      checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$APP_CARD_FIXTURE")) {
          "missing fixture /rc-fixtures/$APP_CARD_FIXTURE"
        }
        .use { Base64.decode(it.readBytes().decodeToString().trim()) }
    val document = RcDocumentCodec.decode(bytes)

    assertEquals(RcHeader.DENSITY_BEHAVIOR_DP, document.header.densityBehavior)
    assertEquals(2f, document.header.density)

    val edges =
      document.operations.filterIsInstance<RcPaddingModifier>().map {
        listOf(it.left, it.top, it.right, it.bottom).map(RcFloatWord::value)
      }
    assertEquals(listOf(listOf(24f, 24f, 24f, 24f)), edges)
  }

  /** Current DP-behavior documents scale their raw padding values at playback density. */
  @Test
  fun aDpDocumentScalesPaddingWithPlaybackDensity() {
    for (density in listOf(1f, 2f)) {
      val edge = (INSET * density).toInt()
      assertEquals(0, colorAt(edge - 1, 50, Density(density)), "outside at density $density")
      assertEquals(WHITE, colorAt(edge + 1, 50, Density(density)), "inside at density $density")
      assertEquals(WHITE, colorAt(50, edge + 1, Density(density)), "top at density $density")
      assertEquals(0, colorAt(50, edge - 1, Density(density)), "above at density $density")
    }
  }

  /** Arrangement spacing follows the same DP-behavior rule as padding. */
  @Test
  fun aDpDocumentScalesSpacedByWithPlaybackDensity() {
    for (density in listOf(1f, 2f)) {
      val gap = (INSET * density).toInt()
      val child = (SIZE - gap) / 2
      assertEquals(
        WHITE,
        colorAt(50, child - 1, Density(density), spacedColumn()),
        "the first weight ends at the gap at density $density",
      )
      assertEquals(
        0,
        colorAt(50, 50, Density(density), spacedColumn()),
        "the gap is empty at density $density",
      )
      assertEquals(
        WHITE,
        colorAt(50, child + gap, Density(density), spacedColumn()),
        "the second weight begins after a 20px gap at density $density",
      )
    }
  }

  /** Writes PR evidence when explicitly requested; ordinary test runs remain side-effect free. */
  @Test
  fun writeDpPaddingEvidence() {
    val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    val scene =
      ImageComposeScene(width = SIZE, height = SIZE, density = Density(2f)) {
        RcComposePlayer(paddedBoxes())
      }
    try {
      directory.resolve("dp-padding.png").writeBytes(scene.render().encodeToData()!!.bytes)
    } finally {
      scene.close()
    }
  }

  private fun colorAt(
    x: Int,
    y: Int,
    density: Density,
    document: RcDocument = paddedBoxes(),
  ): Int {
    val scene =
      ImageComposeScene(width = SIZE, height = SIZE, density = density) {
        RcComposePlayer(document)
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(SIZE, SIZE) }
      check(scene.render().readPixels(bitmap))
      return bitmap.getColor(x, y)
    } finally {
      scene.close()
    }
  }

  /** A filling box inset by 20px on every side, wrapped around a filling white box. */
  private fun paddedBoxes(): RcDocument =
    RcDocument(
      dpHeader(),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcBoxLayout(3, 30, 1, 4),
        fillWidth(),
        fillHeight(),
        RcPaddingModifier(
          RcFloatWord.literal(INSET),
          RcFloatWord.literal(INSET),
          RcFloatWord.literal(INSET),
          RcFloatWord.literal(INSET),
        ),
        RcLayoutContent(4),
        RcBoxLayout(5, 50, 1, 4),
        fillWidth(),
        fillHeight(),
        whiteBackground(),
        RcLayoutContent(6),
      ) + List(6) { RcNoArg(RcOpcodes.CONTAINER_END) },
    )

  /** A DP-behavior header at a generation density of 2.0 — what every capture used to declare. */
  private fun dpHeader() =
    RcHeader(
      RcVersion(1, 0, 0),
      properties =
        listOf(
          RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(SIZE)),
          RcHeaderProperty(RcHeader.DOC_HEIGHT, RcHeaderValue.IntValue(SIZE)),
          RcHeaderProperty(
            RcHeader.DOC_DENSITY_AT_GENERATION,
            RcHeaderValue.FloatValue(RcFloatWord.literal(2f)),
          ),
          RcHeaderProperty(
            RcHeader.DOC_DENSITY_BEHAVIOR,
            RcHeaderValue.IntValue(RcHeader.DENSITY_BEHAVIOR_DP),
          ),
        ),
    )

  private fun whiteBackground() =
    RcBackgroundModifier(
      flags = 0,
      colorId = 0,
      reserved1 = 0,
      reserved2 = 0,
      red = RcFloatWord.literal(1f),
      green = RcFloatWord.literal(1f),
      blue = RcFloatWord.literal(1f),
      alpha = RcFloatWord.literal(1f),
      shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
    )

  /** A filling column with a 20px gap, wrapped around two weighted white boxes. */
  private fun spacedColumn(): RcDocument {
    val child = { componentId: Int ->
      listOf<RcOperation>(
        RcBoxLayout(componentId, componentId * 10, 1, 4),
        fillWidth(),
        RcHeightModifier(RcDimensionType.WEIGHT, RcFloatWord.literal(1f)),
        whiteBackground(),
        RcLayoutContent(componentId * 10 + 1),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
      )
    }
    return RcDocument(
      dpHeader(),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcColumnLayout(3, 30, 1, 4, RcFloatWord.literal(INSET)),
        fillWidth(),
        fillHeight(),
        RcLayoutContent(4),
      ) + child(5) + child(6) + List(4) { RcNoArg(RcOpcodes.CONTAINER_END) },
    )
  }

  private fun fillWidth() = RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f))

  private fun fillHeight() = RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f))

  private companion object {
    const val APP_CARD_FIXTURE = "AppCardRemote-640x480.rc.b64"
    const val SIZE = 100
    const val INSET = 20f
    val WHITE = 0xffffffff.toInt()
  }
}

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * Flattened layout ([LocalRcFlattenLayout]) draws what the per-component path draws: every catalog
 * document it applies to, rendered both ways, is the same to the pixel; and the texts it draws
 * itself are still in the semantics tree.
 */
@OptIn(ExperimentalRcPlayerApi::class)
class RcFlatLayoutTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }

  @Test
  fun aFlattenedDocumentDrawsTheSameAsItsComponents() {
    var compared = 0
    val changed = mutableListOf<String>()
    for (file in corpus) {
      val prepared =
        runCatching { RcPreparedDocument.decode(file.readBytes()) }.getOrNull() ?: continue
      // Only a document that defers bounds animations is flattened.
      if (prepared.plainComponents.isEmpty() || prepared.changesLayoutOnItsOwn) continue
      val header = prepared.document.header
      if (header.width !in 1..1024 || header.height !in 1..1024) continue
      val components = render(prepared, flatten = false) ?: continue
      if (!components.contentEquals(render(prepared, flatten = false))) continue
      compared++
      if (!components.contentEquals(render(prepared, flatten = true))) changed += file.name
    }
    assertTrue(compared > 100, "only $compared corpus documents were compared")
    assertTrue(changed.isEmpty(), "${changed.size} of $compared drew differently: $changed")
  }

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun aTextTheContainerDrawsIsStillInTheSemanticsTree() =
    runSkikoComposeUiTest(size = Size(454f, 200f), density = Density(DENSITY)) {
      val prepared = RcPreparedDocument.decode(corpus.single { it.name == SUBHEADER }.readBytes())
      assertTrue(prepared.plainComponents.isNotEmpty())
      setContent {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides true,
        ) {
          RcComposePlayer(prepared, systemColors = { null })
        }
      }
      waitForIdle()
      onNodeWithText("Subtitle").assertExists()
    }

  @Test
  fun aRowLeavesRoomForItsGapsWhenItMeasures() {
    // 60 + 10 + 60 in 100: the second box gets the 30 left after the gap, as a Compose row gives
    // it.
    assertSameBothWays(linear(row = true, weighted = false), width = 100, height = 40, room = 40)
  }

  @Test
  fun aRowRunsFromTheEndRightToLeft() {
    assertSameBothWays(linear(row = true, weighted = false), width = 200, height = 40, rtl = true)
  }

  /**
   * Compose gives a weighted child no space when the main axis is unbounded; the flat layout
   * measures it the same way rather than dropping it (which drew the same nothing, but left a
   * composed child unmeasured).
   */
  @Test
  fun aWeightedChildOfAnUnboundedColumnIsStillMeasured() {
    assertSameBothWays(
      linear(row = false, weighted = true),
      width = 100,
      height = 120,
      unboundedHeight = true,
    )
  }

  private fun assertSameBothWays(
    document: RcDocument,
    width: Int,
    height: Int,
    rtl: Boolean = false,
    unboundedHeight: Boolean = false,
    /** Space beside the player, so a child that overflows it shows. */
    room: Int = 0,
  ) {
    val prepared = RcPreparedDocument(document)
    assertTrue(prepared.plainComponents.isNotEmpty(), "nothing to flatten")
    fun draw(flatten: Boolean): IntArray {
      val scene =
        ImageComposeScene(width = width + room, height = height, density = Density(1f)) {
          CompositionLocalProvider(
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalRcDeferLayoutAnimations provides true,
            LocalRcFlattenLayout provides flatten,
          ) {
            val host =
              if (unboundedHeight) Modifier.wrapContentHeight(unbounded = true) else Modifier
            Box(host) { RcComposePlayer(prepared, Modifier.width(width.dp)) }
          }
        }
      try {
        val sceneWidth = width + room
        val bitmap = Bitmap().apply { allocN32Pixels(sceneWidth, height) }
        check(scene.render(0L).readPixels(bitmap))
        return IntArray(sceneWidth * height) { bitmap.getColor(it % sceneWidth, it / sceneWidth) }
      } finally {
        scene.close()
      }
    }
    val components = draw(flatten = false)
    assertTrue(components.any { it != 0 }, "nothing was drawn")
    assertContentEquals(components, draw(flatten = true))
  }

  /** A row or column filling the width: two coloured boxes 10 apart, the second weighted or 60. */
  private fun linear(row: Boolean, weighted: Boolean): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    fun box(id: Int, red: Float, weightedBox: Boolean) =
      listOf(
        RcBoxLayout(id, -1, 1, 1),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
        if (weightedBox) RcHeightModifier(RcDimensionType.WEIGHT, RcFloatWord.literal(1f))
        else RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        RcBackgroundModifier(
          flags = 0,
          colorId = 0,
          reserved1 = 0,
          reserved2 = 0,
          red = RcFloatWord.literal(red),
          green = RcFloatWord.literal(1f - red),
          blue = RcFloatWord.literal(0f),
          alpha = RcFloatWord.literal(1f),
          shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
        ),
        RcLayoutContent(id + 1),
        end,
        end,
      )
    val container =
      if (row) RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(10f))
      else RcColumnLayout(3, -1, 1, 4, RcFloatWord.literal(10f))
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 200, legacyHeight = 120, modern = false),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        container,
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcLayoutContent(4),
      ) +
        box(10, 1f, weightedBox = false) +
        box(20, 0f, weightedBox = weighted) +
        listOf(end, end, end, end),
    )
  }

  private fun render(prepared: RcPreparedDocument, flatten: Boolean): IntArray? {
    val width = prepared.document.header.width
    val height = prepared.document.header.height
    val scene =
      ImageComposeScene(width = width, height = height, density = Density(DENSITY)) {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides flatten,
        ) {
          RcComposePlayer(prepared, systemColors = { null })
        }
      }
    return try {
      val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
      if (!scene.render(0L).readPixels(bitmap)) return null
      IntArray(width * height) { bitmap.getColor(it % width, it / width) }
    } catch (_: Exception) {
      null
    } finally {
      scene.close()
    }
  }

  private companion object {
    const val DENSITY = 2f
    const val SUBHEADER = "listsubheader__ideal__centred__compact.rc"
  }
}

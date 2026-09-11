package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

class RcEmptyBoxRenderTest {
  @Test
  fun emptyBoxPaintsItsAuthoredBoundsAtBothDensities() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 100, modern = false),
        listOf(
          RcRootLayout(1),
          RcBoxLayout(2, 20, 1, 4),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(30f)),
          RcBackgroundModifier(
            flags = 0,
            colorId = 0,
            reserved1 = 0,
            reserved2 = 0,
            red = RcFloatWord.literal(1f),
            green = RcFloatWord.literal(0f),
            blue = RcFloatWord.literal(0f),
            alpha = RcFloatWord.literal(1f),
            shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )
    assertTrue(document.composeSupportReport().fullyRenderable)
    for (density in listOf(1f, 2f)) {
      val scene =
        ImageComposeScene(width = 100, height = 100, density = Density(density)) {
          RcComposePlayer(document)
        }
      try {
        val rendered = scene.render(0L)
        val bitmap = Bitmap().apply { allocN32Pixels(100, 100) }
        check(rendered.readPixels(bitmap))
        assertEquals(0xffff0000.toInt(), bitmap.getColor(10, 10))
        assertEquals(0, bitmap.getColor(40, 10))
        assertEquals(0, bitmap.getColor(10, 30))
        File("build/empty-box-evidence").mkdirs()
        val leafPng = rendered.encodeToData()!!.bytes
        File("build/empty-box-evidence/density-$density.png").writeBytes(leafPng)
        // The previously supported, equivalent encoding has an explicit empty content block.
        val explicitContent =
          document.copy(
            operations =
              document.operations.dropLast(2) +
                RcLayoutContent(3) +
                List(3) { RcNoArg(RcOpcodes.CONTAINER_END) }
          )
        val reference =
          ImageComposeScene(width = 100, height = 100, density = Density(density)) {
            RcComposePlayer(explicitContent)
          }
        try {
          val referencePng = reference.render(0L).encodeToData()!!.bytes
          assertContentEquals(referencePng, leafPng)
          File("build/empty-box-evidence/explicit-content-$density.png").writeBytes(referencePng)
        } finally {
          reference.close()
        }
      } finally {
        scene.close()
      }
    }
  }
}

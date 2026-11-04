package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.threed.Rc3DRenderTest.Companion.render
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Writes the evidence renders under `-Prc.3d.out`; a no-op otherwise. */
class Rc3DEvidenceTest {
  @Test
  fun writeRenders() {
    val out = System.getProperty("rc.3d.out")?.let(::File) ?: return
    out.mkdirs()
    val size = 256
    val renders =
      mapOf(
        "cube-software" to
          render(Rc3DDocuments.litCube(Rc3D.MODE_SOFTWARE_SMOOTH, size).encode(), size = size),
        "cube-gpu" to
          render(Rc3DDocuments.litCube(Rc3D.MODE_CANVAS_SMOOTH, size).encode(), size = size),
        "cube-wireframe" to
          render(
            Rc3DDocuments.litCube(Rc3D.MODE_SOFTWARE_FLAT or Rc3D.MODE_WIREFRAME, size).encode(),
            size = size,
          ),
      )
    renders.forEach { (name, pixels) -> write(File(out, "$name.png"), pixels, size) }
    val small = Rc3DRenderTest.SIZE
    mapOf(
        "torus" to render(Rc3DDocuments.torus().encode()),
        "height-field" to render(Rc3DDocuments.heightField().encode()),
        "host-camera-side" to
          render(
            Rc3DDocuments.hostCameraCube().encode(),
            namedValues = Rc3DHostCamera(eye = floatArrayOf(2.5f, 1.5f, 2.5f)).namedValues(),
          ),
      )
      .forEach { (name, pixels) -> write(File(out, "$name.png"), pixels, small) }
  }

  private fun write(file: File, pixels: IntArray, size: Int) {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, size, size, pixels, 0, size)
    ImageIO.write(image, "png", file)
  }
}

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

  /**
   * What the XR sample's panel shows: in the fallback the player draws both meshes; handing off,
   * the rigid cube has gone to SceneCore and only the deforming torus stays, found after its
   * geometry changed on two frames running.
   */
  @Test
  fun writeXrPanelRenders() {
    val out = System.getProperty("rc.3d.out")?.let(::File) ?: return
    out.mkdirs()
    val bytes = Rc3DDocuments.xrSample().encode()
    val times = List(5) { it * 100_000_000L }
    val fallback = Rc3DRenderTest.renderFrames(bytes, times).last()
    val handOff =
      Rc3DRenderTest.renderFrames(
          bytes,
          times,
          Rc3DOptions(drawInPlayer = false, sceneListener = KeepDeforming()),
        )
        .last()
    write(File(out, "xr-panel-fallback.png"), fallback, Rc3DRenderTest.SIZE)
    write(File(out, "xr-panel-handoff.png"), handOff, Rc3DRenderTest.SIZE)
  }

  /** The sample host's rule: a draw whose mesh version changed on two frames running stays. */
  private class KeepDeforming : Rc3DSceneListener {
    private val last = mutableMapOf<String, Long>()
    private val changedLast = mutableSetOf<String>()
    private val deforming = mutableSetOf<String>()

    override fun onDrawMesh(draw: Rc3DMeshDraw) = Unit

    override fun onFrame(frame: Rc3DFrame) {
      for (draw in frame.draws) {
        val previous = last.put(draw.key, draw.meshVersion)
        val changed = previous != null && previous != draw.meshVersion
        if (changed && draw.key in changedLast) deforming += draw.key
        if (changed) changedLast += draw.key else changedLast -= draw.key
      }
    }

    override fun keepInPlayer(draw: Rc3DMeshDraw): Boolean = draw.key in deforming
  }

  private fun write(file: File, pixels: IntArray, size: Int) {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, size, size, pixels, 0, size)
    ImageIO.write(image, "png", file)
  }
}

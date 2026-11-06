package ee.schimke.composeai.rcplayer.xrsample

import ee.schimke.composeai.rcplayer.protocol.RcDraw6
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextAnchored
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootContentBehavior
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.threed.Rc3DDocumentBuilder
import ee.schimke.composeai.rcplayer.threed.Rc3DLight
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3D

/**
 * The document the sample plays: a 2D card with a title, which stays on the panel, and two meshes,
 * which an XR host lifts into the room.
 *
 * - A cube spun by the clock: rigid, so its mesh version never changes and the host only moves its
 *   entity each frame.
 * - A torus whose tube breathes with the clock: its geometry changes each frame, so the host
 *   rebuilds it, the case that shows what a deforming mesh costs.
 *
 * Built with [Rc3DDocumentBuilder] and encoded, as a server would send it.
 */
internal object RcXrSampleDocument {
  const val SIZE: Int = 400

  fun bytes(): ByteArray =
    Rc3DDocumentBuilder(SIZE, SIZE)
      .apply {
        // Scaled to fit whatever size the player is: the card and the scene share the document's
        // 400 units, so they stay together on a phone and on a resized XR panel.
        add(
          RcRootContentBehavior(
            RcRootContentBehavior.NONE,
            RcRootContentBehavior.ALIGNMENT_CENTER,
            RcRootContentBehavior.SIZING_SCALE,
            RcRootContentBehavior.SCALE_FIT,
          ),
        )
        // The card: a translucent rounded rectangle and a title, drawn by the player on the panel.
        color(0xCC1E2430.toInt())
        add(
          RcDraw6(
            RcOpcodes.DRAW_ROUND_RECT,
            float(8f),
            float(8f),
            float(SIZE - 8f),
            float(SIZE - 8f),
            float(28f),
            float(28f),
          ),
        )
        val title = newId()
        add(RcTextData(title, "Remote Compose in XR"))
        color(0xFFFFFFFF.toInt())
        add(
          RcDrawTextAnchored(
            title,
            float(SIZE / 2f),
            float(40f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            0,
          ),
        )

        // The 3D scene.
        val time = continuousSeconds()
        clearDepth()
        perspectiveCamera(
          float(0.9f),
          float(1f),
          float(0.1f),
          float(100f),
          listOf(float(0f), float(0.6f), float(4f)),
          listOf(float(0f), float(0f), float(0f)),
        )
        lights(Rc3DLight.directional(-0.3f, -0.5f, -0.8f))

        // A rigid cube, spun by the clock.
        color(0xFFEE8844.toInt())
        identity()
        translate(float(-0.9f), float(0f), float(0f))
        rotate(times(time, float(0.8f)), 0f, 1f, 0f)
        rotate(float(0.35f), 1f, 0f, 0f)
        draw(primitive(RcPrimitive3D.CUBE, listOf(0.5f, 0.5f, 0.5f, 0f, 0f, 0f).map(::float)))

        // A torus whose tube radius follows the clock: a deforming mesh.
        val tube =
          expression(
            listOf(
              time,
              Rc3DDocumentBuilder.operator(Rc3DDocumentBuilder.SIN),
              float(0.06f),
              Rc3DDocumentBuilder.operator(Rc3DDocumentBuilder.MUL),
              float(0.22f),
              Rc3DDocumentBuilder.operator(Rc3DDocumentBuilder.ADD),
            ),
          )
        color(0xFF4488EE.toInt())
        identity()
        translate(float(0.9f), float(0f), float(0f))
        rotate(float(1.1f), 1f, 0f, 0f)
        draw(
          primitive(
            RcPrimitive3D.TORUS,
            listOf(float(0.55f), tube, float(0f), float(0f), float(0f)),
            float(32f),
          ),
        )
      }
      .encode()
}

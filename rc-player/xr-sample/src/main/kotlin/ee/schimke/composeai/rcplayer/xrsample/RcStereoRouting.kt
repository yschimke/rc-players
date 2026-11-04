package ee.schimke.composeai.rcplayer.xrsample

import ee.schimke.composeai.rcplayer.threed.Rc3DFrame
import ee.schimke.composeai.rcplayer.threed.Rc3DMeshDraw

/** Where [RcSceneCoreHost] sends one draw; the levels of docs/design/RC_3D_EXTENSION.md, "XR". */
internal enum class RcDrawRoute {
  /** Level 3: a `MeshEntity` in the room, built once and moved. */
  Entity,

  /** Level 2: rendered once per eye into the side-by-side stereo surface over the viewport. */
  Stereo,

  /** Level 1: handed back to the player, drawn flat on the panel. */
  Panel,
}

/**
 * What the stereo surface needs this frame. All three must hold for a deforming draw to go stereo;
 * otherwise it falls back to the panel.
 *
 * @property contentAllowed the session allows 3D content (Full Space), as the entities need too.
 * @property eyesTracked both eyes' poses are known: device tracking is configured and reporting.
 * @property rendererReady the GLES renderer has a surface and has not failed.
 */
internal data class RcStereoState(
  val contentAllowed: Boolean,
  val eyesTracked: Boolean,
  val rendererReady: Boolean,
) {
  val ready: Boolean
    get() = contentAllowed && eyesTracked && rendererReady

  companion object {
    val OFF = RcStereoState(contentAllowed = false, eyesTracked = false, rendererReady = false)
  }
}

internal object RcDrawRouting {
  /**
   * A rigid draw is handed off as an entity. A deforming one, which SceneCore 1.0 cannot take as an
   * entity, goes to the stereo surface when it is [ready][RcStereoState.ready], and back to the
   * panel when it is not.
   */
  fun route(deforming: Boolean, stereo: RcStereoState): RcDrawRoute =
    when {
      !deforming -> RcDrawRoute.Entity
      stereo.ready -> RcDrawRoute.Stereo
      else -> RcDrawRoute.Panel
    }
}

/**
 * The draws the stereo surface shows, across components. Each component's [Rc3DFrame] replaces what
 * that component had here, as the listener contract says, and leaves other components' alone; the
 * surface then renders the whole set for both eyes.
 */
internal class RcStereoLayer {
  private val draws = LinkedHashMap<String, Rc3DMeshDraw>()

  /** The current draws, in the order their components first reported them. */
  val current: Collection<Rc3DMeshDraw>
    get() = draws.values

  val isEmpty: Boolean
    get() = draws.isEmpty()

  /**
   * Replace [frame]'s component's draws with [stereo], the ones of its frame routed here. True when
   * the layer changed, so the surface needs a new render.
   */
  fun update(frame: Rc3DFrame, stereo: List<Rc3DMeshDraw>): Boolean {
    val prefix = "${frame.componentId}/"
    val removed = draws.keys.removeAll { it.startsWith(prefix) }
    for (draw in stereo) draws[draw.key] = draw
    return removed || stereo.isNotEmpty()
  }

  fun clear() = draws.clear()
}

package ee.schimke.composeai.rcplayer.xrsample

import androidx.xr.runtime.Session
import androidx.xr.runtime.math.BoundingBox
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.IntSize2d
import androidx.xr.runtime.math.Matrix4
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.math.Vector4
import androidx.xr.scenecore.AlphaMode
import androidx.xr.scenecore.CustomMesh
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.ExperimentalSurfaceEntityPixelDimensionsApi
import androidx.xr.scenecore.KhronosPbrMaterial
import androidx.xr.scenecore.MeshEntity
import androidx.xr.scenecore.MeshSubsetTopology
import androidx.xr.scenecore.SurfaceEntity
import androidx.xr.scenecore.VertexAttribute
import androidx.xr.scenecore.VertexAttributeType
import androidx.xr.scenecore.VertexLayout
import ee.schimke.composeai.rcplayer.threed.Rc3DFrame
import ee.schimke.composeai.rcplayer.threed.Rc3DMeshDraw
import ee.schimke.composeai.rcplayer.threed.Rc3DPanelPlacement
import ee.schimke.composeai.rcplayer.threed.Rc3DSceneListener
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Mirrors a document's 3D draws into SceneCore as `MeshEntity`s, children of a panel, so the meshes
 * are real objects in the room: seen in stereo, occluding and occluded, lit by the system.
 *
 * The player keeps evaluating the document (`Rc3DOptions.drawInPlayer = false`) and reports each
 * component's whole pass through [onFrame]. Per draw key this host keeps one entity:
 * - a new key creates it; a key missing from its component's frame hides it;
 * - an unchanged mesh version only moves it, which is all a rigid mesh spun by the clock costs;
 * - a changed version rebuilds its geometry once.
 *
 * A mesh whose version changes again within a few frames is deforming. SceneCore 1.0 takes fixed
 * geometry and does not let an app free an entity (`Entity.dispose` is restricted), so rebuilding
 * every frame would leak one per frame. Such a draw goes to the stereo surface instead (level 2 in
 * docs/design/RC_3D_EXTENSION.md): a side-by-side stereo `SurfaceEntity` over the player's
 * viewport, rendered once per eye by [RcStereoSurfaceRenderer] with cameras from the eyes' poses
 * relative to the panel ([RcStereoCameras]). When the stereo surface cannot run (no 3D content, no
 * eye poses, or its renderer failed), a deforming draw is handed back to the player
 * ([keepInPlayer]) and drawn flat on the panel: level 1. [RcDrawRouting] makes that choice.
 *
 * The stereo surface is a flat layer just in front of the panel, so its meshes have depth for the
 * eyes but are not sorted against the room entities or the room: a rigid entity in front of a
 * deforming mesh still shows behind the surface's pixels where they overlap.
 *
 * Placement is [Rc3DPanelPlacement]: the document's camera puts the scene behind and in front of
 * the panel so that, seen head-on, it lines up with the flat render.
 */
internal class RcSceneCoreHost(
  private val session: Session,
  private val parent: Entity,
  private val scope: CoroutineScope,
  /**
   * Where the player draws on the panel, read per frame since the user can resize the panel and the
   * layout can move the player; null until it has been measured.
   */
  private val viewport: () -> RcXrViewport?,
  /**
   * Both eyes' positions in the panel's space, in metres, or null while they are not tracked. Read
   * per frame; null routes deforming draws back to the panel.
   */
  private val eyes: () -> RcEyePositions? = { null },
  /** Whether the session allows 3D content now (Full Space); the stereo surface needs it too. */
  private val contentAllowed: () -> Boolean = { true },
) : Rc3DSceneListener, AutoCloseable {
  private class Mirror(val entity: MeshEntity, val meshVersion: Long, var color: Int)

  private val mirrors = mutableMapOf<String, Mirror>()
  private val materials = mutableMapOf<Int, KhronosPbrMaterial>()
  private val loadingMaterials = mutableSetOf<Int>()

  /** The latest draw per key whose material is still loading; applied when it arrives. */
  private val waiting = mutableMapOf<String, Rc3DMeshDraw>()

  /** Deforming draws, which go stereo or back to the player: see the class comment. */
  private val tracker = RcDeformationTracker()

  private val stereoLayer = RcStereoLayer()
  private val stereoRenderer = RcStereoSurfaceRenderer()
  private var stereoSurface: SurfaceEntity? = null
  private var stereoViewport: RcXrViewport? = null

  /** Entities currently in the room, for the UI and tests. */
  val entityCount: Int
    get() = mirrors.size

  override fun onDrawMesh(draw: Rc3DMeshDraw) = Unit

  /** What the stereo surface has this frame. */
  private fun stereoState(): RcStereoState =
    RcStereoState(
      contentAllowed = contentAllowed(),
      eyesTracked = eyes() != null,
      rendererReady = stereoRenderer.available,
    )

  override fun keepInPlayer(draw: Rc3DMeshDraw): Boolean =
    RcDrawRouting.route(tracker.isDeforming(draw.key), stereoState()) == RcDrawRoute.Panel

  override fun onFrame(frame: Rc3DFrame) {
    val prefix = "${frame.componentId}/"
    val drawn = frame.draws.mapTo(HashSet()) { it.key }
    // What the component no longer draws leaves the room.
    mirrors.keys.filter { it.startsWith(prefix) && it !in drawn }.forEach(::hide)
    waiting.keys.removeAll { it.startsWith(prefix) && it !in drawn }
    val stereo = stereoState()
    val stereoDraws = mutableListOf<Rc3DMeshDraw>()
    for (draw in frame.draws) {
      tracker.observe(draw.key, draw.meshVersion)
      // Routed every frame, not only the one a draw became deforming on: the stereo surface can
      // come and go with eye tracking.
      when (RcDrawRouting.route(tracker.isDeforming(draw.key), stereo)) {
        RcDrawRoute.Entity -> apply(draw)
        RcDrawRoute.Stereo -> {
          hide(draw.key)
          waiting.remove(draw.key)
          stereoDraws += draw
        }
        RcDrawRoute.Panel -> {
          hide(draw.key)
          waiting.remove(draw.key)
        }
      }
    }
    stereoLayer.update(frame, stereoDraws)
    if (!stereo.ready) stereoLayer.clear()
    renderStereo()
  }

  /** Render the stereo layer for both eyes, or hide the surface when there is nothing to show. */
  private fun renderStereo() {
    val area = viewport()
    val eyes = eyes()
    if (stereoLayer.isEmpty || area == null || eyes == null) {
      stereoSurface?.setEnabled(false)
      return
    }
    val window = RcStereoWindow.of(area)
    val left = RcStereoCameras.eye(eyes.leftX, eyes.leftY, eyes.leftZ, window)
    val right = RcStereoCameras.eye(eyes.rightX, eyes.rightY, eyes.rightZ, window)
    val entity = stereoSurface(area, window) ?: return
    if (left == null || right == null) {
      // Behind the panel, or level with it: nothing to see through the window.
      entity.setEnabled(false)
      return
    }
    entity.setEnabled(true)
    if (!stereoRenderer.render(entity.getSurface(), stereoLayer.current, area, left, right)) {
      entity.setEnabled(false)
    }
  }

  /** The stereo surface over [area], made on first use and re-fitted when the viewport changes. */
  @OptIn(ExperimentalSurfaceEntityPixelDimensionsApi::class)
  private fun stereoSurface(area: RcXrViewport, window: RcStereoWindow): SurfaceEntity? {
    val existing = stereoSurface
    if (existing != null && stereoViewport == area) return existing
    val pose = Pose(Vector3(window.centerX, window.centerY, window.z), IDENTITY.rotation)
    val shape = SurfaceEntity.Shape.Quad(FloatSize2d(window.width, window.height))
    val entity =
      existing
        ?: runCatching {
          SurfaceEntity.create(
            session = session,
            pose = pose,
            shape = shape,
            stereoMode = SurfaceEntity.StereoMode.SIDE_BY_SIDE,
            // The public overloads blend the surface's alpha (TRANSPARENT), so the panel shows
            // around the meshes; the overload that takes a blending mode is restricted.
            parent = parent,
          )
        }
          .getOrNull()
        ?: return null
    if (existing != null) {
      entity.setPose(pose)
      entity.shape = shape
    }
    // Each eye at the player's own pixel size, side by side.
    if (area.widthPx > 0 && area.heightPx > 0) {
      entity.setSurfacePixelDimensions(IntSize2d(area.widthPx * 2, area.heightPx))
    }
    stereoSurface = entity
    stereoViewport = area
    return entity
  }

  private fun apply(draw: Rc3DMeshDraw) {
    val material = materials[draw.color]
    if (material == null) {
      waiting[draw.key] = draw
      requestMaterial(draw.color)
      return
    }
    var mirror = mirrors[draw.key]
    if (mirror == null || mirror.meshVersion != draw.meshVersion) {
      mirror?.let { hide(draw.key) }
      mirror = create(draw, material) ?: return
      mirrors[draw.key] = mirror
    } else if (mirror.color != draw.color) {
      mirror.entity.setMaterial(material)
      mirror.color = draw.color
    }
    place(mirror.entity, draw)
  }

  private fun create(draw: Rc3DMeshDraw, material: KhronosPbrMaterial): Mirror? {
    val packed = runCatching { RcXrMeshPacking.pack(draw.mesh) }.getOrNull() ?: return null
    val layout =
      VertexLayout.Builder()
        .addAttribute(VertexAttribute.POSITION, VertexAttributeType.FLOAT3)
        .addAttribute(VertexAttribute.NORMAL, VertexAttributeType.FLOAT3)
        .apply { if (packed.hasUv) addAttribute(VertexAttribute.UV0, VertexAttributeType.FLOAT2) }
        .build()
    val vertices =
      ByteBuffer.allocateDirect(packed.vertices.size * 4).order(ByteOrder.nativeOrder()).apply {
        asFloatBuffer().put(packed.vertices)
      }
    // SceneCore reads 32-bit indices: CustomMesh counts them as the buffer's size over four.
    val indices =
      ByteBuffer.allocateDirect(packed.indices.size * 4).order(ByteOrder.nativeOrder()).apply {
        asIntBuffer().put(packed.indices)
      }
    val mesh =
      CustomMesh.BuilderFromMeshData(session, layout)
        .addVertexData(vertices)
        .setIndexData(indices)
        .setTopology(MeshSubsetTopology.TRIANGLES)
        .setBounds(
          BoundingBox.fromMinMax(
            Vector3(packed.boundsMin[0], packed.boundsMin[1], packed.boundsMin[2]),
            Vector3(packed.boundsMax[0], packed.boundsMax[1], packed.boundsMax[2]),
          )
        )
        .build()
    // No bones: the player has already resolved every transform into the draw's matrices.
    val entity = MeshEntity.create(session, mesh, listOf(material), 0, IDENTITY, parent)
    return Mirror(entity, draw.meshVersion, draw.color)
  }

  private fun place(entity: MeshEntity, draw: Rc3DMeshDraw) {
    val area = viewport()
    if (area == null) {
      // Not measured yet: hidden rather than placed against the wrong area.
      entity.setEnabled(false)
      return
    }
    entity.setEnabled(true)
    val transform = Matrix4(Rc3DPanelPlacement.panelTransform(draw, area.heightMeters))
    val t = transform.translation
    entity.setPose(
      Pose(
        Vector3(t.x + area.offsetXMeters, t.y + area.offsetYMeters, t.z),
        transform.unscaled().rotation,
      )
    )
    // Uniform: the placement's own scale is uniform, and the public API takes one factor. A model
    // matrix with a non-uniform scale is approximated by its mean.
    val scale = transform.scale
    entity.setScale((scale.x + scale.y + scale.z) / 3f)
  }

  private fun requestMaterial(color: Int) {
    if (!loadingMaterials.add(color)) return
    scope.launch {
      val rgba = RcXrMeshPacking.linearRgba(color)
      val alpha = if (rgba[3] < 1f) AlphaMode.BLEND else AlphaMode.OPAQUE
      val material = KhronosPbrMaterial.create(session, alpha)
      material.setBaseColorFactor(Vector4(rgba[0], rgba[1], rgba[2], rgba[3]))
      // The player's lighting is matte Lambert with an optional highlight: a rough dielectric is
      // the closest physically based match.
      material.setMetallicFactor(0f)
      material.setRoughnessFactor(0.8f)
      materials[color] = material
      loadingMaterials.remove(color)
      val ready = waiting.values.filter { it.color == color }
      ready.forEach { waiting.remove(it.key) }
      ready.forEach(::apply)
    }
  }

  /**
   * Take an entity out of the room. SceneCore 1.0 does not let an app dispose of one, so it is
   * disabled and detached; its native resources go with the session.
   */
  private fun hide(key: String) {
    val mirror = mirrors.remove(key) ?: return
    mirror.entity.setEnabled(false)
    mirror.entity.parent = null
  }

  override fun close() {
    mirrors.keys.toList().forEach(::hide)
    stereoLayer.clear()
    stereoRenderer.dispose()
    stereoSurface?.let {
      it.setEnabled(false)
      it.parent = null
    }
    stereoSurface = null
    materials.values.forEach { it.close() }
    materials.clear()
    waiting.clear()
  }

  private companion object {
    val IDENTITY = Pose(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f))
  }
}

/**
 * Decides which draws are deforming: a mesh whose version changed twice within [WINDOW] frames of
 * each other. A window rather than consecutive frames, since the player can repaint at the same
 * animation time between clock ticks, repeating a version that is still changing every tick. A
 * rigid mesh rebuilt now and then (a host value changed) is repainted every frame it spins, so its
 * rebuilds are far apart in frames. Once deforming, a draw stays with the player, so a mesh that
 * animates in bursts is not handed back and forth. Plain Kotlin, so its rule is unit-tested without
 * a headset.
 */
internal class RcDeformationTracker {
  private class Seen(var version: Long, var frames: Long = 0, var lastChange: Long = Long.MIN_VALUE)

  private val seen = mutableMapOf<String, Seen>()
  private val deforming = mutableSetOf<String>()

  fun isDeforming(key: String): Boolean = key in deforming

  /** Record [key]'s version this frame; true when that makes it deforming just now. */
  fun observe(key: String, version: Long): Boolean {
    if (key in deforming) return false
    val s = seen[key]
    if (s == null) {
      seen[key] = Seen(version)
      return false
    }
    s.frames++
    if (s.version == version) return false
    s.version = version
    if (s.lastChange != Long.MIN_VALUE && s.frames - s.lastChange <= WINDOW) {
      deforming += key
      seen -= key
      return true
    }
    s.lastChange = s.frames
    return false
  }

  private companion object {
    /** Frames between two changes that still count as one deformation. */
    const val WINDOW = 4L
  }
}

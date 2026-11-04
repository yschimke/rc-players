package ee.schimke.composeai.rcplayer.xrsample

import androidx.xr.runtime.Session
import androidx.xr.runtime.math.BoundingBox
import androidx.xr.runtime.math.Matrix4
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.math.Vector4
import androidx.xr.scenecore.AlphaMode
import androidx.xr.scenecore.CustomMesh
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.KhronosPbrMaterial
import androidx.xr.scenecore.MeshEntity
import androidx.xr.scenecore.MeshSubsetTopology
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
 * A mesh whose version changes again on the next frame is deforming. SceneCore 1.0 takes fixed
 * geometry and does not let an app free an entity (`Entity.dispose` is restricted), so rebuilding
 * every frame would leak one per frame. Such a draw is handed back to the player ([keepInPlayer])
 * and drawn on the panel instead: the per-draw fallback in docs/design/RC_3D_EXTENSION.md.
 *
 * Placement is [Rc3DPanelPlacement]: the document's camera puts the scene behind and in front of
 * the panel so that, seen head-on, it lines up with the flat render.
 */
internal class RcSceneCoreHost(
  private val session: Session,
  private val parent: Entity,
  private val scope: CoroutineScope,
  /** The panel's height in metres, read per frame since the user can resize it. */
  private val panelHeightMeters: () -> Float,
) : Rc3DSceneListener, AutoCloseable {
  private class Mirror(val entity: MeshEntity, val meshVersion: Long, var color: Int)

  private val mirrors = mutableMapOf<String, Mirror>()
  private val materials = mutableMapOf<Int, KhronosPbrMaterial>()
  private val loadingMaterials = mutableSetOf<Int>()

  /** The latest draw per key whose material is still loading; applied when it arrives. */
  private val waiting = mutableMapOf<String, Rc3DMeshDraw>()

  /** Deforming draws, which the player keeps: see the class comment. */
  private val tracker = RcDeformationTracker()

  /** Entities currently in the room, for the UI and tests. */
  val entityCount: Int
    get() = mirrors.size

  override fun onDrawMesh(draw: Rc3DMeshDraw) = Unit

  override fun keepInPlayer(draw: Rc3DMeshDraw): Boolean = tracker.isDeforming(draw.key)

  override fun onFrame(frame: Rc3DFrame) {
    val prefix = "${frame.componentId}/"
    val drawn = frame.draws.mapTo(HashSet()) { it.key }
    // What the component no longer draws leaves the room.
    mirrors.keys.filter { it.startsWith(prefix) && it !in drawn }.forEach(::hide)
    waiting.keys.removeAll { it.startsWith(prefix) && it !in drawn }
    for (draw in frame.draws) {
      if (tracker.observe(draw.key, draw.meshVersion)) {
        hide(draw.key)
        waiting.remove(draw.key)
      } else {
        apply(draw)
      }
    }
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
    val transform = Matrix4(Rc3DPanelPlacement.panelTransform(draw, panelHeightMeters()))
    entity.setPose(Pose(transform.translation, transform.unscaled().rotation))
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
    materials.values.forEach { it.close() }
    materials.clear()
    waiting.clear()
  }

  private companion object {
    val IDENTITY = Pose(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f))
  }
}

/**
 * Decides which draws are deforming: a mesh whose version changed on two frames running. Once
 * deforming, a draw stays with the player, so a mesh that animates in bursts is not handed back and
 * forth. Plain Kotlin, so its rule is unit-tested without a headset.
 */
internal class RcDeformationTracker {
  private val lastVersion = mutableMapOf<String, Long>()
  private val changedLastFrame = mutableSetOf<String>()
  private val deforming = mutableSetOf<String>()

  fun isDeforming(key: String): Boolean = key in deforming

  /** Record [key]'s version this frame; true when that makes it deforming just now. */
  fun observe(key: String, version: Long): Boolean {
    if (key in deforming) return false
    val previous = lastVersion.put(key, version)
    val changed = previous != null && previous != version
    if (changed && key in changedLastFrame) {
      deforming += key
      changedLastFrame -= key
      return true
    }
    if (changed) changedLastFrame += key else changedLastFrame -= key
    return false
  }
}

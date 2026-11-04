package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.compose.RcExtensionSession
import ee.schimke.composeai.rcplayer.compose.RcPlayerExtension
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcOperationCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireExtension
import ee.schimke.composeai.rcplayer.protocol.RcWireExtensions
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData

/**
 * The draft 3D operations as a player extension.
 *
 * Install it for the players below a point in the tree:
 * ```
 * CompositionLocalProvider(LocalRcPlayerExtensions provides RcPlayerExtensions(Rc3DExtension())) {
 *   RcComposePlayer(bytes)
 * }
 * ```
 *
 * Without it a document that uses these opcodes fails to decode, as it does on AndroidX's players.
 * With it, each `DRAW_MESH_3D` is drawn where it sits in the document, interleaved with the 2D
 * drawing around it, by the renderer [Rc3DOptions.renderer] selects.
 */
public class Rc3DExtension(public val options: Rc3DOptions = Rc3DOptions()) : RcPlayerExtension {
  override val wire: RcWireExtension
    get() = Rc3DWire

  override fun createSession(document: RcDocument, state: RcPlayerState): RcExtensionSession =
    Rc3DSession(state, options)

  public companion object {
    /** The extension id every 3D operation carries. */
    public const val ID: String = "androidx.remote.draft-3d"

    /** The AndroidX change whose wire layout this extension decodes. */
    public const val DRAFT: String = "ag/4108133"

    /** The 3D codecs alone, for a tool that decodes or writes documents without playing them. */
    public val wireExtensions: RcWireExtensions
      get() = RcWireExtensions(Rc3DWire)
  }
}

internal object Rc3DWire : RcWireExtension {
  override val id: String = Rc3DExtension.ID
  override val codecs: List<RcOperationCodec<out Rc3DOperation>> = rc3DCodecs
}

/** Which rasterizer draws a mesh. */
public enum class Rc3DRenderer {
  /**
   * Follow each draw's mode: the software modes (and wireframe, which needs a depth buffer) go
   * through the software rasterizer, every accelerated mode through the GPU vertex path.
   */
  Auto,

  /** Every mesh through the software rasterizer: per-pixel depth, the reference's output. */
  Software,

  /**
   * Every mesh but wireframe through the GPU: lighting and transform on the CPU, triangles drawn
   * with the platform's `drawVertices` in back-to-front order. No depth buffer, so intersecting
   * geometry sorts per triangle rather than per pixel.
   */
  Gpu,
}

/**
 * How a player draws the 3D operations.
 *
 * @property renderer which rasterizer draws each mesh.
 * @property pixelScale the software rasterizer's resolution relative to the drawing's own units: 1
 *   matches the reference, a host drawing at device resolution passes its density. The GPU path is
 *   resolution-independent and ignores it.
 * @property drawInPlayer false hands drawing to [sceneListener] alone, for a host that renders the
 *   scene itself (an XR runtime's own renderer, say) and composites the 2D document around it.
 * @property sceneListener told about every mesh the document draws, with the matrices and paint it
 *   is drawn with, whether or not the player also draws it.
 */
public class Rc3DOptions(
  public val renderer: Rc3DRenderer = Rc3DRenderer.Auto,
  public val pixelScale: Float = 1f,
  public val drawInPlayer: Boolean = true,
  public val sceneListener: Rc3DSceneListener? = null,
) {
  init {
    require(pixelScale > 0f && pixelScale <= 8f) { "pixelScale $pixelScale is not in (0, 8]" }
    require(drawInPlayer || sceneListener != null) {
      "With drawInPlayer = false a sceneListener is the only output"
    }
  }
}

/**
 * Receives each mesh draw, so a host can hand the scene to another renderer: an XR runtime that
 * turns meshes into scene entities once and then only updates their poses, for instance.
 */
public fun interface Rc3DSceneListener {
  public fun onDrawMesh(draw: Rc3DMeshDraw)
}

/**
 * One `DRAW_MESH_3D`, resolved. Matrices are column-major 4x4. [viewportWidth] and [viewportHeight]
 * are the size, in the drawing's units, that clip space maps onto.
 */
public class Rc3DMeshDraw(
  public val meshId: Int,
  public val mesh: Rc3DMeshData,
  public val model: FloatArray,
  public val view: FloatArray,
  public val projection: FloatArray,
  /** Unpremultiplied ARGB base colour, the paint's at the time of the draw. */
  public val color: Int,
  /** The `SET_TEXTURE_3D` bitmap in effect, or 0. */
  public val textureBitmapId: Int,
  public val mode: Int,
  public val viewportWidth: Float,
  public val viewportHeight: Float,
)

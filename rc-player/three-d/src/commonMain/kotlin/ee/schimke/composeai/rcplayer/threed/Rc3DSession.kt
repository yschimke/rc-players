package ee.schimke.composeai.rcplayer.threed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import ee.schimke.composeai.rcplayer.compose.RcExtensionDrawScope
import ee.schimke.composeai.rcplayer.compose.RcExtensionSession
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcExtensionOperation
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.runtime.RcFloatExpressionEvaluator
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcMeshExpressionGenerator
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3D
import ee.schimke.composeai.rcplayer.threed.engine.RcScreenMesh
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D
import ee.schimke.composeai.rcplayer.threed.engine.RcVectorRpn
import kotlin.math.roundToInt

/**
 * One document's 3D state: the engine, the meshes it has defined and the caches that keep a steady
 * scene cheap. Every operation runs in draw order, as the reference applies them during paint; the
 * camera, model matrix and lights carry from one operation to the next.
 *
 * What is cached, and why: a parametric primitive or expression surface is rebuilt only when the
 * values it was built from change (resolved inputs are the key), a texture's pixels are read back
 * once per bitmap, and the software frame and GPU vertex buffers are reused across frames.
 */
internal class Rc3DSession(private val state: RcPlayerState, private val options: Rc3DOptions) :
  RcExtensionSession {
  private val engine = RcSoftware3D()
  private val screenMesh = RcScreenMesh()
  private val vectorRpn = RcVectorRpn()
  private val vectorOut = FloatArray(RcVectorRpn.DIM)
  /** The document's float arrays as expressions see them, read once per surface build. */
  private val arrays = mutableMapOf<Int, FloatArray?>()
  private val expressions =
    RcMeshExpressionGenerator(
      RcFloatExpressionEvaluator { id -> arrays.getOrPut(id) { state.floatValues(id) } }
    )
  /**
   * One bitmap per software composite in a frame, reused from frame to frame. A frame's composites
   * cannot share one: Android's renderer uploads a mutable bitmap when the frame is synced, so
   * every draw recorded against it would show the last mesh's pixels.
   */
  private val frames = mutableListOf<RcArgbFrame>()
  private var framesUsed = 0
  private var frameKey: Long? = null
  private var textureBitmapId = 0
  private var textureImage: ImageBitmap? = null
  private val texturePixels = mutableMapOf<ImageBitmap, IntArray>()

  /**
   * Bitmaps the document draws into. Their `ImageBitmap` is reused while its pixels change, so a
   * readback of one is kept only for the frame it was taken in. Null when a `DRAW_TO_BITMAP`
   * chooses its target at run time, which makes every bitmap a possible target.
   */
  private val drawTargets: Set<Int>? =
    state.document.operations.filterIsInstance<RcDrawToBitmap>().let { targets ->
      if (targets.any { it.bitmapId and DRAW_ID_DEREFERENCE != 0 }) null
      else targets.mapTo(HashSet()) { it.bitmapId and DRAW_ID_MASK }
    }
  private val frameTexturePixels = mutableMapOf<ImageBitmap, IntArray>()
  private val definedMeshes = mutableMapOf<RcDefineMesh3D, Rc3DMeshData>()
  private val builtMeshes = mutableMapOf<Rc3DOperation, Pair<List<Float>, Rc3DMeshData?>>()

  override fun draw(operation: RcExtensionOperation, scope: RcExtensionDrawScope) {
    val op = operation as? Rc3DOperation ?: return
    beginFrameIfNew()
    ensureSize(scope)
    when (op) {
      is RcDefineMesh3D ->
        define(
          op.id,
          definedMeshes.getOrPut(op) {
            Rc3DMeshData(op.verts, op.normals, op.indices, op.uv.takeIf { it.isNotEmpty() })
          },
        )
      is RcSetCamera3D ->
        engine.setCamera(op.projection, resolve(op.projParams), resolve(op.viewParams))
      is RcMatrix3DOp -> engine.matrixOp(op.sub, resolve(op.args))
      is RcPaint3DState -> {
        val params = resolve(op.params)
        when (op.sub) {
          Rc3D.P3_CLEAR_DEPTH -> engine.clearDepth()
          Rc3D.P3_MATERIAL -> if (params.size >= 2) engine.setMaterial(params[0], params[1])
          Rc3D.P3_DEPTH_BIAS -> if (params.size >= 2) engine.setDepthBias(params[0], params[1])
        }
      }
      is RcSetLights3D -> engine.setLights(op.types, op.colors, resolve(op.params))
      is RcSetTexture3D -> setTexture(op.bitmapId, scope)
      is RcVectorExpression -> applyVectorExpression(op)
      is RcMeshPrimitive3D -> buildPrimitive(op)
      is RcMeshExpression3D -> buildExpression(op)
      is RcDrawMesh3D -> drawMesh(op, scope)
    }
  }

  /** A new frame starts when the clock the player drew it at moves. */
  private fun beginFrameIfNew() {
    val key = state.frameWallClockMillis * 31 + state.animationTimeSeconds.toRawBits().toLong()
    if (key == frameKey) return
    frameKey = key
    framesUsed = 0
    frameTexturePixels.clear()
  }

  private fun ensureSize(scope: RcExtensionDrawScope) {
    val size = scope.drawScope.size
    val scale = options.pixelScale
    engine.setSize(
      (size.width * scale).roundToInt().coerceAtLeast(1),
      (size.height * scale).roundToInt().coerceAtLeast(1),
    )
  }

  /** A variable that is not set reads as 0, as `RemoteContext.getFloat` returns for one. */
  private fun resolve(word: RcFloatWord): Float {
    val value = state.resolve(word)
    return if (value.isNaN() && word.isNaNEncoded) 0f else value
  }

  private fun resolve(words: List<RcFloatWord>): FloatArray =
    FloatArray(words.size) { resolve(words[it]) }

  private fun setTexture(bitmapId: Int, scope: RcExtensionDrawScope) {
    textureBitmapId = bitmapId
    val id = if (bitmapId == 0) 0 else state.drawId(bitmapId)
    val image = if (id == 0) null else scope.bitmap(id)
    textureImage = image
    if (image == null || image.width <= 0 || image.height <= 0) {
      engine.setTexture(null, 0, 0)
      return
    }
    // Read back once per bitmap: SET_TEXTURE_3D runs per mesh per frame, and a readback each
    // time would dominate a textured scene. A bitmap the document draws into is read once per
    // frame instead, since its contents change under the same image.
    val mutable = drawTargets?.contains(id) ?: true
    val cache = if (mutable) frameTexturePixels else texturePixels
    val pixels =
      cache.getOrPut(image) { IntArray(image.width * image.height).also { image.readPixels(it) } }
    engine.setTexture(pixels, image.width, image.height)
  }

  /**
   * `VECTOR_EXPRESSION`. Variables are resolved first, and only variables: operators, vector
   * opcodes and array references are NaN-boxed too and must reach the evaluator untouched. (The
   * reference's own `updateVariables` resolves the vector opcodes as variables and so evaluates
   * every vector program to zeros; this follows the experiments repo's players in not doing so.)
   */
  private fun applyVectorExpression(op: RcVectorExpression) {
    val program =
      op.program.map { word ->
        if (isResolvableToken(word)) RcFloatWord.literal(resolve(word)) else word
      }
    var lanes = vectorRpn.apply(program, vectorOut)
    if ((0 until lanes).any { !vectorOut[it].isFinite() }) {
      // A divide or normalise hit (near) zero: retry with the soft-domain denominator.
      vectorRpn.softDomain = true
      try {
        lanes = vectorRpn.apply(program, vectorOut)
      } finally {
        vectorRpn.softDomain = false
      }
    }
    for (k in 0 until op.dimension) {
      val v = if (k < lanes) vectorOut[k] else 0f
      state.setFloat(op.id + k, if (v.isFinite()) v else 0f)
    }
  }

  private fun buildPrimitive(op: RcMeshPrimitive3D) {
    val segments = resolve(op.segments)
    val channels = op.data.map { resolve(it) }
    val key = buildList {
      add(segments)
      channels.forEach { channel -> channel.forEach(::add) }
    }
    val mesh = cached(op, key) { RcPrimitive3D.build(op.type, segments, op.flags, channels) }
    if (mesh != null) define(op.id, mesh)
  }

  private fun buildExpression(op: RcMeshExpression3D) {
    val params = resolve(op.params)
    arrays.clear()
    // The expressions' own variable reads, and the contents of the arrays they index, become part
    // of the key, so a surface that follows the clock or an updated list rebuilds and a static one
    // never does.
    val key = buildList {
      params.forEach(::add)
      for (group in listOf(op.position, op.normal, op.uv)) {
        for (expression in group) {
          for (word in expression) {
            when {
              isArrayToken(word) -> {
                val id = word.bits and NAN_PAYLOAD_MASK
                arrays.getOrPut(id) { state.floatValues(id) }?.forEach(::add) ?: add(Float.NaN)
              }
              isResolvableToken(word) -> add(resolve(word))
            }
          }
        }
      }
    }
    val mesh = cached(op, key) { expressions.generate(op, params, ::resolve) }
    if (mesh != null) define(op.id, mesh)
  }

  private inline fun cached(
    op: Rc3DOperation,
    key: List<Float>,
    build: () -> Rc3DMeshData?,
  ): Rc3DMeshData? {
    builtMeshes[op]?.let { (builtFrom, mesh) -> if (builtFrom == key) return mesh }
    // A shape that cannot be built is a gap, not noise: remembered, so it fails once per input.
    val mesh = runCatching(build).getOrNull()
    builtMeshes[op] = key to mesh
    return mesh
  }

  /**
   * Mesh versions, bumped when an id is given different geometry; see [Rc3DMeshDraw.meshVersion].
   */
  private val meshVersions = mutableMapOf<Int, Pair<Rc3DMeshData, Long>>()
  private var nextMeshVersion = 1L
  private val passDraws = mutableMapOf<Int, MutableList<Rc3DMeshDraw>>()

  private fun define(id: Int, mesh: Rc3DMeshData) {
    engine.defineMesh(id, mesh)
    val current = meshVersions[id]
    if (current == null || current.first !== mesh) meshVersions[id] = mesh to nextMeshVersion++
  }

  override fun endPass(componentId: Int) {
    val draws = passDraws.remove(componentId) ?: return
    options.sceneListener?.onFrame(Rc3DFrame(componentId, draws))
  }

  private fun drawMesh(op: RcDrawMesh3D, scope: RcExtensionDrawScope) {
    val meshId = op.meshId
    val mesh = engine.mesh(meshId) ?: return
    engine.baseColorArgb = scope.paintColor
    val size = scope.drawScope.size
    var inPlayer = options.drawInPlayer
    options.sceneListener?.let { listener ->
      val pass = passDraws.getOrPut(scope.componentId) { mutableListOf() }
      val draw =
        Rc3DMeshDraw(
          meshId,
          mesh,
          engine.modelMatrix,
          engine.viewMatrix,
          engine.projectionMatrix,
          scope.paintColor,
          textureBitmapId,
          op.mode,
          size.width,
          size.height,
          componentId = scope.componentId,
          drawIndex = pass.size,
          meshVersion = meshVersions[meshId]?.second ?: 0L,
          texture = textureImage,
        )
      pass += draw
      listener.onDrawMesh(draw)
      if (!inPlayer) inPlayer = listener.keepInPlayer(draw)
    }
    if (!inPlayer) return
    if (useGpu(op.mode)) drawGpu(meshId, op.mode, scope) else drawSoftware(meshId, op.mode, scope)
  }

  private fun useGpu(mode: Int): Boolean {
    if ((mode and Rc3D.MODE_WIREFRAME) != 0) return false // needs the depth buffer
    return when (options.renderer) {
      Rc3DRenderer.Software -> false
      Rc3DRenderer.Gpu -> true
      Rc3DRenderer.Auto -> (mode shr 1) != Rc3D.MODE_BACKEND_SOFTWARE
    }
  }

  private fun drawSoftware(meshId: Int, mode: Int, scope: RcExtensionDrawScope) {
    engine.drawMesh(meshId, mode)
    val w = engine.width
    val h = engine.height
    // Never reused within a frame. Past MAX_FRAMES composites a frame's bitmaps are allocated
    // fresh rather than kept, so a mesh-heavy document costs memory only while it draws.
    val index = framesUsed++
    val reusable = frames.getOrNull(index)?.takeIf { it.width == w && it.height == h }
    val current = reusable ?: RcArgbFrame(w, h)
    if (reusable == null && index < MAX_FRAMES) {
      if (index < frames.size) frames[index] = current else frames += current
    }
    current.update(engine.colorBuffer)
    // Only this mesh's pixels: an earlier mesh is already composited, and repainting it would
    // cover 2D drawn since. Depth stays, so this mesh is still hidden behind earlier ones.
    engine.clearColor()
    // Composited after every mesh, as the reference blits: a mesh, then a label, then another
    // mesh must interleave in that order.
    val size = scope.drawScope.size
    scope.drawScope.drawImage(
      current.image,
      srcOffset = IntOffset.Zero,
      srcSize = IntSize(w, h),
      dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    )
  }

  private fun drawGpu(meshId: Int, mode: Int, scope: RcExtensionDrawScope) {
    val count =
      engine.buildScreenVertices(meshId, screenMesh, (mode and Rc3D.MODE_SMOOTH_MASK) != 0)
    if (count < 3) return
    val texture = textureImage?.takeIf { screenMesh.hasUv }
    val inverse = 1f / options.pixelScale
    scope.drawScope.scale(inverse, inverse, Offset.Zero) {
      rcDrawTriangles(
        screenMesh.positions,
        screenMesh.colors,
        if (texture != null) screenMesh.uvs else null,
        count,
        texture,
      )
    }
  }

  override fun dispose() {
    frames.clear()
    texturePixels.clear()
    frameTexturePixels.clear()
    builtMeshes.clear()
    definedMeshes.clear()
  }

  internal companion object {
    /** How many composite bitmaps are kept from frame to frame. */
    private const val MAX_FRAMES = 32
    private const val DRAW_ID_MASK = 0xffff
    private const val DRAW_ID_DEREFERENCE = 0x40000000
    private const val ID_REGION_MASK = 0x700000
    private const val ID_REGION_ARRAY = 0x200000

    /**
     * A NaN-boxed word that names a variable, as opposed to an operator, a vector opcode or an
     * array reference.
     */
    private const val NAN_PAYLOAD_MASK = 0x7fffff

    /** A NaN-boxed reference to one of the document's float arrays. */
    fun isArrayToken(word: RcFloatWord): Boolean =
      word.isNaNEncoded &&
        !RcVectorRpn.isOperator(word) &&
        (word.bits and ID_REGION_MASK) == ID_REGION_ARRAY

    fun isResolvableToken(word: RcFloatWord): Boolean {
      if (!word.isNaNEncoded) return false
      if (RcVectorRpn.isOperator(word)) return false
      return (word.bits and ID_REGION_MASK) != ID_REGION_ARRAY
    }
  }
}

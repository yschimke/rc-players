@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ee.schimke.composeai.rcplayer.threed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.FLOATS_PER_VERTEX
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.MAX_LIGHTS
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint

internal actual class RcDepthRenderer actual constructor() {
  internal val webgl = RcWebGlDepth()

  actual fun canDraw(scope: DrawScope): Boolean = webgl.available

  actual fun beginFrame() {
    webgl.beginFrame()
  }

  actual fun clearDepth() {
    webgl.clearDepth()
  }

  actual fun draw(
    scope: DrawScope,
    size: Size,
    width: Int,
    height: Int,
    mesh: Rc3DMeshData,
    engine: RcSoftware3D,
    smooth: Boolean,
    texture: ImageBitmap?,
  ): Boolean = webgl.draw(scope, size, width, height, mesh, engine, smooth, texture)

  actual fun dispose() {
    webgl.dispose()
  }
}

/**
 * The WebGL2 depth renderer: [RcGlesDepth]'s port to the browser.
 *
 * Skiko draws through its own WebGL2 context, which it does not expose, and a canvas holds one
 * context, so this renders in a context of its own on an `OffscreenCanvas` and copies each mesh
 * back: an offscreen framebuffer whose 24-bit depth buffer lasts the depth pass, as the software
 * z-buffer does, then `readPixels` straight into a Skia bitmap's pixels in Skiko's Wasm memory (no
 * copy through Kotlin), drawn into the canvas like the software rasterizer's composite. So a mesh
 * still lands where it sits in the document, between the 2D drawn before and after it, at the cost
 * of a GPU-to-CPU read per mesh.
 *
 * The shaders are [Rc3DDepthShaders], the ones Android's renderer runs. GL's rows run bottom-up and
 * Skia's top-down, so the projection is mirrored vertically before it reaches the shader and the
 * read rows come out in Skia's order; the mirror reverses the winding, so the front face is
 * clockwise here where it is counter-clockwise on Android.
 */
internal class RcWebGlDepth {
  private var gl: WebGl2? = null
  private var disabled = false
  private var width = 0
  private var height = 0
  private var fbo: JsAny? = null
  private var colorBuffer: JsAny? = null
  private var depthBuffer: JsAny? = null
  private var program: JsAny? = null
  private var depthDirty = true
  private var drawsThisFrame = 0

  /** The pixels [draw] reads each mesh into; drawing an image snapshots them, so one is enough. */
  private var readback: Bitmap? = null

  private val buffers = LinkedHashMap<Rc3DMeshData, Pair<JsAny, Rc3DShaderMeshData>>()
  private val uniforms = HashMap<String, JsAny?>()
  private var texture: JsAny? = null
  private var textureBitmap: Bitmap? = null
  private var textureGeneration = -1
  private var textureStaging: Bitmap? = null

  /** What the last [render] read back: `left, top, right, bottom` in the bitmap's rows. */
  private val rect = IntArray(4)
  private val matrix = FloatArray(16)
  private val light = FloatArray(7)
  private val lightPos = FloatArray(MAX_LIGHTS * 4)
  private val lightColor = FloatArray(MAX_LIGHTS * 4)
  private val matrixArray: JsAny = float32Array(16)
  private val lightArray: JsAny = float32Array(MAX_LIGHTS * 4)

  /** False once setup failed or the context was lost: every later draw is declined. */
  val available: Boolean
    get() = !disabled && gl?.isContextLost() != true

  /** Meshes drawn in this frame, for the browser tests. */
  val compositesThisFrame: Int
    get() = drawsThisFrame

  fun beginFrame() {
    drawsThisFrame = 0
  }

  fun clearDepth() {
    depthDirty = true
  }

  fun draw(
    scope: DrawScope,
    size: Size,
    width: Int,
    height: Int,
    mesh: Rc3DMeshData,
    engine: RcSoftware3D,
    smooth: Boolean,
    texture: ImageBitmap?,
  ): Boolean {
    if (!available || width <= 0 || height <= 0) return false
    if (drawsThisFrame >= Rc3DDepthLimits.MAX_DRAWS_PER_FRAME) return false
    engine.prepareShading()
    if (engine.eyeLightCount > MAX_LIGHTS) return false
    return try {
      val gl = ensureContext() ?: return false
      if (!ensureTarget(gl, width, height)) return false
      val bitmap = render(gl, mesh, engine, smooth, texture) ?: return false
      drawsThisFrame++
      val (left, top, right, bottom) = rect
      if (left >= right || top >= bottom) return true // off screen: depth only
      // GL pixels map onto the drawing's units by the scale between them, as the software
      // composite does, and only the rectangle that was read is drawn.
      scope.scale(size.width / width, size.height / height, Offset.Zero) {
        drawImage(
          bitmap.asComposeImageBitmap(),
          srcOffset = IntOffset(left, top),
          srcSize = IntSize(right - left, bottom - top),
          dstOffset = IntOffset(left, top),
          dstSize = IntSize(right - left, bottom - top),
        )
      }
      true
    } catch (e: Throwable) {
      println("Rc3D: WebGL2 depth renderer failed; 3D draws use the software rasterizer: $e")
      disabled = true
      false
    }
  }

  private fun render(
    gl: WebGl2,
    mesh: Rc3DMeshData,
    engine: RcSoftware3D,
    smooth: Boolean,
    texture: ImageBitmap?,
  ): Bitmap? {
    val (vbo, data) = upload(gl, mesh) ?: return null
    // The mesh, into the offscreen target: depth kept since the last clear, colour cleared so the
    // composite carries this mesh alone.
    gl.bindFramebuffer(GL_FRAMEBUFFER, fbo)
    gl.viewport(0, 0, width, height)
    gl.clearColor(0f, 0f, 0f, 0f)
    var clear = GL_COLOR_BUFFER_BIT
    if (depthDirty) {
      gl.clearDepth(1f)
      clear = clear or GL_DEPTH_BUFFER_BIT
      depthDirty = false
    }
    gl.clear(clear)
    gl.enable(GL_DEPTH_TEST)
    gl.depthFunc(GL_LESS)
    gl.depthMask(true)
    gl.disable(GL_BLEND)
    // The engine keeps a triangle that is counter-clockwise in normalized device coordinates; the
    // vertical mirror below turns that clockwise.
    gl.enable(GL_CULL_FACE)
    gl.frontFace(GL_CW)
    gl.cullFace(GL_BACK)
    val biasConstant = engine.depthBiasConstant
    val biasSlope = engine.depthBiasSlope
    if (biasConstant != 0f || biasSlope != 0f) {
      gl.enable(GL_POLYGON_OFFSET_FILL)
      // Window-z units to GL's: units are the smallest resolvable step of a 24-bit buffer.
      gl.polygonOffset(biasSlope, biasConstant * DEPTH_STEPS)
    } else {
      gl.disable(GL_POLYGON_OFFSET_FILL)
    }

    gl.useProgram(program)
    setUniforms(gl, engine, smooth && data.hasNormals, texture?.takeIf { data.hasUv })
    gl.bindBuffer(GL_ARRAY_BUFFER, vbo)
    val stride = FLOATS_PER_VERTEX * 4
    for ((location, offset, count) in Rc3DDepthShaders.ATTRIBUTES) {
      gl.enableVertexAttribArray(location)
      gl.vertexAttribPointer(location, count, GL_FLOAT, false, stride, offset * 4)
    }
    gl.drawArrays(GL_TRIANGLES, 0, data.vertexCount)
    for ((location, _, _) in Rc3DDepthShaders.ATTRIBUTES) gl.disableVertexAttribArray(location)
    gl.bindBuffer(GL_ARRAY_BUFFER, null)

    // The colour, straight into the bitmap's pixels: premultiplied RGBA, as the shader writes it.
    // Only the rectangle the mesh's bounds project to is read: the read is a GPU round trip, and
    // a small mesh in a large viewport costs its own area, as on the software path. The mirror
    // makes GL's rows Skia's, so the rectangle is the same in both.
    if (!data.screenRect(engine.modelViewProjection, width, height, rect)) {
      rect[0] = 0
      rect[1] = 0
      rect[2] = width
      rect[3] = height
    }
    val (left, top, right, bottom) = rect
    val bitmap = readback ?: return null
    if (left < right && top < bottom) {
      val pixels = bitmap.peekPixels() ?: return null
      val start = (top * width + left) * 4
      val length = ((bottom - 1 - top) * width + (right - left)) * 4
      val view = skikoHeapBytes(pixels.addr + start, length) ?: return null
      gl.pixelStorei(GL_PACK_ALIGNMENT, 4)
      gl.pixelStorei(GL_PACK_ROW_LENGTH, width)
      gl.readPixels(left, top, right - left, bottom - top, GL_RGBA, GL_UNSIGNED_BYTE, view)
      gl.pixelStorei(GL_PACK_ROW_LENGTH, 0)
    }
    gl.bindFramebuffer(GL_FRAMEBUFFER, null)
    bitmap.notifyPixelsChanged()
    return bitmap
  }

  private fun setUniforms(
    gl: WebGl2,
    engine: RcSoftware3D,
    smooth: Boolean,
    texture: ImageBitmap?,
  ) {
    // y' = -y: row 1 of the column-major projection, negated.
    engine.modelViewProjection.copyInto(matrix)
    for (column in 0 until 4) matrix[column * 4 + 1] = -matrix[column * 4 + 1]
    gl.uniformMatrix4fv(uniform(gl, "uMvp"), false, fill(matrixArray, matrix))
    gl.uniformMatrix4fv(uniform(gl, "uMv"), false, fill(matrixArray, engine.modelView))
    gl.uniformMatrix4fv(uniform(gl, "uNm"), false, fill(matrixArray, engine.normalMatrix))
    val base = engine.baseColorArgb
    gl.uniform4f(
      uniform(gl, "uBase"),
      ((base shr 16) and 0xFF) / 255f,
      ((base shr 8) and 0xFF) / 255f,
      (base and 0xFF) / 255f,
      ((base ushr 24) and 0xFF) / 255f,
    )
    gl.uniform3f(
      uniform(gl, "uShading"),
      if (smooth) 1f else 0f,
      engine.specularStrength,
      engine.specularShininess,
    )
    val lights = engine.eyeLightCount
    lightPos.fill(0f)
    lightColor.fill(0f)
    for (i in 0 until lights) {
      engine.eyeLight(i, light)
      lightPos[i * 4] = light[1]
      lightPos[i * 4 + 1] = light[2]
      lightPos[i * 4 + 2] = light[3]
      lightPos[i * 4 + 3] = if (light[0].toInt() == Rc3D.LIGHT_POINT) 1f else 0f
      lightColor[i * 4] = light[4]
      lightColor[i * 4 + 1] = light[5]
      lightColor[i * 4 + 2] = light[6]
    }
    gl.uniform1i(uniform(gl, "uLightCount"), lights)
    gl.uniform4fv(uniform(gl, "uLightPos"), fill(lightArray, lightPos))
    gl.uniform4fv(uniform(gl, "uLightColor"), fill(lightArray, lightColor))
    val textured = texture != null && bindTexture(gl, texture)
    gl.uniform1f(uniform(gl, "uTextured"), if (textured) 1f else 0f)
    gl.uniform1i(uniform(gl, "uTexture"), 0)
  }

  private fun uniform(gl: WebGl2, name: String): JsAny? =
    uniforms.getOrPut(name) { gl.getUniformLocation(program, name) }

  /**
   * Upload [image] once, and again when a bitmap the document draws into changes: premultiplied
   * RGBA, as Android's `GLUtils` uploads, read from Skiko's memory without a copy through Kotlin.
   */
  private fun bindTexture(gl: WebGl2, image: ImageBitmap): Boolean {
    val source = image.asSkiaBitmap()
    if (texture == null) texture = gl.createTexture()
    gl.activeTexture(GL_TEXTURE0)
    gl.bindTexture(GL_TEXTURE_2D, texture)
    if (source !== textureBitmap || source.generationId != textureGeneration) {
      val w = source.width
      val h = source.height
      if (w <= 0 || h <= 0) return false
      val staging =
        textureStaging?.takeIf { it.width == w && it.height == h }
          ?: Bitmap().also {
            textureStaging?.close()
            it.allocPixels(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
            textureStaging = it
          }
      // Any colour type in, premultiplied RGBA out.
      val snapshot = Image.makeFromBitmap(source)
      val canvas = Canvas(staging)
      val paint = Paint().apply { blendMode = BlendMode.SRC }
      try {
        canvas.drawImage(snapshot, 0f, 0f, paint)
      } finally {
        paint.close()
        canvas.close()
        snapshot.close()
      }
      val pixels = staging.peekPixels() ?: return false
      val view = skikoHeapBytes(pixels.addr, w * h * 4) ?: return false
      gl.pixelStorei(GL_UNPACK_ALIGNMENT, 4)
      gl.texImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, view)
      // Nearest, as the software rasterizer samples: a filtered texture would soften every edge.
      gl.texParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
      gl.texParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
      gl.texParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
      gl.texParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
      textureBitmap = source
      textureGeneration = source.generationId
    }
    return true
  }

  private fun upload(gl: WebGl2, mesh: Rc3DMeshData): Pair<JsAny, Rc3DShaderMeshData>? {
    buffers[mesh]?.let {
      return it
    }
    // No vertex cap, unlike the shader mesh's: a pass that started here has to take every mesh,
    // since declining one would draw it on the software rasterizer, against a depth buffer that
    // holds none of the pass's earlier meshes. WebGL buffers take what the engine can build.
    val data = Rc3DShaderMeshData(mesh)
    val buffer = gl.createBuffer() ?: return null
    gl.bindBuffer(GL_ARRAY_BUFFER, buffer)
    gl.bufferData(
      GL_ARRAY_BUFFER,
      fill(float32Array(data.vertices.size), data.vertices),
      GL_STATIC_DRAW,
    )
    gl.bindBuffer(GL_ARRAY_BUFFER, null)
    val entry = buffer to data
    buffers[mesh] = entry
    // A deforming mesh brings new geometry every frame: keep only the recent ones.
    while (buffers.size > MAX_BUFFERS) {
      val oldest = buffers.keys.first()
      gl.deleteBuffer(buffers.remove(oldest)!!.first)
    }
    return entry
  }

  private fun ensureContext(): WebGl2? {
    gl?.let {
      return it
    }
    val context = createWebGl2Context()
    if (context == null) {
      fail("no WebGL2 context")
      return null
    }
    // A program that does not compile is a platform problem, not a document one: decline for good.
    program = program(context, Rc3DDepthShaders.MESH_VERTEX, Rc3DDepthShaders.MESH_FRAGMENT)
    gl = context
    return context
  }

  /** The offscreen colour and depth buffers, and the readback bitmap, at [width] by [height]. */
  private fun ensureTarget(gl: WebGl2, width: Int, height: Int): Boolean {
    if (fbo != null && this.width == width && this.height == height) return true
    releaseTarget(gl)
    colorBuffer = gl.createRenderbuffer()
    gl.bindRenderbuffer(GL_RENDERBUFFER, colorBuffer)
    gl.renderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, width, height)
    depthBuffer = gl.createRenderbuffer()
    gl.bindRenderbuffer(GL_RENDERBUFFER, depthBuffer)
    gl.renderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height)
    gl.bindRenderbuffer(GL_RENDERBUFFER, null)
    fbo = gl.createFramebuffer()
    gl.bindFramebuffer(GL_FRAMEBUFFER, fbo)
    gl.framebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorBuffer)
    gl.framebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthBuffer)
    val status = gl.checkFramebufferStatus(GL_FRAMEBUFFER)
    gl.bindFramebuffer(GL_FRAMEBUFFER, null)
    if (status != GL_FRAMEBUFFER_COMPLETE) return fail("framebuffer status $status")
    readback =
      Bitmap().also {
        it.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
      }
    this.width = width
    this.height = height
    depthDirty = true
    return true
  }

  private fun program(gl: WebGl2, vertex: String, fragment: String): JsAny? {
    fun shader(type: Int, source: String): JsAny? {
      val id = gl.createShader(type)
      gl.shaderSource(id, source)
      gl.compileShader(id)
      if (!isTrue(gl.getShaderParameter(id, GL_COMPILE_STATUS))) {
        throw IllegalStateException("shader: " + gl.getShaderInfoLog(id))
      }
      return id
    }
    val id = gl.createProgram()
    gl.attachShader(id, shader(GL_VERTEX_SHADER, vertex))
    gl.attachShader(id, shader(GL_FRAGMENT_SHADER, fragment))
    for ((location, name) in Rc3DDepthShaders.ATTRIBUTE_NAMES) {
      gl.bindAttribLocation(id, location, name)
    }
    gl.linkProgram(id)
    if (!isTrue(gl.getProgramParameter(id, GL_LINK_STATUS))) {
      throw IllegalStateException("link: " + gl.getProgramInfoLog(id))
    }
    return id
  }

  private fun fail(what: String): Boolean {
    println("Rc3D: WebGL2 depth renderer unavailable ($what); 3D draws use the software rasterizer")
    disabled = true
    return false
  }

  private fun releaseTarget(gl: WebGl2) {
    fbo?.let { gl.deleteFramebuffer(it) }
    colorBuffer?.let { gl.deleteRenderbuffer(it) }
    depthBuffer?.let { gl.deleteRenderbuffer(it) }
    fbo = null
    colorBuffer = null
    depthBuffer = null
    readback?.close()
    readback = null
  }

  fun dispose() {
    gl?.let { gl ->
      releaseTarget(gl)
      for ((buffer, _) in buffers.values) gl.deleteBuffer(buffer)
      texture?.let { gl.deleteTexture(it) }
      program?.let { gl.deleteProgram(it) }
      // Frees the context now rather than when the collector finds the canvas.
      gl.getExtension("WEBGL_lose_context")?.let { loseContext(it) }
    }
    gl = null
    buffers.clear()
    uniforms.clear()
    program = null
    texture = null
    textureBitmap = null
    textureStaging?.close()
    textureStaging = null
  }

  private companion object {
    const val MAX_BUFFERS = 16
    const val DEPTH_STEPS = 16_777_215f

    const val GL_DEPTH_BUFFER_BIT = 0x0100
    const val GL_COLOR_BUFFER_BIT = 0x4000
    const val GL_TRIANGLES = 0x0004
    const val GL_CW = 0x0900
    const val GL_BACK = 0x0405
    const val GL_CULL_FACE = 0x0B44
    const val GL_DEPTH_TEST = 0x0B71
    const val GL_BLEND = 0x0BE2
    const val GL_POLYGON_OFFSET_FILL = 0x8037
    const val GL_LESS = 0x0201
    const val GL_UNPACK_ALIGNMENT = 0x0CF5
    const val GL_PACK_ALIGNMENT = 0x0D05
    const val GL_PACK_ROW_LENGTH = 0x0D02
    const val GL_TEXTURE_2D = 0x0DE1
    const val GL_UNSIGNED_BYTE = 0x1401
    const val GL_FLOAT = 0x1406
    const val GL_RGBA = 0x1908
    const val GL_RGBA8 = 0x8058
    const val GL_NEAREST = 0x2600
    const val GL_TEXTURE_MAG_FILTER = 0x2800
    const val GL_TEXTURE_MIN_FILTER = 0x2801
    const val GL_TEXTURE_WRAP_S = 0x2802
    const val GL_TEXTURE_WRAP_T = 0x2803
    const val GL_CLAMP_TO_EDGE = 0x812F
    const val GL_TEXTURE0 = 0x84C0
    const val GL_ARRAY_BUFFER = 0x8892
    const val GL_STATIC_DRAW = 0x88E4
    const val GL_FRAGMENT_SHADER = 0x8B30
    const val GL_VERTEX_SHADER = 0x8B31
    const val GL_COMPILE_STATUS = 0x8B81
    const val GL_LINK_STATUS = 0x8B82
    const val GL_FRAMEBUFFER = 0x8D40
    const val GL_RENDERBUFFER = 0x8D41
    const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
    const val GL_COLOR_ATTACHMENT0 = 0x8CE0
    const val GL_DEPTH_ATTACHMENT = 0x8D00
    const val GL_DEPTH_COMPONENT24 = 0x81A6
  }
}

/** The part of `WebGL2RenderingContext` the depth renderer calls. */
internal external interface WebGl2 : JsAny {
  fun isContextLost(): Boolean

  fun getExtension(name: String): JsAny?

  fun createShader(type: Int): JsAny?

  fun shaderSource(shader: JsAny?, source: String)

  fun compileShader(shader: JsAny?)

  fun getShaderParameter(shader: JsAny?, name: Int): JsAny?

  fun getShaderInfoLog(shader: JsAny?): String?

  fun createProgram(): JsAny?

  fun deleteProgram(program: JsAny?)

  fun attachShader(program: JsAny?, shader: JsAny?)

  fun bindAttribLocation(program: JsAny?, index: Int, name: String)

  fun linkProgram(program: JsAny?)

  fun getProgramParameter(program: JsAny?, name: Int): JsAny?

  fun getProgramInfoLog(program: JsAny?): String?

  fun useProgram(program: JsAny?)

  fun getUniformLocation(program: JsAny?, name: String): JsAny?

  fun uniformMatrix4fv(location: JsAny?, transpose: Boolean, data: JsAny)

  fun uniform4fv(location: JsAny?, data: JsAny)

  fun uniform4f(location: JsAny?, x: Float, y: Float, z: Float, w: Float)

  fun uniform3f(location: JsAny?, x: Float, y: Float, z: Float)

  fun uniform1f(location: JsAny?, x: Float)

  fun uniform1i(location: JsAny?, x: Int)

  fun createBuffer(): JsAny?

  fun deleteBuffer(buffer: JsAny?)

  fun bindBuffer(target: Int, buffer: JsAny?)

  fun bufferData(target: Int, data: JsAny, usage: Int)

  fun enableVertexAttribArray(index: Int)

  fun disableVertexAttribArray(index: Int)

  fun vertexAttribPointer(
    index: Int,
    size: Int,
    type: Int,
    normalized: Boolean,
    stride: Int,
    offset: Int,
  )

  fun drawArrays(mode: Int, first: Int, count: Int)

  fun createTexture(): JsAny?

  fun deleteTexture(texture: JsAny?)

  fun activeTexture(unit: Int)

  fun bindTexture(target: Int, texture: JsAny?)

  fun texParameteri(target: Int, name: Int, value: Int)

  fun pixelStorei(name: Int, value: Int)

  fun texImage2D(
    target: Int,
    level: Int,
    internalFormat: Int,
    width: Int,
    height: Int,
    border: Int,
    format: Int,
    type: Int,
    pixels: JsAny?,
  )

  fun createRenderbuffer(): JsAny?

  fun deleteRenderbuffer(buffer: JsAny?)

  fun bindRenderbuffer(target: Int, buffer: JsAny?)

  fun renderbufferStorage(target: Int, format: Int, width: Int, height: Int)

  fun createFramebuffer(): JsAny?

  fun deleteFramebuffer(buffer: JsAny?)

  fun bindFramebuffer(target: Int, buffer: JsAny?)

  fun framebufferRenderbuffer(target: Int, attachment: Int, renderbufferTarget: Int, buffer: JsAny?)

  fun checkFramebufferStatus(target: Int): Int

  fun viewport(x: Int, y: Int, width: Int, height: Int)

  fun clearColor(r: Float, g: Float, b: Float, a: Float)

  fun clearDepth(depth: Float)

  fun clear(mask: Int)

  fun enable(capability: Int)

  fun disable(capability: Int)

  fun depthFunc(function: Int)

  fun depthMask(flag: Boolean)

  fun frontFace(mode: Int)

  fun cullFace(mode: Int)

  fun polygonOffset(factor: Float, units: Float)

  fun readPixels(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    format: Int,
    type: Int,
    pixels: JsAny?,
  )
}

/**
 * A WebGL2 context of the depth renderer's own: on an `OffscreenCanvas` where there is one, else on
 * a detached `<canvas>`. Never antialiased (the software rasterizer is not) and no default depth
 * buffer, since it draws into its own framebuffer. Null where WebGL2 is unavailable.
 */
private fun createWebGl2Context(): WebGl2? =
  js(
    """
    (() => {
      try {
        const attributes = {
          alpha: true,
          antialias: false,
          depth: false,
          stencil: false,
          premultipliedAlpha: true,
          preserveDrawingBuffer: false,
          powerPreference: 'high-performance',
        };
        let canvas = null;
        if (typeof OffscreenCanvas !== 'undefined') canvas = new OffscreenCanvas(1, 1);
        else if (typeof document !== 'undefined') canvas = document.createElement('canvas');
        return canvas ? canvas.getContext('webgl2', attributes) : null;
      } catch (e) {
        return null;
      }
    })()
    """
  )

private fun loseContext(extension: JsAny): Unit = js("extension.loseContext()")

private fun isTrue(value: JsAny?): Boolean = js("value === true")

private fun float32Array(length: Int): JsAny = js("new Float32Array(length)")

private fun setFloat(array: JsAny, index: Int, value: Float): Unit = js("array[index] = value")

private fun fill(array: JsAny, values: FloatArray): JsAny {
  for (i in values.indices) setFloat(array, i, values[i])
  return array
}

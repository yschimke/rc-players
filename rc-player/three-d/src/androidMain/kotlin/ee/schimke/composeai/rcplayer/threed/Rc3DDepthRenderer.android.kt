package ee.schimke.composeai.rcplayer.threed

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.FLOATS_PER_VERTEX
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.MAX_LIGHTS
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.MAX_VERTICES
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal actual class RcDepthRenderer actual constructor() {
  internal val gles: RcGlesDepth? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) RcGlesDepth() else null

  actual fun canDraw(scope: DrawScope): Boolean =
    gles?.available == true && scope.drawContext.canvas.nativeCanvas.isHardwareAccelerated

  actual fun beginFrame() {
    gles?.beginFrame()
  }

  actual fun clearDepth() {
    gles?.clearDepth()
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
  ): Boolean {
    val gles = gles ?: return false
    if (!canDraw(scope)) return false
    return gles.draw(scope, size, width, height, mesh, engine, smooth, texture)
  }

  actual fun dispose() {
    gles?.dispose()
  }
}

/**
 * The OpenGL ES depth renderer. One EGL context per session renders each mesh into an offscreen
 * framebuffer whose depth buffer lasts the depth pass, as the software rasterizer's z-buffer does.
 * After each mesh its colour is copied to an `ImageReader`'s surface and the frame is drawn into
 * the canvas as a hardware bitmap, so the mesh lands where it sits in the document, between the 2D
 * drawn before and after it. Projection, culling (GL's own, on the projected winding) and lighting
 * follow the engine: the shaders repeat `litColor` per vertex, from the same per-face layout as the
 * shader mesh ([Rc3DShaderMeshData]).
 *
 * The context is made current on the drawing thread for each draw and released after it; HWUI's
 * own context lives on its render thread, so the two never share a thread.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class RcGlesDepth {
  private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
  private var context: EGLContext = EGL14.EGL_NO_CONTEXT
  private var config: EGLConfig? = null
  private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
  private var reader: ImageReader? = null
  private var width = 0
  private var height = 0
  private var fbo = 0
  private var colorTexture = 0
  private var depthBuffer = 0
  private var meshProgram = 0
  private var blitProgram = 0
  private var disabled = false
  private var depthDirty = true

  /** Composites still on screen: this frame's and the last's. Older ones go back to the reader. */
  private var current = mutableListOf<Image>()
  private var previous = mutableListOf<Image>()

  private val buffers = LinkedHashMap<Rc3DMeshData, Pair<Int, Rc3DShaderMeshData>>()
  private var textureBitmap: Bitmap? = null
  private var textureGeneration = -1
  private var textureId = 0
  private val light = FloatArray(7)
  private val lightPos = FloatArray(MAX_LIGHTS * 4)
  private val lightColor = FloatArray(MAX_LIGHTS * 4)

  /** False once setup failed: every later draw would be declined. */
  val available: Boolean
    get() = !disabled

  /** Images drawn in this frame, for the device tests. */
  val compositesThisFrame: Int
    get() = current.size

  fun beginFrame() {
    previous.forEach { it.close() }
    previous = current
    current = mutableListOf()
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
    if (disabled || width <= 0 || height <= 0) return false
    if (current.size >= MAX_COMPOSITES_PER_FRAME) return false
    engine.prepareShading()
    if (engine.eyeLightCount > MAX_LIGHTS) return false
    return try {
      if (!ensureContext() || !ensureTarget(width, height)) return false
      if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return fail("eglMakeCurrent")
      try {
        val image = render(mesh, engine, smooth, texture) ?: return false
        current += image
        val buffer = image.hardwareBuffer ?: return false
        val bitmap =
          Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB)) ?: return false
        buffer.close()
        // GL pixels map onto the drawing's units by the scale between them, as the software
        // composite does.
        scope.scale(size.width / width, size.height / height, Offset.Zero) {
          drawImage(bitmap.asImageBitmap())
        }
        true
      } finally {
        EGL14.eglMakeCurrent(
          display,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_CONTEXT,
        )
      }
    } catch (e: RuntimeException) {
      Log.w(TAG, "GLES depth renderer failed; 3D draws use the software rasterizer", e)
      disabled = true
      false
    }
  }

  private fun render(
    mesh: Rc3DMeshData,
    engine: RcSoftware3D,
    smooth: Boolean,
    texture: ImageBitmap?,
  ): Image? {
    val (vbo, data) = upload(mesh) ?: return null
    // The mesh, into the offscreen target: depth kept since the last clear, colour cleared so the
    // composite carries this mesh alone.
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
    GLES30.glViewport(0, 0, width, height)
    GLES30.glClearColor(0f, 0f, 0f, 0f)
    var clear = GLES30.GL_COLOR_BUFFER_BIT
    if (depthDirty) {
      GLES30.glClearDepthf(1f)
      clear = clear or GLES30.GL_DEPTH_BUFFER_BIT
      depthDirty = false
    }
    GLES30.glClear(clear)
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    GLES30.glDepthFunc(GLES30.GL_LESS)
    GLES30.glDepthMask(true)
    GLES30.glDisable(GLES30.GL_BLEND)
    // The engine keeps a triangle that is counter-clockwise in normalized device coordinates; GL
    // reads winding there too, so a mirroring projection culls the same faces.
    GLES30.glEnable(GLES30.GL_CULL_FACE)
    GLES30.glFrontFace(GLES30.GL_CCW)
    GLES30.glCullFace(GLES30.GL_BACK)
    val biasConstant = engine.depthBiasConstant
    val biasSlope = engine.depthBiasSlope
    if (biasConstant != 0f || biasSlope != 0f) {
      GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
      // Window-z units to GL's: units are the smallest resolvable step of a 24-bit buffer.
      GLES30.glPolygonOffset(biasSlope, biasConstant * DEPTH_STEPS)
    } else {
      GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
    }

    GLES30.glUseProgram(meshProgram)
    uniforms(engine, smooth && data.hasNormals, texture?.takeIf { data.hasUv })
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
    val stride = FLOATS_PER_VERTEX * 4
    for ((location, offset, count) in ATTRIBUTES) {
      GLES30.glEnableVertexAttribArray(location)
      GLES30.glVertexAttribPointer(location, count, GLES30.GL_FLOAT, false, stride, offset * 4)
    }
    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, data.vertexCount)
    for ((location, _, _) in ATTRIBUTES) GLES30.glDisableVertexAttribArray(location)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

    // The colour, onto the reader's surface, as one composite.
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    GLES30.glViewport(0, 0, width, height)
    GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    GLES30.glDisable(GLES30.GL_CULL_FACE)
    GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    GLES30.glUseProgram(blitProgram)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(blitProgram, "uColor"), 0)
    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    // The bitmap is sampled by HWUI with no fence to wait on, so the pixels must be done first.
    GLES30.glFinish()
    if (!EGL14.eglSwapBuffers(display, surface)) {
      fail("eglSwapBuffers")
      return null
    }
    return reader?.acquireNextImage()
  }

  private fun uniforms(engine: RcSoftware3D, smooth: Boolean, texture: ImageBitmap?) {
    val p = meshProgram
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(p, "uMvp"), 1, false, engine.modelViewProjection, 0)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(p, "uMv"), 1, false, engine.modelView, 0)
    val base = engine.baseColorArgb
    GLES30.glUniform4f(
      GLES30.glGetUniformLocation(p, "uBase"),
      ((base shr 16) and 0xFF) / 255f,
      ((base shr 8) and 0xFF) / 255f,
      (base and 0xFF) / 255f,
      ((base ushr 24) and 0xFF) / 255f,
    )
    GLES30.glUniform3f(
      GLES30.glGetUniformLocation(p, "uShading"),
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
    GLES30.glUniform1i(GLES30.glGetUniformLocation(p, "uLightCount"), lights)
    GLES30.glUniform4fv(GLES30.glGetUniformLocation(p, "uLightPos"), MAX_LIGHTS, lightPos, 0)
    GLES30.glUniform4fv(GLES30.glGetUniformLocation(p, "uLightColor"), MAX_LIGHTS, lightColor, 0)
    val textured = texture != null && bindTexture(texture)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(p, "uTextured"), if (textured) 1f else 0f)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(p, "uTexture"), 0)
  }

  /** Upload [image] once, and again when a bitmap the document draws into changes. */
  private fun bindTexture(image: ImageBitmap): Boolean {
    val source = image.asAndroidBitmap()
    if (textureId == 0) {
      val ids = IntArray(1)
      GLES30.glGenTextures(1, ids, 0)
      textureId = ids[0]
    }
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
    if (source !== textureBitmap || source.generationId != textureGeneration) {
      // GLUtils reads pixels from memory: a hardware bitmap is copied out first.
      val readable =
        if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false)
        else source
      GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, readable, 0)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
      textureBitmap = source
      textureGeneration = source.generationId
    }
    return true
  }

  private fun upload(mesh: Rc3DMeshData): Pair<Int, Rc3DShaderMeshData>? {
    buffers[mesh]?.let {
      return it
    }
    if (mesh.indices.size > MAX_VERTICES) return null
    val data = Rc3DShaderMeshData(mesh)
    val ids = IntArray(1)
    GLES30.glGenBuffers(1, ids, 0)
    val bytes =
      ByteBuffer.allocateDirect(data.vertices.size * 4).order(ByteOrder.nativeOrder()).apply {
        asFloatBuffer().put(data.vertices)
      }
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, bytes.capacity(), bytes, GLES30.GL_STATIC_DRAW)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    val entry = ids[0] to data
    buffers[mesh] = entry
    // A deforming mesh brings new geometry every frame: keep only the recent ones.
    while (buffers.size > MAX_BUFFERS) {
      val oldest = buffers.keys.first()
      GLES30.glDeleteBuffers(1, intArrayOf(buffers.remove(oldest)!!.first), 0)
    }
    return entry
  }

  private fun ensureContext(): Boolean {
    if (context != EGL14.EGL_NO_CONTEXT) return true
    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    val version = IntArray(2)
    if (!EGL14.eglInitialize(display, version, 0, version, 1)) return fail("eglInitialize")
    val attributes =
      intArrayOf(
        EGL14.EGL_RED_SIZE, 8,
        EGL14.EGL_GREEN_SIZE, 8,
        EGL14.EGL_BLUE_SIZE, 8,
        EGL14.EGL_ALPHA_SIZE, 8,
        EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
        EGL14.EGL_NONE,
      )
    val configs = arrayOfNulls<EGLConfig>(1)
    val count = IntArray(1)
    if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) {
      return fail("eglChooseConfig")
    }
    config = configs[0]
    context =
      EGL14.eglCreateContext(
        display,
        config,
        EGL14.EGL_NO_CONTEXT,
        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
        0,
      )
    if (context == EGL14.EGL_NO_CONTEXT) return fail("eglCreateContext")
    return true
  }

  /** The reader, its EGL surface and the offscreen target, at [width] by [height]. */
  private fun ensureTarget(width: Int, height: Int): Boolean {
    if (reader != null && this.width == width && this.height == height) return true
    releaseTarget()
    val reader =
      ImageReader.newInstance(
        width,
        height,
        PixelFormat.RGBA_8888,
        MAX_COMPOSITES_PER_FRAME * 2 + 2,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
      )
    this.reader = reader
    surface =
      EGL14.eglCreateWindowSurface(display, config, reader.surface, intArrayOf(EGL14.EGL_NONE), 0)
    if (surface == EGL14.EGL_NO_SURFACE) return fail("eglCreateWindowSurface")
    if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return fail("eglMakeCurrent")
    try {
      if (meshProgram == 0) meshProgram = program(MESH_VERTEX, MESH_FRAGMENT)
      if (blitProgram == 0) blitProgram = program(BLIT_VERTEX, BLIT_FRAGMENT)
      val ids = IntArray(1)
      GLES30.glGenTextures(1, ids, 0)
      colorTexture = ids[0]
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture)
      GLES30.glTexImage2D(
        GLES30.GL_TEXTURE_2D,
        0,
        GLES30.GL_RGBA8,
        width,
        height,
        0,
        GLES30.GL_RGBA,
        GLES30.GL_UNSIGNED_BYTE,
        null,
      )
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
      GLES30.glGenRenderbuffers(1, ids, 0)
      depthBuffer = ids[0]
      GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthBuffer)
      GLES30.glRenderbufferStorage(
        GLES30.GL_RENDERBUFFER,
        GLES30.GL_DEPTH_COMPONENT24,
        width,
        height,
      )
      GLES30.glGenFramebuffers(1, ids, 0)
      fbo = ids[0]
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
      GLES30.glFramebufferTexture2D(
        GLES30.GL_FRAMEBUFFER,
        GLES30.GL_COLOR_ATTACHMENT0,
        GLES30.GL_TEXTURE_2D,
        colorTexture,
        0,
      )
      GLES30.glFramebufferRenderbuffer(
        GLES30.GL_FRAMEBUFFER,
        GLES30.GL_DEPTH_ATTACHMENT,
        GLES30.GL_RENDERBUFFER,
        depthBuffer,
      )
      val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
      if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) return fail("framebuffer status $status")
    } finally {
      EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    }
    this.width = width
    this.height = height
    depthDirty = true
    return true
  }

  private fun program(vertex: String, fragment: String): Int {
    fun shader(type: Int, source: String): Int {
      val id = GLES30.glCreateShader(type)
      GLES30.glShaderSource(id, source)
      GLES30.glCompileShader(id)
      val ok = IntArray(1)
      GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, ok, 0)
      if (ok[0] == 0) throw IllegalStateException("shader: " + GLES30.glGetShaderInfoLog(id))
      return id
    }
    val id = GLES30.glCreateProgram()
    GLES30.glAttachShader(id, shader(GLES30.GL_VERTEX_SHADER, vertex))
    GLES30.glAttachShader(id, shader(GLES30.GL_FRAGMENT_SHADER, fragment))
    for ((location, name) in ATTRIBUTE_NAMES) GLES30.glBindAttribLocation(id, location, name)
    GLES30.glLinkProgram(id)
    val ok = IntArray(1)
    GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
    if (ok[0] == 0) throw IllegalStateException("link: " + GLES30.glGetProgramInfoLog(id))
    return id
  }

  private fun fail(what: String): Boolean {
    Log.w(TAG, "GLES depth renderer unavailable ($what, EGL error ${EGL14.eglGetError()})")
    disabled = true
    return false
  }

  private fun releaseTarget() {
    if (display != EGL14.EGL_NO_DISPLAY && context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE) {
      if (EGL14.eglMakeCurrent(display, surface, surface, context)) {
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (colorTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(colorTexture), 0)
        if (depthBuffer != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(depthBuffer), 0)
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
      }
      EGL14.eglDestroySurface(display, surface)
    }
    fbo = 0
    colorTexture = 0
    depthBuffer = 0
    surface = EGL14.EGL_NO_SURFACE
    (previous + current).forEach { it.close() }
    previous.clear()
    current.clear()
    reader?.close()
    reader = null
  }

  fun dispose() {
    releaseTarget()
    if (display != EGL14.EGL_NO_DISPLAY) {
      if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
      EGL14.eglTerminate(display)
    }
    // Buffers, programs and the texture go with the context.
    buffers.clear()
    context = EGL14.EGL_NO_CONTEXT
    display = EGL14.EGL_NO_DISPLAY
    meshProgram = 0
    blitProgram = 0
    textureId = 0
    textureBitmap = null
  }

  private companion object {
    const val TAG = "Rc3D"
    const val EGL_OPENGL_ES3_BIT = 0x40
    const val MAX_COMPOSITES_PER_FRAME = Rc3DDepthLimits.MAX_DRAWS_PER_FRAME
    const val MAX_BUFFERS = 16
    const val DEPTH_STEPS = 16_777_215f

    val ATTRIBUTE_NAMES =
      listOf(
        0 to "aPosition",
        1 to "aNormal",
        2 to "aEdge1",
        3 to "aEdge2",
        4 to "aCentroid",
        5 to "aUv",
      )

    /** Location, float offset in the vertex, and component count. */
    val ATTRIBUTES =
      listOf(
        Triple(0, Rc3DShaderMeshData.POSITION, 3),
        Triple(1, Rc3DShaderMeshData.NORMAL, 3),
        Triple(2, Rc3DShaderMeshData.EDGE1, 3),
        Triple(3, Rc3DShaderMeshData.EDGE2, 3),
        Triple(4, Rc3DShaderMeshData.CENTROID, 3),
        Triple(5, Rc3DShaderMeshData.UV, 2),
      )

    /** `litColor` and `litColorSpecular` per vertex, as the shader mesh lights. */
    val MESH_VERTEX =
      """
      #version 300 es
      in vec3 aPosition;
      in vec3 aNormal;
      in vec3 aEdge1;
      in vec3 aEdge2;
      in vec3 aCentroid;
      in vec2 aUv;
      uniform mat4 uMvp;
      uniform mat4 uMv;
      uniform vec4 uBase;
      uniform vec3 uShading;
      uniform int uLightCount;
      uniform vec4 uLightPos[$MAX_LIGHTS];
      uniform vec4 uLightColor[$MAX_LIGHTS];
      out vec4 vShade;
      out vec2 vUv;

      vec3 toLight(vec4 light, vec3 p) {
        if (light.w > 0.5) {
          vec3 d = light.xyz - p;
          float l = length(d);
          return l > 0.0 ? d / l : d;
        }
        return light.xyz;
      }

      void main() {
        gl_Position = uMvp * vec4(aPosition, 1.0);
        vUv = vec2(aUv.x, 1.0 - aUv.y);
        vec3 eye = (uMv * vec4(aPosition, 1.0)).xyz;
        vec3 face = cross((uMv * vec4(aEdge1, 0.0)).xyz, (uMv * vec4(aEdge2, 0.0)).xyz);
        vec3 centroid = (uMv * vec4(aCentroid, 1.0)).xyz;
        vec3 n;
        vec3 p;
        if (uShading.x > 0.5) {
          n = (uMv * vec4(aNormal, 0.0)).xyz;
          p = eye;
        } else {
          n = face;
          p = uShading.y > 0.0 ? eye : centroid;
        }
        float nl = length(n);
        n = nl > 0.0 ? n / nl : vec3(0.0);
        vec3 diffuse = vec3(${Rc3DShaderMeshData.AMBIENT});
        vec3 spec = vec3(0.0);
        if (uShading.y > 0.0) {
          float pl = length(p);
          vec3 view = pl > 0.0 ? -p / pl : vec3(0.0, 0.0, 1.0);
          if (dot(n, view) < 0.0) n = -n;
          for (int i = 0; i < uLightCount; i++) {
            vec3 l = toLight(uLightPos[i], p);
            float ndl = dot(n, l);
            if (ndl <= 0.0) continue;
            diffuse += uLightColor[i].rgb * ndl;
            vec3 h = l + view;
            float hl = length(h);
            if (hl <= 0.0) continue;
            float ndh = dot(n, h) / hl;
            if (ndh <= 0.0) continue;
            spec += uLightColor[i].rgb * (pow(ndh, uShading.z) * uShading.y);
          }
        } else {
          for (int i = 0; i < uLightCount; i++) {
            diffuse += uLightColor[i].rgb * abs(dot(n, toLight(uLightPos[i], p)));
          }
        }
        vec3 rgb = min(uBase.rgb * diffuse + spec, vec3(1.0));
        vShade = vec4(rgb * uBase.a, uBase.a);
      }
      """
        .trimIndent()

    val MESH_FRAGMENT =
      """
      #version 300 es
      precision highp float;
      in vec4 vShade;
      in vec2 vUv;
      uniform sampler2D uTexture;
      uniform float uTextured;
      out vec4 fragColor;

      void main() {
        if (uTextured > 0.5) {
          vec4 texel = texture(uTexture, vUv);
          // As the software rasterizer does: a fully transparent texel writes neither colour nor
          // depth, so what is behind a hole in the texture shows through it.
          if (texel.a == 0.0) discard;
          fragColor = vShade * texel;
        } else {
          fragColor = vShade;
        }
      }
      """
        .trimIndent()

    /** One triangle over the whole viewport, sampling the offscreen colour. */
    val BLIT_VERTEX =
      """
      #version 300 es
      out vec2 vUv;

      void main() {
        vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
        vUv = p;
        gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
      }
      """
        .trimIndent()

    val BLIT_FRAGMENT =
      """
      #version 300 es
      precision highp float;
      in vec2 vUv;
      uniform sampler2D uColor;
      out vec4 fragColor;

      void main() {
        fragColor = texture(uColor, vUv);
      }
      """
        .trimIndent()
  }
}

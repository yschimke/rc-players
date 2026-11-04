package ee.schimke.composeai.rcplayer.xrsample

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.util.Log
import android.view.Surface
import ee.schimke.composeai.rcplayer.threed.Rc3DMeshDraw
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders the stereo layer's draws into a side-by-side stereo `SurfaceEntity`'s surface: the left
 * eye into the left half, the right eye into the right half, each with its own [RcEyeCamera] and a
 * depth buffer shared by the draws of that eye.
 *
 * The same shape as the player's GLES depth renderer (`Rc3DDepthRenderer.android.kt` in
 * `:rc-player-3d`): one EGL context, made current on the drawing thread for each render and
 * released after it, and vertex buffers cached per mesh, keeping only the recent ones since a
 * deforming mesh brings new geometry every frame. It differs in drawing to a window surface rather
 * than an `ImageReader`, in two viewports with two cameras, and in lighting: a draw carries the
 * document's paint but not its lights, so meshes are lit by one fixed light from above and in front
 * of the panel, the matte Lambert the room entities approximate with a rough PBR material.
 *
 * Output is premultiplied, on a transparent background, so the panel shows through around the
 * meshes.
 */
internal class RcStereoSurfaceRenderer {
  private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
  private var context: EGLContext = EGL14.EGL_NO_CONTEXT
  private var config: EGLConfig? = null
  private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
  private var target: Surface? = null
  private var program = 0
  private var failed = false

  private class Buffers(val vbo: Int, val ibo: Int, val indexCount: Int)

  private val buffers = LinkedHashMap<Rc3DMeshData, Buffers>()
  private val size = IntArray(2)

  /** False once setup or a render failed: the host then routes deforming draws to the panel. */
  val available: Boolean
    get() = !failed

  /**
   * Render [draws], placed for [viewport], for both eyes into [surface]. An empty [draws] clears
   * the surface to transparent. False when nothing was rendered.
   */
  fun render(
    surface: Surface,
    draws: Collection<Rc3DMeshDraw>,
    viewport: RcXrViewport,
    left: RcEyeCamera,
    right: RcEyeCamera,
  ): Boolean {
    if (failed || !surface.isValid) return false
    return try {
      if (!ensureContext() || !ensureSurface(surface)) return false
      if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
        return fail("eglMakeCurrent")
      }
      try {
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_WIDTH, size, 0)
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_HEIGHT, size, 1)
        val width = size[0]
        val height = size[1]
        if (width < 2 || height < 1) return false
        if (program == 0) program = program(VERTEX, FRAGMENT)

        GLES30.glViewport(0, 0, width, height)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClearDepthf(1f)
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (draws.isNotEmpty()) {
          GLES30.glEnable(GLES30.GL_DEPTH_TEST)
          GLES30.glDepthFunc(GLES30.GL_LESS)
          // Both faces: the player culls by the document's own winding, which the per-eye
          // projections keep, but a deforming mesh can turn inside out mid-animation.
          GLES30.glDisable(GLES30.GL_CULL_FACE)
          GLES30.glEnable(GLES30.GL_BLEND)
          GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
          GLES30.glUseProgram(program)
          val models = draws.map { RcStereoCameras.panelModel(it, viewport) }
          val half = width / 2
          for ((index, eye) in listOf(left, right).withIndex()) {
            GLES30.glViewport(index * half, 0, half, height)
            draws.forEachIndexed { i, draw -> drawMesh(draw, models[i], eye) }
          }
          GLES30.glDisable(GLES30.GL_DEPTH_TEST)
          GLES30.glDisable(GLES30.GL_BLEND)
        }
        if (!EGL14.eglSwapBuffers(display, eglSurface)) return fail("eglSwapBuffers")
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
      Log.w(TAG, "Stereo surface renderer failed; deforming meshes go back to the panel", e)
      failed = true
      false
    }
  }

  private fun drawMesh(draw: Rc3DMeshDraw, model: FloatArray, eye: RcEyeCamera) {
    val mesh = upload(draw.mesh) ?: return
    GLES30.glUniformMatrix4fv(uniform("uMvp"), 1, false, eye.modelViewProjection(model), 0)
    GLES30.glUniformMatrix3fv(uniform("uNormal"), 1, false, RcStereoCameras.normalMatrix(model), 0)
    val c = draw.color
    GLES30.glUniform4f(
      uniform("uBase"),
      ((c shr 16) and 0xFF) / 255f,
      ((c shr 8) and 0xFF) / 255f,
      (c and 0xFF) / 255f,
      ((c ushr 24) and 0xFF) / 255f,
    )
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mesh.vbo)
    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.ibo)
    val stride = STRIDE * 4
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
    GLES30.glEnableVertexAttribArray(1)
    GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
    GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.indexCount, GLES30.GL_UNSIGNED_INT, 0)
    GLES30.glDisableVertexAttribArray(0)
    GLES30.glDisableVertexAttribArray(1)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
  }

  private fun uniform(name: String): Int = GLES30.glGetUniformLocation(program, name)

  private fun upload(mesh: Rc3DMeshData): Buffers? {
    buffers[mesh]?.let {
      return it
    }
    val packed = runCatching { RcXrMeshPacking.pack(mesh) }.getOrNull() ?: return null
    // Position and normal only: the packing's optional UVs are dropped, as this renderer does not
    // texture (nor do the room entities yet).
    val count = packed.vertexCount
    val vertices = FloatArray(count * STRIDE)
    for (i in 0 until count) {
      System.arraycopy(packed.vertices, i * packed.stride, vertices, i * STRIDE, STRIDE)
    }
    val ids = IntArray(2)
    GLES30.glGenBuffers(2, ids, 0)
    val vertexBytes =
      ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).apply {
        asFloatBuffer().put(vertices)
      }
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
    GLES30.glBufferData(
      GLES30.GL_ARRAY_BUFFER,
      vertexBytes.capacity(),
      vertexBytes,
      GLES30.GL_STATIC_DRAW,
    )
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    val indexBytes =
      ByteBuffer.allocateDirect(packed.indices.size * 4).order(ByteOrder.nativeOrder()).apply {
        asIntBuffer().put(packed.indices)
      }
    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[1])
    GLES30.glBufferData(
      GLES30.GL_ELEMENT_ARRAY_BUFFER,
      indexBytes.capacity(),
      indexBytes,
      GLES30.GL_STATIC_DRAW,
    )
    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    val entry = Buffers(ids[0], ids[1], packed.indices.size)
    buffers[mesh] = entry
    while (buffers.size > MAX_BUFFERS) {
      val oldest = buffers.keys.first()
      val gone = buffers.remove(oldest)!!
      GLES30.glDeleteBuffers(2, intArrayOf(gone.vbo, gone.ibo), 0)
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
        EGL14.EGL_RED_SIZE,
        8,
        EGL14.EGL_GREEN_SIZE,
        8,
        EGL14.EGL_BLUE_SIZE,
        8,
        EGL14.EGL_ALPHA_SIZE,
        8,
        EGL14.EGL_DEPTH_SIZE,
        24,
        EGL14.EGL_RENDERABLE_TYPE,
        EGL_OPENGL_ES3_BIT,
        EGL14.EGL_SURFACE_TYPE,
        EGL14.EGL_WINDOW_BIT,
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

  private fun ensureSurface(surface: Surface): Boolean {
    if (target === surface && eglSurface != EGL14.EGL_NO_SURFACE) return true
    releaseSurface()
    eglSurface =
      EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
    if (eglSurface == EGL14.EGL_NO_SURFACE) return fail("eglCreateWindowSurface")
    target = surface
    return true
  }

  private fun program(vertex: String, fragment: String): Int {
    fun shader(type: Int, source: String): Int {
      val id = GLES30.glCreateShader(type)
      GLES30.glShaderSource(id, source)
      GLES30.glCompileShader(id)
      val ok = IntArray(1)
      GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, ok, 0)
      check(ok[0] != 0) { "shader: " + GLES30.glGetShaderInfoLog(id) }
      return id
    }
    val id = GLES30.glCreateProgram()
    GLES30.glAttachShader(id, shader(GLES30.GL_VERTEX_SHADER, vertex))
    GLES30.glAttachShader(id, shader(GLES30.GL_FRAGMENT_SHADER, fragment))
    GLES30.glBindAttribLocation(id, 0, "aPosition")
    GLES30.glBindAttribLocation(id, 1, "aNormal")
    GLES30.glLinkProgram(id)
    val ok = IntArray(1)
    GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
    check(ok[0] != 0) { "link: " + GLES30.glGetProgramInfoLog(id) }
    return id
  }

  private fun fail(what: String): Boolean {
    Log.w(TAG, "Stereo surface renderer unavailable ($what, EGL error ${EGL14.eglGetError()})")
    failed = true
    return false
  }

  private fun releaseSurface() {
    if (display != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE) {
      EGL14.eglDestroySurface(display, eglSurface)
    }
    eglSurface = EGL14.EGL_NO_SURFACE
    target = null
  }

  fun dispose() {
    releaseSurface()
    if (display != EGL14.EGL_NO_DISPLAY) {
      if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
      EGL14.eglTerminate(display)
    }
    // Buffers and the program go with the context.
    buffers.clear()
    context = EGL14.EGL_NO_CONTEXT
    display = EGL14.EGL_NO_DISPLAY
    program = 0
  }

  private companion object {
    const val TAG = "RcXrStereo"
    const val EGL_OPENGL_ES3_BIT = 0x40
    const val MAX_BUFFERS = 16

    /** Floats per vertex this renderer uploads: position and normal. */
    const val STRIDE = 6

    val VERTEX =
      """
      #version 300 es
      in vec3 aPosition;
      in vec3 aNormal;
      uniform mat4 uMvp;
      uniform mat3 uNormal;
      out vec3 vNormal;

      void main() {
        gl_Position = uMvp * vec4(aPosition, 1.0);
        vNormal = uNormal * aNormal;
      }
      """
        .trimIndent()

    /** Two-sided Lambert from a light above and in front of the panel, in panel space. */
    val FRAGMENT =
      """
      #version 300 es
      precision mediump float;
      in vec3 vNormal;
      uniform vec4 uBase;
      out vec4 fragColor;

      void main() {
        float len = length(vNormal);
        vec3 n = len > 0.0 ? vNormal / len : vec3(0.0, 0.0, 1.0);
        vec3 l = normalize(vec3(0.3, 0.6, 1.0));
        float shade = 0.3 + 0.7 * abs(dot(n, l));
        vec3 rgb = min(uBase.rgb * shade, vec3(1.0));
        fragColor = vec4(rgb * uBase.a, uBase.a);
      }
      """
        .trimIndent()
  }
}

package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcFloatExpressionEvaluator
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue

/**
 * Writes a draw-list document that uses the draft 3D operations: for tests, samples, and hosts that
 * generate scenes. There is no creation-API support for these operations yet (they are not in
 * AndroidX's writer), so this is the way to author one from Kotlin.
 *
 * Values are [RcFloatWord]s, so anything may be a literal ([float]), a host-driven named value
 * ([namedFloat]), the document clock ([animationTime]) or an expression over them ([expression]).
 */
public class Rc3DDocumentBuilder(public val width: Int, public val height: Int) {
  private val operations = mutableListOf<RcOperation>()
  private var nextId = FIRST_ID

  /** Append any operation, 2D or 3D, in draw order. */
  public fun add(operation: RcOperation): Rc3DDocumentBuilder = apply { operations += operation }

  public fun float(value: Float): RcFloatWord = RcFloatWord.literal(value)

  /** A fresh id for a mesh, bitmap or variable. */
  public fun newId(): Int = nextId++

  /**
   * A float the host can set without re-sending the document, under [name]: the host writes
   * `namedValues["USER:$name"] = RcNamedValue.FloatValue(...)`.
   */
  public fun namedFloat(name: String, default: Float): RcFloatWord {
    val id = newId()
    operations += RcFloatConstant(id, RcFloatWord.literal(default))
    operations += RcNamedVariable(id, RcNamedVariable.FLOAT_TYPE, "USER:$name")
    return reference(id)
  }

  /** Seconds since the document started. */
  public fun animationTime(): RcFloatWord = reference(RcSystemVariables.ANIMATION_TIME)

  /** Seconds on a continuous clock; the AndroidX View player schedules frames only for this. */
  public fun continuousSeconds(): RcFloatWord = reference(RcSystemVariables.CONTINUOUS_SEC)

  /** A float expression in RPN; operators come from [operator]. */
  public fun expression(words: List<RcFloatWord>): RcFloatWord {
    val id = newId()
    operations += RcFloatExpression(id, words, animation = null)
    return reference(id)
  }

  /** `a * b` as an expression. */
  public fun times(a: RcFloatWord, b: RcFloatWord): RcFloatWord =
    expression(listOf(a, b, operator(MUL)))

  public fun color(argb: Int): Rc3DDocumentBuilder = add(RcPaintData(listOf(PAINT_COLOR, argb)))

  public fun clearDepth(): Rc3DDocumentBuilder =
    add(RcPaint3DState(Rc3D.P3_CLEAR_DEPTH, emptyList()))

  public fun material(specular: Float, shininess: Float): Rc3DDocumentBuilder =
    add(RcPaint3DState(Rc3D.P3_MATERIAL, listOf(float(specular), float(shininess))))

  /** Perspective camera: vertical field of view in radians, then gluLookAt's nine values. */
  public fun perspectiveCamera(
    fovY: RcFloatWord,
    aspect: RcFloatWord,
    near: RcFloatWord,
    far: RcFloatWord,
    eye: List<RcFloatWord>,
    center: List<RcFloatWord>,
    up: List<RcFloatWord> = listOf(float(0f), float(1f), float(0f)),
  ): Rc3DDocumentBuilder {
    require(eye.size == 3 && center.size == 3 && up.size == 3)
    return add(
      RcSetCamera3D(
        Rc3D.PROJECTION_PERSPECTIVE,
        listOf(fovY, aspect, near, far),
        eye + center + up,
      ),
    )
  }

  /**
   * A perspective camera a host drives through named values, the convention an XR host uses to
   * render a 3D document for one eye's pose: `<prefix>.eye.x/y/z`, `<prefix>.target.x/y/z`,
   * `<prefix>.up.x/y/z` and `<prefix>.fovY`. [Rc3DHostCamera.namedValues] writes them.
   */
  public fun hostCamera(
    prefix: String = Rc3DHostCamera.DEFAULT_PREFIX,
    default: Rc3DHostCamera,
    aspect: Float = width / height.toFloat(),
    near: Float = 0.1f,
    far: Float = 100f,
  ): Rc3DDocumentBuilder {
    val names = Rc3DHostCamera.names(prefix)
    val values = default.values()
    val words = names.mapIndexed { index, name -> namedFloat(name, values[index]) }
    return perspectiveCamera(
      words[9],
      float(aspect),
      float(near),
      float(far),
      words.subList(0, 3),
      words.subList(3, 6),
      words.subList(6, 9),
    )
  }

  public fun lights(vararg lights: Rc3DLight): Rc3DDocumentBuilder =
    add(
      RcSetLights3D(
        lights.map { it.type },
        lights.map { it.color },
        lights.flatMap { listOf(it.x, it.y, it.z, it.intensity) },
      ),
    )

  public fun identity(): Rc3DDocumentBuilder = add(RcMatrix3DOp(Rc3D.M3_IDENTITY, emptyList()))

  public fun translate(x: RcFloatWord, y: RcFloatWord, z: RcFloatWord): Rc3DDocumentBuilder =
    add(RcMatrix3DOp(Rc3D.M3_TRANSLATE, listOf(x, y, z)))

  public fun scale(x: RcFloatWord, y: RcFloatWord, z: RcFloatWord): Rc3DDocumentBuilder =
    add(RcMatrix3DOp(Rc3D.M3_SCALE, listOf(x, y, z)))

  /** Rotate by [radians] about the axis `(x, y, z)`. */
  public fun rotate(radians: RcFloatWord, x: Float, y: Float, z: Float): Rc3DDocumentBuilder =
    add(RcMatrix3DOp(Rc3D.M3_ROTATE_AXIS, listOf(radians, float(x), float(y), float(z))))

  /** A parametric primitive ([ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3D] types). */
  public fun primitive(
    type: Int,
    params: List<RcFloatWord>,
    segments: RcFloatWord = float(0f),
    flags: Int = 0,
    extraChannels: List<List<RcFloatWord>> = emptyList(),
  ): Int {
    val id = newId()
    add(RcMeshPrimitive3D(id, type, segments, flags, listOf(params) + extraChannels))
    return id
  }

  public fun mesh(
    indices: IntArray,
    verts: FloatArray,
    normals: FloatArray = FloatArray(0),
    uv: FloatArray = FloatArray(0),
  ): Int {
    val id = newId()
    add(RcDefineMesh3D(id, indices, verts, normals, uv))
    return id
  }

  public fun texture(bitmapId: Int): Rc3DDocumentBuilder = add(RcSetTexture3D(bitmapId))

  public fun draw(meshId: Int, mode: Int = Rc3D.MODE_SOFTWARE_SMOOTH): Rc3DDocumentBuilder =
    add(RcDrawMesh3D(meshId, mode))

  public fun build(): RcDocument =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = width, legacyHeight = height, modern = false),
      operations.toList(),
    )

  public fun encode(): ByteArray = RcDocumentCodec.encode(build(), Rc3DExtension.wireExtensions)

  public companion object {
    /** Float expression operators by number, as `AnimatedFloatExpression` numbers them. */
    public const val ADD: Int = 1
    public const val SUB: Int = 2
    public const val MUL: Int = 3
    public const val DIV: Int = 4
    public const val SIN: Int = 18
    public const val COS: Int = 19

    public fun operator(number: Int): RcFloatWord =
      RcFloatExpressionEvaluator.operatorWord(RcFloatExpressionEvaluator.OFFSET + number)

    private const val FIRST_ID = 100
    private const val PAINT_COLOR = 4

    private fun reference(id: Int): RcFloatWord = RcFloatWord(0x7fc00000 or id)
  }
}

/** A light for [Rc3DDocumentBuilder.lights]: a direction (or position) and an intensity. */
public class Rc3DLight(
  public val type: Int,
  public val color: Int,
  public val x: RcFloatWord,
  public val y: RcFloatWord,
  public val z: RcFloatWord,
  public val intensity: RcFloatWord,
) {
  public companion object {
    public fun directional(
      x: Float,
      y: Float,
      z: Float,
      intensity: Float = 1f,
      color: Int = -1,
    ): Rc3DLight =
      Rc3DLight(
        Rc3D.LIGHT_DIRECTIONAL,
        color,
        RcFloatWord.literal(x),
        RcFloatWord.literal(y),
        RcFloatWord.literal(z),
        RcFloatWord.literal(intensity),
      )
  }
}

/**
 * A camera pose a host supplies to a document written with [Rc3DDocumentBuilder.hostCamera]: one
 * eye of a headset, a phone's device pose, or a fixed preview viewpoint.
 */
public class Rc3DHostCamera(
  public val eye: FloatArray,
  public val target: FloatArray = floatArrayOf(0f, 0f, 0f),
  public val up: FloatArray = floatArrayOf(0f, 1f, 0f),
  public val fovYRadians: Float = 0.9f,
) {
  init {
    require(eye.size == 3 && target.size == 3 && up.size == 3)
  }

  internal fun values(): List<Float> = eye.toList() + target.toList() + up.toList() + fovYRadians

  /** The named values that put the document's camera at this pose. */
  public fun namedValues(prefix: String = DEFAULT_PREFIX): Map<String, RcNamedValue> =
    names(prefix).zip(values()).associate { (name, value) ->
      "USER:$name" to RcNamedValue.FloatValue(value)
    }

  public companion object {
    public const val DEFAULT_PREFIX: String = "xr.camera"

    public fun names(prefix: String = DEFAULT_PREFIX): List<String> =
      listOf("eye", "target", "up").flatMap { part ->
        listOf("x", "y", "z").map { axis -> "$prefix.$part.$axis" }
      } + "$prefix.fovY"
  }
}

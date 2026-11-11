package ee.schimke.composeai.rccreation.common

/**
 * Platform-neutral write surface consumed by the typed Creation Compose document program.
 *
 * This interface intentionally describes protocol operations rather than a canvas. Creation Compose
 * retains remote values in its IR until optimization is complete, then resolves them and calls this
 * writer. The JVM/Android migration currently adapts these calls to the legacy Java writer; the
 * common encoder will implement the same surface directly.
 */
public interface RemoteWriter {
  /**
   * The writer itself. AndroidX's Kotlin reaches some operations through
   * `RemoteComposeWriter.getBuffer()` (`writer.buffer.definePattern(…)`), a `RemoteComposeBuffer`
   * under the writer; here the writer writes them itself, and this lets that text stand.
   */
  public val buffer: RemoteWriter
    get() = this

  public fun createFloatId(): Float

  /** The next free id, as `RemoteComposeWriter.nextId`. */
  public fun nextId(): Int

  /** The writer's paint builder, as `RemoteComposeWriter.getRcPaint`. */
  public val rcPaint: RcPaint

  public val componentIdForCache: Int

  public fun addComponentWidthValue(componentId: Int): Float

  public fun addComponentHeightValue(componentId: Int): Float

  /** The x position of [componentId] in its parent's coordinates. */
  public fun addComponentXValue(componentId: Int): Float

  /** The y position of [componentId] in its parent's coordinates. */
  public fun addComponentYValue(componentId: Int): Float

  /** The x position of [componentId] in root coordinates. */
  public fun addComponentRootXValue(componentId: Int): Float

  /** The y position of [componentId] in root coordinates. */
  public fun addComponentRootYValue(componentId: Int): Float

  /**
   * Binds the reserved float variable [valueId] to [componentId]'s value of [type] (AndroidX's
   * `ComponentValue.WIDTH`, `HEIGHT`, `POS_X`, ...), as `RemoteComposeBuffer.addComponentValue`
   * does. A later `addComponent*Value` of the same component and type returns the same variable.
   */
  public fun addComponentValue(type: Int, componentId: Int, valueId: Float)

  public fun startRoot()

  public fun endRoot()

  public fun startBox(modifier: RemoteModifierData, horizontal: Int, vertical: Int)

  public fun endBox()

  public fun startRow(modifier: RemoteModifierData, horizontal: Int, vertical: Int)

  public fun endRow()

  public fun startColumn(modifier: RemoteModifierData, horizontal: Int, vertical: Int)

  public fun endColumn()

  public fun startCollapsibleRow(modifier: RemoteModifierData, horizontal: Int, vertical: Int)

  public fun endCollapsibleRow()

  public fun startCollapsibleColumn(modifier: RemoteModifierData, horizontal: Int, vertical: Int)

  public fun endCollapsibleColumn()

  public fun startCanvas(modifier: RemoteModifierData)

  public fun endCanvas()

  public fun startCanvasOperations()

  public fun endCanvasOperations()

  public fun startFitBox(modifier: RemoteModifierData, horizontal: Int, vertical: Int)

  public fun endFitBox()

  public fun startFlow(
    modifier: RemoteModifierData,
    horizontal: Int,
    vertical: Int,
    maxItemsInEachRow: Int,
    maxLines: Int,
  )

  public fun endFlow()

  public fun startStateLayout(modifier: RemoteModifierData, indexId: Int)

  public fun endStateLayout()

  public fun writeModifier(operation: RemoteModifierOperation)

  public fun startTextComponent(data: RemoteTextData)

  public fun endTextComponent()

  public fun image(data: RemoteImageData)

  public fun startCustom(
    modifier: RemoteModifierData,
    configId: Int,
    properties: List<Custom.CustomProperty>,
  )

  public fun endCustom()

  public fun definePattern(id: Int, parameterIds: IntArray)

  public fun endPatternDefine()

  public fun inflatePattern(id: Int, argumentIds: IntArray)

  public fun endPatternInflation()

  public fun addPatternForEach(collectionId: Int, localItemId: Int)

  public fun endPatternForEach()

  public fun conditionalOperations(type: Int, first: Float, second: Float)

  public fun endConditionalOperations()

  /** A conditional block around [content], as `RemoteComposeWriter.conditionalOperations`. */
  public fun conditionalOperations(type: Int, first: Float, second: Float, content: () -> Unit) {
    conditionalOperations(type, first, second)
    content()
    endConditionalOperations()
  }

  public fun drawOnBitmap(bitmapId: Int, mode: Int, color: Int)

  /**
   * Renders the content of the component [componentId] (or the variable id holding it) into the
   * offscreen [bitmapId], as `RemoteComposeWriter.drawComponentToBitmap(int, int)`.
   */
  public fun drawComponentToBitmap(componentId: Int, bitmapId: Int) {
    drawOnBitmap(bitmapId, DrawToBitmapModeComponentId, componentId)
    drawComponentContent()
    drawOnBitmap(0, 0, 0)
  }

  /**
   * Renders the active component's content into the offscreen [bitmapId], as
   * `RemoteComposeWriter.drawComponentToBitmap(int)`.
   */
  public fun drawComponentToBitmap(bitmapId: Int) {
    drawOnBitmap(bitmapId, 0, 0)
    drawComponentContent()
    drawOnBitmap(0, 0, 0)
  }

  /**
   * Starts the definition of a float function, as `RemoteComposeWriter.createFloatFunction`: [args]
   * is filled with the NaN ids of the function's parameters, and the operations written until
   * [endFloatFunction] are its body. Returns the function's id.
   *
   * A visibility animation (`AnimationSpec`'s enter or exit function id) is a function of six
   * parameters, which the player binds to progress, width, height, x, y and the component's id.
   */
  public fun createFloatFunction(args: FloatArray): Int

  /** Ends the function [createFloatFunction] started. */
  public fun endFloatFunction()

  /** Calls the function [id] with [args], as `RemoteComposeWriter.callFloatFunction`. */
  public fun callFloatFunction(id: Int, vararg args: Float)

  public fun startLoop(indexId: Int, from: Float, step: Float, until: Float)

  public fun endLoop()

  /** A loop around [content], as `RemoteComposeWriter.loop`. */
  public fun loop(indexId: Int, from: Float, step: Float, until: Float, content: () -> Unit) {
    startLoop(indexId, from, step, until)
    content()
    endLoop()
  }

  public fun setNamedVariable(id: Int, name: String, type: Int)

  /** [setNamedVariable] for a colour, under `RemoteComposeWriter`'s name. */
  public fun setColorName(id: Int, name: String) {
    setNamedVariable(id, name, NamedVariable.COLOR_TYPE)
  }

  /** [setNamedVariable] for a string, under `RemoteComposeWriter`'s name. */
  public fun setStringName(id: Int, name: String) {
    setNamedVariable(id, name, NamedVariable.STRING_TYPE)
  }

  /** [setNamedVariable] for a float, under `RemoteComposeWriter`'s name. */
  public fun setFloatName(id: Int, name: String) {
    setNamedVariable(id, name, NamedVariable.FLOAT_TYPE)
  }

  public fun addInteger(value: Int): Long

  public fun addNamedInt(name: String, initialValue: Int): Long

  public fun addNamedFloat(name: String, initialValue: Float): Float

  public fun reserveFloatVariable(): Float

  public fun addFloatArray(values: FloatArray): Float

  public fun addList(ids: IntArray): Float

  /** [addList] of text ids, under `RemoteComposeWriter`'s name. */
  public fun addStringList(vararg strIds: Int): Float = addList(strIds)

  public fun addDynamicFloatArray(size: Float): Float

  public fun setArrayValue(id: Int, index: Float, value: Float)

  public fun floatExpression(vararg values: Float): Float

  public fun floatExpression(values: FloatArray, animation: FloatArray?): Float

  public fun integerExpression(vararg values: Long): Long

  public fun timeAttribute(longId: Int, type: Short, vararg args: Int): Float

  public fun idLookup(arrayId: Float, index: Float): Int

  public fun idLookup(outputId: Int, arrayId: Float, index: Float)

  public fun textLookup(arrayId: Float, index: Float): Int

  public fun textLookup(arrayId: Float, indexId: Int): Int

  public fun textLength(textId: Int): Float

  public fun textMerge(leftId: Int, rightId: Int): Int

  /**
   * LOCAL PATCH (rc-players): writes `TextMerge` into [outputId], an id the caller reserved (see
   * [reserveFloatVariable]), as AndroidX's `RemoteComposeBuffer.textMerge(id, left, right)` does.
   * Merging a string with the empty one copies it into [outputId], and keeps it following.
   */
  public fun textMerge(outputId: Int, leftId: Int, rightId: Int)

  /**
   * LOCAL PATCH (rc-players): writes a free-standing `TouchExpression` (opcode 157), as AndroidX's
   * `RemoteComposeBuffer.addTouchExpression` does: the float [id] (reserved with
   * [reserveFloatVariable]) starts at [value] and, while a touch drags, follows the RPN
   * [expression] (over [RemoteContext.FLOAT_TOUCH_POS_X] and the like, at most 32 words), clamped
   * to [min]..[max]. On release it settles by [stopMode] (one of [TouchStop]) with [stopSpec] (its
   * notches or positions, at most 200 words), easing by [easingSpec].
   *
   * [velocityId] is a float the player writes the drag's velocity to, or 0 for none; [touchEffects]
   * the haptic and visual effects flags, 0 for none.
   */
  public fun addTouchExpression(
    id: Int,
    value: Float,
    min: Float,
    max: Float,
    velocityId: Float,
    touchEffects: Int,
    expression: FloatArray,
    stopMode: Int,
    stopSpec: FloatArray? = null,
    easingSpec: FloatArray? = null,
  )

  public fun textSubtext(textId: Int, start: Float, length: Float): Int

  public fun textTransform(textId: Int, start: Float, length: Float, operation: Int): Int

  public fun createTextFromFloat(value: Float, before: Int, after: Int, flags: Int): Int

  public fun getColorAttribute(colorId: Int, type: Short): Float

  public fun bitmapAttribute(bitmapId: Int, type: Short): Float

  public fun bitmapTextMeasure(
    textId: Int,
    bitmapFontId: Int,
    type: Int,
    glyphSpacing: Float,
  ): Float

  public fun addBitmapUrl(url: String, width: Int = 1, height: Int = 1): Int

  public fun addNamedBitmapUrl(name: String, url: String): Int

  public fun createBitmap(width: Int, height: Int): Int

  /**
   * Reserves an offscreen bitmap sized to the component [componentId] (0: the component being
   * drawn), whose backing bitmap the player takes from its pool when it is drawn into, as
   * `RemoteComposeWriter.createOffscreenBitmap`. Returns the bitmap's id.
   */
  public fun createOffscreenBitmap(componentId: Int = 0): Int

  public fun addBitmapFont(
    glyphs: List<BitmapFontGlyph>,
    kerningTable: Map<String, Short> = emptyMap(),
  ): Int

  public fun matrixExpression(vararg expression: Float): Float

  public fun addColor(argb: Int): Int

  public fun addNamedColor(name: String, argb: Int): Int

  public fun addColorExpression(color1: Int, color2: Int, tween: Float): Short

  public fun addColorExpression(color1: Short, color2: Short, tween: Float): Short

  public fun addColorExpression(alpha: Int, hue: Float, saturation: Float, value: Float): Short

  public fun addColorExpression(alpha: Float, red: Float, green: Float, blue: Float): Short

  public fun addText(value: String): Int

  /** [addText], under the other name `RemoteComposeWriter` has for it. */
  public fun textCreateId(text: String): Int = addText(text)

  public fun addThemedColor(
    group: String,
    lightId: Short,
    darkId: Short,
    lightFallback: Int,
    darkFallback: Int,
  ): Int

  public fun addNamedString(name: String, initialValue: String): Int

  public fun addLong(value: Long): Int

  public fun addNamedLong(name: String, initialValue: Long): Int

  public fun addPathData(pathData: FloatArray, winding: Int = 0): Int

  /**
   * LOCAL PATCH (rc-players): writes `ShaderData` (`DATA_SHADER`), an AGSL runtime shader compiled
   * from the text [sourceId] (see [addText]), and returns the id [PaintBundle.setShader] takes.
   *
   * Each float uniform value is a constant or a NaN-encoded variable id (an expression, a
   * component's size, `animationTime`), which the player resolves every frame. At most 255 uniforms
   * of each kind, 1 to 16 values per float uniform (up to a `float4x4`) and 1 to 4 per int uniform,
   * as AGSL takes them.
   */
  public fun addShader(
    sourceId: Int,
    floatUniforms: Map<String, FloatArray> = emptyMap(),
    intUniforms: Map<String, IntArray> = emptyMap(),
    bitmapUniforms: Map<String, Int> = emptyMap(),
  ): Int

  /**
   * LOCAL PATCH (rc-players): writes a parametric `AddMesh2D` (opcode 104, `TYPE_EXPRESSION`) and
   * returns its id, as AndroidX's `RemoteComposeWriter.addMesh2D` does.
   *
   * Every channel is an RPN float expression over the mesh's domain parameters `u` and `v` (`VAR1`
   * / `VAR2`, see [Mesh2D.U] and [Mesh2D.V]), at most 32 words, or null when absent: an absent
   * position takes the [layout]'s default geometry (the unit square for a grid), absent uv is the
   * identity mapping and with no colour channel at all the mesh has no vertex colours. Channels may
   * reference any other float id, `animationTime` included, so a mesh animates for free.
   *
   * With [antialiasWidth] the mesh grows an alpha-fading skirt that wide (`FLAG_ANTIALIAS`), which
   * needs at least one colour channel.
   *
   * The 2D mesh operations are experimental: the document's header is given the experimental
   * profile (and the AndroidX one, unless it names widgets), as remote-core reads them only there.
   */
  public fun addMesh2D(
    layout: Int,
    uCount: Int,
    vCount: Int,
    x: FloatArray? = null,
    y: FloatArray? = null,
    texU: FloatArray? = null,
    texV: FloatArray? = null,
    alpha: FloatArray? = null,
    red: FloatArray? = null,
    green: FloatArray? = null,
    blue: FloatArray? = null,
    width: FloatArray? = null,
    aux: Int = 0,
    antialiasWidth: Float? = null,
  ): Int

  /**
   * LOCAL PATCH (rc-players): writes a literal `AddMesh2D` (`TYPE_VALUES`, or `TYPE_F16_VALUES`
   * with [halfFloat]): a triangle list of 16-bit [indices] over x,y [vertices], with optional u,v
   * pairs [uv] (normalised) and packed ARGB [colors] per vertex. [layout], [uCount] and [vCount]
   * describe the grid, if the vertices form one, for `MatrixFromMesh2D`.
   */
  public fun addMesh2DValues(
    indices: IntArray,
    vertices: FloatArray,
    uv: FloatArray? = null,
    colors: IntArray? = null,
    halfFloat: Boolean = false,
    layout: Int = Mesh2D.LAYOUT_GRID,
    uCount: Int = 0,
    vCount: Int = 0,
  ): Int

  /**
   * LOCAL PATCH (rc-players): writes `DrawMesh2D` (opcode 105), drawing [meshId] with the current
   * paint, textured by the bitmap [imageId] (0 for none).
   */
  public fun drawMesh2D(meshId: Int, imageId: Int = 0, blend: Int = Mesh2D.BLEND_MODULATE)

  /**
   * LOCAL PATCH (rc-players): writes `MatrixFromMesh2D` (opcode 106), multiplying the mesh's local
   * frame at ([u], [v]) into the canvas matrix. [u] and [v] may be float ids.
   */
  public fun matrixFromMesh2D(meshId: Int, u: Float, v: Float, flags: Int = Mesh2D.MATRIX_FULL)

  public fun applyPaint(paint: PaintBundle)

  /**
   * Whether [id] belonged to a loop, pattern or for-each body that has ended. Such an id is
   * declared inside that body only (a player computes it there, or gives it a new id each time it
   * expands the body), so nothing written afterwards may read it: a caller that remembers the ids
   * it was given has to ask again for one that is retired.
   */
  public fun isRetired(id: Int): Boolean = false

  /** Returns and clears whether the next paint must be emitted as a complete state. */
  public fun checkAndClearForceSendingNewPaint(): Boolean

  public fun save()

  public fun restore()

  public fun translate(dx: Float, dy: Float)

  public fun scale(scaleX: Float, scaleY: Float, centerX: Float, centerY: Float)

  /** A scale about the origin, as `RemoteComposeWriter.scale(float, float)`. */
  public fun scale(scaleX: Float, scaleY: Float) {
    scale(scaleX, scaleY, Float.NaN, Float.NaN)
  }

  public fun rotate(angle: Float, centerX: Float, centerY: Float)

  /** A rotation about the origin, as `RemoteComposeWriter.rotate(float)`. */
  public fun rotate(angle: Float) {
    rotate(angle, Float.NaN, Float.NaN)
  }

  public fun skew(skewX: Float, skewY: Float)

  public fun clipRect(left: Float, top: Float, right: Float, bottom: Float)

  public fun drawRect(left: Float, top: Float, right: Float, bottom: Float)

  public fun drawRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radiusX: Float,
    radiusY: Float,
  )

  public fun drawCircle(centerX: Float, centerY: Float, radius: Float)

  public fun drawOval(left: Float, top: Float, right: Float, bottom: Float)

  public fun drawArc(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    startAngle: Float,
    sweepAngle: Float,
  )

  public fun drawSector(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    startAngle: Float,
    sweepAngle: Float,
  )

  public fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float)

  public fun drawPath(pathId: Int)

  public fun drawBitmap(
    imageId: Int,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    contentDescriptionId: Int,
  )

  public fun drawComponentContent()

  public fun addClipPath(pathId: Int)

  public fun drawTweenPath(path1Id: Int, path2Id: Int, tween: Float, start: Float, stop: Float)

  public fun drawTextOnPath(
    textId: Int,
    pathId: Int,
    horizontalOffset: Float,
    verticalOffset: Float,
  )

  /**
   * Draws [start] to [end] of the text [textId] with the bitmap font [bitmapFontId] at ([x], [y]),
   * as `RemoteComposeWriter.drawBitmapFontTextRun` (`DRAW_BITMAP_FONT_TEXT_RUN`, 48). An [end] of
   * -1 is the end of the text.
   */
  public fun drawBitmapFontTextRun(
    textId: Int,
    bitmapFontId: Int,
    start: Int,
    end: Int,
    x: Float,
    y: Float,
    glyphSpacing: Float,
  )

  /**
   * Draws [start] to [end] of the text [textId] with the bitmap font [bitmapFontId] along the path
   * [pathId], [yAdj] away from it, as `RemoteComposeWriter.drawBitmapFontTextRunOnPath`
   * (`DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH`, 49).
   */
  public fun drawBitmapFontTextRunOnPath(
    textId: Int,
    bitmapFontId: Int,
    pathId: Int,
    start: Int,
    end: Int,
    yAdj: Float,
    glyphSpacing: Float,
  )

  /**
   * A float holding the [attribute] (a [TextAttribute] constant) of the text [textId] measured with
   * the current paint, as `RemoteComposeWriter.textAttribute` (`ATTRIBUTE_TEXT`, 170).
   */
  public fun textAttribute(textId: Int, attribute: Short): Float

  /** As `RemoteComposeWriter.drawTextRun(String, …)`: [text] added as a text, then the run. */
  public fun drawTextRun(
    text: String,
    start: Int,
    end: Int,
    contextStart: Int,
    contextEnd: Int,
    x: Float,
    y: Float,
    isRtl: Boolean,
  ) {
    drawTextRun(addText(text), start, end, contextStart, contextEnd, x, y, isRtl)
  }

  public fun drawTextRun(
    textId: Int,
    start: Int,
    end: Int,
    contextStart: Int,
    contextEnd: Int,
    x: Float,
    y: Float,
    isRtl: Boolean,
  )

  public fun drawTextAnchored(
    textId: Int,
    anchorX: Float,
    anchorY: Float,
    panX: Float,
    panY: Float,
    flags: Int,
  )

  public fun drawTextOnCircle(
    textId: Int,
    centerX: Float,
    centerY: Float,
    radius: Float,
    startAngle: Float,
    warpRadiusOffset: Float,
    alignment: DrawTextOnCircle.Alignment,
    placement: DrawTextOnCircle.Placement,
  )

  public fun drawScaledBitmap(
    imageId: Int,
    srcLeft: Float,
    srcTop: Float,
    srcRight: Float,
    srcBottom: Float,
    dstLeft: Float,
    dstTop: Float,
    dstRight: Float,
    dstBottom: Float,
    scaleType: Int,
    scaleFactor: Float,
    contentDescription: String?,
  )
}

/** `DrawToBitmap.MODE_COMPONENT_ID`: the color argument names the component to draw. */
private const val DrawToBitmapModeComponentId = 2

public data class BitmapFontGlyph(
  val chars: String,
  val bitmapId: Int,
  val marginLeft: Short,
  val marginTop: Short,
  val marginRight: Short,
  val marginBottom: Short,
  val width: Short,
  val height: Short,
)

/** `NamedVariable`'s type constants, as AndroidX's `remote-core` names them. */
public object NamedVariable {
  public const val STRING_TYPE: Int = 0
  public const val FLOAT_TYPE: Int = 1
  public const val COLOR_TYPE: Int = 2
  public const val IMAGE_TYPE: Int = 3
  public const val INT_TYPE: Int = 4
  public const val LONG_TYPE: Int = 5
  public const val FLOAT_ARRAY_TYPE: Int = 6
}

/** `ComponentValue`'s type constants, as AndroidX's `remote-core` names them. */
public object ComponentValue {
  public const val WIDTH: Int = 0
  public const val HEIGHT: Int = 1
  public const val POS_X: Int = 2
  public const val POS_Y: Int = 3
  public const val POS_ROOT_X: Int = 4
  public const val POS_ROOT_Y: Int = 5
}

/**
 * AndroidX's name for the writer. The Kotlin carried from `remote-creation-expression` and
 * `remote-creation-compose` is typed against
 * `androidx.compose.remote.creation.RemoteComposeWriter`, a Java class; here that name is this
 * interface, so the carried text can stand.
 */
public typealias RemoteComposeWriter = RemoteWriter

package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.PaintContext
import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.VariableSupport
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.ShaderData
import androidx.compose.remote.core.operations.paint.PaintBundle
import androidx.compose.remote.core.operations.paint.PaintChanges
import androidx.compose.remote.core.operations.utilities.ArrayAccess
import androidx.compose.remote.core.operations.utilities.DataMap
import java.io.ByteArrayInputStream

/** One call AndroidX's document made on its paint context while it painted a frame. */
internal data class AndroidxDraw(
  val name: String,
  val arguments: List<Float>,
  /** The colour of the paint the call was made with, as the paint operations before it set it. */
  val color: Int,
  /** The text size of that paint; 0 before any paint operation has set one. */
  val textSize: Float = 0f,
) {
  override fun toString(): String = "$name${arguments} #${color.toUInt().toString(16)}"
}

/**
 * Loads [bytes] with AndroidX's own `CoreDocument` (`remote-core`, the release this repository
 * depends on), lays it out at [width] x [height] and paints one frame into a context that records
 * the drawing and transform calls it receives, with the values AndroidX resolved for them. It draws
 * nothing: this is AndroidX's evaluation of the document (which expressions it runs, when, and what
 * they come to), without a rasterizer.
 */
internal fun androidxPaints(bytes: ByteArray, width: Int, height: Int): List<AndroidxDraw> {
  val document = CoreDocument()
  document.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)))
  val context = RecordingRemoteContext()
  val paint = RecordingPaintContext(context)
  context.setPaintContext(paint)
  context.setDensity(1f)
  context.mWidth = width.toFloat()
  context.mHeight = height.toFloat()
  document.initializeContext(context)
  document.measure(context, 0f, width.toFloat(), 0f, height.toFloat())
  document.paint(context, 0)
  return paint.draws
}

private class RecordingPaintContext(context: RemoteContext) : PaintContext(context), PaintChanges {
  val draws = mutableListOf<AndroidxDraw>()
  private var color = 0xff000000.toInt()
  private var textSize = 0f
  private val savedColors = ArrayDeque<Int>()

  private fun record(name: String, vararg arguments: Float) {
    draws += AndroidxDraw(name, arguments.toList(), color, textSize)
  }

  override fun drawCircle(centerX: Float, centerY: Float, radius: Float) =
    record("circle", centerX, centerY, radius)

  override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) =
    record("rect", left, top, right, bottom)

  override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) = record("line", x1, y1, x2, y2)

  override fun drawOval(left: Float, top: Float, right: Float, bottom: Float) =
    record("oval", left, top, right, bottom)

  override fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float) =
    record("roundRect", l, t, r, b, rx, ry)

  override fun drawArc(l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float) =
    record("arc", l, t, r, b, start, sweep)

  override fun drawSector(l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float) =
    record("sector", l, t, r, b, start, sweep)

  override fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float) =
    record("bitmap", id.toFloat(), left, top, right, bottom)

  override fun drawBitmap(
    imageId: Int,
    srcLeft: Int,
    srcTop: Int,
    srcRight: Int,
    srcBottom: Int,
    dstLeft: Int,
    dstTop: Int,
    dstRight: Int,
    dstBottom: Int,
    cdId: Int,
  ) = record("scaledBitmap", imageId.toFloat())

  override fun drawPath(id: Int, start: Float, end: Float) = record("path", id.toFloat())

  override fun drawTextOnPath(textId: Int, pathId: Int, hOffset: Float, vOffset: Float) =
    record("textOnPath", textId.toFloat(), pathId.toFloat(), hOffset, vOffset)

  override fun drawTextRun(
    textId: Int,
    start: Int,
    end: Int,
    contextStart: Int,
    contextEnd: Int,
    x: Float,
    y: Float,
    rtl: Boolean,
  ) = record("textRun", textId.toFloat(), start.toFloat(), end.toFloat(), x, y)

  override fun drawTweenPath(path1: Int, path2: Int, tween: Float, start: Float, stop: Float) =
    record("tweenPath", path1.toFloat(), path2.toFloat(), tween)

  override fun drawComplexText(layout: RcPlatformServices.ComputedTextLayout?) = Unit

  override fun matrixScale(scaleX: Float, scaleY: Float, centerX: Float, centerY: Float) =
    record("matrixScale", scaleX, scaleY, centerX, centerY)

  override fun matrixTranslate(dx: Float, dy: Float) = record("matrixTranslate", dx, dy)

  override fun matrixSkew(skewX: Float, skewY: Float) = record("matrixSkew", skewX, skewY)

  override fun matrixRotate(rotate: Float, pivotX: Float, pivotY: Float) =
    record("matrixRotate", rotate, pivotX, pivotY)

  override fun matrixSave() = record("save")

  override fun matrixRestore() = record("restore")

  override fun drawToBitmap(bitmapId: Int, mode: Int, color: Int) =
    record("drawToBitmap", bitmapId.toFloat())

  override fun applyPaint(paintData: PaintBundle) = paintData.applyPaintChange(this, this)

  override fun replacePaint(paintBundle: PaintBundle) {
    color = 0xff000000.toInt()
    paintBundle.applyPaintChange(this, this)
  }

  override fun savePaint() {
    savedColors.addLast(color)
  }

  override fun restorePaint() {
    color = savedColors.removeLastOrNull() ?: color
  }

  override fun reset() {
    color = 0xff000000.toInt()
  }

  override fun scale(scaleX: Float, scaleY: Float) = Unit

  override fun translate(translateX: Float, translateY: Float) = Unit

  override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) {
    record("textBounds", textId.toFloat())
    bounds.fill(0f)
  }

  override fun layoutComplexText(
    textId: Int,
    start: Int,
    end: Int,
    alignment: Int,
    overflow: Int,
    maxLines: Int,
    maxWidth: Float,
    maxHeight: Float,
    letterSpacing: Float,
    lineHeightAdd: Float,
    lineHeightMultiplier: Float,
    lineBreakStrategy: Int,
    hyphenationFrequency: Int,
    justificationMode: Int,
    underline: Boolean,
    strikethrough: Boolean,
    flags: Int,
  ): RcPlatformServices.ComputedTextLayout? = null

  override fun tweenPath(out: Int, path1: Int, path2: Int, tween: Float) = Unit

  override fun combinePath(out: Int, path1: Int, path2: Int, operation: Byte) = Unit

  override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) = Unit

  override fun clipPath(pathId: Int, regionOp: Int) = Unit

  override fun roundedClipRect(
    width: Float,
    height: Float,
    topStart: Float,
    topEnd: Float,
    bottomStart: Float,
    bottomEnd: Float,
  ) = Unit

  override fun startGraphicsLayer(w: Int, h: Int) = Unit

  override fun setGraphicsLayer(attributes: HashMap<Int, Any>) = Unit

  override fun endGraphicsLayer() = Unit

  override fun getText(textId: Int): String? = mContext.getText(textId)

  override fun matrixFromPath(pathId: Int, fraction: Float, vOffset: Float, flags: Int) = Unit

  // PaintChanges: only the colour is followed.
  override fun setColor(color: Int) {
    this.color = color
  }

  override fun setTextSize(size: Float) {
    textSize = size
  }

  override fun setStrokeWidth(width: Float) = Unit

  override fun setStrokeCap(cap: Int) = Unit

  override fun setStyle(style: Int) = Unit

  override fun setShader(shader: Int) = Unit

  override fun setImageFilterQuality(quality: Int) = Unit

  override fun setAlpha(a: Float) = Unit

  override fun setStrokeMiter(miter: Float) = Unit

  override fun setStrokeJoin(join: Int) = Unit

  override fun setFilterBitmap(filter: Boolean) = Unit

  override fun setBlendMode(mode: Int) = Unit

  override fun setAntiAlias(aa: Boolean) = Unit

  override fun clear(mask: Long) = Unit

  override fun setLinearGradient(
    colors: IntArray,
    stops: FloatArray?,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    tileMode: Int,
  ) = Unit

  override fun setRadialGradient(
    colors: IntArray,
    stops: FloatArray?,
    centerX: Float,
    centerY: Float,
    radius: Float,
    tileMode: Int,
  ) = Unit

  override fun setRadialGradient(
    colors: IntArray,
    stops: FloatArray?,
    centerX: Float,
    centerY: Float,
    radius: Float,
    focalX: Float,
    focalY: Float,
    focalRadius: Float,
    tileMode: Int,
  ) = Unit

  override fun setSweepGradient(
    colors: IntArray,
    stops: FloatArray?,
    centerX: Float,
    centerY: Float,
  ) = Unit

  override fun setColorFilter(color: Int, mode: Int) = Unit

  override fun setTypeFace(fontType: Int, weight: Int, italic: Boolean) = Unit

  override fun setFallbackTypeFace(fontType: Int, weight: Int, italic: Boolean) = Unit

  override fun setShaderMatrix(matrixId: Float) = Unit

  override fun setTypeFace(fontType: String, weight: Int, italic: Boolean) = Unit

  override fun setFontVariationAxes(tags: Array<String>, values: FloatArray) = Unit

  override fun setTextureShader(
    bitmapId: Int,
    tileX: Short,
    tileY: Short,
    filterMode: Short,
    maxAnisotropy: Short,
  ) = Unit

  override fun setPathEffect(pathEffect: FloatArray?) = Unit
}

/** As AndroidX's own test context (`remote-core`'s `HeadlessRemoteContext`): state, no platform. */
private class RecordingRemoteContext : RemoteContext() {
  private val objects = HashMap<Int, Any>()
  private val texts = HashMap<Int, String>()
  private val paths = HashMap<Int, FloatArray>()

  override fun loadPathData(instanceId: Int, winding: Int, floatPath: FloatArray) {
    paths[instanceId] = floatPath
    mRemoteComposeState.putPathData(instanceId, floatPath)
  }

  override fun getPathData(instanceId: Int): FloatArray? =
    paths[instanceId] ?: mRemoteComposeState.getPathData(instanceId)

  override fun loadVariableName(varName: String, varId: Int, varType: Int) = Unit

  override fun loadColor(id: Int, color: Int) {
    mRemoteComposeState.updateColor(id, color)
  }

  override fun getColor(id: Int): Int = mRemoteComposeState.getColor(id)

  override fun setNamedColorOverride(colorName: String, color: Int) = Unit

  override fun setNamedStringOverride(stringName: String, value: String) = Unit

  override fun clearNamedStringOverride(stringName: String) = Unit

  override fun setNamedBooleanOverride(booleanName: String, value: Boolean) = Unit

  override fun clearNamedBooleanOverride(booleanName: String) = Unit

  override fun setNamedIntegerOverride(integerName: String, value: Int) = Unit

  override fun clearNamedIntegerOverride(integerName: String) = Unit

  override fun setNamedFloatOverride(floatName: String, value: Float) = Unit

  override fun clearNamedFloatOverride(floatName: String) = Unit

  override fun setNamedLong(name: String, value: Long) = Unit

  override fun setNamedDataOverride(dataName: String, value: Any) = Unit

  override fun clearNamedDataOverride(dataName: String) = Unit

  override fun addCollection(id: Int, collection: ArrayAccess) {
    mRemoteComposeState.addCollection(id, collection)
  }

  override fun putDataMap(id: Int, map: DataMap) {
    mRemoteComposeState.putDataMap(id, map)
  }

  override fun getDataMap(id: Int): DataMap? = mRemoteComposeState.getDataMap(id)

  override fun runAction(id: Int, metadata: String) = Unit

  override fun runNamedAction(textId: Int, value: Any?) = Unit

  override fun putObject(key: Int, command: Any) {
    objects[key] = command
  }

  override fun getObject(key: Int): Any? = objects[key]

  override fun hapticEffect(type: Int) = Unit

  override fun loadBitmap(
    imageId: Int,
    encoding: Short,
    type: Short,
    width: Int,
    height: Int,
    bitmap: ByteArray,
  ) = Unit

  override fun loadText(id: Int, text: String) {
    texts[id] = text
    mRemoteComposeState.cacheData(id, text)
  }

  override fun getText(id: Int): String? = texts[id] ?: mRemoteComposeState.getFromId(id) as? String

  override fun loadFloat(id: Int, value: Float) {
    mRemoteComposeState.updateFloat(id, value)
  }

  override fun overrideFloat(id: Int, value: Float) {
    mRemoteComposeState.overrideFloat(id, value)
  }

  override fun loadInteger(id: Int, value: Int) {
    mRemoteComposeState.updateInteger(id, value)
  }

  override fun overrideInteger(id: Int, value: Int) {
    mRemoteComposeState.overrideInteger(id, value)
  }

  override fun overrideText(id: Int, valueId: Int) = Unit

  override fun loadAnimatedFloat(id: Int, animatedFloat: FloatExpression) {
    mRemoteComposeState.cacheData(id, animatedFloat)
  }

  override fun loadShader(id: Int, value: ShaderData) = Unit

  override fun getFloat(id: Int): Float = mRemoteComposeState.getFloat(id)

  override fun getInteger(id: Int): Int = mRemoteComposeState.getInteger(id)

  override fun getLong(id: Int): Long = 0L

  override fun listensTo(id: Int, variableSupport: VariableSupport) {
    mRemoteComposeState.listenToVar(id, variableSupport)
  }

  override fun updateOps(): Int = mRemoteComposeState.getOpsToUpdate(this, currentTime)

  override fun getShader(id: Int): ShaderData? = null

  override fun addClickArea(
    id: Int,
    contentDescriptionId: Int,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    metadataId: Int,
  ) = Unit
}

package androidx.compose.remote.creation.common

/** Platform-neutral encoding of a Remote Compose paint delta. */
public class PaintBundleData {
  private val values = mutableListOf<Int>()

  public fun setTextSize(value: Float): PaintBundleData = value(TextSize, value)

  public fun setColor(argb: Int): PaintBundleData = value(Color, argb)

  public fun setColorId(id: Int): PaintBundleData = value(ColorId, id)

  public fun setStrokeWidth(value: Float): PaintBundleData = value(StrokeWidth, value)

  public fun setStrokeMiter(value: Float): PaintBundleData = value(StrokeMiter, value)

  public fun setStrokeCap(value: Int): PaintBundleData = command(StrokeCap, value)

  public fun setStrokeJoin(value: Int): PaintBundleData = command(StrokeJoin, value)

  public fun setStyle(value: Int): PaintBundleData = command(Style, value)

  public fun setImageFilterQuality(value: Int): PaintBundleData = command(ImageFilterQuality, value)

  public fun setBlendMode(value: Int): PaintBundleData = command(BlendMode, value)

  public fun setAntiAlias(value: Boolean): PaintBundleData = command(AntiAlias, if (value) 1 else 0)

  public fun setFilterBitmap(value: Boolean): PaintBundleData =
    command(FilterBitmap, if (value) 1 else 0)

  public fun setAlpha(value: Float): PaintBundleData = value(Alpha, value)

  public fun setShader(shaderId: Int): PaintBundleData = value(Shader, shaderId)

  public fun setShaderMatrix(matrixId: Float): PaintBundleData = value(ShaderMatrix, matrixId)

  public fun setColorFilter(color: Int, mode: Int): PaintBundleData =
    value(ColorFilter or (mode shl 16), color)

  public fun setColorFilterId(colorId: Int, mode: Int): PaintBundleData =
    value(ColorFilterId or (mode shl 16), colorId)

  public fun clearColorFilter(): PaintBundleData = appendCommand(ClearColorFilter)

  public fun setTextStyle(
    fontType: Int,
    weight: Int,
    italic: Boolean,
    fontData: Boolean = false,
  ): PaintBundleData {
    val style = (weight and 0x3ff) or (if (italic) 2048 else 0) or (if (fontData) 1024 else 0)
    return value(Typeface or (style shl 16), fontType)
  }

  public fun setFallbackTypeface(fontType: Int, weight: Int, italic: Boolean): PaintBundleData {
    val style = (weight and 0x3ff) or (if (italic) 2048 else 0)
    return value(FallbackTypeface or (style shl 16), fontType)
  }

  public fun setFontAxes(tags: IntArray, axisValues: FloatArray): PaintBundleData {
    require(tags.size == axisValues.size)
    require(tags.size <= 8)
    append(FontAxis or (tags.size shl 16))
    tags.indices.forEach { index ->
      append(tags[index])
      append(axisValues[index].toRawBits())
    }
    return this
  }

  public fun setPathEffect(pathEffect: FloatArray?): PaintBundleData {
    require((pathEffect?.size ?: 0) <= 2028)
    append(PathEffect or ((pathEffect?.size ?: 0) shl 16))
    pathEffect?.forEach { append(it.toRawBits()) }
    return this
  }

  public fun setLinearGradient(
    colors: IntArray,
    idMask: Int,
    stops: FloatArray?,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    tileMode: Int,
  ): PaintBundleData =
    gradient(LinearGradient, colors, idMask, stops) {
      append(startX.toRawBits())
      append(startY.toRawBits())
      append(endX.toRawBits())
      append(endY.toRawBits())
      append(tileMode)
    }

  public fun setRadialGradient(
    colors: IntArray,
    idMask: Int,
    stops: FloatArray?,
    centerX: Float,
    centerY: Float,
    radius: Float,
    tileMode: Int,
  ): PaintBundleData =
    gradient(RadialGradient, colors, idMask, stops) {
      append(centerX.toRawBits())
      append(centerY.toRawBits())
      append(radius.toRawBits())
      append(tileMode)
    }

  public fun setSweepGradient(
    colors: IntArray,
    idMask: Int,
    stops: FloatArray?,
    centerX: Float,
    centerY: Float,
  ): PaintBundleData =
    gradient(SweepGradient, colors, idMask, stops) {
      append(centerX.toRawBits())
      append(centerY.toRawBits())
    }

  public fun setTextureShader(
    bitmapId: Int,
    tileModeX: Short,
    tileModeY: Short,
    filterMode: Short,
    maxAnisotropy: Short,
  ): PaintBundleData {
    append(Texture)
    append(bitmapId)
    append((tileModeX.toInt() and 0xffff) or (tileModeY.toInt() shl 16))
    append((filterMode.toInt() and 0xffff) or (maxAnisotropy.toInt() shl 16))
    return this
  }

  public fun toIntArray(): IntArray = values.toIntArray()

  private fun command(type: Int, value: Int): PaintBundleData =
    appendCommand(type or (value shl 16))

  private fun value(type: Int, value: Int): PaintBundleData {
    append(type)
    append(value)
    return this
  }

  private fun value(type: Int, value: Float): PaintBundleData = value(type, value.toRawBits())

  private fun appendCommand(command: Int): PaintBundleData {
    append(command)
    return this
  }

  private fun gradient(
    type: Int,
    colors: IntArray,
    idMask: Int,
    stops: FloatArray?,
    tail: PaintBundleData.() -> Unit,
  ): PaintBundleData {
    require(colors.size <= 255)
    append(Gradient or (type shl 16))
    append((idMask shl 16) or colors.size)
    colors.forEach(::append)
    append(stops?.size ?: 0)
    stops?.forEach { append(it.toRawBits()) }
    tail()
    return this
  }

  private fun append(value: Int) {
    values += value
  }

  public companion object {
    public const val FONT_TYPE_DEFAULT: Int = 0
    public const val FONT_TYPE_SANS_SERIF: Int = 1
    public const val FONT_TYPE_SERIF: Int = 2
    public const val FONT_TYPE_MONOSPACE: Int = 3

    public const val TextSize: Int = 1
    public const val Color: Int = 4
    public const val StrokeWidth: Int = 5
    public const val StrokeMiter: Int = 6
    public const val StrokeCap: Int = 7
    public const val Style: Int = 8
    public const val Shader: Int = 9
    public const val ImageFilterQuality: Int = 10
    public const val Gradient: Int = 11
    public const val Alpha: Int = 12
    public const val ColorFilter: Int = 13
    public const val AntiAlias: Int = 14
    public const val StrokeJoin: Int = 15
    public const val Typeface: Int = 16
    public const val FilterBitmap: Int = 17
    public const val BlendMode: Int = 18
    public const val ColorId: Int = 19
    public const val ColorFilterId: Int = 20
    public const val ClearColorFilter: Int = 21
    public const val ShaderMatrix: Int = 22
    public const val FontAxis: Int = 23
    public const val Texture: Int = 24
    public const val PathEffect: Int = 25
    public const val FallbackTypeface: Int = 26
    public const val LinearGradient: Int = 0
    public const val RadialGradient: Int = 1
    public const val SweepGradient: Int = 2
  }
}

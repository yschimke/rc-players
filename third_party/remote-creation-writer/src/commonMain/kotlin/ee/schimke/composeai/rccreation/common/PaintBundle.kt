package ee.schimke.composeai.rccreation.common

/** Platform-neutral encoding of a Remote Compose paint delta. */
public class PaintBundle {
  private val values = mutableListOf<Int>()

  public fun setTextSize(value: Float): PaintBundle = value(TextSize, value)

  public fun setColor(argb: Int): PaintBundle = value(Color, argb)

  public fun setColorId(id: Int): PaintBundle = value(ColorId, id)

  public fun setStrokeWidth(value: Float): PaintBundle = value(StrokeWidth, value)

  public fun setStrokeMiter(value: Float): PaintBundle = value(StrokeMiter, value)

  public fun setStrokeCap(value: Int): PaintBundle = command(StrokeCap, value)

  public fun setStrokeJoin(value: Int): PaintBundle = command(StrokeJoin, value)

  public fun setStyle(value: Int): PaintBundle = command(Style, value)

  public fun setImageFilterQuality(value: Int): PaintBundle = command(ImageFilterQuality, value)

  public fun setBlendMode(value: Int): PaintBundle = command(BlendMode, value)

  public fun setAntiAlias(value: Boolean): PaintBundle = command(AntiAlias, if (value) 1 else 0)

  public fun setFilterBitmap(value: Boolean): PaintBundle =
    command(FilterBitmap, if (value) 1 else 0)

  public fun setAlpha(value: Float): PaintBundle = value(Alpha, value)

  public fun setShader(shaderId: Int): PaintBundle = value(Shader, shaderId)

  public fun setShaderMatrix(matrixId: Float): PaintBundle = value(ShaderMatrix, matrixId)

  public fun setColorFilter(color: Int, mode: Int): PaintBundle =
    value(ColorFilter or (mode shl 16), color)

  public fun setColorFilterId(colorId: Int, mode: Int): PaintBundle =
    value(ColorFilterId or (mode shl 16), colorId)

  public fun clearColorFilter(): PaintBundle = appendCommand(ClearColorFilter)

  public fun setTextStyle(
    fontType: Int,
    weight: Int,
    italic: Boolean,
    fontData: Boolean = false,
  ): PaintBundle {
    val style = (weight and 0x3ff) or (if (italic) 2048 else 0) or (if (fontData) 1024 else 0)
    return value(Typeface or (style shl 16), fontType)
  }

  public fun setFallbackTypeface(fontType: Int, weight: Int, italic: Boolean): PaintBundle {
    val style = (weight and 0x3ff) or (if (italic) 2048 else 0)
    return value(FallbackTypeface or (style shl 16), fontType)
  }

  public fun setTextAxis(tags: IntArray, axisValues: FloatArray): PaintBundle {
    require(tags.size == axisValues.size)
    require(tags.size <= 8)
    append(FontAxis or (tags.size shl 16))
    tags.indices.forEach { index ->
      append(tags[index])
      append(axisValues[index].toRawBits())
    }
    return this
  }

  public fun setPathEffect(pathEffect: FloatArray?): PaintBundle {
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
  ): PaintBundle =
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
  ): PaintBundle =
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
  ): PaintBundle =
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
  ): PaintBundle {
    append(Texture)
    append(bitmapId)
    append((tileModeX.toInt() and 0xffff) or (tileModeY.toInt() shl 16))
    append((filterMode.toInt() and 0xffff) or (maxAnisotropy.toInt() shl 16))
    return this
  }

  public fun toIntArray(): IntArray = values.toIntArray()

  /** Forgets every value set, as `PaintBundle.reset`. */
  public fun reset() {
    values.clear()
  }

  private fun command(type: Int, value: Int): PaintBundle = appendCommand(type or (value shl 16))

  private fun value(type: Int, value: Int): PaintBundle {
    append(type)
    append(value)
    return this
  }

  private fun value(type: Int, value: Float): PaintBundle = value(type, value.toRawBits())

  private fun appendCommand(command: Int): PaintBundle {
    append(command)
    return this
  }

  private fun gradient(
    type: Int,
    colors: IntArray,
    idMask: Int,
    stops: FloatArray?,
    tail: PaintBundle.() -> Unit,
  ): PaintBundle {
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
    public const val BLEND_MODE_CLEAR: Int = 0
    public const val BLEND_MODE_SRC: Int = 1
    public const val BLEND_MODE_DST: Int = 2
    public const val BLEND_MODE_SRC_OVER: Int = 3
    public const val BLEND_MODE_DST_OVER: Int = 4
    public const val BLEND_MODE_SRC_IN: Int = 5
    public const val BLEND_MODE_DST_IN: Int = 6
    public const val BLEND_MODE_SRC_OUT: Int = 7
    public const val BLEND_MODE_DST_OUT: Int = 8
    public const val BLEND_MODE_SRC_ATOP: Int = 9
    public const val BLEND_MODE_DST_ATOP: Int = 10
    public const val BLEND_MODE_XOR: Int = 11
    public const val BLEND_MODE_PLUS: Int = 12
    public const val BLEND_MODE_MODULATE: Int = 13
    public const val BLEND_MODE_SCREEN: Int = 14
    public const val BLEND_MODE_OVERLAY: Int = 15
    public const val BLEND_MODE_DARKEN: Int = 16
    public const val BLEND_MODE_LIGHTEN: Int = 17
    public const val BLEND_MODE_COLOR_DODGE: Int = 18
    public const val BLEND_MODE_COLOR_BURN: Int = 19
    public const val BLEND_MODE_HARD_LIGHT: Int = 20
    public const val BLEND_MODE_SOFT_LIGHT: Int = 21
    public const val BLEND_MODE_DIFFERENCE: Int = 22
    public const val BLEND_MODE_EXCLUSION: Int = 23
    public const val BLEND_MODE_MULTIPLY: Int = 24
    public const val BLEND_MODE_HUE: Int = 25
    public const val BLEND_MODE_SATURATION: Int = 26
    public const val BLEND_MODE_COLOR: Int = 27
    public const val BLEND_MODE_LUMINOSITY: Int = 28
    public const val STYLE_FILL: Int = 0
    public const val STYLE_STROKE: Int = 1
    public const val STYLE_FILL_AND_STROKE: Int = 2

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

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontGlyph
import ee.schimke.composeai.rcplayer.protocol.RcBitmapTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRun
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRunOnPath
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapTextAnchored
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import kotlin.math.PI
import kotlin.math.atan2

internal data class RcBitmapFontBounds(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
)

internal data class RcBitmapGlyphPlacement(
  val bitmapId: Int,
  val left: Float,
  val top: Float,
  val width: Float,
  val height: Float,
  val angleDegrees: Float = 0f,
  val rotationCenter: Offset = Offset.Zero,
)

/** AndroidX bitmap-font measurement, including its trailing glyph-spacing convention. */
internal fun measureBitmapText(
  font: RcBitmapFontData,
  text: String,
  glyphSpacing: Float,
): RcBitmapFontBounds {
  var right = 0f
  var top = 1000f
  var bottom = -Float.MAX_VALUE
  var position = 0
  var previous = ""
  while (position < text.length) {
    val glyph = font.lookupGlyph(text, position)
    if (glyph == null) {
      position++
      previous = ""
      continue
    }
    position += glyph.chars.length
    right += glyph.marginLeft + glyph.marginRight
    if (glyph.bitmapId != -1) right += glyph.bitmapWidth
    right += font.kerning[previous + glyph.chars] ?: 0
    top = minOf(top, glyph.marginTop.toFloat())
    bottom = maxOf(bottom, (glyph.bitmapHeight + glyph.marginTop + glyph.marginBottom).toFloat())
    previous = glyph.chars
    right += glyphSpacing
  }
  return RcBitmapFontBounds(0f, top, right, bottom)
}

internal fun layoutBitmapTextRun(
  font: RcBitmapFontData,
  text: String,
  x: Float,
  y: Float,
  glyphSpacing: Float,
): List<RcBitmapGlyphPlacement> {
  val placements = mutableListOf<RcBitmapGlyphPlacement>()
  var xPosition = x
  var position = 0
  var previous = ""
  while (position < text.length) {
    val glyph = font.lookupGlyph(text, position)
    if (glyph == null) {
      position++
      previous = ""
      continue
    }
    position += glyph.chars.length
    if (glyph.bitmapId == -1) {
      xPosition += glyph.marginLeft + glyph.marginRight
      previous = glyph.chars
      continue
    }
    xPosition += glyph.marginLeft + (font.kerning[previous + glyph.chars] ?: 0)
    placements += glyph.placement(xPosition, y + glyph.marginTop)
    xPosition += glyph.bitmapWidth + glyph.marginRight + glyphSpacing
    previous = glyph.chars
  }
  return placements
}

internal fun layoutAnchoredBitmapText(
  font: RcBitmapFontData,
  text: String,
  x: Float,
  y: Float,
  panX: Float,
  panY: Float,
  glyphSpacing: Float,
): List<RcBitmapGlyphPlacement> {
  // The anchored AndroidX operation deliberately measures without kerning, then applies kerning
  // while drawing. Retaining that asymmetry is observable for centred and right-aligned text.
  val bounds = measureAnchoredBitmapText(font, text, glyphSpacing)
  val horizontal = -(bounds.right - bounds.left) * (1f + panX) / 2f - bounds.left
  val vertical = -(bounds.bottom - bounds.top) * (1f - panY) / 2f - bounds.top
  return layoutBitmapTextRun(font, text, x + horizontal, y + vertical, glyphSpacing)
}

private fun measureAnchoredBitmapText(
  font: RcBitmapFontData,
  text: String,
  glyphSpacing: Float,
): RcBitmapFontBounds {
  var right = 0f
  var top = 1000f
  var bottom = -Float.MAX_VALUE
  var position = 0
  while (position < text.length) {
    val glyph = font.lookupGlyph(text, position)
    if (glyph == null) {
      position++
      continue
    }
    position += glyph.chars.length
    right += glyph.marginLeft + glyph.marginRight
    if (glyph.bitmapId != -1) right += glyph.bitmapWidth
    top = minOf(top, glyph.marginTop.toFloat())
    bottom = maxOf(bottom, (glyph.bitmapHeight + glyph.marginTop + glyph.marginBottom).toFloat())
    right += glyphSpacing
  }
  return RcBitmapFontBounds(0f, top, right, bottom)
}

internal fun layoutBitmapTextOnPath(
  font: RcBitmapFontData,
  text: String,
  path: Path,
  yAdjustment: Float,
  glyphSpacing: Float,
): List<RcBitmapGlyphPlacement> {
  val width = bitmapPathRunWidth(font, text)
  if (width <= 0f) return emptyList()
  val measure = PathMeasure().apply { setPath(path, false) }
  if (measure.length <= 0f) return emptyList()
  val placements = mutableListOf<RcBitmapGlyphPlacement>()
  var progress = 0f
  var position = 0
  var previous = ""
  while (position < text.length) {
    val glyph = font.lookupGlyph(text, position)
    if (glyph == null) {
      position++
      previous = ""
      continue
    }
    position += glyph.chars.length
    if (glyph.bitmapId == -1) {
      progress += glyph.marginLeft + glyph.marginRight
      previous = ""
      continue
    }
    progress += glyph.marginLeft + (font.kerning[previous + glyph.chars] ?: 0)
    val halfWidth = glyph.bitmapWidth / 2f
    val distance = (measure.length * ((progress + halfWidth) / width)) % measure.length
    val center = measure.getPosition(distance)
    val tangent = measure.getTangent(distance)
    placements +=
      RcBitmapGlyphPlacement(
        bitmapId = glyph.bitmapId,
        left = center.x - halfWidth,
        top = center.y + yAdjustment + glyph.marginTop,
        width = glyph.bitmapWidth.toFloat(),
        height = glyph.bitmapHeight.toFloat(),
        angleDegrees = atan2(tangent.y, tangent.x) * 180f / PI.toFloat(),
        rotationCenter = center,
      )
    progress += glyph.bitmapWidth + glyph.marginRight + glyphSpacing
    previous = glyph.chars
  }
  return placements
}

private fun bitmapPathRunWidth(font: RcBitmapFontData, text: String): Float {
  var width = 0f
  var position = 0
  var previous = ""
  while (position < text.length) {
    val glyph = font.lookupGlyph(text, position)
    if (glyph == null) {
      position++
      previous = ""
      continue
    }
    position += glyph.chars.length
    if (glyph.bitmapId == -1) {
      width += glyph.marginLeft + glyph.marginRight
      previous = ""
      continue
    }
    width += glyph.marginLeft + (font.kerning[previous + glyph.chars] ?: 0)
    width += glyph.bitmapWidth + glyph.marginRight
    previous = glyph.chars
  }
  return width
}

private fun RcBitmapFontGlyph.placement(x: Float, y: Float): RcBitmapGlyphPlacement =
  RcBitmapGlyphPlacement(
    bitmapId = bitmapId,
    left = x,
    top = y,
    width = bitmapWidth.toFloat(),
    height = bitmapHeight.toFloat(),
  )

internal fun DrawScope.drawBitmapFontTextRun(
  operation: RcDrawBitmapFontTextRun,
  state: RcPlayerState,
  images: Map<Int, ImageBitmap>,
  alpha: Float,
  blendMode: BlendMode,
) {
  val font = state.bitmapFont(operation.bitmapFontId) ?: return
  val text = state.bitmapTextSlice(operation.textId, operation.start, operation.end) ?: return
  drawBitmapGlyphs(
    layoutBitmapTextRun(
      font,
      text,
      state.resolve(operation.x),
      state.resolve(operation.y),
      state.resolve(operation.glyphSpacing),
    ),
    images,
    alpha,
    blendMode,
  )
}

internal fun DrawScope.drawAnchoredBitmapText(
  operation: RcDrawBitmapTextAnchored,
  state: RcPlayerState,
  images: Map<Int, ImageBitmap>,
  alpha: Float,
  blendMode: BlendMode,
) {
  val font = state.bitmapFont(operation.bitmapFontId) ?: return
  val text =
    state.bitmapTextSlice(
      operation.textId,
      state.resolve(operation.start).toInt(),
      state.resolve(operation.end).toInt(),
    ) ?: return
  drawBitmapGlyphs(
    layoutAnchoredBitmapText(
      font,
      text,
      state.resolve(operation.x),
      state.resolve(operation.y),
      state.resolve(operation.panX),
      state.resolve(operation.panY),
      state.resolve(operation.glyphSpacing),
    ),
    images,
    alpha,
    blendMode,
  )
}

internal fun DrawScope.drawBitmapFontTextOnPath(
  operation: RcDrawBitmapFontTextRunOnPath,
  path: Path,
  state: RcPlayerState,
  images: Map<Int, ImageBitmap>,
  alpha: Float,
  blendMode: BlendMode,
) {
  val font = state.bitmapFont(operation.bitmapFontId) ?: return
  val text = state.bitmapTextSlice(operation.textId, operation.start, operation.end) ?: return
  drawBitmapGlyphs(
    layoutBitmapTextOnPath(
      font,
      text,
      path,
      state.resolve(operation.yAdjustment),
      state.resolve(operation.glyphSpacing),
    ),
    images,
    alpha,
    blendMode,
  )
}

internal fun applyBitmapTextMeasure(operation: RcBitmapTextMeasure, state: RcPlayerState) {
  val font = state.bitmapFont(operation.bitmapFontId) ?: return
  val text = state.text(operation.textId) ?: return
  val bounds = measureBitmapText(font, text, state.resolve(operation.glyphSpacing))
  selectTextMeasurement(
      operation.type,
      bounds.left,
      bounds.top,
      bounds.right,
      bounds.bottom,
      text.length,
      supportsLength = false,
    )
    ?.let { state.setFloat(operation.outId, it) }
}

internal fun resolveBitmapFontPathId(id: Int, state: RcPlayerState): Int =
  if (id and BITMAP_FONT_POINTER_FLAG != 0) state.integer(id and BITMAP_FONT_ID_MASK) ?: 0
  else id and BITMAP_FONT_ID_MASK

private fun RcPlayerState.bitmapFont(id: Int): RcBitmapFontData? =
  document.operations.filterIsInstance<RcBitmapFontData>().lastOrNull { it.fontId == id }

private fun RcPlayerState.bitmapTextSlice(textId: Int, start: Int, end: Int): String? {
  val text = text(textId) ?: return null
  val safeStart = start.coerceIn(0, text.length)
  val safeEnd = if (end < 0 || end > text.length) text.length else end.coerceAtLeast(safeStart)
  return text.substring(safeStart, safeEnd)
}

private fun DrawScope.drawBitmapGlyphs(
  placements: List<RcBitmapGlyphPlacement>,
  images: Map<Int, ImageBitmap>,
  alpha: Float,
  blendMode: BlendMode,
) {
  placements.forEach { placement ->
    val image = images[placement.bitmapId] ?: return@forEach
    if (placement.width <= 0f || placement.height <= 0f) return@forEach
    withTransform({
      if (placement.angleDegrees != 0f) {
        rotate(placement.angleDegrees, placement.rotationCenter)
      }
      translate(placement.left, placement.top)
      scale(placement.width / image.width, placement.height / image.height, Offset.Zero)
    }) {
      drawImage(image, Offset.Zero, alpha = alpha, blendMode = blendMode)
    }
  }
}

private const val BITMAP_FONT_POINTER_FLAG = 0x40000000
private const val BITMAP_FONT_ID_MASK = 0xffff

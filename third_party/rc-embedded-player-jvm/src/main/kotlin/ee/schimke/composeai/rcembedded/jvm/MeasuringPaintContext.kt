/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("RestrictedApiAndroidX")

package ee.schimke.composeai.rcembedded.jvm

import androidx.compose.remote.core.PaintContext
import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.operations.paint.PaintBundle
import androidx.compose.ui.text.font.FontStyle
import ee.schimke.composeai.rcembedded.player.ComposeLocalPaint
import ee.schimke.composeai.rcembedded.player.measureTextBounds
import ee.schimke.composeai.rcembedded.player.toTextPaintSpec
import ee.schimke.composeai.rcembedded.player.updatePaintFromBundle

/**
 * A [PaintContext] that draws nothing but **measures text for real**, so AndroidX's own layout pass
 * can be run headless.
 *
 * ### Why this is not [ee.schimke.composeai.rcembedded.player.GraphPaintContext]
 *
 * There is already a draw-nothing paint context in this module, and it is the wrong tool here. Its
 * own documentation says why: *"an op that genuinely needs to measure text cannot be evaluated this
 * way, and gets nothing rather than a fabricated result."* `getTextBounds` writes nothing and
 * `layoutComplexText` returns null, which is correct for evaluating a colour attribute and fatal
 * for laying out a document — every text component would measure zero and every box around it would
 * come out the wrong size.
 *
 * A layout pass is not a draw pass, but it is also not a value evaluation: it needs the text engine
 * and nothing else. So this context drops every drawing, clipping, matrix and layer call, and
 * routes text measurement to the same skiko text stack that `RcPlayerJvm` rasterizes with. That is
 * the property that makes the result usable — geometry measured with a *different* text engine from
 * the one that draws would agree with neither the corpus nor this player's own pixels.
 *
 * ### The one thing it cannot do, and why that is reported rather than guessed
 *
 * `layoutComplexText` has no counterpart on this side: the skiko seam measures runs, not the
 * multi-line wrapped layout AndroidX's `ComputedTextLayout` carries. Returning null would let a
 * document that wraps text lay out as though the text were empty, and the resulting tree would be
 * diffed as a layout disagreement rather than read as the unmeasured thing it is — the exact
 * failure mode `AndroidxJvmEngine` declined to emit a tree at all to avoid.
 *
 * So the call is recorded in [measuredComplexText]. A caller that asked for geometry must check it
 * and report the tree as unobserved when it is set. Silence here would be a fabricated number, and
 * a reference lane's fabricated number is worse than no reference at all: it would mark a real
 * disagreement as shared and hide the finding.
 *
 * ### Sub-pixel divergence, stated once
 *
 * Android fills its ink bounds into an integer `Rect`, rounding outward; the skiko measurement is
 * fractional (see `measureTextInkBounds`). Bounds here can therefore be a fraction tighter than the
 * framework's. That is the same known divergence the raster lanes already carry, not a second one.
 */
internal class MeasuringPaintContext(context: RemoteContext) : PaintContext(context) {

  /**
   * Whether layout consulted the complex-text path this context cannot answer.
   *
   * Sticky for the lifetime of the context: once a document has laid out one paragraph against a
   * null layout, nothing later in the same pass makes the geometry trustworthy again.
   */
  var measuredComplexText: Boolean = false
    private set

  private var paintState = ComposeLocalPaint()

  /**
   * The saved text state, innermost last.
   *
   * Only the fields text measurement reads are saved, rather than a copy of the whole paint state.
   * That is not a shortcut: nothing else in this context is observable, so saving a stroke cap it
   * cannot draw would be dead weight pretending to be fidelity.
   */
  private val saved = mutableListOf<TextState>()

  private class TextState(
    val textSize: Float,
    val isTextSizeSet: Boolean,
    val fontFamily: Int,
    val isTypefaceSet: Boolean,
    val fontWeight: Int,
    val fontStyle: FontStyle,
    val color: Int,
    val alpha: Float,
  )

  override fun getText(id: Int): String? = mContext.getText(id)

  /**
   * Ink and font bounds of `[start, end)`, following AndroidX `AndroidPaintContext.getTextBounds`'
   * flag order exactly — the flags are read from the document, so a different order is a different
   * measurement rather than a different style.
   *
   * `bounds` is filled as left, top, right, bottom. `0x04` replaces the horizontal box with the
   * advance from the origin, `0x01` makes the right edge advance-relative to the ink left, and
   * `0x02` swaps the vertical box for the font's rounded ascent and descent.
   */
  override fun getTextBounds(
    textId: Int,
    start: Int,
    end: Int,
    flags: Int,
    bounds: FloatArray,
  ) {
    val text = getText(textId)
    if (text == null) {
      // Not an error: an unresolved text id is a document that has not loaded it yet, and the
      // framework zeroes the box rather than failing the frame.
      bounds.fill(0f, 0, MEASURED_BOUNDS)
      return
    }
    val to = if (end == -1 || end > text.length) text.length else end
    val from = start.coerceIn(0, to)
    val measured =
      measureTextBounds(text.substring(from, to), paintState.toTextPaintSpec(), mContext)
    if (flags and USE_ADVANCE_FROM_ORIGIN != 0) {
      bounds[0] = 0f
      bounds[2] = measured.advance
    } else {
      bounds[0] = measured.left
      bounds[2] =
        if (flags and ADVANCE_FROM_INK_LEFT != 0) measured.advance - measured.left
        else measured.right
    }
    if (flags and USE_FONT_METRICS != 0) {
      bounds[1] = measured.fontTop
      bounds[3] = measured.fontBottom
    } else {
      bounds[1] = measured.top
      bounds[3] = measured.bottom
    }
  }

  override fun layoutComplexText(
    textId: Int,
    fontId: Int,
    fontStyle: Int,
    fontWeight: Int,
    fontFamilyId: Int,
    textAlign: Int,
    fontSize: Float,
    letterSpacing: Float,
    lineHeight: Float,
    width: Float,
    height: Float,
    maxLines: Int,
    overflow: Int,
    flags: Int,
    hinting: Boolean,
    ltr: Boolean,
    scaling: Int,
  ): RcPlatformServices.ComputedTextLayout? {
    measuredComplexText = true
    return null
  }

  // ---------------------------------------------------------------- paint state

  override fun applyPaint(paint: PaintBundle) {
    updatePaintFromBundle(paint, paintState, mContext)
  }

  /**
   * Mirrors the framework's `replacePaint`: reset, then apply — not a merge onto what was there.
   */
  override fun replacePaint(paint: PaintBundle) {
    paintState = ComposeLocalPaint()
    updatePaintFromBundle(paint, paintState, mContext)
  }

  override fun savePaint() {
    saved +=
      TextState(
        textSize = paintState.textSize,
        isTextSizeSet = paintState.isTextSizeSet,
        fontFamily = paintState.fontFamily,
        isTypefaceSet = paintState.isTypefaceSet,
        fontWeight = paintState.fontWeight,
        fontStyle = paintState.fontStyle,
        color = paintState.color,
        alpha = paintState.alpha,
      )
  }

  override fun restorePaint() {
    val state = saved.removeLastOrNull() ?: return
    paintState.textSize = state.textSize
    paintState.isTextSizeSet = state.isTextSizeSet
    paintState.fontFamily = state.fontFamily
    paintState.isTypefaceSet = state.isTypefaceSet
    paintState.fontWeight = state.fontWeight
    paintState.fontStyle = state.fontStyle
    paintState.color = state.color
    paintState.alpha = state.alpha
  }

  override fun reset() {
    paintState = ComposeLocalPaint()
    saved.clear()
  }

  // ------------------------------------------------- dropped: a layout pass draws nothing
  //
  // Every member below is a draw, clip, matrix or layer instruction. They are dropped by design:
  // this context exists so a document can be *measured*, and measuring must not paint.

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
  ) {}

  override fun drawBitmap(imageId: Int, left: Float, top: Float, right: Float, bottom: Float) {}

  override fun drawToBitmap(bitmapId: Int, mode: Int, color: Int) {}

  override fun scale(scaleX: Float, scaleY: Float) {}

  override fun translate(translateX: Float, translateY: Float) {}

  override fun drawArc(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    startAngle: Float,
    sweepAngle: Float,
  ) {}

  override fun drawSector(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    startAngle: Float,
    sweepAngle: Float,
  ) {}

  override fun drawCircle(centerX: Float, centerY: Float, radius: Float) {}

  override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) {}

  override fun drawOval(left: Float, top: Float, right: Float, bottom: Float) {}

  override fun drawPath(id: Int, start: Float, end: Float) {}

  override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) {}

  override fun drawRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radiusX: Float,
    radiusY: Float,
  ) {}

  override fun drawTextOnPath(textId: Int, pathId: Int, hOffset: Float, vOffset: Float) {}

  override fun drawTextRun(
    textId: Int,
    start: Int,
    end: Int,
    contextStart: Int,
    contextEnd: Int,
    x: Float,
    y: Float,
    rtl: Boolean,
  ) {}

  override fun drawComplexText(computedTextLayout: RcPlatformServices.ComputedTextLayout?) {}

  override fun drawTweenPath(
    path1Id: Int,
    path2Id: Int,
    tween: Float,
    start: Float,
    stop: Float,
  ) {}

  override fun tweenPath(out: Int, path1: Int, path2: Int, tween: Float) {}

  override fun combinePath(out: Int, path1: Int, path2: Int, operation: Byte) {}

  override fun matrixFromPath(pathId: Int, fraction: Float, offset: Float, flags: Int) {}

  override fun matrixScale(scaleX: Float, scaleY: Float, centerX: Float, centerY: Float) {}

  override fun matrixTranslate(translateX: Float, translateY: Float) {}

  override fun matrixSkew(skewX: Float, skewY: Float) {}

  override fun matrixRotate(rotate: Float, pivotX: Float, pivotY: Float) {}

  override fun matrixSave() {}

  override fun matrixRestore() {}

  override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {}

  override fun clipPath(pathId: Int, regionOp: Int) {}

  override fun roundedClipRect(
    width: Float,
    height: Float,
    topStart: Float,
    topEnd: Float,
    bottomStart: Float,
    bottomEnd: Float,
  ) {}

  override fun startGraphicsLayer(w: Int, h: Int) {}

  override fun setGraphicsLayer(attributes: java.util.HashMap<Int, Any>) {}

  override fun endGraphicsLayer() {}

  private companion object {
    /** `bounds` is left, top, right, bottom. */
    const val MEASURED_BOUNDS = 4

    /** `0x01` — the right edge is the advance, taken relative to the ink left. */
    const val ADVANCE_FROM_INK_LEFT = 0x01

    /** `0x02` — the vertical box is the font's rounded ascent and descent, not the ink. */
    const val USE_FONT_METRICS = 0x02

    /** `0x04` — the horizontal box is `0 … advance`, ignoring where the ink starts. */
    const val USE_ADVANCE_FROM_ORIGIN = 0x04
  }
}

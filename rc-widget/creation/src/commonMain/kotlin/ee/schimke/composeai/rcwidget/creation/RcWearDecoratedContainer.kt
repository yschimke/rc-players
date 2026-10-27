@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.creation

import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.drawWithContent
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.shaders.RemoteBrush
import androidx.compose.remote.creation.compose.shaders.horizontalGradient
import androidx.compose.remote.creation.compose.shaders.linearGradient
import androidx.compose.remote.creation.compose.shaders.radialGradient
import androidx.compose.remote.creation.compose.shaders.sweepGradient
import androidx.compose.remote.creation.compose.shaders.verticalGradient
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteDp
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.clamp
import androidx.compose.remote.creation.compose.state.max
import androidx.compose.remote.creation.compose.state.min
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * What a Wear widget container is painted with: a [base] fill, [layers] painted over it in order,
 * and an [edge] drawn on top. On the watch a widget's surface is the container, so this is what
 * tells one design from another at a glance.
 *
 * It holds no shape: the container's shape is the watch's widget host's, and stays it on every
 * device. Every layer is drawn to fit inside it at the host's own corner radius: glows and
 * gradients are its rounded rect, bands and edges follow its outline. So the same background fits a
 * pill, a round watch's 26dp container and a square one's 4dp. Positions are fractions of the
 * container, for the same reason.
 *
 * Every layer kind plays in the Wear widget profile. Glance Wear's own container brush can only
 * paint colours and straight gradients, so a direct Glance capture keeps the [base] and its
 * [RcWearGradient] layers and leaves the rest out.
 */
public data class RcWearBackground(
  val base: Color,
  val layers: List<RcWearLayer> = emptyList(),
  val edge: RcWearEdge? = null,
)

/** Something painted over an [RcWearBackground]'s base, across the whole container. */
public sealed interface RcWearLayer

/** A straight gradient across the container: top to bottom when [vertical], else start to end. */
public data class RcWearGradient(val colors: List<Color>, val vertical: Boolean = true) :
  RcWearLayer

/** A gradient from the top start corner to the bottom end corner. */
public data class RcWearDiagonal(val colors: List<Color>) : RcWearLayer

/**
 * A round glow: [colors] from the centre at ([x], [y]) outwards to [radius], each a fraction of the
 * container's width (positions may fall outside it, for a glow that rises from an edge).
 */
public data class RcWearGlow(
  val colors: List<Color>,
  val x: Float,
  val y: Float,
  val radius: Float,
) : RcWearLayer

/**
 * A band [thicknessDp] wide along one [side] of the container, following its outline: the top or
 * bottom edge from corner to corner, or round the start corners and edge (an elbow).
 */
public data class RcWearBand(val color: Color, val side: Side, val thicknessDp: Float) :
  RcWearLayer {
  public enum class Side {
    Top,
    Bottom,
    Start,
  }
}

/**
 * A line [widthDp] wide following the container's outline, [insetDp] inside it. More than one
 * colour sweeps round the container (a gradient stroke).
 */
public data class RcWearEdge(val colors: List<Color>, val widthDp: Float, val insetDp: Float = 0f)

/**
 * A reading the container shows as a level: [fraction] of the way from empty to full, a live value
 * the caller binds (a named value the host pushes, or one a widget bakes) and animates as it likes.
 * A fill grows from the start, or rises from the bottom when [vertical].
 */
public data class RcWearLevel(val fraction: RemoteFloat, val vertical: Boolean = false) {
  public companion object {
    /** [value] between [min] and [max], clamped, as a level. */
    public fun of(
      value: RemoteFloat,
      min: Float,
      max: Float,
      vertical: Boolean = false,
    ): RcWearLevel = RcWearLevel(clamp((value - min.rf) / (max - min).rf, 0f, 1f), vertical)
  }
}

/**
 * How a level shows, in [color] at [alpha].
 *
 * A ring ([ringDp] above 0) runs the reading round the container, 4dp inside its outline, in the
 * container's padding: it never sits under the content, so it is drawn at full strength over a
 * faint track. A fill covers the container up to the reading, under the content, so its [alpha]
 * should be the strongest at which the content keeps its contrast on it ([rcReadableAlpha]).
 *
 * @property ringDp the ring's width, or 0 for a fill.
 * @property track whether a ring's track shows the whole way round under its reading.
 * @property pointer whether a ring marks its reading with a short pointer, as a dial does.
 * @property sweep colours a ring's reading sweeps through, in place of [color]; empty for [color].
 * @property segments how many cells a fill is cut into (an LED column, an LCD bar); 0 for none.
 * @property ghosts whether a fill's unlit cells show faintly.
 * @property marker whether a fill's leading edge is ruled with a line in [color].
 * @property markerAlpha the marker's alpha over the fill.
 * @property fades whether a fill rises from transparent to [color] towards its leading edge.
 */
public data class RcWearLevelLook(
  val color: Color,
  val alpha: Float,
  val ringDp: Float = 0f,
  val track: Boolean = true,
  val pointer: Boolean = false,
  val sweep: List<Color> = emptyList(),
  val segments: Int = 0,
  val ghosts: Boolean = false,
  val marker: Boolean = false,
  val markerAlpha: Float = 0f,
  val fades: Boolean = false,
)

/**
 * A Wear widget container's content: [fill], then [background]'s layers, then [level] in [look],
 * then [background]'s edge, with [content] over them. The container's padding is the caller's
 * ([RcWearContainerFrame], [RcWearTemplateContainer]), so all of them use the whole container.
 *
 * The container's shape is the host's and stays it: everything is drawn to fit inside the rounded
 * rect of [cornerRadius], nothing is clipped by it or drawn outside it. Fills and glows are that
 * rounded rect; bands, edges and a ring follow its outline inwards; a fill level is the rounded
 * rect cut straight along its reading. Pass the template's named `wear.container.corner_radius` (as
 * [RcWearTemplateContainer] does) so the host's own radius shapes it.
 */
@Composable
public fun RcWearDecoratedContainer(
  background: RcWearBackground?,
  fill: RemoteColor,
  cornerRadius: RemoteDp,
  modifier: RemoteModifier = RemoteModifier,
  level: RcWearLevel? = null,
  look: RcWearLevelLook? = null,
  content: @Composable () -> Unit,
) {
  val cornerPx = cornerRadius.toPx()
  val gapPx = SEGMENT_GAP_DP.rdp.toPx()
  val markerPx = MARKER_DP.rdp.toPx()
  val ringInsetPx = RING_INSET_DP.rdp.toPx()
  val ringPx = (look?.ringDp ?: 0f).rdp.toPx()
  val edgeWidthPx = background?.edge?.widthDp?.rdp?.toPx()
  val edgeInsetPx = background?.edge?.insetDp?.rdp?.toPx()
  val bandPx =
    background?.layers.orEmpty().filterIsInstance<RcWearBand>().map { it.thicknessDp.rdp.toPx() }
  RemoteBox(
    modifier =
      modifier.fillMaxSize().drawWithContent {
        val corner = RemoteOffset(cornerPx, cornerPx)
        drawRoundRect(RemotePaint { color = fill }, cornerRadius = corner)
        var band = 0
        for (layer in background?.layers.orEmpty()) {
          if (layer is RcWearBand) {
            val thickness = bandPx[band++]
            outline(cornerPx, 0f.rf, thickness).drawSide(layer, thickness)
          } else {
            drawRoundRect(shaded(layer), cornerRadius = corner)
          }
        }
        if (level != null && look != null) {
          if (look.ringDp > 0f) {
            drawRing(look, level.fraction, outline(cornerPx, ringInsetPx, ringPx), ringPx)
          } else {
            drawFill(level, look, level.fraction, cornerPx, gapPx, markerPx)
          }
        }
        background?.edge?.let { edge ->
          val paint = stroke(edgeWidthPx!!, edge.colors.first().rc)
          if (edge.colors.size > 1) {
            with(RemoteBrush.sweepGradient(edge.colors.map { it.rc })) { applyTo(paint, size) }
          }
          outline(cornerPx, edgeInsetPx!!, edgeWidthPx).drawAll(paint)
        }
        drawContent()
      }
  ) {
    content()
  }
}

/**
 * The strongest alpha up to [max] at which [color] over [base] keeps [primary] text at 4.5:1 and
 * [secondary] at 3:1 (WCAG AA for normal text, and for large text and graphics), with a margin for
 * a state tint mixed onto the base.
 */
public fun rcReadableAlpha(
  base: Color,
  color: Color,
  primary: Color,
  secondary: Color,
  max: Float,
): Float {
  var alpha = max
  while (alpha > 0f) {
    val filled = lerp(base, color, alpha)
    if (
      rcContrast(primary, filled) >= PRIMARY_CONTRAST &&
        rcContrast(secondary, filled) >= SECONDARY_CONTRAST
    )
      return alpha
    alpha -= 0.02f
  }
  return 0f
}

/** The WCAG contrast ratio between [a] and [b]. */
public fun rcContrast(a: Color, b: Color): Float {
  val la = a.luminance()
  val lb = b.luminance()
  return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

private const val PRIMARY_CONTRAST = 4.5f + 0.5f
private const val SECONDARY_CONTRAST = 3f + 0.3f

private fun RemoteDrawScope.shaded(layer: RcWearLayer): RemotePaint {
  val brush =
    when (layer) {
      is RcWearGradient -> {
        val stops = layer.colors.map { it.rc }
        if (layer.vertical) RemoteBrush.verticalGradient(stops)
        else RemoteBrush.horizontalGradient(stops)
      }
      is RcWearDiagonal ->
        RemoteBrush.linearGradient(
          layer.colors.map { it.rc },
          RemoteOffset(0f.rf, 0f.rf),
          RemoteOffset(size.width, size.height),
        )
      is RcWearGlow ->
        RemoteBrush.radialGradient(
          layer.colors.map { it.rc },
          center = RemoteOffset(size.width * layer.x, size.height * layer.y),
          radius = size.width * layer.radius,
        )
      is RcWearBand -> error("a band is a stroke")
    }
  return RemotePaint().also { paint -> with(brush) { applyTo(paint, size) } }
}

private fun stroke(width: RemoteFloat, color: RemoteColor) = RemotePaint {
  style = PaintingStyle.Stroke
  strokeWidth = width
  this.color = color
}

/**
 * The container's outline, moved [inset] inwards and then by half of [width], so a stroke [width]
 * wide drawn along it lies [inset] inside the container, whatever its corner radius: a pill, a
 * round watch's 26dp container or a square one's 4dp.
 *
 * It runs clockwise from the middle of the top edge, through nine pieces: the top edge's right
 * half, the top end corner, the end edge, the bottom end corner, the bottom edge, the bottom start
 * corner, the start edge, the top start corner and the top edge's left half. Positions along it are
 * fractions of its length, so any range of it can be drawn ([drawRange]) — a reading's share of a
 * ring, one side for a band.
 */
private class Outline(
  private val scope: RemoteDrawScope,
  corner: RemoteFloat,
  inset: RemoteFloat,
  width: RemoteFloat,
) {
  private val o = inset + width / 2f.rf
  private val w = scope.size.width
  private val h = scope.size.height
  private val left = o
  private val top = o
  private val right = w - o
  private val bottom = h - o

  // A host's stadium says "as round as it gets" with a huge radius (Galaxy's 999dp); the lines
  // and arcs below are built from it, so it is clamped to half the outline's shorter side.
  private val r = min(max(corner - o, 0f), min(right - left, bottom - top) / 2f.rf)
  private val horizontal = max(right - left - r * 2f, 0f)
  private val vertical = max(bottom - top - r * 2f, 0f)
  private val arc = r * QUARTER_TURN
  private val length = (horizontal + vertical) * 2f + arc * 4f

  private sealed interface Piece {
    val length: RemoteFloat
  }

  private class Line(
    val from: RemoteOffset,
    val to: RemoteOffset,
    override val length: RemoteFloat,
  ) : Piece

  private class Arc(
    val centre: RemoteOffset,
    val startAngle: Float,
    override val length: RemoteFloat,
  ) : Piece

  private val pieces: List<Piece> =
    listOf(
      Line(RemoteOffset(w / 2f.rf, top), RemoteOffset(right - r, top), horizontal / 2f.rf),
      Arc(RemoteOffset(right - r, top + r), 270f, arc),
      Line(RemoteOffset(right, top + r), RemoteOffset(right, bottom - r), vertical),
      Arc(RemoteOffset(right - r, bottom - r), 0f, arc),
      Line(RemoteOffset(right - r, bottom), RemoteOffset(left + r, bottom), horizontal),
      Arc(RemoteOffset(left + r, bottom - r), 90f, arc),
      Line(RemoteOffset(left, bottom - r), RemoteOffset(left, top + r), vertical),
      Arc(RemoteOffset(left + r, top + r), 180f, arc),
      Line(RemoteOffset(left + r, top), RemoteOffset(w / 2f.rf, top), horizontal / 2f.rf),
    )

  /**
   * The pieces of one [RcWearBand.Side]: the top edge (its two halves), the bottom edge, or the
   * start corners and edge.
   */
  private fun piecesOf(side: RcWearBand.Side): List<Int> =
    when (side) {
      RcWearBand.Side.Top -> listOf(8, 0)
      RcWearBand.Side.Bottom -> listOf(4)
      RcWearBand.Side.Start -> listOf(5, 6, 7)
    }

  /** Strokes the whole outline. */
  fun drawAll(paint: RemotePaint) {
    scope.drawRoundRect(
      paint,
      RemoteOffset(left, top),
      RemoteSize(right - left, bottom - top),
      RemoteOffset(r, r),
    )
  }

  /** Strokes [band] [width] wide: along the top or bottom edge, or round the start. */
  fun drawSide(band: RcWearBand, width: RemoteFloat) {
    val paint = stroke(width, band.color.rc)
    for (i in piecesOf(band.side)) drawPiece(pieces[i], paint, 0f.rf, 1f.rf)
  }

  /** Strokes the outline from [from] to [to], fractions of its length clockwise from the top. */
  fun drawRange(paint: RemotePaint, from: RemoteFloat, to: RemoteFloat) {
    var start: RemoteFloat = 0f.rf
    for (piece in pieces) {
      // The share of this piece the range covers, guarded against a piece of no length (a pill's
      // straight ends).
      val span = max(piece.length, MIN_PIECE)
      val a = clamp((from * length - start) / span, 0f, 1f)
      val b = clamp((to * length - start) / span, 0f, 1f)
      drawPiece(piece, paint, a, b)
      start = start + piece.length
    }
  }

  private fun drawPiece(piece: Piece, paint: RemotePaint, a: RemoteFloat, b: RemoteFloat) {
    when (piece) {
      is Line -> {
        val dx = piece.to.x - piece.from.x
        val dy = piece.to.y - piece.from.y
        scope.drawLine(
          paint,
          RemoteOffset(piece.from.x + dx * a, piece.from.y + dy * a),
          RemoteOffset(piece.from.x + dx * b, piece.from.y + dy * b),
        )
      }
      is Arc ->
        scope.drawArc(
          paint,
          piece.startAngle.rf + a * 90f,
          (b - a) * 90f,
          false,
          RemoteOffset(piece.centre.x - r, piece.centre.y - r),
          RemoteSize(r * 2f, r * 2f),
        )
    }
  }
}

private fun RemoteDrawScope.outline(corner: RemoteFloat, inset: RemoteFloat, width: RemoteFloat) =
  Outline(this, corner, inset, width)

/**
 * [level] as a ring round the container ([RING_INSET_DP] in, [width] wide): a faint track the whole
 * way round, and the reading in full strength from the top, clockwise — or a dial's pointer at the
 * reading. The ring lies in the container's padding, outside the card's text, so it needs no
 * contrast cap.
 */
private fun RemoteDrawScope.drawRing(
  look: RcWearLevelLook,
  fraction: RemoteFloat,
  ring: Outline,
  width: RemoteFloat,
) {
  if (look.track) ring.drawAll(stroke(width, look.color.copy(alpha = look.alpha * TRACK_SHARE).rc))
  val reading = stroke(width, look.color.copy(alpha = look.alpha).rc)
  if (look.sweep.isNotEmpty()) {
    with(RemoteBrush.sweepGradient(look.sweep.map { it.copy(alpha = look.alpha).rc })) {
      applyTo(reading, size)
    }
  }
  val from = if (look.pointer) max(fraction - POINTER_SHARE, 0f) else 0f.rf
  ring.drawRange(reading, from, fraction)
}

/**
 * [level] as a fill: the container's rounded rect, cut straight along the reading — from the start
 * for a battery or a percentage, rising from the bottom for humidity — so it keeps the container's
 * corners wherever it reaches them. Cut into [RcWearLevelLook.segments] cells, each the rounded
 * rect cut to the cell.
 */
private fun RemoteDrawScope.drawFill(
  level: RcWearLevel,
  look: RcWearLevelLook,
  fraction: RemoteFloat,
  corner: RemoteFloat,
  gap: RemoteFloat,
  marker: RemoteFloat,
) {
  val width = size.width
  val height = size.height
  val radius = RemoteOffset(corner, corner)
  val solid = RemotePaint { color = look.color.copy(alpha = look.alpha).rc }
  // The container's rounded rect within the straight cut from [from] to [to] along the fill.
  fun cut(paint: RemotePaint, from: RemoteFloat, to: RemoteFloat) {
    if (level.vertical) {
      clipRect(0f.rf, height - to, width, height - from) {
        drawRoundRect(paint, cornerRadius = radius)
      }
    } else {
      clipRect(from, 0f.rf, to, height) { drawRoundRect(paint, cornerRadius = radius) }
    }
  }
  val extent = if (level.vertical) height else width
  if (look.segments > 0) {
    val n = look.segments
    val cell = (extent - gap * (n - 1).toFloat()) / n.toFloat()
    val ghost = RemotePaint { color = look.color.copy(alpha = look.alpha * GHOST_SHARE).rc }
    for (i in 0 until n) {
      val lit = clamp(fraction * n.toFloat() - i.toFloat(), 0f, 1f)
      val start = (cell + gap) * i.toFloat()
      if (look.ghosts) cut(ghost, start, start + cell)
      cut(solid, start, start + cell * lit)
    }
    return
  }
  val reach = extent * fraction
  val paint =
    if (look.fades) {
      val from = if (level.vertical) RemoteOffset(0f.rf, height) else RemoteOffset(0f.rf, 0f.rf)
      val to =
        if (level.vertical) RemoteOffset(0f.rf, height - reach) else RemoteOffset(reach, 0f.rf)
      RemotePaint().also { paint ->
        with(
          RemoteBrush.linearGradient(
            listOf(look.color.copy(alpha = 0f).rc, look.color.copy(alpha = look.alpha).rc),
            from,
            to,
          )
        ) {
          applyTo(paint, size)
        }
      }
    } else solid
  cut(paint, 0f.rf, reach)
  if (look.marker) {
    // The fill's leading edge, a line across the container that stops where its outline does.
    val line = RemotePaint { color = look.color.copy(alpha = look.markerAlpha).rc }
    cut(line, reach - marker, reach)
  }
}

private const val SEGMENT_GAP_DP = 2f
private const val MARKER_DP = 2f

/** A ring's distance from the container's outline. */
private const val RING_INSET_DP = 4f

/** A dial pointer's length, as a share of the ring. */
private const val POINTER_SHARE = 0.05f

/** How strongly a ring's track shows, as a share of its reading's alpha. */
private const val TRACK_SHARE = 0.22f

/** How strongly an unlit cell shows, as a share of a lit one's alpha. */
private const val GHOST_SHARE = 0.3f

/** A quarter turn's arc length per unit radius. */
private const val QUARTER_TURN = (kotlin.math.PI / 2).toFloat()

/** The length a piece of no length is divided by, so its share is never NaN. */
private const val MIN_PIECE = 0.001f

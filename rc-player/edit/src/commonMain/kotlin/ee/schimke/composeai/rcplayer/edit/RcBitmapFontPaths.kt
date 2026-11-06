package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import kotlin.math.hypot

/**
 * A static, single-contour `PathData` as a polyline: curves split finely enough that a glyph set
 * along it sits where it would on the curve. Null for a path with a value the player sets, a conic,
 * or more than one contour.
 */
internal class RcPolyline private constructor(private val points: List<Pair<Float, Float>>) {
  /** Distance along the line to each point. */
  private val distances: List<Float> =
    points.indices
      .map { 0f }
      .toMutableList()
      .also { lengths ->
        for (i in 1 until points.size) {
          lengths[i] =
            lengths[i - 1] +
              hypot(points[i].first - points[i - 1].first, points[i].second - points[i - 1].second)
        }
      }

  val length: Float
    get() = distances.last()

  /** The part of the line from [start] to [end] along it, as `PathData` words with id [id]. */
  fun section(id: Int, start: Float, end: Float): RcPathData {
    val section = buildList {
      add(at(start))
      for (i in points.indices) if (distances[i] > start && distances[i] < end) add(points[i])
      add(at(end))
    }
    val words = buildList {
      add(command(RcPathCommands.MOVE))
      add(RcFloatWord.literal(section[0].first))
      add(RcFloatWord.literal(section[0].second))
      for (i in 1 until section.size) {
        add(command(RcPathCommands.LINE))
        // AndroidX's legacy padding: the point the line starts from.
        add(RcFloatWord.literal(section[i - 1].first))
        add(RcFloatWord.literal(section[i - 1].second))
        add(RcFloatWord.literal(section[i].first))
        add(RcFloatWord.literal(section[i].second))
      }
    }
    return RcPathData(id, words)
  }

  private fun at(distance: Float): Pair<Float, Float> {
    val clamped = distance.coerceIn(0f, length)
    val i = (1 until points.size).firstOrNull { distances[it] >= clamped } ?: return points.last()
    val span = distances[i] - distances[i - 1]
    val t = if (span == 0f) 0f else (clamped - distances[i - 1]) / span
    return points[i - 1].first + (points[i].first - points[i - 1].first) * t to
      points[i - 1].second + (points[i].second - points[i - 1].second) * t
  }

  companion object {
    private const val CURVE_STEPS = 32

    fun of(path: RcPathData): RcPolyline? {
      val words = path.words
      val points = mutableListOf<Pair<Float, Float>>()
      var index = 0
      var contourStart: Pair<Float, Float>? = null
      fun literal(): Float? {
        val word = words.getOrNull(index++) ?: return null
        return word.value.takeIf { word.referencedId == null }
      }
      fun point(): Pair<Float, Float>? {
        val x = literal() ?: return null
        val y = literal() ?: return null
        return x to y
      }
      while (index < words.size) {
        val command = words[index++].referencedId ?: return null
        when (command) {
          RcPathCommands.MOVE -> {
            if (points.size > 1) return null
            val start = point() ?: return null
            points.clear()
            points += start
            contourStart = start
          }
          RcPathCommands.LINE -> {
            index += 2
            points += point() ?: return null
          }
          RcPathCommands.QUADRATIC -> {
            index += 2
            val from = points.lastOrNull() ?: return null
            val control = point() ?: return null
            val to = point() ?: return null
            for (step in 1..CURVE_STEPS) {
              val t = step.toFloat() / CURVE_STEPS
              val u = 1 - t
              points +=
                u * u * from.first + 2 * u * t * control.first + t * t * to.first to
                  u * u * from.second + 2 * u * t * control.second + t * t * to.second
            }
          }
          RcPathCommands.CUBIC -> {
            index += 2
            val from = points.lastOrNull() ?: return null
            val c1 = point() ?: return null
            val c2 = point() ?: return null
            val to = point() ?: return null
            for (step in 1..CURVE_STEPS) {
              val t = step.toFloat() / CURVE_STEPS
              val u = 1 - t
              val a = u * u * u
              val b = 3 * u * u * t
              val c = 3 * u * t * t
              val d = t * t * t
              points +=
                a * from.first + b * c1.first + c * c2.first + d * to.first to
                  a * from.second + b * c1.second + c * c2.second + d * to.second
            }
          }
          RcPathCommands.CLOSE -> points += contourStart ?: return null
          RcPathCommands.DONE -> break
          else -> return null
        }
      }
      return if (points.size < 2) null else RcPolyline(points)
    }

    private fun command(id: Int): RcFloatWord = RcFloatWord(0xff800000.toInt() or id)
  }
}

/**
 * The paint a canvas's draws read, as far as `PaintData` sets it from the start of the canvas:
 * enough to know which face, size and colour a `DrawTextOnPath` uses. [known] goes false where the
 * paint can't be read statically — a value the player sets, a conditional or loop around it, or a
 * nested container whose own paint the stream returns from.
 */
internal class RcPaintTrack {
  var known: Boolean = true
  var textSize: Float? = 36f
  var color: Int? = 0xff000000.toInt()
  var colorId: Int? = null
  var fontType: Int = 0
  var fontStyle: Int = 0
  /** What a text drawn as glyph images couldn't keep: a stroke, shader, gradient or filter. */
  var unsupported: Boolean = false
  val axes: MutableList<Pair<Int, Float>> = mutableListOf()

  fun apply(words: List<Int>) {
    var index = 0
    while (index < words.size) {
      val command = words[index++]
      val type = command and 0xffff
      fun word(): Int? = words.getOrNull(index++)
      when (type) {
        1 -> {
          val word = RcFloatWord(word() ?: return unknown())
          textSize = word.value.takeIf { word.referencedId == null }
          if (textSize == null) known = false
        }
        4 -> {
          color = word() ?: return unknown()
          colorId = null
        }
        19 -> {
          colorId = word() ?: return unknown()
          color = null
        }
        16 -> {
          fontStyle = command ushr 16
          fontType = word() ?: return unknown()
        }
        8 -> if (command ushr 16 == 1) unsupported = true
        9 -> if ((word() ?: return unknown()) != 0) unsupported = true
        13,
        20 -> {
          word() ?: return unknown()
          unsupported = true
        }
        21 -> unsupported = false
        5,
        12,
        22 -> word() ?: return unknown()
        7,
        10,
        14,
        15,
        17,
        18 -> Unit
        23 -> {
          axes.clear()
          repeat(command ushr 16) {
            val tag = word() ?: return unknown()
            val value = RcFloatWord(word() ?: return unknown())
            if (value.referencedId != null) known = false
            axes += tag to value.value
          }
        }
        else -> return unknown()
      }
    }
  }

  private fun unknown() {
    known = false
  }
}

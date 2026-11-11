package ee.schimke.composeai.rccreation.bridge

import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.vector.RemoteAnimatedVector
import ee.schimke.composeai.rccreation.compose.vector.RemoteAnimatedVectorGroup
import ee.schimke.composeai.rccreation.compose.vector.RemoteAnimatedVectorPath
import ee.schimke.composeai.rccreation.compose.vector.RemoteAnimatedVectorTarget
import ee.schimke.composeai.rccreation.compose.vector.RemoteInterpolator
import ee.schimke.composeai.rccreation.compose.vector.RemoteKeyframe
import ee.schimke.composeai.rccreation.compose.vector.RemotePropertyAnimator
import ee.schimke.composeai.rccreation.compose.vector.painterRemoteAnimatedVector
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An animated vector's value at a time is one thing, whichever way it is drawn: the iteration and
 * its direction first, then the animator's interpolator, then the interpolator of the keyframe
 * segment the result falls in.
 *
 * Both ways are read through AndroidX's own `CoreDocument` (`remote-core` 1.0.0-alpha23) painting a
 * frame into a context that records its calls. `RemoteAnimatedVector.draw(writer, progress)` works
 * the values out when it writes, so the document holds numbers. `painterRemoteAnimatedVector`
 * writes expressions over a progress the player supplies, so AndroidX evaluates them.
 */
class AnimatedVectorEvaluationTest {
  /** A group whose rotation [rotation] animates, around a triangle whose fill [fill] animates. */
  private fun vector(
    rotation: List<RemotePropertyAnimator> = emptyList(),
    fill: List<RemotePropertyAnimator> = emptyList(),
  ) =
    RemoteAnimatedVector(
      name = "v",
      width = 48f,
      height = 48f,
      viewportWidth = 24f,
      viewportHeight = 24f,
      rootGroup =
        RemoteAnimatedVectorGroup(
          name = "g",
          rotation = 5f,
          children =
            listOf(
              RemoteAnimatedVectorPath(
                name = "p",
                pathData = "M0,0 L24,0 L12,24 Z",
                fillColor = BLACK,
              ),
            ),
        ),
      targets =
        listOf(RemoteAnimatedVectorTarget("g", rotation), RemoteAnimatedVectorTarget("p", fill)),
    )

  /** What AndroidX draws: the group's rotation and the triangle's colour. */
  private class Drawn(val rotation: Float, val color: Int) {
    override fun toString() = "rotation $rotation, colour #${color.toUInt().toString(16)}"
  }

  private fun drawn(bytes: ByteArray): Drawn {
    val calls = androidxPaints(bytes, 48, 48)
    return Drawn(
      rotation = calls.first { it.name == "matrixRotate" }.arguments[0],
      color = calls.single { it.name == "path" }.color,
    )
  }

  /** `RemoteAnimatedVector.draw(writer, progress)`: evaluated as it is written. */
  private fun written(vector: RemoteAnimatedVector, progress: Float): Drawn {
    val writer = RemoteDocumentWriter(48, 48)
    writer.root { vector.draw(this, progress) }
    return drawn(writer.encodeToByteArray())
  }

  /** `painterRemoteAnimatedVector(vector, progress)`: expressions, evaluated by AndroidX. */
  private fun painted(vector: RemoteAnimatedVector, progress: Float): Drawn =
    drawn(
      captureCanvasBytes(48, 48) {
        val named = RemoteFloat.createNamedRemoteFloat("progress", progress)
        with(painterRemoteAnimatedVector(vector, named)) { onDraw() }
      },
    )

  private fun rotation(
    duration: Int,
    startDelay: Int = 0,
    repeatCount: Int = 0,
    repeatMode: Int = RemotePropertyAnimator.REPEAT_MODE_RESTART,
    keyframes: List<RemoteKeyframe> = listOf(RemoteKeyframe(0f, 10f), RemoteKeyframe(1f, 110f)),
    interpolator: RemoteInterpolator = RemoteInterpolator.Linear,
  ) =
    RemotePropertyAnimator(
      "rotation",
      duration,
      startDelay,
      repeatCount,
      repeatMode,
      keyframes,
      interpolator,
    )

  private fun fill(
    duration: Int,
    repeatCount: Int = 0,
    repeatMode: Int = RemotePropertyAnimator.REPEAT_MODE_RESTART,
    keyframes: List<RemoteKeyframe> =
      listOf(RemoteKeyframe(0f, BLACK), RemoteKeyframe(1f, 0xFF0000FF.toInt())),
    interpolator: RemoteInterpolator = RemoteInterpolator.Linear,
  ) =
    RemotePropertyAnimator(
      "fillColor",
      duration,
      repeatCount = repeatCount,
      repeatMode = repeatMode,
      keyframes = keyframes,
      interpolator = interpolator,
    )

  /**
   * The example from review: 100 ms from 0 to 1, repeated once in reverse, is 200 ms long, and
   * three quarters of the way along it is half way back. The painter held the end value, 1.
   */
  @Test
  fun aRepeatingAnimatorRunsBackInReverseMode() {
    val reversing =
      vector(
        rotation =
          listOf(
            rotation(
              duration = 100,
              repeatCount = 1,
              repeatMode = RemotePropertyAnimator.REPEAT_MODE_REVERSE,
              keyframes = listOf(RemoteKeyframe(0f, 1f), RemoteKeyframe(1f, 2f)),
            ),
          ),
      )
    assertEquals(200, reversing.totalDuration)
    assertEquals(1.5f, painted(reversing, 0.25f).rotation, 0.001f)
    assertEquals(2f, painted(reversing, 0.5f).rotation, 0.001f)
    assertEquals(1.5f, painted(reversing, 0.75f).rotation, 0.001f)
    assertEquals(1f, painted(reversing, 1f).rotation, 0.001f)
  }

  /** In restart mode each iteration starts again from the first keyframe, and the last ends. */
  @Test
  fun aRepeatingAnimatorStartsAgainInRestartMode() {
    val restarting =
      vector(
        rotation =
          listOf(
            rotation(
              duration = 100,
              repeatCount = 2,
              keyframes = listOf(RemoteKeyframe(0f, 1f), RemoteKeyframe(1f, 2f)),
            ),
          ),
      )
    assertEquals(300, restarting.totalDuration)
    assertEquals(1.5f, painted(restarting, 0.5f / 3f).rotation, 0.001f)
    assertEquals(1.5f, painted(restarting, 1.5f / 3f).rotation, 0.001f)
    assertEquals(1.75f, painted(restarting, 2.75f / 3f).rotation, 0.001f)
    assertEquals(2f, painted(restarting, 1f).rotation, 0.001f)
  }

  /**
   * The other example: from 0 to 1 with `Accelerate` on the animator and plain keyframes is a
   * quarter of the way half way through. `evaluateAt` returned a half.
   */
  @Test
  fun theAnimatorsInterpolatorIsApplied() {
    val accelerating =
      RemotePropertyAnimator(
        "rotation",
        duration = 100,
        keyframes = listOf(RemoteKeyframe(0f, 0f), RemoteKeyframe(1f, 1f)),
        interpolator = RemoteInterpolator.Accelerate(),
      )
    assertEquals(0.25f, accelerating.evaluateAt(50, 0.5f) as Float, 0.0001f)
    val drawnAccelerating =
      vector(
        rotation =
          listOf(
            rotation(
              duration = 100,
              keyframes = listOf(RemoteKeyframe(0f, 1f), RemoteKeyframe(1f, 2f)),
              interpolator = RemoteInterpolator.Accelerate(),
            ),
          ),
      )
    assertEquals(1.25f, written(drawnAccelerating, 0.5f).rotation, 0.001f)
    assertEquals(1.25f, painted(drawnAccelerating, 0.5f).rotation, 0.001f)
  }

  /** The keyframe's interpolator after the animator's: `Accelerate` twice is the fourth power. */
  @Test
  fun theKeyframesInterpolatorIsAppliedAfterTheAnimators() {
    val twice =
      vector(
        rotation =
          listOf(
            rotation(
              duration = 100,
              keyframes =
                listOf(
                  RemoteKeyframe(0f, 1f),
                  RemoteKeyframe(1f, 17f, RemoteInterpolator.Accelerate()),
                ),
              interpolator = RemoteInterpolator.Accelerate(),
            ),
          ),
      )
    assertEquals(2f, written(twice, 0.5f).rotation, 0.001f)
    assertEquals(2f, painted(twice, 0.5f).rotation, 0.001f)
  }

  /**
   * The same animators through both ways of drawing, at several times: repeats in both modes, an
   * interpolator on the animator, on a keyframe and on both, more than two keyframes, a start
   * delay, and one animator after another on the same property.
   */
  @Test
  fun bothWaysOfDrawingAgree() {
    val reverse = RemotePropertyAnimator.REPEAT_MODE_REVERSE
    val threeKeyframes =
      listOf(
        RemoteKeyframe(0f, 10f),
        RemoteKeyframe(0.25f, 90f, RemoteInterpolator.Decelerate()),
        RemoteKeyframe(1f, 40f, RemoteInterpolator.FastOutSlowIn),
      )
    val threeColours =
      listOf(
        RemoteKeyframe(0f, BLACK),
        RemoteKeyframe(0.5f, 0xFF00FF00.toInt(), RemoteInterpolator.Accelerate()),
        RemoteKeyframe(1f, 0xFFFF0000.toInt()),
      )
    val vectors =
      mapOf(
        "plain" to vector(listOf(rotation(1000)), listOf(fill(1000))),
        "reversed once" to
          vector(
            listOf(rotation(1000, repeatCount = 1, repeatMode = reverse)),
            listOf(fill(1000, repeatCount = 1, repeatMode = reverse)),
          ),
        "reversed twice, against a longer animator" to
          vector(
            listOf(rotation(1000, repeatCount = 2, repeatMode = reverse)),
            listOf(fill(4000)),
          ),
        "restarted twice" to
          vector(listOf(rotation(1000, repeatCount = 2)), listOf(fill(1000, repeatCount = 2))),
        "the animator's interpolator" to
          vector(
            listOf(rotation(1000, interpolator = RemoteInterpolator.Accelerate())),
            listOf(fill(1000, interpolator = RemoteInterpolator.Decelerate())),
          ),
        "an interpolator with a factor" to
          vector(
            listOf(rotation(1000, interpolator = RemoteInterpolator.Accelerate(1.5f))),
            listOf(fill(1000, interpolator = RemoteInterpolator.Decelerate(2f))),
          ),
        "a keyframe's interpolator" to
          vector(
            listOf(
              rotation(
                1000,
                keyframes =
                  listOf(
                    RemoteKeyframe(0f, 10f),
                    RemoteKeyframe(1f, 110f, RemoteInterpolator.AccelerateDecelerate),
                  ),
              ),
            ),
            listOf(
              fill(
                1000,
                keyframes =
                  listOf(
                    RemoteKeyframe(0f, BLACK),
                    RemoteKeyframe(1f, 0xFF0000FF.toInt(), RemoteInterpolator.Accelerate()),
                  ),
              ),
            ),
          ),
        "three keyframes, reversed, eased, delayed" to
          vector(
            listOf(
              rotation(
                1000,
                startDelay = 500,
                repeatCount = 1,
                repeatMode = reverse,
                keyframes = threeKeyframes,
                interpolator = RemoteInterpolator.AccelerateDecelerate,
              ),
            ),
            listOf(
              fill(
                1250,
                repeatCount = 1,
                repeatMode = reverse,
                keyframes = threeColours,
                interpolator = RemoteInterpolator.LinearOutSlowIn,
              ),
            ),
          ),
        "one animator after a repeated one" to
          vector(
            listOf(
              rotation(500, repeatCount = 1, repeatMode = reverse),
              rotation(
                1000,
                startDelay = 1000,
                keyframes = listOf(RemoteKeyframe(0f, 10f), RemoteKeyframe(1f, 200f)),
              ),
            ),
            listOf(fill(2000)),
          ),
      )
    // Not on an iteration's boundary, where the value jumps and the two round differently.
    val times = listOf(0f, 0.07f, 0.2f, 0.31f, 0.45f, 0.58f, 0.7f, 0.83f, 0.96f, 1f)
    val failures = mutableListOf<String>()
    for ((name, vector) in vectors) {
      for (time in times) {
        val written = written(vector, time)
        val painted = painted(vector, time)
        if (!written.isCloseTo(painted)) {
          failures += "$name at $time: written $written, painted $painted"
        }
      }
    }
    assertTrue(failures.isEmpty(), failures.joinToString("\n", prefix = "\n"))
  }

  private fun Drawn.isCloseTo(other: Drawn): Boolean {
    fun channels(color: Int) = listOf(24, 16, 8, 0).map { (color ushr it) and 0xFF }
    return abs(rotation - other.rotation) <= 0.5f &&
      channels(color).zip(channels(other.color)).all { (a, b) -> abs(a - b) <= 3 }
  }

  private companion object {
    const val BLACK = 0xFF000000.toInt()
  }
}

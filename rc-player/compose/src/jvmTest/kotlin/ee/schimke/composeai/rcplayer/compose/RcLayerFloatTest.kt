package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * `rcLayerFloat` hands `RcModulateAlphaLayer.bind` the same reader for as long as what it captures
 * is unchanged, so a recomposition that changed nothing writes no snapshot state. Pixels cannot
 * show this, so it is checked on the readers themselves. A literal cannot be NaN (every NaN float
 * word is a variable reference); a NaN target only reaches the bits key through the clock branch
 * without a frame clock, which these tests do not drive (they always provide one).
 */
class RcLayerFloatTest {
  private val state =
    RcPlayerState(
      RcDocument(
        RcHeader(RcVersion(1, 1, 0), legacyWidth = 1, legacyHeight = 1, modern = false),
        listOf(RcFloatConstant(VARIABLE_ID, RcFloatWord.literal(3f))),
      ),
    )

  @Test
  fun anUnchangedRecompositionReturnsTheSameReader() {
    val cases =
      mapOf(
        "static default" to null,
        "static value" to float(RcFloatWord.literal(0.5f)),
        "referenced, not continuous" to float(reference(VARIABLE_ID)),
        "clock-driven" to float(reference(RcSystemVariables.CONTINUOUS_SEC)),
      )
    assertFalse(state.isContinuouslyDriven(VARIABLE_ID))
    assertTrue(state.isContinuouslyDriven(RcSystemVariables.CONTINUOUS_SEC))
    for ((name, attribute) in cases) {
      val readers = readersAcrossRecompositions(listOf(attribute, attribute, attribute))
      assertEquals(3, readers.size, name)
      assertSame(readers[0], readers[1], "$name: second recomposition")
      assertSame(readers[0], readers[2], "$name: third recomposition")
    }
  }

  @Test
  fun aClockDrivenValueWithoutAFrameClockKeepsItsReader() {
    // Without a frame clock the clock-driven reader is the resolved target, keyed by its bits.
    val attribute = float(reference(RcSystemVariables.CONTINUOUS_SEC))
    val readers =
      readersAcrossRecompositions(listOf(attribute, attribute, attribute), withFrameClock = false)
    assertSame(readers[0], readers[1])
    assertSame(readers[0], readers[2])
    assertEquals(state.resolve(attribute.value).toRawBits(), readers[2]().toRawBits())
  }

  @Test
  fun aHostWriteToAReferencedValueKeepsItsReader() {
    // The reader reads the eased value, so a write moves the value, not the reader.
    val attribute = float(reference(VARIABLE_ID))
    val readers =
      readersAcrossRecompositions(listOf(attribute, attribute, attribute)) { step ->
        state.setFloat(VARIABLE_ID, 3f + step)
      }
    assertSame(readers[0], readers[1])
    assertSame(readers[0], readers[2])
  }

  @Test
  fun aChangedValueGivesANewReader() {
    val readers =
      readersAcrossRecompositions(
        listOf(float(RcFloatWord.literal(0.5f)), float(RcFloatWord.literal(0.25f))),
      )
    assertNotSame(readers[0], readers[1])
    assertEquals(0.25f, readers[1]())
  }

  @Test
  fun negativeZeroIsAChangeFromZero() {
    // `==` says 0f and -0f are equal; the bits do not, so the reader reports the new sign.
    val readers =
      readersAcrossRecompositions(
        listOf(float(RcFloatWord.literal(0f)), float(RcFloatWord.literal(-0f))),
      )
    assertNotSame(readers[0], readers[1])
    assertEquals((-0f).toRawBits(), readers[1]().toRawBits())
  }

  /**
   * The reader `rcLayerFloat` returns in each composition: the first with `attributes[0]`, then one
   * forced recomposition per later attribute (exactly one composition each), under a frame clock as
   * the player provides one unless not [withFrameClock].
   */
  private fun readersAcrossRecompositions(
    attributes: List<RcGraphicsLayerAttribute?>,
    withFrameClock: Boolean = true,
    beforeStep: (Int) -> Unit = {},
  ): List<() -> Float> {
    val steps = mutableListOf<Int>()
    val readers = mutableListOf<() -> Float>()
    var attribute by mutableStateOf(attributes.first())
    var step by mutableIntStateOf(0)
    val frame = mutableFloatStateOf(0f)
    val scene =
      ImageComposeScene(width = 1, height = 1, density = Density(1f)) {
        CompositionLocalProvider(LocalRcFrameTime provides frame.takeIf { withFrameClock }) {
          Reader(attribute, step) { composedStep, reader ->
            steps += composedStep
            readers += reader
          }
        }
      }
    try {
      scene.render(0L)
      for ((index, next) in attributes.withIndex().drop(1)) {
        beforeStep(index)
        attribute = next
        step = index
        Snapshot.sendApplyNotifications()
        scene.render(index * FRAME_NANOS)
      }
    } finally {
      scene.close()
    }
    // One composition per step: an extra one (a snapshot write during composition) fails here.
    assertEquals(attributes.indices.toList(), steps)
    return readers
  }

  /**
   * Reports the reader `rcLayerFloat` returns with the recomposition's [step]. Reading [step] is
   * what keeps this composable from being skipped: Compose skips on unchanged parameters it reads.
   */
  @Composable
  private fun Reader(
    attribute: RcGraphicsLayerAttribute?,
    step: Int,
    onReader: (step: Int, reader: () -> Float) -> Unit,
  ) {
    val reader = rcLayerFloat(attribute, default = 1f, state = state)
    SideEffect { onReader(step, reader) }
  }

  private fun float(word: RcFloatWord) =
    RcGraphicsLayerAttribute.FloatValue(RcGraphicsLayerModifier.ALPHA, word)

  private fun reference(id: Int) = RcFloatWord(REFERENCE_BITS or id)

  private companion object {
    const val VARIABLE_ID = 42
    /** A float word's NaN payload that marks a variable reference. */
    const val REFERENCE_BITS = 0x7fc00000
    const val FRAME_NANOS = 16_000_000L
  }
}

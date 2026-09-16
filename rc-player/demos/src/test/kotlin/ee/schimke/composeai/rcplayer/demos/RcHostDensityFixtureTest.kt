package ee.schimke.composeai.rcplayer.demos

import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The deferred-density fixture, through a real encode/decode.
 *
 * Every other fixture this repository ships folds its capture density into literal constants, so
 * none of them reads [RcSystemVariables.DENSITY] or [RcSystemVariables.FONT_SIZE] and none of them
 * can tell a player that loads those ids from one that does not. The failure is silent by
 * construction — an unloaded reference resolves to its own raw `NaN` bits and every size derived
 * from it becomes `NaN`, so the text is simply never drawn.
 *
 * So this asserts the resolved number, at two different host densities, rather than that the
 * document merely decodes.
 */
class RcHostDensityFixtureTest {

  @Test
  fun theFixtureReadsBothDensityBuiltIns() {
    val document = RcDocumentCodec.decode(RcDocumentCodec.encode(RcDemoDocuments.hostDensityText()))
    val referenced =
      document.operations.filterIsInstance<RcFloatExpression>().flatMap { expression ->
        expression.expression.mapNotNull { it.referencedId }
      }

    assertTrue(
      RcSystemVariables.DENSITY in referenced,
      "the fixture does not defer its density; it would not catch a player that ignores the id",
    )
    assertTrue(RcSystemVariables.FONT_SIZE in referenced)

    // And the text really is sized by that expression rather than by a constant.
    val text = document.operations.filterIsInstance<RcTextLayout>().single()
    assertEquals(
      document.operations.filterIsInstance<RcFloatExpression>().single().id,
      text.fontSize.referencedId,
    )
  }

  @Test
  fun theDeferredTextSizeResolvesAgainstWhateverDensityTheHostSupplies() {
    val document = RcDocumentCodec.decode(RcDocumentCodec.encode(RcDemoDocuments.hostDensityText()))
    val expression = document.operations.filterIsInstance<RcFloatExpression>().single()
    val text = document.operations.filterIsInstance<RcTextLayout>().single()

    val state = RcPlayerState(document)
    state.beginFrame(timeSeconds = 0f, epochMillis = 0L)
    state.applyFloatExpression(expression)
    val unscaled = state.resolve(text.fontSize)

    assertFalse(unscaled.isNaN(), "the deferred text size resolved to NaN")
    // 15sp at a host that scales nothing.
    assertEquals(15f, unscaled)

    // A host with an accessibility text size: the document follows it, which is the entire reason
    // a capture defers instead of folding. The density cancels out of this expression by design —
    // what survives is the font scale.
    state.setHostDensity(density = 2f, fontScale = 1.5f)
    state.beginFrame(timeSeconds = 0f, epochMillis = 0L)
    state.applyFloatExpression(expression)

    assertEquals(15f * 1.5f, state.resolve(text.fontSize))
  }
}

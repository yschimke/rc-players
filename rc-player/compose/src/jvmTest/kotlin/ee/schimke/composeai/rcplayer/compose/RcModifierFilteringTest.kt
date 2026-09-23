package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcLayoutModifiers
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The ordered list and the scroll position have to move together.
 *
 * `scrollPosition` counts the operations that precede the scroll in wire order, so it is an index
 * into `ordered`. A filter that drops an operation from in front of it leaves the index pointing at
 * the wrong place — `padding -> scroll -> offset` became `[offset]` with the position still at 1,
 * and the scroll was then appended after the offset, reversing the two.
 */
class RcModifierFilteringTest {
  private val padding =
    RcPaddingModifier(
      RcFloatWord.literal(4f),
      RcFloatWord.literal(4f),
      RcFloatWord.literal(4f),
      RcFloatWord.literal(4f),
    )
  private val offset = RcOffsetModifier(RcFloatWord.literal(1f), RcFloatWord.literal(2f))
  private val fill = RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f))

  @Test
  fun droppingPaddingMovesTheScrollPositionWithIt() {
    val modifiers =
      RcLayoutModifiers(
        ordered = listOf(padding, offset),
        padding = listOf(padding),
        scrollPosition = 1,
      )
    val filtered = modifiers.withoutPadding()
    assertEquals(listOf(offset), filtered.ordered)
    assertEquals(
      0,
      filtered.scrollPosition,
      "the scroll position did not follow the removed padding",
    )
  }

  @Test
  fun droppingDimensionsMovesTheScrollPositionWithIt() {
    val modifiers =
      RcLayoutModifiers(ordered = listOf(fill, offset), width = fill, scrollPosition = 1)
    val filtered = modifiers.withoutDimensions()
    assertEquals(listOf(offset), filtered.ordered)
    assertEquals(0, filtered.scrollPosition)
  }

  @Test
  fun anOperationRemovedAfterTheScrollLeavesThePositionAlone() {
    val modifiers =
      RcLayoutModifiers(
        ordered = listOf(offset, padding),
        padding = listOf(padding),
        scrollPosition = 0,
      )
    val filtered = modifiers.withoutPadding()
    assertEquals(listOf(offset), filtered.ordered)
    assertEquals(0, filtered.scrollPosition)
  }

  @Test
  fun aDocumentWithoutScrollKeepsANullPosition() {
    val modifiers = RcLayoutModifiers(ordered = listOf(padding, offset), padding = listOf(padding))
    assertEquals(null, modifiers.withoutPadding().scrollPosition)
    assertEquals(listOf(offset), modifiers.withoutPadding().ordered)
  }
}

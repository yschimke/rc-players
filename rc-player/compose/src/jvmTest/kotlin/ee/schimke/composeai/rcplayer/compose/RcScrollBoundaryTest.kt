package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import ee.schimke.composeai.rcplayer.protocol.RcScrollModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class RcScrollBoundaryTest {
  @Test
  fun onlyTheScrollableAxisKeepsRemainderAndFling() = runTest {
    val vertical = RcScrollBoundaryConnection(RcScrollModifier.VERTICAL) { true }
    val horizontal = RcScrollBoundaryConnection(RcScrollModifier.HORIZONTAL) { true }
    val available = Offset(7f, 11f)
    assertEquals(
      Offset(0f, 11f),
      vertical.onPostScroll(Offset.Zero, available, NestedScrollSource.UserInput),
    )
    assertEquals(
      Offset(7f, 0f),
      horizontal.onPostScroll(Offset.Zero, available, NestedScrollSource.UserInput),
    )
    assertEquals(Velocity(0f, 11f), vertical.onPostFling(Velocity.Zero, Velocity(7f, 11f)))
    assertEquals(Velocity(7f, 0f), horizontal.onPostFling(Velocity.Zero, Velocity(7f, 11f)))
  }

  @Test
  fun contentThatFitsPassesScrollToTheHost() = runTest {
    val connection = RcScrollBoundaryConnection(RcScrollModifier.VERTICAL) { false }
    assertEquals(
      Offset.Zero,
      connection.onPostScroll(Offset.Zero, Offset(7f, 11f), NestedScrollSource.UserInput),
    )
    assertEquals(Velocity.Zero, connection.onPostFling(Velocity.Zero, Velocity(7f, 11f)))
  }
}

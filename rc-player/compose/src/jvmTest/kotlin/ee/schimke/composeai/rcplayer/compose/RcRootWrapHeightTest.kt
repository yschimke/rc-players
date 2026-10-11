package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A layout document's root has no height of its own, so it wraps its content within the host's
 * height — the View player's `wrap_content` — instead of taking every pixel a bounded parent
 * offers. Hosts used to need a layout modifier measuring the player with an unbounded height to get
 * this.
 */
class RcRootWrapHeightTest {

  @Test
  fun aPlayerInABoundedColumnWrapsItsContentHeight() {
    var size = IntSize.Zero
    val scene =
      ImageComposeScene(width = 80, height = 100, density = Density(1f)) {
        Column { RcComposePlayer(document(), modifier = Modifier.onSizeChanged { size = it }) }
      }
    try {
      scene.render(0L)
      // Width still fills, as before; height is the 20 px child, not the column's 100.
      assertEquals(IntSize(80, 20), size)
    } finally {
      scene.close()
    }
  }

  @Test
  fun aHostThatPinsTheHeightStillGetsItFilled() {
    var size = IntSize.Zero
    val scene =
      ImageComposeScene(width = 80, height = 100, density = Density(1f)) {
        RcComposePlayer(document(), modifier = Modifier.fillMaxSize().onSizeChanged { size = it })
      }
    try {
      scene.render(0L)
      assertEquals(IntSize(80, 100), size)
    } finally {
      scene.close()
    }
  }

  private fun document(): RcDocument =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 80, legacyHeight = 100, modern = false),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcBoxLayout(3, -1, 1, 4),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        RcLayoutContent(4),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
      ),
    )
}

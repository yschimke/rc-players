package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The remote-m3 toggle controls, captured as published, clicked through the semantics tree.
 *
 * A switch, checkbox or radio keeps its selected state in a mutable remote value that the document
 * declares every frame, so each of these is the stepper's bug (compose-preview-server#1221) at
 * smaller scale: a click that does not outlive the next frame's declaration leaves the control
 * looking dead. A radio is the one that must *not* move when it is already selected, and a disabled
 * control must not be clickable at all.
 */
@OptIn(ExperimentalTestApi::class)
class RcToggleControlsClickTest {
  private fun withControl(name: String, block: ComposeUiTest.() -> Unit) {
    val bytes =
      checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$name.rc")).use { it.readBytes() }
    val document = RcDocumentCodec.decode(bytes)
    runSkikoComposeUiTest(size = Size(454f, 200f), density = Density(1f)) {
      // Manual clock: a control that keeps requesting frames never goes idle, and `waitForIdle`
      // would wait for it forever.
      mainClock.autoAdvance = false
      setContent { RcComposePlayer(document) }
      mainClock.advanceTimeBy(300)
      block()
    }
  }

  private fun ComposeUiTest.frameHash(): Int =
    onRoot().captureToImage().toPixelMap().let { pixels ->
      var hash = 0
      for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
        hash = hash * 31 + pixels[x, y].hashCode()
      }
      hash
    }

  private fun ComposeUiTest.click(index: Int) {
    onAllNodes(hasClickAction())[index].performClick()
    mainClock.advanceTimeBy(700)
  }

  @Test
  fun anUnselectedSwitchTurnsOnAndOffAgain() =
    withControl("SwitchButton-ideal-unselected-compact") {
      val atRest = frameHash()
      click(0)
      val on = frameHash()
      assertNotEquals(atRest, on, "the first click turned the switch on")
      click(0)
      assertEquals(atRest, frameHash(), "the second click turned it back off")
    }

  @Test
  fun anUnselectedCheckboxChecks() =
    withControl("CheckboxButton-ideal-unselected-compact") {
      val atRest = frameHash()
      click(0)
      assertNotEquals(atRest, frameHash(), "the click checked the box")
    }

  @Test
  fun anUnselectedRadioSelects() =
    withControl("RadioButton-ideal-unselected-compact") {
      val atRest = frameHash()
      click(0)
      assertNotEquals(atRest, frameHash(), "the click selected the radio")
    }

  @Test
  fun aSelectedRadioStaysSelected() =
    withControl("RadioButton-ideal-default-compact") {
      val atRest = frameHash()
      click(0)
      assertEquals(atRest, frameHash(), "clicking a selected radio does not deselect it")
    }

  @Test
  fun aDisabledSwitchIsNotClickable() =
    withControl("SwitchButton-ideal-unselected-disabled-compact") {
      assertEquals(0, onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }
}

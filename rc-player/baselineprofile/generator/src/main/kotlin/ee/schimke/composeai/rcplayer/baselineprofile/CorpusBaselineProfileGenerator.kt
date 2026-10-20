package ee.schimke.composeai.rcplayer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Starts the host and flings its corpus list end to end. Every document decodes, lays out and draws
 * as it scrolls into view, so the profile covers the players' cold path for each component kind.
 *
 * Each library filters the shared result to its own package (`baselineProfile { filter { … } }` in
 * its build file), so one collection feeds all four AARs.
 */
@RunWith(AndroidJUnit4::class)
class CorpusBaselineProfileGenerator {

  @get:Rule val rule = BaselineProfileRule()

  @Test
  fun generate() =
    rule.collect(
      packageName = "ee.schimke.composeai.rcplayer.baselineprofile.host",
      includeInStartupProfile = true,
    ) {
      pressHome()
      startActivityAndWait()
      val list =
        device.wait(Until.findObject(By.res("corpus")), 15_000) ?: error("corpus list not shown")
      list.setGestureMargin(device.displayWidth / 5)
      // `fling` reports whether the list can still move; cap it so a broken list cannot hang.
      var flings = 0
      while (list.fling(Direction.DOWN) && flings++ < 200) device.waitForIdle()
      list.fling(Direction.UP)
    }
}

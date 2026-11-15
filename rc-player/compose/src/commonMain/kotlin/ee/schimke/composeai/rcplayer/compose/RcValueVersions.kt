package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState

/**
 * Per-value invalidation: an observable version for every value id something in the player read.
 *
 * The state reports each id it is asked for ([RcPlayerState.valueReadObserver]); [observe] reads
 * that id's version there, so Compose records the dependency in whichever phase read it — a
 * component's composition, its measure pass, its draw. When a host value changes, the player's top
 * level recomposes alone, evaluates the document's derived values again, and [publish]es: each id
 * whose value moved ([RcPlayerState.valueSignature]) bumps its version, and only what read it
 * recomposes, measures or draws again. Before this, one counter every layout node read recomposed
 * the whole card for any change.
 *
 * Reads the top level makes while it evaluates the document are [suppressed]: they are the
 * evaluation, not a use, and would only invalidate the top level it is already running.
 */
internal class RcValueVersions {
  /**
   * The player's state, once it is built: it reads values through [observe], and [publish] compares
   * them.
   */
  var state: RcPlayerState? = null

  private val versions = HashMap<Int, MutableIntState>()
  private val published = HashMap<Int, Any>()

  /** While true, reads are not recorded: the player's top level is evaluating the document. */
  var suppressed: Boolean = false

  fun observe(id: Int) {
    val state = state
    if (suppressed || id < 0 || state == null) return
    val version =
      versions.getOrPut(id) {
        published[id] = state.valueSignature(id)
        mutableIntStateOf(0)
      }
    version.intValue
  }

  internal companion object {
    /**
     * Off, every document invalidates the whole player as before: for tests that compare the two.
     */
    var enabled: Boolean = true
  }

  /** Bumps the version of every observed id whose value is not the one last published for it. */
  fun publish() {
    val state = state ?: return
    for ((id, version) in versions) {
      val now = state.valueSignature(id)
      if (published[id] != now) {
        published[id] = now
        version.intValue += 1
      }
    }
  }
}

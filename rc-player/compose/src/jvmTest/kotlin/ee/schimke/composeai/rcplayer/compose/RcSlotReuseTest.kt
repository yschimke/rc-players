package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.observe
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import java.io.File
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A card that arrives in a lazy list composes once, in the list's measure pass, whether its slot is
 * new or reused from a card that scrolled away, and nothing of it composes again in the
 * recomposer's next frame.
 *
 * A reused slot keeps the player's providers and hands them the new card's document-scoped values
 * (its state, its offscreen pool, its invalidate callback). Provided through a dynamic
 * `compositionLocalOf`, each of those is a state written during that composition, and every scope
 * that had read it is invalidated again once the composition applies: the whole card composed a
 * second time, in the next frame, outside the list's measure and prefetch.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
class RcSlotReuseTest {
  @Test fun aReusedSlotComposesItsCardOnce() = assertComposedOnce(flatten = false)

  @Test fun aReusedFlatSlotComposesItsCardOnce() = assertComposedOnce(flatten = true)

  private fun assertComposedOnce(flatten: Boolean) {
    for (name in CARDS) {
      val prepared = RcPreparedDocument.decode(File(CORPUS, name).readBytes())
      val list = LazyListState()
      val counter = RecomposerPasses()
      val scene =
        ImageComposeScene(WIDTH, HEIGHT, Density(2f)) {
          CompositionLocalProvider(
            LocalRcDeferLayoutAnimations provides true,
            LocalRcFlattenLayout provides flatten,
          ) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
              // One content type, so a card that scrolls away gives its slot to the next one.
              items(ITEMS, key = { it }) {
                RcComposePlayer(
                  prepared,
                  Modifier.fillMaxWidth(),
                  namedValues = rememberRcNamedValues(),
                )
              }
            }
          }
        }
      try {
        var time = 0L
        repeat(3) { scene.render(time).close().also { time += FRAME_NANOS } }
        counter.attach()
        val first = list.firstVisibleItemIndex
        repeat(FRAMES) {
          list.dispatchRawDelta(STEP)
          scene.render(time).close()
          time += FRAME_NANOS
        }
        assertTrue(list.firstVisibleItemIndex - first >= 4, "$name: too few cards scrolled on")
        assertTrue(counter.reused > 0, "$name: no card composed in the list's measure pass")
        assertEquals(
          0,
          counter.recomposed,
          "$name (flat $flatten): player scopes recomposed in the recomposer\n" +
            counter.details.joinToString("\n"),
        )
      } finally {
        counter.close()
        scene.close()
      }
    }
  }

  /** Counts player scopes that compose again outside the lazy list's measure pass. */
  private class RecomposerPasses : CompositionObserver {
    private val seen = IdentityHashMap<RecomposeScope, Boolean>()
    private val handles =
      mutableListOf<androidx.compose.runtime.tooling.CompositionObserverHandle>()
    var recomposed = 0
    /** Player scopes composed in the lazy list's measure pass: the cards that arrived. */
    var reused = 0

    fun attach() {
      Recomposer.runningRecomposers.value.forEach { info ->
        val field = info.javaClass.declaredFields.first { it.type == Recomposer::class.java }
        field.isAccessible = true
        val recomposer = field.get(info) as Recomposer
        handles +=
          recomposer.observe(
            object : CompositionRegistrationObserver {
              override fun onCompositionRegistered(composition: ObservableComposition) {
                handles += composition.setObserver(this@RecomposerPasses)
              }

              override fun onCompositionUnregistered(composition: ObservableComposition) {}
            },
          )
      }
    }

    fun close() = handles.forEach { it.dispose() }

    override fun onScopeEnter(scope: RecomposeScope) {
      val first = seen.put(scope, true) == null
      val stack = Throwable().stackTrace
      if (stack.none { it.className.startsWith(PLAYER) && !it.className.startsWith(TEST) }) return
      if (stack.any { it.methodName == "subcompose" }) reused++
      else if (!first) {
        recomposed++
        details +=
          "scope " +
            stack
              .filter { it.className.startsWith(PLAYER) && !it.className.startsWith(TEST) }
              .take(3)
              .joinToString(" < ") {
                it.className.substringAfterLast('.') + "." + it.methodName + ":" + it.lineNumber
              } +
            " <= " +
            why[scope]
      }
    }

    override fun onBeginComposition(composition: ObservableComposition) {}

    override fun onEndComposition(composition: ObservableComposition) {}

    override fun onReadInScope(scope: RecomposeScope, value: Any) {}

    override fun onScopeExit(scope: RecomposeScope) {}

    override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) {
      val v =
        if (value is androidx.compose.runtime.State<*>) "State(" + value.value + ")"
        else value?.javaClass?.name
      why[scope] =
        (why[scope] ?: "") +
          v +
          " by " +
          Throwable()
            .stackTrace
            .filter { it.className.startsWith(PLAYER) && !it.className.contains("RcSlotReuseTest") }
            .take(3)
            .joinToString(" < ") {
              it.className.substringAfterLast('.') + "." + it.methodName + ":" + it.lineNumber
            } +
          "; "
    }

    val why = IdentityHashMap<RecomposeScope, String>()
    val details = mutableListOf<String>()

    override fun onScopeDisposed(scope: RecomposeScope) {
      seen.remove(scope)
    }
  }

  private companion object {
    val CORPUS = File("../../scripts/rc-catalog-corpus/corpus")
    // Each composed a second time in the recomposer before its locals were static (14 of the
    // corpus's documents did; none does now).
    val CARDS =
      listOf(
        "listheader__ideal__default__compact.rc",
        "text-body__ideal__default__compact.rc",
        "text-branded__ideal__default__compact.rc",
        "typeface-specimen__ideal__default__compact.rc",
      )
    const val PLAYER = "ee.schimke.composeai.rcplayer."
    val TEST = RcSlotReuseTest::class.java.name
    const val WIDTH = 400
    const val HEIGHT = 800
    const val ITEMS = 60
    const val FRAMES = 120
    const val STEP = 24f
    const val FRAME_NANOS = 16_666_667L
  }
}

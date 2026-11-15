package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi

/**
 * Counts, through Compose's composition tracer, the times a composable named [name] ran its body
 * (was not skipped) while [block] runs: what a frame recomposed.
 */
@OptIn(InternalComposeTracingApi::class)
internal fun <T> countingCompositions(name: String, block: (count: () -> Int) -> T): T {
  var count = 0
  Composer.setTracer(
    object : CompositionTracer {
      override fun isTraceInProgress() = true

      override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
        if (info.substringBefore(" (") == name) count++
      }

      override fun traceEventEnd() {}
    },
  )
  try {
    return block { count }
  } finally {
    Composer.setTracer(
      object : CompositionTracer {
        override fun isTraceInProgress() = false

        override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {}

        override fun traceEventEnd() {}
      },
    )
  }
}

internal const val RENDER_LAYOUT_NODE = "ee.schimke.composeai.rcplayer.compose.RenderLayoutNode"

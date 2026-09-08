package ee.schimke.composeai.rcplayer.trace

/** Apple targets have no platform tracer wired up. See the iOS actual for the full rationale. */
internal actual object RcTracePlatform {
  actual fun isEnabled(category: String): Boolean = false

  actual fun begin(category: String, name: String): Any? = null

  actual fun end(token: Any): Unit = Unit

  actual fun instant(category: String, name: String): Unit = Unit

  actual fun counter(category: String, name: String, value: Long): Unit = Unit
}

/** No-op: there is no platform tracer on Apple targets to switch. */
public actual fun setRcPlatformTracingEnabled(enabled: Boolean): Unit = Unit

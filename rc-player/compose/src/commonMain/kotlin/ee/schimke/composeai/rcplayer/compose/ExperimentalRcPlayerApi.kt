package ee.schimke.composeai.rcplayer.compose

/**
 * Player API that is published but still settling, and may change between minor releases. Opt in
 * with `@OptIn(ExperimentalRcPlayerApi::class)`.
 */
@RequiresOptIn(
  message =
    "This player API is experimental and may change between minor releases. " +
      "Opt in with @OptIn(ExperimentalRcPlayerApi::class)."
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class ExperimentalRcPlayerApi

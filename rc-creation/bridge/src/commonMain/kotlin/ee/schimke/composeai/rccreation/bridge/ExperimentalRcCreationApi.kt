package ee.schimke.composeai.rccreation.bridge

/**
 * rc-players' creation API: the host bridge, live embedding and explicit component ids.
 *
 * Published so hosts can build on it, and still settling: its shape may change between minor
 * releases. Opt in with `@OptIn(ExperimentalRcCreationApi::class)`. The creation fork itself
 * (`androidx.compose.remote.creation.*`) follows AndroidX's own stability, not this marker.
 */
@RequiresOptIn(
  message =
    "rc-players' creation API is experimental and may change between minor releases. " +
      "Opt in with @OptIn(ExperimentalRcCreationApi::class)."
)
@Retention(AnnotationRetention.BINARY)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalRcCreationApi

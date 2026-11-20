package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import ee.schimke.composeai.rcplayer.protocol.RcBinaryData
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocLayout
import ee.schimke.composeai.rcplayer.protocol.RcMapRcDocVariables
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.runtime.RcLayoutNode
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState

/**
 * Host-provided resolver for embedded Remote Compose document URLs (`LAYOUT_RC_DOC`).
 *
 * Resolving a URL into serialized `.rc` bytes is the host's responsibility (for example, reading
 * bundled assets, local caches, or pre-fetched payloads). Returning `null` indicates that the URL
 * cannot be resolved or is not permitted by the host.
 */
public fun interface RcDocumentResolver {
  public fun resolve(url: String): ByteArray?
}

/**
 * Opt-in configuration for embedded Remote Compose documents (`LAYOUT_RC_DOC`, `BINARY_DATA`,
 * `MAP_RC_DOC_VARIABLES`).
 *
 * Embedded documents are disabled by default ([Disabled], `maxDepth == 0`). A host opts in by
 * providing an instance through [LocalRcEmbeddedDocuments]:
 * ```kotlin
 * CompositionLocalProvider(
 *   LocalRcEmbeddedDocuments provides RcEmbeddedDocuments(resolver = myResolver),
 * ) {
 *   RcComposePlayer(document)
 * }
 * ```
 *
 * [maxDepth] caps how many levels of nested documents may play, defaulting to [DEFAULT_MAX_DEPTH]
 * (`1`: a top-level document may embed child documents, which cannot embed further documents).
 *
 * When [cache] is provided, resolved URL documents are prepared once and reused across every card
 * or placement that renders them. Inline [RcBinaryData] (`TYPE_RC_DOC`) blobs are also prepared
 * once per parent [RcPreparedDocument] and shared across every card playing that parent.
 */
public class RcEmbeddedDocuments(
  public val maxDepth: Int = DEFAULT_MAX_DEPTH,
  public val resolver: RcDocumentResolver? = null,
  public val cache: RcPreparedDocumentCache? = null,
) {
  init {
    require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
  }

  /** Whether embedded documents are enabled (`maxDepth > 0`). */
  public val isEnabled: Boolean
    get() = maxDepth > 0

  @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class, ExperimentalRcPlayerApi::class)
  private val urlFastCache =
    kotlin.concurrent.atomics.AtomicReference<
      Map<RcEmbeddedUrlKey, Pair<ByteArray, RcPreparedDocument>>,
    >(
      emptyMap(),
    )

  /**
   * Resolves [url] via [resolver] and returns its [RcPreparedDocument], skipping byte hashing when
   * [cache] is configured and [resolver] returns the same [ByteArray] instance as before.
   */
  @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class, ExperimentalRcPlayerApi::class)
  internal fun resolvePrepared(
    url: String,
    extensions: RcPlayerExtensions,
    display: RcDisplay,
  ): RcPreparedDocument? {
    val activeResolver = resolver ?: return null
    val bytes = runCatching { activeResolver.resolve(url) }.getOrNull()
    if (bytes == null || bytes.isEmpty()) return null
    val activeCache = cache
    if (activeCache == null) {
      return runCatching { RcPreparedDocument(decodeCmpDocument(bytes, extensions)) }.getOrNull()
    }
    val key =
      RcEmbeddedUrlKey(
        url = url,
        extensions = if (extensions.extensions.isEmpty()) RcPlayerExtensions.None else extensions,
        display = if (activeCache.preprocessor != null) display else null,
      )
    val cached = urlFastCache.load()[key]
    if (cached != null && cached.first === bytes) return cached.second
    val prepared =
      runCatching { activeCache.prepareNow(bytes, extensions, display) }.getOrNull() ?: return null
    while (true) {
      val snapshot = urlFastCache.load()
      val next = snapshot + (key to (bytes to prepared))
      if (urlFastCache.compareAndSet(snapshot, next)) break
    }
    return prepared
  }

  override fun equals(other: Any?): Boolean =
    other is RcEmbeddedDocuments &&
      maxDepth == other.maxDepth &&
      resolver == other.resolver &&
      cache == other.cache

  override fun hashCode(): Int {
    var result = maxDepth
    result = 31 * result + (resolver?.hashCode() ?: 0)
    result = 31 * result + (cache?.hashCode() ?: 0)
    return result
  }

  public companion object {
    /** Default nesting depth when embedded documents are enabled: one level of embedded docs. */
    public const val DEFAULT_MAX_DEPTH: Int = 1

    /** Embedded documents disabled (`maxDepth = 0`). The default for [LocalRcEmbeddedDocuments]. */
    public val Disabled: RcEmbeddedDocuments = RcEmbeddedDocuments(maxDepth = 0)
  }
}

@OptIn(ExperimentalRcPlayerApi::class)
private data class RcEmbeddedUrlKey(
  val url: String,
  val extensions: RcPlayerExtensions,
  val display: RcDisplay?,
)

/**
 * Controls whether [RcComposePlayer] loads and renders embedded Remote Compose documents
 * (`LAYOUT_RC_DOC`). Disabled by default ([RcEmbeddedDocuments.Disabled]).
 */
public val LocalRcEmbeddedDocuments: ProvidableCompositionLocal<RcEmbeddedDocuments> =
  staticCompositionLocalOf {
    RcEmbeddedDocuments.Disabled
  }

/** Current embedded-document nesting depth (`0` for the top-level player). */
internal val LocalRcEmbeddedDepth: ProvidableCompositionLocal<Int> = staticCompositionLocalOf { 0 }

/** Host-level services forwarded from a parent player to its embedded child players. */
internal class RcEmbeddedHostContext(
  val prepared: RcPreparedDocument,
  val systemColors: (name: String) -> Color?,
  val onEvent: (RcPlayerEvent) -> Unit,
)

internal val LocalRcEmbeddedHost: ProvidableCompositionLocal<RcEmbeddedHostContext?> =
  staticCompositionLocalOf {
    null
  }

/**
 * Resolves an inline [RcBinaryData] (`TYPE_RC_DOC`) referenced either directly by [urlId] or via a
 * `rc:embedded/<id>` URL string.
 */
internal fun findInlineEmbeddedDoc(
  state: RcPlayerState,
  urlId: Int,
  url: String?,
): RcBinaryData? {
  val direct = state.binaryData(urlId)
  if (direct != null && direct.type == RcBinaryData.TYPE_RC_DOC) return direct
  if (url != null && url.startsWith(RcDocLayout.EMBEDDED_URL_PREFIX)) {
    val parsedId = url.substring(RcDocLayout.EMBEDDED_URL_PREFIX.length).trim().toIntOrNull()
    if (parsedId != null) {
      val byPrefix = state.binaryData(parsedId)
      if (byPrefix != null && byPrefix.type == RcBinaryData.TYPE_RC_DOC) return byPrefix
    }
  }
  return null
}

/**
 * Tracks bidirectional variable synchronization state for one [RcLayoutNode.EmbeddedDoc] instance,
 * matching AndroidX `MapRcDocVariables` semantics.
 */
internal class RcEmbeddedVariableSync(private val mappings: List<RcMapRcDocVariables>) {
  private val initialSynced = BooleanArray(mappings.size)
  private val lastFloat = FloatArray(mappings.size) { Float.NaN }
  private val lastInt = IntArray(mappings.size) { Int.MIN_VALUE }
  private val lastColor = IntArray(mappings.size)
  private val lastText = arrayOfNulls<String>(mappings.size)

  /**
   * Pushes `MAP_FORWARD` and changed `MAP_BIDIRECTIONAL` host variables into [childNamedValues].
   * Reads host state during composition so Compose invalidation tracks host variable updates.
   */
  fun syncForward(
    hostState: RcPlayerState,
    childPrepared: RcPreparedDocument,
    childNamedValues: SnapshotStateMap<String, RcNamedValue>,
  ) {
    for (index in mappings.indices) {
      val mapping = mappings[index]
      val mode = mapping.mode
      if (
        mode != RcMapRcDocVariables.MAP_FORWARD && mode != RcMapRcDocVariables.MAP_BIDIRECTIONAL
      ) {
        continue
      }
      val remoteName = hostState.text(mapping.remoteTarget) ?: continue
      val type = childPrepared.namedVariableTypes[remoteName] ?: continue
      val localId = mapping.localId
      when (type) {
        RcNamedVariable.FLOAT_TYPE -> {
          val hostVal = hostState.float(localId) ?: continue
          if (
            mode == RcMapRcDocVariables.MAP_FORWARD ||
              !initialSynced[index] ||
              hostVal.toRawBits() != lastFloat[index].toRawBits()
          ) {
            val next = RcNamedValue.FloatValue(hostVal)
            if (childNamedValues[remoteName] != next) childNamedValues[remoteName] = next
            lastFloat[index] = hostVal
            initialSynced[index] = true
          }
        }
        RcNamedVariable.INT_TYPE -> {
          val hostVal = hostState.integer(localId) ?: continue
          if (
            mode == RcMapRcDocVariables.MAP_FORWARD ||
              !initialSynced[index] ||
              hostVal != lastInt[index]
          ) {
            val next = RcNamedValue.Integer(hostVal)
            if (childNamedValues[remoteName] != next) childNamedValues[remoteName] = next
            lastInt[index] = hostVal
            initialSynced[index] = true
          }
        }
        RcNamedVariable.COLOR_TYPE -> {
          val hostVal = hostState.color(localId)
          if (
            mode == RcMapRcDocVariables.MAP_FORWARD ||
              !initialSynced[index] ||
              hostVal != lastColor[index]
          ) {
            val next = RcNamedValue.Color(hostVal)
            if (childNamedValues[remoteName] != next) childNamedValues[remoteName] = next
            lastColor[index] = hostVal
            initialSynced[index] = true
          }
        }
        RcNamedVariable.STRING_TYPE -> {
          val hostVal = hostState.text(localId) ?: continue
          if (
            mode == RcMapRcDocVariables.MAP_FORWARD ||
              !initialSynced[index] ||
              hostVal != lastText[index]
          ) {
            val next = RcNamedValue.Text(hostVal)
            if (childNamedValues[remoteName] != next) childNamedValues[remoteName] = next
            lastText[index] = hostVal
            initialSynced[index] = true
          }
        }
      }
    }
  }

  /** Pushes `MAP_REVERSE` and changed `MAP_BIDIRECTIONAL` child variables back into [hostState]. */
  fun syncReverse(
    hostState: RcPlayerState,
    childState: RcPlayerState,
    childNamedValues: SnapshotStateMap<String, RcNamedValue>,
    invalidateHost: () -> Unit,
  ) {
    var anyHostChanged = false
    for (index in mappings.indices) {
      val mapping = mappings[index]
      val mode = mapping.mode
      if (
        mode != RcMapRcDocVariables.MAP_REVERSE && mode != RcMapRcDocVariables.MAP_BIDIRECTIONAL
      ) {
        continue
      }
      val remoteName = hostState.text(mapping.remoteTarget) ?: continue
      val variable = childState.namedVariable(remoteName) ?: continue
      val localId = mapping.localId
      when (variable.type) {
        RcNamedVariable.FLOAT_TYPE -> {
          val childVal = childState.float(variable.id) ?: continue
          if (
            mode == RcMapRcDocVariables.MAP_REVERSE ||
              !initialSynced[index] ||
              childVal.toRawBits() != lastFloat[index].toRawBits()
          ) {
            if (hostState.float(localId)?.toRawBits() != childVal.toRawBits()) {
              hostState.setFloat(localId, childVal)
              anyHostChanged = true
            }
            lastFloat[index] = childVal
            initialSynced[index] = true
            if (mode == RcMapRcDocVariables.MAP_BIDIRECTIONAL) {
              val next = RcNamedValue.FloatValue(childVal)
              if (childNamedValues[remoteName] != next) childNamedValues[remoteName] = next
            }
          }
        }
        RcNamedVariable.INT_TYPE -> {
          val childVal = childState.integer(variable.id) ?: continue
          if (
            mode == RcMapRcDocVariables.MAP_REVERSE ||
              !initialSynced[index] ||
              childVal != lastInt[index]
          ) {
            if (hostState.integer(localId) != childVal) {
              hostState.setInteger(localId, childVal)
              anyHostChanged = true
            }
            lastInt[index] = childVal
            initialSynced[index] = true
            if (mode == RcMapRcDocVariables.MAP_BIDIRECTIONAL) {
              val next = RcNamedValue.Integer(childVal)
              if (childNamedValues[remoteName] != next) childNamedValues[remoteName] = next
            }
          }
        }
        RcNamedVariable.COLOR_TYPE -> {
          val childVal = childState.color(variable.id)
          if (
            mode == RcMapRcDocVariables.MAP_REVERSE ||
              !initialSynced[index] ||
              childVal != lastColor[index]
          ) {
            if (hostState.color(localId) != childVal) {
              hostState.setColor(localId, childVal)
              anyHostChanged = true
            }
            lastColor[index] = childVal
            initialSynced[index] = true
            if (mode == RcMapRcDocVariables.MAP_BIDIRECTIONAL) {
              val next = RcNamedValue.Color(childVal)
              if (childNamedValues[remoteName] != next) childNamedValues[remoteName] = next
            }
          }
        }
        RcNamedVariable.STRING_TYPE -> {
          val childVal = childState.text(variable.id) ?: continue
          if (
            mode == RcMapRcDocVariables.MAP_REVERSE ||
              !initialSynced[index] ||
              childVal != lastText[index]
          ) {
            if (hostState.text(localId) != childVal) {
              hostState.setText(localId, childVal)
              anyHostChanged = true
            }
            lastText[index] = childVal
            initialSynced[index] = true
            if (mode == RcMapRcDocVariables.MAP_BIDIRECTIONAL) {
              val next = RcNamedValue.Text(childVal)
              if (childNamedValues[remoteName] != next) childNamedValues[remoteName] = next
            }
          }
        }
      }
    }
    if (anyHostChanged) invalidateHost()
  }

  /**
   * Intercepts `MAP_HOST_ACTION` events from the child document, writing metadata into the mapped
   * host string variable. Returns `true` if intercepted.
   */
  fun handleHostAction(
    hostState: RcPlayerState,
    event: RcPlayerEvent,
    invalidateHost: () -> Unit,
  ): Boolean {
    val actionId: Int
    val metadata: String
    when (event) {
      is RcPlayerEvent.HostAction -> {
        actionId = event.actionId
        metadata = ""
      }
      is RcPlayerEvent.HostActionMetadata -> {
        actionId = event.actionId
        metadata = event.metadata
      }
      else -> return false
    }
    var mapped = false
    for (mapping in mappings) {
      if (mapping.mode == RcMapRcDocVariables.MAP_HOST_ACTION && mapping.remoteTarget == actionId) {
        hostState.setText(mapping.localId, metadata)
        mapped = true
      }
    }
    if (mapped) invalidateHost()
    return mapped
  }
}

private val EmptyEmbeddedNamedValues: SnapshotStateMap<String, RcNamedValue> = mutableStateMapOf()
private val NoOpEmbeddedEventSink: (RcPlayerEvent) -> Unit = {}
private val NoOpEmbeddedSystemColors: (String) -> Color? = { null }

@OptIn(ExperimentalRcPlayerApi::class)
@Composable
internal fun RenderEmbeddedDoc(
  node: RcLayoutNode.EmbeddedDoc,
  docModifier: Modifier,
  state: RcPlayerState,
  theme: Int,
  typefaces: RcTypefaceLoader,
  customComponents: RcCustomComponentRegistry,
  invalidate: () -> Unit,
) {
  val config = LocalRcEmbeddedDocuments.current
  val depth = LocalRcEmbeddedDepth.current
  if (!config.isEnabled || depth >= config.maxDepth) {
    Spacer(docModifier)
    return
  }

  val host = LocalRcEmbeddedHost.current
  val extensions = LocalRcPlayerExtensions.current
  val density = LocalDensity.current
  val url = state.text(node.operation.urlId)
  val inlineBlob = findInlineEmbeddedDoc(state, node.operation.urlId, url)

  val childPrepared: RcPreparedDocument? =
    if (inlineBlob != null) {
      host?.prepared?.inlineEmbeddedDocument(inlineBlob.id, inlineBlob.data, extensions)
        ?: remember(inlineBlob.id, inlineBlob.data, extensions) {
          runCatching { RcPreparedDocument(decodeCmpDocument(inlineBlob.data, extensions)) }
            .getOrNull()
        }
    } else if (url != null && config.resolver != null) {
      val display = RcDisplay(density.density, density.fontScale)
      remember(url, config, extensions, display) {
        config.resolvePrepared(url, extensions, display)
      }
    } else {
      null
    }

  if (childPrepared == null) {
    Spacer(docModifier)
    return
  }

  var sizedModifier = docModifier
  val wrapsWidth = node.modifiers.width?.type == RcDimensionType.WRAP
  val wrapsHeight = node.modifiers.height?.type == RcDimensionType.WRAP
  val intrinsicW = childPrepared.document.header.width
  val intrinsicH = childPrepared.document.header.height
  if (intrinsicW > 0 && (node.modifiers.width == null || wrapsWidth)) {
    sizedModifier = sizedModifier.width(with(density) { intrinsicW.toDp() })
  }
  if (intrinsicH > 0 && (node.modifiers.height == null || wrapsHeight)) {
    sizedModifier = sizedModifier.height(with(density) { intrinsicH.toDp() })
  }

  val mappings = state.docVariableMappings(node.componentId)
  val childNamedValues: SnapshotStateMap<String, RcNamedValue>
  val childOnEvent: (RcPlayerEvent) -> Unit
  val onChildStateChanged: ((RcPlayerState) -> Unit)?
  if (mappings.isEmpty()) {
    childNamedValues = EmptyEmbeddedNamedValues
    childOnEvent = host?.onEvent ?: NoOpEmbeddedEventSink
    onChildStateChanged = null
  } else {
    val hasValueMappings = mappings.any { it.mode != RcMapRcDocVariables.MAP_HOST_ACTION }
    val hasReverseMappings = mappings.any {
      it.mode == RcMapRcDocVariables.MAP_REVERSE || it.mode == RcMapRcDocVariables.MAP_BIDIRECTIONAL
    }
    val hasHostActionMappings = mappings.any { it.mode == RcMapRcDocVariables.MAP_HOST_ACTION }
    val sync =
      remember(childPrepared, node.componentId, mappings) { RcEmbeddedVariableSync(mappings) }
    childNamedValues =
      if (hasValueMappings) {
        remember(childPrepared, node.componentId) { mutableStateMapOf<String, RcNamedValue>() }
          .also { sync.syncForward(state, childPrepared, it) }
      } else {
        EmptyEmbeddedNamedValues
      }
    childOnEvent =
      if (hasHostActionMappings) {
        remember(state, sync, host, invalidate) {
          { event ->
            if (!sync.handleHostAction(state, event, invalidate)) {
              host?.onEvent?.invoke(event)
            }
          }
        }
      } else {
        host?.onEvent ?: NoOpEmbeddedEventSink
      }
    onChildStateChanged =
      if (hasReverseMappings) {
        remember(state, sync, childNamedValues, invalidate) {
          { childState -> sync.syncReverse(state, childState, childNamedValues, invalidate) }
        }
      } else {
        null
      }
  }

  CompositionLocalProvider(LocalRcEmbeddedDepth provides depth + 1) {
    RcComposePlayerResolved(
      prepared = childPrepared,
      modifier = sizedModifier.clipToBounds(),
      theme = theme,
      namedValues = childNamedValues,
      onEvent = childOnEvent,
      typefaces = typefaces,
      systemColors = host?.systemColors ?: NoOpEmbeddedSystemColors,
      customComponents = customComponents,
      onStateChanged = onChildStateChanged,
      observeNamedValues = false,
    )
  }
}

@file:OptIn(ExperimentalAtomicApi::class)

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Where a player turns encoded bytes into an [RcPreparedDocument]: decoding, linking, the layout
 * tree, the state seed and every other per-document index a card would otherwise build for itself.
 *
 * Provided through [LocalRcPreparation] and read by the `RcComposePlayer` overloads that take
 * bytes, and by [rememberRcPreparedDocument]. A player handed an [RcPreparedDocument] directly has
 * nothing left to prepare, whichever is provided.
 */
public sealed interface RcPreparation {
  /**
   * Prepared during the composition that first shows the bytes, on the composition's thread, so the
   * player draws on that composition's frame. The default, and what the player always did.
   */
  public data object InComposition : RcPreparation

  /**
   * Prepared on [context], never in composition. Composition only looks the bytes up in [cache]: a
   * document already there (prepared by an earlier card, or by [RcPreparedDocumentCache.prepare]
   * ahead of time) plays on this frame, and one that is not starts preparing in the background and
   * draws nothing until it is ready. A list that scrolls therefore never decodes a document on its
   * main thread, and a host that prepares its documents before they scroll into view never shows an
   * empty frame either.
   */
  public class Background(
    public val cache: RcPreparedDocumentCache = RcPreparedDocumentCache(),
    public val context: CoroutineContext = Dispatchers.Default,
  ) : RcPreparation
}

/** How the players below prepare documents from bytes; [RcPreparation.InComposition] by default. */
public val LocalRcPreparation: androidx.compose.runtime.ProvidableCompositionLocal<RcPreparation> =
  staticCompositionLocalOf {
    RcPreparation.InComposition
  }

/**
 * Prepared documents by content, shared by every player and host that uses it. Reads ([peek]) never
 * prepare and never block, so composition can make them; [prepare] does the work on a background
 * context and keeps the result. Bounded: past [maxDocuments] the oldest is dropped, which only
 * costs the next card the work again.
 */
public class RcPreparedDocumentCache(private val maxDocuments: Int = 32) {
  private val documents = AtomicReference<Map<RcContentKey, RcPreparedDocument>>(emptyMap())
  private val preparedCount = AtomicInt(0)
  private val missCount = AtomicInt(0)
  private val pending = Mutex()
  private val pendingByKey = mutableMapOf<RcContentKey, CompletableDeferred<RcPreparedDocument>>()

  /** [bytes]' prepared document if it is in memory, else null. Never prepares. */
  public fun peek(bytes: ByteArray): RcPreparedDocument? = peek(bytes, RcPlayerExtensions.None)

  /** As [peek], for bytes decoded with [extensions]' operations. */
  @ExperimentalRcPlayerApi
  public fun peek(bytes: ByteArray, extensions: RcPlayerExtensions): RcPreparedDocument? =
    documents.load()[RcContentKey(bytes, extensions)]

  /**
   * [bytes]' prepared document: from memory, or decoded and prepared on [context] and kept.
   * Concurrent calls for the same bytes (a screen of cards of one template arriving together) share
   * one preparation.
   */
  public suspend fun prepare(
    bytes: ByteArray,
    context: CoroutineContext = Dispatchers.Default,
  ): RcPreparedDocument = prepare(bytes, RcPlayerExtensions.None, context)

  /** As [prepare], for bytes decoded with [extensions]' operations. */
  @ExperimentalRcPlayerApi
  public suspend fun prepare(
    bytes: ByteArray,
    extensions: RcPlayerExtensions,
    context: CoroutineContext = Dispatchers.Default,
  ): RcPreparedDocument {
    val key = RcContentKey(bytes, extensions)
    documents.load()[key]?.let {
      return it
    }
    val mine = CompletableDeferred<RcPreparedDocument>()
    val shared = pending.withLock { pendingByKey.getOrPut(key) { mine } }
    if (shared !== mine) return shared.await()
    try {
      val prepared = withContext(context) { prepareNow(bytes, extensions) }
      mine.complete(prepared)
      return prepared
    } catch (e: Throwable) {
      // A cancelled or failed preparation fails the callers waiting on it; the next call retries.
      mine.completeExceptionally(e)
      throw e
    } finally {
      pending.withLock { pendingByKey.remove(key) }
    }
  }

  /**
   * [bytes]' prepared document: from memory, or prepared on the calling thread and kept. For a host
   * that is already off the main thread, or that has chosen [RcPreparation.InComposition]; a player
   * under [RcPreparation.Background] never calls it.
   */
  public fun prepareNow(bytes: ByteArray): RcPreparedDocument =
    prepareNow(bytes, RcPlayerExtensions.None)

  /** As [prepareNow], for bytes decoded with [extensions]' operations. */
  @ExperimentalRcPlayerApi
  public fun prepareNow(bytes: ByteArray, extensions: RcPlayerExtensions): RcPreparedDocument {
    val key = RcContentKey(bytes, extensions)
    documents.load()[key]?.let {
      return it
    }
    val prepared = RcPreparedDocument(decodeCmpDocument(bytes, extensions))
    preparedCount.incrementAndFetch()
    put(key, prepared)
    return prepared
  }

  /** How many documents [prepare] and [prepareNow] have prepared (not found in memory). */
  public val prepared: Int
    get() = preparedCount.load()

  /**
   * How many times a player under [RcPreparation.Background] composed bytes that were not prepared
   * yet, and so drew nothing for at least a frame. Zero when a host prepares ahead of time.
   */
  public val misses: Int
    get() = missCount.load()

  /** How many prepared documents are in memory. */
  public val size: Int
    get() = documents.load().size

  internal fun recordMiss() {
    missCount.incrementAndFetch()
  }

  private fun put(key: RcContentKey, value: RcPreparedDocument) {
    while (true) {
      val current = documents.load()
      val next = LinkedHashMap<RcContentKey, RcPreparedDocument>(current.size + 1)
      next.putAll(current)
      next.remove(key)
      next[key] = value
      while (next.size > maxDocuments) next.remove(next.keys.first())
      if (documents.compareAndSet(current, next)) return
    }
  }
}

/** Bytes compared by content (hashed once), with the extensions they decode under. */
private class RcContentKey(val bytes: ByteArray, val extensions: RcPlayerExtensions) {
  private val hash = bytes.contentHashCode() * 31 + extensions.hashCode()

  override fun hashCode(): Int = hash

  override fun equals(other: Any?): Boolean =
    other is RcContentKey &&
      other.hash == hash &&
      other.extensions == extensions &&
      (other.bytes === bytes || other.bytes.contentEquals(bytes))
}

/**
 * [bytes] prepared as [preparation] says: under [RcPreparation.InComposition] at once, in this
 * composition; under [RcPreparation.Background] from its cache, or null until a background
 * preparation finishes (the caller draws nothing, or a placeholder, meanwhile).
 */
@Composable
public fun rememberRcPreparedDocument(
  bytes: ByteArray,
  preparation: RcPreparation = LocalRcPreparation.current,
): RcPreparedDocument? {
  val extensions = LocalRcPlayerExtensions.current
  return when (preparation) {
    RcPreparation.InComposition ->
      remember(bytes, extensions) { RcPreparedDocument(decodeCmpDocument(bytes, extensions)) }
    is RcPreparation.Background -> {
      var ready by
        remember(bytes, extensions, preparation) {
          val cached = preparation.cache.peek(bytes, extensions)
          if (cached == null) preparation.cache.recordMiss()
          mutableStateOf(cached)
        }
      if (ready == null) {
        LaunchedEffect(bytes, extensions, preparation) {
          ready = preparation.cache.prepare(bytes, extensions, preparation.context)
        }
      }
      ready
    }
  }
}

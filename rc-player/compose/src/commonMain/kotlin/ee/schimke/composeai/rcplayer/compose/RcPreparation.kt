@file:OptIn(ExperimentalAtomicApi::class)

package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
   * empty frame either. A cache with an [RcPreprocessor] rewrites each document for the display it
   * is played on (`rc-player-edit`'s `forDisplay`) as part of that background work.
   *
   * Only the document is prepared off the main thread. Composing, laying out, drawing and the
   * semantics tree stay the player's ordinary Compose nodes, on the composition's thread.
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
 * The display a document is prepared for: what an [RcPreprocessor] may hardcode into it. [density]
 * and [fontScale] are Compose's (`LocalDensity`).
 */
public data class RcDisplay(val density: Float, val fontScale: Float = 1f)

/**
 * A rewrite of a decoded document for one display, run once per document and display in the
 * background, before the document is prepared: hardcode the density, freeze values no card binds,
 * fold the constants that leaves (`rc-player-edit`). Every player of the document on that display
 * then plays the result. It must not change what the document shows on [RcDisplay]; one that throws
 * leaves the document as it was.
 */
public fun interface RcPreprocessor {
  public fun preprocess(document: RcDocument, display: RcDisplay): RcDocument
}

/**
 * Prepared documents by content (and, with a [preprocessor], by display), shared by every player
 * and host that uses it. Reads ([peek]) never prepare and never block, so composition can make
 * them; [prepare] does the work on a background context and keeps the result. Bounded: past
 * [maxDocuments] the oldest is dropped, which only costs the next card the work again.
 */
public class RcPreparedDocumentCache(
  private val maxDocuments: Int = 32,
  /**
   * Run on each document before it is prepared, for the display a player asks for it on. Without
   * one, documents are prepared as decoded and the display is ignored.
   */
  public val preprocessor: RcPreprocessor? = null,
) {
  private val documents = AtomicReference<Map<RcContentKey, RcPreparedDocument>>(emptyMap())
  private val preparedCount = AtomicInt(0)
  private val missCount = AtomicInt(0)
  private val pending = Mutex()
  private val pendingByKey = mutableMapOf<RcContentKey, CompletableDeferred<RcPreparedDocument>>()

  /** [bytes]' prepared document for [display] if it is in memory, else null. Never prepares. */
  public fun peek(bytes: ByteArray, display: RcDisplay? = null): RcPreparedDocument? =
    peek(bytes, RcPlayerExtensions.None, display)

  /** As [peek], for bytes decoded with [extensions]' operations. */
  @ExperimentalRcPlayerApi
  public fun peek(
    bytes: ByteArray,
    extensions: RcPlayerExtensions,
    display: RcDisplay? = null,
  ): RcPreparedDocument? = documents.load()[key(bytes, extensions, display)]

  /**
   * [bytes]' prepared document for [display]: from memory, or decoded, preprocessed and prepared on
   * [context] and kept. Concurrent calls for the same bytes and display (a screen of cards of one
   * template arriving together) share one preparation.
   */
  public suspend fun prepare(
    bytes: ByteArray,
    display: RcDisplay? = null,
    context: CoroutineContext = Dispatchers.Default,
  ): RcPreparedDocument = prepare(bytes, RcPlayerExtensions.None, display, context)

  /** As [prepare], for bytes decoded with [extensions]' operations. */
  @ExperimentalRcPlayerApi
  public suspend fun prepare(
    bytes: ByteArray,
    extensions: RcPlayerExtensions,
    display: RcDisplay? = null,
    context: CoroutineContext = Dispatchers.Default,
  ): RcPreparedDocument {
    val key = key(bytes, extensions, display)
    documents.load()[key]?.let {
      return it
    }
    val mine = CompletableDeferred<RcPreparedDocument>()
    val shared = pending.withLock { pendingByKey.getOrPut(key) { mine } }
    if (shared !== mine) {
      try {
        return shared.await()
      } catch (e: CancellationException) {
        // The caller that owned the preparation went away (its card scrolled off) before it
        // finished. Unless this caller was cancelled too, prepare again: from memory if the owner
        // got that far, or as the new owner.
        currentCoroutineContext().ensureActive()
        return prepare(bytes, extensions, display, context)
      }
    }
    try {
      val prepared = withContext(context) { prepareNow(bytes, extensions, display) }
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
   * [bytes]' prepared document for [display]: from memory, or prepared on the calling thread and
   * kept. For a host that is already off the main thread, or that has chosen
   * [RcPreparation.InComposition]; a player under [RcPreparation.Background] never calls it.
   */
  public fun prepareNow(bytes: ByteArray, display: RcDisplay? = null): RcPreparedDocument =
    prepareNow(bytes, RcPlayerExtensions.None, display)

  /** As [prepareNow], for bytes decoded with [extensions]' operations. */
  @ExperimentalRcPlayerApi
  public fun prepareNow(
    bytes: ByteArray,
    extensions: RcPlayerExtensions,
    display: RcDisplay? = null,
  ): RcPreparedDocument {
    val key = key(bytes, extensions, display)
    documents.load()[key]?.let {
      return it
    }
    val decoded = decodeCmpDocument(bytes, extensions)
    val document =
      if (key.display == null) decoded
      else
        try {
          preprocessor!!.preprocess(decoded, key.display)
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // A rewrite that cannot handle this document leaves it as it was.
          decoded
        }
    val prepared = RcPreparedDocument(document)
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

  /** Drops all prepared documents from memory. */
  public fun clear() {
    documents.store(emptyMap())
  }

  /**
   * Drops the oldest prepared documents until at most [maxDocuments] remain in memory. Does not
   * cancel in-flight preparations.
   */
  public fun trimToSize(maxDocuments: Int) {
    require(maxDocuments >= 0) { "maxDocuments must be >= 0, was $maxDocuments" }
    if (maxDocuments == 0) {
      clear()
      return
    }
    while (true) {
      val current = documents.load()
      if (current.size <= maxDocuments) return
      val next = LinkedHashMap<RcContentKey, RcPreparedDocument>(maxDocuments)
      val drop = current.size - maxDocuments
      var index = 0
      for ((key, value) in current) {
        if (index++ >= drop) next[key] = value
      }
      if (documents.compareAndSet(current, next)) return
    }
  }

  internal fun recordMiss() {
    missCount.incrementAndFetch()
  }

  /** The display only keys a document that a [preprocessor] rewrites for it. */
  private fun key(bytes: ByteArray, extensions: RcPlayerExtensions, display: RcDisplay?) =
    RcContentKey(bytes, extensions, display.takeIf { preprocessor != null })

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

/**
 * Bytes compared by content (hashed once), with the extensions and display they are prepared for.
 */
private class RcContentKey(
  val bytes: ByteArray,
  val extensions: RcPlayerExtensions,
  val display: RcDisplay?,
) {
  private val hash =
    (bytes.contentHashCode() * 31 + extensions.hashCode()) * 31 + display.hashCode()

  override fun hashCode(): Int = hash

  override fun equals(other: Any?): Boolean =
    other is RcContentKey &&
      other.hash == hash &&
      other.extensions == extensions &&
      other.display == display &&
      (other.bytes === bytes || other.bytes.contentEquals(bytes))
}

/**
 * [bytes] prepared as [preparation] says: under [RcPreparation.InComposition] at once, in this
 * composition; under [RcPreparation.Background] from its cache, for this composition's display when
 * the cache preprocesses, or null until a background preparation finishes (the caller draws
 * nothing, or a placeholder, meanwhile).
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
      val density = LocalDensity.current
      val display = RcDisplay(density.density, density.fontScale)
      var ready by
        remember(bytes, extensions, preparation, display) {
          val cached = preparation.cache.peek(bytes, extensions, display)
          if (cached == null) preparation.cache.recordMiss()
          mutableStateOf(cached)
        }
      if (ready == null) {
        LaunchedEffect(bytes, extensions, preparation, display) {
          ready = preparation.cache.prepare(bytes, extensions, display, preparation.context)
        }
      }
      ready
    }
  }
}

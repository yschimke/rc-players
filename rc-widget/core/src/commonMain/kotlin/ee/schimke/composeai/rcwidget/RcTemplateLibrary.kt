package ee.schimke.composeai.rcwidget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persisted templates by key: a template's key names what shapes it (the design, its size class,
 * its theme) and never the values bound into it. Reads and writes must not throw; a store that
 * can't keep one returns null on the next read.
 */
public interface RcTemplateStore {
  public fun read(key: String): ByteArray?

  public fun write(key: String, bytes: ByteArray)
}

/**
 * Templates made on the device: the local path, and the main one. [get] reads a template from the
 * first of [stores] that has it (a disk cache of earlier captures, then templates bundled with the
 * app, then — if the app uses one — a server's), and otherwise [capture]s it from the app's own
 * composables and writes it to every store, so a key is captured once rather than on every start.
 *
 * Captures are single-flight per key: a second caller for a key waits for the first's result while
 * other keys go ahead. A key whose capture fails is not retried in this process — a capture costs a
 * frame loop — and [onCaptureFailure] hears of it once; the caller draws without the template.
 */
public class RcTemplateLibrary(
  private val stores: List<RcTemplateStore> = emptyList(),
  private val onCaptureFailure: (key: String, Throwable) -> Unit = { _, _ -> },
  private val capture: suspend (key: String) -> ByteArray?,
) {
  private val lock = Mutex()
  private val locks = HashMap<String, Mutex>()
  private val memory = HashMap<String, ByteArray>()
  private val failed = HashSet<String>()

  /** [key]'s template, read or captured; null when there is none and it can't be captured. */
  public suspend fun get(key: String): ByteArray? {
    val keyLock = lock.withLock {
      if (key in failed) return null
      memory[key]?.let {
        return it
      }
      locks.getOrPut(key) { Mutex() }
    }
    return keyLock.withLock {
      lock.withLock { memory[key] }
        ?: (read(key) ?: captureAndStore(key))?.also { lock.withLock { memory[key] = it } }
    }
  }

  private fun read(key: String): ByteArray? = stores.firstNotNullOfOrNull {
    runCatching { it.read(key) }.getOrNull()
  }

  private suspend fun captureAndStore(key: String): ByteArray? {
    val bytes =
      try {
        capture(key)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (lock.withLock { failed.add(key) }) runCatching { onCaptureFailure(key, e) }
        null
      } ?: return null
    for (store in stores) runCatching { store.write(key, bytes) }
    return bytes
  }

  /**
   * Forgets the templates held in memory, keeping the stores: each is a read away rather than a
   * capture. Call it when the system asks the app to trim memory.
   */
  public suspend fun releaseMemory() {
    lock.withLock { memory.clear() }
  }

  /** Forgets [key] everywhere in memory, and its failure: the next [get] reads or captures it. */
  public suspend fun invalidate(key: String) {
    lock.withLock {
      memory.remove(key)
      failed.remove(key)
    }
  }
}

/**
 * Templates bundled with an app, laid out as a static design server would serve them: an index of
 * `<sha256> <key>` lines and `blobs/<sha256>.rc`. Read-only; a blob that doesn't match its index
 * entry is never returned, and a missing or unreadable index bundles nothing.
 *
 * Make one at build time with [layout] from captures of the app's own designs, so a fresh install
 * shows every widget without capturing first.
 */
public class RcBundledTemplates(
  /** Reads a file of the bundle by its path within it (`index.txt`, `blobs/…`), or null. */
  private val open: (path: String) -> ByteArray?
) : RcTemplateStore {
  private val shas: Map<String, String> by lazy {
    runCatching {
      open(INDEX)
        ?.decodeToString()
        ?.lineSequence()
        ?.mapNotNull { line ->
          val sha = line.substringBefore(' ', "")
          val key = line.substringAfter(' ', "")
          if (sha.length == 64 && key.isNotEmpty()) key to sha else null
        }
        ?.toMap()
    }
      .getOrNull()
      .orEmpty()
  }

  /** Every key bundled. */
  public val keys: Set<String>
    get() = shas.keys

  override fun read(key: String): ByteArray? {
    val sha = shas[key] ?: return null
    return runCatching { open(blobPath(sha)) }.getOrNull()?.takeIf { RcSha256.hex(it) == sha }
  }

  override fun write(key: String, bytes: ByteArray) {}

  public companion object {
    public const val INDEX: String = "index.txt"

    public fun blobPath(sha256: String): String = "blobs/$sha256.rc"

    /**
     * The files a bundle of [templates] consists of, by path: the index and one blob per distinct
     * template. Write them under an app's assets (or a static host) and read them with
     * [RcBundledTemplates].
     */
    public fun layout(templates: Map<String, ByteArray>): Map<String, ByteArray> = buildMap {
      val index = StringBuilder()
      for ((key, bytes) in templates.entries.sortedBy { it.key }) {
        require('\n' !in key) { "a key is one line: $key" }
        val sha = RcSha256.hex(bytes)
        index.append(sha).append(' ').append(key).append('\n')
        put(blobPath(sha), bytes)
      }
      put(INDEX, index.toString().encodeToByteArray())
    }
  }
}

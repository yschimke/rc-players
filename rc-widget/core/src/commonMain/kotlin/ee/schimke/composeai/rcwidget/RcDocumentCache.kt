package ee.schimke.composeai.rcwidget

/**
 * A bounded least-recently-used cache of encoded documents, sized in bytes.
 *
 * Key it by what shapes the document — the design, the size, the theme, the display — and never by
 * the values bound into it: a template is the same bytes for every state, so keying by values
 * re-captures on every update.
 *
 * Not thread-safe; guard it, or keep one per thread.
 */
public class RcDocumentCache<K : Any>(public val maxBytes: Long) {
  private val entries = LinkedHashMap<K, ByteArray>()

  /** The bytes held. */
  public var sizeBytes: Long = 0
    private set

  public val size: Int
    get() = entries.size

  public operator fun get(key: K): ByteArray? {
    val bytes = entries.remove(key) ?: return null
    entries[key] = bytes
    return bytes
  }

  public fun put(key: K, bytes: ByteArray) {
    entries.remove(key)?.let { sizeBytes -= it.size }
    entries[key] = bytes
    sizeBytes += bytes.size
    trimTo(maxBytes)
  }

  /** The bytes for [key], made by [create] when absent. */
  public inline fun getOrPut(key: K, create: () -> ByteArray): ByteArray =
    get(key) ?: create().also { put(key, it) }

  public fun remove(key: K): ByteArray? = entries.remove(key)?.also { sizeBytes -= it.size }

  public fun clear() {
    entries.clear()
    sizeBytes = 0
  }

  /** Evicts the least recently used entries until at most [bytes] are held. */
  public fun trimTo(bytes: Long) {
    val iterator = entries.entries.iterator()
    while (sizeBytes > bytes && iterator.hasNext()) {
      sizeBytes -= iterator.next().value.size
      iterator.remove()
    }
  }
}

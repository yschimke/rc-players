package ee.schimke.composeai.rcplayer.protocol

/** A structured failure while reading an AndroidX Remote Compose byte stream. */
public class RcWireException(
  public val byteOffset: Int,
  public val operationOpcode: Int? = null,
  public val operationName: String? = null,
  public val fieldName: String? = null,
  message: String,
) :
  IllegalArgumentException(
    buildString {
      append(message)
      append(" at byte ")
      append(byteOffset)
      operationOpcode?.let { append(", opcode=$it") }
      operationName?.let { append(" ($it)") }
      fieldName?.let { append(", field=$it") }
    }
  )

/** Bounds-checked, big-endian reader matching AndroidX `WireBuffer`. */
public class RcWireReader
internal constructor(
  private val bytes: ByteArray,
  public val limits: RcWireLimits,
  private val idRemapper: RcIdRemapper?,
  internal val libraryApiLevel: Int,
  internal val profile: Int,
) {
  /** Retains the original public constructor and its baseline AndroidX reader semantics. */
  public constructor(
    bytes: ByteArray,
    limits: RcWireLimits = RcWireLimits(),
  ) : this(
    bytes,
    limits,
    idRemapper = null,
    libraryApiLevel = 8,
    profile = RcWireProfiles.ANDROIDX_BASELINE,
  )

  public var offset: Int = 0
    private set

  public val remaining: Int
    get() = bytes.size - offset

  private var opcode: Int? = null
  private var operationName: String? = null

  public inline fun <T> inOperation(opcode: Int, name: String, block: RcWireReader.() -> T): T {
    val oldOpcode = currentOpcode()
    val oldName = currentOperationName()
    setOperation(opcode, name)
    return try {
      block()
    } finally {
      setOperation(oldOpcode, oldName)
    }
  }

  public fun readU8(field: String): Int {
    requireAvailable(1, field)
    return bytes[offset++].toInt() and 0xff
  }

  public fun readBoolean(field: String): Boolean =
    when (val value = readU8(field)) {
      0 -> false
      1 -> true
      else -> fail(field, "Invalid boolean byte $value")
    }

  public fun readU16(field: String): Int {
    requireAvailable(2, field)
    return (readU8Unchecked() shl 8) or readU8Unchecked()
  }

  public fun readInt(field: String): Int {
    requireAvailable(4, field)
    return (readU8Unchecked() shl 24) or
      (readU8Unchecked() shl 16) or
      (readU8Unchecked() shl 8) or
      readU8Unchecked()
  }

  /** Reads an integer resource id and applies the active macro-expansion mapping. */
  public fun readId(field: String): Int = readInt(field).let { idRemapper?.resolve(it) ?: it }

  /** Applies the active mapping to an id already unpacked from an operation-specific flag word. */
  internal fun resolveId(id: Int): Int = idRemapper?.resolve(id) ?: id

  /** Reads an id declaration, allocating a collision-free macro-local id when expanding. */
  public fun readDeclaredId(field: String): Int =
    readInt(field).let { idRemapper?.declare(it) ?: it }

  public fun readLong(field: String): Long {
    requireAvailable(8, field)
    var result = 0L
    repeat(8) { result = (result shl 8) or readU8Unchecked().toLong() }
    return result
  }

  /** Reads a long, translating AndroidX's long-encoded resource-id form when present. */
  public fun readRemappedLong(field: String): Long {
    val value = readLong(field)
    if (value !in 0x100000000L..0x1003fffffL) return value
    val id = (value - 0x100000000L).toInt()
    val mapped = idRemapper?.resolve(id) ?: return value
    return 0x100000000L + mapped
  }

  /** Read a float as raw bits so NaN-boxed ids are never canonicalised. */
  public fun readFloatWord(field: String): RcFloatWord {
    val word = RcFloatWord(readInt(field))
    val id = word.referencedId ?: return word
    val mapped = idRemapper?.resolve(id) ?: return word
    return RcFloatWord((word.bits and 0xffc00000.toInt()) or (mapped and 0x003fffff))
  }

  public fun readByteArray(field: String, maximum: Int = limits.maxBlobBytes): ByteArray {
    val count = readCount("$field.length", maximum)
    requireAvailable(count, field)
    return bytes.copyOfRange(offset, offset + count).also { offset += count }
  }

  /** Reads an already-sized byte region, such as a macro definition body. */
  public fun readRawBytes(
    count: Int,
    field: String,
    maximum: Int = limits.maxBlobBytes,
  ): ByteArray {
    if (count < 0 || count > maximum) fail(field, "Invalid byte count $count; expected 0..$maximum")
    requireAvailable(count, field)
    return bytes.copyOfRange(offset, offset + count).also { offset += count }
  }

  /** Advances over an already-sized region after validating its bounds. */
  public fun skipRawBytes(count: Int, field: String) {
    requireAvailable(count, field)
    offset += count
  }

  public fun readUtf8(field: String, maximum: Int = limits.maxStringBytes): String {
    val data = readByteArray(field, maximum)
    return try {
      data.decodeToString(throwOnInvalidSequence = true)
    } catch (failure: CharacterCodingException) {
      fail(field, "Invalid UTF-8", failure)
    }
  }

  public fun readCount(field: String, maximum: Int): Int {
    val count = readInt(field)
    if (count < 0 || count > maximum) {
      fail(field, "Invalid count $count; expected 0..$maximum")
    }
    return count
  }

  public fun ensureExhausted() {
    if (remaining != 0) fail(null, "$remaining trailing bytes")
  }

  public fun fail(field: String?, message: String, cause: Throwable? = null): Nothing {
    val detail = if (cause?.message.isNullOrEmpty()) message else "$message: ${cause.message}"
    throw RcWireException(offset, opcode, operationName, field, detail)
  }

  @PublishedApi internal fun currentOpcode(): Int? = opcode

  @PublishedApi internal fun currentOperationName(): String? = operationName

  @PublishedApi
  internal fun setOperation(opcode: Int?, name: String?) {
    this.opcode = opcode
    this.operationName = name
  }

  private fun requireAvailable(count: Int, field: String?) {
    if (count < 0 || remaining < count) {
      fail(field, "Truncated input: need $count bytes, have $remaining")
    }
  }

  private fun readU8Unchecked(): Int = bytes[offset++].toInt() and 0xff
}

/** Mutable, forkable ID translation context used while materializing macro bodies. */
public class RcIdRemapper
private constructor(
  initialMappings: Map<Int, Int>,
  private val allocator: RcIdAllocator,
) {
  private val mappings: MutableMap<Int, Int> = initialMappings.toMutableMap()

  public fun resolve(id: Int): Int = mappings[id] ?: id

  public fun declare(id: Int): Int {
    if (id == -1 || id in 0..41) return resolve(id)
    return mappings.getOrPut(id, allocator::allocate)
  }

  public fun fork(additionalMappings: Map<Int, Int> = emptyMap()): RcIdRemapper =
    RcIdRemapper(mappings + additionalMappings, allocator)

  public companion object {
    public fun expanding(
      mappings: Map<Int, Int> = emptyMap(),
      reservedIds: Set<Int> = emptySet(),
    ): RcIdRemapper = RcIdRemapper(mappings, RcIdAllocator(reservedIds))
  }
}

private class RcIdAllocator(private val reservedIds: Set<Int>, private var next: Int = 42) {
  fun allocate(): Int {
    while (next in reservedIds) next++
    if (next > 0x3fffff) error("Macro expansion exhausted the RC id space")
    return next++
  }
}

public data class RcWireLimits(
  val maxDocumentBytes: Int = 16 * 1024 * 1024,
  val maxBlobBytes: Int = 8 * 1024 * 1024,
  val maxStringBytes: Int = 4_000,
  val maxTableEntries: Int = 1_000,
  val maxPaintWords: Int = 1_024,
  val maxPathWords: Int = 20_000,
  val maxCollectionEntries: Int = 2_000,
  val maxImageDimension: Int = 8_192,
)

/** AndroidX document profile bits consumed by conditional [RcSkip] operations. */
public object RcWireProfiles {
  public const val ANDROIDX_BASELINE: Int = 0x200
  public const val ANDROIDX_EXPERIMENTAL: Int = 0x201
}

/** Growable, big-endian writer used by symmetric operation codecs and conformance tests. */
public class RcWireWriter(initialCapacity: Int = 256) {
  private var bytes: ByteArray = ByteArray(initialCapacity.coerceAtLeast(1))
  public var size: Int = 0
    private set

  public fun writeU8(value: Int) {
    ensureCapacity(1)
    bytes[size++] = value.toByte()
  }

  public fun writeBoolean(value: Boolean) {
    writeU8(if (value) 1 else 0)
  }

  public fun writeU16(value: Int) {
    ensureCapacity(2)
    bytes[size++] = (value ushr 8).toByte()
    bytes[size++] = value.toByte()
  }

  public fun writeInt(value: Int) {
    ensureCapacity(4)
    bytes[size++] = (value ushr 24).toByte()
    bytes[size++] = (value ushr 16).toByte()
    bytes[size++] = (value ushr 8).toByte()
    bytes[size++] = value.toByte()
  }

  public fun writeLong(value: Long) {
    ensureCapacity(8)
    for (shift in 56 downTo 0 step 8) bytes[size++] = (value ushr shift).toByte()
  }

  public fun writeFloatWord(value: RcFloatWord) {
    writeInt(value.bits)
  }

  public fun writeByteArray(value: ByteArray) {
    writeInt(value.size)
    ensureCapacity(value.size)
    value.copyInto(bytes, size)
    size += value.size
  }

  /** Writes bytes without a length prefix. */
  public fun writeRawBytes(value: ByteArray) {
    ensureCapacity(value.size)
    value.copyInto(bytes, size)
    size += value.size
  }

  public fun writeUtf8(value: String) {
    writeByteArray(value.encodeToByteArray())
  }

  public fun toByteArray(): ByteArray = bytes.copyOf(size)

  private fun ensureCapacity(additional: Int) {
    val required = size + additional
    if (required <= bytes.size) return
    var next = bytes.size
    while (next < required) next = (next * 2).coerceAtLeast(required)
    bytes = bytes.copyOf(next)
  }
}

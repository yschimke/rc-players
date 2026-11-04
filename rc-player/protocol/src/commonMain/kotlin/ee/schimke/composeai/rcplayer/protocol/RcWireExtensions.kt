package ee.schimke.composeai.rcplayer.protocol

/**
 * Marks the wire-extension API: a way to decode operations this library does not ship, such as the
 * draft 3D operations still in AndroidX review. Opcodes registered through it are not part of any
 * published AndroidX profile and may be reassigned upstream, so a document that uses them is tied
 * to the extension version that wrote it.
 */
@RequiresOptIn(
  message =
    "Wire extensions decode draft operations whose layout may change before AndroidX ships them.",
  level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class ExperimentalRcWireExtension

/**
 * An operation decoded by an [RcWireExtension] rather than by [RcDocumentCodec] itself.
 *
 * The player treats it as an opaque leaf: it is kept in document order, and drawing hands it back
 * to the extension that decoded it. [extensionId] names that extension.
 */
public interface RcExtensionOperation : RcOperation {
  public val extensionId: String
}

/**
 * A named set of codecs for operations outside the built-in table.
 *
 * Every codec's opcode must be one the built-in table leaves free; [RcWireExtensions] refuses one
 * that would shadow a built-in operation, because a document that decodes differently depending on
 * which extensions a host installed is worse than one that fails to decode.
 */
@ExperimentalRcWireExtension
public interface RcWireExtension {
  /** Stable identifier, also carried by every operation this extension decodes. */
  public val id: String

  public val codecs: List<RcOperationCodec<out RcExtensionOperation>>
}

/** The wire extensions a decode may consult, in addition to the built-in operations. */
@ExperimentalRcWireExtension
public class RcWireExtensions(extensions: List<RcWireExtension>) {
  public constructor(vararg extensions: RcWireExtension) : this(extensions.toList())

  public val extensions: List<RcWireExtension> = extensions.toList()

  private val codecs: Map<Int, RcOperationCodec<out RcExtensionOperation>> = buildMap {
    for (extension in this@RcWireExtensions.extensions) {
      for (codec in extension.codecs) {
        val opcode = codec.spec.opcode
        require(opcode in 0..255) { "Extension ${extension.id}: opcode $opcode is not one byte" }
        require(!RcDocumentCodec.isBuiltIn(opcode)) {
          "Extension ${extension.id}: opcode $opcode (${codec.spec.name}) is a built-in operation"
        }
        val previous = put(opcode, codec)
        require(previous == null) {
          "Extension ${extension.id}: opcode $opcode is already registered as ${previous?.spec?.name}"
        }
      }
    }
  }

  /** Opcodes this set decodes. */
  public val opcodes: Set<Int>
    get() = codecs.keys

  public fun codec(opcode: Int): RcOperationCodec<out RcExtensionOperation>? = codecs[opcode]

  /** This set and [other] together; an opcode both register is an error. */
  public operator fun plus(other: RcWireExtensions): RcWireExtensions =
    RcWireExtensions(extensions + other.extensions.filter { it !in extensions })

  public companion object {
    /** No extensions: exactly the built-in decoder. */
    public val None: RcWireExtensions = RcWireExtensions(emptyList())
  }
}

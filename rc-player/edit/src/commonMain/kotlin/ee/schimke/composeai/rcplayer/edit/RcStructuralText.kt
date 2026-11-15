package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcIdMap
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRemark
import ee.schimke.composeai.rcplayer.protocol.RcSoundData
import ee.schimke.composeai.rcplayer.protocol.RcTextData

/**
 * The document as text a person or a line diff reads: the header's properties by name, then one
 * operation per line, indented by the container it sits in, with no `ContainerEnd` lines (the
 * indentation says where a container ends).
 *
 * The text is a pure function of the document's bytes, so it is stable across runs and machines:
 * two documents print the same text exactly when they decode to equal operations (and bytes that
 * decode alike re-encode alike). That makes it a `git diff` textconv for `.rc` files — see
 * `remote-compose-player --dump` — and the input [rcStructuralDiff] compares.
 *
 * An operation prints as its model's fields. A float word prints as its literal value, or as `@id`
 * when it is a NaN-boxed reference to the value `id`. Embedded bytes (images, fonts, sounds, macro
 * bodies) print as their size and a content hash, never inline. A document whose containers do not
 * balance prints flat, one operation per line, rather than guessing where a container ends.
 */
public fun RcDocument.toStructuralText(): String = buildString {
  structuralLines(this@toStructuralText).forEach { appendLine(it.text) }
}

/** One line of [toStructuralText], with what it is the line of. */
internal class RcStructuralLine(val depth: Int, val text: String, val operation: RcOperation?)

internal fun structuralLines(document: RcDocument): List<RcStructuralLine> {
  val lines = mutableListOf<RcStructuralLine>()
  headerLines(document.header).forEach { lines += RcStructuralLine(0, it, null) }
  val tree = operationTree(document.operations)
  if (tree == null) {
    lines += RcStructuralLine(0, "# containers do not balance: printed flat", null)
    document.operations.forEach { lines += RcStructuralLine(0, describeOperation(it), it) }
  } else {
    fun visit(node: RcOperationNode, depth: Int) {
      lines +=
        RcStructuralLine(
          depth,
          "  ".repeat(depth) + describeOperation(node.operation),
          node.operation,
        )
      node.children.forEach { visit(it, depth + 1) }
    }
    tree.forEach { visit(it, 0) }
  }
  return lines
}

private fun headerLines(header: RcHeader): List<String> = buildList {
  val v = header.version
  add("header version=${v.major}.${v.minor}.${v.patch}" + if (header.modern) "" else " (legacy)")
  if (!header.modern) {
    add(
      "  width=${header.legacyWidth} height=${header.legacyHeight} capabilities=${header.legacyCapabilities}",
    )
  }
  header.properties.forEach { property ->
    val value =
      when (val value = property.value) {
        is RcHeaderValue.IntValue -> value.value.toString()
        is RcHeaderValue.LongValue -> "${value.value}L"
        is RcHeaderValue.FloatValue -> describeFloatWord(value.value)
        is RcHeaderValue.StringValue -> quote(value.value)
      }
    add("  ${headerKeyName(property.key)}=$value")
  }
}

/** One operation, as [toStructuralText] prints it. */
internal fun describeOperation(operation: RcOperation): String =
  when (operation) {
    is RcNoArg -> operationName(operation.opcode)
    is RcBitmapData ->
      "RcBitmapData(imageId=${operation.imageId}, width=${operation.width}, " +
        "height=${operation.height}, type=${operation.type}, encoding=${operation.encoding}, " +
        "data=${describeBytes(operation.data, operation.encoding)})"
    is RcFontData ->
      "RcFontData(fontId=${operation.fontId}, type=${operation.type}, data=${describeBytes(operation.data)})"
    is RcSoundData ->
      "RcSoundData(soundId=${operation.soundId}, data=${describeBytes(operation.data)})"
    is RcMacroDefine ->
      "RcMacroDefine(id=${operation.id}, parameterIds=${operation.parameterIds}, " +
        "body=${describeBytes(operation.body)})"
    // Operations that hold text print it quoted and untouched: the rewrites below apply to a
    // model's fields, never to what a string says.
    is RcTextData -> "RcTextData(id=${operation.id}, text=${quote(operation.text)})"
    is RcRemark -> "RcRemark(text=${quote(operation.text)})"
    is RcNamedVariable ->
      "RcNamedVariable(id=${operation.id}, type=${operation.type}, name=${quote(operation.name)})"
    is RcIdMap ->
      "RcIdMap(id=${operation.id}, entries=" +
        operation.entries.joinToString(", ", "[", "]") {
          "(name=${quote(it.name)}, type=${it.type}, id=${it.id})"
        } +
        ")"
    is RcBitmapFontData ->
      "RcBitmapFontData(fontId=${operation.fontId}, glyphs=" +
        operation.glyphs.joinToString(", ", "[", "]") {
          "(chars=${quote(it.chars)}, bitmapId=${it.bitmapId}, margins=[${it.marginLeft}, " +
            "${it.marginTop}, ${it.marginRight}, ${it.marginBottom}], " +
            "size=${it.bitmapWidth}x${it.bitmapHeight})"
        } +
        ", kerning=" +
        operation.kerning.entries.joinToString(", ", "{", "}") { "${quote(it.key)}=${it.value}" } +
        ")"
    else -> readableFields(operation.toString())
  }

private val FLOAT_WORD = Regex("""RcFloatWord\(bits=(-?\d+)\)""")

// A data class prints an array field as its identity (`[B@1b6d3586`), which differs from run to
// run; the operations that carry bytes are printed above, and anything else such is masked here.
private val IDENTITY_ARRAY = Regex("""\[[BCSIJFDZ]@[0-9a-f]+""")

private fun readableFields(text: String): String =
  text
    .replace(FLOAT_WORD) { describeFloatWord(RcFloatWord(it.groupValues[1].toInt())) }
    .replace(IDENTITY_ARRAY, "<array>")

internal fun describeFloatWord(word: RcFloatWord): String =
  word.referencedId?.let { "@$it" } ?: word.value.toString()

private fun describeBytes(bytes: ByteArray, encoding: Int = RcBitmapData.ENCODING_INLINE): String =
  if (encoding == RcBitmapData.ENCODING_URL || encoding == RcBitmapData.ENCODING_FILE) {
    quote(bytes.decodeToString())
  } else {
    "<${bytes.size} bytes fnv1a:${fnv1a64(bytes).toULong().toString(16).padStart(16, '0')}>"
  }

/** FNV-1a, 64-bit: a stable content fingerprint for embedded bytes, not a security hash. */
internal fun fnv1a64(bytes: ByteArray): Long {
  var hash = -0x340d631b7bdddcdbL
  for (b in bytes) {
    hash = hash xor (b.toLong() and 0xff)
    hash *= 0x100000001b3L
  }
  return hash
}

internal fun quote(text: String): String = buildString {
  append('"')
  text.forEach { c ->
    when (c) {
      '"' -> append("\\\"")
      '\\' -> append("\\\\")
      '\n' -> append("\\n")
      '\t' -> append("\\t")
      else -> if (c < ' ') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
    }
  }
  append('"')
}

internal fun headerKeyName(key: Int): String =
  when (key) {
    RcHeader.DOC_WIDTH -> "DOC_WIDTH"
    RcHeader.DOC_HEIGHT -> "DOC_HEIGHT"
    RcHeader.DOC_DENSITY_AT_GENERATION -> "DOC_DENSITY_AT_GENERATION"
    RcHeader.DOC_DESIRED_FPS -> "DOC_DESIRED_FPS"
    RcHeader.DOC_CONTENT_DESCRIPTION -> "DOC_CONTENT_DESCRIPTION"
    RcHeader.DOC_SOURCE -> "DOC_SOURCE"
    RcHeader.DOC_DATA_UPDATE -> "DOC_DATA_UPDATE"
    RcHeader.HOST_EXCEPTION_HANDLER -> "HOST_EXCEPTION_HANDLER"
    RcHeader.DOC_PROFILES -> "DOC_PROFILES"
    RcHeader.FEATURE_PAINT_MEASURE -> "FEATURE_PAINT_MEASURE"
    RcHeader.DEBUG -> "DEBUG"
    RcHeader.FEATURE_MEASURE_VERSION -> "FEATURE_MEASURE_VERSION"
    RcHeader.FEATURE_TOUCH_VERSION -> "FEATURE_TOUCH_VERSION"
    RcHeader.TEST_TIME -> "TEST_TIME"
    RcHeader.TEST_AFTER -> "TEST_AFTER"
    RcHeader.TEST_COLOR_THEME -> "TEST_COLOR_THEME"
    RcHeader.TEST_ACTIONS -> "TEST_ACTIONS"
    RcHeader.FEATURE_PRIORITY_FIX -> "FEATURE_PRIORITY_FIX"
    RcHeader.FEATURE_LT_RESIZE -> "FEATURE_LT_RESIZE"
    RcHeader.FEATURE_ARRAY_LISTENERS -> "FEATURE_ARRAY_LISTENERS"
    RcHeader.FEATURE_CLICK_VERSION -> "FEATURE_CLICK_VERSION"
    RcHeader.DOC_DENSITY_BEHAVIOR -> "DOC_DENSITY_BEHAVIOR"
    RcHeader.FEATURE_OPTIMIZATION_LEVEL -> "FEATURE_OPTIMIZATION_LEVEL"
    RcHeader.FEATURE_DISALLOW_INTERCEPT_TOUCH -> "FEATURE_DISALLOW_INTERCEPT_TOUCH"
    RcHeader.FEATURE_DATA_PASS_CANVAS_OPS -> "FEATURE_DATA_PASS_CANVAS_OPS"
    RcHeader.COMPRESS -> "COMPRESS"
    RcHeader.DOC_SCROLL -> "DOC_SCROLL"
    else -> "KEY_$key"
  }

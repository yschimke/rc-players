package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcOperationInventory
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout

/** One reason a document will not play on a [RcTargetProfile]. */
public data class RcProfileViolation(
  val kind: Kind,
  /** Index into [RcDocument.operations], or null for the header. */
  val operationIndex: Int?,
  /** The offending operation's opcode, or null for the header. */
  val opcode: Int?,
  val message: String,
) {
  public enum class Kind {
    /** The header declares a newer document API level than the target reads, or none it reads. */
    HEADER_API_LEVEL,

    /** The header's form is one the target cannot read: a property header below level 7. */
    HEADER_FORMAT,

    /** The target does not decode this operation at all. */
    UNSUPPORTED_OPERATION,

    /**
     * The target decodes the operation, but not under the API level and profiles the document's own
     * header declares — which is the set a player decodes it with.
     */
    UNDECLARED_OPERATION,

    /** A field the target only reads as a literal holds a variable reference. */
    DYNAMIC_VALUE,

    /** A float expression uses an operator added after the target's API level. */
    FLOAT_OPERATOR,

    /**
     * A size the document's header has in pixels, which the header form the target reads has in dp:
     * reported by [adaptTo] when it is a variable, which no display density can convert.
     */
    DENSITY_SCALED,
  }
}

/**
 * Everything in this document that [target] would reject or misread, in document order with the
 * header first. Empty means the document plays there.
 *
 * Checked: the header's API level and form; every operation against the target's operation set and
 * against the set the document's own header selects; and the extra limits AndroidX's API level 6
 * writer enforces — no variable image alpha or text size, and no float operators beyond level 6 —
 * and what Android 16 itself reads only as literals: paddings, and background and border colours
 * and sizes.
 *
 * `Skip` guards are not visible here: a decoded document has already resolved them for the player
 * that decoded it. Decode with [RcTargetProfile.decode] to validate what the target actually reads.
 */
public fun RcDocument.validateFor(target: RcTargetProfile): List<RcProfileViolation> = buildList {
  val declaredLevel = header.apiLevel
  when {
    declaredLevel == null ->
      add(
        headerViolation(
          RcProfileViolation.Kind.HEADER_API_LEVEL,
          "Header version ${header.version.major}.${header.version.minor} names no API level " +
            "${target.name} reads",
        )
      )
    declaredLevel > target.apiLevel ->
      add(
        headerViolation(
          RcProfileViolation.Kind.HEADER_API_LEVEL,
          "Header declares API level $declaredLevel; ${target.name} reads up to ${target.apiLevel}",
        )
      )
  }
  if (target.apiLevel < 7 && header.modern) {
    add(
      headerViolation(
        RcProfileViolation.Kind.HEADER_FORMAT,
        "${target.name} reads only the fixed width/height header, not a property header",
      )
    )
  }

  // The set a player decodes this document with: the one its own header selects — unless the
  // target brings a fixed set of its own, as the Wear player does, which no header changes.
  val declared =
    if (target.fixedOperations) target
    else RcTargetProfile.of(declaredLevel ?: target.apiLevel, header.profiles)
  operations.forEachIndexed { index, operation ->
    val opcode = operation.opcode
    when {
      opcode !in target.supportedOpcodes ->
        add(
          RcProfileViolation(
            RcProfileViolation.Kind.UNSUPPORTED_OPERATION,
            index,
            opcode,
            "${operationName(opcode)} is not supported by ${target.name}",
          )
        )
      opcode !in declared.supportedOpcodes ->
        add(
          RcProfileViolation(
            RcProfileViolation.Kind.UNDECLARED_OPERATION,
            index,
            opcode,
            "${operationName(opcode)} is not decoded under the header's own " +
              "API level ${declared.apiLevel} and profiles 0x${declared.profiles.toString(16)}",
          )
        )
    }
    if (target.apiLevel <= 6) addAll(levelSixViolations(index, operation, target))
  }
}

/** The limits AndroidX's `WidgetsProfileWriterV6` puts on operations level 6 does decode. */
private fun levelSixViolations(
  index: Int,
  operation: RcOperation,
  target: RcTargetProfile,
): List<RcProfileViolation> = buildList {
  fun dynamic(field: String) =
    add(
      RcProfileViolation(
        RcProfileViolation.Kind.DYNAMIC_VALUE,
        index,
        operation.opcode,
        "${operationName(operation.opcode)}.$field must be a literal on ${target.name}",
      )
    )
  fun literals(vararg fields: Pair<String, RcFloatWord>) = fields.forEach { (field, value) ->
    if (value.isNaNEncoded) dynamic(field)
  }
  when (operation) {
    is RcImageLayout -> if (operation.alpha.isNaNEncoded) dynamic("alpha")
    is RcTextLayout -> if (operation.fontSize.isNaNEncoded) dynamic("fontSize")
    // Android 16 stores these raw and never resolves a variable in them.
    is RcPaddingModifier ->
      literals(
        "left" to operation.left,
        "top" to operation.top,
        "right" to operation.right,
        "bottom" to operation.bottom,
      )
    // Android 16 reads the four words AndroidX now spends on flags and a colour id as a rectangle,
    // so a colour reference draws the zero RGBA written beside it: transparent.
    is RcBackgroundModifier -> {
      if (operation.usesColorId) dynamic("colorId")
      literals(
        "red" to operation.red,
        "green" to operation.green,
        "blue" to operation.blue,
        "alpha" to operation.alpha,
      )
    }
    is RcBorderModifier -> {
      if (operation.usesColorId) dynamic("colorId")
      literals(
        "borderWidth" to operation.borderWidth,
        "roundedCorner" to operation.roundedCorner,
        "red" to operation.red,
        "green" to operation.green,
        "blue" to operation.blue,
        "alpha" to operation.alpha,
      )
    }
    is RcFloatExpression ->
      operation.expression
        .mapNotNull { it.floatOperator() }
        .filter { it >= LEVEL_6_OPERATOR_LIMIT }
        .distinct()
        .forEach { operator ->
          add(
            RcProfileViolation(
              RcProfileViolation.Kind.FLOAT_OPERATOR,
              index,
              operation.opcode,
              "Float operator ${operator - FLOAT_OPERATOR_OFFSET} was added after API level 6",
            )
          )
        }
    else -> Unit
  }
}

/** The operator a NaN-encoded expression word names, or null for a literal or variable. */
private fun RcFloatWord.floatOperator(): Int? = referencedId?.takeIf { it > FLOAT_OPERATOR_OFFSET }

/** AndroidX `AnimatedFloatExpression.OFFSET`: operator ids start above it. */
private const val FLOAT_OPERATOR_OFFSET = 0x310_000

/** AndroidX `AnimatedFloatExpression.API_LEVEL6_MAX`, exclusive as its writer checks it. */
private const val LEVEL_6_OPERATOR_LIMIT = FLOAT_OPERATOR_OFFSET + 50

private fun headerViolation(kind: RcProfileViolation.Kind, message: String) =
  RcProfileViolation(kind, operationIndex = null, opcode = null, message = message)

internal fun operationName(opcode: Int): String =
  RcOperationInventory.byOpcode[opcode]?.constantName ?: "Opcode $opcode"

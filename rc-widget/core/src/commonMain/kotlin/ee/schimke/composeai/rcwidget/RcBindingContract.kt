package ee.schimke.composeai.rcwidget

import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcplayer.edit.RcProfileViolation
import ee.schimke.composeai.rcplayer.edit.RcTargetProfile
import ee.schimke.composeai.rcplayer.edit.namedVariables
import ee.schimke.composeai.rcplayer.edit.validateFor
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable

/** The type of value a named variable holds, as a template declares it. */
public enum class RcBindingType(public val wireType: Int, public val wireName: String) {
  TEXT(RcNamedVariable.STRING_TYPE, "text"),
  FLOAT(RcNamedVariable.FLOAT_TYPE, "float"),
  COLOR(RcNamedVariable.COLOR_TYPE, "color"),
  IMAGE(RcNamedVariable.IMAGE_TYPE, "image"),
  INT(RcNamedVariable.INT_TYPE, "int"),
  LONG(RcNamedVariable.LONG_TYPE, "long"),
  FLOAT_ARRAY(RcNamedVariable.FLOAT_ARRAY_TYPE, "float[]");

  /** Whether [value] is a value of this type. A boolean is held as an integer. */
  public fun accepts(value: RcHardcodedValue): Boolean =
    when (value) {
      is RcHardcodedValue.Text -> this == TEXT
      is RcHardcodedValue.FloatValue -> this == FLOAT
      is RcHardcodedValue.Color -> this == COLOR
      is RcHardcodedValue.Integer,
      is RcHardcodedValue.BooleanValue -> this == INT
      is RcHardcodedValue.LongValue -> this == LONG
    }

  public companion object {
    public fun ofWire(type: Int): RcBindingType? = entries.firstOrNull { it.wireType == type }

    public fun ofWireName(name: String): RcBindingType? = entries.firstOrNull {
      it.wireName == name
    }
  }
}

/** One value a template reads by name: [name] without its `USER:` domain, and its [type]. */
public data class RcBinding(val name: String, val type: RcBindingType)

/**
 * The named values a template reads: what a host must supply for it to show its data, and what a
 * server can check a pushed design against.
 *
 * Derived from the document itself — its `NamedVariable` declarations — rather than from a schema
 * kept beside it, so it cannot disagree with what the player binds. [encode] gives a one-line text
 * form (`name:type` per line) for a manifest or a response header.
 */
public data class RcBindingContract(val bindings: List<RcBinding>) {

  /** The bindings by name. */
  public val byName: Map<String, RcBinding> by lazy { bindings.associateBy { it.name } }

  /** The bindings [values] does not supply, ignoring names under [ignoredPrefixes]. */
  public fun missing(
    values: Map<String, RcHardcodedValue>,
    ignoredPrefixes: Set<String> = emptySet(),
  ): List<RcBinding> = bindings.filter { binding ->
    binding.name !in values && ignoredPrefixes.none { binding.name.startsWith(it) }
  }

  /** The names in [values] that this contract declares with another type. */
  public fun mistyped(values: Map<String, RcHardcodedValue>): List<String> =
    values.mapNotNull { (name, value) ->
      val binding = byName[name] ?: return@mapNotNull null
      name.takeUnless { binding.type.accepts(value) }
    }

  /** One `name:type` line per binding. */
  public fun encode(): String = bindings.joinToString("\n") { "${it.name}:${it.type.wireName}" }

  public companion object {
    /** The `USER:` domain a host-written named value is declared in. */
    public const val USER_DOMAIN: String = "USER:"

    /**
     * The values [document] reads by name. Host-written (`USER:`) names lose the prefix; a name in
     * another domain (a system value such as `COLOR:`) keeps it.
     */
    public fun of(document: RcDocument): RcBindingContract =
      RcBindingContract(
        document.namedVariables
          .mapNotNull { variable ->
            RcBindingType.ofWire(variable.type)?.let {
              RcBinding(variable.name.removePrefix(USER_DOMAIN), it)
            }
          }
          .distinct()
      )

    /** [encode]'s text read back; lines it can't read are skipped. */
    public fun decode(text: String): RcBindingContract =
      RcBindingContract(
        text
          .lineSequence()
          .mapNotNull { line ->
            val name =
              line.substringBeforeLast(':', "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            RcBindingType.ofWireName(line.substringAfterLast(':'))?.let { RcBinding(name, it) }
          }
          .toList()
      )
  }
}

/** Why a template can't replace another: see [RcTemplateCheck.problems]. */
public sealed interface RcTemplateProblem {
  /** It uses something [target]'s player cannot read that the template it replaces did not. */
  public data class Unplayable(val violations: List<RcProfileViolation>) : RcTemplateProblem

  /** It reads a value no host supplies: none of the base's, nor an allowed host name. */
  public data class UnknownBinding(val binding: RcBinding) : RcTemplateProblem

  /** It reads a value the base also reads, with another type. */
  public data class TypeMismatch(
    val name: String,
    val expected: RcBindingType,
    val actual: RcBindingType,
  ) : RcTemplateProblem
}

/**
 * Checks a template someone else authored — a user's design pushed to a server — before it is
 * served in place of a known one.
 */
public object RcTemplateCheck {
  /**
   * What keeps [candidate] from standing in for [base] on [target]: a violation [base] doesn't
   * have, a value it reads that neither [base] reads nor [allowedNames] / [allowedPrefixes] allow
   * the host to write, or a value [base] reads with another type. Empty when it can replace it.
   */
  public fun problems(
    candidate: RcDocument,
    base: RcDocument?,
    target: RcTargetProfile,
    allowedNames: Set<String> = emptySet(),
    allowedPrefixes: Set<String> = emptySet(),
  ): List<RcTemplateProblem> = buildList {
    // A violation the base already has is tolerated wherever it sits: compared by what it is (kind
    // and operation), not by its index, which any operation added before it shifts.
    val tolerated = base?.validateFor(target).orEmpty().mapTo(HashSet()) { it.kind to it.opcode }
    val violations = candidate.validateFor(target).filterNot { (it.kind to it.opcode) in tolerated }
    if (violations.isNotEmpty()) add(RcTemplateProblem.Unplayable(violations))
    val known = base?.let(RcBindingContract::of)?.byName.orEmpty()
    for (binding in RcBindingContract.of(candidate).bindings) {
      val expected = known[binding.name]
      when {
        expected != null && expected.type != binding.type ->
          add(RcTemplateProblem.TypeMismatch(binding.name, expected.type, binding.type))
        expected != null -> Unit
        binding.name in allowedNames -> Unit
        allowedPrefixes.any { binding.name.startsWith(it) } -> Unit
        else -> add(RcTemplateProblem.UnknownBinding(binding))
      }
    }
  }
}

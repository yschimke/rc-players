package ee.schimke.composeai.rcplayer.protocol

public enum class RcOperationStatus {
  IMPLEMENTED,
  PARSE_ONLY,
  UNSUPPORTED,
  /** Public AndroidX constant with no usable reader in the authoritative Java player profile. */
  UNAVAILABLE,
  RESERVED,
  /**
   * Implemented here — codec and renderer both — while remaining [UNAVAILABLE] upstream.
   *
   * The two facts this column usually reports together come apart for these: what the CMP player
   * can execute, and what the authoritative Java registry can read. `DrawTextOnCircle` is the
   * standing case — writable under `WEAR_WIDGETS`, registered in no reader profile
   * (yschimke/wear-m3-catalog#321) — so it belongs in the CMP profiles and must stay out of
   * [RcOperationProfiles.ANDROIDX_JAVA_ALPHA16]. Marking it plain `implemented` would advertise it
   * to a producer targeting the Java player, which would then fail to read its own document.
   */
  IMPLEMENTED_UPSTREAM_UNAVAILABLE,
}

/** One checked-in AndroidX opcode inventory entry, generated from rc-operations.manifest. */
public data class RcOperationInventoryEntry(
  val opcode: Int,
  val constantName: String,
  val stableName: String,
  val cluster: Int,
  val status: RcOperationStatus,
)

/** An explicit opcode allow-list for producers selecting a compatible document subset. */
public data class RcOperationProfile(val name: String, val opcodes: Set<Int>) {
  public fun supports(opcode: Int): Boolean = opcode in opcodes
}

/**
 * The operation sets each player can execute, as published API.
 *
 * They are public rather than internal because a host has to tell `composeSupportReport` which
 * player it is targeting, and that is the only thing these exist for. #4064 asked whether to make
 * them internal before the first release; this is the answer.
 *
 * **`ALPHA16` names the registry generation, and these sets are not frozen at it.** Every profile
 * is computed from [RcOperationInventory], which is generated from the checked-in
 * `rc-operations.manifest`. Advancing that manifest — the normal way this stack tracks a new
 * AndroidX release — changes what `CMP_WASM_ALPHA16` and `CMP_IOS_ALPHA16` contain, because a newly
 * implemented operation joins `cmpImplementedOpcodes` below. The suffix records which AndroidX
 * registry the manifest currently tracks; it is not a promise that the membership is pinned at
 * today's contents.
 *
 * So: a consumer that needs an exact set pins the library version, which is the only thing that
 * actually fixes the manifest. And when the manifest does advance to a later AndroidX release,
 * these constants should be *renamed* to the new generation rather than silently kept — leaving
 * `ALPHA16` on a set that no longer describes alpha16 is the failure mode worth avoiding.
 */
public object RcOperationProfiles {
  /**
   * Both statuses that mean "this player executes the operation". They are two statuses and not one
   * because they differ on whether *AndroidX* can read it, which [ANDROIDX_JAVA_ALPHA16] cares
   * about and the CMP profiles do not.
   */
  private val cmpImplementedStatuses =
    setOf(
      RcOperationStatus.IMPLEMENTED,
      RcOperationStatus.IMPLEMENTED_UPSTREAM_UNAVAILABLE,
    )

  private val cmpImplementedOpcodes: Set<Int> =
    RcOperationInventory.entries
      .filter { it.status in cmpImplementedStatuses }
      .mapTo(linkedSetOf()) { it.opcode }

  /** Operations readable by the authoritative AndroidX alpha16 Java operation registry. */
  public val ANDROIDX_JAVA_ALPHA16: RcOperationProfile =
    RcOperationProfile(
      "androidx-java-alpha16",
      RcOperationInventory.entries
        .filter {
          // `IMPLEMENTED_UPSTREAM_UNAVAILABLE` is excluded for the same reason `UNAVAILABLE` is:
          // this profile answers "can the Java registry read it", and implementing it here does
          // not put a reader in that registry.
          it.status != RcOperationStatus.UNAVAILABLE &&
            it.status != RcOperationStatus.RESERVED &&
            it.status != RcOperationStatus.IMPLEMENTED_UPSTREAM_UNAVAILABLE
        }
        .mapTo(linkedSetOf()) { it.opcode },
    )

  /** Operations with executable semantics in the shared CMP renderer on iOS. */
  public val CMP_IOS_ALPHA16: RcOperationProfile =
    RcOperationProfile("cmp-ios-alpha16", cmpImplementedOpcodes)

  /** Operations with executable semantics in the browser, excluding backend-specific gaps. */
  public val CMP_WASM_ALPHA16: RcOperationProfile =
    RcOperationProfile(
      "cmp-wasm-alpha16",
      cmpImplementedOpcodes.filterTo(linkedSetOf()) {
        // Compose's current Wasm graphics-layer surface disappears when this modifier is
        // present. Keep it available to the shared/iOS renderer but never advertise it to
        // browser producers until that backend behavior is fixed.
        it != RcOpcodes.MODIFIER_GRAPHICS_LAYER
      },
    )
}

public data class RcDocumentSupport(val parseOnly: List<RcOperationInventoryEntry>) {
  public val fullyRenderable: Boolean
    get() = parseOnly.isEmpty()

  public fun requireFullyRenderable() {
    if (parseOnly.isNotEmpty()) {
      throw IllegalArgumentException(
        "Document contains parse-only operations: " +
          parseOnly.joinToString { "${it.stableName}(${it.opcode})" }
      )
    }
  }
}

/** Report semantic coverage separately from successful binary decoding. */
public fun RcDocument.supportReport(): RcDocumentSupport {
  val parseOnly =
    (listOf(header) + operations)
      .mapNotNull { RcOperationInventory.byOpcode[it.opcode] }
      .filter { it.status == RcOperationStatus.PARSE_ONLY }
      .distinctBy { it.opcode }
  return RcDocumentSupport(parseOnly)
}

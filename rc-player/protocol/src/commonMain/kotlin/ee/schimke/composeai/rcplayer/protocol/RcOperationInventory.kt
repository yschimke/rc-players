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
   * [RcOperationProfiles.ANDROIDX_JAVA_ALPHA18]. Marking it plain `implemented` would advertise it
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

/** Stable target keys for generated support and conformance reporting. */
public enum class RcOperationTarget {
  ANDROIDX_JAVA,
  ANDROIDX_EMBEDDED_EXPERIMENTAL,
  CMP_IOS,
  CMP_MACOS,
  CMP_DESKTOP,
  CMP_WASM,
}

/** One operation's advertised support result on one concrete player target. */
public data class RcOperationTargetSupport(
  val operation: RcOperationInventoryEntry,
  val target: RcOperationTarget,
  val supported: Boolean,
)

/**
 * The operation sets each player can execute, as published API.
 *
 * They are public rather than internal because a host has to tell `composeSupportReport` which
 * player it is targeting, and that is the only thing these exist for. #4064 asked whether to make
 * them internal before the first release; this is the answer.
 *
 * **`ALPHA18` names the registry generation, and these sets are not frozen at it.** Every profile
 * is computed from [RcOperationInventory], which is generated from the checked-in
 * `rc-operations.manifest`. Advancing that manifest — the normal way this stack tracks a new
 * AndroidX release — changes what `CMP_WASM_ALPHA18` and `CMP_IOS_ALPHA18` contain, because a newly
 * implemented operation joins `cmpImplementedOpcodes` below. The suffix records which AndroidX
 * registry the manifest currently tracks; it is not a promise that the membership is pinned at
 * today's contents.
 *
 * So: a consumer that needs an exact set pins the library version, which is the only thing that
 * actually fixes the manifest. And when the manifest does advance to a later AndroidX release,
 * these constants should be *renamed* to the new generation rather than silently kept — leaving
 * `ALPHA18` on a set that no longer describes alpha18 is the failure mode worth avoiding.
 */
public object RcOperationProfiles {
  private val androidxExperimentalOnlyOpcodes: Set<Int> =
    setOf(
      RcOpcodes.COMPONENT_START,
      RcOpcodes.DATA_SHADER,
      RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN,
      RcOpcodes.DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH,
      RcOpcodes.PLAY_SOUND,
      RcOpcodes.REFERENCED_OPERATIONS,
      RcOpcodes.PARTICLE_DEFINE,
      RcOpcodes.PARTICLE_LOOP,
      RcOpcodes.DATA_BITMAP_FONT,
      RcOpcodes.DATA_SOUND,
      RcOpcodes.BITMAP_TEXT_MEASURE,
      RcOpcodes.DRAW_BITMAP_TEXT_ANCHORED,
      RcOpcodes.DRAW_TO_BITMAP,
      RcOpcodes.PARTICLE_COMPARE,
      RcOpcodes.SOUND_EXPRESSION,
      RcOpcodes.SKIP,
      RcOpcodes.MACRO_FOR_EACH,
      RcOpcodes.INCLUDE_REFERENCED_OPERATIONS,
      RcOpcodes.MACRO_DEFINE,
      RcOpcodes.MACRO_CALL,
      RcOpcodes.MACRO_ARGUMENT,
      RcOpcodes.MACRO_BLOCK,
    )

  /**
   * Both statuses that mean "this player executes the operation". They are two statuses and not one
   * because they differ on whether *AndroidX* can read it, which [ANDROIDX_JAVA_ALPHA18] cares
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

  /** Operations readable by the authoritative AndroidX alpha18 Java operation registry. */
  public val ANDROIDX_JAVA_ALPHA18: RcOperationProfile =
    RcOperationProfile(
      "androidx-java-alpha18",
      RcOperationInventory.entries
        .filter {
          // `IMPLEMENTED_UPSTREAM_UNAVAILABLE` is excluded for the same reason `UNAVAILABLE` is:
          // this profile answers "can the Java registry read it", and implementing it here does
          // not put a reader in that registry.
          it.status != RcOperationStatus.UNAVAILABLE &&
            it.status != RcOperationStatus.RESERVED &&
            it.status != RcOperationStatus.IMPLEMENTED_UPSTREAM_UNAVAILABLE &&
            it.opcode !in androidxExperimentalOnlyOpcodes
        }
        .mapTo(linkedSetOf()) { it.opcode },
    )

  /** Operations readable by AndroidX's opt-in experimental embedded-player registry. */
  public val ANDROIDX_EMBEDDED_EXPERIMENTAL_ALPHA18: RcOperationProfile =
    RcOperationProfile(
      "androidx-embedded-experimental-alpha18",
      RcOperationInventory.entries
        .filter {
          it.status != RcOperationStatus.UNAVAILABLE &&
            it.status != RcOperationStatus.RESERVED &&
            it.status != RcOperationStatus.IMPLEMENTED_UPSTREAM_UNAVAILABLE
        }
        .mapTo(linkedSetOf()) { it.opcode },
    )

  /** Operations with executable semantics in the shared CMP renderer on iOS. */
  public val CMP_IOS_ALPHA18: RcOperationProfile =
    RcOperationProfile("cmp-ios-alpha18", cmpImplementedOpcodes)

  /** Operations with executable semantics in the native Apple-silicon macOS renderer. */
  public val CMP_MACOS_ALPHA18: RcOperationProfile =
    RcOperationProfile("cmp-macos-alpha18", cmpImplementedOpcodes)

  /** Operations with executable semantics in the Compose Desktop/JVM renderer. */
  public val CMP_DESKTOP_ALPHA18: RcOperationProfile =
    RcOperationProfile("cmp-desktop-alpha18", cmpImplementedOpcodes)

  /** Operations with executable semantics in the browser, excluding backend-specific gaps. */
  public val CMP_WASM_ALPHA18: RcOperationProfile =
    RcOperationProfile(
      "cmp-wasm-alpha18",
      cmpImplementedOpcodes.filterTo(linkedSetOf()) {
        // Compose's current Wasm graphics-layer surface disappears when this modifier is
        // present. Keep it available to the shared/iOS renderer but never advertise it to
        // browser producers until that backend behavior is fixed.
        it != RcOpcodes.MODIFIER_GRAPHICS_LAYER
      },
    )

  @Deprecated("Use ANDROIDX_JAVA_ALPHA18", ReplaceWith("ANDROIDX_JAVA_ALPHA18"))
  public val ANDROIDX_JAVA_ALPHA16: RcOperationProfile = ANDROIDX_JAVA_ALPHA18

  @Deprecated("Use CMP_IOS_ALPHA18", ReplaceWith("CMP_IOS_ALPHA18"))
  public val CMP_IOS_ALPHA16: RcOperationProfile = CMP_IOS_ALPHA18

  @Deprecated("Use CMP_MACOS_ALPHA18", ReplaceWith("CMP_MACOS_ALPHA18"))
  public val CMP_MACOS_ALPHA16: RcOperationProfile = CMP_MACOS_ALPHA18

  @Deprecated("Use CMP_DESKTOP_ALPHA18", ReplaceWith("CMP_DESKTOP_ALPHA18"))
  public val CMP_DESKTOP_ALPHA16: RcOperationProfile = CMP_DESKTOP_ALPHA18

  @Deprecated("Use CMP_WASM_ALPHA18", ReplaceWith("CMP_WASM_ALPHA18"))
  public val CMP_WASM_ALPHA16: RcOperationProfile = CMP_WASM_ALPHA18

  /** Every concrete target profile, keyed for machine-generated support reports. */
  public val byTarget: Map<RcOperationTarget, RcOperationProfile> =
    linkedMapOf(
      RcOperationTarget.ANDROIDX_JAVA to ANDROIDX_JAVA_ALPHA18,
      RcOperationTarget.ANDROIDX_EMBEDDED_EXPERIMENTAL to ANDROIDX_EMBEDDED_EXPERIMENTAL_ALPHA18,
      RcOperationTarget.CMP_IOS to CMP_IOS_ALPHA18,
      RcOperationTarget.CMP_MACOS to CMP_MACOS_ALPHA18,
      RcOperationTarget.CMP_DESKTOP to CMP_DESKTOP_ALPHA18,
      RcOperationTarget.CMP_WASM to CMP_WASM_ALPHA18,
    )
}

/** Complete operation-by-target support matrix derived from the checked-in opcode inventory. */
public object RcOperationSupportMatrix {
  public val entries: List<RcOperationTargetSupport> =
    RcOperationInventory.entries.flatMap { operation ->
      RcOperationProfiles.byTarget.map { (target, profile) ->
        RcOperationTargetSupport(operation, target, profile.supports(operation.opcode))
      }
    }

  public val byTarget: Map<RcOperationTarget, List<RcOperationTargetSupport>> = entries.groupBy {
    it.target
  }

  public fun entry(opcode: Int, target: RcOperationTarget): RcOperationTargetSupport =
    requireNotNull(byTarget.getValue(target).firstOrNull { it.operation.opcode == opcode }) {
      "No operation $opcode in ${target.name} support matrix"
    }
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

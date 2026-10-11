package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits

/**
 * The AndroidX profile bits a document declares in its header (`DOC_PROFILES`), and that select
 * which operations a player decodes. Mirrors AndroidX's `RcProfiles`.
 */
public object RcProfiles {
  /** The standard set for an API level, supported by every player at that level. */
  public const val BASELINE: Int = 0x0

  /** Additive: operations beyond the profile's own set that only some players support. */
  public const val EXPERIMENTAL: Int = 0x1

  /** Additive: operations removed from the baseline after some API level. */
  public const val DEPRECATED: Int = 0x2

  /** Additive: reserved for a specific OEM player by external agreement. */
  public const val OEM: Int = 0x4

  /** Additive: a subset of the baseline for low-power devices or states. */
  public const val LOW_POWER: Int = 0x8

  /** The Android platform (launcher widget) player's set. */
  public const val WIDGETS: Int = 0x100

  /** The AndroidX embedded player's set. */
  public const val ANDROIDX: Int = 0x200

  /** A non-public Android native player's set. */
  public const val ANDROID_NATIVE: Int = 0x400

  /** Wear widgets (Tiles): a selected subset of the baseline. */
  public const val WEAR_WIDGETS: Int = 0x800
}

/**
 * A player a document is meant for: the document API level it reads, the profile bits it decodes
 * with, and so the exact set of operations it accepts.
 *
 * The named profiles in [RcTargetProfiles] mirror AndroidX's creation profiles
 * (`RcPlatformProfiles`); [of] builds the profile any `(apiLevel, profiles)` pair selects, as
 * AndroidX's `Operations.getOperations` does.
 */
public class RcTargetProfile
internal constructor(
  /** A short human-readable name, used in violation messages. */
  public val name: String,
  /** The document API level the player reads: 6 is Android 16 (Baklava), 7 and 8 are AndroidX. */
  public val apiLevel: Int,
  /** The [RcProfiles] bits a document written for this player declares. */
  public val profiles: Int,
  /** Every opcode the player decodes. An operation outside this set fails the document. */
  public val supportedOpcodes: Set<Int>,
  /**
   * Whether the player decodes with [supportedOpcodes] whatever the document's header declares — an
   * allow-list like Wear's — rather than with the set the header's own level and bits select.
   */
  internal val fixedOperations: Boolean = false,
) {
  /**
   * Decodes [bytes] as this player would: a `Skip` guard is resolved against this profile's API
   * level and bits, so content a writer guarded for newer or other players is dropped here exactly
   * as the target drops it.
   */
  public fun decode(bytes: ByteArray, limits: RcWireLimits = RcWireLimits()): RcDocument =
    RcDocumentCodec.decode(bytes, limits, libraryApiLevel = apiLevel, profile = profiles)

  override fun toString(): String = "RcTargetProfile($name, apiLevel=$apiLevel, profiles=$profiles)"

  public companion object {
    /**
     * The profile a player built from AndroidX's own tables would apply to a document declaring
     * [apiLevel] and [profiles] — `Operations.getOperations(apiLevel, profiles)`.
     *
     * Several intersected profiles (`ANDROIDX or WIDGETS`) give the operations every one of them
     * supports, so a document valid for the result plays on each.
     */
    public fun of(apiLevel: Int, profiles: Int): RcTargetProfile =
      RcTargetProfile(
        name = "api $apiLevel, profiles 0x${profiles.toString(16)}",
        apiLevel = apiLevel,
        profiles = profiles,
        supportedOpcodes = operationsFor(apiLevel, profiles),
      )

    private fun operationsFor(apiLevel: Int, profiles: Int): Set<Int> {
      if (apiLevel <= 6) return BASE_OPERATIONS + V6_OPERATIONS
      val sets = buildList {
        if (profiles and RcProfiles.ANDROIDX != 0) {
          add(
            profileOperations(
              profiles,
              V7_ANDROIDX_OPERATIONS,
              V7_ANDROIDX_EXPERIMENTAL_OPERATIONS,
              V7_ANDROIDX_DEPRECATED_OPERATIONS,
            )
          )
        }
        if (profiles and RcProfiles.WIDGETS != 0) {
          add(
            profileOperations(
              profiles,
              V7_WIDGETS_OPERATIONS,
              V7_WIDGETS_EXPERIMENTAL_OPERATIONS,
              V7_WIDGETS_DEPRECATED_OPERATIONS,
            )
          )
        }
      }
      val shared = sets.reduceOrNull { left, right -> left intersect right }.orEmpty()
      return BASE_OPERATIONS + shared + V7_BASELINE_OPERATIONS
    }

    private fun profileOperations(
      profiles: Int,
      operations: Set<Int>,
      experimental: Set<Int>,
      deprecated: Set<Int>,
    ): Set<Int> = buildSet {
      addAll(operations)
      if (profiles and RcProfiles.EXPERIMENTAL != 0) addAll(experimental)
      if (profiles and RcProfiles.DEPRECATED != 0) addAll(deprecated)
    }
  }
}

/** The players AndroidX's creation API writes for, by the name it gives them. */
public object RcTargetProfiles {
  /**
   * Launcher widgets on Android 16 (Baklava): document API level 6, the platform player's original
   * set. AndroidX `RcPlatformProfiles.WIDGETS_V6`. Its writer also refuses dynamic image alpha,
   * dynamic text size and float-expression operators added after level 6; [validateFor] checks
   * those too.
   */
  public val WIDGETS_V6: RcTargetProfile =
    RcTargetProfile.of(6, RcProfiles.BASELINE).named("Launcher widgets (Android 16, API level 6)")

  /** Launcher widgets at document API level 7. AndroidX `RcPlatformProfiles.WIDGETS_V7`. */
  public val WIDGETS_V7: RcTargetProfile =
    RcTargetProfile.of(7, RcProfiles.WIDGETS).named("Launcher widgets (API level 7)")

  /** The AndroidX embedded player. AndroidX `RcPlatformProfiles.ANDROIDX`. */
  public val ANDROIDX: RcTargetProfile =
    RcTargetProfile.of(8, RcProfiles.ANDROIDX).named("AndroidX player")

  /**
   * The AndroidX embedded player with its experimental operations — macros, custom layouts, sound.
   * The set the CMP player itself decodes.
   */
  public val ANDROIDX_EXPERIMENTAL: RcTargetProfile =
    RcTargetProfile.of(8, RcProfiles.ANDROIDX or RcProfiles.EXPERIMENTAL)
      .named("AndroidX player, experimental")

  /**
   * Wear OS widgets (Tiles): document API level 8 and an explicit allow-list, a subset of the
   * baseline without `CoreText`. AndroidX `RcPlatformProfiles.WEAR_WIDGETS`.
   */
  public val WEAR_WIDGETS: RcTargetProfile =
    RcTargetProfile(
      name = "Wear widgets",
      apiLevel = 8,
      profiles = RcProfiles.WEAR_WIDGETS,
      supportedOpcodes = WEAR_WIDGETS_OPERATIONS,
      fixedOperations = true,
    )

  private fun RcTargetProfile.named(name: String): RcTargetProfile =
    RcTargetProfile(name, apiLevel, profiles, supportedOpcodes, fixedOperations)
}

/**
 * The document API level this header declares, as AndroidX reads it from the version (`1.2` is 8,
 * `1.1` is 7, `1.0` is 6), or null for a version no AndroidX player reads.
 */
public val RcHeader.apiLevel: Int?
  get() =
    when {
      version.major == 1 && version.minor == 2 -> 8
      version.major == 1 && version.minor == 1 -> 7
      version.major == 1 && version.minor == 0 -> 6
      version.major == 0 && version.minor <= 3 -> 6
      else -> null
    }

/** The [RcProfiles] bits this header declares; a pre-level-7 header has none. */
public val RcHeader.profiles: Int
  get() =
    properties
      .lastOrNull { it.key == DOC_PROFILES }
      ?.let { (it.value as? RcHeaderValue.IntValue)?.value } ?: RcProfiles.BASELINE

/** AndroidX `Header.DOC_PROFILES`. */
internal const val DOC_PROFILES: Int = 14

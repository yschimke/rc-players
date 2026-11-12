package ee.schimke.composeai.rccreation.common

import ee.schimke.composeai.rcplayer.protocol.RcProfileOperations

// KMP (rc-players): AndroidX's capture profile, in Kotlin. Upstream's is Java, in three modules:
// `remote-core` (`RcProfiles`, `Header`, `Operations`), `remote-creation-core` (`Profile`,
// `RemoteComposeWriterFactory`, `CreationDisplayInfo`, `RemoteComposeWriter.HTag`) and
// `remote-creation`'s `androidMain` (`RcPlatformProfiles`). Each is declared here under its
// AndroidX name, with what AndroidX's has but for `RcPlatformServices`: the platform's image,
// path and text services, which this library reaches another way (a capture's image encoder, a
// pure-Kotlin path).

/** The operation profiles a document declares in its header, as AndroidX's `RcProfiles`. */
public object RcProfiles {
  /**
   * Baseline profile: for a given version, the standard set of operations, supported by players
   * without prior arrangement.
   */
  public const val PROFILE_BASELINE: Int = 0x0

  /** Experimental profile: additional operations only some players support. Additive. */
  public const val PROFILE_EXPERIMENTAL: Int = 0x1

  /** Deprecated profile: operations removed from the baseline after some version. Additive. */
  public const val PROFILE_DEPRECATED: Int = 0x2

  /** OEM profile: reserved for a specific player, by external agreement. Additive. */
  public const val PROFILE_OEM: Int = 0x4

  /** Low power profile: a subset of the baseline for low-power devices or states. Additive. */
  public const val PROFILE_LOW_POWER: Int = 0x8

  /** Launcher widget profile: what the Android platform player supports. */
  public const val PROFILE_WIDGETS: Int = 0x100

  /** AndroidX profile: what the AndroidX player supports. */
  public const val PROFILE_ANDROIDX: Int = 0x200

  /** Android native profile: what a non-public Android native player supports. */
  public const val PROFILE_ANDROID_NATIVE: Int = 0x400

  /** Wear widgets (Tiles) profile: a selected set of baseline operations. */
  public const val PROFILE_WEAR_WIDGETS: Int = 0x800
}

/** The header's property tags and their values, as AndroidX's `Header` names them. */
public object Header {
  /** The document's width, an int. */
  public const val DOC_WIDTH: Short = 5

  /** The document's height, an int. */
  public const val DOC_HEIGHT: Short = 6

  /** The density the document was generated at, a float. */
  public const val DOC_DENSITY_AT_GENERATION: Short = 7

  /** The frame rate the document asks for, a float. */
  public const val DOC_DESIRED_FPS: Short = 8

  /** The document's content description, a string. */
  public const val DOC_CONTENT_DESCRIPTION: Short = 9

  /** The document's source, a string. */
  public const val DOC_SOURCE: Short = 11

  /** Whether the document is a data update, an int. */
  public const val DOC_DATA_UPDATE: Short = 12

  /** The host's exception handler, an int. */
  public const val HOST_EXCEPTION_HANDLER: Short = 13

  /** The [RcProfiles] bits the document's operations are read under, an int. */
  public const val DOC_PROFILES: Short = 14

  /** Whether paint measures text (1, the default) or the layout does (0), an int. */
  public const val FEATURE_PAINT_MEASURE: Short = 15

  /** A debug flag, an int. */
  public const val DEBUG: Short = 16

  /** The measure pass version, an int. */
  public const val FEATURE_MEASURE_VERSION: Short = 17

  /** The touch handling version, an int. */
  public const val FEATURE_TOUCH_VERSION: Short = 18

  /** A fixed time for tests, a long. */
  public const val TEST_TIME: Short = 19

  /** What a test does after loading, an int. */
  public const val TEST_AFTER: Short = 20

  /** The colour theme a test plays under, an int. */
  public const val TEST_COLOR_THEME: Short = 21

  /** The actions a test runs, a string. */
  public const val TEST_ACTIONS: Short = 22

  /** Whether the collapsible priority fix applies, an int. */
  public const val FEATURE_PRIORITY_FIX: Short = 23

  /** Whether the layout resizes on a smaller measure, an int. */
  public const val FEATURE_LT_RESIZE: Short = 24

  /** Whether arrays notify their listeners, an int. */
  public const val FEATURE_ARRAY_LISTENERS: Short = 25

  /** The click handling version, an int. */
  public const val FEATURE_CLICK_VERSION: Short = 26

  /** How density-sensitive fields are read (`CoreDocument.DENSITY_BEHAVIOR_`), an int. */
  public const val DOC_DENSITY_BEHAVIOR: Short = 27

  /** The player's optimization level, an int. */
  public const val FEATURE_OPTIMIZATION_LEVEL: Short = 28

  /**
   * Whether a player with a touch expression keeps its parent from intercepting touches (1, the
   * default) or leaves them to it (0), an int.
   */
  public const val FEATURE_DISALLOW_INTERCEPT_TOUCH: Short = 29

  /** Whether the data pass runs a canvas's operations, an int. */
  public const val FEATURE_DATA_PASS_CANVAS_OPS: Short = 30

  /** How the operations after the header are compressed ([COMPRESSION_NONE], ...), an int. */
  public const val COMPRESS: Short = 31

  /** The directions the root scrolls in ([SCROLL_HORIZONTAL], [SCROLL_VERTICAL]), an int. */
  public const val DOC_SCROLL: Short = 32

  public const val COMPRESSION_NONE: Int = 0
  public const val COMPRESSION_DEFLATE: Int = 1
  public const val SCROLL_HORIZONTAL: Int = 1
  public const val SCROLL_VERTICAL: Int = 2
}

/**
 * One header property, as `RemoteComposeWriter.HTag`: a [Header] tag and its value, an `Int`, a
 * `Float`, a `Long` or a `String`.
 */
public class HTag(public val tag: Short, public val value: Any) {
  init {
    require(value is Int || value is Float || value is Long || value is String) {
      "Unsupported property type: ${value::class.simpleName}"
    }
  }

  override fun equals(other: Any?): Boolean =
    other is HTag && tag == other.tag && value == other.value

  override fun hashCode(): Int = 31 * tag + value.hashCode()

  override fun toString(): String = "HTag($tag, $value)"
}

/** The display a document is created for, as AndroidX's `CreationDisplayInfo`. */
public class CreationDisplayInfo(
  public val width: Int,
  public val height: Int,
  public val densityDpi: Int,
  public val densityBehavior: Int = CoreDocument.DENSITY_BEHAVIOR_LEGACY,
) {
  /** The density as a scale factor, 160 dpi being 1. */
  public val density: Float
    get() = densityDpi / 160f
}

/** Makes the writer a [Profile] writes documents with, as AndroidX's interface of that name. */
public fun interface RemoteComposeWriterFactory {
  /**
   * A writer for a document of [creationDisplayInfo] under [profile]. [writerCallback] is the
   * capture's out-of-band callback, if it has one.
   */
  public fun create(
    creationDisplayInfo: CreationDisplayInfo,
    profile: Profile,
    writerCallback: Any?,
  ): RemoteComposeWriter
}

/**
 * A capture profile, as AndroidX's `Profile`: who a document is written for. It holds
 * - the document API level and the [RcProfiles] bits, which the header declares and which, with
 *   [supportedOperations], say which operations the document may hold;
 * - the factory of the writer, which says how the header is written (its feature tags).
 *
 * A writer made for a profile refuses an operation outside [supportedOperations], and takes the
 * fallback AndroidX's writer takes where it has one (text as a `TextLayout` when `CORE_TEXT` is not
 * supported). What a component asks before it writes (`currentProfileSupports…`) is answered from
 * the same set.
 *
 * KMP (rc-players): no `RcPlatformServices`, which AndroidX's profile also carries.
 */
public class Profile(
  /** The API level of the document's operations. */
  public val apiLevel: Int,
  /** The [RcProfiles] bits: with [apiLevel], the valid set of operations. */
  public val operationsProfiles: Int,
  supportedOperationsProvider: SupportedOperationsProvider?,
  /** What makes the writer; see [create]. */
  public val profileFactory: RemoteComposeWriterFactory,
) {
  /** A profile whose operations are AndroidX's for [apiLevel] and [operationsProfiles]. */
  public constructor(
    apiLevel: Int,
    operationsProfiles: Int,
    profileFactory: RemoteComposeWriterFactory,
  ) : this(apiLevel, operationsProfiles, null, profileFactory)

  private val supportedOperationsProvider: SupportedOperationsProvider =
    supportedOperationsProvider
      ?: SupportedOperationsProvider {
        checkNotNull(Operations.getOperations(apiLevel, operationsProfiles)) {
          "No supported operations defined"
        }
      }

  /** The operations a document written under this profile may hold. */
  public val supportedOperations: Set<Int>
    get() = supportedOperationsProvider.getSupportedOperations()

  /** A writer for a document of [creationDisplayInfo] under this profile. */
  public fun create(
    creationDisplayInfo: CreationDisplayInfo,
    writerCallback: Any?,
  ): RemoteComposeWriter = profileFactory.create(creationDisplayInfo, this, writerCallback)

  /** Provides a profile's set of supported operations. */
  public fun interface SupportedOperationsProvider {
    public fun getSupportedOperations(): Set<Int>
  }

  override fun toString(): String =
    "Profile(apiLevel=$apiLevel, operationsProfiles=0x${operationsProfiles.toString(16)})"
}

/** The profiles AndroidX's creation API names, as its `RcPlatformProfiles`. */
public object RcPlatformProfiles {
  private val writer = RemoteComposeWriterFactory { creationDisplayInfo, profile, _ ->
    RemoteDocumentWriter(creationDisplayInfo, null, profile)
  }

  /**
   * Launcher widgets on Android 16: document API level 6, whose header is the legacy one and
   * declares no profile.
   *
   * KMP (rc-players): AndroidX's `WidgetsProfileWriterV6` also refuses what its writer could
   * otherwise write in a level 6 operation's fields (an image alpha or a text size that is a
   * variable, a float expression operator added later); this writer does not check those, and
   * `:rc-player-edit`'s `validateFor(RcTargetProfiles.WIDGETS_V6)` reports them.
   */
  public val WIDGETS_V6: Profile = Profile(6, RcProfiles.PROFILE_BASELINE, writer)

  /** Launcher widgets at document API level 7. */
  public val WIDGETS_V7: Profile = Profile(7, RcProfiles.PROFILE_WIDGETS, writer)

  /** The AndroidX player, at document API level 7. */
  public val ANDROIDX7: Profile = Profile(7, RcProfiles.PROFILE_ANDROIDX, writer)

  /** The AndroidX player, at document API level 8. */
  public val ANDROIDX8: Profile = Profile(8, RcProfiles.PROFILE_ANDROIDX, writer)

  /** The AndroidX player, at document API level 9. */
  public val ANDROIDX9: Profile = Profile(9, RcProfiles.PROFILE_ANDROIDX, writer)

  /** The AndroidX player, at the current document API level. */
  public val ANDROIDX: Profile =
    Profile(CoreDocument.DOCUMENT_API_LEVEL, RcProfiles.PROFILE_ANDROIDX, writer)
}

internal fun operationsOf(apiLevel: Int, profiles: Int): Set<Int> =
  RcProfileOperations.of(apiLevel, profiles)

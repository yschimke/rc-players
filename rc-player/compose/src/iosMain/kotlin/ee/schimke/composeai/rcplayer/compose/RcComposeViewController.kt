package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcOperationProfiles
import ee.schimke.composeai.rcplayer.runtime.RcDocumentCapabilities
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import ee.schimke.composeai.rcplayer.runtime.fitsWireType
import platform.Foundation.NSBundle
import platform.Foundation.NSLog
import platform.Foundation.NSNumber
import platform.UIKit.UIViewController

/** Live named-variable state owned by an Apple host. */
public class RcComposePlayerController {
  private val inputDrafts = ee.schimke.composeai.rcplayer.runtime.RcInputDrafts()
  internal var inputComponents: RcCustomComponentRegistry = rcInputComponents(inputDrafts)
    private set

  /** Configure before attaching a controller. Old hosts retain read-only input fallbacks. */
  public fun configureInputs(
    editable: Boolean,
    onChange: (String, String) -> Unit,
    onSubmit: (String, Map<String, String>) -> Unit,
  ) {
    inputComponents = rcInputComponents(inputDrafts, editable, onChange, onSubmit)
  }

  internal val values = mutableStateMapOf<String, RcNamedValue>()
  private var types: Map<String, Int> = emptyMap()

  /** Names declared by the document currently attached to the player. */
  public val names: List<String>
    get() = types.keys.sorted()

  /** Sets a float variable, returning false when [name] is absent or has a different type. */
  public fun setFloat(name: String, value: Float): Boolean =
    set(name, RcNamedValue.FloatValue(value))

  /** Sets a string variable, returning false when [name] is absent or has a different type. */
  public fun setString(name: String, value: String): Boolean = set(name, RcNamedValue.Text(value))

  /**
   * Sets an ARGB colour variable, returning false when [name] is absent or has a different type.
   */
  public fun setColor(name: String, argb: Int): Boolean = set(name, RcNamedValue.Color(argb))

  /** Sets an integer variable, returning false when [name] is absent or has a different type. */
  public fun setInteger(name: String, value: Int): Boolean = set(name, RcNamedValue.Integer(value))

  /**
   * Sets a boolean variable, returning false when [name] is absent or has a different type.
   *
   * A Remote Compose boolean is an integer variable holding 1 or 0, as AndroidX's
   * `setNamedBooleanOverride` writes it, so [name] must be declared with the integer type.
   */
  public fun setBoolean(name: String, value: Boolean): Boolean =
    set(name, RcNamedValue.BooleanValue(value))

  /**
   * Sets a long variable — an instant in epoch milliseconds, as a document's time attributes read
   * one — returning false when [name] is absent or has a different type.
   */
  public fun setLong(name: String, value: Long): Boolean = set(name, RcNamedValue.LongValue(value))

  /**
   * The most values [setFloatArray] takes ([RcFloatList.MAX_VALUES]), so a Swift host can refuse a
   * larger array before it copies it into a `KotlinFloatArray` one element at a time.
   */
  public val maxFloatArrayValues: Int
    get() = RcFloatList.MAX_VALUES

  /**
   * Replaces the list a float-array variable holds, returning false when [name] is absent or has a
   * different type, when a value is not finite, or when there are more than a float list holds
   * ([RcFloatList.MAX_VALUES]) — the native Swift player refuses the same arrays. The length is
   * checked before the array is copied; finiteness is checked on the copy.
   */
  public fun setFloatArray(name: String, values: FloatArray): Boolean {
    // Counted before it is copied: a host's array can be far larger than a float list holds.
    if (values.size > RcFloatList.MAX_VALUES) return false
    val list = values.toList()
    if (!RcFloatList.canHold(list)) return false
    return set(name, RcNamedValue.FloatArrayValue(list))
  }

  /**
   * Stops overriding [name]: the value set for it is removed, and the player restores the value the
   * document recorded (`RcPlayerState.clearNamedValue`). Returns false when the attached document
   * does not declare [name]; clearing a name with no value set changes nothing.
   */
  public fun clear(name: String): Boolean {
    val declaredName = declaredName(name) ?: return false
    values.remove(declaredName)
    return true
  }

  /**
   * Attaches a new document's declarations. Each stored value is re-keyed under the name the new
   * document resolves its key to — the resolution `RcPlayerState` will apply — and dropped when
   * that variable is undeclared or of another type. Checking the key as written instead let a bare
   * `count` stored for one document reach a new document's `USER:count` of another type, which
   * `RcPlayerState` rejects by throwing.
   */
  internal fun attach(capabilities: RcDocumentCapabilities) {
    types = capabilities.namedValues
    for ((key, value) in values.toMap()) {
      val resolved = declaredName(key)
      if (resolved == null || !value.fitsWireType(types[resolved])) {
        values.remove(key)
      } else if (resolved != key) {
        values.remove(key)
        values[resolved] = value
      }
    }
  }

  private fun set(name: String, value: RcNamedValue): Boolean {
    val declaredName = declaredName(name) ?: return false
    if (!value.fitsWireType(types[declaredName])) return false
    values[declaredName] = value
    return true
  }

  /**
   * The declared name [name] addresses, resolved as `RcPlayerState` resolves a host name: a name
   * with a namespace as written, otherwise the same name in `USER:` when the document declares it
   * there, otherwise the bare name. Null when the document declares none of them.
   */
  private fun declaredName(name: String): String? {
    val resolved =
      when {
        ':' in name -> name
        "USER:$name" in types -> "USER:$name"
        else -> name
      }
    return resolved.takeIf { it in types }
  }
}

private var didWarnAboutHighRefreshRatePlist = false

private fun warnIfHighRefreshRatePlistEntryIsMissing() {
  val isEnabled =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CADisableMinimumFrameDurationOnPhone")
        as? NSNumber)
      ?.boolValue == true
  if (!didWarnAboutHighRefreshRatePlist && !isEnabled) {
    didWarnAboutHighRefreshRatePlist = true
    NSLog(
      "Remote Compose: CADisableMinimumFrameDurationOnPhone is not YES; " +
        "continuing with the host application's refresh-rate configuration.",
    )
  }
}

/**
 * Thin UIKit host for the common CMP player. The `.rc` bytes remain owned by the caller.
 *
 * The default was the wire constant `RcTheme.UNSPECIFIED` while the Compose entry point defaulted
 * to `RcTheme.SYSTEM`; both now spell the same thing as [RcPlayerTheme.System], which is what they
 * always resolved to — `rcResolveSystemTheme` answers both from `isSystemInDarkTheme()`.
 * `RcPlayerThemeRenderTest` asserts the three spellings resolve identically inside a real
 * composition rather than trusting that reading.
 *
 * **From Swift this is a file-facade call, not a global function.** Kotlin/Native exports top-level
 * declarations as static members of a class named after their file, so a Swift consumer writes
 * `RcComposeViewControllerKt.RcComposeViewController(bytes:theme:onEvent:typefaces:onError:)` —
 * with all five arguments, since Objective-C has no defaults — and has to copy its `Data` into a
 * `KotlinByteArray` by hand. docs/design/RC_PLAYER_SWIFT.md writes both out. The framework ships as
 * `RcComposePlayer.xcframework.zip` on each GitHub Release, addressed by `Package.swift` (#4068);
 * Kotlin Multiplatform consumers reach the same function through the published iOS klibs.
 */
public fun RcComposeViewController(
  bytes: ByteArray,
  theme: RcPlayerTheme = RcPlayerTheme.System,
  onEvent: (RcPlayerEvent) -> Unit = {},
  typefaces: RcTypefaceLoader = RcAppleTypefaceLoader,
  onError: (String) -> Unit = {},
): UIViewController = RcComposeViewController(bytes, theme, onEvent, typefaces, onError, false)

/**
 * [RcComposeViewController] with the playback gate selectable.
 *
 * `lenient = true` plays a document carrying any operation the player *knows*, drawing nothing for
 * the ones this backend has no branch for instead of refusing the whole document — see
 * [RcComposeSupportReport.requirePlayable]. Malformed data, an undeclared id or a missing typeface
 * still fail: those would throw from inside the draw pass, and no mode can play them.
 *
 * It is a separate overload rather than a defaulted parameter because Kotlin/Native does not export
 * default arguments — adding one would rewrite the five-argument Objective-C selector every Swift
 * consumer is calling today. Swift sees two selectors instead, the existing one unchanged and
 * `RcComposeViewController(bytes:theme:onEvent:typefaces:onError:lenient:)` beside it.
 */
public fun RcComposeViewController(
  bytes: ByteArray,
  theme: RcPlayerTheme,
  onEvent: (RcPlayerEvent) -> Unit,
  typefaces: RcTypefaceLoader,
  onError: (String) -> Unit,
  lenient: Boolean,
): UIViewController =
  RcComposeViewController(
    bytes,
    theme,
    onEvent,
    typefaces,
    onError,
    lenient,
    opaque = true,
    soundHost = RcSoundHost.None,
  )

/**
 * [RcComposeViewController] with the renderer background selectable.
 *
 * `opaque = false` preserves alpha in the Compose Metal surface so content behind the controller
 * can remain visible wherever the Remote Compose document does not draw. The containing UIKit views
 * must also be transparent; the SwiftUI source overlay handles that for Swift consumers.
 *
 * This is another overload so the existing five- and six-argument Objective-C selectors remain
 * source and binary compatible.
 */
@OptIn(ExperimentalComposeUiApi::class)
public fun RcComposeViewController(
  bytes: ByteArray,
  theme: RcPlayerTheme,
  onEvent: (RcPlayerEvent) -> Unit,
  typefaces: RcTypefaceLoader,
  onError: (String) -> Unit,
  lenient: Boolean,
  opaque: Boolean,
): UIViewController {
  return RcComposeViewController(
    bytes,
    theme,
    onEvent,
    typefaces,
    onError,
    lenient,
    opaque,
    RcSoundHost.None,
  )
}

/** [RcComposeViewController] with an opt-in, host-owned Apple audio implementation. */
public fun RcComposeViewController(
  bytes: ByteArray,
  theme: RcPlayerTheme,
  onEvent: (RcPlayerEvent) -> Unit,
  typefaces: RcTypefaceLoader,
  onError: (String) -> Unit,
  lenient: Boolean,
  soundHost: RcSoundHost,
): UIViewController =
  RcComposeViewController(
    bytes,
    theme,
    onEvent,
    typefaces,
    onError,
    lenient,
    opaque = true,
    soundHost,
  )

/** [RcComposeViewController] with both background and opt-in audio behavior selectable. */
@OptIn(ExperimentalComposeUiApi::class)
public fun RcComposeViewController(
  bytes: ByteArray,
  theme: RcPlayerTheme,
  onEvent: (RcPlayerEvent) -> Unit,
  typefaces: RcTypefaceLoader,
  onError: (String) -> Unit,
  lenient: Boolean,
  opaque: Boolean,
  soundHost: RcSoundHost,
): UIViewController {
  return createRcComposeViewController(
    bytes,
    theme,
    onEvent,
    typefaces,
    onError,
    lenient,
    opaque,
    soundHost,
    controller = null,
  )
}

/** [RcComposeViewController] with live named-variable state owned by [controller]. */
@OptIn(ExperimentalComposeUiApi::class)
public fun RcComposeViewController(
  bytes: ByteArray,
  theme: RcPlayerTheme,
  onEvent: (RcPlayerEvent) -> Unit,
  typefaces: RcTypefaceLoader,
  onError: (String) -> Unit,
  lenient: Boolean,
  opaque: Boolean,
  soundHost: RcSoundHost,
  controller: RcComposePlayerController,
): UIViewController =
  createRcComposeViewController(
    bytes,
    theme,
    onEvent,
    typefaces,
    onError,
    lenient,
    opaque,
    soundHost,
    controller,
  )

/** [RcComposeViewController] with an overridable external-image loader for Swift hosts. */
@OptIn(ExperimentalComposeUiApi::class)
public fun RcComposeViewController(
  bytes: ByteArray,
  theme: RcPlayerTheme,
  onEvent: (RcPlayerEvent) -> Unit,
  typefaces: RcTypefaceLoader,
  onError: (String) -> Unit,
  lenient: Boolean,
  opaque: Boolean,
  soundHost: RcSoundHost,
  controller: RcComposePlayerController,
  imageLoader: RcImageLoader,
): UIViewController =
  createRcComposeViewController(
    bytes,
    theme,
    onEvent,
    typefaces,
    onError,
    lenient,
    opaque,
    soundHost,
    controller,
    imageLoader,
  )

@OptIn(ExperimentalComposeUiApi::class)
private fun createRcComposeViewController(
  bytes: ByteArray,
  theme: RcPlayerTheme,
  onEvent: (RcPlayerEvent) -> Unit,
  typefaces: RcTypefaceLoader,
  onError: (String) -> Unit,
  lenient: Boolean,
  opaque: Boolean,
  soundHost: RcSoundHost,
  controller: RcComposePlayerController?,
  imageLoader: RcImageLoader = RcImageLoader.Default,
): UIViewController {
  val inputs =
    controller?.inputComponents
      ?: rcInputComponents(ee.schimke.composeai.rcplayer.runtime.RcInputDrafts())
  warnIfHighRefreshRatePlistEntryIsMissing()
  val document = runCatching {
    decodeCmpDocument(bytes).also {
      it
        .composeSupportReport(
          RcOperationProfiles.CMP_IOS_ALPHA18,
          availableFontFamilies = typefaces.families,
          availableCustomComponents = inputs.names,
          allowExternalImagePlaceholders = imageLoader !== RcImageLoader.Empty,
        )
        .requireRenderable(lenient)
    }
  }
    .getOrElse {
      controller?.attach(RcDocumentCapabilities(emptyMap(), emptySet()))
      onError(it.message ?: "Remote Compose document failed to load")
      return ComposeUIViewController(
        configure = {
          this.opaque = opaque
          enforceStrictPlistSanityCheck = false
        },
      ) {}
    }
  controller?.attach(RcDocumentCapabilities.of(document))
  return ComposeUIViewController(
    configure = {
      this.opaque = opaque
      enforceStrictPlistSanityCheck = false
    },
  ) {
    CompositionLocalProvider(
      LocalRcSoundHost provides soundHost,
      LocalRcImageLoader provides imageLoader,
    ) {
      RcComposePlayer(
        document,
        Modifier.fillMaxSize(),
        theme,
        namedValues = controller?.values ?: rememberRcNamedValues(),
        onEvent = { event -> forwardIosPlayerEvent(onEvent, event) },
        typefaces = typefaces,
        customComponents = inputs,
      )
    }
  }
}

internal fun forwardIosPlayerEvent(onEvent: (RcPlayerEvent) -> Unit, event: RcPlayerEvent) {
  onEvent(event)
}

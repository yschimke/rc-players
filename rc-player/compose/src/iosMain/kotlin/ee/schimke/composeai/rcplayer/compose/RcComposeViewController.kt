package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOperationProfiles
import ee.schimke.composeai.rcplayer.runtime.RcDocumentCapabilities
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import platform.Foundation.NSBundle
import platform.Foundation.NSLog
import platform.Foundation.NSNumber
import platform.UIKit.UIViewController

/** Live named-variable state owned by an Apple host. */
public class RcComposePlayerController {
  internal val values = mutableStateMapOf<String, RcNamedValue>()
  private var types: Map<String, Int> = emptyMap()

  /** Names declared by the document currently attached to the player. */
  public val names: List<String>
    get() = types.keys.sorted()

  /** Sets a float variable, returning false when [name] is absent or has a different type. */
  public fun setFloat(name: String, value: Float): Boolean =
    set(name, RcNamedVariable.FLOAT_TYPE, RcNamedValue.FloatValue(value))

  /** Sets a string variable, returning false when [name] is absent or has a different type. */
  public fun setString(name: String, value: String): Boolean =
    set(name, RcNamedVariable.STRING_TYPE, RcNamedValue.Text(value))

  /**
   * Sets an ARGB colour variable, returning false when [name] is absent or has a different type.
   */
  public fun setColor(name: String, argb: Int): Boolean =
    set(name, RcNamedVariable.COLOR_TYPE, RcNamedValue.Color(argb))

  internal fun attach(capabilities: RcDocumentCapabilities) {
    types = capabilities.namedValues
    values.keys.retainAll(types.keys)
  }

  private fun set(name: String, expectedType: Int, value: RcNamedValue): Boolean {
    val qualifiedName = if (name.contains(':')) name else "USER:$name"
    if (types[qualifiedName] != expectedType) return false
    values[qualifiedName] = value
    return true
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
        "continuing with the host application's refresh-rate configuration."
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
  typefaces: RcTypefaceLoader = RcTypefaceLoader.Default,
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
): UIViewController {
  warnIfHighRefreshRatePlistEntryIsMissing()
  val document = runCatching {
    decodeCmpDocument(bytes).also {
      it
        .composeSupportReport(
          RcOperationProfiles.CMP_IOS_ALPHA18,
          availableFontFamilies = typefaces.families,
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
        }
      ) {}
    }
  controller?.attach(RcDocumentCapabilities.of(document))
  return ComposeUIViewController(
    configure = {
      this.opaque = opaque
      enforceStrictPlistSanityCheck = false
    }
  ) {
    CompositionLocalProvider(LocalRcSoundHost provides soundHost) {
      RcComposePlayer(
        document,
        Modifier.fillMaxSize(),
        theme,
        namedValues = controller?.values ?: rememberRcNamedValues(),
        onEvent = { event -> forwardIosPlayerEvent(onEvent, event) },
        typefaces = typefaces,
      )
    }
  }
}

internal fun forwardIosPlayerEvent(onEvent: (RcPlayerEvent) -> Unit, event: RcPlayerEvent) {
  onEvent(event)
}

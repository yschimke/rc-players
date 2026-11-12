@file:kotlin.jvm.JvmName("CaptureRemoteDocumentKt")
@file:kotlin.jvm.JvmMultifileClass

package ee.schimke.composeai.rccreation.compose.capture

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ee.schimke.composeai.rccreation.common.Profile
import ee.schimke.composeai.rccreation.common.RcPlatformProfiles
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.common.RemoteWriter
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposeApplier
import ee.schimke.composeai.rccreation.compose.layout.RemoteRootNode
import ee.schimke.composeai.rccreation.compose.layout.toRemoteLayoutDirection
import ee.schimke.composeai.rccreation.compose.text.platformUses24HourTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Captures one platform-neutral Remote Compose document through the common write-only encoder.
 *
 * @param profile who the document is written for: its API level and operations, and the header its
 *   writer writes. The writer refuses an operation the profile does not support and takes
 *   AndroidX's fallback where it has one, and `currentProfileSupports…` answers from the same set.
 *   Its factory has to make a [RemoteDocumentWriter], as [RcPlatformProfiles]' and [Profile]'s do.
 * @param encodePng how this platform turns an [ImageBitmap] into PNG bytes. Given, every bitmap the
 *   content draws is carried inline in the document; absent, bitmaps are dropped, as they always
 *   were here, because common code has no image encoder of its own.
 */
public suspend fun captureCommonRemoteDocument(
    creationDisplayInfo: RemoteCreationDisplayInfo,
    remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    profile: Profile = RcPlatformProfiles.ANDROIDX,
    encodePng: ((ImageBitmap) -> ByteArray?)? = null,
    content: @Composable @RemoteComposable () -> Unit,
): ByteArray {
    // TODO(upstream): with RemoteDensityBehavior.Dp the header declares dp, but this passes
    // `size` (pixels) unscaled, as AndroidX's `toCreationDisplayInfo()` does. Fix it upstream and
    // take it on the next refresh; see PROVENANCE.md, "Upstream follow-ups".
    val writer = profile.create(creationDisplayInfo.toCreationDisplayInfo(), null)
    check(writer is RemoteDocumentWriter) {
        "A profile captured to bytes has to make a RemoteDocumentWriter; " +
            "use captureCommonRemoteDocumentInto for another writer"
    }
    captureCommonRemoteDocumentInto(
        writer = writer,
        creationDisplayInfo = creationDisplayInfo,
        remoteDensity = remoteDensity,
        layoutDirection = layoutDirection,
        profile = profile,
        platformImageProvider =
            encodePng?.let { InlinePngImageProvider(writer, it) } ?: NoOpPlatformImageProvider,
        content = content,
    )
    return writer.encodeToByteArray()
}

/**
 * LOCAL PATCH (rc-players): the capture of rc-creation-compose 3.0.0, from before the fork had
 * [Profile]. [supportedOperations] answers what a component asks (`currentProfileSupports…`); the
 * writer writes whatever it is asked to and declares no profile but what its operations need.
 */
@Deprecated(
    "Pass a Profile: it also restricts the writer and writes the header.",
    ReplaceWith(
        "captureCommonRemoteDocument(creationDisplayInfo, remoteDensity, layoutDirection, " +
            "Profile(supportedOperations = supportedOperations), encodePng, content)",
    ),
)
public suspend fun captureCommonRemoteDocument(
    creationDisplayInfo: RemoteCreationDisplayInfo,
    remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    supportedOperations: Set<Int>,
    encodePng: ((ImageBitmap) -> ByteArray?)? = null,
    content: @Composable @RemoteComposable () -> Unit,
): ByteArray {
    val writer =
        RemoteDocumentWriter(
            creationDisplayInfo.width,
            creationDisplayInfo.height,
            creationDisplayInfo.densityBehavior.value,
        )
    captureCommonRemoteDocumentInto(
        writer = writer,
        creationDisplayInfo = creationDisplayInfo,
        remoteDensity = remoteDensity,
        layoutDirection = layoutDirection,
        profile = legacyProfile(supportedOperations),
        platformImageProvider =
            encodePng?.let { InlinePngImageProvider(writer, it) } ?: NoOpPlatformImageProvider,
        content = content,
    )
    return writer.encodeToByteArray()
}

/** LOCAL PATCH (rc-players): [captureCommonRemoteDocumentInto] of rc-creation-compose 3.0.0. */
@Deprecated("Pass a Profile.")
public suspend fun captureCommonRemoteDocumentInto(
    writer: RemoteWriter,
    creationDisplayInfo: RemoteCreationDisplayInfo,
    remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    supportedOperations: Set<Int>,
    platformImageProvider: PlatformImageProvider = NoOpPlatformImageProvider,
    content: @Composable @RemoteComposable () -> Unit,
): Unit =
    captureCommonRemoteDocumentInto(
        writer = writer,
        creationDisplayInfo = creationDisplayInfo,
        remoteDensity = remoteDensity,
        layoutDirection = layoutDirection,
        profile = legacyProfile(supportedOperations),
        platformImageProvider = platformImageProvider,
        content = content,
    )

/**
 * Captures [content] into any [RemoteWriter], not only the byte encoder.
 *
 * LOCAL PATCH (rc-players): this is the seam a typed sink uses to receive the document as
 * operations, so a player in the same process can take them without an encode/decode round trip.
 * The writer receives exactly the calls [captureCommonRemoteDocument]'s byte encoder would.
 * [profile] answers what a component asks (`currentProfileSupports…`); what [writer] accepts is its
 * own affair, and a writer made by `profile.create` accepts the profile's operations.
 */
public suspend fun captureCommonRemoteDocumentInto(
    writer: RemoteWriter,
    creationDisplayInfo: RemoteCreationDisplayInfo,
    remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    profile: Profile = RcPlatformProfiles.ANDROIDX,
    platformImageProvider: PlatformImageProvider = NoOpPlatformImageProvider,
    content: @Composable @RemoteComposable () -> Unit,
): Unit =
    withContext(Dispatchers.Default.limitedParallelism(1)) {
        val rootNode = RemoteRootNode()
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(RemoteComposeApplier(rootNode), recomposer)
        val creationState =
            RemoteComposeCaptureState(
                creationDisplayInfo = creationDisplayInfo,
                profile = profile,
                writer = writer,
                remoteDensity = remoteDensity,
                layoutDirection = layoutDirection.toRemoteLayoutDirection(),
                platformImageProvider = platformImageProvider,
            )

        try {
            composition.setContent {
                CompositionLocalProvider(
                    LocalRemoteComposeCreationState provides creationState,
                    LocalRemoteDensity provides remoteDensity,
                    LocalIs24HourFormat provides platformUses24HourTime(),
                    LocalDensity provides
                        Density(creationDisplayInfo.density, creationDisplayInfo.fontScale),
                    LocalInspectionMode provides creationDisplayInfo.isInspectionMode,
                    LocalLayoutDirection provides layoutDirection,
                    // As upstream's Android capture: follows a nested LocalLayoutDirection.
                    LocalRemoteLayoutDirection providesComputed
                        {
                            LocalLayoutDirection.currentValue.toRemoteLayoutDirection()
                        },
                    content = content,
                )
            }
            coroutineScope {
                var frameTime = 0L
                lateinit var frameClock: BroadcastFrameClock
                frameClock = BroadcastFrameClock {
                    launch {
                        frameTime += 16_666_667L
                        frameClock.sendFrame(frameTime)
                    }
                }
                val runner = launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
                recomposer.currentState.filter { it == Recomposer.State.Idle }.first()
                recomposer.cancel()
                runner.join()
            }

            Snapshot.withMutableSnapshot {
                val remoteCanvas = RemoteCanvas(creationState)
                rootNode.render(creationState, remoteCanvas)
                remoteCanvas.flush()
            }
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }

/** Bitmaps written inline as PNG, through a platform's own encoder. */
private class InlinePngImageProvider(
    private val writer: RemoteDocumentWriter,
    private val encodePng: (ImageBitmap) -> ByteArray?,
) : PlatformImageProvider {
    override fun addBitmap(image: ImageBitmap): Int =
        encodePng(image)?.let { writer.addBitmapPng(it, image.width, image.height) } ?: -1

    // Named, so a host can still replace the picture through the name the content declared.
    override fun addNamedBitmap(name: String, image: ImageBitmap): Int =
        encodePng(image)?.let { writer.addNamedBitmapPng(name, it, image.width, image.height) }
            ?: -1
}

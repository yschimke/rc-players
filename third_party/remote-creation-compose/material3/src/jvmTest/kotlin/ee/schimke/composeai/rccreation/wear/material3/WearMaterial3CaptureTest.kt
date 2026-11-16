package ee.schimke.composeai.rccreation.wear.material3

import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.common.RcPlatformProfiles
import ee.schimke.composeai.rccreation.compose.action.hostAction
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rccreation.wear.material3.wearcompose.R
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Wear Remote Material 3 on the JVM: what it writes, read back with the player's reader. Upstream's
 * own tests are Android's (Robolectric and screenshots); these check what the fork's patches change
 * — themed colours from every target, the type scale built from Wear Compose's tokens — and that
 * its components capture away from Android.
 */
class WearMaterial3CaptureTest {
    private fun capture(content: @Composable () -> Unit): RcDocument = runBlocking {
        val bytes =
            captureCommonRemoteDocument(
                creationDisplayInfo = RemoteCreationDisplayInfo(200, 200, 160),
                profile = RcPlatformProfiles.ANDROIDX,
                content = content,
            )
        RcDocumentCodec.decode(bytes, RcWireLimits())
    }

    private fun RcDocument.texts() = operations.filterIsInstance<RcTextData>().map { it.text }

    /** A themed title card, as a Wear app draws one, captures with its text. */
    @Test
    fun aTitleCardCaptures() {
        val document = capture {
            RemoteMaterialTheme {
                RemoteTitleCard(
                    onClick = hostAction("open".rs),
                    title = { RemoteText("Building adaptive apps".rs) },
                ) {
                    RemoteText("10:30 – 11:10".rs)
                }
            }
        }
        assertTrue("Building adaptive apps" in document.texts(), "${document.texts()}")
        assertTrue("10:30 – 11:10" in document.texts())
    }

    /**
     * The default scheme's roles are named colours a host can override (`WearM3.primary`), with
     * Wear Material 3's baseline as their values.
     */
    @Test
    fun theDefaultSchemeIsNamedColours() {
        val document = capture {
            RemoteMaterialTheme {
                RemoteText("x".rs, color = RemoteMaterialTheme.colorScheme.primary)
            }
        }
        val names = document.operations.filterIsInstance<RcNamedVariable>().map { it.name }
        assertTrue(names.any { it.endsWith("WearM3.primary") }, "$names")
        assertTrue(document.operations.none { it is RcColorTheme })
    }

    /**
     * A dynamic scheme follows the player's system colours where the profile has themed colours:
     * the role's system colour, by its index in the "android" group, with the role's baseline as
     * the fallback. Written the same from every target, as only the index reaches the document.
     */
    @Test
    fun aDynamicSchemeWritesThemedColoursByIndex() {
        val document = capture {
            RemoteMaterialTheme(colorScheme = dynamicRemoteColorScheme()) {
                RemoteText("x".rs, color = RemoteMaterialTheme.colorScheme.primary)
            }
        }
        val themed = document.operations.filterIsInstance<RcColorTheme>()
        val primary = R.color.system_primary_dark
        assertTrue(
            themed.any { it.lightModeIndex == primary && it.darkModeIndex == primary },
            "$themed",
        )
        // AndroidX's AndroidSystemColorMap index of android.R.color.system_primary_dark.
        assertEquals(150, primary)
    }

    /** The type scale is Wear Material 3's: its weights, sizes and variable-font axes. */
    @Test
    fun theTypeScaleIsWearMaterial3s() {
        val title = RemoteTypographyTokens.TitleMedium
        assertEquals(16f, title.fontSize?.constantValueOrNull)
        assertTrue(title.fontVariationSettings?.settings?.isNotEmpty() == true, "$title")
        assertEquals(null, title.fontFamily)
        assertEquals("pnum", title.fontFeatureSettings)
    }

    /**
     * The edge button sizes itself from its own measured width, which it reads unnamed
     * (`remoteComponent()`): nothing is left for a host to write but the theme's colours.
     */
    @Test
    fun anEdgeButtonReadsItsWidthUnnamed() {
        val document = capture {
            RemoteMaterialTheme {
                RemoteEdgeButton(onClick = hostAction("more".rs)) { RemoteText("More".rs) }
            }
        }
        val names = document.operations.filterIsInstance<RcNamedVariable>().map { it.name }
        assertTrue(names.all { "WearM3." in it }, "$names")
    }
}

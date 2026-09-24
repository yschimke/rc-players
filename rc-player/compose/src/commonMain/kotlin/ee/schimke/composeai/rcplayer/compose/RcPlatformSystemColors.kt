package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The platform's own resolver for a document's system colour names — the `android.R.color` names
 * [ee.schimke.composeai.rcplayer.protocol.RcAndroidSystemColors] maps `ColorTheme` indices to.
 *
 * This is the default of `RcComposePlayer`'s `systemColors`. On Android it reads the resources, the
 * way the embedded player's `AndroidRemoteContext` does, so a host passes nothing and a themed
 * colour draws the device's palette. Elsewhere there is no such palette and every name is `null`,
 * which keeps the document's recorded fallback. A host lambda passed explicitly replaces this
 * entirely — even one that returns `null` for everything.
 */
@Composable internal expect fun rememberRcPlatformSystemColors(): (name: String) -> Color?

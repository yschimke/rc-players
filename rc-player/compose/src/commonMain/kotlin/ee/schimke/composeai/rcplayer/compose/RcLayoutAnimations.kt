package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether layout changes animate — AndroidX's `RemoteContext.setAnimationEnabled`.
 *
 * On by default: as in AndroidX, every layout component eases its bounds to a new measure and fades
 * across a visibility change, over its `AnimationSpec` or the 300 ms default. A host turns it off
 * where a change should land at once, such as a window being resized; a document that wants a
 * component never to animate says so itself, with an animation spec whose id is 0.
 */
public val LocalRcLayoutAnimations: ProvidableCompositionLocal<Boolean> = compositionLocalOf {
  true
}

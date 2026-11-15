@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.creation.androidx

import androidx.compose.remote.creation.compose.state.rememberRemoteFloatExpression
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.PaintingStyle

// The one file of this binding that is written by hand where `:rc-widget-creation` has a file of the
// same name: every other common file is generated from that module's source (see build.gradle.kts).
// These two functions are what the AndroidX release this module is built on spells differently from
// androidx-main, which the Compose Multiplatform creation modules track.

/**
 * The enclosing layout component's size, as expressions the document evaluates as it plays: not
 * named, so nothing outside the document can write them. Call it inside the content of the
 * component it measures.
 *
 * AndroidX's creation API before androidx-main `ab1b76a2f` reads a component's size through a
 * `RemoteFloatContext` lambda; after it, through `remoteComponent()`.
 */
@Composable
internal fun rememberComponentSize(): RcComponentSize =
  RcComponentSize(
    rememberRemoteFloatExpression { componentWidth() },
    rememberRemoteFloatExpression { componentHeight() },
  )

/**
 * Paints strokes rather than fills. AndroidX's `RemotePaint.style` is Compose's `PaintingStyle`
 * until androidx-main `db6a60d2e` and a `RemotePaintingStyle` after it.
 */
internal fun RemotePaint.stroke() {
  style = PaintingStyle.Stroke
}

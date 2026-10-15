package androidx.compose.remote.creation.compose.capture

import android.graphics.BlendModeColorFilter
import androidx.compose.remote.creation.common.PaintBundleData
import android.graphics.BlendMode as AndroidBlendMode
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.remote.creation.compose.layout.toInt
import androidx.compose.remote.creation.compose.state.ComposeRemoteColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter

internal actual fun PaintBundleData.setComposeColorFilter(filter: ComposeRemoteColorFilter) {
    val native = filter.composeColorFilter.asAndroidColorFilter()
    require(native is BlendModeColorFilter) { "Native color filter not supported: $native" }
    setColorFilter(native.color, native.mode.toComposeBlendMode().toInt())
}

// Carried from the port's DrawHelpersAndroid.kt, the only part of it this actual needs.
private fun AndroidBlendMode.toComposeBlendMode(): BlendMode {
    return when (this) {
        AndroidBlendMode.CLEAR -> BlendMode.Clear
        AndroidBlendMode.SRC -> BlendMode.Src
        AndroidBlendMode.DST -> BlendMode.Dst
        AndroidBlendMode.SRC_OVER -> BlendMode.SrcOver
        AndroidBlendMode.DST_OVER -> BlendMode.DstOver
        AndroidBlendMode.SRC_IN -> BlendMode.SrcIn
        AndroidBlendMode.DST_IN -> BlendMode.DstIn
        AndroidBlendMode.SRC_OUT -> BlendMode.SrcOut
        AndroidBlendMode.DST_OUT -> BlendMode.DstOut
        AndroidBlendMode.SRC_ATOP -> BlendMode.SrcAtop
        AndroidBlendMode.DST_ATOP -> BlendMode.DstAtop
        AndroidBlendMode.XOR -> BlendMode.Xor
        AndroidBlendMode.PLUS -> BlendMode.Plus
        AndroidBlendMode.MODULATE -> BlendMode.Modulate
        AndroidBlendMode.SCREEN -> BlendMode.Screen
        AndroidBlendMode.OVERLAY -> BlendMode.Overlay
        AndroidBlendMode.DARKEN -> BlendMode.Darken
        AndroidBlendMode.LIGHTEN -> BlendMode.Lighten
        AndroidBlendMode.COLOR_DODGE -> BlendMode.ColorDodge
        AndroidBlendMode.COLOR_BURN -> BlendMode.ColorBurn
        AndroidBlendMode.HARD_LIGHT -> BlendMode.Hardlight
        AndroidBlendMode.SOFT_LIGHT -> BlendMode.Softlight
        AndroidBlendMode.DIFFERENCE -> BlendMode.Difference
        AndroidBlendMode.EXCLUSION -> BlendMode.Exclusion
        AndroidBlendMode.MULTIPLY -> BlendMode.Multiply
        AndroidBlendMode.HUE -> BlendMode.Hue
        AndroidBlendMode.SATURATION -> BlendMode.Saturation
        AndroidBlendMode.COLOR -> BlendMode.Color
        AndroidBlendMode.LUMINOSITY -> BlendMode.Luminosity
    }
}

/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ee.schimke.composeai.rccreation.wear.material3.wearcompose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

// Wear Compose Material 3's `Text.kt`: the text configuration its components provide.

/**
 * Material3 components related to text use [LocalTextConfiguration] to set values with which to
 * style child text components.
 */
public val LocalTextConfiguration: ProvidableCompositionLocal<TextConfiguration> =
    compositionLocalOf(structuralEqualityPolicy()) {
        TextConfiguration(
            TextConfigurationDefaults.TextAlign,
            TextConfigurationDefaults.Overflow,
            TextConfigurationDefaults.MaxLines,
        )
    }

/**
 * Class representing aspects of text that can be configured with [LocalTextConfiguration].
 *
 * @param textAlign The alignment of the text within the lines of the paragraph.
 * @param overflow How visual overflow should be handled.
 * @param maxLines The maximum number of lines for the text to span, wrapping if necessary.
 */
public class TextConfiguration(
    public val textAlign: TextAlign?,
    public val overflow: TextOverflow,
    public val maxLines: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is TextConfiguration) return false

        if (textAlign != other.textAlign) return false
        if (overflow != other.overflow) return false
        if (maxLines != other.maxLines) return false

        return true
    }

    override fun hashCode(): Int {
        var result = textAlign.hashCode()
        result = 31 * result + overflow.hashCode()
        result = 31 * result + maxLines.hashCode()
        return result
    }
}

/** Default values for [TextConfiguration] */
public object TextConfigurationDefaults {
    /** Default text alignment */
    public val TextAlign: TextAlign? = null

    /** Default visual text overflow */
    public val Overflow: TextOverflow = TextOverflow.Clip

    /** Default max lines */
    public val MaxLines: Int = Int.MAX_VALUE
}

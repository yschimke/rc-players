package androidx.compose.remote.creation.compose.text

import android.text.format.DateFormat
import java.util.Locale

// The locale's preferred hour cycle, not `DateFormat.is24HourFormat(context)`: a capture composes
// under its own recomposer, where no `LocalContext` is provided, so there is no `Context` to ask
// for the user's 12/24-hour setting. The "j" skeleton resolves to the locale's own hour symbol.
internal actual fun platformUses24HourTime(): Boolean =
    DateFormat.getBestDateTimePattern(Locale.getDefault(), "jm").any { it == 'H' || it == 'k' }

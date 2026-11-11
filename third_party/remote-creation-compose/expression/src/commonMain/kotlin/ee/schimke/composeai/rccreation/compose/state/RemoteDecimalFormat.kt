package ee.schimke.composeai.rccreation.compose.state

import ee.schimke.composeai.rccreation.common.TextFromFloat.GROUPING_BY3
import ee.schimke.composeai.rccreation.common.TextFromFloat.GROUPING_BY4
import ee.schimke.composeai.rccreation.common.TextFromFloat.GROUPING_NONE
import ee.schimke.composeai.rccreation.common.TextFromFloat.OPTIONS_NEGATIVE_PARENTHESES
import ee.schimke.composeai.rccreation.common.TextFromFloat.OPTIONS_ROUNDING
import ee.schimke.composeai.rccreation.common.TextFromFloat.PAD_AFTER_NONE
import ee.schimke.composeai.rccreation.common.TextFromFloat.PAD_AFTER_ZERO
import ee.schimke.composeai.rccreation.common.TextFromFloat.PAD_PRE_NONE
import ee.schimke.composeai.rccreation.common.TextFromFloat.PAD_PRE_ZERO
import ee.schimke.composeai.rccreation.common.TextFromFloat.SEPARATOR_COMMA_PERIOD
import ee.schimke.composeai.rccreation.common.TextFromFloat.SEPARATOR_PERIOD_COMMA
import ee.schimke.composeai.rccreation.common.TextFromFloat.SEPARATOR_SPACE_COMMA
import ee.schimke.composeai.rccreation.common.TextFromFloat.SEPARATOR_UNDER_PERIOD
import kotlin.math.pow

/** Deterministic, platform-neutral number formatting options for remote text. */
public class DecimalFormat {
    public var decimalFormatSymbols: DecimalFormatSymbols = DecimalFormatSymbols()
    public var isGroupingUsed: Boolean = true
    public var groupingSize: Int = 3
    public var minimumIntegerDigits: Int = 1
    public var maximumIntegerDigits: Int = 309
    public var minimumFractionDigits: Int = 0
    public var maximumFractionDigits: Int = 3
    public var negativePrefix: String = "-"
    public var roundingMode: RoundingMode = RoundingMode.HALF_EVEN
}

public data class DecimalFormatSymbols(
    public val decimalSeparator: Char = '.',
    public val groupingSeparator: Char = ',',
)

public enum class RoundingMode {
    UNNECESSARY,
    HALF_EVEN,
}

internal data class TextFromFloatOptions(val before: Int, val after: Int, val flags: Int)

internal val DefaultJavaDecimalFormat: DecimalFormat = DecimalFormat()

internal fun DecimalFormat.toJavaTextFromFloatOptions(): TextFromFloatOptions {
    val decimalSeparator = decimalFormatSymbols.decimalSeparator
    val groupingSeparator = decimalFormatSymbols.groupingSeparator

    val grouping =
        if (!isGroupingUsed) {
            GROUPING_NONE
        } else if (groupingSize == 3) {
            GROUPING_BY3
        } else if (groupingSize == 4) {
            GROUPING_BY4
        } else {
            GROUPING_NONE
        }

    val separator =
        if (groupingSeparator == ',' && decimalSeparator == '.') {
            SEPARATOR_COMMA_PERIOD
        } else if (groupingSeparator == '.' && decimalSeparator == ',') {
            SEPARATOR_PERIOD_COMMA
        } else if (groupingSeparator == ' ' && decimalSeparator == ',') {
            SEPARATOR_SPACE_COMMA
        } else if (groupingSeparator == '_' && decimalSeparator == '.') {
            SEPARATOR_UNDER_PERIOD
        } else {
            SEPARATOR_COMMA_PERIOD
        }

    val before = maximumIntegerDigits.coerceAtMost(255)
    val after = maximumFractionDigits.coerceAtMost(255)
    var options = 0
    if (negativePrefix == "(") {
        options = options or OPTIONS_NEGATIVE_PARENTHESES
    }

    if (roundingMode != RoundingMode.UNNECESSARY) {
        options = options or OPTIONS_ROUNDING
    }

    var flags = separator or grouping or options

    if (minimumFractionDigits > 1) {
        flags = flags or PAD_AFTER_ZERO
    } else {
        flags = flags or PAD_AFTER_NONE
    }

    if (minimumIntegerDigits > 1) {
        flags = flags or PAD_PRE_ZERO
    } else {
        flags = flags or PAD_PRE_NONE
    }

    return TextFromFloatOptions(before, after, flags)
}

internal fun formatRemoteFloatJvm(value: RemoteFloat, format: DecimalFormat): RemoteString {
    val (before, after, flags) = format.toJavaTextFromFloatOptions()

    if (format.minimumFractionDigits == 0 && after > 0 && format !== DefaultJavaDecimalFormat) {
        value.constantValueOrNull?.let { constVal ->
            val resolvedAfter = if (constVal % 1f == 0f) 0 else after
            return value.toRemoteStringWithPadding(
                before,
                resolvedAfter,
                flags,
                format.minimumIntegerDigits,
            )
        }

        val epsilonVal = (0.5f * 10f.pow(-after)).coerceAtLeast(0.00001f)
        val epsilon = epsilonVal.rf
        val isInteger = abs(value - round(value)).isLessThan(epsilon)
        return isInteger.select(
            round(value).toRemoteInt().toRemoteString(format),
            value.toRemoteStringWithPadding(
                before,
                after,
                flags,
                format.minimumIntegerDigits,
            ),
        )
    }

    return value.toRemoteStringWithPadding(
        before,
        after,
        flags,
        format.minimumIntegerDigits,
    )
}

internal fun formatRemoteIntJvm(value: RemoteInt, format: DecimalFormat): RemoteString {
    val (before, after, flags) = format.toJavaTextFromFloatOptions()
    val resolvedAfter = if (format.minimumFractionDigits == 0) 0 else after
    return value
        .toRemoteFloat()
        .toRemoteStringWithPadding(
            before,
            resolvedAfter,
            flags,
            format.minimumIntegerDigits,
        )
}

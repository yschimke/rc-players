package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcBooleanConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorAttribute
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDataMapLookup
import ee.schimke.composeai.rcplayer.protocol.RcDynamicFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIdLookup
import ee.schimke.composeai.rcplayer.protocol.RcIdMap
import ee.schimke.composeai.rcplayer.protocol.RcImageAttribute
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcMatrixConstant
import ee.schimke.composeai.rcplayer.protocol.RcMatrixExpression
import ee.schimke.composeai.rcplayer.protocol.RcMatrixVectorMath
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcParticleDefine
import ee.schimke.composeai.rcplayer.protocol.RcPathAppend
import ee.schimke.composeai.rcplayer.protocol.RcPathCombine
import ee.schimke.composeai.rcplayer.protocol.RcPathCreate
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcPathExpression
import ee.schimke.composeai.rcplayer.protocol.RcPathTween
import ee.schimke.composeai.rcplayer.protocol.RcShaderData
import ee.schimke.composeai.rcplayer.protocol.RcSoundData
import ee.schimke.composeai.rcplayer.protocol.RcSoundExpression
import ee.schimke.composeai.rcplayer.protocol.RcTextAttribute
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextLength
import ee.schimke.composeai.rcplayer.protocol.RcTextLookup
import ee.schimke.composeai.rcplayer.protocol.RcTextLookupInt
import ee.schimke.composeai.rcplayer.protocol.RcTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcTextSubtext
import ee.schimke.composeai.rcplayer.protocol.RcTextTransform
import ee.schimke.composeai.rcplayer.protocol.RcTimeAttribute
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import ee.schimke.composeai.rcplayer.protocol.RcUpdateDynamicFloatList

/**
 * The ids an operation gives a value, or null for one that defines nothing. Covers what the player
 * loads when a document opens and every producer it evaluates in a layout's content.
 */
internal fun RcOperation.definedIds(): List<Int>? =
  when (this) {
    is RcTextData -> listOf(id)
    is RcFloatConstant -> listOf(id)
    is RcColorConstant -> listOf(id)
    is RcIntegerConstant -> listOf(id)
    is RcBooleanConstant -> listOf(id)
    is RcLongConstant -> listOf(id)
    is RcPathData -> listOf(id)
    is RcMatrixConstant -> listOf(id)
    is RcIdList -> listOf(id)
    is RcFloatList -> listOf(id)
    is RcIdMap -> listOf(id)
    is RcBitmapData -> listOf(imageId)
    is RcFontData -> listOf(fontId)
    is RcNamedVariable -> listOf(id)
    is RcDynamicFloatList -> listOf(id)
    is RcUpdateDynamicFloatList -> listOf(listId)
    is RcFloatExpression -> listOf(id)
    is RcIntegerExpression -> listOf(outId)
    is RcColorExpression -> listOf(outId)
    is RcColorTheme -> listOf(outId)
    is RcColorAttribute -> listOf(outId)
    is RcImageAttribute -> listOf(outId)
    is RcDataMapLookup -> listOf(outId)
    is RcIdLookup -> listOf(outId)
    is RcMatrixExpression -> listOf(id)
    is RcMatrixVectorMath -> outputs
    is RcPathExpression -> listOf(id)
    is RcTextFromFloat -> listOf(outId)
    is RcTextMerge -> listOf(outId)
    is RcTextLength -> listOf(outId)
    is RcTextSubtext -> listOf(outId)
    is RcTextTransform -> listOf(outId)
    is RcTextLookup -> listOf(outId)
    is RcTextLookupInt -> listOf(outId)
    is RcTextMeasure -> listOf(outId)
    is RcTouchExpression -> listOf(id)
    is RcComponentValue -> listOf(valueId)
    is RcTextAttribute -> listOf(outId)
    is RcTimeAttribute -> listOf(outId)
    is RcBitmapTextMeasure -> listOf(outId)
    is RcPathCreate -> listOf(id)
    is RcPathAppend -> listOf(id)
    is RcPathCombine -> listOf(outId)
    is RcPathTween -> listOf(outId)
    is RcParticleDefine -> listOf(id) + variableIds
    is RcSoundExpression -> listOf(id)
    is RcBitmapFontData -> listOf(fontId)
    is RcShaderData -> listOf(shaderId)
    is RcSoundData -> listOf(soundId)
    else -> null
  }

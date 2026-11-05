package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBooleanConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorAttribute
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDataMapLookup
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDynamicFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIdLookup
import ee.schimke.composeai.rcplayer.protocol.RcIdMap
import ee.schimke.composeai.rcplayer.protocol.RcImageAttribute
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcMatrixConstant
import ee.schimke.composeai.rcplayer.protocol.RcMatrixExpression
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcOperationInventory
import ee.schimke.composeai.rcplayer.protocol.RcPathAppend
import ee.schimke.composeai.rcplayer.protocol.RcPathCombine
import ee.schimke.composeai.rcplayer.protocol.RcPathCreate
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcPathExpression
import ee.schimke.composeai.rcplayer.protocol.RcPathTween
import ee.schimke.composeai.rcplayer.protocol.RcRemark
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

/**
 * The values a document defines, by the id it reads them through, for the decompilers that turn a
 * document back into source: `toRemoteComposeKotlin` and `toUiBuilderDesign` read one document
 * through the same index, so they agree on what a value is and on which operations only define one.
 *
 * A constant defined more than once holds its last definition, as the player loads constants
 * (`RcPlayerState` assigns each in document order). A computed value is described by the first
 * operation that writes it; it is only named, never evaluated, here.
 */
internal class RcDocumentIndex(document: RcDocument) {
  val texts: Map<Int, String>
  val colors: Map<Int, Int>
  val floats: Map<Int, RcFloatWord>
  val ints: Map<Int, Int>
  val named: Map<Int, RcNamedVariable>
  val bitmaps: Map<Int, RcBitmapData>

  /** Operations computing a value at playback, by the id they write it to. */
  val producers: Map<Int, RcOperation>
  private val producerOperations: Set<RcOperation>
  private val constantIntExpressions: Set<RcOperation>

  init {
    val texts = mutableMapOf<Int, String>()
    val colors = mutableMapOf<Int, Int>()
    val floats = mutableMapOf<Int, RcFloatWord>()
    val ints = mutableMapOf<Int, Int>()
    val named = mutableMapOf<Int, RcNamedVariable>()
    val bitmaps = mutableMapOf<Int, RcBitmapData>()
    val producers = mutableMapOf<Int, RcOperation>()
    val constantIntExpressions = mutableSetOf<RcOperation>()
    fun <V> MutableMap<Int, V>.first(id: Int, value: V) {
      if (id !in this) this[id] = value
    }
    document.operations.forEach { operation ->
      when (operation) {
        is RcTextData -> texts[operation.id] = operation.text
        is RcColorConstant -> colors[operation.id] = operation.argb
        is RcFloatConstant -> floats[operation.id] = operation.value
        is RcIntegerConstant -> ints[operation.id] = operation.value
        is RcNamedVariable -> named[operation.id] = operation
        is RcBitmapData -> bitmaps[operation.imageId] = operation
        is RcFloatExpression -> producers.first(operation.id, operation)
        is RcIntegerExpression ->
          // `1.ri` is written as an expression of one literal and no operators.
          if (operation.values.size == 1 && !operation.isMarked(0)) {
            ints[operation.outId] = operation.values[0]
            constantIntExpressions += operation
          } else {
            producers.first(operation.outId, operation)
          }
        is RcColorExpression -> producers.first(operation.outId, operation)
        is RcColorTheme -> producers.first(operation.outId, operation)
        is RcColorAttribute -> producers.first(operation.outId, operation)
        is RcComponentValue -> producers.first(operation.valueId, operation)
        is RcTextMerge -> producers.first(operation.outId, operation)
        is RcTextFromFloat -> producers.first(operation.outId, operation)
        is RcTextLookup -> producers.first(operation.outId, operation)
        is RcTextLookupInt -> producers.first(operation.outId, operation)
        else -> Unit
      }
    }
    this.texts = texts
    this.colors = colors
    this.floats = floats
    this.ints = ints
    this.named = named
    this.bitmaps = bitmaps
    this.producers = producers
    this.producerOperations = producers.values.toSet()
    this.constantIntExpressions = constantIntExpressions
  }

  /**
   * Whether [operation] only defines a value something else reads. A decompiler writes such a value
   * where it is read, so the definition itself needs no statement.
   */
  fun isDefinition(operation: RcOperation): Boolean =
    operation is RcTextData ||
      operation is RcColorConstant ||
      operation is RcFloatConstant ||
      operation is RcIntegerConstant ||
      operation is RcBooleanConstant ||
      operation is RcLongConstant ||
      operation is RcNamedVariable ||
      operation is RcBitmapData ||
      operation is RcComponentValue ||
      operation is RcRemark ||
      operation is RcIdList ||
      operation is RcFloatList ||
      operation is RcDynamicFloatList ||
      operation is RcIdMap ||
      operation is RcDataMapLookup ||
      operation is RcIdLookup ||
      operation is RcPathData ||
      operation is RcFontData ||
      operation is RcTextLength ||
      operation is RcTextSubtext ||
      operation is RcTextTransform ||
      operation is RcTextMeasure ||
      operation is RcTextAttribute ||
      operation is RcTimeAttribute ||
      operation is RcImageAttribute ||
      operation is RcMatrixConstant ||
      operation is RcMatrixExpression ||
      operation is RcPathCreate ||
      operation is RcPathAppend ||
      operation is RcPathCombine ||
      operation is RcPathExpression ||
      operation is RcPathTween ||
      operation in producerOperations ||
      operation in constantIntExpressions ||
      operation.opcode == RcOpcodes.CONTAINER_END ||
      operation.opcode == RcOpcodes.HEADER

  /** An id as a reader would want it described: what computes it, where something does. */
  fun describeId(id: Int): String {
    val producer = producers[id] ?: return "id $id"
    return "id $id (${producer::class.simpleName?.removePrefix("Rc")})"
  }

  companion object {
    /** The density a document was captured at: [explicit], else the header's, else 1. */
    fun densityOf(header: RcHeader, explicit: Float?): Float =
      explicit
        ?: header.properties
          .firstOrNull { it.key == RcHeader.DOC_DENSITY_AT_GENERATION }
          ?.let { header.density }
        ?: 1f

    /**
     * Whether [header]'s border widths are written in dp. A legacy-density document before version
     * 1.1 kept them in dp; from 1.1, and in a pixels document, they are pixels like other legacy
     * lengths. The player reads them the same way (`RcComposePlayer`'s `borderWidthPixels`).
     */
    fun bordersInDp(header: RcHeader): Boolean {
      if (header.densityBehavior == RcHeader.DENSITY_BEHAVIOR_DP) return true
      val version = header.version
      val atLeast11 = version.major > 1 || (version.major == 1 && version.minor >= 1)
      return header.densityBehavior == RcHeader.DENSITY_BEHAVIOR_LEGACY && !atLeast11
    }

    /** An operation's name, as the operation inventory spells it. */
    fun describe(operation: RcOperation): String =
      RcOperationInventory.byOpcode[operation.opcode]?.stableName
        ?: operation::class.simpleName?.removePrefix("Rc")
        ?: "opcode ${operation.opcode}"
  }
}

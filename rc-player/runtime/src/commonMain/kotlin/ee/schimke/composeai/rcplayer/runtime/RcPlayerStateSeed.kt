package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBooleanConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIdMap
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcMatrixConstant
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcRootContentBehavior
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression

/**
 * What every [RcPlayerState] of [document] starts from, scanned from the document once: its
 * declared constants, texts, paths, lists and named variables, and the indexes the state looks
 * expressions and component values up in.
 *
 * A state built from a seed (`RcPlayerState(seed, …)`) copies these instead of walking the
 * document's operations again, and is otherwise the same state: the host's named values, the clock,
 * sound and particles are still its own. Nothing here is written after construction, so one seed
 * can be shared by any number of states on any thread, and built off the main thread ahead of the
 * first frame that needs a state.
 */
public class RcPlayerStateSeed(public val document: RcDocument) {
  internal val componentValues: Map<Int, List<RcComponentValue>> = rcComponentValuesOf(document)
  internal val floatExpressions: Map<Int, RcFloatExpression> =
    document.operations.filterIsInstance<RcFloatExpression>().associateBy { it.id }
  internal val integerExpressions: Map<Int, RcIntegerExpression> =
    document.operations.filterIsInstance<RcIntegerExpression>().associateBy { it.outId }
  internal val claimedSystemIds: Set<Int> = rcClaimedSystemIdsOf(document)
  internal val rootContentBehavior: RcRootContentBehavior? =
    document.operations.filterIsInstance<RcRootContentBehavior>().lastOrNull()

  private val values = RcSeedValues().also { it.load(document) }

  internal fun copyInto(target: RcSeedValues) {
    target.floats.putAll(values.floats)
    target.integers.putAll(values.integers)
    target.colors.putAll(values.colors)
    target.baseTexts.putAll(values.baseTexts)
    target.booleans.putAll(values.booleans)
    target.longs.putAll(values.longs)
    target.basePaths.putAll(values.basePaths)
    target.matrices.putAll(values.matrices)
    target.idLists.putAll(values.idLists)
    target.floatLists.putAll(values.floatLists)
    target.idMaps.putAll(values.idMaps)
    target.bitmaps.putAll(values.bitmaps)
    target.variableNames.putAll(values.variableNames)
  }
}

/** The maps a state's declarations fill: its own, or a seed's to copy from. */
internal class RcSeedValues(
  val floats: MutableMap<Int, Float> = mutableMapOf(),
  val integers: MutableMap<Int, Int> = mutableMapOf(),
  val colors: MutableMap<Int, Int> = mutableMapOf(),
  val baseTexts: MutableMap<Int, String> = mutableMapOf(),
  val booleans: MutableMap<Int, Boolean> = mutableMapOf(),
  val longs: MutableMap<Int, Long> = mutableMapOf(),
  val basePaths: MutableMap<Int, RcPathData> = mutableMapOf(),
  val matrices: MutableMap<Int, RcMatrixConstant> = mutableMapOf(),
  val idLists: MutableMap<Int, RcIdList> = mutableMapOf(),
  val floatLists: MutableMap<Int, RcFloatList> = mutableMapOf(),
  val idMaps: MutableMap<Int, RcIdMap> = mutableMapOf(),
  val bitmaps: MutableMap<Int, RcBitmapData> = mutableMapOf(),
  val variableNames: MutableMap<String, RcNamedVariable> = mutableMapOf(),
) {
  /**
   * Applies [document]'s declarations in order, as a new state does. A float publishes both numeric
   * views, and an integer does too unless the id already holds that integer, as
   * [RcPlayerState.setInteger] does.
   */
  fun load(document: RcDocument) {
    for (operation in document.operations) {
      when (operation) {
        is RcFloatConstant -> {
          val value = operation.value.value
          floats[operation.id] = value
          integers[operation.id] = value.toInt()
        }
        is RcColorConstant -> colors[operation.id] = operation.argb
        is RcTextData -> baseTexts[operation.id] = operation.text
        is RcIntegerConstant ->
          if (integers[operation.id] != operation.value) {
            integers[operation.id] = operation.value
            floats[operation.id] = operation.value.toFloat()
          }
        is RcBooleanConstant -> booleans[operation.id] = operation.value
        is RcLongConstant -> longs[operation.id] = operation.value
        is RcPathData -> basePaths[operation.id] = operation
        is RcMatrixConstant -> matrices[operation.id] = operation
        is RcIdList -> idLists[operation.id] = operation
        is RcFloatList -> floatLists[operation.id] = operation
        is RcIdMap -> idMaps[operation.id] = operation
        is RcBitmapData -> bitmaps[operation.imageId] = operation
        is RcNamedVariable -> variableNames[operation.name] = operation
        else -> Unit
      }
    }
  }
}

/** [document]'s `ComponentValue`s by the component they read. */
internal fun rcComponentValuesOf(document: RcDocument): Map<Int, List<RcComponentValue>> =
  document.operations.filterIsInstance<RcComponentValue>().distinct().groupBy { it.componentId }

/** The system ids [document]'s own declarations claim; see `RcPlayerState.claimedSystemIds`. */
internal fun rcClaimedSystemIdsOf(document: RcDocument): Set<Int> = buildSet {
  document.operations.forEach { operation ->
    when (operation) {
      is RcFloatConstant -> add(operation.id)
      is RcIntegerConstant -> add(operation.id)
      is RcTouchExpression -> add(operation.id)
      is RcComponentValue -> add(operation.valueId)
      else -> Unit
    }
  }
}
  .intersect(RcSystemVariables.ALL)

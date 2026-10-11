package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcIdOperation
import ee.schimke.composeai.rcplayer.protocol.RcMatrixConstant
import ee.schimke.composeai.rcplayer.protocol.RcMatrixExpression
import ee.schimke.composeai.rcplayer.protocol.RcMatrixVectorMath
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcTheme
import ee.schimke.composeai.rcplayer.protocol.RcTransform2

/**
 * This document with every `MatrixExpression` that only positions a paint's shader rewritten into
 * operations a level 6 player has: the canvas transforms `MatrixTranslate` and `MatrixScale`.
 *
 * A shader matrix maps the shader into the canvas, so drawing a path under it equals drawing the
 * path mapped through the matrix's inverse on a canvas transformed by the matrix itself, with no
 * shader matrix at all: the path lands where it was and the shader, which lives in the canvas's
 * local space, moves with the transform. That is what each draw becomes — `MatrixSave;
 * MatrixTranslate; MatrixScale; DrawPath(inverse path); MatrixRestore` — exactly, for a filled
 * path. It is how AndroidX's `RemoteImageBrush` scales an image to fill a shape, and the one use
 * the catalog makes of matrices.
 *
 * A matrix is lowered only when all of this holds, and left as it is otherwise:
 * - it is a 2D translate and scale (no rotation, skew or projection) and the only definition of its
 *   id, used by no `MatrixVectorMath`;
 * - every paint naming it as a shader matrix is a later sibling in the same container, and the
 *   operations after that paint, up to the next paint that replaces the shader or its matrix, are
 *   only `DrawPath`s of plain path data while the paint fills, and operations that draw nothing;
 * - nothing between the matrix and its last draw changes an id the matrix reads, or the theme;
 * - no paint in the document uses a path effect, which a canvas transform would scale.
 */
public fun RcDocument.lowerMatrixExpressions(): RcDocument {
  if (operations.none { it is RcMatrixExpression }) return this
  val tree = operationTree(operations) ?: return this
  val paints = operations.map {
    (it as? RcPaintData)?.let { paint -> paint.commands() ?: return this }
  }
  val ids = RcIdAllocator(operations)
  val replacements = mutableMapOf<Int, List<RcOperation>>()

  fun visit(siblings: List<RcOperationNode>) {
    siblings.forEachIndexed { position, node ->
      val matrix = node.operation as? RcMatrixExpression
      if (matrix != null) {
        lowerMatrix(matrix, node.start, siblings, position, paints, ids)?.let(replacements::putAll)
      }
      visit(node.children)
    }
  }
  visit(tree)
  if (replacements.isEmpty()) return this
  return copy(
    operations =
      operations.flatMapIndexed { index, operation -> replacements[index] ?: listOf(operation) }
  )
}

/** The replacements lowering [matrix] makes, by operation index, or null to leave it. */
private fun RcDocument.lowerMatrix(
  matrix: RcMatrixExpression,
  index: Int,
  siblings: List<RcOperationNode>,
  position: Int,
  paints: List<List<RcPaintCommand>?>,
  ids: RcIdAllocator,
): Map<Int, List<RcOperation>>? {
  val id = matrix.id
  val matrices = operations.count {
    (it is RcMatrixExpression && it.id == id) || (it is RcMatrixConstant && it.id == id)
  }
  if (matrices != 1) return null
  if (operations.any { it is RcMatrixVectorMath && it.matrixId == id }) return null
  val affine = RcAffine.of(matrix.expression) ?: return null
  if (affine.scaleX.literal == 0f || affine.scaleY.literal == 0f) return null
  // Composite values are computed where the matrix was, so they read what it read when it did.
  val definitions = mutableListOf<RcOperation>()
  val transform = affine.placed(ids, definitions)

  // Every paint naming this matrix, which must all be later siblings.
  val uses =
    paints.indices.filter { paintIndex ->
      val words = (operations[paintIndex] as? RcPaintData)?.words
      paints[paintIndex].orEmpty().any {
        it.tag == PAINT_SHADER_MATRIX && RcFloatWord(words!![it.start + 1]).referencedId == id
      }
    }
  val later = siblings.drop(position + 1)
  if (uses.isEmpty() || uses.any { use -> later.none { it.start == use && !it.isContainer } }) {
    return null
  }

  val result = mutableMapOf<Int, List<RcOperation>>()
  val draws = mutableListOf<Int>()
  for (use in uses) {
    val paint = operations[use] as RcPaintData
    val commands = paints[use]!!
    val matrixCommands = commands.filter { it.tag == PAINT_SHADER_MATRIX }
    if (matrixCommands.size != 1) return null
    val matrixCommand = matrixCommands.single()
    // A shader set after the matrix in the same bundle replaces the shader it positioned.
    if (commands.any { it.start > matrixCommand.start && it.replacesShader() }) return null
    // A paint fills until some bundle sets another style, here or anywhere earlier in the stream.
    var fill =
      commands.lastOrNull { it.tag == PAINT_STYLE }?.let { it.packed == PAINT_STYLE_FILL }
        ?: (0 until use).none { earlier ->
          paints[earlier].orEmpty().any { it.tag == PAINT_STYLE && it.packed != PAINT_STYLE_FILL }
        }
    result[use] =
      listOf(
        paint.copy(
          words =
            paint.words.filterIndexed { word, _ ->
              word !in matrixCommand.start until matrixCommand.end
            }
        )
      )

    // The region the matrix positions: up to the next paint that replaces the shader or its matrix.
    val start = siblings.indexOfFirst { it.start == use }
    val region = siblings.drop(start + 1)
    val end = region.indexOfFirst { sibling ->
      paints[sibling.start].orEmpty().any { it.replacesShader() || it.tag == PAINT_SHADER_MATRIX }
    }
    if (end < 0) return null
    for (sibling in region.take(end)) {
      if (sibling.isContainer) return null
      val operation = sibling.operation
      when {
        operation is RcPaintData -> {
          paints[sibling.start]!!
            .lastOrNull { it.tag == PAINT_STYLE }
            ?.let { fill = it.packed == PAINT_STYLE_FILL }
        }
        operation.opcode == RcOpcodes.DRAW_PATH -> {
          if (!fill) return null
          val pathId = (operation as RcIdOperation).id
          val path = plainPath(pathId) ?: return null
          result[sibling.start] = drawUnder(transform, path, ids) ?: return null
          draws += sibling.start
        }
        operation.opcode !in DRAWS_NOTHING -> return null
      }
    }
  }
  val lastDraw = draws.maxOrNull() ?: return null

  // What the matrix reads must hold still until the last draw reads it instead.
  val operands = matrix.expression.mapNotNullTo(mutableSetOf()) { it.referencedId }
  for (between in index + 1..lastDraw) {
    val operation = operations[between]
    if (operation is RcTheme) return null
    if (operation.definedIds().orEmpty().any { it in operands }) return null
    if (operation.opcode in READ_ONLY_OPCODES) continue
    val mentioned = operation.mentionedValues() ?: return null
    if (mentioned.any { it in operands }) return null
  }

  result[index] = definitions
  return result
}

private fun RcPaintCommand.replacesShader(): Boolean =
  tag == PAINT_SHADER || tag == PAINT_TEXTURE || tag == PAINT_GRADIENT

/** The `PathData` defining [pathId], when it is that id's only definition and plain path data. */
private fun RcDocument.plainPath(pathId: Int): RcPathData? {
  val definitions = operations.filter { it.definedIds()?.contains(pathId) == true }
  return (definitions.singleOrNull() as? RcPathData)?.takeIf { pathPoints(it.words) != null }
}

/**
 * Which words of a path are x and y coordinates — `true` for x, `false` for y, null for a command
 * or a conic weight — or null for a path with a command this does not know.
 */
private fun pathPoints(words: List<RcFloatWord>): List<Boolean?>? {
  val axes = mutableListOf<Boolean?>()
  var index = 0
  fun points(count: Int) =
    repeat(count) {
      axes += true
      axes += false
    }
  while (index < words.size) {
    axes += null
    when (words[index].referencedId?.takeIf { words[index].isNaNEncoded }) {
      RcPathCommands.MOVE -> points(1)
      // The two words after every segment command repeat the previous point.
      RcPathCommands.LINE -> points(2)
      RcPathCommands.QUADRATIC -> points(3)
      RcPathCommands.CONIC -> {
        points(3)
        axes += null
      }
      RcPathCommands.CUBIC -> points(4)
      RcPathCommands.CLOSE,
      RcPathCommands.DONE -> Unit
      else -> return null
    }
    index = axes.size
  }
  return axes.takeIf { it.size == words.size }
}

/**
 * [path] drawn through [transform]'s inverse on a canvas [transform] moves, as the player draws it:
 * the inverse path's coordinates, the path itself, and the transformed draw.
 */
private fun drawUnder(
  transform: RcAffine,
  path: RcPathData,
  ids: RcIdAllocator,
): List<RcOperation>? {
  val axes = pathPoints(path.words) ?: return null
  val definitions = mutableListOf<RcOperation>()
  val inverted = mutableMapOf<Pair<RcFloatWord, Boolean>, RcFloatWord>()
  val words =
    path.words.mapIndexed { word, value ->
      val x = axes[word] ?: return@mapIndexed value
      inverted.getOrPut(value to x) {
        val offset = if (x) transform.translateX else transform.translateY
        val scale = if (x) transform.scaleX else transform.scaleY
        ((RcSymbolic.of(value) - offset) / scale).asWord(ids, definitions)
      }
    }
  val inversePath = ids.next()
  if (inversePath > 0x00ffffff) return null
  return buildList {
    addAll(definitions)
    add(RcPathData((path.winding shl 24) or inversePath, words))
    add(RcNoArg(RcOpcodes.MATRIX_SAVE))
    if (transform.translateX.literal != 0f || transform.translateY.literal != 0f) {
      add(
        RcTransform2(
          RcOpcodes.MATRIX_TRANSLATE,
          transform.translateX.words.single(),
          transform.translateY.words.single(),
        )
      )
    }
    if (transform.scaleX.literal != 1f || transform.scaleY.literal != 1f) {
      add(
        RcDraw4(
          RcOpcodes.MATRIX_SCALE,
          transform.scaleX.words.single(),
          transform.scaleY.words.single(),
          RcFloatWord.literal(0f),
          RcFloatWord.literal(0f),
        )
      )
    }
    add(RcIdOperation(RcOpcodes.DRAW_PATH, inversePath))
    add(RcNoArg(RcOpcodes.MATRIX_RESTORE))
  }
}

/**
 * Operations that draw nothing and leave the paint alone, so a shader matrix region may hold them.
 */
private val DRAWS_NOTHING: Set<Int> =
  setOf(
    RcOpcodes.DATA_PATH,
    RcOpcodes.DATA_FLOAT,
    RcOpcodes.DATA_INT,
    RcOpcodes.ANIMATED_FLOAT,
    RcOpcodes.INTEGER_EXPRESSION,
    RcOpcodes.DATA_TEXT,
    RcOpcodes.COLOR_CONSTANT,
    RcOpcodes.COLOR_EXPRESSIONS,
    RcOpcodes.ATTRIBUTE_COLOR,
    RcOpcodes.ATTRIBUTE_IMAGE,
    RcOpcodes.NAMED_VARIABLE,
    RcOpcodes.MATRIX_SAVE,
    RcOpcodes.MATRIX_RESTORE,
    RcOpcodes.MATRIX_TRANSLATE,
    RcOpcodes.MATRIX_SCALE,
    RcOpcodes.MATRIX_ROTATE,
    RcOpcodes.MATRIX_SKEW,
    RcOpcodes.CLIP_PATH,
    RcOpcodes.CLIP_RECT,
  )

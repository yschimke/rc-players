package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcComponentStart
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIdRemapper
import ee.schimke.composeai.rcplayer.protocol.RcIncludeReferencedOperations
import ee.schimke.composeai.rcplayer.protocol.RcMacroArgument
import ee.schimke.composeai.rcplayer.protocol.RcMacroBlock
import ee.schimke.composeai.rcplayer.protocol.RcMacroCall
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcMacroForEach
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcReferencedOperations
import ee.schimke.composeai.rcplayer.protocol.RcWireWriter
import ee.schimke.composeai.rcplayer.trace.RcTraceCategory
import ee.schimke.composeai.rcplayer.trace.rcTrace

public sealed interface RcLinkedNode {
  public data class Operation(val operation: RcOperation) : RcLinkedNode

  public data class Container(val operation: RcOperation, val children: List<RcLinkedNode>) :
    RcLinkedNode
}

public data class RcLinkedDocument(val source: RcDocument, val operations: List<RcLinkedNode>)

public class RcLinkException(message: String) : IllegalArgumentException(message)

/**
 * Links AndroidX's flat stream and safely materializes its structural reference/macro operations.
 */
public object RcDocumentLinker {
  private const val MAX_NESTING_DEPTH = 256
  private const val MAX_EXPANSION_DEPTH = 64
  private const val MAX_EXPANDED_NODES = 100_000

  public fun link(document: RcDocument): RcLinkedDocument =
    rcTrace(RcTraceCategory.DOCUMENT, "rc:link") { linkUnchecked(document) }

  private fun linkUnchecked(document: RcDocument): RcLinkedDocument {
    val linked = linkNodes(document.operations)
    val references = mutableMapOf<Int, RcLinkedNode.Container>()
    val macros = mutableMapOf<Int, RcMacroDefine>()
    val arrays = mutableMapOf<Int, List<Int>>()
    collectDefinitions(linked, references, macros, arrays)
    val expansion =
      ExpansionState(
        references,
        macros,
        arrays,
        idRemapper = RcIdRemapper.expanding(reservedIds = reservedIds(document.operations)),
      )
    return RcLinkedDocument(
      document,
      expand(linked, expansion, depth = 0, activeDefinitions = emptySet(), blocks = emptyMap()),
    )
  }

  private fun linkNodes(operations: List<RcOperation>): List<RcLinkedNode> {
    val root = mutableListOf<RcLinkedNode>()
    val stack = mutableListOf<Frame>()
    var destination = root
    operations.forEachIndexed { index, operation ->
      when {
        operation.opcode in containerStartOpcodes -> {
          if (stack.size >= MAX_NESTING_DEPTH) {
            throw RcLinkException(
              "Container nesting exceeds $MAX_NESTING_DEPTH at operation $index"
            )
          }
          val frame = Frame(operation, destination, mutableListOf())
          stack += frame
          destination = frame.children
        }
        operation.opcode == RcOpcodes.CONTAINER_END -> {
          val frame =
            stack.removeLastOrNull()
              ?: throw RcLinkException("Unmatched ContainerEnd at operation $index")
          frame.parent += RcLinkedNode.Container(frame.operation, frame.children.toList())
          destination = frame.parent
        }
        else -> destination += RcLinkedNode.Operation(operation)
      }
    }
    if (stack.isNotEmpty()) {
      throw RcLinkException(
        "Unclosed ${stack.last().operation::class.simpleName} container at end of document"
      )
    }
    return root
  }

  private fun collectDefinitions(
    nodes: List<RcLinkedNode>,
    references: MutableMap<Int, RcLinkedNode.Container>,
    macros: MutableMap<Int, RcMacroDefine>,
    arrays: MutableMap<Int, List<Int>>,
  ) {
    nodes.forEach { node ->
      val operation = node.operation()
      if (operation is RcMacroDefine && macros.put(operation.id, operation) != null) {
        throw RcLinkException("Duplicate MacroDefine id ${operation.id}")
      }
      if (operation is RcIdList && arrays.put(operation.id, operation.ids) != null) {
        throw RcLinkException("Duplicate IdList id ${operation.id}")
      }
      if (node is RcLinkedNode.Container) {
        val definition = node.operation as? RcReferencedOperations
        if (definition != null && references.put(definition.id, node) != null) {
          throw RcLinkException("Duplicate ReferencedOperations id ${definition.id}")
        }
        collectDefinitions(node.children, references, macros, arrays)
      }
    }
  }

  private fun expand(
    nodes: List<RcLinkedNode>,
    state: ExpansionState,
    depth: Int,
    activeDefinitions: Set<DefinitionKey>,
    blocks: Map<Int, List<RcLinkedNode>>,
  ): List<RcLinkedNode> {
    if (depth > MAX_EXPANSION_DEPTH) {
      throw RcLinkException("Structural expansion exceeds $MAX_EXPANSION_DEPTH levels")
    }
    val result = mutableListOf<RcLinkedNode>()
    nodes.forEach { node ->
      when {
        node is RcLinkedNode.Container && node.operation is RcReferencedOperations -> Unit
        node is RcLinkedNode.Operation && node.operation is RcMacroDefine -> Unit
        node is RcLinkedNode.Container && node.operation is RcComponentStart ->
          result += expand(node.children, state, depth, activeDefinitions, blocks)
        node is RcLinkedNode.Operation && node.operation is RcIncludeReferencedOperations -> {
          val id = node.operation.id
          val key = DefinitionKey.Reference(id)
          if (key in activeDefinitions) {
            throw RcLinkException("Cyclic ReferencedOperations include for id $id")
          }
          val definition =
            state.references[id] ?: throw RcLinkException("Missing ReferencedOperations id $id")
          result += expand(definition.children, state, depth + 1, activeDefinitions + key, blocks)
        }
        node is RcLinkedNode.Container && node.operation is RcMacroCall -> {
          val call = node.operation
          val key = DefinitionKey.Macro(call.id)
          if (key in activeDefinitions) throw RcLinkException("Recursive MacroCall id ${call.id}")
          val definition =
            state.macros[call.id] ?: throw RcLinkException("Missing MacroDefine id ${call.id}")
          if (definition.parameterIds.size != call.argumentIds.size) {
            throw RcLinkException(
              "Macro ${call.id} expects ${definition.parameterIds.size} arguments, got ${call.argumentIds.size}"
            )
          }
          val callBlocks = mutableMapOf<Int, List<RcLinkedNode>>()
          node.children.forEach { child ->
            val block = child as? RcLinkedNode.Container
            val blockOp = block?.operation as? RcMacroBlock ?: return@forEach
            if (callBlocks.put(blockOp.parameterIndex, block.children) != null) {
              throw RcLinkException("Duplicate MacroBlock index ${blockOp.parameterIndex}")
            }
          }
          val mappings = definition.parameterIds.zip(call.argumentIds).toMap()
          val body =
            RcDocumentCodec.decodeOperations(
              definition.body,
              idRemapper = state.idRemapper.fork(mappings),
            )
          val linkedBody = linkNodes(body)
          collectDefinitions(linkedBody, state.references, state.macros, state.arrays)
          result += expand(linkedBody, state, depth + 1, activeDefinitions + key, callBlocks)
        }
        node is RcLinkedNode.Operation && node.operation is RcMacroArgument -> {
          val argument = node.operation
          val body =
            blocks[argument.parameterIndex]
              ?: throw RcLinkException("Missing MacroBlock index ${argument.parameterIndex}")
          result += expand(body, state, depth + 1, activeDefinitions, blocks)
        }
        node is RcLinkedNode.Container && node.operation is RcMacroForEach -> {
          val loop = node.operation
          val ids =
            state.arrays[loop.collectionId]
              ?: throw RcLinkException("Missing IdList ${loop.collectionId} for MacroForEach")
          ids.forEach { id ->
            val remapped =
              remap(node.children, state.idRemapper.fork(mapOf(loop.localItemId to id)))
            collectDefinitions(remapped, state.references, state.macros, state.arrays)
            result += expand(remapped, state, depth + 1, activeDefinitions, blocks)
          }
        }
        node is RcLinkedNode.Container && node.operation is RcMacroBlock -> {
          throw RcLinkException("MacroBlock outside MacroCall")
        }
        node is RcLinkedNode.Container ->
          result +=
            RcLinkedNode.Container(
              node.operation,
              expand(node.children, state, depth, activeDefinitions, blocks),
            )
        else -> result += node
      }
      state.expandedNodes += 1
      if (state.expandedNodes > MAX_EXPANDED_NODES) {
        throw RcLinkException("Expanded document exceeds $MAX_EXPANDED_NODES nodes")
      }
    }
    return result
  }

  private data class ExpansionState(
    val references: MutableMap<Int, RcLinkedNode.Container>,
    val macros: MutableMap<Int, RcMacroDefine>,
    val arrays: MutableMap<Int, List<Int>>,
    val idRemapper: RcIdRemapper,
    var expandedNodes: Int = 0,
  )

  private sealed interface DefinitionKey {
    data class Reference(val id: Int) : DefinitionKey

    data class Macro(val id: Int) : DefinitionKey
  }

  private fun remap(nodes: List<RcLinkedNode>, remapper: RcIdRemapper): List<RcLinkedNode> {
    val output = RcWireWriter()
    fun write(node: RcLinkedNode) {
      RcDocumentCodec.encodeOperation(output, node.operation())
      if (node is RcLinkedNode.Container) {
        node.children.forEach(::write)
        RcDocumentCodec.encodeOperation(output, RcNoArg(RcOpcodes.CONTAINER_END))
      }
    }
    nodes.forEach(::write)
    return linkNodes(RcDocumentCodec.decodeOperations(output.toByteArray(), idRemapper = remapper))
  }

  /**
   * Conservatively reserves every four-byte word that could be an id. Scanning at every byte offset
   * may reserve harmless literals too, but cannot miss a real encoded id and therefore keeps
   * generated macro-local ids collision-free without duplicating every codec's schema.
   */
  private fun reservedIds(operations: List<RcOperation>): Set<Int> {
    return buildSet {
      operations.forEach { operation ->
        // Support reporting links documents that may already contain an invalid operation. An
        // encoder-level validation failure must remain that operation's support issue rather than
        // being duplicated as an unrelated ContainerStructure failure. Such a document cannot be
        // executed, so omitting only its unencodable words from collision reservation is safe.
        val bytes =
          runCatching {
            RcWireWriter().also { RcDocumentCodec.encodeOperation(it, operation) }.toByteArray()
          }
            .getOrNull() ?: return@forEach
        for (offset in 0..bytes.size - 4) {
          val value =
            ((bytes[offset].toInt() and 0xff) shl 24) or
              ((bytes[offset + 1].toInt() and 0xff) shl 16) or
              ((bytes[offset + 2].toInt() and 0xff) shl 8) or
              (bytes[offset + 3].toInt() and 0xff)
          if (value in 42..0x3fffff) add(value)
        }
      }
    }
  }

  private fun RcLinkedNode.operation(): RcOperation =
    when (this) {
      is RcLinkedNode.Operation -> operation
      is RcLinkedNode.Container -> operation
    }

  private data class Frame(
    val operation: RcOperation,
    val parent: MutableList<RcLinkedNode>,
    val children: MutableList<RcLinkedNode>,
  )

  private val containerStartOpcodes =
    setOf(
      RcOpcodes.CANVAS_OPERATIONS,
      RcOpcodes.MODIFIER_CLICK,
      RcOpcodes.MODIFIER_MULTI_CLICK,
      RcOpcodes.MODIFIER_TOUCH_DOWN,
      RcOpcodes.MODIFIER_TOUCH_UP,
      RcOpcodes.MODIFIER_TOUCH_CANCEL,
      RcOpcodes.MODIFIER_SCROLL,
      RcOpcodes.RUN_ACTION,
      RcOpcodes.LAYOUT_CANVAS_CONTENT,
      RcOpcodes.FUNCTION_DEFINE,
      RcOpcodes.IMPULSE_START,
      RcOpcodes.IMPULSE_PROCESS,
      RcOpcodes.CONDITIONAL_OPERATIONS,
      RcOpcodes.LOOP_START,
      RcOpcodes.PARTICLE_LOOP,
      RcOpcodes.PARTICLE_COMPARE,
      RcOpcodes.LAYOUT_ROOT,
      RcOpcodes.LAYOUT_CONTENT,
      RcOpcodes.LAYOUT_CUSTOM,
      RcOpcodes.LAYOUT_BOX,
      RcOpcodes.LAYOUT_ROW,
      RcOpcodes.LAYOUT_COLUMN,
      RcOpcodes.LAYOUT_STATE,
      RcOpcodes.LAYOUT_FLOW,
      RcOpcodes.LAYOUT_COLLAPSIBLE_ROW,
      RcOpcodes.LAYOUT_COLLAPSIBLE_COLUMN,
      RcOpcodes.LAYOUT_COMPUTE,
      RcOpcodes.LAYOUT_CANVAS,
      RcOpcodes.LAYOUT_FIT_BOX,
      RcOpcodes.LAYOUT_IMAGE,
      RcOpcodes.LAYOUT_TEXT,
      RcOpcodes.CORE_TEXT,
      RcOpcodes.COMPONENT_START,
      RcOpcodes.REFERENCED_OPERATIONS,
      RcOpcodes.MACRO_FOR_EACH,
      RcOpcodes.MACRO_CALL,
      RcOpcodes.MACRO_BLOCK,
    )
}

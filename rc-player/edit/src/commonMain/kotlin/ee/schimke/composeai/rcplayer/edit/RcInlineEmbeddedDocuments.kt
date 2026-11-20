package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBinaryData
import ee.schimke.composeai.rcplayer.protocol.RcBooleanConstant
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIdRemapper
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcMapRcDocVariables
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootContentBehavior
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTheme
import ee.schimke.composeai.rcplayer.protocol.RcValueStringChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcplayer.protocol.RcWireWriter

/**
 * Inlines statically resolvable `LAYOUT_RC_DOC` components into this document, up to [maxDepth]
 * levels deep (defaulting to `1`), eliminating nested player overhead and allowing [optimize] to
 * fold constants across the parent/child boundary.
 *
 * An embedded document is inlined when:
 * - its `urlId` statically refers to an inline [RcBinaryData] (`TYPE_RC_DOC`, directly or via a
 *   constant `"rc:embedded/<id>"` text) or a constant URL resolved by [resolver], and `urlId` is
 *   not bound as a named variable or mutated by a string-change action;
 * - all [RcMapRcDocVariables] targeting it use `MAP_FORWARD` (reverse, bidirectional and
 *   host-action mappings require runtime cross-document synchronization and are left un-inlined);
 * - the child decodes to a balanced layout document (`RootLayout` -> `LayoutContent`) with no
 *   `RootContentBehavior` or `Theme` sections.
 *
 * Any `RcDocLayout` that does not meet these conditions is left untouched.
 */
public fun RcDocument.inlineEmbeddedDocuments(
  maxDepth: Int = 1,
  resolver: ((String) -> ByteArray?)? = null,
): RcDocument {
  require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
  if (maxDepth == 0 || operations.none { it is RcDocLayout }) return this
  var current = this
  repeat(maxDepth) {
    val next = current.inlineEmbeddedOnce(resolver)
    if (next === current) return current
    current = next
  }
  return current
}

private const val INLINE_FIRST_DOC_ID: Int = 42
private const val INLINE_ID_MASK: Int = 0x003fffff
private const val INLINE_ID_REGION_MASK: Int = 0x00c00000
private const val INLINE_ID_REGION_ARRAY: Int = 0x00400000

private fun RcDocument.inlineEmbeddedOnce(resolver: ((String) -> ByteArray?)?): RcDocument {
  if (operations.none { it is RcDocLayout }) return this
  val tree = operationTree(operations) ?: return this

  val binaryDocs = mutableMapOf<Int, RcBinaryData>()
  val texts = mutableMapOf<Int, String>()
  val mutatedIds = mutableSetOf<Int>()
  val mappingsByDoc = mutableMapOf<Int, MutableList<RcMapRcDocVariables>>()
  val usedIds = mutableSetOf<Int>()

  for (op in operations) {
    when (op) {
      is RcBinaryData -> {
        usedIds += op.id
        if (op.type == RcBinaryData.TYPE_RC_DOC) binaryDocs[op.id] = op
      }
      is RcTextData -> {
        usedIds += op.id
        texts[op.id] = op.text
      }
      is RcNamedVariable -> {
        usedIds += op.id
        mutatedIds += op.id
      }
      is RcValueStringChangeAction -> mutatedIds += op.targetValueId
      is RcMapRcDocVariables -> mappingsByDoc.getOrPut(op.docId) { mutableListOf() } += op
      else -> Unit
    }
    op.declaredComponentId()?.let { usedIds += it }
    op.mentionedValues()?.forEach { usedIds += (it and INLINE_ID_MASK) }
  }

  var nextFreshId =
    (usedIds.maxOrNull() ?: INLINE_FIRST_DOC_ID).coerceAtLeast(INLINE_FIRST_DOC_ID) + 1
  fun allocateId(): Int {
    while (nextFreshId in usedIds) nextFreshId++
    val id = nextFreshId++
    usedIds += id
    return id
  }

  val hoistedDefs = mutableListOf<RcOperation>()
  val replacements = mutableMapOf<Int, Pair<Int, List<RcOperation>>>()
  val inlinedDocIds = mutableSetOf<Int>()

  fun visit(node: RcOperationNode) {
    val op = node.operation
    if (op is RcDocLayout) {
      val mappings = mappingsByDoc[op.componentId].orEmpty()
      val allForward = mappings.all { it.mode == RcMapRcDocVariables.MAP_FORWARD }
      if (allForward && op.urlId !in mutatedIds) {
        val childBytes = resolveStaticChildBytes(op.urlId, binaryDocs, texts, resolver)
        val child = childBytes?.let { runCatching { RcDocumentCodec.decode(it) }.getOrNull() }
        if (child != null && isInlinableLayoutChild(child)) {
          val inlined =
            inlineChild(
              docOp = op,
              docNode = node,
              parentOps = operations,
              mappings = mappings,
              parentTexts = texts,
              child = child,
              usedIds = usedIds,
              allocateId = ::allocateId,
            )
          if (inlined != null) {
            hoistedDefs += inlined.hoistedDefs
            replacements[node.start] = node.end to inlined.replacementOps
            inlinedDocIds += op.componentId
            return
          }
        }
      }
    }
    node.children.forEach(::visit)
  }

  tree.forEach(::visit)
  if (replacements.isEmpty()) return this

  // Rebuild operation stream: hoist child data defs before parent's RootLayout, splice inlined
  // boxes in place of RcDocLayout containers, drop consumed MapRcDocVariables and unreferenced
  // inline BinaryData blobs.
  val rootIndex = operations.indexOfFirst { it is RcRootLayout }.let { if (it < 0) 0 else it }
  val rebuilt = ArrayList<RcOperation>(operations.size + hoistedDefs.size + 16)
  var index = 0
  while (index < operations.size) {
    if (index == rootIndex) {
      rebuilt += hoistedDefs
    }
    val replacement = replacements[index]
    if (replacement != null) {
      rebuilt += replacement.second
      index = replacement.first + 1
      continue
    }
    val op = operations[index]
    if (op is RcMapRcDocVariables && op.docId in inlinedDocIds) {
      index++
      continue
    }
    rebuilt += op
    index++
  }

  // Drop BinaryData(TYPE_RC_DOC) blobs no longer referenced by any remaining RcDocLayout.
  val referencedBlobIds = mutableSetOf<Int>()
  for (op in rebuilt) {
    if (op is RcDocLayout) {
      referencedBlobIds += op.urlId
      val url = texts[op.urlId]
      if (url != null && url.startsWith(RcDocLayout.EMBEDDED_URL_PREFIX)) {
        url.substring(RcDocLayout.EMBEDDED_URL_PREFIX.length).trim().toIntOrNull()?.let {
          referencedBlobIds += it
        }
      }
    }
  }
  val finalOps = rebuilt.filterNot {
    it is RcBinaryData && it.type == RcBinaryData.TYPE_RC_DOC && it.id !in referencedBlobIds
  }
  return copy(operations = finalOps)
}

private fun resolveStaticChildBytes(
  urlId: Int,
  binaryDocs: Map<Int, RcBinaryData>,
  texts: Map<Int, String>,
  resolver: ((String) -> ByteArray?)?,
): ByteArray? {
  binaryDocs[urlId]?.let {
    return it.data
  }
  val url = texts[urlId] ?: return null
  if (url.startsWith(RcDocLayout.EMBEDDED_URL_PREFIX)) {
    val id = url.substring(RcDocLayout.EMBEDDED_URL_PREFIX.length).trim().toIntOrNull()
    if (id != null) return binaryDocs[id]?.data
  }
  return resolver?.invoke(url)
}

private fun isInlinableLayoutChild(child: RcDocument): Boolean {
  if (child.operations.any { it is RcRootContentBehavior || it is RcTheme }) return false
  val tree = operationTree(child.operations) ?: return false
  val roots = tree.filter { it.operation is RcRootLayout }
  if (roots.size != 1) return false
  val root = roots.single()
  val contents = root.children.filter { it.operation is RcLayoutContent }
  return contents.size == 1
}

private class InlinedChild(
  val hoistedDefs: List<RcOperation>,
  val replacementOps: List<RcOperation>,
)

private fun inlineChild(
  docOp: RcDocLayout,
  docNode: RcOperationNode,
  parentOps: List<RcOperation>,
  mappings: List<RcMapRcDocVariables>,
  parentTexts: Map<Int, String>,
  child: RcDocument,
  usedIds: MutableSet<Int>,
  allocateId: () -> Int,
): InlinedChild? {
  // Map remote variable name -> parent localId for MAP_FORWARD.
  val forwardByName = mutableMapOf<String, Int>()
  for (mapping in mappings) {
    val remoteName = parentTexts[mapping.remoteTarget] ?: continue
    forwardByName[remoteName] = mapping.localId
  }

  val mappedChildVarIds = mutableMapOf<Int, Int>()
  for (op in child.operations) {
    if (op is RcNamedVariable) {
      val parentLocalId = forwardByName[op.name]
      if (parentLocalId != null) {
        mappedChildVarIds[op.id] = parentLocalId
      }
    }
  }

  // Collect all remappable ids in the child document and assign fresh non-colliding ids.
  val childIds = linkedSetOf<Int>()
  for (op in child.operations) {
    op.declaredComponentId()?.let { if (it != -1) childIds += it }
    op.mentionedValues()?.forEach { raw ->
      if (isRemappableChildId(raw)) childIds += (raw and INLINE_ID_MASK)
    }
    if (op is RcBinaryData && isRemappableChildId(op.id)) childIds += op.id
  }

  val baseMapping = mutableMapOf<Int, Int>()
  for ((childVarId, parentLocalId) in mappedChildVarIds) {
    baseMapping[childVarId] = parentLocalId
  }
  for (id in childIds) {
    if (id !in baseMapping) {
      baseMapping[id] = allocateId()
    }
  }
  val fullMapping = buildMap {
    for ((from, to) in baseMapping) {
      put(from, to)
      if (from >= 0) put(from or INLINE_ID_REGION_ARRAY, to or INLINE_ID_REGION_ARRAY)
    }
  }

  // Filter out the child's own declarations for forward-mapped variables (the parent owns localId).
  val filteredChildOps =
    child.operations.filterNot { op ->
      when (op) {
        is RcNamedVariable -> op.id in mappedChildVarIds
        is RcTextData -> op.id in mappedChildVarIds
        is RcFloatConstant -> op.id in mappedChildVarIds
        is RcIntegerConstant -> op.id in mappedChildVarIds
        is RcColorConstant -> op.id in mappedChildVarIds
        is RcBooleanConstant -> op.id in mappedChildVarIds
        is RcLongConstant -> op.id in mappedChildVarIds
        else -> false
      }
    }

  val remappedChildOps = remapChildOperations(filteredChildOps, fullMapping, usedIds)
  val remappedTree = operationTree(remappedChildOps) ?: return null
  val rootNode = remappedTree.singleOrNull { it.operation is RcRootLayout } ?: return null
  val contentNode =
    rootNode.children.singleOrNull { it.operation is RcLayoutContent } ?: return null

  val hoistedDefs = mutableListOf<RcOperation>()
  for (topNode in remappedTree) {
    if (topNode === rootNode) continue
    for (i in topNode.start..topNode.end) hoistedDefs += remappedChildOps[i]
  }

  val childBodyOps = mutableListOf<RcOperation>()
  for (childNode in contentNode.children) {
    for (i in childNode.start..childNode.end) childBodyOps += remappedChildOps[i]
  }

  // Keep the RcDocLayout's modifiers, applying intrinsic width/height when unspecified or WRAP,
  // and clip to bounds just as RcDocLayout does.
  val modifierOps = mutableListOf<RcOperation>()
  var hasExactWidth = false
  var hasExactHeight = false
  var hasClipRect = false
  for (modNode in docNode.children) {
    val modOp = modNode.operation
    if (modOp is RcWidthModifier && modOp.type != RcDimensionType.WRAP) hasExactWidth = true
    if (modOp is RcHeightModifier && modOp.type != RcDimensionType.WRAP) hasExactHeight = true
    if (modOp is RcClipRectModifier) hasClipRect = true
    for (i in modNode.start..modNode.end) modifierOps += parentOps[i]
  }

  val freshContentId = allocateId()
  val replacementOps = mutableListOf<RcOperation>()
  replacementOps +=
    RcBoxLayout(
      componentId = docOp.componentId,
      animationId = docOp.animationId,
      horizontalPositioning = 0,
      verticalPositioning = 0,
    )
  replacementOps += modifierOps
  if (!hasExactWidth && child.header.width > 0) {
    replacementOps +=
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(child.header.width.toFloat()))
  }
  if (!hasExactHeight && child.header.height > 0) {
    replacementOps +=
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(child.header.height.toFloat()))
  }
  if (!hasClipRect) {
    replacementOps += RcClipRectModifier
  }
  replacementOps += RcLayoutContent(componentId = freshContentId)
  replacementOps += childBodyOps
  replacementOps += RcNoArg(RcOpcodes.CONTAINER_END)
  replacementOps += RcNoArg(RcOpcodes.CONTAINER_END)

  return InlinedChild(hoistedDefs = hoistedDefs, replacementOps = replacementOps)
}

private fun isRemappableChildId(value: Int): Boolean =
  if (value < 0) value != -1
  else
    (value and INLINE_ID_REGION_MASK).let { it == 0 || it == INLINE_ID_REGION_ARRAY } &&
      (value and INLINE_ID_MASK) >= INLINE_FIRST_DOC_ID

private fun remapChildOperations(
  operations: List<RcOperation>,
  mapping: Map<Int, Int>,
  reserved: Set<Int>,
): List<RcOperation> {
  val writer = RcWireWriter()
  operations.forEach { RcDocumentCodec.encodeOperation(writer, it) }
  val bytes = writer.toByteArray()
  return RcDocumentCodec.decodeOperations(
    bytes,
    RcWireLimits(maxBlobBytes = maxOf(bytes.size, RcWireLimits().maxBlobBytes)),
    RcIdRemapper.expanding(mapping, reserved),
  )
}

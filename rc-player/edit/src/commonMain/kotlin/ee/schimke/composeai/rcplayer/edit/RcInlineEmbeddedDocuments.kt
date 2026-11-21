package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBinaryData
import ee.schimke.composeai.rcplayer.protocol.RcBooleanConstant
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFitBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFlowLayout
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIdRemapper
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
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
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcTheme
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatExpressionChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerExpressionChangeAction
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
private const val INLINE_OPERATOR_BASE: Int = 0x300000
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
    op.declaredComponentId()?.let { if (it != -1) usedIds += it }
    op.mentionedValues()?.forEach { raw -> if (raw != -1) usedIds += (raw and INLINE_ID_MASK) }
  }

  var nextFreshId = INLINE_FIRST_DOC_ID
  fun allocateId(): Int? {
    while (nextFreshId < INLINE_OPERATOR_BASE && nextFreshId in usedIds) nextFreshId++
    if (nextFreshId >= INLINE_OPERATOR_BASE) return null
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
  val rebuiltTexts = buildMap {
    for (op in rebuilt) {
      if (op is RcTextData) put(op.id, op.text)
    }
  }
  val referencedBlobIds = mutableSetOf<Int>()
  for (op in rebuilt) {
    if (op is RcDocLayout) {
      referencedBlobIds += op.urlId
      val url = rebuiltTexts[op.urlId]
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
  allocateId: () -> Int?,
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

  // Keep forward-mapped child writes isolated from the parent: if any child action or nested
  // mapping writes to a forward-mapped variable, aliasing it to parentLocalId would leak writes
  // back into parent state. Leave such children un-inlined.
  if (mappedChildVarIds.isNotEmpty()) {
    val writtenChildIds = childWrittenVariableIds(child.operations)
    if (mappedChildVarIds.keys.any { (it and INLINE_ID_MASK) in writtenChildIds }) {
      return null
    }
  }

  val origTree = operationTree(child.operations) ?: return null
  val origRootNode = origTree.singleOrNull { it.operation is RcRootLayout } ?: return null
  val origRootId = (origRootNode.operation as RcRootLayout).componentId
  val hasChildRootModifiers = origRootNode.children.size > 1

  // Collect component IDs and resource IDs separately:
  // - component IDs can be in 0..41 (overlapping system variables like CONTINUOUS_SEC) or negative,
  //   so they are remapped in a dedicated component-operation pass rather than through
  //   RcIdRemapper (which would otherwise rewrite system-variable float words);
  // - resource IDs are strictly in [42, 0x300000), excluding both system variables (< 42) and
  //   NaN-encoded float/matrix expression operators (>= 0x300000).
  val childComponentIds = linkedSetOf<Int>()
  val childResourceIds = linkedSetOf<Int>()
  for (op in child.operations) {
    op.declaredComponentId()?.let { if (it != -1) childComponentIds += it }
    when (op) {
      is RcComponentValue -> if (op.componentId != -1) childComponentIds += op.componentId
      is RcMapRcDocVariables -> if (op.docId != -1) childComponentIds += op.docId
      else -> Unit
    }
    op.mentionedValues()?.forEach { raw ->
      if (isRemappableResourceId(raw)) childResourceIds += (raw and INLINE_ID_MASK)
    }
    if (op is RcBinaryData && isRemappableResourceId(op.id)) childResourceIds += op.id
  }
  childResourceIds.removeAll(childComponentIds)

  val componentMapping = mutableMapOf<Int, Int>()
  // When the child root has no modifiers of its own, the outer replacement RcBoxLayout takes its
  // place directly; alias the child root's componentId to docOp.componentId so RcComponentValue
  // bindings to the child root still receive its geometry.
  if (!hasChildRootModifiers && origRootId != -1 && docOp.componentId != -1) {
    componentMapping[origRootId] = docOp.componentId
  }
  for (id in childComponentIds) {
    if (id !in componentMapping) {
      componentMapping[id] = allocateId() ?: return null
    }
  }

  val resourceMapping = mutableMapOf<Int, Int>()
  for ((childVarId, parentLocalId) in mappedChildVarIds) {
    if (isRemappableResourceId(childVarId)) {
      resourceMapping[childVarId] = parentLocalId
    }
  }
  for (id in childResourceIds) {
    if (id !in resourceMapping) {
      resourceMapping[id] = allocateId() ?: return null
    }
  }
  val fullResourceMapping = buildMap {
    for ((from, to) in resourceMapping) {
      put(from, to)
      put(from or INLINE_ID_REGION_ARRAY, to or INLINE_ID_REGION_ARRAY)
    }
  }

  // Strip all RcNamedVariable declarations from the child:
  // - forward-mapped variables are owned by the parent (their id is aliased to parentLocalId);
  // - unmapped child named variables must not enter the parent's variableNames namespace (their
  //   default constant declarations remain with fresh ids, matching isolated runtime execution).
  val filteredChildOps =
    child.operations.filterNot { op ->
      when (op) {
        is RcNamedVariable -> true
        is RcTextData -> op.id in mappedChildVarIds
        is RcFloatConstant -> op.id in mappedChildVarIds
        is RcIntegerConstant -> op.id in mappedChildVarIds
        is RcColorConstant -> op.id in mappedChildVarIds
        is RcBooleanConstant -> op.id in mappedChildVarIds
        is RcLongConstant -> op.id in mappedChildVarIds
        else -> false
      }
    }

  val remappedChildOps =
    remapComponentIds(
      remapChildOperations(filteredChildOps, fullResourceMapping, usedIds),
      componentMapping,
    )
  val remappedTree = operationTree(remappedChildOps) ?: return null
  val rootNode = remappedTree.singleOrNull { it.operation is RcRootLayout } ?: return null
  val contentNode =
    rootNode.children.singleOrNull { it.operation is RcLayoutContent } ?: return null
  val remappedRootId = (rootNode.operation as RcRootLayout).componentId
  val remappedContentId = (contentNode.operation as RcLayoutContent).componentId

  val hoistedDefs = mutableListOf<RcOperation>()
  for (topNode in remappedTree) {
    if (topNode === rootNode) continue
    for (i in topNode.start..topNode.end) hoistedDefs += remappedChildOps[i]
  }

  val childRootModifierOps = mutableListOf<RcOperation>()
  var hasRootWidth = false
  var hasRootHeight = false
  for (rootChild in rootNode.children) {
    if (rootChild === contentNode) continue
    val op = rootChild.operation
    if (op is RcWidthModifier) hasRootWidth = true
    if (op is RcHeightModifier) hasRootHeight = true
    for (i in rootChild.start..rootChild.end) childRootModifierOps += remappedChildOps[i]
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

  val outerComponentId =
    if (docOp.componentId != -1) docOp.componentId
    else if (!hasChildRootModifiers && remappedRootId != -1) remappedRootId else -1
  val replacementOps = mutableListOf<RcOperation>()
  replacementOps +=
    RcBoxLayout(
      componentId = outerComponentId,
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
  if (childRootModifierOps.isNotEmpty()) {
    val outerContentId = allocateId() ?: return null
    val innerRootId = if (remappedRootId != -1) remappedRootId else (allocateId() ?: return null)
    val innerContentId =
      if (remappedContentId != -1) remappedContentId else (allocateId() ?: return null)
    replacementOps += RcLayoutContent(componentId = outerContentId)
    replacementOps +=
      RcBoxLayout(
        componentId = innerRootId,
        animationId = -1,
        horizontalPositioning = 0,
        verticalPositioning = 0,
      )
    if (!hasRootWidth) {
      replacementOps += RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f))
    }
    if (!hasRootHeight) {
      replacementOps += RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f))
    }
    replacementOps += childRootModifierOps
    replacementOps += RcLayoutContent(componentId = innerContentId)
    replacementOps += childBodyOps
    replacementOps += RcNoArg(RcOpcodes.CONTAINER_END)
    replacementOps += RcNoArg(RcOpcodes.CONTAINER_END)
  } else {
    val contentId =
      if (remappedContentId != -1) remappedContentId else (allocateId() ?: return null)
    replacementOps += RcLayoutContent(componentId = contentId)
    replacementOps += childBodyOps
  }
  replacementOps += RcNoArg(RcOpcodes.CONTAINER_END)
  replacementOps += RcNoArg(RcOpcodes.CONTAINER_END)

  return InlinedChild(hoistedDefs = hoistedDefs, replacementOps = replacementOps)
}

private fun childWrittenVariableIds(operations: List<RcOperation>): Set<Int> {
  val written = mutableSetOf<Int>()
  for (op in operations) {
    when (op) {
      is RcValueIntegerChangeAction -> written += (op.targetValueId and INLINE_ID_MASK)
      is RcValueStringChangeAction -> written += (op.targetValueId and INLINE_ID_MASK)
      is RcValueFloatChangeAction -> written += (op.targetValueId and INLINE_ID_MASK)
      is RcValueFloatExpressionChangeAction -> written += (op.targetValueId and INLINE_ID_MASK)
      is RcValueIntegerExpressionChangeAction ->
        written += (op.targetValueId and INLINE_ID_MASK.toLong()).toInt()
      is RcTouchExpression -> written += (op.id and INLINE_ID_MASK)
      is RcMapRcDocVariables ->
        if (op.mode != RcMapRcDocVariables.MAP_FORWARD) {
          written += (op.localId and INLINE_ID_MASK)
        }
      else -> Unit
    }
  }
  return written
}

private fun isRemappableResourceId(value: Int): Boolean {
  if (value < 0) return false
  val region = value and INLINE_ID_REGION_MASK
  if (region != 0 && region != INLINE_ID_REGION_ARRAY) return false
  val base = value and INLINE_ID_MASK
  return base in INLINE_FIRST_DOC_ID until INLINE_OPERATOR_BASE
}

private fun remapComponentIds(
  operations: List<RcOperation>,
  mapping: Map<Int, Int>,
): List<RcOperation> {
  if (mapping.isEmpty()) return operations
  fun map(id: Int): Int = mapping[id] ?: id
  return operations.map { op ->
    when (op) {
      is RcRootLayout -> op.copy(componentId = map(op.componentId))
      is RcLayoutContent -> op.copy(componentId = map(op.componentId))
      is RcCanvasLayout -> op.copy(componentId = map(op.componentId))
      is RcCanvasContent -> op.copy(componentId = map(op.componentId))
      is RcCustomLayout -> op.copy(componentId = map(op.componentId))
      is RcBoxLayout -> op.copy(componentId = map(op.componentId))
      is RcRowLayout -> op.copy(componentId = map(op.componentId))
      is RcColumnLayout -> op.copy(componentId = map(op.componentId))
      is RcFlowLayout -> op.copy(componentId = map(op.componentId))
      is RcStateLayout -> op.copy(componentId = map(op.componentId))
      is RcCollapsibleRowLayout -> op.copy(componentId = map(op.componentId))
      is RcCollapsibleColumnLayout -> op.copy(componentId = map(op.componentId))
      is RcFitBoxLayout -> op.copy(componentId = map(op.componentId))
      is RcImageLayout -> op.copy(componentId = map(op.componentId))
      is RcDocLayout -> op.copy(componentId = map(op.componentId))
      is RcTextLayout -> op.copy(componentId = map(op.componentId))
      is RcCoreText -> {
        val oldId = op.componentId
        val newId = mapping[oldId]
        if (newId != null && newId != oldId) {
          op.copy(
            properties =
              op.properties.map { prop ->
                if (prop is RcTextStyleProperty.IntValue && prop.id == 1) {
                  prop.copy(value = newId)
                } else {
                  prop
                }
              },
          )
        } else {
          op
        }
      }
      is RcComponentValue -> op.copy(componentId = map(op.componentId))
      is RcMapRcDocVariables -> op.copy(docId = map(op.docId))
      else -> op
    }
  }
}

private fun remapChildOperations(
  operations: List<RcOperation>,
  mapping: Map<Int, Int>,
  reserved: Set<Int>,
): List<RcOperation> {
  val writer = RcWireWriter()
  operations.forEach { RcDocumentCodec.encodeOperation(writer, it) }
  val bytes = writer.toByteArray()
  val decoded =
    RcDocumentCodec.decodeOperations(
      bytes,
      RcWireLimits(maxBlobBytes = maxOf(bytes.size, RcWireLimits().maxBlobBytes)),
      RcIdRemapper.expanding(mapping, reserved),
    )
  // Rewrite nested `"rc:embedded/<id>"` text references so they track remapped BinaryData ids.
  return decoded.map { op ->
    if (op is RcTextData && op.text.startsWith(RcDocLayout.EMBEDDED_URL_PREFIX)) {
      val oldId = op.text.substring(RcDocLayout.EMBEDDED_URL_PREFIX.length).trim().toIntOrNull()
      val newId = if (oldId != null) mapping[oldId] else null
      if (newId != null && newId != oldId) {
        op.copy(text = "${RcDocLayout.EMBEDDED_URL_PREFIX}$newId")
      } else {
        op
      }
    } else {
      op
    }
  }
}

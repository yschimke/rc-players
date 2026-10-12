package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBooleanConstant
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFitBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFlowLayout
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIdMap
import ee.schimke.composeai.rcplayer.protocol.RcIdRemapper
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
import ee.schimke.composeai.rcplayer.protocol.RcMatrixConstant
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcplayer.protocol.RcWireWriter

/** Two documents as the two branches of one `StateLayout` — see [combineAsStates]. */
public data class RcCombinedDocument(
  val document: RcDocument,
  /** The integer named value choosing the branch: 0 shows the first document, 1 the second. */
  val stateName: String,
  /** The component pairs that move between the branches rather than fading. */
  val sharedElements: List<RcSharedElement>,
  /** How many of the second document's definitions now read the first's identical ones. */
  val sharedDefinitions: Int,
)

/**
 * A component of each document given one animation id, so that switching branches animates its
 * bounds from where it is in one to where it is in the other.
 */
public data class RcSharedElement(
  val animationId: Int,
  /** The component's id in the combined document's first branch. */
  val firstComponentId: Int,
  /** The component's id in the combined document's second branch. */
  val secondComponentId: Int,
)

/**
 * This document and [other] as the two branches of one `StateLayout`, switched by the integer named
 * value [stateName] (`USER:` prefixed): 0 shows this document, 1 shows [other], and the player
 * animates between them.
 *
 * - **Ids.** [other]'s ids are moved clear of this document's, so the two never read each other's
 *   state. Ids below 42 — the player's system variables — stay as they are, shared.
 * - **Shared definitions.** A definition in [other] identical to one here, and written by nothing
 *   but its definition in either, is not copied: [other] reads this document's. That is text,
 *   bitmaps, fonts, paths, constants, lists and the expressions over them, so a bitmap both screens
 *   show is carried once. A named variable both declare, with the same name and type, is shared too
 *   when it has the same default, so one host binding drives both screens.
 * - **Shared elements.** Each pair in [matches] — a component id here and one in [other] — and,
 *   with [matchByContent], each text and image the two documents show exactly once each (the same
 *   text, the same bitmap), gets one animation id: the player moves it between its two positions
 *   instead of fading one out and the other in. Everything else cross-fades with its branch.
 *
 * Each document's content is laid out in its branch as its root laid it out, filling the root. The
 * combined header is the newer of the two. Throws [IllegalArgumentException] when either document
 * is not one root layout around its content.
 */
public fun RcDocument.combineAsStates(
  other: RcDocument,
  stateName: String = "rcState",
  matches: List<Pair<Int, Int>> = emptyList(),
  matchByContent: Boolean = true,
): RcCombinedDocument {
  val first = RcRootedDocument.of(this, "this document")
  val usedHere = operations.flatMapTo(mutableSetOf()) { it.mentionedValues() ?: emptySet() }
  val merge = RcDocumentMerge(this, other, usedHere)
  val second = RcRootedDocument.of(merge.document, "other")

  val ids =
    RcFreshIds(
      usedHere + merge.usedIds,
      componentIds(operations) + componentIds(merge.document.operations),
    )
  val stateIndex = ids.next()
  val stateComponent = ids.nextComponent()
  val stateContent = ids.nextComponent()

  val firstBranch = branch(first, ids)
  val secondBranch = branch(second, ids)
  val shared = mutableListOf<RcSharedElement>()
  val firstOperations = firstBranch.toMutableList()
  val secondOperations = secondBranch.toMutableList()
  val pairs =
    matches.map { (here, there) -> here to merge.componentId(there) } +
      if (matchByContent) contentMatches(firstBranch, secondBranch) else emptyList()
  var animationId = maxOf(animationIds(operations), animationIds(merge.document.operations)) + 1
  pairs
    .distinctBy { it.first }
    .distinctBy { it.second }
    .forEach { (here, there) ->
      if (
        firstOperations.setAnimationId(here, animationId) &&
          secondOperations.setAnimationId(there, animationId)
      ) {
        shared += RcSharedElement(animationId, here, there)
        animationId++
      }
    }

  val combined = buildList {
    addAll(first.before)
    addAll(second.before)
    add(RcIntegerConstant(stateIndex, 0))
    add(RcNamedVariable(stateIndex, RcNamedVariable.INT_TYPE, "USER:$stateName"))
    addAll(first.rootOpening)
    add(RcStateLayout(stateComponent, -1, START, TOP, stateIndex))
    add(RcLayoutContent(stateContent))
    addAll(firstOperations)
    addAll(secondOperations)
    add(END)
    add(END)
    addAll(first.rootClosing)
    addAll(first.after)
    addAll(second.after)
  }
  val header = if (other.header.isNewerThan(header)) other.header else header
  return RcCombinedDocument(
    RcDocument(header, combined),
    stateName,
    shared,
    merge.sharedDefinitions,
  )
}

/** A document split around its one root layout. */
private class RcRootedDocument(
  val before: List<RcOperation>,
  /** The root layout, its modifiers and its content wrapper, up to the content's children. */
  val rootOpening: List<RcOperation>,
  val content: List<RcOperation>,
  /** The content wrapper's and the root's `ContainerEnd`. */
  val rootClosing: List<RcOperation>,
  val after: List<RcOperation>,
) {
  companion object {
    fun of(document: RcDocument, name: String): RcRootedDocument {
      val operations = document.operations
      val tree = requireNotNull(operationTree(operations)) { "$name has unbalanced containers" }
      val roots = tree.filter { it.operation is RcRootLayout }
      require(roots.size == 1) { "$name has ${roots.size} root layouts, not one" }
      val root = roots.single()
      // AndroidX writes the root's components straight into it; a `LayoutContent` wrapper, where
      // there is one, is the root's only child.
      val wrapper = root.children.singleOrNull()?.takeIf { it.operation is RcLayoutContent }
      val contentStart = (wrapper ?: root).start + 1
      val contentEnd = (wrapper ?: root).end
      return RcRootedDocument(
        before = operations.subList(0, root.start),
        rootOpening = operations.subList(root.start, contentStart),
        content = operations.subList(contentStart, contentEnd),
        rootClosing = operations.subList(contentEnd, root.end + 1),
        after = operations.subList(root.end + 1, operations.size),
      )
    }
  }
}

/**
 * [document]'s content as one branch: a box around it that, like the state layout, takes its size
 * from what it holds, so the content measures against the root as it did on its own.
 */
private fun branch(document: RcRootedDocument, ids: RcFreshIds): List<RcOperation> =
  listOf(RcBoxLayout(ids.nextComponent(), -1, START, TOP), RcLayoutContent(ids.nextComponent())) +
    document.content +
    listOf(END, END)

/**
 * [other]'s operations with every id moved clear of [usedHere], except where a definition is shared
 * with [first]: then [other] reads [first]'s id and its own copy is dropped.
 */
private class RcDocumentMerge(first: RcDocument, other: RcDocument, usedHere: Set<Int>) {
  val document: RcDocument
  val sharedDefinitions: Int
  val usedIds: Set<Int>
  private val mapping: Map<Int, Int>

  init {
    val otherOperations = other.operations
    val candidates =
      otherOperations
        .flatMapTo(mutableSetOf()) { it.mentionedValues() ?: emptySet() }
        .filter(::isRemappableId)
        .mapTo(mutableSetOf()) { if (it < 0) it else it and ID_MASK }
    // Mentioned values over-report — any window of an operation's bytes — but only fields the
    // codec reads as ids are looked up, so a mapped value that is not an id is never touched.
    val fresh =
      RcFreshIds(
        usedHere + candidates,
        componentIds(first.operations) + componentIds(otherOperations),
      )
    val base = candidates.associateWith { if (it < 0) fresh.nextComponent() else fresh.next() }

    val firstDefinitions = shareableDefinitions(first.operations)
    val otherPinned = pinnedDefinitions(otherOperations)
    // [other]'s base ids read through to one of [first]'s.
    val shared = sharedNamedVariables(first.operations, otherOperations).toMutableMap()
    var remapped: List<RcOperation>
    while (true) {
      remapped = remap(otherOperations, idMapping(base, shared))
      val before = shared.size
      remapped.forEachIndexed { index, operation ->
        val original = otherOperations[index].definedIds()?.singleOrNull() ?: return@forEachIndexed
        val key = original and ID_MASK
        if (key in shared || original !in otherPinned || !operation.isShareable()) {
          return@forEachIndexed
        }
        val target = firstDefinitions[operation.withoutDefinedId()] ?: return@forEachIndexed
        shared[key] = target and ID_MASK
      }
      // A shared input can make an expression over it identical too: go round until none does.
      if (shared.size == before) break
    }
    val sharedTargets = shared.values.toSet()
    document =
      RcDocument(
        other.header,
        remapped.filterIndexed { index, operation ->
          val original = otherOperations[index].definedIds()?.singleOrNull()
          val now = operation.definedIds()?.singleOrNull()
          // [other]'s own copy of a definition it now reads from [first], and the name
          // declaration of a shared named variable, go.
          !(original != null &&
            (original and ID_MASK) in shared &&
            now != null &&
            (now and ID_MASK) in sharedTargets)
        },
      )
    sharedDefinitions = shared.size
    mapping = idMapping(base, shared)
    usedIds = document.operations.flatMapTo(mutableSetOf()) { it.mentionedValues() ?: emptySet() }
  }

  /** [other]'s component [id] as the combined document has it. */
  fun componentId(id: Int): Int = mapping[id] ?: id
}

/** Every value that may be one of a document's ids: not -1, not a system variable. */
private fun isRemappableId(value: Int): Boolean =
  if (value < 0) value != -1
  else
    (value and ID_REGION_MASK).let { it == 0 || it == ID_REGION_ARRAY } &&
      (value and ID_MASK) >= FIRST_DOCUMENT_ID

/** [base] with [shared] taking precedence, each id also in its array-region form. */
private fun idMapping(base: Map<Int, Int>, shared: Map<Int, Int>): Map<Int, Int> = buildMap {
  (base + shared).forEach { (from, to) ->
    put(from, to)
    if (from >= 0) put(from or ID_REGION_ARRAY, to or ID_REGION_ARRAY)
  }
}

/** [operations] re-read with [mapping] applied to every id field, as the codec knows them. */
private fun remap(operations: List<RcOperation>, mapping: Map<Int, Int>): List<RcOperation> {
  val writer = RcWireWriter()
  operations.forEach { RcDocumentCodec.encodeOperation(writer, it) }
  val bytes = writer.toByteArray()
  val reserved = mapping.values.toSet()
  return RcDocumentCodec.decodeOperations(
    bytes,
    RcWireLimits(maxBlobBytes = maxOf(bytes.size, RcWireLimits().maxBlobBytes)),
    RcIdRemapper.expanding(mapping, reserved),
  )
}

/**
 * This definition's bytes with its own id replaced by one fixed placeholder: two definitions with
 * the same key hold the same thing. Bytes, because a bitmap's or font's data class compares its
 * array by identity.
 */
private fun RcOperation.withoutDefinedId(): RcDefinitionKey {
  val id = definedIds()?.singleOrNull()
  val placed = if (id == null) this else remap(listOf(this), mapOf(id to PLACEHOLDER_ID)).single()
  return RcDefinitionKey(
    RcWireWriter().also { RcDocumentCodec.encodeOperation(it, placed) }.toByteArray()
  )
}

private class RcDefinitionKey(val bytes: ByteArray) {
  override fun equals(other: Any?): Boolean =
    other is RcDefinitionKey && bytes.contentEquals(other.bytes)

  override fun hashCode(): Int = bytes.contentHashCode()
}

/** Ids defined by exactly one unconditional operation and only ever read elsewhere. */
private fun pinnedDefinitions(operations: List<RcOperation>): Set<Int> {
  val unconditional = unconditionalOperations(operations) ?: return emptySet()
  val definers = mutableMapOf<Int, MutableList<Int>>()
  val mentions = mutableMapOf<Int, MutableList<Int>>()
  operations.forEachIndexed { index, operation ->
    operation.definedIds()?.forEach { definers.getOrPut(it) { mutableListOf() } += index }
    operation.mentionedValues()?.forEach { mentions.getOrPut(it) { mutableListOf() } += index }
  }
  return definers
    .filter { (id, at) ->
      val definer = at.singleOrNull() ?: return@filter false
      unconditional[definer] &&
        mentions[id].orEmpty().all { it == definer || operations[it].opcode in READ_ONLY_OPCODES }
    }
    .keys
}

/**
 * Each pinned, shareable top-level definition in [operations], keyed by its content without its id.
 */
private fun shareableDefinitions(operations: List<RcOperation>): Map<RcDefinitionKey, Int> {
  val pinned = pinnedDefinitions(operations)
  // Only top-level definitions: one inside a layout may only run while its branch is shown.
  val topLevel = operationTree(operations)?.filterNot { it.isContainer }?.map { it.start }.orEmpty()
  return buildMap {
    topLevel
      .map { operations[it] }
      .forEach { operation ->
        val id = operation.definedIds()?.singleOrNull() ?: return@forEach
        if (id in pinned && operation.isShareable()) getOrPut(operation.withoutDefinedId()) { id }
      }
  }
}

/**
 * Definitions that hold data, or derive it from other data, and nothing else: two screens reading
 * one copy can never see each other's state. Not touch, particle, loop or host-written values.
 */
private fun RcOperation.isShareable(): Boolean =
  this is RcTextData ||
    this is RcBitmapData ||
    this is RcFontData ||
    this is RcPathData ||
    this is RcFloatConstant ||
    this is RcColorConstant ||
    this is RcIntegerConstant ||
    this is RcBooleanConstant ||
    this is RcLongConstant ||
    this is RcFloatList ||
    this is RcIdList ||
    this is RcIdMap ||
    this is RcMatrixConstant ||
    this is RcFloatExpression ||
    this is RcIntegerExpression ||
    this is RcColorExpression ||
    this is RcColorTheme ||
    this is RcTextFromFloat ||
    this is RcTextMerge

/**
 * [other]'s named variables [first] declares too, with the same name, type and default, keyed by
 * base id: one host binding then drives both screens, and until a host sets it each screen shows
 * the default it showed alone. Defaults that differ keep the two apart.
 */
private fun sharedNamedVariables(
  first: List<RcOperation>,
  other: List<RcOperation>,
): Map<Int, Int> {
  fun defaults(operations: List<RcOperation>): Map<Int?, List<RcDefinitionKey>?> =
    operations
      .filterNot { it is RcNamedVariable }
      .groupBy { it.definedIds()?.singleOrNull() }
      .mapValues { (_, definers) ->
        if (definers.all { it.isConstantDefinition() }) definers.map { it.withoutDefinedId() }
        else null
      }
  val firstNames = first.filterIsInstance<RcNamedVariable>().associateBy { it.type to it.name }
  val firstDefaults = defaults(first)
  val otherDefaults = defaults(other)
  return buildMap {
    other.filterIsInstance<RcNamedVariable>().forEach { variable ->
      val match = firstNames[variable.type to variable.name] ?: return@forEach
      val default = otherDefaults[variable.id] ?: return@forEach
      if (default == firstDefaults[match.id]) put(variable.id and ID_MASK, match.id and ID_MASK)
    }
  }
}

private fun RcOperation.isConstantDefinition(): Boolean =
  this is RcTextData ||
    this is RcFloatConstant ||
    this is RcColorConstant ||
    this is RcIntegerConstant ||
    this is RcBooleanConstant ||
    this is RcLongConstant

/**
 * Allocates ids neither document uses: data ids upward from 42, skipping [used], and component ids
 * downward from below every one in [components].
 */
internal class RcFreshIds(private val used: Set<Int>, components: Collection<Int>) {
  private var nextId = FIRST_DOCUMENT_ID
  private var nextComponentId = minOf(-2, (components.filter { it < -1 }.minOrNull() ?: -1) - 1)

  fun next(): Int {
    while (nextId in used) nextId++
    check(nextId <= ID_MASK) { "the document has run out of ids" }
    return nextId++
  }

  fun nextComponent(): Int = nextComponentId--
}

/** Every component id [operations] declare, layout content and canvas content included. */
internal fun componentIds(operations: List<RcOperation>): List<Int> =
  operations.mapNotNull { operation ->
    operation.componentIdOrNull()
      ?: when (operation) {
        is RcRootLayout -> operation.componentId
        is RcLayoutContent -> operation.componentId
        is RcCanvasContent -> operation.componentId
        else -> null
      }
  }

/**
 * Text and image components [first] and [second] each show exactly once, with the same text or
 * bitmap id — the same content, once shared definitions have unified the ids.
 */
private fun contentMatches(
  first: List<RcOperation>,
  second: List<RcOperation>,
): List<Pair<Int, Int>> {
  fun keyed(operations: List<RcOperation>): Map<String, Int> =
    operations
      .mapNotNull { operation ->
        when (operation) {
          is RcTextLayout -> "text:${operation.textId}" to operation.componentId
          is RcCoreText -> "text:${operation.textId}" to operation.componentId
          is RcImageLayout -> "image:${operation.bitmapId}" to operation.componentId
          else -> null
        }
      }
      .groupBy({ it.first }, { it.second })
      .filterValues { it.size == 1 }
      .mapValues { it.value.single() }
  val here = keyed(first)
  val there = keyed(second)
  return here.mapNotNull { (key, component) -> there[key]?.let { component to it } }
}

/**
 * Gives component [componentId] [animationId], on its `AnimationSpec` modifier too if it has one.
 */
private fun MutableList<RcOperation>.setAnimationId(componentId: Int, animationId: Int): Boolean {
  val tree = operationTree(this) ?: return false
  var found: RcOperationNode? = null
  tree.forEach { top ->
    top.walk { if (found == null && it.operation.componentIdOrNull() == componentId) found = it }
  }
  val node = found ?: return false
  this[node.start] = node.operation.withAnimationId(animationId) ?: return false
  node.children
    .filter { it.operation is RcAnimationSpec }
    .forEach { this[it.start] = (it.operation as RcAnimationSpec).copy(animationId = animationId) }
  return true
}

private fun RcOperation.componentIdOrNull(): Int? =
  when (this) {
    is RcBoxLayout -> componentId
    is RcRowLayout -> componentId
    is RcColumnLayout -> componentId
    is RcFlowLayout -> componentId
    is RcFitBoxLayout -> componentId
    is RcCollapsibleRowLayout -> componentId
    is RcCollapsibleColumnLayout -> componentId
    is RcStateLayout -> componentId
    is RcCanvasLayout -> componentId
    is RcImageLayout -> componentId
    is RcTextLayout -> componentId
    is RcCustomLayout -> componentId
    is RcCoreText -> componentId
    else -> null
  }

private fun RcOperation.withAnimationId(id: Int): RcOperation? =
  when (this) {
    is RcBoxLayout -> copy(animationId = id)
    is RcRowLayout -> copy(animationId = id)
    is RcColumnLayout -> copy(animationId = id)
    is RcFlowLayout -> copy(animationId = id)
    is RcFitBoxLayout -> copy(animationId = id)
    is RcCollapsibleRowLayout -> copy(animationId = id)
    is RcCollapsibleColumnLayout -> copy(animationId = id)
    is RcStateLayout -> copy(animationId = id)
    is RcCanvasLayout -> copy(animationId = id)
    is RcImageLayout -> copy(animationId = id)
    is RcTextLayout -> copy(animationId = id)
    is RcCustomLayout -> copy(animationId = id)
    is RcCoreText ->
      copy(
        properties =
          properties.filterNot { it.id == CORE_TEXT_ANIMATION_ID } +
            RcTextStyleProperty.IntValue(CORE_TEXT_ANIMATION_ID, id)
      )
    else -> null
  }

/** The largest animation id [operations] already use, so new ones cannot collide. */
private fun animationIds(operations: List<RcOperation>): Int =
  operations.maxOfOrNull { operation ->
    when (operation) {
      is RcAnimationSpec -> operation.animationId
      is RcCoreText -> operation.animationId
      else -> operation.componentIdOrNull()?.let { operation.withAnimationIdValue() } ?: 0
    }
  } ?: 0

private fun RcOperation.withAnimationIdValue(): Int =
  when (this) {
    is RcBoxLayout -> animationId
    is RcRowLayout -> animationId
    is RcColumnLayout -> animationId
    is RcFlowLayout -> animationId
    is RcFitBoxLayout -> animationId
    is RcCollapsibleRowLayout -> animationId
    is RcCollapsibleColumnLayout -> animationId
    is RcStateLayout -> animationId
    is RcCanvasLayout -> animationId
    is RcImageLayout -> animationId
    is RcTextLayout -> animationId
    is RcCustomLayout -> animationId
    else -> 0
  }

private fun RcHeader.isNewerThan(other: RcHeader): Boolean {
  val mine = (apiLevel ?: 0) to (version.major * 1000 + version.minor)
  val theirs = (other.apiLevel ?: 0) to (other.version.major * 1000 + other.version.minor)
  return compareValuesBy(mine, theirs, { it.first }, { it.second }) > 0
}

private val END: RcOperation = RcNoArg(RcOpcodes.CONTAINER_END)

/** AndroidX box positioning: start, and top. */
private const val START = 1
private const val TOP = 4

/** `CoreText`'s animation id property. */
private const val CORE_TEXT_ANIMATION_ID = 2

private const val FIRST_DOCUMENT_ID = 42
private const val ID_MASK = 0x0fffff
private const val ID_REGION_MASK = 0x700000
private const val ID_REGION_ARRAY = 0x200000

/** An id no real document reaches, standing in for a definition's own id when comparing two. */
private const val PLACEHOLDER_ID = 0x0ffffe

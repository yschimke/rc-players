package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcClickArea
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcHostMetadataAction
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion

/**
 * How two versions of a document differ, structurally: [summary] says what changed in terms a
 * reviewer acts on, and [unified] is the line diff of the two [toStructuralText]s.
 *
 * Remote Compose ids are allocated in document order, so one value added early renumbers every id
 * after it, and a byte or line diff of the two shows that renumbering as a change to nearly every
 * line. The summary therefore compares what does not depend on ids — the header by property, text
 * by content, named values by name, embedded data by hash, operations by how many of each there are
 * — and the line diff shows the after document in the before's ids, matched along the lines the two
 * share, when that leaves fewer lines changed: what changed, rather than what was renumbered.
 */
public class RcStructuralDiff
internal constructor(
  /** What changed, one finding a line; empty when the documents are identical. */
  public val summary: List<String>,
  /** The unified diff of the two documents' structural text, with [context] lines of context. */
  public val unified: String,
) {
  /** Whether the two documents decode to the same operations. */
  public val isIdentical: Boolean
    get() = unified.isEmpty() && summary.isEmpty()

  /** The summary, then the unified diff, as `remote-compose-player --diff` prints them. */
  override fun toString(): String = buildString {
    if (isIdentical) {
      appendLine("identical")
      return@buildString
    }
    summary.forEach { appendLine("* $it") }
    if (unified.isNotEmpty()) {
      appendLine()
      append(unified)
    }
  }
}

/**
 * The structural difference from this document to [after]; [beforeName] and [afterName] label the
 * unified diff's `---` and `+++` lines.
 */
public fun RcDocument.structuralDiff(
  after: RcDocument,
  beforeName: String = "before",
  afterName: String = "after",
  context: Int = 3,
): RcStructuralDiff {
  val a = structuralLines(this)
  val b = structuralLines(after)
  val raw = diffLines(a.map { it.text }, b.map { it.text })
  if (raw.all { it.kind == EditKind.Same }) {
    return RcStructuralDiff(explain(this, after, LineChanges(0, 0)), "")
  }
  // Ids are allocated in document order, so one value added early renumbers every id after it.
  // Matched against the before document's ids, the after document's lines differ only where the
  // documents do.
  val matched = matchIds(a, b)
  val renumbered = diffLines(a.map { it.text }, matched.map { it.text })
  val changes = LineChanges(raw.changes(), renumbered.changes())
  val unified =
    if (changes.renumbered) unifiedDiff(a, matched, renumbered, beforeName, afterName, context)
    else unifiedDiff(a, b, raw, beforeName, afterName, context)
  return RcStructuralDiff(explain(this, after, changes), unified)
}

/**
 * How many lines differ as written ([raw]) and with the after document's ids matched ([matched]).
 */
private class LineChanges(val raw: Int, val matched: Int) {
  val renumbered: Boolean
    get() = matched < raw
}

private fun List<Edit>.changes(): Int = count { it.kind != EditKind.Same }

// A field naming an id (`id=`, `textId=`, `componentId=`, …), a field listing ids (`ids=[…]`,
// `argumentIds=[…]`), and a NaN-boxed reference to one.
private val ID_FIELD = Regex("""\b(\w*[iI]d)=(-?\d+)\b""")
private val ID_LIST = Regex("""\b(\w*[iI]ds)=\[([-\d, ]*)]""")
private val ID_REFERENCE = Regex("""@(\d+)\b""")
private val INTEGER = Regex("""-?\d+""")

// Below this are the player's system variables, and NaN words from 0x300000 up are expression
// operators: both mean the same in every document, so they are not ids to match.
private const val FIRST_DOCUMENT_ID = 42
private const val FIRST_OPERATOR_ID = 0x300000

/**
 * The fields of [operation] whose value the player hands to the host as it is — every raw id in an
 * `RcPlayerEvent`: `RcHostAction` and `RcHostMetadataAction` send their `actionId`, and a tap on an
 * `RcClickArea` sends its `id` (`HostActionMetadata`). A changed one is a changed behaviour, never
 * a renumbering, so they are never matched. `RcHostNamedAction` sends resolved values, not ids, so
 * its references match as usual.
 */
private fun hostFacingFields(operation: RcOperation?): Set<String> =
  when (operation) {
    is RcHostAction,
    is RcHostMetadataAction -> setOf("actionId")
    is RcClickArea -> setOf("id")
    else -> emptySet()
  }

/** An id written at [range] (its digits) of a line. */
private class IdUse(val range: IntRange, val value: Int)

/**
 * The document ids [text] names, in order.
 *
 * Read from the rendered text, so the rule leans one way: a missed id only leaves a renumbered line
 * in the diff, but a number wrongly taken for an id hides a real change. So an id field counts only
 * at the operation's top level — `id=`, `…Id=` and `…Ids=[…]` among its own fields, never inside a
 * nested value (a text style's properties, a shader's uniforms keyed by name, a map's entries) —
 * and never what a string says. A NaN-boxed `@` reference is a typed reference wherever it appears.
 * Never ids: 0 and -1 (no id), the player's system variables (1 to 41), operators, and what the
 * player hands to the host ([hostFacingFields]).
 */
private fun idUses(text: String, operation: RcOperation?): List<IdUse> {
  val hostFacing = hostFacingFields(operation)
  val uses = mutableListOf<IdUse>()
  val strings = quotedSpans(text)
  val depth = nestingDepth(text, strings)
  fun add(start: Int, digits: String) {
    if (strings.any { start in it }) return
    val value = digits.toIntOrNull() ?: return
    // Positive ids below the first document id are the player's system variables (window
    // size, time, …), the same in every document; negative ones are allocated component ids.
    if (value == -1 || value in 0 until FIRST_DOCUMENT_ID) return
    uses += IdUse(start until start + digits.length, value)
  }
  ID_FIELD.findAll(text).forEach { match ->
    if (depth[match.range.first] != 1 || match.groupValues[1] in hostFacing) return@forEach
    val digits = match.groupValues[2]
    add(match.range.last + 1 - digits.length, digits)
  }
  ID_LIST.findAll(text).forEach { match ->
    if (depth[match.range.first] != 1 || match.groupValues[1] in hostFacing) return@forEach
    val start = match.range.first + match.groupValues[1].length + 2
    INTEGER.findAll(match.groupValues[2]).forEach { add(start + it.range.first, it.value) }
  }
  ID_REFERENCE.findAll(text).forEach { match ->
    val digits = match.groupValues[1]
    val value = digits.toIntOrNull() ?: return@forEach
    if (value in FIRST_DOCUMENT_ID until FIRST_OPERATOR_ID) add(match.range.first + 1, digits)
  }
  return uses.sortedBy { it.range.first }
}

/**
 * How deep in brackets each character of [text] sits, outside [strings]: 1 inside an operation's
 * own parentheses, more inside a list, map or nested value.
 */
private fun nestingDepth(text: String, strings: List<IntRange>): IntArray {
  val depth = IntArray(text.length)
  var level = 0
  text.forEachIndexed { i, c ->
    val inString = strings.any { i in it }
    if (!inString && (c == ')' || c == ']' || c == '}')) level--
    depth[i] = level
    if (!inString && (c == '(' || c == '[' || c == '{')) level++
  }
  return depth
}

/** The spans of [text] inside `quote`d strings, quotes included, honouring `\\` escapes. */
private fun quotedSpans(text: String): List<IntRange> {
  val spans = mutableListOf<IntRange>()
  var start = -1
  var i = 0
  while (i < text.length) {
    when {
      start >= 0 && text[i] == '\\' -> i++
      text[i] == '"' ->
        if (start < 0) start = i
        else {
          spans += start..i
          start = -1
        }
    }
    i++
  }
  if (start >= 0) spans += start until text.length
  return spans
}

private fun String.withIds(uses: List<IdUse>, rename: (Int) -> String): String = buildString {
  var at = 0
  uses.forEach { use ->
    append(this@withIds, at, use.range.first)
    append(rename(use.value))
    at = use.range.last + 1
  }
  append(this@withIds, at, this@withIds.length)
}

/**
 * [after] in [before]'s ids. The two are aligned with every id masked; each pair of aligned lines
 * pairs their ids in order, and the first pairing of an id wins. An id the after document has with
 * no partner in the before prints primed (`57'`), so it cannot be read as one of the before's.
 *
 * Ids are read from field names and references, so an id packed into a raw word (paint data, an
 * integer expression's values) keeps its number.
 */
internal fun matchIds(
  before: List<RcStructuralLine>,
  after: List<RcStructuralLine>,
): List<RcStructuralLine> {
  val beforeUses = before.map { idUses(it.text, it.operation) }
  val afterUses = after.map { idUses(it.text, it.operation) }
  val alignment =
    diffLines(
      before.indices.map { before[it].text.withIds(beforeUses[it]) { "%" } },
      after.indices.map { after[it].text.withIds(afterUses[it]) { "%" } },
    )
  val toBefore = mutableMapOf<Int, Int>()
  val taken = mutableSetOf<Int>()
  alignment
    .filter { it.kind == EditKind.Same }
    .forEach { pair ->
      beforeUses[pair.aIndex].zip(afterUses[pair.bIndex]).forEach { (was, now) ->
        if (now.value !in toBefore && was.value !in taken) {
          toBefore[now.value] = was.value
          taken += was.value
        }
      }
    }
  return after.indices.map { index ->
    val line = after[index]
    if (afterUses[index].isEmpty()) line
    else
      RcStructuralLine(
        line.depth,
        line.text.withIds(afterUses[index]) { id -> toBefore[id]?.toString() ?: "$id'" },
        line.operation,
      )
  }
}

private fun explain(before: RcDocument, after: RcDocument, lines: LineChanges): List<String> =
  buildList {
    // The header, property by property.
    if (before.header.version != after.header.version) {
      add("version ${before.header.version.text()} -> ${after.header.version.text()}")
    }
    val headerBefore = before.header.properties.associate { it.key to it.value }
    val headerAfter = after.header.properties.associate { it.key to it.value }
    (headerBefore.keys + headerAfter.keys).sorted().forEach { key ->
      val was = headerBefore[key]
      val now = headerAfter[key]
      if (was != now) {
        add(
          "header ${headerKeyName(key)}: " +
            when {
              was == null -> "added (${now.text()})"
              now == null -> "removed (was ${was.text()})"
              else -> "${was.text()} -> ${now.text()}"
            },
        )
      }
    }

    // How many of each operation, which no renumbering changes.
    val countsBefore = before.operations.groupingBy { it.opcode }.eachCount()
    val countsAfter = after.operations.groupingBy { it.opcode }.eachCount()
    val counts =
      (countsBefore.keys + countsAfter.keys)
        .filter { it != RcOpcodes.CONTAINER_END }
        .sorted()
        .mapNotNull { opcode ->
          val delta = (countsAfter[opcode] ?: 0) - (countsBefore[opcode] ?: 0)
          if (delta == 0) null
          else (if (delta > 0) "+$delta " else "$delta ") + operationName(opcode)
        }
    if (before.operations.size != after.operations.size || counts.isNotEmpty()) {
      add(
        "operations ${before.operations.size} -> ${after.operations.size}" +
          if (counts.isEmpty()) "" else ": ${counts.joinToString(", ")}",
      )
    }

    // Values by what they are, not by the id they were given.
    multisetChange("text", before.texts(), after.texts())?.let(::add)
    multisetChange("named value", before.namedValues(), after.namedValues())?.let(::add)
    multisetChange("embedded data", before.embedded(), after.embedded())?.let(::add)

    when {
      lines.raw == 0 -> Unit
      lines.matched == 0 ->
        add("only ids differ: the same document with its ids allocated differently")
      lines.renumbered ->
        add(
          "${lines.matched} structural lines differ once ids are matched (${lines.raw} as " +
            "written); the line diff shows the after document in the before's ids, an id only " +
            "it has primed (57')",
        )
      else -> add("${lines.raw} structural lines differ")
    }
  }

private fun RcDocument.texts(): List<String> =
  operations.filterIsInstance<RcTextData>().map { quote(it.text) }

private fun RcDocument.namedValues(): List<String> =
  operations.filterIsInstance<RcNamedVariable>().map {
    val type =
      when (it.type) {
        RcNamedVariable.STRING_TYPE -> "string"
        RcNamedVariable.FLOAT_TYPE -> "float"
        RcNamedVariable.COLOR_TYPE -> "color"
        else -> "type ${it.type}"
      }
    "${quote(it.name)} ($type)"
  }

private fun RcDocument.embedded(): List<String> = operations.mapNotNull {
  when (it) {
    is RcBitmapData ->
      if (it.encoding == RcBitmapData.ENCODING_INLINE) {
        "image ${it.width}x${it.height} ${it.data.size} bytes fnv1a:${fnv1a64(it.data).hex()}"
      } else {
        "image ${quote(it.data.decodeToString())}"
      }
    is RcFontData -> "font ${it.data.size} bytes fnv1a:${fnv1a64(it.data).hex()}"
    else -> null
  }
}

/** "label added: …; removed: …" for what is in one list more often than in the other. */
private fun multisetChange(label: String, before: List<String>, after: List<String>): String? {
  val was = before.groupingBy { it }.eachCount()
  val now = after.groupingBy { it }.eachCount()
  val added = mutableListOf<String>()
  val removed = mutableListOf<String>()
  (was.keys + now.keys).sorted().forEach { key ->
    val delta = (now[key] ?: 0) - (was[key] ?: 0)
    repeat(maxOf(delta, 0)) { added += key }
    repeat(maxOf(-delta, 0)) { removed += key }
  }
  if (added.isEmpty() && removed.isEmpty()) return null
  return buildList {
      if (added.isNotEmpty()) add("added ${abbreviate(added)}")
      if (removed.isNotEmpty()) add("removed ${abbreviate(removed)}")
    }
    .joinToString("; ", prefix = "$label: ")
}

private fun abbreviate(items: List<String>, limit: Int = 8): String =
  if (items.size <= limit) items.joinToString(", ")
  else items.take(limit).joinToString(", ") + " and ${items.size - limit} more"

private fun Long.hex(): String = toULong().toString(16).padStart(16, '0')

private fun RcVersion.text(): String = "$major.$minor.$patch"

private fun RcHeaderValue?.text(): String =
  when (this) {
    null -> "none"
    is RcHeaderValue.IntValue -> value.toString()
    is RcHeaderValue.LongValue -> "${value}L"
    is RcHeaderValue.FloatValue -> describeFloatWord(value)
    is RcHeaderValue.StringValue -> quote(value)
  }

internal enum class EditKind {
  Same,
  Removed,
  Added,
}

/** One step of an edit script: line [aIndex] of the first list, line [bIndex] of the second. */
internal class Edit(val kind: EditKind, val aIndex: Int, val bIndex: Int)

/**
 * A shortest edit script from [a] to [b] (Myers' O((N+M)D) algorithm), after trimming the common
 * prefix and suffix — the whole of a typical change between two captures of one template.
 */
internal fun diffLines(a: List<String>, b: List<String>): List<Edit> {
  var prefix = 0
  while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++
  var suffix = 0
  while (
    suffix < a.size - prefix &&
      suffix < b.size - prefix &&
      a[a.size - 1 - suffix] == b[b.size - 1 - suffix]
  ) suffix++
  val edits = mutableListOf<Edit>()
  for (i in 0 until prefix) edits += Edit(EditKind.Same, i, i)
  edits +=
    myers(a.subList(prefix, a.size - suffix), b.subList(prefix, b.size - suffix), prefix, prefix)
  for (k in suffix downTo 1) edits += Edit(EditKind.Same, a.size - k, b.size - k)
  return edits
}

/** The most edits [myers] aligns; beyond it a span is reported as replaced. */
internal const val MAX_EDIT_DISTANCE: Int = 2_000

private fun myers(a: List<String>, b: List<String>, aOffset: Int, bOffset: Int): List<Edit> {
  val n = a.size
  val m = b.size
  if (n == 0) return List(m) { Edit(EditKind.Added, aOffset, bOffset + it) }
  if (m == 0) return List(n) { Edit(EditKind.Removed, aOffset + it, bOffset) }
  val max = n + m
  val offset = max
  val v = IntArray(2 * max + 2)
  // Each step keeps only the diagonals it can reach, -d..d: O(D²) memory in all, not O(D·(N+M)).
  val trace = mutableListOf<IntArray>()
  var found = false
  for (d in 0..max) {
    // Past this many edits the two have little in common line for line: report the span as
    // replaced rather than spend memory and time on an alignment nobody reads.
    if (d > MAX_EDIT_DISTANCE) {
      return List(n) { Edit(EditKind.Removed, aOffset + it, bOffset) } +
        List(m) { Edit(EditKind.Added, aOffset + n, bOffset + it) }
    }
    trace += v.copyOfRange(offset - d, offset + d + 1)
    var k = -d
    while (k <= d) {
      var x =
        if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) v[offset + k + 1]
        else v[offset + k - 1] + 1
      var y = x - k
      while (x < n && y < m && a[x] == b[y]) {
        x++
        y++
      }
      v[offset + k] = x
      if (x >= n && y >= m) {
        found = true
        break
      }
      k += 2
    }
    if (found) break
  }
  // Walk the trace back from (n, m) to (0, 0).
  val reversed = mutableListOf<Edit>()
  var x = n
  var y = m
  for (d in trace.size - 1 downTo 0) {
    val window = trace[d]
    fun vd(k: Int) = window[k + d]
    val k = x - y
    val previousK = if (k == -d || (k != d && vd(k - 1) < vd(k + 1))) k + 1 else k - 1
    val previousX = if (d == 0) 0 else vd(previousK)
    val previousY = previousX - previousK
    while (x > previousX && y > previousY) {
      x--
      y--
      reversed += Edit(EditKind.Same, aOffset + x, bOffset + y)
    }
    if (d > 0) {
      if (x == previousX) {
        y--
        reversed += Edit(EditKind.Added, aOffset + x, bOffset + y)
      } else {
        x--
        reversed += Edit(EditKind.Removed, aOffset + x, bOffset + y)
      }
    }
  }
  return reversed.asReversed()
}

/**
 * `diff -u` of the two line lists, each hunk headed — as `git diff` heads one with its function —
 * by the container the hunk's first line sits in.
 */
private fun unifiedDiff(
  a: List<RcStructuralLine>,
  b: List<RcStructuralLine>,
  edits: List<Edit>,
  beforeName: String,
  afterName: String,
  context: Int,
): String = buildString {
  appendLine("--- $beforeName")
  appendLine("+++ $afterName")
  val changed = edits.indices.filter { edits[it].kind != EditKind.Same }
  var i = 0
  while (i < changed.size) {
    // A hunk runs from `context` lines before a change to `context` lines after the last change
    // that is within 2 * context of the one before it.
    var last = i
    while (last + 1 < changed.size && changed[last + 1] - changed[last] <= 2 * context + 1) last++
    val from = maxOf(0, changed[i] - context)
    val to = minOf(edits.size - 1, changed[last] + context)
    val hunk = edits.subList(from, to + 1)
    val aStart = hunk.first().aIndex
    val bStart = hunk.first().bIndex
    val aCount = hunk.count { it.kind != EditKind.Added }
    val bCount = hunk.count { it.kind != EditKind.Removed }
    val enclosing =
      edits[changed[i]].let { first ->
        if (first.kind == EditKind.Added) enclosingContainer(b, first.bIndex)
        else enclosingContainer(a, first.aIndex)
      }
    append("@@ -${range(aStart, aCount)} +${range(bStart, bCount)} @@")
    enclosing?.let { append(' ').append(it.trim()) }
    appendLine()
    hunk.forEach {
      when (it.kind) {
        EditKind.Same -> append(' ').appendLine(a[it.aIndex].text)
        EditKind.Removed -> append('-').appendLine(a[it.aIndex].text)
        EditKind.Added -> append('+').appendLine(b[it.bIndex].text)
      }
    }
    i = last + 1
  }
}

private fun range(start: Int, count: Int): String =
  if (count == 0) "$start,0" else if (count == 1) "${start + 1}" else "${start + 1},$count"

/** The nearest line before [index] that is less indented than it: the container it sits in. */
private fun enclosingContainer(lines: List<RcStructuralLine>, index: Int): String? {
  if (index !in lines.indices) return null
  val depth = lines[index].depth
  if (depth == 0) return null
  for (j in index - 1 downTo 0) if (lines[j].depth < depth && lines[j].operation != null)
    return lines[j].text
  return null
}

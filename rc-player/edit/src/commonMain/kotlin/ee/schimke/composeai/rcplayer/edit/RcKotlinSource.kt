package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsiblePriorityModifier
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFitBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFlowLayout
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightInModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcHostMetadataAction
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedAction
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedActionValue
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRippleModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVisibilityModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthInModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.protocol.RcZIndexModifier
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * How [toRemoteComposeKotlin] writes its source file.
 *
 * @property packageName the generated file's package, or empty for none.
 * @property functionName the name of the generated `@Composable @RemoteComposable` function.
 * @property density the density the document was captured at, used to turn the pixel values of a
 *   document that is not written in dp back into dp. Null takes it from the header, where AndroidX
 *   records it, and otherwise assumes 1.
 * @property imageDecoder how an image embedded in the document is decoded back into an
 *   `ImageBitmap` at creation time.
 */
public data class RcKotlinOptions(
  val packageName: String = "",
  val functionName: String = "RemoteDocument",
  val density: Float? = null,
  val imageDecoder: RcKotlinImageDecoder = RcKotlinImageDecoder.Skia,
)

/** Which platform API the generated source decodes an embedded image with. */
public enum class RcKotlinImageDecoder {
  /** `org.jetbrains.skia.Image.makeFromEncoded` — desktop JVM, iOS, macOS and Wasm. */
  Skia,

  /** `android.graphics.BitmapFactory.decodeByteArray` — Android. */
  Android,
}

/**
 * A Kotlin source file that re-creates a document through the AndroidX Remote Compose creation API.
 *
 * @property code the source, one `@Composable @RemoteComposable` function and what it needs.
 * @property unsupported what the source could not say, one line each, in document order. Every
 *   entry is also written into [code] as a `TODO(rc-to-kotlin)` comment where it applies, so the
 *   gap is visible in the file as well as here. Empty when the source re-creates every operation
 *   the generator reads.
 */
public data class RcKotlinSource(val code: String, val unsupported: List<String>) {
  /** Whether nothing in the document was left out or approximated. */
  public val isComplete: Boolean
    get() = unsupported.isEmpty()
}

/**
 * Turns a decoded document back into a Remote Compose `.kt` file: the layout tree as the
 * `RemoteBox`, `RemoteRow`, `RemoteColumn`, `RemoteText` and `RemoteImage` calls that write it,
 * with the modifier chain, colours, strings, named (host-bound) values, click actions and semantics
 * each component carries.
 *
 * This is a decompiler, not a disassembler. It writes the source a person would have written, so an
 * operation the creation API produces as a side effect of something else — a component value, a
 * constant an expression reads — is re-created by the call that needs it rather than spelled out.
 * What it cannot express through the public creation API (canvas drawing, float and colour
 * expressions, touch handlers, macros, particle systems) is written as a `TODO(rc-to-kotlin)`
 * comment where it occurred, with a placeholder where a value is required so the file still
 * compiles, and listed in [RcKotlinSource.unsupported].
 *
 * A document written in dp (`RcHeader.DENSITY_BEHAVIOR_DP`) maps back exactly. One written in
 * pixels is turned back into dp with [RcKotlinOptions.density]; where a value does not divide back
 * exactly the source keeps the pixels instead (`16f.rf`), so capturing it at that density writes
 * the same number.
 */
public fun RcDocument.toRemoteComposeKotlin(
  options: RcKotlinOptions = RcKotlinOptions()
): RcKotlinSource = RcKotlinWriter(this, options).write()

private class RcKotlinWriter(
  private val document: RcDocument,
  private val options: RcKotlinOptions,
) {
  private val header = document.header
  private val densityBehavior = header.densityBehavior
  private val density: Float = RcDocumentIndex.densityOf(header, options.density)

  private val imports = mutableSetOf<String>()
  private val unsupported = mutableListOf<String>()
  private val hoisted = linkedMapOf<String, String>()
  private val hoistedNames = mutableMapOf<String, String>()
  private val fileLevel = mutableListOf<String>()
  private val embeddedBitmaps = mutableSetOf<Int>()
  private val body = StringBuilder()
  private var indent = 1

  private val index = RcDocumentIndex(document)
  private val texts = index.texts
  private val colors = index.colors
  private val floats = index.floats
  private val ints = index.ints
  private val named = index.named
  private val bitmaps = index.bitmaps
  private val producers = index.producers

  fun write(): RcKotlinSource {
    imports += "androidx.compose.runtime.Composable"
    imports += "androidx.compose.remote.creation.compose.layout.RemoteComposable"
    val tree = operationTree(document.operations)
    if (tree == null) {
      todo("the document's containers do not balance; nothing was generated")
    } else {
      val roots = tree.filter { it.operation is RcRootLayout }
      if (roots.isEmpty()) todo("the document has no root layout; nothing was generated")
      tree
        .filter { it.operation !is RcRootLayout }
        .forEach { node -> statement(node, Parent.Other, topLevel = true) }
      roots.forEach { root -> content(root.children, Parent.Other) }
    }
    flushComments()
    return RcKotlinSource(render(), unsupported.toList())
  }

  // --- the file -----------------------------------------------------------------------------

  private fun render(): String = buildString {
    appendLine(
      "// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin."
    )
    appendLine(
      "// Document: ${header.width} x ${header.height}, " +
        "version ${header.version.major}.${header.version.minor}.${header.version.patch}, " +
        "density behaviour ${densityName()}."
    )
    if (unsupported.isNotEmpty()) {
      appendLine(
        "// ${unsupported.size} construct(s) could not be expressed: search for TODO(rc-to-kotlin)."
      )
    }
    if (options.packageName.isNotEmpty()) {
      appendLine()
      appendLine("package ${options.packageName}")
    }
    appendLine()
    imports.sorted().forEach { name -> appendLine("import $name") }
    fileLevel.forEach {
      appendLine()
      appendLine(it)
    }
    appendLine()
    appendLine("@Composable")
    appendLine("@RemoteComposable")
    appendLine("fun ${options.functionName}() {")
    hoisted.forEach { (name, initializer) -> appendLine("  val $name = $initializer") }
    append(body)
    appendLine("}")
  }

  private fun densityName(): String =
    when (densityBehavior) {
      RcHeader.DENSITY_BEHAVIOR_DP -> "dp"
      RcHeader.DENSITY_BEHAVIOR_PIXELS -> "pixels"
      else -> "legacy"
    }

  private fun line(text: String) {
    repeat(indent) { body.append("  ") }
    body.appendLine(text)
  }

  private fun todo(message: String) {
    unsupported += message
    line("// TODO(rc-to-kotlin): $message")
  }

  /** Records a gap inside an expression, returning the comment to place beside it. */
  private fun gap(message: String): String {
    unsupported += message
    pendingComments += message
    return ""
  }

  private val pendingComments = mutableListOf<String>()

  private fun flushComments() {
    pendingComments.forEach { line("// TODO(rc-to-kotlin): $it") }
    pendingComments.clear()
  }

  private fun import(vararg names: String) {
    names.forEach { imports += it }
  }

  // --- the component tree -------------------------------------------------------------------

  /** The scope a child is written in, which decides the scoped modifiers it can use. */
  private enum class Parent {
    Row,
    Column,
    CollapsibleRow,
    CollapsibleColumn,
    Other;

    val isRow: Boolean
      get() = this == Row || this == CollapsibleRow

    val isColumn: Boolean
      get() = this == Column || this == CollapsibleColumn
  }

  private fun content(nodes: List<RcOperationNode>, parent: Parent) {
    nodes.forEach { node ->
      if (node.operation is RcLayoutContent) content(node.children, parent)
      else statement(node, parent, topLevel = false)
    }
  }

  private fun statement(node: RcOperationNode, parent: Parent, topLevel: Boolean) {
    when (val operation = node.operation) {
      is RcBoxLayout -> box(node, operation, parent)
      is RcRowLayout -> row(node, operation, parent)
      is RcColumnLayout -> column(node, operation, parent)
      is RcFlowLayout -> flowRow(node, operation, parent)
      is RcFitBoxLayout -> fitBox(node, operation, parent)
      is RcCollapsibleRowLayout -> collapsibleRow(node, operation, parent)
      is RcCollapsibleColumnLayout -> collapsibleColumn(node, operation, parent)
      is RcCoreText -> coreText(node, operation, parent)
      is RcTextLayout -> textLayout(node, operation, parent)
      is RcImageLayout -> image(node, operation, parent)
      is RcCanvasLayout -> canvas(node, operation, parent)
      is RcStateLayout -> {
        todo(
          "state layout ${operation.componentId} switches on integer ${operation.indexId}; " +
            "only its first branch is generated"
        )
        val branches = node.children.firstOrNull { it.operation is RcLayoutContent }?.children
        branches?.firstOrNull()?.let { statement(it, parent, topLevel) }
      }
      else ->
        when {
          operation.opcode == RcOpcodes.CANVAS_OPERATIONS ->
            todo("drawWithContent drawing is not generated: ${drawing(node.children)}")
          !operation.isSilent() ->
            todo("${describe(operation)} is not expressible through the creation API")
        }
    }
  }

  private fun canvas(node: RcOperationNode, layout: RcCanvasLayout, parent: Parent) {
    import("androidx.compose.remote.creation.compose.layout.RemoteCanvas")
    val arguments = mutableListOf<String>()
    modifier(node, parent, layout.componentId)?.let { arguments += "modifier = $it" }
    flushComments()
    val content =
      node.children
        .filter { it.operation is RcCanvasContent || it.operation is RcLayoutContent }
        .flatMap { it.children }
    call("RemoteCanvas", arguments, trailingLambda = true) {
      todo("canvas drawing is not generated: ${drawing(content)}")
    }
  }

  /** What a block of canvas operations draws, by operation and count, for its TODO. */
  private fun drawing(nodes: List<RcOperationNode>): String {
    val counts = linkedMapOf<String, Int>()
    nodes.forEach { root ->
      root.walk { node ->
        val operation = node.operation
        if (!operation.isSilent() && operation.opcode != RcOpcodes.CONTAINER_END) {
          val name = describe(operation)
          counts[name] = (counts[name] ?: 0) + 1
        }
      }
    }
    if (counts.isEmpty()) return "nothing"
    return counts.entries.joinToString(", ") { (name, count) ->
      if (count == 1) name else "$name x$count"
    }
  }

  /** Definitions the creation API writes for whichever call reads them, so need no statement. */
  private fun RcOperation.isSilent(): Boolean = index.isDefinition(this)

  private fun box(node: RcOperationNode, layout: RcBoxLayout, parent: Parent) {
    import("androidx.compose.remote.creation.compose.layout.RemoteBox")
    val arguments = mutableListOf<String>()
    modifier(node, parent, layout.componentId)?.let { arguments += "modifier = $it" }
    boxAlignment(layout.horizontalPositioning, layout.verticalPositioning)?.let {
      arguments += "contentAlignment = $it"
    }
    container("RemoteBox", arguments, node, Parent.Other)
  }

  private fun row(node: RcOperationNode, layout: RcRowLayout, parent: Parent) {
    import("androidx.compose.remote.creation.compose.layout.RemoteRow")
    val arguments = mutableListOf<String>()
    modifier(node, parent, layout.componentId)?.let { arguments += "modifier = $it" }
    horizontalArrangement(layout.horizontalPositioning, layout.spacedBy)?.let {
      arguments += "horizontalArrangement = $it"
    }
    verticalAlignment(layout.verticalPositioning, default = TOP)?.let {
      arguments += "verticalAlignment = $it"
    }
    container("RemoteRow", arguments, node, Parent.Row)
  }

  private fun column(node: RcOperationNode, layout: RcColumnLayout, parent: Parent) {
    import("androidx.compose.remote.creation.compose.layout.RemoteColumn")
    val arguments = mutableListOf<String>()
    modifier(node, parent, layout.componentId)?.let { arguments += "modifier = $it" }
    verticalArrangement(layout.verticalPositioning, layout.spacedBy, default = TOP)?.let {
      arguments += "verticalArrangement = $it"
    }
    horizontalAlignment(layout.horizontalPositioning, default = START)?.let {
      arguments += "horizontalAlignment = $it"
    }
    container("RemoteColumn", arguments, node, Parent.Column)
  }

  private fun flowRow(node: RcOperationNode, layout: RcFlowLayout, parent: Parent) {
    import("androidx.compose.remote.creation.compose.layout.RemoteFlowRow")
    val arguments = mutableListOf<String>()
    modifier(node, parent, layout.componentId)?.let { arguments += "modifier = $it" }
    horizontalArrangement(layout.horizontalPositioning, layout.spacedBy)?.let {
      arguments += "horizontalArrangement = $it"
    }
    verticalArrangement(layout.verticalPositioning, RcFloatWord.literal(0f), default = TOP)?.let {
      arguments += "verticalArrangement = $it"
    }
    if (layout.maxItemsInEachRow != Int.MAX_VALUE) {
      arguments += "maxItemsInEachRow = ${layout.maxItemsInEachRow}"
    }
    if (layout.maxLines != Int.MAX_VALUE) arguments += "maxLines = ${layout.maxLines}"
    container("RemoteFlowRow", arguments, node, Parent.Other)
  }

  private fun fitBox(node: RcOperationNode, layout: RcFitBoxLayout, parent: Parent) {
    import("androidx.compose.remote.creation.compose.layout.RemoteFitBox")
    val arguments = mutableListOf<String>()
    modifier(node, parent, layout.componentId)?.let { arguments += "modifier = $it" }
    horizontalAlignment(layout.horizontalPositioning, default = CENTER)?.let {
      arguments += "horizontalAlignment = $it"
    }
    verticalArrangement(layout.verticalPositioning, RcFloatWord.literal(0f), default = CENTER)
      ?.let { arguments += "verticalArrangement = $it" }
    container("RemoteFitBox", arguments, node, Parent.Other)
  }

  private fun collapsibleRow(
    node: RcOperationNode,
    layout: RcCollapsibleRowLayout,
    parent: Parent,
  ) {
    import("androidx.compose.remote.creation.compose.layout.RemoteCollapsibleRow")
    val arguments = mutableListOf<String>()
    modifier(node, parent, layout.componentId)?.let { arguments += "modifier = $it" }
    horizontalArrangement(layout.horizontalPositioning, layout.spacedBy)?.let {
      arguments += "horizontalArrangement = $it"
    }
    verticalAlignment(layout.verticalPositioning, default = TOP)?.let {
      arguments += "verticalAlignment = $it"
    }
    container("RemoteCollapsibleRow", arguments, node, Parent.CollapsibleRow)
  }

  private fun collapsibleColumn(
    node: RcOperationNode,
    layout: RcCollapsibleColumnLayout,
    parent: Parent,
  ) {
    import("androidx.compose.remote.creation.compose.layout.RemoteCollapsibleColumn")
    val arguments = mutableListOf<String>()
    modifier(node, parent, layout.componentId)?.let { arguments += "modifier = $it" }
    verticalArrangement(layout.verticalPositioning, layout.spacedBy, default = TOP)?.let {
      arguments += "verticalArrangement = $it"
    }
    horizontalAlignment(layout.horizontalPositioning, default = START)?.let {
      arguments += "horizontalAlignment = $it"
    }
    container("RemoteCollapsibleColumn", arguments, node, Parent.CollapsibleColumn)
  }

  private fun container(
    function: String,
    arguments: List<String>,
    node: RcOperationNode,
    scope: Parent,
  ) {
    flushComments()
    val children = node.children.filter { it.operation is RcLayoutContent }.flatMap { it.children }
    val childNodes = children.filter { !it.operation.isSilent() || it.operation is RcLayoutContent }
    call(function, arguments, trailingLambda = childNodes.isNotEmpty() || function != "RemoteBox") {
      content(children, scope)
    }
  }

  private fun call(
    function: String,
    arguments: List<String>,
    trailingLambda: Boolean,
    lambdaBody: () -> Unit = {},
  ) {
    val head =
      when {
        arguments.isEmpty() -> function
        else -> "$function(${arguments.joinToString(", ")})"
      }
    if (!trailingLambda) {
      line(if (arguments.isEmpty()) "$function()" else head)
      return
    }
    val start = body.length
    line("$head {")
    val opened = body.length
    indent++
    lambdaBody()
    indent--
    if (body.length == opened) {
      body.setLength(start)
      line("$head {}")
    } else {
      line("}")
    }
  }

  // --- text ---------------------------------------------------------------------------------

  private fun coreText(node: RcOperationNode, text: RcCoreText, parent: Parent) {
    import(
      "androidx.compose.remote.creation.compose.layout.RemoteText",
      "androidx.compose.remote.creation.compose.text.RemoteTextOverflow",
    )
    val properties = text.properties.associateBy { it.id }

    fun int(id: Int): Int? = (properties[id] as? RcTextStyleProperty.IntValue)?.value

    fun word(id: Int): RcFloatWord? = (properties[id] as? RcTextStyleProperty.FloatValue)?.value

    val arguments = mutableListOf(string(text.textId))
    modifier(node, parent, text.componentId.takeUnless { it == -1 } ?: 0)?.let {
      arguments += "modifier = $it"
    }
    val colorId = int(TEXT_COLOR_ID)?.takeUnless { it == -1 }
    when {
      colorId != null -> arguments += "color = ${colorById(colorId)}"
      int(TEXT_COLOR) != null -> arguments += "color = ${literalColor(int(TEXT_COLOR)!!)}"
    }
    // The writer omits a font size equal to the wire default, so an absent one is 36, not
    // RemoteText's own 12sp.
    val size = word(TEXT_FONT_SIZE) ?: RcFloatWord.literal(WIRE_DEFAULT_FONT_SIZE)
    arguments += "fontSize = ${fontSize(size)}"
    int(TEXT_FONT_STYLE)?.let { style -> fontStyle(style)?.let { arguments += "fontStyle = $it" } }
    word(TEXT_FONT_WEIGHT)?.let { arguments += "fontWeight = ${fontWeight(it)}" }
    int(TEXT_FONT_FAMILY)
      ?.takeUnless { it == -1 }
      ?.let { familyId -> arguments += "fontFamily = ${fontFamily(familyId)}" }
    int(TEXT_ALIGN)?.let { align -> textAlign(align)?.let { arguments += "textAlign = $it" } }
    arguments += "overflow = ${overflow(int(TEXT_OVERFLOW) ?: 1)}"
    int(TEXT_MAX_LINES)?.takeUnless { it == Int.MAX_VALUE }?.let { arguments += "maxLines = $it" }
    textStyle(text, properties, size)?.let { arguments += "style = $it" }
    fontVariations(properties)?.let { arguments += "fontVariationSettings = $it" }
    val known =
      setOf(
        TEXT_ID,
        TEXT_ANIMATION_ID,
        TEXT_COLOR,
        TEXT_COLOR_ID,
        TEXT_FONT_SIZE,
        TEXT_FONT_STYLE,
        TEXT_FONT_WEIGHT,
        TEXT_FONT_FAMILY,
        TEXT_ALIGN,
        TEXT_OVERFLOW,
        TEXT_MAX_LINES,
        TEXT_LETTER_SPACING,
        TEXT_LINE_HEIGHT_MULTIPLIER,
        TEXT_UNDERLINE,
        TEXT_STRIKETHROUGH,
        TEXT_FONT_AXIS,
        TEXT_FONT_AXIS_VALUES,
        TEXT_STYLE_PARENT,
      )
    val unknown = text.properties.filter { it.id !in known && !it.isDefault() }
    if (unknown.isNotEmpty()) {
      gap(
        "text ${text.textId} sets style properties " +
          unknown.joinToString { (TEXT_PROPERTY_NAMES[it.id] ?: "#${it.id}") } +
          " that are not generated"
      )
    }
    if (text.textStyleId != null) gap("text ${text.textId} inherits a TextStyle; it is not applied")
    flushComments()
    call("RemoteText", arguments, trailingLambda = false)
  }

  /**
   * The `RemoteTextStyle` for what `RemoteText` only takes through its style. The creation API
   * writes letter spacing and line height as ratios of the font size, so they are multiplied back.
   */
  private fun textStyle(
    text: RcCoreText,
    properties: Map<Int, RcTextStyleProperty>,
    size: RcFloatWord,
  ): String? {
    val fields = mutableListOf<String>()
    val sizeSp =
      when {
        size.isNaNEncoded -> null
        densityBehavior == RcHeader.DENSITY_BEHAVIOR_DP -> size.value
        else -> size.value / density
      }

    fun ratio(id: Int, default: Float, field: String) {
      val word = (properties[id] as? RcTextStyleProperty.FloatValue)?.value ?: return
      if (!word.isNaNEncoded && word.value == default) return
      if (word.isNaNEncoded || sizeSp == null) {
        gap("text ${text.textId}'s $field is read from a variable; it is not generated")
        return
      }
      import(
        "androidx.compose.remote.creation.compose.state.asRemoteTextUnit",
        "androidx.compose.ui.unit.sp",
      )
      fields += "$field = ${float(word.value * sizeSp)}.sp.asRemoteTextUnit()"
    }
    ratio(TEXT_LETTER_SPACING, 0f, "letterSpacing")
    ratio(TEXT_LINE_HEIGHT_MULTIPLIER, 1f, "lineHeight")
    val decorations =
      listOfNotNull(
        "Underline"
          .takeIf {
            (properties[TEXT_UNDERLINE] as? RcTextStyleProperty.BooleanValue)?.value == true
          },
        "LineThrough"
          .takeIf {
            (properties[TEXT_STRIKETHROUGH] as? RcTextStyleProperty.BooleanValue)?.value == true
          },
      )
    if (decorations.isNotEmpty()) {
      import("androidx.compose.ui.text.style.TextDecoration")
      fields +=
        "textDecoration = " +
          if (decorations.size == 1) "TextDecoration.${decorations[0]}"
          else
            "TextDecoration.combine(listOf(${decorations.joinToString { "TextDecoration.$it" }}))"
    }
    if (fields.isEmpty()) return null
    import("androidx.compose.remote.creation.compose.text.RemoteTextStyle")
    return "RemoteTextStyle(${fields.joinToString(", ")})"
  }

  private fun fontVariations(properties: Map<Int, RcTextStyleProperty>): String? {
    val axes = (properties[TEXT_FONT_AXIS] as? RcTextStyleProperty.IntArrayValue)?.values
    val values = (properties[TEXT_FONT_AXIS_VALUES] as? RcTextStyleProperty.FloatArrayValue)?.values
    if (axes.isNullOrEmpty() || values == null || values.size != axes.size) return null
    if (values.any { it.isNaNEncoded } || axes.any { texts[it] == null }) {
      gap("font axes read from variables are not generated")
      return null
    }
    import("androidx.compose.ui.text.font.FontVariation")
    val settings =
      axes.indices.joinToString(", ") { index ->
        "FontVariation.Setting(${quote(texts.getValue(axes[index]))}, ${float(values[index].value)})"
      }
    return "FontVariation.Settings($settings)"
  }

  private fun textLayout(node: RcOperationNode, text: RcTextLayout, parent: Parent) {
    import(
      "androidx.compose.remote.creation.compose.layout.RemoteText",
      "androidx.compose.remote.creation.compose.text.RemoteTextOverflow",
    )
    val arguments = mutableListOf(string(text.textId))
    modifier(node, parent, text.componentId)?.let { arguments += "modifier = $it" }
    arguments +=
      if (text.flags and RcTextLayout.FLAG_DYNAMIC_COLOR != 0) "color = ${colorById(text.color)}"
      else "color = ${literalColor(text.color)}"
    arguments += "fontSize = ${fontSize(text.fontSize)}"
    fontStyle(text.fontStyle)?.let { arguments += "fontStyle = $it" }
    arguments += "fontWeight = ${fontWeight(text.fontWeight)}"
    if (text.fontFamilyId != -1) arguments += "fontFamily = ${fontFamily(text.fontFamilyId)}"
    textAlign(text.textAlign)?.let { arguments += "textAlign = $it" }
    arguments += "overflow = ${overflow(text.overflow)}"
    if (text.maxLines != Int.MAX_VALUE) arguments += "maxLines = ${text.maxLines}"
    gap("text ${text.textId} is a TextLayout; the creation API re-creates it as CoreText")
    flushComments()
    call("RemoteText", arguments, trailingLambda = false)
  }

  private fun fontSize(word: RcFloatWord): String {
    if (word.isNaNEncoded) {
      gap("a font size reads ${describeId(word.referencedId!!)}; 14.sp is used instead")
      import("androidx.compose.remote.creation.compose.state.rsp")
      return "14.rsp"
    }
    val sp =
      if (densityBehavior == RcHeader.DENSITY_BEHAVIOR_DP) word.value else word.value / density
    return if (sp == sp.toInt().toFloat()) {
      import("androidx.compose.remote.creation.compose.state.rsp")
      "${sp.toInt()}.rsp"
    } else {
      import(
        "androidx.compose.remote.creation.compose.state.asRemoteTextUnit",
        "androidx.compose.ui.unit.sp",
      )
      "${float(sp)}.sp.asRemoteTextUnit()"
    }
  }

  /**
   * A font style as the `RemoteFontStyle` that writes the same wire value: `Italic` writes 1. No
   * creation call writes another value, such as the Typeface-style 2 (italic) or 3 (bold italic),
   * so those are reported rather than written as 1, which players read differently.
   */
  private fun fontStyle(style: Int): String? =
    when (style) {
      0 -> null
      1 -> {
        import("androidx.compose.remote.creation.compose.text.RemoteFontStyle")
        "RemoteFontStyle.Italic"
      }
      else -> {
        gap("font style $style has no RemoteFontStyle; the text is written with the default style")
        null
      }
    }

  private fun fontWeight(word: RcFloatWord): String {
    import("androidx.compose.remote.creation.compose.text.RemoteFontWeight")
    if (word.isNaNEncoded) {
      gap("a font weight reads ${describeId(word.referencedId!!)}; Normal is used instead")
      return "RemoteFontWeight.Normal"
    }
    val weight = word.value.toInt()
    return FONT_WEIGHTS[weight]?.let { "RemoteFontWeight.$it" } ?: "RemoteFontWeight($weight)"
  }

  private fun fontFamily(textId: Int): String {
    import("androidx.compose.remote.creation.compose.text.RemoteFontFamily")
    return when (val name = texts[textId]) {
      "default" -> "RemoteFontFamily.Default"
      "serif" -> "RemoteFontFamily.Serif"
      "sans-serif" -> "RemoteFontFamily.SansSerif"
      "monospace" -> "RemoteFontFamily.Monospace"
      "cursive" -> "RemoteFontFamily.Cursive"
      null -> {
        gap("font family reads ${describeId(textId)}; Default is used")
        "RemoteFontFamily.Default"
      }
      else -> "RemoteFontFamily.Named(${quote(name)})"
    }
  }

  private fun textAlign(align: Int): String? {
    val name =
      when (align and 0xffff) {
        RcTextLayout.ALIGN_LEFT -> "RemoteTextAlign.Left"
        RcTextLayout.ALIGN_RIGHT -> "RemoteTextAlign.Right"
        RcTextLayout.ALIGN_CENTER -> "RemoteTextAlign.Center"
        RcTextLayout.ALIGN_JUSTIFY -> "RemoteTextAlign.Justify"
        RcTextLayout.ALIGN_START -> "RemoteTextAlign.Start"
        RcTextLayout.ALIGN_END -> "RemoteTextAlign.End"
        else -> null
      }
    if (name != null) import("androidx.compose.remote.creation.compose.text.RemoteTextAlign")
    return name
  }

  private fun overflow(overflow: Int): String =
    when (overflow) {
      RcTextLayout.OVERFLOW_VISIBLE -> "RemoteTextOverflow.Visible"
      RcTextLayout.OVERFLOW_ELLIPSIS -> "RemoteTextOverflow.Ellipsis"
      RcTextLayout.OVERFLOW_START_ELLIPSIS -> "RemoteTextOverflow.StartEllipsis"
      RcTextLayout.OVERFLOW_MIDDLE_ELLIPSIS -> "RemoteTextOverflow.MiddleEllipsis"
      else -> "RemoteTextOverflow.Clip"
    }

  // --- images -------------------------------------------------------------------------------

  private fun image(node: RcOperationNode, layout: RcImageLayout, parent: Parent) {
    import(
      "androidx.compose.remote.creation.compose.layout.RemoteImage",
      "androidx.compose.remote.creation.compose.layout.RemoteContentScale",
    )
    val arguments = mutableListOf<String>()
    arguments += bitmap(layout.bitmapId)
    arguments += "contentDescription = null"
    modifier(node, parent, layout.componentId)?.let { arguments += "modifier = $it" }
    arguments += "contentScale = ${contentScale(layout.scaleType)}"
    arguments += "alpha = ${floatValue(layout.alpha)}"
    flushComments()
    call("RemoteImage", arguments, trailingLambda = false)
  }

  @OptIn(ExperimentalEncodingApi::class)
  private fun bitmap(id: Int): String {
    named[id]
      ?.takeIf { it.type == RcNamedVariable.IMAGE_TYPE }
      ?.let { variable ->
        val url = bitmaps[id]?.takeIf { it.encoding == RcBitmapData.ENCODING_URL }
        if (url != null) {
          import("androidx.compose.remote.creation.compose.state.rememberNamedRemoteImageBitmap")
          reportComputed(id, variable.name)
          val (name, domain) = domainOf(variable.name)
          return hoist(
            "image$id",
            "rememberNamedRemoteImageBitmap(${quote(name)}, ${quote(url.data.decodeToString())}$domain)",
          )
        }
      }
    val data = bitmaps[id]
    return when {
      data == null -> {
        gap("image $id has no bitmap data in the document")
        placeholderBitmap(id)
      }
      data.encoding == RcBitmapData.ENCODING_URL -> {
        import("androidx.compose.remote.creation.compose.state.rememberRemoteImageBitmap")
        hoist("image$id", "rememberRemoteImageBitmap(${quote(data.data.decodeToString())})")
      }
      data.encoding == RcBitmapData.ENCODING_INLINE &&
        (data.type == RcBitmapData.TYPE_PNG_8888 || data.type == RcBitmapData.TYPE_PNG) -> {
        import("kotlin.io.encoding.Base64")
        val constant = "Image${id}Png"
        // Two images can show one bitmap; its bytes are declared once.
        if (embeddedBitmaps.add(id))
          fileLevel +=
            "/** Image $id of the document, ${data.width} x ${data.height}, as PNG. */\n" +
              // Not a `const val`: the compiler folds a constant's concatenation into one string,
              // and the JVM caps a constant string at 65,535 bytes. Each literal stays under it
              // and the parts are joined at run time.
              "private val $constant: String =\n" +
              "  listOf(\n" +
              Base64.encode(data.data).chunked(BASE64_CHUNK).joinToString(",\n") { "    \"$it\"" } +
              ",\n  ).joinToString(\"\")"
        val decode =
          when (options.imageDecoder) {
            RcKotlinImageDecoder.Skia -> {
              import(
                "org.jetbrains.skia.Image",
                "androidx.compose.ui.graphics.toComposeImageBitmap",
              )
              "Image.makeFromEncoded(Base64.decode($constant)).toComposeImageBitmap()"
            }
            RcKotlinImageDecoder.Android -> {
              import("android.graphics.BitmapFactory", "androidx.compose.ui.graphics.asImageBitmap")
              "Base64.decode($constant).let { BitmapFactory.decodeByteArray(it, 0, it.size) }" +
                ".asImageBitmap()"
            }
          }
        val variable = named[id]?.takeIf { it.type == RcNamedVariable.IMAGE_TYPE }
        if (variable != null) {
          // A named image keeps its name, so the host can still replace it.
          import("androidx.compose.remote.creation.compose.state.rememberNamedRemoteImageBitmap")
          reportComputed(id, variable.name)
          val (name, domain) = domainOf(variable.name)
          hoist("image$id", "rememberNamedRemoteImageBitmap(${quote(name)}$domain) { $decode }")
        } else {
          import(
            "androidx.compose.runtime.remember",
            "androidx.compose.remote.creation.compose.state.rb",
          )
          hoist("image$id", "remember { $decode.rb }")
        }
      }
      else -> {
        gap("image $id is stored as ${bitmapKind(data)}, which is not generated")
        placeholderBitmap(id)
      }
    }
  }

  private fun placeholderBitmap(id: Int): String {
    import(
      "androidx.compose.runtime.remember",
      "androidx.compose.remote.creation.compose.state.rb",
      "androidx.compose.ui.graphics.ImageBitmap",
    )
    return hoist("image$id", "remember { ImageBitmap(1, 1).rb }")
  }

  private fun bitmapKind(data: RcBitmapData): String =
    when (data.encoding) {
      RcBitmapData.ENCODING_FILE -> "a file reference"
      RcBitmapData.ENCODING_EMPTY -> "an empty bitmap"
      RcBitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER -> "a component's offscreen buffer"
      else -> "raw pixels (type ${data.type})"
    }

  private fun contentScale(scale: Int): String =
    when (scale) {
      SCALE_NONE -> "RemoteContentScale.None"
      SCALE_INSIDE -> "RemoteContentScale.Inside"
      SCALE_FILL_WIDTH -> "RemoteContentScale.FillWidth"
      SCALE_FILL_HEIGHT -> "RemoteContentScale.FillHeight"
      SCALE_FIT -> "RemoteContentScale.Fit"
      SCALE_CROP -> "RemoteContentScale.Crop"
      SCALE_FILL_BOUNDS -> "RemoteContentScale.FillBounds"
      else -> {
        gap("image scale type $scale has no RemoteContentScale; Fit is used")
        "RemoteContentScale.Fit"
      }
    }

  // --- modifiers ----------------------------------------------------------------------------

  /**
   * The modifier chain of the component [node] opens, or null when it has none. The operations
   * before its `LayoutContent` are its modifiers, in order.
   */
  private fun modifier(node: RcOperationNode, parent: Parent, componentId: Int): String? {
    val chain = mutableListOf<String>()
    val modifiers =
      node.children.takeWhile {
        it.operation !is RcLayoutContent && it.operation !is RcCanvasContent
      }
    var index = 0
    while (index < modifiers.size) {
      val current = modifiers[index]
      val next = modifiers.getOrNull(index + 1)?.operation
      val operation = current.operation
      // A width followed by the same height reads as `size` / `fillMaxSize`.
      if (
        operation is RcWidthModifier && next is RcHeightModifier && sizeOf(operation, next) != null
      ) {
        chain += sizeOf(operation, next)!!
        index += 2
        continue
      }
      if (operation is RcClickModifier) {
        val semantics = next as? RcAccessibilitySemantics
        val role = semantics?.takeIf { it.isClickRole() }
        chain += clickable(current, role)
        index += if (role != null) 2 else 1
        continue
      }
      if (operation.opcode != RcOpcodes.MODIFIER_DRAW_CONTENT) {
        modifierElement(operation, parent)?.let { chain += it }
      }
      index++
    }
    if (componentId > 0) {
      import("androidx.compose.remote.creation.compose.modifier.componentId")
      chain += "componentId($componentId)"
    }
    if (chain.isEmpty()) return null
    import("androidx.compose.remote.creation.compose.modifier.RemoteModifier")
    return "RemoteModifier." + chain.joinToString(".")
  }

  private fun sizeOf(width: RcWidthModifier, height: RcHeightModifier): String? {
    if (width.type != height.type || width.value.bits != height.value.bits) return null
    return when (width.type) {
      RcDimensionType.EXACT_DP -> {
        if (width.value.isNaNEncoded) return null
        import(
          "androidx.compose.remote.creation.compose.modifier.size",
          "androidx.compose.remote.creation.compose.state.rdp",
        )
        "size(${dp(width.value.value)})"
      }
      RcDimensionType.FILL -> {
        import("androidx.compose.remote.creation.compose.modifier.fillMaxSize")
        if (width.value.isLiteral(1f)) "fillMaxSize()"
        else "fillMaxSize(${floatValue(width.value)})"
      }
      RcDimensionType.WRAP -> {
        import("androidx.compose.remote.creation.compose.modifier.wrapContentSize")
        "wrapContentSize()"
      }
      else -> null
    }
  }

  private fun modifierElement(operation: RcOperation, parent: Parent): String? =
    when (operation) {
      is RcWidthModifier -> dimension("Width", operation.type, operation.value, parent)
      is RcHeightModifier -> dimension("Height", operation.type, operation.value, parent)
      is RcPaddingModifier -> padding(operation)
      is RcBackgroundModifier -> background(operation)
      is RcBorderModifier -> border(operation)
      is RcRoundedClipRectModifier -> roundedClip(operation)
      is RcClipRectModifier -> {
        import("androidx.compose.remote.creation.compose.modifier.clip")
        "clip()"
      }
      is RcOffsetModifier -> {
        import("androidx.compose.remote.creation.compose.modifier.offset")
        // `offset` takes only dp, so a pixel or variable length is converted rather than kept.
        "offset(${lengthDp(operation.x)}, ${lengthDp(operation.y)})"
      }
      is RcZIndexModifier -> {
        import("androidx.compose.remote.creation.compose.modifier.zIndex")
        "zIndex(${floatValue(operation.value)})"
      }
      is RcWidthInModifier -> sizeIn("widthIn", operation.minimum, operation.maximum)
      is RcHeightInModifier -> sizeIn("heightIn", operation.minimum, operation.maximum)
      is RcVisibilityModifier -> {
        import("androidx.compose.remote.creation.compose.modifier.visibility")
        "visibility(${intById(operation.visibilityId)})"
      }
      is RcRippleModifier -> {
        import("androidx.compose.remote.creation.compose.modifier.rippleEffect")
        "rippleEffect()"
      }
      is RcAccessibilitySemantics -> semantics(operation)
      is RcCollapsiblePriorityModifier -> {
        val horizontal = operation.orientation == RcCollapsiblePriorityModifier.HORIZONTAL
        if (
          (horizontal && parent == Parent.CollapsibleRow) ||
            (!horizontal && parent == Parent.CollapsibleColumn)
        ) {
          "collapsiblePriority(${floatLiteral(operation.priority, "a collapsible priority")})"
        } else {
          gap(
            "a collapsible priority outside a collapsible ${if (horizontal) "row" else "column"} is not generated"
          )
          null
        }
      }
      else -> {
        gap("modifier ${describe(operation)} is not generated")
        null
      }
    }

  private fun dimension(axis: String, type: Int, value: RcFloatWord, parent: Parent): String? {
    val lower = axis.lowercase()
    return when (type) {
      RcDimensionType.EXACT_DP -> {
        import("androidx.compose.remote.creation.compose.modifier.$lower")
        // A RemoteFloat would write EXACT, in pixels; keep the dp type.
        if (value.isNaNEncoded) {
          import("androidx.compose.remote.creation.compose.state.asRemoteDp")
          "$lower(${floatValue(value)}.asRemoteDp())"
        } else {
          import("androidx.compose.remote.creation.compose.state.rdp")
          "$lower(${dp(value.value)})"
        }
      }
      RcDimensionType.EXACT -> {
        import("androidx.compose.remote.creation.compose.modifier.$lower")
        "$lower(${floatValue(value)})"
      }
      RcDimensionType.FILL -> {
        import("androidx.compose.remote.creation.compose.modifier.fillMax$axis")
        if (value.isLiteral(1f)) "fillMax$axis()" else "fillMax$axis(${floatValue(value)})"
      }
      RcDimensionType.WRAP -> {
        import("androidx.compose.remote.creation.compose.modifier.wrapContent$axis")
        "wrapContent$axis()"
      }
      RcDimensionType.WEIGHT -> {
        val main = (axis == "Width" && parent.isRow) || (axis == "Height" && parent.isColumn)
        if (main) "weight(${if (value.isNaNEncoded) floatValue(value) else float(value.value)})"
        else {
          gap(
            "a ${lower} weight outside a ${if (axis == "Width") "row" else "column"} is not generated"
          )
          null
        }
      }
      RcDimensionType.INTRINSIC_MIN,
      RcDimensionType.INTRINSIC_MAX -> {
        import(
          "androidx.compose.remote.creation.compose.modifier.$lower",
          "androidx.compose.remote.creation.compose.layout.RemoteIntrinsicSize",
        )
        "$lower(RemoteIntrinsicSize.${if (type == RcDimensionType.INTRINSIC_MIN) "Min" else "Max"})"
      }
      RcDimensionType.FILL_PARENT_MAX_WIDTH,
      RcDimensionType.FILL_PARENT_MAX_HEIGHT -> {
        import("androidx.compose.remote.creation.compose.modifier.fillParentMax$axis")
        "fillParentMax$axis(${floatValue(value)})"
      }
      else -> {
        gap("$lower dimension type $type is not generated")
        null
      }
    }
  }

  private fun padding(padding: RcPaddingModifier): String {
    import("androidx.compose.remote.creation.compose.modifier.padding")
    val sides = listOf(padding.left, padding.top, padding.right, padding.bottom)
    val written =
      if (densityBehavior == RcHeader.DENSITY_BEHAVIOR_DP) {
        // In a dp document the RemoteFloat overload divides by the density, so every side,
        // a variable one included, is written as a RemoteDp.
        sides.map { lengthDp(it) }
      } else {
        val values = sides.map { length(it) }
        // A length that fell back to pixels is a RemoteFloat, which this overload writes as it
        // is; padding takes four of one kind.
        val mixed = values.any { it.endsWith(".rdp") } && values.any { !it.endsWith(".rdp") }
        if (mixed) sides.map { floatValue(it) } else values
      }
    return when {
      written.distinct().size == 1 -> "padding(${written[0]})"
      written[0] == written[2] && written[1] == written[3] ->
        "padding(horizontal = ${written[0]}, vertical = ${written[1]})"
      else -> "padding(${written.joinToString(", ")})"
    }
  }

  private fun sizeIn(name: String, minimum: RcFloatWord, maximum: RcFloatWord): String {
    import(
      "androidx.compose.remote.creation.compose.modifier.$name",
      "androidx.compose.remote.creation.compose.state.rdp",
    )
    val arguments = mutableListOf<String>()
    fun bound(word: RcFloatWord): String =
      when {
        word.isNaNEncoded -> {
          // A pixels document's bound is written as dp times the density, so a variable that
          // holds pixels would be scaled twice.
          if (densityBehavior == RcHeader.DENSITY_BEHAVIOR_PIXELS) {
            gap(
              "a $name bound reads ${describeId(word.referencedId!!)} in pixels; it is written " +
                "as dp, which the creation API scales by the density again"
            )
          }
          import("androidx.compose.remote.creation.compose.state.asRemoteDp")
          "${floatValue(word)}.asRemoteDp()"
        }
        densityBehavior == RcHeader.DENSITY_BEHAVIOR_PIXELS -> dp(word.value / density)
        else -> dp(word.value)
      }
    if (!(minimum.isLiteral(0f))) arguments += "min = ${bound(minimum)}"
    if (!(maximum.isLiteral(Float.MAX_VALUE))) arguments += "max = ${bound(maximum)}"
    return "$name(${arguments.joinToString(", ")})"
  }

  private fun background(background: RcBackgroundModifier): String {
    import("androidx.compose.remote.creation.compose.modifier.background")
    val color =
      if (background.usesColorId) colorById(background.colorId)
      else rgbaColor(background.red, background.green, background.blue, background.alpha)
    if (background.shapeType == RcBackgroundModifier.SHAPE_CIRCLE) {
      gap("a circular background is drawn as a rectangle")
    }
    return "background($color)"
  }

  private fun border(border: RcBorderModifier): String {
    if (border.wireVersion == 0) {
      // The creation API writes version 1, which players inset by half the stroke; version 0
      // centres it on the bounds. No public call writes version 0.
      gap(
        "a version 0 border is re-created as version 1, which insets its stroke by half its width"
      )
    }
    import(
      "androidx.compose.remote.creation.compose.modifier.border",
      "androidx.compose.remote.creation.compose.state.rdp",
    )
    val color =
      if (border.usesColorId) colorById(border.colorId)
      else rgbaColor(border.red, border.green, border.blue, border.alpha)
    // A pre-1.1 legacy document already holds its border width in dp.
    val width =
      if (!border.borderWidth.isNaNEncoded && RcDocumentIndex.bordersInDp(header)) {
        import("androidx.compose.remote.creation.compose.state.rdp")
        dp(border.borderWidth.value)
      } else {
        lengthDp(border.borderWidth)
      }
    val shape =
      when {
        border.shapeType == RcBackgroundModifier.SHAPE_CIRCLE -> {
          import("androidx.compose.remote.creation.compose.shapes.RemoteCircleShape")
          ", RemoteCircleShape"
        }
        !border.roundedCorner.isLiteral(0f) -> {
          import("androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape")
          ", RemoteRoundedCornerShape(${lengthDp(border.roundedCorner)})"
        }
        else -> ""
      }
    return "border($width, $color$shape)"
  }

  private fun roundedClip(clip: RcRoundedClipRectModifier): String {
    import(
      "androidx.compose.remote.creation.compose.modifier.clip",
      "androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape",
    )
    val corners = listOf(clip.topStart, clip.topEnd, clip.bottomEnd, clip.bottomStart)
    val values = corners.map { lengthDp(it) }
    return if (values.distinct().size == 1) "clip(RemoteRoundedCornerShape(${values[0]}))"
    else
      "clip(RemoteRoundedCornerShape(topStart = ${values[0]}, topEnd = ${values[1]}, " +
        "bottomEnd = ${values[2]}, bottomStart = ${values[3]}))"
  }

  private fun semantics(semantics: RcAccessibilitySemantics): String {
    import("androidx.compose.remote.creation.compose.modifier.semantics")
    val properties = mutableListOf<String>()
    if (semantics.contentDescriptionId != 0) {
      import("androidx.compose.remote.creation.compose.modifier.contentDescription")
      properties += "contentDescription = ${string(semantics.contentDescriptionId)}"
    }
    if (semantics.textId != 0) {
      import("androidx.compose.remote.creation.compose.modifier.text")
      properties += "text = ${string(semantics.textId)}"
    }
    if (semantics.stateDescriptionId != 0) {
      import("androidx.compose.remote.creation.compose.modifier.stateDescription")
      properties += "stateDescription = ${string(semantics.stateDescriptionId)}"
    }
    ROLES[semantics.role]?.takeIf { semantics.role != RcAccessibilitySemantics.ROLE_UNKNOWN }
      ?.let {
        import(
          "androidx.compose.remote.creation.compose.modifier.role",
          "androidx.compose.ui.semantics.Role",
        )
        properties += "role = Role.$it"
      }
    if (!semantics.enabled) {
      import("androidx.compose.remote.creation.compose.modifier.enabled")
      properties += "enabled = false"
    }
    val block = if (properties.isEmpty()) "{}" else "{ ${properties.joinToString("; ")} }"
    return when (semantics.mode) {
      RcAccessibilitySemantics.MODE_CLEAR_AND_SET -> {
        import("androidx.compose.remote.creation.compose.modifier.clearAndSetSemantics")
        "clearAndSetSemantics $block"
      }
      RcAccessibilitySemantics.MODE_MERGE -> "semantics(mergeDescendants = true) $block"
      else -> "semantics $block"
    }
  }

  private fun RcAccessibilitySemantics.isClickRole(): Boolean =
    contentDescriptionId == 0 &&
      textId == 0 &&
      stateDescriptionId == 0 &&
      mode == RcAccessibilitySemantics.MODE_SET &&
      enabled &&
      !clickable &&
      role in ROLES.keys

  private fun clickable(click: RcOperationNode, semantics: RcAccessibilitySemantics?): String {
    import("androidx.compose.remote.creation.compose.modifier.clickable")
    val written = click.children.filterNot { it.operation.isSilent() }
    val actions = written.mapNotNull { action(it.operation) }
    val action =
      when (actions.size) {
        0 -> {
          // An action that could not be written has already said so.
          if (written.isEmpty()) gap("a click handler with no action; it does nothing")
          import("androidx.compose.remote.creation.compose.action.combinedAction")
          "combinedAction()"
        }
        1 -> actions.single()
        else -> {
          import("androidx.compose.remote.creation.compose.action.combinedAction")
          "combinedAction(${actions.joinToString(", ")})"
        }
      }
    val role =
      when (semantics) {
        null -> ", role = null"
        else ->
          if (semantics.role == RcAccessibilitySemantics.ROLE_BUTTON) ""
          else {
            import("androidx.compose.ui.semantics.Role")
            ", role = Role.${ROLES.getValue(semantics.role)}"
          }
      }
    return "clickable($action$role)"
  }

  private fun action(operation: RcOperation): String? =
    when (operation) {
      is RcHostAction -> {
        import("androidx.compose.remote.creation.compose.action.hostAction")
        "hostAction(${operation.actionId})"
      }
      is RcHostMetadataAction -> {
        import("androidx.compose.remote.creation.compose.action.hostAction")
        "hostAction(${operation.actionId}, ${string(operation.metadataTextId)})"
      }
      is RcHostNamedAction -> {
        import("androidx.compose.remote.creation.compose.action.hostAction")
        val name = string(operation.nameTextId)
        when (val value = operation.value) {
          RcHostNamedActionValue.None -> "hostAction($name)"
          is RcHostNamedActionValue.TextValue -> "hostAction($name, ${string(value.valueId)})"
          is RcHostNamedActionValue.IntegerValue -> "hostAction($name, ${intById(value.valueId)})"
          is RcHostNamedActionValue.FloatValue ->
            "hostAction($name, ${floatValue(RcFloatWord(nanId(value.valueId)))})"
          is RcHostNamedActionValue.FloatListValue -> {
            gap("host action ${texts[operation.nameTextId]} sends a float list; it sends nothing")
            "hostAction($name)"
          }
        }
      }
      else -> {
        gap("click action ${describe(operation)} is not generated")
        null
      }
    }

  // --- values -------------------------------------------------------------------------------

  private fun hoist(preferred: String, initializer: String): String {
    hoistedNames[initializer]?.let {
      return it
    }
    var name = preferred
    var suffix = 2
    while (name in hoisted) name = "$preferred${suffix++}"
    hoisted[name] = initializer
    hoistedNames[initializer] = name
    return name
  }

  /** A text id as a `RemoteString` expression. */
  private fun string(id: Int): String {
    named[id]
      ?.takeIf { it.type == RcNamedVariable.STRING_TYPE }
      ?.let { variable ->
        import(
          "androidx.compose.runtime.remember",
          "androidx.compose.remote.creation.compose.state.RemoteString",
        )
        reportComputed(id, variable.name)
        val (name, domain) = domainOf(variable.name)
        return hoist(
          identifier(name),
          "remember { RemoteString.createNamedRemoteString(${quote(name)}, ${quote(texts[id] ?: "")}$domain) }",
        )
      }
    texts[id]?.let {
      import("androidx.compose.remote.creation.compose.state.rs")
      return "${quote(it)}.rs"
    }
    gap("text reads ${describeId(id)}; its value is not generated")
    import("androidx.compose.remote.creation.compose.state.rs")
    return "\"\".rs"
  }

  private fun intById(id: Int): String {
    named[id]
      ?.takeIf { it.type == RcNamedVariable.INT_TYPE }
      ?.let { variable ->
        import("androidx.compose.remote.creation.compose.state.rememberNamedRemoteInt")
        reportComputed(id, variable.name)
        val (name, domain) = domainOf(variable.name)
        return hoist(
          identifier(name),
          "rememberNamedRemoteInt(${quote(name)}, ${ints[id] ?: 0}$domain)",
        )
      }
    import("androidx.compose.remote.creation.compose.state.ri")
    ints[id]?.let {
      return "$it.ri"
    }
    gap("an integer reads ${describeId(id)}; 0 is used instead")
    return "0.ri"
  }

  /** A float word for a call that only takes a literal `Float`. */
  private fun floatLiteral(word: RcFloatWord, what: String): String {
    if (!word.isNaNEncoded) return float(word.value)
    gap("$what reads ${describeId(word.referencedId!!)}; 0 is used instead")
    return "0f"
  }

  /** A float word as a `RemoteFloat` expression: a literal, or the value an id holds. */
  private fun floatValue(word: RcFloatWord): String {
    if (!word.isNaNEncoded) return literalFloat(word.value)
    val id = word.referencedId!!
    named[id]
      ?.takeIf { it.type == RcNamedVariable.FLOAT_TYPE }
      ?.let { variable ->
        import(
          "androidx.compose.runtime.remember",
          "androidx.compose.remote.creation.compose.state.RemoteFloat",
        )
        reportComputed(id, variable.name)
        val (name, domain) = domainOf(variable.name)
        val default = floats[id]?.takeUnless { it.isNaNEncoded }?.value ?: 0f
        return hoist(
          identifier(name),
          "remember { RemoteFloat.createNamedRemoteFloat(${quote(name)}, ${float(default)}$domain) }",
        )
      }
    floats[id]
      ?.takeUnless { it.isNaNEncoded }
      ?.let {
        return literalFloat(it.value)
      }
    gap("a value reads ${describeId(id)}; 0 is used instead")
    return literalFloat(0f)
  }

  private fun literalFloat(value: Float): String {
    import("androidx.compose.remote.creation.compose.state.rf")
    return "${float(value)}.rf"
  }

  /** A colour id as a `RemoteColor` expression. */
  private fun colorById(id: Int): String {
    named[id]
      ?.takeIf { it.type == RcNamedVariable.COLOR_TYPE }
      ?.let { variable ->
        import(
          "androidx.compose.remote.creation.compose.state.rememberNamedRemoteColor",
          "androidx.compose.ui.graphics.Color",
        )
        reportComputed(id, variable.name)
        val (name, domain) = domainOf(variable.name)
        return hoist(
          identifier(name),
          "rememberNamedRemoteColor(${quote(name)}, ${colorLiteral(colors[id] ?: 0)}$domain)",
        )
      }
    colors[id]?.let {
      return literalColor(it)
    }
    gap("a colour reads ${describeId(id)}; black is used instead")
    return literalColor(0xFF000000.toInt())
  }

  private fun literalColor(argb: Int): String {
    import(
      "androidx.compose.remote.creation.compose.state.RemoteColor",
      "androidx.compose.ui.graphics.Color",
    )
    return "RemoteColor(${colorLiteral(argb)})"
  }

  private fun rgbaColor(
    red: RcFloatWord,
    green: RcFloatWord,
    blue: RcFloatWord,
    alpha: RcFloatWord,
  ): String {
    val channels = listOf(alpha, red, green, blue)
    if (channels.any { it.isNaNEncoded }) {
      gap("a colour with a channel read from a variable is drawn black")
      return literalColor(0xFF000000.toInt())
    }
    val exact = channels.all { (it.value * 255f).let { v -> v == kotlin.math.round(v) } }
    if (!exact) {
      import(
        "androidx.compose.remote.creation.compose.state.RemoteColor",
        "androidx.compose.ui.graphics.Color",
      )
      return "RemoteColor(Color(red = ${float(red.value)}, green = ${float(green.value)}, " +
        "blue = ${float(blue.value)}, alpha = ${float(alpha.value)}))"
    }
    val argb =
      channels.fold(0) { acc, word -> (acc shl 8) or kotlin.math.round(word.value * 255f).toInt() }
    return literalColor(argb)
  }

  private fun colorLiteral(argb: Int): String =
    "Color(0x${argb.toUInt().toString(16).uppercase().padStart(8, '0')})"

  /**
   * A padding or offset length. In a dp document the creation API writes dp; otherwise it writes
   * the dp times the capture density, so the pixels divide back by it when they do so exactly.
   */
  private fun length(word: RcFloatWord): String {
    if (word.isNaNEncoded) return floatValue(word)
    if (densityBehavior == RcHeader.DENSITY_BEHAVIOR_DP) {
      import("androidx.compose.remote.creation.compose.state.rdp")
      return dp(word.value)
    }
    val dp = word.value / density
    if (dp * density == word.value) {
      import("androidx.compose.remote.creation.compose.state.rdp")
      return dp(dp)
    }
    return floatValue(word)
  }

  /** [length], for a call that only takes a `RemoteDp`. */
  private fun lengthDp(word: RcFloatWord): String {
    if (word.isNaNEncoded) {
      if (densityBehavior != RcHeader.DENSITY_BEHAVIOR_DP) {
        gap(
          "a length reads ${describeId(word.referencedId!!)} in pixels; it is written as dp, " +
            "which the creation API scales by the density again"
        )
      }
      import("androidx.compose.remote.creation.compose.state.asRemoteDp")
      return "${floatValue(word)}.asRemoteDp()"
    }
    import("androidx.compose.remote.creation.compose.state.rdp")
    val dp =
      if (densityBehavior == RcHeader.DENSITY_BEHAVIOR_DP) word.value else word.value / density
    return dp(dp)
  }

  private fun dp(value: Float): String = "${number(value)}.rdp"

  private fun number(value: Float): String =
    if (value == value.toInt().toFloat() && kotlin.math.abs(value) < 1e9f) value.toInt().toString()
    else float(value)

  private fun float(value: Float): String =
    when {
      value.isNaN() -> "Float.NaN"
      value == Float.MAX_VALUE -> "Float.MAX_VALUE"
      value == Float.POSITIVE_INFINITY -> "Float.POSITIVE_INFINITY"
      value == Float.NEGATIVE_INFINITY -> "Float.NEGATIVE_INFINITY"
      value == value.toInt().toFloat() && kotlin.math.abs(value) < 1e9f -> "${value.toInt()}f"
      else -> "${value}f"
    }

  // --- arrangement and alignment ------------------------------------------------------------

  private fun horizontalArrangement(positioning: Int, spacedBy: RcFloatWord): String? =
    arrangementOf(horizontalArrangementOf(positioning, spacedBy))

  private fun verticalArrangement(positioning: Int, spacedBy: RcFloatWord, default: Int): String? =
    arrangementOf(verticalArrangementOf(positioning, spacedBy, default))

  private fun arrangementOf(arrangement: String?): String? {
    if (arrangement != null)
      import("androidx.compose.remote.creation.compose.layout.RemoteArrangement")
    return arrangement
  }

  private fun horizontalArrangementOf(positioning: Int, spacedBy: RcFloatWord): String? {
    if (!spacedBy.isLiteral(0f)) {
      return "RemoteArrangement.spacedBy(${spacing(spacedBy)}${alignmentFor(positioning, horizontal = true)})"
    }
    return when (positioning) {
      START -> null
      CENTER -> "RemoteArrangement.Center"
      END -> "RemoteArrangement.End"
      SPACE_BETWEEN -> "RemoteArrangement.SpaceBetween"
      SPACE_EVENLY -> "RemoteArrangement.SpaceEvenly"
      SPACE_AROUND -> "RemoteArrangement.SpaceAround"
      else -> {
        gap("horizontal arrangement $positioning is not generated")
        null
      }
    }
  }

  private fun verticalArrangementOf(
    positioning: Int,
    spacedBy: RcFloatWord,
    default: Int,
  ): String? {
    if (!spacedBy.isLiteral(0f)) {
      return "RemoteArrangement.spacedBy(${spacing(spacedBy)}${alignmentFor(positioning, horizontal = false)})"
    }
    if (positioning == default) return null
    return when (positioning) {
      TOP -> "RemoteArrangement.Top"
      CENTER -> "RemoteArrangement.Center"
      BOTTOM -> "RemoteArrangement.Bottom"
      SPACE_BETWEEN -> "RemoteArrangement.SpaceBetween"
      SPACE_EVENLY -> "RemoteArrangement.SpaceEvenly"
      SPACE_AROUND -> "RemoteArrangement.SpaceAround"
      else -> {
        gap("vertical arrangement $positioning is not generated")
        null
      }
    }
  }

  /** The alignment a `spacedBy` arrangement carries, unless it is the arrangement's default. */
  private fun alignmentFor(positioning: Int, horizontal: Boolean): String {
    val alignment =
      when {
        horizontal && positioning == START -> ""
        !horizontal && positioning == TOP -> ""
        horizontal && positioning == CENTER -> ", RemoteAlignment.CenterHorizontally"
        horizontal && positioning == END -> ", RemoteAlignment.End"
        !horizontal && positioning == CENTER -> ", RemoteAlignment.CenterVertically"
        !horizontal && positioning == BOTTOM -> ", RemoteAlignment.Bottom"
        else -> {
          // spacedBy takes an alignment, not a distribution: SpaceBetween, SpaceEvenly and
          // SpaceAround add their gap to the spacing at playback, which no call can say.
          gap(
            "spacing combined with a distributed arrangement ($positioning) keeps only the spacing"
          )
          ""
        }
      }
    if (alignment.isNotEmpty())
      import("androidx.compose.remote.creation.compose.layout.RemoteAlignment")
    return alignment
  }

  /**
   * A `spacedBy` length. In a dp document the RemoteFloat overload divides by the density, so a dp
   * document's spacing is always a RemoteDp; otherwise it writes pixels as they are.
   */
  private fun spacing(word: RcFloatWord): String =
    if (densityBehavior == RcHeader.DENSITY_BEHAVIOR_DP) lengthDp(word) else length(word)

  private fun horizontalAlignment(positioning: Int, default: Int): String? {
    if (positioning == default) return null
    import("androidx.compose.remote.creation.compose.layout.RemoteAlignment")
    return when (positioning) {
      START -> "RemoteAlignment.Start"
      CENTER -> "RemoteAlignment.CenterHorizontally"
      END -> "RemoteAlignment.End"
      else -> {
        gap("horizontal alignment $positioning is not generated")
        null
      }
    }
  }

  private fun verticalAlignment(positioning: Int, default: Int): String? {
    if (positioning == default) return null
    import("androidx.compose.remote.creation.compose.layout.RemoteAlignment")
    return when (positioning) {
      TOP -> "RemoteAlignment.Top"
      CENTER -> "RemoteAlignment.CenterVertically"
      BOTTOM -> "RemoteAlignment.Bottom"
      else -> {
        gap("vertical alignment $positioning is not generated")
        null
      }
    }
  }

  private fun boxAlignment(horizontal: Int, vertical: Int): String? {
    if (horizontal == START && vertical == TOP) return null
    val h =
      when (horizontal) {
        START -> "Start"
        CENTER -> "Center"
        END -> "End"
        else -> null
      }
    val v =
      when (vertical) {
        TOP -> "Top"
        CENTER -> "Center"
        BOTTOM -> "Bottom"
        else -> null
      }
    if (h == null || v == null) {
      gap("box alignment ($horizontal, $vertical) is not generated")
      return null
    }
    import("androidx.compose.remote.creation.compose.layout.RemoteAlignment")
    return "RemoteAlignment." + if (h == "Center" && v == "Center") "Center" else "$v$h"
  }

  // --- naming -------------------------------------------------------------------------------

  /**
   * Reports a named value that the document computes, rather than holds as a constant. A
   * `createNamed…Expression` writes an expression and names its output; the source writes the name
   * with its default instead, so the computation is lost until the host sets the value.
   */
  private fun reportComputed(id: Int, name: String) {
    if (producers[id] != null) {
      gap("named value \"$name\" is computed by ${describeId(id)}; only its name is generated")
    }
  }

  /** A named value's name and the `domain` argument that writes its prefix back. */
  private fun domainOf(full: String): Pair<String, String> {
    val separator = full.indexOf(':')
    if (separator < 0) {
      gap("named value \"$full\" has no domain; it is written into the user domain")
      return full to ""
    }
    val name = full.substring(separator + 1)
    return when (full.substring(0, separator)) {
      "USER" -> name to ""
      "SYSTEM" -> {
        import("androidx.compose.remote.creation.compose.state.RemoteState")
        name to ", domain = RemoteState.Domain.System"
      }
      else -> {
        gap(
          "named value \"$full\" is in a domain the creation API cannot name; it is written into the user domain"
        )
        full to ""
      }
    }
  }

  private fun identifier(name: String): String {
    val words = name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
    val joined =
      words
        .mapIndexed { index, word ->
          if (index == 0) word.replaceFirstChar { it.lowercase() }
          else word.replaceFirstChar { it.uppercase() }
        }
        .joinToString("")
    val candidate = if (joined.isEmpty() || joined[0].isDigit()) "value$joined" else joined
    return if (candidate in KEYWORDS) "${candidate}Value" else candidate
  }

  private fun quote(text: String): String = buildString {
    append('"')
    text.forEach { char ->
      when (char) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '$' -> append("\\$")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else ->
          if (char.code < 0x20) append("\\u" + char.code.toString(16).padStart(4, '0'))
          else append(char)
      }
    }
    append('"')
  }

  private fun describeId(id: Int): String = index.describeId(id)

  private fun describe(operation: RcOperation): String = RcDocumentIndex.describe(operation)

  private fun RcFloatWord.isLiteral(value: Float): Boolean = !isNaNEncoded && this.value == value

  private fun nanId(id: Int): Int = 0x7fc00000 or (id and 0x003fffff)

  private companion object {
    // AndroidX `ColumnLayout` positioning values.
    const val START = 1
    const val CENTER = 2
    const val END = 3
    const val TOP = 4
    const val BOTTOM = 5
    const val SPACE_BETWEEN = 6
    const val SPACE_EVENLY = 7
    const val SPACE_AROUND = 8

    // AndroidX `ImageScaling` values.
    const val SCALE_NONE = 0
    const val SCALE_INSIDE = 1
    const val SCALE_FILL_WIDTH = 2
    const val SCALE_FILL_HEIGHT = 3
    const val SCALE_FIT = 4
    const val SCALE_CROP = 5
    const val SCALE_FILL_BOUNDS = 6

    // AndroidX `TextStyle` property ids.
    const val TEXT_ID = 1
    const val TEXT_ANIMATION_ID = 2
    const val TEXT_COLOR = 3
    const val TEXT_COLOR_ID = 4
    const val TEXT_FONT_SIZE = 5
    const val TEXT_FONT_STYLE = 6
    const val TEXT_FONT_WEIGHT = 7
    const val TEXT_FONT_FAMILY = 8
    const val TEXT_ALIGN = 9
    const val TEXT_OVERFLOW = 10
    const val TEXT_MAX_LINES = 11

    /** How much of an image's Base64 goes into one string literal: under the JVM's 65,535. */
    const val BASE64_CHUNK = 60_000
    const val TEXT_LETTER_SPACING = 12
    const val TEXT_LINE_HEIGHT_MULTIPLIER = 14
    const val TEXT_UNDERLINE = 18
    const val TEXT_STRIKETHROUGH = 19
    const val TEXT_FONT_AXIS = 20
    const val TEXT_FONT_AXIS_VALUES = 21
    const val TEXT_STYLE_PARENT = 24

    /** A `CoreText` font size the document omits: AndroidX's `TextStyle` default. */
    const val WIRE_DEFAULT_FONT_SIZE = 36f

    val TEXT_PROPERTY_NAMES: Map<Int, String> =
      mapOf(
        12 to "letterSpacing",
        13 to "lineHeightAdd",
        14 to "lineHeightMultiplier",
        15 to "lineBreakStrategy",
        16 to "hyphenationFrequency",
        17 to "justificationMode",
        18 to "underline",
        19 to "strikethrough",
        20 to "fontAxis",
        21 to "fontAxisValues",
        22 to "autosize",
        24 to "parentStyle",
        25 to "minFontSize",
        26 to "maxFontSize",
      )

    val FONT_WEIGHTS: Map<Int, String> =
      mapOf(
        100 to "Thin",
        200 to "ExtraLight",
        300 to "Light",
        400 to "Normal",
        500 to "Medium",
        600 to "SemiBold",
        700 to "Bold",
        800 to "ExtraBold",
        900 to "Black",
      )

    val ROLES: Map<Int, String> =
      mapOf(
        RcAccessibilitySemantics.ROLE_BUTTON to "Button",
        RcAccessibilitySemantics.ROLE_CHECKBOX to "Checkbox",
        RcAccessibilitySemantics.ROLE_SWITCH to "Switch",
        RcAccessibilitySemantics.ROLE_RADIO_BUTTON to "RadioButton",
        RcAccessibilitySemantics.ROLE_TAB to "Tab",
        RcAccessibilitySemantics.ROLE_IMAGE to "Image",
        RcAccessibilitySemantics.ROLE_DROPDOWN_LIST to "DropdownList",
      )

    val KEYWORDS: Set<String> =
      setOf(
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while",
      )
  }
}

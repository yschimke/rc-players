package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw3
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDraw6
import ee.schimke.composeai.rcplayer.protocol.RcFitBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFlowLayout
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier

/**
 * How [toUiBuilderDesign] writes its design.
 *
 * @property designId the design's id, which is also how the UI Builder addresses it.
 * @property title the design's title.
 * @property density the density the document was captured at, as for [RcKotlinOptions.density].
 * @property theme the environment theme the design opens in: `light` or `dark`.
 */
public data class RcUiBuilderOptions(
  val designId: String = "remote-document",
  val title: String = "Remote document",
  val density: Float? = null,
  val theme: String = "dark",
)

/**
 * A Compose UI Builder design (a `.uid` file's `compose-ui-builder-document/v1-candidate` JSON)
 * re-creating a document in the `remote-m3` catalog.
 *
 * @property json the design document.
 * @property assets the images the design's `asset/image` nodes name, by asset key, as PNG.
 * @property unsupported what the design could not say, one line each, in document order. A design
 *   is a design vocabulary rather than a program, so this is longer than [RcKotlinSource]'s for the
 *   same document: named host values, actions and most text styling have no node property.
 */
public class RcUiBuilderDesign(
  public val json: String,
  public val assets: Map<String, ByteArray>,
  public val unsupported: List<String>,
) {
  /** Whether nothing in the document was left out or approximated. */
  public val isComplete: Boolean
    get() = unsupported.isEmpty()
}

/**
 * Turns a decoded document into a Compose UI Builder design: the sibling of [toRemoteComposeKotlin]
 * for a person who wants to keep designing the screen rather than keep its code. Both read the same
 * container tree and the same value index, so they agree on what the document says.
 *
 * Layouts become `layout/box`, `layout/row`, `layout/column`, `layout/flow-row`, `layout/fit-box`
 * and the collapsible layouts; text becomes `remote-m3/remote-text`; images become `asset/image`
 * with their bytes in [RcUiBuilderDesign.assets]; and a canvas becomes `draw/canvas` with a
 * `draw/rect`, `draw/circle`, `draw/line`, `draw/oval` or `draw/arc` for each primitive whose
 * geometry is constant, in the colour, style and stroke the paint had when it drew. A theme colour
 * named `<system>.<role>` (`WearM3.onSurface`) becomes that role's colour token.
 *
 * What the `remote-m3` catalog has no node or property for is listed in
 * [RcUiBuilderDesign.unsupported] — that list is the catalog's to-do list as much as the
 * document's. The output is not validated here: the UI Builder's `ui_builder_check_design` is the
 * authority on the schema, and the design should be run through it before anyone edits it.
 */
public fun RcDocument.toUiBuilderDesign(
  options: RcUiBuilderOptions = RcUiBuilderOptions()
): RcUiBuilderDesign = RcUiBuilderWriter(this, options).write()

/** A JSON value, written in insertion order. */
private sealed interface Json {
  data class Str(val value: String) : Json

  data class Num(val value: Float) : Json

  data class IntNum(val value: Int) : Json

  data class Bool(val value: Boolean) : Json

  data class Arr(val values: List<Json>) : Json

  class Obj(val fields: LinkedHashMap<String, Json> = linkedMapOf()) : Json {
    operator fun set(key: String, value: Json) {
      fields[key] = value
    }
  }
}

private fun obj(vararg fields: Pair<String, Json>): Json.Obj = Json.Obj(linkedMapOf(*fields))

private fun str(value: String) = Json.Str(value)

private fun num(value: Float) = Json.Num(value)

private fun int(value: Int) = Json.IntNum(value)

/** A node property, wrapped in its kind as the design schema wants it. */
private fun prop(type: String, value: Json) = obj("type" to str(type), "value" to value)

private class RcUiBuilderWriter(
  private val document: RcDocument,
  private val options: RcUiBuilderOptions,
) {
  private val header = document.header
  private val density = RcDocumentIndex.densityOf(header, options.density)
  private val index = RcDocumentIndex(document)
  private val nodes = linkedMapOf<String, Json.Obj>()
  private val assets = linkedMapOf<String, ByteArray>()
  private val unsupported = mutableListOf<String>()
  private val ids = mutableMapOf<String, Int>()

  fun write(): RcUiBuilderDesign {
    val tree = operationTree(document.operations)
    val roots = mutableListOf<String>()
    when {
      tree == null -> gap("the document's containers do not balance; nothing was generated")
      else -> {
        val root = tree.firstOrNull { it.operation is RcRootLayout }
        if (root == null) gap("the document has no root layout; nothing was generated")
        tree.filter { it.operation !is RcRootLayout }.forEach { statement(it) }
        root
          ?.let { content(it.children) }
          ?.let { children ->
            // A design has at most one root: wrap several in a box, as the document's root lays
            // its children over each other.
            roots +=
              when (children.size) {
                0 -> return@let
                1 -> children.single()
                else -> node("root", "layout/box", Json.Obj(), slots("children" to children), null)
              }
          }
      }
    }
    val design =
      obj(
        "schema" to str("compose-ui-builder-document/v1-candidate"),
        "id" to str(options.designId),
        "title" to str(options.title),
        "revision" to int(0),
        "catalogPin" to
          obj(
            "systemId" to str("remote-m3"),
            "catalogRevision" to str("wear-widget-scaffolds-v1"),
            "capabilityDigest" to str("candidate"),
            "nativeRuntimeId" to str("candidate"),
          ),
        "environment" to
          obj(
            "widthDp" to num(dpOfHeader(header.width)),
            "heightDp" to num(dpOfHeader(header.height)),
            "density" to num(density),
            "theme" to str(options.theme),
            "locale" to str("en-US"),
            "fontScale" to num(1f),
            "layoutDirection" to str("ltr"),
          ),
        "roots" to Json.Arr(roots.map { str(it) }),
        "nodes" to Json.Obj(LinkedHashMap(nodes)),
      )
    return RcUiBuilderDesign(render(design), assets.toMap(), unsupported.toList())
  }

  private fun dpOfHeader(size: Int): Float =
    if (header.densityBehavior == RcHeader.DENSITY_BEHAVIOR_DP) size.toFloat() else size / density

  private fun gap(message: String) {
    unsupported += message
  }

  private fun newId(base: String): String {
    val count = (ids[base] ?: 0) + 1
    ids[base] = count
    return if (count == 1) base else "$base-$count"
  }

  private fun slots(vararg slots: Pair<String, List<String>>): Json.Obj =
    obj(*slots.map { (name, ids) -> name to Json.Arr(ids.map { str(it) }) }.toTypedArray())

  private fun node(
    base: String,
    componentId: String,
    properties: Json.Obj,
    slots: Json.Obj?,
    modifiers: List<Json>?,
  ): String {
    val id = newId(base)
    val node = obj("id" to str(id), "componentId" to str(componentId), "properties" to properties)
    if (slots != null) node["slots"] = slots
    if (!modifiers.isNullOrEmpty()) node["modifiers"] = Json.Arr(modifiers)
    nodes[id] = node
    return id
  }

  // --- the component tree -------------------------------------------------------------------

  private fun content(children: List<RcOperationNode>): List<String> = children.flatMap { child ->
    if (child.operation is RcLayoutContent) content(child.children)
    else listOfNotNull(statement(child))
  }

  private fun statement(node: RcOperationNode): String? {
    val operation = node.operation
    return when (operation) {
      is RcBoxLayout ->
        container(node, "box", "layout/box") { properties ->
          boxAlignment(operation.horizontalPositioning, operation.verticalPositioning)?.let {
            properties["contentAlignment"] = prop("string", str(it))
          }
        }
      is RcRowLayout ->
        container(node, "row", "layout/row") { properties ->
          horizontal(properties, operation.horizontalPositioning, operation.spacedBy)
          vertical(properties, "verticalAlignment", operation.verticalPositioning, ALIGNMENTS_V)
        }
      is RcCollapsibleRowLayout ->
        container(node, "row", "layout/collapsible-row") { properties ->
          horizontal(properties, operation.horizontalPositioning, operation.spacedBy)
          vertical(properties, "verticalAlignment", operation.verticalPositioning, ALIGNMENTS_V)
        }
      is RcColumnLayout ->
        container(node, "column", "layout/column") { properties ->
          verticalArrangement(properties, operation.verticalPositioning, operation.spacedBy)
          horizontalAlignment(properties, operation.horizontalPositioning)
        }
      is RcCollapsibleColumnLayout ->
        container(node, "column", "layout/collapsible-column") { properties ->
          verticalArrangement(properties, operation.verticalPositioning, operation.spacedBy)
          horizontalAlignment(properties, operation.horizontalPositioning)
        }
      is RcFlowLayout ->
        container(node, "flow", "layout/flow-row") { properties ->
          horizontal(properties, operation.horizontalPositioning, operation.spacedBy)
          vertical(properties, "verticalArrangement", operation.verticalPositioning, ARRANGEMENTS)
          if (operation.maxItemsInEachRow != Int.MAX_VALUE) {
            properties["maxItemsInEachRow"] =
              prop("float", num(operation.maxItemsInEachRow.toFloat()))
          }
          if (operation.maxLines != Int.MAX_VALUE) gap("flow row maxLines has no property")
        }
      is RcFitBoxLayout ->
        container(node, "fit", "layout/fit-box") { properties ->
          horizontalAlignment(properties, operation.horizontalPositioning)
          vertical(properties, "verticalArrangement", operation.verticalPositioning, ALIGNMENTS_V)
        }
      is RcStateLayout -> {
        gap(
          "state layout ${operation.componentId} switches on integer ${operation.indexId}; " +
            "only its first branch is generated"
        )
        node.children
          .firstOrNull { it.operation is RcLayoutContent }
          ?.children
          ?.firstOrNull()
          ?.let { statement(it) }
      }
      is RcCoreText -> coreText(node, operation)
      is RcTextLayout -> textLayout(node, operation)
      is RcImageLayout -> image(node, operation)
      is RcCanvasLayout -> canvas(node)
      else -> {
        when {
          operation.opcode == RcOpcodes.CANVAS_OPERATIONS ->
            gap("drawWithContent drawing has no modifier and is not generated")
          !index.isDefinition(operation) ->
            gap("${RcDocumentIndex.describe(operation)} has no catalog component")
        }
        null
      }
    }
  }

  private fun container(
    node: RcOperationNode,
    base: String,
    componentId: String,
    properties: (Json.Obj) -> Unit,
  ): String {
    val props = Json.Obj()
    properties(props)
    val modifiers = modifiers(node)
    val children = content(node.children.filter { it.operation is RcLayoutContent })
    return node(base, componentId, props, slots("children" to children), modifiers)
  }

  // --- text ---------------------------------------------------------------------------------

  private fun coreText(node: RcOperationNode, text: RcCoreText): String {
    val properties = text.properties.associateBy { it.id }

    fun intOf(id: Int) = (properties[id] as? RcTextStyleProperty.IntValue)?.value

    fun wordOf(id: Int) = (properties[id] as? RcTextStyleProperty.FloatValue)?.value

    val props = Json.Obj()
    props["text"] = prop("string", str(textOf(text.textId)))
    // An absent size is the wire default the writer omits, not the catalog's.
    fontSize(wordOf(TEXT_FONT_SIZE) ?: RcFloatWord.literal(WIRE_DEFAULT_FONT_SIZE))?.let {
      props["fontSize"] = prop("float", num(it))
    }
    val colorId = intOf(TEXT_COLOR_ID)?.takeUnless { it == -1 }
    when {
      colorId != null -> colorString(colorId)?.let { props["color"] = prop("string", str(it)) }
      intOf(TEXT_COLOR) != null ->
        props["color"] = prop("string", str(hexOpaque(intOf(TEXT_COLOR)!!)))
    }
    fontStyle(intOf(TEXT_FONT_STYLE) ?: 0)?.let { props["fontStyle"] = prop("string", str(it)) }
    wordOf(TEXT_FONT_WEIGHT)?.let { weight ->
      fontWeight(weight)?.let { props["fontWeight"] = prop("string", str(it)) }
    }
    intOf(TEXT_ALIGN)?.let { align ->
      TEXT_ALIGNS[align and 0xffff]?.let { props["textAlign"] = prop("enum", str(it)) }
    }
    intOf(TEXT_OVERFLOW)?.let { overflow ->
      OVERFLOWS[overflow]?.let { props["overflow"] = prop("string", str(it)) }
    }
    intOf(TEXT_MAX_LINES)
      ?.takeUnless { it == Int.MAX_VALUE }
      ?.let { props["maxLines"] = prop("int", int(it)) }
    fontVariations(properties)?.let { props["fontVariationSettings"] = prop("string", str(it)) }
    if (intOf(TEXT_FONT_FAMILY)?.takeUnless { it == -1 } != null) {
      gap("text ${text.textId}'s font family has no property")
    }
    val unsaid =
      text.properties.filter { it.id !in TEXT_PROPERTIES_SAID && !it.isDefault() }.map { it.id }
    if (unsaid.isNotEmpty()) {
      gap(
        "text ${text.textId} sets style properties ${unsaid.joinToString()} that have no property"
      )
    }
    return node("text", "remote-m3/remote-text", props, null, modifiers(node))
  }

  private fun textLayout(node: RcOperationNode, text: RcTextLayout): String {
    val props = Json.Obj()
    if (text.fontFamilyId != -1) gap("text ${text.textId}'s font family has no property")
    props["text"] = prop("string", str(textOf(text.textId)))
    fontSize(text.fontSize)?.let { props["fontSize"] = prop("float", num(it)) }
    val color =
      if (text.flags and RcTextLayout.FLAG_DYNAMIC_COLOR != 0) colorString(text.color)
      else hexOpaque(text.color)
    color?.let { props["color"] = prop("string", str(it)) }
    fontStyle(text.fontStyle)?.let { props["fontStyle"] = prop("string", str(it)) }
    fontWeight(text.fontWeight)?.let { props["fontWeight"] = prop("string", str(it)) }
    TEXT_ALIGNS[text.textAlign]?.let { props["textAlign"] = prop("enum", str(it)) }
    OVERFLOWS[text.overflow]?.let { props["overflow"] = prop("string", str(it)) }
    if (text.maxLines != Int.MAX_VALUE) props["maxLines"] = prop("int", int(text.maxLines))
    return node("text", "remote-m3/remote-text", props, null, modifiers(node))
  }

  /** A wire font style as the design's: 1 is what the creation API writes for italic. */
  private fun fontStyle(style: Int): String? =
    when (style) {
      0 -> null
      1 -> "italic"
      else -> {
        gap("font style $style has no design value; the text is written with the default style")
        null
      }
    }

  /**
   * A font weight, or for one read from a variable its default where the document holds one. A
   * design property is a literal, so the binding itself is reported.
   */
  private fun fontWeight(word: RcFloatWord): String? {
    if (!word.isNaNEncoded) return word.value.toInt().toString()
    val id = word.referencedId!!
    val default = index.floats[id]?.takeUnless { it.isNaNEncoded }?.value
    gap(
      "a font weight reads ${index.describeId(id)}; " +
        if (default != null) "its default is written" else "it is not generated"
    )
    return default?.toInt()?.toString()
  }

  private fun fontSize(word: RcFloatWord): Float? {
    if (word.isNaNEncoded) {
      gap("a font size reads ${index.describeId(word.referencedId!!)}; it is not generated")
      return null
    }
    return if (header.densityBehavior == RcHeader.DENSITY_BEHAVIOR_DP) word.value
    else word.value / density
  }

  private fun fontVariations(properties: Map<Int, RcTextStyleProperty>): String? {
    val axes = (properties[TEXT_FONT_AXIS] as? RcTextStyleProperty.IntArrayValue)?.values
    val values = (properties[TEXT_FONT_AXIS_VALUES] as? RcTextStyleProperty.FloatArrayValue)?.values
    if (axes.isNullOrEmpty() || values == null || values.size != axes.size) return null
    if (values.any { it.isNaNEncoded } || axes.any { index.texts[it] == null }) {
      gap("font axes read from variables are not generated")
      return null
    }
    return axes.indices.joinToString(", ") {
      "\"${index.texts.getValue(axes[it])}\" ${format(values[it].value)}"
    }
  }

  private fun textOf(id: Int): String {
    index.named[id]
      ?.takeIf { it.type == RcNamedVariable.STRING_TYPE }
      ?.let { gap("named string \"${it.name}\" is written as its default value") }
    return index.texts[id]
      ?: run {
        gap("text reads ${index.describeId(id)}; it is written empty")
        ""
      }
  }

  // --- images and canvases ------------------------------------------------------------------

  private fun image(node: RcOperationNode, layout: RcImageLayout): String {
    val props = Json.Obj()
    val data = index.bitmaps[layout.bitmapId]
    val key = "image-${layout.bitmapId}"
    when {
      data == null -> gap("image ${layout.bitmapId} has no bitmap data in the document")
      data.encoding == RcBitmapData.ENCODING_INLINE &&
        (data.type == RcBitmapData.TYPE_PNG_8888 || data.type == RcBitmapData.TYPE_PNG) ->
        assets["$key.png"] = data.data
      else -> gap("image ${layout.bitmapId} is not an embedded PNG; its asset is not written")
    }
    props["assetKey"] = prop("string", str(key))
    index.named[layout.bitmapId]
      ?.takeIf { it.type == RcNamedVariable.IMAGE_TYPE }
      ?.let { gap("named image \"${it.name}\" is written as its default asset") }
    CONTENT_SCALES[layout.scaleType]?.let { props["contentScale"] = prop("enum", str(it)) }
      ?: gap("image scale type ${layout.scaleType} has no contentScale value")
    if (layout.alpha.isNaNEncoded || layout.alpha.value != 1f) gap("image alpha has no property")
    return node("image", "asset/image", props, null, modifiers(node))
  }

  /** The paint a canvas has set so far, as `draw` nodes take it. */
  private class Paint(
    var color: String? = "#000000",
    var stroke: Boolean = false,
    var fill: Boolean = true,
    var strokeWidth: Float = 0f,
    var cap: String? = null,
  )

  private fun canvas(node: RcOperationNode): String {
    val ops = mutableListOf<String>()
    val paint = Paint()
    val skipped = linkedMapOf<String, Int>()
    node.children
      .filter { it.operation is RcCanvasContent || it.operation is RcLayoutContent }
      .flatMap { it.children }
      .forEach { child -> child.walk { drawOp(it.operation, paint, ops, skipped) } }
    if (skipped.isNotEmpty()) {
      gap(
        "canvas operations not generated: " +
          skipped.entries.joinToString { (name, count) ->
            if (count == 1) name else "$name x$count"
          }
      )
    }
    return node("canvas", "draw/canvas", Json.Obj(), slots("ops" to ops), modifiers(node))
  }

  private fun drawOp(
    operation: RcOperation,
    paint: Paint,
    ops: MutableList<String>,
    skipped: MutableMap<String, Int>,
  ) {
    fun skip() {
      val name = RcDocumentIndex.describe(operation)
      skipped[name] = (skipped[name] ?: 0) + 1
    }
    if (index.isDefinition(operation)) return
    when (operation) {
      is RcPaintData -> if (!applyPaint(operation.words, paint)) skip()
      is RcDraw4 -> {
        val (a, b, c, d) =
          listOf(operation.first, operation.second, operation.third, operation.fourth).map {
            dp(it) ?: return skip()
          }
        when (operation.opcode) {
          RcOpcodes.DRAW_RECT -> ops += shape("rect", "draw/rect", paint, rect(a, b, c, d))
          RcOpcodes.DRAW_OVAL -> ops += shape("oval", "draw/oval", paint, rect(a, b, c, d))
          RcOpcodes.DRAW_LINE ->
            ops +=
              shape(
                "line",
                "draw/line",
                paint,
                listOf("startXDp" to a, "startYDp" to b, "endXDp" to c, "endYDp" to d),
                line = true,
              )
          else -> skip()
        }
      }
      is RcDraw3 ->
        if (operation.opcode == RcOpcodes.DRAW_CIRCLE) {
          val (x, y, r) =
            listOf(operation.first, operation.second, operation.third).map {
              dp(it) ?: return skip()
            }
          ops +=
            shape(
              "circle",
              "draw/circle",
              paint,
              listOf("centerXDp" to x, "centerYDp" to y, "radiusDp" to r),
            )
        } else skip()
      is RcDraw6 -> {
        val box =
          listOf(operation.first, operation.second, operation.third, operation.fourth).map {
            dp(it) ?: return skip()
          }
        val (left, top, right, bottom) = box
        when (operation.opcode) {
          RcOpcodes.DRAW_ROUND_RECT -> {
            if (operation.fifth.isNaNEncoded || operation.fifth.bits != operation.sixth.bits) {
              return skip()
            }
            ops +=
              shape(
                "rect",
                "draw/rect",
                paint,
                rect(left, top, right, bottom) +
                  ("cornerRadiusDp" to operation.fifth.value / density),
              )
          }
          RcOpcodes.DRAW_ARC,
          RcOpcodes.DRAW_SECTOR -> {
            if (operation.fifth.isNaNEncoded || operation.sixth.isNaNEncoded) return skip()
            val ids =
              shape(
                "arc",
                "draw/arc",
                paint,
                rect(left, top, right, bottom) +
                  listOf(
                    "startAngle" to operation.fifth.value,
                    "sweepAngle" to operation.sixth.value,
                  ),
              )
            ids.forEach { id ->
              (nodes.getValue(id).fields["properties"] as Json.Obj)["useCenter"] =
                prop("bool", Json.Bool(operation.opcode == RcOpcodes.DRAW_SECTOR))
            }
            ops += ids
          }
          else -> skip()
        }
      }
      else -> skip()
    }
  }

  private fun rect(left: Float, top: Float, right: Float, bottom: Float) =
    listOf("xDp" to left, "yDp" to top, "widthDp" to right - left, "heightDp" to bottom - top)

  /**
   * The `draw` nodes one primitive becomes in [paint]: one, or for a fill-and-stroke paint a fill
   * and then a stroke, since a node has one style and the players draw both passes in that order.
   */
  private fun shape(
    base: String,
    componentId: String,
    paint: Paint,
    geometry: List<Pair<String, Float>>,
    line: Boolean = false,
  ): List<String> {
    fun pass(stroke: Boolean): String {
      val props = Json.Obj()
      geometry.forEach { (name, value) -> props[name] = prop("float", num(value)) }
      paint.color?.let { props["color"] = prop("string", str(it)) }
      if (!line) props["style"] = prop("enum", str(if (stroke) "stroke" else "fill"))
      if (stroke || line) props["strokeWidthDp"] = prop("float", num(paint.strokeWidth / density))
      paint.cap?.let { props["strokeCap"] = prop("enum", str(it)) }
      return node(base, componentId, props, null, null)
    }
    return when {
      line -> listOf(pass(stroke = true))
      paint.fill && paint.stroke -> listOf(pass(stroke = false), pass(stroke = true))
      else -> listOf(pass(stroke = paint.stroke))
    }
  }

  /**
   * Applies an AndroidX `PaintBundle` to [paint], or false when it sets something a `draw` node has
   * no property for (a shader, a gradient, a filter, a path effect).
   */
  private fun applyPaint(words: List<Int>, paint: Paint): Boolean {
    var complete = true
    var i = 0
    try {
      while (i < words.size) {
        val command = words[i++]
        val packed = command ushr 16
        when (command and 0xffff) {
          PAINT_COLOR -> paint.color = hexOpaque(words[i++])
          PAINT_COLOR_ID -> paint.color = colorString(words[i++])
          PAINT_STROKE_WIDTH -> {
            val word = RcFloatWord(words[i++])
            if (word.isNaNEncoded) complete = false else paint.strokeWidth = word.value
          }
          PAINT_STYLE -> {
            paint.stroke = packed == STYLE_STROKE || packed == STYLE_FILL_AND_STROKE
            paint.fill = packed != STYLE_STROKE
          }
          PAINT_STROKE_CAP -> paint.cap = STROKE_CAPS[packed]
          PAINT_TEXT_SIZE -> i++
          PAINT_STROKE_MITER -> {
            // A draw node has no miter limit; Android's default, 4, is what it draws.
            val word = RcFloatWord(words[i++])
            if (word.isNaNEncoded || word.value != DEFAULT_MITER_LIMIT) {
              gap("a stroke miter limit has no design property; 4 is drawn")
            }
          }
          PAINT_ALPHA -> {
            val word = RcFloatWord(words[i++])
            if (word.isNaNEncoded || word.value != 1f) complete = false
          }
          PAINT_STROKE_JOIN ->
            // A draw node has no join; the miter default is what it draws.
            if (packed != 0) {
              gap(
                "stroke join ${STROKE_JOINS[packed] ?: packed} has no design property; miter is drawn"
              )
            }
          PAINT_ANTI_ALIAS,
          PAINT_FILTER_QUALITY,
          PAINT_FILTER_BITMAP,
          PAINT_CLEAR_COLOR_FILTER -> Unit
          PAINT_TYPEFACE,
          PAINT_FALLBACK_TYPEFACE -> i++
          else -> return false
        }
      }
    } catch (_: IndexOutOfBoundsException) {
      return false
    }
    return complete
  }

  private fun dp(word: RcFloatWord): Float? = if (word.isNaNEncoded) null else word.value / density

  // --- modifiers ----------------------------------------------------------------------------

  private fun modifiers(node: RcOperationNode): List<Json> {
    val result = mutableListOf<Json>()
    val chain =
      node.children.takeWhile {
        it.operation !is RcLayoutContent && it.operation !is RcCanvasContent
      }
    var index = 0
    while (index < chain.size) {
      val operation = chain[index].operation
      val next = chain.getOrNull(index + 1)?.operation
      if (
        operation is RcWidthModifier &&
          next is RcHeightModifier &&
          operation.type == next.type &&
          operation.value.bits == next.value.bits
      ) {
        when (operation.type) {
          RcDimensionType.FILL ->
            if (!operation.value.isNaNEncoded && operation.value.value == 1f) {
              result += obj("type" to str("fillMaxSize"))
              index += 2
              continue
            }
          RcDimensionType.EXACT_DP ->
            if (!operation.value.isNaNEncoded) {
              val size = operation.value.value
              result += obj("type" to str("size"), "widthDp" to num(size), "heightDp" to num(size))
              index += 2
              continue
            }
        }
      }
      modifier(operation)?.let { result += it }
      index++
    }
    return result
  }

  private fun modifier(operation: RcOperation): Json? =
    when (operation) {
      is RcWidthModifier -> dimension("width", "Width", operation.type, operation.value)
      is RcHeightModifier -> dimension("height", "Height", operation.type, operation.value)
      is RcPaddingModifier -> {
        val sides = listOf(operation.left, operation.top, operation.right, operation.bottom)
        if (sides.any { it.isNaNEncoded }) {
          gap("a padding read from a variable is not generated")
          null
        } else {
          val (start, top, end, bottom) = sides.map { length(it.value) }
          obj(
            "type" to str("padding"),
            "startDp" to num(start),
            "topDp" to num(top),
            "endDp" to num(end),
            "bottomDp" to num(bottom),
          )
        }
      }
      is RcBackgroundModifier -> {
        val color =
          if (operation.usesColorId) colorValue(operation.colorId)
          else
            listOf(operation.red, operation.green, operation.blue, operation.alpha)
              .takeIf { channels -> channels.none { it.isNaNEncoded } }
              ?.let { (r, g, b, a) ->
                prop("color", str(hexOf(a.value, r.value, g.value, b.value)))
              }
        if (color == null) {
          gap("a background colour read from a variable is not generated")
          null
        } else {
          val background = obj("type" to str("background"), "color" to color)
          if (operation.shapeType == RcBackgroundModifier.SHAPE_CIRCLE) {
            gap("a circular background has no shape token; it is written as a rectangle")
          }
          background
        }
      }
      is RcRoundedClipRectModifier -> {
        val corners =
          listOf(operation.topStart, operation.topEnd, operation.bottomStart, operation.bottomEnd)
        if (corners.any { it.isNaNEncoded } || corners.map { it.bits }.distinct().size != 1) {
          gap("a clip with differing or variable corners is not generated")
          null
        } else {
          obj("type" to str("clip"), "shape" to str(format(length(operation.topStart.value))))
        }
      }
      is RcClipRectModifier -> obj("type" to str("clip"), "shape" to str("0"))
      is RcAccessibilitySemantics -> {
        if (operation.contentDescriptionId != 0 || operation.textId != 0) {
          gap("semantics have no modifier; a content description is not generated")
        }
        null
      }
      else -> {
        if (operation.opcode == RcOpcodes.MODIFIER_DRAW_CONTENT) return null
        gap("modifier ${RcDocumentIndex.describe(operation)} has no design modifier")
        null
      }
    }

  private fun dimension(name: String, axis: String, type: Int, value: RcFloatWord): Json? {
    if (value.isNaNEncoded) {
      gap("a $name read from ${index.describeId(value.referencedId!!)} is not generated")
      return null
    }
    return when (type) {
      RcDimensionType.EXACT_DP -> obj("type" to str(name), "${name}Dp" to num(value.value))
      RcDimensionType.EXACT -> obj("type" to str(name), "${name}Dp" to num(value.value / density))
      RcDimensionType.FILL ->
        if (value.value == 1f) obj("type" to str("fillMax$axis"))
        else obj("type" to str("fillMax$axis"), "fraction" to num(value.value))
      RcDimensionType.WEIGHT -> obj("type" to str("weight"), "weight" to num(value.value))
      RcDimensionType.WRAP -> null
      else -> {
        gap("$name dimension type $type has no design modifier")
        null
      }
    }
  }

  /** A padding or corner length in dp: dp in a dp document, pixels otherwise. */
  private fun length(value: Float): Float =
    if (header.densityBehavior == RcHeader.DENSITY_BEHAVIOR_DP) value else value / density

  // --- arrangement and alignment ------------------------------------------------------------

  private fun horizontal(properties: Json.Obj, positioning: Int, spacedBy: RcFloatWord) {
    ARRANGEMENTS[positioning]?.takeUnless { positioning == START }
      ?.let { properties["horizontalArrangement"] = prop("enum", str(it)) }
    spacing(spacedBy)?.let { properties["horizontalSpacingDp"] = prop("float", num(it)) }
  }

  private fun verticalArrangement(properties: Json.Obj, positioning: Int, spacedBy: RcFloatWord) {
    vertical(properties, "verticalArrangement", positioning, ARRANGEMENTS)
    spacing(spacedBy)?.let { properties["verticalSpacingDp"] = prop("float", num(it)) }
  }

  private fun vertical(
    properties: Json.Obj,
    name: String,
    positioning: Int,
    values: Map<Int, String>,
  ) {
    if (positioning == TOP) return
    values[positioning]?.let { properties[name] = prop("enum", str(it)) }
      ?: gap("$name $positioning has no value")
  }

  private fun horizontalAlignment(properties: Json.Obj, positioning: Int) {
    if (positioning == START) return
    ALIGNMENTS_H[positioning]?.let { properties["horizontalAlignment"] = prop("enum", str(it)) }
      ?: gap("horizontal alignment $positioning has no value")
  }

  private fun spacing(word: RcFloatWord): Float? {
    if (word.isNaNEncoded) {
      gap("spacing read from a variable is not generated")
      return null
    }
    return length(word.value).takeUnless { it == 0f }
  }

  private fun boxAlignment(horizontal: Int, vertical: Int): String? {
    if (horizontal == START && vertical == TOP) return null
    val v = mapOf(TOP to "top", CENTER to "center", BOTTOM to "bottom")[vertical]
    val h = mapOf(START to "Start", CENTER to "Center", END to "End")[horizontal]
    if (v == null || h == null) {
      gap("box alignment ($horizontal, $vertical) has no value")
      return null
    }
    return if (v == "center" && h == "Center") "center" else "$v$h"
  }

  // --- colours ------------------------------------------------------------------------------

  /** A colour id as a node property value: a theme token where the name is one, else its hex. */
  private fun colorValue(id: Int): Json? {
    themeRole(id)?.let {
      return prop("colorToken", str(it))
    }
    return colorString(id)?.let { prop("color", str(it)) }
  }

  /** A colour id as the hex string `draw` and text nodes take. */
  private fun colorString(id: Int): String? {
    index.named[id]
      ?.takeIf { it.type == RcNamedVariable.COLOR_TYPE }
      ?.let { gap("named colour \"${it.name}\" is written as its default value") }
    return index.colors[id]?.let { hexOpaque(it) }
      ?: run {
        gap("a colour reads ${index.describeId(id)}; it is not generated")
        null
      }
  }

  /** The role of a named theme colour, `USER:WearM3.onSurface` → `onSurface`. */
  private fun themeRole(id: Int): String? {
    val name =
      index.named[id]?.takeIf { it.type == RcNamedVariable.COLOR_TYPE }?.name ?: return null
    // Only a Material theme's own roles are tokens: `USER:brand.primary` is a host colour that
    // happens to share a role's name, and keeps its default (reported) instead.
    val qualified = name.substringAfter(':')
    val namespace = qualified.substringBefore('.', "")
    val role = qualified.substringAfter('.', "")
    if (!MATERIAL_NAMESPACE.matches(namespace)) return null
    return role.takeIf { it in MATERIAL_COLOR_ROLES }
  }

  private fun hexOpaque(argb: Int): String {
    if (argb ushr 24 != 0xFF) gap("a translucent colour is written opaque")
    return hex(argb)
  }

  private fun hex(argb: Int): String =
    "#" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

  private fun hexOf(alpha: Float, red: Float, green: Float, blue: Float): String {
    fun channel(value: Float) = kotlin.math.round(value * 255f).toInt().coerceIn(0, 255)
    return hexOpaque(
      (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    )
  }

  // --- JSON ---------------------------------------------------------------------------------

  private fun render(value: Json): String = buildString { write(value, 0) }

  private fun StringBuilder.write(value: Json, depth: Int) {
    when (value) {
      is Json.Str -> quote(value.value)
      is Json.Num -> append(format(value.value))
      is Json.IntNum -> append(value.value)
      is Json.Bool -> append(value.value)
      is Json.Arr ->
        if (value.values.isEmpty()) append("[]")
        else if (value.values.all { it is Json.Str || it is Json.Num }) {
          append('[')
          value.values.forEachIndexed { i, item ->
            if (i > 0) append(", ")
            write(item, depth)
          }
          append(']')
        } else {
          appendLine('[')
          value.values.forEachIndexed { i, item ->
            indent(depth + 1)
            write(item, depth + 1)
            appendLine(if (i < value.values.size - 1) "," else "")
          }
          indent(depth)
          append(']')
        }
      is Json.Obj ->
        if (value.fields.isEmpty()) append("{}")
        else {
          appendLine('{')
          val entries = value.fields.entries.toList()
          entries.forEachIndexed { i, (key, item) ->
            indent(depth + 1)
            quote(key)
            append(": ")
            write(item, depth + 1)
            appendLine(if (i < entries.size - 1) "," else "")
          }
          indent(depth)
          append('}')
        }
    }
  }

  private fun StringBuilder.indent(depth: Int) = repeat(depth) { append("  ") }

  private fun StringBuilder.quote(text: String) {
    append('"')
    text.forEach { char ->
      when (char) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
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

  private fun format(value: Float): String =
    when {
      value.isNaN() || value.isInfinite() -> "0"
      value == value.toInt().toFloat() && kotlin.math.abs(value) < 1e9f -> value.toInt().toString()
      else -> value.toString()
    }

  private companion object {
    const val START = 1
    const val CENTER = 2
    const val END = 3
    const val TOP = 4
    const val BOTTOM = 5

    val ARRANGEMENTS: Map<Int, String> =
      mapOf(
        START to "start",
        CENTER to "center",
        END to "end",
        TOP to "top",
        BOTTOM to "bottom",
        6 to "spaceBetween",
        7 to "spaceEvenly",
        8 to "spaceAround",
      )
    val ALIGNMENTS_V: Map<Int, String> = mapOf(TOP to "top", CENTER to "center", BOTTOM to "bottom")
    val ALIGNMENTS_H: Map<Int, String> = mapOf(START to "start", CENTER to "center", END to "end")

    val TEXT_ALIGNS: Map<Int, String> =
      mapOf(
        RcTextLayout.ALIGN_START to "start",
        RcTextLayout.ALIGN_LEFT to "start",
        RcTextLayout.ALIGN_CENTER to "center",
        RcTextLayout.ALIGN_END to "end",
        RcTextLayout.ALIGN_RIGHT to "end",
        RcTextLayout.ALIGN_JUSTIFY to "justify",
      )
    val OVERFLOWS: Map<Int, String> =
      mapOf(
        RcTextLayout.OVERFLOW_CLIP to "clip",
        RcTextLayout.OVERFLOW_VISIBLE to "visible",
        RcTextLayout.OVERFLOW_ELLIPSIS to "ellipsis",
        RcTextLayout.OVERFLOW_START_ELLIPSIS to "startEllipsis",
        RcTextLayout.OVERFLOW_MIDDLE_ELLIPSIS to "middleEllipsis",
      )
    // AndroidX `ImageScaling` values the catalog's `contentScale` has.
    val CONTENT_SCALES: Map<Int, String> =
      mapOf(1 to "inside", 4 to "fit", 5 to "crop", 6 to "fillBounds")

    // AndroidX `TextStyle` property ids.
    const val TEXT_COLOR = 3
    const val TEXT_COLOR_ID = 4
    const val TEXT_FONT_SIZE = 5
    const val TEXT_FONT_STYLE = 6
    const val TEXT_FONT_WEIGHT = 7
    const val TEXT_FONT_FAMILY = 8
    const val TEXT_ALIGN = 9
    const val TEXT_OVERFLOW = 10
    const val TEXT_MAX_LINES = 11
    const val WIRE_DEFAULT_FONT_SIZE = 36f

    /** The namespaces theme colours are published under, such as `WearM3` and `Material3`. */
    val MATERIAL_NAMESPACE: Regex = Regex("(?i)(wear)?(m3|material3?|materialtheme)")

    /** Material 3 colour scheme roles, the values a `colorToken` takes. */
    val MATERIAL_COLOR_ROLES: Set<String> =
      setOf(
        "primary",
        "onPrimary",
        "primaryContainer",
        "onPrimaryContainer",
        "primaryDim",
        "inversePrimary",
        "secondary",
        "onSecondary",
        "secondaryContainer",
        "onSecondaryContainer",
        "secondaryDim",
        "tertiary",
        "onTertiary",
        "tertiaryContainer",
        "onTertiaryContainer",
        "tertiaryDim",
        "error",
        "onError",
        "errorContainer",
        "onErrorContainer",
        "errorDim",
        "background",
        "onBackground",
        "surface",
        "onSurface",
        "surfaceVariant",
        "onSurfaceVariant",
        "surfaceTint",
        "inverseSurface",
        "inverseOnSurface",
        "surfaceBright",
        "surfaceDim",
        "surfaceContainer",
        "surfaceContainerLow",
        "surfaceContainerLowest",
        "surfaceContainerHigh",
        "surfaceContainerHighest",
        "outline",
        "outlineVariant",
        "scrim",
      )
    const val TEXT_FONT_AXIS = 20
    const val TEXT_FONT_AXIS_VALUES = 21
    val TEXT_PROPERTIES_SAID: Set<Int> =
      setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, TEXT_FONT_AXIS, TEXT_FONT_AXIS_VALUES)

    // AndroidX `PaintBundle` commands; a command's value may be packed in its high 16 bits.
    const val PAINT_TEXT_SIZE = 1
    const val PAINT_COLOR = 4
    const val PAINT_STROKE_WIDTH = 5
    const val PAINT_STROKE_MITER = 6
    const val DEFAULT_MITER_LIMIT = 4f
    const val PAINT_STROKE_CAP = 7
    const val PAINT_STYLE = 8
    const val PAINT_FILTER_QUALITY = 10
    const val PAINT_ALPHA = 12
    const val PAINT_ANTI_ALIAS = 14
    const val PAINT_STROKE_JOIN = 15
    const val PAINT_TYPEFACE = 16
    const val PAINT_FILTER_BITMAP = 17
    const val PAINT_COLOR_ID = 19
    const val PAINT_CLEAR_COLOR_FILTER = 21
    const val PAINT_FALLBACK_TYPEFACE = 26
    const val STYLE_STROKE = 1
    const val STYLE_FILL_AND_STROKE = 2
    val STROKE_JOINS: Map<Int, String> = mapOf(0 to "miter", 1 to "round", 2 to "bevel")
    val STROKE_CAPS: Map<Int, String> = mapOf(0 to "butt", 1 to "round", 2 to "square")
  }
}

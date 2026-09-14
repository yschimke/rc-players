package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw3
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDraw6
import ee.schimke.composeai.rcplayer.protocol.RcDrawText
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextAnchored
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcIdOperation
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcMultiClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextLength
import ee.schimke.composeai.rcplayer.protocol.RcTextLookup
import ee.schimke.composeai.rcplayer.protocol.RcTextLookupInt
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcTextStyle
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcTextSubtext
import ee.schimke.composeai.rcplayer.protocol.RcTextTransform
import ee.schimke.composeai.rcplayer.protocol.RcTransform2
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLinkedNode
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState

/**
 * Static, renderer-neutral snapshot used by the experimental native Apple player.
 *
 * This is intentionally a narrow bridge rather than a second wire decoder. Kotlin owns the mature
 * codec and expression runtime for the POC; Swift receives immutable values and owns every view and
 * drawing call. The bridge can disappear once a stable cross-language render IR is designed.
 */
public data class RcNativeDocumentSnapshot(
  public val width: Int,
  public val height: Int,
  public val root: RcNativeNodeSnapshot,
  public val unsupportedOpcodes: List<Int>,
  public val notes: List<String>,
)

/** A component in the native player's platform-neutral view tree. */
public data class RcNativeNodeSnapshot(
  public val kind: Int,
  public val componentId: Int,
  public val commands: List<RcNativeDrawCommand>,
  public val children: List<RcNativeNodeSnapshot>,
  public val semanticRole: Int = NONE,
  public val clickable: Boolean = false,
  public val enabled: Boolean = true,
  public val semanticLabel: String? = null,
) {
  public companion object {
    public const val NONE: Int = -1
    public const val ROOT: Int = 0
    public const val CONTENT: Int = 1
    public const val CANVAS: Int = 2
    public const val GROUP: Int = 3
  }
}

/** One Core Graphics-friendly command with resolved geometry and paint. */
public data class RcNativeDrawCommand(
  public val kind: Int,
  public val first: Float = 0f,
  public val second: Float = 0f,
  public val third: Float = 0f,
  public val fourth: Float = 0f,
  public val fifth: Float = 0f,
  public val sixth: Float = 0f,
  public val color: Int = 0xff000000.toInt(),
  public val alpha: Float = 1f,
  public val strokeWidth: Float = 1f,
  public val stroke: Boolean = false,
  public val textSize: Float = 16f,
  public val text: String? = null,
) {
  public companion object {
    public const val SAVE: Int = 0
    public const val RESTORE: Int = 1
    public const val TRANSLATE: Int = 2
    public const val SCALE: Int = 3
    public const val ROTATE: Int = 4
    public const val SKEW: Int = 5
    public const val CLIP_RECT: Int = 6
    public const val RECT: Int = 10
    public const val OVAL: Int = 11
    public const val CIRCLE: Int = 12
    public const val LINE: Int = 13
    public const val ROUND_RECT: Int = 14
    public const val ARC: Int = 15
    public const val SECTOR: Int = 16
    public const val TEXT: Int = 17
  }
}

/** Decode `.rc` bytes into the deliberately small immutable POC render model. */
public object RcNativeSnapshotBridge {
  @Throws(IllegalArgumentException::class)
  public fun decode(bytes: ByteArray): RcNativeDocumentSnapshot {
    val document = RcDocumentCodec.decode(bytes)
    val state = RcPlayerState(document)
    state.beginFrame(timeSeconds = 0f)
    val unsupported = linkedSetOf<Int>()
    val notes = linkedSetOf<String>()
    val linked = RcDocumentLinker.link(document)
    val paint = NativePaint()
    val styles =
      document.operations
        .filterIsInstance<RcTextStyle>()
        .mapNotNull { style -> style.styleId?.let { it to style } }
        .toMap()

    fun resolveStyle(styleId: Int, visiting: MutableSet<Int>): List<RcTextStyleProperty> {
      require(visiting.add(styleId)) { "Cyclic TextStyle parent at id $styleId" }
      val style = requireNotNull(styles[styleId]) { "Missing TextStyle id $styleId" }
      val merged = linkedMapOf<Int, RcTextStyleProperty>()
      style.parentStyleId
        ?.takeUnless { it == -1 }
        ?.let { resolveStyle(it, visiting).forEach { property -> merged[property.id] = property } }
      style.properties
        .filterNot { it.id == 1 || it.id == 2 || it.id == 23 || it.id == 24 }
        .forEach { merged[it.id] = it }
      visiting.remove(styleId)
      return merged.values.toList()
    }

    fun resolvedStyle(operation: RcCoreText): List<RcTextStyleProperty> {
      val merged = linkedMapOf<Int, RcTextStyleProperty>()
      operation.textStyleId?.let { styleId ->
        resolveStyle(styleId, linkedSetOf()).forEach { merged[it.id] = it }
      }
      operation.properties
        .filterNot { it.id == 1 || it.id == 2 || it.id == 24 }
        .forEach { merged[it.id] = it }
      return merged.values.toList()
    }

    fun nodeFor(container: RcLinkedNode.Container): RcNativeNodeSnapshot {
      val operation = container.operation
      val semantics =
        container.children
          .filterIsInstance<RcLinkedNode.Operation>()
          .map { it.operation }
          .filterIsInstance<RcAccessibilitySemantics>()
          .lastOrNull()
      val hasClickModifier =
        container.children.filterIsInstance<RcLinkedNode.Container>().any {
          it.operation is RcClickModifier || it.operation is RcMultiClickModifier
        }
      val kind =
        when (operation) {
          is RcRootLayout -> RcNativeNodeSnapshot.ROOT
          is RcLayoutContent,
          is RcTextLayout,
          is RcCoreText -> RcNativeNodeSnapshot.CONTENT
          is RcCanvasLayout,
          is RcCanvasContent -> RcNativeNodeSnapshot.CANVAS
          else -> RcNativeNodeSnapshot.GROUP
        }
      val componentId =
        when (operation) {
          is RcRootLayout -> operation.componentId
          is RcLayoutContent -> operation.componentId
          is RcCanvasLayout -> operation.componentId
          is RcCanvasContent -> operation.componentId
          is RcTextLayout -> operation.componentId
          is RcCoreText -> operation.componentId
          else -> 0
        }
      val commands = mutableListOf<RcNativeDrawCommand>()
      if (operation is RcTextLayout) {
        val size = state.resolve(operation.fontSize)
        val color =
          if (operation.flags and RcTextLayout.FLAG_DYNAMIC_COLOR != 0) state.color(operation.color)
          else operation.color
        commands +=
          NativePaint(color = color, textSize = size)
            .command(
              RcNativeDrawCommand.TEXT,
              values = listOf(0f, size, -1f, -1f),
              text = state.text(operation.textId).orEmpty(),
            )
        notes += "Text layout geometry and shaping are approximate in the native POC"
      } else if (operation is RcCoreText) {
        val properties = resolvedStyle(operation)
        val size =
          properties
            .filterIsInstance<RcTextStyleProperty.FloatValue>()
            .lastOrNull { it.id == 5 }
            ?.value
            ?.let(state::resolve) ?: 36f
        val literalColor =
          properties
            .filterIsInstance<RcTextStyleProperty.IntValue>()
            .lastOrNull { it.id == 3 }
            ?.value ?: 0xff000000.toInt()
        val colorId =
          properties
            .filterIsInstance<RcTextStyleProperty.IntValue>()
            .lastOrNull { it.id == 4 }
            ?.value ?: -1
        commands +=
          NativePaint(
              color = if (colorId == -1) literalColor else state.color(colorId),
              textSize = size,
            )
            .command(
              RcNativeDrawCommand.TEXT,
              values = listOf(0f, size, -1f, -1f),
              text = state.text(operation.textId).orEmpty(),
            )
        notes += "CoreText layout geometry and shaping are approximate in the native POC"
      } else if (
        operation !is RcRootLayout &&
          operation !is RcLayoutContent &&
          operation !is RcCanvasLayout &&
          operation !is RcCanvasContent &&
          operation !is RcClickModifier &&
          operation !is RcMultiClickModifier
      ) {
        unsupported += operation.opcode
      }
      val children = mutableListOf<RcNativeNodeSnapshot>()
      for (child in container.children) {
        when (child) {
          is RcLinkedNode.Container -> children += nodeFor(child)
          is RcLinkedNode.Operation ->
            consume(child.operation, state, paint, commands, unsupported, notes)
        }
      }
      val clickable = semantics?.clickable == true || hasClickModifier
      val label =
        semantics
          ?.let { state.text(it.contentDescriptionId) ?: state.text(it.textId) }
          ?.takeUnless(String::isBlank)
      return RcNativeNodeSnapshot(
        kind = kind,
        componentId = componentId,
        commands = commands,
        children = children,
        semanticRole =
          semantics?.role
            ?: if (clickable) RcAccessibilitySemantics.ROLE_BUTTON else RcNativeNodeSnapshot.NONE,
        clickable = clickable,
        enabled = semantics?.enabled ?: true,
        semanticLabel = label,
      )
    }

    val rootCommands = mutableListOf<RcNativeDrawCommand>()
    val rootChildren = mutableListOf<RcNativeNodeSnapshot>()
    for (node in linked.operations) {
      when (node) {
        is RcLinkedNode.Container -> rootChildren += nodeFor(node)
        is RcLinkedNode.Operation ->
          consume(node.operation, state, paint, rootCommands, unsupported, notes)
      }
    }
    return RcNativeDocumentSnapshot(
      width = document.header.width,
      height = document.header.height,
      root =
        RcNativeNodeSnapshot(
          RcNativeNodeSnapshot.ROOT,
          componentId = 0,
          commands = rootCommands,
          children = rootChildren,
        ),
      unsupportedOpcodes = unsupported.toList(),
      notes = notes.toList(),
    )
  }

  private fun consume(
    operation: RcOperation,
    state: RcPlayerState,
    paint: NativePaint,
    commands: MutableList<RcNativeDrawCommand>,
    unsupported: MutableSet<Int>,
    notes: MutableSet<String>,
  ) {
    when (operation) {
      is RcAccessibilitySemantics -> Unit
      is RcPaintData -> applyPaint(operation, state, paint, notes)
      is RcFloatExpression -> state.applyFloatExpression(operation)
      is RcIntegerExpression -> state.applyIntegerExpression(operation)
      is RcColorExpression -> state.applyColorExpression(operation)
      is RcTextMerge,
      is RcTextLength,
      is RcTextSubtext,
      is RcTextTransform,
      is RcTextFromFloat,
      is RcTextLookup,
      is RcTextLookupInt -> state.applyTextOperation(operation)
      is RcDraw4 -> {
        val values =
          listOf(operation.first, operation.second, operation.third, operation.fourth)
            .map(state::resolve)
        val kind =
          when (operation.opcode) {
            RcOpcodes.DRAW_RECT -> RcNativeDrawCommand.RECT
            RcOpcodes.DRAW_OVAL -> RcNativeDrawCommand.OVAL
            RcOpcodes.DRAW_LINE -> RcNativeDrawCommand.LINE
            RcOpcodes.CLIP_RECT -> RcNativeDrawCommand.CLIP_RECT
            RcOpcodes.MATRIX_SCALE -> RcNativeDrawCommand.SCALE
            else -> null
          }
        if (kind == null) unsupported += operation.opcode
        else commands += paint.command(kind, values)
      }
      is RcDraw3 -> {
        val values = listOf(operation.first, operation.second, operation.third).map(state::resolve)
        val kind =
          when (operation.opcode) {
            RcOpcodes.DRAW_CIRCLE -> RcNativeDrawCommand.CIRCLE
            RcOpcodes.MATRIX_ROTATE -> RcNativeDrawCommand.ROTATE
            else -> null
          }
        if (kind == null) unsupported += operation.opcode
        else commands += paint.command(kind, values)
      }
      is RcDraw6 -> {
        val values =
          listOf(
              operation.first,
              operation.second,
              operation.third,
              operation.fourth,
              operation.fifth,
              operation.sixth,
            )
            .map(state::resolve)
        val kind =
          when (operation.opcode) {
            RcOpcodes.DRAW_ROUND_RECT -> RcNativeDrawCommand.ROUND_RECT
            RcOpcodes.DRAW_ARC -> RcNativeDrawCommand.ARC
            RcOpcodes.DRAW_SECTOR -> RcNativeDrawCommand.SECTOR
            else -> null
          }
        if (kind == null) unsupported += operation.opcode
        else commands += paint.command(kind, values)
      }
      is RcTransform2 -> {
        val kind =
          when (operation.opcode) {
            RcOpcodes.MATRIX_TRANSLATE -> RcNativeDrawCommand.TRANSLATE
            RcOpcodes.MATRIX_SKEW -> RcNativeDrawCommand.SKEW
            else -> null
          }
        if (kind == null) unsupported += operation.opcode
        else
          commands +=
            paint.command(
              kind,
              listOf(state.resolve(operation.first), state.resolve(operation.second)),
            )
      }
      is RcNoArg ->
        when (operation.opcode) {
          RcOpcodes.MATRIX_SAVE -> commands += paint.command(RcNativeDrawCommand.SAVE)
          RcOpcodes.MATRIX_RESTORE -> commands += paint.command(RcNativeDrawCommand.RESTORE)
        }
      is RcDrawText -> {
        val fullText = state.text(operation.textId).orEmpty()
        val start = operation.start.coerceIn(0, fullText.length)
        val end = operation.end.coerceIn(start, fullText.length)
        commands +=
          paint.command(
            RcNativeDrawCommand.TEXT,
            listOf(state.resolve(operation.x), state.resolve(operation.y)),
            fullText.substring(start, end),
          )
      }
      is RcDrawTextAnchored -> {
        val text = state.text(operation.textId).orEmpty()
        commands +=
          paint.command(
            RcNativeDrawCommand.TEXT,
            listOf(
              state.resolve(operation.x),
              state.resolve(operation.y),
              state.resolve(operation.panX),
              state.resolve(operation.panY),
            ),
            text,
          )
      }
      is RcIdOperation -> unsupported += operation.opcode
      else -> {
        // Data declarations and layout metadata are already represented in state or the node tree.
        if (operation.opcode in DRAWING_OR_BEHAVIOR_OPCODES) unsupported += operation.opcode
      }
    }
  }

  private fun applyPaint(
    operation: RcPaintData,
    state: RcPlayerState,
    paint: NativePaint,
    notes: MutableSet<String>,
  ) {
    var index = 0
    while (index < operation.words.size) {
      val command = operation.words[index++]
      when (command and 0xffff) {
        1 -> paint.textSize = state.resolveWord(operation.words[index++])
        4 -> paint.color = operation.words[index++]
        5 -> paint.strokeWidth = state.resolveWord(operation.words[index++])
        8 -> paint.stroke = command ushr 16 == 1
        12 -> paint.alpha = state.resolveWord(operation.words[index++]).coerceIn(0f, 1f)
        19 -> paint.color = state.color(operation.words[index++])
        7,
        10,
        14,
        15,
        17,
        18 -> Unit
        16 -> {
          paint.fontStyle = command ushr 16
          paint.fontType = operation.words[index++]
        }
        else -> {
          notes += "Paint command ${command and 0xffff} is not represented by the native POC"
          return
        }
      }
    }
  }

  private fun RcPlayerState.resolveWord(bits: Int): Float =
    resolve(ee.schimke.composeai.rcplayer.protocol.RcFloatWord(bits))

  private data class NativePaint(
    var color: Int = 0xff000000.toInt(),
    var alpha: Float = 1f,
    var strokeWidth: Float = 1f,
    var stroke: Boolean = false,
    var textSize: Float = 16f,
    var fontType: Int = 0,
    var fontStyle: Int = 0,
  ) {
    fun command(kind: Int, values: List<Float> = emptyList(), text: String? = null) =
      RcNativeDrawCommand(
        kind = kind,
        first = values.getOrElse(0) { 0f },
        second = values.getOrElse(1) { 0f },
        third = values.getOrElse(2) { 0f },
        fourth = values.getOrElse(3) { 0f },
        fifth = values.getOrElse(4) { 0f },
        sixth = values.getOrElse(5) { 0f },
        color = color,
        alpha = alpha,
        strokeWidth = strokeWidth,
        stroke = stroke,
        textSize = textSize,
        text = text,
      )
  }

  private val DRAWING_OR_BEHAVIOR_OPCODES: Set<Int> =
    setOf(
      RcOpcodes.DRAW_BITMAP,
      RcOpcodes.DRAW_BITMAP_INT,
      RcOpcodes.DRAW_BITMAP_SCALED,
      RcOpcodes.DRAW_PATH,
      RcOpcodes.DRAW_TWEEN_PATH,
      RcOpcodes.DRAW_TEXT_ON_PATH,
      RcOpcodes.DRAW_TEXT_ON_CIRCLE,
      RcOpcodes.CLICK_AREA,
      RcOpcodes.MODIFIER_CLICK,
      RcOpcodes.MODIFIER_MULTI_CLICK,
    )
}

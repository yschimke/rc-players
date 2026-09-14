package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionConstraintsModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw3
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDraw6
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmap
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapInt
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapScaled
import ee.schimke.composeai.rcplayer.protocol.RcDrawText
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextAnchored
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcHeightInModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIdOperation
import ee.schimke.composeai.rcplayer.protocol.RcImageAttribute
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcMultiClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcOperationInventory
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcRootContentBehavior
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcStateLayout
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
import ee.schimke.composeai.rcplayer.protocol.RcVisibilityModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthInModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.protocol.RcZIndexModifier
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLinkedDocument
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
  public val diagnostics: List<RcNativeDiagnostic> = emptyList(),
  public val rootSizing: Int = RcRootContentBehavior.SIZING_SCALE,
  public val rootMode: Int = RcRootContentBehavior.SCALE_FIT,
  public val rootAlignment: Int = RcRootContentBehavior.ALIGNMENT_CENTER,
  public val images: List<RcNativeImageResource> = emptyList(),
  public val fonts: List<RcNativeFontResource> = emptyList(),
)

/** Encoded image bytes or an opaque host reference, copied from the document without decoding. */
public data class RcNativeImageResource(
  public val id: Int,
  public val width: Int,
  public val height: Int,
  public val type: Int,
  public val encoding: Int,
  public val data: ByteArray,
)

/** Embedded font bytes copied from the document for bounded registration by the Swift host. */
public data class RcNativeFontResource(
  public val id: Int,
  public val type: Int,
  public val data: ByteArray,
)

/** One structured compatibility issue found while producing a native render snapshot. */
public data class RcNativeDiagnostic(
  public val severity: Int,
  public val opcode: Int,
  public val operationName: String,
  public val componentId: Int,
  public val reason: String,
) {
  public companion object {
    /** The operation renders, but the native result is known to be approximate. */
    public const val WARNING: Int = 0

    /** The operation or one of its requested behaviors is not rendered. */
    public const val UNSUPPORTED: Int = 1
  }
}

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
  public val widthType: Int = RcDimensionType.WRAP,
  public val widthValue: Float = 0f,
  public val heightType: Int = RcDimensionType.WRAP,
  public val heightValue: Float = 0f,
  public val minimumHeight: Float = 0f,
  public val minimumWidth: Float = 0f,
  public val maximumWidth: Float = -1f,
  public val maximumHeight: Float = -1f,
  public val paddingLeft: Float = 0f,
  public val paddingTop: Float = 0f,
  public val paddingRight: Float = 0f,
  public val paddingBottom: Float = 0f,
  public val cornerRadius: Float = 0f,
  public val hasBackground: Boolean = false,
  public val backgroundColor: Int = 0,
  public val horizontalPositioning: Int = 1,
  public val verticalPositioning: Int = 4,
  public val spacing: Float = 0f,
  public val offsetX: Float = 0f,
  public val offsetY: Float = 0f,
  public val zIndex: Float = 0f,
  /** AndroidX visibility: 0 gone, 1 visible, 2 invisible but measured. */
  public val visibility: Int = 1,
) {
  public companion object {
    public const val NONE: Int = -1
    public const val ROOT: Int = 0
    public const val CONTENT: Int = 1
    public const val CANVAS: Int = 2
    public const val GROUP: Int = 3
    public const val BOX: Int = 4
    public const val ROW: Int = 5
    public const val COLUMN: Int = 6
    public const val TEXT: Int = 7
    public const val IMAGE: Int = 8
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
  public val strokeCap: Int = 0,
  public val strokeJoin: Int = 0,
  public val blendMode: Int = 3,
  public val textSize: Float = 16f,
  public val textWeight: Float = 400f,
  public val text: String? = null,
  public val path: List<RcNativePathCommand> = emptyList(),
  public val pathWinding: Int = 0,
  public val gradient: RcNativeGradient? = null,
  public val textStyle: RcNativeTextStyle? = null,
  public val image: RcNativeImageDraw? = null,
  public val textureImageId: Int = -1,
  public val textureTileModeX: Int = 0,
  public val textureTileModeY: Int = 0,
) {
  public companion object {
    public const val SAVE: Int = 0
    public const val RESTORE: Int = 1
    public const val TRANSLATE: Int = 2
    public const val SCALE: Int = 3
    public const val ROTATE: Int = 4
    public const val SKEW: Int = 5
    public const val CLIP_RECT: Int = 6
    public const val CLIP_PATH: Int = 7
    public const val RECT: Int = 10
    public const val OVAL: Int = 11
    public const val CIRCLE: Int = 12
    public const val LINE: Int = 13
    public const val ROUND_RECT: Int = 14
    public const val ARC: Int = 15
    public const val SECTOR: Int = 16
    public const val TEXT: Int = 17
    public const val PATH: Int = 18
    public const val IMAGE: Int = 19
  }
}

/** Resolved source/destination geometry for one ordered image draw. */
public data class RcNativeImageDraw(
  public val imageId: Int,
  public val sourceLeft: Float,
  public val sourceTop: Float,
  public val sourceRight: Float,
  public val sourceBottom: Float,
  public val destinationLeft: Float,
  public val destinationTop: Float,
  public val destinationRight: Float,
  public val destinationBottom: Float,
  public val scaleType: Int = 6,
  public val scaleFactor: Float = 1f,
  public val contentDescription: String? = null,
)

/** Resolved paragraph and font properties for native layout text or ordered Core Text drawing. */
public data class RcNativeTextStyle(
  public val fontStyle: Int = 0,
  public val fontFamilyId: Int = -1,
  public val fontFamilyName: String? = null,
  public val alignment: Int = RcTextLayout.ALIGN_LEFT,
  public val overflow: Int = RcTextLayout.OVERFLOW_CLIP,
  public val maxLines: Int = Int.MAX_VALUE,
  public val letterSpacing: Float = 0f,
  public val lineHeightAdd: Float = 0f,
  public val lineHeightMultiplier: Float = 1f,
  public val breakStrategy: Int = 0,
  public val hyphenation: Int = 0,
  public val justified: Boolean = false,
  public val underline: Boolean = false,
  public val strikeThrough: Boolean = false,
)

/** A validated inline gradient whose coordinates are resolved for Core Graphics. */
public data class RcNativeGradient(
  public val kind: Int,
  public val colors: List<Int>,
  public val stops: List<Float>,
  public val first: Float,
  public val second: Float,
  public val third: Float = 0f,
  public val fourth: Float = 0f,
  public val tileMode: Int = 0,
) {
  public companion object {
    public const val LINEAR: Int = 0
    public const val RADIAL: Int = 1
    public const val SWEEP: Int = 2
  }
}

/** One validated segment in an exported native path. */
public data class RcNativePathCommand(
  public val kind: Int,
  public val first: Float = 0f,
  public val second: Float = 0f,
  public val third: Float = 0f,
  public val fourth: Float = 0f,
  public val fifth: Float = 0f,
  public val sixth: Float = 0f,
)

/** Retained native render session. Decode/link state survives immutable frame snapshots. */
public class RcNativeSnapshotSession(bytes: ByteArray) {
  private val document: RcDocument = RcDocumentCodec.decode(bytes)
  private val linked: RcLinkedDocument = RcDocumentLinker.link(document)
  private val state: RcPlayerState = RcPlayerState(document)

  /**
   * Resolve one immutable frame without rebuilding the document codec, linker, or runtime state.
   */
  @Throws(IllegalArgumentException::class)
  public fun snapshot(timeSeconds: Float = 0f): RcNativeDocumentSnapshot =
    RcNativeSnapshotBridge.snapshot(document, linked, state, timeSeconds)
}

/** Decode `.rc` bytes into the deliberately small immutable POC render model. */
public object RcNativeSnapshotBridge {
  /** Create a retained session for hosts that render more than one immutable frame. */
  @Throws(IllegalArgumentException::class)
  public fun createSession(bytes: ByteArray): RcNativeSnapshotSession =
    RcNativeSnapshotSession(bytes)

  @Throws(IllegalArgumentException::class)
  public fun decode(bytes: ByteArray): RcNativeDocumentSnapshot = createSession(bytes).snapshot()

  internal fun snapshot(
    document: RcDocument,
    linked: RcLinkedDocument,
    state: RcPlayerState,
    timeSeconds: Float,
  ): RcNativeDocumentSnapshot {
    require(timeSeconds.isFinite() && timeSeconds >= 0f) {
      "Native snapshot time must be finite and non-negative"
    }
    state.beginFrame(timeSeconds = timeSeconds)
    val diagnostics = NativeDiagnosticCollector()
    val paint = NativePaint()
    val bitmaps =
      document.operations.filterIsInstance<RcBitmapData>().associateBy(RcBitmapData::imageId)
    val paths = document.operations.filterIsInstance<RcPathData>().associateBy(RcPathData::id)
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

    fun descendantOperations(container: RcLinkedNode.Container): Sequence<RcOperation> =
      container.children.asSequence().flatMap { child ->
        when (child) {
          is RcLinkedNode.Container -> sequenceOf(child.operation) + descendantOperations(child)
          is RcLinkedNode.Operation -> sequenceOf(child.operation)
        }
      }

    fun nodeFor(container: RcLinkedNode.Container): RcNativeNodeSnapshot {
      val operation = container.operation
      val directOperations =
        container.children.filterIsInstance<RcLinkedNode.Operation>().map { it.operation }
      val semantics =
        container.children
          .filterIsInstance<RcLinkedNode.Operation>()
          .map { it.operation }
          .filterIsInstance<RcAccessibilitySemantics>()
          .lastOrNull()
      val clickModifiers =
        container.children
          .filterIsInstance<RcLinkedNode.Container>()
          .map { it.operation }
          .filter { it is RcClickModifier || it is RcMultiClickModifier }
      val hasClickModifier = clickModifiers.isNotEmpty()
      val kind =
        when (operation) {
          is RcRootLayout -> RcNativeNodeSnapshot.ROOT
          is RcLayoutContent -> RcNativeNodeSnapshot.CONTENT
          is RcCanvasLayout,
          is RcCanvasContent -> RcNativeNodeSnapshot.CANVAS
          is RcBoxLayout,
          is RcStateLayout -> RcNativeNodeSnapshot.BOX
          is RcRowLayout -> RcNativeNodeSnapshot.ROW
          is RcColumnLayout -> RcNativeNodeSnapshot.COLUMN
          is RcTextLayout,
          is RcCoreText -> RcNativeNodeSnapshot.TEXT
          is RcImageLayout -> RcNativeNodeSnapshot.IMAGE
          else -> RcNativeNodeSnapshot.GROUP
        }
      val componentId =
        when (operation) {
          is RcRootLayout -> operation.componentId
          is RcLayoutContent -> operation.componentId
          is RcCanvasLayout -> operation.componentId
          is RcCanvasContent -> operation.componentId
          is RcBoxLayout -> operation.componentId
          is RcRowLayout -> operation.componentId
          is RcColumnLayout -> operation.componentId
          is RcStateLayout -> operation.componentId
          is RcTextLayout -> operation.componentId
          is RcCoreText -> operation.componentId
          is RcImageLayout -> operation.componentId
          else -> 0
        }
      val commands = mutableListOf<RcNativeDrawCommand>()
      if (operation is RcImageLayout) {
        val bitmap =
          requireNotNull(bitmaps[operation.bitmapId]) { "Missing bitmap ${operation.bitmapId}" }
        val alpha = state.resolve(operation.alpha)
        require(alpha.isFinite()) { "ImageLayout alpha must be finite" }
        commands +=
          NativePaint(alpha = alpha.coerceIn(0f, 1f))
            .command(
              RcNativeDrawCommand.IMAGE,
              image =
                validatedImageDraw(
                  RcNativeImageDraw(
                    imageId = operation.bitmapId,
                    sourceLeft = 0f,
                    sourceTop = 0f,
                    sourceRight = bitmap.width.toFloat(),
                    sourceBottom = bitmap.height.toFloat(),
                    destinationLeft = 0f,
                    destinationTop = 0f,
                    destinationRight = bitmap.width.toFloat(),
                    destinationBottom = bitmap.height.toFloat(),
                    scaleType = operation.scaleType,
                  ),
                  bitmaps,
                ),
            )
      } else if (operation is RcTextLayout) {
        val size = state.resolve(operation.fontSize) / document.header.density
        val weight = state.resolve(operation.fontWeight).coerceIn(1f, 1000f)
        require(size.isFinite() && size > 0f) { "TextLayout font size must be finite and positive" }
        require(operation.textAlign in 1..6) {
          "TextLayout alignment ${operation.textAlign} is invalid"
        }
        require(operation.overflow in 1..5) {
          "TextLayout overflow ${operation.overflow} is invalid"
        }
        require(operation.maxLines >= 1) { "TextLayout maxLines must be positive" }
        require(operation.fontStyle and 3 == operation.fontStyle) {
          "TextLayout font style ${operation.fontStyle} is invalid"
        }
        val familyName = state.text(operation.fontFamilyId)
        val color =
          if (operation.flags and RcTextLayout.FLAG_DYNAMIC_COLOR != 0) state.color(operation.color)
          else operation.color
        commands +=
          NativePaint(color = color, textSize = size)
            .command(
              RcNativeDrawCommand.TEXT,
              values = listOf(0f, size, -1f, -1f),
              text = state.text(operation.textId).orEmpty(),
              textWeight = weight,
              textStyle =
                RcNativeTextStyle(
                  fontStyle = operation.fontStyle,
                  fontFamilyId = operation.fontFamilyId,
                  fontFamilyName = familyName,
                  alignment = operation.textAlign,
                  overflow = operation.overflow,
                  maxLines = operation.maxLines,
                ),
            )
        if (
          familyName?.lowercase() !in setOf(null, "default", "sans-serif", "serif", "monospace")
        ) {
          diagnostics.warning(
            operation,
            componentId,
            "Font family $familyName uses deterministic system fallback until resource loading",
          )
        }
        diagnostics.warning(
          operation,
          componentId,
          "Text layout geometry and shaping are approximate in the native POC",
        )
      } else if (operation is RcCoreText) {
        val properties = resolvedStyle(operation)
        val size =
          properties
            .filterIsInstance<RcTextStyleProperty.FloatValue>()
            .lastOrNull { it.id == 5 }
            ?.value
            ?.let(state::resolve)
            ?.div(document.header.density) ?: 36f
        val weight =
          properties
            .filterIsInstance<RcTextStyleProperty.FloatValue>()
            .lastOrNull { it.id == 7 }
            ?.value
            ?.let(state::resolve)
            ?.coerceIn(1f, 1000f) ?: 400f
        fun intProperty(id: Int, default: Int): Int =
          properties
            .filterIsInstance<RcTextStyleProperty.IntValue>()
            .lastOrNull { it.id == id }
            ?.value ?: default
        fun floatProperty(id: Int, default: Float): Float =
          properties
            .filterIsInstance<RcTextStyleProperty.FloatValue>()
            .lastOrNull { it.id == id }
            ?.value
            ?.let(state::resolve) ?: default
        fun booleanProperty(id: Int): Boolean =
          properties
            .filterIsInstance<RcTextStyleProperty.BooleanValue>()
            .lastOrNull { it.id == id }
            ?.value ?: false
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
        val fontStyle = intProperty(6, 0)
        val fontFamilyId = intProperty(8, -1)
        val familyName = state.text(fontFamilyId)
        val alignment = intProperty(9, RcTextLayout.ALIGN_LEFT)
        val overflow = intProperty(10, RcTextLayout.OVERFLOW_CLIP)
        val maxLines = intProperty(11, Int.MAX_VALUE)
        val letterSpacing = floatProperty(12, 0f)
        val lineHeightAdd = floatProperty(13, 0f)
        val lineHeightMultiplier = floatProperty(14, 1f)
        require(size.isFinite() && size > 0f) { "CoreText font size must be finite and positive" }
        require(alignment in 1..6) { "CoreText alignment $alignment is invalid" }
        require(overflow in 1..5) { "CoreText overflow $overflow is invalid" }
        require(maxLines >= 1) { "CoreText maxLines must be positive" }
        require(fontStyle and 3 == fontStyle) { "CoreText font style $fontStyle is invalid" }
        require(letterSpacing.isFinite()) { "CoreText letter spacing must be finite" }
        require(lineHeightAdd.isFinite()) { "CoreText line-height addition must be finite" }
        require(lineHeightMultiplier.isFinite() && lineHeightMultiplier > 0f) {
          "CoreText line-height multiplier must be finite and positive"
        }
        commands +=
          NativePaint(
              color = if (colorId == -1) literalColor else state.color(colorId),
              textSize = size,
            )
            .command(
              RcNativeDrawCommand.TEXT,
              values = listOf(0f, size, -1f, -1f),
              text = state.text(operation.textId).orEmpty(),
              textWeight = weight,
              textStyle =
                RcNativeTextStyle(
                  fontStyle = fontStyle,
                  fontFamilyId = fontFamilyId,
                  fontFamilyName = familyName,
                  alignment = alignment,
                  overflow = overflow,
                  maxLines = maxLines,
                  letterSpacing = letterSpacing,
                  lineHeightAdd = lineHeightAdd,
                  lineHeightMultiplier = lineHeightMultiplier,
                  breakStrategy = intProperty(15, 0),
                  hyphenation = intProperty(16, 0),
                  justified = intProperty(17, 0) == 1,
                  underline = booleanProperty(18),
                  strikeThrough = booleanProperty(19),
                ),
            )
        if (booleanProperty(22)) {
          diagnostics.unsupportedLimitation(
            operation,
            componentId,
            "CoreText autosizing is not represented by the native player",
          )
        }
        if (
          properties.filterIsInstance<RcTextStyleProperty.IntArrayValue>().any { it.id == 20 } ||
            properties.filterIsInstance<RcTextStyleProperty.FloatArrayValue>().any { it.id == 21 }
        ) {
          diagnostics.unsupportedLimitation(
            operation,
            componentId,
            "CoreText font variation axes are not represented by the native player",
          )
        }
        if (intProperty(15, 0) != 0) {
          diagnostics.warning(
            operation,
            componentId,
            "CoreText break strategy uses the nearest UIKit paragraph behavior",
          )
        }
        if (
          familyName?.lowercase() !in setOf(null, "default", "sans-serif", "serif", "monospace")
        ) {
          diagnostics.warning(
            operation,
            componentId,
            "Font family $familyName uses deterministic system fallback until resource loading",
          )
        }
        diagnostics.warning(
          operation,
          componentId,
          "CoreText layout geometry and shaping are approximate in the native POC",
        )
      } else if (
        operation !is RcRootLayout &&
          operation !is RcLayoutContent &&
          operation !is RcCanvasLayout &&
          operation !is RcCanvasContent &&
          operation !is RcBoxLayout &&
          operation !is RcRowLayout &&
          operation !is RcColumnLayout &&
          operation !is RcStateLayout &&
          operation !is RcImageLayout &&
          operation !is RcClickModifier &&
          operation !is RcMultiClickModifier
      ) {
        diagnostics.unsupported(operation, componentId)
      }
      val children = mutableListOf<RcNativeNodeSnapshot>()
      clickModifiers.forEach { modifier ->
        diagnostics.unsupported(
          modifier,
          componentId,
          "Click action dispatch is not implemented by the native player",
        )
      }
      for (child in container.children) {
        when (child) {
          is RcLinkedNode.Operation ->
            consume(
              child.operation,
              componentId,
              state,
              paint,
              commands,
              diagnostics,
              paths,
              bitmaps,
            )
          is RcLinkedNode.Container -> {
            if (operation is RcStateLayout && child.operation is RcLayoutContent) {
              val contentComponentId = (child.operation as RcLayoutContent).componentId
              val contentVisibilityModifier =
                child.children
                  .filterIsInstance<RcLinkedNode.Operation>()
                  .map { it.operation }
                  .filterIsInstance<RcVisibilityModifier>()
                  .lastOrNull()
              val alternatives = child.children.filterIsInstance<RcLinkedNode.Container>()
              var alternativeIndex = 0
              for (contentChild in child.children) {
                when (contentChild) {
                  is RcLinkedNode.Operation ->
                    consume(
                      contentChild.operation,
                      contentComponentId,
                      state,
                      paint,
                      commands,
                      diagnostics,
                      paths,
                      bitmaps,
                    )
                  is RcLinkedNode.Container -> {
                    val contentVisibility =
                      contentVisibilityModifier?.let {
                        nativeVisibility(state.integer(it.visibilityId) ?: 0)
                      } ?: 1
                    val selected =
                      (state.integer(operation.indexId) ?: 0).coerceIn(
                        0,
                        (alternatives.size - 1).coerceAtLeast(0),
                      )
                    if (
                      contentVisibility != 0 &&
                        alternativeIndex == selected &&
                        alternatives.isNotEmpty()
                    ) {
                      children += nodeFor(contentChild)
                    }
                    alternativeIndex++
                  }
                }
              }
            } else {
              children += nodeFor(child)
            }
          }
        }
      }
      val clickable = semantics?.clickable == true || hasClickModifier
      val label =
        semantics
          ?.let { state.text(it.contentDescriptionId) ?: state.text(it.textId) }
          ?.takeUnless(String::isBlank)
      // AndroidX fixes each axis at the first size modifier in wire order.
      val width = directOperations.filterIsInstance<RcWidthModifier>().firstOrNull()
      val height = directOperations.filterIsInstance<RcHeightModifier>().firstOrNull()
      var minimumWidth = 0f
      var maximumWidth = -1f
      var minimumHeight = 0f
      var maximumHeight = -1f
      fun mergeRange(horizontal: Boolean, minimum: Float, maximum: Float) {
        val scaledMinimum = if (minimum == -1f) -1f else minimum / document.header.density
        val scaledMaximum = if (maximum == -1f) -1f else maximum / document.header.density
        if (horizontal) {
          if (scaledMinimum != -1f) minimumWidth = maxOf(minimumWidth, scaledMinimum)
          if (scaledMaximum != -1f) {
            maximumWidth =
              if (maximumWidth == -1f) scaledMaximum else minOf(maximumWidth, scaledMaximum)
          }
        } else {
          if (scaledMinimum != -1f) minimumHeight = maxOf(minimumHeight, scaledMinimum)
          if (scaledMaximum != -1f) {
            maximumHeight =
              if (maximumHeight == -1f) scaledMaximum else minOf(maximumHeight, scaledMaximum)
          }
        }
      }
      directOperations.forEach { modifier ->
        when (modifier) {
          is RcWidthInModifier ->
            mergeRange(true, state.resolve(modifier.minimum), state.resolve(modifier.maximum))
          is RcHeightInModifier ->
            mergeRange(false, state.resolve(modifier.minimum), state.resolve(modifier.maximum))
          is RcDimensionConstraintsModifier -> {
            when (modifier.type) {
              RcDimensionConstraintsModifier.HORIZONTAL,
              RcDimensionConstraintsModifier.REQUIRED_HORIZONTAL ->
                mergeRange(
                  true,
                  state.resolve(modifier.minimum),
                  state.resolve(modifier.maximum),
                )
              RcDimensionConstraintsModifier.VERTICAL,
              RcDimensionConstraintsModifier.REQUIRED_VERTICAL ->
                mergeRange(
                  false,
                  state.resolve(modifier.minimum),
                  state.resolve(modifier.maximum),
                )
              else ->
                diagnostics.unsupportedLimitation(
                  modifier,
                  componentId,
                  "Dimension constraint type ${modifier.type} is invalid",
                )
            }
            if (
              modifier.type == RcDimensionConstraintsModifier.REQUIRED_HORIZONTAL ||
                modifier.type == RcDimensionConstraintsModifier.REQUIRED_VERTICAL
            ) {
              diagnostics.unsupportedLimitation(
                modifier,
                componentId,
                "Required constraints cannot overflow the native parent bounds",
              )
            }
          }
          else -> Unit
        }
      }
      val padding =
        directOperations.filterIsInstance<RcPaddingModifier>().fold(FloatArray(4)) {
          result,
          modifier ->
          result[0] += state.resolve(modifier.left) / document.header.density
          result[1] += state.resolve(modifier.top) / document.header.density
          result[2] += state.resolve(modifier.right) / document.header.density
          result[3] += state.resolve(modifier.bottom) / document.header.density
          result
        }
      val offset =
        directOperations.filterIsInstance<RcOffsetModifier>().fold(FloatArray(2)) { result, modifier
          ->
          result[0] += state.resolve(modifier.x) / document.header.density
          result[1] += state.resolve(modifier.y) / document.header.density
          result
        }
      val zIndex =
        directOperations
          .filterIsInstance<RcZIndexModifier>()
          .sumOf { state.resolve(it.value).toDouble() }
          .toFloat()
      val visibility =
        directOperations.filterIsInstance<RcVisibilityModifier>().lastOrNull()?.let {
          nativeVisibility(state.integer(it.visibilityId) ?: 0)
        } ?: 1
      val cornerRadius =
        directOperations.filterIsInstance<RcRoundedClipRectModifier>().lastOrNull()?.let { modifier
          ->
          maxOf(
            state.resolve(modifier.topStart) / document.header.density,
            state.resolve(modifier.topEnd) / document.header.density,
            state.resolve(modifier.bottomStart) / document.header.density,
            state.resolve(modifier.bottomEnd) / document.header.density,
          )
        } ?: 0f
      val hasDrawContent =
        directOperations.filterIsInstance<RcNoArg>().any {
          it.opcode == RcOpcodes.MODIFIER_DRAW_CONTENT
        }
      val backgroundPaint =
        if (hasDrawContent) {
          descendantOperations(container).filterIsInstance<RcPaintData>().firstOrNull()?.let {
            NativePaint().also { paint ->
              applyPaint(it, componentId, state, paint, diagnostics, bitmaps)
            }
          }
        } else null
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
        widthType = width?.type ?: RcDimensionType.WRAP,
        widthValue =
          width?.let {
            state.resolve(it.value) /
              if (it.type == RcDimensionType.EXACT_DP) document.header.density else 1f
          } ?: 0f,
        heightType = height?.type ?: RcDimensionType.WRAP,
        heightValue =
          height?.let {
            state.resolve(it.value) /
              if (it.type == RcDimensionType.EXACT_DP) document.header.density else 1f
          } ?: 0f,
        minimumHeight = minimumHeight,
        minimumWidth = minimumWidth,
        maximumWidth = maximumWidth,
        maximumHeight = maximumHeight,
        paddingLeft = padding[0],
        paddingTop = padding[1],
        paddingRight = padding[2],
        paddingBottom = padding[3],
        cornerRadius = cornerRadius,
        hasBackground = backgroundPaint != null,
        backgroundColor = backgroundPaint?.color ?: 0,
        horizontalPositioning =
          when (operation) {
            is RcBoxLayout -> operation.horizontalPositioning
            is RcRowLayout -> operation.horizontalPositioning
            is RcColumnLayout -> operation.horizontalPositioning
            is RcStateLayout -> operation.horizontalPositioning
            else -> 1
          },
        verticalPositioning =
          when (operation) {
            is RcBoxLayout -> operation.verticalPositioning
            is RcRowLayout -> operation.verticalPositioning
            is RcColumnLayout -> operation.verticalPositioning
            is RcStateLayout -> operation.verticalPositioning
            else -> 4
          },
        spacing =
          when (operation) {
            is RcRowLayout -> state.resolve(operation.spacedBy) / document.header.density
            is RcColumnLayout -> state.resolve(operation.spacedBy) / document.header.density
            else -> 0f
          },
        offsetX = offset[0],
        offsetY = offset[1],
        zIndex = zIndex,
        visibility = visibility,
      )
    }

    val rootCommands = mutableListOf<RcNativeDrawCommand>()
    val rootChildren = mutableListOf<RcNativeNodeSnapshot>()
    for (node in linked.operations) {
      when (node) {
        is RcLinkedNode.Container -> rootChildren += nodeFor(node)
        is RcLinkedNode.Operation ->
          consume(node.operation, 0, state, paint, rootCommands, diagnostics, paths, bitmaps)
      }
    }
    val rootBehavior = state.rootContentBehavior
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
      unsupportedOpcodes = diagnostics.unsupportedOpcodes.toList(),
      notes = diagnostics.notes.toList(),
      diagnostics = diagnostics.issues.toList(),
      rootSizing = rootBehavior?.sizing ?: RcRootContentBehavior.SIZING_SCALE,
      rootMode = rootBehavior?.mode ?: RcRootContentBehavior.SCALE_FIT,
      rootAlignment = rootBehavior?.alignment ?: RcRootContentBehavior.ALIGNMENT_CENTER,
      images =
        document.operations.filterIsInstance<RcBitmapData>().map {
          RcNativeImageResource(it.imageId, it.width, it.height, it.type, it.encoding, it.data)
        },
      fonts =
        document.operations.filterIsInstance<RcFontData>().map {
          RcNativeFontResource(it.fontId, it.type, it.data)
        },
    )
  }

  private fun nativeVisibility(value: Int): Int =
    when {
      value and 32 == 32 -> 1
      value and 16 == 16 -> 0
      value and 64 == 64 -> 2
      value == 1 -> 1
      value == 2 -> 2
      else -> 0
    }

  private fun consume(
    operation: RcOperation,
    componentId: Int,
    state: RcPlayerState,
    paint: NativePaint,
    commands: MutableList<RcNativeDrawCommand>,
    diagnostics: NativeDiagnosticCollector,
    paths: Map<Int, RcPathData>,
    bitmaps: Map<Int, RcBitmapData>,
  ) {
    when (operation) {
      is RcAccessibilitySemantics,
      is RcBitmapData,
      is RcDimensionConstraintsModifier,
      is RcFontData,
      is RcHeightInModifier,
      is RcHeightModifier,
      is RcOffsetModifier,
      is RcPaddingModifier,
      is RcVisibilityModifier,
      is RcWidthInModifier,
      is RcWidthModifier,
      is RcZIndexModifier -> Unit
      is RcRootContentBehavior ->
        if (operation.scroll != RcRootContentBehavior.NONE) {
          diagnostics.unsupportedLimitation(
            operation,
            componentId,
            "Root scrolling is not implemented by the native player",
          )
        }
      is RcPaintData -> applyPaint(operation, componentId, state, paint, diagnostics, bitmaps)
      is RcFloatExpression -> state.applyFloatExpression(operation)
      is RcIntegerExpression -> state.applyIntegerExpression(operation)
      is RcColorExpression -> state.applyColorExpression(operation)
      is RcImageAttribute -> state.applyImageAttribute(operation)
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
        if (kind == null) diagnostics.unsupported(operation, componentId)
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
        if (kind == null) diagnostics.unsupported(operation, componentId)
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
        if (kind == null) diagnostics.unsupported(operation, componentId)
        else commands += paint.command(kind, values)
      }
      is RcTransform2 -> {
        val kind =
          when (operation.opcode) {
            RcOpcodes.MATRIX_TRANSLATE -> RcNativeDrawCommand.TRANSLATE
            RcOpcodes.MATRIX_SKEW -> RcNativeDrawCommand.SKEW
            else -> null
          }
        if (kind == null) diagnostics.unsupported(operation, componentId)
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
        if (paint.gradient != null) {
          diagnostics.unsupportedLimitation(
            operation,
            componentId,
            "Gradient canvas text falls back to the current solid color",
          )
        }
        val fullText = state.text(operation.textId).orEmpty()
        val start = operation.start.coerceIn(0, fullText.length)
        val end = operation.end.coerceIn(start, fullText.length)
        commands +=
          paint.command(
            RcNativeDrawCommand.TEXT,
            listOf(state.resolve(operation.x), state.resolve(operation.y), -1f, -1f),
            fullText.substring(start, end),
          )
      }
      is RcDrawTextAnchored -> {
        if (paint.gradient != null) {
          diagnostics.unsupportedLimitation(
            operation,
            componentId,
            "Gradient canvas text falls back to the current solid color",
          )
        }
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
      is RcDrawBitmap -> {
        val bitmap =
          requireNotNull(bitmaps[operation.imageId]) { "Missing bitmap ${operation.imageId}" }
        commands +=
          paint.command(
            RcNativeDrawCommand.IMAGE,
            image =
              validatedImageDraw(
                RcNativeImageDraw(
                  imageId = operation.imageId,
                  sourceLeft = 0f,
                  sourceTop = 0f,
                  sourceRight = bitmap.width.toFloat(),
                  sourceBottom = bitmap.height.toFloat(),
                  destinationLeft = state.resolve(operation.left),
                  destinationTop = state.resolve(operation.top),
                  destinationRight = state.resolve(operation.right),
                  destinationBottom = state.resolve(operation.bottom),
                  contentDescription = state.text(operation.contentDescriptionId),
                ),
                bitmaps,
              ),
          )
      }
      is RcDrawBitmapInt ->
        commands +=
          paint.command(
            RcNativeDrawCommand.IMAGE,
            image =
              validatedImageDraw(
                RcNativeImageDraw(
                  imageId = operation.imageId,
                  sourceLeft = operation.srcLeft.toFloat(),
                  sourceTop = operation.srcTop.toFloat(),
                  sourceRight = operation.srcRight.toFloat(),
                  sourceBottom = operation.srcBottom.toFloat(),
                  destinationLeft = operation.dstLeft.toFloat(),
                  destinationTop = operation.dstTop.toFloat(),
                  destinationRight = operation.dstRight.toFloat(),
                  destinationBottom = operation.dstBottom.toFloat(),
                  contentDescription = state.text(operation.contentDescriptionId),
                ),
                bitmaps,
              ),
          )
      is RcDrawBitmapScaled ->
        commands +=
          paint.command(
            RcNativeDrawCommand.IMAGE,
            image =
              validatedImageDraw(
                RcNativeImageDraw(
                  imageId = operation.imageId,
                  sourceLeft = state.resolve(operation.srcLeft),
                  sourceTop = state.resolve(operation.srcTop),
                  sourceRight = state.resolve(operation.srcRight),
                  sourceBottom = state.resolve(operation.srcBottom),
                  destinationLeft = state.resolve(operation.dstLeft),
                  destinationTop = state.resolve(operation.dstTop),
                  destinationRight = state.resolve(operation.dstRight),
                  destinationBottom = state.resolve(operation.dstBottom),
                  scaleType = operation.scaleType,
                  scaleFactor = state.resolve(operation.scaleFactor),
                  contentDescription = state.text(operation.contentDescriptionId),
                ),
                bitmaps,
              ),
          )
      is RcIdOperation ->
        if (operation.opcode == RcOpcodes.DRAW_PATH || operation.opcode == RcOpcodes.CLIP_PATH) {
          val data = requireNotNull(paths[operation.id]) { "Missing path ${operation.id}" }
          commands +=
            paint.command(
              if (operation.opcode == RcOpcodes.DRAW_PATH) RcNativeDrawCommand.PATH
              else RcNativeDrawCommand.CLIP_PATH,
              path = nativePath(data, componentId, state, diagnostics),
              pathWinding = data.winding,
            )
        } else {
          diagnostics.unsupported(operation, componentId)
        }
      else -> {
        // Data declarations and layout metadata are already represented in state or the node tree.
        if (operation.opcode in DRAWING_OR_BEHAVIOR_OPCODES) {
          diagnostics.unsupported(operation, componentId)
        }
      }
    }
  }

  private fun validatedImageDraw(
    draw: RcNativeImageDraw,
    bitmaps: Map<Int, RcBitmapData>,
  ): RcNativeImageDraw {
    val bitmap = requireNotNull(bitmaps[draw.imageId]) { "Missing bitmap ${draw.imageId}" }
    val coordinates =
      listOf(
        draw.sourceLeft,
        draw.sourceTop,
        draw.sourceRight,
        draw.sourceBottom,
        draw.destinationLeft,
        draw.destinationTop,
        draw.destinationRight,
        draw.destinationBottom,
        draw.scaleFactor,
      )
    require(coordinates.all(Float::isFinite)) { "Bitmap ${draw.imageId} geometry must be finite" }
    require(
      draw.sourceLeft >= 0f &&
        draw.sourceTop >= 0f &&
        draw.sourceRight > draw.sourceLeft &&
        draw.sourceBottom > draw.sourceTop &&
        draw.sourceRight <= bitmap.width.toFloat() &&
        draw.sourceBottom <= bitmap.height.toFloat()
    ) {
      "Bitmap ${draw.imageId} source rectangle is outside ${bitmap.width}x${bitmap.height}"
    }
    require(
      draw.destinationRight > draw.destinationLeft && draw.destinationBottom > draw.destinationTop
    ) {
      "Bitmap ${draw.imageId} destination rectangle must have positive area"
    }
    require(draw.scaleType in 0..7) { "Bitmap ${draw.imageId} scale type is invalid" }
    if (draw.scaleType == 7) {
      require(draw.scaleFactor > 0f) { "Bitmap ${draw.imageId} scale factor must be positive" }
    }
    return draw
  }

  private fun nativePath(
    data: RcPathData,
    componentId: Int,
    state: RcPlayerState,
    diagnostics: NativeDiagnosticCollector,
  ): List<RcNativePathCommand> {
    val result = mutableListOf<RcNativePathCommand>()
    var index = 0
    fun argument(): Float {
      require(index < data.words.size) { "Truncated PathData ${data.id} at word $index" }
      return state.resolve(data.words[index++])
    }
    fun skipLegacyPadding() {
      require(index + 2 <= data.words.size) { "Truncated PathData ${data.id} legacy padding" }
      index += 2
    }
    while (index < data.words.size) {
      val commandIndex = index
      val command =
        requireNotNull(data.words[index++].referencedId) {
          "PathData ${data.id} command at word $commandIndex is not NaN-encoded"
        }
      val values =
        when (command) {
          RcPathCommands.MOVE -> listOf(argument(), argument())
          RcPathCommands.LINE -> {
            skipLegacyPadding()
            listOf(argument(), argument())
          }
          RcPathCommands.QUADRATIC -> {
            skipLegacyPadding()
            List(4) { argument() }
          }
          RcPathCommands.CONIC -> {
            skipLegacyPadding()
            diagnostics.unsupportedLimitation(
              data,
              componentId,
              "Rational conic path segments are approximated as quadratic curves",
            )
            List(5) { argument() }
          }
          RcPathCommands.CUBIC -> {
            skipLegacyPadding()
            List(6) { argument() }
          }
          RcPathCommands.CLOSE -> emptyList()
          RcPathCommands.DONE -> return result
          else -> throw IllegalArgumentException("PathData ${data.id} has unknown command $command")
        }
      result +=
        RcNativePathCommand(
          kind = command,
          first = values.getOrElse(0) { 0f },
          second = values.getOrElse(1) { 0f },
          third = values.getOrElse(2) { 0f },
          fourth = values.getOrElse(3) { 0f },
          fifth = values.getOrElse(4) { 0f },
          sixth = values.getOrElse(5) { 0f },
        )
    }
    return result
  }

  private fun applyPaint(
    operation: RcPaintData,
    componentId: Int,
    state: RcPlayerState,
    paint: NativePaint,
    diagnostics: NativeDiagnosticCollector,
    bitmaps: Map<Int, RcBitmapData>,
  ) {
    var index = 0
    while (index < operation.words.size) {
      val command = operation.words[index++]
      val type = command and 0xffff
      if (type == 11) {
        val gradientType = command ushr 16
        require(gradientType in 0..2) { "Gradient type $gradientType is invalid" }
        require(index < operation.words.size) { "Paint command 11 is truncated" }
        val meta = operation.words[index++]
        val colorCount = meta and 0xff
        require(colorCount in 1..16) { "Gradient color count $colorCount is invalid" }
        require(index + colorCount < operation.words.size) { "Paint command 11 is truncated" }
        val register = (meta ushr 16) and 0xffff
        val colors =
          List(colorCount) { colorIndex ->
            val word = operation.words[index++]
            if (register and (1 shl colorIndex) != 0) state.color(word) else word
          }
        val stopCount = operation.words[index++]
        require(stopCount == 0 || stopCount == colorCount) {
          "Gradient stop count $stopCount does not match $colorCount colors"
        }
        require(index + stopCount <= operation.words.size) { "Paint command 11 is truncated" }
        val stops = List(stopCount) { state.resolveWord(operation.words[index++]) }
        require(stops.all { it.isFinite() && it in 0f..1f }) {
          "Gradient stops must be finite and between 0 and 1"
        }
        require(stops.zipWithNext().all { (low, high) -> low <= high }) {
          "Gradient stops must be ordered"
        }
        val coordinateCount = if (gradientType == RcNativeGradient.LINEAR) 4 else 3
        // Sweep gradients encode only their center; radial gradients add radius.
        val actualCoordinateCount =
          if (gradientType == RcNativeGradient.SWEEP) 2 else coordinateCount
        val trailingWords =
          actualCoordinateCount + if (gradientType == RcNativeGradient.SWEEP) 0 else 1
        require(index + trailingWords <= operation.words.size) { "Paint command 11 is truncated" }
        val coordinateWords = List(actualCoordinateCount) { operation.words[index++] }
        val coordinates = coordinateWords.map { state.resolveWord(it) }
        require(coordinates.all(Float::isFinite)) {
          "Gradient coordinates must be finite: words=$coordinateWords resolved=$coordinates"
        }
        if (gradientType == RcNativeGradient.RADIAL) {
          require(coordinates[2] > 0f) { "Radial gradient radius must be positive" }
        }
        val tileMode = if (gradientType == RcNativeGradient.SWEEP) 0 else operation.words[index++]
        require(tileMode in 0..3) { "Gradient tile mode $tileMode is invalid" }
        if (tileMode != 0) {
          diagnostics.unsupportedLimitation(
            operation,
            componentId,
            "Gradient tile mode $tileMode is approximated with clamp",
          )
        }
        paint.gradient =
          RcNativeGradient(
            kind = gradientType,
            colors = colors,
            stops = stops,
            first = coordinates[0],
            second = coordinates[1],
            third = coordinates.getOrElse(2) { 0f },
            fourth = coordinates.getOrElse(3) { 0f },
            tileMode = tileMode,
          )
        paint.textureImageId = -1
        continue
      }
      val argumentCount =
        when (type) {
          1,
          4,
          5,
          9,
          12,
          13,
          16,
          19,
          20,
          22 -> 1
          24 -> 3
          7,
          8,
          10,
          14,
          15,
          17,
          18,
          21 -> 0
          23 -> (command ushr 16) * 2
          else -> {
            diagnostics.unsupportedLimitation(
              operation,
              componentId,
              "Paint command $type is not represented by the native POC",
            )
            return
          }
        }
      require(index + argumentCount <= operation.words.size) { "Paint command $type is truncated" }
      when (type) {
        1 -> paint.textSize = state.resolveWord(operation.words[index++])
        4 -> paint.color = operation.words[index++]
        5 -> paint.strokeWidth = state.resolveWord(operation.words[index++])
        8 -> paint.stroke = command ushr 16 == 1
        12 -> paint.alpha = state.resolveWord(operation.words[index++]).coerceIn(0f, 1f)
        19 -> paint.color = state.color(operation.words[index++])
        24 -> {
          val bitmapId = operation.words[index++]
          require(bitmapId in bitmaps) { "Missing bitmap $bitmapId" }
          val tileModes = operation.words[index++]
          val tileX = tileModes and 0xf
          val tileY = (tileModes ushr 16) and 0xf
          require(tileX in 0..3 && tileY in 0..3) { "Texture tile modes are invalid" }
          index++ // Filtering/max-anisotropy is delegated to UIKit for the POC.
          if (tileX != 1 || tileY != 1) {
            diagnostics.unsupportedLimitation(
              operation,
              componentId,
              "Texture tile modes $tileX/$tileY use Core Graphics pattern repetition",
            )
          }
          paint.gradient = null
          paint.textureImageId = bitmapId
          paint.textureTileModeX = tileX
          paint.textureTileModeY = tileY
        }
        9 -> {
          val shaderId = operation.words[index++]
          if (shaderId == 0) {
            paint.gradient = null
            paint.textureImageId = -1
          } else
            diagnostics.unsupportedLimitation(
              operation,
              componentId,
              "Shader id $shaderId is not represented by the native player",
            )
        }
        10,
        14,
        17,
        21 -> Unit
        22 -> {
          index++
          diagnostics.unsupportedLimitation(
            operation,
            componentId,
            "Texture shader matrices are not represented by the native POC",
          )
        }
        7 -> {
          val cap = command ushr 16
          if (cap in 0..2) paint.strokeCap = cap
          else
            diagnostics.unsupportedLimitation(
              operation,
              componentId,
              "Stroke cap $cap is invalid",
            )
        }
        15 -> {
          val join = command ushr 16
          if (join in 0..2) paint.strokeJoin = join
          else
            diagnostics.unsupportedLimitation(
              operation,
              componentId,
              "Stroke join $join is invalid",
            )
        }
        18 -> {
          val blendMode = command ushr 16
          if (blendMode in 0..28) paint.blendMode = blendMode
          else
            diagnostics.unsupportedLimitation(
              operation,
              componentId,
              "Blend mode $blendMode is invalid",
            )
        }
        16 -> {
          val style = command ushr 16
          paint.textWeight = (style and 0x3ff).takeIf { it > 0 }?.toFloat() ?: 400f
          paint.fontStyle = if (style shr 10 > 0) 2 else 0
          paint.fontType = operation.words[index++]
        }
        23 -> {
          if (argumentCount > 0) {
            diagnostics.unsupportedLimitation(
              operation,
              componentId,
              "Font axes are not represented by the native POC",
            )
          }
          index += argumentCount
        }
        else -> {
          diagnostics.unsupportedLimitation(
            operation,
            componentId,
            "Paint command $type is not represented by the native POC",
          )
          index += argumentCount
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
    var strokeCap: Int = 0,
    var strokeJoin: Int = 0,
    var blendMode: Int = 3,
    var textSize: Float = 16f,
    var fontType: Int = 0,
    var fontStyle: Int = 0,
    var textWeight: Float = 400f,
    var gradient: RcNativeGradient? = null,
    var textureImageId: Int = -1,
    var textureTileModeX: Int = 0,
    var textureTileModeY: Int = 0,
  ) {
    fun command(
      kind: Int,
      values: List<Float> = emptyList(),
      text: String? = null,
      textWeight: Float = this.textWeight,
      path: List<RcNativePathCommand> = emptyList(),
      pathWinding: Int = 0,
      textStyle: RcNativeTextStyle? = null,
      image: RcNativeImageDraw? = null,
    ) =
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
        strokeCap = strokeCap,
        strokeJoin = strokeJoin,
        blendMode = blendMode,
        textSize = textSize,
        textWeight = textWeight,
        text = text,
        path = path,
        pathWinding = pathWinding,
        gradient = gradient,
        textStyle =
          textStyle
            ?: if (kind == RcNativeDrawCommand.TEXT) {
              RcNativeTextStyle(
                fontStyle = fontStyle,
                fontFamilyId = fontType,
                fontFamilyName =
                  when (fontType) {
                    1 -> "sans-serif"
                    2 -> "serif"
                    3 -> "monospace"
                    else -> "default"
                  },
              )
            } else null,
        image = image,
        textureImageId = textureImageId,
        textureTileModeX = textureTileModeX,
        textureTileModeY = textureTileModeY,
      )
  }

  private class NativeDiagnosticCollector {
    val unsupportedOpcodes = linkedSetOf<Int>()
    val notes = linkedSetOf<String>()
    val issues = linkedSetOf<RcNativeDiagnostic>()

    fun warning(operation: RcOperation, componentId: Int, reason: String) {
      notes += reason
      add(RcNativeDiagnostic.WARNING, operation, componentId, reason)
    }

    fun unsupported(
      operation: RcOperation,
      componentId: Int,
      reason: String = "Operation is not represented by the native player",
    ) {
      unsupportedOpcodes += operation.opcode
      add(RcNativeDiagnostic.UNSUPPORTED, operation, componentId, reason)
    }

    fun unsupportedLimitation(operation: RcOperation, componentId: Int, reason: String) {
      notes += reason
      add(RcNativeDiagnostic.UNSUPPORTED, operation, componentId, reason)
    }

    private fun add(severity: Int, operation: RcOperation, componentId: Int, reason: String) {
      issues +=
        RcNativeDiagnostic(
          severity = severity,
          opcode = operation.opcode,
          operationName =
            RcOperationInventory.byOpcode[operation.opcode]?.stableName
              ?: "Opcode ${operation.opcode}",
          componentId = componentId,
          reason = reason,
        )
    }
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

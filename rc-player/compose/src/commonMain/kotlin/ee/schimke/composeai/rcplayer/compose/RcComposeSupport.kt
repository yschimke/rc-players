package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsiblePriorityModifier
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcColorAttribute
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcConditionalOperations
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcDataMapLookup
import ee.schimke.composeai.rcplayer.protocol.RcDebugMessage
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextOnCircle
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcDynamicFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFitBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionCall
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionDefine
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFlowLayout
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHapticFeedback
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcHostMetadataAction
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedAction
import ee.schimke.composeai.rcplayer.protocol.RcIdMap
import ee.schimke.composeai.rcplayer.protocol.RcImageAttribute
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLayoutCompute
import ee.schimke.composeai.rcplayer.protocol.RcLoopOperation
import ee.schimke.composeai.rcplayer.protocol.RcMarqueeModifier
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcOperationInventory
import ee.schimke.composeai.rcplayer.protocol.RcOperationProfile
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcParticleCompare
import ee.schimke.composeai.rcplayer.protocol.RcParticleDefine
import ee.schimke.composeai.rcplayer.protocol.RcParticleLoop
import ee.schimke.composeai.rcplayer.protocol.RcPlaySound
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcScrollModifier
import ee.schimke.composeai.rcplayer.protocol.RcShaderData
import ee.schimke.composeai.rcplayer.protocol.RcTextAttribute
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextLookup
import ee.schimke.composeai.rcplayer.protocol.RcTextLookupInt
import ee.schimke.composeai.rcplayer.protocol.RcTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcTextStyle
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcTextSubtext
import ee.schimke.composeai.rcplayer.protocol.RcTextTransform
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueFloatExpressionChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerExpressionChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcValueStringChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.protocol.supportReport
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcIntegerExpressionEvaluator
import ee.schimke.composeai.rcplayer.runtime.RcLayoutTree
import ee.schimke.composeai.rcplayer.runtime.RcLinkedNode
import ee.schimke.composeai.rcplayer.runtime.hasPortableVisibilityAnimation
import ee.schimke.composeai.rcplayer.runtime.isLayoutComputeExecutable
import org.jetbrains.skia.RuntimeEffect

/**
 * How much of a document an issue costs, and therefore whether a lenient host can play it anyway.
 *
 * The line is drawn by what the *renderer* does when it meets the operation, not by how serious the
 * gap looks. `drawOperations` ends in `else -> Unit`, so an operation it has no branch for is
 * silently skipped and the rest of the document draws normally — that is [SKIPPABLE]. An operation
 * it does have a branch for, handed data that branch cannot execute, throws from inside the draw
 * pass and takes the whole composition with it — that is [BLOCKING], and no mode can play it.
 */
public enum class RcComposeSupportSeverity {
  /**
   * The document cannot be drawn at all: the renderer would throw partway through the draw pass, or
   * would need data the host has not supplied.
   */
  BLOCKING,

  /**
   * The operation decodes and is known, but this backend draws nothing for it. A strict host
   * refuses the document; a lenient one plays it with that operation skipped.
   */
  SKIPPABLE,
}

public data class RcComposeSupportIssue(
  val operationIndex: Int,
  val operation: String,
  val detail: String,
  val severity: RcComposeSupportSeverity = RcComposeSupportSeverity.BLOCKING,
)

public data class RcComposeSupportReport(val issues: List<RcComposeSupportIssue>) {
  public val fullyRenderable: Boolean
    get() = issues.isEmpty()

  /**
   * The issues that survive lenient mode — the ones no host can skip its way past.
   *
   * Everything else is an operation the player knows but this backend does not draw, which a
   * lenient host renders as a hole rather than as a refused document.
   */
  public val blockingIssues: List<RcComposeSupportIssue>
    get() = issues.filter { it.severity == RcComposeSupportSeverity.BLOCKING }

  /** True when the document can be played with any known operation, skipping what is undrawn. */
  public val playable: Boolean
    get() = blockingIssues.isEmpty()

  public fun requireFullyRenderable() {
    if (issues.isNotEmpty()) {
      throw IllegalArgumentException(
        "Document is not renderable by the CMP player: " +
          issues.joinToString { "${it.operation}[${it.operationIndex}]: ${it.detail}" }
      )
    }
  }

  /**
   * The lenient counterpart of [requireFullyRenderable]: accept the document as long as every
   * operation it carries is one the player *knows*, even where this backend draws nothing for it.
   *
   * This is what a host wants when a document is worth seeing incomplete — a catalog page, a
   * preview, a diff lane — rather than not at all. It is the gate for documents written against a
   * profile wider than the one this backend advertises: `DrawTextOnCircle` is writable under
   * `WEAR_WIDGETS` while no reader profile lists it, so a document carrying one is refused whole by
   * every strict player even though the rest of it draws (wear-m3-catalog#321).
   */
  public fun requirePlayable() {
    val blocking = blockingIssues
    if (blocking.isNotEmpty()) {
      throw IllegalArgumentException(
        "Document is not playable by the CMP player: " +
          blocking.joinToString { "${it.operation}[${it.operationIndex}]: ${it.detail}" }
      )
    }
  }

  /**
   * Apply whichever of the two gates above [lenient] selects, so a host that carries the mode as a
   * flag does not have to branch at every call site.
   */
  public fun requireRenderable(lenient: Boolean) {
    if (lenient) requirePlayable() else requireFullyRenderable()
  }
}

/**
 * Backend-specific coverage, including nested PaintBundle commands hidden behind one RC opcode.
 *
 * [allowExternalImagePlaceholders] treats host-backed bitmap encodings as intentionally blank. It
 * is useful for offline renderers that need to exercise the rest of a document without claiming
 * that an unreachable URL image was decoded.
 */
public fun RcDocument.composeSupportReport(
  profile: RcOperationProfile? = null,
  availableFontFamilies: Set<String> = emptySet(),
  allowExternalImagePlaceholders: Boolean = false,
): RcComposeSupportReport =
  composeSupportReport(
    profile,
    availableFontFamilies,
    allowExternalImagePlaceholders,
    emptySet(),
  )

public fun RcDocument.composeSupportReport(
  profile: RcOperationProfile? = null,
  availableFontFamilies: Set<String> = emptySet(),
  allowExternalImagePlaceholders: Boolean = false,
  availableCustomComponents: Set<String>,
): RcComposeSupportReport {
  val issues = mutableListOf<RcComposeSupportIssue>()
  val bitmapIds = operations.filterIsInstance<RcBitmapData>().mapTo(mutableSetOf()) { it.imageId }
  val shaderIds = operations.filterIsInstance<RcShaderData>().mapTo(mutableSetOf()) { it.shaderId }
  // Both the shader-install scan and the offscreen audit ask what the RENDERER will do, and the
  // renderer draws the LINKED stream, not the wire stream. `RcDocumentLinker` drops a
  // `ReferencedOperations` definition nothing includes, and materialises a `MacroDefine` body only
  // where it is called — the body is a separate byte array that `document.operations` never holds.
  // Scanning the raw stream therefore errs in both directions at once: it counts a paint in an
  // unused definition that never draws, and misses one expanded from a macro that does.
  //
  // Where linking fails there is nothing to walk, and the document is already refused for the
  // failure itself (`ContainerStructure`, below). The raw stream is the fallback there, which is no
  // worse than what these checks would otherwise see.
  val linkResult = runCatching { RcDocumentLinker.link(this) }
  val executed =
    linkResult.getOrNull()?.let { executedOperations(it.operations) }
      ?: operations.map { RcExecutedOperation(it, certain = true) }
  val installedShaders = installedShaderDeclarations(executed, operations, shaderIds)
  val fontIds = operations.filterIsInstance<RcFontData>().mapTo(mutableSetOf()) { it.fontId }
  val particleDefinitionGroups = operations.filterIsInstance<RcParticleDefine>().groupBy { it.id }
  val particleDefinitions = particleDefinitionGroups.mapValues { it.value.last() }
  val texts =
    operations.filterIsInstance<ee.schimke.composeai.rcplayer.protocol.RcTextData>().associate {
      it.id to it.text
    }
  val idMaps = operations.filterIsInstance<RcIdMap>().associateBy { it.id }
  // A text id is declared either by a literal DATA_TEXT or by a runtime operation that publishes a
  // string under its out id. Only the literals carry a value the report can inspect (font family
  // names, style properties), so `texts` stays literal-only while `textIds` covers both.
  val textIds =
    texts.keys +
      operations.mapNotNull { operation ->
        when (operation) {
          is RcTextMerge -> operation.outId
          is RcTextSubtext -> operation.outId
          is RcTextTransform -> operation.outId
          is RcTextFromFloat -> operation.outId
          is RcTextLookup -> operation.outId
          is RcTextLookupInt -> operation.outId
          // A data-map lookup writes `outId` to the store its selected entry's type names, so it
          // declares text only if the map can yield a string at all. The key is resolved at
          // runtime, so a map mixing string and non-string entries stays permissive: rejecting it
          // would refuse a document whose key selects the string.
          is RcDataMapLookup ->
            operation.outId.takeIf { _ ->
              idMaps[operation.mapId]?.entries?.any { it.type == RcIdMap.TYPE_STRING } ?: true
            }
          else -> null
        }
      }
  // A colour id is declared by a literal `ColorConstant` or by one of the two operations that
  // *compute* a colour under an out id — `ColorExpression` (a tween/HSV/ARGB build) and
  // `ColorTheme` (a light/dark pair, resolved by `applyColorTheme`). The player publishes all
  // three into the same colour store, so a text style reading a computed id resolves exactly like
  // one reading a literal; counting only the literals rejected documents the player renders fine.
  // Every themed `RemoteButton` label is one — a `ColorExpression` over the container's
  // `ColorAttribute` channels (compose-ai-tools#4166).
  val colorIds =
    operations.mapNotNullTo(mutableSetOf()) { operation ->
      when (operation) {
        is ee.schimke.composeai.rcplayer.protocol.RcColorConstant -> operation.id
        is RcColorExpression -> operation.outId
        is ee.schimke.composeai.rcplayer.protocol.RcColorTheme -> operation.outId
        else -> null
      }
    }
  val dynamicLists = operations.filterIsInstance<RcDynamicFloatList>().associateBy { it.id }
  val integerExpressionIds =
    operations.filterIsInstance<RcIntegerExpression>().map { it.outId }.toSet()
  val floatExpressionIds =
    operations
      .filterIsInstance<ee.schimke.composeai.rcplayer.protocol.RcFloatExpression>()
      .map { it.id }
      .toSet()
  supportReport().parseOnly.forEach { entry ->
    // Decoded, inert, and skipped by `drawOperations`' `else -> Unit`. Refusing the document over
    // it is a strict-mode choice, not a rendering constraint.
    issues +=
      RcComposeSupportIssue(
        -1,
        entry.stableName,
        "operation is decoded but has no semantics",
        RcComposeSupportSeverity.SKIPPABLE,
      )
  }
  if (profile != null) {
    operations.forEachIndexed { index, operation ->
      if (!profile.supports(operation.opcode)) {
        issues +=
          RcComposeSupportIssue(
            index,
            RcOperationInventory.byOpcode[operation.opcode]?.stableName
              ?: "Opcode${operation.opcode}",
            "operation is excluded from the ${profile.name} profile",
            // A profile is an advertised subset, not a capability boundary: the operation decoded,
            // so it is known, and the renderer either draws it or skips it. Excluding it is the
            // reason lenient mode exists.
            RcComposeSupportSeverity.SKIPPABLE,
          )
      }
    }
  }
  operations.forEachIndexed { index, operation ->
    if (operation is RcCustomLayout) {
      val config = texts[operation.configId]
      when {
        config == null ->
          issues +=
            RcComposeSupportIssue(
              index,
              "Custom",
              "config text id ${operation.configId} is not declared",
            )
        config !in availableCustomComponents ->
          issues +=
            RcComposeSupportIssue(
              index,
              "Custom",
              "host custom component '$config' is not registered",
            )
      }
    }
    if (operation is RcAnimationSpec) {
      when {
        !operation.motionDurationMillis.value.isFinite() ||
          operation.motionDurationMillis.value < 0f ->
          issues +=
            RcComposeSupportIssue(
              index,
              "AnimationSpec",
              "motion duration ${operation.motionDurationMillis.value} is not supported",
            )
        !operation.visibilityDurationMillis.value.isFinite() ||
          operation.visibilityDurationMillis.value < 0f ->
          issues +=
            RcComposeSupportIssue(
              index,
              "AnimationSpec",
              "visibility duration ${operation.visibilityDurationMillis.value} is not supported",
            )
        operation.motionEasingType !in setOf(1, 2, 3, 4, 5, 6, 13, 14) ->
          issues +=
            RcComposeSupportIssue(
              index,
              "AnimationSpec",
              "motion easing ${operation.motionEasingType} requires parameters or is unknown",
            )
        operation.visibilityEasingType !in setOf(1, 2, 3, 4, 5, 6, 13, 14) ->
          issues +=
            RcComposeSupportIssue(
              index,
              "AnimationSpec",
              "visibility easing ${operation.visibilityEasingType} requires parameters or is unknown",
            )
        !operation.hasPortableVisibilityAnimation ->
          issues +=
            RcComposeSupportIssue(
              index,
              "AnimationSpec",
              "exit animation ${operation.exitAnimation.wireValue} requires ParticleAnimation",
            )
      }
    }
    if (operation is RcPaintData) {
      paintIssue(operation, shaderIds)?.let { detail ->
        issues += RcComposeSupportIssue(index, "PaintData", detail)
      }
    }
    if (operation is RcShaderData) {
      when {
        operation.shaderTextId !in textIds ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ShaderData",
              "shader text id ${operation.shaderTextId} is not declared",
            )
        texts[operation.shaderTextId].isNullOrBlank() ->
          issues += RcComposeSupportIssue(index, "ShaderData", "shader source is empty")
        !runtimeShaderSourceIsSupported(texts.getValue(operation.shaderTextId)) ->
          // The severity turns on whether a paint INSTALLS this shader, because that is what
          // decides
          // which half of the enum's contract applies.
          //
          // Declared and unused, it is SKIPPABLE exactly as documented: the operation decodes, the
          // backend draws nothing for it, and a lenient host plays the rest. Nothing ever asks Skia
          // to compile it.
          //
          // Installed by a paint, it is BLOCKING — "the renderer would throw partway through the
          // draw pass", verbatim. `applyPaint` reaches `buildRuntimeShader`, whose catch RETHROWS
          // as
          // IllegalArgumentException, and no one skips it. Calling that SKIPPABLE promised a
          // lenient
          // host it could play the document with the operation skipped, and then the frame died.
          issues +=
            RcComposeSupportIssue(
              index,
              "ShaderData",
              "shader source is not accepted by the Skia runtime",
              if (operation in installedShaders) RcComposeSupportSeverity.BLOCKING
              else RcComposeSupportSeverity.SKIPPABLE,
            )
        operation.floatUniforms.any { it.value.isEmpty() } ->
          issues += RcComposeSupportIssue(index, "ShaderData", "float uniform has no values")
        operation.intUniforms.any { it.value.size !in 1..4 } ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ShaderData",
              "integer uniform must contain 1..4 values on the Skia backend",
            )
        else ->
          operation.bitmapUniforms.entries
            .firstOrNull { it.value !in bitmapIds }
            ?.let {
              issues +=
                RcComposeSupportIssue(
                  index,
                  "ShaderData",
                  "bitmap uniform '${it.key}' references undeclared bitmap ${it.value}",
                )
            }
      }
    }
    if (
      operation is RcDrawToBitmap && operation.bitmapId != 0 && operation.bitmapId !in bitmapIds
    ) {
      issues +=
        RcComposeSupportIssue(
          index,
          "DrawToBitmap",
          "bitmap id ${operation.bitmapId} is not declared",
        )
    }
    if (operation is RcDebugMessage && operation.textId !in textIds) {
      issues +=
        RcComposeSupportIssue(index, "DebugMessage", "text id ${operation.textId} is not declared")
    }
    if (operation is RcDrawTextOnCircle) {
      when {
        operation.textId !in textIds ->
          issues +=
            RcComposeSupportIssue(
              index,
              "DrawTextOnCircle",
              "text id ${operation.textId} is not declared",
            )
        operation.alignment !in RcDrawTextOnCircle.ALIGN_START..RcDrawTextOnCircle.ALIGN_END ->
          issues +=
            RcComposeSupportIssue(
              index,
              "DrawTextOnCircle",
              "alignment ${operation.alignment} is not implemented",
            )
        operation.placement !in
          RcDrawTextOnCircle.PLACEMENT_OUTSIDE..RcDrawTextOnCircle.PLACEMENT_INSIDE ->
          issues +=
            RcComposeSupportIssue(
              index,
              "DrawTextOnCircle",
              "placement ${operation.placement} is not implemented",
            )
      }
    }
    val supportedMeasurementTypes =
      when (operation) {
        is RcTextMeasure -> 0..5
        is RcTextAttribute -> 0..6
        else -> null
      }
    val measurementType =
      when (operation) {
        is RcTextMeasure -> operation.type and 0xff
        is RcTextAttribute -> operation.type and 0xff
        else -> null
      }
    if (
      measurementType != null &&
        supportedMeasurementTypes != null &&
        measurementType !in supportedMeasurementTypes
    ) {
      issues +=
        RcComposeSupportIssue(index, "TextMeasurement", "type $measurementType is not implemented")
    }
    if (operation is RcBitmapData) {
      when {
        operation.encoding != RcBitmapData.ENCODING_INLINE && !allowExternalImagePlaceholders ->
          issues +=
            RcComposeSupportIssue(
              index,
              "BitmapData",
              "encoding ${operation.encoding} requires an image host",
            )
        operation.type !in
          setOf(
            RcBitmapData.TYPE_PNG_8888,
            RcBitmapData.TYPE_PNG,
            RcBitmapData.TYPE_RAW8,
            RcBitmapData.TYPE_RAW8888,
            RcBitmapData.TYPE_PNG_ALPHA_8,
          ) ->
          issues +=
            RcComposeSupportIssue(index, "BitmapData", "type ${operation.type} is not implemented")
        operation.type == RcBitmapData.TYPE_RAW8 &&
          operation.data.size < operation.width * operation.height ->
          issues += RcComposeSupportIssue(index, "BitmapData", "raw alpha payload is truncated")
        operation.type == RcBitmapData.TYPE_RAW8888 &&
          operation.data.size < operation.width * operation.height * 4 ->
          issues += RcComposeSupportIssue(index, "BitmapData", "raw RGBA payload is truncated")
      }
    }
    if (operation is RcImageAttribute && operation.type !in 0..1) {
      issues +=
        RcComposeSupportIssue(index, "ImageAttribute", "type ${operation.type} is not implemented")
    }
    if (operation is RcImageLayout) {
      if (operation.bitmapId !in bitmapIds) {
        issues +=
          RcComposeSupportIssue(
            index,
            "ImageLayout",
            "bitmap id ${operation.bitmapId} is not declared",
          )
      }
      if (operation.scaleType !in 0..7) {
        issues +=
          RcComposeSupportIssue(
            index,
            "ImageLayout",
            "scale type ${operation.scaleType} is not implemented",
          )
      }
    }
    if (operation is RcTextLayout) {
      when {
        operation.textId !in textIds ->
          issues +=
            RcComposeSupportIssue(
              index,
              "TextLayout",
              "text id ${operation.textId} is not declared",
            )
        operation.fontStyle !in 0..3 ->
          issues +=
            RcComposeSupportIssue(
              index,
              "TextLayout",
              "font style ${operation.fontStyle} is not implemented",
            )
        fontFamilyIssue(operation.fontFamilyId, texts, fontIds, availableFontFamilies) != null ->
          issues +=
            RcComposeSupportIssue(
              index,
              "TextLayout",
              requireNotNull(
                fontFamilyIssue(operation.fontFamilyId, texts, fontIds, availableFontFamilies)
              ),
            )
        operation.textAlign !in RcTextLayout.ALIGN_LEFT..RcTextLayout.ALIGN_END ->
          issues +=
            RcComposeSupportIssue(
              index,
              "TextLayout",
              "text alignment ${operation.textAlign} is not implemented",
            )
        operation.flags and RcTextLayout.FLAG_DYNAMIC_COLOR.inv() != 0 ->
          issues +=
            RcComposeSupportIssue(
              index,
              "TextLayout",
              "flags ${operation.flags} are not implemented",
            )
        operation.flags and RcTextLayout.FLAG_DYNAMIC_COLOR != 0 && operation.color !in colorIds ->
          issues +=
            RcComposeSupportIssue(
              index,
              "TextLayout",
              "dynamic color id ${operation.color} is not declared",
            )
        operation.overflow !in RcTextLayout.OVERFLOW_CLIP..RcTextLayout.OVERFLOW_MIDDLE_ELLIPSIS ->
          issues +=
            RcComposeSupportIssue(
              index,
              "TextLayout",
              "overflow ${operation.overflow} is not implemented",
            )
        operation.maxLines <= 0 ->
          issues += RcComposeSupportIssue(index, "TextLayout", "maxLines must be positive")
      }
    }
    if (operation is RcCoreText) {
      when {
        operation.textId !in textIds ->
          issues +=
            RcComposeSupportIssue(index, "CoreText", "text id ${operation.textId} is not declared")
        else ->
          textStyleIssue(operation.properties, colorIds, texts, fontIds, availableFontFamilies)
            ?.let { detail -> issues += RcComposeSupportIssue(index, "CoreText", detail) }
      }
    }
    if (operation is RcTextStyle) {
      textStyleIssue(operation.properties, colorIds, texts, fontIds, availableFontFamilies)?.let {
        detail ->
        issues += RcComposeSupportIssue(index, "TextStyle", detail)
      }
    }
    if (operation is RcColorAttribute && operation.type !in 0..6) {
      issues +=
        RcComposeSupportIssue(index, "ColorAttribute", "type ${operation.type} is not implemented")
    }
    if (operation is RcColorExpression && operation.mode !in 0..6) {
      issues +=
        RcComposeSupportIssue(index, "ColorExpression", "mode ${operation.mode} is not implemented")
    }
    if (
      operation is RcBackgroundModifier &&
        operation.shapeType !in
          RcBackgroundModifier.SHAPE_RECTANGLE..RcBackgroundModifier.SHAPE_CIRCLE
    ) {
      issues +=
        RcComposeSupportIssue(
          index,
          "BackgroundModifier",
          "shape type ${operation.shapeType} is not implemented",
        )
    }
    if (operation is RcConditionalOperations && operation.type !in 0..6) {
      issues +=
        RcComposeSupportIssue(
          index,
          "ConditionalOperations",
          "condition type ${operation.type} is not implemented",
        )
    }
    if (operation is RcLoopOperation) {
      val from = operation.from.takeIf { it.referencedId == null }?.value
      val step = operation.step.takeIf { it.referencedId == null }?.value
      val until = operation.until.takeIf { it.referencedId == null }?.value
      when {
        listOfNotNull(from, step, until).any { !it.isFinite() } ->
          issues += RcComposeSupportIssue(index, "LoopOperation", "literal values must be finite")
        step == 0f ->
          issues += RcComposeSupportIssue(index, "LoopOperation", "literal step cannot be zero")
        step != null && from != null && until != null && step < 0f && from < until ->
          issues +=
            RcComposeSupportIssue(
              index,
              "LoopOperation",
              "literal step is negative while from < until",
            )
        step != null &&
          from != null &&
          until != null &&
          from < until &&
          (until - from) / step > 10_000f ->
          issues +=
            RcComposeSupportIssue(index, "LoopOperation", "literal loop exceeds 10000 iterations")
      }
    }
    if (operation is RcParticleDefine) {
      when {
        particleDefinitionGroups[operation.id].orEmpty().size > 1 ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ParticlesCreate",
              "particle system ${operation.id} is defined more than once",
            )
        operation.particleCount !in 0..8_000 ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ParticlesCreate",
              "${operation.particleCount} particles are outside the executable 0..8000 range",
            )
        operation.variableIds.size != operation.initializationEquations.size ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ParticlesCreate",
              "variable and initialization equation counts differ",
            )
        operation.variableIds.size > 64 ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ParticlesCreate",
              "${operation.variableIds.size} variables exceed the executable limit of 64",
            )
      }
    }
    if (operation is RcParticleLoop) {
      val definition = particleDefinitions[operation.id]
      when {
        definition == null ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ParticlesLoop",
              "particle system ${operation.id} is missing",
            )
        operation.updateEquations.size != definition.variableIds.size ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ParticlesLoop",
              "${operation.updateEquations.size} equations do not match " +
                "${definition.variableIds.size} variables",
            )
        definition.particleCount.toLong() * (1L + operation.updateEquations.sumOf { it.size }) >
          20_000L ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ParticlesLoop",
              "literal particle work exceeds 20000 units per frame",
            )
      }
    }
    if (operation is RcParticleCompare) {
      val definition = particleDefinitions[operation.id]
      when {
        definition == null ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ParticlesCompare",
              "particle system ${operation.id} is missing",
            )
        operation.firstEquations.size != definition.variableIds.size ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ParticlesCompare",
              "first result equation count does not match ${definition.variableIds.size} variables",
            )
        operation.secondEquations.isNotEmpty() &&
          operation.secondEquations.size != definition.variableIds.size ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ParticlesCompare",
              "second result equation count does not match ${definition.variableIds.size} variables",
            )
        particleCompareWork(operation, definition.particleCount) > 20_000L ->
          issues +=
            RcComposeSupportIssue(
              index,
              "ParticlesCompare",
              "literal particle work exceeds 20000 units per frame",
            )
      }
    }
    if (operation is RcGraphicsLayerModifier) {
      val supported =
        setOf(
          RcGraphicsLayerModifier.SCALE_X,
          RcGraphicsLayerModifier.SCALE_Y,
          RcGraphicsLayerModifier.ROTATION_X,
          RcGraphicsLayerModifier.ROTATION_Y,
          RcGraphicsLayerModifier.ROTATION_Z,
          RcGraphicsLayerModifier.TRANSFORM_ORIGIN_X,
          RcGraphicsLayerModifier.TRANSFORM_ORIGIN_Y,
          RcGraphicsLayerModifier.TRANSLATION_X,
          RcGraphicsLayerModifier.TRANSLATION_Y,
          RcGraphicsLayerModifier.SHADOW_ELEVATION,
          RcGraphicsLayerModifier.ALPHA,
          RcGraphicsLayerModifier.CAMERA_DISTANCE,
        )
      operation.attributes
        .firstOrNull { it.index !in supported || it !is RcGraphicsLayerAttribute.FloatValue }
        ?.let { attribute ->
          issues +=
            RcComposeSupportIssue(
              index,
              "GraphicsLayerModifier",
              "attribute ${attribute.index} is not implemented by the CMP graphics backend",
            )
        }
    }
    if (operation is RcIntegerExpression) {
      RcIntegerExpressionEvaluator.validationError(operation)?.let { detail ->
        issues += RcComposeSupportIssue(index, "IntegerExpression", detail)
      }
    }
    if (operation is RcWidthModifier && operation.type !in SUPPORTED_WIDTH_TYPES) {
      issues +=
        RcComposeSupportIssue(
          index,
          "WidthModifier",
          "dimension type ${operation.type} is not implemented",
        )
    }
    if (operation is RcHeightModifier && operation.type !in SUPPORTED_HEIGHT_TYPES) {
      issues +=
        RcComposeSupportIssue(
          index,
          "HeightModifier",
          "dimension type ${operation.type} is not implemented",
        )
    }
    if (operation is RcBoxLayout) {
      if (operation.horizontalPositioning !in 1..3) {
        issues +=
          RcComposeSupportIssue(
            index,
            "BoxLayout",
            "horizontal position ${operation.horizontalPositioning} is not implemented",
          )
      }
      if (operation.verticalPositioning !in setOf(2, 4, 5)) {
        issues +=
          RcComposeSupportIssue(
            index,
            "BoxLayout",
            "vertical position ${operation.verticalPositioning} is not implemented",
          )
      }
    }
    if (operation is RcFitBoxLayout) {
      if (operation.horizontalPositioning !in 1..3) {
        issues +=
          RcComposeSupportIssue(
            index,
            "FitBoxLayout",
            "horizontal position ${operation.horizontalPositioning} is not implemented",
          )
      }
      if (operation.verticalPositioning !in setOf(2, 4, 5)) {
        issues +=
          RcComposeSupportIssue(
            index,
            "FitBoxLayout",
            "vertical position ${operation.verticalPositioning} is not implemented",
          )
      }
    }
    if (operation is RcRowLayout) {
      if (operation.horizontalPositioning !in setOf(1, 2, 3, 6, 7, 8)) {
        issues +=
          RcComposeSupportIssue(
            index,
            "RowLayout",
            "horizontal position ${operation.horizontalPositioning} is not implemented",
          )
      }
      if (operation.verticalPositioning !in setOf(2, 4, 5)) {
        issues +=
          RcComposeSupportIssue(
            index,
            "RowLayout",
            "vertical position ${operation.verticalPositioning} is not implemented",
          )
      }
    }
    if (operation is RcColumnLayout) {
      if (operation.horizontalPositioning !in 1..3) {
        issues +=
          RcComposeSupportIssue(
            index,
            "ColumnLayout",
            "horizontal position ${operation.horizontalPositioning} is not implemented",
          )
      }
      if (operation.verticalPositioning !in setOf(2, 4, 5, 6, 7, 8)) {
        issues +=
          RcComposeSupportIssue(
            index,
            "ColumnLayout",
            "vertical position ${operation.verticalPositioning} is not implemented",
          )
      }
    }
    if (operation is RcFlowLayout) {
      if (operation.horizontalPositioning !in setOf(1, 2, 3, 6, 7, 8)) {
        issues +=
          RcComposeSupportIssue(
            index,
            "FlowLayout",
            "horizontal position ${operation.horizontalPositioning} is not implemented",
          )
      }
      if (operation.verticalPositioning !in setOf(2, 4, 5)) {
        issues +=
          RcComposeSupportIssue(
            index,
            "FlowLayout",
            "vertical position ${operation.verticalPositioning} is not implemented",
          )
      }
      if (operation.maxItemsInEachRow <= 0) {
        issues +=
          RcComposeSupportIssue(
            index,
            "FlowLayout",
            "maxItemsInEachRow ${operation.maxItemsInEachRow} must be positive",
          )
      }
      if (operation.maxLines <= 0) {
        issues +=
          RcComposeSupportIssue(
            index,
            "FlowLayout",
            "maxLines ${operation.maxLines} must be positive",
          )
      }
    }
    if (operation is RcCollapsibleRowLayout) {
      if (operation.horizontalPositioning !in setOf(1, 2, 3, 6, 7, 8)) {
        issues +=
          RcComposeSupportIssue(
            index,
            "CollapsibleRowLayout",
            "horizontal position ${operation.horizontalPositioning} is not implemented",
          )
      }
      if (operation.verticalPositioning !in setOf(2, 4, 5)) {
        issues +=
          RcComposeSupportIssue(
            index,
            "CollapsibleRowLayout",
            "vertical position ${operation.verticalPositioning} is not implemented",
          )
      }
    }
    if (operation is RcCollapsibleColumnLayout) {
      if (operation.horizontalPositioning !in 1..3) {
        issues +=
          RcComposeSupportIssue(
            index,
            "CollapsibleColumnLayout",
            "horizontal position ${operation.horizontalPositioning} is not implemented",
          )
      }
      if (operation.verticalPositioning !in setOf(2, 4, 5, 6, 7, 8)) {
        issues +=
          RcComposeSupportIssue(
            index,
            "CollapsibleColumnLayout",
            "vertical position ${operation.verticalPositioning} is not implemented",
          )
      }
    }
    if (
      operation is RcCollapsiblePriorityModifier &&
        operation.orientation !in
          setOf(RcCollapsiblePriorityModifier.HORIZONTAL, RcCollapsiblePriorityModifier.VERTICAL)
    ) {
      issues +=
        RcComposeSupportIssue(
          index,
          "CollapsiblePriorityModifierOperation",
          "orientation ${operation.orientation} is not implemented",
        )
    }
    if (operation is RcLayoutCompute) {
      if (operation.type !in setOf(RcLayoutCompute.MEASURE, RcLayoutCompute.POSITION)) {
        issues +=
          RcComposeSupportIssue(
            index,
            "LayoutComputeOperation",
            "type ${operation.type} is not implemented",
          )
      }
      if (operation.animateChanges) {
        issues +=
          RcComposeSupportIssue(
            index,
            "LayoutComputeOperation",
            "animated measure transitions are not implemented",
          )
      }
      val bounds = dynamicLists[operation.boundsId]
      when {
        bounds == null ->
          issues +=
            RcComposeSupportIssue(
              index,
              "LayoutComputeOperation",
              "bounds id ${operation.boundsId} is not a dynamic float list",
            )
        bounds.length.referencedId == null && bounds.length.value.toInt() != 6 ->
          issues +=
            RcComposeSupportIssue(
              index,
              "LayoutComputeOperation",
              "bounds list ${operation.boundsId} has length ${bounds.length.value.toInt()}, expected 6",
            )
      }
    }
    if (operation is RcAccessibilitySemantics) {
      if (operation.role !in -1..RcAccessibilitySemantics.ROLE_UNKNOWN) {
        issues +=
          RcComposeSupportIssue(index, "CoreSemantics", "role ${operation.role} is not implemented")
      }
      if (
        operation.mode !in RcAccessibilitySemantics.MODE_SET..RcAccessibilitySemantics.MODE_MERGE
      ) {
        issues +=
          RcComposeSupportIssue(index, "CoreSemantics", "mode ${operation.mode} is not implemented")
      }
      listOf(
          "content description" to operation.contentDescriptionId,
          "text" to operation.textId,
          "state description" to operation.stateDescriptionId,
        )
        .filter { (_, id) -> id != 0 && id !in textIds }
        .forEach { (name, id) ->
          issues +=
            RcComposeSupportIssue(index, "CoreSemantics", "$name text id $id is not declared")
        }
    }
    if (
      operation is RcScrollModifier &&
        operation.direction !in RcScrollModifier.VERTICAL..RcScrollModifier.HORIZONTAL
    ) {
      issues +=
        RcComposeSupportIssue(
          index,
          "ScrollModifierOperation",
          "direction ${operation.direction} is not implemented",
        )
    }
    if (operation is RcMarqueeModifier) {
      listOf(
          "repeat delay" to operation.repeatDelayMillis.value,
          "initial delay" to operation.initialDelayMillis.value,
          "spacing" to operation.spacing.value,
          "velocity" to operation.velocity.value,
        )
        .filter { (_, value) -> !value.isFinite() }
        .forEach { (name, _) ->
          issues += RcComposeSupportIssue(index, "MarqueeModifierOperation", "$name must be finite")
        }
      if (operation.velocity.value <= 0f) {
        issues +=
          RcComposeSupportIssue(
            index,
            "MarqueeModifierOperation",
            "velocity must be greater than zero",
          )
      }
    }
    if (operation is RcTouchExpression) {
      when {
        operation.touchEffects != 0 ->
          issues +=
            RcComposeSupportIssue(
              index,
              "TouchExpression",
              "touch effects ${operation.touchEffects} are not implemented",
            )
        operation.easingSpec.isNotEmpty() ->
          issues +=
            RcComposeSupportIssue(
              index,
              "TouchExpression",
              "custom velocity easing is not implemented",
            )
        operation.stopMode !in
          RcTouchExpression.STOP_GENTLY..RcTouchExpression.STOP_NOTCHES_SINGLE_EVEN ->
          issues +=
            RcComposeSupportIssue(
              index,
              "TouchExpression",
              "stop mode ${operation.stopMode} is not implemented",
            )
        operation.stopMode in
          setOf(RcTouchExpression.STOP_NOTCHES_EVEN, RcTouchExpression.STOP_NOTCHES_SINGLE_EVEN) &&
          operation.stopSpec.isEmpty() ->
          issues += RcComposeSupportIssue(index, "TouchExpression", "even notches require a count")
      }
    }
    if (operation is RcValueStringChangeAction && operation.valueId !in textIds) {
      issues +=
        RcComposeSupportIssue(
          index,
          "ValueStringChangeActionOperation",
          "value text id ${operation.valueId} is not declared",
        )
    }
    if (operation is RcHostNamedAction && operation.nameTextId !in textIds) {
      issues +=
        RcComposeSupportIssue(
          index,
          "HostNamedActionOperation",
          "name text id ${operation.nameTextId} is not declared",
        )
    }
    if (operation is RcHostMetadataAction && operation.metadataTextId !in textIds) {
      issues +=
        RcComposeSupportIssue(
          index,
          "HostActionMetadataOperation",
          "metadata text id ${operation.metadataTextId} is not declared",
        )
    }
    if (
      operation is RcValueIntegerExpressionChangeAction &&
        operation.expressionId.toInt() !in integerExpressionIds
    ) {
      issues +=
        RcComposeSupportIssue(
          index,
          "ValueIntegerExpressionChangeActionOperation",
          "integer expression id ${operation.expressionId} is not declared",
        )
    }
    if (
      operation is RcValueFloatExpressionChangeAction &&
        operation.expressionId !in floatExpressionIds
    ) {
      issues +=
        RcComposeSupportIssue(
          index,
          "ValueFloatExpressionChangeActionOperation",
          "float expression id ${operation.expressionId} is not declared",
        )
    }
  }
  val functions = operations.filterIsInstance<RcFloatFunctionDefine>().associateBy { it.id }
  operations.forEachIndexed { index, operation ->
    if (operation is RcFloatFunctionCall) {
      val definition = functions[operation.functionId]
      when {
        definition == null ->
          issues +=
            RcComposeSupportIssue(
              index,
              "FunctionCall",
              "function ${operation.functionId} is not defined",
            )
        operation.arguments.size > definition.parameterIds.size ->
          issues +=
            RcComposeSupportIssue(
              index,
              "FunctionCall",
              "${operation.arguments.size} arguments exceed ${definition.parameterIds.size} parameters",
            )
      }
    }
  }
  linkResult.fold(
    onSuccess = { linked ->
      invalidLayoutComputeChild(linked.operations)?.let { operation ->
        issues +=
          RcComposeSupportIssue(
            -1,
            "LayoutComputeOperation",
            "nested opcode ${operation.opcode} cannot execute during layout",
          )
      }
      invalidActionChild(linked.operations, RcOpcodes.MODIFIER_CLICK)?.let { operation ->
        issues +=
          RcComposeSupportIssue(
            -1,
            "ClickModifierOperation",
            "nested opcode ${operation.opcode} is not a click action",
          )
      }
      invalidActionChild(linked.operations, RcOpcodes.MODIFIER_MULTI_CLICK)?.let { operation ->
        issues +=
          RcComposeSupportIssue(
            -1,
            "MultiClickModifier",
            "nested opcode ${operation.opcode} is not an action",
          )
      }
      listOf(
          RcOpcodes.MODIFIER_TOUCH_DOWN to "TouchDownModifierOperation",
          RcOpcodes.MODIFIER_TOUCH_UP to "TouchUpModifierOperation",
          RcOpcodes.MODIFIER_TOUCH_CANCEL to "TouchCancelModifierOperation",
        )
        .forEach { (opcode, name) ->
          invalidActionChild(linked.operations, opcode)?.let { operation ->
            issues +=
              RcComposeSupportIssue(
                -1,
                name,
                "nested opcode ${operation.opcode} is not an action",
              )
          }
        }
      invalidActionChild(linked.operations, RcOpcodes.RUN_ACTION)?.let { operation ->
        issues +=
          RcComposeSupportIssue(
            -1,
            "RunActionOperation",
            "nested opcode ${operation.opcode} is not an action",
          )
      }
      invalidScrollChild(linked.operations)?.let { detail ->
        issues += RcComposeSupportIssue(-1, "ScrollModifierOperation", detail)
      }
      val layoutResult = runCatching { RcLayoutTree.build(linked) }
      layoutResult.exceptionOrNull()?.let {
        issues += RcComposeSupportIssue(-1, "LayoutStructure", it.message ?: "invalid")
      }
      layoutResult.getOrNull()?.let { layout ->
        if (hasUndispatchedAccessibilityClick(layout)) {
          issues +=
            RcComposeSupportIssue(
              -1,
              "CoreSemantics",
              "clickable semantics requires a ClickModifierOperation on the same component",
            )
        }
      }
      if (hasInvalidDrawContent(linked.operations)) {
        issues +=
          RcComposeSupportIssue(
            -1,
            "DrawContent",
            "operation must be inside CanvasOperations attached to a layout component",
          )
      }
    },
    onFailure = {
      issues += RcComposeSupportIssue(-1, "ContainerStructure", it.message ?: "invalid")
    },
  )
  issues += offscreenAllocationIssues(executed, operations)
  return RcComposeSupportReport(issues)
}

/**
 * Flattens the linked tree back to the order the renderer executes it in: a container's own
 * operation, then its children.
 *
 * The order is what the shader walk needs — which declaration is live at a paint is a fact about
 * position, not about the document as a set.
 */
/**
 * One operation the renderer can reach, and whether reaching it is CERTAIN.
 *
 * The flag is what keeps the shader walk honest across control flow. A declaration inside a
 * conditional may or may not run, so it cannot be treated as having replaced the one before it —
 * see [installedShaderDeclarations].
 */
private class RcExecutedOperation(val operation: RcOperation, val certain: Boolean)

/**
 * The operations the renderer can reach, in execution order, from the linked tree.
 *
 * Two things this is not: it is not `document.operations`, which holds definitions the renderer
 * never runs; and it is not a plain walk of the linked tree either, because two node kinds move
 * their bodies:
 * - a `FloatFunctionDefine` is registered and stepped over where it is defined, and its children
 *   are drawn from each `FloatFunctionCall` instead — so the body is expanded at the calls, once
 *   per call, and not at the definition;
 * - a call already on the stack is skipped, matching the renderer's own `functions.executing`
 *   guard, which rejects recursion rather than following it.
 *
 * Certainty is granted conservatively: only the top level and the handful of container kinds that
 * always draw their children carry it forward. Anything else — a conditional, a loop, an impulse,
 * an action, a particle body, a called function — is marked uncertain, which can only widen what
 * the shader walk considers live. Missing a container from [CERTAIN_CHILD_OPCODES] costs precision;
 * wrongly adding one would cost soundness, so the set is short on purpose.
 */
private fun executedOperations(nodes: List<RcLinkedNode>): List<RcExecutedOperation> {
  val definitions = mutableMapOf<Int, RcLinkedNode.Container>()
  fun collectDefinitions(list: List<RcLinkedNode>) {
    list.forEach { node ->
      if (node is RcLinkedNode.Container) {
        (node.operation as? RcFloatFunctionDefine)?.let { definitions[it.id] = node }
        collectDefinitions(node.children)
      }
    }
  }
  collectDefinitions(nodes)

  val flat = mutableListOf<RcExecutedOperation>()
  val calling = mutableSetOf<Int>()
  fun visit(node: RcLinkedNode, certain: Boolean) {
    val operation = node.operation()
    flat += RcExecutedOperation(operation, certain)
    if (operation is RcFloatFunctionCall) {
      val definition = definitions[operation.functionId] ?: return
      if (!calling.add(operation.functionId)) return
      definition.children.forEach { visit(it, certain = false) }
      calling.remove(operation.functionId)
      return
    }
    if (node is RcLinkedNode.Container && operation !is RcFloatFunctionDefine) {
      val childrenCertain = certain && operation.opcode in CERTAIN_CHILD_OPCODES
      node.children.forEach { visit(it, childrenCertain) }
    }
  }
  nodes.forEach { visit(it, certain = true) }
  return flat
}

/** Containers whose children draw whenever the container itself does. Deliberately short. */
private val CERTAIN_CHILD_OPCODES =
  setOf(
    RcOpcodes.CANVAS_OPERATIONS,
    RcOpcodes.COMPONENT_START,
    RcOpcodes.LAYOUT_ROOT,
    RcOpcodes.LAYOUT_CONTENT,
    RcOpcodes.LAYOUT_BOX,
    RcOpcodes.LAYOUT_ROW,
    RcOpcodes.LAYOUT_COLUMN,
  )

/**
 * Which shader DECLARATIONS a paint can install, walked in execution order.
 *
 * Not a set of ids: the renderer keeps one live shader per id and overwrites it as it walks
 * (`functions.shaders[operation.shaderId] = operation`), so a document may declare id 7 invalid,
 * declare it again valid, and paint with 7 — and draw perfectly well. Recording the id would
 * condemn the superseded declaration and refuse that document.
 *
 * But a redeclaration only *supersedes* if it is certain to run. One inside a conditional may be
 * skipped, leaving the earlier declaration live at the paint, so an uncertain declaration is added
 * to what might be installed rather than replacing it. This keeps the answer sound where the
 * previous, position-only version was not: a valid shader declared inside a conditional does not
 * excuse an invalid one declared before it.
 *
 * Where a paint reaches an id with nothing recorded, `buildRuntimeShader` falls back to the LAST
 * declaration of that id anywhere in the wire document, whatever the order — so a paint preceding
 * its own shader still installs one, and that fallback is mirrored here.
 *
 * Reusing `paintIssue` rather than writing a second walk is the point: the argument-length table it
 * carries is the only thing that knows where a shader id sits in the word stream, and two copies of
 * it would drift. A malformed paint stops its own walk early, so what it references beyond the
 * fault is unknown — that paint is already reported as an issue of its own, and a document with one
 * is refused before any of this matters.
 *
 * Declarations compare by value, so a document that declares the same shader twice and paints one
 * of them marks both. That over-reports the index and not the verdict: the copy the renderer does
 * install is unbuildable, so refusing the document is right either way.
 */
private fun installedShaderDeclarations(
  executed: List<RcExecutedOperation>,
  declared: List<RcOperation>,
  shaderIds: Set<Int>,
): Set<RcShaderData> {
  val fallback = declared.filterIsInstance<RcShaderData>().associateBy { it.shaderId }
  val live = mutableMapOf<Int, MutableSet<RcShaderData>>()
  val installed = mutableSetOf<RcShaderData>()
  executed.forEach { step ->
    when (val operation = step.operation) {
      is RcShaderData -> {
        val forId = live.getOrPut(operation.shaderId) { mutableSetOf() }
        if (step.certain) forId.clear()
        forId += operation
      }
      is RcPaintData -> {
        val referenced = mutableSetOf<Int>()
        paintIssue(operation, shaderIds, referenced)
        referenced.forEach { id ->
          val candidates = live[id]?.takeIf { it.isNotEmpty() }
          if (candidates != null) installed += candidates else fallback[id]?.let(installed::add)
        }
      }
      else -> Unit
    }
  }
  return installed
}

/**
 * The two ceilings `RcOffscreenTargetPool` enforces while drawing, asked before the draw instead.
 *
 * The pool `require`s both, so exceeding either throws partway through the draw pass — the enum's
 * own definition of [RcComposeSupportSeverity.BLOCKING]. Nothing here reported them, so a document
 * with a sixty-fifth target passed `requireRenderable` and then died on the frame; per-operation
 * checking cannot see it either, because both limits are properties of the whole document.
 *
 * Counted the way the pool allocates, which is what makes the preflight agree with the draw rather
 * than merely resemble it:
 * - one target per DISTINCT `bitmapId`, allocated on that id's first `DrawToBitmap` and reused
 *   afterwards, so repeated draws to one target cost one allocation;
 * - sized by [offscreenTargetSize], because the pool charges `source.width * source.height` of the
 *   DECODED image rather than the declared fields;
 * - `bitmapId == 0` means the stage rather than a target, and an undeclared id is already reported
 *   next to the operation — neither reaches an allocation, so neither is counted here.
 */
private fun offscreenAllocationIssues(
  executed: List<RcExecutedOperation>,
  declarations: List<RcOperation>,
): List<RcComposeSupportIssue> {
  val limits = RcOffscreenTargetLimits()
  // Resources come from the WIRE stream, uses from the linked one, because that is the split the
  // renderer itself makes: `decodeInlineImagesUncounted` walks `document.operations`, so a bitmap
  // declared inside a `ReferencedOperations` block nothing includes is still decoded and still
  // available to an active `DrawToBitmap` elsewhere. Taking declarations from the linked stream
  // dropped those ids and, with them, the targets that use them.
  // The decoder keeps the last declaration of an id that DECODES, not the last one written:
  // `decodeInlineImagesUncounted` drops a failed decode before `toMap()` chooses, so a corrupt
  // redeclaration leaves the earlier good image in place. `associateBy` would have sized the
  // target from the corrupt one.
  val declared =
    declarations
      .filterIsInstance<RcBitmapData>()
      .groupBy { it.imageId }
      .mapValues { (_, all) -> all.lastOrNull { it.isDecodable() } ?: all.last() }
  val targets =
    executed
      .map { it.operation }
      .filterIsInstance<RcDrawToBitmap>()
      .map { it.bitmapId }
      .filter { it != 0 && it in declared }
      .distinct()
  if (targets.isEmpty()) return emptyList()

  val issues = mutableListOf<RcComposeSupportIssue>()
  if (targets.size > limits.maxTargets) {
    issues +=
      RcComposeSupportIssue(
        -1,
        "DrawToBitmap",
        "document declares ${targets.size} mutable targets; " +
          "the renderer allocates at most ${limits.maxTargets}",
      )
  }
  // Saturating, because a crafted IHDR can advertise `Int.MAX_VALUE` in both axes: each product
  // fits in a Long while three of them do not, and a wrapped total compares BELOW the ceiling and
  // reports a document renderable that the pool refuses on its first target.
  var pixels = 0L
  for (id in targets) {
    val (width, height) = offscreenTargetSize(declared.getValue(id))
    pixels += width.toLong() * height.toLong()
    if (pixels < 0L || pixels > limits.maxPixels) {
      pixels = pixels.coerceAtLeast(limits.maxPixels + 1)
      break
    }
  }
  if (pixels > limits.maxPixels) {
    issues +=
      RcComposeSupportIssue(
        -1,
        "DrawToBitmap",
        "mutable targets total $pixels pixels; the renderer allocates at most ${limits.maxPixels}",
      )
  }
  return issues
}

/**
 * The size the pool will charge for a target, which is the size of the image it DECODES.
 *
 * `RcBitmapData.width`/`height` are metadata, and for an encoded payload nothing forces them to
 * agree with the pixels inside it: `decodeInlineImage` builds the `ImageBitmap` from the bytes, and
 * `RcOffscreenTargetPool.canvasFor` then charges what that image measures. A payload larger than
 * its declared fields would otherwise pass this preflight and blow the ceiling mid-draw — the exact
 * failure the audit exists to catch.
 *
 * A PNG says its own size in the IHDR chunk, which is a fixed offset into the header, so no decoder
 * is needed to read it — this stays pure common code. Anything unreadable falls back to the
 * declared fields: the raw types are sized by them (and `decodeInlineImage` `require`s the payload
 * matches), and a PNG whose header will not parse is one Skia will not decode either, which is a
 * separate hole and not this one.
 */
private fun offscreenTargetSize(bitmap: RcBitmapData): Pair<Int, Int> {
  val encodedAsPng =
    bitmap.type == RcBitmapData.TYPE_PNG_8888 ||
      bitmap.type == RcBitmapData.TYPE_PNG ||
      bitmap.type == RcBitmapData.TYPE_PNG_ALPHA_8
  return (if (encodedAsPng) pngHeaderSize(bitmap.data) else null) ?: (bitmap.width to bitmap.height)
}

/**
 * Whether `decodeInlineImage` could plausibly build this bitmap, judged without a decoder.
 *
 * Only as sharp as the metadata allows: a readable PNG header, or a raw payload long enough for the
 * dimensions it declares — the same `require` the decoder makes. It exists to choose between
 * DUPLICATE declarations of one id the way the decoder does, not to rule on decodability in
 * general.
 */
private fun RcBitmapData.isDecodable(): Boolean =
  when (type) {
    RcBitmapData.TYPE_PNG_8888,
    RcBitmapData.TYPE_PNG,
    RcBitmapData.TYPE_PNG_ALPHA_8 -> pngHeaderSize(data) != null
    RcBitmapData.TYPE_RAW8888 -> data.size >= width.toLong() * height.toLong() * 4L
    RcBitmapData.TYPE_RAW8 -> data.size >= width.toLong() * height.toLong()
    else -> false
  }

/** Width and height from a PNG's IHDR, or null if [data] is not a PNG header this can read. */
private fun pngHeaderSize(data: ByteArray): Pair<Int, Int>? {
  // 8-byte signature, a 4-byte chunk length, the 4-byte type "IHDR", then width and height as
  // big-endian 32-bit integers.
  if (data.size < 24) return null
  if (!PNG_SIGNATURE.indices.all { data[it] == PNG_SIGNATURE[it] }) return null
  if (!IHDR.indices.all { data[12 + it] == IHDR[it] }) return null
  val width = bigEndianInt(data, 16)
  val height = bigEndianInt(data, 20)
  return if (width > 0 && height > 0) width to height else null
}

private fun bigEndianInt(data: ByteArray, at: Int): Int =
  ((data[at].toInt() and 0xFF) shl 24) or
    ((data[at + 1].toInt() and 0xFF) shl 16) or
    ((data[at + 2].toInt() and 0xFF) shl 8) or
    (data[at + 3].toInt() and 0xFF)

private val PNG_SIGNATURE =
  byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) // \x89 P N G \r \n \x1a \n

private val IHDR = byteArrayOf(73, 72, 68, 82) // I H D R

private fun invalidScrollChild(nodes: List<RcLinkedNode>, insideScroll: Boolean = false): String? {
  nodes.forEach { node ->
    if (node is RcLinkedNode.Operation && node.operation is RcTouchExpression && !insideScroll) {
      return "TouchExpression is only implemented as a direct scroll child"
    }
    if (node is RcLinkedNode.Container && node.operation is RcScrollModifier) {
      val children = node.children.map { it.operation() }
      if (children.size != 1 || children.singleOrNull() !is RcTouchExpression) {
        return "container requires exactly one TouchExpression child"
      }
      invalidScrollChild(node.children, insideScroll = true)?.let {
        return it
      }
      return@forEach
    }
    if (node is RcLinkedNode.Container)
      invalidScrollChild(node.children, insideScroll)?.let {
        return it
      }
  }
  return null
}

private fun invalidActionChild(
  nodes: List<RcLinkedNode>,
  containerOpcode: Int,
): ee.schimke.composeai.rcplayer.protocol.RcOperation? {
  nodes.forEach { node ->
    if (node is RcLinkedNode.Container && node.operation.opcode == containerOpcode) {
      node.children.forEach { child ->
        val operation = (child as? RcLinkedNode.Operation)?.operation ?: return child.operation()
        if (
          operation !is RcHostAction &&
            operation !is RcHapticFeedback &&
            operation !is RcPlaySound &&
            operation !is RcHostMetadataAction &&
            operation !is RcHostNamedAction &&
            operation !is ee.schimke.composeai.rcplayer.protocol.RcTextData &&
            operation !is RcValueIntegerChangeAction &&
            operation !is RcValueIntegerExpressionChangeAction &&
            operation !is RcValueStringChangeAction &&
            operation !is RcValueFloatChangeAction &&
            operation !is RcValueFloatExpressionChangeAction
        ) {
          return operation
        }
      }
    }
    if (node is RcLinkedNode.Container)
      invalidActionChild(node.children, containerOpcode)?.let {
        return it
      }
  }
  return null
}

private fun hasUndispatchedAccessibilityClick(
  node: ee.schimke.composeai.rcplayer.runtime.RcLayoutNode
): Boolean {
  if (node.modifiers.accessibility.any { it.clickable } && node.modifiers.clicks.isEmpty())
    return true
  val children =
    when (node) {
      is ee.schimke.composeai.rcplayer.runtime.RcLayoutNode.Root -> node.children
      is ee.schimke.composeai.rcplayer.runtime.RcLayoutNode.Content -> node.children
      is ee.schimke.composeai.rcplayer.runtime.RcLayoutNode.Canvas -> listOfNotNull(node.content)
      is ee.schimke.composeai.rcplayer.runtime.RcLayoutNode.Box -> listOf(node.content)
      is ee.schimke.composeai.rcplayer.runtime.RcLayoutNode.Row -> listOf(node.content)
      is ee.schimke.composeai.rcplayer.runtime.RcLayoutNode.Column -> listOf(node.content)
      is ee.schimke.composeai.rcplayer.runtime.RcLayoutNode.Flow -> listOf(node.content)
      is ee.schimke.composeai.rcplayer.runtime.RcLayoutNode.State -> listOf(node.content)
      is ee.schimke.composeai.rcplayer.runtime.RcLayoutNode.CollapsibleRow -> listOf(node.content)
      is ee.schimke.composeai.rcplayer.runtime.RcLayoutNode.CollapsibleColumn ->
        listOf(node.content)
      is ee.schimke.composeai.rcplayer.runtime.RcLayoutNode.FitBox -> listOf(node.content)
      else -> emptyList()
    }
  return children.any(::hasUndispatchedAccessibilityClick)
}

private fun invalidLayoutComputeChild(
  nodes: List<RcLinkedNode>
): ee.schimke.composeai.rcplayer.protocol.RcOperation? {
  nodes.forEach { node ->
    if (node is RcLinkedNode.Container && node.operation is RcLayoutCompute) {
      node.children.forEach { child ->
        val operation = (child as? RcLinkedNode.Operation)?.operation ?: return child.operation()
        if (!operation.isLayoutComputeExecutable()) return operation
      }
    }
    if (node is RcLinkedNode.Container)
      invalidLayoutComputeChild(node.children)?.let {
        return it
      }
  }
  return null
}

private fun RcLinkedNode.operation(): ee.schimke.composeai.rcplayer.protocol.RcOperation =
  when (this) {
    is RcLinkedNode.Operation -> operation
    is RcLinkedNode.Container -> operation
  }

private fun textStyleIssue(
  properties: List<RcTextStyleProperty>,
  colorIds: Set<Int>,
  texts: Map<Int, String>,
  fontIds: Set<Int>,
  availableFontFamilies: Set<String>,
): String? {
  fun int(id: Int, default: Int): Int =
    properties.filterIsInstance<RcTextStyleProperty.IntValue>().lastOrNull { it.id == id }?.value
      ?: default
  fun float(id: Int, default: Float): ee.schimke.composeai.rcplayer.protocol.RcFloatWord =
    properties.filterIsInstance<RcTextStyleProperty.FloatValue>().lastOrNull { it.id == id }?.value
      ?: ee.schimke.composeai.rcplayer.protocol.RcFloatWord.literal(default)
  fun bool(id: Int, default: Boolean): Boolean =
    properties
      .filterIsInstance<RcTextStyleProperty.BooleanValue>()
      .lastOrNull { it.id == id }
      ?.value ?: default

  val colorId = int(4, -1)
  if (colorId != -1 && colorId !in colorIds) return "dynamic color id $colorId is not declared"
  val fontStyle = int(6, 0)
  if (fontStyle !in 0..3) return "font style $fontStyle is not implemented"
  val fontFamily = int(8, -1)
  fontFamilyIssue(fontFamily, texts, fontIds, availableFontFamilies)?.let {
    return it
  }
  val alignment = int(9, RcTextLayout.ALIGN_LEFT)
  if (alignment !in RcTextLayout.ALIGN_LEFT..RcTextLayout.ALIGN_END) {
    return "text alignment $alignment is not implemented"
  }
  val overflow = int(10, RcTextLayout.OVERFLOW_CLIP)
  if (overflow !in RcTextLayout.OVERFLOW_CLIP..RcTextLayout.OVERFLOW_MIDDLE_ELLIPSIS) {
    return "overflow $overflow is not implemented"
  }
  val maxLines = int(11, Int.MAX_VALUE)
  if (maxLines <= 0) return "maxLines must be positive"
  val breakStrategy = int(15, 0)
  if (breakStrategy !in 0..2) return "line break strategy $breakStrategy is not implemented"
  val hyphenation = int(16, 0)
  if (hyphenation !in 0..1) return "hyphenation frequency $hyphenation is not implemented"
  val justification = int(17, 0)
  if (justification !in 0..1) return "justification mode $justification is not implemented"
  val axes =
    properties.filterIsInstance<RcTextStyleProperty.IntArrayValue>().lastOrNull { it.id == 20 }
  val axisValues =
    properties.filterIsInstance<RcTextStyleProperty.FloatArrayValue>().lastOrNull { it.id == 21 }
  // Font-variation axes ARE implemented for layout text — the player resolves properties 20/21 into
  // an `RcFontVariations` and instances the host's face at them (see `fontVariationSettings`).
  // What is still a hard error is a *malformed* pair of arrays: tags and values are positional, so
  // unequal lengths mean the document cannot say which value belongs to which axis, and rendering
  // it would apply a silently wrong instance rather than a missing one.
  //
  // Axes on a family the host doesn't supply as bytes (a generic, or an inline `FontData`) are
  // dropped at render rather than rejected here: the text still draws in the right family, one
  // instance off. That is a substitution the audit records, not a document this lane cannot read.
  if (!axes?.values.isNullOrEmpty() || !axisValues?.values.isNullOrEmpty()) {
    if (axes?.values?.size != axisValues?.values?.size)
      return "font axis arrays have different sizes"
  }
  val flags = int(23, 0)
  if (flags != 0) return "flags $flags are not implemented"
  return null
}

private fun fontFamilyIssue(
  fontFamilyId: Int,
  texts: Map<Int, String>,
  embeddedFontIds: Set<Int>,
  availableFontFamilies: Set<String>,
): String? {
  if (fontFamilyId == -1) return null
  val family = texts[fontFamilyId] ?: return "font family name id $fontFamilyId is not declared"
  val normalized = family.lowercase().removePrefix("google:")
  val available = availableFontFamilies.mapTo(mutableSetOf()) { it.lowercase() }
  if (
    normalized !in setOf("default", "sans-serif", "serif", "monospace") &&
      normalized !in available &&
      fontFamilyId !in embeddedFontIds
  ) {
    return "custom font family $family ($fontFamilyId) has no DataFont"
  }
  return null
}

private fun hasInvalidDrawContent(
  nodes: List<RcLinkedNode>,
  hasLayoutComponent: Boolean = false,
  drawContentAvailable: Boolean = false,
): Boolean = nodes.any { node ->
  when (node) {
    is RcLinkedNode.Operation ->
      node.operation is RcNoArg &&
        node.operation.opcode == RcOpcodes.DRAW_CONTENT &&
        !drawContentAvailable
    is RcLinkedNode.Container -> {
      val component =
        hasLayoutComponent ||
          node.operation.opcode in
            setOf(
              RcOpcodes.LAYOUT_ROOT,
              RcOpcodes.LAYOUT_BOX,
              RcOpcodes.LAYOUT_ROW,
              RcOpcodes.LAYOUT_COLUMN,
              RcOpcodes.LAYOUT_FLOW,
              RcOpcodes.LAYOUT_COLLAPSIBLE_ROW,
              RcOpcodes.LAYOUT_COLLAPSIBLE_COLUMN,
              RcOpcodes.LAYOUT_CANVAS,
              RcOpcodes.LAYOUT_FIT_BOX,
            )
      val available =
        if (node.operation.opcode == RcOpcodes.CANVAS_OPERATIONS) component
        else drawContentAvailable
      hasInvalidDrawContent(node.children, component, available)
    }
  }
}

private fun runtimeShaderSourceIsSupported(source: String): Boolean = runCatching {
  RuntimeEffect.makeForShader(source).close()
}
  .isSuccess

private fun paintIssue(
  paint: RcPaintData,
  shaderIds: Set<Int> = emptySet(),
  /** Filled with each DECLARED shader id this paint installs — see [referencedShaderIds]. */
  referenced: MutableSet<Int>? = null,
): String? {
  var index = 0
  while (index < paint.words.size) {
    val command = paint.words[index++]
    val type = command and 0xffff
    if (type == PAINT_FONT_AXIS && command ushr 16 !in 0..8) {
      return "font axis count ${command ushr 16} is invalid"
    }
    val gradientWords =
      if (type == PAINT_GRADIENT) {
        if (command ushr 16 !in 0..2) return "gradient type ${command ushr 16} is not implemented"
        if (index >= paint.words.size) return "paint command $type is truncated"
        val colorCount = paint.words[index] and 0xff
        if (colorCount !in 1..16) return "gradient color count $colorCount is invalid"
        val stopCountIndex = index + 1 + colorCount
        if (stopCountIndex >= paint.words.size) return "paint command $type is truncated"
        val stopCount = paint.words[stopCountIndex]
        if (stopCount != 0 && stopCount != colorCount) {
          return "gradient stop count $stopCount does not match $colorCount colors"
        }
        1 +
          colorCount +
          1 +
          stopCount +
          when (command ushr 16) {
            0 -> 5
            1 -> 4
            else -> 2
          }
      } else {
        null
      }
    val argumentWords =
      gradientWords
        ?: when (type) {
          PAINT_TEXT_SIZE,
          PAINT_COLOR,
          PAINT_STROKE_WIDTH,
          PAINT_ALPHA,
          PAINT_COLOR_ID,
          PAINT_TYPEFACE,
          PAINT_SHADER,
          PAINT_COLOR_FILTER,
          PAINT_COLOR_FILTER_ID -> 1
          PAINT_STROKE_CAP,
          PAINT_STYLE,
          PAINT_STROKE_JOIN,
          PAINT_BLEND_MODE,
          PAINT_CLEAR_COLOR_FILTER,
          // Sampling hints, value packed in the command's high bits, no operand word. Compose's
          // DrawScope owns anti-aliasing and bitmap filtering, so these are consumed and ignored --
          // the same treatment the embedded player gives them.
          PAINT_IMAGE_FILTER_QUALITY,
          PAINT_ANTI_ALIAS,
          PAINT_FILTER_BITMAP -> 0
          PAINT_SHADER_MATRIX -> 1
          PAINT_TEXTURE -> 3
          PAINT_FONT_AXIS -> (command ushr 16) * 2
          else -> return "paint command $type is not implemented"
        }
    if (index + argumentWords > paint.words.size) return "paint command $type is truncated"
    if (type == PAINT_STYLE && command ushr 16 !in 0..1) {
      return "paint style ${command ushr 16} is not implemented"
    }
    if (type == PAINT_BLEND_MODE && command ushr 16 !in 0..28) {
      return "blend mode ${command ushr 16} is not implemented"
    }
    if (type in setOf(PAINT_COLOR_FILTER, PAINT_COLOR_FILTER_ID) && command ushr 16 !in 0..28) {
      return "color filter mode ${command ushr 16} is not implemented"
    }
    if (type == PAINT_TYPEFACE && paint.words[index] !in 0..3) {
      return "font id ${paint.words[index]} is not implemented"
    }
    if (type == PAINT_SHADER && paint.words[index] != 0) {
      if (paint.words[index] !in shaderIds) return "shader id ${paint.words[index]} is not declared"
      referenced?.add(paint.words[index])
    }
    if (type == PAINT_FONT_AXIS) {
      for (axisIndex in 0 until (command ushr 16)) {
        val tag = paint.words[index + axisIndex * 2]
        if (tag !in SUPPORTED_FONT_AXES) return "font axis ${fontAxisName(tag)} is not implemented"
      }
    }
    index += argumentWords
  }
  return null
}

/**
 * The dimension types `applyWidth`/`applyHeight` turn into a Compose size.
 *
 * [RcDimensionType.WRAP] is in the list even though neither applies a modifier for it: wrapping the
 * content *is* Compose's default sizing, so the absence of a modifier is the implementation, and it
 * is the same thing AndroidX's own embedded player does (`Type.WRAP -> this // Default`). Reporting
 * it as unimplemented refused documents both players render identically — every `RemoteButton`
 * sizes its label row that way (compose-ai-tools#4166).
 *
 * [RcDimensionType.INTRINSIC_MIN] and [RcDimensionType.INTRINSIC_MAX] stay out: those need a real
 * intrinsic measurement, and dropping them silently sizes the component from its parent instead.
 */
private val SUPPORTED_WIDTH_TYPES =
  setOf(
    RcDimensionType.EXACT,
    RcDimensionType.FILL,
    RcDimensionType.WRAP,
    RcDimensionType.WEIGHT,
    RcDimensionType.EXACT_DP,
    RcDimensionType.FILL_PARENT_MAX_WIDTH,
  )

/** [SUPPORTED_WIDTH_TYPES]'s vertical twin — same reasoning, `FILL_PARENT_MAX_HEIGHT` instead. */
private val SUPPORTED_HEIGHT_TYPES =
  setOf(
    RcDimensionType.EXACT,
    RcDimensionType.FILL,
    RcDimensionType.WRAP,
    RcDimensionType.WEIGHT,
    RcDimensionType.EXACT_DP,
    RcDimensionType.FILL_PARENT_MAX_HEIGHT,
  )

private const val PAINT_TEXT_SIZE = 1
private const val PAINT_COLOR = 4
private const val PAINT_STROKE_WIDTH = 5
private const val PAINT_STROKE_CAP = 7
private const val PAINT_STYLE = 8
private const val PAINT_SHADER = 9
private const val PAINT_IMAGE_FILTER_QUALITY = 10
private const val PAINT_GRADIENT = 11
private const val PAINT_ALPHA = 12
private const val PAINT_COLOR_FILTER = 13
private const val PAINT_ANTI_ALIAS = 14
private const val PAINT_STROKE_JOIN = 15
private const val PAINT_FILTER_BITMAP = 17
private const val PAINT_BLEND_MODE = 18
private const val PAINT_COLOR_ID = 19
private const val PAINT_COLOR_FILTER_ID = 20
private const val PAINT_TYPEFACE = 16
private const val PAINT_CLEAR_COLOR_FILTER = 21
private const val PAINT_SHADER_MATRIX = 22
private const val PAINT_FONT_AXIS = 23
private const val PAINT_TEXTURE = 24

private const val FONT_AXIS_WEIGHT = 0x77676874 // wght
private const val FONT_AXIS_ITALIC = 0x6974616c // ital
private const val FONT_AXIS_SLANT = 0x736c6e74 // slnt
private val SUPPORTED_FONT_AXES = setOf(FONT_AXIS_WEIGHT, FONT_AXIS_ITALIC, FONT_AXIS_SLANT)

private fun particleCompareWork(operation: RcParticleCompare, particleCount: Int): Long {
  fun literalIndex(word: RcFloatWord, fallback: Int): Int =
    word
      .takeIf { it.referencedId == null }
      ?.value
      ?.let { value -> if (value < 0f) fallback else value.toInt().coerceIn(0, particleCount) }
      ?: fallback
  val start = literalIndex(operation.minimumIndex, 0)
  val end = literalIndex(operation.maximumIndex, particleCount).coerceAtLeast(start)
  val selected = (end - start).toLong()
  val evaluations =
    1L +
      operation.condition.size +
      operation.firstEquations.sumOf { it.size } +
      operation.secondEquations.sumOf { it.size }
  val visits =
    if (operation.secondEquations.isEmpty()) selected else selected * (selected - 1L) / 2L
  return visits * evaluations
}

private fun fontAxisName(tag: Int): String =
  buildString(4) {
    append((tag ushr 24).toChar())
    append((tag ushr 16 and 0xff).toChar())
    append((tag ushr 8 and 0xff).toChar())
    append((tag and 0xff).toChar())
  }

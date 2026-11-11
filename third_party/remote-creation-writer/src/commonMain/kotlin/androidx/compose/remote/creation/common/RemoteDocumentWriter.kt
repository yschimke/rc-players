package androidx.compose.remote.creation.common

/**
 * Common Kotlin write side for Remote Compose documents.
 *
 * This starts with the layout/text surface used by the cross-platform smoke client. Additional
 * operations are added here as Creation Compose moves off the legacy Java writer.
 */
public class RemoteDocumentWriter(
  width: Int,
  height: Int,
  densityBehavior: Int = DensityBehaviorDp,
  profiles: Int = 0,
) : RemoteWriter {
  private val documentWidth = width
  private val documentHeight = height
  private val documentDensityBehavior = densityBehavior
  private val documentProfiles = profiles
  private var customComponentsUsed = false
  private var drawToBitmapUsed = false
  private var shaderUsed = false
  private var mesh2DUsed = false
  private var patternUsed = false
  private var bitmapFontOnPathUsed = false
  private val bodyBuffer = RemoteWireBuffer()
  private var wire = bodyBuffer
  private val declarationBuffer = RemoteWireBuffer()
  private var headerSize = 0
  private var nextComponentId = -1
  private var lastComponentId = -1
  private var nextDataId = 42
  private var paintResetPending = true
  private val textIds = mutableMapOf<String, Int>()
  private val pathIds = mutableMapOf<PathKey, Int>()
  private val integerIds = mutableMapOf<Int, Int>()
  private val floatArrayIds = mutableMapOf<IntArrayKey, Int>()
  private val idListIds = mutableMapOf<IntArrayKey, Int>()
  private val floatExpressionIds = mutableMapOf<IntArrayKey, Int>()
  private val integerExpressionIds = mutableMapOf<LongArrayKey, Int>()
  private val textLengthIds = mutableMapOf<Int, Int>()
  private val textLookupIds = mutableMapOf<Long, Int>()
  private val textLookupIntIds = mutableMapOf<Long, Int>()
  private val textFromFloatIds = mutableMapOf<TextFromFloatKey, Int>()
  private val bitmapUrlIds = mutableMapOf<String, Int>()
  private val inlineBitmapIds = mutableMapOf<List<Byte>, Int>()
  private val componentValueIds = mutableMapOf<Long, Int>()
  private val patternDefinitions = mutableListOf<Pair<RemoteWireBuffer, Int>>()

  /**
   * One entry per open [createFloatFunction]: what restores the dedup caches to their state before
   * it.
   */
  private val functionScopes = mutableListOf<() -> Unit>()

  /**
   * One entry per open [definePattern], [addPatternForEach] or [startLoop]: the ids that belong to
   * its body. They are the body's own variables (a pattern's parameters, a for-each's item, a
   * loop's index) and every declaration that reads one of them, directly or through another.
   *
   * Such a declaration is written where it is made, in the body, not ahead of the layout: a pattern
   * or for-each body is a template the player expands with those ids replaced, and a loop body is
   * run once per value of its index, so what is computed from them has to be computed there. It is
   * forgotten when the body ends. Every other declaration made in a body goes ahead of the layout
   * like any other, so what is declared for the body and read again after it is one id, declared
   * once, outside the template.
   */
  private val bodyScopes = mutableListOf<MutableSet<Int>>()

  init {
    writeHeader(width, height, profiles, densityBehavior)
    headerSize = wire.toByteArray().size
  }

  override fun createFloatId(): Float = Utils.asNan(nextDataId++)

  override fun nextId(): Int = nextDataId++

  override val rcPaint: RcPaint = RcPaint(this)

  public fun root(content: RemoteDocumentWriter.() -> Unit) {
    startRoot()
    content()
    endRoot()
  }

  override fun startRoot() {
    operation(LayoutRoot)
    wire.writeInt(componentId())
  }

  override fun endRoot(): Unit = containerEnd()

  override fun startBox(modifier: RemoteModifierData, horizontal: Int, vertical: Int): Unit =
    startLayout(LayoutBox, modifier, horizontal, vertical)

  override fun endBox(): Unit = endLayout()

  override fun startRow(modifier: RemoteModifierData, horizontal: Int, vertical: Int): Unit =
    startLinearLayout(LayoutRow, modifier, horizontal, vertical)

  override fun endRow(): Unit = endLayout()

  override fun startColumn(modifier: RemoteModifierData, horizontal: Int, vertical: Int): Unit =
    startLinearLayout(LayoutColumn, modifier, horizontal, vertical)

  override fun endColumn(): Unit = endLayout()

  override fun startCollapsibleRow(
    modifier: RemoteModifierData,
    horizontal: Int,
    vertical: Int,
  ): Unit = startLinearLayout(LayoutCollapsibleRow, modifier, horizontal, vertical)

  override fun endCollapsibleRow(): Unit = endLayout()

  override fun startCollapsibleColumn(
    modifier: RemoteModifierData,
    horizontal: Int,
    vertical: Int,
  ): Unit = startLinearLayout(LayoutCollapsibleColumn, modifier, horizontal, vertical)

  override fun endCollapsibleColumn(): Unit = endLayout()

  override fun startCanvas(modifier: RemoteModifierData) {
    operation(LayoutCanvas)
    wire.writeInt(resolveComponentId(modifier.componentId))
    wire.writeInt(-1)
    modifier.writeTo(this)
    contentSection(modifier.contentComponentId)
  }

  override fun endCanvas(): Unit = endLayout()

  override fun startCanvasOperations(): Unit = operation(CanvasOperations)

  override fun endCanvasOperations(): Unit = containerEnd()

  override fun startFitBox(modifier: RemoteModifierData, horizontal: Int, vertical: Int): Unit =
    startLayout(LayoutFitBox, modifier, horizontal, vertical)

  override fun endFitBox(): Unit = endLayout()

  override fun startFlow(
    modifier: RemoteModifierData,
    horizontal: Int,
    vertical: Int,
    maxItemsInEachRow: Int,
    maxLines: Int,
  ) {
    operation(LayoutFlow)
    wire.writeInt(resolveComponentId(modifier.componentId))
    wire.writeInt(-1)
    wire.writeInt(horizontal)
    wire.writeInt(vertical)
    wire.writeFloat(modifier.spacedBy)
    wire.writeInt(maxItemsInEachRow)
    wire.writeInt(maxLines)
    modifier.writeTo(this)
    contentSection(modifier.contentComponentId)
  }

  override fun endFlow(): Unit = endLayout()

  override fun startStateLayout(modifier: RemoteModifierData, indexId: Int) {
    operation(LayoutState)
    wire.writeInt(resolveComponentId(modifier.componentId))
    wire.writeInt(-1)
    wire.writeInt(0)
    wire.writeInt(0)
    wire.writeInt(indexId)
    modifier.writeTo(this)
    contentSection(modifier.contentComponentId)
  }

  override fun endStateLayout(): Unit = endLayout()

  override fun writeModifier(modifier: RemoteModifierOperation) {
    when (modifier) {
      is RemoteModifierOperation.Width -> {
        operation(ModifierWidth)
        wire.writeInt(modifier.type)
        wire.writeFloat(modifier.value)
      }
      is RemoteModifierOperation.Height -> {
        operation(ModifierHeight)
        wire.writeInt(modifier.type)
        wire.writeFloat(modifier.value)
      }
      is RemoteModifierOperation.Padding ->
        floats(ModifierPadding, modifier.left, modifier.top, modifier.right, modifier.bottom)
      is RemoteModifierOperation.Background -> {
        operation(ModifierBackground)
        wire.writeInt(modifier.flags)
        wire.writeInt(modifier.colorId)
        wire.writeInt(0)
        wire.writeInt(0)
        wire.writeFloat(modifier.red)
        wire.writeFloat(modifier.green)
        wire.writeFloat(modifier.blue)
        wire.writeFloat(modifier.alpha)
        wire.writeInt(modifier.shape)
      }
      RemoteModifierOperation.ClipRect -> operation(ModifierClipRect)
      is RemoteModifierOperation.RoundedClipRect ->
        floats(
          ModifierRoundedClipRect,
          modifier.topStart,
          modifier.topEnd,
          modifier.bottomStart,
          modifier.bottomEnd,
        )
      is RemoteModifierOperation.WidthIn -> floats(ModifierWidthIn, modifier.min, modifier.max)
      is RemoteModifierOperation.HeightIn -> floats(ModifierHeightIn, modifier.min, modifier.max)
      is RemoteModifierOperation.Offset -> floats(ModifierOffset, modifier.x, modifier.y)
      is RemoteModifierOperation.ZIndex -> floats(ModifierZIndex, modifier.value)
      RemoteModifierOperation.Ripple -> operation(ModifierRipple)
      RemoteModifierOperation.DrawContent -> operation(ModifierDrawContent)
      is RemoteModifierOperation.MacroCall -> {
        operation(MacroCall)
        wire.writeInt(modifier.id)
        wire.writeInt(modifier.argumentIds.size)
        modifier.argumentIds.forEach(wire::writeInt)
        containerEnd()
      }
      is RemoteModifierOperation.Click -> {
        if (modifier.clickType == 0) {
          operation(ModifierClick)
        } else {
          operation(ModifierMultiClick)
          wire.writeInt(modifier.clickType)
        }
        modifier.actions.forEach(::writeAction)
        containerEnd()
      }
      is RemoteModifierOperation.Touch -> {
        operation(
          when (modifier.type) {
            0 -> ModifierTouchDown
            1 -> ModifierTouchUp
            else -> ModifierTouchCancel
          },
        )
        modifier.actions.forEach(::writeAction)
        containerEnd()
      }
      is RemoteModifierOperation.Semantics -> {
        operation(AccessibilitySemantics)
        wire.writeInt(modifier.contentDescriptionId)
        wire.writeByte(modifier.role)
        wire.writeInt(modifier.textId)
        wire.writeInt(modifier.stateDescriptionId)
        wire.writeByte(modifier.mode)
        wire.writeByte(if (modifier.enabled) 1 else 0)
        wire.writeByte(if (modifier.clickable) 1 else 0)
      }
      is RemoteModifierOperation.GraphicsLayer -> {
        operation(ModifierGraphicsLayer)
        wire.writeInt(modifier.attributes.size)
        // Match the legacy Integer-keyed HashMap's stable bucket iteration order.
        val capacity = if (modifier.attributes.size > 12) 32 else 16
        modifier.attributes
          .sortedBy { it.id and (capacity - 1) }
          .forEach { attribute ->
            when (attribute) {
              is RemoteLayerAttribute.FloatValue -> {
                wire.writeInt(attribute.id or (1 shl 10))
                wire.writeFloat(attribute.value)
              }
              is RemoteLayerAttribute.IntValue -> {
                wire.writeInt(attribute.id)
                wire.writeInt(attribute.value)
              }
            }
          }
      }
      is RemoteModifierOperation.Border -> {
        operation(ModifierBorder)
        wire.writeInt(if (modifier.dynamicColor) 2 else 0)
        wire.writeInt(if (modifier.dynamicColor) modifier.color else 0)
        // Wire version 1, as AndroidX's `BorderModifierOperation` writes: players draw the stroke
        // inside the bounds. Version 0 is the legacy centred stroke, half of it clipped away.
        wire.writeInt(BorderWireVersion)
        wire.writeInt(0)
        wire.writeFloat(modifier.width)
        wire.writeFloat(modifier.roundedCorner)
        if (modifier.dynamicColor) {
          repeat(4) { wire.writeFloat(0f) }
        } else {
          wire.writeFloat(((modifier.color ushr 16) and 0xff) / 255f)
          wire.writeFloat(((modifier.color ushr 8) and 0xff) / 255f)
          wire.writeFloat((modifier.color and 0xff) / 255f)
          wire.writeFloat(((modifier.color ushr 24) and 0xff) / 255f)
        }
        wire.writeInt(modifier.shapeType)
      }
      is RemoteModifierOperation.Visibility -> {
        operation(ModifierVisibility)
        wire.writeInt(modifier.valueId)
      }
      is RemoteModifierOperation.CollapsiblePriority -> {
        operation(ModifierCollapsiblePriority)
        wire.writeInt(modifier.orientation)
        wire.writeFloat(modifier.priority)
      }
      is RemoteModifierOperation.AlignBy -> {
        operation(ModifierAlignBy)
        wire.writeFloat(modifier.line)
        wire.writeInt(modifier.flags)
      }
      is RemoteModifierOperation.Marquee -> {
        operation(ModifierMarquee)
        wire.writeInt(modifier.iterations)
        wire.writeInt(modifier.animationMode)
        wire.writeFloat(modifier.repeatDelayMillis)
        wire.writeFloat(modifier.initialDelayMillis)
        wire.writeFloat(modifier.spacing)
        wire.writeFloat(modifier.velocity)
      }
      is RemoteModifierOperation.AnimationSpec -> {
        operation(AnimationSpec)
        wire.writeInt(modifier.animationId)
        wire.writeFloat(modifier.motionDuration)
        wire.writeInt(modifier.motionEasingType)
        wire.writeFloat(modifier.visibilityDuration)
        wire.writeInt(modifier.visibilityEasingType)
        wire.writeInt(modifier.enterAnimation)
        wire.writeInt(modifier.exitAnimation)
        // Mirrors AnimationSpec.apply: the function ids follow only when either side is CUSTOM or
        // names one, so every other spec keeps the seven-field encoding older players read.
        if (
          (modifier.enterAnimation and 0xFF) == AnimationCustom ||
            (modifier.exitAnimation and 0xFF) == AnimationCustom ||
            modifier.enterFunctionId != -1 ||
            modifier.exitFunctionId != -1
        ) {
          wire.writeInt(modifier.enterFunctionId)
          wire.writeInt(modifier.exitFunctionId)
        }
      }
      is RemoteModifierOperation.Scroll -> {
        operation(ModifierScroll)
        wire.writeInt(modifier.direction)
        wire.writeFloat(modifier.position)
        wire.writeFloat(modifier.maximum)
        wire.writeFloat(modifier.notchMaximum)

        addTouchExpression(
          id = Utils.idFromNan(modifier.position),
          value = 0f,
          min = 0f,
          max = modifier.maximum,
          velocityId = 0f,
          touchEffects = 3,
          expression =
            floatArrayOf(
              Utils.asNan(if (modifier.direction != 0) 13 else 14),
              -1f,
              Utils.asNan(0x310003),
            ),
          stopMode = if (modifier.notches > 0) TouchStop.NOTCHES_EVEN else TouchStop.GENTLY,
          stopSpec =
            if (modifier.notches > 0) {
              floatArrayOf(modifier.notches.toFloat(), modifier.notchMaximum)
            } else {
              null
            },
        )
        containerEnd()
      }
    }
  }

  private fun writeAction(action: RemoteActionData) {
    when (action) {
      is RemoteActionData.Host -> {
        operation(HostAction)
        wire.writeInt(action.actionId)
      }
      is RemoteActionData.HostMetadata -> {
        operation(HostMetadataAction)
        wire.writeInt(action.actionId)
        wire.writeInt(action.metadataId)
      }
      is RemoteActionData.HostNamed -> {
        operation(HostNamedAction)
        wire.writeInt(action.nameId)
        wire.writeInt(action.type)
        wire.writeInt(action.valueId)
      }
      is RemoteActionData.IntegerChange -> {
        operation(ValueIntegerChange)
        wire.writeInt(action.targetId)
        wire.writeInt(action.value)
      }
      is RemoteActionData.IntegerExpressionChange -> {
        operation(ValueIntegerExpressionChange)
        wire.writeLong(action.targetId)
        wire.writeLong(action.expressionId)
      }
      is RemoteActionData.FloatChange -> {
        operation(ValueFloatChange)
        wire.writeInt(action.targetId)
        wire.writeFloat(action.value)
      }
      is RemoteActionData.FloatExpressionChange -> {
        operation(ValueFloatExpressionChange)
        wire.writeInt(action.targetId)
        wire.writeInt(action.expressionId)
      }
      is RemoteActionData.StringChange -> {
        operation(ValueStringChange)
        wire.writeInt(action.targetId)
        wire.writeInt(action.valueId)
      }
    }
  }

  override fun startTextComponent(data: RemoteTextData) {
    require(data.fontAxisIds.size == data.fontAxisValues.size)
    operation(CoreText)
    wire.writeInt(data.textId)
    val parameters = mutableListOf<() -> Unit>()
    fun int(id: Int, value: Int, default: Int) {
      if (value != default)
        parameters += {
          wire.writeByte(id)
          wire.writeInt(value)
        }
    }
    fun float(id: Int, value: Float, default: Float) {
      if (value != default)
        parameters += {
          wire.writeByte(id)
          wire.writeFloat(value)
        }
    }
    fun boolean(id: Int, value: Boolean) {
      if (value)
        parameters += {
          wire.writeByte(id)
          wire.writeByte(1)
        }
    }
    int(1, resolveComponentId(data.modifier.componentId), -1)
    int(3, data.color, 0xff000000.toInt())
    int(4, data.colorId, -1)
    float(5, data.fontSize, 36f)
    float(25, data.minFontSize, -1f)
    float(26, data.maxFontSize, -1f)
    int(6, data.fontStyle, 0)
    float(7, data.fontWeight, 400f)
    int(8, data.fontFamilyId, -1)
    int(9, data.textAlign, 1)
    int(10, data.overflow, 1)
    int(11, data.maxLines, Int.MAX_VALUE)
    float(12, data.letterSpacing, 0f)
    float(13, data.lineHeightAdd, 0f)
    float(14, data.lineHeightMultiplier, 1f)
    int(15, data.lineBreakStrategy, 0)
    int(16, data.hyphenationFrequency, 0)
    int(17, data.justificationMode, 0)
    boolean(18, data.underline)
    boolean(19, data.strikethrough)
    if (data.fontAxisIds.isNotEmpty()) {
      parameters += {
        wire.writeByte(20)
        wire.writeShort(data.fontAxisIds.size)
        data.fontAxisIds.forEach(wire::writeInt)
      }
      parameters += {
        wire.writeByte(21)
        wire.writeShort(data.fontAxisValues.size)
        data.fontAxisValues.forEach(wire::writeFloat)
      }
    }
    boolean(22, data.autosize)
    int(23, data.flags, 0)
    int(24, data.textStyleId, -1)
    wire.writeShort(parameters.size)
    parameters.forEach { it() }
    data.modifier.writeTo(this)
    contentSection(data.modifier.contentComponentId)
  }

  override fun endTextComponent(): Unit = endLayout()

  override fun image(data: RemoteImageData) {
    operation(LayoutImage)
    wire.writeInt(resolveComponentId(data.modifier.componentId))
    wire.writeInt(-1)
    wire.writeInt(data.bitmapId)
    wire.writeInt(data.scaleType)
    wire.writeFloat(data.alpha)
    data.modifier.writeTo(this)
    containerEnd()
  }

  override fun startCustom(
    modifier: RemoteModifierData,
    configId: Int,
    properties: List<Custom.CustomProperty>,
  ) {
    customComponentsUsed = true
    operation(LayoutCustom)
    wire.writeInt(resolveComponentId(modifier.componentId))
    wire.writeInt(-1)
    wire.writeInt(configId)
    wire.writeInt(properties.size)
    properties.forEach { property ->
      wire.writeShort(property.id.toInt())
      wire.writeShort(property.dataType.toInt())
      if (
        property.dataType == Custom.CustomProperty.FLOAT_PROP ||
          property.dataType == Custom.CustomProperty.FLOAT_RETURN
      ) {
        wire.writeFloat(property.floatValue)
      } else {
        wire.writeInt(property.intValue)
      }
    }
    modifier.writeTo(this)
    // LOCAL PATCH (rc-players): as upstream's `addContentStart`, the content section takes a fresh
    // component id. The port reused the custom layout's own id, giving two components one id,
    // which rc-players' player rejects.
    contentSection(modifier.contentComponentId)
  }

  override fun endCustom() {
    containerEnd()
    containerEnd()
  }

  override fun definePattern(id: Int, parameterIds: IntArray) {
    patternUsed = true
    operation(PatternDefine)
    wire.writeInt(id)
    wire.writeInt(parameterIds.size)
    parameterIds.forEach(wire::writeInt)
    patternDefinitions += wire to wire.size
    wire.writeInt(0)
    bodyScopes += parameterIds.toMutableSet()
    // LOCAL PATCH (rc-players): the body is a template, drawn where the pattern is called with
    // whatever paint the player has there, so the first paint in it is written in full, and so is
    // the first after the definition, after each call and after a for-each, where the player's
    // paint is the template's last. Upstream's canvas shares one `PaintTracker` across all of them
    // and its writer forces a full paint only at a component's content start.
    paintResetPending = true
  }

  override fun endPatternDefine() {
    endBodyScope()
    // The body's length, as `PatternDefine.applyEnd`, in the buffer the definition began in,
    // whichever buffer the body's last operation went to.
    val (definedIn, offset) = patternDefinitions.removeLast()
    definedIn.overwriteInt(offset, definedIn.size - offset - 4)
    containerEnd()
    paintResetPending = true
  }

  override fun inflatePattern(id: Int, argumentIds: IntArray) {
    patternUsed = true
    operation(PatternInflation)
    wire.writeInt(id)
    wire.writeInt(argumentIds.size)
    argumentIds.forEach(wire::writeInt)
  }

  override fun endPatternInflation() {
    containerEnd()
    paintResetPending = true
  }

  override fun addPatternForEach(collectionId: Int, localItemId: Int) {
    patternUsed = true
    operation(PatternForEach)
    wire.writeInt(collectionId)
    wire.writeInt(localItemId)
    bodyScopes += mutableSetOf(localItemId)
    paintResetPending = true
  }

  override fun endPatternForEach() {
    endBodyScope()
    containerEnd()
    paintResetPending = true
  }

  override fun conditionalOperations(type: Int, first: Float, second: Float) {
    operation(ConditionalOperations)
    wire.writeByte(type)
    wire.writeFloat(first)
    wire.writeFloat(second)
  }

  override fun endConditionalOperations(): Unit = containerEnd()

  override fun drawOnBitmap(bitmapId: Int, mode: Int, color: Int) {
    drawToBitmapUsed = true
    operation(DrawToBitmap)
    wire.writeInt(bitmapId)
    wire.writeInt(mode)
    wire.writeInt(color)
  }

  /**
   * As `RemoteComposeWriter.createFloatFunction`, with one difference in where the function lands.
   *
   * The definition, `FunctionDefine` through its `ContainerEnd`, is written whole into the
   * declarations, ahead of the layout, wherever it is called from. Its body is not hoisted further:
   * every operation in it, declarations included, stays inside it in order, as upstream writes it,
   * because a player evaluates a function body's expressions and data only when it runs the
   * function, with the parameters bound. For the same reason nothing the body declares is reused
   * after it: the dedup caches go back to what they held before [createFloatFunction]. The first
   * paint in the body, and the first after it, are written in full (a player resets the paint
   * before it runs a visibility function).
   */
  override fun createFloatFunction(args: FloatArray): Int {
    val id = nextDataId++
    val parameterIds = IntArray(args.size) { nextDataId++ }
    parameterIds.forEachIndexed { index, parameterId -> args[index] = Utils.asNan(parameterId) }
    operation(FunctionDefine)
    wire.writeInt(id)
    wire.writeInt(parameterIds.size)
    parameterIds.forEach(wire::writeInt)
    functionScopes += snapshotCaches()
    paintResetPending = true
    return id
  }

  override fun endFloatFunction() {
    check(functionScopes.isNotEmpty()) { "endFloatFunction without createFloatFunction" }
    containerEnd()
    functionScopes.removeLast().invoke()
    paintResetPending = true
  }

  override fun callFloatFunction(id: Int, vararg args: Float) {
    operation(FunctionCall)
    wire.writeInt(id)
    wire.writeInt(args.size)
    args.forEach(wire::writeFloat)
  }

  override fun startLoop(indexId: Int, from: Float, step: Float, until: Float) {
    operation(LoopStart)
    wire.writeInt(indexId)
    wire.writeFloat(from)
    wire.writeFloat(step)
    wire.writeFloat(until)
    // What the body computes from the index is computed in the body, once per iteration: a player
    // runs an expression declared ahead of the layout once a frame, not once per index
    // (`LoopOperation.paint` applies only the loop's own operations for each value).
    bodyScopes += mutableSetOf(indexId)
  }

  override fun endLoop() {
    endBodyScope()
    containerEnd()
  }

  public fun column(
    horizontal: Int = HorizontalStart,
    vertical: Int = VerticalTop,
    spacedBy: Float = 0f,
    componentId: Int = -1,
    content: RemoteDocumentWriter.() -> Unit,
  ) {
    operation(LayoutColumn)
    wire.writeInt(resolveComponentId(componentId))
    wire.writeInt(-1)
    wire.writeInt(horizontal)
    wire.writeInt(vertical)
    wire.writeFloat(spacedBy)
    contentSection()
    content()
    containerEnd()
    containerEnd()
  }

  public fun text(value: String, fontSize: Float = 12f, componentId: Int = -1) {
    val textId = textId(value)

    operation(CoreText)
    wire.writeInt(textId)
    wire.writeShort(2)
    wire.writeByte(TextParameterId)
    wire.writeInt(resolveComponentId(componentId))
    wire.writeByte(TextParameterFontSize)
    wire.writeFloat(fontSize)
    contentSection()
    containerEnd()
    containerEnd()
  }

  public fun encodeToByteArray(): ByteArray {
    val body = bodyBuffer.toByteArray()
    val declarations = declarationBuffer.toByteArray()
    // Custom inputs/components require the experimental profile even when the caller uses the
    // general-purpose common capture API. Keep that requirement in the writer, not a host shim.
    var profiles = documentProfiles
    if (customComponentsUsed) profiles = profiles or ProfileAndroidxExperimental
    // LOCAL PATCH (rc-players): remote-core reads `DrawToBitmap` only under the AndroidX or widgets
    // profile (`Operations.createMapV7_Androidx`/`_Widgets`); under neither, AndroidX stops at the
    // first one with "Unknown operation". Upstream's writer refuses the operation under such a
    // profile; this one names AndroidX's in the header, as it names the experimental profile for
    // custom components.
    if (drawToBitmapUsed && (profiles and (ProfileAndroidx or ProfileWidgets)) == 0) {
      profiles = profiles or ProfileAndroidx
    }
    // LOCAL PATCH (rc-players): likewise `ShaderData`, which remote-core reads only under the
    // AndroidX profile (`createMapV7_Androidx`; the widgets maps have no runtime shaders).
    // (A widgets-profile document can't hold one: `addShader` refuses it there.)
    if (shaderUsed && (profiles and ProfileAndroidx) == 0) {
      profiles = profiles or ProfileAndroidx
    }
    // LOCAL PATCH (rc-players): the 2D mesh operations are read only by remote-core's experimental
    // V7 maps (`createMapV7_Androidx_Experimental` / `_Widgets_Experimental`), so a document that
    // carries one names the experimental profile, and AndroidX's when it names neither base.
    if (mesh2DUsed) {
      if ((profiles and (ProfileAndroidx or ProfileWidgets)) == 0) {
        profiles = profiles or ProfileAndroidx
      }
      profiles = profiles or ProfileExperimental
    }
    // LOCAL PATCH (rc-players): likewise the pattern operations (`MACRO_DEFINE`, `MACRO_CALL`,
    // `MACRO_FOR_EACH`), which remote-core reads only under the AndroidX experimental map
    // (`createMapV7_Androidx_Experimental`; the widgets maps have none). The capture functions take
    // no profile, so without this a document from `RemoteCanvas.definePattern` or `forEach` would
    // be refused as holding unknown operations.
    if (patternUsed) {
      if ((profiles and (ProfileAndroidx or ProfileWidgets)) == 0) {
        profiles = profiles or ProfileAndroidx
      }
      profiles = profiles or ProfileExperimental
    }
    // LOCAL PATCH (rc-players): and a bitmap-font text run on a path, read under the AndroidX or
    // widgets profile (`createMapV7_Androidx` / `_Widgets`), as `DrawToBitmap` is.
    if (bitmapFontOnPathUsed && (profiles and (ProfileAndroidx or ProfileWidgets)) == 0) {
      profiles = profiles or ProfileAndroidx
    }
    val header =
      if (profiles != documentProfiles || customComponentsUsed) {
        val previous = wire
        val output = RemoteWireBuffer()
        wire = output
        try {
          writeHeader(
            documentWidth,
            documentHeight,
            profiles,
            documentDensityBehavior,
            if (customComponentsUsed) CustomMinorVersion else MinorVersion,
          )
          output.toByteArray()
        } finally {
          wire = previous
        }
      } else body.copyOfRange(0, headerSize)
    return header + declarations + body.copyOfRange(headerSize, body.size)
  }

  override val componentIdForCache: Int
    get() = lastComponentId

  override fun addComponentWidthValue(componentId: Int): Float =
    componentValue(ComponentWidth, componentId)

  override fun addComponentHeightValue(componentId: Int): Float =
    componentValue(ComponentHeight, componentId)

  override fun addComponentXValue(componentId: Int): Float =
    componentValue(ComponentPosX, componentId)

  override fun addComponentYValue(componentId: Int): Float =
    componentValue(ComponentPosY, componentId)

  override fun addComponentRootXValue(componentId: Int): Float =
    componentValue(ComponentPosRootX, componentId)

  override fun addComponentRootYValue(componentId: Int): Float =
    componentValue(ComponentPosRootY, componentId)

  override fun addComponentValue(type: Int, componentId: Int, valueId: Float) {
    val id = Utils.idFromNan(valueId)
    componentValueIds.getOrPut(pairKey(componentId, type)) { id }
    operation(ComponentValue)
    wire.writeInt(type)
    wire.writeInt(componentId)
    wire.writeInt(id)
  }

  override fun setNamedVariable(id: Int, name: String, type: Int): Unit =
    writeNamedVariable(id, type, name)

  override fun addInteger(value: Int): Long {
    val id =
      integerIds.getOrPut(value) {
        val allocated = nextDataId++
        writeInteger(allocated, value)
        allocated
      }
    return id.toLong() + IntegerIdOffset
  }

  override fun addNamedInt(name: String, initialValue: Int): Long {
    val id = nextDataId++
    writeNamedVariable(id, androidx.compose.remote.creation.common.NamedVariable.INT_TYPE, name)
    writeInteger(id, initialValue)
    return id.toLong() + IntegerIdOffset
  }

  override fun addNamedFloat(name: String, initialValue: Float): Float {
    val id = nextDataId++
    writeNamedVariable(id, androidx.compose.remote.creation.common.NamedVariable.FLOAT_TYPE, name)
    operation(DataFloat)
    wire.writeInt(id)
    wire.writeFloat(initialValue)
    return Utils.asNan(id)
  }

  override fun reserveFloatVariable(): Float = Utils.asNan(nextDataId++)

  override fun addFloatArray(values: FloatArray): Float {
    val key = IntArrayKey(IntArray(values.size) { values[it].toRawBits() })
    val id =
      floatArrayIds.getOrPut(key) {
        val allocated = nextDataId++
        declaration(FloatList, allocated, inBody(values))
        wire.writeInt(allocated)
        wire.writeInt(values.size)
        values.forEach(wire::writeFloat)
        allocated
      }
    return Utils.asNan(id)
  }

  override fun addList(ids: IntArray): Float {
    val id =
      idListIds.getOrPut(IntArrayKey(ids)) {
        val allocated = nextDataId++
        declaration(IdList, allocated, ids.any(::inBody))
        wire.writeInt(allocated)
        wire.writeInt(ids.size)
        ids.forEach(wire::writeInt)
        allocated
      }
    return Utils.asNan(id)
  }

  override fun addDynamicFloatArray(size: Float): Float {
    val id = nextDataId++
    declaration(DynamicFloatList, id, inBody(size))
    wire.writeInt(id)
    wire.writeFloat(size)
    return Utils.asNan(id)
  }

  override fun setArrayValue(id: Int, index: Float, value: Float) {
    declaration(UpdateDynamicFloatList, id, inBody(id) || inBody(index) || inBody(value))
    wire.writeInt(id)
    wire.writeFloat(index)
    wire.writeFloat(value)
  }

  override fun floatExpression(vararg values: Float): Float = floatExpression(values, null)

  override fun floatExpression(values: FloatArray, animation: FloatArray?): Float {
    val key = IntArrayKey(values.toRawBits())
    val id =
      floatExpressionIds.getOrPut(key) {
        val allocated = nextDataId++
        declaration(AnimatedFloat, allocated, inBody(values) || inBody(animation))
        wire.writeInt(allocated)
        wire.writeInt(values.size or ((animation?.size ?: 0) shl 16))
        values.forEach(wire::writeFloat)
        animation?.forEach(wire::writeFloat)
        allocated
      }
    return Utils.asNan(id)
  }

  override fun integerExpression(vararg values: Long): Long {
    var mask = 0
    val encoded =
      IntArray(values.size) { index ->
        val value = values[index]
        if (value >= IntegerIdOffset) {
          mask = mask or (1 shl index)
          (value - IntegerIdOffset).toInt()
        } else {
          value.toInt()
        }
      }
    val key = LongArrayKey(longArrayOf(mask.toLong(), *values))
    val id =
      integerExpressionIds.getOrPut(key) {
        val allocated = nextDataId++
        val reads = values.any { it >= IntegerIdOffset && inBody((it - IntegerIdOffset).toInt()) }
        declaration(IntegerExpression, allocated, reads)
        wire.writeInt(allocated)
        wire.writeInt(mask)
        wire.writeInt(encoded.size)
        encoded.forEach(wire::writeInt)
        allocated
      }
    return id.toLong() + IntegerIdOffset
  }

  override fun timeAttribute(longId: Int, type: Short, vararg args: Int): Float {
    val id = nextDataId++
    declaration(AttributeTime, id, inBody(longId))
    wire.writeInt(id)
    wire.writeInt(longId)
    wire.writeShort(type.toInt())
    wire.writeShort(args.size)
    args.forEach(wire::writeInt)
    return Utils.asNan(id)
  }

  override fun idLookup(arrayId: Float, index: Float): Int {
    val id = nextDataId++
    declaration(IdLookup, id, inBody(arrayId) || inBody(index))
    wire.writeInt(id)
    wire.writeInt(Utils.idFromNan(arrayId))
    wire.writeFloat(index)
    return id
  }

  override fun idLookup(outputId: Int, arrayId: Float, index: Float) {
    declaration(IdLookup, outputId, inBody(outputId) || inBody(arrayId) || inBody(index))
    wire.writeInt(outputId)
    wire.writeInt(Utils.idFromNan(arrayId))
    wire.writeFloat(index)
  }

  override fun textLookup(arrayId: Float, index: Float): Int {
    val key = pairKey(arrayId.toRawBits(), index.toRawBits())
    return textLookupIds.getOrPut(key) {
      val id = nextDataId++
      declaration(TextLookup, id, inBody(arrayId) || inBody(index))
      wire.writeInt(id)
      wire.writeInt(Utils.idFromNan(arrayId))
      wire.writeFloat(index)
      id
    }
  }

  override fun textLookup(arrayId: Float, indexId: Int): Int {
    val key = pairKey(arrayId.toRawBits(), indexId)
    return textLookupIntIds.getOrPut(key) {
      val id = nextDataId++
      declaration(TextLookupInt, id, inBody(arrayId) || inBody(indexId))
      wire.writeInt(id)
      wire.writeInt(Utils.idFromNan(arrayId))
      wire.writeInt(indexId)
      id
    }
  }

  override fun textLength(textId: Int): Float {
    val id =
      textLengthIds.getOrPut(textId) {
        val allocated = nextDataId++
        declaration(TextLength, allocated, inBody(textId))
        wire.writeInt(allocated)
        wire.writeInt(textId)
        allocated
      }
    return Utils.asNan(id)
  }

  override fun textMerge(leftId: Int, rightId: Int): Int {
    val id = nextDataId++
    textMerge(id, leftId, rightId)
    return id
  }

  override fun textMerge(outputId: Int, leftId: Int, rightId: Int) {
    declaration(TextMerge, outputId, inBody(outputId) || inBody(leftId) || inBody(rightId))
    wire.writeInt(outputId)
    wire.writeInt(leftId)
    wire.writeInt(rightId)
  }

  override fun addTouchExpression(
    id: Int,
    value: Float,
    min: Float,
    max: Float,
    velocityId: Float,
    touchEffects: Int,
    expression: FloatArray,
    stopMode: Int,
    stopSpec: FloatArray?,
    easingSpec: FloatArray?,
  ) {
    require(expression.size <= 32) { "a touch expression is at most 32 words: ${expression.size}" }
    require(stopMode in 0..0xffff) { "stop mode out of range: $stopMode" }
    val stops = stopSpec ?: FloatArray(0)
    val easing = easingSpec ?: FloatArray(0)
    require(stops.size <= 200) { "a stop spec is at most 200 words: ${stops.size}" }
    require(easing.size <= 200) { "an easing spec is at most 200 words: ${easing.size}" }
    operation(TouchExpression)
    wire.writeInt(id)
    wire.writeFloat(value)
    wire.writeFloat(min)
    wire.writeFloat(max)
    wire.writeFloat(velocityId)
    wire.writeInt(touchEffects)
    wire.writeInt(expression.size)
    expression.forEach(wire::writeFloat)
    wire.writeInt((stopMode shl 16) or stops.size)
    stops.forEach(wire::writeFloat)
    wire.writeInt(easing.size)
    easing.forEach(wire::writeFloat)
  }

  override fun textSubtext(textId: Int, start: Float, length: Float): Int {
    val id = nextDataId++
    declaration(TextSubtext, id, inBody(textId) || inBody(start) || inBody(length))
    wire.writeInt(id)
    wire.writeInt(textId)
    wire.writeFloat(start)
    wire.writeFloat(length)
    return id
  }

  override fun textTransform(textId: Int, start: Float, length: Float, operation: Int): Int {
    val id = nextDataId++
    declaration(TextTransform, id, inBody(textId) || inBody(start) || inBody(length))
    wire.writeInt(id)
    wire.writeInt(textId)
    wire.writeFloat(start)
    wire.writeFloat(length)
    wire.writeInt(operation)
    return id
  }

  override fun createTextFromFloat(value: Float, before: Int, after: Int, flags: Int): Int {
    val key = TextFromFloatKey(value.toRawBits(), before, after, flags)
    return textFromFloatIds.getOrPut(key) {
      val id = nextDataId++
      declaration(TextFromFloat, id, inBody(value))
      wire.writeInt(id)
      wire.writeFloat(value)
      wire.writeInt((before shl 16) or (after and 0xffff))
      wire.writeInt(flags)
      id
    }
  }

  override fun getColorAttribute(colorId: Int, type: Short): Float {
    val id = nextDataId++
    declaration(AttributeColor, id, inBody(colorId))
    wire.writeInt(id)
    wire.writeInt(colorId)
    wire.writeShort(type.toInt())
    return Utils.asNan(id)
  }

  override fun bitmapAttribute(bitmapId: Int, type: Short): Float {
    val id = nextDataId++
    declaration(AttributeImage, id, inBody(bitmapId))
    wire.writeInt(id)
    wire.writeInt(bitmapId)
    wire.writeShort(type.toInt())
    wire.writeShort(0)
    return Utils.asNan(id)
  }

  override fun bitmapTextMeasure(
    textId: Int,
    bitmapFontId: Int,
    type: Int,
    glyphSpacing: Float,
  ): Float {
    val id = nextDataId++
    declaration(
      BitmapTextMeasure,
      id,
      inBody(textId) || inBody(bitmapFontId) || inBody(glyphSpacing),
    )
    if (glyphSpacing == 0f) {
      wire.writeInt(id)
    } else {
      wire.writeInt(id or Int.MIN_VALUE)
      wire.writeFloat(glyphSpacing)
    }
    wire.writeInt(textId)
    wire.writeInt(bitmapFontId)
    wire.writeInt(type)
    return Utils.asNan(id)
  }

  override fun addBitmapUrl(url: String, width: Int, height: Int): Int =
    bitmapUrlIds.getOrPut(url) {
      val id = nextDataId++
      writeBitmapData(
        id = id,
        type = BitmapTypePng,
        width = width,
        encoding = BitmapEncodingUrl,
        height = height,
        data = url.encodeToByteArray(),
      )
      id
    }

  /**
   * PNG bytes carried in the document itself, as an inline `BitmapData`.
   *
   * The only bitmap form the common writer can produce without a platform: an ImageBitmap reaches
   * it already encoded (see `captureCommonRemoteDocument`'s `encodePng`). Identical bytes share one
   * id.
   */
  public fun addBitmapPng(png: ByteArray, width: Int, height: Int): Int =
    inlineBitmapIds.getOrPut(png.asList()) {
      val id = nextDataId++
      writeBitmapData(
        id = id,
        type = BitmapTypePng,
        width = width,
        encoding = BitmapEncodingInline,
        height = height,
        data = png,
      )
      id
    }

  /** [addBitmapPng], bound to [name] the way [addNamedBitmapUrl] binds a URL bitmap. */
  public fun addNamedBitmapPng(name: String, png: ByteArray, width: Int, height: Int): Int {
    val id = addBitmapPng(png, width, height)
    writeNamedVariable(id, androidx.compose.remote.creation.common.NamedVariable.IMAGE_TYPE, name)
    return id
  }

  override fun addNamedBitmapUrl(name: String, url: String): Int {
    val id = addBitmapUrl(url)
    writeNamedVariable(id, androidx.compose.remote.creation.common.NamedVariable.IMAGE_TYPE, name)
    return id
  }

  override fun createBitmap(width: Int, height: Int): Int {
    val id = nextDataId++
    writeBitmapData(
      id = id,
      type = BitmapTypeRaw8888,
      width = width,
      encoding = BitmapEncodingEmpty,
      height = height,
      data = byteArrayOf(),
    )
    return id
  }

  override fun createOffscreenBitmap(componentId: Int): Int {
    val id = nextDataId++
    val payload =
      if (componentId == 0) byteArrayOf()
      else
        byteArrayOf(
          (componentId ushr 24).toByte(),
          (componentId ushr 16).toByte(),
          (componentId ushr 8).toByte(),
          componentId.toByte(),
        )
    writeBitmapData(
      id = id,
      type = BitmapTypeRaw8888,
      width = 1,
      encoding = BitmapEncodingComponentOffscreenBuffer,
      height = 1,
      data = payload,
    )
    return id
  }

  override fun addBitmapFont(glyphs: List<BitmapFontGlyph>, kerningTable: Map<String, Short>): Int {
    require(glyphs.size < 0xffff) { "Too many glyphs, the maximum is 65535" }
    require(kerningTable.size < 0xffff) { "Kerning table too big, the maximum size is 65535" }
    val id = nextDataId++
    operation(DataBitmapFont)
    wire.writeInt(id)
    wire.writeInt(glyphs.size or (if (kerningTable.isEmpty()) 0 else 1 shl 16))
    glyphs
      .sortedByDescending { it.chars.length }
      .forEach { glyph ->
        wire.writeUtf8(glyph.chars)
        wire.writeInt(glyph.bitmapId)
        wire.writeShort(glyph.marginLeft.toInt())
        wire.writeShort(glyph.marginTop.toInt())
        wire.writeShort(glyph.marginRight.toInt())
        wire.writeShort(glyph.marginBottom.toInt())
        wire.writeShort(glyph.width.toInt())
        wire.writeShort(glyph.height.toInt())
      }
    if (kerningTable.isNotEmpty()) {
      wire.writeShort(kerningTable.size)
      kerningTable.forEach { (pair, adjustment) ->
        wire.writeUtf8(pair)
        wire.writeShort(adjustment.toInt())
      }
    }
    return id
  }

  override fun matrixExpression(vararg expression: Float): Float {
    val id = nextDataId++
    declaration(MatrixExpression, id, inBody(expression))
    wire.writeInt(id)
    wire.writeInt(0)
    wire.writeInt(expression.size)
    expression.forEach(wire::writeFloat)
    return Utils.asNan(id)
  }

  override fun addColor(argb: Int): Int {
    val id = nextDataId++
    operation(ColorConstant)
    wire.writeInt(id)
    wire.writeInt(argb)
    return id
  }

  override fun addNamedColor(name: String, argb: Int): Int {
    val id = addColor(argb)
    writeNamedVariable(id, androidx.compose.remote.creation.common.NamedVariable.COLOR_TYPE, name)
    return id
  }

  override fun addColorExpression(color1: Int, color2: Int, tween: Float): Short =
    writeColorExpression(mode = 0, color1 = color1, color2 = color2, fourth = tween.toRawBits())

  override fun addColorExpression(color1: Short, color2: Short, tween: Float): Short =
    writeColorExpression(
      mode = 3,
      color1 = color1.toInt(),
      color2 = color2.toInt(),
      fourth = tween.toRawBits(),
    )

  override fun addColorExpression(
    alpha: Int,
    hue: Float,
    saturation: Float,
    value: Float,
  ): Short =
    writeColorExpression(
      mode = ColorHsvMode or (alpha shl 16),
      color1 = hue.toRawBits(),
      color2 = saturation.toRawBits(),
      fourth = value.toRawBits(),
    )

  override fun addColorExpression(alpha: Float, red: Float, green: Float, blue: Float): Short {
    val mode =
      if (alpha.isNaN()) {
        ColorIdArgbMode or (Utils.idFromNan(alpha) shl 16)
      } else {
        ColorArgbMode or ((alpha * 1024).toInt() shl 16)
      }
    return writeColorExpression(
      mode,
      red.toRawBits(),
      green.toRawBits(),
      blue.toRawBits(),
      readsInBody = inBody(alpha),
    )
  }

  override fun addText(value: String): Int = textId(value)

  override fun addThemedColor(
    group: String,
    lightId: Short,
    darkId: Short,
    lightFallback: Int,
    darkFallback: Int,
  ): Int {
    val id = nextDataId++
    val groupId = textId(group)
    operation(ColorTheme)
    wire.writeInt(id)
    wire.writeInt(groupId)
    wire.writeShort(lightId.toInt())
    wire.writeShort(darkId.toInt())
    wire.writeInt(lightFallback)
    wire.writeInt(darkFallback)
    return id
  }

  override fun addNamedString(name: String, initialValue: String): Int {
    val id = nextDataId++
    writeNamedVariable(id, androidx.compose.remote.creation.common.NamedVariable.STRING_TYPE, name)
    writeText(id, initialValue)
    return id
  }

  override fun addLong(value: Long): Int {
    val id = nextDataId++
    writeLong(id, value)
    return id
  }

  override fun addNamedLong(name: String, initialValue: Long): Int {
    val id = nextDataId++
    writeNamedVariable(id, androidx.compose.remote.creation.common.NamedVariable.LONG_TYPE, name)
    writeLong(id, initialValue)
    return id
  }

  override fun addPathData(pathData: FloatArray, winding: Int): Int =
    pathIds.getOrPut(PathKey(pathData, winding)) {
      val id = nextDataId++
      declaration(DataPath, id, inBody(pathData))
      wire.writeInt(id or (winding shl 24))
      wire.writeInt(pathData.size)
      pathData.forEach(wire::writeFloat)
      id
    }

  override fun addShader(
    sourceId: Int,
    floatUniforms: Map<String, FloatArray>,
    intUniforms: Map<String, IntArray>,
    bitmapUniforms: Map<String, Int>,
  ): Int {
    require(floatUniforms.size <= MaxShaderUniforms) { "At most $MaxShaderUniforms float uniforms" }
    require(intUniforms.size <= MaxShaderUniforms) { "At most $MaxShaderUniforms int uniforms" }
    require(bitmapUniforms.size <= MaxShaderUniforms) {
      "At most $MaxShaderUniforms bitmap uniforms"
    }
    floatUniforms.forEach { (name, values) ->
      require(values.size in 1..MaxShaderUniformValues) {
        "Float uniform '$name' has ${values.size} values"
      }
    }
    intUniforms.forEach { (name, values) ->
      require(values.size in 1..MaxShaderIntUniformValues) {
        "Int uniform '$name' has ${values.size} values"
      }
    }
    check(documentProfiles and ProfileWidgets == 0 || documentProfiles and ProfileAndroidx != 0) {
      "A widgets-profile document can't hold a runtime shader: remote-core's widgets operation maps " +
        "have no ShaderData, and a widget host can't run one. Draw a fallback instead."
    }
    shaderUsed = true
    val id = nextDataId++
    operation(DataShader)
    wire.writeInt(id)
    wire.writeInt(sourceId)
    wire.writeInt(floatUniforms.size or (intUniforms.size shl 8) or (bitmapUniforms.size shl 16))
    floatUniforms.forEach { (name, values) ->
      wire.writeUtf8(name)
      wire.writeInt(values.size)
      values.forEach(wire::writeFloat)
    }
    intUniforms.forEach { (name, values) ->
      wire.writeUtf8(name)
      wire.writeInt(values.size)
      values.forEach(wire::writeInt)
    }
    bitmapUniforms.forEach { (name, bitmapId) ->
      wire.writeUtf8(name)
      wire.writeInt(bitmapId)
    }
    return id
  }

  override fun addMesh2D(
    layout: Int,
    uCount: Int,
    vCount: Int,
    x: FloatArray?,
    y: FloatArray?,
    texU: FloatArray?,
    texV: FloatArray?,
    alpha: FloatArray?,
    red: FloatArray?,
    green: FloatArray?,
    blue: FloatArray?,
    width: FloatArray?,
    aux: Int,
    antialiasWidth: Float?,
  ): Int {
    require(uCount >= 1 && vCount >= 1) { "Mesh2D needs at least one sample along u and v" }
    require(uCount.toLong() * vCount.toLong() <= Mesh2D.MAX_GRID) {
      "Mesh2D grid ${uCount}x$vCount exceeds MAX_MESH_2D_GRID (${Mesh2D.MAX_GRID})"
    }
    val groups = arrayOf(x, y, texU, texV, alpha, red, green, blue, width)
    groups.forEach { group ->
      require(group == null || group.size <= Mesh2D.MAX_EXPRESSION_SIZE) {
        "Mesh2D expression has ${group!!.size} words; at most ${Mesh2D.MAX_EXPRESSION_SIZE}"
      }
    }
    if (antialiasWidth != null) {
      require(listOf(alpha, red, green, blue).any { it != null && it.isNotEmpty() }) {
        "Mesh2D antialiasing needs vertex colours; set a colour channel"
      }
      require(antialiasWidth.isNaN() || (antialiasWidth >= 0f && antialiasWidth.isFinite())) {
        "Mesh2D antialias width must be finite and not negative: $antialiasWidth"
      }
    }
    mesh2DUsed = true
    val id = nextDataId++
    operation(AddMesh2DOp)
    wire.writeInt(id)
    wire.writeInt(Mesh2D.TYPE_EXPRESSION)
    wire.writeInt(layout)
    wire.writeInt(uCount)
    wire.writeInt(vCount)
    wire.writeInt(if (antialiasWidth != null) Mesh2D.FLAG_ANTIALIAS else 0)
    wire.writeInt(aux)
    groups.forEach { group ->
      // An absent channel is a zero length, not a missing field.
      wire.writeInt(group?.size ?: 0)
      group?.forEach(wire::writeFloat)
    }
    if (antialiasWidth != null) wire.writeFloat(antialiasWidth)
    return id
  }

  override fun addMesh2DValues(
    indices: IntArray,
    vertices: FloatArray,
    uv: FloatArray?,
    colors: IntArray?,
    halfFloat: Boolean,
    layout: Int,
    uCount: Int,
    vCount: Int,
  ): Int {
    require(vertices.size / 2 <= Mesh2D.MAX_VERTICES) { "Mesh2D has too many vertices" }
    require(indices.size <= Mesh2D.MAX_INDICES) { "Mesh2D has too many indices" }
    require(uv == null || uv.size / 2 <= Mesh2D.MAX_VERTICES) { "Mesh2D has too many uv pairs" }
    require(colors == null || colors.size <= Mesh2D.MAX_VERTICES) { "Mesh2D has too many colours" }
    require(indices.all { it in 0..0xffff }) { "Mesh2D indices are 16-bit" }
    mesh2DUsed = true
    val id = nextDataId++
    operation(AddMesh2DOp)
    wire.writeInt(id)
    wire.writeInt(if (halfFloat) Mesh2D.TYPE_F16_VALUES else Mesh2D.TYPE_VALUES)
    wire.writeInt(layout)
    wire.writeInt(uCount)
    wire.writeInt(vCount)
    wire.writeInt(0)
    wire.writeInt(0)
    wire.writeInt(indices.size)
    indices.forEach { wire.writeShort(it) }
    val safeUv = uv ?: FloatArray(0)
    val safeColors = colors ?: IntArray(0)
    wire.writeInt(vertices.size)
    wire.writeInt(safeUv.size)
    wire.writeInt(safeColors.size)
    if (halfFloat) {
      vertices.forEach { wire.writeShort(floatToHalf(it)) }
      safeUv.forEach { wire.writeShort(floatToHalf(it)) }
    } else {
      vertices.forEach(wire::writeFloat)
      safeUv.forEach(wire::writeFloat)
    }
    safeColors.forEach(wire::writeInt)
    return id
  }

  override fun drawMesh2D(meshId: Int, imageId: Int, blend: Int) {
    mesh2DUsed = true
    operation(DrawMesh2DOp)
    wire.writeInt(meshId)
    wire.writeInt(blend)
    wire.writeInt(imageId)
  }

  override fun matrixFromMesh2D(meshId: Int, u: Float, v: Float, flags: Int) {
    mesh2DUsed = true
    operation(MatrixFromMesh2DOp)
    wire.writeInt(meshId)
    wire.writeFloat(u)
    wire.writeFloat(v)
    wire.writeInt(flags)
  }

  override fun applyPaint(paint: PaintBundle) {
    val values = paint.toIntArray()
    operation(PaintValues)
    wire.writeInt(values.size)
    values.forEach(wire::writeInt)
  }

  override fun checkAndClearForceSendingNewPaint(): Boolean {
    val result = paintResetPending
    paintResetPending = false
    return result
  }

  override fun save(): Unit = operation(MatrixSave)

  override fun restore(): Unit = operation(MatrixRestore)

  override fun translate(dx: Float, dy: Float): Unit = floats(MatrixTranslate, dx, dy)

  override fun scale(scaleX: Float, scaleY: Float, centerX: Float, centerY: Float): Unit =
    floats(MatrixScale, scaleX, scaleY, centerX, centerY)

  override fun rotate(angle: Float, centerX: Float, centerY: Float): Unit =
    floats(MatrixRotate, angle, centerX, centerY)

  override fun skew(skewX: Float, skewY: Float): Unit = floats(MatrixSkew, skewX, skewY)

  override fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Unit =
    floats(ClipRect, left, top, right, bottom)

  override fun drawRect(left: Float, top: Float, right: Float, bottom: Float): Unit =
    floats(DrawRect, left, top, right, bottom)

  override fun drawRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radiusX: Float,
    radiusY: Float,
  ): Unit = floats(DrawRoundRect, left, top, right, bottom, radiusX, radiusY)

  override fun drawCircle(centerX: Float, centerY: Float, radius: Float): Unit =
    floats(DrawCircle, centerX, centerY, radius)

  override fun drawOval(left: Float, top: Float, right: Float, bottom: Float): Unit =
    floats(DrawOval, left, top, right, bottom)

  override fun drawArc(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    startAngle: Float,
    sweepAngle: Float,
  ): Unit = floats(DrawArc, left, top, right, bottom, startAngle, sweepAngle)

  override fun drawSector(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    startAngle: Float,
    sweepAngle: Float,
  ): Unit = floats(DrawSector, left, top, right, bottom, startAngle, sweepAngle)

  override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float): Unit =
    floats(DrawLine, x1, y1, x2, y2)

  override fun drawPath(pathId: Int): Unit = intOperation(DrawPath, pathId)

  override fun drawBitmap(
    imageId: Int,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    contentDescriptionId: Int,
  ) {
    operation(DrawBitmap)
    wire.writeInt(imageId)
    wire.writeFloat(left)
    wire.writeFloat(top)
    wire.writeFloat(right)
    wire.writeFloat(bottom)
    wire.writeInt(contentDescriptionId)
  }

  override fun drawComponentContent(): Unit = operation(DrawContent)

  override fun addClipPath(pathId: Int): Unit = intOperation(ClipPath, pathId)

  override fun drawTweenPath(
    path1Id: Int,
    path2Id: Int,
    tween: Float,
    start: Float,
    stop: Float,
  ) {
    operation(DrawTweenPath)
    wire.writeInt(path1Id)
    wire.writeInt(path2Id)
    floatsWithoutOpcode(tween, start, stop)
  }

  override fun drawTextOnPath(
    textId: Int,
    pathId: Int,
    horizontalOffset: Float,
    verticalOffset: Float,
  ) {
    operation(DrawTextOnPath)
    wire.writeInt(textId)
    wire.writeInt(pathId)
    // This is the protocol order, despite the public API naming horizontal first.
    wire.writeFloat(verticalOffset)
    wire.writeFloat(horizontalOffset)
  }

  override fun drawBitmapFontTextRun(
    textId: Int,
    bitmapFontId: Int,
    start: Int,
    end: Int,
    x: Float,
    y: Float,
    glyphSpacing: Float,
  ) {
    operation(DrawBitmapFontTextRun)
    writeTextIdWithGlyphSpacing(textId, glyphSpacing)
    wire.writeInt(bitmapFontId)
    wire.writeInt(start)
    wire.writeInt(end)
    wire.writeFloat(x)
    wire.writeFloat(y)
  }

  override fun drawBitmapFontTextRunOnPath(
    textId: Int,
    bitmapFontId: Int,
    pathId: Int,
    start: Int,
    end: Int,
    yAdj: Float,
    glyphSpacing: Float,
  ) {
    bitmapFontOnPathUsed = true
    operation(DrawBitmapFontTextRunOnPath)
    writeTextIdWithGlyphSpacing(textId, glyphSpacing)
    wire.writeInt(bitmapFontId)
    wire.writeInt(pathId)
    wire.writeInt(start)
    wire.writeInt(end)
    wire.writeFloat(yAdj)
  }

  /**
   * As `DrawBitmapFontText.apply` and `DrawBitmapFontTextOnPath.apply`: the text id alone, or with
   * its sign bit set and followed by a glyph spacing that is not zero.
   */
  private fun writeTextIdWithGlyphSpacing(textId: Int, glyphSpacing: Float) {
    if (glyphSpacing == 0f) {
      wire.writeInt(textId)
    } else {
      wire.writeInt(textId or Int.MIN_VALUE)
      wire.writeFloat(glyphSpacing)
    }
  }

  override fun textAttribute(textId: Int, attribute: Short): Float {
    val id = nextDataId++
    operation(AttributeText)
    wire.writeInt(id)
    wire.writeInt(textId)
    wire.writeShort(attribute.toInt())
    wire.writeShort(0)
    return Utils.asNan(id)
  }

  override fun drawTextRun(
    textId: Int,
    start: Int,
    end: Int,
    contextStart: Int,
    contextEnd: Int,
    x: Float,
    y: Float,
    isRtl: Boolean,
  ) {
    operation(DrawTextRun)
    wire.writeInt(textId)
    wire.writeInt(start)
    wire.writeInt(end)
    wire.writeInt(contextStart)
    wire.writeInt(contextEnd)
    wire.writeFloat(x)
    wire.writeFloat(y)
    wire.writeByte(if (isRtl) 1 else 0)
  }

  override fun drawTextAnchored(
    textId: Int,
    anchorX: Float,
    anchorY: Float,
    panX: Float,
    panY: Float,
    flags: Int,
  ) {
    operation(DrawTextAnchored)
    wire.writeInt(textId)
    wire.writeFloat(anchorX)
    wire.writeFloat(anchorY)
    wire.writeFloat(panX)
    wire.writeFloat(panY)
    wire.writeInt(flags)
  }

  override fun drawTextOnCircle(
    textId: Int,
    centerX: Float,
    centerY: Float,
    radius: Float,
    startAngle: Float,
    warpRadiusOffset: Float,
    alignment: DrawTextOnCircle.Alignment,
    placement: DrawTextOnCircle.Placement,
  ) {
    operation(DrawTextOnCircleOp)
    wire.writeInt(textId)
    floatsWithoutOpcode(centerX, centerY, radius, startAngle, warpRadiusOffset)
    wire.writeByte(alignment.ordinal)
    wire.writeByte(placement.ordinal)
  }

  override fun drawScaledBitmap(
    imageId: Int,
    srcLeft: Float,
    srcTop: Float,
    srcRight: Float,
    srcBottom: Float,
    dstLeft: Float,
    dstTop: Float,
    dstRight: Float,
    dstBottom: Float,
    scaleType: Int,
    scaleFactor: Float,
    contentDescription: String?,
  ) {
    // As upstream's RemoteComposeWriter: no description is id 0 rather than an empty TextData.
    val descriptionId = contentDescription?.let(::textId) ?: 0
    operation(DrawBitmapScaled)
    wire.writeInt(imageId)
    listOf(srcLeft, srcTop, srcRight, srcBottom, dstLeft, dstTop, dstRight, dstBottom)
      .forEach(wire::writeFloat)
    wire.writeInt(scaleType)
    wire.writeFloat(scaleFactor)
    wire.writeInt(descriptionId)
  }

  private fun writeHeader(
    width: Int,
    height: Int,
    profiles: Int,
    densityBehavior: Int,
    minorVersion: Int = MinorVersion,
  ) {
    // Header may be encoded into a separate buffer at export; do not switch back to the body.
    wire.writeByte(Header)
    wire.writeInt(MagicNumber or MajorVersion)
    wire.writeInt(minorVersion)
    wire.writeInt(PatchVersion)
    wire.writeInt(5)
    headerInt(HeaderWidth, width)
    headerInt(HeaderHeight, height)
    headerString(HeaderContentDescription, "")
    headerInt(HeaderProfiles, profiles)
    headerInt(HeaderDensityBehavior, densityBehavior)
  }

  private fun headerInt(tag: Int, value: Int) {
    wire.writeShort(tag)
    wire.writeShort(IntSize)
    wire.writeInt(value)
  }

  private fun headerString(tag: Int, value: String) {
    val encoded = value.encodeToByteArray()
    wire.writeShort(tag or HeaderStringType)
    wire.writeShort(encoded.size + IntSize)
    wire.writeInt(encoded.size)
    wire.writeBytes(encoded)
  }

  private fun contentSection(requested: Int = -1) {
    operation(LayoutContent)
    wire.writeInt(resolveComponentId(requested))
    // As `RemoteComposeWriter.addContentStart` does: a player begins each component with a fresh
    // paint, so the first paint inside one has to be written in full, not as a delta against the
    // previous component's.
    paintResetPending = true
  }

  private fun containerEnd(): Unit = operation(ContainerEnd)

  private fun componentId(): Int {
    lastComponentId = --nextComponentId
    return lastComponentId
  }

  private fun resolveComponentId(requested: Int): Int {
    lastComponentId = if (requested == -1) --nextComponentId else requested
    return lastComponentId
  }

  private fun startLayout(
    opcode: Int,
    modifier: RemoteModifierData,
    horizontal: Int,
    vertical: Int,
  ) {
    operation(opcode)
    wire.writeInt(resolveComponentId(modifier.componentId))
    wire.writeInt(-1)
    wire.writeInt(horizontal)
    wire.writeInt(vertical)
    modifier.writeTo(this)
    contentSection(modifier.contentComponentId)
  }

  private fun startLinearLayout(
    opcode: Int,
    modifier: RemoteModifierData,
    horizontal: Int,
    vertical: Int,
  ) {
    operation(opcode)
    wire.writeInt(resolveComponentId(modifier.componentId))
    wire.writeInt(-1)
    wire.writeInt(horizontal)
    wire.writeInt(vertical)
    wire.writeFloat(modifier.spacedBy)
    modifier.writeTo(this)
    contentSection(modifier.contentComponentId)
  }

  private fun endLayout() {
    containerEnd()
    containerEnd()
  }

  private fun componentValue(type: Int, componentId: Int): Float {
    val key = pairKey(componentId, type)
    val id =
      componentValueIds.getOrPut(key) {
        val allocated = nextDataId++
        operation(ComponentValue)
        wire.writeInt(type)
        wire.writeInt(componentId)
        wire.writeInt(allocated)
        allocated
      }
    return Utils.asNan(id)
  }

  private fun operation(opcode: Int) {
    wire =
      when {
        functionScopes.isNotEmpty() -> declarationBuffer
        opcode in DeclarationOpcodes -> declarationBuffer
        else -> bodyBuffer
      }
    wire.writeByte(opcode)
  }

  /**
   * Starts a declaration of [id] that reads other values. [inBody] says one of them belongs to an
   * open pattern, for-each or loop body ([bodyScopes]): then the declaration is written in the body
   * and [id] belongs to the body too. Otherwise it goes ahead of the layout.
   */
  private fun declaration(opcode: Int, id: Int, inBody: Boolean) {
    if (!inBody || functionScopes.isNotEmpty()) return operation(opcode)
    bodyScopes.last() += id
    wire = bodyBuffer
    wire.writeByte(opcode)
  }

  private fun inBody(id: Int): Boolean = bodyScopes.any { id in it }

  /** Whether [value] is the id of something that belongs to an open body. */
  private fun inBody(value: Float): Boolean = value.isNaN() && inBody(Utils.idFromNan(value))

  private fun inBody(values: FloatArray?): Boolean = values != null && values.any(::inBody)

  /** Forgets what the innermost body declared, so nothing after it reads an id it owned. */
  private fun endBodyScope() {
    val owned = bodyScopes.removeLast()
    dedupCaches().forEach { cache -> cache.values.removeAll { it in owned } }
  }

  private fun dedupCaches(): List<MutableMap<*, Int>> =
    listOf(
      textIds,
      pathIds,
      integerIds,
      floatArrayIds,
      idListIds,
      floatExpressionIds,
      integerExpressionIds,
      textLengthIds,
      textLookupIds,
      textLookupIntIds,
      textFromFloatIds,
      bitmapUrlIds,
      inlineBitmapIds,
      componentValueIds,
    )

  /** Returns what forgets every dedup cache entry added after this call. */
  private fun snapshotCaches(): () -> Unit {
    val caches = dedupCaches()
    val saved = caches.map { it.keys.toSet() }
    return {
      caches.zip(saved).forEach { (cache, keys) ->
        val iterator = cache.keys.iterator()
        while (iterator.hasNext()) if (iterator.next() !in keys) iterator.remove()
      }
    }
  }

  private fun intOperation(opcode: Int, value: Int) {
    operation(opcode)
    wire.writeInt(value)
  }

  private fun floats(opcode: Int, vararg values: Float) {
    operation(opcode)
    floatsWithoutOpcode(*values)
  }

  private fun floatsWithoutOpcode(vararg values: Float) = values.forEach(wire::writeFloat)

  private fun FloatArray.toRawBits(): IntArray = IntArray(size) { this[it].toRawBits() }

  private fun pairKey(first: Int, second: Int): Long =
    (first.toLong() shl 32) or (second.toLong() and 0xffffffffL)

  private fun textId(value: String): Int =
    textIds.getOrPut(value) {
      val id = nextDataId++
      writeText(id, value)
      id
    }

  private fun writeText(id: Int, value: String) {
    operation(DataText)
    wire.writeInt(id)
    wire.writeUtf8(value)
  }

  private fun writeLong(id: Int, value: Long) {
    operation(DataLong)
    wire.writeInt(id)
    wire.writeLong(value)
  }

  private fun writeInteger(id: Int, value: Int) {
    operation(DataInt)
    wire.writeInt(id)
    wire.writeInt(value)
  }

  private fun writeNamedVariable(id: Int, type: Int, name: String) {
    operation(NamedVariable)
    wire.writeInt(id)
    wire.writeInt(type)
    wire.writeUtf8(name)
  }

  private fun writeBitmapData(
    id: Int,
    type: Int,
    width: Int,
    encoding: Int,
    height: Int,
    data: ByteArray,
  ) {
    operation(DataBitmap)
    wire.writeInt(id)
    wire.writeInt((type shl 16) or (width and 0xffff))
    wire.writeInt((encoding shl 16) or (height and 0xffff))
    wire.writeInt(data.size)
    wire.writeBytes(data)
  }

  private fun writeColorExpression(
    mode: Int,
    color1: Int,
    color2: Int,
    fourth: Int,
    readsInBody: Boolean = false,
  ): Short {
    val id = nextDataId++
    // An argument is a colour or float id, a colour, or a float's bits, by mode.
    val reads =
      readsInBody ||
        intArrayOf(color1, color2, fourth).any { inBody(it) || inBody(Float.fromBits(it)) }
    declaration(ColorExpression, id, reads)
    wire.writeInt(id)
    wire.writeInt(mode)
    wire.writeInt(color1)
    wire.writeInt(color2)
    wire.writeInt(fourth)
    return id.toShort()
  }

  public companion object {
    public const val DensityBehaviorDp: Int = 2
    public const val HorizontalStart: Int = 1
    public const val VerticalTop: Int = 4

    private const val Header = 0
    private const val PaintValues = 40
    private const val DataBitmap = 101
    private const val DataText = 102
    private const val DataFloat = 80
    private const val AnimatedFloat = 81
    private const val LayoutRoot = 200
    private const val LayoutContent = 201
    private const val LayoutBox = 202
    private const val LayoutRow = 203
    private const val LayoutColumn = 204
    private const val LayoutCanvas = 205
    private const val LayoutCollapsibleRow = 230
    private const val LayoutCollapsibleColumn = 233
    private const val CanvasOperations = 173
    private const val LayoutFitBox = 176
    private const val LayoutState = 217
    private const val LayoutFlow = 240
    private const val LayoutImage = 234
    private const val LayoutCustom = 93
    private const val ModifierWidth = 16
    private const val ModifierHeight = 67
    private const val ModifierBackground = 55
    private const val ModifierPadding = 58
    private const val ModifierClipRect = 108
    private const val ModifierRoundedClipRect = 54
    private const val ModifierWidthIn = 231
    private const val ModifierHeightIn = 232
    private const val ModifierDrawContent = 174
    private const val ModifierOffset = 221
    private const val ModifierZIndex = 223
    private const val ModifierRipple = 229
    private const val MacroCall = 247
    private const val ModifierClick = 59
    private const val ModifierMultiClick = 83
    private const val ModifierTouchDown = 219
    private const val ModifierTouchUp = 220
    private const val ModifierTouchCancel = 225
    private const val HostAction = 209
    private const val HostNamedAction = 210
    private const val ValueIntegerChange = 212
    private const val ValueStringChange = 213
    private const val HostMetadataAction = 216
    private const val ValueIntegerExpressionChange = 218
    private const val ValueFloatChange = 222
    private const val ValueFloatExpressionChange = 227
    private const val AccessibilitySemantics = 250
    private const val ModifierGraphicsLayer = 224
    private const val ModifierBorder = 107
    private const val BorderWireVersion = 1
    private const val ModifierVisibility = 211
    private const val ModifierMarquee = 228
    private const val ModifierCollapsiblePriority = 235
    private const val ModifierAlignBy = 237
    private const val AnimationSpec = 14
    /** `AnimationSpec.ANIMATION.CUSTOM.ordinal()`. */
    private const val AnimationCustom = 8
    private const val TouchExpression = 157
    private const val ModifierScroll = 226
    private const val PatternForEach = 244
    private const val PatternDefine = 246
    private const val PatternInflation = 247
    private const val ConditionalOperations = 178
    private const val DrawToBitmap = 190
    private const val FunctionCall = 166
    private const val FunctionDefine = 168
    private const val LoopStart = 215
    private const val ContainerEnd = 214
    private const val CoreText = 239
    private const val ClipRect = 39
    private const val ClipPath = 38
    private const val DrawRect = 42
    private const val DrawTextRun = 43
    private const val DrawCircle = 46
    private const val DrawLine = 47
    private const val DrawRoundRect = 51
    private const val DrawSector = 52
    private const val DrawTextOnPath = 53
    private const val DrawBitmapFontTextRun = 48
    private const val DrawBitmapFontTextRunOnPath = 49
    private const val AttributeText = 170
    private const val DrawOval = 56
    private const val DrawTextOnCircleOp = 57
    private const val DataPath = 123
    private const val DataShader = 45
    private const val AddMesh2DOp = 104
    private const val DrawMesh2DOp = 105
    private const val MatrixFromMesh2DOp = 106
    private const val MaxShaderUniforms = 255
    private const val MaxShaderUniformValues = 16
    private const val MaxShaderIntUniformValues = 4
    private const val DrawPath = 124
    private const val DrawBitmap = 44
    private const val ColorTheme = 196
    private const val DrawContent = 139
    private const val DrawTweenPath = 125
    private const val MatrixScale = 126
    private const val MatrixTranslate = 127
    private const val MatrixSkew = 128
    private const val MatrixRotate = 129
    private const val MatrixSave = 130
    private const val MatrixRestore = 131
    private const val DrawTextAnchored = 133
    private const val ColorExpression = 134
    private const val TextFromFloat = 135
    private const val TextMerge = 136
    private const val NamedVariable = 137
    private const val ColorConstant = 138
    private const val DataInt = 140
    private const val IntegerExpression = 144
    private const val IdList = 146
    private const val FloatList = 147
    private const val DataLong = 148
    private const val DrawBitmapScaled = 149
    private const val ComponentValue = 150
    private const val DrawArc = 152
    private const val TextLookup = 151
    private const val TextLookupInt = 153
    private const val TextLength = 156
    private const val AttributeImage = 171
    private const val AttributeTime = 172
    private const val AttributeColor = 180
    private const val TextSubtext = 182
    private const val BitmapTextMeasure = 183
    private const val MatrixExpression = 187
    private const val IdLookup = 192
    private const val DynamicFloatList = 197
    private const val UpdateDynamicFloatList = 198
    private const val TextTransform = 199
    private const val DataBitmapFont = 167

    private val DeclarationOpcodes =
      setOf(
        DataBitmap,
        DataText,
        DataFloat,
        AnimatedFloat,
        DataPath,
        DataShader,
        ColorExpression,
        TextFromFloat,
        TextMerge,
        NamedVariable,
        ColorConstant,
        DataInt,
        IntegerExpression,
        IdList,
        FloatList,
        DataLong,
        ComponentValue,
        TextLookup,
        TextLookupInt,
        TextLength,
        DataBitmapFont,
        // A function is defined ahead of the layout; see createFloatFunction.
        FunctionDefine,
        AttributeImage,
        AttributeTime,
        AttributeColor,
        TextSubtext,
        BitmapTextMeasure,
        MatrixExpression,
        IdLookup,
        DynamicFloatList,
        UpdateDynamicFloatList,
        TextTransform,
      )

    private const val BitmapTypePng = 1
    private const val BitmapTypeRaw8888 = 3
    private const val BitmapEncodingInline = 0
    private const val BitmapEncodingUrl = 1
    private const val BitmapEncodingEmpty = 3
    private const val BitmapEncodingComponentOffscreenBuffer = 4
    private const val ColorHsvMode = 4
    private const val ColorArgbMode = 5
    private const val ColorIdArgbMode = 6
    private const val ComponentWidth = 0
    private const val ComponentHeight = 1
    private const val ComponentPosX = 2
    private const val ComponentPosY = 3
    private const val ComponentPosRootX = 4
    private const val ComponentPosRootY = 5

    private const val MagicNumber = 0x048C0000
    private const val MajorVersion = 1
    private const val MinorVersion = 1
    private const val CustomMinorVersion = 2
    private const val ProfileAndroidxExperimental = 0x201
    private const val ProfileAndroidx = 0x200
    private const val ProfileExperimental = 0x1
    private const val ProfileWidgets = 0x100
    private const val PatchVersion = 0
    private const val IntSize = 4
    private const val HeaderWidth = 5
    private const val HeaderHeight = 6
    private const val HeaderContentDescription = 9
    private const val HeaderProfiles = 14
    private const val HeaderDensityBehavior = 27
    private const val HeaderStringType = 3 shl 10
    private const val TextParameterId = 1
    private const val TextParameterFontSize = 5
    private const val IntegerIdOffset = 0x100000000L
  }
}

private class PathKey(pathData: FloatArray, private val winding: Int) {
  private val bits = IntArray(pathData.size) { pathData[it].toRawBits() }

  override fun equals(other: Any?): Boolean =
    other is PathKey && winding == other.winding && bits.contentEquals(other.bits)

  override fun hashCode(): Int = 31 * bits.contentHashCode() + winding
}

private class IntArrayKey(values: IntArray) {
  private val values = values.copyOf()

  override fun equals(other: Any?): Boolean =
    other is IntArrayKey && values.contentEquals(other.values)

  override fun hashCode(): Int = values.contentHashCode()
}

private class LongArrayKey(values: LongArray) {
  private val values = values.copyOf()

  override fun equals(other: Any?): Boolean =
    other is LongArrayKey && values.contentEquals(other.values)

  override fun hashCode(): Int = values.contentHashCode()
}

private data class TextFromFloatKey(
  val valueBits: Int,
  val before: Int,
  val after: Int,
  val flags: Int,
)

/** `Mesh2DGenerator.floatToHalf`: an IEEE 754 half, rounded to nearest, in the low 16 bits. */
private fun floatToHalf(value: Float): Int {
  val bits = value.toRawBits()
  val sign = (bits ushr 16) and 0x8000
  val exponent = (bits ushr 23) and 0xff
  var mantissa = bits and 0x7fffff
  if (exponent == 0xff) return sign or 0x7c00 or (if (mantissa != 0) 0x200 else 0)
  val unbiased = exponent - 127 + 15
  if (unbiased >= 0x1f) return sign or 0x7c00
  if (unbiased <= 0) {
    if (unbiased < -10) return sign
    mantissa = mantissa or 0x800000
    val shift = 14 - unbiased
    var half = mantissa ushr shift
    if ((mantissa ushr (shift - 1)) and 0x1 != 0) half++
    return sign or half
  }
  var half = (unbiased shl 10) or (mantissa ushr 13)
  if (mantissa and 0x1000 != 0) half++
  return sign or half
}

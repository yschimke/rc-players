package ee.schimke.composeai.rcplayer.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.offset
import androidx.compose.ui.zIndex
import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcAlignByModifier
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcClickArea
import ee.schimke.composeai.rcplayer.protocol.RcClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcCollapsiblePriorityModifier
import ee.schimke.composeai.rcplayer.protocol.RcColorAttribute
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcConditionalOperations
import ee.schimke.composeai.rcplayer.protocol.RcDataMapLookup
import ee.schimke.composeai.rcplayer.protocol.RcDebugMessage
import ee.schimke.composeai.rcplayer.protocol.RcDimensionConstraintsModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw3
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDraw6
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmap
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRun
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRunOnPath
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapInt
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapScaled
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapTextAnchored
import ee.schimke.composeai.rcplayer.protocol.RcDrawText
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextAnchored
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextOnCircle
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextOnPath
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcDrawTweenPath
import ee.schimke.composeai.rcplayer.protocol.RcDynamicFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionCall
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionDefine
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
import ee.schimke.composeai.rcplayer.protocol.RcHapticFeedback
import ee.schimke.composeai.rcplayer.protocol.RcHapticType
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightInModifier
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIdLookup
import ee.schimke.composeai.rcplayer.protocol.RcIdOperation
import ee.schimke.composeai.rcplayer.protocol.RcImageAttribute
import ee.schimke.composeai.rcplayer.protocol.RcImpulseProcess
import ee.schimke.composeai.rcplayer.protocol.RcImpulseStart
import ee.schimke.composeai.rcplayer.protocol.RcIntegerExpression
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.protocol.RcLayoutCompute
import ee.schimke.composeai.rcplayer.protocol.RcLoopOperation
import ee.schimke.composeai.rcplayer.protocol.RcMarqueeModifier
import ee.schimke.composeai.rcplayer.protocol.RcMatrixExpression
import ee.schimke.composeai.rcplayer.protocol.RcMatrixFromPath
import ee.schimke.composeai.rcplayer.protocol.RcMatrixVectorMath
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcParticleCompare
import ee.schimke.composeai.rcplayer.protocol.RcParticleLoop
import ee.schimke.composeai.rcplayer.protocol.RcPathAppend
import ee.schimke.composeai.rcplayer.protocol.RcPathCombine
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathCreate
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcPathExpression
import ee.schimke.composeai.rcplayer.protocol.RcPathTween
import ee.schimke.composeai.rcplayer.protocol.RcRippleModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootContentBehavior
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcScrollModifier
import ee.schimke.composeai.rcplayer.protocol.RcShaderData
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextAttribute
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextLength
import ee.schimke.composeai.rcplayer.protocol.RcTextLookup
import ee.schimke.composeai.rcplayer.protocol.RcTextLookupInt
import ee.schimke.composeai.rcplayer.protocol.RcTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcTextSubtext
import ee.schimke.composeai.rcplayer.protocol.RcTextTransform
import ee.schimke.composeai.rcplayer.protocol.RcTheme
import ee.schimke.composeai.rcplayer.protocol.RcTimeAttribute
import ee.schimke.composeai.rcplayer.protocol.RcTransform2
import ee.schimke.composeai.rcplayer.protocol.RcUpdateDynamicFloatList
import ee.schimke.composeai.rcplayer.protocol.RcWakeIn
import ee.schimke.composeai.rcplayer.protocol.RcWidthInModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.protocol.RcZIndexModifier
import ee.schimke.composeai.rcplayer.protocol.referencesAnyOf
import ee.schimke.composeai.rcplayer.protocol.referencesContinuousSystemVariable
import ee.schimke.composeai.rcplayer.protocol.referencesMovingSystemVariable
import ee.schimke.composeai.rcplayer.runtime.RcClickActionBlock
import ee.schimke.composeai.rcplayer.runtime.RcClickActionType
import ee.schimke.composeai.rcplayer.runtime.RcComponentGeometry
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcImpulsePhase
import ee.schimke.composeai.rcplayer.runtime.RcLayoutModifiers
import ee.schimke.composeai.rcplayer.runtime.RcLayoutNode
import ee.schimke.composeai.rcplayer.runtime.RcLayoutTree
import ee.schimke.composeai.rcplayer.runtime.RcLinkedNode
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEffect
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import ee.schimke.composeai.rcplayer.runtime.RcScrollBlock
import ee.schimke.composeai.rcplayer.runtime.RcTouchActionBlock
import ee.schimke.composeai.rcplayer.runtime.RcTouchActionType
import ee.schimke.composeai.rcplayer.runtime.RcTouchExpressionRuntime
import ee.schimke.composeai.rcplayer.trace.RcTraceCategory
import ee.schimke.composeai.rcplayer.trace.rcTrace
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
public fun RcComposePlayer(
  bytes: ByteArray,
  modifier: Modifier = Modifier,
  theme: RcPlayerTheme = RcPlayerTheme.System,
  namedValues: SnapshotStateMap<String, RcNamedValue> = rememberRcNamedValues(),
  onEvent: (RcPlayerEvent) -> Unit = {},
  typefaces: RcTypefaceLoader = RcTypefaceLoader.Default,
  systemColors: (name: String) -> Color? = rememberRcPlatformSystemColors(),
) {
  RcComposePlayer(
    bytes = bytes,
    modifier = modifier,
    theme = theme,
    namedValues = namedValues,
    onEvent = onEvent,
    typefaces = typefaces,
    systemColors = systemColors,
    customComponents = RcCustomComponentRegistry.Empty,
  )
}

@Composable
public fun RcComposePlayer(
  bytes: ByteArray,
  modifier: Modifier = Modifier,
  theme: RcPlayerTheme = RcPlayerTheme.System,
  namedValues: SnapshotStateMap<String, RcNamedValue> = rememberRcNamedValues(),
  onEvent: (RcPlayerEvent) -> Unit = {},
  typefaces: RcTypefaceLoader = RcTypefaceLoader.Default,
  systemColors: (name: String) -> Color? = rememberRcPlatformSystemColors(),
  customComponents: RcCustomComponentRegistry,
) {
  val document = remember(bytes) { decodeCmpDocument(bytes) }
  RcComposePlayer(
    document,
    modifier,
    theme,
    namedValues,
    onEvent,
    typefaces,
    systemColors,
    customComponents,
  )
}

/** CMP implements the opt-in operation family, so `Skip` must observe the experimental bit. */
internal fun decodeCmpDocument(bytes: ByteArray): RcDocument = RcDocumentCodec.decode(bytes)

@Composable
public fun RcComposePlayer(
  document: RcDocument,
  modifier: Modifier = Modifier,
  theme: RcPlayerTheme = RcPlayerTheme.System,
  namedValues: SnapshotStateMap<String, RcNamedValue> = rememberRcNamedValues(),
  onEvent: (RcPlayerEvent) -> Unit = {},
  typefaces: RcTypefaceLoader = RcTypefaceLoader.Default,
  systemColors: (name: String) -> Color? = rememberRcPlatformSystemColors(),
) {
  RcComposePlayer(
    document = document,
    modifier = modifier,
    theme = theme,
    namedValues = namedValues,
    onEvent = onEvent,
    typefaces = typefaces,
    systemColors = systemColors,
    customComponents = RcCustomComponentRegistry.Empty,
  )
}

@Composable
public fun RcComposePlayer(
  document: RcDocument,
  modifier: Modifier = Modifier,
  theme: RcPlayerTheme = RcPlayerTheme.System,
  namedValues: SnapshotStateMap<String, RcNamedValue> = rememberRcNamedValues(),
  onEvent: (RcPlayerEvent) -> Unit = {},
  typefaces: RcTypefaceLoader = RcTypefaceLoader.Default,
  systemColors: (name: String) -> Color? = rememberRcPlatformSystemColors(),
  customComponents: RcCustomComponentRegistry,
) {
  // Resolve once, at the only place that can: `RcPlayerTheme.System` is a question for the host,
  // and everything below this point — section gating, and every `ColorTheme` selection — needs a
  // concrete answer. See [rcResolveSystemTheme] for why leaving it unresolved is not a neutral
  // default.
  RcComposePlayerResolved(
    document,
    modifier,
    theme.resolve(),
    namedValues,
    onEvent,
    typefaces,
    systemColors,
    customComponents,
  )
}

@Composable
private fun RcComposePlayerResolved(
  document: RcDocument,
  modifier: Modifier,
  theme: Int,
  namedValues: SnapshotStateMap<String, RcNamedValue>,
  onEvent: (RcPlayerEvent) -> Unit,
  typefaces: RcTypefaceLoader,
  systemColors: (name: String) -> Color?,
  customComponents: RcCustomComponentRegistry,
) {
  val latestEventSink by rememberUpdatedState(onEvent)
  val latestSystemColors by rememberUpdatedState(systemColors)
  val latestTimeSource by rememberUpdatedState(LocalRcTimeSource.current)
  val latestHapticFeedback by rememberUpdatedState(LocalHapticFeedback.current)
  val soundHost = LocalRcSoundHost.current
  val soundDispatcher = remember(document) { RcSoundHostDispatcher(soundHost) }
  SideEffect { soundDispatcher.updateHost(soundHost) }
  var invalidationVersion by remember { mutableIntStateOf(0) }
  var wakeIntervalSeconds by remember(document) { mutableStateOf<Float?>(null) }
  var nextFrameRequestVersion by remember(document) { mutableIntStateOf(0) }
  // Keyed on the document alone. `namedValues` is deliberately *not* in the key: a host rebuilding
  // an equal map in a parent recomposition would otherwise construct a fresh `RcPlayerState` and
  // discard running animation timelines, mid-drag touch state and every variable a document action
  // had changed — the same hazard the `systemColors` comment below describes, on the one API a host
  // uses to drive a live document. Changes are applied incrementally instead; see the
  // `LaunchedEffect` under this block.
  // The exact map handed to the constructor, kept so the bridge below knows what the state
  // actually holds rather than re-reading the holder. Computed in its own `remember` immediately
  // before the state's, so both see the same snapshot of `namedValues`.
  val seededNamedValues = remember(document) { namedValues.toMap() }
  val state =
    remember(document) {
      RcPlayerState(
        document,
        // Seeded once, from whatever the map holds when this document is first composed.
        seededNamedValues,
        eventSink = { latestEventSink(it) },
        onInvalidated = { invalidationVersion += 1 },
        effectSink = { effect ->
          when (effect) {
            is RcPlayerEffect.HapticFeedback ->
              latestHapticFeedback.performAndroidXHaptic(effect.type)
            is RcPlayerEffect.WakeIn -> {
              val current = wakeIntervalSeconds
              if (!effect.seconds.isNaN() && (current == null || effect.seconds < current)) {
                wakeIntervalSeconds = effect.seconds
              }
            }
            RcPlayerEffect.NextFrame -> nextFrameRequestVersion += 1
          }
        },
        // Read through `latestTimeSource` for the reason `systemColorLookup` is below: a host that
        // provides a new source must not discard the running document's state.
        timeSource = RcForwardingTimeSource { latestTimeSource },
        soundSink = soundDispatcher::dispatch,
        // Read through `latestSystemColors`, never captured directly: a host's lookup is usually a
        // capturing lambda, so a parent recomposition hands us a fresh instance. Keying the state
        // on it would rebuild `RcPlayerState` — discarding variables an action changed,
        // touch-expression state and running animation timelines — because a colour callback that
        // resolves the same palette happened to be reallocated.
        //
        // The `Color` -> ARGB conversion happens here, at the module boundary: `RcPlayerState`
        // lives in `:rc-player-runtime`, which has no Compose UI dependency, and packed ARGB really
        // is the wire value there. See [toRcArgb].
        systemColorLookup = { name -> latestSystemColors(name)?.toRcArgb() },
      )
    }
  // Apply host edits to the live state instead of rebuilding it. `setNamedValue` already applied a
  // single value incrementally against `variableNames`, type-checked against the AndroidX variable
  // type; nothing on the public path called it. A removal means "stop overriding this", which needs
  // the pre-override value, so `clearNamedValue` is its inverse (see `RcPlayerState`).
  //
  // Errors are deliberately not caught: an unknown name or a type mismatch threw from the
  // `RcPlayerState` constructor before this change, and a host that names a variable the document
  // does not have should still hear about it rather than watch the value quietly not apply.
  //
  // `appliedNamedValues` tracks what the *state* holds, not what the holder holds, and is
  // remembered on `state` so it survives the effect restarting. Two things need that:
  //
  //  * It is seeded from `seededNamedValues` — the map the constructor actually received — rather
  //    than from a fresh read of the holder. A host that writes between the state's construction
  //    and this effect starting would otherwise make the first emission compare equal to a value
  //    the state never saw, and the player would stay stale until that entry moved again.
  //  * The effect is keyed on the holder's identity as well as `state`, so a parent that swaps in a
  //    *different* `SnapshotStateMap` for the same document is followed instead of leaving the
  //    collector subscribed to the detached old one. Restarting the collector does not rebuild
  //    `RcPlayerState`, which is the whole point of this bridge; because `appliedNamedValues`
  //    outlives the restart, the new holder is diffed against what the state really has, so
  //    entries the old holder had and the new one does not are cleared rather than left applied.
  // What the host is actually playing at, for a capture that deferred its density instead of
  // folding it in. `state` is remembered on the document alone, so a rotation or an accessibility
  // text change arrives here as a recomposition around the same state rather than a new one — which
  // is why this is pushed on every composition instead of passed to the constructor.
  val hostDensity = androidx.compose.ui.platform.LocalDensity.current
  SideEffect { state.setHostDensity(hostDensity.density, hostDensity.fontScale) }
  val appliedNamedValues = remember(state) { seededNamedValues.toMutableMap() }
  LaunchedEffect(state, namedValues) {
    snapshotFlow { namedValues.toMap() }
      .collect { current ->
        if (current == appliedNamedValues) return@collect
        (appliedNamedValues.keys - current.keys).forEach(state::clearNamedValue)
        current.forEach { (name, value) ->
          if (appliedNamedValues[name] != value) state.setNamedValue(name, value)
        }
        appliedNamedValues.clear()
        appliedNamedValues.putAll(current)
        // `setNamedValue` writes into the state's maps without going through the action path, so
        // nothing else tells the draw layer a value moved.
        invalidationVersion += 1
      }
  }
  val documentDeclaresAnimation =
    remember(document) {
      document.operations.filterIsInstance<RcFloatExpression>().any { it.animation != null } ||
        // A marquee is not here: `basicMarquee` runs its own animation.
        document.operations.any { it is RcTimeAttribute && it.type.requiresContinuousFrames } ||
        // A document can also animate by reading the clock directly, with no animation attached to
        // anything: `remote-m3`'s indeterminate circular progress builds its sweep from a float
        // expression over the player-supplied `CONTINUOUS_SEC` (#4264). Without this the state's
        // per-frame variables would be loaded exactly once and the arc would hold its first pose.
        document.referencesContinuousSystemVariable()
    }
  // A document that reads the clock only in whole seconds — a digital watch face — changes once a
  // second, and redrawing it at the display rate in between draws the same frame again. AndroidX
  // sleeps such a document to the next second; so does this.
  // Any other clock read — a text field showing the seconds directly, a date that must turn over at
  // midnight — gets the same once-a-second refresh, found by scanning every field.
  val ticksEverySecond =
    remember(document) {
      !documentDeclaresAnimation &&
        (document.referencesMovingSystemVariable() ||
          document.referencesAnyOf(RcSystemVariables.CLOCK))
    }
  // An implicit graphics-layer tween needs no document frames: it runs on Compose's own frame clock
  // and updates only its layer (see `applyGraphicsLayer`).
  val needsContinuousFrames = documentDeclaresAnimation
  var frameNanos by remember { mutableLongStateOf(0L) }
  val animationClock = LocalRcAnimationClock.current
  var frameOriginNanos by remember(document) { mutableLongStateOf(Long.MIN_VALUE) }
  val recordFrame: (Long) -> Unit = { nanos ->
    if (frameOriginNanos == Long.MIN_VALUE) frameOriginNanos = nanos
    frameNanos = nanos - frameOriginNanos
  }
  LaunchedEffect(document) { withFrameNanos(recordFrame) }
  LaunchedEffect(needsContinuousFrames) {
    if (needsContinuousFrames) {
      while (true) withFrameNanos(recordFrame)
    }
  }
  LaunchedEffect(ticksEverySecond, needsContinuousFrames) {
    if (ticksEverySecond && !needsContinuousFrames) {
      while (true) {
        delay(MILLIS_PER_SECOND - latestTimeSource.currentTimeMillis().mod(MILLIS_PER_SECOND))
        withFrameNanos(recordFrame)
        invalidationVersion += 1
      }
    }
  }
  LaunchedEffect(nextFrameRequestVersion) {
    if (nextFrameRequestVersion > 0) {
      withFrameNanos(recordFrame)
      invalidationVersion += 1
    }
  }
  LaunchedEffect(wakeIntervalSeconds) {
    val seconds = wakeIntervalSeconds ?: return@LaunchedEffect
    val delayMillis =
      if (!seconds.isFinite()) Int.MAX_VALUE.toLong()
      else (seconds * 1_000f).toLong().coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong())
    if (delayMillis > 0L) delay(delayMillis)
    withFrameNanos(recordFrame)
    wakeIntervalSeconds = null
    invalidationVersion += 1
  }
  val linkedDocument = remember(document) { RcDocumentLinker.link(document) }
  val layout = remember(linkedDocument) { RcLayoutTree.build(linkedDocument) }
  LaunchedEffect(linkedDocument, layout) {
    // Layout rendering consumes paint operations through component content rather than walking
    // the document root. AndroidX still applies root-level diagnostics during document execution.
    if (layout != null) {
      linkedDocument.operations.forEach { node ->
        ((node as? RcLinkedNode.Operation)?.operation as? RcDebugMessage)?.let(
          state::emitDebugMessage
        )
      }
    }
  }
  val images = remember(document) { decodeInlineImages(document) }
  val offscreenTargets = remember(document) { RcOffscreenTargetPool() }
  DisposableEffect(offscreenTargets) { onDispose { offscreenTargets.dispose() } }
  val fonts = remember(document) { decodeInlineFonts(document) }
  val textMeasurer = rememberTextMeasurer()
  val drawObserver = LocalRcDrawObserver.current
  // Subscribe *composition* to invalidations, not just the draw layer below.
  //
  // `rootContentDescription` and the legacy click areas are read here, during composition, and both
  // can be backed by a named text — so a host write, or a document action, changes what a screen
  // reader should announce. Every such mutation raises `invalidationVersion`, but until this read
  // existed the only composition that observed it was the `layout != null` branch further down; on
  // a legacy canvas document `invalidationVersion` was read solely inside a `drawWithContent`
  // lambda, which redraws without recomposing. The label went stale while the pixels were right —
  // invisible to a pixel test, which is why `RcNamedValueSemanticsTest` asserts through the
  // semantics tree.
  //
  // It also fixes an ordering hazard that has nothing to do with accessibility: when the *host*
  // swaps in a different named-value holder, the recomposition that swap triggers runs before the
  // bridge below has applied the new values, so a composition-time read of player state would
  // otherwise show the previous holder's overrides and never be revisited.
  //
  // Cheap, because `invalidationVersion` is event-driven — actions, wake-ins, next-frame requests,
  // host writes — and not bumped per frame; continuous animation drives `frameNanos` instead.
  invalidationVersion
  val semanticsModifier =
    state.rootContentDescription?.let { description ->
      modifier.semantics { contentDescription = description }
    } ?: modifier
  val interactiveModifier =
    semanticsModifier
      .then(
        RcTouchPositionElement(
          state = state,
          documentWidth = document.header.width.coerceAtLeast(1).toFloat(),
          documentHeight = document.header.height.coerceAtLeast(1).toFloat(),
          onRepaint = { invalidationVersion += 1 },
        )
      )
      .applyAndroidXClickAreas(
        areas = state.clickAreas,
        state = state,
        documentWidth = document.header.width.coerceAtLeast(1).toFloat(),
        documentHeight = document.header.height.coerceAtLeast(1).toFloat(),
        rootContentBehavior = state.rootContentBehavior,
      )
  val redrawModifier =
    interactiveModifier
      .drawWithContent {
        invalidationVersion // Subscribe the draw layer to action and WakeIn invalidations.
        drawObserver?.onFrame()
        drawContent()
      }
      // Published here rather than on the root layout component, because a canvas-only document has
      // no layout tree at all — it takes the `Canvas` branch below — and hanging the state off a
      // component that does not exist made every expression, colour and text probe on such a
      // document report "not implemented" while the document itself was perfectly observable.
      .inspectDocument(state, LocalRcInspection.current)
  if (layout != null) {
    // Layout variables (visibility, dimensions, offsets, and constraints) are read during
    // composition/measurement rather than painting, so action mutations must invalidate this
    // branch as well as the draw layer.
    invalidationVersion
    state.beginFrame(animationClock?.seconds() ?: (frameNanos / 1_000_000_000f))
    // beginFrame resets derived text to the document's literals, so the ids the layout's own data
    // operations publish must be recomputed before this same composition measures and draws.
    state.applyLayoutContentStateOperations(linkedDocument.operations, theme)
    val version = invalidationVersion
    val settles = state.hasAnyComponentValues
    val tree: @Composable (probe: RcGeometryProbe?) -> Unit = { probe ->
      LookaheadScope {
        CompositionLocalProvider(
          LocalRcLookaheadScope provides this,
          LocalRcLayoutVersion provides version,
          LocalRcGeometryProbe provides probe,
          LocalRcFonts provides fonts,
          LocalRcTypefaces provides typefaces,
          LocalRcCustomComponents provides customComponents,
          LocalRcInvalidate provides { invalidationVersion += 1 },
          LocalRcOffscreenTargets provides offscreenTargets,
        ) {
          // The root sits at the window's origin at its own size. A root smaller than the window's
          // minimum — one still animating toward a resize, or one the document sizes below the
          // host — would otherwise be coerced up and centred in the difference.
          Box(if (settles) Modifier else redrawModifier) {
            RenderLayoutNode(
              node = layout,
              // When the tree settles, the host's modifier and the player's own hooks sit on the
              // settling layout instead — the node the host's parent actually sees, so parent data
              // like `weight` still reaches it, and host padding or sizing shapes the constraints
              // the probes measure under as well as the kept tree's.
              modifier = Modifier,
              state = state,
              textMeasurer = textMeasurer,
              images = images,
              theme = theme,
            )
          }
        }
      }
    }
    if (settles) {
      RcSettledLayout(
        modifier = redrawModifier,
        documentSize = IntSize(document.header.width, document.header.height),
        settle = remember(state) { RcFirstLayoutSettle() },
        onSettled = { state.applyLayoutContentStateOperations(linkedDocument.operations, theme) },
        tree = tree,
      )
    } else {
      tree(null)
    }
  } else {
    // A host that sizes nothing gets the document's own size, as the View player's wrap-content
    // does. A 0x0 scope draws its colours (nothing clips them) but no brush: a `ShaderBrush` makes
    // no shader for an empty size, so every gradient and runtime shader would vanish.
    val documentSize =
      with(androidx.compose.ui.platform.LocalDensity.current) {
        DpSize(
          document.header.width.coerceAtLeast(0).toDp(),
          document.header.height.coerceAtLeast(0).toDp(),
        )
      }
    Canvas(redrawModifier.defaultMinSize(documentSize.width, documentSize.height)) {
      val width = document.header.width.coerceAtLeast(1)
      val height = document.header.height.coerceAtLeast(1)
      val rootTransform =
        computeRootTransform(
          documentWidth = width.toFloat(),
          documentHeight = height.toFloat(),
          viewportWidth = size.width,
          viewportHeight = size.height,
          behavior = state.rootContentBehavior,
        )
      withTransform({
        translate(rootTransform.translateX, rootTransform.translateY)
        scale(rootTransform.scaleX, rootTransform.scaleY, Offset.Zero)
      }) {
        state.beginFrame(animationClock?.seconds() ?: (frameNanos / 1_000_000_000f))
        rcTrace(RcTraceCategory.FRAME, "rc:drawRoot") {
          drawOperations(
            linkedDocument.operations,
            state,
            RcPaintState(typefaces, drawObserver),
            mutableMapOf(),
            textMeasurer,
            images,
            RcFloatFunctionRuntime(),
            theme,
            filterTheme = true,
            offscreenTargets = offscreenTargets,
          )
        }
      }
    }
  }
}

@Composable
private fun RenderLayoutNode(
  node: RcLayoutNode,
  modifier: Modifier = Modifier,
  forceGone: Boolean = false,
  /**
   * Ignore the node's own visibility modifier, for the branch of a layout that owns its children's
   * visibility — a `FitBox` or a `StateLayout`.
   *
   * AndroidX does the same: its inspector resolves a `FitBox`/`StateLayout` child's visibility from
   * the component itself rather than from the modifier, because the layout is what marks a branch
   * GONE when it declines to show it. Honouring the modifier there instead made
   * `fitbox_child_visibility` and `state_layout_child_visibility` report a `visibility: "visible"`
   * child as gone — the modifier's id names a *text* slot, which resolves to no integer, and the
   * fallback for an unresolvable id is GONE.
   */
  ignoreOwnVisibility: Boolean = false,
  state: RcPlayerState,
  textMeasurer: TextMeasurer,
  images: MutableMap<Int, ImageBitmap>,
  theme: Int,
) {
  val layoutVersion = LocalRcLayoutVersion.current
  val lookaheadScope = LocalRcLookaheadScope.current
  val geometryProbe = LocalRcGeometryProbe.current
  val fontFamilies = LocalRcFonts.current
  val typefaces = LocalRcTypefaces.current
  val drawObserver = LocalRcDrawObserver.current
  val customComponents = LocalRcCustomComponents.current
  val invalidate = LocalRcInvalidate.current
  val offscreenTargets = LocalRcOffscreenTargets.current
  val density = androidx.compose.ui.platform.LocalDensity.current
  val visibility =
    if (forceGone) {
      0
    } else if (layoutVersion == Int.MIN_VALUE) {
      error("unreachable layout invalidation version")
    } else if (ignoreOwnVisibility) {
      1
    } else {
      node.modifiers.visibility?.let { androidXVisibility(state.integer(it.visibilityId) ?: 0) }
        ?: 1
    }
  // Inside a `StateLayout` switcher a component that carries an animation id is a shared element:
  // its bounds are interpolated between the outgoing and incoming branch by the shared transition,
  // so it must not also drive `animateRcBounds` — two approach-layout animations chasing the same
  // node fight each other. Outside a switcher this is null and nothing changes.
  val layoutAnimations = LocalRcLayoutAnimations.current
  val sharedElementModifier =
    if (node is RcLayoutNode.Content) null
    else rcSharedElementModifier(node.componentId, node.animationId, node.modifiers.animationSpec)
  val boundsModifier =
    if (sharedElementModifier != null) {
      modifier.then(sharedElementModifier)
    } else if (node is RcLayoutNode.Content || lookaheadScope == null) {
      modifier
    } else {
      // AndroidX animates every component but layout content to its new measure, over the
      // component's spec or the 300 ms default (`Component.layout`). A resize lands at once only
      // because the host turns animation off for it, which is what `LocalRcLayoutAnimations` is.
      val spec = node.modifiers.animationSpec ?: DefaultRcAnimationSpec
      if (layoutAnimations) modifier.animateRcBounds(lookaheadScope, spec) else modifier
    }
  val geometryIds = node.geometryComponentIds()
  val inspecting = LocalRcInspection.current
  // The modifiers a `StateLayout` actually applies — it drops its own fill and padding, see its
  // branch below. Derived here rather than in the branch so the reported content inset describes
  // the chain that was built: an inset left over from dropped padding subtracts 20 from every
  // child's position, which is what `state_layout_padding_container` was reporting as `x: -20`.
  val layoutModifiers =
    if (node is RcLayoutNode.State) node.modifiers.withoutDimensions().withoutPadding()
    else node.modifiers
  val contentInset =
    if (inspecting) rcContentInsetPixels(layoutModifiers, state, density) else Offset.Zero
  val goneReport: @Composable () -> Unit = {
    if (geometryIds.any(state::hasComponentValues) || inspecting) {
      // A gone component still reports, at zero size. Dropping it would make "laid out at nothing"
      // and "not in the tree" the same observation, and they are not: the corpus asserts `isGone`
      // on nodes it still expects to find.
      Layout(
        Modifier.trackComponentGeometry(geometryIds, state, geometryProbe)
          .inspectComponent(node, visibility, inspecting, contentInset)
      ) { _, _ ->
        layout(0, 0) {}
      }
    }
  }
  val body: @Composable (Modifier) -> Unit = { visibleModifier ->
    // A collapsible layout that keeps none of its children is GONE itself (AndroidX
    // `computeVisibleChildren`), which only its measure pass can know.
    val collapse =
      if (node is RcLayoutNode.CollapsibleRow || node is RcLayoutNode.CollapsibleColumn) {
        remember(node) { RcCollapse() }
      } else null
    val effectiveModifier =
      (collapse?.let { Modifier.goneWhenCollapsed(it).then(visibleModifier) } ?: visibleModifier)
        .trackComponentGeometry(geometryIds, state, geometryProbe)
        .inspectComponent(
          node,
          if (collapse?.collapsed == true) 0 else visibility,
          inspecting,
          contentInset,
        )
    when (node) {
      is RcLayoutNode.Root ->
        Box(
          effectiveModifier.applyComponentModifiers(
            node.modifiers,
            state,
            geometryIds,
            fillMissingDimensions = true,
            node.canvasOperations,
            textMeasurer,
            images,
            theme,
          )
        ) {
          node.children.forEach {
            RenderLayoutNode(
              it,
              state = state,
              textMeasurer = textMeasurer,
              images = images,
              theme = theme,
            )
          }
        }
      is RcLayoutNode.Content -> {
        val content: @Composable () -> Unit = {
          node.children.forEach {
            RenderLayoutNode(
              it,
              state = state,
              textMeasurer = textMeasurer,
              images = images,
              theme = theme,
            )
          }
        }
        if (visibility == 2) Box(effectiveModifier) { content() } else content()
      }
      is RcLayoutNode.Canvas ->
        Box(
          effectiveModifier.applyComponentModifiers(
            node.modifiers,
            state,
            geometryIds,
            fillMissingDimensions = true,
            node.canvasOperations,
            textMeasurer,
            images,
            theme,
          )
        ) {
          node.content
            ?.operations
            ?.takeIf { it.isNotEmpty() }
            ?.let { operations ->
              Canvas(Modifier.fillMaxSize()) {
                rcTrace(RcTraceCategory.FRAME, "rc:drawCanvas") {
                  drawOperations(
                    operations,
                    state,
                    RcPaintState(typefaces, drawObserver),
                    mutableMapOf(),
                    textMeasurer,
                    images,
                    RcFloatFunctionRuntime(),
                    theme,
                    filterTheme = true,
                    offscreenTargets = offscreenTargets,
                  )
                }
              }
            }
          node.content?.let {
            RenderLayoutNode(
              it,
              state = state,
              textMeasurer = textMeasurer,
              images = images,
              theme = theme,
            )
          }
        }
      is RcLayoutNode.CanvasContent ->
        Canvas(Modifier.fillMaxSize()) {
          rcTrace(RcTraceCategory.FRAME, "rc:drawCanvas") {
            drawOperations(
              node.operations,
              state,
              RcPaintState(typefaces, drawObserver),
              mutableMapOf(),
              textMeasurer,
              images,
              RcFloatFunctionRuntime(),
              theme,
              filterTheme = true,
              offscreenTargets = offscreenTargets,
            )
          }
        }
      is RcLayoutNode.Custom -> {
        val customModifier =
          effectiveModifier.applyComponentModifiers(
            node.modifiers,
            state,
            geometryIds,
            fillMissingDimensions = false,
            canvasOperations = null,
            textMeasurer,
            images,
            theme,
          )
        Box(customModifier) {
          val config = state.text(node.operation.configId).orEmpty()
          customComponents
            .content(config)
            ?.invoke(
              node.operation.component(config, state, invalidate),
              Modifier.fillMaxSize(),
            )
        }
      }
      is RcLayoutNode.Box ->
        Box(
          effectiveModifier.applyComponentModifiers(
            node.modifiers,
            state,
            geometryIds,
            fillMissingDimensions = false,
            node.canvasOperations,
            textMeasurer,
            images,
            theme,
          ),
          contentAlignment =
            boxAlignment(node.operation.horizontalPositioning, node.operation.verticalPositioning),
        ) {
          node.content?.let { content ->
            RenderLayoutNode(
              content,
              state = state,
              textMeasurer = textMeasurer,
              images = images,
              theme = theme,
            )
          }
        }
      is RcLayoutNode.Row -> {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val spacingDp = state.dpTypedDp(state.resolve(node.operation.spacedBy), density)
        val rowModifier =
          effectiveModifier.applyComponentModifiers(
            node.modifiers,
            state,
            geometryIds,
            fillMissingDimensions = false,
            node.canvasOperations,
            textMeasurer,
            images,
            theme,
          )
        val alignedRow = rememberRcAlignedRow(node.content.children, state)
        val hasWeightedChildren =
          node.content.children.any { child ->
            child.modifiers.width?.type == RcDimensionType.WEIGHT &&
              child.modifiers.visibility?.let {
                androidXVisibility(state.integer(it.visibilityId) ?: 0) != 0
              } != false
          }
        Row(
          // Compose's Row places an alignment-line-aligned group at the top of the row and ignores
          // `verticalAlignment` for it; AndroidX offsets the group by the row's vertical
          // positioning against the tallest child. See [rcAlignedRowPositioning].
          if (alignedRow != null) {
            rowModifier.rcAlignedRowPositioning(
              alignedRow,
              rowAlignment(node.operation.verticalPositioning),
            )
          } else {
            rowModifier
          },
          horizontalArrangement =
            RcHorizontalArrangement(
              node.operation.horizontalPositioning,
              visualSpacing = spacingDp,
              // AndroidX measures weighted children from all remaining row space, then adds the
              // configured gaps while positioning. Compose normally reserves those gaps before
              // distributing weight, which makes every weighted child too narrow.
              spacing = if (hasWeightedChildren) 0.dp else spacingDp,
            ),
          verticalAlignment = rowAlignment(node.operation.verticalPositioning),
        ) {
          node.content.children.forEachIndexed { index, child ->
            RenderLayoutNode(
              child,
              modifier =
                rowWeightModifier(child, state).let { weight ->
                  alignedRow?.let { weight.then(rcRowAnchorModifier(it, index)) } ?: weight
                },
              state = state,
              textMeasurer = textMeasurer,
              images = images,
              theme = theme,
            )
          }
        }
      }
      is RcLayoutNode.Column -> {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val spacing = state.dpTypedDp(state.resolve(node.operation.spacedBy), density)
        Column(
          effectiveModifier.applyComponentModifiers(
            node.modifiers,
            state,
            geometryIds,
            fillMissingDimensions = false,
            node.canvasOperations,
            textMeasurer,
            images,
            theme,
          ),
          verticalArrangement = RcVerticalArrangement(node.operation.verticalPositioning, spacing),
          horizontalAlignment = columnAlignment(node.operation.horizontalPositioning),
        ) {
          node.content.children.forEach { child ->
            RenderLayoutNode(
              child,
              modifier = columnWeightModifier(child, state),
              state = state,
              textMeasurer = textMeasurer,
              images = images,
              theme = theme,
            )
          }
        }
      }
      is RcLayoutNode.Flow -> {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val spacing = state.dpTypedDp(state.resolve(node.operation.spacedBy), density)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
          effectiveModifier.applyComponentModifiers(
            node.modifiers,
            state,
            geometryIds,
            fillMissingDimensions = false,
            node.canvasOperations,
            textMeasurer,
            images,
            theme,
          ),
          horizontalArrangement =
            RcHorizontalArrangement(node.operation.horizontalPositioning, spacing),
          verticalArrangement = RcVerticalArrangement(node.operation.verticalPositioning, 0.dp),
          itemVerticalAlignment = rowAlignment(node.operation.verticalPositioning),
          maxItemsInEachRow = node.operation.maxItemsInEachRow,
          maxLines = node.operation.maxLines,
        ) {
          RenderLayoutNode(
            node.content,
            state = state,
            textMeasurer = textMeasurer,
            images = images,
            theme = theme,
          )
        }
      }
      is RcLayoutNode.State -> {
        val selected = state.integer(node.operation.indexId) ?: 0
        // AndroidX sizes a state container to whichever branch is showing and ignores fill
        // modifiers
        // on it — `state_layout_basic` declares `fillMaxSize` and the reference still reports the
        // container at its active child's 120x80. Honouring the fill, as Compose naturally does,
        // stretches the container to the viewport and paints its background across everything the
        // reference leaves clear: 50,400 differing pixels on a 300x200 canvas.
        // The padding goes the same way as the fill: `state_layout_padding_container` declares
        // `fillMaxSize` + `padding: 20` around an 80x80 child, and the reference reports the
        // container at the child's own 80x80 with the child at (0, 0) — neither the container's
        // padding nor the fill reaches its geometry.
        val stateModifiers = layoutModifiers
        val contentVisibility =
          node.content.modifiers.visibility?.let {
            androidXVisibility(state.integer(it.visibilityId) ?: 0)
          } ?: 1
        Box(
          effectiveModifier.applyComponentModifiers(
            stateModifiers,
            state,
            geometryIds,
            fillMissingDimensions = false,
            canvasOperations = null,
            textMeasurer,
            images,
            theme,
          )
        ) {
          val children = node.content.children
          val target = selected.coerceIn(0, (children.size - 1).coerceAtLeast(0))
          val renderChildren: @Composable () -> Unit = {
            // Every branch the switch is not showing still publishes its (zero) geometry: a
            // component-value expression reads those ids whether or not its branch is on screen,
            // and
            // that was true before the switch animated too.
            children.forEachIndexed { index, child ->
              if (index != target || contentVisibility == 0) {
                RenderLayoutNode(
                  child,
                  forceGone = true,
                  state = state,
                  textMeasurer = textMeasurer,
                  images = images,
                  theme = theme,
                )
              }
            }
            if (contentVisibility != 0 && children.isNotEmpty()) {
              // Alignment stays TopStart, which is where this player has always placed a state
              // branch — upstream centres it, and matching that is a layout change rather than the
              // transition this ports.
              RcAnimatedAlternatives(
                target = target,
                spec = node.modifiers.animationSpec ?: DefaultRcAnimationSpec,
                alignment = Alignment.TopStart,
                label = "RcStateLayout",
                sharedElements = { index -> children[index].sharedElementComponents() },
              ) { index ->
                val child = children[index]
                key(child.componentId) {
                  RenderLayoutNode(
                    child,
                    ignoreOwnVisibility = true,
                    state = state,
                    textMeasurer = textMeasurer,
                    images = images,
                    theme = theme,
                  )
                }
              }
            }
          }
          if (contentVisibility == 0) {
            Layout(
              Modifier.trackComponentGeometry(
                listOf(node.content.componentId),
                state,
                geometryProbe,
              )
            ) { _, _ ->
              layout(0, 0) {}
            }
            renderChildren()
          } else {
            Box(
              Modifier.trackComponentGeometry(
                listOf(node.content.componentId),
                state,
                geometryProbe,
              )
            ) {
              renderChildren()
            }
          }
        }
      }
      is RcLayoutNode.CollapsibleRow -> {
        RcCollapsibleLayout(
          children = node.content.children,
          orientation = RcCollapseOrientation.Horizontal,
          collapse = requireNotNull(collapse),
          mainPositioning = node.operation.horizontalPositioning,
          crossPositioning = node.operation.verticalPositioning,
          spacing =
            state.dpTypedPixels(state.resolve(node.operation.spacedBy), density).roundToInt(),
          modifier =
            effectiveModifier.applyComponentModifiers(
              node.modifiers,
              state,
              geometryIds,
              fillMissingDimensions = false,
              node.canvasOperations,
              textMeasurer,
              images,
              theme,
            ),
          state = state,
          textMeasurer = textMeasurer,
          images = images,
          theme = theme,
        )
      }
      is RcLayoutNode.CollapsibleColumn -> {
        RcCollapsibleLayout(
          children = node.content.children,
          orientation = RcCollapseOrientation.Vertical,
          collapse = requireNotNull(collapse),
          mainPositioning = node.operation.verticalPositioning,
          crossPositioning = node.operation.horizontalPositioning,
          spacing =
            state.dpTypedPixels(state.resolve(node.operation.spacedBy), density).roundToInt(),
          modifier =
            effectiveModifier.applyComponentModifiers(
              node.modifiers,
              state,
              geometryIds,
              fillMissingDimensions = false,
              node.canvasOperations,
              textMeasurer,
              images,
              theme,
            ),
          state = state,
          textMeasurer = textMeasurer,
          images = images,
          theme = theme,
        )
      }
      is RcLayoutNode.Image -> {
        val image = images[node.operation.bitmapId]
        val density = androidx.compose.ui.platform.LocalDensity.current
        var imageModifier =
          effectiveModifier.applyComponentModifiers(
            node.modifiers,
            state,
            geometryIds,
            fillMissingDimensions = false,
            canvasOperations = null,
            textMeasurer,
            images,
            theme,
          )
        // A wrap-content image sizes to its bitmap's intrinsic dimensions. `applyWidth` treats WRAP
        // as the absence of a size modifier — Compose would size the Canvas to its (empty) content,
        // which is 0×0 — so the intrinsic size has to be applied here, as it is when no dimension
        // is
        // declared at all. `image_layout_sizing_options` asserts exactly that: its wrap-content
        // ImageLayout is 60×40, the bitmap's own size.
        val wrapsWidth = node.modifiers.width?.type == RcDimensionType.WRAP
        val wrapsHeight = node.modifiers.height?.type == RcDimensionType.WRAP
        if (image != null && (node.modifiers.width == null || wrapsWidth)) {
          imageModifier = imageModifier.width(with(density) { image.width.toDp() })
        }
        if (image != null && (node.modifiers.height == null || wrapsHeight)) {
          imageModifier = imageModifier.height(with(density) { image.height.toDp() })
        }
        // Drawn through Compose's painter pipeline (`Modifier.paint` + `ContentScale`, the mapping
        // AndroidX's embedded player uses) rather than a hand-computed scaled rect.
        // `sizeToIntrinsics = false` keeps the sizing above authoritative — a `Spacer` measures
        // to its modifiers exactly as a `Canvas` does — and the clip matches `Image`'s.
        val painter = remember(image) { image?.let(::BitmapPainter) }
        var drawModifier = imageModifier
        if (painter != null) {
          drawModifier =
            drawModifier
              .clipToBounds()
              .paint(
                painter,
                sizeToIntrinsics = false,
                alignment = Alignment.Center,
                contentScale = imageLayoutContentScale(node.operation.scaleType),
                alpha = state.resolve(node.operation.alpha),
              )
        }
        Spacer(drawModifier)
      }
      is RcLayoutNode.Text -> {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val operation = node.operation
        val fontWeight = state.resolve(operation.fontWeight).roundToInt().coerceIn(1, 1000)
        val boldWeight = if (operation.fontStyle and 1 != 0) 700 else fontWeight
        BasicText(
          text = state.text(operation.textId).orEmpty(),
          modifier =
            effectiveModifier.applyComponentModifiers(
              node.modifiers,
              state,
              geometryIds,
              fillMissingDimensions = false,
              canvasOperations = null,
              textMeasurer,
              images,
              theme,
            ),
          style =
            TextStyle(
              color =
                Color(
                  if (operation.flags and RcTextLayout.FLAG_DYNAMIC_COLOR != 0)
                    state.color(operation.color)
                  else operation.color
                ),
              // `toSp()`, not `/ density.density`: Compose rasterizes `x.sp` at
              // `x * density * fontScale`, so returning the wire's pixels needs BOTH divided out.
              // Dividing by density alone left the host's font scale applied a second time, which
              // was
              // invisible at `fontScale = 1` and doubled this operation's text at 2.0 while
              // `CoreText` below held still. Worse than a doubling on a `RemoteDensity.Host`
              // document, whose sizes already carry Android's damped sp curve: a 44sp headline is
              // 50.4px at fontScale 2.0, and scaling that again gives 100.8.
              fontSize = with(density) { state.resolve(operation.fontSize).toSp() },
              fontWeight = FontWeight(boldWeight),
              fontStyle =
                if (operation.fontStyle and 2 != 0) FontStyle.Italic else FontStyle.Normal,
              fontFamily =
                resolveFontFamily(operation.fontFamilyId, state, fontFamilies, typefaces),
              textAlign = operation.composeTextAlign(),
            ),
          overflow = operation.composeTextOverflow(),
          maxLines = androidXMaxLines(operation.overflow, operation.maxLines),
        )
      }
      is RcLayoutNode.CoreText -> {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val properties = node.resolvedStyle
        val fontSize = state.resolve(properties.floatProperty(5, 36f))
        val lineHeightAdd = state.resolve(properties.floatProperty(13, 0f))
        val lineHeightMultiplier = state.resolve(properties.floatProperty(14, 1f))
        val fontStyle = properties.intProperty(6, 0)
        val fontWeight =
          state.resolve(properties.floatProperty(7, 400f)).roundToInt().coerceIn(1, 1000)
        val boldWeight = if (fontStyle and 1 != 0) 700 else fontWeight
        val colorId = properties.intProperty(4, -1)
        val autosize = properties.booleanProperty(22, false)
        val minFontSize = properties.floatProperty(25, -1f).let(state::resolve)
        val maxFontSize = properties.floatProperty(26, -1f).let(state::resolve)
        val resolvedMaxFontSize = if (maxFontSize > 0f) maxFontSize else 400f
        val resolvedMinFontSize =
          minOf(if (minFontSize > 0f) minFontSize else 4f, resolvedMaxFontSize)
        // Font-variation axes (properties 20/21) — a variable font's `wght` / `wdth` / … instance.
        // The tags arrive as text ids and the values may be document floats, so both are resolved
        // through the player state before they are paired up.
        val variations =
          fontVariationSettings(
            axisTags = properties.intArrayProperty(CORE_TEXT_FONT_AXIS_TAGS).map { state.text(it) },
            axisValues =
              properties.floatArrayProperty(CORE_TEXT_FONT_AXIS_VALUES).map { state.resolve(it) },
          )
        val lines = remember { RcTextLines() }
        val text = state.text(node.operation.textId).orEmpty()
        val overflow = properties.intProperty(10, RcTextLayout.OVERFLOW_CLIP)
        val maxLines = androidXMaxLines(overflow, properties.intProperty(11, Int.MAX_VALUE))
        // Under the closed-form Ahem model a truncated run is sized as the model sizes it (§2.3):
        // its kept lines, the last one ellipsized. Without the model it keeps the full width.
        // The model keeps `maxLines` lines whatever the overflow, one font size each.
        val truncatedBlock: ((Float, Float) -> Size)? =
          if (LocalRcAhemTextMetrics.current && lineHeightAdd == 0f && lineHeightMultiplier == 1f) {
            { maxWidth, laidOutFontSize ->
              rcAhemTruncatedBlock(
                text,
                maxWidth,
                laidOutFontSize,
                properties.intProperty(11, Int.MAX_VALUE),
                ellipsis = overflow == RcTextLayout.OVERFLOW_ELLIPSIS,
              )
            }
          } else null
        BasicText(
          text = text,
          modifier =
            effectiveModifier
              .applyComponentModifiers(
                node.modifiers,
                state,
                geometryIds,
                fillMissingDimensions = false,
                canvasOperations = null,
                textMeasurer,
                images,
                theme,
              )
              .fitToLines(lines, truncatedBlock),
          onTextLayout = { lines.result = it },
          style =
            TextStyle(
              color =
                Color(
                  if (colorId == -1) properties.intProperty(3, 0xff000000.toInt())
                  else state.color(colorId)
                ),
              fontSize = with(density) { fontSize.toSp() },
              // **Ems, not pixels.** Property 12 carries what
              // `android.graphics.Paint.setLetterSpacing`
              // takes, which is a multiple of the font size — the vendored AndroidX player spells
              // the
              // same value `data.letterSpacing.em`. Converting it as a pixel length made every
              // document's spacing effectively zero: the `remote-m3` body style asks for 0.02857
              // em,
              // and `0.02857.toSp()` at density 2 is 0.014 sp. Text then measured about 5% narrow,
              // which is invisible on one line and re-breaks every paragraph that wraps.
              letterSpacing = state.resolve(properties.floatProperty(12, 0f)).em,
              lineHeight =
                if (lineHeightAdd == 0f && lineHeightMultiplier == 1f) TextUnit.Unspecified
                else if (autosize)
                  (lineHeightMultiplier + lineHeightAdd / fontSize.coerceAtLeast(0.0001f)).em
                else with(density) { (fontSize * lineHeightMultiplier + lineHeightAdd).toSp() },
              fontWeight = FontWeight(boldWeight),
              fontStyle = if (fontStyle and 2 != 0) FontStyle.Italic else FontStyle.Normal,
              fontFamily =
                resolveFontFamily(
                  properties.intProperty(8, -1),
                  state,
                  fontFamilies,
                  typefaces,
                  withWeightAxis(variations, boldWeight),
                ),
              textAlign =
                if (properties.intProperty(17, 0) == 1) TextAlign.Justify
                else androidXTextAlign(properties.intProperty(9, RcTextLayout.ALIGN_LEFT)),
              lineBreak =
                when (properties.intProperty(15, 0)) {
                  1 -> LineBreak.Paragraph
                  2 -> LineBreak.Heading
                  else -> LineBreak.Simple
                },
              hyphens = if (properties.intProperty(16, 0) > 0) Hyphens.Auto else Hyphens.None,
              textDecoration =
                when {
                  properties.booleanProperty(18, false) && properties.booleanProperty(19, false) ->
                    TextDecoration.combine(
                      listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                    )
                  properties.booleanProperty(18, false) -> TextDecoration.Underline
                  properties.booleanProperty(19, false) -> TextDecoration.LineThrough
                  else -> TextDecoration.None
                },
            ),
          overflow = androidXTextOverflow(properties.intProperty(10, RcTextLayout.OVERFLOW_CLIP)),
          maxLines =
            androidXMaxLines(
              properties.intProperty(10, RcTextLayout.OVERFLOW_CLIP),
              properties.intProperty(11, Int.MAX_VALUE),
            ),
          autoSize =
            if (autosize && LocalRcAhemTextMetrics.current)
              RcAhemAutoSize(
                minPx = resolvedMinFontSize,
                maxPx = resolvedMaxFontSize,
                // The declared cap, whatever the overflow: the model keeps `maxLines` lines.
                maxLines = properties.intProperty(11, Int.MAX_VALUE),
              )
            else if (autosize)
              TextAutoSize.StepBased(
                minFontSize = with(density) { resolvedMinFontSize.toSp() },
                maxFontSize = with(density) { resolvedMaxFontSize.toSp() },
                stepSize = with(density) { 0.5f.toSp() },
              )
            else null,
        )
      }
      is RcLayoutNode.FitBox -> {
        // Two phases, ported from AndroidX's embedded player ("Add FitBox shared element
        // transitions
        // using Compose Intrinsics", androidx-main `6fb763d3fe4`). The probe pass asks each
        // alternative for its intrinsic size — no placeables, and nothing is placed, so none of the
        // probe subtree's `onGloballyPositioned` geometry ever reaches the document's component
        // values. The content pass then composes the winner alone, inside the same switcher a
        // `StateLayout` uses, so an alternative that gives way to another as the box resizes
        // cross-fades into it and the components the two share morph between their two sizes rather
        // than jumping.
        val alignment =
          boxAlignment(node.operation.horizontalPositioning, node.operation.verticalPositioning)
        val children = node.content.children
        val contentVisibility =
          node.content.modifiers.visibility?.let {
            androidXVisibility(state.integer(it.visibilityId) ?: 0)
          } ?: 1
        // The switcher, hoisted out of the measure pass so it reads as ordinary composition: it is
        // called from `subcompose` with the alternative the probe chose.
        val alternatives: @Composable (Int) -> Unit = { chosenIndex ->
          RcAnimatedAlternatives(
            target = chosenIndex,
            spec = node.modifiers.animationSpec ?: DefaultRcAnimationSpec,
            alignment = alignment,
            label = "RcFitBox",
            sharedElements = { index -> children[index].sharedElementComponents() },
          ) { index ->
            val child = children[index]
            key(child.componentId) {
              RenderLayoutNode(
                child,
                ignoreOwnVisibility = true,
                state = state,
                textMeasurer = textMeasurer,
                images = images,
                theme = theme,
              )
            }
          }
        }
        SubcomposeLayout(
          modifier =
            effectiveModifier.applyComponentModifiers(
              node.modifiers,
              state,
              geometryIds,
              fillMissingDimensions = false,
              node.canvasOperations,
              textMeasurer,
              images,
              theme,
            )
        ) { constraints ->
          val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
          val maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
          val probes =
            subcompose(RcFitBoxSlot.Probe) {
              children.forEach { child ->
                Box(Modifier.clearAndSetSemantics {}) {
                  RenderLayoutNode(
                    child,
                    // The same override the content pass applies, or the two disagree about what an
                    // alternative measures: a visibility-decorated child would probe as 0x0, always
                    // "fit", and be selected — then be rendered visible at its real size,
                    // displacing
                    // a later alternative that actually fits.
                    ignoreOwnVisibility = true,
                    state = state,
                    textMeasurer = textMeasurer,
                    images = images,
                    theme = theme,
                  )
                }
              }
            }
          val fits =
            probes.indices.firstOrNull { index ->
              probes[index].maxIntrinsicWidth(maxHeight) <= maxWidth &&
                probes[index].maxIntrinsicHeight(maxWidth) <= maxHeight
            }
          // Nothing fits: remote-core hides the box entirely, and showing the smallest alternative
          // is
          // upstream's answer — a clipped component says more than a blank one. `fitbox_fit`
          // asserts
          // the other behaviour (box and child both GONE, geometry retained), which is a separate
          // change: the box's own gone state is a composition-time value while the fit decision is
          // made here, and forcing only the child leaves the box drawing its background.
          val chosen =
            fits ?: probes.indices.minByOrNull { probes[it].maxIntrinsicWidth(maxHeight) }
          // Loose, and the FitBox places the result itself: the switcher wraps the winner, and the
          // box aligns that against its own size the way it always has. Letting the switcher fill
          // and
          // align internally would work too, but only for a FitBox that has a size of its own — a
          // wrap-content one has to keep measuring at the winner's size.
          val loose = constraints.copy(minWidth = 0, minHeight = 0)
          val placeables =
            if (chosen == null || contentVisibility == 0) emptyList()
            else
              subcompose(RcFitBoxSlot.Content) {
                  if (contentVisibility == 2) {
                    Box(Modifier.alpha(0f)) { alternatives(chosen) }
                  } else {
                    alternatives(chosen)
                  }
                }
                .map { it.measure(loose) }
          val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
          val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
          layout(width, height) {
            placeables.forEach { placeable ->
              val offset =
                alignment.align(
                  IntSize(placeable.width, placeable.height),
                  IntSize(width, height),
                  layoutDirection,
                )
              placeable.place(offset.x, offset.y)
            }
          }
        }
      }
    }
  }
  if (node is RcLayoutNode.Content) {
    if (visibility == 0) goneReport()
    else body(if (visibility == 2) modifier.alpha(0f) else modifier)
  } else {
    RcVisibilityTransition(
      visibility = visibility,
      spec = node.modifiers.animationSpec,
      enabled = layoutAnimations,
      // The parent orders its children by `zIndex`, and the child it orders is the visibility
      // wrapper, so that is where the component's own `zIndex` has to be.
      modifier =
        node.modifiers.ordered.filterIsInstance<RcZIndexModifier>().lastOrNull()?.let {
          boundsModifier.zIndex(state.resolve(it.value))
        } ?: boundsModifier,
      gone = goneReport,
      content = body,
    )
  }
}

/** The two subcompositions [RcLayoutNode.FitBox] measures in: intrinsics probe, then the winner. */
private enum class RcFitBoxSlot {
  Probe,
  Content,
}

private val LocalRcLookaheadScope = compositionLocalOf<LookaheadScope?> { null }
private val LocalRcLayoutVersion = compositionLocalOf { 0 }
private val LocalRcGeometryProbe = staticCompositionLocalOf<RcGeometryProbe?> { null }
private val LocalRcFonts = compositionLocalOf<Map<Int, FontFamily>> { emptyMap() }
private val LocalRcTypefaces = compositionLocalOf<RcTypefaceLoader> { RcTypefaceLoader.Empty }
private val LocalRcCustomComponents = compositionLocalOf { RcCustomComponentRegistry.Empty }
private val LocalRcInvalidate = compositionLocalOf<() -> Unit> { {} }
private val LocalRcOffscreenTargets =
  compositionLocalOf<RcOffscreenTargetPool> { error("No document-scoped offscreen target pool") }

internal val DefaultRcAnimationSpec =
  RcAnimationSpec(
    animationId = -1,
    motionDurationMillis = RcFloatWord.literal(300f),
    motionEasingType = 1,
    visibilityDurationMillis = RcFloatWord.literal(300f),
    visibilityEasingType = 1,
    enterAnimation = RcLayoutAnimation.FadeIn,
    exitAnimation = RcLayoutAnimation.FadeOut,
  )

@Composable
private fun RcLayoutChild(
  child: RcLayoutNode,
  state: RcPlayerState,
  textMeasurer: TextMeasurer,
  images: MutableMap<Int, ImageBitmap>,
  theme: Int,
) {
  Layout(
    content = {
      RenderLayoutNode(
        child,
        state = state,
        textMeasurer = textMeasurer,
        images = images,
        theme = theme,
      )
    }
  ) { measurables, constraints ->
    // Measured with the incoming constraints rather than a relaxed copy: the collapsible layout's
    // weight pass hands a weighted child exact constraints on the main axis, and the child's own
    // measure is what turns those into its painted size. Callers that want a preferred-size
    // measurement already pass relaxed constraints (`loose` / `childConstraints`).
    val placeable = measurables.singleOrNull()?.measure(constraints)
    val alignmentLines =
      buildMap<AlignmentLine, Int> {
        placeable
          ?.get(FirstBaseline)
          ?.takeUnless { it == AlignmentLine.Unspecified }
          ?.let { put(FirstBaseline, it) }
        placeable
          ?.get(LastBaseline)
          ?.takeUnless { it == AlignmentLine.Unspecified }
          ?.let { put(LastBaseline, it) }
      }
    layout(placeable?.width ?: 0, placeable?.height ?: 0, alignmentLines = alignmentLines) {
      placeable?.place(0, 0)
    }
  }
}

private fun resolveAlignByAnchor(
  modifier: RcAlignByModifier?,
  placeable: androidx.compose.ui.layout.Placeable,
  state: RcPlayerState,
): Float =
  when (modifier?.line?.referencedId) {
    null -> modifier?.line?.value ?: 0f
    RcAlignByModifier.FIRST_BASELINE_ID ->
      placeable[FirstBaseline].takeUnless { it == AlignmentLine.Unspecified }?.toFloat() ?: 0f
    RcAlignByModifier.LAST_BASELINE_ID ->
      placeable[LastBaseline].takeUnless { it == AlignmentLine.Unspecified }?.toFloat() ?: 0f
    else -> state.resolve(modifier.line)
  }

/** AndroidX RowLayout aligns all children, including unanchored ones, to the maximum anchor. */
internal fun alignByCrossPositions(
  totalSize: Int,
  maximumChildSize: Int,
  verticalPositioning: Int,
  anchors: List<Float>,
): IntArray {
  val maximumAnchor = anchors.maxOrNull() ?: 0f
  val base =
    when (verticalPositioning) {
      4 -> 0f
      2 -> (totalSize - maximumChildSize) / 2f
      5 -> (totalSize - maximumChildSize).toFloat()
      else -> error("Unknown AndroidX row vertical position $verticalPositioning")
    }
  return IntArray(anchors.size) { index -> (base + maximumAnchor - anchors[index]).roundToInt() }
}

private enum class RcCollapseOrientation {
  Horizontal,
  Vertical,
}

@Composable
private fun RcCollapsibleLayout(
  children: List<RcLayoutNode>,
  orientation: RcCollapseOrientation,
  collapse: RcCollapse,
  mainPositioning: Int,
  crossPositioning: Int,
  spacing: Int,
  modifier: Modifier,
  state: RcPlayerState,
  textMeasurer: TextMeasurer,
  images: MutableMap<Int, ImageBitmap>,
  theme: Int,
) {
  Layout(
    content = {
      children.forEach { child ->
        // Keep one measurable per wire child even when its visibility modifier resolves to gone.
        RcLayoutChild(child, state, textMeasurer, images, theme)
      }
    },
    modifier = modifier,
  ) { measurables, constraints ->
    val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val weights = children.map { child ->
      val weightWord =
        if (orientation == RcCollapseOrientation.Horizontal) {
          child.modifiers.width?.takeIf { it.type == RcDimensionType.WEIGHT }?.value
        } else {
          child.modifiers.height?.takeIf { it.type == RcDimensionType.WEIGHT }?.value
        }
      weightWord?.let { state.resolve(it).coerceAtLeast(0f) } ?: 0f
    }
    // AndroidX measures an unweighted child before the fit test and leaves a weighted one
    // unmeasured — zero on the main axis — until the leftover space is distributed, so a weight
    // can never push an unweighted sibling out of the container.
    //
    // The fit test is against the child's *natural* size, so it measures with the main axis
    // unbounded — `computeVisibleChildren` passes `Float.MAX_VALUE` there. Measuring within the
    // container instead clamps an oversized child to exactly the space available, so it always
    // "fits": a 100 px child in a 60 px row was kept and drawn squeezed to 60 rather than dropped.
    // A kept child fits by construction, so its unbounded measurement is also its final one.
    val fitConstraints =
      if (orientation == RcCollapseOrientation.Horizontal) {
        childConstraints.copy(maxWidth = Constraints.Infinity)
      } else {
        childConstraints.copy(maxHeight = Constraints.Infinity)
      }
    val placeables = arrayOfNulls<Placeable>(measurables.size)
    measurables.forEachIndexed { index, measurable ->
      if (weights[index] <= 0f) placeables[index] = measurable.measure(fitConstraints)
    }
    val mainSizes = placeables.map {
      when {
        it == null -> 0
        orientation == RcCollapseOrientation.Horizontal -> it.width
        else -> it.height
      }
    }
    val priorities = children.map { child ->
      val priority = child.modifiers.collapsiblePriority
      val expectedOrientation =
        if (orientation == RcCollapseOrientation.Horizontal) {
          RcCollapsiblePriorityModifier.HORIZONTAL
        } else {
          RcCollapsiblePriorityModifier.VERTICAL
        }
      if (priority?.orientation == expectedOrientation) state.resolve(priority.priority)
      else Float.MAX_VALUE
    }
    val maximumMain =
      if (orientation == RcCollapseOrientation.Horizontal) constraints.maxWidth
      else constraints.maxHeight
    val retained = selectCollapsibleChildren(mainSizes, priorities, maximumMain)
    val retainedIndices = retained.indices.filter { retained[it] }
    // A GONE child measures zero on the main axis, so it always "fits" and is always retained; it
    // still shows nothing. The container collapses when nothing it kept is visible.
    collapse.collapsed = retainedIndices.none { index ->
      val visibility = children[index].modifiers.visibility
      visibility == null || androidXVisibility(state.integer(visibility.visibilityId) ?: 0) != 0
    }
    // Distribute the main-axis space the retained unweighted children left, in proportion to each
    // retained weighted child's weight, and measure those children at their share.
    val totalWeight =
      retainedIndices.fold(0f) { total, index ->
        if (weights[index] > 0f) total + weights[index] else total
      }
    if (totalWeight > 0f) {
      // The gaps are laid out between the retained children, so they come out of the space the
      // weights divide — the vendored reference charges `neededSpacing` into `usedUnweighted` the
      // same way. Without this the shares fill the whole axis, the container constrains the result,
      // and the last child is clipped by exactly the total gap.
      val gaps = spacing * (retainedIndices.size - 1).coerceAtLeast(0)
      val usedUnweighted =
        retainedIndices.fold(gaps) { used, index ->
          if (weights[index] <= 0f) used + mainSizes[index] else used
        }
      val remaining = (maximumMain - usedUnweighted).coerceAtLeast(0)
      retainedIndices.forEach { index ->
        if (weights[index] <= 0f) return@forEach
        val share = (remaining * (weights[index] / totalWeight)).toInt().coerceAtLeast(0)
        placeables[index] =
          measurables[index].measure(
            if (orientation == RcCollapseOrientation.Horizontal) {
              childConstraints.copy(minWidth = share, maxWidth = share)
            } else {
              childConstraints.copy(minHeight = share, maxHeight = share)
            }
          )
      }
    }
    val retainedMainSizes =
      retainedIndices
        .map { index ->
          val placeable = requireNotNull(placeables[index])
          if (orientation == RcCollapseOrientation.Horizontal) placeable.width else placeable.height
        }
        .toIntArray()
    val retainedCrossSize =
      retainedIndices.maxOfOrNull { index ->
        val placeable = requireNotNull(placeables[index])
        if (orientation == RcCollapseOrientation.Horizontal) placeable.height else placeable.width
      } ?: 0
    val naturalMain =
      retainedMainSizes.sum() + spacing * (retainedMainSizes.size - 1).coerceAtLeast(0)
    val width =
      constraints.constrainWidth(
        if (orientation == RcCollapseOrientation.Horizontal) naturalMain else retainedCrossSize
      )
    val height =
      constraints.constrainHeight(
        if (orientation == RcCollapseOrientation.Horizontal) retainedCrossSize else naturalMain
      )
    val mainAvailable = if (orientation == RcCollapseOrientation.Horizontal) width else height
    val mainPositions =
      arrangeLinear(
        mainAvailable,
        retainedMainSizes,
        mainPositioning,
        spacing,
        reverse =
          orientation == RcCollapseOrientation.Horizontal && layoutDirection == LayoutDirection.Rtl,
      )
    val alignedCrossPositions =
      if (
        orientation == RcCollapseOrientation.Horizontal &&
          retainedIndices.any { children[it].modifiers.alignBy != null }
      ) {
        val retainedAnchors = retainedIndices.map { index ->
          resolveAlignByAnchor(
            children[index].modifiers.alignBy,
            requireNotNull(placeables[index]),
            state,
          )
        }
        alignByCrossPositions(height, retainedCrossSize, crossPositioning, retainedAnchors)
      } else {
        null
      }
    layout(width, height) {
      retainedIndices.forEachIndexed { retainedIndex, childIndex ->
        val placeable = requireNotNull(placeables[childIndex])
        val crossAvailable = if (orientation == RcCollapseOrientation.Horizontal) height else width
        val crossSize =
          if (orientation == RcCollapseOrientation.Horizontal) placeable.height else placeable.width
        val crossPosition =
          alignedCrossPositions?.get(retainedIndex)
            ?: arrangeLinear(
              crossAvailable,
              intArrayOf(crossSize),
              crossPositioning,
              spacing = 0,
              reverse =
                orientation == RcCollapseOrientation.Vertical &&
                  layoutDirection == LayoutDirection.Rtl,
            )[0]
        if (orientation == RcCollapseOrientation.Horizontal) {
          placeable.place(mainPositions[retainedIndex], crossPosition)
        } else {
          placeable.place(crossPosition, mainPositions[retainedIndex])
        }
      }
    }
  }
}

/** Whether a collapsible layout kept none of its children on its last measure. */
private class RcCollapse {
  var collapsed by mutableStateOf(false)
}

/**
 * Makes a collapsible layout that kept nothing GONE: no space in its parent and nothing drawn,
 * background included.
 *
 * AndroidX's `computeVisibleChildren` marks the container itself GONE when no child fits, so it
 * contributes nothing to its parent and paints nothing. Only the collapsible's own measure knows
 * that, and it runs *inside* the size and decoration modifiers — an exact `width(60)` outside it
 * would still claim 60 px and the background would still paint across them. This sits outside the
 * whole chain: it measures the component, which is where the verdict is written, and then declines
 * to place it at all.
 */
private fun Modifier.goneWhenCollapsed(collapse: RcCollapse): Modifier =
  layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    if (collapse.collapsed) layout(0, 0) {}
    else layout(placeable.width, placeable.height) { placeable.place(0, 0) }
  }

/** AndroidX alpha18 priority sort and first-overflow cutoff; spacing is deliberately excluded. */
internal fun selectCollapsibleChildren(
  mainSizes: List<Int>,
  priorities: List<Float>,
  maximumMain: Int,
): BooleanArray {
  require(mainSizes.size == priorities.size)
  val retained = BooleanArray(mainSizes.size)
  val ranked =
    mainSizes.indices.sortedWith { left, right -> (priorities[right] - priorities[left]).toInt() }
  var used = 0
  for (index in ranked) {
    if (used + mainSizes[index] > maximumMain) break
    retained[index] = true
    used += mainSizes[index]
  }
  return retained
}

private fun RcTextLayout.composeTextAlign(): TextAlign = androidXTextAlign(textAlign)

private fun androidXTextAlign(value: Int): TextAlign =
  when (value) {
    RcTextLayout.ALIGN_LEFT -> TextAlign.Left
    RcTextLayout.ALIGN_RIGHT -> TextAlign.Right
    RcTextLayout.ALIGN_CENTER -> TextAlign.Center
    // AndroidPaintContext maps this alignment field to ALIGN_NORMAL. CoreText's property 17 is
    // the independent switch that enables inter-word/inter-character justification.
    RcTextLayout.ALIGN_JUSTIFY -> TextAlign.Start
    RcTextLayout.ALIGN_START -> TextAlign.Start
    RcTextLayout.ALIGN_END -> TextAlign.End
    else -> error("Unknown AndroidX text alignment $value")
  }

private fun RcTextLayout.composeTextOverflow(): TextOverflow = androidXTextOverflow(overflow)

private fun androidXTextOverflow(value: Int): TextOverflow =
  when (value) {
    RcTextLayout.OVERFLOW_CLIP -> TextOverflow.Clip
    RcTextLayout.OVERFLOW_VISIBLE -> TextOverflow.Visible
    RcTextLayout.OVERFLOW_ELLIPSIS -> TextOverflow.Ellipsis
    RcTextLayout.OVERFLOW_START_ELLIPSIS -> TextOverflow.StartEllipsis
    RcTextLayout.OVERFLOW_MIDDLE_ELLIPSIS -> TextOverflow.MiddleEllipsis
    else -> error("Unsupported AndroidX text overflow $value")
  }

private fun androidXMaxLines(overflow: Int, maxLines: Int): Int =
  if (
    maxLines > 1 &&
      (overflow == RcTextLayout.OVERFLOW_CLIP || overflow == RcTextLayout.OVERFLOW_VISIBLE)
  ) {
    Int.MAX_VALUE
  } else {
    maxLines
  }

private fun List<RcTextStyleProperty>.intProperty(id: Int, default: Int): Int =
  filterIsInstance<RcTextStyleProperty.IntValue>().lastOrNull { it.id == id }?.value ?: default

private fun List<RcTextStyleProperty>.floatProperty(id: Int, default: Float): RcFloatWord =
  filterIsInstance<RcTextStyleProperty.FloatValue>().lastOrNull { it.id == id }?.value
    ?: RcFloatWord.literal(default)

private fun List<RcTextStyleProperty>.booleanProperty(id: Int, default: Boolean): Boolean =
  filterIsInstance<RcTextStyleProperty.BooleanValue>().lastOrNull { it.id == id }?.value ?: default

private fun List<RcTextStyleProperty>.intArrayProperty(id: Int): List<Int> =
  filterIsInstance<RcTextStyleProperty.IntArrayValue>().lastOrNull { it.id == id }?.values.orEmpty()

private fun List<RcTextStyleProperty>.floatArrayProperty(id: Int): List<RcFloatWord> =
  filterIsInstance<RcTextStyleProperty.FloatArrayValue>()
    .lastOrNull { it.id == id }
    ?.values
    .orEmpty()

/** Mirrors Component.Visibility, including the override-bit precedence used by AndroidX. */
internal fun androidXVisibility(value: Int): Int =
  when {
    value and 32 == 32 -> 1
    value and 16 == 16 -> 0
    value and 64 == 64 -> 2
    value == 1 -> 1
    value == 2 -> 2
    else -> 0
  }

internal fun boxAlignment(horizontal: Int, vertical: Int): Alignment =
  // 0 is "unset", not a positioning value: AndroidX numbers horizontal 1/2/3 and vertical 4/2/5,
  // and
  // writes 0 where a component states no preference — a spacer, or a box a macro expanded. Erroring
  // on it refused the whole document over a field that simply was not filled in, so it resolves to
  // the start/top default a Box has when nothing asks otherwise.
  when ((horizontal.takeIf { it != 0 } ?: 1) to (vertical.takeIf { it != 0 } ?: 4)) {
    1 to 4 -> Alignment.TopStart
    2 to 4 -> Alignment.TopCenter
    3 to 4 -> Alignment.TopEnd
    1 to 2 -> Alignment.CenterStart
    2 to 2 -> Alignment.Center
    3 to 2 -> Alignment.CenterEnd
    1 to 5 -> Alignment.BottomStart
    2 to 5 -> Alignment.BottomCenter
    3 to 5 -> Alignment.BottomEnd
    else -> error("Unknown AndroidX box alignment horizontal=$horizontal vertical=$vertical")
  }

internal fun rowAlignment(vertical: Int): Alignment.Vertical =
  when (vertical) {
    // 0 is "unset", not a positioning value — see [boxAlignment]. AndroidX lays an unpositioned
    // row's children out from the top, which is what this player does for a document that states
    // nothing at all.
    0,
    4 -> Alignment.Top
    2 -> Alignment.CenterVertically
    5 -> Alignment.Bottom
    else -> error("Unknown AndroidX row vertical position $vertical")
  }

internal fun columnAlignment(horizontal: Int): Alignment.Horizontal =
  when (horizontal) {
    0,
    1 -> Alignment.Start
    2 -> Alignment.CenterHorizontally
    3 -> Alignment.End
    else -> error("Unknown AndroidX column horizontal position $horizontal")
  }

private class RcHorizontalArrangement(
  private val positioning: Int,
  private val visualSpacing: Dp,
  override val spacing: Dp = visualSpacing,
) : Arrangement.Horizontal {
  override fun Density.arrange(
    totalSize: Int,
    sizes: IntArray,
    layoutDirection: LayoutDirection,
    outPositions: IntArray,
  ) {
    arrangeLinear(
      totalSize,
      sizes,
      positioning,
      visualSpacing.roundToPx(),
      reverse = layoutDirection == LayoutDirection.Rtl,
      outPositions = outPositions,
    )
  }
}

private class RcVerticalArrangement(private val positioning: Int, override val spacing: Dp) :
  Arrangement.Vertical {
  override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
    arrangeLinear(
      totalSize,
      sizes,
      positioning,
      spacing.roundToPx(),
      reverse = false,
      outPositions = outPositions,
    )
  }
}

/** AndroidX fields that are pixels in LEGACY/PIXELS documents and dp in DP documents. */
internal fun rcDpTypedPixels(value: Float, density: Float, densityBehavior: Int): Float =
  if (densityBehavior == RcHeader.DENSITY_BEHAVIOR_DP) value * density else value

private fun RcPlayerState.dpTypedPixels(value: Float, density: Density): Float =
  rcDpTypedPixels(value, density.density, document.header.densityBehavior)

private fun RcPlayerState.dpTypedDp(value: Float, density: Density): Dp =
  with(density) { dpTypedPixels(value, density).toDp() }

/** DimensionIn is the exception: AndroidX treats LEGACY and DP as dp, PIXELS as pixels. */
internal fun rcDimensionConstraintDp(value: Float, density: Float, densityBehavior: Int): Float =
  if (densityBehavior == RcHeader.DENSITY_BEHAVIOR_PIXELS) value / density else value

/** AndroidX RowLayout/ColumnLayout positioning, including its additive spacedBy behaviour. */
internal fun arrangeLinear(
  totalSize: Int,
  sizes: IntArray,
  positioning: Int,
  spacing: Int,
  reverse: Boolean,
  outPositions: IntArray = IntArray(sizes.size),
): IntArray {
  require(outPositions.size >= sizes.size)
  if (sizes.isEmpty()) return outPositions
  val childSize = sizes.sum().toFloat()
  val contentSize = childSize + spacing * (sizes.size - 1)
  var distributedGap = 0f
  var current =
    when (positioning) {
      // 0 is "unset": a container that states no positioning preference arranges from the start,
      // the same as the explicit start value (1 horizontally, 4 vertically).
      0,
      1,
      4 -> 0f
      2 -> (totalSize - contentSize) / 2f
      3,
      5 -> totalSize - contentSize
      6 -> {
        if (sizes.size > 1) distributedGap = (totalSize - childSize) / (sizes.size - 1)
        if (sizes.size == 1) (totalSize - contentSize) / 2f else 0f
      }
      7 -> {
        distributedGap = (totalSize - childSize) / (sizes.size + 1)
        distributedGap
      }
      8 -> {
        distributedGap = (totalSize - childSize) / sizes.size
        distributedGap / 2f
      }
      else -> error("Unknown AndroidX linear position $positioning")
    }
  sizes.forEachIndexed { index, size ->
    val position = current.roundToInt()
    outPositions[index] = if (reverse) totalSize - position - size else position
    current += size + spacing
    if (positioning in 6..8) current += distributedGap
  }
  return outPositions
}

@Composable
private fun Modifier.applyComponentModifiers(
  modifiers: RcLayoutModifiers,
  state: RcPlayerState,
  geometryComponentIds: List<Int>,
  fillMissingDimensions: Boolean,
  canvasOperations: List<RcLinkedNode>?,
  textMeasurer: TextMeasurer,
  images: MutableMap<Int, ImageBitmap>,
  theme: Int,
): Modifier {
  val density = androidx.compose.ui.platform.LocalDensity.current
  val offscreenTargets = LocalRcOffscreenTargets.current
  val typefaces = LocalRcTypefaces.current
  val drawObserver = LocalRcDrawObserver.current
  // A `RippleModifier` is an `Indication` at its wire position, driven by the presses of the
  // component's clickable through this shared source (see `applyAndroidXRipple`).
  val rippleInteractions =
    if (modifiers.ordered.any { it is RcRippleModifier }) remember { MutableInteractionSource() }
    else null
  var result =
    if (modifiers.layoutComputes.isEmpty()) this
    else {
      val computeBase =
        if (modifiers.layoutComputes.any { it.operation.type == RcLayoutCompute.MEASURE }) {
          this.clipToBounds()
        } else {
          this
        }
      computeBase.applyLayoutComputes(modifiers, state)
    }
  modifiers.dimensionConstraints.forEach { constraint ->
    result = result.applyDimensionConstraint(constraint, state, density)
  }
  if (fillMissingDimensions && modifiers.width == null) result = result.fillMaxWidth()
  if (fillMissingDimensions && modifiers.height == null) result = result.fillMaxHeight()
  var appliedWidth = false
  var appliedHeight = false
  var appliedCanvasOperations = false
  var graphicsLayerApplied = false

  /**
   * Appends the component's graphics layer the first time something that draws asks for it.
   *
   * Wire order puts the layer after the component's size and background modifiers, and a layer
   * appended there composites nothing: the background's `drawBehind` has already painted to the
   * parent canvas. Deferring it to just before the first drawing modifier puts the background, the
   * border and the canvas stream *inside* the layer, which is what the reference does — its layer
   * covers the component as a whole.
   */
  @Composable
  fun applyPendingGraphicsLayer(modifier: Modifier): Modifier {
    val layer = modifiers.graphicsLayer ?: return modifier
    if (graphicsLayerApplied) return modifier
    graphicsLayerApplied = true
    return modifier.applyGraphicsLayer(layer, state)
  }

  fun applyCanvasOperations(modifier: Modifier): Modifier {
    val operations = canvasOperations ?: return modifier
    appliedCanvasOperations = true
    return modifier.drawWithContent {
      rcTrace(RcTraceCategory.FRAME, "rc:drawCanvas") {
        drawOperations(
          operations,
          state,
          RcPaintState(typefaces, drawObserver),
          mutableMapOf(),
          textMeasurer,
          images,
          RcFloatFunctionRuntime(),
          theme,
          filterTheme = true,
          offscreenTargets = offscreenTargets,
          drawContent = { drawContent() },
        )
      }
    }
  }
  var scrollApplied = false
  var operationIndex = 0
  while (operationIndex < modifiers.ordered.size) {
    if (modifiers.scrollPosition == operationIndex) {
      modifiers.scroll?.let {
        result = result.applyAndroidXScroll(it, state, geometryComponentIds)
        scrollApplied = true
      }
    }
    val operation = modifiers.ordered[operationIndex]
    // AndroidX paints CanvasOperations at the component's full bounds, temporarily undoing the
    // content-padding inset. Put the Compose draw wrapper outside the first padding modifier even
    // when the wire DrawContent marker follows it.
    if (operation is RcPaddingModifier && !appliedCanvasOperations) {
      result = applyCanvasOperations(applyPendingGraphicsLayer(result))
    }
    result =
      when (operation) {
        is RcWidthModifier ->
          if (appliedWidth) result
          else {
            appliedWidth = true
            result.applyWidth(operation, state, density)
          }
        is RcHeightModifier ->
          if (appliedHeight) result
          else {
            appliedHeight = true
            result.applyHeight(operation, state, density)
          }
        is RcPaddingModifier -> {
          var left = 0f
          var top = 0f
          var right = 0f
          var bottom = 0f
          var next = operationIndex
          while (next < modifiers.ordered.size) {
            if (next > operationIndex && modifiers.scrollPosition == next) break
            val padding = modifiers.ordered[next] as? RcPaddingModifier ?: break
            left += state.dpTypedPixels(state.resolve(padding.left), density)
            top += state.dpTypedPixels(state.resolve(padding.top), density)
            right += state.dpTypedPixels(state.resolve(padding.right), density)
            bottom += state.dpTypedPixels(state.resolve(padding.bottom), density)
            next++
          }
          operationIndex = next - 1
          result.rcPaddingPixels(left = left, top = top, right = right, bottom = bottom, density)
        }
        is RcOffsetModifier ->
          result.offset {
            IntOffset(
              state.dpTypedPixels(state.resolve(operation.x), density).roundToInt(),
              state.dpTypedPixels(state.resolve(operation.y), density).roundToInt(),
            )
          }
        is RcZIndexModifier -> result.zIndex(state.resolve(operation.value))
        is RcBackgroundModifier,
        is RcBorderModifier,
        is RcClipRectModifier,
        is RcRoundedClipRectModifier ->
          applyPendingGraphicsLayer(result).applyPaintDecorator(operation, state)
        is RcRippleModifier ->
          applyPendingGraphicsLayer(result)
            .applyAndroidXRipple(
              interactions = checkNotNull(rippleInteractions),
              emitOwnPresses = modifiers.clicks.isEmpty(),
            )
        is RcGraphicsLayerModifier -> result
        is RcMarqueeModifier -> result.applyAndroidXMarquee(operation, state)
        is RcNoArg ->
          if (
            operation.opcode == RcOpcodes.MODIFIER_DRAW_CONTENT &&
              !appliedCanvasOperations &&
              modifiers.ordered.drop(operationIndex + 1).none { it is RcPaddingModifier }
          ) {
            applyCanvasOperations(applyPendingGraphicsLayer(result))
          } else result
        else -> result
      }
    operationIndex++
  }
  if (!scrollApplied) {
    modifiers.scroll?.let { result = result.applyAndroidXScroll(it, state, geometryComponentIds) }
  }
  if (!appliedCanvasOperations) {
    // The layer goes on first so the canvas stream is drawn *inside* it: a component whose paint
    // arrives as canvas operations — `modifier_graphics_layer`'s scaled rect, the translation
    // tween's block — has nothing else for the layer to transform, and a layer applied after the
    // stream leaves the drawing untransformed.
    result = applyCanvasOperations(applyPendingGraphicsLayer(result))
  }
  // No indication on the clickable itself: AndroidX draws no press feedback unless the document
  // asks for a ripple, and Compose's default indication would wash the component on press, hover
  // and focus. When it does ask, the clickable's presses reach the ripple's `Indication` through
  // `rippleInteractions`.
  if (modifiers.clicks.any { it.type != RcClickActionType.CLICK }) {
    result = result.applyAndroidXMultiClick(modifiers.clicks, state, rippleInteractions)
  } else if (modifiers.clicks.isNotEmpty()) {
    result =
      result.clickable(interactionSource = rippleInteractions, indication = null) {
        modifiers.clicks.forEach(state::executeClick)
      }
  }
  if (modifiers.touchActions.isNotEmpty()) {
    result = result.applyAndroidXTouchActions(modifiers, state)
  }
  modifiers.accessibility.forEach { semantics ->
    result =
      result.applyAccessibilitySemantics(
        semantics,
        state,
        hasClickAction = modifiers.clicks.isNotEmpty(),
      )
  }
  return result
}

/**
 * Compose's `padding`, fed whole-pixel edges.
 *
 * AndroidX keeps padding in physical pixels until measure, then rounds the combined inset on each
 * axis. Compose's Dp padding rounds each edge independently, which adds a pixel whenever both wire
 * edges end in .5 (for example 31.5px + 31.5px must occupy 63px, not 64px). So the start edge is
 * rounded on its own and the end edge takes the rest of the rounded total. Whole pixels survive the
 * Dp round trip exactly, and the combined inset matches AndroidX.
 */
private fun Modifier.rcPaddingPixels(
  left: Float,
  top: Float,
  right: Float,
  bottom: Float,
  density: Density,
): Modifier {
  val start = left.coerceAtLeast(0f).roundToInt()
  val topEdge = top.coerceAtLeast(0f).roundToInt()
  val end = rcCombinedPaddingPixels(left, right) - start
  val bottomEdge = rcCombinedPaddingPixels(top, bottom) - topEdge
  return with(density) {
    this@rcPaddingPixels.padding(
      start = start.toDp(),
      top = topEdge.toDp(),
      end = end.toDp(),
      bottom = bottomEdge.toDp(),
    )
  }
}

internal fun rcCombinedPaddingPixels(first: Float, second: Float): Int =
  (first.coerceAtLeast(0f) + second.coerceAtLeast(0f)).roundToInt()

/** Publishes the pointer position to the document, as AndroidX does on every touch event. */
private data class RcTouchPositionElement(
  val state: RcPlayerState,
  val documentWidth: Float,
  val documentHeight: Float,
  val onRepaint: () -> Unit,
) : ModifierNodeElement<RcTouchPositionNode>() {
  override fun create(): RcTouchPositionNode =
    RcTouchPositionNode(state, documentWidth, documentHeight, onRepaint)

  override fun update(node: RcTouchPositionNode) {
    node.state = state
    node.documentWidth = documentWidth
    node.documentHeight = documentHeight
    node.onRepaint = onRepaint
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "androidXTouchPosition"
  }
}

/**
 * Observes, and never consumes: runs on the initial pass so a component that handles the same
 * pointer still sees it, and the position is already published when its actions read it.
 */
private class RcTouchPositionNode(
  var state: RcPlayerState,
  var documentWidth: Float,
  var documentHeight: Float,
  var onRepaint: () -> Unit,
) : Modifier.Node(), PointerInputModifierNode {
  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
    if (pass != PointerEventPass.Initial) return
    val change =
      pointerEvent.changes.firstOrNull { it.pressed || it.changedToUpIgnoreConsumed() } ?: return
    val transform =
      computeRootTransform(
        documentWidth = documentWidth,
        documentHeight = documentHeight,
        viewportWidth = bounds.width.toFloat(),
        viewportHeight = bounds.height.toFloat(),
        behavior = state.rootContentBehavior,
      )
    val x = (change.position.x - transform.translateX) / transform.scaleX
    val y = (change.position.y - transform.translateY) / transform.scaleY
    if (state.publishTouchPosition(x, y)) onRepaint()
  }

  override fun onCancelPointerInput() = Unit
}

private fun Modifier.applyAndroidXClickAreas(
  areas: List<RcClickArea>,
  state: RcPlayerState,
  documentWidth: Float,
  documentHeight: Float,
  rootContentBehavior: RcRootContentBehavior?,
): Modifier =
  if (areas.isEmpty()) this
  else then(RcClickAreasElement(areas, state, documentWidth, documentHeight, rootContentBehavior))

private data class RcClickAreasElement(
  val areas: List<RcClickArea>,
  val state: RcPlayerState,
  val documentWidth: Float,
  val documentHeight: Float,
  val rootContentBehavior: RcRootContentBehavior?,
) : ModifierNodeElement<RcClickAreasNode>() {
  override fun create(): RcClickAreasNode =
    RcClickAreasNode(areas, state, documentWidth, documentHeight, rootContentBehavior)

  override fun update(node: RcClickAreasNode) {
    node.areas = areas
    node.state = state
    node.documentWidth = documentWidth
    node.documentHeight = documentHeight
    node.rootContentBehavior = rootContentBehavior
    node.invalidateSemantics()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "androidXClickAreas"
  }
}

private class RcClickAreasNode(
  var areas: List<RcClickArea>,
  var state: RcPlayerState,
  var documentWidth: Float,
  var documentHeight: Float,
  var rootContentBehavior: RcRootContentBehavior?,
) :
  Modifier.Node(),
  PointerInputModifierNode,
  SemanticsModifierNode,
  CompositionLocalConsumerModifierNode {
  private var pressed = false
  private var longPressed = false
  private var downPosition = Offset.Zero
  private var pendingClickPosition = Offset.Zero
  private var waitingForSecondClick = false
  private var longPressJob: Job? = null
  private var singleClickJob: Job? = null

  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
    if (pass != PointerEventPass.Main) return
    val down = pointerEvent.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
    if (!pressed && down != null) {
      pressed = true
      longPressed = false
      downPosition = down.position
      longPressJob?.cancel()
      longPressJob = coroutineScope.launch {
        delay(currentValueOf(LocalViewConfiguration).longPressTimeoutMillis)
        if (pressed) {
          pressed = false
          longPressed = true
          waitingForSecondClick = false
          singleClickJob?.cancel()
        }
      }
    }
    if (
      pressed &&
        pointerEvent.changes.any {
          it.pressed &&
            (it.position - downPosition).getDistance() >
              currentValueOf(LocalViewConfiguration).touchSlop
        }
    ) {
      pressed = false
      longPressJob?.cancel()
    }
    if (pressed && pointerEvent.changes.isNotEmpty() && pointerEvent.changes.all { !it.pressed }) {
      pressed = false
      longPressJob?.cancel()
      val up = pointerEvent.changes.firstOrNull { it.changedToUpIgnoreConsumed() }
      if (!longPressed && up != null) completeClick(up.position, bounds)
    }
  }

  override fun onCancelPointerInput() {
    pressed = false
    longPressJob?.cancel()
  }

  override fun onDetach() {
    pressed = false
    waitingForSecondClick = false
    longPressJob?.cancel()
    singleClickJob?.cancel()
  }

  override fun SemanticsPropertyReceiver.applySemantics() {
    val descriptions = areas.mapNotNull { state.text(it.contentDescriptionId) }
    if (descriptions.isNotEmpty()) contentDescription = descriptions.joinToString(", ")
    role = Role.Button
    areas.firstOrNull()?.let { first ->
      onClick {
        state.executeClickArea(first)
        true
      }
    }
    customActions = areas.map { area ->
      CustomAccessibilityAction(
        label = state.text(area.contentDescriptionId).orEmpty(),
        action = {
          state.executeClickArea(area)
          true
        },
      )
    }
  }

  private fun completeClick(position: Offset, bounds: IntSize) {
    if (waitingForSecondClick) {
      waitingForSecondClick = false
      singleClickJob?.cancel()
      return
    }
    waitingForSecondClick = true
    val transform =
      computeRootTransform(
        documentWidth = documentWidth,
        documentHeight = documentHeight,
        viewportWidth = bounds.width.toFloat(),
        viewportHeight = bounds.height.toFloat(),
        behavior = rootContentBehavior,
      )
    pendingClickPosition =
      Offset(
        (position.x - transform.translateX) / transform.scaleX,
        (position.y - transform.translateY) / transform.scaleY,
      )
    singleClickJob = coroutineScope.launch {
      delay(currentValueOf(LocalViewConfiguration).doubleTapTimeoutMillis)
      if (waitingForSecondClick) {
        waitingForSecondClick = false
        state.executeClickAreasAt(pendingClickPosition.x, pendingClickPosition.y)
      }
    }
  }
}

/** AndroidX player-view's 21-entry haptic table mapped to the portable CMP vocabulary. */
internal fun HapticFeedback.performAndroidXHaptic(type: RcHapticType) {
  val composeType =
    when (type.wireValue % 21) {
      0 -> null
      1 -> HapticFeedbackType.LongPress
      2 -> HapticFeedbackType.VirtualKey
      3 -> HapticFeedbackType.KeyboardTap
      4 -> HapticFeedbackType.SegmentTick
      5 -> HapticFeedbackType.ContextClick
      6,
      7 -> HapticFeedbackType.KeyboardTap
      8 -> HapticFeedbackType.VirtualKey
      9 -> HapticFeedbackType.TextHandleMove
      10 -> HapticFeedbackType.GestureThresholdActivate
      11 -> HapticFeedbackType.GestureEnd
      12 -> HapticFeedbackType.Confirm
      13 -> HapticFeedbackType.Reject
      14 -> HapticFeedbackType.ToggleOn
      15 -> HapticFeedbackType.ToggleOff
      16 -> HapticFeedbackType.GestureThresholdActivate
      17 -> HapticFeedbackType.GestureEnd
      18 -> HapticFeedbackType.GestureThresholdActivate
      19 -> HapticFeedbackType.SegmentTick
      20 -> HapticFeedbackType.SegmentFrequentTick
      else -> error("Negative AndroidX haptic type ${type.wireValue}")
    }
  if (composeType != null) performHapticFeedback(composeType)
}

/**
 * `MultiClickModifier`s: one recogniser for every block of a component, so single, long and double
 * clicks cannot compete, with `CLICK` blocks running as the single click.
 *
 * This stays hand-rolled rather than `combinedClickable`, which was tried and broke two binding
 * conformance checks (`interactivity_multi_click_types`): `combinedClickable` ignores a second tap
 * that lands within `doubleTapMinTimeMillis` of the first, and treats a press that starts inside
 * the double-tap window as a second-tap candidate that can no longer long-press. The reference
 * player has neither rule. Press feedback still goes through Compose: presses are emitted into
 * [interactionSource], which drives the ripple `Indication` when the document asks for one.
 */
@Composable
private fun Modifier.applyAndroidXMultiClick(
  blocks: List<RcClickActionBlock>,
  state: RcPlayerState,
  interactionSource: MutableInteractionSource?,
): Modifier {
  val hapticFeedback = LocalHapticFeedback.current
  return then(RcMultiClickElement(blocks, state, hapticFeedback, interactionSource))
}

private data class RcMultiClickElement(
  val blocks: List<RcClickActionBlock>,
  val state: RcPlayerState,
  val hapticFeedback: HapticFeedback,
  val interactionSource: MutableInteractionSource?,
) : ModifierNodeElement<RcMultiClickNode>() {
  override fun create(): RcMultiClickNode =
    RcMultiClickNode(blocks, state, hapticFeedback, interactionSource)

  override fun update(node: RcMultiClickNode) {
    node.blocks = blocks
    node.state = state
    node.hapticFeedback = hapticFeedback
    if (node.interactionSource != interactionSource) {
      node.cancelPress()
      node.interactionSource = interactionSource
    }
    node.invalidateSemantics()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "androidXMultiClick"
  }
}

private class RcMultiClickNode(
  var blocks: List<RcClickActionBlock>,
  var state: RcPlayerState,
  var hapticFeedback: HapticFeedback,
  var interactionSource: MutableInteractionSource?,
) :
  Modifier.Node(),
  PointerInputModifierNode,
  SemanticsModifierNode,
  CompositionLocalConsumerModifierNode {
  private var pressed = false
  private var longPressDispatched = false
  private var downPosition = Offset.Zero
  private var waitingForSecondClick = false
  private var longPressJob: Job? = null
  private var singleClickJob: Job? = null
  private var press: PressInteraction.Press? = null

  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
    if (pass != PointerEventPass.Main) return
    val down = pointerEvent.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
    if (!pressed && down != null) {
      pressed = true
      longPressDispatched = false
      downPosition = down.position
      startPress(down.position)
      longPressJob?.cancel()
      if (longActions.isNotEmpty()) {
        longPressJob = coroutineScope.launch {
          delay(currentValueOf(LocalViewConfiguration).longPressTimeoutMillis)
          if (pressed) {
            pressed = false
            longPressDispatched = true
            waitingForSecondClick = false
            singleClickJob?.cancel()
            endPress()
            dispatch(longActions)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
          }
        }
      }
    }
    if (
      pressed &&
        pointerEvent.changes.any {
          it.pressed &&
            (it.position - downPosition).getDistance() >
              currentValueOf(LocalViewConfiguration).touchSlop
        }
    ) {
      pressed = false
      longPressJob?.cancel()
      cancelPress()
    }
    if (pressed && pointerEvent.changes.isNotEmpty() && pointerEvent.changes.all { !it.pressed }) {
      pressed = false
      longPressJob?.cancel()
      endPress()
      if (!longPressDispatched && pointerEvent.changes.all { it.changedToUpIgnoreConsumed() }) {
        completeClick()
      }
    }
  }

  override fun onCancelPointerInput() {
    pressed = false
    longPressJob?.cancel()
    cancelPress()
  }

  override fun onDetach() {
    longPressJob?.cancel()
    singleClickJob?.cancel()
    pressed = false
    waitingForSecondClick = false
    cancelPress()
  }

  private fun startPress(position: Offset) {
    cancelPress()
    val source = interactionSource ?: return
    press = PressInteraction.Press(position).also(source::tryEmit)
  }

  private fun endPress() {
    press?.let { interactionSource?.tryEmit(PressInteraction.Release(it)) }
    press = null
  }

  fun cancelPress() {
    press?.let { interactionSource?.tryEmit(PressInteraction.Cancel(it)) }
    press = null
  }

  override fun SemanticsPropertyReceiver.applySemantics() {
    role = Role.Button
    onClick {
      dispatch(singleActions)
      performSingleHaptic()
      true
    }
    if (longActions.isNotEmpty()) {
      onLongClick {
        dispatch(longActions)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        true
      }
    }
  }

  private fun completeClick() {
    if (doubleActions.isEmpty()) {
      dispatch(singleActions)
      performSingleHaptic()
    } else if (waitingForSecondClick) {
      waitingForSecondClick = false
      singleClickJob?.cancel()
      dispatch(doubleActions)
      hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
    } else {
      waitingForSecondClick = true
      singleClickJob = coroutineScope.launch {
        delay(currentValueOf(LocalViewConfiguration).doubleTapTimeoutMillis)
        if (waitingForSecondClick) {
          waitingForSecondClick = false
          dispatch(singleActions)
          performSingleHaptic()
        }
      }
    }
  }

  private fun dispatch(actions: List<RcClickActionBlock>) {
    actions.forEach(state::executeClick)
  }

  private fun performSingleHaptic() {
    if (singleActions.any { it.type == RcClickActionType.SINGLE }) {
      hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
    }
  }

  private val singleActions: List<RcClickActionBlock>
    get() = blocks.filter {
      it.type == RcClickActionType.CLICK || it.type == RcClickActionType.SINGLE
    }

  private val longActions: List<RcClickActionBlock>
    get() = blocks.filter { it.type == RcClickActionType.LONG }

  private val doubleActions: List<RcClickActionBlock>
    get() = blocks.filter { it.type == RcClickActionType.DOUBLE }
}

private fun Modifier.applyAndroidXTouchActions(
  modifiers: RcLayoutModifiers,
  state: RcPlayerState,
): Modifier = then(RcTouchActionsElement(modifiers.touchActions, state))

private data class RcTouchActionsElement(
  val actions: List<RcTouchActionBlock>,
  val state: RcPlayerState,
) : ModifierNodeElement<RcTouchActionsNode>() {
  override fun create(): RcTouchActionsNode = RcTouchActionsNode(actions, state)

  override fun update(node: RcTouchActionsNode) {
    node.actions = actions
    node.state = state
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "androidXTouchActions"
  }
}

private class RcTouchActionsNode(var actions: List<RcTouchActionBlock>, var state: RcPlayerState) :
  Modifier.Node(), PointerInputModifierNode {
  private var pressed: Boolean = false

  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
    if (pass != PointerEventPass.Main) return
    if (!pressed && pointerEvent.changes.any { it.changedToDownIgnoreConsumed() }) {
      pressed = true
      dispatch(RcTouchActionType.DOWN)
    }
    if (pressed && pointerEvent.changes.isNotEmpty() && pointerEvent.changes.all { !it.pressed }) {
      pressed = false
      if (pointerEvent.changes.all { it.changedToUpIgnoreConsumed() }) {
        dispatch(RcTouchActionType.UP)
      } else {
        dispatch(RcTouchActionType.CANCEL)
      }
    }
  }

  override fun onCancelPointerInput() {
    if (pressed) {
      pressed = false
      dispatch(RcTouchActionType.CANCEL)
    }
  }

  private fun dispatch(type: RcTouchActionType) {
    actions.filter { it.type == type }.forEach(state::executeTouch)
  }
}

/**
 * `MarqueeModifierOperation` carries exactly `basicMarquee`'s parameters, and AndroidX's embedded
 * Compose player maps it onto `basicMarquee` unconditionally; so does this. Spacing is a dp-typed
 * field (pixels in LEGACY/PIXELS documents) that may be a variable; velocity is dp per second.
 */
@Composable
private fun Modifier.applyAndroidXMarquee(
  operation: RcMarqueeModifier,
  state: RcPlayerState,
): Modifier {
  val density = androidx.compose.ui.platform.LocalDensity.current
  val spacing = state.dpTypedDp(state.resolve(operation.spacing), density)
  return basicMarquee(
    iterations = if (operation.iterations == -1) Int.MAX_VALUE else operation.iterations,
    animationMode =
      if (operation.animationMode == 0) MarqueeAnimationMode.Immediately
      else MarqueeAnimationMode.WhileFocused,
    repeatDelayMillis = state.resolve(operation.repeatDelayMillis).toInt().coerceAtLeast(0),
    initialDelayMillis = state.resolve(operation.initialDelayMillis).toInt().coerceAtLeast(0),
    spacing = MarqueeSpacing(spacing),
    velocity = state.resolve(operation.velocity).dp,
  )
}

@Composable
private fun Modifier.applyAndroidXScroll(
  block: RcScrollBlock,
  state: RcPlayerState,
  geometryComponentIds: List<Int> = emptyList(),
): Modifier {
  val operation = block.operation
  val touch =
    block.children
      .filterIsInstance<RcLinkedNode.Operation>()
      .mapNotNull { it.operation as? ee.schimke.composeai.rcplayer.protocol.RcTouchExpression }
      .singleOrNull()
  val initialPosition = state.resolve(operation.position).takeIf(Float::isFinite)?.roundToInt() ?: 0
  val scrollState = rememberScrollState(initialPosition.coerceAtLeast(0))
  val touchRuntime = touch?.let { remember(it) { RcTouchExpressionRuntime(it) } }

  LaunchedEffect(scrollState, operation, state) {
    snapshotFlow { scrollState.value to scrollState.maxValue }
      .collect { (position, maximum) ->
        operation.position.referencedId?.let { state.setFloat(it, position.toFloat()) }
        operation.max.referencedId?.let { state.setFloat(it, maximum.toFloat()) }
        operation.notchMax.referencedId?.let {
          state.setFloat(it, (maximum + scrollState.viewportSize).toFloat())
        }
        val contentExtent = (maximum + scrollState.viewportSize).toFloat()
        geometryComponentIds.forEach { componentId ->
          if (operation.direction == RcScrollModifier.VERTICAL) {
            state.publishComponentContentSize(componentId, height = contentExtent)
          } else {
            state.publishComponentContentSize(componentId, width = contentExtent)
          }
        }
      }
  }
  // A snapping stop mode settles the fling on the stop the touch expression picks, through
  // Compose's own snap fling rather than a second animation launched once the scroll goes idle.
  // AndroidX's embedded player maps the scroll's even-notch stop the same way
  // (third_party/rc-embedded-player `ScrollModifier.kt`); the stop itself stays the runtime's, so
  // ends, percent, absolute and single-notch stops keep their wire semantics.
  val snaps = touch != null && touch.stopMode in SnappingStopModes
  if (snaps && touchRuntime != null) {
    // A single-notch stop is bounded by the value the gesture started from.
    LaunchedEffect(scrollState, touchRuntime, state) {
      scrollState.interactionSource.interactions.collect { interaction ->
        if (interaction is DragInteraction.Start) {
          touchRuntime.onDown(scrollState.value.toFloat(), 0f, 0f, state::resolve)
        }
      }
    }
  }
  val flingBehavior =
    if (snaps && touchRuntime != null) {
      val snapProvider =
        remember(scrollState, touchRuntime, state) {
          object : SnapLayoutInfoProvider {
            override fun calculateSnapOffset(velocity: Float): Float {
              val current = scrollState.value.toFloat()
              val target =
                touchRuntime
                  .stopTarget(
                    currentValue = current,
                    minimum = 0f,
                    maximum = scrollState.maxValue.toFloat(),
                    resolve = state::resolve,
                  )
                  .roundToInt()
                  .coerceIn(0, scrollState.maxValue)
              return target - current
            }
          }
        }
      rememberSnapFlingBehavior(snapProvider)
    } else {
      ScrollableDefaults.flingBehavior()
    }

  val scrolled =
    if (operation.direction == RcScrollModifier.VERTICAL) {
      verticalScroll(scrollState, flingBehavior = flingBehavior)
    } else {
      horizontalScroll(scrollState, flingBehavior = flingBehavior)
    }
  if (!LocalRcInspection.current) return scrolled
  // The offset the children are drawn under, published so the tree reader can take it back out of
  // their positions and report it as `scroll_x`/`scroll_y` instead. Derived rather than read at
  // composition: a scroll does not recompose, so a plain read here would report the offset the
  // container had when it was last composed.
  val scrollOffset by
    remember(scrollState, operation.direction) {
      derivedStateOf {
        if (operation.direction == RcScrollModifier.VERTICAL) {
          Offset(0f, -scrollState.value.toFloat())
        } else {
          Offset(-scrollState.value.toFloat(), 0f)
        }
      }
    }
  return scrolled.semantics { rcScrollOffset = scrollOffset }
}

/** Stop modes whose release settles on a stop other than where the fling leaves the value. */
private val SnappingStopModes =
  setOf(
    ee.schimke.composeai.rcplayer.protocol.RcTouchExpression.STOP_ENDS,
    ee.schimke.composeai.rcplayer.protocol.RcTouchExpression.STOP_NOTCHES_EVEN,
    ee.schimke.composeai.rcplayer.protocol.RcTouchExpression.STOP_NOTCHES_PERCENTS,
    ee.schimke.composeai.rcplayer.protocol.RcTouchExpression.STOP_NOTCHES_ABSOLUTE,
    ee.schimke.composeai.rcplayer.protocol.RcTouchExpression.STOP_NOTCHES_SINGLE_EVEN,
  )

private fun RcLayoutNode.geometryComponentIds(): List<Int> =
  when (this) {
    is RcLayoutNode.Root ->
      listOf(componentId) + children.filterIsInstance<RcLayoutNode.Content>().map { it.componentId }
    is RcLayoutNode.Canvas -> listOfNotNull(componentId, content?.componentId)
    is RcLayoutNode.CanvasContent -> listOf(componentId)
    is RcLayoutNode.Box -> listOfNotNull(componentId, content?.componentId)
    is RcLayoutNode.Row -> listOf(componentId, content.componentId)
    is RcLayoutNode.Column -> listOf(componentId, content.componentId)
    is RcLayoutNode.Flow -> listOf(componentId, content.componentId)
    is RcLayoutNode.State -> listOf(componentId)
    is RcLayoutNode.CollapsibleRow -> listOf(componentId, content.componentId)
    is RcLayoutNode.CollapsibleColumn -> listOf(componentId, content.componentId)
    is RcLayoutNode.FitBox -> listOf(componentId, content.componentId)
    is RcLayoutNode.Content -> emptyList() // Its layout manager publishes the wrapper geometry.
    is RcLayoutNode.Image -> listOfNotNull(componentId, contentComponentId)
    is RcLayoutNode.Text -> listOfNotNull(componentId, contentComponentId)
    is RcLayoutNode.CoreText -> listOfNotNull(componentId, contentComponentId)
    is RcLayoutNode.Custom -> listOf(componentId)
  }

private fun Modifier.trackComponentGeometry(
  componentIds: List<Int>,
  state: RcPlayerState,
  probe: RcGeometryProbe?,
): Modifier {
  val tracked = componentIds.distinct().filter(state::hasComponentValues)
  if (tracked.isEmpty()) return this
  // A settling pass is measured and thrown away, never placed, so it publishes the one thing a
  // measure pass knows. The size is the node's own from this point of the chain inward — the same
  // size `coordinates.size` reports below — so the pass the player keeps measures against it.
  if (probe != null) {
    return layout { measurable, constraints ->
      val placeable = measurable.measure(constraints)
      tracked.forEach { id ->
        probe.onPublished(
          state.publishComponentSize(id, placeable.width.toFloat(), placeable.height.toFloat())
        )
      }
      layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
  }
  return onGloballyPositioned { coordinates ->
    val local = coordinates.positionInParent()
    val root = coordinates.positionInRoot()
    val geometry =
      RcComponentGeometry(
        width = coordinates.size.width.toFloat(),
        height = coordinates.size.height.toFloat(),
        localX = local.x,
        localY = local.y,
        rootX = root.x,
        rootY = root.y,
      )
    tracked.forEach { state.publishComponentGeometry(it, geometry) }
  }
}

/**
 * Publishes this component's identity onto the semantics tree, when inspection is on.
 *
 * The sibling of [trackComponentGeometry], and deliberately separate from it. That one feeds the
 * *document* — it publishes only the components a `ComponentValue` binds, because that is all a
 * document can read — and widening it to every node would make every document pay for an
 * observation only a harness wants.
 *
 * No geometry is written here, and that is the point: a semantics node already exposes
 * `positionInRoot`, `size` and `boundsInRoot`, read from the layout node on demand. Pushing
 * geometry instead would mean an `onGloballyPositioned` per component, firing after every layout
 * pass of the whole tree, to deliver numbers the reader could have pulled.
 *
 * Returns the receiver untouched when inspection is off, so no semantics modifier is added and the
 * chain is byte-for-byte what it was before this existed.
 */

/** Drops padding, for the layouts whose reference sizes to its content rather than to a chain. */
internal fun RcLayoutModifiers.withoutPadding(): RcLayoutModifiers {
  if (padding.isEmpty()) return this
  return removingOrdered { it is RcPaddingModifier }.copy(padding = emptyList())
}

/**
 * This component's modifiers with every width and height dropped, so it wraps its content.
 *
 * Only `StateLayout` uses this, and only because AndroidX's own state container does: its `measure`
 * is overridden to measure the branch it is showing and take that branch's size, and never applies
 * its own dimension modifiers — a fill *or* an exact size. Dropping only fills left
 * `interaction_click_button`'s `height(100)` state container at 100 where AndroidX reports the
 * shown branch's 40. Dropping the modifier from both `ordered` and the resolved `width`/`height`
 * matters — `applyComponentModifiers` walks the ordered list to preserve AndroidX's order-sensitive
 * modifier semantics and consults the resolved fields separately, so removing it from one and not
 * the other would apply half of it.
 */
internal fun RcLayoutModifiers.withoutDimensions(): RcLayoutModifiers {
  if (width == null && height == null) return this
  return removingOrdered { it is RcWidthModifier || it is RcHeightModifier }
    .copy(width = null, height = null)
}

/**
 * Drops the operations [predicate] selects from `ordered`, moving
 * [RcLayoutModifiers.scrollPosition] with them.
 *
 * `scrollPosition` counts the operations that precede the scroll in wire order, so it is an index
 * into `ordered` — filtering an operation out from in front of it leaves the index pointing at the
 * wrong place. `padding -> scroll -> offset` became `[offset]` with the position still at 1, and
 * `applyComponentModifiers` then never matched it and appended scrolling *after* the offset,
 * reversing the two.
 */
internal fun RcLayoutModifiers.removingOrdered(
  predicate: (RcOperation) -> Boolean
): RcLayoutModifiers {
  val position = scrollPosition
  return copy(
    ordered = ordered.filterNot(predicate),
    scrollPosition = position?.minus(ordered.take(position).count(predicate)),
  )
}

/**
 * Greedy word wrap on character counts — the closed-form Ahem model (`CONFORMANCE_FORMAT.md` §2.3).
 */
/** The latest text layout of one text component, written during its own measure pass. */
private class RcTextLines {
  var result: TextLayoutResult? = null
}

/**
 * Sizes a text component to its lines, as AndroidX's `CoreText` does, rather than to the width it
 * was allowed.
 *
 * Compose lays a paragraph out across the whole available width and reports that width as soon as
 * the text wraps, so a wrapped `CoreText` measured 200 px where AndroidX reports its widest line,
 * 144 — which moves anything aligned against it and widens its background. This keeps the paragraph
 * as laid out and narrows the node to the span its lines cover, shifting it so that centred and
 * end-aligned lines keep their positions within that span. It never goes below the incoming
 * minimum, so an explicit width still wins.
 *
 * Truncated text keeps the full width — it was cut because it did not fit — unless [truncatedWidth]
 * says how a truncated run is sized.
 */
private fun Modifier.fitToLines(
  lines: RcTextLines,
  truncatedBlock: ((maxWidth: Float, fontSize: Float) -> Size)? = null,
): Modifier = layout { measurable, constraints ->
  val placeable = measurable.measure(constraints)
  val result = lines.result
  if (result != null && result.hasVisualOverflow && truncatedBlock != null) {
    // The size the paragraph was laid out at, which autosize chose.
    val laidOutFontSize = with(this) { result.layoutInput.style.fontSize.toPx() }
    val block = truncatedBlock(placeable.width.toFloat(), laidOutFontSize)
    val width =
      constraints
        .constrainWidth(ceil(block.width - LINE_EXTENT_EPSILON).toInt())
        .coerceAtMost(placeable.width)
    val height =
      constraints
        .constrainHeight(ceil(block.height - LINE_EXTENT_EPSILON).toInt())
        .coerceAtMost(placeable.height)
    return@layout layout(width, height) { placeable.place(0, 0) }
  }
  if (result == null || result.lineCount == 0 || result.hasVisualOverflow) {
    return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
  }
  var left = Float.MAX_VALUE
  var right = 0f
  for (line in 0 until result.lineCount) {
    left = minOf(left, result.getLineLeft(line))
    right = maxOf(right, result.getLineRight(line))
  }
  // A line's extent is a float sum of advances, so a run of whole-pixel glyphs can land a hair
  // above an integer; rounding that hair up reported 229 for a 228 px line.
  val span = ceil(right - left - LINE_EXTENT_EPSILON).toInt()
  val width = constraints.constrainWidth(span).coerceAtMost(placeable.width)
  val shift = if (width < placeable.width) -left.roundToInt() else 0
  layout(width, placeable.height) { placeable.place(shift, 0) }
}

private const val LINE_EXTENT_EPSILON = 0.01f

internal fun rcAhemWrap(text: String, availableWidthPx: Float, fontSizePx: Float): List<String> {
  if (fontSizePx <= 0f || availableWidthPx <= 0f) return listOf(text)
  val perLine = floor(availableWidthPx / fontSizePx).toInt().coerceAtLeast(1)
  val lines = mutableListOf<String>()
  text.split('\n').forEach { paragraph ->
    if (paragraph.isEmpty()) {
      lines += ""
      return@forEach
    }
    var line = ""
    paragraph.split(' ').forEach { word ->
      if (line.isNotEmpty() && line.length + 1 + word.length <= perLine) {
        line = "$line $word"
        return@forEach
      }
      if (line.isNotEmpty()) lines += line
      // A word longer than a line is cut into line-sized pieces; the last piece, if it is short,
      // starts the next line rather than taking one of its own.
      val pieces = word.chunked(perLine).ifEmpty { listOf("") }
      lines += pieces.dropLast(1)
      line = pieces.last()
      if (line.length == perLine && pieces.size > 1) {
        lines += line
        line = ""
      }
    }
    if (line.isNotEmpty()) lines += line
  }
  return lines
}

/**
 * The block a truncated run fills under the closed-form Ahem model (`CONFORMANCE_FORMAT.md` §2.3):
 * greedy word wrap, the first [maxLines] lines kept and, for an [ellipsis], `"..."` appended to the
 * last kept line — cutting it back to three short of a full line when the dots would not fit. As
 * wide as the widest kept line and as tall as the kept lines, one font size a character and a line.
 */
internal fun rcAhemTruncatedBlock(
  text: String,
  availableWidthPx: Float,
  fontSizePx: Float,
  maxLines: Int,
  ellipsis: Boolean = true,
): Size {
  val perLine = floor(availableWidthPx / fontSizePx).toInt().coerceAtLeast(1)
  val wrapped = rcAhemWrap(text, availableWidthPx, fontSizePx)
  val kept = wrapped.take(maxLines.coerceAtLeast(1)).toMutableList()
  if (ellipsis && wrapped.size > kept.size && kept.isNotEmpty()) {
    val last = kept.last()
    kept[kept.lastIndex] =
      if (last.length + ELLIPSIS.length <= perLine) last + ELLIPSIS
      else last.take((perLine - ELLIPSIS.length).coerceAtLeast(0)) + ELLIPSIS
  }
  return Size((kept.maxOfOrNull { it.length } ?: 0) * fontSizePx, kept.size * fontSizePx)
}

private const val ELLIPSIS = "..."

/**
 * Autosize under the closed-form Ahem model, resolved where Compose resolves it.
 *
 * `TextAutoSize.getFontSize` is handed the incoming [Constraints] — the one place the available
 * space is visible at the moment the size is chosen. Earlier attempts at this tried to obtain that
 * space by wrapping `CoreText` in a `BoxWithConstraints` or a `SubcomposeLayout`; both reported the
 * host's size rather than the text run's. No host is needed.
 *
 * The search is the reference harness's own (`CoreText.computeWrapSize` over its Ahem text layout):
 * bisect `[minFontSize, maxFontSize]` on whether the block is *strictly* shorter than the box, snap
 * down to the half-point grid, then take one half-step more if that still fits. The block is the
 * first `maxLines` lines of [rcAhemWrap] — the declared cap, whatever the overflow — and width
 * never enters it: a word too long for a line is cut into more lines, which is what makes a narrow
 * box pick a smaller size.
 *
 * Every line is one em tall (`0.8em` ascent + `0.2em` descent), so a block is `lines × size`.
 */
private class RcAhemAutoSize(
  private val minPx: Float,
  private val maxPx: Float,
  private val maxLines: Int,
  private val stepPx: Float = 0.5f,
) : TextAutoSize {
  override fun TextAutoSizeLayoutScope.getFontSize(
    constraints: Constraints,
    text: AnnotatedString,
  ): TextUnit {
    val width = constraints.maxWidth.toFloat()
    val height = constraints.maxHeight.toFloat()
    fun fits(size: Float): Boolean =
      rcAhemWrap(text.text, width, size).take(maxLines).size * size < height
    // A bisection on height alone, then one half-step up if that still fits — the reference's
    // `CoreText.computeWrapSize`. Width never enters it: a word too long for a line is cut into
    // more lines, which is what makes a narrow box choose a smaller size.
    var low = minPx
    var high = maxPx
    while (high - low >= stepPx) {
      val current = (low + high) / 2f
      if (fits(current)) low = current else high = current
    }
    var size = floor((low - minPx) / stepPx) * stepPx + minPx
    if (size + stepPx < maxPx && fits(size + stepPx)) size += stepPx
    return with(this) { size.coerceAtLeast(minPx).toSp() }
  }

  override fun equals(other: Any?): Boolean =
    other is RcAhemAutoSize &&
      minPx == other.minPx &&
      maxPx == other.maxPx &&
      maxLines == other.maxLines &&
      stepPx == other.stepPx

  override fun hashCode(): Int =
    ((minPx.hashCode() * 31 + maxPx.hashCode()) * 31 + maxLines) * 31 + stepPx.hashCode()
}

private fun Modifier.inspectComponent(
  node: RcLayoutNode,
  visibility: Int,
  inspecting: Boolean,
  contentInset: Offset,
): Modifier {
  if (!inspecting) return this
  val id = node.componentId ?: return this
  val kind = node.androidXComponentKind() ?: return this
  return semantics {
    rcComponentId = id
    rcComponentKind = kind
    rcComponentVisibility = visibility
    if (contentInset != Offset.Zero) rcContentInset = contentInset
  }
}

/**
 * The top-left inset this component's padding modifiers impose on its children, in pixels.
 *
 * Summed over every `RcPaddingModifier` in the chain, matching how `applyComponentModifiers`
 * applies them — AndroidX writes consecutive padding operations and the player accumulates rather
 * than replaces. Computed here, before the chain is built, because the component's semantics node
 * sits *outside* the padding: a value published from inside it would land on a different layout
 * node and never reach the same semantics configuration.
 */
@Composable
private fun rcContentInsetPixels(
  modifiers: RcLayoutModifiers,
  state: RcPlayerState,
  density: Density,
): Offset {
  var left = 0f
  var top = 0f
  modifiers.ordered.forEach { operation ->
    if (operation is RcPaddingModifier) {
      left += state.dpTypedPixels(state.resolve(operation.left), density)
      top += state.dpTypedPixels(state.resolve(operation.top), density)
    }
  }
  return if (left == 0f && top == 0f) Offset.Zero else Offset(left, top)
}

/**
 * Publishes the document's non-visual state — slots, named variables, particles — onto the player's
 * outermost node.
 *
 * It rides the same semantics channel as the tree so a reader needs one traversal and no second
 * API, and it sits at the player's root rather than the *document's* root component because the two
 * are not the same thing: a canvas-only document has no layout components whatsoever.
 */
private fun Modifier.inspectDocument(state: RcPlayerState, inspecting: Boolean): Modifier =
  if (!inspecting) this else semantics { rcDocumentState = state }

/**
 * This node's class in AndroidX's vocabulary, or null for a node AndroidX's tree does not name.
 *
 * The portable name rather than the Kotlin one, because the wire format is AndroidX's: a consumer
 * comparing two players should not have to know that this one spells its box `RcLayoutNode.Box`.
 * `Content` and `CanvasContent` return null — they are wrappers with no entry of their own.
 */
private fun RcLayoutNode.androidXComponentKind(): String? =
  when (this) {
    is RcLayoutNode.Root -> "RootLayoutComponent"
    is RcLayoutNode.Box -> "BoxLayout"
    is RcLayoutNode.Row -> "RowLayout"
    is RcLayoutNode.Column -> "ColumnLayout"
    is RcLayoutNode.Flow -> "FlowLayout"
    is RcLayoutNode.State -> "StateLayout"
    is RcLayoutNode.FitBox -> "FitBoxLayout"
    is RcLayoutNode.CollapsibleRow -> "CollapsibleRowLayout"
    is RcLayoutNode.CollapsibleColumn -> "CollapsibleColumnLayout"
    is RcLayoutNode.Image -> "ImageLayout"
    is RcLayoutNode.Canvas -> "CanvasLayout"
    is RcLayoutNode.CoreText -> "CoreText"
    is RcLayoutNode.Text -> "TextLayout"
    is RcLayoutNode.Custom -> "CustomLayout"
    is RcLayoutNode.Content,
    is RcLayoutNode.CanvasContent -> null
  }

private fun Modifier.applyAccessibilitySemantics(
  operation: RcAccessibilitySemantics,
  state: RcPlayerState,
  hasClickAction: Boolean,
): Modifier {
  val properties: SemanticsPropertyReceiver.() -> Unit = {
    operation.contentDescriptionId
      .takeUnless { it == 0 }
      ?.let { id -> state.text(id)?.let { contentDescription = it } }
    operation.textId
      .takeUnless { it == 0 }
      ?.let { id -> state.text(id)?.let { text = AnnotatedString(it) } }
    operation.stateDescriptionId
      .takeUnless { it == 0 }
      ?.let { id -> state.text(id)?.let { stateDescription = it } }
    androidXSemanticsRole(operation.role)?.let { role = it }
    if (!operation.enabled) disabled()
    if (operation.clickable && !hasClickAction) {
      onClick { false }
      rcAccessibilityOnlyClick = true
    }
  }
  return when (operation.mode) {
    RcAccessibilitySemantics.MODE_SET -> semantics(properties = properties)
    RcAccessibilitySemantics.MODE_CLEAR_AND_SET -> clearAndSetSemantics(properties)
    RcAccessibilitySemantics.MODE_MERGE ->
      semantics(mergeDescendants = true, properties = properties)
    else -> this
  }
}

internal fun androidXSemanticsRole(role: Int): Role? =
  when (role) {
    RcAccessibilitySemantics.ROLE_BUTTON -> Role.Button
    RcAccessibilitySemantics.ROLE_CHECKBOX -> Role.Checkbox
    RcAccessibilitySemantics.ROLE_SWITCH -> Role.Switch
    RcAccessibilitySemantics.ROLE_RADIO_BUTTON -> Role.RadioButton
    RcAccessibilitySemantics.ROLE_TAB -> Role.Tab
    RcAccessibilitySemantics.ROLE_IMAGE -> Role.Image
    RcAccessibilitySemantics.ROLE_DROPDOWN_LIST -> Role.DropdownList
    RcAccessibilitySemantics.ROLE_PICKER -> Role.ValuePicker
    RcAccessibilitySemantics.ROLE_CAROUSEL -> Role.Carousel
    else -> null
  }

private fun Modifier.applyLayoutComputes(
  modifiers: RcLayoutModifiers,
  state: RcPlayerState,
): Modifier = layout { measurable, constraints ->
  val placeable = measurable.measure(constraints)
  val parentWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
  val parentHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
  var width = placeable.width
  var height = placeable.height
  var x = 0
  var y = 0
  modifiers.layoutComputes.forEach { block ->
    val values =
      state.evaluateLayoutCompute(
        block,
        floatArrayOf(
          x.toFloat(),
          y.toFloat(),
          width.toFloat(),
          height.toFloat(),
          parentWidth.toFloat(),
          parentHeight.toFloat(),
        ),
      )
    when (block.operation.type) {
      RcLayoutCompute.MEASURE -> {
        width = constraints.constrainWidth(values[2].roundToInt().coerceAtLeast(0))
        height = constraints.constrainHeight(values[3].roundToInt().coerceAtLeast(0))
      }
      RcLayoutCompute.POSITION -> {
        x = values[0].roundToInt()
        y = values[1].roundToInt()
      }
    }
  }
  layout(width, height) { placeable.placeRelative(x, y) }
}

/**
 * AndroidX wraps every graphics-layer float in an `AnimatableValue`, so a variable this modifier
 * reads eases to its new value instead of jumping there. Here each such float is a Compose
 * [Animatable] (see [rcLayerFloat]) — held per component, because two components can share one
 * identical modifier operation and still be mid-tween at different points.
 *
 * The targets are resolved in composition, which a host write or a document action already
 * recomposes; the eased values are read inside the [graphicsLayer] block, so a running tween only
 * updates the layer's properties each frame instead of recomposing the component or holding the
 * player's document frame loop open. Compose's own frame clock drives it, as it drives the player's
 * other layout transitions.
 */
@Composable
private fun Modifier.applyGraphicsLayer(
  operation: RcGraphicsLayerModifier,
  state: RcPlayerState,
): Modifier {
  val attributes = remember(operation) { operation.attributes.associateBy { it.index } }
  fun float(index: Int) = attributes[index]
  fun int(index: Int): Int? = (attributes[index] as? RcGraphicsLayerAttribute.IntValue)?.value

  val scaleX = rcLayerFloat(float(RcGraphicsLayerModifier.SCALE_X), 1f, state)
  val scaleY = rcLayerFloat(float(RcGraphicsLayerModifier.SCALE_Y), 1f, state)
  val rotationX = rcLayerFloat(float(RcGraphicsLayerModifier.ROTATION_X), 0f, state)
  val rotationY = rcLayerFloat(float(RcGraphicsLayerModifier.ROTATION_Y), 0f, state)
  val rotationZ = rcLayerFloat(float(RcGraphicsLayerModifier.ROTATION_Z), 0f, state)
  // `GraphicsLayerModifierOperation`'s own default, so an absent origin pivots at the top-left as
  // it does in AndroidX's embedded Compose player; the current writer writes a centre explicitly.
  val transformOriginX = rcLayerFloat(float(RcGraphicsLayerModifier.TRANSFORM_ORIGIN_X), 0f, state)
  val transformOriginY = rcLayerFloat(float(RcGraphicsLayerModifier.TRANSFORM_ORIGIN_Y), 0f, state)
  val translationX = rcLayerFloat(float(RcGraphicsLayerModifier.TRANSLATION_X), 0f, state)
  val translationY = rcLayerFloat(float(RcGraphicsLayerModifier.TRANSLATION_Y), 0f, state)
  val translationZ = rcLayerFloat(float(RcGraphicsLayerModifier.TRANSLATION_Z), 0f, state)
  val shadowElevation = rcLayerFloat(float(RcGraphicsLayerModifier.SHADOW_ELEVATION), 0f, state)
  val alpha = rcLayerFloat(float(RcGraphicsLayerModifier.ALPHA), 1f, state)
  val cameraDistance = rcLayerFloat(float(RcGraphicsLayerModifier.CAMERA_DISTANCE), 8f, state)
  val shapeRadius = rcLayerFloat(float(RcGraphicsLayerModifier.SHAPE_RADIUS), 0f, state)
  val blurX = rcLayerFloat(float(RcGraphicsLayerModifier.BLUR_RADIUS_X), 0f, state)
  val blurY = rcLayerFloat(float(RcGraphicsLayerModifier.BLUR_RADIUS_Y), 0f, state)
  // `COMPOSITING_STRATEGY` is accepted and ignored, as both AndroidX players ignore it (a
  // RenderNode layer composites like Compose's `Auto`).
  val shapeType = int(RcGraphicsLayerModifier.SHAPE)
  val blurTileMode =
    when (int(RcGraphicsLayerModifier.BLUR_TILE_MODE)) {
      RcGraphicsLayerModifier.TILE_MODE_REPEATED -> TileMode.Repeated
      RcGraphicsLayerModifier.TILE_MODE_MIRROR -> TileMode.Mirror
      RcGraphicsLayerModifier.TILE_MODE_DECAL -> TileMode.Decal
      else -> TileMode.Clamp
    }
  val ambientShadow = int(RcGraphicsLayerModifier.AMBIENT_SHADOW_COLOR)?.let(::Color)
  val spotShadow = int(RcGraphicsLayerModifier.SPOT_SHADOW_COLOR)?.let(::Color)
  return graphicsLayer {
    this.scaleX = scaleX()
    this.scaleY = scaleY()
    this.rotationX = rotationX()
    this.rotationY = rotationY()
    this.rotationZ = rotationZ()
    transformOrigin = TransformOrigin(transformOriginX(), transformOriginY())
    this.translationX = translationX()
    this.translationY = translationY()
    // A RenderNode's shadow sits at elevation + translationZ, which is what AndroidX's paint
    // context sets; Compose has no translationZ, so the sum goes into shadowElevation.
    this.shadowElevation = shadowElevation() + translationZ()
    this.alpha = alpha()
    this.cameraDistance = cameraDistance()
    // The layer's outline. Compose uses it for the shadow; the layer is not clipped to it, since
    // the document has no clip attribute and neither AndroidX player clips there.
    shape =
      when (shapeType) {
        RcGraphicsLayerModifier.SHAPE_ROUND_RECT -> RoundedCornerShape(CornerSize(shapeRadius()))
        RcGraphicsLayerModifier.SHAPE_CIRCLE -> CircleShape
        else -> RectangleShape
      }
    ambientShadow?.let { ambientShadowColor = it }
    spotShadow?.let { spotShadowColor = it }
    val radiusX = blurX()
    val radiusY = blurY()
    renderEffect =
      if (radiusX > 0f || radiusY > 0f) BlurEffect(radiusX, radiusY, blurTileMode) else null
  }
}

/**
 * One graphics-layer float, as a reader for the [graphicsLayer] block.
 *
 * A variable the document or a host moves eases to its new value over AndroidX `AnimatableValue`'s
 * 300ms standard curve — [DefaultRcAnimationSpec]'s motion, sampled through [rcMotionEasing] — and
 * a change mid-tween eases on from wherever the value is. Two rules come with it, and they matter
 * more than the curve does:
 * * **No animation on first appearance.** The [Animatable] starts at the first value the variable
 *   takes, the document's opening pose.
 * * **A source the document keeps moving is followed, not chased.** A literal cannot change, and a
 *   clock-driven or dragged value (see [RcPlayerState.isContinuouslyDriven]) moves every frame;
 *   easing towards each new target in turn would draw it a third of a second behind itself, so both
 *   are read straight through.
 */
@Composable
private fun rcLayerFloat(
  attribute: RcGraphicsLayerAttribute?,
  default: Float,
  state: RcPlayerState,
): () -> Float {
  val word = (attribute as? RcGraphicsLayerAttribute.FloatValue)?.value ?: return { default }
  val target = state.resolve(word)
  val referencedId = word.referencedId
  if (referencedId == null || state.isContinuouslyDriven(referencedId)) return { target }
  val animatable = remember(state) { Animatable(target) }
  LaunchedEffect(animatable, target) {
    when {
      // A value with nothing to ease from lands, as the first one does.
      !target.isFinite() || !animatable.value.isFinite() -> animatable.snapTo(target)
      animatable.targetValue != target -> animatable.animateTo(target, RcLayerTween)
    }
  }
  return remember(animatable) { { animatable.value } }
}

/** AndroidX `AnimatableValue`'s tween: 300ms along `GeneralEasing.CUBIC_STANDARD`. */
private val RcLayerTween: FiniteAnimationSpec<Float> =
  tween(
    durationMillis = DefaultRcAnimationSpec.rcMotionDurationMillis(),
    easing = DefaultRcAnimationSpec.rcMotionEasing(),
  )

private fun Modifier.applyDimensionConstraint(
  operation: ee.schimke.composeai.rcplayer.protocol.RcOperation,
  state: RcPlayerState,
  density: Density,
): Modifier =
  when (operation) {
    is RcWidthInModifier ->
      applyWidthRange(
        state.dimensionConstraintDp(state.resolve(operation.minimum), density),
        state.dimensionConstraintDp(state.resolve(operation.maximum), density),
      )
    is RcHeightInModifier ->
      applyHeightRange(
        state.dimensionConstraintDp(state.resolve(operation.minimum), density),
        state.dimensionConstraintDp(state.resolve(operation.maximum), density),
      )
    is RcDimensionConstraintsModifier ->
      when (operation.type) {
        RcDimensionConstraintsModifier.HORIZONTAL ->
          applyWidthRange(
            state.dimensionConstraintDp(state.resolve(operation.minimum), density),
            state.dimensionConstraintDp(state.resolve(operation.maximum), density),
          )
        RcDimensionConstraintsModifier.VERTICAL ->
          applyHeightRange(
            state.dimensionConstraintDp(state.resolve(operation.minimum), density),
            state.dimensionConstraintDp(state.resolve(operation.maximum), density),
          )
        RcDimensionConstraintsModifier.REQUIRED_HORIZONTAL ->
          applyWidthRange(
            state.dimensionConstraintDp(state.resolve(operation.minimum), density),
            state.dimensionConstraintDp(state.resolve(operation.maximum), density),
            required = true,
          )
        RcDimensionConstraintsModifier.REQUIRED_VERTICAL ->
          applyHeightRange(
            state.dimensionConstraintDp(state.resolve(operation.minimum), density),
            state.dimensionConstraintDp(state.resolve(operation.maximum), density),
            required = true,
          )
        else -> this
      }
    else -> this
  }

private fun RcPlayerState.dimensionConstraintDp(value: Float, density: Density): Float =
  if (value == -1f) value
  else rcDimensionConstraintDp(value, density.density, document.header.densityBehavior)

private fun Modifier.applyWidthRange(
  minimum: Float,
  maximum: Float,
  required: Boolean = false,
): Modifier {
  val min = minimum.dp
  val max = maximum.dp
  return when {
    minimum == -1f && maximum == -1f -> this
    required && minimum == -1f -> requiredWidthIn(max = max)
    required && maximum == -1f -> requiredWidthIn(min = min)
    required -> requiredWidthIn(min = min, max = max)
    minimum == -1f -> widthIn(max = max)
    maximum == -1f -> widthIn(min = min)
    else -> widthIn(min = min, max = max)
  }
}

private fun Modifier.applyHeightRange(
  minimum: Float,
  maximum: Float,
  required: Boolean = false,
): Modifier {
  val min = minimum.dp
  val max = maximum.dp
  return when {
    minimum == -1f && maximum == -1f -> this
    required && minimum == -1f -> requiredHeightIn(max = max)
    required && maximum == -1f -> requiredHeightIn(min = min)
    required -> requiredHeightIn(min = min, max = max)
    minimum == -1f -> heightIn(max = max)
    maximum == -1f -> heightIn(min = min)
    else -> heightIn(min = min, max = max)
  }
}

@Composable
private fun Modifier.applyPaintDecorator(
  operation: ee.schimke.composeai.rcplayer.protocol.RcOperation,
  state: RcPlayerState,
): Modifier {
  val localDensity = androidx.compose.ui.platform.LocalDensity.current
  return when (operation) {
    is RcBackgroundModifier ->
      drawBehind {
        val color =
          if (operation.usesColorId) {
            Color(state.color(operation.colorId))
          } else {
            Color(
              red = state.resolve(operation.red),
              green = state.resolve(operation.green),
              blue = state.resolve(operation.blue),
              alpha = state.resolve(operation.alpha),
            )
          }
        when (operation.shapeType) {
          RcBackgroundModifier.SHAPE_RECTANGLE -> drawRect(color)
          RcBackgroundModifier.SHAPE_CIRCLE ->
            drawCircle(color, radius = minOf(size.width, size.height) / 2f)
        }
      }
    is RcBorderModifier ->
      drawBehind {
        val color =
          if (operation.usesColorId) {
            Color(state.color(operation.colorId))
          } else {
            Color(
              red = state.resolve(operation.red),
              green = state.resolve(operation.green),
              blue = state.resolve(operation.blue),
              alpha = state.resolve(operation.alpha),
            )
          }
        val borderWidth =
          state.borderWidthPixels(operation.borderWidth, localDensity).coerceAtLeast(0f)
        val corner =
          state
            .dpTypedPixels(state.resolve(operation.roundedCorner), localDensity)
            .coerceAtLeast(0f)
        val halfSize = minOf(size.width, size.height) / 2f
        if (operation.wireVersion != 0 && borderWidth >= halfSize) {
          when (operation.shapeType) {
            RcBackgroundModifier.SHAPE_RECTANGLE -> drawRect(color)
            RcBackgroundModifier.SHAPE_CIRCLE -> drawCircle(color, radius = halfSize)
            else -> drawRoundRect(color, cornerRadius = CornerRadius(corner))
          }
        } else {
          val inset = if (operation.wireVersion == 0) 0f else borderWidth / 2f
          val stroke = Stroke(width = borderWidth)
          when (operation.shapeType) {
            RcBackgroundModifier.SHAPE_RECTANGLE ->
              drawRect(
                color,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                style = stroke,
              )
            RcBackgroundModifier.SHAPE_CIRCLE ->
              drawCircle(color, radius = (halfSize - inset).coerceAtLeast(0f), style = stroke)
            else ->
              drawRoundRect(
                color,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
                style = stroke,
              )
          }
        }
      }
    // Component clips are Compose's own layer clip — what `Modifier.clip` expands to — so the
    // content (and hit testing) is clipped by the layer rather than by a hand-rolled draw pass.
    // The lambda form of `graphicsLayer` keeps the radius reads in the layer phase: an animated
    // corner re-clips without recomposing.
    RcClipRectModifier -> clipToBounds()
    is RcRoundedClipRectModifier ->
      graphicsLayer {
        shape =
          RcCornerClipShape(
            topLeft = state.dpTypedPixels(state.resolve(operation.topStart), localDensity),
            topRight = state.dpTypedPixels(state.resolve(operation.topEnd), localDensity),
            bottomRight = state.dpTypedPixels(state.resolve(operation.bottomEnd), localDensity),
            bottomLeft = state.dpTypedPixels(state.resolve(operation.bottomStart), localDensity),
          )
        clip = true
      }
    else -> this
  }
}

/**
 * A rounded-rect clip in pixels. Not `RoundedCornerShape`: that one mirrors start/end under RTL,
 * while AndroidX's own `RemoteRoundedClipShape` always maps the wire's "start" corners to the left.
 * `Outline.Rounded` scales oversized radii the same way Skia's `addRoundRect` does.
 */
private data class RcCornerClipShape(
  val topLeft: Float,
  val topRight: Float,
  val bottomRight: Float,
  val bottomLeft: Float,
) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
    Outline.Rounded(
      RoundRect(
        left = 0f,
        top = 0f,
        right = size.width,
        bottom = size.height,
        topLeftCornerRadius = CornerRadius(topLeft.coerceAtLeast(0f)),
        topRightCornerRadius = CornerRadius(topRight.coerceAtLeast(0f)),
        bottomRightCornerRadius = CornerRadius(bottomRight.coerceAtLeast(0f)),
        bottomLeftCornerRadius = CornerRadius(bottomLeft.coerceAtLeast(0f)),
      )
    )
}

private fun RcPlayerState.borderWidthPixels(word: RcFloatWord, density: Density): Float {
  val version = document.header.version
  val atLeastV7 = version.major > 1 || (version.major == 1 && version.minor >= 1)
  return if (document.header.densityBehavior == RcHeader.DENSITY_BEHAVIOR_LEGACY && !atLeastV7) {
    resolve(word) * density.density
  } else {
    dpTypedPixels(resolve(word), density)
  }
}

/**
 * AndroidX's `RippleModifier`, as a Compose [Modifier.indication] at the modifier's wire position.
 *
 * The component's clickable (or multi-click recogniser) emits its presses into [interactions]; a
 * component that asks for a ripple but has no click action of its own gets a press observer in its
 * place, because AndroidX ripples on every touch down whether or not the component is clickable.
 */
private fun Modifier.applyAndroidXRipple(
  interactions: MutableInteractionSource,
  emitOwnPresses: Boolean,
): Modifier {
  val ripple = indication(interactions, RcRippleIndication)
  if (!emitOwnPresses) return ripple
  return ripple.pointerInput(interactions) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      val press = PressInteraction.Press(down.position)
      interactions.tryEmit(press)
      val up = waitForUpOrCancellation()
      interactions.tryEmit(
        if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press)
      )
    }
  }
}

/**
 * The Java player's clipped two-phase ripple, drawn as a Compose [IndicationNodeFactory] rather
 * than Material's ripple: the colour fades from `0xb4fafafa` to transparent over a second while the
 * radius grows to the component's larger side in half that, from the press position. Material's
 * `ripple()` is not in this module's dependency set, and its look is theme-derived rather than the
 * one the document's reference draws.
 */
private data object RcRippleIndication : IndicationNodeFactory {
  override fun create(interactionSource: InteractionSource): DelegatableNode =
    RcRippleNode(interactionSource)
}

private class RcRippleNode(private val interactionSource: InteractionSource) :
  Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
  private val colorProgress = Animatable(1f)
  private val radiusProgress = Animatable(1f)
  private var origin = Offset.Zero

  override fun onAttach() {
    coroutineScope.launch {
      interactionSource.interactions.collect { interaction ->
        if (interaction is PressInteraction.Press) start(interaction.pressPosition)
      }
    }
  }

  private fun start(position: Offset) {
    origin = position
    currentValueOf(LocalHapticFeedback).performHapticFeedback(HapticFeedbackType.LongPress)
    val standard = CubicBezierEasing(.4f, 0f, .2f, 1f)
    coroutineScope.launch {
      colorProgress.snapTo(0f)
      colorProgress.animateTo(1f, tween(durationMillis = 1_000, easing = standard))
    }
    coroutineScope.launch {
      radiusProgress.snapTo(0f)
      radiusProgress.animateTo(1f, tween(durationMillis = 500, easing = standard))
    }
  }

  override fun ContentDrawScope.draw() {
    drawContent()
    if (colorProgress.value < 1f || radiusProgress.value < 1f) {
      val color = lerp(Color(0xb4fafafa.toInt()), Color(0x00c8c8c8), colorProgress.value)
      val radius = maxOf(size.width, size.height) * radiusProgress.value
      clipRect { drawCircle(color = color, radius = radius, center = origin) }
    }
  }
}

private fun Modifier.applyWidth(
  width: RcWidthModifier,
  state: RcPlayerState,
  density: Density,
): Modifier =
  when (width.type) {
    RcDimensionType.EXACT -> width(with(density) { state.resolve(width.value).toDp() })
    RcDimensionType.EXACT_DP -> width(state.resolve(width.value).dp)
    RcDimensionType.FILL,
    RcDimensionType.FILL_PARENT_MAX_WIDTH -> fillMaxWidth(state.fillFraction(width.value))
    // WRAP is the *absence* of a size modifier — Compose already sizes a component to its content,
    // which is how AndroidX's own embedded player implements it too. INTRINSIC_MIN/MAX fall here
    // as well, and those genuinely are unimplemented; `composeSupportReport` draws that line, so
    // the two cases stay distinguishable even though the modifier chain treats them alike.
    else -> this
  }

private fun RowScope.rowWeightModifier(node: RcLayoutNode, state: RcPlayerState): Modifier {
  val width = node.modifiers.width
  return if (width?.type == RcDimensionType.WEIGHT) {
    Modifier.weight(state.resolve(width.value).coerceAtLeast(Float.MIN_VALUE))
  } else {
    Modifier
  }
}

/** A row child's `alignBy` anchor, as AndroidX's RowLayout resolves it. */
private sealed interface RcRowAnchor {
  /** A text baseline, read from the measured child; a child with no text anchors at its top. */
  data class Baseline(val line: AlignmentLine) : RcRowAnchor

  /** A literal or state-resolved line, or 0 for a child without `alignBy`. */
  data class Fixed(val value: Float) : RcRowAnchor
}

/**
 * The children of a row that carries `alignBy`, and what their measure passes report.
 *
 * AndroidX's RowLayout places every child at `round(base + maxAnchor - anchor)`, where `maxAnchor`
 * spans the literal anchors and the measured baselines alike, and `base` offsets the group against
 * the tallest child. Compose's Row lines children up on integer alignment lines, so a fractional
 * anchor can only be snapped once the row's largest anchor is known — and that includes baselines,
 * which exist only after measure. Compose's Row asks for every child's `alignBy { }` value after it
 * has measured all of them, so each child records its height and baseline here while it is measured
 * and the `alignBy` blocks read the finished set.
 *
 * Kept across recompositions: a child that is not remeasured keeps its last, still current, entry.
 */
private class RcAlignedRowMeasurements(size: Int) {
  val heights = IntArray(size)
  val baselines = IntArray(size)
}

private class RcAlignedRow(
  val anchors: List<RcRowAnchor>,
  /** False for a GONE child: it is not laid out, so its recorded measurements are stale. */
  val laidOut: BooleanArray,
  val measurements: RcAlignedRowMeasurements,
) {
  private val fixedAnchors = anchors.filterIsInstance<RcRowAnchor.Fixed>().map { it.value }
  private val hasBaselines = fixedAnchors.size != anchors.size

  val tallestChild: Int
    get() = anchors.indices.maxOf { if (laidOut[it]) measurements.heights[it] else 0 }

  /**
   * The integer line for a fixed anchor: `reference - round(maxAnchor - anchor)`, so the Row's
   * `maxAnchor - anchor` is exactly the value AndroidX rounds. With baselines in the row the
   * reference is `round(maxAnchor)`, which a baseline (an integer no larger than `maxAnchor`) never
   * exceeds; without them it is the fixed anchors' rounded span, keeping every line non-negative
   * because Compose's Row never aligns to a line above 0.
   */
  fun fixedLine(anchor: Float): Int {
    val maximumFixed = fixedAnchors.max()
    val maximumBaseline =
      anchors.indices
        .filter { anchors[it] is RcRowAnchor.Baseline && laidOut[it] }
        .maxOfOrNull { measurements.baselines[it] }
    val maximum = maxOf(maximumFixed, maximumBaseline?.toFloat() ?: maximumFixed)
    val reference =
      if (hasBaselines) maximum.roundToInt() else (maximum - fixedAnchors.min()).roundToInt()
    return reference - (maximum - anchor).roundToInt()
  }
}

/** The aligned-row layout state for [children], or null when none of them carries `alignBy`. */
@Composable
private fun rememberRcAlignedRow(
  children: List<RcLayoutNode>,
  state: RcPlayerState,
): RcAlignedRow? {
  val measurements = remember(children.size) { RcAlignedRowMeasurements(children.size) }
  if (children.none { it.modifiers.alignBy != null }) return null
  val laidOut =
    BooleanArray(children.size) { index ->
      children[index].modifiers.visibility?.let {
        androidXVisibility(state.integer(it.visibilityId) ?: 0) != 0
      } != false
    }
  val anchors = children.mapIndexed { index, child ->
    val line = child.modifiers.alignBy?.line
    when (line?.referencedId) {
      // A GONE baseline child measures as nothing and so anchors at 0, as it does upstream.
      RcAlignByModifier.FIRST_BASELINE_ID ->
        if (laidOut[index]) RcRowAnchor.Baseline(FirstBaseline) else RcRowAnchor.Fixed(0f)
      RcAlignByModifier.LAST_BASELINE_ID ->
        if (laidOut[index]) RcRowAnchor.Baseline(LastBaseline) else RcRowAnchor.Fixed(0f)
      null -> RcRowAnchor.Fixed(line?.value ?: 0f)
      else -> RcRowAnchor.Fixed(state.resolve(line))
    }
  }
  return RcAlignedRow(anchors, laidOut, measurements)
}

/**
 * Records the child's height and baseline for [RcAlignedRow], and aligns it with Compose's
 * `RowScope.alignBy`, whose block the Row evaluates once every child has been measured.
 */
private fun RowScope.rcRowAnchorModifier(row: RcAlignedRow, index: Int): Modifier {
  val anchor = row.anchors[index]
  return Modifier.alignBy { measured ->
      when (anchor) {
        is RcRowAnchor.Fixed -> row.fixedLine(anchor.value)
        // Read from the child rather than the record so the Row observes the baseline and is
        // remeasured when it moves; the record holds the same value for the fixed anchors.
        is RcRowAnchor.Baseline ->
          measured[anchor.line].takeUnless { it == AlignmentLine.Unspecified } ?: 0
      }
    }
    .layout { measurable, constraints ->
      val placeable = measurable.measure(constraints)
      row.measurements.heights[index] = placeable.height
      if (anchor is RcRowAnchor.Baseline) {
        row.measurements.baselines[index] =
          placeable[anchor.line].takeUnless { it == AlignmentLine.Unspecified } ?: 0
      }
      layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/**
 * Sizes and positions an aligned row the way AndroidX's RowLayout does: its height is the tallest
 * child's, not the aligned group's extent, and the group is offset by the row's vertical
 * positioning against that height. This runs as part of the Row's own measure, after its children
 * have recorded their heights.
 */
private fun Modifier.rcAlignedRowPositioning(
  row: RcAlignedRow,
  alignment: Alignment.Vertical,
): Modifier = layout { measurable, constraints ->
  val placeable = measurable.measure(constraints.copy(minHeight = 0))
  val tallestChild = row.tallestChild
  val height = constraints.constrainHeight(tallestChild)
  layout(placeable.width, height) { placeable.place(0, alignment.align(tallestChild, height)) }
}

private fun ColumnScope.columnWeightModifier(node: RcLayoutNode, state: RcPlayerState): Modifier {
  val height = node.modifiers.height
  return if (height?.type == RcDimensionType.WEIGHT) {
    Modifier.weight(state.resolve(height.value).coerceAtLeast(Float.MIN_VALUE))
  } else {
    Modifier
  }
}

private fun Modifier.applyHeight(
  height: RcHeightModifier,
  state: RcPlayerState,
  density: Density,
): Modifier =
  when (height.type) {
    RcDimensionType.EXACT -> height(with(density) { state.resolve(height.value).toDp() })
    RcDimensionType.EXACT_DP -> height(state.resolve(height.value).dp)
    RcDimensionType.FILL,
    RcDimensionType.FILL_PARENT_MAX_HEIGHT -> fillMaxHeight(state.fillFraction(height.value))
    // See `applyWidth`: WRAP is Compose's default sizing, INTRINSIC_MIN/MAX are the unimplemented
    // pair the support report names.
    else -> this
  }

/**
 * The fraction a fill modifier fills, or all of it when the document gives none.
 *
 * "None" is a NaN that references nothing — which includes the plain quiet NaN `0x7fc00000`. Its
 * NaN payload is 0, and AndroidX's `Utils.isVariable` rejects id 0 as a reference, so it is not a
 * read of slot 0: resolving it that way read 0 and laid a `fillMaxSize()` box out at 0 x 0, which
 * left its click target untappable.
 */
private fun RcPlayerState.fillFraction(value: RcFloatWord): Float =
  if (value.value.isNaN() && (value.referencedId ?: 0) == 0) 1f else resolve(value)

internal data class RcRootTransform(
  val scaleX: Float,
  val scaleY: Float,
  val translateX: Float,
  val translateY: Float,
)

/** AndroidX CoreDocument.computeScale/computeTranslate semantics for root canvas documents. */
internal fun computeRootTransform(
  documentWidth: Float,
  documentHeight: Float,
  viewportWidth: Float,
  viewportHeight: Float,
  behavior: RcRootContentBehavior?,
): RcRootTransform {
  if (behavior?.sizing != RcRootContentBehavior.SIZING_SCALE) {
    return RcRootTransform(1f, 1f, 0f, 0f)
  }
  val widthRatio = viewportWidth / documentWidth.coerceAtLeast(1f)
  val heightRatio = viewportHeight / documentHeight.coerceAtLeast(1f)
  val scale =
    when (behavior.mode) {
      RcRootContentBehavior.SCALE_INSIDE -> minOf(1f, widthRatio, heightRatio)
      RcRootContentBehavior.SCALE_FIT -> minOf(widthRatio, heightRatio)
      RcRootContentBehavior.SCALE_FILL_WIDTH -> widthRatio
      RcRootContentBehavior.SCALE_FILL_HEIGHT -> heightRatio
      RcRootContentBehavior.SCALE_CROP -> maxOf(widthRatio, heightRatio)
      else -> 1f
    }
  val scaleX = if (behavior.mode == RcRootContentBehavior.SCALE_FILL_BOUNDS) widthRatio else scale
  val scaleY = if (behavior.mode == RcRootContentBehavior.SCALE_FILL_BOUNDS) heightRatio else scale
  val contentWidth = documentWidth * scaleX
  val contentHeight = documentHeight * scaleY
  val translateX =
    when (behavior.alignment and 0xf0) {
      RcRootContentBehavior.ALIGNMENT_HORIZONTAL_CENTER -> (viewportWidth - contentWidth) / 2f
      RcRootContentBehavior.ALIGNMENT_END -> viewportWidth - contentWidth
      else -> 0f
    }
  val translateY =
    when (behavior.alignment and 0x0f) {
      RcRootContentBehavior.ALIGNMENT_VERTICAL_CENTER -> (viewportHeight - contentHeight) / 2f
      RcRootContentBehavior.ALIGNMENT_BOTTOM -> viewportHeight - contentHeight
      else -> 0f
    }
  return RcRootTransform(scaleX, scaleY, translateX, translateY)
}

/**
 * The canvas paint, as `PaintData` operations leave it.
 *
 * [typefaces] is the host's loader, so canvas text resolves its families the way `CoreText` does —
 * a host that supplies a default face gets it on a `DrawText` as well as on a text component.
 */
private class RcPaintState(
  val typefaces: RcTypefaceLoader = RcTypefaceLoader.Empty,
  /** Told about each text run as it is drawn; null — the default — records nothing. */
  val observer: RcDrawObserver? = null,
) {
  var color: Int = 0xff000000.toInt()
  var strokeWidth: Float = 1f
  var stroke: Boolean = false
  var strokeCap: StrokeCap = StrokeCap.Butt
  var strokeJoin: StrokeJoin = StrokeJoin.Miter
  var alpha: Float = 1f
  var blendMode: BlendMode = BlendMode.SrcOver
  var blendModeValue: Int = 3
  var brush: Brush? = null
  var baseShader: Shader? = null
  var runtimeShaderOwner: Any? = null
  var colorFilter: ColorFilter? = null
  /** How bitmaps are sampled when scaled — `FILTER_BITMAP` / `IMAGE_FILTER_QUALITY`. */
  var filterQuality: FilterQuality = FilterQuality.Low
  var textSize: Float = 16f
  /** Until a `PaintData` names a font type, the host's default face — as `CoreText` gets it. */
  var fontFamily: FontFamily = rcResolveTypeface(null, -1, emptyMap(), typefaces)
  var fontWeight: FontWeight = FontWeight.Normal
  var fontStyle: FontStyle = FontStyle.Normal
  var fontType: Int = 0

  fun composeColor(): Color {
    val color = Color(color)
    return color.copy(alpha = color.alpha * alpha)
  }

  fun style() =
    if (stroke) Stroke(width = strokeWidth, cap = strokeCap, join = strokeJoin) else Fill
}

private class RcFloatFunctionRuntime {
  val definitions = mutableMapOf<Int, RcLinkedNode.Container>()
  val executing = mutableSetOf<Int>()
  val shaders = mutableMapOf<Int, RcShaderData>()
}

/** Per-draw-pass canvas routing for AndroidX's mutable bitmap targets. */
private class RcDrawTargetState(
  private val mainCanvas: androidx.compose.ui.graphics.Canvas,
  private val mainSize: Size,
  private val images: MutableMap<Int, ImageBitmap>,
  private val offscreenTargets: RcOffscreenTargetPool,
) {
  private var depth = 0

  fun enter() {
    depth++
  }

  fun leave(scope: DrawScope) {
    depth--
    check(depth >= 0) { "Unbalanced bitmap draw-target scope" }
    if (depth == 0) restoreMain(scope)
  }

  fun redirect(scope: DrawScope, operation: RcDrawToBitmap, bitmapId: Int) {
    if (bitmapId == 0) {
      restoreMain(scope)
      return
    }
    val source =
      requireNotNull(images[bitmapId]) { "DrawToBitmap references missing bitmap $bitmapId" }
    val canvas = offscreenTargets.canvasFor(bitmapId, source, images)
    scope.drawContext.canvas = canvas
    scope.drawContext.size = Size(source.width.toFloat(), source.height.toFloat())
    if (operation.mode and RcDrawToBitmap.MODE_NO_INITIALIZE == 0) {
      canvas.drawRect(
        0f,
        0f,
        source.width.toFloat(),
        source.height.toFloat(),
        Paint().apply {
          color = Color(operation.color)
          blendMode = BlendMode.Src
        },
      )
    }
  }

  private fun restoreMain(scope: DrawScope) {
    scope.drawContext.canvas = mainCanvas
    scope.drawContext.size = mainSize
  }
}

private fun DrawScope.drawOperations(
  operations: List<RcLinkedNode>,
  state: RcPlayerState,
  paint: RcPaintState,
  computedPaths: MutableMap<Int, Path>,
  textMeasurer: TextMeasurer,
  images: MutableMap<Int, ImageBitmap>,
  functions: RcFloatFunctionRuntime,
  requestedTheme: Int,
  filterTheme: Boolean,
  drawContent: (() -> Unit)? = null,
  offscreenTargets: RcOffscreenTargetPool? = null,
  targets: RcDrawTargetState? = null,
) {
  val activeTargets =
    targets
      ?: RcDrawTargetState(
        drawContext.canvas,
        drawContext.size,
        images,
        requireNotNull(offscreenTargets) { "Top-level drawing requires an offscreen target pool" },
      )
  activeTargets.enter()
  try {
    drawOperationsRouted(
      operations,
      state,
      paint,
      computedPaths,
      textMeasurer,
      images,
      functions,
      requestedTheme,
      filterTheme,
      drawContent,
      activeTargets,
    )
  } finally {
    activeTargets.leave(this)
  }
}

private fun DrawScope.drawOperationsRouted(
  operations: List<RcLinkedNode>,
  state: RcPlayerState,
  paint: RcPaintState,
  computedPaths: MutableMap<Int, Path>,
  textMeasurer: TextMeasurer,
  images: MutableMap<Int, ImageBitmap>,
  functions: RcFloatFunctionRuntime,
  requestedTheme: Int,
  filterTheme: Boolean,
  drawContent: (() -> Unit)?,
  targets: RcDrawTargetState,
) {
  var currentTheme = RcTheme.UNSPECIFIED
  for (node in operations) {
    if (node is RcLinkedNode.Container) {
      val functionDefinition = node.operation as? RcFloatFunctionDefine
      if (functionDefinition != null) {
        functions.definitions[functionDefinition.id] = node
        continue
      }
      if (!filterTheme || isThemeVisible(requestedTheme, currentTheme)) {
        when (node.operation.opcode) {
          RcOpcodes.CANVAS_OPERATIONS ->
            drawOperations(
              node.children,
              state,
              paint,
              computedPaths,
              textMeasurer,
              images,
              functions,
              requestedTheme,
              filterTheme = false,
              drawContent = drawContent,
              targets = targets,
            )
          RcOpcodes.RUN_ACTION -> state.executeRunAction(node.children)
          RcOpcodes.CONDITIONAL_OPERATIONS -> {
            val conditional = node.operation as RcConditionalOperations
            val holds = state.evaluateConditional(conditional)
            paint.observer?.onConditional(
              RcBranch(
                conditional,
                state.resolve(conditional.left),
                state.resolve(conditional.right),
                holds,
              )
            )
            if (holds) {
              drawOperations(
                node.children,
                state,
                paint,
                computedPaths,
                textMeasurer,
                images,
                functions,
                requestedTheme,
                filterTheme = false,
                drawContent = drawContent,
                targets = targets,
              )
            }
          }
          RcOpcodes.LOOP_START -> {
            val loop = node.operation as RcLoopOperation
            state.forEachLoopValue(loop) {
              drawOperations(
                node.children,
                state,
                paint,
                computedPaths,
                textMeasurer,
                images,
                functions,
                requestedTheme,
                filterTheme = false,
                drawContent = drawContent,
                targets = targets,
              )
            }
          }
          RcOpcodes.PARTICLE_LOOP -> {
            val loop = node.operation as RcParticleLoop
            state.forEachParticle(loop) {
              drawOperations(
                node.children,
                state,
                paint,
                computedPaths,
                textMeasurer,
                images,
                functions,
                requestedTheme,
                filterTheme = false,
                drawContent = drawContent,
                targets = targets,
              )
            }
          }
          RcOpcodes.PARTICLE_COMPARE -> {
            val comparison = node.operation as RcParticleCompare
            state.compareParticles(comparison) {
              drawOperations(
                node.children,
                state,
                paint,
                computedPaths,
                textMeasurer,
                images,
                functions,
                requestedTheme,
                filterTheme = false,
                drawContent = drawContent,
                targets = targets,
              )
            }
          }
          RcOpcodes.IMPULSE_START -> {
            val impulse = node.operation as RcImpulseStart
            val process =
              (node.children.lastOrNull() as? RcLinkedNode.Container)?.takeIf {
                it.operation === RcImpulseProcess
              }
            when (state.evaluateImpulse(impulse)) {
              RcImpulsePhase.INITIALIZE ->
                drawOperations(
                  if (process == null) node.children else node.children.dropLast(1),
                  state,
                  paint,
                  computedPaths,
                  textMeasurer,
                  images,
                  functions,
                  requestedTheme,
                  filterTheme = false,
                  drawContent = drawContent,
                  targets = targets,
                )
              RcImpulsePhase.PROCESS ->
                process?.let {
                  drawOperations(
                    it.children,
                    state,
                    paint,
                    computedPaths,
                    textMeasurer,
                    images,
                    functions,
                    requestedTheme,
                    filterTheme = false,
                    drawContent = drawContent,
                    targets = targets,
                  )
                }
              RcImpulsePhase.WAITING,
              RcImpulsePhase.IDLE -> Unit
            }
          }
          RcOpcodes.IMPULSE_PROCESS ->
            drawOperations(
              node.children,
              state,
              paint,
              computedPaths,
              textMeasurer,
              images,
              functions,
              requestedTheme,
              filterTheme = false,
              drawContent = drawContent,
              targets = targets,
            )
          else -> error("Container opcode ${node.operation.opcode} is not renderable")
        }
      }
      continue
    }
    val operation = (node as RcLinkedNode.Operation).operation
    if (operation is RcTheme) {
      currentTheme = operation.theme
      continue
    }
    if (filterTheme && !isThemeVisible(requestedTheme, currentTheme)) continue
    when (operation) {
      is RcPaintData -> applyPaint(operation, paint, state, images, functions.shaders)
      is RcDraw4 -> draw4(operation, paint, state)
      is RcDraw3 -> draw3(operation, paint, state)
      is RcDraw6 -> draw6(operation, paint, state)
      is RcTransform2 -> transform2(operation, state)
      is RcIdOperation -> drawIdOperation(operation, paint, state, computedPaths)
      is RcPathTween ->
        tweenPathData(
            operation.outId,
            operation.path1Id,
            operation.path2Id,
            state.resolve(operation.tween),
            state,
          )
          ?.let { state.setPath(operation.outId, it) }
      is RcPathCreate ->
        state.setPath(
          operation.id,
          RcPathData(
            operation.id,
            listOf(
              RcFloatWord(0x7fc00000 or RcPathCommands.MOVE),
              operation.startX,
              operation.startY,
            ),
          ),
        )
      is RcPathAppend -> {
        val firstCommand = operation.words.firstOrNull()?.referencedId
        if (firstCommand == RcPathCommands.RESET) {
          state.setPath(operation.id, RcPathData(operation.id, emptyList()))
        } else {
          val existing = state.path(operation.id)
          state.setPath(
            operation.id,
            RcPathData(
              existing?.idAndWinding ?: operation.id,
              existing.orEmptyWords() + operation.words,
            ),
          )
        }
      }
      is RcPathCombine -> {
        val first = pathForId(operation.path1Id, state, computedPaths)
        val second = pathForId(operation.path2Id, state, computedPaths)
        val pathOperation =
          when (operation.operation) {
            0 -> PathOperation.Difference
            1 -> PathOperation.Intersect
            2 -> PathOperation.ReverseDifference
            3 -> PathOperation.Union
            4 -> PathOperation.Xor
            else -> error("Unknown AndroidX path operation ${operation.operation}")
          }
        computedPaths[operation.outId] = Path().apply { op(first, second, pathOperation) }
      }
      is RcPathExpression -> state.applyPathExpression(operation)
      is RcFloatExpression -> state.applyFloatExpression(operation)
      is RcMatrixFromPath -> applyMatrixFromPath(operation, state, computedPaths)
      is RcMatrixVectorMath -> state.applyMatrixVectorMath(operation)
      is RcMatrixExpression -> state.applyMatrixExpression(operation)
      is RcTextMerge,
      is RcTextLength,
      is RcTextSubtext -> state.applyTextOperation(operation)
      is RcTextTransform -> state.applyTextOperation(operation)
      is RcTextFromFloat -> state.applyTextOperation(operation)
      is RcTextLookup -> state.applyTextOperation(operation)
      is RcTextLookupInt -> state.applyTextOperation(operation)
      is RcDataMapLookup -> state.applyDataOperation(operation)
      is RcIdLookup -> state.applyDataOperation(operation)
      is RcDynamicFloatList -> state.applyDataOperation(operation)
      is RcUpdateDynamicFloatList -> state.applyDataOperation(operation)
      is RcFloatFunctionCall -> {
        val definition =
          requireNotNull(functions.definitions[operation.functionId]) {
            "Missing float function ${operation.functionId}"
          }
        val descriptor = definition.operation as RcFloatFunctionDefine
        require(operation.arguments.size <= descriptor.parameterIds.size) {
          "Float function ${operation.functionId} received ${operation.arguments.size} arguments " +
            "for ${descriptor.parameterIds.size} parameters"
        }
        require(functions.executing.add(operation.functionId)) {
          "Recursive float function ${operation.functionId} is not allowed"
        }
        try {
          operation.arguments.forEachIndexed { index, argument ->
            state.setFloat(descriptor.parameterIds[index], state.resolve(argument))
          }
          drawOperations(
            definition.children,
            state,
            paint,
            computedPaths,
            textMeasurer,
            images,
            functions,
            requestedTheme,
            filterTheme = false,
            drawContent = drawContent,
            targets = targets,
          )
        } finally {
          functions.executing.remove(operation.functionId)
        }
      }
      is RcImageAttribute -> state.applyImageAttribute(operation)
      is RcColorAttribute -> state.applyColorAttribute(operation)
      is RcColorExpression -> state.applyColorExpression(operation)
      is RcColorTheme -> state.applyColorTheme(operation, requestedTheme)
      is RcIntegerExpression -> state.applyIntegerExpression(operation)
      is RcHapticFeedback -> state.performHapticFeedback(operation)
      is RcTimeAttribute -> state.applyTimeAttribute(operation)
      is RcWakeIn -> state.requestWakeIn(operation)
      is RcDebugMessage -> state.emitDebugMessage(operation)
      is RcDrawText -> drawTextOperation(operation, state, paint, textMeasurer)
      is RcDrawTextAnchored -> drawTextAnchored(operation, state, paint, textMeasurer)
      is RcDrawTextOnPath -> drawTextOnPath(operation, state, paint, computedPaths, textMeasurer)
      is RcDrawTextOnCircle -> drawTextOnCircle(operation, state, paint, textMeasurer)
      is RcDrawBitmap -> drawBitmap(operation, state, paint, images)
      is RcDrawBitmapInt -> drawBitmapInt(operation, state, paint, images)
      is RcDrawBitmapScaled -> drawBitmapScaled(operation, state, paint, images)
      is RcDrawBitmapFontTextRun ->
        drawBitmapFontTextRun(operation, state, images, paint.alpha, paint.blendMode)
      is RcDrawBitmapFontTextRunOnPath ->
        drawBitmapFontTextOnPath(
          operation,
          pathForId(resolveBitmapFontPathId(operation.pathId, state), state, computedPaths),
          state,
          images,
          paint.alpha,
          paint.blendMode,
        )
      is RcDrawBitmapTextAnchored ->
        drawAnchoredBitmapText(operation, state, images, paint.alpha, paint.blendMode)
      is RcBitmapTextMeasure -> applyBitmapTextMeasure(operation, state)
      is RcTextMeasure -> measureTextOperation(operation, state, paint, textMeasurer)
      is RcTextAttribute ->
        measureTextOperation(
          operation.outId,
          operation.textId,
          operation.type,
          state,
          paint,
          textMeasurer,
        )
      is RcDrawTweenPath -> drawTweenPath(operation, paint, state)
      is RcDrawToBitmap -> targets.redirect(this, operation, state.drawId(operation.bitmapId))
      is RcShaderData -> functions.shaders[operation.shaderId] = operation
      is RcNoArg ->
        when (operation.opcode) {
          RcOpcodes.MATRIX_SAVE -> drawContext.canvas.save()
          RcOpcodes.MATRIX_RESTORE -> drawContext.canvas.restore()
          RcOpcodes.DRAW_CONTENT -> drawContent?.invoke()
        }
      else -> Unit // Constants/data have already populated RcPlayerState.
    }
  }
}

private fun decodeInlineImages(document: RcDocument): MutableMap<Int, ImageBitmap> =
  rcTrace(RcTraceCategory.DOCUMENT, "rc:decodeImages") { decodeInlineImagesUncounted(document) }

private fun decodeInlineImagesUncounted(document: RcDocument): MutableMap<Int, ImageBitmap> =
  document.operations
    .filterIsInstance<RcBitmapData>()
    .mapNotNull { bitmap ->
      if (bitmap.encoding != RcBitmapData.ENCODING_INLINE) null
      else runCatching { bitmap.imageId to decodeInlineImage(bitmap) }.getOrNull()
    }
    .toMap()
    .toMutableMap()

private fun decodeInlineFonts(document: RcDocument): Map<Int, FontFamily> =
  rcTrace(RcTraceCategory.DOCUMENT, "rc:decodeFonts") { decodeInlineFontsUncounted(document) }

private fun decodeInlineFontsUncounted(document: RcDocument): Map<Int, FontFamily> =
  document.operations
    .filterIsInstance<RcFontData>()
    .mapNotNull { font ->
      runCatching {
        font.fontId to FontFamily(rcFontFromBytes("remote-compose-${font.fontId}", font.data))
      }
        .getOrNull()
    }
    .toMap()

/**
 * The [FontFamily] a text op's `fontFamilyId` names, instanced at [settings] when the host holds
 * the face's bytes.
 *
 * [variations] are the document's font-variation axes. They can only be applied to a host face
 * ([RcFontFaces] keeps the bytes for exactly this reason) — a generic family or an inline
 * `FontData` resolves to a `FontFamily` whose faces are already built, so for those the axes are
 * dropped rather than approximated. That is a substitution the render shows honestly; approximating
 * `wdth` by scaling would not be.
 *
 * The naming rules themselves live in [rcResolveTypeface], which is shared so every host gets them
 * rather than reimplementing them. All this adds is reading the recorded name out of player state.
 */
private fun resolveFontFamily(
  fontFamilyId: Int,
  state: RcPlayerState,
  embeddedFonts: Map<Int, FontFamily>,
  typefaces: RcTypefaceLoader,
  variations: RcFontVariations? = null,
): FontFamily =
  rcResolveTypeface(state.text(fontFamilyId), fontFamilyId, embeddedFonts, typefaces, variations)

/**
 * The font-variation axes a `CoreText` style declares: property 20 is a list of *text ids* naming
 * the axis tags (`wght`, `wdth`, …) and property 21 the matching values, which may themselves be
 * document floats rather than literals. Empty when the style names none.
 *
 * Extracted (and pure) so the pairing rule — an axis counts only when both its tag and its value
 * are present — is unit-testable without a document.
 */
private const val CORE_TEXT_FONT_AXIS_TAGS = 20

private const val CORE_TEXT_FONT_AXIS_VALUES = 21

internal fun fontVariationSettings(
  axisTags: List<String?>,
  axisValues: List<Float?>,
): RcFontVariations? {
  val axes = axisTags.mapIndexedNotNull { index, tag ->
    val value = axisValues.getOrNull(index) ?: return@mapIndexedNotNull null
    tag?.takeIf { it.isNotBlank() }?.let { RcFontAxis(it, value) }
  }
  return if (axes.isEmpty()) null else RcFontVariations(axes)
}

/**
 * [settings] with the style's weight added as a `wght` axis, unless the document named `wght`
 * itself.
 *
 * `TextStyle.fontWeight` only picks *between* registered faces, so a family carrying one variable
 * file registered at 400 — which is what a `fonts.json` default role usually is — renders every
 * weight at 400: a `CoreText` asking for Medium came out visibly lighter than the reference, which
 * rasterizes with a real 500 face. Naming the axis is what actually moves a variable font, and on a
 * static face an axis the file does not define is ignored by the font engine, so this is a no-op
 * for families that select by weight in the ordinary way.
 *
 * An explicit `wght` from the document wins: a specimen sweeping the axis is naming the value it
 * wants, and the style weight beside it is only there so a non-variable fallback picks a face.
 */
internal fun withWeightAxis(variations: RcFontVariations?, weight: Int): RcFontVariations? {
  val existing = variations?.axes.orEmpty()
  if (existing.any { it.tag == "wght" }) return variations
  return RcFontVariations(existing + RcFontAxis("wght", weight.coerceIn(1, 1000).toFloat()))
}

private fun decodeInlineImage(bitmap: RcBitmapData): ImageBitmap =
  when (bitmap.type) {
    RcBitmapData.TYPE_PNG_8888,
    RcBitmapData.TYPE_PNG,
    RcBitmapData.TYPE_PNG_ALPHA_8 -> decodeRcEncodedImage(bitmap.data)
    RcBitmapData.TYPE_RAW8888 -> {
      require(bitmap.data.size >= bitmap.width * 4 * bitmap.height) { "Truncated RGBA bitmap" }
      rcRasterImage(bitmap.width, bitmap.height, bitmap.data, alphaOnly = false)
    }
    RcBitmapData.TYPE_RAW8 -> {
      require(bitmap.data.size >= bitmap.width * bitmap.height) { "Truncated alpha bitmap" }
      rcRasterImage(bitmap.width, bitmap.height, bitmap.data, alphaOnly = true)
    }
    else -> error("Unknown AndroidX bitmap type ${bitmap.type}")
  }

private fun DrawScope.drawBitmap(
  operation: RcDrawBitmap,
  state: RcPlayerState,
  paint: RcPaintState,
  images: Map<Int, ImageBitmap>,
) {
  val image = images[state.drawId(operation.imageId)] ?: return
  val left = state.resolve(operation.left)
  val top = state.resolve(operation.top)
  val width = state.resolve(operation.right) - left
  val height = state.resolve(operation.bottom) - top
  if (width == 0f || height == 0f) return
  withTransform({
    translate(left, top)
    scale(width / image.width, height / image.height, Offset.Zero)
  }) {
    // The rect overload: it is the one that takes a sampling quality.
    drawImage(
      image = image,
      dstSize = IntSize(image.width, image.height),
      alpha = paint.alpha,
      blendMode = paint.blendMode,
      filterQuality = paint.filterQuality,
    )
  }
}

private fun DrawScope.drawBitmapInt(
  operation: RcDrawBitmapInt,
  state: RcPlayerState,
  paint: RcPaintState,
  images: Map<Int, ImageBitmap>,
) {
  val image = images[state.drawId(operation.imageId)] ?: return
  drawBitmapRegion(
    image,
    operation.srcLeft,
    operation.srcTop,
    operation.srcRight,
    operation.srcBottom,
    operation.dstLeft,
    operation.dstTop,
    operation.dstRight,
    operation.dstBottom,
    paint,
  )
}

private fun DrawScope.drawBitmapScaled(
  operation: RcDrawBitmapScaled,
  state: RcPlayerState,
  paint: RcPaintState,
  images: Map<Int, ImageBitmap>,
) {
  val image = images[state.drawId(operation.imageId)] ?: return
  val sl = state.resolve(operation.srcLeft)
  val st = state.resolve(operation.srcTop)
  val sr = state.resolve(operation.srcRight)
  val sb = state.resolve(operation.srcBottom)
  val dl = state.resolve(operation.dstLeft)
  val dt = state.resolve(operation.dstTop)
  val dr = state.resolve(operation.dstRight)
  val db = state.resolve(operation.dstBottom)
  val scaled =
    computeImageScaling(
      sl,
      st,
      sr,
      sb,
      dl,
      dt,
      dr,
      db,
      operation.scaleType,
      state.resolve(operation.scaleFactor),
    ) ?: return
  withTransform({ clipRect(dl, dt, dr, db) }) {
    drawBitmapRegion(
      image,
      sl.toInt(),
      st.toInt(),
      sr.toInt(),
      sb.toInt(),
      scaled.left.toInt(),
      scaled.top.toInt(),
      scaled.right.toInt(),
      scaled.bottom.toInt(),
      paint,
    )
  }
}

private fun DrawScope.drawBitmapRegion(
  image: ImageBitmap,
  srcLeft: Int,
  srcTop: Int,
  srcRight: Int,
  srcBottom: Int,
  dstLeft: Int,
  dstTop: Int,
  dstRight: Int,
  dstBottom: Int,
  paint: RcPaintState,
) {
  val srcWidth = srcRight - srcLeft
  val srcHeight = srcBottom - srcTop
  val dstWidth = dstRight - dstLeft
  val dstHeight = dstBottom - dstTop
  if (srcWidth <= 0 || srcHeight <= 0 || dstWidth == 0 || dstHeight == 0) return
  drawImage(
    image = image,
    srcOffset = IntOffset(srcLeft, srcTop),
    srcSize = IntSize(srcWidth, srcHeight),
    dstOffset = IntOffset(dstLeft, dstTop),
    dstSize = IntSize(dstWidth, dstHeight),
    alpha = paint.alpha,
    blendMode = paint.blendMode,
    filterQuality = paint.filterQuality,
  )
}

/**
 * Maps an `ImageLayout`'s AndroidX `ImageScaling` type onto Compose's [ContentScale], as AndroidX's
 * embedded player does. The `ImageLayout` operation carries no scale factor, so `SCALE_FIXED_SCALE`
 * (7) draws 1:1 — [ContentScale.None], which is `FixedScale(1f)`.
 */
internal fun imageLayoutContentScale(scaleType: Int): ContentScale =
  when (scaleType) {
    0 -> ContentScale.None
    1 -> ContentScale.Inside
    2 -> ContentScale.FillWidth
    3 -> ContentScale.FillHeight
    4 -> ContentScale.Fit
    5 -> ContentScale.Crop
    6 -> ContentScale.FillBounds
    7 -> ContentScale.None
    else -> error("Unknown AndroidX image scale type $scaleType")
  }

internal data class RcScaledRect(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
)

/** Exact integer-centering arithmetic from AndroidX ImageScaling.adjustDrawToType. */
internal fun computeImageScaling(
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
): RcScaledRect? {
  val srcWidth = (srcRight - srcLeft).toInt()
  val srcHeight = (srcBottom - srcTop).toInt()
  if (srcWidth == 0 || srcHeight == 0) return null
  val dstWidth = (dstRight - dstLeft).toInt()
  val dstHeight = (dstBottom - dstTop).toInt()
  var width = dstWidth
  var height = dstHeight
  when (scaleType) {
    0 -> {
      width = srcWidth
      height = srcHeight
    }
    1 ->
      if (!(dstHeight > srcHeight && dstWidth > srcWidth)) {
        if (srcWidth.toFloat() * (dstBottom - dstTop) > (dstRight - dstLeft) * srcHeight) {
          height = dstWidth * srcHeight / srcWidth
        } else width = dstHeight * srcWidth / srcHeight
      } else {
        width = srcWidth
        height = srcHeight
      }
    2 -> height = dstWidth * srcHeight / srcWidth
    3 -> width = dstHeight * srcWidth / srcHeight
    4 ->
      if (srcWidth.toFloat() * (dstBottom - dstTop) > (dstRight - dstLeft) * srcHeight) {
        height = dstWidth * srcHeight / srcWidth
      } else width = dstHeight * srcWidth / srcHeight
    5 ->
      if (srcWidth.toFloat() * (dstBottom - dstTop) < (dstRight - dstLeft) * srcHeight) {
        height = dstWidth * srcHeight / srcWidth
      } else width = dstHeight * srcWidth / srcHeight
    6 -> Unit
    7 -> {
      width = (srcWidth * scaleFactor).toInt()
      height = (srcHeight * scaleFactor).toInt()
    }
    else -> error("Unknown AndroidX image scale type $scaleType")
  }
  val x = (dstWidth - width) / 2
  val y = (dstHeight - height) / 2
  return RcScaledRect(dstLeft + x, dstTop + y, dstLeft + x + width, dstTop + y + height)
}

private fun DrawScope.textStyle(paint: RcPaintState): TextStyle =
  TextStyle(
    color = paint.composeColor(),
    // Same conversion as `RcTextLayout` and `CoreText`, for the same reason: `DrawScope` is a
    // `Density`, so `/ density` divided out the density and left the font scale in.
    fontSize = paint.textSize.toSp(),
    fontFamily = paint.fontFamily,
    fontWeight = paint.fontWeight,
    fontStyle = paint.fontStyle,
  )

private fun DrawScope.drawTextOperation(
  operation: RcDrawText,
  state: RcPlayerState,
  paint: RcPaintState,
  textMeasurer: TextMeasurer,
) {
  val source = state.text(operation.textId) ?: return
  val end =
    if (operation.end == -1 || operation.end > source.length) source.length else operation.end
  val text = source.substring(operation.start, end)
  val style = textStyle(paint)
  val layout =
    rcTrace(RcTraceCategory.FRAME, "rc:measureText") {
      textMeasurer.measure(
        text,
        style,
        layoutDirection = if (operation.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
      )
    }
  drawRcTextLayout(
    layout,
    Offset(state.resolve(operation.x), state.resolve(operation.y) - layout.firstBaseline),
    paint,
  )
}

/**
 * Draws an already-measured text layout with the paint's brush or colour, alpha, stroke and blend
 * mode, as AndroidX's `Canvas.drawText` takes them all from its `Paint`.
 *
 * Drawing the measured [layout] rather than handing the text back to `drawText(textMeasurer, …)`
 * saves a second layout per text per frame.
 */
private fun DrawScope.drawRcTextLayout(
  layout: TextLayoutResult,
  topLeft: Offset,
  paint: RcPaintState,
) {
  val brush = paint.brush
  if (brush != null) {
    drawText(
      layout,
      brush = brush,
      topLeft = topLeft,
      alpha = paint.alpha,
      drawStyle = paint.style(),
      blendMode = paint.blendMode,
    )
  } else {
    drawText(
      layout,
      color = paint.composeColor(),
      topLeft = topLeft,
      drawStyle = paint.style(),
      blendMode = paint.blendMode,
    )
  }
}

internal data class RcAnchoredTextPosition(val x: Float, val baselineY: Float)

internal fun computeAnchoredTextPosition(
  anchorX: Float,
  anchorY: Float,
  panX: Float,
  panY: Float,
  left: Float,
  top: Float,
  right: Float,
  bottom: Float,
  baselineRelative: Boolean,
): RcAnchoredTextPosition {
  val width = right - left
  val height = bottom - top
  val x = anchorX - width * (1f + panX) / 2f - left
  val y =
    if (panY.isNaN()) anchorY
    else anchorY - height * (1f - panY) / 2f + if (baselineRelative) height / 2f else -top
  return RcAnchoredTextPosition(x, y)
}

private fun DrawScope.drawTextAnchored(
  operation: RcDrawTextAnchored,
  state: RcPlayerState,
  paint: RcPaintState,
  textMeasurer: TextMeasurer,
) {
  val text = state.text(operation.textId) ?: return
  val style = textStyle(paint)
  val rtl = operation.flags and RcDrawTextAnchored.TEXT_RTL != 0
  val layout =
    rcTrace(RcTraceCategory.FRAME, "rc:measureText") {
      textMeasurer.measure(
        text,
        style,
        layoutDirection = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
      )
    }
  val boxes = text.indices.map(layout::getBoundingBox)
  val left = boxes.minOfOrNull { it.left } ?: 0f
  val right = boxes.maxOfOrNull { it.right } ?: layout.size.width.toFloat()
  val top = (boxes.minOfOrNull { it.top } ?: 0f) - layout.firstBaseline
  val bottom =
    (boxes.maxOfOrNull { it.bottom } ?: layout.size.height.toFloat()) - layout.firstBaseline
  val position =
    computeAnchoredTextPosition(
      state.resolve(operation.x),
      state.resolve(operation.y),
      state.resolve(operation.panX),
      state.resolve(operation.panY),
      left,
      top,
      right,
      bottom,
      operation.flags and RcDrawTextAnchored.BASELINE_RELATIVE != 0,
    )
  drawRcTextLayout(layout, Offset(position.x, position.baselineY - layout.firstBaseline), paint)
  paint.observer?.let { observer ->
    val origin = toDevice(Offset(position.x, position.baselineY))
    observer.onTextRun(RcTextRun(text, unicodeScalars(text).size, origin.x, origin.y))
  }
}

/** [local] in device pixels, through every transform the canvas is under — for [RcDrawObserver]. */
/** The angle the current transform turns the local x-axis to on the device, in degrees. */
private fun DrawScope.deviceRotationDegrees(): Float {
  val m = rcLocalToDevice()
  return atan2(m[3], m[0]) * (180f / PI.toFloat())
}

private fun DrawScope.toDevice(local: Offset): Offset {
  val m = rcLocalToDevice()
  return Offset(
    m[0] * local.x + m[1] * local.y + m[2],
    m[3] * local.x + m[4] * local.y + m[5],
  )
}

/**
 * Curved text, laid along an arc cut to the measured width of the string.
 *
 * This mirrors `DrawTextOnCircle.paint` rather than reimplementing it: measure the text, turn that
 * width into a sweep at the effective radius, resolve [RcDrawTextOnCircle.alignment] into the angle
 * the arc actually starts at, then hand the arc to the same glyph-walker `DrawTextOnPath` uses. The
 * measure and the draw share one [TextMeasurer] and one style for a reason — an arc cut to a width
 * measured under a different typeface would lay the string along the wrong curve.
 *
 * [RcDrawTextOnCircle.PLACEMENT_INSIDE] is a negative sweep, which reverses the tangent the
 * glyph-walker rotates each glyph by, and so flips them upright for a reader at the bottom of the
 * dial. That falls out of the shared walker; there is no second code path for it.
 */
private fun DrawScope.drawTextOnCircle(
  operation: RcDrawTextOnCircle,
  state: RcPlayerState,
  paint: RcPaintState,
  textMeasurer: TextMeasurer,
) {
  val text = state.text(operation.textId).orEmpty()
  if (text.isEmpty()) return
  val centerX = state.resolve(operation.centerX)
  val centerY = state.resolve(operation.centerY)
  val radius = state.resolve(operation.radius) + state.resolve(operation.warpRadiusOffset)
  val startAngle = state.resolve(operation.startAngle)
  // A variable can resolve to anything, and a zero or negative radius divides the sweep by zero.
  // Drawing nothing is what the AndroidX player does with an unusable circle, and it keeps a live
  // document that animates the radius through zero from throwing mid-frame.
  if (!centerX.isFinite() || !centerY.isFinite() || !startAngle.isFinite() || !(radius > 0f)) {
    return
  }
  val style = textStyle(paint)
  // Measured once, summed for the arc, then walked — all from the same list. The arc has to be as
  // long as what the walker will actually consume, and the walker consumes per-glyph advances,
  // whose sum is not the width of the string measured whole: shaping and kerning apply across a
  // whole string and not across one-glyph measurements, and every measurement rounds. Cutting the
  // arc to the whole-string width leaves it a few points short, the walker runs off the end, and
  // the trailing glyphs are dropped — "REMOTE COMPOSE" drawn as "REMOTE COMPOS". Sharing one list
  // between the two is what makes that unrepresentable rather than merely fixed.
  val segments = measureTextSegments(text, style, textMeasurer)
  val textWidth = segments.sumOf { it.advance.toDouble() }.toFloat()
  if (textWidth <= 0f) return
  val arc = arcForCircleText(centerX, centerY, radius, startAngle, textWidth, operation)
  val measure = RcPathMeasure(arc)
  if (measure.length <= 0f) return
  drawTextSegmentsOnPath(
    segments = segments,
    measure = measure,
    horizontalOffset = 0f,
    verticalOffset = 0f,
    paint = paint,
    style = style,
    textMeasurer = textMeasurer,
  )
}

/**
 * The arc [drawTextOnCircle] lays its glyphs along, split out so the angle arithmetic is testable
 * without a draw scope. [radius] is already the effective radius (the operation's radius plus its
 * warp offset), and [textWidth] the measured width of the whole string.
 */
internal fun arcForCircleText(
  centerX: Float,
  centerY: Float,
  radius: Float,
  startAngle: Float,
  textWidth: Float,
  operation: RcDrawTextOnCircle,
): Path {
  val clockwise = operation.placement != RcDrawTextOnCircle.PLACEMENT_INSIDE
  // Arc length over radius is the angle it subtends, in radians.
  val magnitude = textWidth / radius * 180f / PI.toFloat()
  val sweep = if (clockwise) magnitude else -magnitude
  // `startAngle` pins whichever end of the string `alignment` names, so CENTER and END walk the
  // start of the arc backwards along the direction of travel. Signs differ between the two
  // placements because the direction of travel does.
  val offset =
    when (operation.alignment) {
      RcDrawTextOnCircle.ALIGN_CENTER -> magnitude / 2f
      RcDrawTextOnCircle.ALIGN_END -> magnitude
      else -> 0f
    }
  val arcStart = if (clockwise) startAngle - offset else startAngle + offset
  return Path().apply {
    addArc(
      Rect(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
      arcStart,
      sweep,
    )
  }
}

/** AndroidX-compatible glyph-centre placement implemented with Compose's cross-platform fonts. */
private fun DrawScope.drawTextOnPath(
  operation: RcDrawTextOnPath,
  state: RcPlayerState,
  paint: RcPaintState,
  computedPaths: Map<Int, Path>,
  textMeasurer: TextMeasurer,
) {
  val text = state.text(operation.textId).orEmpty()
  if (text.isEmpty()) return
  val path = pathForId(state.drawId(operation.pathId), state, computedPaths)
  val measure = RcPathMeasure(path)
  if (measure.length <= 0f) return
  drawTextOnPathWithCompose(
    text = text,
    measure = measure,
    horizontalOffset = state.resolve(operation.horizontalOffset),
    verticalOffset = state.resolve(operation.verticalOffset),
    paint = paint,
    textMeasurer = textMeasurer,
  )
}

/**
 * Compose text layout supplies the same bundled/fallback fonts on desktop and Wasm. The AndroidX
 * glyph-centre path placement rule is retained, and surrogate pairs are never split.
 */
private fun DrawScope.drawTextOnPathWithCompose(
  text: String,
  measure: RcPathMeasure,
  horizontalOffset: Float,
  verticalOffset: Float,
  paint: RcPaintState,
  textMeasurer: TextMeasurer,
) {
  val style = textStyle(paint)
  drawTextSegmentsOnPath(
    segments = measureTextSegments(text, style, textMeasurer),
    measure = measure,
    horizontalOffset = horizontalOffset,
    verticalOffset = verticalOffset,
    paint = paint,
    style = style,
    textMeasurer = textMeasurer,
  )
}

/** One scalar of a string with the advance the path walker will consume for it. */
private class RcTextSegment(
  val text: String,
  val advance: Float,
  val firstBaseline: Float,
)

/**
 * Measure a string one unicode scalar at a time — the unit the path walker places — so a caller
 * that needs the total width gets the number the walker will really consume rather than one
 * measured a different way. [drawTextOnCircle] cuts its arc from exactly this sum.
 */
private fun measureTextSegments(
  text: String,
  style: androidx.compose.ui.text.TextStyle,
  textMeasurer: TextMeasurer,
): List<RcTextSegment> =
  rcTrace(RcTraceCategory.FRAME, "rc:measureText") {
    unicodeScalars(text).map { scalar ->
      val layout = textMeasurer.measure(scalar, style)
      RcTextSegment(scalar, layout.size.width.toFloat(), layout.firstBaseline)
    }
  }

private fun DrawScope.drawTextSegmentsOnPath(
  segments: List<RcTextSegment>,
  measure: RcPathMeasure,
  horizontalOffset: Float,
  verticalOffset: Float,
  paint: RcPaintState,
  style: androidx.compose.ui.text.TextStyle,
  textMeasurer: TextMeasurer,
) {
  var contourLength = measure.length
  var distance = horizontalOffset
  val glyphs = paint.observer?.let { mutableListOf<RcGlyphPlacement>() }
  for (segment in segments) {
    val advance = segment.advance
    val center = distance + advance / 2f
    if (center > contourLength) {
      // Out of path: stop drawing, but still report the glyphs that were drawn.
      if (!measure.nextContour()) break
      contourLength = measure.length
      distance = 0f
    }
    val position = measure.position(distance + advance / 2f)
    val tangent = measure.tangent(distance + advance / 2f)
    if (position != null && tangent != null) {
      val composePosition = Offset(position.x, position.y)
      val placement =
        computePathTextPlacement(
          composePosition,
          Offset(tangent.x, tangent.y),
          advance,
          verticalOffset,
          segment.firstBaseline,
        )
      withTransform({ rotate(placement.angleDegrees, composePosition) }) {
        drawText(
          textMeasurer = textMeasurer,
          text = segment.text,
          topLeft = placement.topLeft,
          style = style,
          blendMode = paint.blendMode,
        )
        // The glyph's baseline origin and angle, read inside its own rotation so both are where
        // and how it was actually drawn — including any rotation the canvas was already under.
        glyphs?.add(
          toDevice(placement.topLeft + Offset(0f, segment.firstBaseline)).let {
            RcGlyphPlacement(it.x, it.y, deviceRotationDegrees())
          }
        )
      }
    }
    distance += advance
  }
  if (glyphs != null && glyphs.isNotEmpty()) {
    paint.observer?.onTextRun(
      RcTextRun(
        text = segments.joinToString("") { it.text },
        glyphCount = glyphs.size,
        originX = glyphs.first().x,
        originY = glyphs.first().y,
        glyphs = glyphs,
      )
    )
  }
}

internal data class RcPathTextPlacement(val topLeft: Offset, val angleDegrees: Float)

internal fun computePathTextPlacement(
  position: Offset,
  tangent: Offset,
  advance: Float,
  verticalOffset: Float,
  firstBaseline: Float,
): RcPathTextPlacement =
  RcPathTextPlacement(
    topLeft = Offset(position.x - advance / 2f, position.y + verticalOffset - firstBaseline),
    angleDegrees = atan2(tangent.y, tangent.x) * 180f / PI.toFloat(),
  )

private fun unicodeScalars(text: String): List<String> = buildList {
  var offset = 0
  while (offset < text.length) {
    val first = text[offset].code
    val length =
      if (
        first in 0xd800..0xdbff &&
          offset + 1 < text.length &&
          text[offset + 1].code in 0xdc00..0xdfff
      )
        2
      else 1
    add(text.substring(offset, offset + length))
    offset += length
  }
}

private fun DrawScope.measureTextOperation(
  operation: RcTextMeasure,
  state: RcPlayerState,
  paint: RcPaintState,
  textMeasurer: TextMeasurer,
) =
  measureTextOperation(
    operation.outId,
    operation.textId,
    operation.type,
    state,
    paint,
    textMeasurer,
    supportsLength = false,
  )

private fun DrawScope.measureTextOperation(
  outId: Int,
  textId: Int,
  type: Int,
  state: RcPlayerState,
  paint: RcPaintState,
  textMeasurer: TextMeasurer,
  supportsLength: Boolean = true,
) {
  val text = state.text(textId).orEmpty()
  val layout =
    rcTrace(RcTraceCategory.FRAME, "rc:measureText") {
      textMeasurer.measure(text, textStyle(paint))
    }
  var left = 0f
  var right = layout.size.width.toFloat()
  var top = -layout.firstBaseline
  var bottom = layout.size.height - layout.firstBaseline
  if (text.isNotEmpty()) {
    val boxes = text.indices.map(layout::getBoundingBox)
    left = boxes.minOf { it.left }
    right = boxes.maxOf { it.right }
    top = boxes.minOf { it.top } - layout.firstBaseline
    bottom = boxes.maxOf { it.bottom } - layout.firstBaseline
  }
  val flags = type ushr 8
  if (flags and 0x04 != 0) {
    left = 0f
    right = layout.size.width.toFloat()
  } else if (flags and 0x01 != 0) {
    right = layout.size.width.toFloat() - left
  }
  if (flags and 0x02 != 0) {
    top = -layout.firstBaseline
    bottom = layout.size.height - layout.firstBaseline
  }
  selectTextMeasurement(type, left, top, right, bottom, text.length, supportsLength)?.let { value ->
    state.setFloat(outId, value)
  }
}

/**
 * Selects the value written by AndroidX's text measurement operations.
 *
 * `TextMeasure` (155) only defines selectors 0..5; `TextAttribute` additionally defines selector 6
 * for string length. AndroidX leaves the destination untouched for an unknown selector, hence the
 * nullable result.
 */
internal fun selectTextMeasurement(
  type: Int,
  left: Float,
  top: Float,
  right: Float,
  bottom: Float,
  textLength: Int,
  supportsLength: Boolean = true,
): Float? =
  when (type and 0xff) {
    0 -> right - left
    1 -> bottom - top
    2 -> left
    3 -> right
    4 -> top
    5 -> bottom
    6 -> if (supportsLength) textLength.toFloat() else null
    else -> null
  }

private fun DrawScope.applyMatrixFromPath(
  operation: RcMatrixFromPath,
  state: RcPlayerState,
  computedPaths: Map<Int, Path>,
) {
  val path = pathForId(operation.pathId, state, computedPaths)
  val measure = PathMeasure().apply { setPath(path, forceClosed = false) }
  if (measure.length <= 0f) return
  // This modulo, and the currently unused vertical offset, intentionally match AndroidPaintContext.
  val distance = (measure.length * state.resolve(operation.percent)) % measure.length
  if (operation.flags and POSITION_MATRIX_FLAG != 0) {
    val position = measure.getPosition(distance)
    drawContext.transform.translate(position.x, position.y)
  }
  if (operation.flags and TANGENT_MATRIX_FLAG != 0) {
    val tangent = measure.getTangent(distance)
    val degrees = atan2(tangent.y, tangent.x) * 180f / PI.toFloat()
    drawContext.transform.rotate(degrees, Offset.Zero)
  }
}

private const val POSITION_MATRIX_FLAG = 0x01
private const val TANGENT_MATRIX_FLAG = 0x02

private fun RcPathData?.orEmptyWords(): List<RcFloatWord> = this?.words ?: emptyList()

private fun DrawScope.drawTweenPath(
  operation: RcDrawTweenPath,
  paint: RcPaintState,
  state: RcPlayerState,
) {
  val data =
    tweenPathData(
      -1,
      state.drawId(operation.path1Id),
      state.drawId(operation.path2Id),
      state.resolve(operation.tween),
      state,
    ) ?: return
  val path = buildPath(data, state)
  val start = state.resolve(operation.start)
  val stop = state.resolve(operation.stop)
  val trimmed = trimPath(path, start, stop)
  drawRcPath(trimmed, paint)
}

/**
 * The interpolated path, or null when either source path is not in the cache.
 *
 * A missing source is not an error: `path_tween_morph` ships a `PATH_TWEEN` that references two
 * paths the document never declares, and both reference implementations record the tween without
 * morphing anything — the vendored TypeScript player stubs `PathTween` outright. Throwing here took
 * the rest of the document with it, so the tween is skipped instead, exactly as a `DrawPath` with
 * no path draws nothing.
 */
internal fun tweenPathData(
  outId: Int,
  path1Id: Int,
  path2Id: Int,
  tween: Float,
  state: RcPlayerState,
): RcPathData? {
  val first = state.path(path1Id) ?: return null
  val second = state.path(path2Id) ?: return null
  if (tween == 0f) return first.copy(idAndWinding = outId)
  if (tween == 1f) return second.copy(idAndWinding = outId)
  require(first.words.size >= second.words.size) {
    "Path $path1Id has fewer words than path $path2Id"
  }
  val commandIndexes = pathCommandIndexes(first.words)
  val words =
    List(second.words.size) { index ->
      val firstWord = first.words[index]
      val secondWord = second.words[index]
      if (index in commandIndexes) {
        firstWord
      } else {
        val start = state.resolve(firstWord)
        val end = state.resolve(secondWord)
        RcFloatWord.literal(start + (end - start) * tween)
      }
    }
  return RcPathData(outId, words)
}

private fun pathCommandIndexes(words: List<RcFloatWord>): Set<Int> {
  val indexes = mutableSetOf<Int>()
  var index = 0
  while (index < words.size) {
    indexes += index
    when (words[index].referencedId) {
      RcPathCommands.MOVE -> index += 3
      RcPathCommands.LINE -> index += 5
      RcPathCommands.QUADRATIC -> index += 7
      RcPathCommands.CONIC -> index += 8
      RcPathCommands.CUBIC -> index += 9
      RcPathCommands.CLOSE,
      RcPathCommands.DONE -> index += 1
      else -> error("Path command at word $index is invalid")
    }
  }
  return indexes
}

private fun trimPath(path: Path, start: Float, stop: Float): Path {
  if (start <= 0f && stop >= 1f) return path
  val result = Path()
  if (start < stop) {
    val measure = PathMeasure().apply { setPath(path, forceClosed = false) }
    measure.getSegment(
      start.coerceAtLeast(0f) * measure.length,
      stop.coerceAtMost(1f) * measure.length,
      result,
      startWithMoveTo = true,
    )
  }
  return result
}

internal fun isThemeVisible(requestedTheme: Int, operationTheme: Int): Boolean =
  requestedTheme == RcTheme.UNSPECIFIED ||
    operationTheme == RcTheme.UNSPECIFIED ||
    operationTheme == requestedTheme

private fun DrawScope.drawIdOperation(
  operation: RcIdOperation,
  paint: RcPaintState,
  state: RcPlayerState,
  computedPaths: Map<Int, Path>,
) {
  when (operation.opcode) {
    RcOpcodes.DRAW_PATH -> {
      drawRcPath(pathForId(state.drawId(operation.id), state, computedPaths), paint)
    }
    RcOpcodes.CLIP_PATH -> {
      // AndroidX packs the path id in the low 20 bits and the Region.Op in the high byte.
      val pathId = operation.id and 0x000fffff
      val regionOp = operation.id shr 24
      drawContext.canvas.clipPath(
        pathForId(pathId, state, computedPaths),
        if (regionOp == 1) ClipOp.Difference else ClipOp.Intersect,
      )
    }
  }
}

private fun DrawScope.drawRcPath(path: Path, paint: RcPaintState) {
  val brush = paint.brush
  if (brush == null) {
    drawPath(
      path = path,
      color = paint.composeColor(),
      style = paint.style(),
      colorFilter = paint.colorFilter,
      blendMode = paint.blendMode,
    )
  } else {
    drawPath(
      path = path,
      brush = brush,
      alpha = paint.alpha,
      style = paint.style(),
      colorFilter = paint.colorFilter,
      blendMode = paint.blendMode,
    )
  }
}

private fun pathForId(id: Int, state: RcPlayerState, computedPaths: Map<Int, Path>): Path =
  computedPaths[id] ?: state.path(id)?.let { buildPath(it, state) } ?: error("Missing path $id")

/** Convert AndroidX's padded float-word path encoding without canonicalising command NaNs. */
private fun buildPath(data: RcPathData, state: RcPlayerState): Path {
  val path =
    Path().apply {
      fillType = if (data.winding == 1) PathFillType.EvenOdd else PathFillType.NonZero
    }
  var index = 0
  // Common `Path` cannot report its current point, and a conic drawn as quads needs its start.
  var lastX = 0f
  var lastY = 0f
  var contourX = 0f
  var contourY = 0f
  fun argument(): Float {
    if (index >= data.words.size) error("Truncated PathData ${data.id} at word $index")
    return state.resolve(data.words[index++])
  }
  fun skipLegacyPadding() {
    if (index + 2 > data.words.size) error("Truncated PathData ${data.id} legacy padding")
    index += 2
  }
  while (index < data.words.size) {
    val command =
      data.words[index++].referencedId
        ?: error("PathData ${data.id} command at word ${index - 1} is not NaN-encoded")
    when (command) {
      RcPathCommands.MOVE -> {
        lastX = argument()
        lastY = argument()
        contourX = lastX
        contourY = lastY
        path.moveTo(lastX, lastY)
      }
      RcPathCommands.LINE -> {
        skipLegacyPadding()
        lastX = argument()
        lastY = argument()
        path.lineTo(lastX, lastY)
      }
      RcPathCommands.QUADRATIC -> {
        skipLegacyPadding()
        val cx = argument()
        val cy = argument()
        lastX = argument()
        lastY = argument()
        path.quadraticTo(cx, cy, lastX, lastY)
      }
      RcPathCommands.CONIC -> {
        skipLegacyPadding()
        val cx = argument()
        val cy = argument()
        val x = argument()
        val y = argument()
        path.rcConicTo(lastX, lastY, cx, cy, x, y, argument())
        lastX = x
        lastY = y
      }
      RcPathCommands.CUBIC -> {
        skipLegacyPadding()
        val c1x = argument()
        val c1y = argument()
        val c2x = argument()
        val c2y = argument()
        lastX = argument()
        lastY = argument()
        path.cubicTo(c1x, c1y, c2x, c2y, lastX, lastY)
      }
      RcPathCommands.CLOSE -> {
        path.close()
        lastX = contourX
        lastY = contourY
      }
      RcPathCommands.DONE -> return path
      else -> error("PathData ${data.id} has unknown command $command")
    }
  }
  return path
}

private fun DrawScope.draw4(operation: RcDraw4, paint: RcPaintState, state: RcPlayerState) {
  val a = state.resolve(operation.first)
  val b = state.resolve(operation.second)
  val c = state.resolve(operation.third)
  val d = state.resolve(operation.fourth)
  when (operation.opcode) {
    RcOpcodes.DRAW_RECT -> {
      val topLeft = Offset(a, b)
      val size = Size(c - a, d - b)
      val brush = paint.brush
      if (brush == null) {
        drawRect(
          paint.composeColor(),
          topLeft,
          size,
          style = paint.style(),
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      } else {
        drawRect(
          brush,
          topLeft,
          size,
          alpha = paint.alpha,
          style = paint.style(),
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      }
    }
    RcOpcodes.DRAW_OVAL -> {
      val brush = paint.brush
      if (brush == null) {
        drawOval(
          paint.composeColor(),
          Offset(a, b),
          Size(c - a, d - b),
          style = paint.style(),
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      } else {
        drawOval(
          brush,
          Offset(a, b),
          Size(c - a, d - b),
          alpha = paint.alpha,
          style = paint.style(),
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      }
    }
    RcOpcodes.DRAW_LINE -> {
      val brush = paint.brush
      if (brush == null) {
        drawLine(
          paint.composeColor(),
          Offset(a, b),
          Offset(c, d),
          strokeWidth = paint.strokeWidth,
          cap = paint.strokeCap,
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      } else {
        drawLine(
          brush,
          Offset(a, b),
          Offset(c, d),
          strokeWidth = paint.strokeWidth,
          cap = paint.strokeCap,
          alpha = paint.alpha,
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      }
    }
    RcOpcodes.CLIP_RECT -> drawContext.canvas.clipRect(a, b, c, d)
    RcOpcodes.MATRIX_SCALE -> drawContext.transform.scale(a, b, rcMatrixPivot(c, d))
  }
}

private fun DrawScope.draw3(operation: RcDraw3, paint: RcPaintState, state: RcPlayerState) {
  val a = state.resolve(operation.first)
  val b = state.resolve(operation.second)
  val c = state.resolve(operation.third)
  when (operation.opcode) {
    RcOpcodes.DRAW_CIRCLE -> {
      // A gradient set on the paint shades a circle as it does a rect: drawing the flat colour
      // instead painted `canvas_shader_gradient`'s sweep-gradient disc solid black.
      val brush = paint.brush
      if (brush == null) {
        drawCircle(
          paint.composeColor(),
          c,
          Offset(a, b),
          style = paint.style(),
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      } else {
        drawCircle(
          brush,
          c,
          Offset(a, b),
          alpha = paint.alpha,
          style = paint.style(),
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      }
    }
    RcOpcodes.MATRIX_ROTATE -> drawContext.transform.rotate(a, rcMatrixPivot(b, c))
  }
}

/** AndroidX encodes an omitted matrix pivot as NaN in the first pivot coordinate. */
internal fun rcMatrixPivot(x: Float, y: Float): Offset =
  if (x.isNaN()) Offset.Zero else Offset(x, y)

private fun DrawScope.draw6(operation: RcDraw6, paint: RcPaintState, state: RcPlayerState) {
  val a = state.resolve(operation.first)
  val b = state.resolve(operation.second)
  val c = state.resolve(operation.third)
  val d = state.resolve(operation.fourth)
  val e = state.resolve(operation.fifth)
  val f = state.resolve(operation.sixth)
  when (operation.opcode) {
    RcOpcodes.DRAW_ROUND_RECT -> {
      val topLeft = Offset(a, b)
      val size = Size(c - a, d - b)
      val cornerRadius = CornerRadius(e, f)
      val brush = paint.brush
      if (brush == null) {
        drawRoundRect(
          paint.composeColor(),
          topLeft,
          size,
          cornerRadius,
          style = paint.style(),
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      } else {
        drawRoundRect(
          brush,
          topLeft,
          size,
          cornerRadius,
          alpha = paint.alpha,
          style = paint.style(),
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      }
    }
    RcOpcodes.DRAW_ARC,
    RcOpcodes.DRAW_SECTOR -> {
      val useCenter = operation.opcode == RcOpcodes.DRAW_SECTOR
      val brush = paint.brush
      if (brush == null) {
        drawArc(
          paint.composeColor(),
          e,
          f,
          useCenter = useCenter,
          topLeft = Offset(a, b),
          size = Size(c - a, d - b),
          style = paint.style(),
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      } else {
        drawArc(
          brush,
          e,
          f,
          useCenter = useCenter,
          topLeft = Offset(a, b),
          size = Size(c - a, d - b),
          alpha = paint.alpha,
          style = paint.style(),
          colorFilter = paint.colorFilter,
          blendMode = paint.blendMode,
        )
      }
    }
  }
}

private fun DrawScope.transform2(operation: RcTransform2, state: RcPlayerState) {
  val a = state.resolve(operation.first)
  val b = state.resolve(operation.second)
  when (operation.opcode) {
    RcOpcodes.MATRIX_TRANSLATE -> drawContext.transform.translate(a, b)
    RcOpcodes.MATRIX_SKEW -> {
      val matrix =
        Matrix().apply {
          this[1, 0] = a
          this[0, 1] = b
        }
      drawContext.transform.transform(matrix)
    }
  }
}

private fun applyPaint(
  operation: RcPaintData,
  state: RcPaintState,
  values: RcPlayerState,
  images: Map<Int, ImageBitmap>,
  shaders: Map<Int, RcShaderData>,
) {
  var index = 0
  while (index < operation.words.size) {
    val command = operation.words[index++]
    when (command and 0xffff) {
      1 ->
        state.textSize =
          values.resolve(
            ee.schimke.composeai.rcplayer.protocol.RcFloatWord(operation.words[index++])
          )
      4 -> state.color = operation.words[index++] // PaintBundle.COLOR
      5 ->
        state.strokeWidth =
          values.resolve(
            ee.schimke.composeai.rcplayer.protocol.RcFloatWord(operation.words[index++])
          )
      7 ->
        state.strokeCap =
          when (command ushr 16) {
            1 -> StrokeCap.Round
            2 -> StrokeCap.Square
            else -> StrokeCap.Butt
          }
      8 -> state.stroke = command ushr 16 == 1
      9 -> {
        val shaderId = operation.words[index++]
        if (shaderId == 0) {
          state.baseShader = null
          state.runtimeShaderOwner = null
          state.brush = null
        } else {
          val runtimeShader = buildRuntimeShader(shaderId, values, images, shaders)
          state.runtimeShaderOwner = runtimeShader.owner
          state.baseShader = runtimeShader.shader
          state.brush = constantShaderBrush(runtimeShader.shader)
        }
      }
      11 -> index = applyGradient(operation.words, index, command, state, values)
      12 ->
        state.alpha =
          values
            .resolve(ee.schimke.composeai.rcplayer.protocol.RcFloatWord(operation.words[index++]))
            .coerceIn(0f, 1f)
      15 ->
        state.strokeJoin =
          when (command ushr 16) {
            1 -> StrokeJoin.Round
            2 -> StrokeJoin.Bevel
            else -> StrokeJoin.Miter
          }
      18 -> {
        state.blendModeValue = command ushr 16
        state.blendMode = blendMode(state.blendModeValue)
      }
      // Sampling hints whose value is packed in the command's high bits, with no operand word. A
      // container painter emits FILTER_BITMAP ahead of its TEXTURE, so rejecting one rejected the
      // whole document. The bitmap ones set how scaled bitmaps are sampled, as the embedded player
      // maps them; ANTI_ALIAS is Compose's to decide and is consumed.
      10 ->
        state.filterQuality =
          when (command ushr 16) {
            0 -> FilterQuality.None
            1 -> FilterQuality.Low
            2 -> FilterQuality.Medium
            3 -> FilterQuality.High
            else -> FilterQuality.Low
          }
      17 ->
        state.filterQuality = if (command ushr 16 != 0) FilterQuality.Low else FilterQuality.None
      14 -> Unit
      19 -> state.color = values.color(operation.words[index++])
      13 -> {
        state.colorFilter =
          ColorFilter.tint(Color(operation.words[index++]), blendMode(command ushr 16))
      }
      20 -> {
        state.colorFilter =
          ColorFilter.tint(
            Color(values.color(operation.words[index++])),
            blendMode(command ushr 16),
          )
      }
      21 -> state.colorFilter = null
      22 -> {
        val matrixId =
          ee.schimke.composeai.rcplayer.protocol.RcFloatWord(operation.words[index++]).referencedId
        val shader = state.baseShader
        if (shader != null) {
          state.brush =
            constantShaderBrush(transformRcShader(shader, matrixId?.let(values::matrixValues)))
        }
      }
      23 -> {
        val count = command ushr 16
        repeat(count) {
          val axis = operation.words[index++]
          val value =
            values.resolve(
              ee.schimke.composeai.rcplayer.protocol.RcFloatWord(operation.words[index++])
            )
          when (axis) {
            FONT_AXIS_WEIGHT -> state.fontWeight = FontWeight(value.roundToInt().coerceIn(1, 1000))
            FONT_AXIS_ITALIC ->
              state.fontStyle = if (value >= 0.5f) FontStyle.Italic else FontStyle.Normal
            FONT_AXIS_SLANT ->
              state.fontStyle = if (value != 0f) FontStyle.Italic else FontStyle.Normal
          }
        }
      }
      24 -> {
        val imageId = operation.words[index++]
        val tileModes = operation.words[index++]
        index++ // filter/max-anisotropy; Compose owns bitmap sampling.
        state.baseShader =
          images[imageId]?.let {
            ImageShader(
              it,
              gradientTileMode(tileModes and 0xf),
              gradientTileMode((tileModes ushr 16) and 0xf),
            )
          }
        state.brush = state.baseShader?.let(::constantShaderBrush)
      }
      16 -> {
        val style = command ushr 16
        val fontType = operation.words[index++]
        state.fontType = fontType
        val family =
          when (fontType) {
            0 -> RC_DEFAULT_FAMILY
            1 -> "sans-serif"
            2 -> "serif"
            3 -> "monospace"
            else -> error("AndroidX font id $fontType is not implemented by the CMP backend")
          }
        state.fontFamily = rcResolveTypeface(family, -1, emptyMap(), state.typefaces)
        state.fontWeight = FontWeight((style and 0x3ff).takeIf { it > 0 } ?: 400)
        state.fontStyle = if (style and 0x800 != 0) FontStyle.Italic else FontStyle.Normal
      }
      else -> error("Paint command ${command and 0xffff} is not implemented by the baseline player")
    }
  }
}

private fun buildRuntimeShader(
  shaderId: Int,
  state: RcPlayerState,
  images: Map<Int, ImageBitmap>,
  shaders: Map<Int, RcShaderData>,
): RcRuntimeShader {
  val data =
    requireNotNull(
      shaders[shaderId]
        ?: state.document.operations.filterIsInstance<RcShaderData>().lastOrNull {
          it.shaderId == shaderId
        }
    ) {
      "Missing ShaderData for shader $shaderId"
    }
  val source =
    requireNotNull(state.text(data.shaderTextId)) {
      "Shader $shaderId references missing text ${data.shaderTextId}"
    }
  return try {
    val builder = rcRuntimeShaderBuilder(source)
    data.floatUniforms.forEach { (name, words) ->
      val dynamic = words.singleOrNull()?.referencedId?.let(state::floatValues)
      builder.floatUniform(name, dynamic ?: words.map(state::resolve).toFloatArray())
    }
    data.intUniforms.forEach { (name, values) ->
      require(values.size in 1..4) {
        "Shader $shaderId integer uniform '$name' has ${values.size} values; expected 1..4"
      }
      builder.intUniform(name, values.toIntArray())
    }
    data.bitmapUniforms.forEach { (name, bitmapId) ->
      val image =
        requireNotNull(images[bitmapId]) {
          "Shader $shaderId bitmap uniform '$name' references missing bitmap $bitmapId"
        }
      builder.bitmapUniform(name, image)
    }
    builder.build()
  } catch (failure: Throwable) {
    throw IllegalArgumentException(
      "Shader $shaderId is unsupported by the platform runtime: ${failure.message}",
      failure,
    )
  }
}

private fun applyGradient(
  words: List<Int>,
  startIndex: Int,
  command: Int,
  state: RcPaintState,
  values: RcPlayerState,
): Int {
  var index = startIndex
  val descriptor = words[index++]
  val colorCount = descriptor and 0xff
  val colorIdMask = descriptor ushr 16
  val colors =
    List(colorCount) { colorIndex ->
      val word = words[index++]
      Color(if (colorIdMask and (1 shl colorIndex) != 0) values.color(word) else word)
    }
  val stopCount = words[index++]
  val stops = List(stopCount) { values.resolve(RcFloatWord(words[index++])) }
  fun coordinate(): Float = values.resolve(RcFloatWord(words[index++]))
  // A gradient replaces the preceding shader, so a following SHADER_MATRIX clear must not
  // resurrect an image texture from an earlier PaintData operation.
  state.baseShader = null
  state.brush =
    when (command ushr 16) {
      0 -> {
        val start = Offset(coordinate(), coordinate())
        val end = Offset(coordinate(), coordinate())
        val tileMode = gradientTileMode(words[index++])
        if (stops.isEmpty()) Brush.linearGradient(colors, start, end, tileMode)
        else
          Brush.linearGradient(
            *stops.zip(colors).map { it.first to it.second }.toTypedArray(),
            start = start,
            end = end,
            tileMode = tileMode,
          )
      }
      1 -> {
        val center = Offset(coordinate(), coordinate())
        val radius = coordinate()
        val tileMode = gradientTileMode(words[index++])
        if (stops.isEmpty()) Brush.radialGradient(colors, center, radius, tileMode)
        else
          Brush.radialGradient(
            *stops.zip(colors).map { it.first to it.second }.toTypedArray(),
            center = center,
            radius = radius,
            tileMode = tileMode,
          )
      }
      2 -> {
        val center = Offset(coordinate(), coordinate())
        if (stops.isEmpty()) Brush.sweepGradient(colors, center)
        else
          Brush.sweepGradient(
            *stops.zip(colors).map { it.first to it.second }.toTypedArray(),
            center = center,
          )
      }
      else -> error("Gradient type ${command ushr 16} is not implemented")
    }
  return index
}

private fun constantShaderBrush(shader: Shader): Brush =
  object : ShaderBrush() {
    override fun createShader(size: Size): Shader = shader
  }

internal expect fun transformRcShader(shader: Shader, matrix: FloatArray?): Shader

private fun gradientTileMode(value: Int): TileMode =
  when (value) {
    1 -> TileMode.Repeated
    2 -> TileMode.Mirror
    3 -> TileMode.Decal
    else -> TileMode.Clamp
  }

private const val FONT_AXIS_WEIGHT = 0x77676874 // wght
private const val FONT_AXIS_ITALIC = 0x6974616c // ital
private const val FONT_AXIS_SLANT = 0x736c6e74 // slnt

private fun blendMode(value: Int): BlendMode =
  when (value) {
    0 -> BlendMode.Clear
    1 -> BlendMode.Src
    2 -> BlendMode.Dst
    3 -> BlendMode.SrcOver
    4 -> BlendMode.DstOver
    5 -> BlendMode.SrcIn
    6 -> BlendMode.DstIn
    7 -> BlendMode.SrcOut
    8 -> BlendMode.DstOut
    9 -> BlendMode.SrcAtop
    10 -> BlendMode.DstAtop
    11 -> BlendMode.Xor
    12 -> BlendMode.Plus
    13 -> BlendMode.Modulate
    14 -> BlendMode.Screen
    15 -> BlendMode.Overlay
    16 -> BlendMode.Darken
    17 -> BlendMode.Lighten
    18 -> BlendMode.ColorDodge
    19 -> BlendMode.ColorBurn
    20 -> BlendMode.Hardlight
    21 -> BlendMode.Softlight
    22 -> BlendMode.Difference
    23 -> BlendMode.Exclusion
    24 -> BlendMode.Multiply
    25 -> BlendMode.Hue
    26 -> BlendMode.Saturation
    27 -> BlendMode.Color
    28 -> BlendMode.Luminosity
    else -> BlendMode.SrcOver
  }

private const val MILLIS_PER_SECOND = 1_000L

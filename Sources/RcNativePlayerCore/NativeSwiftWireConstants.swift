import Foundation

/// Native names for the image scale values defined by AndroidX `ImageScaling`.
/// Keep every wire value here so native hosts do not grow independent magic-number mappings.
public enum NativeSwiftImageScaleType {
  public static let none = 0
  public static let inside = 1
  public static let fitWidth = 2
  public static let fitHeight = 3
  public static let fit = 4
  public static let crop = 5
  public static let fillBounds = 6
  public static let fixed = 7
}

/// The text transform values defined by AndroidX `TextTransform`.
public enum NativeSwiftTextTransformOperation {
  public static let identity = 0
  public static let lowercase = 1
  public static let uppercase = 2
  public static let trim = 3
  public static let capitalizeWords = 4
  public static let capitalizeFirst = 5
}

public enum NativeSwiftWireOpcode {
  public static let drawText = 43
  public static let drawTextOnPath = 53
  public static let drawTextOnCircle = 57
  public static let drawTextAnchored = 133
  public static let conditionalOperations = 178
  public static let drawBitmap = 44
  public static let textFromFloat = 135
  public static let textMerge = 136
  public static let drawBitmapScaled = 149
  public static let textLookup = 151
  public static let textLookupInt = 153
  public static let textTransform = 199
  public static let matrixConstant = 186
  public static let matrixExpression = 187
  public static let matrixVectorMath = 188

  // Every other opcode in AndroidX's `Operations` registry, from the authoritative wire
  // implementation (`rc-player-protocol`'s `rc-operations.manifest`). Named here so a decoder,
  // census or probe never has to spell one as a literal; the core does not execute them all.
  public static let header = 0
  public static let componentStart = 2
  public static let animationSpec = 14
  public static let modifierWidth = 16
  public static let clipPath = 38
  public static let clipRect = 39
  public static let paintValues = 40
  public static let drawRect = 42
  public static let dataShader = 45
  public static let drawCircle = 46
  public static let drawLine = 47
  public static let drawBitmapFontTextRun = 48
  public static let drawBitmapFontTextRunOnPath = 49
  public static let drawRoundRect = 51
  public static let drawSector = 52
  public static let modifierRoundedClipRect = 54
  public static let modifierBackground = 55
  public static let drawOval = 56
  public static let modifierPadding = 58
  public static let modifierClick = 59
  public static let theme = 63
  public static let clickArea = 64
  public static let rootContentBehavior = 65
  public static let drawBitmapInt = 66
  public static let modifierHeight = 67
  public static let dataFloat = 80
  public static let animatedFloat = 81
  public static let modifierMultiClick = 83
  public static let layoutCustom = 93
  public static let dataBitmap = 101
  public static let dataText = 102
  public static let rootContentDescription = 103
  public static let modifierBorder = 107
  public static let modifierClipRect = 108
  public static let dataPath = 123
  public static let drawPath = 124
  public static let drawTweenPath = 125
  public static let matrixScale = 126
  public static let matrixTranslate = 127
  public static let matrixSkew = 128
  public static let matrixRotate = 129
  public static let matrixSave = 130
  public static let matrixRestore = 131
  public static let colorExpressions = 134
  public static let namedVariable = 137
  public static let colorConstant = 138
  public static let drawContent = 139
  public static let dataInt = 140
  public static let playSound = 141
  public static let referencedOperations = 142
  public static let dataBoolean = 143
  public static let integerExpression = 144
  public static let idMap = 145
  public static let idList = 146
  public static let floatList = 147
  public static let dataLong = 148
  public static let componentValue = 150
  public static let drawArc = 152
  public static let dataMapLookup = 154
  public static let textMeasure = 155
  public static let textLength = 156
  public static let touchExpression = 157
  public static let pathTween = 158
  public static let pathCreate = 159
  public static let pathAdd = 160
  public static let particleDefine = 161
  public static let particleProcess = 162
  public static let particleLoop = 163
  public static let impulseStart = 164
  public static let impulseProcess = 165
  public static let functionCall = 166
  public static let dataBitmapFont = 167
  public static let functionDefine = 168
  public static let dataSound = 169
  public static let attributeText = 170
  public static let attributeImage = 171
  public static let attributeTime = 172
  public static let canvasOperations = 173
  public static let modifierDrawContent = 174
  public static let pathCombine = 175
  public static let layoutFitBox = 176
  public static let hapticFeedback = 177
  public static let debugMessage = 179
  public static let attributeColor = 180
  public static let matrixFromPath = 181
  public static let textSubtext = 182
  public static let bitmapTextMeasure = 183
  public static let drawBitmapTextAnchored = 184
  public static let rem = 185
  public static let dataFont = 189
  public static let drawToBitmap = 190
  public static let wakeIn = 191
  public static let idLookup = 192
  public static let pathExpression = 193
  public static let particleCompare = 194
  public static let colorTheme = 196
  public static let dynamicFloatList = 197
  public static let updateDynamicFloatList = 198
  public static let layoutRoot = 200
  public static let layoutContent = 201
  public static let layoutBox = 202
  public static let layoutRow = 203
  public static let layoutColumn = 204
  public static let layoutCanvas = 205
  public static let soundExpression = 206
  public static let layoutCanvasContent = 207
  public static let layoutText = 208
  public static let hostAction = 209
  public static let hostNamedAction = 210
  public static let modifierVisibility = 211
  public static let valueIntegerChangeAction = 212
  public static let valueStringChangeAction = 213
  public static let containerEnd = 214
  public static let loopStart = 215
  public static let hostMetadataAction = 216
  public static let layoutState = 217
  public static let valueIntegerExpressionChangeAction = 218
  public static let modifierTouchDown = 219
  public static let modifierTouchUp = 220
  public static let modifierOffset = 221
  public static let valueFloatChangeAction = 222
  public static let modifierZindex = 223
  public static let modifierGraphicsLayer = 224
  public static let modifierTouchCancel = 225
  public static let modifierScroll = 226
  public static let valueFloatExpressionChangeAction = 227
  public static let modifierMarquee = 228
  public static let modifierRipple = 229
  public static let layoutCollapsibleRow = 230
  public static let modifierWidthIn = 231
  public static let modifierHeightIn = 232
  public static let layoutCollapsibleColumn = 233
  public static let layoutImage = 234
  public static let modifierCollapsiblePriority = 235
  public static let runAction = 236
  public static let modifierAlignBy = 237
  public static let layoutCompute = 238
  public static let coreText = 239
  public static let layoutFlow = 240
  public static let skip = 241
  public static let textStyle = 242
  public static let modifierDimensionConstraints = 243
  public static let macroForEach = 244
  public static let includeReferencedOperations = 245
  public static let macroDefine = 246
  public static let macroCall = 247
  public static let macroArgument = 248
  public static let macroBlock = 249
  public static let accessibilitySemantics = 250
  public static let loadBitmap = 4
  public static let matrixSet = 132
  public static let update = 195
  public static let extensionRangeReserved4 = 251
  public static let extensionRangeReserved3 = 252
  public static let extensionRangeReserved2 = 253
  public static let extensionRangeReserved1 = 254
  public static let extendedOpcode = 255
}

/// `SKIP`'s condition types: every value AndroidX's `Skip` defines (the vendored
/// `third_party/remote-compose-player` `Skip.ts`). Any other condition never skips.
enum NativeSwiftSkipCondition {
  static let apiLessThan = 1
  static let apiGreaterThan = 2
  static let apiEqualTo = 3
  static let apiNotEqualTo = 4
  static let profileIncludes = 5
  static let profileExcludes = 6

  /// The library API level a `SKIP` condition is compared against: AndroidX's current player
  /// baseline, `Skip.ts`'s default `sLibraryApiLevel`.
  static let libraryAPILevel = 7
  /// The profile bits a `SKIP` condition is compared against: `Skip.ts`'s default `sProfile`.
  static let profile = 0
}

/// `ATTRIBUTE_TIME`'s type field: every value AndroidX's `TimeAttribute` defines. 13 is unassigned.
public enum NativeSwiftTimeAttributeType {
  public static let fromNowSeconds = 0
  public static let fromNowMinutes = 1
  public static let fromNowHours = 2
  public static let fromArgumentSeconds = 3
  public static let fromArgumentMinutes = 4
  public static let fromArgumentHours = 5
  public static let second = 6
  public static let minute = 7
  public static let hour = 8
  public static let dayOfMonth = 9
  public static let monthValue = 10
  public static let dayOfWeek = 11
  public static let year = 12
  public static let fromLoadSeconds = 14
  public static let dayOfYear = 15
}

/// The DrawTextRun `end` value AndroidX reads as "to the end of the text".
let nativeSwiftDrawTextRunEndOfText = -1

/// `RcDimensionType`: the width/height modifier types, as AndroidX writes them. Shared by the core
/// and both native renderers so none of them infers the wire meaning from a literal.
public enum NativeSwiftDimensionType {
  public static let exact = 0
  public static let fill = 1
  public static let wrap = 2
  public static let weight = 3
  public static let intrinsicMin = 4
  public static let intrinsicMax = 5
  public static let exactDp = 6
  public static let fillParentMaxWidth = 7
  public static let fillParentMaxHeight = 8

  /// The types whose value is a fill fraction.
  public static func isFill(_ type: Int) -> Bool {
    type == fill || type == fillParentMaxWidth || type == fillParentMaxHeight
  }
}

/// The LOOM id tiers, from AndroidX `RemapContext` (the vendored
/// `third_party/remote-compose-player` `Utils.ts` `isSystemGlobal` / `isMacroLocal`). System
/// globals keep their meaning inside a macro; macro-local ids are always made unique per expansion.
enum NativeSwiftLoomID {
  static let lastSystemGlobal = 41
  static let firstMacroLocal = 0x4000
  static let lastMacroLocal = 0x4fff
}

/// Opcode groups that share one fixed payload shape inside a LOOM macro body. The capture walk and
/// the parameter-remapping walk both switch on them, so each group is spelled once here.
struct NativeSwiftOpcodeGroup {
  let opcodes: Set<Int>

  init(_ opcodes: Set<Int>) { self.opcodes = opcodes }

  static func ~= (group: NativeSwiftOpcodeGroup, opcode: Int) -> Bool {
    group.opcodes.contains(opcode)
  }

  /// Draws whose payload is four float words: clip rectangle, rectangle, line, oval.
  static let fourWordDraws = NativeSwiftOpcodeGroup([
    NativeSwiftWireOpcode.clipRect, NativeSwiftWireOpcode.drawRect,
    NativeSwiftWireOpcode.drawLine, NativeSwiftWireOpcode.drawOval,
  ])
  /// Draws whose payload is six float words: rounded rectangle, sector, arc.
  static let sixWordDraws = NativeSwiftOpcodeGroup([
    NativeSwiftWireOpcode.drawRoundRect, NativeSwiftWireOpcode.drawSector,
    NativeSwiftWireOpcode.drawArc,
  ])
  /// The payload-free matrix stack operations.
  static let matrixStack = NativeSwiftOpcodeGroup([
    NativeSwiftWireOpcode.matrixSave, NativeSwiftWireOpcode.matrixRestore,
  ])
}

/// The kind of a decoded draw command (`ParsedDrawCommand.kind`, `NativeSwiftDrawCommandSnapshot.kind`).
/// These are the core's own command kinds, not wire opcodes: every kind the core emits is listed,
/// and the renderers switch on these names. 8 and 9 are unassigned.
public enum NativeSwiftDrawKind {
  public static let matrixSave = 0
  public static let matrixRestore = 1
  public static let matrixTranslate = 2
  public static let matrixScale = 3
  public static let matrixRotate = 4
  public static let matrixSkew = 5
  public static let clipRect = 6
  public static let clipPath = 7
  public static let rect = 10
  public static let oval = 11
  public static let circle = 12
  public static let line = 13
  public static let roundRect = 14
  public static let arc = 15
  public static let sector = 16
  public static let text = 17
  public static let path = 18
  public static let bitmap = 19
  public static let textOnPath = 20
  public static let textOnCircle = 21
}

/// `RcPathCommands`: the NaN-boxed verbs of a `PATH_DATA` word stream, and the kinds of
/// `NativeSwiftPathElementSnapshot`.
public enum NativeSwiftPathVerb {
  public static let move = 10
  public static let line = 11
  public static let quadratic = 12
  public static let conic = 13
  public static let cubic = 14
  public static let close = 15
  public static let done = 16
  public static let reset = 17
}

/// AndroidX `AnimatedFloatExpression` operators, as offsets from its NaN-boxed operator base.
/// 64...69 are unassigned.
enum NativeSwiftFloatOperator {
  static let add = 1
  static let sub = 2
  static let mul = 3
  static let div = 4
  static let mod = 5
  static let min = 6
  static let max = 7
  static let pow = 8
  static let sqrt = 9
  static let abs = 10
  static let sign = 11
  static let copySign = 12
  static let exp = 13
  static let floor = 14
  static let log = 15
  static let ln = 16
  static let round = 17
  static let sin = 18
  static let cos = 19
  static let tan = 20
  static let asin = 21
  static let acos = 22
  static let atan = 23
  static let atan2 = 24
  static let mad = 25
  static let ifElse = 26
  static let clamp = 27
  static let cbrt = 28
  static let deg = 29
  static let rad = 30
  static let ceil = 31
  static let arrayDeref = 32
  static let arrayMax = 33
  static let arrayMin = 34
  static let arraySum = 35
  static let arrayAverage = 36
  static let arrayLength = 37
  static let arraySpline = 38
  static let rand = 39
  static let randSeed = 40
  static let noiseFrom = 41
  static let randInRange = 42
  static let squareSum = 43
  static let step = 44
  static let square = 45
  static let dup = 46
  static let hypot = 47
  static let swap = 48
  static let lerp = 49
  static let smoothStep = 50
  static let log2 = 51
  static let inv = 52
  static let fract = 53
  static let pingPong = 54
  static let nop = 55
  static let storeR0 = 56
  static let storeR1 = 57
  static let storeR2 = 58
  static let storeR3 = 59
  static let loadR0 = 60
  static let loadR1 = 61
  static let loadR2 = 62
  static let loadR3 = 63
  static let var1 = 70
  static let var2 = 71
  static let var3 = 72
  static let changeSign = 73
  static let cubic = 74
  static let arraySplineLoop = 75
  static let arraySumTill = 76
  static let arraySumXY = 77
  static let arraySumSquares = 78
  static let arrayLerp = 79
  /// The highest operator AndroidX assigns; payloads above it are references.
  static let last = arrayLerp
}

/// `RcIntegerExpression`'s operators, as offsets from `offset`.
enum NativeSwiftIntegerOperator {
  static let offset = 65_536
  static let add = 1
  static let sub = 2
  static let mul = 3
  static let div = 4
  static let mod = 5
  static let shl = 6
  static let shr = 7
  static let ushr = 8
  static let or = 9
  static let and = 10
  static let xor = 11
  static let copySign = 12
  static let min = 13
  static let max = 14
  static let neg = 15
  static let abs = 16
  static let incr = 17
  static let decr = 18
  static let not = 19
  static let sign = 20
  static let clamp = 21
  static let ifElse = 22
  static let mad = 23
  static let var1 = 24
  static let var2 = 25
  static let var3 = 26
}

/// AndroidX `MatrixOperations` operators, as offsets from its NaN-boxed operator base.
enum NativeSwiftMatrixOperator {
  static let identity = 1
  static let rotateX = 2
  static let rotateY = 3
  static let rotateZ = 4
  static let translateX = 5
  static let translateY = 6
  static let translateZ = 7
  static let translate2 = 8
  static let translate3 = 9
  static let scaleX = 10
  static let scaleY = 11
  static let scaleZ = 12
  static let scale2 = 13
  static let scale3 = 14
  static let mul = 15
  static let rotatePivotZ = 16
  static let rotateAxis = 17
  static let projection = 18
  /// The end of the range `MatrixOperations` reserves for operators (`LAST_OP`).
  static let last = 54
}

/// `RcHeader`'s modern property-map keys: the low 10 bits of a property tag. Every key AndroidX's
/// `Header` defines (the vendored `third_party/remote-compose-player` `Header.ts`); the core reads
/// only the size, density and density-behaviour keys and reads past the rest by their type.
enum NativeSwiftHeaderKey {
  static let documentWidth = 5
  static let documentHeight = 6
  static let densityAtGeneration = 7
  static let desiredFPS = 8
  static let contentDescription = 9
  static let source = 11
  static let dataUpdate = 12
  static let hostExceptionHandler = 13
  static let profiles = 14
  static let featurePaintMeasure = 15
  static let debug = 16
  static let featureMeasureVersion = 17
  static let featureTouchVersion = 18
  static let densityBehavior = 27
}

/// The magic a modern header ORs into the high half of its major-version word
/// (`Header.MAGIC_NUMBER`).
let nativeSwiftHeaderMagic = 0x048c_0000

/// The type field (`tag >> 10`) of a modern header property.
enum NativeSwiftHeaderValueType {
  static let int = 0
  static let float = 1
  static let long = 2
  static let string = 3
}

/// `RcHeader`'s density behaviours.
enum NativeSwiftDensityBehavior {
  static let legacy = 0
  static let pixels = 1
  static let dp = 2
}

/// `RcCustomProperty`'s data types.
public enum NativeSwiftCustomPropertyType {
  public static let intProperty = 0
  public static let floatProperty = 1
  public static let stringProperty = 2
  public static let floatReturn = 3
  public static let textReturn = 4
  public static let intReturn = 5
  public static let colorReturn = 6
  public static let colorIDProperty = 7
  public static let colorProperty = 8
  public static let intIDProperty = 9
}

/// `RcGraphicsLayerModifier`'s attribute ids: the low 10 bits of each attribute tag.
enum NativeSwiftGraphicsLayerAttribute {
  static let scaleX = 0
  static let scaleY = 1
  static let rotationX = 2
  static let rotationY = 3
  static let rotationZ = 4
  static let transformOriginX = 5
  static let transformOriginY = 6
  static let translationX = 7
  static let translationY = 8
  static let translationZ = 9
  static let shadowElevation = 10
  static let alpha = 11
  static let cameraDistance = 12
  static let compositingStrategy = 13
  static let spotShadowColor = 14
  static let ambientShadowColor = 15
  static let hasBlur = 16
  static let blurRadiusX = 17
  static let blurRadiusY = 18
  static let blurTileMode = 19
  static let shape = 20
  static let shapeRadius = 21
  static let attributeCount = 22
}

/// The value type of a graphics-layer attribute: bits 10-11 of its tag. Every type
/// `rc-player-protocol`'s `GraphicsLayerModifierCodec` accepts (AndroidX
/// `GraphicsLayerModifierOperation`); the core reads any other type as an int, as the vendored
/// `third_party/remote-compose-player` player does.
enum NativeSwiftGraphicsLayerValueType {
  static let int = 0
  static let float = 1
}

/// `MODIFIER_MULTI_CLICK`'s click types: every value AndroidX's `MultiClickModifier` defines
/// (`RcMultiClickType`, and the vendored `third_party/remote-compose-player` `MultiClickModifier`).
enum NativeSwiftMultiClickType {
  static let single = 0
  static let long = 1
  static let double = 2
}

/// `ACCESSIBILITY_SEMANTICS`' roles: every value AndroidX's `CoreSemantics` defines
/// (`RcAccessibilitySemantics.ROLE_*`).
enum NativeSwiftAccessibilityRole {
  static let button = 0
  static let checkbox = 1
  static let switchRole = 2
  static let radioButton = 3
  static let tab = 4
  static let image = 5
  static let dropdownList = 6
  static let picker = 7
  static let carousel = 8
  static let unknown = 9
  /// Not a wire value: what the core records for a role outside `button...unknown` (the catalog
  /// writes 255 for "no role"), and the unspecified role the native hosts fall back to.
  static let unspecified = -1
}

/// `ACCESSIBILITY_SEMANTICS`' merge modes: every value AndroidX's `CoreSemantics` defines
/// (`RcAccessibilitySemantics.MODE_*`).
enum NativeSwiftAccessibilityMode {
  static let set = 0
  static let clearAndSet = 1
  static let merge = 2
}

/// AndroidX `Component.Visibility`: the plain states, and the override bits that apply above 15.
public enum NativeSwiftVisibility {
  public static let gone = 0
  public static let visible = 1
  public static let invisible = 2
  public static let overrideGone = 16
  public static let overrideVisible = 32
  public static let overrideInvisible = 64
  public static let clearOverride = 128
}

/// AndroidX `LayoutManager` positioning values, shared by the box, row, column, flow, fit-box,
/// state and collapsible layouts. The `space*` distributions apply on the row/column main axis.
public enum NativeSwiftPositioning {
  public static let start = 1
  public static let center = 2
  public static let end = 3
  public static let top = 4
  public static let bottom = 5
  public static let spaceBetween = 6
  public static let spaceEvenly = 7
  public static let spaceAround = 8
}

/// `RcLayoutAnimation`: AndroidX `AnimationSpec.ANIMATION`, a layout's enter and exit animation.
public enum NativeSwiftLayoutAnimation {
  public static let fadeIn = 0
  public static let fadeOut = 1
  public static let slideLeft = 2
  public static let slideRight = 3
  public static let slideTop = 4
  public static let slideBottom = 5
  public static let rotate = 6
  public static let particle = 7
}

/// The winding of a `PATH_DATA` path, the top byte of its id word. AndroidX fills even-odd for 1
/// and non-zero otherwise.
public enum NativeSwiftPathWinding {
  public static let nonZero = 0
  public static let evenOdd = 1
}

/// `RcDrawTextAnchored`'s flag bits.
public enum NativeSwiftDrawTextAnchoredFlag {
  public static let textRTL = 1
  public static let monospaceMeasure = 2
  public static let measureEveryTime = 4
  public static let baselineRelative = 8
}

/// `RcBitmapData`'s encodings, from AndroidX `BitmapData`. Only inline data is decodable offline.
public enum NativeSwiftBitmapEncoding {
  // `ENCODING_INLINE` is the only encoding the authoritative wire implementations in this
  // repository name (`RcModel.kt`, the vendored players); add others from those sources only.
  public static let inline = 0
}

/// `RcTextLayout`'s text alignment values.
public enum NativeSwiftTextAlignment {
  public static let left = 1
  public static let right = 2
  public static let center = 3
  public static let justify = 4
  public static let start = 5
  public static let end = 6
}

/// `RcTextLayout`'s text overflow values.
public enum NativeSwiftTextOverflow {
  public static let clip = 1
  public static let visible = 2
  public static let ellipsis = 3
  public static let startEllipsis = 4
  public static let middleEllipsis = 5
}

/// The property ids of a `CoreText` / `TextStyle` parameter list, from AndroidX `CoreText`.
enum NativeSwiftTextProperty {
  static let componentID = 1
  static let animationID = 2
  static let color = 3
  static let colorID = 4
  static let fontSize = 5
  static let fontStyle = 6
  static let fontWeight = 7
  static let fontFamily = 8
  static let textAlign = 9
  static let overflow = 10
  static let maxLines = 11
  static let letterSpacing = 12
  static let lineHeightAdd = 13
  static let lineHeightMultiplier = 14
  static let breakStrategy = 15
  static let hyphenationFrequency = 16
  static let justificationMode = 17
  static let underline = 18
  static let strikethrough = 19
  static let fontAxis = 20
  static let fontAxisValues = 21
  static let autosize = 22
  static let flags = 23
  static let textStyleID = 24
  static let minFontSize = 25
  static let maxFontSize = 26
}

/// `RcIdMap`'s entry types.
enum NativeSwiftDataMapType {
  static let string = 0
  static let int = 1
  static let float = 2
  static let long = 3
  static let boolean = 4
}

/// `RcNamedVariable`'s types.
enum NativeSwiftNamedVariableType {
  static let string = 0
  static let float = 1
  static let color = 2
  static let image = 3
  static let int = 4
  static let long = 5
  static let floatArray = 6
}

/// `RcComponentValue`'s types.
enum NativeSwiftComponentValueType {
  static let width = 0
  static let height = 1
  static let localX = 2
  static let localY = 3
  static let rootX = 4
  static let rootY = 5
  static let contentWidth = 6
  static let contentHeight = 7
}

/// `RcConditionalOperations`' comparison types.
public enum NativeSwiftConditionalType {
  public static let equal = 0
  public static let notEqual = 1
  public static let lessThan = 2
  public static let lessThanOrEqual = 3
  public static let greaterThan = 4
  public static let greaterThanOrEqual = 5
  public static let changed = 6
}

/// `RcHostNamedActionValue`'s value types.
enum NativeSwiftHostActionValueType {
  static let none = -1
  static let float = 0
  static let integer = 1
  static let string = 2
  static let floatArray = 3
}

/// `RcColorAttribute`'s channel types.
enum NativeSwiftColorAttributeType {
  static let hue = 0
  static let saturation = 1
  static let brightness = 2
  static let red = 3
  static let green = 4
  static let blue = 5
  static let alpha = 6
}

/// `RcColorExpression`'s modes: the low byte of its mode-and-alpha word.
enum NativeSwiftColorExpressionMode {
  static let colorColorInterpolate = 0
  static let idColorInterpolate = 1
  static let colorIDInterpolate = 2
  static let idIDInterpolate = 3
  static let hsv = 4
  static let argb = 5
  static let idARGB = 6
}

/// `RcImageAttribute`'s types.
enum NativeSwiftImageAttributeType {
  static let width = 0
  static let height = 1
}

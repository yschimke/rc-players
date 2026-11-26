/// Every `.rc` operation code, named after its AndroidX `Operations` constant.
///
/// Generated from `rc-player/protocol/src/main/rc-operations.manifest`, the repository's opcode
/// inventory, so the table is complete rather than only what this writer emits: AGENTS.md's rule
/// for native wire constants. Regenerate it from the manifest when the manifest changes.
enum RemoteOpcode {
  static let header = 0
  static let componentStart = 2
  static let loadBitmap = 4
  static let animationSpec = 14
  static let modifierWidth = 16
  static let clipPath = 38
  static let clipRect = 39
  static let paintValues = 40
  static let binaryData = 41
  static let drawRect = 42
  static let drawTextRun = 43
  static let drawBitmap = 44
  static let dataShader = 45
  static let drawCircle = 46
  static let drawLine = 47
  static let drawBitmapFontTextRun = 48
  static let drawBitmapFontTextRunOnPath = 49
  static let drawRoundRect = 51
  static let drawSector = 52
  static let drawTextOnPath = 53
  static let modifierRoundedClipRect = 54
  static let modifierBackground = 55
  static let drawOval = 56
  static let drawTextOnCircle = 57
  static let modifierPadding = 58
  static let modifierClick = 59
  static let theme = 63
  static let clickArea = 64
  static let rootContentBehavior = 65
  static let drawBitmapInt = 66
  static let modifierHeight = 67
  static let dataFloat = 80
  static let animatedFloat = 81
  static let modifierMultiClick = 83
  static let layoutCustom = 93
  static let modifierEventHandler = 100
  static let dataBitmap = 101
  static let dataText = 102
  static let rootContentDescription = 103
  static let addMesh2d = 104
  static let drawMesh2d = 105
  static let matrixFromMesh2d = 106
  static let modifierBorder = 107
  static let modifierClipRect = 108
  static let defineMesh3d = 110
  static let setCamera3d = 111
  static let matrix3dOp = 112
  static let drawMesh3d = 113
  static let paint3dState = 114
  static let setLights3d = 115
  static let vectorExpression = 116
  static let meshExpression3d = 117
  static let setTexture3d = 118
  static let meshPrimitive3d = 120
  static let layoutRcDoc = 121
  static let mapRcDocVariables = 122
  static let dataPath = 123
  static let drawPath = 124
  static let drawTweenPath = 125
  static let matrixScale = 126
  static let matrixTranslate = 127
  static let matrixSkew = 128
  static let matrixRotate = 129
  static let matrixSave = 130
  static let matrixRestore = 131
  static let matrixSet = 132
  static let drawTextAnchor = 133
  static let colorExpressions = 134
  static let textFromFloat = 135
  static let textMerge = 136
  static let namedVariable = 137
  static let colorConstant = 138
  static let drawContent = 139
  static let dataInt = 140
  static let playSound = 141
  static let referencedOperations = 142
  static let dataBoolean = 143
  static let integerExpression = 144
  static let idMap = 145
  static let idList = 146
  static let floatList = 147
  static let dataLong = 148
  static let drawBitmapScaled = 149
  static let componentValue = 150
  static let textLookup = 151
  static let drawArc = 152
  static let textLookupInt = 153
  static let dataMapLookup = 154
  static let textMeasure = 155
  static let textLength = 156
  static let touchExpression = 157
  static let pathTween = 158
  static let pathCreate = 159
  static let pathAdd = 160
  static let particleDefine = 161
  static let particleProcess = 162
  static let particleLoop = 163
  static let impulseStart = 164
  static let impulseProcess = 165
  static let functionCall = 166
  static let dataBitmapFont = 167
  static let functionDefine = 168
  static let dataSound = 169
  static let attributeText = 170
  static let attributeImage = 171
  static let attributeTime = 172
  static let canvasOperations = 173
  static let modifierDrawContent = 174
  static let pathCombine = 175
  static let layoutFitBox = 176
  static let hapticFeedback = 177
  static let conditionalOperations = 178
  static let debugMessage = 179
  static let attributeColor = 180
  static let matrixFromPath = 181
  static let textSubtext = 182
  static let bitmapTextMeasure = 183
  static let drawBitmapTextAnchored = 184
  static let rem = 185
  static let matrixConstant = 186
  static let matrixExpression = 187
  static let matrixVectorMath = 188
  static let dataFont = 189
  static let drawToBitmap = 190
  static let wakeIn = 191
  static let idLookup = 192
  static let pathExpression = 193
  static let particleCompare = 194
  static let update = 195
  static let colorTheme = 196
  static let dynamicFloatList = 197
  static let updateDynamicFloatList = 198
  static let textTransform = 199
  static let layoutRoot = 200
  static let layoutContent = 201
  static let layoutBox = 202
  static let layoutRow = 203
  static let layoutColumn = 204
  static let layoutCanvas = 205
  static let soundExpression = 206
  static let layoutCanvasContent = 207
  static let layoutText = 208
  static let hostAction = 209
  static let hostNamedAction = 210
  static let modifierVisibility = 211
  static let valueIntegerChangeAction = 212
  static let valueStringChangeAction = 213
  static let containerEnd = 214
  static let loopStart = 215
  static let hostMetadataAction = 216
  static let layoutState = 217
  static let valueIntegerExpressionChangeAction = 218
  static let modifierTouchDown = 219
  static let modifierTouchUp = 220
  static let modifierOffset = 221
  static let valueFloatChangeAction = 222
  static let modifierZindex = 223
  static let modifierGraphicsLayer = 224
  static let modifierTouchCancel = 225
  static let modifierScroll = 226
  static let valueFloatExpressionChangeAction = 227
  static let modifierMarquee = 228
  static let modifierRipple = 229
  static let layoutCollapsibleRow = 230
  static let modifierWidthIn = 231
  static let modifierHeightIn = 232
  static let layoutCollapsibleColumn = 233
  static let layoutImage = 234
  static let modifierCollapsiblePriority = 235
  static let runAction = 236
  static let modifierAlignBy = 237
  static let layoutCompute = 238
  static let coreText = 239
  static let layoutFlow = 240
  static let skip = 241
  static let textStyle = 242
  static let modifierDimensionConstraints = 243
  static let macroForEach = 244
  static let includeReferencedOperations = 245
  static let macroDefine = 246
  static let macroCall = 247
  static let macroArgument = 248
  static let macroBlock = 249
  static let accessibilitySemantics = 250
  static let extensionRangeReserved4 = 251
  static let extensionRangeReserved3 = 252
  static let extensionRangeReserved2 = 253
  static let extensionRangeReserved1 = 254
  static let extendedOpcode = 255
}

/// `NamedVariable`'s `varType` (`NamedVariable.*_TYPE`).
enum RemoteNamedVariableType {
  static let string = 0
  static let float = 1
  static let color = 2
  static let image = 3
  static let int = 4
  static let long = 5
  /// `FLOAT_ARRAY_TYPE`; AndroidX's path variables share the value.
  static let floatArray = 6
}

/// `HostNamedAction`'s value `type`.
enum RemoteHostActionValueType {
  static let none = -1
  static let float = 0
  static let int = 1
  static let string = 2
  static let floatArray = 3
}

/// Width and height modifier `type` (`DimensionModifierOperation.Type`).
enum RemoteDimensionType {
  static let exact = 0
  static let fill = 1
  static let wrap = 2
  static let weight = 3
  static let intrinsicMin = 4
  static let intrinsicMax = 5
  static let exactDp = 6
  static let fillParentMaxWidth = 7
  static let fillParentMaxHeight = 8
}

/// Modern header property keys (`Header.DOC_*` and the feature tags).
enum RemoteHeaderKey {
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

/// The type field (`tag >> 10`) of a modern header property.
enum RemoteHeaderValueType {
  static let int = 0
  static let float = 1
  static let long = 2
  static let string = 3
}

/// The version words of a modern header.
enum RemoteHeaderVersion {
  /// `Header.MAGIC_NUMBER`, OR'd into the major version.
  static let magic = 0x048C_0000
  static let major = 1
  static let minor = 1
  static let patch = 0
}

/// `PaintBundle` tags, and the values of its `Style` command.
enum RemotePaintTag {
  static let textSize = 1
  static let color = 4
  static let strokeWidth = 5
  static let strokeMiter = 6
  static let strokeCap = 7
  static let style = 8
  static let shader = 9
  static let imageFilterQuality = 10
  static let gradient = 11
  static let alpha = 12
  static let colorFilter = 13
  static let antiAlias = 14
  static let strokeJoin = 15
  static let typeface = 16
  static let filterBitmap = 17
  static let blendMode = 18
  static let colorID = 19
  static let colorFilterID = 20
  static let clearColorFilter = 21
  static let shaderMatrix = 22
  static let fontAxis = 23
  static let texture = 24
  static let pathEffect = 25
  static let fallbackTypeface = 26
  static let styleFill = 0
  static let styleStroke = 1
  static let styleFillAndStroke = 2
}

/// `AnimatedFloatExpression`'s operators, as offsets from ``RemoteNaN/operatorOffset``: every
/// operator AndroidX defines, as the native player's `NativeSwiftFloatOperator` lists them.
enum RemoteFloatOperatorCode {
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
  static let cmd1 = 64
  static let cmd2 = 65
  static let cmd3 = 66
  static let cmd4 = 67
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
}

/// `TextFromFloat`'s formatting flags (`TextFromFloat.*`).
enum RemoteTextFromFloatFlag {
  static let padAfterSpace = 0
  static let padAfterNone = 1
  static let padAfterZero = 3
  static let padPreSpace = 0
  static let padPreNone = 4
  static let padPreZero = 12
  static let groupingNone = 0
  static let groupingBy3 = 16
  static let groupingBy4 = 32
  static let groupingBy32 = 48
  static let separatorCommaPeriod = 0
  static let separatorPeriodComma = 64
  static let separatorSpaceComma = 128
  static let separatorUnderPeriod = 192
  static let optionsNegativeParentheses = 256
  static let optionsRounding = 512
  static let legacyMode = 1024
  static let fullFormat = 4096
}

/// `TextLayout`'s alignment flag: the colour word is a colour id.
let remoteTextDynamicColorFlag = 1 << 16

/// `AnimatedFloatExpression`'s operator offset and the NaN boxing every id reference uses
/// (`Utils.asNan`).
enum RemoteNaN {
  static let mask: UInt32 = 0xFF80_0000
  static let operatorOffset = 0x0031_0000

  static func id(_ id: Int) -> Float { Float(bitPattern: mask | UInt32(id)) }
  static func operation(_ code: Int) -> Float {
    Float(bitPattern: mask | UInt32(operatorOffset + code))
  }
}

/// Big-endian `.rc` bytes, as AndroidX's `WireBuffer` writes them.
struct RemoteByteBuffer {
  private(set) var bytes: [UInt8] = []

  mutating func byte(_ value: Int) { bytes.append(UInt8(truncatingIfNeeded: value)) }

  mutating func short(_ value: Int) {
    let raw = UInt16(truncatingIfNeeded: value)
    bytes.append(UInt8(truncatingIfNeeded: raw >> 8))
    bytes.append(UInt8(truncatingIfNeeded: raw))
  }

  mutating func int(_ value: Int) {
    let raw = UInt32(bitPattern: Int32(truncatingIfNeeded: value))
    for shift: UInt32 in [24, 16, 8, 0] { bytes.append(UInt8(truncatingIfNeeded: raw >> shift)) }
  }

  mutating func float(_ value: Float) { int(Int(Int32(bitPattern: value.bitPattern))) }

  mutating func utf8(_ value: String) {
    let encoded = Array(value.utf8)
    int(encoded.count)
    bytes.append(contentsOf: encoded)
  }

  mutating func append(_ other: RemoteByteBuffer) { bytes.append(contentsOf: other.bytes) }
}

/// Background and border modifier `shapeType`.
enum RemoteModifierShape {
  static let rectangle = 0
  static let circle = 1
}

/// Background and border modifier `flags`.
enum RemoteModifierColorFlag {
  /// The colour is the id in `colorId`, not the four channel floats.
  static let colorID = 2
}

/// `BorderModifierOperation`'s wire versions (its `reserve1` word).
enum RemoteBorderWireVersion {
  /// The legacy stroke, centred on the bounds.
  static let centred = 0
  /// The stroke drawn inside the bounds, as AndroidX writes it from API level 8.
  static let inside = 1
}

/// `TextLayout`'s `fontStyle`.
enum RemoteFontStyle {
  static let normal = 0
  static let italic = 1
}

/// The "none" value of an id field: no component id requested, no animation, the default font.
let remoteNoID = -1

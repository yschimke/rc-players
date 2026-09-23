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

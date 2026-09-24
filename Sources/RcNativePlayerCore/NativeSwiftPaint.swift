import Foundation

/// A paint gradient held as it arrived on the wire, so its colours and coordinates resolve at the
/// same point as every other draw value. Colour words are literal ARGB unless its bit is set in
/// `colorRegister`, in which case they are colour IDs to look up.
struct ParsedGradient {
  var kind: Int
  var colorWords: [Int]
  var colorRegister: Int
  var stopWords: [UInt32]
  var coordinateWords: [UInt32]
  var tileMode: Int
}

/// Command types in a `PAINT_DATA` bundle, which are separate from document operation opcodes.
enum NativeSwiftPaintCommand {
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
}

enum NativeSwiftPaintStyle {
  static let fill = 0
  static let stroke = 1
  static let fillAndStroke = 2
}

enum NativeSwiftPaintGradientKind {
  static let linear = 0
  static let radial = 1
  static let sweep = 2
}

enum NativeSwiftPaintFilterQuality {
  static let none = 0
  static let low = 1
  static let medium = 2
  static let high = 3
}

struct ParsedPaint {
  var colorARGB: UInt32 = 0xff00_0000
  var colorID: Int?
  var colorFilterARGB: UInt32?
  var colorFilterID: Int?
  var colorFilterMode: NativeSwiftPaintBlendMode?
  var gradient: ParsedGradient?
  var alpha: Float = 1
  var strokeWidth: UInt32 = Float(1).bitPattern
  var isStroke = false
  var strokeCap = NativeSwiftStrokeCap.butt
  var strokeJoin = NativeSwiftStrokeJoin.miter
  var blendMode = NativeSwiftPaintBlendMode.sourceOver
  var textureImageID: Int?
  var textureTileModeX = 0
  var textureTileModeY = 0
  /// The `MatrixAccess` id a `SHADER_MATRIX` field named, or nil when the field was cleared or
  /// never set. Applies to whichever shader the paint currently carries.
  var shaderMatrixID: Int?
  /// How an image or texture is sampled: 0 none, 1 low, 2 medium, 3 high. Nil when the paint never
  /// said, which leaves the renderer's default in place.
  var filterQuality: Int?
  var textSize: UInt32 = Float(16).bitPattern
}

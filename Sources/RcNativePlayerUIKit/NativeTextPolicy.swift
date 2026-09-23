import Foundation
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

enum NativeTextDirection {
  case leftToRight
  case rightToLeft
}

enum NativeResolvedTextAlignment: Equatable {
  case left
  case right
  case center
  case justified
}

enum NativeResolvedTextOverflow: Equatable {
  case clip
  case visible
  case tail
  case head
  case middle
}

enum NativeResolvedLineBreak: Equatable {
  case clip
  case wordWrap
  case tail
  case head
  case middle
}

enum NativeTextPolicy {
  static func alignment(
    value: Int, justified: Bool, direction: NativeTextDirection
  ) -> NativeResolvedTextAlignment {
    if justified { return .justified }
    switch value {
    case NativeSwiftTextAlignment.left: return .left
    case NativeSwiftTextAlignment.right: return .right
    case NativeSwiftTextAlignment.center: return .center
    case NativeSwiftTextAlignment.end: return direction == .rightToLeft ? .left : .right
    default: return direction == .rightToLeft ? .right : .left
    }
  }

  static func overflow(_ value: Int) -> NativeResolvedTextOverflow {
    switch value {
    case NativeSwiftTextOverflow.visible: return .visible
    case NativeSwiftTextOverflow.ellipsis: return .tail
    case NativeSwiftTextOverflow.startEllipsis: return .head
    case NativeSwiftTextOverflow.middleEllipsis: return .middle
    default: return .clip
    }
  }

  static func numberOfLines(overflow: Int, maximum: Int) -> Int {
    maximum > 1 && (overflow == NativeSwiftTextOverflow.clip || overflow == NativeSwiftTextOverflow.visible)
      ? 0 : max(maximum, 1)
  }

  static func lineBreak(overflow: Int) -> NativeResolvedLineBreak {
    switch self.overflow(overflow) {
    case .clip: return .clip
    case .visible: return .wordWrap
    case .tail: return .tail
    case .head: return .head
    case .middle: return .middle
    }
  }
}

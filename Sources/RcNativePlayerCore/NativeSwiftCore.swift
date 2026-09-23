import Foundation

/// `Int(Float)` traps on NaN, infinity and anything beyond `Int`'s range, and a document is free to
/// compute any of those. NaN maps to 0 and everything else saturates, so a malformed value degrades
/// one frame's output instead of crashing the host.
func nativeSwiftClampedInt(_ value: Float) -> Int {
  guard !value.isNaN else { return 0 }
  if value >= Float(Int.max) { return Int.max }
  if value <= Float(Int.min) { return Int.min }
  return Int(value)
}

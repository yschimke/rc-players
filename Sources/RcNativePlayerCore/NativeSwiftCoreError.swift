import Foundation

public enum NativeSwiftCoreError: Error, CustomStringConvertible, LocalizedError {
  case unsupported(opcode: Int, offset: Int, reason: String)
  case malformed(offset: Int, reason: String)

  public var isUnsupported: Bool {
    if case .unsupported = self { return true }
    return false
  }

  /// `LocalizedError`, not just `CustomStringConvertible`, because callers reach this through
  /// `localizedDescription`. Without the conformance that goes through the `NSError` bridge and
  /// renders as "The operation couldn't be completed. (…NativeSwiftCoreError error 1.)", which is
  /// what a host puts in front of a user when a *frame* fails to resolve — the document-open path
  /// happens to downcast and read `description`, the per-frame path does not.
  public var errorDescription: String? { description }

  public var description: String {
    switch self {
    case .unsupported(let opcode, let offset, let reason):
      return "Unsupported Remote Compose opcode \(opcode) at byte \(offset): \(reason)"
    case .malformed(let offset, let reason):
      return "Malformed Remote Compose document at byte \(offset): \(reason)"
    }
  }
}

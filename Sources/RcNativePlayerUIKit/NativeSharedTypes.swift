import Foundation

/// The host surface behind an opaque or transparent player. It has no UIKit dependency so AppKit
/// hosts and shared adapters can use the same public configuration vocabulary.
public enum RemoteComposeNativePlayerBackground: Equatable, Sendable {
  case opaque
  case transparent
}

/// A value a host sets on a document's named variable before it renders: the four kinds a `.rc`
/// document declares a `USER:` name with. A boolean travels as `.integer` 0 or 1, as on the wire.
public enum RemoteComposeNativeNamedValue: Equatable, Sendable {
  case text(String)
  case float(Float)
  case integer(Int)
  /// ARGB, as the document stores a color.
  case color(UInt32)
}

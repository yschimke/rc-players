import Foundation

/// The host surface behind an opaque or transparent player. It has no UIKit dependency so AppKit
/// hosts and shared adapters can use the same public configuration vocabulary.
public enum RemoteComposeNativePlayerBackground: Equatable, Sendable {
  case opaque
  case transparent
}

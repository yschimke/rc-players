// Typed host writes: a Swift host that authored its document with RcCreation writes a variable
// through the same `RemoteVariable` the document reads, so the name, the type and an enum's
// position are checked by the compiler rather than spelled as strings.
//
//   enum Vars { static let tab = RemoteVariable("tab", default: Tab.week) }
//   controller.set(Vars.tab, to: .month)   // not setInteger(2, for: "tab")
//
// Opt-in: an app that only plays documents links neither RcCreation nor this.

import RcCreation
import RcNativePlayerCore

#if canImport(RcComposePlayerSwiftUI)
  import RcComposePlayerSwiftUI
#endif
#if canImport(UIKit) && canImport(RcNativePlayerUIKit)
  import RcNativePlayerUIKit
#endif

extension NativeSwiftDocumentSession {
  /// Writes `value` to `variable`, in the slot the document declared for it. Returns whether the
  /// document has the variable and took the value, as the named setters do.
  @discardableResult
  public func set<Value>(_ variable: RemoteVariable<Value>, to value: Value) -> Bool {
    switch value.remoteHostValue {
    case .float(let float): setFloat(float, for: variable.name)
    case .integer(let integer): setInteger(integer, for: variable.name)
    case .string(let string): setString(string, for: variable.name)
    case .color(let argb): setColor(argb, for: variable.name)
    }
  }
}

#if canImport(RcComposePlayerSwiftUI)
  extension RemoteComposePlayerController {
    /// Writes `value` to `variable`, in the slot the document declared for it. Returns whether
    /// the document has the variable and took the value, as the named setters do.
    @discardableResult
    public func set<Value>(_ variable: RemoteVariable<Value>, to value: Value) -> Bool {
      switch value.remoteHostValue {
      case .float(let float): setFloat(float, for: variable.name)
      case .integer(let integer): setInteger(integer, for: variable.name)
      case .string(let string): setString(string, for: variable.name)
      case .color(let argb): setColor(argb, for: variable.name)
      }
    }
  }
#endif

#if canImport(UIKit) && canImport(RcNativePlayerUIKit)
  extension RemoteComposeNativePlayerView {
    /// Writes `value` to `variable`, in the slot the document declared for it; see the named
    /// setters for what the answer means.
    @discardableResult
    public func set<Value>(_ variable: RemoteVariable<Value>, to value: Value) async -> Bool {
      switch value.remoteHostValue {
      case .float(let float): await setFloat(float, for: variable.name)
      case .integer(let integer): await setInteger(integer, for: variable.name)
      case .string(let string): await setString(string, for: variable.name)
      case .color(let argb): await setColor(argb, for: variable.name)
      }
    }
  }

  extension RemoteComposeNativePlayerViewController {
    /// Writes `value` to `variable`, in the slot the document declared for it; see the named
    /// setters for what the answer means.
    @discardableResult
    public func set<Value>(_ variable: RemoteVariable<Value>, to value: Value) async -> Bool {
      switch value.remoteHostValue {
      case .float(let float): await setFloat(float, for: variable.name)
      case .integer(let integer): await setInteger(integer, for: variable.name)
      case .string(let string): await setString(string, for: variable.name)
      case .color(let argb): await setColor(argb, for: variable.name)
      }
    }
  }
#endif

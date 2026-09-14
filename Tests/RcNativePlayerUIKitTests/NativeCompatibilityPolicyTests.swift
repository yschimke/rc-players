import Foundation

@main
enum NativeCompatibilityPolicyTests {
  static func main() {
    let clean = RemoteComposeNativePlayerDiagnostics(
      issues: [], unsupportedOpcodes: [], notes: [])
    let issue = RemoteComposeNativePlayerDiagnostic(
      severity: .unsupported,
      opcode: 124,
      operationName: "DrawPath",
      componentID: 42,
      reason: "Operation is not represented by the native player")
    let partial = RemoteComposeNativePlayerDiagnostics(
      issues: [issue], unsupportedOpcodes: [124], notes: [])

    precondition(
      RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: .compatible, diagnostics: partial))
    precondition(
      RemoteComposeNativeCompatibilityDecision.shouldRender(policy: .strict, diagnostics: clean))
    precondition(
      !RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: .strict, diagnostics: partial))
    precondition(partial.isPartial)
    precondition(!clean.isPartial)

    guard
      case .incompatible(let diagnostics) =
        RemoteComposeNativePlayerError.incompatible(partial)
    else {
      preconditionFailure("incompatible error did not preserve diagnostics")
    }
    precondition(diagnostics == partial)
    print("native UIKit compatibility policy tests: ok")
  }
}

import Foundation
import Testing

@testable import RcNativePlayerUIKit

@Suite struct NativeCompatibilityPolicyTests {
  @Test func compatibilityPolicy() {
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

    #expect(
      RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: .compatible, diagnostics: partial))
    #expect(
      RemoteComposeNativeCompatibilityDecision.shouldRender(policy: .strict, diagnostics: clean))
    #expect(
      !RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: .strict, diagnostics: partial))
    #expect(partial.isPartial)
    #expect(!clean.isPartial)

    guard
      case .incompatible(let diagnostics) =
        RemoteComposeNativePlayerError.incompatible(partial)
    else {
      Issue.record("incompatible error did not preserve diagnostics")
      return
    }
    #expect(diagnostics == partial)
  }
}

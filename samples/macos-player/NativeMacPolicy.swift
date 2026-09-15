import Foundation
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

enum NativeMacCompatibility: String, CaseIterable, Identifiable {
  case compatible
  case strict

  var id: Self { self }
  var title: String { self == .compatible ? "Compatible" : "Strict" }
  var detail: String {
    switch self {
    case .compatible: "Render the supported pure-Swift profile."
    case .strict: "Refuse documents outside the supported pure-Swift profile."
    }
  }

  var nativePolicy: RemoteComposeNativePlayerCompatibilityPolicy {
    self == .compatible ? .compatible : .strict
  }
}

struct NativeMacPolicyReport: Equatable {
  let diagnostics: RemoteComposeNativePlayerDiagnostics
  let budget: NativeFrameBudget
}

enum NativeMacPolicy {
  static let executionLimits = RemoteComposeNativeExecutionLimits.default

  static func validateDocument(_ data: Data) throws {
    try NativeFrameBudget.validate(executionLimits)
    let maximum = min(executionLimits.maximumDocumentBytes, Int(Int32.max))
    guard data.count <= maximum else {
      throw RemoteComposeNativeLimitError.documentTooLarge(actual: data.count, maximum: maximum)
    }
  }

  static func evaluate(
    _ snapshot: NativeSwiftDocumentSnapshot,
    compatibility: NativeMacCompatibility
  ) throws -> NativeMacPolicyReport {
    let budget = try validateSnapshot(snapshot)
    let diagnostics = RemoteComposeNativePlayerDiagnostics(
      issues: [], unsupportedOpcodes: [], notes: [])
    guard
      RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: compatibility.nativePolicy, diagnostics: diagnostics)
    else { throw RemoteComposeNativePlayerError.incompatible(diagnostics) }
    return NativeMacPolicyReport(diagnostics: diagnostics, budget: budget)
  }

  static func validate(events: [NativeSwiftEvent], against report: NativeMacPolicyReport) throws {
    var budget = report.budget
    for event in events {
      switch event {
      case .namedAction(let name, let value):
        let text: String?
        if case .text(let value) = value { text = value } else { text = nil }
        try budget.recordEvent(strings: [name, text], limits: executionLimits)
      }
    }
  }

  private static func validateSnapshot(_ snapshot: NativeSwiftDocumentSnapshot) throws
    -> NativeFrameBudget
  {
    var budget = NativeFrameBudget()
    try budget.validateDocumentDimensions(
      [Double(snapshot.width), Double(snapshot.height)], limits: executionLimits)
    var pending: [(NativeSwiftNodeSnapshot, Int)] = [(snapshot.root, 1)]
    while let (node, depth) = pending.popLast() {
      try budget.recordNode(depth: depth, limits: executionLimits)
      try budget.recordStrings(
        [
          node.text?.value, node.custom?.config, node.accessibility?.contentDescription,
          node.accessibility?.text, node.accessibility?.stateDescription,
        ], limits: executionLimits)
      try budget.validateNumbers(
        [
          node.widthValue, node.heightValue, node.padding.left, node.padding.top,
          node.padding.right, node.padding.bottom, node.minimumHeight, node.cornerRadius,
          node.spacing,
        ].map(Double.init), componentID: node.componentID, field: "layout",
        limits: executionLimits)
      for command in node.commands {
        try budget.recordCommand(pathElementCount: command.path.count, limits: executionLimits)
        try budget.validateNumbers(
          command.values.map(Double.init), componentID: node.componentID, field: "draw geometry",
          limits: executionLimits)
        try budget.validateNumbers(
          [command.alpha, command.strokeWidth].map(Double.init), componentID: node.componentID,
          field: "paint", limits: executionLimits)
        for element in command.path {
          try budget.validateNumbers(
            element.values.map(Double.init), componentID: node.componentID, field: "path",
            limits: executionLimits)
        }
      }
      pending.append(contentsOf: node.children.map { ($0, depth + 1) })
    }
    return budget
  }
}

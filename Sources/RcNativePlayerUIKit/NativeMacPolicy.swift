#if canImport(AppKit) && !targetEnvironment(macCatalyst)
import Foundation
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

public enum NativeMacCompatibility: String, CaseIterable, Identifiable, Sendable {
  case compatible
  case strict

  public var id: Self { self }
  public var title: String { self == .compatible ? "Compatible" : "Strict" }
  public var detail: String {
    switch self {
    case .compatible: "Render the supported pure-Swift profile."
    case .strict: "Refuse documents outside the supported pure-Swift profile."
    }
  }

  var nativePolicy: RemoteComposeNativePlayerCompatibilityPolicy {
    self == .compatible ? .compatible : .strict
  }
}

public struct NativeMacPolicyReport: Equatable, Sendable {
  public let diagnostics: RemoteComposeNativePlayerDiagnostics
  let budget: NativeFrameBudget
}

public enum NativeMacPolicy {
  public static let executionLimits = RemoteComposeNativeExecutionLimits.default

  public static func validateDocument(_ data: Data) throws {
    try NativeFrameBudget.validate(executionLimits)
    let maximum = min(executionLimits.maximumDocumentBytes, Int(Int32.max))
    guard data.count <= maximum else {
      throw RemoteComposeNativeLimitError.documentTooLarge(actual: data.count, maximum: maximum)
    }
  }

  public static func evaluate(
    _ snapshot: NativeSwiftDocumentSnapshot,
    compatibility: NativeMacCompatibility
  ) throws -> NativeMacPolicyReport {
    try validateImages(snapshot.images)
    let budget = try validateSnapshot(snapshot)
    let diagnostics = RemoteComposeNativePlayerDiagnostics(
      issues: [], unsupportedOpcodes: [], notes: [])
    guard
      RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: compatibility.nativePolicy, diagnostics: diagnostics)
    else { throw RemoteComposeNativePlayerError.incompatible(diagnostics) }
    return NativeMacPolicyReport(diagnostics: diagnostics, budget: budget)
  }

  private static func validateImages(_ images: [NativeSwiftImageResourceSnapshot]) throws {
    guard images.count <= 32 else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Too many embedded images")
    }
    var totalBytes = 0
    var ids = Set<Int>()
    for image in images {
      guard ids.insert(image.id).inserted else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Duplicate image \(image.id)")
      }
      guard image.data.count <= 8 * 1_024 * 1_024 else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Image \(image.id) is too large")
      }
      let (nextTotal, overflowed) = totalBytes.addingReportingOverflow(image.data.count)
      guard !overflowed, nextTotal <= 24 * 1_024 * 1_024 else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Embedded images are too large")
      }
      totalBytes = nextTotal
      let (pixels, pixelOverflowed) = image.width.multipliedReportingOverflow(by: image.height)
      guard
        image.width > 0, image.height > 0, image.width <= 4_096, image.height <= 4_096,
        !pixelOverflowed, pixels <= 16_777_216
      else {
        throw NativeSwiftCoreError.malformed(
          offset: 0, reason: "Image \(image.id) dimensions are unsafe")
      }
      guard image.encoding == NativeSwiftBitmapEncoding.inline else {
        throw NativeSwiftCoreError.unsupported(
          opcode: NativeSwiftWireOpcode.dataBitmap, offset: 0,
          reason: "external AppKit image resources")
      }
    }
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
          node.padding.right, node.padding.bottom, node.minimumWidth, node.maximumWidth,
          node.minimumHeight, node.maximumHeight, node.cornerRadius,
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
#endif

import CoreGraphics
import Foundation
import RcComposePlayer

enum NativeMacCompatibility: String, CaseIterable, Identifiable {
  case compatible
  case strict

  var id: Self { self }
  var title: String { self == .compatible ? "Compatible" : "Strict" }
  var detail: String {
    switch self {
    case .compatible:
      "Render the supported subset and report every known difference."
    case .strict:
      "Refuse any frame with unsupported or approximate native behavior."
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
  static let resourceLimits = RemoteComposeNativeResourceLimits.default

  static func validateDocument(_ data: Data) throws {
    try NativeFrameBudget.validate(executionLimits)
    try NativeResourcePolicy.validate(limits: resourceLimits)
    let maximum = min(executionLimits.maximumDocumentBytes, Int(Int32.max))
    guard data.count <= maximum else {
      throw RemoteComposeNativeLimitError.documentTooLarge(actual: data.count, maximum: maximum)
    }
  }

  static func evaluate(
    _ snapshot: RcNativeDocumentSnapshot,
    compatibility: NativeMacCompatibility
  ) throws -> NativeMacPolicyReport {
    var budget = try validateSnapshot(snapshot)
    try validateResources(snapshot)
    let diagnostics = makeDiagnostics(snapshot)
    guard
      RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: compatibility.nativePolicy, diagnostics: diagnostics)
    else { throw RemoteComposeNativePlayerError.incompatible(diagnostics) }
    try budget.recordWork(
      snapshot.images.count + snapshot.fonts.count, limits: executionLimits)
    return NativeMacPolicyReport(diagnostics: diagnostics, budget: budget)
  }

  static func validate(
    events: [RcNativeEvent], against report: NativeMacPolicyReport
  ) throws {
    var budget = report.budget
    for event in events {
      try budget.recordEvent(
        additionalWork: event.floatListValue.count,
        strings: [event.name, event.textValue], limits: executionLimits)
    }
  }

  private static func validateSnapshot(_ snapshot: RcNativeDocumentSnapshot) throws
    -> NativeFrameBudget
  {
    try NativeFrameBudget.validate(executionLimits)
    var budget = NativeFrameBudget()
    try budget.recordStrings(
      snapshot.notes.map(Optional.some)
        + snapshot.diagnostics.flatMap { [Optional($0.operationName), Optional($0.reason)] },
      limits: executionLimits)
    try budget.validateDocumentDimensions(
      [Double(snapshot.width), Double(snapshot.height)], limits: executionLimits)
    try budget.validateNumbers(
      [Double(snapshot.density)], componentID: 0, field: "density", limits: executionLimits)
    guard snapshot.density > 0 else {
      throw RemoteComposeNativePlayerError.decode("Document density must be positive")
    }
    guard snapshot.wakeAfterSeconds.isFinite, snapshot.wakeAfterSeconds >= -1 else {
      throw RemoteComposeNativePlayerError.decode(
        "Scheduled frame delay must be finite and non-negative, or -1 when absent")
    }

    var pending: [(RcNativeNodeSnapshot, Int)] = [(snapshot.root, 1)]
    while let (node, depth) = pending.popLast() {
      let componentID = Int(node.componentId)
      try budget.recordNode(depth: depth, limits: executionLimits)
      try budget.recordStrings(
        [node.semanticLabel, node.semanticText, node.semanticStateDescription],
        limits: executionLimits)
      try budget.recordWork(node.clickActionTypes.count, limits: executionLimits)
      try budget.validateLayoutDimension(
        value: Double(node.widthValue), type: Int(node.widthType), componentID: componentID,
        field: "width", limits: executionLimits)
      try budget.validateLayoutDimension(
        value: Double(node.heightValue), type: Int(node.heightType), componentID: componentID,
        field: "height", limits: executionLimits)
      try budget.validateNumbers(
        [
          node.minimumWidth, node.minimumHeight, max(node.maximumWidth, 0),
          max(node.maximumHeight, 0), node.paddingTop, node.paddingLeft, node.paddingBottom,
          node.paddingRight, node.cornerRadius, node.spacing, node.offsetX, node.offsetY,
        ].map(Double.init), componentID: componentID, field: "layout", limits: executionLimits)
      try budget.validateFinite([Double(node.zIndex)], componentID: componentID, field: "z-index")
      try budget.validateCanvasDimensions(
        [
          abs(Double(node.minimumWidth)), abs(Double(node.minimumHeight)),
          abs(Double(max(node.maximumWidth, 0))), abs(Double(max(node.maximumHeight, 0))),
        ], limits: executionLimits)

      for command in node.commands {
        let gradientWork = command.gradient.map { $0.colors.count + $0.stops.count + 4 } ?? 0
        try budget.recordCommand(
          pathElementCount: command.path.count, additionalWork: gradientWork,
          strings: [
            command.text, command.image?.contentDescription, command.textStyle?.fontFamilyName,
          ],
          limits: executionLimits)
        var geometry = [
          command.first, command.second, command.third, command.fourth, command.fifth,
          command.sixth,
        ]
        if command.kind == 3 {
          if geometry[2].isNaN { geometry[2] = 0 }
          if geometry[3].isNaN { geometry[3] = 0 }
        } else if command.kind == 4 {
          if geometry[1].isNaN { geometry[1] = 0 }
          if geometry[2].isNaN { geometry[2] = 0 }
        }
        try budget.validateNumbers(
          geometry.map(Double.init), componentID: componentID, field: "draw geometry",
          limits: executionLimits)
        try budget.validateNumbers(
          [
            command.alpha, command.strokeWidth, command.textSize, command.textWeight,
            command.textStyle?.letterSpacing ?? 0, command.textStyle?.lineHeightAdd ?? 0,
            command.textStyle?.lineHeightMultiplier ?? 1,
          ].map(Double.init), componentID: componentID, field: "paint", limits: executionLimits)
        switch Int(command.kind) {
        case 6, 10, 11, 13, 14, 15, 16:
          try budget.validateCanvasDimensions(
            [
              abs(Double(geometry[2] - geometry[0])),
              abs(Double(geometry[3] - geometry[1])),
            ], limits: executionLimits)
        case 12:
          try budget.validateCanvasDimensions(
            [abs(Double(geometry[2] * 2))], limits: executionLimits)
        default: break
        }

        for segment in command.path {
          let values = [
            segment.first, segment.second, segment.third, segment.fourth, segment.fifth,
            segment.sixth,
          ]
          try budget.validateNumbers(
            values.map(Double.init), componentID: componentID, field: "path",
            limits: executionLimits)
        }
        if let gradient = command.gradient {
          try budget.validateNumbers(
            [gradient.first, gradient.second, gradient.third, gradient.fourth].map(Double.init),
            componentID: componentID, field: "gradient", limits: executionLimits)
          try budget.validateGradientStops(
            gradient.stops.map { Double(truncating: $0) }, componentID: componentID)
        }
        if let image = command.image {
          try budget.validateNumbers(
            [
              image.sourceLeft, image.sourceTop, image.sourceRight, image.sourceBottom,
              image.destinationLeft, image.destinationTop, image.destinationRight,
              image.destinationBottom,
            ].map(Double.init), componentID: componentID, field: "image", limits: executionLimits)
          try budget.validateFinite(
            [Double(image.scaleFactor)], componentID: componentID, field: "image scale")
          try budget.validateCanvasDimensions(
            [
              abs(Double(image.sourceRight - image.sourceLeft)),
              abs(Double(image.sourceBottom - image.sourceTop)),
              abs(Double(image.destinationRight - image.destinationLeft)),
              abs(Double(image.destinationBottom - image.destinationTop)),
            ], limits: executionLimits)
        }
      }
      pending.append(contentsOf: node.children.map { ($0, depth + 1) })
    }
    return budget
  }

  private static func validateResources(_ snapshot: RcNativeDocumentSnapshot) throws {
    let count = snapshot.images.count + snapshot.fonts.count
    guard count <= resourceLimits.maximumResourceCount else {
      throw RemoteComposeNativeResourceError.tooManyResources(
        actual: count, maximum: resourceLimits.maximumResourceCount)
    }
    try NativeResourcePolicy.validateUniqueIDs(snapshot.images.map { Int($0.id) })
    try NativeResourcePolicy.validateUniqueIDs(snapshot.fonts.map { Int($0.id) })
    var totalBytes = 0
    for image in snapshot.images {
      let data = RcDataBridgeKt.rcData(bytes: image.data) as Data
      try NativeResourcePolicy.validate(
        id: Int(image.id), byteCount: data.count, width: Int(image.width),
        height: Int(image.height), runningTotal: &totalBytes, limits: resourceLimits)
      if image.encoding != 0 {
        _ = try NativeResourcePolicy.reference(from: data, id: Int(image.id))
      }
    }
    for font in snapshot.fonts {
      let data = RcDataBridgeKt.rcData(bytes: font.data) as Data
      try NativeResourcePolicy.validateBytes(
        id: Int(font.id), byteCount: data.count, runningTotal: &totalBytes,
        limits: resourceLimits)
    }
  }

  private static func makeDiagnostics(_ snapshot: RcNativeDocumentSnapshot)
    -> RemoteComposeNativePlayerDiagnostics
  {
    var issues = snapshot.diagnostics.map {
      RemoteComposeNativePlayerDiagnostic(
        severity: $0.severity == 0 ? .warning : .unsupported, opcode: Int($0.opcode),
        operationName: $0.operationName, componentID: Int($0.componentId), reason: $0.reason)
    }
    var pending = [snapshot.root]
    while let node = pending.popLast() {
      if node.kind == 8 {
        issues.append(
          issue(kind: 19, node: node, reason: "AppKit image components are not rendered yet"))
      }
      if node.hasSemantics, !node.clickable {
        issues.append(
          issue(
            kind: -1, node: node, severity: .warning,
            reason: "AppKit exposes semantic buttons, but non-button roles are approximate"))
      }
      for command in node.commands {
        switch command.kind {
        case 5:
          issues.append(
            issue(kind: 5, node: node, reason: "AppKit skew transforms are not rendered"))
        case 7:
          issues.append(issue(kind: 7, node: node, reason: "AppKit path clipping is not rendered"))
        case 19:
          issues.append(
            issue(kind: 19, node: node, reason: "AppKit bitmap drawing is not rendered yet"))
        default: break
        }
        if command.gradient != nil {
          issues.append(
            issue(
              kind: Int(command.kind), node: node,
              reason: "AppKit gradients use the fallback solid color"))
        }
        if command.textureImageId != -1 {
          issues.append(
            issue(
              kind: Int(command.kind), node: node,
              reason: "AppKit paint textures are not rendered"))
        }
        if command.blendMode != 3 {
          issues.append(
            issue(
              kind: Int(command.kind), node: node,
              reason: "AppKit currently uses the default source-over blend mode"))
        }
        if command.kind == 3, !command.third.isNaN || !command.fourth.isNaN {
          issues.append(
            issue(
              kind: 3, node: node, severity: .warning,
              reason: "AppKit scale pivot handling is approximate"))
        }
        if command.kind == 4, !command.second.isNaN || !command.third.isNaN {
          issues.append(
            issue(
              kind: 4, node: node, severity: .warning,
              reason: "AppKit rotation pivot handling is approximate"))
        }
      }
      pending.append(contentsOf: node.children)
    }
    if !snapshot.fonts.isEmpty {
      issues.append(
        RemoteComposeNativePlayerDiagnostic(
          severity: .unsupported, opcode: -1, operationName: "Embedded fonts", componentID: 0,
          reason: "The AppKit POC does not register document fonts yet"))
    }
    let unsupported = Set(
      snapshot.unsupportedOpcodes.map { Int(truncating: $0) }
        + issues.filter { $0.severity == .unsupported && $0.opcode >= 0 }.map(\.opcode))
    return RemoteComposeNativePlayerDiagnostics(
      issues: deduplicated(issues), unsupportedOpcodes: unsupported.sorted(), notes: snapshot.notes)
  }

  private static func issue(
    kind: Int, node: RcNativeNodeSnapshot,
    severity: RemoteComposeNativePlayerDiagnostic.Severity = .unsupported,
    reason: String
  ) -> RemoteComposeNativePlayerDiagnostic {
    RemoteComposeNativePlayerDiagnostic(
      severity: severity, opcode: kind, operationName: "AppKit snapshot command \(kind)",
      componentID: Int(node.componentId), reason: reason)
  }

  private static func deduplicated(_ issues: [RemoteComposeNativePlayerDiagnostic])
    -> [RemoteComposeNativePlayerDiagnostic]
  {
    var keys = Set<String>()
    return issues.filter {
      keys.insert("\($0.severity)|\($0.opcode)|\($0.componentID)|\($0.reason)").inserted
    }
  }
}

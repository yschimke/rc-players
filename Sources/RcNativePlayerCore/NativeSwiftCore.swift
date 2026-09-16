import Foundation

/// Immutable, platform-neutral output from the Swift wire/runtime path.
///
/// Keeping this model free of UIKit and foreign-runtime objects lets decoding and document state remain
/// actor-isolated while the final UIView model is materialized on the main actor.
public struct NativeSwiftDocumentSnapshot: Sendable {
  public let width: Int
  public let height: Int
  public let density: Float
  public let densityBehavior: Int
  public let root: NativeSwiftNodeSnapshot
  public let images: [NativeSwiftImageResourceSnapshot]
  public let needsContinuousFrames: Bool
}

public struct NativeSwiftImageResourceSnapshot: Sendable {
  public let id: Int
  public let width: Int
  public let height: Int
  public let type: Int
  public let encoding: Int
  public let data: Data
}

public struct NativeSwiftNodeSnapshot: Sendable {
  public enum Kind: Sendable {
    case root, content, canvas, box, row, column, text, image, custom
  }

  public let kind: Kind
  public let componentID: Int
  public let children: [NativeSwiftNodeSnapshot]
  public let commands: [NativeSwiftDrawCommandSnapshot]
  public let isClickable: Bool
  public let supportedGestures: [NativeSwiftGestureKind]
  public let accessibility: NativeSwiftAccessibilitySnapshot?
  public let widthType: Int
  public let widthValue: Float
  public let heightType: Int
  public let heightValue: Float
  public let padding: NativeSwiftInsets
  public let minimumWidth: Float
  public let maximumWidth: Float
  public let minimumHeight: Float
  public let maximumHeight: Float
  public let cornerRadius: Float
  public let backgroundARGB: UInt32?
  public let horizontalPositioning: Int
  public let verticalPositioning: Int
  public let spacing: Float
  public let text: NativeSwiftTextSnapshot?
  public let custom: NativeSwiftCustomSnapshot?
}

public struct NativeSwiftAccessibilitySnapshot: Sendable {
  public let role: Int
  public let mode: Int
  public let contentDescription: String?
  public let text: String?
  public let stateDescription: String?
  public let isEnabled: Bool
  public let isClickable: Bool
}

public struct NativeSwiftDrawCommandSnapshot: Sendable {
  public let kind: Int
  public let values: [Float]
  public let colorARGB: UInt32
  public let alpha: Float
  public let strokeWidth: Float
  public let isStroke: Bool
  public let strokeCap: Int
  public let strokeJoin: Int
  public let blendMode: Int
  public let path: [NativeSwiftPathElementSnapshot]
  public let pathWinding: Int
  public let image: NativeSwiftImageDrawSnapshot?
  public let textureImageID: Int?
  public let textureTileModeX: Int
  public let textureTileModeY: Int
  public let usesComponentGeometry: Bool
}

public struct NativeSwiftImageDrawSnapshot: Sendable {
  public let imageID: Int
  public let sourceLeft: Float
  public let sourceTop: Float
  public let sourceRight: Float
  public let sourceBottom: Float
  public let destinationLeft: Float
  public let destinationTop: Float
  public let destinationRight: Float
  public let destinationBottom: Float
  public let scaleType: Int
  public let scaleFactor: Float
  public let contentDescription: String?
}

public struct NativeSwiftPathElementSnapshot: Sendable {
  public let kind: Int
  public let values: [Float]
}

public enum NativeSwiftEvent: Equatable, Sendable {
  case namedAction(name: String, value: NativeSwiftActionValue)
}

public enum NativeSwiftGestureKind: Int, CaseIterable, Equatable, Sendable {
  case tap
  case longPress
  case doubleTap
  case touchDown
  case touchUp
  case touchCancel
}

public struct NativeSwiftPointerSample: Equatable, Sendable {
  public let x: Float
  public let y: Float
  public let velocityX: Float
  public let velocityY: Float

  public init(x: Float, y: Float, velocityX: Float = 0, velocityY: Float = 0) {
    self.x = x
    self.y = y
    self.velocityX = velocityX
    self.velocityY = velocityY
  }
}

public enum NativeSwiftActionValue: Equatable, Sendable {
  case none
  case float(Float)
  case integer(Int)
  case text(String)
}

public struct NativeSwiftInsets: Sendable {
  public var left: Float = 0
  public var top: Float = 0
  public var right: Float = 0
  public var bottom: Float = 0
}

public struct NativeSwiftTextSnapshot: Sendable {
  public let value: String
  public let colorARGB: UInt32
  public let size: Float
  public let style: Int
  public let weight: Float
  public let familyID: Int
  public let familyName: String?
  public let alignment: Int
  public let overflow: Int
  public let maximumLines: Int

  public init(
    value: String, colorARGB: UInt32, size: Float, style: Int, weight: Float,
    familyID: Int, familyName: String? = nil, alignment: Int, overflow: Int, maximumLines: Int
  ) {
    self.value = value
    self.colorARGB = colorARGB
    self.size = size
    self.style = style
    self.weight = weight
    self.familyID = familyID
    self.familyName = familyName
    self.alignment = alignment
    self.overflow = overflow
    self.maximumLines = maximumLines
  }
}

public struct NativeSwiftCustomSnapshot: Sendable {
  public let config: String
  public let properties: [NativeSwiftCustomPropertySnapshot]
}

public struct NativeSwiftCustomPropertySnapshot: Sendable {
  public let id: Int
  public let dataType: Int
  public let floatValue: Float
  public let integerValue: Int
  public let textValue: String?
}

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

/// The ids AndroidX's `RemoteContext` reserves for values the player supplies, not the document.
///
/// Only the ones this core actually loads are named, matching `RcSystemVariables` on the Kotlin
/// side. A reference to an id the player does not load resolves to 0 and poisons the arithmetic
/// downstream, so naming one here without loading it would be worse than leaving it out.
public enum NativeSwiftSystemVariables {
  /// Device pixels per dp, as the player is playing the document.
  public static let density = 27

  /// The host's default text size in pixels — 14sp at the player's density and font scale.
  public static let fontSize = 33

  /// The `sp` behind `fontSize`, before density and font scale. Every player has to agree on it:
  /// a capture divides its text size by this id to recover the `sp` it authored.
  public static let defaultFontSizeSp: Float = 14
}

/// Retained document state for the first pure-Swift operation family.
public final class NativeSwiftDocumentSession: @unchecked Sendable {
  private let document: ParsedDocument
  private var texts: [Int: String]
  private var floats: [Int: Float]
  private var hostDensity: Float = 1
  private var hostFontScale: Float = 1
  private var colors: [Int: UInt32]
  private var integers: [Int: Int]

  private init(document: ParsedDocument) {
    self.document = document
    texts = document.texts
    floats = document.floats
    colors = document.colors
    integers = document.integers
  }

  public static func open(data: Data) throws -> NativeSwiftDocumentSession {
    NativeSwiftDocumentSession(document: try NativeSwiftDocumentDecoder.decode(data))
  }

  /// Tells the document what density it is being played at.
  ///
  /// A `RemoteDensity.Host` capture defers its density instead of folding it in: it writes
  /// expressions over `ID_DENSITY` (27) and `ID_FONT_SIZE` (33), so the same document resolves at
  /// whatever the player supplies. Left unsaid, the native player plays at 1.0 — which is the
  /// density its default layout mode resolves dp geometry at, so the two agree by construction.
  /// A host reproducing Android geometry supplies its real playback density here.
  ///
  /// Non-finite and non-positive values are ignored rather than stored: both reach a document as a
  /// divisor, and a host that reports a zero density mid-layout should not turn the frame's
  /// geometry into NaN.
  public func setHostDensity(_ density: Float, fontScale: Float = 1) {
    if density.isFinite, density > 0 { hostDensity = density }
    if fontScale.isFinite, fontScale > 0 { hostFontScale = fontScale }
  }

  public func snapshot(timeSeconds: TimeInterval = 0) throws -> NativeSwiftDocumentSnapshot {
    let values = try resolvedFloats(timeSeconds: timeSeconds)
    for conversion in document.textFromFloats {
      let value = NativeSwiftFloatExpression.resolve(conversion.value, values: values)
      let precision = min(max(conversion.digitsAfter, 0), 12)
      texts[conversion.outputID] = String(format: "%.*f", precision, value)
    }
    for lookup in document.textLookups {
      guard let ids = document.idLists[lookup.listID], !ids.isEmpty else { continue }
      let index = min(max(integers[lookup.indexID] ?? 0, 0), ids.count - 1)
      texts[lookup.outputID] = texts[ids[index]] ?? ""
    }
    for merge in document.textMerges {
      texts[merge.outputID] = (texts[merge.leftID] ?? "") + (texts[merge.rightID] ?? "")
    }
    let resolvedColors = resolveColors(values: values)
    return NativeSwiftDocumentSnapshot(
      width: document.width,
      height: document.height,
      density: document.density,
      densityBehavior: document.densityBehavior,
      root: try resolve(document.root, values: values, colors: resolvedColors),
      images: document.images.values.sorted { $0.id < $1.id }.map(\.snapshot),
      needsContinuousFrames: document.needsContinuousFrames)
  }

  public func click(componentID: Int, timeSeconds: TimeInterval) throws -> [NativeSwiftEvent]? {
    try gesture(.tap, componentID: componentID, sample: nil, timeSeconds: timeSeconds)
  }

  public func gesture(
    _ kind: NativeSwiftGestureKind, componentID: Int,
    sample: NativeSwiftPointerSample? = nil, timeSeconds: TimeInterval
  ) throws -> [NativeSwiftEvent]? {
    guard let node = document.nodes[componentID], node.isClickable,
      node.accessibility?.isEnabled != false, let actions = node.actions[kind]
    else { return nil }
    let values = try resolvedFloats(timeSeconds: timeSeconds)
    var events: [NativeSwiftEvent] = []
    for action in actions {
      switch action {
      case .integerExpression(let targetID, let expressionID):
        guard let expression = document.integerExpressions[expressionID] else { continue }
        integers[targetID] = try NativeSwiftIntegerExpression.evaluate(
          mask: expression.mask, tokens: expression.tokens, values: integers)
      case .named(let action):
        guard let name = texts[action.nameTextID] else { continue }
        let value: NativeSwiftActionValue
        switch action.valueType {
        case -1: value = .none
        case 0: value = .float(values[action.valueID] ?? 0)
        case 1: value = .integer(integers[action.valueID] ?? 0)
        case 2: value = .text(texts[action.valueID] ?? "")
        default: continue
        }
        events.append(.namedAction(name: name, value: value))
      }
    }
    return events
  }

  public func setFloat(_ value: Float, for name: String) -> Bool {
    guard value.isFinite, let variable = document.namedVariables[name], variable.type == 1 else {
      return false
    }
    floats[variable.id] = value
    return true
  }

  public func setString(_ value: String, for name: String) -> Bool {
    guard value.utf8.count <= NativeSwiftDocumentDecoder.maximumStringBytes,
      let variable = document.namedVariables[name], variable.type == 0
    else { return false }
    texts[variable.id] = value
    return true
  }

  public func setColor(_ value: UInt32, for name: String) -> Bool {
    guard let variable = document.namedVariables[name], variable.type == 2 else { return false }
    colors[variable.id] = value
    return true
  }

  public func returnCustomText(_ value: String, componentID: Int, propertyID: Int) throws -> Bool {
    guard value.utf8.count <= NativeSwiftDocumentDecoder.maximumStringBytes,
      let node = document.nodes[componentID], node.kind == .custom,
      let property = node.custom?.properties.first(where: {
        $0.type == propertyID && $0.dataType == 4
      })
    else { return false }
    texts[property.valueBits] = value
    return true
  }

  public func returnCustomFloat(_ value: Float, componentID: Int, propertyID: Int) throws -> Bool {
    guard value.isFinite, let node = document.nodes[componentID], node.kind == .custom,
      node.custom?.properties.contains(where: {
        $0.type == propertyID && $0.dataType == 3
      }) == true
    else { return false }
    // Float return storage lands in a later operation-family slice. Rejecting it is safer than
    // claiming an update whose dependent expressions cannot yet be resolved by this runtime.
    return false
  }

  private func resolve(
    _ node: ParsedNode, values: [Int: Float], colors resolvedColors: [Int: UInt32]
  ) throws -> NativeSwiftNodeSnapshot {
    let text: NativeSwiftTextSnapshot?
    if let source = node.text {
      // `size > 0` used to be a parse-time guard. It still holds, but a computed size has no value
      // to check until here, so the check moved with it rather than being dropped.
      let size = try resolvedFloat(source.sizeWord, "text size", values: values, positive: true)
      let weight = try resolvedFloat(source.weightWord, "text weight", values: values)
      text = NativeSwiftTextSnapshot(
        value: texts[source.textID] ?? "",
        colorARGB: source.colorID.flatMap { resolvedColors[$0] } ?? source.colorARGB,
        size: size,
        style: source.style,
        weight: min(max(weight, 1), 1_000),
        familyID: source.familyID,
        familyName: texts[source.familyID],
        alignment: source.alignment,
        overflow: source.overflow,
        maximumLines: source.maximumLines)
    } else {
      text = nil
    }

    let custom: NativeSwiftCustomSnapshot?
    if let source = node.custom {
      guard let config = texts[source.configID] else {
        throw NativeSwiftCoreError.malformed(
          offset: 0, reason: "Custom component \(node.componentID) has no config text")
      }
      let properties = source.properties.map { property in
        let floatValue =
          property.dataType == 1
          ? Float(bitPattern: UInt32(bitPattern: Int32(property.valueBits))) : 0
        let integerValue: Int
        let textValue: String?
        switch property.dataType {
        case 2:
          integerValue = property.valueBits
          textValue = texts[property.valueBits]
        case 7:
          integerValue = Int(Int32(bitPattern: resolvedColors[property.valueBits] ?? 0))
          textValue = nil
        default:
          integerValue = property.valueBits
          textValue = nil
        }
        return NativeSwiftCustomPropertySnapshot(
          id: property.type, dataType: property.dataType, floatValue: floatValue,
          integerValue: integerValue, textValue: textValue)
      }
      custom = NativeSwiftCustomSnapshot(config: config, properties: properties)
    } else {
      custom = nil
    }

    let maximumWidth = try resolvedFloat(node.maximumWidthWord, "maximum width", values: values)
    let maximumHeight = try resolvedFloat(node.maximumHeightWord, "maximum height", values: values)
    let cornerRadii = try node.cornerRadiusWords.map {
      try resolvedFloat($0, "corner radius", values: values)
    }
    let cornerRadius = cornerRadii.max() ?? 0

    return NativeSwiftNodeSnapshot(
      kind: node.kind,
      componentID: node.componentID,
      children: try node.children.map { try resolve($0, values: values, colors: resolvedColors) },
      commands: try node.commands.map {
        try $0.resolve(
          values: values, colors: resolvedColors, texts: texts,
          componentValueIDs: Set(document.componentValues.map(\.valueID)))
      },
      isClickable: node.isClickable,
      supportedGestures: node.actions.keys.sorted { $0.rawValue < $1.rawValue },
      accessibility: node.accessibility.map {
        NativeSwiftAccessibilitySnapshot(
          role: $0.role, mode: $0.mode,
          contentDescription: texts[$0.contentDescriptionID], text: texts[$0.textID],
          stateDescription: texts[$0.stateDescriptionID], isEnabled: $0.isEnabled,
          isClickable: $0.isClickable)
      },
      widthType: node.widthType,
      widthValue: try resolvedFloat(node.widthWord, "width", values: values),
      heightType: node.heightType,
      heightValue: try resolvedFloat(node.heightWord, "height", values: values),
      padding: NativeSwiftInsets(
        left: try resolvedFloat(node.paddingWords.left, "padding left", values: values),
        top: try resolvedFloat(node.paddingWords.top, "padding top", values: values),
        right: try resolvedFloat(node.paddingWords.right, "padding right", values: values),
        bottom: try resolvedFloat(node.paddingWords.bottom, "padding bottom", values: values)),
      minimumWidth: try resolvedFloat(node.minimumWidthWord, "minimum width", values: values),
      maximumWidth: maximumWidth > 1_000_000 ? -1 : maximumWidth,
      minimumHeight: try resolvedFloat(node.minimumHeightWord, "minimum height", values: values),
      maximumHeight: maximumHeight > 1_000_000 ? -1 : maximumHeight,
      cornerRadius: cornerRadius,
      backgroundARGB: node.backgroundColorID.flatMap { resolvedColors[$0] } ?? node.backgroundARGB,
      horizontalPositioning: node.horizontalPositioning,
      verticalPositioning: node.verticalPositioning,
      spacing: try resolvedFloat(node.spacingWord, "spacing", values: values),
      text: text,
      custom: custom)
  }

  /// Resolves a float field that a document may either state outright or compute.
  ///
  /// A literal word is its own bit pattern, so `resolve` is a strict generalisation of the eager
  /// `Float` these fields used to hold; a NaN-boxed word is a reference into `values`. Validation
  /// that used to run while parsing runs here, because a computed field has nothing to validate
  /// until it resolves — and failing here is what keeps a garbage value from reaching UIKit.
  private func resolvedFloat(
    _ word: UInt32, _ field: String, values: [Int: Float], positive: Bool = false
  ) throws -> Float {
    let value = NativeSwiftFloatExpression.resolve(word, values: values)
    guard value.isFinite else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "\(field) resolved to \(value)")
    }
    guard !positive || value > 0 else {
      throw NativeSwiftCoreError.malformed(
        offset: 0, reason: "\(field) resolved to \(value), which is not positive")
    }
    return value
  }

  private func resolvedFloats(timeSeconds: TimeInterval) throws -> [Int: Float] {
    var result = floats
    for attribute in document.colorAttributes {
      result[attribute.outputID] = colorAttribute(
        attribute.type, of: colors[attribute.colorID] ?? 0)
    }
    // Player-supplied monotonic animation time. This fixture family uses both ids interchangeably
    // as moving clocks; keeping them tied to the injected logical timeline makes captures stable.
    result[1] = Float(timeSeconds)
    result[30] = Float(timeSeconds)
    // Not clocks, but owed by the player for the same reason and with the same failure: an
    // unsupplied reference resolves to 0 here, so a `RemoteDensity.Host` capture's
    // `([33] 14.0 / [27] / 15.0 *)` divides by zero and every size built from it becomes NaN.
    // A document that declares its own value at either id keeps it — `floats` seeds `result`.
    if result[NativeSwiftSystemVariables.density] == nil {
      result[NativeSwiftSystemVariables.density] = hostDensity
    }
    if result[NativeSwiftSystemVariables.fontSize] == nil {
      result[NativeSwiftSystemVariables.fontSize] =
        NativeSwiftSystemVariables.defaultFontSizeSp * hostFontScale * hostDensity
    }
    // Expressions are evaluated twice, deliberately. A component-value binding measures a node, and
    // a node's own geometry can now itself be a reference, so neither ordering is right alone: the
    // first pass gives the measurement something better than zero to read, the second lets an
    // expression that reads a measured value see it. Evaluation is pure, so repeating it is safe.
    //
    // This first pass is deliberately tolerant. An expression that reads a binding the measurement
    // below has not produced yet resolves that reference to zero, which can divide to a non-finite
    // result that `evaluate` rejects — correctly, but not yet. Dropping it here leaves the id
    // unset, exactly as it was before this pass existed; the authoritative pass after the
    // measurement evaluates it for real and throws if it is still bad.
    for expression in document.expressions {
      if let value = try? NativeSwiftFloatExpression.evaluate(expression.words, values: result) {
        result[expression.id] = value
      }
    }
    for binding in document.componentValues {
      let available = binding.type == 0 ? Float(document.width) : Float(document.height)
      guard let node = document.nodes[binding.componentID] else {
        // Matching the reference player: a width or height binding to a component this document
        // does not describe resolves to the document's own, rather than leaving the id unset and
        // taking every expression built on it down with it.
        if binding.type == 0 || binding.type == 1 { result[binding.valueID] = available }
        continue
      }
      let measuredNode = node.parent ?? node
      result[binding.valueID] = estimatedDimension(
        of: measuredNode, type: binding.type, available: available, values: result)
    }
    for expression in document.expressions {
      result[expression.id] = try NativeSwiftFloatExpression.evaluate(
        expression.words, values: result)
    }
    return result
  }

  private func colorAttribute(_ type: Int, of color: UInt32) -> Float {
    let red = Float((color >> 16) & 0xff) / 255
    let green = Float((color >> 8) & 0xff) / 255
    let blue = Float(color & 0xff) / 255
    let maximum = max(red, green, blue)
    let minimum = min(red, green, blue)
    let delta = maximum - minimum
    switch type {
    case 0:
      let sector: Float
      if maximum == minimum { sector = 0 }
      else if maximum == red { sector = (green - blue) / delta }
      else if maximum == green { sector = (blue - red) / delta + 2 }
      else { sector = (red - green) / delta + 4 }
      var hue = (sector * 60).truncatingRemainder(dividingBy: 360)
      if hue < 0 { hue += 360 }
      return hue / 360
    case 1: return maximum == minimum ? 0 : delta / maximum
    case 2: return maximum
    case 3: return red
    case 4: return green
    case 5: return blue
    default: return Float((color >> 24) & 0xff) / 255
    }
  }

  private func resolveColors(values: [Int: Float]) -> [Int: UInt32] {
    var result = colors
    for expression in document.colorExpressions {
      let mode = expression.modeAndAlpha & 0xff
      switch mode {
      case 0...3:
        let first = mode & 1 != 0 ? result[expression.first] ?? 0 : UInt32(bitPattern: Int32(expression.first))
        let second = mode & 2 != 0 ? result[expression.second] ?? 0 : UInt32(bitPattern: Int32(expression.second))
        let tween = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.third)), values: values)
        result[expression.outputID] = interpolateColor(first, second, tween: tween)
      case 4...6:
        let alpha: Float
        if mode == 4 { alpha = Float(expression.modeAndAlpha >> 16) / 255 }
        else if mode == 5 { alpha = Float(expression.modeAndAlpha >> 16) / 1024 }
        else {
          alpha = NativeSwiftFloatExpression.resolve(
            0x7fc0_0000 | UInt32(expression.modeAndAlpha >> 16), values: values)
        }
        let first = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.first)), values: values)
        let second = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.second)), values: values)
        let third = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.third)), values: values)
        result[expression.outputID] =
          mode == 4
          ? hsvColor(alpha: alpha, hue: first, saturation: second, brightness: third)
          : argbColor(alpha: alpha, red: first, green: second, blue: third)
      default: break
      }
    }
    return result
  }

  private func interpolateColor(_ first: UInt32, _ second: UInt32, tween: Float) -> UInt32 {
    if !tween.isFinite || tween == 0 { return first }
    if tween == 1 { return second }
    func channel(_ color: UInt32, shift: UInt32) -> Float {
      powf(Float((color >> shift) & 0xff) / 255, 2.2)
    }
    func encoded(_ value: Float) -> UInt32 {
      UInt32(min(max(Int(powf(value, 1 / 2.2) * 255), 0), 255))
    }
    let alpha = Float((first >> 24) & 0xff) + tween * Float(Int((second >> 24) & 0xff) - Int((first >> 24) & 0xff))
    let red = channel(first, shift: 16) + tween * (channel(second, shift: 16) - channel(first, shift: 16))
    let green = channel(first, shift: 8) + tween * (channel(second, shift: 8) - channel(first, shift: 8))
    let blue = channel(first, shift: 0) + tween * (channel(second, shift: 0) - channel(first, shift: 0))
    return UInt32(min(max(Int(alpha), 0), 255)) << 24 | encoded(red) << 16 | encoded(green) << 8
      | encoded(blue)
  }

  private func argbColor(alpha: Float, red: Float, green: Float, blue: Float) -> UInt32 {
    func byte(_ value: Float) -> UInt32 { UInt32(min(max(Int(value * 255 + 0.5), 0), 255)) }
    return byte(alpha) << 24 | byte(red) << 16 | byte(green) << 8 | byte(blue)
  }

  private func hsvColor(
    alpha: Float, hue: Float, saturation: Float, brightness: Float
  ) -> UInt32 {
    let section = Int(hue * 6)
    let fraction = hue * 6 - Float(section)
    let p = brightness * (1 - saturation)
    let q = brightness * (1 - fraction * saturation)
    let t = brightness * (1 - (1 - fraction) * saturation)
    let rgb: (Float, Float, Float)
    switch section {
    case 0: rgb = (brightness, t, p)
    case 1: rgb = (q, brightness, p)
    case 2: rgb = (p, brightness, t)
    case 3: rgb = (p, q, brightness)
    case 4: rgb = (t, p, brightness)
    case 5: rgb = (brightness, p, q)
    default: rgb = (0, 0, 0)
    }
    return argbColor(alpha: alpha, red: rgb.0, green: rgb.1, blue: rgb.2)
  }

  private func estimatedDimension(
    of node: ParsedNode, type: Int, available: Float, values: [Int: Float]
  ) -> Float {
    // Measurement runs before the final resolution pass and must not throw: an unresolvable field
    // reads as zero here and is rejected properly by `resolvedFloat` when the snapshot is built.
    func float(_ word: UInt32) -> Float {
      let value = NativeSwiftFloatExpression.resolve(word, values: values)
      return value.isFinite ? value : 0
    }
    let dimensionType = type == 0 ? node.widthType : node.heightType
    let dimensionValue = float(type == 0 ? node.widthWord : node.heightWord)
    if dimensionType == 0 || dimensionType == 6 { return max(dimensionValue, 0) }
    if dimensionType == 1 || dimensionType == 7 || dimensionType == 8 {
      return available * (dimensionValue.isNaN ? 1 : max(dimensionValue, 0))
    }
    let children = flattenedChildren(of: node, values: values)
    let childDimensions = children.map {
      estimatedDimension(of: $0, type: type, available: available, values: values)
    }
    let intrinsic: Float
    if let text = node.text {
      if type == 0 {
        intrinsic = Float(texts[text.textID]?.count ?? 0) * float(text.sizeWord) * 0.6
      } else {
        intrinsic = float(text.sizeWord) * 1.2
      }
    } else if type == 0 {
      intrinsic = node.kind == .row ? childDimensions.reduce(0, +) : childDimensions.max() ?? 0
    } else {
      intrinsic =
        node.kind == .column
        ? childDimensions.reduce(0, +)
          + float(node.spacingWord) * Float(max(childDimensions.count - 1, 0))
        : childDimensions.max() ?? 0
    }
    let padding =
      type == 0
      ? float(node.paddingWords.left) + float(node.paddingWords.right)
      : float(node.paddingWords.top) + float(node.paddingWords.bottom)
    let minimum = type == 1 ? float(node.minimumHeightWord) : 0
    return max(intrinsic + padding, minimum)
  }

  /// Flattens a bare content wrapper into its parent for measurement.
  ///
  /// The padding test runs on resolved values, not on words: a wrapper whose padding is computed
  /// and comes out zero is just as bare as one that says `0`, and comparing the encoded words
  /// would keep it — and so measure a row's grandchildren as one child's maximum rather than
  /// their sum.
  private func flattenedChildren(of node: ParsedNode, values: [Int: Float]) -> [ParsedNode] {
    func isZero(_ word: UInt32) -> Bool {
      NativeSwiftFloatExpression.resolve(word, values: values) == 0
    }
    return node.children.flatMap { child in
      if child.kind == .content, child.widthType == 2, child.heightType == 2,
        isZero(child.paddingWords.left), isZero(child.paddingWords.top),
        isZero(child.paddingWords.right), isZero(child.paddingWords.bottom)
      {
        return flattenedChildren(of: child, values: values)
      }
      return [child]
    }
  }
}

private struct ParsedDocument {
  let width: Int
  let height: Int
  let density: Float
  let densityBehavior: Int
  let root: ParsedNode
  let nodes: [Int: ParsedNode]
  let texts: [Int: String]
  let floats: [Int: Float]
  let colors: [Int: UInt32]
  let integers: [Int: Int]
  let integerExpressions: [Int: ParsedIntegerExpression]
  let namedVariables: [String: ParsedNamedVariable]
  let expressions: [ParsedFloatExpression]
  let componentValues: [ParsedComponentValue]
  let colorAttributes: [ParsedColorAttribute]
  let colorExpressions: [ParsedColorExpression]
  let images: [Int: ParsedImageResource]
  let textFromFloats: [ParsedTextFromFloat]
  let textMerges: [ParsedTextMerge]
  let idLists: [Int: [Int]]
  let textLookups: [ParsedTextLookupInt]
  let needsContinuousFrames: Bool
}

private struct ParsedImageResource {
  let id: Int
  let width: Int
  let height: Int
  let type: Int
  let encoding: Int
  let data: Data

  var snapshot: NativeSwiftImageResourceSnapshot {
    NativeSwiftImageResourceSnapshot(
      id: id, width: width, height: height, type: type, encoding: encoding, data: data)
  }
}

private struct ParsedComponentValue {
  let type: Int
  let componentID: Int
  let valueID: Int
}

private struct ParsedColorAttribute {
  let outputID: Int
  let colorID: Int
  let type: Int
}

private struct ParsedColorExpression {
  let outputID: Int
  let modeAndAlpha: Int
  let first: Int
  let second: Int
  let third: Int
}

private struct ParsedNamedVariable {
  let id: Int
  let type: Int
}

private struct ParsedFloatExpression {
  let id: Int
  let words: [UInt32]
}

private struct ParsedIntegerExpression {
  let mask: Int
  let tokens: [Int]
}

private struct ParsedTextFromFloat {
  let outputID: Int
  let value: UInt32
  let digitsAfter: Int
}

private struct ParsedTextMerge {
  let outputID: Int
  let leftID: Int
  let rightID: Int
}

private struct ParsedTextLookupInt {
  let outputID: Int
  let listID: Int
  let indexID: Int
}

private struct ParsedNamedAction {
  let nameTextID: Int
  let valueType: Int
  let valueID: Int
}

private enum ParsedAction {
  case named(ParsedNamedAction)
  case integerExpression(targetID: Int, expressionID: Int)
}

private struct ParsedModifierContainer {
  let node: ParsedNode?
  let gesture: NativeSwiftGestureKind?
}

private struct ParsedAccessibility {
  let contentDescriptionID: Int
  let role: Int
  let textID: Int
  let stateDescriptionID: Int
  let mode: Int
  let isEnabled: Bool
  let isClickable: Bool
}

private struct ParsedDrawCommand {
  let kind: Int
  let words: [UInt32]
  let paint: ParsedPaint
  let path: ParsedPath?
  let image: ParsedImageDraw?
  let alphaWord: UInt32?

  init(
    kind: Int, words: [UInt32], paint: ParsedPaint, path: ParsedPath? = nil,
    image: ParsedImageDraw? = nil, alphaWord: UInt32? = nil
  ) {
    self.kind = kind
    self.words = words
    self.paint = paint
    self.path = path
    self.image = image
    self.alphaWord = alphaWord
  }

  func resolve(
    values: [Int: Float], colors: [Int: UInt32], texts: [Int: String],
    componentValueIDs: Set<Int>
  ) throws
    -> NativeSwiftDrawCommandSnapshot
  {
    let geometryWords = words + (path?.words ?? []) + (image?.destination ?? [])
    let usesComponentGeometry = geometryWords.contains { word in
      NativeSwiftFloatExpression.referenceID(word).map(componentValueIDs.contains) ?? false
    }
    return NativeSwiftDrawCommandSnapshot(
      kind: kind,
      values: words.map { NativeSwiftFloatExpression.resolve($0, values: values) },
      colorARGB: paint.colorID.flatMap { colors[$0] } ?? paint.colorARGB,
      alpha: alphaWord.map { NativeSwiftFloatExpression.resolve($0, values: values) } ?? paint.alpha,
      strokeWidth: NativeSwiftFloatExpression.resolve(paint.strokeWidth, values: values),
      isStroke: paint.isStroke,
      strokeCap: paint.strokeCap,
      strokeJoin: paint.strokeJoin,
      blendMode: paint.blendMode,
      path: try path?.resolve(values: values) ?? [],
      pathWinding: path?.winding ?? 0,
      image: image?.resolve(values: values, texts: texts),
      textureImageID: paint.textureImageID,
      textureTileModeX: paint.textureTileModeX,
      textureTileModeY: paint.textureTileModeY,
      usesComponentGeometry: usesComponentGeometry)
  }
}

private struct ParsedImageDraw {
  let imageID: Int
  let source: [UInt32]
  let destination: [UInt32]
  let scaleType: Int
  let scaleFactor: UInt32
  let contentDescriptionID: Int

  func resolve(values: [Int: Float], texts: [Int: String]) -> NativeSwiftImageDrawSnapshot {
    let source = source.map { NativeSwiftFloatExpression.resolve($0, values: values) }
    let destination = destination.map { NativeSwiftFloatExpression.resolve($0, values: values) }
    return NativeSwiftImageDrawSnapshot(
      imageID: imageID,
      sourceLeft: source[0], sourceTop: source[1], sourceRight: source[2],
      sourceBottom: source[3], destinationLeft: destination[0], destinationTop: destination[1],
      destinationRight: destination[2], destinationBottom: destination[3], scaleType: scaleType,
      scaleFactor: NativeSwiftFloatExpression.resolve(scaleFactor, values: values),
      contentDescription: contentDescriptionID == 0 ? nil : texts[contentDescriptionID])
  }
}

private struct ParsedPath {
  let winding: Int
  let words: [UInt32]

  func resolve(values: [Int: Float]) throws -> [NativeSwiftPathElementSnapshot] {
    var result: [NativeSwiftPathElementSnapshot] = []
    var index = 0
    func arguments(_ count: Int, skippingLegacyPadding: Bool = false) throws -> [Float] {
      if skippingLegacyPadding { index += 2 }
      guard index >= 0, index + count <= words.count else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Truncated path data")
      }
      let resolved = words[index..<(index + count)].map {
        NativeSwiftFloatExpression.resolve($0, values: values)
      }
      index += count
      return resolved
    }
    while index < words.count {
      guard let command = NativeSwiftFloatExpression.referenceID(words[index]) else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Path command is not encoded")
      }
      index += 1
      switch command {
      case 10:
        result.append(NativeSwiftPathElementSnapshot(kind: command, values: try arguments(2)))
      case 11:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(2, skippingLegacyPadding: true)))
      case 12:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(4, skippingLegacyPadding: true)))
      case 13:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(5, skippingLegacyPadding: true)))
      case 14:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(6, skippingLegacyPadding: true)))
      case 15:
        result.append(NativeSwiftPathElementSnapshot(kind: command, values: []))
      case 16: return result
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: 123, offset: 0, reason: "path command \(command)")
      }
    }
    return result
  }
}

private struct ParsedPaint {
  var colorARGB: UInt32 = 0xff00_0000
  var colorID: Int?
  var alpha: Float = 1
  var strokeWidth: UInt32 = Float(1).bitPattern
  var isStroke = false
  var strokeCap = 0
  var strokeJoin = 0
  var blendMode = 3
  var textureImageID: Int?
  var textureTileModeX = 0
  var textureTileModeY = 0
}

/// The word for a literal `-1`, which is how a node says "no maximum". Spelled once so the default
/// cannot drift from the `> 1_000_000 ? -1` sentinel the snapshot still hands the layout.
private let nativeSwiftNegativeOneWord = Float(-1).bitPattern

/// Padding held as it arrived on the wire. `NativeSwiftInsets` stays the resolved, public shape.
private struct ParsedInsetWords {
  var left: UInt32 = 0
  var top: UInt32 = 0
  var right: UInt32 = 0
  var bottom: UInt32 = 0
}

private final class ParsedNode {
  let kind: NativeSwiftNodeSnapshot.Kind
  let componentID: Int
  weak var parent: ParsedNode?
  var children: [ParsedNode] = []
  var commands: [ParsedDrawCommand] = []
  var isClickable = false
  var actions: [NativeSwiftGestureKind: [ParsedAction]] = [:]
  var accessibility: ParsedAccessibility?
  var widthType = 2
  var widthWord: UInt32 = 0
  var heightType = 2
  var heightWord: UInt32 = 0
  var paddingWords = ParsedInsetWords()
  var minimumWidthWord: UInt32 = 0
  var maximumWidthWord: UInt32 = nativeSwiftNegativeOneWord
  var minimumHeightWord: UInt32 = 0
  var maximumHeightWord: UInt32 = nativeSwiftNegativeOneWord
  // Four words rather than one: a rounded clip states a radius per corner, and the maximum of four
  // references is not itself a word, so the reduction has to wait until they resolve.
  var cornerRadiusWords: [UInt32] = []
  var backgroundARGB: UInt32?
  var backgroundColorID: Int?
  var horizontalPositioning = 1
  var verticalPositioning = 4
  var spacingWord: UInt32 = 0
  var text: ParsedText?
  var custom: ParsedCustom?

  init(kind: NativeSwiftNodeSnapshot.Kind, componentID: Int) {
    self.kind = kind
    self.componentID = componentID
  }
}

private enum NativeSwiftIntegerExpression {
  static func evaluate(mask: Int, tokens: [Int], values: [Int: Int]) throws -> Int {
    var stack: [Int32] = []
    func pop(_ count: Int) throws -> [Int32] {
      guard stack.count >= count else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Integer expression stack underflow")
      }
      let result = Array(stack.suffix(count))
      stack.removeLast(count)
      return result
    }
    for (index, token) in tokens.enumerated() {
      let marked = UInt32(bitPattern: Int32(mask)) & (UInt32(1) << UInt32(index & 31)) != 0
      if !marked || token < 65_536 {
        stack.append(Int32(truncatingIfNeeded: marked ? values[token] ?? 0 : token))
        continue
      }
      let operation = token - 65_536
      if (1...14).contains(operation) {
        let value = try pop(2)
        let left = value[0]
        let right = value[1]
        switch operation {
        case 1: stack.append(left &+ right)
        case 2: stack.append(left &- right)
        case 3: stack.append(left &* right)
        case 4: stack.append(right == 0 || (left == .min && right == -1) ? 0 : left / right)
        case 5: stack.append(right == 0 || (left == .min && right == -1) ? 0 : left % right)
        case 6: stack.append(left << (right & 31))
        case 7: stack.append(left >> (right & 31))
        case 8:
          stack.append(Int32(bitPattern: UInt32(bitPattern: left) >> UInt32(right & 31)))
        case 9: stack.append(left | right)
        case 10: stack.append(left & right)
        case 11: stack.append(left ^ right)
        case 12: stack.append((left ^ (right >> 31)) &- (right >> 31))
        case 13: stack.append(min(left, right))
        default: stack.append(max(left, right))
        }
      } else if (15...20).contains(operation) {
        let value = try pop(1)[0]
        switch operation {
        case 15: stack.append(0 &- value)
        case 16: stack.append(value == .min ? .min : abs(value))
        case 17: stack.append(value &+ 1)
        case 18: stack.append(value &- 1)
        case 19: stack.append(~value)
        default: stack.append((value >> 31) | Int32(bitPattern: 0 &- UInt32(bitPattern: value)) >> 31)
        }
      } else if (21...23).contains(operation) {
        let value = try pop(3)
        if operation == 21 { stack.append(min(max(value[0], value[2]), value[1])) }
        else if operation == 22 { stack.append(value[2] > 0 ? value[1] : value[0]) }
        else { stack.append(value[2] &+ value[1] &* value[0]) }
      } else {
        throw NativeSwiftCoreError.unsupported(
          opcode: 144, offset: 0, reason: "integer expression operator \(operation)")
      }
    }
    guard stack.count == 1, let result = stack.last else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Invalid integer expression result")
    }
    return Int(result)
  }
}

private enum NativeSwiftFloatExpression {
  private static let payloadMask: UInt32 = 0x007f_ffff
  private static let referenceMask: UInt32 = 0x003f_ffff
  private static let operatorOffset = 0x0031_0000

  static func resolve(_ word: UInt32, values: [Int: Float]) -> Float {
    guard isEncoded(word) else { return Float(bitPattern: word) }
    return values[Int(word & referenceMask)] ?? 0
  }

  static func referenceID(_ word: UInt32) -> Int? {
    guard isEncoded(word) else { return nil }
    let payload = Int(word & payloadMask)
    guard payload <= operatorOffset || payload > operatorOffset + 79 else { return nil }
    return Int(word & referenceMask)
  }

  static func evaluate(_ words: [UInt32], values: [Int: Float]) throws -> Float {
    var stack: [Float] = []
    stack.reserveCapacity(min(words.count, 128))
    func pop(_ count: Int) throws -> [Float] {
      guard stack.count >= count else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Float expression stack underflow")
      }
      let result = Array(stack.suffix(count))
      stack.removeLast(count)
      return result
    }
    for word in words {
      let payload = Int(word & payloadMask)
      guard isEncoded(word), payload > operatorOffset, payload <= operatorOffset + 79 else {
        stack.append(resolve(word, values: values))
        guard stack.count <= 128 else {
          throw NativeSwiftCoreError.malformed(offset: 0, reason: "Float expression stack overflow")
        }
        continue
      }
      let operation = payload - operatorOffset
      switch operation {
      case 1...8:
        let value = try pop(2)
        switch operation {
        case 1: stack.append(value[0] + value[1])
        case 2: stack.append(value[0] - value[1])
        case 3: stack.append(value[0] * value[1])
        case 4: stack.append(value[0] / value[1])
        case 5: stack.append(value[0].truncatingRemainder(dividingBy: value[1]))
        case 6: stack.append(min(value[0], value[1]))
        case 7: stack.append(max(value[0], value[1]))
        default: stack.append(powf(value[0], value[1]))
        }
      case 9...11, 13...23, 28...31, 45, 51...53, 73:
        let value = try pop(1)[0]
        switch operation {
        case 9: stack.append(sqrtf(value))
        case 10: stack.append(abs(value))
        case 11: stack.append(value == 0 ? 0 : (value < 0 ? -1 : 1))
        case 13: stack.append(expf(value))
        case 14: stack.append(floorf(value))
        case 15: stack.append(log10f(value))
        case 16: stack.append(logf(value))
        case 17: stack.append(roundf(value))
        case 18: stack.append(sinf(value))
        case 19: stack.append(cosf(value))
        case 20: stack.append(tanf(value))
        case 21: stack.append(asinf(value))
        case 22: stack.append(acosf(value))
        case 23: stack.append(atanf(value))
        case 28: stack.append(cbrtf(value))
        case 29: stack.append(value * 57.29578)
        case 30: stack.append(value * 0.017453292)
        case 31: stack.append(ceilf(value))
        case 45: stack.append(value * value)
        case 51: stack.append(log2f(value))
        case 52: stack.append(1 / value)
        case 53: stack.append(value - Float(Int(value)))
        default: stack.append(-value)
        }
      case 12, 24, 43, 44, 47, 54:
        let value = try pop(2)
        switch operation {
        case 12: stack.append(copysignf(abs(value[0]), value[1]))
        case 24: stack.append(atan2f(value[0], value[1]))
        case 43: stack.append(value[0] * value[0] + value[1] * value[1])
        case 44: stack.append(value[0] > value[1] ? 1 : 0)
        case 47: stack.append(hypotf(value[0], value[1]))
        default:
          let doubled = value[1] * 2
          let remainder = value[0].truncatingRemainder(dividingBy: doubled)
          stack.append(remainder < value[1] ? remainder : doubled - remainder)
        }
      case 25:
        let value = try pop(3)
        stack.append(value[2] + value[1] * value[0])
      case 26:
        let value = try pop(3)
        stack.append(value[2] > 0 ? value[1] : value[0])
      case 27:
        let value = try pop(3)
        stack.append(min(max(value[0], value[2]), value[1]))
      case 49:
        let value = try pop(3)
        stack.append(value[0] + (value[1] - value[0]) * value[2])
      case 74:
        let value = try pop(5)
        stack.append(cubicEasing(value[0], value[1], value[2], value[3], value[4]))
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: 81, offset: 0, reason: "float expression operator \(operation)")
      }
    }
    guard stack.count == 1, let result = stack.last, result.isFinite else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Invalid float expression result")
    }
    return result
  }

  private static func isEncoded(_ word: UInt32) -> Bool {
    word & 0x7f80_0000 == 0x7f80_0000 && word & 0x007f_ffff != 0
  }

  private static func cubicEasing(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ x: Float)
    -> Float
  {
    if x <= 0 { return 0 }
    if x >= 1 { return 1 }
    func coordinate(_ t: Float, _ first: Float, _ second: Float) -> Float {
      let inverse = 1 - t
      return first * 3 * inverse * inverse * t + second * 3 * inverse * t * t + t * t * t
    }
    var t: Float = 0.5
    var range: Float = 0.5
    while range > 0.01 {
      let current = coordinate(t, x1, x2)
      range *= 0.5
      if current < x { t += range } else { t -= range }
    }
    let lowerX = coordinate(t - range, x1, x2)
    let upperX = coordinate(t + range, x1, x2)
    let lowerY = coordinate(t - range, y1, y2)
    let upperY = coordinate(t + range, y1, y2)
    return (upperY - lowerY) * (x - lowerX) / (upperX - lowerX) + lowerY
  }
}

private struct ParsedText {
  let textID: Int
  let colorARGB: UInt32
  let colorID: Int?
  let sizeWord: UInt32
  let style: Int
  let weightWord: UInt32
  let familyID: Int
  let alignment: Int
  let overflow: Int
  let maximumLines: Int
}

private struct ParsedCustom {
  let configID: Int
  let properties: [ParsedCustomProperty]
}

private struct ParsedCustomProperty {
  let type: Int
  let dataType: Int
  let valueBits: Int
}

private enum NativeSwiftDocumentDecoder {
  static let maximumStringBytes = 4_000
  private static let maximumOperations = 100_000
  private static let maximumProperties = 2_000
  private static let maximumNodes = 20_000
  private static let maximumNestingDepth = 256

  static func decode(_ data: Data) throws -> ParsedDocument {
    var input = WireReader(data)
    guard try input.u8("header opcode") == 0 else {
      throw input.malformed("Document must begin with a header")
    }
    let encodedMajor = try input.int("major version")
    _ = try input.int("minor version")
    _ = try input.int("patch version")
    let major: Int
    let width: Int
    let height: Int
    var density: Float = 1
    var densityBehavior = 0
    if encodedMajor < 0x10000 {
      major = encodedMajor
      width = try input.int("width")
      height = try input.int("height")
      _ = try input.int("capabilities high word")
      _ = try input.int("capabilities low word")
    } else {
      guard encodedMajor & ~0xffff == 0x048c_0000 else {
        throw input.malformed("Invalid modern header magic")
      }
      major = encodedMajor & 0xffff
      let count = try input.count("header property count", maximum: maximumProperties)
      var modernWidth: Int?
      var modernHeight: Int?
      for index in 0..<count {
        let tag = try input.u16("header property \(index) tag")
        let length = Int(try input.u16("header property \(index) length"))
        let type = Int(tag >> 10)
        let key = Int(tag & 0x03ff)
        switch type {
        case 0:
          guard length == 4 else { throw input.malformed("Invalid integer header property length") }
          let value = try input.int("header property \(index)")
          if key == 5 { modernWidth = value }
          if key == 6 { modernHeight = value }
          if key == 27 { densityBehavior = value }
        case 1:
          guard length == 4 else { throw input.malformed("Invalid float header property length") }
          let value = Float(bitPattern: try input.word("header property \(index)"))
          if key == 7 { density = value }
        case 2:
          guard length == 8 else { throw input.malformed("Invalid long header property length") }
          _ = try input.int("header property \(index) high word")
          _ = try input.int("header property \(index) low word")
        case 3:
          guard length >= 4 else { throw input.malformed("Invalid string header property length") }
          let value = try input.utf8("header property \(index)", maximum: maximumStringBytes)
          guard value.utf8.count + 4 == length else {
            throw input.malformed("Invalid string header property length")
          }
        default: throw input.malformed("Unknown header property type \(type)")
        }
      }
      guard let modernWidth, let modernHeight else {
        throw input.malformed("Modern header has no document dimensions")
      }
      width = modernWidth
      height = modernHeight
    }
    guard major >= 0, width > 0, height > 0, density.isFinite, density > 0,
      (0...2).contains(densityBehavior)
    else {
      throw input.malformed("Header dimensions, version, or density metadata are invalid")
    }

    var texts: [Int: String] = [:]
    var floats: [Int: Float] = [:]
    var colors: [Int: UInt32] = [:]
    var integers: [Int: Int] = [:]
    var integerExpressions: [Int: ParsedIntegerExpression] = [:]
    var namedVariables: [String: ParsedNamedVariable] = [:]
    var expressions: [ParsedFloatExpression] = []
    var componentValues: [ParsedComponentValue] = []
    var colorAttributes: [ParsedColorAttribute] = []
    var colorExpressions: [ParsedColorExpression] = []
    var paths: [Int: ParsedPath] = [:]
    var images: [Int: ParsedImageResource] = [:]
    var textFromFloats: [ParsedTextFromFloat] = []
    var textMerges: [ParsedTextMerge] = []
    var idLists: [Int: [Int]] = [:]
    var textLookups: [ParsedTextLookupInt] = []
    var nodes: [Int: ParsedNode] = [:]
    var stack: [ParsedNode] = []
    var root: ParsedNode?
    var operationCount = 0
    var expressionWordCount = 0
    var modifierContainers: [ParsedModifierContainer] = []
    var paint = ParsedPaint()

    func begin(_ node: ParsedNode) throws {
      guard nodes.count < maximumNodes else {
        throw input.malformed("Node count exceeds \(maximumNodes)")
      }
      guard stack.count < maximumNestingDepth else {
        throw input.malformed("Nesting depth exceeds \(maximumNestingDepth)")
      }
      guard nodes[node.componentID] == nil else {
        throw input.malformed("Duplicate component id \(node.componentID)")
      }
      if let parent = stack.last {
        node.parent = parent
        parent.children.append(node)
      } else if root == nil {
        root = node
      } else {
        throw input.malformed("Document has more than one root component")
      }
      nodes[node.componentID] = node
      stack.append(node)
    }

    while !input.isAtEnd {
      operationCount += 1
      guard operationCount <= maximumOperations else {
        throw input.malformed("Operation count exceeds \(maximumOperations)")
      }
      let opcodeOffset = input.offset
      let opcode = try input.u8("opcode")
      switch opcode {
      case 40:  // Paint data
        let count = try input.count("paint word count", maximum: 1_024)
        var words: [Int] = []
        words.reserveCapacity(count)
        for _ in 0..<count { words.append(try input.int("paint word")) }
        try applyPaint(words, to: &paint, input: input)
      case 38:  // Clip path
        let id = try input.int("clip path id")
        guard let path = paths[id] else { throw input.malformed("Missing path \(id)") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 7, words: [], paint: paint, path: path))
      case 39:  // Clip rectangle
        let words = try (0..<4).map { _ in try input.word("clip rectangle value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 6, words: words, paint: paint))
      case 42:  // Draw rectangle
        let words = try (0..<4).map { _ in try input.word("draw rectangle value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 10, words: words, paint: paint))
      case 44:  // Draw bitmap
        let imageID = try input.int("draw bitmap image id")
        guard let bitmap = images[imageID] else { throw input.malformed("Missing bitmap \(imageID)") }
        let destination = try (0..<4).map { _ in try input.word("draw bitmap destination") }
        let descriptionID = try input.int("draw bitmap content description id")
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(
            kind: 19, words: [], paint: paint,
            image: ParsedImageDraw(
              imageID: imageID,
              source: [0, 0, Float(bitmap.width).bitPattern, Float(bitmap.height).bitPattern],
              destination: destination, scaleType: 0, scaleFactor: Float(1).bitPattern,
              contentDescriptionID: descriptionID)))
      case 46:  // Draw circle
        let words = try (0..<3).map { _ in try input.word("draw circle value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 12, words: words, paint: paint))
      case 47:  // Draw line
        let words = try (0..<4).map { _ in try input.word("draw line value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 13, words: words, paint: paint))
      case 51:  // Draw rounded rectangle
        let words = try (0..<6).map { _ in try input.word("draw rounded rectangle value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 14, words: words, paint: paint))
      case 52:  // Draw sector
        let words = try (0..<6).map { _ in try input.word("draw sector value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 16, words: words, paint: paint))
      case 56:  // Draw oval
        let words = try (0..<4).map { _ in try input.word("draw oval value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 11, words: words, paint: paint))
      case 54:  // Rounded clip rectangle
        let node = try currentNode(stack, input: input)
        node.cornerRadiusWords = try (0..<4).map { _ in try input.word("corner radius") }
      case 59:  // Click modifier encloses its action operations.
        let node = try currentNode(stack, input: input)
        node.isClickable = true
        modifierContainers.append(ParsedModifierContainer(node: node, gesture: .tap))
      case 83:  // Multi-click modifier.
        let node = try currentNode(stack, input: input)
        let raw = try input.int("multi-click type")
        let gesture: NativeSwiftGestureKind
        switch raw {
        case 0: gesture = .tap
        case 1: gesture = .longPress
        case 2: gesture = .doubleTap
        default: throw input.malformed("Unknown multi-click type \(raw)")
        }
        node.isClickable = true
        modifierContainers.append(ParsedModifierContainer(node: node, gesture: gesture))
      case 219, 220, 225:  // Pointer lifecycle action containers.
        let node = try currentNode(stack, input: input)
        let gesture: NativeSwiftGestureKind =
          opcode == 219 ? .touchDown : (opcode == 220 ? .touchUp : .touchCancel)
        node.isClickable = true
        modifierContainers.append(ParsedModifierContainer(node: node, gesture: gesture))
      case 130:  // Matrix save
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 0, words: [], paint: paint))
      case 131:  // Matrix restore
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 1, words: [], paint: paint))
      case 173:  // Canvas operations container
        modifierContainers.append(ParsedModifierContainer(node: nil, gesture: nil))
      case 139, 174:  // Marker/modifier operations without payload
        break
      case 16:  // Width
        let node = try currentNode(stack, input: input)
        node.widthType = try input.dimensionType("width type")
        node.widthWord = try input.word("width")
      case 55:  // Background
        let node = try currentNode(stack, input: input)
        let flags = try input.int("background flags")
        let colorID = try input.int("background color id")
        _ = try input.int("background reserved1")
        _ = try input.int("background reserved2")
        let usesColorID = flags & 2 != 0
        let red = try input.floatWord("background red", requireLiteral: !usesColorID)
        let green = try input.floatWord("background green", requireLiteral: !usesColorID)
        let blue = try input.floatWord("background blue", requireLiteral: !usesColorID)
        let alpha = try input.floatWord("background alpha", requireLiteral: !usesColorID)
        let shape = try input.int("background shape")
        guard shape == 0 else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset, reason: "non-rectangular backgrounds")
        }
        node.backgroundARGB =
          !usesColorID ? argb(red: red, green: green, blue: blue, alpha: alpha) : nil
        node.backgroundColorID = usesColorID ? colorID : nil
      case 58:  // Padding
        let node = try currentNode(stack, input: input)
        node.paddingWords = ParsedInsetWords(
          left: try input.word("padding left"),
          top: try input.word("padding top"),
          right: try input.word("padding right"),
          bottom: try input.word("padding bottom"))
      case 67:  // Height
        let node = try currentNode(stack, input: input)
        node.heightType = try input.dimensionType("height type")
        node.heightWord = try input.word("height")
      case 80:  // Float constant
        let floatID = try input.int("float id")
        let constantWord = try input.word("float value")
        if NativeSwiftFloatExpression.referenceID(constantWord) != nil {
          // A constant that names another value is an alias, not a number. Expressing it as a
          // one-word expression runs it through the same ordered evaluation as any other computed
          // value, instead of freezing a reference's raw NaN bits into the seed map.
          expressions.append(ParsedFloatExpression(id: floatID, words: [constantWord]))
        } else {
          let value = Float(bitPattern: constantWord)
          guard value.isFinite else { throw input.malformed("float value must be finite") }
          floats[floatID] = value
        }
      case 101:  // Bitmap data
        let imageID = try input.int("bitmap id")
        let widthAndType = UInt32(bitPattern: Int32(try input.int("bitmap width and type")))
        let heightAndEncoding = UInt32(
          bitPattern: Int32(try input.int("bitmap height and encoding")))
        let width = Int(widthAndType & 0xffff)
        let height = Int(heightAndEncoding & 0xffff)
        let type = Int(widthAndType >> 16)
        let encoding = Int(heightAndEncoding >> 16)
        guard width > 0, height > 0, width <= 4_096, height <= 4_096 else {
          throw input.malformed("Invalid bitmap dimensions \(width)x\(height)")
        }
        guard images[imageID] == nil else { throw input.malformed("Duplicate bitmap \(imageID)") }
        images[imageID] = ParsedImageResource(
          id: imageID, width: width, height: height, type: type, encoding: encoding,
          data: try input.data("bitmap data", maximum: 8 * 1_024 * 1_024))
      case 81:  // Float expression
        let id = try input.int("float expression id")
        // The length word packs two counts: the expression's own tokens in the low half, and an
        // optional trailing animation description in the high half. Both have to be consumed to
        // keep the stream in sync -- the buffer has no length prefixes -- but only the first half
        // is the expression. Reading all of them as tokens left the animation's parameters on the
        // evaluation stack, so a one-token expression with a spring or duration after it finished
        // holding two values and was rejected as malformed, taking the document with it.
        //
        // The animation itself is read and dropped: this player resolves a float expression to its
        // current value and does not animate it.
        let lengths = try input.int("float expression lengths")
        let valueCount = lengths & 0xffff
        let animationCount = (lengths >> 16) & 0xffff
        let count = valueCount + animationCount
        guard count <= 65_567 else { throw input.malformed("Float expression is too long") }
        expressionWordCount += count
        guard expressionWordCount <= 200_000 else {
          throw input.malformed("Float expression work exceeds 200000 words")
        }
        var words: [UInt32] = []
        words.reserveCapacity(count)
        for _ in 0..<count { words.append(try input.word("float expression word")) }
        expressions.append(ParsedFloatExpression(id: id, words: Array(words.prefix(valueCount))))
      case 93:  // Custom
        let id = try input.int("custom component id")
        _ = try input.int("custom animation id")
        let configID = try input.int("custom config id")
        let count = try input.count("custom property count", maximum: maximumProperties)
        var properties: [ParsedCustomProperty] = []
        properties.reserveCapacity(count)
        for index in 0..<count {
          let type = try input.signedU16("custom property \(index) type")
          let dataType = try input.signedU16("custom property \(index) data type")
          guard (0...9).contains(dataType) else {
            throw input.malformed("Unknown custom property data type \(dataType)")
          }
          let valueBits = try input.int("custom property \(index) value")
          if [3, 5, 6, 9].contains(dataType) {
            throw NativeSwiftCoreError.unsupported(
              opcode: opcode, offset: opcodeOffset,
              reason: "custom property data type \(dataType) is not migrated")
          }
          if dataType == 1, Float(bitPattern: UInt32(bitPattern: Int32(valueBits))).isNaN {
            throw NativeSwiftCoreError.unsupported(
              opcode: opcode, offset: opcodeOffset, reason: "dynamic custom float property")
          }
          properties.append(
            ParsedCustomProperty(
              type: type, dataType: dataType, valueBits: valueBits))
        }
        let node = ParsedNode(kind: .custom, componentID: id)
        node.custom = ParsedCustom(configID: configID, properties: properties)
        try begin(node)
      case 102:  // Text data
        let id = try input.int("text id")
        texts[id] = try input.utf8("text", maximum: maximumStringBytes)
      case 138:  // Color constant
        colors[try input.int("color id")] = UInt32(bitPattern: Int32(try input.int("color")))
      case 140:  // Integer constant
        integers[try input.int("integer id")] = try input.int("integer value")
      case 144:  // Integer expression
        let outputID = try input.int("integer expression output id")
        let mask = try input.int("integer expression mask")
        let count = try input.count("integer expression value count", maximum: 320)
        var tokens: [Int] = []
        tokens.reserveCapacity(count)
        for _ in 0..<count { tokens.append(try input.int("integer expression value")) }
        integerExpressions[outputID] = ParsedIntegerExpression(mask: mask, tokens: tokens)
        integers[outputID] = try NativeSwiftIntegerExpression.evaluate(
          mask: mask, tokens: tokens, values: integers)
      case 146:  // List of resource ids
        let id = try input.int("id list id")
        let count = try input.count("id list count", maximum: maximumProperties)
        idLists[id] = try (0..<count).map { _ in try input.int("id list value") }
      case 134:  // Dynamic color expression
        let expression = ParsedColorExpression(
          outputID: try input.int("color expression output id"),
          modeAndAlpha: try input.int("color expression mode and alpha"),
          first: try input.int("color expression first value"),
          second: try input.int("color expression second value"),
          third: try input.int("color expression third value"))
        guard (0...6).contains(expression.modeAndAlpha & 0xff) else {
          throw input.malformed("Unknown color expression mode")
        }
        colorExpressions.append(expression)
      case 135:  // Text derived from a float
        let outputID = try input.int("text from float output id")
        let value = try input.word("text from float value")
        let digits = UInt32(bitPattern: Int32(try input.int("text from float digits")))
        _ = try input.int("text from float flags")
        textFromFloats.append(
          ParsedTextFromFloat(
            outputID: outputID, value: value,
            digitsAfter: Int(Int16(bitPattern: UInt16(digits & 0xffff)))))
      case 136:  // Text concatenation
        textMerges.append(
          ParsedTextMerge(
            outputID: try input.int("text merge output id"),
            leftID: try input.int("text merge left id"),
            rightID: try input.int("text merge right id")))
      case 153:  // Text lookup using an integer id
        textLookups.append(
          ParsedTextLookupInt(
            outputID: try input.int("text lookup output id"),
            listID: try input.int("text lookup list id"),
            indexID: try input.int("text lookup index id")))
      case 123:  // Path data; bounded now, drawing support is a separate operation family.
        let idAndWinding = try input.int("path id and winding")
        let count = try input.count("path word count", maximum: 20_000)
        var words: [UInt32] = []
        words.reserveCapacity(count)
        for _ in 0..<count { words.append(try input.word("path word")) }
        paths[idAndWinding & 0x00ff_ffff] = ParsedPath(
          winding: idAndWinding >> 24, words: words)
      case 124:
        let id = try input.int("path id")
        guard let path = paths[id] else { throw input.malformed("Missing path \(id)") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 18, words: [], paint: paint, path: path))
      case 137:  // Named variable
        let id = try input.int("named variable id")
        let type = try input.int("named variable type")
        guard (0...6).contains(type) else { throw input.malformed("Unknown named variable type") }
        let name = try input.utf8("named variable name", maximum: maximumStringBytes)
        namedVariables[name] = ParsedNamedVariable(id: id, type: type)
      case 150:  // Component value binding
        let type = try input.int("component value type")
        let componentID = try input.int("component value component id")
        let valueID = try input.int("component value id")
        guard type == 0 || type == 1 else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset, reason: "component value type \(type)")
        }
        // A binding may name a component that is not an open ancestor at this point in the stream --
        // one already closed, or one the document declares elsewhere. The old guard searched the
        // open stack only and rejected the whole document otherwise, which is both stricter than
        // the lookup that consumes these bindings (it searches every known node) and stricter than
        // the reference player, which falls back to the document's own dimensions when it cannot
        // find the component. Resolution below handles an unknown component on its own.
        componentValues.append(
          ParsedComponentValue(type: type, componentID: componentID, valueID: valueID))
      case 180:  // Color channel attribute
        let attribute = ParsedColorAttribute(
          outputID: try input.int("color attribute output id"),
          colorID: try input.int("color attribute color id"),
          type: try input.signedU16("color attribute type"))
        guard (0...6).contains(attribute.type) else {
          throw input.malformed("Unknown color attribute type \(attribute.type)")
        }
        colorAttributes.append(attribute)
      case 187:  // Matrix expression; retained shader transforms are approximated by native scaling.
        _ = try input.int("matrix expression id")
        _ = try input.int("matrix expression type")
        let count = try input.count("matrix expression value count", maximum: 32)
        for _ in 0..<count { _ = try input.word("matrix expression value") }
      case 171:  // Image dimension attribute
        let outputID = try input.int("image attribute output id")
        let imageID = try input.int("image attribute image id")
        let type = try input.signedU16("image attribute type")
        let count = Int(try input.u16("image attribute argument count"))
        guard count <= maximumProperties else {
          throw input.malformed("Too many image attribute arguments")
        }
        for _ in 0..<count { _ = try input.int("image attribute argument") }
        guard let image = images[imageID] else { throw input.malformed("Missing bitmap \(imageID)") }
        guard type == 0 || type == 1 else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset, reason: "image attribute type \(type)")
        }
        floats[outputID] = Float(type == 0 ? image.width : image.height)
      case 200:  // Root
        try begin(ParsedNode(kind: .root, componentID: try input.int("root component id")))
      case 201:  // Content
        try begin(ParsedNode(kind: .content, componentID: try input.int("content component id")))
      case 202:  // Box
        let node = ParsedNode(kind: .box, componentID: try input.int("box component id"))
        _ = try input.int("box animation id")
        node.horizontalPositioning = try input.int("box horizontal positioning")
        node.verticalPositioning = try input.int("box vertical positioning")
        try begin(node)
      case 203:  // Row
        let node = ParsedNode(kind: .row, componentID: try input.int("row component id"))
        _ = try input.int("row animation id")
        node.horizontalPositioning = try input.int("row horizontal positioning")
        node.verticalPositioning = try input.int("row vertical positioning")
        node.spacingWord = try input.word("row spacing")
        try begin(node)
      case 204:  // Column
        let node = ParsedNode(kind: .column, componentID: try input.int("column component id"))
        _ = try input.int("column animation id")
        node.horizontalPositioning = try input.int("column horizontal positioning")
        node.verticalPositioning = try input.int("column vertical positioning")
        node.spacingWord = try input.word("column spacing")
        try begin(node)
      case 205:  // Canvas
        let node = ParsedNode(kind: .canvas, componentID: try input.int("canvas component id"))
        _ = try input.int("canvas animation id")
        try begin(node)
      case 129:  // Matrix rotate
        let words = try (0..<3).map { _ in try input.word("matrix rotate value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 4, words: words, paint: paint))
      case 126:  // Matrix scale
        let words = try (0..<4).map { _ in try input.word("matrix scale value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 3, words: words, paint: paint))
      case 127:  // Matrix translate
        let words = try (0..<2).map { _ in try input.word("matrix translate value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 2, words: words, paint: paint))
      case 128:  // Matrix skew
        let words = try (0..<2).map { _ in try input.word("matrix skew value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 5, words: words, paint: paint))
      case 208:  // Text layout
        let node = ParsedNode(kind: .text, componentID: try input.int("text component id"))
        _ = try input.int("text animation id")
        let textID = try input.int("text id")
        let color = UInt32(bitPattern: Int32(try input.int("text color")))
        let size = try input.word("text size")
        let style = try input.int("text style")
        let weight = try input.word("text weight")
        let familyID = try input.int("text family id")
        let alignmentAndFlags = try input.int("text alignment")
        let flags = UInt32(bitPattern: Int32(alignmentAndFlags)) >> 16
        guard flags == 0 else {
          throw NativeSwiftCoreError.unsupported(
            opcode: opcode, offset: opcodeOffset, reason: "dynamic text colors")
        }
        let overflow = try input.int("text overflow")
        let maximumLines = try input.int("text maximum lines")
        // `size` is no longer checked here: it may be a reference, and `resolvedFloat` applies the
        // same `> 0` rule once there is a number to apply it to.
        guard (0...3).contains(style), (1...6).contains(alignmentAndFlags & 0xffff),
          (1...5).contains(overflow), maximumLines > 0
        else { throw input.malformed("Invalid text layout values") }
        node.text = ParsedText(
          textID: textID, colorARGB: color, colorID: nil, sizeWord: size, style: style,
          weightWord: weight,
          familyID: familyID, alignment: alignmentAndFlags & 0xffff, overflow: overflow,
          maximumLines: maximumLines)
        try begin(node)
      case 234:  // Image layout
        let node = ParsedNode(kind: .image, componentID: try input.int("image component id"))
        _ = try input.int("image animation id")
        let imageID = try input.int("image bitmap id")
        let scaleType = try input.int("image scale type")
        let alpha = try input.word("image alpha")
        guard let bitmap = images[imageID] else { throw input.malformed("Missing bitmap \(imageID)") }
        guard (0...7).contains(scaleType) else { throw input.malformed("Invalid image scale type") }
        let source: [UInt32] = [
          0, 0, Float(bitmap.width).bitPattern, Float(bitmap.height).bitPattern,
        ]
        node.commands.append(
          ParsedDrawCommand(
            kind: 19, words: [], paint: paint,
            image: ParsedImageDraw(
              imageID: imageID, source: source, destination: source, scaleType: scaleType,
              scaleFactor: Float(1).bitPattern, contentDescriptionID: 0), alphaWord: alpha))
        try begin(node)
      case 210:  // Host named action
        let action = ParsedNamedAction(
          nameTextID: try input.int("host action name text id"),
          valueType: try input.int("host action value type"),
          valueID: try input.int("host action value id"))
        guard
          let container = modifierContainers.reversed().first(where: { $0.node != nil }),
          let target = container.node, let gesture = container.gesture
        else {
          throw input.malformed("Host named action is outside a click modifier")
        }
        target.actions[gesture, default: []].append(.named(action))
      case 218:  // Integer expression change action
        let targetID = try input.longAsInt("integer action target id")
        let expressionID = try input.longAsInt("integer action expression id")
        guard
          let container = modifierContainers.reversed().first(where: { $0.node != nil }),
          let target = container.node, let gesture = container.gesture
        else { throw input.malformed("Integer action is outside a click modifier") }
        guard integerExpressions[expressionID] != nil else {
          throw input.malformed("Missing integer action expression \(expressionID)")
        }
        target.actions[gesture, default: []].append(
          .integerExpression(targetID: targetID, expressionID: expressionID))
      case 214:  // Container end
        if !modifierContainers.isEmpty {
          modifierContainers.removeLast()
        } else {
          // Modern AndroidX documents carry a final document-level terminator after the root.
          if stack.isEmpty, input.isAtEnd { break }
          guard !stack.isEmpty else { throw input.malformed("Unmatched container end") }
          stack.removeLast()
        }
      case 231:  // Minimum/maximum width
        let node = try currentNode(stack, input: input)
        node.minimumWidthWord = try input.word("minimum width")
        node.maximumWidthWord = try input.word("maximum width")
      case 232:  // Minimum/maximum height
        let node = try currentNode(stack, input: input)
        node.minimumHeightWord = try input.word("minimum height")
        node.maximumHeightWord = try input.word("maximum height")
      case 239:  // CoreText
        let textID = try input.int("core text id")
        let propertyCount = Int(try input.u16("core text property count"))
        guard propertyCount <= 26 else { throw input.malformed("Too many CoreText properties") }
        var integers: [Int: Int] = [:]
        var floats: [Int: UInt32] = [:]
        for _ in 0..<propertyCount {
          let id = try input.u8("core text property id")
          if [1, 2, 3, 4, 6, 8, 9, 10, 11, 15, 16, 17, 23, 24].contains(id) {
            integers[id] = try input.int("core text integer")
          } else if [5, 7, 12, 13, 14, 25, 26].contains(id) {
            floats[id] = try input.word("core text float")
          } else if [18, 19, 22].contains(id) {
            _ = try input.u8("core text boolean")
          } else if id == 20 || id == 21 {
            let count = Int(try input.u16("core text array count"))
            guard count <= maximumProperties else {
              throw input.malformed("CoreText array is too long")
            }
            for _ in 0..<count { _ = try input.int("core text array value") }
          } else {
            throw input.malformed("Unknown CoreText property \(id)")
          }
        }
        let componentID = integers[1] ?? -(textID + 1)
        let node = ParsedNode(kind: .text, componentID: componentID)
        let color =
          integers[4].flatMap { colors[$0] }
          ?? UInt32(bitPattern: Int32(integers[3] ?? -16_777_216))
        node.text = ParsedText(
          textID: textID, colorARGB: color, colorID: integers[4],
          sizeWord: floats[5] ?? Float(36).bitPattern,
          style: integers[6] ?? 0, weightWord: floats[7] ?? Float(400).bitPattern,
          familyID: integers[8] ?? -1, alignment: integers[9] ?? 1,
          overflow: integers[10] ?? 1, maximumLines: integers[11] ?? Int.max)
        try begin(node)
      case 250:  // Accessibility semantics
        let node = try currentNode(stack, input: input)
        let contentDescriptionID = try input.int("content description id")
        let role = try input.u8("semantic role")
        let textID = try input.int("semantic text id")
        let stateDescriptionID = try input.int("state description id")
        let mode = try input.u8("semantic mode")
        let enabled = try input.u8("semantic enabled")
        let clickable = try input.u8("semantic clickable")
        guard role <= 9, mode <= 2, enabled <= 1, clickable <= 1 else {
          throw input.malformed("Invalid accessibility semantics")
        }
        node.accessibility = ParsedAccessibility(
          contentDescriptionID: contentDescriptionID, role: role, textID: textID,
          stateDescriptionID: stateDescriptionID, mode: mode, isEnabled: enabled == 1,
          isClickable: clickable == 1)
      case 152:  // Draw arc
        let words = try (0..<6).map { _ in try input.word("draw arc value") }
        try currentNode(stack, input: input).commands.append(
          ParsedDrawCommand(kind: 15, words: words, paint: paint))
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: opcode, offset: opcodeOffset, reason: "operation family not migrated")
      }
    }
    guard stack.isEmpty else { throw input.malformed("Unclosed layout container") }
    guard let root, root.kind == .root else { throw input.malformed("Missing root component") }
    let needsContinuousFrames = expressions.contains { expression in
      expression.words.contains { word in
        NativeSwiftFloatExpression.referenceID(word).map { $0 == 1 || $0 == 30 } ?? false
      }
    }
    return ParsedDocument(
      width: width, height: height, density: density, densityBehavior: densityBehavior,
      root: root, nodes: nodes, texts: texts, floats: floats,
      colors: colors, integers: integers, integerExpressions: integerExpressions,
      namedVariables: namedVariables, expressions: expressions,
      componentValues: componentValues, colorAttributes: colorAttributes,
      colorExpressions: colorExpressions, images: images, textFromFloats: textFromFloats,
      textMerges: textMerges, idLists: idLists, textLookups: textLookups,
      needsContinuousFrames: needsContinuousFrames)
  }

  private static func applyPaint(_ words: [Int], to paint: inout ParsedPaint, input: WireReader)
    throws
  {
    var index = 0
    while index < words.count {
      let command = words[index]
      index += 1
      let encodedCommand = UInt32(bitPattern: Int32(command))
      let type = Int(encodedCommand & 0xffff)
      let highBits = Int(encodedCommand >> 16)
      let argumentCount: Int
      if type == 11 {
        guard index < words.count else { throw input.malformed("Truncated paint gradient") }
        let colorCount = words[index] & 0xff
        guard (1...16).contains(colorCount), index + 1 + colorCount < words.count else {
          throw input.malformed("Invalid paint gradient")
        }
        let stopCount = words[index + 1 + colorCount]
        guard stopCount == 0 || stopCount == colorCount else {
          throw input.malformed("Invalid paint gradient stops")
        }
        argumentCount = 1 + colorCount + 1 + stopCount + (highBits == 0 ? 5 : (highBits == 1 ? 4 : 2))
      } else {
        switch type {
      case 1, 4, 5, 9, 12, 13, 16, 19, 20, 22: argumentCount = 1
      case 24: argumentCount = 3
      case 7, 8, 10, 14, 15, 17, 18, 21: argumentCount = 0
      case 23: argumentCount = highBits * 2
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: 40, offset: input.offset, reason: "paint command \(type)")
        }
      }
      guard index + argumentCount <= words.count else { throw input.malformed("Truncated paint") }
      switch type {
      case 4:
        paint.colorARGB = UInt32(bitPattern: Int32(words[index]))
        paint.colorID = nil
      case 5: paint.strokeWidth = UInt32(bitPattern: Int32(words[index]))
      case 7: paint.strokeCap = highBits
      case 8: paint.isStroke = highBits == 1
      case 12:
        paint.alpha = min(max(Float(bitPattern: UInt32(bitPattern: Int32(words[index]))), 0), 1)
      case 15: paint.strokeJoin = highBits
      case 18: paint.blendMode = highBits
      case 19:
        paint.colorID = words[index]
      case 24:
        paint.textureImageID = words[index]
        paint.textureTileModeX = words[index + 1] & 0xf
        paint.textureTileModeY = (words[index + 1] >> 16) & 0xf
      default: break
      }
      index += argumentCount
    }
  }

  private static func currentNode(_ stack: [ParsedNode], input: WireReader) throws -> ParsedNode {
    guard let node = stack.last else { throw input.malformed("Modifier has no component") }
    return node
  }

  private static func argb(red: Float, green: Float, blue: Float, alpha: Float) -> UInt32 {
    func channel(_ value: Float) -> UInt32 {
      UInt32((min(max(value, 0), 1) * 255).rounded())
    }
    return channel(alpha) << 24 | channel(red) << 16 | channel(green) << 8 | channel(blue)
  }
}

private struct WireReader {
  private let bytes: [UInt8]
  private(set) var offset = 0

  init(_ data: Data) { bytes = Array(data) }
  var isAtEnd: Bool { offset == bytes.count }

  mutating func u8(_ field: String) throws -> Int {
    guard offset < bytes.count else { throw malformed("Unexpected end while reading \(field)") }
    defer { offset += 1 }
    return Int(bytes[offset])
  }

  mutating func signedU16(_ field: String) throws -> Int {
    let raw = try u16(field)
    return Int(Int16(bitPattern: raw))
  }

  mutating func u16(_ field: String) throws -> UInt16 {
    guard bytes.count - offset >= 2 else {
      throw malformed("Unexpected end while reading \(field)")
    }
    defer { offset += 2 }
    return UInt16(bytes[offset]) << 8 | UInt16(bytes[offset + 1])
  }

  mutating func int(_ field: String) throws -> Int {
    Int(Int32(bitPattern: try uint32(field)))
  }

  mutating func word(_ field: String) throws -> UInt32 {
    try uint32(field)
  }

  mutating func dimensionType(_ field: String) throws -> Int {
    let value = try int(field)
    guard (0...8).contains(value) else { throw malformed("Invalid \(field) \(value)") }
    return value
  }

  mutating func count(_ field: String, maximum: Int) throws -> Int {
    let value = try int(field)
    guard value >= 0, value <= maximum else {
      throw malformed("\(field) \(value) is outside 0...\(maximum)")
    }
    return value
  }

  /// Reads a float word, optionally refusing a reference outright.
  ///
  /// `requireLiteral: true` is the deliberate refusal a field keeps when it genuinely cannot be
  /// resolved later — a background colour written as channels rather than a colour id, where the
  /// document itself says which form it used. Fields that can carry a word to resolution time read
  /// it with `word(_:)` and resolve it against the frame's values instead.
  mutating func floatWord(_ field: String, requireLiteral: Bool) throws -> Float {
    let bits = try uint32(field)
    let value = Float(bitPattern: bits)
    if value.isNaN, requireLiteral {
      throw NativeSwiftCoreError.unsupported(
        opcode: -1, offset: offset - 4, reason: "dynamic float \(field)")
    }
    guard !requireLiteral || value.isFinite else { throw malformed("\(field) must be finite") }
    return value
  }

  mutating func utf8(_ field: String, maximum: Int) throws -> String {
    let length = try count("\(field) length", maximum: maximum)
    guard bytes.count - offset >= length else {
      throw malformed("Unexpected end while reading \(field)")
    }
    let value = String(bytes: bytes[offset..<(offset + length)], encoding: .utf8)
    offset += length
    guard let value else { throw malformed("\(field) is not valid UTF-8") }
    return value
  }

  mutating func data(_ field: String, maximum: Int) throws -> Data {
    let length = try count("\(field) length", maximum: maximum)
    guard bytes.count - offset >= length else {
      throw malformed("Unexpected end while reading \(field)")
    }
    defer { offset += length }
    return Data(bytes[offset..<(offset + length)])
  }

  mutating func longAsInt(_ field: String) throws -> Int {
    let high = UInt64(try word("\(field) high word"))
    let low = UInt64(try word("\(field) low word"))
    let value = high << 32 | low
    if (0x1_0000_0000...0x1_003f_ffff).contains(value) {
      return Int(value - 0x1_0000_0000)
    }
    guard value <= UInt64(Int.max) else { throw malformed("\(field) is outside Int range") }
    return Int(value)
  }

  mutating func utf8Bytes(_ field: String, length: Int) throws -> String {
    guard length >= 0, length <= NativeSwiftDocumentDecoder.maximumStringBytes,
      bytes.count - offset >= length
    else { throw malformed("Invalid \(field) length") }
    let value = String(bytes: bytes[offset..<(offset + length)], encoding: .utf8)
    offset += length
    guard let value else { throw malformed("\(field) is not valid UTF-8") }
    return value
  }

  func malformed(_ reason: String) -> NativeSwiftCoreError {
    .malformed(offset: offset, reason: reason)
  }

  private mutating func uint32(_ field: String) throws -> UInt32 {
    guard bytes.count - offset >= 4 else {
      throw malformed("Unexpected end while reading \(field)")
    }
    defer { offset += 4 }
    return UInt32(bytes[offset]) << 24 | UInt32(bytes[offset + 1]) << 16
      | UInt32(bytes[offset + 2]) << 8 | UInt32(bytes[offset + 3])
  }
}

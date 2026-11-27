import Foundation
@_spi(RemoteComposeController) import RcNativePlayerCore
import RcWireOperations
import Testing

@testable import RcCreation

/// Documents written by RcCreation and read back by the native document core: what a Swift author
/// composes is what a player gets.
@Suite struct RemoteDocumentRoundTripTests {
  /// The components under `node`, looking through the content wrappers layouts put around their
  /// children.
  private func components(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftNodeSnapshot] {
    node.children.flatMap { child in child.kind == .content ? components(child) : [child] }
  }

  private func open(_ document: RemoteDocument) throws -> NativeSwiftDocumentSession {
    try NativeSwiftDocumentSession.open(data: document.encodedData())
  }

  private func first(_ document: RemoteDocument) throws -> NativeSwiftNodeSnapshot {
    try #require(components(try open(document).snapshot().root).first)
  }

  /// The operations `document` encodes to, read back through the generated bindings.
  private func operations(_ document: RemoteDocument) throws -> [WireOperation] {
    try WireDocument.load(from: document.encode()).operations
  }

  @Test func headerRecordsSizeInPixelsAndTheScale() throws {
    let document = RemoteDocument(width: 200, height: 150, displayScale: 2) {
      RemoteColor.white
    }
    let snapshot = try open(document).snapshot()
    #expect(snapshot.width == 400)
    #expect(snapshot.height == 300)
    #expect(snapshot.density == 2)
  }

  @Test func stackOfTexts() throws {
    let document = RemoteDocument(width: 200, height: 100) {
      RemoteVStack(alignment: .center, spacing: 4) {
        RemoteText("Title").font(.system(size: 20, weight: .bold))
        RemoteText("Subtitle").foregroundStyle(.gray)
      }
      .padding(8)
      .frame(maxWidth: .infinity)
    }
    let stack = try first(document)
    #expect(stack.componentKind == "ColumnLayout")
    #expect(stack.spacing == 4)
    #expect(stack.horizontalPositioning == NativeSwiftPositioning.center)
    #expect(stack.padding.left == 8 && stack.padding.top == 8)
    let texts = components(stack)
    #expect(texts.map(\.text?.value) == ["Title", "Subtitle"])
    #expect(texts.first?.text?.size == 20)
    #expect(texts.first?.text?.weight == Float(RemoteFontWeight.bold.value))
    #expect(texts.last?.text?.colorARGB == UInt32(bitPattern: RemoteColorValue.gray.argb))
  }

  @Test func lengthsAreWrittenInPixelsAtTheDisplayScale() throws {
    let scale = 3.0
    let document = RemoteDocument(width: 100, height: 100, displayScale: scale) {
      RemoteColor.blue
        .frame(width: 40, height: 20)
        .padding(RemoteEdgeInsets(top: 1, leading: 2, bottom: 1, trailing: 2))
    }
    let box = try first(document)
    #expect(box.widthValue == Float(40 * scale))
    #expect(box.heightValue == Float(20 * scale))
    #expect(box.padding.left == Float(2 * scale) && box.padding.top == Float(1 * scale))
  }

  /// A rounded background is drawn behind the content, as AndroidX's `drawWithContent`, rather
  /// than clipping it; a rounded border keeps its own shape.
  @Test func roundedBackgroundDrawsBehindWithoutClipping() throws {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteZStack {}
        .frame(width: 50, height: 50)
        .background(.hex(0x3366CC), in: .roundedRectangle(cornerRadius: 6))
        .border(.black, width: 2, cornerRadius: 6)
    }
    let operations = try operations(document)
    #expect(!operations.contains { $0.opCode == .roundedClipRectModifierOperation })
    #expect(operations.contains { $0.opCode == .drawContentOperation })
    let box = try first(document)
    let fill = try #require(drawCommands(box).first)
    #expect(fill.colorARGB == 0xFF33_66CC)
    // The component's full size, from its bound width and height, with the corner radius.
    #expect(fill.values == [0, 0, 50, 50, 6, 6])
    #expect(box.borderARGB == 0xFF00_0000)
    #expect(box.borderWidth == 2)
  }

  /// A rounded background inside a padding covers only what the padding surrounds, as SwiftUI
  /// insets it: the padding goes on a box around the background's own.
  @Test func roundedBackgroundInsideAPaddingIsInset() throws {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteZStack {}
        .frame(width: 40, height: 40)
        .background(.red, in: .roundedRectangle(cornerRadius: 4))
        .padding(5)
    }
    let outer = try first(document)
    #expect(outer.padding.left == 5 && outer.padding.top == 5)
    let fill = try #require(drawCommands(outer).first)
    #expect(fill.values == [0, 0, 40, 40, 4, 4])
  }

  @Test func flexWeightsShareAStack() throws {
    let document = RemoteDocument(width: 300, height: 50) {
      RemoteHStack(alignment: .center, distribution: .spaceBetween) {
        RemoteColor.red.frame(height: 10).flexWeight(1)
        RemoteColor.blue.frame(height: 10).flexWeight(2)
      }
      .frame(maxWidth: .infinity)
    }
    let stack = try first(document)
    #expect(stack.componentKind == "RowLayout")
    #expect(stack.horizontalPositioning == NativeSwiftPositioning.spaceBetween)
    #expect(stack.verticalPositioning == NativeSwiftPositioning.center)
    let children = components(stack)
    #expect(
      children.map(\.widthType) == [
        NativeSwiftDimensionType.weight, NativeSwiftDimensionType.weight,
      ])
    #expect(children.map(\.widthValue) == [1, 2])
  }

  @Test func aSpacerTakesTheStacksLeftoverSpace() throws {
    let document = RemoteDocument(width: 300, height: 50) {
      RemoteVStack {
        RemoteText("Top")
        RemoteSpacer()
        RemoteText("Bottom")
      }
    }
    let spacer = try #require(components(try first(document)).dropFirst().first)
    #expect(spacer.heightType == NativeSwiftDimensionType.weight)
  }

  @Test func textFollowsAVariable() throws {
    let status = RemoteVariable("status", default: "Idle")
    let document = RemoteDocument(width: 200, height: 50) { RemoteText(status) }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.text?.value == "Idle")
    #expect(session.setString("Running", for: status.name))
    #expect(components(try session.snapshot().root).first?.text?.value == "Running")
  }

  @Test func interpolationFormatsLiveNumbers() throws {
    let count = RemoteVariable("count", default: 3.0)
    let label = RemoteVariable("label", default: "Items")
    let document = RemoteDocument(width: 200, height: 50) {
      RemoteText("\(label): \(count, decimals: 0) of \(10)")
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.text?.value == "Items: 3 of 10")
    #expect(session.setFloat(12, for: count.name))
    #expect(components(try session.snapshot().root).first?.text?.value == "Items: 12 of 10")
  }

  @Test func decimalsAreZeroPadded() throws {
    let price = RemoteVariable("price", default: 1.5)
    let document = RemoteDocument(width: 200, height: 50) { RemoteText("\(price, decimals: 2)") }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.text?.value == "1.50")
    #expect(session.setFloat(12, for: price.name))
    #expect(components(try session.snapshot().root).first?.text?.value == "12.00")
  }

  @Test func oneVariableReadTwiceIsOneValue() throws {
    let document = RemoteDocument(width: 200, height: 50) {
      RemoteVStack {
        RemoteText(RemoteVariable("status", default: "Idle"))
        RemoteText(RemoteVariable("status", default: "Idle"))
      }
    }
    let session = try open(document)
    #expect(session.setString("Running", for: "status"))
    let stack = try #require(components(try session.snapshot().root).first)
    #expect(components(stack).map(\.text?.value) == ["Running", "Running"])
  }

  @Test func expressionDrivesWidth() throws {
    let progress = RemoteVariable("progress", default: 0.25)
    let scale = 2.0
    let document = RemoteDocument(width: 200, height: 50, displayScale: scale) {
      RemoteColor.blue.frame(width: progress * 100, height: 8)
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.widthValue == Float(25 * scale))
    #expect(session.setFloat(0.5, for: progress.name))
    #expect(components(try session.snapshot().root).first?.widthValue == Float(50 * scale))
  }

  @Test func visibilityFollowsAVariable() throws {
    let shown = RemoteVariable("shown", default: RemoteVisibility.gone)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteColor.red.frame(width: 10, height: 10).visibility(shown)
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.visibility == NativeSwiftVisibility.gone)
    #expect(session.setInteger(NativeSwiftVisibility.visible, for: shown.name))
    #expect(
      components(try session.snapshot().root).first?.visibility == NativeSwiftVisibility.visible)
  }

  @Test func colorVariableIsAnId() throws {
    let accent = RemoteVariable("accent", default: RemoteColorValue.hex(0xFF0000))
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteColor.variable(accent).frame(width: 10, height: 10)
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.backgroundARGB == 0xFFFF_0000)
    #expect(session.setColor(0xFF00_FF00, for: accent.name))
    #expect(components(try session.snapshot().root).first?.backgroundARGB == 0xFF00_FF00)
  }

  /// A text colour that is an id (a variable here, a theme pair by default) is the dynamic-colour
  /// flag on `TextLayout`, which the native player resolves like any other colour id.
  @Test func textColorFollowsAVariable() throws {
    let accent = RemoteVariable("accent", default: RemoteColorValue.hex(0xFF0000))
    let document = RemoteDocument(width: 100, height: 50) {
      RemoteText("Alert").foregroundStyle(.variable(accent))
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.text?.colorARGB == 0xFFFF_0000)
    #expect(session.setColor(0xFF00_FF00, for: accent.name))
    #expect(components(try session.snapshot().root).first?.text?.colorARGB == 0xFF00_FF00)
  }

  @Test func adaptiveColorIsAColorTheme() throws {
    let light = RemoteColorValue.hex(0xF5F5F7)
    let dark = RemoteColorValue.hex(0x1C1C1E)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteColor.adaptive(light: light, dark: dark)
    }
    let themes = try operations(document).compactMap {
      if case .colorTheme(let theme) = $0 { theme } else { nil }
    }
    #expect(themes.count == 1)
    #expect(themes.first?.lightModeFallback == light.argb)
    #expect(themes.first?.darkModeFallback == dark.argb)
    // A player with no theme resolves the light fallback.
    #expect(try first(document).backgroundARGB == UInt32(bitPattern: light.argb))
  }

  @Test func accessibilityModifiersMergeIntoOneSemantics() throws {
    let document = RemoteDocument(width: 100, height: 100, accessibilityLabel: "Upload card") {
      RemoteColor.blue
        .frame(width: 40, height: 8)
        .accessibilityLabel("Upload progress")
        .accessibilityValue("62 percent")
        .accessibilityRole(.image)
    }
    let operations = try operations(document)
    let semantics = operations.compactMap {
      if case .coreSemantics(let semantics) = $0 { semantics } else { nil }
    }
    try #require(semantics.count == 1)
    let texts = Dictionary(
      operations.compactMap {
        if case .textData(let text) = $0 { (text.textId, text.text) } else { nil }
      }, uniquingKeysWith: { first, _ in first })
    #expect(texts[semantics[0].contentDescriptionId] == "Upload progress")
    #expect(texts[semantics[0].stateDescriptionId] == "62 percent")
    #expect(Int32(Int8(bitPattern: semantics[0].role)) == CoreSemantics.roleImage)
    #expect(
      try WireDocument.load(from: document.encode()).properties?.contains {
        $0.stringValue == "Upload card"
      } == true)
  }

  @Test func aTextStyleScalesWithTheReadersTextSize() throws {
    let scale = 2.0
    let document = RemoteDocument(width: 200, height: 100, displayScale: scale) {
      RemoteVStack {
        RemoteText("Styled").font(.headline)
        RemoteText("Fixed").font(.system(size: 17))
      }
    }
    let texts = components(try first(document))
    // At the default text size a style is its nominal size.
    #expect(texts.map(\.text?.size) == [Float(17 * scale), Float(17 * scale)])
    #expect(texts.first?.text?.weight == Float(RemoteFontWeight.semibold.value))
    // The styled size is an expression over the player's font size; the fixed one is a literal.
    let sizes = try operations(document).compactMap {
      if case .textLayout(let text) = $0 { text.fontSize } else { nil }
    }
    #expect(sizes.map(\.isNaN) == [true, false])
  }

  @Test func tapRunsActions() throws {
    let tab = RemoteVariable("tab", default: 0)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteColor.red
        .frame(width: 40, height: 40)
        .onTap(.host(id: 7), .host("opened"), .set(tab, to: 2))
    }
    let session = try open(document)
    let box = try #require(components(try session.snapshot().root).first)
    #expect(box.isClickable)
    let events = try #require(try session.click(componentID: box.componentID, timeSeconds: 0))
    #expect(events.contains(.action(id: 7)))
    #expect(
      events.contains { if case .namedAction(name: "opened", _) = $0 { true } else { false } })
    #expect(session.writtenNamedValues().contains { $0.name == tab.name })
  }

  private func drawCommands(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftDrawCommandSnapshot] {
    node.commands + node.children.flatMap(drawCommands)
  }

  @Test func canvasDraws() throws {
    let scale: Float = 2
    let document = RemoteDocument(width: 100, height: 100, displayScale: Double(scale)) {
      RemoteCanvas { context, _ in
        context.fill(.rect(RemoteRect(x: 0, y: 0, width: 40, height: 40)), with: .red)
        context.stroke(
          .circle(center: RemotePoint(x: 20, y: 20), radius: 10), with: .blue, lineWidth: 2)
        context.stroke(
          .line(from: .zero, to: RemotePoint(x: 40, y: 40)), with: .black)
      }
      .frame(width: 40, height: 40)
    }
    let canvas = try first(document)
    #expect(canvas.componentKind == "CanvasLayout")
    let commands = drawCommands(canvas)
    try #require(commands.count == 3, "kinds: \(commands.map(\.kind)) tree: \(canvas)")
    #expect(commands.first?.colorARGB == UInt32(bitPattern: RemoteColorValue.red.argb))
    #expect(commands.first?.values == [0, 0, 40 * scale, 40 * scale])
    #expect(commands[1].isStroke && commands[1].strokeWidth == 2 * scale)
    #expect(commands[1].values == [20 * scale, 20 * scale, 10 * scale])
  }

  @Test func canvasSizeIsLive() throws {
    let scale: Float = 2
    let document = RemoteDocument(width: 100, height: 100, displayScale: Double(scale)) {
      RemoteVStack {
        RemoteCanvas { context, size in
          context.fill(.circle(center: size.center, radius: 5), with: .red)
        }
        .frame(width: 40, height: 20)
        RemoteCanvas { context, size in
          context.fill(.circle(center: size.center, radius: 5), with: .red)
        }
        .frame(width: 60, height: 30)
      }
    }
    let canvases = components(try first(document))
    // Each canvas reads its own size, even through the same expression.
    #expect(drawCommands(canvases[0]).first?.values == [20 * scale, 10 * scale, 5 * scale])
    #expect(drawCommands(canvases[1]).first?.values == [30 * scale, 15 * scale, 5 * scale])
  }

  /// A stroke writes its cap and join even when they are the defaults, as a paint's state carries
  /// over from the draw before it.
  @Test func aDefaultStrokeAfterARoundOneIsButt() throws {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteCanvas { context, _ in
        context.stroke(
          .line(from: RemotePoint(x: 0, y: 0), to: RemotePoint(x: 10, y: 10)), with: .black,
          lineWidth: 4, lineCap: .round, lineJoin: .round)
        context.stroke(
          .line(from: RemotePoint(x: 0, y: 10), to: RemotePoint(x: 10, y: 0)), with: .black,
          lineWidth: 4)
      }
      .frame(width: 20, height: 20)
    }
    let strokes = drawCommands(try first(document))
    #expect(strokes.map(\.strokeCap) == [.round, .butt])
    #expect(strokes.map(\.strokeJoin) == [.round, .miter])
  }

  @Test func modifiersApplyToCustomViews() throws {
    struct Badge: RemoteView {
      let label: String
      var body: some RemoteView {
        RemoteText(label)
          .font(.caption)
          .foregroundStyle(.white)
          .padding(4)
          .background(.red, in: .roundedRectangle(cornerRadius: 8))
      }
    }
    let labels = ["One", "Two", "Three"]
    let showFooter = false
    let document = RemoteDocument(width: 300, height: 100) {
      RemoteHStack(spacing: 4) {
        RemoteForEach(labels) { label in Badge(label: label).padding(2) }
        if showFooter { RemoteText("never written") }
      }
    }
    func texts(_ node: NativeSwiftNodeSnapshot) -> [String] {
      (node.text.map { [$0.value] } ?? []) + node.children.flatMap(texts)
    }
    #expect(texts(try first(document)) == labels)
    // As SwiftUI applies them, outermost first: the outer padding on a box, the rounded
    // background on a box inside it (drawing behind its content, clipping nothing), then the
    // text with its own padding.
    let operations = try operations(document)
    #expect(!operations.contains { $0.opCode == .roundedClipRectModifierOperation })
    #expect(operations.filter { $0.opCode == .drawContentOperation }.count == labels.count)
    let text = try #require(
      operations.firstIndex { if case .textLayout = $0 { true } else { false } })
    let content = try #require(operations[text...].firstIndex { $0.opCode == .layoutContent })
    #expect(operations[(text + 1)..<content].map(\.opCode) == [.paddingModifierOperation])
    let marker = try #require(operations[..<text].lastIndex { $0.opCode == .drawContentOperation })
    #expect(operations[marker + 1].opCode == .layoutContent)
    #expect(operations[marker + 2].opCode == .canvasOperations)
  }

  @Test func conflictingVariablesFailToEncode() {
    let document = RemoteDocument(width: 100, height: 50) {
      RemoteVStack {
        RemoteText(RemoteVariable("status", default: "Idle"))
        RemoteText(RemoteVariable("status", default: "Busy"))
      }
    }
    #expect(throws: RemoteEncodingError.conflictingVariable(name: "status")) {
      try document.encode()
    }
  }

  @Test func invalidValuesFailToEncode() {
    #expect(throws: RemoteEncodingError.invalidDisplayScale(0)) {
      try RemoteDocument(width: 10, height: 10, displayScale: 0) { RemoteText("x") }.encode()
    }
    #expect(throws: RemoteEncodingError.invalidFontWeight(0)) {
      try RemoteDocument(width: 10, height: 10) {
        RemoteText("x").fontWeight(RemoteFontWeight(0))
      }.encode()
    }
    #expect(throws: RemoteEncodingError.invalidFlexWeight(0)) {
      try RemoteDocument(width: 10, height: 10) {
        RemoteHStack { RemoteText("x").flexWeight(0) }
      }.encode()
    }
  }

  /// The bindings hold each field to the limit a reader enforces, so text too long to load fails
  /// to encode instead of producing a document no player opens.
  @Test func textOverTheReadersLimitFailsToEncode() {
    let length = WireLimits.maxStringSize + 1
    let document = RemoteDocument(width: 10, height: 10) {
      RemoteText(String(repeating: "a", count: length))
    }
    #expect(
      throws: RemoteEncodingError.wire(
        .lengthExceedsLimit(
          type: "TextData", field: "text", length: length, limit: WireLimits.maxStringSize))
    ) { try document.encode() }
  }

  @Test func nonFiniteValuesFailToEncode() {
    for document in [
      RemoteDocument(width: 10, height: 10) {
        RemoteColor.red.frame(width: RemoteFloat(Double.nan))
      },
      RemoteDocument(width: 10, height: 10) { RemoteColor.red.zIndex(.infinity) },
      RemoteDocument(width: 10, height: 10) { RemoteVStack(spacing: .nan) { RemoteText("x") } },
      RemoteDocument(width: 10, height: 10) {
        RemoteColor.red.border(.black, width: .infinity)
      },
    ] {
      #expect(throws: RemoteEncodingError.nonFiniteValue) { try document.encode() }
    }
  }

  @Test func integersOutsideThirtyTwoBitsFailToEncode() {
    let tooLarge = Int(Int32.max) + 1
    #expect(throws: RemoteEncodingError.integerOutOfRange(tooLarge)) {
      try RemoteDocument(width: 10, height: 10) {
        RemoteColor.red.onTap(.host(id: tooLarge))
      }.encode()
    }
    #expect(throws: RemoteEncodingError.integerOutOfRange(tooLarge)) {
      try RemoteDocument(width: 10, height: 10) {
        RemoteColor.red.visibility(.visible).onTap(
          .set(RemoteVariable("n", default: 0), to: tooLarge))
      }.encode()
    }
  }

  /// A tappable view's semantics say it is clickable.
  @Test func semanticsOfATappableViewAreClickable() throws {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteColor.blue.frame(width: 40, height: 40)
        .onTap(.host("go"))
        .accessibilityLabel("Go")
    }
    let semantics = try operations(document).compactMap {
      if case .coreSemantics(let semantics) = $0 { semantics } else { nil }
    }
    #expect(semantics.map(\.clickable) == [true])
  }

  @Test func colorComponentsAreClampedWhenMade() throws {
    let color = RemoteColorValue(red: 2, green: -1, blue: .nan, opacity: 0.5)
    #expect([color.red, color.green, color.blue, color.opacity] == [1, 0, 0, 0.5])
    // A background writes the clamped channels too, not only text and paint.
    let backgrounds = try operations(
      RemoteDocument(width: 10, height: 10) { RemoteColor(color) }
    ).compactMap {
      if case .backgroundModifierOperation(let background) = $0 { background } else { nil }
    }
    #expect(backgrounds.map { [$0.r, $0.g, $0.b, $0.a] } == [[1, 0, 0, 0.5]])
  }

  @Test func doubleVariablesCombine() throws {
    let a = RemoteVariable("a", default: 3.0)
    let b = RemoteVariable("b", default: 4.0)
    let document = RemoteDocument(width: 200, height: 50) {
      RemoteText(
        "\(a + b, decimals: 0) \(-a, decimals: 0) \((a - b).magnitude, decimals: 0) \(b.clamped(to: 0...2), decimals: 0)"
      )
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.text?.value == "7 -3 1 2")
  }

  /// Text styles come from the environment, so they apply on a container, after a layout
  /// modifier, and on a custom view, as SwiftUI's do; the innermost setting wins.
  @Test func textStylesComeFromTheEnvironment() throws {
    struct Caption: RemoteView {
      let text: String
      var body: some RemoteView { RemoteText(text).padding(2) }
    }
    let scale = 2.0
    let document = RemoteDocument(width: 300, height: 200, displayScale: scale) {
      RemoteVStack {
        RemoteText("Container")
        RemoteText("Inner").font(.system(size: 20))
        RemoteText("After padding").padding(4).foregroundStyle(.white)
        Caption(text: "Custom").italic()
      }
      .font(.system(size: 13))
      .fontWeight(.bold)
    }
    let texts = components(try #require(components(try open(document).snapshot().root).first))
      .flatMap { $0.text == nil ? components($0) : [$0] }
      .compactMap(\.text)
    #expect(texts.map(\.size) == [13, 20, 13, 13].map { Float($0 * scale) })
    #expect(texts.allSatisfy { $0.weight == Float(RemoteFontWeight.bold.value) })
    #expect(texts[2].colorARGB == 0xFFFF_FFFF)
    #expect(texts.map(\.style) == [0, 0, 0, 1])
  }

  /// A weight set outside a font keeps applying when an inner `font` changes the size.
  @Test func fontWeightSurvivesAnInnerFont() throws {
    let document = RemoteDocument(width: 100, height: 40) {
      RemoteText("x").font(.system(size: 17)).fontWeight(.bold)
    }
    #expect(
      try first(document).text?.weight == Float(RemoteFontWeight.bold.value))
  }

  @Test func namedColorsAreLightDarkPairs() throws {
    let themes = try operations(RemoteDocument(width: 10, height: 10) { RemoteColor.blue })
      .compactMap { if case .colorTheme(let theme) = $0 { theme } else { nil } }
    #expect(themes.map(\.lightModeFallback) == [RemoteColorValue.hex(0x007AFF).argb])
    #expect(themes.map(\.darkModeFallback) == [RemoteColorValue.hex(0x0A84FF).argb])
  }

  @Test func identicalValuesAreDeclaredOnce() throws {
    func texts(_ document: RemoteDocument) throws -> [String] {
      try operations(document).compactMap {
        if case .textData(let text) = $0 { text.text } else { nil }
      }
    }
    let twice = RemoteDocument(width: 10, height: 10) {
      RemoteVStack {
        RemoteText("same")
        RemoteText("same")
      }
    }
    #expect(try texts(twice) == ["same"])
  }
}

import Foundation
import RcCreationAppleUI
import RcCreationFoundation
@_spi(RemoteComposeController) import RcNativePlayerCore
import RcWireOperations
import Testing

@testable import RcCreation

/// The Apple component libraries, read back through the native player: what a tap does, what a
/// widget's document declares, and the colours a theme gives.
@Suite struct RemoteAppleUITests {
  private func open(_ document: RemoteDocument) throws -> NativeSwiftDocumentSession {
    try NativeSwiftDocumentSession.open(data: document.encodedData())
  }

  private func operations(_ document: RemoteDocument) throws -> [WireOperation] {
    try WireDocument.load(from: document.encode()).operations
  }

  /// Every node under `node`, depth first.
  private func flatten(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftNodeSnapshot] {
    [node] + node.children.flatMap(flatten)
  }

  private func nodes(_ session: NativeSwiftDocumentSession) throws -> [NativeSwiftNodeSnapshot] {
    flatten(try session.snapshot().root)
  }

  /// Whether each node painted `argb` is shown: neither it nor an ancestor is gone.
  private func shown(_ argb: UInt32, in session: NativeSwiftDocumentSession) throws -> [Bool] {
    func walk(_ node: NativeSwiftNodeSnapshot, hidden: Bool) -> [Bool] {
      let hidden = hidden || node.visibility == NativeSwiftVisibility.gone
      return (node.backgroundARGB == argb ? [!hidden] : [])
        + node.children.flatMap { walk($0, hidden: hidden) }
    }
    return walk(try session.snapshot().root, hidden: false)
  }

  /// The colours a rounded background or a canvas paints: what each node draws.
  private func drawnColors(_ session: NativeSwiftDocumentSession) throws -> [UInt32] {
    try nodes(session).flatMap { node in
      node.commands.map(\.colorARGB)
    }
  }

  /// The nodes the player shows: none under a gone node, as a switch's other states are.
  private func shownNodes(_ session: NativeSwiftDocumentSession) throws
    -> [NativeSwiftNodeSnapshot]
  {
    func walk(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftNodeSnapshot] {
      node.visibility == NativeSwiftVisibility.gone ? [] : [node] + node.children.flatMap(walk)
    }
    return walk(try session.snapshot().root)
  }

  private func texts(_ session: NativeSwiftDocumentSession) throws -> [String] {
    try shownNodes(session).compactMap(\.text?.value)
  }

  @Test func toggleFlipsWhenItsRowIsTapped() throws {
    let wifi = RemoteVariable("wifi", default: false)
    let session = try open(
      RemoteDocument(width: 390, height: 100) { RemoteToggle("Wi-Fi", isOn: wifi) })
    func row() throws -> NativeSwiftNodeSnapshot {
      try #require(try shownNodes(session).first { $0.isClickable })
    }
    // The on state's green track is shown only while the switch is on, and VoiceOver reads the
    // state as the toggle's value.
    let green: UInt32 = 0xFF34_C759
    #expect(try shown(green, in: session) == [false])
    #expect(try row().accessibility?.stateDescription == "Off")
    _ = try session.click(componentID: try row().componentID, timeSeconds: 0)
    #expect(try shown(green, in: session) == [true])
    #expect(try row().accessibility?.stateDescription == "On")
    #expect(session.writtenNamedValues().contains { $0.name == wifi.name })
  }

  /// A disabled section's controls take no taps and draw dimmed.
  @Test func disabledControlsTakeNoTaps() throws {
    let document = RemoteDocument(width: 390, height: 400) {
      RemoteList {
        RemoteSection("Locked") {
          RemoteToggle("Wi-Fi", isOn: RemoteVariable("wifi", default: true))
          RemoteStepper("Guests", value: RemoteVariable("guests", default: 2.0), in: 1...8)
          RemoteSegmentedPicker(
            selection: RemoteVariable("range", default: 0), options: ["Day", "Week"])
          RemoteNavigationRow("VPN", action: .host("vpn"))
          RemoteButton("Go", action: .host("go")).buttonStyle(.borderedProminent)
        }
        .disabled()
      }
    }
    let session = try open(document)
    #expect(try nodes(session).filter(\.isClickable).isEmpty)
    // The toggle's on track is not the green it draws when enabled.
    #expect(try shown(0xFF34_C759, in: session).allSatisfy { !$0 })
  }

  @Test func segmentedPickerSelectsTheTappedSegment() throws {
    let selection = RemoteVariable("range", default: 0)
    let session = try open(
      RemoteDocument(width: 390, height: 100) {
        RemoteSegmentedPicker(selection: selection, options: ["Day", "Week", "Month"])
      })
    // The white selection behind exactly one segment.
    func selected() throws -> [Bool] { try shown(0xFFFF_FFFF, in: session) }
    #expect(try selected() == [true, false, false])
    let segments = try nodes(session).filter(\.isClickable)
    try #require(segments.count == 3)
    _ = try session.click(componentID: segments[2].componentID, timeSeconds: 0)
    #expect(try selected() == [false, false, true])
  }

  @Test func stepperStaysInItsRange() throws {
    let guests = RemoteVariable("guests", default: 7.0)
    let session = try open(
      RemoteDocument(width: 390, height: 100) {
        RemoteStepper("Guests: \(guests)", value: guests, in: 1...8)
      })
    let buttons = try nodes(session).filter(\.isClickable)
    try #require(buttons.count == 2)
    #expect(try texts(session).contains("Guests: 7"))
    _ = try session.click(componentID: buttons[1].componentID, timeSeconds: 0)
    _ = try session.click(componentID: buttons[1].componentID, timeSeconds: 1)
    #expect(try texts(session).contains("Guests: 8"))
    _ = try session.click(componentID: buttons[0].componentID, timeSeconds: 2)
    #expect(try texts(session).contains("Guests: 7"))
  }

  @Test func buttonRunsItsActionUnlessDisabled() throws {
    let document = RemoteDocument(width: 390, height: 100) {
      RemoteVStack {
        RemoteButton("Play", symbol: .playFill, action: .host(id: 4))
          .buttonStyle(.borderedProminent)
        RemoteButton("Delete", role: .destructive, action: .host(id: 5)).disabled()
      }
    }
    let session = try open(document)
    let clickable = try nodes(session).filter(\.isClickable)
    try #require(clickable.count == 1)
    let events = try #require(
      try session.click(componentID: clickable[0].componentID, timeSeconds: 0))
    #expect(events.contains(.action(id: 4)))
    // The prominent button is the tint on white text: the default tint is system blue.
    #expect(try drawnColors(session).contains(0xFF00_7AFF))
  }

  @Test func sectionSeparatesItsRows() throws {
    let document = RemoteDocument(width: 390, height: 400) {
      RemoteList {
        RemoteSection("Network", footer: "Footer") {
          RemoteToggle("Wi-Fi", isOn: RemoteVariable("wifi", default: true))
          RemoteNavigationRow("Bluetooth", symbol: .boltFill, value: "On", action: .host("bt"))
          RemoteNavigationRow("VPN", action: .host("vpn"))
        }
      }
    }
    let session = try open(document)
    #expect(try texts(session) == ["Network", "Wi-Fi", "Bluetooth", "On", "VPN", "Footer"])
    // Two separators between three rows: half-point lines in the separator colour.
    let separators = try nodes(session).filter { $0.heightValue == 0.5 }
    #expect(separators.count == 2)
  }

  @Test func aListScrollsVertically() throws {
    let document = RemoteDocument(width: 390, height: 200) {
      RemoteList {
        RemoteSection("Rows") {
          for index in 1...20 { RemoteNavigationRow("Row \(index)", action: .host("r")) }
        }
      }
    }
    let scrolled = try nodes(open(document)).filter { $0.scrollDirection != nil }
    #expect(scrolled.map(\.scrollDirection) == [.vertical])
  }

  @Test func themeAndTintReachTheControls() throws {
    let document = RemoteDocument(width: 390, height: 100) {
      RemoteButton("Go", action: .host("go"))
        .buttonStyle(.borderedProminent)
        .tint(.hex(0x123456))
    }
    #expect(try drawnColors(try open(document)).contains(0xFF12_3456))
  }

  @Test func widgetFamiliesHaveTheirSizes() throws {
    for family in RemoteWidgetFamily.allCases {
      let widget = RemoteWidget(family: family, displayScale: 2) { RemoteText("Hi") }
      let snapshot = try NativeSwiftDocumentSession.open(data: Data(try widget.encode())).snapshot()
      #expect(snapshot.width == Int(family.size.width * 2))
      #expect(snapshot.height == Int(family.size.height * 2))
    }
  }

  @Test func widgetURLIsANamedHostAction() throws {
    let widget = RemoteWidget(family: .systemSmall, url: "weather://today") { RemoteText("21°") }
    let session = try open(widget.document)
    let container = try #require(try nodes(session).first(where: \.isClickable))
    let events = try #require(try session.click(componentID: container.componentID, timeSeconds: 0))
    #expect(
      events.contains {
        if case .namedAction(name: RemoteWidget.openURLAction, _) = $0 { true } else { false }
      })
  }

  @Test func accessoryWidgetsAreVibrant() throws {
    let widget = RemoteWidget(family: .accessoryRectangular) { RemoteText("Next") }
    let text = try #require(try nodes(try open(widget.document)).first { $0.text != nil })
    #expect(text.text?.colorARGB == 0xFFFF_FFFF)
  }

  @Test func clockShowsTheTime() throws {
    func textFromFloats(_ document: RemoteDocument) throws -> [TextFromFloat] {
      try operations(document).compactMap {
        if case .textFromFloat(let text) = $0 { text } else { nil }
      }
    }
    let hour24 = try textFromFloats(
      RemoteDocument(width: 100, height: 40) { RemoteText.time(.hourMinute24) })
    #expect(hour24.count == 2)
    #expect(hour24.allSatisfy { $0.flags & TextFromFloat.padPreZero == TextFromFloat.padPreZero })
    #expect(
      hour24[0].value.bitPattern == RemoteWriter.reference(RemoteContextIds.idTimeInHr).bitPattern)
    // The native player resolves it to a time of day.
    let session = try open(
      RemoteDocument(width: 100, height: 40) { RemoteText.time(.hourMinute24) })
    let shown = try #require(try texts(session).first)
    #expect(shown.count == 5 && shown.contains(":"))
  }

  @Test func gaugeAndProgressDrawLiveArcs() throws {
    let value = RemoteVariable("value", default: 0.5)
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteVStack {
        RemoteGauge(value: RemoteFloat(value), label: "Load", currentValueLabel: "\(value)")
        RemoteProgressView(value: RemoteFloat(value)).progressViewStyle(.circular)
        RemoteProgressView()
      }
    }
    let arcs = try operations(document).compactMap {
      if case .drawArc(let arc) = $0 { arc } else { nil }
    }
    // The gauge's track and value, the ring's value, the spinner.
    #expect(arcs.count == 4)
    #expect(arcs.filter { $0.sweepAngle.isNaN }.count == 2)
    #expect(arcs.filter { $0.startAngle.isNaN }.count == 1)
    _ = try open(document).snapshot()
  }

  @Test func everyBuiltInSymbolParses() {
    let symbols: [RemoteSymbol] = [
      .chevronRight, .chevronLeft, .chevronDown, .chevronUp, .checkmark, .xmark, .plus, .minus,
      .circle, .circleFill, .checkmarkCircle, .xmarkCircle, .infoCircle, .ellipsis, .playFill,
      .pauseFill, .boltFill, .heartFill, .starFill, .bellFill,
    ]
    for symbol in symbols {
      #expect(!symbol.parts.isEmpty && symbol.parts.allSatisfy { !$0.path.isEmpty })
    }
  }

  @Test func imagesAreDeclaredOnce() throws {
    let png: [UInt8] = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteImage(png: png, pixelWidth: 2, pixelHeight: 2).frame(width: 20, height: 20)
        RemoteImage(png: png, pixelWidth: 2, pixelHeight: 2).scaledToFill()
      }
    }
    let operations = try operations(document)
    let bitmaps = operations.compactMap { if case .bitmapData(let data) = $0 { data } else { nil } }
    try #require(bitmaps.count == 1)
    #expect(bitmaps[0].width == 2 && bitmaps[0].height == 2 && bitmaps[0].bitmap == png)
    let layouts = operations.compactMap {
      if case .imageLayout(let layout) = $0 { layout } else { nil }
    }
    #expect(layouts.map(\.scaleType) == [ImageLayout.scaleFit, ImageLayout.scaleCrop])
    #expect(throws: RemoteEncodingError.invalidImageSize(width: 0, height: 2)) {
      try RemoteDocument(width: 10, height: 10) {
        RemoteImage(png: png, pixelWidth: 0, pixelHeight: 2)
      }.encode()
    }
  }

  @Test func adaptiveSystemColorsResolveToTheLightValue() throws {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteColor.systemGroupedBackground
    }
    #expect(try nodes(try open(document)).contains { $0.backgroundARGB == 0xFFF2_F2F7 })
  }
}

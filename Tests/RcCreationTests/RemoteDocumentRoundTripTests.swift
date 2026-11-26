import Foundation
import RcCreation
@_spi(RemoteComposeController) import RcNativePlayerCore
import Testing

/// Documents written by RcCreation and read back by the native document core: what a Swift author
/// composes is what a player gets.
@Suite struct RemoteDocumentRoundTripTests {
  /// The components under [node], looking through the content wrappers layouts put around their
  /// children.
  private func components(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftNodeSnapshot] {
    node.children.flatMap { child in child.kind == .content ? components(child) : [child] }
  }

  private func open(_ document: RemoteDocument) throws -> NativeSwiftDocumentSession {
    try NativeSwiftDocumentSession.open(data: document.encodedData())
  }

  @Test func headerRecordsSizeAndDensity() throws {
    let document = RemoteDocument(width: 400, height: 300, density: 2) {
      RemoteBox(modifier: .fillMaxSize())
    }
    let snapshot = try open(document).snapshot()
    #expect(snapshot.width == 400)
    #expect(snapshot.height == 300)
    #expect(snapshot.density == 2)
  }

  @Test func columnOfTexts() throws {
    let document = RemoteDocument(width: 200, height: 100) {
      RemoteColumn(
        modifier: .fillMaxWidth().padding(8),
        verticalArrangement: .spacedBy(4),
        horizontalAlignment: .center
      ) {
        RemoteText("Title", fontSize: 20, fontWeight: .bold)
        RemoteText("Subtitle", color: .gray)
      }
    }
    let snapshot = try open(document).snapshot()
    let column = try #require(components(snapshot.root).first)
    #expect(column.componentKind == "ColumnLayout")
    #expect(column.spacing == 4)
    #expect(column.horizontalPositioning == NativeSwiftPositioning.center)
    #expect(column.padding.left == 8 && column.padding.top == 8)
    let texts = components(column)
    #expect(texts.map(\.text?.value) == ["Title", "Subtitle"])
    #expect(texts.first?.text?.size == 20)
    #expect(texts.first?.text?.weight == 700)
    #expect(texts.last?.text?.colorARGB == 0xFF88_8888)
  }

  @Test func dimensionsAreWrittenInPixelsAtTheDocumentDensity() throws {
    let document = RemoteDocument(width: 300, height: 300, density: 3) {
      RemoteBox(modifier: .size(width: 40, height: 20).padding(horizontal: 2, vertical: 1))
    }
    let box = try #require(components(try open(document).snapshot().root).first)
    #expect(box.widthValue == 120)
    #expect(box.heightValue == 60)
    #expect(box.padding.left == 6 && box.padding.top == 3)
  }

  @Test func backgroundBorderAndClip() throws {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteBox(
        modifier: .size(50).clip(.rounded(6)).background(RemoteColor(rgb: 0x3366CC))
          .border(2, .black, cornerRadius: 6))
    }
    let box = try #require(components(try open(document).snapshot().root).first)
    #expect(box.backgroundARGB == 0xFF33_66CC)
    #expect(box.borderARGB == 0xFF00_0000)
    #expect(box.borderWidth == 2)
    #expect(box.cornerRadius == 6)
  }

  @Test func rowWeightsAndAlignment() throws {
    let document = RemoteDocument(width: 300, height: 50) {
      RemoteRow(
        modifier: .fillMaxWidth(), horizontalArrangement: .spaceBetween,
        verticalAlignment: .center
      ) {
        RemoteBox(modifier: .weight(1).height(10))
        RemoteBox(modifier: .weight(2).height(10))
      }
    }
    let row = try #require(components(try open(document).snapshot().root).first)
    #expect(row.componentKind == "RowLayout")
    #expect(row.horizontalPositioning == NativeSwiftPositioning.spaceBetween)
    #expect(row.verticalPositioning == NativeSwiftPositioning.center)
    let weights = components(row)
    #expect(
      weights.map(\.widthType) == [
        NativeSwiftDimensionType.weight, NativeSwiftDimensionType.weight,
      ])
    #expect(weights.map(\.widthValue) == [1, 2])
  }

  @Test func namedTextFollowsTheHost() throws {
    let document = RemoteDocument(width: 200, height: 50) {
      RemoteText(.named("status", "Idle"))
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.text?.value == "Idle")
    #expect(session.setString("Running", for: "status"))
    #expect(components(try session.snapshot().root).first?.text?.value == "Running")
  }

  @Test func formattedNumberAndConcatenation() throws {
    let count = RemoteFloat.named("count", 3)
    let document = RemoteDocument(width: 200, height: 50) {
      RemoteText("Items: " + RemoteString(count, decimals: 0))
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.text?.value == "Items: 3")
    #expect(session.setFloat(12, for: "count"))
    #expect(components(try session.snapshot().root).first?.text?.value == "Items: 12")
  }

  @Test func decimalsAreZeroPadded() throws {
    let document = RemoteDocument(width: 200, height: 50) {
      RemoteText(RemoteString(RemoteFloat.named("price", 1.5), decimals: 2))
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.text?.value == "1.50")
    #expect(session.setFloat(12, for: "price"))
    #expect(components(try session.snapshot().root).first?.text?.value == "12.00")
  }

  @Test func oneNameReadTwiceIsOneValue() throws {
    let status = RemoteString.named("status", "Idle")
    let document = RemoteDocument(width: 200, height: 50) {
      RemoteText(status)
      RemoteText(.named("status", "Idle"))
    }
    let session = try open(document)
    #expect(session.setString("Running", for: "status"))
    #expect(
      components(try session.snapshot().root).map(\.text?.value) == ["Running", "Running"])
  }

  @Test func expressionDrivesWidth() throws {
    let progress = RemoteFloat.named("progress", 0.25)
    let document = RemoteDocument(width: 400, height: 50, density: 2) {
      RemoteBox(modifier: .width(progress * 100).height(8).background(.blue))
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.widthValue == 50)
    #expect(session.setFloat(0.5, for: "progress"))
    #expect(components(try session.snapshot().root).first?.widthValue == 100)
  }

  @Test func visibilityFollowsANamedInteger() throws {
    let shown = RemoteInt.named("shown", RemoteVisibility.gone)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteBox(modifier: .size(10).visibility(shown))
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.visibility == NativeSwiftVisibility.gone)
    #expect(session.setInteger(RemoteVisibility.visible, for: "shown"))
    #expect(
      components(try session.snapshot().root).first?.visibility == NativeSwiftVisibility.visible)
  }

  @Test func namedColorIsAnId() throws {
    let accent = RemoteColor.named("accent", .red)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteBox(modifier: .size(10).background(accent))
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.backgroundARGB == 0xFFFF_0000)
    #expect(session.setColor(0xFF00_FF00, for: "accent"))
    #expect(components(try session.snapshot().root).first?.backgroundARGB == 0xFF00_FF00)
  }

  @Test func clickRunsActions() throws {
    let tab = RemoteInt.named("tab", 0)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteBox(modifier: .size(40).clickable(.host(7), .host("opened"), .set(tab, to: 2)))
    }
    let session = try open(document)
    let box = try #require(components(try session.snapshot().root).first)
    #expect(box.isClickable)
    let events = try #require(try session.click(componentID: box.componentID, timeSeconds: 0))
    #expect(events.contains(.action(id: 7)))
    #expect(
      events.contains { if case .namedAction(name: "opened", _) = $0 { true } else { false } })
    #expect(session.writtenNamedValues().contains { $0.name == "tab" })
  }

  @Test func canvasDraws() throws {
    let document = RemoteDocument(width: 100, height: 100, density: 2) {
      RemoteCanvas(modifier: .size(40)) { scope in
        scope.drawRect(.red, size: RemoteSize(40, 40))
        scope.drawCircle(.blue, radius: 10, center: RemotePoint(20, 20), style: .stroke(width: 2))
        scope.drawLine(.black, start: RemotePoint(0, 0), end: RemotePoint(40, 40))
      }
    }
    let canvas = try #require(components(try open(document).snapshot().root).first)
    #expect(canvas.componentKind == "CanvasLayout")
    func allCommands(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftDrawCommandSnapshot] {
      node.commands + node.children.flatMap(allCommands)
    }
    let commands = allCommands(canvas)
    try #require(commands.count == 3, "kinds: \(commands.map(\.kind)) tree: \(canvas)")
    #expect(commands.first?.colorARGB == 0xFFFF_0000)
    #expect(commands.first?.values == [0, 0, 80, 80])
    #expect(commands[1].isStroke && commands[1].strokeWidth == 4)
    #expect(commands[1].values == [40, 40, 20])
  }

  @Test func customViewsComposeLikeComposables() throws {
    struct Badge: RemoteView {
      let label: String
      var body: some RemoteView {
        RemoteBox(modifier: .background(.red, shape: .rounded(8)).padding(4)) {
          RemoteText(label, color: .white, fontSize: 12)
        }
      }
    }
    let labels = ["One", "Two", "Three"]
    let showFooter = false
    let document = RemoteDocument(width: 300, height: 100) {
      RemoteRow(horizontalArrangement: .spacedBy(4)) {
        for label in labels { Badge(label: label) }
        if showFooter { RemoteText("never written") }
      }
    }
    let row = try #require(components(try open(document).snapshot().root).first)
    let badges = components(row)
    #expect(badges.count == 3)
    #expect(badges.compactMap { components($0).first?.text?.value } == labels)
  }

  @Test func identicalValuesAreDeclaredOnce() {
    let one = RemoteDocument(width: 10, height: 10) { RemoteText("same") }.encode()
    let two = RemoteDocument(width: 10, height: 10) {
      RemoteText("same")
      RemoteText("same")
    }.encode()
    // "same" is a 4-byte string: a second declaration would add its opcode, id, length and bytes.
    let textDeclaration = [UInt8](arrayLiteral: 102)
    func declarations(_ bytes: [UInt8]) -> Int {
      bytes.indices.filter { bytes[$0...].starts(with: textDeclaration + [0, 0, 0, 42]) }.count
    }
    #expect(declarations(one) == 1)
    #expect(declarations(two) == 1)
  }
}

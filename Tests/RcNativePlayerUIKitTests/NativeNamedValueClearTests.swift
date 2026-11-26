#if canImport(UIKit)
  import Foundation
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @_spi(Conformance) @_spi(RemoteComposeController) @testable import RcNativePlayerCore
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// The UIKit player's clear (`clear(_:)`, `submitClear(for:)`) and its long and float-array
  /// writes: what the live document shows, and what a reopened session starts from. The Kotlin
  /// reference is `RcNamedValueRenderTest.settingAndThenRemovingANamedValueDrivesTheLiveDocument
  /// BothWays`. No sleeps; waits poll with a failing deadline.
  @MainActor @Suite(.serialized) struct NativeNamedValueClearTests {
    private static var visible: Int { NativeSwiftVisibility.visible }

    private func settled(
      _ view: RemoteComposeNativePlayerView, _ what: String = "the player settles"
    ) async throws {
      try await waitUntil(what) { !view.isOpeningDocument && !view.isBusy }
    }

    private func component(_ id: Int, in view: UIView) -> UIView? {
      var pending: [UIView] = [view]
      while !pending.isEmpty {
        let next = pending.removeFirst()
        if let component = next as? NativeComponentView,
          let found = component.descendant(withID: id)
        {
          return found
        }
        pending.append(contentsOf: next.subviews)
      }
      return nil
    }

    private func isBoxShown(in view: UIView) -> Bool? {
      component(NativeTestDocuments.boxID, in: view).map { !$0.isHidden }
    }

    private func labelText(in view: UIView) -> String? {
      guard let found = component(NativeTestDocuments.labelID, in: view) else { return nil }
      var labels: [UIView] = [found]
      while !labels.isEmpty {
        let candidate = labels.removeFirst()
        if let label = candidate as? UILabel { return label.text }
        labels.append(contentsOf: candidate.subviews)
      }
      return nil
    }

    // MARK: - The live document

    /// `RcNamedValueRenderTest` :89 — setting and then clearing a value drives the live document
    /// both ways, and the same value written again after the clear is shown again.
    @Test func settingAndThenClearingDrivesTheLiveDocumentBothWays() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil)
      try await settled(view, "the document opens")
      #expect(isBoxShown(in: view) == false, "the document starts gone")

      #expect(await view.setInteger(Self.visible, for: "shown"))
      #expect(isBoxShown(in: view) == true, "the host's value did not reach the document")

      #expect(await view.clear("shown"))
      #expect(isBoxShown(in: view) == false, "the document's recorded value did not come back")

      #expect(await view.setInteger(Self.visible, for: "shown"))
      #expect(isBoxShown(in: view) == true, "the same value written after the clear")
    }

    /// A name the document does not declare is refused, and clearing twice is harmless.
    @Test func aClearOfAnUndeclaredNameIsRefusedAndAClearTwiceIsHarmless() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil)
      try await settled(view, "the document opens")
      #expect(!(await view.clear("missing")))
      #expect(await view.submitClear(for: "missing") == .refused)
      #expect(await view.clear("shown"))
      #expect(await view.clear("shown"))
      #expect(isBoxShown(in: view) == false)
    }

    /// Long and float-array writes reach the open session, typed against the declarations.
    @Test func longAndFloatArrayWritesReachTheSessionTyped() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.longAndFloatArray(), resourceResolver: nil)
      try await settled(view, "the document opens")
      #expect(await view.setLong(946_684_800_000, for: "when"))
      #expect(await view.setFloatArray([3, 4, 5], for: "series"))
      #expect(!(await view.setLong(1, for: "scalar")))
      #expect(!(await view.setFloatArray([1], for: "when")))
      #expect(!(await view.setFloatArray([.nan], for: "series")))
      #expect(await view.clear("series"))
      #expect(await view.clear("when"))
    }

    // MARK: - Reopens

    /// Every new type round-trips through two reopens unchanged, and a cleared name is not carried.
    @Test func longAndFloatArrayAreCarriedAndAClearedNameIsNot() async throws {
      let data = NativeTestDocuments.longAndFloatArray()
      let (first, _) = try await NativePlayerEngine.open(data: data, maximumDocumentBytes: 1 << 20)
      _ = try await first.apply(.setNamed("when", value: .long(-5)))
      _ = try await first.apply(.setNamed("series", value: .floatArray([7, 8])))
      _ = try await first.apply(.setNamed("scalar", value: .float(2)))
      #expect(try await first.apply(.clearNamed("scalar")).accepted)
      let expected: [NativeSwiftNamedValue] = [.floatArray([7, 8]), .long(-5)]
      #expect(await first.writtenNamedValues().map(\.value) == expected)
      let (second, _) = try await NativePlayerEngine.open(
        data: data, maximumDocumentBytes: 1 << 20, namedValues: await first.writtenNamedValues())
      #expect(await second.writtenNamedValues().map(\.value) == expected)
      #expect(try await second.apply(.clearNamed("when")).accepted)
      #expect(await second.writtenNamedValues().map(\.value) == [.floatArray([7, 8])])
    }

    /// The host's clear that never reached the session it replaces is in the seed: the reopened
    /// session starts the name from the document's value, not the one the old session carried.
    @Test func aSeededClearStartsTheNameFromTheDocumentsValue() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), resourceResolver: nil)
      try await settled(view, "the document opens")
      #expect(await view.submit(.text("Host"), for: "label") == .accepted)
      view.namedValueSeed = { [(name: "label", value: nil)] }
      var firstFrames: [String?] = []
      view.onSessionOpened = { [unowned view] in firstFrames.append(labelText(in: view)) }

      view.configureHostDensity(2)
      try await settled(view, "the reopen settles")

      #expect(firstFrames == ["Idle"], "first frame after the reopen: \(firstFrames)")
      #expect(labelText(in: view) == "Idle")
    }

    /// A clear the session already took is not applied again by a later seed: the document's
    /// own change after it is the newer value and survives the reopen.
    @Test func aSeededClearTheSessionTookKeepsTheDocumentsLaterChange() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), resourceResolver: nil)
      try await settled(view, "the document opens")
      #expect(await view.submit(.text("Host"), for: "label") == .accepted)
      #expect(await view.submitClear(for: "label") == .accepted)
      // Straight into the session, as the document's own action would change it.
      #expect(await view.setString("Document", for: "label"))
      view.namedValueSeed = { [(name: "label", value: nil)] }

      view.configureHostDensity(2)
      try await settled(view, "the reopen settles")

      #expect(labelText(in: view) == "Document", "the seeded clear was applied again")
    }

    /// A starting value left out of a session (it broke a limit) is offered to the next session
    /// until a write replaces it — and a clear of its name replaces it too.
    @Test func aClearReplacesAValueLeftOutOfTheSession() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), executionLimits: limits, resourceResolver: nil,
        onError: { errors.append($0) })
      try await settled(view, "the document opens")
      view.namedValueSeed = { [(name: "label", value: .text(String(repeating: "x", count: 100)))] }
      view.configureHostDensity(2)
      try await waitUntil("the left-out value is reported") {
        !view.isOpeningDocument && !view.isBusy && !errors.isEmpty
      }
      try #require(errors.count == 1, "\(errors)")

      #expect(await view.clear("label"))
      view.namedValueSeed = nil
      view.configureHostDensity(3)
      try await settled(view, "the second reopen settles")

      #expect(errors.count == 1, "the cleared left-out value was offered again: \(errors)")
      #expect(labelText(in: view) == "Idle")
    }

    // MARK: - The seeded first open (`initialNamedValues`)

    /// A clear in the seed of a reopen that supersedes the first open drops the value that open
    /// was seeded with: the host cleared it before any session presented.
    @Test func aSeededClearDropsAnInitialValueThatHadNotPresented() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil,
        initialNamedValues: [(name: "shown", value: .integer(Self.visible))])
      try #require(view.isOpeningDocument)
      view.namedValueSeed = { [(name: "shown", value: nil)] }

      view.configureHostDensity(2)
      try await settled(view, "the reopen settles")

      #expect(isBoxShown(in: view) == false, "the cleared initial value was applied")
    }

    /// Without a clear, the same reopen keeps the initial value (the seeded first open's contract).
    @Test func aReopenBeforeTheFirstPresentKeepsAnInitialValue() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil,
        initialNamedValues: [(name: "shown", value: .integer(Self.visible))])
      view.configureHostDensity(2)
      try await settled(view, "the reopen settles")
      #expect(isBoxShown(in: view) == true)
    }

    /// A long and a float array handed to the first open are what its session starts from, and a
    /// clear of one of them leaves the other.
    @Test func longAndFloatArrayThroughTheSeededFirstOpen() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.longAndFloatArray(), resourceResolver: nil,
        initialNamedValues: [
          (name: "when", value: .long(946_684_800_000)),
          (name: "series", value: .floatArray([7, 8])),
          (name: "scalar", value: .floatArray([1])),
        ])
      try await settled(view, "the document opens")
      #expect(
        await view.writtenNamedValues().map(\.value) == [
          .floatArray([7, 8]), .long(946_684_800_000),
        ],
        "a value of the wrong type was applied, or one was dropped")

      #expect(await view.clear("series"))
      #expect(await view.writtenNamedValues().map(\.value) == [.long(946_684_800_000)])
    }
  }
#endif

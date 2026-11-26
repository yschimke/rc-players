#if canImport(UIKit)
  import Foundation
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @_spi(RemoteComposeController) @testable import RcNativePlayerCore
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// What a session that failed hands on: the writes that failed it (`failedWrites`) by the name
  /// the document declares, the values left out of its first frame, and which batched write is
  /// refused, on the direct UIKit API.
  /// No sleeps; waits poll the player's state with a failing deadline.
  @MainActor @Suite(.serialized) struct NativeFailedWriteCarryTests {
    private func until(
      _ what: @autoclosure () -> String, within seconds: TimeInterval = 60,
      _ condition: () -> Bool
    ) async throws {
      let deadline = Date().addingTimeInterval(seconds)
      while !condition() {
        try #require(Date() < deadline, "timed out waiting until \(what())")
        await Task.yield()
      }
    }

    private func settled(_ view: RemoteComposeNativePlayerView, _ what: String = "settles")
      async throws
    {
      try await until(what) { !view.isOpeningDocument && !view.isBusy }
    }

    private static let labelID = 20
    private static let titleID = 21

    /// Like `NativeTestDocuments.label()`, but the label's variable is declared on the wire as
    /// `USER:label`, as the Kotlin authoring API writes user values; a host writes "label".
    private static func userLabel() -> Data {
      typealias Op = NativeSwiftWireOpcode
      let wire = NativeTestWire()
      wire.header(width: 200, height: 100)
      wire.u8(Op.dataText).int(60).utf8("Idle").named(
        60, NativeTestWire.NamedType.string, "USER:label")
      wire.u8(Op.dataText).int(61).utf8("Title").named(
        61, NativeTestWire.NamedType.string, "USER:title")
      wire.u8(Op.layoutRoot).int(1)
      for (componentID, text) in [(labelID, 60), (titleID, 61)] {
        wire.u8(Op.layoutText)
        for value in [componentID, 0, text, Int(Int32(bitPattern: 0xFF00_0000))] { wire.int(value) }
        wire.float(16).int(0).float(400)
        for value in [-1, 1, 1, 1] { wire.int(value) }
        wire.u8(Op.containerEnd)
      }
      wire.u8(Op.containerEnd)
      return wire.data
    }

    private func text(_ id: Int, in view: UIView) -> String? {
      var pending: [UIView] = [view]
      while !pending.isEmpty {
        let next = pending.removeFirst()
        if let component = next as? NativeComponentView,
          let found = component.descendant(withID: id)
        {
          var labels: [UIView] = [found]
          while !labels.isEmpty {
            let candidate = labels.removeFirst()
            if let label = candidate as? UILabel { return label.text }
            labels.append(contentsOf: candidate.subviews)
          }
          return nil
        }
        pending.append(contentsOf: next.subviews)
      }
      return nil
    }

    private var lowTextLimit: RemoteComposeNativeExecutionLimits {
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      return limits
    }

    private let long = String(repeating: "x", count: 100)

    // MARK: - A failed write is recognised under either spelling of its name

    /// Control: the host writes the declared spelling. The retry does not replay the failed write.
    @Test func retryWithDeclaredSpellingDoesNotReplay() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      let view = RemoteComposeNativePlayerView(
        data: Self.userLabel(), executionLimits: lowTextLimit, resourceResolver: nil,
        onError: { errors.append($0) })
      try await settled(view, "opens")
      try #require(errors.isEmpty, "\(errors)")
      #expect(await view.setString(long, for: "USER:label") == false)
      try #require(errors.count == 1, "\(errors)")
      view.load(Self.userLabel())
      try await settled(view, "retry settles")
      #expect(errors.count == 1, "replayed: \(errors)")
      #expect(text(Self.labelID, in: view) == "Idle")
    }

    /// The host writes "label" (the documented spelling for a `USER:` variable). The write that
    /// failed the session must not be replayed — and reported again — on the retry, nor on each
    /// later reopen.
    @Test func retryWithHostSpellingDoesNotReplay() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      let view = RemoteComposeNativePlayerView(
        data: Self.userLabel(), executionLimits: lowTextLimit, resourceResolver: nil,
        onError: { errors.append($0) })
      try await settled(view, "opens")
      try #require(errors.isEmpty, "\(errors)")
      #expect(await view.setString(long, for: "label") == false)
      try #require(errors.count == 1, "\(errors)")
      view.load(Self.userLabel())
      try await settled(view, "retry settles")
      #expect(errors.count == 1, "the failed write was replayed on the retry: \(errors)")
      view.configureHostDensity(2)
      try await settled(view, "density reopen settles")
      #expect(errors.count == 1, "the failed write is reported again at each reopen: \(errors)")
      #expect(text(Self.labelID, in: view) == "Idle")
    }

    // MARK: - failedWrites + leftOut

    /// A value both left out and then written equal through `submit` (counted) after the limit
    /// is raised by a reopen: shown once, not reported again.
    @Test func aLeftOutValueThenAnEqualSubmitAfterTheLimitIsRaised() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), resourceResolver: nil,
        onError: { errors.append($0) })
      try await settled(view, "opens")
      let long = self.long
      view.namedValueSeed = { [(name: "label", value: .text(long))] }
      view.configureExecutionLimits(lowTextLimit)
      try await until("left out") { !view.isOpeningDocument && !view.isBusy && !errors.isEmpty }
      try #require(errors.count == 1)
      view.configureExecutionLimits(.default)
      try await settled(view, "raised")
      #expect(text(NativeTestDocuments.labelID, in: view) == long)
      #expect(await view.submit(.text(long), for: "label") == .accepted)
      view.configureHostDensity(2)
      try await settled(view, "density")
      #expect(text(NativeTestDocuments.labelID, in: view) == long)
      #expect(errors.count == 1, "\(errors)")
    }

    // MARK: - Interleavings

    /// A write deferred while a reopen is loading, then a second reopen supersedes it and fails
    /// before opening (the document is over the size limit): the waiting write gets a final
    /// answer, not a hang.
    @Test func aWriteDeferredByAReopenThatFailsGetsAFinalAnswer() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), resourceResolver: nil, onError: { _ in })
      try await settled(view, "opens")
      view.configureHostDensity(2)
      try #require(view.isOpeningDocument)
      let availability = view.inputAvailability
      var answer: RemoteComposeNativeInputAdmission?
      Task { @MainActor in
        while true {
          let mark = availability.mark
          let result = await view.submit(.text("A"), for: "label")
          if result != .deferred {
            answer = result
            return
          }
          await availability.wait(after: mark)
        }
      }
      var tiny = RemoteComposeNativeExecutionLimits.default
      tiny.maximumDocumentBytes = 8
      view.configureExecutionLimits(tiny)
      try await until("the write is answered") { answer != nil }
      #expect(answer == .refused)
    }

    /// Background with input queued, then other bytes are loaded while in the background, then
    /// the original bytes again, then activation: the session starts fresh (no carry from the
    /// first bytes' session, no write count reuse).
    @Test func otherBytesAndBackInTheBackgroundStartFresh() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), resourceResolver: nil, onError: { _ in })
      try await settled(view, "opens")
      #expect(await view.submit(.text("A"), for: "label") == .accepted)
      for _ in 0..<20 {
        Task { @MainActor in _ = await view.submit(.integer(1), for: "missing") }
      }
      try await until("input is outstanding") { view.hasOutstandingInput }
      view.perform(NSSelectorFromString("sceneDidEnterBackground"))
      view.load(NativeTestDocuments.shownBox())
      view.load(NativeTestDocuments.label())
      view.perform(NSSelectorFromString("sceneDidActivate"))
      try await settled(view, "reopen settles")
      #expect(text(NativeTestDocuments.labelID, in: view) == "Idle")
    }
  }
#endif

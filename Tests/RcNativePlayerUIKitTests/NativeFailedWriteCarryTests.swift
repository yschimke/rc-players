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
    private func settled(_ view: RemoteComposeNativePlayerView, _ what: String = "settles")
      async throws
    {
      try await waitUntil(what, within: 60) { !view.isOpeningDocument && !view.isBusy }
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

    // MARK: - Stale failedWrites after a repair

    /// A write refused while the session was failed stays in the session and is shown once a
    /// later write repairs it. When the session later fails for another name, a retry must
    /// carry that shown value: only the write that failed it this time is left behind.
    @Test func aValueShownAfterARepairSurvivesARetryAfterALaterFailure() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), executionLimits: lowTextLimit, resourceResolver: nil,
        onError: { errors.append($0) })
      try await settled(view, "opens")
      #expect(await view.setString(long, for: "label") == false)
      #expect(await view.setString("T2", for: "title") == false, "refused: the frame still fails")
      #expect(await view.setString("ok", for: "label") == true, "repairs the session")
      try #require(text(NativeTestDocuments.labelID + 1, in: view) == "T2", "the title is shown")
      #expect(await view.setString(long, for: "label") == false, "fails it again")
      view.load(NativeTestDocuments.label())
      try await settled(view, "retry settles")
      #expect(
        text(NativeTestDocuments.labelID + 1, in: view) == "T2",
        "the title shown before the second failure was dropped by a stale failedWrites entry")
    }

    // MARK: - Batched writes: which write is blamed

    /// Two writes queued back to back behind other input: the first breaks the text limit, the
    /// second is fine. The first is the refused one, as it is when it runs alone, rather than
    /// answered `.accepted` with the next write blamed for the frame it broke.
    @Test func aBatchedBadWriteIsRefusedNotTheNextOne() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), executionLimits: lowTextLimit, resourceResolver: nil,
        onError: { errors.append($0) })
      try await settled(view, "opens")
      for _ in 0..<20 {
        Task { @MainActor in _ = await view.submit(.integer(1), for: "missing") }
      }
      try await waitUntil("input is outstanding", within: 60) { view.hasOutstandingInput }
      var bad: RemoteComposeNativeInputAdmission?
      var good: RemoteComposeNativeInputAdmission?
      Task { @MainActor in bad = await view.submit(.text(long), for: "label") }
      Task { @MainActor in good = await view.submit(.text("T2"), for: "title") }
      try await waitUntil("both answered", within: 60) { bad != nil && good != nil }
      try await settled(view)
      #expect(
        bad == .refused, "the write that broke the limit was answered \(String(describing: bad))")
      #expect(good == .accepted || good == .refused)
      // Raise the limit: the retry starts from what the session held, less the failing write.
      view.configureExecutionLimits(.default)
      try await settled(view, "retry settles")
      #expect(
        text(NativeTestDocuments.labelID + 1, in: view) == "T2" || good == .refused,
        "title")
      #expect(
        text(NativeTestDocuments.labelID, in: view) != long || bad == .accepted,
        "label: the failing write was carried")
    }

    // MARK: - failedWrites + leftOut

    /// A seed value left out of a reopen, then written directly (it fails the session again):
    /// the retry hands the next session that value neither as the failed write nor as the
    /// left-out entry for the same name, so it is not reported once more.
    @Test func aLeftOutValueWrittenAgainAndFailedIsNotReplayedOnTheRetry() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), resourceResolver: nil,
        onError: { errors.append($0) })
      try await settled(view, "opens")
      view.namedValueSeed = { [(name: "label", value: .text(String(repeating: "x", count: 100)))] }
      view.configureExecutionLimits(lowTextLimit)
      try await waitUntil("left out and reported", within: 60) {
        !view.isOpeningDocument && !view.isBusy && !errors.isEmpty
      }
      try #require(errors.count == 1, "\(errors)")
      try #require(text(NativeTestDocuments.labelID, in: view) == "Idle")
      #expect(await view.setString(long, for: "label") == false)
      try #require(errors.count == 2, "\(errors)")
      view.load(NativeTestDocuments.label())
      try await settled(view, "retry settles")
      #expect(errors.count == 2, "the failed write came back through leftOut: \(errors)")
    }

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
      try await waitUntil("left out", within: 60) {
        !view.isOpeningDocument && !view.isBusy && !errors.isEmpty
      }
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

    /// A clear of a left-out name that fails the session — another write already failed it, so
    /// its frame still breaks a limit — still hands the variable back to the document's value:
    /// the left-out value is not offered to the next session, as the Kotlin player's
    /// `clearNamedValue` drops it whatever the frame then does.
    @Test func aFailedClearOfALeftOutNameDropsTheLeftOutValue() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), resourceResolver: nil,
        onError: { errors.append($0) })
      try await settled(view, "opens")
      #expect(await view.setString(long, for: "label"))
      view.configureExecutionLimits(lowTextLimit)
      try await waitUntil("left out and reported", within: 60) {
        !view.isOpeningDocument && !view.isBusy && !errors.isEmpty
      }
      try #require(errors.count == 1, "\(errors)")
      try #require(text(NativeTestDocuments.labelID, in: view) == "Idle")
      #expect(await view.setString(long, for: "title") == false, "fails the session")
      try #require(errors.count == 2, "\(errors)")
      #expect(await view.clear("label") == false, "the frame still fails")
      view.configureExecutionLimits(.default)
      try await settled(view, "raised")
      #expect(text(NativeTestDocuments.labelID, in: view) == "Idle")
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
      try await waitUntil("the write is answered", within: 60) { answer != nil }
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
      try await waitUntil("input is outstanding", within: 60) { view.hasOutstandingInput }
      view.perform(NSSelectorFromString("sceneDidEnterBackground"))
      view.load(NativeTestDocuments.shownBox())
      view.load(NativeTestDocuments.label())
      view.perform(NSSelectorFromString("sceneDidActivate"))
      try await settled(view, "reopen settles")
      #expect(text(NativeTestDocuments.labelID, in: view) == "Idle")
    }
  }
#endif

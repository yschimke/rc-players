#if canImport(UIKit)
  import Foundation
  import RcNativePlayerCore
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// How the native player answers a named-variable write it cannot take yet. A caller that has
  /// already promised the write — the SwiftUI controller — needs `.deferred` and a signal for when
  /// to offer it again, as distinct from `.refused`, the document's final answer.
  @MainActor @Suite struct NativeInputDeferralTests {
    private static let visible = NativeSwiftVisibility.visible
    private static let gone = NativeSwiftVisibility.gone

    private func openedView() async throws -> RemoteComposeNativePlayerView {
      var errors: [RemoteComposeNativePlayerError] = []
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil, onError: { errors.append($0) })
      try await waitUntil("the document opens") { !view.isOpeningDocument }
      try #require(errors.isEmpty, "\(errors)")
      return view
    }

    /// SwiftPM runs without a window scene, so the scene notifications are delivered directly.
    private func moveToBackground(_ view: RemoteComposeNativePlayerView) {
      view.perform(NSSelectorFromString("sceneDidEnterBackground"))
    }

    private func activate(_ view: RemoteComposeNativePlayerView) {
      view.perform(NSSelectorFromString("sceneDidActivate"))
    }

    @Test func aBackgroundSceneDefersInputAndSignalsWhenItIsActiveAgain() async throws {
      let view = try await openedView()
      moveToBackground(view)
      let mark = view.inputAvailability.mark
      #expect(
        await view.submit(.integer(Self.visible), for: NativeTestDocuments.shownName) == .deferred)
      // The public setter's answer is unchanged: the write was not applied.
      #expect(!(await view.setInteger(Self.visible, for: NativeTestDocuments.shownName)))
      #expect(view.inputAvailability.mark == mark)

      activate(view)
      #expect(view.inputAvailability.mark != mark)
      #expect(
        await view.submit(.integer(Self.visible), for: NativeTestDocuments.shownName) == .accepted)
    }

    /// A write beyond the queue's capacity is deferred, and the player signals as the queue drains.
    @Test func aFullQueueDefersInputAndSignalsAsItDrains() async throws {
      let view = try await openedView()
      let mark = view.inputAvailability.mark
      let writes = (0..<300).map { index in
        Task { @MainActor in
          await view.submit(
            .integer(index % 2 == 0 ? Self.visible : Self.gone), for: NativeTestDocuments.shownName)
        }
      }
      var answers: [RemoteComposeNativeInputAdmission] = []
      for write in writes { answers.append(await write.value) }
      #expect(answers.contains(.deferred))
      #expect(answers.contains(.accepted))
      #expect(!answers.contains(.refused))
      #expect(view.inputAvailability.mark != mark)
    }

    /// A write the session is applying as the scene goes to the background is answered with that
    /// session, which is replaced on activation, so it is deferred, not accepted (the value it
    /// set is carried into the reopened session; see the next test).
    @Test func anInputInFlightWhenTheSceneGoesToTheBackgroundIsDeferred() async throws {
      let view = try await openedView()
      // The write is in flight only while the engine applies it; a write that completes before the
      // test sees it in flight is simply tried again.
      for _ in 0..<50 {
        var answer: RemoteComposeNativeInputAdmission?
        Task { @MainActor in
          answer = await view.submit(.integer(Self.visible), for: NativeTestDocuments.shownName)
        }
        try await waitUntil("the write is in flight or answered") {
          answer != nil || view.isApplyingInput
        }
        guard view.isApplyingInput else { continue }
        moveToBackground(view)
        try await waitUntil("the write is answered") { answer != nil }
        #expect(answer == .deferred)
        #expect(view.isOpeningDocument, "the bytes are reopened on activation")
        return
      }
      Issue.record("never saw the write in flight")
    }

    /// The value a write in flight was applying as the scene left is carried into the reopened
    /// session: the session that applied it is read once the write is done, before it is replaced.
    @Test func aValueInFlightWhenTheSceneGoesToTheBackgroundIsCarried() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), resourceResolver: nil)
      try await waitUntil("the document opens") { !view.isOpeningDocument }
      for attempt in 0..<50 {
        var answer: RemoteComposeNativeInputAdmission?
        let value = "Value \(attempt)"
        Task { @MainActor in
          answer = await view.submit(.text(value), for: NativeTestDocuments.labelName)
        }
        try await waitUntil("the write is in flight or answered") {
          answer != nil || view.isApplyingInput
        }
        guard view.isApplyingInput else { continue }
        moveToBackground(view)
        try await waitUntil("the write is answered") { answer != nil }
        activate(view)
        try await waitUntil("the reopen settles") { !view.isOpeningDocument && !view.isBusy }
        #expect(text(NativeTestDocuments.labelID, in: view) == value)
        return
      }
      Issue.record("never saw the write in flight")
    }

    /// A write the replaced session applied while the scene left counts as taken from the host
    /// even though it was answered `.deferred`: a seed that then brings the host's older value
    /// back is newer than it, and is applied over it.
    @Test func aSeedIsAppliedOverAWriteASupersededSessionApplied() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), resourceResolver: nil)
      try await waitUntil("the document opens") { !view.isOpeningDocument }
      for attempt in 0..<50 {
        // What the session took from the host last, before the write that is caught in flight.
        try #require(
          await view.submit(.text("Host"), for: NativeTestDocuments.labelName) == .accepted)
        var answer: RemoteComposeNativeInputAdmission?
        let value = "In flight \(attempt)"
        Task { @MainActor in
          answer = await view.submit(.text(value), for: NativeTestDocuments.labelName)
        }
        try await waitUntil("the write is in flight or answered") {
          answer != nil || view.isApplyingInput
        }
        guard view.isApplyingInput else { continue }
        moveToBackground(view)
        try await waitUntil("the write is answered") { answer != nil }
        // The host's latest value is its first one again.
        view.namedValueSeed = { [(name: NativeTestDocuments.labelName, value: .text("Host"))] }
        activate(view)
        try await waitUntil("the reopen settles") { !view.isOpeningDocument && !view.isBusy }
        #expect(text(NativeTestDocuments.labelID, in: view) == "Host")
        return
      }
      Issue.record("never saw the write in flight")
    }

    /// The text the component with `id` shows, or nil when it is not on screen.
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

    /// A write the document refuses is answered `.refused` once, even while other input is queued:
    /// it is final, not a reason to offer it again.
    @Test func aRefusalWhileInputIsQueuedIsFinal() async throws {
      let view = try await openedView()
      let busy = (0..<20).map { _ in
        Task { @MainActor in
          await view.submit(.integer(Self.visible), for: NativeTestDocuments.shownName)
        }
      }
      let refused = Task { @MainActor in await view.submit(.integer(1), for: "missing") }
      #expect(await refused.value == .refused)
      for write in busy { #expect(await write.value == .accepted) }
    }

    /// Returns once `condition` holds, yielding to the tasks it waits on; fails the test after
    /// `seconds` rather than hanging.

    /// Whether a component with `id` is in the document view on screen.
    private func hasComponent(_ id: Int, in view: UIView) -> Bool {
      var pending: [UIView] = [view]
      while !pending.isEmpty {
        let next = pending.removeFirst()
        if let component = next as? NativeComponentView, component.descendant(withID: id) != nil {
          return true
        }
        pending.append(contentsOf: next.subviews)
      }
      return false
    }

    /// Whether the document's box is on screen, or nil before the document has a view.
    private func isBoxShown(in view: UIView) -> Bool? {
      var pending: [UIView] = [view]
      while !pending.isEmpty {
        let next = pending.removeFirst()
        if let component = next as? NativeComponentView,
          let box = component.descendant(withID: NativeTestDocuments.boxID)
        {
          return !box.isHidden
        }
        pending.append(contentsOf: next.subviews)
      }
      return nil
    }

    /// A session reopened — here for a new density — starts from the seed, read as it opens, and
    /// its first frame shows it; the opening releases waiting writes.
    @Test func aReopenedSessionStartsFromTheSeedInItsFirstFrame() async throws {
      let view = try await openedView()
      #expect(isBoxShown(in: view) == false)
      var seedReads = 0
      view.namedValueSeed = {
        seedReads += 1
        return [
          (name: NativeTestDocuments.shownName, value: .integer(Self.visible)),
          (name: "missing", value: .integer(1)),
        ]
      }
      var firstFrames: [Bool?] = []
      view.onSessionOpened = { [unowned view] in firstFrames.append(isBoxShown(in: view)) }
      let mark = view.inputAvailability.mark
      view.configureHostDensity(2)
      #expect(view.isOpeningDocument)
      try await waitUntil("the reopen finishes") { !view.isOpeningDocument }
      #expect(seedReads == 1)
      // The undeclared "missing" is skipped, as a declined write is, and does not fail the open.
      #expect(firstFrames == [true])
      #expect(view.inputAvailability.mark != mark)
    }

    /// A session that opens after a failed first open starts from the seed too, so a write
    /// answered `.refused` while the document was failed is shown once an open succeeds.
    @Test func anOpenAfterAFailedFirstOpenStartsFromTheSeed() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil,
        onError: { errors.append($0) })
      view.namedValueSeed = {
        [(name: NativeTestDocuments.shownName, value: .integer(Self.visible))]
      }
      // Invalid limits fail the open before it starts, so the first session never opens.
      var invalid = RemoteComposeNativeExecutionLimits.default
      invalid.maximumFrameWork = 0
      view.configureExecutionLimits(invalid)
      #expect(!view.isOpeningDocument && errors.count == 1)
      #expect(
        await view.submit(.integer(Self.visible), for: NativeTestDocuments.shownName) == .refused)

      var firstFrames: [Bool?] = []
      view.onSessionOpened = { [unowned view] in firstFrames.append(isBoxShown(in: view)) }
      view.configureExecutionLimits(.default)
      try await waitUntil("the reopen finishes") { !view.isOpeningDocument }
      #expect(errors.count == 1)
      #expect(firstFrames == [true])
    }

    /// A seeded value that breaks an execution limit does not fail the reopen, under either policy:
    /// the session opens without the seed, the value is refused and reported once as a write
    /// would be, and the session keeps taking writes.
    @Test(arguments: [
      RemoteComposeNativePlayerCompatibilityPolicy.compatible,
      RemoteComposeNativePlayerCompatibilityPolicy.strict,
    ])
    func aSeedValueThatBreaksALimitIsReportedAndTheReopenStillOpens(
      policy: RemoteComposeNativePlayerCompatibilityPolicy
    ) async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = NativeTestDocuments.textLimitBytes
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), compatibilityPolicy: policy, executionLimits: limits,
        resourceResolver: nil, onError: { errors.append($0) })
      try await waitUntil("the document opens") { !view.isOpeningDocument }
      try #require(errors.isEmpty, "\(errors)")
      view.namedValueSeed = {
        [(name: NativeTestDocuments.labelName, value: .text(NativeTestDocuments.overLimitText()))]
      }

      view.configureHostDensity(2)
      // Settled: the reopen finished and has nothing left to apply or report.
      try await waitUntil("the reopen settles") {
        !view.isOpeningDocument && !view.isBusy && !errors.isEmpty
      }
      #expect(errors.count == 1, "\(errors)")
      #expect(
        errors.first?.localizedDescription.contains("UTF-8 bytes; the limit is 64") == true,
        "the text limit, reported for the seeded value: \(errors)")
      #expect(hasComponent(NativeTestDocuments.labelID, in: view))
      // The session is open, not failed: it still takes a write.
      #expect(await view.submit(.text("Ready"), for: NativeTestDocuments.labelName) == .accepted)
      #expect(errors.count == 1, "\(errors)")
    }

    /// A seeded value that breaks a limit is left out of the reopened session, which opens with the
    /// rest of the seed and takes a write made while it opened. Written into the session, the bad
    /// value would stay there and fail every later frame, refusing every later write.
    @Test func aSeedValueThatBreaksALimitIsLeftOutAndLaterWritesAreTaken() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = NativeTestDocuments.textLimitBytes
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), executionLimits: limits, resourceResolver: nil,
        onError: { errors.append($0) })
      try await waitUntil("the document opens") { !view.isOpeningDocument }
      let tooLong = NativeTestDocuments.overLimitText()
      view.namedValueSeed = {
        [
          (name: NativeTestDocuments.labelName, value: .text(tooLong)),
          (name: NativeTestDocuments.titleName, value: .text("Seeded")),
        ]
      }
      var applied: [String] = []
      view.onInputApplied = { input in
        if case .setNamed(let name, .text(let value)) = input { applied.append("\(name)=\(value)") }
      }

      view.configureHostDensity(2)
      #expect(view.isOpeningDocument)
      // Offered as the controller offers it: again each time the player signals.
      var answer = RemoteComposeNativeInputAdmission.deferred
      while answer == .deferred {
        let mark = view.inputAvailability.mark
        answer = await view.submit(.text("Ready"), for: NativeTestDocuments.titleName)
        if answer == .deferred { await view.inputAvailability.wait(after: mark) }
      }
      try await waitUntil("the reopen settles") { !view.isBusy }
      #expect(answer == .accepted)
      // "title=Seeded" came in the seed, before the first frame; only the write is input.
      #expect(applied == ["title=Ready"])
      #expect(errors.count == 1, "\(errors)")
    }

    /// A reopen — here for a new density — carries the named values the session it replaces
    /// held, written by the host or by the document, into the first frame of the new session.
    @Test func aReopenCarriesTheSessionsNamedValues() async throws {
      let view = try await openedView()
      #expect(
        await view.submit(.integer(Self.visible), for: NativeTestDocuments.shownName) == .accepted)
      var firstFrames: [Bool?] = []
      view.onSessionOpened = { [unowned view] in firstFrames.append(isBoxShown(in: view)) }
      view.configureHostDensity(2)
      try await waitUntil("the reopen finishes") { !view.isOpeningDocument }
      #expect(firstFrames == [true])
    }

    /// The seed is applied over the carried values: it holds the host's writes the replaced session
    /// had not taken, which are newer.
    @Test func theSeedIsAppliedOverTheCarriedValues() async throws {
      let view = try await openedView()
      #expect(
        await view.submit(.integer(Self.visible), for: NativeTestDocuments.shownName) == .accepted)
      view.namedValueSeed = { [(name: NativeTestDocuments.shownName, value: .integer(Self.gone))] }
      var firstFrames: [Bool?] = []
      view.onSessionOpened = { [unowned view] in firstFrames.append(isBoxShown(in: view)) }
      view.configureHostDensity(2)
      try await waitUntil("the reopen finishes") { !view.isOpeningDocument }
      #expect(firstFrames == [false])
    }

    /// The seed and the carried values belong to the document they were set for: other bytes
    /// clear them, and their first frame shows their own values.
    @Test func loadingOtherBytesClearsTheSeed() async throws {
      let view = try await openedView()
      #expect(
        await view.submit(.integer(Self.visible), for: NativeTestDocuments.shownName) == .accepted)
      view.namedValueSeed = {
        [(name: NativeTestDocuments.shownName, value: .integer(Self.visible))]
      }
      var firstFrames: [Bool?] = []
      view.onSessionOpened = { [unowned view] in firstFrames.append(isBoxShown(in: view)) }

      view.load(NativeTestDocuments.shownBox(size: 300))
      try await waitUntil("the new document opens") { !view.isOpeningDocument }
      #expect(firstFrames == [false])
      #expect(view.namedValueSeed == nil)
    }

    /// A waiter is released when the player goes away, so nothing waits for a scene that will
    /// never come back.
    @Test func theSignalOpensWhenThePlayerGoesAway() async throws {
      var view: RemoteComposeNativePlayerView? = try await openedView()
      let availability = try #require(view?.inputAvailability)
      let mark = availability.mark
      #expect(!availability.isClosed)
      moveToBackground(try #require(view))
      view = nil
      // UIKit may hold the view in the current autorelease pool until the main queue turns over.
      try await waitUntil("the player is released and closes its availability") {
        availability.isClosed
      }
      #expect(availability.mark != mark, "closing signals")
      await availability.wait(after: mark)
      // A waiter that starts after the close, from the current mark, returns at once too.
      var lateWaiterReturned = false
      Task { @MainActor in
        await availability.wait(after: availability.mark)
        lateWaiterReturned = true
      }
      try await waitUntil("a waiter that starts after the close returns") { lateWaiterReturned }
    }
  }
#endif

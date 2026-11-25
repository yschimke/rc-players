#if canImport(UIKit)
  import Foundation
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @_spi(Conformance) @_spi(RemoteComposeController) @testable import RcNativePlayerCore
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// What a reopened session carries over (`writtenNamedValues()`), for every type a host can
  /// write and by exact identity, what a retry and the first-frame check do with it, and what the
  /// fallback open of a reopen costs. No sleeps; waits poll with a failing deadline.
  @MainActor @Suite(.serialized) struct NativeNamedValueCarryTests {
    private func open(
      _ values: [(name: String, value: NativeSwiftNamedValue)] = []
    ) async throws -> NativePlayerEngine {
      try await NativePlayerEngine.open(
        data: NativeTestDocuments.everyHostType(), maximumDocumentBytes: 1 << 20,
        namedValues: values
      ).0
    }

    private func written(
      _ engine: NativePlayerEngine
    ) async -> [String: NativeSwiftNamedValue] {
      var result: [String: NativeSwiftNamedValue] = [:]
      for (name, value) in await engine.writtenNamedValues() where name != "computed" {
        result[name] = value
      }
      return result
    }

    /// Every host-writable type round-trips through two reopens unchanged: nothing lost,
    /// duplicated or converted.
    @Test func everyTypeIsCarriedThroughTwoReopens() async throws {
      let first = try await open()
      #expect(await written(first).isEmpty)
      _ = try await first.apply(.setFloat(2.5, name: "f"))
      _ = try await first.apply(.setColor(0xFF00_FF00, name: "c"))
      _ = try await first.apply(.setInteger(1, name: "b"))
      _ = try await first.apply(.setString("hello", name: "s"))
      _ = try await first.apply(.setInteger(7, name: "i"))
      let expected: [String: NativeSwiftNamedValue] = [
        "f": .float(2.5), "c": .color(0xFF00_FF00), "b": .integer(1), "s": .text("hello"),
        "i": .integer(7),
      ]
      #expect(await written(first) == expected)
      let second = try await open(await first.writtenNamedValues())
      #expect(await written(second) == expected)
      let third = try await open(await second.writtenNamedValues())
      #expect(await written(third) == expected)
    }

    /// A value written back to the document's initial value is carried as any written value is
    /// (harmless: the same value), so a value an earlier reopen left out for that name is known to
    /// be replaced.
    @Test func aValueWrittenBackToItsInitialValueIsHarmless() async throws {
      let first = try await open()
      _ = try await first.apply(.setInteger(3, name: "i"))
      _ = try await first.apply(.setString("a", name: "s"))
      _ = try await first.apply(.setFloat(1.5, name: "f"))
      let carried = await written(first)
      #expect(carried == ["i": .integer(3), "s": .text("a"), "f": .float(1.5)])
      let second = try await open(await first.writtenNamedValues())
      #expect(await written(second) == carried)
    }

    /// A named string the document computes (TextFromFloat) is never written, so it is not
    /// carried: the next session's frames recompute it.
    @Test func aDocumentComputedValueIsNotReportedAsWritten() async throws {
      let first = try await open()
      _ = try await first.frame(at: 0, wallClock: nil)
      let names = await first.writtenNamedValues().map(\.name)
      #expect(!names.contains("computed"), "computed value reported as written: \(names)")
    }

    /// A stale computed value handed to a reopened session is recomputed in its first frame.
    @Test func aStaleComputedValueIsRecomputedInTheReopenedSession() async throws {
      let second = try await open([(name: "computed", value: .text("stale"))])
      let computed = await second.writtenNamedValues().first { $0.name == "computed" }?.value
      #expect(computed != .text("stale"), "\(String(describing: computed))")
    }

    // MARK: - Identity, retries and the first frame's events

    /// An integer a float write reached (the two views share an id; the integer view holds the
    /// float truncated) is carried with both views, not as the truncated integer alone.
    @Test func anIntegerAFloatWriteReachedIsCarriedWithBothViews() async throws {
      let data = NativeTestDocuments.everyHostType()
      let first = try NativeSwiftDocumentSession.open(data: data)
      let id = try #require(first.namedVariableID("i"))
      #expect(first.setFloat(2.75, forID: id))
      let carried = first.writtenNamedValues()
      #expect(carried.map(\.name) == ["i"])
      #expect(carried.map(\.value) == [.integer(3, float: 2.75)])
      let (second, _) = try await NativePlayerEngine.open(
        data: data, maximumDocumentBytes: 1 << 20, namedValues: carried)
      #expect(await second.writtenNamedValues().map(\.value) == [.integer(3, float: 2.75)])
    }

    /// A value carried by its declared name lands in that variable, not in the one the host's
    /// name rule would pick: with both "foo" and "USER:foo" declared, a host's "foo" means
    /// "USER:foo", but the carried "foo" is the bare variable the document wrote.
    @Test func aCarriedValueLandsInTheVariableItCameFrom() async throws {
      let data = NativeTestDocuments.bareAndUserFoo()
      let first = try NativeSwiftDocumentSession.open(data: data)
      // A document's own write by id, as a scroll gesture moves its value.
      #expect(first.setFloat(2, forID: 600))
      let carried = first.writtenNamedValues()
      try #require(carried.map(\.name) == ["foo"])
      let (second, _) = try await NativePlayerEngine.open(
        data: data, maximumDocumentBytes: 1 << 20, namedValues: carried)
      let reopened = await second.writtenNamedValues()
      #expect(reopened.map(\.name) == ["foo"], "landed in \(reopened.map(\.name))")
      #expect(reopened.map(\.value) == [.float(2)])
    }

    private func settled(
      _ view: RemoteComposeNativePlayerView, _ what: String = "the player settles"
    ) async throws {
      try await until(what) { !view.isOpeningDocument && !view.isBusy }
    }

    private func labelText(in view: UIView) -> String? {
      var pending: [UIView] = [view]
      while !pending.isEmpty {
        let next = pending.removeFirst()
        if let component = next as? NativeComponentView,
          let found = component.descendant(withID: NativeTestDocuments.labelID)
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

    /// A retry of the same bytes out of a failed session starts from what that session started
    /// from, not from what it held when it failed: the write that failed it is not applied again
    /// (and reported again) on every retry.
    @Test func aRetryOutOfAFailedSessionDoesNotReplayTheValueThatFailedIt() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), executionLimits: limits, resourceResolver: nil,
        onError: { errors.append($0) })
      try await settled(view, "the document opens")
      #expect(
        await view.submit(.text(String(repeating: "x", count: 100)), for: "label") == .refused)
      try #require(errors.count == 1, "\(errors)")

      view.load(NativeTestDocuments.label())
      try await settled(view, "the retry settles")
      #expect(errors.count == 1, "the failed write was replayed: \(errors)")
      #expect(labelText(in: view) == "Idle")
      #expect(await view.submit(.text("Ready"), for: "label") == .accepted)
    }

    /// A starting value is checked against the first frame's whole budget, events included: one
    /// that fits the document but not the document and its events is left out and reported, and
    /// the session opens and takes writes, rather than failing while holding the value.
    @Test func aStartingValueThatBreaksTheBudgetWithTheFirstFramesEventsIsLeftOut() async throws {
      let data = NativeTestDocuments.labelWithAFrameHaptic()
      let long = String(repeating: "x", count: 100)
      let (probe, frame) = try await NativePlayerEngine.open(
        data: data, maximumDocumentBytes: 1 << 20,
        namedValues: [(name: "label", value: .text(long))])
      _ = probe
      try #require(frame.effects.count == 1, "\(frame.effects)")
      let model = try NativeDocument(frame: frame, timeSeconds: 0, limits: .default)
      var limits = RemoteComposeNativeExecutionLimits.default
      // The document with the long label fits exactly; its haptic does not.
      limits.maximumFrameWork = model.executionBudget.work
      var errors: [RemoteComposeNativePlayerError] = []
      let view = RemoteComposeNativePlayerView(
        data: data, executionLimits: limits, resourceResolver: nil,
        onError: { errors.append($0) })
      try await settled(view, "the document opens")
      try #require(errors.isEmpty, "\(errors)")
      view.namedValueSeed = { [(name: "label", value: .text(long))] }

      view.configureHostDensity(2)
      try await until("the reopen settles") {
        !view.isOpeningDocument && !view.isBusy && !errors.isEmpty
      }
      #expect(errors.count == 1, "\(errors)")
      #expect(!isShowingTheErrorLabel(view), "the reopened session failed")
      #expect(labelText(in: view) == "Idle")
      #expect(await view.submit(.text("Ready"), for: "label") == .accepted)
    }

    /// A seeded value replaces the carried value of the same name outright: the replaced one is
    /// neither applied nor judged, so it cannot be reported as left out.
    @Test func aCarriedValueTheSeedReplacesIsNotReported() async throws {
      var errors: [RemoteComposeNativePlayerError] = []
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.label(), resourceResolver: nil, onError: { errors.append($0) })
      try await settled(view, "the document opens")
      let long = String(repeating: "x", count: 100)
      #expect(await view.submit(.text(long), for: "label") == .accepted)
      view.namedValueSeed = {
        [(name: "label", value: .text("ok")), (name: "title", value: .text(long))]
      }
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      view.configureExecutionLimits(limits)
      try await until("the reopen settles") {
        !view.isOpeningDocument && !view.isBusy && !errors.isEmpty
      }
      #expect(errors.count == 1, "only the long title is left out: \(errors)")
      #expect(labelText(in: view) == "ok")
    }

    /// Whether the player shows its error label: its own label, not one inside the document.
    private func isShowingTheErrorLabel(_ view: RemoteComposeNativePlayerView) -> Bool {
      view.subviews.contains { ($0 as? UILabel)?.isHidden == false }
    }

    // MARK: - Cost of the fallback open (R5)

    private func until(
      _ what: @autoclosure () -> String, within seconds: TimeInterval = 120, _ condition: () -> Bool
    ) async throws {
      let deadline = Date().addingTimeInterval(seconds)
      while !condition() {
        try #require(Date() < deadline, "timed out waiting until \(what())")
        await Task.yield()
      }
    }

    /// Measures a reopen with `count` seeded values, with and without one value that breaks the
    /// text limit, and the longest main-actor stall during it. The fallback reopens the document
    /// once per value (each attempt replaying every value kept so far), building a
    /// `NativeDocument` on the main actor each time.
    private func reopenCost(count: Int, withBadValue: Bool) async throws -> (
      seconds: Double, longestMainStall: Double
    ) {
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.manyIntegers(count: count, withLabel: true),
        executionLimits: limits,
        resourceResolver: nil, onError: { _ in })
      try await until("the document opens") { !view.isOpeningDocument && !view.isBusy }
      var seed: [(name: String, value: RemoteComposeNativeNamedValue)] = (0..<count).map {
        (name: "n\($0)", value: .integer(NativeSwiftVisibility.visible))
      }
      if withBadValue {
        seed.insert((name: "label", value: .text(String(repeating: "x", count: 100))), at: 0)
      }
      view.namedValueSeed = { seed }
      var longest = 0.0
      var last = Date()
      var opened = false
      view.onSessionOpened = { opened = true }
      let started = Date()
      view.configureHostDensity(2)
      while !opened {
        try #require(Date().timeIntervalSince(started) < 300, "timed out reopening")
        await Task.yield()
        let now = Date()
        longest = max(longest, now.timeIntervalSince(last))
        last = now
      }
      return (Date().timeIntervalSince(started), longest)
    }

    /// Reports the fallback's cost; fails when one bad value makes the reopen of a 200-name
    /// document more than 10× slower than the same reopen without it.
    @Test func oneBadSeedValueDoesNotMakeTheReopenOrdersOfMagnitudeSlower() async throws {
      let count = 200
      let good = try await reopenCost(count: count, withBadValue: false)
      let bad = try await reopenCost(count: count, withBadValue: true)
      print(
        "reopen cost, \(count) values: clean \(good.seconds)s (main stall \(good.longestMainStall)s), "
          + "one bad value \(bad.seconds)s (main stall \(bad.longestMainStall)s)")
      #expect(
        bad.seconds < 10 * max(good.seconds, 0.05),
        "clean \(good.seconds)s (longest main-actor gap \(good.longestMainStall)s) vs one bad value \(bad.seconds)s (longest main-actor gap \(bad.longestMainStall)s)"
      )
    }
  }
#endif

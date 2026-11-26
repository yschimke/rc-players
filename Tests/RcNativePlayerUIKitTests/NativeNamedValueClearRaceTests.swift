#if canImport(UIKit)
  import Foundation
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @_spi(Conformance) @_spi(RemoteComposeController) @testable import RcNativePlayerCore
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// Clears racing writes and reopens on the UIKit player's asynchronous path. Kotlin applies the
  /// host's operations in order, so the last one wins. No sleeps; waits poll with a deadline.
  @MainActor @Suite(.serialized) struct NativeNamedValueClearRaceTests {
    private static var visible: Int { NativeSwiftVisibility.visible }

    private func settled(_ view: RemoteComposeNativePlayerView) async throws {
      try await waitUntil("the player settles") { !view.isOpeningDocument && !view.isBusy }
    }

    private func isBoxShown(in view: UIView) -> Bool? {
      var pending: [UIView] = [view]
      while !pending.isEmpty {
        let next = pending.removeFirst()
        if let component = next as? NativeComponentView,
          let found = component.descendant(withID: NativeTestDocuments.boxID)
        {
          return !found.isHidden
        }
        pending.append(contentsOf: next.subviews)
      }
      return nil
    }

    /// clear / set / clear issued without awaiting between them: the document's value.
    @Test func clearSetClearInFlightEndsOnTheDocumentsValue() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil)
      try await settled(view)
      #expect(await view.setInteger(Self.visible, for: "shown"))
      let a = Task { await view.clear("shown") }
      let b = Task { await view.setInteger(Self.visible, for: "shown") }
      let c = Task { await view.clear("shown") }
      let answers = [await a.value, await b.value, await c.value]
      try await settled(view)
      #expect(answers == [true, true, true])
      #expect(isBoxShown(in: view) == false)
    }

    /// set / clear / set in flight: the host's value.
    @Test func setClearSetInFlightEndsOnTheHostsValue() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil)
      try await settled(view)
      let a = Task { await view.setInteger(Self.visible, for: "shown") }
      let b = Task { await view.clear("shown") }
      let c = Task { await view.setInteger(Self.visible, for: "shown") }
      _ = (await a.value, await b.value, await c.value)
      try await settled(view)
      #expect(isBoxShown(in: view) == true)
    }

    /// A clear issued while a reopen is in flight answers `false`, as every `set*` does while the
    /// document opens (nothing is queued; the reopened session carries the value), and the same
    /// clear once it has opened takes it away.
    @Test func aClearDuringAnInFlightReopenIsAnsweredFalseAndWorksOnceOpen() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil)
      try await settled(view)
      #expect(await view.setInteger(Self.visible, for: "shown"))
      view.configureHostDensity(2)
      try #require(view.isOpeningDocument)
      #expect(!(await view.clear("shown")))
      try await settled(view)
      #expect(isBoxShown(in: view) == true, "the reopened session carries the value")

      #expect(await view.clear("shown"))
      try await settled(view)
      #expect(isBoxShown(in: view) == false)
    }

    /// The same through the controller channel (`submitClear`), and a second reopen superseding
    /// the first while the clear is queued.
    @Test func aSubmittedClearAcrossTwoSupersedingReopens() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil)
      try await settled(view)
      #expect(await view.submit(.integer(Self.visible), for: "shown") == .accepted)
      view.configureHostDensity(2)
      let clear = Task { await view.submitClear(for: "shown") }
      view.configureHostDensity(3)
      let admission = await clear.value
      try await settled(view)
      #expect(admission != .refused)
      if admission == .deferred {
        #expect(await view.submitClear(for: "shown") == .accepted)
        try await settled(view)
      }
      #expect(isBoxShown(in: view) == false)
    }

    /// A seed carrying a clear when the session never had the value: nothing changes, and a
    /// document's own value is left alone only when the seeded clear is the write the session took.
    @Test func aSeededClearOfAValueTheSessionNeverHadShowsTheDocument() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil)
      try await settled(view)
      view.namedValueSeed = { [(name: "shown", value: nil)] }
      view.configureHostDensity(2)
      try await settled(view)
      #expect(isBoxShown(in: view) == false)
      view.configureHostDensity(3)
      try await settled(view)
      #expect(isBoxShown(in: view) == false)

      // The document-change half: once the session took the host's clear, a value the document
      // changed itself afterwards is newer, and the same seeded clear leaves it.
      #expect(await view.submit(.integer(Self.visible), for: "shown") == .accepted)
      #expect(await view.submitClear(for: "shown") == .accepted)
      #expect(await view.setInteger(Self.visible, for: "shown"))  // as a document action would
      view.configureHostDensity(4)
      try await settled(view)
      #expect(isBoxShown(in: view) == true, "the seeded clear wiped the document's change")
    }

    /// Long and float-array values survive a reopen (density change) and a clear after it.
    @Test func longAndFloatArrayAcrossAReopenThenClear() async throws {
      let view = RemoteComposeNativePlayerView(
        data: NativeTestDocuments.longAndFloatArray(), resourceResolver: nil)
      try await settled(view)
      #expect(await view.setLong(Int64.max, for: "when"))
      #expect(await view.setFloatArray([], for: "series"))
      view.configureHostDensity(2)
      try await settled(view)
      #expect(
        await view.writtenNamedValues().map(\.value) == [.floatArray([]), .long(Int64.max)],
        "the reopened session lost a value")
      #expect(await view.clear("when"))
      #expect(await view.clear("series"))
      #expect(await view.writtenNamedValues().isEmpty)
      #expect(await view.setLong(Int64.min, for: "when"))
      try await settled(view)
      #expect(await view.writtenNamedValues().map(\.value) == [.long(Int64.min)])
    }
  }
#endif

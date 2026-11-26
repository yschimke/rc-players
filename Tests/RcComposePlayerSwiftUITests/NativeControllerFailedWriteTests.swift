#if canImport(UIKit)
  import Foundation
  import RcNativePlayerCore
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @testable import RcComposePlayerSwiftUI
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// Refused and repairing writes, and reopens, through the SwiftUI controller. Expected values
  /// are what the Kotlin player (one RcPlayerState per document, host map diffed against the last
  /// applied one) shows.
  @MainActor @Suite(.serialized) struct NativeControllerFailedWriteTests: NativeControllerSuite {
    private var label: Int { NativeTestDocuments.labelID }
    private let long = String(repeating: "x", count: 100)

    private func lowTextLimit() -> RemoteComposeNativeExecutionLimits {
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      return limits
    }

    private func queueDeclined(_ player: Player) async throws {
      for _ in 0..<50 {
        Task { @MainActor in _ = await player.view.submit(.integer(1), for: "missing") }
      }
      try await until("input is outstanding") { player.view.hasOutstandingInput }
    }

    /// A, refused B (breaks the limit, fails the session), A again: A is a change from B, is
    /// delivered and repairs the session; a background reopen keeps A and reports nothing more.
    @Test func aThenRefusedBThenAShowsA() async throws {
      let player = try await player(limits: lowTextLimit())
      #expect(player.controller.setString("A", for: "label"))
      try await settle(player)
      #expect(player.controller.setString(long, for: "label"))
      try await settle(player)
      try #require(player.errors.all.count == 1, "\(player.errors.all)")
      #expect(player.controller.setString("A", for: "label"))
      try await settle(player)
      #expect(text(label, in: player.view) == "A")
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(text(label, in: player.view) == "A")
      #expect(player.errors.all.count == 1, "\(player.errors.all)")
      player.view.configureExecutionLimits(.default)
      try await settle(player)
      #expect(text(label, in: player.view) == "A")
    }

    /// The controller writes in the same main-actor turn as the reopen starts: the delivery
    /// task has not run, the seed takes the value, and the session applies it exactly once.
    @Test func aWriteInTheSameTurnAsTheActivationIsAppliedOnce() async throws {
      let player = try await player()
      try await queueDeclined(player)
      background(player)
      try #require(player.view.isOpeningDocument)
      var applied = 0
      player.view.onInputApplied = { input in
        if case .setNamed("label", _) = input { applied += 1 }
      }
      #expect(player.controller.setString("Race", for: "label"))
      activate(player)
      try await settle(player)
      #expect(text(label, in: player.view) == "Race")
      #expect(applied == 0, "applied \(applied) more times after the seed")
      try await setLocally(player, "Doc", for: "label")
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(
        text(label, in: player.view) == "Doc", "the seeded write overrode a later document change")
    }

    /// Two reopens with a write between, and a document change after each: each reopen shows
    /// the newest of the two sides.
    @Test func twoReopensWithAHostWriteAndADocumentChangeBetween() async throws {
      let player = try await player()
      #expect(player.controller.setString("H1", for: "label"))
      try await settle(player)
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      try await setLocally(player, "D1", for: "label")
      #expect(player.controller.setString("H2", for: "label"))
      try await settle(player)
      try await setLocally(player, "D2", for: "label")
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(text(label, in: player.view) == "D2")
      #expect(player.controller.setString("H2", for: "label"), "equal: no change")
      try await settle(player)
      #expect(text(label, in: player.view) == "D2")
      #expect(player.controller.setString("H1", for: "label"))
      try await settle(player)
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(text(label, in: player.view) == "H1")
    }

    /// A refused host write, then an innocent write of another name refused while the session is
    /// failed, then a repair, then a second failure and a background reopen with the default
    /// limit: the innocent value, shown after the repair, is shown after the reopen.
    @Test func anInnocentWriteRefusedWhileFailedSurvivesALaterReopen() async throws {
      let player = try await player(limits: lowTextLimit())
      #expect(player.controller.setString(long, for: "label"))
      try await settle(player)
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(player.controller.setString("ok", for: "label"))
      try await settle(player)
      try #require(isShown(Self.shownBox, in: player.view) == true, "shown after the repair")
      #expect(player.controller.setString(long, for: "label"))
      try await settle(player)
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == true)
    }

    /// Writes to a name the document does not declare and to a declared name with the wrong
    /// type are refused at the controller, are not seeded, and do not disturb a reopen.
    @Test func undeclaredAndMistypedWritesAreNotSeeded() async throws {
      let player = try await player()
      #expect(!player.controller.setString("x", for: "nope"))
      #expect(!player.controller.setString("x", for: "shown"))
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == true)
      #expect(player.errors.all.isEmpty, "\(player.errors.all)")
    }

    /// The document changes the value while the reopen is loading (the local write is deferred),
    /// then the host writes the same value as before: the reopened session shows the host's
    /// value, which it already held; nothing is lost or duplicated.
    @Test func aDocumentWriteDuringTheReopenIsDeferredAndTheHostValueStays() async throws {
      let player = try await player()
      #expect(player.controller.setString("H", for: "label"))
      try await settle(player)
      try await backgroundWithInputQueued(player)
      activate(player)
      #expect(await player.view.setString("Doc", for: "label") == false, "deferred while opening")
      try await settle(player)
      #expect(text(label, in: player.view) == "H")
    }
  }
#endif

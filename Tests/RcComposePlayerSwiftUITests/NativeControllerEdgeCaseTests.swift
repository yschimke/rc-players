#if canImport(UIKit)
  import Foundation
  import RcNativePlayerCore
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @testable import RcComposePlayerSwiftUI
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// Edge cases of the native fallback with a controller: equal writes, refusals, limits,
  /// failed sessions, reopens during reopens, controller swaps — each asserting what the Kotlin
  /// player (one `RcPlayerState` per document, host values diffed against the last applied map)
  /// shows. No sleeps; every wait polls the player's state with a failing deadline.
  @MainActor @Suite(.serialized) struct NativeControllerEdgeCaseTests: NativeControllerSuite {
    private var label: Int { NativeTestDocuments.labelID }

    /// Fills the open session's queue with writes the document declines, so the next writes stay
    /// queued (not applied) when the scene leaves.
    private func queueDeclinedInput(_ player: Player) async throws {
      for _ in 0..<50 {
        Task { @MainActor in _ = await player.view.submit(.integer(1), for: "missing") }
      }
      try await until("input is outstanding") { player.view.hasOutstandingInput }
    }

    // MARK: - Equal writes in every order

    /// Host A (applied), the document changes it, then the host writes B and back to A in separate
    /// main-queue turns while the queue is busy, and the scene leaves before either is applied.
    /// The host's A is the newest write: Kotlin's collector sees B and then A as two changes
    /// (each turn ends with a snapshot apply), so it applies both and shows A.
    @Test func hostABAQueuedAtTheBackgroundShowsTheHostsNewestValue() async throws {
      let player = try await player()
      #expect(player.controller.setString("A", for: "label"))
      try await settle(player)
      try await setLocally(player, "Doc", for: "label")
      try #require(text(label, in: player.view) == "Doc")
      try await queueDeclinedInput(player)
      #expect(player.controller.setString("B", for: "label"))
      for _ in 0..<5 { await Task.yield() }
      #expect(player.controller.setString("A", for: "label"))
      for _ in 0..<5 { await Task.yield() }
      try #require(player.view.hasOutstandingInput, "B and A are still queued")
      background(player)
      try #require(player.view.isOpeningDocument)
      activate(player)
      try await settle(player)
      #expect(
        text(label, in: player.view) == "A",
        "the host's newest write (A, after the document's Doc) was dropped by the seed filter")
    }

    /// The same writes in one main-queue turn, with a reopen for a new density in that turn too.
    /// Kotlin diffs the host's map against the one it last applied once per pass
    /// (`applyHostValues`), so B-then-A in one pass is no change from the applied A, and the
    /// document's "Doc" stays. The native player matches it: neither write reached the session
    /// before the reopen, so the seed's A is the value the session already took.
    @Test func hostABAInOneTurnWithADensityReopenKeepsTheDocumentsValueAsKotlinDoes()
      async throws
    {
      let player = try await player()
      #expect(player.controller.setString("A", for: "label"))
      try await settle(player)
      try await setLocally(player, "Doc", for: "label")
      #expect(player.controller.setString("B", for: "label"))
      #expect(player.controller.setString("A", for: "label"))
      player.view.configureHostDensity(2)
      try await settle(player)
      #expect(
        text(label, in: player.view) == "Doc",
        "B and A in one turn are no change from the applied A on Kotlin")
    }

    /// Host A, document Doc, host A again: no change on either player (Kotlin's map is unchanged),
    /// and a reopen keeps the document's value.
    @Test func hostDocumentHostEqualKeepsTheDocumentsValueThroughAReopen() async throws {
      let player = try await player()
      #expect(player.controller.setString("A", for: "label"))
      try await settle(player)
      try await setLocally(player, "Doc", for: "label")
      #expect(player.controller.setString("A", for: "label"))
      try await settle(player)
      #expect(text(label, in: player.view) == "Doc")
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(text(label, in: player.view) == "Doc")
    }

    /// A first host write equal to the document's initial value, after the document changed it,
    /// is a change for the controller (it never held it) and is shown, then carried.
    @Test func aWriteEqualToTheDocumentsInitialValueIsApplied() async throws {
      let player = try await player()
      try await setLocally(player, "Doc", for: "label")
      #expect(player.controller.setString("Idle", for: "label"))
      try await settle(player)
      #expect(text(label, in: player.view) == "Idle")
      player.view.configureHostDensity(2)
      try await settle(player)
      #expect(text(label, in: player.view) == "Idle")
    }

    // MARK: - Refusal, then the same write

    /// A host value refused under a low limit is shown once the limit is raised (the reopen is
    /// seeded with it); an equal write in between changes nothing.
    @Test func aRefusedValueThenAnEqualWriteIsShownOnceTheLimitIsRaised() async throws {
      var errors: [RemoteComposePlayerError] = []
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      let player = try await player(limits: limits, onError: { errors.append($0) })
      let long = String(repeating: "x", count: 100)
      #expect(player.controller.setString(long, for: "label"))
      try await settle(player)
      try #require(!errors.isEmpty)
      let reported = errors.count
      #expect(player.controller.setString(long, for: "label"), "an equal write answers true")
      try await settle(player)
      #expect(errors.count == reported, "the equal write is not offered again")
      player.view.configureExecutionLimits(.default)
      try await settle(player)
      #expect(text(label, in: player.view) == long)
    }

    // MARK: - Limit lowered then raised

    @Test func aLeftOutHostValueWithAnEqualWriteInBetweenComesBack() async throws {
      let player = try await player()
      let long = String(repeating: "x", count: 100)
      #expect(player.controller.setString(long, for: "label"))
      try await settle(player)
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      player.view.configureExecutionLimits(limits)
      try await settle(player)
      try #require(text(label, in: player.view) == "Idle")
      #expect(player.controller.setString(long, for: "label"))
      try await settle(player)
      player.view.configureExecutionLimits(.default)
      try await settle(player)
      #expect(text(label, in: player.view) == long)
    }

    @Test func aDocumentChangeWhileAHostValueIsLeftOutWinsWhenTheLimitIsRaised() async throws {
      let player = try await player()
      let long = String(repeating: "x", count: 100)
      #expect(player.controller.setString(long, for: "label"))
      try await settle(player)
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      player.view.configureExecutionLimits(limits)
      try await settle(player)
      try await setLocally(player, "Doc", for: "label")
      player.view.configureExecutionLimits(.default)
      try await settle(player)
      #expect(text(label, in: player.view) == "Doc")
    }

    // MARK: - Failed session, then a reopen

    /// After that reopen the session takes writes again.
    @Test func aFailedSessionRetryTakesLaterWrites() async throws {
      var errors: [RemoteComposePlayerError] = []
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      let player = try await player(limits: limits, onError: { errors.append($0) })
      #expect(player.controller.setString(String(repeating: "x", count: 100), for: "label"))
      try await settle(player)
      player.view.configureHostDensity(2)
      try await settle(player)
      #expect(player.controller.setString("ok", for: "label"))
      try await settle(player)
      #expect(text(label, in: player.view) == "ok")
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == true)
    }

    // MARK: - Reopen during the fallback open

    /// A second reopen starts while the first one is opening with a value it must leave out: the
    /// value is reported once (by the session that opens), and the rest is carried.
    @Test func aReopenDuringALeftOutReopenReportsOnceAndCarriesTheRest() async throws {
      var errors: [RemoteComposePlayerError] = []
      let player = try await player(onError: { errors.append($0) })
      try await setLocally(player, String(repeating: "x", count: 100), for: "label")
      try await setLocally(player, Self.visible, for: "local")
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      player.view.configureExecutionLimits(limits)
      try #require(player.view.isOpeningDocument)
      player.view.configureHostDensity(2)
      try await settle(player)
      #expect(errors.count == 1, "\(errors)")
      #expect(text(label, in: player.view) == "Idle")
      #expect(isShown(Self.localBox, in: player.view) == true)
    }

    // MARK: - Controller swap with equal values

    /// The second controller holds the same value as the first. After the swap the player shows
    /// it (the iOS Kotlin host rebuilds its view controller on a swap as well), and an equal write
    /// to the new controller after a document change changes nothing, as on Kotlin.
    @Test func aControllerSwapWithAnEqualValue() async throws {
      let first = RemoteComposePlayerController()
      let player = try await player(controller: first)
      #expect(first.setString("A", for: "label"))
      try await settle(player)
      let second = RemoteComposePlayerController()
      let other = try await self.player(controller: second)
      #expect(second.setString("A", for: "label"))
      try await settle(other)
      try await setLocally(player, "Doc", for: "label")
      player.host.update(
        data: Self.document(), controller: second, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      let swapped = Player(controller: second, host: player.host, errors: player.errors)
      try await settle(swapped)
      #expect(text(label, in: swapped.view) == "A")
      try await setLocally(swapped, "Doc2", for: "label")
      #expect(second.setString("A", for: "label"))
      try await settle(swapped)
      #expect(text(label, in: swapped.view) == "Doc2")
    }
  }
#endif

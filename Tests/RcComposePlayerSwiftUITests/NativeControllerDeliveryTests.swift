#if canImport(UIKit)
  import Foundation
  import RcNativePlayerCore
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @testable import RcComposePlayerSwiftUI
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// A controller write that returned `true` reaches the screen of the real native player, whatever
  /// the scene and the session do in between: the background, a write queued as the scene leaves,
  /// a session reopened for a new density, and a first open that failed.
  @MainActor @Suite struct NativeControllerDeliveryTests {
    private static let visible = NativeSwiftVisibility.visible

    private struct Player {
      let controller: RemoteComposePlayerController
      let host: RemoteComposePlayerViewController
      let view: RemoteComposeNativePlayerView
    }

    private func player() async throws -> Player {
      let controller = RemoteComposePlayerController()
      let host = RemoteComposePlayerViewController(
        data: NativeTestDocuments.shownBox(), controller: controller, resourceResolver: nil)
      host.loadViewIfNeeded()
      let native = try #require(host.children.first as? RemoteComposeNativePlayerViewController)
      try await waitUntil("the document opens and is attached") {
        !controller.names.isEmpty && !native.isOpeningDocument
      }
      return Player(controller: controller, host: host, view: native.playerView)
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

    /// Returns once the player has opened, has nothing left to apply, and the controller has no
    /// write left to deliver; fails the test rather than hanging.
    private func settle(
      _ player: Player, _ what: String = "the player settles",
      sourceLocation: SourceLocation = #_sourceLocation
    ) async throws {
      try await waitUntil(what, sourceLocation: sourceLocation) {
        !player.view.isOpeningDocument && !player.view.isBusy
          && player.host.nativeUpdateTasks.isEmpty
      }
    }

    /// Queues `count` writes the document declines, so input is outstanding for a while.
    private func queueOtherInput(_ player: Player, count: Int = 50) {
      for _ in 0..<count {
        Task { @MainActor in _ = await player.view.submit(.integer(1), for: "missing") }
      }
    }

    private func moveToBackground(_ view: RemoteComposeNativePlayerView) {
      view.perform(NSSelectorFromString("sceneDidEnterBackground"))
    }

    private func activate(_ view: RemoteComposeNativePlayerView) {
      view.perform(NSSelectorFromString("sceneDidActivate"))
    }

    /// A write in the background waits on the player's signal, with no time limit (the controller
    /// used to give up after 2.5 s), and is shown once the scene is active.
    @Test func aWriteInTheBackgroundIsShownOnceTheSceneIsActive() async throws {
      let player = try await player()
      #expect(isBoxShown(in: player.view) == false)
      moveToBackground(player.view)
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await waitUntil("the write waits for the player's signal") {
        player.view.inputAvailability.waiterCount == 1
      }
      #expect(player.host.nativeUpdateTasks["shown"] != nil, "the delivery is still pending")
      #expect(isBoxShown(in: player.view) == false)

      activate(player.view)
      try await settle(player)
      #expect(isBoxShown(in: player.view) == true)
    }

    /// The scene leaves while the write is queued behind other input the session is applying, so
    /// the queue is abandoned and the session is reopened from the bytes on activation; the write
    /// still lands. (A write that is itself in flight as the scene leaves is answered `.deferred`
    /// in the same way — `NativeInputDeferralTests` covers that answer.)
    @Test func aWriteQueuedAsTheSceneLeavesIsShownAfterIt() async throws {
      let player = try await player()
      for _ in 0..<50 {
        Task { @MainActor in
          _ = await player.view.submit(.integer(NativeSwiftVisibility.gone), for: "shown")
        }
      }
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await waitUntil("input is outstanding") { player.view.hasOutstandingInput }
      moveToBackground(player.view)
      #expect(player.view.isOpeningDocument, "the bytes are reopened on activation")
      activate(player.view)
      try await settle(player)
      #expect(isBoxShown(in: player.view) == true)
    }

    /// A value already on screen survives the reopen that follows the scene leaving while the
    /// session is busy with other input.
    @Test func aShownValueSurvivesTheReopenAfterTheBackground() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(isBoxShown(in: player.view) == true)

      queueOtherInput(player)
      try await waitUntil("input is outstanding") { player.view.hasOutstandingInput }
      moveToBackground(player.view)
      #expect(player.view.isOpeningDocument, "the bytes are reopened on activation")
      activate(player.view)
      try await settle(player)
      #expect(isBoxShown(in: player.view) == true)
    }

    /// The player's first open fails, so a write the controller accepted is refused by the
    /// player; once a later open succeeds, the write is shown.
    @Test func aWriteRefusedByAFailedFirstOpenIsShownOnceAnOpenSucceeds() async throws {
      let controller = RemoteComposePlayerController()
      let host = RemoteComposePlayerViewController(
        data: NativeTestDocuments.shownBox(), controller: controller, resourceResolver: nil)
      host.loadViewIfNeeded()
      let native = try #require(host.children.first as? RemoteComposeNativePlayerViewController)
      // Invalid limits fail the open before it starts; the declarations are still read.
      var invalid = RemoteComposeNativeExecutionLimits.default
      invalid.maximumFrameWork = 0
      native.playerView.configureExecutionLimits(invalid)
      #expect(!native.isOpeningDocument)
      try await waitUntil("the declarations are attached") { !controller.names.isEmpty }
      #expect(controller.setInteger(Self.visible, for: "shown"))
      let player = Player(controller: controller, host: host, view: native.playerView)
      try await settle(player, "the failed player refuses the write")
      #expect(isBoxShown(in: native.playerView) == nil)

      native.playerView.configureExecutionLimits(.default)
      try await settle(player)
      #expect(isBoxShown(in: native.playerView) == true)
    }

    /// The session a reopen opens shows the host's values in its very first frame, as the Kotlin
    /// player seeds a new state with them, rather than the document's own values first.
    @Test func theFirstFrameAfterAReopenAlreadyHasTheValues() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(isBoxShown(in: player.view) == true)

      var firstFrames: [Bool?] = []
      player.view.onSessionOpened = { [view = player.view] in
        firstFrames.append(isBoxShown(in: view))
      }
      player.view.configureHostDensity(2)
      try await settle(player)
      #expect(firstFrames == [true])
    }

    /// A write queued as the scene leaves is answered `.deferred` and offered again, but the
    /// reopened session's seed already holds the same value: the session applies it once, from the
    /// seed, rather than again as a write.
    @Test func aWriteQueuedAtAReopenIsAppliedOnceFromTheSeed() async throws {
      let player = try await player()
      queueOtherInput(player)
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await waitUntil("input is outstanding") { player.view.hasOutstandingInput }
      moveToBackground(player.view)
      var applied = 0
      player.view.onInputApplied = { input in
        if case .setNamed("shown", .integer) = input { applied += 1 }
      }

      activate(player.view)
      // Settled: the session is open and idle, and no delivery is left to offer anything.
      try await settle(player, "the reopened player settles")
      #expect(isBoxShown(in: player.view) == true)
      #expect(applied == 0, "the seed applied it; \(applied) more writes did")
    }

    /// Writes waiting on a full queue are woken one per freed slot, not all of them for every
    /// slot: waking them all made `n` waiting writes cost about `n²/2` offers. Driven through the
    /// controller's own delivery loop against the real player.
    @Test func manyWritesWaitingOnAFullQueueCostLinearlyManyOffers() async throws {
      let native = RemoteComposeNativePlayerViewController(
        data: NativeTestDocuments.shownBox(), resourceResolver: nil)
      native.loadViewIfNeeded()
      try await waitUntil("the document opens") { !native.isOpeningDocument }
      let target = CountingTarget(native)
      let writes = 600
      var finished = 0
      let deliveries = (0..<writes).map { index in
        Task { @MainActor in
          let value = ControllerNamedValue.integer(
            Int32(index % 2 == 0 ? Self.visible : NativeSwiftVisibility.gone))
          await value.deliver(for: "shown", to: { target }, isCurrent: { true })
          finished += 1
        }
      }
      // A write stranded without a wake-up fails the test instead of hanging it.
      defer { for delivery in deliveries { delivery.cancel() } }
      try await waitUntil("every write is delivered", within: 60) { finished == writes }
      #expect(target.accepted == writes)
      // Each write is offered once, plus once each time it is woken after a deferral: at least
      // `writes - capacity` (256) more, 944 here. A write whose mark goes stale between its offer
      // and its wait retries early, which is timing-dependent and not bounded tightly, so the bound
      // is empirical: 15 runs took 944-1,163 offers, and waking every waiter per freed slot takes
      // about 59,800. Five offers per write sits well clear of both.
      #expect(target.offers <= 5 * writes, "\(target.offers) offers for \(writes) writes")
    }

    /// A new density reopens the session from the bytes; the values written before survive it.
    @Test func aReopenForANewDensityKeepsTheValues() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(isBoxShown(in: player.view) == true)

      player.view.configureHostDensity(2)
      #expect(player.view.isOpeningDocument)
      // Settled, not a frame that happened to land before the reopened session's own.
      try await settle(player)
      #expect(isBoxShown(in: player.view) == true)
    }
  }

  /// The real player, counting what the delivery loop offers it.
  @MainActor
  private final class CountingTarget: NativeNamedValueTarget {
    private let player: RemoteComposeNativePlayerViewController
    private(set) var offers = 0
    private(set) var accepted = 0

    init(_ player: RemoteComposeNativePlayerViewController) { self.player = player }

    var inputAvailability: RemoteComposeNativeInputAvailability { player.inputAvailability }

    func submit(_ value: RemoteComposeNativeNamedValue, for name: String) async
      -> RemoteComposeNativeInputAdmission
    {
      offers += 1
      let admission = await player.submit(value, for: name)
      if admission == .accepted { accepted += 1 }
      return admission
    }

    func submitClear(for name: String) async -> RemoteComposeNativeInputAdmission {
      offers += 1
      let admission = await player.submitClear(for: name)
      if admission == .accepted { accepted += 1 }
      return admission
    }
  }
#endif

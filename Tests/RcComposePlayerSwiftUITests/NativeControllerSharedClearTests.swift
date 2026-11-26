#if canImport(UIKit)
  import Foundation
  @_spi(RemoteComposeController) import RcNativePlayerCore
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @testable import RcComposePlayerSwiftUI
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// The controller's clear through the native fallback with what #974 brought: one update
  /// handler per player sharing a controller, a seeded first open, in-place configuration updates.
  /// Each expectation is what the Kotlin iOS player shows: it diffs the controller's map once per
  /// pass and calls `clearNamedValue` for each removed key, and keeps its state through the
  /// background, so it never shows a cleared value again.
  @MainActor @Suite(.serialized)
  struct NativeControllerSharedClearTests: NativeControllerSuite {
    /// Control for the next test: one player, a clear in the background, a reopen on the
    /// foreground. Its first frame shows the document's value.
    @Test func controlABackgroundClearReopensCleared() async throws {
      let a = try await player()
      #expect(a.controller.setInteger(Self.visible, for: "shown"))
      try await settle(a)
      try #require(isShown(Self.shownBox, in: a.view) == true)

      background(a)
      #expect(a.controller.clear("shown"))
      a.view.configureHostDensity(2)
      var firstFrames: [Bool?] = []
      let view = a.view
      view.onSessionOpened = { [unowned view] in
        firstFrames.append(isShown(Self.shownBox, in: view))
      }
      activate(a)
      try await settle(a)
      #expect(firstFrames == [false], "first frames \(firstFrames)")
      #expect(isShown(Self.shownBox, in: a.view) == false)
    }

    /// A second player attaching to the shared controller must not drop the first player's pending
    /// clear: the first player's reopen is seeded with it, rather than starting from the value the
    /// old session carried.
    @Test func aSecondPlayerAttachingKeepsAPendingClearForTheFirstPlayersReopen() async throws {
      let controller = RemoteComposePlayerController()
      let a = try await player(controller: controller)
      #expect(controller.setInteger(Self.visible, for: "shown"))
      try await settle(a)
      try #require(isShown(Self.shownBox, in: a.view) == true)

      background(a)
      #expect(controller.clear("shown"))
      try #require(a.host.nativeUpdateTasks["shown"] != nil, "the clear waits for the foreground")
      let b = try await player(controller: controller)  // attaches the same controller
      #expect(isShown(Self.shownBox, in: b.view) == false)

      a.view.configureHostDensity(2)
      var firstFrames: [Bool?] = []
      let view = a.view
      view.onSessionOpened = { [unowned view] in
        firstFrames.append(isShown(Self.shownBox, in: view))
      }
      activate(a)
      try await settle(a)
      #expect(firstFrames == [false], "first frames after the reopen: \(firstFrames)")
      #expect(isShown(Self.shownBox, in: a.view) == false, "the cleared value stayed")
    }

    /// The same, with the reopen forced by input queued at the background.
    @Test func aSecondPlayerAttachingThenAQueuedInputReopen() async throws {
      let controller = RemoteComposePlayerController()
      let a = try await player(controller: controller)
      #expect(controller.setInteger(Self.visible, for: "shown"))
      try await settle(a)
      try await backgroundWithInputQueued(a)
      #expect(controller.clear("shown"))
      let b = try await player(controller: controller)
      var firstFrames: [Bool?] = []
      let view = a.view
      view.onSessionOpened = { [unowned view] in
        firstFrames.append(isShown(Self.shownBox, in: view))
      }
      activate(a)
      try await settle(a)
      try await settle(b)
      #expect(firstFrames.first == false, "first frames after the reopen: \(firstFrames)")
      #expect(isShown(Self.shownBox, in: a.view) == false)
      #expect(isShown(Self.shownBox, in: b.view) == false)
    }

    /// Shared players, one updated in place (a compatibility change reopens its session), then a
    /// clear: both show the document's value, also after a later background reopen.
    @Test func sharedPlayersInPlaceUpdateThenClearThenReopen() async throws {
      let controller = RemoteComposePlayerController()
      let a = try await player(controller: controller)
      let b = try await player(controller: controller)
      #expect(controller.setInteger(Self.visible, for: "shown"))
      try await settle(a)
      try await settle(b)
      let native = a.native
      a.host.update(
        data: Self.document(), controller: controller,
        configuration: .init(compatibility: .compatible),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      try await settle(a)
      try #require(a.native === native)
      #expect(controller.clear("shown"))
      try await settle(a)
      try await settle(b)
      #expect(isShown(Self.shownBox, in: a.view) == false)
      #expect(isShown(Self.shownBox, in: b.view) == false)
      try await backgroundWithInputQueued(a)
      activate(a)
      try await settle(a)
      #expect(isShown(Self.shownBox, in: a.view) == false, "the cleared value came back")
    }

    /// Shared players; one swaps to another controller; a clear on the first controller reaches
    /// the player still on it and not the swapped one, which follows its new controller.
    @Test func anOwnerSwapOnOneOfTwoSharedPlayers() async throws {
      let first = RemoteComposePlayerController()
      let a = try await player(controller: first)
      let b = try await player(controller: first)
      #expect(first.setInteger(Self.visible, for: "shown"))
      try await settle(a)
      try await settle(b)

      let second = RemoteComposePlayerController()
      a.host.update(
        data: Self.document(), controller: second, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      let swapped = Player(controller: second, host: a.host, errors: a.errors)
      try await settle(swapped)
      #expect(isShown(Self.shownBox, in: swapped.view) == false)

      #expect(first.clear("shown"))
      try await settle(b)
      #expect(isShown(Self.shownBox, in: b.view) == false)
      #expect(second.setInteger(Self.visible, for: "shown"))
      try await settle(swapped)
      #expect(isShown(Self.shownBox, in: swapped.view) == true)
      #expect(isShown(Self.shownBox, in: b.view) == false)
    }

    /// A backgrounded player with a clear on its way is rebuilt for a new document after a second
    /// player attached: the rebuild's first open is seeded from the controller, without the value.
    @Test func aRebuildAfterASecondPlayerAttachedWithAPendingClear() async throws {
      let controller = RemoteComposePlayerController()
      let a = try await player(controller: controller)
      #expect(controller.setInteger(Self.visible, for: "shown"))
      try await settle(a)
      background(a)
      #expect(controller.clear("shown"))
      _ = try await player(controller: controller)
      a.host.update(
        data: Self.document(size: 300), controller: controller, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      activate(a)
      try await settle(a)
      #expect(isShown(Self.shownBox, in: a.view) == false)
    }

    /// A clear and an equal write in one turn on a shared controller: both players keep the
    /// document's own change, as Kotlin's once-per-pass diff sees the value it last applied again
    /// and changes nothing.
    @Test func aClearAndAnEqualWriteInOneTurnOnSharedPlayers() async throws {
      let controller = RemoteComposePlayerController()
      let a = try await player(controller: controller)
      let b = try await player(controller: controller)
      #expect(controller.setString("Host", for: "label"))
      try await settle(a)
      try await settle(b)
      try await setLocally(a, "Document", for: "label")
      try await setLocally(b, "Document", for: "label")
      #expect(controller.clear("label"))
      #expect(controller.setString("Host", for: "label"))
      try await settle(a)
      try await settle(b)
      #expect(text(NativeTestDocuments.labelID, in: a.view) == "Document")
      #expect(text(NativeTestDocuments.labelID, in: b.view) == "Document")
    }

    /// Players sharing a controller whose documents spell one variable differently ("USER:shown"
    /// and "shown", which a host's "shown" addresses alike): a clear the first player takes from
    /// its reopen's seed reaches the variable, keyed as the document declares it.
    @Test func aSeededClearReachesAVariableDeclaredUnderUserNamespace() async throws {
      let controller = RemoteComposePlayerController()
      let a = try await player(
        data: NativeTestDocuments.shownBox(variableName: "USER:shown"), controller: controller)
      let b = try await player(data: NativeTestDocuments.shownBox(), controller: controller)
      #expect(controller.setInteger(Self.visible, for: "shown"))
      try await settle(a)
      try await settle(b)
      try #require(isShown(NativeTestDocuments.boxID, in: a.view) == true)
      try #require(isShown(NativeTestDocuments.boxID, in: b.view) == true)
      try await backgroundWithInputQueued(a)
      #expect(controller.clear("shown"))
      activate(a)
      try await settle(a)
      try await settle(b)
      #expect(isShown(NativeTestDocuments.boxID, in: a.view) == false)
      #expect(isShown(NativeTestDocuments.boxID, in: b.view) == false)
    }

    /// A write handed over in the same turn as an in-place reopen reaches the session only through
    /// the reopen's seed; a later clear of it must still reach the player.
    @Test func aValueThatArrivedOnlyThroughAReopenSeedIsStillCleared() async throws {
      let player = try await player()
      let native = player.native
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      // Same turn: the policy change reopens synchronously and reads the seed.
      player.host.update(
        data: Self.document(), controller: player.controller,
        configuration: .init(compatibility: .compatible),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      try await settle(player)
      try #require(player.native === native, "the update was not in place")
      try #require(isShown(Self.shownBox, in: player.view) == true)

      #expect(player.controller.clear("shown"))
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false, "the clear was dropped")
    }

    /// A clear whose delivery is in flight when a newer write starts: the late `.accepted` of
    /// the clear must not forget that the newer write is held. Repeated to reach the window.
    @Test func aLateAcceptedClearDoesNotForgetANewerWrite() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      var dropped = 0
      for round in 0..<20 {
        #expect(player.controller.clear("shown"))
        // Let the clear's task start and offer the clear to the player.
        try await waitUntil("the clear is in flight (round \(round))") {
          player.view.isBusy || player.host.nativeUpdateTasks["shown"] == nil
        }
        #expect(player.controller.setInteger(Self.visible, for: "shown"))
        try await settle(player)
        #expect(player.controller.clear("shown"))
        try await settle(player)
        if isShown(Self.shownBox, in: player.view) == true { dropped += 1 }
        #expect(player.controller.setInteger(Self.visible, for: "shown"))
        try await settle(player)
      }
      #expect(dropped == 0, "clears dropped in \(dropped) of 20 rounds")
    }
  }
#endif

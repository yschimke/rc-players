#if canImport(UIKit)
  import Foundation
  import RcNativePlayerCore
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @testable import RcComposePlayerSwiftUI
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// State transitions of the SwiftUI native fallback with a controller: which ones keep the
  /// player (and the state only its session holds), and which values each session shows.
  ///
  /// "Document-local" state is a value the session holds that the controller never wrote — here
  /// written straight into the player, standing in for one the document's own actions change.
  @MainActor @Suite(.serialized)
  struct NativeControllerTransitionAuditTests: NativeControllerSuite {
    // MARK: - Same input, new configuration: is the player (and its session) kept?

    @Test func theSameBytesReloadedKeepThePlayerAndItsState() async throws {
      let player = try await player()
      try await setLocally(player, Self.visible, for: "local")
      let native = player.native
      let copy = Data(Array(Self.document()))
      player.host.update(
        data: copy, controller: player.controller, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      #expect(player.native === native)
      #expect(isShown(Self.localBox, in: player.view) == true)
    }

    /// A background change applies in place (`playerBackground`) without rebuilding the native
    /// player or resetting document-local state.
    @Test func aBackgroundChangeKeepsThePlayerAndItsDocumentLocalState() async throws {
      let player = try await player()
      try await setLocally(player, Self.visible, for: "local")
      let native = player.native
      player.host.update(
        data: Self.document(), controller: player.controller,
        configuration: .init(background: .transparent),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      try await settle(player)
      #expect(player.native === native, "the native player was rebuilt")
      #expect(isShown(Self.localBox, in: player.view) == true, "document-local state was reset")
    }

    /// A resource resolver change configures the existing native player in place rather than
    /// rebuilding it, preserving document-local state across the reopen.
    @Test func aResolverChangeKeepsThePlayerAndItsDocumentLocalState() async throws {
      let player = try await player()
      try await setLocally(player, Self.visible, for: "local")
      let native = player.native
      player.host.update(
        data: Self.document(), controller: player.controller, configuration: .init(),
        resourceResolver: RemoteComposeNativeURLSessionResourceResolver.shared,
        onEvent: { _ in }, onError: { _ in })
      try await settle(player)
      #expect(player.native === native, "the native player was rebuilt")
      #expect(isShown(Self.localBox, in: player.view) == true, "document-local state was reset")
    }

    /// A compatibility policy change updates the existing native player in place, so its reopened
    /// session starts from the carried and seeded values on its first frame.
    @Test func aCompatibilityChangeKeepsThePlayerAndSeedsItsFirstFrame() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      try #require(isShown(Self.shownBox, in: player.view) == true)

      let native = player.native
      player.host.update(
        data: Self.document(), controller: player.controller,
        configuration: .init(compatibility: .compatible),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      var firstFrames: [Bool?] = []
      let view = player.view
      view.onSessionOpened = { [unowned view] in
        firstFrames.append(isShown(Self.shownBox, in: view))
      }
      try await settle(player)
      #expect(player.native === native, "the native player was rebuilt")
      #expect(isShown(Self.shownBox, in: player.view) == true)
      #expect(firstFrames == [true], "first frame after the policy change: \(firstFrames)")
    }

    /// Switching to other bytes declaring the same name seeds the new player's first open, so its
    /// first frame already shows the controller's value.
    @Test func theFirstFrameOfANewDocumentIsSeeded() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)

      player.host.update(
        data: Self.document(size: 300), controller: player.controller, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      var firstFrames: [Bool?] = []
      let view = player.view
      view.onSessionOpened = { [unowned view] in
        firstFrames.append(isShown(Self.shownBox, in: view))
      }
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == true, "the value is re-keyed and shown")
      #expect(firstFrames == [true], "first frame of the new document: \(firstFrames)")
    }

    // MARK: - Controller ownership

    @Test func aControllerSwapShowsOnlyTheCurrentControllersValues() async throws {
      let first = RemoteComposePlayerController()
      let player = try await player(controller: first)
      #expect(first.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      try #require(isShown(Self.shownBox, in: player.view) == true)

      let second = RemoteComposePlayerController()
      player.host.update(
        data: Self.document(), controller: second, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      let swapped = Player(controller: second, host: player.host, errors: player.errors)
      try await settle(swapped)
      #expect(isShown(Self.shownBox, in: swapped.view) == false, "the old controller's value")
      #expect(second.setInteger(Self.visible, for: "local"))
      try await settle(swapped)
      #expect(isShown(Self.localBox, in: swapped.view) == true)
      // A write to the detached controller is not shown...
      _ = first.setInteger(Self.gone, for: "shown")
      try await settle(swapped)
      #expect(isShown(Self.shownBox, in: swapped.view) == false)

      // ...and is what it replays once it is attached again.
      player.host.update(
        data: Self.document(), controller: first, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false)
      #expect(isShown(Self.localBox, in: player.view) == false)
    }

    /// A controller shared by two players delivers writes to both of them.
    @Test func aControllerSharedByTwoPlayersReachesBoth() async throws {
      let controller = RemoteComposePlayerController()
      let a = try await player(controller: controller)
      let b = try await player(controller: controller)
      #expect(controller.setInteger(Self.visible, for: "shown"))
      try await settle(a)
      try await settle(b)
      #expect(isShown(Self.shownBox, in: b.view) == true)
      #expect(isShown(Self.shownBox, in: a.view) == true, "the first player never got the write")
    }

    // MARK: - Reopen (background with input outstanding)

    /// A named variable the host never wrote, changed by the document, keeps the document's value
    /// through a reopen, as the Kotlin player — which keeps its state — shows it.
    @Test func aReopenAfterTheBackgroundKeepsDocumentLocalState() async throws {
      let player = try await player()
      try await setLocally(player, Self.visible, for: "local")
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(isShown(Self.localBox, in: player.view) == true, "document-local state was reset")
    }

    /// The host wrote "shown", the document changed it since: the reopen keeps the document's
    /// newer value, as the Kotlin player does, rather than the host's older one.
    @Test func aReopenKeepsTheDocumentsNewerValueOverTheHostsOlderOne() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      try await setLocally(player, Self.gone, for: "shown")
      try #require(isShown(Self.shownBox, in: player.view) == false)
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false, "the host's old value came back")
    }

    /// The document changed "label", then the host wrote it again: the host's write is the newer
    /// one and the reopen keeps it. (Writing the host's previous value again would change nothing,
    /// as on the Kotlin player; see `anEqualHostWriteAfterADocumentChangeMatchesKotlin`.)
    @Test func aReopenKeepsTheHostsNewerWriteOverTheDocumentsOlderChange() async throws {
      let player = try await player()
      #expect(player.controller.setString("Host", for: "label"))
      try await settle(player)
      try await setLocally(player, "Document", for: "label")
      #expect(player.controller.setString("Newer", for: "label"))
      try await settle(player)
      try #require(text(NativeTestDocuments.labelID, in: player.view) == "Newer")
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(
        text(NativeTestDocuments.labelID, in: player.view) == "Newer",
        "the document's older value")
    }

    /// A host write that only reached the session through a reopen's seed is the session's value
    /// from then on: a document change after it wins at the next reopen.
    @Test func aSeededHostWriteDoesNotOverrideALaterDocumentChangeAtTheNextReopen() async throws {
      let player = try await player()
      player.view.perform(NSSelectorFromString("sceneDidEnterBackground"))
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      // Configured while in the background, the reopen waits for the foreground and seeds the
      // write that was deferred there.
      player.view.configureHostDensity(2)
      activate(player)
      try await settle(player)
      try #require(isShown(Self.shownBox, in: player.view) == true)

      try await setLocally(player, Self.gone, for: "shown")
      player.view.configureHostDensity(3)
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false, "the seeded host value came back")
    }

    /// A reopen that starts while the previous one is still opening starts from what that one
    /// would have: the replaced session's values and the seed it took from the controller.
    @Test func aReopenDuringAReopenKeepsWhatTheFirstStartedFrom() async throws {
      let player = try await player()
      try await setLocally(player, Self.visible, for: "local")
      player.view.perform(NSSelectorFromString("sceneDidEnterBackground"))
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      player.view.configureHostDensity(2)
      activate(player)
      // The first reopen took the deferred write in its seed; the second supersedes it at once.
      try #require(player.view.isOpeningDocument)
      try #require(player.host.nativeUpdateTasks["shown"] == nil, "the seed took the write")
      player.view.configureHostDensity(3)
      try await settle(player)
      #expect(isShown(Self.localBox, in: player.view) == true, "document-local state was reset")
      #expect(isShown(Self.shownBox, in: player.view) == true, "the seeded write was lost")
    }

    /// A value the document holds that breaks a limit the host has since lowered is left out of
    /// the reopened session and reported once; the session opens and keeps taking writes.
    @Test func aCarriedValueThatBreaksANewLimitIsLeftOutAndReported() async throws {
      var errors: [RemoteComposePlayerError] = []
      let player = try await player(onError: { errors.append($0) })
      try await setLocally(player, String(repeating: "x", count: 100), for: "label")
      try await setLocally(player, Self.visible, for: "local")
      try #require(errors.isEmpty, "\(errors)")

      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      player.view.configureExecutionLimits(limits)
      try await settle(player)
      #expect(errors.count == 1, "the over-long label is reported once: \(errors)")
      #expect(text(NativeTestDocuments.labelID, in: player.view) == "Idle")
      #expect(isShown(Self.localBox, in: player.view) == true, "the rest is carried")
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == true, "a later write is taken")
      #expect(errors.count == 1, "\(errors)")
    }

    /// A write made while the reopen waits for the foreground is newer than the seed and wins.
    @Test func aWriteMadeDuringTheReopenWinsOverTheSeed() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      try await backgroundWithInputQueued(player)
      #expect(player.controller.setInteger(Self.gone, for: "shown"))
      activate(player)
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false)
    }

    /// A value that breaks an execution limit was refused once. The reopened session, which is
    /// offered it again, leaves it out rather than holding it — held, it would fail every later
    /// frame — and takes other writes, as upstream/main's reopen did.
    @Test func aRefusedValueDoesNotBlockOtherWritesAfterAReopen() async throws {
      var errors: [RemoteComposePlayerError] = []
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      let player = try await player(limits: limits, onError: { errors.append($0) })
      #expect(player.controller.setString(String(repeating: "x", count: 100), for: "label"))
      try await settle(player)
      try #require(!errors.isEmpty, "the over-long label is refused and reported")

      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      let reported = errors.count
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(
        isShown(Self.shownBox, in: player.view) == true,
        "a write to another name after the reopen; errors: \(errors.dropFirst(reported))")
    }

    /// A host value the reopened session left out for breaking a limit stays the host's: the next
    /// session, opened under a limit it meets, takes it.
    @Test func aHostValueLeftOutOfAReopenIsOfferedToTheNextOne() async throws {
      var errors: [RemoteComposePlayerError] = []
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      let player = try await player(limits: limits, onError: { errors.append($0) })
      let long = String(repeating: "x", count: 100)
      #expect(player.controller.setString(long, for: "label"))
      try await settle(player)
      try #require(!errors.isEmpty, "the over-long label is refused and reported")

      player.view.configureHostDensity(2)
      try await settle(player)
      #expect(text(NativeTestDocuments.labelID, in: player.view) == "Idle", "left out")
      player.view.configureExecutionLimits(.default)
      try await settle(player)
      #expect(text(NativeTestDocuments.labelID, in: player.view) == long)
    }

    // MARK: - Teardown and hierarchy

    @Test func aDeliveryWaitingWhenThePlayerIsReleasedFinishes() async throws {
      var player: Player? = try await player()
      player!.view.perform(NSSelectorFromString("sceneDidEnterBackground"))
      #expect(player!.controller.setInteger(Self.visible, for: "shown"))
      let delivery = try #require(player!.host.nativeUpdateTasks["shown"])
      try await waitUntil("the delivery waits") { player!.view.inputAvailability.waiterCount == 1 }
      var finished = false
      Task { @MainActor in
        await delivery.value
        finished = true
      }
      player = nil
      try await waitUntil("the delivery finishes after the player is gone") { finished }
    }

    @Test func aWriteDeferredInTheBackgroundSurvivesARebuild() async throws {
      let player = try await player()
      player.view.perform(NSSelectorFromString("sceneDidEnterBackground"))
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      player.host.update(
        data: Self.document(size: 300), controller: player.controller, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == true)
    }

    @Test func removingAndReaddingTheViewKeepsTheSession() async throws {
      let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 200, height: 200))
      let player = try await player()
      window.addSubview(player.host.view)
      try await setLocally(player, Self.visible, for: "local")
      var opens = 0
      player.view.onSessionOpened = { opens += 1 }
      player.host.view.removeFromSuperview()
      window.addSubview(player.host.view)
      try await settle(player)
      #expect(opens == 0)
      #expect(isShown(Self.localBox, in: player.view) == true)
      player.host.view.removeFromSuperview()
    }
  }
#endif

#if canImport(UIKit)
  import Foundation
  @_spi(RemoteComposeController) import RcNativePlayerCore
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @testable import RcComposePlayerSwiftUI
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// `RemoteComposePlayerController.clear(_:)`, `setLong` and `setFloatArray` through the SwiftUI
  /// native fallback to what the player shows, across the transitions the controller suites
  /// drive. The Kotlin reference is `RcNamedValueRenderTest` :89 (removing a named value drives the
  /// live document back) with `RcComposePlayerController.clear`.
  @MainActor @Suite(.serialized)
  struct NativeControllerClearTests: NativeControllerSuite {
    /// Set, then clear: the view shows the host's value, then the document's again.
    @Test func aClearDrivesTheViewBackToTheDocumentsValue() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      #expect(player.controller.setString("Host", for: "label"))
      try await settle(player)
      try #require(isShown(Self.shownBox, in: player.view) == true)
      try #require(text(NativeTestDocuments.labelID, in: player.view) == "Host")

      #expect(player.controller.clear("shown"))
      #expect(player.controller.clear("label"))
      try await settle(player)

      #expect(isShown(Self.shownBox, in: player.view) == false)
      #expect(text(NativeTestDocuments.labelID, in: player.view) == "Idle")
    }

    /// The Kotlin player diffs the host's values, so the value written before the clear is a change
    /// again after it — even though the controller had answered it before.
    @Test func theSameValueWrittenAfterAClearIsShownAgain() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(player.controller.clear("shown"))
      try await settle(player)
      try #require(isShown(Self.shownBox, in: player.view) == false)

      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)

      #expect(isShown(Self.shownBox, in: player.view) == true)
    }

    /// Undeclared names are refused; a declared name nobody set is accepted and changes nothing,
    /// including a value the document changed itself.
    @Test func aClearOfAnUndeclaredOrUnsetName() async throws {
      let detached = RemoteComposePlayerController()
      #expect(!detached.clear("shown"), "before a document is attached")
      let player = try await player()
      #expect(!player.controller.clear("missing"))
      try await setLocally(player, Self.visible, for: "local")

      #expect(player.controller.clear("local"))
      #expect(player.host.nativeUpdateTasks.isEmpty, "a clear with nothing to clear was delivered")
      try await settle(player)
      #expect(isShown(Self.localBox, in: player.view) == true, "the document's own value was reset")
    }

    /// A write and its clear in the same main-actor turn are no change, as the Kotlin player sees
    /// them: it compares the host's whole map once per pass. The write never reaches the player,
    /// so neither may the clear — it would reset the document's own change. Nor may a reopen.
    @Test func aWriteClearedInTheSameTurnLeavesTheDocumentsChange() async throws {
      let player = try await player()
      try await setLocally(player, "Document", for: "label")

      #expect(player.controller.setString("Host", for: "label"))
      #expect(player.controller.clear("label"))
      try await settle(player)
      #expect(text(NativeTestDocuments.labelID, in: player.view) == "Document")

      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(
        text(NativeTestDocuments.labelID, in: player.view) == "Document",
        "the reopen applied the clear")
    }

    /// A write the player was handed, then a write and a clear in one turn: the clear is a change
    /// from what the player holds, as the Kotlin map diff sees it, and is delivered.
    @Test func aClearInTheSameTurnAsAWriteStillClearsAnEarlierHandedValue() async throws {
      let player = try await player()
      #expect(player.controller.setString("Host", for: "label"))
      try await settle(player)
      try #require(text(NativeTestDocuments.labelID, in: player.view) == "Host")

      #expect(player.controller.setString("Newer", for: "label"))
      #expect(player.controller.clear("label"))
      try await settle(player)

      #expect(text(NativeTestDocuments.labelID, in: player.view) == "Idle")
    }

    /// A clear the session took is not undone by a reopen: the reopened session does not carry the
    /// cleared value, and the seed does not bring it back.
    @Test func aClearedValueStaysClearedAcrossAReopen() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(player.controller.clear("shown"))
      try await settle(player)
      try #require(isShown(Self.shownBox, in: player.view) == false)

      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)

      #expect(isShown(Self.shownBox, in: player.view) == false, "the cleared value came back")
    }

    /// A clear made while the scene is in the background cannot reach the session yet; the reopen
    /// on the foreground starts from the document's value, so even its first frame does not show
    /// the cleared value.
    @Test func aClearInTheBackgroundIsShownFromTheReopensFirstFrame() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      try #require(isShown(Self.shownBox, in: player.view) == true)

      background(player)
      #expect(player.controller.clear("shown"))
      // Configured while in the background, the reopen waits for the foreground.
      player.view.configureHostDensity(2)
      var firstFrames: [Bool?] = []
      let view = player.view
      view.onSessionOpened = { [unowned view] in
        firstFrames.append(isShown(Self.shownBox, in: view))
      }
      activate(player)
      try await settle(player)

      #expect(firstFrames == [false], "first frame after the reopen: \(firstFrames)")
      #expect(isShown(Self.shownBox, in: player.view) == false)
    }

    /// A controller shared by two players reaches both (one update handler per player), and so
    /// does its clear.
    @Test func aClearOnASharedControllerReachesBothPlayers() async throws {
      let controller = RemoteComposePlayerController()
      let a = try await player(controller: controller)
      let b = try await player(controller: controller)
      #expect(controller.setInteger(Self.visible, for: "shown"))
      try await settle(a)
      try await settle(b)
      try #require(isShown(Self.shownBox, in: a.view) == true)
      try #require(isShown(Self.shownBox, in: b.view) == true)

      #expect(controller.clear("shown"))
      try await settle(a)
      try await settle(b)

      #expect(isShown(Self.shownBox, in: a.view) == false)
      #expect(isShown(Self.shownBox, in: b.view) == false)
    }

    // MARK: - The seeded first open and in-place updates

    /// A second player's first open is seeded with the controller's values as they were when it
    /// was built. A clear made before that player attaches still reaches it once it does.
    @Test func aClearBeforeASharedPlayersSeededFirstOpenReachesIt() async throws {
      let controller = RemoteComposePlayerController()
      let a = try await player(controller: controller)
      #expect(controller.setInteger(Self.visible, for: "shown"))
      try await settle(a)

      let host = RemoteComposePlayerViewController(
        data: Self.document(), controller: controller, resourceResolver: nil)
      host.loadViewIfNeeded()  // The first open starts, seeded with "shown".
      #expect(controller.clear("shown"), "the first player keeps the declarations attached")
      let b = Player(controller: controller, host: host, errors: NativeControllerTestErrors())
      try await settle(b)
      try await settle(a)

      #expect(isShown(Self.shownBox, in: b.view) == false, "the seeded value outlived the clear")
      #expect(isShown(Self.shownBox, in: a.view) == false)
    }

    /// With no other player attached, a clear before the rebuilt player's declarations are read
    /// is answered `false`, as a write is then, and the value stays; once attached, it clears.
    @Test func aClearBeforeARebuiltPlayerAttachesIsRefusedThenWorks() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)

      player.host.update(
        data: Self.document(size: 300), controller: player.controller, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      #expect(!player.controller.clear("shown"))
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == true)

      #expect(player.controller.clear("shown"))
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false)
    }

    /// A configuration change the native player applies in place keeps its update handler, so a
    /// clear afterwards reaches the same player — also one whose session the change reopened.
    @Test func aClearAfterAnInPlaceConfigurationUpdate() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      let native = player.native

      player.host.update(
        data: Self.document(), controller: player.controller,
        configuration: .init(background: .transparent),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      try await settle(player)
      try #require(player.native === native, "the update was not in place")
      #expect(player.controller.clear("shown"))
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false)

      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      player.host.update(
        data: Self.document(), controller: player.controller,
        configuration: .init(compatibility: .compatible),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      try await settle(player)
      try #require(player.native === native, "the update was not in place")
      try #require(isShown(Self.shownBox, in: player.view) == true)
      #expect(player.controller.clear("shown"))
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false)

      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false, "the cleared value came back")
    }

    /// A long and a float array the controller holds seed a new player's first open, and are not
    /// handed to it a second time as writes.
    @Test func longAndFloatArraySeedANewPlayersFirstOpen() async throws {
      let controller = RemoteComposePlayerController()
      let a = try await player(
        data: NativeTestDocuments.longAndFloatArray(), controller: controller)
      #expect(controller.setLong(946_684_800_000, for: "when"))
      #expect(controller.setFloatArray([7, 8], for: "series"))
      try await settle(a)

      let b = try await player(
        data: NativeTestDocuments.longAndFloatArray(), controller: controller)
      #expect(
        await b.view.writtenNamedValues().map(\.value) == [
          .floatArray([7, 8]), .long(946_684_800_000),
        ])
      var replayed = 0
      b.view.onInputApplied = { if case .setNamed = $0 { replayed += 1 } }
      try await settle(b)
      #expect(replayed == 0)

      #expect(controller.clear("series"))
      try await settle(b)
      #expect(await b.view.writtenNamedValues().map(\.value) == [.long(946_684_800_000)])
    }

    /// Long and float-array writes reach the session as the controller typed them.
    @Test func longAndFloatArrayWritesReachTheSession() async throws {
      let player = try await player(data: NativeTestDocuments.longAndFloatArray())
      var applied: [String] = []
      player.view.onInputApplied = { input in
        switch input {
        case .setNamed(let name, .long(1_700_000_001_000)): applied.append(name)
        case .setNamed(let name, .floatArray([3, 4])): applied.append(name)
        case .clearNamed(let name): applied.append("clear \(name)")
        default: break
        }
      }
      #expect(player.controller.setLong(1_700_000_001_000, for: "when"))
      #expect(player.controller.setFloatArray([3, 4], for: "series"))
      #expect(!player.controller.setLong(1, for: "series"))
      #expect(!player.controller.setFloatArray([1], for: "scalar"))
      try await settle(player)
      #expect(player.controller.clear("series"))
      try await settle(player)

      #expect(applied == ["when", "series", "clear series"])
    }
  }
#endif

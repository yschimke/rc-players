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
  @MainActor @Suite(.serialized) struct NativeControllerTransitionAuditTests {
    private static let visible = NativeSwiftVisibility.visible
    private static let gone = NativeSwiftVisibility.gone
    /// Box ids in ``document(size:)``.
    private static let shownBox = 10
    private static let localBox = 11

    /// A document with two boxes, gone until the integers named "shown" and "local" say otherwise,
    /// and a text reading the string named "label". `size` only changes the bytes.
    static func document(size: Int = 200) -> Data {
      typealias Op = NativeSwiftWireOpcode
      var data = Data()
      func u8(_ value: Int) { data.append(UInt8(truncatingIfNeeded: value)) }
      func int(_ value: Int) {
        let raw = UInt32(bitPattern: Int32(truncatingIfNeeded: value))
        for shift in [24, 16, 8, 0] { data.append(UInt8(truncatingIfNeeded: raw >> shift)) }
      }
      func float(_ value: Float) { int(Int(Int32(bitPattern: value.bitPattern))) }
      func utf8(_ value: String) {
        let bytes = Array(value.utf8)
        int(bytes.count)
        data.append(contentsOf: bytes)
      }
      u8(Op.header)
      for value in [1, 0, 0, size, size, 0, 0] { int(value) }
      for (id, name) in [(550, "shown"), (551, "local")] {
        u8(Op.dataInt)
        int(id)
        int(NativeSwiftVisibility.gone)
        u8(Op.namedVariable)
        int(id)
        int(4)  // integer
        utf8(name)
      }
      u8(Op.dataText)
      int(560)
      utf8("Idle")
      u8(Op.namedVariable)
      int(560)
      int(0)  // string
      utf8("label")
      u8(Op.layoutRoot)
      int(1)
      for (boxID, variable) in [(shownBox, 550), (localBox, 551)] {
        u8(Op.layoutBox)
        for value in [boxID, -1, 1, 1] { int(value) }
        for opcode in [Op.modifierWidth, Op.modifierHeight] {
          u8(opcode)
          int(NativeSwiftDimensionType.exact)
          float(20)
        }
        u8(Op.modifierVisibility)
        int(variable)
        u8(Op.layoutContent)
        int(boxID + 100)
        u8(Op.containerEnd)
        u8(Op.containerEnd)
      }
      u8(Op.layoutText)
      for value in [NativeTestDocuments.labelID, 0, 560, Int(Int32(bitPattern: 0xFF00_0000))] {
        int(value)
      }
      float(16)
      int(0)
      float(400)
      for value in [-1, 1, 1, 1] { int(value) }
      u8(Op.containerEnd)  // text
      u8(Op.containerEnd)  // root
      return data
    }

    /// Every error any player in this suite reported, for the timeout messages.
    static var lastErrors: [RemoteComposePlayerError] = []

    @MainActor private struct Player {
      let controller: RemoteComposePlayerController
      let host: RemoteComposePlayerViewController
      var native: RemoteComposeNativePlayerViewController {
        host.children.first as! RemoteComposeNativePlayerViewController
      }
      var view: RemoteComposeNativePlayerView { native.playerView }
    }

    private func player(
      data: Data = document(), controller: RemoteComposePlayerController = .init(),
      limits: RemoteComposeNativeExecutionLimits? = nil,
      onError: @escaping (RemoteComposePlayerError) -> Void = { _ in }
    ) async throws -> Player {
      let host = RemoteComposePlayerViewController(
        data: data, controller: controller, resourceResolver: nil,
        onError: { error in
          Self.lastErrors.append(error)
          onError(error)
        })
      host.loadViewIfNeeded()
      let player = Player(controller: controller, host: host)
      try #require(host.children.first is RemoteComposeNativePlayerViewController)
      if let limits { player.view.configureExecutionLimits(limits) }
      try await settle(player)
      return player
    }

    /// The player has opened, attached the controller, and has nothing left to apply or deliver.
    private func settle(_ player: Player, _ what: String = "the player settles") async throws {
      try await until(
        "\(what): names \(player.controller.names), opening \(player.view.isOpeningDocument), "
          + "busy \(player.view.isBusy), deliveries \(player.host.nativeUpdateTasks.keys.sorted()), errors \(Self.lastErrors)"
      ) {
        !player.controller.names.isEmpty && !player.view.isOpeningDocument
          && !player.view.isBusy && player.host.nativeUpdateTasks.isEmpty
      }
    }

    private func until(
      _ what: @autoclosure () -> String, within seconds: TimeInterval = 30, _ condition: () -> Bool
    ) async throws {
      let deadline = Date().addingTimeInterval(seconds)
      while !condition() {
        try #require(Date() < deadline, "timed out waiting until \(what())")
        await Task.yield()
      }
    }

    private func isShown(_ id: Int, in view: UIView) -> Bool? {
      var pending: [UIView] = [view]
      while !pending.isEmpty {
        let next = pending.removeFirst()
        if let component = next as? NativeComponentView, let box = component.descendant(withID: id)
        {
          return !box.isHidden
        }
        pending.append(contentsOf: next.subviews)
      }
      return nil
    }

    /// Writes straight into the open session, as the document's own action would change it.
    private func setLocally(_ player: Player, _ value: Int, for name: String) async throws {
      try #require(await player.view.submit(.integer(value), for: name) == .accepted)
    }

    /// Sends the scene to the background while input is outstanding, so the session is reopened
    /// from the bytes when the scene is active again — the reopen the SwiftUI fallback can hit.
    private func backgroundWithInputInFlight(_ player: Player) async throws {
      for _ in 0..<50 {
        Task { @MainActor in _ = await player.view.submit(.integer(1), for: "missing") }
      }
      try await until("input is outstanding") { player.view.hasOutstandingInput }
      player.view.perform(NSSelectorFromString("sceneDidEnterBackground"))
      try #require(player.view.isOpeningDocument, "the bytes are reopened on activation")
    }

    private func activate(_ player: Player) {
      player.view.perform(NSSelectorFromString("sceneDidActivate"))
    }

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

    /// Pre-existing (upstream/main rebuilds the same way): a background change, which the native
    /// view can apply in place (`playerBackground`), rebuilds the whole native player.
    @Test func aBackgroundChangeKeepsDocumentLocalState() async throws {
      let player = try await player()
      try await setLocally(player, Self.visible, for: "local")
      let native = player.native
      player.host.update(
        data: Self.document(), controller: player.controller,
        configuration: .init(background: .transparent),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      try await settle(player)
      withKnownIssue("pre-existing: any configuration change rebuilds the native player") {
        #expect(player.native === native, "the native player was rebuilt")
        #expect(isShown(Self.localBox, in: player.view) == true, "document-local state was reset")
      }
    }

    /// Pre-existing: a rebuild's first open is not seeded, so its first frame shows the
    /// document's own values and the host's arrive a frame later.
    @Test func theFirstFrameAfterARebuildShowsTheHostValues() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      try #require(isShown(Self.shownBox, in: player.view) == true)

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
      #expect(isShown(Self.shownBox, in: player.view) == true)
      withKnownIssue("pre-existing: a rebuild's first open is not seeded") {
        #expect(firstFrames == [true], "first frame after the rebuild: \(firstFrames)")
      }
    }

    /// Pre-existing: as above, for a switch to other bytes declaring the same name.
    @Test func theFirstFrameOfANewDocumentShowsTheHostValues() async throws {
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
      withKnownIssue("pre-existing: a new document's first open is not seeded") {
        #expect(firstFrames == [true], "first frame of the new document: \(firstFrames)")
      }
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
      let swapped = Player(controller: second, host: player.host)
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

    /// Pre-existing: the backend holds one update handler, so the last player attached takes
    /// every write and the other one never sees it.
    @Test func aControllerSharedByTwoPlayersReachesBoth() async throws {
      let controller = RemoteComposePlayerController()
      let a = try await player(controller: controller)
      let b = try await player(controller: controller)
      #expect(controller.setInteger(Self.visible, for: "shown"))
      try await settle(a)
      try await settle(b)
      #expect(isShown(Self.shownBox, in: b.view) == true)
      withKnownIssue("pre-existing: the backend holds one update handler") {
        #expect(isShown(Self.shownBox, in: a.view) == true, "the first player never got the write")
      }
    }

    // MARK: - Reopen (background with input in flight)

    /// Pre-existing: a reopen restarts the session from the bytes; only controller values are
    /// seeded back. The Kotlin player keeps its state through the background.
    @Test func aReopenAfterTheBackgroundKeepsDocumentLocalState() async throws {
      let player = try await player()
      try await setLocally(player, Self.visible, for: "local")
      try await backgroundWithInputInFlight(player)
      activate(player)
      try await settle(player)
      withKnownIssue("pre-existing: a native reopen restarts the session from the bytes") {
        #expect(isShown(Self.localBox, in: player.view) == true, "document-local state was reset")
      }
    }

    /// New with the seed: the host wrote "shown", the document changed it since; the reopen
    /// restores the host's older value over the document's newer one. (upstream/main restores the
    /// document's own initial value instead; the Kotlin player keeps the document's.)
    @Test func aReopenKeepsTheDocumentsNewerValueOverTheHostsOlderOne() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      try await setLocally(player, Self.gone, for: "shown")
      try #require(isShown(Self.shownBox, in: player.view) == false)
      try await backgroundWithInputInFlight(player)
      activate(player)
      try await settle(player)
      withKnownIssue("behaviour change in #969, left open: the seed restores the host's value") {
        #expect(
          isShown(Self.shownBox, in: player.view) == false, "the host's old value came back")
      }
    }

    /// A write made while the reopen waits for the foreground is newer than the seed and wins.
    @Test func aWriteMadeDuringTheReopenWinsOverTheSeed() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      try await backgroundWithInputInFlight(player)
      #expect(player.controller.setInteger(Self.gone, for: "shown"))
      activate(player)
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false)
    }

    /// A value that breaks an execution limit was refused once. upstream/main's reopen dropped it
    /// and the reopened session took other writes; with the seed it is written into every
    /// reopened session again, where it stays and fails every later frame.
    @Test func aRefusedValueDoesNotBlockOtherWritesAfterAReopen() async throws {
      var errors: [RemoteComposePlayerError] = []
      var limits = RemoteComposeNativeExecutionLimits.default
      limits.maximumTextBytes = 64
      let player = try await player(limits: limits, onError: { errors.append($0) })
      #expect(player.controller.setString(String(repeating: "x", count: 100), for: "label"))
      try await settle(player)
      try #require(!errors.isEmpty, "the over-long label is refused and reported")

      try await backgroundWithInputInFlight(player)
      activate(player)
      try await settle(player)
      let reported = errors.count
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(
        isShown(Self.shownBox, in: player.view) == true,
        "a write to another name after the reopen; errors: \(errors.dropFirst(reported))")
    }

    // MARK: - Teardown and hierarchy

    @Test func aDeliveryWaitingWhenThePlayerIsReleasedFinishes() async throws {
      var player: Player? = try await player()
      player!.view.perform(NSSelectorFromString("sceneDidEnterBackground"))
      #expect(player!.controller.setInteger(Self.visible, for: "shown"))
      let delivery = try #require(player!.host.nativeUpdateTasks["shown"])
      try await until("the delivery waits") { player!.view.inputAvailability.waiterCount == 1 }
      var finished = false
      Task { @MainActor in
        await delivery.value
        finished = true
      }
      player = nil
      try await until("the delivery finishes after the player is gone") { finished }
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

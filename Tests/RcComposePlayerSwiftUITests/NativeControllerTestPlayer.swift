#if canImport(UIKit)
  import Foundation
  import RcNativePlayerCore
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @testable import RcComposePlayerSwiftUI
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// The SwiftUI native fallback with a controller, as the controller suites drive it.
  @MainActor struct NativeControllerTestPlayer {
    let controller: RemoteComposePlayerController
    let host: RemoteComposePlayerViewController
    /// Every error this player reported, for the timeout messages.
    let errors: NativeControllerTestErrors
    var native: RemoteComposeNativePlayerViewController {
      host.children.first as! RemoteComposeNativePlayerViewController
    }
    var view: RemoteComposeNativePlayerView { native.playerView }
  }

  @MainActor final class NativeControllerTestErrors {
    var all: [RemoteComposePlayerError] = []
  }

  /// The players, waits and probes the controller suites share. Every wait polls the player's
  /// own state with a failing deadline; none sleeps.
  @MainActor protocol NativeControllerSuite {}

  extension NativeControllerSuite {
    typealias Player = NativeControllerTestPlayer

    static var visible: Int { NativeSwiftVisibility.visible }
    static var gone: Int { NativeSwiftVisibility.gone }
    /// Box ids in ``document(size:)``.
    static var shownBox: Int { 10 }
    static var localBox: Int { 11 }

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

    /// A player of `data` with `controller`, settled.
    func player(
      data: Data = Self.document(), controller: RemoteComposePlayerController = .init(),
      limits: RemoteComposeNativeExecutionLimits? = nil,
      onError: @escaping (RemoteComposePlayerError) -> Void = { _ in }
    ) async throws -> Player {
      let errors = NativeControllerTestErrors()
      let host = RemoteComposePlayerViewController(
        data: data, controller: controller, resourceResolver: nil,
        onError: { error in
          errors.all.append(error)
          onError(error)
        })
      host.loadViewIfNeeded()
      let player = Player(controller: controller, host: host, errors: errors)
      try #require(host.children.first is RemoteComposeNativePlayerViewController)
      if let limits { player.view.configureExecutionLimits(limits) }
      try await settle(player)
      return player
    }

    /// The player has opened, attached the controller, and has nothing left to apply or deliver.
    func settle(
      _ player: Player, _ what: String = "the player settles",
      sourceLocation: SourceLocation = #_sourceLocation
    ) async throws {
      try await waitUntil(
        "\(what): names \(player.controller.names), opening \(player.view.isOpeningDocument), "
          + "busy \(player.view.isBusy), deliveries \(player.host.nativeUpdateTasks.keys.sorted()), "
          + "errors \(player.errors.all)",
        sourceLocation: sourceLocation
      ) {
        !player.controller.names.isEmpty && !player.view.isOpeningDocument
          && !player.view.isBusy && player.host.nativeUpdateTasks.isEmpty
      }
    }

    /// The document component with `id`, or nil when it is not on screen.
    func find(_ id: Int, in view: UIView) -> UIView? {
      var pending: [UIView] = [view]
      while !pending.isEmpty {
        let next = pending.removeFirst()
        if let component = next as? NativeComponentView,
          let found = component.descendant(withID: id)
        {
          return found
        }
        pending.append(contentsOf: next.subviews)
      }
      return nil
    }

    func isShown(_ id: Int, in view: UIView) -> Bool? {
      find(id, in: view).map { !$0.isHidden }
    }

    /// The text the component with `id` shows, or nil when it is not on screen.
    func text(_ id: Int, in view: UIView) -> String? {
      guard let found = find(id, in: view) else { return nil }
      var labels: [UIView] = [found]
      while !labels.isEmpty {
        let candidate = labels.removeFirst()
        if let label = candidate as? UILabel { return label.text }
        labels.append(contentsOf: candidate.subviews)
      }
      return nil
    }

    /// Writes straight into the open session through the player's own setter, not the
    /// controller's channel (`submit`), as the document's own action would change it.
    func setLocally(_ player: Player, _ value: Int, for name: String) async throws {
      try #require(await player.view.setInteger(value, for: name))
    }

    /// Writes a string straight into the open session, as the document's own action would.
    func setLocally(_ player: Player, _ value: String, for name: String) async throws {
      try #require(await player.view.setString(value, for: name))
    }

    /// SwiftPM runs without a window scene, so the scene notifications are delivered directly.
    func background(_ player: Player) {
      player.view.perform(NSSelectorFromString("sceneDidEnterBackground"))
    }

    func activate(_ player: Player) {
      player.view.perform(NSSelectorFromString("sceneDidActivate"))
    }

    /// Queues writes the document declines, then sends the scene to the background while they are
    /// outstanding, so the session is reopened from the bytes when the scene is active again — the
    /// reopen the SwiftUI fallback can hit.
    func backgroundWithInputQueued(_ player: Player) async throws {
      for _ in 0..<50 {
        Task { @MainActor in _ = await player.view.submit(.integer(1), for: "missing") }
      }
      try await waitUntil("input is outstanding") { player.view.hasOutstandingInput }
      background(player)
      try #require(player.view.isOpeningDocument, "the bytes are reopened on activation")
    }
  }
#endif

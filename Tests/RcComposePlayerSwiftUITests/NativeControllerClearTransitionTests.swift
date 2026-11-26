#if canImport(UIKit)
  import Foundation
  @testable import RcNativePlayerCore
  import Testing
  import RcNativePlayerTestDocuments
  import UIKit

  @testable import RcComposePlayerSwiftUI
  @_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

  /// The controller's clear through the native fallback across writes, owner swaps and reopens.
  /// Each test asserts what the Kotlin iOS player does (`RcComposePlayer.applyHostValues` diffs the
  /// controller's map and calls `clearNamedValue` for each removed key).
  @MainActor @Suite(.serialized)
  struct NativeControllerClearTransitionTests: NativeControllerSuite {
    /// write X, clear, write X with no settle in between: Kotlin's snapshot map ends at X.
    @Test func writeClearWriteWithoutSettling() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      #expect(player.controller.clear("shown"))
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == true)
    }

    /// clear, write X, clear with no settle: the document's value.
    @Test func setClearSetClearWithoutSettling() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      #expect(player.controller.clear("shown"))
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      #expect(player.controller.clear("shown"))
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false)
    }

    /// Kotlin: a swapped-in holder that lacks a key clears it. Here: a new controller with no value.
    @Test func aControllerSwapDropsTheOldControllersValue() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      try await settle(player)
      try #require(isShown(Self.shownBox, in: player.view) == true)

      let other = RemoteComposePlayerController()
      player.host.update(
        data: Self.document(), controller: other, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      let swapped = Player(controller: other, host: player.host, errors: player.errors)
      try await settle(swapped)
      #expect(isShown(Self.shownBox, in: swapped.view) == false)
      #expect(other.setString("New", for: "label"))
      try await settle(swapped)
      #expect(text(NativeTestDocuments.labelID, in: swapped.view) == "New")
    }

    /// A clear while backgrounded, then a controller swap before the foreground: the new
    /// controller's value (none) and the document's value.
    @Test func aBackgroundClearThenAnOwnerSwap() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      #expect(player.controller.setString("Host", for: "label"))
      try await settle(player)
      background(player)
      #expect(player.controller.clear("shown"))
      let other = RemoteComposePlayerController()
      player.host.update(
        data: Self.document(), controller: other, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      let swapped = Player(controller: other, host: player.host, errors: player.errors)
      activate(swapped)
      try await settle(swapped)
      #expect(isShown(Self.shownBox, in: swapped.view) == false)
      #expect(text(NativeTestDocuments.labelID, in: swapped.view) == "Idle")
    }

    /// Clear the one name, keep the other, through a background reopen with input queued.
    @Test func aClearAndAKeptValueThroughABackgroundReopen() async throws {
      let player = try await player()
      #expect(player.controller.setInteger(Self.visible, for: "shown"))
      #expect(player.controller.setString("Host", for: "label"))
      try await settle(player)
      try await backgroundWithInputQueued(player)
      #expect(player.controller.clear("shown"))
      activate(player)
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == false)
      #expect(text(NativeTestDocuments.labelID, in: player.view) == "Host")
    }

    /// A clear, then a background reopen, then the document changes the value itself, then a
    /// second reopen: the seeded clear (already taken) must not wipe the document's change.
    @Test func aTakenClearDoesNotWipeALaterDocumentChangeAtTheNextReopen() async throws {
      let player = try await player()
      #expect(player.controller.setString("Host", for: "label"))
      try await settle(player)
      #expect(player.controller.clear("label"))
      try await settle(player)
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      try await setLocally(player, "Document", for: "label")
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      #expect(text(NativeTestDocuments.labelID, in: player.view) == "Document")
    }

    /// A clear that reaches the player only through a reopen's seed leaves the name not held: a
    /// later write and its clear in one turn are no change (Kotlin's per-pass diff never sees
    /// them), so the document's own change survives.
    @Test func aClearTakenFromAReopenSeedLeavesTheNameNotHeld() async throws {
      let player = try await player()
      #expect(player.controller.setString("Host", for: "label"))
      try await settle(player)
      try await backgroundWithInputQueued(player)
      #expect(player.controller.clear("label"))
      activate(player)
      try await settle(player)
      try #require(text(NativeTestDocuments.labelID, in: player.view) == "Idle")
      try await setLocally(player, "Document", for: "label")
      #expect(player.controller.setString("X", for: "label"))
      #expect(player.controller.clear("label"))
      try await settle(player)
      #expect(text(NativeTestDocuments.labelID, in: player.view) == "Document")
    }

    /// A stored value the new document's variable cannot take was never seeded into its first
    /// open, so the player does not hold the name: a write and its clear in one turn are no
    /// change, and the document's own change survives.
    @Test func aStoredValueTheNewDocumentRefusesIsNotHeld() async throws {
      let player = try await player()
      #expect(player.controller.setString("Host", for: "label"))
      try await settle(player)
      // The same name, declared as an integer that shows the box.
      let wire = NativeTestWire()
      wire.header(width: 100, height: 100)
      wire.u8(NativeSwiftWireOpcode.dataInt).int(550).int(Self.gone)
        .named(550, NativeTestWire.NamedType.int, "label")
      wire.u8(NativeSwiftWireOpcode.layoutRoot).int(1)
      wire.box(id: Self.shownBox, size: 20, visibility: 550)
      wire.u8(NativeSwiftWireOpcode.containerEnd)
      player.host.update(
        data: wire.data, controller: player.controller, configuration: .init(),
        resourceResolver: nil, onEvent: { _ in }, onError: { _ in })
      try await settle(player)
      try #require(isShown(Self.shownBox, in: player.view) == false)
      try await setLocally(player, Self.visible, for: "label")
      #expect(player.controller.setInteger(Self.gone, for: "label"))
      #expect(player.controller.clear("label"))
      try await settle(player)
      #expect(isShown(Self.shownBox, in: player.view) == true)
    }

    /// Long and float array values a controller holds reach a reopened session; a clear
    /// before the reopen is not undone.
    @Test func longAndFloatArrayThroughAReopenWithAClear() async throws {
      let player = try await player(data: NativeTestDocuments.longAndFloatArray())
      #expect(player.controller.setLong(Int64.min, for: "when"))
      #expect(player.controller.setFloatArray([1, 2, 3], for: "series"))
      try await settle(player)
      #expect(player.controller.clear("series"))
      try await settle(player)
      try await backgroundWithInputQueued(player)
      activate(player)
      try await settle(player)
      let seed = player.controller.backend.seedValues
      #expect(seed.map(\.name) == ["when", "series"], "\(seed)")
      #expect(seed.map(\.value) == [.long(Int64.min), nil], "\(seed)")
      #expect(player.errors.all.isEmpty, "\(player.errors.all)")
    }

    /// The equal-write check: write X, the document changes it, clear, write X again: applied.
    @Test func equalWriteCheckAfterClear() async throws {
      let player = try await player()
      #expect(player.controller.setString("Host", for: "label"))
      try await settle(player)
      try await setLocally(player, "Document", for: "label")
      #expect(player.controller.setString("Host", for: "label"))  // equal: not handed over
      try await settle(player)
      #expect(text(NativeTestDocuments.labelID, in: player.view) == "Document")
      #expect(player.controller.clear("label"))
      try await settle(player)
      #expect(text(NativeTestDocuments.labelID, in: player.view) == "Idle")
      #expect(player.controller.setString("Host", for: "label"))
      try await settle(player)
      #expect(text(NativeTestDocuments.labelID, in: player.view) == "Host")
    }

    /// The float arrays the Kotlin controller refuses (`RcComposeViewControllerTest`) are refused
    /// here too: one value more than a float list holds, or a value that is not finite.
    @Test func answersMatchTheKotlinController() async throws {
      let player = try await player(data: NativeTestDocuments.longAndFloatArray())
      let limit = NativeSwiftDocumentDecoder.maximumFloatListValues
      #expect(
        !player.controller.setFloatArray(Array(repeating: 1, count: limit + 1), for: "series"))
      #expect(!player.controller.setFloatArray([.nan], for: "series"))
      #expect(player.controller.setFloatArray(Array(repeating: 1, count: limit), for: "series"))
    }
  }

#endif

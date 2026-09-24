#if canImport(AppKit) && !targetEnvironment(macCatalyst)
  import AppKit
  import Foundation
  import RcNativePlayerCore
  import Testing

  @testable import RcNativePlayerUIKit

  /// A document that binds a component's measured size is resolved twice on AppKit: once before
  /// layout, then refined against the laid-out geometry. The refinement has to resolve the frame it
  /// is refining, at that frame's instant and wall clock, not whatever the process clock says.
  @MainActor @Suite struct NativeAppKitBoundGeometryTests {
    /// Animated, and binds its canvas's `componentWidth` / `componentHeight`.
    private static let animatedBoundDocument = "IndeterminateCircularProgress-400x400.rc"

    private func render(_ name: String, at timeSeconds: TimeInterval) throws -> Data {
      try NativeAppKitWindowController.renderFrame(
        data: NativeTestFixtures.data(name), timeSeconds: timeSeconds, wallClock: .capture
      ).png
    }

    @Test func aRefinedFrameIsTheRequestedInstant() throws {
      let first = try render(Self.animatedBoundDocument, at: 0.35)
      let again = try render(Self.animatedBoundDocument, at: 0.35)
      let start = try render(Self.animatedBoundDocument, at: 0)
      // The same instant and capture clock render the same pixels however long the process has run.
      #expect(first == again)
      // And the refinement kept the requested instant rather than falling back to the process's
      // own elapsed time, which is close to zero for both captures.
      #expect(first != start)
    }

    @Test func aStaticBoundDocumentRendersDeterministically() throws {
      let first = try render("TitleCardRemote-640x480.rc", at: 0)
      let again = try render("TitleCardRemote-640x480.rc", at: 0)
      #expect(first == again)
    }
  }
#endif

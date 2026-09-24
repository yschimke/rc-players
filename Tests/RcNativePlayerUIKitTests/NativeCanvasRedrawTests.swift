#if canImport(UIKit)
  import CoreGraphics
  import Foundation
  import Testing
  import UIKit

  @testable import RcNativePlayerCore
  @testable import RcNativePlayerUIKit

  /// `NativeCanvasView` redraws only when its commands compare unequal, so `NativeDrawCommand`'s
  /// `==` must hold for an unchanged frame — NaN sentinels included — and fail for any change.
  @Suite struct NativeCanvasRedrawTests {
    private func snapshot(
      kind: Int = NativeSwiftDrawKind.text,
      values: [Float] = [0, 10, -1, .nan, 0, 0],
      alpha: Float = 1,
      pathValues: [Float] = [0, 0],
      gradientStops: [Float] = [0, 1],
      shaderMatrix: [Float]? = nil,
      unsetValueIndices: [Int] = [3]
    ) -> NativeSwiftDrawCommandSnapshot {
      NativeSwiftDrawCommandSnapshot(
        kind: kind, values: values, colorARGB: 0xff00_00ff, alpha: alpha, strokeWidth: 1,
        isStroke: false, strokeCap: .butt, strokeJoin: .miter, blendMode: .sourceOver,
        path: [
          NativeSwiftPathElementSnapshot(kind: NativeSwiftPathCommand.move, values: pathValues)
        ],
        pathWinding: .nonZero, image: nil, textureImageID: nil, textureTileModeX: 0,
        textureTileModeY: 0, shaderMatrix: shaderMatrix, filterQuality: nil,
        usesComponentGeometry: false,
        gradient: NativeSwiftGradientSnapshot(
          kind: 0, colorsARGB: [0xff00_0000, 0xffff_ffff], stops: gradientStops,
          values: [0, 0, 10, 10], tileMode: 0),
        text: "anchored", textSize: 14, textFlags: 0, unsetValueIndices: unsetValueIndices)
    }

    private func commands(_ snapshots: NativeSwiftDrawCommandSnapshot...) -> [NativeDrawCommand] {
      snapshots.map { NativeDrawCommand($0) }
    }

    @Test func identicalCommandListsCompareEqual() {
      let plain = snapshot(values: [1, 2, 3, 4], unsetValueIndices: [])
      #expect(commands(plain, snapshot()) == commands(plain, snapshot()))
    }

    @Test func anUnchangedNaNSentinelComparesEqual() {
      // The anchored-text panY NaN sentinel: IEEE == would redraw this canvas every frame.
      #expect(snapshot().values[3].isNaN)
      #expect(NativeDrawCommand(snapshot()) == NativeDrawCommand(snapshot()))
      #expect(
        NativeDrawCommand(snapshot(alpha: .nan, pathValues: [.nan, 0], gradientStops: [.nan, 1]))
          == NativeDrawCommand(
            snapshot(alpha: .nan, pathValues: [.nan, 0], gradientStops: [.nan, 1])))
      #expect(
        NativeDrawCommand(snapshot(shaderMatrix: [1, .nan, 0]))
          == NativeDrawCommand(snapshot(shaderMatrix: [1, .nan, 0])))
    }

    @Test func aChangedValueComparesUnequal() {
      let base = NativeDrawCommand(snapshot())
      #expect(base != NativeDrawCommand(snapshot(values: [0, 11, -1, .nan, 0, 0])))
      #expect(base != NativeDrawCommand(snapshot(values: [0, 10, -1, 0, 0, 0])))
      #expect(base != NativeDrawCommand(snapshot(values: [0, 10, -1, .nan, 0])))
      #expect(base != NativeDrawCommand(snapshot(kind: NativeSwiftDrawKind.rect)))
      #expect(base != NativeDrawCommand(snapshot(alpha: 0.5)))
      #expect(base != NativeDrawCommand(snapshot(pathValues: [0, 1])))
      #expect(base != NativeDrawCommand(snapshot(gradientStops: [0, 0.5])))
      #expect(base != NativeDrawCommand(snapshot(shaderMatrix: [1, 0, 0])))
      #expect(commands(snapshot()) != commands(snapshot(), snapshot()))
    }

    /// `NativeDrawCommand.==` is written out member by member; a new stored property must be
    /// compared there too. Update this count only after doing so.
    @Test func equalityCoversEveryStoredProperty() {
      #expect(Mirror(reflecting: NativeDrawCommand(snapshot())).children.count == 23)
    }
  }
#endif

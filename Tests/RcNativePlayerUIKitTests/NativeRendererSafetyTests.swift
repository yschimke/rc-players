import CoreGraphics
import Foundation
import Testing

@testable import RcNativePlayerUIKit

@Suite struct NativeRendererSafetyTests {
  private let rect = CGRect(x: 0, y: 0, width: 20, height: 10)

  @Test func cornerRadiiWithinBoundsAreKept() {
    let radii = NativeGraphicsState.clampedCornerRadii(in: rect, cornerWidth: 4, cornerHeight: 3)
    #expect(radii.width == 4)
    #expect(radii.height == 3)
  }

  @Test func oversizedCornerRadiiClampToHalfEachSide() {
    let radii = NativeGraphicsState.clampedCornerRadii(
      in: rect, cornerWidth: 50, cornerHeight: 50)
    #expect(radii.width == 10)
    #expect(radii.height == 5)
  }

  @Test func negativeAndNonFiniteCornerRadiiBecomeZero() {
    let negative = NativeGraphicsState.clampedCornerRadii(
      in: rect, cornerWidth: -2, cornerHeight: -.infinity)
    #expect(negative.width == 0)
    #expect(negative.height == 0)
    let nan = NativeGraphicsState.clampedCornerRadii(
      in: rect, cornerWidth: .nan, cornerHeight: .infinity)
    #expect(nan.width == 0)
    #expect(nan.height == 0)
  }

  @Test func cornerRadiiUseTheStandardizedSidesOfAFlippedRect() {
    // Document rects are built as (left, top, right - left, bottom - top) and may be inverted.
    let flipped = CGRect(x: 20, y: 10, width: -20, height: -10)
    let radii = NativeGraphicsState.clampedCornerRadii(
      in: flipped, cornerWidth: 50, cornerHeight: 50)
    #expect(radii.width == 10)
    #expect(radii.height == 5)
  }

  @Test func roundedRectPathAcceptsDocumentRadii() {
    let path = NativeGraphicsState.roundedRectPath(rect, cornerWidth: 100, cornerHeight: -1)
    #expect(path.boundingBoxOfPath.width == 20)
    #expect(path.boundingBoxOfPath.height == 10)
  }

  @Test func grayscaleColourYieldsFourComponents() {
    let gray = CGColor(gray: 0.5, alpha: 0.25)
    #expect(gray.components?.count == 2)
    let rgba = NativeGradientRenderer.rgbaComponents(gray)
    #expect(rgba.count == 4)
    #expect(abs(rgba[0] - rgba[1]) < 0.001)
    #expect(abs(rgba[1] - rgba[2]) < 0.001)
    #expect(rgba[0] > 0 && rgba[0] < 1)
    #expect(abs(rgba[3] - 0.25) < 0.001)
  }

  @Test func rgbColourKeepsItsComponents() {
    let space = CGColorSpace(name: CGColorSpace.sRGB)!
    let color = CGColor(colorSpace: space, components: [0.2, 0.4, 0.6, 0.8])!
    let rgba = NativeGradientRenderer.rgbaComponents(color)
    #expect(rgba.count == 4)
    for (actual, expected) in zip(rgba, [0.2, 0.4, 0.6, 0.8] as [CGFloat]) {
      #expect(abs(actual - expected) < 0.001)
    }
  }
}

#if canImport(UIKit)
  import Foundation
  import RcNativePlayerCore
  import Testing
  import UIKit

  @testable import RcNativePlayerUIKit

  /// The UIKit renderer playing remote-core alpha22's custom visibility animations: the parent lays
  /// a canvas over a child in transition, hides the child, and draws the spec's function there —
  /// its `DrawContent` drawing the child — or the child as it is.
  @MainActor @Suite struct NativeVisibilityTransitionRenderTests {
    private typealias Op = NativeSwiftWireOpcode
    private static let boxID = 10
    private static let visibilityID = 550

    private struct Bytes {
      var data = Data()

      mutating func u8(_ value: Int) {
        data.append(UInt8(truncatingIfNeeded: value))
      }

      mutating func int(_ value: Int) {
        let raw = UInt32(bitPattern: Int32(truncatingIfNeeded: value))
        for shift in [24, 16, 8, 0] { data.append(UInt8(truncatingIfNeeded: raw >> shift)) }
      }

      mutating func float(_ value: Float) {
        int(Int(Int32(bitPattern: value.bitPattern)))
      }

      /// A NaN-boxed reference to float `id`.
      mutating func reference(_ id: Int) {
        int(Int(Int32(bitPattern: 0xff80_0000 | UInt32(id))))
      }

      /// A NaN-boxed float-expression operator.
      mutating func operation(_ operation: Int) {
        int(Int(Int32(bitPattern: 0xff80_0000 | UInt32(0x0031_0000 + operation))))
      }
    }

    /// A 200×200 document: a 50×50 red box whose visibility follows the integer named "shown", and
    /// function 100, which draws the box (`DrawContent`) and then a 10×10 black square at the box's
    /// corner, from the (progress, width, height, x, y, component) it is called with.
    private func document(
      initiallyShown: Bool, enter: Int, exit: Int, enterFunction: Int, exitFunction: Int,
      motion: Float = 300
    ) -> Data {
      var bytes = Bytes()
      bytes.u8(Op.header)
      for value in [1, 0, 0, 200, 200, 0, 0] { bytes.int(value) }
      bytes.u8(Op.functionDefine)
      bytes.int(100)
      bytes.int(6)
      for argument in 500...505 { bytes.int(argument) }
      bytes.u8(Op.drawContent)
      // 900 = x + 10, 901 = y + 10.
      for (output, argument) in [(900, 503), (901, 504)] {
        bytes.u8(Op.animatedFloat)
        bytes.int(output)
        bytes.int(3)
        bytes.reference(argument)
        bytes.float(10)
        bytes.operation(1)
      }
      bytes.u8(Op.drawRect)
      for id in [503, 504, 900, 901] { bytes.reference(id) }
      bytes.u8(Op.containerEnd)

      bytes.u8(Op.dataInt)
      bytes.int(Self.visibilityID)
      bytes.int(initiallyShown ? NativeSwiftVisibility.visible : NativeSwiftVisibility.gone)
      let name = Array("shown".utf8)
      bytes.u8(Op.namedVariable)
      bytes.int(Self.visibilityID)
      bytes.int(4)  // NativeSwiftNamedVariableType.int
      bytes.int(name.count)
      for byte in name { bytes.u8(Int(byte)) }

      bytes.u8(Op.layoutRoot)
      bytes.int(1)
      bytes.u8(Op.layoutBox)
      for value in [Self.boxID, -1, 1, 1] { bytes.int(value) }
      for opcode in [Op.modifierWidth, Op.modifierHeight] {
        bytes.u8(opcode)
        bytes.int(NativeSwiftDimensionType.exact)
        bytes.float(50)
      }
      bytes.u8(Op.modifierBackground)
      for value in [0, 0, 0, 0] { bytes.int(value) }
      for channel: Float in [1, 0, 0, 1] { bytes.float(channel) }
      bytes.int(0)
      bytes.u8(Op.modifierVisibility)
      bytes.int(Self.visibilityID)
      bytes.u8(Op.animationSpec)
      bytes.int(1)
      bytes.float(motion)
      bytes.int(Int(NativeSwiftFloatEasingType.cubicLinear.rawValue))
      bytes.float(300)
      bytes.int(Int(NativeSwiftFloatEasingType.cubicLinear.rawValue))
      bytes.int(enter)
      bytes.int(exit)
      bytes.int(enterFunction)
      bytes.int(exitFunction)
      bytes.u8(Op.layoutContent)
      bytes.int(-1)
      bytes.u8(Op.containerEnd)
      bytes.u8(Op.containerEnd)
      bytes.u8(Op.containerEnd)
      return bytes.data
    }

    private func makeView(_ snapshot: NativeSwiftDocumentSnapshot) -> NativeComponentView {
      let view = NativeComponentView(
        node: NativeNode(swiftSnapshot: snapshot.root, densityBehavior: snapshot.densityBehavior),
        images: [:], fontNames: [:], offscreenTargets: NativeOffscreenTargets(),
        customComponents: RemoteComposeNativeCustomComponentRegistry(),
        onGesture: { _, _, _ in }, onCustomReturn: { _, _, _ in })
      view.frame = CGRect(x: 0, y: 0, width: 200, height: 200)
      view.layoutIfNeeded()
      return view
    }

    private func overlay(in view: UIView) -> NativeCanvasView? {
      if view.accessibilityIdentifier == "rc-native-visibility-\(Self.boxID)",
        let canvas = view as? NativeCanvasView
      {
        return canvas
      }
      return view.subviews.lazy.compactMap { overlay(in: $0) }.first
    }

    /// What `canvas` draws, at one pixel per point.
    private func render(_ canvas: NativeCanvasView) -> CGImage? {
      let format = UIGraphicsImageRendererFormat()
      format.scale = 1
      format.opaque = false
      format.preferredRange = .standard
      return UIGraphicsImageRenderer(bounds: canvas.bounds, format: format).image { _ in
        canvas.draw(canvas.bounds)
      }.cgImage
    }

    /// The RGBA bytes at `point` of `image`, top-left origin.
    private func pixel(_ image: CGImage?, _ point: CGPoint) -> [UInt8] {
      guard let image else { return [] }
      var pixel = [UInt8](repeating: 0, count: 4)
      pixel.withUnsafeMutableBytes { bytes in
        guard
          let context = CGContext(
            data: bytes.baseAddress, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { return }
        let height = CGFloat(image.height)
        context.draw(
          image,
          in: CGRect(
            x: -point.x.rounded(.down), y: point.y.rounded(.down) + 1 - height,
            width: CGFloat(image.width), height: height))
      }
      return pixel
    }

    /// Whether `pixel` is `expected`, within a colour conversion's rounding.
    private func matches(_ pixel: [UInt8], _ expected: [UInt8]) -> Bool {
      pixel.count == expected.count
        && zip(pixel, expected).allSatisfy { abs(Int($0) - Int($1)) <= 8 }
    }

    /// A session whose box has just started changing to `visibility`, resolved `elapsed` in.
    private func transition(
      to visibility: Int, enter: Int = NativeSwiftLayoutAnimation.custom,
      exit: Int = NativeSwiftLayoutAnimation.custom, function: Int = 100, elapsed: TimeInterval
    ) throws -> (NativeSwiftDocumentSession, NativeSwiftDocumentSnapshot) {
      let session = try NativeSwiftDocumentSession.open(
        data: document(
          initiallyShown: visibility != NativeSwiftVisibility.visible, enter: enter, exit: exit,
          enterFunction: function, exitFunction: function))
      _ = try session.snapshot(timeSeconds: 1)
      #expect(session.setInteger(visibility, for: "shown"))
      _ = try session.snapshot(timeSeconds: 1)
      return (session, try session.snapshot(timeSeconds: 1 + elapsed))
    }

    @Test func anExitDrawsItsFunctionOverTheHiddenComponent() throws {
      let (session, snapshot) = try transition(to: NativeSwiftVisibility.gone, elapsed: 0.15)
      let view = makeView(snapshot)
      let box = try #require(view.descendant(withID: Self.boxID))
      #expect(box.isHidden, "the box is drawn by its overlay, not on screen")
      let canvas = try #require(overlay(in: view), "no overlay for the exiting box")
      #expect(canvas.frame == view.bounds && canvas.animatingComponent === box)
      let frame = box.frame
      #expect(frame.width == 50 && frame.height == 50)
      let image = render(canvas)
      // The function's square, over the box it drew first, at the bounds it was called with.
      #expect(matches(pixel(image, CGPoint(x: frame.minX + 5, y: frame.minY + 5)), [0, 0, 0, 255]))
      #expect(
        matches(pixel(image, CGPoint(x: frame.minX + 30, y: frame.minY + 30)), [255, 0, 0, 255]))
      // `DrawContent` clips the component to its bounds; nothing else is drawn.
      #expect(pixel(image, CGPoint(x: frame.maxX + 20, y: frame.maxY + 20))[3] == 0)

      // Once the exit has played the overlay goes and the box stays hidden, gone.
      let ended = try session.snapshot(timeSeconds: 1.5)
      #expect(
        view.update(
          node: NativeNode(swiftSnapshot: ended.root, densityBehavior: ended.densityBehavior),
          images: [:], fontNames: [:]))
      view.layoutIfNeeded()
      #expect(overlay(in: view) == nil)
      #expect(box.isHidden)
    }

    @Test func anUndefinedFunctionDrawsTheComponentAsItIs() throws {
      let (_, snapshot) = try transition(
        to: NativeSwiftVisibility.gone, function: 404, elapsed: 0.15)
      let view = makeView(snapshot)
      let canvas = try #require(overlay(in: view))
      let frame = try #require(view.descendant(withID: Self.boxID)).frame
      let image = render(canvas)
      #expect(
        matches(pixel(image, CGPoint(x: frame.minX + 5, y: frame.minY + 5)), [255, 0, 0, 255]))
    }

    @Test func anEnterAfterDrawsNothingWhileItWaits() throws {
      let after =
        NativeSwiftLayoutAnimation.custom
        | NativeSwiftAnimationSequence.after << NativeSwiftAnimationSequence.shift
      let (_, snapshot) = try transition(
        to: NativeSwiftVisibility.visible, enter: after, elapsed: 0.1)
      let view = makeView(snapshot)
      let box = try #require(view.descendant(withID: Self.boxID))
      #expect(box.isHidden)
      let canvas = try #require(overlay(in: view))
      let image = render(canvas)
      #expect(pixel(image, CGPoint(x: box.frame.midX, y: box.frame.midY))[3] == 0)
    }
  }
#endif

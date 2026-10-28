import Foundation
import Testing

@_spi(Conformance) @testable import RcNativePlayerCore

/// remote-core 1.0.0-alpha22's custom visibility animations and component offscreen buffers
/// (androidx `b34eed8a88b`), as the core decodes, sequences and resolves them. The reference
/// behaviour is `CustomVisibilityAnimationTest`, `AnimateMeasure`, `BitmapData` and `DrawToBitmap`.
@Suite struct NativeSwiftVisibilityTransitionTests {
  typealias Op = NativeSwiftWireOpcode

  private static let visibilityID = 550
  private static let boxID = 10
  private static let linear = Int(NativeSwiftFloatEasingType.cubicLinear.rawValue)

  // MARK: Writing documents

  /// `AnimationSpec`: an id, motion duration and easing, visibility duration and easing, the packed
  /// enter and exit words, and the function ids when either side is CUSTOM.
  private static func spec(
    _ writer: Writer, id: Int = 1, motion: Float = 300, visibility: Float = 300,
    easing: Int = linear, enter: Int, exit: Int, enterFunction: Int = -1, exitFunction: Int = -1
  ) {
    writer.u8(Op.animationSpec).int(id).float(motion).int(easing).float(visibility).int(easing)
      .int(enter).int(exit)
    let custom = NativeSwiftLayoutAnimation.custom
    if NativeSwiftLayoutAnimation.kind(ofWord: enter) == custom
      || NativeSwiftLayoutAnimation.kind(ofWord: exit) == custom
    {
      writer.int(enterFunction).int(exitFunction)
    }
  }

  private static func packed(_ animation: Int, _ sequence: Int) -> Int {
    animation | sequence << NativeSwiftAnimationSequence.shift
  }

  /// A function drawing the rectangle (x, y, width, progress × 100) from the arguments it is
  /// called with — 0 for one it does not declare — then its component.
  private static func boundsFunction(_ writer: Writer, id: Int, arguments: [Int]) {
    writer.u8(Op.functionDefine).int(id).int(arguments.count)
    for argument in arguments { writer.int(argument) }
    func argument(_ index: Int) -> Int {
      arguments.indices.contains(index)
        ? Writer.nanReference(arguments[index]) : Writer.literal(0)
    }
    typealias Argument = NativeSwiftVisibilityFunctionArgument
    writer.u8(Op.animatedFloat).int(900).int(3).int(argument(Argument.progress))
      .int(Writer.literal(100)).int(Writer.floatOperator(NativeSwiftFloatOperator.mul))
    writer.u8(Op.drawRect).int(argument(Argument.x)).int(argument(Argument.y))
      .int(argument(Argument.width)).int(Writer.nanReference(900))
    writer.u8(Op.drawContent)
    writer.u8(Op.containerEnd)
  }

  /// A root holding one 100×100 box whose visibility follows the integer the host names "shown".
  private static func document(
    initiallyShown: Bool = true, specs: (Writer) -> Void, functions: (Writer) -> Void = { _ in }
  ) -> Data {
    let writer = Writer()
    writer.header(width: 400, height: 400)
    functions(writer)
    writer.u8(Op.dataInt).int(visibilityID)
      .int(initiallyShown ? NativeSwiftVisibility.visible : NativeSwiftVisibility.gone)
    writer.namedVariable(id: visibilityID, type: NativeSwiftNamedVariableType.int, name: "shown")
    writer.u8(Op.layoutRoot).int(1)
    writer.u8(Op.layoutBox).int(boxID).int(-1).int(1).int(1)
    writer.u8(Op.modifierWidth).int(NativeSwiftDimensionType.exact).float(100)
    writer.u8(Op.modifierHeight).int(NativeSwiftDimensionType.exact).float(100)
    writer.u8(Op.modifierVisibility).int(visibilityID)
    specs(writer)
    writer.u8(Op.layoutContent).int(-1).u8(Op.containerEnd)
    writer.u8(Op.containerEnd)
    writer.u8(Op.containerEnd)
    return writer.data
  }

  private static func box(_ snapshot: NativeSwiftDocumentSnapshot) throws -> NativeSwiftNodeSnapshot
  {
    func find(_ node: NativeSwiftNodeSnapshot) -> NativeSwiftNodeSnapshot? {
      if node.componentID == boxID { return node }
      return node.children.lazy.compactMap(find).first
    }
    return try #require(find(snapshot.root), "the box did not decode")
  }

  private static func refusal(_ data: Data) -> NativeSwiftCoreError? {
    do {
      _ = try NativeSwiftDocumentSession.open(data: data).snapshot()
      return nil
    } catch let error as NativeSwiftCoreError {
      return error
    } catch {
      Issue.record("unexpected error \(error)")
      return nil
    }
  }

  // MARK: Decoding

  @Test func aSpecKeepsItsFunctionIDsAndSequences() throws {
    let custom = NativeSwiftLayoutAnimation.custom
    let data = Self.document(specs: {
      Self.spec(
        $0, enter: Self.packed(custom, NativeSwiftAnimationSequence.after),
        exit: Self.packed(
          NativeSwiftLayoutAnimation.slideLeft, NativeSwiftAnimationSequence.before),
        enterFunction: 42, exitFunction: 43)
    })
    let spec = try #require(
      try Self.box(NativeSwiftDocumentSession.open(data: data).snapshot()).animationSpec)
    #expect(spec.enterFunctionID == 42 && spec.exitFunctionID == 43)
    #expect(spec.enterKind == custom && spec.exitKind == NativeSwiftLayoutAnimation.slideLeft)
    #expect(spec.enterSequence == NativeSwiftAnimationSequence.after)
    #expect(spec.exitSequence == NativeSwiftAnimationSequence.before)

    // Unknown values read as the reference reads them: FADE_IN and CONCURRENT.
    let unknown = NativeSwiftAnimationSpec(
      motionDuration: 1, motionEasingType: 1, visibilityDuration: 1, visibilityEasingType: 1,
      enterAnimation: 0x0907, exitAnimation: 0x0309)
    #expect(unknown.enterKind == NativeSwiftLayoutAnimation.particle)
    #expect(unknown.exitKind == NativeSwiftLayoutAnimation.fadeIn)
    #expect(unknown.enterSequence == NativeSwiftAnimationSequence.concurrent)
    #expect(unknown.exitSequence == NativeSwiftAnimationSequence.concurrent)
    #expect(unknown.enterFunctionID == NativeSwiftFunctionDefine.unsetID)
  }

  @Test func aFunctionDefinitionDecodesAsItsOwnBody() throws {
    let data = Self.document(
      specs: { Self.spec($0, enter: NativeSwiftLayoutAnimation.custom, exit: 0, enterFunction: 7) },
      functions: { Self.boundsFunction($0, id: 7, arguments: [500, 501, 502, 503, 504, 505]) })
    let session = try NativeSwiftDocumentSession.open(data: data)
    let snapshot = try session.snapshot()
    // The body draws nowhere in the tree: the root and the box carry no commands.
    #expect(snapshot.root.commands.isEmpty)
    #expect(try Self.box(snapshot).commands.isEmpty)
    #expect(try Self.box(snapshot).visibility == NativeSwiftVisibility.visible)
    // Its expression is not the frame's: the frame evaluates nothing at 900.
    #expect(try session.probeValues(timeSeconds: 0).floats[900] == nil)
    // The definition is a single top-level operation, as the reference's container is.
    #expect(session.operationCensus.contains(Op.functionDefine))
  }

  @Test func whatAFunctionBodyMayHoldIsBounded() {
    func function(_ body: (Writer) -> Void, arguments: Int = 1) -> Data {
      Self.document(
        specs: { _ in },
        functions: { writer in
          writer.u8(Op.functionDefine).int(7).int(arguments)
          for index in 0..<max(arguments, 0) { writer.int(500 + index) }
          body(writer)
          writer.u8(Op.containerEnd)
        })
    }
    // A conditional would be decided once at decode, not per call: refused, explicitly.
    let conditional = Self.refusal(
      function({
        $0.u8(Op.conditionalOperations).u8(NativeSwiftConditionalType.equal).float(0).float(0)
        $0.u8(Op.containerEnd)
      }))
    #expect(conditional?.isUnsupported == true)
    let nested = Self.refusal(
      function({ $0.u8(Op.functionDefine).int(8).int(0).u8(Op.containerEnd) }))
    #expect(nested?.isUnsupported == true)
    let integerExpression = Self.refusal(
      function({ $0.u8(Op.integerExpression).int(600).int(0).int(1).int(1) }))
    #expect(integerExpression?.isUnsupported == true)
    // An animated expression would need a runtime per call.
    let animated = Self.refusal(
      function({
        $0.u8(Op.animatedFloat).int(600).int(1 | 1 << 16).int(Writer.literal(1))
          .int(Writer.literal(300))
      }))
    #expect(animated?.isUnsupported == true)
    // `FloatFunctionDefine.read`'s own bound, and an unclosed body, are malformed.
    let tooMany = Self.refusal(function({ _ in }, arguments: 33))
    #expect(tooMany != nil && tooMany?.isUnsupported == false)
    let negative = Self.refusal(function({ _ in }, arguments: -1))
    #expect(negative != nil && negative?.isUnsupported == false)
    // Unclosed at the end of the document.
    let unclosedWriter = Writer()
    unclosedWriter.raw(Self.document(specs: { _ in }))
    unclosedWriter.u8(Op.functionDefine).int(7).int(0)
    let unclosed = Self.refusal(unclosedWriter.data)
    #expect(unclosed != nil && unclosed?.isUnsupported == false)
    #expect(Self.refusal(function({ _ in }, arguments: 32)) == nil)
  }

  @Test func aComponentOffscreenBufferIsATargetNotAnImage() throws {
    // `createOffscreenBitmap`: RAW8888, a placeholder 1×1, encoding 4, and an empty payload (the
    // active component) or the component's id as four big-endian bytes.
    func buffer(_ writer: Writer, id: Int, component: Int?) {
      let encoding = NativeSwiftBitmapEncoding.componentOffscreenBuffer
      writer.u8(Op.dataBitmap).int(id).int(NativeSwiftBitmapType.raw8888 << 16 | 1)
        .int(encoding << 16 | 1)
      if let component {
        writer.int(4).int(component)
      } else {
        writer.int(0)
      }
    }
    let writer = Writer()
    writer.header(width: 100, height: 100)
    buffer(writer, id: 20, component: nil)
    buffer(writer, id: 21, component: 77)
    writer.u8(Op.dataInt).int(30).int(88)
    writer.u8(Op.layoutRoot).int(1).u8(Op.layoutCanvas).int(2).int(-1)
    // Into the active component's buffer, then capturing component 30 — an integer naming 88.
    writer.u8(Op.drawToBitmap).int(20).int(0).int(-1)
    writer.u8(Op.drawContent)
    writer.u8(Op.drawToBitmap).int(0).int(0).int(0)
    writer.u8(Op.drawToBitmap).int(21).int(NativeSwiftDrawToBitmapMode.componentID).int(30)
    writer.u8(Op.drawContent)
    writer.u8(Op.drawToBitmap).int(0).int(0).int(0)
    writer.u8(Op.drawBitmap).int(21).float(0).float(0).float(10).float(10).int(0)
    // Outside a component target DrawContent is the modifier marker it always was.
    writer.u8(Op.drawContent)
    writer.u8(Op.containerEnd).u8(Op.containerEnd)
    let snapshot = try NativeSwiftDocumentSession.open(data: writer.data).snapshot()
    #expect(snapshot.images.isEmpty, "a component buffer was exported as an image")
    let commands = snapshot.root.children[0].commands
    #expect(
      commands.map(\.kind) == [
        NativeSwiftDrawKind.drawToBitmap, NativeSwiftDrawKind.drawContent,
        NativeSwiftDrawKind.drawToBitmap, NativeSwiftDrawKind.drawToBitmap,
        NativeSwiftDrawKind.drawContent, NativeSwiftDrawKind.drawToBitmap,
        NativeSwiftDrawKind.bitmap,
      ])
    let active = try #require(commands[0].offscreenTarget)
    #expect(active.isComponentBuffer && active.capturesComponent)
    #expect(active.bufferComponentID == NativeSwiftComponentOffscreenBuffer.activeComponent)
    #expect(active.targetComponentID == nil && active.colorARGB == 0xffff_ffff)
    let captured = try #require(commands[3].offscreenTarget)
    #expect(captured.isComponentBuffer && captured.bufferComponentID == 77)
    #expect(captured.capturedComponentID == 88 && captured.colorARGB == 0)
    // The buffer's own component wins over the redirect's, as `ensureOffscreenBitmap` looks.
    #expect(captured.targetComponentID == 77)
    #expect(commands[6].image?.sourceIsWholeBitmap == true)
    #expect(commands[2].offscreenTarget?.capturesComponent == false)
  }

  @Test func aBufferIsSizedToItsComponentWithinTheReferenceBounds() {
    typealias Buffer = NativeSwiftComponentOffscreenBuffer
    #expect(Buffer.size(x: 0, y: 0, width: 100, height: 40)! == (100, 40))
    // Floored origin, size widened to cover the component from there.
    #expect(Buffer.size(x: 10.5, y: 3.25, width: 20.2, height: 10)! == (21, 11))
    #expect(Buffer.size(x: 0, y: 0, width: 0, height: -5)! == (1, 1))
    #expect(Buffer.size(x: 0, y: 0, width: 8_000, height: 655)! == (8_000, 655))
    #expect(Buffer.size(x: 0, y: 0, width: 8_001, height: 1) == nil)
    #expect(Buffer.size(x: 0, y: 0, width: 8_000, height: 656) == nil, "over 20 MiB")
    #expect(Buffer.size(x: .nan, y: 0, width: 1, height: 1) == nil)
    #expect(Buffer.size(x: 0, y: 0, width: -.infinity, height: 1) == nil)
    #expect(Buffer.size(x: 0, y: 0, width: -1e30, height: 1)! == (1, 1))
    #expect(Buffer.componentID(payload: Data([0, 0, 1, 2, 9])) == 258)
    #expect(Buffer.componentID(payload: Data([0, 0, 1])) == Buffer.activeComponent)
  }

  // MARK: Sequencing

  @Test func eachSequenceOrdersTheVisibilityAnimationAgainstTheLayout() {
    let visible = NativeSwiftVisibility.visible
    let gone = NativeSwiftVisibility.gone
    func timeline(enter: Int, exit: Int, from: Int, to: Int) -> NativeSwiftVisibilityTimeline {
      NativeSwiftVisibilityTimeline(
        spec: NativeSwiftAnimationSpec(
          motionDuration: 400, motionEasingType: Self.linear, visibilityDuration: 600,
          visibilityEasingType: Self.linear, enterAnimation: enter, exitAnimation: exit),
        from: from, to: to)
    }
    let custom = NativeSwiftLayoutAnimation.custom
    let before = Self.packed(custom, NativeSwiftAnimationSequence.before)
    let after = Self.packed(custom, NativeSwiftAnimationSequence.after)

    // CustomVisibilityAnimationTest.testBeforeAndAfterLayoutSequencing: an exit BEFORE plays at
    // once and keeps its space until it has played.
    let exitBefore = timeline(enter: after, exit: before, from: visible, to: gone)
    let halfway = exitBefore.state(elapsedMillis: 300)
    #expect(halfway.phase == .playing && abs(halfway.progress - 0.5) < 0.05)
    #expect(halfway.layoutVisibility == visible && !halfway.isDone)
    let played = exitBefore.state(elapsedMillis: 600)
    #expect(played.isDone && played.layoutVisibility == gone)

    // An enter AFTER takes its space at once, paints nothing through the motion, then plays.
    let enterAfter = timeline(enter: after, exit: before, from: gone, to: visible)
    let moving = enterAfter.state(elapsedMillis: 200)
    #expect(moving.phase == .waiting && moving.progress == 0 && moving.layoutVisibility == visible)
    let entering = enterAfter.state(elapsedMillis: 700)
    #expect(entering.phase == .playing && abs(entering.progress - 0.5) < 0.05)
    #expect(!enterAfter.state(elapsedMillis: 999).isDone)
    #expect(enterAfter.state(elapsedMillis: 1_000).isDone)

    // An exit AFTER paints as it is through the motion; its space goes at once.
    let exitAfter = timeline(enter: custom, exit: after, from: visible, to: gone)
    let holding = exitAfter.state(elapsedMillis: 100)
    #expect(holding.phase == .holding && holding.layoutVisibility == gone)
    #expect(exitAfter.state(elapsedMillis: 400).phase == .playing)

    // CONCURRENT runs until both the motion and the visibility animation are over; an enter
    // BEFORE takes its space at once, as the reference measures only an exit as held.
    let concurrent = timeline(enter: custom, exit: custom, from: visible, to: gone)
    #expect(concurrent.state(elapsedMillis: 0).progress == 0)
    #expect(concurrent.state(elapsedMillis: 0).layoutVisibility == gone)
    #expect(!concurrent.state(elapsedMillis: 599).isDone)
    #expect(concurrent.state(elapsedMillis: 600).progress == 1)
    let enterBefore = timeline(enter: before, exit: custom, from: gone, to: visible)
    #expect(enterBefore.state(elapsedMillis: 0).layoutVisibility == visible)
    #expect(enterBefore.state(elapsedMillis: 600).isDone)

    // Time before the start reads as the start; a zero duration has nothing to animate.
    #expect(concurrent.state(elapsedMillis: -50).progress == 0)
    let instant = NativeSwiftVisibilityTimeline(
      spec: NativeSwiftAnimationSpec(
        motionDuration: 0, motionEasingType: 1, visibilityDuration: 0, visibilityEasingType: 1,
        enterAnimation: custom, exitAnimation: custom), from: visible, to: gone)
    #expect(instant.state(elapsedMillis: 0).isDone && instant.state(elapsedMillis: 0).progress == 1)

    // Only a change with a visible side is a transition.
    #expect(
      !NativeSwiftVisibilityTimeline.isTransition(from: gone, to: NativeSwiftVisibility.invisible))
    #expect(
      NativeSwiftVisibilityTimeline.isTransition(from: NativeSwiftVisibility.invisible, to: visible)
    )
  }

  // MARK: Playing in a session

  @Test func aCustomExitRunsItsFunctionAcrossFrames() throws {
    // CustomVisibilityAnimationTest.testCustomExitAnimationExecution, frame by frame.
    let custom = NativeSwiftLayoutAnimation.custom
    let data = Self.document(
      specs: { Self.spec($0, enter: custom, exit: custom, enterFunction: 100, exitFunction: 100) },
      functions: { Self.boundsFunction($0, id: 100, arguments: [500, 501, 502, 503, 504, 505]) })
    let session = try NativeSwiftDocumentSession.open(data: data)
    let first = try session.snapshot(timeSeconds: 1)
    #expect(try Self.box(first).visibilityTransition == nil, "the first frame animated")
    #expect(!first.needsContinuousFrames)

    #expect(session.setInteger(NativeSwiftVisibility.gone, for: "shown"))
    let start = try Self.box(session.snapshot(timeSeconds: 1))
    let started = try #require(start.visibilityTransition)
    #expect(started.phase == .playing && started.progress == 0 && !started.isEntering)
    #expect(start.visibility == NativeSwiftVisibility.gone, "a concurrent exit gives up its space")

    let middle = try session.snapshot(timeSeconds: 1.15)
    #expect(middle.needsContinuousFrames)
    let transition = try #require(try Self.box(middle).visibilityTransition)
    #expect(abs(transition.progress - 0.5) < 0.01)
    let function = try #require(transition.function)
    #expect(function.id == 100 && function.componentID == Self.boxID)
    let arguments = function.arguments(x: 10.5, y: 3.25, width: 20.2, height: 10)
    #expect(arguments[0] == transition.progress && Array(arguments[1...]) == [21, 11, 10, 3, 10])
    let commands = try function.commands(x: 4, y: 6, width: 100, height: 100)
    #expect(commands.map(\.kind) == [NativeSwiftDrawKind.rect, NativeSwiftDrawKind.drawContent])
    #expect(Array(commands[0].values.prefix(3)) == [4, 6, 100])
    #expect(abs(commands[0].values[3] - 50) < 1)
    // Resolving the same frame again is the same frame.
    #expect(
      try Self.box(session.snapshot(timeSeconds: 1.15)).visibilityTransition?.progress
        == transition.progress)

    let end = try session.snapshot(timeSeconds: 1.35)
    #expect(try Self.box(end).visibilityTransition == nil)
    #expect(try Self.box(end).visibility == NativeSwiftVisibility.gone)
    #expect(!end.needsContinuousFrames)
  }

  @Test func anExitBeforeHoldsItsSpaceAndAnEnterAfterWaits() throws {
    let custom = NativeSwiftLayoutAnimation.custom
    let data = Self.document(
      specs: {
        Self.spec(
          $0, motion: 400, visibility: 600,
          enter: Self.packed(custom, NativeSwiftAnimationSequence.after),
          exit: Self.packed(custom, NativeSwiftAnimationSequence.before), enterFunction: 301,
          exitFunction: 302)
      },
      functions: {
        Self.boundsFunction($0, id: 301, arguments: [701])
        Self.boundsFunction($0, id: 302, arguments: [702])
      })
    let session = try NativeSwiftDocumentSession.open(data: data)
    _ = try session.snapshot(timeSeconds: 1)
    #expect(session.setInteger(NativeSwiftVisibility.gone, for: "shown"))
    _ = try session.snapshot(timeSeconds: 1)
    let exiting = try Self.box(session.snapshot(timeSeconds: 1.3))
    #expect(exiting.visibility == NativeSwiftVisibility.visible, "the exit gave up its space")
    #expect(exiting.visibilityTransition?.function?.id == 302)
    #expect(abs((exiting.visibilityTransition?.progress ?? 0) - 0.5) < 0.05)
    #expect(
      try Self.box(session.snapshot(timeSeconds: 1.6)).visibility == NativeSwiftVisibility.gone)

    #expect(session.setInteger(NativeSwiftVisibility.visible, for: "shown"))
    _ = try session.snapshot(timeSeconds: 3)
    let waiting = try Self.box(session.snapshot(timeSeconds: 3.2))
    #expect(waiting.visibility == NativeSwiftVisibility.visible)
    #expect(waiting.visibilityTransition?.phase == .waiting)
    let entering = try #require(
      try Self.box(session.snapshot(timeSeconds: 3.7)).visibilityTransition)
    #expect(entering.phase == .playing && abs(entering.progress - 0.5) < 0.05)
    // A function declaring only the progress argument receives only that.
    let commands = try #require(entering.function).commands(x: 0, y: 0, width: 100, height: 100)
    #expect(commands.first.map { Array($0.values.prefix(3)) } == [0, 0, 0])
    #expect(abs((commands.first?.values.last ?? 0) - 50) < 5)
  }

  @Test func onlyACustomSideOfAnEnabledSpecAnimates() throws {
    let custom = NativeSwiftLayoutAnimation.custom
    func firstChange(_ specs: @escaping (Writer) -> Void, to visibility: Int) throws
      -> NativeSwiftNodeSnapshot
    {
      let data = Self.document(
        initiallyShown: visibility != NativeSwiftVisibility.visible, specs: specs,
        functions: { Self.boundsFunction($0, id: 9, arguments: [500]) })
      let session = try NativeSwiftDocumentSession.open(data: data)
      _ = try session.snapshot(timeSeconds: 0)
      #expect(session.setInteger(visibility, for: "shown"))
      return try Self.box(session.snapshot(timeSeconds: 0.1))
    }
    // A built-in exit lands at once; the custom enter of the same spec is not involved.
    let fade = try firstChange(
      { Self.spec($0, enter: custom, exit: NativeSwiftLayoutAnimation.fadeOut, enterFunction: 9) },
      to: NativeSwiftVisibility.gone)
    #expect(fade.visibilityTransition == nil && fade.visibility == NativeSwiftVisibility.gone)
    // `AnimationSpec.DISABLED`: id 0.
    let disabled = try firstChange(
      { Self.spec($0, id: 0, enter: custom, exit: custom, enterFunction: 9, exitFunction: 9) },
      to: NativeSwiftVisibility.gone)
    #expect(disabled.visibilityTransition == nil)
    // No spec is AnimationSpec.DEFAULT, which fades.
    #expect(try firstChange({ _ in }, to: NativeSwiftVisibility.gone).visibilityTransition == nil)
    // A function the document does not define: the transition plays, without a function, and the
    // host paints the component as it is.
    let undefined = try firstChange(
      { Self.spec($0, enter: custom, exit: custom, enterFunction: 9, exitFunction: 404) },
      to: NativeSwiftVisibility.gone)
    #expect(undefined.visibilityTransition?.functionID == 404)
    #expect(
      undefined.visibilityTransition != nil && undefined.visibilityTransition?.function == nil)
    let entering = try firstChange(
      { Self.spec($0, enter: custom, exit: custom, enterFunction: 9, exitFunction: 404) },
      to: NativeSwiftVisibility.visible)
    #expect(entering.visibilityTransition?.function?.id == 9)
    #expect(entering.visibilityTransition?.isEntering == true)
  }

  @Test func aChangeMidFlightRetargetsAndAnimationsCanBeTurnedOff() throws {
    let custom = NativeSwiftLayoutAnimation.custom
    let data = Self.document(
      specs: { Self.spec($0, enter: custom, exit: custom, enterFunction: 9, exitFunction: 9) },
      functions: { Self.boundsFunction($0, id: 9, arguments: [500]) })
    let session = try NativeSwiftDocumentSession.open(data: data)
    _ = try session.snapshot(timeSeconds: 0)
    #expect(session.setInteger(NativeSwiftVisibility.gone, for: "shown"))
    _ = try session.snapshot(timeSeconds: 1)
    #expect(try Self.box(session.snapshot(timeSeconds: 1.1)).visibilityTransition != nil)

    // Back to visible halfway: the enter starts again from the visibility it was heading to.
    #expect(session.setInteger(NativeSwiftVisibility.visible, for: "shown"))
    let reversed = try #require(
      try Self.box(session.snapshot(timeSeconds: 1.15)).visibilityTransition)
    #expect(reversed.isEntering && reversed.from == NativeSwiftVisibility.gone)
    #expect(reversed.progress == 0)

    // A detached copy carries the transition with it.
    let copy = session.detachedCopy()
    #expect(try Self.box(copy.snapshot(timeSeconds: 1.3)).visibilityTransition != nil)

    // Reduce Motion: the playing transition ends and later changes land at once.
    session.setVisibilityAnimationsEnabled(false)
    let stopped = try session.snapshot(timeSeconds: 1.2)
    #expect(try Self.box(stopped).visibilityTransition == nil && !stopped.needsContinuousFrames)
    #expect(session.setInteger(NativeSwiftVisibility.gone, for: "shown"))
    #expect(try Self.box(session.snapshot(timeSeconds: 1.25)).visibilityTransition == nil)
  }

  @Test func aBodyRunsItsExpressionsAndDrawsInOrder() throws {
    // `900 = 10; drawRect(…900…); 900 = 20; drawRect(…900…)`: each draw sees the value at its own
    // position, as the reference runs the body sequentially.
    let custom = NativeSwiftLayoutAnimation.custom
    let data = Self.document(
      specs: { Self.spec($0, enter: custom, exit: custom, enterFunction: 9, exitFunction: 9) },
      functions: { writer in
        writer.u8(Op.functionDefine).int(9).int(1).int(500)
        writer.u8(Op.animatedFloat).int(900).int(1).int(Writer.literal(10))
        writer.u8(Op.drawRect).float(0).float(0).int(Writer.nanReference(900)).float(1)
        writer.u8(Op.animatedFloat).int(900).int(1).int(Writer.literal(20))
        writer.u8(Op.drawRect).float(0).float(0).int(Writer.nanReference(900)).float(1)
        writer.u8(Op.containerEnd)
      })
    let session = try NativeSwiftDocumentSession.open(data: data)
    _ = try session.snapshot(timeSeconds: 0)
    #expect(session.setInteger(NativeSwiftVisibility.gone, for: "shown"))
    let function = try #require(
      try Self.box(session.snapshot(timeSeconds: 0.1)).visibilityTransition?.function)
    let commands = try function.commands(x: 0, y: 0, width: 10, height: 10)
    #expect(commands.map { $0.values[2] } == [10, 20])
  }

  @Test func aFunctionFadesByAPaintAlphaBoundToItsProgress() throws {
    // `PaintData(ALPHA, progress); DrawContent`: the alpha resolves against the call's arguments.
    let custom = NativeSwiftLayoutAnimation.custom
    let data = Self.document(
      specs: { Self.spec($0, enter: custom, exit: custom, enterFunction: 9, exitFunction: 9) },
      functions: { writer in
        writer.u8(Op.functionDefine).int(9).int(1).int(500)
        writer.u8(Op.paintValues).int(2).int(NativeSwiftPaintCommand.alpha)
          .int(Writer.nanReference(500))
        writer.u8(Op.drawContent)
        writer.u8(Op.containerEnd)
      })
    let session = try NativeSwiftDocumentSession.open(data: data)
    _ = try session.snapshot(timeSeconds: 1)
    #expect(session.setInteger(NativeSwiftVisibility.gone, for: "shown"))
    _ = try session.snapshot(timeSeconds: 1)  // The exit starts here.
    let transition = try #require(
      try Self.box(session.snapshot(timeSeconds: 1.15)).visibilityTransition)
    let function = try #require(transition.function)
    let commands = try function.commands(x: 0, y: 0, width: 10, height: 10)
    #expect(commands.map(\.kind) == [NativeSwiftDrawKind.drawContent])
    #expect(abs(commands[0].alpha - transition.progress) < 0.001)
    #expect(transition.progress > 0.1 && transition.progress < 0.9)
  }

  @Test func aFunctionThatCannotEvaluateThrowsForTheHostToDegrade() throws {
    let custom = NativeSwiftLayoutAnimation.custom
    let data = Self.document(
      specs: { Self.spec($0, enter: custom, exit: custom, enterFunction: 9, exitFunction: 9) },
      functions: { writer in
        // width ÷ (progress − progress): not a finite number.
        writer.u8(Op.functionDefine).int(9).int(2).int(500).int(501)
        writer.u8(Op.animatedFloat).int(900).int(5).int(Writer.nanReference(501))
          .int(Writer.nanReference(500)).int(Writer.nanReference(500))
          .int(Writer.floatOperator(NativeSwiftFloatOperator.sub))
          .int(Writer.floatOperator(NativeSwiftFloatOperator.div))
        writer.u8(Op.drawRect).float(0).float(0).int(Writer.nanReference(900)).float(1)
        writer.u8(Op.containerEnd)
      })
    let session = try NativeSwiftDocumentSession.open(data: data)
    _ = try session.snapshot(timeSeconds: 0)
    #expect(session.setInteger(NativeSwiftVisibility.gone, for: "shown"))
    // The frame itself resolves: the body is not part of it.
    let function = try #require(
      try Self.box(session.snapshot(timeSeconds: 0.1)).visibilityTransition?.function)
    #expect(throws: NativeSwiftCoreError.self) {
      _ = try function.commands(x: 0, y: 0, width: 10, height: 10)
    }
  }

  @Test func aFunctionDrawingANonFiniteValueThrows() throws {
    let custom = NativeSwiftLayoutAnimation.custom
    let data = Self.document(
      specs: { Self.spec($0, enter: custom, exit: custom, enterFunction: 9, exitFunction: 9) },
      functions: { writer in
        // A rectangle as wide as an argument the call does not load: NaN reads as 0, but an
        // infinite literal is drawn as written.
        writer.u8(Op.functionDefine).int(9).int(1).int(500)
        writer.u8(Op.drawRect).float(0).float(0).float(.infinity).float(1)
        writer.u8(Op.containerEnd)
      })
    let session = try NativeSwiftDocumentSession.open(data: data)
    _ = try session.snapshot(timeSeconds: 0)
    #expect(session.setInteger(NativeSwiftVisibility.gone, for: "shown"))
    let function = try #require(
      try Self.box(session.snapshot(timeSeconds: 0.1)).visibilityTransition?.function)
    #expect(throws: NativeSwiftCoreError.self) {
      _ = try function.commands(x: 0, y: 0, width: 10, height: 10)
    }
  }
}

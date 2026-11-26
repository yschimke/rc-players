import RcWireOperations
import Testing

/// The generated bindings' hand-written helpers, whose layouts the fork names from the schema:
/// checked against the layouts AndroidX writes.
@Suite struct HelperTests {
  @Test func animationSpecPacksTheSchemaLayout() throws {
    let packed = FloatAnimationSpec.pack(
      duration: 2, type: AnimationEasing.cubicLinear, spec: [0.25, 0.75], initialValue: 3, wrap: 4)
    let flags = packed[1].bitPattern
    // AndroidX's `FloatAnimation.packToFloatArray`: type in bits 0-7, wrap at 8, initial value
    // at 9, the parameter count from bit 16.
    #expect(flags == 4 | 1 << 8 | 1 << 9 | 2 << 16)
    let spec = try FloatAnimationSpec.unpack(packed)
    #expect(spec.duration == 2)
    #expect(spec.type == AnimationEasing.cubicLinear)
    #expect(spec.params == [0.25, 0.75])
    #expect(spec.initialValue == 3)
    #expect(spec.wrap == 4)
  }

  @Test func pathEffectsRoundTrip() throws {
    for effect in [
      PathEffect.dash(phase: 1, intervals: [2, 3, 4]),
      .discrete(segmentLength: 5, deviation: 6),
      .pathDash(shapeId: 7, advance: 8, phase: 9, style: 1),
      .sum(.discrete(segmentLength: 1, deviation: 2), .dash(phase: 0, intervals: [1])),
    ] {
      #expect(PathEffect.decode(try effect.encode()) == effect)
    }
  }

  @Test func pathCommandsDecodeAtTheirOffsets() {
    func tag(_ id: Int32) -> Float {
      Float(bitPattern: UInt32(bitPattern: IdEncoding.nanPrefix | id))
    }
    // A move is its tag and a point; a line is its tag, two words of padding, then its end point.
    let data: [Float] = [
      tag(PathCommandTags.move), 1, 2,
      tag(PathCommandTags.line), 0, 0, 3, 4,
      tag(PathCommandTags.close),
      tag(PathCommandTags.done),
    ]
    #expect(
      PathCommand.decode(data) == [.moveTo(x: 1, y: 2), .lineTo(x: 3, y: 4), .close, .done])
  }

  @Test func timeIdsAreTheSchemasTimeVariables() {
    #expect(RemoteContextIds.isTime(RemoteContextIds.idContinuousSec))
    #expect(!RemoteContextIds.isTime(RemoteContextIds.idDensity))
  }
}

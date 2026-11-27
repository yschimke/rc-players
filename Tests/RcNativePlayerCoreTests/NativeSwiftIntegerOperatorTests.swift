import Testing

@testable import RcNativePlayerCore

/// Integer expression operators against AndroidX's `IntegerExpressionEvaluator`.
@Suite struct NativeSwiftIntegerOperatorTests {
  private func evaluate(_ value: Int, _ operation: Int) throws -> Int {
    // [value, op]: the operator is marked, the literal is not.
    try NativeSwiftIntegerExpression.evaluate(
      mask: 0b10, tokens: [value, NativeSwiftIntegerOperator.offset + operation], values: [:],
      offset: 0)
  }

  /// `I_SIGN` is `(v >> 31) | (-v >>> 31)`: the second shift is logical.
  @Test func signIsTheSignum() throws {
    let sign = NativeSwiftIntegerOperator.sign
    #expect(try evaluate(7, sign) == 1)
    #expect(try evaluate(1, sign) == 1)
    #expect(try evaluate(0, sign) == 0)
    #expect(try evaluate(-3, sign) == -1)
    #expect(try evaluate(Int(Int32.max), sign) == 1)
    #expect(try evaluate(Int(Int32.min), sign) == -1)
  }
}

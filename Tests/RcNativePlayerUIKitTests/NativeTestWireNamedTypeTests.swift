import RcNativePlayerTestDocuments
import Testing

@testable import RcNativePlayerCore

/// `NativeTestWire.NamedType` copies the core's internal `NativeSwiftNamedVariableType` so the
/// test documents can be written without `@testable`; this keeps the copy equal to the core.
@Suite struct NativeTestWireNamedTypeTests {
  @Test func everyNamedTypeIsTheCoresValue() {
    typealias Copy = NativeTestWire.NamedType
    typealias Core = NativeSwiftNamedVariableType
    #expect(Copy.string == Core.string)
    #expect(Copy.float == Core.float)
    #expect(Copy.color == Core.color)
    #expect(Copy.image == Core.image)
    #expect(Copy.int == Core.int)
    #expect(Copy.long == Core.long)
    #expect(Copy.floatArray == Core.floatArray)
  }
}

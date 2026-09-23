import Foundation

struct ParsedFloatExpression {
  let id: Int
  let words: [UInt32]
  let animationWords: [UInt32]?
}

struct ParsedIntegerExpression {
  let mask: Int
  let tokens: [Int]
}

/// A `MATRIX_EXPRESSION` held as it arrived on the wire. The expression is RPN over a small matrix
/// stack and is evaluated at snapshot time, once the floats its operands name have resolved.
struct ParsedMatrixExpression {
  let id: Int
  let type: Int
  let words: [UInt32]
}

struct ParsedMatrixVectorMath {
  let type: Int
  let outputIDs: [Int]
  let matrixID: Int
  let inputWords: [UInt32]
}

enum NativeSwiftIntegerExpression {
  static func evaluate(mask: Int, tokens: [Int], values: [Int: Int]) throws -> Int {
    var stack: [Int32] = []
    func pop(_ count: Int) throws -> [Int32] {
      guard stack.count >= count else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Integer expression stack underflow")
      }
      let result = Array(stack.suffix(count))
      stack.removeLast(count)
      return result
    }
    for (index, token) in tokens.enumerated() {
      let marked = UInt32(bitPattern: Int32(mask)) & (UInt32(1) << UInt32(index & 31)) != 0
      if !marked || token < 65_536 {
        stack.append(Int32(truncatingIfNeeded: marked ? values[token] ?? 0 : token))
        continue
      }
      let operation = token - 65_536
      if (1...14).contains(operation) {
        let value = try pop(2)
        let left = value[0]
        let right = value[1]
        switch operation {
        case 1: stack.append(left &+ right)
        case 2: stack.append(left &- right)
        case 3: stack.append(left &* right)
        case 4: stack.append(right == 0 || (left == .min && right == -1) ? 0 : left / right)
        case 5: stack.append(right == 0 || (left == .min && right == -1) ? 0 : left % right)
        case 6: stack.append(left << (right & 31))
        case 7: stack.append(left >> (right & 31))
        case 8:
          stack.append(Int32(bitPattern: UInt32(bitPattern: left) >> UInt32(right & 31)))
        case 9: stack.append(left | right)
        case 10: stack.append(left & right)
        case 11: stack.append(left ^ right)
        case 12: stack.append((left ^ (right >> 31)) &- (right >> 31))
        case 13: stack.append(min(left, right))
        default: stack.append(max(left, right))
        }
      } else if (15...20).contains(operation) {
        let value = try pop(1)[0]
        switch operation {
        case 15: stack.append(0 &- value)
        case 16: stack.append(value == .min ? .min : abs(value))
        case 17: stack.append(value &+ 1)
        case 18: stack.append(value &- 1)
        case 19: stack.append(~value)
        default: stack.append((value >> 31) | Int32(bitPattern: 0 &- UInt32(bitPattern: value)) >> 31)
        }
      } else if (21...23).contains(operation) {
        let value = try pop(3)
        if operation == 21 { stack.append(min(max(value[0], value[2]), value[1])) }
        else if operation == 22 { stack.append(value[2] > 0 ? value[1] : value[0]) }
        else { stack.append(value[2] &+ value[1] &* value[0]) }
      } else {
        throw NativeSwiftCoreError.unsupported(
          opcode: 144, offset: 0, reason: "integer expression operator \(operation)")
      }
    }
    guard stack.count == 1, let result = stack.last else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Invalid integer expression result")
    }
    return Int(result)
  }
}

enum NativeSwiftFloatExpression {
  private static let payloadMask: UInt32 = 0x007f_ffff
  private static let referenceMask: UInt32 = 0x003f_ffff
  private static let operatorOffset = 0x0031_0000

  static func resolve(_ word: UInt32, values: [Int: Float]) -> Float {
    guard isEncoded(word) else { return Float(bitPattern: word) }
    return values[Int(word & referenceMask)] ?? 0
  }

  static func referenceID(_ word: UInt32) -> Int? {
    guard isEncoded(word) else { return nil }
    let payload = Int(word & payloadMask)
    guard payload <= operatorOffset || payload > operatorOffset + 79 else { return nil }
    return Int(word & referenceMask)
  }

  static func evaluate(
    _ words: [UInt32], values: [Int: Float], variables: [Float] = []
  ) throws -> Float {
    var stack: [Float] = []
    stack.reserveCapacity(min(words.count, 128))
    func pop(_ count: Int) throws -> [Float] {
      guard stack.count >= count else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Float expression stack underflow")
      }
      let result = Array(stack.suffix(count))
      stack.removeLast(count)
      return result
    }
    for word in words {
      let payload = Int(word & payloadMask)
      guard isEncoded(word), payload > operatorOffset, payload <= operatorOffset + 79 else {
        stack.append(resolve(word, values: values))
        guard stack.count <= 128 else {
          throw NativeSwiftCoreError.malformed(offset: 0, reason: "Float expression stack overflow")
        }
        continue
      }
      let operation = payload - operatorOffset
      switch operation {
      case 70...72:
        let index = operation - 70
        stack.append(variables.indices.contains(index) ? variables[index] : 0)
      case 1...8:
        let value = try pop(2)
        switch operation {
        case 1: stack.append(value[0] + value[1])
        case 2: stack.append(value[0] - value[1])
        case 3: stack.append(value[0] * value[1])
        case 4: stack.append(value[0] / value[1])
        case 5: stack.append(value[0].truncatingRemainder(dividingBy: value[1]))
        case 6: stack.append(min(value[0], value[1]))
        case 7: stack.append(max(value[0], value[1]))
        default: stack.append(powf(value[0], value[1]))
        }
      case 9...11, 13...23, 28...31, 45, 51...53, 73:
        let value = try pop(1)[0]
        switch operation {
        case 9: stack.append(sqrtf(value))
        case 10: stack.append(abs(value))
        case 11: stack.append(value == 0 ? 0 : (value < 0 ? -1 : 1))
        case 13: stack.append(expf(value))
        case 14: stack.append(floorf(value))
        case 15: stack.append(log10f(value))
        case 16: stack.append(logf(value))
        case 17: stack.append(roundf(value))
        case 18: stack.append(sinf(value))
        case 19: stack.append(cosf(value))
        case 20: stack.append(tanf(value))
        case 21: stack.append(asinf(value))
        case 22: stack.append(acosf(value))
        case 23: stack.append(atanf(value))
        case 28: stack.append(cbrtf(value))
        case 29: stack.append(value * 57.29578)
        case 30: stack.append(value * 0.017453292)
        case 31: stack.append(ceilf(value))
        case 45: stack.append(value * value)
        case 51: stack.append(log2f(value))
        case 52: stack.append(1 / value)
        case 53:
          // FRACT, and the reference's own definition: the fraction above the floor, wrapped
          // positive. `value - Float(Int(value))` trapped on a non-finite or out-of-range operand
          // and was wrong for negatives anyway.
          let fraction = value - floorf(value)
          stack.append(fraction < 0 ? fraction + 1 : fraction)
        default: stack.append(-value)
        }
      case 12, 24, 43, 44, 47, 54:
        let value = try pop(2)
        switch operation {
        case 12: stack.append(copysignf(abs(value[0]), value[1]))
        case 24: stack.append(atan2f(value[0], value[1]))
        case 43: stack.append(value[0] * value[0] + value[1] * value[1])
        case 44: stack.append(value[0] > value[1] ? 1 : 0)
        case 47: stack.append(hypotf(value[0], value[1]))
        default:
          let doubled = value[1] * 2
          let remainder = value[0].truncatingRemainder(dividingBy: doubled)
          stack.append(remainder < value[1] ? remainder : doubled - remainder)
        }
      case 25:
        let value = try pop(3)
        stack.append(value[2] + value[1] * value[0])
      case 26:
        let value = try pop(3)
        stack.append(value[2] > 0 ? value[1] : value[0])
      case 27:
        let value = try pop(3)
        stack.append(min(max(value[0], value[2]), value[1]))
      case 48:
        // SWAP: the reference's expression language can exchange its top two operands, which is how
        // a formula written for a stack machine reads `a` and `b` in the order it wants.
        let value = try pop(2)
        stack.append(value[1])
        stack.append(value[0])
      case 49:
        let value = try pop(3)
        stack.append(value[0] + (value[1] - value[0]) * value[2])
      case 50:
        // SMOOTH_STEP: 0 below the first edge, 1 above the second, and the Hermite curve between
        // them. `expr_interpolation` is the gold that names it.
        let value = try pop(3)
        if value[0] < value[2] {
          stack.append(0)
        } else if value[0] > value[1] {
          stack.append(1)
        } else {
          let t = (value[0] - value[2]) / (value[1] - value[2])
          stack.append(t * t * (3 - 2 * t))
        }
      case 74:
        let value = try pop(5)
        stack.append(cubicEasing(value[0], value[1], value[2], value[3], value[4]))
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: 81, offset: 0, reason: "float expression operator \(operation)")
      }
    }
    guard stack.count == 1, let result = stack.last, result.isFinite else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Invalid float expression result")
    }
    return result
  }

  private static func isEncoded(_ word: UInt32) -> Bool {
    word & 0x7f80_0000 == 0x7f80_0000 && word & 0x007f_ffff != 0
  }

  private static func cubicEasing(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ x: Float)
    -> Float
  {
    if x <= 0 { return 0 }
    if x >= 1 { return 1 }
    func coordinate(_ t: Float, _ first: Float, _ second: Float) -> Float {
      let inverse = 1 - t
      return first * 3 * inverse * inverse * t + second * 3 * inverse * t * t + t * t * t
    }
    var t: Float = 0.5
    var range: Float = 0.5
    while range > 0.01 {
      let current = coordinate(t, x1, x2)
      range *= 0.5
      if current < x { t += range } else { t -= range }
    }
    let lowerX = coordinate(t - range, x1, x2)
    let upperX = coordinate(t + range, x1, x2)
    let lowerY = coordinate(t - range, y1, y2)
    let upperY = coordinate(t + range, y1, y2)
    return (upperY - lowerY) * (x - lowerX) / (upperX - lowerX) + lowerY
  }
}

/// Evaluates a `MATRIX_EXPRESSION` into the 3x3 layout AndroidX's `MatrixAccess` exposes.
///
/// The expression is an RPN stream over a small matrix stack: literals are operands, and a
/// NaN-boxed operator token acts on the operand slots immediately before it. This is the same
/// evaluator `MatrixOperations` runs, kept here because a paint's `SHADER_MATRIX` field names one
/// and a texture shader is meaningless without it.
///
/// A token sequence this evaluator cannot complete resolves to nil rather than failing the
/// document: the matrix used to be dropped outright, so dropping it again is not a regression, and
/// the shader still draws at its natural scale.
enum NativeSwiftMatrixExpression {
  static let operatorOffset = 0x0032_0000
  static let lastOperator = operatorOffset + 54

  /// The NaN-encoded id a `SHADER_MATRIX` paint field carries, or nil when the field is zero.
  static func referenceID(word: UInt32) -> Int? {
    guard word & 0x7f80_0000 == 0x7f80_0000 else { return nil }
    let payload = Int(word & 0x003f_ffff)
    guard payload == 0 || payload <= operatorOffset || payload > lastOperator else { return nil }
    return payload
  }

  static func evaluate4x4(_ expression: ParsedMatrixExpression, values: [Int: Float]) -> [Float]? {
    var matrices = [[Float]](repeating: identity, count: 10)
    var index = 0
    matrices[0] = identity
    func operand(_ position: Int) -> Float? {
      guard position >= 0, position < expression.words.count else { return nil }
      let value = NativeSwiftFloatExpression.resolve(expression.words[position], values: values)
      return value.isFinite ? value : nil
    }
    for (position, word) in expression.words.enumerated() {
      let payload = Int(word & 0x003f_ffff)
      guard word & 0x7f80_0000 == 0x7f80_0000, payload > operatorOffset,
        payload <= lastOperator
      else { continue }
      let operation = payload - operatorOffset
      switch operation {
      case 1:  // IDENTITY pushes a new matrix for a following scale or rotation.
        index += 1
        guard index < matrices.count else { return nil }
        matrices[index] = identity
      case 2, 3, 4:
        guard let degrees = operand(position - 1) else { return nil }
        matrices[index] = multiply(matrices[index], rotation(axis: operation, degrees: degrees))
      case 5, 6, 7:
        guard let value = operand(position - 1) else { return nil }
        matrices[index] = multiply(
          matrices[index],
          translation(
            x: operation == 5 ? value : 0, y: operation == 6 ? value : 0,
            z: operation == 7 ? value : 0))
      case 8, 9:
        let first = operand(position - 2)
        let second = operand(position - 1)
        guard let first, let second else { return nil }
        matrices[index] = multiply(
          matrices[index], translation(x: first, y: second, z: 0))
      case 10, 11, 12:
        guard let value = operand(position - 1) else { return nil }
        scale(
          &matrices[index],
          x: operation == 10 ? value : 1, y: operation == 11 ? value : 1,
          z: operation == 12 ? value : 1)
      case 13:
        let first = operand(position - 2)
        let second = operand(position - 1)
        guard let first, let second else { return nil }
        scale(&matrices[index], x: first, y: second, z: 0)
      case 14:
        let first = operand(position - 3)
        let second = operand(position - 2)
        let third = operand(position - 1)
        guard let first, let second, let third else { return nil }
        scale(&matrices[index], x: first, y: second, z: third)
      case 15:  // MUL merges the top two matrices.
        guard index > 0 else { return nil }
        matrices[index - 1] = multiply(matrices[index - 1], matrices[index])
        index -= 1
      case 16:  // ROT_PZ: angle, pivot x, pivot y.
        let pivotX = operand(position - 2)
        let pivotY = operand(position - 1)
        let degrees = operand(position - 3)
        guard let pivotX, let pivotY, let degrees else { return nil }
        matrices[index] = multiply(
          rotationWithPivot(pivotX: pivotX, pivotY: pivotY, degrees: degrees), matrices[index])
      case 17:  // ROT_AXIS: angle, x, y, z.
        let x = operand(position - 3)
        let y = operand(position - 2)
        let z = operand(position - 1)
        let degrees = operand(position - 4)
        guard let x, let y, let z, let degrees else { return nil }
        matrices[index] = multiply(rotationAroundAxis(x: x, y: y, z: z, degrees: degrees), matrices[index])
      case 18:  // PROJECTION: fov degrees, aspect ratio, near, far.
        let fov = operand(position - 4)
        let aspect = operand(position - 3)
        let near = operand(position - 2)
        let far = operand(position - 1)
        guard let fov, let aspect, let near, let far else { return nil }
        matrices[index] = multiply(matrices[index], projection(fov: fov, aspect: aspect, near: near, far: far))
      default:
        return nil
      }
    }
    return matrices[0]
  }

  static func evaluate(_ expression: ParsedMatrixExpression, values: [Int: Float]) -> [Float]? {
    guard let matrix = evaluate4x4(expression, values: values) else { return nil }
    // `MatrixAccess.to3x3`: a 4x4 collapses to the Android 3x3 layout, which is
    // [scaleX, skewX, translateX, skewY, scaleY, translateY, persp0, persp1, persp2].
    return [
      matrix[0], matrix[1], matrix[3],
      matrix[4], matrix[5], matrix[7],
      matrix[8], matrix[9], matrix[15],
    ]
  }

  private static let identity: [Float] = [
    1, 0, 0, 0,
    0, 1, 0, 0,
    0, 0, 1, 0,
    0, 0, 0, 1,
  ]

  private static func multiply(_ first: [Float], _ second: [Float]) -> [Float] {
    var result = [Float](repeating: 0, count: 16)
    for row in 0..<4 {
      for column in 0..<4 {
        var sum: Float = 0
        for k in 0..<4 { sum += first[row * 4 + k] * second[k * 4 + column] }
        result[row * 4 + column] = sum
      }
    }
    return result
  }

  private static func translation(x: Float, y: Float, z: Float) -> [Float] {
    var matrix = identity
    matrix[3] = x
    matrix[7] = y
    matrix[11] = z
    return matrix
  }

  private static func scale(_ matrix: inout [Float], x: Float, y: Float, z: Float) {
    matrix[0] *= x
    matrix[5] *= y
    matrix[10] *= z
  }

  private static func rotation(axis: Int, degrees: Float) -> [Float] {
    let radians = degrees * .pi / 180
    let cosine = cosf(radians)
    let sine = sinf(radians)
    var matrix = identity
    switch axis {
    case 2:
      matrix[5] = cosine
      matrix[6] = -sine
      matrix[9] = sine
      matrix[10] = cosine
    case 3:
      matrix[0] = cosine
      matrix[2] = sine
      matrix[8] = -sine
      matrix[10] = cosine
    default:
      matrix[0] = cosine
      matrix[1] = -sine
      matrix[4] = sine
      matrix[5] = cosine
    }
    return matrix
  }

  private static func rotationWithPivot(pivotX: Float, pivotY: Float, degrees: Float) -> [Float] {
    let radians = degrees * .pi / 180
    let cosine = cosf(radians)
    let sine = sinf(radians)
    var matrix = identity
    matrix[0] = cosine
    matrix[1] = -sine
    matrix[3] = pivotX * (1 - cosine) + pivotY * sine
    matrix[4] = sine
    matrix[5] = cosine
    matrix[7] = pivotY * (1 - cosine) - pivotX * sine
    return matrix
  }

  private static func rotationAroundAxis(x: Float, y: Float, z: Float, degrees: Float) -> [Float] {
    let lengthSquared = x * x + y * y + z * z
    guard lengthSquared > 0 else { return identity }
    let length = sqrtf(lengthSquared)
    let ux = x / length
    let uy = y / length
    let uz = z / length
    let radians = degrees * .pi / 180
    let cosine = cosf(radians)
    let sine = sinf(radians)
    let oneMinusCosine = 1 - cosine
    var matrix = identity
    matrix[0] = cosine + ux * ux * oneMinusCosine
    matrix[1] = ux * uy * oneMinusCosine - uz * sine
    matrix[2] = ux * uz * oneMinusCosine + uy * sine
    matrix[4] = uy * ux * oneMinusCosine + uz * sine
    matrix[5] = cosine + uy * uy * oneMinusCosine
    matrix[6] = uy * uz * oneMinusCosine - ux * sine
    matrix[8] = uz * ux * oneMinusCosine - uy * sine
    matrix[9] = uz * uy * oneMinusCosine + ux * sine
    matrix[10] = cosine + uz * uz * oneMinusCosine
    return matrix
  }

  private static func projection(fov: Float, aspect: Float, near: Float, far: Float) -> [Float] {
    let radians = fov * .pi / 180
    let f = 1 / tanf(radians / 2)
    let range = 1 / (near - far)
    var matrix = identity
    matrix[0] = f / aspect
    matrix[5] = f
    matrix[10] = (far + near) * range
    matrix[11] = -1
    matrix[14] = 2 * far * near * range
    matrix[15] = 0
    return matrix
  }
}

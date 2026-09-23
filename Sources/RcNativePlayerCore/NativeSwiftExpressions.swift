import Foundation

struct ParsedFloatExpression {
  let id: Int
  let words: [UInt32]
  let animationWords: [UInt32]?
  /// The operation that declared the expression -- `ANIMATED_FLOAT`, or a `DATA_FLOAT` alias -- and
  /// its byte offset, so a failure while evaluating it names where it came from.
  let opcode: Int
  let offset: Int
}

struct ParsedIntegerExpression {
  let mask: Int
  let tokens: [Int]
  /// The declaring `INTEGER_EXPRESSION`'s byte offset, for evaluation failures.
  let offset: Int
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
  /// - Parameter offset: the declaring `INTEGER_EXPRESSION`'s byte offset, reported by any error.
  static func evaluate(mask: Int, tokens: [Int], values: [Int: Int], offset: Int) throws -> Int {
    var stack: [Int32] = []
    stack.reserveCapacity(tokens.count)
    // Operands come off as a tuple, oldest first, rather than as a fresh `Array` per operator.
    func require(_ count: Int) throws {
      guard stack.count >= count else {
        throw NativeSwiftCoreError.malformed(
          offset: offset, reason: "Integer expression stack underflow")
      }
    }
    func pop1() throws -> Int32 {
      try require(1)
      return stack.removeLast()
    }
    func pop2() throws -> (Int32, Int32) {
      try require(2)
      let second = stack.removeLast()
      return (stack.removeLast(), second)
    }
    func pop3() throws -> (Int32, Int32, Int32) {
      try require(3)
      let third = stack.removeLast()
      let second = stack.removeLast()
      return (stack.removeLast(), second, third)
    }
    for (index, token) in tokens.enumerated() {
      let marked = UInt32(bitPattern: Int32(mask)) & (UInt32(1) << UInt32(index & 31)) != 0
      if !marked || token < NativeSwiftIntegerOperator.offset {
        stack.append(Int32(truncatingIfNeeded: marked ? values[token] ?? 0 : token))
        continue
      }
      let operation = token - NativeSwiftIntegerOperator.offset
      if (NativeSwiftIntegerOperator.add...NativeSwiftIntegerOperator.max).contains(operation) {
        let (left, right) = try pop2()
        switch operation {
        case NativeSwiftIntegerOperator.add: stack.append(left &+ right)
        case NativeSwiftIntegerOperator.sub: stack.append(left &- right)
        case NativeSwiftIntegerOperator.mul: stack.append(left &* right)
        case NativeSwiftIntegerOperator.div:
          stack.append(right == 0 || (left == .min && right == -1) ? 0 : left / right)
        case NativeSwiftIntegerOperator.mod:
          stack.append(right == 0 || (left == .min && right == -1) ? 0 : left % right)
        case NativeSwiftIntegerOperator.shl: stack.append(left << (right & 31))
        case NativeSwiftIntegerOperator.shr: stack.append(left >> (right & 31))
        case NativeSwiftIntegerOperator.ushr:
          stack.append(Int32(bitPattern: UInt32(bitPattern: left) >> UInt32(right & 31)))
        case NativeSwiftIntegerOperator.or: stack.append(left | right)
        case NativeSwiftIntegerOperator.and: stack.append(left & right)
        case NativeSwiftIntegerOperator.xor: stack.append(left ^ right)
        case NativeSwiftIntegerOperator.copySign: stack.append((left ^ (right >> 31)) &- (right >> 31))
        case NativeSwiftIntegerOperator.min: stack.append(min(left, right))
        default: stack.append(max(left, right))
        }
      } else if (NativeSwiftIntegerOperator.neg...NativeSwiftIntegerOperator.sign).contains(operation) {
        let value = try pop1()
        switch operation {
        case NativeSwiftIntegerOperator.neg: stack.append(0 &- value)
        case NativeSwiftIntegerOperator.abs: stack.append(value == .min ? .min : abs(value))
        case NativeSwiftIntegerOperator.incr: stack.append(value &+ 1)
        case NativeSwiftIntegerOperator.decr: stack.append(value &- 1)
        case NativeSwiftIntegerOperator.not: stack.append(~value)
        default: stack.append((value >> 31) | Int32(bitPattern: 0 &- UInt32(bitPattern: value)) >> 31)
        }
      } else if (NativeSwiftIntegerOperator.clamp...NativeSwiftIntegerOperator.mad).contains(operation) {
        let (first, second, third) = try pop3()
        if operation == NativeSwiftIntegerOperator.clamp {
          stack.append(min(max(first, third), second))
        } else if operation == NativeSwiftIntegerOperator.ifElse {
          stack.append(third > 0 ? second : first)
        } else {
          stack.append(third &+ second &* first)
        }
      } else {
        throw NativeSwiftCoreError.unsupported(
          opcode: NativeSwiftWireOpcode.integerExpression, offset: offset,
          reason: "integer expression operator \(operation)")
      }
    }
    guard stack.count == 1, let result = stack.last else {
      throw NativeSwiftCoreError.malformed(
        offset: offset, reason: "Invalid integer expression result")
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
    guard payload <= operatorOffset || payload > operatorOffset + NativeSwiftFloatOperator.last else {
      return nil
    }
    return Int(word & referenceMask)
  }

  /// - Parameters:
  ///   - opcode: the operation that declared `words` -- `ANIMATED_FLOAT`, a `DATA_FLOAT` alias, or
  ///     a particle definition or loop -- reported by any error.
  ///   - offset: that operation's byte offset.
  static func evaluate(
    _ words: [UInt32], values: [Int: Float], variables: [Float] = [], opcode: Int, offset: Int
  ) throws -> Float {
    var stack: [Float] = []
    stack.reserveCapacity(min(words.count, 128))
    // Operands come off as a tuple, oldest first, rather than as a fresh `Array` per operator.
    func require(_ count: Int) throws {
      guard stack.count >= count else {
        throw NativeSwiftCoreError.malformed(
          offset: offset, reason: "Float expression stack underflow")
      }
    }
    func pop1() throws -> Float {
      try require(1)
      return stack.removeLast()
    }
    func pop2() throws -> (Float, Float) {
      try require(2)
      let second = stack.removeLast()
      return (stack.removeLast(), second)
    }
    func pop3() throws -> (Float, Float, Float) {
      try require(3)
      let third = stack.removeLast()
      let second = stack.removeLast()
      return (stack.removeLast(), second, third)
    }
    func pop5() throws -> (Float, Float, Float, Float, Float) {
      try require(5)
      let fifth = stack.removeLast()
      let fourth = stack.removeLast()
      let third = stack.removeLast()
      let second = stack.removeLast()
      return (stack.removeLast(), second, third, fourth, fifth)
    }
    for word in words {
      let payload = Int(word & payloadMask)
      guard isEncoded(word), payload > operatorOffset,
        payload <= operatorOffset + NativeSwiftFloatOperator.last
      else {
        stack.append(resolve(word, values: values))
        guard stack.count <= 128 else {
          throw NativeSwiftCoreError.malformed(
            offset: offset, reason: "Float expression stack overflow")
        }
        continue
      }
      let operation = payload - operatorOffset
      switch operation {
      case NativeSwiftFloatOperator.var1...NativeSwiftFloatOperator.var3:
        let index = operation - NativeSwiftFloatOperator.var1
        stack.append(variables.indices.contains(index) ? variables[index] : 0)
      case NativeSwiftFloatOperator.add...NativeSwiftFloatOperator.pow:
        let (first, second) = try pop2()
        switch operation {
        case NativeSwiftFloatOperator.add: stack.append(first + second)
        case NativeSwiftFloatOperator.sub: stack.append(first - second)
        case NativeSwiftFloatOperator.mul: stack.append(first * second)
        case NativeSwiftFloatOperator.div: stack.append(first / second)
        case NativeSwiftFloatOperator.mod:
          stack.append(first.truncatingRemainder(dividingBy: second))
        case NativeSwiftFloatOperator.min: stack.append(min(first, second))
        case NativeSwiftFloatOperator.max: stack.append(max(first, second))
        default: stack.append(powf(first, second))
        }
      case NativeSwiftFloatOperator.sqrt...NativeSwiftFloatOperator.sign,
        NativeSwiftFloatOperator.exp...NativeSwiftFloatOperator.atan,
        NativeSwiftFloatOperator.cbrt...NativeSwiftFloatOperator.ceil,
        NativeSwiftFloatOperator.square,
        NativeSwiftFloatOperator.log2...NativeSwiftFloatOperator.fract,
        NativeSwiftFloatOperator.changeSign:
        let value = try pop1()
        switch operation {
        case NativeSwiftFloatOperator.sqrt: stack.append(sqrtf(value))
        case NativeSwiftFloatOperator.abs: stack.append(abs(value))
        case NativeSwiftFloatOperator.sign: stack.append(value == 0 ? 0 : (value < 0 ? -1 : 1))
        case NativeSwiftFloatOperator.exp: stack.append(expf(value))
        case NativeSwiftFloatOperator.floor: stack.append(floorf(value))
        case NativeSwiftFloatOperator.log: stack.append(log10f(value))
        case NativeSwiftFloatOperator.ln: stack.append(logf(value))
        case NativeSwiftFloatOperator.round: stack.append(roundf(value))
        case NativeSwiftFloatOperator.sin: stack.append(sinf(value))
        case NativeSwiftFloatOperator.cos: stack.append(cosf(value))
        case NativeSwiftFloatOperator.tan: stack.append(tanf(value))
        case NativeSwiftFloatOperator.asin: stack.append(asinf(value))
        case NativeSwiftFloatOperator.acos: stack.append(acosf(value))
        case NativeSwiftFloatOperator.atan: stack.append(atanf(value))
        case NativeSwiftFloatOperator.cbrt: stack.append(cbrtf(value))
        case NativeSwiftFloatOperator.deg: stack.append(value * 57.29578)
        case NativeSwiftFloatOperator.rad: stack.append(value * 0.017453292)
        case NativeSwiftFloatOperator.ceil: stack.append(ceilf(value))
        case NativeSwiftFloatOperator.square: stack.append(value * value)
        case NativeSwiftFloatOperator.log2: stack.append(log2f(value))
        case NativeSwiftFloatOperator.inv: stack.append(1 / value)
        case NativeSwiftFloatOperator.fract:
          // FRACT, and the reference's own definition: the fraction above the floor, wrapped
          // positive. `value - Float(Int(value))` trapped on a non-finite or out-of-range operand
          // and was wrong for negatives anyway.
          let fraction = value - floorf(value)
          stack.append(fraction < 0 ? fraction + 1 : fraction)
        default: stack.append(-value)
        }
      case NativeSwiftFloatOperator.copySign, NativeSwiftFloatOperator.atan2,
        NativeSwiftFloatOperator.squareSum, NativeSwiftFloatOperator.step,
        NativeSwiftFloatOperator.hypot, NativeSwiftFloatOperator.pingPong:
        let (first, second) = try pop2()
        switch operation {
        case NativeSwiftFloatOperator.copySign: stack.append(copysignf(abs(first), second))
        case NativeSwiftFloatOperator.atan2: stack.append(atan2f(first, second))
        case NativeSwiftFloatOperator.squareSum: stack.append(first * first + second * second)
        case NativeSwiftFloatOperator.step: stack.append(first > second ? 1 : 0)
        case NativeSwiftFloatOperator.hypot: stack.append(hypotf(first, second))
        default:
          let doubled = second * 2
          let remainder = first.truncatingRemainder(dividingBy: doubled)
          stack.append(remainder < second ? remainder : doubled - remainder)
        }
      case NativeSwiftFloatOperator.mad:
        let (first, second, third) = try pop3()
        stack.append(third + second * first)
      case NativeSwiftFloatOperator.ifElse:
        let (first, second, third) = try pop3()
        stack.append(third > 0 ? second : first)
      case NativeSwiftFloatOperator.clamp:
        let (first, second, third) = try pop3()
        stack.append(min(max(first, third), second))
      case NativeSwiftFloatOperator.swap:
        // SWAP: the reference's expression language can exchange its top two operands, which is how
        // a formula written for a stack machine reads `a` and `b` in the order it wants.
        let (first, second) = try pop2()
        stack.append(second)
        stack.append(first)
      case NativeSwiftFloatOperator.lerp:
        let (first, second, third) = try pop3()
        stack.append(first + (second - first) * third)
      case NativeSwiftFloatOperator.smoothStep:
        // SMOOTH_STEP: 0 below the first edge, 1 above the second, and the Hermite curve between
        // them. `expr_interpolation` is the gold that names it.
        let (first, second, third) = try pop3()
        if first < third {
          stack.append(0)
        } else if first > second {
          stack.append(1)
        } else {
          let t = (first - third) / (second - third)
          stack.append(t * t * (3 - 2 * t))
        }
      case NativeSwiftFloatOperator.cubic:
        let (x1, y1, x2, y2, x) = try pop5()
        stack.append(cubicEasing(x1, y1, x2, y2, x))
      default:
        throw NativeSwiftCoreError.unsupported(
          opcode: opcode, offset: offset, reason: "float expression operator \(operation)")
      }
    }
    guard stack.count == 1, let result = stack.last, result.isFinite else {
      throw NativeSwiftCoreError.malformed(
        offset: offset, reason: "Invalid float expression result")
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
  static let lastOperator = operatorOffset + NativeSwiftMatrixOperator.last

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
      case NativeSwiftMatrixOperator.identity:
        // IDENTITY pushes a new matrix for a following scale or rotation.
        index += 1
        guard index < matrices.count else { return nil }
        matrices[index] = identity
      case NativeSwiftMatrixOperator.rotateX, NativeSwiftMatrixOperator.rotateY,
        NativeSwiftMatrixOperator.rotateZ:
        guard let degrees = operand(position - 1) else { return nil }
        matrices[index] = multiply(matrices[index], rotation(axis: operation, degrees: degrees))
      case NativeSwiftMatrixOperator.translateX, NativeSwiftMatrixOperator.translateY,
        NativeSwiftMatrixOperator.translateZ:
        guard let value = operand(position - 1) else { return nil }
        matrices[index] = multiply(
          matrices[index],
          translation(
            x: operation == NativeSwiftMatrixOperator.translateX ? value : 0,
            y: operation == NativeSwiftMatrixOperator.translateY ? value : 0,
            z: operation == NativeSwiftMatrixOperator.translateZ ? value : 0))
      case NativeSwiftMatrixOperator.translate2, NativeSwiftMatrixOperator.translate3:
        let first = operand(position - 2)
        let second = operand(position - 1)
        guard let first, let second else { return nil }
        matrices[index] = multiply(
          matrices[index], translation(x: first, y: second, z: 0))
      case NativeSwiftMatrixOperator.scaleX, NativeSwiftMatrixOperator.scaleY,
        NativeSwiftMatrixOperator.scaleZ:
        guard let value = operand(position - 1) else { return nil }
        scale(
          &matrices[index],
          x: operation == NativeSwiftMatrixOperator.scaleX ? value : 1,
          y: operation == NativeSwiftMatrixOperator.scaleY ? value : 1,
          z: operation == NativeSwiftMatrixOperator.scaleZ ? value : 1)
      case NativeSwiftMatrixOperator.scale2:
        let first = operand(position - 2)
        let second = operand(position - 1)
        guard let first, let second else { return nil }
        scale(&matrices[index], x: first, y: second, z: 0)
      case NativeSwiftMatrixOperator.scale3:
        let first = operand(position - 3)
        let second = operand(position - 2)
        let third = operand(position - 1)
        guard let first, let second, let third else { return nil }
        scale(&matrices[index], x: first, y: second, z: third)
      case NativeSwiftMatrixOperator.mul:  // MUL merges the top two matrices.
        guard index > 0 else { return nil }
        matrices[index - 1] = multiply(matrices[index - 1], matrices[index])
        index -= 1
      case NativeSwiftMatrixOperator.rotatePivotZ:  // ROT_PZ: angle, pivot x, pivot y.
        let pivotX = operand(position - 2)
        let pivotY = operand(position - 1)
        let degrees = operand(position - 3)
        guard let pivotX, let pivotY, let degrees else { return nil }
        matrices[index] = multiply(
          rotationWithPivot(pivotX: pivotX, pivotY: pivotY, degrees: degrees), matrices[index])
      case NativeSwiftMatrixOperator.rotateAxis:  // ROT_AXIS: angle, x, y, z.
        let x = operand(position - 3)
        let y = operand(position - 2)
        let z = operand(position - 1)
        let degrees = operand(position - 4)
        guard let x, let y, let z, let degrees else { return nil }
        matrices[index] = multiply(rotationAroundAxis(x: x, y: y, z: z, degrees: degrees), matrices[index])
      case NativeSwiftMatrixOperator.projection:
        // PROJECTION: fov degrees, aspect ratio, near, far.
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
    case NativeSwiftMatrixOperator.rotateX:
      matrix[5] = cosine
      matrix[6] = -sine
      matrix[9] = sine
      matrix[10] = cosine
    case NativeSwiftMatrixOperator.rotateY:
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

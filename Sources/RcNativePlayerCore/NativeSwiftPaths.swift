import Foundation

struct ParsedPath {
  /// The operation that wrote a run of `words`: from `firstWord` up to the next origin's. A path
  /// can be declared by one operation and extended by others, and a word that fails to resolve is
  /// reported against the operation that wrote it.
  struct Origin {
    let firstWord: Int
    let opcode: Int
    let offset: Int
  }

  /// Where a path's leading geometry comes from. Words a `PATH_ADD` appends follow it, in `words`,
  /// whichever it is.
  indirect enum Source {
    /// Nothing but `words`: `PATH_DATA`, `PATH_CREATE` and `PATH_ADD`.
    case words
    /// A `PATH_EXPRESSION`, sampled afresh on every frame.
    case expression(ParsedPathExpression)
    /// A `PATH_TWEEN` or `DRAW_TWEEN_PATH`: two paths as they stood when the operation ran.
    case tween(ParsedPathTween)
    /// A `DRAW_PATH` or `DRAW_TWEEN_PATH` path id that names an integer variable: whichever path
    /// that variable holds when the operation paints.
    case dereferenced(ParsedPathDereference)
  }

  let winding: NativeSwiftPathWinding
  let source: Source
  /// The paths of its own this path's geometry reads: one for a path with its own geometry, both
  /// sides' for a tween, and the most any one candidate reads for a dereferenced path, which
  /// resolves only the one its variable picks. Every one is resolved on every frame the path
  /// draws, so a tween of tweens is bounded by it (`ParsedPathTween.maximumSources`).
  let sourceCount: Int
  private(set) var words: [UInt32]
  /// Never empty: every path starts with the operation that declared it.
  private(set) var origins: [Origin]

  init(winding: NativeSwiftPathWinding, words: [UInt32], opcode: Int, offset: Int) {
    self.winding = winding
    source = .words
    sourceCount = 1
    self.words = words
    origins = [Origin(firstWord: 0, opcode: opcode, offset: offset)]
  }

  /// A path whose geometry `source` generates, declared by the operation at `offset`.
  init(winding: NativeSwiftPathWinding, source: Source, opcode: Int, offset: Int) {
    self.winding = winding
    self.source = source
    switch source {
    case .words, .expression: sourceCount = 1
    case .tween(let tween): sourceCount = tween.first.sourceCount + tween.second.sourceCount
    case .dereferenced(let dereference): sourceCount = dereference.sourceCount
    }
    words = []
    origins = [Origin(firstWord: 0, opcode: opcode, offset: offset)]
  }

  /// Appends `more` words written by the operation at `offset`.
  ///
  /// In place, so a path built by a run of `PATH_ADD`s grows in amortised constant time rather than
  /// being copied whole by each one; a draw that already holds the path keeps its own copy.
  mutating func append(_ more: [UInt32], opcode: Int, offset: Int) {
    origins.append(Origin(firstWord: words.count, opcode: opcode, offset: offset))
    words.append(contentsOf: more)
  }

  /// The operation that wrote the word at `index`.
  private func origin(ofWord index: Int) -> Origin {
    origins.last { $0.firstWord <= index } ?? origins[0]
  }

  /// Whether any of this path's *argument* words references one of `ids`.
  ///
  /// The walk is structural on purpose. A path's command tokens are NaN-boxed ids 10...16, which
  /// collide with the system-variable ids in that range, so a flat scan over the words would report
  /// `OFFSET_TO_UTC` (10) on any document that draws a path at all.
  func references(anyOf ids: Set<Int>) -> Bool {
    func matches(_ word: UInt32) -> Bool {
      NativeSwiftFloatExpression.referenceID(word).map(ids.contains) ?? false
    }
    if sourceReferences(anyOf: ids) { return true }
    var index = 0
    while index < words.count {
      guard let command = NativeSwiftFloatExpression.referenceID(words[index]) else { return false }
      index += 1
      let padding: Int
      let argumentCount: Int
      switch command {
      case NativeSwiftPathCommand.move:
        padding = 0
        argumentCount = 2
      case NativeSwiftPathCommand.line:
        padding = 2
        argumentCount = 2
      case NativeSwiftPathCommand.quadratic:
        padding = 2
        argumentCount = 4
      case NativeSwiftPathCommand.conic:
        padding = 2
        argumentCount = 5
      case NativeSwiftPathCommand.cubic:
        padding = 2
        argumentCount = 6
      case NativeSwiftPathCommand.close:
        padding = 0
        argumentCount = 0
      case NativeSwiftPathCommand.done:
        return false
      default:
        return false
      }
      index += padding
      guard index + argumentCount <= words.count else { return false }
      for offset in 0..<argumentCount where matches(words[index + offset]) { return true }
      index += argumentCount
    }
    return false
  }

  /// Whether the geometry `source` generates reads one of `ids`: an expression's operands, or a
  /// tween's fraction, its trim, and the two paths it reads.
  func sourceReferences(anyOf ids: Set<Int>) -> Bool {
    switch source {
    case .words: return false
    case .expression(let expression): return expression.references(anyOf: ids)
    case .tween(let tween): return tween.references(anyOf: ids)
    case .dereferenced(let dereference):
      return dereference.candidates.values.contains { $0.references(anyOf: ids) }
    }
  }

  /// The path that draws for this frame: itself, or for a dereferenced path the candidate its
  /// variable holds in `integers` — nil when that names no path.
  func selected(integers: [Int: Int]) -> ParsedPath? {
    guard case .dereferenced(let dereference) = source else { return self }
    return dereference.path(integers: integers)
  }

  /// The winding this frame fills with: a dereferenced path fills as the path it picks does.
  func winding(integers: [Int: Int]) -> NativeSwiftPathWinding {
    guard case .dereferenced = source else { return winding }
    return selected(integers: integers)?.winding ?? winding
  }

  /// The path's elements for a frame. `integers` is the frame's integer state, which a
  /// dereferenced path id reads; a variable that names no path draws nothing, as the reference's
  /// canvas player skips a path its state does not hold.
  func resolve(values: [Int: Float], integers: [Int: Int]) throws
    -> [NativeSwiftPathElementSnapshot]
  {
    switch source {
    case .words:
      return try resolveWords(values: values)
    case .expression(let expression):
      return try Self.elements(
        fromData: expression.data(values: values), opcode: expression.opcode,
        offset: expression.offset) + resolveWords(values: values)
    case .tween(let tween):
      return try tween.resolve(values: values, integers: integers) + resolveWords(values: values)
    case .dereferenced(let dereference):
      guard let path = dereference.path(integers: integers) else { return [] }
      return try path.resolve(values: values, integers: integers)
    }
  }

  /// The path as AndroidX holds it in its state: the float array `PATH_DATA` carries, with each
  /// command token kept and every argument resolved. A tween interpolates two of these word by
  /// word, so it has to see the legacy padding words as well.
  func data(values: [Int: Float], integers: [Int: Int]) throws -> [Float] {
    switch source {
    case .words:
      return try wordData(values: values)
    case .expression(let expression):
      return try expression.data(values: values) + wordData(values: values)
    case .tween(let tween):
      return try tween.data(values: values, integers: integers) + wordData(values: values)
    case .dereferenced(let dereference):
      guard let path = dereference.path(integers: integers) else { return [] }
      return try path.data(values: values, integers: integers)
    }
  }

  /// `words` as `data(values:)` returns them. The walk is structural, as `references(anyOf:)`'s
  /// is, so an argument that names a variable in the command tokens' id range is still resolved.
  private func wordData(values: [Int: Float]) throws -> [Float] {
    var data: [Float] = []
    data.reserveCapacity(words.count)
    var index = 0
    while index < words.count {
      let writer = origin(ofWord: index)
      guard let command = NativeSwiftFloatExpression.referenceID(words[index]) else {
        throw NativeSwiftCoreError.malformed(
          offset: writer.offset, reason: "Path command is not encoded")
      }
      guard let argumentCount = Self.dataArgumentCount(command) else {
        throw NativeSwiftCoreError.unsupported(
          opcode: writer.opcode, offset: writer.offset, reason: "path command \(command)")
      }
      data.append(Float(bitPattern: pathCommandWord(command)))
      index += 1
      guard index + argumentCount <= words.count else {
        throw NativeSwiftCoreError.malformed(offset: writer.offset, reason: "Truncated path data")
      }
      for word in words[index..<(index + argumentCount)] {
        data.append(NativeSwiftFloatExpression.resolve(word, values: values))
      }
      index += argumentCount
    }
    return data
  }

  /// The words that follow a command token in path data, legacy padding included: AndroidX's
  /// `FloatsToPath` layout. Nil for a token that is not a path command.
  private static func dataArgumentCount(_ command: Int) -> Int? {
    switch command {
    case NativeSwiftPathCommand.move: 2
    case NativeSwiftPathCommand.line: 4
    case NativeSwiftPathCommand.quadratic: 6
    case NativeSwiftPathCommand.conic: 7
    case NativeSwiftPathCommand.cubic: 8
    case NativeSwiftPathCommand.close, NativeSwiftPathCommand.done: 0
    default: nil
    }
  }

  /// Path data turned into elements as AndroidX's `FloatsToPath.genPath` reads it: the two
  /// padding words after a line or curve token skipped, and `DONE` passed over rather than ending
  /// the path. A failure is reported against the operation at `offset`.
  static func elements(fromData data: [Float], opcode: Int, offset: Int) throws
    -> [NativeSwiftPathElementSnapshot]
  {
    var result: [NativeSwiftPathElementSnapshot] = []
    var index = 0
    while index < data.count {
      guard let command = NativeSwiftFloatExpression.referenceID(data[index].bitPattern) else {
        throw NativeSwiftCoreError.malformed(offset: offset, reason: "Path command is not encoded")
      }
      guard let argumentCount = dataArgumentCount(command) else {
        throw NativeSwiftCoreError.unsupported(
          opcode: opcode, offset: offset, reason: "path command \(command)")
      }
      guard index + 1 + argumentCount <= data.count else {
        throw NativeSwiftCoreError.malformed(offset: offset, reason: "Truncated path data")
      }
      // Every argument after a move's is preceded by the two legacy padding words.
      let first = index + 1 + (command == NativeSwiftPathCommand.move ? 0 : min(argumentCount, 2))
      let values = Array(data[first..<(index + 1 + argumentCount)])
      if command != NativeSwiftPathCommand.done {
        result.append(NativeSwiftPathElementSnapshot(kind: command, values: values))
      }
      index += 1 + argumentCount
    }
    return result
  }

  private func resolveWords(values: [Int: Float]) throws -> [NativeSwiftPathElementSnapshot] {
    var result: [NativeSwiftPathElementSnapshot] = []
    var index = 0
    // The command word being resolved; a failure is reported against the operation that wrote it.
    var commandIndex = 0
    func arguments(_ count: Int, skippingLegacyPadding: Bool = false) throws -> [Float] {
      if skippingLegacyPadding { index += 2 }
      guard index >= 0, index + count <= words.count else {
        throw NativeSwiftCoreError.malformed(
          offset: origin(ofWord: commandIndex).offset, reason: "Truncated path data")
      }
      let resolved = words[index..<(index + count)].map {
        NativeSwiftFloatExpression.resolve($0, values: values)
      }
      index += count
      return resolved
    }
    while index < words.count {
      commandIndex = index
      guard let command = NativeSwiftFloatExpression.referenceID(words[index]) else {
        throw NativeSwiftCoreError.malformed(
          offset: origin(ofWord: index).offset, reason: "Path command is not encoded")
      }
      index += 1
      switch command {
      case NativeSwiftPathCommand.move:
        result.append(NativeSwiftPathElementSnapshot(kind: command, values: try arguments(2)))
      case NativeSwiftPathCommand.line:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(2, skippingLegacyPadding: true)))
      case NativeSwiftPathCommand.quadratic:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(4, skippingLegacyPadding: true)))
      case NativeSwiftPathCommand.conic:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(5, skippingLegacyPadding: true)))
      case NativeSwiftPathCommand.cubic:
        result.append(
          NativeSwiftPathElementSnapshot(
            kind: command, values: try arguments(6, skippingLegacyPadding: true)))
      case NativeSwiftPathCommand.close:
        result.append(NativeSwiftPathElementSnapshot(kind: command, values: []))
      case NativeSwiftPathCommand.done: return result
      default:
        let source = origin(ofWord: commandIndex)
        throw NativeSwiftCoreError.unsupported(
          opcode: source.opcode, offset: source.offset, reason: "path command \(command)")
      }
    }
    return result
  }
}

/// A `PATH_EXPRESSION` held as it arrived: two RPN float expressions over the sampled parameter,
/// which they read as `VAR1`, and the range and point count to sample. The path is generated on
/// every frame, as AndroidX regenerates it whenever a variable it reads changes.
struct ParsedPathExpression {
  /// AndroidX's `Limits.MAX_EXPRESSION_SIZE`: the most words either expression may carry.
  static let maximumExpressionWords = 32
  /// The most points one path samples. AndroidX's only bound is its heap; this one keeps the
  /// generated path, nine words a point, within `PATH_DATA`'s own 20,000 words.
  static let maximumPoints = 2_000

  let flags: Int
  let minimum: UInt32
  let maximum: UInt32
  let count: UInt32
  let expressionX: [UInt32]
  /// The Y expression, or for a polar path the centre: its first two words, read as values.
  let expressionY: [UInt32]
  let opcode: Int
  let offset: Int

  var isPolar: Bool { flags & NativeSwiftPathExpressionFlag.polar != 0 }

  /// The winding the flags' high byte gives the generated path.
  var winding: NativeSwiftPathWinding {
    NativeSwiftPathWinding(
      wireValue: (flags & NativeSwiftPathExpressionFlag.windingMask)
        >> NativeSwiftPathExpressionFlag.windingShift)
  }

  func references(anyOf ids: Set<Int>) -> Bool {
    ([minimum, maximum, count] + expressionX + expressionY).contains { word in
      NativeSwiftFloatExpression.referenceID(word).map(ids.contains) ?? false
    }
  }

  /// The point count `value` asks for, as AndroidX's `(int)` cast reads it. AndroidX throws for
  /// zero points ("path length must be > 1") and fails allocating a negative count, so both refuse
  /// here, as does a count past `maximumPoints` or one that is not a number.
  static func pointCount(_ value: Float, offset: Int) throws -> Int {
    guard value >= 1, value < Float(maximumPoints + 1) else {
      throw NativeSwiftCoreError.malformed(
        offset: offset, reason: "PathExpression count \(value) is outside 1...\(maximumPoints)")
    }
    return Int(value)
  }

  /// The generated path in `PATH_DATA`'s float layout: AndroidX's `PathGenerator` over the
  /// sampled points, a move followed by one cubic per segment, closed when the path loops.
  func data(values: [Int: Float]) throws -> [Float] {
    let points = try Self.pointCount(
      NativeSwiftFloatExpression.resolve(count, values: values), offset: offset)
    let loop = flags & NativeSwiftPathExpressionFlag.loop != 0
    let start = NativeSwiftFloatExpression.resolve(minimum, values: values)
    let end = NativeSwiftFloatExpression.resolve(maximum, values: values)
    let gap = end - start
    let step = loop ? gap / Float(points) : gap / Float(points - 1)
    var centerX: Float = 0
    var centerY: Float = 0
    if isPolar {
      guard expressionY.count >= 2 else {
        throw NativeSwiftCoreError.malformed(
          offset: offset, reason: "A polar PathExpression needs a centre")
      }
      // The centre words are values, not an expression: a literal, or a variable to read.
      centerX = NativeSwiftFloatExpression.resolve(expressionY[0], values: values)
      centerY = NativeSwiftFloatExpression.resolve(expressionY[1], values: values)
    }
    var x = [Float](repeating: 0, count: points)
    var y = [Float](repeating: 0, count: points)
    for index in 0..<points {
      // AndroidX computes `min + i * step`. The first sample is spelled out so a single point,
      // whose step divides by zero, still samples `min` rather than `0 * inf`.
      let parameter = index == 0 ? start : start + Float(index) * step
      let first = try NativeSwiftFloatExpression.evaluate(
        expressionX, values: values, variables: [parameter], opcode: opcode, offset: offset)
      if isPolar {
        x[index] = centerX + first * Float(cos(Double(parameter)))
        y[index] = centerY + first * Float(sin(Double(parameter)))
      } else {
        x[index] = first
        y[index] = try NativeSwiftFloatExpression.evaluate(
          expressionY, values: values, variables: [parameter], opcode: opcode, offset: offset)
      }
    }
    switch flags & NativeSwiftPathExpressionFlag.interpolationMask {
    case NativeSwiftPathExpressionFlag.linear: return Self.linear(x, y, loop: loop)
    case NativeSwiftPathExpressionFlag.monotonic: return Self.monotonic(x, y, loop: loop)
    default: return Self.spline(x, y, loop: loop)
    }
  }

  /// `PathGenerator.Path`: the path data a generator writes, each cubic carrying the current point
  /// in its padding words as AndroidX's does.
  private struct Builder {
    private(set) var data: [Float] = []
    private var currentX: Float = 0
    private var currentY: Float = 0

    private static func token(_ command: Int) -> Float {
      Float(bitPattern: pathCommandWord(command))
    }

    mutating func move(_ x: Float, _ y: Float) {
      data += [Self.token(NativeSwiftPathCommand.move), x, y]
      currentX = x
      currentY = y
    }

    mutating func cubic(
      _ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ x3: Float, _ y3: Float
    ) {
      data += [
        Self.token(NativeSwiftPathCommand.cubic), currentX, currentY, x1, y1, x2, y2, x3, y3,
      ]
      currentX = x3
      currentY = y3
    }

    mutating func close() {
      data.append(Self.token(NativeSwiftPathCommand.close))
    }
  }

  /// Each segment's chord length and unit slope, as both curved generators measure them.
  private static func chords(_ x: [Float], _ y: [Float], segments: Int)
    -> (lengths: [Float], slopesX: [Float], slopesY: [Float])
  {
    var lengths = [Float](repeating: 0, count: segments)
    var slopesX = [Float](repeating: 0, count: segments)
    var slopesY = [Float](repeating: 0, count: segments)
    for from in 0..<segments {
      let to = (from + 1) % x.count
      let dx = x[to] - x[from]
      let dy = y[to] - y[from]
      var distance = Float(hypot(Double(dx), Double(dy)))
      if distance == 0 { distance = 1e-12 }
      lengths[from] = distance
      slopesX[from] = dx / distance
      slopesY[from] = dy / distance
    }
    return (lengths, slopesX, slopesY)
  }

  /// `PathGenerator.Spline`: a C1 cubic spline through the points.
  private static func spline(_ x: [Float], _ y: [Float], loop: Bool) -> [Float] {
    var path = Builder()
    guard let firstX = x.first, let firstY = y.first else { return path.data }
    path.move(firstX, firstY)
    guard x.count > 1 else { return path.data }
    let segments = loop ? x.count : x.count - 1
    let (lengths, slopesX, slopesY) = chords(x, y, segments: segments)
    let tangentsX = smoothTangents(slopesX, lengths, loop: loop)
    let tangentsY = smoothTangents(slopesY, lengths, loop: loop)
    for from in 0..<segments {
      let to = (from + 1) % x.count
      let length = lengths[from]
      path.cubic(
        x[from] + tangentsX[from] * length / 3, y[from] + tangentsY[from] * length / 3,
        x[to] - tangentsX[to] * length / 3, y[to] - tangentsY[to] * length / 3, x[to], y[to])
    }
    if loop { path.close() }
    return path.data
  }

  /// `Spline.smoothTangents`: each point's slope the length-weighted average of its segments'.
  private static func smoothTangents(_ slopes: [Float], _ lengths: [Float], loop: Bool)
    -> [Float]
  {
    let segments = slopes.count
    let count = loop ? segments : segments + 1
    var tangents = [Float](repeating: 0, count: count)
    if loop {
      for index in 0..<count {
        let previous = (index - 1 + segments) % segments
        let next = index % segments
        tangents[index] =
          (lengths[previous] * slopes[next] + lengths[next] * slopes[previous])
          / (lengths[previous] + lengths[next])
      }
    } else {
      tangents[0] = slopes[0]
      tangents[count - 1] = slopes[segments - 1]
      for index in 1..<(count - 1) {
        tangents[index] =
          (lengths[index - 1] * slopes[index] + lengths[index] * slopes[index - 1])
          / (lengths[index - 1] + lengths[index])
      }
    }
    return tangents
  }

  /// `PathGenerator.Monotonic`: a cubic Hermite curve through the points whose tangents are
  /// limited so it never overshoots them. AndroidX places the control points in double precision.
  private static func monotonic(_ x: [Float], _ y: [Float], loop: Bool) -> [Float] {
    var path = Builder()
    guard let firstX = x.first, let firstY = y.first else { return path.data }
    path.move(firstX, firstY)
    guard x.count > 1 else { return path.data }
    let segments = loop ? x.count : x.count - 1
    let (lengths, slopesX, slopesY) = chords(x, y, segments: segments)
    let tangentsX = monotoneTangents(slopesX, lengths, loop: loop)
    let tangentsY = monotoneTangents(slopesY, lengths, loop: loop)
    for from in 0..<segments {
      let to = (from + 1) % x.count
      let length = Double(lengths[from])
      path.cubic(
        Float(Double(x[from]) + Double(tangentsX[from]) * length / 3),
        Float(Double(y[from]) + Double(tangentsY[from]) * length / 3),
        Float(Double(x[to]) - Double(tangentsX[to]) * length / 3),
        Float(Double(y[to]) - Double(tangentsY[to]) * length / 3), x[to], y[to])
    }
    if loop { path.close() }
    return path.data
  }

  /// `Monotonic.monotoneTangents`: Fritsch-Carlson tangents, then its Hyman filter.
  private static func monotoneTangents(_ slopes: [Float], _ lengths: [Float], loop: Bool)
    -> [Float]
  {
    let segments = slopes.count
    let count = loop ? segments : segments + 1
    var tangents = [Float](repeating: 0, count: count)
    for index in 0..<count {
      let previous = (index - 1 + segments) % segments
      let next = index % segments
      if !loop, index == 0 {
        tangents[index] = slopes[0]
      } else if !loop, index == count - 1 {
        tangents[index] = slopes[segments - 1]
      } else {
        let before = slopes[previous]
        let after = slopes[next]
        if before == 0 || after == 0 || (before < 0) != (after < 0) {
          tangents[index] = 0
        } else {
          let first = 2 * lengths[next] + lengths[previous]
          let second = lengths[next] + 2 * lengths[previous]
          tangents[index] = (first + second) / (first / before + second / after)
        }
      }
    }
    for index in 0..<segments {
      let next = (index + 1) % count
      if slopes[index] == 0 {
        tangents[index] = 0
        tangents[next] = 0
      } else {
        let a = tangents[index] / slopes[index]
        let b = tangents[next] / slopes[index]
        let square = a * a + b * b
        if square > 9 {
          let scale = 3 / Float(Double(square).squareRoot())
          tangents[index] = scale * a * slopes[index]
          tangents[next] = scale * b * slopes[index]
        }
      }
    }
    return tangents
  }

  /// `PathGenerator.Linear`: straight segments, each written as a cubic whose control points are
  /// its own ends.
  private static func linear(_ x: [Float], _ y: [Float], loop: Bool) -> [Float] {
    var path = Builder()
    guard let firstX = x.first, let firstY = y.first else { return path.data }
    path.move(firstX, firstY)
    guard x.count > 1 else { return path.data }
    let segments = loop ? x.count : x.count - 1
    for from in 0..<segments {
      let to = (from + 1) % x.count
      path.cubic(x[from], y[from], x[to], y[to], x[to], y[to])
    }
    if loop { path.close() }
    return path.data
  }
}

/// A `PATH_TWEEN` or `DRAW_TWEEN_PATH`: the interpolation between two paths, as AndroidX's
/// `AndroidPaintContext.getPathArray` computes it, trimmed for a draw to the `[start, stop]`
/// fraction of its length. `PATH_TWEEN` never trims, so its `start` and `stop` are 0 and 1.
struct ParsedPathTween {
  /// The most paths of their own one tween may read, counting through tweens of tweens. Each is
  /// resolved on every frame the tween draws, and a chain that tweens its own output doubles that
  /// each time.
  static let maximumSources = 64

  let first: ParsedPath
  let second: ParsedPath
  let fraction: UInt32
  let start: UInt32
  let stop: UInt32
  let opcode: Int
  let offset: Int

  func references(anyOf ids: Set<Int>) -> Bool {
    let ownWords = [fraction, start, stop].contains { word in
      NativeSwiftFloatExpression.referenceID(word).map(ids.contains) ?? false
    }
    return ownWords || first.references(anyOf: ids) || second.references(anyOf: ids)
  }

  /// The interpolated path data. At exactly 0 or 1 it is one side's data as it stands; otherwise
  /// every number in the second path's length is interpolated from the first's, and every command
  /// token is the first path's, so the first path must be at least as long as the second. A side
  /// whose dereferenced id names no path this frame leaves nothing to draw, as the reference's
  /// canvas player draws nothing for a tween it cannot find both paths of.
  func data(values: [Int: Float], integers: [Int: Int]) throws -> [Float] {
    guard let first = first.selected(integers: integers),
      let second = second.selected(integers: integers)
    else { return [] }
    let tween = NativeSwiftFloatExpression.resolve(fraction, values: values)
    if tween == 0 { return try first.data(values: values, integers: integers) }
    if tween == 1 { return try second.data(values: values, integers: integers) }
    let from = try first.data(values: values, integers: integers)
    let to = try second.data(values: values, integers: integers)
    guard from.count >= to.count else {
      throw NativeSwiftCoreError.malformed(
        offset: offset, reason: "A path tween's first path is shorter than its second")
    }
    return to.indices.map { index in
      let a = from[index]
      let b = to[index]
      return a.isNaN || b.isNaN ? a : (b - a) * tween + a
    }
  }

  /// The interpolated path, trimmed as `FloatsToPath.genPath` trims: to its first contour's
  /// `[start, stop]` fraction, unless the range covers the whole path.
  func resolve(values: [Int: Float], integers: [Int: Int]) throws
    -> [NativeSwiftPathElementSnapshot]
  {
    let elements = try ParsedPath.elements(
      fromData: data(values: values, integers: integers), opcode: opcode, offset: offset)
    let trimStart = NativeSwiftFloatExpression.resolve(start, values: values)
    let trimStop = NativeSwiftFloatExpression.resolve(stop, values: values)
    guard trimStart > 0 || trimStop < 1 else { return elements }
    guard trimStart < trimStop else { return [] }
    let measure = NativeSwiftPathMeasure(elements)
    return measure.segment(
      from: max(trimStart, 0) * measure.length, to: min(trimStop, 1) * measure.length)
  }
}

/// A path id word with `NativeSwiftPaintOperationID.pointerDereference` set, as AndroidX
/// `PaintOperation.getId` reads it when the operation paints: the id's low sixteen bits name an
/// integer variable, and that variable's value names the path. `candidates` are the document's
/// paths as they stood when the operation ran, as every other path an operation reads is.
struct ParsedPathDereference {
  let variableID: Int
  let candidates: [Int: ParsedPath]
  /// The most paths of their own any one candidate reads: only the one picked is resolved.
  let sourceCount: Int

  init(variableID: Int, candidates: [Int: ParsedPath]) {
    self.variableID = variableID
    self.candidates = candidates
    sourceCount = max(candidates.values.map(\.sourceCount).max() ?? 1, 1)
  }

  /// The candidate the variable holds in `integers`; an unset variable reads 0, as the
  /// reference's integer state does.
  func path(integers: [Int: Int]) -> ParsedPath? {
    candidates[integers[variableID] ?? 0]
  }
}

/// The measurements AndroidX takes with `PathMeasure(path, false)`: the length of a path's first
/// contour that has any, a point and tangent at a distance along it, and the piece of it between
/// two distances. Curves are measured by flattening, as Skia's measure does; a conic is measured as
/// the rational curve it is, even though the hosts draw it as a quadratic.
struct NativeSwiftPathMeasure {
  private struct Point {
    var x: Double
    var y: Double

    func lerp(_ other: Point, _ t: Double) -> Point {
      Point(x: x + (other.x - x) * t, y: y + (other.y - y) * t)
    }
  }

  private enum Segment {
    case line(Point, Point)
    case quadratic(Point, Point, Point)
    case conic(Point, Point, Point, Double)
    case cubic(Point, Point, Point, Point)
  }

  /// The chords a curve is flattened into: its length is their sum, and a distance maps back to
  /// its parameter by interpolating between them.
  private static let curveSteps = 32

  private var segments: [Segment] = []
  /// Each segment's cumulative distance along the contour at the end of each of its chords.
  private var chordEnds: [[Double]] = []
  private var segmentStarts: [Double] = []
  private var total: Double = 0

  /// The contour's length; zero when the path has no contour with any length.
  var length: Float { Float(total) }

  init(_ elements: [NativeSwiftPathElementSnapshot]) {
    var start = Point(x: 0, y: 0)
    var current = start
    var contour: [Segment] = []
    func point(_ values: [Float], _ index: Int) -> Point {
      Point(x: Double(values[index]), y: Double(values[index + 1]))
    }
    for element in elements {
      let values = element.values
      switch element.kind {
      case NativeSwiftPathCommand.move where values.count >= 2:
        if adopt(contour) { return }
        contour.removeAll()
        start = point(values, 0)
        current = start
      case NativeSwiftPathCommand.line where values.count >= 2:
        let end = point(values, 0)
        contour.append(.line(current, end))
        current = end
      case NativeSwiftPathCommand.quadratic where values.count >= 4:
        let end = point(values, 2)
        contour.append(.quadratic(current, point(values, 0), end))
        current = end
      case NativeSwiftPathCommand.conic where values.count >= 5:
        let end = point(values, 2)
        contour.append(.conic(current, point(values, 0), end, Double(values[4])))
        current = end
      case NativeSwiftPathCommand.cubic where values.count >= 6:
        let end = point(values, 4)
        contour.append(.cubic(current, point(values, 0), point(values, 2), end))
        current = end
      case NativeSwiftPathCommand.close:
        // A close draws the line back to the contour's start, and ends the contour.
        if current.x != start.x || current.y != start.y {
          contour.append(.line(current, start))
        }
        current = start
        if adopt(contour) { return }
        contour.removeAll()
      default:
        continue
      }
    }
    _ = adopt(contour)
  }

  /// Measures `contour` and keeps it when it has a finite, non-zero length. Zero-length segments
  /// are dropped, as Skia's measure drops them.
  private mutating func adopt(_ contour: [Segment]) -> Bool {
    var kept: [Segment] = []
    var ends: [[Double]] = []
    var starts: [Double] = []
    var distance: Double = 0
    for segment in contour {
      let steps: Int
      if case .line = segment { steps = 1 } else { steps = Self.curveSteps }
      var previous = Self.position(segment, 0)
      var segmentEnds: [Double] = []
      segmentEnds.reserveCapacity(steps)
      var running = distance
      for step in 1...steps {
        let next = Self.position(segment, Double(step) / Double(steps))
        running += hypot(next.x - previous.x, next.y - previous.y)
        segmentEnds.append(running)
        previous = next
      }
      guard running.isFinite else { return false }
      guard running > distance else { continue }
      kept.append(segment)
      starts.append(distance)
      ends.append(segmentEnds)
      distance = running
    }
    guard distance > 0, !kept.isEmpty else { return false }
    segments = kept
    chordEnds = ends
    segmentStarts = starts
    total = distance
    return true
  }

  /// The segment `distance` falls in and its parameter there, `distance` already within
  /// `0...total`.
  private func locate(_ distance: Double) -> (segment: Int, t: Double) {
    var low = 0
    var high = segments.count - 1
    while low < high {
      let middle = (low + high) / 2
      if chordEnds[middle][chordEnds[middle].count - 1] < distance {
        low = middle + 1
      } else {
        high = middle
      }
    }
    let ends = chordEnds[low]
    var previous = segmentStarts[low]
    for (index, end) in ends.enumerated() {
      if end >= distance {
        let span = end - previous
        let fraction = span > 0 ? min(max((distance - previous) / span, 0), 1) : 0
        return (low, (Double(index) + fraction) / Double(ends.count))
      }
      previous = end
    }
    return (low, 1)
  }

  private static func position(_ segment: Segment, _ t: Double) -> Point {
    let u = 1 - t
    switch segment {
    case .line(let a, let b):
      return a.lerp(b, t)
    case .quadratic(let p0, let p1, let p2):
      return Point(
        x: u * u * p0.x + 2 * u * t * p1.x + t * t * p2.x,
        y: u * u * p0.y + 2 * u * t * p1.y + t * t * p2.y)
    case .conic(let p0, let p1, let p2, let w):
      let denominator = u * u + 2 * w * u * t + t * t
      return Point(
        x: (u * u * p0.x + 2 * w * u * t * p1.x + t * t * p2.x) / denominator,
        y: (u * u * p0.y + 2 * w * u * t * p1.y + t * t * p2.y) / denominator)
    case .cubic(let p0, let p1, let p2, let p3):
      return blossom(p0, p1, p2, p3, t, t, t)
    }
  }

  /// The direction of travel at `t`, not normalized. Where a curve's derivative vanishes, at an
  /// end whose control point coincides with it, the chord to the next distinct control point
  /// stands in, as Skia's evaluators do.
  private static func tangent(_ segment: Segment, _ t: Double) -> Point {
    func isZero(_ point: Point) -> Bool { point.x == 0 && point.y == 0 }
    func difference(_ a: Point, _ b: Point) -> Point { Point(x: b.x - a.x, y: b.y - a.y) }
    let u = 1 - t
    switch segment {
    case .line(let a, let b):
      return difference(a, b)
    case .quadratic(let p0, let p1, let p2):
      let derivative = Point(
        x: 2 * u * (p1.x - p0.x) + 2 * t * (p2.x - p1.x),
        y: 2 * u * (p1.y - p0.y) + 2 * t * (p2.y - p1.y))
      return isZero(derivative) ? difference(p0, p2) : derivative
    case .conic(let p0, let p1, let p2, let w):
      // The quotient rule over the rational form: N'D - ND', whose direction is the tangent's.
      let d = u * u + 2 * w * u * t + t * t
      let dPrime = -2 * u + 2 * w * (1 - 2 * t) + 2 * t
      func component(_ a: Double, _ b: Double, _ c: Double) -> Double {
        let n = u * u * a + 2 * w * u * t * b + t * t * c
        let nPrime = -2 * u * a + 2 * w * (1 - 2 * t) * b + 2 * t * c
        return nPrime * d - n * dPrime
      }
      let derivative = Point(x: component(p0.x, p1.x, p2.x), y: component(p0.y, p1.y, p2.y))
      return isZero(derivative) ? difference(p0, p2) : derivative
    case .cubic(let p0, let p1, let p2, let p3):
      let derivative = Point(
        x: 3 * (u * u * (p1.x - p0.x) + 2 * u * t * (p2.x - p1.x) + t * t * (p3.x - p2.x)),
        y: 3 * (u * u * (p1.y - p0.y) + 2 * u * t * (p2.y - p1.y) + t * t * (p3.y - p2.y)))
      guard isZero(derivative) else { return derivative }
      let chord = t < 0.5 ? difference(p0, p2) : difference(p1, p3)
      return isZero(chord) ? difference(p0, p3) : chord
    }
  }

  /// The cubic's blossom at `(a, b, c)`: de Casteljau with a parameter per level. `(t, t, t)` is
  /// the point at `t`, and `(t0, t0, t1)` and `(t0, t1, t1)` are the control points of the piece
  /// between `t0` and `t1`.
  private static func blossom(
    _ p0: Point, _ p1: Point, _ p2: Point, _ p3: Point, _ a: Double, _ b: Double, _ c: Double
  ) -> Point {
    let first = p0.lerp(p1, a)
    let second = p1.lerp(p2, a)
    let third = p2.lerp(p3, a)
    return first.lerp(second, b).lerp(second.lerp(third, b), c)
  }

  /// The segment's piece from `t0` to `t1`, as the element that continues from its start.
  private static func piece(_ segment: Segment, from t0: Double, to t1: Double)
    -> NativeSwiftPathElementSnapshot
  {
    func element(_ kind: Int, _ values: [Double]) -> NativeSwiftPathElementSnapshot {
      NativeSwiftPathElementSnapshot(kind: kind, values: values.map { Float($0) })
    }
    let end = position(segment, t1)
    if t0 == t1 { return element(NativeSwiftPathCommand.line, [end.x, end.y]) }
    switch segment {
    case .line:
      return element(NativeSwiftPathCommand.line, [end.x, end.y])
    case .quadratic(let p0, let p1, let p2):
      // The quadratic's blossom at (t0, t1).
      let control = p0.lerp(p1, t0).lerp(p1.lerp(p2, t0), t1)
      return element(NativeSwiftPathCommand.quadratic, [control.x, control.y, end.x, end.y])
    case .conic(let p0, let p1, let p2, let w):
      // The same blossom over the homogeneous control points, then back to unit end weights.
      func blossom(_ a: Double, _ b: Double) -> (x: Double, y: Double, w: Double) {
        func mix(_ v0: Double, _ v1: Double, _ v2: Double) -> Double {
          let left = v0 + (v1 - v0) * a
          let right = v1 + (v2 - v1) * a
          return left + (right - left) * b
        }
        return (mix(p0.x, w * p1.x, p2.x), mix(p0.y, w * p1.y, p2.y), mix(1, w, 1))
      }
      let startWeight = blossom(t0, t0).w
      let control = blossom(t0, t1)
      let endWeight = blossom(t1, t1).w
      guard startWeight > 0, endWeight > 0, control.w != 0 else {
        return element(NativeSwiftPathCommand.line, [end.x, end.y])
      }
      let weight = control.w / (startWeight * endWeight).squareRoot()
      guard weight.isFinite else { return element(NativeSwiftPathCommand.line, [end.x, end.y]) }
      return element(
        NativeSwiftPathCommand.conic,
        [control.x / control.w, control.y / control.w, end.x, end.y, weight])
    case .cubic(let p0, let p1, let p2, let p3):
      let first = blossom(p0, p1, p2, p3, t0, t0, t1)
      let second = blossom(p0, p1, p2, p3, t0, t1, t1)
      return element(
        NativeSwiftPathCommand.cubic, [first.x, first.y, second.x, second.y, end.x, end.y])
    }
  }

  /// The piece of the contour between two distances along it, starting with a move, as Skia's
  /// `getSegment(start, stop, dst, true)`: the distances are clamped to the contour, and a range
  /// that is empty once clamped yields nothing.
  func segment(from startDistance: Float, to stopDistance: Float)
    -> [NativeSwiftPathElementSnapshot]
  {
    guard !segments.isEmpty else { return [] }
    let from = max(Double(startDistance), 0)
    let to = min(Double(stopDistance), total)
    // `PathMeasure.getSegment` returns false, leaving the destination empty, once the clamped
    // start is not before the stop: an equal pair is a zero-length piece that draws nothing.
    guard from < to else { return [] }
    let start = locate(from)
    let stop = locate(to)
    let origin = Self.position(segments[start.segment], start.t)
    var result = [
      NativeSwiftPathElementSnapshot(
        kind: NativeSwiftPathCommand.move, values: [Float(origin.x), Float(origin.y)])
    ]
    if start.segment == stop.segment {
      result.append(Self.piece(segments[start.segment], from: start.t, to: stop.t))
      return result
    }
    result.append(Self.piece(segments[start.segment], from: start.t, to: 1))
    for index in stride(from: start.segment + 1, to: stop.segment, by: 1) {
      result.append(Self.piece(segments[index], from: 0, to: 1))
    }
    result.append(Self.piece(segments[stop.segment], from: 0, to: stop.t))
    return result
  }

  /// `AndroidPaintContext.matrixFromPath`: the matrix `PathMeasure.getMatrix` gives at
  /// `(length * fraction) % length`, in `NativeSwiftDrawKind.matrixFromPath`'s value order. It
  /// rotates onto the tangent for `NativeSwiftMatrixFromPathFlag.tangent`, then translates to the
  /// point for `position`. A path with no length, or a distance that is not a number, leaves the
  /// identity, as a failed `getMatrix` leaves the reference's fresh `Matrix`.
  func matrix(fraction: Float, flags: Int) -> [Float] {
    let identity: [Float] = [1, 0, 0, 1, 0, 0]
    guard !segments.isEmpty, length > 0 else { return identity }
    let distance = (length * fraction).truncatingRemainder(dividingBy: length)
    guard !distance.isNaN else { return identity }
    let located = locate(min(max(Double(distance), 0), total))
    let segment = segments[located.segment]
    var result = identity
    if flags & NativeSwiftMatrixFromPathFlag.tangent != 0 {
      let direction = Self.tangent(segment, located.t)
      let magnitude = hypot(direction.x, direction.y)
      if magnitude > 0 {
        let cosine = Float(direction.x / magnitude)
        let sine = Float(direction.y / magnitude)
        result[0] = cosine
        result[1] = sine
        result[2] = -sine
        result[3] = cosine
      }
    }
    if flags & NativeSwiftMatrixFromPathFlag.position != 0 {
      let point = Self.position(segment, located.t)
      result[4] = Float(point.x)
      result[5] = Float(point.y)
    }
    return result.allSatisfy(\.isFinite) ? result : identity
  }
}

import Foundation

struct ParsedParticleDefinition {
  let id: Int
  let particleCount: Int
  let variableIDs: [Int]
  let initializationEquations: [[UInt32]]
  /// The `PARTICLE_DEFINE` operation's byte offset, for evaluation failures.
  let offset: Int
}

struct ParsedParticleLoop {
  let id: Int
  let restartEquation: [UInt32]
  let updateEquations: [[UInt32]]
  /// The `PARTICLE_LOOP` operation's byte offset, for evaluation failures.
  let offset: Int
}

/// AndroidX's `ParticlesCompare`: a condition tested over a range of one system's particles.
///
/// With only `firstEquations`, each particle in `minimumWord..<maximumWord` whose `condition` is
/// positive is replaced by those equations, all evaluated against its previous values. With both
/// equation sets, every pair `(first, second)` in the range, `second` before `first`, is tested:
/// a variable reference followed by `cmd1` reads the first particle, one followed by `cmd2` the
/// second, and a bare reference the first. A pair that passes replaces the first particle with
/// `firstEquations` and the second with `secondEquations`, whose bare references read the second
/// particle. A negative bound means the start or the end of the system.
struct ParsedParticleCompare {
  let id: Int
  /// Read and kept, but the reference's `paint` never consults them.
  let flags: Int
  let minimumWord: UInt32
  let maximumWord: UInt32
  let condition: [UInt32]
  let firstEquations: [[UInt32]]
  /// Empty when the wire carries none, which selects the one-particle form.
  let secondEquations: [[UInt32]]
  /// The `PARTICLE_COMPARE` operation's byte offset, for evaluation failures.
  let offset: Int

  var comparesPairs: Bool { !firstEquations.isEmpty && !secondEquations.isEmpty }
}

/// A per-frame particle operation, kept in wire order: a comparison sees what the loops before it
/// left, and a loop what the comparisons before it changed.
enum ParsedParticleOperation {
  case loop(ParsedParticleLoop)
  case compare(ParsedParticleCompare)
}

final class NativeSwiftParticleSystemRuntime {
  let id: Int
  let variableIDs: [Int]
  let initializationEquations: [[UInt32]]
  let definitionOffset: Int
  var particles: [[Float]]

  init(definition: ParsedParticleDefinition, values: [Int: Float]) {
    id = definition.id
    variableIDs = definition.variableIDs
    initializationEquations = definition.initializationEquations
    definitionOffset = definition.offset
    particles = Array(repeating: Array(repeating: 0, count: definition.variableIDs.count), count: definition.particleCount)
    var scratch = values
    var variables: [Float] = [0, 0, 0]
    for index in particles.indices {
      initialize(index: index, baseValues: values, scratch: &scratch, variables: &variables)
    }
  }

  private init(copying other: NativeSwiftParticleSystemRuntime) {
    id = other.id
    variableIDs = other.variableIDs
    initializationEquations = other.initializationEquations
    definitionOffset = other.definitionOffset
    particles = other.particles
  }

  func detachedCopy() -> NativeSwiftParticleSystemRuntime { NativeSwiftParticleSystemRuntime(copying: self) }

  var snapshot: NativeSwiftParticleSystemSnapshot {
    NativeSwiftParticleSystemSnapshot(id: id, variableIDs: variableIDs, particles: particles)
  }

  /// Advances every particle one step.
  ///
  /// One working copy of `baseValues` serves every particle: the only keys a particle writes are
  /// its own variable ids, and each iteration overwrites all of them before evaluating anything, so
  /// the table a particle's equations read is exactly `baseValues` plus its own variables -- what a
  /// fresh copy per particle gave, without copying the whole value table up to 8,000 times a frame.
  func advance(loop: ParsedParticleLoop, baseValues: [Int: Float]) {
    guard loop.updateEquations.count == variableIDs.count else { return }
    var values = baseValues
    var variables: [Float] = [0, 0, 0]
    for index in particles.indices {
      let previous = particles[index]
      for (variableIndex, variableID) in variableIDs.enumerated() { values[variableID] = previous[variableIndex] }
      variables[0] = Float(index)
      let updated = loop.updateEquations.map {
        (try? NativeSwiftFloatExpression.evaluate(
          $0, values: values, variables: variables, opcode: NativeSwiftWireOpcode.particleLoop,
          offset: loop.offset)) ?? 0
      }
      particles[index] = updated
      for (variableIndex, variableID) in variableIDs.enumerated() { values[variableID] = updated[variableIndex] }
      if (
        (try? NativeSwiftFloatExpression.evaluate(
          loop.restartEquation, values: values, variables: variables,
          opcode: NativeSwiftWireOpcode.particleLoop, offset: loop.offset)) ?? 0
      ) > 0 {
        // `values` is this loop's working copy; the next particle overwrites every variable id
        // `initialize` touches, so it is safe to lend it out as scratch.
        initialize(index: index, baseValues: baseValues, scratch: &values, variables: &variables)
      }
    }
  }

  /// Applies one `PARTICLE_COMPARE` to the retained particles in place, as AndroidX's
  /// `ParticlesCompare.paint` does. Returns the particle the reference leaves loaded into the
  /// system's variables afterwards -- the last one it published, nil when it visited none -- and
  /// whether any condition passed, which is when the reference asks to be painted again.
  ///
  /// Every equation of a passing particle (or pair) is resolved before any is evaluated, so all of
  /// them read the values from before the update, as the reference's substituted copies do. An
  /// expression that fails to evaluate counts as 0, as a loop's does.
  func compare(_ compare: ParsedParticleCompare, baseValues: [Int: Float])
    -> (published: [Float]?, fired: Bool)
  {
    let count = particles.count
    func bound(_ word: UInt32, negative: Int) -> Int {
      let value = NativeSwiftFloatExpression.resolve(word, values: baseValues)
      // Java's `(int)` cast: NaN is 0 and a value past the range saturates; the reference indexes
      // past the end of the system from there, which is clamped here instead.
      guard value >= 0 else { return value.isNaN ? 0 : negative }
      return min(Int(min(value, Float(Int32.max))), count)
    }
    let start = bound(compare.minimumWord, negative: 0)
    let end = bound(compare.maximumWord, negative: count)
    guard start < end, compare.firstEquations.count == variableIDs.count else {
      return (nil, false)
    }
    var values = baseValues
    func load(_ particle: [Float]) {
      for (index, variableID) in variableIDs.enumerated() { values[variableID] = particle[index] }
    }
    func evaluate(_ words: [UInt32]) -> Float {
      (try? NativeSwiftFloatExpression.evaluate(
        words, values: values, opcode: NativeSwiftWireOpcode.particleCompare,
        offset: compare.offset)) ?? 0
    }
    var published: [Float]?
    var fired = false
    guard compare.comparesPairs else {
      for index in start..<end {
        load(particles[index])
        published = particles[index]
        guard evaluate(compare.condition) > 0 else { continue }
        let updated = compare.firstEquations.map { evaluate($0) }
        particles[index] = updated
        published = updated
        fired = true
      }
      return (published, fired)
    }
    guard compare.secondEquations.count == variableIDs.count else { return (nil, false) }
    for secondIndex in start..<end {
      for firstIndex in (secondIndex + 1)..<max(secondIndex + 1, end) {
        let first = particles[firstIndex]
        let second = particles[secondIndex]
        load(first)
        published = first
        guard evaluate(pairWords(compare.condition, first: first, second: second)) > 0 else {
          continue
        }
        let updatedFirst = compare.firstEquations.map {
          evaluate(pairWords($0, first: first, second: second))
        }
        // The second set's bare references read the second particle.
        load(second)
        let updatedSecond = compare.secondEquations.map {
          evaluate(pairWords($0, first: first, second: second))
        }
        particles[firstIndex] = updatedFirst
        particles[secondIndex] = updatedSecond
        published = updatedSecond
        fired = true
      }
    }
    return (published, fired)
  }

  /// `words` with each reference to one of this system's variables that `cmd1` or `cmd2` follows
  /// replaced by the first or second particle's value as a literal, and the command dropped: what
  /// the reference's substitution leaves, a literal and a `NOP`. Every other word is kept.
  private func pairWords(_ words: [UInt32], first: [Float], second: [Float]) -> [UInt32] {
    let command1 = NativeSwiftFloatExpression.operatorWord(NativeSwiftFloatOperator.cmd1)
    let command2 = NativeSwiftFloatExpression.operatorWord(NativeSwiftFloatOperator.cmd2)
    var result: [UInt32] = []
    result.reserveCapacity(words.count)
    var index = 0
    while index < words.count {
      let word = words[index]
      if index + 1 < words.count, words[index + 1] == command1 || words[index + 1] == command2,
        let id = NativeSwiftFloatExpression.referenceID(word),
        let variableIndex = variableIDs.firstIndex(of: id)
      {
        let particle = words[index + 1] == command1 ? first : second
        result.append(particle[variableIndex].bitPattern)
        index += 2
        continue
      }
      result.append(word)
      index += 1
    }
    return result
  }

  /// Re-seeds one particle from its initialization equations.
  ///
  /// `scratch` is a working copy of `baseValues` that may still hold another particle's variables.
  /// Each variable id is restored to its `baseValues` entry (or removed) first, so equation n reads
  /// the variables before it as just initialized and the rest as `baseValues` has them -- exactly
  /// what a fresh copy of `baseValues` read.
  private func initialize(
    index: Int, baseValues: [Int: Float], scratch values: inout [Int: Float],
    variables: inout [Float]
  ) {
    for variableID in variableIDs { values[variableID] = baseValues[variableID] }
    variables[0] = Float(index)
    variables[1] = 0
    variables[2] = 0
    var initialized = Array(repeating: Float(0), count: variableIDs.count)
    for (variableIndex, equation) in initializationEquations.enumerated() {
      let value =
        (try? NativeSwiftFloatExpression.evaluate(
          equation, values: values, variables: variables,
          opcode: NativeSwiftWireOpcode.particleDefine, offset: definitionOffset)) ?? 0
      initialized[variableIndex] = value
      values[variableIDs[variableIndex]] = value
    }
    particles[index] = initialized
  }
}

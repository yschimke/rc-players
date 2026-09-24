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

import Foundation

struct ParsedParticleDefinition {
  let id: Int
  let particleCount: Int
  let variableIDs: [Int]
  let initializationEquations: [[UInt32]]
}

struct ParsedParticleLoop {
  let id: Int
  let restartEquation: [UInt32]
  let updateEquations: [[UInt32]]
}

final class NativeSwiftParticleSystemRuntime {
  let id: Int
  let variableIDs: [Int]
  let initializationEquations: [[UInt32]]
  var particles: [[Float]]

  init(definition: ParsedParticleDefinition, values: [Int: Float]) {
    id = definition.id
    variableIDs = definition.variableIDs
    initializationEquations = definition.initializationEquations
    particles = Array(repeating: Array(repeating: 0, count: definition.variableIDs.count), count: definition.particleCount)
    for index in particles.indices { initialize(index: index, baseValues: values) }
  }

  private init(copying other: NativeSwiftParticleSystemRuntime) {
    id = other.id
    variableIDs = other.variableIDs
    initializationEquations = other.initializationEquations
    particles = other.particles
  }

  func detachedCopy() -> NativeSwiftParticleSystemRuntime { NativeSwiftParticleSystemRuntime(copying: self) }

  var snapshot: NativeSwiftParticleSystemSnapshot {
    NativeSwiftParticleSystemSnapshot(id: id, variableIDs: variableIDs, particles: particles)
  }

  func advance(loop: ParsedParticleLoop, baseValues: [Int: Float]) {
    guard loop.updateEquations.count == variableIDs.count else { return }
    for index in particles.indices {
      let previous = particles[index]
      var values = baseValues
      for (variableIndex, variableID) in variableIDs.enumerated() { values[variableID] = previous[variableIndex] }
      let variables = [Float(index), 0, 0]
      let updated = loop.updateEquations.map {
        (try? NativeSwiftFloatExpression.evaluate($0, values: values, variables: variables)) ?? 0
      }
      particles[index] = updated
      for (variableIndex, variableID) in variableIDs.enumerated() { values[variableID] = updated[variableIndex] }
      if (
        (try? NativeSwiftFloatExpression.evaluate(
          loop.restartEquation, values: values, variables: variables)) ?? 0
      ) > 0 {
        initialize(index: index, baseValues: baseValues)
      }
    }
  }

  private func initialize(index: Int, baseValues: [Int: Float]) {
    var values = baseValues
    let variables = [Float(index), 0, 0]
    var initialized = Array(repeating: Float(0), count: variableIDs.count)
    for (variableIndex, equation) in initializationEquations.enumerated() {
      let value =
        (try? NativeSwiftFloatExpression.evaluate(
          equation, values: values, variables: variables)) ?? 0
      initialized[variableIndex] = value
      values[variableIDs[variableIndex]] = value
    }
    particles[index] = initialized
  }
}

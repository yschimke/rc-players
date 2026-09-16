# AREA-11: Particle Simulation Engine Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview

RemoteCompose includes a client-side, high-performance **Particle Simulation Engine** (`ParticlesCreate`, `ParticlesLoop`, `ParticlesCompare`) and layout-level particle transition support (`ParticleAnimation`). 

The engine simulates dynamic visual elements (e.g., rain, snow, sparks, confetti, flocking, or ambient dust) with zero per-frame object allocation. By executing the physics simulation loop natively on the player using contiguous float buffers and RPN physics equations, the system avoids generating per-particle serialization overhead or garbage collection churn.

---

## 2. Architecture & Memory Layout

A particle system is backed by a contiguous 2D float state buffer:

$$\text{StateBuffer}[N][V]$$

Where $N$ is the particle capacity ($N \le \text{MAX\_PARTICLE\_COUNT} = 8000$, from `Limits.java`), and $V$ is the number of state variables per particle (`varId.length` $\le 2000$).

```
+-------------------------------------------------------------------------------+
| Particle 0: [ x, y, vx, vy, life, maxLife, scale, alpha, rot, ... ]          |
+-------------------------------------------------------------------------------+
| Particle 1: [ x, y, vx, vy, life, maxLife, scale, alpha, rot, ... ]          |
+-------------------------------------------------------------------------------+
| ...                                                                           |
+-------------------------------------------------------------------------------+
| Particle N-1: [ x, y, vx, vy, life, maxLife, scale, alpha, rot, ... ]        |
+-------------------------------------------------------------------------------+
```

---

## 3. Particle System Operations

### 3.1 ParticlesCreate (`PARTICLE_DEFINE`, Opcode 161)
Allocates and initializes the particle system.
* **Wire Format**:
  * `id: INT`: Unique identifier for the particle system.
  * `particleCount: INT`: Number of particles ($N \le 8000$).
  * `varCount: INT`: Number of variables per particle ($V \le 2000$).
  * For each variable $j \in [0, V-1]$:
    * `varId: INT`: The variable identifier for that attribute.
    * `equLen: INT`: Length of the initialization equation ($L \le 32$).
    * `equation: FLOAT[equLen]`: RPN initialization expression.
* **Player Behavior**:
  1. Allocates state buffer `float[particleCount][varCount]`.
  2. For each particle index $i \in [0, N-1]$, binds particle index $i$ to `AnimatedFloatExpression.VAR1` and evaluates the initialization equations to seed initial attributes.

### 3.2 ParticlesLoop (`PARTICLE_LOOP`, Opcode 163)
Executes numerical integration and drives per-particle child rendering.
* **Wire Format**:
  * `id: INT`: Target particle system ID.
  * `restartLen: INT`: Length of restart equation (`0` if no restart).
  * `restartEquation: FLOAT[restartLen]`: RPN condition evaluated for recycling.
  * `varCount: INT`: Number of update equations ($V \le 2000$).
  * For each variable $j \in [0, V-1]$:
    * `equLen: INT`: Length of update equation ($L \le 32$).
    * `equation: FLOAT[equLen]`: RPN update equation.
* **Container Semantics**:
  * `ParticlesLoop` implements `Container`. It contains child operations (`mList`) representing the visual representation of each particle (e.g. `DrawCircle`, `DrawBitmapScaled`).
* **Frame Update Algorithm**:
  For each particle $i \in [0, N-1]$:
  1. Loads particle variables into `RemoteContext` (`remoteContext.loadFloat(mVarId[j], mParticles[i][j])`).
  2. Evaluates update equations and writes new values back to the particle's slice in `mParticles`.
  3. Evaluates `restartEquation`: if result $> 0$, calls `initializeParticle(i)` to respawn the particle.
  4. Executes all child operations in `mList` for particle $i$.
  5. Calls `context.needsRepaint()` to request continuous vsync scheduling.

### 3.3 ParticlesCompare (`PARTICLE_COMPARE`, Opcode 194)
Applies conditional rules, boundary constraints, and pairwise particle interactions.
* **Wire Format**:
  * `id: INT`: Target particle system ID.
  * `flags: SHORT`: Configuration flags.
  * `min: FLOAT`, `max: FLOAT`: Range of particle indices to process.
  * `expression: FLOAT[]`: Comparison/collision condition equation.
  * `result1: FLOAT[][]`: Update equations applied when condition evaluates $> 0$.
  * `result2: FLOAT[][]`: Secondary update equations (used for 2-body collisions; `null` if 1-body).
* **Execution Modes**:
  * **1-Body Boundary Checks (`result2 == null`)**:
    Evaluates `expression` for each particle $i \in [\text{min}, \text{max}]$. If $> 0$, applies `result1` update equations to particle $i$ and executes child operations.
  * **2-Body Pairwise Interactions (`result2 != null`)**:
    Iterates through all particle pairs $(k, i)$ with $k < i$. Evaluates `expression` using `AnimatedFloatExpression.CMD1` (binding particle 1 attributes) and `AnimatedFloatExpression.CMD2` (binding particle 2 attributes). If $> 0$ (collision detected), updates particle 1 via `result1`, particle 2 via `result2`, executes child rendering, and triggers `context.needsRepaint()`.

---

## 4. Component Layout Particle Transitions

In addition to procedural particle fields, RemoteCompose supports particle-based component enter/exit transitions via `AnimationSpec` (`enterAnimation = ANIMATION.PARTICLE (7)` or `exitAnimation = ANIMATION.PARTICLE (7)`).

* Governed by `ParticleAnimation.java`.
* When a component transitions into or out of the hierarchy, its rectangular layout bounds are subdivided into 20 procedural particle elements (`ArrayList<Particle>`).
* Over the transition duration (`visibilityDuration`), these particles scatter outwards with randomized velocities, radii, and alpha fades, creating a disintegration or coalescing visual effect.

---

## 5. Conformance Requirements

1. **Capacity Clamping**: Declaring a particle system with `particleCount > MAX_PARTICLE_COUNT` (8000) or `equLen > MAX_EQU_LENGTH` (32 in Create/Loop, 46 in Compare) must throw a validation error and refuse to allocate excess state.
2. **Container Hierarchy**: Conforming players must treat `ParticlesLoop` and `ParticlesCompare` as nested containers, properly maintaining parent/child operational scoping during binary parsing and execution.
3. **Buffer Isolation**: Memory accesses during particle update loops must strictly respect array bounds $[0, N-1] \times [0, V-1]$ without memory corruption.
4. **Repaint Scheduling**: Conforming players executing an active `ParticlesLoop` must invoke `context.needsRepaint()` every frame to guarantee continuous animation playback.

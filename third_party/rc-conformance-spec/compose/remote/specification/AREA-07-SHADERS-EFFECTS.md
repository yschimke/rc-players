# AREA-07: Shaders & Dynamic GPU Materials Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player
**Normative Status:** Core Specification  

---

## 1. Overview

RemoteCompose supports hardware-accelerated programmable shaders and dynamic GPU materials via **AGSL (Android Graphics Shading Language)**. 

This subsystem allows documents to define custom procedural fills, liquid effects, glow/lighting models, and image distortions. A conforming player must compile shader programs, link shader uniforms reactively to RemoteCompose dynamic expressions, enforce strict execution sandboxing (`ShaderControl`), and provide visual fallbacks when shaders are unsupported or blocked.

---

## 2. Shader Architecture & Lifecycle

```
[Document Ingestion: DATA_SHADER (Opcode 45)]
                     |
                     v
[Player Security Check: ShaderControl.acceptShader(source)]
    ├── REJECTED: Flag shader as disabled; activate fallback solid color
    └── ACCEPTED: Compile shader program (e.g., RuntimeShader in Android)
                     |
                     v
[Frame Render Loop]
    ├── Ingest updated expression values for uniforms (time, touch, colors)
    ├── Bind uniforms to active GPU shader program
    ├── Attach shader to PaintBundle
    └── Execute draw calls (DRAW_RECT, DRAW_PATH, etc.)
```

---

## 3. Shader Declaration & Uniform Binding

### 3.1 ShaderData (`Opcode 45`)
* **Fields**:
  * `shaderId: INT`: Unique identifier for the shader instance.
  * `shaderSource: UTF8`: Text containing AGSL / GLSL fragment shader code.
  * `uniformBindingsCount: INT`: Number of dynamic uniform bindings.
  * `uniformBindings: UniformBinding[]`: Array of `(uniformName: UTF8, varId: INT, type: BYTE)`.

### 3.2 Uniform Type System

| Uniform Type | Wire Value | AGSL Type | Binding Source |
| :--- | :--- | :--- | :--- |
| `FLOAT` | `1` | `float` | Resolved dynamic float ID from `RemoteContext` |
| `FLOAT2` | `2` | `float2` | Array of 2 float IDs (e.g., resolution, touch $X, Y$) |
| `FLOAT3` | `3` | `float3` | Array of 3 float IDs (e.g., 3D position, normal) |
| `FLOAT4` | `4` | `float4` | Array of 4 float IDs |
| `COLOR` | `5` | `half4` / `vec4` | ARGB Color ID unpacked into normalized $[r, g, b, a] \in [0.0, 1.0]$ |
| `IMAGE_SAMPLER`| `6`| `shader` | Reference to a `DATA_BITMAP` input texture |

### 3.3 Dynamic Reactive Updates
Before rasterizing any primitive using a shader:
1. The player checks if any variable bound to a uniform is marked dirty in `RemoteContext`.
2. For each bound uniform, the player extracts the current numerical value and uploads it to the GPU program via `shader.setFloatUniform()` or `shader.setColorUniform()`.
3. If `ANIMATION_TIME` is bound to a uniform, the uniform is re-uploaded on every frame.

---

## 4. Security, Sandboxing & Policy (`ShaderControl`)

Because shader execution runs directly on the GPU, malicious or unbounded shaders could induce GPU hangs or device battery drain.

### 4.1 ShaderControl Policy Interface
The player provides a host configuration API:
```java
public interface ShaderControl {
    boolean acceptShader(@NonNull String shaderSource);
}
```
* **Default Policy**: By default, `RemoteComposePlayer` initializes with a strict deny-all policy: `(shader) -> false`. Shaders are **disabled** unless the host application explicitly configures a permissive or verifying `ShaderControl`.
* **Verification Rules**: Conforming implementations should reject shaders that:
  1. Exceed a maximum byte size (default: **16 KB**).
  2. Contain infinite loops or high instruction counts that risk GPU watchdogs.
  3. Attempt texture lookups outside allocated surface dimensions.

### 4.2 Graceful Degradation
If a shader is rejected by `ShaderControl` or fails GPU compilation:
* The player **MUST NOT crash**.
* The player sets the active paint shader to `null` and falls back to rendering a solid color fill (using the primary color defined in `PaintBundle` or a default neutral color).

---

## 5. Visual Filters & Compositing

In addition to custom procedural shaders, the player supports built-in filters:
* **Color Filters**: Matrix color transforms and Porter-Duff tint filters.
* **Layer Blur**: Gaussian blur radii applied through `GraphicsLayerModifierOperation`.
* **Blend Modes**: Modern Skia blend modes including `Multiply`, `Screen`, `Overlay`, `Darken`, and `Lighten`.

---

## 6. Conformance Requirements

1. **Security Gate Enforcement**: If `ShaderControl` returns `false`, no shader compilation or GPU program execution may take place.
2. **Color Normalization**: A color ID containing `0xFF804020` bound to an AGSL `half4` uniform must upload $[0.502, 0.251, 0.125, 1.0]$ within floating point epsilon.
3. **Compilation Failure Resilience**: A shader containing syntax errors must trigger a log message, fallback to solid fill, and continue rendering the rest of the document uninterrupted.

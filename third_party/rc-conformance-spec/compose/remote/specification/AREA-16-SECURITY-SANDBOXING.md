# AREA-16: Security, Sandboxing & Resource Quotas Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player
**Normative Status:** Core Specification  

---

## 1. Overview

RemoteCompose documents are frequently transmitted over networks or IPC boundaries from remote or third-party servers. 

The player runtime operates as a secure execution sandbox. It must treat incoming documents as potentially untrusted input, strictly enforcing runtime operation quotas, memory ceilings, loop/recursion bounds, filesystem path traversal defenses, and isolated error containment to prevent host application crashes or device compromise.

---

## 2. Resource Quotas & Limits (`Limits.java`)

A conforming player must enforce the following hard resource boundaries:

| Resource Metric | Constant | Default Ceiling | Enforcement Action on Breach |
| :--- | :--- | :--- | :--- |
| **Max Operations per Frame** | `MAX_OP_COUNT` | `20,000` | Aborts current frame execution; logs warning; resumes on next frame. |
| **Default Buffer Size** | `BUFFER_SIZE` | `1,048,576 bytes` (1 MB) | Initial buffer allocation size. |
| **Max Table Size** | `MAX_TABLE_SIZE` | `1,000 entries` | Rejects headers or lookup tables exceeding entry count. |
| **Max Data Map Entries** | `MAX_DATA_MAP_SIZE` | `2,000 entries` | Clamps or rejects oversized data maps. |
| **Max State Variables** | `MAX_STATE_DATA` | `10,000 variables` | Prevents runaway dynamic state variable allocation. |
| **Max String Size** | `MAX_STRING_SIZE` | `4,000 bytes` | Rejects oversized UTF-8 strings. |
| **Max Image Dimension** | `MAX_IMAGE_DIMENSION` | `8,000 px` | Refuses to decode images with width or height $> 8000$. |
| **Max Bitmap Memory** | `MAX_BITMAP_MEMORY` | `20,971,520 bytes` (20 MB) | Rejects further bitmap allocations; substitutes placeholder. |
| **Max Image Header Size** | `MAX_IMAGE_HEADER_SIZE` | `10,000 bytes` | Rejects corrupt or oversized bitmap headers. |
| **Max RPN Expression Size** | `MAX_EXPRESSION_SIZE` | `32 operations` | Aborts oversized expressions. |
| **Max Shader Float Count** | `MAX_SHADER_FLOAT_COUNT` | `200 floats` | Rejects oversized shader uniform float arrays. |
| **Max Particle Array Size** | `MAX_PARTICLE_FLOAT_ARRAY_SIZE` | `2,000 floats` | Limits particle float array allocations. |
| **Max Particle Count** | `MAX_PARTICLE_COUNT` | `8,000 particles` | Clamps maximum active particles. |
| **Max Easing Points** | `MAX_EASING_LEN` | `200 values` | Limits knot points in custom spline easing curves. |
| **Max Touch Stops** | `MAX_TOUCH_STOPS` | `200 stops` | Clamps notch stop arrays in `TouchExpression`. |
| **Max SumTill Iterations** | `MAX_SUM_TILL_ITERATIONS` | `10,000 iterations` | Protects against runaway loop iteration counters. |
| **Max Function Arguments** | `MAX_FUNCTION_ARGUMENTS` | `32 arguments` | Clamps procedural subroutine parameter counts. |
| **Max Cache Entries** | `MAX_CACHE_ENTRIES` | `20 entries` | Evicts oldest entries in player-side LRU caches. |
| **Max Font Data Size** | `MAX_FONT_DATA` | `800,000 bytes` | Rejects embedded font resources exceeding 800 KB. |
| **Max Dash Intervals** | `MAX_DASH_INTERVALS` | `1,000 intervals` | Clamps path dash array lengths. |
| **Max Nesting Depth** | `MAX_NESTING_DEPTH` | `256 containers` | Aborts document parsing if container nesting depth exceeds 256. |
| **Max Click Reentrancy Depth** | `MAX_CLICK_REENTRANCY_DEPTH` | `128 recursive calls` | Throws `RuntimeException` to abort runaway recursive click cascades. |

### 2.1 Capability Gates
* `ENABLE_HAPTIC_FEEDBACK = true`: Enables sensory tactile pulses via host haptic actuators.
* `ENABLE_IMAGE_URLS = false`: Disallows loading bitmaps via arbitrary HTTP/HTTPS URLs by default.
* `ENABLE_IMAGE_FILES = false`: Disallows loading bitmaps from arbitrary local file system paths by default.
* `ENABLE_RUN_ACTION_HOST_ACTIONS = false`: Disallows invoking host actions directly from within untrusted `RunAction` sequences unless explicitly permitted by host security policy.

---

## 3. Filesystem & Cache Sandboxing

When persistent caching of macro templates is enabled (`saveMacro`):
* **Path Traversal Defense**: The player must strictly validate that the generated canonical file path resides within the designated internal storage directory:
  ```java
  String canonicalFolder = folder.getCanonicalPath();
  String canonicalFile = file.getCanonicalPath();
  if (!canonicalFile.startsWith(canonicalFolder + File.separator)) {
      throw new SecurityException("Path traversal detected in macro name");
  }
  ```
* Untrusted characters (e.g., `..`, `/`, `\`) in macro names must be sanitized or rejected.

---

## 4. Shader Sandboxing & Execution Safety

* **Deny-by-Default Policy**: All AGSL shaders are rejected unless the host explicitly assigns an approving `ShaderControl` instance.
* **Payload Verification**: Shaders exceeding uniform float limits (`MAX_SHADER_FLOAT_COUNT = 200`) or declaring excessive texture samples must be rejected.
* **Compilation Isolation**: GPU shader compilation errors are caught cleanly, preventing native graphics driver crashes.

---

## 5. Fault Isolation & Error Containment

A conforming player runtime must guarantee **Host Survivability**:

```
+-------------------------------------------------------------------------------+
| Host Application Process                                                      |
|                                                                               |
|   +-----------------------------------------------------------------------+   |
|   | RemoteComposePlayer Sandbox                                           |   |
|   |                                                                       |   |
|   |  [Corrupt Wire Bytes] -> MalformedOperationException trapped          |   |
|   |  [Divide by Zero]     -> Clamped to 0.0 without throwing              |   |
|   |  [Missing Asset ID]   -> Render placeholder; continue sibling draw    |   |
|   |  [OOM Protection]     -> Enforce MAX_BITMAP_MEMORY ceiling             |   |
|   +-----------------------------------------------------------------------+   |
|                                                                               |
|   Host Process continues running normally; UI degrades gracefully             |
+-------------------------------------------------------------------------------+
```

---

## 6. Conformance Requirements

1. **Infinite Loop Defense**: A document containing an infinite `LoopOperation` or recursive macro must be halted when reaching `MAX_OP_COUNT` without freezing the host main thread.
2. **Path Traversal Rejection**: Attempting to save a macro named `"../../evil"` must be blocked immediately without touching disk outside the macro directory.
3. **No Process Termination**: Under no circumstances may a malformed binary wire stream cause the host application process to crash with an unhandled exception.

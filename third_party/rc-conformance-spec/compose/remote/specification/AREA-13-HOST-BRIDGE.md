# AREA-13: Host Application Communication Bridge Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview

A RemoteCompose document does not operate in isolation; it lives within an embedding **Host Application** (e.g., an Android Activity, a system widget host, or a Compose surface). 

The Host Application Communication Bridge governs bidirectional data flow between the player runtime and the hosting environment:
* **Outbound**: Delivering user clicks, actions, and custom events to host listeners.
* **Inbound**: Injecting application data, system properties, and dynamic overrides into the document via `setUserLocal*` and `setSystemLocal*` APIs.
* **Extensibility**: Providing custom execution hooks through `CustomContext` / `setCustomSupport`.

---

## 2. Bidirectional Bridge Architecture

```
+-------------------------------------------------------------------------------+
|                             Host Application                                  |
|         (Android Activity, Composable, Widget Host, or System Service)        |
+-------------------------------------------------------------------------------+
       |                                                                ^
       | [Inbound State Overrides]            [Outbound Action Dispatch]|
       |  - setUserLocalString(name, value)    - onAction(id, metadata) |
       |  - setUserLocalFloat(name, value)     - onAction(name, value)  |
       |  - setUserLocalInt(name, value)                                |
       |  - setUserLocalColor(name, value)                              |
       |  - setUserLocalBitmap(name, bitmap)                            |
       |  - setSystemLocalString(name, val)                             |
       v                                                                |
+-------------------------------------------------------------------------------+
|                       RemoteCompose Player Runtime                            |
|                                                                               |
|   [State Injection Pipeline]                      [Action Dispatch Engine]    |
|   Updates USER:* / SYSTEM:* domains               Listens to ClickModifiers,  |
|   Marks reactive variables dirty                  packages action payloads,   |
|   Triggers measure/render pass                    flushes to host listeners   |
+-------------------------------------------------------------------------------+
```

---

## 3. Outbound Event Dispatching (Document to Host)

When user interactions trigger host-directed modifiers, the player packages and delivers events to registered host listeners.

### 3.1 Action Operations

| Opcode | Operation | Class | Payload | Player Dispatch Contract |
| :--- | :--- | :--- | :--- | :--- |
| `209` | `HOST_ACTION` | `HostActionOperation` | `actionId: INT` | Dispatches integer ID via `context.runAction(id, "")` to `IdActionCallbacks.onAction(id, "")`. |
| `210` | `HOST_NAMED_ACTION` | `HostNamedActionOperation` | `textId: INT` | Dispatches semantic action name via `context.runNamedAction(textId, value)` to `ActionCallback.onAction(name, value)`. |
| `216` | `HOST_ACTION_METADATA`| `HostActionMetadataOperation` | `actionId: INT, metadata: UTF8` | Dispatches integer ID paired with unstructured string payload to `IdActionCallbacks.onAction(id, metadata)`. |
| `236` | `RUN_ACTION` | `RunActionOperation` | `childOps: CONTAINER` | Sequences multiple local variable change operations (`ValueFloatChange`, `ValueIntegerChange`, etc.). |

### 3.2 Host Listener Interfaces
Host applications attach listeners via `RemoteComposePlayer`:
```java
// Numerical / ID-based actions
public interface IdActionCallbacks {
    void onAction(int id, @NonNull String metadata);
}
player.addIdActionListener(callback);

// Named semantic actions
public interface ActionCallback {
    void onAction(@NonNull String name, @Nullable Object value);
}
document.addActionCallback(callback);
```

### 3.3 Dispatch Rules & Security Invariants
1. **Thread Contract**: Action callbacks MUST be dispatched on the host application's **Main / UI Thread**.
2. **Re-entrancy Protection**: `CoreDocument` maintains `mClickReentrancyDepth`. If nested click triggers or chained host callbacks exceed `MAX_CLICK_REENTRANCY_DEPTH = 128`, playback throws a `RuntimeException("Maximum click re-entrancy depth exceeded")` to prevent recursion blowouts.
3. **RunAction Host Gating**: Per `Limits.ENABLE_RUN_ACTION_HOST_ACTIONS = false` (default), host action operations (`HostActionOperation`, `HostNamedActionOperation`, `HostActionMetadataOperation`) are disallowed inside `RunActionOperation` containers unless explicitly unlocked by host security configuration.

---

## 4. Inbound State Ingestion (Host to Document)

The host application dynamically injects and clears variables using domain-namespaced APIs on `RemoteComposePlayer`:

```java
// User domain ("USER:<name>")
public void setUserLocalString(String name, String content);
public void clearUserLocalString(String name);

public void setUserLocalInt(String name, int value);
public void clearUserLocalInt(String name);

public void setUserLocalColor(String name, int value);
public void clearUserLocalColor(String name);

public void setUserLocalFloat(String name, float value);
public void clearUserLocalFloat(String name);

public void setUserLocalBitmap(String name, Bitmap value);
public void clearUserLocalBitmap(String name);

// System domain ("SYSTEM:<name>")
public void setSystemLocalString(String name, String content);
public void clearSystemLocalString(String name);

// Generic namespaced domain
public void setLocalString(String domain, String name, String content);
public void clearLocalString(String domain, String name);
```

### 4.1 Domain Isolation
* **`USER:<name>`**: Application-specific business data (e.g., user profile name, unread message count, sensor data).
* **`SYSTEM:<name>`**: System-level properties (e.g., system theme, font scale, localization overrides).
* **Precedence Rule**: Host local overrides take absolute precedence over hardcoded default values defined in the document's wire payload.

### 4.2 Invalidation & Propagation
When an inbound update is applied:
1. `RemoteContext` checks if the incoming value differs from the active cached value.
2. If changed, the target variable is marked dirty.
3. The player invokes `requestLayout()` if dimensional/layout-affecting variables changed, or `invalidate()` for purely visual repaint updates.

---

## 5. Extensibility via CustomContext

To support platform-specific hardware extensions, custom draw shaders, or proprietary host views without modifying the core engine:
* The player exposes `player.setCustomSupport(@Nullable AndroidCustomContext androidCustomSupport)`.
* Custom paint operations and custom components query `context.getPaintContext().getCustomSupport()`.
* If no custom handler is attached, unknown or custom operations are safely ignored without breaking playback.

---

## 6. Conformance Requirements

1. **Payload Integrity**: String metadata passed to `HOST_ACTION_METADATA` must be delivered byte-for-byte unmodified to `IdActionCallbacks.onAction()`.
2. **Clear Reversion**: Calling `clearUserLocalString("name")` must immediately revert the variable's value back to the default string declared in the document's original `DATA_TEXT` operation.
3. **Re-entrancy Cap**: Recursive action dispatches exceeding depth 128 must be terminated with a `RuntimeException`.
4. **Thread Safety**: State updates submitted from background threads must be posted to the player's UI loop before the subsequent frame layout pass.

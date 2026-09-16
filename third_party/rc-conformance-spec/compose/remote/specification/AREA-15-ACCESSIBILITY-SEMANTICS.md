# AREA-15: Accessibility (a11y) & Semantics Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview

RemoteCompose is engineered to provide fully accessible experiences for users relying on screen readers (e.g., TalkBack) or assistive navigation services. 

Because RemoteCompose renders content via hardware-accelerated drawing passes rather than inflating individual native Android views for each leaf node, the player runtime synthesizes and maintains a **Virtual Accessibility Node Hierarchy** (`CoreDocumentAccessibility`, `RemoteComposeTouchHelper`) that reflects component layout, text, interactive affordances, and semantics.

---

## 2. Virtual Accessibility Architecture

```
+-------------------------------------------------------------------------------+
| RemoteCompose Layout Tree (CoreDocument)                                      |
| [RootLayoutComponent] -> [RowLayout] -> [Button Component with ClickModifier] |
+-------------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------------+
| Semantic Tree Synthesis (CoreDocumentAccessibility / AccessibleComponent)     |
| - Inspects components and modifiers                                           |
| - Extracts Content Descriptions (Opcode 103, 250)                             |
| - Assigns Roles: Button, Checkbox, Switch, Image, Tab, Carousel, etc.         |
| - Computes Screen Bounding Rects (local bounds transformed to screen space)   |
+-------------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------------+
| Virtual Viewport Bridge (RemoteComposeTouchHelper / ExploreByTouchHelper)     |
| - Exposes virtual view hierarchy to Android Accessibility Framework (TalkBack)|
| - Handles focus traversal & accessibility touch exploration                   |
| - Dispatches a11y actions: performClick, scrollForward, scrollBackward        |
+-------------------------------------------------------------------------------+
```

---

## 3. Semantic Attributes & Roles

### 3.1 AccessibleComponent Roles (`AccessibleComponent.Role`)

| Role Ordinal | Enum Constant | Description | TalkBack Announcement Behavior |
| :--- | :--- | :--- | :--- |
| `0` | `BUTTON` | Clickable button | "Button; Double tap to activate" |
| `1` | `CHECKBOX` | Two-state checkbox | Announces checked/unchecked status |
| `2` | `SWITCH` | Toggle switch | Announces on/off switch status |
| `3` | `RADIO_BUTTON` | Selection radio button | Announces radio button selection |
| `4` | `TAB` | Page navigation tab | Announces tab selection and index |
| `5` | `IMAGE` | Non-text visual element | Announces content description; identifies as image |
| `6` | `DROPDOWN_LIST`| Dropdown selector | Announces expandable dropdown affordance |
| `7` | `PICKER` | Value picker / spinner | Exposes increment/decrement accessibility actions |
| `8` | `CAROUSEL` | Paged horizontal carousel | Announces page items and scrolling affordances |
| `9` | `UNKNOWN` | Unspecified component | Falls back to default container semantics |

### 3.2 Semantic Merge Modes (`AccessibleComponent.Mode`)
* **`SET (0)`**: Sets or overrides semantics on this component node.
* **`CLEAR_AND_SET (1)`**: Clears all descendant semantics and sets this node's semantics (mirrors `Modifier.clearAndSetSemantics`).
* **`MERGE (2)`**: Merges child descendant semantic descriptions into this node (mirrors `Modifier.semantics(mergeDescendants = true)`).

### 3.3 CoreSemantics Operation (`ACCESSIBILITY_SEMANTICS`, Opcode 250)
Binds explicit accessibility semantics to a component via `CoreSemantics.java`:
* **Wire Payload**:
  * `contentDescriptionId: INT`: Text variable ID for content description (`declareId()`).
  * `role: BYTE`: Component role ordinal (`0..9`, or `-1` for null).
  * `textId: INT`: Text variable ID for announced text content.
  * `stateDescriptionId: INT`: Text variable ID for state description (e.g., "Expanded", "50%").
  * `mode: BYTE`: Merge mode ordinal (`0=SET, 1=CLEAR_AND_SET, 2=MERGE`).
  * `enabled: BOOLEAN`: Whether component is enabled.
  * `clickable: BOOLEAN`: Whether component responds to click actions.

---

## 4. Virtual Bounding Rect Calculation

For touch exploration to work correctly when the user drags a finger across the screen:
1. The player calculates the component's untransformed rectangle $[0, W] \times [0, H]$.
2. The component's accumulated transformation matrix (including parent offsets, matrix rotations, scales, and scroll offsets) is applied:
   $$\text{ScreenRect} = M_{\text{accumulated}} \times [0, W] \times [0, H]$$
3. The resulting screen-space axis-aligned bounding box is supplied to `AccessibilityNodeInfoCompat.setBoundsInScreen()`.

---

## 5. Assistive Action Dispatching

When a screen reader performs an assistive action:
* **`ACTION_CLICK`**:
  ```java
  player.performClick(document, component, metadata);
  ```
  Triggers the component's `onClick` modifier and executes associated host or state actions.
* **`ACTION_SCROLL`**:
  ```java
  player.scrollDirection(component, direction);
  ```
  Delegates directly to the component's `ScrollDelegate`.
* **`ACTION_SHOW_ON_SCREEN`**:
  Recursively scrolls enclosing scroll containers until the focused component is fully within the visible viewport.

---

## 6. Conformance Requirements

1. **Hidden Node Exclusion**: Components marked `Visibility.GONE` or `Visibility.INVISIBLE` must be completely omitted from the accessibility virtual tree.
2. **Clickable Affordance Guarantee**: Every component bearing a `ClickModifierOperation` or `CoreSemantics.clickable == true` must be focusable by accessibility services and support `performClick()`.
3. **Dynamic Label Updates**: When a dynamic text variable bound to a content description changes, the player must issue an `AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED` event to notify screen readers.

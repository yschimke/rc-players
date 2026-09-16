# AREA-02: Core Operations & Opcode Catalog Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview

The RemoteCompose protocol represents all graphics, text, layout structures, reactive state variables, and interactions as discrete operations identified by an 8-bit unsigned Opcode (`0` to `255`). 

This document defines the normative catalog of RemoteCompose operations, their binary opcode identifiers, parameter payloads, execution phases, and side-effect contracts.

---

## 2. Operation Execution Phase Model

A conforming RemoteCompose Player must execute each operation within its designated lifecycle phase:

1. **Phase 1: Ingestion & Registration (Parse Time)**:
   * Allocates resources, declares variables, defines templates/macros, registers string and bitmap tables.
   * Operations: `HEADER (0)`, `DATA_*`, `NAMED_VARIABLE`, `MACRO_DEFINE`, `FUNCTION_DEFINE`.
2. **Phase 2: Materialization & Tree Assembly**:
   * Expands Loom macros, resolves ID remapping, nests components into hierarchical tree structures.
   * Operations: `MACRO_CALL`, `COMPONENT_START`, `CONTAINER_END`, `LAYOUT_*`.
3. **Phase 3: Measurement & Constraint Pass**:
   * Calculates intrinsic sizes, evaluates layout modifiers, populates `MeasurePass`.
   * Operations: `MODIFIER_WIDTH*`, `MODIFIER_HEIGHT*`, `MODIFIER_PADDING`, `LAYOUT_COMPUTE`, `TEXT_MEASURE`.
4. **Phase 4: State Evaluation & Frame Update**:
   * Evaluates dirty RPN expressions, updates simulation physics, handles clock ticks.
   * Operations: `ANIMATED_FLOAT`, `INTEGER_EXPRESSION`, `PARTICLE_LOOP`, `TOUCH_EXPRESSION`.
5. **Phase 5: Paint & Rasterization**:
   * Applies matrix transforms, sets paint styles, composites graphics layers, and issues canvas draw calls.
   * Operations: `PAINT_VALUES`, `MATRIX_*`, `DRAW_*`, `CLIP_*`.

---

## 3. Normative Opcode Catalog

### 3.1 Protocol & Document Lifecycle

| Opcode | Constant | Payload Fields | Phase | Description |
| :--- | :--- | :--- | :--- | :--- |
| `0` | `HEADER` | `major:INT, minor:INT, patch:INT, propCount:INT, [tag:SHORT, len:SHORT, val][]` | Parse | Root document metadata & capability flags |
| `2` | `COMPONENT_START` | `componentId:INT, animId:INT, x:FLOAT, y:FLOAT, w:FLOAT, h:FLOAT` | Layout | Structural container opening token |
| `63` | `THEME` | `themeMode:INT (0=Unspecified, 1=Light, 2=Dark)` | Parse | Global theme mode selector |
| `64` | `CLICK_AREA` | `id:INT, left:FLOAT, top:FLOAT, right:FLOAT, bottom:FLOAT` | Layout | Expanded hit-test target zone |
| `65` | `ROOT_CONTENT_BEHAVIOR` | `scroll:BYTE, alignment:BYTE, sizing:BYTE` | Layout | Viewport fitting and scrolling policies |
| `103` | `ROOT_CONTENT_DESCRIPTION` | `textId:INT` | Paint | Accessible content description for root |
| `150` | `COMPONENT_VALUE` | `componentId:INT, valueId:INT, propertyType:INT` | State | Injects dynamic property into component |
| `185` | `REM` | `text:UTF8` | Parse | Non-executable developer comment |
| `191` | `WAKE_IN` | `delayMillis:FLOAT` | State | Requests timed player wake/invalidation |
| `241` | `SKIP` | `conditionType:SHORT, apiLevelOrVal:INT, skipByteCount:INT` | Parse | Conditionally skips $N$ wire bytes based on API level |
| `250` | `ACCESSIBILITY_SEMANTICS`| `contentDescId:INT, role:BYTE, textId:INT, stateDescId:INT, mode:BYTE, enabled:BOOL, clickable:BOOL` | Paint | Semantic accessibility node attributes |

---

### 3.2 2D Canvas & Vector Graphics

| Opcode | Constant | Payload Fields | Phase | Description |
| :--- | :--- | :--- | :--- | :--- |
| `38` | `CLIP_PATH` | `pathId:INT` | Paint | Clips canvas to vector path |
| `39` | `CLIP_RECT` | `left:FLOAT, top:FLOAT, right:FLOAT, bottom:FLOAT` | Paint | Clips canvas to rectangle |
| `40` | `PAINT_VALUES` | `paintBundlePayload:BUFFER` | Paint | Updates painter stroke, fill, color, and filters |
| `42` | `DRAW_RECT` | `left:FLOAT, top:FLOAT, right:FLOAT, bottom:FLOAT` | Paint | Draws primitive rectangle |
| `46` | `DRAW_CIRCLE` | `centerX:FLOAT, centerY:FLOAT, radius:FLOAT` | Paint | Draws circle |
| `47` | `DRAW_LINE` | `x1:FLOAT, y1:FLOAT, x2:FLOAT, y2:FLOAT` | Paint | Draws straight line |
| `51` | `DRAW_ROUND_RECT` | `left:FLOAT, top:FLOAT, right:FLOAT, bottom:FLOAT, rx:FLOAT, ry:FLOAT` | Paint | Draws rounded rectangle |
| `52` | `DRAW_SECTOR` | `cx:FLOAT, cy:FLOAT, r:FLOAT, startAngle:FLOAT, sweepAngle:FLOAT` | Paint | Draws pie slice sector |
| `56` | `DRAW_OVAL` | `left:FLOAT, top:FLOAT, right:FLOAT, bottom:FLOAT` | Paint | Draws ellipse |
| `123` | `DATA_PATH` | `pathId:INT, commands:BUFFER` | Parse | Declares static vector path geometry |
| `124` | `DRAW_PATH` | `pathId:INT` | Paint | Renders vector path with current paint |
| `125` | `DRAW_TWEEN_PATH` | `path1Id:INT, path2Id:INT, tween:FLOAT` | Paint | Morphs and renders between two paths |
| `139` | `DRAW_CONTENT` | *(none)* | Paint | Content draw hook inside custom canvas |
| `152` | `DRAW_ARC` | `cx:FLOAT, cy:FLOAT, r:FLOAT, startAngle:FLOAT, sweepAngle:FLOAT` | Paint | Draws stroke arc perimeter |
| `158` | `PATH_TWEEN` | `dstPathId:INT, src1Id:INT, src2Id:INT, tween:FLOAT` | State | Interpolates path geometry into destination |
| `159` | `PATH_CREATE` | `pathId:INT` | Parse | Allocates dynamic mutable path |
| `160` | `PATH_ADD` | `pathId:INT, segmentType:BYTE, params:FLOAT[]` | State | Appends bezier/line segments to path |
| `173` | `CANVAS_OPERATIONS` | `operations:CONTAINER` | Layout | Standalone canvas drawing operation block |
| `174` | `MODIFIER_DRAW_CONTENT` | *(none)* | Paint | Modifier ordering content rendering hook |
| `175` | `PATH_COMBINE` | `dstId:INT, op:BYTE, src1Id:INT, src2Id:INT` | State | Boolean path operations (union, diff, xor) |
| `190` | `DRAW_TO_BITMAP` | `bitmapId:INT, childOps:CONTAINER` | Paint | Offscreen render-to-texture pass |
| `193` | `PATH_EXPRESSION` | `pathId:INT, tMin:FLOAT, tMax:FLOAT, xEq:RPN, yEq:RPN` | State | Algorithmic parametric path generator |

---

### 3.3 Transformation Matrices

| Opcode | Constant | Payload Fields | Phase | Description |
| :--- | :--- | :--- | :--- | :--- |
| `126` | `MATRIX_SCALE` | `scaleX:FLOAT, scaleY:FLOAT, centerX:FLOAT, centerY:FLOAT` | Paint | Scales current coordinate space |
| `127` | `MATRIX_TRANSLATE` | `dx:FLOAT, dy:FLOAT` | Paint | Translates coordinate space |
| `128` | `MATRIX_SKEW` | `skewX:FLOAT, skewY:FLOAT` | Paint | Applies skew transformation |
| `129` | `MATRIX_ROTATE` | `angleDegrees:FLOAT, pivotX:FLOAT, pivotY:FLOAT` | Paint | Rotates coordinate space |
| `130` | `MATRIX_SAVE` | *(none)* | Paint | Pushes canvas matrix & clip to stack |
| `131` | `MATRIX_RESTORE` | *(none)* | Paint | Pops canvas matrix & clip from stack |
| `132` | `MATRIX_SET` | `matrixId:INT` | Paint | Replaces canvas matrix with matrix variable |
| `181` | `MATRIX_FROM_PATH` | `matrixId:INT, pathId:INT, distance:FLOAT, flags:INT` | State | Tangent/normal matrix along vector path |
| `186` | `MATRIX_CONSTANT` | `matrixId:INT, values:FLOAT[9]` | Parse | Declares constant 3x3 affine matrix |
| `187` | `MATRIX_EXPRESSION` | `matrixId:INT, rpnTransformOps:BUFFER` | State | Dynamic calculated matrix |
| `188` | `MATRIX_VECTOR_MATH`| `dstVectorId:INT, matrixId:INT, srcVectorId:INT` | State | Transforms 2D vector by matrix |

---

### 3.4 Text Subsystem

| Opcode | Constant | Payload Fields | Phase | Description |
| :--- | :--- | :--- | :--- | :--- |
| `43` | `DRAW_TEXT_RUN` | `textId:INT, start:INT, end:INT, x:FLOAT, y:FLOAT, rtl:BOOLEAN` | Paint | Draws raw text run |
| `48` | `DRAW_BITMAP_FONT_TEXT_RUN` | `fontId:INT, textId:INT, x:FLOAT, y:FLOAT` | Paint | Renders pre-rendered bitmap font |
| `49` | `DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH` | `fontId:INT, textId:INT, pathId:INT, offset:FLOAT` | Paint | Bitmap font rendered along path |
| `53` | `DRAW_TEXT_ON_PATH` | `textId:INT, pathId:INT, hOffset:FLOAT, vOffset:FLOAT` | Paint | TrueType text warped along path |
| `57` | `DRAW_TEXT_ON_CIRCLE` | `textId:INT, cx:FLOAT, cy:FLOAT, radius:FLOAT, angle:FLOAT` | Paint | Curved text along circular arc |
| `102` | `DATA_TEXT` | `textId:INT, value:UTF8` | Parse | Declares static text string |
| `133` | `DRAW_TEXT_ANCHOR` | `textId:INT, x:FLOAT, y:FLOAT, panX:FLOAT, panY:FLOAT, flags:INT` | Paint | Text anchored by relative origin |
| `135` | `TEXT_FROM_FLOAT` | `textId:INT, floatId:INT, minInt:INT, maxDec:INT, flags:INT` | State | Dynamic float formatting to string |
| `136` | `TEXT_MERGE` | `dstTextId:INT, srcTextId1:INT, srcTextId2:INT` | State | String concatenation |
| `151` | `TEXT_LOOKUP` | `dstTextId:INT, indexId:INT, tableId:INT` | State | String array index lookup (float/ID index) |
| `153` | `TEXT_LOOKUP_INT` | `dstTextId:INT, indexId:INT, tableId:INT` | State | String array lookup using integer index |
| `155` | `TEXT_MEASURE` | `dstFloatId:INT, textId:INT, measureType:INT` | Measure | Measures text width/bounds |
| `156` | `TEXT_LENGTH` | `dstIntId:INT, textId:INT` | State | Returns character length of text |
| `167` | `DATA_BITMAP_FONT`| `fontId:INT, atlasBitmapId:INT, glyphTable:BUFFER` | Parse | Declares bitmap font glyph atlas |
| `170` | `ATTRIBUTE_TEXT` | `attributeId:INT, textId:INT` | State | Binds dynamic text attribute |
| `182` | `TEXT_SUBTEXT` | `dstTextId:INT, srcTextId:INT, start:INT, length:INT` | State | Substring extraction |
| `183` | `BITMAP_TEXT_MEASURE` | `dstFloatId:INT, fontId:INT, textId:INT` | Measure | Measures bitmap font text metrics |
| `184` | `DRAW_BITMAP_TEXT_ANCHORED` | `fontId:INT, textId:INT, x:FLOAT, y:FLOAT, panX:FLOAT, panY:FLOAT` | Paint | Anchored bitmap text |
| `189` | `DATA_FONT` | `fontId:INT, fontBytes:BUFFER` | Parse | Embedded TrueType/OpenType resource |
| `199` | `TEXT_TRANSFORM` | `dstTextId:INT, srcTextId:INT, transformType:BYTE` | State | Case conversion (upper, lower, cap) |
| `208` | `LAYOUT_TEXT` | `textId:INT, animId:INT, x:FLOAT, y:FLOAT, w:FLOAT, h:FLOAT` | Layout | Basic layout text container |
| `239` | `CORE_TEXT` | `id:INT, animId:INT, textId:INT, styleId:INT` | Layout | Full-featured multi-line text layout |
| `242` | `TEXT_STYLE` | `id:INT, paramCount:INT, [tag:BYTE, val][]` | Parse | Text styling via CommandParameters |

---

### 3.5 Bitmaps & Assets

| Opcode | Constant | Payload Fields | Phase | Description |
| :--- | :--- | :--- | :--- | :--- |
| `4` | `LOAD_BITMAP` | `bitmapId:INT, uri:UTF8` | Parse | Asynchronous external bitmap loading |
| `44` | `DRAW_BITMAP` | `bitmapId:INT, left:FLOAT, top:FLOAT, right:FLOAT, bottom:FLOAT` | Paint | Renders bitmap |
| `45` | `DATA_SHADER` | `shaderId:INT, shaderSource:UTF8` | Parse | AGSL runtime shader source |
| `66` | `DRAW_BITMAP_INT` | `bitmapId:INT, srcL:INT, srcT:INT, srcR:INT, srcB:INT, dstL:FLOAT, dstT:FLOAT, dstR:FLOAT, dstB:FLOAT` | Paint | Sub-rect blit operation |
| `101` | `DATA_BITMAP` | `bitmapId:INT, width:SHORT, height:SHORT, format:BYTE, data:BUFFER` | Parse | Embedded raster image payload |
| `149` | `DRAW_BITMAP_SCALED` | `bitmapId:INT, scaleType:BYTE, dstL:FLOAT, dstT:FLOAT, dstR:FLOAT, dstB:FLOAT` | Paint | Scale-to-fit/crop bitmap drawing |
| `171` | `ATTRIBUTE_IMAGE` | `attributeId:INT, imageId:INT` | State | Dynamic image attribute binding |

---

### 3.6 Expressions & Reactive State

| Opcode | Constant | Payload Fields | Phase | Description |
| :--- | :--- | :--- | :--- | :--- |
| `80` | `DATA_FLOAT` | `floatId:INT, value:FLOAT` | Parse | Declares constant float variable |
| `81` | `ANIMATED_FLOAT` | `floatId:INT, rpnExpr:FLOAT[], animSpec:FLOAT[]` | State | Dynamic mathematical expression |
| `134` | `COLOR_EXPRESSIONS`| `colorId:INT, rpnBytes:BUFFER` | State | Dynamic ARGB color calculation |
| `137` | `NAMED_VARIABLE` | `type:BYTE, varId:INT, name:UTF8` | Parse | Declares host-accessible named variable |
| `138` | `COLOR_CONSTANT` | `colorId:INT, argbColor:INT` | Parse | Declares constant 32-bit ARGB color |
| `140` | `DATA_INT` | `intId:INT, value:INT` | Parse | Declares constant integer variable |
| `143` | `DATA_BOOLEAN` | `boolId:INT, value:BOOLEAN` | Parse | Declares constant boolean |
| `144` | `INTEGER_EXPRESSION`| `intId:INT, mask:INT, rpnOps:INT[]` | State | Dynamic integer bitmask expression |
| `145` | `ID_MAP` | `mapId:INT, count:INT, keys:INT[], values:INT[]` | Parse | Key-value integer lookup dictionary |
| `146` | `ID_LIST` | `listId:INT, count:INT, items:INT[]` | Parse | Immutable array of IDs |
| `147` | `FLOAT_LIST` | `listId:INT, count:INT, items:FLOAT[]` | Parse | Immutable array of floats |
| `148` | `DATA_LONG` | `longId:INT, value:LONG` | Parse | Declares 64-bit integer |
| `154` | `DATA_MAP_LOOKUP` | `dstId:INT, mapId:INT, keyId:INT` | State | Dictionary lookup |
| `157` | `TOUCH_EXPRESSION` | `varId:INT, min:FLOAT, max:FLOAT, mode:BYTE, rpn:BUFFER` | State | Input touch physics mapping |
| `172` | `ATTRIBUTE_TIME` | `attributeId:INT, timeValue:FLOAT` | State | Dynamic time attribute binding |
| `178` | `CONDITIONAL_OPERATIONS` | `comparisonType:BYTE, varA:FLOAT, varB:FLOAT, ops:CONTAINER` | Layout | Conditional if-branch block (EQ, NEQ, LT, LTE, GT, GTE, CHANGED) |
| `180` | `ATTRIBUTE_COLOR` | `attributeId:INT, colorId:INT` | State | Dynamic color attribute binding |
| `192` | `ID_LOOKUP` | `dstId:INT, indexId:INT, listId:INT` | State | Resolves ID by index from list |
| `196` | `COLOR_THEME` | `themeMode:INT, lightColor:INT, darkColor:INT` | State | Adaptive theme color mapping |
| `197` | `DYNAMIC_FLOAT_LIST` | `listId:INT, initialCapacity:INT` | Parse | Mutable dynamic array of floats |
| `198` | `UPDATE_DYNAMIC_FLOAT_LIST` | `listId:INT, index:INT, value:FLOAT` | State | In-place dynamic list update |

---

### 3.7 Audio, Haptics & Feedback

| Opcode | Constant | Payload Fields | Phase | Description |
| :--- | :--- | :--- | :--- | :--- |
| `141` | `PLAY_SOUND` | `soundId:INT, volume:FLOAT` | State | Triggers playback of sound asset |
| `169` | `DATA_SOUND` | `soundId:INT, audioData:BUFFER` | Parse | Declares embedded audio clip |
| `177` | `HAPTIC_FEEDBACK` | `feedbackType:INT` | State | Generates device haptic vibration |
| `179` | `DEBUG_MESSAGE` | `messageTextId:INT, flags:INT` | State | Logs runtime diagnostic message |
| `206` | `SOUND_EXPRESSION` | `soundId:INT, rpnExpr:BUFFER` | State | Procedural audio synthesis formula |

---

### 3.8 Particle Systems & Physics

| Opcode | Constant | Payload Fields | Phase | Description |
| :--- | :--- | :--- | :--- | :--- |
| `161` | `PARTICLE_DEFINE` | `id:INT, varIds:INT[], equations:FLOAT[][], count:INT` | Parse | Declares particle system and init formulas |
| `162` | `PARTICLE_PROCESS` | `systemId:INT` | State | Advances particle simulation step |
| `163` | `PARTICLE_LOOP` | `systemId:INT, updateEquations:FLOAT[][]` | State | Numerical integration equations per frame |
| `164` | `IMPULSE_START` | `impulseId:INT, force:FLOAT, duration:FLOAT` | State | Initiates mechanical force impulse |
| `165` | `IMPULSE_PROCESS` | `impulseId:INT` | State | Applies impulse decay calculation |
| `194` | `PARTICLE_COMPARE` | `systemId:INT, equations1:FLOAT[][], equations2:FLOAT[][]` | State | Particle boundary and lifecycle conditions |

---

### 3.9 Loom Metaprogramming & Procedural Functions

| Opcode | Constant | Payload Fields | Phase | Description |
| :--- | :--- | :--- | :--- | :--- |
| `142` | `REFERENCED_OPERATIONS` | `blockId:INT, operations:CONTAINER` | Parse | Reusable static operations block |
| `166` | `FUNCTION_CALL` | `funcId:INT, argCount:INT, args:INT[]` | State | Calls procedural float subroutine |
| `168` | `FUNCTION_DEFINE` | `funcId:INT, paramCount:INT, body:BUFFER` | Parse | Declares procedural subroutine |
| `215` | `LOOP_START` | `loopCountId:INT, indexVarId:INT, body:CONTAINER` | Layout | Repeats block $N$ times |
| `244` | `MACRO_FOR_EACH` | `collectionId:INT, localItemId:INT, body:CONTAINER` | Materialize | Expands template across collection |
| `245` | `INCLUDE_REFERENCED_OPERATIONS` | `blockId:INT` | Materialize | Inlines referenced operations block |
| `246` | `MACRO_DEFINE` | `name:UTF8, paramCount:INT, paramIds:INT[], body:BUFFER` | Parse | Defines Loom template macro |
| `247` | `MACRO_CALL` | `name:UTF8, argCount:INT, argIds:INT[], blocks:CONTAINER[]` | Materialize | Inflates Loom template macro |
| `248` | `MACRO_ARGUMENT` | `paramId:INT` | Materialize | Placeholder for slot block argument |
| `249` | `MACRO_BLOCK` | `slotName:UTF8, operations:CONTAINER` | Materialize | Concrete slot argument payload |

---

### 3.10 Layout Containers & Modifiers

| Opcode | Constant | Payload Fields | Phase | Description |
| :--- | :--- | :--- | :--- | :--- |
| `14` | `ANIMATION_SPEC` | `motionDuration:FLOAT, visDuration:FLOAT, enterAnim:INT, exitAnim:INT, easing:INT` | Layout | Configures bounds layout animation |
| `16` | `MODIFIER_WIDTH` | `type:BYTE, value:FLOAT` | Measure | Sets width policy (Fixed, Wrap, Fill, Weight) |
| `54` | `MODIFIER_ROUNDED_CLIP_RECT` | `rx:FLOAT, ry:FLOAT` | Paint | Clips component to rounded corners |
| `55` | `MODIFIER_BACKGROUND` | `colorId:INT, shapeType:BYTE` | Paint | Paints component background shape |
| `58` | `MODIFIER_PADDING` | `left:FLOAT, top:FLOAT, right:FLOAT, bottom:FLOAT` | Measure | Insets component layout box |
| `59` | `MODIFIER_CLICK` | `clickActionId:INT` | Layout | Single-click event trigger |
| `67` | `MODIFIER_HEIGHT` | `type:BYTE, value:FLOAT` | Measure | Sets height policy |
| `83` | `MODIFIER_MULTI_CLICK` | `actionId:INT, requiredClicks:INT, timeoutMs:INT` | Layout | Double/triple-click event trigger |
| `93` | `LAYOUT_CUSTOM` | `id:INT, animId:INT, x:FLOAT, y:FLOAT, w:FLOAT, h:FLOAT` | Layout | Custom layout container |
| `107` | `MODIFIER_BORDER` | `width:FLOAT, colorId:INT, shapeType:BYTE, cornerRadius:FLOAT` | Paint | Stroked perimeter border |
| `108` | `MODIFIER_CLIP_RECT`| *(none)* | Paint | Clips rendering to component bounds |
| `176` | `LAYOUT_FIT_BOX` | *(same as LayoutManager)* | Layout | Selector container picking first fitting child |
| `200` | `LAYOUT_ROOT` | `rootId:INT, animId:INT, width:FLOAT, height:FLOAT` | Layout | Root layout container |
| `201` | `LAYOUT_CONTENT` | *(none)* | Layout | Encapsulates children of a LayoutComponent |
| `202` | `LAYOUT_BOX` | *(same as LayoutManager)* | Layout | Overlays children at same spatial location |
| `203` | `LAYOUT_ROW` | `hArrangement:INT, vAlignment:INT, spacedBy:FLOAT` | Layout | Horizontal linear layout manager |
| `204` | `LAYOUT_COLUMN` | `vArrangement:INT, hAlignment:INT, spacedBy:FLOAT` | Layout | Vertical linear layout manager |
| `205` | `LAYOUT_CANVAS` | *(none)* | Layout | 2D freeform drawing canvas |
| `207` | `LAYOUT_CANVAS_CONTENT`| *(none)* | Layout | Encapsulates canvas drawing children |
| `209` | `HOST_ACTION` | `actionId:INT` | State | Emits host action event upon execution |
| `210` | `HOST_NAMED_ACTION` | `actionName:UTF8` | State | Emits named string event to host |
| `211` | `MODIFIER_VISIBILITY` | `visibilityId:INT` | Layout | Binds dynamic visibility (`VISIBLE`, `INVISIBLE`, `GONE`) |
| `212` | `VALUE_INTEGER_CHANGE_ACTION` | `intId:INT, newValue:INT` | State | Mutates integer on event |
| `213` | `VALUE_STRING_CHANGE_ACTION` | `textId:INT, newText:UTF8` | State | Mutates string on event |
| `214` | `CONTAINER_END` | *(none)* | Layout | Terminates component/container scope |
| `216` | `HOST_METADATA_ACTION` | `actionId:INT, metadata:UTF8` | State | Emits action with metadata payload |
| `217` | `LAYOUT_STATE` | `stateIndexId:INT` | Layout | Multi-branch state switching layout |
| `218` | `VALUE_INTEGER_EXPRESSION_CHANGE_ACTION` | `intId:INT, rpnExpr:BUFFER` | State | Mutates integer via expression |
| `219` | `MODIFIER_TOUCH_DOWN` | `actionId:INT` | Layout | Event fired on pointer initial contact |
| `220` | `MODIFIER_TOUCH_UP` | `actionId:INT` | Layout | Event fired on pointer release |
| `221` | `MODIFIER_OFFSET` | `dx:FLOAT, dy:FLOAT` | Layout | Visual rendering offset |
| `222` | `VALUE_FLOAT_CHANGE_ACTION` | `floatId:INT, newValue:FLOAT` | State | Mutates float variable on event |
| `223` | `MODIFIER_ZINDEX` | `zIndex:FLOAT` | Paint | Controls sibling painting and hit-test order |
| `224` | `MODIFIER_GRAPHICS_LAYER` | `alpha:FLOAT, scaleX:FLOAT, scaleY:FLOAT, rotZ:FLOAT, ...` | Paint | Hardware compositing layer |
| `225` | `MODIFIER_TOUCH_CANCEL`| `actionId:INT` | Layout | Event fired when touch is intercepted |
| `226` | `MODIFIER_SCROLL` | `orientation:BYTE, scrollOffsetId:INT` | Layout | Enables scrolling with physics delegate |
| `227` | `VALUE_FLOAT_EXPRESSION_CHANGE_ACTION` | `floatId:INT, rpnExpr:BUFFER` | State | Mutates float via expression |
| `228` | `MODIFIER_MARQUEE` | `speed:FLOAT, delayMs:INT, iterations:INT` | Paint | Animated horizontal ticker |
| `229` | `MODIFIER_RIPPLE` | `colorId:INT, bounded:BOOLEAN` | Paint | Interactive touch ripple effect |
| `230` | `LAYOUT_COLLAPSIBLE_ROW` | `hArrangement:INT, vAlignment:INT, spacedBy:FLOAT` | Layout | Row layout dropping non-fitting children |
| `231` | `MODIFIER_WIDTH_IN` | `min:FLOAT, max:FLOAT` | Measure | Clamps width between min and max |
| `232` | `MODIFIER_HEIGHT_IN` | `min:FLOAT, max:FLOAT` | Measure | Clamps height between min and max |
| `233` | `LAYOUT_COLLAPSIBLE_COLUMN` | `vArrangement:INT, hAlignment:INT, spacedBy:FLOAT` | Layout | Column layout dropping non-fitting children |
| `234` | `LAYOUT_IMAGE` | `imageId:INT, scaleType:INT` | Layout | Resizable layout image component |
| `235` | `MODIFIER_COLLAPSIBLE_PRIORITY` | `priority:INT` | Measure | Priority for collapsible dropping order |
| `236` | `RUN_ACTION` | `actionOps:CONTAINER` | State | Executes nested action operations |
| `237` | `MODIFIER_ALIGN_BY` | `baselineId:INT` | Measure | Baseline alignment guide for Row/Column |
| `238` | `LAYOUT_COMPUTE` | `computeType:BYTE, rpnExpr:BUFFER` | Measure | Dynamically computes bounds via expression |
| `240` | `LAYOUT_FLOW` | `lineSpacing:FLOAT, itemSpacing:FLOAT` | Layout | Multi-line wrapping layout |
| `243` | `MODIFIER_DIMENSION_CONSTRAINTS` | `constraintType:INT, min:FLOAT, max:FLOAT` | Measure | Required horizontal/vertical constraints |

---

## 4. Conformance Rules

1. **Unknown Opcode Skipping**: A conforming player encountering an unlisted opcode with declared length MUST skip the payload without corrupting downstream alignment.
2. **Missing Terminator Detection**: If an open container (`ComponentStart`, `Layout*`) is not closed with a matching `ContainerEnd` before stream end, the player must raise a `MalformedDocumentException`.
3. **Opcode Dispatch Invariant**: All operations classified as `Phase: Paint` MUST NOT mutate document state variables or re-trigger layout invalidations during painting.

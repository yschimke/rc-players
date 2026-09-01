# Remote Compose document composition

## Decision

Composition is a capability of the common Compose Multiplatform player. It is not a Wasm protocol
and it does not require a second browser-only renderer.

An outer Remote Compose document owns the layout. An AndroidX `LAYOUT_CUSTOM` operation marks an
extension point and names a host renderer through its config text. The host renderer runs in the
same Compose tree and may render:

- ordinary Compose UI for a platform control;
- application content for a named slot; or
- another `RcComposePlayer` for a child Remote Compose document.

This is useful on every CMP target: Android and iOS can embed native capabilities, desktop can host
application widgets, and Wasm can load child documents without inventing a browser-specific wire
operation.

## Composite documents and slots

The wire document remains independently decodable and cacheable. A composite package or UI-builder
manifest maps stable keys to child documents; the outer document refers to those keys through
custom properties. Resolving, loading and caching a child is a host responsibility, not part of the
Remote Compose binary codec.

A named slot is the same mechanism with a different registry entry. For example, `slot:hero` can be
filled by application Compose content while `rc:document` resolves a child document key. This keeps
layout constraints in the outer document and avoids encoding executable host behavior in a UI
Builder manifest.

Custom component names should be namespaced (`slot:`, `rc:`, or an application domain) so documents
can be composed without accidental registry collisions. `composeSupportReport` accepts the names
available in a host and rejects missing config text or an unregistered renderer before playback.

## State and top-level ownership

Every `RcComposePlayer` instance owns an independent `RcPlayerState`. Therefore two placements of
the same child document do not accidentally share animations, variables, focus, scroll positions or
component geometry. Compose identity determines the lifetime of a nested instance; a host that
reorders children should use a stable `key` around each child.

The outer host remains the owner of top-level policy:

| Concern | Default for a child | How to share deliberately |
| --- | --- | --- |
| RC variables and animation state | Isolated per player instance | Map custom properties into the child's named values |
| Child output | Isolated | Map child events or named values to declared float/text return channels |
| Theme | Explicit player argument | Pass the resolved host theme to each child |
| Fonts and system colours | Explicit player arguments | Capture and pass the same host services |
| Host events, navigation and analytics | Explicit event sink | Route child events through the outer host with an instance path |
| Document loading and cache policy | Host-owned | Resolve manifest keys before or while rendering the custom component |

The custom property object resolves float and text references against live parent state. A renderer
can write only to a return channel declared by the document. That makes the boundary inspectable and
prevents an embedded child from mutating arbitrary parent variables.

## Follow-up capabilities

The custom-component seam is the minimum interoperable primitive. A UI Builder composite format can
be added above it without changing the player wire model. Useful next layers are:

1. a renderer-neutral composite manifest with document keys, hashes and slot contracts;
2. a child-document resolver with loading, error and fallback content;
3. typed helpers that bridge custom properties to child named values and events back to return
   channels;
4. stable instance paths for tracing, accessibility diagnostics and event attribution; and
5. lifecycle limits for nesting depth, total decoded bytes and concurrent child loads.

These belong in shared CMP or renderer-neutral modules. The Wasm host should only adapt browser
loading and JavaScript configuration to those common contracts.

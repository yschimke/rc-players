# Prototype architecture notes

## Recommended split

```text
Kotlin authoring app
  └─ remote-creation-compose
       ├─ Remote Apple components (this prototype)
       └─ remote-material3 (optional, sibling vocabulary)
            ↓ capture
         .rc bytes
            ↓
SwiftUI host → RemoteComposePlayerView → CMP player
```

Most of the useful API belongs above the wire format. `RemoteAppleButton`, `RemoteAppleToggle`, and
their peers are Kotlin composables which record operations; they are not UI views at runtime. This
keeps them usable with every player and makes their behavior testable before an Apple host exists.

SwiftUI should initially wrap only the player boundary. The repository already has that wrapper:
`RemoteComposePlayerView` accepts `Data`, forwards typed host events, and exposes named state through
`RemoteComposePlayerController`. Adding another SwiftUI view per remote component would imply that
the document contains native child views, which it does not, and would split authoring between two
languages.

## Relationship to `remote-material3`

The Apple set should be a sibling of Wear Remote Material 3, not a skin over it:

- both can share `Action`, `RemoteModifier`, `RemoteString`, and mutable remote state;
- both should emit standard operations rather than require player changes;
- an application can capture components from both sets in one document; but
- Wear Material 3 encodes watch-specific minimum sizes, typography, shapes, and component structure.
  Wrapping `RemoteButton` and overriding colors would inherit those decisions and produce something
  Apple-colored rather than Apple-like.

A small neutral layer may become worthwhile after a second implementation proves the repeated
mechanics. Likely candidates are enabled-action handling, control semantics, content-color
provision, and state-derived colors. Creating that abstraction now would mostly mirror unstable
alpha APIs from `remote-creation-compose`.

## Swift-native authoring later

A Swift result-builder DSL could eventually write `.rc` bytes directly, but it is a separate project
from SwiftUI integration. It would need to own operation encoding, expression/state references,
layout scopes, capability profiles, and compatibility tests against the AndroidX writer. A safer
first experiment would expose a few document factories from Kotlin/Native and return `Data`; this
reuses the Kotlin component vocabulary but should wait until Kotlin/Native can consume the AndroidX
creation stack (the current creation artifacts are Android/JVM AARs).

## Questions this prototype is meant to answer

1. Can Apple control geometry be expressed with today's standard operations? Yes for this first
   button/toggle/progress/section slice.
2. Can interaction remain remote? Yes: the toggle records `valueChange`, and buttons emit host
   actions.
3. Does this require an Apple-specific player? No.
4. What is missing? Dynamic Apple system-color mapping, checked-state semantics in the creation API,
   richer symbols/images, focus/input controls, and rendered parity evidence against SwiftUI.

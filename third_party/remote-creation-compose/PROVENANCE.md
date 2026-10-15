# Forked: Compose Multiplatform Remote Compose creation

**Prototype, not for landing.** Covers `:third-party-remote-creation-compose` (this directory) and
`:third-party-remote-creation-writer` (`../remote-creation-writer`).

This is a fork of AndroidX's Remote Compose **creation** stack, ported to Compose Multiplatform, so
`@RemoteComposable` content can be authored and captured on every rc-players target.

The fork stays in **AndroidX's own package** (`androidx.compose.remote.creation.*`) and changes as
little as it can. Component libraries such as Wear `remote-material3`, catalog code, and code a UI
builder exports are all written against that API. They then compile against this fork in a CMP host
and against real AndroidX on an Android device, from the same source.

Anything rc-players adds to creation lives outside this fork, in its own package
(`ee.schimke.composeai.rccreation`, `:rc-creation-bridge`). The fork carries only the seams that
layer cannot reach from outside, and each one is a marked local patch.

## Upstream

- Repository: <https://github.com/androidx/androidx>
- AndroidX change [4307936](https://android-review.googlesource.com/c/platform/frameworks/support/+/4307936)
  patch set 17, commit `01398020c7ec20f51e51bfc2b65b9b425e338be9`, refreshed to androidx.dev
  snapshot `16547072`.
- License: Apache-2.0
- Paths:

| This fork | AndroidX source |
| --- | --- |
| `src/commonMain/.../creation/compose` | `compose/remote/remote-creation-compose` |
| `src/commonMain/.../foundation` | `compose/remote/foundation/foundation` |
| `../remote-creation-writer` | write-side sources from `remote-core`, `remote-creation-core` and `remote-creation`, re-expressed as a common `RemoteWriter` |

## The CMP port it starts from

The CMP port of those sources was made in yschimke/remote-m3-catalog `vendor/` and published there
as `ee.schimke.remotecompose:*:4307936-ps17-cmp09`. Its `vendor/README.md` lists what the port took
from upstream and what it left out. This fork is that port's **commonMain**, copied from
remote-m3-catalog `9cb05a6`:

- **remote-creation-compose and remote-foundation:** `src/commonMain` is copied into `src/commonMain`.
- **remote-write-core:** `src/commonMain` and `src/commonTest` are copied into `../remote-creation-writer`.
- **Actuals:**
  - `src/skikoMain` (JVM, Wasm, iOS, macOS) holds the port's three `wasmJsMain` actuals. They have no
    platform dependencies.
  - `src/androidMain` holds the port's Android actuals.

These are deliberately **not** carried:

- The port's legacy JVM writer path (`jvmMain`, `jvmAndAndroidMain`, and `vendor/remote-core`'s
  roughly 340 Java files).
- Its AndroidX `remote-creation` Android integration.

Every target, Android included, captures through the common `RemoteWriter`.

Formatting: `./gradlew :third-party-remote-creation-compose:ktfmtFormat` uses ktfmt's kotlinlang
style, which AndroidX uses. It re-wraps lines in about 80 files where the port had drifted.
`../remote-creation-writer` is the port's own code and keeps Google style.

## Local patches

Every patched line sits under a `// LOCAL PATCH (rc-players)` comment. Each patch is generic enough
to offer upstream, and the list is meant to shrink.

| Where | What | Why |
| --- | --- | --- |
| `capture/CaptureRemoteDocument.common.kt` | `captureCommonRemoteDocumentInto(writer, …)`; the byte path calls it | Capture into any `RemoteWriter` |
| `capture/RemoteDocumentSession.kt` (new) | A composition kept alive under a host `CompositionContext`, re-rendered into a fresh writer on demand | The live, in-process path. It mirrors upstream's Android `captureRemoteDocument` loop. |
| `capture/CommonCreationState.kt` | `writer` settable (internal); `resetComponentIds()` | A session renders each frame into a fresh writer. Without the reset, every render renumbers every component (upstream has the same drift). |
| `layout/RemoteComposeApplier.kt` | `onChangesApplied` | Tells a session when to re-render |
| `capture/DisplayInfo.kt` | `RemoteDensityBehavior.wireValue` | Lets a writer outside the module describe the same display |
| `../remote-creation-writer/.../RemoteDocumentWriter.kt` | `startCustom` content section takes a fresh component id | **Port bug.** Upstream's `addContentStart` allocates one; the port reused the layout's own id, and rc-players' player rejects the duplicate. yschimke/remote-m3-catalog's vendor copy still has it. |
| `src/androidMain/.../ComposeColorFilter.android.kt` | Inlines the port's blend-mode mapping | The rest of the port's Android sources are not carried |

## Refreshing

Apply the upstream delta to the files this fork carries, as the port's earlier refreshes did, rather
than re-copying. Then re-apply the patches above. `:rc-player-embedded:jvmTest` is the check that the
seams still hold.

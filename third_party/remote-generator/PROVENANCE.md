# Forked: AndroidX Remote Compose code generator

`:third-party-remote-generator` is AndroidX's `compose/remote/remote-generator`: the Remote Compose
operation schema (`operations.json`) and its generators for C++, Rust, Python, Swift, TypeScript,
Java and Kotlin. rc-players forks it to add platforms upstream does not ship and targets of its own
(`:rc-codegen`), without waiting for a release: the published `remote-generator:1.0.0-alpha24` jar
holds only a licence file.

## Upstream

| | |
| --- | --- |
| Repository | `https://android.googlesource.com/platform/frameworks/support` |
| Path | `compose/remote/remote-generator` |
| Commit | `aba0a479ed6` (androidx-main, 2026-11-26) |
| Licence | Apache 2.0, as each file's header says |

What was copied:

- `src/main/**` — every source file and both schema resources, at upstream's paths, in upstream's
  package (`androidx.compose.remote.generator`). This module is build-only and never published, so
  it keeps upstream's package rather than relocating it as the creation fork does.
- `upstream/` — upstream's `build.gradle`, `README.md` and API dumps, for reference only. This
  directory's `build.gradle.kts` replaces the build file.

Not copied: `src/test/**`. Upstream's tests compile against `:compose:remote:remote-core` and
`:kruth`, which are not in this build. `:rc-codegen`'s tests and the Swift suites cover what this
repository uses.

## Formatting

Every file without a local patch is byte-identical to upstream's, so a re-sync is a copy and the
patches are the whole diff. That is why the module's sources are excluded from this repository's
ktfmt: its version re-wraps about 1,600 of upstream's lines. Patches are written in upstream's
kotlinlang style by hand.

## Local patches

Every hunk is marked `LOCAL PATCH (rc-players)`.

| File | Patch | Why |
| --- | --- | --- |
| `RemoteCodegenTarget.kt` (new) | `RemoteCodegenTarget`, `remoteCodegenTarget(id) { … }`, and `builtInTargets()`, which exposes upstream's languages plus `swift-portable` as targets | Upstream picks a platform by `LANGUAGE_*` constant, so a new platform means editing upstream's dispatcher. A target is the extension point. |
| `RemoteSerializationCodeGenerator.kt` | `loadBundledSchema()` made public; `generate(target, outputDirectory, schema)` added | A target outside the module needs the same schema, and a way to run. |
| `codegen/SwiftGenerator.kt` | `SwiftGenerator.Options`: `DEFAULT` (upstream's output, unchanged) or `PORTABLE` (no Foundation). The portable output uses `[UInt8]` (`WireBytes`), a typed `RemoteComposeWireError` in place of `NSError`, and a validating UTF-8 decode | So the bindings build with the standard library alone, and a caller can inspect a failure. Written as a rewrite of the finished output, so upstream's templates stay untouched. |
| `codegen/SwiftGenerator.kt` | `Swift.min` in array reads | Upstream bug: in an operation with a field named `min`, the unqualified call resolves to the field, and upstream's Swift fails to compile (16 errors). |

## Found while forking (for upstream)

These are also tracked in the upstream-issues document linked from
[`docs/design/RC_CODEGEN.md`](../../docs/design/RC_CODEGEN.md).

- Upstream's Swift output does not compile: `min(...)` is shadowed by an operation's `min` field.
- Upstream's Swift `RemoteDocument` drops a modern header's properties when it loads one (density,
  content description and the rest), and `save()` always writes the legacy header. Operations
  round-trip; the header does not.
- `rpnOperators` spells `STORE_R0` as `STORE_RO`.

## Re-syncing

1. Copy upstream's `src/main` over this directory's `src/main`.
2. Re-apply the hunks marked `LOCAL PATCH (rc-players)` (`git diff` against the previous sync shows
   them). Then update the commit above.
3. Run `./gradlew :rc-codegen:generateSwiftSources`, then the Swift suites
   (`swift test --filter 'RcCreationTests|RcWireOperationsTests'`), and commit the regenerated
   sources.

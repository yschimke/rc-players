# Code generation from the AndroidX schema (`rc-codegen`)

rc-players generates wire code from AndroidX's Remote Compose schema rather than transcribing it.
Two modules do this:

- **`:third-party-remote-generator`** is AndroidX's `remote-generator`, forked
  ([PROVENANCE](../../third_party/remote-generator/PROVENANCE.md)). It holds the schema
  (`operations.json`) and upstream's generators, plus one extension point: `RemoteCodegenTarget`,
  an id and a function from the schema to files. Upstream's languages are targets
  (`builtInTargets()`); the fork's Swift is upstream's `swift` target, patched.
- **`:rc-codegen`** defines rc-players' own targets, runs them, and keeps their committed output in
  sync.

The generator stays upstream's JVM code rather than a SwiftPM build plugin. A fork re-syncs by copy
and keeps every other language's output byte-identical to upstream's, so each Swift change is a
small, reviewable patch that can be offered back; a Swift rewrite would be a second generator to
keep in step with the schema. The generated Swift is committed, so building the Swift package needs
no JVM.

## Targets

| Target | From | Output | Committed at |
| --- | --- | --- | --- |
| `cpp`, `rust`, `python`, `typescript` | upstream | upstream's bindings | — |
| `swift` | upstream, patched | Swift 6 bindings without Foundation: `[UInt8]` bytes, `throws(WireError)`, `Sendable`/`Hashable` values, a closed `WireOperation` enum, a `WireDocument` that round-trips its header, lowerCamelCase constants, and the operator tables a writer needs | `Sources/RcWireOperations/RemoteComposeOperations.swift` |

`RcCreation` writes through the `swift` bindings, so every value it writes comes from the schema.
Where upstream's schema left a value out, the fork's schema adds it as a marked local patch (see the
PROVENANCE): the header property types and version words, the background and border
colour-reference flag, the border shape (including the rounded rectangle) and wire version, the text
font style, the "no id" sentinel and the component visibility values. Upstream's validator runs over
the patched schema in `:rc-codegen`'s tests.

## Commands

```
./gradlew :rc-codegen:generateSwiftSources    # regenerate the committed Swift, in place
./gradlew :rc-codegen:check                   # tests, and fail if a committed file is stale
```

`RcCodegenMain` also runs any single target: `--target=<id> --out=<dir>`, or `--list`.

## Defining a target

```kotlin
class MyTarget : RemoteCodegenTarget {
  override val id = "my-platform"
  override fun generate(schema: OperationSchema): Map<String, String> =
    mapOf("Ops.ext" to render(schema.operations))
}
```

Add it to `rcPlayersTargets()`, which is empty today. If its output is committed, also add it to
`RcCodegenMain.committedOutputs`, so `check` keeps it in sync.

## Verification

- `:rc-codegen:check` runs on the Linux CI lane. It runs the module's tests (the schema passes
  upstream's validation, conditions name their values, output is deterministic, the Swift has no
  Foundation, untyped throws or unnamed numbers, and carries every value the writer needs), then
  fails if the committed Swift differs from a fresh generation.
- `RcWireOperationsTests` loads all 701 catalog-corpus documents and the native player's fixtures
  with the generated bindings, writes them back, and compares the whole document byte for byte,
  header included. It does the same for documents `RcCreation` writes, which go through the same
  bindings.
- The generated sources compile on Linux in Swift 6 language mode. Foundation is not implicitly
  imported there, so a compile on Linux shows `RcWireOperations` uses none.

## Upstream

The forked generator's local patches and the issues found while forking are listed in its
[PROVENANCE](../../third_party/remote-generator/PROVENANCE.md). The upstream issue tracker
document collects them with the earlier findings (published artifact empty, JVM-only Kotlin
output, and others).

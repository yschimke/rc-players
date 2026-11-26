# Code generation from the AndroidX schema (`rc-codegen`)

rc-players generates wire code from AndroidX's Remote Compose schema rather than transcribing it.
Two modules do this:

- **`:third-party-remote-generator`** is AndroidX's `remote-generator`, forked
  ([PROVENANCE](../../third_party/remote-generator/PROVENANCE.md)). It holds the schema
  (`operations.json`) and upstream's generators, plus one extension point: `RemoteCodegenTarget`,
  an id and a function from the schema to files. Upstream's languages are targets
  (`builtInTargets()`), and so are the platform variants the fork adds.
- **`:rc-codegen`** defines rc-players' own targets, runs them, and keeps their committed output in
  sync.

## Targets

| Target | From | Output | Committed at |
| --- | --- | --- | --- |
| `cpp`, `rust`, `python`, `swift`, `typescript` | upstream | upstream's bindings | — |
| `swift-portable` | fork | upstream's Swift bindings without Foundation: `[UInt8]` bytes and a typed `RemoteComposeWireError` | `Sources/RcWireOperations/RemoteComposeOperations.swift` |
| `rc-swift-wire-constants` | rc-players | every opcode, operator and enumerated field value `RcCreation` writes, as complete Swift namespaces | `Sources/RcCreation/RemoteWireConstants.swift` |

`rc-swift-wire-constants` takes opcodes from the schema and from `rc-operations.manifest`, because
the manifest also carries constants the schema has no entry for: the 3D range, the reserved
extension range, and opcodes with no reader. Everything else comes from the schema. The few values
the schema does not carry (header value types, the header magic, the colour-id flag, the border wire
version, the font style) stay hand-written in `RemoteWireWriter.swift`, each naming its AndroidX
source.

## Commands

```
./gradlew :rc-codegen:generateSwiftSources    # regenerate the committed Swift, in place
./gradlew :rc-codegen:check                   # tests, and fail if a committed file is stale
```

`RcCodegenMain` also runs any single target:
`--target=<id> --out=<dir> [--manifest=<rc-operations.manifest>]`, or `--list`.

## Defining a target

```kotlin
class MyTarget : RemoteCodegenTarget {
  override val id = "my-platform"
  override fun generate(schema: OperationSchema): Map<String, String> =
    mapOf("Ops.ext" to render(schema.operations))
}
```

Add it to `rcPlayersTargets()`. If its output is committed, also add it to
`RcCodegenMain.committedOutputs`, so `check` keeps it in sync.

## Verification

- `:rc-codegen:check` runs on the Linux CI lane. It runs the module's tests (every manifest and
  schema opcode is named, output is deterministic, the portable Swift has no Foundation), then
  fails if either committed Swift file differs from a fresh generation.
- `RcWireOperationsTests` loads all 701 catalog-corpus documents and the native player's fixtures
  with the generated portable bindings, writes them back, and compares every operation byte for
  byte. It does the same for documents `RcCreation` writes. The header is excluded, because
  upstream's Swift `RemoteDocument` does not keep a modern header's properties.
- The generated sources compile on Linux in Swift 6 language mode. Foundation is not implicitly
  imported there, so a compile on Linux shows `RcWireOperations` uses none.

## Upstream

The forked generator's local patches and the issues found while forking are listed in its
[PROVENANCE](../../third_party/remote-generator/PROVENANCE.md). The upstream issue tracker
document collects them with the earlier findings (published artifact empty, JVM-only Kotlin
output, and others).

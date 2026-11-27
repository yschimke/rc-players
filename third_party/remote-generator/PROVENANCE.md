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
| Commit | `eea249910d7` (androidx-main, 2026-11-27); previously `aba0a479ed6` (2026-11-26) |
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

Upstream's tests can still be run against the fork, to see where it diverges: copy upstream's
`src/test` in and add `junit` and `remote-core` as test dependencies, temporarily. At `eea249910d7`,
with the published `remote-core` 1.0.0-alpha24, 31 of the 40 pass. Three fail on pure upstream too:
two compare the schema with a newer `remote-core` than alpha24, and the third is the subset
golden snapshot. The other six assert the literal text upstream's Swift and schema write (`>> 10`,
`maxLength: 4000`, `buffer.remaining / 20`, `type != 0 && …`), where the fork writes names.

## Formatting

Every file without a local patch is byte-identical to upstream's, so a re-sync is a copy and the
patches are the whole diff. That is why the module's sources are excluded from this repository's
ktfmt: its version re-wraps about 1,600 of upstream's lines. Patches are written in upstream's
kotlinlang style by hand.

## Local patches

Every hunk is marked `LOCAL PATCH (rc-players)`.

| File | Patch | Why |
| --- | --- | --- |
| `RemoteCodegenTarget.kt` (new) | `RemoteCodegenTarget`, `remoteCodegenTarget(id) { … }`, and `builtInTargets()`, which exposes upstream's languages as targets (the fork's Swift as `swift`) | Upstream picks a platform by `LANGUAGE_*` constant, so a new platform means editing upstream's dispatcher. A target is the extension point. |
| `RemoteSerializationCodeGenerator.kt` | `loadBundledSchema()` made public; `generate(target, outputDirectory, schema)` added | A target outside the module needs the same schema, and a way to run. |
| `codegen/SwiftGenerator.kt`, `codegen/MultiLangProtocolGenerator.kt` (Swift output only) | Every number in the generated Swift is named. A `WireLimits` enum holds each field's `maxLength` under its `javaLimit` name (or one made for the field), each `minLength`, and the initial-capacity cap; a length prefix's range is `Int(UInt16.max)` and the like. Element sizes are `MemoryLayout<…>.size` or a struct's `minWireBytes`. Each struct gets a `BitLayout` enum (shifts, masks, sign-extension shifts, reserved-bit masks, from the schema's bit ranges), its minimums and field defaults as `min…` and `default…` constants, and an op with sparse parameters a `SparseParameterID` enum. Strict-enum checks and skip conditions match the struct's named constants. `WireBuffer` reads and writes integers through one generic big-endian reader and writer. `HeaderLayout`, from the schema's header bit layouts (upstream's values for a schema without them), and an internal `HeaderKey` name every header word the `WireDocument` loader and writer use; its hash discriminators are strings. The hand-written helpers name their layouts: `FloatAnimationSpec.BitLayout` from `animationSpec.flagsBits`, `PathCommandLayout` and the `PathEffect` offsets from the schema's fields, `isTime` by variable name, the operator tables' valid ids as an `ids` set, `ColorExpression`'s `alphaPayloadShift` and `alphaPayloadMask`. `toSwiftLowerCamel` is internal so both files name constants alike. | AGENTS.md forbids wire magic numbers in Swift decoders, and upstream's templates write them throughout (`>> 10`, `& 0x3F`, keys 5/6/14/31, `/ 20`, `4096`, `case 0, 1, 2`). `RcCodegenTest.swiftNamesEveryNumber` fails if the generated file has a number (other than 0, 1 or an error code) outside a named definition; every other language's output is upstream's. |
| `codegen/MultiLangProtocolGenerator.kt` (Swift output only) | A doc comment on `FloatAnimationSpec.pack`: its size matches AndroidX's `FloatAnimation.packToFloatArray`, trailing zeros included | The count looks wrong (the initial value and the parameters are counted more than once), but it is AndroidX's, so a Swift writer's words match a Java or Kotlin writer's. `HelperTests` pins the words. |
| `operations.json`, `JsonSchemaParser.kt`, `OperationSchema.kt`, `codegen/CodegenExprUtils.kt`, every generator | Condition expressions (`presentIf`, `conditionalTypes`, `conditionalRoles`) name their values instead of writing numbers: a top-level `expressionConstants` table (mesh types and flag, animation kind mask and `CUSTOM`, colour-expression modes, custom-property types, event flags, graphics-layer and header tag fields, the bitmap-font kerning version, the text dynamic-colour flag), parsed into `OperationSchema.expressionConstants`; `conditionConstants` adds the header constants. `translateCondition` takes a `constants` map. Every language but Swift inlines the values, and its output is byte-identical to upstream's; Swift references a generated `WireExpressionConstants` enum and compares fields widened to `Int`. | Upstream copies each expression verbatim into every language, so its numbers (`(enterAnimation & 255) == 8`) became wire magic numbers in the Swift decoder. `RcCodegenTest.conditionsNameEveryValue` fails if a number (other than a zero flag test) comes back. JSON has no comments, so this row marks the `operations.json` hunks; upstream's validator passes over the patched file (`RcCodegenTest.schemaPassesUpstreamValidation`). |

### Patches upstream has taken

Dropped at `eea249910d7`, because upstream now does the same (with its own names, which the fork
adopts):

| Commit | Upstream change | The fork's patch it replaces |
| --- | --- | --- |
| `28319b34d33` "Make generated Swift bindings zero-Foundation and Swift 6-ready" | `[UInt8]` bytes, a typed `WireError` and `throws(WireError)`, a validating UTF-8 decode, `Equatable`/`Hashable`/`Sendable` throughout (bit-exact float equality), a closed `WireOperation` enum, `WireDocument` keeping a modern header's properties (`WireHeaderProperty`, `HeaderPropertyValue`), lowerCamelCase constants, `Swift.min` | The Swift 6 dialect, the `WireDocument` rewrite, the lowerCamelCase rename pass and the `Swift.min` fix |
| `9576762a7d8` "Add Swift writer tables and symmetric maxLength write checks" | `FloatExpressionOperator` and `IntegerExpressionOperator`, `IdEncoding.noId`, `PaintCommandTags.tagMask` and `inlineValueShift`; every `maxLength` field checked on write | The writer tables and the write-side `maxLength` checks |
| `72d1b1f9e0a` "Ignore sign bit in isNanBits across all generated languages" | `isNanBits` takes any NaN with a payload as an id, in every language (`idEncoding.nanExponent`) | The Swift-only `isNanBits` fix |
| `6325c2be1f1` "Enrich operations.json schema metadata, enums, and reserved opcodes" | `COLOR_REF` on the background and border flags; the border shape (with `ROUNDED_RECTANGLE` = 2) and wire version; `TextLayout.fontStyle` (`FONT_STYLE_NORMAL`, `FONT_STYLE_ITALIC`); `headerConstants.dataTypes` and `version`; `idEncoding.noId` and `nanExponent`; `visibilityConstants` (Swift `ComponentVisibility`); `STORE_R0` spelled right; `reservedOpcodes`; `opCodeName` and `javaClass` | Every `operations.json` value addition, and `SchemaIdEncoding.noId` in the parser |
| `2f4299a9fb1` "Harden JsonSchemaParser opCodeName, reservedOpcodes, and resolvedAfter validation" | Validation of the new schema fields | (none: new upstream behaviour) |

## Found while forking (for upstream)

These are also tracked in the upstream-issues document linked from
[`docs/design/RC_CODEGEN.md`](../../docs/design/RC_CODEGEN.md).

- Fixed upstream in `28319b34d33`: upstream's Swift output did not compile (`min(...)` shadowed by
  an operation's `min` field); its `RemoteDocument` dropped a modern header's properties and
  `save()` always wrote the legacy header; it was not Swift 6-ready (no `Sendable` or `Hashable`,
  untyped `throws`, `[RemoteOperation]` existentials, SCREAMING_SNAKE_CASE constants).
- Fixed upstream in `6325c2be1f1`: `rpnOperators` spelled `STORE_R0` as `STORE_RO`; the schema named
  no values for the background and border colour-reference flag, the border shape (and had no
  rounded rectangle, which writers send as 2), the border wire version, the text font style, the
  header property types and version words, the "no id" sentinel, and the component visibility
  values.
- Fixed upstream in `72d1b1f9e0a`: the Swift `IdEncoding.isNanBits` disagreed with the Java readers
  on a NaN without its sign bit (`0x7F800005`: Java id 5, Swift a value).
- Every generator's `FloatAnimationSpec.pack`, like AndroidX's `FloatAnimation.packToFloatArray`, sizes
  its array by counting the initial value three times and the parameters twice, so a packed spec
  ends in unread zero words. The fork keeps it, for byte compatibility with Java and Kotlin writers.
- The schema's condition expressions compare against bare numbers (61 of them), which every generator
  copies into its output. The fork names them (above).
- Upstream's Swift templates still write wire values as numbers (header keys and masks, array
  limits and element sizes, bit shifts and masks, sparse ids, enum values). The fork names them
  (above).

## Re-syncing

1. Copy upstream's `src/main` over this directory's `src/main`.
2. Re-apply the hunks marked `LOCAL PATCH (rc-players)` (`git diff` against the previous sync shows
   them), and the `operations.json` condition names in the table above, dropping any upstream has
   since made itself (and crediting its commit under "Patches upstream has taken"). Then update the
   commit above, and copy upstream's `build.gradle`, `README.md` and `api/` into `upstream/`.
3. Run `./gradlew :rc-codegen:generateSwiftSources`, then the Swift suites
   (`swift test --filter 'RcCreationTests|RcWireOperationsTests'`), and commit the regenerated
   sources.

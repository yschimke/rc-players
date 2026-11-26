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
| `codegen/SwiftGenerator.kt` | `HeaderLayout`, emitted from the schema's bit layouts (`Header.majorVersion`'s version and magic, `HeaderProperty.tag`'s key and data type) and limits; the `RemoteDocument` loader names every header value (`HeaderConstants.DATA_TYPE_*`, `MAGIC_NUMBER`, `HeaderProperty.DOC_WIDTH` …) instead of repeating it as a literal | Upstream's template hard-codes `0xFFFF0000`, `0x048C0000`, `0x3F`, `case 0…3` and keys 5/6/14/31, which this repository's rule on native wire constants forbids. |
| `operations.json`, `JsonSchemaParser.kt`, `OperationSchema.kt`, `codegen/CodegenExprUtils.kt`, every generator | Condition expressions (`presentIf`, `conditionalTypes`, `conditionalRoles`) name their values instead of writing numbers: a top-level `expressionConstants` table (mesh types and flag, animation kind mask and `CUSTOM`, colour-expression modes, custom-property types, event flags, graphics-layer and header tag fields, the bitmap-font kerning version, the text dynamic-colour flag), parsed into `OperationSchema.expressionConstants`; `conditionConstants` adds the header constants. `translateCondition` takes a `constants` map. Every language but Swift inlines the values, and its output is byte-identical to before; Swift references a generated `WireExpressionConstants` enum and compares fields widened to `Int`. | Upstream copies each expression verbatim into every language, so its numbers (`(enterAnimation & 255) == 8`) became wire magic numbers in the Swift decoder. `RcCodegenTest.conditionsNameEveryValue` fails if a number (other than a zero flag test) comes back. |
| `JsonSchemaParser.kt`, `OperationSchema.kt` | `SchemaIdEncoding.noId`, read from `idEncoding.noId` (default -1) | The "no id" sentinel (no component id requested, no animation, the default font) is a wire value the schema did not name. |
| `resources/schema/operations.json` | Values upstream's schema leaves out, added so generated code needs no hand-written constants: `colorRef` bit on `BackgroundModifierOperation.flags` and `BorderModifierOperation.flags`; enums on `BorderModifierOperation.shapeType` (`RECTANGLE`, `CIRCLE`, `ROUNDED_RECTANGLE` = 2), `BorderModifierOperation.reserve1` (`LEGACY`, `VERSION_1`) and `TextLayout.fontStyle` (`NORMAL`, `ITALIC`); `headerConstants.propertyType` (`DATA_TYPE_INT` … `DATA_TYPE_STRING`) and `headerConstants.version` (`MAGIC_NUMBER` 0x048C0000, major 1, minor 1, patch 0); `idEncoding.noId` (-1) | JSON has no comments, so this table is the marker. Each value is AndroidX's: the writers in `remote-creation-compose` and `remote-core`. `ROUNDED_RECTANGLE` matters: without it a writer sends a radius with shape 0 and players draw the border square. Upstream's validator passes over the patched file (`RcCodegenTest.schemaPassesUpstreamValidation`). |

## Found while forking (for upstream)

These are also tracked in the upstream-issues document linked from
[`docs/design/RC_CODEGEN.md`](../../docs/design/RC_CODEGEN.md).

- Upstream's Swift output does not compile: `min(...)` is shadowed by an operation's `min` field.
- Upstream's Swift `RemoteDocument` drops a modern header's properties when it loads one (density,
  content description and the rest), and `save()` always writes the legacy header. Operations
  round-trip; the header does not.
- `rpnOperators` spells `STORE_R0` as `STORE_RO`.
- The schema names no values for several fields writers set: the background and border colour-reference
  flag, the border shape (and has no rounded rectangle, which writers send as 2), the border wire
  version, the text font style, the header property types and version words, and the "no id"
  sentinel. The fork adds them (above).
- The schema's condition expressions compare against bare numbers (61 of them), which every generator
  copies into its output. The fork names them (above).

## Re-syncing

1. Copy upstream's `src/main` over this directory's `src/main`.
2. Re-apply the hunks marked `LOCAL PATCH (rc-players)` (`git diff` against the previous sync shows
   them), and the `operations.json` additions in the table above, dropping any upstream has since
   made itself. Then update the commit above.
3. Run `./gradlew :rc-codegen:generateSwiftSources`, then the Swift suites
   (`swift test --filter 'RcCreationTests|RcWireOperationsTests'`), and commit the regenerated
   sources.

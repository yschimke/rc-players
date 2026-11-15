# Reproducible captures, and diffing two versions of a document

## Reproducibility

A capture has to be a pure function of its content and its display. Livery content-addresses
catalog blobs by hash, captures its sample catalogs in CI, and reviews a template change as a
change to its `.rc`; a capture that differed from run to run would be a spurious change in every
review and a cache miss in every build.

What was checked (November 2026):

| Check | Where | Result |
| --- | --- | --- |
| The six `RcToKotlinSamples` captured three times in one JVM, interleaved and in reverse order | `RcCaptureDeterminismTest` (`:rc-creation-bridge:jvmTest`) | identical bytes |
| The same captured in a forked JVM with `ar-EG`, `Pacific/Kiritimati`, ISO-8859-1 | the same test | identical bytes |
| Livery's sample catalogs captured twice from scratch (`:livery-capture-cmp:captureSamples --rerun`) | `livery/scripts/check-capture-determinism.sh` | identical bytes |
| The same under another locale and time zone | the same, by hand | identical bytes |
| `livery build` of one source tree, twice | Livery's `ReproducibleBuildTest` | blobs identical; the manifest's `serial`, `generatedAtEpochMs` and `expiresAtEpochMs` read the clock. Livery now honours `SOURCE_DATE_EPOCH` |

What could have broken it, and why it does not:

- **Hash containers.** The creation code keeps `HashMap`/`HashSet`s keyed by identity hashes
  (`RemoteStateInstanceKey`, `SpanOp`). Every one is used for lookup or membership; whatever is
  written is iterated from a list or a `LinkedHashMap`, in composition order.
- **`platformUses24HourTime()`** reads the locale on Android. On the JVM (Skiko) it is `true`, so a
  JVM capture does not depend on the machine. An Android capture of a clock does: capture
  under a fixed locale when the bytes are compared.
- **The frame clock** is synthetic (16.67 ms steps from 0), not the wall clock.
- **Compression.** The writer never sets `COMPRESS`, so no deflate implementation is involved.
- **Embedded PNGs** are encoded by the caller's `encodePng`. Skia's encoder writes the same bytes
  for the same pixels; an encoder that stamps a time chunk would not.

The guard is `RcCaptureDeterminismTest`: it fails with the structural diff of the two captures.

## Reading a document

`RcDocument.toStructuralText()` (`rc-player-edit`, and `remote-compose-player --dump`) prints the
header's properties by name, then one operation per line, indented by container, with no
`ContainerEnd` lines. Float words print as their value or as `@id` for a NaN-boxed reference;
embedded bytes print as their size and an FNV-1a hash. It is a pure function of the bytes, so it is a
`git diff` textconv:

```sh
git config diff.rc.textconv "remote-compose-player --dump"
echo '*.rc diff=rc' >> .gitattributes
```

## Diffing two versions

A byte diff of two `.rc` files is useless, and a naive line diff of their dumps is little better:
the writer allocates ids in document order, so one value added near the top renumbers every id after
it and every later line changes. `RcDocument.structuralDiff(after)` (`--diff`) handles that in two
parts.

1. **A summary of what renumbering cannot hide**: header properties by name; operation counts by
   opcode name (`-1 MODIFIER_CLICK, +4 ATTRIBUTE_COLOR`); text by content; named values by name and
   type; embedded images and fonts by hash; and whether only ids differ.
2. **A line diff in the old document's ids.** The two dumps are aligned with every id masked
   (Myers' diff); each pair of aligned lines pairs the ids they name, in order; the new dump is
   rewritten in the old one's ids, an id only it has printed primed (`61'`), and diffed again.
   Hunks are headed by their enclosing container, as `git diff` heads one with its function.

The disabled filled button in the catalog corpus against the enabled one goes from 91 changed lines
to 31, and the summary says what a reviewer needs: the click handler gone, `onPrimary` and `primary`
replaced by `onSurface` with expressions scaling one of its channels by 0.12 and 0.38 (the disabled container and label).

Limits: ids are read from field names (`id=`, `…Id=`, `…Ids=[…]`) and `@` references, so an id
packed into a raw word (paint data, an integer expression's values, `idAndWinding`) keeps its
number and shows as changed. The summary does not try to say what a change means for drawing;
`--render-png` or the A/B renders under `renders/` do.

## Prior art

In these repositories:

- **Kotlin ABI dumps** (`api/*.api`, `checkKotlinAbi`): a binary artifact reviewed as a committed text
  projection. The same idea as `--dump` as a textconv.
- **Livery's `livery check`** (`CompatibilityCheck`): compares two catalog source trees and explains
  each change in what it means for apps (a template now needs a newer minor, reads a new value,
  gains a risky feature), with `--markdown` for a PR comment; `livery review` adds before/after
  renders. The model for a semantic layer over this diff.
- **`BindingAgreementTest`** (Livery): compares two captures of one design by operation histogram,
  as this summary does, and by pixels.
- **`rc-conformance`'s `Pixelmatch` and `DiffHeatmap`**, and `scripts/rc-lane-ab`: visual diffs of
  what two players draw.
- **`RcToKotlinRoundTripTest`**: compares a capture and a recapture; its failure message is now this
  structural diff.

Elsewhere, for similar formats:

- **Disassemble, then diff text**: `wasm2wat`, `llvm-dis`, `javap -c`, `aapt2 dump xmltree`,
  `protoc --decode`; Git's `textconv` drivers exist for exactly this.
- **Renumbering-aware IR diff**: `llvm-diff` matches values between two modules along matched
  instructions rather than by name — the approach of step 2.
- **Whole-artifact explainers**: `diffoscope` (reproducible-builds.org) recursively unpacks two
  artifacts and diffs each layer in its own format; Android Studio's APK Analyzer and
  `apkanalyzer`/`bundletool` compare two builds by component; `jar`/`zipinfo` diffs for archives.
- **Reproducible builds**: `SOURCE_DATE_EPOCH` for timestamps, fixed archive ordering, and
  checking by building twice and comparing, as `check-capture-determinism.sh` does.

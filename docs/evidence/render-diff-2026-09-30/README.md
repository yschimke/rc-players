# Player render differences, remote-m3 catalog, 2026-09-30

Source: the published player comparison on preview.coo.ee (`/remote-m3/compare`, generation
`2f7e6c42a6b0b71e07e58bc467426866d2deffdc`), 703 previews. Every lane's PNG was diffed against the
`baked` lane (the AndroidX render) with a per-pixel test (max channel delta > 32 on white, so a
ranking rather than pixelmatch's exact score).

`mismatch-vs-baked.json` has one row per preview: percent of pixels that differ per lane, `"BLANK"`
where the lane drew nothing but baked has content.

| lane | mean % | previews > 5% | blank |
|---|---|---|---|
| vendored Android (`embedded`) | 0.20 | 3 | 0 |
| cmp-wasm (645 previews) | 0.40 | 8 | 0 |
| androidx.dev (`androidx-embedded`) | 2.03 | 44 | 1 |
| JS | 2.05 | 64 | 15 |
| cmp-jvm | 8.20 | 128 | 224 |

Montage columns: baked, js, embedded, androidx-embedded, cmp-jvm, cmp-wasm.

`cmp-jvm-harness-mismatch-vs-baked.json`: the same diff for `RcCmpRenderHarness` (the real CMP JVM
player, rc-players main) run over all 703 documents at density 2: 703 rendered, 0 blank, mean 0.41%.
The catalog's published `cmp-jvm` column is a different player (see the correction on the cmp-jvm issue).

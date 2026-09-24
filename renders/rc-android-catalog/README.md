# rc-android-catalog — the CMP player drawing the remote-m3 catalog on Android

One document of every remote-m3 component family from the committed corpus
(`scripts/rc-catalog-corpus/corpus`), rendered by `RcComposePlayer` on **Android** — through
`android.graphics`, not Skiko — under Robolectric's native graphics mode (API 34), 384 px at
density 2, downscaled into one sheet.

![Android catalog sheet](android-catalog-sheet.png)

This is `RcAndroidRenderTest.rendersOneDocumentOfEveryCatalogComponent`. The test asserts every
document renders without an exception and fewer than a tenth come out as one flat colour; with
`-Prc.android.out=<abs dir>` it also writes each render as a PNG:

```
./gradlew :rc-player-compose:testAndroidHostTest --rerun -Prc.android.out=<abs dir>
```

Fonts are supplied as a host would: each document's `google:` families through
`RcGoogleFontsTypefaceLoader` over the shared Google Fonts cache (pointed at the wasm host's
vendored files, which use the cache's `<slug>-<weight>.ttf` names), and everything else —
`default` is Roboto Flex — from the same directory's `fonts.json`. Orbitron, Space Grotesk,
JetBrains Mono, Google Sans Flex and Inter draw in their own faces. Two gaps are the test
directory's naming, not the loader: Lobster Two ships as `LobsterTwo-*.ttf` rather than
`lobster-two-400.ttf`, and the `wght`/`wdth` specimens need a `roboto-flex-variable.ttf` the
directory does not carry (it has `RobotoFlex.ttf`), so their axes do not vary here.

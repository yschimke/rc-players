# `coreTextAsImages`: styled text kept for players without `CoreText`

Each strip is one catalog document served to Wear widgets (API level 8, no `CoreText`) at density 2,
its named colours left for the host to set, drawn by the CMP player over a dark backdrop:

1. **Original** — the `CoreText` document as a newer player shows it.
2. **Images** — `coreTextAsImages(WEAR_WIDGETS, RcComposeTextRasterizer(fonts), RcDisplay(2f))`, each
   text wrapped at the width `coreTextWidths` measured; a text in a named colour is a white mask the
   canvas paint tints with that colour.
3. **Styling dropped** — `adaptTo(WEAR_WIDGETS, setOf(RcApproximation.TEXT))`, the `TextLayout`
   conversion that loses letter spacing, line height and font axes.

Regenerate with:

```
./gradlew :rc-player-compose:jvmTest --tests '*RcTextImagesRenderTest*' \
  -Prc.textImages.out=$PWD/renders/rc-text-images
```

`RcTextImagesRenderTest` asserts each image document differs from the original in under 1% of
pixels (channels more than 16 levels apart: a tinted mask rounds glyph edges differently) and less
than the dropped one does. These are CMP renders of the converted documents; no Wear or Android 16
device rendered these frames. The tint relies on the paint's colour filter applying to
`DrawBitmap`, as AndroidX's `AndroidPaintContext.drawBitmap` does with its whole paint.

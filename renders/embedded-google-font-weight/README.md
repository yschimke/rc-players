# Embedded lane drew a variable Google font at the file's default weight

Evidence for the `GoogleFontFamilies.variationAxes` fix in `third_party/rc-embedded-player`.

`edgebutton-label-weight.png` shows four `edgebutton__*` corpus documents at their manifest size
(454×200 at density 2), as three captures:

| column | what it is |
| --- | --- |
| AndroidX embedded before | `RcEmbeddedRenderHarness` on `main`, font cache `~/.cache/composeai/fonts` |
| AndroidX embedded after | the same harness with this change |
| CMP | `RcCmpRenderHarness`, for comparison |

## What was wrong

The edge button's label is a `CoreText` in `google:Roboto Flex` at weight 500, and it carries one
font axis (`pnum = 1`). A document with any axes makes the embedded harness resolve the family's
*variable* file (`roboto-flex-variable.ttf`) and build the `Font` with those axes as its
`variationSettings`. Only the document's axes were passed. Every axis the document didn't name
stayed at the file's default, `wght 400` included, so the label drew **Regular**.

It should be Medium. On a device the `google:` family resolves through the downloadable-font
provider at weight 500, and that path never sees the axes. The CMP player's own Android loader
(`RcGoogleFontsTypefaceLoader.variationSettings`) already applies the face's weight and slant first,
then the document's axes, and the embedded harness now does the same. A document `wght` still
replaces the requested weight.

The disabled variants made this look like an alpha difference, because a light label in a 38%-alpha
colour reads as "fainter". The colour is identical in every lane, `(246, 237, 255, 116)`; only the
stroke weight differed.

## Numbers

Label ink on `edgebutton__ideal__default__compact` (pixels at the label's full colour):

| lane | ink |
| --- | --- |
| embedded before | 1380 |
| embedded after | 1728 |
| CMP | 1720 |

The stroke weight now matches. The pixelmatch score between CMP and embedded goes *up* over the 64
edge buttons, though (mean 0.54% → 0.93%), because the Medium advances rasterize about 1 px wider
per glyph on the Android lane than on Skia (the line spans 135 px vs 131). The Regular face happened
to sit closer to CMP's line width, so the old score was flattering the wrong weight. At weight 500
the single-line `extra-small` label also ellipsizes one character earlier on Android ("MMM…" vs
"MMMM…").

The view lane (`RcViewPlayerRenderHarness`) is unchanged. It runs the unmodified upstream
`RemoteDocumentPlayer`, which has no font cache, and it still draws the label at its fallback
weight.

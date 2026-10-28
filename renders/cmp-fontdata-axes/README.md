# cmp-fontdata-axes — a document's own `FontData` font, at its axes, in both text paths

The CMP player loaded a document's embedded `FontData` font for `CoreText`, but as a `FontFamily`
built once per document, so the style's variation axes (properties 20/21) never reached it; and
canvas text (`DrawTextRun` with a paint `TYPEFACE`) could not name an embedded font at all — it drew
the host's (here: Compose's) default face. Embedded fonts are now held as bytes (`RcDocumentFonts`)
and instanced per font, weight, slant and axis set, the way `RcFontFaces` instances a host's faces,
for both `CoreText` and canvas text.

Each image stacks one run at `wdth` 25, 100 and 151. The font is the repo's vendored Roboto Flex,
embedded in the document as `FontData`; the host supplies no fonts, so whatever face the text is in
came from the document. Both documents name the font through a family-name text under the
`FontData`'s id — the one shape the old player also read.

| render | before | after |
| --- | --- | --- |
| `coretext-fontdata-wdth` | embedded Roboto Flex, all three lines at its default width | three widths |
| `canvas-fontdata-wdth` | Compose's default face, all three lines identical | embedded Roboto Flex at three widths |

`RcDocumentFontRenderTest` asserts the same from ink width, plus the shapes the evidence does not
show: a `CoreText` naming the bare `FontData` id (what AndroidX's creation API writes), a paint
`TYPEFACE` with the font-data bit (`RcPaint.setTypeface(fontDataId)`), an axis bound to a named
float, and a `wght` ramp. All of them fail against the previous player.

Regenerate with:

```
RC_LAYOUT_EVIDENCE_DIR=<dir> ./gradlew :rc-player-compose:jvmTest --tests '*RcDocumentFontRenderTest*'
```

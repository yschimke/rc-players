# cmp-dp-font-size — a DP document's literal font size is in dp

remote-core 1.0.0-alpha22's `CoreText` and `TextLayout` scale a **literal** font size by the host
density when the document's density behavior is DP, as they already did padding and offsets. A font
size that is a variable stays the pixels it evaluates to. The CMP player read every font size as
pixels.

One string at a literal font size of 20 in a DP document, rendered at density 2.0 through each text
operation:

| render | before | after |
| --- | --- | --- |
| `dp-coretext` | 20 px text | 40 px text |
| `dp-textlayout` | 20 px text | 40 px text |

After the change both match the same string at a literal 40 px in a legacy document, which is what
`RcDpFontSizeRenderTest` asserts from the ink width. The creation fork writes DP-mode font sizes in dp
to match (see `third_party/remote-creation-compose/PROVENANCE.md`); before that, a fork capture in DP
mode would have drawn at density squared after this change.

Regenerate with:

```
RC_LAYOUT_EVIDENCE_DIR=<dir> ./gradlew :rc-player-compose:jvmTest --tests '*RcDpFontSizeRenderTest*'
```

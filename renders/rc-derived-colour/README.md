# A derived colour reaching the text that names it

Before/after for the two deltas in [#47](https://github.com/yschimke/rc-players/issues/47), which
close two of the three symptoms reported in [#46](https://github.com/yschimke/rc-players/issues/46).

Both lanes are rendered locally by `RcEmbeddedRenderHarness` from the same staged documents with the
same font cache, so the only variable is the player. The reference column is
`RcViewPlayerRenderHarness` over the same documents.

![Derived colour before and after](derived-colour-before-after.png)

| Document | before | after | View player |
| --- | --- | --- | --- |
| `button-filledvariant__ideal__icon` secondary label | `(15, 12, 23)` — **2.07:1** | `(212, 202, 227)` — **5.95:1** | `(212, 202, 227)` — 5.95:1 |
| `textbutton__ideal__disabled` | 0 ink px | 876 ink px, max alpha 97 | 915 ink px, max alpha 97 |
| `checkboxbutton__ideal__unselected-disabled` | 0 ink px | 33585 ink px, max alpha 59 | 44980 ink px, max alpha 116 |

The checkbox is **partly** fixed and shown that way on purpose: the container and the box now draw
where nothing did before, but its labels are still missing and its content alpha is 59 against the
View player's 116. That residue is the same disabled-content shortfall
[wear-m3-catalog#91](https://github.com/yschimke/wear-m3-catalog/issues/91) reports on
`RemoteButton`, and it is not fixed here.

![EdgeButton label before and after](edgebutton-label-before-after.png)

`edgebutton__ideal__outlined-extra-small-disabled` is the one document of 475 whose pixelmatch score
against the View lane went *up*. It is not a regression: it went from drawing a bare outline to
drawing its label, which the View player draws too — the score rises because the label's glyph
positions differ from the View lane's font, and a label that is absent contributes no differing
pixels at all. Worth keeping in view whenever a score is used as the gate.

## Whole-catalog effect

All 475 published `remote-m3` documents, before and after, same harness and same font cache:

| | documents |
| --- | ---: |
| unchanged | 437 |
| changed, closer to the View player | 24 |
| changed, further from it | 1 (the EdgeButton above) |

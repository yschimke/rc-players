# Embedded player: `CoreText` sized to its lines (#572)

Each row of `before-after-cmp.png` shows one wear-m3-catalog `remote-m3` document, at 227dp and
dpi 320, in three columns: the vendored embedded player at `main`, the same player with the patch,
and the CMP player (`rc-player-compose` 2.1.1). `tonal-icon-left-edge-3x.png` zooms in on the left
edge of the `TonalRemoteButton` `icon` variant.

All three lanes ran in Robolectric (SDK 35, native graphics) with no Google Fonts cache, so every
lane drew `google:Roboto Flex` in the same fallback face. Moving to the real face changes the text
widths, but the change in how a text node is sized is the same.

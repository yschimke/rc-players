// ahem-font.mjs — the W3C Ahem test font, as used by the conformance corpus.
//
// Ahem exists to make text layout arithmetic instead of typography. Every glyph is a solid
// 1em square: units-per-em 1000, advance 800, ascent 800, descent 200. A run of N characters
// at size S is therefore exactly N*S wide and S tall, on every platform, with no hinting,
// kerning or fallback to argue about.
//
// That property is why the corpus renders text with it. Two players disagreeing about the
// shape of a lowercase 'g' is not a conformance failure, but it is indistinguishable from one
// in a pixel comparison. Pinning the typeface removes the whole class of false positives, so a
// raster difference means a real difference in placement, colour or transform.
//
// The font file is the single source of truth. It used to be a 29 KB base64 literal in this
// file, which meant the bytes a player registered and the bytes a report embedded were two
// copies that nothing kept in step.
//
// Public domain / CC0 — http://dev.w3.org/CSS/fonts/ahem/COPYING

import { readFileSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

/** Absolute path to the TTF. Registering a font needs a path, not bytes. */
export const AHEM_FONT_PATH = join(dirname(fileURLToPath(import.meta.url)), 'fonts', 'Ahem.ttf');

/**
 * The family name to render with. This is the name the font declares internally, so a
 * registration under this name and a CSS `font-family` using it resolve to the same face.
 */
export const AHEM_FONT_FAMILY = 'Ahem';

let cachedBase64 = null;

/**
 * The font as base64, for consumers that must inline it — the HTML reports embed it in a
 * `@font-face` so a report stays self-contained when passed around as a single file.
 *
 * Read lazily: players registering the font by path should not pay to base64-encode 21 KB.
 */
export function ahemFontBase64() {
    if (cachedBase64 === null) {
        cachedBase64 = readFileSync(AHEM_FONT_PATH).toString('base64');
    }
    return cachedBase64;
}

/** Back-compatible alias for {@link ahemFontBase64}. */
export const AHEM_FONT_BASE64 = ahemFontBase64();

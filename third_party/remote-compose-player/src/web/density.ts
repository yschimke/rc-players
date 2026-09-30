/**
 * The density a document is laid out at.
 *
 * A host that called `setDensity` has said what display it is showing on, so that wins. Without
 * one, the document's own generation density is used: a headless page has a `devicePixelRatio` of
 * 1, which lays every dp-typed dimension of a density-2 document out at half its size and re-wraps
 * its text. `ambient` (the browser's `devicePixelRatio`) is only the last resort, for a document
 * that declares no density at all.
 */
export function resolveDensity(explicit: number | null, generation: number, ambient: number): number {
    if (explicit !== null && explicit > 0) return explicit;
    if (generation > 0) return generation;
    return ambient > 0 ? ambient : 1;
}

// report-paths.mjs — where a player's results and reports live.
//
// Results are namespaced per player:
//
//   <spec>/results/<player>/conformance-results.json
//   <spec>/results/<player>/conformance-audit.html
//   <spec>/results/<player>/test-inspector.html
//
// There can be any number of third-party players, so "the" results file is not a meaningful
// idea. A player writes into its own directory and cannot clobber anyone else's.
//
// Corpus-level artifacts — gold-overview.html and gold-overview.json — describe the golds
// rather than any player, so they stay at the top level.
//
// Nothing here knows what a player *is*; it only knows that results are filed under a name.

import { existsSync, readdirSync, statSync } from 'fs';
import { isAbsolute, join, resolve } from 'path';

export const RESULTS_DIRNAME = 'results';
export const RESULTS_FILENAME = 'conformance-results.json';

/** Reads a `--flag value` pair from argv, or returns the fallback. */
export function argValue(flag, fallback = null, argv = process.argv) {
    const i = argv.indexOf(flag);
    return (i !== -1 && argv[i + 1]) ? argv[i + 1] : fallback;
}

/** `<spec>/results/<player>` — where that player files everything it produces. */
export function playerDir(specDir, player) {
    return join(specDir, RESULTS_DIRNAME, player);
}

/** Every player that has published results, by directory name. */
export function listPlayers(specDir) {
    const root = join(specDir, RESULTS_DIRNAME);
    if (!existsSync(root)) return [];
    return readdirSync(root).sort().filter((name) => {
        const dir = join(root, name);
        return statSync(dir).isDirectory() && existsSync(join(dir, RESULTS_FILENAME));
    });
}

/**
 * Works out which results file to render.
 *
 * Precedence: an explicit file, then `--player NAME`, then — if exactly one player has
 * published results — that one. With several players and no choice made, we refuse rather
 * than pick, because silently rendering the alphabetically-first player's numbers under a
 * generic filename is exactly the confusion namespacing exists to prevent.
 *
 * @returns {{ resultsFile: string, player: string }}
 */
export function resolveResultsFile(specDir, { explicitFile = null, player = null } = {}) {
    if (explicitFile) {
        const file = isAbsolute(explicitFile) ? explicitFile : resolve(process.cwd(), explicitFile);
        if (!existsSync(file)) throw new Error(`Results file not found: ${file}`);
        return { resultsFile: file, player: player || 'player' };
    }

    const available = listPlayers(specDir);

    if (player) {
        const file = join(playerDir(specDir, player), RESULTS_FILENAME);
        if (!existsSync(file)) {
            throw new Error(
                `No results for player "${player}" (looked for ${file}).`
                + (available.length ? `\nAvailable: ${available.join(', ')}` : ''));
        }
        return { resultsFile: file, player };
    }

    if (available.length === 1) {
        return { resultsFile: join(playerDir(specDir, available[0]), RESULTS_FILENAME),
            player: available[0] };
    }

    if (available.length === 0) {
        throw new Error(
            `No results found under ${join(specDir, RESULTS_DIRNAME)}.\n`
            + 'Run a player\'s conformance runner first, or pass an explicit results file.');
    }

    throw new Error(
        `Several players have published results: ${available.join(', ')}.\n`
        + 'Choose one with --player <name>, or pass an explicit results file.');
}

/**
 * Turns an `--out` value into a concrete file path.
 * A directory (or anything without the expected extension) means "write the default filename
 * in here"; a full filename is taken literally.
 */
export function resolveOutFile(out, defaultDir, defaultName) {
    if (!out) return join(defaultDir, defaultName);
    const p = isAbsolute(out) ? out : resolve(process.cwd(), out);
    const looksLikeFile = /\.[A-Za-z0-9]+$/.test(p);
    return looksLikeFile ? p : join(p, defaultName);
}

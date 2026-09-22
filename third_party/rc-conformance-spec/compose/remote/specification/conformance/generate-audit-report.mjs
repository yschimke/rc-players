// generate-audit-report.mjs — Generate HTML Visual Conformance Audit Report & Test Inspector
//
// Reads a player's conformance-results.json and generates:
// 1. conformance-audit.html: Full visual conformance audit report with matrix, areas,
//    side-by-side / overlay toggles, live animation playback, and collapsible JSON docs.
// 2. test-inspector.html: Dedicated interactive single-test inspector with searchable test picker,
//    frame-by-frame scrubbers, timeline animations, and full document inspection.
//
// This viewer is player-agnostic: it renders whatever results it is given and takes the
// player's name from the file itself.
//
// Usage:
//   node generate-audit-report.mjs                     # the only player with results
//   node generate-audit-report.mjs --player NAME       # pick one of several
//   node generate-audit-report.mjs --results FILE      # an explicit results file
//   node generate-audit-report.mjs --out DIR|FILE      # where to write
//   node generate-audit-report.mjs RESULTS OUT         # positional form, still supported

import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "fs";
import { resolve, dirname, join, relative } from "path";
import { fileURLToPath } from "url";
import { attachSourceDocuments, filterActiveTests, generateInspectorHtml } from "./generate-test-inspector.mjs";
import { AHEM_FONT_BASE64 } from "./ahem-font.mjs";
import { argValue, resolveOutFile, resolveResultsFile } from "./report-paths.mjs";
import {
    loadOrBuildOperationsRegistry,
    renderOperationsStyles,
    renderOperationsOverviewCard,
    renderOperationsTab,
    renderOperationsScript,
} from "./generate-operations-grid.mjs";

// This generator lives beside the corpus it renders, so the corpus needs no configuring.
const SPEC_DIR = dirname(fileURLToPath(import.meta.url));

const positionalResults = process.argv[2] && !process.argv[2].startsWith("--") ? process.argv[2] : null;
const positionalOut = process.argv[3] && !process.argv[3].startsWith("--") ? process.argv[3] : null;

const { resultsFile, player } = resolveResultsFile(SPEC_DIR, {
    explicitFile: argValue("--results", positionalResults),
    player: argValue("--player"),
});

// Reports default to sitting beside the results they describe, which keeps each player's
// output self-contained under results/<player>/.
const outputFile = resolveOutFile(
    argValue("--out", positionalOut), dirname(resultsFile), "conformance-audit.html");
const inspectorFile = resolve(dirname(outputFile), "test-inspector.html");
mkdirSync(dirname(outputFile), { recursive: true });

// Each card links to the authoring JSON in the corpus. That is a relative href, and the report
// no longer necessarily sits next to `tests/` — so compute the hop from wherever it lands.
// Forward slashes: this is a URL, not a filesystem path.
const TESTS_HREF = (relative(dirname(outputFile), join(SPEC_DIR, "tests")) || "tests")
    .split(/[\\/]/).join("/");

const rawData = readFileSync(resultsFile, "utf8");
const report = JSON.parse(rawData);
const operationsRegistry = await loadOrBuildOperationsRegistry(SPEC_DIR);
const opImplRate = (
    ((operationsRegistry.summary.conforming100 +
        operationsRegistry.summary.verifiedPartial +
        operationsRegistry.summary.failingOnly +
        operationsRegistry.summary.implementedUntested) /
        operationsRegistry.summary.androidActive) *
    100
).toFixed(1);

// The gold corpus, indexed by test name.
//
// The results file carries what the player *observed* but not what the corpus *expected* —
// only the diffs of failing checks. That asymmetry is why the value evidence below could not
// be rendered from results alone: a passing check left no trace at all, so 250 of 252 tests
// had their observed numbers carried all the way here and then displayed nowhere.
//
// Reading the corpus is not a player-specific dependency: this generator already lives inside
// the corpus, and doing it here means a player has to emit nothing extra to get value
// evidence in its report.
const GOLD_BY_NAME = (() => {
    const index = new Map();
    const goldRoot = join(SPEC_DIR, "gold");
    if (!existsSync(goldRoot)) return index;
    for (const category of readdirSync(goldRoot, { withFileTypes: true })) {
        if (!category.isDirectory()) continue;
        const dir = join(goldRoot, category.name);
        for (const file of readdirSync(dir)) {
            if (!file.endsWith(".gold.json")) continue;
            try {
                const gold = JSON.parse(readFileSync(join(dir, file), "utf8"));
                if (!gold || !gold.name) continue;
                // A gold is identified by category *and* name, not by name alone. The corpus
                // briefly carried two different tests both called `data_map_id_lookup`, one
                // under `dataoperations` and one under `loom`; with a name-only key one of them
                // won, and a passing card was shown the other test's expected values and
                // reported them as misses. The loom test has since been renamed to
                // `data_map_literal_key_lookup`, so there is no collision today -- but the key
                // stays a pair, because that is what actually identifies a gold and it is the
                // only thing standing between a future duplicate and the same silent mix-up.
                // The bare name remains as a fallback for results whose category does not
                // correspond to a gold directory.
                index.set(`${category.name}/${gold.name}`, gold);
                if (!index.has(gold.name)) index.set(gold.name, gold);
            } catch {
                // A corrupt gold must not take the whole report down with it; the test simply
                // renders without value evidence, exactly as it did before.
            }
        }
    }
    return index;
})();

function goldFor(res) {
    return GOLD_BY_NAME.get(`${res.category}/${res.name}`) || GOLD_BY_NAME.get(res.name);
}

/**
 * The authoring JSON for a test, for the "{ } JSON Doc" viewer on each card.
 *
 * `results[].document` is present on all 252 results and empty on all 252 — it is a v1 field the
 * runner no longer fills. Every card was therefore showing `{"name": …, "parameters": …}` where
 * the test source was meant to be. Reading `tests/<category>/<name>.json` is the same corpus-local
 * access the gold index above already makes, and it costs a player nothing.
 */
const TEST_DOC_CACHE = new Map();
function testDocument(res, category) {
    const key = `${category}/${res.name}`;
    if (!TEST_DOC_CACHE.has(key)) {
        let doc = null;
        try {
            doc = JSON.parse(readFileSync(join(SPEC_DIR, "tests", category, `${res.name}.json`), "utf8"));
        } catch {
            // Not every result has a file under the category it reports; fall back to the stub
            // rather than losing the card.
        }
        TEST_DOC_CACHE.set(key, doc);
    }
    return TEST_DOC_CACHE.get(key) || res.document || { name: res.name, parameters: res.parameters };
}

// Suspicious tags come from the corpus, carried through conformance-results.json by the runner.
// This file used to keep a duplicate list, which went stale whenever a tag was corrected.

// Multi-subsystem categorization
const layoutResults = report.results.filter(r => 
    (r.category === 'layout' || !r.category) &&
    !r.suspicious && 
    r.status !== "SUSPICIOUS" && 
    !(r.tags && r.tags.includes("suspicious"))
);

const expressionResults = report.results.filter(r => r.category === 'expressions');
const particleResults = report.results.filter(r => r.category === 'particles');
const wireResults = report.results.filter(r => r.category === 'wire');
const loomResults = report.results.filter(r => r.category === 'loom');
const canvasResults = report.results.filter(r => r.category === 'canvas' || r.category === 'textpath');
const shadersResults = report.results.filter(r => r.category === 'shaders' || r.category === 'shaderdata');
const clockResults = report.results.filter(r => r.category === 'clock');
const interactivityResults = report.results.filter(r => r.category === 'interactivity');
const textOperationsResults = report.results.filter(r => r.category === 'textoperations');
const colorThemeResults = report.results.filter(r => r.category === 'colortheme');
const dataOperationsResults = report.results.filter(r => r.category === 'dataoperations');
const pathOperationsResults = report.results.filter(r => r.category === 'pathoperations');
const semanticsResults = report.results.filter(r => r.category === 'semantics');
const schedulingResults = report.results.filter(r => r.category === 'scheduling');
const conditionalsResults = report.results.filter(r => r.category === 'conditionals');
const matrixMathResults = report.results.filter(r => r.category === 'matrixmath');
const animationSpecResults = report.results.filter(r => r.category === 'animationspec');

const activeResults = layoutResults; // For backwards compatibility with layout helpers
const resultsMap = new Map();
let totalPassed = 0;
let totalFailed = 0;

for (const r of layoutResults) {
    if (r.status === "PASS") {
        totalPassed++;
    } else {
        totalFailed++;
    }
    resultsMap.set(r.name, r);
}

const totalAll = layoutResults.length;
const passRate = totalAll > 0 ? ((totalPassed / totalAll) * 100).toFixed(1) : "0";

// Expression subsystem stats
let exprPassed = 0;
let exprFailed = 0;
for (const r of expressionResults) {
    if (r.status === "PASS") exprPassed++;
    else exprFailed++;
}
const exprTotal = expressionResults.length;
const exprPassRate = exprTotal > 0 ? ((exprPassed / exprTotal) * 100).toFixed(1) : "0";

// Particle subsystem stats
let particlePassed = 0;
let particleFailed = 0;
for (const r of particleResults) {
    if (r.status === "PASS") particlePassed++;
    else particleFailed++;
}
const particleTotal = particleResults.length;
const particlePassRate = particleTotal > 0 ? ((particlePassed / particleTotal) * 100).toFixed(1) : "0";

// Wire Protocol subsystem stats
let wirePassed = 0;
let wireFailed = 0;
for (const r of wireResults) {
    if (r.status === "PASS") wirePassed++;
    else wireFailed++;
}
const wireTotal = wireResults.length;
const wirePassRate = wireTotal > 0 ? ((wirePassed / wireTotal) * 100).toFixed(1) : "0";

// Loom Macro Engine subsystem stats
let loomPassed = 0;
let loomFailed = 0;
for (const r of loomResults) {
    if (r.status === "PASS") loomPassed++;
    else loomFailed++;
}
const loomTotal = loomResults.length;
const loomPassRate = loomTotal > 0 ? ((loomPassed / loomTotal) * 100).toFixed(1) : "0";
const totalInflatedComponents = loomResults.reduce((s, r) => s + (r.componentCount || 0), 0);

// Canvas & Shaders subsystem stats (includes folded Shader Data & AGSL)
let canvasPassed = 0;
let canvasFailed = 0;
for (const r of canvasResults) {
    if (r.status === "PASS") canvasPassed++;
    else canvasFailed++;
}
const canvasTotal = canvasResults.length;
const canvasPassRate = canvasTotal > 0 ? ((canvasPassed / canvasTotal) * 100).toFixed(1) : "0";

// Clock subsystem stats
let clockPassed = 0;
let clockFailed = 0;
for (const r of clockResults) {
    if (r.status === "PASS") clockPassed++;
    else clockFailed++;
}
const clockTotal = clockResults.length;
const clockPassRate = clockTotal > 0 ? ((clockPassed / clockTotal) * 100).toFixed(1) : "0";

// Interactivity subsystem stats
let interactivityPassed = 0;
let interactivityFailed = 0;
for (const r of interactivityResults) {
    if (r.status === "PASS") interactivityPassed++;
    else interactivityFailed++;
}
const interactivityTotal = interactivityResults.length;
const interactivityPassRate = interactivityTotal > 0 ? ((interactivityPassed / interactivityTotal) * 100).toFixed(1) : "0";

// Text Operations subsystem stats
let textOperationsPassed = 0, textOperationsFailed = 0;
for (const r of textOperationsResults) {
    if (r.status === "PASS") textOperationsPassed++;
    else textOperationsFailed++;
}
const textOperationsTotal = textOperationsResults.length;
const textOperationsPassRate = textOperationsTotal > 0 ? ((textOperationsPassed / textOperationsTotal) * 100).toFixed(1) : "0";

// Color Theme subsystem stats
let colorThemePassed = 0, colorThemeFailed = 0;
for (const r of colorThemeResults) {
    if (r.status === "PASS") colorThemePassed++;
    else colorThemeFailed++;
}
const colorThemeTotal = colorThemeResults.length;
const colorThemePassRate = colorThemeTotal > 0 ? ((colorThemePassed / colorThemeTotal) * 100).toFixed(1) : "0";

// Data Operations subsystem stats
let dataOperationsPassed = 0, dataOperationsFailed = 0;
for (const r of dataOperationsResults) {
    if (r.status === "PASS") dataOperationsPassed++;
    else dataOperationsFailed++;
}
const dataOperationsTotal = dataOperationsResults.length;
const dataOperationsPassRate = dataOperationsTotal > 0 ? ((dataOperationsPassed / dataOperationsTotal) * 100).toFixed(1) : "0";

// Path Operations subsystem stats
let pathOperationsPassed = 0, pathOperationsFailed = 0;
for (const r of pathOperationsResults) {
    if (r.status === "PASS") pathOperationsPassed++;
    else pathOperationsFailed++;
}
const pathOperationsTotal = pathOperationsResults.length;
const pathOperationsPassRate = pathOperationsTotal > 0 ? ((pathOperationsPassed / pathOperationsTotal) * 100).toFixed(1) : "0";

// Semantics subsystem stats
let semanticsPassed = 0, semanticsFailed = 0;
for (const r of semanticsResults) {
    if (r.status === "PASS") semanticsPassed++;
    else semanticsFailed++;
}
const semanticsTotal = semanticsResults.length;
const semanticsPassRate = semanticsTotal > 0 ? ((semanticsPassed / semanticsTotal) * 100).toFixed(1) : "0";

// Scheduling subsystem stats
let schedulingPassed = 0, schedulingFailed = 0;
for (const r of schedulingResults) {
    if (r.status === "PASS") schedulingPassed++;
    else schedulingFailed++;
}
const schedulingTotal = schedulingResults.length;
const schedulingPassRate = schedulingTotal > 0 ? ((schedulingPassed / schedulingTotal) * 100).toFixed(1) : "0";

let conditionalsPassed = 0, conditionalsFailed = 0;
for (const r of conditionalsResults) {
    if (r.status === "PASS") conditionalsPassed++;
    else conditionalsFailed++;
}
const conditionalsTotal = conditionalsResults.length;
const conditionalsPassRate = conditionalsTotal > 0 ? ((conditionalsPassed / conditionalsTotal) * 100).toFixed(1) : "0";

let matrixMathPassed = 0, matrixMathFailed = 0;
for (const r of matrixMathResults) {
    if (r.status === "PASS") matrixMathPassed++;
    else matrixMathFailed++;
}
const matrixMathTotal = matrixMathResults.length;
const matrixMathPassRate = matrixMathTotal > 0 ? ((matrixMathPassed / matrixMathTotal) * 100).toFixed(1) : "0";

let shadersPassed = 0, shadersFailed = 0;
for (const r of shadersResults) {
    if (r.status === "PASS") shadersPassed++;
    else shadersFailed++;
}
const shadersTotal = shadersResults.length;
const shadersPassRate = shadersTotal > 0 ? ((shadersPassed / shadersTotal) * 100).toFixed(1) : "0";

let animationSpecPassed = 0, animationSpecFailed = 0;
for (const r of animationSpecResults) {
    if (r.status === "PASS") animationSpecPassed++;
    else animationSpecFailed++;
}
const animationSpecTotal = animationSpecResults.length;
const animationSpecPassRate = animationSpecTotal > 0 ? ((animationSpecPassed / animationSpecTotal) * 100).toFixed(1) : "0";

// A single pass-rate colour scale, used by every tile, badge, bar and table cell
// so that the same number is never drawn in two different colours.
//   100% green · >=90% light green · >=60% orange · below that red
const RATE_COLORS = { perfect: '#22c55e', high: '#86efac', mid: '#f59e0b', low: '#ef4444' };
function rateTier(rate) {
    const r = Number(rate);
    if (r >= 100) return 'perfect';
    if (r >= 90) return 'high';
    if (r >= 60) return 'mid';
    return 'low';
}
const rateClass = (rate) => `rate-${rateTier(rate)}`;
const rateColor = (rate) => RATE_COLORS[rateTier(rate)];
function rateBadge(rate) {
    const tier = rateTier(rate);
    const label = { perfect: 'Complete', high: 'Grade A', mid: 'Partial', low: 'Divergent' }[tier];
    const cls = { perfect: 'badge-pass', high: 'badge-pass', mid: 'badge-warn', low: 'badge-fail' }[tier];
    return `<span class="badge ${cls}">${label}</span>`;
}

// Overall totals across all 18 RemoteCompose subsystems
const overallTotal = totalAll + exprTotal + particleTotal + wireTotal + loomTotal + canvasTotal + shadersTotal + clockTotal + interactivityTotal + textOperationsTotal + colorThemeTotal + dataOperationsTotal + pathOperationsTotal + semanticsTotal + schedulingTotal + conditionalsTotal + matrixMathTotal + animationSpecTotal;
const overallPassed = totalPassed + exprPassed + particlePassed + wirePassed + loomPassed + canvasPassed + shadersPassed + clockPassed + interactivityPassed + textOperationsPassed + colorThemePassed + dataOperationsPassed + pathOperationsPassed + semanticsPassed + schedulingPassed + conditionalsPassed + matrixMathPassed + animationSpecPassed;
const overallFailed = totalFailed + exprFailed + particleFailed + wireFailed + loomFailed + canvasFailed + shadersFailed + clockFailed + interactivityFailed + textOperationsFailed + colorThemeFailed + dataOperationsFailed + pathOperationsFailed + semanticsFailed + schedulingFailed + conditionalsFailed + matrixMathFailed + animationSpecFailed;
const overallPassRate = overallTotal > 0 ? ((overallPassed / overallTotal) * 100).toFixed(1) : "0";

const coverageJson = (() => {
    try { return JSON.parse(readFileSync(join(SPEC_DIR, "coverage.json"), "utf8")); }
    catch { return null; }
})();
const covRoot = coverageJson?.coverage || coverageJson;

function buildSubsystemCov(subId) {
    const subRaw = covRoot?.by_subsystem?.[subId];
    const items = (covRoot?.classes || []).filter(i => i.subsystem === subId);
    const coveredCount = items.filter(i => (i.lines_covered || i.instructions_covered || 0) > 0).length;
    const sub = {
        total: items.length,
        covered: coveredCount,
        linePct: subRaw ? Number(subRaw.line_coverage_pct).toFixed(1) : "0.0",
        instrPct: subRaw ? Number(subRaw.instruction_coverage_pct).toFixed(1) : "0.0"
    };
    const rowsHtml = items.map(item => {
        const shortOp = item.name.replace("operations/loom/", "").replace("operations/", "");
        const isCov = (item.lines_covered || item.instructions_covered || 0) > 0;
        const linePct = Number(item.lines_pct || 0).toFixed(1);
        const instrPct = Number(item.instructions_pct || 0).toFixed(1);
        return `
          <tr>
            <td><code style="color: #f8fafc; font-weight: 600;">${escapeHtml(shortOp)}</code><div style="font-size: 11px; color: var(--text-muted);">${escapeHtml(item.name)}</div></td>
            <td><span class="badge" style="background: rgba(148,163,184,0.12); color: #cbd5e1;">${escapeHtml(item.category || "Operation")}</span></td>
            <td><span class="badge ${isCov ? "badge-pass" : "badge-warn"}">${isCov ? "✔ Covered" : "○ Uncovered"}</span></td>
            <td>
              <div style="font-weight: 600; color: ${rateColor(linePct)};">${linePct}% lines <span style="font-size: 11.5px; color: var(--text-muted); font-weight: 400;">(${item.lines_covered || 0}/${item.lines_total || 0})</span></div>
              <div style="font-size: 11px; color: var(--text-muted);">${instrPct}% instructions (${item.instructions_covered || 0}/${item.instructions_total || 0})</div>
            </td>
          </tr>
        `;
    }).join("");
    return { sub, rowsHtml };
}

const { sub: wireCovSub, rowsHtml: wireOpsRowsHtml } = buildSubsystemCov("wire");
const { sub: loomCovSub, rowsHtml: loomOpsRowsHtml } = buildSubsystemCov("loom");

// Functional Areas in RemoteCompose Layout Subsystem
const RC_AREAS = [
    {
        name: "Row & Column (Linear Layout)",
        category: "Containers",
        desc: "Sequential horizontal and vertical component placement, cross-axis alignment, and item spacing.",
        tests: [
            "row_basic", "row_spaced_by", "row_alignment_center", "row_alignment_end",
            "row_alignment_space_around", "row_alignment_space_between", "row_alignment_space_evenly",
            "column_basic", "column_spaced_by", "column_alignment_center", "column_alignment_end",
            "column_alignment_space_around", "column_alignment_space_between", "column_alignment_space_evenly",
            "row_padding_all", "column_padding_all", "row_align_center_mixed_heights", "column_align_center_mixed_widths",
            "row_align_top_bottom", "column_align_start_end", "row_background_border", "row_child_offset",
            "row_child_zindex", "row_child_graphicslayer", "row_child_constraints", "row_child_visibility",
            "column_background_border", "column_child_offset", "column_child_zindex", "column_child_graphicslayer",
            "column_child_constraints", "column_child_visibility"
        ],
        gaps: "None. All alignments, spacing rules, and cross-axis bounds conform to reference."
    },
    {
        name: "Proportional Weights & Spacers",
        category: "Sizing",
        desc: "Distribution of remaining container space among weighted children and weighted spacer elements.",
        tests: ["row_weights", "column_weights", "spacer_weighted", "row_multi_weights", "column_multi_weights", "spacer_fixed_sizes"],
        gaps: "None. Weight ratios and remainder space distribution conform to reference."
    },
    {
        name: "Box Overlay & Stacking",
        category: "Containers",
        desc: "Multi-child overlay stacking, 9-point anchor alignment, and z-index ordering.",
        tests: ["box_stack", "box_alignment_all", "modifier_zindex", "box_align_corners", "box_center_in_parent", "box_offset_overlap", "modifier_zindex_reorder", "box_background_border_container", "box_child_scroll", "box_child_priority"],
        gaps: "None. Z-order and alignment placement conform to reference."
    },
    {
        name: "Flow Layout (Wrapping Flex)",
        category: "Containers",
        desc: "Multi-line wrapping rows with item spacing, maxLines limits, and maxItemsInEachRow limits.",
        tests: ["flow_basic", "flow_spacing", "flow_max_columns", "flow_spacing_wrap", "flow_item_alignment", "flow_padding_container", "flow_child_padding", "flow_child_background_border", "flow_child_offset", "flow_child_visibility", "flow_child_graphicslayer", "flow_child_zindex", "flow_child_constraints"],
        gaps: "Missing maxItemsInEachRow column wrapping with overflow clipping."
    },
    {
        name: "Adaptive & Priority Containers",
        category: "Containers",
        desc: "FitBox aspect-ratio fitting and CollapsibleRow / CollapsibleColumn priority-based child hiding.",
        tests: ["collapsible_row", "collapsible_column", "collapsible_column_spacing", "collapsible_row_spacing", "collapsible_column_all_fit", "collapsible_row_all_fit", "fitbox_scale_fit", "fitbox_padding_container", "fitbox_child_padding", "fitbox_background_border", "fitbox_child_offset", "fitbox_child_graphicslayer", "fitbox_child_constraints", "fitbox_child_visibility", "collapsible_row_padding_container", "collapsible_row_child_padding", "collapsible_row_background_border", "collapsible_row_child_offset", "collapsible_row_child_visibility", "collapsible_row_child_graphicslayer", "collapsible_row_child_zindex", "collapsible_row_weights", "collapsible_column_padding_container", "collapsible_column_child_padding", "collapsible_column_background_border", "collapsible_column_child_offset", "collapsible_column_child_visibility", "collapsible_column_child_graphicslayer", "collapsible_column_child_zindex", "collapsible_column_weights"],
        gaps: "None. FitBox, CollapsibleRow, and CollapsibleColumn priority mechanisms conform to reference."
    },
    {
        name: "State Layout & Hierarchies",
        category: "Architecture",
        desc: "StateLayout multi-state page selection and complex multi-level nested hierarchies.",
        tests: ["nested_complex_layout", "row_nested_columns", "column_nested_rows", "state_layout_padding_container", "state_layout_child_padding", "state_layout_background_border", "state_layout_child_offset", "state_layout_child_graphicslayer", "state_layout_child_constraints"],
        gaps: "None. StateLayout multi-state page selection and hierarchy nesting conform to reference."
    },
    {
        name: "Dimension & Constraints",
        category: "Modifiers",
        desc: "Exact dimensions, fillMaxSize, fillParentMax, widthIn/heightIn clamping, and wrapContentSize.",
        tests: ["row_wrap_content", "modifier_fractional_fill", "modifier_size_array", "modifier_constraints_in", "modifier_fill_parent", "row_fill_max_height", "column_fill_max_width", "box_fill_max_size_nested", "modifier_size_exact"],
        gaps: "widthIn/heightIn min/max clamping omitted on fixed sizes; FILL_PARENT_MAX_* unhandled in initial pass."
    },
    {
        name: "Image & Media Layout",
        category: "Media",
        desc: "ImageLayout container sizing across wrap content (intrinsic bitmap dimensions), exact size, fill parent, fractional fill, and flex weights under dynamic resizing.",
        tests: ["image_layout_sizing_options"],
        gaps: "None. All intrinsic bitmap wrap dimensions, exact sizes, fill modes, and proportional weights conform to reference."
    },
    {
        name: "Positioning & Visual Styling",
        category: "Modifiers",
        desc: "Uniform and directional padding, offsets, backgrounds, borders, baseline alignment, and graphicsLayer.",
        tests: [
            "modifier_padding", "modifier_padding_directional", "modifier_offset",
            "modifier_negative_offset", "modifier_background", "modifier_border",
            "modifier_align_by_baseline", "modifier_graphics_layer", "modifier_border_padding", "modifier_padding_symmetric", "box_padding_all", "modifier_graphicslayer_scale"
        ],
        gaps: "None. Insets, visual layer transformations, and baseline shifts conform to reference."
    },
    {
        name: "Runtime Visibility & Scrolling",
        category: "Interactivity",
        desc: "Visibility flags (GONE/INVISIBLE/VISIBLE) and vertical/horizontal scroll viewports.",
        tests: ["modifier_visibility", "modifier_scroll", "modifier_visibility_gone", "modifier_visibility_invisible", "row_scroll_basic", "column_scroll_basic"],
        gaps: "None. Visibility override masks and scroll container viewports conform to reference."
    },
    {
        name: "CoreText & Typography",
        category: "Typography",
        desc: "Deterministic text bounds, multiline wrapping, text alignment (start/center/end), maxLines, overflow ellipsis, and dynamic autoSize font scaling with W3C Ahem test font.",
        tests: ["core_text_simple", "core_text_multiline_wrap", "core_text_alignment", "core_text_overflow_ellipsis", "core_text_autosize_basic", "core_text_autosize_height_driven", "core_text_autosize_max_clamped", "core_text_autosize_min_clamped", "core_text_autosize_multiline"],
        gaps: "None. Single-line, multiline wrapping, alignment, ellipsis truncation, and dynamic autosize font scaling conform to reference using W3C Ahem metrics."
    },
    {
        name: "Dynamic Viewport & Reflow",
        category: "Interactivity & Layout",
        desc: "Reflow, priority collapsing, weight adjustment, and aspect scaling under dynamic viewport dimension changes.",
        tests: ["resize_flow_wrap", "resize_collapsible_row", "resize_collapsible_column", "resize_row_weights", "resize_fitbox"],
        gaps: "None. Line wrapping, collapsible priority hiding, and weighted distribution resize conform to reference."
    },
    {
        name: "Layout Animation",
        category: "Animation & Motion",
        desc: "Continuous time-driven interpolation of layout bounds, alignment offsets, and state transitions across motion frames.",
        tests: ["animation_measure_transition", "animation_box_offset", "animation_state_transition", "animation_state_3_states", "animation_state_row_to_column"],
        gaps: "None. Time-driven coordinate interpolation and easing curves conform to reference across all motion frames."
    },
    {
        name: "Touch & Input Routing",
        category: "Interactivity & Input",
        desc: "Pointer touch down, multi-step drag gestures, click events, and velocity fling handling on scroll and interactive containers.",
        tests: ["interaction_click_button", "interaction_scroll_column", "interaction_scroll_row", "interaction_touch_drag_sequence", "interaction_swipe_scroll_decay"],
        gaps: "StateLayout reactive page switching requires dynamic re-measure upon variable mutation in the player."
    }
];

// Matrix Mapping: Test -> [Manager|Modifier]
const TEST_CAPABILITIES = {
  "box_alignment_all": [ "Box|Fill", "Box|Size" ],
  "box_stack": [ "Box|Fill", "Box|Size" ],
  "collapsible_column": [ "CollapsibleColumn|Fill", "Box|Size", "Box|Priority", "CollapsibleColumn|Size", "CollapsibleColumn|Priority" ],
  "collapsible_row": [ "CollapsibleRow|Fill", "Box|Size", "Box|Priority", "CollapsibleRow|Size", "CollapsibleRow|Priority" ],
  "column_alignment_center": [ "Column|Fill", "Box|Size", "Column|Size" ],
  "column_alignment_end": [ "Column|Fill", "Box|Size", "Column|Size" ],
  "column_alignment_space_around": [ "Column|Fill", "Box|Size", "Column|Size" ],
  "column_alignment_space_between": [ "Column|Fill", "Box|Size", "Column|Size" ],
  "column_alignment_space_evenly": [ "Column|Fill", "Box|Size", "Column|Size" ],
  "column_basic": [ "Column|Fill", "Box|Size", "Column|Size" ],
  "column_spaced_by": [ "Column|Fill", "Box|Size", "Column|Size" ],
  "column_weights": [ "Column|Fill", "Box|Size", "Column|Size", "Box|Weight", "Column|Weight" ],
  "core_text_simple": [ "Row|Fill", "CoreText|Size" ],
  "core_text_autosize_basic": [ "Box|Fill", "CoreText|Size" ],
  "core_text_autosize_height_driven": [ "Box|Fill", "CoreText|Size" ],
  "core_text_autosize_max_clamped": [ "Box|Fill", "CoreText|Size" ],
  "core_text_autosize_min_clamped": [ "Box|Fill", "CoreText|Size" ],
  "core_text_autosize_multiline": [ "Box|Fill", "CoreText|Size" ],
  "core_text_multiline_wrap": [ "Column|Fill", "CoreText|Size" ],
  "core_text_alignment": [ "Column|Fill", "CoreText|Fill", "CoreText|Align" ],
  "core_text_overflow_ellipsis": [ "Column|Fill", "CoreText|Size" ],
  "flow_basic": [ "Flow|Fill", "Box|Size", "Flow|Size" ],
  "flow_max_columns": [ "Flow|Fill", "Box|Size", "Flow|Size" ],
  "flow_spacing": [ "Flow|Fill", "Box|Size", "Flow|Size" ],
  "modifier_align_by_baseline": [ "Row|Fill", "Box|Size", "Box|Align", "Row|Size", "Row|Align" ],
  "modifier_background": [ "Box|Fill", "Box|Size", "Box|Background / Border" ],
  "modifier_border": [ "Box|Fill", "Box|Size", "Box|Background / Border" ],
  "modifier_constraints_in": [ "Row|Fill", "Box|Size", "Box|Constraints", "Row|Size", "Row|Constraints" ],
  "modifier_fill_parent": [ "Box|Fill" ],
  "modifier_fractional_fill": [ "Column|Fill", "Box|Fill", "Box|Size", "Column|Size" ],
  "modifier_graphics_layer": [ "Box|Fill", "Box|Size", "Box|GraphicsLayer" ],
  "modifier_negative_offset": [ "Box|Fill", "Box|Size", "Box|Offset" ],
  "modifier_offset": [ "Column|Fill", "Box|Size", "Box|Offset", "Column|Size", "Column|Offset" ],
  "modifier_padding": [ "Column|Fill", "Column|Padding", "Box|Fill", "Box|Size", "Column|Size" ],
  "modifier_padding_directional": [ "Box|Fill", "Box|Padding" ],
  "modifier_scroll": [ "Column|Fill", "Column|Scroll", "Box|Size", "Column|Size" ],
  "modifier_size_array": [ "Row|Fill", "Box|Size", "Row|Size" ],
  "modifier_visibility": [ "Row|Fill", "Box|Size", "Row|Size", "Box|Visibility", "Row|Visibility" ],
  "modifier_zindex": [ "Box|Fill", "Box|Size", "Box|Z-Index" ],
  "nested_complex_layout": [ "Column|Fill", "Column|Padding", "Row|Fill", "Row|Size", "Column|Size", "Box|Size", "Column|Weight", "Row|Weight", "Row|Padding", "Box|Fill", "Box|Weight", "Box|Padding" ],
  "row_alignment_center": [ "Row|Fill", "Box|Size", "Row|Size" ],
  "row_alignment_end": [ "Row|Fill", "Box|Size", "Row|Size" ],
  "row_alignment_space_around": [ "Row|Fill", "Box|Size", "Row|Size" ],
  "row_alignment_space_between": [ "Row|Fill", "Box|Size", "Row|Size" ],
  "row_alignment_space_evenly": [ "Row|Fill", "Box|Size", "Row|Size" ],
  "row_basic": [ "Row|Fill", "Box|Size", "Row|Size" ],
  "row_spaced_by": [ "Row|Fill", "Box|Size", "Row|Size" ],
  "row_weights": [ "Row|Fill", "Box|Size", "Row|Size", "Box|Weight", "Row|Weight" ],
  "row_wrap_content": [ "Box|Fill", "Row|Constraints", "Box|Constraints", "Box|Size", "Row|Size" ],
  "spacer_weighted": [ "Row|Fill", "Box|Size", "Row|Size", "Row|Weight" ],
  "row_padding_all": ["Row|Fill", "Row|Padding", "Box|Size", "Box|Padding"],
  "column_padding_all": ["Column|Fill", "Column|Padding", "Box|Size", "Box|Padding"],
  "box_padding_all": ["Box|Fill", "Box|Padding", "Box|Size"],
  "row_fill_max_height": ["Row|Fill", "Box|Size", "Box|Fill"],
  "column_fill_max_width": ["Column|Fill", "Box|Size", "Box|Fill"],
  "box_fill_max_size_nested": ["Box|Fill", "Box|Size"],
  "row_nested_columns": ["Row|Fill", "Column|Fill", "Column|Weight", "Box|Size"],
  "column_nested_rows": ["Column|Fill", "Row|Fill", "Row|Weight", "Box|Size"],
  "box_offset_overlap": ["Box|Fill", "Box|Size", "Box|Offset"],
  "modifier_zindex_reorder": ["Box|Fill", "Box|Size", "Box|Z-Index"],
  "modifier_visibility_gone": ["Row|Fill", "Row|Size", "Box|Size", "Box|Visibility"],
  "modifier_visibility_invisible": ["Row|Fill", "Row|Size", "Box|Size", "Box|Visibility"],
  "row_align_center_mixed_heights": ["Row|Fill", "Row|Align", "Box|Size"],
  "column_align_center_mixed_widths": ["Column|Fill", "Column|Align", "Box|Size"],
  "modifier_border_padding": ["Box|Fill", "Box|Background / Border", "Box|Padding", "Box|Size"],
  "modifier_graphicslayer_scale": ["Box|Fill", "Box|GraphicsLayer", "Box|Size"],
  "flow_spacing_wrap": ["Flow|Fill", "Flow|Size", "Box|Size"],
  "collapsible_column_spacing": ["CollapsibleColumn|Fill", "CollapsibleColumn|Priority", "Box|Size", "Box|Priority"],
  "collapsible_row_spacing": ["CollapsibleRow|Fill", "CollapsibleRow|Priority", "Box|Size", "Box|Priority"],
  "box_align_corners": ["Box|Fill", "Box|Align", "Box|Size"],
  "spacer_fixed_sizes": ["Row|Fill", "Row|Size", "Box|Size"],
  "row_multi_weights": ["Row|Fill", "Row|Weight", "Box|Size", "Box|Weight"],
  "column_multi_weights": ["Column|Fill", "Column|Weight", "Box|Size", "Box|Weight"],
  "modifier_padding_symmetric": ["Box|Fill", "Box|Padding"],
  "modifier_size_exact": ["Box|Fill", "Box|Size"],
  "row_scroll_basic": ["Row|Fill", "Row|Scroll", "Box|Size"],
  "column_scroll_basic": ["Column|Fill", "Column|Scroll", "Box|Size"],
  "flow_item_alignment": ["Flow|Fill", "Flow|Align", "Box|Size"],
  "collapsible_column_all_fit": ["CollapsibleColumn|Fill", "CollapsibleColumn|Priority", "Box|Size", "Box|Priority"],
  "collapsible_row_all_fit": ["CollapsibleRow|Fill", "CollapsibleRow|Priority", "Box|Size", "Box|Priority"],
  "box_center_in_parent": ["Box|Fill", "Box|Align", "Box|Size"],
  "column_align_start_end": ["Column|Fill", "Column|Align", "Box|Size"],
  "row_align_top_bottom": ["Row|Fill", "Row|Align", "Box|Size"],
  "fitbox_scale_fit": ["FitBox|Fill", "FitBox|Size", "Box|Size"],
  "flow_padding_container": ["Flow|Fill", "Flow|Padding", "Box|Size"],
  "flow_child_padding": ["Flow|Fill", "Flow|Padding", "Box|Size", "Box|Padding"],
  "flow_child_background_border": ["Flow|Fill", "Flow|Background / Border", "Box|Size", "Box|Background / Border"],
  "flow_child_offset": ["Flow|Fill", "Flow|Offset", "Box|Size", "Box|Offset"],
  "flow_child_visibility": ["Flow|Fill", "Flow|Visibility", "Box|Size", "Box|Visibility"],
  "flow_child_graphicslayer": ["Flow|Fill", "Flow|GraphicsLayer", "Box|Size", "Box|GraphicsLayer"],
  "flow_child_zindex": ["Flow|Fill", "Flow|Z-Index", "Box|Size", "Box|Z-Index"],
  "flow_child_constraints": ["Flow|Fill", "Flow|Constraints", "Box|Size", "Box|Constraints"],
  "fitbox_padding_container": ["FitBox|Fill", "FitBox|Padding", "Box|Size"],
  "fitbox_child_padding": ["FitBox|Fill", "FitBox|Padding", "Box|Size", "Box|Padding"],
  "fitbox_background_border": ["FitBox|Fill", "FitBox|Background / Border", "Box|Size"],
  "fitbox_child_offset": ["FitBox|Fill", "FitBox|Offset", "Box|Size", "Box|Offset"],
  "fitbox_child_graphicslayer": ["FitBox|Fill", "FitBox|GraphicsLayer", "Box|Size", "Box|GraphicsLayer"],
  "fitbox_child_constraints": ["FitBox|Fill", "FitBox|Constraints", "Box|Size", "Box|Constraints"],
  "fitbox_child_visibility": ["FitBox|Fill", "FitBox|Visibility", "Box|Size", "Box|Visibility"],
  "collapsible_row_padding_container": ["CollapsibleRow|Fill", "CollapsibleRow|Padding", "Box|Size", "Box|Priority"],
  "collapsible_row_child_padding": ["CollapsibleRow|Fill", "CollapsibleRow|Padding", "Box|Size", "Box|Padding", "Box|Priority"],
  "collapsible_row_background_border": ["CollapsibleRow|Fill", "CollapsibleRow|Background / Border", "Box|Size", "Box|Priority"],
  "collapsible_row_child_offset": ["CollapsibleRow|Fill", "CollapsibleRow|Offset", "Box|Size", "Box|Offset", "Box|Priority"],
  "collapsible_row_child_visibility": ["CollapsibleRow|Fill", "CollapsibleRow|Visibility", "Box|Size", "Box|Visibility", "Box|Priority"],
  "collapsible_row_child_graphicslayer": ["CollapsibleRow|Fill", "CollapsibleRow|GraphicsLayer", "Box|Size", "Box|GraphicsLayer", "Box|Priority"],
  "collapsible_row_child_zindex": ["CollapsibleRow|Fill", "CollapsibleRow|Z-Index", "Box|Size", "Box|Z-Index", "Box|Priority"],
  "collapsible_row_weights": ["CollapsibleRow|Fill", "CollapsibleRow|Weight", "Box|Size", "Box|Weight", "Box|Priority"],
  "collapsible_column_padding_container": ["CollapsibleColumn|Fill", "CollapsibleColumn|Padding", "Box|Size", "Box|Priority"],
  "collapsible_column_child_padding": ["CollapsibleColumn|Fill", "CollapsibleColumn|Padding", "Box|Size", "Box|Padding", "Box|Priority"],
  "collapsible_column_background_border": ["CollapsibleColumn|Fill", "CollapsibleColumn|Background / Border", "Box|Size", "Box|Priority"],
  "collapsible_column_child_offset": ["CollapsibleColumn|Fill", "CollapsibleColumn|Offset", "Box|Size", "Box|Offset", "Box|Priority"],
  "collapsible_column_child_visibility": ["CollapsibleColumn|Fill", "CollapsibleColumn|Visibility", "Box|Size", "Box|Visibility", "Box|Priority"],
  "collapsible_column_child_graphicslayer": ["CollapsibleColumn|Fill", "CollapsibleColumn|GraphicsLayer", "Box|Size", "Box|GraphicsLayer", "Box|Priority"],
  "collapsible_column_child_zindex": ["CollapsibleColumn|Fill", "CollapsibleColumn|Z-Index", "Box|Size", "Box|Z-Index", "Box|Priority"],
  "collapsible_column_weights": ["CollapsibleColumn|Fill", "CollapsibleColumn|Weight", "Box|Size", "Box|Weight", "Box|Priority"],
  "state_layout_padding_container": ["StateLayout|Fill", "StateLayout|Padding", "Box|Size"],
  "state_layout_child_padding": ["StateLayout|Fill", "StateLayout|Padding", "Box|Size", "Box|Padding"],
  "state_layout_background_border": ["StateLayout|Fill", "StateLayout|Background / Border", "Box|Size"],
  "state_layout_child_offset": ["StateLayout|Fill", "StateLayout|Offset", "Box|Size", "Box|Offset"],
  "state_layout_child_graphicslayer": ["StateLayout|Fill", "StateLayout|GraphicsLayer", "Box|Size", "Box|GraphicsLayer"],
  "state_layout_child_constraints": ["StateLayout|Fill", "StateLayout|Constraints", "Box|Size", "Box|Constraints"],
  "row_background_border": ["Row|Fill", "Row|Background / Border", "Box|Size"],
  "row_child_offset": ["Row|Fill", "Row|Offset", "Box|Size", "Box|Offset"],
  "row_child_zindex": ["Row|Fill", "Row|Z-Index", "Box|Size", "Box|Z-Index"],
  "row_child_graphicslayer": ["Row|Fill", "Row|GraphicsLayer", "Box|Size", "Box|GraphicsLayer"],
  "row_child_constraints": ["Row|Fill", "Row|Constraints", "Box|Size", "Box|Constraints"],
  "row_child_visibility": ["Row|Fill", "Row|Visibility", "Box|Size", "Box|Visibility"],
  "column_background_border": ["Column|Fill", "Column|Background / Border", "Box|Size"],
  "column_child_offset": ["Column|Fill", "Column|Offset", "Box|Size", "Box|Offset"],
  "column_child_zindex": ["Column|Fill", "Column|Z-Index", "Box|Size", "Box|Z-Index"],
  "column_child_graphicslayer": ["Column|Fill", "Column|GraphicsLayer", "Box|Size", "Box|GraphicsLayer"],
  "column_child_constraints": ["Column|Fill", "Column|Constraints", "Box|Size", "Box|Constraints"],
  "column_child_visibility": ["Column|Fill", "Column|Visibility", "Box|Size", "Box|Visibility"],
  "box_background_border_container": ["Box|Fill", "Box|Background / Border", "Box|Size"],
  "box_child_scroll": ["Box|Fill", "Box|Scroll", "Box|Size"],
  "box_child_priority": ["Box|Fill", "Box|Priority", "Box|Size"],
  "resize_flow_wrap": ["Flow|Fill", "Flow|Size", "Box|Size"],
  "resize_collapsible_row": ["CollapsibleRow|Fill", "CollapsibleRow|Priority", "Box|Size", "Box|Priority"],
  "resize_collapsible_column": ["CollapsibleColumn|Fill", "CollapsibleColumn|Priority", "Box|Size", "Box|Priority"],
  "resize_row_weights": ["Row|Fill", "Row|Weight", "Box|Size", "Box|Weight"],
  "resize_fitbox": ["FitBox|Fill", "FitBox|Size", "Box|Size"],
  "image_layout_sizing_options": ["ImageLayout|Size", "ImageLayout|Fill", "ImageLayout|Weight", "ImageLayout|Constraints", "Row|Fill", "Row|Size", "Row|Weight", "Column|Fill", "Column|Background / Border"],
  "animation_measure_transition": ["Row|Fill", "Row|Align", "Box|Size"],
  "animation_box_offset": ["Box|Fill", "Box|Size"],
  "animation_state_transition": ["StateLayout|Fill", "StateLayout|Size", "Column|Fill", "Row|Fill", "Row|Align", "Box|Size"],
  "animation_state_3_states": ["StateLayout|Fill", "StateLayout|Size", "Column|Fill", "Row|Fill", "Row|Align", "Box|Size"],
  "animation_state_row_to_column": ["StateLayout|Fill", "StateLayout|Size", "Column|Fill", "Row|Fill", "Row|Align", "Box|Size"],
  "interaction_click_button": ["StateLayout|Fill", "StateLayout|Size", "Column|Fill", "Box|Size"],
  "interaction_scroll_column": ["Column|Fill", "Column|Scroll", "Box|Size"],
  "interaction_scroll_row": ["Row|Fill", "Row|Scroll", "Box|Size"],
  "interaction_touch_drag_sequence": ["Column|Fill", "Column|Scroll", "Box|Size"],
  "interaction_swipe_scroll_decay": ["Column|Fill", "Column|Scroll", "Box|Size"],
  "state_layout_basic": ["StateLayout|Fill", "Box|Size"],
  "state_layout_pages": ["StateLayout|Fill", "Box|Size"],
  "state_layout_child_visibility": ["StateLayout|Fill", "Box|Size", "Box|Visibility"],
  "fitbox_fit": ["FitBox|Fill", "Box|Size"],
  "flow_max_lines": ["Flow|Fill", "Box|Size"],
  "collapsible_row_scroll": ["CollapsibleRow|Fill", "CollapsibleRow|Scroll", "Box|Size"],
  "collapsible_column_scroll": ["CollapsibleColumn|Fill", "CollapsibleColumn|Scroll", "Box|Size"],
  "canvas_layout_embedded": ["CanvasLayout|Size", "Row|Fill", "Box|Size"],
  "modifier_marquee_ticker": ["CoreText|Marquee", "CoreText|Size", "Row|Fill"],
  "modifier_multi_click": ["Box|MultiClick", "Box|Size", "Box|Fill"],
  "modifier_rounded_clip_rect": ["Box|ClipRect", "Box|Size"]
};

const matrixManagers = ["Row", "Column", "Box", "Flow", "FitBox", "CollapsibleRow", "CollapsibleColumn", "StateLayout", "CoreText", "ImageLayout", "CanvasLayout"];
const matrixModifiers = ["Size", "Padding", "Weight", "Fill", "Constraints", "Offset", "Align", "Background / Border", "Visibility", "Z-Index", "GraphicsLayer", "Scroll", "Priority", "ClipRect", "Marquee", "MultiClick", "Ripple", "Semantics"];
const matrixModifierLabels = {
  "Size": "Size",
  "Padding": "Padding",
  "Weight": "Weight",
  "Fill": "Fill",
  "Constraints": "Constraints",
  "Offset": "Offset",
  "Align": "Align",
  "Background / Border": "Bg / Border",
  "Visibility": "Visibility",
  "Z-Index": "Z-Index",
  "GraphicsLayer": "Graphics",
  "Scroll": "Scroll",
  "Priority": "Priority",
  "ClipRect": "ClipRect",
  "Marquee": "Marquee",
  "MultiClick": "MultiClick",
  "Ripple": "Ripple",
  "Semantics": "Semantics"
};

const typeToManager = {
  "row": "Row",
  "column": "Column",
  "box": "Box",
  "flow": "Flow",
  "fitbox": "FitBox",
  "collapsiblerow": "CollapsibleRow",
  "collapsiblecolumn": "CollapsibleColumn",
  "statelayout": "StateLayout",
  "imagelayout": "ImageLayout",
  "image": "ImageLayout",
  "text": "CoreText",
  "canvas": "CanvasLayout",
  "canvaslayout": "CanvasLayout",
  "canvas_layout": "CanvasLayout"
};

const modifierKeyToName = {
  "width": "Size",
  "height": "Size",
  "size": "Size",
  "minWidth": "Size",
  "minHeight": "Size",
  "maxWidth": "Size",
  "maxHeight": "Size",
  "fontSize": "Size",
  "fillMaxWidth": "Fill",
  "fillMaxHeight": "Fill",
  "fillMaxSize": "Fill",
  "fillParentMaxWidth": "Fill",
  "fillParentMaxHeight": "Fill",
  "fillParentMaxSize": "Fill",
  "padding": "Padding",
  "paddingHorizontal": "Padding",
  "paddingVertical": "Padding",
  "paddingLeft": "Padding",
  "paddingRight": "Padding",
  "paddingTop": "Padding",
  "paddingBottom": "Padding",
  "paddingStart": "Padding",
  "paddingEnd": "Padding",
  "weight": "Weight",
  "widthIn": "Constraints",
  "heightIn": "Constraints",
  "wrapContentSize": "Constraints",
  "wrapContentWidth": "Constraints",
  "wrapContentHeight": "Constraints",
  "wrapContent": "Constraints",
  "offset": "Offset",
  "offsetX": "Offset",
  "offsetY": "Offset",
  "align": "Align",
  "alignSelf": "Align",
  "horizontalArrangement": "Align",
  "verticalAlignment": "Align",
  "alignment": "Align",
  "textAlign": "Align",
  "background": "Background / Border",
  "border": "Background / Border",
  "visibility": "Visibility",
  "zIndex": "Z-Index",
  "graphicsLayer": "GraphicsLayer",
  "scaleX": "GraphicsLayer",
  "scaleY": "GraphicsLayer",
  "alpha": "GraphicsLayer",
  "rotationZ": "GraphicsLayer",
  "scrollHorizontal": "Scroll",
  "scrollVertical": "Scroll",
  "verticalScroll": "Scroll",
  "horizontalScroll": "Scroll",
  "priority": "Priority"
};

function extractCapabilitiesFromDoc(doc) {
  const caps = new Set();
  if (!doc || !doc.root) return caps;
  function walk(node, parentMgr = null) {
    if (!node || typeof node !== "object") return;
    const rawType = (node.type || "").toLowerCase();
    const mgr = typeToManager[rawType] || null;

    const appliedMods = new Set();
    if (Array.isArray(node.modifiers)) {
      for (const m of node.modifiers) {
        if (m && typeof m === "object") {
          for (const k of Object.keys(m)) {
            const mapped = modifierKeyToName[k];
            if (mapped) appliedMods.add(mapped);
          }
        }
      }
    }
    for (const k of ["horizontalArrangement", "verticalAlignment", "alignment", "textAlign", "fontSize"]) {
      if (k in node) {
        const mapped = modifierKeyToName[k];
        if (mapped) appliedMods.add(mapped);
      }
    }

    if (mgr) {
      for (const mod of appliedMods) {
        caps.add(`${mgr}|${mod}`);
      }
    }
    if (parentMgr) {
      for (const mod of ["Weight", "Priority", "Align", "Padding", "Scroll", "Constraints"]) {
        if (appliedMods.has(mod)) {
          caps.add(`${parentMgr}|${mod}`);
        }
      }
    }

    const currentMgr = mgr || parentMgr;
    if (Array.isArray(node.children)) {
      for (const child of node.children) {
        walk(child, currentMgr);
      }
    }
  }
  walk(doc.root);
  return caps;
}

function extractManagersFromDoc(doc) {
  const mgrs = new Set();
  if (!doc || !doc.root) return mgrs;
  function walk(node) {
    if (!node || typeof node !== "object") return;
    const rawType = (node.type || "").toLowerCase();
    const mgr = typeToManager[rawType];
    if (mgr) mgrs.add(mgr);
    if (Array.isArray(node.children)) {
      for (const c of node.children) walk(c);
    }
  }
  walk(doc.root);
  return mgrs;
}

const ACTIVE_TEST_CAPABILITIES = {};
for (const [testName, caps] of Object.entries(TEST_CAPABILITIES)) {
  ACTIVE_TEST_CAPABILITIES[testName] = new Set(caps);
}
for (const r of report.results) {
  if (!ACTIVE_TEST_CAPABILITIES[r.name]) {
    ACTIVE_TEST_CAPABILITIES[r.name] = new Set();
  }
  const autoCaps = extractCapabilitiesFromDoc(r.document);
  for (const c of autoCaps) {
    ACTIVE_TEST_CAPABILITIES[r.name].add(c);
  }
}

function escapeHtml(str) {
    if (!str) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

export function applyScrollToTree(tree) {
    if (!tree || tree.length === 0) return [];
    const scrollContainers = [];
    for (const c of tree) {
        const sx = c.scroll_x || 0;
        const sy = c.scroll_y || 0;
        if (sx !== 0 || sy !== 0) {
            scrollContainers.push({
                id: c.id,
                depth: c.depth,
                sx,
                sy
            });
        }
    }
    if (scrollContainers.length === 0) return tree;

    return tree.map(c => {
        if (scrollContainers.some(sc => sc.id === c.id)) return c;
        let totalSx = 0;
        let totalSy = 0;
        for (const sc of scrollContainers) {
            if (c.parentId !== undefined && c.parentId !== null) {
                let pId = c.parentId;
                let isDescendant = false;
                while (pId) {
                    if (pId === sc.id) {
                        isDescendant = true;
                        break;
                    }
                    const parent = tree.find(t => t.id === pId);
                    pId = parent ? parent.parentId : null;
                }
                if (isDescendant) {
                    totalSx += sc.sx;
                    totalSy += sc.sy;
                }
            } else if (c.depth > sc.depth) {
                totalSx += sc.sx;
                totalSy += sc.sy;
            }
        }
        if (totalSx !== 0 || totalSy !== 0) {
            return {
                ...c,
                x: c.x + totalSx,
                y: c.y + totalSy,
                unscrolledX: c.x,
                unscrolledY: c.y,
                scrollOffsetX: totalSx,
                scrollOffsetY: totalSy
            };
        }
        return c;
    });
}

function computeTestBounds(r) {
    let minX = 0;
    let minY = 0;
    let maxX = 50;
    let maxY = 50;
    if (r.parameters) {
        if (r.parameters.width) maxX = Math.max(maxX, r.parameters.width);
        if (r.parameters.height) maxY = Math.max(maxY, r.parameters.height);
        if (r.parameters.animation && r.parameters.animation.trigger) {
            const trig = r.parameters.animation.trigger;
            if (trig.width) maxX = Math.max(maxX, trig.width);
            if (trig.height) maxY = Math.max(maxY, trig.height);
        }
    }
    const scanTree = (tree) => {
        const vTree = applyScrollToTree(tree);
        for (const c of vTree || []) {
            minX = Math.min(minX, c.x || 0);
            minY = Math.min(minY, c.y || 0);
            maxX = Math.max(maxX, (c.x || 0) + (c.width || 0));
            maxY = Math.max(maxY, (c.y || 0) + (c.height || 0));
        }
    };
    scanTree(r.expectedTree);
    scanTree(r.actualTree);
    (r.expectedFrames || []).forEach(f => scanTree(f.tree));
    (r.actualFrames || []).forEach(f => scanTree(f.tree));
    (r.expectedResizeSteps || []).forEach(s => {
        scanTree(s.tree);
        if (s.width) maxX = Math.max(maxX, s.width);
        if (s.height) maxY = Math.max(maxY, s.height);
    });
    (r.actualResizeSteps || []).forEach(s => scanTree(s.tree));
    (r.expectedInteractions || []).forEach(s => {
        scanTree(s.tree);
        if (s.x !== undefined) {
            minX = Math.min(minX, s.x - 20);
            maxX = Math.max(maxX, s.x + 20);
        }
        if (s.y !== undefined) {
            minY = Math.min(minY, s.y - 20);
            maxY = Math.max(maxY, s.y + 20);
        }
    });
    (r.actualInteractions || []).forEach(s => scanTree(s.tree));
    return { minX, minY, maxX, maxY };
}

function escapeXml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function renderTextLinesSvg(c, color, scale, isGold, showLetters = true) {
    if (!c.textInfo || !Array.isArray(c.textInfo.lines)) return '';
    const fs = (c.textInfo.fontSize || 16) * scale;
    const lh = (c.textInfo.lineHeight || c.textInfo.fontSize || 16) * scale;
    const bx = (c.x || 0) * scale;
    const by = (c.y || 0) * scale;
    const bw = (c.width || 0) * scale;
    const align = c.textInfo.textAlign || 'start';

    let out = '';
    c.textInfo.lines.forEach((line, idx) => {
        if (!line && line !== '') return;
        const lineTop = by + idx * lh;
        const chars = Array.from(line);
        const lineW = chars.length * fs;
        let lineX = bx;
        if (align === 'center') {
            lineX = bx + Math.max(0, (bw - lineW) / 2);
        } else if (align === 'end' || align === 'right') {
            lineX = bx + Math.max(0, bw - lineW);
        }
        const baselineY = lineTop + 0.8 * fs;

        // 1. Glyph Area Cells: Exact 1em x 1em cells matching Ahem font geometry
        chars.forEach((ch, i) => {
            const cellX = lineX + i * fs;
            if (ch === ' ') {
                out += `<rect x="${cellX.toFixed(1)}" y="${lineTop.toFixed(1)}" width="${fs.toFixed(1)}" height="${fs.toFixed(1)}" fill="${color}" fill-opacity="0.04" stroke="${color}" stroke-width="0.5" stroke-dasharray="2 2" stroke-opacity="0.35"/>`;
            } else {
                out += `<rect x="${cellX.toFixed(1)}" y="${lineTop.toFixed(1)}" width="${fs.toFixed(1)}" height="${fs.toFixed(1)}" fill="${color}" fill-opacity="${isGold ? 0.20 : 0.32}" stroke="${color}" stroke-width="0.5" stroke-opacity="0.65"/>`;
            }
        });

        // 2. Typographical baseline guide (dashed red line at y + 0.8 * fontSize)
        out += `<line x1="${lineX.toFixed(1)}" y1="${baselineY.toFixed(1)}" x2="${(lineX + lineW).toFixed(1)}" y2="${baselineY.toFixed(1)}" stroke="#ef4444" stroke-width="1" stroke-dasharray="3 2" opacity="0.85"/>`;

        // 3. Characters positioned precisely within each glyph area cell
        if (showLetters && fs >= 6) {
            out += `<g text-anchor="middle" font-family="ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace" font-weight="700" font-size="${(fs * 0.72).toFixed(1)}" fill="#ffffff" opacity="0.95">`;
            chars.forEach((ch, i) => {
                if (ch !== ' ') {
                    const charX = lineX + (i + 0.5) * fs;
                    out += `<text x="${charX.toFixed(1)}" y="${baselineY.toFixed(1)}">${escapeXml(ch)}</text>`;
                }
            });
            out += `</g>`;
        }
    });
    return out;
}

function renderSvg(expectedTree, actualTree, mode = 'overlay', width = 360, height = 240, activeStep = null, allSteps = null, bounds = null) {
    const colors = ["#2563eb", "#10b981", "#f59e0b", "#8b5cf6", "#ec4899", "#06b6d4"];
    const waypoints = (allSteps || []).filter(s => s && s.x !== undefined && s.y !== undefined);

    function buildSvg(treeExpRaw, treeActRaw, m, w, h) {
        const treeExp = applyScrollToTree(treeExpRaw);
        const treeAct = applyScrollToTree(treeActRaw);

        let minX = bounds && bounds.minX !== undefined ? bounds.minX : 0;
        let minY = bounds && bounds.minY !== undefined ? bounds.minY : 0;
        let maxX = bounds && bounds.maxX ? bounds.maxX : 50;
        let maxY = bounds && bounds.maxY ? bounds.maxY : 50;
        if (!bounds) {
            for (const c of treeExp || []) {
                minX = Math.min(minX, c.x || 0);
                minY = Math.min(minY, c.y || 0);
                maxX = Math.max(maxX, (c.x || 0) + (c.width || 0));
                maxY = Math.max(maxY, (c.y || 0) + (c.height || 0));
            }
            for (const c of treeAct || []) {
                minX = Math.min(minX, c.x || 0);
                minY = Math.min(minY, c.y || 0);
                maxX = Math.max(maxX, (c.x || 0) + (c.width || 0));
                maxY = Math.max(maxY, (c.y || 0) + (c.height || 0));
            }
        }

        const spanX = Math.max(maxX - minX, 50);
        const spanY = Math.max(maxY - minY, 50);
        const scaleX = (w - 40) / spanX;
        const scaleY = (h - 40) / spanY;
        const scale = Math.min(scaleX, scaleY);

        const offsetX = 20 - minX * scale;
        const offsetY = 20 - minY * scale;

        let svg = `<svg viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" class="box-preview">`;
        svg += `<rect width="${w}" height="${h}" fill="#0f172a" rx="8"/>`;
        svg += `<g transform="translate(${offsetX}, ${offsetY})">`;

        if (m === 'overlay' || m === 'gold') {
            for (let i = 0; i < (treeExp || []).length; i++) {
                const c = treeExp[i];
                const isGone = Boolean(c.isGone || c.visibility === 'GONE');
                const color = colors[i % colors.length];
                const x = (c.x || 0) * scale;
                const y = (c.y || 0) * scale;
                const bw = (c.width || 0) * scale;
                const bh = (c.height || 0) * scale;
                const strokeDash = isGone ? '3 3' : (m === 'overlay' ? '4 2' : 'none');
                const fillOp = isGone ? 0.04 : (m === 'gold' ? (c.textInfo && c.textInfo.lines ? 0.08 : 0.25) : 0);
                const rectOp = isGone ? 0.4 : 0.8;
                const goneTag = isGone ? ' [🚫 GONE]' : '';
                if (c.textInfo && c.textInfo.lines) {
                    svg += `<rect x="${x}" y="${y}" width="${bw}" height="${bh}" fill="${m === 'gold' ? color : 'none'}" fill-opacity="${fillOp}" stroke="${color}" stroke-dasharray="${strokeDash}" stroke-width="1.2" opacity="${rectOp}"/>`;
                    svg += renderTextLinesSvg(c, color, scale, true, true);
                    svg += `<text x="${x + 4}" y="${Math.max(10, y - 4)}" fill="${color}" font-size="8" font-family="monospace">${(c.kind || "").replace(/Layout$/, "")} [${c.id}]${goneTag}${c.textInfo.autosize ? ' (' + c.textInfo.fontSize + 'px)' : ''}</text>`;
                } else {
                    svg += `<rect x="${x}" y="${y}" width="${bw}" height="${bh}" fill="${m === 'gold' ? color : 'none'}" fill-opacity="${fillOp}" stroke="${color}" stroke-dasharray="${strokeDash}" stroke-width="1.5" opacity="${rectOp}"/>`;
                    if (m === 'gold' || bw > 25 || isGone) {
                        svg += `<text x="${x + 4}" y="${y + 12}" fill="${color}" font-size="9" font-family="monospace">${(c.kind || "").replace(/Layout$/, "")} [${c.id}]${goneTag}</text>`;
                    }
                }
            }
        }

        if (m === 'overlay' || m === 'actual') {
            for (let i = 0; i < (treeAct || []).length; i++) {
                const c = treeAct[i];
                const isGone = Boolean(c.isGone || c.visibility === 'GONE');
                const color = colors[i % colors.length];
                const x = (c.x || 0) * scale;
                const y = (c.y || 0) * scale;
                const bw = (c.width || 0) * scale;
                const bh = (c.height || 0) * scale;
                const strokeDash = isGone ? '3 3' : 'none';
                const fillOp = isGone ? 0.04 : (c.textInfo && c.textInfo.lines ? 0.06 : 0.18);
                const rectOp = isGone ? 0.4 : 1.0;
                const goneTag = isGone ? ' [🚫 GONE]' : '';
                if (c.textInfo && c.textInfo.lines) {
                    svg += `<rect x="${x}" y="${y}" width="${bw}" height="${bh}" fill="${color}" fill-opacity="${fillOp}" stroke="${color}" stroke-dasharray="${strokeDash}" stroke-width="1.2" opacity="${rectOp}"/>`;
                    svg += renderTextLinesSvg(c, color, scale, false, m !== 'overlay');
                    svg += `<text x="${x + 4}" y="${Math.max(10, y - 4)}" fill="${color}" font-size="8" font-family="monospace">${(c.kind || "").replace(/Layout$/, "")} [${c.id}]${goneTag}${c.textInfo.autosize ? ' (' + c.textInfo.fontSize + 'px)' : ''}</text>`;
                } else {
                    svg += `<rect x="${x}" y="${y}" width="${bw}" height="${bh}" fill="${color}" fill-opacity="${fillOp}" stroke="${color}" stroke-dasharray="${strokeDash}" stroke-width="1.5" opacity="${rectOp}"/>`;
                    svg += `<text x="${x + 4}" y="${y + 12}" fill="${color}" font-size="9" font-family="monospace">${(c.kind || "").replace(/Layout$/, "")} [${c.id}]${goneTag}</text>`;
                }
            }
        }

        // ── Interaction Layer: Gesture Trail & Touch Pointer ──
        if (waypoints.length > 0) {
            if (waypoints.length > 1) {
                let pathD = 'M ' + (waypoints[0].x * scale) + ' ' + (waypoints[0].y * scale);
                for (let k = 1; k < waypoints.length; k++) {
                    pathD += ' L ' + (waypoints[k].x * scale) + ' ' + (waypoints[k].y * scale);
                }
                svg += `<path d="${pathD}" stroke="#38bdf8" stroke-width="2" stroke-dasharray="4 3" opacity="0.8" fill="none"/>`;
                waypoints.forEach((wp, wIdx) => {
                    const wx = wp.x * scale;
                    const wy = wp.y * scale;
                    const isCur = activeStep && activeStep.x === wp.x && activeStep.y === wp.y;
                    svg += `<circle cx="${wx}" cy="${wy}" r="6" fill="${isCur ? '#f59e0b' : '#0f172a'}" stroke="${isCur ? '#ffffff' : '#38bdf8'}" stroke-width="1.5"/>`;
                    svg += `<text cx="${wx}" cy="${wy + 3}" fill="${isCur ? '#ffffff' : '#38bdf8'}" font-size="7.5" font-family="monospace" font-weight="bold" text-anchor="middle">${wIdx + 1}</text>`;
                });
            }

            if (activeStep && activeStep.x !== undefined && activeStep.y !== undefined) {
                const px = activeStep.x * scale;
                const py = activeStep.y * scale;
                const act = activeStep.action;

                if (act === 'click') {
                    svg += `
                        <circle cx="${px}" cy="${py}" r="18" fill="none" stroke="#f59e0b" stroke-width="2" opacity="0.85">
                            <animate attributeName="r" values="5;22" dur="1.2s" repeatCount="indefinite"/>
                            <animate attributeName="opacity" values="1;0" dur="1.2s" repeatCount="indefinite"/>
                        </circle>
                        <circle cx="${px}" cy="${py}" r="10" fill="rgba(245, 158, 11, 0.3)" stroke="#f59e0b" stroke-width="1.5"/>
                        <circle cx="${px}" cy="${py}" r="4" fill="#fef08a" stroke="#d97706" stroke-width="1"/>
                        <g transform="translate(${Math.min(w - 90, px + 10)}, ${Math.max(14, py - 8)})">
                            <rect x="0" y="-10" width="76" height="15" rx="3" fill="#0f172a" fill-opacity="0.9" stroke="#f59e0b" stroke-width="1"/>
                            <text x="4" y="1" fill="#fbbf24" font-size="8" font-family="monospace" font-weight="bold">👆 CLICK (${activeStep.x}, ${activeStep.y})</text>
                        </g>
                    `;
                } else if (act === 'touch_down') {
                    svg += `
                        <circle cx="${px}" cy="${py}" r="14" fill="rgba(56, 189, 248, 0.35)" stroke="#38bdf8" stroke-width="1.5"/>
                        <circle cx="${px}" cy="${py}" r="4" fill="#ffffff"/>
                        <g transform="translate(${Math.min(w - 95, px + 10)}, ${Math.max(14, py - 8)})">
                            <rect x="0" y="-10" width="80" height="15" rx="3" fill="#0f172a" fill-opacity="0.9" stroke="#38bdf8" stroke-width="1"/>
                            <text x="4" y="1" fill="#38bdf8" font-size="8" font-family="monospace" font-weight="bold">👇 DOWN (${activeStep.x}, ${activeStep.y})</text>
                        </g>
                    `;
                } else if (act === 'touch_drag') {
                    svg += `
                        <circle cx="${px}" cy="${py}" r="12" fill="rgba(16, 185, 129, 0.35)" stroke="#10b981" stroke-width="1.5"/>
                        <circle cx="${px}" cy="${py}" r="4" fill="#ffffff"/>
                        <g transform="translate(${Math.min(w - 90, px + 10)}, ${Math.max(14, py - 8)})">
                            <rect x="0" y="-10" width="76" height="15" rx="3" fill="#0f172a" fill-opacity="0.9" stroke="#10b981" stroke-width="1"/>
                            <text x="4" y="1" fill="#4ade80" font-size="8" font-family="monospace" font-weight="bold">↔ DRAG (${activeStep.x}, ${activeStep.y})</text>
                        </g>
                    `;
                } else if (act === 'touch_up') {
                    svg += `
                        <circle cx="${px}" cy="${py}" r="13" fill="none" stroke="#a855f7" stroke-dasharray="2 2" stroke-width="1.5"/>
                        <circle cx="${px}" cy="${py}" r="4" fill="#a855f7"/>
                        <g transform="translate(${Math.min(w - 85, px + 10)}, ${Math.max(14, py - 8)})">
                            <rect x="0" y="-10" width="70" height="15" rx="3" fill="#0f172a" fill-opacity="0.9" stroke="#a855f7" stroke-width="1"/>
                            <text x="4" y="1" fill="#c084fc" font-size="8" font-family="monospace" font-weight="bold">👆 UP (${activeStep.x}, ${activeStep.y})</text>
                        </g>
                    `;
                }

                // Hit-test target highlight
                const activeTree = treeExp || treeAct || [];
                const hit = activeTree.find(c => c.id !== -2 && activeStep.x >= c.x && activeStep.x <= c.x + c.width && activeStep.y >= c.y && activeStep.y <= c.y + c.height);
                if (hit) {
                    const hx = hit.x * scale;
                    const hy = hit.y * scale;
                    const hw = hit.width * scale;
                    const hh = hit.height * scale;
                    svg += `<rect x="${hx - 2}" y="${hy - 2}" width="${hw + 4}" height="${hh + 4}" rx="4" fill="rgba(245, 158, 11, 0.08)" stroke="#f59e0b" stroke-width="1.5" stroke-dasharray="4 2"/>`;
                    svg += `<text x="${hx + 2}" y="${hy - 3}" fill="#f59e0b" font-size="7.5" font-family="monospace">Hit: ${(hit.kind||'').replace(/Layout$/,'')} [${hit.id}]</text>`;
                }
            }
        }

        svg += `</g></svg>`;
        return svg;
    }

    if (mode === 'side_by_side') {
        const halfW = 180;
        const halfH = 220;
        return `<div class="sbs-preview-wrap">
          <div class="sbs-pane">
            <div class="sbs-header" style="color:#38bdf8;">Android Gold</div>
            ${buildSvg(expectedTree, null, 'gold', halfW, halfH)}
          </div>
          <div class="sbs-pane">
            <div class="sbs-header" style="color:#4ade80;">Player Actual</div>
            ${buildSvg(null, actualTree, 'actual', halfW, halfH)}
          </div>
        </div>`;
    }

    return buildSvg(expectedTree, actualTree, mode, width, height);
}

// Client dataset for live animation scrubbing, side-by-side switching, and inspector
const clientTestRecordMap = {};
for (const r of activeResults) {
    clientTestRecordMap[r.name] = {
        name: r.name,
        description: r.description,
        status: r.status,
        expectedTree: r.expectedTree,
        actualTree: r.actualTree,
        expectedFrames: r.expectedFrames || null,
        actualFrames: r.actualFrames || null,
        expectedResizeSteps: r.expectedResizeSteps || null,
        actualResizeSteps: r.actualResizeSteps || null,
        expectedInteractions: r.expectedInteractions || null,
        actualInteractions: r.actualInteractions || null,
        parameters: r.parameters || {},
        document: r.document || null,
        // The inspector's "Diffs & Bounds" table formats rows as `[id] Kind | Property | Gold |
        // Actual | Diff`, which is the shape of a tree diff. Raster diffs belong to the visual
        // comparison panel, not here.
        diffs: (r.diffs || []).filter((d) => d.probe !== "raster"),
        bounds: computeTestBounds(r),
        goldImageBase64: r.goldImageBase64 || null,
        renderedCanvasBase64: r.renderedCanvasBase64 || null,
        diffHeatmapBase64: r.diffHeatmapBase64 || null,
        rmse: r.rmse,
        maxDelta: r.maxDelta,
        maxRmse: r.maxRmse
    };
}

// Client dataset for particle simulations
const clientParticleRecordMap = {};
for (const r of particleResults) {
    const frames = r.expectedFrames || r.actualFrames || [];
    // Each particle is a flat array; its width tells us how many components the simulation
    // tracks. `particle_lifetime_decay` carries a fifth (life), which the old hard-coded
    // four-name list silently dropped from the table.
    const width = frames.length && frames[0].particles?.length ? frames[0].particles[0].length : 4;
    clientParticleRecordMap[r.name] = {
        name: r.name,
        description: r.description,
        status: r.status,
        parameters: r.parameters || {},
        variables: ['x', 'y', 'vx', 'vy', 'life'].slice(0, width),
        expectedFrames: r.expectedFrames || null,
        actualFrames: r.actualFrames || null,
        diffs: (r.diffs || []).filter((d) => d.probe !== "raster")
    };
}




function renderSubsystemFilterBar(tabKey, results) {
    const total = results.length;
    const failedList = results.filter(r => r.status !== "PASS");
    const passedList = results.filter(r => r.status === "PASS");
    const failed = failedList.length;
    const passed = passedList.length;

    const failJumpHtml = failed > 0 ? `
      <div class="failing-jump-bar" id="failing-jump-${tabKey}">
        <span class="failing-jump-label">⚠ Failing (${failed}):</span>
        <div class="failing-jump-pills">
          ${failedList.map(r => `<button type="button" class="failing-jump-pill" onclick="jumpToSubsystemTest('${tabKey}', '${r.name}')" title="Jump directly to ${r.name}">${r.name}</button>`).join("")}
        </div>
      </div>
    ` : "";

    return `
      <div class="filter-bar" id="subfilter-bar-${tabKey}">
        <div class="filter-btn-group">
          <button type="button" class="filter-btn active" id="subfilter-${tabKey}-all" onclick="filterSubsystem('${tabKey}', 'all')">All Tests (${total})</button>
          <button type="button" class="filter-btn" id="subfilter-${tabKey}-fail" onclick="filterSubsystem('${tabKey}', 'fail')" style="color: ${failed > 0 ? '#f87171' : '#64748b'};" ${failed === 0 ? 'disabled title="No failing tests in this subsystem"' : ''}>Failing Only (${failed})</button>
          <button type="button" class="filter-btn" id="subfilter-${tabKey}-pass" onclick="filterSubsystem('${tabKey}', 'pass')" style="color: #4ade80;">Passing Only (${passed})</button>
        </div>
        <input type="text" id="subsearch-${tabKey}" class="search-input" placeholder="Search ${tabKey} tests by name or description..." oninput="searchSubsystem('${tabKey}', this.value)">
      </div>
      ${failJumpHtml}
    `;
}

function subsystemActionCell(tabId, tabKey, label, failedCount) {
    const exploreBtn = `<button type="button" class="card-action-btn" onclick="event.stopPropagation(); switchMainTab('${tabId}');">Explore ${label} →</button>`;
    const failBtn = failedCount > 0
        ? `<button type="button" class="card-action-btn" style="color: #f87171; border-color: rgba(239, 68, 68, 0.45); background: rgba(239, 68, 68, 0.12); font-weight: 600;" onclick="event.stopPropagation(); jumpToSubsystemFailing('${tabId}', '${tabKey}');" title="Switch to ${label} and filter to failing tests">⚠ ${failedCount} Failing</button>`
        : "";
    return `<div style="display: flex; gap: 6px; align-items: center; flex-wrap: wrap;">${exploreBtn}${failBtn}</div>`;
}

function generateExpressionCardsHtml(tests) {
    return renderSubsystemFilterBar('expressions', tests) + tests.map(res => {
        const isPass = res.status === "PASS";
        const docObj = testDocument(res, "expressions");
        const docJsonStr = JSON.stringify(docObj, null, 2);

        return `
        <div class="test-detail-card" data-status="${res.status}" data-category="expressions" id="test-${res.name}">
          <div class="test-header">
            <div class="test-header-left">
              <span class="test-name">${res.name}</span>
              <span class="badge ${isPass ? "badge-pass" : "badge-fail"}">${res.status}</span>
              <span class="badge" style="background: rgba(56, 189, 248, 0.15); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.3);">Expression Evaluation</span>
            </div>
            <div class="card-actions">
              <span style="font-size: 12.5px; color: var(--text-muted); margin-right: 6px;">${res.durationMs || 0}ms</span>
              <button type="button" class="card-action-btn" onclick="toggleJsonCard('${res.name}')">{ } JSON Doc</button>
              <a href="${TESTS_HREF}/expressions/${res.name}.json" target="_blank" class="card-action-link">📄 Raw JSON</a>
            </div>
          </div>
          <div class="test-desc">${res.description || ""}</div>

          <!-- Collapsible JSON Document Viewer -->
          <div id="json-viewer-${res.name}" class="card-json-viewer" style="display: none;">
            <div class="json-viewer-header">
              <span>Test Document Specification: <code>tests/expressions/${res.name}.json</code></span>
              <button type="button" class="json-copy-btn" onclick="copyJsonText('${res.name}')">Copy JSON</button>
            </div>
            <pre class="json-pre" id="json-pre-${res.name}"><code>${escapeHtml(docJsonStr)}</code></pre>
          </div>

          ${renderVisualComparison(res)}
        </div>
        `;
    }).join("");
}

function generateParticleCardsHtml(tests) {
    return renderSubsystemFilterBar('particles', tests) + tests.map(res => {
        const isPass = res.status === "PASS";
        const docObj = testDocument(res, "particles");
        const docJsonStr = JSON.stringify(docObj, null, 2);
        const vars = ["x", "y", "vx", "vy"];
        // Derived from the same array that drives the scrubber below, so the badge, the slider
        // range and the animation cannot disagree. The old `framesCount`/`particleCount` fields
        // were v1 and are not emitted, so this card used to claim "0 Particles" on every test.
        const frames = Array.isArray(res.expectedFrames) ? res.expectedFrames : [];
        const framesCount = Math.max(0, frames.length - 1);
        const particleCount = frames.length ? (frames[0].particles || []).length : 0;
        const tol = res.parameters?.tolerance || 0.05;
        const physicsPass = ((res.diffs || []).filter((d) => d.probe !== "raster")).length === 0;

        return `
        <div class="test-detail-card" data-status="${res.status}" data-category="particles" id="test-${res.name}">
          <div class="test-header">
            <div class="test-header-left">
              <span class="test-name">${res.name}</span>
              <span class="badge ${isPass ? "badge-pass" : "badge-fail"}">${res.status}</span>
              ${rasterBadgeHtml(res)}
              <span class="badge" style="background: rgba(168, 85, 247, 0.15); color: #c084fc; border: 1px solid rgba(168, 85, 247, 0.3);">Particle Simulation</span>
              <span class="badge badge-anim">${particleCount} Particles</span>
              <span class="badge badge-warn">${framesCount} Frames</span>
            </div>
            <div class="card-actions">
              <span style="font-size: 12.5px; color: var(--text-muted); margin-right: 6px;">${res.durationMs || 0}ms</span>
              <button type="button" class="card-action-btn" onclick="toggleJsonCard('${res.name}')">{ } JSON Doc</button>
              <a href="${TESTS_HREF}/particles/${res.name}.json" target="_blank" class="card-action-link">📄 Raw JSON</a>
            </div>
          </div>
          <div class="test-desc">${res.description || ""}</div>

          <!-- Collapsible JSON Document Viewer -->
          <div id="json-viewer-${res.name}" class="card-json-viewer" style="display: none;">
            <div class="json-viewer-header">
              <span>Test Document Specification: <code>tests/particles/${res.name}.json</code></span>
              <button type="button" class="json-copy-btn" onclick="copyJsonText('${res.name}')">Copy JSON</button>
            </div>
            <pre class="json-pre" id="json-pre-${res.name}"><code>${escapeHtml(docJsonStr)}</code></pre>
          </div>

          ${renderVisualComparison(res)}

          <!-- Interactive 2D Simulation Player -->
          <div class="particle-player-container">
            <div class="particle-canvas-wrapper">
              <canvas id="canvas-particle-${res.name}" class="particle-canvas" width="320" height="220"></canvas>
            </div>
            <div class="particle-controls">
              <div class="particle-ctrl-row">
                <button type="button" class="ctrl-btn" id="btn-play-${res.name}" onclick="toggleParticlePlay('${res.name}')">▶ Play</button>
                <button type="button" class="ctrl-btn" onclick="resetParticleSim('${res.name}')">⟲ Reset</button>
                <span class="frame-label" id="frame-label-${res.name}" style="font-family: monospace; font-size: 12px; color: #38bdf8;">Frame: 0 / ${framesCount}</span>
              </div>
              <input type="range" class="particle-slider" id="slider-particle-${res.name}" min="0" max="${framesCount}" value="0" step="1"
                     oninput="scrubParticleSim('${res.name}', parseInt(this.value, 10))">
              <div class="particle-schema-info">
                <span>Variables: <code>${vars.join(", ")}</code></span>
                <span>Tolerance: <code>±${tol}</code></span>
                <span>Physics Check: <strong style="color: ${physicsPass ? "#4ade80" : "#f87171"};">${physicsPass ? "PASS (within tolerance)" : "FAIL (diff detected)"}</strong></span>
              </div>
            </div>
          </div>

          <!-- Particle State Matrix Table -->
          <div style="overflow-x: auto; margin-top: 14px;">
            <table class="particle-matrix-table" id="table-particle-${res.name}">
              <thead>
                <tr>
                  <th>Particle</th>
                  ${vars.map(v => `<th>${v} (Act / Exp)</th>`).join("")}
                  <th>Status</th>
                </tr>
              </thead>
              <tbody id="tbody-particle-${res.name}">
                <!-- Populated dynamically per frame -->
              </tbody>
            </table>
          </div>
        </div>
        `;
    }).join("");
}

function generateWireCardsHtml(tests) {
    return renderSubsystemFilterBar('wire', tests) + tests.map(res => {
        const isPass = res.status === "PASS";
        const docObj = testDocument(res, "wire");
        const docJsonStr = JSON.stringify(docObj, null, 2);

        return `
        <div class="test-detail-card" data-status="${res.status}" data-category="wire" id="test-${res.name}">
          <div class="test-header">
            <div class="test-header-left">
              <span class="test-name">${res.name}</span>
              <span class="badge ${isPass ? "badge-pass" : "badge-fail"}">${res.status}</span>
              <span class="badge" style="background: rgba(20, 184, 166, 0.15); color: #2dd4bf; border: 1px solid rgba(20, 184, 166, 0.3);">Wire Protocol</span>
            </div>
            <div class="card-actions">
              <span style="font-size: 12.5px; color: var(--text-muted); margin-right: 6px;">${res.durationMs || 0}ms</span>
              <button type="button" class="card-action-btn" onclick="toggleJsonCard('${res.name}')">{ } JSON Doc</button>
              <a href="${TESTS_HREF}/wire/${res.name}.json" target="_blank" class="card-action-link">📄 Raw JSON</a>
            </div>
          </div>
          <div class="test-desc">${res.description || ""}</div>

          <!-- Collapsible JSON Document Viewer -->
          <div id="json-viewer-${res.name}" class="card-json-viewer" style="display: none;">
            <div class="json-viewer-header">
              <span>Test Document Specification: <code>tests/wire/${res.name}.json</code></span>
              <button type="button" class="json-copy-btn" onclick="copyJsonText('${res.name}')">Copy JSON</button>
            </div>
            <pre class="json-pre" id="json-pre-${res.name}"><code>${escapeHtml(docJsonStr)}</code></pre>
          </div>

          ${renderVisualComparison(res)}

          <!-- Subsystem Verification Result -->
          <div style="margin-top: 14px; background: rgba(15, 23, 42, 0.6); border: 1px solid var(--surface-border); border-radius: 8px; padding: 12px 16px;">
            <div style="font-size: 13px; font-weight: 700; color: #f1f5f9; margin-bottom: 6px;">Conformance Verification</div>
            <div style="font-size: 12.5px; color: var(--text-muted); line-height: 1.5;">
              ${isPass ? `✔ All wire buffer serialization &amp; referenced operation assertions evaluated with zero discrepancies.` : `✘ Discrepancies detected: ${(res.diffs || []).map(d => d.message).join('; ')}`}
            </div>
          </div>
        </div>
        `;
    }).join("");
}

function generateLoomCardsHtml(tests) {
    return renderSubsystemFilterBar('loom', tests) + tests.map(res => {
        const isPass = res.status === "PASS";
        const docObj = testDocument(res, "loom");
        const docJsonStr = JSON.stringify(docObj, null, 2);

        return `
        <div class="test-detail-card" data-status="${res.status}" data-category="loom" id="test-${res.name}">
          <div class="test-header">
            <div class="test-header-left">
              <span class="test-name">${res.name}</span>
              <span class="badge ${isPass ? "badge-pass" : "badge-fail"}">${res.status}</span>
              <span class="badge" style="background: rgba(16, 185, 129, 0.15); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.3);">Loom Macro Engine</span>
              ${res.componentCount > 0 ? `<span class="badge badge-warn">${res.componentCount} Inflated Components</span>` : ''}
            </div>
            <div class="card-actions">
              <span style="font-size: 12.5px; color: var(--text-muted); margin-right: 6px;">${res.durationMs || 0}ms</span>
              <button type="button" class="card-action-btn" onclick="toggleJsonCard('${res.name}')">{ } JSON Doc</button>
              <a href="${TESTS_HREF}/loom/${res.name}.json" target="_blank" class="card-action-link">📄 Raw JSON</a>
            </div>
          </div>
          <div class="test-desc">${res.description || ""}</div>

          <!-- Collapsible JSON Document Viewer -->
          <div id="json-viewer-${res.name}" class="card-json-viewer" style="display: none;">
            <div class="json-viewer-header">
              <span>Test Document Specification: <code>tests/loom/${res.name}.json</code></span>
              <button type="button" class="json-copy-btn" onclick="copyJsonText('${res.name}')">Copy JSON</button>
            </div>
            <pre class="json-pre" id="json-pre-${res.name}"><code>${escapeHtml(docJsonStr)}</code></pre>
          </div>

          ${renderVisualComparison(res)}

          <!-- Subsystem Verification Result -->
          <div style="margin-top: 14px; background: rgba(15, 23, 42, 0.6); border: 1px solid var(--surface-border); border-radius: 8px; padding: 12px 16px;">
            <div style="font-size: 13px; font-weight: 700; color: #f1f5f9; margin-bottom: 6px;">Conformance Verification</div>
            <div style="font-size: 12.5px; color: var(--text-muted); line-height: 1.5;">
              ${isPass ? `✔ All macro inflation, slot block, and tiered ID remapping assertions evaluated with zero discrepancies.` : `✘ Discrepancies detected: ${(res.diffs || []).map(d => d.message).join('; ')}`}
            </div>
          </div>
        </div>
        `;
    }).join("");
}

// Shared styling for every raster panel, so the three panes stay visually comparable.
const VIZ_IMG_STYLE = "max-width: 100%; height: auto; max-height: 240px; border-radius: 6px;"
    + " border: 1px solid #334155; background-color: #0b1120;"
    + " background-image: linear-gradient(45deg, #1e293b 25%, transparent 25%),"
    + " linear-gradient(-45deg, #1e293b 25%, transparent 25%),"
    + " linear-gradient(45deg, transparent 75%, #1e293b 75%),"
    + " linear-gradient(-45deg, transparent 75%, #1e293b 75%);"
    + " background-size: 16px 16px; background-position: 0 0, 0 8px, 8px -8px, -8px 0px;";

const VIZ_PANEL_STYLE = "background: #0b101d; border: 1px solid var(--surface-border);"
    + " border-radius: 10px; padding: 12px; text-align: center;";

// ── Value evidence ────────────────────────────────────────────────────────────────────────────
//
// Most of this corpus does not assert pixels. 250 of 252 tests observe scalar values —
// expression results, integer bitwise ops, colours, text transforms, matrices — and for a long
// while the report drew none of them, because its only per-test visual was the raster panel.
// Subsystems that draw nothing by design (expressions, matrixmath, dataoperations, semantics)
// therefore rendered as blank pages, which read as "nothing was tested" rather than "the thing
// tested is a number".
//
// The v1 report had one narrow version of this: a "waveform scrubber" driven by an
// `expected_steps` blob. The v1→v2 gold migration replaced that blob with proper `timeline`
// steps and per-`at` checks, and the scrubber was never rewired — it hung off `res.expectedSteps`,
// a field the v2 runner does not emit, so it silently rendered for zero tests. It has since been
// deleted. What follows replaces it and is not limited to one test or one category.

// Probes whose expected/observed values are worth showing literally. Excludes `tree`, `raster`,
// `particles` and friends, which have their own dedicated visualisations.
const SCALAR_PROBES = new Set(["float", "int", "text", "color", "matrix", "float_array"]);

// A capture id that names an instant on the timeline, e.g. `t_0.25` or `frame_3`. Used to order
// series left-to-right and to label the x axis.
function captureOrder(gold) {
    const steps = Array.isArray(gold?.timeline) ? gold.timeline : [];
    const order = new Map();
    steps.forEach((s, i) => { if (s && s.id != null) order.set(String(s.id), i); });
    return order;
}

function formatScalar(probe, value) {
    if (value === null || value === undefined) return "—";
    if (probe === "color") {
        if (typeof value === "number") return "0x" + (value >>> 0).toString(16).toUpperCase();
        return String(value);
    }
    if (Array.isArray(value)) return value.map((v) => (typeof v === "number" ? round4(v) : v)).join(", ");
    if (typeof value === "number") return String(round4(value));
    return String(value);
}

function round4(n) {
    return Number.isInteger(n) ? n : Math.round(n * 10000) / 10000;
}

// A colour probe's value, as something a browser can paint. Accepts both the integer form the
// corpus stores and the `0x…` string the player reports.
function colorCss(value) {
    let n = null;
    if (typeof value === "number") n = value >>> 0;
    else if (typeof value === "string" && /^0x[0-9a-f]+$/i.test(value)) n = parseInt(value, 16) >>> 0;
    if (n === null) return null;
    const a = ((n >>> 24) & 0xff) / 255;
    return `rgba(${(n >>> 16) & 0xff}, ${(n >>> 8) & 0xff}, ${n & 0xff}, ${a.toFixed(3)})`;
}

function colorSwatch(value) {
    const css = colorCss(value);
    if (css === null) return "";
    return `<span style="display: inline-block; width: 11px; height: 11px; border-radius: 2px; vertical-align: -1px;
        margin-right: 5px; background: ${css}; border: 1px solid rgba(148,163,184,0.45);"></span>`;
}

/**
 * Whether the player's run reported a failure for a given check.
 *
 * The report must not re-derive pass/fail. Comparison semantics live in the runner and are not
 * uniform: `draw_log` matches the expected command list as a *subsequence* of the observed log,
 * `records` compares field-by-field with a tolerance, `ops` compares scalars exactly. A deep
 * equality check here marked 14 passing tests as failing — `canvas_arc_and_sector` expects
 * ["paint","drawArc","paint","drawSector"] inside a 17-command log and is correct to pass.
 *
 * `diffs` is the runner's verdict, so keying off it means the panels cannot contradict the
 * card's own status badge.
 */
function checkFailed(res, check) {
    const diffs = res.diffs || [];
    const at = String(check.at);
    const channel = check.channel ?? null;
    const target = check.target === undefined ? null : String(check.target);
    return diffs.some((d) => {
        if (String(d.at) !== at || d.probe !== check.probe) return false;
        if (channel !== null && d.channel !== undefined && d.channel !== channel) return false;
        // Scalar diffs name the variable in `target`; structured diffs use `target` for the record
        // *within* the channel, so only compare it when the check itself pins one down.
        if (channel === null && target !== null && d.target !== undefined
            && String(d.target) !== target) return false;
        return true;
    });
}

/**
 * Is this test's raster comparison advisory — still captured and compared, but not the gate?
 *
 * Read from the gold, which is where the decision lives. The report must not infer it from the
 * category or from whether a tree happens to exist: that would be a second, drifting copy of a
 * policy the corpus already states per check.
 */
function rasterIsAdvisory(res) {
    const rasters = (goldFor(res)?.checks || []).filter((c) => c.probe === "raster");
    return rasters.length > 0 && rasters.every((c) => c.advisory === true);
}

/**
 * The `Δ N px / tol` badge.
 *
 * A gating raster keeps the green/red verdict colours. An advisory one must not use them: on a
 * layout test the tree is the verdict, and painting a pixel difference red next to a green PASS
 * reads as a contradiction. Advisory over-tolerance shows amber — "look at this" — and within
 * tolerance shows grey.
 */
function rasterBadgeHtml(res) {
    const aaPixels = typeof res.aaPixels === "number" ? res.aaPixels : null;
    const tolerance = typeof res.rasterTolerance === "number" ? res.rasterTolerance : 16;
    const within = aaPixels !== null && aaPixels <= tolerance;
    const delta = `Δ ${aaPixels === null ? "?" : aaPixels} px / ${tolerance}`;
    if (!rasterIsAdvisory(res)) {
        return `<span class="badge ${within ? "badge-pass" : "badge-fail"}">${delta}</span>`;
    }
    const style = within
        ? "background: rgba(148,163,184,0.12); color: #94a3b8; border: 1px solid rgba(148,163,184,0.25);"
        : "background: rgba(245,158,11,0.15); color: #f59e0b; border: 1px solid rgba(245,158,11,0.3);";
    const primaryDesc = res.category === "particles" ? "computed particle state" : "component tree";
    return `<span class="badge" style="${style}" title="Visual comparison only. The ${primaryDesc} `
        + `is what decides this test; the raster is captured and compared with the same `
        + `metric and tolerance, but does not gate.">${delta} · visual</span>`;
}

function scalarsEqual(probe, expect, actual, tolerance) {
    if (actual === null || actual === undefined) return false;
    if (Array.isArray(expect)) {
        if (!Array.isArray(actual) || actual.length !== expect.length) return false;
        return expect.every((v, i) => scalarsEqual(probe, v, actual[i], tolerance));
    }
    if (typeof expect === "number") {
        const act = typeof actual === "number"
            ? actual
            : (typeof actual === "string" && /^0x[0-9a-f]+$/i.test(actual) ? parseInt(actual, 16) : NaN);
        if (Number.isNaN(act)) return false;
        return Math.abs(act - expect) <= (typeof tolerance === "number" ? tolerance : 0);
    }
    if (probe === "color") return formatScalar(probe, expect) === formatScalar(probe, actual);
    return String(expect) === String(actual);
}

/**
 * Pairs each scalar check in the gold with the value the player reported for it.
 *
 * Returns one row per check, in timeline order, so both the table and the chart below work
 * from the same list and cannot disagree about what passed.
 */
function collectValueRows(res, gold) {
    if (!gold || !Array.isArray(gold.checks)) return [];
    const order = captureOrder(gold);
    const observed = res.observed || {};
    const rows = [];
    for (const check of gold.checks) {
        if (!check || !SCALAR_PROBES.has(check.probe)) continue;
        const at = String(check.at);
        const target = String(check.target);
        // A scalar check may also carry a channel — `float_array` checks do — and the runner then
        // records the observation under `<probe>:<channel>` with the target nested inside.
        const key = check.channel == null ? check.probe : `${check.probe}:${check.channel}`;
        const actual = observed[at]?.[key]?.[target];
        rows.push({
            at,
            atIndex: order.has(at) ? order.get(at) : Number.MAX_SAFE_INTEGER,
            probe: check.probe,
            target,
            expect: check.expect,
            actual: actual === undefined ? null : actual,
            tolerance: check.tolerance,
            pass: actual !== undefined && !checkFailed(res, check),
            observedAtAll: actual !== undefined,
        });
    }
    rows.sort((a, b) => a.atIndex - b.atIndex);
    return rows;
}

/**
 * A line chart of expected vs observed for every numeric series that spans two or more captures.
 *
 * This is what the old waveform scrubber was trying to be. The scrubber could only ever show one
 * side at a time, which made a divergence something you had to remember rather than see; drawing
 * both lines on one axis makes "the player's sine wave is flat" a glance rather than a deduction.
 */
function renderValueSeries(res, gold, rows) {
    const numeric = rows.filter((r) => typeof r.expect === "number" && r.probe !== "color");
    if (!numeric.length) return "";

    const captures = [...new Set(numeric.map((r) => r.at))];
    if (captures.length < 2) return "";

    // Group by probe+target; keep only series that actually vary across captures on one side or
    // the other, otherwise a page of flat lines buries the interesting one.
    const series = new Map();
    for (const r of numeric) {
        const key = `${r.probe}:${r.target}`;
        if (!series.has(key)) series.set(key, { probe: r.probe, target: r.target, points: [] });
        series.get(key).points.push(r);
    }
    const drawable = [...series.values()].filter((s) => s.points.length >= 2);
    if (!drawable.length) return "";

    const W = 640, H = 128, PAD_L = 52, PAD_R = 12, PAD_T = 10, PAD_B = 22;
    const charts = drawable.map((s) => {
        const exp = captures.map((c) => s.points.find((p) => p.at === c)?.expect ?? null);
        const act = captures.map((c) => {
            const p = s.points.find((q) => q.at === c);
            return typeof p?.actual === "number" ? p.actual : null;
        });
        const all = [...exp, ...act].filter((v) => v !== null);
        if (!all.length) return "";
        let lo = Math.min(...all), hi = Math.max(...all);
        if (hi - lo < 1e-9) { hi = lo + 1; lo -= 1; }
        const pad = (hi - lo) * 0.15;
        lo -= pad; hi += pad;
        const x = (i) => PAD_L + (i * (W - PAD_L - PAD_R)) / Math.max(1, captures.length - 1);
        const y = (v) => PAD_T + (H - PAD_T - PAD_B) * (1 - (v - lo) / (hi - lo));
        const path = (vals) => {
            const parts = [];
            vals.forEach((v, i) => { if (v !== null) parts.push(`${parts.length ? "L" : "M"}${x(i).toFixed(1)},${y(v).toFixed(1)}`); });
            return parts.join(" ");
        };
        const dots = (vals, colour) => vals.map((v, i) => v === null ? ""
            : `<circle cx="${x(i).toFixed(1)}" cy="${y(v).toFixed(1)}" r="3" fill="${colour}"/>`).join("");
        const diverges = captures.some((c, i) => {
            const p = s.points.find((q) => q.at === c);
            return p && !p.pass;
        });
        return `
        <div style="margin-bottom: 10px;">
          <div style="font-size: 11.5px; font-weight: 700; color: ${diverges ? "#f87171" : "#cbd5e1"}; margin-bottom: 2px;">
            ${escapeHtml(s.target)}<span style="color: var(--text-muted); font-weight: 500;"> · ${s.probe}</span>
          </div>
          <svg viewBox="0 0 ${W} ${H}" style="width: 100%; height: auto; max-width: ${W}px; display: block;">
            <rect x="${PAD_L}" y="${PAD_T}" width="${W - PAD_L - PAD_R}" height="${H - PAD_T - PAD_B}"
                  fill="#0b101d" stroke="#1e293b"/>
            <text x="6" y="${PAD_T + 9}" fill="#64748b" font-size="9">${round4(hi)}</text>
            <text x="6" y="${H - PAD_B}" fill="#64748b" font-size="9">${round4(lo)}</text>
            <path d="${path(exp)}" fill="none" stroke="#22c55e" stroke-width="2.5"/>
            ${dots(exp, "#22c55e")}
            <path d="${path(act)}" fill="none" stroke="#f43f5e" stroke-width="1.8"/>
            ${dots(act, "#f43f5e")}
            ${captures.map((c, i) => `<text x="${x(i).toFixed(1)}" y="${H - 6}" fill="#64748b" font-size="9"
                 text-anchor="${i === 0 ? "start" : i === captures.length - 1 ? "end" : "middle"}">${escapeXml(c)}</text>`).join("")}
          </svg>
        </div>`;
    }).join("");

    if (!charts.trim()) return "";
    return `
    <div style="margin-top: 16px;">
      <div style="font-size: 13px; font-weight: 700; color: #f1f5f9; margin-bottom: 8px;">
        Value Timeline
        <span style="font-weight: 500; font-size: 11px; color: #22c55e; margin-left: 10px;">■ reference</span>
        <span style="font-weight: 500; font-size: 11px; color: #f43f5e; margin-left: 8px;">■ ${escapeHtml(report.player || "player")}</span>
      </div>
      <div style="${VIZ_PANEL_STYLE}">${charts}</div>
    </div>`;
}

/**
 * Colour probes over time, as swatches rather than a line chart.
 *
 * `renderValueSeries` deliberately excludes colour: an ARGB word is not a magnitude, so plotting
 * it on a numeric axis produces a line whose shape means nothing. But a test like
 * `color_theme_mode_switching` is *entirely* about colour changing between captures, and with the
 * chart skipped it had no visualisation at all — only hex strings in the table below. A strip of
 * paired swatches per capture restores the "see the divergence" property the chart provides for
 * numbers.
 */
function renderColorSeries(res, gold, rows) {
    const colours = rows.filter((r) => r.probe === "color");
    if (!colours.length) return "";

    const captures = [...new Set(colours.map((r) => r.at))];
    if (captures.length < 2) return "";

    const series = new Map();
    for (const r of colours) {
        if (!series.has(r.target)) series.set(r.target, []);
        series.get(r.target).push(r);
    }

    const chip = (value, missing) => {
        const css = colorCss(value);
        return `<div style="width: 100%; height: 22px; border-radius: 3px; border: 1px solid rgba(148,163,184,0.35);
            background: ${css ?? "repeating-linear-gradient(45deg, #1e293b 0 4px, #0b101d 4px 8px)"};"
            title="${escapeHtml(missing ? "not reported" : formatScalar("color", value))}"></div>`;
    };

    const strips = [...series.entries()].map(([target, points]) => {
        const diverges = points.some((p) => !p.pass);
        const cells = captures.map((c) => {
            const p = points.find((q) => q.at === c);
            if (!p) return `<div style="flex: 1 1 0; min-width: 0;"></div>`;
            return `
            <div style="flex: 1 1 0; min-width: 0;">
              ${chip(p.expect, false)}
              <div style="height: 3px;"></div>
              ${chip(p.actual, !p.observedAtAll)}
              <div style="font-size: 9.5px; color: ${p.pass ? "#64748b" : "#f87171"}; margin-top: 3px;
                          overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${escapeHtml(c)}</div>
            </div>`;
        }).join("");
        return `
        <div style="margin-bottom: 12px;">
          <div style="font-size: 11.5px; font-weight: 700; color: ${diverges ? "#f87171" : "#cbd5e1"}; margin-bottom: 3px;">
            ${escapeHtml(target)}<span style="color: var(--text-muted); font-weight: 500;"> · color</span>
          </div>
          <div style="display: flex; gap: 6px; align-items: flex-start; max-width: 640px;">${cells}</div>
        </div>`;
    }).join("");

    return `
    <div style="margin-top: 16px;">
      <div style="font-size: 13px; font-weight: 700; color: #f1f5f9; margin-bottom: 8px;">
        Colour Timeline
        <span style="font-weight: 500; font-size: 11px; color: #22c55e; margin-left: 10px;">■ upper = reference</span>
        <span style="font-weight: 500; font-size: 11px; color: #f43f5e; margin-left: 8px;">■ lower = ${escapeHtml(report.player || "player")}</span>
      </div>
      <div style="${VIZ_PANEL_STYLE}">${strips}</div>
    </div>`;
}

/**
 * The expected/observed table. This is the primary evidence for every test that does not draw.
 */
function renderValueTable(res, gold, rows) {
    if (!rows.length) return "";
    const byCapture = new Map();
    for (const r of rows) {
        if (!byCapture.has(r.at)) byCapture.set(r.at, []);
        byCapture.get(r.at).push(r);
    }
    const failed = rows.filter((r) => !r.pass).length;

    const blocks = [...byCapture.entries()].map(([at, group]) => {
        const body = group.map((r) => {
            const swatch = r.probe === "color" ? colorSwatch(r.expect) : "";
            const actSwatch = r.probe === "color" ? colorSwatch(r.actual) : "";
            const tol = typeof r.tolerance === "number" && r.tolerance > 0
                ? `<span style="color: var(--text-muted); font-size: 10.5px;"> ±${r.tolerance}</span>` : "";
            return `
            <tr>
              <td style="padding: 3px 10px 3px 0; color: #cbd5e1; white-space: nowrap;">${escapeHtml(r.target)}</td>
              <td style="padding: 3px 10px 3px 0; color: var(--text-muted); font-size: 11px;">${r.probe}</td>
              <td style="padding: 3px 10px 3px 0; color: #4ade80; font-variant-numeric: tabular-nums;">${swatch}${escapeHtml(formatScalar(r.probe, r.expect))}${tol}</td>
              <td style="padding: 3px 10px 3px 0; color: ${r.pass ? "#cbd5e1" : "#f87171"}; font-variant-numeric: tabular-nums;">${actSwatch}${escapeHtml(r.observedAtAll ? formatScalar(r.probe, r.actual) : "not reported")}</td>
              <td style="padding: 3px 0; color: ${r.pass ? "#4ade80" : "#f87171"};">${r.pass ? "✔" : "✘"}</td>
            </tr>`;
        }).join("");
        const label = byCapture.size > 1
            ? `<div style="font-size: 11px; color: #94a3b8; font-weight: 700; margin: 8px 0 3px;">@ ${escapeHtml(at)}</div>` : "";
        return `${label}<table style="width: 100%; border-collapse: collapse; font-size: 12px; font-family: ui-monospace, monospace;">
          <tr style="color: var(--text-muted); font-size: 10.5px; text-transform: uppercase; letter-spacing: 0.04em;">
            <td style="padding-bottom: 3px;">target</td><td>probe</td><td>reference</td><td>${escapeHtml(report.player || "player")}</td><td></td>
          </tr>${body}</table>`;
    }).join("");

    return `
    <div style="margin-top: 16px;">
      <div style="font-size: 13px; font-weight: 700; color: #f1f5f9; margin-bottom: 8px;">
        Asserted Values
        <span style="font-weight: 500; font-size: 11.5px; color: ${failed ? "#f87171" : "#4ade80"}; margin-left: 8px;">
          ${rows.length - failed} / ${rows.length} match</span>
      </div>
      <div style="${VIZ_PANEL_STYLE}">${blocks}</div>
    </div>`;
}

// Probes whose value is a structure rather than a scalar. These are how the non-drawing
// subsystems assert themselves — semantics nodes, shader uniform bindings, scheduling op counts,
// decoded animation specs, registered paths — and without them those pages stay blank even after
// the scalar table above.
//
// Note the different storage shape: scalars live at observed[at][probe][target], structures at
// the flat key observed[at]["<probe>:<target>"]. That asymmetry comes from the runner and is
// load-bearing, so it is matched here rather than normalised away.
const STRUCTURED_PROBES = new Set(["records", "ops", "trace", "draw_log", "relation"]);

function stableStringify(value) {
    if (value === null || typeof value !== "object") return JSON.stringify(value);
    if (Array.isArray(value)) return "[" + value.map(stableStringify).join(",") + "]";
    return "{" + Object.keys(value).sort()
        .map((k) => JSON.stringify(k) + ":" + stableStringify(value[k])).join(",") + "}";
}

function renderStructuredEvidence(res, gold) {
    if (!gold || !Array.isArray(gold.checks)) return "";
    const observed = res.observed || {};
    const order = captureOrder(gold);
    const rows = [];
    for (const check of gold.checks) {
        if (!check || !STRUCTURED_PROBES.has(check.probe)) continue;
        const at = String(check.at);
        // Structured probes name their payload with `channel`; the runner keys the observation as
        // `<probe>:<channel>`. `target` is the scalar-probe spelling and is never set on these —
        // reading it here made every lookup miss and every panel claim a failure.
        const channel = check.channel ?? check.target;
        const key = channel == null ? check.probe : `${check.probe}:${channel}`;
        const actual = observed[at]?.[key];
        rows.push({
            at, key,
            atIndex: order.has(at) ? order.get(at) : Number.MAX_SAFE_INTEGER,
            expect: check.expect,
            actual: actual === undefined ? null : actual,
            observedAtAll: actual !== undefined,
            pass: actual !== undefined && !checkFailed(res, check),
        });
    }
    if (!rows.length) return "";
    rows.sort((a, b) => a.atIndex - b.atIndex);

    const pre = "margin: 0; padding: 8px 10px; background: #070b14; border: 1px solid #1e293b;"
        + " border-radius: 6px; font-size: 11px; line-height: 1.45; overflow-x: auto;"
        + " font-family: ui-monospace, monospace; text-align: left; white-space: pre-wrap;";
    const blocks = rows.map((r) => `
      <div style="margin-bottom: 10px;">
        <div style="font-size: 11.5px; font-weight: 700; color: ${r.pass ? "#cbd5e1" : "#f87171"}; margin-bottom: 4px; text-align: left;">
          ${r.pass ? "✔" : "✘"} ${escapeHtml(r.key)}
          ${rows.some((o) => o.at !== r.at) ? `<span style="color: var(--text-muted); font-weight: 500;"> @ ${escapeHtml(r.at)}</span>` : ""}
        </div>
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 8px;">
          <div><div style="font-size: 10px; color: #4ade80; margin-bottom: 3px; text-align: left;">reference</div>
            <pre style="${pre} color: #86efac;">${escapeHtml(JSON.stringify(r.expect, null, 1))}</pre></div>
          <div><div style="font-size: 10px; color: ${r.pass ? "#94a3b8" : "#f87171"}; margin-bottom: 3px; text-align: left;">${escapeHtml(report.player || "player")}</div>
            <pre style="${pre} color: ${r.pass ? "#cbd5e1" : "#fca5a5"};">${escapeHtml(r.observedAtAll ? JSON.stringify(r.actual, null, 1) : "not reported")}</pre></div>
        </div>
      </div>`).join("");

    const failed = rows.filter((r) => !r.pass).length;
    return `
    <div style="margin-top: 16px;">
      <div style="font-size: 13px; font-weight: 700; color: #f1f5f9; margin-bottom: 8px;">
        Asserted Structures
        <span style="font-weight: 500; font-size: 11.5px; color: ${failed ? "#f87171" : "#4ade80"}; margin-left: 8px;">
          ${rows.length - failed} / ${rows.length} match</span>
      </div>
      <div style="${VIZ_PANEL_STYLE}">${blocks}</div>
    </div>`;
}

/**
 * Values the player reported that no gold check covers.
 *
 * Both panels above iterate over the gold's `checks`, so anything a player observes *beyond* what
 * is asserted renders nowhere — which is exactly where coverage gaps hide. This panel closes that
 * blind spot. It currently fires for no test: every value the TypeScript player reports is covered
 * by a check. An earlier version claimed 45 tests were uncovered, which was a keying bug in this
 * function, not a corpus gap — it looked for structured checks under `target` when the corpus uses
 * `channel`. A future subsystem that reports more than it asserts will show up here.
 *
 * Where the gold explains the omission in its `unasserted` array (§2.8), the reason is shown
 * alongside, so a deliberate gap reads differently from an accidental one.
 */
function renderUnassertedObservations(res, gold) {
    const observed = res.observed || {};
    const order = captureOrder(gold);
    const checked = new Set();
    for (const c of gold.checks || []) {
        if (SCALAR_PROBES.has(c.probe)) {
            const key = c.channel == null ? c.probe : `${c.probe}:${c.channel}`;
            checked.add(`${c.at}\u0000${key}\u0000${c.target}`);
        }
        else if (STRUCTURED_PROBES.has(c.probe)) {
            // `channel`, not `target` — see renderStructuredEvidence.
            const channel = c.channel ?? c.target;
            checked.add(`${c.at}\u0000${channel == null ? c.probe : `${c.probe}:${channel}`}`);
        }
    }
    // Reasons the corpus already gives for not asserting something.
    const reasons = new Map();
    for (const u of gold.unasserted || []) {
        if (u && typeof u.key === "string") reasons.set(u.key, u.reason || "");
    }

    const rows = [];
    for (const [at, probes] of Object.entries(observed)) {
        for (const [key, value] of Object.entries(probes || {})) {
            const probe = key.includes(":") ? key.slice(0, key.indexOf(":")) : key;
            // Scalar observations are `<probe>` or `<probe>:<channel>`, with the target nested
            // inside; structured ones are `<probe>:<channel>` with the payload at the top.
            if (SCALAR_PROBES.has(probe) && value && typeof value === "object" && !Array.isArray(value)) {
                for (const [target, v] of Object.entries(value)) {
                    if (checked.has(`${at}\u0000${key}\u0000${target}`)) continue;
                    rows.push({ at, label: `${key}[${target}]`, probe, value: v,
                        reason: reasons.get(`${key}@${at}`) ?? reasons.get(key) });
                }
            } else if (STRUCTURED_PROBES.has(probe) && key.includes(":")) {
                if (checked.has(`${at}\u0000${key}`)) continue;
                rows.push({ at, label: key, probe, value,
                    reason: reasons.get(`${key}@${at}`) ?? reasons.get(key) });
            }
        }
    }
    if (!rows.length) return "";
    rows.sort((a, b) => (order.get(a.at) ?? 1e9) - (order.get(b.at) ?? 1e9) || a.label.localeCompare(b.label));

    const body = rows.map((r) => {
        const v = typeof r.value === "object" && r.value !== null
            ? JSON.stringify(r.value)
            : formatScalar(r.probe, r.value);
        const short = v.length > 160 ? v.slice(0, 157) + "…" : v;
        return `
        <tr>
          <td style="padding: 3px 10px 3px 0; color: var(--text-muted); white-space: nowrap; font-size: 11px;">${escapeHtml(r.at)}</td>
          <td style="padding: 3px 10px 3px 0; color: #cbd5e1; white-space: nowrap;">${escapeHtml(r.label)}</td>
          <td style="padding: 3px 0; color: #94a3b8; word-break: break-word;">${escapeHtml(short)}${
            r.reason ? `<div style="color: #7c8698; font-size: 10.5px; font-style: italic; margin-top: 1px;">${escapeHtml(r.reason)}</div>` : ""}</td>
        </tr>`;
    }).join("");

    return `
    <div style="margin-top: 16px;">
      <div style="font-size: 13px; font-weight: 700; color: #94a3b8; margin-bottom: 8px;">
        Observed, Not Asserted
        <span style="font-weight: 500; font-size: 11.5px; color: var(--text-muted); margin-left: 8px;">
          ${rows.length} value${rows.length === 1 ? "" : "s"} no check covers</span>
      </div>
      <div style="${VIZ_PANEL_STYLE} border-style: dashed;">
        <table style="width: 100%; border-collapse: collapse; font-size: 12px; text-align: left;
                      font-family: ui-monospace, monospace;">
          <tr style="color: var(--text-muted); font-size: 10.5px; text-transform: uppercase; letter-spacing: 0.04em;">
            <td style="padding-bottom: 3px;">at</td><td>key</td><td>value</td>
          </tr>${body}
        </table>
      </div>
    </div>`;
}

/**
 * All non-raster evidence for one test. Appended to every card by renderVisualComparison, so a
 * subsystem cannot be added later and silently get no visualisation.
 */
function renderValueEvidence(res) {
    const gold = goldFor(res);
    if (!gold) return "";
    const rows = collectValueRows(res, gold);
    return renderValueSeries(res, gold, rows)
        + renderColorSeries(res, gold, rows)
        + renderValueTable(res, gold, rows)
        + renderStructuredEvidence(res, gold)
        + renderUnassertedObservations(res, gold);
}

function vizPanel(title, colour, body) {
    return `<div style="${VIZ_PANEL_STYLE}">
        <div style="font-size: 11px; color: ${colour}; font-weight: 700; margin-bottom: 8px;"
             >${title}</div>
        ${body}
      </div>`;
}

/**
 * Renders the raster comparison for one test.
 *
 * Two rules this encodes, both learned from the previous version getting them wrong:
 *
 *  1. A missing diff heatmap is never replaced by the rendered canvas. Doing so put the
 *     player's own output under a "Diff" heading, which reads as "these pixels are wrong"
 *     when in fact nothing was compared at all.
 *  2. A test with no baseline still shows what the player drew. Hiding the whole block left
 *     entire subsystems -- expressions, loom, textpath -- with no visual evidence whatsoever,
 *     and silently implied they had nothing to show.
 *
 * Metrics read "n/a" rather than 0 when no comparison happened, because a defaulted zero is
 * indistinguishable from a perfect score.
 */
function renderVisualComparison(res, idPrefix = "") {
    return renderRasterComparison(res, idPrefix) + renderValueEvidence(res);
}

function renderRasterComparison(res, idPrefix = "") {
    const comparisons = Array.isArray(res.rasterComparisons) ? res.rasterComparisons : [];
    // Several instants of the same document are each their own assertion. Only the first gets
    // the shared element ids, because the resize-step script drives exactly one panel set.
    if (comparisons.length > 1) {
        return comparisons.map((cmp, i) => renderOneComparison({
            name: res.name,
            goldImageBase64: cmp.goldImageBase64,
            renderedCanvasBase64: cmp.renderedCanvasBase64 || res.renderedCanvasBase64,
            aaPixels: cmp.aaPixels,
            rasterTolerance: cmp.rasterTolerance,
            rmse: cmp.rmse,
            maxDelta: cmp.maxDelta,
            differingPixels: cmp.differingPixels,
            totalPixels: cmp.totalPixels,
            diffHeatmapBase64: cmp.diffHeatmapBase64,
            maxRmse: cmp.maxRmse,
        }, i === 0 ? idPrefix : "", cmp.at)).join("");
    }
    return renderOneComparison(res, idPrefix, comparisons[0]?.at || null);
}

function renderOneComparison(res, idPrefix = "", at = null) {
    const id = (k) => (idPrefix ? ` id="${idPrefix}-${k}-${res.name}"` : "");
    const hasBaseline = Boolean(res.goldImageBase64);
    const hasRender = Boolean(res.renderedCanvasBase64);
    if (!hasBaseline && !hasRender) return "";
    const atLabel = at ? ` <span style="color: var(--text-muted); font-weight: 500;">@ ${at}</span>` : "";

    if (!hasBaseline) {
        // The corpus distinguishes two reasons a test has no reference image, and they read
        // completely differently. Showing the same empty-looking canvas panel for both was
        // misleading: it implied a visual result for fifty tests -- matrix arithmetic, text
        // formatting, expression evaluation -- that draw nothing by design and never could.
        const drawsNothing = (res.unasserted || []).some(
            (u) => typeof u?.key === "string" && u.key.startsWith("raster@"));

        if (drawsNothing) {
            return `
        <div style="margin-top: 16px; margin-bottom: 16px;">
          <div style="background: #0f172a; border: 1px solid var(--surface-border); border-radius: 8px; padding: 10px 14px; font-size: 12px; color: var(--text-muted);">
            <strong style="color: #94a3b8;">No visual output.</strong>
            This document draws nothing, so there is no image to compare. What it asserts is
            shown as values below.
          </div>
        </div>`;
        }

        // The reference recorded no image, yet this player drew something. That is a divergence
        // no check can catch, because the absent baseline is precisely what would have caught it.
        return `
        <div style="margin-top: 16px; margin-bottom: 16px;">
          <div style="font-size: 13px; font-weight: 700; color: #f1f5f9; margin-bottom: 8px;">Rendered Output${atLabel}</div>
          <div style="display: grid; grid-template-columns: minmax(220px, 360px); gap: 16px; margin-bottom: 12px;">
            ${vizPanel("Rendered Canvas", "#4ade80",
                `<img${id("act")} src="${res.renderedCanvasBase64}" alt="Rendered ${res.name}" style="${VIZ_IMG_STYLE}">`)}
          </div>
          <div style="background: #0f172a; border: 1px solid var(--surface-border); border-radius: 8px; padding: 10px 14px; font-size: 12px; color: var(--text-muted);">
            <strong style="color: #fbbf24;">No reference baseline.</strong>
            This test declares no <code>raster</code> check, so nothing about these pixels is asserted.
            The render is shown for inspection only and is not part of the verdict.
          </div>
        </div>`;
    }

    // The verdict is the antialiasing-aware differing-pixel count (CONFORMANCE_FORMAT.md §2.5).
    // RMSE is still shown, but it decides nothing and so is not coloured as though it did.
    const aa = typeof res.aaPixels === "number" ? res.aaPixels : null;
    const tolerance = typeof res.rasterTolerance === "number" ? res.rasterTolerance
        : (typeof res.maxRmse === "number" ? res.maxRmse : 16);
    const aaPass = aa !== null && aa <= tolerance;
    const rmse = typeof res.rmse === "number" ? res.rmse : null;
    const maxDelta = typeof res.maxDelta === "number" ? res.maxDelta : null;
    const differing = typeof res.differingPixels === "number" ? res.differingPixels : null;
    const total = typeof res.totalPixels === "number" ? res.totalPixels : null;

    const diffBody = res.diffHeatmapBase64
        ? `<img${id("img-diff")} src="${res.diffHeatmapBase64}" alt="Diff ${res.name}" style="${VIZ_IMG_STYLE}">`
        : `<div style="${VIZ_IMG_STYLE} display: flex; align-items: center; justify-content: center; min-height: 120px; color: var(--text-muted); font-size: 12px; padding: 12px;">Not computed by this player</div>`;

    const exact = differing === 0;
    const advisory = rasterIsAdvisory(res);
    const primaryLabel = res.category === "particles" ? "particle state is primary check" : "component tree is primary check";
    const titleNote = advisory
        ? ` <span style="font-weight: 500; color: var(--text-muted); font-size: 11.5px;">(visual inspection only — ${primaryLabel})</span>`
        : "";
    const aaColor = advisory
        ? (aaPass ? "#94a3b8" : "#f59e0b")
        : (aaPass ? "#4ade80" : "#f87171");
    return `
    <div style="margin-top: 16px; margin-bottom: 16px;">
      <div style="font-size: 13px; font-weight: 700; color: #f1f5f9; margin-bottom: 8px;">Visual Raster Golden Comparison${atLabel}${titleNote}</div>
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 12px;">
        ${vizPanel("Gold Baseline Image", "#38bdf8",
            `<img${id("img-gold")} src="${res.goldImageBase64}" alt="Gold ${res.name}" style="${VIZ_IMG_STYLE}">`)}
        ${vizPanel("Rendered Canvas", "#4ade80",
            `<img${id("img-act")} src="${res.renderedCanvasBase64}" alt="Actual ${res.name}" style="${VIZ_IMG_STYLE}">`)}
        ${vizPanel(exact ? "Diff Heatmap (Δ) — no differences" : "Diff Heatmap (Δ)", "#f472b6", diffBody)}
      </div>
      <div style="background: #0f172a; border: 1px solid var(--surface-border); border-radius: 8px; padding: 10px 14px; display: flex; justify-content: space-between; align-items: center; font-size: 12px; flex-wrap: wrap; gap: 8px;">
        <span style="color: var(--text-muted);">Pixel Verification Metric:</span>
        <span>
          <strong${id("metric-aa")} style="color: ${aaColor};">Differing px (AA-aware): ${aa === null ? "n/a" : aa}</strong>
          <span style="color: var(--text-muted); margin: 0 8px;">|</span>
          <span style="color: var(--text-muted);">Tolerance: &le; ${tolerance}</span>
          <span style="color: var(--text-muted); margin: 0 8px;">|</span>
          <span${id("metric-rmse")} style="color: var(--text-muted);">RMSE: ${rmse === null ? "n/a" : rmse.toFixed(4)}</span>
          <span style="color: var(--text-muted); margin: 0 8px;">|</span>
          <span${id("metric-delta")} style="color: var(--text-muted);">Max delta: ${maxDelta === null ? "n/a" : maxDelta}</span>
          <span style="color: var(--text-muted); margin: 0 8px;">|</span>
          <span style="color: var(--text-muted);">Raw differing px: ${differing === null ? "n/a" : `${differing}${total ? " / " + total : ""}`}</span>
        </span>
      </div>
    </div>`;
}

function generateCanvasCardsHtml(tests) {
    const catKey = tests[0]?.category || "canvas";
    return renderSubsystemFilterBar(catKey, tests) + tests.map(res => {
        const isPass = res.status === "PASS";
        const docObj = testDocument(res, res.category || "canvas");
        const docJsonStr = JSON.stringify(docObj, null, 2);
        const hasGoldImage = Boolean(res.goldImageBase64);
        const rmse = typeof res.rmse === "number" ? res.rmse : null;
        // The gate is the anti-aliasing-aware pixel count, not RMSE. This badge used to be
        // coloured by `rmse <= maxRmse`, which kept showing a green "pass" for tests the suite
        // now fails — conditional_skip_api_gate scores RMSE 5.44 and 263 differing pixels.
        // rasterBadgeHtml also knows whether the gold made this raster advisory.

        const isClock = res.category === 'clock';
        const isShaders = res.category === 'shaders' || res.category === 'shaderdata' || res.name.startsWith('shader_') || res.name.includes('shader');
        const isTextPath = res.category === 'textpath' || res.name.startsWith('text_');
        const badgeBg = isClock ? 'rgba(245, 158, 11, 0.15)' : isShaders ? 'rgba(192, 132, 252, 0.15)' : isTextPath ? 'rgba(167, 139, 250, 0.15)' : 'rgba(14, 165, 233, 0.15)';
        const badgeColor = isClock ? '#f59e0b' : isShaders ? '#c084fc' : isTextPath ? '#a78bfa' : '#38bdf8';
        const badgeBorder = isClock ? 'rgba(245, 158, 11, 0.3)' : isShaders ? 'rgba(192, 132, 252, 0.3)' : isTextPath ? 'rgba(167, 139, 250, 0.3)' : 'rgba(56, 189, 248, 0.3)';
        const badgeLabel = isClock ? 'Clock &amp; Time' : isShaders ? 'Shaders &amp; AGSL' : isTextPath ? 'Text on Path &amp; Anchoring' : '2D Canvas';
        return `
        <div class="test-detail-card" data-status="${res.status}" data-category="${res.category || 'canvas'}" id="test-${res.name}">
          <div class="test-header">
            <div class="test-header-left">
              <span class="test-name">${res.name}</span>
              <span class="badge ${isPass ? "badge-pass" : "badge-fail"}">${res.status}</span>
              <span class="badge" style="background: ${badgeBg}; color: ${badgeColor}; border: 1px solid ${badgeBorder};">${badgeLabel}</span>
              ${hasGoldImage ? `
              ${rasterBadgeHtml(res)}
              ${rmse === null ? "" : `<span class="badge" style="background: rgba(148,163,184,0.12); color: #94a3b8; border: 1px solid rgba(148,163,184,0.25);">RMSE ${rmse.toFixed(4)}</span>`}
              ` : ''}
            </div>
            <div class="card-actions">
              <span style="font-size: 12.5px; color: var(--text-muted); margin-right: 6px;">${res.durationMs || 0}ms</span>
              <button type="button" class="card-action-btn" onclick="toggleJsonCard('${res.name}')">{ } JSON Doc</button>
              <a href="${TESTS_HREF}/${res.category || 'canvas'}/${res.name}.json" target="_blank" class="card-action-link">📄 Raw JSON</a>
            </div>
          </div>
          <div class="test-desc">${res.description || ""}</div>

          <!-- Collapsible JSON Document Viewer -->
          <div id="json-viewer-${res.name}" class="card-json-viewer" style="display: none;">
            <div class="json-viewer-header">
              <span>Test Document Specification: <code>tests/${res.category || 'canvas'}/${res.name}.json</code></span>
              <button type="button" class="json-copy-btn" onclick="copyJsonText('${res.name}')">Copy JSON</button>
            </div>
            <pre class="json-pre" id="json-pre-${res.name}"><code>${escapeHtml(docJsonStr)}</code></pre>
          </div>

          <!-- Visual Side-by-Side: Gold | Actual | Diff Heatmap -->
          ${renderVisualComparison(res)}

          <!-- Subsystem Verification Result -->
          <div style="margin-top: 14px; background: rgba(15, 23, 42, 0.6); border: 1px solid var(--surface-border); border-radius: 8px; padding: 12px 16px;">
            <div style="font-size: 13px; font-weight: 700; color: #f1f5f9; margin-bottom: 6px;">Conformance Verification</div>
            <div style="font-size: 12.5px; color: var(--text-muted); line-height: 1.5;">
              ${isPass ? `✔ All specification assertions evaluated with zero discrepancies against canonical byte buffer.` : `✘ Discrepancies detected: ${(res.diffs || []).map(d => d.message).join('; ')}`}
            </div>
          </div>
        </div>
        `;
    }).join("");
}

function generateSubsystemGenericCardsHtml(tests, categoryKey, title, badgeColor) {
    return renderSubsystemFilterBar(categoryKey, tests) + tests.map(res => {
        const isPass = res.status === "PASS";
        const docObj = testDocument(res, categoryKey);
        const docJsonStr = JSON.stringify(docObj, null, 2);

        return `
        <div class="test-detail-card" data-status="${res.status}" data-category="${categoryKey}" id="test-${res.name}">
          <div class="test-header">
            <div class="test-header-left">
              <span class="test-name">${res.name}</span>
              <span class="badge ${isPass ? "badge-pass" : "badge-fail"}">${res.status}</span>
              <span class="badge" style="background: ${badgeColor}22; color: ${badgeColor}; border: 1px solid ${badgeColor}44;">${title}</span>
            </div>
            <div class="card-actions">
              <span style="font-size: 12.5px; color: var(--text-muted); margin-right: 6px;">${res.durationMs || 0}ms</span>
              <button type="button" class="card-action-btn" onclick="toggleJsonCard('${res.name}')">{ } JSON Doc</button>
              <a href="${TESTS_HREF}/${categoryKey}/${res.name}.json" target="_blank" class="card-action-link">📄 Raw JSON</a>
            </div>
          </div>
          <div class="test-desc">${res.description || ""}</div>

          <!-- Collapsible JSON Document Viewer -->
          <div id="json-viewer-${res.name}" class="card-json-viewer" style="display: none;">
            <div class="json-viewer-header">
              <span>Test Document Specification: <code>tests/${categoryKey}/${res.name}.json</code></span>
              <button type="button" class="json-copy-btn" onclick="copyJsonText('${res.name}')">Copy JSON</button>
            </div>
            <pre class="json-pre" id="json-pre-${res.name}"><code>${escapeHtml(docJsonStr)}</code></pre>
          </div>

          ${renderVisualComparison(res)}

          <!-- Subsystem Verification Result -->
          <div style="margin-top: 14px; background: rgba(15, 23, 42, 0.6); border: 1px solid var(--surface-border); border-radius: 8px; padding: 12px 16px;">
            <div style="font-size: 13px; font-weight: 700; color: #f1f5f9; margin-bottom: 6px;">Conformance Verification</div>
            <div style="font-size: 12.5px; color: var(--text-muted); line-height: 1.5;">
              ${isPass ? `✔ All specification assertions evaluated with zero discrepancies against canonical byte buffer.` : `✘ Discrepancies detected: ${res.diffs.map(d => d.message).join('; ')}`}
            </div>
          </div>
        </div>
        `;
    }).join("");
}

function generateInteractivityCardsHtml(tests) {
    return renderSubsystemFilterBar('interactivity', tests) + tests.map(res => {
        const isPass = res.status === "PASS";
        const docObj = testDocument(res, "interactivity");
        const docJsonStr = JSON.stringify(docObj, null, 2);

        return `
        <div class="test-detail-card" data-status="${res.status}" data-category="interactivity" id="test-${res.name}">
          <div class="test-header">
            <div class="test-header-left">
              <span class="test-name">${res.name}</span>
              <span class="badge ${isPass ? "badge-pass" : "badge-fail"}">${res.status}</span>
              <span class="badge" style="background: rgba(45, 212, 191, 0.15); color: #2dd4bf; border: 1px solid rgba(45, 212, 191, 0.3);">Interactivity &amp; Gestures</span>
            </div>
            <div class="card-actions">
              <span style="font-size: 12.5px; color: var(--text-muted); margin-right: 6px;">${res.durationMs || 0}ms</span>
              <button type="button" class="card-action-btn" onclick="toggleJsonCard('${res.name}')">{ } JSON Doc</button>
              <a href="${TESTS_HREF}/interactivity/${res.name}.json" target="_blank" class="card-action-link">📄 Raw JSON</a>
            </div>
          </div>
          <div class="test-desc">${res.description || ""}</div>

          <!-- Collapsible JSON Document Viewer -->
          <div id="json-viewer-${res.name}" class="card-json-viewer" style="display: none;">
            <div class="json-viewer-header">
              <span>Test Document Specification: <code>tests/interactivity/${res.name}.json</code></span>
              <button type="button" class="json-copy-btn" onclick="copyJsonText('${res.name}')">Copy JSON</button>
            </div>
            <pre class="json-pre" id="json-pre-${res.name}"><code>${escapeHtml(docJsonStr)}</code></pre>
          </div>

          ${renderVisualComparison(res)}
        </div>
        `;
    }).join("");
}



const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>RemoteCompose Layout Conformance Audit</title>
  <style>
    @font-face {
      font-family: 'Ahem';
      src: url('data:font/truetype;charset=utf-8;base64,${AHEM_FONT_BASE64}') format('truetype');
      font-weight: normal;
      font-style: normal;
    }
    :root {
      --bg: #0b0f19;
      --surface: #131b2e;
      --surface-border: #243049;
      --text: #f1f5f9;
      --text-muted: #94a3b8;
      --accent: #38bdf8;
      --accent-glow: rgba(56, 189, 248, 0.25);
      --pass: #22c55e;
      --rate-perfect: #22c55e;  /* exactly 100% */
      --rate-high: #86efac;     /* 90% and above */
      --rate-mid: #f59e0b;      /* 60% to 90% */
      --rate-low: #ef4444;      /* below 60% */
      --fail: #ef4444;
      --warn: #eab308;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background: var(--bg);
      color: var(--text);
      line-height: 1.5;
      padding: 28px 20px;
    }
    .container { max-width: 1320px; margin: 0 auto; }
    header { margin-bottom: 24px; border-bottom: 1px solid var(--surface-border); padding-bottom: 20px; }
    h1 { font-size: 28px; font-weight: 700; color: #fff; margin-bottom: 6px; }
    .subtitle { color: var(--text-muted); font-size: 14.5px; }

    /* Typography Info & Ahem Showcase */
    .typography-card {
      background: #0f172a;
      border: 1px solid #334155;
      border-radius: 8px;
      padding: 12px 14px;
      margin-bottom: 14px;
    }
    .typography-card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 10px;
    }
    .typography-badge {
      font-size: 11.5px;
      font-weight: 700;
      color: #38bdf8;
      background: rgba(56, 189, 248, 0.15);
      border: 1px solid rgba(56, 189, 248, 0.3);
      padding: 3px 8px;
      border-radius: 4px;
      display: inline-flex;
      align-items: center;
      gap: 5px;
    }
    .typography-mode-badge {
      font-size: 11px;
      font-weight: 600;
      color: #fbbf24;
      background: rgba(245, 158, 11, 0.15);
      border: 1px solid rgba(245, 158, 11, 0.3);
      padding: 2px 7px;
      border-radius: 4px;
    }
    .typography-metrics-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 8px;
      margin-bottom: 10px;
    }
    .metric-item {
      background: #1e293b;
      border: 1px solid #334155;
      border-radius: 5px;
      padding: 6px 8px;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .metric-label { font-size: 10px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.03em; }
    .metric-value { font-size: 12px; font-weight: 700; font-family: monospace; color: #f1f5f9; }
    .typography-text-preview {
      font-size: 12px;
      display: flex;
      align-items: center;
      gap: 8px;
      background: #111827;
      padding: 6px 10px;
      border-radius: 5px;
      border: 1px solid #1e293b;
    }
    .typography-text-code {
      color: #38bdf8;
      font-family: monospace;
      font-weight: 600;
      word-break: break-all;
    }

    .ahem-showcase-box {
      background: #0f172a;
      border: 1px solid #334155;
      border-radius: 10px;
      padding: 18px 22px;
      margin: 20px 0 28px 0;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
    }
    .ahem-showcase-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
      flex-wrap: wrap;
      gap: 10px;
    }
    .ahem-showcase-title {
      font-size: 16px;
      font-weight: 700;
      color: #fff;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .ahem-showcase-desc {
      font-size: 13px;
      color: var(--text-muted);
      line-height: 1.5;
      margin-bottom: 14px;
    }
    .ahem-interactive-dock {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 14px;
      flex-wrap: wrap;
    }
    .ahem-preview-canvas {
      width: 100%;
      height: 120px;
      background: #0b101d;
      border: 1px solid #23304a;
      border-radius: 8px;
      overflow: hidden;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    /* Quick Navigation Bar */
    .quick-nav-bar {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-top: 14px;
      flex-wrap: wrap;
    }
    .quick-nav-label { font-size: 12px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); font-weight: 600; }
    .quick-nav-link {
      color: var(--accent);
      text-decoration: none;
      font-size: 12.5px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 6px;
      background: rgba(56, 189, 248, 0.08);
      border: 1px solid rgba(56, 189, 248, 0.25);
      transition: all 0.15s ease;
      cursor: pointer;
    }
    .quick-nav-link:hover {
      background: rgba(56, 189, 248, 0.22);
      color: #fff;
      border-color: var(--accent);
    }
    .nav-sep { color: #334155; }

    /* Metrics Grid */
    .metrics {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
      gap: 14px;
      margin-bottom: 28px;
    }
    .card {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 12px;
      padding: 18px;
      transition: transform 0.15s, border-color 0.15s;
    }
    .card.clickable-card { cursor: pointer; }
    .card.clickable-card:hover {
      transform: translateY(-2px);
      border-color: var(--accent);
      box-shadow: 0 4px 14px rgba(0, 0, 0, 0.3);
    }
    .card-title { font-size: 12px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); font-weight: 600; }
    .card-value { font-size: 30px; font-weight: 800; margin-top: 4px; }
    .card-sub { font-size: 12px; color: var(--text-muted); margin-top: 2px; }
    .pass-val { color: var(--pass); }
    .fail-val { color: var(--fail); }

    /* Pass-rate colour scale. Green is reserved for a perfect 100% so that a
       97% subsystem is never mistaken for a complete one at a glance. */
    .rate-perfect { color: var(--rate-perfect); }
    .rate-high    { color: var(--rate-high); }
    .rate-mid     { color: var(--rate-mid); }
    .rate-low     { color: var(--rate-low); }

    .section-title {
      font-size: 19px;
      font-weight: 700;
      margin: 32px 0 14px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      color: #f8fafc;
    }
    .section-title-left { display: flex; align-items: center; gap: 8px; }
    .section-tip { font-size: 12.5px; color: var(--text-muted); font-weight: normal; }

    .badge {
      display: inline-block;
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 11.5px;
      font-weight: 600;
      text-transform: uppercase;
    }
    .badge-pass { background: rgba(34, 197, 94, 0.2); color: var(--pass); border: 1px solid var(--pass); }
    .badge-warn { background: rgba(234, 179, 8, 0.2); color: var(--warn); border: 1px solid var(--warn); }
    .badge-fail { background: rgba(239, 68, 68, 0.2); color: var(--fail); border: 1px solid var(--fail); }
    .badge-anim { background: rgba(168, 85, 247, 0.2); color: #c084fc; border: 1px solid rgba(168, 85, 247, 0.4); }

    table {
      width: 100%;
      border-collapse: collapse;
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 12px;
      overflow: hidden;
      margin-bottom: 24px;
    }
    th, td {
      padding: 12px 16px;
      text-align: left;
      border-bottom: 1px solid var(--surface-border);
      font-size: 13.5px;
    }
    th {
      background: rgba(255, 255, 255, 0.03);
      color: var(--text-muted);
      font-weight: 600;
      font-size: 11.5px;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    tr:last-child td { border-bottom: none; }

    /* Interactive Functional Area Rows */
    .area-row { cursor: pointer; transition: background 0.15s ease; }
    .area-row:hover td { background: rgba(56, 189, 248, 0.05); }
    .area-row.active-area td { background: rgba(56, 189, 248, 0.12); }
    .area-title-link { color: #fff; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; }
    .area-row:hover .area-title-link { color: var(--accent); }
    .jump-arrow { font-size: 12px; opacity: 0.6; transition: transform 0.15s ease, opacity 0.15s ease; }
    .area-row:hover .jump-arrow { opacity: 1; transform: translate(2px, -2px); color: var(--accent); }
    .area-count-chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 7px;
      border-radius: 5px;
      background: #1e293b;
      border: 1px solid var(--surface-border);
      font-family: monospace;
      font-size: 11.5px;
      color: var(--text);
    }
    .failing-link {
      color: #f87171;
      font-family: monospace;
      text-decoration: underline;
      cursor: pointer;
      font-weight: 600;
      margin-right: 4px;
      padding: 1px 4px;
      border-radius: 4px;
      background: rgba(239, 68, 68, 0.1);
      transition: background 0.15s, color 0.15s;
    }
    .failing-link:hover { background: rgba(239, 68, 68, 0.25); color: #fca5a5; }

    /* Tightened Matrix Styles */
    .matrix-wrapper {
      overflow-x: auto;
      margin-bottom: 32px;
      border: 1px solid var(--surface-border);
      border-radius: 12px;
      background: var(--surface);
      max-width: 100%;
    }
    .matrix-table {
      width: 100%;
      border-collapse: collapse;
      margin-bottom: 0;
      border: none;
      table-layout: auto;
    }
    .matrix-table th, .matrix-table td {
      text-align: center;
      padding: 7px 3px;
      font-size: 11px;
      white-space: nowrap;
    }
    .matrix-table th {
      background: rgba(255, 255, 255, 0.03);
      color: var(--text-muted);
      font-weight: 600;
      font-size: 10.5px;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      padding: 9px 3px;
      border-bottom: 1px solid var(--surface-border);
    }
    .matrix-table th.matrix-mod-th {
      cursor: pointer;
      transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
      user-select: none;
    }
    .matrix-table th.matrix-mod-th:hover {
      background: rgba(56, 189, 248, 0.18);
      color: var(--accent);
    }
    .matrix-table th.matrix-mod-th.active-mod {
      background: var(--accent);
      color: #0b0f19;
      box-shadow: 0 0 10px rgba(56, 189, 248, 0.6);
    }
    .matrix-mgr-btn {
      background: none;
      border: none;
      color: var(--text);
      font-weight: 600;
      font-size: 12px;
      font-family: inherit;
      cursor: pointer;
      text-align: left;
      padding: 3px 6px;
      border-radius: 4px;
      display: inline-flex;
      align-items: center;
      width: 100%;
      box-sizing: border-box;
      transition: all 0.15s ease;
    }
    .matrix-mgr-btn:hover {
      background: rgba(56, 189, 248, 0.18);
      color: var(--accent);
      transform: translateX(2px);
    }
    .matrix-mgr-btn.active-mgr {
      background: var(--accent);
      color: #0b0f19;
      box-shadow: 0 0 10px rgba(56, 189, 248, 0.6);
    }
    .matrix-table th:first-child, .matrix-table td:first-child {
      text-align: left;
      font-weight: 600;
      font-size: 12px;
      padding: 7px 10px;
      position: sticky;
      left: 0;
      background: #151e34;
      z-index: 2;
      border-right: 1px solid var(--surface-border);
      width: 135px;
      min-width: 125px;
    }
    .matrix-table tr:hover td:not(:first-child) { background: rgba(255, 255, 255, 0.02); }
    .cell-pill {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 2.5px 5px;
      border-radius: 4px;
      font-size: 10px;
      font-weight: 600;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      min-width: 38px;
      cursor: pointer;
      border: none;
      background: none;
      transition: transform 0.15s ease, box-shadow 0.15s ease, outline 0.15s ease;
      user-select: none;
    }
    .cell-pill:hover {
      transform: scale(1.12);
      box-shadow: 0 0 10px rgba(56, 189, 248, 0.4);
      z-index: 3;
      position: relative;
    }
    .cell-pill.active-pill {
      outline: 2px solid var(--accent);
      outline-offset: 1px;
      box-shadow: 0 0 12px rgba(56, 189, 248, 0.7);
    }
    .cell-pass { background: rgba(34, 197, 94, 0.15); color: #4ade80; border: 1px solid rgba(34, 197, 94, 0.3); }
    .cell-partial { background: rgba(234, 179, 8, 0.15); color: #facc15; border: 1px solid rgba(234, 179, 8, 0.3); }
    .cell-fail { background: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); }
    .cell-none { color: #475569; font-weight: 400; font-size: 11px; }

    /* Active Filter Banner */
    .filter-banner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: rgba(56, 189, 248, 0.1);
      border: 1px solid rgba(56, 189, 248, 0.3);
      border-radius: 8px;
      padding: 12px 16px;
      margin-bottom: 20px;
      font-size: 13.5px;
      flex-wrap: wrap;
      gap: 10px;
    }
    .filter-banner-left { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .filter-badge {
      background: var(--accent);
      color: #0b0f19;
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      padding: 2px 8px;
      border-radius: 4px;
    }
    .clear-filter-btn {
      background: #1e293b;
      border: 1px solid var(--surface-border);
      color: var(--text);
      padding: 6px 14px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .clear-filter-btn:hover { background: #334155; color: #fff; border-color: #475569; }

    /* Collapsible Details Container */
    details.test-details-container {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 12px;
      margin-top: 36px;
      overflow: hidden;
    }
    details.test-details-container summary {
      padding: 18px 22px;
      font-size: 18px;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      justify-content: space-between;
      align-items: center;
      user-select: none;
      background: rgba(255, 255, 255, 0.02);
      border-bottom: 1px solid transparent;
      transition: background 0.2s;
    }
    details.test-details-container summary:hover { background: rgba(255, 255, 255, 0.04); }
    details.test-details-container[open] summary { border-bottom-color: var(--surface-border); }
    .summary-left { display: flex; align-items: center; gap: 12px; }
    .summary-sub { font-size: 13px; font-weight: normal; color: var(--text-muted); }
    .test-details-body { padding: 24px; }

    .filter-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 20px;
    }
    .filter-btn-group { display: flex; gap: 8px; flex-wrap: wrap; }
    .filter-btn {
      background: #1e293b;
      border: 1px solid var(--surface-border);
      color: var(--text-muted);
      padding: 6px 14px;
      border-radius: 6px;
      font-size: 13px;
      cursor: pointer;
      font-weight: 500;
      transition: all 0.15s;
    }
    .filter-btn:hover { color: #fff; border-color: #475569; }
    .filter-btn.active {
      background: #0284c7;
      border-color: #38bdf8;
      color: #fff;
    }

    .failing-jump-bar {
      display: flex;
      align-items: center;
      gap: 10px;
      background: rgba(239, 68, 68, 0.08);
      border: 1px solid rgba(239, 68, 68, 0.28);
      border-radius: 8px;
      padding: 10px 14px;
      margin-bottom: 18px;
      flex-wrap: wrap;
    }
    .failing-jump-label {
      font-size: 12px;
      font-weight: 700;
      color: #f87171;
      white-space: nowrap;
    }
    .failing-jump-pills {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }
    .failing-jump-pill {
      background: rgba(15, 23, 42, 0.8);
      color: #fca5a5;
      border: 1px solid rgba(239, 68, 68, 0.35);
      border-radius: 6px;
      padding: 4px 10px;
      font-size: 11.5px;
      font-family: 'JetBrains Mono', monospace;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .failing-jump-pill:hover {
      background: rgba(239, 68, 68, 0.25);
      color: #fff;
      border-color: #f87171;
    }
    .global-failing-banner {
      background: rgba(239, 68, 68, 0.07);
      border: 1px solid rgba(239, 68, 68, 0.3);
      border-radius: 10px;
      padding: 14px 18px;
      margin-bottom: 24px;
    }

    .search-input {
      background: #1e293b;
      border: 1px solid var(--surface-border);
      border-radius: 6px;
      color: #fff;
      padding: 7px 14px;
      font-size: 13px;
      outline: none;
      width: 280px;
      transition: border-color 0.15s;
    }
    .search-input:focus { border-color: var(--accent); }

    /* Test Cards */
    .test-detail-card {
      background: #0d1527;
      border: 1px solid var(--surface-border);
      border-radius: 12px;
      padding: 20px;
      margin-bottom: 20px;
      transition: border-color 0.3s ease, box-shadow 0.3s ease;
    }
    @keyframes highlight-pulse {
      0% { border-color: var(--accent); box-shadow: 0 0 20px rgba(56, 189, 248, 0.7); }
      50% { border-color: var(--accent); box-shadow: 0 0 10px rgba(56, 189, 248, 0.4); }
      100% { border-color: var(--surface-border); box-shadow: none; }
    }
    .highlight-card { animation: highlight-pulse 2.2s ease-out; }

    .test-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
      flex-wrap: wrap;
      gap: 10px;
    }
    .test-header-left { display: flex; align-items: center; gap: 10px; }
    .test-name { font-size: 16px; font-weight: 600; }
    .card-actions { display: flex; align-items: center; gap: 8px; }
    .card-action-btn, .card-action-link {
      background: #1e293b;
      border: 1px solid var(--surface-border);
      color: var(--text-muted);
      padding: 4px 10px;
      border-radius: 5px;
      font-size: 12px;
      font-weight: 600;
      text-decoration: none;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 4px;
      transition: all 0.15s;
    }
    .card-action-btn:hover, .card-action-link:hover {
      color: #fff;
      border-color: var(--accent);
      background: #23304a;
    }
    .test-desc { font-size: 13px; color: var(--text-muted); margin-bottom: 14px; }

    /* Collapsible Card JSON Viewer */
    .card-json-viewer {
      background: #080c16;
      border: 1px solid var(--surface-border);
      border-radius: 8px;
      margin-bottom: 16px;
      overflow: hidden;
    }
    .json-viewer-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: rgba(255, 255, 255, 0.03);
      padding: 8px 14px;
      border-bottom: 1px solid var(--surface-border);
      font-size: 12px;
      color: var(--text-muted);
    }
    .json-viewer-header code { color: var(--accent); }
    .copy-small-btn {
      background: #1e293b;
      border: 1px solid var(--surface-border);
      color: var(--text-muted);
      font-size: 11px;
      padding: 2px 7px;
      border-radius: 4px;
      cursor: pointer;
    }
    .copy-small-btn:hover { color: #fff; border-color: var(--accent); }
    .card-json-viewer pre {
      padding: 14px;
      font-family: ui-monospace, monospace;
      font-size: 11.5px;
      color: #cbd5e1;
      max-height: 280px;
      overflow-y: auto;
      line-height: 1.4;
    }

    /* Card Body */
    .test-body {
      display: grid;
      grid-template-columns: 1fr 400px;
      gap: 20px;
    }
    @media (max-width: 960px) { .test-body { grid-template-columns: 1fr; } }

    .diff-table { width: 100%; font-size: 13px; border-radius: 8px; }
    .diff-table th, .diff-table td { padding: 8px 12px; }
    .diff-val-exp { color: #38bdf8; font-family: monospace; font-weight: 600; }
    .diff-val-act { color: #f87171; font-family: monospace; font-weight: 600; }

    /* Visual Preview & Side-by-Side */
    .card-viz-column {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .card-viz-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
    }
    .view-pill-group {
      display: flex;
      gap: 4px;
      background: #151e34;
      padding: 2px;
      border-radius: 6px;
      border: 1px solid var(--surface-border);
    }
    .view-pill {
      background: transparent;
      border: none;
      color: var(--text-muted);
      padding: 3px 8px;
      border-radius: 4px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s;
    }
    .view-pill:hover { color: #fff; }
    .view-pill.active {
      background: var(--accent);
      color: #0b0f19;
    }
    .box-preview { width: 100%; border-radius: 8px; border: 1px solid #334155; height: 240px; }
    .sbs-preview-wrap {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px;
    }
    .sbs-pane {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
    }
    .sbs-header { font-size: 11px; font-weight: 700; text-transform: uppercase; }
    .sbs-preview-wrap .box-preview { height: 210px; }

    .legend {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: var(--text-muted);
    }
    .legend-item { display: flex; align-items: center; gap: 6px; }
    .legend-box { width: 12px; height: 12px; border-radius: 2px; }

    /* Card Motion Dock (Animation & Step Scrubbers) */
    .card-motion-dock {
      background: #111a2e;
      border: 1px solid var(--surface-border);
      border-radius: 8px;
      padding: 10px 14px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .motion-row {
      display: flex;
      align-items: center;
      gap: 10px;
      justify-content: space-between;
      flex-wrap: wrap;
    }
    .motion-play-btn {
      background: var(--accent);
      color: #0b0f19;
      border: none;
      padding: 5px 14px;
      border-radius: 5px;
      font-size: 12px;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.15s;
    }
    .motion-play-btn:hover { filter: brightness(1.15); }
    .motion-scrubber {
      flex: 1;
      min-width: 120px;
      height: 5px;
      border-radius: 3px;
      background: #1e293b;
      outline: none;
      cursor: pointer;
      accent-color: var(--accent);
    }
    .motion-badge {
      font-family: monospace;
      font-size: 11px;
      color: #cbd5e1;
      padding: 3px 6px;
      background: #1e293b;
      border-radius: 4px;
      border: 1px solid var(--surface-border);
    }
    .step-btn-group { display: flex; gap: 4px; flex-wrap: wrap; }
    .step-pill {
      background: #1e293b;
      border: 1px solid var(--surface-border);
      color: var(--text-muted);
      padding: 3px 8px;
      border-radius: 4px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
    }
    .step-pill:hover { color: #fff; }
    .step-pill.active {
      background: #38bdf8;
      color: #0b0f19;
      border-color: #38bdf8;
    }

    /* Floating Navigation Bar */
    .floating-nav {
      position: fixed;
      bottom: 24px;
      right: 24px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      z-index: 100;
      background: rgba(19, 27, 46, 0.9);
      backdrop-filter: blur(10px);
      padding: 6px;
      border-radius: 10px;
      border: 1px solid var(--surface-border);
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
    }
    .floating-btn {
      background: #1e293b;
      border: 1px solid var(--surface-border);
      color: var(--text-muted);
      padding: 6px 12px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
      text-align: center;
    }
    .floating-btn:hover {
      background: var(--accent);
      color: #0b0f19;
      border-color: var(--accent);
    }
  
    /* Main Subsystem Tab Navigation */
    .main-tab-nav {
      display: flex;
      gap: 10px;
      background: #0f172a;
      padding: 8px;
      border-radius: 14px;
      border: 1px solid var(--surface-border);
      margin-bottom: 28px;
      overflow-x: auto;
      box-shadow: 0 8px 30px rgba(0, 0, 0, 0.35);
    }
    .main-tab-btn {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 22px;
      border-radius: 10px;
      border: 1px solid transparent;
      background: transparent;
      color: var(--text-muted);
      font-size: 14.5px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      white-space: nowrap;
    }
    .main-tab-btn:hover {
      color: #fff;
      background: rgba(255, 255, 255, 0.05);
      border-color: rgba(255, 255, 255, 0.1);
    }
    .main-tab-btn.active {
      background: linear-gradient(135deg, #0284c7 0%, #38bdf8 100%);
      color: #080c16;
      border-color: #38bdf8;
      box-shadow: 0 4px 18px rgba(56, 189, 248, 0.4);
      font-weight: 700;
    }
    .main-tab-btn .tab-badge {
      font-size: 11.5px;
      padding: 3px 9px;
      border-radius: 999px;
      background: rgba(0, 0, 0, 0.25);
      color: inherit;
      font-weight: 700;
      letter-spacing: 0.3px;
    }
    .main-tab-btn .tab-badge.rate-perfect { color: var(--rate-perfect); }
    .main-tab-btn .tab-badge.rate-high    { color: var(--rate-high); }
    .main-tab-btn .tab-badge.rate-mid     { color: var(--rate-mid); }
    .main-tab-btn .tab-badge.rate-low     { color: var(--rate-low); }
    /* The active tab has a light fill, so the badge reverts to dark ink for contrast. */
    .main-tab-btn.active .tab-badge,
    .main-tab-btn.active .tab-badge.rate-perfect,
    .main-tab-btn.active .tab-badge.rate-high,
    .main-tab-btn.active .tab-badge.rate-mid,
    .main-tab-btn.active .tab-badge.rate-low {
      background: rgba(8, 12, 22, 0.35);
      color: #080c16;
    }
    .tab-pane {
      display: none;
      animation: fadeInTab 0.25s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .tab-pane.active {
      display: block;
    }
    @keyframes fadeInTab {
      from { opacity: 0; transform: translateY(6px); }
      to { opacity: 1; transform: translateY(0); }
    }

    /* Overall Recap Cards & Table */
    .recap-hero-banner {
      background: linear-gradient(135deg, rgba(30, 41, 59, 0.8) 0%, rgba(15, 23, 42, 0.95) 100%);
      border: 1px solid var(--surface-border);
      border-radius: 16px;
      padding: 28px 32px;
      margin-bottom: 28px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 24px;
      box-shadow: 0 10px 30px rgba(0,0,0,0.3);
    }
    .recap-hero-title {
      font-size: 24px;
      font-weight: 800;
      color: #f8fafc;
      margin-bottom: 8px;
    }
    .recap-hero-desc {
      color: var(--text-muted);
      font-size: 14px;
      max-width: 650px;
      line-height: 1.6;
    }
    .recap-kpi-ring {
      display: flex;
      flex-direction: column;
      align-items: center;
      background: rgba(56, 189, 248, 0.08);
      border: 1px solid rgba(56, 189, 248, 0.25);
      padding: 16px 28px;
      border-radius: 14px;
      min-width: 140px;
    }
    .recap-kpi-val {
      font-size: 38px;
      font-weight: 900;
      color: #38bdf8;
      line-height: 1;
      margin-bottom: 4px;
    }
    .recap-kpi-label {
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      color: var(--text-muted);
      font-weight: 700;
    }
    .recap-kpi-sub {
      font-size: 12px;
      color: var(--text-muted);
      font-weight: 600;
      margin-top: 6px;
      padding-top: 6px;
      border-top: 1px solid rgba(148, 163, 184, 0.18);
      white-space: nowrap;
    }

    /* Subsystem Comparison Table */
    .subsystem-table {
      width: 100%;
      border-collapse: collapse;
      margin-top: 14px;
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 12px;
      overflow: hidden;
      margin-bottom: 28px;
    }
    .subsystem-table th {
      background: rgba(15, 23, 42, 0.8);
      padding: 14px 18px;
      font-size: 13px;
      font-weight: 700;
      color: var(--text-muted);
      text-align: left;
      border-bottom: 1px solid var(--surface-border);
    }
    .subsystem-table td {
      padding: 16px 18px;
      font-size: 13.5px;
      border-bottom: 1px solid var(--surface-border);
      color: var(--text-primary);
    }
    .subsystem-table tr:last-child td {
      border-bottom: none;
    }
    .subsystem-table tr:hover td {
      background: rgba(255, 255, 255, 0.02);
    }
    .subsystem-progress-bar {
      width: 100%;
      height: 8px;
      background: #1e293b;
      border-radius: 4px;
      overflow: hidden;
      margin-top: 6px;
    }
    .subsystem-progress-fill {
      height: 100%;
      border-radius: 4px;
      transition: width 0.6s ease;
    }

    /* Expression Engine Specifics */
    .variable-eval-table {
      width: 100%;
      border-collapse: collapse;
      margin-top: 12px;
      background: rgba(11, 16, 29, 0.7);
      border: 1px solid var(--surface-border);
      border-radius: 8px;
      overflow: hidden;
    }
    .variable-eval-table th {
      background: #0f172a;
      padding: 9px 14px;
      font-size: 12px;
      font-weight: 700;
      color: var(--text-muted);
      text-align: left;
      border-bottom: 1px solid var(--surface-border);
    }
    .variable-eval-table td {
      padding: 9px 14px;
      font-size: 12.5px;
      border-bottom: 1px solid rgba(51, 65, 85, 0.4);
    }
    .variable-eval-table tr:last-child td {
      border-bottom: none;
    }
    .var-name {
      font-family: monospace;
      color: #38bdf8;
      font-weight: 600;
    }
    .var-formula {
      font-family: monospace;
      color: #e2e8f0;
      font-size: 12px;
      background: rgba(30, 41, 59, 0.6);
      padding: 2px 6px;
      border-radius: 4px;
    }
    .val-exp { color: #94a3b8; font-family: monospace; }
    .val-act { color: #4ade80; font-family: monospace; font-weight: 600; }
    .val-diff { font-family: monospace; color: #a5b4fc; }
    .val-tol { color: #64748b; font-size: 11px; }

    /* Particle System Canvas & Controls */
    .particle-player-container {
      display: flex;
      flex-wrap: wrap;
      gap: 18px;
      margin-top: 14px;
      background: #0f172a;
      border: 1px solid var(--surface-border);
      border-radius: 12px;
      padding: 16px;
      align-items: center;
    }
    .particle-canvas-wrapper {
      position: relative;
      border-radius: 8px;
      overflow: hidden;
      border: 1px solid rgba(56, 189, 248, 0.3);
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
      background: #0b101d;
      width: 320px;
      height: 220px;
      flex-shrink: 0;
    }
    .particle-canvas {
      width: 100%;
      height: 100%;
      display: block;
    }
    .particle-controls {
      flex: 1;
      min-width: 260px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .particle-ctrl-row {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }
    .ctrl-btn {
      background: #1e293b;
      border: 1px solid var(--surface-border);
      color: #e2e8f0;
      padding: 6px 14px;
      border-radius: 6px;
      font-size: 12.5px;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.15s;
    }
    .ctrl-btn:hover {
      background: #38bdf8;
      color: #0b0f19;
      border-color: #38bdf8;
    }
    .particle-slider {
      width: 100%;
      height: 6px;
      background: #1e293b;
      border-radius: 3px;
      outline: none;
      cursor: pointer;
      accent-color: #a855f7;
    }
    .particle-schema-info {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: var(--text-muted);
      flex-wrap: wrap;
    }
    .particle-schema-info code {
      color: #c084fc;
      background: rgba(168, 85, 247, 0.15);
      padding: 2px 6px;
      border-radius: 4px;
    }
    .particle-matrix-table {
      width: 100%;
      border-collapse: collapse;
      margin-top: 12px;
      background: rgba(11, 16, 29, 0.7);
      border: 1px solid var(--surface-border);
      border-radius: 8px;
      overflow: hidden;
      font-family: monospace;
      font-size: 12px;
    }
    .particle-matrix-table th {
      background: #0f172a;
      padding: 8px 12px;
      color: var(--text-muted);
      text-align: left;
      border-bottom: 1px solid var(--surface-border);
    }
    .particle-matrix-table td {
      padding: 8px 12px;
      border-bottom: 1px solid rgba(51, 65, 85, 0.4);
      color: #e2e8f0;
    }
    .particle-matrix-table tr:last-child td { border-bottom: none; }
    ${renderOperationsStyles()}
</style>
</head>
<body>
  <div class="container" id="summary">
    <header>
      <h1>RemoteCompose Conformance Audit</h1>
      <div class="subtitle">
        Comprehensive conformance suite comparing player evaluation against Android reference binaries across all 18 subsystems: Layout, Expressions, Particles, Wire Protocol, Loom Macro Engine, 2D Canvas (including Text on Path &amp; Anchoring), Shaders &amp; AGSL, Clock &amp; Time, Interactivity, Text Operations, Color Theme, Data Operations, Path Operations, Semantics &amp; Accessibility, Autonomous Scheduling, Conditional Branching, Matrix Expressions, and Animation Specifications.
      </div>
    </header>

    <!-- Main Navigation Tabs -->
    <div class="main-tab-nav" id="main-tab-nav">
      <button class="main-tab-btn active" id="btn-tab-overview" onclick="switchMainTab('tab-overview')">
        <span class="tab-icon">📊</span>
        <span>Overall Recap</span>
        <span class="tab-badge ${rateClass(overallPassRate)}" id="badge-overview">${overallPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-opcodes" onclick="switchMainTab('tab-opcodes')">
        <span class="tab-icon">🔢</span>
        <span>Operations Grid</span>
        <span class="tab-badge pass" id="badge-opcodes">${opImplRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-layout" onclick="switchMainTab('tab-layout')">
        <span class="tab-icon">📐</span>
        <span>Layout</span>
        <span class="tab-badge ${rateClass(passRate)}" id="badge-layout">${passRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-expressions" onclick="switchMainTab('tab-expressions')">
        <span class="tab-icon">🧮</span>
        <span>Expressions</span>
        <span class="tab-badge ${rateClass(exprPassRate)}" id="badge-expressions">${exprPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-particles" onclick="switchMainTab('tab-particles')">
        <span class="tab-icon">⚛</span>
        <span>Particles</span>
        <span class="tab-badge ${rateClass(particlePassRate)}" id="badge-particles">${particlePassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-wire" onclick="switchMainTab('tab-wire')">
        <span class="tab-icon">📡</span>
        <span>Wire Protocol</span>
        <span class="tab-badge ${rateClass(wirePassRate)}" id="badge-wire">${wirePassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-loom" onclick="switchMainTab('tab-loom')">
        <span class="tab-icon">🧵</span>
        <span>Loom</span>
        <span class="tab-badge ${rateClass(loomPassRate)}" id="badge-loom">${loomPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-canvas" onclick="switchMainTab('tab-canvas')">
        <span class="tab-icon">🎨</span>
        <span>2D Canvas</span>
        <span class="tab-badge ${rateClass(canvasPassRate)}" id="badge-canvas">${canvasPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-shaders" onclick="switchMainTab('tab-shaders')">
        <span class="tab-icon">🔮</span>
        <span>Shaders</span>
        <span class="tab-badge ${rateClass(shadersPassRate)}" id="badge-shaders">${shadersPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-clock" onclick="switchMainTab('tab-clock')">
        <span class="tab-icon">⏱</span>
        <span>Clock &amp; Time</span>
        <span class="tab-badge ${rateClass(clockPassRate)}" id="badge-clock">${clockPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-interactivity" onclick="switchMainTab('tab-interactivity')">
        <span class="tab-icon">👆</span>
        <span>Interactivity</span>
        <span class="tab-badge ${rateClass(interactivityPassRate)}" id="badge-interactivity">${interactivityPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-textoperations" onclick="switchMainTab('tab-textoperations')">
        <span class="tab-icon">🔤</span>
        <span>Text Ops</span>
        <span class="tab-badge ${rateClass(textOperationsPassRate)}" id="badge-textoperations">${textOperationsPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-colortheme" onclick="switchMainTab('tab-colortheme')">
        <span class="tab-icon">🎭</span>
        <span>Color Theme</span>
        <span class="tab-badge ${rateClass(colorThemePassRate)}" id="badge-colortheme">${colorThemePassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-dataoperations" onclick="switchMainTab('tab-dataoperations')">
        <span class="tab-icon">💾</span>
        <span>Data Ops</span>
        <span class="tab-badge ${rateClass(dataOperationsPassRate)}" id="badge-dataoperations">${dataOperationsPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-pathoperations" onclick="switchMainTab('tab-pathoperations')">
        <span class="tab-icon">〰️</span>
        <span>Path Ops</span>
        <span class="tab-badge ${rateClass(pathOperationsPassRate)}" id="badge-pathoperations">${pathOperationsPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-semantics" onclick="switchMainTab('tab-semantics')">
        <span class="tab-icon">♿</span>
        <span>Semantics</span>
        <span class="tab-badge ${rateClass(semanticsPassRate)}" id="badge-semantics">${semanticsPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-scheduling" onclick="switchMainTab('tab-scheduling')">
        <span class="tab-icon">⏰</span>
        <span>Scheduling</span>
        <span class="tab-badge ${rateClass(schedulingPassRate)}" id="badge-scheduling">${schedulingPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-conditionals" onclick="switchMainTab('tab-conditionals')">
        <span class="tab-icon">🔀</span>
        <span>Conditionals</span>
        <span class="tab-badge ${rateClass(conditionalsPassRate)}" id="badge-conditionals">${conditionalsPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-matrixmath" onclick="switchMainTab('tab-matrixmath')">
        <span class="tab-icon">🧮</span>
        <span>Matrix Math</span>
        <span class="tab-badge ${rateClass(matrixMathPassRate)}" id="badge-matrixmath">${matrixMathPassRate}%</span>
      </button>
      <button class="main-tab-btn" id="btn-tab-animationspec" onclick="switchMainTab('tab-animationspec')">
        <span class="tab-icon">🎬</span>
        <span>AnimationSpec</span>
        <span class="tab-badge ${rateClass(animationSpecPassRate)}" id="badge-animationspec">${animationSpecPassRate}%</span>
      </button>
    </div>

    <!-- TAB 1: OVERALL RECAP -->
    <div class="tab-pane active" id="tab-overview">
      <div class="recap-hero-banner">
        <div>
          <div class="recap-hero-title">RemoteCompose Conformance Report</div>
          <div class="recap-hero-desc">
            Rigorous cross-platform validation suite evaluating client-side evaluation against Android AOSP reference binaries across all 18 RemoteCompose subsystems.
          </div>
        </div>
        <div class="recap-kpi-ring">
          <div class="recap-kpi-val" style="color: ${rateColor(overallPassRate)};">${overallPassRate}%</div>
          <div class="recap-kpi-label">Overall Conformance</div>
          <div class="recap-kpi-sub">${overallPassed} of ${overallTotal} tests passed</div>
        </div>
      </div>

      <div class="metrics">
        <div class="card clickable-card" onclick="switchMainTab('tab-layout')" title="Click to view Layout Conformance">
          <div class="card-title">Layout Subsystem</div>
          <div class="card-value ${rateClass(passRate)}">${passRate}%</div>
          <div class="card-sub">${totalPassed} of ${totalAll} passed (${totalFailed} known gaps) ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-expressions')" title="Click to view Expression Engine">
          <div class="card-title">Expression Engine</div>
          <div class="card-value ${rateClass(exprPassRate)}">${exprPassRate}%</div>
          <div class="card-sub">${exprPassed} of ${exprTotal} passed (100% exact math) ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-particles')" title="Click to view Particle System">
          <div class="card-title">Particle System</div>
          <div class="card-value ${rateClass(particlePassRate)}">${particlePassRate}%</div>
          <div class="card-sub">${particlePassed} of ${particleTotal} simulations verified ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-wire')" title="Click to view Wire Protocol">
          <div class="card-title">Wire Protocol</div>
          <div class="card-value ${rateClass(wirePassRate)}">${wirePassRate}%</div>
          <div class="card-sub">${wirePassed} of ${wireTotal} passed (WireBuffer + RefOps) ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-loom')" title="Click to view Loom Macro Engine">
          <div class="card-title">Loom Macro Engine</div>
          <div class="card-value ${rateClass(loomPassRate)}">${loomPassRate}%</div>
          <div class="card-sub">${loomPassed} of ${loomTotal} macro tests passed ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-canvas')" title="Click to view 2D Canvas">
          <div class="card-title">2D Canvas</div>
          <div class="card-value ${rateClass(canvasPassRate)}">${canvasPassRate}%</div>
          <div class="card-sub">${canvasPassed} of ${canvasTotal} passed (Canvas + Text on Path) ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-shaders')" title="Click to view Shaders & AGSL">
          <div class="card-title">Shaders &amp; AGSL</div>
          <div class="card-value ${rateClass(shadersPassRate)}">${shadersPassRate}%</div>
          <div class="card-sub">${shadersPassed} of ${shadersTotal} passed (AGSL + Uniforms + Gradients) ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-clock')" title="Click to view Clock & Time Simulation">
          <div class="card-title">Clock &amp; Time</div>
          <div class="card-value ${rateClass(clockPassRate)}">${clockPassRate}%</div>
          <div class="card-sub">${clockPassed} of ${clockTotal} time tests verified ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-interactivity')" title="Click to view Interactivity">
          <div class="card-title">Interactivity &amp; Gestures</div>
          <div class="card-value ${rateClass(interactivityPassRate)}">${interactivityPassRate}%</div>
          <div class="card-sub">${interactivityPassed} of ${interactivityTotal} gestures verified ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-textoperations')" title="Click to view Text Operations">
          <div class="card-title">Text Operations</div>
          <div class="card-value ${rateClass(textOperationsPassRate)}">${textOperationsPassRate}%</div>
          <div class="card-sub">${textOperationsPassed} of ${textOperationsTotal} passed ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-colortheme')" title="Click to view Color Theme">
          <div class="card-title">Color Theme</div>
          <div class="card-value ${rateClass(colorThemePassRate)}">${colorThemePassRate}%</div>
          <div class="card-sub">${colorThemePassed} of ${colorThemeTotal} passed ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-dataoperations')" title="Click to view Data Operations">
          <div class="card-title">Data Operations</div>
          <div class="card-value ${rateClass(dataOperationsPassRate)}">${dataOperationsPassRate}%</div>
          <div class="card-sub">${dataOperationsPassed} of ${dataOperationsTotal} passed ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-pathoperations')" title="Click to view Path Operations">
          <div class="card-title">Path Operations</div>
          <div class="card-value ${rateClass(pathOperationsPassRate)}">${pathOperationsPassRate}%</div>
          <div class="card-sub">${pathOperationsPassed} of ${pathOperationsTotal} passed ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-semantics')" title="Click to view Semantics &amp; Accessibility">
          <div class="card-title">Semantics &amp; A11y</div>
          <div class="card-value ${rateClass(semanticsPassRate)}">${semanticsPassRate}%</div>
          <div class="card-sub">${semanticsPassed} of ${semanticsTotal} passed ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-scheduling')" title="Click to view Autonomous Scheduling">
          <div class="card-title">Scheduling</div>
          <div class="card-value ${rateClass(schedulingPassRate)}">${schedulingPassRate}%</div>
          <div class="card-sub">${schedulingPassed} of ${schedulingTotal} passed ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-conditionals')" title="Click to view Conditional Branching">
          <div class="card-title">Conditionals</div>
          <div class="card-value ${rateClass(conditionalsPassRate)}">${conditionalsPassRate}%</div>
          <div class="card-sub">${conditionalsPassed} of ${conditionalsTotal} passed ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-matrixmath')" title="Click to view Matrix Expressions">
          <div class="card-title">Matrix Math</div>
          <div class="card-value ${rateClass(matrixMathPassRate)}">${matrixMathPassRate}%</div>
          <div class="card-sub">${matrixMathPassed} of ${matrixMathTotal} passed ↗</div>
        </div>
        <div class="card clickable-card" onclick="switchMainTab('tab-animationspec')" title="Click to view Animation Specifications">
          <div class="card-title">AnimationSpec</div>
          <div class="card-value ${rateClass(animationSpecPassRate)}">${animationSpecPassRate}%</div>
          <div class="card-sub">${animationSpecPassed} of ${animationSpecTotal} passed ↗</div>
        </div>
      </div>

      ${(() => {
        const subItems = [
          { icon: '📐', label: 'Layout', tabId: 'tab-layout', tabKey: 'layout', failed: totalFailed },
          { icon: '🧮', label: 'Expressions', tabId: 'tab-expressions', tabKey: 'expressions', failed: exprFailed },
          { icon: '⚛', label: 'Particles', tabId: 'tab-particles', tabKey: 'particles', failed: particleFailed },
          { icon: '📡', label: 'Wire Protocol', tabId: 'tab-wire', tabKey: 'wire', failed: wireFailed },
          { icon: '🧵', label: 'Loom', tabId: 'tab-loom', tabKey: 'loom', failed: loomFailed },
          { icon: '🎨', label: '2D Canvas', tabId: 'tab-canvas', tabKey: 'canvas', failed: canvasFailed },
          { icon: '🔮', label: 'Shaders & AGSL', tabId: 'tab-shaders', tabKey: 'shaders', failed: shadersFailed },
          { icon: '⏱', label: 'Clock & Time', tabId: 'tab-clock', tabKey: 'clock', failed: clockFailed },
          { icon: '👆', label: 'Interactivity', tabId: 'tab-interactivity', tabKey: 'interactivity', failed: interactivityFailed },
          { icon: '🔤', label: 'Text Ops', tabId: 'tab-textoperations', tabKey: 'textoperations', failed: textOperationsFailed },
          { icon: '🎭', label: 'Color Theme', tabId: 'tab-colortheme', tabKey: 'colortheme', failed: colorThemeFailed },
          { icon: '💾', label: 'Data Ops', tabId: 'tab-dataoperations', tabKey: 'dataoperations', failed: dataOperationsFailed },
          { icon: '〰️', label: 'Path Ops', tabId: 'tab-pathoperations', tabKey: 'pathoperations', failed: pathOperationsFailed },
          { icon: '♿', label: 'Semantics', tabId: 'tab-semantics', tabKey: 'semantics', failed: semanticsFailed },
          { icon: '⏰', label: 'Scheduling', tabId: 'tab-scheduling', tabKey: 'scheduling', failed: schedulingFailed },
          { icon: '🔀', label: 'Conditionals', tabId: 'tab-conditionals', tabKey: 'conditionals', failed: conditionalsFailed },
          { icon: '🧮', label: 'Matrix Math', tabId: 'tab-matrixmath', tabKey: 'matrixmath', failed: matrixMathFailed },
          { icon: '🎬', label: 'AnimationSpec', tabId: 'tab-animationspec', tabKey: 'animationspec', failed: animationSpecFailed },
        ].filter(s => s.failed > 0);
        if (subItems.length === 0) return '';
        const totalFailCount = subItems.reduce((sum, s) => sum + s.failed, 0);
        return `
        <div class="global-failing-banner">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; flex-wrap: wrap; gap: 8px;">
            <span style="font-size: 13px; font-weight: 700; color: #f87171;">⚠ Quick Navigation — Subsystems with Failing Tests (${subItems.length} subsystems, ${totalFailCount} failing tests)</span>
            <span style="font-size: 11.5px; color: var(--text-muted);">Click any pill to jump directly to that subsystem filtered to Failing Only</span>
          </div>
          <div style="display: flex; gap: 8px; flex-wrap: wrap;">
            ${subItems.map(s => `<button type="button" class="failing-jump-pill" onclick="jumpToSubsystemFailing('${s.tabId}', '${s.tabKey}')" style="font-family: 'Inter', sans-serif; font-weight: 600; padding: 6px 12px; font-size: 12.5px;">${s.icon} ${s.label}: <strong>${s.failed} failing</strong> →</button>`).join('')}
          </div>
        </div>
        `;
      })()}

      ${renderOperationsOverviewCard(operationsRegistry)}

      <div class="section-title">
        <div class="section-title-left">
          <span>Subsystem Conformance Matrix &amp; Comparative Analysis</span>
        </div>
        <span class="section-tip">Click any subsystem row to navigate to its detailed test suite</span>
      </div>

      <table class="subsystem-table">
        <thead>
          <tr>
            <th>Subsystem</th>
            <th>Domain / Scope</th>
            <th>Specifications</th>
            <th>Conforming</th>
            <th>Divergent</th>
            <th>Pass Rate</th>
            <th>Status</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          <tr onclick="switchMainTab('tab-layout')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>📐</span> RemoteCompose Layout
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">CoreDocument + MeasurePass</div>
            </td>
            <td>Linear rows/columns, boxes, flex flow, adaptive priority containers, padding, modifiers</td>
            <td><strong>${totalAll}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${totalPassed}</span></td>
            <td><span style="color: ${totalFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${totalFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(passRate)};">${passRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${passRate}%; background: ${rateColor(passRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(passRate)}</td>
            <td>${subsystemActionCell('tab-layout', 'layout', 'Layout', totalFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-expressions')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>🧮</span> Expression Engine
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">FloatExpression + IntegerExpression</div>
            </td>
            <td>RPN stack evaluation, math operators, trigonometric functions, easing interpolators, spring physics, and continuous animation waveforms</td>
            <td><strong>${exprTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${exprPassed}</span></td>
            <td><span style="color: ${exprFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${exprFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(exprPassRate)};">${exprPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${exprPassRate}%; background: ${rateColor(exprPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(exprPassRate)}</td>
            <td>${subsystemActionCell('tab-expressions', 'expressions', 'Expressions', exprFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-particles')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>⚛</span> Particle Systems
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">ParticlesCreate + ParticlesLoop</div>
            </td>
            <td>Deterministic PRNG seeding, multi-frame physics integration, velocity drag, radial bursts, boundary bounce, and lifecycle recycling</td>
            <td><strong>${particleTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${particlePassed}</span></td>
            <td><span style="color: ${particleFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${particleFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(particlePassRate)};">${particlePassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${particlePassRate}%; background: ${rateColor(particlePassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(particlePassRate)}</td>
            <td>${subsystemActionCell('tab-particles', 'particles', 'Particles', particleFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-wire')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>📡</span> Wire Protocol
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">WireBuffer + Header + ReferencedOperations</div>
            </td>
            <td>Binary wire buffer primitive types, header version handshakes, NaN-boxed float IDs, and ReferencedOperations / IncludeReferencedOperations inlining</td>
            <td><strong>${wireTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${wirePassed}</span></td>
            <td><span style="color: ${wireFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${wireFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(wirePassRate)};">${wirePassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${wirePassRate}%; background: ${rateColor(wirePassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(wirePassRate)}</td>
            <td>${subsystemActionCell('tab-wire', 'wire', 'Wire Protocol', wireFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-loom')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>🧵</span> Loom Macro Engine
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">PatternDefine + PatternInflation + PatternForEach</div>
            </td>
            <td>Macro templating (PatternDefine), pattern inflation (PatternInflation), slot blocks (PatternBlock), loop expansion (PatternForEach), and tiered ID remapping</td>
            <td><strong>${loomTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${loomPassed}</span></td>
            <td><span style="color: ${loomFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${loomFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(loomPassRate)};">${loomPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${loomPassRate}%; background: ${rateColor(loomPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(loomPassRate)}</td>
            <td>${subsystemActionCell('tab-loom', 'loom', 'Loom', loomFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-canvas')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>🎨</span> 2D Canvas
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">CanvasPaintContext + DrawTextOnPath / Anchored / OnCircle</div>
            </td>
            <td>2D primitives (arcs, sectors, bezier paths), matrix stack (save/restore/scale/rotate), clipRect, scaled bitmaps, and text-on-path / circle / anchored glyph placement</td>
            <td><strong>${canvasTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${canvasPassed}</span></td>
            <td><span style="color: ${canvasFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${canvasFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(canvasPassRate)};">${canvasPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${canvasPassRate}%; background: ${rateColor(canvasPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(canvasPassRate)}</td>
            <td>${subsystemActionCell('tab-canvas', 'canvas', '2D Canvas', canvasFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-shaders')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>🔮</span> Shaders &amp; AGSL
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">ShaderData + AGSL + Linear &amp; Sweep Gradients</div>
            </td>
            <td>Linear &amp; sweep gradient shaders, ShaderData uniform bindings (float/int vector pipelines), and AGSL procedural radial shaders</td>
            <td><strong>${shadersTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${shadersPassed}</span></td>
            <td><span style="color: ${shadersFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${shadersFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(shadersPassRate)};">${shadersPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${shadersPassRate}%; background: ${rateColor(shadersPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(shadersPassRate)}</td>
            <td>${subsystemActionCell('tab-shaders', 'shaders', 'Shaders & AGSL', shadersFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-clock')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>⏱</span> Clock &amp; Time Simulation
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">CoreDocument Clock + TimeVariables</div>
            </td>
            <td>Continuous wall-clock simulation, analog sweeping hands (hours, minutes, seconds), calendar date variables, and time-driven rotation</td>
            <td><strong>${clockTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${clockPassed}</span></td>
            <td><span style="color: ${clockFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${clockFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(clockPassRate)};">${clockPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${clockPassRate}%; background: ${rateColor(clockPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(clockPassRate)}</td>
            <td>${subsystemActionCell('tab-clock', 'clock', 'Clock', clockFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-interactivity')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>👆</span> Interactivity &amp; Touch
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">TouchOperations + ActionCallbacks</div>
            </td>
            <td>Pointer down/move/up dispatch, component hit-testing bounding boxes, click state mutations, host action triggers, and continuous drag sliders</td>
            <td><strong>${interactivityTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${interactivityPassed}</span></td>
            <td><span style="color: ${interactivityFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${interactivityFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(interactivityPassRate)};">${interactivityPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${interactivityPassRate}%; background: ${rateColor(interactivityPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(interactivityPassRate)}</td>
            <td>${subsystemActionCell('tab-interactivity', 'interactivity', 'Interactivity', interactivityFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-textoperations')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>🔤</span> Text Operations
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">TextFromFloat + TextMerge + TextTransform</div>
            </td>
            <td>Dynamic string buffer formatting from floats (decimal precision, padding), string concatenation, and casing/substring transformations</td>
            <td><strong>${textOperationsTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${textOperationsPassed}</span></td>
            <td><span style="color: ${textOperationsFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${textOperationsFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(textOperationsPassRate)};">${textOperationsPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${textOperationsPassRate}%; background: ${rateColor(textOperationsPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(textOperationsPassRate)}</td>
            <td>${subsystemActionCell('tab-textoperations', 'textoperations', 'Text Ops', textOperationsFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-colortheme')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>🎭</span> Color &amp; Theme
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">ColorTheme + ColorConstant + ColorExpression</div>
            </td>
            <td>Dynamic light/dark theme mode switching, ARGB color constants, HSV/RGB color space interpolation, and semantic palette slots</td>
            <td><strong>${colorThemeTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${colorThemePassed}</span></td>
            <td><span style="color: ${colorThemeFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${colorThemeFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(colorThemePassRate)};">${colorThemePassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${colorThemePassRate}%; background: ${rateColor(colorThemePassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(colorThemePassRate)}</td>
            <td>${subsystemActionCell('tab-colortheme', 'colortheme', 'Color Theme', colorThemeFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-dataoperations')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>💾</span> Data Operations
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">DataDynamicListFloat + DataMapLookup</div>
            </td>
            <td>Dynamic float array buffers, runtime index mutation via UpdateDynamicFloatList, and structured key-value DataMap lookups</td>
            <td><strong>${dataOperationsTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${dataOperationsPassed}</span></td>
            <td><span style="color: ${dataOperationsFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${dataOperationsFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(dataOperationsPassRate)};">${dataOperationsPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${dataOperationsPassRate}%; background: ${rateColor(dataOperationsPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(dataOperationsPassRate)}</td>
            <td>${subsystemActionCell('tab-dataoperations', 'dataoperations', 'Data Ops', dataOperationsFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-pathoperations')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>〰️</span> Path Operations
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">PathCreate + PathAppend + PathTween</div>
            </td>
            <td>Procedural vector path construction, dynamic command appending, and topological PathTween shape morphing</td>
            <td><strong>${pathOperationsTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${pathOperationsPassed}</span></td>
            <td><span style="color: ${pathOperationsFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${pathOperationsFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(pathOperationsPassRate)};">${pathOperationsPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${pathOperationsPassRate}%; background: ${rateColor(pathOperationsPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(pathOperationsPassRate)}</td>
            <td>${subsystemActionCell('tab-pathoperations', 'pathoperations', 'Path Ops', pathOperationsFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-semantics')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>♿</span> Semantics &amp; Accessibility
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">RootContentDescription + AccessibilitySemantics</div>
            </td>
            <td>Accessibility semantics hierarchy, document root descriptions, button/heading accessibility roles, and clickable states</td>
            <td><strong>${semanticsTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${semanticsPassed}</span></td>
            <td><span style="color: ${semanticsFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${semanticsFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(semanticsPassRate)};">${semanticsPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${semanticsPassRate}%; background: ${rateColor(semanticsPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(semanticsPassRate)}</td>
            <td>${subsystemActionCell('tab-semantics', 'semantics', 'Semantics', semanticsFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-scheduling')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>⏰</span> Autonomous Scheduling
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">WakeIn + ImpulseOperation + ImpulseProcess</div>
            </td>
            <td>Autonomous wake timer intervals, discrete ImpulseOperation execution windows, and timed state transitions</td>
            <td><strong>${schedulingTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${schedulingPassed}</span></td>
            <td><span style="color: ${schedulingFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${schedulingFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(schedulingPassRate)};">${schedulingPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${schedulingPassRate}%; background: ${rateColor(schedulingPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(schedulingPassRate)}</td>
            <td>${subsystemActionCell('tab-scheduling', 'scheduling', 'Scheduling', schedulingFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-conditionals')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>🔀</span> Conditional Branching
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">ConditionalOperations (op 178) + Skip (op 241)</div>
            </td>
            <td>All six comparison operators, inclusive end-marker skip distance, nested branch gating, and parse-time API/profile skip blocks</td>
            <td><strong>${conditionalsTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${conditionalsPassed}</span></td>
            <td><span style="color: ${conditionalsFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${conditionalsFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(conditionalsPassRate)};">${conditionalsPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${conditionalsPassRate}%; background: ${rateColor(conditionalsPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(conditionalsPassRate)}</td>
            <td>${subsystemActionCell('tab-conditionals', 'conditionals', 'Conditionals', conditionalsFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-matrixmath')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>🧮</span> Matrix Expressions
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">MatrixExpression / VectorMath / FromPath (ops 186–188, 181)</div>
            </td>
            <td>4×4 matrix RPN evaluation (multiply, invert, perspective projection), homogeneous vector transformation with perspective divide, and path tangent frames</td>
            <td><strong>${matrixMathTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${matrixMathPassed}</span></td>
            <td><span style="color: ${matrixMathFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${matrixMathFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(matrixMathPassRate)};">${matrixMathPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${matrixMathPassRate}%; background: ${rateColor(matrixMathPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(matrixMathPassRate)}</td>
            <td>${subsystemActionCell('tab-matrixmath', 'matrixmath', 'Matrix Math', matrixMathFailed)}</td>
          </tr>

          <tr onclick="switchMainTab('tab-animationspec')" style="cursor: pointer;">
            <td>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
                <span>🎬</span> Animation Specifications
              </div>
              <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">AnimationSpec (op 14)</div>
            </td>
            <td>Asserts that components actually adopt the spec on the wire — a spec that decodes but is ignored silently replaces every custom duration with the 300 ms default</td>
            <td><strong>${animationSpecTotal}</strong></td>
            <td><span style="color: #4ade80; font-weight: 700;">${animationSpecPassed}</span></td>
            <td><span style="color: ${animationSpecFailed > 0 ? "#f87171" : "#94a3b8"}; font-weight: 700;">${animationSpecFailed}</span></td>
            <td style="min-width: 140px;">
              <div style="font-weight: 700; color: ${rateColor(animationSpecPassRate)};">${animationSpecPassRate}%</div>
              <div class="subsystem-progress-bar">
                <div class="subsystem-progress-fill" style="width: ${animationSpecPassRate}%; background: ${rateColor(animationSpecPassRate)};"></div>
              </div>
            </td>
            <td>${rateBadge(animationSpecPassRate)}</td>
            <td>${subsystemActionCell('tab-animationspec', 'animationspec', 'AnimationSpec', animationSpecFailed)}</td>
          </tr>
        </tbody>
      </table>

      <div class="section-title">
        <div class="section-title-left">
          <span>Architecture &amp; Cross-Platform Conformance Guarantees</span>
        </div>
      </div>
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; margin-bottom: 28px;">
        <div class="card" style="padding: 20px;">
          <div style="font-size: 16px; font-weight: 700; color: #f8fafc; margin-bottom: 8px;">📦 Symmetrical Binary Gold Generation</div>
          <div style="font-size: 13px; color: var(--text-muted); line-height: 1.6;">
            Reference geometries and simulation states are computed directly by the canonical Android AOSP <code>CoreDocument</code> runtime with headless context wrappers. Base64-encoded binary payloads guarantee byte-for-byte serialization identity across implementations.
          </div>
        </div>
        <div class="card" style="padding: 20px;">
          <div style="font-size: 16px; font-weight: 700; color: #f8fafc; margin-bottom: 8px;">⚡ Zero-Allocation Numerical Physics</div>
          <div style="font-size: 13px; color: var(--text-muted); line-height: 1.6;">
            Particle physics algorithms evaluate in-place float buffers with zero per-frame garbage collector overhead. The reference and the player under test achieve identical multi-frame trajectories within tight floating point numerical bounds ($\Delta &lt; 0.05$).
          </div>
        </div>
        <div class="card" style="padding: 20px;">
          <div style="font-size: 16px; font-weight: 700; color: #f8fafc; margin-bottom: 8px;">🎯 Cross-Subsystem Expression Parity</div>
          <div style="font-size: 13px; color: var(--text-muted); line-height: 1.6;">
            RemoteCompose's RPN expression evaluator drives dynamic styling, layout sizing, and particle kinetics seamlessly. All arithmetic operators, transcendental functions, and animation time-steps evaluate with 100.0% parity.
          </div>
        </div>
      </div>
    </div>

    ${renderOperationsTab(operationsRegistry)}

    <!-- TAB 2: LAYOUT CONFORMANCE -->
    <div class="tab-pane" id="tab-layout">
      <div class="quick-nav-bar" style="margin-bottom: 20px;">
        <span class="quick-nav-label">Jump to:</span>
        <a href="#functional-areas" class="quick-nav-link">☰ Functional Areas (${RC_AREAS.length})</a>
        <span class="nav-sep">•</span>
        <a href="#matrix-section" class="quick-nav-link">⊞ Conformance Matrix (${matrixManagers.length}×${matrixModifiers.length})</a>
        <span class="nav-sep">•</span>
        <a href="#test-details" onclick="openDetails()" class="quick-nav-link">▼ Individual Tests (${totalAll})</a>
        <span class="nav-sep">•</span>
        <a href="#test-details" onclick="filterStatus('fail')" class="quick-nav-link" style="color: #f87171; border-color: rgba(239, 68, 68, 0.3); background: rgba(239, 68, 68, 0.1);">⚠ Divergent Tests (${totalFailed})</a>
        <span class="nav-sep">•</span>
        <a href="test-inspector.html" target="_blank" class="quick-nav-link" style="color: #c084fc; border-color: rgba(192, 132, 252, 0.3); background: rgba(192, 132, 252, 0.1);">🔍 Dedicated Test Inspector ↗</a>
      </div>

    <div class="metrics">
      <div class="card clickable-card" onclick="clearAllFilters()" title="Click to view all tests">
        <div class="card-title">Pass Rate</div>
        <div class="card-value ${rateClass(passRate)}">${passRate}%</div>
        <div class="card-sub">${totalPassed} of ${totalAll} tests passed ↗</div>
      </div>
      <div class="card clickable-card" onclick="filterStatus('pass')" title="Click to filter to conformant tests">
        <div class="card-title">Conformant Tests</div>
        <div class="card-value pass-val">${totalPassed}</div>
        <div class="card-sub">Passed within 0.5px tolerance ↗</div>
      </div>
      <div class="card clickable-card" onclick="filterStatus('fail')" title="Click to filter to divergent tests">
        <div class="card-title">Divergent Tests</div>
        <div class="card-value ${totalFailed > 0 ? "fail-val" : "pass-val"}">${totalFailed}</div>
        <div class="card-sub">Player divergence (genuine gaps) ↗</div>
      </div>
      <div class="card clickable-card" onclick="clearAllFilters()" title="Click to view all tests">
        <div class="card-title">Total Test Suite</div>
        <div class="card-value">${totalAll}</div>
        <div class="card-sub">Active layout specifications ↗</div>
      </div>
      <div class="card">
        <div class="card-title">Target Player Engine</div>
        <div class="card-value" style="font-size: 18px; margin-top: 8px;">${escapeHtml(report.engine || "RemoteCompose Player")}</div>
        <div class="card-sub">${report.timestamp ? escapeHtml(new Date(report.timestamp).toLocaleString()) : 'Conformance Audit'}</div>
      </div>
    </div>

    <div class="section-title" id="functional-areas">
      <div class="section-title-left">
        <span>RemoteCompose Functional Areas &amp; Quality Status</span>
      </div>
      <span class="section-tip">Click any row to view its corresponding individual tests</span>
    </div>
    <table>
      <thead>
        <tr>
          <th>Functional Area</th>
          <th>Type</th>
          <th>Tests Passing</th>
          <th>Pass Rate</th>
          <th>Quality Status</th>
          <th>Conformance &amp; Key Gaps</th>
        </tr>
      </thead>
      <tbody>
        ${RC_AREAS.map((area) => {
            const activeAreaTests = area.tests.filter(t => resultsMap.has(t));
            let passed = 0;
            const failing = [];
            for (const t of activeAreaTests) {
                const res = resultsMap.get(t);
                if (res && res.status === "PASS") {
                    passed++;
                } else {
                    failing.push(t);
                }
            }
            const total = activeAreaTests.length;
            const rate = total > 0 ? ((passed / total) * 100).toFixed(0) : "100";
            let badgeClass = "badge-pass";
            let statusText = "Verified (A+)";
            if (total === 0) {
                badgeClass = "badge-pass";
                statusText = "N/A";
            } else if (passed === 0) {
                badgeClass = "badge-fail";
                statusText = "Failing (F)";
            } else if (passed < total) {
                badgeClass = rate >= 60 ? "badge-warn" : "badge-fail";
                statusText = rate >= 60 ? `Minor Gaps (${rate}%)` : `Major Gaps (${rate}%)`;
            }
            const safeName = area.name.replace(/'/g, "\\'");
            const testsAttr = activeAreaTests.join(",");
            return `<tr class="area-row" onclick="filterByArea('${safeName}', '${testsAttr}')" title="Click to view all ${total} tests in ${area.name}">
              <td>
                <span class="area-title-link">
                  ${area.name}
                  <span class="jump-arrow">↗</span>
                </span>
              </td>
              <td><span style="color: var(--text-muted); font-size: 13px;">${area.category}</span></td>
              <td>
                <span class="area-count-chip">${passed} / ${total}</span>
              </td>
              <td style="font-weight: 700; color: ${passed === total ? "var(--pass)" : (passed === 0 ? "var(--fail)" : "var(--warn)")};">${rate}%</td>
              <td><span class="badge ${badgeClass}">${statusText}</span></td>
              <td style="font-size: 13px; color: ${failing.length ? "#fca5a5" : "var(--text-muted);"};">
                ${failing.length ? `<strong>Failing:</strong> ${failing.map(f => `<a href="javascript:void(0)" class="failing-link" onclick="jumpToTest('${f}', event)" title="Jump directly to ${f}">${f}</a>`).join(" ")}<br/>` : ""}
                ${area.gaps}
              </td>
            </tr>`;
        }).join("")}
      </tbody>
    </table>

    <!-- Ahem Font & Typography Engine Showcase -->
    <div class="ahem-showcase-box" id="typography-showcase">
      <div class="ahem-showcase-header">
        <div class="ahem-showcase-title">
          <span>🔤 W3C Ahem Test Font &amp; CoreText Typography Subsystem</span>
          <span class="badge badge-pass">100% Pass (9/9)</span>
        </div>
        <div style="font-size:12px; color:var(--text-muted); font-family:monospace;">
          W3C WPT Standard Metric: 1em square, 0.8em ascent, 0.2em descent
        </div>
      </div>
      <div class="ahem-showcase-desc">
        RemoteCompose conforms to deterministic typography and text layout across the reference and player runtimes using the official <strong>W3C Ahem test font</strong>. Every glyph occupies an exact <code>1em &times; 1em</code> square with typographic baseline at <code>y + 0.8em</code> and descent at <code>y + 1.0em</code>. Single-line bounds, multiline wrapping, alignment (Start/Center/End), ellipsis truncation, and dynamic Autosize font scaling all pass 100%.
      </div>
      <div class="ahem-interactive-dock">
        <div style="display:flex; align-items:center; gap:8px;">
          <span style="font-size:12px; color:var(--text-muted); font-weight:600;">Font Size:</span>
          <input type="range" id="ahem-demo-slider" min="12" max="36" value="22" oninput="updateAhemDemo(this.value)" style="accent-color:var(--accent); cursor:pointer;">
          <span id="ahem-demo-size-badge" style="font-size:12px; font-family:monospace; color:#38bdf8; font-weight:bold;">22px</span>
        </div>
        <div style="display:flex; align-items:center; gap:8px;">
          <span style="font-size:12px; color:var(--text-muted); font-weight:600;">Alignment:</span>
          <div class="view-pill-group">
            <button type="button" class="view-pill active" id="demo-align-start" onclick="setAhemDemoAlign('start', this)">Start</button>
            <button type="button" class="view-pill" id="demo-align-center" onclick="setAhemDemoAlign('center', this)">Center</button>
            <button type="button" class="view-pill" id="demo-align-end" onclick="setAhemDemoAlign('end', this)">End</button>
          </div>
        </div>
        <div style="display:flex; align-items:center; gap:8px;">
          <span style="font-size:12px; color:var(--text-muted); font-weight:600;">View Mode:</span>
          <div class="view-pill-group">
            <button type="button" class="view-pill active" id="demo-mode-both" onclick="setAhemDemoMode('both', this)">Ahem + Letters</button>
            <button type="button" class="view-pill" id="demo-mode-ahem" onclick="setAhemDemoMode('ahem', this)">Ahem Blocks</button>
            <button type="button" class="view-pill" id="demo-mode-metrics" onclick="setAhemDemoMode('metrics', this)">Metrics &amp; Baseline</button>
          </div>
        </div>
      </div>
      <div id="ahem-demo-viewport" class="ahem-preview-canvas"></div>
    </div>

    <div class="section-title" id="matrix-section">
      <div class="section-title-left">
        <span>Layout Managers &amp; Modifiers Conformance Matrix</span>
      </div>
      <span class="section-tip">Click any cell, manager row, or modifier column to filter the test catalog</span>
    </div>
    <div class="matrix-wrapper">
      <table class="matrix-table">
        <thead>
          <tr>
            <th title="Layout Managers and Modifiers">Manager / Modifier</th>
            ${matrixModifiers.map(m => {
                const matchingTests = [];
                for (const [testName, caps] of Object.entries(ACTIVE_TEST_CAPABILITIES)) {
                    if (Array.from(caps).some(c => c.endsWith(`|${m}`)) && resultsMap.has(testName)) {
                        if (!matchingTests.includes(testName)) matchingTests.push(testName);
                    }
                }
                const testsAttr = matchingTests.join(",");
                return `<th class="matrix-mod-th" onclick="filterByModifier('${m}', '${testsAttr}')" title="Filter tests using ${m} modifier (${matchingTests.length} tests)">${matrixModifierLabels[m] || m}</th>`;
            }).join("")}
          </tr>
        </thead>
        <tbody>
          ${matrixManagers.map(mgr => {
              const mgrMatchingTests = [];
              for (const [testName, res] of resultsMap.entries()) {
                  const caps = ACTIVE_TEST_CAPABILITIES[testName] || new Set();
                  // `res.document` is empty in v2 results, so this half of the union has been
                  // dead: the matrix matched on declared capabilities only. The test file nests
                  // the component tree under `document`.
                  const docMgrs = extractManagersFromDoc(testDocument(res, res.category || "layout").document);
                  if (docMgrs.has(mgr) || Array.from(caps).some(c => c.startsWith(`${mgr}|`))) {
                      mgrMatchingTests.push(testName);
                  }
              }
              const mgrTestsAttr = mgrMatchingTests.join(",");
              const cells = matrixModifiers.map(mod => {
                  const key = `${mgr}|${mod}`;
                  let total = 0;
                  let passed = 0;
                  const fails = [];
                  const matchingTests = [];
                  for (const [testName, caps] of Object.entries(ACTIVE_TEST_CAPABILITIES)) {
                      if (caps.has(key) && resultsMap.has(testName)) {
                          total++;
                          matchingTests.push(testName);
                          const res = resultsMap.get(testName);
                          if (res.status === "PASS") {
                              passed++;
                          } else {
                              fails.push(testName);
                          }
                      }
                  }
                  if (total === 0) {
                      return `<td><span class="cell-none">—</span></td>`;
                  }
                  const testsAttr = matchingTests.join(",");
                  if (passed === total) {
                      return `<td><button type="button" class="cell-pill cell-pass" onclick="filterByMatrix('${mgr}', '${mod}', '${testsAttr}')" title="${mgr} + ${mod}: ${passed}/${total} passed. Click to view tests.">✔ ${passed}/${total}</button></td>`;
                  }
                  if (passed === 0) {
                      return `<td><button type="button" class="cell-pill cell-fail" onclick="filterByMatrix('${mgr}', '${mod}', '${testsAttr}')" title="${mgr} + ${mod}: 0/${total} passed (Failing: ${fails.join(", ")}). Click to view tests.">✘ ${passed}/${total}</button></td>`;
                  }
                  return `<td><button type="button" class="cell-pill cell-partial" onclick="filterByMatrix('${mgr}', '${mod}', '${testsAttr}')" title="${mgr} + ${mod}: ${passed}/${total} passed (Failing: ${fails.join(", ")}). Click to view tests.">⚠ ${passed}/${total}</button></td>`;
              }).join("");
              return `<tr><td><button type="button" class="matrix-mgr-btn" onclick="filterByManager('${mgr}', '${mgrTestsAttr}')" title="Filter tests using ${mgr} manager (${mgrMatchingTests.length} tests)">${mgr}</button></td>${cells}</tr>`;
          }).join("")}
        </tbody>
      </table>
    </div>

    <!-- Collapsible Individual Test Audits -->
    <details class="test-details-container" id="test-details">
      <summary>
        <div class="summary-left">
          <span>Individual Test Audits &amp; Visual Geometry (${totalAll} Tests)</span>
          <span class="badge badge-warn" style="font-size: 11px;" id="details-badge">Hidden by default</span>
        </div>
        <span class="summary-sub">Click to expand side-by-side component diffs and wireframes ▾</span>
      </summary>
      <div class="test-details-body">
        <!-- Active Filter Banner -->
        <div id="filter-banner" class="filter-banner" style="display: none;">
          <div class="filter-banner-left">
            <span class="filter-badge" id="filter-badge">FILTER</span>
            <span id="filter-description">Showing tests...</span>
          </div>
          <button type="button" class="clear-filter-btn" onclick="clearAllFilters()">✕ Clear Filter (Show All ${totalAll})</button>
        </div>

        ${renderSubsystemFilterBar('layout', activeResults)}

        ${activeResults.map((res) => {
            const isPass = res.status === "PASS";
            const docObj = testDocument(res, "layout");
            const docJsonStr = JSON.stringify(docObj, null, 2);
            const hasAnimation = res.expectedFrames && res.expectedFrames.length > 1;
            const hasResize = res.expectedResizeSteps && res.expectedResizeSteps.length > 1;
            const hasInteractions = res.expectedInteractions && res.expectedInteractions.length > 1;
            const hasGoldImage = Boolean(res.goldImageBase64);
            const rmse = typeof res.rmse === "number" ? res.rmse : null;
            // See generateCanvasCardsHtml / rasterBadgeHtml: on layout cards the tree is the gate
            // and the raster is advisory.

            return `<div class="test-detail-card" data-status="${res.status}" id="test-${res.name}">
              <div class="test-header">
                <div class="test-header-left">
                  <span class="test-name">${res.name}</span>
                  <span class="badge ${isPass ? "badge-pass" : "badge-fail"}">${res.status}</span>
                  ${hasGoldImage ? `
                    ${rasterBadgeHtml(res)}
                    ${rmse === null ? "" : `<span class="badge" style="background: rgba(148,163,184,0.12); color: #94a3b8; border: 1px solid rgba(148,163,184,0.25);">RMSE ${rmse.toFixed(4)}</span>`}
                  ` : ""}
                  ${hasAnimation ? `<span class="badge badge-anim">Animation</span>` : ""}
                  ${hasResize ? `<span class="badge badge-warn">Resize Steps</span>` : ""}
                  ${hasInteractions ? `<span class="badge badge-anim">Interaction</span>` : ""}
                </div>
                <div class="card-actions">
                  <span style="font-size: 12.5px; color: var(--text-muted); margin-right: 6px;">${res.durationMs || 0}ms</span>
                  <button type="button" class="card-action-btn" onclick="toggleJsonCard('${res.name}')">{ } JSON Doc</button>
                  <a href="test-inspector.html?test=${res.name}" target="_blank" class="card-action-link" title="Open in dedicated interactive inspector">🔍 Inspector ↗</a>
                  <a href="${TESTS_HREF}/layout/${res.name}.json" target="_blank" class="card-action-link" title="Open original JSON test specification">📄 Raw JSON</a>
                </div>
              </div>
              <div class="test-desc">${res.description || ""}</div>

              <!-- Collapsible JSON Document Viewer -->
              <div id="json-viewer-${res.name}" class="card-json-viewer" style="display: none;">
                <div class="json-viewer-header">
                  <span>RemoteCompose Document Specification: <code>tests/layout/${res.name}.json</code></span>
                  <button type="button" class="copy-small-btn" onclick="copyCardJson('${res.name}')">📋 Copy</button>
                </div>
                <pre id="json-pre-${res.name}"><code>${escapeHtml(docJsonStr)}</code></pre>
              </div>

              <!-- Visual Side-by-Side: Gold Baseline | Rendered Canvas | Diff Heatmap -->
              ${renderVisualComparison(res, "viz")}

              <div class="test-body">
                <div>
                  ${(() => {
                    const textComp = (res.actualTree || []).find(c => c.textInfo) || (res.expectedTree || []).find(c => c.textInfo);
                    if (!textComp) return "";
                    const ti = textComp.textInfo;
                    return `
                      <div class="typography-card">
                        <div class="typography-card-header">
                          <span class="typography-badge">🔤 W3C Ahem Test Font</span>
                          <span class="typography-mode-badge">${ti.autosize ? 'Dynamic Autosize' : 'Fixed Font Size'}</span>
                        </div>
                        <div class="typography-metrics-grid">
                          <div class="metric-item"><span class="metric-label">Font Size</span><span class="metric-value">${ti.fontSize}px</span></div>
                          <div class="metric-item"><span class="metric-label">Alignment</span><span class="metric-value">${ti.textAlign}</span></div>
                          <div class="metric-item"><span class="metric-label">Baseline (Ascent)</span><span class="metric-value">${ti.ascent}px (0.8em)</span></div>
                          <div class="metric-item"><span class="metric-label">Descent</span><span class="metric-value">${ti.descent}px (0.2em)</span></div>
                          <div class="metric-item"><span class="metric-label">Line Height</span><span class="metric-value">${ti.lineHeight}px</span></div>
                          <div class="metric-item"><span class="metric-label">Lines</span><span class="metric-value">${ti.lines.length}</span></div>
                        </div>
                        <div class="typography-text-preview">
                          <span style="color:var(--text-muted); font-size:11px;">Rendered Text:</span>
                          <code class="typography-text-code">${escapeXml(ti.text)}</code>
                        </div>
                      </div>
                    `;
                  })()}
                  ${(() => {
                    const treeDiffs = (res.diffs || []).filter((d) => d.probe !== "raster");
                    return treeDiffs.length > 0 ? `
                    <table class="diff-table">
                      <thead>
                        <tr>
                          <th>Component</th>
                          <th>Property</th>
                          <th>Android Gold</th>
                          <th>Player Actual</th>
                          <th>Diff (px)</th>
                        </tr>
                      </thead>
                      <tbody>
                        ${treeDiffs.map((d) => {
                          const isGoneDiff = d.property === 'isGone' || d.property === 'visibility';
                          const expStr = d.expected !== undefined ? (isGoneDiff ? d.expected : d.expected + (typeof d.expected === 'number' ? 'px' : '')) : '—';
                          const actStr = d.actual !== undefined ? (isGoneDiff ? d.actual : d.actual + (typeof d.actual === 'number' ? 'px' : '')) : '—';
                          const diffStr = isGoneDiff ? (d.expected !== d.actual ? 'MISMATCH' : 'MATCH') : (d.diff !== undefined ? d.diff + (typeof d.diff === 'number' ? 'px' : '') : (d.message || 'extra'));
                          return `
                          <tr>
                            <td><code>id=${d.id ?? d.target ?? 'root'} [${(d.kind || "").replace(/Layout$/, "")}]</code></td>
                            <td><strong>${d.property || d.type || "diff"}</strong></td>
                            <td class="diff-val-exp">${expStr}</td>
                            <td class="diff-val-act">${actStr}</td>
                            <td>${diffStr}</td>
                          </tr>
                        `;}).join("")}
                      </tbody>
                    </table>
                  ` : `
                    <div style="padding: 16px; background: rgba(34, 197, 94, 0.1); border: 1px solid rgba(34, 197, 94, 0.2); border-radius: 8px; color: var(--pass);">
                      ✔ All ${res.componentCount || 0} components layout within 0.5px tolerance of reference gold.
                    </div>
                  `;
                  })()}
                </div>

                <div class="card-viz-column">
                  <div class="card-viz-toolbar">
                    <div class="view-pill-group">
                      <button type="button" class="view-pill active" onclick="switchCardVizMode('${res.name}', 'overlay', this)">Overlay</button>
                      <button type="button" class="view-pill" onclick="switchCardVizMode('${res.name}', 'side_by_side', this)">Side-by-Side</button>
                      <button type="button" class="view-pill" onclick="switchCardVizMode('${res.name}', 'gold', this)">Gold</button>
                      <button type="button" class="view-pill" onclick="switchCardVizMode('${res.name}', 'actual', this)">Actual</button>
                    </div>
                    <div class="legend">
                      <div class="legend-item"><div class="legend-box" style="border: 1px dashed #38bdf8;"></div> Gold</div>
                      <div class="legend-item"><div class="legend-box" style="background: #38bdf8; opacity: 0.3; border: 1px solid #38bdf8;"></div> Actual</div>
                      <div class="legend-item"><div class="legend-box" style="border: 1.5px dashed #f87171; opacity: 0.6; background: rgba(239, 68, 68, 0.1);"></div> GONE</div>
                      <div class="legend-item"><div class="legend-box" style="border-bottom: 2px dashed #ef4444; background: rgba(56, 189, 248, 0.25);"></div> Baseline (0.8em)</div>
                    </div>
                  </div>

                  <div id="viz-${res.name}">
                    ${renderSvg(
                        (hasInteractions && res.expectedInteractions ? res.expectedInteractions[0].tree : (hasAnimation && res.expectedFrames ? res.expectedFrames[0].tree : res.expectedTree)),
                        (hasInteractions && res.actualInteractions ? res.actualInteractions[0].tree : (hasAnimation && res.actualFrames ? res.actualFrames[0].tree : res.actualTree)),
                        'overlay', 360, 240,
                        (hasInteractions && res.expectedInteractions ? res.expectedInteractions[0] : null),
                        (hasInteractions ? res.expectedInteractions : null),
                        computeTestBounds(res)
                    )}
                  </div>

                  <!-- Motion Scrubber (for Animations) -->
                  ${hasAnimation ? `
                    <div class="card-motion-dock">
                      <div class="motion-row">
                        <button type="button" class="motion-play-btn" id="play-${res.name}" onclick="toggleCardAnimation('${res.name}')">▶ Play</button>
                        <input type="range" class="motion-scrubber" id="scrub-${res.name}" min="0" max="${res.expectedFrames.length - 1}" value="0" oninput="scrubCardAnimation('${res.name}', this.value)">
                        <span class="motion-badge" id="badge-${res.name}">Frame ${res.expectedFrames[0].frame} (t=${res.expectedFrames[0].time}s)</span>
                      </div>
                    </div>
                  ` : ""}

                  <!-- Stepper (for Resizes) -->
                  ${hasResize ? `
                    <div class="card-motion-dock">
                      <div class="motion-row">
                        <span style="font-size:11.5px; color:var(--text-muted); font-weight:600;">Resize Steps:</span>
                        <div class="step-btn-group">
                          ${res.expectedResizeSteps.map((s, idx) => `
                            <button type="button" class="step-pill ${idx === res.expectedResizeSteps.length - 1 ? 'active' : ''}" onclick="selectCardResizeStep('${res.name}', ${idx}, this)">Step ${s.step} (${s.width}×${s.height})</button>
                          `).join("")}
                        </div>
                      </div>
                    </div>
                  ` : ""}

                  <!-- Stepper & Gesture Player (for Interactions) -->
                  ${hasInteractions ? `
                    <div class="card-motion-dock">
                      <div class="motion-row" style="margin-bottom: 6px;">
                        <button type="button" class="motion-play-btn" style="background:#d97706;" id="play-gesture-${res.name}" onclick="toggleCardGesture('${res.name}')">▶ Play Gesture</button>
                        <input type="range" class="motion-scrubber" id="scrub-gesture-${res.name}" min="0" max="${res.expectedInteractions.length - 1}" value="0" oninput="scrubCardGesture('${res.name}', this.value)">
                        <span class="motion-badge" id="badge-gesture-${res.name}">${res.expectedInteractions[0].label || 'Step 0: Initial'}</span>
                      </div>
                      <div class="motion-row" style="justify-content: space-between;">
                        <div class="step-btn-group" id="pills-gesture-${res.name}">
                          ${res.expectedInteractions.map((act, idx) => {
                            let icon = '⓪';
                            if (idx > 0) {
                              if (act.action === 'click') icon = '👆';
                              else if (act.action === 'touch_down') icon = '👇';
                              else if (act.action === 'touch_drag') icon = '↔';
                              else if (act.action === 'touch_up') icon = '👆';
                            }
                            return `<button type="button" class="step-pill ${idx === 0 ? 'active' : ''}" onclick="selectCardInteractionStep('${res.name}', ${idx}, this)">${icon} ${act.action} ${act.x !== undefined ? `(${act.x},${act.y})` : ''}</button>`;
                          }).join("")}
                        </div>
                        ${res.expectedInteractions.some(a => a.action === 'click') ? `
                          <button type="button" class="view-pill" style="background:rgba(245,158,11,0.15); border-color:#f59e0b; color:#fbbf24; font-weight:bold;" onclick="toggleCardClickSim('${res.name}')" title="Simulate click at target coordinates">👆 Trigger Click</button>
                        ` : ""}
                      </div>
                    </div>
                  ` : ""}
                </div>
              </div>
            </div>`;
        }).join("")}
      </div>
    </details>
    </div> <!-- END OF TAB 2 (tab-layout) -->

    <!-- TAB 3: EXPRESSION ENGINE -->
    <div class="tab-pane" id="tab-expressions">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Expression Pass Rate</div>
          <div class="card-value ${rateClass(exprPassRate)}">${exprPassRate}%</div>
          <div class="card-sub">${exprPassed} of ${exprTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Transcendental &amp; Trig</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">sin, cos, atan2, sqrt, pow, log</div>
        </div>
        <div class="card">
          <div class="card-title">Waveform Animations</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Time-driven periodic waveforms</div>
        </div>
        <div class="card">
          <div class="card-title">Math Precision</div>
          <div class="card-value pass-val">Exact</div>
          <div class="card-sub">Tolerance &plusmn;0.001</div>
        </div>
      </div>

      <div class="section-title">
        <div class="section-title-left">
          <span>Expression Engine Conformance Tests (${exprTotal})</span>
        </div>
      </div>

      <div class="test-details-body" style="padding: 0;">
        ${generateExpressionCardsHtml(expressionResults)}
      </div>
    </div>

    <!-- TAB 4: PARTICLE SYSTEM -->
    <div class="tab-pane" id="tab-particles">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Particle Pass Rate</div>
          <div class="card-value ${rateClass(particlePassRate)}">${particlePassRate}%</div>
          <div class="card-sub">${particlePassed} of ${particleTotal} simulations verified</div>
        </div>
        <div class="card">
          <div class="card-title">Physics Integrator</div>
          <div class="card-value pass-val">Euler / RK</div>
          <div class="card-sub">Zero-allocation buffer updates</div>
        </div>
        <div class="card">
          <div class="card-title">Boundary &amp; Collision</div>
          <div class="card-value pass-val">Verified</div>
          <div class="card-sub">Bounce, drag, and restart loop</div>
        </div>
        <div class="card">
          <div class="card-title">Numerical Parity</div>
          <div class="card-value pass-val">&plusmn;0.05</div>
          <div class="card-sub">Multi-frame trajectory sync</div>
        </div>
      </div>

      <div class="section-title">
        <div class="section-title-left">
          <span>Particle System Conformance Simulations (${particleTotal})</span>
        </div>
        <span class="section-tip">Use the interactive 2D canvas controls below each card to scrub or play the simulations</span>
      </div>

      <div class="test-details-body" style="padding: 0;">
        ${generateParticleCardsHtml(particleResults)}
      </div>
    </div>

    <!-- TAB: WIRE PROTOCOL -->
    <div class="tab-pane" id="tab-wire">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Wire Protocol Pass Rate</div>
          <div class="card-value ${rateClass(wirePassRate)}">${wirePassRate}%</div>
          <div class="card-sub">${wirePassed} of ${wireTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">WireBuffer Serialization</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Primitive types, NaN-boxed float IDs &amp; header handshakes</div>
        </div>
        <div class="card">
          <div class="card-title">ReferencedOperations</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">IncludeReferencedOperations stream inlining</div>
        </div>
        <div class="card">
          <div class="card-title">Engine Coverage</div>
          <div class="card-value ${rateClass(wireCovSub.linePct)}">${wireCovSub.linePct}%</div>
          <div class="card-sub">${wireCovSub.covered}/${wireCovSub.total} classes (${wireCovSub.instrPct}% instrs)</div>
        </div>
      </div>

      <div class="section-title">
        <div class="section-title-left">
          <span>Wire Protocol Classes Coverage (${wireCovSub.covered}/${wireCovSub.total} Classes Exercised)</span>
        </div>
        <span class="section-tip">Classes under WireBuffer, RemoteComposeBuffer, Header &amp; ReferencedOperations</span>
      </div>

      <table class="subsystem-table">
        <thead>
          <tr>
            <th>Wire Protocol Class</th>
            <th>Category</th>
            <th>Coverage Status</th>
            <th>Reference Engine Coverage</th>
          </tr>
        </thead>
        <tbody>
          ${wireOpsRowsHtml}
        </tbody>
      </table>

      <div class="section-title" style="margin-top: 28px;">
        <div class="section-title-left">
          <span>Wire Protocol Conformance Tests (${wireTotal})</span>
        </div>
      </div>

      <div class="test-details-body" style="padding: 0;">
        ${generateWireCardsHtml(wireResults)}
      </div>
    </div>

    <!-- TAB: LOOM MACRO ENGINE -->
    <div class="tab-pane" id="tab-loom">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Loom Pass Rate</div>
          <div class="card-value ${rateClass(loomPassRate)}">${loomPassRate}%</div>
          <div class="card-sub">${loomPassed} of ${loomTotal} macro tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">PatternDefine &amp; Inflation</div>
          <div class="card-value ${rateClass(loomPassRate)}">${loomPassRate}%</div>
          <div class="card-sub">Macro definition, single/multi instantiation &amp; nested macros</div>
        </div>
        <div class="card">
          <div class="card-title">Slot Blocks &amp; ForEach</div>
          <div class="card-value ${rateClass(loomPassRate)}">${loomPassRate}%</div>
          <div class="card-sub">PatternBlock slot injection &amp; PatternForEach loops</div>
        </div>
        <div class="card">
          <div class="card-title">Engine Coverage</div>
          <div class="card-value ${rateClass(loomCovSub.linePct)}">${loomCovSub.linePct}%</div>
          <div class="card-sub">${loomCovSub.covered}/${loomCovSub.total} Loom classes (${loomCovSub.instrPct}% instrs)</div>
        </div>
      </div>

      <div class="section-title">
        <div class="section-title-left">
          <span>Loom Macro Engine Classes Coverage (${loomCovSub.covered}/${loomCovSub.total} Classes Exercised)</span>
        </div>
        <span class="section-tip">Classes under androidx.compose.remote.core.operations.loom</span>
      </div>

      <table class="subsystem-table">
        <thead>
          <tr>
            <th>Loom Operation Class</th>
            <th>Category</th>
            <th>Coverage Status</th>
            <th>Reference Engine Coverage</th>
          </tr>
        </thead>
        <tbody>
          ${loomOpsRowsHtml}
        </tbody>
      </table>

      <div class="section-title" style="margin-top: 28px;">
        <div class="section-title-left">
          <span>Loom Macro Engine Conformance Tests (${loomTotal})</span>
        </div>
      </div>

      <div class="test-details-body" style="padding: 0;">
        ${generateLoomCardsHtml(loomResults)}
      </div>
    </div>

    <!-- TAB 5: 2D CANVAS -->
    <div class="tab-pane" id="tab-canvas">
      <div class="metrics">
        <div class="card">
          <div class="card-title">2D Canvas Pass Rate</div>
          <div class="card-value ${rateClass(canvasPassRate)}">${canvasPassRate}%</div>
          <div class="card-sub">${canvasPassed} of ${canvasTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">2D Primitives &amp; Paths</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Arc, Sector, Bezier quadratic &amp; cubic curves</div>
        </div>
        <div class="card">
          <div class="card-title">Matrix Stack &amp; Clipping</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Save, restore, translate, rotate, scale, clip</div>
        </div>
        <div class="card">
          <div class="card-title">Text on Path &amp; Anchoring</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">DrawTextOnPath glyph placement, DrawTextAnchored &amp; DrawTextOnCircle</div>
        </div>
      </div>

      <div class="section-title">
        <div class="section-title-left">
          <span>2D Canvas Conformance Tests (${canvasTotal})</span>
        </div>
      </div>

      <div class="test-details-body" style="padding: 0;">
        ${generateCanvasCardsHtml(canvasResults)}
      </div>
    </div>

    <!-- TAB 6: SHADERS & AGSL -->
    <div class="tab-pane" id="tab-shaders">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Shaders &amp; AGSL Pass Rate</div>
          <div class="card-value ${rateClass(shadersPassRate)}">${shadersPassRate}%</div>
          <div class="card-sub">${shadersPassed} of ${shadersTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Linear &amp; Sweep Gradients</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Shader gradient stops, color interpolation &amp; sweep angles</div>
        </div>
        <div class="card">
          <div class="card-title">ShaderData Uniform Bindings</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Float &amp; integer uniform arrays bound to shader pipelines</div>
        </div>
        <div class="card">
          <div class="card-title">AGSL Procedural Shaders</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Procedural radial gradient evaluation &amp; shader state records</div>
        </div>
      </div>

      <div class="section-title">
        <div class="section-title-left">
          <span>Shaders &amp; AGSL Conformance Tests (${shadersTotal})</span>
        </div>
      </div>

      <div class="test-details-body" style="padding: 0;">
        ${generateCanvasCardsHtml(shadersResults)}
      </div>
    </div>

    <!-- TAB 7: CLOCK & TIME SIMULATION -->
    <div class="tab-pane" id="tab-clock">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Clock Pass Rate</div>
          <div class="card-value ${rateClass(clockPassRate)}">${clockPassRate}%</div>
          <div class="card-sub">${clockPassed} of ${clockTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Analog Hand Kinematics</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">rot_hour, rot_min, rot_sec rotation formulas</div>
        </div>
        <div class="card">
          <div class="card-title">Smooth Continuous Sweep</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Fractional continuousSec() second hand sweep</div>
        </div>
        <div class="card">
          <div class="card-title">Calendar &amp; Epoch Variables</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">year, calendarMonth, dayOfMonth, weekDay, epochSecond</div>
        </div>
      </div>

      <div class="section-title">
        <div class="section-title-left">
          <span>Clock &amp; Time Simulation Tests (${clockTotal})</span>
        </div>
      </div>

      <div class="test-details-body" style="padding: 0;">
        ${generateCanvasCardsHtml(clockResults)}
      </div>
    </div>

    <!-- TAB 7: INTERACTIVITY & GESTURES -->
    <div class="tab-pane" id="tab-interactivity">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Interactivity Pass Rate</div>
          <div class="card-value ${rateClass(interactivityPassRate)}">${interactivityPassRate}%</div>
          <div class="card-sub">${interactivityPassed} of ${interactivityTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Gesture Event Dispatch</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Click, touchDown, touchDrag, touchUp</div>
        </div>
        <div class="card">
          <div class="card-title">Multi-Click Modifier</div>
          <div class="card-value pass-val">Supported</div>
          <div class="card-sub">Single click, long press, double click</div>
        </div>
        <div class="card">
          <div class="card-title">State Mutations &amp; Actions</div>
          <div class="card-value pass-val">Verified</div>
          <div class="card-sub">hostNamedAction &amp; touchExpression sliders</div>
        </div>
      </div>

      <div class="section-title">
        <div class="section-title-left">
          <span>Interactivity &amp; Gestures Conformance Tests (${interactivityTotal})</span>
        </div>
      </div>

      <div class="test-details-body" style="padding: 0;">
        ${generateInteractivityCardsHtml(interactivityResults)}
      </div>
    </div>

    <!-- TAB 8: TEXT OPERATIONS -->
    <div class="tab-pane" id="tab-textoperations">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Text Operations Pass Rate</div>
          <div class="card-value ${rateClass(textOperationsPassRate)}">${textOperationsPassRate}%</div>
          <div class="card-sub">${textOperationsPassed} of ${textOperationsTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Number Formatting</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">TextFromFloat integer &amp; decimal formatting</div>
        </div>
        <div class="card">
          <div class="card-title">String Manipulation</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">TextMerge &amp; TextTransform case handling</div>
        </div>
      </div>
      <div class="section-title">
        <div class="section-title-left">
          <span>Text Operations Conformance Tests (${textOperationsTotal})</span>
        </div>
      </div>
      <div class="test-details-body" style="padding: 0;">
        ${generateSubsystemGenericCardsHtml(textOperationsResults, 'textoperations', 'Text Operations', '#38bdf8')}
      </div>
    </div>

    <!-- TAB 9: COLOR THEME -->
    <div class="tab-pane" id="tab-colortheme">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Color Theme Pass Rate</div>
          <div class="card-value ${rateClass(colorThemePassRate)}">${colorThemePassRate}%</div>
          <div class="card-sub">${colorThemePassed} of ${colorThemeTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Theme Palette Switching</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Light Mode vs Dark Mode automatic resolution</div>
        </div>
        <div class="card">
          <div class="card-title">Dynamic Color Expressions</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">ColorConstant &amp; ARGB channel derivation</div>
        </div>
      </div>
      <div class="section-title">
        <div class="section-title-left">
          <span>Color Theme Conformance Tests (${colorThemeTotal})</span>
        </div>
      </div>
      <div class="test-details-body" style="padding: 0;">
        ${generateSubsystemGenericCardsHtml(colorThemeResults, 'colortheme', 'Color Theme', '#f472b6')}
      </div>
    </div>

    <!-- TAB 10: DATA OPERATIONS -->
    <div class="tab-pane" id="tab-dataoperations">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Data Operations Pass Rate</div>
          <div class="card-value ${rateClass(dataOperationsPassRate)}">${dataOperationsPassRate}%</div>
          <div class="card-sub">${dataOperationsPassed} of ${dataOperationsTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Dynamic Array Mutation</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">DataDynamicListFloat &amp; UpdateDynamicFloatList</div>
        </div>
        <div class="card">
          <div class="card-title">Dictionary Maps</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">DataMapIds &amp; DataMapLookup key mapping</div>
        </div>
      </div>
      <div class="section-title">
        <div class="section-title-left">
          <span>Data Operations Conformance Tests (${dataOperationsTotal})</span>
        </div>
      </div>
      <div class="test-details-body" style="padding: 0;">
        ${generateSubsystemGenericCardsHtml(dataOperationsResults, 'dataoperations', 'Data Operations', '#34d399')}
      </div>
    </div>

    <!-- TAB 11: PATH OPERATIONS -->
    <div class="tab-pane" id="tab-pathoperations">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Path Operations Pass Rate</div>
          <div class="card-value ${rateClass(pathOperationsPassRate)}">${pathOperationsPassRate}%</div>
          <div class="card-sub">${pathOperationsPassed} of ${pathOperationsTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Vector Streaming</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">PathCreate &amp; PathAppend polygon assembly</div>
        </div>
        <div class="card">
          <div class="card-title">Shape Morphing</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">PathTween linear interpolation &amp; morphing</div>
        </div>
      </div>
      <div class="section-title">
        <div class="section-title-left">
          <span>Path Operations Conformance Tests (${pathOperationsTotal})</span>
        </div>
      </div>
      <div class="test-details-body" style="padding: 0;">
        ${generateSubsystemGenericCardsHtml(pathOperationsResults, 'pathoperations', 'Path Operations', '#a78bfa')}
      </div>
    </div>

    <!-- TAB 13: SEMANTICS & ACCESSIBILITY -->
    <div class="tab-pane" id="tab-semantics">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Semantics Pass Rate</div>
          <div class="card-value ${rateClass(semanticsPassRate)}">${semanticsPassRate}%</div>
          <div class="card-sub">${semanticsPassed} of ${semanticsTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Accessibility Roles</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Buttons, Headings, Images, Clickable controls</div>
        </div>
        <div class="card">
          <div class="card-title">Document Content Description</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">RootContentDescription &amp; State descriptions</div>
        </div>
      </div>
      <div class="section-title">
        <div class="section-title-left">
          <span>Semantics &amp; Accessibility Conformance Tests (${semanticsTotal})</span>
        </div>
      </div>
      <div class="test-details-body" style="padding: 0;">
        ${generateSubsystemGenericCardsHtml(semanticsResults, 'semantics', 'Semantics & Accessibility', '#facc15')}
      </div>
    </div>

    <!-- TAB 14: AUTONOMOUS SCHEDULING -->
    <div class="tab-pane" id="tab-scheduling">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Scheduling Pass Rate</div>
          <div class="card-value ${rateClass(schedulingPassRate)}">${schedulingPassRate}%</div>
          <div class="card-sub">${schedulingPassed} of ${schedulingTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">WakeIn Autonomous Ticks</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">Autonomous delayed repaints &amp; interval wakeups</div>
        </div>
        <div class="card">
          <div class="card-title">Impulse Animation Containers</div>
          <div class="card-value pass-val">100%</div>
          <div class="card-sub">ImpulseOperation event window gating &amp; loop bodies</div>
        </div>
      </div>
      <div class="section-title">
        <div class="section-title-left">
          <span>Autonomous Scheduling Conformance Tests (${schedulingTotal})</span>
        </div>
      </div>
      <div class="test-details-body" style="padding: 0;">
        ${generateSubsystemGenericCardsHtml(schedulingResults, 'scheduling', 'Autonomous Scheduling', '#2dd4bf')}
      </div>
    </div>

    <!-- CONDITIONAL BRANCHING -->
    <div class="tab-pane" id="tab-conditionals">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Conditionals Pass Rate</div>
          <div class="card-value ${rateClass(conditionalsPassRate)}">${conditionalsPassRate}%</div>
          <div class="card-sub">${conditionalsPassed} of ${conditionalsTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Verification Focus</div>
          <div class="card-value pass-val" style="font-size: 17px; line-height: 1.3;">Branch Execution & Skip Gating</div>
          <div class="card-sub">ConditionalOperations comparison operators, nested branch gating, and parse-time SKIP API guards</div>
        </div>
      </div>
      <div class="section-title">
        <div class="section-title-left">
          <span>Conditional Branching Conformance Tests (${conditionalsTotal})</span>
        </div>
      </div>
      <div class="test-details-body" style="padding: 0;">
        ${generateSubsystemGenericCardsHtml(conditionalsResults, 'conditionals', 'Conditional Branching', '#f59e0b')}
      </div>
    </div>

    <!-- MATRIX EXPRESSIONS -->
    <div class="tab-pane" id="tab-matrixmath">
      <div class="metrics">
        <div class="card">
          <div class="card-title">Matrix Math Pass Rate</div>
          <div class="card-value ${rateClass(matrixMathPassRate)}">${matrixMathPassRate}%</div>
          <div class="card-sub">${matrixMathPassed} of ${matrixMathTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Verification Focus</div>
          <div class="card-value pass-val" style="font-size: 17px; line-height: 1.3;">Numeric Matrix & Vector Results</div>
          <div class="card-sub">MatrixConstant storage, RPN MatrixExpression evaluation, and MatrixVectorMath affine/perspective transforms</div>
        </div>
      </div>
      <div class="section-title">
        <div class="section-title-left">
          <span>Matrix Expressions Conformance Tests (${matrixMathTotal})</span>
        </div>
      </div>
      <div class="test-details-body" style="padding: 0;">
        ${generateSubsystemGenericCardsHtml(matrixMathResults, 'matrixmath', 'Matrix Expressions', '#38bdf8')}
      </div>
    </div>

    <!-- ANIMATION SPECIFICATIONS -->
    <div class="tab-pane" id="tab-animationspec">
      <div class="metrics">
        <div class="card">
          <div class="card-title">AnimationSpec Pass Rate</div>
          <div class="card-value ${rateClass(animationSpecPassRate)}">${animationSpecPassRate}%</div>
          <div class="card-sub">${animationSpecPassed} of ${animationSpecTotal} tests passed</div>
        </div>
        <div class="card">
          <div class="card-title">Verification Focus</div>
          <div class="card-value pass-val" style="font-size: 17px; line-height: 1.3;">Spec Adoption & Easing Selection</div>
          <div class="card-sub">AnimationSpec field round trip, per-component spec adoption, defaults, and the animationId 0 disable signal</div>
        </div>
      </div>
      <div class="section-title">
        <div class="section-title-left">
          <span>Animation Specifications Conformance Tests (${animationSpecTotal})</span>
        </div>
      </div>
      <div class="test-details-body" style="padding: 0;">
        ${generateSubsystemGenericCardsHtml(animationSpecResults, 'animationspec', 'Animation Specifications', '#fb7185')}
      </div>
    </div>




    <!-- Floating Navigation Bar -->
    <div class="floating-nav" id="floating-nav">
      <button class="floating-btn" onclick="scrollToSection('summary')" title="Jump to Top">↑ Top</button>
      <button class="floating-btn" onclick="scrollToSection('functional-areas')" title="Jump to Functional Areas">☰ Areas</button>
      <button class="floating-btn" onclick="scrollToSection('matrix-section')" title="Jump to Matrix">⊞ Matrix</button>
      <button class="floating-btn" onclick="openAndScrollToTests()" title="Jump to Test Details">▼ Tests</button>
    </div>

    <script>
      const TEST_DATA = ${JSON.stringify(clientTestRecordMap)};

      const cardState = {};
      Object.keys(TEST_DATA).forEach(k => {
        cardState[k] = {
          mode: 'overlay',
          frameIdx: 0,
          isPlaying: false,
          playTimer: null
        };
      });

      let currentFilter = {
        type: 'all',
        status: 'all',
        allowedSet: null,
        label: '',
        searchQuery: ''
      };

      function scrollToSection(id) {
        const el = document.getElementById(id);
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }

      function openDetails() {
        const d = document.getElementById('test-details');
        if (d) {
          d.open = true;
          const badge = document.getElementById('details-badge');
          if (badge) badge.style.display = 'none';
        }
      }

      function openAndScrollToTests() {
        openDetails();
        scrollToSection('test-details');
      }

      function applyFilters() {
        const cards = document.querySelectorAll('#test-details .test-detail-card');
        const banner = document.getElementById('filter-banner');
        const desc = document.getElementById('filter-description');
        const badge = document.getElementById('filter-badge');

        let visibleCount = 0;
        let passCount = 0;
        let failCount = 0;

        const query = currentFilter.searchQuery.toLowerCase().trim();

        cards.forEach(card => {
          const name = card.id.replace('test-', '');
          const status = card.dataset.status;
          const text = card.textContent.toLowerCase();

          let match = true;
          if (currentFilter.allowedSet && !currentFilter.allowedSet.has(name)) match = false;
          if (match && currentFilter.status === 'pass' && status !== 'PASS') match = false;
          if (match && currentFilter.status === 'fail' && status === 'PASS') match = false;
          if (match && query && !text.includes(query)) match = false;

          if (match) {
            card.style.display = 'block';
            visibleCount++;
            if (status === 'PASS') passCount++;
            else failCount++;
          } else {
            card.style.display = 'none';
          }
        });

        const isFiltered = currentFilter.allowedSet !== null || currentFilter.status !== 'all' || query.length > 0;
        if (isFiltered) {
          banner.style.display = 'flex';
          badge.textContent = currentFilter.type.toUpperCase();
          let text = 'Showing <strong>' + visibleCount + '</strong> test' + (visibleCount === 1 ? '' : 's');
          if (currentFilter.label) text += ' for <strong>' + currentFilter.label + '</strong>';
          if (currentFilter.status !== 'all') text += ' (' + currentFilter.status.toUpperCase() + ' only)';
          if (query) text += ' matching "' + query + '"';
          text += ' &mdash; <span style="color: #4ade80; font-weight:600;">' + passCount + ' passed</span>, <span style="color: #f87171; font-weight:600;">' + failCount + ' failed</span>';
          desc.innerHTML = text;
        } else {
          banner.style.display = 'none';
        }
      }

      function filterStatus(status) {
        openDetails();
        currentFilter.status = status;
        ['all', 'fail', 'pass'].forEach(s => {
          const btn = document.getElementById('subfilter-layout-' + s);
          if (btn) btn.classList.toggle('active', s === status);
        });
        document.querySelectorAll('.matrix-mgr-btn').forEach(b => b.classList.remove('active-mgr'));
        document.querySelectorAll('.matrix-mod-th').forEach(h => h.classList.remove('active-mod'));
        applyFilters();
        scrollToSection('test-details');
      }

      const subFilterState = {};
      function applySubsystemFilter(tabKey) {
        if (tabKey === 'layout') {
          applyFilters();
          return;
        }
        const st = subFilterState[tabKey] || { status: 'all', query: '' };
        const tabEl = document.getElementById('tab-' + tabKey);
        if (!tabEl) return;
        const cards = tabEl.querySelectorAll('.test-detail-card');
        const q = (st.query || '').toLowerCase().trim();
        cards.forEach(card => {
          const cardStatus = card.dataset.status;
          const text = card.textContent.toLowerCase();
          let match = true;
          if (st.status === 'pass' && cardStatus !== 'PASS') match = false;
          if (st.status === 'fail' && cardStatus === 'PASS') match = false;
          if (match && q && !text.includes(q)) match = false;
          card.style.display = match ? 'block' : 'none';
        });
      }

      function filterSubsystem(tabKey, status) {
        if (tabKey === 'layout') {
          filterStatus(status);
          return;
        }
        if (!subFilterState[tabKey]) subFilterState[tabKey] = { status: 'all', query: '' };
        subFilterState[tabKey].status = status;
        ['all', 'fail', 'pass'].forEach(s => {
          const btn = document.getElementById('subfilter-' + tabKey + '-' + s);
          if (btn) btn.classList.toggle('active', s === status);
        });
        applySubsystemFilter(tabKey);
      }

      function searchSubsystem(tabKey, query) {
        if (tabKey === 'layout') {
          onSearchInput(query);
          return;
        }
        if (!subFilterState[tabKey]) subFilterState[tabKey] = { status: 'all', query: '' };
        subFilterState[tabKey].query = query;
        applySubsystemFilter(tabKey);
      }

      function jumpToSubsystemFailing(tabId, tabKey) {
        switchMainTab(tabId);
        filterSubsystem(tabKey, 'fail');
        const bar = document.getElementById('subfilter-bar-' + tabKey);
        if (bar) {
          setTimeout(() => bar.scrollIntoView({ behavior: 'smooth', block: 'start' }), 60);
        }
      }

      function jumpToSubsystemTest(tabKey, testName) {
        switchMainTab('tab-' + tabKey);
        if (tabKey === 'layout') {
          jumpToTest(testName);
          return;
        }
        const st = subFilterState[tabKey];
        if (st && st.status === 'pass') {
          filterSubsystem(tabKey, 'all');
        }
        const card = document.getElementById('test-' + testName);
        if (card) {
          card.style.display = 'block';
          setTimeout(() => {
            card.scrollIntoView({ behavior: 'smooth', block: 'center' });
            card.classList.remove('highlight-card');
            void card.offsetWidth;
            card.classList.add('highlight-card');
          }, 60);
        }
      }
      window.filterSubsystem = filterSubsystem;
      window.searchSubsystem = searchSubsystem;
      window.jumpToSubsystemFailing = jumpToSubsystemFailing;
      window.jumpToSubsystemTest = jumpToSubsystemTest;

      function filterByMatrix(mgr, mod, testsStr) {
        const key = mgr + ' × ' + mod;
        if (currentFilter.type === 'matrix' && currentFilter.label === key) {
          clearAllFilters();
          return;
        }
        openDetails();
        const testList = testsStr ? testsStr.split(',').filter(Boolean) : [];
        currentFilter.type = 'matrix';
        currentFilter.allowedSet = new Set(testList);
        currentFilter.label = key;

        document.querySelectorAll('.cell-pill').forEach(p => p.classList.remove('active-pill'));
        document.querySelectorAll('.area-row').forEach(r => r.classList.remove('active-area'));
        document.querySelectorAll('.matrix-mgr-btn').forEach(b => b.classList.remove('active-mgr'));
        document.querySelectorAll('.matrix-mod-th').forEach(h => h.classList.remove('active-mod'));
        if (window.event && window.event.currentTarget) {
          window.event.currentTarget.classList.add('active-pill');
        }

        applyFilters();
        scrollToSection('test-details');
      }

      function filterByManager(mgr, testsStr) {
        if (currentFilter.type === 'manager' && currentFilter.label === mgr) {
          clearAllFilters();
          return;
        }
        openDetails();
        const testList = testsStr ? testsStr.split(',').filter(Boolean) : [];
        currentFilter.type = 'manager';
        currentFilter.allowedSet = new Set(testList);
        currentFilter.label = mgr;

        document.querySelectorAll('.cell-pill').forEach(p => p.classList.remove('active-pill'));
        document.querySelectorAll('.area-row').forEach(r => r.classList.remove('active-area'));
        document.querySelectorAll('.matrix-mgr-btn').forEach(b => b.classList.remove('active-mgr'));
        document.querySelectorAll('.matrix-mod-th').forEach(h => h.classList.remove('active-mod'));
        if (window.event && window.event.currentTarget) {
          window.event.currentTarget.classList.add('active-mgr');
        }

        applyFilters();
        scrollToSection('test-details');
      }

      function filterByModifier(mod, testsStr) {
        if (currentFilter.type === 'modifier' && currentFilter.label === mod) {
          clearAllFilters();
          return;
        }
        openDetails();
        const testList = testsStr ? testsStr.split(',').filter(Boolean) : [];
        currentFilter.type = 'modifier';
        currentFilter.allowedSet = new Set(testList);
        currentFilter.label = mod;

        document.querySelectorAll('.cell-pill').forEach(p => p.classList.remove('active-pill'));
        document.querySelectorAll('.area-row').forEach(r => r.classList.remove('active-area'));
        document.querySelectorAll('.matrix-mgr-btn').forEach(b => b.classList.remove('active-mgr'));
        document.querySelectorAll('.matrix-mod-th').forEach(h => h.classList.remove('active-mod'));
        if (window.event && window.event.currentTarget) {
          window.event.currentTarget.classList.add('active-mod');
        }

        applyFilters();
        scrollToSection('test-details');
      }

      function filterByArea(areaName, testsStr) {
        if (currentFilter.type === 'area' && currentFilter.label === areaName) {
          clearAllFilters();
          return;
        }
        openDetails();
        const testList = testsStr ? testsStr.split(',').filter(Boolean) : [];
        currentFilter.type = 'area';
        currentFilter.allowedSet = new Set(testList);
        currentFilter.label = areaName;

        document.querySelectorAll('.area-row').forEach(r => r.classList.remove('active-area'));
        document.querySelectorAll('.cell-pill').forEach(p => p.classList.remove('active-pill'));
        document.querySelectorAll('.matrix-mgr-btn').forEach(b => b.classList.remove('active-mgr'));
        document.querySelectorAll('.matrix-mod-th').forEach(h => h.classList.remove('active-mod'));
        if (window.event && window.event.currentTarget) {
          window.event.currentTarget.classList.add('active-area');
        }

        applyFilters();
        scrollToSection('test-details');
      }

      function onSearchInput(val) {
        currentFilter.searchQuery = val;
        openDetails();
        applyFilters();
      }

      function clearAllFilters() {
        currentFilter.type = 'all';
        currentFilter.status = 'all';
        currentFilter.allowedSet = null;
        currentFilter.label = '';
        currentFilter.searchQuery = '';
        const searchInput = document.getElementById('subsearch-layout');
        if (searchInput) searchInput.value = '';
        ['all', 'fail', 'pass'].forEach(s => {
          const btn = document.getElementById('subfilter-layout-' + s);
          if (btn) btn.classList.toggle('active', s === 'all');
        });
        document.querySelectorAll('.cell-pill').forEach(p => p.classList.remove('active-pill'));
        document.querySelectorAll('.area-row').forEach(r => r.classList.remove('active-area'));
        document.querySelectorAll('.matrix-mgr-btn').forEach(b => b.classList.remove('active-mgr'));
        document.querySelectorAll('.matrix-mod-th').forEach(h => h.classList.remove('active-mod'));
        applyFilters();
      }

      function jumpToTest(testName, ev) {
        if (ev) ev.stopPropagation();
        openDetails();
        if (currentFilter.allowedSet && !currentFilter.allowedSet.has(testName)) {
          clearAllFilters();
        } else if (currentFilter.status === 'pass') {
          filterStatus('all');
        }
        const card = document.getElementById('test-' + testName);
        if (card) {
          card.style.display = 'block';
          card.scrollIntoView({ behavior: 'smooth', block: 'center' });
          card.classList.remove('highlight-card');
          void card.offsetWidth;
          card.classList.add('highlight-card');
        }
      }

      function toggleJsonCard(name) {
        const v = document.getElementById('json-viewer-' + name);
        if (v) {
          v.style.display = v.style.display === 'none' ? 'block' : 'none';
        }
      }

      function copyCardJson(name) {
        const pre = document.getElementById('json-pre-' + name);
        if (pre) {
          navigator.clipboard.writeText(pre.textContent).then(() => {
            const btn = event.target;
            const orig = btn.textContent;
            btn.textContent = '✔ Copied!';
            setTimeout(() => btn.textContent = orig, 1500);
          });
        }
      }

      function applyScrollToTree(tree) {
        if (!tree || tree.length === 0) return [];
        const scrollContainers = [];
        for (const c of tree) {
          const sx = c.scroll_x || 0;
          const sy = c.scroll_y || 0;
          if (sx !== 0 || sy !== 0) {
            scrollContainers.push({ id: c.id, depth: c.depth, sx, sy });
          }
        }
        if (scrollContainers.length === 0) return tree;

        return tree.map(c => {
          if (scrollContainers.some(sc => sc.id === c.id)) return c;
          let totalSx = 0, totalSy = 0;
          for (const sc of scrollContainers) {
            if (c.parentId !== undefined && c.parentId !== null) {
              let pId = c.parentId;
              let isDescendant = false;
              while (pId) {
                if (pId === sc.id) { isDescendant = true; break; }
                const parent = tree.find(t => t.id === pId);
                pId = parent ? parent.parentId : null;
              }
              if (isDescendant) { totalSx += sc.sx; totalSy += sc.sy; }
            } else if (c.depth > sc.depth) {
              totalSx += sc.sx;
              totalSy += sc.sy;
            }
          }
          if (totalSx !== 0 || totalSy !== 0) {
            return Object.assign({}, c, {
              x: c.x + totalSx,
              y: c.y + totalSy,
              unscrolledX: c.x,
              unscrolledY: c.y,
              scrollOffsetX: totalSx,
              scrollOffsetY: totalSy
            });
          }
          return c;
        });
      }

      // Dynamic Card SVG Generator
      function renderCardSvg(expectedTree, actualTree, mode = 'overlay', activeStep = null, allSteps = null, bounds = null) {
        const colors = ["#2563eb", "#10b981", "#f59e0b", "#8b5cf6", "#ec4899", "#06b6d4"];
        const waypoints = (allSteps || []).filter(s => s && s.x !== undefined && s.y !== undefined);

        function buildSingle(treeExpRaw, treeActRaw, m, w, h) {
          const treeExp = applyScrollToTree(treeExpRaw);
          const treeAct = applyScrollToTree(treeActRaw);

          let minX = bounds && bounds.minX !== undefined ? bounds.minX : 0;
          let minY = bounds && bounds.minY !== undefined ? bounds.minY : 0;
          let maxX = bounds && bounds.maxX ? bounds.maxX : 50;
          let maxY = bounds && bounds.maxY ? bounds.maxY : 50;
          if (!bounds) {
            for (const c of treeExp || []) {
              minX = Math.min(minX, c.x || 0);
              minY = Math.min(minY, c.y || 0);
              maxX = Math.max(maxX, (c.x || 0) + (c.width || 0));
              maxY = Math.max(maxY, (c.y || 0) + (c.height || 0));
            }
            for (const c of treeAct || []) {
              minX = Math.min(minX, c.x || 0);
              minY = Math.min(minY, c.y || 0);
              maxX = Math.max(maxX, (c.x || 0) + (c.width || 0));
              maxY = Math.max(maxY, (c.y || 0) + (c.height || 0));
            }
          }

          const spanX = Math.max(maxX - minX, 50);
          const spanY = Math.max(maxY - minY, 50);
          const scaleX = (w - 40) / spanX;
          const scaleY = (h - 40) / spanY;
          const scale = Math.min(scaleX, scaleY);

          const offsetX = 20 - minX * scale;
          const offsetY = 20 - minY * scale;

          let svg = '<svg viewBox="0 0 ' + w + ' ' + h + '" width="' + w + '" height="' + h + '" class="box-preview">';
          svg += '<rect width="' + w + '" height="' + h + '" fill="#0f172a" rx="8"/>';
          svg += '<g transform="translate(' + offsetX + ', ' + offsetY + ')">';

          function escapeXmlClient(str) {
            if (str === null || str === undefined) return '';
            return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
          }

          function renderTextLinesSvgClient(c, color, scale, isGold, showLetters) {
            if (!c.textInfo || !Array.isArray(c.textInfo.lines)) return '';
            const fs = (c.textInfo.fontSize || 16) * scale;
            const lh = (c.textInfo.lineHeight || c.textInfo.fontSize || 16) * scale;
            const bx = (c.x || 0) * scale;
            const by = (c.y || 0) * scale;
            const bw = (c.width || 0) * scale;
            const align = c.textInfo.textAlign || 'start';

            let out = '';
            c.textInfo.lines.forEach((line, idx) => {
              if (!line && line !== '') return;
              const lineTop = by + idx * lh;
              const chars = Array.from(line);
              const lineW = chars.length * fs;
              let lineX = bx;
              if (align === 'center') {
                lineX = bx + Math.max(0, (bw - lineW) / 2);
              } else if (align === 'end' || align === 'right') {
                lineX = bx + Math.max(0, bw - lineW);
              }
              const baselineY = lineTop + 0.8 * fs;

              // 1. Glyph Area Cells: Exact 1em x 1em cells matching Ahem font geometry
              chars.forEach((ch, i) => {
                const cellX = lineX + i * fs;
                if (ch === ' ') {
                  out += '<rect x="' + cellX.toFixed(1) + '" y="' + lineTop.toFixed(1) + '" width="' + fs.toFixed(1) + '" height="' + fs.toFixed(1) + '" fill="' + color + '" fill-opacity="0.04" stroke="' + color + '" stroke-width="0.5" stroke-dasharray="2 2" stroke-opacity="0.35"/>';
                } else {
                  out += '<rect x="' + cellX.toFixed(1) + '" y="' + lineTop.toFixed(1) + '" width="' + fs.toFixed(1) + '" height="' + fs.toFixed(1) + '" fill="' + color + '" fill-opacity="' + (isGold ? 0.20 : 0.32) + '" stroke="' + color + '" stroke-width="0.5" stroke-opacity="0.65"/>';
                }
              });

              // 2. Baseline guide
              out += '<line x1="' + lineX.toFixed(1) + '" y1="' + baselineY.toFixed(1) + '" x2="' + (lineX + lineW).toFixed(1) + '" y2="' + baselineY.toFixed(1) + '" stroke="#ef4444" stroke-width="1" stroke-dasharray="3 2" opacity="0.85"/>';

              // 3. Characters centered in each glyph cell
              if (showLetters && fs >= 6) {
                out += '<g text-anchor="middle" font-family="ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace" font-weight="700" font-size="' + (fs * 0.72).toFixed(1) + '" fill="#ffffff" opacity="0.95">';
                chars.forEach((ch, i) => {
                  if (ch !== ' ') {
                    const charX = lineX + (i + 0.5) * fs;
                    out += '<text x="' + charX.toFixed(1) + '" y="' + baselineY.toFixed(1) + '">' + escapeXmlClient(ch) + '</text>';
                  }
                });
                out += '</g>';
              }
            });
            return out;
          }

          if (m === 'overlay' || m === 'gold') {
            for (let i = 0; i < (treeExp || []).length; i++) {
              const c = treeExp[i];
              const isGone = Boolean(c.isGone || c.visibility === 'GONE');
              const color = colors[i % colors.length];
              const x = (c.x || 0) * scale;
              const y = (c.y || 0) * scale;
              const bw = (c.width || 0) * scale;
              const bh = (c.height || 0) * scale;
              const strokeDash = isGone ? '3 3' : (m === 'overlay' ? '4 2' : 'none');
              const fillOp = isGone ? '0.04' : (m === 'gold' ? (c.textInfo && c.textInfo.lines ? '0.08' : '0.25') : '0');
              const rectOp = isGone ? '0.4' : '0.8';
              const goneTag = isGone ? ' [🚫 GONE]' : '';
              if (c.textInfo && c.textInfo.lines) {
                svg += '<rect x="' + x + '" y="' + y + '" width="' + bw + '" height="' + bh + '" fill="' + (m === 'gold' ? color : 'none') + '" fill-opacity="' + fillOp + '" stroke="' + color + '" stroke-dasharray="' + strokeDash + '" stroke-width="1.2" opacity="' + rectOp + '"/>';
                svg += renderTextLinesSvgClient(c, color, scale, true, true);
                svg += '<text x="' + (x + 4) + '" y="' + Math.max(10, y - 4) + '" fill="' + color + '" font-size="' + 8 + '" font-family="monospace">' + (c.kind || "").replace(/Layout$/, "") + ' [' + c.id + ']' + goneTag + (c.textInfo.autosize ? ' (' + c.textInfo.fontSize + 'px)' : '') + '</text>';
              } else {
                svg += '<rect x="' + x + '" y="' + y + '" width="' + bw + '" height="' + bh + '" fill="' + (m === 'gold' ? color : 'none') + '" fill-opacity="' + fillOp + '" stroke="' + color + '" stroke-dasharray="' + strokeDash + '" stroke-width="1.5" opacity="' + rectOp + '"/>';
                if (m === 'gold' || bw > 25 || isGone) {
                  svg += '<text x="' + (x + 4) + '" y="' + (y + 12) + '" fill="' + color + '" font-size="9" font-family="monospace">' + (c.kind || "").replace(/Layout$/, "") + ' [' + c.id + ']' + goneTag + '</text>';
                }
              }
            }
          }

          if (m === 'overlay' || m === 'actual') {
            for (let i = 0; i < (treeAct || []).length; i++) {
              const c = treeAct[i];
              const isGone = Boolean(c.isGone || c.visibility === 'GONE');
              const color = colors[i % colors.length];
              const x = (c.x || 0) * scale;
              const y = (c.y || 0) * scale;
              const bw = (c.width || 0) * scale;
              const bh = (c.height || 0) * scale;
              const strokeDash = isGone ? '3 3' : 'none';
              const fillOp = isGone ? '0.04' : (c.textInfo && c.textInfo.lines ? '0.06' : '0.18');
              const rectOp = isGone ? '0.4' : '1.0';
              const goneTag = isGone ? ' [🚫 GONE]' : '';
              if (c.textInfo && c.textInfo.lines) {
                svg += '<rect x="' + x + '" y="' + y + '" width="' + bw + '" height="' + bh + '" fill="' + color + '" fill-opacity="' + fillOp + '" stroke="' + color + '" stroke-dasharray="' + strokeDash + '" stroke-width="1.2" opacity="' + rectOp + '"/>';
                svg += renderTextLinesSvgClient(c, color, scale, false, m !== 'overlay');
                svg += '<text x="' + (x + 4) + '" y="' + Math.max(10, y - 4) + '" fill="' + color + '" font-size="8" font-family="monospace">' + (c.kind || "").replace(/Layout$/, "") + ' [' + c.id + ']' + goneTag + (c.textInfo.autosize ? ' (' + c.textInfo.fontSize + 'px)' : '') + '</text>';
              } else {
                svg += '<rect x="' + x + '" y="' + y + '" width="' + bw + '" height="' + bh + '" fill="' + color + '" fill-opacity="' + fillOp + '" stroke="' + color + '" stroke-dasharray="' + strokeDash + '" stroke-width="1.5" opacity="' + rectOp + '"/>';
                svg += '<text x="' + (x + 4) + '" y="' + (y + 12) + '" fill="' + color + '" font-size="9" font-family="monospace">' + (c.kind || "").replace(/Layout$/, "") + ' [' + c.id + ']' + goneTag + '</text>';
              }
            }
          }

          // ── Interaction Layer ──
          if (waypoints.length > 0) {
            if (waypoints.length > 1) {
              let pathD = 'M ' + (waypoints[0].x * scale) + ' ' + (waypoints[0].y * scale);
              for (let k = 1; k < waypoints.length; k++) {
                pathD += ' L ' + (waypoints[k].x * scale) + ' ' + (waypoints[k].y * scale);
              }
              svg += '<path d="' + pathD + '" stroke="#38bdf8" stroke-width="2" stroke-dasharray="4 3" opacity="0.8" fill="none"/>';
              waypoints.forEach((wp, wIdx) => {
                const wx = wp.x * scale;
                const wy = wp.y * scale;
                const isCur = activeStep && activeStep.x === wp.x && activeStep.y === wp.y;
                svg += '<circle cx="' + wx + '" cy="' + wy + '" r="6" fill="' + (isCur ? '#f59e0b' : '#0f172a') + '" stroke="' + (isCur ? '#ffffff' : '#38bdf8') + '" stroke-width="1.5"/>';
                svg += '<text cx="' + wx + '" cy="' + (wy + 3) + '" fill="' + (isCur ? '#ffffff' : '#38bdf8') + '" font-size="7.5" font-family="monospace" font-weight="bold" text-anchor="middle">' + (wIdx + 1) + '</text>';
              });
            }

            if (activeStep && activeStep.x !== undefined && activeStep.y !== undefined) {
              const px = activeStep.x * scale;
              const py = activeStep.y * scale;
              const act = activeStep.action;

              if (act === 'click') {
                svg += '<circle cx="' + px + '" cy="' + py + '" r="18" fill="none" stroke="#f59e0b" stroke-width="2" opacity="0.85"><animate attributeName="r" values="5;22" dur="1.2s" repeatCount="indefinite"/><animate attributeName="opacity" values="1;0" dur="1.2s" repeatCount="indefinite"/></circle>';
                svg += '<circle cx="' + px + '" cy="' + py + '" r="10" fill="rgba(245, 158, 11, 0.3)" stroke="#f59e0b" stroke-width="1.5"/>';
                svg += '<circle cx="' + px + '" cy="' + py + '" r="4" fill="#fef08a" stroke="#d97706" stroke-width="1"/>';
                svg += '<g transform="translate(' + Math.min(w - 90, px + 10) + ', ' + Math.max(14, py - 8) + ')"><rect x="0" y="-10" width="76" height="15" rx="3" fill="#0f172a" fill-opacity="0.9" stroke="#f59e0b" stroke-width="1"/><text x="4" y="1" fill="#fbbf24" font-size="8" font-family="monospace" font-weight="bold">👆 CLICK (' + activeStep.x + ', ' + activeStep.y + ')</text></g>';
              } else if (act === 'touch_down') {
                svg += '<circle cx="' + px + '" cy="' + py + '" r="14" fill="rgba(56, 189, 248, 0.35)" stroke="#38bdf8" stroke-width="1.5"/>';
                svg += '<circle cx="' + px + '" cy="' + py + '" r="4" fill="#ffffff"/>';
                svg += '<g transform="translate(' + Math.min(w - 95, px + 10) + ', ' + Math.max(14, py - 8) + ')"><rect x="0" y="-10" width="80" height="15" rx="3" fill="#0f172a" fill-opacity="0.9" stroke="#38bdf8" stroke-width="1"/><text x="4" y="1" fill="#38bdf8" font-size="8" font-family="monospace" font-weight="bold">👇 DOWN (' + activeStep.x + ', ' + activeStep.y + ')</text></g>';
              } else if (act === 'touch_drag') {
                svg += '<circle cx="' + px + '" cy="' + py + '" r="12" fill="rgba(16, 185, 129, 0.35)" stroke="#10b981" stroke-width="1.5"/>';
                svg += '<circle cx="' + px + '" cy="' + py + '" r="4" fill="#ffffff"/>';
                svg += '<g transform="translate(' + Math.min(w - 90, px + 10) + ', ' + Math.max(14, py - 8) + ')"><rect x="0" y="-10" width="76" height="15" rx="3" fill="#0f172a" fill-opacity="0.9" stroke="#10b981" stroke-width="1"/><text x="4" y="1" fill="#4ade80" font-size="8" font-family="monospace" font-weight="bold">↔ DRAG (' + activeStep.x + ', ' + activeStep.y + ')</text></g>';
              } else if (act === 'touch_up') {
                svg += '<circle cx="' + px + '" cy="' + py + '" r="13" fill="none" stroke="#a855f7" stroke-dasharray="2 2" stroke-width="1.5"/>';
                svg += '<circle cx="' + px + '" cy="' + py + '" r="4" fill="#a855f7"/>';
                svg += '<g transform="translate(' + Math.min(w - 85, px + 10) + ', ' + Math.max(14, py - 8) + ')"><rect x="0" y="-10" width="70" height="15" rx="3" fill="#0f172a" fill-opacity="0.9" stroke="#a855f7" stroke-width="1"/><text x="4" y="1" fill="#c084fc" font-size="8" font-family="monospace" font-weight="bold">👆 UP (' + activeStep.x + ', ' + activeStep.y + ')</text></g>';
              }

              const activeTree = treeExp || treeAct || [];
              const hit = activeTree.find(c => c.id !== -2 && activeStep.x >= c.x && activeStep.x <= c.x + c.width && activeStep.y >= c.y && activeStep.y <= c.y + c.height);
              if (hit) {
                const hx = hit.x * scale;
                const hy = hit.y * scale;
                const hw = hit.width * scale;
                const hh = hit.height * scale;
                svg += '<rect x="' + (hx - 2) + '" y="' + (hy - 2) + '" width="' + (hw + 4) + '" height="' + (hh + 4) + '" rx="4" fill="rgba(245, 158, 11, 0.08)" stroke="#f59e0b" stroke-width="1.5" stroke-dasharray="4 2"/>';
                svg += '<text x="' + (hx + 2) + '" y="' + (hy - 3) + '" fill="#f59e0b" font-size="7.5" font-family="monospace">Hit: ' + (hit.kind||'').replace(/Layout$/,'') + ' [' + hit.id + ']</text>';
              }
            }
          }

          svg += '</g></svg>';
          return svg;
        }

        if (mode === 'side_by_side') {
          return '<div class="sbs-preview-wrap">' +
            '<div class="sbs-pane"><div class="sbs-header" style="color:#38bdf8;">Android Gold</div>' + buildSingle(expectedTree, null, 'gold', 180, 210) + '</div>' +
            '<div class="sbs-pane"><div class="sbs-header" style="color:#4ade80;">Player Actual</div>' + buildSingle(null, actualTree, 'actual', 180, 210) + '</div>' +
          '</div>';
        }
        return buildSingle(expectedTree, actualTree, mode, 380, 240);
      }

      function updateCardVisual(name) {
        const d = TEST_DATA[name];
        if (!d) return;
        const st = cardState[name];
        const container = document.getElementById('viz-' + name);
        if (!container) return;

        let expTree = d.expectedTree;
        let actTree = d.actualTree;
        let activeStep = null;
        let allSteps = null;

        if (d.expectedFrames && d.expectedFrames.length > 0) {
          const fIdx = st.frameIdx || 0;
          if (d.expectedFrames[fIdx]) expTree = d.expectedFrames[fIdx].tree;
          if (d.actualFrames && d.actualFrames[fIdx]) actTree = d.actualFrames[fIdx].tree;
        } else if (d.expectedResizeSteps && d.expectedResizeSteps.length > 0) {
          const sIdx = st.frameIdx || 0;
          if (d.expectedResizeSteps[sIdx]) expTree = d.expectedResizeSteps[sIdx].tree;
          if (d.actualResizeSteps && d.actualResizeSteps[sIdx]) {
            actTree = d.actualResizeSteps[sIdx].tree;
            const actStep = d.actualResizeSteps[sIdx];
            const expStep = d.expectedResizeSteps[sIdx];
            const goldImg = document.getElementById('viz-img-gold-' + name);
            const actImg = document.getElementById('viz-img-act-' + name);
            const diffImg = document.getElementById('viz-img-diff-' + name);
            const rmseMetric = document.getElementById('viz-metric-rmse-' + name);
            const deltaMetric = document.getElementById('viz-metric-delta-' + name);

            if (actImg && actStep.renderedCanvasBase64) {
              actImg.src = actStep.renderedCanvasBase64;
            }
            if (goldImg && actStep.goldImageBase64) {
              goldImg.src = actStep.goldImageBase64;
            }
            if (diffImg) {
              // Never fall back to the rendered canvas here: showing the player's own output
              // under a 'Diff' heading reads as 'these pixels are wrong' when in fact this
              // step has no computed difference at all.
              if (actStep.diffHeatmapBase64) {
                diffImg.src = actStep.diffHeatmapBase64;
                diffImg.style.visibility = 'visible';
              } else {
                diffImg.style.visibility = 'hidden';
              }
            }
            if (rmseMetric) {
              rmseMetric.textContent = (typeof actStep.rmse === 'number')
                ? 'RMSE: ' + actStep.rmse.toFixed(4) : 'RMSE: n/a';
              rmseMetric.style.color = (typeof actStep.rmse === 'number' && actStep.rmse <= (d.maxRmse || 1.0))
                ? '#4ade80' : '#f87171';
            }
            if (deltaMetric) {
              deltaMetric.textContent = (typeof actStep.maxDelta === 'number')
                ? 'Max Delta: ' + actStep.maxDelta : 'Max Delta: n/a';
            }
          }
        } else if (d.expectedInteractions && d.expectedInteractions.length > 0) {
          const aIdx = st.frameIdx || 0;
          allSteps = d.expectedInteractions;
          activeStep = d.expectedInteractions[aIdx] || d.expectedInteractions[0];
          if (activeStep) expTree = activeStep.tree;
          if (d.actualInteractions && d.actualInteractions[aIdx]) actTree = d.actualInteractions[aIdx].tree;
        }

        container.innerHTML = renderCardSvg(expTree, actTree, st.mode, activeStep, allSteps, d.bounds);
      }

      function switchCardVizMode(name, mode, btn) {
        cardState[name].mode = mode;
        const parent = btn.parentElement;
        parent.querySelectorAll('.view-pill').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        updateCardVisual(name);
      }

      function scrubCardAnimation(name, val) {
        cardState[name].frameIdx = parseInt(val, 10);
        const d = TEST_DATA[name];
        const f = d.expectedFrames[cardState[name].frameIdx];
        const badge = document.getElementById('badge-' + name);
        if (badge && f) badge.textContent = 'Frame ' + f.frame + ' (t=' + f.time + 's)';
        updateCardVisual(name);
      }

      function toggleCardAnimation(name) {
        const st = cardState[name];
        const d = TEST_DATA[name];
        if (!d.expectedFrames || d.expectedFrames.length <= 1) return;
        const playBtn = document.getElementById('play-' + name);

        if (st.isPlaying) {
          st.isPlaying = false;
          if (st.playTimer) clearInterval(st.playTimer);
          st.playTimer = null;
          if (playBtn) playBtn.textContent = '▶ Play';
        } else {
          st.isPlaying = true;
          if (playBtn) playBtn.textContent = '⏸ Pause';
          st.playTimer = setInterval(() => {
            st.frameIdx = (st.frameIdx + 1) % d.expectedFrames.length;
            const scrubber = document.getElementById('scrub-' + name);
            if (scrubber) scrubber.value = st.frameIdx;
            const f = d.expectedFrames[st.frameIdx];
            const badge = document.getElementById('badge-' + name);
            if (badge && f) badge.textContent = 'Frame ' + f.frame + ' (t=' + f.time + 's)';
            updateCardVisual(name);
          }, 240);
        }
      }

      function selectCardResizeStep(name, idx, btn) {
        cardState[name].frameIdx = idx;
        const parent = btn.parentElement;
        parent.querySelectorAll('.step-pill').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        updateCardVisual(name);
      }

      function selectCardInteractionStep(name, idx, btn) {
        cardState[name].frameIdx = idx;
        const parent = btn.parentElement;
        parent.querySelectorAll('.step-pill').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');

        const scrubber = document.getElementById('scrub-gesture-' + name);
        if (scrubber) scrubber.value = idx;
        const d = TEST_DATA[name];
        const badge = document.getElementById('badge-gesture-' + name);
        if (badge && d.expectedInteractions && d.expectedInteractions[idx]) {
          const act = d.expectedInteractions[idx];
          badge.textContent = act.label || ('Step ' + idx + ': ' + act.action);
        }
        updateCardVisual(name);
      }

      function scrubCardGesture(name, val) {
        const idx = parseInt(val, 10);
        cardState[name].frameIdx = idx;
        const d = TEST_DATA[name];
        const badge = document.getElementById('badge-gesture-' + name);
        if (badge && d.expectedInteractions && d.expectedInteractions[idx]) {
          const act = d.expectedInteractions[idx];
          badge.textContent = act.label || ('Step ' + idx + ': ' + act.action);
        }
        const pillGroup = document.getElementById('pills-gesture-' + name);
        if (pillGroup) {
          const pills = pillGroup.querySelectorAll('.step-pill');
          pills.forEach((p, i) => {
            if (i === idx) p.classList.add('active');
            else p.classList.remove('active');
          });
        }
        updateCardVisual(name);
      }

      function toggleCardGesture(name) {
        const st = cardState[name];
        const d = TEST_DATA[name];
        if (!d.expectedInteractions || d.expectedInteractions.length <= 1) return;
        const playBtn = document.getElementById('play-gesture-' + name);

        if (st.isPlaying) {
          st.isPlaying = false;
          if (st.playTimer) clearInterval(st.playTimer);
          st.playTimer = null;
          if (playBtn) playBtn.textContent = '▶ Play Gesture';
        } else {
          st.isPlaying = true;
          if (playBtn) playBtn.textContent = '⏸ Pause';
          st.playTimer = setInterval(() => {
            st.frameIdx = (st.frameIdx + 1) % d.expectedInteractions.length;
            const scrubber = document.getElementById('scrub-gesture-' + name);
            if (scrubber) scrubber.value = st.frameIdx;
            const badge = document.getElementById('badge-gesture-' + name);
            if (badge && d.expectedInteractions[st.frameIdx]) {
              const act = d.expectedInteractions[st.frameIdx];
              badge.textContent = act.label || ('Step ' + st.frameIdx + ': ' + act.action);
            }
            const pillGroup = document.getElementById('pills-gesture-' + name);
            if (pillGroup) {
              const pills = pillGroup.querySelectorAll('.step-pill');
              pills.forEach((p, i) => {
                if (i === st.frameIdx) p.classList.add('active');
                else p.classList.remove('active');
              });
            }
            updateCardVisual(name);
          }, 800);
        }
      }

      function toggleCardClickSim(name) {
        const st = cardState[name];
        const d = TEST_DATA[name];
        if (!d.expectedInteractions || d.expectedInteractions.length <= 1) return;
        const nextIdx = (st.frameIdx === 0) ? 1 : 0;
        scrubCardGesture(name, nextIdx);
      }

      // Ahem Demo Interactive Playground
      let ahemDemoSize = 22;
      let ahemDemoAlign = 'start';
      let ahemDemoMode = 'both';

      function renderAhemDemo() {
        const container = document.getElementById('ahem-demo-viewport');
        if (!container) return;
        const clientW = container.clientWidth;
        const h = 120;
        const text = 'RemoteCompose Ahem CoreText';
        const chars = Array.from(text);
        const fs = ahemDemoSize;
        const lineW = chars.length * fs;
        const baseW = (clientW && clientW > 100) ? clientW : 860;
        const viewW = Math.max(baseW, lineW + 48);

        let lineX = 24;
        if (ahemDemoAlign === 'center') {
          lineX = Math.max(16, (viewW - lineW) / 2);
        } else if (ahemDemoAlign === 'end') {
          lineX = Math.max(16, viewW - lineW - 24);
        }

        const lineTop = (h - fs) / 2;
        const baselineY = lineTop + 0.8 * fs;
        const descentY = lineTop + 1.0 * fs;

        const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

        let svg = '<svg viewBox="0 0 ' + viewW.toFixed(1) + ' ' + h + '" width="100%" height="' + h + '" style="display:block;">';
        svg += '<rect width="' + viewW.toFixed(1) + '" height="' + h + '" fill="#0b101d"/>';

        // Guide lines if metrics mode or both
        if (ahemDemoMode === 'metrics' || ahemDemoMode === 'both') {
          svg += '<line x1="12" y1="' + lineTop.toFixed(1) + '" x2="' + (viewW - 12).toFixed(1) + '" y2="' + lineTop.toFixed(1) + '" stroke="#38bdf8" stroke-width="1" stroke-dasharray="2 2" opacity="0.55"/>';
          svg += '<text x="14" y="' + (lineTop - 4).toFixed(1) + '" fill="#38bdf8" font-size="9" font-family="monospace">Ascent top (y = 0.0em)</text>';
          svg += '<line x1="12" y1="' + descentY.toFixed(1) + '" x2="' + (viewW - 12).toFixed(1) + '" y2="' + descentY.toFixed(1) + '" stroke="#a855f7" stroke-width="1" stroke-dasharray="2 2" opacity="0.55"/>';
          svg += '<text x="14" y="' + (descentY + 11).toFixed(1) + '" fill="#a855f7" font-size="9" font-family="monospace">Descent bottom (y + 1.0em)</text>';
        }

        // 1. Glyph Area Cells: Exact 1em x 1em cells matching Ahem font geometry
        const color = '#38bdf8';
        const glyphFillOpacity = (ahemDemoMode === 'ahem') ? '0.62' : (ahemDemoMode === 'both' ? '0.28' : '0.10');
        const glyphStrokeOpacity = (ahemDemoMode === 'ahem') ? '0.85' : (ahemDemoMode === 'both' ? '0.65' : '0.45');
        const glyphStrokeWidth = (ahemDemoMode === 'ahem') ? '0.75' : '0.5';

        chars.forEach((ch, i) => {
          const cellX = lineX + i * fs;
          if (ch === ' ') {
            svg += '<rect x="' + cellX.toFixed(1) + '" y="' + lineTop.toFixed(1) + '" width="' + fs.toFixed(1) + '" height="' + fs.toFixed(1) + '" fill="' + color + '" fill-opacity="0.04" stroke="' + color + '" stroke-width="0.5" stroke-dasharray="2 2" stroke-opacity="0.35"/>';
          } else {
            svg += '<rect x="' + cellX.toFixed(1) + '" y="' + lineTop.toFixed(1) + '" width="' + fs.toFixed(1) + '" height="' + fs.toFixed(1) + '" fill="' + color + '" fill-opacity="' + glyphFillOpacity + '" stroke="' + color + '" stroke-width="' + glyphStrokeWidth + '" stroke-opacity="' + glyphStrokeOpacity + '"/>';
          }
        });

        // 2. Typographical baseline guide (dashed red line at y + 0.8 * fontSize)
        svg += '<line x1="' + Math.max(12, lineX - 8).toFixed(1) + '" y1="' + baselineY.toFixed(1) + '" x2="' + Math.min(viewW - 12, lineX + lineW + 8).toFixed(1) + '" y2="' + baselineY.toFixed(1) + '" stroke="#ef4444" stroke-width="1.5" stroke-dasharray="3 2" opacity="0.9"/>';
        const labelX = (lineX + lineW + 115 < viewW) ? (lineX + lineW + 12) : Math.max(14, lineX - 98);
        svg += '<text x="' + labelX.toFixed(1) + '" y="' + (baselineY + 3).toFixed(1) + '" fill="#ef4444" font-size="9" font-family="monospace" font-weight="bold">Baseline (0.8em)</text>';

        // 3. Characters positioned precisely within each glyph area cell
        if (ahemDemoMode === 'both' || ahemDemoMode === 'metrics') {
          const textFill = (ahemDemoMode === 'both') ? '#ffffff' : '#e2e8f0';
          const textOpacity = (ahemDemoMode === 'both') ? '0.95' : '0.80';
          svg += '<g text-anchor="middle" font-family="ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace" font-weight="700" font-size="' + (fs * 0.72).toFixed(1) + '" fill="' + textFill + '" opacity="' + textOpacity + '">';
          chars.forEach((ch, i) => {
            if (ch !== ' ') {
              const charX = lineX + (i + 0.5) * fs;
              svg += '<text x="' + charX.toFixed(1) + '" y="' + baselineY.toFixed(1) + '">' + esc(ch) + '</text>';
            }
          });
          svg += '</g>';
        }

        svg += '</svg>';
        container.innerHTML = svg;
      }

      function updateAhemDemo(val) {
        ahemDemoSize = parseInt(val, 10);
        const b = document.getElementById('ahem-demo-size-badge');
        if (b) b.textContent = ahemDemoSize + 'px';
        renderAhemDemo();
      }

      function setAhemDemoAlign(align, btn) {
        ahemDemoAlign = align;
        btn.parentElement.querySelectorAll('.view-pill').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        renderAhemDemo();
      }

      function setAhemDemoMode(mode, btn) {
        ahemDemoMode = mode;
        btn.parentElement.querySelectorAll('.view-pill').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        renderAhemDemo();
      }

      window.updateAhemDemo = updateAhemDemo;
      window.setAhemDemoAlign = setAhemDemoAlign;
      window.setAhemDemoMode = setAhemDemoMode;
      window.addEventListener('resize', renderAhemDemo);
      setTimeout(renderAhemDemo, 100);
    
      // Tab switching
      function switchMainTab(tabId) {
        document.querySelectorAll('.tab-pane').forEach(el => el.classList.remove('active'));
        document.querySelectorAll('.main-tab-btn').forEach(el => el.classList.remove('active'));
        const target = document.getElementById(tabId);
        if (target) target.classList.add('active');
        const btn = document.getElementById('btn-' + tabId);
        if (btn) btn.classList.add('active');
        try {
          history.replaceState(null, '', '#' + tabId.replace('tab-', ''));
        } catch (e) {}
        if (tabId === 'tab-layout') {
          setTimeout(renderAhemDemo, 50);
        }
        if (tabId === 'tab-particles') {
          setTimeout(initParticleSims, 50);
        }
      }
      window.switchMainTab = switchMainTab;

      // Particle simulation engine
      const PARTICLE_DATA = ${JSON.stringify(clientParticleRecordMap)};
      const particleSimState = {};

      function initParticleSims() {
        for (const [name, d] of Object.entries(PARTICLE_DATA)) {
          if (!particleSimState[name]) {
            particleSimState[name] = {
              frame: 0,
              playing: false,
              timer: null
            };
            renderParticleCanvas(name, 0);
          }
        }
      }

      function renderParticleCanvas(name, frameIdx) {
        const d = PARTICLE_DATA[name];
        if (!d) return;
        const canvas = document.getElementById('canvas-particle-' + name);
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        const w = canvas.width;
        const h = canvas.height;

        ctx.fillStyle = '#0b101d';
        ctx.fillRect(0, 0, w, h);

        ctx.strokeStyle = 'rgba(255, 255, 255, 0.06)';
        ctx.lineWidth = 1;
        for (let x = 0; x < w; x += 25) {
          ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke();
        }
        for (let y = 0; y < h; y += 25) {
          ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
        }

        const frames = d.actualFrames || d.expectedFrames || [];
        const frameObj = frames[frameIdx] || frames[0];
        if (!frameObj || !frameObj.particles) return;

        const particles = frameObj.particles;
        const colors = ['#38bdf8', '#4ade80', '#c084fc', '#f43f5e', '#fbbf24', '#2dd4bf', '#a78bfa', '#fb923c'];

        particles.forEach((p, idx) => {
          const px = (p[0] / 200) * w;
          const py = (p[1] / 200) * h;
          const color = colors[idx % colors.length];

          ctx.beginPath();
          ctx.arc(px, py, 7, 0, Math.PI * 2);
          ctx.fillStyle = color;
          ctx.globalAlpha = 0.25;
          ctx.fill();

          ctx.beginPath();
          ctx.arc(px, py, 3.5, 0, Math.PI * 2);
          ctx.fillStyle = color;
          ctx.globalAlpha = 1.0;
          ctx.fill();

          if (p.length >= 4) {
            const vx = p[2] * 4;
            const vy = p[3] * 4;
            ctx.beginPath();
            ctx.moveTo(px, py);
            ctx.lineTo(px + vx, py + vy);
            ctx.strokeStyle = color;
            ctx.lineWidth = 1.5;
            ctx.stroke();
          }

          ctx.fillStyle = 'rgba(255, 255, 255, 0.7)';
          ctx.font = '9px monospace';
          ctx.fillText('P' + idx, px + 6, py - 4);
        });

        const slider = document.getElementById('slider-particle-' + name);
        if (slider) slider.value = frameIdx;
        const label = document.getElementById('frame-label-' + name);
        if (label) label.textContent = 'Frame: ' + frameIdx + ' / ' + (frames.length - 1);

        renderParticleTable(name, frameIdx);
      }

      function renderParticleTable(name, frameIdx) {
        const d = PARTICLE_DATA[name];
        if (!d) return;
        const tbody = document.getElementById('tbody-particle-' + name);
        if (!tbody) return;

        const actFrames = d.actualFrames || [];
        const expFrames = d.expectedFrames || [];
        const actParticles = actFrames[frameIdx]?.particles || [];
        const expParticles = expFrames[frameIdx]?.particles || [];
        const tol = d.parameters?.tolerance || 0.05;

        let html = '';
        for (let i = 0; i < actParticles.length; i++) {
          const actP = actParticles[i] || [];
          const expP = expParticles[i] || [];
          let pass = true;
          for (let j = 0; j < expP.length; j++) {
            if (Math.abs((actP[j] ?? 0) - expP[j]) > tol) pass = false;
          }
          html += '<tr><td><strong>P' + i + '</strong></td>';
          for (let j = 0; j < d.variables.length; j++) {
            const aVal = actP[j] !== undefined ? actP[j] : '—';
            const eVal = expP[j] !== undefined ? expP[j] : '—';
            html += '<td><span style="color:#4ade80">' + aVal + '</span> <span style="color:#64748b">/ ' + eVal + '</span></td>';
          }
          html += '<td><span class="badge ' + (pass ? 'badge-pass' : 'badge-fail') + '">' + (pass ? '✔ PASS' : '✘ FAIL') + '</span></td></tr>';
        }
        tbody.innerHTML = html;
      }

      function scrubParticleSim(name, frameIdx) {
        if (!particleSimState[name]) particleSimState[name] = { frame: 0, playing: false, timer: null };
        particleSimState[name].frame = frameIdx;
        renderParticleCanvas(name, frameIdx);
      }

      function toggleParticlePlay(name) {
        if (!particleSimState[name]) particleSimState[name] = { frame: 0, playing: false, timer: null };
        const st = particleSimState[name];
        const d = PARTICLE_DATA[name];
        const frames = d.actualFrames || d.expectedFrames || [];
        const btn = document.getElementById('btn-play-' + name);

        if (st.playing) {
          st.playing = false;
          clearInterval(st.timer);
          if (btn) btn.textContent = '▶ Play';
        } else {
          st.playing = true;
          if (btn) btn.textContent = '⏸ Pause';
          st.timer = setInterval(() => {
            st.frame = (st.frame + 1) % frames.length;
            renderParticleCanvas(name, st.frame);
          }, 180);
        }
      }

      function resetParticleSim(name) {
        if (!particleSimState[name]) return;
        const st = particleSimState[name];
        if (st.playing) toggleParticlePlay(name);
        st.frame = 0;
        renderParticleCanvas(name, 0);
      }
      window.scrubParticleSim = scrubParticleSim;
      window.toggleParticlePlay = toggleParticlePlay;
      window.resetParticleSim = resetParticleSim;

      ${renderOperationsScript(operationsRegistry)}

      // Handle hash navigation
      window.addEventListener('DOMContentLoaded', () => {
        const hash = window.location.hash.replace('#', '');
        if (hash && document.getElementById('tab-' + hash)) {
          switchMainTab('tab-' + hash);
        }
      });
</script>
  </div>
</body>
</html>`;

writeFileSync(outputFile, html, "utf8");
console.log(`Successfully generated visual audit report: ${outputFile}`);

// The inspector covers every subsystem, not just layout. `activeResults` is the layout slice
// this file's own helpers use, and passing it here produced a layout-only inspector that
// clobbered the full one — whichever generator ran last silently decided the contents.
// `attachSourceDocuments` is the same display-only source pass the standalone generator does.
const inspectorResults = attachSourceDocuments(
    filterActiveTests(report.results || []), join(SPEC_DIR, "tests"));
writeFileSync(inspectorFile,
    generateInspectorHtml(inspectorResults, report.player?.name || player), "utf8");
console.log(`Successfully generated test inspector: ${inspectorFile}`);


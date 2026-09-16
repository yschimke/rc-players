#!/usr/bin/env node

/**
 * generate-gold-overview.mjs
 *
 * Generates gold-overview.json and gold-overview.html covering all 14 RemoteCompose subsystems:
 *   1. Layout Conformance (173 tests)
 *   2. Expression Engine (14 tests)
 *   3. Particle System (8 tests)
 *   4. Wire Protocol & Loom (13 tests)
 *   5. 2D Canvas (7 tests)
 *   6. Clock & Time Simulation (3 tests)
 *   7. Interactivity & Gestures (7 tests)
 *   8. Text Operations (2 tests)
 *   9. Color Theme & Palettes (2 tests)
 *  10. Data Operations (2 tests)
 *  11. Path Operations & Morphing (2 tests)
 *  12. Shader Data & AGSL (2 tests)
 *  13. Semantics & Accessibility (2 tests)
 *  14. Autonomous Scheduling (2 tests)
 *
 * Preserves Android reference JaCoCo instruction, line, and branch coverage metrics.
 */

import { mkdirSync, readFileSync, writeFileSync, readdirSync, existsSync, statSync } from 'fs';
import { join, basename, dirname } from 'path';
import { fileURLToPath } from 'url';
import { argValue } from './report-paths.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const GESTURE_KINDS = new Set([
  'click', 'longPress', 'doubleClick', 'touch_down', 'touch_drag', 'touch_up',
]);

/**
 * Summarises a gold's v2 `checks` / `timeline` into the handful of questions the overview
 * actually asks. Every subsystem gets its numbers from here, so a new probe or a new
 * subsystem shows up in the report without a code change — which is precisely what the
 * thirty-odd `expected_*` lookups this replaces could not do.
 *
 * Channel keys are `probe` or `probe:channel`, matching the conformance format.
 */
function checkStats(raw) {
  const checks = Array.isArray(raw.checks) ? raw.checks : [];
  const timeline = Array.isArray(raw.timeline) ? raw.timeline : [];
  const key = (c) => c.probe + (c.channel ? `:${c.channel}` : '');
  // How many things a single `expect` pins: array length, map size, or one for a scalar.
  const size = (e) => Array.isArray(e) ? e.length
    : (e && typeof e === 'object') ? Object.keys(e).length : 1;
  const pick = (keys) => keys.length ? checks.filter((c) => keys.includes(key(c))) : checks;

  return {
    /** Number of checks on the given channels. */
    count: (...keys) => pick(keys).length,
    /** Total number of asserted items across the given channels. */
    items: (...keys) => pick(keys).reduce((n, c) => n + size(c.expect), 0),
    /** Largest single assertion on a channel — the right measure when each check restates
     *  the whole thing (a layout tree per resize, a particle population per frame). */
    widest: (...keys) => pick(keys).reduce((n, c) => Math.max(n, size(c.expect)), 0),
    /** A scalar `expect` value, for channels like ops:count that assert a number outright. */
    scalar: (...keys) => {
      const c = pick(keys).find((x) => typeof x.expect === 'number');
      return c ? c.expect : 0;
    },
    has: (...keys) => pick(keys).length > 0,
    steps: (...kinds) => timeline.filter((s) => kinds.includes(s.kind)).length,
    gestureSteps: () => timeline.filter((s) => GESTURE_KINDS.has(s.kind)).length,
  };
}

const SUBSYSTEM_CONFIG = {
  layout: {
    id: 'layout',
    title: 'Layout Conformance',
    shortName: 'Layout',
    icon: '📐',
    color: '#38bdf8',
    bgColor: 'rgba(56, 189, 248, 0.15)',
    borderColor: 'rgba(56, 189, 248, 0.3)',
    description: 'Declarative component tree measurement, positioning, constraints, modifiers, scrolling, and multi-pass layouts',
    verification: 'Layout Tree & Bounding Boxes'
  },
  expressions: {
    id: 'expressions',
    title: 'Expression Engine',
    shortName: 'Expressions',
    icon: '⚡',
    color: '#a855f7',
    bgColor: 'rgba(168, 85, 247, 0.15)',
    borderColor: 'rgba(168, 85, 247, 0.3)',
    description: 'Dynamic mathematical expression graphs, float variables, trig/vector functions, and animated waveforms',
    verification: 'Dynamic Float & Variable Evaluation'
  },
  particles: {
    id: 'particles',
    title: 'Particle System',
    shortName: 'Particles',
    icon: '✨',
    color: '#ec4899',
    bgColor: 'rgba(236, 72, 153, 0.15)',
    borderColor: 'rgba(236, 72, 153, 0.3)',
    description: 'Multi-frame deterministic particle physics simulation, boundary bounce, drag damping, and radial bursts',
    verification: 'Physics States & Multi-frame Coordinates'
  },
  wire: {
    id: 'wire',
    title: 'Wire Protocol',
    shortName: 'Wire Protocol',
    icon: '📡',
    color: '#14b8a6',
    bgColor: 'rgba(20, 184, 166, 0.15)',
    borderColor: 'rgba(20, 184, 166, 0.3)',
    description: 'Binary wire buffer primitives, header & version handshakes, ReferencedOperations inlining, and NaN-boxed float references',
    verification: 'Binary Serialization & Operation Streams'
  },
  loom: {
    id: 'loom',
    title: 'Loom Macro Engine',
    shortName: 'Loom',
    icon: '🧵',
    color: '#10b981',
    bgColor: 'rgba(16, 185, 129, 0.15)',
    borderColor: 'rgba(16, 185, 129, 0.3)',
    description: 'Macro definition (PatternDefine), pattern inflation, slot blocks (PatternBlock), loop expansion (PatternForEach), and tiered ID remapping',
    verification: 'Macro Inflation & Tiered ID Remapping'
  },
  canvas: {
    id: 'canvas',
    title: '2D Canvas',
    shortName: '2D Canvas',
    icon: '🎨',
    color: '#f59e0b',
    bgColor: 'rgba(245, 158, 11, 0.15)',
    borderColor: 'rgba(245, 158, 11, 0.3)',
    description: 'Vector graphics drawing primitives, bezier paths, clipRect operations, DrawTextOnPath/DrawTextAnchored/DrawTextOnCircle placement, and 2D matrix stacks',
    verification: 'Vector Commands, Glyph Placement & Raster Snapshots'
  },
  shaders: {
    id: 'shaders',
    title: 'Shaders & AGSL',
    shortName: 'Shaders',
    icon: '🔮',
    color: '#c084fc',
    bgColor: 'rgba(192, 132, 252, 0.15)',
    borderColor: 'rgba(192, 132, 252, 0.3)',
    description: 'Linear and sweep gradient shaders, ShaderData uniform bindings, float/int vector pipelines, and AGSL procedural shaders',
    verification: 'Shader Uniform Bindings, Gradient Shaders & AGSL State'
  },
  clock: {
    id: 'clock',
    title: 'Clock & Time Simulation',
    shortName: 'Clock',
    icon: '⏱️',
    color: '#06b6d4',
    bgColor: 'rgba(6, 182, 212, 0.15)',
    borderColor: 'rgba(6, 182, 212, 0.3)',
    description: 'Continuous time provider simulation, analog sweeping second hands, and digital calendar rendering',
    verification: 'Time Provider Dynamics + Raster Visuals'
  },
  interactivity: {
    id: 'interactivity',
    title: 'Interactivity & Gestures',
    shortName: 'Interactivity',
    icon: '👆',
    color: '#6366f1',
    bgColor: 'rgba(99, 102, 241, 0.15)',
    borderColor: 'rgba(99, 102, 241, 0.3)',
    description: 'Hit-testing bounding boxes, click state mutations, host actions, and slider drag tracking',
    verification: 'Touch Event Dispatch & State Mutations'
  },
  textoperations: {
    id: 'textoperations',
    title: 'Text Operations',
    shortName: 'Text Ops',
    icon: '🔤',
    color: '#34d399',
    bgColor: 'rgba(52, 211, 153, 0.15)',
    borderColor: 'rgba(52, 211, 153, 0.3)',
    description: 'Text formatting from float numbers, dynamic string concatenation (TextMerge), and casing transformations',
    verification: 'Dynamic Text Formatting & String Buffers'
  },
  colortheme: {
    id: 'colortheme',
    title: 'Color Theme & Palette',
    shortName: 'Color Theme',
    icon: '🎭',
    color: '#fb7185',
    bgColor: 'rgba(251, 113, 133, 0.15)',
    borderColor: 'rgba(251, 113, 133, 0.3)',
    description: 'Theme mode switching (light vs dark mode), ColorConstant RGBA channels, and dynamic theme palette slots',
    verification: 'Theme Palette Switching & RGBA Values'
  },
  dataoperations: {
    id: 'dataoperations',
    title: 'Data Operations',
    shortName: 'Data Ops',
    icon: '💾',
    color: '#fbbf24',
    bgColor: 'rgba(251, 191, 36, 0.15)',
    borderColor: 'rgba(251, 191, 36, 0.3)',
    description: 'DataDynamicListFloat array buffers, runtime element mutation via UpdateDynamicFloatList, and DataMap lookups',
    verification: 'Dynamic Float Buffers & Key-Value Lookups'
  },
  pathoperations: {
    id: 'pathoperations',
    title: 'Path Operations',
    shortName: 'Path Ops',
    icon: '〰️',
    color: '#818cf8',
    bgColor: 'rgba(129, 140, 248, 0.15)',
    borderColor: 'rgba(129, 140, 248, 0.3)',
    description: 'Path construction, vector command appending, and PathTween topological shape morphing',
    verification: 'Path Geometry & Interpolated Tweens'
  },
  semantics: {
    id: 'semantics',
    title: 'Semantics & Accessibility',
    shortName: 'Semantics',
    icon: '♿',
    color: '#2dd4bf',
    bgColor: 'rgba(45, 212, 191, 0.15)',
    borderColor: 'rgba(45, 212, 191, 0.3)',
    description: 'Accessibility semantics node tree, button/heading roles, clickable flags, and content descriptions',
    verification: 'Accessibility Tree & Semantic Roles'
  },
  scheduling: {
    id: 'scheduling',
    title: 'Autonomous Scheduling',
    shortName: 'Scheduling',
    icon: '⏰',
    color: '#f43f5e',
    bgColor: 'rgba(244, 63, 94, 0.15)',
    borderColor: 'rgba(244, 63, 94, 0.3)',
    description: 'Autonomous wake-in timer intervals, discrete ImpulseOperation event windows, and duration timing',
    verification: 'Wake Timers & Discrete Impulse Windows'
  },
  conditionals: {
    id: 'conditionals',
    title: 'Conditional Branching',
    shortName: 'Conditionals',
    icon: '🔀',
    color: '#f59e0b',
    bgColor: 'rgba(245, 158, 11, 0.15)',
    borderColor: 'rgba(245, 158, 11, 0.3)',
    description: 'ConditionalOperations comparison operators, nested branch gating, and parse-time SKIP API guards',
    verification: 'Branch Execution & Skip Gating'
  },
  matrixmath: {
    id: 'matrixmath',
    title: 'Matrix Expressions',
    shortName: 'Matrix Math',
    icon: '🧮',
    color: '#38bdf8',
    bgColor: 'rgba(56, 189, 248, 0.15)',
    borderColor: 'rgba(56, 189, 248, 0.3)',
    description: 'MatrixConstant storage, RPN MatrixExpression evaluation, and MatrixVectorMath affine/perspective transforms',
    verification: 'Numeric Matrix & Vector Results'
  },
  animationspec: {
    id: 'animationspec',
    title: 'Animation Specifications',
    shortName: 'AnimationSpec',
    icon: '🎬',
    color: '#fb7185',
    bgColor: 'rgba(251, 113, 133, 0.15)',
    borderColor: 'rgba(251, 113, 133, 0.3)',
    description: 'AnimationSpec field round trip, per-component spec adoption, defaults, and the animationId 0 disable signal',
    verification: 'Spec Adoption & Easing Selection'
  }
};

// The suspicious tag and its reason are corpus data, read from each gold file below. A
// hardcoded map used to live here and took precedence over the gold, so correcting a tag in
// the corpus had no effect on this page.

/**
 * Loads the reference-engine coverage figures.
 *
 * These come from JaCoCo, which only the Java gold generator can reach, so it publishes them
 * to `coverage.json` and this script renders them. Previously they were scavenged out of this
 * script's own previous `gold-overview.json`, which made a generated file load-bearing as an
 * input — deleting it silently swapped in the stale hardcoded defaults below.
 *
 * The old location is still honoured so nothing breaks before the next Java run.
 */
function loadCoverage(baseDir, previousData) {
  const coveragePath = join(baseDir, 'coverage.json');
  if (existsSync(coveragePath)) {
    try {
      const parsed = JSON.parse(readFileSync(coveragePath, 'utf8'));
      // Accept either the bare coverage object or one wrapped in a `coverage` key.
      return parsed.coverage ?? parsed;
    } catch (e) {
      console.warn(`Could not parse ${coveragePath}: ${e.message}`);
    }
  }
  if (previousData && previousData.coverage) {
    return previousData.coverage;
  }
  console.warn(
    'No coverage.json and no previous gold-overview.json — falling back to stale built-in\n'
    + '  numbers. Regenerate with the Java gold generator to refresh them.');
  return {
    subsystem: 'androidx.compose.remote.core.operations.layout',
    total_instructions: 36848,
    covered_instructions: 33705,
    instruction_coverage_pct: 91.47,
    total_lines: 8890,
    covered_lines: 8153,
    line_coverage_pct: 91.71,
    total_branches: 3928,
    covered_branches: 3157,
    branch_coverage_pct: 80.37,
    classes: []
  };
}

/**
 * @param baseDir  where the `gold/` corpus and `coverage.json` live.
 * @param outDir   where gold-overview.json / .html are written. Defaults to `baseDir`; split
 *                 out so the report can be produced without writing into the spec tree.
 */
export function runGenerator(baseDir, outDir = baseDir) {
  const goldBaseDir = join(baseDir, 'gold');
  const overviewJsonPath = join(outDir, 'gold-overview.json');

  // The previous output is still read, but only for the per-gold fields it may carry —
  // coverage now has its own file.
  const previousJsonPath = existsSync(overviewJsonPath)
    ? overviewJsonPath
    : join(baseDir, 'gold-overview.json');

  let existingData = null;
  if (existsSync(previousJsonPath)) {
    try {
      existingData = JSON.parse(readFileSync(previousJsonPath, 'utf8'));
    } catch (e) {
      console.warn('Could not parse existing gold-overview.json, starting fresh');
    }
  }

  const coverage = loadCoverage(baseDir, existingData);

  const existingGoldMap = new Map();
  if (existingData && Array.isArray(existingData.gold_files)) {
    for (const gf of existingData.gold_files) {
      existingGoldMap.set(gf.name, gf);
    }
  }

  const allGoldFiles = [];
  const subsystemStats = {};

  for (const catId of Object.keys(SUBSYSTEM_CONFIG)) {
    const catConf = SUBSYSTEM_CONFIG[catId];
    const catDir = join(goldBaseDir, catId);
    subsystemStats[catId] = {
      id: catId,
      title: catConf.title,
      shortName: catConf.shortName,
      icon: catConf.icon,
      color: catConf.color,
      bgColor: catConf.bgColor,
      borderColor: catConf.borderColor,
      total: 0,
      active: 0,
      suspicious: 0,
      visuals: 0,
      verification: catConf.verification,
      description: catConf.description
    };

    if (!existsSync(catDir)) continue;

    const entries = readdirSync(catDir).filter(e => e.endsWith('.gold.json')).sort();
    for (const entry of entries) {
      const name = entry.replace('.gold.json', '');
      const filePath = join(catDir, entry);
      let raw = null;
      try {
        raw = JSON.parse(readFileSync(filePath, 'utf8'));
      } catch (err) {
        console.error(`Error reading ${filePath}:`, err);
        continue;
      }

      const existingRecord = existingGoldMap.get(name) || {};
      const isSuspicious = Boolean(raw.tags && raw.tags.includes('suspicious'));
      const defectReason = isSuspicious
        ? (raw.suspicious_reason || 'Marked suspicious in gold file, no reason recorded')
        : null;

      // The reference image is the raster check's `expect` — the image belongs to the assertion
      // that compares against it. Sidecar `.gold.png` files used to sit alongside the gold as a
      // second copy of the same bytes; they were deleted, and with them the question of which
      // copy was authoritative.
      const hasVisual = (raw.checks || []).some((c) => c.probe === 'raster');

      // Extract canvas / dimensions
      let width = raw.width || (raw.parameters && raw.parameters.width) || existingRecord.width || null;
      let height = raw.height || (raw.parameters && raw.parameters.height) || existingRecord.height || null;
      let density = raw.density || (raw.parameters && raw.parameters.density) || existingRecord.density || 1.0;

      // Extract component kinds / elements tested
      let componentKinds = existingRecord.component_kinds ? [...existingRecord.component_kinds] : [];
      let componentCount = existingRecord.component_count || null;
      let targetDisplay = '';

      // Everything below is derived from the v2 `checks` / `timeline` pair rather than from
      // per-subsystem `expected_*` keys. One structure answers the same question for every
      // category — "how many assertions, over what" — so adding a subsystem no longer means
      // adding a branch here just to be counted.
      const st = checkStats(raw);

      if (catId === 'layout') {
        // For a layout test the interesting number is the size of the tree it pins, which is
        // the largest single tree assertion rather than the sum over resize steps.
        if (!componentCount) componentCount = st.widest('tree') || 3;
        if (componentKinds.length === 0) {
          const kinds = new Set(['BoxLayout', 'RootLayoutComponent']);
          if (name.includes('column')) kinds.add('ColumnLayout');
          if (name.includes('row')) kinds.add('RowLayout');
          if (name.includes('fitbox')) kinds.add('FitBoxLayout');
          if (name.includes('flow')) kinds.add('FlowLayout');
          if (name.includes('collapsible_column')) kinds.add('CollapsibleColumnLayout');
          if (name.includes('collapsible_row')) kinds.add('CollapsibleRowLayout');
          if (name.includes('state_layout')) kinds.add('StateLayout');
          if (name.includes('core_text') || name.includes('text')) kinds.add('CoreText');
          if (name.includes('canvas')) kinds.add('CanvasLayout');
          if (name.includes('image')) kinds.add('ImageLayout');
          if (name.includes('spacer')) kinds.add('Spacer');
          componentKinds = Array.from(kinds);
        }
        targetDisplay = (width && height) ? `${width}×${height} @ ${density}x` : 'Dynamic Layout';
      } else if (catId === 'expressions') {
        const varCount = st.count('float', 'int', 'color') || 5;
        componentCount = varCount;
        componentKinds = ['FloatExpression', 'ExpressionGraph'];
        if (name.includes('waveforms') || name.includes('sine')) componentKinds.push('Waveforms');
        if (name.includes('trig')) componentKinds.push('Trigonometry');
        if (name.includes('trajectory')) componentKinds.push('Ballistics');
        if (name.includes('color')) componentKinds.push('ColorExpressions');
        if (name.includes('bitwise')) componentKinds.push('BitwiseOps');
        targetDisplay = `${varCount} variables`;
      } else if (catId === 'particles') {
        // Particle checks are one-per-sampled-frame, each holding the whole population.
        const pCount = st.widest('particles') || 6;
        const fCount = st.count('particles') || 5;
        componentCount = pCount;
        componentKinds = ['ParticleAnimation', 'PhysicsSimulation'];
        if (name.includes('burst')) componentKinds.push('RadialBurst');
        if (name.includes('damping') || name.includes('drag')) componentKinds.push('DragDamping');
        if (name.includes('bounce')) componentKinds.push('BoundaryBounce');
        if (name.includes('fountain') || name.includes('gravity')) componentKinds.push('GravityFountain');
        targetDisplay = `${pCount} particles / ${fCount} frames`;
      } else if (catId === 'loom') {
        const opsCount = st.scalar('ops:count') || st.items('records:components') || 4;
        componentCount = opsCount;
        componentKinds = ['LoomBuffer', 'WireProtocol'];
        if (name.includes('macro')) componentKinds.push('MacroExpansion');
        if (name.includes('slot')) componentKinds.push('SlotBlocks');
        if (name.includes('remapping')) componentKinds.push('IDRemapping');
        if (name.includes('foreach')) componentKinds.push('ForEachBlock');
        targetDisplay = `${opsCount} wire ops`;
      } else if (catId === 'canvas') {
        const cmdCount = st.items('draw_log:commands') || st.items('records:glyph_runs', 'records:anchor_runs', 'relation:anchor_runs') || 4;
        componentCount = cmdCount;
        componentKinds = ['CanvasOperations', '2DDrawing'];
        if (name.includes('path') || name.includes('bezier')) componentKinds.push('PathBezier');
        if (name.includes('clip')) componentKinds.push('ClipRect');
        if (name.includes('matrix')) componentKinds.push('MatrixStack');
        if (name.includes('bitmap')) componentKinds.push('BitmapScaled');
        if (name.startsWith('text_')) {
          componentKinds.push('TextPlacement');
          if (st.has('records:glyph_runs')) componentKinds.push('DrawTextOnPath');
          if (st.has('records:anchor_runs', 'relation:anchor_runs')) componentKinds.push('DrawTextAnchored');
          if (name.includes('circle')) componentKinds.push('DrawTextOnCircle');
          targetDisplay = `${cmdCount} placement assertions`;
        } else {
          targetDisplay = `${width || 200}×${height || 200} @ 1.0x`;
        }
      } else if (catId === 'shaders') {
        const uniformCount = st.items('records:uniforms') || st.items('draw_log:commands') || 2;
        componentCount = uniformCount;
        componentKinds = ['Shaders', 'ShaderData'];
        if (name.includes('agsl')) componentKinds.push('AGSL');
        if (name.includes('uniform')) componentKinds.push('UniformBinding');
        if (name.includes('gradient')) componentKinds.push('ShaderGradient');
        targetDisplay = `${uniformCount} shader pipelines`;
      } else if (catId === 'clock') {
        componentCount = st.steps('clock_snapshot') || 1;
        componentKinds = ['ClockSimulation', 'TimeProvider'];
        if (name.includes('hands')) componentKinds.push('AnalogHands');
        if (name.includes('continuous') || name.includes('sweep')) componentKinds.push('ContinuousSweep');
        if (name.includes('calendar') || name.includes('digital')) componentKinds.push('DigitalCalendar');
        targetDisplay = `${width || 300}×${height || 300} @ 1.0x`;
      } else if (catId === 'interactivity') {
        const actCount = st.gestureSteps() || 2;
        componentCount = actCount;
        componentKinds = ['TouchInteractivity', 'GestureHandler'];
        if (name.includes('click')) componentKinds.push('ClickDispatch');
        if (name.includes('hit_testing')) componentKinds.push('HitTesting');
        if (name.includes('slider')) componentKinds.push('SliderDrag');
        if (name.includes('multi_click')) componentKinds.push('MultiClick');
        targetDisplay = `${actCount} touch actions`;
      } else if (catId === 'textoperations') {
        const textCount = st.count('text') || 3;
        componentCount = textCount;
        componentKinds = ['TextOperations', 'DynamicBuffers'];
        if (name.includes('formatting') || name.includes('float')) componentKinds.push('TextFromFloat');
        if (name.includes('merge')) componentKinds.push('TextMerge');
        if (name.includes('transform')) componentKinds.push('TextTransform');
        targetDisplay = `${textCount} text buffers`;
      } else if (catId === 'colortheme') {
        const colCount = st.count('color') || 4;
        componentCount = colCount;
        componentKinds = ['ColorTheme', 'DynamicPalette'];
        if (name.includes('mode') || name.includes('switching')) componentKinds.push('ThemeLightDark');
        if (name.includes('expression')) componentKinds.push('ColorExpression');
        targetDisplay = `${colCount} color slots`;
      } else if (catId === 'dataoperations') {
        const itemsCount = st.count('float_array:dynamic', 'float_array:data', 'float', 'text') || 2;
        componentCount = itemsCount;
        componentKinds = ['DataOperations', 'BufferStorage'];
        if (name.includes('dynamic') || name.includes('list')) componentKinds.push('DynamicListFloat');
        if (name.includes('map')) componentKinds.push('DataMapLookup');
        targetDisplay = `${itemsCount} data operations`;
      } else if (catId === 'pathoperations') {
        const pathCount = st.items('records:paths', 'records:tweens') || 1;
        componentCount = pathCount;
        componentKinds = ['PathOperations', 'VectorGeometry'];
        if (name.includes('create') || name.includes('append')) componentKinds.push('PathAppend');
        if (name.includes('tween') || name.includes('morph')) componentKinds.push('PathTweenMorph');
        targetDisplay = `${pathCount} path shapes`;
      } else if (catId === 'semantics') {
        const semCount = st.items('records:semantics') || 2;
        componentCount = semCount;
        componentKinds = ['Semantics', 'AccessibilityTree'];
        if (name.includes('roles')) componentKinds.push('AccessibilityRoles');
        if (name.includes('tree')) componentKinds.push('SemanticsHierarchy');
        targetDisplay = `${semCount} semantic nodes`;
      } else if (catId === 'scheduling') {
        const schedCount = st.items('records:impulses') || st.count() || 2;
        componentCount = schedCount;
        componentKinds = ['AutonomousScheduling', 'TimerDispatch'];
        if (name.includes('wake')) componentKinds.push('WakeInIntervals');
        if (name.includes('impulse')) componentKinds.push('ImpulseAnimation');
        targetDisplay = `${schedCount} schedule timers`;
      } else if (catId === 'conditionals') {
        const branchCount = st.items('trace:branches', 'ops:absent', 'ops:present');
        componentCount = branchCount;
        componentKinds = ['ConditionalOperations'];
        if (st.has('ops:absent', 'ops:present')) componentKinds.push('Skip');
        if (name.includes('nested')) componentKinds.push('NestedBranches');
        targetDisplay = `${branchCount} branch assertions`;
      } else if (catId === 'matrixmath') {
        const mCount = st.count('matrix', 'float');
        componentCount = mCount;
        componentKinds = ['Matrix'];
        if (st.has('matrix')) componentKinds.push('MatrixValues');
        if (st.has('float')) componentKinds.push('MatrixVectorMath');
        if (name.includes('expression')) componentKinds.push('MatrixExpression');
        if (name.includes('constant')) componentKinds.push('MatrixConstant');
        targetDisplay = `${mCount} matrix/vector values`;
      } else if (catId === 'animationspec') {
        const sCount = st.items('records:animation_specs', 'records:component_bindings');
        componentCount = sCount;
        componentKinds = ['AnimationSpec'];
        if (st.has('records:component_bindings')) componentKinds.push('ComponentBinding');
        if (name.includes('defaults')) componentKinds.push('SpecDefaults');
        targetDisplay = `${sCount} spec assertions`;
      }

      const goldRecord = {
        name,
        category: catId,
        description: raw.description || existingRecord.description || `${catConf.title}: ${name}`,
        target_display: targetDisplay,
        width,
        height,
        density,
        component_count: componentCount,
        component_kinds: componentKinds,
        verification_type: catConf.verification,
        has_visual: hasVisual,
        suspicious: isSuspicious,
        suspicious_reason: defectReason,
        tolerance: raw.parameters ? raw.parameters.tolerance : (existingRecord.tolerance || 0.5)
      };

      allGoldFiles.push(goldRecord);

      subsystemStats[catId].total++;
      if (isSuspicious) subsystemStats[catId].suspicious++;
      else subsystemStats[catId].active++;
      if (hasVisual) subsystemStats[catId].visuals++;
    }
  }

  // Sort tests by category order, then name
  const catOrder = Object.keys(SUBSYSTEM_CONFIG);
  allGoldFiles.sort((a, b) => {
    const idxA = catOrder.indexOf(a.category);
    const idxB = catOrder.indexOf(b.category);
    if (idxA !== idxB) return idxA - idxB;
    return a.name.localeCompare(b.name);
  });

  const totalGoldFiles = allGoldFiles.length;
  const totalComponents = allGoldFiles
    .filter(f => f.category === 'layout')
    .reduce((acc, f) => acc + (f.component_count || 0), 0);
  const totalVisuals = allGoldFiles.filter(f => f.has_visual).length;
  const totalSuspicious = allGoldFiles.filter(f => f.suspicious).length;

  const outputJson = {
    coverage,
    total_gold_files: totalGoldFiles,
    total_components: totalComponents,
    total_visuals: totalVisuals,
    total_suspicious: totalSuspicious,
    generated_at: new Date().toISOString(),
    subsystems: subsystemStats,
    gold_files: allGoldFiles
  };

  mkdirSync(outDir, { recursive: true });

  writeFileSync(overviewJsonPath, JSON.stringify(outputJson, null, 2));
  console.log(`Wrote multi-subsystem gold overview JSON (${totalGoldFiles} tests) to ${overviewJsonPath}`);

  // Generate HTML overview
  const htmlContent = renderGoldOverviewHtml(outputJson);
  const overviewHtmlPath = join(outDir, 'gold-overview.html');
  writeFileSync(overviewHtmlPath, htmlContent);
  console.log(`Wrote multi-subsystem gold overview HTML (${totalGoldFiles} tests) to ${overviewHtmlPath}`);
}

// The same pass-rate scale the conformance audit uses, so a number means the same
// thing on both pages: green only at 100%, light green from 90%, orange from 60%.
const RATE_COLORS = {
  perfect: '#22c55e',  // 100%
  high: '#86efac',     // light green
  warm: '#facc15',     // yellow
  mid: '#f59e0b',      // orange
  low: '#ef4444',      // red
};
function rateColor(pct) {
  const r = Number(pct) || 0;
  if (r >= 100) return RATE_COLORS.perfect;
  if (r >= 90) return RATE_COLORS.high;
  if (r >= 60) return RATE_COLORS.mid;
  return RATE_COLORS.low;
}
// Coverage uses a five-step scale: green only at 100, light green from 90, yellow from 80,
// orange from 50, red below. Coverage of a general-purpose engine by a conformance corpus is
// nowhere near 100 today, so most subsystems land in the orange/red end -- that is the honest
// reading, not a rendering artefact.
const COV_BANDS = { perfect: 100, high: 90, warm: 80, mid: 50 };
function covColor(pct) {
  const r = Number(pct) || 0;
  if (r >= COV_BANDS.perfect) return RATE_COLORS.perfect;
  if (r >= COV_BANDS.high) return RATE_COLORS.high;
  if (r >= COV_BANDS.warm) return RATE_COLORS.warm;
  if (r >= COV_BANDS.mid) return RATE_COLORS.mid;
  return RATE_COLORS.low;
}

function renderGoldOverviewHtml(data) {
  const cov = data.coverage;
  const classes = cov.classes || [];
  const bySub = cov.by_subsystem || {};
  const hasSubCoverage = Object.keys(bySub).length > 0;

  const subCardsHtml = Object.values(data.subsystems).map(sub => `
    <div class="sub-card" onclick="filterGoldCategory('${sub.id}')" style="border-top: 3px solid ${sub.color};">
      <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom: 6px;">
        <span class="sub-icon">${sub.icon}</span>
        <span class="badge" style="color:${sub.color}; border-color:${sub.color}; background:rgba(255,255,255,0.04); font-size:11px;">
          ${sub.total} tests
        </span>
      </div>
      <div class="sub-title" style="color:#fff;">${sub.title}</div>
      <div class="sub-desc">${sub.description}</div>
      ${bySub[sub.id] ? `
      <div class="sub-cov">
        <div class="sub-cov-row">
          <span>Reference code coverage</span>
          <span style="color:${covColor(bySub[sub.id].instruction_coverage_pct)}; font-weight:700;">
            ${bySub[sub.id].instruction_coverage_pct.toFixed(1)}%
          </span>
        </div>
        <div class="bar-bg"><div class="bar-fill" style="width:${bySub[sub.id].instruction_coverage_pct}%; background:${covColor(bySub[sub.id].instruction_coverage_pct)};"></div></div>
        <div class="sub-cov-sub">${bySub[sub.id].covered_instructions.toLocaleString()} / ${bySub[sub.id].total_instructions.toLocaleString()} instructions &middot; ${bySub[sub.id].line_coverage_pct.toFixed(1)}% lines</div>
      </div>` : ''}
      <div class="sub-meta">
        <span style="color:${sub.color}; font-weight:600;">${sub.verification}</span>
        ${sub.visuals > 0 ? `<span class="badge" style="color:#10b981; border-color:#059669; font-size:10px;">${sub.visuals} Visuals</span>` : ''}
        ${sub.suspicious > 0 ? `<span class="badge" style="color:#c084fc; border-color:rgba(192,132,252,0.4); font-size:10px;">${sub.suspicious} Suspicious</span>` : ''}
      </div>
    </div>
  `).join('');

  const filterButtonsHtml = `
    <button class="btn active" onclick="filterGoldCategory('all')">All Subsystems (${data.total_gold_files})</button>
    ${Object.values(data.subsystems).map(sub => `
      <button class="btn" data-cat-btn="${sub.id}" onclick="filterGoldCategory('${sub.id}')">
        ${sub.shortName} (${sub.total})
      </button>
    `).join('')}
    <button class="btn" data-cat-btn="visuals" onclick="filterGoldCategory('visuals')" style="color:#10b981;">
      Visual Rasters (${data.total_visuals})
    </button>
    <button class="btn" data-cat-btn="suspicious" onclick="filterGoldCategory('suspicious')" style="color:#c084fc;">
      Suspicious Only (${data.total_suspicious})
    </button>
  `;

  // Ordered by the subsystem catalogue, with anything unattributed ("core") last.
  const subCovRowsHtml = Object.keys(bySub)
    .sort((a, b) => {
      const ia = Object.keys(SUBSYSTEM_CONFIG).indexOf(a);
      const ib = Object.keys(SUBSYSTEM_CONFIG).indexOf(b);
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    })
    .map(id => {
      const c = bySub[id];
      const conf = SUBSYSTEM_CONFIG[id];
      const title = conf ? conf.title : 'Core / Shared Infrastructure';
      const icon = conf ? conf.icon : '⚙️';
      const classCount = classes.filter(cl => cl.subsystem === id).length;
      const goldCount = conf && data.subsystems[id] ? data.subsystems[id].total : 0;
      const cell = (pct, covered, total) => `
        <td>
          <div style="display:flex; justify-content:space-between; font-size:12px;">
            <span style="color:${covColor(pct)}; font-weight:700;">${pct.toFixed(1)}%</span>
            <span style="color:var(--text-muted);">${covered.toLocaleString()} / ${total.toLocaleString()}</span>
          </div>
          <div class="bar-bg"><div class="bar-fill" style="width:${pct}%; background:${covColor(pct)};"></div></div>
        </td>`;
      return `
      <tr${conf ? ` onclick="filterGoldCategory('${id}')" style="cursor:pointer;"` : ''}>
        <td><strong>${icon} ${escapeHtml(title)}</strong></td>
        <td class="mono">${classCount}</td>
        <td class="mono">${conf ? goldCount : '—'}</td>
        ${cell(c.instruction_coverage_pct, c.covered_instructions, c.total_instructions)}
        ${cell(c.line_coverage_pct, c.covered_lines, c.total_lines)}
        ${cell(c.branch_coverage_pct, c.covered_branches, c.total_branches)}
      </tr>`;
    }).join('');

  const covRowsHtml = classes.map(c => {
    const isCovered = (c.instructions_covered > 0 || c.lines_covered > 0);
    const instrColor = c.instructions_total === 0 ? '#64748b' : covColor(c.instructions_pct);
    const lineColor = c.lines_total === 0 ? '#64748b' : covColor(c.lines_pct);
    const owner = c.subsystem ? (SUBSYSTEM_CONFIG[c.subsystem]
        ? SUBSYSTEM_CONFIG[c.subsystem].shortName : 'Core') : null;
    let tagClass = 'tag';
    if (c.category === 'Layout Manager') tagClass = 'tag tag-manager';
    else if (c.category === 'Modifier') tagClass = 'tag tag-modifier';
    else if (c.category === 'Measure Policy') tagClass = 'tag tag-policy';

    return `
      <tr data-cat="${escapeHtml(c.category)}" data-cov="${isCovered ? 'covered' : 'zero'}" data-sub="${escapeHtml(c.subsystem || '')}">
        <td class="mono"><strong>${escapeHtml(c.name)}</strong></td>
        <td>
          <span class="${tagClass}">${escapeHtml(c.category)}</span>
          ${owner ? `<span class="tag" style="margin-left:4px; opacity:0.75;">${escapeHtml(owner)}</span>` : ''}
        </td>
        <td>
          <div style="display:flex; justify-content:space-between; font-size:12px;">
            <span style="color:${instrColor}; font-weight:600;">${c.instructions_pct.toFixed(1)}%</span>
            <span style="color:var(--text-muted);">${c.instructions_covered} / ${c.instructions_total}</span>
          </div>
          <div class="bar-bg"><div class="bar-fill" style="width:${c.instructions_pct}%; background:${instrColor};"></div></div>
        </td>
        <td>
          <div style="display:flex; justify-content:space-between; font-size:12px;">
            <span style="color:${lineColor}; font-weight:600;">${c.lines_pct.toFixed(1)}%</span>
            <span style="color:var(--text-muted);">${c.lines_covered} / ${c.lines_total}</span>
          </div>
          <div class="bar-bg"><div class="bar-fill" style="width:${c.lines_pct}%; background:${lineColor};"></div></div>
        </td>
      </tr>
    `;
  }).join('');

  const goldRowsHtml = data.gold_files.map(gf => {
    const sub = SUBSYSTEM_CONFIG[gf.category] || SUBSYSTEM_CONFIG.layout;
    const kindTags = (gf.component_kinds || []).map(k => {
      let cls = 'tag';
      if (k.endsWith('Layout') || k === 'CoreText') cls = 'tag tag-manager';
      else if (k.includes('Modifier') || k.includes('Action')) cls = 'tag tag-modifier';
      return `<span class="${cls}">${escapeHtml(k)}</span>`;
    }).join(' ');

    const visualBadge = gf.has_visual ?
      `<span class="badge" style="background:rgba(16,185,129,0.15); color:#34d399; border-color:rgba(16,185,129,0.3); font-size:10px; margin-left:6px;">📸 Visual PNG</span>` : '';

    const suspBadge = gf.suspicious ?
      `<br/><span class="badge" style="background:rgba(192,132,252,0.15); color:#c084fc; border:1px solid rgba(192,132,252,0.3); font-size:10px; margin-top:4px;">SUSPICIOUS (REFERENCE DEFECT)</span>` : '';

    const defectHtml = gf.suspicious_reason ?
      `<div style="color:#c084fc; font-size:12px; margin-top:6px; background:rgba(192,132,252,0.06); padding:4px 8px; border-radius:4px; border-left:3px solid #c084fc;"><strong>Defect:</strong> ${escapeHtml(gf.suspicious_reason)}</div>` : '';

    const trBg = gf.suspicious ? 'background: rgba(192, 132, 252, 0.04);' : '';

    return `
      <tr data-cat="${gf.category}" data-susp="${gf.suspicious}" data-vis="${gf.has_visual}" style="${trBg}">
        <td class="mono">
          <strong style="color:${sub.color};">${escapeHtml(gf.name)}</strong>
          ${visualBadge}
          ${suspBadge}
        </td>
        <td>
          <span class="badge" style="color:${sub.color}; border-color:${sub.borderColor}; background:${sub.bgColor};">
            ${sub.shortName}
          </span>
        </td>
        <td style="color:var(--text-muted); font-size:12px;">${escapeHtml(gf.target_display)}</td>
        <td>
          <div style="display:flex; flex-wrap:wrap; gap:4px; align-items:center;">
            ${gf.component_count ? `<span class="tag" style="background:#23304a; color:#f1f5f9; font-weight:700;">${gf.component_count} items</span>` : ''}
            ${kindTags}
          </div>
        </td>
        <td style="color:#cbd5e1;">
          ${escapeHtml(gf.description)}
          ${defectHtml}
        </td>
      </tr>
    `;
  }).join('');

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>RemoteCompose Conformance — Gold Files & Subsystems Overview</title>
  <style>
    :root {
      --bg: #090d16; --card: #131b2e; --card-header: #1a243c; --border: #23304a;
      --text: #f1f5f9; --text-muted: #94a3b8; --accent: #3b82f6; --accent-glow: rgba(59,130,246,0.15);
      --pass: #10b981; --warn: #f59e0b; --fail: #ef4444;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
           background: var(--bg); color: var(--text); padding: 32px 24px; line-height: 1.5; }
    .container { max-width: 1440px; margin: 0 auto; }
    header { margin-bottom: 28px; border-bottom: 1px solid var(--border); padding-bottom: 20px; }
    .badge-bar { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
    .badge { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em;
             padding: 4px 10px; border-radius: 9999px; background: #1e293b; color: #38bdf8;
             border: 1px solid #334155; display: inline-flex; align-items: center; }
    h1 { font-size: 28px; font-weight: 800; color: #fff; letter-spacing: -0.02em; margin-bottom: 6px; }
    .subtitle { color: var(--text-muted); font-size: 14px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .card { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 20px; }
    .card-label { font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); margin-bottom: 8px; }
    .card-val { font-size: 28px; font-weight: 800; color: #fff; }
    .card-sub { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
    .sub-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 14px; margin-bottom: 30px; }
    .sub-card { background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px; cursor: pointer; transition: all 0.15s ease; }
    .sub-card:hover { transform: translateY(-2px); border-color: rgba(255,255,255,0.25); background: #18223a; }
    .sub-icon { font-size: 20px; }
    .sub-title { font-size: 14px; font-weight: 700; margin-bottom: 4px; }
    .sub-desc { font-size: 12px; color: var(--text-muted); line-height: 1.4; margin-bottom: 10px; height: 34px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
    .sub-cov {
      margin-top: 10px;
      padding-top: 9px;
      border-top: 1px solid rgba(148, 163, 184, 0.14);
    }
    .sub-cov-row {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      font-size: 11px;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.4px;
      margin-bottom: 4px;
    }
    .sub-cov-sub {
      font-size: 10.5px;
      color: var(--text-muted);
      margin-top: 4px;
    }
    .sub-meta { display: flex; justify-content: space-between; align-items: center; font-size: 11px; border-top: 1px solid rgba(255,255,255,0.06); padding-top: 8px; }
    .notice-box { background: rgba(59,130,246,0.08); border: 1px solid rgba(59,130,246,0.25); border-radius: 10px; padding: 16px 20px; margin-bottom: 28px; }
    .notice-title { font-weight: 700; color: #60a5fa; font-size: 14px; margin-bottom: 4px; }
    .notice-desc { font-size: 13px; color: #cbd5e1; line-height: 1.6; }
    .section-title { font-size: 18px; font-weight: 700; margin: 28px 0 14px 0; color: #f8fafc; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
    .filter-bar { display: flex; gap: 8px; margin-bottom: 14px; flex-wrap: wrap; }
    .btn { background: var(--card); border: 1px solid var(--border); color: var(--text-muted); padding: 6px 14px; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.15s; }
    .btn:hover { background: #1e293b; color: #fff; }
    .btn.active { background: var(--accent); border-color: var(--accent); color: #fff; }
    .search-input { background: var(--card); border: 1px solid var(--border); border-radius: 8px; color: #fff; padding: 8px 14px; font-size: 13px; width: 340px; outline: none; }
    .search-input:focus { border-color: var(--accent); }
    table { width: 100%; border-collapse: collapse; background: var(--card); border: 1px solid var(--border); border-radius: 10px; overflow: hidden; margin-bottom: 28px; font-size: 13px; }
    th { background: var(--card-header); padding: 12px 16px; text-align: left; font-weight: 600; color: var(--text-muted); border-bottom: 1px solid var(--border); }
    td { padding: 11px 16px; border-bottom: 1px solid rgba(255,255,255,0.04); vertical-align: middle; }
    tr:last-child td { border-bottom: none; }
    tr:hover td { background: rgba(255,255,255,0.02); }
    .bar-bg { width: 100%; height: 6px; background: rgba(255,255,255,0.08); border-radius: 3px; overflow: hidden; margin-top: 4px; }
    .bar-fill { height: 100%; border-radius: 3px; }
    .tag { display: inline-block; padding: 2px 7px; border-radius: 4px; font-size: 11px; font-weight: 600; background: #1e293b; color: #94a3b8; border: 1px solid #334155; margin: 2px; }
    .tag-manager { background: rgba(59,130,246,0.15); color: #93c5fd; border-color: rgba(59,130,246,0.3); }
    .tag-modifier { background: rgba(16,185,129,0.15); color: #6ee7b7; border-color: rgba(16,185,129,0.3); }
    .tag-policy { background: rgba(245,158,11,0.15); color: #fcd34d; border-color: rgba(245,158,11,0.3); }
    .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 12px; }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <div class="badge-bar">
        <span class="badge">RemoteCompose Conformance</span>
        <span class="badge" style="color:#10b981; border-color:#059669;">Reference Engine: Android</span>
        <span class="badge" style="color:#a855f7; border-color:#9333ea;">Subsystems: ${Object.keys(SUBSYSTEM_CONFIG).length}</span>
        <span class="badge" style="color:#f59e0b; border-color:#d97706;">Total Gold Files: ${data.total_gold_files}</span>
      </div>
      <h1>RemoteCompose Conformance — Gold Files & Subsystems Overview</h1>
      <div class="subtitle">Complete specification gold file catalog, verification types, and reference implementation coverage across all ${Object.keys(SUBSYSTEM_CONFIG).length} RemoteCompose subsystems</div>
    </header>

    <div class="grid">
      <div class="card">
        <div class="card-label">Total Gold Files</div>
        <div class="card-val">${data.total_gold_files}</div>
        <div class="card-sub">${data.total_gold_files - data.total_suspicious} active + ${data.total_suspicious} suspicious</div>
      </div>
      <div class="card">
        <div class="card-label">Subsystems Covered</div>
        <div class="card-val" style="color:#a855f7;">${Object.keys(SUBSYSTEM_CONFIG).length}</div>
        <div class="card-sub">Layout, Wire, Shaders, Canvas, Time...</div>
      </div>
      <div class="card">
        <div class="card-label">Engine Instr Coverage</div>
        <div class="card-val" style="color:${covColor(cov.instruction_coverage_pct)};">${cov.instruction_coverage_pct.toFixed(1)}%</div>
        <div class="card-sub">${cov.covered_instructions.toLocaleString()} / ${cov.total_instructions.toLocaleString()} instrs</div>
      </div>
      <div class="card">
        <div class="card-label">Engine Line Coverage</div>
        <div class="card-val" style="color:${covColor(cov.line_coverage_pct)};">${cov.line_coverage_pct.toFixed(1)}%</div>
        <div class="card-sub">${cov.covered_lines.toLocaleString()} / ${cov.total_lines.toLocaleString()} lines</div>
      </div>
      <div class="card" style="border-color: rgba(192, 132, 252, 0.4);">
        <div class="card-label">Suspicious Defects</div>
        <div class="card-val" style="color:#c084fc;">${data.total_suspicious}</div>
        <div class="card-sub">Ignored for player conformance</div>
      </div>
    </div>

    <div class="notice-box">
      <div class="notice-title">Scope of RemoteCompose Conformance Testing</div>
      <div class="notice-desc">
        This conformance suite indexes <strong>${data.total_gold_files} gold specification files</strong> spanning all ${Object.keys(SUBSYSTEM_CONFIG).length} RemoteCompose subsystems.
        Verification includes box coordinate trees, dynamic math expression evaluation, multi-frame particle simulations, binary wire macros, 2D vector drawing commands &amp; text-on-path glyph placement, AGSL/gradient shaders &amp; uniform bindings, clock second-hand sweeps, touch dispatch, text buffer transforms, light/dark themes, dynamic float lists, path morphing, accessibility semantics, autonomous wake/impulse scheduling, conditional branch execution, matrix expression evaluation, and animation specifications.
      </div>
    </div>

    ${data.total_suspicious > 0 ? `
    <div class="notice-box" style="background: rgba(192, 132, 252, 0.08); border-color: rgba(192, 132, 252, 0.3); margin-bottom: 28px;">
      <div class="notice-title" style="color: #c084fc;">⚠ Suspicious Reference Implementations (${data.total_suspicious} Tests Tagged)</div>
      <div class="notice-desc" style="color: #e9d5ff;">
        The following tests generate outputs from the Java reference implementation that exhibit known reference defects, modifier omission, or specification contradictions. They are tagged with <code>tags: ["suspicious"]</code> and excluded from player conformance requirements until the reference implementation is corrected:
      </div>
      <ul style="margin-top: 10px; margin-left: 20px; font-size: 13px; color: #cbd5e1; line-height: 1.6;">
${data.gold_files.filter(gf => gf.suspicious).map(gf =>
        `        <li><strong style="color:#38bdf8;">${escapeHtml(gf.name)}</strong>: ${escapeHtml(gf.suspicious_reason || 'no reason recorded')}</li>`
      ).join('\n')}
      </ul>
    </div>` : ''}

    <!-- 18 Subsystem Breakdown Cards -->
    <div class="section-title">
      <span>Subsystems &amp; Verification Domains (${Object.keys(data.subsystems).length} Areas)</span>
      <span style="font-size:13px; color:var(--text-muted); font-weight:normal;">Click any subsystem to filter the test catalog below</span>
    </div>
    <div class="sub-grid">
      ${subCardsHtml}
    </div>

    <!-- Coverage Sections -->
    ${hasSubCoverage ? `
    <div class="section-title">
      <span>Reference Engine Code Coverage by Subsystem</span>
      <span style="font-size:13px; color:var(--text-muted); font-weight:normal;">JaCoCo over ${escapeHtml(cov.subsystem)}.* while every gold document in the corpus is played &middot; colour bands <span style="color:${RATE_COLORS.perfect};">${COV_BANDS.perfect}%</span> / <span style="color:${RATE_COLORS.high};">&ge;${COV_BANDS.high}%</span> / <span style="color:${RATE_COLORS.warm};">&ge;${COV_BANDS.warm}%</span> / <span style="color:${RATE_COLORS.mid};">&ge;${COV_BANDS.mid}%</span> / <span style="color:${RATE_COLORS.low};">below ${COV_BANDS.mid}%</span></span>
    </div>
    <table id="cov-sub-table">
      <thead>
        <tr>
          <th style="width:22%;">Subsystem</th>
          <th style="width:8%;">Classes</th>
          <th style="width:8%;">Gold Files</th>
          <th style="width:21%;">Instruction Coverage</th>
          <th style="width:21%;">Line Coverage</th>
          <th style="width:20%;">Branch Coverage</th>
        </tr>
      </thead>
      <tbody>
        ${subCovRowsHtml}
      </tbody>
    </table>
    ` : ''}

    <div class="section-title">
      <span>Reference Engine Code Coverage by Class</span>
      <span style="font-size:13px; color:var(--text-muted); font-weight:normal;">Target: ${escapeHtml(cov.subsystem)}.* (${classes.length} classes)</span>
    </div>
    <div class="filter-bar">
      <button class="btn active" onclick="filterCov('all')">All Classes (${classes.length})</button>
      <button class="btn" onclick="filterCov('Layout Manager')">Layout Managers</button>
      <button class="btn" onclick="filterCov('Modifier')">Modifiers</button>
      <button class="btn" onclick="filterCov('Measure Policy')">Measure Policies</button>
      <button class="btn" onclick="filterCov('Measure Subsystem')">Measure Subsystem</button>
      <button class="btn" onclick="filterCov('covered')">Covered Only</button>
    </div>
    <table id="cov-table">
      <thead>
        <tr>
          <th style="width:35%;">Class Name</th>
          <th style="width:15%;">Category</th>
          <th style="width:25%;">Instruction Coverage</th>
          <th style="width:25%;">Line Coverage</th>
        </tr>
      </thead>
      <tbody>
        ${covRowsHtml}
      </tbody>
    </table>

    <!-- Complete Gold Files Catalog -->
    <div class="section-title">
      <span>Gold Files Specification Catalog (${data.total_gold_files} Tests)</span>
      <input type="text" id="search" class="search-input" placeholder="Search test name, subsystem, elements..." oninput="searchTests()">
    </div>
    <div class="filter-bar gold-filter-bar">
      ${filterButtonsHtml}
    </div>
    <table id="gold-table">
      <thead>
        <tr>
          <th style="width:24%;">Test Name</th>
          <th style="width:13%;">Subsystem</th>
          <th style="width:13%;">Target / Canvas</th>
          <th style="width:22%;">Elements Tested</th>
          <th style="width:28%;">Description</th>
        </tr>
      </thead>
      <tbody>
        ${goldRowsHtml}
      </tbody>
    </table>
  </div>

  <script>
    let currentCat = 'all';

    function filterCov(cat) {
      document.querySelectorAll('#cov-table').length && document.querySelectorAll('.filter-bar:not(.gold-filter-bar) .btn').forEach(b => b.classList.remove('active'));
      event.target.classList.add('active');
      const rows = document.querySelectorAll('#cov-table tbody tr');
      rows.forEach(r => {
        if (cat === 'all') r.style.display = '';
        else if (cat === 'covered') r.style.display = r.getAttribute('data-cov') === 'covered' ? '' : 'none';
        else r.style.display = r.getAttribute('data-cat') === cat ? '' : 'none';
      });
    }

    function filterGoldCategory(cat) {
      currentCat = cat;
      document.querySelectorAll('.gold-filter-bar .btn').forEach(b => {
        b.classList.remove('active');
        if (b.getAttribute('data-cat-btn') === cat || (cat === 'all' && !b.getAttribute('data-cat-btn'))) {
          b.classList.add('active');
        }
      });

      applyFilters();

      // Scroll to table if clicked from top cards
      const tbl = document.getElementById('gold-table');
      if (tbl && window.scrollY < tbl.offsetTop - 400) {
        tbl.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }

    function searchTests() {
      applyFilters();
    }

    function applyFilters() {
      const q = (document.getElementById('search')?.value || '').toLowerCase().trim();
      const rows = document.querySelectorAll('#gold-table tbody tr');

      rows.forEach(r => {
        const rowCat = r.getAttribute('data-cat');
        const isSusp = r.getAttribute('data-susp') === 'true';
        const isVis = r.getAttribute('data-vis') === 'true';

        let catMatch = true;
        if (currentCat === 'all') catMatch = true;
        else if (currentCat === 'suspicious') catMatch = isSusp;
        else if (currentCat === 'visuals') catMatch = isVis;
        else catMatch = (rowCat === currentCat);

        const textMatch = !q || r.textContent.toLowerCase().includes(q);

        r.style.display = (catMatch && textMatch) ? '' : 'none';
      });
    }
  </script>
</body>
</html>`;
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// The corpus this reads is a sibling of this file, and by default the artifacts land beside it
// too — they describe the golds, not any one player, so they belong at the top level.
//
//   node generate-gold-overview.mjs              # corpus and output both here
//   node generate-gold-overview.mjs --out DIR    # read here, write there
//   node generate-gold-overview.mjs CORPUS_DIR   # point at a scratch copy of the corpus
const targetDir = (process.argv[2] && !process.argv[2].startsWith('--')) ? process.argv[2] : __dirname;
const outDir = argValue('--out', targetDir);
runGenerator(targetDir, outDir);

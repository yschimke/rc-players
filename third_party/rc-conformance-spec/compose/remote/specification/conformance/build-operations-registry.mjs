import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const JAVA_OPS_PATH = path.resolve(
  __dirname,
  '../../remote-core/src/main/java/androidx/compose/remote/core/Operations.java'
);
const TS_PLAYER_DIR = '/Users/nicolasroard/Documents/GitHub/remotecompose-experiments/players/typescript';
const TS_OPS_PATH = path.join(TS_PLAYER_DIR, 'src/core/Operations.ts');
const TS_NODE_ENTRY = path.join(TS_PLAYER_DIR, 'build-node/node-entry.js');
const GOLD_ROOT = path.join(__dirname, 'gold');
const RESULTS_PATH = path.join(__dirname, 'results/typescript/conformance-results.json');
const OUT_REGISTRY_PATH = path.join(__dirname, 'operations-registry.json');

// Subsystem categorization helper for Android operations
function inferSubsystem(constName, javaClass, tsSrcFile) {
  const n = (constName || '').toUpperCase();
  const f = (tsSrcFile || '').toLowerCase();
  if (n.includes('SHADER') || n === 'DATA_SHADER') return 'shaders';
  if (n.includes('PATH_TWEEN') || n.includes('PARTICLE')) return 'particles';
  if (n.includes('IMPULSE')) return 'impulse';
  if (n.includes('MATRIX_CONSTANT') || n.includes('MATRIX_EXPRESSION') || n.includes('MATRIX_VECTOR_MATH') || n.includes('MATRIX_FROM_PATH')) return 'matrixmath';
  if (n.includes('CONDITIONAL') || n === 'SKIP') return 'conditionals';
  if (n.includes('ANIMATION_SPEC') || n.includes('ANIMATED_FLOAT') || n.includes('FLOAT_EXPRESSION') || n.includes('INTEGER_EXPRESSION') || n.includes('COLOR_EXPRESSIONS')) return 'expressions';
  if (n.includes('MACRO') || n.includes('PATTERN') || n.includes('LOOM') || f.includes('/loom/')) return 'loom';
  if (n.includes('TIME') || n.includes('WAKE_IN') || n.includes('CALENDAR')) return 'clock';
  if (n.includes('TOUCH') || n.includes('CLICK') || n.includes('ACTION') || n.includes('HAPTIC')) return 'interactivity';
  if (n.includes('SEMANTICS') || n.includes('ACCESSIBILITY') || n.includes('CONTENT_DESCRIPTION')) return 'semantics';
  if (n.includes('THEME') || n.includes('COLOR_CONSTANT') || n.includes('COLOR_THEME')) return 'theme';
  if (n.includes('TEXT') || n.includes('FONT')) return 'text';
  if (n.includes('BITMAP') || n.includes('IMAGE')) return 'bitmap';
  if (n.includes('LAYOUT') || n.includes('MODIFIER') || n.includes('COMPONENT') || n.includes('CONTAINER') || f.includes('/layout/')) return 'layout';
  if (n.includes('DRAW_') || n.includes('CLIP_') || n.includes('PAINT') || n.includes('MATRIX_') || n.includes('CANVAS') || n.includes('PATH')) return 'canvas';
  if (n === 'HEADER' || n.includes('DATA_') || n.includes('NAMED_VARIABLE') || n.includes('ID_LOOKUP') || n.includes('REM')) return 'wire';
  return 'core';
}

export async function buildOperationsRegistry() {
  const javaSrc = fs.readFileSync(JAVA_OPS_PATH, 'utf8');
  const tsOpsSrc = fs.readFileSync(TS_OPS_PATH, 'utf8');
  const resultsJson = JSON.parse(fs.readFileSync(RESULTS_PATH, 'utf8'));

  const testStatusByName = {};
  for (const r of resultsJson.results) {
    testStatusByName[r.name] = r.status;
  }

  // 1. Parse Android Operations.java constants (0..255)
  const javaConsts = {};
  const constRegex = /public\s+static\s+final\s+int\s+([A-Z0-9_]+)\s*=\s*(\d+)\s*;/g;
  let m;
  while ((m = constRegex.exec(javaSrc)) !== null) {
    const name = m[1];
    const id = parseInt(m[2], 10);
    if (id >= 0 && id <= 255) {
      if (!javaConsts[id]) {
        javaConsts[id] = { names: [], javaClass: null, activeInJava: false, profiles: [] };
      }
      if (!javaConsts[id].names.includes(name)) {
        javaConsts[id].names.push(name);
      }
    }
  }

  // Parse active registrations in Operations.java
  const putRegex = /^\s*(?!\/\/)([A-Za-z0-9_]+)\.put\(\s*([A-Z0-9_]+)\s*,\s*([A-Za-z0-9_]+)::read\s*\)/gm;
  while ((m = putRegex.exec(javaSrc)) !== null) {
    const mapName = m[1];
    const constName = m[2];
    const cls = m[3];
    for (const id of Object.keys(javaConsts)) {
      if (javaConsts[id].names.includes(constName)) {
        javaConsts[id].javaClass = cls;
        javaConsts[id].activeInJava = true;
        if (!javaConsts[id].profiles.includes(mapName)) {
          javaConsts[id].profiles.push(mapName);
        }
      }
    }
  }

  // 2. Parse TypeScript Operations.ts imports and m.set calls
  const importMap = {};
  const importRegex = /import\s*\{([^}]+)\}\s*from\s*["']([^"']+)["']/g;
  while ((m = importRegex.exec(tsOpsSrc)) !== null) {
    const names = m[1].split(',').map(s => s.trim()).filter(Boolean);
    const srcPath = m[2];
    for (const n of names) importMap[n] = srcPath;
  }

  const setLines = [];
  const setRegex = /m\.set\(\s*([^,]+)\s*,\s*([A-Za-z0-9_]+)\.read\s*\)/g;
  while ((m = setRegex.exec(tsOpsSrc)) !== null) {
    const clsName = m[2].trim();
    setLines.push({
      keyExpr: m[1].trim(),
      clsName,
      srcFile: importMap[clsName] || 'unknown',
    });
  }

  // Import TS runtime to map exact runtime opcode numbers and scan conformance gold documents
  const mod = await import(TS_NODE_ENTRY);
  const { RemoteComposeBuffer, CoreDocument, CanvasPaintContext, WebRemoteContext, Operations } = mod;
  const { createCanvas } = await import(
    path.join(TS_PLAYER_DIR, 'node_modules/canvas/index.js')
  );

  const map = Operations.getOperations();
  const entries = Array.from(map.entries());
  const tsMap = {};
  entries.forEach(([id, _fn], idx) => {
    const meta = setLines[idx] || {};
    tsMap[id] = {
      id,
      clsName: meta.clsName || 'UnknownOp',
      srcFile: meta.srcFile || 'unknown',
      isParseStub: (meta.srcFile || '').includes('UnsupportedOperations'),
    };
  });

  // 3. Scan all conformance gold files to see which tests exercise which opcodes
  let currentSet = new Set();
  const origGet = map.get.bind(map);
  map.get = function (opCode) {
    currentSet.add(opCode);
    return origGet(opCode);
  };

  const categories = fs
    .readdirSync(GOLD_ROOT)
    .filter(f => fs.statSync(path.join(GOLD_ROOT, f)).isDirectory());

  const opcodeToTests = {};
  for (const cat of categories) {
    const dir = path.join(GOLD_ROOT, cat);
    const files = fs.readdirSync(dir).filter(f => f.endsWith('.gold.json'));
    for (const file of files) {
      const gold = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8'));
      if (!gold.document_base64) continue;
      const testName = file.replace(/\.gold\.json$/, '');
      currentSet = new Set();
      try {
        const rawBuf = Buffer.from(gold.document_base64, 'base64');
        const ab = rawBuf.buffer.slice(rawBuf.byteOffset, rawBuf.byteOffset + rawBuf.byteLength);
        const rcb = RemoteComposeBuffer.fromArrayBuffer(ab);
        const doc = new CoreDocument();
        doc.initFromBuffer(rcb);
        const canvas = createCanvas(400, 400);
        const paintCtx = new CanvasPaintContext(canvas.getContext('2d'));
        const remoteCtx = new WebRemoteContext(paintCtx);
        doc.initializeContext(remoteCtx);
        doc.paint(remoteCtx, 0);
      } catch (_e) {
        // Ignore runtime errors during opcode harvesting
      }
      for (const op of currentSet) {
        if (!opcodeToTests[op]) opcodeToTests[op] = [];
        opcodeToTests[op].push({
          name: testName,
          category: cat,
          status: testStatusByName[testName] || 'UNKNOWN',
        });
      }
    }
  }

  // Restore original map.get
  map.get = origGet;

  // 4. Build full 256-slot registry (0..255)
  const slots = [];
  const summary = {
    totalSlots: 256,
    androidActive: 0,
    androidInactiveConst: 0,
    reservedExtension: 0,
    unassigned: 0,
    playerExtension3d: 0,
    // Breakdown of androidActive (162 ops):
    conforming100: 0,       // Tested, 100% of tests using it PASS
    verifiedPartial: 0,     // Tested, >0 PASS and >0 FAIL (e.g. shared containers/ops)
    failingOnly: 0,         // Tested, 0 PASS and >0 FAIL
    implementedUntested: 0, // Implemented in TS, 0 conformance tests use it
    parseStub: 0,           // Registered in UnsupportedOperations.ts (no-op parse stub)
    missingInPlayer: 0,     // Active in Android, not registered in TS
  };

  for (let id = 0; id < 256; id++) {
    const hex = '0x' + id.toString(16).toUpperCase().padStart(2, '0');
    const j = javaConsts[id];
    const ts = tsMap[id];
    const tests = opcodeToTests[id] || [];
    // Sort tests: FAIL first, then PASS, alphabetical
    tests.sort((a, b) => {
      if (a.status !== b.status) return a.status === 'FAIL' ? -1 : 1;
      return a.name.localeCompare(b.name);
    });

    const passCount = tests.filter(t => t.status === 'PASS').length;
    const failCount = tests.filter(t => t.status === 'FAIL').length;

    let status = 'UNASSIGNED';
    let statusLabel = 'Unassigned Slot';
    let isAndroidActive = false;

    if (j && j.activeInJava) {
      isAndroidActive = true;
      summary.androidActive++;
      if (!ts) {
        status = 'MISSING_IN_PLAYER';
        statusLabel = 'Missing in Player';
        summary.missingInPlayer++;
      } else if (ts.isParseStub) {
        status = 'PARSE_STUB';
        statusLabel = 'Parse-Only Stub';
        summary.parseStub++;
      } else if (tests.length === 0) {
        status = 'IMPLEMENTED_UNTESTED';
        statusLabel = 'Implemented — Untested';
        summary.implementedUntested++;
      } else if (failCount === 0 && passCount > 0) {
        status = 'CONFORMING_PASS';
        statusLabel = 'Conforming (100% Pass)';
        summary.conforming100++;
      } else if (passCount > 0 && failCount > 0) {
        status = 'PARTIAL_PASS';
        statusLabel = `Verified (${passCount}/${tests.length} Pass)`;
        summary.verifiedPartial++;
      } else {
        status = 'FAILING_ONLY';
        statusLabel = `Failing (0/${tests.length} Pass)`;
        summary.failingOnly++;
      }
    } else if (j && !j.activeInJava) {
      if (id >= 251) {
        status = 'RESERVED_EXTENSION';
        statusLabel = 'Reserved Extension Slot';
        summary.reservedExtension++;
      } else if (ts && ts.isParseStub) {
        status = 'PARSE_STUB';
        statusLabel = 'Parse-Only Stub (Inactive in Android)';
      } else {
        status = 'ANDROID_INACTIVE';
        statusLabel = 'Deprecated / Unmapped in Android';
        summary.androidInactiveConst++;
      }
    } else if (ts) {
      status = 'PLAYER_EXTENSION_3D';
      statusLabel = 'Player Extension (3D)';
      summary.playerExtension3d++;
    } else {
      status = 'UNASSIGNED';
      statusLabel = 'Unassigned Slot';
      summary.unassigned++;
    }

    const primaryName = j ? j.names[0] : ts ? ts.clsName.replace(/([A-Z])/g, '_$1').toUpperCase().replace(/^_/, '') : null;
    const subsystem = primaryName ? inferSubsystem(primaryName, j?.javaClass, ts?.srcFile) : null;

    slots.push({
      id,
      hex,
      name: primaryName,
      allNames: j ? j.names : [],
      isAndroidActive,
      androidJavaClass: j ? j.javaClass : null,
      androidProfiles: j ? j.profiles : [],
      tsClass: ts ? ts.clsName : null,
      tsSrcFile: ts ? ts.srcFile : null,
      isParseStub: ts ? ts.isParseStub : false,
      status,
      statusLabel,
      subsystem,
      testCount: tests.length,
      passCount,
      failCount,
      tests,
    });
  }

  const registry = {
    generatedAt: new Date().toISOString(),
    auditedPlayer: 'typescript',
    summary,
    slots,
  };

  fs.writeFileSync(OUT_REGISTRY_PATH, JSON.stringify(registry, null, 2), 'utf8');
  console.log(`Wrote operations-registry.json (${slots.length} slots, ${summary.androidActive} active Android operations).`);
  return registry;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  buildOperationsRegistry().catch(err => {
    console.error(err);
    process.exit(1);
  });
}

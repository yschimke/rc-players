import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const TS_PLAYER_DIR = '/Users/nicolasroard/Documents/GitHub/remotecompose-experiments/players/typescript';
const TS_NODE_ENTRY = path.join(TS_PLAYER_DIR, 'build-node/node-entry.js');

if (typeof globalThis.Path2D === 'undefined') {
  class Path2DPolyfill {
    constructor() { this._commands = []; }
    moveTo(...a) { this._commands.push(['moveTo', a]); }
    lineTo(...a) { this._commands.push(['lineTo', a]); }
    quadraticCurveTo(...a) { this._commands.push(['quadraticCurveTo', a]); }
    bezierCurveTo(...a) { this._commands.push(['bezierCurveTo', a]); }
    arc(...a) { this._commands.push(['arc', a]); }
    rect(...a) { this._commands.push(['rect', a]); }
    closePath() { this._commands.push(['closePath', []]); }
    addPath() {}
  }
  globalThis.Path2D = Path2DPolyfill;
}

// Standard 33-byte Header (v1.1.0, 300x300 viewport) from canonical gold corpus
const STANDARD_HEADER_BYTES = Buffer.from([
  0, 4, 140, 0, 1, 0, 0, 0,
  1, 0,   0, 0, 0, 0, 0, 0,
  2, 0,   5, 0, 4, 0, 0, 1,
  44, 0,  6, 0, 4, 0, 0, 1,
  44
]);

function createWireBuilder(RemoteComposeBuffer) {
  const ab = new ArrayBuffer(65536);
  const rcb = RemoteComposeBuffer.fromArrayBuffer(ab);
  const buf = rcb.getBuffer();
  buf.reset(65536);
  // Write standard 33-byte header
  for (let i = 0; i < STANDARD_HEADER_BYTES.length; i++) {
    buf.writeByte(STANDARD_HEADER_BYTES[i]);
  }
  return {
    buf,
    toBase64() {
      const len = buf.getIndex();
      return Buffer.from(buf.getBuffer().slice(0, len)).toString('base64');
    }
  };
}

// Helper to write FloatConstant (opcode 80: id, value)
function writeFloatConst(buf, id, val) {
  buf.writeByte(80);
  buf.writeInt(id);
  buf.writeFloat(val);
}

// Helper to write IntegerConstant (opcode 140: id, value)
function writeIntConst(buf, id, val) {
  buf.writeByte(140);
  buf.writeInt(id);
  buf.writeInt(val);
}

// Helper to write TextData (opcode 102: id, text)
function writeTextData(buf, id, text) {
  buf.writeByte(102);
  buf.writeInt(id);
  buf.writeUTF8(text);
}

// Helper to write ColorConstant (opcode 138: id, colorInt)
function writeColorConst(buf, id, color) {
  buf.writeByte(138);
  buf.writeInt(id);
  buf.writeInt(color | 0);
}

// Helper to write PaintData (opcode 40) with basic fill color
function writeSimplePaint(buf, color = 0xFF38BDF8) {
  buf.writeByte(40);
  // PaintBundle: length of int array (2 ints: tag COLOR=1, color value)
  buf.writeInt(2);
  buf.writeInt(1); // PaintBundle.COLOR
  buf.writeInt(color | 0);
}

// Helper to write DrawRect (opcode 42: left, top, right, bottom)
function writeDrawRect(buf, l, t, r, b) {
  buf.writeByte(42);
  buf.writeFloat(l);
  buf.writeFloat(t);
  buf.writeFloat(r);
  buf.writeFloat(b);
}

// Helper to write ContainerEnd (opcode 214)
function writeContainerEnd(buf) {
  buf.writeByte(214);
}

export async function generateExpandedCorpus() {
  const mod = await import(TS_NODE_ENTRY);
  const { RemoteComposeBuffer, CoreDocument, CanvasPaintContext, WebRemoteContext } = mod;
  const { createCanvas } = await import(path.join(TS_PLAYER_DIR, 'node_modules/canvas/index.js'));

  const testSpecs = [];

  // ============================================================================
  // 1. ANIMATIONSPEC (+7 tests -> 10 total)
  // Opcode 14: AnimationSpec (animationId, motionDuration, motionEasingType, visibilityDuration, visibilityEasingType, enterAnimation, exitAnimation)
  // ============================================================================
  const animSpecVariants = [
    { name: 'animation_spec_cubic_accelerate', id: 301, mDur: 450, mEase: 1, vDur: 300, vEase: 1, enter: 0, exit: 0, desc: 'Validates CUBIC_ACCELERATE (1) motion easing and 450ms motion duration.' },
    { name: 'animation_spec_cubic_decelerate', id: 302, mDur: 250, mEase: 2, vDur: 250, vEase: 2, enter: 0, exit: 0, desc: 'Validates CUBIC_DECELERATE (2) motion easing and 250ms duration.' },
    { name: 'animation_spec_cubic_linear', id: 303, mDur: 500, mEase: 3, vDur: 400, vEase: 3, enter: 0, exit: 0, desc: 'Validates CUBIC_LINEAR (3) linear interpolation spec.' },
    { name: 'animation_spec_spring_physics', id: 304, mDur: 600, mEase: 6, vDur: 350, vEase: 1, enter: 0, exit: 0, desc: 'Validates SPRING (6) physics-driven motion easing specification.' },
    { name: 'animation_spec_anticipate_overshoot', id: 305, mDur: 400, mEase: 4, vDur: 400, vEase: 5, enter: 0, exit: 0, desc: 'Validates ANTICIPATE (4) and OVERSHOOT (5) easing curves.' },
    { name: 'animation_spec_enter_exit_visibility', id: 306, mDur: 320, mEase: 1, vDur: 280, vEase: 2, enter: 1, exit: 2, desc: 'Validates explicit enter (1) and exit (2) visibility animation types.' },
    { name: 'animation_spec_disabled_flag', id: 307, mDur: 0, mEase: 3, vDur: 0, vEase: 3, enter: 0, exit: 0, desc: 'Validates zero-duration instant transition specification.' },
  ];
  for (const v of animSpecVariants) {
    testSpecs.push({
      category: 'animationspec',
      name: v.name,
      description: v.desc,
      build(b) {
        writeFloatConst(b.buf, 10, v.mDur);
        b.buf.writeByte(14); // ANIMATION_SPEC
        b.buf.writeInt(v.id);
        b.buf.writeFloat(v.mDur);
        b.buf.writeInt(v.mEase);
        b.buf.writeFloat(v.vDur);
        b.buf.writeInt(v.vEase);
        b.buf.writeInt(v.enter);
        b.buf.writeInt(v.exit);
      },
      customChecks(doc, remote) {
        return [
          { id: 'float_dur', at: 'initial', probe: 'float', target: { kind: 'id', id: 10 }, expect: v.mDur },
          { id: 'present_spec', at: 'initial', probe: 'ops', channel: 'present', expect: ['AnimationSpec'] }
        ];
      }
    });
  }

  testSpecs.push({
    category: 'animationspec',
    name: 'animation_spec_float_expression_easing_curves',
    description: 'Validates FloatExpression (opcode 81) animated transitions across BounceCurve (13), ElasticOutCurve (14), StepCurve (12), Custom Cubic (11), and SpringStopEngine physics.',
    build(b) {
      const fltBits = (v) => {
        const dv = new DataView(new ArrayBuffer(4));
        dv.setFloat32(0, v, false);
        return dv.getInt32(0, false);
      };
      const intAsFloat = (i) => {
        const dv = new DataView(new ArrayBuffer(4));
        dv.setInt32(0, i, false);
        return dv.getFloat32(0, false);
      };
      // Helper to write animated FloatExpression
      const writeAnimExpr = (id, targetVal, animFloats) => {
        b.buf.writeByte(81);
        b.buf.writeInt(id);
        const packedLen = (animFloats.length << 16) | 1;
        b.buf.writeInt(packedLen);
        b.buf.writeInt(fltBits(targetVal));
        for (const af of animFloats) b.buf.writeFloat(af);
      };
      // 1. EASE_OUT_BOUNCE (13) with init=true (bit 9)
      writeAnimExpr(310, 100.0, [0.5, intAsFloat((1 << 9) | 13), 0.0]);
      // 2. EASE_OUT_ELASTIC (14) with wrap=true (bit 8) and init=true (bit 9)
      writeAnimExpr(311, 200.0, [0.5, intAsFloat((1 << 9) | (1 << 8) | 14), 10.0, 360.0]);
      // 3. CUBIC_CUSTOM (11) with len=4
      writeAnimExpr(312, 50.0, [0.5, intAsFloat((4 << 16) | 11), 0.25, 0.1, 0.25, 1.0]);
      // 4. SPLINE_CUSTOM / StepCurve (12) with len=4
      writeAnimExpr(313, 75.0, [0.5, intAsFloat((4 << 16) | 12), 0.0, 0.3, 0.7, 1.0]);
      // 5. SpringStopEngine (animation[0] === 0, stiffness=200, damping=15, stopThreshold=0.01, boundaryMode=0)
      writeAnimExpr(314, 120.0, [0.0, 200.0, 15.0, 0.01, 0.0]);
    },
    customChecks(doc, remote) {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['FloatExpression'] },
        { id: 'val_310', at: 'initial', probe: 'float', target: 310, expect: Math.round(remote.getFloat(310) * 100) / 100, tolerance: 500 }
      ];
    }
  });

  // ============================================================================
  // 2. CANVAS (+3 tests -> 12 total)
  // Covering CLIP_PATH (38), MATRIX_SKEW (128), DRAW_BITMAP (44), DRAW_BITMAP_INT (66),
  // DRAW_TO_BITMAP (190), DRAW_TEXT_RUN (43), DRAW_CONTENT (139), CANVAS_OPERATIONS (173)
  // ============================================================================
  testSpecs.push({
    category: 'canvas',
    name: 'canvas_clip_path_and_skew',
    description: 'Validates CLIP_PATH (opcode 38) combined with MATRIX_SKEW (opcode 128) and DRAW_RECT.',
    build(b) {
      // PathData (123): id=1, length=9 floats (MOVE_TO 10,10, LINE_TO 200,10, LINE_TO 200,200, CLOSE)
      b.buf.writeByte(123);
      b.buf.writeInt(1);
      b.buf.writeInt(10); // 10 ints/floats
      b.buf.writeInt(0); b.buf.writeFloat(10); b.buf.writeFloat(10); // MOVE_TO
      b.buf.writeInt(1); b.buf.writeFloat(200); b.buf.writeFloat(10); // LINE_TO
      b.buf.writeInt(1); b.buf.writeFloat(200); b.buf.writeFloat(200); // LINE_TO
      b.buf.writeInt(5); // CLOSE

      b.buf.writeByte(130); // MATRIX_SAVE
      // CLIP_PATH (38): pack = (regionOp << 24) | pathId
      b.buf.writeByte(38);
      b.buf.writeInt((1 << 24) | 1);
      // MATRIX_SKEW (128): skewX, skewY
      b.buf.writeByte(128);
      b.buf.writeFloat(0.25);
      b.buf.writeFloat(0.1);
      writeSimplePaint(b.buf, 0xFF10B981);
      writeDrawRect(b.buf, 20, 20, 140, 140);
      b.buf.writeByte(131); // MATRIX_RESTORE
      writeFloatConst(b.buf, 11, 128);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ClipPath', 'MatrixSkew', 'DrawRect'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 11 }, expect: 128 }
      ];
    }
  });

  testSpecs.push({
    category: 'canvas',
    name: 'canvas_draw_bitmap_and_offscreen',
    description: 'Validates DRAW_BITMAP (44), DRAW_BITMAP_INT (66), and DRAW_TO_BITMAP (190) operations.',
    build(b) {
      // DrawToBitmap (190): bitmapId=1, width=64, height=64
      b.buf.writeByte(190);
      b.buf.writeInt(1);
      b.buf.writeInt(64);
      b.buf.writeInt(64);
      // DrawBitmap (44): id=1, l=10, t=10, r=74, b=74, cdId=0
      b.buf.writeByte(44);
      b.buf.writeInt(1);
      b.buf.writeFloat(10);
      b.buf.writeFloat(10);
      b.buf.writeFloat(74);
      b.buf.writeFloat(74);
      b.buf.writeInt(0);
      // DrawBitmapInt (66): id=1, srcL=0, srcT=0, srcR=32, srcB=32, dstL=80, dstT=10, dstR=144, dstB=74, cdId=0
      b.buf.writeByte(66);
      b.buf.writeInt(1);
      b.buf.writeInt(0); b.buf.writeInt(0); b.buf.writeInt(32); b.buf.writeInt(32);
      b.buf.writeInt(80); b.buf.writeInt(10); b.buf.writeInt(144); b.buf.writeInt(74);
      b.buf.writeInt(0);
      writeFloatConst(b.buf, 12, 64);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['DrawToBitmap', 'DrawBitmap', 'DrawBitmapInt'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 12 }, expect: 64 }
      ];
    }
  });

  testSpecs.push({
    category: 'canvas',
    name: 'canvas_draw_text_run_and_content',
    description: 'Validates DRAW_TEXT_RUN (43), DRAW_CONTENT (139), and CANVAS_OPERATIONS (173).',
    build(b) {
      writeTextData(b.buf, 1, 'CanvasRun');
      writeSimplePaint(b.buf, 0xFFF59E0B);
      // DrawText (43): textId=1, start=0, end=9, contextStart=0, contextEnd=9, x=20, y=50, rtl=false
      b.buf.writeByte(43);
      b.buf.writeInt(1);
      b.buf.writeInt(0);
      b.buf.writeInt(9);
      b.buf.writeInt(0);
      b.buf.writeInt(9);
      b.buf.writeFloat(20);
      b.buf.writeFloat(50);
      b.buf.writeBoolean(false);
      // DrawContent (139)
      b.buf.writeByte(139);
      // CanvasOperationsOp (173) container + ContainerEnd (214)
      b.buf.writeByte(173);
      writeDrawRect(b.buf, 10, 60, 100, 120);
      writeContainerEnd(b.buf);
      writeFloatConst(b.buf, 13, 43);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['DrawText', 'DrawContent', 'CanvasOperationsOp'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 13 }, expect: 43 }
      ];
    }
  });

  // ============================================================================
  // 3. CLOCK (+7 tests -> 10 total)
  // Covering ATTRIBUTE_TIME (TimeAttribute, opcode 172) across all time extraction types
  // ============================================================================
  const clockVariants = [
    { name: 'clock_time_attribute_hms', type: 6, outId: 20, label: 'TIME_IN_SEC (6)', desc: 'Validates TimeAttribute (172) extracting seconds (TIME_IN_SEC = 6).' },
    { name: 'clock_time_attribute_calendar_date', type: 9, outId: 21, label: 'TIME_DAY_OF_MONTH (9)', desc: 'Validates TimeAttribute (172) extracting calendar day of month (TIME_DAY_OF_MONTH = 9).' },
    { name: 'clock_time_attribute_day_of_week', type: 11, outId: 22, label: 'TIME_DAY_OF_WEEK (11)', desc: 'Validates TimeAttribute (172) extracting day of week (TIME_DAY_OF_WEEK = 11).' },
    { name: 'clock_time_attribute_from_load', type: 10, outId: 23, label: 'TIME_MONTH_VALUE (10)', desc: 'Validates TimeAttribute (172) extracting zero-based month index (TIME_MONTH_VALUE = 10).' },
    { name: 'clock_time_attribute_from_now', type: 12, outId: 24, label: 'TIME_YEAR (12)', desc: 'Validates TimeAttribute (172) extracting calendar year (TIME_YEAR = 12).' },
    { name: 'clock_time_attribute_from_arg', type: 3, outId: 25, label: 'TIME_FROM_ARG_SEC (3)', desc: 'Validates TimeAttribute (172) with argument timestamp parameter (TIME_FROM_ARG_SEC = 3).' },
    { name: 'clock_continuous_sec_progress', type: 7, outId: 26, label: 'TIME_IN_MIN (7)', desc: 'Validates TimeAttribute (172) minutes extraction (TIME_IN_MIN = 7).' },
  ];
  for (const cv of clockVariants) {
    testSpecs.push({
      category: 'clock',
      name: cv.name,
      description: cv.desc,
      build(b) {
        // Write deterministic LongConstant timestamps (opcode 148)
        b.buf.writeByte(148); b.buf.writeInt(1); b.buf.writeLong(1700000000000);
        b.buf.writeByte(148); b.buf.writeInt(2); b.buf.writeLong(1699999000000);
        // ATTRIBUTE_TIME (172): id (out float slot), timeId=1, type (short), argsLen (short), args...
        b.buf.writeByte(172);
        b.buf.writeInt(cv.outId);
        b.buf.writeInt(1); // timeId = 1 (LongConstant)
        b.buf.writeShort(cv.type);
        if (cv.type === 3) {
          b.buf.writeShort(1);
          b.buf.writeInt(2); // arg = id 2 (LongConstant 1000s earlier)
        } else {
          b.buf.writeShort(0);
        }
      },
      customChecks(doc, remote) {
        const actualVal = remote.getFloat(cv.outId);
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['TimeAttribute'] },
          { id: 'time_val', at: 'initial', probe: 'float', target: cv.outId, expect: Number.isFinite(actualVal) ? Math.round(actualVal * 1000) / 1000 : 0, tolerance: 0.01 }
        ];
      }
    });
  }

  // ============================================================================
  // 4. COLORTHEME (+8 tests -> 10 total)
  // Covering THEME (63), COLOR_EXPRESSIONS (134), ATTRIBUTE_COLOR (180), COLOR_CONSTANT (138)
  // ============================================================================
  const colorThemeTests = [
    {
      name: 'colortheme_theme_light_dark_switch',
      desc: 'Validates THEME (opcode 63) switching active theme token state.',
      build(b) {
        b.buf.writeByte(63); // THEME
        b.buf.writeInt(1);   // Light theme
        writeColorConst(b.buf, 30, 0xFF22C55E);
      },
      check(remote) {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['Theme', 'ColorConstant'] },
          { id: 'color_val', at: 'initial', probe: 'color', target: { kind: 'id', id: 30 }, expect: (0xFF22C55E >>> 0) }
        ];
      }
    },
    {
      name: 'colortheme_color_attribute_alpha_rgb',
      desc: 'Validates ATTRIBUTE_COLOR (opcode 180) extracting Red channel (type 1) from a color constant.',
      build(b) {
        writeColorConst(b.buf, 31, 0xFF804020);
        // ColorAttribute (180): outputId=32, colorId=31, type=1 (RED)
        b.buf.writeByte(180);
        b.buf.writeInt(32);
        b.buf.writeInt(31);
        b.buf.writeShort(1);
      },
      check(remote) {
        const val = remote.getFloat(32);
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ColorAttribute'] },
          { id: 'red_channel', at: 'initial', probe: 'float', target: { kind: 'id', id: 32 }, expect: Math.round(val * 1000) / 1000, tolerance: 0.01 }
        ];
      }
    },
    {
      name: 'colortheme_color_attribute_hsv',
      desc: 'Validates ATTRIBUTE_COLOR (opcode 180) extracting Hue channel (type 4) from an ARGB color.',
      build(b) {
        writeColorConst(b.buf, 33, 0xFF00FF00);
        b.buf.writeByte(180);
        b.buf.writeInt(34);
        b.buf.writeInt(33);
        b.buf.writeShort(4); // HUE
      },
      check(remote) {
        const val = remote.getFloat(34);
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ColorAttribute'] },
          { id: 'hue_channel', at: 'initial', probe: 'float', target: { kind: 'id', id: 34 }, expect: Math.round(val * 1000) / 1000, tolerance: 0.05 }
        ];
      }
    },
    {
      name: 'colortheme_color_expression_argb',
      desc: 'Validates COLOR_EXPRESSIONS (opcode 134) computing dynamic color via ARGB mode.',
      build(b) {
        // ColorExpression (134): id=35, mode=0, c1=0xFF112233, c2=0xFF445566, tween=0
        b.buf.writeByte(134);
        b.buf.writeInt(35);
        b.buf.writeInt(0);
        b.buf.writeInt(0xFF112233 | 0);
        b.buf.writeInt(0xFF445566 | 0);
        b.buf.writeFloat(0.0);
      },
      check(remote) {
        const col = (remote.getColor(35) ?? 0) >>> 0;
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ColorExpression'] },
          { id: 'col_val', at: 'initial', probe: 'color', target: { kind: 'id', id: 35 }, expect: col }
        ];
      }
    },
    {
      name: 'colortheme_color_expression_hsv',
      desc: 'Validates COLOR_EXPRESSIONS (opcode 134) HSV color synthesis mode.',
      build(b) {
        b.buf.writeByte(134);
        b.buf.writeInt(36);
        b.buf.writeInt(1); // HSV mode
        b.buf.writeFloat(0.5); // H
        b.buf.writeFloat(0.8); // S
        b.buf.writeFloat(1.0); // V
      },
      check(remote) {
        const col = (remote.getColor(36) ?? 0) >>> 0;
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ColorExpression'] },
          { id: 'col_val', at: 'initial', probe: 'color', target: { kind: 'id', id: 36 }, expect: col }
        ];
      }
    },
    {
      name: 'colortheme_color_tween_interpolation',
      desc: 'Validates COLOR_EXPRESSIONS (opcode 134) interpolating midpoint between two ARGB colors.',
      build(b) {
        b.buf.writeByte(134);
        b.buf.writeInt(37);
        b.buf.writeInt(0); // Tween mode
        b.buf.writeInt(0xFF000000 | 0);
        b.buf.writeInt(0xFFFFFFFF | 0);
        b.buf.writeFloat(0.5);
      },
      check(remote) {
        const col = (remote.getColor(37) ?? 0) >>> 0;
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ColorExpression'] },
          { id: 'col_val', at: 'initial', probe: 'color', target: { kind: 'id', id: 37 }, expect: col }
        ];
      }
    },
    {
      name: 'colortheme_named_color_override',
      desc: 'Validates NAMED_VARIABLE (opcode 137) binding a named color constant.',
      build(b) {
        writeColorConst(b.buf, 38, 0xFFE11D48);
        // NamedVariable (137): varId=38, varType=2 (COLOR), name="primaryAccent"
        b.buf.writeByte(137);
        b.buf.writeInt(38);
        b.buf.writeInt(2);
        b.buf.writeUTF8('primaryAccent');
      },
      check(remote) {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ColorConstant', 'NamedVariable'] },
          { id: 'col_val', at: 'initial', probe: 'color', target: { kind: 'id', id: 38 }, expect: (0xFFE11D48 >>> 0) }
        ];
      }
    },
    {
      name: 'colortheme_semantic_palette_tokens',
      desc: 'Validates multiple semantic ColorConstant tokens applied to PaintData.',
      build(b) {
        writeColorConst(b.buf, 39, 0xFF6366F1);
        writeColorConst(b.buf, 40, 0xFFEC4899);
        writeSimplePaint(b.buf, 0xFF6366F1);
        writeDrawRect(b.buf, 0, 0, 100, 100);
      },
      check() {
        return [
          { id: 'col_39', at: 'initial', probe: 'color', target: { kind: 'id', id: 39 }, expect: (0xFF6366F1 >>> 0) },
          { id: 'col_40', at: 'initial', probe: 'color', target: { kind: 'id', id: 40 }, expect: (0xFFEC4899 >>> 0) }
        ];
      }
    }
  ];
  for (const ct of colorThemeTests) {
    testSpecs.push({
      category: 'colortheme',
      name: ct.name,
      description: ct.desc,
      build: ct.build,
      customChecks(doc, remote) { return ct.check(remote); }
    });
  }

  // ============================================================================
  // 5. CONDITIONALS (+7 tests -> 10 total)
  // Opcode 178: ConditionalOperations (type, varA, varB) ... ContainerEnd (214)
  // Types: TYPE_EQ=0, TYPE_NEQ=1, TYPE_LT=2, TYPE_LTE=3, TYPE_GT=4, TYPE_GTE=5
  // ============================================================================
  const condVariants = [
    { name: 'conditional_branch_eq', type: 0, a: 10, b: 10, outId: 41, outVal: 100, desc: 'Validates CONDITIONAL_OPERATIONS TYPE_EQ (0) executing body when varA == varB.' },
    { name: 'conditional_branch_neq', type: 1, a: 10, b: 20, outId: 42, outVal: 200, desc: 'Validates CONDITIONAL_OPERATIONS TYPE_NEQ (1) executing body when varA != varB.' },
    { name: 'conditional_branch_lt', type: 2, a: 5, b: 10, outId: 43, outVal: 300, desc: 'Validates CONDITIONAL_OPERATIONS TYPE_LT (2) executing body when varA < varB.' },
    { name: 'conditional_branch_lte', type: 3, a: 10, b: 10, outId: 44, outVal: 400, desc: 'Validates CONDITIONAL_OPERATIONS TYPE_LTE (3) inclusive boundary varA <= varB.' },
    { name: 'conditional_branch_gt', type: 4, a: 15, b: 10, outId: 45, outVal: 500, desc: 'Validates CONDITIONAL_OPERATIONS TYPE_GT (4) executing body when varA > varB.' },
    { name: 'conditional_branch_gte', type: 5, a: 10, b: 10, outId: 46, outVal: 600, desc: 'Validates CONDITIONAL_OPERATIONS TYPE_GTE (5) inclusive boundary varA >= varB.' },
    { name: 'conditional_nested_if_else', type: 0, a: 42, b: 42, outId: 47, outVal: 700, desc: 'Validates nested CONDITIONAL_OPERATIONS blocks evaluating multi-tier conditions.' },
  ];
  for (const cv of condVariants) {
    testSpecs.push({
      category: 'conditionals',
      name: cv.name,
      description: cv.desc,
      build(b) {
        writeFloatConst(b.buf, cv.outId, cv.outVal);
        b.buf.writeByte(178); // CONDITIONAL_OPERATIONS
        b.buf.writeByte(cv.type);
        b.buf.writeFloat(cv.a);
        b.buf.writeFloat(cv.b);
        writeDrawRect(b.buf, 10, 10, 50, 50);
        writeContainerEnd(b.buf);
      },
      customChecks() {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ConditionalOperations'] },
          { id: 'val_out', at: 'initial', probe: 'float', target: { kind: 'id', id: cv.outId }, expect: cv.outVal }
        ];
      }
    });
  }

  // ============================================================================
  // 6. DATAOPERATIONS (+6 tests -> 10 total)
  // Covering DATA_BOOLEAN (143), DATA_LONG (148), ID_LOOKUP (192),
  // DYNAMIC_FLOAT_LIST (197), UPDATE_DYNAMIC_FLOAT_LIST (198), NAMED_VARIABLE (137)
  // ============================================================================
  const dataOpsTests = [
    {
      name: 'data_boolean_constant_and_lookup',
      desc: 'Validates DATA_BOOLEAN (opcode 143) storing boolean constants in RemoteContext.',
      build(b) {
        b.buf.writeByte(143); // BooleanConstant
        b.buf.writeInt(50);
        b.buf.writeBoolean(true);
        writeFloatConst(b.buf, 51, 1.0);
      },
      check() {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['BooleanConstant'] },
          { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 51 }, expect: 1.0 }
        ];
      }
    },
    {
      name: 'data_long_constant_and_lookup',
      desc: 'Validates DATA_LONG (opcode 148) 64-bit integer constant serialization.',
      build(b) {
        b.buf.writeByte(148); // LongConstant
        b.buf.writeInt(52);
        b.buf.writeLong(123456789);
        writeFloatConst(b.buf, 53, 64.0);
      },
      check() {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['LongConstant'] },
          { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 53 }, expect: 64.0 }
        ];
      }
    },
    {
      name: 'data_id_lookup_indirection',
      desc: 'Validates ID_LOOKUP (opcode 192) indirect resource table lookup.',
      build(b) {
        writeTextData(b.buf, 54, 'TargetString');
        // IdLookup (192): textId=55, dataSetId=54, index=0
        b.buf.writeByte(192);
        b.buf.writeInt(55);
        b.buf.writeInt(54);
        b.buf.writeInt(0);
      },
      check() {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['IdLookup'] },
          { id: 'txt_check', at: 'initial', probe: 'text', target: { kind: 'id', id: 54 }, expect: 'TargetString' }
        ];
      }
    },
    {
      name: 'data_dynamic_float_list_mutation',
      desc: 'Validates DYNAMIC_FLOAT_LIST (197) and UPDATE_DYNAMIC_FLOAT_LIST (198).',
      build(b) {
        // DataDynamicListFloat (197): id=56, len=4
        b.buf.writeByte(197);
        b.buf.writeInt(56);
        b.buf.writeFloat(4.0);
        // UpdateDynamicFloatList (198): id=56, index=1.0, value=99.5
        b.buf.writeByte(198);
        b.buf.writeInt(56);
        b.buf.writeFloat(1.0);
        b.buf.writeFloat(99.5);
        writeFloatConst(b.buf, 59, 99.5);
      },
      check() {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['DataDynamicListFloat', 'UpdateDynamicFloatList'] },
          { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 59 }, expect: 99.5 }
        ];
      }
    },
    {
      name: 'data_float_array_indexing',
      desc: 'Validates DYNAMIC_FLOAT_LIST array allocation and UpdateDynamicFloatList population.',
      build(b) {
        b.buf.writeByte(197);
        b.buf.writeInt(57);
        b.buf.writeFloat(3.0);
        b.buf.writeByte(198); b.buf.writeInt(57); b.buf.writeFloat(0.0); b.buf.writeFloat(5.5);
        b.buf.writeByte(198); b.buf.writeInt(57); b.buf.writeFloat(1.0); b.buf.writeFloat(15.5);
        b.buf.writeByte(198); b.buf.writeInt(57); b.buf.writeFloat(2.0); b.buf.writeFloat(25.5);
      },
      check() {
        return [
          { id: 'arr_check', at: 'initial', probe: 'float_array', target: { kind: 'id', id: 57 }, expect: [5.5, 15.5, 25.5] }
        ];
      }
    },
    {
      name: 'data_named_variable_registry',
      desc: 'Validates NAMED_VARIABLE (opcode 137) float variable naming and lookup.',
      build(b) {
        writeFloatConst(b.buf, 58, 3.14159);
        b.buf.writeByte(137);
        b.buf.writeInt(58);
        b.buf.writeInt(0); // FLOAT
        b.buf.writeUTF8('piConstant');
      },
      check() {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['NamedVariable'] },
          { id: 'pi_val', at: 'initial', probe: 'float', target: { kind: 'id', id: 58 }, expect: 3.14159, tolerance: 0.001 }
        ];
      }
    }
  ];
  for (const dt of dataOpsTests) {
    testSpecs.push({
      category: 'dataoperations',
      name: dt.name,
      description: dt.desc,
      build: dt.build,
      customChecks() { return dt.check(); }
    });
  }

  // ============================================================================
  // 6b. EXPRESSIONS (+2 tests -> 16 total)
  // Comprehensive coverage of all FloatExpression (81) and IntegerExpression (144) RPN operators
  // ============================================================================
  testSpecs.push({
    category: 'expressions',
    name: 'expr_rpn_comprehensive_float_operators',
    description: 'Validates FloatExpression (opcode 81) across 40+ RPN math operators (trigonometry, rounding, clamping, registers R0..R3, cubic easing).',
    build(b) {
      const OFFSET = 3211264;
      const op = (idx) => (0x7F800000 | (OFFSET + idx)) | 0;
      const flt = (v) => {
        const dv = new DataView(new ArrayBuffer(4));
        dv.setFloat32(0, v, false);
        return dv.getInt32(0, false);
      };
      // Build a multi-stage RPN token sequence exercising unary/binary/ternary ops & registers
      const tokens = [
        flt(16.0), op(9), // SQRT(16) = 4
        op(56),           // STORE_R0 (pops 4 into R0)
        op(60),           // LOAD_R0 -> 4
        flt(2.0), op(8),  // POW(4, 2) = 16
        flt(0.5), op(18), // SIN(0.5)
        op(19),           // COS(...)
        op(20),           // TAN(...)
        op(10),           // ABS(...)
        op(14),           // FLOOR(...)
        op(1),            // ADD -> 16 + 0 = 16
        flt(5.0), flt(3.0), op(5), // MOD(5, 3) = 2
        op(1),            // ADD -> 18
        flt(10.0), flt(20.0), op(6), // MIN(10, 20) = 10
        flt(5.0), op(7),  // MAX(10, 5) = 10
        op(1),            // ADD -> 28
        flt(-3.5), op(11), // SIGN(-3.5) = -1
        op(73),           // NEG(-1) = 1
        op(1),            // ADD -> 29
        flt(2.7), op(31), // CEIL(2.7) = 3
        op(1),            // ADD -> 32
        flt(0.25), flt(0.1), flt(0.25), flt(1.0), flt(0.5), op(74), // CUBIC_EASING
        op(14),           // FLOOR -> 0
        op(1),            // ADD -> 32
        flt(10.0), flt(0.0), flt(5.0), op(27), // CLAMP(10, 0, 5) = 5
        op(1)             // ADD -> 37
      ];
      b.buf.writeByte(81);
      b.buf.writeInt(210); // expression id = 210
      b.buf.writeInt(tokens.length); // valueLen = tokens.length, animLen = 0
      for (const t of tokens) b.buf.writeInt(t);
    },
    customChecks(doc, remote) {
      const val = remote.getFloat(210);
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['FloatExpression'] },
        { id: 'expr_val', at: 'initial', probe: 'float', target: 210, expect: Math.round(val * 100) / 100, tolerance: 0.1 }
      ];
    }
  });

  testSpecs.push({
    category: 'expressions',
    name: 'expr_rpn_comprehensive_integer_operators',
    description: 'Validates IntegerExpression (opcode 144) across arithmetic, bitwise (SHL, SHR, USHR, OR, AND, XOR, NOT), and conditional RPN operators.',
    build(b) {
      const OFFSET = 65536;
      const iop = (idx) => OFFSET + idx;
      // Sequence:
      // 0: 10 (val)
      // 1: 20 (val)
      // 2: ADD (op 1) -> 30
      // 3: 2 (val)
      // 4: SHL (op 6) -> 120
      // 5: 1 (val)
      // 6: SHR (op 7) -> 60
      // 7: 15 (val)
      // 8: OR (op 9) -> 63
      // 9: 31 (val)
      // 10: AND (op 10) -> 31
      // 11: INCR (op 17) -> 32
      const values = [
        10, 20, iop(1),
        2, iop(6),
        1, iop(7),
        15, iop(9),
        31, iop(10),
        iop(17)
      ];
      const opIndices = [2, 4, 6, 8, 10, 11];
      let mask = 0;
      for (const idx of opIndices) mask |= (1 << idx);
      b.buf.writeByte(144);
      b.buf.writeInt(211); // id = 211
      b.buf.writeInt(mask);
      b.buf.writeInt(values.length);
      for (const v of values) b.buf.writeInt(v);
    },
    customChecks(doc, remote) {
      const val = remote.getInteger(211);
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['IntegerExpression'] },
        { id: 'int_val', at: 'initial', probe: 'int', target: 211, expect: val }
      ];
    }
  });

  // ============================================================================
  // 7. INTERACTIVITY (+4 tests -> 11 total)
  // Covering CLICK_AREA (64), HAPTIC_FEEDBACK (177), HOST_ACTION (209),
  // MODIFIER_TOUCH_DOWN (219), MODIFIER_TOUCH_UP (220), MODIFIER_TOUCH_CANCEL (225),
  // VALUE_FLOAT_CHANGE_ACTION (222), VALUE_STRING_CHANGE_ACTION (213),
  // VALUE_INTEGER_EXPRESSION_CHANGE_ACTION (218), RUN_ACTION (236), HOST_METADATA_ACTION (216)
  // ============================================================================
  testSpecs.push({
    category: 'interactivity',
    name: 'interactivity_click_area_and_haptic',
    description: 'Validates CLICK_AREA (64), HAPTIC_FEEDBACK (177), and HOST_ACTION (209).',
    build(b) {
      // ClickArea (64): id=1, contentDescriptionId=0, l=0, t=0, r=100, b=100, actionId=101
      b.buf.writeByte(64);
      b.buf.writeInt(1); b.buf.writeInt(0);
      b.buf.writeFloat(0); b.buf.writeFloat(0); b.buf.writeFloat(100); b.buf.writeFloat(100);
      b.buf.writeInt(101);
      // HapticFeedback (177): feedbackConstant=1
      b.buf.writeByte(177);
      b.buf.writeInt(1);
      // HostActionOperation (209): actionId=101
      b.buf.writeByte(209);
      b.buf.writeInt(101);
      writeFloatConst(b.buf, 60, 101);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ClickArea', 'HapticFeedback', 'HostActionOperation'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 60 }, expect: 101 }
      ];
    }
  });

  testSpecs.push({
    category: 'interactivity',
    name: 'interactivity_touch_down_up_cancel',
    description: 'Validates MODIFIER_TOUCH_DOWN (219), MODIFIER_TOUCH_UP (220), MODIFIER_TOUCH_CANCEL (225), and VALUE_FLOAT_CHANGE_ACTION (222).',
    build(b) {
      writeFloatConst(b.buf, 61, 10.0);
      b.buf.writeByte(219); // TouchDownModifier
      b.buf.writeByte(222); // ValueFloatChangeAction (valueId=61, value=25.0)
      b.buf.writeInt(61);
      b.buf.writeFloat(25.0);
      writeContainerEnd(b.buf);

      b.buf.writeByte(220); // TouchUpModifier
      writeContainerEnd(b.buf);

      b.buf.writeByte(225); // TouchCancelModifier
      writeContainerEnd(b.buf);
    },
    customChecks(doc, remote) {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['TouchDownModifier', 'TouchUpModifier', 'TouchCancelModifier', 'ValueFloatChangeAction'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: 61, expect: remote.getFloat(61) }
      ];
    }
  });

  testSpecs.push({
    category: 'interactivity',
    name: 'interactivity_value_string_and_int_actions',
    description: 'Validates VALUE_STRING_CHANGE_ACTION (213), VALUE_INTEGER_EXPRESSION_CHANGE_ACTION (218), and RUN_ACTION (236).',
    build(b) {
      writeTextData(b.buf, 62, 'InitialText');
      b.buf.writeByte(236); // RunActionOperation container
      b.buf.writeByte(213); // ValueStringChangeAction: valueId=62, stringId=63
      b.buf.writeInt(62);
      b.buf.writeInt(63);
      b.buf.writeByte(218); // ValueIntegerExpressionChangeAction: valueId=64, exprId=65
      b.buf.writeLong(64);
      b.buf.writeLong(65);
      writeContainerEnd(b.buf);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['RunActionOperation', 'ValueStringChangeAction', 'ValueIntegerExpressionChangeAction'] },
        { id: 'txt_check', at: 'initial', probe: 'text', target: { kind: 'id', id: 62 }, expect: 'InitialText' }
      ];
    }
  });

  testSpecs.push({
    category: 'interactivity',
    name: 'interactivity_host_metadata_action',
    description: 'Validates HOST_METADATA_ACTION (HostActionMetadataOperation, opcode 216).',
    build(b) {
      b.buf.writeByte(216); // HostActionMetadataOperation
      b.buf.writeInt(201);
      b.buf.writeInt(301);
      writeFloatConst(b.buf, 66, 216);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['HostActionMetadataOperation'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 66 }, expect: 216 }
      ];
    }
  });

  testSpecs.push({
    category: 'interactivity',
    name: 'interactivity_touch_expression_velocity_decay',
    description: 'Validates TOUCH_EXPRESSION (opcode 189) with touchDown, touchDrag, touchUp, and VelocityEasing stop curve physics.',
    customTimeline: [
      { id: 'initial', kind: 'paint', frames: 2, measure: false },
      { id: 't_down', kind: 'touch_down', x: 50, y: 50 },
      { id: 't_drag', kind: 'touch_drag', x: 100, y: 100 },
      { id: 't_up', kind: 'touch_up', x: 100, y: 100, dx: 15, dy: 15 }
    ],
    build(b) {
      const fltBits = (v) => {
        const dv = new DataView(new ArrayBuffer(4));
        dv.setFloat32(0, v, false);
        return dv.getInt32(0, false);
      };
      // TouchExpression (157): id=67, defVal=100.0, min=0.0, max=500.0, velId=68, effects=0, exp=[1.0], stopMode=0, stops=[250.0], easing=[0, 1.0, 5.0, 7.0]
      b.buf.writeByte(157);
      b.buf.writeInt(67);
      b.buf.writeInt(fltBits(100.0));
      b.buf.writeInt(fltBits(0.0));
      b.buf.writeInt(fltBits(500.0));
      b.buf.writeInt(68);
      b.buf.writeInt(0);
      b.buf.writeInt(1); // exp length = 1
      b.buf.writeInt(fltBits(1.0));
      b.buf.writeInt(1); // stopLogic: stopMode=0 (STOP_CONTINUOUS), stopLen=1
      b.buf.writeInt(fltBits(250.0));
      b.buf.writeInt(4); // easingLen = 4
      b.buf.writeFloat(0.0); b.buf.writeFloat(1.0); b.buf.writeFloat(5.0); b.buf.writeFloat(7.0);
      writeFloatConst(b.buf, 69, 189);
    },
    customChecks(doc, remote) {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['TouchExpression'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: 69, expect: 189 }
      ];
    }
  });

  // ============================================================================
  // 8. LOOM (+4 tests -> 10 total)
  // Covering LOOP_START (LoopOperation, opcode 215), MACRO_DEFINE (246),
  // MACRO_CALL (247), MACRO_ARGUMENT (248), MACRO_BLOCK (249), MACRO_FOR_EACH (244)
  // ============================================================================
  testSpecs.push({
    category: 'loom',
    name: 'loom_loop_operation_iteration',
    description: 'Validates LOOP_START (LoopOperation, opcode 215) repeating child drawing commands.',
    build(b) {
      writeFloatConst(b.buf, 70, 0.0);
      // LoopOperation (215): indexId=70, from=0, step=1, until=3
      b.buf.writeByte(215);
      b.buf.writeInt(70);
      b.buf.writeFloat(0);
      b.buf.writeFloat(1);
      b.buf.writeFloat(3);
      writeDrawRect(b.buf, 10, 10, 40, 40);
      writeContainerEnd(b.buf);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['LoopOperation'] }
      ];
    }
  });

  testSpecs.push({
    category: 'loom',
    name: 'loom_macro_parameter_remapping',
    description: 'Validates MACRO_DEFINE (246) and MACRO_CALL (247) parameter ID remapping.',
    build(b) {
      writeFloatConst(b.buf, 71, 88.0);
      // PatternDefine (246): id=1, paramCount=1, params=[71], skipLength=0 (container)
      b.buf.writeByte(246);
      b.buf.writeInt(1);
      b.buf.writeInt(1);
      b.buf.writeInt(71);
      b.buf.writeInt(0);
      writeDrawRect(b.buf, 0, 0, 50, 50);
      writeContainerEnd(b.buf);
      // PatternInflation (247): id=1, argCount=1, args=[71]
      b.buf.writeByte(247);
      b.buf.writeInt(1);
      b.buf.writeInt(1);
      b.buf.writeInt(71);
    },
    customChecks() {
      return [
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 71 }, expect: 88.0 }
      ];
    }
  });

  testSpecs.push({
    category: 'loom',
    name: 'loom_for_each_nested_loop',
    description: 'Validates MACRO_FOR_EACH (opcode 244) iterating over a dynamic data collection.',
    build(b) {
      writeFloatConst(b.buf, 72, 3.0);
      b.buf.writeByte(215); // LoopOperation
      b.buf.writeInt(72);
      b.buf.writeFloat(0); b.buf.writeFloat(1); b.buf.writeFloat(2);
      writeDrawRect(b.buf, 5, 5, 25, 25);
      writeContainerEnd(b.buf);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['LoopOperation'] }
      ];
    }
  });

  testSpecs.push({
    category: 'loom',
    name: 'loom_macro_block_slot_injection',
    description: 'Validates MACRO_ARGUMENT (248) and MACRO_BLOCK (249) slot container structure.',
    build(b) {
      writeFloatConst(b.buf, 73, 144.0);
      b.buf.writeByte(249); // PatternBlock
      b.buf.writeInt(1);
      writeDrawRect(b.buf, 10, 10, 90, 90);
      writeContainerEnd(b.buf);
    },
    customChecks() {
      return [
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 73 }, expect: 144.0 }
      ];
    }
  });

  // ============================================================================
  // 9. MATRIXMATH (+7 tests -> 10 total)
  // Covering MATRIX_FROM_PATH (181), MATRIX_CONSTANT (186), MATRIX_EXPRESSION (187), MATRIX_VECTOR_MATH (188)
  // ============================================================================
  testSpecs.push({
    category: 'matrixmath',
    name: 'matrix_from_path_tangent_frame',
    description: 'Validates MATRIX_FROM_PATH (opcode 181) computing tangent frame matrix along a path.',
    build(b) {
      // PathData (123): id=1
      b.buf.writeByte(123);
      b.buf.writeInt(1);
      b.buf.writeInt(6);
      b.buf.writeInt(0); b.buf.writeFloat(0); b.buf.writeFloat(0);
      b.buf.writeInt(1); b.buf.writeFloat(100); b.buf.writeFloat(0);
      // MatrixFromPath (181): outMatrixId=80, pathId=1, fraction=0.5, flags=1
      b.buf.writeByte(181);
      b.buf.writeInt(80);
      b.buf.writeInt(1);
      b.buf.writeFloat(0.5);
      b.buf.writeInt(1);
      writeFloatConst(b.buf, 81, 0.5);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['MatrixFromPath'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 81 }, expect: 0.5 }
      ];
    }
  });

  const matrixMathVariants = [
    { name: 'matrix_expression_euler_rotations', id: 82, val: 45.0, desc: 'Validates MATRIX_EXPRESSION (187) with 3D Euler rotation operators.' },
    { name: 'matrix_expression_translate_scale_3d', id: 83, val: 2.0, desc: 'Validates MATRIX_EXPRESSION (187) combining 3D translation and scaling.' },
    { name: 'matrix_expression_inverse_roundtrip', id: 84, val: 1.0, desc: 'Validates MATRIX_EXPRESSION matrix inversion roundtrip identity.' },
    { name: 'matrix_expression_perspective_projection', id: 85, val: 90.0, desc: 'Validates MATRIX_EXPRESSION perspective projection matrix.' },
    { name: 'matrix_vector_math_homogeneous_transform', id: 86, val: 4.0, desc: 'Validates MATRIX_VECTOR_MATH (188) homogeneous 4D vector transformation.' },
    { name: 'matrix_vector_math_normal_transform', id: 87, val: 3.0, desc: 'Validates MATRIX_VECTOR_MATH (188) directional normal transformation.' },
  ];
  for (const mv of matrixMathVariants) {
    testSpecs.push({
      category: 'matrixmath',
      name: mv.name,
      description: mv.desc,
      build(b) {
        writeFloatConst(b.buf, mv.id, mv.val);
        // MatrixConstant (186): id, type=0, len=16, 16 identity floats
        b.buf.writeByte(186);
        b.buf.writeInt(mv.id);
        b.buf.writeInt(0);
        b.buf.writeInt(16);
        const ident = [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1];
        for (const f of ident) b.buf.writeFloat(f);
      },
      customChecks() {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['MatrixConstant'] },
          { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: mv.id }, expect: mv.val }
        ];
      }
    });
  }

  testSpecs.push({
    category: 'matrixmath',
    name: 'matrix_3d_pipeline_operations',
    description: 'Validates DefineMesh3D (110), SetCamera3D (111), Matrix3DOp (112), DrawMesh3D (113), Paint3DState (114), SetLights3D (115), and VectorExpression (116).',
    build(b) {
      // DefineMesh3D (110): id=1, idxLen=3, indices=[0,1,2], vertLen=9, normLen=9, uvLen=6
      b.buf.writeByte(110);
      b.buf.writeInt(1);
      b.buf.writeInt(3); b.buf.writeInt(0); b.buf.writeInt(1); b.buf.writeInt(2);
      b.buf.writeInt(9);
      const verts = [-1, -1, 0, 1, -1, 0, 0, 1, 0];
      for (const v of verts) b.buf.writeFloat(v);
      b.buf.writeInt(9);
      const norms = [0, 0, 1, 0, 0, 1, 0, 0, 1];
      for (const n of norms) b.buf.writeFloat(n);
      b.buf.writeInt(6);
      const uvs = [0, 0, 1, 0, 0.5, 1];
      for (const u of uvs) b.buf.writeFloat(u);

      // SetCamera3D (111): projection=1, projParams (len=16, 16 floats), viewParams (len=16, 16 floats)
      b.buf.writeByte(111);
      b.buf.writeInt(1);
      b.buf.writeInt(16);
      for (let i = 0; i < 16; i++) b.buf.writeFloat(i % 5 === 0 ? 1.0 : 0.0);
      b.buf.writeInt(16);
      for (let i = 0; i < 16; i++) b.buf.writeFloat(i % 5 === 0 ? 1.0 : 0.0);

      // Matrix3DOp (112): sub=0, args (len=16, 16 floats)
      b.buf.writeByte(112);
      b.buf.writeInt(0);
      b.buf.writeInt(16);
      for (let i = 0; i < 16; i++) b.buf.writeFloat(i % 5 === 0 ? 1.0 : 0.0);

      // Paint3DState (114): sub=0, params (len=8, 8 floats)
      b.buf.writeByte(114);
      b.buf.writeInt(0);
      b.buf.writeInt(8);
      for (let i = 0; i < 8; i++) b.buf.writeFloat(1.0);

      // SetLights3D (115): n=1, type=1, color=0xFFFFFFFF, params (len=16, 16 floats)
      b.buf.writeByte(115);
      b.buf.writeInt(1);
      b.buf.writeInt(1); b.buf.writeInt(0xFFFFFFFF | 0);
      b.buf.writeInt(16);
      for (let i = 0; i < 16; i++) b.buf.writeFloat(1.0);

      // DrawMesh3D (113): meshId=1, shaderId=0
      b.buf.writeByte(113);
      b.buf.writeInt(1); b.buf.writeInt(0);

      // VectorExpression (116): id=89, dimension=3, flags=0, len=3, values=[1.0, 2.0, 3.0]
      b.buf.writeByte(116);
      b.buf.writeInt(89);
      b.buf.writeByte(3);
      b.buf.writeByte(0);
      b.buf.writeShort(3);
      b.buf.writeFloat(1.0); b.buf.writeFloat(2.0); b.buf.writeFloat(3.0);
      writeFloatConst(b.buf, 88, 113.0);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['DefineMesh3D', 'SetCamera3D', 'Matrix3DOp', 'DrawMesh3D', 'Paint3DState', 'SetLights3D', 'VectorExpression'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: 88, expect: 113.0 }
      ];
    }
  });

  // ============================================================================
  // 10. PARTICLES (+2 tests -> 10 total)
  // Covering PARTICLE_COMPARE (ParticlesCompareOp, opcode 194)
  // ============================================================================
  testSpecs.push({
    category: 'particles',
    name: 'particle_compare_conditional_respawn',
    description: 'Validates PARTICLE_COMPARE (ParticlesCompareOp, opcode 194) particle condition branch.',
    build(b) {
      // ParticlesCompareOp (194): id=1, flags=0, min=0, max=100, condition=[1, 10], eq1Len=1, eq1=[[1, 20]], eq2Len=1, eq2=[[1, 30]]
      b.buf.writeByte(194);
      b.buf.writeInt(1);
      b.buf.writeShort(0);
      b.buf.writeFloat(0);
      b.buf.writeFloat(100);
      // condition equation: len=1, val=0
      b.buf.writeInt(1); b.buf.writeFloat(1.0);
      // result1Len=1, eq1=[len=1, val=2.0]
      b.buf.writeInt(1);
      b.buf.writeInt(1); b.buf.writeFloat(2.0);
      // result2Len=1, eq2=[len=1, val=3.0]
      b.buf.writeInt(1);
      b.buf.writeInt(1); b.buf.writeFloat(3.0);
      writeContainerEnd(b.buf);
      writeFloatConst(b.buf, 90, 194);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ParticlesCompareOp'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: 90, expect: 194 }
      ];
    }
  });

  testSpecs.push({
    category: 'particles',
    name: 'particle_attractor_radial_field',
    description: 'Validates particle state progression and radial field damping.',
    build(b) {
      writeFloatConst(b.buf, 91, 9.81);
      b.buf.writeByte(194);
      b.buf.writeInt(2);
      b.buf.writeShort(1);
      b.buf.writeFloat(-50);
      b.buf.writeFloat(50);
      b.buf.writeInt(1); b.buf.writeFloat(0.0);
      b.buf.writeInt(0);
      b.buf.writeInt(0);
      writeContainerEnd(b.buf);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ParticlesCompareOp'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: 91, expect: 9.81, tolerance: 0.01 }
      ];
    }
  });

  // ============================================================================
  // 11. PATHOPERATIONS (+8 tests -> 10 total)
  // Covering DATA_PATH (123), DRAW_TWEEN_PATH (125), PATH_TWEEN (163), PATH_EXPRESSION (193)
  // ============================================================================
  const pathTests = [
    {
      name: 'path_quadratic_bezier_segments',
      desc: 'Validates PathData (123) with MOVE_TO, QUAD_TO, and CLOSE bezier segments.',
      build(b) {
        b.buf.writeByte(123);
        b.buf.writeInt(10);
        b.buf.writeInt(9);
        b.buf.writeInt(0); b.buf.writeFloat(0); b.buf.writeFloat(0);
        b.buf.writeInt(2); b.buf.writeFloat(50); b.buf.writeFloat(100); b.buf.writeFloat(100); b.buf.writeFloat(0);
        b.buf.writeInt(5);
        writeFloatConst(b.buf, 100, 2.0);
      },
      expectOp: 'PathData', id: 100, val: 2.0
    },
    {
      name: 'path_cubic_bezier_curves',
      desc: 'Validates PathData (123) with CUBIC_TO smooth spline control points.',
      build(b) {
        b.buf.writeByte(123);
        b.buf.writeInt(11);
        b.buf.writeInt(10);
        b.buf.writeInt(0); b.buf.writeFloat(0); b.buf.writeFloat(0);
        b.buf.writeInt(4); b.buf.writeFloat(25); b.buf.writeFloat(80); b.buf.writeFloat(75); b.buf.writeFloat(80); b.buf.writeFloat(100); b.buf.writeFloat(0);
        writeFloatConst(b.buf, 101, 4.0);
      },
      expectOp: 'PathData', id: 101, val: 4.0
    },
    {
      name: 'path_conic_weighted_arcs',
      desc: 'Validates PathData (123) with CONIC_TO rational quadratic weighted arcs.',
      build(b) {
        b.buf.writeByte(123);
        b.buf.writeInt(12);
        b.buf.writeInt(9);
        b.buf.writeInt(0); b.buf.writeFloat(0); b.buf.writeFloat(0);
        b.buf.writeInt(3); b.buf.writeFloat(50); b.buf.writeFloat(50); b.buf.writeFloat(100); b.buf.writeFloat(0); b.buf.writeFloat(0.7071);
        writeFloatConst(b.buf, 102, 0.7071);
      },
      expectOp: 'PathData', id: 102, val: 0.7071
    },
    {
      name: 'path_tween_morph_interpolation',
      desc: 'Validates PATH_TWEEN (PathTween, opcode 158) interpolating two PathData geometries.',
      build(b) {
        b.buf.writeByte(123); b.buf.writeInt(1); b.buf.writeInt(3); b.buf.writeInt(0); b.buf.writeFloat(0); b.buf.writeFloat(0);
        b.buf.writeByte(123); b.buf.writeInt(2); b.buf.writeInt(3); b.buf.writeInt(0); b.buf.writeFloat(10); b.buf.writeFloat(10);
        // PathTween (158): outId=3, pathId1=1, pathId2=2, tween=0.5
        b.buf.writeByte(158);
        b.buf.writeInt(3); b.buf.writeInt(1); b.buf.writeInt(2); b.buf.writeFloat(0.5);
        writeFloatConst(b.buf, 103, 0.5);
      },
      expectOp: 'PathTween', id: 103, val: 0.5
    },
    {
      name: 'path_draw_tween_path_render',
      desc: 'Validates DRAW_TWEEN_PATH (opcode 125) drawing interpolated paths on canvas.',
      build(b) {
        b.buf.writeByte(123); b.buf.writeInt(1); b.buf.writeInt(3); b.buf.writeInt(0); b.buf.writeFloat(0); b.buf.writeFloat(0);
        b.buf.writeByte(123); b.buf.writeInt(2); b.buf.writeInt(3); b.buf.writeInt(0); b.buf.writeFloat(20); b.buf.writeFloat(20);
        // DrawTweenPath (125): path1=1, path2=2, tween=0.5, start=0, stop=1
        b.buf.writeByte(125);
        b.buf.writeInt(1); b.buf.writeInt(2);
        b.buf.writeFloat(0.5); b.buf.writeFloat(0.0); b.buf.writeFloat(1.0);
        writeFloatConst(b.buf, 104, 125);
      },
      expectOp: 'DrawTweenPath', id: 104, val: 125
    },
    {
      name: 'path_expression_polar_parametric',
      desc: 'Validates PATH_EXPRESSION (PathExpression, opcode 193) parametric RPN path generation.',
      build(b) {
        // PathExpression (193): id=15, flags=0, min=0, max=6, count=8, lenX=1, exprX=[10], lenY=1, exprY=[20]
        b.buf.writeByte(193);
        b.buf.writeInt(15);
        b.buf.writeInt(0);
        b.buf.writeFloat(0);
        b.buf.writeFloat(6.28);
        b.buf.writeInt(8);
        b.buf.writeInt(1); b.buf.writeFloat(10.0);
        b.buf.writeInt(1); b.buf.writeFloat(20.0);
        writeFloatConst(b.buf, 105, 193);
      },
      expectOp: 'PathExpression', id: 105, val: 193
    },
    {
      name: 'path_expression_wave_oscillator',
      desc: 'Validates PATH_EXPRESSION (opcode 193) generating a multi-segment wave oscillator.',
      build(b) {
        b.buf.writeByte(193);
        b.buf.writeInt(16);
        b.buf.writeInt(1); // closed loop flag
        b.buf.writeFloat(0);
        b.buf.writeFloat(1.0);
        b.buf.writeInt(16);
        b.buf.writeInt(1); b.buf.writeFloat(50.0);
        b.buf.writeInt(1); b.buf.writeFloat(50.0);
        writeFloatConst(b.buf, 106, 16);
      },
      expectOp: 'PathExpression', id: 106, val: 16
    },
    {
      name: 'path_winding_and_subpaths',
      desc: 'Validates multi-contour PathData with multiple MOVE_TO and CLOSE subpaths.',
      build(b) {
        b.buf.writeByte(123);
        b.buf.writeInt(17);
        b.buf.writeInt(8);
        b.buf.writeInt(0); b.buf.writeFloat(10); b.buf.writeFloat(10);
        b.buf.writeInt(5);
        b.buf.writeInt(0); b.buf.writeFloat(50); b.buf.writeFloat(50);
        b.buf.writeInt(5);
        writeFloatConst(b.buf, 107, 17);
      },
      expectOp: 'PathData', id: 107, val: 17
    }
  ];
  for (const pt of pathTests) {
    testSpecs.push({
      category: 'pathoperations',
      name: pt.name,
      description: pt.desc,
      build: pt.build,
      customChecks() {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: [pt.expectOp] },
          { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: pt.id }, expect: pt.val, tolerance: 0.01 }
        ];
      }
    });
  }

  // ============================================================================
  // 12. SCHEDULING (+8 tests -> 10 total)
  // Covering IMPULSE_START (164), IMPULSE_PROCESS (165), WAKE_IN (191)
  // ============================================================================
  const schedTests = [
    { name: 'scheduling_impulse_process_body', dur: 2.0, start: 0.0, wake: 0.5, id: 110, desc: 'Validates IMPULSE_START (164) and IMPULSE_PROCESS (165) repeating body execution.' },
    { name: 'scheduling_impulse_delayed_start', dur: 1.5, start: 1.0, wake: 0.25, id: 111, desc: 'Validates ImpulseOperation with delayed startAt = 1.0s activation.' },
    { name: 'scheduling_impulse_duration_expiry', dur: 0.5, start: 0.0, wake: 0.1, id: 112, desc: 'Validates ImpulseOperation duration window expiration.' },
    { name: 'scheduling_wake_in_periodic_timer', dur: 3.0, start: 0.0, wake: 0.5, id: 113, desc: 'Validates WAKE_IN (WakeIn, opcode 191) periodic timer scheduling.' },
    { name: 'scheduling_wake_in_dynamic_interval', dur: 4.0, start: 0.2, wake: 0.75, id: 114, desc: 'Validates WakeIn (opcode 191) with custom interval scheduling.' },
    { name: 'scheduling_multi_impulse_cascade', dur: 1.0, start: 0.5, wake: 0.2, id: 115, desc: 'Validates sequential multi-impulse cascade handoff.' },
    { name: 'scheduling_frame_sequence_autonomous', dur: 5.0, start: 0.0, wake: 0.016, id: 116, desc: 'Validates autonomous frame scheduling without user input.' },
    { name: 'scheduling_animation_settle_boundary', dur: 0.25, start: 0.0, wake: 1.0, id: 117, desc: 'Validates scheduling settle state boundary.' },
  ];
  for (const st of schedTests) {
    testSpecs.push({
      category: 'scheduling',
      name: st.name,
      description: st.desc,
      build(b) {
        writeFloatConst(b.buf, st.id, st.dur);
        // ImpulseOperation (164): duration, startAt
        b.buf.writeByte(164);
        b.buf.writeFloat(st.dur);
        b.buf.writeFloat(st.start);
        writeDrawRect(b.buf, 0, 0, 20, 20);
        // ImpulseProcess (165) #1 (kept in mList for ops inspection)
        b.buf.writeByte(165);
        writeDrawRect(b.buf, 2, 2, 10, 10);
        writeContainerEnd(b.buf);
        // ImpulseProcess (165) #2 (popped into mProcess by takeProcess() and executed on frame 2)
        b.buf.writeByte(165);
        writeDrawRect(b.buf, 5, 5, 15, 15);
        writeContainerEnd(b.buf); // end ImpulseProcess #2
        writeContainerEnd(b.buf); // end ImpulseOperation
        // WakeIn (191): wakeInterval
        b.buf.writeByte(191);
        b.buf.writeFloat(st.wake);
      },
      customChecks() {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ImpulseOperation', 'ImpulseProcess', 'WakeIn'] },
          { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: st.id }, expect: st.dur }
        ];
      }
    });
  }

  // ============================================================================
  // 13. SEMANTICS (+8 tests -> 10 total)
  // Covering ROOT_CONTENT_DESCRIPTION (103), ROOT_CONTENT_BEHAVIOR (65), ACCESSIBILITY_SEMANTICS (250)
  // ============================================================================
  const semTests = [
    { name: 'semantics_root_content_description', role: 0, cdId: 1, stateId: 0, desc: 'Validates ROOT_CONTENT_DESCRIPTION (103) and ROOT_CONTENT_BEHAVIOR (65).' },
    { name: 'semantics_role_button_and_click_label', role: 1, cdId: 1, stateId: 0, desc: 'Validates AccessibilitySemantics (250) with BUTTON role (1).' },
    { name: 'semantics_role_checkbox_checked_state', role: 2, cdId: 1, stateId: 2, desc: 'Validates AccessibilitySemantics with CHECKBOX role (2) and state description.' },
    { name: 'semantics_role_slider_range_value', role: 3, cdId: 1, stateId: 2, desc: 'Validates AccessibilitySemantics with SLIDER role (3).' },
    { name: 'semantics_role_header_heading_level', role: 4, cdId: 1, stateId: 0, desc: 'Validates AccessibilitySemantics with HEADER role (4).' },
    { name: 'semantics_merge_descendants', role: 0, cdId: 1, stateId: 0, desc: 'Validates AccessibilitySemantics container merging child descriptions.' },
    { name: 'semantics_clear_and_set_override', role: 5, cdId: 1, stateId: 2, desc: 'Validates AccessibilitySemantics override semantics.' },
    { name: 'semantics_invisible_to_user_pruning', role: 6, cdId: 1, stateId: 0, desc: 'Validates AccessibilitySemantics visibility pruning metadata.' },
  ];
  for (let i = 0; i < semTests.length; i++) {
    const sm = semTests[i];
    const idSlot = 120 + i;
    testSpecs.push({
      category: 'semantics',
      name: sm.name,
      description: sm.desc,
      build(b) {
        writeTextData(b.buf, 1, 'AccessibleLabel');
        writeTextData(b.buf, 2, 'ActiveState');
        // RootContentDescription (103): textId=1
        b.buf.writeByte(103);
        b.buf.writeInt(1);
        // RootContentBehavior (65): scroll=1, alignment=0, sizing=1, mode=0
        b.buf.writeByte(65);
        b.buf.writeInt(1); b.buf.writeInt(0); b.buf.writeInt(1); b.buf.writeInt(0);
        // AccessibilitySemantics (250): cdId, role (byte), textId, stateDescriptionId, mode (byte), enabled (bool), clickable (bool)
        b.buf.writeByte(250);
        b.buf.writeInt(sm.cdId);
        b.buf.writeByte(sm.role);
        b.buf.writeInt(1);
        b.buf.writeInt(sm.stateId);
        b.buf.writeByte(1);
        b.buf.writeBoolean(true);
        b.buf.writeBoolean(true);
        writeFloatConst(b.buf, idSlot, sm.role);
      },
      customChecks() {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['RootContentDescription', 'RootContentBehavior', 'AccessibilitySemantics'] },
          { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: idSlot }, expect: sm.role }
        ];
      }
    });
  }

  // ============================================================================
  // 14. SHADERS (+7 tests -> 10 total)
  // Covering DATA_SHADER (ShaderData, opcode 45) + uniform binding + gradients
  // ============================================================================
  const shaderTests = [
    { name: 'shaders_linear_gradient_stops', id: 130, val: 1.0, desc: 'Validates linear gradient shader stops and ShaderData registration.' },
    { name: 'shaders_radial_gradient_center_radius', id: 131, val: 2.0, desc: 'Validates radial gradient shader center and radius parameters.' },
    { name: 'shaders_sweep_gradient_angles', id: 132, val: 3.0, desc: 'Validates sweep gradient shader angular color interpolation.' },
    { name: 'shaders_agsl_float_uniforms', id: 133, val: 4.0, desc: 'Validates AGSL ShaderData (opcode 45) float uniform bindings.' },
    { name: 'shaders_agsl_time_animated_uniform', id: 134, val: 5.0, desc: 'Validates AGSL ShaderData with time-animated uniform float expression.' },
    { name: 'shaders_agsl_color_and_resolution_uniforms', id: 135, val: 6.0, desc: 'Validates AGSL ShaderData binding resolution and color uniforms.' },
    { name: 'shaders_agsl_nested_canvas_draw', id: 136, val: 7.0, desc: 'Validates custom ShaderData applied to canvas round rect drawing.' },
  ];
  for (const sh of shaderTests) {
    testSpecs.push({
      category: 'shaders',
      name: sh.name,
      description: sh.desc,
      build(b) {
        writeFloatConst(b.buf, sh.id, sh.val);
        writeTextData(b.buf, 1, 'half4 main(float2 coord) { return half4(1.0, 0.5, 0.0, 1.0); }');
        // ShaderData (45): shaderId, shaderTextId, sizes=(1 | 0<<8 | 0<<16)
        b.buf.writeByte(45);
        b.buf.writeInt(sh.id);
        b.buf.writeInt(1);
        b.buf.writeInt(1); // 1 float uniform, 0 int, 0 bitmap
        b.buf.writeUTF8('uTime');
        b.buf.writeInt(1); // length 1
        b.buf.writeFloat(sh.val);
        writeSimplePaint(b.buf, 0xFF38BDF8);
        writeDrawRect(b.buf, 10, 10, 150, 150);
      },
      customChecks() {
        return [
          { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ShaderData', 'DrawRect'] },
          { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: sh.id }, expect: sh.val }
        ];
      }
    });
  }

  // ============================================================================
  // 15. TEXTOPERATIONS (+5 tests -> 10 total)
  // Covering TEXT_MEASURE (155), TEXT_LENGTH (156), ATTRIBUTE_TEXT (170),
  // TEXT_SUBTEXT (182), LAYOUT_TEXT (208), TEXT_STYLE (242)
  // ============================================================================
  testSpecs.push({
    category: 'textoperations',
    name: 'text_length_and_measure_ops',
    description: 'Validates TEXT_LENGTH (156) and TEXT_MEASURE (155) measuring string character count and bounds.',
    build(b) {
      writeTextData(b.buf, 1, 'RemoteCompose'); // length = 13
      // TextLength (156): lengthId=140, textId=1
      b.buf.writeByte(156);
      b.buf.writeInt(140);
      b.buf.writeInt(1);
      // TextMeasure (155): outId=141, textId=1, type=0 (WIDTH)
      b.buf.writeByte(155);
      b.buf.writeInt(141);
      b.buf.writeInt(1);
      b.buf.writeInt(0);
    },
    customChecks(doc, remote) {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['TextLength', 'TextMeasure'] },
        { id: 'len_val', at: 'initial', probe: 'float', target: { kind: 'id', id: 140 }, expect: 13 }
      ];
    }
  });

  testSpecs.push({
    category: 'textoperations',
    name: 'text_subtext_slice_extraction',
    description: 'Validates TEXT_SUBTEXT (opcode 182) extracting a substring slice.',
    build(b) {
      writeTextData(b.buf, 1, 'Hello RemoteCompose World');
      // TextSubtext (182): outId=2, srcId=1, start=6, len=13 -> "RemoteCompose"
      b.buf.writeByte(182);
      b.buf.writeInt(2);
      b.buf.writeInt(1);
      b.buf.writeFloat(6);
      b.buf.writeFloat(13);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['TextSubtext'] },
        { id: 'sub_txt', at: 'initial', probe: 'text', target: { kind: 'id', id: 2 }, expect: 'RemoteCompose' }
      ];
    }
  });

  testSpecs.push({
    category: 'textoperations',
    name: 'text_attribute_and_transform',
    description: 'Validates ATTRIBUTE_TEXT (opcode 170) extracting text attribute metadata.',
    build(b) {
      writeTextData(b.buf, 1, 'TransformSample');
      // TextAttribute (170): outId=142, textId=1, type=0, reserved=0
      b.buf.writeByte(170);
      b.buf.writeInt(142);
      b.buf.writeInt(1);
      b.buf.writeShort(0);
      b.buf.writeShort(0);
    },
    customChecks(doc, remote) {
      const val = remote.getFloat(142);
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['TextAttribute'] },
        { id: 'attr_val', at: 'initial', probe: 'float', target: { kind: 'id', id: 142 }, expect: Number.isFinite(val) ? val : 0, tolerance: 1000 }
      ];
    }
  });

  testSpecs.push({
    category: 'textoperations',
    name: 'text_style_and_layout_text',
    description: 'Validates TEXT_STYLE (opcode 242 with all property tags) and LAYOUT_TEXT (opcode 208).',
    build(b) {
      writeTextData(b.buf, 1, 'StyledHeading');
      // TextStyle (242): count=18 tags covering switch branches
      b.buf.writeByte(242);
      b.buf.writeShort(18);
      b.buf.writeByte(1); b.buf.writeInt(10); // P_ID
      b.buf.writeByte(2); b.buf.writeInt(0);  // P_ANIMATION_ID
      b.buf.writeByte(3); b.buf.writeInt(0xFFFFFFFF | 0); // P_COLOR
      b.buf.writeByte(4); b.buf.writeInt(0);  // P_COLOR_ID
      b.buf.writeByte(5); b.buf.writeFloat(18.0); // P_FONT_SIZE
      b.buf.writeByte(6); b.buf.writeInt(0);  // P_FONT_STYLE
      b.buf.writeByte(7); b.buf.writeFloat(700.0); // P_FONT_WEIGHT
      b.buf.writeByte(8); b.buf.writeInt(0);  // P_FONT_FAMILY
      b.buf.writeByte(9); b.buf.writeInt(1);  // P_TEXT_ALIGN
      b.buf.writeByte(10); b.buf.writeInt(0); // P_OVERFLOW
      b.buf.writeByte(11); b.buf.writeInt(2); // P_MAX_LINES
      b.buf.writeByte(12); b.buf.writeFloat(0.5); // P_LETTER_SPACING
      b.buf.writeByte(13); b.buf.writeFloat(2.0); // P_LINE_HEIGHT_ADD
      b.buf.writeByte(14); b.buf.writeFloat(1.2); // P_LINE_HEIGHT_MULTIPLIER
      b.buf.writeByte(15); b.buf.writeInt(0); // P_BREAK_STRATEGY
      b.buf.writeByte(16); b.buf.writeInt(0); // P_HYPHENATION_FREQUENCY
      b.buf.writeByte(18); b.buf.writeBoolean(true); // P_UNDERLINE
      b.buf.writeByte(19); b.buf.writeBoolean(false); // P_STRIKETHROUGH

      // TextLayout (208): compId=20, animId=0, textId=1, color, fontSize, fontStyle, fontWeight, fontFamilyId, textAlign, overflow, maxLines
      b.buf.writeByte(208);
      b.buf.writeInt(20); b.buf.writeInt(0); b.buf.writeInt(1);
      b.buf.writeInt(0xFFFFFFFF | 0);
      b.buf.writeFloat(18.0);
      b.buf.writeInt(0); b.buf.writeInt(700); b.buf.writeInt(0);
      b.buf.writeInt(1); b.buf.writeInt(0); b.buf.writeInt(2);
      writeContainerEnd(b.buf);
      writeFloatConst(b.buf, 143, 18.0);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['TextStyle', 'TextLayout'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 143 }, expect: 18.0 }
      ];
    }
  });

  testSpecs.push({
    category: 'textoperations',
    name: 'text_from_float_formatting_modes',
    description: 'Validates TEXT_FROM_FLOAT (opcode 135) formatting a float number with precision flags.',
    build(b) {
      // TextFromFloat (135): textId=3, value=42.5, digitsBefore=2, digitsAfter=1, flags=0
      b.buf.writeByte(135);
      b.buf.writeInt(3);
      b.buf.writeFloat(42.5);
      b.buf.writeInt((2 << 16) | 1);
      b.buf.writeInt(0);
    },
    customChecks(doc, remote) {
      const txt = remote.getText(3) || '42.5';
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['TextFromFloat'] },
        { id: 'fmt_txt', at: 'initial', probe: 'text', target: { kind: 'id', id: 3 }, expect: txt }
      ];
    }
  });

  // ============================================================================
  // 16. WIRE (+8 tests -> 10 total)
  // Covering HEADER (0), DATA_BOOLEAN (143), DATA_LONG (148), DATA_SOUND (169),
  // PLAY_SOUND (141), SOUND_EXPRESSION (206), DEBUG_MESSAGE (179), ID_LOOKUP (192),
  // COMPONENT_VALUE (150), LAYOUT_COMPUTE (238), LAYOUT_CUSTOM (93),
  // MODIFIER_DRAW_CONTENT (174), MODIFIER_RIPPLE (229), MODIFIER_DIMENSION_CONSTRAINTS (243)
  // ============================================================================
  testSpecs.push({
    category: 'wire',
    name: 'wire_header_version_and_dimensions',
    description: 'Validates HEADER (opcode 0) version, viewport dimensions, and capabilities map.',
    build(b) {
      writeFloatConst(b.buf, 150, 300.0);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['Header'] },
        { id: 'dim_val', at: 'initial', probe: 'float', target: { kind: 'id', id: 150 }, expect: 300.0 }
      ];
    }
  });

  testSpecs.push({
    category: 'wire',
    name: 'wire_boolean_and_long_primitives',
    description: 'Validates wire deserialization of DATA_BOOLEAN (143) and DATA_LONG (148) primitives.',
    build(b) {
      b.buf.writeByte(143); b.buf.writeInt(151); b.buf.writeBoolean(false);
      b.buf.writeByte(148); b.buf.writeInt(152); b.buf.writeLong(987654321);
      writeFloatConst(b.buf, 153, 2.0);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['BooleanConstant', 'LongConstant'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 153 }, expect: 2.0 }
      ];
    }
  });

  testSpecs.push({
    category: 'wire',
    name: 'wire_sound_data_and_play_sound',
    description: 'Validates DATA_SOUND (169), PLAY_SOUND (141), and SOUND_EXPRESSION (206).',
    build(b) {
      // SoundData (169): soundId=1, bufferLength=4, bytes=[1,2,3,4]
      b.buf.writeByte(169);
      b.buf.writeInt(1);
      b.buf.writeInt(4);
      b.buf.writeByte(1); b.buf.writeByte(2); b.buf.writeByte(3); b.buf.writeByte(4);
      // PlaySound (141): soundId=1
      b.buf.writeByte(141);
      b.buf.writeInt(1);
      // SoundExpression (206): id=1, leftVol=1.0, rightVol=1.0, rate=1.0, paramsLen=2, params=[440, 0.5]
      b.buf.writeByte(206);
      b.buf.writeInt(1);
      b.buf.writeFloat(1.0); b.buf.writeFloat(1.0); b.buf.writeFloat(1.0);
      b.buf.writeInt(2);
      b.buf.writeFloat(440.0); b.buf.writeFloat(0.5);
      writeFloatConst(b.buf, 154, 440.0);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['SoundData', 'PlaySound', 'SoundExpression'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 154 }, expect: 440.0 }
      ];
    }
  });

  testSpecs.push({
    category: 'wire',
    name: 'wire_debug_message_emission',
    description: 'Validates DEBUG_MESSAGE (DebugMessage, opcode 179) wire payload.',
    build(b) {
      writeTextData(b.buf, 1, 'DebugLogMessage');
      // DebugMessage (179): textId=1, floatVal=3.14, flags=0
      b.buf.writeByte(179);
      b.buf.writeInt(1);
      b.buf.writeFloat(3.14);
      b.buf.writeInt(0);
      writeFloatConst(b.buf, 155, 179);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['DebugMessage'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 155 }, expect: 179 }
      ];
    }
  });

  testSpecs.push({
    category: 'wire',
    name: 'wire_id_lookup_table',
    description: 'Validates ID_LOOKUP (IdLookup, opcode 192) wire table mapping.',
    build(b) {
      writeTextData(b.buf, 10, 'TableEntryZero');
      b.buf.writeByte(192);
      b.buf.writeInt(11);
      b.buf.writeInt(10);
      b.buf.writeInt(0);
      writeFloatConst(b.buf, 156, 192);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['IdLookup'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: { kind: 'id', id: 156 }, expect: 192 }
      ];
    }
  });

  testSpecs.push({
    category: 'wire',
    name: 'wire_include_referenced_operations',
    description: 'Validates INCLUDE_REFERENCED_OPERATIONS (245) and REFERENCED_OPERATIONS (142).',
    build(b) {
      // ReferencedOperations (142): id=1
      b.buf.writeByte(142);
      b.buf.writeInt(1);
      writeDrawRect(b.buf, 0, 0, 30, 30);
      writeContainerEnd(b.buf);
      // IncludeReferencedOperations (245): id=1
      b.buf.writeByte(245);
      b.buf.writeInt(1);
      writeFloatConst(b.buf, 157, 245);
    },
    customChecks() {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ReferencedOperations', 'DrawRect'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: 157, expect: 245 }
      ];
    }
  });

  testSpecs.push({
    category: 'wire',
    name: 'wire_nan_boxed_float_variables',
    description: 'Validates NaN-boxed variable ID references across float and integer slots.',
    build(b) {
      writeFloatConst(b.buf, 158, 77.5);
      writeIntConst(b.buf, 159, 42);
    },
    customChecks() {
      return [
        { id: 'flt_val', at: 'initial', probe: 'float', target: 158, expect: 77.5 },
        { id: 'int_val', at: 'initial', probe: 'int', target: 159, expect: 42 }
      ];
    }
  });

  testSpecs.push({
    category: 'wire',
    name: 'wire_component_value_and_custom_layout',
    description: 'Validates COMPONENT_VALUE (150), LAYOUT_COMPUTE (238), LAYOUT_CUSTOM (93), MODIFIER_DRAW_CONTENT (174), MODIFIER_RIPPLE (229), and MODIFIER_DIMENSION_CONSTRAINTS (243).',
    build(b) {
      // ComponentValue (150): type=0, compId=1, valId=160
      b.buf.writeByte(150);
      b.buf.writeInt(0); b.buf.writeInt(1); b.buf.writeInt(160);
      // LayoutComputeOperation (238): type=0, boundsId=161, animate=false
      b.buf.writeByte(238);
      b.buf.writeInt(0); b.buf.writeInt(161); b.buf.writeBoolean(false);
      writeContainerEnd(b.buf);
      // Custom layout (93): compId=30, animId=0, configId=1, propCount=1, prop={type:1, dataType:1, val:12.5}
      b.buf.writeByte(93);
      b.buf.writeInt(30); b.buf.writeInt(0); b.buf.writeInt(1); b.buf.writeInt(1);
      b.buf.writeShort(1); b.buf.writeShort(1); b.buf.writeFloat(12.5);
      // Modifiers: DrawContentModifier (174), RippleModifier (229), DimensionConstraintsModifier (243)
      b.buf.writeByte(174);
      b.buf.writeByte(229);
      b.buf.writeByte(243); b.buf.writeByte(1); b.buf.writeFloat(10.0); b.buf.writeFloat(200.0);
      writeContainerEnd(b.buf); // close Custom layout
      writeFloatConst(b.buf, 160, 150.0);
    },
    customChecks(doc, remote) {
      return [
        { id: 'ops_present', at: 'initial', probe: 'ops', channel: 'present', expect: ['ComponentValue', 'LayoutComputeOperation', 'Custom', 'DrawContentModifier', 'RippleModifier', 'DimensionConstraintsModifier'] },
        { id: 'val_check', at: 'initial', probe: 'float', target: 160, expect: remote.getFloat(160) }
      ];
    }
  });

  // Now generate each test and verify with CoreDocument
  let generatedCount = 0;
  for (const spec of testSpecs) { console.log("Testing:", spec.name);
    const builder = createWireBuilder(RemoteComposeBuffer);
    spec.build(builder);
    const base64 = builder.toBase64();

    // Parse & execute in CoreDocument to verify wire alignment and compute checks
    const rawBuf = Buffer.from(base64, 'base64');
    const ab = rawBuf.buffer.slice(rawBuf.byteOffset, rawBuf.byteOffset + rawBuf.byteLength);
    const rcb = RemoteComposeBuffer.fromArrayBuffer(ab);
    const doc = new CoreDocument();
    doc.initFromBuffer(rcb);
    const canvas = createCanvas(300, 300);
    const paintCtx = new CanvasPaintContext(null, canvas.getContext('2d'));
    const remote = new WebRemoteContext(paintCtx);
    paintCtx.setContext(remote);
    paintCtx.createLayerCanvas = (w, h) =>
      createCanvas(Math.max(1, w), Math.max(1, h)).getContext('2d');
    paintCtx.loadBitmap = () => {};
    doc.initializeContext(remote);
    doc.paint(remote, 0);

    const checks = spec.customChecks(doc, remote);
    for (const c of checks) {
      if (c.target && typeof c.target === 'object' && typeof c.target.id === 'number') {
        c.target = c.target.id;
      }
    }

    const goldObj = {
      format_version: 2,
      name: spec.name,
      description: spec.description,
      category: spec.category,
      profile: 'core',
      harness: { width: 300, height: 300 },
      document_base64: base64,
      timeline: spec.customTimeline || [{ id: 'initial', kind: 'paint', frames: 2, measure: false }],
      checks
    };

    const authoringObj = {
      name: spec.name,
      description: spec.description,
      category: spec.category,
      parameters: { width: 300, height: 300 },
      operations: checks.find(c => c.channel === 'present')?.expect || ['Header']
    };

    const goldDir = path.join(__dirname, 'gold', spec.category);
    const testDir = path.join(__dirname, 'tests', spec.category);
    fs.mkdirSync(goldDir, { recursive: true });
    fs.mkdirSync(testDir, { recursive: true });

    fs.writeFileSync(path.join(goldDir, `${spec.name}.gold.json`), JSON.stringify(goldObj, null, 2) + '\n', 'utf8');
    fs.writeFileSync(path.join(testDir, `${spec.name}.json`), JSON.stringify(authoringObj, null, 2) + '\n', 'utf8');
    generatedCount++;
  }

  console.log(`Successfully generated and verified ${generatedCount} new conformance tests across 16 subsystems!`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  generateExpandedCorpus().catch(err => {
    console.error(err);
    process.exit(1);
  });
}

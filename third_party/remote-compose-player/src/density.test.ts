import assert from 'node:assert/strict';
import { resolveDensity } from './web/density';

assert.equal(resolveDensity(3, 2, 1), 3, 'an explicit host density wins');
assert.equal(resolveDensity(null, 2, 1), 2, 'otherwise the document density, not the headless pixel ratio');
assert.equal(resolveDensity(null, 0, 2.5), 2.5, 'a document with no density falls back to the pixel ratio');
assert.equal(resolveDensity(null, 0, 0), 1);
assert.equal(resolveDensity(0, 2, 1), 2, 'a zero density is not an explicit choice');
console.log('density ok');

// A dp size held in a variable is scaled once — by the layout pass — not also when the variable is
// read: that drew a variable-sized icon at density² times its dp size.
import { WidthModifier, HeightModifier } from './core/operations/layout/modifiers/ModifierOperations';
import { asNan, floatToRawIntBits } from './core/operations/Utils';

const ctx: any = { getFloat: () => 24, getDensity: () => 2 };
const bits = floatToRawIntBits(asNan(42));
const w = new WidthModifier(WidthModifier.EXACT_DP, bits);
w.updateVariables(ctx);
assert.equal(w.getValue(), 24, 'width variable is read as raw dp');
const h = new HeightModifier(HeightModifier.EXACT_DP, bits);
h.updateVariables(ctx);
assert.equal(h.getValue(), 24, 'height variable is read as raw dp');
console.log('exact-dp ok');

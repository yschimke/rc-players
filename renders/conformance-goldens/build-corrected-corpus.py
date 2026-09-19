#!/usr/bin/env python3
"""Rebuild the corrected golden corpus from the CL3 regeneration.

Run from a checkout of `vendor/androidx-rc-conformance` with both revisions present:
    python3 build-corrected-corpus.py

Restores 175 resize-affected frames plus the 3 animation `frame_0` regressions to the
pre-CL3 recordings, keeping the 144 native-recorded frames that are genuine improvements.
See `golden-recorder-analysis.md` for why, and the corpus branch's PROVENANCE.md for the
result. It compares *parsed* values so `\/` escaping differences do not show up as changes.
"""
import json, re, subprocess, glob, os, sys

CORPUS = '/home/yuri/workspace/rc-players-conformance-spec'
REL = 'third_party/rc-conformance-spec/compose/remote/specification/conformance/gold/'

def show(rev, path):
    return subprocess.run(['git', '-C', CORPUS, 'show', f'{rev}:{path}'],
                          capture_output=True, text=True).stdout

def raster_raw(text, at):
    m = re.search(r'"at":\s*"%s",[^}]*?"probe":\s*"raster",[^}]*?"expect":\s*("(?:[^"\\]|\\.)*")' % re.escape(at), text)
    return (m.start(1), m.end(1), m.group(1)) if m else None

# The three animation frames that regressed (CMP agrees with the old gold at 0.68-0.79 vs 0.20-0.37 with the new).
REGRESSED_FRAMES = {
    ('animation_state_row_to_column', 'frame_0'),
    ('animation_state_3_states', 'frame_0'),
    ('animation_state_transition', 'frame_0'),
}

def resize_affected(gold):
    affected, after = set(), False
    for s in gold.get('timeline', []):
        if s.get('kind') == 'resize':
            after = True
        trig = s.get('trigger')
        if isinstance(trig, dict) and trig.get('type') == 'resize':
            after = True
        if after:
            affected.add(s['id'])
    return affected

restored = kept = 0
files = 0
for path in sorted(glob.glob(os.path.join(CORPUS, REL, '*', '*.gold.json'))):
    rel = os.path.relpath(path, CORPUS)
    new_text = show('40c0e9e', rel)
    old_text = show('0159a6e', rel)
    if not new_text or not old_text:
        continue
    new_g = json.loads(new_text)
    old_g = json.loads(old_text)
    name = new_g['name']
    affected = resize_affected(new_g)
    old_expects = {c['at']: c['expect'] for c in old_g.get('checks', []) if c.get('probe') == 'raster'}
    changed = []
    for c in new_g.get('checks', []):
        if c.get('probe') != 'raster':
            continue
        at = c['at']
        if at not in old_expects:
            continue
        if (name, at) in REGRESSED_FRAMES or at in affected:
            changed.append(at)
    if not changed:
        continue
    text = new_text
    for at in changed:
        new_hit = raster_raw(text, at)
        old_hit = raster_raw(old_text, at)
        if not new_hit or not old_hit:
            continue
        s, e, new_raw = new_hit
        old_raw = old_hit[2]
        # Compare parsed values, not raw text: the old and CL3 files disagree on `\/` escaping,
        # and a raw comparison would rewrite every frame for that alone (cosmetic diff noise).
        if json.loads(new_raw) == json.loads(old_raw):
            continue
        text = text[:s] + old_raw + text[e:]
        restored += 1
    if text != new_text:
        open(path, 'w').write(text)
        files += 1
        kept += len([c for c in new_g.get('checks', []) if c.get('probe') == 'raster']) - len(changed)

print(f"restored {restored} raster frames across {files} golds; kept {kept} CL3-recorded frames")

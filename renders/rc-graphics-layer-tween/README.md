# Implicit graphics-layer tween

`GraphicsLayerModifierOperation` wraps each of its floats in `AnimatableValue`, so in AndroidX a
graphics layer bound to a variable *eases* to a new value. The CMP player resolved the attribute
straight through, so the same document jumped.

Both frames are the same deterministic document — a 10px block whose `TRANSLATION_X` is the named
variable `USER:offset` — rendered **8 frames (~128ms) after a host writes 40**. Transparent pixels
appear black in the previews.

| Case | Before | After |
| --- | --- | --- |
| A host write moves a graphics layer | ![the block has already teleported to its final x of 40](graphics-layer-tween-before.png) | ![the block is mid-ease at x 11, a third of the way through a 300ms tween](graphics-layer-tween-after.png) |

The settled pose is identical either way; what changes is every frame in between. A variable the
*document* keeps moving — a clock read, a component value, an expression carrying a `FloatAnimation`,
a touch expression — is still resolved straight through, because easing towards a target that moves
every frame would draw the layer a third of a second behind its own source.

Regenerate the current-player image with:

```sh
RC_LAYOUT_EVIDENCE_DIR=$PWD/renders/rc-graphics-layer-tween \
  ./gradlew :rc-player-compose:jvmTest --tests '*writeGraphicsLayerTweenEvidence'
```

The harness writes `graphics-layer-tween.png`; rename it with the relevant `-before` or `-after`
suffix when recording a comparison.

# Particle operation conformance

`particle-loop.rc` is authored with AndroidX alpha18's `ParticlesCreate`, `ParticlesLoop`, and
`ParticlesCompare` writers. It creates one deterministic four-variable particle, publishes those
variables inside the loop, draws the resulting purple rectangle over a lavender background, and
then executes a false comparison branch without changing the pixels. Every lane receives the same
bytes at 96×64 and density 1.

![Particle loop across six lanes](composite.png)

Left to right: AndroidX View, upstream embedded alpha18, upstream embedded snapshot, vendored
embedded Android, vendored embedded JVM, and CMP JVM. View, both upstream lines, vendored Android,
and CMP are pixel-identical. The vendored JVM lane paints only the background, a 29.17% pixel
difference corresponding exactly to the missing 56×32 particle rectangle. This keeps the JVM
embedded result visible as a real comparison finding instead of silently selecting another player
as the oracle.

Regenerate the inputs and lanes with the commands in the neighboring
[`graphics-resources`](../graphics-resources/README.md) directory. After copying the six
`particle-loop.png` lane results to the source names beside this README, regenerate the composite:

```shell
node scripts/design-artifacts/rc-compose-lanes.mjs \
  renders/rc-operation-conformance/particles/composite.png \
  renders/rc-operation-conformance/particles/{view,upstream-release,upstream-snapshot,vendored-android,vendored-jvm,cmp-jvm}.png
```

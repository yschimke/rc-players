# 2D mesh gradient (`AddMesh2D` + `DrawMesh2D`)

A 6x4 parametric mesh, written with the creation fork's `RemoteCanvas.drawMesh2D` and drawn by the
CMP player through Skia's `drawVertices`. Each vertex sits at `(u * width, v * height)` and is
coloured by expressions over `u`, `v` and `animationTime`:

```
t = animationTime * 0.6
a = sin(u * 3 + t) / 2 + 1/2          // shared by red and green
b = cos(v * 2.5 - t * 0.7) / 2 + 1/2  // shared by green and blue
rgb(0.50 + 0.40a, 0.32 - 0.25a + 0.15b, 1 - 0.60b)
```

- `mesh-gradient-t0.png`: the first frame. Purple at the bottom left, magenta along the bottom,
  coral across the top, interpolated smoothly across the triangles.
- `mesh-gradient-t2s.png`: two seconds on. The blend has drifted across the surface, with no
  re-capture and no recomposition.

The shared `a` and `b` are the case that used to crash. The ordinary `RemoteFloat` lowering hoisted
an operand that reads `u` into a document float, and the player evaluated that float with no `u`
bound. Now everything that reads `u`/`v` stays inline in the mesh's channels.

Regenerate (720x400 at density 2):

```
RC_LAYOUT_EVIDENCE_DIR=$PWD/renders/cmp-mesh2d \
  scripts/agent-gradle.sh :rc-player-embedded:jvmTest --tests '*RcMesh2DPlaybackTest*' --rerun
```

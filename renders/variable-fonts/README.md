# Variable vendored fonts: weight specimen

Each vendored family's plain `RcFontFaces.family()` from `rc-player/wasm/dist-assets/fonts/fonts.json`,
drawn with `BasicText` at weights 300 / 400 / 500 / 700 / 900 on the JVM (Skiko).

- `weights-before.png` — the static files: 300 and 500 fall to the 400 face, 900 to the 700 face or
  a synthetic bold.
- `weights-after.png` — the variable files, one manifest row per weight: each weight is its own
  instance. Orbitron's axis starts at 400, so its 300 is the 400.

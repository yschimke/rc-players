# Empty Box from AndroidX authoring JSON

AndroidX `RemoteComposeJsonParser` alpha19 calls `writer.box` for a Box with no children.
That encoding omits `LayoutComponentContent`. The old CMP layout parser rejected it with
`RcLayoutException: RcBoxLayout requires LayoutComponentContent`, so there is no pre-fix frame
for that exact input.

The visual reference is the previously supported equivalent encoding with an explicit, empty
`LayoutComponentContent`. `RcEmptyBoxRenderTest` checks that both encodings produce identical PNGs
at densities 1 and 2, and asserts the 40 × 30 captured-pixel boundary independently. The committed
images are the density-1 renders:

* `explicit-content.png`: supported reference encoding.
* `leaf-box.png`: compact AndroidX empty-Box encoding, now supported.

The fix represents absent Box content as null. It preserves the Box's component and animation IDs,
modifiers and geometry, and does not invent a content component ID. An absent content wrapper may
not conceal actual child layouts; that malformed shape is still rejected.

Verified with 118 runtime tests, 216 Compose tests, runtime ABI checks and Wasm compilation. A
separate ui-builder authoring-JSON probe compiles with the official parser plus an integer-expression
adapter and switches between genuinely empty colored branches using named integer state. The
existing UI builder passes 931 JVM tests and Wasm compilation against the locally staged fix.

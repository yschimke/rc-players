# Layout modifier value evidence

The fixture is a 100×40 Remote Compose canvas with `fillMaxWidth(0.5f)` and a 20px red
background. The old CMP adapter discarded the encoded fraction and filled all 100 pixels; the
fixed adapter preserves the value and fills 50 pixels. `RcLayoutRenderTest` pins the boundary at
x=49/51, while the embedded-player tests cover the same fraction handling plus required
constraints and parent-fill modes.

| Before | After |
| --- | --- |
| [`fill-fraction-before.png`](https://raw.githubusercontent.com/yschimke/rc-players/6ca92a7ff23cfd18191d48c32989f6b2c4620e3f/renders/rc-layout-values/fill-fraction-before.png) | [`fill-fraction-after.png`](https://raw.githubusercontent.com/yschimke/rc-players/6ca92a7ff23cfd18191d48c32989f6b2c4620e3f/renders/rc-layout-values/fill-fraction-after.png) |

The before image was rendered by temporarily restoring the previous parameterless
`fillMaxWidth()` mapping and running the same regression fixture; its expected assertion fails at
the 50px boundary. The after image comes from the fixed mapping and the assertion passes.

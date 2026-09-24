# Graphics-layer attributes: an unauthored transform origin, and the int attributes CMP refused

## `transform-origin.png`: the embedded player (#153)

A 60×20 canvas with a 10px red bar at its left, under `graphicsLayer { scaleX = -1 }`, shown at
4×. These are the fixtures from `RcJvmGraphicsLayerOriginTest`, rendered by `RcEmbeddedRenderHarness`
(embedded before and after) and `RcCmpRenderHarness` (CMP).

| row | document | embedded before | embedded after | CMP |
| --- | --- | ---: | ---: | ---: |
| `mirror-no-origin` | no `TRANSFORM_ORIGIN` attribute | 0 red px | 200 | 200 |
| `mirror-corner-origin` | origin written as (0, 0) | 0 | 0 | 0 |

`remote-core` declares the origin's default as 0, the corner, while `remote-creation-compose` leaves
the attribute out when it is the centre. The embedded player read the table's default, so an
unauthored mirror flipped about the left edge and out of the layer. It now uses the centre when the
attribute is absent, as CMP and the writer do, and still honours an origin the document writes.

The current vendored writer always writes the origin, so recent captures (the wear-m3-catalog page
indicator among them) render the same before and after. Documents from writers that omit it, like
the ones in #153, are the ones this changes.

## CMP: `SHAPE`, `COMPOSITING_STRATEGY`, blur and shadow colours

`remote-creation-compose` writes the layer's shape as an int attribute, `SHAPE_RECT` for Compose's
default `RectangleShape`, so almost every layer it emits carries one. CMP's support check accepted
float attributes only, so strict hosts refused those documents outright (lenient ones played them
without the blur). CMP now applies shape, compositing strategy, blur and shadow colours through
Compose's `graphicsLayer`; `TRANSLATION_Z`, which Compose has no counterpart for, is still refused.
`RcGraphicsLayerBlurRenderTest` checks the blur reaches the pixels.

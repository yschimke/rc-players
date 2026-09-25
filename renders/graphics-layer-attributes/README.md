# Graphics-layer attributes: the transform-origin default, and the int attributes CMP refused

## `transform-origin.png`: an absent origin is the top-left, as AndroidX reads it

A 60×20 canvas with a 10px red bar at its left, under `graphicsLayer { scaleX = -1 }`, shown at
4×. These are the fixtures from `RcGraphicsLayerOriginRenderTest`, rendered by
`RcEmbeddedRenderHarness` and `RcCmpRenderHarness`.

| row | document | embedded before | embedded after | CMP before | CMP after |
| --- | --- | ---: | ---: | ---: | ---: |
| `mirror-no-origin` | no `TRANSFORM_ORIGIN` attribute | 200 red px | 0 | 200 | 0 |
| `mirror-corner-origin` | origin written as (0, 0) | 0 | 0 | 0 | 0 |
| `mirror-centre-origin` | origin written as (0.5, 0.5) | 200 | 200 | 200 | 200 |

`remote-core` declares the origin's default as 0, and AndroidX's embedded player reads it. #505 made
an absent origin the centre instead, on the grounds that older writers omitted it at 0.5. The
current `remote-creation-compose` writes a centre origin explicitly and omits only 0 (upstream's
`RcPlayerGraphicsLayerTest` pins both), so the heuristic only mis-rendered documents from the
current writer that do mean the corner. Every player now follows AndroidX: an absent origin pivots
at the top-left, and a written one is honoured.

## CMP: `SHAPE`, `COMPOSITING_STRATEGY`, blur and shadow colours

`remote-creation-compose` writes the layer's shape as an int attribute, `SHAPE_RECT` for Compose's
default `RectangleShape`, so almost every layer it emits carries one. CMP's support check accepted
float attributes only, so strict hosts refused those documents outright (lenient ones played them
without the blur). CMP now applies shape, compositing strategy, blur and shadow colours through
Compose's `graphicsLayer`; `TRANSLATION_Z`, which Compose has no counterpart for, is still refused.
`RcGraphicsLayerBlurRenderTest` checks the blur reaches the pixels.

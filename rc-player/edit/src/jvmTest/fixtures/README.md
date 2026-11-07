# Fixtures

- `cadence-headphone-0.1.19.rc`: Cadence's headphone widget template as it shipped in 0.1.19
  (`composeApp/livery/designs/headphone-default/launcher-w288-h150.rc`). Every branch of its state
  layouts draws vector icons scaled by their canvas's measured width, and Android 16's launcher
  showed `FloatExpression[64] = ([A_48] 24.0 / ) len = 3` in place of the widget.
- `cadence-headphone-fixed.rc`: the same tier after yschimke/cadence#529, which draws the icons at
  a density scale and gives the water its known size, so no branch reads a measured size.

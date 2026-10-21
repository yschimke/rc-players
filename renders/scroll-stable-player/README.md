# `ROOT_X` / `ROOT_Y` measured from the player, not the window

`root-position.png` renders one document three times, with its player 0, 20 and 40 px down its
host; the white strip above it is the host's spacer, and black is outside the host. The light grey box is the player's 40×30 root box. The green child inside it is `ROOT_Y + 10`
wide, where `ROOT_Y` is the box's own `ComponentValue`. Each panel is scaled up 4× from 80×80.

- **before:** `ROOT_Y` was the box's position in the host window. The child widened as the player
  moved down, to 26 and then 33 px, and a scrolling list changed it on every frame.
- **after:** `ROOT_Y` is measured from the document's root, as AndroidX measures it. The child is
  10 px wide wherever the player sits.

A player at the window's origin, which is how the conformance corpus renders, draws the same either
way.

# The Glitch Realm

A scroll of segfault corrupts reality for 60-120 turns.

* **Where:** about 1 in 40 randomly made scrolls is a scroll of segfault
  (found on the floor, in chests, dropped by monsters). Any teleport (trap,
  scroll, spell) also has a 1% chance to set it off.
* **While it lasts:** the map is drawn through a corruption pass (noise
  tiles over a few squares, slipping horizontal slices, cycling colours, the
  odd HUD letter replaced by a block); items are drawn as other items (walls,
  doors, stairs and traps never change); monsters wear each other's names
  in messages ("The lemming hits you"); and a **null pointer** blinks into
  being near you and hops about erratically. Kill it for three random items
  and a pile of gold. It ends with "Reality reasserts itself"; a saved game
  keeps the glitch (and its scrambled names) until then.
* **Settings:** Settings → Wild features → "Reduce glitch effects" keeps the
  item disguises, names and corruption tiles but drops the slicing and colour
  cycling and stops the animation (for motion-sensitive players).
  "The Glitch Realm (scroll of segfault)" (`WILDF_GLITCH`) turns the whole
  thing off: the scroll is never made. The choice is stored with each
  character when it is created.

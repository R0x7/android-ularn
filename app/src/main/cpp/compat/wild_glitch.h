/* =============================================================================
 * FILENAME: wild_glitch.h
 *
 * Ularn: Wild Edition feature "glitch" (docs/WILD_EDITION_PLAN.md, 3.6).
 * Helpers the upstream code calls at the shared insertion points. Every one
 * of them is a no-op (or returns the neutral value) when the WILDF_GLITCH
 * bit is off, and none of them draws a random number then, so a vanilla
 * game's random stream is untouched.
 * =============================================================================
 */
#ifndef ULARN_WILD_GLITCH_H
#define ULARN_WILD_GLITCH_H

/* newscroll(): 1 when this new scroll should be a scroll of segfault. */
int  wild_glitch_scroll(void);

/* read_scroll(SSEGFAULT): reality starts to flicker. */
void wild_glitch_read_scroll(void);

/* oteleport(): a 1% chance that the trip corrupts reality. */
void wild_glitch_teleport(void);

/* GetTile(): the tile to draw for object `obj` (whose normal tile is
 * `tile`) while reality is glitched. Only items change; walls, doors,
 * stairs, traps and dungeon features always keep their tile. */
int  wild_glitch_object_tile(int obj, int tile);

#endif

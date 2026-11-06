/* ularn_android_panel.h - minimap classes and inventory text for the Android
 * minimap / side panel (see "minimap / side panel" in shared_state.h). */
#ifndef ULARN_ANDROID_PANEL_H
#define ULARN_ANDROID_PANEL_H

#include "../shared_state.h"

/* Records the minimap class of map square (x, y), which is now displayed as
 * `tile` (the tile just written to s->tiles[y][x]). */
void android_panel_cell(UlarnShared *s, int x, int y, int monster_shown);

/* Rewrites s->inv_text from the player's inventory. */
void android_panel_inventory(UlarnShared *s);

#endif

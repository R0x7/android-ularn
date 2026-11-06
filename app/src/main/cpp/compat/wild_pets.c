/* =============================================================================
 * FILENAME: wild_pets.c
 *
 * Ularn: Wild Edition feature "pets". See docs/WILD_EDITION_PLAN.md for the
 * design and wild.h for when each hook is called. Everything in this file
 * runs only when the feature's bit is on (wild.c checks before calling).
 * =============================================================================
 */
#include <stdio.h>
#include <string.h>

#include "header.h"
#include "ularn_game.h"
#include "ularn_win.h"
#include "dungeon.h"
#include "player.h"
#include "monster.h"
#include "itm.h"
#include "../shared_state.h"
#include "wild.h"

void wild_pets_init(void) {}
void wild_pets_new_character(void) {}
void wild_pets_turn(void) {}
void wild_pets_new_level(int lev) { (void) lev; }
void wild_pets_leaving_level(int lev) { (void) lev; }
void wild_pets_make_level(int lev) { (void) lev; }
void wild_pets_kill(int monst, int x, int y) { (void) monst; (void) x; (void) y; }
void wild_pets_level_up(int newlevel) { (void) newlevel; }
void wild_pets_take(int obj, int arg) { (void) obj; (void) arg; }
int  wild_pets_object(int obj) { (void) obj; return 0; }
int  wild_pets_wear(int slot) { (void) slot; return 0; }
int  wild_pets_takeoff(void) { return 0; }
int  wild_pets_read(int slot) { (void) slot; return 0; }
int  wild_pets_eat(int slot) { (void) slot; return 0; }
int  wild_pets_dodge(void) { return 0; }
void wild_pets_player_hit(int dmg) { (void) dmg; }
void wild_pets_paint(void) {}

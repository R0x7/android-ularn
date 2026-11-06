/* =============================================================================
 * FILENAME: wild_perks.c
 *
 * Ularn: Wild Edition feature "perks". See docs/WILD_EDITION_PLAN.md for the
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

void wild_perks_init(void) {}
void wild_perks_new_character(void) {}
void wild_perks_turn(void) {}
void wild_perks_new_level(int lev) { (void) lev; }
void wild_perks_leaving_level(int lev) { (void) lev; }
void wild_perks_make_level(int lev) { (void) lev; }
void wild_perks_kill(int monst, int x, int y) { (void) monst; (void) x; (void) y; }
void wild_perks_level_up(int newlevel) { (void) newlevel; }
void wild_perks_take(int obj, int arg) { (void) obj; (void) arg; }
int  wild_perks_object(int obj) { (void) obj; return 0; }
int  wild_perks_wear(int slot) { (void) slot; return 0; }
int  wild_perks_takeoff(void) { return 0; }
int  wild_perks_read(int slot) { (void) slot; return 0; }
int  wild_perks_eat(int slot) { (void) slot; return 0; }
int  wild_perks_dodge(void) { return 0; }
void wild_perks_player_hit(int dmg) { (void) dmg; }
void wild_perks_paint(void) {}

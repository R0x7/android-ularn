/* =============================================================================
 * FILENAME: wild_radio.c
 *
 * Ularn: Wild Edition feature "radio". See docs/WILD_EDITION_PLAN.md for the
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

void wild_radio_init(void) {}
void wild_radio_new_character(void) {}
void wild_radio_turn(void) {}
void wild_radio_new_level(int lev) { (void) lev; }
void wild_radio_leaving_level(int lev) { (void) lev; }
void wild_radio_make_level(int lev) { (void) lev; }
void wild_radio_kill(int monst, int x, int y) { (void) monst; (void) x; (void) y; }
void wild_radio_level_up(int newlevel) { (void) newlevel; }
void wild_radio_take(int obj, int arg) { (void) obj; (void) arg; }
int  wild_radio_object(int obj) { (void) obj; return 0; }
int  wild_radio_wear(int slot) { (void) slot; return 0; }
int  wild_radio_takeoff(void) { return 0; }
int  wild_radio_read(int slot) { (void) slot; return 0; }
int  wild_radio_eat(int slot) { (void) slot; return 0; }
int  wild_radio_dodge(void) { return 0; }
void wild_radio_player_hit(int dmg) { (void) dmg; }
void wild_radio_paint(void) {}

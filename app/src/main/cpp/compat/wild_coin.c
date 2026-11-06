/* =============================================================================
 * FILENAME: wild_coin.c
 *
 * Ularn: Wild Edition feature "coin". See docs/WILD_EDITION_PLAN.md for the
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

void wild_coin_init(void) {}
void wild_coin_new_character(void) {}
void wild_coin_turn(void) {}
void wild_coin_new_level(int lev) { (void) lev; }
void wild_coin_leaving_level(int lev) { (void) lev; }
void wild_coin_make_level(int lev) { (void) lev; }
void wild_coin_kill(int monst, int x, int y) { (void) monst; (void) x; (void) y; }
void wild_coin_level_up(int newlevel) { (void) newlevel; }
void wild_coin_take(int obj, int arg) { (void) obj; (void) arg; }
int  wild_coin_object(int obj) { (void) obj; return 0; }
int  wild_coin_wear(int slot) { (void) slot; return 0; }
int  wild_coin_takeoff(void) { return 0; }
int  wild_coin_read(int slot) { (void) slot; return 0; }
int  wild_coin_eat(int slot) { (void) slot; return 0; }
int  wild_coin_dodge(void) { return 0; }
void wild_coin_player_hit(int dmg) { (void) dmg; }
void wild_coin_paint(void) {}

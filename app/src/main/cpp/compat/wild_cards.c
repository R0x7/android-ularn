/* =============================================================================
 * FILENAME: wild_cards.c
 *
 * Ularn: Wild Edition feature "cards". See docs/WILD_EDITION_PLAN.md for the
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

void wild_cards_init(void) {}
void wild_cards_new_character(void) {}
void wild_cards_turn(void) {}
void wild_cards_new_level(int lev) { (void) lev; }
void wild_cards_leaving_level(int lev) { (void) lev; }
void wild_cards_make_level(int lev) { (void) lev; }
/* Drop odds, in percent. The first kill of a species always drops a card so
 * the album fills at a steady pace; repeats are rarer, and any drop can be a
 * holo. The album itself lives in the app (cards/CardAlbum.kt): the game only
 * announces the drop, it keeps no card state beyond WILD_CARD_LAST. */
#define CARD_REPEAT_PCT 15
#define CARD_HOLO_PCT    5

void wild_cards_kill(int monst, int x, int y)
{
  int holo;
  (void) x; (void) y;
  if (monst <= 0 || monst >= MONST_COUNT) return;
  /* wild_kill() has already counted this kill, so 1 means "first one". */
  if (wild_kill_count(monst) > 1 && rnd(100) > CARD_REPEAT_PCT) return;
  holo = rnd(100) <= CARD_HOLO_PCT;
  c[WILD_CARD_LAST] = monst;   /* saved with the character; handy when debugging */
  wild_event(US_EV_WILD_CARD, monst, holo, 0);
}
void wild_cards_level_up(int newlevel) { (void) newlevel; }
void wild_cards_take(int obj, int arg) { (void) obj; (void) arg; }
int  wild_cards_object(int obj) { (void) obj; return 0; }
int  wild_cards_wear(int slot) { (void) slot; return 0; }
int  wild_cards_takeoff(void) { return 0; }
int  wild_cards_read(int slot) { (void) slot; return 0; }
int  wild_cards_eat(int slot) { (void) slot; return 0; }
int  wild_cards_dodge(void) { return 0; }
void wild_cards_player_hit(int dmg) { (void) dmg; }
void wild_cards_paint(void) {}

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

/* Bonus tracks on cassette tapes. The index is what the app receives in
 * US_EV_WILD_RADIO and must match audio/Songs.kt (Songs.BONUS). */
#define RADIO_TRACKS 3
static const char *track_names[RADIO_TRACKS] =
{
  "Elevator Music",   /* plays in the bank */
  "Surf's Up",        /* plays while fishing */
  "Boss Fight"        /* plays when a demon lord is in sight */
};

/* Where each tape lies: dungeon level 2, level 6, volcano level 2. */
static const int tape_levels[RADIO_TRACKS] = { 2, 6, MAXLEVEL + 1 };

void wild_radio_init(void) {}
void wild_radio_new_character(void) {}
void wild_radio_turn(void) {}
void wild_radio_new_level(int lev) { (void) lev; }
void wild_radio_leaving_level(int lev) { (void) lev; }

void wild_radio_make_level(int lev)
{
  int t, x, y;
  for (t = 0; t < RADIO_TRACKS; t++)
  {
    if (tape_levels[t] != lev) continue;
    /* No free cell on a cramped level: the tape just isn't there. */
    if (!wild_free_cell(&x, &y)) return;
    item[x][y] = OCASSETTE;
    iarg[x][y] = (short) t;
  }
}

void wild_radio_kill(int monst, int x, int y) { (void) monst; (void) x; (void) y; }
void wild_radio_level_up(int newlevel) { (void) newlevel; }

static void unlock(int t)
{
  if (t < 0 || t >= RADIO_TRACKS) t = 0;
  Printf("\nA cassette tape! Side A: '%s'.", track_names[t]);
  wild_extra[WX_RADIO_TRACKS] |= 1 << t;
  wild_event(US_EV_WILD_RADIO, t, 0, 0);
}

/* A tape that reaches the pack some other way (normally stepping on one
 * plays it at once, see below) still unlocks its track; the case stays in
 * the pack as a souvenir. */
void wild_radio_take(int obj, int arg)
{
  if (obj == OCASSETTE) unlock(arg);
}

/* Stepping on a tape: no take/ignore prompt, it goes straight to the radio.
 * Taken off the floor so it can't be unlocked twice. */
int wild_radio_object(int obj)
{
  if (obj != OCASSETTE) return 0;
  unlock(iarg[playerx][playery]);
  forget();
  iarg[playerx][playery] = 0;
  return 1;
}

int  wild_radio_wear(int slot) { (void) slot; return 0; }
int  wild_radio_takeoff(void) { return 0; }
int  wild_radio_read(int slot) { (void) slot; return 0; }
int  wild_radio_eat(int slot) { (void) slot; return 0; }
int  wild_radio_dodge(void) { return 0; }
void wild_radio_player_hit(int dmg) { (void) dmg; }
void wild_radio_paint(void) {}

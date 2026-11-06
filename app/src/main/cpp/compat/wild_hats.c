/* =============================================================================
 * FILENAME: wild_hats.c
 *
 * Ularn: Wild Edition feature "hats". See docs/WILD_EDITION_PLAN.md for the
 * design and wild.h for when each hook is called. Everything in this file
 * runs only when the feature's bit is on (wild.c checks before calling; the
 * helpers in wild_hats.h check themselves).
 *
 * c[WILD_HAT] holds the object id of the hat being worn (0 = none). The hat
 * counts as worn while one of that object is in the pack; the first such
 * slot is the one shown as "(on your head)". Dropping, selling or losing it
 * any other way takes it off: the effects that read the pack (dodge, flying,
 * spell regen, discount, confusion, the sprite) stop at once, and the c[]
 * modifiers (cone AC, party hat CHA) are undone by hats_sync() on the next
 * turn. Those modifiers live in c[], which the save keeps, so a restored game
 * needs nothing re-applied.
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
#include "wild_hats.h"

extern UlarnShared *ularn_shared;

#define HAT_CONE_AC      2    /* traffic cone: +2 AC (via c[MOREDEFENSES]) */
#define HAT_CONE_DODGE  10    /* traffic cone: % of melee blows that swerve */
#define HAT_PARTY_CHA    3    /* party hat: +3 charisma */
#define HAT_OVERLAY_FIRST 392 /* tiles 392..396 = OHATCONE..OHATTINFOIL on the player */

static const char *hat_name[5] =
{
  "traffic cone", "propeller beanie", "wizard hat", "party hat", "tinfoil hat"
};

static int is_hat(int obj)
{
  return obj >= OHATCONE && obj <= OHATTINFOIL;
}

static const char *name_of(int obj)
{
  return is_hat(obj) ? hat_name[obj - OHATCONE] : "hat";
}

static int slot_of(int obj)
{
  int i;
  for (i = 0; i < IVENSIZE; i++)
    if ((unsigned char) iven[i] == obj) return i;
  return -1;
}

/* The hat that is on the player's head right now, or 0. */
static int worn(void)
{
  int h;
  if (!wild_on(WILDF_HATS)) return 0;
  h = (int) c[WILD_HAT];
  if (!is_hat(h) || slot_of(h) < 0) return 0;
  return h;
}

/* Adds (sign 1) or removes (sign -1) the c[] modifiers of a hat. */
static void hat_effects(int obj, int sign)
{
  if (obj == OHATCONE) c[MOREDEFENSES] += sign * HAT_CONE_AC;
  if (obj == OHATPARTY) adjust_ability(CHARISMA, sign * HAT_PARTY_CHA);
  recalc();
  UpdateStatus();
}

static void set_tile(void)
{
  int h = worn();
  if (ularn_shared != NULL)
    ularn_shared->hat_tile = h ? HAT_OVERLAY_FIRST + (h - OHATCONE) : 0;
}

static void take_off(int obj)
{
  c[WILD_HAT] = 0;
  hat_effects(obj, -1);
  set_tile();
  wild_event(US_EV_WILD_HAT, 0, 0, 0);
}

/* The worn hat left the pack (dropped, sold, stolen...): undo its effects. */
static void hats_sync(void)
{
  int h = (int) c[WILD_HAT];
  if (h == 0 || (is_hat(h) && slot_of(h) >= 0)) return;
  take_off(h);
  Printf("\nYour %s is no longer on your head.", name_of(h));
}

/* ---- helpers for the shared insertion points (wild_hats.h) ---- */

int wild_hats_worn_slot(void)
{
  int h = worn();
  return h ? slot_of(h) : -1;
}

int wild_hats_fly(const char *what)
{
  if (worn() != OHATPROPELLER) return 0;
  Printf("\nYour propeller whirs and you fly right over the %s.", what);
  return 1;
}

void wild_hats_regen(void)
{
  if (worn() == OHATWIZARD && c[SPELLS] < c[SPELLMAX]) c[SPELLS]++;
}

void wild_hats_unconfuse(void)
{
  if (c[CONFUSE] == 0 || worn() != OHATTINFOIL) return;
  c[CONFUSE] = 0;
  Print("\nYour tinfoil hat crackles. Nobody is messing with YOUR head.");
}

int wild_hats_hidden(int obj)
{
  return is_hat(obj) && !wild_on(WILDF_HATS);
}

long wild_hats_price(long price)
{
  return worn() == OHATPARTY ? price - price / 10 : price;
}

/* ---- hooks ---- */

void wild_hats_init(void) {}
void wild_hats_new_character(void) {}

void wild_hats_turn(void)
{
  hats_sync();
  wild_hats_unconfuse();
}

void wild_hats_new_level(int lev) { (void) lev; }
void wild_hats_leaving_level(int lev) { (void) lev; }

/* One hat on a quarter of the levels, more often deeper down. */
void wild_hats_make_level(int lev)
{
  int x, y, chance;
  if (lev <= 0) return;   /* not in town */
  chance = 25 + 2 * lev;
  if (chance > 60) chance = 60;
  if (rnd(100) > chance) return;
  if (!wild_free_cell(&x, &y)) return;
  item[x][y] = (char) (OHATCONE + rund(5));
  iarg[x][y] = 0;
}

void wild_hats_kill(int monst, int x, int y) { (void) monst; (void) x; (void) y; }
void wild_hats_level_up(int newlevel) { (void) newlevel; }
void wild_hats_take(int obj, int arg) { (void) obj; (void) arg; }
int  wild_hats_object(int obj) { (void) obj; return 0; }

int wild_hats_wear(int slot)
{
  int obj = (unsigned char) iven[slot];
  if (!is_hat(obj)) return 0;

  hats_sync();
  if (c[WILD_HAT] != 0)
  {
    if (wild_hats_worn_slot() == slot)
      Printf("\nYou are already wearing your %s.", name_of(obj));
    else
      Printf("\nYou already have a %s on your head. One hat at a time!", name_of((int) c[WILD_HAT]));
    return 1;
  }

  c[WILD_HAT] = obj;
  if (c[WIELD] == slot) c[WIELD] = -1;
  hat_effects(obj, 1);
  set_tile();
  wild_event(US_EV_WILD_HAT, obj, 0, 0);

  switch (obj)
  {
    case OHATCONE:
      Print("\nYou put the traffic cone on your head. Monsters will have to swerve.");
      break;
    case OHATPROPELLER:
      Print("\nYou put on the propeller beanie. The propeller spins hopefully.");
      break;
    case OHATWIZARD:
      Print("\nYou put on the pointy wizard hat. You feel extremely magical.");
      break;
    case OHATPARTY:
      Print("\nYou put on the party hat. Everyone loves a party animal!");
      break;
    default:
      Print("\nYou put on the tinfoil hat. Your thoughts are your own again.");
      break;
  }
  return 1;
}

/* 'T': with only a hat on, it comes off. With armour or a shield on as well
 * the player chooses; choosing the armour returns 0 so the upstream code
 * takes off the shield / armour as usual. */
int wild_hats_takeoff(void)
{
  int h, i;

  hats_sync();
  h = worn();
  if (h == 0) return 0;

  if (c[WEAR] != -1 || c[SHIELD] != -1)
  {
    i = get_prompt_input("\nTake off your (h)at or your (a)rmor? ", "ha\033", 1);
    if (i == 'a') return 0;
    if (i != 'h') return 1;
  }

  take_off(h);
  Printf("\nYou take off your %s.", name_of(h));
  return 1;
}

int  wild_hats_read(int slot) { (void) slot; return 0; }
int  wild_hats_eat(int slot) { (void) slot; return 0; }

/* Traffic cone: some blows swerve around it. */
int wild_hats_dodge(void)
{
  return worn() == OHATCONE && rnd(100) <= HAT_CONE_DODGE;
}

void wild_hats_player_hit(int dmg) { (void) dmg; }

void wild_hats_paint(void)
{
  set_tile();
}

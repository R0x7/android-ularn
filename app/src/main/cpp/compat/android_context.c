/* =============================================================================
 * FILENAME: android_context.c
 *
 * Exports what the player could act on (the object underfoot, closed doors
 * next to them) and the spells they know into the shared state, for the
 * app's context-action button and spell picker. Called by the front end each
 * time the game waits for a command, so it only reads game state.
 *
 * In Ularn 1.6.3a there are no "pick up" / "climb" / "enter" commands: the
 * main loop (ularn.c) calls lookforobject() (object.c) at the start of every
 * turn, which prompts about whatever is underfoot. So the app re-triggers that
 * prompt with '.' (ACTION_WAIT, which takes a turn) and answers it.
 * ctx_kind records which answer applies; see shared_state.h.
 * =============================================================================
 */

#include <stdio.h>
#include <string.h>

#include "header.h"
#include "ularn_game.h"
#include "dungeon.h"
#include "player.h"
#include "itm.h"
#include "potion.h"
#include "scroll.h"
#include "spell.h"

#include "../shared_state.h"
#include "wild.h"

extern UlarnShared *ularn_shared;

/* Neighbour offsets, in the order of the movement keys "hjklyubn". */
static const int door_dx[8] = { -1, 0, 0, 1, -1, 1, -1, 1 };
static const int door_dy[8] = { 0, 1, -1, 0, -1, -1, 1, 1 };

/* What lookforobject() would offer for object `obj`. */
static int context_kind(int obj)
{
  switch (obj)
  {
    case ONOTHING:
    case OUNKNOWN:
    case OWALL:
    /* Triggered when stepped on; repeating them only hurts (or kills). */
    case OPIT:
    case OTELEPORTER:
    case OTRAPARROW:
    case ODARTRAP:
    case OTRAPDOOR:
    case OANNIHILATION:
    case OELEVATORUP:
    case OELEVATORDOWN:
    /* Hidden traps: never reveal them. */
    case OTRAPARROWIV:
    case OIVDARTRAP:
    case OIVTRAPDOOR:
    case OIVTELETRAP:
    /* Only met in passing (the game moves you back if it stays shut). */
    case OCLOSEDDOOR:
      return US_CTX_NONE;

    case OSTATUE:
    case OMIRROR:
    case ODEADFOUNTAIN:
    case OOPENDOOR:
      return US_CTX_INSPECT;

    case OGOLDPILE:
    case ODGOLD:
    case OKGOLD:
    case OMAXGOLD:
      return US_CTX_GOLD;

    case OSTAIRSUP:     return US_CTX_STAIRS_UP;
    case OSTAIRSDOWN:   return US_CTX_STAIRS_DOWN;
    case OVOLDOWN:      return US_CTX_SHAFT_DOWN;
    case OVOLUP:        return US_CTX_SHAFT_UP;

    case OSCHOOL:
    case ODNDSTORE:
    case OBANK:
    case OBANK2:
    case OENTRANCE:
    case OHOME:
    case OTRADEPOST:
    case OLRS:
      return US_CTX_ENTER;

    case OPAD:          return US_CTX_PAD;
    case OCHEST:        return US_CTX_CHEST;
    case OFOUNTAIN:     return US_CTX_FOUNTAIN;
    case OALTAR:        return US_CTX_ALTAR;
    case OTHRONE:
    case OTHRONE2:
    case ODEADTHRONE:   return US_CTX_THRONE;
    case OBRASSLAMP:    return US_CTX_LAMP;
    case OVENDING:      /* Wild Edition: buy / kick, or a plain item with the feature off */
      return wild_on(WILDF_VENDING) ? US_CTX_VENDING : US_CTX_TAKE;

    default:
      /* potions, scrolls, books, cookies, drugs and finditem() items:
       * every one of those prompts offers (t) take. */
      if (obj > 0 && obj < OCOUNT) return US_CTX_TAKE;
      return US_CTX_NONE;
  }
}

/* lookforobject() returns without a prompt for these while a monster is
 * adjacent (object.c, the `if (nearbymonst()) return;` cases). */
static int needs_no_monster(int obj)
{
  switch (obj)
  {
    case OALTAR: case OTHRONE: case OTHRONE2: case OORB: case OWWAND:
    case OHANDofFEAR: case OFOUNTAIN: case OSTATUE: case OSCHOOL:
    case OMIRROR: case OBANK: case OBANK2: case ODEADFOUNTAIN:
    case ODNDSTORE: case OTRADEPOST: case OHOME: case OPAD: case OLRS:
      return 1;
    default:
      return 0;
  }
}

void android_update_context(void)
{
  UlarnShared *s = ularn_shared;
  int obj, kind, flags = 0, doors = 0, i, x, y;
  const char *name = "";
  char buf[US_CTX_NAME];
  int arg;

  if (!s) return;

  /* ---- what's underfoot */
  obj = ONOTHING;
  if (playerx >= 0 && playerx < MAXX && playery >= 0 && playery < MAXY)
    obj = (unsigned char) item[playerx][playery];
  kind = context_kind(obj);
  if (kind == US_CTX_NONE) obj = ONOTHING;   /* don't leak hidden traps */

  if (kind != US_CTX_NONE)
  {
    name = objectname[obj];
    arg = iarg[playerx][playery];
    /* name identified potions and scrolls: "a potion of healing" */
    if (obj == OPOTION && arg >= 0 && arg < MAXPOTION && potionknown[arg])
    {
      snprintf(buf, sizeof(buf), "a potion of%s", potionname[arg]);
      name = buf;
    }
    else if (obj == OSCROLL && arg >= 0 && arg < MAXSCROLL && scrollknown[arg])
    {
      snprintf(buf, sizeof(buf), "a scroll of%s", scrollname[arg]);
      name = buf;
    }
  }
  if (name == NULL) name = "";

  if (enhance_interface) flags |= US_CTXF_ENHANCED;
  /* lookforobject() does nothing while time is stopped. */
  if (c[TIMESTOP]) flags |= US_CTXF_BLOCKED | US_CTXF_TIMESTOP;
  else if (needs_no_monster(obj) && nearbymonst()) flags |= US_CTXF_BLOCKED;

  /* ---- closed doors the player knows about, next to them */
  for (i = 0; i < 8; i++)
  {
    x = playerx + door_dx[i];
    y = playery + door_dy[i];
    if (x < 0 || x >= MAXX || y < 0 || y >= MAXY) continue;
    if (know[x][y] == OCLOSEDDOOR && item[x][y] == OCLOSEDDOOR) doors |= 1 << i;
  }

  s->ctx_object = obj;
  s->ctx_kind = kind;
  s->ctx_flags = flags;
  s->ctx_doors = doors;
  strncpy(s->ctx_name, name, sizeof(s->ctx_name) - 1);
  s->ctx_name[sizeof(s->ctx_name) - 1] = 0;

  /* ---- spells known this run (codes are fixed, but cheap to copy) */
  s->spell_count = SPELL_COUNT < US_SPELLS ? SPELL_COUNT : US_SPELLS;
  for (i = 0; i < s->spell_count; i++)
  {
    s->spell_known[i] = spelknow[i] != 0;
    strncpy(s->spell_codes[i], spelcode[i], sizeof(s->spell_codes[i]) - 1);
    s->spell_codes[i][sizeof(s->spell_codes[i]) - 1] = 0;
  }
  s->spells = c[SPELLS];
  s->spellmax = c[SPELLMAX];
}

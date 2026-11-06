/* =============================================================================
 * FILENAME: ularn_android_panel.c
 *
 * Extra display state for the Android app's minimap and side panel:
 *
 *  - a class per map square (floor, wall, stairs, shop, item, monster...),
 *    recorded by the front end every time it sets that square's tile, so the
 *    minimap shows exactly what the map shows and nothing the player hasn't
 *    seen;
 *  - the inventory, one line per slot, worded like the game's own inventory
 *    list (show3() / show1() in show.c) but without printing anything.
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

#include "ularn_android_panel.h"
#include "wild_hats.h"

static int object_class(int obj)
{
  switch (obj)
  {
    case OUNKNOWN:
      return US_MM_UNKNOWN;

    case OWALL:
      return US_MM_WALL;

    case OOPENDOOR:
    case OCLOSEDDOOR:
      return US_MM_DOOR;

    case OSTAIRSUP:
    case OELEVATORUP:
      return US_MM_UP;

    case OSTAIRSDOWN:
    case OELEVATORDOWN:
      return US_MM_DOWN;

    case OVOLDOWN:
    case OVOLUP:
      return US_MM_VOLCANO;

    case OENTRANCE:
      return US_MM_ENTRANCE;

    case OSCHOOL: case ODNDSTORE: case OBANK2: case OBANK: case OHOME:
    case OTRADEPOST: case OLRS: case OPAD:
      return US_MM_BUILDING;

    case OFOUNTAIN:
    case ODEADFOUNTAIN:
      return US_MM_FOUNTAIN;

    case OALTAR:
      return US_MM_ALTAR;

    case OTHRONE:
    case OTHRONE2:
    case ODEADTHRONE:
      return US_MM_THRONE;

    /* Visible traps only: the invisible ones are drawn as plain floor. */
    case OPIT: case OTELEPORTER: case OTRAPARROW: case ODARTRAP:
    case OTRAPDOOR: case OANNIHILATION:
      return US_MM_TRAP;

    case OSTATUE:
    case OMIRROR:
      return US_MM_FEATURE;

    case OGOLDPILE: case ODGOLD: case OKGOLD: case OMAXGOLD:
      return US_MM_GOLD;

    case ONOTHING:
    case OTRAPARROWIV: case OIVDARTRAP: case OIVTRAPDOOR: case OIVTELETRAP:
      return US_MM_FLOOR;

    default:
      /* armour, weapons, rings, magic items, gems, drugs, chests... */
      if (obj > ONOTHING && obj < OCOUNT) return US_MM_ITEM;
      return US_MM_FLOOR;
  }
}

void android_panel_cell(UlarnShared *s, int x, int y, int monster_shown)
{
  int cls;

  if (s == NULL || x < 0 || x >= MAXX || y < 0 || y >= MAXY) return;

  /* What the player believes is here: the same thing GetTile() draws. */
  cls = object_class((unsigned char) know[x][y]);

  /* GetTile() decided a monster is visible here. */
  if (cls != US_MM_UNKNOWN && monster_shown)
    cls |= US_MM_MONSTER;

  s->mm_cat[y][x] = (uint8_t) cls;
}

/* One inventory line, worded as show3() / show1() do. */
static void describe(int idx, char *out, size_t len)
{
  int obj = (unsigned char) iven[idx];
  int arg = ivenarg[idx];
  const char *name = objectname[obj];
  const char *of = NULL;
  size_t n;

  if (name == NULL) name = "";

  if (obj == OPOTION)
  {
    if (arg >= 0 && arg < MAXPOTION && potionknown[arg] &&
        potionname[arg] != NULL && potionname[arg][0] != 0)
      of = potionname[arg];
  }
  else if (obj == OSCROLL)
  {
    if (arg >= 0 && arg < MAXSCROLL && scrollknown[arg] &&
        scrollname[arg] != NULL && scrollname[arg][0] != 0)
      of = scrollname[arg];
  }

  /* The potion / scroll names start with a space (" healing"). */
  if (of != NULL) snprintf(out, len, "%s of%s", name, of);
  else snprintf(out, len, "%s", name);

  switch (obj)
  {
    case OPOTION: case OSCROLL:
    case OLARNEYE: case OBOOK: case OSPIRITSCARAB: case ODIAMOND: case ORUBY:
    case OCUBEofUNDEAD: case OEMERALD: case OCHEST: case OCOOKIE:
    case OSAPPHIRE: case OORB: case OHANDofFEAR: case OBRASSLAMP: case OURN:
    case OWWAND: case OSPHTALISMAN: case ONOTHEFT:
      break;

    default:
      if (obj >= OWILD_FIRST) break;   /* Wild Edition objects: no plusses */
      /* show_plusses() */
      n = strlen(out);
      if (arg > 0 || wizard) snprintf(out + n, len - n, " +%d", arg);
      else if (arg < 0) snprintf(out + n, len - n, " %d", arg);
      break;
  }

  n = strlen(out);
  if (c[WIELD] == idx)
    snprintf(out + n, len - n, " (weapon in hand)");
  n = strlen(out);
  if ((c[WEAR] == idx) || (c[SHIELD] == idx))
    snprintf(out + n, len - n, " (being worn)");
  n = strlen(out);
  if (wild_hats_worn_slot() == idx)   /* Wild Edition */
    snprintf(out + n, len - n, " (on your head)");
}

void android_panel_inventory(UlarnShared *s)
{
  char line[US_INV_LEN];
  int i, obj;

  if (s == NULL) return;

  for (i = 0; i < US_INV_SLOTS && i < IVENSIZE; i++)
  {
    obj = (unsigned char) iven[i];
    if (obj <= ONOTHING || obj >= OCOUNT) line[0] = 0;
    else describe(i, line, sizeof(line));

    /* Only touch shared memory when the text actually changes. */
    if (strncmp(s->inv_text[i], line, US_INV_LEN) != 0)
    {
      strncpy(s->inv_text[i], line, US_INV_LEN - 1);
      s->inv_text[i][US_INV_LEN - 1] = 0;
    }
  }
}

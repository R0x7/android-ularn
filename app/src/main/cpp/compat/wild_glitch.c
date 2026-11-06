/* =============================================================================
 * FILENAME: wild_glitch.c
 *
 * Ularn: Wild Edition feature "glitch" (the Glitch Realm). See
 * docs/WILD_EDITION_PLAN.md (3.6) for the design and wild.h for when each
 * hook is called. Everything in this file runs only when the feature's bit
 * is on (wild.c checks before calling; the helpers in wild_glitch.h check
 * themselves).
 *
 * A scroll of segfault (SSEGFAULT, made by newscroll() only when the bit is
 * on) or, rarely, a teleport sets c[WILD_GLITCH] to the turns of corruption
 * left. While it is > 0:
 *   - the app draws the map through a corruption pass (S->glitch, wild.c);
 *   - items are drawn as other items (wild_glitch_object_tile(), GetTile());
 *   - the ordinary monsters' names are shuffled, so messages lie. The
 *     shuffle is a function of wild_extra[WX_GLITCH_SEED] alone and is
 *     re-applied from the untouched original table, so a restored save
 *     shows the same names and nothing is lost if we stop half way;
 *   - a null pointer, spawned when it starts, hops about erratically.
 * c[WILD_GLITCH] and the seed are saved with the game (c[] and the trailer).
 * =============================================================================
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "header.h"
#include "ularn_game.h"
#include "ularn_win.h"
#include "dungeon.h"
#include "player.h"
#include "monster.h"
#include "itm.h"
#include "scroll.h"
#include "../shared_state.h"
#include "wild.h"
#include "wild_glitch.h"

#define GLITCH_MIN_TURNS   60   /* a glitch lasts 60 + rnd(60) turns */
#define GLITCH_RAND_TURNS  60
#define GLITCH_SCROLL_ODDS 40   /* 1 in 40 new scrolls is a segfault */
#define GLITCH_TELE_ODDS  100   /* 1 in 100 teleports corrupts reality */
#define GLITCH_RESHUFFLE   10   /* names get a new shuffle every 10 turns */
#define NULLPTR_HOP         3   /* the null pointer hops up to 3 cells */

/* Ordinary monsters whose names take part in the shuffle: the upstream ones
 * (the Wild Edition monsters keep their names, so the null pointer is always
 * recognisable in its own death message). */
#define NAME_FIRST 1
#define NAME_LAST  (NULLPOINTER - 1)

static char *orig_name[MONST_COUNT];   /* the table as the game built it */
static int   names_saved;
static int   names_seed;               /* seed of the shuffle in place; 0 = none */

/* ---- monster names ---- */

static void names_restore(void)
{
  int i;
  if (!names_saved) return;
  for (i = 0; i < MONST_COUNT; i++) monster[i].name = orig_name[i];
  names_seed = 0;
}

/* A private generator so shuffling names (also done while painting) never
 * disturbs the game's own random numbers. */
static unsigned int lcg_next(unsigned int *s)
{
  *s = *s * 1103515245u + 12345u;
  return (*s >> 16) & 0x7fff;
}

static void names_shuffle(int seed)
{
  unsigned int s = (unsigned int) seed;
  int i, j;
  char *t;
  names_restore();
  for (i = NAME_LAST; i > NAME_FIRST; i--)
  {
    j = NAME_FIRST + (int) (lcg_next(&s) % (unsigned int) (i - NAME_FIRST + 1));
    t = monster[i].name;
    monster[i].name = monster[j].name;
    monster[j].name = t;
  }
  names_seed = seed;
}

/* Makes the name table match c[WILD_GLITCH] and the saved seed. */
static void names_sync(void)
{
  int seed = wild_extra[WX_GLITCH_SEED];
  if (c[WILD_GLITCH] > 0 && seed != 0)
  {
    if (names_seed != seed) names_shuffle(seed);
  }
  else if (names_seed != 0)
    names_restore();
}

/* The scroll only has a name (and so only shows in identification lists,
 * the wizard's scroll pile...) while the feature is on. */
static void scroll_sync(void)
{
  scrollname[SSEGFAULT] = " segfault";
}

/* ---- the null pointer ---- */

static void move_monster(int x, int y, int nx, int ny)
{
  mitem[nx][ny] = mitem[x][y];
  hitp[nx][ny] = hitp[x][y];
  stealth[nx][ny] = stealth[x][y] | STEALTH_AWAKE;
  mitem[x][y].mon = MONST_NONE;
  mitem[x][y].n = 0;
  hitp[x][y] = 0;
  stealth[x][y] = 0;
  /* redraw both squares, but never reveal one the player hasn't seen */
  if (know[x][y] != OUNKNOWN) show1cell(x, y);
  if (know[nx][ny] != OUNKNOWN) show1cell(nx, ny);
}

/* A free cell near (x,y), `min`..`max` cells away (Chebyshev); 0 if none. */
static int cell_near(int x, int y, int min, int max, int *ox, int *oy)
{
  int tries, nx, ny, d;
  for (tries = 0; tries < 40; tries++)
  {
    nx = x + rnd(2 * max + 1) - max - 1;
    ny = y + rnd(2 * max + 1) - max - 1;
    d = abs(nx - x) > abs(ny - y) ? abs(nx - x) : abs(ny - y);
    if (d < min || nx < 0 || ny < 0 || nx >= MAXX || ny >= MAXY) continue;
    if (nx == playerx && ny == playery) continue;
    if (!cgood(nx, ny, 0, 1)) continue;
    *ox = nx; *oy = ny;
    return 1;
  }
  return 0;
}

static void spawn_null_pointer(void)
{
  int x, y;
  if (monster[NULLPOINTER].flags & FL_GENOCIDED) return;
  if (!cell_near(playerx, playery, 2, 5, &x, &y) && !wild_free_cell(&x, &y)) return;
  mitem[x][y].mon = NULLPOINTER;
  mitem[x][y].n = 0;
  hitp[x][y] = monster[NULLPOINTER].hitpoints;
  stealth[x][y] = STEALTH_AWAKE;
  Print("\nSomething that should not exist blinks into being.");
}

/* Every other turn each null pointer on the level jumps to a nearby cell. */
static void null_pointers_hop(void)
{
  int px[8], py[8], n = 0, x, y, i, nx, ny;
  if (c[TIMESTOP] || (gtime & 1)) return;
  for (x = 0; x < MAXX && n < 8; x++)
    for (y = 0; y < MAXY && n < 8; y++)
      if (mitem[x][y].mon == NULLPOINTER) { px[n] = x; py[n] = y; n++; }
  for (i = 0; i < n; i++)
    if (cell_near(px[i], py[i], 1, NULLPTR_HOP, &nx, &ny))
      move_monster(px[i], py[i], nx, ny);
}

/* ---- start / end ---- */

static void glitch_start(int turns)
{
  int fresh = c[WILD_GLITCH] <= 0;
  if (c[WILD_GLITCH] < turns) c[WILD_GLITCH] = turns;
  if (!fresh) return;
  wild_extra[WX_GLITCH_SEED] = rnd(30000);
  names_sync();
  wild_event(US_EV_WILD_GLITCH, 1, (int) c[WILD_GLITCH], 0);
  spawn_null_pointer();
  draws(0, MAXX, 0, MAXY);   /* items change their looks at once */
}

static void glitch_end(void)
{
  c[WILD_GLITCH] = 0;
  wild_extra[WX_GLITCH_SEED] = 0;
  names_restore();
  Print("\nReality reasserts itself. You feel vaguely embarrassed.");
  wild_event(US_EV_WILD_GLITCH, 0, 0, 0);
  draws(0, MAXX, 0, MAXY);   /* and back */
}

/* ---- helpers for the shared insertion points (wild_glitch.h) ---- */

int wild_glitch_scroll(void)
{
  if (!wild_on(WILDF_GLITCH)) return 0;
  return rnd(GLITCH_SCROLL_ODDS) == 1;
}

void wild_glitch_read_scroll(void)
{
  if (!wild_on(WILDF_GLITCH)) return;
  if (c[WILD_GLITCH] > 0)
    Print("\nSegmentation fault (core dumped)... again. Reality flickers harder.");
  else
    Print("\nSegmentation fault (core dumped)... just kidding. Reality flickers.");
  glitch_start(GLITCH_MIN_TURNS + rnd(GLITCH_RAND_TURNS));
}

void wild_glitch_teleport(void)
{
  if (!wild_on(WILDF_GLITCH)) return;
  if (c[WILD_GLITCH] > 0 || rnd(GLITCH_TELE_ODDS) != 1) return;
  Print("\nYou arrive slightly out of bounds. Reality flickers.");
  glitch_start(GLITCH_MIN_TURNS + rnd(GLITCH_RAND_TURNS));
}

/* Items that can lie about what they are (never features, traps or
 * buildings: the map must stay navigable). */
static int is_item(int obj)
{
  return (obj >= OGOLDPILE && obj <= OLIFEPRESERVER) ||
         (obj >= ODIAMOND && obj <= OSAPPHIRE) ||
         obj == OCOOKIE ||
         (obj >= OSPEED && obj <= OCOKE) ||
         (obj >= OFIZZ && obj <= OCASSETTE);
}

int wild_glitch_object_tile(int obj, int tile)
{
  static int items[OCOUNT];
  static int n_items;
  int i, pick;
  if (!wild_on(WILDF_GLITCH) || c[WILD_GLITCH] <= 0 || !is_item(obj)) return tile;
  if (n_items == 0)
    for (i = 0; i < OCOUNT; i++)
      if (is_item(i)) items[n_items++] = i;
  /* Changes every 5 turns; a different disguise per kind of object. */
  pick = items[(int) (((long) obj * 7 + gtime / 5) % n_items)];
  if (pick == obj) pick = items[(int) (((long) obj * 7 + gtime / 5 + 1) % n_items)];
  return objtilelist[pick];
}

/* ---- hooks ---- */

void wild_glitch_init(void)
{
  int i;
  /* Called once per process, before the mask is known: the scroll has no
   * name until the feature turns out to be on. */
  for (i = 0; i < MONST_COUNT; i++) orig_name[i] = monster[i].name;
  names_saved = 1;
  names_seed = 0;
}

void wild_glitch_new_character(void)
{
  names_restore();
  scroll_sync();
}

void wild_glitch_turn(void)
{
  scroll_sync();
  if (c[WILD_GLITCH] > 0)
  {
    c[WILD_GLITCH]--;
    if (c[WILD_GLITCH] == 0)
      glitch_end();
    else
    {
      if (c[WILD_GLITCH] % GLITCH_RESHUFFLE == 0)
      {
        /* a new lie every so often; the seed never becomes 0 (= no shuffle) */
        wild_extra[WX_GLITCH_SEED] = wild_extra[WX_GLITCH_SEED] % 29999 + 1 + rnd(30000);
      }
      /* the front end repaints cells one by one as they change; disguised
       * items change every 5 turns (wild_glitch_object_tile()) */
      if (gtime % 5 == 0) draws(0, MAXX, 0, MAXY);
    }
  }
  names_sync();
  null_pointers_hop();
}

void wild_glitch_new_level(int lev) { (void) lev; scroll_sync(); names_sync(); }
void wild_glitch_leaving_level(int lev) { (void) lev; }
void wild_glitch_make_level(int lev) { (void) lev; }

void wild_glitch_kill(int monst, int x, int y)
{
  int i;
  if (monst != NULLPOINTER) return;
  Print("\nThe null pointer dereferences. Garbage spills everywhere.");
  for (i = 0; i < 3; i++) something(x, y, level);
  createitem(x, y, OGOLDPILE, 250 + rnd(250) * (level + 1));
}

void wild_glitch_level_up(int newlevel) { (void) newlevel; }
void wild_glitch_take(int obj, int arg) { (void) obj; (void) arg; }
int  wild_glitch_object(int obj) { (void) obj; return 0; }
int  wild_glitch_wear(int slot) { (void) slot; return 0; }
int  wild_glitch_takeoff(void) { return 0; }
int  wild_glitch_read(int slot) { (void) slot; return 0; }
int  wild_glitch_eat(int slot) { (void) slot; return 0; }
int  wild_glitch_dodge(void) { return 0; }
void wild_glitch_player_hit(int dmg) { (void) dmg; }

/* Runs on every repaint, so a restored glitched game gets its scroll name
 * and shuffled monster names back before anything is shown. */
void wild_glitch_paint(void)
{
  scroll_sync();
  names_sync();
}

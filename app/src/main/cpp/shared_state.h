/* shared_state.h - display state shared between the game process and the app.
 *
 * The spawner maps this struct with MAP_SHARED|MAP_ANONYMOUS before fork(),
 * so the game child writes into it and the app reads the same pages through
 * a direct ByteBuffer. Field offsets are reported to Kotlin at runtime via
 * NativeBridge.layout(), so nothing has to be mirrored by hand.
 *
 * Concurrency: the game is the only writer. It bumps `seq` after each batch
 * of changes and pokes the notify pipe; the app redraws from whatever is in
 * the struct. A torn read just shows a half-updated frame for one redraw.
 */
#ifndef ULARN_SHARED_STATE_H
#define ULARN_SHARED_STATE_H

#include <stdint.h>

#define US_MAGIC    0x554C524E   /* 'ULRN' */
#define US_VERSION  1

#define US_MAP_W    67           /* MAXX */
#define US_MAP_H    17           /* MAXY */
#define US_LINE_LEN 80
#define US_MSG_LINES  5
#define US_TEXT_LINES 25
#define US_EFFECTS  17

/* display_mode */
#define US_DISPLAY_MAP  0
#define US_DISPLAY_TEXT 1

/* input_mode: what the game is currently waiting for */
#define US_INPUT_BUSY     0  /* not waiting (thinking / animating) */
#define US_INPUT_COMMAND  1  /* get_normal_input */
#define US_INPUT_PROMPT   2  /* get_prompt_input: one of `answers` */
#define US_INPUT_DIR      3  /* get_dir_input */
#define US_INPUT_NUMBER   4  /* get_num_input */
#define US_INPUT_PASSWORD 5  /* get_password_input */

/* text format codes (FormatType) */
#define US_FMT_NORMAL    0
#define US_FMT_STANDOUT  1
#define US_FMT_STANDOUT2 2
#define US_FMT_STANDOUT3 3
#define US_FMT_BOLD      4
#define US_FMT_INVERSE   5

typedef struct UlarnShared
{
  int32_t magic;
  int32_t version;
  volatile int32_t seq;

  int32_t display_mode;
  int32_t input_mode;
  char    answers[64];          /* acceptable keys for US_INPUT_PROMPT */
  int32_t caret;                /* 1 = show caret at cursor */
  int32_t cursor_x;             /* 1-based, in the active text area */
  int32_t cursor_y;
  int32_t beep_count;

  /* map */
  int32_t player_x;
  int32_t player_y;
  int32_t player_cursor;        /* 1 = draw the cursor tile over the player */
  int16_t tiles[US_MAP_H][US_MAP_W];    /* tile index into ularn_gfx */
  int16_t overlay[US_MAP_H][US_MAP_W];  /* -1 = none (spell sparkle etc.) */

  /* status */
  char    status1[96];
  char    status2[96];
  int32_t hp, hpmax, spells, spellmax, gold, plevel, ac, wc;
  int32_t exp;
  int32_t dlevel;               /* dungeon level index */
  char    level_name[8];        /* "H", "1".."10", "V1".. */
  int32_t effects;              /* bit i set = effect i active (see front end) */
  int32_t class_num;            /* character class 0..7, -1 = not chosen yet */
  int32_t sex;                  /* 0 female, 1 male */

  /* message area (map mode) and full text page (text mode) */
  char    msg_chr[US_MSG_LINES][US_LINE_LEN + 1];
  uint8_t msg_fmt[US_MSG_LINES][US_LINE_LEN];
  char    text_chr[US_TEXT_LINES][US_LINE_LEN + 1];
  uint8_t text_fmt[US_TEXT_LINES][US_LINE_LEN];
} UlarnShared;

#endif

/* web_main.c - runs the game in a browser (Emscripten + Asyncify).
 *
 * The web counterpart of app/src/main/cpp/spawn.c + ularn_jni.c. The game
 * sources and the Android front end (compat/ularn_winandroid.c) are compiled
 * unchanged; web_shim.h routes their read() of the key pipe and usleep()
 * here (ularn_web_read / ularn_web_usleep), which yield with emscripten_sleep.
 *
 *  - display state: the same UlarnShared struct, here a static in wasm
 *    memory; the page reads it through HEAP views (offsets: ularn_web_layout).
 *  - input: a small key ring the page fills with ularn_web_push_key().
 *  - new game: the page reloads, so the game's globals always start pristine
 *    (the job fork() does on Android).
 *  - files: /ularn is IndexedDB-backed (IDBFS), mounted by the page before
 *    main() runs; the data files are copied there from the preloaded /data.
 *
 * The page calls ularn_web_run() itself (there is no main()).
 * This file is compiled WITHOUT compat/android_shim.h, so main/exit are real.
 */
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <emscripten.h>

#include "shared_state.h"

extern int ularn_main(int argc, char **argv);
extern UlarnShared *ularn_shared;

static UlarnShared state;

/* ---- input ---- */
#define KEYQ 256
static unsigned char keyq[KEYQ];
static int kq_head, kq_tail;

EMSCRIPTEN_KEEPALIVE void ularn_web_push_key(int ch)
{
  int next = (kq_tail + 1) % KEYQ;
  if (next == kq_head) return;          /* full: drop */
  keyq[kq_tail] = (unsigned char) ch;
  kq_tail = next;
}

static int ularn_web_pop_key(void)
{
  int ch;
  if (kq_head == kq_tail) return -1;
  ch = keyq[kq_head];
  kq_head = (kq_head + 1) % KEYQ;
  return ch;
}

/* compat/ularn_winandroid.c uses arc4random() (Bionic) for the run id;
 * this Emscripten libc has none. */
EM_JS(unsigned int, web_random32, (void), {
  var a = new Uint32Array(1); crypto.getRandomValues(a); return a[0];
});
unsigned int arc4random(void) { return web_random32(); }

/* The key "pipe": read_key() reads ularn_input_fd, which here is a made-up
 * fd number; any other fd is a real file. */
#define WEB_INPUT_FD 1000
extern int ularn_input_fd;

ssize_t ularn_web_read(int fd, void *buf, size_t n)
{
  int k;
  if (fd != WEB_INPUT_FD) return read(fd, buf, n);
  if (n == 0) return 0;
  while ((k = ularn_web_pop_key()) < 0) emscripten_sleep(16);
  *(unsigned char *) buf = (unsigned char) k;
  return 1;
}

int ularn_web_usleep(unsigned int usec)
{
  emscripten_sleep(usec / 1000);
  return 0;
}

EMSCRIPTEN_KEEPALIVE UlarnShared *ularn_web_state(void) { return &state; }

/* Field offsets as JSON, so the page never mirrors the struct by hand. */
EMSCRIPTEN_KEEPALIVE const char *ularn_web_layout(void)
{
  static char buf[4096];
#define F(name) "\"" #name "\":%d,"
#define O(name) (int) offsetof(UlarnShared, name)
  snprintf(buf, sizeof buf,
           "{" F(seq) F(display_mode) F(input_mode) F(answers) F(caret) F(cursor_x) F(cursor_y)
           F(player_x) F(player_y) F(player_cursor) F(tiles) F(overlay)
           F(status1) F(status2) F(hp) F(hpmax) F(spells) F(spellmax) F(gold) F(plevel) F(ac) F(wc)
           F(exp) F(level_name) F(effects) F(stats) F(level_title) F(game_time)
           F(msg_chr) F(msg_fmt) F(text_chr) F(text_fmt) F(mm_cat) F(inv_text)
           F(end_state) F(end_score) F(end_reason) F(hat_tile) F(pet_x) F(pet_y) F(weather)
           F(run_id) F(kills) F(class_num) F(hardness) F(wild_flags) F(dlevel)
           "\"size\":%d,\"map_w\":%d,\"map_h\":%d,\"line\":%d,\"msg_lines\":%d,\"text_lines\":%d}",
           O(seq), O(display_mode), O(input_mode), O(answers), O(caret), O(cursor_x), O(cursor_y),
           O(player_x), O(player_y), O(player_cursor), O(tiles), O(overlay),
           O(status1), O(status2), O(hp), O(hpmax), O(spells), O(spellmax), O(gold), O(plevel), O(ac), O(wc),
           O(exp), O(level_name), O(effects), O(stats), O(level_title), O(game_time),
           O(msg_chr), O(msg_fmt), O(text_chr), O(text_fmt), O(mm_cat), O(inv_text),
           O(end_state), O(end_score), O(end_reason), O(hat_tile), O(pet_x), O(pet_y), O(weather),
           O(run_id), O(kills), O(class_num), O(hardness), O(wild_flags), O(dlevel),
           (int) sizeof(UlarnShared), US_MAP_W, US_MAP_H, US_LINE_LEN, US_MSG_LINES, US_TEXT_LINES);
#undef F
#undef O
  return buf;
}

/* 1 if this run must stay off the shared leaderboard: wizard mode or a
 * cheat, the same runs Ularn keeps off its own scoreboard (scores.c). */
extern char wizard, cheat;
EMSCRIPTEN_KEEPALIVE int ularn_web_unranked(void) { return wizard || cheat; }

/* ---- exit ---- */
void ularn_exit(int status)
{
  fflush(NULL);
  /* Save files to IndexedDB, then let the page offer a new game. The game
   * must not continue, so wait here for ever (the page reloads). */
  EM_ASM({ if (Module.onGameExit) Module.onGameExit($0); }, status);
  for (;;) emscripten_sleep(1000);
}

static void copy_file(const char *from, const char *to)
{
  char buf[8192];
  size_t n;
  FILE *in = fopen(from, "rb"), *out;
  if (!in) return;
  out = fopen(to, "wb");
  if (out)
  {
    while ((n = fread(buf, 1, sizeof buf, in)) > 0) fwrite(buf, 1, n, out);
    fclose(out);
  }
  fclose(in);
}

/* Started by the page (ccall, async): wild = Wild Edition feature mask,
 * restore_ckp = 1 to restore the checkpoint (-r). Never returns. */
EMSCRIPTEN_KEEPALIVE void ularn_web_run(int wild, int restore_ckp)
{
  char *gargv[4], wbuf[16];
  int gargc = 0, i;
  static const char *data[] = { "Uhelp", "Umaps", "Ufortune" };

  memset(&state, 0, sizeof state);
  state.magic = US_MAGIC;
  state.version = US_VERSION;
  state.class_num = -1;
  ularn_shared = &state;
  ularn_input_fd = WEB_INPUT_FD;

  gargv[gargc++] = "ularn";
  if (restore_ckp) gargv[gargc++] = "-r";
  gargv[gargc] = NULL;
  snprintf(wbuf, sizeof wbuf, "%d", wild);
  setenv("ULARN_WILD", wbuf, 1);

  mkdir("/ularn", 0777);
  mkdir("/ularn/home", 0777);
  for (i = 0; i < 3; i++)
  {
    char from[64], to[64];
    snprintf(from, sizeof from, "/data/%s", data[i]);
    snprintf(to, sizeof to, "/ularn/%s", data[i]);
    copy_file(from, to);
  }
  setenv("HOME", "/ularn/home", 1);
  setenv("USER", "player", 1);
  setenv("LOGNAME", "player", 1);
  if (chdir("/ularn") != 0) perror("chdir");

  ularn_exit(ularn_main(gargc, gargv));
}

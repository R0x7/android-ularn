/* Force-included (-include) into every game source file.
 *
 * The game runs in a process forked from the app (no exec), so:
 *  - main() is renamed so the spawner can call it directly;
 *  - exit() is redirected to ularn_exit(), which flushes stdio and calls
 *    _exit(). Calling the real exit() in a forked app process would run the
 *    parent's atexit handlers / C++ static destructors (ART, etc.).
 */
#ifndef ULARN_ANDROID_SHIM_H
#define ULARN_ANDROID_SHIM_H

#include <stdlib.h>   /* pull in the real declaration before the macro */

void ularn_exit(int status) __attribute__((noreturn));
#define exit(s) ularn_exit(s)
#define main ularn_main

/* Not available (or API-level gated) in bionic; see bionic_compat.c. */
#define sigpause ularn_sigpause
#define setpgrp  ularn_setpgrp
int ularn_sigpause(int mask);
int ularn_setpgrp(void);

/* Termcap API provided by termcap_vt100.c (no ncurses on Android). */
int   tgetent(char *bp, const char *name);
int   tgetflag(const char *id);
int   tgetnum(const char *id);
char *tgetstr(const char *id, char **area);
char *tgoto(const char *cap, int col, int row);
int   tputs(const char *str, int affcnt, int (*putc_fn)(int));

#endif

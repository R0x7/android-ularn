/* Stand-in for ncurses' <term.h>: the NDK has no curses. The termcap
 * functions are implemented by termcap_vt100.c and declared in
 * android_shim.h (force-included). */
#ifndef ULARN_COMPAT_TERM_H
#define ULARN_COMPAT_TERM_H
int   tgetent(char *bp, const char *name);
int   tgetflag(const char *id);
int   tgetnum(const char *id);
char *tgetstr(const char *id, char **area);
char *tgoto(const char *cap, int col, int row);
int   tputs(const char *str, int affcnt, int (*putc_fn)(int));
#endif

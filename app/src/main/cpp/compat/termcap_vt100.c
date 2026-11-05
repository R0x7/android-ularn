/* Minimal built-in termcap: always describes a VT100, which is exactly what
 * the app's terminal view emulates. Replaces ncurses/termcap on Android. */
#include <stdio.h>
#include <string.h>

short ospeed = 0;
char  PC = 0;
/* BC and UP are defined by the game (io.c) */

static const struct { const char *id, *val; } caps[] = {
    { "so", "\033[7m" },      /* standout on  */
    { "se", "\033[m"  },      /* standout off */
    { "md", "\033[1m" },      /* bold */
    { "me", "\033[m"  },
    { "cm", "\033[%i%d;%dH" },/* cursor motion (row;col, 1-based) */
    { "ce", "\033[K"  },      /* clear to end of line */
    { "cd", "\033[J"  },      /* clear to end of display */
    { "cl", "\033[H\033[J" }, /* clear screen */
    { "al", "\033[L"  },      /* insert line */
    { "dl", "\033[M"  },      /* delete line */
    { "up", "\033[A"  },
    { "ho", "\033[H"  },
    { "le", "\b"      },
    { "bc", "\b"      },
    { 0, 0 }
};

int tgetent(char *bp, const char *name) { (void)bp; (void)name; return 1; }

int tgetflag(const char *id) { return strcmp(id, "bs") == 0 || strcmp(id, "am") == 0; }

int tgetnum(const char *id)
{
    if (strcmp(id, "co") == 0) return 80;
    if (strcmp(id, "li") == 0) return 24;
    return -1;
}

char *tgetstr(const char *id, char **area)
{
    int i;
    for (i = 0; caps[i].id; i++) {
        if (strcmp(caps[i].id, id) == 0) {
            char *out = *area;
            size_t n = strlen(caps[i].val) + 1;
            memcpy(out, caps[i].val, n);
            *area += n;
            return out;
        }
    }
    return 0;
}

/* Only the "cm" capability is ever passed here. */
char *tgoto(const char *cap, int col, int row)
{
    static char buf[32];
    (void)cap;
    snprintf(buf, sizeof buf, "\033[%d;%dH", row + 1, col + 1);
    return buf;
}

int tputs(const char *str, int affcnt, int (*putc_fn)(int))
{
    (void)affcnt;
    if (!str) return -1;
    /* skip any leading padding digits (termcap convention) */
    while (*str >= '0' && *str <= '9') str++;
    if (*str == '.') { str++; while (*str >= '0' && *str <= '9') str++; }
    if (*str == '*') str++;
    while (*str) putc_fn((unsigned char)*str++);
    return 0;
}

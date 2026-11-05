/* tty_termios.c - POSIX termios replacement for ularn's tty.c.
 * Same entry points: setctty, gettty, settty, setuptty, scbr, sncbr,
 * setupvt100, clearvt100. */
#include "header.h"
#include "player.h"
#include "extern.h"

#include <termios.h>

extern short ospeed;

static struct termios inittyb, curttyb;
static int have_tty = 0;

int setctty(void)
{
    if (have_tty) tcsetattr(0, TCSADRAIN, &curttyb);
    return 0;
}

int setuptty(void)
{
    curttyb.c_lflag &= ~(ECHO | ECHOE | ECHOK | ECHONL | ICANON);
    curttyb.c_cc[VMIN] = 1;
    curttyb.c_cc[VTIME] = 0;
    curttyb.c_iflag &= ~ISTRIP;
    curttyb.c_oflag &= ~TABDLY;     /* do not expand tabs */
    setctty();
    return 0;
}

int gettty(void)
{
    if (tcgetattr(0, &inittyb) == 0) have_tty = 1;
    curttyb = inittyb;
    ospeed = 0;
    setuptty();
    return 0;
}

int settty(void)
{
    if (have_tty) tcsetattr(0, TCSADRAIN, &inittyb);
    return 0;
}

int scbr(void)
{
    curttyb.c_lflag &= ~(ECHO | ECHOE | ECHOK | ECHONL | ICANON);
    setctty();
    return 0;
}

int sncbr(void)
{
    curttyb.c_lflag |= (ECHO | ECHOE | ECHOK | ECHONL | ICANON);
    setctty();
    return 0;
}

int setupvt100(void)
{
    clear();
    setscroll();
    gettty();
    return 0;
}

int clearvt100(void)
{
    resetscroll();
    settty();
    return 0;
}

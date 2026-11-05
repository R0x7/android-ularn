/* Stand-in for the legacy <termio.h>. The Android port replaces tty.c with
 * tty_termios.c, so only the POSIX termios API is needed. */
#ifndef ULARN_COMPAT_TERMIO_H
#define ULARN_COMPAT_TERMIO_H
#include <termios.h>
#include <sys/ioctl.h>
#endif

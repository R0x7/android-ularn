/* Force-included before every game source. Replaces upstream src/config.h
 * (whose include guard __CONFIG_H we define here, so it becomes a no-op). */
#ifndef ULARN_ANDROID_CONFIG_H
#define ULARN_ANDROID_CONFIG_H
#define __CONFIG_H

#define UNIX            /* POSIX file handling, getenv("HOME") etc. */
#define ANDROID_WIN     /* our front end: ularn_winandroid.c */

/* Data files are found relative to the game's working directory. */
#define LIBDIR "."

#endif

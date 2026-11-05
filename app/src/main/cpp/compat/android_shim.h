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

#include "android_config.h"
#include <stdlib.h>   /* pull in the real declaration before the macro */

void ularn_exit(int status) __attribute__((noreturn));
#define exit(s) ularn_exit(s)
#define main ularn_main

#endif

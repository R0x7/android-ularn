/* web_shim.h - force-included (after compat/android_shim.h) into every game
 * source in the web build only. The Android front end blocks on read() of the
 * key pipe and naps with usleep(); here both go to web_main.c, which waits on
 * the page's key queue / yields to the browser (Asyncify). Doing it with
 * macros keeps the shared C sources byte-identical to the Android branch, so
 * merging ularn-wild into the web branch never conflicts. */
#ifndef ULARN_WEB_SHIM_H
#define ULARN_WEB_SHIM_H
#include <unistd.h>
ssize_t ularn_web_read(int fd, void *buf, size_t n);
int ularn_web_usleep(unsigned int usec);
#define read(fd, buf, n) ularn_web_read(fd, buf, n)
#define usleep(us) ularn_web_usleep(us)
#endif

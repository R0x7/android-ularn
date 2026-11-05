/* Small replacements for libc calls bionic lacks or gates by API level.
 * Compiled with android_shim.h force-included, like the game sources. */
#include <signal.h>
#include <unistd.h>

/* BSD sigpause(mask): wait for a signal with the given mask (game passes 0). */
int ularn_sigpause(int mask)
{
    sigset_t set;
    (void)mask;
    sigemptyset(&set);
    return sigsuspend(&set);
}

int ularn_setpgrp(void)
{
    return setpgid(0, 0);
}

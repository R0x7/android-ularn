/* spawn.c - run the game in a forked child attached to a pseudo-terminal.
 *
 * Android apps can't easily ship standalone executables, so the game is
 * linked into the JNI library with main() renamed to ularn_main(). We fork,
 * make the child a session leader with the pty slave as its controlling
 * terminal on fds 0/1/2, and call ularn_main() there. The parent only ever
 * talks to the master fd. Each new game is a fresh fork, so the game's
 * global state always starts pristine.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>
#include <dirent.h>

#include "spawn.h"

extern int ularn_main(int argc, char **argv);

void ularn_exit(int status)
{
    fflush(NULL);
    _exit(status);
}

static void close_other_fds(void)
{
    DIR *d = opendir("/proc/self/fd");
    if (d) {
        int self = dirfd(d);
        struct dirent *e;
        while ((e = readdir(d)) != NULL) {
            int fd = atoi(e->d_name);
            if (fd > 2 && fd != self) close(fd);
        }
        closedir(d);
    } else {
        int fd;
        for (fd = 3; fd < 1024; fd++) close(fd);
    }
}

int ularn_spawn(const char *workdir, int argc, char **argv,
                char **envp, int rows, int cols, pid_t *pid_out)
{
    int master, i;
    char slave_name[128];
    struct winsize ws;
    pid_t pid;

    master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0) return -errno;
    if (grantpt(master) < 0 || unlockpt(master) < 0 ||
        ptsname_r(master, slave_name, sizeof slave_name) != 0) {
        int e = errno;
        close(master);
        return -e;
    }

    memset(&ws, 0, sizeof ws);
    ws.ws_row = (unsigned short)rows;
    ws.ws_col = (unsigned short)cols;
    ioctl(master, TIOCSWINSZ, &ws);

    pid = fork();
    if (pid < 0) {
        int e = errno;
        close(master);
        return -e;
    }

    if (pid == 0) {
        /* ---- child ---- */
        sigset_t all;
        int slave;
        struct termios t;

        /* The app process blocks / handles various signals; reset them. */
        sigfillset(&all);
        sigprocmask(SIG_UNBLOCK, &all, NULL);
        for (i = 1; i < NSIG; i++) signal(i, SIG_DFL);

        setsid();
        slave = open(slave_name, O_RDWR);
        if (slave < 0) _exit(127);
        ioctl(slave, TIOCSCTTY, 0);

        if (tcgetattr(slave, &t) == 0) {
            t.c_iflag |= ICRNL;
            t.c_iflag &= ~(IXON | IXOFF);   /* let ^S / ^Q through */
            t.c_oflag |= OPOST | ONLCR;
            t.c_cc[VERASE] = 0x7f;
            tcsetattr(slave, TCSANOW, &t);
        }

        dup2(slave, 0);
        dup2(slave, 1);
        dup2(slave, 2);
        close_other_fds();

        if (workdir && chdir(workdir) != 0) _exit(126);

        if (envp)
            for (i = 0; envp[i]; i++) putenv(envp[i]);

        setvbuf(stdin, NULL, _IONBF, 0);
        ularn_exit(ularn_main(argc, argv));
    }

    /* ---- parent ---- */
    *pid_out = pid;
    return master;
}

int ularn_set_size(int master, int rows, int cols)
{
    struct winsize ws;
    memset(&ws, 0, sizeof ws);
    ws.ws_row = (unsigned short)rows;
    ws.ws_col = (unsigned short)cols;
    return ioctl(master, TIOCSWINSZ, &ws);
}

int ularn_wait(pid_t pid)
{
    int status = 0;
    while (waitpid(pid, &status, 0) < 0) {
        if (errno != EINTR) return -1;
    }
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

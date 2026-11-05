/* spawn.c - run the game in a forked child.
 *
 * Android apps can't easily ship standalone executables, so the game is
 * linked into the JNI library with main() renamed to ularn_main(). Each new
 * game is a fresh fork(), so the game's global state always starts pristine.
 *
 * Parent <-> child:
 *   - display state: one UlarnShared struct in MAP_SHARED anonymous memory,
 *     created once and inherited by every child;
 *   - input: a pipe the app writes ASCII keys into (child's ularn_input_fd);
 *   - notify: a pipe the child pokes after display changes, so the app only
 *     redraws when something changed.
 * The child's stdout/stderr go to ularn.log in the work directory.
 */
#define _GNU_SOURCE
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include "spawn.h"
#include "shared_state.h"

extern int ularn_main(int argc, char **argv);
extern UlarnShared *ularn_shared;
extern int ularn_input_fd;
extern int ularn_notify_fd;

static UlarnShared *shared_region = NULL;

void ularn_exit(int status)
{
    fflush(NULL);
    _exit(status);
}

UlarnShared *ularn_shared_region(void)
{
    if (!shared_region) {
        void *p = mmap(NULL, sizeof(UlarnShared), PROT_READ | PROT_WRITE,
                       MAP_SHARED | MAP_ANONYMOUS, -1, 0);
        if (p == MAP_FAILED) return NULL;
        shared_region = (UlarnShared *) p;
    }
    return shared_region;
}

static void close_other_fds(int keep1, int keep2)
{
    DIR *d = opendir("/proc/self/fd");
    if (d) {
        int self = dirfd(d);
        struct dirent *e;
        while ((e = readdir(d)) != NULL) {
            int fd;
            if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
            fd = atoi(e->d_name);
            if (fd > 2 && fd != self && fd != keep1 && fd != keep2) close(fd);
        }
        closedir(d);
    }
}

int ularn_spawn(const char *workdir, int argc, char **argv, char **envp,
                UlarnSpawn *out)
{
    int in_pipe[2], note_pipe[2], i;
    UlarnShared *s = ularn_shared_region();
    pid_t pid;

    if (!s) return -ENOMEM;
    memset(s, 0, sizeof(*s));
    s->magic = US_MAGIC;
    s->version = US_VERSION;
    s->class_num = -1;

    if (pipe(in_pipe) < 0) return -errno;
    if (pipe(note_pipe) < 0) {
        int e = errno;
        close(in_pipe[0]); close(in_pipe[1]);
        return -e;
    }
    /* The child must never block on a notification. */
    fcntl(note_pipe[1], F_SETFL, O_NONBLOCK);

    pid = fork();
    if (pid < 0) {
        int e = errno;
        close(in_pipe[0]); close(in_pipe[1]);
        close(note_pipe[0]); close(note_pipe[1]);
        return -e;
    }

    if (pid == 0) {
        /* ---- child ---- */
        sigset_t all;
        int devnull, log;

        sigfillset(&all);
        sigprocmask(SIG_UNBLOCK, &all, NULL);
        for (i = 1; i < NSIG; i++) signal(i, SIG_DFL);
        signal(SIGPIPE, SIG_IGN);
        setsid();

        if (workdir && chdir(workdir) != 0) _exit(126);

        devnull = open("/dev/null", O_RDONLY);
        if (devnull >= 0) { dup2(devnull, 0); if (devnull > 0) close(devnull); }
        log = open("ularn.log", O_WRONLY | O_CREAT | O_TRUNC, 0600);
        if (log >= 0) { dup2(log, 1); dup2(log, 2); if (log > 2) close(log); }

        close(in_pipe[1]);
        close(note_pipe[0]);
        close_other_fds(in_pipe[0], note_pipe[1]);

        if (envp)
            for (i = 0; envp[i]; i++) putenv(envp[i]);

        ularn_shared = s;
        ularn_input_fd = in_pipe[0];
        ularn_notify_fd = note_pipe[1];

        setvbuf(stdout, NULL, _IOLBF, 0);
        ularn_exit(ularn_main(argc, argv));
    }

    /* ---- parent ---- */
    close(in_pipe[0]);
    close(note_pipe[1]);
    fcntl(in_pipe[1], F_SETFD, FD_CLOEXEC);
    fcntl(note_pipe[0], F_SETFD, FD_CLOEXEC);
    out->pid = pid;
    out->input_fd = in_pipe[1];
    out->notify_fd = note_pipe[0];
    return 0;
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

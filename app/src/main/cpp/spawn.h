#ifndef ULARN_SPAWN_H
#define ULARN_SPAWN_H
#include <sys/types.h>

/* Returns the pty master fd (>= 0) or -errno. */
int ularn_spawn(const char *workdir, int argc, char **argv,
                char **envp, int rows, int cols, pid_t *pid_out);
int ularn_set_size(int master, int rows, int cols);
/* Blocks until the child exits; returns exit code (128+sig if killed). */
int ularn_wait(pid_t pid);

#endif

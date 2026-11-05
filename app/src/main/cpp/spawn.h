#ifndef ULARN_SPAWN_H
#define ULARN_SPAWN_H
#include <sys/types.h>
#include "shared_state.h"

/* Exported only for the desktop test harness (-DULARN_HOST_TEST). */
#ifdef ULARN_HOST_TEST
#define ULARN_TEST_API __attribute__((visibility("default")))
#else
#define ULARN_TEST_API
#endif

typedef struct UlarnSpawn {
    pid_t pid;
    int input_fd;    /* app writes keys here */
    int notify_fd;   /* app reads change notifications here */
} UlarnSpawn;

/* The display-state region shared with every game child (created once). */
ULARN_TEST_API UlarnShared *ularn_shared_region(void);

/* Forks a game. Returns 0 on success or -errno. */
ULARN_TEST_API int ularn_spawn(const char *workdir, int argc, char **argv, char **envp,
                UlarnSpawn *out);

/* Blocks until the child exits; returns exit code (128+sig if killed). */
ULARN_TEST_API int ularn_wait(pid_t pid);

#endif

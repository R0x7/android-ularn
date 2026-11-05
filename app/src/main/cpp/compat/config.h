/* config.h - hand-written for the Android (bionic) port of Ularn.
 * Replaces the autoconf-generated config.h. */
#ifndef ULARN_ANDROID_CONFIG_H
#define ULARN_ANDROID_CONFIG_H

#define PACKAGE_NAME "Ularn"
#define PACKAGE_VERSION "1.7.0"

#define HAVE_FCNTL_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_SYS_FILE_H 1
#define HAVE_SYS_IOCTL_H 1
#define HAVE_SYS_PARAM_H 1
#define HAVE_SYS_TIME_H 1
#define HAVE_TERMIO_H 1          /* satisfied by compat/termio.h */
#define HAVE_UNISTD_H 1
#define HAVE_LIBZ 1
#define HAVE_SETITIMER 1
#define HAVE_RANDOM 1
#define HAVE_DRAND48 1
#define HAVE_STRCSPN 1
#define HAVE_FORK 1
#define HAVE_WORKING_FORK 1
#define HAVE_MALLOC 1
#define HAVE_REALLOC 1
#define STDC_HEADERS 1

#define RETSIGTYPE void

#endif

#!/bin/sh
# Builds tools/host/out/ularn_host: the real game + Android front end + a
# stdin-driven test driver, for Linux. Needs gcc; no Android SDK.
set -e
cd "$(dirname "$0")"
CPP=../../app/src/main/cpp
OUT=out
mkdir -p $OUT
SRCS=$(sed -n '/add_library(ularn_game STATIC/,/)/p' $CPP/CMakeLists.txt | tr ' ' '\n' | tr -d ')' | grep -E '\.c$' | sed "s#\${U}/#$CPP/ularn/#; s#^compat/#$CPP/compat/#")
# The game sources get the Android shim (main -> ularn_main, exit -> ularn_exit);
# the driver and spawner are compiled without it.
gcc -O1 -g -fsigned-char -std=gnu99 -w -include $CPP/compat/android_shim.h -I$CPP/compat -I$CPP/ularn -I$CPP \
    -c $SRCS
gcc -O1 -g -I$CPP -o $OUT/ularn_host *.o $CPP/spawn.c host_main.c -lm
rm -f *.o
echo "built $OUT/ularn_host"

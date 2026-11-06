#!/bin/sh
# Syntax-checks every game source the Android build compiles, with the same
# flags, using the host gcc. No Android SDK needed. Exit code != 0 on error.
set -e
cd "$(dirname "$0")/../../app/src/main/cpp"
FLAGS="-fsyntax-only -fsigned-char -std=gnu99 -Wall -Wno-unused -Wno-pointer-sign -Wno-parentheses -Wno-format-security -Wno-implicit-function-declaration -Wno-int-conversion -Wno-incompatible-pointer-types -Wno-return-type -Wno-misleading-indentation -Wno-char-subscripts -Wno-missing-braces -include compat/android_shim.h -Icompat -Iularn -I."
SRCS=$(sed -n '/add_library(ularn_game STATIC/,/)/p' CMakeLists.txt | tr ' ' '\n' | tr -d ')' | grep -E '\.c$' | sed 's#${U}/#ularn/#')
rc=0
for f in $SRCS; do
  if ! gcc $FLAGS "$f" 2>"/tmp/ularn_check_$$.err"; then
    echo "== $f"; cat "/tmp/ularn_check_$$.err"; rc=1
  else
    # warnings only in compat/ (upstream is -w in the real build)
    case "$f" in compat/*) if [ -s "/tmp/ularn_check_$$.err" ]; then echo "== $f (warnings)"; cat "/tmp/ularn_check_$$.err"; fi;; esac
  fi
done
rm -f "/tmp/ularn_check_$$.err"
for f in spawn.c ularn_jni.c; do
  gcc -fsyntax-only -I. -I/usr/lib/jvm/default-java/include -I/usr/lib/jvm/default-java/include/linux "$f" 2>/dev/null || gcc -fsyntax-only -I. -I$(dirname $(dirname $(readlink -f $(which javac))))/include -I$(dirname $(dirname $(readlink -f $(which javac))))/include/linux "$f" || rc=1
done
[ $rc = 0 ] && echo "check.sh: OK"
exit $rc

#!/bin/sh
# Builds the web version into web/site/: the real game (upstream core + the
# Android front end, same source list as app/src/main/cpp/CMakeLists.txt)
# compiled to WebAssembly with Emscripten, plus the page.
#   web/build.sh            # needs emcc 3.1.6 (Ubuntu 24.04: apt-get install emscripten;
#                           #  CI uses the emscripten/emsdk:3.1.6 image)
#   python3 -m http.server -d web/site 8000
set -e
cd "$(dirname "$0")"
CPP=../app/src/main/cpp
OUT=site
mkdir -p $OUT obj
SRCS=$(sed -n '/add_library(ularn_game STATIC/,/)/p' $CPP/CMakeLists.txt | tr ' ' '\n' | tr -d ')' | grep -E '\.c$' | sed "s#\${U}/#$CPP/ularn/#; s#^compat/#$CPP/compat/#")
CFLAGS="-O2 -fsigned-char -std=gnu99 -w -Wno-implicit-function-declaration -Wno-int-conversion -Wno-incompatible-pointer-types -Wno-return-type"
# Game sources get the Android shim (main -> ularn_main, exit -> ularn_exit)
# and web_shim.h (key pipe read / usleep -> web_main.c).
for f in $SRCS; do
  emcc $CFLAGS -include $CPP/compat/android_shim.h -include web_shim.h -I$CPP/compat -I$CPP/ularn -I$CPP -c "$f" -o "obj/$(basename "$f" .c).o"
done
emcc -O2 -I$CPP -c web_main.c -o obj/web_main.o

emcc -O2 obj/*.o -o $OUT/ularn.js \
  -sASYNCIFY=1 -sASYNCIFY_STACK_SIZE=65536 \
  -sALLOW_MEMORY_GROWTH=1 -sEXIT_RUNTIME=0 \
  -sFORCE_FILESYSTEM=1 -lidbfs.js \
  -sEXPORTED_FUNCTIONS=_ularn_web_push_key,_ularn_web_state,_ularn_web_layout,_ularn_web_run,_ularn_web_unranked \
  -sEXPORTED_RUNTIME_METHODS=ccall,UTF8ToString,FS \
  --preload-file $CPP/../assets/ularn@/data
cp index.html game.js style.css leaderboard.js leaderboard-config.js $OUT/
cp $CPP/../assets/ularn_tiles.png $OUT/
rm -rf obj
echo "built web/$OUT"

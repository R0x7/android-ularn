cmake_minimum_required(VERSION 3.22.1)
project(ularn C)

# Ularn 1.6.3a game core (upstream, unmodified). Platform front ends
# (TTY, X11, Win32, Amiga) are not built; compat/ularn_winandroid.c replaces them.
set(U ${CMAKE_CURRENT_SOURCE_DIR}/ularn)
add_library(ularn_game STATIC
        ${U}/ularn.c ${U}/ularn_game.c ${U}/ularn_ask.c ${U}/store.c
        ${U}/sphere.c ${U}/spell.c ${U}/show.c ${U}/scroll.c ${U}/scores.c
        ${U}/saveutils.c ${U}/savegame.c ${U}/potion.c ${U}/player.c
        ${U}/object.c ${U}/monster.c ${U}/itm.c ${U}/help.c ${U}/getopt.c
        ${U}/fortune.c ${U}/dungeon_obj.c ${U}/dungeon.c ${U}/diag.c
        ${U}/action.c
        compat/ularn_winandroid.c
        compat/android_context.c)

# Hidden visibility: the game's globals (e.g. `dirname`, `index`) share names
# with libc functions. Exported, the dynamic linker could bind the game's own
# references to libc's symbols instead. Hidden + -Bsymbolic keeps every game
# reference inside this library; only JNI entry points are exported.
target_compile_options(ularn_game PRIVATE
        -fvisibility=hidden
        # The game assumes `char` is signed (x86/m68k), e.g. its direction
        # tables hold -1 in chars. On ARM `char` is unsigned by default, which
        # turned every move with a negative component into "off the map".
        -fsigned-char
        -std=gnu99
        -w
        -Wno-implicit-function-declaration
        -Wno-int-conversion
        -Wno-incompatible-pointer-types
        -Wno-return-type
        -Wno-error
        -Wno-format-security
        -Wno-error=format-security
        # android_shim.h pulls in android_config.h, which defines upstream's
        # __CONFIG_H guard so ularn/config.h (TTY settings) becomes a no-op.
        -include ${CMAKE_CURRENT_SOURCE_DIR}/compat/android_shim.h)
target_include_directories(ularn_game PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/compat
        ${U})

add_library(ularn SHARED spawn.c ularn_jni.c)
target_link_libraries(ularn ularn_game)
target_compile_options(ularn PRIVATE -fvisibility=hidden)
target_link_options(ularn PRIVATE -Wl,--no-undefined -Wl,-Bsymbolic)

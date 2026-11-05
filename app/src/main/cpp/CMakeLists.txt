cmake_minimum_required(VERSION 3.22.1)
project(ularn C)

# Upstream Ularn sources (unmodified, except tty.c which is replaced).
file(GLOB ULARN_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/ularn/*.c)

add_library(ularn_game STATIC
        ${ULARN_SOURCES}
        compat/tty_termios.c
        compat/termcap_vt100.c
        compat/bionic_compat.c)

# The game is 1980s K&R C: compile it as gnu89 and silence the noise.
target_compile_options(ularn_game PRIVATE
        -std=gnu89
        -w
        -Wno-implicit-function-declaration
        -Wno-implicit-int
        -Wno-int-conversion
        -Wno-incompatible-pointer-types
        -Wno-return-type
        -Wno-error
        -Wno-format-security
        -Wno-error=format-security
        -include ${CMAKE_CURRENT_SOURCE_DIR}/compat/android_shim.h)
target_compile_definitions(ularn_game PRIVATE LIBDIR=\".\")
# compat/ first so our config.h and termio.h win.
target_include_directories(ularn_game BEFORE PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/compat
        ${CMAKE_CURRENT_SOURCE_DIR}/ularn)

add_library(ularn SHARED spawn.c ularn_jni.c)
target_link_libraries(ularn ularn_game z)
target_link_options(ularn PRIVATE -Wl,--no-undefined)

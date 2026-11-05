# Ularn for Android

An Android port of **Ularn 1.6.3a** (Julian Olds' graphical version, from
[R0x7/ularn-1.6.3a](https://github.com/R0x7/ularn-1.6.3a)), with the original
tile graphics and fully customizable on-screen controls.

The upstream C game core runs unmodified. A new Android front end
(`ularn_winandroid.c`) implements the game's display/input interface
(`ularn_win.h`), the same way the X11, Windows and Amiga versions do.

## Building

**Android Studio (easiest):** open this folder, let it sync (it will offer to
install the NDK and CMake 3.22.1 if missing), then Run ▶ on a device.

**Command line:** with the Android SDK installed (`ANDROID_HOME` set):

```
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # build and install on a connected device
```

**Just want to install it:** on your phone, open
https://github.com/R0x7/android-ularn/releases/latest/download/ularn.apk
and tap the download to install. Every push to `main` builds a new APK
(`.github/workflows/build.yml`) and publishes it as the latest release, so
that link always gets the newest version.

Requires Android 7.0+ (API 24). Builds for arm64-v8a, armeabi-v7a and x86_64.

## Playing

- **Start screen:** New game / Continue saved game / Resume from checkpoint,
  scoreboard, settings and the control editor.
- **Back button** opens the in-game menu (keyboard, edit controls, settings,
  help, save & quit).
- **Map:** the view follows your character; pinch to zoom. Tap a square to
  step towards it, long-press to run. In landscape the map fits between your
  side controls (toggle in Settings).
- **Prompts:** whenever the game asks a question (y/n, pick an item, a store
  menu, how much gold), buttons for the valid answers appear under the map.
  Tap a text page (inventory, help, scores) to continue.
- **Hardware / Bluetooth keyboards** work directly; arrow keys and the numpad
  move (toggle in Settings).
- **Checkpoints:** the game checkpoints every 25 moves and whenever the app
  goes to the background, so if Android closes it you can *Resume from
  checkpoint*. Use *Save game & quit* (or `S`) for a full save.
- **Settings:** character name and gender, skip intro, no delays, no beep, and
  1.6's *enhanced interface* (open doors with `o`, chests with `O`).

## Customizing the controls

Open **Edit controls** from the start screen or the menu (or give any button
the *Edit controls* action). Portrait and landscape have separate layouts.

| In edit mode | |
|---|---|
| Drag a control | move it (snaps to an 8 dp grid; toggle *Snap*) |
| Drag the orange corner handle | resize |
| Tap a selected control | open its properties |
| **+ Button / + D-pad** | add a control |
| **Look** | overall opacity, vibration |
| **Profiles** | save/load named layouts, copy/paste layouts as JSON |
| **Reset** | restore the default layout for this orientation |
| **⇅** | move the toolbar to the other edge |
| **Done / Cancel** | keep or discard changes |

**Button properties:** label, action (*Send keys*, *Run modifier*,
*Show/hide keyboard*, *Open menu*, *Edit controls*), keys to send, repeat
while held, round/rounded shape, width and height. Duplicate or delete from
*More…*.

**D-pad properties:** the key for each of the 8 directions and the centre,
4-way or 8-way, keep-moving-while-held, diameter.

**Key notation** (buttons and D-pad):

| Write | Sends |
|---|---|
| `i`, `q`, `Hl` | those characters, in order |
| `\e` or `<esc>` | Escape |
| `\n` or `<ret>` | Enter |
| `\s` or `<spc>` | Space |
| `\b` or `<del>` | Backspace |
| `^X` | Ctrl+X |
| `\\`, `\^` | a literal `\` or `^` |

So a button can be a macro, e.g. `c` + spell code: `cpro`. Ularn 1.6
commands: `hjklyubn` move (capitals run), `.` rest, `i` inventory, `c` cast,
`q` quaff, `r` read, `w` wield, `W` wear, `T` take off, `d` drop, `e` eat,
`\\` list known spells/items, `o`/`C` open/close door, `O` open chest,
`Z` teleport, `^` identify trap, `g` pack weight, `P` taxes, `S` save, `?` help.

**Run modifier:** tap *Run*, then a D-pad direction to run (sends `H J K L…`).
Long-press *Run* to keep it on; tap again to turn it off.

## How it works

```
app/src/main/cpp/
  ularn/                 Ularn 1.6.3a sources (upstream; only the game core is
                         built, not the TTY/X11/Win32/Amiga front ends)
  compat/
    ularn_winandroid.c   Android front end: implements ularn_win.h by writing the
                         display (map tiles, status, effects, messages, text
                         pages, what input is expected) into shared memory, and
                         reading keys from a pipe; adds checkpointing
    android_config.h     replaces upstream config.h (UNIX, LIBDIR ".")
    android_shim.h       renames main()/exit() so the game runs in a forked process
  shared_state.h         the shared display struct
  spawn.c                forks the game with the shared memory and two pipes
  ularn_jni.c            JNI bindings (shared buffer + field offsets)
app/src/main/assets/
  ularn_tiles.png        upstream ularn_gfx.xpm, with the cursor/sparkle masks baked in
  ularn/                 Uhelp, Umaps, Ufortune
app/src/main/java/com/ularn/android/
  game/                  GameState (reads the shared struct), GameView (renders it)
  controls/              layout model, overlay (play + edit mode), editor, persistence
  GameSession.kt         process lifecycle and I/O
  MainActivity.kt        UI, menus, settings
```

Each game is a fresh `fork()` of the app process, so the game's global state
always starts clean. Game files (`ularn_player.sav`, `ularn_player.ckp`,
`Uscore`, `ularn.opt`) live in the app's private storage.

## License

GPL v2, same as Ularn. See `LICENSE` and `app/src/main/cpp/ularn/COPYRIGHT`.
The non-GPL modules in the 1.6.3a distribution (X11 menu library, Amiga helpers)
are not used and not included.

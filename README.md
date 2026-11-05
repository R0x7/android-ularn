# Ularn for Android

An Android port of [Ularn](https://github.com/ularn/ularn) (v1.7.0), the classic
terminal roguelike, with fully customizable on-screen controls.

The original C game runs unmodified (apart from a replaced tty layer) inside a
pseudo-terminal, and the app draws it with a built-in VT100 terminal view.

## Building

**Android Studio (easiest):** open this folder, let it sync (it will offer to
install the NDK and CMake 3.22.1 if missing), then Run ▶ on a device.

**Command line:** with the Android SDK installed (`ANDROID_HOME` set):

```
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # build and install on a connected device
```

**No Android tools at all:** push this folder to a GitHub repository. The
included workflow (`.github/workflows/build.yml`) builds the APK on every push;
download it from the run's *Artifacts*.

Requires Android 7.0+ (API 24). Builds for arm64-v8a, armeabi-v7a and x86_64.

## Playing

- **Start screen:** New game / Continue saved game / Resume from checkpoint,
  scoreboard, settings and the control editor.
- **Back button** opens the in-game menu (keyboard, edit controls, settings,
  help, save & quit).
- **Map:** pinch to zoom, drag to pan, double-tap to reset. In landscape the map
  automatically shrinks to fit between your side controls (toggle in Settings).
- **Hardware / Bluetooth keyboards** work directly; arrow keys and the numpad
  move (toggle in Settings).
- **Auto-checkpointing** is on by default, so if Android closes the app you can
  pick up from the last checkpoint. Use *Save game & quit* (or `S`) for a full
  save.

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

So a button can be a macro, e.g. `c` + spell code: `cpro\n`.

**Run modifier:** tap *Run*, then a D-pad direction to run (sends `H J K L…`).
Long-press *Run* to keep it on; tap again to turn it off.

## How it works

```
app/src/main/cpp/
  ularn/               upstream game sources (tty.c removed)
  compat/              config.h, built-in VT100 termcap, termios tty layer,
                       bionic shims, and a force-included header that renames
                       main()/exit() so the game can run in a forked process
  spawn.c              forks the game on a new pty (posix_openpt)
  ularn_jni.c          JNI bindings
app/src/main/java/com/ularn/android/
  term/                VT100 emulator + terminal view
  controls/            layout model, overlay (play + edit mode), editor dialog,
                       persistence
  GameSession.kt       process lifecycle and I/O
  MainActivity.kt      UI, menus, settings
```

Each game is a fresh `fork()` of the app process with the pty as its
controlling terminal, so the game's global state always starts clean, and
closing the session sends `SIGHUP`, which makes Ularn write its checkpoint.
Game files (`Ularn.sav`, `Ularn.ckp`, `Uscore`, `.Ularnopts`) live in the
app's private storage.

## License

GPL v2, same as Ularn. See `LICENSE`.

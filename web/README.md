# Ularn Web

The browser version: the same C game as the Android app (upstream Ularn
1.6.3a core + `app/src/main/cpp/compat/`, Wild Edition included) compiled to
WebAssembly with Emscripten, plus a small canvas/DOM front end.

Play: https://r0x7.github.io/android-ularn/ (published by
`.github/workflows/web.yml` to the `gh-pages` branch).

## Build and run locally

```
sudo apt-get install emscripten        # or any emsdk
web/build.sh                           # -> web/site/
python3 -m http.server -d web/site 8000
```

## How it fits together

| Android | Web |
|---|---|
| `spawn.c` forks the game; `ularn_jni.c` | `web_main.c`: `ularn_web_run()` starts the game in-page |
| `UlarnShared` in shared memory | the same struct, read from wasm memory (`ularn_web_layout()` gives field offsets) |
| keys written to a pipe | `ularn_web_push_key()` fills a ring; `read_key()` polls it and yields with `emscripten_sleep` (Asyncify) |
| new game = new process | new game = page reload |
| files in app storage | `/ularn` on IndexedDB (IDBFS), synced every 15 s, on save/quit and when the tab hides |
| `GameView`, `ControlsOverlay` (Kotlin) | `game.js`, `index.html`, `style.css` |

The only change to shared C code is two `#ifdef __EMSCRIPTEN__` blocks in
`compat/ularn_winandroid.c` (`read_key()` and `nap()`); the Android build
is unaffected.

## Not ported yet

From the Android front end: the side panel, minimap, spell picker, context
action button, control-layout editor, save slots, run stats, and the Wild
Edition extras drawn by the app (chiptune radio/sounds, weather and
day/night effects, glitch visuals, Polaroid photo mode, trading-card
gallery). The game-side parts of the Wild features (pets, hats, vending
machines, LarnCoin, fishing, perks...) all work, since they live in the C
code.

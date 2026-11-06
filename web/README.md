# Ularn Web

The browser version: the same C game as the Android app (upstream Ularn
1.6.3a core + `app/src/main/cpp/compat/`, Wild Edition included) compiled to
WebAssembly with Emscripten, plus a small canvas/DOM front end.

Play: https://r0x7.github.io/android-ularn/

## Branches and publishing

The web port lives on its own branch (`ccr-fa7a4c55-ev0a7r`) = `ularn-wild`
plus `web/` and two workflows. It follows `ularn-wild` automatically:

* `.github/workflows/web-sync.yml` (on `ularn-wild`): every push to
  `ularn-wild` is merged into the web branch, built, and published to the
  `gh-pages` branch, which GitHub Pages serves.
* `.github/workflows/web.yml`: rebuilds and publishes on pushes to the web
  branch itself (changes to the web front end).

If a sync run fails (merge conflict, or GitHub refusing to let Actions push a
merge that changes a workflow file), merge `ularn-wild` into the web branch by
hand once.

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
| keys written to a pipe | `ularn_web_push_key()` fills a ring; `read_key()`'s `read()` goes to `ularn_web_read()`, which polls it and yields with `emscripten_sleep` (Asyncify) |
| new game = new process | new game = page reload |
| files in app storage | `/ularn` on IndexedDB (IDBFS), synced every 15 s, on save/quit and when the tab hides |
| `GameView`, `ControlsOverlay` (Kotlin) | `game.js`, `index.html`, `style.css` |

Shared code is not changed at all: `web_shim.h` (force-included in the web
build only) points the front end's `read()` of the key pipe and `usleep()`
at `web_main.c`. So this branch differs from `ularn-wild` only by new files
(`web/`, `.github/workflows/web*.yml`) and merges from it never conflict.

## Leaderboard

A shared top-100 leaderboard (title screen and end-of-game screen) stored in
Supabase. Off until `leaderboard-config.js` has a project URL and anon key.

Setup, once:
1. Create a free project at https://supabase.com.
2. SQL Editor -> New query -> paste `leaderboard.sql` -> Run. This makes the
   `ularn_scores` table (public read, no direct writes) and the
   `submit_score()` function, which validates an entry, ignores a run already
   submitted and keeps only the best 100.
3. Put the project URL and the anon / publishable key (Project Settings ->
   API) in `leaderboard-config.js`. Both are public by design.

What gets sent: when the game shows its final score (death or win) the page
submits the name from the title screen, score, the game's own "killed by ..."
line, class, level, dungeon, turns, kills, difficulty and whether Wild
features were on. Wizard and cheat games are never sent (Ularn keeps them off
its own scoreboard too), and a quick quit has no score. Runs that end offline
are queued in the browser and sent next time. The game runs in the player's
browser, so a determined player could still post a fake score; the table can
be edited by hand in the Supabase dashboard.

## Not ported yet

From the Android front end: the side panel, minimap, spell picker, context
action button, control-layout editor, save slots, run stats, and the Wild
Edition extras drawn by the app (chiptune radio/sounds, weather and
day/night effects, glitch visuals, Polaroid photo mode, trading-card
gallery). The game-side parts of the Wild features (pets, hats, vending
machines, LarnCoin, fishing, perks...) all work, since they live in the C
code.

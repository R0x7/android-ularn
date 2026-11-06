# Rules for AI agents

## Never touch the `original-backup` branch

`original-backup` is a preserved copy of the game before any agent changes.
It must stay exactly as it is.

- Do not check it out to commit, and never commit, merge, rebase, cherry-pick
  or reset onto it.
- Never push to it, force-push it, rename it, or delete it, locally or on
  GitHub.
- Do not open pull requests that target it.
- Reading it (for example `git diff original-backup..main`) is fine.

If a task seems to require changing it, stop and ask the repository owner
instead.

## Quality-of-life changes go on `main`, then merge into `ularn-wild`

There are two apps. `main` builds the base game, which is meant to stay the
original Ularn plus quality-of-life (QoL) changes only. `ularn-wild` builds
the Wild Edition, which is `main` plus the Wild features. Both apps should
have every QoL change.

- Make QoL changes (controls, layout, prompts, settings, accessibility,
  build and CI fixes) on `main`, not on `ularn-wild`.
- Wild-only features go on `ularn-wild` and never on `main`.
- Bring `main` into `ularn-wild` with a merge (`git merge main` on
  `ularn-wild`), never by cherry-picking. A merge records what has come over,
  so the next merge only brings the new changes and conflicts are not
  resolved twice.
- The `Merge main into ularn-wild` workflow (`.github/workflows/wild-sync.yml`)
  does this on every push to `main`. If the merge conflicts it opens a pull
  request instead; resolve it there or merge by hand.
- If a QoL fix was first made on `ularn-wild`, also make it on `main`, then
  merge `main` into `ularn-wild` again.

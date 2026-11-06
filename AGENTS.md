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

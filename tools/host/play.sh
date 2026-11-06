#!/usr/bin/env python3
"""Plays the game on the host from a key script. Usage:

  tools/host/play.sh [options] STEP [STEP ...]
  tools/host/play.sh [options] -f script.txt

Each STEP is one line for ularn_host (see host_main.c): raw keys, with \\n
enter, \\e escape, \\s space, and "{N:keys}" repeating keys N times
(expanded here). After every step the game state is printed.

Options:
  --seed N        reproducible run (ULARN_SEED)
  --wild MASK     ULARN_WILD feature mask (default: all on, 2047); 0 = vanilla
  --class a..h    character class letter (default a)
  --quiet         print only messages that changed + events
  --keep          keep the temp HOME/workdir (printed) for inspection
  --home DIR      reuse this HOME (to test save/restore across two runs)
  --wizard        start as a wizard (sends '-' + password first)
  --timeout MS    per-step idle timeout (default 4000)
  -- ARGS         extra game arguments (e.g. -- -r to restore a checkpoint)

Examples:
  tools/host/play.sh --wizard 'hhjj' '.'            # walk, look
  tools/host/play.sh --wizard --seed 7 '+' '...'    # wizard: create an item (follow the prompt)
  tools/host/play.sh '{300:.}'                      # rest 300 turns
Exit code 1 if the game crashed (GAME KILLED / ularn.log has a signal).
"""
import os, re, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "ularn")
EXE = os.path.join(HERE, "out", "ularn_host")


def expand(step):
    # {N:keys} -> keys repeated N times (nesting not supported)
    return re.sub(r"\{(\d+):([^}]*)\}", lambda m: m.group(2) * int(m.group(1)), step)


def main():
    args = sys.argv[1:]
    seed = wild = home = None
    cls, quiet, keep, wizard, timeout = "a", False, False, False, 4000
    steps, gargs, script = [], [], None
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--seed": seed = args[i + 1]; i += 2
        elif a == "--wild": wild = args[i + 1]; i += 2
        elif a == "--class": cls = args[i + 1]; i += 2
        elif a == "--home": home = args[i + 1]; i += 2
        elif a == "--timeout": timeout = int(args[i + 1]); i += 2
        elif a == "--quiet": quiet = True; i += 1
        elif a == "--keep": keep = True; i += 1
        elif a == "--wizard": wizard = True; i += 1
        elif a == "-f": script = args[i + 1]; i += 2
        elif a == "--": gargs = args[i + 1:]; break
        elif a in ("-h", "--help"): print(__doc__); return 0
        else: steps.append(a); i += 1
    if script:
        with open(script) as f:
            steps = [l.rstrip("\n") for l in f if l.strip() and not l.startswith("#")]
    if not os.path.exists(EXE):
        subprocess.check_call(["sh", os.path.join(HERE, "build.sh")])

    tmp = tempfile.mkdtemp(prefix="ularn_host_")
    work = os.path.join(tmp, "work")
    os.makedirs(work)
    for n in ("Uhelp", "Umaps", "Ufortune"):
        shutil.copy(os.path.join(ASSETS, n), work)
    if home is None:
        home = os.path.join(tmp, "home")
    os.makedirs(home, exist_ok=True)
    classes = {"a": "Ogre", "b": "Wizard", "c": "Klingon", "d": "Elf", "e": "Rogue",
               "f": "Adventurer", "g": "Dwarf", "h": "Rambo"}
    with open(os.path.join(home, "ularn.opt"), "w") as f:
        f.write("OPTION name:Tester,class:%s,gender:male,nowelcome,nonap,nobeep,noenhanced_interface\n"
                % classes.get(cls, cls))
    env = dict(os.environ, HOME=home, USER="player", LOGNAME="player")
    env["ULARN_WILD"] = wild if wild is not None else "2047"
    if seed is not None: env["ULARN_SEED"] = seed

    lines = []
    if wizard: lines.append("-fizban\\n")
    lines += [expand(s) for s in steps]
    cmd = [EXE, work, "--timeout", str(timeout)] + (["--quiet"] if quiet else []) + ["--"] + gargs
    print("# HOME=%s workdir=%s wild=%s seed=%s" % (home, work, env["ULARN_WILD"], seed))
    p = subprocess.run(cmd, input="\n".join(lines) + "\n", text=True, env=env, capture_output=True)
    out = p.stdout
    print(out, end="")
    if p.stderr: print(p.stderr, end="", file=sys.stderr)
    log = os.path.join(work, "ularn.log")
    crashed = "GAME KILLED" in out
    if os.path.exists(log):
        with open(log, errors="replace") as f:
            txt = f.read()
        if txt.strip():
            print("# ularn.log:"); print(txt[-3000:])
    if keep or crashed:
        print("# kept %s" % tmp)
    else:
        shutil.rmtree(tmp, ignore_errors=True)
    return 1 if crashed else 0


if __name__ == "__main__":
    sys.exit(main())

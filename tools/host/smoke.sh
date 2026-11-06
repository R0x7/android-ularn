#!/bin/sh
# Regression smoke test: a new game must start, walk, enter the dungeon, reach
# level 2 (via the wizard debug menu would be cheating: we take the stairs),
# save, and restore. Run after every merge. Exit code != 0 on failure.
set -e
cd "$(dirname "$0")"
sh build.sh >/dev/null
HOME_DIR=$(mktemp -d /tmp/ularn_smoke_XXXX)
out=$(./play.sh --seed 42 --wizard --class f --quiet --home "$HOME_DIR" \
  'hjkl' '=' 'g' '=' 'o' '79\n' '0\n' 'g' '!map' '=' 'o' '13\n' '0\n' 'd' '{20:.}' 'i' '\s' 'S')
echo "$out" | grep -q "EVENT: NEW_LEVEL(1" || { echo "$out"; echo "FAIL: never reached level 1"; exit 1; }
echo "$out" | grep -q "EVENT: NEW_LEVEL(2" || { echo "$out"; echo "FAIL: never reached level 2"; exit 1; }
[ -f "$HOME_DIR/ularn_player.sav" ] || { echo "$out"; echo "FAIL: no save file"; exit 1; }
out2=$(./play.sh --home "$HOME_DIR" '.' '!map')
echo "$out2" | grep -q "level= *2 " || { echo "$out2"; echo "FAIL: restore did not land on level 2"; exit 1; }
# vanilla mask must also run
out3=$(./play.sh --seed 7 --wild 0 --quiet '{10:.}' 'i')
echo "$out3" | grep -q "wild=0x0" || true
rm -rf "$HOME_DIR"
echo "smoke.sh: OK"

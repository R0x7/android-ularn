#!/usr/bin/env python3
"""Rebuilds app/src/main/assets/ularn_tiles.png from the original sheet plus
every authored tile in tools/tiles/src/<id>_<name>.txt.

Tile file format (see docs/WILD_EDITION_PLAN.md, "Tiles"):
  # comment lines are ignored, except palette lines:
  # k=#000000 w=#ffffff      (one or more "char=#rrggbb" per palette line)
  then exactly 32 lines of exactly 32 characters; '.' is transparent.

Ids must be >= BASE_TILES (368). Reserved ids without a file get a magenta
"missing" placeholder so a forgotten tile is obvious in the game. The sheet
grows by whole rows of 16 tiles. Exit code 1 on any validation error.
"""
import os, re, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "src")
BASE = os.path.join(HERE, "base_sheet.png")
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "ularn_tiles.png")
TILE, COLS = 32, 16
BASE_TILES = 368
# Highest id the plan reserves (docs/WILD_EDITION_PLAN.md); the sheet always covers it.
RESERVED_MAX = 447


def parse_tile(path):
    palette = {".": None}
    rows = []
    with open(path, encoding="utf-8") as f:
        for ln, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if line.startswith("#"):
                for ch, hexcol in re.findall(r"(\S)=#([0-9a-fA-F]{6})", line):
                    palette[ch] = tuple(int(hexcol[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
                continue
            if not line.strip():
                continue
            if len(line) != TILE:
                raise ValueError(f"{path}:{ln}: row has {len(line)} chars, want {TILE}")
            rows.append(line)
    if len(rows) != TILE:
        raise ValueError(f"{path}: {len(rows)} pixel rows, want {TILE}")
    img = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch not in palette:
                raise ValueError(f"{path}:{y + 1}: '{ch}' not in palette")
            col = palette[ch]
            if col is not None:
                px[x, y] = col
    return img


def placeholder(tid):
    img = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 29, 29], outline=(255, 0, 255, 255), fill=(80, 0, 80, 255))
    d.text((5, 10), str(tid), fill=(255, 255, 255, 255))
    return img


def main():
    base = Image.open(BASE).convert("RGBA")
    if base.size[0] != COLS * TILE or base.size[1] != (BASE_TILES // COLS) * TILE:
        sys.exit(f"base sheet is {base.size}, expected {COLS*TILE}x{(BASE_TILES//COLS)*TILE}")
    tiles = {}
    errors = []
    for name in sorted(os.listdir(SRC)) if os.path.isdir(SRC) else []:
        m = re.match(r"^(\d+)_[A-Za-z0-9_-]+\.txt$", name)
        if not m:
            if not name.startswith("."):
                errors.append(f"{name}: file name must be <id>_<name>.txt")
            continue
        tid = int(m.group(1))
        if tid < BASE_TILES:
            errors.append(f"{name}: id {tid} is inside the original sheet (< {BASE_TILES})")
            continue
        if tid in tiles:
            errors.append(f"{name}: id {tid} defined twice")
            continue
        try:
            tiles[tid] = parse_tile(os.path.join(SRC, name))
        except ValueError as e:
            errors.append(str(e))
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    top = max([RESERVED_MAX] + list(tiles))
    rows = (top // COLS) + 1
    sheet = Image.new("RGBA", (COLS * TILE, rows * TILE), (0, 0, 0, 0))
    sheet.paste(base, (0, 0))
    for tid in range(BASE_TILES, rows * COLS):
        img = tiles.get(tid)
        if img is None and tid <= RESERVED_MAX:
            img = placeholder(tid)
        if img is not None:
            sheet.paste(img, ((tid % COLS) * TILE, (tid // COLS) * TILE))
    sheet.save(OUT, optimize=True)
    print(f"wrote {os.path.relpath(OUT, os.getcwd())}: {rows} rows, {len(tiles)} authored tiles "
          f"({', '.join(str(t) for t in sorted(tiles)) or 'none'})")


if __name__ == "__main__":
    main()

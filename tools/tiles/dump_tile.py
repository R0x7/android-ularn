#!/usr/bin/env python3
"""Prints a tile of the current sheet as text, in the tile-file format, so you
can study the original art style or check your own tile. Usage:
  dump_tile.py <id> [--base]     (--base reads the untouched original sheet)
'.' = transparent; other characters are assigned per distinct colour."""
import os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
tid = int(sys.argv[1])
path = os.path.join(HERE, "base_sheet.png") if "--base" in sys.argv else \
    os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "ularn_tiles.png")
img = Image.open(path).convert("RGBA")
x0, y0 = (tid % 16) * 32, (tid // 16) * 32
chars = "kwrgbycmoKWRGBYCMOanpqstuvxzADEFHIJLNPQSTUVXZ0123456789"
pal, rows = {}, []
for y in range(32):
    row = ""
    for x in range(32):
        p = img.getpixel((x0 + x, y0 + y))
        if p[3] == 0:
            row += "."
            continue
        key = p[:3]
        if key not in pal:
            pal[key] = chars[len(pal)] if len(pal) < len(chars) else "?"
        row += pal[key]
    rows.append(row)
print(f"# tile {tid} from {os.path.basename(path)}")
items = [f"{c}=#{r:02x}{g:02x}{b:02x}" for (r, g, b), c in pal.items()]
for i in range(0, len(items), 6):
    print("# " + " ".join(items[i:i + 6]))
print("\n".join(rows))

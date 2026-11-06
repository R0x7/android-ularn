#!/usr/bin/env python3
"""Prints object ids (itm.h), monster ids (monster.h) and attribute indices
(player.h) as the game uses them, for play.sh scripts. Optional filter arg."""
import os, re, sys
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "cpp", "ularn")

def enum(path, end):
    s = open(os.path.join(ROOT, path)).read()
    body = s[s.index("typedef enum") + len("typedef enum"):s.index(end)]
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    return [t.strip() for t in body.replace("{", "").split(",") if t.strip()]

flt = sys.argv[1].lower() if len(sys.argv) > 1 else ""
for title, names in (("objects (itm.h)", enum("itm.h", "} ObjectIdType;")),
                     ("monsters (monster.h)", enum("monster.h", "} MonsterIdType;")),
                     ("attributes (player.h)", enum("player.h", "} AttributeType;"))):
    print("#", title)
    for i, n in enumerate(names):
        if flt in n.lower():
            print(f"{i:4d} {n}")

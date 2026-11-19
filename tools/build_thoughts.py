"""Genera app/src/main/assets/thoughts.json a partir de thoughts_part1/2 y valida el resultado."""
import json, sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
from thoughts_part1 import AUTHORS, WORKS, QUOTES
from thoughts_part2 import AFFINE, PRACTICES
from thoughts_part3 import AUTHORS3, WORKS3, QUOTES3, PRACTICES3
AUTHORS = {**AUTHORS, **AUTHORS3}
WORKS = {**WORKS, **WORKS3}

out = pathlib.Path(sys.argv[1])
items = []
for (i, a, w, r, es, fr) in QUOTES + AFFINE + QUOTES3:
    items.append({"id": i, "kind": "QUOTE", "author": a, "work": w, "ref": r, "text": {"es": es, "fr": fr}})
for (i, a, w, r, tes, tfr, es, fr) in PRACTICES + PRACTICES3:
    items.append({"id": i, "kind": "PRACTICE", "author": a, "work": w, "ref": r,
                  "title": {"es": tes, "fr": tfr}, "text": {"es": es, "fr": fr}})

ids = [x["id"] for x in items]
dupes = {i for i in ids if ids.count(i) > 1}
assert not dupes, f"ids duplicados: {dupes}"
for x in items:
    assert x["author"] in AUTHORS, x
    assert x["work"] in WORKS, x
    for lang in ("es", "fr"):
        assert x["text"][lang].strip(), x
data = {
    "authors": {k: {"es": v[0], "fr": v[1]} for k, v in AUTHORS.items()},
    "works": {k: {"es": v[0], "fr": v[1]} for k, v in WORKS.items()},
    "thoughts": items,
}
out.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
from collections import Counter
print(len(items), "pensamientos;", Counter(x["kind"] for x in items))
print(Counter(AUTHORS[x["author"]][0] for x in items).most_common())

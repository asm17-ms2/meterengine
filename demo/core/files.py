from __future__ import annotations

import json
import os


def write_json_atomic(path: str, data, sort_keys: bool = True) -> None:
    parent = os.path.dirname(path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    temporary = path + ".tmp"
    with open(temporary, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=sort_keys)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())
    os.replace(temporary, path)

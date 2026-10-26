from __future__ import annotations

import json
import os
from typing import Optional


def write_json_atomic(path: str, data, sort_keys: bool = True, mode: Optional[int] = None) -> None:
    parent = os.path.dirname(path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    temporary = path + ".tmp"
    if mode is None:
        handle = open(temporary, "w", encoding="utf-8")
    else:
        descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, mode)
        os.fchmod(descriptor, mode)
        handle = os.fdopen(descriptor, "w", encoding="utf-8")
    with handle as f:
        json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=sort_keys)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())
    os.replace(temporary, path)

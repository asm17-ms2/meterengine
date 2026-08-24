"""파일 쓰기 유틸.

여기 있는 것은 하나뿐이다. 사람이 손으로 고칠 수도 있는 JSON 파일을 덮어쓸 때,
쓰다가 죽어도 반쯤 쓰인 파일이 남지 않게 하는 방법이다. 브리지의 상태 파일과
Claude 설정이 둘 다 그 자리라, 한쪽만 고치면 다른 쪽이 남는다.
"""

from __future__ import annotations

import json
import os


def write_json_atomic(path: str, data, sort_keys: bool = True) -> None:
    """임시 파일에 다 쓰고 나서 제자리로 옮긴다.

    같은 파일을 열어 바로 쓰면 open이 먼저 내용을 지운다. 그 뒤 디스크가 차거나
    프로세스가 죽으면 빈 파일이 남는다. 남의 설정 파일이면 그것으로 끝이다.

    sort_keys를 끄는 자리가 있다. 사람이 관리하는 파일(~/.claude/settings.json)은
    우리가 키 순서를 뒤집으면 그 사람 눈에 통째로 바뀐 것처럼 보인다.
    """
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

"""브리지의 설정, 세션 매핑, 고객 해석 (MS2-169).

OTel 이벤트에는 어느 폴더에서 돌았는지가 없다. 실리는 것은 session.id뿐이다.
그래서 hook이 "이 세션은 이 폴더"를 알려 주고, 이 모듈이 그것을 프로젝트 이름과
고객으로 옮긴다.

개인 프로젝트 이름이 공개된 화면에 뜨지 않도록, 허용 목록에 적은 레포만 실명으로
쓰고 나머지는 하나의 폴백 프로젝트로 합친다.
"""

from __future__ import annotations

import json
import os
import subprocess
import threading
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set, Tuple

from core.api_client import ApiClient
from core.model import DEFAULT_ORG_ID, is_uuid

HOME_DIR = os.path.join(os.path.expanduser("~"), ".meterengine")
CONFIG_PATH = os.path.join(HOME_DIR, "bridge.json")
STATE_PATH = os.path.join(HOME_DIR, "state.json")

DEFAULT_FALLBACK_PROJECT = "기타 프로젝트"
DEFAULT_BASE_URL = "http://localhost:8080"

# 프로젝트 하나가 가질 수 있는 상태. 설정 파일에는 allow와 deny 두 배열로 저장되지만,
# 사람이 고를 때는 셋 중 하나를 고르는 편이 자연스럽다 (console.py의 목록).
NAMED = "실명으로 보냄"
MERGED = "기타 프로젝트로 합침"
SKIPPED = "보내지 않음"
PROJECT_STATES = [NAMED, MERGED, SKIPPED]

# 고객 이름 상한. 서버가 255자를 넘기면 400이다 (SaveCustomerRequest).
MAX_CUSTOMER_NAME = 255


@dataclass
class BridgeConfig:
    """~/.meterengine/bridge.json의 내용.

    base_url 기본값이 로컬인 것은 일부러다. usage_event는 append-only라 배포
    서버로 잘못 보낸 이벤트를 지울 수 없다. 배포 주소는 손으로 적어 넣게 한다.
    """

    owner: str = ""
    base_url: str = DEFAULT_BASE_URL
    org_id: str = DEFAULT_ORG_ID
    allow: List[str] = field(default_factory=list)
    deny: List[str] = field(default_factory=list)
    fallback_project: str = DEFAULT_FALLBACK_PROJECT
    timeout_seconds: float = 10.0

    @staticmethod
    def load(path: str = CONFIG_PATH) -> "BridgeConfig":
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
        except FileNotFoundError:
            return BridgeConfig()
        except ValueError as error:
            raise ValueError("%s를 읽을 수 없습니다: %s" % (path, error)) from error
        if not isinstance(data, dict):
            raise ValueError("%s의 최상위는 JSON 객체여야 합니다" % path)
        config = BridgeConfig(
            owner=str(data.get("owner") or ""),
            base_url=str(data.get("base_url") or DEFAULT_BASE_URL),
            org_id=str(data.get("org_id") or DEFAULT_ORG_ID),
            allow=[str(x) for x in data.get("allow") or []],
            deny=[str(x) for x in data.get("deny") or []],
            fallback_project=str(data.get("fallback_project") or DEFAULT_FALLBACK_PROJECT),
            timeout_seconds=float(data.get("timeout_seconds") or 10.0),
        )
        config.validate()
        return config

    def validate(self) -> None:
        """읽을 때도 저장할 때도 통과해야 하는 조건.

        save가 이것을 먼저 부른다. 잘못된 값이 파일에 남으면 그 뒤로는 load가 죽어
        config 명령으로도 되돌릴 수 없고, 손으로 JSON을 고쳐야 하기 때문이다.
        """
        if not is_uuid(self.org_id):
            raise ValueError("org_id가 UUID가 아닙니다: " + self.org_id)
        if not self.base_url.startswith(("http://", "https://")):
            raise ValueError("base_url이 http:// 또는 https://로 시작해야 합니다: " + self.base_url)

    def save(self, path: str = CONFIG_PATH) -> None:
        self.validate()
        _write_json(
            path,
            {
                "owner": self.owner,
                "base_url": self.base_url,
                "org_id": self.org_id,
                "allow": self.allow,
                "deny": self.deny,
                "fallback_project": self.fallback_project,
                "timeout_seconds": self.timeout_seconds,
            },
        )

    def scope(self) -> str:
        """고객 캐시가 어느 서버 것인지 가리키는 값.

        customer_id를 발급한 것이 (전송 대상, 도입사)이므로 둘을 함께 본다.
        """
        return "%s|%s" % (self.base_url.rstrip("/"), self.org_id)

    def project_state(self, project: str) -> str:
        """이 프로젝트가 지금 어떤 상태인지."""
        if project in self.deny:
            return SKIPPED
        if project in self.allow:
            return NAMED
        return MERGED

    def set_project_states(self, states: Dict[str, str]) -> None:
        """목록에서 고른 상태를 allow와 deny로 되돌린다.

        MERGED는 어느 배열에도 넣지 않는다. 그것이 "허용 목록에 없다"의 뜻이다.
        다만 그 결과 allow가 비면 project_for_cwd가 전부 실명으로 보내므로,
        고른 것과 정반대가 된다. 부르는 쪽이 그 상태를 사람에게 알려야 한다
        (console.py의 규칙 줄).
        """
        self.allow = sorted(name for name, state in states.items() if state == NAMED)
        self.deny = sorted(name for name, state in states.items() if state == SKIPPED)

    def names_everything(self) -> bool:
        """지금 설정이 모든 프로젝트를 실명으로 보내는 상태인가."""
        return not self.allow

    def customer_name(self, project: str) -> str:
        """화면에 뜰 고객 이름. 주인을 적어 두면 사람별로 갈라진다."""
        name = "%s(%s)" % (project, self.owner) if self.owner else project
        return name[:MAX_CUSTOMER_NAME]


def repo_name(cwd: str) -> Optional[str]:
    """작업 폴더가 속한 git 레포의 이름.

    --show-toplevel이 아니라 --git-common-dir을 보는 이유는 워크트리 때문이다.
    toplevel은 워크트리 경로(.../workspaces/meterengine/MS2-169)라 워크트리마다
    다른 이름이 나온다. common-dir은 본 레포의 .git을 가리키므로 어느 워크트리에서
    일해도 같은 이름(meterengine)으로 모인다.

    git 저장소가 아니면 None이다. 판정은 호출자가 한다.
    """
    if not cwd or not os.path.isdir(cwd):
        return None
    try:
        output = subprocess.run(
            ["git", "-C", cwd, "rev-parse", "--path-format=absolute", "--git-common-dir"],
            capture_output=True,
            text=True,
            timeout=5,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if output.returncode != 0:
        return None
    git_dir = output.stdout.strip()
    if not git_dir:
        return None
    # 베어 저장소가 아니면 .git의 부모가 작업 트리 루트다.
    name = os.path.basename(os.path.dirname(git_dir))
    return name or None


def project_for_cwd(cwd: str, config: BridgeConfig) -> Optional[str]:
    """폴더를 프로젝트 이름으로 옮긴다. None이면 보내지 않는다.

    deny에 걸리면 버린다. allow가 비어 있으면 모두 실명이고, 값이 있으면 거기
    적힌 것만 실명이며 나머지는 폴백 하나로 합쳐진다. 개인 프로젝트 이름이
    공개된 화면에 뜨지 않게 하는 장치다.
    """
    name = repo_name(cwd) or (os.path.basename(os.path.normpath(cwd)) if cwd else "")
    if not name:
        return config.fallback_project
    if name in config.deny:
        return None
    if config.allow and name not in config.allow:
        return config.fallback_project
    return name


class BridgeState:
    """세션 매핑과 고객 캐시. 브리지를 재시작해도 살아남게 디스크에 둔다.

    HTTP 서버가 스레드로 돌기 때문에 잠금이 필요하다.
    """

    def __init__(self, path: str = STATE_PATH, scope: str = ""):
        self.path = path
        self.scope = scope
        self._lock = threading.Lock()
        self.sessions: Dict[str, str] = {}
        self.customers: Dict[str, str] = {}
        # deny에 걸린 폴더의 세션. 매핑이 그냥 없는 것과 구별해야 한다. 없으면
        # hook을 놓친 세션으로 보고 폴백으로 보내는데, 그러면 deny가 무력해진다.
        self.denied: Set[str] = set()
        self._load()

    def _load(self) -> None:
        try:
            with open(self.path, encoding="utf-8") as f:
                data = json.load(f)
        except (FileNotFoundError, ValueError):
            return
        if not isinstance(data, dict):
            return
        sessions = data.get("sessions")
        customers = data.get("customers")
        denied = data.get("denied")
        if isinstance(sessions, dict):
            self.sessions = {str(k): str(v) for k, v in sessions.items()}
        if isinstance(denied, list):
            self.denied = {str(x) for x in denied}
        # 고객 캐시는 서버에 딸린 값이다. customer_id를 발급한 것이 그 서버라,
        # 전송 대상을 바꾸면 그 id는 저쪽에 없어 이벤트가 전부 거절된다
        # (unknown_customer_reference). 그래서 어느 서버 것인지 함께 적어 두고
        # 다르면 버린다. 세션 매핑은 폴더에 대한 것이라 서버와 무관하므로 남긴다.
        if self.scope and str(data.get("scope") or "") != self.scope:
            return
        if isinstance(customers, dict):
            self.customers = {str(k): str(v) for k, v in customers.items() if is_uuid(str(v))}

    def _save_locked(self) -> None:
        _write_json(
            self.path,
            {
                "scope": self.scope,
                "sessions": self.sessions,
                "customers": self.customers,
                "denied": sorted(self.denied),
            },
        )

    def remember_session(self, session_id: str, project: str) -> None:
        with self._lock:
            if self.sessions.get(session_id) == project and session_id not in self.denied:
                return
            self.sessions[session_id] = project
            self.denied.discard(session_id)
            self._save_locked()

    def deny_session(self, session_id: str) -> None:
        """이 세션의 이벤트는 보내지 않는다."""
        with self._lock:
            if session_id in self.denied and session_id not in self.sessions:
                return
            self.denied.add(session_id)
            self.sessions.pop(session_id, None)
            self._save_locked()

    def snapshot(self) -> Tuple[Dict[str, str], Dict[str, str], int]:
        """health가 보여 줄 값을 잠금 아래에서 한 번에 복사한다.

        잠금 밖에서 sessions를 순회하면 hook이 매핑을 넣는 순간 dict 크기가 바뀌어
        터진다. 그러면 health가 응답하지 못하고, 콘솔은 그것을 브리지가 꺼진 것으로
        읽는다.
        """
        with self._lock:
            return dict(self.sessions), dict(self.customers), len(self.denied)

    def is_denied(self, session_id: Optional[str]) -> bool:
        if not session_id:
            return False
        with self._lock:
            return session_id in self.denied

    def project_of(self, session_id: Optional[str]) -> Optional[str]:
        if not session_id:
            return None
        with self._lock:
            return self.sessions.get(session_id)

    def cached_customer(self, name: str) -> Optional[str]:
        with self._lock:
            return self.customers.get(name)

    def remember_customer(self, name: str, customer_id: str) -> None:
        with self._lock:
            if self.customers.get(name) == customer_id:
                return
            self.customers[name] = customer_id
            self._save_locked()


class CustomerResolver:
    """고객 이름을 customer_id로 옮긴다. 없으면 등록한다.

    등록 전에 반드시 목록을 조회한다. 고객 등록 API는 이름 중복을 막지 않아
    (backend/openapi.yaml) 조회를 빠뜨리면 같은 이름의 고객이 계속 늘어난다.
    """

    def __init__(self, client: ApiClient, state: BridgeState):
        self.client = client
        self.state = state
        self._lock = threading.Lock()

    def resolve(self, name: str) -> str:
        cached = self.state.cached_customer(name)
        if cached:
            return cached
        # 같은 이름을 여러 스레드가 동시에 등록하지 않게 잠근다.
        with self._lock:
            cached = self.state.cached_customer(name)
            if cached:
                return cached
            found = self._find(name)
            customer_id = found or self._create(name)
            self.state.remember_customer(name, customer_id)
            return customer_id

    def _find(self, name: str) -> Optional[str]:
        result = self.client.get_customers()
        if result.status != 200 or not isinstance(result.body, dict):
            raise RuntimeError("고객 목록 조회 실패: HTTP %d %s" % (result.status, result.body_text[:200]))
        for customer in result.body.get("customers") or []:
            if customer.get("name") == name:
                customer_id = customer.get("customer_id")
                if isinstance(customer_id, str) and is_uuid(customer_id):
                    return customer_id
        return None

    def _create(self, name: str) -> str:
        result = self.client.create_customer(name)
        if result.status != 201 or not isinstance(result.body, dict):
            raise RuntimeError("고객 등록 실패: HTTP %d %s" % (result.status, result.body_text[:200]))
        customer_id = result.body.get("customer_id")
        if not isinstance(customer_id, str) or not is_uuid(customer_id):
            raise RuntimeError("고객 등록 응답에 customer_id가 없습니다: " + result.body_text[:200])
        return customer_id


def _write_json(path: str, data: dict) -> None:
    """원자적으로 쓴다. 중간에 죽어도 반쯤 쓰인 파일이 남지 않는다."""
    parent = os.path.dirname(path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    temporary = path + ".tmp"
    with open(temporary, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())
    os.replace(temporary, path)

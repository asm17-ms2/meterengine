from __future__ import annotations

import json
import os
import subprocess
import threading
import time
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set, Tuple

from bridge.const import CONFIG_PATH, STATE_PATH
from core.api_client import ApiClient
from core.files import write_json_atomic
from core.model import DEFAULT_BASE_URL, DEFAULT_ORG_ID, is_uuid

DEFAULT_FALLBACK_PROJECT = "기타 프로젝트"

SESSION_TTL_SECONDS = 24 * 60 * 60

NAMED = "실명으로 보냄"
MERGED = "기타 프로젝트로 합침"
SKIPPED = "보내지 않음"
PROJECT_STATES = [NAMED, MERGED, SKIPPED]

MAX_CUSTOMER_NAME = 255


@dataclass
class BridgeConfig:
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
        if not is_uuid(self.org_id):
            raise ValueError("org_id가 UUID가 아닙니다: " + self.org_id)
        if not self.base_url.startswith(("http://", "https://")):
            raise ValueError("base_url이 http:// 또는 https://로 시작해야 합니다: " + self.base_url)

    def save(self, path: str = CONFIG_PATH) -> None:
        self.validate()
        write_json_atomic(
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
        return "%s|%s" % (self.base_url.rstrip("/"), self.org_id)

    def project_state(self, project: str) -> str:
        """이 프로젝트가 지금 어떤 상태인지."""
        if project in self.deny:
            return SKIPPED
        if project in self.allow:
            return NAMED
        return MERGED

    def set_project_states(self, states: Dict[str, str]) -> None:
        self.allow = sorted(name for name, state in states.items() if state == NAMED)
        self.deny = sorted(name for name, state in states.items() if state == SKIPPED)

    def names_everything(self) -> bool:
        """지금 설정이 모든 프로젝트를 실명으로 보내는 상태인가."""
        return not self.allow

    def customer_name(self, project: str) -> str:
        name = "%s(%s)" % (project, self.owner) if self.owner else project
        return name[:MAX_CUSTOMER_NAME]


def repo_name(cwd: str) -> Optional[str]:
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
    name = os.path.basename(os.path.dirname(git_dir))
    return name or None


def project_for_cwd(cwd: str, config: BridgeConfig) -> Optional[str]:
    name = repo_name(cwd) or (os.path.basename(os.path.normpath(cwd)) if cwd else "")
    if not name:
        return config.fallback_project
    if name in config.deny:
        return None
    if config.allow and name not in config.allow:
        return config.fallback_project
    return name


class BridgeState:
    def __init__(self, path: str = STATE_PATH, scope: str = ""):
        self.path = path
        self.scope = scope
        self._lock = threading.Lock()
        self.sessions: Dict[str, str] = {}
        self.customers: Dict[str, str] = {}
        self.denied: Set[str] = set()
        self.seen: Dict[str, float] = {}
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
        seen = data.get("seen")
        if isinstance(sessions, dict):
            self.sessions = {str(k): str(v) for k, v in sessions.items()}
        if isinstance(denied, list):
            self.denied = {str(x) for x in denied}
        if isinstance(seen, dict):
            for key, value in seen.items():
                try:
                    self.seen[str(key)] = float(value)
                except (TypeError, ValueError):
                    continue
        now = time.time()
        for session_id in list(self.sessions) + list(self.denied):
            self.seen.setdefault(session_id, now)
        self._prune(now)
        if self.scope and str(data.get("scope") or "") != self.scope:
            return
        if isinstance(customers, dict):
            self.customers = {str(k): str(v) for k, v in customers.items() if is_uuid(str(v))}

    def _prune(self, now: float) -> None:
        """하루 넘게 hook이 오지 않은 세션을 버린다."""
        stale = [
            session_id
            for session_id, last in self.seen.items()
            if now - last > SESSION_TTL_SECONDS
        ]
        for session_id in stale:
            self.seen.pop(session_id, None)
            self.sessions.pop(session_id, None)
            self.denied.discard(session_id)

    def _save_locked(self) -> None:
        self._prune(time.time())
        write_json_atomic(
            self.path,
            {
                "scope": self.scope,
                "sessions": self.sessions,
                "customers": self.customers,
                "denied": sorted(self.denied),
                "seen": self.seen,
            },
        )

    def remember_session(self, session_id: str, project: str) -> None:
        with self._lock:
            self.seen[session_id] = time.time()
            if self.sessions.get(session_id) == project and session_id not in self.denied:
                return
            self.sessions[session_id] = project
            self.denied.discard(session_id)
            self._save_locked()

    def deny_session(self, session_id: str) -> None:
        """이 세션의 이벤트는 보내지 않는다."""
        with self._lock:
            self.seen[session_id] = time.time()
            if session_id in self.denied and session_id not in self.sessions:
                return
            self.denied.add(session_id)
            self.sessions.pop(session_id, None)
            self._save_locked()

    def snapshot(self) -> Tuple[Dict[str, str], Dict[str, str], int]:
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

    def forget_customer(self, name: str) -> None:
        with self._lock:
            if name not in self.customers:
                return
            del self.customers[name]
            self._save_locked()


class CustomerResolver:
    def __init__(self, client: ApiClient, state: BridgeState):
        self.client = client
        self.state = state
        self._lock = threading.Lock()

    def resolve(self, name: str) -> str:
        cached = self.state.cached_customer(name)
        if cached:
            return cached
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
                customer_id = customer.get("id")
                if isinstance(customer_id, str) and is_uuid(customer_id):
                    return customer_id
        return None

    def _create(self, name: str) -> str:
        result = self.client.create_customer(name)
        if result.status != 201 or not isinstance(result.body, dict):
            raise RuntimeError("고객 등록 실패: HTTP %d %s" % (result.status, result.body_text[:200]))
        customer_id = result.body.get("id")
        if not isinstance(customer_id, str) or not is_uuid(customer_id):
            raise RuntimeError("고객 등록 응답에 id가 없습니다: " + result.body_text[:200])
        return customer_id

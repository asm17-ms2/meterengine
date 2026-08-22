"""브리지를 켜고 끄고 설정하는 일 (MS2-169).

CLI와 TUI가 같은 코드를 쓴다. 그래서 이 모듈은 아무것도 출력하지 않는다.
무엇을 했는지 값으로 돌려주고, 사람에게 보여 주는 일은 부르는 쪽이 한다.
"""

from __future__ import annotations

import json
import os
import subprocess
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Dict, List, Optional

from bridge.const import (
    BRIDGE_ERR,
    BRIDGE_LOG,
    BRIDGE_SCRIPT,
    CLAUDE_SETTINGS_PATH,
    DEFAULT_HOST,
    DEFAULT_PORT,
    LAUNCHD_LABEL,
    PLIST_PATH,
    health_endpoint,
    logs_endpoint,
    session_endpoint,
)

# Claude에 심을 환경 변수.
#   메트릭을 끄는 이유: 우리가 쓰는 것은 이벤트 로그뿐이고, 메트릭까지 켜면
#   브리지가 걸러낼 것만 늘어난다.
#   http/json인 이유: protobuf를 풀지 않고 표준 라이브러리로 읽기 위해서다.
OTEL_ENV = {
    "CLAUDE_CODE_ENABLE_TELEMETRY": "1",
    "OTEL_LOGS_EXPORTER": "otlp",
    "OTEL_METRICS_EXPORTER": "none",
    "OTEL_EXPORTER_OTLP_PROTOCOL": "http/json",
    "OTEL_LOGS_EXPORT_INTERVAL": "5000",
}

# hook을 UserPromptSubmit에 다는 이유는 두 가지다. SessionStart는 command와
# mcp_tool 타입만 지원해서 http hook을 걸 수 없고(공식 문서), 세션당 한 번뿐이라
# 그때 브리지가 꺼져 있으면 그 세션 전체가 폴백으로 간다. UserPromptSubmit은 매
# 턴 오므로 브리지를 중간에 재시작해도 다음 턴에 매핑이 저절로 복구된다.
HOOK_EVENT = "UserPromptSubmit"


@dataclass
class Change:
    """설정에서 무엇이 어떻게 바뀌는지."""

    key: str
    before: Optional[str]
    after: str

    def __str__(self) -> str:
        return "%s: %s -> %s" % (self.key, self.before if self.before is not None else "(없음)", self.after)


@dataclass
class SettingsPlan:
    """~/.claude/settings.json에 반영할 내용."""

    path: str
    settings: dict
    changes: List[Change] = field(default_factory=list)

    @property
    def needed(self) -> bool:
        return bool(self.changes)


class AdminError(Exception):
    """사람이 고쳐야 하는 상태. 메시지를 그대로 보여 주면 된다."""


# ------------------------------------------------------------ Claude 설정


def plan_claude_settings(
    path: str = CLAUDE_SETTINGS_PATH, host: str = DEFAULT_HOST, port: int = DEFAULT_PORT
) -> SettingsPlan:
    """설정 파일에 무엇을 더할지 계산한다. 파일을 쓰지는 않는다.

    기존 키를 지우지 않는다. 이미 다른 hook이 있으면 그 옆에 붙인다.
    """
    settings: dict = {}
    if os.path.exists(path):
        try:
            with open(path, encoding="utf-8") as f:
                settings = json.load(f)
        except ValueError as error:
            raise AdminError("%s가 올바른 JSON이 아닙니다: %s" % (path, error)) from error
        if not isinstance(settings, dict):
            raise AdminError("%s의 최상위가 JSON 객체가 아닙니다" % path)

    plan = SettingsPlan(path=path, settings=settings)

    wanted = dict(OTEL_ENV)
    wanted["OTEL_EXPORTER_OTLP_LOGS_ENDPOINT"] = logs_endpoint(host, port)

    env = settings.setdefault("env", {})
    if not isinstance(env, dict):
        raise AdminError("settings.json의 env가 객체가 아닙니다")
    for key, value in wanted.items():
        if env.get(key) != value:
            plan.changes.append(Change("env." + key, env.get(key), value))
            env[key] = value

    hooks = settings.setdefault("hooks", {})
    if not isinstance(hooks, dict):
        raise AdminError("settings.json의 hooks가 객체가 아닙니다")
    entries = hooks.setdefault(HOOK_EVENT, [])
    if not isinstance(entries, list):
        raise AdminError("settings.json의 hooks.%s가 배열이 아닙니다" % HOOK_EVENT)

    url = session_endpoint(host, port)
    if not _has_hook_url(entries, url):
        entries.append({"hooks": [{"type": "http", "url": url, "timeout": 5}]})
        plan.changes.append(Change("hooks." + HOOK_EVENT, None, "http " + url))
    return plan


def apply_claude_settings(plan: SettingsPlan) -> Optional[str]:
    """계획을 파일에 쓴다. 백업을 남겼으면 그 경로를 돌려준다."""
    parent = os.path.dirname(plan.path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    backup = None
    if os.path.exists(plan.path):
        backup = plan.path + ".bak"
        with open(plan.path, encoding="utf-8") as src, open(backup, "w", encoding="utf-8") as dst:
            dst.write(src.read())
    with open(plan.path, "w", encoding="utf-8") as f:
        json.dump(plan.settings, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return backup


def _has_hook_url(entries: list, url: str) -> bool:
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        for hook in entry.get("hooks") or []:
            if isinstance(hook, dict) and hook.get("url") == url:
                return True
    return False


# ------------------------------------------------------------ launchd


def worktree_root(script: str = BRIDGE_SCRIPT) -> Optional[str]:
    """스크립트가 git 워크트리 안에 있으면 그 경로. 아니면 None.

    워크트리는 --show-toplevel과 --git-common-dir의 부모가 다르다는 것으로 가른다.
    plist에 절대 경로가 박히므로, 워크트리에 등록하면 그것을 지우는 순간 브리지가
    죽고 launchd는 살리려다 실패를 반복한다.
    """
    directory = os.path.dirname(script)
    try:
        top = subprocess.run(
            ["git", "-C", directory, "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, timeout=5,
        )
        common = subprocess.run(
            ["git", "-C", directory, "rev-parse", "--path-format=absolute", "--git-common-dir"],
            capture_output=True, text=True, timeout=5,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if top.returncode != 0 or common.returncode != 0:
        return None
    toplevel = top.stdout.strip()
    main = os.path.dirname(common.stdout.strip())
    if toplevel and main and os.path.normpath(toplevel) != os.path.normpath(main):
        return toplevel
    return None


def plist_text(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT, python: str = "") -> str:
    import sys

    arguments = [python or sys.executable, BRIDGE_SCRIPT, "serve",
                 "--host", host, "--port", str(port)]
    items = "\n".join("      <string>%s</string>" % _xml_escape(a) for a in arguments)
    return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
  <dict>
    <key>Label</key>
    <string>%s</string>
    <key>ProgramArguments</key>
    <array>
%s
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>%s</string>
    <key>StandardErrorPath</key>
    <string>%s</string>
  </dict>
</plist>
""" % (LAUNCHD_LABEL, items, _xml_escape(BRIDGE_LOG), _xml_escape(BRIDGE_ERR))


def install(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT, force: bool = False) -> str:
    """로그인할 때 자동으로 뜨도록 등록한다. 등록 파일 경로를 돌려준다."""
    worktree = worktree_root()
    if worktree and not force:
        raise AdminError(
            "워크트리에서 등록하려 합니다: %s\n"
            "워크트리를 지우면 브리지가 죽습니다. 본 저장소의 demo/otel_bridge.py로 "
            "등록하세요." % worktree
        )
    os.makedirs(os.path.dirname(BRIDGE_LOG), exist_ok=True)
    os.makedirs(os.path.dirname(PLIST_PATH), exist_ok=True)
    with open(PLIST_PATH, "w", encoding="utf-8") as f:
        f.write(plist_text(host, port))
    launchctl(["bootout", _domain_target()])
    code, message = launchctl(["bootstrap", _domain(), PLIST_PATH])
    if code != 0:
        raise AdminError("launchd 등록에 실패했습니다: " + message)
    return PLIST_PATH


def uninstall() -> None:
    launchctl(["bootout", _domain_target()])
    if os.path.exists(PLIST_PATH):
        os.remove(PLIST_PATH)


def installed() -> bool:
    return os.path.exists(PLIST_PATH)


def start() -> None:
    if not installed():
        raise AdminError("먼저 자동 시작 등록(install)을 하세요.")
    code, message = launchctl(["bootstrap", _domain(), PLIST_PATH])
    if code != 0:
        # 이미 등록돼 있으면 bootstrap이 실패한다. 그때는 다시 띄운다.
        code, message = launchctl(["kickstart", "-k", _domain_target()])
        if code != 0:
            raise AdminError("시작하지 못했습니다: " + message)


def stop() -> None:
    launchctl(["bootout", _domain_target()])


def restart() -> None:
    """설정을 바꾼 뒤 부른다. base_url 같은 값은 기동 때 한 번만 읽는다."""
    if not installed():
        raise AdminError("자동 시작 등록이 없어 재시작할 수 없습니다. 직접 띄운 브리지는 손으로 껐다 켜세요.")
    code, message = launchctl(["kickstart", "-k", _domain_target()])
    if code != 0:
        raise AdminError("재시작하지 못했습니다: " + message)


def _domain() -> str:
    return "gui/%d" % os.getuid()


def _domain_target() -> str:
    return "%s/%s" % (_domain(), LAUNCHD_LABEL)


def launchctl(arguments: List[str]):
    try:
        result = subprocess.run(["launchctl"] + arguments, capture_output=True, text=True, timeout=15)
    except (OSError, subprocess.SubprocessError) as error:
        return 1, str(error)
    return result.returncode, (result.stderr or result.stdout).strip()


def _xml_escape(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


# ------------------------------------------------------------ 상태


def health(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT, timeout: float = 3.0) -> Optional[Dict]:
    """브리지 상태. 응답하지 않으면 None (꺼져 있다는 뜻이고 오류가 아니다)."""
    try:
        with urllib.request.urlopen(health_endpoint(host, port), timeout=timeout) as response:
            body = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, OSError, ValueError):
        return None
    return body if isinstance(body, dict) else None

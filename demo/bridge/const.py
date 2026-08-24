"""브리지가 쓰는 경로와 주소 (MS2-169).

CLI(otel_bridge.py), 서버(server.py), 운영 명령(admin.py), TUI(console.py)가
같은 값을 봐야 해서 한곳에 모은다. 특히 hook URL과 OTLP 엔드포인트는 setup이
Claude 설정에 적어 넣는 값이라, 여기서 어긋나면 이벤트가 조용히 사라진다.
"""

from __future__ import annotations

import os

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 4318

DEMO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGS_DIR = os.path.join(DEMO_DIR, "logs")
BRIDGE_SCRIPT = os.path.join(DEMO_DIR, "otel_bridge.py")

HOME_DIR = os.path.join(os.path.expanduser("~"), ".meterengine")
CONFIG_PATH = os.path.join(HOME_DIR, "bridge.json")
STATE_PATH = os.path.join(HOME_DIR, "state.json")
BRIDGE_LOG = os.path.join(HOME_DIR, "bridge.log")
BRIDGE_ERR = os.path.join(HOME_DIR, "bridge.err")

LAUNCHD_LABEL = "com.meterengine.otel-bridge"
PLIST_PATH = os.path.join(
    os.path.expanduser("~"), "Library", "LaunchAgents", LAUNCHD_LABEL + ".plist"
)

CLAUDE_SETTINGS_PATH = os.path.join(os.path.expanduser("~"), ".claude", "settings.json")

# 브리지가 여는 경로.
#   LOGS_PATH    Claude Code의 OTLP exporter가 사용량을 보내는 곳
#   SESSION_PATH UserPromptSubmit hook이 세션과 폴더를 알리는 곳
#   HEALTH_PATH  status와 TUI가 상태를 읽는 곳
LOGS_PATH = "/v1/logs"
SESSION_PATH = "/meterengine/session"
HEALTH_PATH = "/meterengine/health"


def logs_endpoint(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT) -> str:
    return "http://%s:%d%s" % (host, port, LOGS_PATH)


def session_endpoint(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT) -> str:
    return "http://%s:%d%s" % (host, port, SESSION_PATH)


def health_endpoint(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT) -> str:
    return "http://%s:%d%s" % (host, port, HEALTH_PATH)

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

LOGS_PATH = "/v1/logs"
SESSION_PATH = "/meterengine/session"
HEALTH_PATH = "/meterengine/health"


def logs_endpoint(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT) -> str:
    return "http://%s:%d%s" % (host, port, LOGS_PATH)


def session_endpoint(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT) -> str:
    return "http://%s:%d%s" % (host, port, SESSION_PATH)


def health_endpoint(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT) -> str:
    return "http://%s:%d%s" % (host, port, HEALTH_PATH)

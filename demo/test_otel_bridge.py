import contextlib
import io
import json
import os
import stat
import tempfile
import unittest

import otel_bridge
from bridge import server
from core.api_client import ApiResult
from core.model import API_KEY_ENV

KEY = "me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA"
ENV_KEY = "me_test_wiCp766QYNqhyYm4Z7Xq9wYosNvQCeVC"
ORGANIZATION_ID = "d7cee55d-8c82-4afc-b996-6749d8b26a4e"


class FakeClient:
    reply = None
    created = []

    def __init__(self, base_url, api_key, timeout_seconds=10.0):
        self.base_url = base_url
        self.api_key = api_key
        FakeClient.created.append(api_key)

    def get_organization(self):
        return FakeClient.reply


class BridgeCommandTestCase(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.config_path = os.path.join(self.directory.name, "bridge.json")
        self.state_path = os.path.join(self.directory.name, "state.json")
        original_env = os.environ.pop(API_KEY_ENV, None)
        if original_env is not None:
            self.addCleanup(os.environ.__setitem__, API_KEY_ENV, original_env)
        self.addCleanup(os.environ.pop, API_KEY_ENV, None)

    def main(self, argv):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = otel_bridge.main(argv)
        return code, out.getvalue(), err.getvalue()


class ConfigCommandTest(BridgeCommandTestCase):
    def test_키를_저장하고_가린_값만_보여준다(self):
        code, out, err = self.main(["config", "--config", self.config_path, "--api-key", KEY])
        self.assertEqual(code, 0)
        self.assertNotIn(KEY, out + err)
        self.assertIn("me_live_****2LtA", out)
        with open(self.config_path, encoding="utf-8") as f:
            self.assertEqual(json.load(f)["api_key"], KEY)
        self.assertEqual(stat.S_IMODE(os.stat(self.config_path).st_mode), 0o600)

    def test_보기만_해도_키를_가린다(self):
        self.main(["config", "--config", self.config_path, "--api-key", KEY])
        code, out, err = self.main(["config", "--config", self.config_path])
        self.assertEqual(code, 0)
        self.assertNotIn(KEY, out)
        self.assertIn("me_live_****2LtA", out)

    def test_형식이_틀린_키는_저장하지_않는다(self):
        code, out, err = self.main(["config", "--config", self.config_path, "--api-key", "me_live_short"])
        self.assertEqual(code, 2)
        self.assertNotIn("me_live_short", out + err)
        self.assertFalse(os.path.exists(self.config_path))

    def test_옛_설정의_org_id는_보여주지_않는다(self):
        with open(self.config_path, "w", encoding="utf-8") as f:
            json.dump({"owner": "박성종", "org_id": ORGANIZATION_ID}, f)
        code, out, err = self.main(["config", "--config", self.config_path])
        self.assertEqual(code, 0)
        self.assertNotIn("org_id", out)
        self.assertIn("박성종", out)


class ServeCommandTest(BridgeCommandTestCase):
    def setUp(self):
        super().setUp()
        original_client = server.ApiClient
        server.ApiClient = FakeClient
        self.addCleanup(setattr, server, "ApiClient", original_client)
        FakeClient.created = []
        FakeClient.reply = ApiResult(
            status=200,
            body={"id": ORGANIZATION_ID, "name": "데모 도입사", "status": "active"},
            body_text="",
            elapsed_ms=1,
        )
        self.served = []
        original_serve = server.serve
        server.serve = lambda *args: self.served.append(args) or 0
        self.addCleanup(setattr, server, "serve", original_serve)

    def serve(self, *extra):
        return self.main(["serve", "--config", self.config_path, "--state", self.state_path] + list(extra))

    def test_키가_없으면_안내하고_멈춘다(self):
        code, out, err = self.serve()
        self.assertEqual(code, 2)
        self.assertIn("--api-key", err)
        self.assertEqual(self.served, [])
        self.assertEqual(FakeClient.created, [])

    def test_옛_설정의_org_id만_있으면_키가_없는_것이다(self):
        with open(self.config_path, "w", encoding="utf-8") as f:
            json.dump({"org_id": ORGANIZATION_ID}, f)
        code, out, err = self.serve()
        self.assertEqual(code, 2)
        self.assertEqual(self.served, [])

    def test_설정의_키로_뜨고_scope는_첫_전송까지_정하지_않는다(self):
        self.main(["config", "--config", self.config_path, "--api-key", KEY])
        code, out, err = self.serve()
        self.assertEqual(code, 0)
        config, state, host, port = self.served[0]
        self.assertEqual(config.api_key, KEY)
        self.assertIsNone(state.scope)

    def test_환경변수가_설정보다_먼저다(self):
        self.main(["config", "--config", self.config_path, "--api-key", KEY])
        os.environ[API_KEY_ENV] = ENV_KEY
        self.serve()
        self.assertEqual(self.served[0][0].api_key, ENV_KEY)
        with open(self.config_path, encoding="utf-8") as f:
            self.assertEqual(json.load(f)["api_key"], KEY)

    def test_serve는_api_key_옵션을_받지_않는다(self):
        with self.assertRaises(SystemExit):
            with contextlib.redirect_stderr(io.StringIO()):
                otel_bridge.build_parser().parse_args(["serve", "--api-key", KEY])

    def test_키가_거부돼도_시작한다(self):
        FakeClient.reply = ApiResult(
            status=401, body={"code": "invalid_credentials", "message": "m"}, body_text="", elapsed_ms=1
        )
        self.main(["config", "--config", self.config_path, "--api-key", KEY])
        code, out, err = self.serve()
        self.assertEqual(code, 0)
        self.assertEqual(len(self.served), 1)
        self.assertEqual(FakeClient.created, [])
        self.assertNotIn(KEY, out + err)

    def test_백엔드에_닿지_않아도_시작한다(self):
        self.main(["config", "--config", self.config_path, "--base-url", "http://127.0.0.1:9", "--api-key", KEY])
        code, out, err = self.serve()
        self.assertEqual(code, 0)
        self.assertEqual(len(self.served), 1)


class LaunchdCommandTest(BridgeCommandTestCase):
    def setUp(self):
        super().setUp()
        admin = otel_bridge.admin
        self.called = []
        replacements = {
            "CONFIG_PATH": self.config_path,
            "PLIST_PATH": os.path.join(self.directory.name, "agent.plist"),
            "BRIDGE_LOG": os.path.join(self.directory.name, "bridge.log"),
            "worktree_root": lambda: None,
            "launchctl": lambda arguments: self.called.append(arguments[0]) or (0, ""),
        }
        for name, value in replacements.items():
            self.addCleanup(setattr, admin, name, getattr(admin, name))
            setattr(admin, name, value)

    def test_설정에_키가_없으면_등록과_시작을_거부한다(self):
        os.environ[API_KEY_ENV] = KEY
        for command in ("install", "start"):
            code, out, err = self.main([command])
            self.assertEqual(code, 2, command)
            self.assertIn("config --api-key", err)
        self.assertEqual(self.called, [])

    def test_설정에_키가_있으면_진행한다(self):
        self.main(["config", "--config", self.config_path, "--api-key", KEY])
        for command in ("install", "start"):
            code, out, err = self.main([command])
            self.assertEqual(code, 0, command)
        self.assertEqual(self.called, ["bootout", "bootstrap", "bootstrap"])


if __name__ == "__main__":
    unittest.main()

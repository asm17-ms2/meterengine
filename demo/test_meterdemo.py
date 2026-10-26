import contextlib
import io
import os
import tempfile
import unittest

import meterdemo
from core.api_client import ApiResult
from core.model import API_KEY_ENV
from csvdemo import send_cmd

KEY = "me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA"
CSV = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sample-events-edge.csv")


class RejectingClient:
    created = []

    def __init__(self, base_url, api_key, timeout_seconds=10.0):
        self.base_url = base_url
        self.api_key = api_key
        RejectingClient.created.append(api_key)

    def get_organization(self):
        return ApiResult(
            status=401,
            body={"code": "invalid_credentials", "message": "인증 정보가 올바르지 않습니다"},
            body_text="",
            elapsed_ms=1,
        )


class MainApiKeyTest(unittest.TestCase):
    def setUp(self):
        original_client = send_cmd.ApiClient
        send_cmd.ApiClient = RejectingClient
        self.addCleanup(setattr, send_cmd, "ApiClient", original_client)
        RejectingClient.created = []
        original_env = os.environ.pop(API_KEY_ENV, None)
        if original_env is not None:
            self.addCleanup(os.environ.__setitem__, API_KEY_ENV, original_env)

    def main(self, argv):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = meterdemo.main(argv)
        return code, out.getvalue(), err.getvalue()

    def test_형식이_틀린_키는_시작_전에_멈춘다(self):
        with tempfile.TemporaryDirectory() as directory:
            missing = os.path.join(directory, "없는.csv")
            code, out, err = self.main(["send", "--csv", missing, "--api-key", "me_live_short"])
        self.assertEqual(code, 2)
        self.assertIn("--api-key", err)
        self.assertNotIn("me_live_short", err)
        self.assertNotIn("CSV", err)
        self.assertEqual(RejectingClient.created, [])

    def test_환경변수의_키가_틀려도_멈춘다(self):
        os.environ[API_KEY_ENV] = "틀린값"
        self.addCleanup(os.environ.pop, API_KEY_ENV, None)
        code, out, err = self.main(["verify", "--csv", CSV])
        self.assertEqual(code, 2)
        self.assertIn(API_KEY_ENV, err)

    def test_키가_거부되면_code를_보이고_멈춘다(self):
        code, out, err = self.main(["send", "--csv", CSV, "--dry-run", "--api-key", KEY])
        self.assertEqual(code, 2)
        self.assertIn("invalid_credentials", err)
        self.assertNotIn(KEY, out + err)
        self.assertEqual(RejectingClient.created, [KEY])

    def test_키를_주지_않으면_로컬_키를_쓴다(self):
        self.main(["send", "--csv", CSV, "--dry-run"])
        self.assertEqual(RejectingClient.created, [KEY])


if __name__ == "__main__":
    unittest.main()

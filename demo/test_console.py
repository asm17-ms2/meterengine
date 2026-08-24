"""콘솔 테스트 (MS2-169).

textual이 있어야 돌아간다. demo의 나머지는 표준 라이브러리만 쓰므로, 없으면
건너뛴다. 콘솔만 uv로 실행하는 구조 그대로다 (console.py의 PEP 723 블록).

    uv run --with textual python3 -m unittest test_console
"""

import asyncio
import importlib.util
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

HAS_TEXTUAL = importlib.util.find_spec("textual") is not None


@unittest.skipUnless(HAS_TEXTUAL, "textual이 없다 (uv run --with textual)")
class SaveTest(unittest.TestCase):
    """저장 실패가 앱을 죽이면 안 된다.

    save는 validate를 거치므로 스킴 없는 base_url에 ValueError를 던진다. 그것을
    잡지 않으면 예외가 액션 핸들러를 빠져나가 앱이 트레이스백과 함께 내려간다.
    """

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.config_path = os.path.join(self.directory.name, "bridge.json")
        self.state_path = os.path.join(self.directory.name, "state.json")
        self.addCleanup(self.directory.cleanup)

    def _save_with(self, base_url):
        from textual.widgets import Input

        import console as console_module
        from bridge import admin

        # 기동 직후 health를 물어보러 나가지 않게 막는다. 네트워크를 쓰지 않는다.
        original = admin.health
        admin.health = lambda *args, **kwargs: None
        self.addCleanup(lambda: setattr(admin, "health", original))

        app = console_module.ConsoleApp(self.config_path, self.state_path)

        async def scenario():
            async with app.run_test() as pilot:
                app.query_one("#base_url", Input).value = base_url
                app.action_save()
                await pilot.pause()

        asyncio.run(scenario())
        return app

    def test_스킴_없는_전송_대상을_저장해도_앱이_살아_있다(self):
        app = self._save_with("localhost:8080")
        # 잘못된 값이 메모리에 남으면 화면과 어긋난다. 사본에만 담았어야 한다.
        self.assertEqual(app.config.base_url, "http://localhost:8080")
        self.assertFalse(os.path.exists(self.config_path))

    def test_올바른_값은_저장된다(self):
        app = self._save_with("https://meterengine.com")
        self.assertEqual(app.config.base_url, "https://meterengine.com")
        with open(self.config_path, encoding="utf-8") as f:
            self.assertEqual(json.load(f)["base_url"], "https://meterengine.com")


if __name__ == "__main__":
    unittest.main()
